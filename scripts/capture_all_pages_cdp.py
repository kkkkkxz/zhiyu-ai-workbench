# -*- coding: utf-8 -*-
from __future__ import annotations

import base64
import json
import os
import shutil
import subprocess
import time
import urllib.error
import urllib.request
from pathlib import Path


BASE_URL = os.environ.get("ZWSK_CAPTURE_BASE_URL", "http://127.0.0.1:3000").rstrip("/")
LOGIN_USERNAME = os.environ.get("ZWSK_CAPTURE_USERNAME", "admin")
LOGIN_PASSWORD = os.environ.get("ZWSK_CAPTURE_PASSWORD", "")
EDGE = Path(r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe")

PAGES = [
    ("01-login", "/login", "登录页"),
    ("02-register", "/register", "注册页"),
    ("03-voice-ai", "/voice-ai", "全局语音助手"),
    ("04-workbench", "/workbench", "工作台"),
    ("05-chat", "/chat", "AI 对话"),
    ("06-assistants", "/assistants", "助手选择"),
    ("07-kb", "/kb", "知识库"),
    ("08-voice-diagnostic", "/voice-diagnostic", "语音诊断"),
    ("09-tenants", "/tenants", "租户管理"),
    ("10-platform-dashboard", "/platform/dashboard", "平台数据看板"),
    ("11-platform-departments", "/platform/departments", "部门管理"),
    ("12-platform-admin-applies", "/platform/admin-applies", "管理员申请"),
    ("13-crm-customers", "/crm/customers", "客户管理"),
    ("14-contracts", "/contracts", "合同管理"),
    ("15-work-orders", "/work-orders", "工单管理"),
    ("16-dispatch", "/dispatch", "工作派发"),
    ("17-schedule", "/schedule", "日程管理"),
    ("18-workflow", "/workflow", "审批管理"),
    ("19-notifications", "/notifications", "通知中心"),
    ("20-settings", "/settings", "个人设置"),
    ("21-tenant-admin", "/tenant-admin", "租户管理员页"),
]


def desktop_dir() -> Path:
    path = Path.home() / "Desktop"
    return path if path.exists() else Path(r"C:\Users\admin\Desktop")


def http_json(url: str, payload: dict | None = None, timeout: int = 15):
    data = None
    headers = {"Content-Type": "application/json"}
    if payload is not None:
        data = json.dumps(payload, ensure_ascii=False).encode("utf-8")
    req = urllib.request.Request(url, data=data, headers=headers)
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        raw = resp.read().decode("utf-8")
    return json.loads(raw)


def get_login_state() -> dict:
    if not LOGIN_PASSWORD:
        raise RuntimeError("Set ZWSK_CAPTURE_PASSWORD before capturing authenticated pages.")
    result = http_json(
        f"{BASE_URL}/api/auth/login",
        {"username": LOGIN_USERNAME, "password": LOGIN_PASSWORD},
    )
    data = result.get("data", result)
    return {
        "state": {
            "accessToken": data["accessToken"],
            "refreshToken": data.get("refreshToken"),
            "accessExpiresAt": data.get("accessExpiresAt", int(time.time() * 1000) + 3600000),
            "refreshExpiresAt": data.get("refreshExpiresAt", int(time.time() * 1000) + 86400000),
            "userId": str(data.get("userId")),
            "username": data.get("username"),
            "nickname": data.get("nickname"),
            "tenantId": str(data.get("tenantId")),
            "tenantCode": data.get("tenantCode"),
            "roles": data.get("roles") or [],
            "permissions": data.get("permissions") or [],
        },
        "version": 0,
    }


def get_json(url: str, timeout: int = 5):
    with urllib.request.urlopen(url, timeout=timeout) as resp:
        return json.loads(resp.read().decode("utf-8"))


def post_json(url: str, payload: dict, timeout: int = 10):
    data = json.dumps(payload).encode("utf-8")
    req = urllib.request.Request(url, data=data, headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        raw = resp.read().decode("utf-8")
    return json.loads(raw) if raw else {}


def wait_debugger(port: int):
    deadline = time.time() + 20
    while time.time() < deadline:
        try:
            return get_json(f"http://127.0.0.1:{port}/json/version")
        except Exception:
            time.sleep(0.2)
    raise RuntimeError("Edge DevTools endpoint did not become ready")


def new_tab(port: int):
    req = urllib.request.Request(f"http://127.0.0.1:{port}/json/new", method="PUT")
    with urllib.request.urlopen(req, timeout=5) as resp:
        return json.loads(resp.read().decode("utf-8"))


def wait_ws():
    try:
        import websocket  # type: ignore
        return websocket
    except ImportError as exc:
        raise RuntimeError("Missing websocket-client package. Install with: python -m pip install websocket-client") from exc


class Cdp:
    def __init__(self, websocket_url: str):
        websocket = wait_ws()
        self.ws = websocket.create_connection(websocket_url, timeout=10)
        self.next_id = 0

    def call(self, method: str, params: dict | None = None, timeout: float = 20):
        self.next_id += 1
        ident = self.next_id
        self.ws.send(json.dumps({"id": ident, "method": method, "params": params or {}}))
        deadline = time.time() + timeout
        while time.time() < deadline:
            msg = json.loads(self.ws.recv())
            if msg.get("id") == ident:
                if "error" in msg:
                    raise RuntimeError(f"{method} failed: {msg['error']}")
                return msg.get("result", {})
        raise TimeoutError(method)

    def close(self):
        self.ws.close()


def wait_page_ready(cdp: Cdp, settle_seconds: float = 1.8):
    deadline = time.time() + 25
    while time.time() < deadline:
        ready = cdp.call("Runtime.evaluate", {
            "expression": "document.readyState",
            "returnByValue": True,
        }, timeout=3)["result"].get("value")
        if ready == "complete":
            time.sleep(settle_seconds)
            return
        time.sleep(0.3)
    time.sleep(settle_seconds)


def capture_page(cdp: Cdp, path: str, output: Path):
    cdp.call("Page.navigate", {"url": f"{BASE_URL}{path}"})
    wait_page_ready(cdp, 0.9)
    metrics = cdp.call("Page.getLayoutMetrics")
    height = max(900, min(2400, int(metrics["contentSize"]["height"]) + 40))
    width = 1440
    cdp.call("Emulation.setDeviceMetricsOverride", {
        "width": width,
        "height": height,
        "deviceScaleFactor": 1,
        "mobile": False,
    })
    time.sleep(0.5)
    image = cdp.call("Page.captureScreenshot", {
        "format": "png",
        "fromSurface": True,
        "captureBeyondViewport": True,
    }, timeout=20)
    output.write_bytes(base64.b64decode(image["data"]))


def main():
    out_dir = desktop_dir() / "知语AI-全部页面截图"
    out_dir.mkdir(parents=True, exist_ok=True)

    state = get_login_state()
    user_data = Path.cwd() / ".tmp-edge-cdp-profile"
    if user_data.exists():
        shutil.rmtree(user_data, ignore_errors=True)
    user_data.mkdir(parents=True, exist_ok=True)

    port = 9223
    proc = subprocess.Popen([
        str(EDGE),
        f"--remote-debugging-port={port}",
        f"--user-data-dir={user_data}",
        "--headless=new",
        "--disable-gpu",
        "--hide-scrollbars",
        "--remote-allow-origins=*",
        "--window-size=1440,1000",
        "about:blank",
    ], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    try:
        wait_debugger(port)
        tab = new_tab(port)
        cdp = Cdp(tab["webSocketDebuggerUrl"])
        try:
            cdp.call("Page.enable")
            cdp.call("Runtime.enable")
            cdp.call("Emulation.setDeviceMetricsOverride", {
                "width": 1440,
                "height": 1000,
                "deviceScaleFactor": 1,
                "mobile": False,
            })
            cdp.call("Page.navigate", {"url": f"{BASE_URL}/login"})
            wait_page_ready(cdp, 0.8)
            escaped = json.dumps(json.dumps(state, ensure_ascii=False))
            cdp.call("Runtime.evaluate", {
                "expression": f"localStorage.setItem('zwsk.auth', {escaped}); true;",
                "returnByValue": True,
            })
            for slug, route, title in PAGES:
                output = out_dir / f"{slug}-{title}.png"
                if output.exists() and output.stat().st_size > 10_000:
                    print(f"skip existing {route} -> {output}")
                    continue
                print(f"capture {route} -> {output}")
                try:
                    capture_page(cdp, route, output)
                except Exception as exc:
                    print(f"failed {route}: {exc}")
        finally:
            cdp.close()
    finally:
        proc.terminate()
        try:
            proc.wait(timeout=5)
        except subprocess.TimeoutExpired:
            proc.kill()
        shutil.rmtree(user_data, ignore_errors=True)
    print(out_dir)


if __name__ == "__main__":
    main()
