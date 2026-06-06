#!/usr/bin/env bash
#
# Runs Markdown Reader ensuring JDK 21 is used.
#
set -euo pipefail

# Detects JDK 21 via SDKMAN, if available.
if [ -z "${JAVA_HOME:-}" ] || ! "$JAVA_HOME/bin/java" -version 2>&1 | grep -q '"21'; then
    for candidate in "$HOME"/.sdkman/candidates/java/21*; do
        if [ -d "$candidate" ]; then
            export JAVA_HOME="$candidate"
            break
        fi
    done
fi

echo "Using JAVA_HOME=${JAVA_HOME:-<system default>}"
exec mvn -q clean javafx:run "$@"
