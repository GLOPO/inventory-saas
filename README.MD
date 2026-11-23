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

---

## 📚 The Engineering Storybook (Role-Based Guides)

This repository is documented so that **Frontend Developers**, **QA Testers**, and **CI/CD / DevOps Engineers** can integrate, verify, and deploy the system autonomously without needing to reach out for clarification.

| Chapter | Document | Target Audience | What You Will Find Inside |
| :--- | :--- | :--- | :--- |
| **Chapter 1** | [**Architecture & Domain Model**](docs/01-architecture.md) | All Engineers | Engineering philosophy, request & event flow diagrams, multi-tenant isolation model, modular monolith event bus, and core entity relationships. |
| **Chapter 2** | [**Frontend Integration Playbook**](docs/02-frontend-guide.md) | Frontend & Mobile Devs | Interactive Swagger (`/docs`), JWT lifecycle, RBAC UI rules, complete endpoint reference, geolocation payload formats, PDF blob downloads, and global error handling. |
| **Chapter 3** | [**QA & Automation Verification Guide**](docs/03-qa-playbook.md) | QA & SDET Engineers | Mathematical stock invariants, transactional rollback guarantees, `test_order_flow.py` E2E runner instructions, edge-case matrix, and Mailtrap verification. |
| **Chapter 4** | [**CI/CD, Deployment & Git Runbook**](docs/04-devops-runbook.md) | DevOps & Release Eng | Complete environment variable contract, Flyway migration guardrails, Docker/Compose orchestration, health probes, CI pipeline stages, and Git branching protocol. |

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