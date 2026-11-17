"""Re-cut the Mirage demo video (v3): longer holds + red circle/arrow annotations.

usage: python annotate_video.py SRC.mp4 OUT.mp4 [--stills DIR] [--cues CUES.md]

SRC is the v1 recording ("Mirage demo video.mp4", 1:41, 1920x1080, 30 fps).
Needs Pillow and ffmpeg (set FFMPEG=path\to\ffmpeg.exe if it is not on PATH).

The v1 recording is mostly still holds between steps, so the new cut is a list
of PLAY (source range at 1x) and HOLD (one source frame for N seconds) segments.
Before each click the frame is held with the control circled; after each step
the results are circled one after another, in the order of the voice-over cue
sheet in docs/DEMO-VIDEO-PROGRESS.md. All coordinates are in the 1920x1080
source frame, so this edit list only fits that recording.

--stills DIR  writes one still per annotation (fully drawn) instead of a video,
              for checking placement.
--cues FILE   writes the voice-over cue sheet (video time of each annotation).
"""
import math
import subprocess
import sys

from PIL import Image, ImageDraw, ImageFont

W, H, FPS = 1920, 1080, 30
RED = (230, 32, 38)
WHITE = (255, 255, 255)
FONTS = ["/usr/share/fonts/opentype/inter/Inter-Bold.otf",          # Linux (Inter)
         "C:/Windows/Fonts/segoeuib.ttf", "C:/Windows/Fonts/arialbd.ttf",  # Windows
         "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", "DejaVuSans-Bold.ttf"]
FFMPEG = __import__("os").environ.get("FFMPEG", "ffmpeg")
SS = 2  # supersampling for antialiased strokes

# --------------------------------------------------------------- the edit list
# Shapes: ("ell", cx, cy, w, h) ellipse, ("rect", x0, y0, x1, y1) rounded rect.
# An annotation: dict(t0, t1, shapes, label, at=(x, y) label centre, to=index
# of the shape the arrow points at, cue=voice-over text for the cue sheet).

LAUNCH = ("ell", 452, 258, 150, 58)
CAMPAIGN = ("ell", 192, 144, 210, 50)
TARGET = ("ell", 163, 102, 178, 44)
B_TARPIT = ("ell", 880, 568, 120, 50)
B_FAKE = ("ell", 1003, 568, 178, 50)
B_BLOCK = ("ell", 1128, 568, 112, 50)
B_COUNTER = ("ell", 1252, 568, 186, 50)
B_SIMUL = ("ell", 1434, 568, 232, 50)
B_VERIFY = ("ell", 1830, 990, 170, 54)
POP_HEAD = ("ell", 1366, 110, 700, 84)


def hold(src, dur, *annots):
    return ("hold", src, dur, list(annots))


def play(a, b):
    return ("play", a, b, [])


def A(t0, t1, shapes, label, at, to=None, cue=None):
    return dict(t0=t0, t1=t1, shapes=shapes, label=label, at=at,
                to=len(shapes) - 1 if to is None else to, cue=cue or label.replace("\n", " "))


EDIT = [
    play(0.0, 7.2),
    hold(7.2, 8.0,
         A(0.2, 2.8, [("rect", 6, 30, 804, 1040), ("ell", 190, 52, 340, 40)],
           "ATTACKER\nSpecter (red team)", (400, 700),
           cue="Left: Specter, the built-in attacker (red team)"),
         A(2.9, 5.4, [("rect", 816, 30, 1914, 1040), ("ell", 935, 52, 210, 40)],
           "DEFENDER\nMirage Console (blue team)", (1090, 640),
           cue="Right: the Mirage Console that watches the decoys and responds"),
         A(5.5, 8.6, [CAMPAIGN, LAUNCH], "Step 1: Recon scan → Launch", (420, 640),
           cue="Step 1: Specter launches a recon scan")),
    play(7.2, 9.2),
    hold(9.2, 11.0,
         A(0.3, 3.8, [("pill", 8, 652, 748, 708)], "Scan finds 3 open decoy ports", (400, 880)),
         A(3.9, 7.4, [POP_HEAD], "Alert: PROBE DETECTED\nport scan · T1046 · MEDIUM", (1366, 800),
           cue="Popup PROBE DETECTED (T1046): any touch on a decoy is high-confidence"),
         A(7.6, 11.4, [CAMPAIGN, LAUNCH], "Step 2: Brute force → Launch", (420, 880),
           cue="Step 2: Specter brute-forces the decoy login")),
    play(19.3, 19.6),
    hold(19.6, 10.0,
         A(0.3, 3.3, [("pill", 8, 788, 638, 844)], "Every password rejected", (400, 960)),
         A(3.4, 6.6, [POP_HEAD], "Alert: UNDER ATTACK\nbrute force · T1110 · HIGH", (1366, 800),
           cue="Popup UNDER ATTACK (T1110): one alert per finding, into the signed ledger"),
         A(6.8, 10.4, [CAMPAIGN, LAUNCH], "Step 3: Honeytoken replay → Launch", (440, 960),
           cue="Step 3: Specter replays a stolen credential")),
    play(26.5, 28.2),
    hold(28.2, 12.0,
         A(0.3, 3.0, [("pill", 226, 512, 730, 548)], "Replays a stolen credential…", (400, 880)),
         A(3.1, 6.0, [("pill", 8, 696, 680, 752)], "…which was planted bait (a honeytoken)", (420, 880),
           cue="ACCESS GRANTED, but the credential was bait and the admin page is fake"),
         A(6.1, 9.0, [POP_HEAD], "COMPROMISED · T1078 · CRITICAL\nwaiting for the operator", (1366, 800),
           cue="Popup COMPROMISED (T1078); auto-response is off, the operator decides"),
         A(9.2, 12.5, [B_TARPIT], "Step 4: operator clicks Tarpit", (880, 800),
           cue="Step 4: operator presses Tarpit")),
    play(37.4, 39.2),
    hold(39.2, 12.0,
         A(0.3, 3.2, [("rect", 822, 698, 1390, 812)], "ScopeGuard checks the target first", (1366, 1000),
           cue="Op shell: ScopeGuard.require, in range, dispatching"),
         A(3.3, 6.0, [("pill", 820, 884, 1694, 928)], "Sensor confirms: TARPIT applied", (1500, 1012)),
         A(6.1, 8.8, [("pill", 8, 606, 710, 662)], "The attacker's replay is stuck on the line", (420, 860),
           cue="Specter: CONNECTION STALLED — the attacker is held on the line"),
         A(9.0, 12.4, [B_FAKE], "Step 5: operator clicks Feed fake data", (1003, 800),
           cue="Step 5: operator presses Feed fake data")),
    play(45.3, 46.15),
    hold(46.15, 4.5,
         A(0.2, 2.2, [("pill", 818, 880, 1562, 936)], "Fake data armed on the Sensor", (1366, 1000),
           cue="Op shell: FEED FAKE DATA DISPATCHED, sensor confirms"),
         A(2.3, 4.9, [CAMPAIGN, LAUNCH], "Brute force again → Launch", (440, 880))),
    play(46.15, 47.3),
    hold(47.3, 9.0,
         A(0.3, 3.0, [("pill", 8, 720, 682, 776)], "The attacker \"gets in\"…", (420, 900),
           cue="Brute force now 'gets in' — ACCESS GRANTED"),
         A(3.1, 6.2, [("rect", 236, 606, 510, 680)], "…to fake files: backup.cfg holds\nanother honeytoken",
           (460, 900), cue="…to fake files; the fake backup.cfg holds another honeytoken"),
         A(6.4, 9.4, [B_BLOCK], "Step 6: operator clicks Block", (1128, 800),
           cue="Step 6: operator presses Block")),
    play(59.3, 60.0),
    hold(60.0, 6.0,
         A(0.3, 3.0, [POP_HEAD], "NEUTRALISED", (1366, 800),
           cue="Popup turns NEUTRALISED: Sensor confirmed the block"),
         A(3.1, 6.0, [("pill", 818, 880, 1504, 936)], "Sensor confirms: BLOCK applied", (1366, 1000))),
    play(60.0, 61.0),
    hold(61.0, 2.5,
         A(0.1, 2.9, [CAMPAIGN, LAUNCH], "Replay the credential again", (440, 900),
           cue="Specter tries the stolen credential again")),
    play(61.0, 61.5),
    hold(61.5, 6.0,
         A(0.3, 3.0, [("pill", 8, 606, 710, 662)], "Connection reset: the source is cut off", (420, 860),
           cue="CONNECTION BLOCKED — the source is cut off"),
         A(3.1, 6.4, [B_COUNTER], "Step 7: operator clicks Counter-attack\n(lab range only)", (1252, 800),
           cue="Step 7: operator presses Counter-attack (lab range only, through ScopeGuard)")),
    play(69.0, 70.0),
    hold(70.0, 9.0,
         A(0.3, 3.0, [("pill", 8, 584, 736, 640)], "The attacker's laptop sees the counter", (420, 880),
           cue="Counter-watch: INCOMING COUNTER-ATTACK"),
         A(3.1, 6.0, [("pill", 820, 786, 1726, 818)], "Their own technique, mirrored back", (1366, 1000),
           cue="The mirror counter replays the attacker's own technique back"),
         A(6.1, 9.4, [B_SIMUL], "Operator clicks Simultaneous counter", (1434, 800),
           cue="Simultaneous counter: several techniques at once")),
    play(75.2, 76.5),
    hold(76.5, 7.0,
         A(0.3, 3.5, [("rect", 822, 696, 1900, 1000)], "One pane per thread: 4 methods at once", (1366, 640),
           cue="One pane per thread — a bounded set, not a flood"),
         A(3.6, 7.0, [("rect", 12, 678, 792, 776)], "Each arrives as its own connection", (420, 900))),
    play(84.7, 85.5),
    hold(85.5, 11.0,
         A(0.3, 2.8, [("ell", 934, 140, 240, 90)], "Source 127.0.0.1: CRITICAL, 3 alerts", (1090, 520),
           cue="Dashboard: the network map marks the source CRITICAL"),
         A(2.9, 5.6, [("rect", 820, 289, 1362, 356)], "3 alerts, one per technique", (1090, 560),
           cue="Alert feed: three alerts, one per ATT&CK technique"),
         A(5.7, 8.5, [("rect", 1378, 284, 1904, 588)], "Attacker dossier", (1640, 760),
           cue="Attacker dossier: services probed, passwords tried, honeytokens used"),
         A(8.6, 11.4, [B_VERIFY], "Step 8: Verify ledger", (1640, 800),
           cue="Step 8: Verify ledger")),
    play(88.0, 88.6),
    hold(88.6, 7.0,
         A(0.3, 3.8, [("ell", 1142, 1016, 190, 44)], "Hash-chained + signed ledger: verified", (1100, 800),
           cue="Ledger: verified — every alert hash-chained and ECDSA-signed; tampering shows as BROKEN"),
         A(4.0, 7.4, [TARGET], "Step 9: type a target outside\nthe lab range: 8.8.8.8", (440, 900),
           cue="Step 9: Specter is pointed at 8.8.8.8, outside the lab range")),
    play(93.3, 94.3),
    hold(94.3, 2.5,
         A(0.1, 2.9, [TARGET, LAUNCH], "8.8.8.8 → Launch", (440, 900))),
    play(94.4, 95.3),
    hold(95.3, 10.0,
         A(0.3, 4.5, [("pill", 8, 560, 772, 616)], "REFUSED: outside the lab range", (420, 820),
           cue="REFUSED — OUTSIDE LAB RANGE"),
         A(4.6, 9.0, [("rect", 12, 488, 792, 548)], "ScopeGuard: nothing leaves the lab range", (420, 820),
           cue="ScopeGuard is the safety boundary: every response goes through it")),
]

# --------------------------------------------------------------- timeline


def build_timeline():
    """Return (src_frame_for_each_out_frame, [(start_frame, end_frame, annot)])."""
    src, annots = [], []
    t = 0
    for kind, a, b, ann in EDIT:
        if kind == "play":
            n = round((b - a) * FPS)
            s0 = round(a * FPS)
            src.extend(range(s0, s0 + n))
        else:
            n = round(b * FPS)
            for an in ann:
                annots.append((t + round(an["t0"] * FPS), t + round(an["t1"] * FPS), an))
            src.extend([round(a * FPS)] * n)
        t += n
    return src, annots


# --------------------------------------------------------------- drawing

_font_cache = {}


def font(px):
    if px not in _font_cache:
        for path in FONTS:
            try:
                _font_cache[px] = ImageFont.truetype(path, px)
                break
            except OSError:
                continue
        else:
            raise SystemExit("no bold font found; add one to FONTS")
    return _font_cache[px]


def ease(x):
    x = max(0.0, min(1.0, x))
    return 1 - (1 - x) ** 3


def shape_bbox(s):
    if s[0] == "ell":
        _, cx, cy, w, h = s
        return (cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2)
    return s[1:]


def outline(s, n=240):
    """Closed outline of a shape, starting at top-centre, clockwise."""
    x0, y0, x1, y1 = shape_bbox(s)
    cx, cy, rx, ry = (x0 + x1) / 2, (y0 + y1) / 2, (x1 - x0) / 2, (y1 - y0) / 2
    if s[0] == "ell":
        return [(cx + rx * math.sin(2 * math.pi * i / n), cy - ry * math.cos(2 * math.pi * i / n))
                for i in range(n + 1)]
    r = ry if s[0] == "pill" else min(14, rx, ry)
    pts = [(cx, y0)]
    corners = [((x1 - r, y0 + r), -90), ((x1 - r, y1 - r), 0), ((x0 + r, y1 - r), 90), ((x0 + r, y0 + r), 180)]
    for (ccx, ccy), a0 in corners:
        for k in range(0, 13):
            a = math.radians(a0 + 90 * k / 12)
            pts.append((ccx + r * math.cos(a), ccy + r * math.sin(a)))
    pts.append((cx, y0))
    # resample evenly so the sweep runs at constant speed
    seg = [math.dist(pts[i], pts[i + 1]) for i in range(len(pts) - 1)]
    total = sum(seg)
    out, acc, j = [], 0.0, 0
    for i in range(n + 1):
        want = total * i / n
        while j < len(seg) - 1 and acc + seg[j] < want:
            acc += seg[j]
            j += 1
        t = 0 if seg[j] == 0 else (want - acc) / seg[j]
        out.append((pts[j][0] + (pts[j + 1][0] - pts[j][0]) * t, pts[j][1] + (pts[j + 1][1] - pts[j][1]) * t))
    return out


def edge_point(s, toward):
    """Point on the shape's outline in the direction of `toward`."""
    x0, y0, x1, y1 = shape_bbox(s)
    cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
    dx, dy = toward[0] - cx, toward[1] - cy
    if dx == 0 and dy == 0:
        return cx, cy
    rx, ry = (x1 - x0) / 2, (y1 - y0) / 2
    if s[0] == "ell":
        k = 1 / math.sqrt((dx / rx) ** 2 + (dy / ry) ** 2)
    else:
        k = min(rx / abs(dx) if dx else 1e9, ry / abs(dy) if dy else 1e9)
    return cx + dx * k, cy + dy * k


def label_box(text, at):
    f = font(30)
    lines = text.split("\n")
    d = ImageDraw.Draw(Image.new("L", (1, 1)))
    ws = [d.textlength(l, font=f) for l in lines]
    lh = 38
    w = max(ws) + 40
    h = lh * len(lines) + 20
    x0 = min(max(at[0] - w / 2, 8), W - 8 - w)
    y0 = min(max(at[1] - h / 2, 8), H - 8 - h)
    return (x0, y0, x0 + w, y0 + h), lines, ws, lh


def label_edge(box, toward):
    x0, y0, x1, y1 = box
    cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
    dx, dy = toward[0] - cx, toward[1] - cy
    k = min((x1 - x0) / 2 / abs(dx) if dx else 1e9, (y1 - y0) / 2 / abs(dy) if dy else 1e9)
    return cx + dx * k, cy + dy * k


def draw_overlay(an, p_circle, p_arrow, alpha):
    """Return (RGBA image, (x, y)) for one annotation at the given progress."""
    lb, lines, ws, lh = label_box(an["label"], an["at"])
    target = an["shapes"][an["to"]]
    # overlay covers the whole frame (simple, composited by bbox below)
    xs, ys = [lb[0], lb[2]], [lb[1], lb[3]]
    for s in an["shapes"]:
        b = shape_bbox(s)
        xs += [b[0], b[2]]
        ys += [b[1], b[3]]
    pad = 24
    ox, oy = int(max(min(xs) - pad, 0)), int(max(min(ys) - pad, 0))
    ex, ey = int(min(max(xs) + pad, W)), int(min(max(ys) + pad, H))
    cw, ch = ex - ox, ey - oy
    img = Image.new("RGBA", (cw * SS, ch * SS), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)

    def P(x, y):
        return ((x - ox) * SS, (y - oy) * SS)

    def B(b):
        return [*P(b[0], b[1]), *P(b[2], b[3])]

    # shapes: white halo then red stroke, swept in clockwise from the top
    for i, s in enumerate(an["shapes"]):
        pc = ease(p_circle * 1.3 - i * 0.3)
        if pc <= 0:
            continue
        pts = outline(s)
        pts = [P(*q) for q in pts[:max(2, int(len(pts) * pc) + 1)]]
        d.line(pts, fill=WHITE + (235,), width=11 * SS, joint="curve")
        d.line(pts, fill=RED + (255,), width=6 * SS, joint="curve")
        for q, col, rad in ((pts[0], WHITE + (235,), 5.5), (pts[-1], WHITE + (235,), 5.5),
                            (pts[0], RED + (255,), 3), (pts[-1], RED + (255,), 3)):
            d.ellipse([q[0] - rad * SS, q[1] - rad * SS, q[0] + rad * SS, q[1] + rad * SS], fill=col)

    # arrow from the label to the target outline
    tgt_c = ((shape_bbox(target)[0] + shape_bbox(target)[2]) / 2,
             (shape_bbox(target)[1] + shape_bbox(target)[3]) / 2)
    lbc = ((lb[0] + lb[2]) / 2, (lb[1] + lb[3]) / 2)
    head = edge_point(target, lbc)
    tail = label_edge(lb, tgt_c)
    dx, dy = head[0] - tail[0], head[1] - tail[1]
    dist = math.hypot(dx, dy)
    pa = ease(p_arrow)
    if dist > 30 and pa > 0:
        ux, uy = dx / dist, dy / dist
        head = (head[0] - ux * 8, head[1] - uy * 8)  # small gap before the outline
        tail = (tail[0] + ux * 6, tail[1] + uy * 6)
        dist = math.hypot(head[0] - tail[0], head[1] - tail[1])
        tip = (tail[0] + ux * dist * pa, tail[1] + uy * dist * pa)
        hl = 30
        base = (tip[0] - ux * hl, tip[1] - uy * hl)
        nx, ny = -uy, ux
        tri = [tip, (base[0] + nx * 15, base[1] + ny * 15), (base[0] - nx * 15, base[1] - ny * 15)]
        shaft_end = (tip[0] - ux * hl * 0.8, tip[1] - uy * hl * 0.8)
        for col, wd, grow in ((WHITE + (235,), 11, 3), (RED + (255,), 6, 0)):
            d.line([P(*tail), P(*shaft_end)], fill=col, width=wd * SS)
            t2 = [(tip[0] + ux * grow, tip[1] + uy * grow),
                  (base[0] + nx * (15 + grow) - ux * grow, base[1] + ny * (15 + grow) - uy * grow),
                  (base[0] - nx * (15 + grow) - ux * grow, base[1] - ny * (15 + grow) - uy * grow)]
            d.polygon([P(*q) for q in (t2 if grow else tri)], fill=col)

    # label pill
    la = ease(p_arrow * 1.6)
    if la > 0:
        a = int(255 * la)
        d.rounded_rectangle(B((lb[0] - 3, lb[1] - 3, lb[2] + 3, lb[3] + 3)), radius=16 * SS,
                            fill=WHITE + (int(a * 0.95),))
        d.rounded_rectangle(B(lb), radius=13 * SS, fill=RED + (a,))
        f2 = font(30 * SS)
        for i, (l, w) in enumerate(zip(lines, ws)):
            x = (lb[0] + lb[2]) / 2 - w / 2
            y = lb[1] + 10 + i * lh
            d.text(P(x, y), l, font=f2, fill=WHITE + (a,))

    img = img.resize((cw, ch), Image.LANCZOS)
    if alpha < 1:
        r, g, b, al = img.split()
        al = al.point(lambda v: int(v * alpha))
        img = Image.merge("RGBA", (r, g, b, al))
    return img, (ox, oy)


_ov_cache = {}


def overlay_for(idx, an, f, f0, f1):
    """Overlay for annotation `an` (index idx) at out frame f within [f0, f1)."""
    t = (f - f0) / FPS
    tout = (f1 - f) / FPS
    pc = min(1.0, t / 0.45)
    pa = min(1.0, max(0.0, (t - 0.3) / 0.35))
    alpha = min(1.0, tout / 0.25)
    key = (idx, round(pc * 30), round(pa * 30), round(alpha * 10))
    if key not in _ov_cache:
        _ov_cache[key] = draw_overlay(an, key[1] / 30, key[2] / 30, key[3] / 10)
    return _ov_cache[key]


# --------------------------------------------------------------- render


def fmt(sec):
    return f"{int(sec // 60)}:{sec % 60:04.1f}"


def main():
    srcpath, outpath = sys.argv[1], sys.argv[2]
    stills = cues = None
    if "--stills" in sys.argv:
        stills = sys.argv[sys.argv.index("--stills") + 1]
    if "--cues" in sys.argv:
        cues = sys.argv[sys.argv.index("--cues") + 1]
    srcmap, annots = build_timeline()
    total = len(srcmap)
    print(f"output: {total} frames = {fmt(total / FPS)}", flush=True)

    if cues:
        with open(cues, "w") as fh:
            fh.write("| Time | Annotation on screen | Talking point (from the cue sheet) |\n| --- | --- | --- |\n")
            for f0, f1, an in annots:
                fh.write(f"| {fmt(f0 / FPS)} | {an['label'].replace(chr(10), ' ')} | {an['cue']} |\n")
            fh.write(f"| {fmt(total / FPS)} | End | |\n")

    if stills:
        # one still per annotation, fully drawn, for checking placement
        need = {}
        for i, (f0, f1, an) in enumerate(annots):
            need.setdefault(srcmap[min(f0 + 25, total - 1)], []).append((i, an))
    dec = subprocess.Popen([FFMPEG, "-v", "error", "-i", srcpath, "-f", "rawvideo", "-pix_fmt", "rgb24", "-"],
                           stdout=subprocess.PIPE)
    enc = None
    if not stills:
        enc = subprocess.Popen([FFMPEG, "-v", "error", "-y",
                                "-f", "rawvideo", "-pix_fmt", "rgb24", "-s", f"{W}x{H}", "-r", str(FPS), "-i", "-",
                                "-f", "lavfi", "-i", "anullsrc=r=48000:cl=stereo",
                                "-map", "0:v", "-map", "1:a", "-shortest",
                                "-vf", f"fade=t=out:st={total / FPS - 0.8:.2f}:d=0.8",
                                "-c:v", "libx264", "-preset", "medium", "-crf", "18", "-pix_fmt", "yuv420p",
                                "-c:a", "aac", "-b:a", "128k", "-movflags", "+faststart", outpath],
                               stdin=subprocess.PIPE)
    fsize = W * H * 3
    cur_idx, cur = -1, None
    for f in range(total):
        want = srcmap[f]
        while cur_idx < want:
            buf = dec.stdout.read(fsize)
            if len(buf) < fsize:
                break
            cur, cur_idx = buf, cur_idx + 1
        if stills:
            if f == 0 or srcmap[f] != srcmap[f - 1]:
                pass
            for i, an in need.get(cur_idx, []) if (f == 0 or srcmap[f - 1] != cur_idx) else []:
                base = Image.frombuffer("RGB", (W, H), cur).convert("RGBA")
                ov, pos = draw_overlay(an, 1, 1, 1)
                base.alpha_composite(ov, pos)
                base.convert("RGB").save(f"{stills}/a{i:02d}.png")
            continue
        active = [(i, a) for i, a in enumerate(annots) if a[0] <= f < a[1]]
        if not active:
            enc.stdin.write(cur)
            continue
        base = Image.frombuffer("RGB", (W, H), cur).convert("RGBA")
        for i, (f0, f1, an) in active:
            ov, pos = overlay_for(i, an, f, f0, f1)
            base.alpha_composite(ov, pos)
        enc.stdin.write(base.convert("RGB").tobytes())
        if f % 300 == 0:
            print(f"  {fmt(f / FPS)}", flush=True)
    dec.stdout.close()
    dec.kill()
    if enc:
        enc.stdin.close()
        enc.wait()


if __name__ == "__main__":
    main()
