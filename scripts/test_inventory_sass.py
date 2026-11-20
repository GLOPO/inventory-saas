
import time
import uuid
import json
import requests

BASE_URL = "http://localhost:8080/api"
RUN_ID = str(uuid.uuid4())[:6]
TIMEOUT = 15

session = requests.Session()


# =========================================================
# TEST AUDITOR
# =========================================================

class TestAuditor:
    def __init__(self):
        self.results = []

    def record(self, name, status, details=""):
        result = {
            "name": name,
            "status": status,
            "details": details
        }
        self.results.append(result)

        if status == "PASS":
            print(f"[PASS] {name}")
        elif status == "FAIL":
            print(f"[FAIL] {name}")
            if details:
                print(f"       {details}")
        else:
            print(f"[SKIP] {name}")
            if details:
                print(f"       {details}")

    def check(self, name, condition, details=""):
        self.record(
            name,
            "PASS" if condition else "FAIL",
            "" if condition else details
        )
        return bool(condition)

    def skip(self, name, reason):
        self.record(name, "SKIP", reason)

    def report(self):
        passed = sum(r["status"] == "PASS" for r in self.results)
        failed = sum(r["status"] == "FAIL" for r in self.results)
        skipped = sum(r["status"] == "SKIP" for r in self.results)

        completed = passed + failed
        score = (passed / completed * 100) if completed else 0

        print("\n")
        print("=" * 62)
        print("              INVENTORY SAAS TEST AUDIT")
        print("=" * 62)

        print(f"Run ID:       {RUN_ID}")
        print(f"Total checks: {len(self.results)}")
        print(f"Passed:       {passed}")
        print(f"Failed:       {failed}")
        print(f"Skipped:      {skipped}")
        print("-" * 62)
        print(f"Overall score: {score:.2f}%")
        print("=" * 62)

        if failed:
            print("\nFAILED TESTS")

            for index, result in enumerate(
                [r for r in self.results if r["status"] == "FAIL"],
                start=1
            ):
                print(f"\n{index}. {result['name']}")
                print(f"   Reason: {result['details']}")

        if skipped:
            print("\nSKIPPED TESTS")

            for index, result in enumerate(
                [r for r in self.results if r["status"] == "SKIP"],
                start=1
            ):
                print(f"\n{index}. {result['name']}")
                print(f"   Reason: {result['details']}")

        print("\n" + "=" * 62)

        if failed == 0 and skipped == 0:
            print("ALL TESTS PASSED SUCCESSFULLY!")
        elif failed == 0:
            print("NO FAILURES, BUT SOME TESTS WERE SKIPPED.")
        else:
            print("TEST AUDIT COMPLETED — FAILURES DETECTED.")

        print("=" * 62)


auditor = TestAuditor()


# =========================================================
# HTTP HELPERS
# =========================================================

def auth_headers(token):
    return {
        "Authorization": f"Bearer {token}",
        "Content-Type": "application/json"
    }


def api_request(method, endpoint, expected_status, name,
                token=None, payload=None):

    url = f"{BASE_URL}{endpoint}"
    headers = auth_headers(token) if token else None

    try:
        response = session.request(
            method=method,
            url=url,
            json=payload,
            headers=headers,
            timeout=TIMEOUT
        )

    except requests.exceptions.RequestException as error:
        auditor.record(
            name,
            "FAIL",
            f"Request error: {error}"
        )
        return None

    # Support one expected status or multiple acceptable statuses.
    acceptable_statuses = (
        expected_status
        if isinstance(expected_status, (tuple, list, set))
        else (expected_status,)
    )

    if response.status_code not in acceptable_statuses:

        expected_text = " or ".join(
            str(status) for status in acceptable_statuses
        )

        auditor.record(
            name,
            "FAIL",
            (
                f"Expected HTTP {expected_text}, "
                f"received HTTP {response.status_code}. "
                f"URL: {response.url}. "
                f"Response: {response.text}"
            )
        )
        return None

    auditor.record(
        name,
        "PASS",
        f"HTTP {response.status_code}"
    )

    try:
        return response.json()
    except ValueError:
        return response.text

def post(endpoint, payload, expected, name, token=None):
    return api_request(
        "POST", endpoint, expected, name, token, payload
    )


def get(endpoint, expected, name, token):
    return api_request(
        "GET", endpoint, expected, name, token
    )


def put(endpoint, payload, expected, name, token):
    return api_request(
        "PUT", endpoint, expected, name, token, payload
    )


# =========================================================
# VALIDATION HELPERS
# =========================================================

def get_id(data, label):
    if not isinstance(data, dict) or not data.get("id"):
        auditor.record(
            f"Retrieve {label} ID",
            "FAIL",
            f"Expected an object containing an ID. Received: {data}"
        )
        return None

    auditor.record(f"Retrieve {label} ID", "PASS")
    return data["id"]


def login(email, password, label):
    data = post(
        "/auth/login",
        {
            "email": email,
            "password": password
        },
        200,
        f"Login as {label}"
    )

    if not isinstance(data, dict):
        auditor.record(
            f"Retrieve {label} token",
            "FAIL",
            f"Unexpected login response: {data}"
        )
        return None

    token = data.get("token") or data.get("accessToken")

    if not token:
        auditor.record(
            f"Retrieve {label} token",
            "FAIL",
            f"No token found. Response: {data}"
        )
        return None

    auditor.record(f"Retrieve {label} token", "PASS")
    return token


def find_inventory(inventories, warehouse_name, product_name, label):
    """
    The current inventory response contains:
    id, productName, warehouseName, quantity.

    It does not contain productId or warehouseId.
    """

    if not isinstance(inventories, list):
        auditor.record(
            label,
            "FAIL",
            f"Expected a list. Received: {inventories}"
        )
        return None

    matches = [
        item for item in inventories
        if isinstance(item, dict)
        and item.get("warehouseName") == warehouse_name
        and item.get("productName") == product_name
    ]

    if len(matches) == 0:
        auditor.record(
            label,
            "FAIL",
            (
                f"No matching inventory record found. "
                f"Expected warehouse='{warehouse_name}', "
                f"product='{product_name}'. "
                f"API returned: {json.dumps(inventories, indent=2)}"
            )
        )
        return None

    if len(matches) > 1:
        auditor.record(
            label,
            "FAIL",
            (
                f"Found {len(matches)} matching records. "
                f"Expected exactly one. Matches: "
                f"{json.dumps(matches, indent=2)}"
            )
        )
        return None

    auditor.record(label, "PASS")
    return matches[0]


def verify_quantity(inventory, expected, label):
    if inventory is None:
        auditor.skip(
            label,
            "Could not verify quantity because inventory lookup failed."
        )
        return False

    actual = inventory.get("quantity")

    return auditor.check(
        label,
        actual == expected,
        f"Expected quantity {expected}, received {actual}."
    )


# =========================================================
# MAIN E2E TEST SUITE
# =========================================================

def run_e2e_suite():

    print("\n" + "=" * 62)
    print("       INVENTORY SAAS END-TO-END TEST SUITE")
    print(f"                    RUN: {RUN_ID}")
    print("=" * 62)

    owner_token = None
    mgr_a_token = None
    staff_a_token = None
    rival_token = None

    wa_id = None
    wb_id = None
    product_id = None

    product_name = "Coaster Cabin Biscuit"
    wa_name = "Warehouse A - Dominos"
    wb_name = "Warehouse B - Festac"

    owner_email = f"owner_{RUN_ID}@marketsquare.com"
    owner_pass = "Glory_123"

    mgr_a_email = f"mgr_wa_{RUN_ID}@marketsquare.com"
    mgr_a_pass = "Manager_123"

    staff_a_email = f"staff_wa_{RUN_ID}@marketsquare.com"
    staff_a_pass = "Staff_123"

    # -----------------------------------------------------
    # 1. REGISTER COMPANY A AND LOGIN
    # -----------------------------------------------------

    print("\n--- 1. COMPANY REGISTRATION ---")

    registration = post(
        "/auth/register",
        {
            "name": f"Market Square {RUN_ID}",
            "adminEmail": owner_email,
            "password": owner_pass
        },
        201,
        "Register Company A and Owner"
    )

    if registration is not None:
        owner_token = login(
            owner_email,
            owner_pass,
            "Company A Owner"
        )

    if not owner_token:
        auditor.skip(
            "Company A setup",
            "Owner registration or login failed."
        )

    # -----------------------------------------------------
    # 2. CREATE WAREHOUSES
    # -----------------------------------------------------

    print("\n--- 2. WAREHOUSE MANAGEMENT ---")

    if owner_token:

        wa = post(
            "/warehouse/create",
            {
                "name": wa_name,
                "code": f"WA-{RUN_ID}"
            },
            201,
            "Owner creates Warehouse A",
            owner_token
        )

        if wa is not None:
            wa_id = get_id(wa, "Warehouse A")

        wb = post(
            "/warehouse/create",
            {
                "name": "Warehouse B - Alakija",
                "code": f"WB-{RUN_ID}"
            },
            201,
            "Owner creates Warehouse B",
            owner_token
        )

        if wb is not None:
            wb_id = get_id(wb, "Warehouse B")

        if wb_id:
            put(
                f"/warehouse/update/{wb_id}",
                {
                    "name": wb_name,
                    "code": f"WB-{RUN_ID}"
                },
                200,
                "Owner updates Warehouse B",
                owner_token
            )

    else:
        auditor.skip("Create Warehouse A", "Owner token unavailable.")
        auditor.skip("Create Warehouse B", "Owner token unavailable.")
        auditor.skip("Update Warehouse B", "Owner token unavailable.")

    # -----------------------------------------------------
    # 3. CREATE AND UPDATE PRODUCT
    # -----------------------------------------------------

    print("\n--- 3. PRODUCT MANAGEMENT ---")

    if owner_token:

        product = post(
            "/products/create",
            {
                "name": "Coaster Biscuit",
                "sku": f"COAB-{RUN_ID}"
            },
            201,
            "Owner creates Product",
            owner_token
        )

        if product is not None:
            product_id = get_id(product, "Product")

        if product_id:
            put(
                f"/products/update/{product_id}",
                {
                    "name": product_name,
                    "sku": f"COAB-{RUN_ID}"
                },
                200,
                "Owner updates Product",
                owner_token
            )

    else:
        auditor.skip("Create Product", "Owner token unavailable.")
        auditor.skip("Update Product", "Owner token unavailable.")

    # -----------------------------------------------------
    # 4. CREATE MANAGER
    # -----------------------------------------------------

    print("\n--- 4. USER MANAGEMENT ---")

    if owner_token and wa_id:

        post(
            "/auth/managers",
            {
                "email": mgr_a_email,
                "password": mgr_a_pass,
                "warehouseId": wa_id
            },
            201,
            "Owner creates Warehouse Manager for WA",
            owner_token
        )

        mgr_a_token = login(
            mgr_a_email,
            mgr_a_pass,
            "Warehouse A Manager"
        )

    else:
        auditor.skip(
            "Create Warehouse Manager",
            "Owner token or Warehouse A ID unavailable."
        )

    # -----------------------------------------------------
    # 5. MANAGER CREATES STAFF
    # -----------------------------------------------------

    if mgr_a_token:

        post(
            "/auth/staffs",
            {
                "email": staff_a_email,
                "password": staff_a_pass
            },
            201,
            "Manager A creates Staff assigned to WA",
            mgr_a_token
        )

        staff_a_token = login(
            staff_a_email,
            staff_a_pass,
            "Warehouse A Staff"
        )

    else:
        auditor.skip(
            "Manager creates Staff",
            "Manager login unavailable."
        )

    # -----------------------------------------------------
    # 6. RBAC SECURITY TESTS
    # -----------------------------------------------------

    print("\n--- 6. RBAC SECURITY BOUNDARIES ---")

    if staff_a_token:

        post(
            "/warehouse/create",
            {
                "name": "Rogue Warehouse",
                "code": f"RG-{RUN_ID}"
            },
            403,
            "Staff cannot create a warehouse",
            staff_a_token
        )

        post(
            "/auth/staffs",
            {
                "email": f"fake_{RUN_ID}@marketsquare.com",
                "password": "Password123"
            },
            403,
            "Staff cannot create another staff member",
            staff_a_token
        )

    else:
        auditor.skip("Staff cannot create a warehouse", "Staff token unavailable.")
        auditor.skip("Staff cannot create another staff member", "Staff token unavailable.")

    if mgr_a_token and wb_id:

        post(
            "/auth/managers",
            {
                "email": f"rogue_mgr_{RUN_ID}@marketsquare.com",
                "password": "Password123",
                "warehouseId": wb_id
            },
            403,
            "Manager cannot create another manager",
            mgr_a_token
        )

    else:
        auditor.skip(
            "Manager cannot create another manager",
            "Manager token or Warehouse B ID unavailable."
        )

    # -----------------------------------------------------
    # 7. STOCK MOVEMENTS
    # -----------------------------------------------------

    print("\n--- 7. INVENTORY MATHEMATICS ---")
    print("Expected: Purchase 100 -> Sell 50 -> Transfer 20")

    stock_operations_ready = (
        mgr_a_token and staff_a_token and
        product_id and wa_id and wb_id
    )

    if stock_operations_ready:

        purchase = post(
            "/stock-movement/purchase",
            {
                "productId": product_id,
                "quantity": 100
            },
            201,
            "Manager purchases 100 units into assigned WA",
            mgr_a_token
        )

        purchase_ok = purchase is not None

        if purchase_ok:
            auditor.check(
                "Purchase destination is Warehouse A",
                isinstance(purchase, dict)
                and purchase.get("destinationWarehouseName") == wa_name,
                (
                    f"Expected '{wa_name}', received "
                    f"'{purchase.get('destinationWarehouseName')}'"
                    if isinstance(purchase, dict)
                    else f"Unexpected response: {purchase}"
                )
            )

        if purchase_ok:

            sale = post(
                "/stock-movement/sale",
                {
                    "productId": product_id,
                    "sourceWarehouseId": wa_id,
                    "quantity": 50
                },
                201,
                "Staff sells 50 units from WA",
                staff_a_token
            )

        else:
            sale = None
            auditor.skip(
                "Staff sells 50 units from WA",
                "Purchase failed; stock was not initialized."
            )

        if sale is not None:

            transfer = post(
                "/stock-movement/transfer",
                {
                    "productId": product_id,
                    "sourceWarehouseId": wa_id,
                    "destinationWarehouseId": wb_id,
                    "quantity": 20
                },
                201,
                "Manager transfers 20 units from WA to WB",
                mgr_a_token
            )

        else:
            transfer = None
            auditor.skip(
                "Manager transfers 20 units from WA to WB",
                "Sale failed; transfer was not attempted."
            )

        if transfer is not None:

            post(
                "/stock-movement/sale",
                {
                    "productId": product_id,
                    "sourceWarehouseId": wa_id,
                    "quantity": 40
                },
                400,
                "Stock guard rejects sale of 40 when only 30 remain",
                staff_a_token
            )

        else:
            auditor.skip(
                "Stock guard rejects sale of 40",
                "Transfer failed; expected balance was not established."
            )

    else:
        auditor.skip(
            "Purchase 100 units",
            "Required user, product, or warehouse setup is unavailable."
        )
        auditor.skip(
            "Sale 50 units",
            "Required stock setup is unavailable."
        )
        auditor.skip(
            "Transfer 20 units",
            "Required stock setup is unavailable."
        )
        auditor.skip(
            "Stock guard rejects sale of 40",
            "Required stock setup is unavailable."
        )

    # -----------------------------------------------------
    # 8. COMPANY-WIDE INVENTORY VISIBILITY
    # -----------------------------------------------------

    print("\n--- 8. COMPANY-WIDE INVENTORY VISIBILITY ---")

    inventory_ready = (
        mgr_a_token and staff_a_token and wa_id and
        wb_id and product_id
    )

    if inventory_ready:

        inventories = get(
            "/inventory/all",
            200,
            "Manager fetches all company inventories",
            mgr_a_token
        )

        if isinstance(inventories, list):

            wa_inventory = find_inventory(
                inventories,
                wa_name,
                product_name,
                "Manager can see Warehouse A inventory"
            )

            wb_inventory = find_inventory(
                inventories,
                wb_name,
                product_name,
                "Manager can see Warehouse B inventory"
            )

            verify_quantity(
                wa_inventory,
                30,
                "Manager sees WA quantity = 30"
            )

            verify_quantity(
                wb_inventory,
                20,
                "Manager sees WB quantity = 20"
            )

        elif inventories is not None:
            auditor.record(
                "Manager inventory response is a list",
                "FAIL",
                f"Expected list, received: {inventories}"
            )

            auditor.skip(
                "Manager can see Warehouse A inventory",
                "Inventory response was not a list."
            )
            auditor.skip(
                "Manager can see Warehouse B inventory",
                "Inventory response was not a list."
            )
            auditor.skip(
                "Manager sees WA quantity = 30",
                "Inventory response was not a list."
            )
            auditor.skip(
                "Manager sees WB quantity = 20",
                "Inventory response was not a list."
            )

        else:
            auditor.skip(
                "Manager can see Warehouse A inventory",
                "Inventory request failed."
            )
            auditor.skip(
                "Manager can see Warehouse B inventory",
                "Inventory request failed."
            )
            auditor.skip(
                "Manager sees WA quantity = 30",
                "Inventory request failed."
            )
            auditor.skip(
                "Manager sees WB quantity = 20",
                "Inventory request failed."
            )

        # Staff must also see both company warehouses.

        staff_inventories = get(
            "/inventory/all",
            200,
            "Staff fetches all company inventories",
            staff_a_token
        )

        if isinstance(staff_inventories, list):

            staff_wa = find_inventory(
                staff_inventories,
                wa_name,
                product_name,
                "Staff can see Warehouse A inventory"
            )

            staff_wb = find_inventory(
                staff_inventories,
                wb_name,
                product_name,
                "Staff can see Warehouse B inventory"
            )

            verify_quantity(
                staff_wa,
                30,
                "Staff sees WA quantity = 30"
            )

            verify_quantity(
                staff_wb,
                20,
                "Staff sees WB quantity = 20"
            )

        elif staff_inventories is not None:
            auditor.record(
                "Staff inventory response is a list",
                "FAIL",
                f"Expected list, received: {staff_inventories}"
            )

            auditor.skip(
                "Staff can see Warehouse A inventory",
                "Inventory response was not a list."
            )
            auditor.skip(
                "Staff can see Warehouse B inventory",
                "Inventory response was not a list."
            )
            auditor.skip(
                "Staff sees WA quantity = 30",
                "Inventory response was not a list."
            )
            auditor.skip(
                "Staff sees WB quantity = 20",
                "Inventory response was not a list."
            )

        else:
            auditor.skip(
                "Staff can see Warehouse A inventory",
                "Inventory request failed."
            )
            auditor.skip(
                "Staff can see Warehouse B inventory",
                "Inventory request failed."
            )
            auditor.skip(
                "Staff sees WA quantity = 30",
                "Inventory request failed."
            )
            auditor.skip(
                "Staff sees WB quantity = 20",
                "Inventory request failed."
            )

    else:
        auditor.skip(
            "Manager inventory visibility",
            "Required setup is unavailable."
        )
        auditor.skip(
            "Staff inventory visibility",
            "Required setup is unavailable."
        )

    # -----------------------------------------------------
    # 9. STOCK MOVEMENT AUDIT
    # -----------------------------------------------------

    print("\n--- 9. STOCK MOVEMENT AUDIT ---")

    if mgr_a_token:

        audits = get(
            "/stock-movement/audit",
            200,
            "Manager fetches stock movement audit logs",
            mgr_a_token
        )

        if isinstance(audits, list):
            auditor.check(
                "Manager sees exactly 3 audit records",
                len(audits) == 3,
                f"Expected 3 records, received {len(audits)}."
            )
        elif audits is not None:
            auditor.record(
                "Manager audit response is a list",
                "FAIL",
                f"Expected list, received: {audits}"
            )
        else:
            auditor.skip(
                "Manager sees exactly 3 audit records",
                "Audit request failed."
            )

    else:
        auditor.skip("Manager audit access", "Manager token unavailable.")
        auditor.skip("Manager sees exactly 3 audit records", "Manager token unavailable.")

    if staff_a_token:

        staff_audits = get(
            "/stock-movement/audit",
            200,
            "Staff fetches stock movement audit logs",
            staff_a_token
        )

        if isinstance(staff_audits, list):
            auditor.check(
                "Staff sees exactly 3 audit records",
                len(staff_audits) == 3,
                f"Expected 3 records, received {len(staff_audits)}."
            )
        elif staff_audits is not None:
            auditor.record(
                "Staff audit response is a list",
                "FAIL",
                f"Expected list, received: {staff_audits}"
            )
        else:
            auditor.skip(
                "Staff sees exactly 3 audit records",
                "Audit request failed."
            )

    else:
        auditor.skip("Staff audit access", "Staff token unavailable.")
        auditor.skip("Staff sees exactly 3 audit records", "Staff token unavailable.")

        # -----------------------------------------------------
        # 10. MULTI-TENANT ISOLATION
        # -----------------------------------------------------

        print("\n--- 10. MULTI-TENANT ISOLATION ---")

        rival_email = f"rival_{RUN_ID}@othercorp.com"
        rival_pass = "Rival_123"

        rival_registration = post(
            "/auth/register",
            {
                "name": f"Rival Corp {RUN_ID}",
                "adminEmail": rival_email,
                "password": rival_pass
            },
            201,
            "Register Rival Company B"
        )

        if rival_registration is not None:

            rival_token = login(
                rival_email,
                rival_pass,
                "Company B Owner"
            )

        if rival_token:

            # Company B should only see its own warehouses.
            rival_warehouses = get(
                "/warehouse/all",
                200,
                "Company B owner queries warehouses",
                rival_token
            )

            if isinstance(rival_warehouses, list):

                company_a_warehouse_ids = {
                    wa_id,
                    wb_id
                } - {None}

                rival_warehouse_ids = {
                    warehouse.get("id")
                    for warehouse in rival_warehouses
                    if isinstance(warehouse, dict)
                }

                auditor.check(
                    "Company B cannot see Company A warehouses",
                    company_a_warehouse_ids.isdisjoint(
                        rival_warehouse_ids
                    ),
                    (
                        "Company B's warehouse response contains "
                        "Company A warehouse IDs."
                    )
                )

            elif rival_warehouses is not None:

                auditor.record(
                    "Company B warehouse response is a list",
                    "FAIL",
                    f"Unexpected response: {rival_warehouses}"
                )

            else:

                auditor.skip(
                    "Company B cannot see Company A warehouses",
                    "Warehouse request failed."
                )

            # Company B must not update Company A's product.
            if product_id:

                put(
                    f"/products/update/{product_id}",
                    {
                        "name": "Hacked Biscuit",
                        "sku": f"COAB-{RUN_ID}"
                    },
                    (403, 404),
                    "Company B cannot update Company A product",
                    rival_token
                )

            else:

                auditor.skip(
                    "Company B cannot update Company A product",
                    "Company A product ID unavailable."
                )

        else:

            auditor.skip(
                "Company B cannot see Company A warehouses",
                "Rival company registration or login failed."
            )

            auditor.skip(
                "Company B cannot update Company A product",
                "Rival company registration or login failed."
            )

    # -----------------------------------------------------
    # 11. REDIS CACHE LATENCY
    # -----------------------------------------------------

    print("\n--- 11. REDIS CACHE LATENCY ---")

    if owner_token:

        first_start = time.perf_counter()

        first_response = get(
            "/products/all",
            200,
            "First product fetch",
            owner_token
        )

        first_ms = (time.perf_counter() - first_start) * 1000

        second_start = time.perf_counter()

        second_response = get(
            "/products/all",
            200,
            "Second product fetch",
            owner_token
        )

        second_ms = (time.perf_counter() - second_start) * 1000

        if first_response is not None and second_response is not None:
            print(f"[INFO] First fetch:  {first_ms:.2f} ms")
            print(f"[INFO] Second fetch: {second_ms:.2f} ms")

            auditor.record(
                "Redis latency measurements captured",
                "PASS",
                "Both product requests completed successfully."
            )

        else:
            auditor.skip(
                "Redis latency measurements captured",
                "One or both product requests failed."
            )

    else:
        auditor.skip(
            "Redis cache latency",
            "Owner token unavailable."
        )

    # -----------------------------------------------------
    # FINAL REPORT
    # -----------------------------------------------------

    auditor.report()


# =========================================================
# EXECUTION
# =========================================================

if __name__ == "__main__":

    try:
        run_e2e_suite()

    except KeyboardInterrupt:
        print("\nTest execution interrupted by user.")
        auditor.report()

    except Exception as error:
        auditor.record(
            "Unexpected test runner error",
            "FAIL",
            f"{type(error).__name__}: {error}"
        )
        auditor.report()
