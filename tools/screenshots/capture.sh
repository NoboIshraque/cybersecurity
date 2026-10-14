#!/usr/bin/env bash
# Capture the Mirage GUI screenshots used in REPORT.md Section 5.
# Requires: a compiled project (mvn compile), a JDK, Xvfb/xvfb-run, and ImageMagick.
# Writes to docs/screenshots/: console-dashboard.png, specter-console.png, ledger-verify.png.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
CLASSES="$ROOT/target/classes"
OUT="$ROOT/docs/screenshots"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

command -v xvfb-run >/dev/null || { echo "need xvfb-run"; exit 1; }
command -v import    >/dev/null || { echo "need ImageMagick (import/convert)"; exit 1; }
[ -d "$CLASSES" ] || { echo "run 'mvn compile' first"; exit 1; }

echo "compiling harnesses..."
javac -cp "$CLASSES" -d "$WORK" \
  "$ROOT/tools/screenshots/DashboardShot.java" \
  "$ROOT/tools/screenshots/SpecterShot.java"

# shoot <main-class> <args> <out.png> : run the harness on a virtual display and grab the root window
shoot() {
  local main="$1" args="$2" out="$3"
  xvfb-run -a -s "-screen 0 900x600x24" bash -c "
    java -Djava.awt.headless=false -cp '$CLASSES:$WORK' $main $args >/dev/null 2>&1 &
    JP=\$!
    sleep 7
    import -display \$DISPLAY -window root '$out'
    kill \$JP 2>/dev/null
    exit 0
  "
}

mkdir -p "$OUT"
echo "capturing dashboard (verified / broken) and specter..."
shoot DashboardShot verified "$WORK/dv.png"
shoot DashboardShot broken   "$WORK/db.png"
shoot SpecterShot   ""        "$WORK/sp.png"

echo "trimming + framing..."
# Dashboard is setSize(840,540); after trimming the black Xvfb border the window is 840x540.
convert "$WORK/dv.png" -trim +repage "$WORK/dv-win.png"
convert "$WORK/db.png" -trim +repage "$WORK/db-win.png"
convert "$WORK/dv-win.png" -bordercolor '#cfcfcf' -border 1 "$OUT/console-dashboard.png"
convert "$WORK/sp.png" -trim +repage -bordercolor '#cfcfcf' -border 1 "$OUT/specter-console.png"

# Ledger figure: the verified status strip stacked over the BROKEN one (bottom ~34px of the window).
convert "$WORK/dv-win.png" -crop 840x34+0+505 +repage "$WORK/strip-v.png"
convert "$WORK/db-win.png" -crop 840x34+0+505 +repage "$WORK/strip-b.png"
convert \
  \( "$WORK/strip-v.png" -bordercolor '#cfcfcf' -border 1 \) \
  \( "$WORK/strip-b.png" -bordercolor '#cfcfcf' -border 1 \) \
  -append "$OUT/ledger-verify.png"

echo "done -> $OUT"
ls -l "$OUT"
