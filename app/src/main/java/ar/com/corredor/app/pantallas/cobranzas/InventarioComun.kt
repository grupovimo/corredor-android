package ar.com.corredor.app.pantallas.cobranzas

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ar.com.corredor.app.app.*
import ar.com.corredor.app.nucleo.*
import ar.com.corredor.app.tema.*
import coil3.compose.AsyncImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Instant
import java.util.UUID
import kotlin.math.roundToInt

// Lo común de inventarios, actas y devolución (web: lib/propiedades/inv-estados.ts): los destinos
// propios, los modelos chicos que se leen de otras áreas, la comparación de la devolución con la
// entrega y la nota de voz por ítem.

/** La recorrida forzada a un modo (web: /inventarios/<id>?tipo=entrega|devolucion). */
@Serializable
data class ARecorrida(val id: String, val tipo: String)

/** La devolución comparada con la entrega (web: /inventarios/<id>/devolucion). */
@Serializable
data class ADevolucion(val id: String)

// Los destinos nuevos de inventarios (`destinosInventarios()` en iOS, se encadena en `destinosCobranzas()`).
// Los mapea app/Registro.kt:
//   ARecorrida(id, tipo) → Inventario(propiedad = id, tipoPedido = tipo)
//   ADevolucion(id)      → Devolucion(propiedad = id)

/** Un contacto, con lo que necesitan el acta y la devolución (GET /v1/contactos/{id}). */
@Serializable
data class ContactoActa(val id: String, val nombre: String, val telefono: String? = null)

/** La inmobiliaria con sus datos legales (GET /v1/inmobiliaria): el membrete del acta lleva la matrícula. */
@Serializable
data class InmobiliariaActa(val nombre: String? = null, val legales: Legales? = null) {
    @Serializable
    data class Legales(val matricula: String? = null, val colegio: String? = null, val corredorResponsable: String? = null)

    /** «Mat. CUCICBA 1234», como la web. */
    val matricula: String?
        get() {
            val m = legales?.matricula?.takeIf { it.isNotBlank() } ?: return null
            return listOfNotNull("Mat.", legales?.colegio?.takeIf { it.isNotBlank() }, m).joinToString(" ")
        }
}

/** Un link para firmar desde el celular (GET …/actas/{acta}/links; sin el token). */
@Serializable
data class LinkFirma(val id: String, val parte: String, val venceEn: String, val estado: String, val url: String? = null)

/** Lo elegido en «¿Quién paga?» para un cambio (GET/PUT …/actas/{entrada}/devolucion). */
@Serializable
data class DecisionDevolucion(val quien: String, val importe: Double? = null)

object Inv {
    /** Nombre de cada estado (NOMBRE_ESTADO de la web). */
    fun nombre(e: String): String = ItemInventario.estados.firstOrNull { it.first == e }?.second ?: e

    /** Los tres botones de la recorrida: nuevo y muy bueno cuentan como «Bueno». */
    fun grupo(e: String): String = if (e == "regular" || e == "malo") e else "bueno"

    val rango: Map<String, Int> = mapOf("nuevo" to 4, "muy_bueno" to 3, "bueno" to 2, "regular" to 1, "malo" to 0)

    /** «12/03/2026», como `fechaNumerica` de la web. */
    fun numerica(s: String?): String {
        val p = (s ?: "").take(10).split("-").filter { it.isNotEmpty() }
        if (p.size != 3) return s ?: ""
        return "${p[2]}/${p[1]}/${p[0]}"
    }

    fun numerica(f: Instant): String {
        val d = f.atZone(Formato.zona).toLocalDate()
        return String.format(Formato.argentina, "%02d/%02d/%04d", d.dayOfMonth, d.monthValue, d.year)
    }

    fun primerNombre(n: String?): String = n?.split(" ")?.firstOrNull { it.isNotEmpty() } ?: ""

    /** «Cocina · mesada de granito». */
    fun rotulo(ambiente: String, descripcion: String): String {
        val d = descripcion.take(1).lowercase() + descripcion.drop(1)
        return "$ambiente · $d"
    }

    fun ambientes(items: List<String>): List<String> {
        val vistos = mutableListOf<String>()
        for (a in items) if (a !in vistos) vistos.add(a)
        return vistos
    }

    /** Lo que cambió entre la entrega y hoy (`comparar` de la web). */
    data class Cambio(
        val clave: String,
        val ambiente: String,
        val descripcion: String,
        val entrega: Acta.Item?,
        val hoy: Acta.Item?,
        /** Bajó un escalón hasta «regular»: lo normal es que sea desgaste por uso. */
        val desgaste: Boolean,
        /** Empeoró (o falta / bajó la cantidad): lo que se discute. */
        val empeoro: Boolean,
    ) {
        val id: String get() = clave
    }

    fun clave(ambiente: String, descripcion: String): String = "${ambiente.trim().lowercase()}|${descripcion.trim().lowercase()}"

    fun comparar(entrega: List<Acta.Item>, hoy: List<Acta.Item>): List<Cambio> {
        val antes = mutableListOf<Pair<String, Acta.Item>>()
        for (i in entrega) { val k = clave(i.ambiente, i.descripcion); if (antes.none { it.first == k }) antes.add(k to i) }
        val ahora = mutableMapOf<String, Acta.Item>()
        val ordenAhora = mutableListOf<String>()
        for (i in hoy) { val k = clave(i.ambiente, i.descripcion); if (ahora[k] == null) { ahora[k] = i; ordenAhora.add(k) } }
        val out = mutableListOf<Cambio>()
        for ((k, a) in antes) {
            val b = ahora[k]
            if (b != null && b.estado == a.estado && (b.cantidad ?: 1) == (a.cantidad ?: 1)) continue
            val caida = b?.let { (rango[a.estado] ?: 2) - (rango[it.estado] ?: 2) } ?: 99
            val desgaste = b != null && (b.cantidad ?: 1) == (a.cantidad ?: 1) && caida == 1 && b.estado == "regular"
            val empeoro = b == null || caida > 0 || (b.cantidad ?: 1) < (a.cantidad ?: 1)
            out.add(Cambio(k, a.ambiente, a.descripcion, a, b, desgaste, empeoro))
        }
        val claves = antes.map { it.first }.toSet()
        for (k in ordenAhora) if (k !in claves) {
            ahora[k]?.let { b -> out.add(Cambio(k, b.ambiente, b.descripcion, null, b, desgaste = false, empeoro = false)) }
        }
        fun peso(c: Cambio): Int = if (c.empeoro) (if (c.desgaste) 1 else 2) else 0
        // Lo que más empeoró, primero (estable, como el sort de JS).
        return out.sortedByDescending { peso(it) }
    }

    /** Un ítem vivo como queda en un acta. */
    fun comoActa(i: ItemInventario): Acta.Item =
        Acta.Item(i.ambiente, i.descripcion, i.cantidad ?: 1, i.estado, i.notas, i.fotos, i.fotosUrl)

    /** Un error en el aviso de abajo, con «Reintentar». */
    fun errorConReintento(estado: Estado, e: Throwable, reintentar: () -> Unit) {
        estado.avisar(e)
        if (estado.aviso?.ok == false) estado.aviso = estado.aviso?.copy(accion = "Reintentar" to reintentar)
    }
}

/** La nota de voz de un ítem (web: inv-voz.svelte.ts): se escucha y a la vez se transcribe en el teléfono
 *  (SpeechRecognizer es-AR); al terminar devuelve el texto, el archivo de audio y los segundos.
 *  Android no deja grabar el audio y reconocer la voz a la vez con el mismo micrófono: con el reconocedor
 *  disponible se transcribe (sin archivo); sin él, se graba el audio con MediaRecorder. */
class NotaDeVoz {
    var grabando by mutableStateOf(false)
    var texto by mutableStateOf("")
    var segundos by mutableIntStateOf(0)
    private var reconocedor: SpeechRecognizer? = null
    private var grabador: MediaRecorder? = null
    private var url: File? = null
    private var desde = 0L
    private var reloj: Job? = null
    private var acumulado = ""
    private val alcance: CoroutineScope = MainScope()

    /** Lo que devuelve `terminar` (la tupla de iOS). */
    data class Grabado(val texto: String, val audio: File?, val segundos: Int)

    private fun unir(a: String, b: String) = listOf(a, b).filter { it.isNotBlank() }.joinToString(" ")

    private fun intento(): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-AR")
        .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)

    private fun escuchar() {
        val r = reconocedor ?: return
        runCatching { r.startListening(intento()) }
    }

    /** Arranca a grabar. Devuelve el error para mostrar si no se pudo. `pedirPermiso`: el del micrófono
     *  (AVAudioApplication.requestRecordPermission en iOS). */
    suspend fun empezar(ctx: Context, pedirPermiso: suspend (String) -> Boolean): String? {
        if (grabando) return null
        val mic = pedirPermiso("microfono")
        if (!mic) return "No hay permiso para usar el micrófono."
        acumulado = ""
        url = null
        if (SpeechRecognizer.isRecognitionAvailable(ctx)) {
            val r = runCatching { SpeechRecognizer.createSpeechRecognizer(ctx) }.getOrNull() ?: return "No encontramos un micrófono."
            r.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
                override fun onPartialResults(partialResults: Bundle?) {
                    val p = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: return
                    texto = unir(acumulado, p)
                }
                override fun onResults(results: Bundle?) {
                    val p = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                    acumulado = unir(acumulado, p)
                    texto = acumulado
                    // El reconocedor corta en cada silencio: mientras se graba, sigue escuchando.
                    if (grabando) escuchar()
                }
                override fun onError(error: Int) {
                    if (grabando && error != SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) escuchar()
                }
            })
            reconocedor = r
            escuchar()
        } else {
            val f = File(ctx.cacheDir, "nota-${UUID.randomUUID()}.m4a")
            val g = (if (Build.VERSION.SDK_INT >= 31) MediaRecorder(ctx) else @Suppress("DEPRECATION") MediaRecorder())
            try {
                g.setAudioSource(MediaRecorder.AudioSource.MIC)
                g.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                g.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                g.setAudioEncodingBitRate(32000)
                g.setOutputFile(f.absolutePath)
                g.prepare()
                g.start()
            } catch (e: Exception) {
                runCatching { g.release() }
                return "No encontramos un micrófono."
            }
            grabador = g
            url = f
        }
        texto = ""
        segundos = 0
        desde = System.currentTimeMillis()
        grabando = true
        Haptico.impacto()
        reloj = alcance.launch {
            while (isActive) {
                delay(500)
                segundos = ((System.currentTimeMillis() - desde) / 1000).toInt()
            }
        }
        return null
    }

    /** Termina y devuelve lo grabado (el audio sólo si duró algo). */
    suspend fun terminar(): Grabado {
        if (!grabando) return Grabado("", null, 0)
        reloj?.cancel()
        grabando = false
        runCatching { reconocedor?.stopListening() }
        runCatching { grabador?.stop() }
        runCatching { grabador?.release() }
        grabador = null
        val s = maxOf(1, ((System.currentTimeMillis() - desde) / 1000.0).roundToInt())
        // Un momento para que llegue lo último de la transcripción.
        delay(400)
        runCatching { reconocedor?.destroy() }
        reconocedor = null
        val dur = (System.currentTimeMillis() - desde) / 1000.0
        return Grabado(texto.trim(), if (dur > 1.2) url else null, s)
    }

    companion object {
        /** «0:12». */
        fun duracion(s: Int): String = "${s / 60}:${String.format(Formato.argentina, "%02d", s % 60)}"
    }
}

// MARK: - Piezas de inventarios y actas (compartidas por Actas, ListaActas y Devolucion)

/** Un botón de ícono de la barra (ToolbarItem con Image). */
@Composable
internal fun BotonBarraActa(simbolo: String, etiqueta: String, alTocar: () -> Unit) {
    Box(Modifier.size(44.dp).clickable(onClick = alTocar).semantics { contentDescription = etiqueta }, contentAlignment = Alignment.Center) {
        Icono(simbolo, 19f, C.accion)
    }
}

/** Una foto elegida o sacada, como JPEG al 80 % (UIImage.jpegData(compressionQuality: 0.8)). */
internal suspend fun jpegDe(ctx: Context, uri: Uri): ByteArray? = withContext(Dispatchers.IO) {
    val crudo = runCatching { ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull() ?: return@withContext null
    val b = BitmapFactory.decodeByteArray(crudo, 0, crudo.size) ?: return@withContext crudo
    jpegDe(b)
}

internal fun jpegDe(b: Bitmap): ByteArray = ByteArrayOutputStream().also { b.compress(Bitmap.CompressFormat.JPEG, 80, it) }.toByteArray()
