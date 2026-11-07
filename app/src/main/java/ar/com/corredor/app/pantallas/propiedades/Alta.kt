package ar.com.corredor.app.pantallas.propiedades

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import ar.com.corredor.app.app.BarraDe
import ar.com.corredor.app.app.BotonBarra
import ar.com.corredor.app.app.LocalEstado
import ar.com.corredor.app.nucleo.*
import ar.com.corredor.app.tema.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.put
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.text.Normalizer
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.concurrent.thread
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

// Clon de Pantallas/Propiedades/Alta.swift.

/** Sin acentos y en minúsculas (`folding(.diacriticInsensitive, .caseInsensitive)` de iOS). */
private fun sinAcentos(s: String): String =
    Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").lowercase(Formato.argentina)

/** Lo que se carga en el alta (y en el borrador que se guarda solo). En /v1/borradores va con el formato de la web
 *  (lib/propiedades/alta-modelo.ts), para seguirlo en cualquier aparato: las fotos como `[{id, url}]`, más título,
 *  comodidades y dónde publicar, que acá no se editan pero se respetan. */
@Serializable(with = DatosAltaSerializer::class)
data class DatosAlta(
    val operacion: String = "venta",
    val tipo: String = "departamento",
    val direccion: String = "",
    val barrio: String = "",
    val ciudad: String = "",
    val precio: Double? = null,
    val moneda: String = "USD",
    val expensas: Double? = null,
    val ambientes: Int = 2,
    val dormitorios: Int? = null,
    val banos: Int? = null,
    val cocheras: Int? = null,
    val superficie: Double? = null,
    val cubierta: Double? = null,
    val antiguedad: Int? = null,
    val aptoCredito: Boolean = false,
    val descripcion: String = "",
    val fotos: List<String> = emptyList(),
    val fotosUrl: List<String> = emptyList(),
    val propietarioId: String? = null,
    val propietarioNombre: String? = null,
    // Los de la web.
    val titulo: String = "",
    /** El título lo tocó la persona: ya no se arma solo de la descripción. */
    val tituloPropio: Boolean = false,
    val comodidades: List<String> = emptyList(),
    val sugeridas: List<String> = emptyList(),
    val publicar: Map<String, Boolean> = mapOf("web" to true, "mercadolibre" to true, "zonaprop" to true, "argenprop" to false),
    val audioId: String? = null,
    val audioSegundos: Double? = null,
) {
    constructor(p: Propiedad) : this(
        operacion = p.operacion ?: "venta",
        tipo = p.tipo ?: "departamento",
        direccion = p.direccion ?: "",
        barrio = p.barrio ?: "",
        ciudad = p.ciudad ?: "",
        precio = p.precio,
        moneda = p.moneda ?: "USD",
        expensas = p.expensas,
        ambientes = p.ambientes ?: 2,
        dormitorios = p.dormitorios,
        banos = p.banos,
        cocheras = p.cocheras,
        superficie = p.superficie,
        cubierta = p.cubierta,
        antiguedad = p.antiguedad,
        aptoCredito = p.aptoCredito ?: false,
        descripcion = p.descripcion ?: "",
        fotos = p.fotos ?: emptyList(),
        fotosUrl = p.fotosUrl ?: emptyList(),
        propietarioId = p.propietarioId,
        titulo = p.titulo ?: "",
        tituloPropio = true,
        comodidades = p.comodidades ?: emptyList(),
        sugeridas = p.comodidades ?: emptyList(),
        publicar = mapOf("web" to (p.publicada ?: true), "mercadolibre" to false, "zonaprop" to false, "argenprop" to false),
    )

    // MARK: El título (tituloSugerido y tituloFinal de la web)

    private val tipoCorto: String get() = if (tipo == "departamento") "Depto" else (Propiedad.tipos.firstOrNull { it.first == tipo }?.second ?: tipo)

    /** «3 amb al frente con balcón · Belgrano · apto crédito», armado de la descripción. */
    val tituloSugerido: String
        get() {
            val t = sinAcentos(descripcion)
            val cabeza = listOf(
                if (tipo == "departamento" || tipo == "ph") "$ambientes amb" else "$tipoCorto $ambientes amb",
                if (t.contains("al frente")) "al frente" else "",
                if (t.contains("balcon")) "con balcón" else if (t.contains("terraza")) "con terraza" else if (t.contains("patio")) "con patio" else "",
            ).filter { it.isNotEmpty() }.joinToString(" ")
            return listOf(cabeza, barrio.trim(), if (t.contains("apto credito")) "apto crédito" else "").filter { it.isNotEmpty() }.joinToString(" · ")
        }

    /** Un título que la API acepte (3 a 120 letras). */
    val tituloFinal: String
        get() {
            val t = (if (tituloPropio) titulo else titulo.nonEmpty ?: tituloSugerido).trim()
            if (t.length >= 3) return t.take(120)
            return "$tipoCorto · $direccion".take(120)
        }

    // MARK: Lo que va a la API

    /** El cuerpo de POST/PUT /v1/propiedades (propiedades.Datos). El PUT reemplaza todo: en la edición se parte de la
     *  propiedad entera (`base`) y se cambia sólo lo del alta, como cuerpo() de la web. Sin `base` (el alta), se publica
     *  en la web según `publicar`. */
    fun cuerpo(base: Propiedad? = null): CuerpoPropiedad {
        val b = base
        val periodo: String? = if (operacion == "alquiler") (b?.periodo ?: "mes") else if (operacion == "temporario") (b?.periodo ?: "noche") else null
        return CuerpoPropiedad(
            titulo = tituloFinal, slug = b?.slug, descripcion = descripcion.nonEmpty?.trim(),
            tipo = tipo, operacion = operacion, estado = b?.estado, precio = precio, moneda = moneda, periodo = periodo, expensas = expensas,
            precioOculto = b?.precioOculto, direccion = direccion.trim(),
            barrio = barrio.nonEmpty?.trim(), ciudad = ciudad.nonEmpty?.trim(),
            lat = b?.lat, lng = b?.lng, ambientes = ambientes, dormitorios = dormitorios, banos = banos, cocheras = cocheras,
            superficie = superficie, cubierta = cubierta, antiguedad = antiguedad,
            aptoCredito = if (b?.aptoCredito == null && !aptoCredito) null else aptoCredito, tambienTemporario = b?.tambienTemporario,
            comodidades = comodidades, caracteristicas = b?.caracteristicas, servicios = b?.servicios, fotos = fotos, video = b?.video,
            ficha = b?.ficha, destacada = b?.destacada, publicada = publicar["web"] ?: true,
            agenteId = b?.agenteId, propietarioId = propietarioId, emprendimientoId = b?.emprendimientoId,
        )
    }
}

/** El formato de la web. Tolerante al leer: el de la web, el de MIRA y el viejo de la app (fotos como ids sueltos y
 *  `fotosUrl`). */
object DatosAltaSerializer : KSerializer<DatosAlta> {
    override val descriptor: SerialDescriptor = JsonObject.serializer().descriptor

    override fun deserialize(decoder: Decoder): DatosAlta {
        val o = ((decoder as? JsonDecoder)?.decodeJsonElement() as? JsonObject) ?: return DatosAlta()
        fun prim(k: String) = o[k] as? JsonPrimitive
        fun s(k: String): String? = prim(k)?.takeIf { it.isString }?.content
        fun n(k: String): Double? = prim(k)?.takeIf { !it.isString }?.doubleOrNull
        fun i(k: String): Int? = n(k)?.takeIf { it == Math.floor(it) }?.toInt()
        fun b(k: String): Boolean? = prim(k)?.takeIf { !it.isString }?.booleanOrNull
        fun lista(k: String): List<String>? {
            val a = o[k] as? JsonArray ?: return null
            val r = a.map { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }
            return if (r.any { it == null }) null else r.filterNotNull()
        }
        val base = DatosAlta()
        var tipo = s("tipo") ?: base.tipo
        if (tipo == "terreno") tipo = "lote"
        val urls = lista("fotosUrl") ?: emptyList()
        val fotos = mutableListOf<String>()
        val fotosUrl = mutableListOf<String>()
        (o["fotos"] as? JsonArray)?.forEachIndexed { idx, f ->
            val obj = f as? JsonObject
            val id = (obj?.get("id") as? JsonPrimitive)?.takeIf { it.isString }?.content
            if (id != null && !id.startsWith("subiendo-")) {
                fotos.add(id)
                fotosUrl.add((obj["url"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: "")
            } else if (f is JsonPrimitive && f.isString) {
                fotos.add(f.content)
                fotosUrl.add(urls.getOrNull(idx) ?: "")
            }
        }
        val publicar = base.publicar.toMutableMap()
        (o["publicar"] as? JsonObject)?.let { m ->
            val v = m.mapValues { (it.value as? JsonPrimitive)?.takeIf { p -> !p.isString }?.booleanOrNull }
            if (v.values.none { it == null }) v.forEach { (k, x) -> publicar[k] = x!! }
        }
        return DatosAlta(
            operacion = s("operacion") ?: base.operacion,
            tipo = tipo,
            direccion = s("direccion") ?: "",
            barrio = s("barrio") ?: "",
            ciudad = s("ciudad") ?: "",
            precio = n("precio"),
            moneda = s("moneda") ?: base.moneda,
            expensas = n("expensas"),
            ambientes = i("ambientes") ?: base.ambientes,
            dormitorios = i("dormitorios"),
            banos = i("banos"),
            cocheras = i("cocheras"),
            superficie = n("superficie"),
            cubierta = n("cubierta"),
            antiguedad = i("antiguedad"),
            aptoCredito = b("aptoCredito") ?: false,
            descripcion = s("descripcion") ?: "",
            fotos = fotos,
            fotosUrl = fotosUrl,
            propietarioId = s("propietarioId"),
            propietarioNombre = s("propietarioNombre"),
            titulo = s("titulo") ?: "",
            tituloPropio = b("tituloPropio") ?: false,
            comodidades = lista("comodidades") ?: emptyList(),
            sugeridas = lista("sugeridas") ?: emptyList(),
            publicar = publicar,
            audioId = s("audioId"),
            audioSegundos = n("audioSegundos"),
        )
    }

    override fun serialize(encoder: Encoder, value: DatosAlta) {
        val d = value
        val o = buildJsonObject {
            put("operacion", d.operacion)
            put("tipo", d.tipo)
            put("direccion", d.direccion)
            put("barrio", d.barrio)
            put("precio", d.precio?.let { JsonPrimitive(it) } ?: JsonNull)
            put("moneda", d.moneda)
            put("ambientes", d.ambientes)
            put("fotos", JsonArray(d.fotos.zip(d.fotosUrl).map { (id, url) ->
                buildJsonObject { put("id", id); url.nonEmpty?.let { put("url", it) } }
            }))
            put("descripcion", d.descripcion)
            put("titulo", d.titulo)
            put("tituloPropio", d.tituloPropio)
            put("comodidades", JsonArray(d.comodidades.map { JsonPrimitive(it) }))
            put("sugeridas", JsonArray(d.sugeridas.map { JsonPrimitive(it) }))
            put("publicar", JsonObject(d.publicar.mapValues { JsonPrimitive(it.value) }))
            d.audioId?.let { put("audioId", it) }
            d.audioSegundos?.let { put("audioSegundos", it) }
            // Lo que la web no carga en el alta (la web lo deja pasar tal cual).
            d.ciudad.nonEmpty?.let { put("ciudad", it) }
            d.expensas?.let { put("expensas", it) }
            d.dormitorios?.let { put("dormitorios", it) }
            d.banos?.let { put("banos", it) }
            d.cocheras?.let { put("cocheras", it) }
            d.superficie?.let { put("superficie", it) }
            d.cubierta?.let { put("cubierta", it) }
            d.antiguedad?.let { put("antiguedad", it) }
            if (d.aptoCredito) put("aptoCredito", true)
            d.propietarioId?.let { put("propietarioId", it) }
            d.propietarioNombre?.let { put("propietarioNombre", it) }
        }
        (encoder as JsonEncoder).encodeJsonElement(o)
    }
}

/** POST/PUT /v1/propiedades: todos los campos de propiedades.Datos (lo que falta va como null). */
@Serializable
data class CuerpoPropiedad(
    val titulo: String,
    val slug: String? = null,
    val descripcion: String? = null,
    val tipo: String,
    val operacion: String,
    val estado: String? = null,
    val precio: Double? = null,
    val moneda: String,
    val periodo: String? = null,
    val expensas: Double? = null,
    val precioOculto: Boolean? = null,
    val direccion: String,
    val barrio: String? = null,
    val ciudad: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    val ambientes: Int? = null,
    val dormitorios: Int? = null,
    val banos: Int? = null,
    val cocheras: Int? = null,
    val superficie: Double? = null,
    val cubierta: Double? = null,
    val antiguedad: Int? = null,
    val aptoCredito: Boolean? = null,
    val tambienTemporario: Boolean? = null,
    val comodidades: List<String>? = null,
    val caracteristicas: List<String>? = null,
    val servicios: List<String>? = null,
    val fotos: List<String>,
    val video: String? = null,
    val ficha: JSONLibre? = null,
    val destacada: Boolean? = null,
    val publicada: Boolean? = null,
    val agenteId: String? = null,
    val propietarioId: String? = null,
    val emprendimientoId: String? = null,
)

/** Lo que se guarda en /v1/borradores: `{paso, datos}`, como la web (el paso es el del alta de la web, de 0 a 4; la app
 *  lo respeta para que allá se siga donde quedó). */
@Serializable(with = BorradorAltaSerializer::class)
data class BorradorAlta(val paso: Int = 0, val datos: DatosAlta = DatosAlta())

object BorradorAltaSerializer : KSerializer<BorradorAlta> {
    override val descriptor: SerialDescriptor = JsonObject.serializer().descriptor

    override fun deserialize(decoder: Decoder): BorradorAlta {
        val j = decoder as? JsonDecoder ?: return BorradorAlta()
        val o = j.decodeJsonElement() as? JsonObject ?: return BorradorAlta()
        val paso = (o["paso"] as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull?.toInt() ?: 0
        val datos = (o["datos"] as? JsonObject)?.let { runCatching { j.json.decodeFromJsonElement(DatosAltaSerializer, it) }.getOrNull() } ?: DatosAlta()
        return BorradorAlta(minOf(4, maxOf(0, paso)), datos)
    }

    override fun serialize(encoder: Encoder, value: BorradorAlta) {
        val j = encoder as JsonEncoder
        j.encodeJsonElement(buildJsonObject {
            put("paso", value.paso)
            put("datos", j.json.encodeToJsonElement(DatosAltaSerializer, value.datos))
        })
    }
}

/** Un borrador de GET /v1/borradores. */
@Serializable
data class BorradorPropiedad(
    val id: String,
    val datos: BorradorAlta = BorradorAlta(),
    val actualizadoEn: FechaApi? = null,
)

/** Las 12 comodidades del alta (web: COMODIDADES) con su ícono y cómo se nombran en una descripción. */
object ComodidadesAlta {
    data class Comodidad(val nombre: String, val icono: String, val patron: String)

    val todas: List<Comodidad> = listOf(
        Comodidad("Balcón", "sun.max", "balcon"), Comodidad("Cochera", "car", "cochera|garage"), Comodidad("Luminoso", "light.max", "luminos[oa]"),
        Comodidad("Apto mascotas", "pawprint", "mascotas?"), Comodidad("Pileta", "figure.pool.swim", "pileta|piscina"), Comodidad("Parrilla", "flame", "parrilla"),
        Comodidad("Amenities", "dumbbell", "amenities|gimnasio"), Comodidad("Apto crédito", "building.columns", "apto credito"), Comodidad("Lavadero", "washer", "lavadero"),
        Comodidad("Baulera", "shippingbox", "baulera"), Comodidad("A estrenar", "sparkles", "a estrenar"), Comodidad("Vista abierta", "mountain.2", "vista abierta"),
    )

    /** Las que se nombran en el texto (sin acentos), en orden de aparición. */
    fun nombradas(texto: String): List<String> {
        val t = sinAcentos(texto)
        val out = mutableListOf<Pair<String, Int>>()
        for (c in todas) {
            Regex(c.patron).find(t)?.let { out.add(c.nombre to it.range.first) }
        }
        return out.sortedBy { it.second }.map { it.first }
    }

    /** «una», «dos»… para «Marcamos dos que nombraste». */
    fun enLetras(n: Int): String =
        listOf("cero", "una", "dos", "tres", "cuatro", "cinco", "seis", "siete", "ocho", "nueve", "diez", "once", "doce").getOrNull(n) ?: "$n"
}

/** Cómo salió en cada lugar al publicar. */
data class SalidaAlta(
    val lugar: String,
    /** esperando · publicando · ok · revision · error */
    val estado: String = "esperando",
    val detalle: String? = null,
) {
    val id: String get() = lugar
}

data class CampoAlta(val id: String)

@Serializable private data class SubidaFoto(val id: String, val url: String? = null, val ajustes: List<String>? = null)
@Serializable private data class CreadoBorrador(val id: String)
@Serializable private data class CuerpoGrabacion(val transcripcion: String, val audioId: String, val audioSegundos: Int? = null)
@Serializable private data class EstadoPublicacion(val estado: String? = null, val error: String? = null)

/** Los guardados en fila (Task de iOS que no se cancelan a mitad del pedido). */
private class TareasAlta {
    var espera: Job? = null
    var enFila: Job? = null
}

/** 4A Alta (y edición) de una propiedad, con lo mismo que los cinco pasos de la web en una sola pantalla: las fotos (con
 *  los ajustes de la subida), lo mínimo, la descripción (dictada, con audio y las comodidades que nombra), el título
 *  para los portales, las comodidades y dónde publicarla. En el alta se guarda sola como borrador (el mismo que se sigue
 *  en la web); en la edición cada cambio se guarda solo. Al publicar, la pantalla «Publicada» muestra cómo salió en cada
 *  portal. */
@Composable
fun AltaPropiedad(
    /** El id de la propiedad a editar (nil: una nueva). */
    editar: String? = null,
    /** El id de un borrador de /v1/borradores para seguirlo. */
    borrador: String? = null,
    inicial: DatosAlta? = null,
    /** Abrir en un paso puntual: «fotos», «comodidades» o «portales». */
    enPaso: String? = null,
) {
    val estado = LocalEstado.current
    val ctx = LocalContext.current
    val cerrar = LocalCerrarHoja.current
    val alcance = rememberCoroutineScope()
    val densidad = LocalDensity.current

    var d by remember { mutableStateOf(DatosAlta()) }
    /** En la edición, la propiedad entera como vino: el PUT parte de ella. */
    var base by remember { mutableStateOf<Propiedad?>(null) }
    var paso by remember { mutableIntStateOf(0) }
    /** Los borradores a medio cargar (para seguir uno en vez de empezar otro). */
    var paraSeguir by remember { mutableStateOf<List<BorradorPropiedad>>(emptyList()) }
    var borradorId by remember { mutableStateOf<String?>(null) }
    var guardadoEn by remember { mutableStateOf<Instant?>(null) }
    /** nada · guardando · listo · error */
    var guardado by remember { mutableStateOf("nada") }
    var errores by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var publicando by remember { mutableStateOf(false) }
    var subiendo by remember { mutableIntStateOf(0) }
    var campo by remember { mutableStateOf<String?>(null) }
    var masDatos by remember { mutableStateOf(false) }
    val dictado = rememberDictado()
    var ahora by remember { mutableStateOf(Instant.now()) }
    var sinPermiso by remember { mutableStateOf(false) }
    /** Las fotos que la API enderezó o aclaró al subirlas, y si se está viendo el antes. */
    var ajustadas by remember { mutableStateOf<Set<String>>(emptySet()) }
    var verAntes by remember { mutableStateOf(false) }
    /** nada · subiendo · listo */
    var audio by remember { mutableStateOf("nada") }
    var dictadoGrabado by remember { mutableStateOf(false) }
    /** En la edición: el estado de cada portal (publicada, error, finalizada…). */
    val portales = remember { mutableStateMapOf<String, String>() }
    /** La pantalla «Publicada» (nil mientras se carga). */
    var salidas by remember { mutableStateOf<List<SalidaAlta>?>(null) }
    var creada by remember { mutableStateOf<Propiedad?>(null) }
    var interesadosN by remember { mutableIntStateOf(0) }
    var envioAlta by remember { mutableStateOf(UUID.randomUUID().toString()) }
    var web by remember { mutableStateOf<String?>(null) }
    var ultimo by remember { mutableStateOf<DatosAlta?>(null) }
    val tareas = remember { TareasAlta() }
    val desplazar = rememberScrollState()
    val posiciones = remember { mutableStateMapOf<String, Int>() }
    var menuFoto by remember { mutableStateOf<Int?>(null) }
    var arrastrando by remember { mutableStateOf<Int?>(null) }
    var corrido by remember { mutableStateOf(0f) }

    val lugares = listOf("web", "mercadolibre", "zonaprop", "argenprop").filter { d.publicar[it] == true }

    val etiquetaSeguir: String = run {
        if (editar != null) return@run "Listo"
        if (publicando) return@run "Publicando…"
        val n = lugares.size
        if (n == 0) "Guardar sin publicar" else "Publicar en $n ${if (n == 1) "lugar" else "lugares"}"
    }

    val textoGuardado: String = run {
        if (guardado == "guardando") return@run "Guardando…"
        if (guardado == "error") return@run "No se guardó"
        val g = guardadoEn
        if (guardado == "nada" || g == null) return@run if (editar != null) "Cada cambio se guarda solo" else "Se guarda solo como borrador"
        val s = maxOf(1L, Duration.between(g, ahora).seconds)
        if (s < 60) "Guardado hace $s s" else "Guardado hace ${s / 60} min"
    }

    val sugeridasEnTexto = ComodidadesAlta.nombradas(d.descripcion).filter { d.comodidades.contains(it) }

    /** Lo que se nombra se marca en Comodidades (una vez: si la persona la desmarca, queda así). */
    fun marcarNombradas(x: DatosAlta): DatosAlta {
        val nuevas = ComodidadesAlta.nombradas(x.descripcion).filter { !x.sugeridas.contains(it) }
        if (nuevas.isEmpty()) return x
        return x.copy(sugeridas = x.sugeridas + nuevas, comodidades = x.comodidades + nuevas.filter { !x.comodidades.contains(it) })
    }

    val faltaPortales: List<String> = buildList {
        if (d.fotos.isEmpty()) add("fotos")
        if (d.precio == null) add("precio")
        if (d.direccion.trim().length < 3) add("dirección")
        if (d.descripcion.trim().length < 30) add("descripción")
    }

    fun detalleLugar(l: String): String {
        if (editar != null && l != "web" && portales[l] == "error") return "La rechazó · mirá la ficha"
        return when (l) {
            "web" -> (web ?: "tu web").replace("https://", "").replace("http://", "")
            "mercadolibre" -> "publicación clásica"
            else -> "por feed, en 15 min"
        }
    }

    // MARK: Guardar

    suspend fun guardarAhora() {
        if (d == ultimo) return
        val foto = d
        guardado = "guardando"
        try {
            val b = base
            val bid = borradorId
            if (editar != null && b != null) {
                val p: Propiedad = estado.api.conCamposComo("PUT", "/v1/propiedades/${b.id}", d.cuerpo(b))
                base = p
            } else if (bid != null) {
                estado.api.hacer("PUT", "/v1/borradores/$bid", BorradorAlta(paso, foto))
            } else {
                val c: CreadoBorrador = estado.api.enviar("POST", "/v1/borradores", BorradorAlta(paso, foto))
                borradorId = c.id
            }
            ultimo = foto
            guardado = "listo"
            guardadoEn = Instant.now()
            errores = emptyMap()
        } catch (e: CancellationException) {
            throw e
        } catch (e: ErrorConCampos) {
            guardado = "error"
            errores = e.campos
        } catch (e: Exception) {
            guardado = "error"
            if (editar != null) estado.avisar(e)
        }
    }

    /** El guardado en curso: los guardados van en fila (el primero crea el borrador y los que siguen lo actualizan), y no
     *  se cancelan a mitad del pedido. */
    suspend fun guardarYa() {
        val previo = tareas.enFila
        val t = alcance.launch {
            previo?.join()
            guardarAhora()
        }
        tareas.enFila = t
        t.join()
    }

    fun programarGuardado() {
        if (publicando || salidas != null || d == ultimo) return
        tareas.espera?.cancel()
        tareas.espera = alcance.launch {
            delay(if (editar == null) 800 else 700)
            guardarYa()
        }
    }

    fun seguir(b: BorradorPropiedad) {
        borradorId = b.id
        paso = b.datos.paso
        d = b.datos.datos
        ultimo = d
        paraSeguir = emptyList()
    }

    suspend fun arrancar() {
        if (web == null) web = estado.api.opcional<Inmobiliaria>("/v1/inmobiliaria")?.web
        if (inicial != null) { d = inicial; return }
        if (editar != null) {
            try {
                val p: Propiedad = estado.api.get("/v1/propiedades/$editar")
                base = p
                d = DatosAlta(p)
                ultimo = d
                guardado = "listo"
                val pubs = estado.api.opcional<List<PublicacionPortal>>("/v1/propiedades/$editar/publicaciones") ?: emptyList()
                var x = d
                for (pb in pubs) {
                    portales[pb.portal] = pb.estado
                    x = x.copy(publicar = x.publicar + (pb.portal to (pb.estado == "publicada" || pb.estado == "activa")))
                }
                d = x
                ultimo = d
            } catch (e: CancellationException) { throw e } catch (e: Exception) { estado.avisar(e) }
            return
        }
        try {
            val bs: List<BorradorPropiedad> = estado.api.get("/v1/borradores")
            if (borrador != null) {
                val b = bs.firstOrNull { it.id == borrador }
                if (b != null) seguir(b) else estado.avisar("Ese borrador ya no está: empezamos uno nuevo.")
            } else {
                paraSeguir = bs
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: ErrorApi) {
            if (e.estado == 403) sinPermiso = true else if (borrador != null) estado.avisar(e)
        } catch (e: Exception) {
            if (borrador != null) estado.avisar(e)
        }
    }

    suspend fun salir() {
        dictado.parar()
        tareas.espera?.cancel()
        guardarYa()
        val id = borradorId
        if (editar == null && id != null) {
            estado.sugerir("Quedó en borrador. Seguila cuando quieras.", "Seguir", AAltaPropiedad(borrador = id))
        }
        cerrar()
    }

    suspend fun asociarAudio(propiedad: String) {
        val a = d.audioId ?: return
        estado.api.pedir(
            "POST", "/v1/propiedades/$propiedad/grabaciones",
            cuerpo = Api.json.encodeToString(CuerpoGrabacion(d.descripcion.trim(), a, d.audioSegundos?.roundToInt())).toByteArray(),
            envio = "audio-$a",
        )
        d = d.copy(audioId = null, audioSegundos = null)
    }

    suspend fun publicar() {
        tareas.espera?.cancel()
        guardarYa()
        publicando = true
        try {
            val ruta = "/v1/propiedades" + (borradorId?.let { "?borrador=$it" } ?: "")
            val nueva: Propiedad
            try {
                nueva = estado.api.conCamposComo("POST", ruta, d.cuerpo(), envio = envioAlta)
            } catch (e: CancellationException) {
                throw e
            } catch (e: ErrorConCampos) {
                errores = e.campos
                estado.avisar(e.error)
                return
            } catch (e: ErrorApi) {
                if (e.sinConexion) {
                    // Sin señal, el alta queda en la cola y se sube sola.
                    try {
                        estado.cola.enviar("POST", ruta, d.cuerpo(), etiqueta = "Alta de ${d.direccion}")
                        estado.avisar("En cola · se publica cuando vuelva la señal")
                        estado.version += 1
                        cerrar()
                    } catch (x: CancellationException) { throw x } catch (x: Exception) { estado.avisar(x) }
                } else estado.avisar(e)
                return
            } catch (e: Exception) {
                estado.avisar(e)
                return
            }
            creada = nueva
            borradorId = null
            estado.version += 1
            try { asociarAudio(nueva.id) } catch (e: CancellationException) { throw e } catch (e: Exception) {
                estado.avisar("El audio de la descripción no se guardó en la propiedad.", ok = false)
            }
            val s = lugares.map { SalidaAlta(it) }.toMutableList()
            salidas = s.toList()
            estado.api.opcional<List<Interesado>>("/v1/propiedades/${nueva.id}/interesados", listOf("sinVisita" to "si"))?.let { interesadosN = it.size }
            for (i in s.indices) {
                s[i] = s[i].copy(estado = "publicando")
                salidas = s.toList()
                delay(350)
                if (s[i].lugar == "web") { s[i] = s[i].copy(estado = "ok"); salidas = s.toList(); continue }
                try {
                    val raw = estado.api.pedir("POST", "/v1/propiedades/${nueva.id}/publicaciones/${s[i].lugar}", envio = "$envioAlta-${s[i].lugar}")
                    val x = runCatching { Api.json.decodeFromString<EstadoPublicacion>(raw.decodeToString()) }.getOrNull()
                    s[i] = when (x?.estado) {
                        "publicada" -> s[i].copy(estado = if (s[i].lugar == "mercadolibre") "revision" else "ok")
                        "error" -> s[i].copy(estado = "error", detalle = x.error)
                        else -> s[i].copy(estado = "revision")
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: ErrorApi) {
                    s[i] = s[i].copy(estado = "error", detalle = e.detalle)
                } catch (e: Exception) {
                    s[i] = s[i].copy(estado = "error")
                }
                salidas = s.toList()
            }
        } finally {
            publicando = false
        }
    }

    suspend fun seguirAdelante() {
        errores = emptyMap()
        if (d.direccion.trim().length < 3) {
            errores = mapOf("direccion" to "Poné la dirección: sin ella no se puede publicar.")
            return
        }
        if (editar != null) {
            tareas.espera?.cancel()
            guardarYa()
            if (guardado != "error") { estado.version += 1; cerrar() }
            return
        }
        publicar()
    }

    /** En la edición, cada portal se prende y apaga al toque (con deshacer al despublicar). En el alta, sólo se elige. */
    suspend fun alternarLugar(l: String) {
        val antes = d.publicar[l] == true
        d = d.copy(publicar = d.publicar + (l to !antes))
        val id = base?.id
        if (editar == null || l == "web" || id == null) return
        val nombre = PublicacionPortal.nombres[l] ?: l
        suspend fun hacer(prender: Boolean) {
            try {
                val raw = estado.api.pedir(if (prender) "POST" else "DELETE", "/v1/propiedades/$id/publicaciones/$l")
                val x = runCatching { Api.json.decodeFromString<EstadoPublicacion>(raw.decodeToString()) }.getOrNull()
                portales[l] = x?.estado ?: (if (prender) "publicada" else "finalizada")
                d = d.copy(publicar = d.publicar + (l to (portales[l] == "publicada")))
                ultimo = d
                if (portales[l] == "error") estado.avisar("$nombre no la publicó. Mirá qué falta en la ficha.", ok = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                d = d.copy(publicar = d.publicar + (l to !prender))
                ultimo = d
                estado.avisar(e)
            }
        }
        hacer(!antes)
        if (antes && d.publicar[l] != true) {
            estado.avisar("Despublicada de $nombre") { estado.tarea { hacer(true) } }
        }
    }

    // MARK: Fotos

    fun mover(i: Int, j: Int) {
        if (i == j || i !in d.fotos.indices || j !in d.fotos.indices) return
        val fs = d.fotos.toMutableList(); val us = d.fotosUrl.toMutableList()
        val f = fs.removeAt(i); val u = us.removeAt(i)
        fs.add(j, f); us.add(j, u)
        d = d.copy(fotos = fs, fotosUrl = us)
        Haptico.impacto()
    }

    fun sacarFoto(i: Int) {
        if (i !in d.fotos.indices) return
        val antes = d
        d = d.copy(fotos = d.fotos.filterIndexed { k, _ -> k != i }, fotosUrl = d.fotosUrl.filterIndexed { k, _ -> k != i })
        estado.avisar("Sacamos la foto") { d = antes }
    }

    suspend fun subir(jpg: ByteArray) {
        subiendo += 1
        try {
            if (jpg.size > (25 shl 20)) { estado.avisar("El archivo pasa de 25 MB.", ok = false); return }
            val raw = estado.api.multipart("/v1/archivos", listOf("uso" to "foto"), jpg, "foto-${Instant.now().epochSecond}.jpg", "image/jpeg")
            val a = estado.api.decodificar<SubidaFoto>(raw)
            d = d.copy(fotos = d.fotos + a.id, fotosUrl = d.fotosUrl + (a.url ?: ""))
            if (!(a.ajustes ?: emptyList()).isEmpty()) ajustadas = ajustadas + a.id
        } catch (e: CancellationException) { throw e } catch (e: Exception) { estado.avisar(e) } finally { subiendo -= 1 }
    }

    /** El audio de la descripción (subido o dictado) queda en la propiedad como grabación con el texto: en el alta va al
     *  borrador y se asocia al publicar; en la edición, va derecho. */
    suspend fun subirAudio(leer: () -> Pair<ByteArray, String>, segundos: Double?) {
        audio = "subiendo"
        dictadoGrabado = segundos != null
        try {
            val (datos, nombre) = leer()
            if (datos.size > (25 shl 20)) { audio = if (d.audioId == null) "nada" else "listo"; estado.avisar("El archivo pasa de 25 MB.", ok = false); return }
            val ext = nombre.substringAfterLast('.', "").lowercase()
            val tipo = mapOf("mp3" to "audio/mpeg", "ogg" to "audio/ogg", "wav" to "audio/wav", "aac" to "audio/aac", "webm" to "audio/webm")[ext] ?: "audio/mp4"
            val a = estado.api.subir(datos, nombre = nombre, tipo = tipo, uso = "audio")
            d = d.copy(audioId = a.id, audioSegundos = segundos)
            base?.id?.let { asociarAudio(it) }
            audio = "listo"
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            audio = if (d.audioId == null) "nada" else "listo"
            estado.avisar(e)
        }
    }

    // La cámara del sistema (UIImagePickerController) y la fototeca (PhotosPicker, hasta 30).
    var fotoCamara by remember { mutableStateOf<Uri?>(null) }
    val camara = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val u = fotoCamara
        if (ok && u != null) alcance.launch { jpegDe(ctx, u)?.let { subir(it) } }
    }
    val fototeca = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(30)) { uris ->
        alcance.launch {
            for (u in uris) {
                val datos = jpegDe(ctx, u)
                if (datos != null) subir(datos) else estado.avisar("Algo salió mal. Probá de nuevo.", ok = false)
            }
        }
    }
    val eligiendoAudio = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        if (u != null) alcance.launch { subirAudio({ leerArchivo(ctx, u) }, segundos = null) }
    }

    LaunchedEffect(Unit) { arrancar() }
    LaunchedEffect(Unit) {
        dictado.alGrabar = { f, s -> alcance.launch { subirAudio({ f.readBytes() to f.name }, segundos = s) } }
        while (true) {
            delay(1000)
            ahora = Instant.now()
        }
    }
    // .onChange(of: d): las comodidades nombradas, el título armado y el guardado solo.
    LaunchedEffect(Unit) {
        snapshotFlow { d }.drop(1).collect { nuevo ->
            var x = marcarNombradas(nuevo)
            if (!nuevo.tituloPropio && nuevo.titulo != nuevo.tituloSugerido) x = x.copy(titulo = nuevo.tituloSugerido)
            if (x != d) d = x
            programarGuardado()
        }
    }
    LaunchedEffect(dictado.texto) { if (dictado.grabando) d = d.copy(descripcion = (dictado.base + dictado.texto).take(3000)) }
    LaunchedEffect(Unit) {
        val p = enPaso ?: return@LaunchedEffect
        delay(400)
        posiciones[p]?.let { desplazar.animateScrollTo(it) }
    }
    LaunchedEffect(errores) {
        val a = if (errores["fotos"] != null) "fotos" else if (errores["titulo"] != null) "descripcion" else null
        if (a != null) posiciones[a]?.let { desplazar.animateScrollTo(it) }
    }

    BarraDe(
        titulo = if (salidas != null) "Publicada" else if (editar == null) "Nueva propiedad" else "Editar propiedad",
        izquierda = {
            if (salidas == null && !sinPermiso) BotonBarra(if (editar == null) "Salir" else "Volver") { alcance.launch { salir() } }
        },
        acciones = {
            if (salidas == null && !sinPermiso) {
                BotonBarra(etiquetaSeguir, fuerte = true, habilitado = !(publicando || subiendo > 0 || (editar != null && base == null))) {
                    alcance.launch { seguirAdelante() }
                }
            } else if (salidas != null) {
                BotonBarra("Listo", fuerte = true, habilitado = !publicando) { cerrar() }
            }
        },
    )
    // Sin el «‹» de la barra (navigationBarBackButtonHidden): el «atrás» del sistema hace lo mismo que «Salir».
    BackHandler(enabled = salidas == null && !sinPermiso) { alcance.launch { salir() } }

    Box(Modifier.fillMaxSize()) {
        FondoApp()
        val sal = salidas
        if (sinPermiso) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EstadoVacio(
                    "lock", "No podés cargar propiedades",
                    texto = "Tu rol es Lector: podés ver la cartera y compartirla. Para cargar una, pedile acceso a la dueña de la inmobiliaria.",
                    accion = "Volver" to { cerrar() },
                )
            }
        } else if (sal != null) {
            // MARK: Publicada
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(Modifier.fillMaxWidth().height(240.dp).clip(RoundedCornerShape(M.rDestacada))) {
                    Foto(url = d.fotosUrl.firstOrNull(), modifier = Modifier.fillMaxSize())
                    Box(Modifier.matchParentSize().background(androidx.compose.ui.graphics.Brush.verticalGradient(0.5f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.6f))))
                    Column(Modifier.align(Alignment.BottomStart).padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Texto(d.precio?.let { Formato.plata(it, d.moneda) } ?: "Consultar precio", Estilo(26f, Peso.bold), Color.White)
                        Texto(listOf(d.direccion, d.barrio).filter { it.isNotEmpty() }.joinToString(" · "), Estilo.subhead, Color.White)
                    }
                }
                Column(Modifier.fillMaxWidth().tarjeta()) {
                    if (sal.isEmpty()) FilaSalida("square.and.pencil", C.texto3, "Guardada sin publicar", null, null, primera = true)
                    sal.forEachIndexed { i, s ->
                        val ok = s.estado == "ok" || s.estado == "revision"
                        Box(Modifier.alpha(if (s.estado == "esperando" || s.estado == "publicando") 0.6f else 1f)) {
                            FilaSalida(
                                if (ok) "checkmark.circle.fill" else if (s.estado == "error") "exclamationmark.circle.fill" else "clock",
                                if (ok) C.okTinta else if (s.estado == "error") C.errorTinta else C.texto3,
                                PublicacionPortal.nombres[s.lugar] ?: s.lugar, if (s.estado == "error") s.detalle else null,
                                when (s.estado) { "ok" -> "Publicada"; "revision" -> "En revisión de Mercado Libre"; "error" -> "No la publicó"; else -> "Publicando…" },
                                primera = i == 0,
                            )
                        }
                    }
                }
                val c = creada
                if (interesadosN > 0 && c != null) {
                    BotonPrincipal(
                        "Mandar a $interesadosN ${if (interesadosN == 1) "interesado que le encaja" else "interesados que le encajan"}",
                        { estado.abrir(AMandarPropiedad(id = c.id)) }, simbolo = "square.and.arrow.up",
                    )
                }
                if (c != null) BotonHundido("Ver la ficha", { estado.abrir(AFichaPropiedad(id = c.id)) })
                BotonTexto("Cargar otra", {
                    d = DatosAlta()
                    ultimo = null
                    borradorId = null
                    creada = null
                    salidas = null
                    interesadosN = 0
                    ajustadas = emptySet()
                    audio = "nada"
                    guardado = "nada"
                    envioAlta = UUID.randomUUID().toString()
                }, Modifier.fillMaxWidth())
            }
        } else {
            // MARK: Formulario
            Column(
                Modifier.fillMaxSize().verticalScroll(desplazar).padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                // Estado del guardado
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    val tinta = if (guardado == "error") C.errorTinta else C.okTinta
                    Row(
                        Modifier.height(28.dp).clip(CircleShape).background(if (guardado == "error") C.errorFondo else C.okFondo).padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        if (guardado == "guardando") Rueda(tinta, 12.dp) else Icono(if (guardado == "error") "exclamationmark.icloud" else "checkmark.icloud", 13f, tinta)
                        Texto(textoGuardado, Estilo(13f, Peso.semibold), tinta)
                        if (guardado == "error") Box(Modifier.clickable { alcance.launch { guardarYa() } }) { Texto("Reintentar", Estilo(13f, Peso.bold), tinta) }
                    }
                }
                // A medio cargar: «Para seguir», los borradores de la persona (de la web, de otro aparato o de MIRA).
                if (editar == null && borradorId == null && paraSeguir.isNotEmpty() && d == DatosAlta()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Texto("PARA SEGUIR", Estilo(13f, Peso.bold, 0.5f / 13f), C.texto2, Modifier.padding(horizontal = 4.dp))
                        Column(Modifier.fillMaxWidth().tarjeta(16.dp)) {
                            paraSeguir.forEachIndexed { i, b ->
                                Tocable({ seguir(b) }, Modifier.fillMaxWidth()) {
                                    Column {
                                        if (i > 0) Box(Modifier.fillMaxWidth().height(0.5.dp).background(C.linea))
                                        Row(Modifier.fillMaxWidth().heightIn(min = 54.dp).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                            Box(Modifier.width(24.dp), contentAlignment = Alignment.Center) { Icono("square.and.pencil", 18f, C.oro) }
                                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                                Texto(b.datos.datos.direccion.nonEmpty ?: "Sin dirección todavía", Estilo.filaLista, C.texto, lineas = 1)
                                                Texto(listOfNotNull("Paso ${b.datos.paso + 1} de 5", b.actualizadoEn?.let { Formato.hace(it, ahora = ahora) }).joinToString(" · "), Estilo.footnote, C.texto2)
                                            }
                                            Chevron()
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                // Fotos
                Column(Modifier.onGloballyPositioned { posiciones["fotos"] = it.positionInParent().y.toInt() }, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Texto("FOTOS · ${d.fotos.size}", Estilo(13f, Peso.bold, 0.5f / 13f), C.texto2, Modifier.padding(horizontal = 4.dp))
                    val pasoPx = with(densidad) { 96.dp.toPx() }
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 2.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        d.fotosUrl.forEachIndexed { i, u ->
                            val ver = if (verAntes && ajustadas.contains(d.fotos.getOrNull(i) ?: "")) u else u + (if (u.contains("?")) "&" else "?") + "tam=chica"
                            val esta = arrastrando == i
                            Box(
                                Modifier.zIndex(if (esta) 1f else 0f).offset { IntOffset(if (esta) corrido.roundToInt() else 0, 0) }.size(88.dp)
                                    .pointerInput(i, d.fotos.size) {
                                        // Mantener apretada abre el menú; mantener y arrastrar la cambia de lugar (draggable/dropDestination).
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = { arrastrando = i; corrido = 0f; Haptico.impacto() },
                                            onDrag = { c, delta -> c.consume(); corrido += delta.x },
                                            onDragEnd = {
                                                val desde = arrastrando ?: i
                                                if (abs(corrido) < 12f) menuFoto = desde
                                                else mover(desde, (desde + (corrido / pasoPx).roundToInt()).coerceIn(0, d.fotos.size - 1))
                                                arrastrando = null; corrido = 0f
                                            },
                                            onDragCancel = { arrastrando = null; corrido = 0f },
                                        )
                                    },
                            ) {
                                Foto(url = ver, modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp)))
                                Box(Modifier.align(Alignment.TopStart).padding(6.dp).size(22.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
                                    Texto("${i + 1}", Estilo(12f, Peso.bold), Color.White)
                                }
                                Tocable({ sacarFoto(i) }, Modifier.align(Alignment.TopEnd).padding(6.dp), descripcion = "Sacar esta foto") {
                                    Box(Modifier.size(22.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
                                        Icono("xmark", 11f, Color.White, peso = Peso.bold)
                                    }
                                }
                                if (i == 0) {
                                    Box(
                                        Modifier.align(Alignment.BottomCenter).padding(6.dp).fillMaxWidth().heightIn(min = 20.dp).clip(RoundedCornerShape(6.dp)).background(C.oro),
                                        contentAlignment = Alignment.Center,
                                    ) { Texto("Portada", Estilo(11f, Peso.bold), hex(0x1A1405)) }
                                }
                                if (menuFoto == i) {
                                    MenuFotoAlta(
                                        buildList {
                                            if (i > 0) add(OpcionDeMenu("Usar de portada", "star") { mover(i, 0) })
                                            if (i > 0) add(OpcionDeMenu("Mover antes", "arrow.left") { mover(i, i - 1) })
                                            if (i < d.fotos.size - 1) add(OpcionDeMenu("Mover después", "arrow.right") { mover(i, i + 1) })
                                            add(OpcionDeMenu("Sacar", "trash", destructiva = true) { sacarFoto(i) })
                                        },
                                    ) { menuFoto = null }
                                }
                            }
                        }
                        if (subiendo > 0) {
                            Box(Modifier.size(88.dp).clip(RoundedCornerShape(14.dp)).background(C.hundida), contentAlignment = Alignment.Center) { Rueda() }
                        }
                        EtiquetaFotoAlta("camera", "Cámara") {
                            val (_, u) = archivoCamaraAlta(ctx)
                            fotoCamara = u
                            // Sin cámara, la fototeca (sourceType .photoLibrary de iOS).
                            runCatching { camara.launch(u) }.onFailure { fototeca.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                        }
                        EtiquetaFotoAlta("photo.on.rectangle", "Fototeca") {
                            fototeca.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }
                    }
                    Texto("Mantené apretada una foto y arrastrala para ordenar. La primera es la portada.", Estilo.footnote, C.texto3, Modifier.padding(horizontal = 4.dp))
                    if (ajustadas.isNotEmpty()) {
                        Tocable({ verAntes = !verAntes }, Modifier.fillMaxWidth()) {
                            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(C.avisoFondo).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icono("wand.and.stars", 17f, C.avisoTinta)
                                Texto(
                                    if (verAntes) "Estás viendo el antes. Tocá para volver a las arregladas."
                                    else "Enderezamos y aclaramos ${if (ajustadas.size == 1) "1 foto oscura" else "${ajustadas.size} fotos oscuras"}. Tocá para ver el antes.",
                                    Estilo.footnote, C.texto,
                                )
                            }
                        }
                    }
                    errores["fotos"]?.let { MensajeError(it) }
                }
                // Lo mínimo
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Segmentado(listOf("venta" to "Venta", "alquiler" to "Alquiler", "temporario" to "Temporario"), d.operacion, { d = d.copy(operacion = it) })
                    val forma = RoundedCornerShape(16.dp)
                    Column(Modifier.fillMaxWidth().tarjeta(16.dp).border(1.5.dp, if (errores["direccion"] != null) C.error else Color.Transparent, forma)) {
                        FilaCampoAlta("Tipo", Propiedad.tipos.firstOrNull { it.first == d.tipo }?.second ?: d.tipo, primera = true) { campo = "tipo" }
                        FilaCampoAlta("Dirección", d.direccion.nonEmpty ?: "Calle y altura, piso y depto") { campo = "direccion" }
                        FilaCampoAlta("Barrio", d.barrio.nonEmpty ?: "Barrio") { campo = "barrio" }
                        FilaCampoAlta("Precio", d.precio?.let { Formato.plata(it, d.moneda) } ?: "Sin precio") { campo = "precio" }
                        Box(Modifier.fillMaxWidth().height(0.5.dp).background(C.linea))
                        Row(Modifier.fillMaxWidth().heightIn(min = 54.dp).padding(start = 14.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Texto("Ambientes", Estilo.subhead, C.texto2, Modifier.width(96.dp))
                            Texto("${d.ambientes}", Estilo(17f, Peso.bold), C.texto)
                            Spacer(Modifier.weight(1f))
                            Pasador(d.ambientes, { d = d.copy(ambientes = it) }, 1..20, que = "ambientes")
                        }
                    }
                    errores["direccion"]?.let { MensajeError(it) }
                    (errores["precio"] ?: errores["tipo"] ?: errores["operacion"])?.let { MensajeError(it) }
                    Texto(Formato.sinViudas("Superficie, expensas y antigüedad las sumás después desde la ficha. No hacen falta para publicar."), Estilo.footnote, C.texto3, Modifier.padding(horizontal = 4.dp))
                }
                // Descripción
                Column(
                    Modifier.onGloballyPositioned { posiciones["descripcion"] = it.positionInParent().y.toInt() }.fillMaxWidth().tarjeta(16.dp).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Texto("Descripción", Estilo.subheadFuerte, C.texto, Modifier.weight(1f))
                        PildoraAlta(if (audio == "subiendo") "Subiendo…" else "Subir audio", "waveform", C.texto, C.hundida, habilitado = audio != "subiendo") {
                            eligiendoAudio.launch(arrayOf("audio/*"))
                        }
                        PildoraAlta(if (dictado.grabando) "Terminar" else "Dictar", if (dictado.grabando) "stop.circle" else "mic", C.avisoTinta, C.avisoFondo) {
                            if (dictado.grabando) dictado.parar() else dictado.empezar(base = if (d.descripcion.isEmpty()) "" else d.descripcion + " ")
                        }
                    }
                    Texto(Formato.sinViudas("Contala como se la contarías a un interesado. Nosotros la pasamos en limpio para los portales."), Estilo.footnote, C.texto2)
                    Entrada(
                        d.descripcion, { d = d.copy(descripcion = it.take(3000)) },
                        "Ej.: «Tres ambientes al frente, muy luminoso, balcón corrido, a dos cuadras de Cabildo…»",
                        estilo = Estilo(16f, Peso.regular), lineas = 3, lineasMaximas = 12,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Texto(if (dictado.grabando) "Escuchando… tocá para terminar" else "Tocá para dictar · o escribí", Estilo.footnote, C.texto3, Modifier.weight(1f))
                        Texto("${Formato.numero(d.descripcion.length)} / 3.000 caracteres", Estilo.footnote, if (d.descripcion.length >= 3000) C.avisoTinta else C.texto3)
                    }
                    if (dictado.grabando) {
                        Row(Modifier.height(22.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            val t = ahora.toEpochMilli() / 1000.0
                            (0 until 24).forEach { i ->
                                Box(Modifier.width(3.dp).height((4 + abs(sin(i + t * 6)) * 18).dp).clip(CircleShape).background(C.avisoTinta))
                            }
                        }
                    }
                    if (audio == "listo") {
                        PistaAlta(
                            "waveform",
                            if (dictadoGrabado) (if (editar != null) "Guardamos el audio de lo que dictaste en la propiedad." else "Guardamos el audio de lo que dictaste: queda en la propiedad al publicar.")
                            else (if (editar != null) "Guardamos el audio en la propiedad." else "Guardamos el audio: queda en la propiedad al publicar."),
                        )
                    }
                    if (sugeridasEnTexto.isNotEmpty()) {
                        val s = sugeridasEnTexto
                        PistaAlta("lightbulb", "Mencionaste ${Cartera.lista(s)}: ${if (s.size == 1) "la marcamos" else "las marcamos"} en Comodidades.")
                    }
                }
                // Título
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Texto("Título para los portales", Estilo.footnoteFuerte, C.texto2)
                    val forma = RoundedCornerShape(14.dp)
                    Box(
                        Modifier.fillMaxWidth().height(50.dp).clip(forma).background(C.superficie).border(1.dp, if (errores["titulo"] != null) C.error else C.linea2, forma).padding(horizontal = 14.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Entrada(d.titulo, { d = d.copy(titulo = it.take(120), tituloPropio = true) }, "", estilo = Estilo.body16)
                    }
                    val n = d.titulo.length
                    Texto(
                        "${if (d.tituloPropio) "Lo escribiste vos." else "Lo armamos de la descripción."} Mercado Libre corta en 60 caracteres: este tiene $n${if (n > 60) ", sacale ${n - 60}" else ""}.",
                        Estilo.footnote, if (n > 60) C.avisoTinta else C.texto3,
                    )
                    errores["titulo"]?.let { MensajeError(it) }
                }
                // Comodidades
                Column(Modifier.onGloballyPositioned { posiciones["comodidades"] = it.positionInParent().y.toInt() }, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Texto("COMODIDADES", Estilo(13f, Peso.bold, 0.5f / 13f), C.texto2, Modifier.weight(1f))
                        Texto("${d.comodidades.size} ${if (d.comodidades.size == 1) "marcada" else "marcadas"}", Estilo.footnote, C.texto2)
                    }
                    Texto(
                        "Tocá las que tiene.${if (sugeridasEnTexto.isEmpty()) "" else " Marcamos ${ComodidadesAlta.enLetras(sugeridasEnTexto.size)} que nombraste en la descripción."}",
                        Estilo.footnote, C.texto2, Modifier.padding(horizontal = 4.dp),
                    )
                    FlujoLayout(espacio = 8.dp) {
                        ComodidadesAlta.todas.forEach { c ->
                            val on = d.comodidades.contains(c.nombre)
                            Tocable({
                                Haptico.seleccion()
                                d = if (on) d.copy(comodidades = d.comodidades.filter { it != c.nombre }) else d.copy(comodidades = d.comodidades + c.nombre)
                            }, Modifier.semantics { selected = on }) {
                                Row(
                                    Modifier.height(36.dp).clip(CircleShape).background(if (on) C.accion else C.superficie)
                                        .border(1.dp, if (on) Color.Transparent else C.linea2, CircleShape).padding(horizontal = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp),
                                ) {
                                    Icono(if (on) "checkmark" else "plus", 13f, if (on) C.sobreAccion else C.texto)
                                    Texto(c.nombre, Estilo(14f, Peso.semibold), if (on) C.sobreAccion else C.texto)
                                }
                            }
                        }
                    }
                }
                // Dónde publicarla
                Column(Modifier.onGloballyPositioned { posiciones["portales"] = it.positionInParent().y.toInt() }, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Texto("DÓNDE PUBLICARLA", Estilo(13f, Peso.bold, 0.5f / 13f), C.texto2, Modifier.padding(horizontal = 4.dp))
                    Texto(Formato.sinViudas("Un toque y sale en todos. Si un portal la rechaza, te decimos qué arreglar."), Estilo.footnote, C.texto2, Modifier.padding(horizontal = 4.dp))
                    Column(Modifier.fillMaxWidth().tarjeta(16.dp)) {
                        listOf("web", "mercadolibre", "zonaprop", "argenprop").forEachIndexed { i, l ->
                            if (i > 0) Box(Modifier.fillMaxWidth().height(0.5.dp).background(C.linea))
                            Row(Modifier.fillMaxWidth().heightIn(min = 58.dp).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                LogoPortal(l)
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                    Texto(PublicacionPortal.nombres[l] ?: l, Estilo.filaLista, C.texto)
                                    Texto(detalleLugar(l), Estilo.footnote, if (portales[l] == "error" && editar != null) C.errorTinta else C.texto2)
                                }
                                Llave(d.publicar[l] == true, { alcance.launch { alternarLugar(l) } })
                            }
                        }
                    }
                    val f = faltaPortales
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (f.isEmpty()) C.okFondo else C.avisoFondo).padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icono(if (f.isEmpty()) "checkmark.seal.fill" else "info.circle.fill", 17f, if (f.isEmpty()) C.okTinta else C.avisoTinta)
                        Texto(
                            if (f.isEmpty()) "Tiene todo lo que piden los portales: ${Formato.cantidad(d.fotos.size, "foto", "fotos")}, precio, dirección y descripción."
                            else "Para los portales falta ${Cartera.lista(f)}. La web la muestra igual.",
                            Estilo.footnote, C.texto,
                        )
                    }
                }
                // Más datos (DisclosureGroup)
                Column {
                    Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable { masDatos = !masDatos }, verticalAlignment = Alignment.CenterVertically) {
                        Texto("Más datos", Estilo.subheadFuerte, C.accion, Modifier.weight(1f))
                        Icono("chevron.right", 14f, C.accion, Modifier.rotate(if (masDatos) 90f else 0f))
                    }
                    if (masDatos) {
                        Column(Modifier.padding(top = 8.dp).fillMaxWidth().tarjeta(16.dp)) {
                            CampoNumeroAlta("Dormitorios", d.dormitorios?.toDouble(), entero = true) { d = d.copy(dormitorios = it?.toInt()) }
                            CampoNumeroAlta("Baños", d.banos?.toDouble(), entero = true) { d = d.copy(banos = it?.toInt()) }
                            CampoNumeroAlta("Cocheras", d.cocheras?.toDouble(), entero = true) { d = d.copy(cocheras = it?.toInt()) }
                            CampoNumeroAlta("Superficie m²", d.superficie, entero = false) { d = d.copy(superficie = it) }
                            CampoNumeroAlta("Cubierta m²", d.cubierta, entero = false) { d = d.copy(cubierta = it) }
                            CampoNumeroAlta("Expensas ARS", d.expensas, entero = false) { d = d.copy(expensas = it) }
                            CampoNumeroAlta("Antigüedad (años)", d.antiguedad?.toDouble(), entero = true) { d = d.copy(antiguedad = it?.toInt()) }
                            Box(Modifier.fillMaxWidth().height(0.5.dp).background(C.linea))
                            Row(Modifier.fillMaxWidth().heightIn(min = 50.dp).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Texto("Apto crédito", Estilo.body16, C.texto, Modifier.weight(1f))
                                Llave(d.aptoCredito, { d = d.copy(aptoCredito = it) }, color = hex(0x2F9E6F))
                            }
                            FilaCampoAlta("Propietario", d.propietarioNombre ?: (if (d.propietarioId == null) "Elegí un contacto" else "Cargado")) { campo = "propietario" }
                        }
                    }
                }
            }
        }
    }

    Hoja(campo != null, { campo = null }, alturas = listOf(Altura.media, Altura.grande)) {
        campo?.let { c -> EditorCampoAlta(c, d) { d = it } }
    }
}

@Composable
private fun FilaCampoAlta(l: String, v: String, primera: Boolean = false, hacer: () -> Unit) {
    Tocable(hacer, Modifier.fillMaxWidth()) {
        Column {
            if (!primera) Box(Modifier.fillMaxWidth().height(0.5.dp).background(C.linea))
            Row(Modifier.fillMaxWidth().heightIn(min = 50.dp).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Texto(l, Estilo.subhead, C.texto2, Modifier.width(96.dp))
                Texto(v, Estilo(16f, Peso.medium), C.texto, Modifier.weight(1f), lineas = 1)
                Chevron()
            }
        }
    }
}

/** Los `TextField(value:format: .number)` de «Más datos». */
@Composable
private fun CampoNumeroAlta(l: String, valor: Double?, entero: Boolean, alCambiar: (Double?) -> Unit) {
    fun mostrar(v: Double?) = v?.let { if (entero) Formato.numero(it.toInt()) else numeroCorto(it) } ?: ""
    fun leer(t: String): Double? = t.replace(".", "").replace(",", ".").trim().toDoubleOrNull()?.let { if (entero) it.toInt().toDouble() else it }
    var texto by remember { mutableStateOf(mostrar(valor)) }
    // El TextField(value:) de iOS sigue al Binding: si el valor cambia de afuera (seguir un borrador, «Deshacer»),
    // se muestra el nuevo; mientras se escribe («12,» → 12) no se pisa lo tecleado.
    LaunchedEffect(valor) { if (leer(texto) != valor) texto = mostrar(valor) }
    Column {
        Box(Modifier.fillMaxWidth().height(0.5.dp).background(C.linea))
        Row(Modifier.fillMaxWidth().heightIn(min = 50.dp).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Texto(l, Estilo.subhead, C.texto2, Modifier.weight(1f))
            Entrada(
                texto, { t ->
                    texto = t
                    alCambiar(leer(t))
                },
                "—", Modifier.width(if (entero) 90.dp else 110.dp), estilo = Estilo.body16,
                teclado = if (entero) KeyboardType.Number else KeyboardType.Decimal, alinear = TextAlign.End,
            )
        }
    }
}

private fun numeroCorto(n: Double): String =
    if (n == Math.floor(n)) Formato.numero(n.toInt()) else n.toString().replace(".", ",")

@Composable
private fun PildoraAlta(texto: String, icono: String, tinta: Color, fondo: Color, habilitado: Boolean = true, hacer: () -> Unit) {
    Tocable(hacer, habilitado = habilitado) {
        Row(
            Modifier.height(36.dp).alpha(if (habilitado) 1f else 0.5f).clip(CircleShape).background(fondo).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Icono(icono, 14f, tinta)
            Texto(texto, Estilo(14f, Peso.semibold), tinta)
        }
    }
}

@Composable
private fun PistaAlta(icono: String, t: String) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(C.avisoFondo).padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icono(icono, 17f, C.avisoTinta)
        Texto(t, Estilo.footnote, C.texto, Modifier.weight(1f))
    }
}

@Composable
private fun EtiquetaFotoAlta(icono: String, texto: String, hacer: () -> Unit) {
    val linea = C.linea2
    Tocable(hacer) {
        Column(
            Modifier.size(88.dp).drawBehind {
                drawRoundRect(linea, cornerRadius = CornerRadius(14.dp.toPx()), style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))))
            },
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
        ) {
            Icono(icono, 24f, C.accion)
            Texto(texto, Estilo(12f, Peso.semibold), C.accion)
        }
    }
}

/** El menú de una foto (el .contextMenu de iOS al mantenerla apretada sin moverla). */
@Composable
private fun MenuFotoAlta(opciones: List<OpcionDeMenu>, alCerrar: () -> Unit) {
    Popup(alignment = Alignment.BottomStart, onDismissRequest = alCerrar) {
        Column(Modifier.padding(top = 92.dp).widthIn(min = 230.dp).tarjeta(14.dp)) {
            opciones.forEachIndexed { i, o ->
                if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(C.linea))
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { alCerrar(); o.accion() }.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Texto(o.titulo, Estilo(16f, Peso.regular), if (o.destructiva) C.error else C.texto)
                    Icono(o.simbolo, 17f, if (o.destructiva) C.error else C.texto)
                }
            }
        }
    }
}

@Composable
private fun FilaSalida(icono: String, tono: Color, t: String, motivo: String?, st: String?, primera: Boolean) {
    Column {
        if (!primera) Box(Modifier.fillMaxWidth().height(0.5.dp).background(C.linea))
        Row(Modifier.fillMaxWidth().heightIn(min = 54.dp).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icono(icono, 20f, tono)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Texto(t, Estilo.filaLista, C.texto)
                if (motivo != null) Texto(motivo, Estilo.footnote, C.errorTinta)
            }
            if (st != null) Texto(st, Estilo.footnote, C.texto2)
        }
    }
}

/** La hoja para editar un campo del alta (tipo, dirección, barrio, precio, propietario). */
@Composable
fun EditorCampoAlta(campo: String, d: DatosAlta, alCambiar: (DatosAlta) -> Unit) {
    val estado = LocalEstado.current
    val cerrar = LocalCerrarHoja.current
    var texto by remember { mutableStateOf("") }
    var precio by remember { mutableStateOf(d.precio?.let { numeroCorto(it) } ?: "") }
    var contactos by remember { mutableStateOf<List<Resultado>>(emptyList()) }

    LaunchedEffect(texto) {
        if (campo != "propietario" || texto.length < 2) return@LaunchedEffect
        delay(220)
        contactos = estado.api.opcional<List<Resultado>>("/v1/buscar", listOf("tipo" to "contacto", "q" to texto)) ?: emptyList()
    }

    Column(Modifier.fillMaxWidth()) {
        // La barra de la NavigationStack: el título y «Listo».
        Box(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 8.dp)) {
            Texto(
                mapOf("tipo" to "Tipo", "direccion" to "Dirección", "barrio" to "Barrio", "precio" to "Precio", "propietario" to "Propietario")[campo] ?: "",
                Estilo.headline, C.texto, Modifier.align(Alignment.Center),
            )
            Box(Modifier.align(Alignment.CenterEnd)) { BotonBarra("Listo", fuerte = true) { cerrar() } }
        }
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val forma = RoundedCornerShape(12.dp)
            when (campo) {
                "tipo" -> Column(Modifier.fillMaxWidth().clip(forma).background(C.superficie)) {
                    Propiedad.tipos.forEachIndexed { i, (k, n) ->
                        if (i > 0) LineaEditor()
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { alCambiar(d.copy(tipo = k)) }.padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Texto(n, Estilo.body, C.texto, Modifier.weight(1f))
                            if (d.tipo == k) Icono("checkmark", 16f, C.accion)
                        }
                    }
                }
                "direccion" -> Column(Modifier.fillMaxWidth().clip(forma).background(C.superficie)) {
                    RenglonEditor { Entrada(d.direccion, { alCambiar(d.copy(direccion = it)) }, "Mendoza 2350, 5.º A") }
                    LineaEditor()
                    RenglonEditor { Entrada(d.ciudad, { alCambiar(d.copy(ciudad = it)) }, "Ciudad (opcional)") }
                }
                "barrio" -> Column(Modifier.fillMaxWidth().clip(forma).background(C.superficie)) {
                    RenglonEditor { Entrada(d.barrio, { alCambiar(d.copy(barrio = it)) }, "Belgrano") }
                }
                "precio" -> Column(Modifier.fillMaxWidth().clip(forma).background(C.superficie)) {
                    Box(Modifier.padding(12.dp)) { Segmentado(listOf("USD" to "USD", "ARS" to "ARS"), d.moneda, { alCambiar(d.copy(moneda = it)) }, descripcion = "Moneda") }
                    LineaEditor()
                    RenglonEditor {
                        Entrada(precio, { t ->
                            precio = t
                            alCambiar(d.copy(precio = t.replace(".", "").replace(",", ".").trim().toDoubleOrNull()))
                        }, "172000", teclado = KeyboardType.Number)
                    }
                }
                "propietario" -> Column(Modifier.fillMaxWidth().clip(forma).background(C.superficie)) {
                    RenglonEditor { Entrada(texto, { texto = it }, "Buscar contacto") }
                    contactos.forEach { c ->
                        LineaEditor()
                        Column(
                            Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable {
                                alCambiar(d.copy(propietarioId = c.id, propietarioNombre = c.titulo))
                                cerrar()
                            }.padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Texto(c.titulo, Estilo.body, C.accion)
                            c.subtitulo?.let { Texto(it, Estilo.footnote, C.texto2) }
                        }
                    }
                }
                else -> {}
            }
        }
    }
}

@Composable
private fun RenglonEditor(contenido: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) { contenido() }
}

@Composable
private fun LineaEditor() {
    Box(Modifier.fillMaxWidth().padding(start = 16.dp).height(0.5.dp).background(C.linea))
}

// MARK: - Dictado

/** Un Dictado listo para usar en una pantalla, con el permiso del micrófono (SFSpeechRecognizer.requestAuthorization). */
@Composable
fun rememberDictado(): Dictado {
    val ctx = LocalContext.current
    val dictado = remember { Dictado() }
    val permiso = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (ok) dictado.arrancar() }
    dictado.contexto = ctx
    dictado.pedirPermiso = { permiso.launch(Manifest.permission.RECORD_AUDIO) }
    DisposableEffect(Unit) { onDispose { dictado.parar() } }
    return dictado
}

/** Dictado en español con el reconocimiento de voz del teléfono. Además graba el audio (WAV, desde Android 13: el
 *  reconocedor lee del mismo micrófono) y, al terminar, lo entrega con su duración (`alGrabar`): queda en la propiedad
 *  como grabación. */
class Dictado {
    var texto by mutableStateOf("")
    var grabando by mutableStateOf(false)
    var base = ""
    /** El audio de lo dictado y cuántos segundos dura. */
    var alGrabar: ((File, Double) -> Unit)? = null
    internal var contexto: Context? = null
    internal var pedirPermiso: (() -> Unit)? = null

    private var reconocedor: SpeechRecognizer? = null
    private var grabador: AudioRecord? = null
    private var salida: ParcelFileDescriptor.AutoCloseOutputStream? = null
    private var hilo: Thread? = null
    private var archivo: File? = null
    private var desde = 0L
    @Volatile private var sigue = false

    fun empezar(base: String) {
        this.base = base
        texto = ""
        val ctx = contexto ?: return
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) arrancar()
        else pedirPermiso?.invoke()
    }

    @SuppressLint("MissingPermission")
    internal fun arrancar() {
        val ctx = contexto ?: return
        if (grabando || !SpeechRecognizer.isRecognitionAvailable(ctx)) return
        val r = SpeechRecognizer.createSpeechRecognizer(ctx)
        reconocedor = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
            override fun onError(error: Int) { parar() }
            override fun onPartialResults(p: Bundle?) {
                p?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { texto = it }
            }
            override fun onResults(p: Bundle?) {
                p?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { texto = it }
                parar()
            }
        })
        val pedido = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-AR")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        if (Build.VERSION.SDK_INT >= 33) runCatching { grabarA(ctx, pedido) }.onFailure { cortarGrabacion() }
        desde = System.currentTimeMillis()
        grabando = true
        runCatching { r.startListening(pedido) }.onFailure { parar() }
    }

    /** Graba el micrófono a un WAV y se lo pasa al reconocedor por un caño (EXTRA_AUDIO_SOURCE, Android 13+). */
    @SuppressLint("MissingPermission")
    private fun grabarA(ctx: Context, pedido: Intent) {
        if (Build.VERSION.SDK_INT < 33) return
        val tasa = 16000
        val minimo = AudioRecord.getMinBufferSize(tasa, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val g = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, tasa, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minimo, 4096))
        val cano = ParcelFileDescriptor.createPipe()
        val s = ParcelFileDescriptor.AutoCloseOutputStream(cano[1])
        val f = File(ctx.cacheDir, "descripcion-${System.currentTimeMillis() / 1000}.wav")
        val crudo = RandomAccessFile(f, "rw").apply { setLength(0); write(ByteArray(44)) }
        pedido.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, cano[0])
        pedido.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1)
        pedido.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
        pedido.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, tasa)
        grabador = g; salida = s; archivo = f
        g.startRecording()
        sigue = true
        hilo = thread(name = "dictado") {
            val buf = ByteArray(4096)
            var total = 0
            var alReconocedor = true
            while (sigue) {
                val n = g.read(buf, 0, buf.size)
                if (n <= 0) continue
                if (alReconocedor) alReconocedor = runCatching { s.write(buf, 0, n) }.isSuccess
                crudo.write(buf, 0, n)
                total += n
            }
            runCatching { s.close() }
            runCatching { encabezadoWav(crudo, total, tasa); crudo.close() }
        }
    }

    private fun cortarGrabacion() {
        sigue = false
        runCatching { salida?.close() }
        hilo?.join(1500)
        grabador?.let { runCatching { it.stop() }; runCatching { it.release() } }
        grabador = null; salida = null; hilo = null
    }

    fun parar() {
        if (!grabando) return
        cortarGrabacion()
        reconocedor?.let { runCatching { it.stopListening(); it.cancel(); it.destroy() } }
        reconocedor = null
        grabando = false
        val segundos = (System.currentTimeMillis() - desde) / 1000.0
        val f = archivo
        archivo = null
        if (f != null && segundos >= 2 && f.length() > 44) alGrabar?.invoke(f, segundos)
    }

    private fun encabezadoWav(a: RandomAccessFile, datos: Int, tasa: Int) {
        fun le32(v: Int) = byteArrayOf(v.toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v shr 24).toByte())
        fun le16(v: Int) = byteArrayOf(v.toByte(), (v shr 8).toByte())
        a.seek(0)
        a.write("RIFF".toByteArray()); a.write(le32(36 + datos)); a.write("WAVE".toByteArray())
        a.write("fmt ".toByteArray()); a.write(le32(16)); a.write(le16(1)); a.write(le16(1))
        a.write(le32(tasa)); a.write(le32(tasa * 2)); a.write(le16(2)); a.write(le16(16))
        a.write("data".toByteArray()); a.write(le32(datos))
    }
}

// MARK: - Cámara

/** La cámara del sistema para sacar una foto (UIImagePickerController): con `visible`, se abre; al volver entrega la foto
 *  en JPEG (85 %) y llama a `alCerrar`. */
@Composable
fun Camara(visible: Boolean, alCerrar: () -> Unit, alSacar: (ByteArray) -> Unit) {
    val ctx = LocalContext.current
    var destino by remember { mutableStateOf<Uri?>(null) }
    val lanzador = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val u = destino
        if (ok && u != null) jpegDe(ctx, u)?.let(alSacar)
        alCerrar()
    }
    LaunchedEffect(visible) {
        if (!visible) return@LaunchedEffect
        val (_, u) = archivoCamaraAlta(ctx)
        destino = u
        runCatching { lanzador.launch(u) }.onFailure { alCerrar() }
    }
}

/** El archivo para la foto de la cámara, en cache/compartir (el FileProvider de nucleo/Abrir.kt). */
private fun archivoCamaraAlta(ctx: Context): Pair<File, Uri> {
    val f = archivoParaCompartir(ctx, "camara-${System.currentTimeMillis()}.jpg")
    return f to FileProvider.getUriForFile(ctx, PROVEEDOR_ARCHIVOS, f)
}

/** La foto en JPEG al 85 % (jpegData(compressionQuality: 0.85) de iOS), derecha; el lado mayor hasta 4096 para no
 *  quedarse sin memoria. */
private fun jpegDe(ctx: Context, uri: Uri): ByteArray? = runCatching {
    val lado = 4096
    val bmp: Bitmap = if (Build.VERSION.SDK_INT >= 28) {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(ctx.contentResolver, uri)) { dec, info, _ ->
            val m = maxOf(info.size.width, info.size.height)
            if (m > lado) dec.setTargetSize(info.size.width * lado / m, info.size.height * lado / m)
            dec.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    } else {
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } ?: return null
    }
    ByteArrayOutputStream().use { s -> bmp.compress(Bitmap.CompressFormat.JPEG, 85, s); s.toByteArray() }
}.getOrNull()

/** Los bytes y el nombre de un archivo elegido (fileImporter). */
private fun leerArchivo(ctx: Context, uri: Uri): Pair<ByteArray, String> {
    val datos = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: throw ErrorApi.desconocido
    val nombre = ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null
    } ?: "audio.m4a"
    return datos to nombre
}
