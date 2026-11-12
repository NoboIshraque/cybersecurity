# Demo video — progress log

Goal: a demo video of **under 2 minutes**, edited in **Clipchamp** (or another online editor).
The user adds the voice-over afterwards. The Claude "glowing edges" (computer-use overlay) must
not be in it. This file is the hand-over: a new session (or a new account) reads it and carries
on from the first unticked step.

**Current version: v2** (2026-11-10/11). v1 (below, superseded) was a Snipping Tool recording;
the user found its pointer movement unnatural and asked for one like the UHID project's video
(`D:\UHID demo video.mp4`, made by `E:\hospital-management-system\video\`).

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
