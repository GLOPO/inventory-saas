# Chapter 1: System Architecture & Domain Model

[← Back to README](../README.md) | [Next: Frontend Integration Playbook →](02-frontend-guide.md)

---

## 1.1 Why This System Was Built This Way

`inventorysaas` is engineered around four real-world operational failure modes common in multi-warehouse retail and distribution:

1. **Eliminating "Ghost Inventory":** When multiple warehouses (`WA`, `WB`, `WC`) process concurrent orders, transfers, and restocks without atomic transactions, stock counts drift from physical reality. Every mutation in this system executes inside a Spring `@Transactional` boundary and appends an immutable record to the **Stock Movement Audit Log**.
2. **Zero-Trust Multi-Tenant Isolation:** In a SaaS environment, Tenant A must never read, mutate, or infer the existence of Tenant B's warehouses, SKUs, or orders. Tenant resolution happens inside the Spring Security filter chain (`JwtService` → `UserPrincipal`), binding every query to the authenticated user's tenant context.
3. **Automated Geolocation Order Fulfillment:** Public storefront orders do not require manual dispatcher triage. Warehouses store geographic coordinates (`latitude`, `longitude`), allowing the order engine to compute distances and automatically match incoming customer orders to the optimal warehouse holding available stock.
4. **Modular Monolith with Asynchronous Events:** Instead of introducing premature microservice network latency and distributed transaction complexity, internal domain events (such as low-stock/out-of-stock alerts and order status updates) are decoupled via **Spring Application Events** (`@EventListener` / `@Async`), keeping HTTP response times fast while ensuring reliable email notifications.

---

## 1.2 End-to-End Request & Event Architecture

```text
┌─────────────────────────────────────────────────────────────────────────┐
│                         CLIENT APPLICATIONS                             │
│   Admin Web Dashboard  │  Warehouse Portal  │  Public Customer Checkout │
└────────────────────────┴────────────────────┴───────────────────────────┘
                                    │
                                    ▼  HTTPS / JSON (+ Bearer JWT on protected routes)
┌─────────────────────────────────────────────────────────────────────────┐
│                     SPRING SECURITY FILTER CHAIN                        │
│  • Permits /api/v1/auth/**, /api/v1/public/**, /docs, /v3/api-docs      │
│  • Validates JWT signature & expiration via JwtService                  │
│  • Hydrates UserPrincipal (userId, tenantId, role) into SecurityContext │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                      REST CONTROLLERS & VALIDATION                      │
│  • Enforces RBAC via @PreAuthorize("hasRole(...)")                      │
│  • Validates request payloads via Jakarta @Valid annotations            │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                    TRANSACTIONAL SERVICE LAYER                          │
│  • Maps DTOs <-> Entities cleanly via MapStruct                         │
│  • Executes atomic stock math, geolocation matching & state transitions │
└───────────────┬───────────────────────────────────┬─────────────────────┘
                │                                   │
                ▼                                   ▼
┌───────────────────────────────┐   ┌─────────────────────────────────────┐
│         REDIS CACHE           │   │   SPRING APPLICATION EVENT BUS      │
│ • Caches Product Catalog &    │   │ • Publishes OrderApprovedEvent &    │
│   frequent inventory lookups  │   │   OutOfStockEvent asynchronously    │
│ • Evicts on catalog/stock     │   │ • Listeners dispatch SMTP emails    │
│   mutations                   │   │   via Spring Mail (Mailtrap/Prod)   │
└───────────────────────────────┘   └─────────────────────────────────────┘
                │
                ▼
┌─────────────────────────────────────────────────────────────────────────┐
│               SPRING DATA JPA / HIBERNATE + POSTGRESQL                  │
│  • Enforces foreign keys, unique tenant-SKU constraints & ACID writes   │
│  • Schema strictly managed via versioned Flyway migrations              │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## 1.3 Core Domain Entities & Relationships

```text
[Tenant / Organization]
   ├── 1:N ──► [User] (role: OWNER | WAREHOUSE_MANAGER | STAFF)
   ├── 1:N ──► [Warehouse] (name, address, latitude, longitude)
   ├── 1:N ──► [Product] (sku, name, unitPrice, lowStockThreshold)
   └── 1:N ──► [Order] (customerInfo, deliveryCoords, status, assignedWarehouse)
                  └── 1:N ──► [OrderItem] (product, quantity, unitPrice)

[Warehouse] ◄── N:M via [WarehouseStock] ──► [Product]
                    (availableQuantity)

[StockMovement (Immutable Audit Log)]
   • Links: tenantId, productId, sourceWarehouseId, targetWarehouseId, performedByUserId
   • Fields: movementType (PURCHASE | SALE | TRANSFER), quantity, referenceId, createdAt
```

### Role-Based Access Control (RBAC) Matrix

| Capability | `OWNER` | `WAREHOUSE_MANAGER` | `STAFF` |
| :--- | :---: | :---: | :---: |
| Manage Tenant & Staff Accounts | ✅ | ❌ | ❌ |
| Create / Edit Warehouses & Coordinates | ✅ | ❌ | ❌ |
| Create / Edit Product Catalog & SKUs | ✅ | ❌ | ❌ |
| View Product Catalog & Stock Levels | ✅ | ✅ | ✅ |
| Record Stock Purchases (`+`) & Sales (`-`) | ✅ | ✅ | ✅ |
| Execute Inter-Warehouse Transfers | ✅ | ✅ | ✅ |
| View Incoming Customer Orders | ✅ | ✅ | ❌ |
| Approve, Reroute, or Complete Orders | ✅ | ❌ | ❌ |
| View & Export PDF Audit Logs | ✅ | ✅ | ❌ |

---

[← Back to README](../README.md) | [Next: Frontend Integration Playbook →](02-frontend-guide.md)