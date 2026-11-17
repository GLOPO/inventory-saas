package com.glory.inventorysaas.entity;

import com.glory.inventorysaas.entity.enums.OrderStatus;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "customer_orders")
@Setter @Getter @NoArgsConstructor @AllArgsConstructor @Builder
public class CustomerOrder extends BaseEntity{
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "matched_product_id", nullable = true)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "fulfilled_warehouse_id", nullable = true)
    private Warehouse warehouse;

    @Column(nullable = false, name = "requested_product_name")
    private String requestedProductName;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "customer_email", nullable = false)
    private String customerEmail;

    @Column(name = "delivery_location", nullable = false)
    private String deliveryLocation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;
}
