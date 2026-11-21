#!/usr/bin/env python3
"""Exercise notification creation and every notification API endpoint.

Use an owner and two warehouse-manager accounts in the same company. Supply
their warehouse IDs and a product ID with which the owner can add stock.

    python scripts/test_notification_flow.py \
      --owner-email owner@example.com --owner-password '...' \
      --manager-a-email a@example.com --manager-a-password '...' \
      --manager-b-email b@example.com --manager-b-password '...' \
      --warehouse-a-id UUID --warehouse-b-id UUID --product-id UUID
"""
import argparse
import sys
import time
import uuid

import requests


parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--base-url", default="http://localhost:8080")
parser.add_argument("--owner-email", required=True)
parser.add_argument("--owner-password", required=True)
parser.add_argument("--manager-a-email", required=True)
parser.add_argument("--manager-a-password", required=True)
parser.add_argument("--manager-b-email", required=True)
parser.add_argument("--manager-b-password", required=True)
parser.add_argument("--warehouse-a-id", required=True)
parser.add_argument("--warehouse-b-id", required=True)
parser.add_argument("--product-id", required=True)
parser.add_argument("--timeout", type=int, default=20)
parser.add_argument("--event-wait", type=int, default=20)
args = parser.parse_args()
BASE = args.base_url.rstrip("/")
checks = []
session = requests.Session()


def check(name, ok, details=""):
    checks.append((name, bool(ok)))
    print(("PASS" if ok else "FAIL") + " " + name + (f" — {details}" if not ok and details else ""))
    return bool(ok)


def call(method, path, body=None, token=None):
    headers = {"Accept": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    try:
        return session.request(method, BASE + path, json=body, headers=headers, timeout=args.timeout)
    except requests.RequestException as exc:
        return exc


def body_of(result):
    if isinstance(result, Exception):
        return None
    try:
        return result.json()
    except ValueError:
        return None


def expect(name, result, statuses):
    if isinstance(result, Exception):
        return check(name, False, str(result))
    return check(name, result.status_code in statuses,
                 f"HTTP {result.status_code}: {result.text[:250]}")


def login(email, password, label):
    result = call("POST", "/api/auth/login", {"email": email, "password": password})
    if not expect(label, result, {200}):
        return None, None
    data = body_of(result) or {}
    access_token = data.get("accessToken") or data.get("token")
    user = data.get("user") if isinstance(data.get("user"), dict) else {}
    company = user.get("company") if isinstance(user.get("company"), dict) else {}
    check(label + " returns access token", bool(access_token), str(data)[:250])
    return access_token, company.get("id") or user.get("companyId")


def notifications(token):
    result = call("GET", "/api/notifications", token=token)
    if not expect("GET /api/notifications", result, {200}):
        return None
    data = body_of(result)
    if not isinstance(data, list):
        check("GET /api/notifications returns a list", False, str(data)[:250])
        return None
    return data


def wait_for_type(token, type_name, label, reference_id=None):
    deadline = time.monotonic() + args.event_wait
    latest = []
    while time.monotonic() < deadline:
        result = call("GET", "/api/notifications", token=token)
        data = body_of(result)
        if not isinstance(result, Exception) and result.status_code == 200 and isinstance(data, list):
            latest = data
            match = next(
                (
                    item for item in data
                    if item.get("type") == type_name
                    and (reference_id is None or str(item.get("referenceId")) == str(reference_id))
                ),
                None,
            )
            if match:
                check(label, True)
                return match
        time.sleep(0.5)
    check(label, False, f"No {type_name} notification arrived (ref={reference_id}); latest count={len(latest)}")
    return None


def unread_count(token, label):
    result = call("GET", "/api/notifications/unread-count", token=token)
    if not expect("GET /api/notifications/unread-count", result, {200}):
        return None
    count = (body_of(result) or {}).get("unreadCount")
    check(label, isinstance(count, int), str(body_of(result)))
    return count


def report():
    passed = sum(ok for _, ok in checks)
    failed = len(checks) - passed
    print(f"\nNotification flow results: {passed} passed, {failed} failed, {len(checks)} total")
    return 1 if failed else 0


def main():
    owner, company_id = login(args.owner_email, args.owner_password, "owner login")
    manager_a, _ = login(args.manager_a_email, args.manager_a_password, "manager A login")
    manager_b, _ = login(args.manager_b_email, args.manager_b_password, "manager B login")
    if not all((owner, manager_a, manager_b, company_id)):
        check("all credentials belong to a valid company", False,
              "Need valid owner and two manager accounts in one company")
        return report()

    # Ensure the source warehouse has stock before manager A accepts a request.
    result = call("POST", f"/api/stock-movement/purchase/{args.warehouse_a_id}", {
        "productId": args.product_id, "quantity": 20,
    }, owner)
    expect("seed source warehouse stock", result, {201})

    order_result = call("POST", f"/api/orders/public/place/{company_id}", {
        "customerEmail": f"notification-test-{uuid.uuid4().hex[:8]}@example.test",
        "quantity": 1,
        "deliveryLocation": "Ikeja, Lagos, Nigeria",
        "requestedProductName": "Notification test order",
        "selectedProductId": args.product_id,
    })
    order_id = None
    if expect("place customer order", order_result, {201}):
        order_id = (body_of(order_result) or {}).get("id")
    if not order_id:
        check("customer order response contains ID", False, str(body_of(order_result)))
        return report()

    owner_notification = wait_for_type(
        owner, "NEW_CUSTOMER_ORDER", "owner receives new-order notification", order_id
    )

    result = call("POST", f"/api/orders/{order_id}/approve", {
        "productId": args.product_id, "warehouseId": args.warehouse_a_id,
    }, owner)
    if expect("approve order to warehouse A", result, {200}):
        wait_for_type(manager_a, "ORDER_ASSIGNED", "manager A receives assignment notification", order_id)

    result = call("POST", f"/api/orders/{order_id}/reroute", {
        "targetWarehouseId": args.warehouse_b_id,
        "reroutingReason": "Notification flow test reroute",
    }, manager_a)
    if expect("reroute order from A to B", result, {200}):
        wait_for_type(manager_a, "ORDER_REROUTED", "manager A receives reroute notification", order_id)
        wait_for_type(manager_b, "ORDER_REROUTED", "manager B receives reroute notification", order_id)

    transfer_result = call("POST", "/api/orders/transfer-requests", {
        "sourceWarehouseId": args.warehouse_a_id,
        "productId": args.product_id,
        "quantity": 2,
    }, manager_b)
    transfer_id = None
    if expect("create transfer request B to A", transfer_result, {201}):
        transfer_id = (body_of(transfer_result) or {}).get("id")
    if not transfer_id:
        check("transfer request response contains ID", False, str(body_of(transfer_result)))
        return report()

    requested_notice = wait_for_type(
        manager_a, "TRANSFER_REQUESTED", "manager A receives transfer-request notification", transfer_id
    )
    result = call("POST", f"/api/orders/transfer/{transfer_id}/accept", token=manager_a)
    if expect("accept transfer request", result, {200}):
        wait_for_type(manager_b, "TRANSFER_ACCEPTED", "manager B receives accepted notification", transfer_id)

    declined_result = call("POST", "/api/orders/transfer-requests", {
        "sourceWarehouseId": args.warehouse_a_id,
        "productId": args.product_id,
        "quantity": 1,
    }, manager_b)
    declined_id = None
    if expect("create second transfer request", declined_result, {201}):
        declined_id = (body_of(declined_result) or {}).get("id")
    if declined_id:
        result = call("POST", f"/api/orders/transfer/{declined_id}/decline", token=manager_a)
        if expect("decline transfer request", result, {200}):
            wait_for_type(manager_b, "TRANSFER_DECLINED", "manager B receives declined notification", declined_id)
    else:
        check("second transfer request response contains ID", False, str(body_of(declined_result)))

    # Verify inbox listing and unread count, then exercise single and bulk read.
    owner_list = notifications(owner)
    a_list = notifications(manager_a)
    b_list = notifications(manager_b)
    for label, data in (("owner inbox includes order notification", owner_list),
                        ("manager A inbox includes workflow notifications", a_list),
                        ("manager B inbox includes workflow notifications", b_list)):
        check(label, isinstance(data, list) and len(data) > 0, str(data)[:250])

    owner_unread = unread_count(owner, "owner unread count is an integer")
    a_unread = unread_count(manager_a, "manager A unread count is an integer")
    b_unread = unread_count(manager_b, "manager B unread count is an integer")
    check("notification events created unread entries", all(
        isinstance(count, int) and count > 0 for count in (owner_unread, a_unread, b_unread)
    ), f"owner={owner_unread}, managerA={a_unread}, managerB={b_unread}")

    if requested_notice and requested_notice.get("id"):
        result = call("PATCH", f"/api/notifications/{requested_notice['id']}/read", token=manager_a)
        if expect("PATCH /api/notifications/{notificationId}/read", result, {200}):
            res_body = body_of(result) or {}
            check("mark-as-read response is read", (res_body.get("isRead") is True or res_body.get("read") is True))
    else:
        check("PATCH /api/notifications/{notificationId}/read", False,
              "No transfer-request notification to mark read")

    if owner_notification and owner_notification.get("id"):
        result = call("PATCH", f"/api/notifications/{owner_notification['id']}/read", token=manager_a)
        expect("cannot mark another user's notification read", result, {404})

    result = call("PATCH", "/api/notifications/read-all", token=manager_b)
    if expect("PATCH /api/notifications/read-all", result, {200}):
        check("read-all reports updated count", isinstance((body_of(result) or {}).get("updated"), int))
    result = call("PATCH", "/api/notifications/read-all", token=manager_a)
    expect("read-all for manager A", result, {200})

    after_a = unread_count(manager_a, "manager A unread count after read-all is integer")
    after_b = unread_count(manager_b, "manager B unread count after read-all is integer")
    check("read-all clears unread notifications", after_a == 0 and after_b == 0,
          f"managerA={after_a}, managerB={after_b}")

    return report()


if __name__ == "__main__":
    sys.exit(main())