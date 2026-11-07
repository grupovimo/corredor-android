#!/bin/sh
# Compara las capturas de iOS y Android pantalla por pantalla: baja el último artifact «capturas» de
# grupovimo/corredor-ios y de grupovimo/corredor-android (o los de los run que se pasen) y arma, por pantalla,
# una hoja iOS | Android en claro y en oscuro, más un index.html. Uso: comparar.sh <salida> [run-ios] [run-android]
set -e
SAL=${1:?salida}; RI=$2; RA=$3
export GH_TOKEN=$(gh auth token -u grupovimo)
ultimo() { gh run list -R "$1" --workflow "$2" --status success --limit 1 --json databaseId -q '.[0].databaseId'; }
[ -n "$RI" ] || RI=$(ultimo grupovimo/corredor-ios iOS)
[ -n "$RA" ] || RA=$(ultimo grupovimo/corredor-android Android)
rm -rf "$SAL" && mkdir -p "$SAL/ios" "$SAL/android"
gh run download "$RI" -R grupovimo/corredor-ios -n capturas -D "$SAL/ios"
gh run download "$RA" -R grupovimo/corredor-android -n capturas -D "$SAL/android"
python3 "$(dirname "$0")/comparar.py" "$SAL"
