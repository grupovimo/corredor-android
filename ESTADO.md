# Corredor para Android: estado y cómo retomar

Objetivo (Nahuel, 7/11/2026): Android **idéntica** al iPhone (`~/Trabajo/vimo/corredor-ios`), completa, sin nada
que falte. Nada sale a las tiendas hasta que esté. Reglas del clon: `CLON.md`. Molde: Tengo Libre y Tu Local Web
(`~/Trabajo/vimo/tengolibre/apps/android/GUIA-CLON-IOS.md`).

## Cómo se trabaja
- Repo propio `grupovimo/corredor-android` (público, Actions gratis), sin monorepo ni `publicar.sh`: se commitea y
  se empuja acá. Push con la cuenta `grupovimo`.
- Compila sólo GitHub (`.github/workflows/android.yml`): `assembleDebug` + `testDebugUnitTest`, y sube el artifact
  `capturas` (PantallasTest en claro y oscuro). «Run workflow» arma el paquete firmado para Play (artifact `play`).
- Secretos `CR_FIREBASE_*` (proyecto `grupo-vimo`) y `CR_FIRMA_JKS`/`CR_FIRMA_CLAVE`. Llave de subida:
  `~/Trabajo/vimo/credenciales-android/corredor-subida.{jks,clave}`, alias `corredor`, SHA-256
  `55:A8:E7:8B:5C:B1:41:E6:1E:06:96:23:E1:2F:A3:EB:8B:F1:98:6D:A8:4A:13:CC:3E:4B:4F:90:12:6F:05:C5` (está en el
  `assetlinks.json` de corredor.com.ar; sumar la de la firma de Play cuando exista la app).
- `referencia-tlw/` es el código de Tu Local Web que sirvió de molde: no compila y está en `.gitignore`.
- `herramientas/paridad.sh` escribe `paridad.txt` (los 89 `.swift` de Pantallas); `ParidadTest` exige su `.kt`.
- `app/src/test/resources/pruebas/respuestas.json`: las respuestas de `CorredorTests/PantallasTests.swift`.

## Hecho (7/11)
- Un `.kt` por cada `.swift` (App, Núcleo, Tema, Compartido, Widgets y las 89 pantallas), con los mismos nombres,
  textos y endpoints (comparados automáticamente: iguales salvo lo de plataforma).
- Dos repasadas de fidelidad (la primera con 4 auditores, la segunda a fondo con 11, cada uno leyendo enteros el
  `.swift` y el `.kt`): unas 200 diferencias corregidas. Bugs del iPhone que encontró el clon, corregidos allá:
  el rango de lotes y la nota de voz que quedaba en pausa (corredor-ios 4a445db); el «Llamar» del aviso de consulta
  (la API no mandaba el teléfono, crm-inmobiliaria a103c34).
- PantallasTest (156 pantallas × claro/oscuro, los nombres de iOS) y la navegación total por cada destino.
- API (rama `corredor` de crm-inmobiliaria, desplegada el 7/11): FCM sólo con datos para avisos y visita en curso
  (migración 00171), compra con Google Play `POST /v1/suscripcion/google` (00172, apagada hasta tener la cuenta de
  Play: `CORREDOR_PLAY_CUENTA` y `CORREDOR_PLAY_CLAVE_WEBHOOK`), teléfono en el aviso de consulta.

## Distinto a propósito (plataforma)
- Ingreso sólo con Google (sin Apple). «Face ID» → «tu huella o tu cara»; «iPhone» → «teléfono».
- Plan con Play Billing (mismos ids), textos de Google Play en vez de App Store.
- Live Activity → notificación en curso que arma la app con la cita; el push sólo la mueve o la termina.
- Siri y Spotlight: los 7 atajos van a `res/xml/shortcuts.xml` (el lanzador muestra 4); Spotlight no tiene par.
- MapKit → teselas de CARTO; PDFs con `PdfDocument`; la vista previa de archivos abre el visor del sistema.
- Nota de voz del inventario: Android no graba y transcribe a la vez (con reconocedor, sólo texto).
- Ordenar secciones de Mi web con flechas (Compose no tiene `onMove`); material translúcido → color al 94 %;
  la animación de las tarjetas al entrar en pantalla (`scrollTransition`) no está.

## Sin probar en un teléfono
Nunca corrió en un aparato real: ingreso con Clerk, push FCM, notificación en curso, cámara y escáner, dictado,
Play Billing, widgets y atajos. Con la prueba interna de Play.

## Falta
1. Cuenta de Play: crear la app, productos de suscripción, RTDN, cuenta de servicio, SHA-256 de Play en
   `assetlinks.json`, cliente OAuth Android de Google. Después, prueba interna.
2. La revisión de Nahuel con la galería de capturas.
