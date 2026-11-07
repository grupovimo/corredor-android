package ar.com.corredor.app.pantallas.mas

import ar.com.corredor.app.app.PantallaApilada

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import ar.com.corredor.app.app.LocalEstado
import ar.com.corredor.app.nucleo.*
import ar.com.corredor.app.pantallas.mensajes.Charla
import ar.com.corredor.app.tema.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import java.time.Instant
import java.time.ZoneId

// Clon de Pantallas/Mas/Actividad.swift.

// MARK: - Actividad

/** Un registro de la auditoría (GET /v1/actividad, transversal.Registro). */
@Serializable
data class EntradaActividad(
    /** En la API es un número (la auditoría), y sirve para seguir bajando con `antesDe`. */
    val id: Long,
    val actorTipo: String? = null,
    val actorId: String? = null,
    val actorNombre: String? = null,
    val accion: String? = null,
    val entidad: String? = null,
    val entidadId: String? = null,
    val antes: JsonElement? = null, // JSONLibre de iOS
    val despues: JsonElement? = null, // JSONLibre de iOS
    val entidadTitulo: String? = null,
    val creadoEn: FechaApi? = null,
)

/** Lo que hizo la IA de una persona (GET /v1/ia/registro, mcp.Accion). */
@Serializable
data class AccionIA(
    val id: Long,
    val usuarioId: String? = null,
    val personaNombre: String? = null,
    val cliente: String? = null,
    val resultado: String? = null,
    val detalle: String? = null,
    val entidad: String? = null,
    val entidadId: String? = null,
    val creadoEn: FechaApi? = null,
)

/** Cada registro dicho en criollo, como la web (lib/configuracion/actividad.ts): «Martín registró el cobro de Lucía
 *  Méndez · ARS 816.700», «Sofía pasó Mendoza 2350 a Visita». */
object TextosActividad {
    val articulo: Map<String, String> = mapOf(
        "propiedad" to "la propiedad", "contacto" to "el contacto", "operacion" to "la operación", "cita" to "la cita", "contrato" to "el contrato",
        "cobro" to "el cobro", "rendicion" to "la rendición", "emprendimiento" to "el emprendimiento", "consorcio" to "el consorcio", "unidad" to "la unidad",
        "reserva" to "la reserva", "reserva_temporada" to "la reserva de temporada", "tarifa_temporada" to "la tarifa de temporada", "objetivo" to "un objetivo",
        "documento" to "un documento", "acta" to "el acta", "inventario" to "el inventario", "inventario_item" to "un ítem del inventario",
        "inmobiliaria" to "los datos de la inmobiliaria", "ia_ajustes" to "los ajustes de Corredor en tu IA", "ia_conexion" to "su asistente de IA",
        "publicacion" to "una publicación", "pregunta_ml" to "una pregunta de Mercado Libre", "borrador" to "un borrador", "importacion" to "una importación",
        "periodo_expensas" to "las expensas", "recibo_sueldo" to "un recibo de sueldo", "link_consorcio" to "un link del consorcio",
        "conversacion_whatsapp" to "una conversación de WhatsApp", "whatsapp" to "WhatsApp", "mercadolibre" to "Mercado Libre",
    )
    val verbo: Map<String, String> = mapOf(
        "crear" to "cargó", "editar" to "editó", "borrar" to "borró", "borrar_definitivo" to "borró del todo", "restaurar" to "recuperó", "duplicar" to "duplicó",
        "publicar" to "publicó", "despublicar" to "sacó de la web", "compartir" to "compartió", "dejar_de_compartir" to "dejó de compartir", "conectar" to "conectó",
        "desconectar" to "desconectó", "firmar" to "firmó", "liquidar" to "liquidó", "importar" to "importó", "responder" to "respondió", "reabrir" to "reabrió",
        "soltar" to "dejó sin agente", "estado" to "cambió el estado de", "cambiar_estado" to "cambió el estado de", "descobrar" to "anuló el cobro de",
    )
    val etapa = mapOf("nuevo" to "Nuevo", "contactado" to "Contactado", "visita" to "Visita", "reserva" to "Reserva", "cierre" to "Cierre")
    val rol = mapOf("duenio" to "dueño", "agente" to "agente", "lector" to "lector")

    /** El nombre de pila de quien lo hizo, o «Corredor» / «Alguien desde la web». */
    fun quien(r: EntradaActividad): String {
        if (r.actorTipo == "sistema" || r.actorNombre?.trim().isNullOrEmpty()) return if (r.actorTipo == "publico") "Alguien desde la web" else "Corredor"
        return Charla.primerNombre(r.actorNombre)
    }

    private fun campo(v: JsonElement?, k: String): JsonElement? = (v as? JsonObject)?.get(k)
    private fun txt(v: JsonElement?): String? = (v as? JsonPrimitive)?.takeIf { it.isString }?.content?.trim()?.takeIf { it.isNotEmpty() }
    private fun num(v: JsonElement?): Double? = (v as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull

    /** Lo que hizo, sin el sujeto: «pasó Mendoza 2350 a Visita». */
    fun frase(r: EntradaActividad): String {
        val d = r.despues
        val a = r.antes
        val accion = r.accion ?: ""
        val entidad = r.entidad ?: ""
        val titulo = r.entidadTitulo?.takeIf { it.isNotBlank() } ?: txt(campo(d, "titulo")) ?: txt(campo(d, "nombre")) ?: txt(campo(a, "titulo")) ?: txt(campo(a, "nombre"))
        val que = titulo ?: articulo[entidad] ?: entidad.replace("_", " ")
        if (accion.startsWith("ia.")) return "usó su IA: ${accion.drop(3).replace("_", " ")}"
        when ("$accion:$entidad") {
            "mover:operacion" -> {
                val e = txt(campo(d, "etapa")) ?: ""
                return "pasó $que a ${etapa[e] ?: e}"
            }
            "caer:operacion" -> return "dio por caída $que${txt(campo(d, "motivo"))?.let { " · ${it.lowercase()}" } ?: ""}"
            "cobrar:contrato" -> return "registró el cobro de $que${num(campo(d, "monto"))?.let { " · " + Formato.plata(it, txt(campo(d, "moneda")) ?: "ARS") } ?: ""}"
            "editar:propiedad" -> {
                val p0 = num(campo(a, "precio"))
                val p1 = num(campo(d, "precio"))
                if (p0 != null && p1 != null && p0 != p1) return "cambió el precio de $que: ${Formato.numero(p0.toInt())} → ${Formato.numero(p1.toInt())}"
            }
            "crear:contacto" -> return "cargó a $que"
            "crear:operacion" -> return "abrió la operación $que"
            "crear:cita" -> return "agendó $que"
            "invitar:invitacion" -> {
                val r0 = txt(campo(d, "rol")) ?: ""
                return "invitó a ${txt(campo(d, "email")) ?: "alguien"} como ${rol[r0] ?: r0}"
            }
            "cambiar_rol:miembro" -> {
                val r0 = txt(campo(d, "rol")) ?: ""
                return "cambió el rol de alguien del equipo a ${rol[r0] ?: r0}"
            }
            "sacar:miembro" -> return "sacó a alguien del equipo"
            "verificar_dominio:inmobiliaria" -> return "verificó el dominio ${txt(campo(d, "dominio")) ?: ""}".trim()
            "regenerar_clave_feed:inmobiliaria" -> return "generó otra clave para los feeds de los portales"
            "enviar_plantilla:conversacion_whatsapp" -> return "mandó una plantilla por WhatsApp"
            else -> {}
        }
        val v = verbo[accion] ?: accion.replace("_", " ")
        if (titulo != null && articulo[entidad] != null) return "$v $titulo"
        return "$v $que"
    }
}

/** Una línea de la actividad: lo que hizo una persona o su IA. */
private sealed class Linea {
    data class persona(val r: EntradaActividad) : Linea()
    data class ia(val a: AccionIA) : Linea()

    val id: String get() = when (this) { is persona -> "p${r.id}"; is ia -> "i${a.id}" }
    val en: Instant get() = when (this) { is persona -> r.creadoEn ?: Instant.EPOCH; is ia -> a.creadoEn ?: Instant.EPOCH }
}

private const val PAGINA = 50

/** Actividad, como la del panel (47): quién hizo qué y cuándo, mezclado por hora con lo que hizo la IA de cada
 *  persona; Todo · Personas · La IA; por persona (el dueño); por día; y «Ver más».
 *  `filtro`: «ia» abre filtrado en lo que hizo la IA («Ver todo» del Inicio). */
@Composable
fun Actividad(filtro: String? = null) {
    val estado = LocalEstado.current
    val alcance = rememberCoroutineScope()
    var registros by remember { mutableStateOf<List<EntradaActividad>?>(null) }
    var ia by remember { mutableStateOf<List<AccionIA>>(emptyList()) }
    var equipo by remember { mutableStateOf<List<Miembro>>(emptyList()) }
    // .onAppear { if let filtro, de == "todo" { de = filtro } }
    var de by remember { mutableStateOf(filtro ?: "todo") }
    var persona by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<ErrorApi?>(null) }
    var hayMas by remember { mutableStateOf(false) }
    var bajando by remember { mutableStateOf(false) }

    /** Mezcladas por hora. Con la actividad de personas paginando, la IA entra sólo en la misma ventana. */
    val lineas: List<Linea> = run {
        val rs = registros ?: emptyList()
        val piso = if (de == "todo" && hayMas) rs.lastOrNull()?.creadoEn else null
        val l: List<Linea> = rs.map { Linea.persona(it) } + ia.filter { a -> piso?.let { (a.creadoEn ?: Instant.EPOCH) >= it } ?: true }.map { Linea.ia(it) }
        l.sortedByDescending { it.en }
    }

    val grupos: List<Pair<String, List<Linea>>> = run {
        val g = mutableListOf<Pair<String, MutableList<Linea>>>()
        val zona = Formato.zona
        val hoy = Instant.now().atZone(zona).toLocalDate()
        for (l in lineas) {
            val dia = l.en.atZone(zona).toLocalDate()
            val t = if (dia == hoy) "Hoy" else if (dia == hoy.minusDays(1)) "Ayer" else Formato.dia(l.en)
            if (g.lastOrNull()?.first == t) g.last().second.add(l) else g.add(t to mutableListOf(l))
        }
        g
    }

    suspend fun pedirRegistros(antesDe: Long?): List<EntradaActividad> {
        val q = mutableListOf("limite" to "$PAGINA")
        if (persona.isNotEmpty()) q.add("actor" to persona)
        if (antesDe != null) q.add("antesDe" to "$antesDe")
        return estado.api.get("/v1/actividad", q)
    }

    suspend fun pedirIA(antesDe: Long?): List<AccionIA> {
        val q = mutableListOf("limite" to "$PAGINA")
        if (persona.isNotEmpty()) q.add("persona" to persona)
        if (antesDe != null) q.add("antesDe" to "$antesDe")
        return estado.api.opcional<List<AccionIA>>("/v1/ia/registro", q) ?: emptyList()
    }

    suspend fun cargar() {
        try {
            var rs: List<EntradaActividad> = emptyList()
            if (de != "ia") rs = pedirRegistros(null)
            registros = rs
            var x: List<AccionIA> = emptyList()
            if (de != "personas") x = pedirIA(null)
            ia = x
            hayMas = if (de == "ia") ia.size == PAGINA else rs.size == PAGINA
            error = null
        } catch (e: CancellationException) { throw e } catch (e: ErrorApi) { if (registros == null) error = e } catch (e: Exception) {}
        if (equipo.isEmpty()) equipo = estado.api.opcional<List<Miembro>>("/v1/equipo") ?: emptyList()
    }

    /** La página siguiente: lo anterior al último que se ve (`antesDe`, sin saltos aunque entren nuevos). */
    suspend fun masViejos() {
        bajando = true
        try {
            if (de == "ia") {
                val u = ia.lastOrNull()?.id ?: return
                val l = pedirIA(u)
                ia = ia + l
                hayMas = l.size == PAGINA
            } else {
                val u = registros?.lastOrNull()?.id ?: return
                val l = pedirRegistros(u)
                registros = (registros ?: emptyList()) + l
                val iu = ia.lastOrNull()?.id
                if (de == "todo" && iu != null) {
                    val mas = pedirIA(iu)
                    ia = ia + mas
                }
                hayMas = l.size == PAGINA
            }
        } catch (e: CancellationException) { throw e } catch (e: Exception) { estado.avisar(e) } finally { bajando = false }
    }

    fun abrir(entidad: String?, id: String?) {
        if (id == null) return
        when (entidad) {
            "propiedad" -> estado.abrir(AFichaPropiedad(id = id))
            "contacto" -> estado.abrir(AFichaContacto(id = id))
            "operacion" -> estado.abrir(AFichaOperacion(id = id))
            "contrato" -> estado.abrir(AFichaContrato(id = id))
            "cita" -> estado.abrir(ACita(id = id))
            else -> {}
        }
    }

    @Composable
    fun linea(l: Linea) {
        when (l) {
            is Linea.persona -> {
                val r = l.r
                Tocable({ abrir(r.entidad, r.entidadId) }) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Texto(r.creadoEn?.let { Formato.horaDe(it) } ?: "", Estilo(12f, Peso.regular), C.texto3, Modifier.width(40.dp).padding(top = 2.dp))
                        TextoRico(buildAnnotatedString {
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(TextosActividad.quien(r)) }
                            append(" " + TextosActividad.frase(r))
                        }, Estilo.subhead, C.texto, Modifier.weight(1f))
                    }
                }
            }
            is Linea.ia -> {
                val a = l.a
                val ok = a.resultado == "ok"
                Tocable({ abrir(a.entidad, a.entidadId) }) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Texto(a.creadoEn?.let { Formato.horaDe(it) } ?: "", Estilo(12f, Peso.regular), C.texto3, Modifier.width(40.dp).padding(top = 2.dp))
                        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(Modifier.height(20.dp).clip(Capsula).background(if (ok) C.violetaFondo else C.errorFondo).padding(horizontal = 6.dp), contentAlignment = Alignment.Center) {
                                ConIcono("IA", "sparkles", Estilo(11f, Peso.bold), if (ok) C.violetaTinta else C.errorTinta)
                            }
                            TextoRico(buildAnnotatedString {
                                append(a.detalle ?: "")
                                append(" · ")
                                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(Charla.primerNombre(a.personaNombre).ifBlank { "Alguien" }) }
                            }, Estilo.subhead, if (ok) C.texto else C.errorTinta, Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(estado.version, de, persona) { cargar() }

    PantallaApilada("Actividad") {
        Refrescable({ cargar() }) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Texto(
                    if (estado.rol == "agente") "Lo que hiciste vos y tu IA, lo último primero." else "Quién hizo qué y cuándo. También lo que hizo la IA de cada persona.",
                    Estilo.subhead, C.texto2,
                )
                Segmentado(listOf("todo" to "Todo", "personas" to "Personas", "ia" to "La IA"), de, { de = it })
                if (estado.rol != "agente" && equipo.size > 1) {
                    SelectorMenu(listOf("" to "Todo el equipo") + equipo.map { it.usuarioId to it.completo }, persona, { persona = it }, descripcion = "Persona")
                }
                val regs = registros
                val err = error
                if (regs != null) {
                    if (lineas.isEmpty()) {
                        Box(Modifier.padding(top = 30.dp)) {
                            EstadoVacio(
                                icono = if (de == "ia") "sparkles" else "clock.arrow.circlepath",
                                titulo = if (de == "ia") "La IA todavía no hizo nada" else "Todavía no hay actividad",
                                texto = if (de == "ia") "Cuando alguien conecte su asistente, cada cosa que haga queda acá." else "Cada cambio del equipo aparece acá con quién lo hizo y cuándo.",
                                accion = if (de != "todo" || persona.isNotEmpty()) ("Ver todo" to { de = "todo"; persona = "" }) else null,
                            )
                        }
                    } else {
                        Column(Modifier.fillMaxWidth().tarjeta()) {
                            grupos.forEach { (titulo, ls) ->
                                Texto(titulo, Estilo.label, C.texto2, Modifier.padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 4.dp))
                                ls.forEach { l -> androidx.compose.runtime.key(l.id) { linea(l) } }
                            }
                            if (hayMas) {
                                BotonTexto(if (bajando) "Cargando…" else "Ver más", { alcance.launch { masViejos() } }, habilitado = !bajando)
                            }
                        }
                    }
                } else if (err != null) {
                    EstadoError("la actividad", err) { alcance.launch { cargar() } }
                } else {
                    EsqueletoFilas(6)
                }
            }
        }
    }
}
