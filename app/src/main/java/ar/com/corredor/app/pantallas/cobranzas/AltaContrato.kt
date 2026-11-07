package ar.com.corredor.app.pantallas.cobranzas

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ar.com.corredor.app.app.*
import ar.com.corredor.app.nucleo.*
import ar.com.corredor.app.tema.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.ByteArrayOutputStream
import java.text.Normalizer
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.roundToLong

// MARK: - Lo que se carga (y el borrador de /v1/borradores?clase=contrato, con la forma de la web)

/** Algo elegido en el alta: una propiedad o un contacto. */
@Serializable
data class ElegidoAlta(val id: String, val nombre: String, val detalle: String? = null)

/** Un documento ya subido para colgarlo del contrato al crearlo. */
@Serializable
data class DocumentoAlta(val id: String, val nombre: String, val tipo: String, val bytes: Int)

/** Los datos del alta, como `DatosContrato` de lib/contratos/alta.ts: el borrador se sigue igual en
 *  la web y en la app. Los nulos se escriben (un «Sin ajuste» tiene que llegar como `indice: null`): ver `json()`. */
@Serializable
data class DatosContratoAlta(
    val tipo: String = "alquiler",
    val propiedad: ElegidoAlta? = null,
    val inquilino: ElegidoAlta? = null,
    val propietario: ElegidoAlta? = null,
    val garanteNombre: String = "",
    val garanteTelefono: String = "",
    val garanteNotas: String = "",
    val inicio: String = primeroDelMesQueViene(Formato.hoy().toString()),
    /** Duración en meses (el fin se calcula). */
    val meses: Int? = 36,
    val monto: Double? = null,
    val moneda: String = "ARS",
    val diaVencimiento: Int = 10,
    val indice: String? = "icl",
    val ajusteMeses: Int? = 6,
    val ajustePorcentaje: Double? = null,
    val honorarios: Double? = null,
    val comision: Double? = null,
    val punitorioDiario: Double? = null,
    val deposito: Double? = null,
    val documentos: List<DocumentoAlta> = emptyList(),
    val notas: String = "",
    val renueva: Renueva? = null,
) {
    @Serializable
    data class Renueva(val id: String, val titulo: String)

    val alquiler: Boolean get() = tipo == "alquiler"

    /** Un borrador como venga, completado con lo que falta (como `desdeBorrador`). */
    fun normalizado(): DatosContratoAlta = copy(
        tipo = if (tipo == "alquiler" || tipo == "venta") tipo else "alquiler",
        moneda = if (moneda == "USD") "USD" else "ARS",
    )

    /** Con los nulos escritos (el encode de iOS). */
    fun json(): JsonElement = conNulos.encodeToJsonElement(this)

    companion object {
        private val conNulos = Json { explicitNulls = true; encodeDefaults = true }

        fun primeroDelMesQueViene(hoy: String): String {
            val d = Fecha.fecha(hoy.take(7) + "-01") ?: return hoy
            return Formato.diaISO(d.plusMonths(1))
        }

        /** Lee los datos de un borrador (si no se pueden leer, unos nuevos). */
        fun de(e: JsonElement?): DatosContratoAlta =
            e?.let { runCatching { Api.json.decodeFromJsonElement(serializer(), it).normalizado() }.getOrNull() } ?: DatosContratoAlta()
    }
}

/** `{paso, datos}` en /v1/borradores?clase=contrato. */
data class BorradorContrato(val paso: Int = 0, val datos: DatosContratoAlta = DatosContratoAlta()) {
    fun json(): JsonObject = buildJsonObject {
        put("paso", paso)
        put("datos", datos.json())
    }

    companion object {
        fun de(e: JsonElement?): BorradorContrato {
            val o = e as? JsonObject ?: return BorradorContrato()
            val paso = runCatching { o["paso"]?.jsonPrimitive?.contentOrNull?.toInt() }.getOrNull() ?: 0
            return BorradorContrato(min(4, max(0, paso)), DatosContratoAlta.de(o["datos"]))
        }
    }
}

/** Un borrador de GET /v1/borradores?clase=contrato. */
data class BorradorContratoAPI(val id: String, val datos: BorradorContrato, val actualizadoEn: Instant?) {
    companion object {
        fun de(o: JsonObject): BorradorContratoAPI? {
            val id = runCatching { o["id"]?.jsonPrimitive?.contentOrNull }.getOrNull() ?: return null
            return BorradorContratoAPI(id, BorradorContrato.de(o["datos"]),
                Fecha.iso(runCatching { o["actualizadoEn"]?.jsonPrimitive?.contentOrNull }.getOrNull()))
        }
    }
}

// MARK: - Alta

/** Lo que en iOS son los @State de AltaContrato. */
@Stable
private class AltaEstado {
    var d by mutableStateOf(DatosContratoAlta())
    var paso by mutableStateOf(0)
    var idBorrador by mutableStateOf<String?>(null)
    var errores by mutableStateOf<Map<String, String>>(emptyMap())
    var creando by mutableStateOf(false)
    var cargado by mutableStateOf(false)
    var sinPermiso by mutableStateOf(false)
    var paraSeguir by mutableStateOf<List<BorradorContratoAPI>>(emptyList())
    // Guardado.
    var guardado by mutableStateOf("nada")
    var guardadoEn by mutableStateOf<Instant?>(null)
    var ultimoGuardado = ""
    val fila = Mutex()
    val envioAlta = UUID.randomUUID().toString()
    // Leer el PDF.
    var leyendo by mutableStateOf(false)
    var leidoDe by mutableStateOf<String?>(null)
    var dudosos by mutableStateOf<List<String>>(emptyList())
    var sugeridos by mutableStateOf<Map<String, String>>(emptyMap())
    // Elegir y subir.
    var eligiendo by mutableStateOf<String?>(null)
    var subiendoDoc by mutableStateOf(false)
    // Textos de los campos con coma decimal.
    var tHonorarios by mutableStateOf("")
    var tComision by mutableStateOf("")
    var tPunitorio by mutableStateOf("")
    var tPorcentaje by mutableStateOf("")
    var tDia by mutableStateOf("10")
    var tMeses by mutableStateOf("")

    val pasos: List<Int> get() = if (d.alquiler) listOf(0, 1, 2, 3, 4) else listOf(0, 1, 3, 4)
    val indicePaso: Int get() = max(0, pasos.indexOf(paso))
    val ultimo: Boolean get() = indicePaso == pasos.size - 1
    val fin: String? get() = AltaContrato.finDe(d.inicio, d.meses)
    val kicker: String get() = d.renueva?.let { "Renovación · ${it.titulo}" } ?: (if (d.alquiler) "Contrato nuevo" else "Venta nueva")
    val titulo: String get() = if (d.renueva != null) "Renovar contrato" else "Nuevo contrato"
    val yaLeido: Boolean get() = dudosos.isNotEmpty() || sugeridos.isNotEmpty() || d.documentos.any { it.id == leidoDe }
    val dudososAca: List<String> get() = if (paso == 0) emptyList() else dudosos.filter { AltaContrato.pasoDe[it] == paso }

    fun textosDesdeDatos() {
        tHonorarios = AltaContrato.textoPorcentaje(d.honorarios)
        tComision = AltaContrato.textoPorcentaje(d.comision)
        tPunitorio = AltaContrato.textoPorcentaje(d.punitorioDiario)
        tPorcentaje = AltaContrato.textoPorcentaje(d.ajustePorcentaje)
        tDia = d.diaVencimiento.toString()
        tMeses = d.meses?.let { if (it in listOf(24, 36)) "" else it.toString() } ?: ""
    }

    fun cuerpoBorrador(): BorradorContrato = BorradorContrato(paso, d)
    fun firma(): String = runCatching { Api.json.encodeToString(JsonObject.serializer(), cuerpoBorrador().json()) }.getOrDefault("")

    fun seguirBorrador(b: BorradorContratoAPI) {
        d = b.datos.datos
        paso = b.datos.paso
        if (paso !in pasos) paso = pasos.lastOrNull() ?: 0
        idBorrador = b.id
        guardado = "listo"
        guardadoEn = b.actualizadoEn
        textosDesdeDatos()
        ultimoGuardado = firma()
    }

    /** Lo que falta para seguir desde un paso (campo → mensaje). */
    fun faltan(p: Int): Map<String, String> {
        val e = mutableMapOf<String, String>()
        if (p == 0) {
            if (d.propiedad == null) e["propiedadId"] = "Elegí la propiedad del contrato."
            if (d.inquilino == null) e["contactoId"] = if (d.alquiler) "Elegí quién alquila." else "Elegí quién compra."
        }
        if (p == 1) {
            if (Fecha.fecha(d.inicio) == null) e["inicio"] = "Poné la fecha de inicio."
            if (d.alquiler && (d.meses ?: 0) <= 0) e["fin"] = "Poné cuántos meses dura."
            if ((d.monto ?: 0.0) <= 0) e["monto"] = if (d.alquiler) "Poné el alquiler del primer mes." else "Poné el monto de la venta."
        }
        if (p == 2 && d.alquiler && d.indice != null) {
            if ((d.ajusteMeses ?: 0) <= 0) e["ajusteMeses"] = "Elegí cada cuántos meses se ajusta."
            if (d.indice == "fijo" && (d.ajustePorcentaje ?: 0.0) <= 0) e["ajustePorcentaje"] = "Poné el porcentaje de cada aumento."
        }
        return e
    }

    fun revisar(hasta: Int): Boolean {
        for (p in pasos.filter { it < hasta }) {
            val e = faltan(p)
            if (e.isNotEmpty()) { errores = e; paso = p; return false }
        }
        errores = emptyMap()
        return true
    }

    fun elegirIndice(v: String?) {
        d = d.copy(indice = v)
        if (v != null && (d.ajusteMeses ?: 0) <= 0) d = d.copy(ajusteMeses = if (v == "ipc") 3 else 6)
    }
}

@Serializable private data class CreadoAlta(val id: String)
@Serializable private data class PropAlta(val propietarioId: String? = null)
@Serializable private data class CtAlta(val id: String, val nombre: String)
@Serializable private data class DocAlta(val archivoId: String, val contratoId: String, val nombre: String)
@Serializable private data class LecturaAlta(val datos: AltaContrato.Leido, val prueba: Boolean? = null)
@Serializable private data class ArchivoAlta(val archivoId: String)

@Serializable
private data class CuerpoAlta(
    val tipo: String, val estado: String = "vigente", val propiedadId: String? = null, val contactoId: String? = null, val propietarioId: String? = null,
    val agenteId: String? = null, val inicio: String, val fin: String? = null, val monto: Double, val moneda: String, val ajusteMeses: Int? = null,
    val ajusteIndice: String? = null, val ajustePorcentaje: Double? = null, val comision: Double, val honorarios: Double? = null,
    val punitorioDiario: Double? = null, val deposito: Double? = null, val diaVencimiento: Int? = null, val garanteNombre: String? = null,
    val garanteTelefono: String? = null, val garanteNotas: String? = null, val notas: String? = null,
)

/** El alta de un contrato (/contratos/nuevo): pasos cortos (Partes → Plazo y monto → Ajuste →
 *  Condiciones → Documentos; una venta saltea Ajuste), validación por paso, guardado solo en un
 *  borrador de /v1/borradores?clase=contrato («Guardado hace 2 s», «Seguir después») y «Subir el PDF y
 *  que lo lea». Con `renovar` arranca desde el contrato anterior; con `operacion`, desde la operación. */
@Composable
fun AltaContrato(
    leer: Boolean = false,
    renovar: String? = null,
    operacion: String? = null,
    /** Seguir un borrador (el de la web, el de otro aparato). */
    borrador: String? = null,
    /** Apilada (desde una ruta) en vez de en una hoja. */
    apilada: Boolean = false,
) {
    val estado = LocalEstado.current
    val ctx = LocalContext.current
    val alcance = rememberCoroutineScope()
    val cerrarHoja = LocalCerrarHoja.current
    // PARA EL NÚCLEO: en iOS es @Environment(\.dismiss): apilada vuelve atrás (estado.volver()), en hoja la cierra.
    val cerrar: () -> Unit = { if (apilada) estado.volver() else cerrarHoja() }
    val s = remember { AltaEstado() }
    val desplazamiento = rememberScrollState()

    fun programarGuardado(ya: Boolean = false) {
        if (!s.cargado || s.creando || s.sinPermiso) return
        alcance.launch {
            if (!ya) delay(0)
            s.fila.withLock { guardarYaAlta(s, estado.api) }
        }
    }

    suspend fun propietarioDe(p: ElegidoAlta) = propietarioDeAlta(s, estado.api, p)

    /** «Seguir después»: queda en borrador y se sigue cuando se quiera. */
    suspend fun salir() {
        if (!s.sinPermiso && s.d != DatosContratoAlta()) {
            s.fila.withLock { guardarYaAlta(s, estado.api) }
        }
        s.idBorrador?.let { id ->
            estado.sugerir("Quedó en borrador. Seguilo cuando quieras.", "Seguir") { estado.abrir(AAltaContrato(borrador = id)) }
        }
        cerrar()
    }

    suspend fun crear() {
        if (!s.revisar(99)) return
        s.fila.withLock { guardarYaAlta(s, estado.api) }
        s.creando = true
        val ruta = "/v1/contratos" + (s.idBorrador?.let { "?borrador=$it" } ?: "")
        val n: CreadoAlta
        try {
            val a = s.d.alquiler
            val d = s.d
            val cuerpo = CuerpoAlta(
                tipo = d.tipo, propiedadId = d.propiedad?.id, contactoId = d.inquilino?.id, propietarioId = d.propietario?.id, agenteId = estado.usuario?.id,
                inicio = d.inicio, fin = if (a) AltaContrato.finDe(d.inicio, d.meses) else null, monto = d.monto ?: 0.0, moneda = d.moneda,
                ajusteMeses = if (a && d.indice != null) d.ajusteMeses else null, ajusteIndice = if (a) d.indice else null,
                ajustePorcentaje = if (a && d.indice == "fijo") d.ajustePorcentaje else null, comision = d.comision ?: 0.0, honorarios = d.honorarios,
                punitorioDiario = if (a) d.punitorioDiario else null, deposito = d.deposito, diaVencimiento = if (a) d.diaVencimiento else null,
                garanteNombre = d.garanteNombre.ifBlank { null }, garanteTelefono = d.garanteTelefono.ifBlank { null },
                garanteNotas = d.garanteNotas.ifBlank { null }, notas = d.notas.ifBlank { null },
            )
            val datos = estado.api.pedir("POST", ruta, cuerpo = Api.json.encodeToString(CuerpoAlta.serializer(), cuerpo).toByteArray(), envio = s.envioAlta)
            n = Api.json.decodeFromString(CreadoAlta.serializer(), datos)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val ea = e as? ErrorApi
            if (ea != null && ea.campos.isNotEmpty()) {
                // La API marcó campos: volver al primer paso que los tiene y marcarlos (como AltaContrato.svelte).
                s.creando = false
                s.errores = ea.campos
                val enPasos = s.pasos
                s.paso = ea.campos.keys.map { AltaContrato.pasoDelCampo(it) }.filter { it in enPasos }.minOrNull() ?: s.paso
                Haptico.error()
                estado.avisar(if (ea.detalle.isEmpty()) "Revisá lo marcado." else ea.detalle, ok = false)
                return
            }
            s.creando = false
            estado.sugerir(ea?.let { if (it.detalle.isEmpty()) it.titulo else it.detalle } ?: "No se creó el contrato.", "Reintentar") {
                alcance.launch { crear() }
            }
            return
        }
        // Los documentos ya subidos se cuelgan del contrato.
        val sinSubir = mutableListOf<String>()
        for (doc in s.d.documentos) {
            try {
                estado.api.pedir("POST", "/v1/documentos", cuerpo = Api.json.encodeToString(DocAlta.serializer(), DocAlta(doc.id, n.id, doc.nombre)).toByteArray(),
                    envio = "${s.envioAlta}-${doc.id.take(8)}")
            } catch (e: CancellationException) { throw e } catch (e: Exception) { sinSubir.add(doc.nombre) }
        }
        estado.version += 1
        cerrar()
        estado.abrir(AFichaContrato(id = n.id))
        estado.avisar(if (sinSubir.isEmpty()) "Contrato cargado. Ya calcula ajustes y cobros." else "Contrato cargado. Faltó sumar ${sinSubir.joinToString(", ")}: subilo desde la ficha.",
            ok = sinSubir.isEmpty())
    }

    suspend fun seguir() {
        if (s.creando) return
        val e = s.faltan(s.paso)
        if (e.isNotEmpty()) { s.errores = e; Haptico.error(); return }
        s.errores = emptyMap()
        if (!s.ultimo) { s.paso = s.pasos[s.indicePaso + 1]; return }
        crear()
    }

    suspend fun leerDatos(datos: ByteArray, nombre: String, tipo: String) {
        if (s.leyendo) return
        s.leyendo = true
        try {
            val sub = estado.api.subir(datos, nombre = nombre, tipo = tipo, uso = "documento")
            val r: LecturaAlta = estado.api.enviar("POST", "/v1/contratos/leer", ArchivoAlta(sub.id))
            val l = r.datos
            var nuevo = AltaContrato.aplicarLectura(s.d, l)
            if (nuevo.documentos.none { it.id == sub.id }) {
                nuevo = nuevo.copy(documentos = nuevo.documentos + DocumentoAlta(sub.id, sub.nombre ?: nombre, sub.tipo ?: tipo, sub.bytes ?: datos.size))
            }
            if (s.d.propiedad == null && l.direccion != null) nuevo = nuevo.copy(propiedad = encontrarAlta(estado.api, "propiedad", l.direccion, null))
            if (s.d.inquilino == null && l.inquilino?.nombre != null) nuevo = nuevo.copy(inquilino = encontrarAlta(estado.api, "contacto", l.inquilino.nombre, l.inquilino.nombre))
            if (s.d.propietario == null && l.propietario?.nombre != null) nuevo = nuevo.copy(propietario = encontrarAlta(estado.api, "contacto", l.propietario.nombre, l.propietario.nombre))
            val antes = s.d
            val antesTextos = listOf(s.tHonorarios, s.tComision, s.tPunitorio, s.tPorcentaje, s.tDia, s.tMeses)
            s.d = nuevo
            s.textosDesdeDatos()
            s.leidoDe = sub.id
            val sug = mutableMapOf<String, String>()
            if (nuevo.propiedad == null) l.direccion?.let { sug["propiedad"] = it }
            if (nuevo.inquilino == null) l.inquilino?.nombre?.let { sug["inquilino"] = it }
            if (nuevo.propietario == null) l.propietario?.nombre?.let { sug["propietario"] = it }
            s.sugeridos = sug
            s.dudosos = (l.dudosos ?: emptyList()).filter { AltaContrato.pasoDe[it] != null }
            if (antes.propiedad == null) nuevo.propiedad?.let { propietarioDe(it) }
            val texto = if (r.prueba == true) "Datos de prueba: la lectura no está configurada"
            else if (s.dudosos.isEmpty()) "Lo leímos. Revisá y seguí"
            else "Lo leímos. Revisá ${if (s.dudosos.size == 1) "lo marcado" else "las ${s.dudosos.size} cosas marcadas"}"
            estado.avisar(texto) {
                s.d = antes
                s.tHonorarios = antesTextos[0]; s.tComision = antesTextos[1]; s.tPunitorio = antesTextos[2]
                s.tPorcentaje = antesTextos[3]; s.tDia = antesTextos[4]; s.tMeses = antesTextos[5]
                s.dudosos = emptyList()
                s.sugeridos = emptyMap()
                s.leidoDe = null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            estado.avisar("No se pudo leer · ${(e as? ErrorApi)?.detalle ?: "probá de nuevo"}", ok = false)
        } finally {
            s.leyendo = false
        }
    }

    suspend fun subirDoc(datos: ByteArray, nombre: String, tipo: String) {
        s.subiendoDoc = true
        try {
            val a = estado.api.subir(datos, nombre = nombre, tipo = tipo, uso = "documento")
            s.d = s.d.copy(documentos = s.d.documentos + DocumentoAlta(a.id, a.nombre ?: nombre, a.tipo ?: tipo, a.bytes ?: datos.size))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            estado.avisar("No se subió · ${(e as? ErrorApi)?.detalle ?: ""}", ok = false)
        } finally {
            s.subiendoDoc = false
        }
    }

    // .fileImporter y PhotosPicker: el selector de documentos y el de fotos del sistema.
    val elegirLectura = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        if (u != null) alcance.launch {
            val a = leerUriAlta(ctx, u)
            if (a == null) estado.avisar("No se pudo leer el archivo.", ok = false) else leerDatos(a.first, a.second, a.third)
        }
    }
    val fotoLectura = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { u ->
        if (u != null) alcance.launch {
            val a = leerUriAlta(ctx, u) ?: return@launch
            leerDatos(aJpegAlta(a.first), "Contrato.jpg", "image/jpeg")
        }
    }
    val elegirDocs = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { us ->
        alcance.launch {
            for (u in us) {
                val a = leerUriAlta(ctx, u)
                if (a == null) estado.avisar("No se pudo leer el archivo.", ok = false) else subirDoc(a.first, a.second, a.third)
            }
        }
    }
    val fotoDoc = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { u ->
        if (u != null) alcance.launch {
            val a = leerUriAlta(ctx, u) ?: return@launch
            subirDoc(aJpegAlta(a.first), "Foto ${Formato.hoy().toString()}.jpg", "image/jpeg")
        }
    }
    val tiposArchivo = arrayOf("application/pdf", "image/*")

    // MARK: Arranque
    LaunchedEffect(Unit) {
        if (s.cargado) return@LaunchedEffect
        val bs: List<BorradorContratoAPI> = try {
            estado.api.get<List<JsonObject>>("/v1/borradores", listOf("clase" to "contrato")).mapNotNull { BorradorContratoAPI.de(it) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if ((e as? ErrorApi)?.estado == 403) {
                s.sinPermiso = true
                s.cargado = true
                return@LaunchedEffect
            }
            emptyList()
        }
        s.paraSeguir = bs
        if (borrador != null) {
            val b = bs.firstOrNull { it.id == borrador }
            if (b != null) s.seguirBorrador(b) else estado.avisar("Ese borrador ya no está: empezamos uno nuevo.")
        } else if (renovar != null) {
            desdeContratoAlta(s, estado.api, renovar)
        } else if (operacion != null) {
            desdeOperacionAlta(s, estado.api, operacion)
        }
        s.tDia = s.d.diaVencimiento.toString()
        s.ultimoGuardado = s.firma()
        s.cargado = true
        if (leer) elegirLectura.launch(tiposArchivo)
    }
    // .onChange(of: d) / .onChange(of: paso): se guarda solo a los 700 ms.
    LaunchedEffect(s.d, s.paso) {
        if (!s.cargado || s.creando || s.sinPermiso) return@LaunchedEffect
        delay(700)
        programarGuardado(ya = true)
    }
    LaunchedEffect(s.paso) { desplazamiento.animateScrollTo(0) }
    // .interactiveDismissDisabled: con algo cargado, «atrás» es «Seguir después» (queda en el borrador).
    BackHandler(enabled = !s.sinPermiso && s.d != DatosContratoAlta()) { alcance.launch { salir() } }

    val cuerpo: @Composable () -> Unit = {
        Box(Modifier.fillMaxSize().then(if (apilada) Modifier else Modifier.background(C.fondo))) {
            if (s.sinPermiso) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EstadoVacio("lock", "No podés cargar contratos",
                        "Tu rol es Lector: podés ver los contratos y sus cobros. Para cargar uno, pedile acceso a la dueña de la inmobiliaria.")
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    Column(
                        Modifier.weight(1f).fillMaxWidth().verticalScroll(desplazamiento).padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        CabezaAlta(s) { programarGuardado(ya = true) }
                        if (s.dudososAca.isNotEmpty()) {
                            Etiqueta("No se lee bien en el contrato: ${s.dudososAca.mapNotNull { AltaContrato.nombreDato[it] }.joinToString(", ")}. Revisalo.",
                                "questionmark.circle", Estilo.footnote, C.avisoTinta,
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(C.avisoFondo).padding(12.dp))
                        }
                        when (s.paso) {
                            0 -> PasoPartesAlta(s, renovar, operacion,
                                leerArchivo = { elegirLectura.launch(tiposArchivo) },
                                leerFoto = { fotoLectura.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) })
                            1 -> PasoPlazoAlta(s)
                            2 -> PasoAjusteAlta(s)
                            3 -> PasoCondicionesAlta(s)
                            else -> PasoDocumentosAlta(s,
                                subirArchivo = { elegirDocs.launch(tiposArchivo) },
                                subirFoto = { fotoDoc.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                                sacar = { doc ->
                                    val antes = s.d.documentos
                                    s.d = s.d.copy(documentos = s.d.documentos.filter { it.id != doc.id })
                                    estado.avisar("Sacaste «${doc.nombre}»") { s.d = s.d.copy(documentos = antes) }
                                })
                        }
                    }
                    PieAlta(s) { alcance.launch { seguir() } }
                }
            }
        }
    }

    if (apilada) {
        PantallaApilada(s.titulo, acciones = if (!s.sinPermiso) ({ BotonLink("Seguir después", Estilo.body) { alcance.launch { salir() } } }) else null) {
            cuerpo()
        }
    } else {
        Column(Modifier.fillMaxSize().background(C.fondo)) {
            BarraHoja(s.titulo, cancelar = if (s.sinPermiso) "Cerrar" else "Seguir después", alCancelar = { alcance.launch { salir() } })
            Box(Modifier.weight(1f)) { cuerpo() }
        }
    }

    HojaDe(s.eligiendo, { s.eligiendo = null }) { clave ->
        when (clave) {
            "propiedad" -> ElegirPropiedadAlta(s.d.tipo, s.sugeridos["propiedad"]) { p ->
                s.d = s.d.copy(propiedad = p)
                s.sugeridos = s.sugeridos - "propiedad"
                s.errores = s.errores - "propiedadId"
                alcance.launch { propietarioDe(p) }
            }
            "inquilino" -> ElegirParteAlta(if (s.d.alquiler) "inquilino" else "comprador", s.sugeridos["inquilino"]) { c ->
                s.d = s.d.copy(inquilino = c)
                s.sugeridos = s.sugeridos - "inquilino"
                s.errores = s.errores - "contactoId"
            }
            else -> ElegirParteAlta("propietario", s.sugeridos["propietario"]) { c ->
                s.d = s.d.copy(propietario = c)
                s.sugeridos = s.sugeridos - "propietario"
            }
        }
    }
}

// MARK: Guardado y datos (fuera de la vista)

private suspend fun guardarYaAlta(s: AltaEstado, api: Api) {
    val f = s.firma()
    if (f == s.ultimoGuardado || s.creando) return
    s.guardado = "guardando"
    try {
        val b = s.idBorrador
        if (b != null) {
            api.hacer("PUT", "/v1/borradores/$b", s.cuerpoBorrador().json())
        } else {
            val c: CreadoAlta = api.enviar("POST", "/v1/borradores?clase=contrato", s.cuerpoBorrador().json())
            s.idBorrador = c.id
        }
        s.ultimoGuardado = f
        s.guardado = "listo"
        s.guardadoEn = Instant.now()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        s.guardado = "error"
    }
}

/** El propietario de la propiedad, si está cargado, viene solo. */
private suspend fun propietarioDeAlta(s: AltaEstado, api: Api, p: ElegidoAlta) {
    if (s.d.propietario != null) return
    val pr: PropAlta = api.opcional("/v1/propiedades/${p.id}") ?: return
    val pid = pr.propietarioId ?: return
    val c: CtAlta = api.opcional("/v1/contactos/$pid") ?: return
    if (s.d.propietario != null) return
    s.d = s.d.copy(propietario = ElegidoAlta(c.id, c.nombre, "Propietario de la propiedad"))
}

/** Renovar: las mismas partes y condiciones, desde el día después del fin, con el alquiler de hoy y
 *  la duración del anterior. */
private suspend fun desdeContratoAlta(s: AltaEstado, api: Api, id: String) {
    val c: ContratoAPI = api.opcional("/v1/contratos/$id") ?: return
    val a: AjusteContrato? = api.opcional("/v1/contratos/$id/ajuste")
    var x = DatosContratoAlta(
        tipo = c.tipo ?: "alquiler",
        propiedad = c.propiedadId?.let { ElegidoAlta(it, c.propiedadTitulo ?: "Propiedad") },
        inquilino = c.contactoId?.let { ElegidoAlta(it, c.contactoNombre ?: "Inquilino") },
        propietario = c.propietarioId?.let { ElegidoAlta(it, c.propietarioNombre ?: "Propietario") },
        garanteNombre = c.garanteNombre ?: "",
        garanteTelefono = c.garanteTelefono ?: "",
        garanteNotas = c.garanteNotas ?: "",
    )
    Fecha.fecha(c.fin)?.let { x = x.copy(inicio = Formato.diaISO(it.plusDays(1))) }
    val i = Fecha.fecha(c.inicio)
    val f = Fecha.fecha(c.fin)
    x = if (i != null && f != null) x.copy(meses = max(1, (ChronoUnit.DAYS.between(i, f) / 30.44).roundToInt())) else x.copy(meses = 36)
    x = x.copy(
        monto = (a?.vigente ?: c.monto)?.let { Math.round(it).toDouble() },
        moneda = if (c.moneda == "USD") "USD" else "ARS",
        diaVencimiento = c.diaVencimiento ?: 1,
        indice = c.ajusteIndice,
        ajusteMeses = c.ajusteMeses,
        ajustePorcentaje = c.ajustePorcentaje,
        honorarios = c.honorarios,
        comision = if ((c.comision ?: 0.0) > 0) c.comision else null,
        punitorioDiario = c.punitorioDiario,
        deposito = c.deposito,
        renueva = DatosContratoAlta.Renueva(c.id, c.propiedadTitulo ?: "el contrato"),
    )
    s.d = x
    s.textosDesdeDatos()
}

/** «Hacer el contrato» de una operación en cierre: la propiedad, el contacto y el valor. */
private suspend fun desdeOperacionAlta(s: AltaEstado, api: Api, id: String) {
    val o: Operacion = api.opcional("/v1/operaciones/$id") ?: return
    o.propiedadId?.let { s.d = s.d.copy(propiedad = ElegidoAlta(it, o.propiedadTitulo ?: "Propiedad")) }
    o.contactoId?.let { s.d = s.d.copy(inquilino = ElegidoAlta(it, o.contactoNombre ?: "Contacto")) }
    if (o.moneda == "ARS" || o.moneda == "USD") s.d = s.d.copy(moneda = o.moneda ?: "ARS")
    o.valor?.let { if (it > 0) s.d = s.d.copy(monto = it) }
    s.d.propiedad?.let { propietarioDeAlta(s, api, it) }
}

/** Una sola coincidencia, o la que se llama igual: se elige sola. Si no, queda sugerida. */
private suspend fun encontrarAlta(api: Api, tipo: String, q: String, igual: String?): ElegidoAlta? {
    val r: List<Resultado> = api.opcional("/v1/buscar", listOf("tipo" to tipo, "q" to q)) ?: return null
    val x = igual?.let { n -> r.firstOrNull { AltaContrato.mismoNombre(it.titulo, n) } } ?: (if (r.size == 1) r.first() else null)
    return x?.let { ElegidoAlta(it.id, it.titulo, it.subtitulo) }
}

// MARK: Cabecera y pie

@Composable
private fun CabezaAlta(s: AltaEstado, reintentar: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            s.pasos.forEachIndexed { i, _ ->
                Box(Modifier.weight(1f).height(4.dp).alpha(if (i < s.indicePaso) 0.5f else 1f).clip(RoundedCornerShape(50))
                    .background(if (i <= s.indicePaso) C.accion else C.hundida))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Texto(s.d.propiedad?.nombre ?: s.kicker, Estilo.footnote, C.texto2, Modifier.weight(1f), lineas = 1)
            EstadoGuardadoAlta(s, reintentar)
        }
        Texto(AltaContrato.titulos[s.paso], Estilo.titulo, C.texto)
    }
}

/** TimelineView(.periodic(by: 1)): «Guardado hace 2 s» se actualiza solo. */
@Composable
private fun EstadoGuardadoAlta(s: AltaEstado, reintentar: () -> Unit) {
    var ahora by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(Unit) { while (true) { delay(1000); ahora = Instant.now() } }
    val color = if (s.guardado == "error") C.errorTinta else C.texto3
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icono(when (s.guardado) { "error" -> "exclamationmark.icloud"; "guardando" -> "arrow.triangle.2.circlepath.icloud"; else -> "checkmark.icloud" }, 12f, color)
        Texto(textoGuardadoAlta(s, ahora), Estilo.caption, color)
        if (s.guardado == "error") BotonLink("Reintentar", Estilo.footnoteFuerte, color, alTocar = reintentar)
    }
}

private fun textoGuardadoAlta(s: AltaEstado, ahora: Instant): String {
    if (s.guardado == "guardando") return "Guardando…"
    if (s.guardado == "error") return "No se guardó"
    val g = s.guardadoEn
    if (s.guardado == "nada" || g == null) return "Se guarda solo"
    val seg = max(1L, ((ahora.toEpochMilli() - g.toEpochMilli()) / 1000.0).roundToLong())
    return if (seg < 60) "Guardado hace $seg s" else "Guardado hace ${seg / 60} min"
}

@Composable
private fun PieAlta(s: AltaEstado, seguir: () -> Unit) {
    Row(Modifier.fillMaxWidth().background(C.fondo).padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (s.indicePaso > 0) BotonHundido("Atrás", { s.paso = s.pasos[s.indicePaso - 1]; s.errores = emptyMap() }, Modifier.width(100.dp))
        Row(
            Modifier.weight(1f).heightIn(min = 44.dp).clip(RoundedCornerShape(12.dp)).background(C.accion).alpha(if (s.creando) 0.5f else 1f)
                .clickable(enabled = !s.creando, onClick = seguir).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically,
        ) {
            if (s.creando) CargandoCobro(C.sobreAccion)
            Texto(if (s.ultimo) "Crear contrato" else "Seguir: ${AltaContrato.pasosNombre[s.pasos[min(s.indicePaso + 1, s.pasos.size - 1)]]}",
                Estilo(15f, Peso.semibold), C.sobreAccion, lineas = 1)
            Icono(if (s.ultimo) "checkmark" else "arrow.right", 15f, C.sobreAccion)
        }
    }
}

@Composable
private fun TextoErrorAlta(e: String) {
    Etiqueta(e, "exclamationmark.circle", Estilo.footnote, C.errorTinta)
}

// MARK: Paso 0 · Partes

@Composable
private fun PasoPartesAlta(s: AltaEstado, renovar: String?, operacion: String?, leerArchivo: () -> Unit, leerFoto: () -> Unit) {
    if (s.idBorrador == null && renovar == null && operacion == null && s.paraSeguir.isNotEmpty() && s.d == DatosContratoAlta()) AMedioCargarAlta(s)
    TarjetaLeerAlta(s, leerArchivo, leerFoto)
    Etiquetado("Contrato de") {
        Segmentado(listOf("alquiler" to "Alquiler", "venta" to "Venta"), s.d.tipo, { s.d = s.d.copy(tipo = it) })
    }
    CampoElegirAlta(s, "Propiedad", s.d.propiedad, s.sugeridos["propiedad"], "direccion" in s.dudosos, s.errores["propiedadId"], "propiedad", false)
    CampoElegirAlta(s, if (s.d.alquiler) "Inquilino" else "Comprador", s.d.inquilino, s.sugeridos["inquilino"], "inquilino" in s.dudosos, s.errores["contactoId"], "inquilino", false)
    CampoElegirAlta(s, "Propietario", s.d.propietario, s.sugeridos["propietario"], "propietario" in s.dudosos, s.errores["propietarioId"], "propietario", true)
    if (s.d.alquiler) {
        Etiquetado("Garante (opcional)") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CampoCobro(s.d.garanteNombre, { s.d = s.d.copy(garanteNombre = it) }, "Nombre y apellido", Modifier.fillMaxWidth())
                CampoCobro(s.d.garanteTelefono, { s.d = s.d.copy(garanteTelefono = it) }, "Teléfono", Modifier.fillMaxWidth(), teclado = KeyboardType.Phone)
                CampoCobro(s.d.garanteNotas, { s.d = s.d.copy(garanteNotas = it) }, "Garantía: recibo de sueldo, propietaria, seguro de caución…", Modifier.fillMaxWidth())
                listOf("garanteNombre", "garanteTelefono", "garanteNotas").forEach { k -> s.errores[k]?.let { TextoErrorAlta(it) } }
            }
        }
    }
}

@Composable
private fun TarjetaLeerAlta(s: AltaEstado, leerArchivo: () -> Unit, leerFoto: () -> Unit) {
    val forma = RoundedCornerShape(16.dp)
    val linea2 = C.linea2
    val opciones = listOf(OpcionDeMenu("Un archivo (PDF)", "doc") { leerArchivo() }, OpcionDeMenu("Una foto", "camera") { leerFoto() })
    Box(Modifier.alpha(if (s.leyendo) 0.6f else 1f)) {
        MenuBoton(if (s.leyendo) emptyList() else opciones, "Subir el PDF y que lo lea") {
            Row(
                Modifier.fillMaxWidth().clip(forma).background(if (s.yaLeido) C.okFondo else C.superficie)
                    .drawBehind {
                        drawRoundRect(linea2, cornerRadius = CornerRadius(16.dp.toPx()),
                            style = Stroke(1.dp.toPx(), pathEffect = if (s.yaLeido) null else PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 5.dp.toPx()))))
                    }
                    .padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.width(32.dp), contentAlignment = Alignment.Center) {
                    if (s.leyendo) CargandoCobro() else Icono(if (s.yaLeido) "checkmark.circle.fill" else "sparkles", 22f, if (s.yaLeido) C.okTinta else C.accion)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Texto(if (s.leyendo) "Leyendo el contrato…" else if (s.yaLeido) "Leímos el contrato" else "Subir el PDF y que lo lea", Estilo.subheadFuerte, C.texto)
                    Texto(if (s.leyendo) "Tarda unos segundos." else if (s.yaLeido) "Revisá cada paso. Podés subir otro." else "O una foto. Completamos lo que se lea.",
                        Estilo.footnote, C.texto2)
                }
            }
        }
    }
}

@Composable
private fun CampoElegirAlta(s: AltaEstado, etiqueta: String, v: ElegidoAlta?, sugerido: String?, dudoso: Boolean, error: String?, clave: String, opcional: Boolean) {
    Etiquetado(etiqueta + if (opcional) " (opcional)" else "") {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            val forma = RoundedCornerShape(12.dp)
            Row(
                Modifier.weight(1f).heightIn(min = 50.dp).clip(forma).background(C.superficie).border(BorderStroke(1.dp, if (error == null) C.linea2 else C.error), forma)
                    .clickable { s.eligiendo = clave }.padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                Icono(if (clave == "propiedad") "house" else "person", 17f, C.texto2)
                Column(Modifier.weight(1f).padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Texto(v?.nombre ?: (sugerido?.let { "¿$it?" } ?: "Elegí"), Estilo.body, if (v == null) C.texto3 else C.texto)
                    val det = v?.detalle
                    if (det != null) Texto(det, Estilo.footnote, C.texto2)
                    else if (v == null && sugerido != null) Texto("Lo leímos del contrato: tocá para buscarlo o cargarlo", Estilo.footnote, C.avisoTinta)
                }
                if (dudoso) {
                    Box(Modifier.height(22.dp).clip(RoundedCornerShape(50)).background(C.avisoFondo).padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
                        Texto("Revisá", Estilo(12f, Peso.bold), C.avisoTinta)
                    }
                }
                Chevron()
            }
            if (v != null && opcional) {
                Box(Modifier.size(36.dp, 44.dp).clickable { s.d = s.d.copy(propietario = null) }.semantics { contentDescription = "Sacar ${etiqueta.lowercase()}" },
                    contentAlignment = Alignment.Center) { Icono("xmark.circle.fill", 20f, C.texto3) }
            }
        }
        if (error != null) TextoErrorAlta(error)
    }
}

/** Los borradores a medio cargar («Para seguir»). */
@Composable
private fun AMedioCargarAlta(s: AltaEstado) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TituloBloque("Para seguir")
        Column(Modifier.fillMaxWidth().tarjeta()) {
            s.paraSeguir.forEachIndexed { i, b ->
                if (i > 0) LineaFila()
                Row(
                    Modifier.fillMaxWidth().clickable { s.seguirBorrador(b) }.padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.width(24.dp), contentAlignment = Alignment.Center) { Icono("square.and.pencil", 17f, C.oroTinta) }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Texto(b.datos.datos.propiedad?.nombre ?: "Sin propiedad todavía", Estilo.filaLista, C.texto)
                        Texto("${AltaContrato.pasosNombre[b.datos.paso]} · ${b.actualizadoEn?.let { haceAlta(it) } ?: ""}", Estilo.footnote, C.texto2)
                    }
                    Chevron()
                }
            }
        }
    }
}

// MARK: Paso 1 · Plazo y monto

@Composable
private fun PasoPlazoAlta(s: AltaEstado) {
    Etiquetado("Empieza el") {
        RenglonFecha("", Fecha.fecha(s.d.inicio) ?: Formato.hoy(), { s.d = s.d.copy(inicio = Formato.diaISO(it)) })
        s.errores["inicio"]?.let { TextoErrorAlta(it) }
    }
    if (s.d.alquiler) {
        Etiquetado("Vence el pago") {
            val forma = RoundedCornerShape(12.dp)
            Row(Modifier.fillMaxWidth().heightIn(min = 50.dp).clip(forma).background(C.superficie).border(BorderStroke(1.dp, C.linea2), forma).padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Texto("día", Estilo.body, C.texto2)
                CampoCobro(s.tDia, { v ->
                    s.tDia = v
                    v.filter { it.isDigit() }.toIntOrNull()?.let { n -> if (n in 1..31) s.d = s.d.copy(diaVencimiento = n) }
                }, "10", Modifier.weight(1f), teclado = KeyboardType.Number, marco = false)
            }
            Texto("Día de cada mes (1 a 31)", Estilo.footnote, C.texto2)
        }
        Etiquetado("Dura") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(24, 36).forEach { m ->
                    val on = s.d.meses == m && s.tMeses.isEmpty()
                    Box(Modifier.weight(1f).heightIn(min = 44.dp).clip(RoundedCornerShape(12.dp)).background(if (on) C.accion else C.hundida)
                        .clickable { s.d = s.d.copy(meses = m); s.tMeses = "" }, contentAlignment = Alignment.Center) {
                        Texto("$m meses", Estilo.subheadFuerte, if (on) C.sobreAccion else C.texto)
                    }
                }
                Row(Modifier.weight(1f).heightIn(min = 44.dp).clip(RoundedCornerShape(12.dp)).background(if (s.tMeses.isEmpty()) C.hundida else C.okFondo)
                    .padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                    CampoCobro(s.tMeses, { v ->
                        s.tMeses = v
                        if (v.isNotEmpty()) {
                            val n = v.filter { it.isDigit() }.toIntOrNull() ?: 0
                            s.d = s.d.copy(meses = if (n in 1..240) n else null)
                        }
                    }, "Otra", Modifier.weight(1f).semantics { contentDescription = "Otra duración, en meses" }, teclado = KeyboardType.Number, marco = false,
                        alinear = TextAlign.Center)
                    if (s.tMeses.isNotEmpty()) Texto("meses", Estilo.footnote, C.texto2)
                }
            }
            s.fin?.let { fin -> Texto("Termina el ${AltaContrato.fmtFecha(fin)} · ${AltaContrato.mesesEntre(s.d.inicio, fin)} meses.", Estilo.footnote, C.texto2) }
            s.errores["fin"]?.let { TextoErrorAlta(it) }
        }
    }
    Etiquetado(if (s.d.alquiler) "Alquiler del primer mes" else "Monto de la venta") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(110.dp)) { Segmentado(listOf("ARS" to "ARS", "USD" to "USD"), s.d.moneda, { s.d = s.d.copy(moneda = it) }) }
            CampoCobro(montoTextoAlta(s.d.monto), { v -> s.d = s.d.copy(monto = v.filter { it.isDigit() }.toDoubleOrNull()) }, "0", Modifier.weight(1f),
                teclado = KeyboardType.Number)
        }
        s.errores["monto"]?.let { TextoErrorAlta(it) }
    }
    if (s.d.renueva != null) {
        Texto("Viene del alquiler de hoy del contrato anterior, con el último ajuste. Cambialo si pactaron otro.", Estilo.footnote, C.texto2)
    }
}

// MARK: Paso 2 · Ajuste

@Composable
private fun PasoAjusteAlta(s: AltaEstado) {
    Etiquetado("Índice") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AltaContrato.indices.chunked(2).forEach { fila ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    fila.forEach { i ->
                        val on = s.d.indice == i.first
                        val tinta = if (on) C.sobreAccion else C.texto
                        Column(Modifier.weight(1f).heightIn(min = 54.dp).clip(RoundedCornerShape(12.dp)).background(if (on) C.accion else C.hundida)
                            .clickable { s.elegirIndice(i.first) }.padding(10.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Texto(i.second, Estilo.subheadFuerte, tinta)
                            Texto(i.third, Estilo.footnote, tinta, Modifier.alpha(0.8f))
                        }
                    }
                    if (fila.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
    val indice = s.d.indice
    if (indice != null) {
        Etiquetado("Se ajusta cada") {
            Segmentado(listOf(3 to "3 meses", 4 to "4 meses", 6 to "6 meses", 12 to "12 meses"), s.d.ajusteMeses ?: 6, { s.d = s.d.copy(ajusteMeses = it) })
            s.errores["ajusteMeses"]?.let { TextoErrorAlta(it) }
        }
        if (indice == "fijo") {
            Etiquetado("Aumento en cada ajuste") {
                CampoPorcentajeAlta(s.tPorcentaje, { s.tPorcentaje = it }, "%") { s.d = s.d.copy(ajustePorcentaje = it) }
                s.errores["ajustePorcentaje"]?.let { TextoErrorAlta(it) }
            }
        }
        Etiqueta(
            if (indice == "fijo") "Cada ${s.d.ajusteMeses ?: 0} meses el alquiler sube ${if (s.tPorcentaje.isEmpty()) "0" else s.tPorcentaje} %. Te avisamos antes para que se lo cuentes a las partes."
            else "Cada ${s.d.ajusteMeses ?: 0} meses, el alquiler se multiplica por lo que subió el ${TextosCobro.nombreIndice[indice] ?: ""} en ese período. Corredor hace la cuenta, te la explica y te avisa antes.",
            "function", Estilo.footnote, C.texto2,
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(C.okFondo).padding(12.dp), colorIcono = C.okTinta,
        )
    } else {
        Texto("El alquiler queda igual todo el contrato.", Estilo.footnote, C.texto2)
    }
}

@Composable
private fun CampoPorcentajeAlta(t: String, alCambiar: (String) -> Unit, sufijo: String, poner: (Double?) -> Unit) {
    val forma = RoundedCornerShape(12.dp)
    Row(Modifier.fillMaxWidth().heightIn(min = 50.dp).clip(forma).background(C.superficie).border(BorderStroke(1.dp, C.linea2), forma).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically) {
        CampoCobro(t, { v -> alCambiar(v); poner(AltaContrato.leerPorcentaje(v)) }, "0", Modifier.weight(1f), teclado = KeyboardType.Decimal, marco = false)
        Texto(sufijo, Estilo.body, C.texto2)
    }
}

// MARK: Paso 3 · Condiciones

@Composable
private fun PasoCondicionesAlta(s: AltaEstado) {
    val d = s.d
    val totalContrato = if (d.alquiler && (d.monto ?: 0.0) > 0 && (d.meses ?: 0) > 0) (d.monto ?: 0.0) * (d.meses ?: 0) else (d.monto ?: 0.0)
    val honorariosMonto = if ((d.honorarios ?: 0.0) > 0 && totalContrato > 0) Math.round(totalContrato * (d.honorarios ?: 0.0) / 100).toDouble() else null
    Etiquetado("Honorarios (opcional)") {
        CampoPorcentajeAlta(s.tHonorarios, { s.tHonorarios = it }, "%") { s.d = s.d.copy(honorarios = it) }
        Texto(honorariosMonto?.let { "${Formato.plata(it, d.moneda)} del total del contrato" } ?: "Del total del contrato", Estilo.footnote, C.texto2)
        s.errores["honorarios"]?.let { TextoErrorAlta(it) }
    }
    Etiquetado(if (d.alquiler) "Administración (opcional)" else "Comisión (opcional)") {
        CampoPorcentajeAlta(s.tComision, { s.tComision = it }, "%") { s.d = s.d.copy(comision = it) }
        Texto(if (d.alquiler) "De lo cobrado cada mes; se descuenta al rendir" else "Sobre el monto de la venta", Estilo.footnote, C.texto2)
        s.errores["comision"]?.let { TextoErrorAlta(it) }
    }
    if (d.alquiler) {
        Etiquetado("Punitorios (opcional)") {
            CampoPorcentajeAlta(s.tPunitorio, { s.tPunitorio = it }, "% diario") { s.d = s.d.copy(punitorioDiario = it) }
            Texto("Por día de atraso, sobre el alquiler del mes", Estilo.footnote, C.texto2)
            s.errores["punitorioDiario"]?.let { TextoErrorAlta(it) }
        }
        Etiquetado("Depósito") {
            val forma = RoundedCornerShape(12.dp)
            Row(Modifier.fillMaxWidth().heightIn(min = 50.dp).clip(forma).background(C.superficie).border(BorderStroke(1.dp, C.linea2), forma).padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Texto(d.moneda, Estilo.body, C.texto2)
                CampoCobro(montoTextoAlta(d.deposito), { v -> s.d = s.d.copy(deposito = v.filter { it.isDigit() }.toDoubleOrNull()) }, "0", Modifier.weight(1f),
                    teclado = KeyboardType.Number, marco = false)
            }
            val m = d.monto
            if (m != null && m > 0 && d.deposito != m) {
                BotonLink("Un mes de alquiler: ${Formato.plata(m, d.moneda)}", Estilo.footnoteFuerte, C.accion) { s.d = s.d.copy(deposito = m) }
            }
            s.errores["deposito"]?.let { TextoErrorAlta(it) }
        }
    }
}

// MARK: Paso 4 · Documentos

@Composable
private fun PasoDocumentosAlta(s: AltaEstado, subirArchivo: () -> Unit, subirFoto: () -> Unit, sacar: (DocumentoAlta) -> Unit) {
    Texto("El contrato firmado, el acta de entrega, el DNI de las partes. Lo podés sumar después desde la ficha.", Estilo.subhead, C.texto2)
    Column(Modifier.fillMaxWidth().tarjeta()) {
        s.d.documentos.forEachIndexed { i, doc ->
            if (i > 0) LineaFila()
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(28.dp), contentAlignment = Alignment.Center) { Icono(if (doc.tipo.contains("pdf")) "doc.richtext" else "photo", 17f, C.azulTinta) }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Texto(doc.nombre, Estilo.filaLista, C.texto, lineas = 1)
                    Texto(tamanoArchivoAlta(doc.bytes.toLong()), Estilo.footnote, C.texto2)
                }
                Box(Modifier.size(44.dp).clickable { sacar(doc) }.semantics { contentDescription = "Sacar ${doc.nombre}" }, contentAlignment = Alignment.Center) {
                    Icono("trash", 17f, C.texto3)
                }
            }
        }
        if (s.d.documentos.isNotEmpty()) LineaFila()
        Box(Modifier.alpha(if (s.subiendoDoc) 0.5f else 1f)) {
            MenuBoton(if (s.subiendoDoc) emptyList() else listOf(OpcionDeMenu("Un archivo (PDF)", "doc") { subirArchivo() }, OpcionDeMenu("Una foto", "photo") { subirFoto() }),
                "Tocá para subir un archivo") {
                Etiqueta(if (s.subiendoDoc) "Subiendo…" else "Tocá para subir un archivo", "arrow.up.doc", Estilo.subheadFuerte, C.accion,
                    Modifier.fillMaxWidth().heightIn(min = 50.dp).padding(horizontal = 14.dp, vertical = 14.dp))
            }
        }
    }
    Etiquetado("Notas (opcional)") {
        CampoCobro(s.d.notas, { s.d = s.d.copy(notas = it) }, "Lo que conviene recordar: cláusulas especiales, acuerdos de palabra…", Modifier.fillMaxWidth(),
            lineasMin = 3, lineasMax = 8)
        s.errores["notas"]?.let { TextoErrorAlta(it) }
    }
}

/** Lo estático de AltaContrato (los `static` de iOS). */
object AltaContrato {
    val pasosNombre = listOf("Partes", "Plazo y monto", "Ajuste", "Condiciones", "Documentos")
    val titulos = listOf("¿Quiénes firman?", "Plazo y monto", "¿Cómo se ajusta?", "Condiciones", "Documentos")
    val indices: List<Triple<String?, String, String>> = listOf(Triple("icl", "ICL", "BCRA, diario"), Triple("ipc", "IPC", "INDEC, mensual"), Triple("uva", "UVA", "BCRA, diario"),
        Triple("fijo", "Fijo", "un porcentaje"), Triple(null, "Sin ajuste", "monto fijo"))
    /** En qué paso se revisa cada dato leído (para marcar «No se lee bien» donde corresponde). */
    val pasoDe: Map<String, Int> = mapOf("tipo" to 0, "direccion" to 0, "inquilino" to 0, "propietario" to 0, "garante" to 0, "inicio" to 1, "fin" to 1, "meses" to 1,
        "monto" to 1, "moneda" to 1, "diaVencimiento" to 1, "ajusteIndice" to 2, "ajusteMeses" to 2, "ajustePorcentaje" to 2,
        "garantia" to 3, "deposito" to 3, "honorarios" to 3, "punitorioDiario" to 3, "notas" to 3)

    /** En qué paso está cada campo que rechaza la API: partes 0, ajuste 2, condiciones 3, notas 4 y el
     *  resto (plazo y monto) 1. */
    fun pasoDelCampo(c: String): Int {
        if (c in listOf("propiedadId", "contactoId", "propietarioId", "tipo") || c.startsWith("garante")) return 0
        if (c.startsWith("ajuste")) return 2
        if (c in listOf("comision", "honorarios", "punitorioDiario", "deposito")) return 3
        if (c == "notas") return 4
        return 1
    }

    val nombreDato: Map<String, String> = mapOf("tipo" to "el tipo", "direccion" to "la dirección", "inquilino" to "el inquilino", "propietario" to "el propietario",
        "garante" to "el garante", "garantia" to "la garantía", "inicio" to "el inicio", "fin" to "el fin", "meses" to "la duración",
        "monto" to "el monto", "moneda" to "la moneda", "diaVencimiento" to "el día de vencimiento", "ajusteIndice" to "el índice",
        "ajusteMeses" to "cada cuánto ajusta", "ajustePorcentaje" to "el porcentaje", "deposito" to "el depósito",
        "honorarios" to "los honorarios", "punitorioDiario" to "el punitorio", "notas" to "las notas")

    @Serializable data class ParteLeida(val nombre: String, val telefono: String? = null)

    @Serializable
    data class Leido(
        val tipo: String? = null, val inquilino: ParteLeida? = null, val propietario: ParteLeida? = null, val garante: ParteLeida? = null,
        val garantia: String? = null, val direccion: String? = null, val inicio: String? = null, val fin: String? = null, val meses: Int? = null,
        val monto: Double? = null, val moneda: String? = null, val ajusteIndice: String? = null, val ajusteMeses: Int? = null,
        val ajustePorcentaje: Double? = null, val deposito: Double? = null, val honorarios: Double? = null, val punitorioDiario: Double? = null,
        val diaVencimiento: Int? = null, val notas: String? = null, val dudosos: List<String>? = null,
    )

    /** Lo leído pisa lo cargado sólo donde se leyó algo; las partes y la propiedad se buscan aparte. */
    fun aplicarLectura(d: DatosContratoAlta, l: Leido): DatosContratoAlta {
        var x = d
        val positivo: (Double?) -> Double? = { v -> v?.takeIf { it.isFinite() && it > 0 } }
        if (l.tipo == "alquiler" || l.tipo == "venta") x = x.copy(tipo = l.tipo)
        if (l.inicio != null && Fecha.fecha(l.inicio) != null) x = x.copy(inicio = l.inicio)
        var meses: Double? = positivo(l.meses?.toDouble())
        if (meses == null) {
            val i = Fecha.fecha(l.inicio)
            val f = Fecha.fecha(l.fin)
            if (i != null && f != null) meses = max(1.0, Math.round(ChronoUnit.DAYS.between(i, f) / 30.44).toDouble())
        }
        meses?.let { x = x.copy(meses = it.roundToInt()) }
        positivo(l.monto)?.let { x = x.copy(monto = it) }
        if (l.moneda == "ARS" || l.moneda == "USD") x = x.copy(moneda = l.moneda)
        l.diaVencimiento?.let { if (it in 1..28) x = x.copy(diaVencimiento = it) }
        l.ajusteIndice?.let { if (it in listOf("icl", "ipc", "uva", "fijo")) x = x.copy(indice = it) }
        positivo(l.ajusteMeses?.toDouble())?.let { x = x.copy(ajusteMeses = it.roundToInt()) }
        positivo(l.ajustePorcentaje)?.let { x = x.copy(ajustePorcentaje = it) }
        positivo(l.deposito)?.let { x = x.copy(deposito = it) }
        positivo(l.honorarios)?.let { x = x.copy(honorarios = it) }
        positivo(l.punitorioDiario)?.let { x = x.copy(punitorioDiario = it) }
        l.garante?.nombre?.takeIf { it.isNotBlank() }?.let { g ->
            x = x.copy(garanteNombre = g)
            l.garante.telefono?.takeIf { it.isNotBlank() }?.let { x = x.copy(garanteTelefono = it) }
        }
        val notas = listOfNotNull(l.garantia?.let { "Garantía: $it" }, l.notas?.takeIf { it.isNotBlank() }).joinToString("\n")
        if (notas.isNotEmpty() && !x.notas.contains(notas)) x = x.copy(notas = listOf(x.notas.trim(), notas).filter { it.isNotEmpty() }.joinToString("\n\n"))
        return x
    }

    /** Meses enteros entre dos fechas (del 01/07/2024 al 30/06/2027 → 36): el último día cuenta. */
    fun mesesEntre(desde: String, hasta: String): Int {
        val a = desde.take(10).split("-").mapNotNull { it.toIntOrNull() }
        val h0 = Fecha.fecha(hasta)
        if (a.size != 3 || h0 == null) return 0
        val h = h0.plusDays(1)
        return max(0, (h.year - a[0]) * 12 + (h.monthValue - a[1]) - if (h.dayOfMonth < a[2]) 1 else 0)
    }

    /** El último día del plazo: 36 meses desde el 01/07/2024 → 30/06/2027. */
    fun finDe(inicio: String, meses: Int?): String? {
        if (meses == null || meses <= 0) return null
        val p = inicio.take(10).split("-").mapNotNull { it.toIntOrNull() }
        if (p.size != 3) return null
        val total = p[1] - 1 + meses
        val anio = p[0] + total / 12
        val mes = total % 12 + 1
        return runCatching {
            val primero = LocalDate.of(anio, mes, 1)
            Formato.diaISO(LocalDate.of(anio, mes, min(p[2], primero.lengthOfMonth())).minusDays(1))
        }.getOrNull()
    }

    /** «01/07/2027». */
    fun fmtFecha(f: String): String = "${TextosCobro.fechaCortita(f)}/${f.take(4)}"

    /** «4,15» o «4.15» → 4.15. Vacío → nil. */
    fun leerPorcentaje(s: String): Double? {
        val t = s.replace(",", ".").filter { it.isDigit() || it == '.' }
        return if (t.isEmpty()) null else t.toDoubleOrNull()
    }

    /** 4.15 → «4,15». */
    fun textoPorcentaje(n: Double?): String {
        if (n == null) return ""
        return (if (Math.rint(n) == n) n.toLong().toString() else n.toString()).replace(".", ",")
    }

    /** «Martín  Ríos» y «martin rios» son el mismo nombre. */
    fun mismoNombre(a: String, b: String): Boolean {
        val n: (String) -> String = {
            Normalizer.normalize(it, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").lowercase()
                .split(Regex("\\s+")).filter { p -> p.isNotEmpty() }.joinToString(" ")
        }
        return n(a) == n(b)
    }
}

/** El alta apilada desde una ruta (`/contratos/nuevo?borrador=` o `?renovar=`). */
@Composable
fun AltaContratoPantalla(destino: AAltaContrato) {
    AltaContrato(leer = destino.leer, renovar = destino.renovar, borrador = destino.borrador, apilada = true)
}

// MARK: - Elegir propiedad y partes

@Serializable private data class PropElegir(val id: String, val titulo: String? = null, val barrio: String? = null, val estado: String? = null)

/** Elegir la propiedad del contrato: sin texto, las de la operación del contrato (alquiler o venta). */
@Composable
fun ElegirPropiedadAlta(operacion: String, sugerido: String?, alElegir: (ElegidoAlta) -> Unit) {
    val estado = LocalEstado.current
    val cerrar = LocalCerrarHoja.current
    var q by remember { mutableStateOf(sugerido ?: "") }
    var res by remember { mutableStateOf<List<ElegidoAlta>>(emptyList()) }
    var cargando by remember { mutableStateOf(true) }

    LaunchedEffect(q) {
        delay(200)
        cargando = true
        res = if (q.length >= 2) {
            val r: List<Resultado> = estado.api.opcional("/v1/buscar", listOf("tipo" to "propiedad", "q" to q)) ?: emptyList()
            r.map { ElegidoAlta(it.id, it.titulo, it.subtitulo) }
        } else {
            val l: List<PropElegir> = estado.api.opcional("/v1/propiedades", listOf("limite" to "8", "operacion" to operacion)) ?: emptyList()
            l.map { p ->
                ElegidoAlta(p.id, p.titulo ?: "Propiedad",
                    listOfNotNull(p.barrio, if (p.estado == "disponible") null else p.estado).joinToString(" · ").ifBlank { null })
            }
        }
        cargando = false
    }

    Column(Modifier.fillMaxSize()) {
        BarraHoja("Elegí la propiedad", cancelar = "Cancelar", alCancelar = cerrar)
        BuscadorAlta(q, { q = it }, "Calle, barrio o código")
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)) {
            SeccionCobro {
                if (!cargando && res.isEmpty()) {
                    Texto(if (q.length >= 2) "No encontramos propiedades con «$q»." else "Escribí la calle, el barrio o el código.", Estilo.body, C.texto2,
                        Modifier.padding(16.dp))
                }
                res.forEachIndexed { i, r ->
                    if (i > 0 || (!cargando && res.isEmpty())) LineaFila(16.dp)
                    FilaToque(alTocar = { alElegir(r); cerrar() }) {
                        Column(Modifier.weight(1f)) {
                            Texto(r.nombre, Estilo.filaLista, C.texto)
                            r.detalle?.let { Texto(it, Estilo.footnote, C.texto2) }
                        }
                    }
                }
            }
        }
    }
}

@Serializable private data class CtElegir(val id: String, val nombre: String, val telefono: String? = null, val tipo: String? = null)
@Serializable private data class CuerpoContactoAlta(val nombre: String, val tipo: String)

/** Elegir una parte: sin texto, los más recientes del tipo (inquilino, comprador o propietario), con
 *  tipo y teléfono; «Cargar «…» como contacto nuevo» con ese tipo. */
@Composable
fun ElegirParteAlta(tipo: String, sugerido: String?, alElegir: (ElegidoAlta) -> Unit) {
    val estado = LocalEstado.current
    val cerrar = LocalCerrarHoja.current
    val alcance = rememberCoroutineScope()
    var q by remember { mutableStateOf(sugerido ?: "") }
    var res by remember { mutableStateOf<List<ElegidoAlta>>(emptyList()) }
    var creando by remember { mutableStateOf(false) }

    LaunchedEffect(q) {
        delay(200)
        res = if (q.length >= 2) {
            val r: List<Resultado> = estado.api.opcional("/v1/buscar", listOf("tipo" to "contacto", "q" to q)) ?: emptyList()
            r.map { ElegidoAlta(it.id, it.titulo, it.subtitulo) }
        } else {
            val l: List<CtElegir> = estado.api.opcional("/v1/contactos", listOf("limite" to "8", "tipo" to tipo)) ?: emptyList()
            l.map { c -> ElegidoAlta(c.id, c.nombre, listOfNotNull(c.tipo?.let { ElegirParteAlta.tipos[it] }, c.telefono).joinToString(" · ").ifBlank { null }) }
        }
    }

    suspend fun crear() {
        creando = true
        try {
            val c: CtElegir = estado.api.enviar("POST", "/v1/contactos", CuerpoContactoAlta(q.trim(), tipo))
            estado.avisar("${c.nombre} quedó en Contactos")
            alElegir(ElegidoAlta(c.id, c.nombre, ElegirParteAlta.tipos[tipo]))
            cerrar()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            estado.avisar(e)
        } finally {
            creando = false
        }
    }

    Column(Modifier.fillMaxSize()) {
        BarraHoja("Elegí ${ElegirParteAlta.tipos[tipo]?.lowercase() ?: "el contacto"}", cancelar = "Cancelar", alCancelar = cerrar)
        BuscadorAlta(q, { q = it }, "Nombre o teléfono")
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)) {
            SeccionCobro {
                res.forEachIndexed { i, r ->
                    if (i > 0) LineaFila(16.dp)
                    FilaToque(alTocar = { alElegir(r); cerrar() }) {
                        Column(Modifier.weight(1f)) {
                            Texto(r.nombre, Estilo.filaLista, C.texto)
                            r.detalle?.let { Texto(it, Estilo.footnote, C.texto2) }
                        }
                    }
                }
                if (q.trim().length >= 2 && estado.escribe) {
                    if (res.isNotEmpty()) LineaFila(16.dp)
                    FilaToque(habilitado = !creando, alTocar = { alcance.launch { crear() } }) {
                        Etiqueta("Cargar «$q» como contacto nuevo", "person.badge.plus", Estilo.body, C.accion)
                    }
                }
            }
        }
    }
}

object ElegirParteAlta {
    val tipos: Map<String, String> = mapOf("inquilino" to "Inquilino", "propietario" to "Propietario", "garante" to "Garante", "comprador" to "Comprador", "vendedor" to "Vendedor")
}

// MARK: - Piezas de esta pantalla

/** .searchable(placement: .navigationBarDrawer(displayMode: .always)): la caja de búsqueda fija bajo la barra. */
@Composable
private fun BuscadorAlta(q: String, alCambiar: (String) -> Unit, prompt: String) {
    Row(
        Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(38.dp).clip(RoundedCornerShape(11.dp)).background(C.hundida).padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Icono("magnifyingglass", 17f, C.texto3)
        CampoCobro(q, alCambiar, prompt, Modifier.weight(1f), marco = false)
        if (q.isNotEmpty()) Box(Modifier.clickable { alCambiar("") }.semantics { contentDescription = "Borrar la búsqueda" }) { Icono("xmark.circle.fill", 15f, C.texto3) }
    }
}

/** El monto de un TextField(value:format: .number): «150000» (vacío si no hay). */
private fun montoTextoAlta(n: Double?): String = n?.let { if (Math.rint(it) == it) it.toLong().toString() else it.toString() } ?: ""

/** Formato.hace: «hace 3 h», «hace 12 min», «hace 2 días». PARA EL NÚCLEO: usar Formato.hace si recibe Instant. */
internal fun haceAlta(f: Instant): String {
    val s = max(0L, (Instant.now().toEpochMilli() - f.toEpochMilli()) / 1000)
    if (s < 60) return "recién"
    if (s < 3600) return "hace ${s / 60} min"
    if (s < 86400) return "hace ${s / 3600} h"
    val d = s / 86400
    return if (d == 1L) "hace 1 día" else "hace $d días"
}

/** ByteCountFormatter(.file): «820 KB», «1,2 MB». */
internal fun tamanoArchivoAlta(bytes: Long): String {
    if (bytes < 1000) return "$bytes bytes"
    val kb = bytes / 1000.0
    if (kb < 1000) return "${kb.roundToInt()} KB"
    val mb = kb / 1000.0
    if (mb < 1000) return TextosCobro.numero(Math.round(mb * 10) / 10.0) + " MB"
    return TextosCobro.numero(Math.round(mb / 100) / 10.0) + " GB"
}

/** Los bytes, el nombre y el tipo de un archivo elegido (fileImporter/PhotosPicker). */
internal fun leerUriAlta(ctx: Context, u: Uri): Triple<ByteArray, String, String>? = runCatching {
    val r = ctx.contentResolver
    val datos = r.openInputStream(u)?.use { it.readBytes() } ?: return null
    val nombre = r.query(u, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null } ?: "Archivo"
    val pdf = nombre.lowercase().endsWith(".pdf") || r.getType(u) == "application/pdf"
    Triple(datos, nombre, if (pdf) "application/pdf" else "image/jpeg")
}.getOrNull()

/** UIImage(data:).jpegData(compressionQuality: 0.85). */
internal fun aJpegAlta(datos: ByteArray): ByteArray = runCatching {
    val b = BitmapFactory.decodeByteArray(datos, 0, datos.size) ?: return datos
    ByteArrayOutputStream().also { b.compress(Bitmap.CompressFormat.JPEG, 85, it) }.toByteArray()
}.getOrDefault(datos)

