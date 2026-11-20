#!/usr/bin/env python3
"""End-to-end API checks for inventory, order, transfer, and auth endpoints.

Run against a running application:
    python test_order_flow.py --base-url http://localhost:8080

The script registers a uniquely named company and leaves its test data in the
database. It does not require direct database or Docker access.
"""
import argparse
import random
import string
import sys
import time

import requests


parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--base-url", default="http://localhost:8080")
parser.add_argument("--timeout", type=int, default=30)
args = parser.parse_args()
BASE = args.base_url.rstrip("/")
RUN = "".join(random.choices(string.ascii_lowercase + string.digits, k=8))
PASSED = FAILED = SKIPPED = 0


def check(name, condition, detail=""):
    global PASSED, FAILED
    if condition:
        PASSED += 1
        print(f"PASS  {name}")
    else:
        FAILED += 1
        print(f"FAIL  {name}" + (f" — {detail}" if detail else ""))
    return condition


def skip(name, reason):
    global SKIPPED
    SKIPPED += 1
    print(f"SKIP  {name} — {reason}")


def request(method, path, token=None, body=None):
    headers = {"Accept": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    try:
        return requests.request(
            method, BASE + path, headers=headers, json=body, timeout=args.timeout
        )
    except requests.RequestException as exc:
        return exc


def status_check(name, result, expected):
    if isinstance(result, Exception):
        return check(name, False, str(result))
    return check(name, result.status_code in expected,
                 f"HTTP {result.status_code}: {result.text[:300]}")


def payload(result):
    if isinstance(result, Exception):
        return {}
    try:
        return result.json()
    except (ValueError, requests.exceptions.JSONDecodeError):
        return {}


def token_from(result):
    data = payload(result)
    return data.get("token") or data.get("accessToken") or data.get("access_token")


def identified(result):
    return payload(result).get("id")


def login(email, password, label):
    result = request("POST", "/api/auth/login", body={"email": email, "password": password})
    ok = status_check(label, result, {200})
    token = token_from(result)
    if ok and not token:
        check(f"{label}: token returned", False, str(payload(result))[:300])
    return token


def main():
    global SKIPPED
    print(f"API: {BASE} | test run: {RUN}")

    # Register an isolated tenant so this suite does not depend on preexisting
    # user credentials, warehouse IDs, or test data.
    owner_email = f"owner-{RUN}@example.test"
    password = "TestPass_123!"
    company_result = request("POST", "/api/auth/register", body={
        "name": f"API Test {RUN}", "adminEmail": owner_email, "password": password,
    })
    if not status_check("POST /api/auth/register", company_result, {201}):
        print_summary()
        return 1

    owner_token = login(owner_email, password, "POST /api/auth/login (owner)")
    if not owner_token:
        print_summary()
        return 1

    # Warehouse CRUD endpoints.
    warehouses = []
    for label, address in (("A", "Ikeja, Lagos, Nigeria"), ("B", "Abuja, Nigeria")):
        result = request("POST", "/api/warehouse/create", owner_token, {
            "name": f"Test Warehouse {label} {RUN}",
            "code": f"{RUN.upper()}-{label}",
            "address": address,
        })
        if status_check(f"POST /api/warehouse/create ({label})", result, {201}):
            wid = identified(result)
            if wid:
                warehouses.append(wid)
            else:
                check(f"warehouse {label} returns id", False, str(payload(result)))
    if len(warehouses) < 2:
        for name in ("GET /api/warehouse/all", "PUT /api/warehouse/update/{warehouseId}",
                     "POST /api/auth/managers", "POST /api/auth/staffs",
                     "POST /api/products/create", "GET /api/products/all",
                     "PUT /api/products/update/{productId}", "POST /api/inventory/create",
                     "GET /api/inventory/all", "POST /api/stock-movement/transfer",
                     "POST /api/stock-movement/sale", "POST /api/stock-movement/purchase/{warehouseId}",
                     "GET /api/stock-movement/audit", "order and transfer workflow"):
            skip(name, "requires two created warehouses")
        print_summary()
        return 1

    result = request("GET", "/api/warehouse/all", owner_token)
    status_check("GET /api/warehouse/all", result, {200})
    result = request("PUT", f"/api/warehouse/update/{warehouses[0]}", owner_token, {
        "name": f"Updated Warehouse A {RUN}", "code": f"{RUN.upper()}-A",
        "address": "Ikeja, Lagos, Nigeria",
    })
    status_check("PUT /api/warehouse/update/{warehouseId}", result, {200})

    # Owner creates two warehouse managers; each token represents its assigned
    # warehouse, which is required for stock and delivery operations.
    managers = []
    for label, warehouse_id in zip(("a", "b"), warehouses):
        email = f"manager-{label}-{RUN}@example.test"
        result = request("POST", "/api/auth/managers", owner_token, {
            "email": email, "password": password, "warehouseId": warehouse_id,
        })
        if status_check(f"POST /api/auth/managers ({label})", result, {201}):
            manager_token = login(email, password, f"POST /api/auth/login (manager {label})")
            managers.append(manager_token)
    if len(managers) < 2 or not all(managers):
        for name in ("POST /api/auth/staffs", "products, inventory, stock, order and transfer workflow"):
            skip(name, "requires manager tokens for both warehouses")
        print_summary()
        return 1
    manager_a, manager_b = managers

    result = request("POST", "/api/auth/staffs", manager_a, {
        "email": f"staff-{RUN}@example.test", "password": password,
    })
    status_check("POST /api/auth/staffs", result, {201})

    # Product CRUD and seed inventory.
    product_result = request("POST", "/api/products/create", owner_token, {
        "name": f"Test Product {RUN}", "sku": f"SKU-{RUN.upper()}",
    })
    product_ok = status_check("POST /api/products/create", product_result, {201})
    product_id = identified(product_result) if product_ok else None
    second_product_result = request("POST", "/api/products/create", owner_token, {
        "name": f"Inventory Only {RUN}", "sku": f"INV-{RUN.upper()}",
    })
    second_product_ok = status_check("POST /api/products/create (inventory seed)", second_product_result, {201})
    second_product_id = identified(second_product_result) if second_product_ok else None
    result = request("GET", "/api/products/all", owner_token)
    status_check("GET /api/products/all", result, {200})
    if product_id:
        result = request("PUT", f"/api/products/update/{product_id}", owner_token, {
            "name": f"Updated Test Product {RUN}", "sku": f"SKU-{RUN.upper()}-UPDATED",
        })
        status_check("PUT /api/products/update/{productId}", result, {200})

    # The owner purchase endpoint provisions stock in both warehouses.
    for label, warehouse_id in zip(("A", "B"), warehouses):
        result = request("POST", f"/api/stock-movement/purchase/{warehouse_id}", owner_token, {
            "productId": product_id, "quantity": 100,
        }) if product_id else None
        if result is None:
            skip(f"POST /api/stock-movement/purchase/{warehouse_id}", "product creation failed")
        else:
            status_check(f"POST /api/stock-movement/purchase ({label})", result, {201})

    if second_product_id:
        result = request("POST", "/api/inventory/create", manager_a, {
            "productId": second_product_id, "quantity": 20,
        })
        status_check("POST /api/inventory/create", result, {201})
    else:
        skip("POST /api/inventory/create", "inventory seed product creation failed")
    result = request("GET", "/api/inventory/all", manager_a)
    status_check("GET /api/inventory/all", result, {200})

    # Direct stock transfer and sale endpoints.
    if product_id:
        result = request("POST", "/api/stock-movement/transfer", manager_a, {
            "productId": product_id, "destinationWarehouseId": warehouses[1], "quantity": 2,
        })
        status_check("POST /api/stock-movement/transfer", result, {201})
        result = request("POST", "/api/stock-movement/sale", manager_b, {
            "productId": product_id, "quantity": 1,
        })
        status_check("POST /api/stock-movement/sale", result, {201})
    else:
        skip("POST /api/stock-movement/transfer", "product creation failed")
        skip("POST /api/stock-movement/sale", "product creation failed")
    result = request("GET", "/api/stock-movement/audit", owner_token)
    status_check("GET /api/stock-movement/audit", result, {200})

    # Public order placement, owner review, rerouting between warehouses, and
    # manager completion. Keep separate orders for reject and delivery paths.
    place_path = f"/api/orders/public/place/{payload(company_result).get('companyId', '')}"
    company_user = payload(company_result)
    company_id = company_user.get("companyId") or (company_user.get("company") or {}).get("id")
    if not company_id:
        # Registration returns a user; login includes the company ID in the
        # nested user object in the current API contract.
        login_result = request("POST", "/api/auth/login", body={"email": owner_email, "password": password})
        user = payload(login_result).get("user", {})
        company = user.get("company", {}) if isinstance(user, dict) else {}
        company_id = company.get("id") or user.get("companyId")
    if not company_id:
        check("company ID available for public order workflow", False, str(company_user))
        place_path = None
    else:
        place_path = f"/api/orders/public/place/{company_id}"

    order_body = {
        "customerEmail": f"customer-{RUN}@example.test", "quantity": 3,
        "deliveryLocation": "Victoria Island, Lagos, Nigeria",
        "requestedProductName": f"Test Product {RUN}", "selectedProductId": product_id,
    }
    order_ids = []
    if place_path and product_id:
        placed = request("POST", place_path, body=order_body)
        if status_check("POST /api/orders/public/place/{companyId}", placed, {201}):
            order_ids.append(identified(placed))
        # Rejection endpoint.
        rejected = request("POST", place_path, body={**order_body, "quantity": 1})
        if status_check("POST public order for rejection", rejected, {201}):
            reject_id = identified(rejected)
            result = request("POST", f"/api/orders/{reject_id}/reject", owner_token,
                             {"rejectionReason": "Automated endpoint test"})
            status_check("POST /api/orders/{orderId}/reject", result, {200})
        # A second order is approved then rerouted and delivered.
        delivery = request("POST", place_path, body=order_body)
        if status_check("POST public order for delivery", delivery, {201}):
            order_ids.append(identified(delivery))
    else:
        for name in ("POST /api/orders/public/place/{companyId}",
                     "POST /api/orders/{orderId}/reject", "POST /api/orders/{orderId}/approve",
                     "GET /api/orders/all", "GET /api/orders/warehouse",
                     "POST /api/orders/{orderId}/reroute", "POST /api/orders/{orderId}/complete"):
            skip(name, "company ID or product unavailable")

    if place_path and product_id:
        result = request("GET", "/api/orders/all", owner_token)
        status_check("GET /api/orders/all", result, {200})
        # Approve the latest pending order into warehouse A.
        delivery_id = order_ids[-1] if order_ids else None
        if delivery_id:
            result = request("POST", f"/api/orders/{delivery_id}/approve", owner_token, {
                "productId": product_id, "warehouseId": warehouses[0],
            })
            if status_check("POST /api/orders/{orderId}/approve", result, {200}):
                result = request("GET", "/api/orders/warehouse", manager_a)
                status_check("GET /api/orders/warehouse (source warehouse)", result, {200})
                result = request("POST", f"/api/orders/{delivery_id}/reroute", manager_a, {
                    "targetWarehouseId": warehouses[1],
                    "reroutingReason": "Automated rerouting endpoint test",
                })
                if status_check("POST /api/orders/{orderId}/reroute", result, {200}):
                    result = request("GET", "/api/orders/warehouse", manager_b)
                    status_check("GET /api/orders/warehouse (target warehouse)", result, {200})
                    result = request("POST", f"/api/orders/{delivery_id}/complete", manager_b)
                    status_check("POST /api/orders/{orderId}/complete", result, {200})
        else:
            for name in ("POST /api/orders/{orderId}/approve", "GET /api/orders/warehouse",
                         "POST /api/orders/{orderId}/reroute", "POST /api/orders/{orderId}/complete"):
                skip(name, "order creation failed")

    # Transfer request: B requests stock from A, A accepts; a second request
    # covers decline. Both warehouse list endpoints are checked.
    if product_id:
        transfer = request("POST", "/api/orders/transfer-requests", manager_b, {
            "sourceWarehouseId": warehouses[0], "productId": product_id, "quantity": 2,
        })
        transfer_id = identified(transfer) if status_check(
            "POST /api/orders/transfer-requests", transfer, {201}) else None
        result = request("GET", "/api/orders/incoming-requests", manager_a)
        status_check("GET /api/orders/incoming-requests", result, {200})
        result = request("GET", "/api/orders/transfer-requests", manager_b)
        status_check("GET /api/orders/transfer-requests", result, {200, 201})
        if transfer_id:
            result = request("POST", f"/api/orders/transfer/{transfer_id}/accept", manager_a)
            status_check("POST /api/orders/transfer/{requestId}/accept", result, {200})
        else:
            skip("POST /api/orders/transfer/{requestId}/accept", "transfer request creation failed")

        declined = request("POST", "/api/orders/transfer-requests", manager_b, {
            "sourceWarehouseId": warehouses[0], "productId": product_id, "quantity": 1,
        })
        decline_id = identified(declined) if status_check(
            "POST transfer request for decline", declined, {201}) else None
        if decline_id:
            result = request("POST", f"/api/orders/transfer/{decline_id}/decline", manager_a)
            status_check("POST /api/orders/transfer/{requestId}/decline", result, {200})
        else:
            skip("POST /api/orders/transfer/{requestId}/decline", "transfer request creation failed")
    else:
        for name in ("POST /api/orders/transfer-requests", "GET /api/orders/incoming-requests",
                     "GET /api/orders/transfer-requests", "POST /api/orders/transfer/{requestId}/accept",
                     "POST /api/orders/transfer/{requestId}/decline"):
            skip(name, "product creation failed")

    print_summary()
    return 1 if FAILED else 0


def print_summary():
    total = PASSED + FAILED
    print("\n===== ENDPOINT TEST RESULTS =====")
    print(f"Passed: {PASSED}")
    print(f"Failed: {FAILED}")
    print(f"Skipped: {SKIPPED}")
    print(f"Executed checks: {total}")


if __name__ == "__main__":
    try:
        sys.exit(main())
    except KeyboardInterrupt:
        print("\nInterrupted.")
        print_summary()
        sys.exit(130)
