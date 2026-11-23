# Chapter 3: QA & Automation Verification Guide

[← Chapter 2: Frontend Guide](02-frontend-guide.md) | [Back to README](../README.md) | [Next: CI/CD & Deployment Runbook →](04-devops-runbook.md)

---

## 3.1 Running the Automated E2E Test Suite (`test_order_flow.py`)

The repository ships with a comprehensive Python end-to-end integration test script (`test_order_flow.py`) that executes **41 sequential assertions** against a live stack (multi-tenant setup, multi-warehouse geolocation seeding, product catalog creation, stock transfers, public order routing, order rerouting, and order approval).

### Execution Steps
1. Start a clean environment:
   ```bash
   docker-compose down -v && docker-compose up -d --build
   ```
2. Wait until `http://localhost:8080/v3/api-docs` responds with `200 OK`.
3. Install Python dependencies (if running outside container) and execute the suite:
   ```bash
   pip install requests pytest
   python test_order_flow.py
   ```
4. **Pass Criteria:** All 41 checks pass with `0` HTTP `500` errors and exact stock balance reconciliation at termination.

---

## 3.2 Non-Negotiable System Invariants

Every release must preserve these six mathematical and security invariants:

1. **Zero Negative Inventory Invariant:**
   $$\forall (w, p) \in \text{Warehouses} \times \text{Products}, \quad \text{Stock}(w, p) \ge 0$$
   Any sale, transfer, or order approval that would reduce stock below `0` must abort with `400 Bad Request` and perform a full database rollback.
2. **Transfer Conservation Law:**
   When transferring $Q$ units of Product $P$ from Source Warehouse $W_A$ to Destination Warehouse $W_B$:
   $$\text{Stock}_{\text{new}}(W_A, P) = \text{Stock}_{\text{old}}(W_A, P) - Q$$
   $$\text{Stock}_{\text{new}}(W_B, P) = \text{Stock}_{\text{old}}(W_B, P) + Q$$
   $$\sum_{w} \text{Stock}_{\text{new}}(w, P) = \sum_{w} \text{Stock}_{\text{old}}(w, P)$$
3. **Order Approval Atomicity:**
   Approving a `PENDING` order must execute four effects atomically:
    * Transition order status from `PENDING` → `APPROVED`.
    * Deduct ordered item quantities from the assigned warehouse.
    * Append an immutable `SALE` record to `StockMovement`.
    * Publish an asynchronous notification event.
4. **Idempotent State Transitions:**
   Calling `/approve` on an order that is already `APPROVED`, `COMPLETED`, or `CANCELLED` must fail (`400` or `409`) and **must never deduct stock twice**.
5. **Cross-Tenant Opacity:**
   Authenticating as Tenant B and requesting a known UUID belonging to Tenant A must never return Tenant A's data (`404 Not Found` / `403 Forbidden`).
6. **Cache Coherence:**
   Updating a product or modifying stock must immediately invalidate stale entries in Redis so subsequent `GET` requests never return outdated quantities or prices.

---

## 3.3 Comprehensive QA Test Matrix

| ID | Category | Test Case & Preconditions | Expected API & System Behavior |
| :--- | :--- | :--- | :--- |
| **QA-01** | **Geolocation Routing** | Warehouse `WA` is 2 km from customer; `WB` is 45 km away. Both hold 50 units of `PA`. Customer places public order for 5 units of `PA`. | `201 Created`. Order status is `PENDING` and `assignedWarehouseId` equals `WA.id`. |
| **QA-02** | **Stock-Aware Fallback Routing** | Nearest warehouse `WA` has `0` units of `PA`; farther warehouse `WB` has `20` units. Customer places public order near `WA` for 5 units. | System skips `WA` (insufficient stock) and routes the order to `WB` cleanly without `500` errors. |
| **QA-03** | **Order Rerouting Before Approval** | Order is `PENDING` on `WA`. `OWNER` calls `PATCH /orders/{id}/reroute` to `WB`, then calls `PATCH /orders/{id}/approve`. | Stock is deducted from `WB` (not `WA`), and the `SALE` audit log references `WB`. |
| **QA-04** | **Double Approval Guard** | `OWNER` calls `PATCH /orders/{id}/approve` twice consecutively on the same order. | 1st call: `200 OK` (stock deducted once). 2nd call: `400`/`409` error (stock unchanged). |
| **QA-05** | **Atomic Transfer Rollback** | `WA` has `10` units. Attempt `POST /inventory/transfer` of `15` units from `WA` to `WB`. | `400 Bad Request`. `WA` still has `10` units, `WB` has `0` units, and no `TRANSFER` audit log is written. |
| **QA-06** | **Out-of-Stock Event Notification** | `WA` has `5` units of `PA`. Approve an order (or record a sale) for `5` units, bringing stock to `0`. | HTTP request returns `200 OK` immediately; Mailtrap inbox receives an out-of-stock alert email for `WA` / `PA`. |
| **QA-07** | **RBAC Enforcement** | Authenticate as `STAFF` and attempt `POST /warehouses`, `POST /products`, and `PATCH /orders/{id}/approve`. | All three requests are rejected with `403 Forbidden`. |
| **QA-08** | **Multi-Tenant Isolation** | Create Warehouse `W1` under Tenant A. Authenticate as `OWNER` of Tenant B and call `GET /warehouses/{W1.id}`. | Rejected with `404 Not Found` or `403 Forbidden`. Tenant B's `GET /warehouses` list does not include `W1`. |
| **QA-09** | **PDF Audit Export Integrity** | Record 1 `PURCHASE`, 1 `TRANSFER`, and 1 `SALE`. Call `GET /audit-logs/export/pdf`. | Returns `200 OK`, `Content-Type: application/pdf`, valid binary PDF stream containing all 3 movement records. |

---

[← Chapter 2: Frontend Guide](02-frontend-guide.md) | [Back to README](../README.md) | [Next: CI/CD & Deployment Runbook →](04-devops-runbook.md)