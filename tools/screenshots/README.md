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

## Live-shells dry run (`LiveDemoShot.java`)

`LiveDemoShot` is the Phase 8 dry run of the live-shells demo **and** the source of the
`docs/screenshots/live-*.png` figures. It starts the real Console, Sensor and Specter in one JVM
(default config, everything on `127.0.0.1`, counter-watch moved to `:18080` so a busy `:8080`
can't skew it) and drives them through their own buttons: every Specter campaign (one at a time
and all at once), Tarpit / Feed fake data / Block from the threat popup, Counter-attack and
Simultaneous counter landing on the counter-watch, Unblock from the dashboard, auto-response
switched on, and an out-of-range target being refused. It prints `PASS`/`FAIL` for 28 checks
and exits non-zero if any fail. Windows are rendered straight to PNG (no OS title bar), so other
windows on the desktop never end up in a figure. It needs a real display; it isn't headless.

**Windows, the easy way:** run `tools\screenshots\dry-run.cmd` from the repo root (Command
Prompt or PowerShell; in PowerShell type `.\tools\screenshots\dry-run.cmd`), or double-click it
in Explorer. It builds the classes with the Maven Wrapper, gets the classpath from Maven,
compiles `LiveDemoShot` into a fresh `target\dryrun`, runs it there and writes the figures to
`docs\screenshots`. It exits with LiveDemoShot's code (0 = all checks passed) and, on a
failure, keeps the window open. `dry-run.cmd --compile-only` stops after compiling, without
starting the demo (a quick check that the build works).

The same by hand, from the repo root. Each runs `LiveDemoShot` in an empty folder, because it
creates `data/` (H2) and `bait/` in the working directory.

Windows, Git Bash: `$PWD` there is `/e/...`, which Java can't read, so the Windows paths come
from `pwd -W` (`E:/...`):

```bash
./mvnw -q -DskipTests compile dependency:build-classpath -Dmdep.outputFile=target/cp.txt
ROOT="$(pwd -W)"; CP="$ROOT/target/classes;$(cat target/cp.txt)"
rm -rf target/dryrun && mkdir -p target/dryrun
javac -encoding UTF-8 -cp "$CP" -d target/dryrun/out tools/screenshots/LiveDemoShot.java
(cd target/dryrun && java -cp "out;$CP" LiveDemoShot "$ROOT/docs/screenshots")
```

Windows PowerShell: (5.1 has no `&&`; the `-D…` argument is quoted so PowerShell doesn't split it)

```powershell
.\mvnw.cmd -B -q -DskipTests compile dependency:build-classpath "-Dmdep.outputFile=target\cp.txt"
$root = (Get-Location).Path; $cp = "$root\target\classes;" + (Get-Content target\cp.txt -Raw).Trim()
Remove-Item -Recurse -Force target\dryrun -ErrorAction SilentlyContinue; New-Item -ItemType Directory target\dryrun | Out-Null
javac -encoding UTF-8 -cp $cp -d target\dryrun\out tools\screenshots\LiveDemoShot.java
if ($LASTEXITCODE -eq 0) { Push-Location target\dryrun; java -cp "out;$cp" LiveDemoShot "$root\docs\screenshots"; "exit code $LASTEXITCODE"; Pop-Location }
```

macOS / Linux (`:` separates the classpath there):

```bash
./mvnw -q -DskipTests compile dependency:build-classpath -Dmdep.outputFile=target/cp.txt
mkdir -p /tmp/mirage-dry && CP="$PWD/target/classes:$(cat target/cp.txt)"
javac -encoding UTF-8 -cp "$CP" -d /tmp/mirage-dry/out tools/screenshots/LiveDemoShot.java
(cd /tmp/mirage-dry && java -cp "out:$CP" LiveDemoShot "$OLDPWD/docs/screenshots")
```

| Figure | Shows |
| --- | --- |
| `live-specter-recon.png` | Specter recon shell → `SCAN COMPLETE — n OPEN` |
| `live-specter-access-granted.png` | honeytoken replay → `ACCESS GRANTED` (the decoy's fake admin page) |
| `live-specter-concurrent.png` | full campaign "all at once", one pane per thread |
| `live-threat-popup-compromised.png` | Console threat popup, `COMPROMISED`, waiting for the operator |
| `live-op-shell-block.png` | operation shell: operator → ScopeGuard → broadcast → Sensor ACK, per op thread |
| `live-threat-popup-neutralised.png` | the popup after the Sensor confirms the block |
| `live-specter-stalled.png` / `live-specter-blocked.png` | Specter after a tarpit / a block |
| `live-op-shell-counter-split.png` | simultaneous counter, split per counter thread |
| `live-counterwatch-incoming.png` | the attacker's counter-watch: `INCOMING COUNTER-ATTACK` |
| `live-pipeline-shell.png` | the whole Console pipeline, incl. the auto-response dispatch |
| `live-dashboard.png` | dashboard with the auto-response switch on and the status bar |
| `live-specter-refused.png` | an out-of-range target → `REFUSED — OUTSIDE LAB RANGE` |
