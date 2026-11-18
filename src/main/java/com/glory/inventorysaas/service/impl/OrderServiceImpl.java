package com.glory.inventorysaas.service.impl;

import com.glory.inventorysaas.dto.Coordinates;
import com.glory.inventorysaas.dto.request.ApproveOrderRequest;
import com.glory.inventorysaas.dto.request.PlaceOrderRequest;
import com.glory.inventorysaas.dto.request.StockSaleRequest;
import com.glory.inventorysaas.dto.response.PlaceOrderResponse;
import com.glory.inventorysaas.entity.Company;
import com.glory.inventorysaas.entity.CustomerOrder;
import com.glory.inventorysaas.entity.Product;
import com.glory.inventorysaas.entity.Warehouse;
import com.glory.inventorysaas.entity.enums.OrderStatus;
import com.glory.inventorysaas.exception.ResourceNotFoundException;
import com.glory.inventorysaas.mapper.OrderMapper;
import com.glory.inventorysaas.repository.CompanyRepository;
import com.glory.inventorysaas.repository.OrderRepository;
import com.glory.inventorysaas.repository.ProductRepository;
import com.glory.inventorysaas.repository.WarehouseRepository;
import com.glory.inventorysaas.security.SecurityUtils;
import com.glory.inventorysaas.service.GeocodingService;
import com.glory.inventorysaas.service.OrderService;
import com.glory.inventorysaas.service.StockService;
import com.glory.inventorysaas.utils.DistanceCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
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

    private CustomerOrder updateOrderStatus (UUID orderId, OrderStatus status) {
        UUID companyId = securityUtils.getCurrentCompanyId();

        CustomerOrder order = orderRepository.findByIdAndCompanyId(orderId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Order Not Found"));

        if (!order.getCompany().getId().equals(companyId)) {
            throw new AccessDeniedException("Order Not Found");
        }

        if(order.getStatus() != OrderStatus.PENDING_REVIEW) {
            throw new IllegalArgumentException("This order has been acted on");
        }

        if (order.getProduct() != null) {
            Product matchedProduct = productRepository.findByIdAndCompanyId(order.getProduct().getId(), companyId)
                    .orElseThrow(() -> new ResourceNotFoundException("Product Not Found"));
        }

        if(order.getWarehouse() != null) {
            Warehouse fulfilledWarehouse = warehouseRepository.findByIdAndCompanyId(order.getWarehouse().getId(), companyId)
                    .orElseThrow(() -> new ResourceNotFoundException("Warehouse Not Found"));
        }

        order.setStatus(status);

         return orderRepository.save(order);
    }

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

    @Override
    @Transactional
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
    public List<PlaceOrderResponse> incomingOrders() {
        UUID companyId = securityUtils.getCurrentCompanyId();

        List<CustomerOrder> orders = orderRepository.findByCompanyIdOrderByCreatedAtDesc(companyId);

        return orders.stream()
                .map(orderMapper::toCustomerOrderResponse)
                .toList();
    }

    @Override
    @Transactional
    public PlaceOrderResponse approveOrder(UUID orderId, ApproveOrderRequest request) {
        UUID companyId = securityUtils.getCurrentCompanyId();

        CustomerOrder order = orderRepository.findByIdAndCompanyId(orderId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Order Not Found"));

        if (!order.getStatus().equals(OrderStatus.PENDING_REVIEW)) {
            throw new IllegalArgumentException("This order has been acted on");
        }

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

        StockSaleRequest recordRequest = StockSaleRequest.builder()
                .productId(request.productId())
                .quantity(order.getQuantity())
                .warehouseId(request.warehouseId())
                .build();

        stockService.recordSale(request.warehouseId(), recordRequest);

        return orderMapper.toCustomerOrderResponse(saved);
    } 
}

