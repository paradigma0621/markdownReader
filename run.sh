#!/usr/bin/env bash
#
# Executa o Markdown Reader garantindo o uso do JDK 21.
#
set -euo pipefail

# Detecta JDK 21 via SDKMAN, se disponível.
if [ -z "${JAVA_HOME:-}" ] || ! "$JAVA_HOME/bin/java" -version 2>&1 | grep -q '"21'; then
    for candidate in "$HOME"/.sdkman/candidates/java/21*; do
        if [ -d "$candidate" ]; then
            export JAVA_HOME="$candidate"
            break
        fi
    done
fi

echo "Usando JAVA_HOME=${JAVA_HOME:-<padrão do sistema>}"
exec mvn -q clean javafx:run "$@"
