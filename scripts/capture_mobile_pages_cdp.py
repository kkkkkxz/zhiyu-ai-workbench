# -*- coding: utf-8 -*-
from __future__ import annotations

import base64
import json
import shutil
import subprocess
import time
from pathlib import Path

from capture_all_pages_cdp import BASE_URL, EDGE, Cdp, get_json, get_login_state, new_tab, wait_debugger


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


def wait_page_ready(cdp: Cdp, settle_seconds: float = 1.2):
    deadline = time.time() + 20
    while time.time() < deadline:
        ready = cdp.call("Runtime.evaluate", {
            "expression": "document.readyState",
            "returnByValue": True,
        }, timeout=3)["result"].get("value")
        if ready == "complete":
            time.sleep(settle_seconds)
            return
        time.sleep(0.25)
    time.sleep(settle_seconds)


def capture(cdp: Cdp, path: str, output: Path):
    cdp.call("Page.navigate", {"url": f"{BASE_URL}{path}"})
    wait_page_ready(cdp)
    overflow = cdp.call("Runtime.evaluate", {
        "expression": "({w: window.innerWidth, sw: document.documentElement.scrollWidth, path: location.pathname})",
        "returnByValue": True,
    })["result"]["value"]
    metrics = cdp.call("Page.getLayoutMetrics")
    height = max(844, min(2600, int(metrics["contentSize"]["height"]) + 24))
    cdp.call("Emulation.setDeviceMetricsOverride", {
        "width": 390,
        "height": height,
        "deviceScaleFactor": 2,
        "mobile": True,
    })
    time.sleep(0.4)
    image = cdp.call("Page.captureScreenshot", {
        "format": "png",
        "fromSurface": True,
        "captureBeyondViewport": True,
    }, timeout=20)
    output.write_bytes(base64.b64decode(image["data"]))
    return overflow


def main():
    out_dir = desktop_dir() / "知语AI-移动端修复后截图"
    out_dir.mkdir(parents=True, exist_ok=True)
    state = get_login_state()

    profile = Path.cwd() / ".tmp-edge-mobile-cdp-profile"
    if profile.exists():
        shutil.rmtree(profile, ignore_errors=True)
    profile.mkdir(parents=True, exist_ok=True)

    port = 9224
    proc = subprocess.Popen([
        str(EDGE),
        f"--remote-debugging-port={port}",
        f"--user-data-dir={profile}",
        "--headless=new",
        "--disable-gpu",
        "--hide-scrollbars",
        "--remote-allow-origins=*",
        "--window-size=390,844",
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
                "width": 390,
                "height": 844,
                "deviceScaleFactor": 2,
                "mobile": True,
            })
            cdp.call("Page.navigate", {"url": f"{BASE_URL}/login"})
            wait_page_ready(cdp, 0.8)
            escaped = json.dumps(json.dumps(state, ensure_ascii=False))
            cdp.call("Runtime.evaluate", {
                "expression": f"localStorage.setItem('zwsk.auth', {escaped}); true;",
                "returnByValue": True,
            })
            for name, route in PAGES:
                output = out_dir / f"{name}.png"
                overflow = capture(cdp, route, output)
                print(f"{route} -> {output} inner={overflow['w']} scroll={overflow['sw']}")
        finally:
            cdp.close()
    finally:
        proc.terminate()
        try:
            proc.wait(timeout=5)
        except subprocess.TimeoutExpired:
            proc.kill()
        shutil.rmtree(profile, ignore_errors=True)


if __name__ == "__main__":
    main()
