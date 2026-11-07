#!/bin/sh
# Escribe app/src/test/resources/paridad.txt: cada pantalla de iOS (Corredor/Pantallas/<Carpeta>/<Nombre>.swift) que
# tiene que tener su clon en Android (pantallas/<carpeta>/<Nombre>.kt). Lo lee ParidadTest. Se corre al sumar o sacar una
# pantalla en iOS. Uso: herramientas/paridad.sh [ruta a corredor-ios] (por defecto ~/Trabajo/vimo/corredor-ios).
set -e
IOS=${1:-$HOME/Trabajo/vimo/corredor-ios}
cd "$(dirname "$0")/.."
( cd "$IOS/Corredor/Pantallas" && find . -name '*.swift' | sed 's|^\./||' | LC_ALL=C sort ) > app/src/test/resources/paridad.txt
echo "$(wc -l < app/src/test/resources/paridad.txt | tr -d ' ') pantallas en app/src/test/resources/paridad.txt"
