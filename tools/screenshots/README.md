# GUI screenshot harnesses

Offline harnesses that render the real Swing GUIs and capture the figures used in
[`REPORT.md`](../../REPORT.md) Section 5 (and the Report artifact). They are **not** part of
the application — they build the real `Dashboard` / `SpecterConsole` components, drive them,
and grab a PNG. Everything stays inside the `ScopeGuard` lab range.

## Files

- `DashboardShot.java` — builds the Console `Dashboard`, seeds the `AttackerDirectory` with
  sample activity for four sources, publishes six alerts, selects a source, and renders one
  view. Arg: `dossier` (default) | `replay` | `responses` | `broken`. In `broken` mode it
  corrupts one stored ledger entry's hash so the integrity check reports BROKEN and the
  tampered row highlights red.
- `SpecterShot.java` — builds the `SpecterConsole`, selects the full campaign, and launches
  it against `127.0.0.1` (authorised by the lab range) so the live attack log fills.
- `capture.sh` — compiles both harnesses, runs each under a headless `Xvfb` display,
  screenshots with ImageMagick, trims/frames, and writes the four figures to
  `docs/screenshots/`: `console-dashboard.png` (Fig 5), `specter-console.png` (Fig 6),
  `detail-replay-responses.png` (Fig 7), `ledger-broken.png` (Fig 8).

## Regenerate the figures

```bash
mvn compile
tools/screenshots/capture.sh
```

Requires a JDK, `Xvfb` / `xvfb-run`, and ImageMagick (`import`, `convert`). The harnesses run
on a virtual display, so no desktop is needed.
