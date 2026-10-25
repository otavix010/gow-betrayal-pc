#!/bin/bash
# Compila o app (camada MIDP + host + tradução) -> build/gowb.jar e monta dist/gowb-port.
# O jogo NÃO entra no app: o usuário fornece o .jar dele (pasta jogo/ ou arrastando para a janela).
set -e
cd "$(dirname "$0")"
rm -rf build/classes && mkdir -p build/classes/traducao
find midp/src host/src -name '*.java' > build/sources.txt
javac -source 21 -target 21 -encoding UTF-8 -nowarn --add-exports java.desktop/com.sun.media.sound=ALL-UNNAMED -cp "lib/*" -d build/classes @build/sources.txt
cp traducao/pt-BR.txt build/classes/traducao/ 2>/dev/null || true
{
    echo "Main-Class: gowb.Main"
    echo "Add-Exports: java.desktop/com.sun.media.sound"
    echo "Class-Path: $(cd lib && ls *.jar | sed 's|^|lib/|' | tr '\n' ' ')"
} > build/manifest.txt
jar cfm build/gowb.jar build/manifest.txt -C build/classes .
echo "ok: build/gowb.jar"

rm -rf dist && mkdir -p dist/gowb-port/lib dist/gowb-port/jogo
cp build/gowb.jar dist/gowb-port/
cp lib/*.jar dist/gowb-port/lib/
cp pacote/jogar.sh pacote/LEIA-ME.txt dist/gowb-port/
cp pacote/jogo-LEIA.txt dist/gowb-port/jogo/LEIA.txt
# banco de sons GM (GeneralUser GS, licença livre) para a música e os efeitos MIDI
mkdir -p dist/gowb-port/sons && cp sons/GeneralUser-GS.sf2 sons/LICENSE-GeneralUser-GS.txt dist/gowb-port/sons/
chmod +x dist/gowb-port/jogar.sh 2>/dev/null || true
echo "ok: dist/gowb-port"
