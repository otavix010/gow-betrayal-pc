#!/bin/bash
# God of War: Betrayal - port nativo. Coloque o .jar do jogo na pasta "jogo" (ou arraste para a janela).
# Opções: --windowed  --integer  --filter sharp|nearest|smooth  --no-ambient  --idioma pt-BR|en  --jogo arquivo.jar
d="$(dirname "$(realpath "$0")")"
cd "$d" || exit 1
# nomes de pasta com acento precisam de locale UTF-8
[ -z "$LANG" ] && export LANG=C.UTF-8
java="$d/jre/bin/java"
[ -x "$java" ] || java=java
exec "$java" -jar "$d/gowb.jar" "$@" > "$d/log.txt" 2>&1
