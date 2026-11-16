package com.glory.inventorysaas.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "products",
        uniqueConstraints = {
            @UniqueConstraint(
                    columnNames = {"company_id", "sku"}
            )
        }
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Product extends BaseEntity{

    @Column(nullable = false)
    private String sku;

    @Column(nullable = false)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;
}
