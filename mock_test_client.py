#!/usr/bin/env python3
"""
Mock test client for manager-service.
- Starts a local HTTP callback server
- Calls POST /api/v1/manager/tasks/create
- Polls POST /api/v1/manager/tasks/status
- Prints all callback payloads received
"""

import json
import threading
import time
from http.server import BaseHTTPRequestHandler, HTTPServer

import requests

# ============ Config ============
MANAGER_URL = "http://localhost:18081"
CALLBACK_HOST = "0.0.0.0"
CALLBACK_PORT = 9999

# Request payload matching ManagerTaskRequest (camelCase)
CREATE_PAYLOAD = {
    "storyId": "STORY-001",
    "phase": "SIT",
    "docType": "api",
    "testCaseIdList": ["TP001"],
    "envDTO": "## 测试环境\n\n**ZK注册地址**：zookeeper://168.63.65.196:2182",
    "callbackUrl": f"http://localhost:{CALLBACK_PORT}/callback"
}

# ============ Callback Server ============
class CallbackHandler(BaseHTTPRequestHandler):
    callbacks = []
    callback_event = threading.Event()

    def do_POST(self):
        content_length = int(self.headers.get("Content-Length", 0))
        body = self.rfile.read(content_length).decode("utf-8")
        CallbackHandler.callbacks.append(body)
        CallbackHandler.callback_event.set()
        print(f"\n[CALLBACK RECEIVED] {body}\n")
        self.send_response(200)
        self.end_headers()

    def log_message(self, format, *args):
        pass  # quiet


def start_callback_server():
    server = HTTPServer((CALLBACK_HOST, CALLBACK_PORT), CallbackHandler)
    threading.Thread(target=server.serve_forever, daemon=True).start()
    print(f"Callback server started at http://{CALLBACK_HOST}:{CALLBACK_PORT}")
    return server


# ============ Main ============
def main():
    start_callback_server()
    time.sleep(0.5)

    create_url = f"{MANAGER_URL}/api/v1/manager/tasks/create"
    status_url = f"{MANAGER_URL}/api/v1/manager/tasks/status"

    print(f"\n>>> POST {create_url}")
    print(f"Payload: {json.dumps(CREATE_PAYLOAD, ensure_ascii=False)}")
    try:
        resp = requests.post(create_url, json=CREATE_PAYLOAD, timeout=10)
    except Exception as e:
        print(f"Create request failed: {e}")
        return

    print(f"Status: {resp.status_code}")
    print(f"Body: {resp.text}")

    try:
        data = resp.json()
    except Exception:
        print("Response is not valid JSON.")
        return

    task_id = data.get("taskId")
    if not task_id:
        print("No taskId returned.")
        return

    print(f"\nManager task id: {task_id}")
    print(f">>> Polling {status_url} every 5s ...")

    for i in range(120):  # max 10 minutes
        try:
            status_resp = requests.post(status_url, json={"taskId": task_id}, timeout=10)
            status_body = status_resp.json()
            print(f"Poll {i+1}: {status_body}")
            task_status = status_body.get("taskStatus")
            if task_status in (0, -1):  # completed or failed
                break
        except Exception as e:
            print(f"Poll {i+1} failed: {e}")
        time.sleep(5)

    print("\nPolling finished, waiting for callback ...")
    received = CallbackHandler.callback_event.wait(timeout=60)
    if not received:
        print("WARNING: Timeout waiting for callback (60s)")

    print("\n" + "=" * 50)
    print("All received callbacks:")
    for idx, cb in enumerate(CallbackHandler.callbacks, 1):
        print(f"\n--- Callback {idx} ---")
        try:
            parsed = json.loads(cb)
            print(json.dumps(parsed, indent=2, ensure_ascii=False))
        except Exception:
            print(cb)

    if not CallbackHandler.callbacks:
        print("(No callbacks received)")


if __name__ == "__main__":
    main()
