#!/usr/bin/env python3
"""
Generates every Taskfern brand asset from one geometric definition, so the logo, launcher icon, monochrome icon,
notification icon and splash mark always match.

The mark: a check mark whose long stroke is a fern frond. Leaflets (pinnae) grow along the long stroke and shrink
toward the tip, reading as both "done" and "growing".

Usage: python3 -I tools/brand/generate_brand.py  (from the repository root)
Requires Pillow for the PNG renders.
"""
import math
import os
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
RES = os.path.join(ROOT, "app", "src", "main", "res")
ASSETS = os.path.join(ROOT, "docs", "assets")

# Palette
FOREST = "#1E4D3B"      # icon background
FOREST_DEEP = "#163A2C"
CREAM = "#F4F1E4"       # mark on dark
FERN = "#2F6B4F"        # mark on light
SPROUT = "#9BD3A8"      # small accent leaf tip

# ---- Geometry (108 x 108 adaptive-icon canvas; the safe zone is the central 66 units) -----------------------------
STROKE = 7.6
SHORT_START = (31.0, 56.0)
JOINT = (46.5, 68.0)
# Long stroke as a quadratic bezier from the joint to the tip, bowing slightly like a frond.
CTRL = (55.0, 47.0)
TIP = (79.0, 27.0)


def bez(t, p0=JOINT, p1=CTRL, p2=TIP):
    x = (1 - t) ** 2 * p0[0] + 2 * (1 - t) * t * p1[0] + t ** 2 * p2[0]
    y = (1 - t) ** 2 * p0[1] + 2 * (1 - t) * t * p1[1] + t ** 2 * p2[1]
    return x, y


def bez_tangent(t, p0=JOINT, p1=CTRL, p2=TIP):
    dx = 2 * (1 - t) * (p1[0] - p0[0]) + 2 * t * (p2[0] - p1[0])
    dy = 2 * (1 - t) * (p1[1] - p0[1]) + 2 * t * (p2[1] - p1[1])
    n = math.hypot(dx, dy)
    return dx / n, dy / n


def leaf(base, angle, length, width):
    """A pointed leaf from `base`, pointing at `angle` (radians), as polygon points (smooth enough at icon sizes)."""
    ux, uy = math.cos(angle), math.sin(angle)
    nx, ny = -uy, ux
    pts = []
    steps = 14
    for side in (1, -1):
        rng = range(steps + 1) if side == 1 else range(steps, -1, -1)
        for i in rng:
            s = i / steps
            # Leaf half-width profile: widest a little before the middle, pointed at both ends.
            w = width * math.sin(math.pi * s) ** 0.9 * (1.0 - 0.25 * s)
            x = base[0] + ux * length * s + nx * w * side
            y = base[1] + uy * length * s + ny * w * side
            pts.append((x, y))
    return pts


def leaflets_raw():
    out = []
    # (position along the frond, length, half-width). Pinnae shrink toward the tip like a real fern frond.
    pairs = [(0.26, 15.0, 3.3), (0.39, 13.6, 3.1), (0.52, 11.8, 2.8), (0.64, 9.8, 2.4), (0.75, 7.6, 2.0), (0.85, 5.4, 1.6)]
    for t, length, width in pairs:
        for side in (1, -1):
            tt = t + (0.035 if side == -1 else 0.0)  # pinnae alternate slightly, as on a fern
            bx, by = bez(tt)
            tx, ty = bez_tangent(tt)
            base_angle = math.atan2(ty, tx)
            # Pinnae stand out at ~62 degrees, leaning toward the tip.
            a = base_angle - side * math.radians(62)
            out.append(leaf((bx, by), a, length, width))
    return out


def _center_offset():
    pts = [SHORT_START, JOINT] + [bez(i / 24) for i in range(25)]
    for l in leaflets_raw():
        pts += l
    xs = [p[0] for p in pts]
    ys = [p[1] for p in pts]
    pad = STROKE / 2
    cx = (min(xs) - pad + max(xs) + pad) / 2
    cy = (min(ys) - pad + max(ys) + pad) / 2
    return 54 - cx, 54 - cy


def leaflets():
    dx, dy = OFFSET
    return [[(x + dx, y + dy) for x, y in l] for l in leaflets_raw()]


def path_from_points(pts):
    d = "M%.2f,%.2f" % pts[0]
    for p in pts[1:]:
        d += " L%.2f,%.2f" % p
    return d + " Z"


def check_points(n=24):
    dx, dy = OFFSET
    pts = [SHORT_START, JOINT] + [bez(i / n) for i in range(1, n + 1)]
    return [(x + dx, y + dy) for x, y in pts]


def check_path():
    p = check_points()
    d = "M%.2f,%.2f" % p[0]
    for x, y in p[1:]:
        d += " L%.2f,%.2f" % (x, y)
    return d


OFFSET = _center_offset()


LEAF_PATHS = [path_from_points(l) for l in leaflets()]
CHECK_D = check_path()


# ---- Writers -----------------------------------------------------------------------------------------------------
def svg_mark(color, size=108, background=None, rounded=False, transform=""):
    bg = ""
    if background:
        if rounded:
            bg = f'<rect width="108" height="108" rx="26" fill="{background}"/>'
        else:
            bg = f'<rect width="108" height="108" fill="{background}"/>'
    leaves = "".join(f'<path d="{d}" fill="{color}"/>' for d in LEAF_PATHS)
    return (
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{size}" height="{size}" viewBox="0 0 108 108">'
        f"{bg}<g{transform}>"
        f'<path d="{CHECK_D}" fill="none" stroke="{color}" stroke-width="{STROKE}" stroke-linecap="round" stroke-linejoin="round"/>'
        f"{leaves}</g></svg>\n"
    )


def vector_drawable(color, size_dp=108, viewport=108, group_transform=None, background=None):
    gt = ""
    if group_transform:
        sx, sy, px, py = group_transform
        gt = f' android:scaleX="{sx}" android:scaleY="{sy}" android:pivotX="{px}" android:pivotY="{py}"'
    bg = ""
    if background:
        bg = f'    <path android:fillColor="{background}" android:pathData="M0,0h108v108h-108z"/>\n'
    leaves = "".join(
        f'        <path android:fillColor="{color}" android:pathData="{d}"/>\n' for d in LEAF_PATHS
    )
    return (
        '<?xml version="1.0" encoding="utf-8"?>\n'
        "<!-- Generated by tools/brand/generate_brand.py. Do not edit by hand. -->\n"
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
        f'    android:width="{size_dp}dp" android:height="{size_dp}dp"\n'
        f'    android:viewportWidth="{viewport}" android:viewportHeight="{viewport}">\n'
        f"{bg}"
        f"    <group{gt}>\n"
        f'        <path android:pathData="{CHECK_D}" android:strokeColor="{color}" android:strokeWidth="{STROKE}"\n'
        '            android:strokeLineCap="round" android:strokeLineJoin="round" android:fillColor="#00000000"/>\n'
        f"{leaves}"
        "    </group>\n"
        "</vector>\n"
    )


def write(path, text):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        f.write(text)
    print("wrote", os.path.relpath(path, ROOT))


def render_png(path, px, background, color, rounded_radius=None, circle=False, mark_scale=1.0):
    from PIL import Image, ImageDraw

    ss = 4  # supersample for smooth edges
    s = px * ss / 108.0
    img = Image.new("RGBA", (px * ss, px * ss), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    if background:
        if circle:
            d.ellipse([0, 0, px * ss - 1, px * ss - 1], fill=background)
        elif rounded_radius is not None:
            d.rounded_rectangle([0, 0, px * ss - 1, px * ss - 1], radius=rounded_radius * s, fill=background)
        else:
            d.rectangle([0, 0, px * ss, px * ss], fill=background)

    def tr(p):
        cx = cy = 54.0
        return ((cx + (p[0] - cx) * mark_scale) * s, (cy + (p[1] - cy) * mark_scale) * s)

    w = STROKE * s * mark_scale
    line = [tr(p) for p in check_points(48)]
    d.line(line, fill=color, width=int(round(w)), joint="curve")
    for end in (line[0], line[-1]):
        r = w / 2
        d.ellipse([end[0] - r, end[1] - r, end[0] + r, end[1] + r], fill=color)
    for l in leaflets():
        d.polygon([tr(p) for p in l], fill=color)
    img = img.resize((px, px), Image.LANCZOS)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path, optimize=True)
    print("wrote", os.path.relpath(path, ROOT))


def main():
    # Docs / README assets
    write(os.path.join(ASSETS, "taskfern-logo.svg"), svg_mark(CREAM, 512, FOREST, rounded=True))
    write(os.path.join(ASSETS, "taskfern-mark.svg"), svg_mark(FERN, 512))
    write(os.path.join(ASSETS, "taskfern-mark-light.svg"), svg_mark(CREAM, 512))

    # Android adaptive icon layers. The foreground mark is drawn at 0.92 scale to sit comfortably in the safe zone.
    write(os.path.join(RES, "drawable", "ic_launcher_foreground.xml"),
          vector_drawable(CREAM, group_transform=(0.92, 0.92, 54, 54)))
    write(os.path.join(RES, "drawable", "ic_launcher_monochrome.xml"),
          vector_drawable("#FFFFFFFF", group_transform=(0.92, 0.92, 54, 54)))
    # Splash screen icon (Android 12+ masks it to a circle of 2/3 the size; keep it inside).
    write(os.path.join(RES, "drawable", "ic_splash_mark.xml"),
          vector_drawable(CREAM, group_transform=(0.80, 0.80, 54, 54)))
    # Mark for in-app use (About screen, empty states), tinted from code.
    write(os.path.join(RES, "drawable", "ic_taskfern_mark.xml"), vector_drawable("#FF2F6B4F", size_dp=48))
    # Notification small icon: must be white on transparent.
    write(os.path.join(RES, "drawable", "ic_stat_taskfern.xml"),
          vector_drawable("#FFFFFFFF", size_dp=24, group_transform=(1.25, 1.25, 54, 52)))

    if "--no-png" in sys.argv:
        return
    render_png(os.path.join(ASSETS, "taskfern-logo-1024.png"), 1024, FOREST, CREAM, rounded_radius=26)
    render_png(os.path.join(ASSETS, "taskfern-icon-512.png"), 512, FOREST, CREAM, rounded_radius=26)
    # Legacy launcher icons (pre-adaptive launchers and some third-party launchers still read these).
    for density, px in {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}.items():
        render_png(os.path.join(RES, f"mipmap-{density}", "ic_launcher.png"), px, FOREST, CREAM,
                   rounded_radius=22, mark_scale=1.0)
        render_png(os.path.join(RES, f"mipmap-{density}", "ic_launcher_round.png"), px, FOREST, CREAM,
                   circle=True, mark_scale=0.98)


if __name__ == "__main__":
    main()
