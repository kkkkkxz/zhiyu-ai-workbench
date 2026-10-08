# -*- coding: utf-8 -*-
from __future__ import annotations

import json
import os
import subprocess
import time
import urllib.request
from pathlib import Path


BASE_URL = os.environ.get("ZWSK_CAPTURE_BASE_URL", "http://127.0.0.1:3000").rstrip("/")
LOGIN_USERNAME = os.environ.get("ZWSK_CAPTURE_USERNAME", "admin")
LOGIN_PASSWORD = os.environ.get("ZWSK_CAPTURE_PASSWORD", "")
EDGE = Path(r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe")
PAGES = [
    ("workbench", "/workbench"),
    ("kb", "/kb"),
    ("customers", "/crm/customers"),
    ("schedule", "/schedule"),
    ("settings", "/settings"),
]


def desktop_dir() -> Path:
    path = Path.home() / "Desktop"
    return path if path.exists() else Path(r"C:\Users\admin\Desktop")


def login_state() -> dict:
    if not LOGIN_PASSWORD:
        raise RuntimeError("Set ZWSK_CAPTURE_PASSWORD before capturing authenticated pages.")
    payload = json.dumps({"username": LOGIN_USERNAME, "password": LOGIN_PASSWORD}).encode("utf-8")
    req = urllib.request.Request(
        f"{BASE_URL}/api/auth/login",
        data=payload,
        headers={"Content-Type": "application/json"},
    )
    with urllib.request.urlopen(req, timeout=15) as resp:
        body = json.loads(resp.read().decode("utf-8"))
    data = body.get("data", body)
    return {
        "state": {
            "accessToken": data["accessToken"],
            "refreshToken": data["refreshToken"],
            "accessExpiresAt": data["accessExpiresAt"],
            "refreshExpiresAt": data["refreshExpiresAt"],
            "userId": str(data["userId"]),
            "username": data["username"],
            "nickname": data.get("nickname"),
            "tenantId": str(data["tenantId"]),
            "tenantCode": data.get("tenantCode"),
            "roles": data.get("roles") or [],
            "permissions": data.get("permissions") or [],
        },
        "version": 0,
    }


def main():
    out_dir = desktop_dir() / "知语AI-移动端修复后截图"
    out_dir.mkdir(parents=True, exist_ok=True)
    state = json.dumps(login_state(), ensure_ascii=False).replace('"', '\\"')
    init_html = out_dir / "mobile-auth.html"
    init_html.write_text(
        f"""<!doctype html><meta charset='utf-8'><script>
localStorage.setItem('zwsk.auth', "{state}");
location.href = '{BASE_URL}/workbench';
</script>""",
        encoding="utf-8",
    )
    subprocess.run([
        str(EDGE),
        "--headless=new",
        "--disable-gpu",
        "--window-size=390,844",
        "--virtual-time-budget=1500",
        f"file:///{init_html.as_posix()}",
    ], check=False, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    time.sleep(0.5)
    for name, path in PAGES:
        output = out_dir / f"{name}.png"
        subprocess.run([
            str(EDGE),
            "--headless=new",
            "--disable-gpu",
            "--hide-scrollbars",
            "--window-size=390,844",
            "--virtual-time-budget=4500",
            f"--screenshot={output}",
            f"{BASE_URL}{path}",
        ], check=False, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        print(output)


if __name__ == "__main__":
    main()
