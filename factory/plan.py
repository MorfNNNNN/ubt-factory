"""Deterministic daily format plan. Same date -> same plan: auditable, no AI.

Formats per platform rotate day to day; ~1-2 days a week Instagram gets a
combo (reel+story or photo+story) inside one account reservation. Instagram
never repeats the same format on consecutive days.
"""
from __future__ import annotations

import random
from datetime import date, timedelta
from functools import lru_cache

IG_POOL = ["reel", "reel", "photo", "carousel", "story", "photo", "reel+story", "photo+story"]
FB_POOL = ["video", "photo"]
PIN_POOL = ["cover", "slide", "carousel"]
IG_FORMATS = {"reel", "photo", "carousel", "story", "reel+story", "photo+story"}
_EPOCH = date(2026, 1, 1)


@lru_cache(maxsize=2048)
def _ig_eff(d: date) -> str:
    """Effective Instagram format for the day, never equal to the previous day's."""
    base = random.Random("ig:" + d.isoformat()).choice(IG_POOL)
    if d <= _EPOCH:
        return base
    prev = _ig_eff(d - timedelta(days=1))
    if base != prev:
        return base
    return random.Random("ig2:" + d.isoformat()).choice([p for p in IG_POOL if p != prev])


def daily_plan(day: date) -> dict:
    rng = random.Random("rest:" + day.isoformat())
    return {"instagram": _ig_eff(day), "facebook": rng.choice(FB_POOL),
            "pinterest": rng.choice(PIN_POOL), "youtube": "short", "linkedin": "video"}
