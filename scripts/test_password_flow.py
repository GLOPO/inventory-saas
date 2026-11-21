#!/usr/bin/env python3
"""Exercise one-time password setup and authenticated password changes.

Create a manager or staff account first, then copy the setup URL from its
onboarding email and run:

    python scripts/test_password_flow.py --email user@example.com \
      --setup-url 'http://localhost:5173/setup-password?token=...'

This script changes the target account password and does not restore it.
"""
import argparse
import secrets
import sys
from urllib.parse import parse_qs, urlparse

import requests


parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--base-url", default="http://localhost:8080")
parser.add_argument("--email", required=True, help="Email of the provisioned account")
parser.add_argument("--setup-url", required=True, help="One-time URL from the onboarding email")
parser.add_argument("--new-password", default=None, help="Password to set (generated if omitted)")
parser.add_argument("--changed-password", default=None, help="Password after change-password")
parser.add_argument("--timeout", type=int, default=20)
args = parser.parse_args()
BASE = args.base_url.rstrip("/")
NEW_PASSWORD = args.new_password or ("Set_" + secrets.token_urlsafe(12))
CHANGED_PASSWORD = args.changed_password or ("Changed_" + secrets.token_urlsafe(12))
checks = []
session = requests.Session()


def check(name, ok, details=""):
    checks.append((name, bool(ok), details if not ok else ""))
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


def expected(name, result, statuses):
    if isinstance(result, Exception):
        return check(name, False, str(result))
    detail = f"HTTP {result.status_code}: {result.text[:250]}"
    return check(name, result.status_code in statuses, detail)


def login(password, label):
    result = call("POST", "/api/auth/login", {"email": args.email, "password": password})
    if not expected(label, result, {200}):
        return None
    try:
        data = result.json()
    except ValueError:
        check(label + " response JSON", False, result.text[:250])
        return None
    token = data.get("accessToken") or data.get("token")
    check(label + " returns access token", bool(token), str(data)[:250])
    return token


def report():
    passed = sum(ok for _, ok, _ in checks)
    failed = len(checks) - passed
    print(f"\nPassword flow results: {passed} passed, {failed} failed, {len(checks)} total")
    return 1 if failed else 0


def main():
    parsed = urlparse(args.setup_url)
    token = parse_qs(parsed.query).get("token", [None])[0]
    if not token:
        check("setup URL contains token query parameter", False, "The URL must contain ?token=...")
        return report()
    check("setup URL contains token query parameter", True)

    # Invalid tokens and mismatched confirmation must not consume a real token.
    result = call("POST", "/api/auth/setup-password", {
        "token": "invalid-" + secrets.token_urlsafe(12),
        "password": NEW_PASSWORD,
        "confirmPassword": NEW_PASSWORD,
    })
    expected("setup rejects unknown token", result, {400})

    result = call("POST", "/api/auth/setup-password", {
        "token": token, "password": NEW_PASSWORD, "confirmPassword": NEW_PASSWORD + "-mismatch",
    })
    expected("setup rejects mismatched confirmation", result, {400})

    result = call("POST", "/api/auth/setup-password", {
        "token": token, "password": NEW_PASSWORD, "confirmPassword": NEW_PASSWORD,
    })
    if not expected("setup accepts valid one-time token", result, {200}):
        return report()

    result = call("POST", "/api/auth/setup-password", {
        "token": token, "password": NEW_PASSWORD, "confirmPassword": NEW_PASSWORD,
    })
    expected("setup token cannot be reused", result, {400})

    auth_token = login(NEW_PASSWORD, "login after password setup")
    if not auth_token:
        return report()

    result = call("POST", "/api/auth/change-password", {
        "currentPassword": "wrong-current-password",
        "newPassword": CHANGED_PASSWORD,
        "confirmPassword": CHANGED_PASSWORD,
    }, auth_token)
    expected("change rejects incorrect current password", result, {400, 401, 403})

    result = call("POST", "/api/auth/change-password", {
        "currentPassword": NEW_PASSWORD,
        "newPassword": CHANGED_PASSWORD,
        "confirmPassword": CHANGED_PASSWORD + "-mismatch",
    }, auth_token)
    expected("change rejects mismatched confirmation", result, {400})

    result = call("POST", "/api/auth/change-password", {
        "currentPassword": NEW_PASSWORD,
        "newPassword": NEW_PASSWORD,
        "confirmPassword": NEW_PASSWORD,
    }, auth_token)
    expected("change rejects reusing current password", result, {400})

    result = call("POST", "/api/auth/change-password", {
        "currentPassword": NEW_PASSWORD,
        "newPassword": CHANGED_PASSWORD,
        "confirmPassword": CHANGED_PASSWORD,
    }, auth_token)
    if not expected("change accepts valid new password", result, {200}):
        return report()

    result = call("POST", "/api/auth/login", {"email": args.email, "password": NEW_PASSWORD})
    expected("old password no longer logs in", result, {401, 403})
    login(CHANGED_PASSWORD, "login after password change")
    return report()


if __name__ == "__main__":
    sys.exit(main())
