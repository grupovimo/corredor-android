"""Arma las hojas iOS | Android de cada pantalla (claro y oscuro) y un index.html con las que faltan en Android."""
import os, sys, html
from PIL import Image, ImageDraw
sal = sys.argv[1]
ios = {f[:-4]: f for f in os.listdir(f'{sal}/ios') if f.endswith('.png')}
andr = {f[:-4]: f for f in os.listdir(f'{sal}/android') if f.endswith('.png')}
pantallas = sorted({n.rsplit('-', 1)[0] for n in ios} | {n.rsplit('-', 1)[0] for n in andr})
os.makedirs(f'{sal}/hojas', exist_ok=True)
ALTO = 1400
def abrir(lado, nombre):
    tabla = ios if lado == 'ios' else andr
    if nombre not in tabla: return None
    i = Image.open(f'{sal}/{lado}/{tabla[nombre]}').convert('RGB')
    return i.resize((int(i.width * ALTO / i.height), ALTO))
filas, faltan = [], []
for p in pantallas:
    imgs = [abrir(l, f'{p}-{t}') for t in ('claro', 'oscuro') for l in ('ios', 'android')]
    if imgs[1] is None and imgs[3] is None: faltan.append(p)
    ancho = 645
    hoja = Image.new('RGB', (ancho * 4 + 60, ALTO + 50), '#e9e7e2')
    d = ImageDraw.Draw(hoja)
    for k, (im, rot) in enumerate(zip(imgs, ['iOS claro', 'Android claro', 'iOS oscuro', 'Android oscuro'])):
        x = k * (ancho + 15) + (15 if k >= 2 else 0)
        d.text((x + 4, 8), rot, fill='#333')
        if im: hoja.paste(im, (x, 40))
        else: d.text((x + 200, ALTO // 2), 'falta', fill='#b42318')
    hoja.save(f'{sal}/hojas/{p}.jpg', quality=82)
    filas.append(p)
with open(f'{sal}/index.html', 'w') as f:
    f.write('<meta charset="utf-8"><title>iOS | Android</title><body style="font-family:system-ui;background:#f4eee2">')
    f.write(f'<h1>Tengo Libre: iOS | Android</h1><p>{len(pantallas) - len(faltan)} de {len(pantallas)} pantallas en Android. Faltan: {html.escape(", ".join(faltan))}</p>')
    for p in filas:
        f.write(f'<h2>{html.escape(p)}</h2><img src="hojas/{html.escape(p)}.jpg" style="max-width:100%">')
print(f'{len(pantallas) - len(faltan)}/{len(pantallas)} pantallas en Android; hojas en {sal}/hojas')
