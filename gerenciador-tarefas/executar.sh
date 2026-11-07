#!/usr/bin/env sh
# Compila e executa o Gerenciador de Tarefas (Linux/macOS). Requer JDK 17 ou superior.
# Uso: ./executar.sh [porta]
set -e
cd "$(dirname "$0")"
rm -rf out
mkdir -p out
javac -encoding UTF-8 -d out -sourcepath src/main/java src/main/java/com/gerenciador/Main.java
cp -r src/main/resources/. out/
exec java -cp out com.gerenciador.Main "$@"
