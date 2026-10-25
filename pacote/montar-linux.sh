#!/bin/bash
# Monta o pacote Linux x64 (rodar numa máquina Linux): dist/gowb-port + Java enxuto (jlink) -> gowb-port-linux-x64.tar.gz
# Uso: montar-linux.sh <pasta dist/gowb-port> <JDK 21 linux x64> <saída.tar.gz>
set -e
src="$1"; jdk="$2"; out="$3"
tmp="$(mktemp -d)"
cp -a "$src" "$tmp/gowb-port"
rm -f "$tmp"/gowb-port/lib/*natives-windows*.jar
"$jdk/bin/jlink" --add-modules java.base,java.desktop,jdk.unsupported --strip-debug --no-header-files \
    --no-man-pages --compress=zip-6 --output "$tmp/gowb-port/jre"
chmod +x "$tmp/gowb-port/jogar.sh"
tar -C "$tmp" -czf "$out" gowb-port
rm -rf "$tmp"
du -h "$out"
