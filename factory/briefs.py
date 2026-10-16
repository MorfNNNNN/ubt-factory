"""Generate validated educational script JSONs from topics.json.

No AI calls: deterministic rendering of an authored topic bank.
Each output script matches the worker factory.mjs validateScript contract
(no URLs, no {link}, no product claims; captions for all platforms).

Usage: python factory/briefs.py topics.json out_dir [--emit-sql out.sql]
"""
from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
import uuid
from pathlib import Path

BRAND = "Семейный щит"
FOOTER = "Памятка подготовлена по рекомендациям Банка России. Образовательный материал, без рекламы."
BASE_TAGS = ["#СемейныйЩит", "#Кибербезопасность", "#Мошенники"]
FORBIDDEN = re.compile(r"https?:|www\.|\{link\}|ссылк[^ ]*\s+(в|из)\s+(описани|профил)|link in (bio|description)", re.I)


def clip(s: str, n: int) -> str:
    s = re.sub(r"\s+", " ", str(s)).strip()
    return s[:n].rstrip()


def beats_for(t: dict) -> list:
    beats = [{"kind": "hook", "text": t["hook"], "screen": t["hook_screen"]}]
    for text, screen in t["points"]:
        beats.append({"kind": "point", "text": text, "screen": screen})
    beats.append({"kind": "cta", "text": t["cta"], "screen": t["cta_screen"]})
    return beats


def captions_for(t: dict, points_plain: list[str]) -> dict:
    title = clip(t["title"], 58)
    hook = t["hook"].rstrip(".")
    tags_line = " ".join(BASE_TAGS)
    body_short = ". ".join(p.rstrip(".") for p in points_plain[:3]) + "."
    long_text = f"{hook}.\n\n{chr(10).join('— ' + p for p in points_plain)}\n\n{t['cta']}\n\n{FOOTER}"
    return {
        "youtube": {"title": title, "description": clip(long_text + " " + tags_line, 900),
                    "tags": [tag.lstrip("#") for tag in BASE_TAGS] + t["tags"]},
        "tiktok": {"caption": clip(f"{hook}. {t['cta']} {tags_line}", 178)},
        "instagram": {"caption": clip(f"{hook}.\n\n{body_short}\n\n{t['cta']}\n{tags_line}", 398)},
        "facebook": {"title": clip(title, 108), "description": clip(long_text, 698)},
        "pinterest": {"title": title, "description": clip(f"{hook}. {body_short} {t['cta']}", 448)},
        "linkedin": {"commentary": clip(long_text, 898)},
        "x": {"text": clip(f"{hook}. {t['cta']} {tags_line}", 228)},
        "telegram": {"text": clip(long_text, 898)},
        "reddit": {"title": clip(title + " — памятка для семьи", 278), "body": clip(long_text, 2498)},
        "bluesky": {"text": clip(f"{hook}. {t['cta']} {tags_line}", 228)},
        "discord": {"text": clip(long_text, 498)},
        "mastodon": {"text": clip(f"{hook}. {t['cta']} {tags_line}", 228)},
    }


def slim_body(full: dict) -> dict:
    """Queue form stored in D1: no captions (enriched in CI from topics.json)."""
    return {k: full[k] for k in ("language", "brand", "rubric", "theme", "seed", "topic", "beats", "promotional", "sources")}


def enrich(script: dict, topics: list) -> dict:
    """Add deterministic captions to a slim script claimed from the queue."""
    if script.get("captions"):
        return script
    tid = script.get("topic")
    t = next((x for x in topics if x["id"] == tid), None)
    if not t:
        raise ValueError(f"topic not found for enrichment: {tid}")
    full = script_for(t, list(x["id"] for x in topics).index(tid))
    slim = slim_body(full)
    for k in ("language", "brand", "theme", "seed"):
        if slim[k] != script.get(k):
            raise ValueError(f"topic drift on {k}: queue={script.get(k)!r} bank={slim[k]!r}")
    return {**script, "captions": full["captions"]}


def script_for(t: dict, idx: int) -> dict:
    points_plain = [p[0] for p in t["points"]]
    s = {
        "language": "ru",
        "brand": BRAND,
        "rubric": "tips",
        "theme": int(t.get("theme", idx)) % 5,
        "seed": 1000 + idx * 613,
        "topic": t["id"],
        "beats": beats_for(t),
        "captions": captions_for(t, points_plain),
        "promotional": False,
        "sources": ["https://www.cbr.ru/faq/information_security/"],
    }
    return s


def lint(s: dict) -> list[str]:
    """Local pre-check mirroring validateScript limits; real validator runs after."""
    errs = []
    payload = json.dumps({"beats": s["beats"], "captions": s["captions"]}, ensure_ascii=False)
    if FORBIDDEN.search(payload):
        errs.append("forbidden pattern (url/link)")
    if not (4 <= len(s["beats"]) <= 7):
        errs.append("beats count")
    total = 0
    for b in s["beats"]:
        text = re.sub(r"[*_#`>\[\]]", "", b["text"]).strip()
        total += len(text)
        if not (10 <= len(text) <= 200):
            errs.append(f"beat text len {len(text)}")
        if not (3 <= len(b["screen"]) <= 80):
            errs.append(f"beat screen len {len(b['screen'])}")
    if total > 750:
        errs.append(f"total {total}")
    for p in ("youtube", "tiktok", "instagram"):
        if p not in s["captions"]:
            errs.append(f"missing caption {p}")
    return errs


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("topics", type=Path)
    ap.add_argument("out", type=Path)
    ap.add_argument("--emit-sql", type=Path)
    a = ap.parse_args()
    topics = json.loads(a.topics.read_text())
    a.out.mkdir(parents=True, exist_ok=True)
    rows, bad = [], 0
    for i, t in enumerate(topics):
        s = script_for(t, i)
        errs = lint(s)
        if errs:
            bad += 1
            print(f"{t['id']}: FAIL {errs}", flush=True)
            continue
        body = json.dumps(slim_body(s), ensure_ascii=False, separators=(",", ":"))
        sid = str(uuid.uuid5(uuid.NAMESPACE_URL, "ubt-family-shield-ru:" + t["id"]))
        (a.out / f"{sid}.json").write_text(json.dumps({**s, "id": sid}, ensure_ascii=False, indent=1))
        rows.append((sid, body, hashlib.sha256(body.encode()).hexdigest()))
        print(f"{t['id']}: ok {sid} ({len(body)} B)", flush=True)
    if a.emit_sql and rows:
        lines = []
        for sid, body, digest in rows:
            esc = body.replace("'", "''")
            lines.append(
                "INSERT INTO scripts(id,project,language,body,state,created,digest,approved_digest) "
                f"VALUES('{sid}','ubt-family-shield-ru','ru','{esc}','ready',CAST(strftime('%s','now') AS INTEGER),'{digest}','{digest}') "
                "ON CONFLICT(id) DO NOTHING;")
        a.emit_sql.write_text("\n".join(lines) + "\n")
        print(f"sql: {a.emit_sql} ({len(rows)} rows)", flush=True)
    if bad:
        sys.exit(1)


if __name__ == "__main__":
    main()
