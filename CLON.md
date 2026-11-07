# Corredor Android: reglas del clon (léelas enteras antes de escribir)

Objetivo (Nahuel): Corredor para Android **idéntica** al iPhone, completa, sin nada que falte.

- **Fuente de verdad:** `~/Trabajo/vimo/corredor-ios` (SwiftUI). Lo que hace el iPhone, lo hace Android; si el
  iPhone hace algo raro, Android hace lo mismo. Los mismos textos **letra por letra**, los mismos endpoints `/v1/…`,
  los mismos estados (cargando, vacío, error, sin conexión), roles (`estado.escribe`, `estado.esDuenio`…), avisos con
  «Deshacer», hojas, menús y confirmaciones.
- **Un `.kt` por cada `.swift`**, mismo nombre, carpeta equivalente en minúscula, paquete `ar.com.corredor.app`:
  `Corredor/Pantallas/Cobranzas/AltaContrato.swift` → `app/src/main/java/ar/com/corredor/app/pantallas/cobranzas/AltaContrato.kt`
  (paquete `ar.com.corredor.app.pantallas.cobranzas`). `Corredor/Nucleo` → `nucleo`, `Corredor/Tema` → `tema`,
  `Corredor/App` → `app`, `Compartido` → `compartido`, `CorredorWidgets` → `widgets`.
- **Mismos nombres que en Swift:** cada `struct X: View` es un `@Composable fun X(...)` con los mismos parámetros;
  modelos `Codable` → `@Serializable data class` con los mismos campos; `enum` → `enum class` o `sealed`; funciones
  y helpers con el mismo nombre. Destinos (`struct AFichaPropiedad: Hashable`) → `@Serializable data class
  AFichaPropiedad(...)` en el mismo archivo que en Swift.
- **Tema propio, sin Material3.** Los mismos nombres que `Corredor/Tema`: `C.fondo`, `C.texto2`, `Estilo`,
  `.estilo(...)`, `BotonPrincipal`, `TarjetaSeccion`, `EstadoVacio`, `EstadoError`, `ChipFiltro`, `Segmentado`,
  `FondoApp`, `pantallaApilada`… Íconos por nombre de SF Symbol: `Icono("person.2.fill")`.
- **Equivalencias ya resueltas** (molde Tengo Libre / Tu Local Web, ver `referencia-tlw/` y
  `~/Trabajo/vimo/tengolibre/apps/android/GUIA-CLON-IOS.md` §3 y §5):
  - `@Observable Estado` + `.environment` → `class Estado` con `mutableStateOf` + `LocalEstado.current`.
  - `.task(id: estado.version)` → `LaunchedEffect(estado.version) { cargar() }`; `try? await api.get` →
    `estado.api.opcional<T>(ruta)`; `try await estado.api.get<T>(ruta, query)`.
  - `NavigationStack`/`navigationDestination` → `estado.abrir(destino)` + `app/Registro.kt`, que mapea cada destino
    a su pantalla (lo escribe el agente del núcleo con los nombres de las pantallas de Swift).
  - `.sheet`/detents → `Hoja(visible, alCerrar, alturas)`; `confirmationDialog` → `Confirmacion`; `Menu` →
    `MenuBoton`; `Picker` → `SelectorMenu`; `.segmented` → `Segmentado`; `DatePicker` → `RenglonFecha`/`RenglonHora`;
    `Toggle` → `Interruptor`; `Form`/`Section` → `Bloque`; `.refreshable` → `Refrescable`; TipKit → `Consejos`;
    Swift Charts → `tema/Graficos.kt`; `PhotosPicker` → `PickVisualMedia`; `fileImporter` → `OpenDocument`;
    `ShareLink` → `estado.compartirTexto`; Keychain → `Llavero`; `ColorPicker` → `SelectorDeColor`.
  - MapKit → mapa con osmdroid o una vista estática; PDFKit/impresos → `PdfDocument` de Android; Speech → 
    `SpeechRecognizer` es-AR; AVFoundation (audio) → `MediaRecorder`/`MediaPlayer`; cámara → CameraX.
- **Plataforma:** «Seguir con Apple» no va (sólo Google). «Face ID» → «tu huella o tu cara». «iPhone» →
  «teléfono»/«celular». Suscripción: Play Billing en vez de StoreKit (mismos ids). El resto, igual.
- **Lo que en el iPhone es exclusivo de Apple** (Live Activity, App Intents, Spotlight, widgets de pantalla
  bloqueada) tiene su equivalente Android: notificación en curso, `shortcuts.xml`, nada, Glance.
- **No se compila en la Mac** (nunca `gradlew`, ni emulador). Compila GitHub: repo `grupovimo/corredor-android`.
- Cosas que ya mordieron: `Api.opcional` y toda extensión que usen varias pantallas va en `nucleo/` o `tema/`, no
  en una pantalla; `LocalDensity.current.fontScale` (no `LocalFontScale`); en `BoxWithConstraints` anidado,
  `this@BoxWithConstraints.maxWidth`; las clases `@Serializable` locales dentro de funciones compilan; `ACTION_DIAL`
  para llamar; `Confirmacion` corre la acción antes de cerrar.
- **No toques archivos de otro agente.** Si necesitás algo compartido que no existe, escribilo en tu archivo como
  `private` con un comentario `// PARA EL NÚCLEO: …`, y anotalo en tu informe.
