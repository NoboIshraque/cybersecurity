#!/usr/bin/env bash
# Capture the Mirage GUI screenshots used in REPORT.md Section 5.
# Requires: a compiled project (mvn compile), a JDK, Xvfb/xvfb-run, and ImageMagick.
# Writes to docs/screenshots/: console-dashboard.png, specter-console.png,
# detail-replay-responses.png, ledger-broken.png.
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
  xvfb-run -a -s "-screen 0 1160x730x24" bash -c "
    java -Djava.awt.headless=false -cp '$CLASSES:$WORK' $main $args >/dev/null 2>&1 &
    JP=\$!
    sleep 8
    import -display \$DISPLAY -window root '$out'
    kill \$JP 2>/dev/null
    exit 0
  "
}

mkdir -p "$OUT"
echo "capturing dashboard states and specter..."
shoot DashboardShot dossier   "$WORK/dossier.png"
shoot DashboardShot replay    "$WORK/replay.png"
shoot DashboardShot responses "$WORK/responses.png"
shoot DashboardShot broken    "$WORK/broken.png"
shoot SpecterShot   ""         "$WORK/specter.png"

echo "trimming + framing..."
for M in dossier replay responses broken; do
  convert "$WORK/$M.png" -trim +repage "$WORK/dw-$M.png"
done
# Figure 5: full dashboard, dossier tab
convert "$WORK/dw-dossier.png" -bordercolor '#cfcfcf' -border 1 "$OUT/console-dashboard.png"
# Figure 8: full dashboard, broken ledger (red row + BROKEN)
convert "$WORK/dw-broken.png"  -bordercolor '#cfcfcf' -border 1 "$OUT/ledger-broken.png"
# Figure 6: Specter console
convert "$WORK/specter.png" -trim +repage -bordercolor '#cfcfcf' -border 1 "$OUT/specter-console.png"

# Figure 7: the detail pane (tab bar + content) from the replay and responses states, stacked.
# The dashboard window is setSize(1120,680); the detail pane sits at x>=572 after trimming.
convert "$WORK/dw-replay.png"    -crop 546x170+572+136 +repage "$WORK/det-replay.png"
convert "$WORK/dw-responses.png" -crop 546x228+572+136 +repage "$WORK/det-responses.png"
convert "$WORK/det-replay.png"    -bordercolor '#cfcfcf' -border 1 "$WORK/a.png"
convert "$WORK/det-responses.png" -bordercolor '#cfcfcf' -border 1 "$WORK/b.png"
convert "$WORK/a.png" \( -size 548x14 xc:white \) "$WORK/b.png" -append "$OUT/detail-replay-responses.png"

echo "done -> $OUT"
ls -l "$OUT"
