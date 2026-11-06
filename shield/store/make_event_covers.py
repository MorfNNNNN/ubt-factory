"""RuStore event covers 2880x2160 (4:3): gradient, headline in Manrope, the real screen in a phone frame, a 3D object.

Usage (from shield/, after rendering screens with -Pshield.screenshots=true):
    python3 store/make_event_covers.py app/build/store-screenshots app/src/main/res store/events
"""
import os, sys
from PIL import Image, ImageDraw, ImageFilter, ImageFont

SHOTS, RES, OUT = sys.argv[1], sys.argv[2], sys.argv[3]
os.makedirs(OUT, exist_ok=True)
FONT_XB = os.path.join(RES, "font", "manrope_extrabold.ttf")
FONT_SB = os.path.join(RES, "font", "manrope_semibold.ttf")
W, H = 2880, 2160

GRADS = {
    "emerald": ((16, 185, 129), (6, 78, 59)),
    "coral": ((251, 113, 133), (190, 18, 60)),
    "sky": ((96, 165, 250), (29, 78, 216)),
    "violet": ((167, 139, 250), (91, 33, 182)),
    "sunrise": ((251, 191, 36), (234, 88, 12)),
    "night": ((51, 65, 85), (11, 18, 32)),
}

COVERS = [
    ("event-01-premiere", "01-family.png", "Семейный щит уже в RuStore", "Защита от мошенников для всей семьи", "emerald", "ic3d_shield"),
    ("event-02-new-year", "35-alerts-advice.png@290", "Новогодние схемы мошенников", "Предупредим родителей до перевода денег", "coral", "ic3d_bell_festive"),
    ("event-03-school", "17-child-home.png", "Каникулы и школа без споров", "Экранное время, сон и уроки по правилам", "sky", "ic3d_stopwatch"),
    ("event-04-trial", "14-premium.png", "7 дней Премиума бесплатно", "Все функции защиты для всей семьи", "violet", "ic3d_crown"),
    ("event-05-gos", "25-gos.png", "Самозапрет на кредиты по шагам", "И ещё 6 бесплатных официальных мер", "sunrise", "ic3d_locker"),
    ("event-06-summer", "11-location.png", "Лето: где ребёнок и всё ли в порядке", "Геозоны, «Я дома» и SOS", "night", "ic3d_map_pin"),
]

SHORT = {"в", "во", "с", "со", "и", "а", "к", "ко", "о", "об", "у", "на", "по", "от", "за", "до", "из", "не"}


def gradient(top, bottom):
    img = Image.new("RGB", (W, H), top)
    d = ImageDraw.Draw(img)
    for x in range(W):  # left-to-right with a slight diagonal feel
        t = x / (W - 1)
        d.line([(x, 0), (x, H)], fill=tuple(int(top[i] + (bottom[i] - top[i]) * t) for i in range(3)))
    return img


def wrap(draw, text, font, width):
    words = []
    for w in text.split():
        if words and (w == "—" or words[-1].split(" ")[-1].lower() in SHORT):
            words[-1] += " " + w
        else:
            words.append(w)
    lines, cur = [], ""
    for w in words:
        test = (cur + " " + w).strip()
        if draw.textlength(test, font=font) <= width:
            cur = test
        else:
            lines.append(cur)
            cur = w
    if cur:
        lines.append(cur)
    return lines


def rounded(im, radius):
    mask = Image.new("L", im.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, im.width - 1, im.height - 1), radius=radius, fill=255)
    out = Image.new("RGBA", im.size, (0, 0, 0, 0))
    out.paste(im, (0, 0), mask)
    return out


for name, shot, title, sub, grad, art in COVERS:
    bg = gradient(*GRADS[grad]).convert("RGBA")
    glow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    ImageDraw.Draw(glow).ellipse((-600, -700, 1900, 1300), fill=(255, 255, 255, 46))
    bg.alpha_composite(glow.filter(ImageFilter.GaussianBlur(260)))
    d = ImageDraw.Draw(bg)

    # phone on the right
    file, _, crop = shot.partition("@")
    screen = Image.open(os.path.join(SHOTS, file)).convert("RGB")
    if crop:
        screen = screen.crop((0, int(crop), screen.width, screen.height))
    ph = 1880
    pw = int(screen.width * ph / screen.height)
    if pw > 1000:  # cropped tall screens: keep the phone narrow and cut the bottom
        pw = 1000
        screen = screen.resize((pw, int(screen.height * pw / screen.width)), Image.LANCZOS).crop((0, 0, pw, ph))
    else:
        screen = screen.resize((pw, ph), Image.LANCZOS)
    screen = rounded(screen, 90)
    bezel = 28
    phone = Image.new("RGBA", (pw + 2 * bezel, ph + 2 * bezel), (0, 0, 0, 0))
    ImageDraw.Draw(phone).rounded_rectangle((0, 0, phone.width - 1, phone.height - 1), radius=118, fill=(11, 18, 32, 255))
    phone.alpha_composite(screen, (bezel, bezel))
    px, py = W - phone.width - 260, (H - phone.height) // 2 + 60
    shadow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    ImageDraw.Draw(shadow).rounded_rectangle((px + 20, py + 60, px + phone.width - 20, py + phone.height), radius=130, fill=(0, 0, 0, 120))
    bg.alpha_composite(shadow.filter(ImageFilter.GaussianBlur(70)))
    bg.alpha_composite(phone, (px, py))

    # 3D object at the phone's lower-left edge, clear of the screen's header
    obj = Image.open(os.path.join(RES, "drawable-nodpi", art + ".webp")).convert("RGBA").resize((520, 520), Image.LANCZOS)
    ox, oy = px - 400, py + phone.height - 600
    oshadow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    ImageDraw.Draw(oshadow).ellipse((ox + 90, oy + 400, ox + 470, oy + 540), fill=(0, 0, 0, 80))
    bg.alpha_composite(oshadow.filter(ImageFilter.GaussianBlur(30)))
    bg.alpha_composite(obj, (ox, oy))

    # headline on the left
    left, width = 200, px - 300 - 200 - 40
    f_title = ImageFont.truetype(FONT_XB, 168)
    f_sub = ImageFont.truetype(FONT_SB, 84)
    tlines = wrap(d, title, f_title, width)
    slines = wrap(d, sub, f_sub, width)
    block = len(tlines) * 196 + 50 + len(slines) * 112
    y = (H - block) // 2 + 180
    for line in tlines:
        d.text((left, y), line, font=f_title, fill="white")
        y += 196
    y += 50
    for line in slines:
        d.text((left, y), line, font=f_sub, fill=(255, 255, 255, 230))
        y += 112

    out = os.path.join(OUT, name + ".jpg")
    q = 90
    bg.convert("RGB").save(out, "JPEG", quality=q, optimize=True)
    while os.path.getsize(out) > 2_900_000 and q > 60:
        q -= 5
        bg.convert("RGB").save(out, "JPEG", quality=q, optimize=True)
    print(out, os.path.getsize(out) // 1024, "KB")
