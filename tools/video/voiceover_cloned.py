"""Voice-over for the v3 video in Nobo's voice: XTTS-v2 fine-tuned on his recording.

usage: python voiceover_cloned.py REPO_DIR XTTS_DIR CHECKPOINT REFS_DIR OUT.wav [--takes N]

XTTS_DIR holds the base XTTS-v2 files (config.json, vocab.json, speakers_xtts.pth, model.pth);
CHECKPOINT is the fine-tuned model (best_model_*.pth from the GPT trainer, or "base");
REFS_DIR holds Nobo's clean reference clips (*.wav). Lines come from narration_v3.txt, one per
annotation, each placed 0.25 s after its annotation starts. For every line several takes are made;
the kept take is the one Whisper understands (>= 0.85 word match) that is closest to Nobo's voice
(ECAPA speaker similarity) and fits before the next line. The result is then matched to the
recording's microphone (a smooth EQ curve) and laid on a quiet bed of his room tone, so it sounds
like the same recording rather than studio-clean synthesis.

Needs (in one venv): coqui-tts, torch/torchaudio 2.8 CPU, faster-whisper, speechbrain, soundfile, Pillow.
"""
import difflib
import glob
import importlib.util
import json
import os
import re
import sys

os.environ["COQUI_TOS_AGREED"] = "1"
import numpy as np
import soundfile as sf
import torch
import torchaudio
from faster_whisper import WhisperModel
from speechbrain.inference.speaker import EncoderClassifier
from TTS.tts.configs.xtts_config import XttsConfig
from TTS.tts.models.xtts import Xtts

REPO, M, CKPT, REFS, OUT = sys.argv[1:6]
TAKES = int(sys.argv[sys.argv.index("--takes") + 1]) if "--takes" in sys.argv else 5
ECAPA = os.environ.get("ECAPA_DIR", os.path.join(os.path.dirname(os.path.abspath(OUT)), "ecapa"))
torch.set_num_threads(4)
SR = 24000

spec = importlib.util.spec_from_file_location("a", f"{REPO}/tools/video/annotate_video.py")
av = importlib.util.module_from_spec(spec); spec.loader.exec_module(av)
_, ann = av.build_timeline()
TOTAL = len(_) / av.FPS
starts = [0.0] + [f0 / av.FPS for f0, f1, a in ann]
lines = [l.strip() for l in open(f"{REPO}/tools/video/narration_v3.txt", encoding="utf-8") if l.strip()]
assert len(lines) == len(starts)
SAY = {"ScopeGuard": "Scope Guard", "brute-force": "brute force", "counter-attack": "counter attack",
       "hash-chained": "hash chained", "port-scan": "port scan", "…": "."}

cfg = XttsConfig(); cfg.load_json(f"{M}/config.json")
model = Xtts.init_from_config(cfg)
if CKPT == "base":
    model.load_checkpoint(cfg, checkpoint_dir=M, use_deepspeed=False)
else:
    model.load_checkpoint(cfg, checkpoint_path=CKPT, vocab_path=f"{M}/vocab.json",
                          speaker_file_path=f"{M}/speakers_xtts.pth", use_deepspeed=False)
model.eval()
refs = sorted(glob.glob(f"{REFS}/*.wav"))
gpt, spk = model.get_conditioning_latents(audio_path=refs, gpt_cond_len=30, max_ref_length=60)

asr = WhisperModel("small.en", device="cpu", compute_type="int8")
enc = EncoderClassifier.from_hparams(source=ECAPA, savedir=ECAPA, run_opts={"device": "cpu"})


def to16(a, sr):
    return torchaudio.functional.resample(torch.tensor(a, dtype=torch.float32)[None], sr, 16000)


ref_all = np.concatenate([sf.read(r, dtype="float32")[0] for r in refs])
ref_sr = sf.info(refs[0]).samplerate
REF_EMB = enc.encode_batch(to16(ref_all, ref_sr)).squeeze()


def norm(t):
    return re.sub(r"[^a-z0-9 ]", " ", t.lower().replace("-", " ")).split()


def judge(audio, expected):
    a16 = to16(audio, SR)
    heard = " ".join(s.text for s in asr.transcribe(a16[0].numpy(), beam_size=5)[0])
    words = difflib.SequenceMatcher(None, norm(expected), norm(heard)).ratio()
    voice = float(torch.nn.functional.cosine_similarity(REF_EMB, enc.encode_batch(a16).squeeze(), dim=0))
    return words, voice, heard.strip()


def trim(audio):
    nz = np.nonzero(np.abs(audio) > 0.02)[0]
    return audio[max(nz[0] - 480, 0): nz[-1] + 2400] if len(nz) else audio


# ---------------------------------------------------------------- the lines
track = np.zeros(int((TOTAL + 1) * SR), dtype=np.float32)
LEAD = 0.25
report = []
for i, (t0, line) in enumerate(zip(starts, lines)):
    t_end = starts[i + 1] if i + 1 < len(starts) else TOTAL - 0.9
    window = t_end - t0 - LEAD - 0.15
    spoken = line
    for x, y in SAY.items():
        spoken = spoken.replace(x, y)
    takes = []
    for k in range(TAKES):
        speed = 1.0 if k < TAKES - 1 else 1.12
        torch.manual_seed(1000 * i + k)
        out = model.inference(spoken, "en", gpt, spk, temperature=0.7, speed=speed)
        audio = trim(np.array(out["wav"], dtype=np.float32))
        words, voice, heard = judge(audio, line.replace("…", ""))
        takes.append(dict(audio=audio, dur=len(audio) / SR, words=words, voice=voice, heard=heard, speed=speed))
    ok = [t for t in takes if t["words"] >= 0.85 and t["dur"] <= window] or \
         [t for t in takes if t["dur"] <= window] or takes
    best = max(ok, key=lambda t: (t["words"] >= 0.85, t["voice"]))
    s = int((t0 + LEAD) * SR)
    track[s:s + len(best["audio"])] += best["audio"]
    report.append(dict(i=i, line=line, heard=best["heard"], words=round(best["words"], 2),
                       voice=round(best["voice"], 3), dur=round(best["dur"], 2), window=round(window, 2),
                       fits=best["dur"] <= window, speed=best["speed"]))
    print(i, report[-1]["fits"], report[-1]["words"], report[-1]["voice"], "|", best["heard"], flush=True)


# ---------------------------------------------------------------- sound like the same recording
def avg_spectrum(x, sr, n=2048):
    win = np.hanning(n)
    frames = [x[j:j + n] * win for j in range(0, len(x) - n, n // 2)]
    frames = [f for f in frames if np.sqrt(np.mean(f ** 2)) > 0.01]       # speech only
    return np.mean([np.abs(np.fft.rfft(f)) for f in frames], axis=0), np.fft.rfftfreq(n, 1 / sr)


ref24 = torchaudio.functional.resample(torch.tensor(ref_all)[None], ref_sr, SR)[0].numpy()
ref_spec, freqs = avg_spectrum(ref24, SR)
gen_spec, _ = avg_spectrum(track, SR)
gain = ref_spec / np.maximum(gen_spec, 1e-9)
gain /= np.median(gain[(freqs > 300) & (freqs < 3000)])
# smooth over ~1/3 octave and limit to +-6 dB so it only colours, never distorts
logf = np.log2(np.maximum(freqs, 20))
smooth = np.array([np.mean(gain[np.abs(logf - lf) < 1 / 6]) for lf in logf])
smooth = np.clip(smooth, 0.5, 2.0)
spec = np.fft.rfft(track)
curve = np.interp(np.fft.rfftfreq(len(track), 1 / SR), freqs, smooth)
track = np.fft.irfft(spec * curve, n=len(track)).astype(np.float32)

# room tone: the quietest 2 s of his recording, looped very low under everything
hop = SR // 10
rms = np.array([np.sqrt(np.mean(ref24[j:j + 2 * SR] ** 2)) for j in range(0, len(ref24) - 2 * SR, hop)])
j = int(np.argmin(rms)) * hop
room = ref24[j:j + 2 * SR]
fade = np.minimum(1, np.minimum(np.arange(len(room)), np.arange(len(room))[::-1]) / (0.2 * SR))
room = room * fade
bed = np.zeros_like(track)
for k in range(0, len(bed), int(1.8 * SR)):
    seg = room[: len(bed) - k]
    bed[k:k + len(seg)] += seg
track = track / np.abs(track).max() * 0.89
bed = bed / (np.sqrt(np.mean(bed ** 2)) + 1e-9) * 10 ** (-58 / 20)       # about -58 dBFS
track = track + bed
sf.write(OUT, track[: int(TOTAL * SR)], SR)
json.dump(report, open(OUT + ".json", "w"), indent=1)
print("voice similarity mean", round(float(np.mean([r["voice"] for r in report])), 3),
      "| lines not understood:", [r["i"] for r in report if r["words"] < 0.85],
      "| overflow:", [r["i"] for r in report if not r["fits"]])
