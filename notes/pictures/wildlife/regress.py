"""Copies the shared test cases with this worktree's paths and port 15541 into build/wildlife-regress/, and writes
tools/sessions/wildlife-tests.json to run them (python notes/pictures/wildlife/regress.py, then
node tools/mcp-session.mjs tools/sessions/wildlife-tests.json)."""
import json
import os

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", ".."))
PROJECT = "_worktrees/CozyTales-Ranching-wildlife"
NAMES = ["animator", "animal", "models-a", "models-b", "taming", "houseui", "breeding", "care", "house", "wildlife"]
out_dir = os.path.join(ROOT, "build", "wildlife-regress")
os.makedirs(out_dir, exist_ok=True)


def fix(node):
    if isinstance(node, dict):
        if node.get("tool") == "server_start" and isinstance(node.get("arguments"), dict):
            node["arguments"]["port"] = 15541
        return {k: fix(v) for k, v in node.items()}
    if isinstance(node, list):
        return [fix(v) for v in node]
    if isinstance(node, str) and node.startswith("CozyTales-Ranching/"):
        return PROJECT + "/" + node[len("CozyTales-Ranching/"):]
    return node


count = 0
for name in NAMES:
    src = os.path.join(ROOT, "tests", "hytale", name + ".test.json")
    if not os.path.isfile(src):
        continue
    data = fix(json.load(open(src, encoding="utf-8")))
    with open(os.path.join(out_dir, name + ".test.json"), "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, indent=2)
    count += 1

steps = [
    {"tool": "test_run", "args": {"project": PROJECT, "directory": "build/wildlife-regress"}, "save": "t"},
    {"tool": "job_status", "args": {"jobId": "$t", "waitMs": 5000}, "until": "finishedAt", "timeoutMs": 1800000, "maxChars": 6000},
]
with open(os.path.join(ROOT, "tools", "sessions", "wildlife-tests.json"), "w", encoding="utf-8", newline="\n") as f:
    f.write("[\n" + ",\n".join("  " + json.dumps(s) for s in steps) + "\n]\n")
print(count, "test files")
