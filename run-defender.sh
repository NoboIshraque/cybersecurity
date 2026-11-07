#!/usr/bin/env sh
# Mirage - Laptop A (defender): Console + Sensor (decoys) in one process.
# Put mirage.jar (or build it: ./mvnw -DskipTests package) and mirage.properties next to this script.
cd "$(dirname "$0")" || exit 1
JAR=./mirage.jar
[ -f "$JAR" ] || JAR=./target/mirage.jar
if [ ! -f "$JAR" ]; then
  echo "mirage.jar not found. Build it with:  ./mvnw -DskipTests package"
  exit 1
fi
command -v java >/dev/null 2>&1 || { echo "Java 17 or newer is required: https://adoptium.net"; exit 1; }
[ -f mirage.properties ] || echo "No mirage.properties here: running the single-machine defaults (127.0.0.1)."
exec java -jar "$JAR" defender "$@"
