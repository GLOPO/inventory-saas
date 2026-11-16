package com.glory.inventorysaas.entity;


import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "companies")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Company extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @OneToMany(mappedBy = "company")
    @Builder.Default
    private List<User> users = new ArrayList<>();

    @OneToMany(mappedBy = "company")
    @Builder.Default
    private List<Warehouse> warehouses = new ArrayList<>();
}
