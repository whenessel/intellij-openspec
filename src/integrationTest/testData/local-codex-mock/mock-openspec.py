#!/usr/bin/env python3
"""Captured OpenSpec fixture replays; no network, package manager or real CLI needed."""
import json
from pathlib import Path
import sys
HOME = Path(__file__).resolve().parent
ROOT = Path.cwd() / "openspec" / "changes" / "demo-add-farewell"
args = sys.argv[1:]
if args == ["--version"]:
    print("1.8.0")
elif args and args[0] == "status":
    result = json.loads((HOME / "status.json").read_text())
    result["changeName"] = ROOT.name
    if (ROOT / "specs/mock-alpha/spec.md").exists() and (ROOT / "specs/mock-beta/spec.md").exists():
        for artifact in result["artifacts"]:
            if artifact["id"] == "specs": artifact["status"] = "done"
        result["isComplete"] = True
    print(json.dumps(result))
elif args[:2] == ["instructions", "specs"]:
    result = json.loads((HOME / "instructions-specs.json").read_text())
    result["changeName"] = ROOT.name
    result["changeDir"] = str(ROOT)
    print(json.dumps(result))
else:
    # Ancillary probes explicitly fall back to existing plugin defaults; do not fake successful schemas.
    print("Unsupported offline mock command", file=sys.stderr)
    sys.exit(1)
