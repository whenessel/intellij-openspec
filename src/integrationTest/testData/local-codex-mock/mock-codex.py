#!/usr/bin/env python3
"""Offline UI mock: captured metadata, synthetic account and model turn events."""
import copy
import json
import os
from pathlib import Path
import sys

HOME = Path(__file__).resolve().parent
CAPTURE = json.loads((HOME / "handshake.json").read_text())
REPLIES = {frame["id"]: frame["result"] for frame in CAPTURE if "id" in frame}
THREAD = "ui-smoke-thread"
TURN = "ui-smoke-turn"

def emit(frame):
    try:
        print(json.dumps(frame), flush=True)
    except BrokenPipeError:
        sys.exit(0)

def event(method, params):
    emit({"method": method, "params": params})

def record(method, params):
    # Record control metadata only; never prompts, authentication material or argv.
    with (HOME / "methods.jsonl").open("a") as stream:
        stream.write(json.dumps({"pid": os.getpid(), "method": method,
                                 "structured": "outputSchema" in params}) + "\n")

def envelope():
    return json.dumps({"schemaVersion": 1, "artifactId": "specs", "files": [
        {"relativePath": "specs/" + name + "/spec.md", "operation": "create",
         "content": (HOME / (name + ".md")).read_text()}
        for name in ("mock-alpha", "mock-beta")]})

def complete(text, status="completed"):
    event("item/completed", {"threadId": THREAD, "turnId": TURN,
          "item": {"type": "agentMessage", "id": "mock-message", "phase": "final_answer", "text": text},
          "completedAtMs": 0})
    event("turn/completed", {"threadId": THREAD,
          "turn": {"id": TURN, "items": [], "status": status, "error": None}})

for line in sys.stdin:
    request = json.loads(line)
    method = request.get("method", "")
    params = request.get("params") or {}
    record(method, params)
    if "id" not in request:
        continue
    if method == "initialize":
        result = copy.deepcopy(REPLIES[1])
    elif method == "config/read":
        result = copy.deepcopy(REPLIES[3])
        result["config"]["permissions"]["openspec_reviewed"]["filesystem"] = {os.getcwd(): "read"}
    elif method == "account/read":
        result = copy.deepcopy(REPLIES[2])
        result["account"] = {"type": "chatgpt", "email": "ui-fixture@example.test", "planType": "plus"}
        result["workspaceRouting"] = {"chatgptAccountId": "ui-fixture-account",
                                      "backendOrigin": "https://chatgpt.com", "accountRoutingOverride": "NO_CONSTRAINT"}
    elif method == "account/rateLimits/read":
        result = {"ordinaryUsageAllowed": True, "rateLimits": None, "rateLimitsByLimitId": None}
    elif method == "model/list":
        result = copy.deepcopy(REPLIES[5])
    elif method in ("thread/start", "thread/resume"):
        result = copy.deepcopy(REPLIES[4])
        result["thread"]["id"] = THREAD
        result["cwd"] = os.getcwd()
        if "model" in params:
            result["model"] = params["model"]
    elif method == "turn/start":
        result = {"turn": {"id": TURN, "items": [], "status": "inProgress", "error": None}}
    elif method == "turn/interrupt":
        result = {}
    else:
        emit({"id": request["id"], "error": {"code": -32601, "message": "Unexpected mock method: " + method}})
        continue
    emit({"id": request["id"], "result": result})
    if method == "turn/start":
        if "outputSchema" in params:
            complete(envelope())
        else:
            event("item/agentMessage/delta", {"threadId": THREAD, "turnId": TURN,
                  "itemId": "mock-message", "delta": "UI smoke streamed response awaiting cancellation"})
            # No timer: hold the turn until the real UI sends interrupt.
    if method == "turn/interrupt":
        # Deliberately adversarial late success after interrupt must never reach file application.
        complete(envelope())
