#!/usr/bin/env python3
"""actgen: sinh Activity Diagram swimlane tu file .act (cung phong cach seqgen.py)

Ra 3 file cung ten voi file .act:
  <ten>.drawio  - mo bang draw.io de sua tay
  <ten>.svg     - ban vector
  <ten>.png     - anh de chen Word (can playwright; thieu thi bo qua)

Cach dung:  python actgen.py spec.act [-o thu_muc_ra] [--scale 1.5] [--no-png]

Cu phap .act (dong bat dau bang # la ghi chu):
  title: UC09 Activity
  lane   N "Nguoi hoc"                  # khai bao lane theo thu tu trai -> phai
  start    st N                         # <loai> <id> <lane> "<nhan>" [down=k]
  action   a1 N "Nhan co the\\nxuong dong"
  decision d1 S "Dieu kien?"
  fork     f1                           # thanh ngang, khong can lane / nhan
  join     j1
  end      e1 S
  a1 -> d1                              # canh
  d1 -> a2 : Co                         # canh co nhan
  d1 -> a3 : Khong [side]               # [side]: dich nam cung hang voi nguon
  d2 -> a1 : Thu lai [back]             # [back]: canh quay nguoc, di vong ra le phai
Hang cua node tu tinh theo do dai duong di; "down=k" day node xuong them k hang.
Cung hang + cung lane: tu xep trai/phai theo vi tri node di truoc (hoa thi theo thu tu khai bao).
"""
import argparse
import os
import re
import shlex
import sys
from xml.sax.saxutils import escape

FILL = '#7ED3F7'
KINDS = ('start', 'end', 'action', 'decision')
BARS = ('fork', 'join')
ACT_W, DEC_W = 190, 170
SLOT_W, LANE_MIN = 225, 250
GAP, TOP, HDR = 56, 56, 30
BACK0, BACK_STEP = 34, 26
PAD = 6
CHAN = [0, -12, 12, -22, 22]


class SpecError(Exception):
    pass


def esc(t):
    return escape(t, {'"': '&quot;'})


def wrap(text, width):
    out = []
    for para in text.replace('\\n', '\n').split('\n'):
        line = ''
        for w in para.split():
            if line and len(line) + 1 + len(w) > width:
                out.append(line)
                line = w
            else:
                line = (line + ' ' + w).strip()
        out.append(line)
    return out or ['']


# ------------------------------------------------------------------ parse
def parse(text):
    title, lanes, nodes, edges = 'Activity', [], {}, []
    order = []
    for n, raw in enumerate(text.splitlines(), 1):
        line = raw.strip()
        if not line or line.startswith('#'):
            continue
        try:
            if line.lower().startswith('title:'):
                title = line.split(':', 1)[1].strip()
                continue
            head = line.split(None, 1)[0].lower()
            if head == 'lane':
                a = shlex.split(line)
                if len(a) != 3:
                    raise SpecError('khai bao lane can: lane <id> "<ten>"')
                if any(l['id'] == a[1] for l in lanes):
                    raise SpecError(f'lane "{a[1]}" bi trung')
                lanes.append(dict(id=a[1], name=a[2]))
                continue
            if head in KINDS or head in BARS:
                a = shlex.split(line)
                down = 0
                rest = []
                for t in a[2:]:
                    m = re.match(r'^down=(\d+)$', t)
                    if m:
                        down = int(m.group(1))
                    else:
                        rest.append(t)
                nid = a[1] if len(a) > 1 else ''
                if not re.match(r'^\w+$', nid):
                    raise SpecError('thieu id node')
                if nid in nodes:
                    raise SpecError(f'id "{nid}" bi trung')
                if head in BARS:
                    lane, label = None, ''
                else:
                    if not rest:
                        raise SpecError(f'khai bao can: {head} <id> <lane> "<nhan>"')
                    lane = rest[0]
                    label = rest[1] if len(rest) > 1 else ''
                    if not any(l['id'] == lane for l in lanes):
                        raise SpecError(f'chua khai bao lane "{lane}"')
                    if head in ('action', 'decision') and not label:
                        raise SpecError(f'{head} can co nhan')
                nodes[nid] = dict(id=nid, kind=head, lane=lane, label=label, down=down, n=n)
                order.append(nid)
                continue
            m = re.match(r'^(\w+)\s*->\s*(\w+)\s*(.*)$', line)
            if m:
                s, t, rest = m.groups()
                flags = set(re.findall(r'\[(back|side)\]', rest))
                rest = re.sub(r'\[(?:back|side)\]', '', rest).strip()
                label = rest[1:].strip() if rest.startswith(':') else ''
                if rest and not rest.startswith(':'):
                    raise SpecError('khong hieu phan sau mui ten (nhan phai bat dau bang ":")')
                edges.append(dict(s=s, t=t, label=label.replace('\\n', '\n'),
                                  back='back' in flags, side='side' in flags, n=n))
                continue
            raise SpecError('khong hieu dong nay')
        except SpecError as e:
            raise SpecError(f'dong {n}: {e}\n    > {raw.strip()}')
    if not lanes:
        raise SpecError('chua khai bao lane nao')
    for e in edges:
        for k in ('s', 't'):
            if e[k] not in nodes:
                raise SpecError(f'dong {e["n"]}: chua khai bao node "{e[k]}"')
    for nid in order:
        nd = nodes[nid]
        outs = [e for e in edges if e['s'] == nid]
        ins = [e for e in edges if e['t'] == nid]
        if nd['kind'] == 'start' and ins:
            raise SpecError(f'start "{nid}" khong duoc co canh di vao')
        if nd['kind'] == 'end' and outs:
            raise SpecError(f'end "{nid}" khong duoc co canh di ra')
        if nd['kind'] != 'start' and not ins:
            raise SpecError(f'node "{nid}" khong co canh di vao')
        if nd['kind'] != 'end' and not outs:
            raise SpecError(f'node "{nid}" khong co canh di ra')
        if nd['kind'] == 'decision' and len(outs) < 2:
            raise SpecError(f'decision "{nid}" can it nhat 2 canh di ra')
        if nd['kind'] == 'fork' and len(outs) < 2:
            raise SpecError(f'fork "{nid}" can it nhat 2 canh di ra')
        if nd['kind'] == 'join' and len(ins) < 2:
            raise SpecError(f'join "{nid}" can it nhat 2 canh di vao')
    return title, lanes, nodes, order, edges


# ------------------------------------------------------------------ layout
def assign_rows(nodes, order, edges):
    fwd = [e for e in edges if not e['back']]
    bump = {i: 0 for i in order}

    def relax():
        row = {i: 0 for i in order}
        for _ in range(len(order) + 3):
            changed = False
            for t in order:
                preds = [e for e in fwd if e['t'] == t]
                r = max([row[e['s']] + (0 if e['side'] else 1) for e in preds], default=0) + nodes[t]['down'] + bump[t]
                if r != row[t]:
                    row[t] = r
                    changed = True
            if not changed:
                return row
        raise SpecError('co vong lap chua danh dau [back] (canh quay nguoc phai co [back])')

    row = relax()
    for _ in range(len(order) * 3 + 5):
        brows = {row[i] for i in order if nodes[i]['kind'] in BARS}
        moved = False
        for t in order:
            if nodes[t]['kind'] not in BARS and row[t] in brows:
                bump[t] += 1
                moved = True
        if not moved:
            break
        row = relax()
    for e in fwd:
        if row[e['t']] < row[e['s']]:
            raise SpecError(f'canh {e["s"]} -> {e["t"]} di nguoc len, hay them [back]')
    return row


def size_of(nd):
    k = nd['kind']
    if k == 'start':
        nd['lines'], nd['w'], nd['h'] = [], 24, 24
    elif k == 'end':
        nd['lines'], nd['w'], nd['h'] = [], 28, 28
    elif k == 'action':
        nd['lines'] = wrap(nd['label'], 27)
        nd['w'], nd['h'] = ACT_W, max(50, 24 + 17 * len(nd['lines']))
    elif k == 'decision':
        nd['lines'] = wrap(nd['label'], 15)
        nd['w'], nd['h'] = DEC_W, max(90, 44 + 18 * len(nd['lines']))
    else:
        nd['lines'], nd['w'], nd['h'] = [], 120, 10


def layout(lanes, nodes, order, edges):
    row = assign_rows(nodes, order, edges)
    for nid in order:
        nodes[nid]['row'] = row[nid]
        size_of(nodes[nid])
    nrows = max(row.values()) + 1
    lane_idx = {l['id']: i for i, l in enumerate(lanes)}
    # slot trong (lane, hang)
    groups = {}
    for nid in order:
        nd = nodes[nid]
        if nd['lane'] is None:
            continue
        groups.setdefault((nd['lane'], nd['row']), []).append(nid)
    lane_w = []
    for l in lanes:
        m = max([len(v) for (ln, r), v in groups.items() if ln == l['id']] or [1])
        lane_w.append(max(LANE_MIN, m * SLOT_W))
    nback = sum(1 for e in edges if e['back'])
    margin = (BACK0 + BACK_STEP * nback) if nback else 0
    lane_x = [sum(lane_w[:i]) for i in range(len(lanes))]
    lane_w[-1] += margin
    W = sum(lane_w)
    # chieu cao hang
    rh = [max([nodes[i]['h'] for i in order if nodes[i]['row'] == r] or [10]) for r in range(nrows)]
    rtop, y = [], TOP
    for r in range(nrows):
        rtop.append(y)
        y += rh[r] + GAP
    H = y - GAP + 40
    fwd_all = [e for e in edges if not e['back']]
    fwd = [e for e in fwd_all if not e['side']]
    for r in range(nrows):
        for l in lanes:
            ids = groups.get((l['id'], r))
            if not ids:
                continue
            li = lane_idx[l['id']]
            usable = lane_w[li] - (margin if li == len(lanes) - 1 else 0)

            def key(nid):
                xs = [nodes[e['s']]['cx'] for e in fwd_all if e['t'] == nid and 'cx' in nodes[e['s']]]
                return sum(xs) / len(xs) if xs else None
            keys = [key(i) for i in ids]
            if len(ids) > 1 and all(k is not None for k in keys):
                ids.sort(key=key)
            for k, nid in enumerate(ids):
                nodes[nid]['cx'] = lane_x[li] + usable * (k + 0.5) / len(ids)
            if len(ids) == 1:
                nd = nodes[ids[0]]
                for e in fwd:
                    p = nodes[e['s']]
                    if e['t'] == nd['id'] and p['lane'] == nd['lane'] and p['row'] < nd['row'] and 'cx' in p:
                        lo, hi = lane_x[li] + nd['w'] / 2 + 8, lane_x[li] + usable - nd['w'] / 2 - 8
                        nd['cx'] = min(hi, max(lo, p['cx']))
                        break
    for nid in order:
        nd = nodes[nid]
        nd['cy'] = rtop[nd['row']] + rh[nd['row']] / 2
    # thanh fork / join: rong theo cac node ke ben
    for nid in order:
        nd = nodes[nid]
        if nd['lane'] is not None:
            continue
        nb = [nodes[e['t'] if e['s'] == nid else e['s']] for e in edges if nid in (e['s'], e['t'])]
        xs = [b['cx'] for b in nb if 'cx' in b]
        if not xs:
            xs = [W / 2]
        nd['cx'] = (min(xs) + max(xs)) / 2
        nd['w'] = max(120, max(xs) - min(xs) + 90)
    for nd in nodes.values():
        nd['x1'], nd['x2'] = nd['cx'] - nd['w'] / 2, nd['cx'] + nd['w'] / 2
        nd['y1'], nd['y2'] = nd['cy'] - nd['h'] / 2, nd['cy'] + nd['h'] / 2
    return dict(W=W, H=H, lane_x=lane_x, lane_w=lane_w, rtop=rtop, rh=rh, margin=margin, lane_idx=lane_idx)


# ------------------------------------------------------------------ routing
def seg_hits(a, b, box):
    x1, y1, x2, y2 = box
    if a[0] == b[0]:
        return x1 < a[0] < x2 and min(a[1], b[1]) < y2 and max(a[1], b[1]) > y1
    if a[1] == b[1]:
        return y1 < a[1] < y2 and min(a[0], b[0]) < x2 and max(a[0], b[0]) > x1
    return True


def blockers(pts, nodes, skip):
    hit = []
    for nd in nodes.values():
        if nd['id'] in skip:
            continue
        p = 2 if nd['kind'] in BARS else PAD
        box = (nd['x1'] - p, nd['y1'] - p, nd['x2'] + p, nd['y2'] + p)
        if any(seg_hits(pts[i], pts[i + 1], box) for i in range(len(pts) - 1)):
            hit.append(nd['id'])
    return hit


def side_of(nd, pt):
    if abs(pt[1] - nd['y2']) < 0.5 and nd['x1'] - 0.5 <= pt[0] <= nd['x2'] + 0.5:
        return 'bottom'
    if abs(pt[1] - nd['y1']) < 0.5 and nd['x1'] - 0.5 <= pt[0] <= nd['x2'] + 0.5:
        return 'top'
    return 'right' if pt[0] > nd['cx'] else 'left'


def route_all(nodes, order, edges, L):
    used = {}
    chan = {}
    warns = []
    rtop = L['rtop']
    # bien phai cho canh back: canh ngan nam gan noi dung hon
    backs = sorted([e for e in edges if e['back']],
                   key=lambda e: abs(nodes[e['s']]['row'] - nodes[e['t']]['row']))
    for k, e in enumerate(backs):
        e['mx'] = L['W'] - L['margin'] + BACK0 - 8 + BACK_STEP * k if L['margin'] else L['W'] - 20
    # canh hang theo thu tu: side truoc, roi forward, back sau cung
    seq = sorted(edges, key=lambda e: (e['back'], not e['side']))
    for e in seq:
        s, t = nodes[e['s']], nodes[e['t']]
        cands = []
        if e['back']:
            mx = e['mx']
            yb = rtop[s['row']] + L['rh'][s['row']] + GAP / 2
            yt = rtop[t['row']] - GAP / 2
            cands = [
                [(s['cx'], s['y1']), (s['cx'], t['y2'])] if abs(s['cx'] - t['cx']) < 2 and t['row'] < s['row'] else
                [(s['x2'], s['cy']), (mx, s['cy']), (mx, t['cy']), (t['x2'], t['cy'])],
                [(s['x2'], s['cy']), (mx, s['cy']), (mx, t['cy']), (t['x2'], t['cy'])],
                [(s['x2'], s['cy']), (mx, s['cy']), (mx, yt), (t['cx'], yt), (t['cx'], t['y1'])],
                [(s['cx'], s['y2']), (s['cx'], yb), (mx, yb), (mx, t['cy']), (t['x2'], t['cy'])],
                [(s['cx'], s['y2']), (s['cx'], yb), (mx, yb), (mx, yt), (t['cx'], yt), (t['cx'], t['y1'])],
            ]
        elif s['kind'] in BARS or t['kind'] in BARS:
            if s['kind'] in BARS and t['kind'] in BARS:
                x = (s['cx'] + t['cx']) / 2
            elif s['kind'] in BARS:
                x = t['cx']
            else:
                x = s['cx']
            cands = [[(x, s['y2']), (x, t['y1'])]]
        elif t['row'] == s['row']:
            if t['cx'] > s['cx']:
                cands = [[(s['x2'], s['cy']), (t['x1'], t['cy'])]]
            else:
                cands = [[(s['x1'], s['cy']), (t['x2'], t['cy'])]]
        else:
            ym = rtop[t['row']] - GAP / 2
            xs = 'right' if t['cx'] > s['cx'] else 'left'
            sx = s['x2'] if xs == 'right' else s['x1']
            tx = t['x1'] if xs == 'right' else t['x2']
            A = [(s['cx'], s['y2']), (t['cx'], t['y1'])]
            C = [(sx, s['cy']), (t['cx'], s['cy']), (t['cx'], t['y1'])]
            D = [(s['cx'], s['y2']), (s['cx'], ym), (t['cx'], ym), (t['cx'], t['y1'])]
            B = [(s['cx'], s['y2']), (s['cx'], t['cy']), (tx, t['cy'])]
            if abs(s['cx'] - t['cx']) < 2:
                cands = [A, C, D, B]
            elif s['lane'] != t['lane'] or s['kind'] == 'decision':
                cands = [C, D, B]
            else:
                cands = [D, C, B]
            if s['kind'] == 'decision' and abs(s['cx'] - t['cx']) >= 2 and s['lane'] == t['lane']:
                cands = [D, C, B]
        # duong vong: canh dung thang bi node chan thi di vong ben phai node chan
        if not e['back'] and cands and cands[0][0][0] == cands[0][-1][0] and t['row'] > s['row'] + 1:
            c0 = cands[0]
            bl = blockers(c0, nodes, {s['id'], t['id']})
            if bl:
                xb = max(nodes[b]['x2'] for b in bl) + 18
                y1 = rtop[s['row']] + L['rh'][s['row']] + GAP / 2
                y2 = rtop[t['row']] - GAP / 2
                x0, x3 = c0[0][0], c0[-1][0]
                det = [(x0, s['y2']), (x0, y1), (xb, y1), (xb, y2), (x3, y2), (x3, t['y1'])]
                if not blockers(det, nodes, {s['id'], t['id']}):
                    cands = [det] + cands
        chosen = None
        for pts in cands:
            ex = side_of(s, pts[0])
            if used.get((s['id'], ex)) and s['kind'] not in BARS:
                continue
            if t['kind'] not in BARS and used.get((t['id'], side_of(t, pts[-1]))):
                continue
            if not blockers(pts, nodes, {s['id'], t['id']}):
                chosen = pts
                break
        if chosen is None:
            for pts in cands:
                ex = side_of(s, pts[0])
                if not used.get((s['id'], ex)) or s['kind'] in BARS:
                    chosen = pts
                    break
            chosen = chosen or cands[0]
            bl = blockers(chosen, nodes, {s['id'], t['id']})
            if bl:
                warns.append(f'canh {e["s"]} -> {e["t"]} (dong {e["n"]}) di xuyen qua node {", ".join(bl)}; '
                             'dung down=k hoac doi thu tu khai bao de tranh')
        # moi duong ngang trong khe giua hai hang di mot "lan" rieng de khong dinh nhau
        chosen = [list(q) for q in chosen]
        for i in range(len(chosen) - 1):
            a_, b_ = chosen[i], chosen[i + 1]
            if abs(a_[1] - b_[1]) < 0.01 and abs(a_[0] - b_[0]) > 0.5:
                for b in range(len(rtop) + 1):
                    if b < len(rtop) and abs(a_[1] - (rtop[b] - GAP / 2)) < 0.01:
                        off = CHAN[chan.get(b, 0) % len(CHAN)]
                        chan[b] = chan.get(b, 0) + 1
                        a_[1] += off
                        b_[1] += off
                        break
        chosen = [tuple(q) for q in chosen]
        e['pts'] = [(round(x, 1), round(y, 1)) for x, y in chosen]
        used[(s['id'], side_of(s, chosen[0]))] = True
    return warns


def frac(nd, pt):
    fx = (pt[0] - nd['x1']) / nd['w']
    fy = (pt[1] - nd['y1']) / nd['h']
    return round(min(1, max(0, fx)), 3), round(min(1, max(0, fy)), 3)


def path_len(pts):
    return sum(abs(pts[i + 1][0] - pts[i][0]) + abs(pts[i + 1][1] - pts[i][1]) for i in range(len(pts) - 1))


def label_at(e, nodes):
    pts = e['pts']
    total = path_len(pts)
    first = abs(pts[1][0] - pts[0][0]) + abs(pts[1][1] - pts[0][1])
    f = 0.5
    if nodes[e['s']]['kind'] == 'decision' and total > 0:
        f = min(0.5, (first / 2) / total)
    d = f * total
    for i in range(len(pts) - 1):
        seg = abs(pts[i + 1][0] - pts[i][0]) + abs(pts[i + 1][1] - pts[i][1])
        if d <= seg or i == len(pts) - 2:
            r = d / seg if seg else 0
            return f, (pts[i][0] + (pts[i + 1][0] - pts[i][0]) * r, pts[i][1] + (pts[i + 1][1] - pts[i][1]) * r)
        d -= seg


# ------------------------------------------------------------------ draw.io
def to_drawio(title, lanes, nodes, order, edges, L):
    c = ['<mxCell id="0"/>', '<mxCell id="1" parent="0"/>']
    for i, l in enumerate(lanes):
        c.append(f'<mxCell id="ln_{l["id"]}" value="{esc(l["name"])}" style="swimlane;horizontal=1;startSize={HDR};whiteSpace=wrap;html=1;fillColor=none;strokeColor=#000000;fontStyle=0;fontSize=12;" vertex="1" parent="1"><mxGeometry x="{L["lane_x"][i]}" y="0" width="{L["lane_w"][i]}" height="{L["H"]}" as="geometry"/></mxCell>')
    for nid in order:
        nd = nodes[nid]
        k = nd['kind']
        if k == 'start':
            st = 'ellipse;html=1;fillColor=#000000;strokeColor=#000000;'
        elif k == 'end':
            st = 'ellipse;shape=endState;html=1;fillColor=#000000;strokeColor=#000000;'
        elif k == 'action':
            st = f'rounded=1;arcSize=25;whiteSpace=wrap;html=1;fillColor={FILL};strokeColor=#000000;fontSize=12;'
        elif k == 'decision':
            st = f'rhombus;whiteSpace=wrap;html=1;fillColor={FILL};strokeColor=#000000;fontSize=12;'
        else:
            st = 'html=1;fillColor=#000000;strokeColor=#000000;'
        val = esc('\n'.join(nd['lines'])).replace('\n', '&lt;br&gt;')
        if nd['lane'] is None:
            par, ox = '1', 0
        else:
            li = L['lane_idx'][nd['lane']]
            par, ox = f'ln_{nd["lane"]}', L['lane_x'][li]
        c.append(f'<mxCell id="n_{nid}" value="{val}" style="{st}" vertex="1" parent="{par}"><mxGeometry x="{round(nd["x1"] - ox, 1)}" y="{round(nd["y1"], 1)}" width="{round(nd["w"], 1)}" height="{nd["h"]}" as="geometry"/></mxCell>')
    for k, e in enumerate(edges):
        s, t = nodes[e['s']], nodes[e['t']]
        p = e['pts']
        ex, ey = frac(s, p[0])
        nx, ny = frac(t, p[-1])
        st = (f'edgeStyle=none;html=1;endArrow=block;endFill=1;exitX={ex};exitY={ey};entryX={nx};entryY={ny};'
              'labelBackgroundColor=#ffffff;fontSize=11;')
        mid = ''.join(f'<mxPoint x="{x}" y="{y}"/>' for x, y in p[1:-1])
        arr = f'<Array as="points">{mid}</Array>' if mid else ''
        f, _ = label_at(e, nodes)
        lx = round(2 * f - 1, 3)
        val = esc(e['label']).replace('\n', '&lt;br&gt;')
        c.append(f'<mxCell id="ed_{k}" value="{val}" style="{st}" edge="1" parent="1" source="n_{e["s"]}" target="n_{e["t"]}"><mxGeometry x="{lx}" relative="1" as="geometry">{arr}</mxGeometry></mxCell>')
    W, H = L['W'], L['H']
    return ('<mxfile host="app.diagrams.net"><diagram name="%s" id="act1"><mxGraphModel dx="%d" dy="%d" grid="1" gridSize="10" guides="1" tooltips="1" connect="1" arrows="1" fold="1" page="1" pageScale="1" pageWidth="%d" pageHeight="%d" math="0" shadow="0"><root>%s</root></mxGraphModel></diagram></mxfile>'
            % (esc(title), W, H, W, H, ''.join(c)))


# ------------------------------------------------------------------ svg
def text_lines(lines, x, y, fs=12):
    lh = fs * 1.25
    y0 = y - (len(lines) - 1) * lh / 2
    return ''.join(f'<text x="{x}" y="{y0 + i * lh + fs * 0.35:.1f}" text-anchor="middle" font-size="{fs}">{esc(t)}</text>'
                   for i, t in enumerate(lines))


def to_svg(lanes, nodes, order, edges, L):
    W, H = L['W'], L['H']
    o = [f'<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}" font-family="DejaVu Sans, Arial, sans-serif" fill="#000"><rect width="100%" height="100%" fill="#fff"/>',
         '<defs><marker id="ar" viewBox="0 0 10 10" refX="10" refY="5" markerWidth="11" markerHeight="11" orient="auto"><path d="M0,0 L10,5 L0,10 z" fill="#000"/></marker></defs>']
    for i, l in enumerate(lanes):
        x, w = L['lane_x'][i], L['lane_w'][i]
        o.append(f'<rect x="{x}" y="0" width="{w}" height="{H}" fill="none" stroke="#000"/><line x1="{x}" y1="{HDR}" x2="{x + w}" y2="{HDR}" stroke="#000"/>')
        o.append(text_lines([l['name']], x + w / 2, HDR / 2))
    for e in edges:
        pts = ' '.join(f'{x},{y}' for x, y in e['pts'])
        o.append(f'<polyline points="{pts}" fill="none" stroke="#000" stroke-width="1.2" marker-end="url(#ar)"/>')
    for nid in order:
        nd = nodes[nid]
        k, cx, cy = nd['kind'], nd['cx'], nd['cy']
        if k == 'start':
            o.append(f'<circle cx="{cx}" cy="{cy}" r="12" fill="#000"/>')
        elif k == 'end':
            o.append(f'<circle cx="{cx}" cy="{cy}" r="13" fill="#fff" stroke="#000" stroke-width="1.5"/><circle cx="{cx}" cy="{cy}" r="8" fill="#000"/>')
        elif k == 'action':
            o.append(f'<rect x="{nd["x1"]}" y="{nd["y1"]}" width="{nd["w"]}" height="{nd["h"]}" rx="{nd["h"] * 0.25:.0f}" fill="{FILL}" stroke="#000"/>' + text_lines(nd['lines'], cx, cy))
        elif k == 'decision':
            o.append(f'<polygon points="{cx},{nd["y1"]} {nd["x2"]},{cy} {cx},{nd["y2"]} {nd["x1"]},{cy}" fill="{FILL}" stroke="#000"/>' + text_lines(nd['lines'], cx, cy))
        else:
            o.append(f'<rect x="{nd["x1"]}" y="{nd["y1"]}" width="{nd["w"]}" height="{nd["h"]}" fill="#000"/>')
    for e in edges:
        if not e['label']:
            continue
        ls = e['label'].split('\n')
        _, (lx, ly) = label_at(e, nodes)
        wd = max(len(t) for t in ls) * 6.4 + 8
        h = 13.5 * len(ls) + 2
        o.append(f'<rect x="{lx - wd / 2:.1f}" y="{ly - h / 2:.1f}" width="{wd:.1f}" height="{h}" fill="#fff"/>')
        o.append(''.join(f'<text x="{lx:.1f}" y="{ly - h / 2 + 11.5 + i * 13.5:.1f}" text-anchor="middle" font-size="11">{esc(t)}</text>' for i, t in enumerate(ls)))
    o.append('</svg>')
    return ''.join(o)


# ------------------------------------------------------------------ main
def build(text):
    title, lanes, nodes, order, edges = parse(text)
    L = layout(lanes, nodes, order, edges)
    warns = route_all(nodes, order, edges, L)
    return title, lanes, nodes, order, edges, L, warns


def main():
    ap = argparse.ArgumentParser(description='Sinh activity diagram (.drawio/.svg/.png) tu file .act')
    ap.add_argument('spec')
    ap.add_argument('-o', '--out', help='thu muc xuat (mac dinh: cung thu muc voi file .act)')
    ap.add_argument('--scale', type=float, default=1.5, help='do phan giai PNG (mac dinh 1.5)')
    ap.add_argument('--no-png', action='store_true', help='chi ra .drawio va .svg')
    a = ap.parse_args()
    try:
        title, lanes, nodes, order, edges, L, warns = build(open(a.spec, encoding='utf8').read())
    except SpecError as e:
        sys.exit(f'LOI: {e}')
    out = a.out or os.path.dirname(os.path.abspath(a.spec))
    os.makedirs(out, exist_ok=True)
    stem = os.path.join(out, os.path.splitext(os.path.basename(a.spec))[0])
    open(stem + '.drawio', 'w', encoding='utf8').write(to_drawio(title, lanes, nodes, order, edges, L))
    open(stem + '.svg', 'w', encoding='utf8').write(to_svg(lanes, nodes, order, edges, L))
    print(f'da ghi {stem}.drawio, {stem}.svg  ({L["W"]:.0f}x{L["H"]:.0f}px, {len(nodes)} node, {len(edges)} canh)')
    for w in warns:
        print('CANH BAO:', w)
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
        pg = b.new_page(viewport={'width': int(L['W']), 'height': int(L['H'])}, device_scale_factor=a.scale)
        pg.goto('file://' + os.path.abspath(stem + '.svg'))
        pg.screenshot(path=stem + '.png')
        b.close()
    print(f'da ghi {stem}.png')


if __name__ == '__main__':
    main()
