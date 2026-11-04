#!/usr/bin/env bash
GRADLE_BIN="/home/bhoid/.gradle/wrapper/dists/gradle-8.14.3-all/10utluxaxniiv4wxiphsi49nj/gradle-8.14.3/bin/gradle"
if [ -x "$GRADLE_BIN" ]; then
    exec "$GRADLE_BIN" "$@"
elif which gradle >/dev/null 2>&1; then
    exec gradle "$@"
else
    echo "Error: Gradle binary not found." >&2
    exit 1
fi
