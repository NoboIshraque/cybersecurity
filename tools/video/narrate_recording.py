"""Lay a real voice recording over the annotated demo video (v3), stretching the video to fit.

usage: python narrate_recording.py RECORDING SOURCE_V1.mp4 OUT.mp4 [--work DIR]

RECORDING is the narrator reading docs/submission/Mirage_Voiceover_Script.md (11 parts, in the
order of the demo). The recording is cut into those parts at the quietest point of the pause
between them; each part plays over its own step of the video. Where a part is longer than its
step, that step's still holds are lengthened (the clicks and transitions keep their real speed)
and the annotations stretch with them; a short hold on the last frame carries the closing line.

Needs: numpy, soundfile, Pillow, ffmpeg. Calls annotate_video.py with --stretch for the picture.
"""
import importlib.util
import json
import os
import subprocess
import sys

import numpy as np
import soundfile as sf

HERE = os.path.dirname(os.path.abspath(__file__))
FFMPEG = os.environ.get("FFMPEG", "ffmpeg")
SR = 48000

# Where each part of the recording starts (seconds, nominal; refined to the quietest point nearby),
# and the first annotation (0 = the title, 1.. = annotation index + 1) of the step it belongs to.
PARTS = [  # (cut_at, first_cue, what)
    (0.0, 0, "title: three object-oriented Java applications"),
    (7.6, 3, "recon scan: Decoy, inheritance, Template Method"),
    (20.35, 6, "brute force: DetectionRule, Strategy, polymorphism"),
    (34.2, 9, "honeytoken: fake page, JDBC into H2"),
    (47.6, 13, "tarpit: Command object on its own thread"),
    (62.7, 17, "fake data: one more Command, overriding"),
    (82.1, 22, "block: TLS socket, ACK, Observer"),
    (101.4, 27, "counter-attack: ScopeGuard, thread pool"),
    (127.6, 33, "dashboard: Swing, JTable; ledger: SHA-256, ECDSA"),
    (153.8, 38, "refused target; producer-consumer"),
    (178.1, None, "closing: Swing, JDBC, sockets, threads, patterns"),
]
LEAD = 0.4   # start speaking this long after the step's first circle starts
TAIL = 0.5   # and leave this much quiet before the next step


def load_annotate():
    spec = importlib.util.spec_from_file_location("annotate_video", os.path.join(HERE, "annotate_video.py"))
    m = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(m)
    return m


def quietest(audio, t, span=0.45):
    """The quietest 20 ms point within +-span of t."""
    if t <= 0:
        return 0.0
    a0, a1 = int((t - span) * SR), int((t + span) * SR)
    seg = audio[max(a0, 0):a1]
    hop = int(0.02 * SR)
    rms = [np.sqrt(np.mean(seg[i:i + hop] ** 2)) for i in range(0, len(seg) - hop, hop)]
    return (max(a0, 0) + int(np.argmin(rms)) * hop + hop // 2) / SR


def main():
    rec, src_video, out = sys.argv[1:4]
    work = sys.argv[sys.argv.index("--work") + 1] if "--work" in sys.argv else os.path.dirname(os.path.abspath(out))
    os.makedirs(work, exist_ok=True)
    av = load_annotate()

    # clean the recording: remove rumble, light denoise; 48 kHz mono
    clean = os.path.join(work, "recording_clean.wav")
    subprocess.run([FFMPEG, "-v", "error", "-y", "-i", rec, "-ac", "1", "-ar", str(SR),
                    "-af", "highpass=f=70,afftdn=nf=-28", clean], check=True)
    audio, _ = sf.read(clean, dtype="float32")
    total_rec = len(audio) / SR

    cuts = [quietest(audio, p[0]) for p in PARTS] + [total_rec]
    parts = [audio[int(cuts[i] * SR):int(cuts[i + 1] * SR)] for i in range(len(PARTS))]

    # base (unstretched) timeline: where each step starts, and its play/hold make-up
    base_src, annots = av.build_timeline()
    base_total = len(base_src) / av.FPS
    cue_start = [0.0] + [f0 / av.FPS for f0, f1, a in annots]
    play_spans = []
    t = 0.0
    for kind, a, b, ann in av.EDIT:
        n = (b - a) if kind == "play" else b
        if kind == "play":
            play_spans.append((t, t + n))
        t += n

    sections, report = [], []
    for k, (cut, cue, what) in enumerate(PARTS[:-1]):
        s0 = cue_start[cue]
        nxt = PARTS[k + 1][1]
        s1 = cue_start[nxt] if nxt is not None else base_total
        play = sum(max(0.0, min(e, s1) - max(b, s0)) for b, e in play_spans)
        hold = (s1 - s0) - play
        need = LEAD + len(parts[k]) / SR + TAIL
        fac = max(1.0, (need - play) / hold) if hold > 0 else 1.0
        sections.append((round(s0, 3), round(s1, 3), round(fac, 4)))
        report.append((k, what, round(len(parts[k]) / SR, 1), round(s1 - s0, 1), round(fac, 2)))
    closing = len(parts[-1]) / SR
    tail = round(LEAD + closing + 1.6, 2)
    cfg = os.path.join(work, "stretch.json")
    json.dump({"sections": sections, "tail": tail}, open(cfg, "w"), indent=1)

    # the picture
    silent = os.path.join(work, "video_stretched.mp4")
    cues = os.path.join(work, "cues_narrated.md")
    subprocess.run([sys.executable, os.path.join(HERE, "annotate_video.py"), src_video, silent,
                    "--stretch", cfg, "--cues", cues], check=True)

    # the sound: each part at its step's new start time
    new_total = av.warp_time(sections, base_total) + tail
    track = np.zeros(int((new_total + 1) * SR), dtype=np.float32)
    starts_at = []
    for k, part in enumerate(parts):
        cue = PARTS[k][1]
        if k == 0:
            at = 0.6                                    # over the title card
        elif cue is None:
            at = av.warp_time(sections, base_total) + LEAD   # closing line, over the held last frame
        else:
            at = av.warp_time(sections, cue_start[cue]) + LEAD
        starts_at.append(round(at, 1))
        s = int(at * SR)
        track[s:s + len(part)] += part[: max(0, len(track) - s)]
    wav = os.path.join(work, "narration_track.wav")
    sf.write(wav, track, SR)

    subprocess.run([FFMPEG, "-v", "error", "-y", "-i", silent, "-i", wav, "-map", "0:v", "-map", "1:a",
                    "-c:v", "copy", "-af", "loudnorm=I=-16:TP=-1.5:LRA=11,aresample=48000",
                    "-c:a", "aac", "-b:a", "160k", "-ac", "2", "-shortest", "-movflags", "+faststart", out],
                    check=True)
    print("cuts:", [round(c, 2) for c in cuts])
    for r in report:
        print("part %d %-50s speech %5.1fs  step %5.1fs  stretch x%.2f  starts %5.1fs"
              % (r + (starts_at[r[0]],)))
    print("closing %.1fs at %.1fs; video %.1fs" % (closing, starts_at[-1], new_total))


if __name__ == "__main__":
    main()
