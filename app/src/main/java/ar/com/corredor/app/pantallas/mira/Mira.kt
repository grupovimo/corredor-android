package ar.com.corredor.app.pantallas.mira

// Clon de Pantallas/Mira/Mira.swift.

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import ar.com.corredor.app.app.*
import ar.com.corredor.app.nucleo.*
import ar.com.corredor.app.pantallas.propiedades.ErrorConCampos
import ar.com.corredor.app.pantallas.propiedades.Propiedad
import ar.com.corredor.app.pantallas.propiedades.Senal
import ar.com.corredor.app.pantallas.propiedades.conCamposComo
import ar.com.corredor.app.pantallas.propiedades.diasCalendario
import ar.com.corredor.app.tema.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Duration
import java.time.Instant
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// Los destinos de MIRA (`.destinosMira()` en iOS; los mapea app/Registro.kt):
// - ACaptadas → Captadas()
// - ARevisionIds(ids) → RevisionIA(ids = ids)

/** Lo que MIRA leyó de un cartel (POST /v1/mira/extraer). */
@Serializable
data class LecturaMira(val datos: Datos, val proveedor: String? = null, val prueba: Boolean? = null) {
    @Serializable
    data class Datos(
        val operacion: String? = null,
        val tipo: String? = null,
        val precio: Double? = null,
        val moneda: String? = null,
        val telefono: String? = null,
        val inmobiliaria: String? = null,
        val direccion: String? = null,
        val ambientes: Int? = null,
        val dormitorios: Int? = null,
        val superficie: Double? = null,
        val notas: String? = null,
        val precioAlternativas: List<Double>? = null,
        val precioConfianza: String? = null,
        val persona: Boolean? = null,
    )
}

/** Una foto de MIRA: en cola (sin señal), leída para revisar o ya captada. */
@Serializable
data class FotoMira(
    val id: String = UUID.randomUUID().toString(),
    val archivo: String,
    val lat: Double? = null,
    val lng: Double? = null,
    val direccionGPS: String? = null,
    val creada: FechaApi = Instant.now(),
    var estado: String = "cola",
    var lectura: LecturaMira.Datos? = null,
    var archivoId: String? = null,
    var propiedadId: String? = null,
    var error: String? = null,
)

/** Las fotos de MIRA guardadas en el teléfono (la cola sin señal y lo captado), con su imagen en disco. */
class ColaMira private constructor() {
    companion object {
        val compartida = ColaMira()
    }

    private var cargada = false
    var fotos by mutableStateOf<List<FotoMira>>(emptyList())
        private set

    /** La foto que se pidió abrir en MIRA para revisarla (desde Captadas). */
    var abrir by mutableStateOf<String?>(null)
    private var contexto: Context? = null

    /** Lo que en iOS es la carpeta de Documentos: se le pasa el contexto la primera vez que se usa. */
    fun usar(ctx: Context): ColaMira {
        if (contexto == null) contexto = ctx.applicationContext
        if (!cargada) {
            cargada = true
            fotos = Guardado.leer<List<FotoMira>>("mira")?.first ?: emptyList()
        }
        return this
    }

    val carpeta: File? get() = contexto?.let { File(it.filesDir, "mira").apply { mkdirs() } }

    fun guardar(datos: ByteArray, lat: Double?, lng: Double?, direccion: String?): FotoMira {
        val nombre = UUID.randomUUID().toString().uppercase() + ".jpg"
        carpeta?.let { runCatching { File(it, nombre).writeBytes(datos) } }
        val f = FotoMira(archivo = nombre, lat = lat, lng = lng, direccionGPS = direccion)
        fotos = listOf(f) + fotos
        persistir()
        return f
    }

    fun actualizar(f: FotoMira) {
        if (fotos.any { it.id == f.id }) { fotos = fotos.map { if (it.id == f.id) f.copy() else it }; persistir() }
    }

    fun sacar(f: FotoMira) {
        fotos = fotos.filter { it.id != f.id }
        carpeta?.let { runCatching { File(it, f.archivo).delete() } }
        persistir()
    }

    fun archivoDe(f: FotoMira): File? = carpeta?.let { File(it, f.archivo) }?.takeIf { it.exists() }

    /** La imagen con la orientación aplicada. */
    fun imagen(f: FotoMira): Bitmap? = archivoDe(f)?.let { leerDerecha(it) }

    /** Lee lo que quedó en cola (al volver la señal o al abrir MIRA). */
    suspend fun leerPendientes(api: Api) {
        for (f in fotos.filter { it.estado == "cola" }) {
            val x = f.copy()
            try {
                MiraLector.leer(x, api, this)
            } catch (e: CancellationException) { throw e } catch (e: ErrorApi) {
                if (e.sinConexion) return
                x.estado = "error"
                x.error = e.detalle
            } catch (e: Exception) {}
            actualizar(x)
        }
    }

    private fun persistir() { Guardado.guardar(fotos, "mira") }
}

object MiraLector {
    @Serializable private data class Cuerpo(val archivoId: String)

    /** Sube la foto y la lee. Deja la foto en «leida» (para revisar), «persona» o «ilegible». Cambia `f` (inout en iOS). */
    suspend fun leer(f: FotoMira, api: Api, cola: ColaMira = ColaMira.compartida) {
        val datos = withContext(Dispatchers.IO) { cola.archivoDe(f)?.readBytes() } ?: return
        if (f.archivoId == null) {
            f.archivoId = api.subir(datos, nombre = f.archivo, tipo = "image/jpeg", uso = "foto").id
        }
        try {
            val l: LecturaMira = api.enviar("POST", "/v1/mira/extraer", Cuerpo(f.archivoId!!))
            f.lectura = l.datos
            f.estado = if (l.datos.persona == true) "persona" else "leida"
        } catch (e: ErrorApi) {
            if (e.codigo != "ilegible") throw e
            f.estado = "ilegible"
        }
    }
}

/** Lo que se revisa antes de captar (la ficha de la web: operación, tipo, precio, teléfono, dirección). */
data class FormMira(
    val operacion: String? = null,
    val tipo: String? = null,
    val ambientes: Int? = null,
    val dormitorios: Int? = null,
    val superficie: Double? = null,
    val precio: Double? = null,
    val moneda: String = "USD",
    val telefono: String? = null,
    val inmobiliaria: String? = null,
    val direccion: String = "",
    val barrio: String? = null,
    val ciudad: String? = null,
    val notas: String? = null,
    /** La dirección salió del GPS (el cartel no la tenía). */
    val gps: Boolean = false,
    val lat: Double? = null,
    val lng: Double? = null,
)

/** Un recuadro en proporciones de la foto (0…1), el CGRect de iOS. */
data class Recorte(val x: Float, val y: Float, val ancho: Float, val alto: Float) {
    companion object { val inicial = Recorte(0.07f, 0.14f, 0.58f, 0.36f) }
}

private enum class Fase { apuntando, leyendo, persona, revisar, nolee, captada, sinSenal }

private val operacionesMira = mapOf("venta" to "Venta", "alquiler" to "Alquiler", "temporario" to "Temporario")
private val tinta = Color(0xFF1A1405)
private val claroVerde = Color(0xFFCFE0D8)

/** 5A–5H MIRA: la cámara a pantalla completa con el visor oro. Saca la foto y la lee (barrido); si aparece una persona,
 *  se elige el recuadro del cartel y se lee sólo eso. Después, la revisión (o «Leímos la mitad» para completar lo que
 *  falta), captar como borrador (el dueño directo queda como contacto) y la pantalla «quedó captada». Sin señal, la foto
 *  queda en cola. */
@Composable
fun MiraRaiz() {
    val estado = LocalEstado.current
    val ctx = LocalContext.current
    val cola = remember { ColaMira.compartida.usar(ctx) }
    val camara = remember { CamaraMira() }
    val ubicacion = remember { Ubicacion() }
    var fase by remember { mutableStateOf(Fase.apuntando) }
    var foto by remember { mutableStateOf<FotoMira?>(null) }
    var captadas by remember { mutableStateOf(false) }
    var editando by remember { mutableStateOf<String?>(null) }
    var f by remember { mutableStateOf(FormMira()) }
    var alternativas by remember { mutableStateOf<List<Double>>(emptyList()) }
    var ilegible by remember { mutableStateOf(false) }
    var recorte by remember { mutableStateOf(Recorte.inicial) }
    var captando by remember { mutableStateOf(false) }
    var captada by remember { mutableStateOf<Pair<Propiedad, String?>?>(null) }
    var recortarSiempre by remember { mutableStateOf(Preferencias.bool("mira-recortar")) }
    val cerrar = { estado.miraAbierta = false }

    // .statusBarHidden()
    val vista = LocalView.current
    DisposableEffect(Unit) {
        val ventana = ctx.actividad()?.window
        val control = ventana?.let { WindowCompat.getInsetsController(it, vista) }
        control?.hide(WindowInsetsCompat.Type.statusBars())
        onDispose { control?.show(WindowInsetsCompat.Type.statusBars()) }
    }

    fun repetir() {
        foto = null
        captada = null
        f = FormMira()
        alternativas = emptyList()
        fase = Fase.apuntando
    }

    fun cargarAMano() {
        cerrar()
        estado.ir(Ruta(Pestana.propiedades, AAltaPropiedad()))
    }

    /** Pasa lo leído a la ficha y completa la dirección con el GPS (con barrio y ciudad) si el cartel no la tenía. */
    suspend fun completar(x: FotoMira) {
        ilegible = x.estado == "ilegible"
        var g = f
        x.lectura?.let { d ->
            g = g.copy(
                operacion = d.operacion, tipo = d.tipo, ambientes = d.ambientes, dormitorios = d.dormitorios, superficie = d.superficie,
                precio = d.precio, moneda = d.moneda ?: "USD", telefono = d.telefono, inmobiliaria = d.inmobiliaria,
                direccion = d.direccion ?: "", notas = d.notas,
            )
            val alt = d.precioAlternativas ?: emptyList()
            alternativas = if (alt.size >= 2) alt.take(3) else emptyList()
            if (alternativas.isNotEmpty()) g = g.copy(precio = null)
        }
        g = g.copy(lat = x.lat, lng = x.lng)
        val lat = x.lat
        val lng = x.lng
        if (g.direccion.isEmpty() && lat != null && lng != null) {
            val p = geocodificar(ctx, lat, lng)
            if (p != null) {
                val dir = listOfNotNull(p.thoroughfare, p.subThoroughfare).joinToString(" ")
                g = g.copy(direccion = dir, barrio = p.subLocality, ciudad = p.locality, gps = dir.isNotEmpty())
            }
        }
        f = g
        val faltan = listOf(g.operacion == null, g.precio == null && alternativas.isEmpty(), g.telefono == null).count { it }
        fase = if (ilegible || faltan >= 2) Fase.nolee else Fase.revisar
        if (fase == Fase.revisar && g.direccion.isEmpty()) editando = "direccion"
    }

    suspend fun leer(x0: FotoMira, recortada: Boolean) {
        val x = x0.copy()
        f = FormMira()
        alternativas = emptyList()
        ilegible = false
        fase = Fase.leyendo
        try {
            MiraLector.leer(x, estado.api, cola)
            if (recortada && x.estado == "persona") x.estado = "leida"
            cola.actualizar(x)
            foto = x.copy()
            if (x.estado == "persona") {
                recorte = Recorte.inicial
                fase = Fase.persona
                return
            }
            completar(x)
        } catch (e: CancellationException) { throw e } catch (e: Exception) {
            if (e is ErrorApi && e.sinConexion) fase = Fase.sinSenal
            else { estado.avisar(e); fase = Fase.apuntando }
        }
    }

    suspend fun procesar(datos: ByteArray) {
        Haptico.impacto()
        val jpg = withContext(Dispatchers.IO) { aJpeg(datos, 80) } ?: datos
        val x = cola.guardar(jpg, ubicacion.ultima?.latitude, ubicacion.ultima?.longitude, ubicacion.direccion)
        foto = x
        if (recortarSiempre) {
            recorte = Recorte.inicial
            fase = Fase.persona
            return
        }
        leer(x, recortada = false)
    }

    suspend fun disparar() {
        val d = camara.sacar(ctx) ?: return
        procesar(d)
    }

    /** Recorta el recuadro del cartel, lo guarda como la foto (lo único que se manda) y lo lee. */
    suspend fun leerRecorte() {
        val x = foto ?: return
        val i = withContext(Dispatchers.IO) { cola.imagen(x) } ?: return
        val r = recorte
        val d = withContext(Dispatchers.IO) {
            val izq = (r.x * i.width).roundToInt().coerceIn(0, i.width - 1)
            val arr = (r.y * i.height).roundToInt().coerceIn(0, i.height - 1)
            val an = (r.ancho * i.width).roundToInt().coerceIn(1, i.width - izq)
            val al = (r.alto * i.height).roundToInt().coerceIn(1, i.height - arr)
            runCatching { Bitmap.createBitmap(i, izq, arr, an, al).let { c -> ByteArrayOutputStream().use { s -> c.compress(Bitmap.CompressFormat.JPEG, 85, s); s.toByteArray() } } }.getOrNull()
        } ?: return
        val nueva = cola.guardar(d, x.lat, x.lng, x.direccionGPS)
        cola.sacar(x)
        foto = nueva
        leer(nueva, recortada = true)
    }

    /** Abre una foto de la cola ya leída (desde Captadas) para revisarla y captarla. */
    fun abrirPedida() {
        val id = cola.abrir ?: return
        val x = cola.fotos.firstOrNull { it.id == id } ?: return
        cola.abrir = null
        foto = x
        if (x.estado == "persona") {
            recorte = Recorte.inicial
            fase = Fase.persona
            return
        }
        f = FormMira()
        alternativas = emptyList()
        estado.tarea { completar(x) }
    }

    val listoParaCaptar = f.operacion != null && f.tipo != null && f.direccion.trim().length >= 3 && !(alternativas.isNotEmpty() && f.precio == null)

    @Serializable data class CuerpoCaptada(
        val archivoId: String? = null, val titulo: String = "", val tipo: String, val operacion: String, val precio: Double? = null,
        val moneda: String, val direccion: String, val barrio: String? = null, val ciudad: String? = null, val lat: Double? = null,
        val lng: Double? = null, val ambientes: Int? = null, val dormitorios: Int? = null, val superficie: Double? = null,
        val telefono: String? = null, val inmobiliaria: String? = null, val notas: String? = null,
    )
    @Serializable data class K(val nombre: String, val telefono: String, val tipo: String = "propietario", val origen: String = "MIRA")
    @Serializable data class Creado(val id: String)

    suspend fun captar() {
        val x = foto?.copy() ?: return
        if (!listoParaCaptar || captando) return
        captando = true
        val envio = UUID.randomUUID().toString().uppercase()
        val dir = f.direccion.trim()
        try {
            val c = CuerpoCaptada(
                archivoId = x.archivoId, tipo = f.tipo ?: "departamento", operacion = f.operacion ?: "venta", precio = f.precio, moneda = f.moneda,
                direccion = dir, barrio = f.barrio, ciudad = f.ciudad, lat = f.lat, lng = f.lng, ambientes = f.ambientes, dormitorios = f.dormitorios,
                superficie = f.superficie, telefono = f.telefono, inmobiliaria = f.inmobiliaria, notas = null,
            )
            var p: Propiedad = estado.api.conCamposComo<Propiedad, CuerpoCaptada>("POST", "/v1/mira/propiedad", c, envio = envio)
            // El dueño directo (cartel sin inmobiliaria) queda como contacto, colgado de la propiedad.
            var contacto: String? = null
            val t = f.telefono
            if (t != null && f.inmobiliaria == null) {
                try {
                    val k: Creado = estado.api.conCamposComo<Creado, K>("POST", "/v1/contactos", K("Dueño de $dir", t), envio = "$envio-c")
                    contacto = k.id
                    p = p.copy(propietarioId = k.id)
                    estado.api.hacer("PUT", "/v1/propiedades/${p.id}", p)
                } catch (e: CancellationException) { throw e } catch (e: Exception) {
                    // La propiedad ya está; el contacto se carga después.
                }
            }
            x.estado = "captada"
            x.propiedadId = p.id
            cola.actualizar(x)
            foto = x.copy()
            estado.version += 1
            captada = p to contacto
            fase = Fase.captada
            Haptico.exito()
            val id = p.id
            val vuelta = x.copy()
            val elContacto = contacto
            estado.avisar("Captada en tu cartera") {
                estado.tarea {
                    try {
                        estado.api.hacer("DELETE", "/v1/propiedades/$id")
                        if (elContacto != null) {
                            try { estado.api.hacer("DELETE", "/v1/contactos/$elContacto") } catch (e: CancellationException) { throw e } catch (e: Exception) { estado.avisar(e) }
                        }
                        val y = vuelta.copy()
                        y.estado = "leida"
                        y.propiedadId = null
                        cola.actualizar(y)
                        captada = null
                        fase = Fase.revisar
                        estado.version += 1
                    } catch (e: CancellationException) { throw e } catch (e: Exception) { estado.avisar(e) }
                }
            }
        } catch (e: CancellationException) { throw e } catch (e: ErrorConCampos) {
            estado.avisar(e.campos.values.firstOrNull() ?: e.error.detalle, ok = false)
        } catch (e: Exception) {
            if (e is ErrorApi && e.sinConexion) fase = Fase.sinSenal else estado.avisar(e)
        } finally { captando = false }
    }

    val elegir = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { u: Uri? ->
        if (u == null) return@rememberLauncherForActivityResult
        estado.tarea {
            val d = withContext(Dispatchers.IO) { runCatching { ctx.contentResolver.openInputStream(u)?.use { it.readBytes() } }.getOrNull() }
            if (d != null) procesar(d) else estado.avisar("Algo salió mal. Probá de nuevo.", ok = false)
        }
    }
    val elegirFoto = { elegir.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }

    LaunchedEffect(Unit) {
        camara.arrancar(ctx) { estado.alPedirPermiso(Manifest.permission.CAMERA) }
        ubicacion.pedir(ctx) { estado.alPedirPermiso(Manifest.permission.ACCESS_FINE_LOCATION) }
        cola.leerPendientes(estado.api)
        abrirPedida()
    }
    LaunchedEffect(cola.abrir) { abrirPedida() }
    // .sensoryFeedback(.success, trigger: fase == .revisar)
    // (suena cuando cambia el valor: al entrar a la revisión y al salir de ella, como en iOS)
    val enRevision = fase == Fase.revisar
    var revisionAntes by remember { mutableStateOf(enRevision) }
    LaunchedEffect(enRevision) { if (enRevision != revisionAntes) { revisionAntes = enRevision; Haptico.exito() } }

    // Captadas, apilada sobre la cámara (navigationDestination(isPresented:) del NavigationStack de MIRA).
    // La raíz de MIRA no tiene título: el «‹» vuelve sin nombre y la barra muestra el título de Captadas
    // (.pantallaApilada("Captadas")), como la barra nativa de la pila.
    val barraCaptadas = remember { Barra() }
    androidx.activity.compose.BackHandler(captadas) { captadas = false }
    if (captadas) {
        CompositionLocalProvider(LocalBarra provides barraCaptadas, LocalCerrarHoja provides { captadas = false }) {
            Column(Modifier.fillMaxSize().background(C.fondo)) {
                BarraNavegacion(barraCaptadas, atras = null) { captadas = false }
                Box(Modifier.weight(1f)) { Captadas() }
            }
        }
        return
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF050807))) {
        val fotoImagen = imagenDe(cola, foto)
        if (camara.permiso == false) {
            SinPermiso(elegirFoto, { cargarAMano() }, cerrar)
        } else if (fase == Fase.persona && foto != null && fotoImagen != null) {
            VistaRecorte(fotoImagen, recorte) { recorte = it }
            HojaPersona({ repetir() }) { estado.tarea { leerRecorte() } }
        } else if (fase == Fase.captada && captada != null) {
            val c = captada!!
            PantallaCaptada(c.first, c.second, fotoImagen, f, { repetir() }) {
                estado.miraAbierta = false
                estado.ir(Ruta(Pestana.propiedades, AAltaPropiedad(editar = c.first.id)))
            }
        } else {
            if (camara.permiso == true) VistaCamara(camara, Modifier.fillMaxSize())
            if (foto != null && fase != Fase.apuntando && fotoImagen != null) {
                // En un marco del tamaño de la pantalla: la foto (más ancha) no estira el resto.
                Image(fotoImagen.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
            VisorMira(fase == Fase.leyendo)
            Arriba(
                flash = camara.flash,
                alCerrar = { if (fase == Fase.apuntando) cerrar() else repetir() },
                alFlash = { camara.flash = !camara.flash },
            )
            if (fase == Fase.apuntando || fase == Fase.leyendo) {
                Pista(fase == Fase.leyendo, recortarSiempre) {
                    recortarSiempre = !recortarSiempre
                    Preferencias.ponerBool("mira-recortar", recortarSiempre)
                    Haptico.seleccion()
                }
                Controles(
                    enCola = cola.fotos.count { it.estado != "captada" }, leyendo = fase == Fase.leyendo,
                    galeria = elegirFoto, disparar = { estado.tarea { disparar() } }, verCaptadas = { captadas = true },
                )
            }
            if (fase == Fase.leyendo) {
                Column(Modifier.fillMaxSize()) {
                    Spacer(Modifier.height(528.dp))
                    FlowChips(
                        listOfNotNull(
                            f.operacion?.let { "tag" to Formato.mayuscula(it) }, f.precio?.let { "dollarsign" to Formato.plata(it, f.moneda) },
                            f.telefono?.let { "phone" to it }, f.ambientes?.let { "bed.double" to "$it amb" },
                        ),
                        Modifier.padding(horizontal = 24.dp),
                    )
                }
            }
            when (fase) {
                Fase.revisar, Fase.nolee -> Revision(
                    nolee = fase == Fase.nolee, ilegible = ilegible, f = f, alternativas = alternativas, listo = listoParaCaptar, captando = captando,
                    editar = { editando = it }, elegirPrecio = { f = f.copy(precio = it) },
                    captar = { estado.tarea { captar() } }, repetir = { repetir() },
                )
                Fase.sinSenal -> AvisoMira(
                    "icloud.slash", Tono.neutro, "Sin señal. La foto quedó en cola.", "La leemos sola cuando vuelva la señal y te avisamos. Podés seguir captando.",
                    "Seguir captando" to { repetir() }, "Ver la cola" to { captadas = true },
                )
                else -> {}
            }
        }
    }

    val ed = editando
    Hoja(ed != null, { editando = null }, alturas = listOf(Altura.media)) {
        if (ed != null) EditorCampoMira(ed, f, { f = it }, alternativas, cerrar = { editando = null })
    }
}

/** La imagen de una foto de la cola, leída del disco sin trabar la pantalla. */
@Composable
private fun imagenDe(cola: ColaMira, f: FotoMira?): Bitmap? {
    val v by produceState<Bitmap?>(null, f?.archivo) { value = f?.let { withContext(Dispatchers.IO) { cola.imagen(it) } } }
    return v
}

// MARK: Partes

/** El visor oro (`visor` en iOS): la cámara oscurecida al 52 % salvo el hueco de radio 24 (36 de cada lado, 210 desde
 *  arriba y 300 de alto), las cuatro esquinas oro de 6 y, mientras lee, el barrido que sube y baja (1,7 s). No toma
 *  toques. */
@Composable
private fun VisorMira(leyendo: Boolean) {
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
        val ancho = this@BoxWithConstraints.maxWidth - 72.dp
        val alto = 300.dp
        Canvas(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
            drawRect(Color.Black.copy(alpha = 0.52f))
            drawRoundRect(
                Color.Black, topLeft = Offset(36.dp.toPx(), 210.dp.toPx()), size = Size(ancho.toPx(), alto.toPx()),
                cornerRadius = CornerRadius(24.dp.toPx()), blendMode = BlendMode.Clear,
            )
        }
        Box(Modifier.offset(33.dp, 207.dp).size(ancho + 6.dp, alto + 6.dp)) { Visor(Modifier.fillMaxSize(), color = C.oro, grosor = 6.dp) }
        if (leyendo) {
            val t = rememberInfiniteTransition(label = "barrido")
            val y by t.animateFloat(6f, 282f, infiniteRepeatable(tween(1700, easing = androidx.compose.animation.core.CubicBezierEasing(0.42f, 0f, 0.58f, 1f)), RepeatMode.Reverse), label = "y")
            Box(
                Modifier.offset(50.dp, 208.dp + y.dp).size(ancho - 28.dp, 4.dp)
                    .shadow(15.dp, Capsula, ambientColor = C.oro.copy(alpha = 0.55f), spotColor = C.oro.copy(alpha = 0.55f))
                    .clip(Capsula).background(C.oro),
            )
        }
    }
}

@Composable
private fun Arriba(flash: Boolean, alCerrar: () -> Unit, alFlash: () -> Unit) {
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            BotonRedondoMira("xmark", "Cerrar MIRA", alCerrar)
            Spacer(Modifier.weight(1f))
            Row(
                Modifier.height(36.dp).clip(Capsula).background(Color.Black.copy(alpha = 0.45f)).padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Visor(Modifier.size(16.dp), color = C.oro, grosor = 2.dp)
                Texto("MIRA", Estilo(15f, Peso.bold, 0.9f / 15f, Relativo.subheadline), Color.White)
            }
            Spacer(Modifier.weight(1f))
            BotonRedondoMira(if (flash) "bolt.fill" else "bolt.slash", if (flash) "Apagar el flash" else "Prender el flash", alFlash)
        }
    }
}

/** «Fotografiá sólo el cartel, sin personas»: tocándolo, se elige siempre el recorte del cartel. */
@Composable
private fun Pista(leyendo: Boolean, recortarSiempre: Boolean, alTocar: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(top = 100.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        val contenido: @Composable () -> Unit = {
            Row(
                Modifier.height(36.dp).clip(Capsula).background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val (t, i) = if (leyendo) "Leyendo el cartel…" to "sparkles"
                else if (recortarSiempre) "Vas a elegir el recorte del cartel" to "crop" else "Fotografiá sólo el cartel, sin personas" to "person.crop.circle.badge.xmark"
                Icono(i, 14f, Color.White)
                Texto(t, Estilo(14f, Peso.semibold, relativo = Relativo.footnote), Color.White, lineas = 1)
            }
        }
        if (leyendo) contenido() else Tocable(alTocar, Modifier.semantics { selected = recortarSiempre }) { _ -> contenido() }
    }
}

@Composable
private fun Controles(enCola: Int, leyendo: Boolean, galeria: () -> Unit, disparar: () -> Unit, verCaptadas: () -> Unit) {
    Column(Modifier.fillMaxSize().navigationBarsPadding()) {
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth().padding(horizontal = 48.dp).padding(bottom = 44.dp), verticalAlignment = Alignment.CenterVertically) {
            Tocable(galeria, descripcion = "Elegir una foto de la galería") { _ ->
                Box(
                    Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF212121)).border(2.dp, Color.White.copy(alpha = 0.8f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center,
                ) { Icono("photo", 17f, Color.White) }
            }
            Spacer(Modifier.weight(1f))
            Tocable(disparar, habilitado = !leyendo, descripcion = "Sacar foto del cartel") { _ ->
                Box(Modifier.size(80.dp).border(5.dp, Color.White, CircleShape).padding(8.dp).clip(CircleShape).background(Color.White).alpha(if (leyendo) 0.6f else 1f))
            }
            Spacer(Modifier.weight(1f))
            Tocable(verCaptadas, descripcion = "Captadas") { _ ->
                Box(Modifier.size(52.dp)) {
                    Box(Modifier.fillMaxSize().clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) { Icono("tray.full", 22f, Color.White) }
                    if (enCola > 0) {
                        Box(Modifier.align(Alignment.TopEnd).offset(2.dp, (-2).dp).size(20.dp).clip(CircleShape).background(C.oro), contentAlignment = Alignment.Center) {
                            Texto("$enCola", Estilo(11f, Peso.bold, relativo = Relativo.caption2), tinta)
                        }
                    }
                }
            }
        }
    }
}

// MARK: Persona en la foto

@Composable
private fun HojaPersona(sacarOtra: () -> Unit, leerRecorte: () -> Unit) {
    Column(Modifier.fillMaxSize().navigationBarsPadding()) {
        Spacer(Modifier.weight(1f))
        Column(
            Modifier.padding(horizontal = 16.dp).padding(bottom = 30.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(C.superficie).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icono("person.crop.circle.badge.xmark", 22f, C.avisoTinta)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Texto("Apareció una persona", Estilo.headline, C.texto)
                    Texto(Formato.sinViudas("Para cuidar su privacidad mandamos a leer sólo el recuadro del cartel. Podés ajustarlo."), Estilo.subhead, C.texto2)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BotonHundido("Sacar otra", sacarOtra, Modifier.weight(1f))
                BotonAccion("Leer el recorte", leerRecorte, Modifier.weight(1f))
            }
        }
    }
}

// MARK: Revisión

@Composable
private fun Revision(
    nolee: Boolean, ilegible: Boolean, f: FormMira, alternativas: List<Double>, listo: Boolean, captando: Boolean,
    editar: (String) -> Unit, elegirPrecio: (Double) -> Unit, captar: () -> Unit, repetir: () -> Unit,
) {
    val dudaPrecio = alternativas.isNotEmpty() && f.precio == null
    val precioTexto = f.precio?.let { Formato.plata(it, f.moneda) } ?: if (alternativas.isNotEmpty()) alternativas.joinToString(" o ") { Formato.plata(it, f.moneda) } else null
    val tipoTexto = f.tipo?.let { t -> listOfNotNull(Propiedad.tipos.firstOrNull { it.first == t }?.second ?: t, f.ambientes?.let { "$it amb." }).joinToString(" · ") }
    val direccionTexto = listOfNotNull(f.direccion.trim().ifEmpty { null }, f.barrio).joinToString(", ")
    val titulo = Estilo(22f, Peso.bold, -0.02f, Relativo.title2)
    Column(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f))) {
        Spacer(Modifier.weight(1f))
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)).background(C.superficie)
                .navigationBarsPadding().padding(horizontal = 16.dp).padding(top = 8.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Box(Modifier.size(36.dp, 5.dp).clip(Capsula).background(C.linea2)) }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (!nolee) {
                    Texto("Revisá lo que leímos", titulo, C.texto)
                    Texto("tocá para corregir", Estilo.footnote, C.texto2)
                } else if (ilegible && f.telefono == null) {
                    Texto("No pudimos leer el cartel", titulo, C.texto)
                    Texto(Formato.sinViudas("Probá con otra foto, más de frente, o completá lo que falta ahora o después."), Estilo.footnote, C.texto2)
                } else {
                    Texto("Leímos la mitad", titulo, C.texto)
                    Texto(Formato.sinViudas("La foto salió movida. Tenemos ${if (f.telefono != null) "el teléfono" else "una parte"}; el resto lo podés completar ahora o después."), Estilo.footnote, C.texto2)
                }
            }
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.hundida)) {
                FilaRevision("Operación", f.operacion?.let { operacionesMira[it] } ?: "Elegí", duda = f.operacion == null, primera = true) { editar("operacion") }
                FilaRevision("Tipo", tipoTexto ?: "Elegí", duda = f.tipo == null) { editar("tipo") }
                FilaRevision("Precio", precioTexto ?: (if (nolee) "No figura en el cartel" else "Sin precio"), duda = dudaPrecio, nota = if (dudaPrecio) "No se lee bien" else null) { editar("precio") }
                if (dudaPrecio) {
                    Row(Modifier.padding(horizontal = 14.dp).padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        alternativas.forEach { n -> BotonTonal(Formato.plata(n, f.moneda), { elegirPrecio(n) }, alto = 32.dp) }
                    }
                }
                FilaRevision("Teléfono", f.telefono ?: "Sin teléfono", duda = false, nota = if (f.telefono == null) null else (f.inmobiliaria ?: "dueño directo")) { editar("telefono") }
                FilaRevision("Dirección", direccionTexto.ifBlank { "Calle y altura" }, duda = f.direccion.trim().length < 3, nota = if (f.gps) "GPS" else null) { editar("direccion") }
            }
            if (!nolee) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Top) {
                    Icono("info.circle", 13f, C.texto2)
                    Texto("MIRA lee con Google Gemini y puede equivocarse. Revisá los datos antes de captar.", Estilo.footnote, C.texto2)
                }
            }
            Tocable(captar, habilitado = listo && !captando) { _ ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 54.dp).clip(RoundedCornerShape(15.dp)).background(C.accion.copy(alpha = if (listo) 1f else 0.45f)),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (captando) Rueda(C.sobreAccion) else Icono("house.badge.plus", 17f, C.sobreAccion)
                    Texto(if (nolee) "Captar con lo que hay" else if (dudaPrecio) "Elegí el precio para captar" else "Captar", Estilo(17f, Peso.bold, relativo = Relativo.headline), C.sobreAccion)
                }
            }
            Tocable(repetir, Modifier.fillMaxWidth()) { _ ->
                Box(Modifier.fillMaxWidth().heightIn(min = 44.dp), contentAlignment = Alignment.Center) {
                    Texto(if (nolee) "Sacar otra foto" else "Sacar de nuevo", Estilo.subheadFuerte, C.accion)
                }
            }
        }
    }
}

@Composable
private fun FilaRevision(k: String, v: String, duda: Boolean, nota: String? = null, primera: Boolean = false, alTocar: () -> Unit) {
    Column {
        if (!primera) Box(Modifier.fillMaxWidth().height(0.5.dp).background(C.linea))
        Tocable(alTocar) { _ ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Texto(k, Estilo.texto14, C.texto2, Modifier.width(86.dp))
                Texto(v, Estilo(16f, Peso.semibold), C.texto, Modifier.weight(1f), lineas = 1, cortarAlMedio = true)
                if (nota != null) {
                    Box(Modifier.height(24.dp).clip(Capsula).background(if (duda) C.avisoFondo else C.superficie).padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
                        Texto(nota, Estilo(12f, Peso.bold, relativo = Relativo.caption), if (duda) C.avisoTinta else C.texto2)
                    }
                } else if (duda) {
                    Box(Modifier.height(24.dp).clip(Capsula).background(C.avisoFondo).padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
                        Texto("Revisá", Estilo(12f, Peso.bold, relativo = Relativo.caption), C.avisoTinta)
                    }
                }
                Icono("pencil", 17f, C.texto3)
            }
        }
    }
}

// MARK: Captada

@Composable
private fun PantallaCaptada(p: Propiedad, contacto: String?, imagen: Bitmap?, f: FormMira, captarOtro: () -> Unit, completarFicha: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        FondoApp()
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Column(Modifier.fillMaxWidth().padding(top = 60.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(96.dp).clip(CircleShape).background(C.oro), contentAlignment = Alignment.Center) { Icono("house.badge.plus", 38f, tinta, peso = Peso.semibold) }
                Texto("${p.direccion ?: "La propiedad"} quedó captada", Estilo.titulo3, C.texto, alinear = TextAlign.Center)
                Texto(
                    Formato.sinViudas("Está como borrador en tu cartera, no se publica hasta que la completes.${if (contacto != null) " El dueño directo quedó cargado como contacto." else ""}"),
                    Estilo.subhead, C.texto2, alinear = TextAlign.Center,
                )
            }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.superficie).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)).background(C.hundida)) {
                    if (imagen != null) Image(imagen.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Texto("${listOfNotNull(p.tipoTexto, p.ambientes?.let { "$it amb" }).joinToString(" ")} · ${operacionesMira[p.operacion ?: ""] ?: ""}", Estilo.subheadFuerte, C.texto)
                    Texto(listOfNotNull(p.precio?.let { Formato.plata(it, p.moneda ?: "USD") } ?: "Sin precio", p.barrio).joinToString(" · "), Estilo.footnote, C.texto2)
                    val linea2 = C.linea2
                    Box(
                        Modifier.height(22.dp).drawWithDash(linea2).padding(horizontal = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) { Texto("Borrador", Estilo(12f, Peso.bold, relativo = Relativo.caption), C.texto2) }
                }
            }
            val t = f.telefono
            if (t != null && f.inmobiliaria == null) {
                BotonAccion("Escribirle al dueño", { Afuera.whatsapp((if (t.startsWith("+")) "" else "549") + t.filter { it.isDigit() }) }, simbolo = "bubble.left.fill")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BotonHundido("Completar ficha", completarFicha, Modifier.weight(1f))
                BotonHundido("Captar otro", captarOtro, Modifier.weight(1f))
            }
        }
    }
}

/** La cápsula con borde punteado (StrokeStyle(lineWidth: 1, dash: [3, 2])). */
private fun Modifier.drawWithDash(color: Color): Modifier = this.drawBehind {
    drawRoundRect(
        color, size = size, cornerRadius = CornerRadius(size.height / 2),
        style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.dp.toPx()))),
    )
}

@Composable
private fun AvisoMira(icono: String, tono: Tono, titulo: String, texto: String, a: Pair<String, () -> Unit>, b: Pair<String, () -> Unit>?) {
    Column(Modifier.fillMaxSize().navigationBarsPadding()) {
        Spacer(Modifier.weight(1f))
        Column(
            Modifier.padding(horizontal = 16.dp).padding(bottom = 40.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(C.superficie).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                IconoTono(icono, tono, tam = 44.dp)
                Texto(titulo, Estilo(18f, Peso.bold, relativo = Relativo.headline), C.texto)
            }
            Texto(texto, Estilo.subhead, C.texto2)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BotonAccion(a.first, a.second, Modifier.weight(1f))
                if (b != null) BotonHundido(b.first, b.second, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SinPermiso(elegirFoto: () -> Unit, cargarAMano: () -> Unit, cerrar: () -> Unit) {
    val ctx = LocalContext.current
    Box(Modifier.fillMaxSize()) {
        FondoVerde(patron = 0.0)
        Column(
            Modifier.fillMaxSize().padding(horizontal = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
        ) {
            Box(Modifier.size(84.dp).clip(RoundedCornerShape(26.dp)).background(C.oro.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                Icono("camera.badge.ellipsis", 38f, C.oro)
            }
            Texto("MIRA necesita la cámara", Estilo(24f, Peso.bold, -0.02f, Relativo.title2), Color.White, alinear = TextAlign.Center)
            // En iOS: «Ajustes › Corredor › Cámara»; en Android el permiso está en la ficha de la app.
            Texto("Activala en Ajustes › Apps › Corredor › Permisos › Cámara. Sólo la usamos para leer carteles.", Estilo.body16, claroVerde, alinear = TextAlign.Center)
            Tocable({
                runCatching {
                    ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }, Modifier.padding(top = 8.dp)) { _ ->
                Box(Modifier.height(52.dp).clip(RoundedCornerShape(14.dp)).background(C.oro).padding(horizontal = 26.dp), contentAlignment = Alignment.Center) {
                    Texto("Abrir Ajustes", Estilo(17f, Peso.bold, relativo = Relativo.headline), tinta)
                }
            }
            Tocable(elegirFoto) { _ -> Box(Modifier.height(44.dp), contentAlignment = Alignment.Center) { Texto("Elegir una foto", Estilo.subheadFuerte, claroVerde) } }
            Tocable(cargarAMano) { _ -> Box(Modifier.height(44.dp), contentAlignment = Alignment.Center) { Texto("Cargar a mano", Estilo.subheadFuerte, claroVerde) } }
            Tocable(cerrar) { _ -> Texto("Cerrar", Estilo.body, Color.White.copy(alpha = 0.7f)) }
        }
    }
}

@Composable
private fun BotonRedondoMira(icono: String, etiqueta: String, hacer: () -> Unit) {
    Tocable(hacer, descripcion = etiqueta) { _ ->
        Box(Modifier.size(44.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) { Icono(icono, 20f, Color.White, peso = Peso.semibold) }
    }
}

/** La foto con el recuadro del cartel: se mueve arrastrándolo y se agranda desde la esquina. Sólo eso se manda a leer. */
@Composable
fun VistaRecorte(imagen: Bitmap, recorte: Recorte, alCambiar: (Recorte) -> Unit) {
    val actual by androidx.compose.runtime.rememberUpdatedState(recorte)
    val cambiar by androidx.compose.runtime.rememberUpdatedState(alCambiar)
    BoxWithConstraints(Modifier.fillMaxSize().statusBarsPadding()) {
        val prop = imagen.width.toFloat() / max(1, imagen.height)
        val ancho = min((this@BoxWithConstraints.maxWidth - 32.dp).value, ((this@BoxWithConstraints.maxHeight - 260.dp).value * prop)).dp
        val alto = (ancho.value / prop).dp
        val densidad = LocalDensity.current
        val anchoPx = with(densidad) { ancho.toPx() }
        val altoPx = with(densidad) { alto.toPx() }
        val oro = C.oro
        Box(Modifier.align(Alignment.TopCenter).padding(top = 70.dp).size(ancho, alto)) {
            Image(imagen.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
            Canvas(Modifier.fillMaxSize().graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)) {
                drawRect(Color.Black.copy(alpha = 0.45f))
                drawRect(Color.Black, Offset(recorte.x * size.width, recorte.y * size.height), Size(recorte.ancho * size.width, recorte.alto * size.height), blendMode = BlendMode.Clear)
            }
            Box(
                Modifier.offset(ancho * recorte.x, alto * recorte.y).size(ancho * recorte.ancho, alto * recorte.alto)
                    .border(3.dp, oro, RoundedCornerShape(6.dp))
                    .pointerInput(anchoPx, altoPx) {
                        var inicio: Recorte? = null
                        var total = Offset.Zero
                        detectDragGestures(onDragStart = { inicio = actual; total = Offset.Zero }, onDragEnd = { inicio = null }, onDragCancel = { inicio = null }) { c, d ->
                            c.consume()
                            total += d
                            val r0 = inicio ?: actual
                            cambiar(r0.copy(
                                x = min(1 - r0.ancho, max(0f, r0.x + total.x / anchoPx)),
                                y = min(1 - r0.alto, max(0f, r0.y + total.y / altoPx)),
                            ))
                        }
                    }
                    .semantics { contentDescription = "Recuadro del cartel: arrastralo para moverlo" },
            ) {
                Box(Modifier.padding(6.dp).height(20.dp).clip(Capsula).background(oro).padding(horizontal = 6.dp), contentAlignment = Alignment.Center) {
                    Texto("Sólo esto se envía", Estilo(11f, Peso.bold, relativo = Relativo.caption2), tinta, lineas = 1)
                }
                Box(
                    Modifier.align(Alignment.BottomEnd).offset(10.dp, 10.dp).size(26.dp).clip(CircleShape).background(oro)
                        .pointerInput(anchoPx, altoPx) {
                            var inicio: Recorte? = null
                            var total = Offset.Zero
                            detectDragGestures(onDragStart = { inicio = actual; total = Offset.Zero }, onDragEnd = { inicio = null }, onDragCancel = { inicio = null }) { c, d ->
                                c.consume()
                                total += d
                                val r0 = inicio ?: actual
                                cambiar(r0.copy(
                                    ancho = min(1 - r0.x, max(0.15f, r0.ancho + total.x / anchoPx)),
                                    alto = min(1 - r0.y, max(0.12f, r0.alto + total.y / altoPx)),
                                ))
                            }
                        }
                        .semantics { contentDescription = "Agrandar el recuadro" },
                    contentAlignment = Alignment.Center,
                ) { Icono("arrow.up.left.and.arrow.down.right", 11f, Color.Black, peso = Peso.bold) }
            }
        }
    }
}

/** Los chips que aparecen mientras MIRA lee. */
@Composable
fun FlowChips(items: List<Pair<String, String>>, modifier: Modifier = Modifier) {
    Row(modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { (i, t) ->
            Row(
                Modifier.height(36.dp).clip(Capsula).background(Color(0xFFFFFDF8)).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icono(i, 15f, Color(0xFF18201D))
                Texto(t, Estilo(15f, Peso.bold, relativo = Relativo.subheadline), Color(0xFF18201D), lineas = 1)
            }
        }
    }
}

/** Corregir un campo de lo leído (operación, tipo con los ambientes, precio, teléfono, dirección). Va en una hoja. */
@Composable
fun EditorCampoMira(campo: String, f: FormMira, alCambiar: (FormMira) -> Unit, alternativas: List<Double>, cerrar: () -> Unit = LocalCerrarHoja.current) {
    BarraHoja("", derecha = { BotonBarra("Listo", fuerte = true) { cerrar() } })
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SeccionLista(null) {
            when (campo) {
                "operacion" -> {
                    val elegida = f.operacion ?: "venta"
                    listOf("venta" to "Venta", "alquiler" to "Alquiler", "temporario" to "Temporario").forEachIndexed { i, (k, t) ->
                        if (i > 0) Separador()
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { alCambiar(f.copy(operacion = k)); Haptico.seleccion() }.padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Texto(t, Estilo.body, C.texto, Modifier.weight(1f))
                            if (k == elegida) Icono("checkmark", 16f, C.accion)
                        }
                    }
                }
                "tipo" -> {
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Texto("Tipo", Estilo.body, C.texto, Modifier.weight(1f))
                        SelectorMenu(Propiedad.tipos.map { it.first to it.second }, f.tipo ?: "departamento", { alCambiar(f.copy(tipo = it)) }, descripcion = "Tipo")
                    }
                    Separador()
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Texto(f.ambientes?.let { "$it ambientes" } ?: "Ambientes", Estilo.body, C.texto, Modifier.weight(1f))
                        Pasador(f.ambientes ?: 0, { alCambiar(f.copy(ambientes = if (it == 0) null else it)) }, rango = 0..20)
                    }
                }
                "precio" -> {
                    var texto by remember { mutableStateOf(f.precio?.let { if (it == Math.floor(it)) it.toLong().toString() else it.toString() } ?: "") }
                    Box(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                        Segmentado(listOf("USD" to "USD", "ARS" to "ARS"), f.moneda, { alCambiar(f.copy(moneda = it)) }, Modifier.fillMaxWidth())
                    }
                    Separador()
                    Entrada(texto, { t ->
                        texto = t
                        alCambiar(f.copy(precio = t.replace(".", "").replace(',', '.').toDoubleOrNull()))
                    }, "Precio", Modifier.padding(horizontal = 16.dp, vertical = 14.dp), teclado = KeyboardType.Number)
                    alternativas.forEach { p ->
                        Separador()
                        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { alCambiar(f.copy(precio = p)); cerrar() }.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Texto("¿Era ${Formato.plata(p, f.moneda)}?", Estilo.body, C.accion)
                        }
                    }
                }
                "telefono" -> {
                    Entrada(f.telefono ?: "", { alCambiar(f.copy(telefono = it.takeIf { x -> x.isNotBlank() })) }, "11 5555-0000",
                        Modifier.padding(horizontal = 16.dp, vertical = 14.dp), teclado = KeyboardType.Phone)
                    Separador()
                    Entrada(f.inmobiliaria ?: "", { alCambiar(f.copy(inmobiliaria = it.takeIf { x -> x.isNotBlank() })) }, "Inmobiliaria del cartel (vacío: dueño directo)",
                        Modifier.padding(horizontal = 16.dp, vertical = 14.dp))
                }
                else -> {
                    Entrada(f.direccion, { alCambiar(f.copy(direccion = it, gps = false)) }, "Calle y altura", Modifier.padding(horizontal = 16.dp, vertical = 14.dp))
                    Separador()
                    Entrada(f.barrio ?: "", { alCambiar(f.copy(barrio = it.takeIf { x -> x.isNotBlank() })) }, "Barrio", Modifier.padding(horizontal = 16.dp, vertical = 14.dp))
                    Separador()
                    Entrada(f.ciudad ?: "", { alCambiar(f.copy(ciudad = it.takeIf { x -> x.isNotBlank() })) }, "Ciudad", Modifier.padding(horizontal = 16.dp, vertical = 14.dp))
                }
            }
        }
    }
}

/** Una captada de la bandeja (web: lib/propiedades/mira-captadas.ts). `deOtra`: la inmobiliaria del cartel: si es de
 *  otra, queda como dato de mercado. */
data class CaptadaMira(val p: Propiedad, val telefono: String?, val deOtra: String?, val faltanFotos: Boolean, val contactado: Boolean) {
    val id: String get() = p.id
    val sinLlamar: Boolean get() = deOtra == null && telefono != null && !contactado

    companion object {
        fun nota(p: Propiedad, prefijo: String): String? {
            val n = p.ficha?.get("notasInternas")?.texto ?: ""
            return n.split("\n").firstOrNull { it.startsWith(prefijo) }?.drop(prefijo.length)?.trim()?.ifEmpty { null }
        }
    }
}

private val semana: Duration = Duration.ofDays(7)

/** 5I Captadas: la cola sin señal, lo leído para revisar (se abre en MIRA para captarlo) y lo captado que sigue en la
 *  bandeja (sin publicar o de esta semana), con «Sin llamar» y «Faltan fotos». */
@Composable
fun Captadas() {
    val estado = LocalEstado.current
    val ctx = LocalContext.current
    val cerrar = LocalCerrarHoja.current
    val cola = remember { ColaMira.compartida.usar(ctx) }
    var delServidor by remember { mutableStateOf<List<CaptadaMira>>(emptyList()) }
    var error by remember { mutableStateOf<ErrorApi?>(null) }
    var cargado by remember { mutableStateOf(false) }
    var ver by remember { mutableStateOf<String?>(null) }
    var meta by remember { mutableStateOf<Int?>(null) }

    @Serializable data class Obj(val metrica: String, val meta: Double, val desde: String, val hasta: String)

    suspend fun traer() {
        try {
            val l: List<Propiedad> = estado.api.todas("/v1/propiedades", query = listOf("orden" to "actualizada"))
            val ahora = Instant.now()
            val ms = l.filter { it.deMira && (it.publicada != true || Duration.between(it.creadaEn ?: Instant.EPOCH, ahora) < semana) }
            val s: Map<String, Senal> = try { Senal.de(ms.map { it.id }, estado.api) } catch (e: CancellationException) { throw e } catch (e: Exception) { emptyMap() }
            delServidor = ms.map { p ->
                val x = s[p.id]
                CaptadaMira(
                    p, CaptadaMira.nota(p, "Teléfono del cartel:"), CaptadaMira.nota(p, "Cartel de:"),
                    faltanFotos = (p.fotos ?: emptyList()).size <= 1, contactado = (x?.interesados ?: 0) > 0 || x?.proximaVisita != null,
                )
            }
            error = null
            val hoy = Formato.diaISO(Instant.now())
            val os = estado.api.opcional<List<Obj>>("/v1/objetivos")
            if (os != null) meta = os.firstOrNull { it.metrica == "propiedades_captadas" && it.desde <= hoy && it.hasta >= hoy }?.let { it.meta.toInt() }
        } catch (e: CancellationException) { throw e } catch (e: ErrorApi) { error = e } catch (e: Exception) {}
        cargado = true
    }

    fun abrirFicha(id: String) {
        estado.abrir(AFichaPropiedad(id = id), en = Pestana.propiedades)
        estado.pestana = Pestana.propiedades
        estado.miraAbierta = false
    }

    /** Lo leído (o con persona, o ilegible) se abre en MIRA para revisarlo y captarlo. */
    fun abrir(f: FotoMira) {
        f.propiedadId?.let { abrirFicha(it); return }
        if (f.estado == "cola") return
        cola.abrir = f.id
        if (!estado.miraAbierta) estado.miraAbierta = true
        cerrar()
    }

    val visibles = when (ver) {
        "sin-llamar" -> delServidor.filter { it.sinLlamar }
        "sin-fotos" -> delServidor.filter { it.faltanFotos && it.deOtra == null }
        else -> delServidor
    }

    LaunchedEffect(estado.version) { cola.leerPendientes(estado.api); traer() }

    PantallaApilada("Captadas") {
        Box(Modifier.fillMaxSize()) {
            Refrescable({ cola.leerPendientes(estado.api); traer() }) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val ahora = Instant.now()
                    val estaSemana = delServidor.count { Duration.between(it.p.creadaEn ?: Instant.EPOCH, ahora) < semana }
                    Texto("$estaSemana esta semana${meta?.let { " · objetivo del mes $it" } ?: ""}", Estilo.subhead, C.texto2)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ChipFiltro("Todas · ${delServidor.size}", ver == null) { ver = null }
                        ChipFiltro("Sin llamar · ${delServidor.count { it.sinLlamar }}", ver == "sin-llamar") { ver = "sin-llamar" }
                        ChipFiltro("Faltan fotos · ${delServidor.count { it.faltanFotos && it.deOtra == null }}", ver == "sin-fotos") { ver = "sin-fotos" }
                    }
                    val enCola = cola.fotos.count { it.estado == "cola" }
                    if (enCola > 0) {
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.hundida).padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icono("icloud.and.arrow.up", 20f, C.texto2)
                            Texto("${Formato.cantidad(enCola, "foto en cola", "fotos en cola")} · se lee sola cuando vuelva la señal", Estilo.texto14, C.texto)
                        }
                    }
                    if (ver == null) {
                        cola.fotos.filter { it.estado != "captada" }.forEach { f -> FilaCola(cola, f, { abrir(f) }) { cola.sacar(f) } }
                    }
                    val err = error
                    if (err != null && delServidor.isEmpty()) {
                        Box(Modifier.padding(top = 20.dp)) {
                            EstadoVacio(
                                icono = "exclamationmark.triangle", titulo = "No pudimos traer lo captado", texto = if (err.sinConexion) "No hay internet." else "Probá de nuevo en un ratito.",
                                accion = "Reintentar" to { estado.tarea { traer() } },
                            )
                        }
                    }
                    visibles.forEach { c -> FilaServidor(c, { abrirFicha(c.p.id) }) }
                    if (cargado && err == null && cola.fotos.isEmpty() && delServidor.isEmpty()) {
                        Box(Modifier.padding(top = 30.dp)) {
                            EstadoVacio(
                                icono = "viewfinder", titulo = "Todavía no captaste carteles",
                                texto = "Fotografiá un cartel de «Vende» y MIRA lo carga por vos.",
                                accion = "Captar con MIRA" to { estado.miraAbierta = true; cerrar() },
                            )
                        }
                    } else if (cargado && delServidor.isNotEmpty() && visibles.isEmpty()) {
                        Texto(if (ver == "sin-llamar") "Ya llamaste a todos." else "Todas tienen fotos.", Estilo.subhead, C.texto2, Modifier.padding(top = 10.dp))
                    }
                    Tocable({ estado.miraAbierta = true; cerrar() }, Modifier.padding(top = 6.dp)) { _ ->
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(14.dp)).background(C.accion),
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icono("viewfinder", 17f, C.sobreAccion)
                            Texto("Captar otra", Estilo(17f, Peso.semibold, relativo = Relativo.headline), C.sobreAccion)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilaServidor(c: CaptadaMira, abrirFicha: () -> Unit) {
    val p = c.p
    Row(Modifier.fillMaxWidth().tarjeta().padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Tocable(abrirFicha, Modifier.weight(1f)) { _ ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(72.dp).clip(RoundedCornerShape(12.dp))) { Foto(p.miniatura, Modifier.fillMaxSize()) }
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Texto(p.direccion ?: p.nombre, Estilo(16f, Peso.bold), C.texto, lineas = 1)
                    Texto(listOfNotNull(p.precioTexto, p.barrio).joinToString(" · "), Estilo.footnote, C.texto2)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        val o = c.deOtra
                        if (o != null) ChipTono("Cartel de $o", Tono.neutro)
                        else if (c.contactado) ChipTono("Dueño contactado", Tono.verde)
                        else if (c.telefono != null) ChipTono("Sin llamar · ${max(0, diasCalendario(p.creadaEn ?: Instant.now(), Instant.now()))} días", Tono.rojo)
                        if (c.faltanFotos && c.deOtra == null) ChipTono("Faltan fotos", Tono.oro)
                    }
                }
            }
        }
        val t = c.telefono
        if (c.sinLlamar && t != null) {
            Tocable({ Afuera.llamar(t) }, descripcion = "Llamar al $t") { _ ->
                Box(Modifier.size(44.dp).clip(CircleShape).background(C.okFondo), contentAlignment = Alignment.Center) { Icono("phone.fill", 17f, C.okTinta) }
            }
        }
    }
}

@Composable
private fun ChipTono(t: String, tono: Tono) {
    Box(Modifier.height(24.dp).clip(Capsula).background(tono.fondo).padding(horizontal = 9.dp), contentAlignment = Alignment.Center) {
        Texto(t, Estilo(12f, Peso.bold, relativo = Relativo.caption), tono.tinta, lineas = 1)
    }
}

@Composable
private fun FilaCola(cola: ColaMira, f: FotoMira, abrir: () -> Unit, sacar: () -> Unit) {
    val (texto, icono, tono) = estadoCaptada(f)
    val imagen = imagenDe(cola, f)
    MenuContextual(listOf(OpcionDeMenu("Sacar de la lista", "trash", destructiva = true) { sacar() }), abrir) {
        Row(Modifier.fillMaxWidth().tarjeta().padding(10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(72.dp).clip(RoundedCornerShape(12.dp)).background(C.hundida)) {
                if (imagen != null) Image(imagen.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Texto(f.lectura?.direccion ?: f.direccionGPS ?: "Cartel sin dirección", Estilo(16f, Peso.bold), C.texto)
                Texto(listOfNotNull(f.lectura?.precio?.let { Formato.plata(it, f.lectura?.moneda ?: "USD") }, Formato.hace(f.creada)).joinToString(" · "), Estilo.footnote, C.texto2)
                Spacer(Modifier.height(2.dp))
                Row(
                    Modifier.height(24.dp).clip(Capsula).background(tono.fondo).padding(horizontal = 9.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icono(icono, 12f, tono.tinta)
                    Texto(texto, Estilo(12f, Peso.bold, relativo = Relativo.caption), tono.tinta, lineas = 1)
                }
            }
        }
    }
}

/** Captadas.estado en iOS: el texto, el ícono y el tono de cada foto de la cola. */
fun estadoCaptada(f: FotoMira): Triple<String, String, Tono> = when (f.estado) {
    "cola" -> Triple("En cola", "icloud.slash", Tono.neutro)
    "leida" -> Triple("Revisá antes del alta", "exclamationmark.circle", Tono.oro)
    "persona" -> Triple("Elegí el recorte del cartel", "crop", Tono.oro)
    "captada" -> Triple("Borrador", "checkmark.circle", Tono.verde)
    "ilegible" -> Triple("No se pudo leer · completalo", "aqi.medium", Tono.rojo)
    else -> Triple(f.error ?: "Con error", "exclamationmark.triangle", Tono.rojo)
}

// MARK: - Cámara y ubicación

/** La cámara trasera para MIRA (AVCaptureSession con foto en iOS; CameraX con ImageCapture acá). `permiso`: null sin
 *  pedir, true dado, false negado. */
class CamaraMira {
    var permiso by mutableStateOf<Boolean?>(null)
    var flash by mutableStateOf(false)
    val captura: ImageCapture = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build()
    internal var lista = false

    suspend fun arrancar(ctx: Context, pedir: suspend () -> Boolean) {
        val dado = ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        permiso = if (dado) true else pedir()
    }

    suspend fun sacar(ctx: Context): ByteArray? {
        if (permiso != true || !lista) return null
        captura.flashMode = if (flash) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
        val archivo = File(ctx.cacheDir, "mira-${System.currentTimeMillis()}.jpg")
        val ok = suspendCancellableCoroutine { c ->
            captura.takePicture(ImageCapture.OutputFileOptions.Builder(archivo).build(), ContextCompat.getMainExecutor(ctx), object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(r: ImageCapture.OutputFileResults) { if (c.isActive) c.resume(true) }
                override fun onError(e: ImageCaptureException) { if (c.isActive) c.resume(false) }
            })
        }
        if (!ok) return null
        return withContext(Dispatchers.IO) { runCatching { archivo.readBytes().also { archivo.delete() } }.getOrNull() }
    }
}

/** La vista previa de la cámara. */
@Composable
fun VistaCamara(camara: CamaraMira, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val ciclo = LocalLifecycleOwner.current
    val vista = remember { PreviewView(ctx).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    DisposableEffect(ciclo) {
        val futuro = ProcessCameraProvider.getInstance(ctx)
        futuro.addListener({
            val proveedor = futuro.get()
            val previa = Preview.Builder().build().also { it.surfaceProvider = vista.surfaceProvider }
            runCatching {
                proveedor.unbindAll()
                proveedor.bindToLifecycle(ciclo, CameraSelector.DEFAULT_BACK_CAMERA, previa, camara.captura)
                camara.lista = true
            }
        }, ContextCompat.getMainExecutor(ctx))
        onDispose {
            camara.lista = false
            runCatching { futuro.get().unbindAll() }
        }
    }
    AndroidView({ vista }, modifier)
}

/** La ubicación del teléfono (para ubicar el cartel y proponer la dirección). */
// CLLocationManager de iOS: los permisos ACCESS_FINE_LOCATION y ACCESS_COARSE_LOCATION están en el manifiesto.
class Ubicacion {
    var ultima by mutableStateOf<Location?>(null)
    var direccion by mutableStateOf<String?>(null)

    @SuppressLint("MissingPermission")
    suspend fun pedir(ctx: Context, permiso: suspend () -> Boolean) {
        val dado = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!dado && !permiso()) return
        val lm = ctx.getSystemService(LocationManager::class.java) ?: return
        val proveedor = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).firstOrNull { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) } ?: return
        val l: Location? = runCatching {
            if (Build.VERSION.SDK_INT >= 30) suspendCancellableCoroutine { c ->
                lm.getCurrentLocation(proveedor, null, ContextCompat.getMainExecutor(ctx)) { x -> if (c.isActive) c.resume(x) }
            } else lm.getLastKnownLocation(proveedor)
        }.getOrNull() ?: runCatching { lm.getLastKnownLocation(proveedor) }.getOrNull()
        l ?: return
        ultima = l
        val p = geocodificar(ctx, l.latitude, l.longitude)
        if (p != null) direccion = listOfNotNull(p.thoroughfare, p.subThoroughfare).joinToString(" ").ifBlank { null }
    }
}

/** CLGeocoder().reverseGeocodeLocation: calle, altura, barrio y ciudad de un punto. */
@Suppress("DEPRECATION")
private suspend fun geocodificar(ctx: Context, lat: Double, lng: Double): android.location.Address? = withContext(Dispatchers.IO) {
    if (!Geocoder.isPresent()) return@withContext null
    runCatching { Geocoder(ctx, Formato.argentina).getFromLocation(lat, lng, 1)?.firstOrNull() }.getOrNull()
}

// MARK: - Piezas de este archivo

/** UIImage.normalizada + jpegData: la imagen derecha (con la orientación aplicada) en JPEG. */
private fun aJpeg(datos: ByteArray, calidad: Int): ByteArray? = runCatching {
    val bmp = if (Build.VERSION.SDK_INT >= 28) {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(java.nio.ByteBuffer.wrap(datos))) { d, _, _ -> d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE }
    } else BitmapFactory.decodeByteArray(datos, 0, datos.size)
    ByteArrayOutputStream().use { s -> bmp.compress(Bitmap.CompressFormat.JPEG, calidad, s); s.toByteArray() }
}.getOrNull()

private fun leerDerecha(f: File): Bitmap? = runCatching {
    if (Build.VERSION.SDK_INT >= 28) ImageDecoder.decodeBitmap(ImageDecoder.createSource(f)) { d, _, _ -> d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE }
    else BitmapFactory.decodeFile(f.path)
}.getOrNull()
