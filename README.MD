# Multi-Tenant SaaS Inventory & Warehouse Management API

A backend system built with **Java**, **Spring Boot**, **PostgreSQL**, **Flyway**, **Redis**, and **Spring Security (JWT)** that enables multiple companies to manage warehouses, product catalogs, stock levels, and audited inventory movements with strict tenant and warehouse-level isolation.

---

## Key Architectural Features

* **Multi-Tenant Data Isolation:** Every operational query and mutation resolves the authenticated user's `companyId` directly from the JWT `SecurityContext`, preventing cross-tenant data leaks or URL parameter tampering.
* **Hierarchical Role-Based Access Control (RBAC):**
    * **`OWNER`:** Registers the company, manages warehouses and product catalogs, views company-wide stock and audit trails, and provisions `WAREHOUSE_MANAGER` accounts.
    * **`WAREHOUSE_MANAGER`:** Scoped strictly to an assigned warehouse; manages local stock movements (`PURCHASE`, `SALE`, `TRANSFER`) and provisions `STAFF` accounts for that warehouse.
    * **`STAFF`:** Scoped to an assigned warehouse to execute authorized daily stock operations.
* **Database-Enforced Integrity (Flyway + PostgreSQL):** Schema changes are managed via sequential Flyway SQL migrations with native `CHECK` constraints guaranteeing valid movement states (`PURCHASE`, `SALE`, `TRANSFER`) and non-negative inventory balances.
* **Transactional Stock Engine & Immutable Audit Trail:** All stock mutations execute within `@Transactional` boundaries—atomically updating source/destination warehouse balances while recording every change in `stock_movements`.
* **Redis Caching Layer:** Accelerates high-frequency read endpoints (product catalogs, warehouse lists, inventory views) with automatic cache eviction (`@CacheEvict`) on write mutations.

---

## Tech Stack

* **Language & Framework:** Java 17+, Spring Boot 3.x (Web, Data JPA, Security, Validation, Cache)
* **Database & Migrations:** PostgreSQL, Flyway
* **Caching:** Redis
* **Authentication:** Stateless JWT (`jjwt`) with BCrypt password hashing
* **Mapping & Documentation:** MapStruct, Lombok, Springdoc OpenAPI (Swagger UI)
* **Containerization:** Multi-stage Docker build

---

## Domain Model

```text
Company (Tenant Root)
 ├── Users (OWNER [warehouse_id=NULL], WAREHOUSE_MANAGER, STAFF)
 ├── Warehouses (WA, WB, WC...)
 ├── Products (Company Catalog with unique SKU per company)
 └── Inventories (Junction tracking product quantity per warehouse)
      └── StockMovements (Immutable audit log: PURCHASE | SALE | TRANSFER)