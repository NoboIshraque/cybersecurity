# Demo video — progress log

Goal: a demo video of **under 2 minutes**, recorded on this laptop with **Snipping Tool's
screen recording**, edited in **Clipchamp** (or another online editor). The user adds the
voice-over afterwards. The Claude "glowing edges" (computer-use overlay) must not be in it.

## How it is made (so any account can redo it)

1. `tools\video\run-demo-video.cmd` builds the project, compiles `tools\video\DemoVideo.java` and
   opens the real Console + Sensor + Specter windows in one JVM (one-laptop mode, `127.0.0.1`),
   laid out on a dark backdrop: **Specter (attacker) on the left, Console (defender) on the
   right**. It then waits.
2. Start Snipping Tool → Record → **New** → drag a region from about **(8,8) to (1528,808)**
   (logical pixels on the 1536x864 screen). Staying 8 px inside the screen edges keeps the
   glowing edges out of the video; the taskbar is outside the region too. Press **Start**.
3. Create the file `target\video\run\go.flag` (e.g. `type nul > target\video\run\go.flag`). The
   demo plays itself: the pointer glides to each control and presses it. It takes ~100 s and
   writes `target\video\run\timeline.txt` (seconds since go → which step).
4. When `done.flag` appears, stop the recording, save it (Videos\Screen Recordings), create
   `quit.flag` to close the apps.
5. Clipchamp: import the recording, trim the head and tail, add a short title, export 1080p.

Demo steps played (condensed from `DEMO.md`): recon → brute force → honeytoken replay
(COMPROMISED) → Tarpit (STALLED) → Feed fake data (fake files) → Block (NEUTRALISED / BLOCKED)
→ Counter-attack + Simultaneous counter (counter-watch INCOMING) → Verify ledger → 8.8.8.8
REFUSED.

## Status

- [x] 2026-11-10 Driver `tools/video/DemoVideo.java` + `tools/video/run-demo-video.cmd` written, compiles.
- [x] 2026-11-10 Trial runs OK: whole demo plays in **~99 s**, all steps reached (snapshots in
      `target\video\run2\shots`). Pauses are long enough to talk over.
- [x] 2026-11-10 Final recording saved, no glow, no audio:
      `%USERPROFILE%\Videos\Screen Recordings\Screen Recording 2026-11-10 082705.mp4`
      (4:29 raw, 1896x998, 30 fps). **The demo runs from 0:15.0 to 1:56.0** (trim everything
      else; before 0:15 the Claude window is in the shot, after 1:56 it is the desktop).
      Timeline inside the demo = `target\video\run6\timeline.txt` + 15 s.
      How the glow was avoided: the driver pins the demo windows on top when it starts; Claude
      starts the recording, then ends its turn (the glow is only on while Claude is acting); a
      background `sleep 25; touch go.flag` starts the demo, and the driver exits when done, which
      wakes Claude to press Stop. The recorder's toolbar is hidden from screenshots: its Stop
      button position was read with UI Automation (856,24 40x39 physical px).
- [x] 2026-11-10 Raw recording copied to OneDrive (`OneDrive\Mirage demo video\mirage-demo-raw.mp4`,
      account noboishraque@gmail.com) because the Chrome upload tool caps files at 10 MB;
      Clipchamp (web, app.clipchamp.com, already signed in) imports it via Import media → ▾ → OneDrive.
- [x] 2026-11-10 Edited in Clipchamp (trimmed to 0:15.0–1:56.0, title) and exported (1080p)
      — Clipchamp project **"Mirage demo video"** (saved in OneDrive › Videos › Clipchamp):
      trimmed to the demo (**1:41**), clip audio muted, title text box (Segoe UI 24,
      "MIRAGE / Deception-based intrusion / detection & active defence") over 0:00–0:05.
      Note: Clipchamp reads the raw file as 4:21 (ffmpeg says 4:29), so its cut points were
      checked by eye: cut at Clipchamp 0:15 (first demo frame) and 1:41 after the first cut
      (last REFUSED frame).
- [x] 2026-11-10 **FINAL VIDEO: `D:\Mirage demo video.mp4`** (D:\ is this laptop's Downloads
      folder), copy in `OneDrive\Mirage demo video\Mirage demo video.mp4`. 1:41, 1920x1080,
      30 fps, 31 MB, silent audio track (ready for the voice-over).
- [ ] (User) Voice-over. Easiest: open the Clipchamp project "Mirage demo video", Record &
      create → Audio (or import a recorded track), lay it under the clip, export again.

## Voice-over cue sheet (times in the final 1:41 video)

| Time | On screen | Talking point |
| --- | --- | --- |
| 0:00 | Title; Specter (attacker) left, Mirage Console (defender) right, empty feed | What Mirage is: decoys + a Console that classifies and responds; Specter is the built-in attacker |
| 0:06 | Recon scan → `SCAN COMPLETE — 3 OPEN`; popup **PROBE DETECTED** (T1046) | Decoys are services nobody legitimate touches, so any touch is high-confidence |
| 0:14 | Brute force → `NO ACCESS`; popup **UNDER ATTACK** (T1110) | One alert per finding, logged to the hash-chained, signed ledger |
| 0:22 | Honeytoken replay → `ACCESS GRANTED`; popup **COMPROMISED** (T1078) | The credential was planted bait; the "admin page" is fake. Auto-response is off: waiting for the operator |
| 0:31 | Operator → **Tarpit**; op shell shows ScopeGuard + sensor ACK; replay → `CONNECTION STALLED` | The attacker is held on the line |
| 0:41 | Operator → **Feed fake data**; brute force now "gets in" to fake files | Misleading the attacker; the fake backup.cfg holds another honeytoken |
| 0:53 | Operator → **Block**; popup **NEUTRALISED**; replay → `CONNECTION BLOCKED` | Sensor confirms the block, the source is cut off |
| 1:05 | **Counter-attack** → counter-watch `INCOMING COUNTER-ATTACK`; **Simultaneous counter** splits into one pane per thread | Mirrors the attacker's own technique back, lab range only, through ScopeGuard; a bounded set, not a flood |
| 1:18 | Dashboard: alert feed (3 alerts), attacker dossier; **Verify ledger** → `Ledger: verified` | Every alert hash-chained + ECDSA-signed; tampering shows as BROKEN |
| 1:30 | Specter target `8.8.8.8` → **REFUSED — OUTSIDE LAB RANGE** | Safety boundary: nothing leaves the lab range |
| 1:41 | End | |

## Notes / where things are

- 2026-11-10: first attempt at recording stopped because the computer-use access request for
  Snipping Tool + File Explorer was declined (the dark backdrop probably hid the dialog). Next
  try: ask for access **before** starting the driver, then start the driver, then record.
- Trial snapshots: `target\video\run2\shots\*.png`; trial timeline: `target\video\run2\timeline.txt`.
