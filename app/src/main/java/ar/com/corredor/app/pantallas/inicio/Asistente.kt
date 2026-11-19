package ar.com.corredor.app.pantallas.inicio

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import ar.com.corredor.app.app.*
import ar.com.corredor.app.nucleo.*
import ar.com.corredor.app.pantallas.mira.ARevisionIds
import ar.com.corredor.app.tema.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.util.UUID

// Clon de Pantallas/Inicio/Asistente.swift.

// MARK: - Lo que contesta la API (dominio/asistente; web lib/asistente/charla.ts)

/** GET /v1/asistente: si hay IA para esta persona (la función IA del plan) y qué preguntar para arrancar. */
@Serializable
data class EstadoAsistente(val ia: Boolean = false, val aviso: String? = null, val sugerencias: List<String>? = null)

/** Un resultado con su página del panel (`/contratos/<id>`, `/cobranzas?registrar=1&contrato=<id>`…). */
@Serializable
data class ItemAsistente(val tipo: String, val id: String, val titulo: String, val subtitulo: String? = null, val ruta: String)

/** Algo que el asistente propone escribir: no existe hasta que la persona lo confirma (va tal cual a
 *  POST /v1/asistente/confirmar). */
@Serializable
data class PropuestaAsistente(
    val herramienta: String,
    val titulo: String,
    val para: String? = null,
    val texto: String? = null,
    val args: Map<String, JSONLibre> = emptyMap(),
)

/** POST /v1/asistente. */
@Serializable
data class RespuestaAsistente(
    val texto: String = "",
    /** motor: reglas propias, sin IA · ia · cache: la misma pregunta hace un rato · sin_ia */
    val origen: String = "",
    val resultados: List<ItemAsistente>? = null,
    val propuestas: List<PropuestaAsistente>? = null,
    /** Por qué no contestó la IA (tope, plan, no respondió). */
    val aviso: String? = null,
)

/** Un turno en pantalla. Las respuestas llevan sus propuestas con su estado (y a dónde ir después de confirmar). */
data class TurnoAsistente(
    val id: Int,
    val rol: Rol,
    val texto: String,
    /** Lo que se preguntó (para «Probar de nuevo» en un error). */
    val pregunta: String = "",
    val respuesta: RespuestaAsistente? = null,
    val propuestas: List<Propuesta> = emptyList(),
) {
    enum class Rol { persona, asistente, error }
    enum class EstadoPropuesta { pendiente, confirmando, hecha, descartada }

    data class Propuesta(
        val p: PropuestaAsistente,
        val estado: EstadoPropuesta = EstadoPropuesta.pendiente,
        val ir: Any? = null,
        /** La Idempotency-Key: el doble toque no la crea dos veces. */
        val envio: String = UUID.randomUUID().toString(),
    ) {
        val id: String get() = envio
    }
}

@Serializable
internal data class TurnoParaApi(val rol: String, val texto: String)

@Serializable
internal data class PreguntaAsistente(val pregunta: String, val historial: List<TurnoParaApi>)

@Serializable
internal data class ConfirmarAsistente(val herramienta: String, val args: Map<String, JSONLibre>)

@Serializable
internal data class HechoAsistente(val resumen: String? = null, val datos: JSONLibre? = null)

// MARK: - Cuentas (charla.ts)

object Asistente {
    /** Los mismos topes que la API (ia.MaxTurnos y ia.MaxLargoTurno). */
    const val maxTurnos = 8
    const val maxLargo = 600

    /** Mientras no llega la API, las que contesta el motor sin IA (dominio/asistente · SugerenciasMotor): nunca se ofrece
     *  algo que termine en «no lo puedo contestar». */
    val sugerenciasBase = listOf(
        "¿Qué contratos ajustan este mes?", "¿Quiénes deben el alquiler?", "¿Qué tengo hoy?", "Contratos por vencer",
        "Borradores para revisar",
    )

    /** El historial para la API: los últimos turnos de texto, recortados; los errores no cuentan. */
    fun historial(turnos: List<TurnoAsistente>): List<Pair<String, String>> {
        val out = mutableListOf<Pair<String, String>>()
        for (t in turnos) {
            when (t.rol) {
                TurnoAsistente.Rol.persona -> out.add("persona" to t.texto.take(maxLargo))
                TurnoAsistente.Rol.asistente -> if (t.texto.nonEmpty != null) out.add("asistente" to t.texto.take(maxLargo))
                TurnoAsistente.Rol.error -> {}
            }
        }
        return out.takeLast(maxTurnos)
    }

    data class QueHace(val icono: String, val tono: Tono, val titulo: String, val listo: String)

    /** Qué hace la propuesta, en criollo (los íconos de «Borradores de tu IA»). */
    fun queHace(p: PropuestaAsistente): QueHace {
        val para = p.para?.nonEmpty?.let { " para $it" } ?: ""
        return when (p.herramienta) {
            "mensajes_crear_borrador" ->
                if (p.args["canal"]?.texto == "email") QueHace("envelope", Tono.azul, "Mail$para", "Quedó el mail para revisar")
                else QueHace("bubble.left", Tono.verde, "WhatsApp$para", "Quedó el mensaje para revisar")
            "propiedad_borrador_descripcion" -> QueHace("doc.text", Tono.oro, "Descripción$para", "Quedó la descripción para revisar")
            "notas_crear_borrador" -> QueHace("note.text", Tono.neutro, "Nota$para", "Quedó la nota para revisar")
            "propiedad_crear_borrador" -> QueHace("house", Tono.oro, "Propiedad nueva en borrador", "Quedó en los borradores de propiedades")
            else -> QueHace("square.and.pencil", Tono.neutro, p.titulo, "Listo")
        }
    }

    /** A dónde ir después de confirmar: el borrador del alta, o ese borrador en «Borradores de tu IA». */
    fun despues(p: PropuestaAsistente, datos: JSONLibre?): Any {
        val id = datos?.get("id")?.texto?.nonEmpty
        if (p.herramienta == "propiedad_crear_borrador") return AAltaPropiedad(borrador = id)
        if (id != null) return ARevisionIds(ids = id)
        return ASeccion(clave = "revision")
    }

    /** De dónde salió la respuesta: el ícono y el texto de abajo. Si el plan no trae IA, la cabeza ya lo dice: el pie
     *  queda en «Sin IA». Con IA en el plan (un tope, un corte), el pie dice el motivo. */
    fun origen(r: RespuestaAsistente, hayIA: Boolean = true): Pair<String, String> = when (r.origen) {
        "motor" -> "bolt" to "Con los datos de Corredor"
        "sin_ia" -> "info.circle" to (if (hayIA) r.aviso?.nonEmpty ?: "Sin IA" else "Sin IA")
        else -> "sparkles" to "Con IA · puede equivocarse"
    }
}

// MARK: - Pantalla

/** El asistente (panel web /asistente): se pregunta como se diría («¿qué contratos ajustan este mes?»). Contesta primero
 *  el motor de Corredor (al instante, sin IA) y, si hace falta y el plan la trae, la IA con lo que el rol deja ver. Lo
 *  que propone escribir queda como propuesta: se crea sólo al tocar «Confirmar» y va a los borradores para revisar (no
 *  se manda nada). La charla vive en la pantalla: el servidor no la guarda; cada pregunta lleva los últimos turnos.
 *  `pregunta`: con algo escrito, desde la búsqueda arranca preguntando eso; desde un link de afuera (`/asistente?q=`,
 *  `enviar` false) sólo lo deja escrito, para que nadie gaste la IA con un link. */
@Composable
fun Asistente(pregunta: String = "", enviar: Boolean = true) {
    val estado = LocalEstado.current
    val alcance = rememberCoroutineScope()
    val focos = LocalFocusManager.current
    var info by remember { mutableStateOf<EstadoAsistente?>(null) }
    var turnos by remember { mutableStateOf<List<TurnoAsistente>>(emptyList()) }
    var texto by remember { mutableStateOf("") }
    var pensando by remember { mutableStateOf(false) }
    var proximo by remember { mutableIntStateOf(0) }
    var arranco by remember { mutableStateOf(false) }
    val lista = rememberLazyListState()

    fun siguiente(): Int {
        proximo += 1
        return proximo
    }

    fun cambiar(turno: Int, id: String, f: (TurnoAsistente.Propuesta) -> TurnoAsistente.Propuesta) {
        turnos = turnos.map { t -> if (t.id != turno) t else t.copy(propuestas = t.propuestas.map { if (it.id == id) f(it) else it }) }
    }

    suspend fun preguntar(q0: String) {
        val q = q0.trim().take(1000)
        if (q.isEmpty() || pensando) return
        val previos = Asistente.historial(turnos).map { TurnoParaApi(it.first, it.second) }
        turnos = turnos + TurnoAsistente(id = siguiente(), rol = TurnoAsistente.Rol.persona, texto = q)
        texto = ""
        pensando = true
        try {
            val r: RespuestaAsistente = estado.api.enviar("POST", "/v1/asistente", PreguntaAsistente(q, previos))
            turnos = turnos + TurnoAsistente(
                id = siguiente(), rol = TurnoAsistente.Rol.asistente, texto = r.texto, respuesta = r,
                propuestas = (r.propuestas ?: emptyList()).map { TurnoAsistente.Propuesta(p = it) },
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: ErrorApi) {
            if (e.estado == 402) {
                // Sin plan: lleva a «Ver planes», no a un «Probar de nuevo» que va a fallar siempre.
                estado.avisar(e)
            } else {
                val m = if (e.sinConexion) "No pude contestar. Revisá la señal y probá de nuevo." else (e.detalle.nonEmpty ?: e.titulo)
                turnos = turnos + TurnoAsistente(id = siguiente(), rol = TurnoAsistente.Rol.error, texto = m, pregunta = q)
            }
        } catch (e: Exception) {
            val a = e as? ErrorApi
            val m = if (a == null || a.sinConexion) "No pude contestar. Revisá la señal y probá de nuevo." else (a.detalle.nonEmpty ?: a.titulo)
            turnos = turnos + TurnoAsistente(id = siguiente(), rol = TurnoAsistente.Rol.error, texto = m, pregunta = q)
        } finally {
            pensando = false
        }
    }

    /** Confirmar: POST /v1/asistente/confirmar. Deja el borrador para revisar (nada se manda) y el aviso lleva ahí. */
    suspend fun confirmar(turno: Int, id: String) {
        val p = turnos.firstOrNull { it.id == turno }?.propuestas?.firstOrNull { it.id == id } ?: return
        if (p.estado != TurnoAsistente.EstadoPropuesta.pendiente) return
        cambiar(turno, id) { it.copy(estado = TurnoAsistente.EstadoPropuesta.confirmando) }
        try {
            val h: HechoAsistente = estado.api.enviar("POST", "/v1/asistente/confirmar", ConfirmarAsistente(p.p.herramienta, p.p.args), envio = p.envio)
            val ir = Asistente.despues(p.p, h.datos)
            cambiar(turno, id) { it.copy(estado = TurnoAsistente.EstadoPropuesta.hecha, ir = ir) }
            Haptico.exito()
            estado.sugerir(Asistente.queHace(p.p).listo, "Ver", ir)
            estado.version += 1
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            cambiar(turno, id) { it.copy(estado = TurnoAsistente.EstadoPropuesta.pendiente) }
            estado.avisar(e)
        }
    }

    suspend fun confirmarTodas(turno: Int) {
        val t = turnos.firstOrNull { it.id == turno } ?: return
        for (p in t.propuestas) if (p.estado == TurnoAsistente.EstadoPropuesta.pendiente) confirmar(turno, p.id)
    }

    /** Descartar no va a la API (la propuesta no existe todavía): sólo se saca, con «Deshacer». */
    fun descartar(turno: Int, id: String) {
        cambiar(turno, id) { it.copy(estado = TurnoAsistente.EstadoPropuesta.descartada) }
        estado.avisar("Propuesta descartada") { cambiar(turno, id) { it.copy(estado = TurnoAsistente.EstadoPropuesta.pendiente) } }
    }

    /** Un resultado abre su pantalla de la app (las rutas son las del panel). */
    fun abrir(x: ItemAsistente) {
        Ruta.de(x.ruta)?.destino?.let { d -> estado.abrir(d) }
    }

    fun mandar() {
        val q = texto
        alcance.launch { preguntar(q) }
    }

    LaunchedEffect(Unit) {
        if (arranco) return@LaunchedEffect
        arranco = true
        // En paralelo: el estado (si hay IA) no demora la primera pregunta.
        alcance.launch { if (info == null) info = estado.api.opcional<EstadoAsistente>("/v1/asistente") }
        pregunta.nonEmpty?.let { if (enviar) preguntar(it) else texto = it }
    }
    // La respuesta se lee desde arriba: con un turno del asistente se va a su comienzo; con la pregunta o «Buscando…», al
    // final. Al abrir no se mueve (la cabeza y el aviso sin IA tienen que verse).
    LaunchedEffect(turnos.size, pensando) {
        if (turnos.isEmpty() && !pensando) return@LaunchedEffect
        val ultimo = turnos.lastOrNull()
        if (!pensando && ultimo != null && ultimo.rol != TurnoAsistente.Rol.persona) {
            lista.animateScrollToItem(1 + turnos.size - 1)
        } else {
            val n = 1 + (if (turnos.isEmpty()) 1 else 0) + turnos.size + (if (pensando) 1 else 0)
            lista.animateScrollToItem(n - 1)
        }
    }

    // MARK: Piezas

    @Composable
    fun Cabeza() {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Texto(Formato.sinViudas("Preguntá como lo dirías. Lo que propongo escribir lo confirmás vos."), Estilo.subhead, C.texto2)
            val i = info
            val a = i?.aviso?.nonEmpty
            if (i != null && !i.ia && a != null) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(C.avisoFondo).padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top,
                ) {
                    Icono("info.circle", 13f, C.avisoTinta)
                    Texto(Formato.sinViudas(a), Estilo.footnote, C.avisoTinta, Modifier.weight(1f))
                }
            }
        }
    }

    /** Para empezar: las sugerencias de la API, de a una por fila. Debajo de una respuesta sin IA vuelven como «Probá con»
     *  (tocables, en vez de una lista escrita). */
    @Composable
    fun Arranque(titulo: String = "PARA EMPEZAR") {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Texto(titulo, Estilo(13f, Peso.bold, 0.5f / 13f), C.texto2, Modifier.padding(horizontal = 4.dp))
            Column(Modifier.fillMaxWidth().tarjeta(16.dp)) {
                val ss = info?.sugerencias?.takeIf { it.isNotEmpty() } ?: Asistente.sugerenciasBase
                ss.forEachIndexed { i, s ->
                    Tocable(alTocar = { alcance.launch { preguntar(s) } }, habilitado = !pensando, modifier = Modifier.lineaArriba(i > 0)) {
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 50.dp).padding(horizontal = 14.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.width(24.dp), contentAlignment = Alignment.Center) { Icono("sparkles", 17f, C.accion) }
                            Texto(s, Estilo.filaLista, C.texto, Modifier.weight(1f))
                            Chevron()
                        }
                    }
                }
            }
        }
    }

    /** La propuesta como en «Borradores de tu IA»: qué es, para quién, el texto y Confirmar / Descartar. */
    @Composable
    fun TarjetaPropuesta(t: TurnoAsistente, p: TurnoAsistente.Propuesta) {
        val a = Asistente.queHace(p.p)
        val burbuja = p.p.herramienta == "mensajes_crear_borrador" && p.p.args["canal"]?.texto != "email"
        Column(Modifier.fillMaxWidth().tarjeta().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                IconoTono(a.icono, a.tono, tam = 38.dp)
                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Texto(a.titulo, Estilo.filaLista, C.texto)
                    Texto(
                        if (p.estado == TurnoAsistente.EstadoPropuesta.hecha) "Quedó para revisar: todavía no se mandó nada" else "Propuesta: no se crea hasta que la confirmes",
                        Estilo.footnote, C.texto2,
                    )
                }
            }
            p.p.texto?.nonEmpty?.let { x ->
                Texto(x, Estilo.texto14, C.texto, if (burbuja) Modifier.clip(RoundedCornerShape(14.dp)).background(C.okFondo).padding(12.dp) else Modifier)
            }
            if (p.estado == TurnoAsistente.EstadoPropuesta.hecha) {
                val ir = p.ir
                if (ir != null) BotonHundido("Ver para revisar", { estado.abrir(ir) })
            } else if (estado.escribe) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    val confirmando = p.estado == TurnoAsistente.EstadoPropuesta.confirmando
                    BotonAccion(if (confirmando) "Guardando…" else "Confirmar", { alcance.launch { confirmar(t.id, p.id) } }, Modifier.weight(1f), habilitado = !confirmando)
                    BotonTexto("Descartar", { descartar(t.id, p.id) }, Modifier.weight(1f), alto = 44.dp)
                }
            }
        }
    }

    @Composable
    fun RespuestaTurno(t: TurnoAsistente, r: RespuestaAsistente) {
        val o = Asistente.origen(r, hayIA = info?.ia ?: true)
        val pendientes = t.propuestas.count { it.estado == TurnoAsistente.EstadoPropuesta.pendiente }
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (r.texto.nonEmpty != null) {
                SelectionContainer { Texto(r.texto, Estilo.body16, C.texto, Modifier.padding(horizontal = 4.dp)) }
            }
            val rs = r.resultados ?: emptyList()
            if (rs.isNotEmpty()) {
                Column(Modifier.fillMaxWidth().tarjeta(16.dp)) {
                    rs.forEachIndexed { i, x ->
                        // (x y no `it`: adentro de Tocable, `it` es si está apretado.)
                        Tocable(alTocar = { abrir(x) }, modifier = Modifier.lineaArriba(i > 0)) {
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 14.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                    Texto(x.titulo, Estilo.filaLista, C.texto, lineas = 2)
                                    x.subtitulo?.nonEmpty?.let { s -> Texto(s, Estilo.footnote, C.texto2, lineas = 2) }
                                }
                                Chevron()
                            }
                        }
                    }
                }
            }
            t.propuestas.forEach { p ->
                if (p.estado != TurnoAsistente.EstadoPropuesta.descartada) TarjetaPropuesta(t, p)
            }
            if (pendientes > 1 && estado.escribe) {
                BotonHundido("Confirmar las $pendientes", { alcance.launch { confirmarTodas(t.id) } })
            }
            // texto2: el pie dice por qué no contestó la IA, es información (texto3 no llega a AA).
            ConIcono(o.second, o.first, Estilo.footnote, C.texto2, Modifier.padding(horizontal = 4.dp))
            if (r.origen == "sin_ia" && r.resultados.isNullOrEmpty() && t.id == turnos.lastOrNull()?.id) {
                Box(Modifier.padding(top = 4.dp)) { Arranque("PROBÁ CON") }
            }
        }
    }

    @Composable
    fun TurnoVista(t: TurnoAsistente) {
        when (t.rol) {
            TurnoAsistente.Rol.persona -> {
                val oscuro = LocalOscuro.current
                val forma = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomEnd = 6.dp, bottomStart = 18.dp)
                // Contra el borde derecho, con 60 de aire a la izquierda (Spacer(minLength: 60) de iOS): sin pesos, el
                // Box se mide al texto.
                Row(Modifier.fillMaxWidth().padding(start = 60.dp), horizontalArrangement = Arrangement.End) {
                    Box(
                        Modifier.clip(forma).background(if (oscuro) hex(0x1F3B33) else hex(0xDCEBE3))
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                    ) { Texto(t.texto, Estilo.body16, C.texto) }
                }
            }
            TurnoAsistente.Rol.error -> {
                Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ConIcono(t.texto, "exclamationmark.icloud", Estilo.footnote, C.errorTinta)
                    Tocable(alTocar = { alcance.launch { preguntar(t.pregunta) } }, habilitado = !pensando) {
                        Box(Modifier.heightIn(min = 44.dp), contentAlignment = Alignment.CenterStart) {
                            Texto("Probar de nuevo", Estilo.subheadFuerte, C.accion)
                        }
                    }
                }
            }
            TurnoAsistente.Rol.asistente -> t.respuesta?.let { RespuestaTurno(t, it) }
        }
    }

    @Composable
    fun Redactar() {
        Row(
            // Sólo el teclado: la barra de pestañas ya deja el lugar de la navegación (y se esconde con el teclado).
            Modifier.fillMaxWidth().background(C.elevado.copy(alpha = 0.94f)).imePadding().padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom,
        ) {
            val estilo = Estilo.body16
            BasicTextField(
                value = texto, onValueChange = { texto = it }, minLines = 1, maxLines = 6,
                textStyle = estilo.ts().copy(color = C.texto), cursorBrush = SolidColor(C.accion),
                // Mayúscula al empezar la oración, como el TextField de iOS.
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.weight(1f).semantics { contentDescription = "Tu pregunta" },
                decorationBox = { campo ->
                    Box(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(19.dp)).background(C.superficie).border(0.5.dp, C.linea2, RoundedCornerShape(19.dp))
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    ) {
                        if (texto.isEmpty()) Texto("¿Qué contratos ajustan este mes?", estilo, C.texto3, lineas = 1)
                        campo()
                    }
                },
            )
            val habilitado = !pensando && texto.nonEmpty != null
            Tocable(alTocar = { mandar() }, habilitado = habilitado, descripcion = "Preguntar") {
                Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(36.dp).alpha(if (habilitado) 1f else 0.5f).clip(CircleShape).background(C.accion), contentAlignment = Alignment.Center) {
                        Icono("arrow.up", 17f, C.sobreAccion, peso = Peso.bold)
                    }
                }
            }
        }
    }

    // .scrollDismissesKeyboard(.interactively): al mover la charla se baja el teclado.
    val bajarTeclado = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: androidx.compose.ui.geometry.Offset, source: NestedScrollSource): androidx.compose.ui.geometry.Offset {
                if (source == NestedScrollSource.UserInput && available.y > 0f) focos.clearFocus()
                return androidx.compose.ui.geometry.Offset.Zero
            }
        }
    }

    PantallaApilada("Asistente") {
        Column(Modifier.fillMaxSize()) {
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth().nestedScroll(bajarTeclado), state = lista,
                contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item(key = "cabeza") { Cabeza() }
                if (turnos.isEmpty()) item(key = "arranque") { Arranque() }
                items(turnos, key = { it.id }) { t -> TurnoVista(t) }
                if (pensando) item(key = "pensando") {
                    Row(Modifier.padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Rueda()
                        Texto("Buscando en Corredor…", Estilo.footnote, C.texto2)
                    }
                }
            }
            Redactar()
        }
    }
}
