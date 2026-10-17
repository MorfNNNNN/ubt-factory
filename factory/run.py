"""Factory runner (GitHub Actions): sync accounts -> claim script -> render ->
upload media to the worker -> publish to every account -> report back.

Usage: python factory/run.py [--max-scripts N] [--render-only] [--no-publish]
Env: WORKER_URL, WORKER_TOKEN (admin), COMPOSIO_API_KEY (optional),
DEFAULT_PROJECT, plus direct-channel secrets (see publish.py).
"""
from __future__ import annotations

import argparse
import json
import os
import subprocess
import sys
import tempfile
import time
from pathlib import Path

import requests

CLEAN_ENV = ("COMPOSIO_API_KEY", "COMPOSIO_USER_ID", "RELEASE_ENABLED", "DEFAULT_PROJECT", "WORKER_URL")


def clean_env() -> None:
    """Tolerate values pasted as NAME=value, with quotes or stray whitespace."""
    for n in CLEAN_ENV:
        v = os.environ.get(n)
        if v is None:
            continue
        c = v.strip().strip('"').strip("'").strip()
        if c.upper().startswith(n + "="):
            c = c[len(n) + 1:].strip().strip('"').strip("'").strip()
        if n == "RELEASE_ENABLED":
            c = c.lower()
        if c != v:
            os.environ[n] = c
            print(f"note: {n} normalized (extra prefix/quotes/whitespace removed)", flush=True)


clean_env()

sys.path.insert(0, str(Path(__file__).resolve().parent))
import publish as _publish
from publish import Ctx, Media, publish_all, safe_error, ADAPTERS
from briefs import enrich as enrich_script
from plan import daily_plan

TOPICS = json.loads((Path(__file__).resolve().parent / "topics.json").read_text())

WORKER = os.environ.get("WORKER_URL", "https://connectorlab-core.morfnegoden.workers.dev").rstrip("/")
TOKEN = os.environ.get("WORKER_TOKEN", "")
HDRS = {"Authorization": "Bearer " + TOKEN}
KNOWN_PLATFORMS = {"youtube", "tiktok", "instagram", "facebook", "pinterest",
                   "linkedin", "twitter", "reddit", "telegram", "discord"}


def refresh_oidc() -> None:
    endpoint = os.environ.get("ACTIONS_ID_TOKEN_REQUEST_URL")
    token = os.environ.get("ACTIONS_ID_TOKEN_REQUEST_TOKEN")
    if endpoint and token:
        r = requests.get(endpoint, params={"audience": "ubt-factory"},
                         headers={"Authorization": "Bearer " + token}, timeout=30)
        r.raise_for_status()
        HDRS["Authorization"] = "Bearer " + r.json()["value"]


def api(method: str, path: str, **kw):
    refresh_oidc()
    r = requests.request(method, WORKER + path, headers=HDRS, timeout=180, **kw)
    if r.status_code >= 400:
        raise RuntimeError(f"{method} {path}: {r.status_code} {r.text[:250]}")
    return r.json()


_DISCOVERED_USER_ID = ""
COMPOSIO_URL = "https://backend.composio.dev/api/v3/connected_accounts"
INVENTORY: list = []  # ACTIVE connected accounts visible to COMPOSIO_API_KEY


def composio_inventory() -> tuple[int, list]:
    key = os.environ.get("COMPOSIO_API_KEY") or ""
    if not key:
        return 0, []
    items, cursor = [], None
    for _ in range(5):
        params = {"statuses": "ACTIVE", "limit": 100}
        if cursor:
            params["cursor"] = cursor
        r = requests.get(COMPOSIO_URL, headers={"x-api-key": key}, params=params, timeout=30)
        if r.status_code != 200:
            return r.status_code, []
        j = r.json()
        items += j.get("items", [])
        cursor = j.get("next_cursor")
        if not cursor:
            break
    return 200, items


def _tk(a: dict) -> str:
    tk = (a.get("toolkit") or {}).get("slug") or ""
    return {"twitter": "x"}.get(tk, tk)


def resolve_accounts(accounts: list) -> list:
    """Map ledger accounts to live Composio connected accounts; drop unresolvable
    ones BEFORE any reservation so a bad id can never brick an account."""
    out = []
    for acc in accounts:
        if acc.get("kind") != "composio":
            out.append(acc)
            continue
        if str(acc["id"]).startswith("ca_"):
            hit = [a for a in INVENTORY if a.get("id") == acc["id"]]
        else:
            same = [a for a in INVENTORY if _tk(a) == acc.get("platform")]
            tail = str(acc["id"]).split("_", 1)[-1].lower()
            named = [a for a in same if tail and tail in json.dumps(a, ensure_ascii=False).lower()]
            hit = named or same
        if not hit:
            print(f"[skipped] {acc.get('platform')}: no ACTIVE Composio connection for this API key", flush=True)
            continue
        ca = hit[0]
        _publish.ACCOUNT_RESOLVE[acc["id"]] = (ca["id"], str(ca.get("user_id") or ""))
        out.append(acc)
    return out


def preflight() -> bool:
    """Loud credential check: returns False when publishing cannot work."""
    key = os.environ.get("COMPOSIO_API_KEY") or ""
    print(f"preflight: RELEASE_ENABLED={os.environ.get('RELEASE_ENABLED')!r}", flush=True)
    print(f"preflight: COMPOSIO_API_KEY present={bool(key)} len={len(key)} "
          f"starts_ak_={key.startswith('ak_')}", flush=True)
    code, items = composio_inventory()
    INVENTORY[:] = items
    print(f"preflight: composio inventory http={code} active_accounts={len(items)} "
          f"toolkits={sorted({_tk(a) for a in items})}", flush=True)
    if code != 200:
        print("::error::COMPOSIO_API_KEY rejected by Composio (HTTP %s). Create a new project API key "
              "at dashboard.composio.dev -> Settings -> API Keys and paste ONLY the key value "
              "into the COMPOSIO_API_KEY secret." % code, flush=True)
        return False
    need = {"facebook", "instagram", "pinterest", "linkedin", "youtube"}
    missing = need - {_tk(a) for a in items}
    if missing:
        print(f"::warning::No ACTIVE Composio connection for: {sorted(missing)}", flush=True)
    return bool(items)


def sync_accounts() -> None:
    """Push the current account inventory to the worker. Best effort."""
    global _DISCOVERED_USER_ID
    accounts = []
    key = os.environ.get("COMPOSIO_API_KEY")
    user_id = os.environ.get("COMPOSIO_USER_ID")
    if key and not user_id:
        ids = {str(a.get("user_id") or "") for a in INVENTORY} - {""}
        if len(ids) == 1:
            _DISCOVERED_USER_ID = ids.pop()
            print("composio user id discovered from inventory", flush=True)
    if key and INVENTORY:
        try:
            for a in INVENTORY:
                tk = (a.get("toolkit") or {}).get("slug") or ""
                tk = {"twitter": "x"}.get(tk, tk)
                if tk in ADAPTERS and a.get("id"):
                    accounts.append({"id": a["id"], "platform": tk, "kind": "composio",
                                     "alias": (a.get("id") or "")[:8]})
        except Exception as e:
            print("composio sync failed:", safe_error(e), flush=True)
    if os.environ.get("TELEGRAM_BOT_TOKEN") and os.environ.get("TELEGRAM_CHAT_ID"):
        accounts.append({"id": "direct:telegram:" + os.environ["TELEGRAM_CHAT_ID"],
                         "platform": "telegram", "kind": "direct", "alias": "telegram"})
    if os.environ.get("DISCORD_WEBHOOK_URL"):
        accounts.append({"id": "direct:discord", "platform": "discord",
                         "kind": "direct", "alias": "discord"})
    if os.environ.get("BSKY_HANDLE") and os.environ.get("BSKY_APP_PASSWORD"):
        accounts.append({"id": "direct:bluesky:" + os.environ["BSKY_HANDLE"],
                         "platform": "bluesky", "kind": "direct", "alias": "bluesky"})
    if os.environ.get("MASTODON_INSTANCE") and os.environ.get("MASTODON_TOKEN"):
        accounts.append({"id": "direct:mastodon", "platform": "mastodon",
                         "kind": "direct", "alias": "mastodon"})
    if not accounts:
        print("no accounts to sync", flush=True)
        return
    project = os.environ.get("DEFAULT_PROJECT")
    if not project:
        raise RuntimeError("DEFAULT_PROJECT must be explicit for account inventory")
    try:
        r = api("POST", "/f/accounts/sync", json={
            "accounts": accounts,
            "default_project": project})
        print(f"accounts synced: {r.get('synced')}", flush=True)
    except Exception as e:
        print("account sync failed:", safe_error(e), flush=True)


def render_script(script: dict, workdir: Path) -> float:
    sp = workdir / "script.json"
    sp.write_text(json.dumps(script, ensure_ascii=False))
    p = subprocess.run([sys.executable, str(Path(__file__).resolve().parent / "render.py"),
                        str(sp), str(workdir)], capture_output=True, text=True, timeout=900)
    if p.returncode != 0:
        raise RuntimeError(f"render failed: {p.stderr[-400:]}")
    info = json.loads(p.stdout.strip().splitlines()[-1])
    return float(info.get("duration") or 0)


def upload_media(script_id: str, workdir: Path, claim_token: str) -> None:
    targets = [("video", "video.mp4", "video/mp4"), ("cover", "cover.jpg", "image/jpeg")]
    for slide in sorted(workdir.glob("slide_*.png")):
        targets.append(("slide&n=" + slide.stem.split("_")[1], slide.name, "image/png"))
    for kind, fname, ctype in targets:
        data = (workdir / fname).read_bytes()
        refresh_oidc()
        r = requests.put(f"{WORKER}/f/media?script={script_id}&kind={kind}",
                         headers={**HDRS, "Content-Type": ctype, "X-Claim-Token": claim_token}, data=data, timeout=600)
        r.raise_for_status()
        print(f"uploaded {fname} ({len(data)//1024} KB)", flush=True)


PUBLISHED: list = []


def process_one(no_publish: bool) -> bool:
    claim = api("POST", "/f/claim", json={"mode": "render" if no_publish else "publish"})
    if claim.get("empty"):
        print("no eligible scripts:", claim.get("reason") or "", flush=True)
        return False
    script, accounts, lease = claim["script"], claim.get("accounts", []), claim["claim_token"]
    print(f"claimed {script['id']} project={script.get('project')}", flush=True)
    # Slim queue rows carry beats only; captions are rebuilt deterministically here.
    script = enrich_script(script, TOPICS)
    want_project = os.environ.get("DEFAULT_PROJECT")
    if want_project and script.get("project") != want_project:
        report([], done=True)
        raise SystemExit(f"project mismatch: claimed {script.get('project')}, want {want_project}")
    def report(results, done=False, rendered=False):
        return api("POST", "/f/report", json={"script": script["id"], "claim_token": lease,
                   "results": results, "done": done, "rendered": rendered})
    rendered = False
    try:
        with tempfile.TemporaryDirectory() as td:
            workdir = Path(td)
            duration = render_script(script, workdir)
            print(f"rendered {duration:.1f}s", flush=True)
            upload_media(script["id"], workdir, lease)
            rendered = True
            if not no_publish:
                media = Media(workdir, script["id"], WORKER + "/m/")
                plan = daily_plan(__import__("datetime").date.today())
                print(f"daily plan: {plan}", flush=True)
                ctx = Ctx(os.environ.get("COMPOSIO_API_KEY"),
                          os.environ.get("COMPOSIO_USER_ID") or _DISCOVERED_USER_ID or "ubt-factory",
                          WORKER + "/go/", script["project"], formats=plan)
                def reserve(acc):
                    api("POST", "/f/begin", json={"script": script["id"],
                        "claim_token": lease, "account": acc["id"]})
                accounts = resolve_accounts(accounts)
                res = publish_all(accounts, script, media, ctx, before_publish=reserve,
                                  on_result=lambda result: report([result]))
                PUBLISHED.extend(r for r in res if r.get("state") in ("posted", "accepted"))
    except Exception as e:
        # Do not invent failures for accounts never attempted, or overwrite receipts.
        print("pipeline error:", safe_error(e), flush=True)
        raise
    finally:
        report([], done=True, rendered=rendered)
    return True


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--max-scripts", type=int, choices=[1], default=1)
    ap.add_argument("--render-only", action="store_true",
                    help="render and upload media, skip publishing")
    ap.add_argument("--check", action="store_true", help="credential preflight only")
    args = ap.parse_args()
    # Fail closed: public sends require a distinct hosted release switch.
    if os.environ.get("RELEASE_ENABLED") != "true":
        args.render_only = True
    ok = preflight()
    if args.check:
        raise SystemExit(0 if ok else 1)
    if not args.render_only and not ok:
        raise SystemExit("publishing enabled but Composio credentials do not work - see preflight above")
    global TOKEN
    if not TOKEN:
        # GitHub OIDC is short-lived and scoped to factory routes, not the admin API.
        endpoint = os.environ.get("ACTIONS_ID_TOKEN_REQUEST_URL")
        request_token = os.environ.get("ACTIONS_ID_TOKEN_REQUEST_TOKEN")
        if not endpoint or not request_token:
            raise SystemExit("Set WORKER_TOKEN locally, or run with GitHub id-token: write")
        oidc = requests.get(endpoint, params={"audience": "ubt-factory"},
                            headers={"Authorization": "Bearer " + request_token}, timeout=30)
        oidc.raise_for_status()
        TOKEN = oidc.json()["value"]
        HDRS["Authorization"] = "Bearer " + TOKEN
    print("worker:", WORKER, flush=True)
    sync_accounts()
    done = 0
    while done < args.max_scripts:
        if not process_one(no_publish=args.render_only):
            break
        done += 1
        time.sleep(5)
    print(f"finished: {done} script(s) processed, {len(PUBLISHED)} publication(s)", flush=True)
    if not args.render_only and done and not PUBLISHED:
        raise SystemExit("publishing run produced zero publications - failing loudly")


if __name__ == "__main__":
    main()
