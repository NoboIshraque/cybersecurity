# GUI screenshot harnesses

Offline harnesses that render the real Swing GUIs and capture the figures used in
[`REPORT.md`](../../REPORT.md) Section 5 (and the Report artifact). They are **not** part of
the application — they build the real `Dashboard` / `SpecterConsole` components, drive them,
and grab a PNG. Everything stays inside the `ScopeGuard` lab range.

## Files

- `DashboardShot.java` — builds the Console `Dashboard`, fills it with six sample alerts
  spanning all severities, optionally tampers one stored ledger entry (`broken` mode), then
  clicks the real **Verify ledger** button. Arg: `verified` (default) or `broken`.
- `SpecterShot.java` — builds the `SpecterConsole`, selects the full campaign, and launches
  it against `127.0.0.1` (authorised by the lab range) so the live attack log fills.
- `capture.sh` — compiles both harnesses, runs each under a headless `Xvfb` display,
  screenshots with ImageMagick, trims/frames, and writes the three figures to
  `docs/screenshots/`.

## Regenerate the figures

```bash
mvn compile
tools/screenshots/capture.sh
```

Requires a JDK, `Xvfb` / `xvfb-run`, and ImageMagick (`import`, `convert`). The harnesses run
on a virtual display, so no desktop is needed.
