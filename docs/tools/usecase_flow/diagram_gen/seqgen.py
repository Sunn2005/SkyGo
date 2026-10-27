#!/usr/bin/env python3
"""seqgen: sinh Sequence Diagram kieu robustness (boundary / control / entity) tu file .seq

Ra 3 file cung ten voi file .seq:
  <ten>.drawio  - mo bang draw.io de sua tay
  <ten>.svg     - ban vector
  <ten>.png     - anh de chen Word (can playwright, xem README; thieu thi bo qua)

Cach dung:  python seqgen.py spec.seq [-o thu_muc_ra] [--scale 1.5] [--no-png]
Cu phap file .seq: xem SYNTAX.md
"""
import argparse
import os
import re
import shlex
import sys
from xml.sax.saxutils import escape

FILL = '#7ED3F7'
KINDS = ('actor', 'boundary', 'control', 'entity')
X0, COL = 60, 165          # x cua cot dau, khoang cach giua cac cot
ICON_CY, LIFE0, STRIP_W = 72, 120, 150
MSG_RE = re.compile(r'^(\w+)\s*(-->|->)\s*(\w+)\s*(?:\[(create|destroy)\])?\s*:\s*(.*)$')
FRAME_RE = re.compile(r'^(alt|loop)\s*:\s*(.*)$', re.I)
ELSE_RE = re.compile(r'^else\s*:\s*(.*)$', re.I)


class SpecError(Exception):
    pass


def esc(t):
    return escape(t, {'"': '&quot;'})


def dv(label):
    return esc(label).replace('\n', '&lt;br&gt;')


# ------------------------------------------------------------------ parse
def parse(text):
    title, parts, ev, stack = 'Sequence', [], [], []
    ids = set()
    for n, raw in enumerate(text.splitlines(), 1):
        line = raw.strip()
        if not line or line.startswith('#'):
            continue
        try:
            if line.lower().startswith('title:'):
                title = line.split(':', 1)[1].strip()
                continue
            head = line.split(None, 1)[0].lower()
            if head in KINDS:
                a = shlex.split(line)
                if len(a) < 3:
                    raise SpecError('khai bao participant can: <loai> <id> "<ten>"')
                rest = a[3:]
                created = 'created' in rest
                rest = [r for r in rest if r != 'created']
                if a[1] in ids:
                    raise SpecError(f'id "{a[1]}" bi trung')
                ids.add(a[1])
                parts.append(dict(kind=head, id=a[1], label=a[2], strip=rest[0] if rest else a[2],
                                  created=created, create_y=None, destroy_y=None))
                continue
            m = FRAME_RE.match(line)
            if m:
                stack.append(m.group(1).lower())
                ev.append(dict(t='open', kind=m.group(1).lower(), cond=m.group(2).strip(), n=n))
                continue
            m = ELSE_RE.match(line)
            if m:
                if not stack or stack[-1] != 'alt':
                    raise SpecError('"else:" chi dung ben trong khoi alt')
                ev.append(dict(t='else', cond=m.group(1).strip(), n=n))
                continue
            if line.lower() == 'end':
                if not stack:
                    raise SpecError('"end" thua, khong co alt/loop dang mo')
                stack.pop()
                ev.append(dict(t='end', n=n))
                continue
            m = MSG_RE.match(line)
            if m:
                f, arrow, t, flag, lab = m.groups()
                for pid in (f, t):
                    if pid not in ids:
                        raise SpecError(f'chua khai bao participant "{pid}"')
                if f == t:
                    raise SpecError('chua ho tro self-message')
                ev.append(dict(t='m', f=f, to=t, kind=flag or ('ret' if arrow == '-->' else 'call'),
                               lab=lab.replace('\\n', '\n').strip(), n=n))
                continue
            raise SpecError('khong hieu dong nay')
        except SpecError as e:
            raise SpecError(f'dong {n}: {e}\n    > {raw.strip()}')
    if stack:
        raise SpecError('con khoi alt/loop chua dong bang "end"')
    if not parts:
        raise SpecError('chua khai bao participant nao')
    pid = {p['id']: p for p in parts}
    for p in parts:
        if p['created'] and not any(e['t'] == 'm' and e['to'] == p['id'] and e['kind'] == 'create' for e in ev):
            raise SpecError(f'"{p["id"]}" danh dau created nhung khong co message [create] toi no')
    for e in ev:
        if e['t'] == 'm' and e['kind'] == 'create' and not pid[e['to']]['created']:
            raise SpecError(f'dong {e["n"]}: [create] toi "{e["to"]}" nhung participant nay chua danh dau created')
    return title, parts, ev


# ------------------------------------------------------------------ layout
def layout(parts, ev):
    idx = {p['id']: i for i, p in enumerate(parts)}
    y, msgs, frames, stack = 165, [], [], []
    for e in ev:
        t = e['t']
        if t == 'm':
            y += 14 + 12 * len(e['lab'].split('\n'))
            m = dict(f=idx[e['f']], t=idx[e['to']], y=y, lab=e['lab'], kind=e['kind'])
            msgs.append(m)
            if m['kind'] == 'create':
                parts[m['t']]['create_y'] = y
                y += 30
            if m['kind'] == 'destroy':
                parts[m['t']]['destroy_y'] = y + 22
                y += 14
            y += 12
        elif t == 'open':
            y += 16
            stack.append(dict(kind=e['kind'], y1=y, cond=e['cond'], x1=4 + 12 * len(stack), dividers=[]))
            y += 28
        elif t == 'else':
            y += 4
            stack[-1]['dividers'].append(dict(y=y, cond=e['cond']))
            y += 28
        elif t == 'end':
            y += 6
            f = stack.pop()
            f['y2'] = y
            frames.append(f)
            y += 14
    H = y + 30
    return msgs, frames, H, H - 15


def bars(parts, msgs):
    out = []
    for i, p in enumerate(parts):
        ms = [m for m in msgs if i in (m['f'], m['t']) and not (m['t'] == i and m['kind'] == 'create')]
        ys = sorted(m['y'] for m in ms)
        if not ys:
            continue
        if p['kind'] == 'actor':
            out.append((i, ys[0] - 8, ys[-1] + 8))
            continue
        cur = None
        for v in ys:
            if cur and v - cur[1] <= 60:
                cur[1] = v
            else:
                if cur:
                    out.append((i, cur[0] - 8, cur[1] + 8))
                cur = [v, v]
        out.append((i, cur[0] - 8, cur[1] + 8))
    return out


def endpoints(m, lx):
    right = m['t'] > m['f']
    s = lx[m['f']] + (6 if right else -6)
    e = lx[m['t']] + (-6 if right else 6)
    if m['kind'] == 'create':
        e = lx[m['t']] - 26
    return s, e


# ------------------------------------------------------------------ draw.io
def to_drawio(title, parts, msgs, frames, H, LIFE_END, bar_list):
    n = len(parts)
    lx = [X0 + COL * i for i in range(n)]
    W = lx[-1] + 60
    c = ['<mxCell id="0"/>', '<mxCell id="1" parent="0"/>']
    for k, f in enumerate(frames):
        x1, x2 = f['x1'], W - f['x1']
        c.append(f'<mxCell id="fr{k}" value="{f["kind"]}" style="shape=umlFrame;whiteSpace=wrap;html=1;width=34;height=20;fontSize=11;fillColor=none;strokeColor=#000000;" vertex="1" parent="1"><mxGeometry x="{x1}" y="{f["y1"]}" width="{x2-x1}" height="{f["y2"]-f["y1"]}" as="geometry"/></mxCell>')
        c.append(f'<mxCell id="frc{k}" value="{esc(f["cond"])}" style="text;html=1;align=left;verticalAlign=middle;fontSize=11;" vertex="1" parent="1"><mxGeometry x="82" y="{f["y1"]}" width="320" height="20" as="geometry"/></mxCell>')
        for j, d in enumerate(f['dividers']):
            c.append(f'<mxCell id="dv{k}_{j}" style="endArrow=none;dashed=1;html=1;strokeColor=#000000;" edge="1" parent="1"><mxGeometry relative="1" as="geometry"><mxPoint x="{x1}" y="{d["y"]}" as="sourcePoint"/><mxPoint x="{x2}" y="{d["y"]}" as="targetPoint"/></mxGeometry></mxCell>')
            c.append(f'<mxCell id="dvc{k}_{j}" value="{esc(d["cond"])}" style="text;html=1;align=left;verticalAlign=middle;fontSize=11;" vertex="1" parent="1"><mxGeometry x="82" y="{d["y"]+2}" width="320" height="20" as="geometry"/></mxCell>')
    for i, p in enumerate(parts):
        c.append(f'<mxCell id="st{i}" value="{esc(p["strip"])}" style="rounded=0;whiteSpace=wrap;html=1;fillColor=#E6E6E6;strokeColor=#AAAAAA;fontSize=11;" vertex="1" parent="1"><mxGeometry x="{lx[i]-STRIP_W//2}" y="4" width="{STRIP_W}" height="26" as="geometry"/></mxCell>')
    for i, p in enumerate(parts):
        cy = p['create_y'] if p['created'] else ICON_CY
        shp = {'actor': ('umlActor', 32, 56), 'boundary': ('umlBoundary', 60, 44),
               'control': ('umlControl', 44, 48), 'entity': ('umlEntity', 44, 44)}[p['kind']]
        c.append(f'<mxCell id="p{i}" value="{esc(p["label"])}" style="shape={shp[0]};whiteSpace=wrap;html=1;verticalLabelPosition=bottom;verticalAlign=top;fillColor={FILL};strokeColor=#000000;fontSize=11;" vertex="1" parent="1"><mxGeometry x="{lx[i]-shp[1]/2}" y="{cy-shp[2]/2}" width="{shp[1]}" height="{shp[2]}" as="geometry"/></mxCell>')
        a = p['create_y'] + 46 if p['created'] else LIFE0
        b = p['destroy_y'] or LIFE_END
        c.append(f'<mxCell id="ll{i}" style="endArrow=none;dashed=1;html=1;strokeColor=#000000;" edge="1" parent="1"><mxGeometry relative="1" as="geometry"><mxPoint x="{lx[i]}" y="{a}" as="sourcePoint"/><mxPoint x="{lx[i]}" y="{b}" as="targetPoint"/></mxGeometry></mxCell>')
        if p['destroy_y']:
            c.append(f'<mxCell id="x{i}" value="" style="shape=umlDestroy;whiteSpace=wrap;html=1;strokeWidth=2;strokeColor=#000000;" vertex="1" parent="1"><mxGeometry x="{lx[i]-10}" y="{p["destroy_y"]-10}" width="20" height="20" as="geometry"/></mxCell>')
    for k, (i, a, b) in enumerate(bar_list):
        c.append(f'<mxCell id="b{k}" value="" style="html=1;points=[];fillColor={FILL};strokeColor=#000000;" vertex="1" parent="1"><mxGeometry x="{lx[i]-6}" y="{a}" width="12" height="{b-a}" as="geometry"/></mxCell>')
    for k, m in enumerate(msgs):
        s, e = endpoints(m, lx)
        dashed = m['kind'] in ('ret', 'create')
        st = 'html=1;verticalAlign=bottom;endArrow=%s;endFill=%d;endSize=%d;strokeColor=#000000;fontSize=11;%s' % (
            'open' if dashed else 'block', 0 if dashed else 1, 8 if dashed else 10, 'dashed=1;' if dashed else '')
        c.append(f'<mxCell id="m{k}" value="{dv(m["lab"])}" style="{st}" edge="1" parent="1"><mxGeometry relative="1" as="geometry"><mxPoint x="{s}" y="{m["y"]}" as="sourcePoint"/><mxPoint x="{e}" y="{m["y"]}" as="targetPoint"/></mxGeometry></mxCell>')
    return ('<mxfile host="app.diagrams.net"><diagram name="%s" id="seq1"><mxGraphModel dx="%d" dy="%d" grid="1" gridSize="10" guides="1" tooltips="1" connect="1" arrows="1" fold="1" page="1" pageScale="1" pageWidth="%d" pageHeight="%d" math="0" shadow="0"><root>%s</root></mxGraphModel></diagram></mxfile>'
            % (esc(title), W, H, W, H, ''.join(c))), W


# ------------------------------------------------------------------ svg
def tx(label, x, y, fs=11, anchor='middle'):
    ls = label.split('\n')
    lh = fs * 1.25
    y0 = y - (len(ls) - 1) * lh / 2
    return ''.join(f'<text x="{x}" y="{y0 + i*lh + fs*0.35}" text-anchor="{anchor}" font-size="{fs}">{esc(t)}</text>' for i, t in enumerate(ls))


def to_svg(parts, msgs, frames, H, LIFE_END, bar_list, W):
    n = len(parts)
    lx = [X0 + COL * i for i in range(n)]
    o = [f'<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}" font-family="DejaVu Sans, Arial, sans-serif" fill="#000"><rect width="100%" height="100%" fill="#fff"/>',
         '<defs><marker id="fa" viewBox="0 0 10 10" refX="10" refY="5" markerWidth="13" markerHeight="13" orient="auto"><path d="M0,0 L10,5 L0,10 z" fill="#000"/></marker><marker id="oa" viewBox="0 0 10 10" refX="10" refY="5" markerWidth="11" markerHeight="11" orient="auto"><path d="M0,0 L10,5 L0,10" fill="none" stroke="#000" stroke-width="1.2"/></marker></defs>']
    for f in frames:
        x1, x2 = f['x1'], W - f['x1']
        o.append(f'<rect x="{x1}" y="{f["y1"]}" width="{x2-x1}" height="{f["y2"]-f["y1"]}" fill="none" stroke="#000" stroke-width="1.2"/>')
        o.append(f'<polygon points="{x1},{f["y1"]} {x1+34},{f["y1"]} {x1+34},{f["y1"]+11} {x1+27},{f["y1"]+20} {x1},{f["y1"]+20}" fill="{FILL}" stroke="#000"/>')
        o.append(tx(f['kind'], x1 + 15, f['y1'] + 10) + tx(f['cond'], 82, f['y1'] + 10, 11, 'start'))
        for d in f['dividers']:
            o.append(f'<line x1="{x1}" y1="{d["y"]}" x2="{x2}" y2="{d["y"]}" stroke="#000" stroke-dasharray="6 4"/>')
            o.append(tx(d['cond'], 82, d['y'] + 12, 11, 'start'))
    for i, p in enumerate(parts):
        o.append(f'<rect x="{lx[i]-STRIP_W//2}" y="4" width="{STRIP_W}" height="26" fill="#E6E6E6" stroke="#AAA"/>' + tx(p['strip'], lx[i], 17))
    for i, p in enumerate(parts):
        x = lx[i]
        cy = p['create_y'] if p['created'] else ICON_CY
        a = p['create_y'] + 46 if p['created'] else LIFE0
        b = p['destroy_y'] or LIFE_END
        o.append(f'<line x1="{x}" y1="{a}" x2="{x}" y2="{b}" stroke="#000" stroke-dasharray="6 4"/>')
        k = p['kind']
        if k == 'actor':
            o.append(f'<circle cx="{x}" cy="{cy-18}" r="9" fill="{FILL}" stroke="#000"/><line x1="{x}" y1="{cy-9}" x2="{x}" y2="{cy+10}" stroke="#000"/><line x1="{x-14}" y1="{cy-2}" x2="{x+14}" y2="{cy-2}" stroke="#000"/><line x1="{x}" y1="{cy+10}" x2="{x-12}" y2="{cy+26}" stroke="#000"/><line x1="{x}" y1="{cy+10}" x2="{x+12}" y2="{cy+26}" stroke="#000"/>')
        elif k == 'boundary':
            o.append(f'<circle cx="{x+10}" cy="{cy}" r="21" fill="{FILL}" stroke="#000" stroke-width="1.3"/><line x1="{x-30}" y1="{cy}" x2="{x-11}" y2="{cy}" stroke="#000" stroke-width="1.5"/><line x1="{x-30}" y1="{cy-17}" x2="{x-30}" y2="{cy+17}" stroke="#000" stroke-width="2"/>')
        elif k == 'control':
            o.append(f'<circle cx="{x}" cy="{cy+2}" r="21" fill="{FILL}" stroke="#000" stroke-width="1.3"/><path d="M{x-5},{cy-25} L{x+4},{cy-20} L{x-5},{cy-15}" fill="none" stroke="#000" stroke-width="1.5"/>')
        else:
            o.append(f'<circle cx="{x}" cy="{cy}" r="21" fill="{FILL}" stroke="#000" stroke-width="1.3"/><line x1="{x-22}" y1="{cy+25}" x2="{x+22}" y2="{cy+25}" stroke="#000" stroke-width="1.5"/>')
        o.append(tx(p['label'], x, cy + 40))
        if p['destroy_y']:
            dy = p['destroy_y']
            o.append(f'<line x1="{x-9}" y1="{dy-9}" x2="{x+9}" y2="{dy+9}" stroke="#000" stroke-width="2.2"/><line x1="{x-9}" y1="{dy+9}" x2="{x+9}" y2="{dy-9}" stroke="#000" stroke-width="2.2"/>')
    for i, a, b in bar_list:
        o.append(f'<rect x="{lx[i]-6}" y="{a}" width="12" height="{b-a}" fill="{FILL}" stroke="#000"/>')
    for m in msgs:
        s, e = endpoints(m, lx)
        dashed = m['kind'] in ('ret', 'create')
        dash = 'stroke-dasharray="6 4"' if dashed else ''
        o.append(f'<line x1="{s}" y1="{m["y"]}" x2="{e}" y2="{m["y"]}" stroke="#000" stroke-width="1.2" {dash} marker-end="url(#{"oa" if dashed else "fa"})"/>')
        ls = m['lab'].split('\n')
        mx = (s + e) / 2
        wd = max(len(t) for t in ls) * 6.4 + 6
        h = 13.5 * len(ls)
        o.append(f'<rect x="{mx-wd/2}" y="{m["y"]-5-h}" width="{wd}" height="{h+1}" fill="#fff" opacity="0.85"/>')
        o.append(''.join(f'<text x="{mx}" y="{m["y"]-5-(len(ls)-1-i)*13.5}" text-anchor="middle" font-size="11">{esc(t_)}</text>' for i, t_ in enumerate(ls)))
    o.append('</svg>')
    return ''.join(o)


# ------------------------------------------------------------------ main
def main():
    ap = argparse.ArgumentParser(description='Sinh sequence diagram (.drawio/.svg/.png) tu file .seq')
    ap.add_argument('spec')
    ap.add_argument('-o', '--out', help='thu muc xuat (mac dinh: cung thu muc voi file .seq)')
    ap.add_argument('--scale', type=float, default=1.5, help='do phan giai PNG (mac dinh 1.5)')
    ap.add_argument('--no-png', action='store_true', help='chi ra .drawio va .svg')
    a = ap.parse_args()
    try:
        title, parts, ev = parse(open(a.spec, encoding='utf8').read())
    except SpecError as e:
        sys.exit(f'LOI: {e}')
    msgs, frames, H, LIFE_END = layout(parts, ev)
    bl = bars(parts, msgs)
    drawio, W = to_drawio(title, parts, msgs, frames, H, LIFE_END, bl)
    svg = to_svg(parts, msgs, frames, H, LIFE_END, bl, W)
    out = a.out or os.path.dirname(os.path.abspath(a.spec))
    os.makedirs(out, exist_ok=True)
    stem = os.path.join(out, os.path.splitext(os.path.basename(a.spec))[0])
    open(stem + '.drawio', 'w', encoding='utf8').write(drawio)
    open(stem + '.svg', 'w', encoding='utf8').write(svg)
    print(f'da ghi {stem}.drawio, {stem}.svg  ({W}x{H}px, {len(msgs)} message)')
    if a.no_png:
        return
    try:
        from playwright.sync_api import sync_playwright
    except ImportError:
        print('Bo qua PNG: chua cai playwright. Cach 1: pip install playwright && playwright install chromium. '
              'Cach 2: mo .drawio trong draw.io, File > Export as > PNG.')
        return
    with sync_playwright() as p:
        b = p.chromium.launch(args=['--no-sandbox'])
        pg = b.new_page(viewport={'width': W, 'height': H}, device_scale_factor=a.scale)
        pg.goto('file://' + os.path.abspath(stem + '.svg'))
        pg.screenshot(path=stem + '.png')
        b.close()
    print(f'da ghi {stem}.png')


if __name__ == '__main__':
    main()
