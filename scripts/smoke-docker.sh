#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
java_command="${JAVA_HOME:+$JAVA_HOME/bin/}java"
jar="$(pwd)/target/openbench-0.2.0-SNAPSHOT.jar"
[ -f "$jar" ] || { echo 'Run ./mvnw clean verify first.' >&2; exit 1; }
docker pull eclipse-temurin:25-jdk
docker pull eclipse-temurin:25-jre
run_root="$(mktemp -d "${TMPDIR:-/tmp}/openbench-smoke.XXXXXXXX")"
printf 'Smoke artifacts: %s\n' "$run_root"
for profile in chronicle-fold partition-weave checkpoint-mosaic; do
  "$java_command" -jar "$jar" run --profile "$profile" --level level-16 --seed 42 --attempts 1 --seconds 600 --output "$run_root/$profile"
  "$java_command" -jar "$jar" pack "$run_root/$profile" "$run_root/$profile.tar.gz"
done
