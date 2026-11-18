#!/usr/bin/env python3
"""
Automated end-to-end test for the inventory-saas order + geolocation flow.

It reads your own OpenAPI spec (/v3/api-docs) to find the login, warehouse,
product and stock endpoints and to build request bodies, so nothing is guessed.

Run:
    pip install requests
    python test_order_flow.py

Optional:
    --base-url http://localhost:8080
    --email bokumart@gmail.com --password Glory_123
    --company-id <uuid>              (only if the script cannot find it)
    --staff-email x --staff-password y   (tests a non-owner role gets 403)
"""
import argparse
import base64
import json
import random
import string
import subprocess
import sys
import time
import uuid

import requests

p = argparse.ArgumentParser()
p.add_argument("--base-url", default="http://localhost:8080")
p.add_argument("--email", default="boku@gmail.com")
p.add_argument("--password", default="Glory_123")
p.add_argument("--company-id", default=None)
p.add_argument("--staff-email", default=None)
p.add_argument("--staff-password", default=None)
p.add_argument("--container", default="inventory-saas")
p.add_argument("--db-user", default="postgres")
p.add_argument("--db-name", default="inventory_saas_db")
args = p.parse_args()

BASE = args.base_url.rstrip("/")
RUN = "".join(random.choices(string.ascii_uppercase + string.digits, k=5))
passed = failed = warned = 0


# ---------- small helpers ----------
def check(name, ok, detail=""):
    global passed, failed
    if ok:
        passed += 1
        print(f"  PASS  {name}")
    else:
        failed += 1
        print(f"  FAIL  {name}  {detail}")
    return ok


def warn(msg):
    global warned
    warned += 1
    print(f"  WARN  {msg}")


def section(title):
    print(f"\n== {title} ==")


def call(method, path, token=None, body=None):
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    r = requests.request(method, BASE + path, headers=headers,
                         data=json.dumps(body) if body is not None else None,
                         timeout=45)
    return r


def snippet(r):
    return f"[{r.status_code}] {r.text[:300]}"


def jwt_claims(token):
    try:
        part = token.split(".")[1]
        part += "=" * (-len(part) % 4)
        return json.loads(base64.urlsafe_b64decode(part))
    except Exception:
        return {}


def deep_find(obj, keys):
    """Find first value whose key (lowercased) is in keys, anywhere in obj."""
    if isinstance(obj, dict):
        for k, v in obj.items():
            if k.lower() in keys and isinstance(v, (str, int)):
                return v
        for v in obj.values():
            found = deep_find(v, keys)
            if found:
                return found
    elif isinstance(obj, list):
        for v in obj:
            found = deep_find(v, keys)
            if found:
                return found
    return None


def db_value(sql):
    try:
        out = subprocess.run(
            ["docker", "exec", args.container, "psql", "-U", args.db_user,
             "-d", args.db_name, "-t", "-A", "-c", sql],
            capture_output=True, text=True, timeout=20)
        return out.stdout.strip() if out.returncode == 0 else None
    except Exception:
        return None


# ---------- OpenAPI discovery ----------
try:
    spec = requests.get(BASE + "/v3/api-docs", timeout=20).json()
except Exception as e:
    print(f"Cannot read {BASE}/v3/api-docs ({e}). Is the app running?")
    sys.exit(1)

OPS = [(m.upper(), path, op) for path, item in spec.get("paths", {}).items()
       for m, op in item.items() if m in ("get", "post", "put", "patch", "delete")]


def find_op(method, include, exclude=()):
    for m, path, op in OPS:
        low = path.lower()
        if m == method and any(i in low for i in include) \
                and not any(x in low for x in exclude):
            return path, op
    return None, None


def resolve(schema):
    if schema and "$ref" in schema:
        return spec["components"]["schemas"][schema["$ref"].split("/")[-1]]
    return schema or {}


def sample(name, prop, ctx):
    n = name.lower()
    if n in ctx:
        return ctx[n]
    prop = resolve(prop)
    if "enum" in prop:
        return prop["enum"][0]
    t, fmt = prop.get("type"), prop.get("format")
    if t == "string":
        if fmt == "uuid":
            return str(uuid.uuid4())
        if "email" in n:
            return f"t{RUN.lower()}@example.com"
        if "code" in n or "sku" in n:
            return f"T-{RUN}-{random.randint(100, 999)}"
        if "address" in n or "location" in n:
            return "Ikeja, Lagos, Nigeria"
        if "desc" in n:
            return "created by test script"
        return f"Test {name} {RUN}"
    if t in ("integer", "number"):
        if "price" in n or "cost" in n:
            return 1500
        if "quant" in n or "stock" in n or "amount" in n:
            return 100
        return 10
    if t == "boolean":
        return True
    if t == "array":
        return []
    return None


def build_body(op, ctx):
    try:
        schema = op["requestBody"]["content"]["application/json"]["schema"]
    except (KeyError, TypeError):
        return {}
    props = resolve(schema).get("properties", {})
    return {k: sample(k, v, ctx) for k, v in props.items()}


# ---------- test data ----------
LAGOS_IKEJA = "Ikeja, Lagos, Nigeria"           # warehouse A (near)
ABUJA_GARKI = "Garki, Abuja, Nigeria"            # warehouse B (far)
CUSTOMER_ADDR = "Victoria Island, Lagos, Nigeria"  # closest to Ikeja

print(f"Base URL: {BASE}   Run id: {RUN}")
print("Discovered endpoints:")
for m, path, _ in OPS:
    if "/api/" in path:
        print(f"   {m:6} {path}")

# =====================================================================
section("1. Login as OWNER")
login_path, login_op = find_op("POST", ["login", "signin", "authenticate"])
if not login_path:
    login_path, login_op = find_op("POST", ["auth"], exclude=["register", "signup"])
check("login endpoint found in OpenAPI", login_path is not None,
      "(no POST path with 'login' or 'auth')")
if not login_path:
    sys.exit(1)

ctx = {"email": args.email, "username": args.email, "password": args.password}
r = call("POST", login_path, body=build_body(login_op, ctx))
check("owner login returns 200", r.status_code == 200, snippet(r))
if r.status_code != 200:
    sys.exit(1)
login_json = r.json()
TOKEN = deep_find(login_json, {"token", "accesstoken", "access_token", "jwt"})
check("token found in login response", bool(TOKEN), snippet(r))
if not TOKEN:
    sys.exit(1)

company_id = args.company_id \
    or deep_find(login_json, {"companyid", "company_id"}) \
    or deep_find(jwt_claims(TOKEN), {"companyid", "company_id"})
check("companyId found (login response / JWT / --company-id)", bool(company_id),
      "pass it with --company-id")
if not company_id:
    sys.exit(1)

# =====================================================================
section("2. ROLE checks (no token / wrong role)")
r = call("GET", "/api/orders/all")
check("no token -> GET /api/orders/all blocked (401/403)",
      r.status_code in (401, 403), snippet(r))
r = call("POST", f"/api/orders/{uuid.uuid4()}/approve",
         body={"productId": str(uuid.uuid4())})
check("no token -> approve blocked (401/403)", r.status_code in (401, 403), snippet(r))
r = call("GET", "/api/orders/all", token="not.a.real.token")
check("garbage token -> blocked (401/403)", r.status_code in (401, 403), snippet(r))

if args.staff_email:
    r = call("POST", login_path, body=build_body(
        login_op, {"email": args.staff_email, "username": args.staff_email,
                   "password": args.staff_password}))
    staff_token = deep_find(r.json(), {"token", "accesstoken", "access_token", "jwt"}) \
        if r.status_code == 200 else None
    if staff_token:
        r = call("GET", "/api/orders/all", token=staff_token)
        check("non-owner role -> GET /api/orders/all is 403", r.status_code == 403, snippet(r))
        r = call("POST", f"/api/orders/{uuid.uuid4()}/approve", token=staff_token,
                 body={"productId": str(uuid.uuid4())})
        check("non-owner role -> approve is 403", r.status_code == 403, snippet(r))
    else:
        warn("staff login failed, role test skipped")
else:
    warn("no --staff-email given: only owner vs no-login was tested")

# =====================================================================
section("3. Create 2 warehouses (geocoding)")
wh_path, wh_op = find_op("POST", ["warehouse"], exclude=["{", "public"])
check("warehouse create endpoint found", wh_path is not None)
wh = {}
for label, addr in (("A-near", LAGOS_IKEJA), ("B-far", ABUJA_GARKI)):
    body = build_body(wh_op, {"name": f"WH {label} {RUN}", "code": f"WH-{label[0]}-{RUN}",
                              "address": addr})
    r = call("POST", wh_path, TOKEN, body)
    ok = check(f"create warehouse {label} ({addr})", r.status_code in (200, 201), snippet(r))
    if ok:
        j = r.json()
        wh[label] = j
        has = j.get("latitude") is not None and j.get("longitude") is not None
        if has:
            check(f"warehouse {label} got coordinates", True)
        else:
            warn(f"warehouse {label} has NULL coordinates (Nominatim missed it)")
    time.sleep(1.2)  # Nominatim: max ~1 request/second

if len(wh) < 2:
    print("Cannot continue without both warehouses.")
    sys.exit(1)
both_geocoded = all(w.get("latitude") is not None for w in wh.values())

# =====================================================================
section("4. Create product and add stock")
pr_path, pr_op = find_op("POST", ["product"], exclude=["{", "public", "search"])
check("product create endpoint found", pr_path is not None)
product_id = None
if pr_path:
    r = call("POST", pr_path, TOKEN, build_body(pr_op, {}))
    if check("create product", r.status_code in (200, 201), snippet(r)):
        product_id = r.json().get("id")
        check("product id returned", bool(product_id))

st_path, st_op = find_op("POST", ["stock", "inventory"], exclude=["{", "public", "search"])
if st_path and product_id:
    ctx = {"productid": product_id, "warehouseid": wh["A-near"]["id"], "quantity": 500}
    r = call("POST", st_path, TOKEN, build_body(st_op, ctx))
    if r.status_code in (200, 201):
        check(f"add stock via {st_path}", True)
    else:
        warn(f"stock call {st_path} failed {snippet(r)} - approve does not need stock yet, continuing")
else:
    warn("no stock endpoint found - approve does not need stock yet, continuing")

# =====================================================================
section("5. PUBLIC customer orders (no login)")
place = f"/api/orders/public/place/{company_id}"
order_body = {"customerEmail": "customer@example.com", "quantity": 5,
              "deliveryLocation": CUSTOMER_ADDR, "requestedProductName": "cabin biscuit"}

r = call("POST", place, body=order_body)
ok = check("customer places order (201)", r.status_code == 201, snippet(r))
order1 = r.json() if ok else {}
if ok:
    check("order status is PENDING_REVIEW", order1.get("status") == "PENDING_REVIEW", snippet(r))
    check("order id returned", bool(order1.get("id")))
    check("order created date returned", bool(order1.get("createdAt")))
    if order1.get("deliveryLatitude") is None:
        warn("delivery address was not geocoded (null coordinates)")
    else:
        check("delivery address geocoded", True)
time.sleep(1.2)

r = call("POST", place, body={**order_body, "quantity": 0})
check("quantity 0 rejected (400)", r.status_code == 400, snippet(r))
r = call("POST", place, body={**order_body, "customerEmail": "not-an-email"})
check("bad email rejected (400)", r.status_code == 400, snippet(r))
r = call("POST", place, body={k: v for k, v in order_body.items() if k != "deliveryLocation"})
check("missing delivery address rejected (400)", r.status_code == 400, snippet(r))
r = call("POST", f"/api/orders/public/place/{uuid.uuid4()}", body=order_body)
check("unknown company -> 404", r.status_code == 404, snippet(r))
r = call("POST", place, body={**order_body, "selectedProductId": str(uuid.uuid4())})
check("unknown selectedProductId -> 404", r.status_code == 404, snippet(r))

if product_id:
    r = call("POST", place, body={**order_body, "selectedProductId": product_id})
    check("order with real selectedProductId (201)", r.status_code == 201, snippet(r))

# =====================================================================
section("6. OWNER inbox")
r = call("GET", "/api/orders/all", TOKEN)
check("owner GET /api/orders/all (200)", r.status_code == 200, snippet(r))
if r.status_code == 200 and order1.get("id"):
    ids = [o.get("id") for o in r.json()]
    check("new order appears in owner's inbox", order1["id"] in ids)
    created = [o.get("createdAt") for o in r.json()]
    check("inbox sorted newest first", created == sorted(created, reverse=True))

if not (order1.get("id") and product_id):
    print("\nCannot test approve without an order and a product.")
    sys.exit(1)

# =====================================================================
section("7. APPROVE")
oid = order1["id"]

r = call("POST", f"/api/orders/{oid}/approve", TOKEN, {})
check("approve without productId rejected (400)", r.status_code == 400, snippet(r))

r = call("POST", f"/api/orders/{uuid.uuid4()}/approve", TOKEN, {"productId": product_id})
check("approve unknown order -> 404", r.status_code == 404, snippet(r))

r = call("POST", f"/api/orders/{oid}/approve", TOKEN, {"productId": str(uuid.uuid4())})
check("approve with unknown productId -> 404", r.status_code == 404, snippet(r))

r = call("POST", f"/api/orders/{oid}/approve", TOKEN,
         {"productId": product_id, "warehouseId": str(uuid.uuid4())})
check("approve with unknown warehouseId -> 404", r.status_code == 404, snippet(r))

# nearest warehouse (no warehouseId)
r = call("POST", f"/api/orders/{oid}/approve", TOKEN, {"productId": product_id})
if both_geocoded and order1.get("deliveryLatitude") is not None:
    ok = check("approve with NO warehouse -> 200 (auto nearest)", r.status_code == 200, snippet(r))
    if ok:
        check("status is APPROVED", r.json().get("status") == "APPROVED", snippet(r))
        picked = db_value(f"select fulfilled_warehouse_id from customer_orders where id='{oid}'")
        if picked is None:
            warn("could not read DB (docker exec failed) - nearest warehouse not verified. "
                 "Tip: add warehouseName to the order response.")
        else:
            check("NEAREST warehouse (Ikeja) was picked for a Victoria Island order",
                  picked == wh["A-near"]["id"], f"picked={picked} expected={wh['A-near']['id']}")
else:
    warn("coordinates missing on a warehouse or the order - auto-nearest check skipped")

# approving twice
r = call("POST", f"/api/orders/{oid}/approve", TOKEN, {"productId": product_id})
check("approve same order twice is blocked (4xx)", 400 <= r.status_code < 500, snippet(r))

# explicit warehouse picked by owner (the far one)
time.sleep(1.2)
r = call("POST", place, body=order_body)
if r.status_code == 201:
    oid2 = r.json()["id"]
    r = call("POST", f"/api/orders/{oid2}/approve", TOKEN,
             {"productId": product_id, "warehouseId": wh["B-far"]["id"]})
    check("approve with owner-chosen warehouse (200)", r.status_code == 200, snippet(r))
    picked = db_value(f"select fulfilled_warehouse_id from customer_orders where id='{oid2}'")
    if picked is not None:
        check("owner's chosen warehouse (Abuja) was used, not the nearest",
              picked == wh["B-far"]["id"], f"picked={picked}")

# order whose address cannot be geocoded, no warehouse chosen
time.sleep(1.2)
r = call("POST", place, body={**order_body, "deliveryLocation": f"zzqxv nowhere {RUN}"})
if r.status_code == 201:
    oid3 = r.json()["id"]
    r = call("POST", f"/api/orders/{oid3}/approve", TOKEN, {"productId": product_id})
    check("un-geocodable address + no warehouse -> 4xx 'choose a warehouse'",
          400 <= r.status_code < 500, snippet(r))
    r = call("POST", f"/api/orders/{oid3}/approve", TOKEN,
             {"productId": product_id, "warehouseId": wh["A-near"]["id"]})
    check("same order approves when owner picks a warehouse", r.status_code == 200, snippet(r))

# =====================================================================
print(f"\n==== RESULT: {passed} passed, {failed} failed, {warned} warnings ====")
print("Note: test data (warehouses, product, orders) is left in your DB; there are no delete endpoints.")
sys.exit(1 if failed else 0)