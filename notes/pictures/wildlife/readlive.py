"""Prints the ranch chat lines (key and params) and the wildlife log lines of a mcp-session output file.
    python notes/pictures/wildlife/readlive.py <session output>"""
import json
import re
import sys

raw = open(sys.argv[1], "rb").read()
text = raw.decode("utf-16") if raw[:2] in (b"\xff\xfe", b"\xfe\xff") else raw.decode("utf-8", errors="replace")
logs = re.findall(r'"logPath"\s*:\s*"([^"]+)"', text)
if logs:
    try:
        text += "\n" + open(logs[0].replace("\\\\", "\\"), encoding="utf-8", errors="replace").read()
    except OSError:
        pass


def flat(m):
    if isinstance(m, dict):
        out = m.get("messageId") or m.get("MessageId") or m.get("rawText") or ""
        params = {}
        for k in ("params", "Params"):
            params.update({a: (b.get("value") if isinstance(b, dict) and "value" in b else b) for a, b in (m.get(k) or {}).items()})
        for k in ("messageParams", "MessageParams"):
            params.update({a: flat(b) for a, b in (m.get(k) or {}).items()})
        if params:
            out += " " + json.dumps(params, ensure_ascii=False)
        return out
    return str(m)


for chunk in re.finditer(r'"message"\s*:\s*(\{)', text):
    start = chunk.start(1)
    depth = 0
    for i in range(start, len(text)):
        if text[i] == "{":
            depth += 1
        elif text[i] == "}":
            depth -= 1
            if depth == 0:
                try:
                    m = json.loads(text[start:i + 1])
                    s = flat(m)
                    if "cozyranch" in s:
                        print("CHAT", s)
                except json.JSONDecodeError:
                    pass
                break

seen = set()
for m in re.finditer(r"[^\n\"]*(Wildlife|wildlife|Exception|SEVERE)[^\n\"]*", text.replace("\\n", "\n")):
    line = m.group(0).strip()
    if line and line not in seen and "cozyranch_wildlife" not in line:
        seen.add(line)
        print("LOG ", line[:400])
