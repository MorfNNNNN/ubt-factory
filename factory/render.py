"""Short-video renderer: script JSON -> 1080x1920 MP4 + cover + carousel slides.

Pipeline: Piper TTS (CC0 / public-domain voices) -> Pillow scene cards ->
FFmpeg (slow push-in, burned captions, progress bar, loudness norm).
Runs on a stock Ubuntu GitHub runner; no paid APIs.
"""
from __future__ import annotations

import json
import math
import os
import random
import re
import subprocess
import wave
from dataclasses import dataclass
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

W, H, FPS = 1080, 1920, 30
HERE = Path(__file__).resolve().parent
FONTS = HERE / "fonts"
BOLD = str(FONTS / "Montserrat-ExtraBold.ttf")
SEMI = str(FONTS / "Montserrat-SemiBold.ttf")

# language -> (voice stem, licence). Only commercially usable voices.
VOICES = {"ru": ("ru_RU-dmitri-medium", "CC0"), "en": ("en_US-john-medium", "public domain")}
VOICE_URL = "https://huggingface.co/rhasspy/piper-voices/resolve/main/{lang}/{loc}/{name}/medium/{stem}.onnx"

THEMES = [
    {"bg": ("#0f172a", "#1e3a8a"), "accent": "#facc15", "card": "#ffffff", "ink": "#0f172a"},
    {"bg": ("#111827", "#7c2d12"), "accent": "#fb923c", "card": "#fff7ed", "ink": "#1c1917"},
    {"bg": ("#052e16", "#065f46"), "accent": "#a3e635", "card": "#f0fdf4", "ink": "#052e16"},
    {"bg": ("#1e1b4b", "#701a75"), "accent": "#f0abfc", "card": "#fdf4ff", "ink": "#1e1b4b"},
    {"bg": ("#0c4a6e", "#155e75"), "accent": "#5eead4", "card": "#f0fdfa", "ink": "#083344"},
]


def run(cmd: list[str]) -> None:
    p = subprocess.run(cmd, capture_output=True, text=True)
    if p.returncode != 0:
        raise RuntimeError(f"command failed: {' '.join(cmd[:4])}...\n{p.stderr[-1500:]}")


def hex2rgb(h: str) -> tuple:
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


# ---------------------------------------------------------------- voice
_voices: dict = {}


FONT_URLS = {
    "Montserrat-ExtraBold.ttf": "https://raw.githubusercontent.com/JulietaUla/Montserrat/master/fonts/ttf/Montserrat-ExtraBold.ttf",
    "Montserrat-SemiBold.ttf": "https://raw.githubusercontent.com/JulietaUla/Montserrat/master/fonts/ttf/Montserrat-SemiBold.ttf",
}


def ensure_fonts() -> None:
    for name, url in FONT_URLS.items():
        p = FONTS / name
        if not p.exists():
            FONTS.mkdir(parents=True, exist_ok=True)
            run(["curl", "-sSfL", "-o", str(p), url])


def voice_path(lang: str, cache: Path) -> Path:
    stem = VOICES[lang][0]
    loc, name = stem.split("-")[0], stem.split("-")[1]
    p = cache / f"{stem}.onnx"
    if not p.exists():
        cache.mkdir(parents=True, exist_ok=True)
        url = VOICE_URL.format(lang=lang, loc=loc, name=name, stem=stem)
        run(["curl", "-sSfL", "-o", str(p), url])
        run(["curl", "-sSfL", "-o", str(p) + ".json", url + ".json"])
    return p


def tts(text: str, out: Path, lang: str, cache: Path, speed: float = 1.0) -> float:
    from piper import PiperVoice, SynthesisConfig

    model = str(voice_path(lang, cache))
    if model not in _voices:
        _voices[model] = PiperVoice.load(model)
    with wave.open(str(out), "wb") as wf:
        _voices[model].synthesize_wav(text, wf, syn_config=SynthesisConfig(length_scale=1.0 / speed))
    with wave.open(str(out)) as wf:
        return wf.getnframes() / float(wf.getframerate())


# ---------------------------------------------------------------- drawing
def font(path: str, size: int):
    return ImageFont.truetype(path, size)


def wrap(draw, text, f, width):
    lines, cur = [], ""
    for w in text.split():
        t = (cur + " " + w).strip()
        if draw.textlength(t.replace("*", ""), font=f) <= width or not cur:
            cur = t
        else:
            lines.append(cur)
            cur = w
    if cur:
        lines.append(cur)
    # keep *accent* spans balanced per line
    fixed, carry = [], False
    for ln in lines:
        if carry:
            ln = "*" + ln
        carry = ln.count("*") % 2 == 1
        fixed.append(ln + ("*" if carry else ""))
    return fixed


def fit(draw, text, path, width, max_size, min_size, max_lines):
    size = max_size
    while size > min_size:
        f = font(path, size)
        lines = wrap(draw, text, f, width)
        if len(lines) <= max_lines and all(draw.textlength(ln.replace("*", ""), font=f) <= width for ln in lines):
            return f, lines
        size -= 6
    f = font(path, min_size)
    lines = wrap(draw, text, f, width)
    if len(lines) > max_lines or any(draw.textlength(ln.replace("*", ""), font=f) > width for ln in lines):
        raise ValueError("text does not fit safe frame; shorten the on-screen copy")
    return f, lines


def draw_rich(draw, xy, line, f, base, accent):
    x, y = xy
    hot = False
    for tok in re.split(r"(\*)", line):
        if tok == "*":
            hot = not hot
            continue
        if tok:
            draw.text((x, y), tok, font=f, fill=accent if hot else base)
            x += draw.textlength(tok, font=f)


def background(theme: dict, seed: int) -> Image.Image:
    a, b = hex2rgb(theme["bg"][0]), hex2rgb(theme["bg"][1])
    img = Image.new("RGB", (W, H))
    px = ImageDraw.Draw(img)
    for y in range(H):
        t = y / (H - 1)
        px.line([(0, y), (W, y)], fill=tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3)))
    rnd = random.Random(seed)
    glow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    g = ImageDraw.Draw(glow)
    acc = hex2rgb(theme["accent"])
    for _ in range(5):
        r = rnd.randint(180, 420)
        cx, cy = rnd.randint(-100, W + 100), rnd.randint(-100, H + 100)
        g.ellipse([cx - r, cy - r, cx + r, cy + r], fill=acc + (rnd.randint(18, 40),))
    glow = glow.filter(ImageFilter.GaussianBlur(90))
    img.paste(glow, (0, 0), glow)
    return img


def scene_card(beat: dict, idx: int, total: int, theme: dict, brand: str, seed: int) -> Image.Image:
    img = background(theme, seed + idx)
    d = ImageDraw.Draw(img)
    accent, white = theme["accent"], "#ffffff"
    brand = brand.split(" — ")[0]
    bf, _ = fit(d, brand, SEMI, W - 400, 40, 24, 1)
    bw = d.textlength(brand, font=bf)
    d.rounded_rectangle([60, 110, 60 + bw + 60, 186], radius=38, outline=accent, width=3)
    d.text((90, 126), brand, font=bf, fill=white)
    if beat.get("kind") == "point":
        sf = font(BOLD, 44)
        label = f"{beat.get('n', idx)}/{beat.get('of', total)}"
        lw = d.textlength(label, font=sf)
        d.rounded_rectangle([W - 60 - lw - 50, 110, W - 60, 186], radius=38, fill=accent)
        d.text((W - 60 - lw - 25, 124), label, font=sf, fill=theme["ink"])
    head = beat.get("screen") or beat.get("text", "")
    f, lines = fit(d, head, BOLD, W - 160, 118 if beat.get("kind") == "hook" else 100, 60, 5)
    lh = int(f.size * 1.18)
    y = 300
    for ln in lines:
        x = (W - d.textlength(ln.replace("*", ""), font=f)) / 2
        draw_rich(d, (x + 4, y + 5), ln.replace("*", ""), f, (0, 0, 0), (0, 0, 0))
        draw_rich(d, (x, y), ln, f, white, accent)
        y += lh
    bullets = [b for b in (beat.get("bullets") or []) if b][:4]
    if bullets:
        y += 50
        cf = font(SEMI, 50)
        rows = [(not b.startswith("-"), wrap(d, b.lstrip("+- "), cf, W - 330)) for b in bullets]
        hgt = sum(len(r[1]) * 64 + 34 for r in rows) + 50
        if hgt > 1330 - y:
            raise ValueError("bullet text exceeds safe frame; shorten the copy")
        if hgt > 120:
            d.rounded_rectangle([80, y, W - 80, y + hgt], radius=36, fill=theme["card"])
            yy = y + 36
            mf = font(BOLD, 40)
            for ok, ls in rows:
                if yy > y + hgt - 60:
                    break
                d.ellipse([120, yy + 6, 172, yy + 58], fill="#16a34a" if ok else "#dc2626")
                mark = "+" if ok else "–"
                d.text((146 - d.textlength(mark, font=mf) / 2, yy + 6), mark, font=mf, fill="#ffffff")
                for ln in ls:
                    d.text((200, yy), ln.replace("*", ""), font=cf, fill=theme["ink"])
                    yy += 64
                yy += 34
    return img


# ---------------------------------------------------------------- captions
def ass_time(t: float) -> str:
    h, rem = divmod(max(t, 0), 3600)
    m, s = divmod(rem, 60)
    return f"{int(h)}:{int(m):02d}:{s:05.2f}"


def chunks(text: str, n: int = 3) -> list[str]:
    out, cur = [], []
    for w in re.sub(r"\s+", " ", text.replace("*", "")).strip().split(" "):
        cur.append(w)
        if len(cur) >= n or re.search(r"[.!?,:;—]$", w):
            out.append(" ".join(cur))
            cur = []
    if cur:
        out.append(" ".join(cur))
    return out


def build_ass(timeline, theme: dict, path: Path) -> None:
    acc = hex2rgb(theme["accent"])
    acc_ass = f"&H00{acc[2]:02X}{acc[1]:02X}{acc[0]:02X}"
    head = (
        "[Script Info]\nScriptType: v4.00+\n"
        f"PlayResX: {W}\nPlayResY: {H}\nWrapStyle: 0\n\n"
        "[V4+ Styles]\n"
        "Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, "
        "Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, "
        "MarginR, MarginV, Encoding\n"
        f"Style: Cap,Montserrat ExtraBold,84,&H00FFFFFF,{acc_ass},&H00000000,&H64000000,-1,0,0,0,100,100,0,0,1,7,3,2,"
        "70,70,330,1\n\n[Events]\n"
        "Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text\n"
    )
    rows = []
    for start, end, text in timeline:
        parts = chunks(text)
        total = sum(len(p) for p in parts) or 1
        t = start
        for i, p in enumerate(parts):
            dur = (end - start) * len(p) / total
            col = "" if i % 2 == 0 else f"\\c{acc_ass}"
            rows.append(f"Dialogue: 0,{ass_time(t)},{ass_time(t + dur)},Cap,,0,0,0,,"
                        f"{{\\fscx78\\fscy78\\t(0,110,\\fscx100\\fscy100){col}}}{p.upper()}")
            t += dur
    path.write_text(head + "\n".join(rows) + "\n", encoding="utf-8")


# ---------------------------------------------------------------- main API
@dataclass
class RenderResult:
    video: Path
    cover: Path
    slides: list
    duration: float
    voice: str


def render(script: dict, out_dir: Path, cache: Path, variant: int = 0) -> RenderResult:
    out_dir.mkdir(parents=True, exist_ok=True)
    ensure_fonts()
    lang = script.get("language") if script.get("language") in VOICES else "ru"
    theme = THEMES[int(script.get("theme", variant)) % len(THEMES)]
    brand = script.get("brand") or "@brand"
    seed = int(script.get("seed", variant * 7919 + 17))
    beats = [dict(b) for b in script["beats"] if (b.get("text") or "").strip()]
    if not 2 <= len(beats) <= 9:
        raise ValueError("script must have 2..9 beats")
    points = [b for b in beats if b.get("kind") == "point"]
    for i, b in enumerate(points, 1):
        b["n"], b["of"] = i, len(points)
    speed = 1.08 + 0.03 * (variant % 3)
    timeline, scenes, slides, t, gap = [], [], [], 0.0, 0.18
    for i, b in enumerate(beats):
        wav = out_dir / f"v{i}.wav"
        dur = tts(b["text"].replace("*", ""), wav, lang, cache, speed=speed) + gap
        png = out_dir / f"slide_{i:02d}.png"
        scene_card(b, i, len(beats), theme, brand, seed).save(png)
        slides.append(png)
        mkv = out_dir / f"s{i}.mkv"
        frames = max(int(math.ceil(dur * FPS)), 1)
        zoom = f"scale=w='trunc({W}*(1+0.05*t/{dur:.3f})/2)*2':h=-2:eval=frame,crop={W}:{H},setsar=1,fps={FPS}"
        run(["ffmpeg", "-y", "-loglevel", "error", "-loop", "1", "-framerate", str(FPS), "-i", str(png), "-i", str(wav),
             "-filter_complex", f"[0:v]{zoom}[v];[1:a]aresample=48000,apad=whole_dur={dur:.3f}[a]",
             "-map", "[v]", "-map", "[a]", "-frames:v", str(frames), "-t", f"{frames / FPS:.3f}",
             "-c:v", "libx264", "-preset", "ultrafast", "-crf", "16", "-pix_fmt", "yuv420p",
             "-c:a", "pcm_s16le", "-ar", "48000", "-ac", "2", str(mkv)])
        scenes.append(mkv)
        timeline.append((t, t + dur - gap, b["text"]))
        t += frames / FPS
    if t > 59.5:
        raise ValueError(f"video too long for Shorts: {t:.1f}s")
    lst = out_dir / "list.txt"
    lst.write_text("".join(f"file '{p.name}'\n" for p in scenes))
    joined = out_dir / "joined.mkv"
    run(["ffmpeg", "-y", "-loglevel", "error", "-f", "concat", "-safe", "0", "-i", str(lst), "-c", "copy", str(joined)])
    ass = out_dir / "captions.ass"
    build_ass(timeline, theme, ass)
    acc = theme["accent"].lstrip("#")
    vf = (f"ass={ass}:fontsdir={FONTS},"
          f"drawbox=x=0:y={H - 14}:w='iw*t/{t:.3f}':h=14:color=0x{acc}@0.95:t=fill")
    video = out_dir / "video.mp4"
    run(["ffmpeg", "-y", "-loglevel", "error", "-i", str(joined), "-vf", vf,
         "-af", "loudnorm=I=-14:TP=-1.5:LRA=11", "-c:v", "libx264", "-preset", "medium", "-crf", "22",
         "-profile:v", "high", "-level", "4.1", "-pix_fmt", "yuv420p", "-r", str(FPS), "-g", str(FPS * 2),
         "-c:a", "aac", "-b:a", "160k", "-ar", "48000", "-movflags", "+faststart", str(video)])
    cover = out_dir / "cover.jpg"
    Image.open(slides[0]).convert("RGB").save(cover, quality=90)
    for p in scenes + [joined, lst] + list(out_dir.glob("v*.wav")):
        p.unlink(missing_ok=True)
    return RenderResult(video, cover, slides, t, VOICES[lang][0])


if __name__ == "__main__":
    import sys

    s = json.loads(Path(sys.argv[1]).read_text())
    r = render(s, Path(sys.argv[2]), Path(os.environ.get("VOICE_CACHE", HERE.parent / "voices")))
    print(json.dumps({"video": str(r.video), "duration": round(r.duration, 2), "slides": len(r.slides)}))
