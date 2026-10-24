#!/usr/bin/env python3
"""
Keep the Spanish and Portuguese lang files in step with English (adapted from Aetherhaven's
scripts/build_server_translations.py).

  Source: every *.lang in src/main/resources/Server/Languages/en-US. Output: the same file names in es-ES and pt-BR.

  Incremental by default. tools/translations/<locale>.json remembers a hash of the English text each line was
  translated from, so a run only machine-translates lines that are new or whose English changed. Lines removed from
  English disappear. A line edited by hand in es-ES / pt-BR is kept until its English changes.

  Backend: Google Cloud Translation v2 when GOOGLE_TRANSLATE_API_KEY is set, otherwise Google's free web
  endpoint (no install needed). {placeholders} are protected; a line whose placeholders do not survive the
  round trip stays English and is retried on the next run.

Run (from the mod folder):
  python tools/translate-lang.py              translate what is new or changed
  python tools/translate-lang.py --check      exit 1 if any locale is missing or stale lines (no network)
  python tools/translate-lang.py --only pt-BR
  python tools/translate-lang.py --force      retranslate everything (loses hand edits)
"""
from __future__ import annotations

import argparse
import hashlib
import html
import json
import os
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

LANGS = [("es-ES", "es"), ("pt-BR", "pt")]
# Google's free web endpoint (the one translate.google.com's own pages use); deep_translator's page scraper is
# redirected to a consent page from here, so it is called directly. Keep the worker count low.
FREE_ENDPOINT = "https://translate.googleapis.com/translate_a/single"

ROOT = Path(__file__).resolve().parents[1]
LANG_DIR = ROOT / "src/main/resources/Server/Languages"
SRC_DIR = LANG_DIR / "en-US"
STATE_DIR = ROOT / "tools/translations"

LINE = re.compile(r"^(\s*)([^#=\s][^=]*?)(\s*=\s?)(.*)$")
PH = re.compile(r"\{[A-Za-z0-9_.]+\}")


def parse(path: Path) -> list[tuple[str, str | None, str | None, str]]:
    """Each line as (raw, key, separator-prefix, value); comments and blanks have key None."""
    out = []
    if not path.is_file():
        return out
    for raw in path.read_text(encoding="utf-8").lstrip("﻿").splitlines():
        m = LINE.match(raw)
        if m and not raw.lstrip().startswith("#"):
            out.append((raw, m.group(2), m.group(1) + m.group(2) + m.group(3), m.group(4)))
        else:
            out.append((raw, None, None, raw))
    return out


def digest(text: str) -> str:
    return hashlib.sha1(text.encode("utf-8")).hexdigest()[:12]


def protect(text: str) -> tuple[str, list[str]]:
    tokens = PH.findall(text)
    for i, tok in enumerate(tokens):
        text = text.replace(tok, "{" + str(i) + "}", 1)  # numbered braces survive; __PH_0__ came back as "pH 0"
    return text, tokens


def restore(text: str, tokens: list[str]) -> str | None:
    for i, tok in enumerate(tokens):
        mark = "{" + str(i) + "}"
        if mark not in text:
            return None
        text = text.replace(mark, tok, 1)
    return text


class Translator:
    cloud_capped = False

    def __init__(self, target: str):
        self.target = target
        self.key = (os.environ.get("GOOGLE_TRANSLATE_API_KEY") or "").strip()

    def __call__(self, text: str) -> str | None:
        # The Cloud API (when a key is set) is retried briefly on a hiccup. A quota refusal ("userRateLimitExceeded",
        # a cap set on the Google Cloud project) stops Cloud calls for the rest of the run; the free endpoint takes over.
        if self.key and not Translator.cloud_capped:
            for attempt in range(4):
                try:
                    url = "https://translation.googleapis.com/language/translate/v2?key=" + urllib.parse.quote(self.key, safe="")
                    body = json.dumps({"q": text, "source": "en", "target": self.target, "format": "text"}).encode("utf-8")
                    req = urllib.request.Request(url, data=body, headers={"Content-Type": "application/json; charset=utf-8"})
                    with urllib.request.urlopen(req, timeout=60) as resp:
                        result = json.load(resp)["data"]["translations"][0]["translatedText"]
                    if result and str(result).strip():
                        return html.unescape(str(result).strip())
                except urllib.error.HTTPError as e:
                    detail = e.read().decode("utf-8", "replace")
                    if any(r in detail for r in ("userRateLimitExceeded", "dailyLimitExceeded", "quotaExceeded")):
                        if not Translator.cloud_capped:
                            Translator.cloud_capped = True
                            print("  Cloud API refused: quota reached on this key's project. Using the free endpoint for the rest "
                                  "of this run.", file=sys.stderr, flush=True)
                        break
                    time.sleep(1.5 * (attempt + 1))
                except Exception:
                    time.sleep(1.5 * (attempt + 1))
        for attempt in range(6):
            try:
                url = FREE_ENDPOINT + "?" + urllib.parse.urlencode(
                    {"client": "gtx", "sl": "en", "tl": self.target, "dt": "t", "q": text})
                with urllib.request.urlopen(url, timeout=60) as resp:
                    result = "".join(part[0] for part in json.load(resp)[0] if part and part[0])
                if result and result.strip():
                    return result.strip()
            except Exception:
                time.sleep(1.0 * (attempt + 1))
        return None


def translate_value(tr: Translator, value: str) -> str | None:
    if not value.strip() or not re.search(r"[A-Za-z]", PH.sub("", value)):
        return value
    masked, tokens = protect(value)
    out = tr(masked)
    if out is None:
        return None
    return restore(out, tokens)


def run_locale(folder: str, code: str, force: bool, check: bool, workers: int) -> int:
    """Returns the number of lines that were (check: would be) translated."""
    state_path = STATE_DIR / f"{folder}.json"
    state = {} if force or not state_path.is_file() else json.loads(state_path.read_text(encoding="utf-8"))
    new_state: dict[str, dict[str, str]] = {}
    tr = None if check else Translator(code)
    pending = 0
    failed = 0
    for src in sorted(SRC_DIR.glob("*.lang")):
        en = parse(src)
        dest_path = LANG_DIR / folder / src.name
        have = {k: v for _, k, _, v in parse(dest_path) if k}
        known = state.get(src.name, {})
        jobs = []
        for i, (_, key, prefix, value) in enumerate(en):
            if key is None:
                continue
            h = digest(value)
            if key in have and known.get(key) == h:
                continue
            jobs.append((i, key, value, h))
        pending += len(jobs)
        if check:
            if jobs:
                print(f"  {folder}/{src.name}: {len(jobs)} line(s) missing or stale")
            continue
        results: dict[int, str | None] = {}
        if jobs:
            print(f"  {folder}/{src.name}: translating {len(jobs)} line(s)", flush=True)
            with ThreadPoolExecutor(max_workers=workers) as ex:
                for (i, _, _, _), out in zip(jobs, ex.map(lambda j: translate_value(tr, j[2]), jobs)):
                    results[i] = out
        lines = []
        file_state = {}
        for i, (raw, key, prefix, value) in enumerate(en):
            if key is None:
                lines.append(raw)
                continue
            if i in results:
                out = results[i]
                if out is None:
                    failed += 1
                    lines.append(prefix + value)  # English for now; no hash, so it is retried next run
                    continue
                lines.append(prefix + out)
            else:
                lines.append(prefix + have[key])
            file_state[key] = digest(value)
        new_state[src.name] = file_state
        dest_path.parent.mkdir(parents=True, exist_ok=True)
        dest_path.write_text("\n".join(lines) + "\n", encoding="utf-8", newline="\n")
        # progress survives an interrupted run
        STATE_DIR.mkdir(parents=True, exist_ok=True)
        state_path.write_text(json.dumps({**state, **new_state}, indent=1, sort_keys=True) + "\n", encoding="utf-8", newline="\n")
    # lang files that no longer exist in English
    for stale in (LANG_DIR / folder).glob("*.lang") if not check else []:
        if not (SRC_DIR / stale.name).is_file():
            stale.unlink()
            print(f"  removed {folder}/{stale.name} (no English source)")
    if not check:
        STATE_DIR.mkdir(parents=True, exist_ok=True)
        state_path.write_text(json.dumps(new_state, indent=1, sort_keys=True) + "\n", encoding="utf-8", newline="\n")
        if failed:
            print(f"  {failed} line(s) left in English after retries; run again later", file=sys.stderr)
    return pending


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--check", action="store_true", help="report missing or stale lines and exit 1 if any (no network)")
    ap.add_argument("--force", action="store_true", help="retranslate every line")
    ap.add_argument("--only", default="", help="comma-separated locales, e.g. pt-BR")
    ap.add_argument("--workers", type=int, default=3)
    args = ap.parse_args()
    only = {x.strip() for x in args.only.split(",") if x.strip()}
    total = 0
    for folder, code in LANGS:
        if only and folder not in only:
            continue
        print(f"--- {folder} ---", flush=True)
        total += run_locale(folder, code, args.force, args.check, max(1, min(16, args.workers)))
    if args.check:
        print("translations up to date" if total == 0 else f"{total} line(s) to translate: python tools/translate-lang.py")
        sys.exit(1 if total else 0)
    print("Done.")


if __name__ == "__main__":
    main()
