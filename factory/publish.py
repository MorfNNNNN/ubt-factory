"""Publish rendered short videos to connected platforms.

Composio adapters: YouTube Shorts, TikTok, Instagram Reels, Facebook Page,
Pinterest, LinkedIn, X, Reddit (off by default, needs meta.subreddit).
Direct adapters: Telegram (bot), Discord (webhook), Bluesky (app password),
Mastodon (token).

Usage: python factory/publish.py script.json accounts.json media_dir [results.json]
Env: COMPOSIO_API_KEY, COMPOSIO_USER_ID, MEDIA_BASE, CTA_BASE,
TELEGRAM_BOT_TOKEN/TELEGRAM_CHAT_ID, DISCORD_WEBHOOK_URL,
BSKY_HANDLE/BSKY_APP_PASSWORD, MASTODON_INSTANCE/MASTODON_TOKEN.
"""
from __future__ import annotations

import json
import os
import re
import sys
import time
from dataclasses import dataclass
from pathlib import Path

import requests


class PublishError(RuntimeError):
    pass


@dataclass
class Media:
    dir: Path
    script_id: str
    media_base: str  # public base with trailing slash, e.g. https://host/m/

    @property
    def video(self) -> Path:
        return self.dir / "video.mp4"

    @property
    def cover(self) -> Path:
        return self.dir / "cover.jpg"

    @property
    def slides(self) -> list:
        return sorted(self.dir.glob("slide_*.png"))

    def slide_url(self, i: int) -> str:
        return f"{self.media_base}media/{self.script_id}/slide_{i:02d}.png"

    @property
    def video_url(self) -> str:
        return f"{self.media_base}media/{self.script_id}/video.mp4"

    @property
    def cover_url(self) -> str:
        return f"{self.media_base}media/{self.script_id}/cover.jpg"


@dataclass
class Ctx:
    composio_key: str | None
    user_id: str
    cta_base: str  # tracked redirect base with trailing slash, e.g. https://host/go/
    project: str
    promotional: bool = False
    formats: dict | None = None  # daily per-platform plan from factory/plan.py

    def link(self, platform: str) -> str:
        if not self.promotional:
            raise PublishError("PROMOTIONAL_LINKS_NOT_AUTHORIZED")
        return f"{self.cta_base}{platform}-{self.project}"


def captions(script: dict, platform: str, ctx: Ctx) -> dict:
    caps = dict((script.get("captions") or {}).get(platform) or {})
    def transform(v):
        if isinstance(v, str):
            if not ctx.promotional and ("{link}" in v or re.search(r"(?i)(?:https?://|www\.|t\.me/|tg://|mailto:)", v)):
                raise PublishError("LINK_IN_NO_PROMOTION_MODE")
            return v.replace("{link}", ctx.link(platform)) if "{link}" in v else v
        if isinstance(v, list):
            return [transform(x) for x in v]
        if isinstance(v, dict):
            return {k: transform(x) for k, x in v.items()}
        return v
    return transform(caps)


def hook_line(script: dict) -> str:
    try:
        return script["beats"][0]["screen"].replace("*", "").strip()
    except Exception:
        return "Новый ролик"


# ---------------------------------------------------------------- composio
_client = None


def _cx(ctx: Ctx):
    global _client
    if _client is None:
        if not ctx.composio_key:
            raise PublishError("NO_COMPOSIO_KEY")
        from composio import Composio

        _client = Composio(api_key=ctx.composio_key)
    return _client


def _dump(resp) -> dict:
    if isinstance(resp, dict):
        return resp
    for attr in ("model_dump", "dict"):
        f = getattr(resp, attr, None)
        if callable(f):
            try:
                return f()
            except Exception:
                pass
    d = getattr(resp, "__dict__", None)
    return d if isinstance(d, dict) else {"raw": str(resp)[:400]}


def exec_tool(ctx: Ctx, slug: str, args: dict, account_id: str) -> dict:
    resp = _cx(ctx).tools.execute(slug, arguments=args, connected_account_id=account_id, user_id=ctx.user_id)
    d = _dump(resp)
    if d.get("successful") is not True:
        raise PublishError(f"{slug}: {str(d.get('error'))[:260]}")
    data = d.get("data")
    return data if isinstance(data, dict) else {"data": data}


def find(d, *names, depth=6):
    """Recursively find the first non-empty value for any of names."""
    if depth < 0 or d is None:
        return None
    if isinstance(d, dict):
        for n in names:
            if d.get(n):
                return d[n]
        for v in d.values():
            r = find(v, *names, depth=depth - 1)
            if r:
                return r
    elif isinstance(d, list):
        for v in d:
            r = find(v, *names, depth=depth - 1)
            if r:
                return r
    return None


def stage_media_file(ctx: Ctx, media: Media, path: Path, slug: str, toolkit: str) -> dict:
    """Explicit SDK 0.27 staging; never hand a raw local path to a remote tool."""
    root = media.dir.resolve()
    path = path.resolve()
    if not path.is_relative_to(root) or not path.is_file():
        raise PublishError("MEDIA_FILE_OUTSIDE_APPROVED_DIRECTORY")
    if path.suffix.lower() not in (".mp4", ".jpg", ".jpeg", ".png"):
        raise PublishError("MEDIA_FILE_TYPE_NOT_ALLOWED")
    if path.stat().st_size > 64 * 1024 * 1024:
        raise PublishError("MEDIA_FILE_TOO_LARGE")
    from importlib.metadata import version
    if version("composio") != "0.27.0":
        raise PublishError("COMPOSIO_STAGING_VERSION_REVIEW_REQUIRED")
    from composio.core.models._files import FileUploadable
    return FileUploadable.from_path(
        client=_cx(ctx)._client, file=path, tool=slug, toolkit=toolkit,
        sensitive_file_upload_protection=True, file_upload_allowlist=[root],
    ).model_dump()


# ---------------------------------------------------------------- composio adapters
def pub_youtube(acc, script, media, ctx):
    c = captions(script, "youtube", ctx)
    d = exec_tool(ctx, "YOUTUBE_UPLOAD_VIDEO", {
        "title": (c.get("title") or hook_line(script))[:100],
        "description": (c.get("description") or "")[:4500],
        "tags": [str(t)[:30] for t in (c.get("tags") or [])][:15],
        "categoryId": str(acc["meta"].get("category_id", "27")),
        "privacyStatus": acc["meta"].get("privacy", "public"),
        "videoFilePath": stage_media_file(ctx, media, media.video, "YOUTUBE_UPLOAD_VIDEO", "youtube"),
    }, acc["id"])
    vid = d.get("id") or d.get("videoId") or d.get("video_id")
    if not isinstance(vid, str) or not re.fullmatch(r"[A-Za-z0-9_-]{11}", vid):
        raise PublishError("YOUTUBE_RECEIPT_ID_UNVERIFIED")
    return {"remote_id": vid, "url": f"https://youtu.be/{vid}", "state": "accepted"}


def pub_tiktok(acc, script, media, ctx):
    c = captions(script, "tiktok", ctx)
    d = exec_tool(ctx, "TIKTOK_UPLOAD_VIDEO", {
        "file_to_upload": str(media.video),
        "publish": bool(acc["meta"].get("publish", True)),
        "caption": (c.get("caption") or "")[:2000],
        "privacy_level": acc["meta"].get("privacy_level", "SELF_ONLY"),
        "is_aigc": True,
        "brand_organic_toggle": True,
    }, acc["id"])
    pid = find(d, "publish_id", "id")
    return {"remote_id": str(pid) if pid else None}


def pub_instagram(acc, script, media, ctx):
    fmt = (ctx.formats or {}).get("instagram", "reel")
    c = captions(script, "instagram", ctx)
    uid = acc["meta"].get("ig_user_id")
    if not uid:
        info = exec_tool(ctx, "INSTAGRAM_GET_USER_INFO", {"ig_user_id": "me"}, acc["id"])
        uid = find(info, "ig_user_id", "id")
    if not uid:
        raise PublishError("IG_USER_ID_UNKNOWN")
    uid = str(uid)
    cap = (c.get("caption") or "")[:2000]

    def create(args):
        d = exec_tool(ctx, "INSTAGRAM_POST_IG_USER_MEDIA", args, acc["id"])
        cid = find(d, "creation_id", "id")
        if not cid:
            raise PublishError("IG_CONTAINER_MISSING")
        return str(cid)

    def publish(cid):
        out = exec_tool(ctx, "INSTAGRAM_POST_IG_USER_MEDIA_PUBLISH",
                        {"ig_user_id": uid, "creation_id": cid, "max_wait_seconds": 300}, acc["id"])
        pid = find(out, "id")
        if not pid:
            raise PublishError("IG_PUBLISH_ID_MISSING")
        return str(pid)

    def do_reel():
        return publish(create({"ig_user_id": uid, "media_type": "REELS", "video_url": media.video_url,
                               "cover_url": media.cover_url, "caption": cap, "share_to_feed": True}))

    def do_photo():
        return publish(create({"ig_user_id": uid, "image_url": media.cover_url, "caption": cap}))

    def do_story():
        return publish(create({"ig_user_id": uid, "media_type": "STORIES",
                               "image_url": media.slide_url(0)}))

    def do_carousel():
        kids = [create({"ig_user_id": uid, "image_url": media.slide_url(i), "is_carousel_item": True})
                for i in range(min(3, len(media.slides)))]
        if len(kids) < 2:
            raise PublishError("IG_CAROUSEL_NEEDS_2_SLIDES")
        return publish(create({"ig_user_id": uid, "media_type": "CAROUSEL",
                               "children": kids, "caption": cap}))

    handlers = {"reel": do_reel, "photo": do_photo, "story": do_story, "carousel": do_carousel}
    ids = []
    for part in fmt.split("+"):
        fn = handlers.get(part)
        if not fn:
            raise PublishError(f"IG_FORMAT_UNKNOWN:{part}")
        ids.append(fn())
    return {"remote_id": ",".join(ids)}


def pub_facebook(acc, script, media, ctx):
    c = captions(script, "facebook", ctx)
    page = acc["meta"].get("page_id")
    if not page:
        raise PublishError("EXPLICIT_FACEBOOK_PAGE_REQUIRED")
    if (ctx.formats or {}).get("facebook") == "photo":
        d = exec_tool(ctx, "FACEBOOK_CREATE_PHOTO_POST", {
            "page_id": str(page), "url": media.cover_url,
            "message": (c.get("description") or "")[:500], "published": True,
        }, acc["id"])
        pid = find(d, "post_id", "id")
        return {"remote_id": str(pid) if pid else None,
                "url": f"https://www.facebook.com/{pid}" if pid else None}
    d = exec_tool(ctx, "FACEBOOK_CREATE_VIDEO_POST", {
        "page_id": str(page),
        "file_url": media.video_url,
        "title": (c.get("title") or "")[:120],
        "description": (c.get("description") or "")[:2000],
        "published": True,
    }, acc["id"])
    pid = find(d, "post_id", "id")
    return {"remote_id": str(pid) if pid else None,
            "url": f"https://www.facebook.com/{pid}" if pid else None}


def pub_pinterest(acc, script, media, ctx):
    c = captions(script, "pinterest", ctx)
    board = acc["meta"].get("board_id")
    if not board:
        raise PublishError("EXPLICIT_PINTEREST_BOARD_REQUIRED")
    pfmt = (ctx.formats or {}).get("pinterest", "cover")
    source = {"source_type": "image_url", "url": media.cover_url}
    if pfmt == "slide" and media.slides:
        i = int(script.get("seed", 0)) % len(media.slides)
        source = {"source_type": "image_url", "url": media.slide_url(i)}
    elif pfmt == "carousel" and len(media.slides) >= 2:
        source = {"source_type": "multiple_image_urls",
                  "items": [{"url": media.slide_url(i)} for i in range(min(3, len(media.slides)))]}
    args = {
        "board_id": str(board),
        "media_source": source,
        "title": (c.get("title") or "")[:100],
        "description": (c.get("description") or "")[:800],
    }
    if ctx.promotional:
        args["link"] = ctx.link("pinterest")
    d = exec_tool(ctx, "PINTEREST_CREATE_PIN", args, acc["id"])
    pid = find(d, "id")
    return {"remote_id": str(pid) if pid else None,
            "url": f"https://www.pinterest.com/pin/{pid}/" if pid else None}


def pub_linkedin(acc, script, media, ctx):
    c = captions(script, "linkedin", ctx)
    up = exec_tool(ctx, "LINKEDIN_UPLOAD_VIDEO", {"video_url": media.video_url}, acc["id"])
    urn = find(up, "video_urn", "videoUrn", "urn", "id")
    if not urn:
        raise PublishError("LI_NO_URN")
    if not str(urn).startswith("urn:"):
        urn = f"urn:li:video:{urn}"
    d = exec_tool(ctx, "LINKEDIN_CREATE_VIDEO_POST", {
        "video_urn": str(urn),
        "commentary": (c.get("commentary") or "")[:2800],
        "visibility": acc["meta"].get("visibility", "PUBLIC"),
    }, acc["id"])
    pid = find(d, "post_urn", "ugcPost", "id")
    return {"remote_id": str(pid) if pid else None}


def pub_x(acc, script, media, ctx):
    c = captions(script, "x", ctx)
    up = exec_tool(ctx, "TWITTER_UPLOAD_LARGE_MEDIA",
                   {"media": str(media.video), "media_category": "tweet_video"}, acc["id"])
    mid = find(up, "media_id_string", "media_id", "id")
    if not mid:
        raise PublishError("X_NO_MEDIA_ID")
    for _ in range(40):
        st = exec_tool(ctx, "TWITTER_GET_MEDIA_UPLOAD_STATUS", {"media_id": str(mid)}, acc["id"])
        state = str(find(st, "state") or "").lower()
        if state in ("succeeded", "ready"):
            break
        if state == "failed":
            raise PublishError("X_MEDIA_FAILED")
        time.sleep(10)
    else:
        raise PublishError("X_MEDIA_PROCESSING_TIMEOUT")
    d = exec_tool(ctx, "TWITTER_CREATION_OF_A_POST",
                  {"text": (c.get("text") or "")[:270], "media_media_ids": [str(mid)]}, acc["id"])
    tid = find(d, "tweet_id", "id")
    return {"remote_id": str(tid) if tid else None,
            "url": f"https://x.com/i/status/{tid}" if tid else None}


def pub_reddit(acc, script, media, ctx):
    sub = acc["meta"].get("subreddit")
    if not sub:
        raise PublishError("REDDIT_NOT_CONFIGURED")
    c = captions(script, "reddit", ctx)
    d = exec_tool(ctx, "REDDIT_CREATE_REDDIT_POST", {
        "subreddit": sub,
        "title": (c.get("title") or hook_line(script))[:300],
        "body": (c.get("body") or "")[:4000],
        "kind": "self",
    }, acc["id"])
    pid = find(d, "id", "name")
    return {"remote_id": str(pid) if pid else None}


# ---------------------------------------------------------------- direct adapters
def pub_telegram(acc, script, media, ctx):
    token = acc["meta"].get("bot_token") or os.environ.get("TELEGRAM_BOT_TOKEN")
    chat = acc["meta"].get("chat_id") or os.environ.get("TELEGRAM_CHAT_ID")
    if not token or not chat:
        raise PublishError("TG_NOT_CONFIGURED")
    c = captions(script, "telegram", ctx)
    with open(media.video, "rb") as f:
        r = requests.post(f"https://api.telegram.org/bot{token}/sendVideo",
                          data={"chat_id": chat, "caption": (c.get("text") or "")[:1000],
                                "supports_streaming": "true"},
                          files={"video": ("video.mp4", f, "video/mp4")}, timeout=300)
    d = r.json()
    if not d.get("ok"):
        raise PublishError(f"TG_{d.get('error_code')}: {str(d.get('description'))[:200]}")
    msg = d["result"]["message_id"]
    url = f"https://t.me/{str(chat).lstrip('@')}/{msg}" if str(chat).startswith("@") else None
    return {"remote_id": str(msg), "url": url}


def pub_discord(acc, script, media, ctx):
    hook = acc["meta"].get("webhook") or os.environ.get("DISCORD_WEBHOOK_URL")
    if not hook:
        raise PublishError("DISCORD_NOT_CONFIGURED")
    c = captions(script, "discord", ctx)
    with open(media.video, "rb") as f:
        r = requests.post(hook, params={"wait": "true"}, data={"payload_json": json.dumps({"content": (c.get("text") or "")[:1900]})},
                          files={"files[0]": ("video.mp4", f, "video/mp4")}, timeout=300)
    if r.status_code not in (200, 204):
        raise PublishError(f"DISCORD_HTTP_{r.status_code}: {r.text[:150]}")
    j = r.json() if r.status_code == 200 else {}
    if not j.get("id"):
        raise PublishError("DISCORD_NO_MESSAGE_RECEIPT")
    return {"remote_id": str(j["id"])}


def pub_bluesky(acc, script, media, ctx):
    handle = acc["meta"].get("handle") or os.environ.get("BSKY_HANDLE")
    pw = acc["meta"].get("app_password") or os.environ.get("BSKY_APP_PASSWORD")
    if not handle or not pw:
        raise PublishError("BSKY_NOT_CONFIGURED")
    s = requests.post("https://bsky.social/xrpc/com.atproto.server.createSession",
                      json={"identifier": handle, "password": pw}, timeout=30).json()
    if "accessJwt" not in s:
        raise PublishError(f"BSKY_AUTH: {str(s)[:150]}")
    h = {"Authorization": "Bearer " + s["accessJwt"]}
    bj = requests.post("https://bsky.social/xrpc/com.atproto.repo.uploadBlob",
                       headers={**h, "Content-Type": "video/mp4"},
                       data=media.video.read_bytes(), timeout=300).json()
    if "blob" not in bj:
        raise PublishError(f"BSKY_BLOB: {str(bj)[:200]}")
    c = captions(script, "bluesky", ctx)
    rec = {"$type": "app.bsky.feed.post", "text": (c.get("text") or "")[:290],
           "createdAt": time.strftime("%Y-%m-%dT%H:%M:%S.000Z", time.gmtime()),
           "embed": {"$type": "app.bsky.embed.video", "video": bj["blob"],
                     "aspectRatio": {"width": 1080, "height": 1920}}}
    r = requests.post("https://bsky.social/xrpc/com.atproto.repo.createRecord", headers=h,
                      json={"repo": s["did"], "collection": "app.bsky.feed.post", "record": rec},
                      timeout=60).json()
    uri = r.get("uri")
    if not uri:
        raise PublishError(f"BSKY_POST: {str(r)[:200]}")
    rkey = uri.rsplit("/", 1)[-1]
    return {"remote_id": uri, "url": f"https://bsky.app/profile/{handle}/post/{rkey}"}


def pub_mastodon(acc, script, media, ctx):
    base = (acc["meta"].get("instance") or os.environ.get("MASTODON_INSTANCE") or "").rstrip("/")
    token = acc["meta"].get("token") or os.environ.get("MASTODON_TOKEN")
    if not base or not token:
        raise PublishError("MASTODON_NOT_CONFIGURED")
    h = {"Authorization": "Bearer " + token}
    with open(media.video, "rb") as f:
        r = requests.post(base + "/api/v2/media", headers=h,
                          files={"file": ("video.mp4", f, "video/mp4")}, timeout=300)
    if r.status_code not in (200, 202):
        raise PublishError(f"MASTODON_MEDIA_{r.status_code}: {r.text[:150]}")
    mid = r.json()["id"]
    for _ in range(30):
        if requests.get(f"{base}/api/v1/media/{mid}", headers=h, timeout=30).status_code == 200:
            break
        time.sleep(5)
    else:
        raise PublishError("MASTODON_MEDIA_PROCESSING_TIMEOUT")
    c = captions(script, "mastodon", ctx)
    text = (c.get("text") or captions(script, "x", ctx).get("text") or "")[:450]
    r = requests.post(base + "/api/v1/statuses", headers={**h, "Idempotency-Key": media.script_id + ":" + acc["id"]},
                      json={"status": text, "media_ids": [mid]}, timeout=60)
    if r.status_code != 200:
        raise PublishError(f"MASTODON_POST_{r.status_code}: {r.text[:150]}")
    j = r.json()
    return {"remote_id": j.get("id"), "url": j.get("url")}


ADAPTERS = {
    "youtube": pub_youtube,
    "tiktok": pub_tiktok,
    "instagram": pub_instagram,
    "facebook": pub_facebook,
    "pinterest": pub_pinterest,
    "linkedin": pub_linkedin,
    "x": pub_x,
    "reddit": pub_reddit,
    "telegram": pub_telegram,
    "discord": pub_discord,
    "bluesky": pub_bluesky,
    "mastodon": pub_mastodon,
}


def safe_error(exc: Exception) -> str:
    # Transport exceptions often contain Telegram bot tokens or Discord webhook URLs.
    text = str(exc)
    for key in ("TELEGRAM_BOT_TOKEN", "DISCORD_WEBHOOK_URL", "COMPOSIO_API_KEY",
                "BSKY_APP_PASSWORD", "MASTODON_TOKEN", "WORKER_TOKEN"):
        value = os.environ.get(key)
        if value:
            text = text.replace(value, "[redacted]")
    text = re.sub(r"https?://[^\s]+", "[url redacted]", text)
    return text[:280]


def publish_all(accounts: list, script: dict, media: Media, ctx: Ctx,
                before_publish=None, on_result=None) -> list:
    results = []
    for acc in accounts:
        acc = {"meta": {}, **acc}
        meta = acc.get("meta") or {}
        fn = ADAPTERS.get(acc.get("platform", ""))
        if not fn or meta.get("publish_enabled") is not True or meta.get("adapter_verified") is not True:
            print(f"[blocked] {acc.get('platform')}: adapter_not_verified_or_not_authorized", flush=True)
            continue
        # CLI-only execution cannot bypass the durable outbound reservation.
        if not before_publish:
            raise PublishError("DURABLE_DELIVERY_RESERVATION_REQUIRED")
        # Check outgoing captions before consuming a reservation or making a remote write.
        captions(script, acc["platform"], ctx)
        before_publish(acc)
        try:
            r = fn(acc, script, media, ctx)
            # Remote task IDs prove acceptance, not completed public publication.
            state = r.get("state") or ("accepted" if acc.get("kind") == "composio" else "posted")
            if not r.get("remote_id") and not r.get("url"):
                state = "unknown"
            result = {"account": acc["id"], "platform": acc["platform"], "state": state,
                      "url": r.get("url"), "remote_id": r.get("remote_id")}
            print(f"[{state}] {acc['platform']}", flush=True)
        except Exception as e:
            # A timeout may occur AFTER a remote service has accepted the content.
            result = {"account": acc["id"], "platform": acc["platform"],
                      "state": "unknown", "error": safe_error(e)}
            print(f"[unknown] {acc.get('platform')}: {result['error']}", flush=True)
        # Persist each result before attempting another account. Do not swallow a
        # failed receipt write: the existing reservation then requires reconciliation.
        if on_result:
            on_result(result)
        results.append(result)
        time.sleep(2)
    return results


if __name__ == "__main__":
    script = json.loads(Path(sys.argv[1]).read_text())
    accounts = json.loads(Path(sys.argv[2]).read_text())
    media = Media(Path(sys.argv[3]), script["id"],
                  os.environ.get("MEDIA_BASE", "https://connectorlab-core.morfnegoden.workers.dev/m/"))
    ctx = Ctx(os.environ.get("COMPOSIO_API_KEY"), os.environ.get("COMPOSIO_USER_ID", "ubt-factory"),
              os.environ.get("CTA_BASE", "https://connectorlab-core.morfnegoden.workers.dev/go/"),
              script.get("project", "ubt"))
    results = publish_all(accounts, script, media, ctx)
    out = json.dumps(results, ensure_ascii=False, indent=1)
    if len(sys.argv) > 4:
        Path(sys.argv[4]).write_text(out)
    print(out)
