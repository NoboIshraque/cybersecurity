# Demo video — progress log

Goal: a demo video (v1/v2: **under 2 minutes**; v3: about **2:30**), edited in **Clipchamp** (or another online editor).
The user adds the voice-over afterwards. The Claude "glowing edges" (computer-use overlay) must
not be in it. This file is the hand-over: a new session (or a new account) reads it and carries
on from the first unticked step.

**Latest: v3** (2026-11-17), the annotated cut of the v1 recording (section below). **v2**
(2026-11-10/11) is the self-recorded version. v1 (below, superseded) was a Snipping Tool recording;
the user found its pointer movement unnatural and asked for one like the UHID project's video
(`D:\UHID demo video.mp4`, made by `E:\hospital-management-system\video\`).

## v3 — annotated, 2:30 (2026-11-17)

The user asked for the v1 video (`D:\Mirage demo video.mp4`, 1:41) to be about **2:30**, with a
**red circle and an arrow** on each thing that is about to be clicked or shown, kept in step with
the cue sheet.

How it is made: `tools/video/annotate_video.py` post-processes the v1 file (Pillow + ffmpeg). The
v1 recording is mostly still holds between steps, so the new cut is an edit list of PLAY (a source
range at 1x) and HOLD (one source frame for N seconds) segments:

- **before each click** the frame is held and the control is circled (Campaign + Launch, Tarpit,
  Feed fake data, Block, Counter-attack, Simultaneous counter, Verify ledger, the Target field);
- **after each step** the results are circled one after another (banner in the shell, the popup
  header with the ATT&CK id, op-shell ScopeGuard lines and the sensor confirmation, …);
- each annotation draws in (the outline sweeps clockwise, then the arrow grows from a red label),
  holds, and fades; the last 0.8 s fades to black. Silent AAC track kept for the voice-over.

```
pip install pillow
set FFMPEG=C:\Users\noboi\AppData\Local\Programs\Python\Python312\Lib\site-packages\imageio_ffmpeg\binaries\ffmpeg-win-x86_64-v9.0.2.exe
python tools\video\annotate_video.py "D:\Mirage demo video.mp4" "D:\Mirage demo video v3.mp4" --cues cues-v3.md
```

`--stills DIR` writes one fully drawn still per annotation instead of the video (to check
placement). The coordinates are in the 1920x1080 v1 frame, so the edit list fits only that file.

- [x] 2026-11-17 Rendered: **2:30.3**, 1920x1080, 30 fps, 8.3 MB (under the 10 MB upload cap),
      41 annotations; every annotation checked on a still, and sample frames of the rendered
      video checked.
- [x] 2026-11-17 Voice-over added: Kokoro TTS (voice `af_heart`), lines in
      `tools/video/narration_v3.txt`, laid out by `tools/video/voiceover.py` (each line starts 0.25 s
      after its annotation; 87 s of speech in 2:30). Checked by transcribing it back with Whisper:
      every line heard, in its slot. The video with the voice-over is embedded in
      `docs/submission/Mirage_Presentation.pptx` (slide 8, "Demo: Mirage in action", just before
      section 02).
- [x] 2026-11-17 **Narrated by Nobo, in his own voice** (replaces the TTS voice-over). An AI clone
      of his voice (XTTS-v2 from a 3-minute sample) still sounded artificial, so his real recording
      of `docs/submission/Mirage_Voiceover_Script.md` (3:05) is used instead:
      `tools/video/narrate_recording.py` cuts it into its 11 parts at the pauses, lengthens each
      step's still holds to fit its part (`annotate_video.py --stretch`; clicks stay at real speed,
      the circles stretch with the holds) and holds the last frame for the closing line. Result:
      **3:20**, every part over its own step (checked by transcription), no longer used: its
      script does not match this video.
- [x] 2026-11-17 **Final: narrated in Nobo's voice by an AI voice trained on his recording**
      (`tools/video/voiceover_cloned.py`). XTTS-v2 was fine-tuned for 8 passes on his 3-minute
      recording (29 sentence clips, CPU, ~40 min); speaker similarity to his real voice (ECAPA, a
      separate model) went from 0.41 (zero-shot clone) to 0.64 on unseen lines, 0.70 over the final
      narration (his own 3-s clips score 0.74). Each of the 42 lines in `narration_v3.txt` keeps the
      best of up to 12 takes (understood by Whisper, closest to his voice); four lines were reworded
      where the voice stumbled. Nothing is sped up: steps are lengthened where his pace needs it, so
      the video is **2:45**. The track is EQ-matched to his recording and laid on his room tone.
      Embedded on slide 8 of the deck.

The spoken script (short lines, paced for a slow speaker) is in [`VOICE-OVER-SCRIPT.md`](VOICE-OVER-SCRIPT.md).

### Voice-over cue sheet — v3 (times in the 2:30 video; each row is when that annotation starts)

| Time | Annotation on screen | Talking point (from the cue sheet) |
| --- | --- | --- |
| 0:07.4 | ATTACKER Specter (red team) | Left: Specter, the built-in attacker (red team) |
| 0:10.1 | DEFENDER Mirage Console (blue team) | Right: the Mirage Console that watches the decoys and responds |
| 0:12.7 | Step 1: Recon scan → Launch | Step 1: Specter launches a recon scan |
| 0:17.5 | Scan finds 3 open decoy ports | Scan finds 3 open decoy ports |
| 0:21.1 | Alert: PROBE DETECTED port scan · T1046 · MEDIUM | Popup PROBE DETECTED (T1046): any touch on a decoy is high-confidence |
| 0:24.8 | Step 2: Brute force → Launch | Step 2: Specter brute-forces the decoy login |
| 0:28.8 | Every password rejected | Every password rejected |
| 0:31.9 | Alert: UNDER ATTACK brute force · T1110 · HIGH | Popup UNDER ATTACK (T1110): one alert per finding, into the signed ledger |
| 0:35.3 | Step 3: Honeytoken replay → Launch | Step 3: Specter replays a stolen credential |
| 0:40.5 | Replays a stolen credential… | Replays a stolen credential… |
| 0:43.3 | …which was planted bait (a honeytoken) | ACCESS GRANTED, but the credential was bait and the admin page is fake |
| 0:46.3 | COMPROMISED · T1078 · CRITICAL waiting for the operator | Popup COMPROMISED (T1078); auto-response is off, the operator decides |
| 0:49.4 | Step 4: operator clicks Tarpit | Step 4: operator presses Tarpit |
| 0:54.3 | ScopeGuard checks the target first | Op shell: ScopeGuard.require, in range, dispatching |
| 0:57.3 | Sensor confirms: TARPIT applied | Sensor confirms: TARPIT applied |
| 1:00.1 | The attacker's replay is stuck on the line | Specter: CONNECTION STALLED — the attacker is held on the line |
| 1:03.0 | Step 5: operator clicks Feed fake data | Step 5: operator presses Feed fake data |
| 1:07.1 | Fake data armed on the Sensor | Op shell: FEED FAKE DATA DISPATCHED, sensor confirms |
| 1:09.2 | Brute force again → Launch | Brute force again → Launch |
| 1:12.8 | The attacker "gets in"… | Brute force now 'gets in' — ACCESS GRANTED |
| 1:15.6 | …to fake files: backup.cfg holds another honeytoken | …to fake files; the fake backup.cfg holds another honeytoken |
| 1:18.9 | Step 6: operator clicks Block | Step 6: operator presses Block |
| 1:22.5 | NEUTRALISED | Popup turns NEUTRALISED: Sensor confirmed the block |
| 1:25.3 | Sensor confirms: BLOCK applied | Sensor confirms: BLOCK applied |
| 1:29.3 | Replay the credential again | Specter tries the stolen credential again |
| 1:32.5 | Connection reset: the source is cut off | CONNECTION BLOCKED — the source is cut off |
| 1:35.3 | Step 7: operator clicks Counter-attack (lab range only) | Step 7: operator presses Counter-attack (lab range only, through ScopeGuard) |
| 1:39.5 | The attacker's laptop sees the counter | Counter-watch: INCOMING COUNTER-ATTACK |
| 1:42.3 | Their own technique, mirrored back | The mirror counter replays the attacker's own technique back |
| 1:45.3 | Operator clicks Simultaneous counter | Simultaneous counter: several techniques at once |
| 1:49.8 | One pane per thread: 4 methods at once | One pane per thread — a bounded set, not a flood |
| 1:53.1 | Each arrives as its own connection | Each arrives as its own connection |
| 1:57.6 | Source 127.0.0.1: CRITICAL, 3 alerts | Dashboard: the network map marks the source CRITICAL |
| 2:00.2 | 3 alerts, one per technique | Alert feed: three alerts, one per ATT&CK technique |
| 2:03.0 | Attacker dossier | Attacker dossier: services probed, passwords tried, honeytokens used |
| 2:05.9 | Step 8: Verify ledger | Step 8: Verify ledger |
| 2:09.2 | Hash-chained + signed ledger: verified | Ledger: verified — every alert hash-chained and ECDSA-signed; tampering shows as BROKEN |
| 2:12.9 | Step 9: type a target outside the lab range: 8.8.8.8 | Step 9: Specter is pointed at 8.8.8.8, outside the lab range |
| 2:17.0 | 8.8.8.8 → Launch | 8.8.8.8 → Launch |
| 2:20.6 | REFUSED: outside the lab range | REFUSED — OUTSIDE LAB RANGE |
| 2:24.9 | ScopeGuard: nothing leaves the lab range | ScopeGuard is the safety boundary: every response goes through it |
| 2:30.3 | End | |

## How v2 is made (so any account can redo it)

`tools\video\run-demo-video.cmd` builds the project and runs `tools\video\DemoVideo.java`, which
**records itself — nothing is filmed from the screen**:

- The real Console, Sensor and Specter run in one JVM on 127.0.0.1 and are driven through their
  own buttons (as in `tools/screenshots/LiveDemoShot.java`).
- 30 times a second it composes a 1920x1080 frame: a dark backdrop, then each window's contents
  (`printAll`) in a fixed layout — **Specter (attacker) left, Mirage Console (defender) right** —
  under a drawn Windows 11 style title bar. Frames are piped to ffmpeg.
- On top it draws what the UHID video has: a **pointer** that glides to each control on a slightly
  curved path with minimum-jerk easing (quick in the middle, slow at both ends, longer moves take a
  little longer), a **ripple** where it clicks, **pressed/hover** looks on buttons, the campaign
  **dropdown opened** with the highlight following the pointer, the target **typed** at a varied
  pace, a **lower-third title** per part, and a **title card** and **closing card**.
- Because the desktop is never recorded, the glow (or anything else on screen, this window
  included) cannot get into the video; the laptop can be used while it runs. A bad take is just
  run again.

Run it:

```
set FFMPEG=C:\Users\noboi\AppData\Local\Programs\Python\Python312\Lib\site-packages\imageio_ffmpeg\binaries\ffmpeg-win-x86_64-v9.0.2.exe
tools\video\run-demo-video.cmd
```

Output `target\video\mirage-demo.mp4` (~1:48), `target\video\run\timeline.txt` (video time of each
step) and a still per step in `target\video\run\shots\`. The driver prints its frame timing at
the end: on this laptop ~16 ms to compose a frame and 95 % of the 30 fps frames are unique (the
rest repeat the previous frame when composing falls behind, so video time stays = real time).

Speed fixes that matter if the driver is changed (each was measured): windows are cached as
images and only re-painted where Swing's `RepaintManager` says they changed; title bars are drawn
once per title; shadows are thin strips, not filled or antialiased shapes; windows hidden by later
ones are skipped; writing to ffmpeg runs on its own thread.

Then: re-encode small for the web editor (the Chrome upload tool caps files at 10 MB) with a
silent audio track:

```
ffmpeg -i mirage-demo.mp4 -f lavfi -i anullsrc=r=48000:cl=stereo -map 0:v -map 1:a -shortest -c:v libx264 -preset slow -crf 20 -tune stillimage -pix_fmt yuv420p -c:a aac -b:a 96k -movflags +faststart mirage-demo-small.mp4
```

## Status (v2)

- [x] 2026-11-10 Driver rewritten (self-recording, drawn pointer, ripples, captions, cards).
- [x] 2026-11-10 Ten trial takes to get the frame rate up (119 ms → 16 ms per frame) and check
      the motion frame by frame; every step reached in each take.
- [x] 2026-11-10 Final take: **1:47.9**, 1920x1080, 30 fps. (Its working folder under
      `target\video\` was cleared by a later build; the kept copies are the ones below.)
- [x] 2026-11-11 Small encode with a silent audio track (5 MB, text checked sharp at full size):
      **`D:\Mirage demo video v2.mp4`** (D:\ is this laptop's Downloads folder), copy in
      `OneDrive\Mirage demo video\Mirage demo video v2.mp4`; timeline in
      `OneDrive\Mirage demo video\timeline v2.txt`. The v1 files are left as they were.
- [x] 2026-11-11 Clipchamp project **"Mirage demo video v2"** (OneDrive › Videos › Clipchamp)
      with the v2 file on the timeline (1:47 in Clipchamp; no edits needed: titles and cards are
      in the video). How it was uploaded, for a redo: the OneDrive picker opens a pop-up the
      browser tools cannot reach, and Clipchamp's Import media opens a native file dialog, so a
      temporary `<input type=file>` was added to the page, the file (copied into
      `target\upload\`, a folder the upload tool may read) was put in it, and a synthetic drop
      event on the "Drag & drop media" area imported it.
- [ ] (User) Voice-over in that project (Record & create → Audio, or import a narration file onto
      the audio track), then Export → 1080p. Keep the narration within 1:48.

## Voice-over cue sheet — v2 (times in the 1:48 video)

| Time | On screen | Talking point |
| --- | --- | --- |
| 0:00 | Title card: Mirage, course, author | What Mirage is: decoys + a Console that classifies and responds; Specter is the built-in attacker |
| 0:05 | Recon scan → `SCAN COMPLETE — 3 OPEN`; popup **PROBE DETECTED** (T1046) | Decoys are services nobody legitimate touches, so any touch is high-confidence |
| 0:13 | Brute force → `NO ACCESS`; popup **UNDER ATTACK** (T1110) | One alert per finding, logged to the hash-chained, signed ledger |
| 0:21 | Honeytoken replay → `ACCESS GRANTED`; popup **COMPROMISED** (T1078) | The credential was planted bait; the "admin page" is fake. Auto-response is off: waiting for the operator |
| 0:30 | Operator → **Tarpit**; op shell shows ScopeGuard + sensor ACK; replay → `CONNECTION STALLED` | The attacker is held on the line |
| 0:40 | Operator → **Feed fake data**; brute force now "gets in" to fake files | Misleading the attacker; the fake backup.cfg holds another honeytoken |
| 0:51 | Operator → **Block**; popup **NEUTRALISED**; replay → `CONNECTION BLOCKED` | Sensor confirms the block, the source is cut off |
| 1:04 | **Counter-attack** → counter-watch `INCOMING COUNTER-ATTACK`; 1:10 **Simultaneous counter** splits into one pane per thread | Mirrors the attacker's own technique back, lab range only, through ScopeGuard; a bounded set, not a flood |
| 1:18 | Dashboard: alert feed (3 alerts), attacker dossier; **Verify ledger** → `Ledger: verified` | Every alert hash-chained + ECDSA-signed; tampering shows as BROKEN |
| 1:31 | Specter target typed `8.8.8.8` → **REFUSED — OUTSIDE LAB RANGE** | Safety boundary: nothing leaves the lab range |
| 1:43 | Closing card: "Every response goes through ScopeGuard." · 3 decoys · 7 rules · signed ledger · 299 tests | |
| 1:48 | End | |

---

## v1 (superseded 2026-11-10) — Snipping Tool recording

- Recorded with Snipping Tool's screen recording while the old driver (real mouse pointer moved
  by `java.awt.Robot`, programmatic clicks) played the demo; glow avoided by ending Claude's turn
  during the recording. Raw: `%USERPROFILE%\Videos\Screen Recordings\Screen Recording 2026-11-10 082705.mp4`.
- Edited in Clipchamp, project **"Mirage demo video"** (OneDrive › Videos › Clipchamp): trimmed
  to 1:41, clip audio muted, title text box. Exported: `D:\Mirage demo video.mp4` (copy in
  `OneDrive\Mirage demo video\`).
- Why replaced: the pointer moved in straight jumps, the dropdown changed without opening and
  button presses did not show.
