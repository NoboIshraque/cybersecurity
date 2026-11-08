#!/usr/bin/env sh
# Mirage - Laptop B (attacker): Specter + counter-watch.
# Put mirage.jar and mirage.properties next to this script. Only Java 17+ is needed on this laptop.
cd "$(dirname "$0")" || exit 1
JAR=./mirage.jar
[ -f "$JAR" ] || JAR=./target/mirage.jar
if [ ! -f "$JAR" ]; then
  echo "mirage.jar not found. Copy it from the defender laptop, or build it with:  ./mvnw -DskipTests package"
  exit 1
fi
command -v java >/dev/null 2>&1 || { echo "Java 17 or newer is required: https://adoptium.net"; exit 1; }
[ -f mirage.properties ] || echo "No mirage.properties here: running the single-machine defaults (127.0.0.1)."
exec java -jar "$JAR" attacker "$@"
