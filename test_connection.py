#!/usr/bin/env python3
"""Quick Pear Desktop API connection tester.

Usage examples:
  python test_connection.py --base-url http://192.168.1.25:3000 --id my-device-id
  python test_connection.py --base-url http://127.0.0.1:3000 --id local-test --timeout 5
"""

from __future__ import annotations

import argparse
import json
import sys
import urllib.error
import urllib.parse
import urllib.request
from typing import Any, Optional, Tuple


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Test Pear Desktop API connectivity")
    parser.add_argument("--base-url", required=True, help="Server base URL, e.g. http://192.168.1.25:3000")
    parser.add_argument("--id", required=True, help="Auth ID used for /auth/{id}")
    parser.add_argument("--timeout", type=float, default=8.0, help="Request timeout in seconds (default: 8)")
    parser.add_argument(
        "--test-volume",
        type=float,
        default=None,
        help="Optional: send POST /api/v1/volume with this value and read volume state back",
    )
    return parser.parse_args()


def normalize_base_url(base_url: str) -> str:
    value = base_url.strip()
    if not value:
        raise ValueError("base URL is empty")
    if not value.startswith("http://") and not value.startswith("https://"):
        value = "http://" + value
    if value.endswith("/"):
        value = value[:-1]
    return value


def request_json(
    method: str,
    url: str,
    timeout: float,
    token: Optional[str] = None,
    payload: Optional[dict[str, Any]] = None,
) -> Tuple[int, Any]:
    headers = {"Accept": "application/json"}
    body_bytes: Optional[bytes] = None

    if payload is not None:
        body_bytes = json.dumps(payload).encode("utf-8")
        headers["Content-Type"] = "application/json"

    if token:
        headers["Authorization"] = f"Bearer {token}"

    req = urllib.request.Request(url=url, data=body_bytes, headers=headers, method=method)

    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            status = resp.getcode()
            text = resp.read().decode("utf-8", errors="replace").strip()
            if not text:
                return status, None
            try:
                return status, json.loads(text)
            except json.JSONDecodeError:
                return status, text
    except urllib.error.HTTPError as err:
        text = err.read().decode("utf-8", errors="replace").strip()
        details: Any = text
        if text:
            try:
                details = json.loads(text)
            except json.JSONDecodeError:
                pass
        return err.code, details


def parse_volume_state(data: Any) -> Optional[float]:
    if not isinstance(data, dict):
        return None
    state = data.get("state")
    if isinstance(state, (int, float)):
        return float(state)
    return None


def main() -> int:
    args = parse_args()

    try:
        base_url = normalize_base_url(args.base_url)
    except ValueError as err:
        print(f"Invalid base URL: {err}")
        return 2

    auth_id = urllib.parse.quote(args.id, safe="")
    auth_url = f"{base_url}/auth/{auth_id}"

    print("== Pear API Connection Test ==")
    print(f"Server: {base_url}")
    print(f"Auth ID: {args.id}")
    print()

    print("1) Authenticating...")
    try:
        auth_status, auth_data = request_json("POST", auth_url, timeout=args.timeout)
    except Exception as err:
        print(f"   FAIL: network error during auth: {err}")
        return 1

    if auth_status != 200 or not isinstance(auth_data, dict) or "accessToken" not in auth_data:
        print(f"   FAIL: auth failed (status {auth_status})")
        if auth_data is not None:
            print(f"   Response: {auth_data}")
        return 1

    token = str(auth_data["accessToken"])
    print("   OK: received access token")

    checks = [
        ("GET", "/api/v1/song", None),
        ("GET", "/api/v1/volume", None),
        ("GET", "/api/v1/repeat-mode", None),
        ("GET", "/api/v1/like-state", None),
    ]

    all_ok = True
    for index, (method, path, payload) in enumerate(checks, start=2):
        print(f"{index}) {method} {path} ...")
        url = f"{base_url}{path}"
        try:
            status, data = request_json(method, url, timeout=args.timeout, token=token, payload=payload)
        except Exception as err:
            print(f"   FAIL: network error: {err}")
            all_ok = False
            continue

        if status in (200, 204):
            print(f"   OK: status {status}")
            if data is not None:
                print(f"   Data: {data}")
        else:
            print(f"   FAIL: status {status}")
            if data is not None:
                print(f"   Response: {data}")
            all_ok = False

    if args.test_volume is not None:
        print(f"{len(checks) + 2}) POST /api/v1/volume (test value: {args.test_volume}) ...")
        post_url = f"{base_url}/api/v1/volume"
        post_payload = {"volume": args.test_volume}

        try:
            post_status, post_data = request_json(
                "POST",
                post_url,
                timeout=args.timeout,
                token=token,
                payload=post_payload,
            )
        except Exception as err:
            print(f"   FAIL: network error while setting volume: {err}")
            all_ok = False
        else:
            if post_status in (200, 204):
                print(f"   OK: set request status {post_status}")
            else:
                print(f"   FAIL: set request status {post_status}")
                if post_data is not None:
                    print(f"   Response: {post_data}")
                all_ok = False

        print(f"{len(checks) + 3}) GET /api/v1/volume (readback) ...")
        try:
            rb_status, rb_data = request_json("GET", post_url, timeout=args.timeout, token=token)
        except Exception as err:
            print(f"   FAIL: network error while reading volume: {err}")
            all_ok = False
        else:
            if rb_status in (200, 204):
                print(f"   OK: readback status {rb_status}")
                if rb_data is not None:
                    print(f"   Data: {rb_data}")

                readback_state = parse_volume_state(rb_data)
                if readback_state is None:
                    print("   WARN: could not parse readback volume state")
                elif abs(readback_state - args.test_volume) < 1e-6:
                    print("   OK: readback matches requested value")
                else:
                    print(
                        "   WARN: readback does not match requested value "
                        f"(requested={args.test_volume}, readback={readback_state})"
                    )
            else:
                print(f"   FAIL: readback status {rb_status}")
                if rb_data is not None:
                    print(f"   Response: {rb_data}")
                all_ok = False

    print()
    if all_ok:
        print("Result: SUCCESS. Connection and API access look good.")
        return 0

    print("Result: PARTIAL FAILURE. Server is reachable but one or more API checks failed.")
    return 1


if __name__ == "__main__":
    sys.exit(main())
