"""RuStore screenshots 1080x1920: gradient, caption in Manrope, the real screen in a phone frame, a 3D object.

Usage (from shield/, after rendering screens with -Pshield.screenshots=true):
    python3 store/make_screenshots.py app/build/store-screenshots app/src/main/res store/screenshots
"""
import os, sys
from PIL import Image, ImageDraw, ImageFilter, ImageFont

SHOTS, RES, OUT = sys.argv[1], sys.argv[2], sys.argv[3]
os.makedirs(OUT, exist_ok=True)
FONT_XB = os.path.join(RES, "font", "manrope_extrabold.ttf")
FONT_SB = os.path.join(RES, "font", "manrope_semibold.ttf")
W, H = 1080, 1920

GRADS = {
    "emerald": ((16, 185, 129), (6, 78, 59)),
    "coral": ((251, 113, 133), (190, 18, 60)),
    "sky": ((96, 165, 250), (29, 78, 216)),
    "violet": ((167, 139, 250), (91, 33, 182)),
    "sunrise": ((251, 191, 36), (234, 88, 12)),
    "ocean": ((34, 211, 238), (14, 116, 144)),
    "night": ((51, 65, 85), (11, 18, 32)),
}

FRAMES = [
    ("01-family.png", "Вся семья под защитой", "Дети, подростки и родители — в одном приложении", "emerald", "ic3d_shield"),
    ("35-alerts-advice.png@290", "Предупредим, если маму обманывают", "И подскажем, что делать прямо сейчас", "coral", "ic3d_call_ringing"),
    ("15-mom-home.png", "Простой режим для родителей", "Крупные кнопки, SOS и «Я в порядке»", "violet", "ic3d_notify_heart"),
    ("17-child-home.png", "Разумные правила для детей", "Экранное время, сон, уроки и SOS", "sky", "ic3d_stopwatch"),
    ("41-calendar.png", "Семейный календарь", "Кто кого забирает, кружки и врачи", "ocean", "ic3d_calendar"),
    ("39-week.png", "Неделя семьи", "Итоги семьи каждое воскресенье", "emerald", "ic3d_star"),
    ("11-location.png", "Где ребёнок и как прошёл день", "Геозоны и хронология: дом, школа, секция", "night", "ic3d_map_pin"),
    ("25-gos.png", "Самозапрет на кредиты — по шагам", "И ещё 6 бесплатных официальных мер", "sunrise", "ic3d_locker"),
    ("50-trust-child.png", "Правила мягчают вместе с доверием", "Задания, баллы и новый уровень свободы", "sky", "ic3d_medal"),
    ("18-consent.png", "Защита, а не слежка", "Личная переписка остаётся личной", "emerald", "ic3d_lock"),
]


def gradient(top, bottom):
    img = Image.new("RGB", (W, H), top)
    d = ImageDraw.Draw(img)
    for y in range(H):
        t = y / (H - 1)
        d.line([(0, y), (W, y)], fill=tuple(int(top[i] + (bottom[i] - top[i]) * t) for i in range(3)))
    return img


SHORT = {"в", "во", "с", "со", "и", "а", "к", "ко", "о", "об", "у", "на", "по", "от", "за", "до", "из", "не"}


def wrap(draw, text, font, width):
    # A short preposition never ends a line and a dash never starts one.
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


for i, (shot, title, sub, grad, art) in enumerate(FRAMES, 1):
    bg = gradient(*GRADS[grad]).convert("RGBA")
    # soft light blob for depth
    glow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    ImageDraw.Draw(glow).ellipse((-300, -380, 900, 640), fill=(255, 255, 255, 46))
    bg.alpha_composite(glow.filter(ImageFilter.GaussianBlur(120)))
    d = ImageDraw.Draw(bg)
    f_title = ImageFont.truetype(FONT_XB, 78)
    f_sub = ImageFont.truetype(FONT_SB, 40)
    y = 120
    for line in wrap(d, title, f_title, W - 140):
        tw = d.textlength(line, font=f_title)
        d.text(((W - tw) / 2, y), line, font=f_title, fill="white")
        y += 92
    y += 14
    for line in wrap(d, sub, f_sub, W - 160):
        tw = d.textlength(line, font=f_sub)
        d.text(((W - tw) / 2, y), line, font=f_sub, fill=(255, 255, 255, 225))
        y += 54
    # phone
    name, _, crop = shot.partition("@")
    screen = Image.open(os.path.join(SHOTS, name)).convert("RGB")
    if crop:
        screen = screen.crop((0, int(crop), screen.width, screen.height))
    pw = 820
    ph = int(screen.height * pw / screen.width)
    screen = rounded(screen.resize((pw, ph), Image.LANCZOS), 54)
    bezel = 18
    phone = Image.new("RGBA", (pw + 2 * bezel, ph + 2 * bezel), (0, 0, 0, 0))
    ImageDraw.Draw(phone).rounded_rectangle((0, 0, phone.width - 1, phone.height - 1), radius=72, fill=(11, 18, 32, 255))
    phone.alpha_composite(screen, (bezel, bezel))
    top = max(y + 80, 480)
    px = (W - phone.width) // 2
    shadow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    ImageDraw.Draw(shadow).rounded_rectangle((px + 10, top + 30, px + phone.width - 10, top + phone.height), radius=80, fill=(0, 0, 0, 110))
    bg.alpha_composite(shadow.filter(ImageFilter.GaussianBlur(40)))
    bg.alpha_composite(phone, (px, top))
    # 3D object overlapping the phone's top corner
    obj = Image.open(os.path.join(RES, "drawable-nodpi", art + ".webp")).convert("RGBA").resize((230, 230), Image.LANCZOS)
    oshadow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    ox, oy = W - 250, top - 70
    ImageDraw.Draw(oshadow).ellipse((ox + 40, oy + 170, ox + 210, oy + 240), fill=(0, 0, 0, 80))
    bg.alpha_composite(oshadow.filter(ImageFilter.GaussianBlur(18)))
    bg.alpha_composite(obj, (ox, oy))
    out = os.path.join(OUT, f"rustore-{i:02d}.jpg")
    bg.convert("RGB").save(out, "JPEG", quality=92, optimize=True)
    print(out, os.path.getsize(out) // 1024, "KB")
