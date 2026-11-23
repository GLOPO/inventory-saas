# Chapter 2: Frontend & Mobile Integration Playbook

[← Chapter 1: Architecture](01-architecture.md) | [Back to README](../README.md) | [Next: QA Verification Guide →](03-qa-playbook.md)

---

## 2.1 Live API Exploration & Code Generation

Before writing manual fetch wrappers, inspect the live OpenAPI contract generated directly from the backend controllers:
* **Swagger UI:** `http://localhost:8080/docs`
* **Raw OpenAPI 3 JSON:** `http://localhost:8080/v3/api-docs` (Import into Postman or run `openapi-typescript` to generate exact TypeScript interfaces).

---

## 2.2 Authentication & Session Management

### 1. Public vs. Protected Routes
* **No Token Required:** `/api/v1/auth/**` (registration/login) and `/api/v1/public/**` (public customer order placement).
* **Token Required:** All other `/api/v1/**` endpoints require the HTTP `Authorization` header:
  ```http
  Authorization: Bearer <access_token>
  Content-Type: application/json
  ```

### 2. Storage & Interceptor Rules
* **Web Clients (React/Vite):** Store tokens in memory or secure storage; attach via a centralized Axios/Fetch request interceptor.
* **Mobile Clients (React Native / Expo):** Persist tokens using `expo-secure-store`.
* **Global `401 Unauthorized` Handling:** When a JWT expires, protected routes return `401`. Configure your HTTP response interceptor to clear local session state and redirect to `/login` (or call your refresh token flow).
* **Global `403 Forbidden` Handling:** Indicates a valid login with insufficient RBAC privileges (e.g., `STAFF` attempting an `OWNER` action). Decode the user's role on login to conditionally hide restricted buttons, and display an `"Insufficient Permissions"` toast if `403` occurs.

---

## 2.3 Endpoint Reference by Module

### A. Authentication & User Provisioning
| Method | Endpoint | Access | Description & Payload Notes |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/v1/auth/register` | Public | Registers a new Tenant organization and its initial `OWNER` account. |
| `POST` | `/api/v1/auth/login` | Public | Authenticates email/password and returns `{ token, user, role, tenantId }`. |
| `POST` | `/api/v1/users` | `OWNER` | Provisions a `WAREHOUSE_MANAGER` or `STAFF` user inside the owner's tenant. |
| `GET` | `/api/v1/users` | `OWNER` | Lists all active staff and managers under the current tenant. |

### B. Warehouses (Geolocation Enabled)
| Method | Endpoint | Access | Description & Payload Notes |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/v1/warehouses` | `OWNER` | Creates a warehouse. **Must include `latitude` and `longitude`** (decimal degrees) for automated order routing. |
| `GET` | `/api/v1/warehouses` | Authenticated | Lists all warehouses belonging to the caller's tenant. |
| `PUT` | `/api/v1/warehouses/{id}` | `OWNER` | Updates warehouse address, capacity metadata, or geolocation coordinates. |

### C. Product Catalog
| Method | Endpoint | Access | Description & Payload Notes |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/v1/products` | `OWNER` | Creates a catalog product. `sku` must be unique within the tenant. |
| `GET` | `/api/v1/products` | Authenticated | Returns tenant products (served via Redis cache for low latency). |
| `PUT` | `/api/v1/products/{id}` | `OWNER` | Updates product details/pricing and automatically invalidates Redis cache. |

### D. Inventory Levels & Stock Movements
> **Frontend Rule:** Never compute or optimistically overwrite stock balances locally. Always submit the movement and update UI state from the server's response.

| Method | Endpoint | Access | Description & Payload Notes |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/v1/inventory/warehouse/{warehouseId}` | Authenticated | Fetches current product quantities stocked in a specific warehouse. |
| `POST` | `/api/v1/inventory/purchase` | `OWNER`, `MANAGER`, `STAFF` | Records incoming supplier stock (`+quantity`) into a target warehouse. |
| `POST` | `/api/v1/inventory/sale` | `OWNER`, `MANAGER`, `STAFF` | Records direct/walk-in stock deduction (`-quantity`) from a warehouse. |
| `POST` | `/api/v1/inventory/transfer` | `OWNER`, `MANAGER`, `STAFF` | Atomically moves `quantity` from `sourceWarehouseId` to `targetWarehouseId`. Fails with `400` if source stock is insufficient. |

### E. Public Storefront Orders & Fulfillment Workflow
| Method | Endpoint | Access | Description & Payload Notes |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/v1/public/orders` | **Public** | Customer checkout endpoint. Accepts customer contact details, delivery coordinates (`latitude`, `longitude`), and line items (`productId`, `quantity`). Automatically routes to the closest warehouse with sufficient stock. |
| `GET` | `/api/v1/orders` | `OWNER`, `MANAGER` | Lists incoming and historical orders with filter support by status (`PENDING`, `APPROVED`, `REROUTED`, `COMPLETED`). |
| `PATCH` | `/api/v1/orders/{id}/approve` | `OWNER` | Approves a `PENDING` order, atomically deducts warehouse stock, logs a `SALE` movement, and dispatches confirmation notifications. |
| `PATCH` | `/api/v1/orders/{id}/reroute` | `OWNER` | Reassigns an order to an alternate `targetWarehouseId` prior to approval/fulfillment. |
| `PATCH` | `/api/v1/orders/{id}/complete` | `OWNER` | Marks an approved order as physically fulfilled/delivered. |

### F. Immutable Audit Logs & PDF Export
| Method | Endpoint | Access | Description & Payload Notes |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/v1/audit-logs` | `OWNER`, `MANAGER` | Paginated history of all `PURCHASE`, `SALE`, and `TRANSFER` records. |
| `GET` | `/api/v1/audit-logs/export/pdf` | `OWNER`, `MANAGER` | Streams a formatted PDF report (`Content-Type: application/pdf`). |

#### Handling the PDF Export in Frontend JavaScript/TypeScript:
```typescript
const response = await api.get('/api/v1/audit-logs/export/pdf', {
  responseType: 'blob',
});
const url = window.URL.createObjectURL(new Blob([response.data], { type: 'application/pdf' }));
const link = document.createElement('a');
link.href = url;
link.setAttribute('download', `inventory-audit-${new Date().toISOString().slice(0, 10)}.pdf`);
document.body.appendChild(link);
link.click();
link.remove();
```

---

## 2.4 Standardized HTTP Status & Error Response Contract

All exceptions are intercepted by `@RestControllerAdvice` and normalized into a consistent JSON error payload:

```json
{
  "timestamp": "2026-11-23T09:10:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Insufficient stock in Warehouse A for SKU-001. Available: 4, Requested: 10",
  "path": "/api/v1/inventory/transfer"
}
```

| Status Code | Meaning | Typical Cause | Recommended Frontend Action |
| :--- | :--- | :--- | :--- |
| `200` / `201` | Success | Operation completed | Render updated state / success toast |
| `400` | Bad Request | Missing required field, quantity `<= 0`, or insufficient stock | Display `error.message` directly to user |
| `401` | Unauthorized | Missing, tampered, or expired JWT | Redirect to login / refresh token |
| `403` | Forbidden | Role lacks permission (`STAFF` calling `OWNER` endpoint) | Show access denied notification |
| `404` | Not Found | Entity ID does not exist or belongs to another tenant | Show "Resource not found" state |
| `409` | Conflict | Duplicate SKU/email or illegal order status transition | Prompt user to refresh state |

---

[← Chapter 1: Architecture](01-architecture.md) | [Back to README](../README.md) | [Next: QA Verification Guide →](03-qa-playbook.md)