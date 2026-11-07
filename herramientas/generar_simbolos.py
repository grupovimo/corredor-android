"""Escribe tema/Simbolos.kt (SF Symbol → carácter de Material Symbols) e imprime la lista de unicodes."""
import sys
mapa, codepoints, salida = sys.argv[1:4]
cp = dict(l.split() for l in open(codepoints) if l.strip())
pares = [l.strip().split('=') for l in open(mapa) if '=' in l]
lineas = '\n'.join(f'        "{sf}" to \'\\u{cp[m]}\', // {m}' for sf, m in pares)
open(salida, 'w').write(f'''package ar.com.corredor.app.tema

// Generado por herramientas/simbolos.sh a partir de herramientas/simbolos.txt: no editar a mano.
// Cada nombre de SF Symbol que usa la app de iPhone, con su equivalente de Material Symbols Rounded.
internal object Simbolos {{
    val mapa: Map<String, Char> = mapOf(
{lineas}
    )
}}
''')
print(','.join('U+' + cp[m] for _, m in pares))
