"""Voice-over for the v3 demo video: Kokoro TTS (voice af_heart), one line per annotation.

usage: python voiceover.py REPO_DIR KOKORO_DIR OUT.wav

KOKORO_DIR holds model.onnx (onnx-community/Kokoro-82M-v1.0-ONNX on Hugging Face) and
voices-v1.0.bin (an npz of voices/*.bin from the same repo, each reshaped to 510x1x256).
Needs: pip install kokoro-onnx soundfile pillow. Lines come from narration_v3.txt; each starts
0.25 s after its annotation starts and is sped up (max 1.25x) only if it would run into the next.
Mux: ffmpeg -i annotated.mp4 -i OUT.wav -map 0:v -map 1:a -c:v copy
     -af "loudnorm=I=-16:TP=-1.5:LRA=11" -c:a aac -b:a 160k -shortest out.mp4
"""
import importlib.util, json, sys
import numpy as np, soundfile as sf
from kokoro_onnx import Kokoro

REPO, KDIR, OUT = sys.argv[1], sys.argv[2], sys.argv[3]
spec = importlib.util.spec_from_file_location('a', f'{REPO}/tools/video/annotate_video.py')
m = importlib.util.module_from_spec(spec); spec.loader.exec_module(m)
src, ann = m.build_timeline()
TOTAL = len(src) / 30
starts = [0.0] + [f0 / 30 for f0, f1, a in ann]
lines = [l.strip() for l in open(f'{REPO}/tools/video/narration_v3.txt', encoding='utf-8') if l.strip()]
assert len(lines) == len(starts), (len(lines), len(starts))
SAY = {"ScopeGuard": "Scope Guard"}  # pronunciation fixes (spoken only)
k = Kokoro(f'{KDIR}/model.onnx', f'{KDIR}/voices-v1.0.bin')
SR = 24000
track = np.zeros(int((TOTAL + 1) * SR), dtype=np.float32)
LEAD = 0.25          # speak just after the circle starts to draw
report = []
for i, (t0, line) in enumerate(zip(starts, lines)):
    t_end = starts[i + 1] if i + 1 < len(starts) else TOTAL - 0.9
    window = t_end - t0 - LEAD - 0.15
    spoken = line
    for a, b in SAY.items():
        spoken = spoken.replace(a, b)
    speed = 1.0
    while True:
        audio, sr = k.create(spoken, voice='af_heart', speed=speed, lang='en-us')
        # trim leading/trailing near-silence
        nz = np.nonzero(np.abs(audio) > 0.01)[0]
        audio = audio[max(nz[0] - 240, 0): nz[-1] + 1200] if len(nz) else audio
        dur = len(audio) / sr
        if dur <= window or speed >= 1.25:
            break
        speed = round(speed + 0.05, 2)
    s = int((t0 + LEAD) * SR)
    track[s:s + len(audio)] += audio
    report.append(dict(i=i, t=round(t0, 1), window=round(window, 2), dur=round(dur, 2), speed=speed,
                       fits=dur <= window, line=line))
peak = np.abs(track).max()
track = track / peak * 0.89
sf.write(OUT, track[: int(TOTAL * SR)], SR)
json.dump(report, open(OUT + '.json', 'w'), indent=1)
bad = [r for r in report if not r['fits'] or r['speed'] > 1.0]
print('lines', len(report), 'speeded/overflow:', [(r['i'], r['speed'], r['dur'], r['window']) for r in bad])
print('speech seconds', round(sum(r['dur'] for r in report), 1), 'of', round(TOTAL, 1))
