#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Widget drawables of the favourite shapes added in build 74 (Material-3-Expressive-inspired).

The geometry mirrors app/src/main/java/dev/ambivalenton/openv/rendering/ExpressiveShapeOutline.kt
point for point; Build74Test compares the generated path data with the Kotlin outline. For every
shape this writes widget_favorite_<shape>.xml (plain mask) and the three resource-only badge layers
widget_symbol_<shape>_{fill,contour,shadow}.xml in the same form as the older shapes.

  python3 tools/generate_favorite_shapes.py          # write the drawables
  python3 tools/generate_favorite_shapes.py --check  # verify they are current
"""
from math import pi, cos, sin, tan, acos, atan2, hypot, sqrt, exp, log
from pathlib import Path
import sys

RES = Path(__file__).resolve().parents[1] / 'app/src/main/res/drawable'


def regular(corners, offset):
    return [(cos(-pi / 2 + offset + 2 * pi * i / corners), sin(-pi / 2 + offset + 2 * pi * i / corners)) for i in range(corners)]


def star(points, outer, inner):
    out = []
    for i in range(points * 2):
        a = -pi / 2 + pi * i / points
        r = outer if i % 2 == 0 else inner
        out.append((r * cos(a), r * sin(a)))
    return out


def rounded(vertices, radii, steps=10):
    n = len(vertices)
    out = []
    for i in range(n):
        vx, vy = vertices[i]
        px, py = vertices[(i - 1 + n) % n]
        nx, ny = vertices[(i + 1) % n]
        r = radii[i]
        if r <= 0.0:
            out.append((vx, vy))
            continue
        ax, ay = px - vx, py - vy
        la = hypot(ax, ay)
        bx, by = nx - vx, ny - vy
        lb = hypot(bx, by)
        ux, uy, wx, wy = ax / la, ay / la, bx / lb, by / lb
        theta = acos(max(-1.0, min(1.0, ux * wx + uy * wy)))
        if theta < 1e-6 or theta > pi - 1e-6:
            out.append((vx, vy))
            continue
        t = r / tan(theta / 2)
        limit = min(la, lb) / 2
        if t > limit:
            t = limit
            radius = limit * tan(theta / 2)
        else:
            radius = r
        sx, sy = ux + wx, uy + wy
        sl = hypot(sx, sy)
        d = radius / sin(theta / 2)
        cx, cy = vx + sx / sl * d, vy + sy / sl * d
        a1 = atan2(vy + uy * t - cy, vx + ux * t - cx)
        a2 = atan2(vy + wy * t - cy, vx + wx * t - cx)
        delta = a2 - a1
        while delta > pi:
            delta -= 2 * pi
        while delta < -pi:
            delta += 2 * pi
        for k in range(steps + 1):
            a = a1 + delta * k / steps
            out.append((cx + radius * cos(a), cy + radius * sin(a)))
    return out


def petals(count, distance, radius):
    out = []
    for j in range(180):
        a = -pi / 2 + 2 * pi * j / 180
        total = 0.0
        for i in range(count):
            along = distance * cos(a - (-pi / 2 + 2 * pi * i / count))
            disc = radius * radius - (distance * distance - along * along)
            if disc >= 0:
                total += exp(40 * (along + sqrt(disc)))
        r = log(total) / 40
        out.append((r * cos(a), r * sin(a)))
    return out


def heart():
    cx, cy, r, tip_y = .5, -.3, .52, .95
    notch_y = cy - sqrt(r * r - cx * cx)
    start = atan2(notch_y - cy, -cx)
    d = hypot(cx, tip_y - cy)
    end = atan2(tip_y - cy, -cx) - acos(r / d)
    right = [(cx + r * cos(start + (end - start) * k / 40), cy + r * sin(start + (end - start) * k / 40)) for k in range(41)]
    left = [(-x, y) for x, y in list(reversed(right))[:-1]]
    outline = right + [(0.0, tip_y)] + left
    return rounded(outline, [.2 if (x == 0.0 and y == tip_y) else 0.0 for x, y in outline])


def ghost():
    dome = [(cos(pi + pi * k / 24), sin(pi + pi * k / 24)) for k in range(25)]
    hem, lobe = .62, 1.0 / 3
    lobes = []
    for i in range(3):
        center = 1 - (2 * i + 1) * lobe
        for k in range(0 if i == 0 else 1, 13):
            a = pi * k / 12
            lobes.append((center + lobe * cos(a), hem + lobe * sin(a)))
    return dome + lobes


def pixel_circle():
    widths = [.5, .75, 1.0, 1.0, 1.0, 1.0, .75, .5]
    raw = []
    for j, w in enumerate(widths):
        raw += [(w, -1 + .25 * j), (w, -1 + .25 * (j + 1))]
    for j in reversed(range(len(widths))):
        w = widths[j]
        raw += [(-w, -1 + .25 * (j + 1)), (-w, -1 + .25 * j)]
    return [p for i, p in enumerate(raw) if raw[i - 1] != p]


def fit(points):
    xs = [p[0] for p in points]
    ys = [p[1] for p in points]
    scale = 1.0 / max(max(xs) - min(xs), max(ys) - min(ys))
    ox = (1 - (max(xs) - min(xs)) * scale) / 2 - min(xs) * scale
    oy = (1 - (max(ys) - min(ys)) * scale) / 2 - min(ys) * scale
    return [(x * scale + ox, y * scale + oy) for x, y in points]


def dome():
    return [(cos(pi + pi * k / 24), sin(pi + pi * k / 24)) for k in range(25)]


SHAPES = {
    'pentagon': lambda: fit(rounded(regular(5, 0.0), [.26] * 5)),
    'gem': lambda: fit(rounded([(-.6, -.8), (.6, -.8), (1.0, -.25), (0.0, 1.0), (-1.0, -.25)], [.18, .18, .2, .26, .2])),
    'arch': lambda: fit(rounded(dome() + [(1.0, 1.0), (-1.0, 1.0)], [0.0] * 25 + [.3, .3])),
    'slanted': lambda: fit(rounded([(-.85, -1.0), (1.15, -1.0), (.85, 1.0), (-1.15, 1.0)], [.5] * 4)),
    'sunny': lambda: fit(rounded(star(8, 1.0, .8), [.14 if i % 2 == 0 else .1 for i in range(16)], steps=8)),
    'flower': lambda: fit(petals(8, .62, .38)),
    'burst': lambda: fit(rounded(star(12, 1.0, .78), [.05 if i % 2 == 0 else .04 for i in range(24)], steps=6)),
    'heart': lambda: fit(heart()),
    'ghost': lambda: fit(ghost()),
    'pixel': lambda: fit(pixel_circle()),
}

HEADER = '<?xml version="1.0" encoding="utf-8"?>\n'
VECTOR = '<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="144dp" android:height="144dp" android:viewportWidth="144" android:viewportHeight="144">'


def path_data(points):
    coords = [f'{x * 144:.3f},{y * 144:.3f}' for x, y in points]
    return 'M' + coords[0] + ' ' + ' '.join('L' + c for c in coords[1:]) + ' Z'


def documents(name, path):
    note = 'generated by tools/generate_favorite_shapes.py'
    yield f'widget_favorite_{name}.xml', (HEADER + f'<!-- SPDX-License-Identifier: GPL-3.0-or-later; {note} -->\n' +
                                          VECTOR + f'<path android:fillColor="#FFFFFFFF" android:pathData="{path}"/></vector>\n')
    for layer in ['fill', 'contour', 'shadow']:
        if layer == 'fill':
            content = f'<path android:fillColor="#FFFFFFFF" android:pathData="{path}"/>'
        elif layer == 'contour':
            content = f'<path android:fillColor="#00000000" android:strokeColor="#FFFFFFFF" android:strokeWidth="1.8" android:strokeLineJoin="round" android:pathData="{path}"/>'
        else:
            content = ''.join(f'<path android:fillColor="{fill}" android:strokeColor="{stroke}" android:strokeWidth="{width}" android:strokeLineJoin="round" android:pathData="{path}"/>'
                              for width, fill, stroke in [(8, '#00000000', '#05000000'), (5, '#00000000', '#08000000'), (2, '#26000000', '#10000000')])
        y = 10 if layer == 'shadow' else 8
        yield f'widget_symbol_{name}_{layer}.xml', (HEADER + f'<!-- SPDX-License-Identifier: GPL-3.0-or-later; {note} -->\n' + VECTOR +
                                                   f'<group android:translateX="8" android:translateY="{y}" android:scaleX="0.8888889" android:scaleY="0.8888889">{content}</group></vector>\n')


def main():
    check = '--check' in sys.argv
    count = 0
    for name, build in SHAPES.items():
        for file_name, text in documents(name, path_data(build())):
            target = RES / file_name
            if check:
                assert target.exists() and target.read_text() == text, f'{target} is not current: run tools/generate_favorite_shapes.py'
            else:
                target.write_text(text)
            count += 1
    print(f'{count} favourite shape drawables {"verified" if check else "generated"}')


if __name__ == '__main__':
    main()
