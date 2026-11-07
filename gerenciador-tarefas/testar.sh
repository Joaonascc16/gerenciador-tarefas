#!/usr/bin/env sh
# Compila o projeto e roda os testes de ponta a ponta (Linux/macOS).
set -e
cd "$(dirname "$0")"
rm -rf out out-test
mkdir -p out out-test
javac -encoding UTF-8 -d out -sourcepath src/main/java src/main/java/com/gerenciador/Main.java
cp -r src/main/resources/. out/
javac -encoding UTF-8 -cp out -d out-test src/test/java/com/gerenciador/TestesApi.java
java -cp out:out-test com.gerenciador.TestesApi
