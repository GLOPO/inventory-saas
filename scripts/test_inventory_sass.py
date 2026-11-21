#!/usr/bin/env python3
"""Full end-to-end API suite for inventory-saas configured for Mailtrap.

Run against the running Spring Boot app:
    python test_inventory_saas.py

All emails are sent to unique '@example.test' dummy addresses per run and
captured in your Mailtrap inbox. Paste the 'Set Your Password' URLs from
Mailtrap when prompted (or pass --manager-a-setup-url / --manager-b-setup-url /
--staff-setup-url).
"""
import argparse
import getpass
import secrets
import sys
import time
import uuid
from urllib.parse import parse_qs, urlparse

import requests


parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--base-url", default="http://localhost:8080")
parser.add_argument("--timeout", type=int, default=25)
parser.add_argument("--event-wait", type=int, default=20)
parser.add_argument("--mail-pause", type=float, default=2.0,
                    help="Seconds to pause between back-to-back emails for Mailtrap rate limits")
parser.add_argument("--manager-a-setup-url")
parser.add_argument("--manager-b-setup-url")
parser.add_argument("--staff-setup-url")
args = parser.parse_args()

BASE = args.base_url.rstrip("/")
RUN = uuid.uuid4().hex[:8]
session = requests.Session()
results = []
setup_password_val = "Setup_" + secrets.token_urlsafe(10)
changed_password_val = "Changed_" + secrets.token_urlsafe(10)
owner_password_val = "Owner_" + secrets.token_urlsafe(10)


def record(name, passed, detail=""):
    results.append((name, bool(passed), detail if not passed else ""))
    print(("PASS" if passed else "FAIL") + " " + name + (f" — {detail}" if not passed and detail else ""))
    return bool(passed)


def skip(name, reason):
    results.append((name, None, reason))
    print(f"SKIP {name} — {reason}")


def call(method, path, body=None, token=None):
    headers = {"Accept": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    try:
        return session.request(method, BASE + path, json=body, headers=headers, timeout=args.timeout)
    except requests.RequestException as exc:
        return exc


def data_of(response):
    if isinstance(response, Exception):
        return None
    try:
        return response.json()
    except ValueError:
        return None


def expect(name, response, statuses):
    if isinstance(response, Exception):
        return record(name, False, str(response))
    return record(name, response.status_code in statuses,
                  f"HTTP {response.status_code}: {response.text[:250]}")


def endpoint(name, method, path, statuses, body=None, token=None):
    response = call(method, path, body, token)
    return (data_of(response) if expect(name, response, statuses) else None), response


def login(email, password, label):
    response = call("POST", "/api/auth/login", {"email": email, "password": password})
    if not expect(label, response, {200}):
        return None, None
    payload = data_of(response) or {}
    token = payload.get("accessToken") or payload.get("token")
    user = payload.get("user") if isinstance(payload.get("user"), dict) else {}
    company = user.get("company") if isinstance(user.get("company"), dict) else {}
    record(label + " returns token", bool(token), str(payload)[:250])
    return token, company.get("id") or user.get("companyId")


def complete_setup_password(email, setup_url, label):
    if not setup_url and sys.stdin.isatty():
        print(f"\nOpen Mailtrap inbox for {email} and copy the 'Set Your Password' URL.")
        setup_url = getpass.getpass(f"Paste {label} onboarding setup URL (input hidden): ")
    token = parse_qs(urlparse(setup_url or "").query).get("token", [None])[0]
    if not token:
        skip(label + " password setup", "No usable setup URL was provided")
        return None

    response = call("POST", "/api/auth/setup-password", {
        "token": "invalid-" + secrets.token_urlsafe(8),
        "password": setup_password_val,
        "confirmPassword": setup_password_val,
    })
    expect(label + " setup rejects invalid token", response, {400})

    response = call("POST", "/api/auth/setup-password", {
        "token": token,
        "password": setup_password_val,
        "confirmPassword": setup_password_val + "-mismatch",
    })
    expect(label + " setup rejects mismatched confirmation", response, {400})

    response = call("POST", "/api/auth/setup-password", {
        "token": token,
        "password": setup_password_val,
        "confirmPassword": setup_password_val,
    })
    if not expect(label + " setup accepts valid one-time token", response, {200}):
        return None

    response = call("POST", "/api/auth/setup-password", {
        "token": token,
        "password": setup_password_val,
        "confirmPassword": setup_password_val,
    })
    expect(label + " setup rejects token replay", response, {400})
    return login(email, setup_password_val, label + " login after setup")[0]


def wait_for_type(token, type_name, label, reference_id=None):
    until = time.monotonic() + args.event_wait
    while time.monotonic() < until:
        response = call("GET", "/api/notifications", token=token)
        payload = data_of(response)
        if not isinstance(response, Exception) and response.status_code == 200 and isinstance(payload, list):
            match = next(
                (
                    item for item in payload
                    if item.get("type") == type_name
                    and (reference_id is None or str(item.get("referenceId")) == str(reference_id))
                ),
                None,
            )
            if match:
                record(label, True)
                return match
        time.sleep(0.5)
    record(label, False, f"No {type_name} notification arrived within {args.event_wait}s")
    return None


def response_id(payload, label):
    value = payload.get("id") if isinstance(payload, dict) else None
    record(label + " response contains ID", bool(value), str(payload)[:250])
    return value


def report():
    passed = sum(ok is True for _, ok, _ in results)
    failed = sum(ok is False for _, ok, _ in results)
    skipped = sum(ok is None for _, ok, _ in results)
    total = passed + failed
    print("\n" + "=" * 62)
    print(f"API suite: {passed} passed, {failed} failed, {skipped} skipped ({total} executed)")
    print(f"Run ID: {RUN}")
    print("=" * 62)
    return 1 if failed or skipped else 0


def main():
    owner_email = f"owner-{RUN}@example.test"
    manager_a_email = f"manager-a-{RUN}@example.test"
    manager_b_email = f"manager-b-{RUN}@example.test"
    staff_email = f"staff-a-{RUN}@example.test"

    print(f"Starting Inventory SaaS E2E Test (Run ID: {RUN})")
    print(f"  Owner:     {owner_email}")
    print(f"  Manager A: {manager_a_email}")
    print(f"  Manager B: {manager_b_email}")
    print(f"  Staff:     {staff_email}\n")

    owner, company_result = endpoint("POST /api/auth/register", "POST", "/api/auth/register", {201}, {
        "name": f"API Test Company {RUN}",
        "adminEmail": owner_email,
        "password": owner_password_val,
    })
    if owner is None:
        return report()

    owner_token, company_id = login(owner_email, owner_password_val, "POST /api/auth/login owner")
    company_id = company_id or ((owner.get("company") or {}).get("id") if isinstance(owner, dict) else None)
    if not owner_token or not company_id:
        record("owner token and company ID available", False, str(company_result)[:250])
        return report()

    response = call("POST", "/api/auth/login", {"email": owner_email, "password": "incorrect"})
    expect("login rejects incorrect password", response, {401, 403})
    response = call("GET", "/api/inventory/all")
    expect("protected inventory rejects anonymous request", response, {401, 403})
    response = call("POST", "/api/auth/register", {
        "name": "", "adminEmail": f"invalid-{RUN}", "password": "short",
    })
    expect("registration rejects invalid fields", response, {400})

    # Warehouse CRUD & Geocoding
    warehouse_a, _ = endpoint("POST /api/warehouse/create A", "POST", "/api/warehouse/create", {201}, {
        "name": f"Warehouse A {RUN}", "code": f"A-{RUN}", "address": "Ikeja, Lagos, Nigeria",
    }, owner_token)
    warehouse_b, _ = endpoint("POST /api/warehouse/create B", "POST", "/api/warehouse/create", {201}, {
        "name": f"Warehouse B {RUN}", "code": f"B-{RUN}", "address": "Abuja, Nigeria",
    }, owner_token)
    wa_id = response_id(warehouse_a, "warehouse A") if warehouse_a else None
    wb_id = response_id(warehouse_b, "warehouse B") if warehouse_b else None

    data, _ = endpoint("GET /api/warehouse/all", "GET", "/api/warehouse/all", {200}, token=owner_token)
    record("warehouse list contains created warehouses", isinstance(data, list) and
           {wa_id, wb_id}.issubset({w.get("id") for w in data if isinstance(w, dict)}), str(data)[:250])
    if wb_id:
        endpoint("PUT /api/warehouse/update/{warehouseId}", "PUT", f"/api/warehouse/update/{wb_id}", {200}, {
            "name": f"Warehouse B Updated {RUN}", "code": f"B-{RUN}", "address": "Abuja, Nigeria",
        }, owner_token)
    else:
        skip("PUT /api/warehouse/update/{warehouseId}", "Warehouse B creation failed")

    endpoint("warehouse rejects duplicate code", "POST", "/api/warehouse/create", {400, 409}, {
        "name": f"Duplicate Warehouse {RUN}", "code": f"A-{RUN}", "address": "Ikeja, Lagos, Nigeria",
    }, owner_token)
    endpoint("warehouse update rejects unknown ID", "PUT", f"/api/warehouse/update/{uuid.uuid4()}", {404}, {
        "name": "Missing Warehouse", "code": f"M-{RUN}", "address": "Abuja, Nigeria",
    }, owner_token)

    # Product CRUD
    product, _ = endpoint("POST /api/products/create", "POST", "/api/products/create", {201}, {
        "name": f"Test Product {RUN}", "sku": f"SKU-{RUN}",
    }, owner_token)
    product_id = response_id(product, "product") if product else None

    inventory_product, _ = endpoint("POST /api/products/create inventory seed", "POST", "/api/products/create", {201}, {
        "name": f"Inventory Seed {RUN}", "sku": f"INV-{RUN}",
    }, owner_token)
    inventory_product_id = response_id(inventory_product, "inventory seed product") if inventory_product else None

    data, _ = endpoint("GET /api/products/all", "GET", "/api/products/all", {200}, token=owner_token)
    record("product list returns a list", isinstance(data, list), str(data)[:250])

    if product_id:
        endpoint("PUT /api/products/update/{productId}", "PUT", f"/api/products/update/{product_id}", {200}, {
            "name": f"Updated Product {RUN}", "sku": f"SKU-{RUN}-UPDATED",
        }, owner_token)
        endpoint("product rejects duplicate SKU", "POST", "/api/products/create", {400, 409}, {
            "name": f"Duplicate {RUN}", "sku": f"SKU-{RUN}-UPDATED",
        }, owner_token)
        endpoint("product update rejects unknown ID", "PUT", f"/api/products/update/{uuid.uuid4()}", {404}, {
            "name": "Missing Product", "sku": f"MISSING-{RUN}",
        }, owner_token)
    endpoint("product rejects invalid fields", "POST", "/api/products/create", {400}, {
        "name": "", "sku": "",
    }, owner_token)

    # Provision Managers & Staff (emails captured by Mailtrap with rate-limit pause)
    if wa_id:
        manager_a, _ = endpoint("POST /api/auth/managers A", "POST", "/api/auth/managers", {201}, {
            "email": manager_a_email, "password": "unused-by-provisioning", "warehouseId": wa_id,
        }, owner_token)
        record("manager A requires setup password", isinstance(manager_a, dict) and
               manager_a.get("mustChangePassword") is True, str(manager_a))
    else:
        manager_a = None
        skip("POST /api/auth/managers A", "Warehouse A creation failed")

    if args.mail_pause > 0:
        time.sleep(args.mail_pause)

    if wb_id:
        manager_b, _ = endpoint("POST /api/auth/managers B", "POST", "/api/auth/managers", {201}, {
            "email": manager_b_email, "password": "unused-by-provisioning", "warehouseId": wb_id,
        }, owner_token)
        record("manager B requires setup password", isinstance(manager_b, dict) and
               manager_b.get("mustChangePassword") is True, str(manager_b))
    else:
        manager_b = None
        skip("POST /api/auth/managers B", "Warehouse B creation failed")

    if args.mail_pause > 0:
        time.sleep(args.mail_pause)

    manager_a_token = complete_setup_password(manager_a_email, args.manager_a_setup_url, "manager A") if manager_a else None
    manager_b_token = complete_setup_password(manager_b_email, args.manager_b_setup_url, "manager B") if manager_b else None

    if manager_a_token:
        staff, _ = endpoint("POST /api/auth/staffs", "POST", "/api/auth/staffs", {201}, {
            "email": staff_email, "password": "unused-by-provisioning",
        }, manager_a_token)
        record("staff requires setup password", isinstance(staff, dict) and
               staff.get("mustChangePassword") is True, str(staff))
        if args.mail_pause > 0:
            time.sleep(args.mail_pause)
    else:
        staff = None
        skip("POST /api/auth/staffs", "Manager A setup was not completed")

    staff_token = complete_setup_password(staff_email, args.staff_setup_url, "staff") if staff else None

    # RBAC Checks
    if staff_token:
        endpoint("staff cannot create warehouse", "POST", "/api/warehouse/create", {403}, {
            "name": "Forbidden", "code": f"NO-{RUN}", "address": "Ikeja, Lagos",
        }, staff_token)
        endpoint("staff cannot provision staff", "POST", "/api/auth/staffs", {403}, {
            "email": f"forbidden-staff-{RUN}@example.test", "password": "unused-password",
        }, staff_token)
    else:
        skip("staff role checks", "Staff setup was not completed")

    if manager_a_token:
        endpoint("manager cannot provision manager", "POST", "/api/auth/managers", {403}, {
            "email": f"forbidden-manager-{RUN}@example.test", "password": "unused-password",
            "warehouseId": wa_id,
        }, manager_a_token)
    else:
        skip("manager role check", "Manager A setup was not completed")

    # Stock & Inventory Operations
    if wa_id and product_id:
        endpoint("POST /api/stock-movement/purchase A", "POST",
                 f"/api/stock-movement/purchase/{wa_id}", {201},
                 {"productId": product_id, "quantity": 100}, owner_token)
    else:
        skip("POST /api/stock-movement/purchase A", "Warehouse or product unavailable")

    if wb_id and product_id:
        endpoint("POST /api/stock-movement/purchase B", "POST",
                 f"/api/stock-movement/purchase/{wb_id}", {201},
                 {"productId": product_id, "quantity": 100}, owner_token)
    else:
        skip("POST /api/stock-movement/purchase B", "Warehouse or product unavailable")

    if manager_a_token and inventory_product_id:
        endpoint("POST /api/inventory/create", "POST", "/api/inventory/create", {201}, {
            "productId": inventory_product_id, "quantity": 20,
        }, manager_a_token)
        endpoint("inventory rejects duplicate warehouse/product row", "POST", "/api/inventory/create", {400}, {
            "productId": inventory_product_id, "quantity": 1,
        }, manager_a_token)
    else:
        skip("POST /api/inventory/create", "Manager A or seed product unavailable")

    if manager_a_token:
        data, _ = endpoint("GET /api/inventory/all", "GET", "/api/inventory/all", {200}, token=manager_a_token)
        record("inventory endpoint returns list", isinstance(data, list), str(data)[:250])
    else:
        skip("GET /api/inventory/all", "Manager A setup was not completed")

    if manager_a_token and product_id and wb_id:
        endpoint("POST /api/stock-movement/transfer", "POST", "/api/stock-movement/transfer", {201}, {
            "productId": product_id, "destinationWarehouseId": wb_id, "quantity": 5,
        }, manager_a_token)
    else:
        skip("POST /api/stock-movement/transfer", "Manager, product, or target warehouse unavailable")

    if staff_token and product_id:
        endpoint("staff cannot record sale", "POST", "/api/stock-movement/sale", {403}, {
            "productId": product_id, "quantity": 10,
        }, staff_token)
    else:
        skip("staff sale RBAC check", "Staff or product unavailable")

    if manager_a_token and product_id:
        endpoint("POST /api/stock-movement/sale", "POST", "/api/stock-movement/sale", {201}, {
            "productId": product_id, "quantity": 10,
        }, manager_a_token)
        endpoint("sale rejects insufficient stock", "POST", "/api/stock-movement/sale", {400}, {
            "productId": product_id, "quantity": 10000,
        }, manager_a_token)
        endpoint("sale rejects unknown product", "POST", "/api/stock-movement/sale", {404}, {
            "productId": str(uuid.uuid4()), "quantity": 1,
        }, manager_a_token)
    else:
        skip("POST /api/stock-movement/sale", "Manager A or product unavailable")

    data, _ = endpoint("GET /api/stock-movement/audit", "GET", "/api/stock-movement/audit", {200}, token=owner_token)
    record("audit endpoint returns list", isinstance(data, list), str(data)[:250])

    # Authenticated Change Password Flow
    if manager_a_token:
        response = call("POST", "/api/auth/change-password", {
            "currentPassword": "wrong-current-password",
            "newPassword": changed_password_val,
            "confirmPassword": changed_password_val,
        }, manager_a_token)
        expect("change-password rejects wrong current password", response, {400, 401, 403})

        response = call("POST", "/api/auth/change-password", {
            "currentPassword": setup_password_val,
            "newPassword": changed_password_val,
            "confirmPassword": changed_password_val + "-wrong",
        }, manager_a_token)
        expect("change-password rejects mismatch", response, {400})

        response = call("POST", "/api/auth/change-password", {
            "currentPassword": setup_password_val,
            "newPassword": changed_password_val,
            "confirmPassword": changed_password_val,
        }, manager_a_token)
        if expect("POST /api/auth/change-password", response, {200}):
            new_token, _ = login(manager_a_email, changed_password_val, "manager A login after password change")
            manager_a_token = new_token or manager_a_token
            response = call("POST", "/api/auth/login", {
                "email": manager_a_email, "password": setup_password_val,
            })
            expect("old manager password no longer works", response, {401, 403})
    else:
        skip("password change scenarios", "Manager A setup was not completed")

    # Public Customer Orders, Triage, Reroute & Delivery Completion
    order_id = rejected_id = None
    if product_id:
        order_body = {
            "customerEmail": f"customer-{RUN}@example.test",
            "quantity": 2,
            "deliveryLocation": "Ikeja, Lagos, Nigeria",
            "requestedProductName": f"Updated Product {RUN}",
            "selectedProductId": product_id,
        }
        placed, _ = endpoint("POST /api/orders/public/place/{companyId}", "POST",
                             f"/api/orders/public/place/{company_id}", {201}, order_body)
        order_id = response_id(placed, "delivery order") if placed else None

        endpoint("public order rejects missing required fields", "POST",
                 f"/api/orders/public/place/{company_id}", {400}, {
                     "customerEmail": "", "quantity": 0, "deliveryLocation": "",
                     "requestedProductName": "",
                 })
        endpoint("public order rejects unknown company", "POST",
                 f"/api/orders/public/place/{uuid.uuid4()}", {404}, {
                     "customerEmail": f"customer-{RUN}@example.test", "quantity": 1,
                     "deliveryLocation": "Ikeja, Lagos", "requestedProductName": "Unknown company",
                 })
        endpoint("public order rejects unknown product ID", "POST",
                 f"/api/orders/public/place/{company_id}", {404}, {
                     "customerEmail": f"customer-{RUN}@example.test", "quantity": 1,
                     "deliveryLocation": "Ikeja, Lagos", "requestedProductName": "Missing product",
                     "selectedProductId": str(uuid.uuid4()),
                 })

        rejected, _ = endpoint("place order to reject", "POST",
                               f"/api/orders/public/place/{company_id}", {201}, order_body)
        rejected_id = response_id(rejected, "rejected order") if rejected else None

        data, _ = endpoint("GET /api/orders/all", "GET", "/api/orders/all", {200}, token=owner_token)
        record("owner order inbox returns list", isinstance(data, list), str(data)[:250])

        if rejected_id:
            endpoint("POST /api/orders/{orderId}/reject", "POST", f"/api/orders/{rejected_id}/reject", {200}, {
                "rejectionReason": "Automated scenario rejection",
            }, owner_token)
            endpoint("reject cannot act on order twice", "POST", f"/api/orders/{rejected_id}/reject", {400}, {
                "rejectionReason": "Second rejection attempt",
            }, owner_token)
        else:
            skip("POST /api/orders/{orderId}/reject", "Order placement failed")

        if order_id and wa_id:
            endpoint("approve rejects missing product ID", "POST", f"/api/orders/{order_id}/approve", {400}, {}, owner_token)
            endpoint("approve rejects unknown order ID", "POST", f"/api/orders/{uuid.uuid4()}/approve", {404}, {
                "productId": product_id,
            }, owner_token)
            endpoint("POST /api/orders/{orderId}/approve", "POST", f"/api/orders/{order_id}/approve", {200}, {
                "productId": product_id, "warehouseId": wa_id,
            }, owner_token)
        else:
            skip("POST /api/orders/{orderId}/approve", "Order or warehouse unavailable")

        if manager_a_token and order_id:
            data, _ = endpoint("GET /api/orders/warehouse A", "GET", "/api/orders/warehouse", {200}, token=manager_a_token)
            record("warehouse A order list returns list", isinstance(data, list), str(data)[:250])
            if wb_id:
                endpoint("POST /api/orders/{orderId}/reroute", "POST", f"/api/orders/{order_id}/reroute", {200}, {
                    "targetWarehouseId": wb_id, "reroutingReason": "Automated reroute scenario",
                }, manager_a_token)
            else:
                skip("POST /api/orders/{orderId}/reroute", "Warehouse B unavailable")
        else:
            skip("GET /api/orders/warehouse", "Manager A or approved order unavailable")
            skip("POST /api/orders/{orderId}/reroute", "Manager A or approved order unavailable")

        if manager_b_token and order_id:
            data, _ = endpoint("GET /api/orders/warehouse B", "GET", "/api/orders/warehouse", {200}, token=manager_b_token)
            record("warehouse B order list returns list", isinstance(data, list), str(data)[:250])
            endpoint("reroute rejects current warehouse", "POST", f"/api/orders/{order_id}/reroute", {400}, {
                "targetWarehouseId": wb_id, "reroutingReason": "Cannot reroute to same warehouse",
            }, manager_b_token)
            if manager_a_token:
                endpoint("previous warehouse manager cannot complete rerouted order", "POST",
                         f"/api/orders/{order_id}/complete", {400}, token=manager_a_token)
            endpoint("POST /api/orders/{orderId}/complete", "POST", f"/api/orders/{order_id}/complete", {200}, token=manager_b_token)
            endpoint("completed order cannot be completed twice", "POST", f"/api/orders/{order_id}/complete", {400}, token=manager_b_token)
        else:
            skip("GET /api/orders/warehouse B", "Manager B or rerouted order unavailable")
            skip("POST /api/orders/{orderId}/complete", "Manager B or rerouted order unavailable")
    else:
        for name in ("POST /api/orders/public/place/{companyId}", "GET /api/orders/all",
                     "POST /api/orders/{orderId}/reject", "POST /api/orders/{orderId}/approve",
                     "GET /api/orders/warehouse", "POST /api/orders/{orderId}/reroute",
                     "POST /api/orders/{orderId}/complete"):
            skip(name, "Product creation failed")

    # Inter-Warehouse Stock Transfer Requests
    transfer_id = decline_id = None
    if product_id and manager_a_token and manager_b_token and wa_id:
        transfer, _ = endpoint("POST /api/orders/transfer-requests", "POST", "/api/orders/transfer-requests", {201}, {
            "sourceWarehouseId": wa_id, "productId": product_id, "quantity": 2,
        }, manager_b_token)
        transfer_id = response_id(transfer, "transfer") if transfer else None

        data, _ = endpoint("GET /api/orders/incoming-requests", "GET", "/api/orders/incoming-requests", {200}, token=manager_a_token)
        record("incoming transfer list returns list", isinstance(data, list), str(data)[:250])
        data, _ = endpoint("GET /api/orders/transfer-requests", "GET", "/api/orders/transfer-requests", {200, 201}, token=manager_b_token)
        record("my transfer list returns list", isinstance(data, list), str(data)[:250])

        if transfer_id:
            endpoint("transfer cannot be accepted by destination manager", "POST",
                     f"/api/orders/transfer/{transfer_id}/accept", {400, 403}, token=manager_b_token)
            endpoint("POST /api/orders/transfer/{requestId}/accept", "POST",
                     f"/api/orders/transfer/{transfer_id}/accept", {200}, token=manager_a_token)
            endpoint("accepted transfer cannot be declined", "POST",
                     f"/api/orders/transfer/{transfer_id}/decline", {400}, token=manager_a_token)
        else:
            skip("POST /api/orders/transfer/{requestId}/accept", "Transfer request failed")

        declined, _ = endpoint("create transfer for decline", "POST", "/api/orders/transfer-requests", {201}, {
            "sourceWarehouseId": wa_id, "productId": product_id, "quantity": 1,
        }, manager_b_token)
        decline_id = response_id(declined, "declined transfer") if declined else None
        if decline_id:
            endpoint("POST /api/orders/transfer/{requestId}/decline", "POST",
                     f"/api/orders/transfer/{decline_id}/decline", {200}, token=manager_a_token)
        else:
            skip("POST /api/orders/transfer/{requestId}/decline", "Transfer request failed")

        endpoint("transfer request rejects own warehouse", "POST", "/api/orders/transfer-requests", {400}, {
            "sourceWarehouseId": wb_id, "productId": product_id, "quantity": 1,
        }, manager_b_token)
    else:
        for name in ("POST /api/orders/transfer-requests", "GET /api/orders/incoming-requests",
                     "GET /api/orders/transfer-requests", "POST /api/orders/transfer/{requestId}/accept",
                     "POST /api/orders/transfer/{requestId}/decline"):
            skip(name, "Transfer workflow prerequisites unavailable")

    # In-App Notifications Verification
    if owner_token and order_id:
        wait_for_type(owner_token, "NEW_CUSTOMER_ORDER", "owner receives customer-order notification", order_id)
    if manager_a_token and order_id:
        wait_for_type(manager_a_token, "ORDER_ASSIGNED", "manager A receives order-assigned notification", order_id)
        wait_for_type(manager_a_token, "ORDER_REROUTED", "manager A receives order-rerouted notification", order_id)
    if manager_a_token and transfer_id:
        wait_for_type(manager_a_token, "TRANSFER_REQUESTED", "manager A receives transfer-request notification", transfer_id)
    if manager_b_token and order_id:
        wait_for_type(manager_b_token, "ORDER_REROUTED", "manager B receives order-rerouted notification", order_id)
    if manager_b_token and transfer_id:
        wait_for_type(manager_b_token, "TRANSFER_ACCEPTED", "manager B receives accepted-transfer notification", transfer_id)
    if manager_b_token and decline_id:
        wait_for_type(manager_b_token, "TRANSFER_DECLINED", "manager B receives declined-transfer notification", decline_id)

    for label, token in (("owner", owner_token), ("manager A", manager_a_token), ("manager B", manager_b_token)):
        if token:
            data, _ = endpoint(f"GET /api/notifications ({label})", "GET", "/api/notifications", {200}, token=token)
            record(f"{label} notification inbox is a list", isinstance(data, list), str(data)[:250])
            count, _ = endpoint(f"GET /api/notifications/unread-count ({label})", "GET",
                                "/api/notifications/unread-count", {200}, token=token)
            record(f"{label} unread count field present", isinstance(count, dict) and isinstance(count.get("unreadCount"), int), str(count))
        else:
            skip(f"notification reads ({label})", "User token unavailable")

    if manager_a_token and transfer_id:
        requested = wait_for_type(manager_a_token, "TRANSFER_REQUESTED", "manager A receives transfer notification", transfer_id)
        if requested and requested.get("id"):
            res_data, _ = endpoint("PATCH /api/notifications/{notificationId}/read", "PATCH",
                                   f"/api/notifications/{requested['id']}/read", {200}, token=manager_a_token)
            if isinstance(res_data, dict):
                record("mark-as-read response sets read flag",
                       res_data.get("isRead") is True or res_data.get("read") is True, str(res_data))
        else:
            skip("PATCH /api/notifications/{notificationId}/read", "No transfer notification was created")
    else:
        skip("PATCH /api/notifications/{notificationId}/read", "Manager A or transfer workflow unavailable")

    if manager_b_token:
        endpoint("PATCH /api/notifications/read-all", "PATCH", "/api/notifications/read-all", {200}, token=manager_b_token)
        if manager_a_token:
            endpoint("PATCH read-all for manager A", "PATCH", "/api/notifications/read-all", {200}, token=manager_a_token)
        else:
            skip("PATCH read-all for manager A", "Manager A token unavailable")
        count, _ = endpoint("verify read-all", "GET", "/api/notifications/unread-count", {200}, token=manager_b_token)
        record("read-all leaves zero unread notifications", isinstance(count, dict) and count.get("unreadCount") == 0, str(count))
    else:
        skip("PATCH /api/notifications/read-all", "Manager B token unavailable")

    # Cross-User Notification Isolation
    if owner_token and manager_a_token:
        owner_notifications, _ = endpoint("owner notification list for isolation", "GET", "/api/notifications", {200}, token=owner_token)
        foreign = next((n for n in owner_notifications if isinstance(n, dict) and n.get("id")), None) if isinstance(owner_notifications, list) else None
        if foreign:
            endpoint("notification cannot be read by another user", "PATCH",
                     f"/api/notifications/{foreign['id']}/read", {404}, token=manager_a_token)
        else:
            skip("notification cross-user isolation", "Owner has no notifications")
        endpoint("marking unknown notification returns 404", "PATCH",
                 f"/api/notifications/{uuid.uuid4()}/read", {404}, token=manager_a_token)

    # Cross-Tenant Isolation Checks
    rival_email = f"rival-{RUN}@example.test"
    rival_password = "Rival_" + secrets.token_urlsafe(10)
    _, _ = endpoint("register rival tenant", "POST", "/api/auth/register", {201}, {
        "name": f"Rival Company {RUN}", "adminEmail": rival_email, "password": rival_password,
    })
    rival_token, _ = login(rival_email, rival_password, "POST /api/auth/login rival")
    if rival_token:
        warehouses, _ = endpoint("rival warehouse list", "GET", "/api/warehouse/all", {200}, token=rival_token)
        a_ids = {wa_id, wb_id} - {None}
        rival_ids = {item.get("id") for item in warehouses if isinstance(item, dict)} if isinstance(warehouses, list) else set()
        record("rival tenant cannot see test company warehouses", a_ids.isdisjoint(rival_ids), str(rival_ids))
        if product_id:
            endpoint("rival cannot update another tenant product", "PUT", f"/api/products/update/{product_id}", {403, 404}, {
                "name": "Cross-tenant attempt", "sku": f"X-{RUN}",
            }, rival_token)

    return report()


if __name__ == "__main__":
    try:
        sys.exit(main())
    except KeyboardInterrupt:
        print("\nInterrupted by user")
        sys.exit(report())