package com.glory.inventorysaas.service.impl;

import com.glory.inventorysaas.dto.Coordinates;
import com.glory.inventorysaas.dto.request.*;
import com.glory.inventorysaas.dto.response.PlaceOrderResponse;
import com.glory.inventorysaas.dto.response.TransferRequestResponse;
import com.glory.inventorysaas.entity.*;
import com.glory.inventorysaas.entity.enums.OrderStatus;
import com.glory.inventorysaas.entity.enums.TransferStatus;
import com.glory.inventorysaas.exception.ResourceNotFoundException;
import com.glory.inventorysaas.mapper.OrderMapper;
import com.glory.inventorysaas.mapper.TransferRequestMapper;
import com.glory.inventorysaas.repository.*;
import com.glory.inventorysaas.security.SecurityUtils;
import com.glory.inventorysaas.service.GeocodingService;
import com.glory.inventorysaas.service.OrderService;
import com.glory.inventorysaas.service.StockService;
import com.glory.inventorysaas.utils.DistanceCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {
    private final ProductRepository productRepository;
    private final CompanyRepository companyRepository;
    private final OrderRepository orderRepository;
    private final WarehouseRepository warehouseRepository;
    private final OrderMapper orderMapper;
    private final SecurityUtils securityUtils;
    private final GeocodingService locationService;
    private final StockService stockService;
    private final TransferRequestRepository transferRequestRepository;
    private final TransferRequestMapper transferRequestMapper;

    private Optional<Warehouse> findNearest(List<Warehouse> warehouses, double lat, double lon) {
        Warehouse nearest = null;
        double shortest = Double.MAX_VALUE;

        for (Warehouse warehouse : warehouses) {
            if (warehouse.getLatitude() == null || warehouse.getLongitude() == null)
                continue;

            double distance = DistanceCalculator.calculateDistance(lat, lon, warehouse.getLatitude(),warehouse.getLongitude());

            if (distance < shortest) {
                shortest = distance;
                nearest = warehouse;
            }
        }

        return Optional.ofNullable(nearest);
    }

    private CustomerOrder findPendingOrder(UUID orderId, UUID companyId) {
        CustomerOrder order = orderRepository.findByIdAndCompanyId(orderId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Order Not Found"));

        if (!order.getStatus().equals(OrderStatus.PENDING_REVIEW)) {
            throw new IllegalArgumentException("This order has been acted on");
        }

        return order;
    }

    private TransferRequest findPendingTransfer(UUID requestId, UUID companyId, UUID warehouseId) {
        TransferRequest transferRequest = transferRequestRepository.findByIdAndCompanyId(requestId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Transfer Request Not Found"));

        if (!transferRequest.getSourceWarehouse().getId().equals(warehouseId)) {
            throw new AccessDeniedException("This request was not sent to your warehouse");
        }
        if (!transferRequest.getStatus().equals(TransferStatus.PENDING)) {
            throw new IllegalArgumentException("This request has already been answered");
        }

        return transferRequest;
    }

    private List<TransferRequestResponse> toTransferResponses(List<TransferRequest> requests) {
        return requests.stream()
                .map(transferRequestMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional
    @CacheEvict(value = "incoming-orders", key = "#companyId.toString()")
    public PlaceOrderResponse placeOrder(UUID companyId, PlaceOrderRequest request) {
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Company not found"));

        Product matchedProduct = null;

        if (request.selectedProductId() != null) {
            matchedProduct = productRepository.findByIdAndCompanyId(request.selectedProductId(), companyId)
                    .orElseThrow(() -> new ResourceNotFoundException("Product Not Found"));
        }

        Optional<Coordinates> coordinates = locationService.getLocation(request.deliveryLocation());
        Double lat = null;
        Double lon = null;

        if (coordinates.isPresent()) {
            lat = coordinates.get().latitude();
            lon = coordinates.get().longitude();
        }

        CustomerOrder order = CustomerOrder.builder()
                .company(company)
                .deliveryLocation(request.deliveryLocation())
                .customerEmail(request.customerEmail())
                .requestedProductName(request.requestedProductName())
                .quantity(request.quantity())
                .status(OrderStatus.PENDING_REVIEW)
                .warehouse(null)
                .product(matchedProduct)
                .deliveryLatitude(lat)
                .deliveryLongitude(lon)
                .build();

        CustomerOrder saved = orderRepository.save(order);

        return orderMapper.toCustomerOrderResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "incoming-orders", key = "@securityUtils.getCurrentCompanyCacheKey()")
    public List<PlaceOrderResponse> incomingOrders() {
        UUID companyId = securityUtils.getCurrentCompanyId();

        List<CustomerOrder> orders = orderRepository.findByCompanyIdOrderByCreatedAtDesc(companyId);

        return orders.stream()
                .map(orderMapper::toCustomerOrderResponse)
                .toList();
    }

    @Override
    @Transactional
    @CacheEvict(value = {"incoming-orders", "warehouse-orders"}, allEntries = true)
    public PlaceOrderResponse approveOrder(UUID orderId, ApproveOrderRequest request) {
        UUID companyId = securityUtils.getCurrentCompanyId();

        CustomerOrder order = findPendingOrder(orderId, companyId);

        Product product = productRepository.findByIdAndCompanyId(request.productId(), companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Product Not Found"));

        Warehouse warehouse;
        if(request.warehouseId() != null) {
            warehouse = warehouseRepository.findByIdAndCompanyId(request.warehouseId(), companyId)
                    .orElseThrow(() -> new ResourceNotFoundException("Warehouse Not Found"));
        } else {
            if (order.getDeliveryLatitude() == null || order.getDeliveryLongitude() == null) {
                throw new IllegalArgumentException("Delivery Location cannot be found. Please choose a warehouse");
            }
            warehouse = findNearest(warehouseRepository.findByCompanyId(companyId), order.getDeliveryLatitude(), order.getDeliveryLongitude())
                    .orElseThrow(() -> new IllegalArgumentException("No warehouse with a location found. Please choose one"));
        }

        order.setProduct(product);
        order.setWarehouse(warehouse);
        order.setStatus(OrderStatus.APPROVED);

        CustomerOrder saved = orderRepository.save(order);

        return orderMapper.toCustomerOrderResponse(saved);
    }

    @Override
    @Transactional
    @CacheEvict(value = {"incoming-orders", "warehouse-orders"}, allEntries = true)
    public PlaceOrderResponse rejectOrder(UUID orderId, RejectOrderRequest request) {
        UUID companyId = securityUtils.getCurrentCompanyId();

        CustomerOrder order = findPendingOrder(orderId, companyId);

        order.setStatus(OrderStatus.REJECTED);
        order.setRejectionReason(request.rejectionReason().trim());

        CustomerOrder saved = orderRepository.save(order);
        return orderMapper.toCustomerOrderResponse(saved);
    }

    @Override
    @Transactional
    @CacheEvict(value = {"incoming-orders", "warehouse-orders"}, allEntries = true)
    public PlaceOrderResponse completeOrder(UUID orderId) {
        UUID companyId = securityUtils.getCurrentCompanyId();
        UUID warehouseId = securityUtils.getCurrentWarehouseId();

        CustomerOrder order = orderRepository.findByIdAndCompanyId(orderId, companyId)
                        .orElseThrow(() -> new ResourceNotFoundException("Order Not Found"));

        if(order.getWarehouse() == null || !order.getWarehouse().getId().equals(warehouseId)) {
            throw new IllegalArgumentException("This order does not belong to your warehouse");
        }

        if(!order.getStatus().equals(OrderStatus.APPROVED)) {
            throw new IllegalArgumentException("This order is not ready for delivery");
        }

        StockSaleRequest recordRequest = StockSaleRequest.builder()
                .productId(order.getProduct().getId())
                .quantity(order.getQuantity())
                .build();

        stockService.recordSale(recordRequest);


        order.setStatus(OrderStatus.COMPLETED);

        CustomerOrder saved = orderRepository.save(order);

        return orderMapper.toCustomerOrderResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "warehouse-orders", key = "@securityUtils.getCurrentWarehouseId()")
    public List<PlaceOrderResponse> warehouseOrders() {
        UUID warehouseId = securityUtils.getCurrentWarehouseId();

        List<CustomerOrder> orders = orderRepository.findByWarehouseIdAndStatusOrderByCreatedAtDesc(warehouseId, OrderStatus.APPROVED);

        return orders.stream()
                .map(orderMapper::toCustomerOrderResponse)
                .toList();
    }

    @Override
    @Transactional
    @CacheEvict(value = {"incoming-orders", "warehouse-orders"}, allEntries = true)
    public PlaceOrderResponse rerouteOrder(UUID orderId, RerouteOrderRequest request) {
        UUID companyId = securityUtils.getCurrentCompanyId();
        UUID warehouseId = securityUtils.getCurrentWarehouseId();

        CustomerOrder order = orderRepository.findByIdAndCompanyId(orderId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Order Not Found"));

        if (order.getWarehouse() == null || !order.getWarehouse().getId().equals(warehouseId)) {
            throw new IllegalArgumentException("This order does not belong to your warehouse");
        }

        if (!order.getStatus().equals(OrderStatus.APPROVED)) {
            throw new IllegalArgumentException("This order is not yet APPROVED");
        }

        if (warehouseId.equals(request.targetWarehouseId())) {
            throw new IllegalArgumentException("You cannot reroute the same warehouse");
        }

        Warehouse targetWarehouse = warehouseRepository.findByIdAndCompanyId(request.targetWarehouseId(), companyId)
                .orElseThrow(() -> new ResourceNotFoundException("The target warehouse does not belong to your company"));

        order.setWarehouse(targetWarehouse);
        order.setReroutingReason(request.reroutingReason().trim());

        CustomerOrder saved = orderRepository.save(order);

        return orderMapper.toCustomerOrderResponse(saved);
    }

    @Override
    @Transactional
    @CacheEvict(value = {"incoming-transfer-requests", "my-transfer-requests"}, allEntries = true)
    public TransferRequestResponse createTransfer(TransferStockRequest request) {
        UUID companyId = securityUtils.getCurrentCompanyId();
        UUID warehouseId = securityUtils.getCurrentWarehouseId();

        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Company Not Found"));

        if (request.sourceWarehouseId().equals(warehouseId)) {
            throw new IllegalArgumentException("You cannot request stock from your warehouse");
        }

        Product product = productRepository.findByIdAndCompanyId(request.productId(), companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Product Not Found"));

        Warehouse sourceWarehouse = warehouseRepository.findByIdAndCompanyId(request.sourceWarehouseId(), companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Source Warehouse Not Found"));

        Warehouse destinationWarehouse = warehouseRepository.findByIdAndCompanyId(warehouseId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Your warehouse not found"));

        CustomerOrder order = null;
        if(request.orderId() != null) {
            order = orderRepository.findByIdAndCompanyId(request.orderId(), companyId)
                    .orElseThrow(() -> new ResourceNotFoundException("Order Not Found"));

            if (order.getWarehouse() == null || !order.getWarehouse().getId().equals(warehouseId)) {
                throw new AccessDeniedException("This order is not assigned to your warehouse");
            }

            if (!order.getStatus().equals(OrderStatus.APPROVED)) {
                throw new IllegalArgumentException("This order is not ready for delivery");
            }
        }

        TransferRequest transferRequest = TransferRequest.builder()
                .company(company)
                .destinationWarehouse(destinationWarehouse)
                .order(order)
                .product(product)
                .quantity(request.quantity())
                .sourceWarehouse(sourceWarehouse)
                .status(TransferStatus.PENDING)
                .build();

        TransferRequest saved = transferRequestRepository.save(transferRequest);

        return transferRequestMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "incoming-transfer-requests", key = "@securityUtils.getCurrentWarehouseId()")
    public List<TransferRequestResponse> incomingTransferRequests() {
        UUID warehouseId = securityUtils.getCurrentWarehouseId();

        List<TransferRequest> requests = transferRequestRepository.findBySourceWarehouseIdAndStatusOrderByCreatedAtDesc(warehouseId, TransferStatus.PENDING);

        return toTransferResponses(requests);
    }

    @Override
    @Transactional
    @CacheEvict(value = {"incoming-transfer-requests", "my-transfer-requests"}, allEntries = true)
    public TransferRequestResponse acceptTransfer(UUID requestId) {
        UUID companyId = securityUtils.getCurrentCompanyId();
        UUID warehouseId = securityUtils.getCurrentWarehouseId();

        TransferRequest transferRequest = findPendingTransfer(requestId, companyId, warehouseId);

        StockTransferRequest request = StockTransferRequest.builder()
                .destinationWarehouseId(transferRequest.getDestinationWarehouse().getId())
                .productId(transferRequest.getProduct().getId())
                .quantity(transferRequest.getQuantity())
                .build();

        stockService.transferStock(request);
        transferRequest.setStatus(TransferStatus.ACCEPTED);

        TransferRequest saved = transferRequestRepository.save(transferRequest);

        return transferRequestMapper.toResponse(saved);
    }

    @Override
    @Transactional
    @CacheEvict(value = {"incoming-transfer-requests", "my-transfer-requests"}, allEntries = true)
    public TransferRequestResponse declineTransfer(UUID requestId) {
        UUID companyId = securityUtils.getCurrentCompanyId();
        UUID warehouseId = securityUtils.getCurrentWarehouseId();

        TransferRequest transferRequest = findPendingTransfer(requestId, companyId, warehouseId);

        transferRequest.setStatus(TransferStatus.DECLINED);

        TransferRequest saved = transferRequestRepository.save(transferRequest);

        return transferRequestMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "my-transfer-requests", key = "@securityUtils.getCurrentWarehouseId()")
    public List<TransferRequestResponse> myRequests() {
        UUID warehouseId = securityUtils.getCurrentWarehouseId();

        List<TransferRequest> requests = transferRequestRepository.findByDestinationWarehouseIdOrderByCreatedAtDesc(warehouseId);

        return toTransferResponses(requests);
    }
}

