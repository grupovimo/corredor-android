#!/bin/sh
# Regenera las fuentes de íconos y tema/Simbolos.kt a partir de herramientas/simbolos.txt
# (nombre de SF Symbol de iOS = nombre de Material Symbols Rounded). Se corre a mano al sumar un ícono.
# Necesita fonttools (pip install fonttools). Material Symbols: Apache 2.0.
set -e
cd "$(dirname "$0")"
T=$(mktemp -d)
curl -sL -o "$T/ms.ttf" "https://github.com/google/material-design-icons/raw/master/variablefont/MaterialSymbolsRounded%5BFILL,GRAD,opsz,wght%5D.ttf"
curl -sL -o "$T/cp" "https://github.com/google/material-design-icons/raw/master/variablefont/MaterialSymbolsRounded%5BFILL,GRAD,opsz,wght%5D.codepoints"
python3 generar_simbolos.py simbolos.txt "$T/cp" ../app/src/main/java/ar/com/corredor/app/tema/Simbolos.kt > "$T/u"
for F in 0 1; do
  fonttools varLib.instancer "$T/ms.ttf" FILL=$F GRAD=0 opsz=24 wght=500 -o "$T/i$F.ttf" -q
  pyftsubset "$T/i$F.ttf" --unicodes="$(cat "$T/u")" --no-layout-closure --layout-features='' --output-file="$T/s$F.ttf"
done
cp "$T/s0.ttf" ../app/src/main/res/font/simbolos_contorno.ttf
cp "$T/s1.ttf" ../app/src/main/res/font/simbolos_relleno.ttf
rm -rf "$T"
