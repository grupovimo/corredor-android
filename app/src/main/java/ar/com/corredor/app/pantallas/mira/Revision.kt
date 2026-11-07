package ar.com.corredor.app.pantallas.mira

// Clon de Pantallas/Mira/Revision.swift.

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import ar.com.corredor.app.app.*
import ar.com.corredor.app.nucleo.*
import ar.com.corredor.app.pantallas.cartera.Contacto
import ar.com.corredor.app.pantallas.propiedades.Propiedad
import ar.com.corredor.app.tema.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import java.time.Instant

// MARK: - Revisión (lo que armó la IA)

/** Un borrador de la IA (GET /v1/revision; dominio/revision). */
@Serializable
data class BorradorIA(
    val id: String,
    /** pendiente · usado · descartado */
    val estado: String? = null,
    /** mensaje · descripcion · nota */
    val tipo: String? = null,
    val canal: String? = null,
    val asunto: String? = null,
    val texto: String? = null,
    val motivo: String? = null,
    val contactoId: String? = null,
    val propiedadId: String? = null,
    val operacionId: String? = null,
    val contratoId: String? = null,
    val contactoNombre: String? = null,
    val propiedadTitulo: String? = null,
    val deLaIA: Boolean? = null,
    val creadoEn: FechaApi? = null,
    val resueltoEn: FechaApi? = null,
)

/** Los borradores que mandó la IA (el link `/revision?ids=…`): esos, incluidos los ya resueltos. */
@Serializable
data class ARevisionIds(val ids: String)

/** Borradores de tu IA: lo que armó «Corredor en tu IA» (mensajes, descripciones, notas) espera acá a que una persona
 *  lo mire, lo corrija y lo use, o lo descarte. La IA no manda ni publica. `ids`: los del link de la IA (los muestra
 *  aunque ya estén resueltos). */
@Composable
fun RevisionIA(ids: String? = null) {
    val estado = LocalEstado.current
    val ctx = LocalContext.current
    var lista by remember { mutableStateOf<List<BorradorIA>?>(null) }
    var pendientesTodos by remember { mutableStateOf<List<BorradorIA>>(emptyList()) }
    var error by remember { mutableStateOf<ErrorApi?>(null) }
    var editando by remember { mutableStateOf<BorradorIA?>(null) }
    var ocultos by remember { mutableStateOf<Set<String>>(emptySet()) }
    var usados by remember { mutableStateOf<Set<String>>(emptySet()) }
    var ocupados by remember { mutableStateOf<Set<String>>(emptySet()) }

    val visibles = (lista ?: emptyList()).filter { it.id !in ocultos }
    val pendientes = visibles.filter { it.estado == "pendiente" && it.id !in usados }
    val resueltos = visibles.filter { it.estado != "pendiente" || it.id in usados }

    suspend fun cargar() {
        try {
            val q = mutableListOf("limite" to "100", "estado" to (if (ids == null) "pendiente" else "todos"))
            if (ids != null) q.add("ids" to ids)
            lista = estado.api.get("/v1/revision", query = q)
            error = null
            pendientesTodos = estado.api.opcional<List<BorradorIA>>("/v1/revision", query = listOf("limite" to "200")) ?: emptyList()
        } catch (e: CancellationException) { throw e } catch (e: ErrorApi) { error = e } catch (e: Exception) {}
    }

    @Serializable data class CuerpoCorregir(val texto: String, val asunto: String? = null)

    suspend fun guardar(b: BorradorIA, asunto: String?, texto: String) {
        try {
            estado.api.hacer("PATCH", "/v1/revision/${b.id}", CuerpoCorregir(texto, if (b.canal == "email") asunto else null))
            lista = lista?.map { if (it.id == b.id) it.copy(texto = texto, asunto = if (b.canal == "email") asunto else it.asunto) else it }
            estado.avisar("Borrador corregido")
        } catch (e: CancellationException) { throw e } catch (e: Exception) { estado.avisar(e) }
    }

    @Serializable data class N(val tipo: String = "nota", val texto: String)
    @Serializable data class O(val texto: String)
    @Serializable data class Conv(val contactoId: String)
    @Serializable data class Creada(val id: String)
    @Serializable data class M(val texto: String)

    /** Usarlo como la web: el WhatsApp sale por la conversación de Corredor (queda en Mensajes); la descripción va a la
     *  ficha; la nota, al contacto o a la operación; el mail se abre en el correo. */
    suspend fun usar(b: BorradorIA) {
        val t = b.texto ?: ""
        if (b.canal == "email") {
            var mail: String? = null
            b.contactoId?.let { c -> mail = estado.api.opcional<Contacto>("/v1/contactos/$c")?.email }
            val u = "mailto:${mail ?: ""}?subject=${Uri.encode(b.asunto ?: "")}&body=${Uri.encode(t)}"
            abrirUrl(ctx, u)
            usados = usados + b.id
            try { estado.api.hacer("POST", "/v1/revision/${b.id}/usado") } catch (e: CancellationException) { throw e } catch (e: Exception) { estado.avisar(e) }
            estado.avisar("Se abrió el mail. Quedó como usado.") { usados = usados - b.id }
            return
        }
        ocupados = ocupados + b.id
        var ir: Any? = null
        try {
            when (b.tipo) {
                "descripcion" -> {
                    val pid = b.propiedadId ?: run { estado.avisar("El borrador no dice de qué propiedad es.", ok = false); return }
                    val p: Propiedad = estado.api.get("/v1/propiedades/$pid")
                    estado.api.hacer("PUT", "/v1/propiedades/$pid", p.copy(descripcion = t))
                    ir = AFichaPropiedad(id = pid)
                }
                "nota" -> {
                    val c = b.contactoId
                    val o = b.operacionId
                    if (c != null) {
                        estado.api.hacer("POST", "/v1/contactos/$c/notas", N(texto = t))
                        ir = AFichaContacto(id = c)
                    } else if (o != null) {
                        estado.api.hacer("POST", "/v1/operaciones/$o/notas", O(t))
                        ir = AFichaOperacion(id = o)
                    } else { estado.avisar("La nota no dice de quién es.", ok = false); return }
                }
                else -> {
                    val c = b.contactoId ?: run { estado.avisar("El borrador no dice a quién mandarlo.", ok = false); return }
                    val conv: Creada = estado.api.enviar("POST", "/v1/whatsapp/conversaciones", Conv(c))
                    ir = AChat(id = conv.id)
                    try {
                        estado.api.hacer("POST", "/v1/whatsapp/conversaciones/${conv.id}/mensajes", M(t))
                    } catch (e: ErrorApi) {
                        if (e.codigo != "fuera_de_ventana") throw e
                        // Fuera de las 24 h sólo sale una plantilla: se abre la conversación para elegirla.
                        estado.sugerir("Pasaron más de 24 h: hay que retomar con una plantilla", "Abrir la conversación", AChat(id = conv.id))
                        return
                    }
                }
            }
            estado.api.hacer("POST", "/v1/revision/${b.id}/usado")
            usados = usados + b.id
            val listo = if (b.tipo == "descripcion") "Quedó en la ficha" else if (b.tipo == "nota") "Quedó como nota" else "Mandado a ${b.contactoNombre ?: "el contacto"}"
            val destino = ir
            if (destino != null) estado.sugerir(listo, "Ver", destino) else estado.avisar(listo)
            estado.version += 1
        } catch (e: CancellationException) { throw e } catch (e: Exception) { estado.avisar(e) } finally { ocupados = ocupados - b.id }
    }

    /** Descartar espera los segundos del aviso (Deshacer) antes de mandarse. */
    fun descartar(b: BorradorIA) {
        ocultos = ocultos + b.id
        val tarea: Job = estado.tarea {
            delay(5000)
            try {
                estado.api.hacer("DELETE", "/v1/revision/${b.id}")
                estado.version += 1
            } catch (e: CancellationException) { throw e } catch (e: Exception) {
                ocultos = ocultos - b.id
                estado.avisar("No se descartó", ok = false)
            }
        }
        estado.avisar("Borrador descartado") { tarea.cancel(); ocultos = ocultos - b.id }
    }

    LaunchedEffect(estado.version) { cargar() }

    PantallaApilada("Borradores de tu IA", acciones = if (ids != null) ({ BotonBarra("Ver todos") { estado.abrir(ASeccion(clave = "revision")) } }) else null) {
        Refrescable({ cargar() }) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Texto(Formato.sinViudas("La IA no manda ni publica: deja acá lo que armó. Miralo, corregilo y usalo, o descartalo."), Estilo.subhead, C.texto2)
                val err = error
                if (lista != null) {
                    // Las cuentas.
                    val c = { t: String -> pendientesTodos.count { it.tipo == t } }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Texto(if (pendientesTodos.isEmpty()) "Al día" else "${pendientesTodos.size} esperan", Estilo.footnoteFuerte, C.texto)
                        Texto("· Mensajes ${c("mensaje")} · Descripciones ${c("descripcion")} · Notas ${c("nota")}", Estilo.footnote, C.texto2)
                    }
                    if (pendientes.isEmpty()) {
                        Box(Modifier.padding(top = 20.dp)) {
                            EstadoVacio(
                                icono = "checkmark.shield", titulo = "No hay borradores esperando",
                                texto = "Cuando le pidas a tu IA que arme un mensaje, una descripción o una nota, aparece acá para que lo revises antes de usarlo.",
                                accion = "Conectar tu IA" to { estado.abrir(ASeccion(clave = "ia")) },
                            )
                        }
                    }
                    pendientes.forEach { b ->
                        TarjetaBorrador(b, estado.escribe, b.id in ocupados,
                            usar = { estado.tarea { usar(b) } }, corregir = { editando = b }, descartar = { descartar(b) })
                    }
                    if (resueltos.isNotEmpty()) {
                        Box(Modifier.padding(top = 8.dp)) { TituloBloque("Ya resueltos") }
                        Column(Modifier.fillMaxWidth().tarjeta()) {
                            resueltos.forEachIndexed { i, b ->
                                val a = aspecto(b)
                                if (i > 0) Box(Modifier.fillMaxWidth().height(0.5.dp).background(C.linea))
                                Row(
                                    Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    IconoTono(a.icono, a.tono, tam = 34.dp)
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                        Texto(a.titulo, Estilo.filaLista, C.texto, lineas = 1)
                                        val cuando = b.resueltoEn ?: (if (b.id in usados) Instant.now() else null)
                                        Texto("${if (b.estado == "descartado") "Descartado" else "Usado"}${cuando?.let { " · ${Formato.hace(it)}" } ?: ""}", Estilo.footnote, C.texto2)
                                    }
                                }
                            }
                        }
                    }
                } else if (err != null) {
                    Box(Modifier.padding(top = 30.dp)) { EstadoError("los borradores", err) { estado.tarea { cargar() } } }
                } else EsqueletoFilas(filas = 4)
            }
        }
    }

    val ed = editando
    Hoja(ed != null, { editando = null }) {
        if (ed != null) CorregirBorrador(ed, cerrar = { editando = null }) { asunto, texto -> estado.tarea { guardar(ed, asunto, texto) } }
    }
}

data class Aspecto(val icono: String, val tono: Tono, val titulo: String, val usar: String)

/** RevisionIA.aspecto en iOS. */
fun aspecto(b: BorradorIA): Aspecto = when (b.tipo) {
    "descripcion" -> Aspecto("doc.text", Tono.oro, "Descripción de ${b.propiedadTitulo ?: "una propiedad"}", "Usar en la ficha")
    "nota" -> Aspecto("note.text", Tono.neutro, "Nota sobre ${b.contactoNombre ?: b.propiedadTitulo ?: "una operación"}", "Guardar como nota")
    else -> if (b.canal == "email") Aspecto("envelope", Tono.azul, "Mail para ${b.contactoNombre ?: "un contacto"}", "Abrir en el mail")
    else Aspecto("bubble.left", Tono.verde, "WhatsApp para ${b.contactoNombre ?: "un contacto"}", "Mandar por WhatsApp")
}

@Composable
private fun TarjetaBorrador(b: BorradorIA, escribe: Boolean, ocupado: Boolean, usar: () -> Unit, corregir: () -> Unit, descartar: () -> Unit) {
    val a = aspecto(b)
    Column(Modifier.fillMaxWidth().tarjeta().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            IconoTono(a.icono, a.tono, tam = 38.dp)
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Texto(a.titulo, Estilo.filaLista, C.texto)
                Texto(
                    "${b.motivo?.let { "$it · " } ?: ""}${if (b.deLaIA == true) "armado por tu IA" else "armado en Corredor"}${b.creadoEn?.let { " · ${Formato.hace(it)}" } ?: ""}",
                    Estilo.footnote, C.texto2,
                )
            }
        }
        b.asunto?.let { s ->
            val c2 = C.texto2
            TextoRico(buildAnnotatedString { withStyle(SpanStyle(color = c2)) { append("Asunto: ") }; append(s) }, Estilo.subheadFuerte, C.texto)
        }
        val burbuja = b.tipo == "mensaje" && b.canal != "email"
        Texto(
            b.texto ?: "", Estilo.texto14, C.texto,
            if (burbuja) Modifier.clip(RoundedCornerShape(14.dp)).background(C.okFondo).padding(12.dp) else Modifier,
        )
        if (escribe) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                BotonAccion(if (ocupado) "Usando…" else a.usar, usar, Modifier.weight(1f), habilitado = !ocupado)
                BotonHundido("Corregir", corregir, Modifier.weight(1f))
                BotonTexto("Descartar", descartar, Modifier.weight(1f))
            }
        }
    }
}

/** Corregir el borrador: el asunto (si es un mail) y el texto. */
@Composable
fun CorregirBorrador(b: BorradorIA, cerrar: () -> Unit = LocalCerrarHoja.current, guardar: (String?, String) -> Unit) {
    var asunto by remember(b.id) { mutableStateOf(b.asunto ?: "") }
    var texto by remember(b.id) { mutableStateOf(b.texto ?: "") }
    BarraHoja(
        "Corregir el borrador",
        izquierda = { BotonBarra("Cancelar") { cerrar() } },
        derecha = { BotonBarra("Guardar", fuerte = true, habilitado = texto.trim().isNotEmpty()) { guardar(asunto.takeIf { it.isNotBlank() }, texto); cerrar() } },
    )
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SeccionLista(null) { Texto(aspecto(b).titulo, Estilo.footnote, C.texto2, Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) }
        if (b.canal == "email") SeccionLista("Asunto") {
            Entrada(asunto, { asunto = it }, "Asunto", Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
        }
        SeccionLista("Texto") {
            Entrada(texto, { texto = it }, "Texto", Modifier.padding(horizontal = 16.dp, vertical = 12.dp), lineas = 6, lineasMaximas = 16)
        }
    }
}

// MARK: - Piezas de este archivo
