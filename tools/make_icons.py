#!/usr/bin/env python3
"""从用户给的新图标生成所有平台需要的图标尺寸。

思路：
1. 把四周的白底挖成透明（只挖和边缘连通的白色，R 内部的白色三角形保留），
   这样放到任何底色上都不会出现一个白方块；
2. Android 旧式图标：白色圆角方块底 + R 字；
3. 圆形图标：白色圆底 + R 字；
4. TV banner：深色渐变 + 图标 + 站名；
5. Windows：多尺寸 ICO（透明圆角）。
"""
from PIL import Image, ImageDraw, ImageFilter
from collections import deque
import os

SRC = "/root/uploads/1794294738557824927-1794294731375-bzqjukrn-炫彩玻璃质感播放标志.png"
ANDROID = "/workspace/rainplayer/app/src/main/res"
WIN = "/workspace/rainplayer/windows/src/RainPlayer.App/Assets"

img = Image.open(SRC).convert("RGBA")
W, H = img.size
px = img.load()

# ---------- 1. 边缘白底 -> 透明 ----------
# 判断「接近白色」，从四条边向内做 BFS，只清掉连通的白色
def near_white(p, tol=26):
    r, g, b, a = p
    return a > 8 and r > 255 - tol and g > 255 - tol and b > 255 - tol

seen = bytearray(W * H)
q = deque()
for x in range(W):
    for y in (0, H - 1):
        q.append((x, y))
for y in range(H):
    for x in (0, W - 1):
        q.append((x, y))

while q:
    x, y = q.popleft()
    if x < 0 or y < 0 or x >= W or y >= H:
        continue
    i = y * W + x
    if seen[i]:
        continue
    if not near_white(px[x, y]):
        continue
    seen[i] = 1
    px[x, y] = (255, 255, 255, 0)
    q.append((x + 1, y)); q.append((x - 1, y))
    q.append((x, y + 1)); q.append((x, y - 1))

glyph = img.crop(img.split()[3].getbbox())   # 只留 R 字本体（按 alpha 取包围盒）
gw, gh = glyph.size
print("glyph", gw, gh)


def fit(canvas, box):
    """把 R 字按比例塞进 box（居中），返回贴好的图层。"""
    g = glyph.copy()
    # 注意：thumbnail 只会缩小，字比目标小时不会放大，必须自己算比例
    scale = box / max(g.width, g.height)
    g = g.resize((max(1, int(g.width * scale)), max(1, int(g.height * scale))), Image.LANCZOS)
    layer = Image.new("RGBA", (canvas, canvas), (0, 0, 0, 0))
    layer.paste(g, ((canvas - g.width) // 2, (canvas - g.height) // 2), g)
    return layer


def rounded(size, radius_ratio=0.22, circle=False):
    """白色底 + R 字，圆角/圆形裁切。"""
    s = size * 4  # 先大后缩，边缘才干净
    bg = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    d = ImageDraw.Draw(bg)
    if circle:
        d.ellipse((0, 0, s - 1, s - 1), fill=(255, 255, 255, 255))
    else:
        d.rounded_rectangle((0, 0, s - 1, s - 1), radius=int(s * radius_ratio),
                            fill=(255, 255, 255, 255))
    # 字占 74%，留一圈白边，视觉上和参考图一致
    bg.alpha_composite(fit(s, int(s * 0.74)))
    return bg.resize((size, size), Image.LANCZOS)


# ---------- 2. Android ----------
for dpi, size in [("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)]:
    p = f"{ANDROID}/mipmap-{dpi}"
    os.makedirs(p, exist_ok=True)
    rounded(size).save(f"{p}/ic_launcher.png")
    rounded(size, circle=True).save(f"{p}/ic_launcher_round.png")
    print("android", dpi, size)

# ---------- 3. TV banner ----------
bw, bh = 320, 180
K = 3  # 超采样倍数
canvas = (bw * K, bh * K)
banner = Image.new("RGBA", canvas, (0, 0, 0, 255))
bd = ImageDraw.Draw(banner)
for y in range(canvas[1]):
    t = y / (canvas[1] - 1)
    bd.line([(0, y), (canvas[0], y)],
            fill=(int(16 + 14 * t), int(22 + 18 * t), int(32 + 30 * t), 255))

icon_size = 104 * K
icon = rounded(icon_size, 0.24)
banner.alpha_composite(icon, (26 * K, (bh * K - icon_size) // 2))

try:
    from PIL import ImageFont

    def font(sz, bold=False):
        path = ("/usr/share/fonts/opentype/noto/NotoSansCJK-Bold.ttc" if bold
                else "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc")
        return ImageFont.truetype(path, sz) if os.path.exists(path) else ImageFont.load_default()

    # 按可用宽度自动收字号，保证「RainPlayer」不会被裁掉
    avail = (bw - 138 - 12) * K
    size = 34 * K
    while size > 10 * K:
        f = font(size, True)
        if bd.textlength("RainPlayer", font=f) <= avail:
            break
        size -= K
    title_font = font(size, True)
    sub_size = 13 * K
    while sub_size > 8 * K:
        fs = font(sub_size)
        if bd.textlength("Emby · Jellyfin · 飞牛影视", font=fs) <= avail:
            break
        sub_size -= K
    bd.text((138 * K, 54 * K), "RainPlayer", font=title_font, fill=(245, 248, 255, 255))
    bd.text((139 * K, 100 * K), "Emby · Jellyfin · 飞牛影视", font=font(sub_size),
            fill=(148, 164, 190, 255))
except Exception as e:
    print("banner text skip", e)

banner = banner.resize((bw, bh), Image.LANCZOS).convert("RGB")
os.makedirs(f"{ANDROID}/drawable-nodpi", exist_ok=True)
banner.save(f"{ANDROID}/drawable-nodpi/tv_banner.png")
print("tv banner")

# ---------- 4. Windows ICO ----------
sizes = [256, 128, 64, 48, 32, 24, 16]
base = rounded(1024, 0.22)
os.makedirs(WIN, exist_ok=True)
base.resize((256, 256), Image.LANCZOS).save(f"{WIN}/app-preview.png")
base.save(f"{WIN}/app.ico", format="ICO",
          sizes=[(s, s) for s in sizes])
print("ico", sizes)
