package com.glory.inventorysaas.service.impl;

import com.glory.inventorysaas.dto.request.PlaceOrderRequest;
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
import com.glory.inventorysaas.service.OrderService;
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

    private CustomerOrder updateOrderStatus (UUID orderId, OrderStatus status) {
        UUID companyId = securityUtils.getCurrentCompanyId();

        CustomerOrder order = orderRepository.findByIdAndCompanyId(orderId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Order Not Found"));

        if (!order.getCompany().getId().equals(companyId)) {
            throw new AccessDeniedException("This order does not belong to your company");
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

        CustomerOrder order = CustomerOrder.builder()
                .company(company)
                .deliveryLocation(request.deliveryLocation())
                .customerEmail(request.customerEmail())
                .requestedProductName(request.requestedProductName())
                .quantity(request.quantity())
                .status(OrderStatus.PENDING_REVIEW)
                .warehouse(null)
                .product(matchedProduct)
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
    public PlaceOrderResponse approveOrder(UUID orderId) {
        return null;
    }
}
