package ar.com.corredor.app.pantallas.mensajes

import android.Manifest
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import ar.com.corredor.app.app.*
import ar.com.corredor.app.nucleo.*
import ar.com.corredor.app.pantallas.agenda.*
import ar.com.corredor.app.pantallas.cartera.*
import ar.com.corredor.app.pantallas.inicio.*
import ar.com.corredor.app.pantallas.propiedades.*
import ar.com.corredor.app.tema.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.math.abs

// Clon de Pantallas/Mensajes/Chat.swift.

/** Lo que otra pantalla deja escrito para mandar en una charla (la ficha que se manda desde un contacto). */
object BorradorChat {
    val textos: MutableMap<String, String> = mutableMapOf()
}

private sealed class Item {
    abstract val id: String
    data class dia(override val id: String, val t: String) : Item()
    data class msj(val m: MensajeWA) : Item() { override val id: String get() = m.id }
    data class vinculo(val t: String) : Item() { override val id: String get() = "vinc" }
    data class visita(override val id: String, val t: String) : Item()
}

private data class Sugerida(val id: String, val texto: String, val icono: String, val valor: String?, val agendar: Instant? = null)

/** 6B–6C Chat de WhatsApp, como la conversación del panel (mensajes/[id]): la propiedad por la que
 *  pregunta, los mensajes con los días, las visitas agendadas y la marca del vínculo; las respuestas
 *  sugeridas con los datos de la ficha, los turnos libres del agente, las respuestas guardadas con «/»,
 *  adjuntos con epígrafe y, fuera de las 24 h, las plantillas aprobadas con sus datos. Desde acá se
 *  toma, se pasa a otro agente, se guarda el número como contacto y se crea la operación. */
@Composable
fun Chat(id: String) {
    val estado = LocalEstado.current
    val ctx = LocalContext.current
    val volver = rememberVolver()
    val alcance = rememberCoroutineScope()
    var conv by remember { mutableStateOf<Conversacion?>(null) }
    var mensajes by remember { mutableStateOf<List<MensajeWA>>(emptyList()) }
    var enviados by remember { mutableStateOf<List<MensajeWA>>(emptyList()) }
    var ocultos by remember { mutableStateOf<Set<String>>(emptySet()) }
    var texto by remember { mutableStateOf("") }
    var chip by remember { mutableStateOf<String?>(null) }
    var adjunto by remember { mutableStateOf<Archivo?>(null) }
    var subiendo by remember { mutableStateOf(false) }
    var plantillas by remember { mutableStateOf<List<PlantillaWA>>(emptyList()) }
    var respuestas by remember { mutableStateOf<List<RespuestaGuardada>>(emptyList()) }
    var ficha by remember { mutableStateOf<Propiedad?>(null) }
    var interesados by remember { mutableStateOf<Int?>(null) }
    var contacto by remember { mutableStateOf<Contacto?>(null) }
    var visitas by remember { mutableStateOf<List<Cita>>(emptyList()) }
    var turnos by remember { mutableStateOf<List<Instant>>(emptyList()) }
    var equipo by remember { mutableStateOf<List<Miembro>>(emptyList()) }
    var web by remember { mutableStateOf<String?>(null) }
    var abiertaR by remember { mutableStateOf(false) }
    var guardandoR by remember { mutableStateOf(false) }
    var vincular by remember { mutableStateOf(false) }
    var guardarContacto by remember { mutableStateOf(false) }
    var plantilla by remember { mutableStateOf<String?>(null) }
    var parametros by remember { mutableStateOf<List<String>>(emptyList()) }
    var otra by remember { mutableStateOf(false) }
    var mandandoPlantilla by remember { mutableStateOf(false) }
    val grabadora = remember { Grabadora() }
    var errorCarga by remember { mutableStateOf<ErrorApi?>(null) }

    val nombre = conv?.quien ?: ""
    val primero = if (conv?.sinNombre == false) Charla.primerNombre(nombre) else ""
    val agente: String? = conv?.agenteNombre?.let { Charla.primerNombre(it) }

    val todos: List<MensajeWA> = run {
        val ids = mensajes.map { it.id }.toSet()
        (mensajes + enviados.filter { it.id !in ids }).filter { it.id !in ocultos }
    }

    // MARK: Datos

    suspend fun cargarMensajes() {
        val m = estado.api.opcional<List<MensajeWA>>("/v1/whatsapp/conversaciones/$id/mensajes", listOf("limite" to "200")) ?: return
        mensajes = m.sortedBy { it.ocurrioEn }
        val ids = mensajes.map { it.id }.toSet()
        enviados = enviados.filter { it.id !in ids && (if (it.local) it.estado != "enviado" else true) }
    }

    suspend fun cargar() {
        val c: Conversacion = try {
            estado.api.get<Conversacion>("/v1/whatsapp/conversaciones/$id").also { errorCarga = null }
        } catch (e: ErrorApi) {
            if (conv == null) errorCarga = e
            return
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return
        }
        val cambio = conv?.id != c.id
        conv = c
        if (cambio) { texto = BorradorChat.textos.remove(c.id) ?: ""; chip = null; adjunto = null }
        cargarMensajes()
        if ((c.noLeidos ?: 0) > 0) intentar { estado.api.hacer("POST", "/v1/whatsapp/conversaciones/$id/leida") }
        val p = c.propiedadId
        if (p != null) {
            ficha = estado.api.opcional<Propiedad>("/v1/propiedades/$p")
            val l: List<JsonElement>? = estado.api.opcional("/v1/propiedades/$p/interesados", listOf("limite" to "100"))
            interesados = l?.size
        } else { ficha = null; interesados = null }
        val x = c.contactoId
        if (x != null) {
            contacto = estado.api.opcional<Contacto>("/v1/contactos/$x")
            val v: List<Cita> = estado.api.opcional<List<Cita>>("/v1/agenda", listOf("contacto" to x, "rango" to "todas", "limite" to "200")) ?: emptyList()
            visitas = v.filter { it.tipo == "visita" && it.estado != "cancelada" }
        } else { contacto = null; visitas = emptyList() }
        val quien = c.agenteId ?: estado.usuario?.id
        if (quien != null) {
            val hoy = Instant.now()
            val en10 = hoy.plusSeconds(10 * 86400)
            val agenda: List<Cita> = estado.api.opcional<List<Cita>>(
                "/v1/agenda", listOf("agente" to quien, "desde" to diaISOenBA(hoy), "hasta" to diaISOenBA(en10), "limite" to "500"),
            ) ?: emptyList()
            turnos = Charla.turnosLibres(agenda.filter { it.estado != "cancelada" })
        }
        if (equipo.isEmpty()) equipo = estado.api.opcional<List<Miembro>>("/v1/equipo") ?: emptyList()
        if (web == null) web = Documentos.inmobiliaria(estado.api)?.web
        if (c.enVentana != true && plantillas.isEmpty()) plantillas = estado.api.opcional<List<PlantillaWA>>("/v1/whatsapp/plantillas") ?: emptyList()
        if (respuestas.isEmpty()) respuestas = estado.api.opcional<List<RespuestaGuardada>>("/v1/respuestas-guardadas") ?: emptyList()
    }

    fun local(t: String?, a: Archivo?): MensajeWA = MensajeWA(
        id = "tmp-${UUID.randomUUID()}", conversacionId = id, direccion = "saliente",
        tipo = a?.let { if ((it.tipo ?: "").startsWith("image/")) "imagen" else "documento" } ?: "texto",
        texto = t?.nonEmpty, mediaTipo = a?.tipo, archivoId = null, estado = "pendiente", autorId = estado.usuario?.id, ocurrioEn = Instant.now(),
    )

    fun marcar(tmp: String, fallo: String) {
        enviados = enviados.map { if (it.id == tmp) it.copy(estado = "fallido", error = fallo) else it }
    }

    suspend fun mandar() {
        val t = texto.trim()
        val a = adjunto
        if ((t.isEmpty() && a == null) || subiendo) return
        val tmp = local(t, a)
        enviados = enviados + tmp
        texto = ""
        chip = null
        adjunto = null
        try {
            if (a != null) {
                @Serializable data class CuerpoConArchivo(val texto: String, val archivoId: String)
                val m: MensajeWA = estado.api.enviar("POST", "/v1/whatsapp/conversaciones/$id/mensajes", CuerpoConArchivo(texto = t, archivoId = a.id))
                enviados = enviados.filter { it.id != tmp.id } + m
            } else {
                @Serializable data class Cuerpo(val texto: String)
                val subio = estado.cola.enviar("POST", "/v1/whatsapp/conversaciones/$id/mensajes", Cuerpo(texto = t), etiqueta = "Mensaje a $nombre")
                if (subio) enviados = enviados.filter { it.id != tmp.id } else estado.avisar("Sin señal: lo que escribas sale cuando vuelva.")
            }
            cargarMensajes()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            marcar(tmp.id, Estado.texto(e, si = "No salió"))
            if (e is ErrorApi && e.codigo == "fuera_de_ventana") cargar()
        }
    }

    suspend fun reintentar(m: MensajeWA) {
        if (m.local) {
            ocultos = ocultos + m.id
            texto = m.texto ?: ""
            mandar()
            return
        }
        try {
            estado.api.hacer("POST", "/v1/whatsapp/mensajes/${m.id}/reintentar")
            cargarMensajes()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            estado.avisar(e)
        }
    }

    suspend fun borrar(m: MensajeWA) {
        ocultos = ocultos + m.id
        if (m.local) return
        try {
            estado.api.hacer("DELETE", "/v1/whatsapp/mensajes/${m.id}")
            cargarMensajes()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ocultos = ocultos - m.id
            estado.avisar("No se borró", ok = false)
        }
    }

    suspend fun subir(d: ByteArray, nombreArchivo: String, tipo: String) {
        subiendo = true
        try {
            adjunto = estado.api.subir(d, nombre = nombreArchivo, tipo = tipo, uso = "adjunto")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            estado.avisar(e)
        } finally {
            subiendo = false
        }
    }

    suspend fun subirArchivo(u: Uri) {
        try {
            val d = withContext(Dispatchers.IO) { ctx.contentResolver.openInputStream(u)?.use { it.readBytes() } } ?: throw IllegalStateException()
            val nombreArchivo = ctx.contentResolver.query(u, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            } ?: "archivo"
            val tipo = ctx.contentResolver.getType(u) ?: "application/octet-stream"
            subir(d, nombreArchivo, tipo)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            estado.avisar("No se pudo subir", ok = false)
        }
    }

    suspend fun mandarNotaDeVoz(u: File) {
        try {
            val d = withContext(Dispatchers.IO) { u.readBytes() }
            val a = estado.api.subir(d, nombre = "nota.m4a", tipo = "audio/mp4", uso = "audio")
            @Serializable data class Cuerpo(val texto: String = "", val archivoId: String)
            estado.api.hacer("POST", "/v1/whatsapp/conversaciones/$id/mensajes", Cuerpo(archivoId = a.id))
            cargarMensajes()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            estado.avisar(e)
        }
    }

    val aprobadas: List<PlantillaWA> = plantillas.filter { it.nombre != "recordatorio_visita" }
    val elegida: PlantillaWA? = aprobadas.firstOrNull { it.nombre == plantilla }
    val completa = elegida != null && parametros.all { it.trim().isNotEmpty() }

    suspend fun mandarPlantilla() {
        val p = elegida ?: return
        if (!completa) return
        @Serializable data class Datos(val nombre: String, val idioma: String, val parametros: List<String>)
        @Serializable data class Cuerpo(val plantilla: Datos)
        mandandoPlantilla = true
        try {
            estado.api.hacer(
                "POST", "/v1/whatsapp/conversaciones/$id/mensajes",
                Cuerpo(plantilla = Datos(nombre = p.nombre, idioma = p.idioma ?: "es_AR", parametros = parametros.map { it.trim() })),
            )
            plantilla = null
            otra = false
            estado.avisar("Plantilla enviada a ${if (primero.isEmpty()) nombre else primero} · esperando respuesta")
            cargar()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            estado.avisarReintentar(Estado.texto(e, si = "No salió")) { alcance.launch { mandarPlantilla() } }
        } finally {
            mandandoPlantilla = false
        }
    }

    /** Un cambio en la conversación (dueño, propiedad, contacto, operación), con su Deshacer. */
    suspend fun cambiar(cuerpo: JsonObject, aviso: String, deshacer: JsonObject?) {
        try {
            estado.api.hacer("PATCH", "/v1/whatsapp/conversaciones/$id", cuerpo)
            if (deshacer != null) {
                estado.avisar(aviso) {
                    alcance.launch {
                        try {
                            estado.api.hacer("PATCH", "/v1/whatsapp/conversaciones/$id", deshacer)
                            estado.version += 1
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            estado.avisar(e)
                        }
                    }
                }
            } else {
                estado.avisar(aviso)
            }
            estado.version += 1
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            estado.avisar(Estado.texto(e, si = "No se guardó"), ok = false)
        }
    }

    fun json(k: String, v: String?): JsonObject = JsonObject(mapOf(k to (v?.let { JsonPrimitive(it) } ?: JsonNull)))

    suspend fun pasar(quien: String?, n: String) {
        val t = quien?.let { if (it == estado.usuario?.id) "La conversación es tuya" else "Se la pasaste a ${Charla.primerNombre(n)}" } ?: "Quedó sin dueño"
        cambiar(json("agenteId", quien), t, deshacer = json("agenteId", conv?.agenteId))
    }

    suspend fun archivar() {
        try {
            estado.api.hacer("PATCH", "/v1/whatsapp/conversaciones/$id", JsonObject(mapOf("archivada" to JsonPrimitive(true))))
            estado.version += 1
            estado.avisar("Conversación archivada") {
                alcance.launch {
                    try {
                        estado.api.hacer("PATCH", "/v1/whatsapp/conversaciones/$id", JsonObject(mapOf("archivada" to JsonPrimitive(false))))
                        estado.version += 1
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        estado.avisar(e)
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            estado.avisar(e)
        }
    }

    /** Guarda el número como contacto (con el teléfono, origen whatsapp y el agente) y lo vincula. */
    suspend fun crearContacto(n: String, tipo: String): Boolean {
        val c = conv ?: return false
        @Serializable data class Cuerpo(val nombre: String, val tipo: String, val telefono: String, val origen: String = "whatsapp", val agenteId: String?)
        @Serializable data class Nuevo(val id: String)
        return try {
            val x: Nuevo = estado.api.enviar("POST", "/v1/contactos", Cuerpo(nombre = n, tipo = tipo, telefono = "+" + (c.waId ?: ""), agenteId = c.agenteId))
            estado.api.hacer("PATCH", "/v1/whatsapp/conversaciones/$id", json("contactoId", x.id))
            estado.sugerir("$n quedó en Contactos", "Ver", AFichaContacto(id = x.id))
            estado.version += 1
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            estado.avisar(Estado.texto(e, si = "No se guardó"), ok = false)
            false
        }
    }

    /** La operación de esta charla: contacto · propiedad, en Contactado, con el precio de la ficha. */
    suspend fun crearOperacion() {
        val c = conv ?: return
        @Serializable data class Cuerpo(
            val titulo: String, val etapa: String = "contactado", val valor: Double, val moneda: String,
            val contactoId: String?, val propiedadId: String?, val agenteId: String?,
        )
        @Serializable data class Nueva(val id: String)
        val titulo = listOfNotNull(c.contactoNombre ?: c.nombre?.nonEmpty, c.propiedadTitulo).joinToString(" · ").nonEmpty ?: "Consulta por WhatsApp"
        try {
            val o: Nueva = estado.api.enviar(
                "POST", "/v1/operaciones",
                Cuerpo(titulo = titulo, valor = ficha?.precio ?: 0.0, moneda = ficha?.moneda ?: "USD", contactoId = c.contactoId, propiedadId = c.propiedadId, agenteId = c.agenteId),
            )
            estado.api.hacer("PATCH", "/v1/whatsapp/conversaciones/$id", json("operacionId", o.id))
            estado.sugerir("Operación creada en Contactado", "Ver", AFichaOperacion(id = o.id))
            estado.version += 1
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            estado.avisar(Estado.texto(e, si = "No se creó"), ok = false)
        }
    }

    /** Agenda la visita que propuso el cliente y deja escrita la confirmación. */
    suspend fun agendar(f: Instant) {
        val c = conv ?: return
        @Serializable data class Cuerpo(
            val tipo: String = "visita", val estado: String = "pendiente", val titulo: String, val empiezaEn: String, val duracionMin: Int = 60,
            val propiedadId: String?, val contactoId: String?, val operacionId: String?, val agenteId: String?, val recordar: Boolean,
        )
        try {
            estado.api.hacer(
                "POST", "/v1/agenda",
                Cuerpo(
                    titulo = c.propiedadTitulo?.let { "Visita · $it" } ?: "Visita", empiezaEn = f.toString(),
                    propiedadId = c.propiedadId, contactoId = c.contactoId, operacionId = c.operacionId, agenteId = c.agenteId, recordar = c.contactoId != null,
                ),
            )
            val d = Charla.diaHora(f)
            estado.sugerir("Visita agendada: $d", "Ver", AAgendaDia(dia = diaISOenBA(f)))
            val p = d.split(" · ")
            texto = "Listo${if (primero.isEmpty()) "" else " $primero"}, te espero el ${p[0]} a las ${if (p.size > 1) p[1] else ""}${c.propiedadTitulo?.let { " en $it" } ?: ""}. Te mando la ubicación."
            estado.version += 1
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            estado.avisar(Estado.texto(e, si = "No se agendó"), ok = false)
        }
    }

    suspend fun abrir(archivo: String, nombreArchivo: String) {
        try {
            val f = ArchivosSesion.archivo(archivo, nombre = nombreArchivo, api = estado.api, ctx = ctx)
            verArchivo(ctx, f)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            estado.avisar(e)
        }
    }

    // MARK: Plataforma: fotos, cámara, archivos y el micrófono

    val elegirFoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { u ->
        if (u == null) return@rememberLauncherForActivityResult
        alcance.launch {
            try {
                val tipo = ctx.contentResolver.getType(u) ?: ""
                val d = withContext(Dispatchers.IO) { ctx.contentResolver.openInputStream(u)?.use { it.readBytes() } } ?: return@launch
                if (tipo.startsWith("video/")) {
                    subir(d, "video.mp4", "video/mp4")
                } else {
                    val jpg = withContext(Dispatchers.Default) {
                        BitmapFactory.decodeByteArray(d, 0, d.size)?.let { b -> ByteArrayOutputStream().also { b.compress(Bitmap.CompressFormat.JPEG, 85, it) }.toByteArray() }
                    } ?: d
                    subir(jpg, "foto.jpg", "image/jpeg")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                estado.avisar(e)
            }
        }
    }
    val camara = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { b ->
        if (b == null) return@rememberLauncherForActivityResult
        alcance.launch {
            val jpg = withContext(Dispatchers.Default) { ByteArrayOutputStream().also { b.compress(Bitmap.CompressFormat.JPEG, 85, it) }.toByteArray() }
            subir(jpg, "foto.jpg", "image/jpeg")
        }
    }
    val archivos = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        if (u != null) alcance.launch { subirArchivo(u) }
    }
    val pedirCamara = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (ok) camara.launch(null) }
    val pedirMicrofono = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    LaunchedEffect(estado.version) { cargar() }
    LaunchedEffect(Unit) {
        // Respaldo: en vivo los mensajes llegan por el stream; si está caído, se consulta cada 8 s.
        while (isActive) {
            delay(8000)
            if (!estado.enVivo) cargarMensajes()
        }
    }

    val guardable = !texto.startsWith("/") && texto.trim().length > 1
    val filtradas: List<RespuestaGuardada> = Charla.filtrar(respuestas, if (texto.startsWith("/")) texto.drop(1) else "").take(6)

    LaunchedEffect(texto) {
        val t = texto
        if (t.startsWith("/") && !abiertaR) abiertaR = true
        else if (!t.startsWith("/") && abiertaR && !guardable && t.isNotEmpty()) abiertaR = false
    }

    fun abrirRespuestas() {
        if (abiertaR) { abiertaR = false; if (texto == "/") texto = ""; return }
        if (texto.trim().isEmpty()) texto = "/"
        abiertaR = true
    }

    fun usarRespuesta(r: RespuestaGuardada) {
        abiertaR = false
        chip = "guardada"
        texto = Charla.completarRespuesta(r.texto, primero)
        alcance.launch { intentar { estado.api.hacer("POST", "/v1/respuestas-guardadas/${r.id}/uso") } }
    }

    fun elegirPlantilla(p: PlantillaWA) {
        plantilla = p.nombre
        val yo = Charla.primerNombre(estado.usuario?.nombre)
        parametros = (0 until (p.parametros ?: 0)).map { i -> if (i == 0) primero else if (i == 1) yo else "" }
    }

    /** Tipo de contacto, origen, «sin dueño» y la ventana de 24 h. */
    val subtitulo: String = conv?.let { c ->
        val s = Charla.subtitulo(tipo = contacto?.tipo, origen = contacto?.origen, primero = primero, sinDueno = c.agenteId == null)
        val v = c.ventanaRestante()?.let { "ventana abierta $it" } ?: (if (c.ultimoEntranteEn != null) "ventana cerrada" else null)
        val quien = if (c.agenteId == null) null else agente?.let { "de $it" }
        listOfNotNull(s.nonEmpty ?: quien, v).joinToString(" · ")
    } ?: ""

    /** Lo que se propone contestar: agendar el día que propuso el cliente (si todavía no hay una visita
     *  cerca), «Sigue disponible + precio», «Mandar la ficha» y «Proponer visita». */
    val sugerencias: List<Sugerida> = run {
        val c = conv
        if (c == null || c.enVentana != true) return@run emptyList()
        val xs = mutableListOf<Sugerida>()
        val ultimo = todos.lastOrNull { !it.saliente }
        val t = ultimo?.texto
        val f = t?.let { Charla.fechaPropuesta(it) }
        if (f != null && c.propiedadId != null && visitas.none { abs(Duration.between(f, it.empiezaEn).seconds) < 3600 }) {
            return@run listOf(Sugerida("agendar", "Agendar ${Charla.diaHora(f)}", "calendar.badge.checkmark", null, agendar = f))
        }
        if (c.esperando != true) return@run xs
        val fi = ficha
        if (fi != null) {
            Charla.sugerenciaDisponible(primero, fi)?.let { xs.add(Sugerida("disponible", "Sigue disponible + precio", "sparkles", it)) }
            Charla.textoFicha(primero, fi, web = web)?.let { xs.add(Sugerida("ficha", "Mandar la ficha", "house", it)) }
        }
        Charla.textoPropuesta(primero, turnos)?.let { xs.add(Sugerida("visita", "Proponer visita", "calendar", it)) }
        xs
    }

    fun usar(s: Sugerida) {
        val f = s.agendar
        if (f != null) { alcance.launch { agendar(f) }; return }
        chip = s.id
        texto = s.valor ?: ""
    }

    // MARK: La charla

    /** Días, burbujas, el vínculo con la propiedad y las visitas agendadas, en orden. */
    val linea: List<Item> = run {
        val out = mutableListOf<Item>()
        val pendientes = visitas.sortedBy { it.creadaEn ?: it.empiezaEn }.toMutableList()
        var ultimoDia = ""
        var vinculo = false
        fun ponerDia(f: Instant) {
            val t = if (esHoyEnBA(f)) "Hoy" else if (esAyerEnBA(f)) "Ayer" else Formato.fechaLarga(f)
            if (t != ultimoDia) { ultimoDia = t; out.add(Item.dia("d${f.epochSecond}", t)) }
        }
        fun ponerVisitas(hasta: Instant?) {
            while (true) {
                val v = pendientes.firstOrNull() ?: break
                if (hasta != null && (v.creadaEn ?: v.empiezaEn) > hasta) break
                pendientes.removeAt(0)
                ponerDia(v.creadaEn ?: v.empiezaEn)
                val t = "Visita agendada: ${Charla.diaHora(v.empiezaEn).replace(" · ", ", ")}" +
                    (v.propiedadTitulo?.let { " · $it" } ?: "") + (v.agenteNombre?.let { " · ${Charla.primerNombre(it)}" } ?: "")
                out.add(Item.visita("v" + v.id, t))
            }
        }
        for (m in todos) {
            ponerVisitas(hasta = m.ocurrioEn)
            ponerDia(m.ocurrioEn)
            out.add(Item.msj(m))
            val p = conv?.propiedadTitulo
            if (!vinculo && p != null && !m.saliente) {
                vinculo = true
                out.add(Item.vinculo("Vinculada a $p${if (conv?.propiedadPorCodigo == true) " por el código del aviso" else ""}"))
            }
        }
        ponerVisitas(hasta = null)
        out
    }

    // MARK: Piezas

    @Composable
    fun Tilde(e: String?) {
        when (e) {
            "pendiente" -> Icono("clock", 11f, C.texto3, descripcion = "pendiente")
            "enviado" -> Icono("checkmark", 11f, C.texto3, descripcion = "enviado")
            "entregado", "leido" -> Row(
                Modifier.semantics(mergeDescendants = true) { contentDescription = if (e == "leido") "leído" else "entregado" },
                horizontalArrangement = Arrangement.spacedBy((-5).dp),
            ) {
                val c = if (e == "leido") C.azulTinta else C.texto3
                Icono("checkmark", 11f, c)
                Icono("checkmark", 11f, c)
            }
            else -> {}
        }
    }

    @Composable
    fun Pie(m: MensajeWA) {
        val color = if (m.estado == "fallido") C.error else C.texto3
        val estilo = Estilo(11f, Peso.regular)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
            if (m.tipo == "plantilla") Texto("plantilla ·", estilo, color)
            if (m.estado == "fallido") {
                Texto("no salió", estilo, color)
                Icono("exclamationmark.circle.fill", 11f, color)
            } else {
                Texto(Formato.horaDe(m.ocurrioEn), estilo, color)
                if (m.saliente) Tilde(m.estado)
            }
        }
    }

    @Composable
    fun Contenido(m: MensajeWA) {
        val tipo = m.tipo ?: "texto"
        val a = m.archivoId
        if (a != null && (tipo == "imagen" || m.mediaTipo?.startsWith("image/") == true)) {
            Tocable(alTocar = { alcance.launch { abrir(a, "foto-$a.jpg") } }, descripcion = m.texto ?: "Foto") {
                ImagenArchivo(id = a, modifier = Modifier.size(220.dp).clip(RoundedCornerShape(14.dp)))
            }
        } else if (a != null && tipo == "audio") {
            AudioArchivo(id = a)
        } else if (a != null) {
            Tocable(alTocar = {
                alcance.launch { abrir(a, if (tipo == "video") "video-$a.mp4" else "documento-$a${if (m.mediaTipo == "application/pdf") ".pdf" else ""}") }
            }) {
                ConIcono(if (tipo == "video") "Video" else "Documento", if (tipo == "video") "film" else "doc.text", Estilo.subheadFuerte, C.accion)
            }
        } else if (tipo != "texto" && tipo != "plantilla" && m.texto?.nonEmpty == null) {
            androidx.compose.foundation.text.BasicText(
                if (tipo == "ubicacion") "Ubicación" else if (tipo == "sticker") "Sticker" else "Mensaje que WhatsApp no deja ver acá",
                style = Estilo.body16.ts().copy(color = C.texto3, fontStyle = FontStyle.Italic),
            )
        }
        val t = m.texto?.nonEmpty
        if (t != null) {
            SelectionContainer { Texto(t, Estilo.body16, C.texto) }
        } else {
            m.plantilla?.nombre?.let { Texto("Plantilla: $it", Estilo.body16, C.texto) }
        }
    }

    @Composable
    fun Burbuja(m: MensajeWA) {
        val sal = m.saliente
        val oscuro = LocalOscuro.current
        val forma = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomEnd = if (sal) 6.dp else 18.dp, bottomStart = if (sal) 18.dp else 6.dp)
        Row(Modifier.fillMaxWidth()) {
            if (sal) Spacer(Modifier.weight(1f).widthIn(min = 60.dp))
            Column(horizontalAlignment = if (sal) Alignment.End else Alignment.Start, verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f, fill = false)) {
                Column(
                    Modifier.sombraE1().clip(forma).background(if (sal) (if (oscuro) hex(0x1F3B33) else hex(0xDCEBE3)) else C.superficie)
                        .border(1.dp, if (m.estado == "fallido") C.error else Color.Transparent, forma)
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Contenido(m)
                    Pie(m)
                }
                if (m.estado == "fallido" && estado.escribe) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Texto("Reintentar", Estilo.footnoteFuerte, C.error, Modifier.clickable { alcance.launch { reintentar(m) } })
                        Texto("·", Estilo.footnoteFuerte, C.texto3)
                        Texto("Borrar", Estilo.footnoteFuerte, C.error, Modifier.clickable { alcance.launch { borrar(m) } })
                    }
                    m.error?.nonEmpty?.let { Texto(it, Estilo.footnote, C.texto3, alinear = TextAlign.End) }
                }
            }
            if (!sal) Spacer(Modifier.weight(1f).widthIn(min = 60.dp))
        }
    }

    @Composable
    fun FilaItem(it: Item) {
        when (it) {
            is Item.dia -> Box(Modifier.fillMaxWidth().padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.height(24.dp).clip(CircleShape).background(C.hundida).padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
                    Texto(it.t, Estilo(12f, Peso.semibold), C.texto2)
                }
            }
            is Item.vinculo -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { ConIcono(it.t, "link", Estilo(12f, Peso.regular), C.texto3) }
            is Item.visita -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Row(
                    Modifier.clip(RoundedCornerShape(12.dp)).background(C.avisoFondo).padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icono("calendar.badge.checkmark", 15f, C.oro)
                    Texto(it.t, Estilo.texto14, C.texto)
                }
            }
            is Item.msj -> Burbuja(it.m)
        }
    }

    @Composable
    fun TarjetaPropiedad(c: Conversacion) {
        val f = ficha
        if (f != null || c.propiedadTitulo != null) {
            Row(Modifier.fillMaxWidth().tarjeta(14.dp).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Tocable(alTocar = { c.propiedadId?.let { estado.abrir(AFichaPropiedad(id = it)) } }, modifier = Modifier.weight(1f)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(44.dp).clip(RoundedCornerShape(9.dp))) { Foto(url = f?.miniatura, modifier = Modifier.matchParentSize()) }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Texto(c.propiedadTitulo ?: f?.nombre ?: "", Estilo.label, C.texto, lineas = 1)
                            if (f != null) {
                                Texto(
                                    listOfNotNull(Charla.resumenFicha(f).split(" · ").firstOrNull()?.nonEmpty, f.barrio).joinToString(" · "),
                                    Estilo(12f, Peso.regular), C.texto2, lineas = 1,
                                )
                                Texto(
                                    listOfNotNull(
                                        Propiedad.estados.firstOrNull { it.first == f.estado }?.second?.lowercase(),
                                        interesados?.let { if (it > 0) Formato.cantidad(it, "interesado", "interesados") else null },
                                    ).joinToString(" · "),
                                    Estilo(12f, Peso.regular), C.texto3, lineas = 1,
                                )
                            }
                        }
                    }
                }
                if (estado.escribe) {
                    Box(Modifier.height(32.dp).clip(CircleShape).background(C.hundida).clickable { vincular = true }.padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
                        Texto("Cambiar", Estilo(13f, Peso.semibold), C.accion)
                    }
                }
            }
        } else if (estado.escribe) {
            val linea2 = C.linea2
            Tocable(alTocar = { vincular = true }) {
                Box(
                    Modifier.fillMaxWidth().heightIn(min = 44.dp).drawBehind {
                        drawRoundRect(linea2, cornerRadius = CornerRadius(14.dp.toPx()), style = Stroke(1.2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))))
                    },
                    contentAlignment = Alignment.Center,
                ) { ConIcono("Vincular a una propiedad", "link", Estilo.label, C.accion) }
            }
        }
    }

    @Composable
    fun ChipBoton(t: String, i: String, activo: Boolean, fuerte: Boolean, hacer: () -> Unit) {
        Tocable(alTocar = hacer) {
            Row(
                Modifier.height(32.dp).clip(CircleShape).background(if (fuerte) C.accion else (if (activo) C.crema else C.hundida)).padding(horizontal = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                val color = if (fuerte) C.sobreAccion else (if (activo) C.accion else C.texto)
                Icono(i, 13f, color)
                Texto(t, Estilo(13f, Peso.semibold), color, lineas = 1)
            }
        }
    }

    @Composable
    fun SugerenciasYTurnos() {
        Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (turnos.isNotEmpty() && estado.escribe) {
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Texto(agente?.let { "$it tiene libre" } ?: "Vos tenés libre", Estilo(12f, Peso.bold), C.texto2)
                    turnos.forEach { t ->
                        Box(
                            Modifier.height(30.dp).clip(CircleShape).background(C.hundida).clickable {
                                chip = "visita"
                                val p = Charla.diaHora(t).split(" · ")
                                texto = "${if (primero.isEmpty()) "¿" else "$primero, ¿"}te queda bien el ${p[0]} a las ${if (p.size > 1) p[1] else ""} para verlo?"
                            }.padding(horizontal = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) { Texto(Charla.diaHora(t), Estilo(13f, Peso.semibold), C.accion) }
                    }
                }
            }
            if (estado.escribe) {
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    sugerencias.forEach { s -> key(s.id) { ChipBoton(s.texto, s.icono, activo = chip == s.id, fuerte = s.agendar != null) { usar(s) } } }
                    ChipBoton("Respuestas guardadas /", "bolt", activo = abiertaR || chip == "guardada", fuerte = false) { abrirRespuestas() }
                }
            }
        }
    }

    @Composable
    fun PanelRespuestas() {
        Column(
            Modifier.padding(horizontal = 12.dp).padding(top = 6.dp).fillMaxWidth().heightIn(max = 280.dp).tarjeta(16.dp).verticalScroll(rememberScrollState()),
        ) {
            filtradas.forEach { r ->
                key(r.id) {
                    Tocable(alTocar = { usarRespuesta(r) }) {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 9.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Texto(r.titulo, Estilo.filaLista, C.texto)
                            Texto(Charla.completarRespuesta(r.texto, primero), Estilo.footnote, C.texto2, lineas = 2)
                        }
                    }
                }
            }
            if (filtradas.isEmpty()) {
                Texto(
                    Formato.sinViudas(if (respuestas.isEmpty()) "Todavía no hay respuestas guardadas. Escribí un mensaje y guardalo para usarlo con una «/»." else "Ninguna coincide."),
                    Estilo.footnote, C.texto2, Modifier.padding(14.dp),
                )
            }
            if (guardable) {
                Tocable(alTocar = { abiertaR = false; guardandoR = true }, modifier = Modifier.lineaArriba()) {
                    ConIcono("Guardar este mensaje como respuesta", "plus", Estilo.subheadFuerte, C.accion, Modifier.fillMaxWidth().padding(14.dp))
                }
            }
        }
    }

    @Composable
    fun Entrada() {
        Column(Modifier.fillMaxWidth().background(C.elevado.copy(alpha = 0.94f)).navigationBarsPadding().imePadding().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val a = adjunto
            if (a != null) {
                Row(
                    Modifier.padding(horizontal = 12.dp).fillMaxWidth().height(36.dp).clip(RoundedCornerShape(10.dp)).background(C.hundida).padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icono(if ((a.tipo ?: "").startsWith("image/")) "photo" else "doc.text", 15f, C.accion)
                    Texto(a.nombre ?: "Adjunto", Estilo.footnote, C.texto, Modifier.weight(1f), lineas = 1)
                    Tocable(alTocar = { adjunto = null }, descripcion = "Sacar el adjunto") { Icono("xmark.circle.fill", 17f, C.texto3) }
                }
            }
            if (estado.escribe) {
                Row(Modifier.padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
                    if (subiendo) {
                        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) { Icono("hourglass", 22f, C.accion) }
                    } else {
                        MenuBoton(
                            opciones = listOf(
                                OpcionDeMenu("Fotos y videos", "photo.on.rectangle") { elegirFoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) },
                                OpcionDeMenu("Sacar una foto", "camera") { pedirCamara.launch(Manifest.permission.CAMERA) },
                                OpcionDeMenu("Un archivo (PDF, video, audio)", "doc") {
                                    archivos.launch(arrayOf("image/jpeg", "image/png", "application/pdf", "video/mp4", "audio/*"))
                                },
                            ),
                            descripcion = "Adjuntar un archivo",
                        ) {
                            Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) { Icono(if (subiendo) "hourglass" else "paperclip", 22f, C.accion) }
                        }
                    }
                    val estilo = Estilo.body16
                    BasicTextField(
                        value = texto, onValueChange = { texto = it }, minLines = 1, maxLines = 6,
                        textStyle = estilo.ts().copy(color = C.texto), cursorBrush = SolidColor(C.accion),
                        modifier = Modifier.weight(1f).semantics { contentDescription = "Mensaje para $nombre" },
                        decorationBox = { campo ->
                            Box(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(19.dp)).background(C.superficie).border(0.5.dp, C.linea2, RoundedCornerShape(19.dp))
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                            ) {
                                if (texto.isEmpty()) Texto("Escribí un mensaje", estilo, C.texto3)
                                campo()
                            }
                        },
                    )
                    if (texto.isEmpty() && adjunto == null) {
                        Box(
                            Modifier.size(40.dp).semantics { contentDescription = "Nota de voz: mantené apretado para grabar" }
                                .pointerInput(Unit) {
                                    detectTapGestures(onPress = {
                                        if (!grabadora.grabando && !grabadora.empezar(ctx)) {
                                            pedirMicrofono.launch(Manifest.permission.RECORD_AUDIO)
                                            return@detectTapGestures
                                        }
                                        tryAwaitRelease()
                                        grabadora.terminar()?.let { u -> alcance.launch { mandarNotaDeVoz(u) } }
                                    })
                                },
                            contentAlignment = Alignment.Center,
                        ) { Icono(if (grabadora.grabando) "waveform" else "mic", 22f, if (grabadora.grabando) C.error else C.accion) }
                    } else {
                        val habilitado = !(subiendo || (texto.trim().isEmpty() && adjunto == null))
                        Tocable(alTocar = { alcance.launch { mandar() } }, habilitado = habilitado, descripcion = "Enviar") {
                            Box(Modifier.size(36.dp).alpha(if (habilitado) 1f else 0.5f).clip(CircleShape).background(C.accion), contentAlignment = Alignment.Center) {
                                Icono("arrow.up", 17f, C.sobreAccion, peso = Peso.bold)
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun TarjetaPlantilla(p: PlantillaWA) {
        val on = plantilla == p.nombre
        Tocable(alTocar = { elegirPlantilla(p) }, modifier = Modifier.semantics { selected = on }) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(C.superficie)
                    .border(1.5.dp, if (on) C.accion else Color.Transparent, RoundedCornerShape(12.dp)).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Texto(Charla.nombrePlantilla(p.nombre), Estilo.subheadFuerte, C.texto, Modifier.weight(1f))
                    Icono(if (on) "largecircle.fill.circle" else "circle", 17f, if (on) C.accion else C.texto3)
                }
                p.cuerpo?.let { cu -> Texto(if (on) Charla.completar(cu, parametros) else cu, Estilo.footnote, C.texto2) }
            }
        }
    }

    @Composable
    fun FueraDeVentana(c: Conversacion) {
        Column(
            Modifier.padding(horizontal = 12.dp).padding(bottom = 16.dp).navigationBarsPadding().imePadding().fillMaxWidth().heightIn(max = 380.dp)
                .clip(RoundedCornerShape(18.dp)).background(C.avisoFondo).verticalScroll(rememberScrollState()).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                Icono("clock", 18f, C.avisoTinta)
                val u = c.ultimoEntranteEn
                Texto(
                    Formato.sinViudas(
                        if (u != null) "${if (primero.isEmpty()) nombre else primero} escribió hace ${maxOf(0, Duration.between(u, Instant.now()).seconds.toInt() / 3600)} h. WhatsApp sólo deja retomar con una plantilla aprobada; cuando conteste, podés escribir libre."
                        else "Todavía no escribió. WhatsApp sólo deja empezar con una plantilla aprobada; cuando conteste, podés escribir libre.",
                    ),
                    Estilo.texto14, C.texto, Modifier.weight(1f),
                )
            }
            if (c.esperando != true && !otra && todos.lastOrNull()?.saliente == true) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icono("hourglass", 15f, C.avisoTinta)
                    Texto("Plantilla enviada · esperando respuesta.", Estilo.subheadFuerte, C.texto, Modifier.weight(1f))
                    if (aprobadas.isNotEmpty() && estado.escribe) Texto("Mandar otra", Estilo.subheadFuerte, C.accion, Modifier.clickable { otra = true })
                }
            } else if (aprobadas.isNotEmpty() && estado.escribe) {
                aprobadas.forEach { p -> key(p.id) { TarjetaPlantilla(p) } }
                val e = elegida
                if (e != null && (e.parametros ?: 0) > 0) {
                    parametros.indices.forEach { i ->
                        key(i) {
                            Etiquetado(if (i == 0) "Nombre" else if (i == 1) "Quién escribe" else "Dato ${i + 1}") {
                                CampoCorredor(parametros[i], { v -> parametros = parametros.toMutableList().also { it[i] = v } })
                            }
                        }
                    }
                }
                BotonPrincipal(if (mandandoPlantilla) "Mandando…" else "Mandar plantilla", { alcance.launch { mandarPlantilla() } }, habilitado = completa && !mandandoPlantilla)
            } else if (aprobadas.isEmpty()) {
                Texto("Todavía no hay plantillas aprobadas en Meta.", Estilo.footnote, C.texto2)
            }
        }
    }

    // MARK: Pantalla

    val lista = rememberLazyListState()
    LaunchedEffect(todos.size) { if (linea.isNotEmpty()) lista.animateScrollToItem(linea.size) }

    // La barra de iOS tiene al medio quién es (un botón); acá la arma la pantalla.
    BarraDe(oculta = true)
    Box(Modifier.fillMaxSize()) {
        FondoApp()
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            // La barra: atrás, quién es (abre el contacto), llamar, «Tomar» y las acciones.
            Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Tocable(alTocar = volver, descripcion = "Atrás") { Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) { Icono("chevron.left", 20f, C.accion) } }
                Tocable(alTocar = { conv?.contactoId?.let { estado.abrir(AFichaContacto(id = it)) } }, modifier = Modifier.weight(1f)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (conv?.sinNombre == true) {
                            Box(Modifier.size(34.dp).clip(CircleShape).background(C.azulFondo), contentAlignment = Alignment.Center) { Texto("+54", Estilo(12f, Peso.bold), C.azulTinta) }
                        } else {
                            Box(Modifier.size(34.dp).clip(CircleShape).background(C.hundida), contentAlignment = Alignment.Center) { Texto(Formato.iniciales(nombre), Estilo(14f, Peso.bold), C.texto) }
                        }
                        Column {
                            Texto(nombre, Estilo(16f, Peso.bold), C.texto, lineas = 1)
                            Texto(subtitulo, Estilo(12f, Peso.regular), C.texto2, lineas = 1)
                        }
                    }
                }
                Tocable(alTocar = { Afuera.llamar(conv?.waId?.let { "+$it" }) }, descripcion = "Llamar a $nombre") {
                    Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) { Icono("phone", 19f, C.accion) }
                }
                val c = conv
                if (estado.escribe && c != null) {
                    if (c.agenteId == null) {
                        Texto("Tomar", Estilo(17f, Peso.semibold), C.accion, Modifier.clickable { alcance.launch { pasar(estado.usuario?.id, "vos") } }.padding(horizontal = 6.dp))
                    }
                    val opciones = buildList {
                        val x = c.contactoId
                        if (x != null) add(OpcionDeMenu("Ver contacto", "person") { estado.abrir(AFichaContacto(id = x)) })
                        else add(OpcionDeMenu("Guardar como contacto", "person.badge.plus") { guardarContacto = true })
                        val o = c.operacionId
                        if (o != null) add(OpcionDeMenu("Ver la operación", "rectangle.split.3x1") { estado.abrir(AFichaOperacion(id = o)) })
                        else add(OpcionDeMenu("Crear operación", "rectangle.split.3x1") { alcance.launch { crearOperacion() } })
                        add(OpcionDeMenu("Pasársela a otro agente", "arrow.left.arrow.right", submenu = buildList {
                            equipo.filter { it.rol != "lector" && it.usuarioId != c.agenteId }.forEach { p ->
                                add(OpcionDeMenu(if (p.usuarioId == estado.usuario?.id) "${p.completo} (vos)" else p.completo, "") { alcance.launch { pasar(p.usuarioId, p.completo) } })
                            }
                            if (c.agenteId != null) add(OpcionDeMenu("Dejarla sin dueño", "person.slash") { alcance.launch { pasar(null, "") } })
                        }))
                        add(OpcionDeMenu(if (c.propiedadId == null) "Vincular a una propiedad" else "Cambiar la propiedad", "link") { vincular = true })
                        add(OpcionDeMenu("Archivar", "archivebox", separadorAntes = true) { alcance.launch { archivar() } })
                    }
                    MenuBoton(opciones = opciones, descripcion = "Más acciones") {
                        Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) { Icono("ellipsis.circle", 20f, C.accion) }
                    }
                }
            }
            val c = conv
            val e = errorCarga
            if (c != null) {
                Box(Modifier.padding(horizontal = 12.dp).padding(top = 10.dp)) { TarjetaPropiedad(c) }
                LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = lista, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(linea, key = { it.id }) { FilaItem(it) }
                    item(key = "fin") { Spacer(Modifier.height(1.dp)) }
                }
            } else if (e != null) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { EstadoError(que = "la conversación", error = e) { alcance.launch { cargar() } } }
            } else {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { Rueda() }
            }
            if (c != null) {
                if (c.enVentana == true) {
                    SugerenciasYTurnos()
                    if (abiertaR) PanelRespuestas()
                    Entrada()
                } else {
                    FueraDeVentana(c)
                }
            }
        }
    }

    Hoja(visible = vincular, alCerrar = { vincular = false }, alturas = listOf(Altura.grande)) {
        VincularPropiedad(tiene = conv?.propiedadId != null) { p ->
            alcance.launch {
                cambiar(
                    json("propiedadId", p?.id),
                    p?.let { "Vinculada a ${it.titulo}" } ?: "Se desvinculó la propiedad",
                    deshacer = json("propiedadId", conv?.propiedadId),
                )
            }
        }
    }
    Hoja(visible = guardarContacto, alCerrar = { guardarContacto = false }, alturas = listOf(Altura.media)) {
        GuardarNumero(waId = conv?.waId ?: "", nombre = conv?.nombre?.nonEmpty ?: "", tipo = if (ficha?.operacion == "alquiler") "inquilino" else "comprador") { n, t ->
            crearContacto(n, t)
        }
    }
    Hoja(visible = guardandoR, alCerrar = { guardandoR = false }, alturas = listOf(Altura.media)) {
        GuardarRespuesta(texto = texto.trim()) { r -> respuestas = respuestas + r }
    }
}

/** Una nota de voz de /v1/archivos (se baja con la sesión antes de escucharla). */
@Composable
fun AudioArchivo(id: String) {
    val estado = LocalEstado.current
    val ctx = LocalContext.current
    var url by remember(id) { mutableStateOf<File?>(null) }
    LaunchedEffect(id) { url = runCatching { ArchivosSesion.archivo(id, nombre = "audio-$id.m4a", api = estado.api, ctx = ctx) }.getOrNull() }
    val u = url
    if (u != null) AudioMensaje(url = u.absolutePath) else Box(Modifier.size(196.dp, 30.dp), contentAlignment = Alignment.Center) { Rueda() }
}

/** Vincular la charla a una propiedad: la búsqueda del servidor (calle, barrio o código) y Desvincular. */
@Composable
fun VincularPropiedad(tiene: Boolean, alElegir: (Resultado?) -> Unit) {
    val estado = LocalEstado.current
    val cerrar = LocalCerrarHoja.current
    var q by remember { mutableStateOf("") }
    var res by remember { mutableStateOf<List<Resultado>>(emptyList()) }
    LaunchedEffect(q) {
        val t = q.trim()
        if (t.length < 2) { res = emptyList(); return@LaunchedEffect }
        delay(200)
        val r: List<Resultado> = estado.api.opcional<List<Resultado>>("/v1/buscar", listOf("q" to t)) ?: emptyList()
        res = r.filter { it.tipo == "propiedad" }.take(8)
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp).padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Grabber() }
        ConIcono("Vincular a una propiedad", "link", Estilo.tituloHojaChico, C.texto)
        Buscador(placeholder = "Calle, barrio o código…", texto = q, alCambiar = { q = it })
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
            Column(Modifier.fillMaxWidth().tarjeta()) {
                res.forEachIndexed { i, x ->
                    key(x.id) {
                        Tocable(alTocar = { alElegir(x); cerrar() }, modifier = Modifier.lineaArriba(i > 0)) {
                            Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.width(24.dp), contentAlignment = Alignment.Center) { Icono("house", 17f, C.accion) }
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                    Texto(x.titulo, Estilo.filaLista, C.texto)
                                    x.subtitulo?.let { Texto(it, Estilo.footnote, C.texto2) }
                                }
                            }
                        }
                    }
                }
                if (q.trim().length >= 2 && res.isEmpty()) {
                    Texto("No encontramos nada con «$q».", Estilo.subhead, C.texto2, Modifier.padding(16.dp))
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (tiene) BotonTexto("Desvincular", { alElegir(null); cerrar() }, Modifier.weight(1f), color = C.error)
            BotonSecundario("Cerrar", { cerrar() })
        }
    }
}

/** «Guardar como contacto»: el nombre y qué busca, con el número de la charla. */
@Composable
fun GuardarNumero(waId: String, nombre: String, tipo: String, guardar: suspend (String, String) -> Boolean) {
    val cerrar = LocalCerrarHoja.current
    val alcance = rememberCoroutineScope()
    val nombreInicial = nombre
    val tipoInicial = tipo
    var nombre by remember { mutableStateOf(nombreInicial) }
    var tipo by remember { mutableStateOf(tipoInicial) }
    var creando by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp).padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Grabber() }
        ConIcono("Guardar como contacto", "person.badge.plus", Estilo.tituloHojaChico, C.texto)
        Texto("Con el número +$waId.", Estilo.subhead, C.texto2)
        Etiquetado("Nombre") { CampoCorredor(nombre, { nombre = it }, mayusculas = KeyboardCapitalization.Words) }
        Etiquetado("Busca") {
            Segmentado(
                opciones = listOf("comprador" to "Comprar", "inquilino" to "Alquilar", "vendedor" to "Vender", "propietario" to "Es propietario"),
                elegido = tipo, alElegir = { tipo = it },
            )
        }
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            // Como el BotonSecundario de iOS: a su ancho; «Guardar» se queda con el resto.
            BotonSecundario("Cancelar", { cerrar() })
            BotonPrincipal(if (creando) "Guardando…" else "Guardar", {
                alcance.launch {
                    creando = true
                    val ok = guardar(nombre.trim(), tipo)
                    creando = false
                    if (ok) cerrar()
                }
            }, Modifier.weight(1f), habilitado = nombre.trim().isNotEmpty() && !creando)
        }
    }
}

/** «Guardar como respuesta»: el nombre corto de lo escrito, para usarlo con una «/». */
@Composable
fun GuardarRespuesta(texto: String, alGuardar: (RespuestaGuardada) -> Unit) {
    val estado = LocalEstado.current
    val cerrar = LocalCerrarHoja.current
    val alcance = rememberCoroutineScope()
    var titulo by remember { mutableStateOf("") }
    var guardando by remember { mutableStateOf(false) }

    suspend fun guardar() {
        @Serializable data class Cuerpo(val titulo: String, val texto: String)
        guardando = true
        try {
            val r: RespuestaGuardada = estado.api.enviar("POST", "/v1/respuestas-guardadas", Cuerpo(titulo = titulo.trim(), texto = texto))
            alGuardar(r)
            estado.avisar("Quedó en las respuestas guardadas: ${r.titulo}")
            cerrar()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            estado.avisar(Estado.texto(e, si = "No se guardó"), ok = false)
        } finally {
            guardando = false
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp).padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Grabber() }
        ConIcono("Guardar como respuesta", "bolt", Estilo.tituloHojaChico, C.texto)
        Texto(Formato.sinViudas("La ve todo el equipo. Escribí «/» en un mensaje para usarla."), Estilo.subhead, C.texto2)
        Etiquetado("Nombre") { CampoCorredor(titulo, { titulo = it }, sinCorrector = true) }
        Texto("Corto, para encontrarla: «Horarios», «Requisitos».", Estilo.footnote, C.texto3)
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            // Como el BotonSecundario de iOS: a su ancho; «Guardar» se queda con el resto.
            BotonSecundario("Cancelar", { cerrar() })
            BotonPrincipal("Guardar", { alcance.launch { guardar() } }, Modifier.weight(1f), habilitado = titulo.trim().isNotEmpty() && !guardando)
        }
    }
}
