package ar.com.corredor.app.pantallas.inicio

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import ar.com.corredor.app.R
import ar.com.corredor.app.app.*
import ar.com.corredor.app.nucleo.*
import ar.com.corredor.app.pantallas.agenda.*
import ar.com.corredor.app.pantallas.cartera.*
import ar.com.corredor.app.pantallas.cobranzas.*
import ar.com.corredor.app.pantallas.mas.*
import ar.com.corredor.app.pantallas.propiedades.*
import ar.com.corredor.app.tema.*
// Las piezas propias, explícitas: otras áreas tienen las suyas con el mismo nombre.
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

// Clon de Pantallas/Inicio/Inicio.swift.

/** Una fila de «Para hacer ahora»: ícono en su tono, qué es, de dónde viene y la acción. */
data class Pendiente2A(
    val id: String,
    val icono: String,
    val tono: Tono,
    val titulo: String,
    val detalle: String,
    val accion: String,
    val ir: Any?,
    val hacer: (() -> Unit)? = null,
)

object InicioRaiz {
    /** Lo que se puede cargar desde el «+» del Inicio (lo mismo que en cada sección, juntado). */
    enum class Cargar { contacto, visita, operacion, contrato }
}

/** 2A Inicio del agente y 2B de la dueña (handoff · «Hoy»; panel web 01 · 7E, 7C): lo urgente con su
 *  acción ahí mismo, «Tu día», la agenda de hoy, las sugerencias, cómo viene el mes, las cobranzas, el
 *  equipo y «Tu web». La dueña elige «Yo» o «Equipo». De noche mira a mañana. Nunca queda vacío: sin
 *  nada urgente propone el próximo paso, y las acciones rápidas están siempre. */
@Composable
fun InicioRaiz() {
    val estado = LocalEstado.current
    val ctx = LocalContext.current
    val alcance = rememberCoroutineScope()
    var datos by remember { mutableStateOf<DatosInicio?>(null) }
    var error by remember { mutableStateOf<ErrorApi?>(null) }
    var eligiendoQueCargar by remember { mutableStateOf(false) }
    var cargando by remember { mutableStateOf<InicioRaiz.Cargar?>(null) }
    /** «Yo» o «Equipo» (la dueña o el dueño). */
    var vista by remember { mutableStateOf(ajustes(ctx).getString("inicioVista", "equipo") ?: "equipo") }
    // Lo optimista, antes de que vuelva la API: consultas asignadas, sugerencias resueltas, citas
    // tildadas, pasos hechos y los primeros pasos ocultos.
    var asignadas by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var ocultas by remember { mutableStateOf<Set<String>>(emptySet()) }
    var tildadas by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    var marcados by remember { mutableStateOf<Set<String>>(emptySet()) }
    var pasosOcultos by remember { mutableStateOf<Boolean?>(null) }
    /** Lo que entró desde la última vez (se resalta). */
    var nuevas by remember { mutableStateOf<Set<String>>(emptySet()) }

    val yo: String? = estado.usuario?.id
    val noche = CuentasInicio.deNoche()
    val equipoVista = estado.esDuenio && vista == "equipo"
    val nombre = estado.usuario?.nombre?.split(" ")?.firstOrNull { it.isNotEmpty() } ?: ""

    /** La vista «Yo» de la dueña deja sólo lo suyo; el agente ve todo lo que le da la API. */
    fun deQuien(agente: String?): Boolean = equipoVista || !estado.esDuenio || agente == yo

    // MARK: Cuentas sobre los datos

    fun citas(d: DatosInicio): List<Cita> = d.agenda.filter { it.estado != "cancelada" && deQuien(it.agenteId) }.sortedBy { it.empiezaEn }
    fun deHoy(d: DatosInicio): List<Cita> = citas(d).filter { esHoyEnBA(it.empiezaEn) }
    fun tuDia(d: DatosInicio): List<Cita> =
        deHoy(d).filter { it.id != d.proxima?.cita?.id && (noche || it.estado == "pendiente" || tildadas[it.id] != null) }

    fun consultas(d: DatosInicio): List<AvisoConsulta> =
        d.consultas.filter { !estado.esDuenio || equipoVista || it.responsableId == yo }.map { a ->
            val c = a.contactoId
            val n = c?.let { asignadas[it] }
            if (n != null) a.copy(responsableId = "asignada", responsableNombre = n) else a
        }

    fun charlas(d: DatosInicio): List<Conversacion> = d.conversaciones.filter { deQuien(it.agenteId) }

    fun fila(f: FilaCobranza): CobranzaInicio = CobranzaInicio(
        contratoId = f.contratoId, propiedadTitulo = f.propiedadTitulo, contactoNombre = f.contactoNombre, agenteId = f.agenteId,
        monto = f.monto, moneda = f.moneda, vence = f.vence ?: diaISOenBA(), estado = f.estado ?: "pendiente",
    )

    /** Cada alquiler que debe, una vez: desde el mes más viejo, con el total (la regla de la API). */
    fun deben(d: DatosInicio): List<CobranzaInicio>? = d.deudas?.map { x ->
        CobranzaInicio(
            contratoId = x.contratoId, propiedadTitulo = x.propiedadTitulo, contactoNombre = x.contactoNombre, agenteId = x.agenteId,
            monto = x.total ?: 0.0, moneda = x.moneda ?: "ARS", vence = x.meses?.firstOrNull()?.vence ?: diaISOenBA(),
            estado = "atrasado", montoMes = x.meses?.firstOrNull()?.esperado,
        )
    }

    fun urgentes(d: DatosInicio): List<Urgente> {
        val pend = (d.cobranzas?.pendientes ?: emptyList()).map { fila(it) }
        val atrasadas = (deben(d) ?: pend.filter { it.estado == "atrasado" }).filter { deQuien(it.agenteId) }
        val us = atrasadas.map { CuentasInicio.cobroUrgente(it) }.toMutableList()
        us += consultas(d).map { CuentasInicio.consultaUrgente(it) }
        if (!(estado.esDuenio && !equipoVista)) {
            us += d.reservas.map { CuentasInicio.reservaUrgente(it) }
            us += d.preguntas.map { CuentasInicio.preguntaUrgente(it) }
        }
        if (!(estado.esDuenio && equipoVista)) us += charlas(d).map { CuentasInicio.whatsappUrgente(it) }
        return CuentasInicio.ordenar(us)
    }

    fun cobranzasMes(d: DatosInicio): Pair<List<CobranzaInicio>, List<CobranzaInicio>> {
        val c = d.cobranzas ?: return emptyList<CobranzaInicio>() to emptyList()
        val cobradas = c.cobradas.map { fila(it) }.filter { deQuien(it.agenteId) }
        var pendientes = c.pendientes.map { fila(it) }
        val db = deben(d)
        if (db != null) pendientes = pendientes.filter { it.estado != "atrasado" } + db
        return cobradas to pendientes.filter { deQuien(it.agenteId) }
    }

    fun sugerencias(d: DatosInicio): List<SugerenciaInicio> =
        (d.panelExtra?.sugerencias ?: emptyList()).filter { deQuien(it.agenteId) && it.id !in ocultas && it.estado != "descartada" && it.estado != "aplicada" }

    fun ritmos(d: DatosInicio): List<RitmoInicio> = (d.panel?.objetivos ?: emptyList())
        .filter { if (estado.esDuenio && equipoVista) it.agenteId == null else it.agenteId == yo }
        .sortedBy { CuentasInicio.ordenMetrica(it.metrica) }
        .map { CuentasInicio.ritmo(it) }

    fun deLicencia(d: DatosInicio, id: String): Licencia? {
        val hoy = diaISOenBA()
        return d.licencias.firstOrNull { it.usuarioId == id && it.desde <= hoy && it.hasta >= hoy }
    }

    /** A quién se le puede dar una consulta: agentes y dueños que no están de licencia hoy. */
    fun asignables(d: DatosInicio): List<Pair<String, String>> =
        d.equipo.filter { it.rol != "lector" && deLicencia(d, it.usuarioId) == null }.map { it.usuarioId to it.completo }

    /** El equipo hoy: cada persona (menos quien mira y los lectores) con sus citas y lo que tiene sin responder. */
    fun equipoHoy(d: DatosInicio): List<PersonaHoy> {
        val hoy = d.agenda.filter { it.estado == "pendiente" && esHoyEnBA(it.empiezaEn) }
        return d.equipo.filter { it.usuarioId != yo && it.rol != "lector" }.map { m ->
            val suyas = hoy.filter { it.agenteId == m.usuarioId }
            val porTipo = mutableListOf<Pair<String, Int>>()
            for (c in suyas) {
                val t = c.tipo ?: "otro"
                val i = porTipo.indexOfFirst { it.first == t }
                if (i >= 0) porTipo[i] = t to porTipo[i].second + 1 else porTipo.add(t to 1)
            }
            val sin = d.conversaciones.filter { it.agenteId == m.usuarioId }
            val desde = sin.mapNotNull { it.ultimoEntranteEn ?: it.ultimoMensajeEn }.minOrNull()
            val lic = deLicencia(d, m.usuarioId)?.let { (it.tipo ?: "licencia") to CuentasInicio.vuelveDe(it.hasta) }
            PersonaHoy(id = m.usuarioId, nombre = m.completo, foto = m.foto, telefono = m.telefono, citas = porTipo,
                sinResponder = sin.size, desde = desde, licencia = lic)
        }
    }

    fun pasosVisibles(d: DatosInicio): Boolean {
        val g = d.guia ?: return false
        return !(pasosOcultos ?: g.pasosOcultos ?: false) && CuentasInicio.primerosPasos(g, duenio = estado.esDuenio).isNotEmpty()
    }

    fun proximos(d: DatosInicio): List<ProximoInicio> = CuentasInicio.proximosPasos(
        d.guia, duenio = estado.esDuenio, pasosVisibles = pasosVisibles(d),
        libres = consultas(d).count { it.responsableId == null },
    )

    fun acciones(d: DatosInicio): List<AccionInicio> {
        val todas = CuentasInicio.accionesRapidas(d.guia, web = d.inmo?.web != null)
        return if (estado.escribe) todas else todas.filter { it.clave == "web" }
    }

    // MARK: Saludo

    /** «Buen día, Nahuel» (la dueña, o de noche); el agente de día, «Hola, Martín». */
    val saludo = run {
        val s = if (estado.esDuenio || noche) CuentasInicio.saludo() else "Hola"
        if (nombre.isEmpty()) s else "$s, $nombre"
    }

    /** «4 cosas no esperan», «Jueves 12 · 2 visitas y 3 mensajes esperando», «Falta tu primera propiedad»… */
    fun resumen(): String {
        val d = datos ?: return " "
        if (d.sinRed) return "Actualizado a las ${Formato.horaDe(d.actualizado)}"
        if (d.guia?.propiedades == 0) return "Falta tu primera propiedad"
        val us = urgentes(d)
        if (estado.esDuenio) {
            if (us.isEmpty()) return "Hoy no hay nada urgente"
            return if (us.size == 1) "1 cosa no espera" else "${us.size} cosas no esperan"
        }
        if (noche) {
            val c = citas(d).firstOrNull { !esHoyEnBA(it.empiezaEn) }
            if (c != null) return "Mañana arrancás a las ${CuentasInicio.horaCorta(c.empiezaEn)}"
            return "Mañana no tenés nada agendado"
        }
        val visitas = deHoy(d).count { it.tipo == "visita" && it.estado == "pendiente" }
        val esperan = charlas(d).size + consultas(d).size
        return "${CuentasInicio.diaCorto()} · ${Formato.cantidad(visitas, "visita", "visitas")}" +
            (if (esperan > 0) " y ${Formato.cantidad(esperan, "mensaje esperando", "mensajes esperando")}" else "")
    }

    // MARK: Datos

    fun ids(d: DatosInicio): Set<String> = urgentes(d).map { it.id }.toSet()

    suspend fun cargar() {
        try {
            val d = CuentasInicio.traer(estado)
            val antes = datos
            if (antes != null) nuevas = ids(d) - ids(antes)
            datos = d
            asignadas = emptyMap()
            tildadas = emptyMap()
            pasosOcultos = null
            error = null
            estado.avisosPendientes = d.avisos
            ActividadVisita.actualizar(d.proxima?.cita)
            d.panel?.let { Guardado.guardar(it, clave = "panel") }
        } catch (e: ErrorApi) {
            val g = if (datos == null) Guardado.leer<Panel>(clave = "panel") else null
            if (g != null) {
                datos = DatosInicio(panel = g.first, actualizado = g.second, sinRed = e.sinConexion)
            } else if (e.sinConexion) {
                datos = datos?.copy(sinRed = true)
            }
            if (datos == null) error = e
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }

    // MARK: Acciones

    /** «Mirá tu web» queda hecho al abrirla desde el Inicio. */
    fun vioLaWeb() {
        if (datos?.guia?.pasos?.web == true || "web" in marcados) return
        marcados = marcados + "web"
        @kotlinx.serialization.Serializable data class C(val webVista: Boolean = true)
        alcance.launch {
            try { estado.api.hacer("PATCH", "/v1/panel/guia", C()) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { marcados = marcados - "web" }
        }
    }

    fun abrirWeb() {
        vioLaWeb()
        datos?.inmo?.web?.let { abrirUrl(ctx, it) }
    }

    fun hacerAccion(clave: String) {
        when (clave) {
            "captar" -> estado.miraAbierta = true
            "propiedad" -> estado.abrir(AAltaPropiedad())
            "contacto" -> cargando = InicioRaiz.Cargar.contacto
            "visita" -> cargando = InicioRaiz.Cargar.visita
            "cobro" -> estado.abrir(ARegistrarCobro())
            "web" -> abrirWeb()
            else -> {}
        }
    }

    /** «Activá los avisos»: pide el permiso ahí mismo; si estaba negado, lleva a Ajustes. */
    suspend fun activarAvisos() {
        val prefs = ajustes(ctx)
        if (!NotificationManagerCompat.from(ctx).areNotificationsEnabled() && prefs.getBoolean("pidioAvisos", false)) {
            // Ya se negó una vez: Android no lo vuelve a preguntar, se activa en los ajustes del sistema.
            runCatching {
                ctx.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            return
        }
        prefs.edit().putBoolean("pidioAvisos", true).apply()
        estado.pedirPermisoPush()
        if (NotificationManagerCompat.from(ctx).areNotificationsEnabled()) {
            marcados = marcados + "avisos"
            estado.avisar("Listo: te van a llegar los avisos")
        }
    }

    fun hacerPaso(p: PasoInicio) {
        when (p.clave) {
            "propiedad" -> estado.miraAbierta = true
            "web" -> abrirWeb()
            "datos" -> estado.abrir(ASeccion(clave = "web"))
            "whatsapp", "portales" -> estado.abrir(ASeccion(clave = "integraciones"))
            "equipo" -> estado.abrir(ASeccion(clave = "equipo"))
            "avisos" -> alcance.launch { activarAvisos() }
            else -> estado.altaAbierta = PasoAlta(clave = p.clave)
        }
    }

    /** «Ocultar» (o se ocultan solos al completarlos): se guarda en la API, con «Deshacer». */
    fun ocultarPasos(completo: Boolean) {
        @kotlinx.serialization.Serializable data class C(val pasosOcultos: Boolean)
        pasosOcultos = true
        alcance.launch {
            try {
                estado.api.hacer("PATCH", "/v1/panel/guia", C(pasosOcultos = true))
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                pasosOcultos = false
                if (!completo) estado.avisar(e)
                return@launch
            }
            if (completo) return@launch
            estado.avisar("Ocultaste los primeros pasos") {
                pasosOcultos = false
                alcance.launch {
                    try { estado.api.hacer("PATCH", "/v1/panel/guia", C(pasosOcultos = false)) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
                        pasosOcultos = true
                        estado.avisar(e)
                    }
                }
            }
        }
    }

    fun hacerProximo(clave: String) {
        when (clave) {
            "libres" -> estado.abrir(AContactos(agente = "libre"))
            "propiedad" -> estado.miraAbierta = true
            "nueva", "mas" -> estado.abrir(AAltaPropiedad())
            "contacto" -> cargando = InicioRaiz.Cargar.contacto
            "importar" -> estado.abrir(ASeccion(clave = "importar"))
            "alta" -> estado.altaAbierta = PasoAlta(clave = datos?.guia?.alta?.faltan?.firstOrNull())
            "whatsapp", "portales" -> estado.abrir(ASeccion(clave = "integraciones"))
            "equipo" -> estado.abrir(ASeccion(clave = "equipo"))
            "datos" -> estado.abrir(ASeccion(clave = "web"))
            "visita" -> cargando = InicioRaiz.Cargar.visita
            else -> estado.pestana = Pestana.propiedades
        }
    }

    /** «Asignar» (la dueña elige a quién) o «Tomarla» (el agente): al toque, con «Deshacer» que la devuelve. */
    fun asignar(u: Urgente, a: Pair<String, String>?) {
        val contacto = u.consultaContacto ?: return
        val yoId = yo ?: return
        val quien = a ?: (yoId to (estado.usuario?.completo ?: ""))
        val propia = quien.first == yoId
        @kotlinx.serialization.Serializable data class C(val agenteId: String?)
        @kotlinx.serialization.Serializable data class R(val antesId: String? = null)
        asignadas = asignadas + (contacto to (if (propia) "tuya" else quien.second))
        alcance.launch {
            val r: R = try {
                estado.api.enviar<R, C>("POST", "/v1/consultas/$contacto/asignar", C(agenteId = quien.first))
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                asignadas = asignadas - contacto
                estado.avisar(e)
                return@launch
            }
            estado.avisar(if (propia) "La consulta es tuya" else "Le pasaste la consulta a ${quien.second}") {
                asignadas = asignadas - contacto
                alcance.launch {
                    try {
                        estado.api.hacer("POST", "/v1/consultas/$contacto/asignar", C(agenteId = r.antesId))
                        estado.version += 1
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        asignadas = asignadas + (contacto to (if (propia) "tuya" else quien.second))
                        estado.avisar(e)
                    }
                }
            }
        }
    }

    /** Tildar una cita de «Tu día» (la marca hecha; «Deshacer» la vuelve a pendiente). */
    fun tildar(c: Cita, si: Boolean) {
        @kotlinx.serialization.Serializable data class C(val estado: String)
        tildadas = tildadas + (c.id to si)
        alcance.launch {
            try {
                estado.api.hacer("PATCH", "/v1/agenda/${c.id}", C(estado = if (si) "hecha" else "pendiente"))
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                tildadas = tildadas + (c.id to !si)
                estado.avisar(e)
                return@launch
            }
            if (si) estado.avisar("Listo: ${c.titulo ?: c.donde}") { tildar(c, false) }
        }
    }

    /** El botón de una sugerencia: la aplica y lleva a la cosa. */
    fun aplicar(s: SugerenciaInicio) {
        ocultas = ocultas + s.id
        alcance.launch {
            try {
                estado.api.hacer("POST", "/v1/sugerencias/${s.id}/aplicar")
            } catch (e: ErrorApi) {
                // 409: ya la resolvió otra persona: da lo mismo.
                if (e.estado != 409) {
                    ocultas = ocultas - s.id
                    estado.avisar(e)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                ocultas = ocultas - s.id
                estado.avisar(e)
            }
        }
        s.boton.second?.let { estado.abrir(it) }
    }

    /** La cruz: el descarte sale cuando vence el «Deshacer» (6 s), así deshacer no vuelve atrás en la API. */
    fun descartar(s: SugerenciaInicio) {
        ocultas = ocultas + s.id
        val t = alcance.launch {
            delay(6000)
            try {
                estado.api.hacer("POST", "/v1/sugerencias/${s.id}/descartar")
            } catch (e: ErrorApi) {
                if (e.estado != 409) {
                    ocultas = ocultas - s.id
                    estado.avisar(e)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                ocultas = ocultas - s.id
                estado.avisar(e)
            }
        }
        estado.avisar("Descartaste la sugerencia") {
            t.cancel()
            ocultas = ocultas - s.id
        }
    }

    /** «Registrar cobro» (la hoja de Cobranzas con ese contrato, como `/cobranzas?registrar=1&contrato=`). */
    fun cobrar(c: CobranzaInicio) = estado.abrir(ARegistrarCobro(contrato = c.contratoId))

    /** «Recordar»: la ficha del contrato con el recordatorio del mes que se debe (`?recordar=AAAA-MM`). */
    fun recordar(c: CobranzaInicio) = estado.abrir(ARecordarMes(contrato = c.contratoId, periodo = c.vence.take(7)))

    LaunchedEffect(estado.version) { cargar() }
    LaunchedEffect(vista) { nuevas = emptySet() }

    // MARK: Bloques

    @Composable
    fun BloqueUrgentes(d: DatosInicio, us: List<Urgente>) {
        UrgentesInicio(
            urgentes = us, titulo = if (noche) "Para mañana" else "Lo urgente", nuevas = nuevas,
            personas = if (estado.esDuenio) asignables(d) else null, asignar = ::asignar, cobrar = ::cobrar, recordar = ::recordar,
        )
    }

    @Composable
    fun ErrorMensajes() {
        ErrorBloque(
            titulo = "No pudimos traer tus mensajes", texto = "WhatsApp no respondió. Es sólo esta vista.",
            salida = "Abrir Mensajes" to { estado.pestana = Pestana.mensajes },
        ) { alcance.launch { cargar() } }
    }

    @Composable
    fun AgendaBloque(d: DatosInicio) {
        if ("agenda" in d.fallo) {
            ErrorBloque(titulo = "No pudimos traer la agenda", salida = "Abrir Agenda" to { estado.abrir(AAgendaDia()) }) { alcance.launch { cargar() } }
        } else {
            val lista = if (noche) citas(d).filter { !esHoyEnBA(it.empiezaEn) } else deHoy(d)
            if (lista.isNotEmpty()) {
                val titulo = if (noche) (if (equipoVista) "Mañana en la agenda del equipo" else "Mañana en tu agenda")
                else (if (equipoVista) "Hoy en la agenda del equipo" else "Hoy en tu agenda")
                AgendaHoyInicio(titulo = titulo, citas = lista)
            }
        }
    }

    @Composable
    fun PasosBloque(d: DatosInicio) {
        val g = d.guia ?: return
        val pasos = CuentasInicio.primerosPasos(g, duenio = estado.esDuenio).map { it.copy(hecho = it.hecho || it.clave in marcados) }
        if (!(pasosOcultos ?: g.pasosOcultos ?: false) && pasos.isNotEmpty()) {
            PrimerosPasos(pasos = pasos, hacer = ::hacerPaso, ocultar = ::ocultarPasos)
        }
    }

    @Composable
    fun CobranzasBloque(d: DatosInicio) {
        val (cobradas, pendientes) = cobranzasMes(d)
        if (cobradas.isNotEmpty() || pendientes.isNotEmpty()) {
            val n = d.panel?.numeros?.rendicionesListas ?: 0
            CobranzasMesInicio(
                cobradas = cobradas, pendientes = pendientes,
                rendiciones = if (estado.esDuenio) n to d.panelExtra?.numeros?.primeraRendicion else null,
            )
        } else if (estado.esDuenio && "cobranzas" !in d.fallo) {
            // Todavía sin alquileres: que se sepa que la cobranza también se lleva desde acá.
            Tocable(alTocar = { estado.abrir(AContratos()) }) {
                Row(Modifier.fillMaxWidth().tarjeta().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconoTono(icono = "dollarsign.circle", tono = Tono.verde, tam = 44.dp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Texto("Cobrá los alquileres desde acá", Estilo.filaLista, C.texto)
                        Texto(Formato.sinViudas("Cargá un contrato: te avisamos quién debe, mandás el recibo y le rendís al dueño."), Estilo.footnote, C.texto2)
                    }
                    Chevron()
                }
            }
        }
    }

    @Composable
    fun NotaSinRed() {
        Texto(
            Formato.sinViudas("Llamar y WhatsApp siguen andando: abren el teléfono, no necesitan Corredor."),
            Estilo.footnote, C.texto2, Modifier.fillMaxWidth(), alinear = TextAlign.Center,
        )
    }

    // MARK: 2B Dueña

    @Composable
    fun Duena(d: DatosInicio) {
        val us = urgentes(d)
        val sug = sugerencias(d)
        val nadaUrgente = us.isEmpty() && sug.isEmpty() && d.numero?.caido != true
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
            Segmentado(opciones = listOf("yo" to "Yo", "equipo" to "Equipo"), elegido = vista, alElegir = { v ->
                vista = v
                ajustes(ctx).edit().putString("inicioVista", v).apply()
            })
            d.proxima?.let { TarjetaProximaVisita(p = it, noche = noche) }
            if (estado.escribe || d.inmo?.web != null) AccionesInicio(acciones = acciones(d), hacer = ::hacerAccion)
            d.numero?.let { n -> if (n.caido) NumeroCaidoInicio(numero = n) }
            if (us.isNotEmpty()) BloqueUrgentes(d, us)
            if ("mensajes" in d.fallo) ErrorMensajes()
            if (sug.isNotEmpty()) SugerenciasInicio(sugerencias = sug, aplicar = ::aplicar, descartar = ::descartar)
            if (nadaUrgente) ProximoPasoInicio(proximos = proximos(d), noche = noche, hacer = ::hacerProximo)
            AgendaBloque(d)
            d.inmo?.web?.let { w -> CompartirWebInicio(web = w, nombre = d.inmo?.nombre ?: estado.inmobiliaria?.nombre ?: "", alAbrir = ::vioLaWeb) }
            PasosBloque(d)
            if (d.guia?.propiedades != 0 || ritmos(d).isNotEmpty()) ComoVieneInicio(ritmos = ritmos(d), cerradas = d.panel?.numeros?.cerradasMes ?: 0)
            CobranzasBloque(d)
            if (equipoVista && !noche) {
                val personas = equipoHoy(d)
                if (personas.isNotEmpty()) EquipoHoyInicio(personas = personas)
            }
            if (d.props.isNotEmpty()) CarteraVisual(props = d.props, total = d.panel?.numeros?.propiedadesDisponibles ?: 0)
            EsteMes(n = d.panel?.numeros, objetivo = null)
            TodoCorredor()
            if (d.sinRed) NotaSinRed()
        }
    }

    // MARK: 2A Agente

    @Composable
    fun Agente(d: DatosInicio) {
        val us = urgentes(d)
        val dia = tuDia(d)
        val sug = sugerencias(d)
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
            d.proxima?.let { TarjetaProximaVisita(p = it, noche = noche) }
            if (estado.escribe || d.inmo?.web != null) AccionesInicio(acciones = acciones(d), hacer = ::hacerAccion)
            d.numero?.let { n -> if (n.caido) NumeroCaidoInicio(numero = n) }
            if ("mensajes" in d.fallo) ErrorMensajes() else if (us.isNotEmpty()) BloqueUrgentes(d, us)
            if (dia.isNotEmpty()) {
                TuDiaInicio(
                    titulo = if (noche) "Quedó pendiente de hoy" else "Tu día", citas = dia,
                    hechas = dia.filter { tildadas[it.id] ?: (it.estado == "hecha") }.map { it.id }.toSet(), tildar = ::tildar,
                )
            }
            if (d.proxima == null && us.isEmpty() && dia.isEmpty()) ProximoPasoInicio(proximos = proximos(d), noche = noche, hacer = ::hacerProximo)
            PasosBloque(d)
            if (sug.isNotEmpty()) SugerenciasInicio(sugerencias = sug, aplicar = ::aplicar, descartar = ::descartar)
            AgendaBloque(d)
            d.inmo?.web?.let { w -> CompartirWebInicio(web = w, nombre = d.inmo?.nombre ?: estado.inmobiliaria?.nombre ?: "", alAbrir = ::vioLaWeb) }
            if (d.guia?.propiedades != 0 || ritmos(d).isNotEmpty()) ComoVieneInicio(ritmos = ritmos(d), cerradas = d.panel?.numeros?.cerradasMes ?: 0)
            CobranzasBloque(d)
            if (d.props.isNotEmpty()) CarteraVisual(props = d.props, total = d.panel?.numeros?.propiedadesDisponibles ?: 0)
            TodoCorredor()
            if (d.sinRed) NotaSinRed()
        }
    }

    // MARK: Pantalla

    val scroll = rememberScrollState()
    val densidad = LocalDensity.current
    // Cuando la portada sale de la pantalla aparece la franja de la hora, para que lo que sube no se
    // monte sobre la hora y la batería.
    val franja by remember { derivedStateOf { scroll.value > with(densidad) { 200.dp.toPx() } } }
    Box(Modifier.fillMaxSize()) {
        FondoApp()
        Refrescable(alRefrescar = { cargar() }) {
            Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(bottom = 32.dp)) {
                PortadaInicio(inmo = datos?.inmo, saludo = saludo, resumen = resumen(), clima = datos?.clima, noche = noche) { eligiendoQueCargar = true }
                Spacer(Modifier.height(6.dp))
                Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (estado.escribe) VistaConsejo(ConsejoCargar())
                    VistaConsejo(ConsejoMas())
                }
                if (estado.cola.cambios.isNotEmpty()) Box(Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp)) { ColaVisible() }
                Box(Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp)) { AvisoPlan() }
                Box(Modifier.padding(top = 6.dp)) {
                    val d = datos
                    val e = error
                    if (d != null) {
                        val entrada = remember { androidx.compose.animation.core.MutableTransitionState(false).apply { targetState = true } }
                        androidx.compose.animation.AnimatedVisibility(visibleState = entrada, enter = fadeIn(tween(350)) + slideInVertically(tween(350)) { with(densidad) { 16.dp.roundToPx() } }) {
                            if (estado.esDuenio) Duena(d) else Agente(d)
                        }
                    } else if (e != null) {
                        Box(Modifier.padding(top = 40.dp)) { EstadoError(que = "tu día", error = e) { alcance.launch { cargar() } } }
                    } else {
                        Box(Modifier.padding(horizontal = 16.dp)) { EsqueletoFilas(filas = 5) }
                    }
                }
            }
        }
        AnimatedVisibility(franja, enter = fadeIn(tween(150)), exit = fadeOut(tween(150)), modifier = Modifier.align(Alignment.TopCenter)) {
            Box(Modifier.fillMaxWidth().background(C.fondo.copy(alpha = 0.92f)).windowInsetsTopHeight(WindowInsets.statusBars))
        }
    }

    Confirmacion(
        visible = eligiendoQueCargar, titulo = "Cargar", alCerrar = { eligiendoQueCargar = false }, volver = "Cancelar",
        opciones = listOf(
            OpcionConfirmacion("Propiedad") { estado.abrir(AAltaPropiedad()) },
            OpcionConfirmacion("Captar un cartel con MIRA") { estado.miraAbierta = true },
            OpcionConfirmacion("Contacto") { cargando = InicioRaiz.Cargar.contacto },
            OpcionConfirmacion("Visita") { cargando = InicioRaiz.Cargar.visita },
            OpcionConfirmacion("Operación") { cargando = InicioRaiz.Cargar.operacion },
            OpcionConfirmacion("Contrato de alquiler") { cargando = InicioRaiz.Cargar.contrato },
            OpcionConfirmacion("Cobro") { estado.abrir(ARegistrarCobro()) },
        ),
    )
    // El último elegido sigue pintándose mientras la hoja baja.
    var ultimo by remember { mutableStateOf<InicioRaiz.Cargar?>(null) }
    if (cargando != null) ultimo = cargando
    Hoja(visible = cargando != null, alCerrar = { cargando = null }, alturas = listOf(Altura.grande)) {
        when (ultimo) {
            InicioRaiz.Cargar.contacto -> EditarContacto()
            InicioRaiz.Cargar.visita -> NuevaCita(dia = Instant.now())
            InicioRaiz.Cargar.operacion -> NuevaOperacion()
            InicioRaiz.Cargar.contrato -> AltaContrato()
            null -> {}
        }
    }
}

// MARK: - Piezas del Inicio

/** El fondo de las tarjetas verdes destacadas: 0f3530, dos rayados finos, grano y la rr de marca de agua. */
@Composable
fun FondoTarjetaVerde(modifier: Modifier = Modifier) {
    val grano = ImageBitmapGrano()
    Box(modifier.background(C.verde800).clipToBounds()) {
        Canvas(Modifier.matchParentSize()) {
            for ((angulo, paso, alfa) in listOf(Triple(28.0, 36.0, 0.14f), Triple(118.0, 50.0, 0.1f))) {
                val r = Math.toRadians(angulo)
                val largo = size.width + size.height
                var x = -largo.toDouble()
                while (x < largo) {
                    drawLine(
                        hex(0x7FC4AD, alfa),
                        Offset(x.toFloat(), -20f),
                        Offset((x + cos(r) * largo * 2).toFloat(), (-20 + sin(r) * largo * 2).toFloat()),
                        strokeWidth = 2.dp.toPx(),
                    )
                    x += paso * density
                }
            }
            if (grano != null) drawRect(ShaderBrush(ImageShader(grano, TileMode.Repeated, TileMode.Repeated)))
        }
        Image(
            painterResource(R.drawable.simbolo), null,
            Modifier.align(Alignment.BottomEnd).offset(x = 16.dp, y = 30.dp).width(130.dp).alpha(0.1f),
            contentScale = ContentScale.Fit,
            colorFilter = ColorFilter.tint(Color.White),
        )
    }
}

@Composable
private fun ImageBitmapGrano(): androidx.compose.ui.graphics.ImageBitmap? =
    androidx.compose.ui.graphics.ImageBitmap.imageResource(R.drawable.grano_claro)

/** La lista de «Para hacer ahora»: la primera acción en Acción, las demás en hundido. */
@Composable
fun ListaPendientes(filas: List<Pendiente2A>) {
    val estado = LocalEstado.current
    Column(Modifier.fillMaxWidth().tarjeta()) {
        filas.forEachIndexed { i, r ->
            key(r.id) {
                Row(
                    Modifier.fillMaxWidth().lineaArriba(i > 0).clickable { r.ir?.let { estado.abrir(it) } }
                        .heightIn(min = 66.dp).padding(start = 14.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconoTono(icono = r.icono, tono = r.tono)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Texto(r.titulo, Estilo.filaLista, C.texto, lineas = 2)
                        Texto(r.detalle, Estilo.footnote, C.texto2)
                    }
                    Tocable(alTocar = { val h = r.hacer; if (h != null) h() else r.ir?.let { estado.abrir(it) } }) {
                        Box(Modifier.heightIn(min = 44.dp), contentAlignment = Alignment.Center) {
                            Box(Modifier.height(34.dp).clip(CircleShape).background(if (i == 0) C.accion else C.hundida).padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                                Texto(r.accion, Estilo(14f, Peso.semibold), if (i == 0) C.sobreAccion else C.accion)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Una tarjeta con título, cifra a la derecha, barra de 10 y un pie. */
@Composable
fun TarjetaAvance(titulo: String, cifra: String, avance: Double, color: Color = C.accion, degrade: List<Color>? = null, pie: String) {
    /** La barra crece desde cero al aparecer. */
    var visto by remember { mutableStateOf(false) }
    val f by animateFloatAsState(if (visto) min(1.0, max(0.0, avance)).toFloat() else 0f, spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessVeryLow), label = "avance")
    LaunchedEffect(Unit) { delay(150); visto = true }
    Column(Modifier.fillMaxWidth().tarjeta().padding(16.dp).semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // HStack(alignment: .firstTextBaseline)
        Row {
            Texto(titulo, Estilo(17f, Peso.bold), C.texto, Modifier.alignByBaseline())
            Spacer(Modifier.weight(1f))
            Texto(cifra, Estilo.subheadFuerte, C.texto, Modifier.alignByBaseline())
        }
        val pista = C.hundida
        Canvas(Modifier.fillMaxWidth().height(10.dp)) {
            val r = androidx.compose.ui.geometry.CornerRadius(size.height / 2)
            drawRoundRect(pista, cornerRadius = r)
            val ancho = size.width * f
            if (ancho > 0) {
                if (degrade != null) drawRoundRect(Brush.horizontalGradient(degrade), size = androidx.compose.ui.geometry.Size(ancho, size.height), cornerRadius = r)
                else drawRoundRect(color, size = androidx.compose.ui.geometry.Size(ancho, size.height), cornerRadius = r)
            }
        }
        Texto(pie, Estilo.footnote, C.texto2)
    }
}

/** Una cifra de la dueña: etiqueta 12/600 y valor 16/700. */
@Composable
fun Cifra(etiqueta: String, valor: String, modifier: Modifier = Modifier) {
    // Todo el alto de la fila (las vecinas quedan parejas aunque una etiqueta o una cifra ocupe dos renglones) y la cifra
    // abajo, alineada con las de al lado.
    Column(modifier.fillMaxWidth().fillMaxHeight().tarjeta(16.dp).padding(12.dp), verticalArrangement = Arrangement.SpaceBetween) {
        Texto(etiqueta, Estilo(12f, Peso.semibold), C.texto2)
        Texto(valor, Estilo(16f, Peso.bold, -0.01f), C.texto, Modifier.padding(top = 4.dp), lineas = 2, escalaMinima = 0.8f)
    }
}

/** «2 cambios en cola»: lo cargado sin señal que todavía no subió. */
@Composable
fun ColaVisible() {
    val cola = LocalEstado.current.cola
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.avisoFondo).padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Icono(if (cola.subiendo) "arrow.triangle.2.circlepath" else "icloud.slash", 17f, C.avisoTinta)
        Column(Modifier.weight(1f)) {
            Texto(Formato.cantidad(cola.cambios.size, "cambio en cola", "cambios en cola"), Estilo.subheadFuerte, C.texto)
            Texto(cola.cambios.firstOrNull { it.error != null }?.error ?: "Se suben solos cuando vuelva la señal.", Estilo.footnote, C.texto2)
        }
        Texto("Subir", Estilo.subheadFuerte, C.accion, Modifier.clickable { cola.subir() })
    }
}

// MARK: - Propuesta A («Mi inmobiliaria», 5/11): la marca primero

/** La portada del Inicio: la foto de portada de su web (o el color de su marca) de punta a punta, con el
 *  logo, el nombre, los botones, el saludo y el resumen del día encima. */
@Composable
fun PortadaInicio(
    inmo: Inmobiliaria?,
    saludo: String,
    resumen: String,
    /** «BELGRANO 22°» al lado de la fecha (GET /v1/clima) y, de noche, la hora. */
    clima: Clima? = null,
    noche: Boolean = false,
    cargar: () -> Unit,
) {
    val estado = LocalEstado.current
    val oscuro = LocalOscuro.current
    val ahora = Instant.now()
    val fecha = run {
        var t = Formato.mayuscula(Formato.diaCompleto(ahora))
        if (clima != null) t += " · ${clima.zona.uppercase(Formato.argentina)} ${clima.temperatura}°"
        if (noche) t += " · ${Formato.horaDe(ahora)}"
        t
    }
    val fondo = C.fondo
    Box(Modifier.fillMaxWidth().height(320.dp).clipToBounds()) {
        Box(Modifier.matchParentSize()) {
            val p = inmo?.portadaUrl
            if (p != null) {
                Foto(url = p, modifier = Modifier.matchParentSize())
            } else {
                val c = inmo?.colorPrimario ?: C.verde700
                Box(Modifier.matchParentSize().background(Brush.linearGradient(listOf(c.copy(alpha = 0.95f), C.verde800))))
            }
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.25f), Color.Transparent, fondo.copy(alpha = 0.92f), fondo))))
        }
        Column(Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, top = 62.dp, bottom = 14.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                val l = inmo?.logoUrl
                if (l != null) {
                    AsyncImage(
                        model = l, contentDescription = null, contentScale = ContentScale.Fit,
                        modifier = Modifier.size(40.dp).clip(RoundedCornerShape(11.dp)).background(Color.White),
                    )
                } else {
                    Box(Modifier.size(40.dp).clip(RoundedCornerShape(11.dp)).background(hex(0xFFFDF8)), contentAlignment = Alignment.Center) {
                        Texto(Formato.iniciales(inmo?.nombre ?: estado.inmobiliaria?.nombre ?: "C"), Estilo(15f, Peso.bold), C.verde700)
                    }
                }
                Texto(
                    (inmo?.nombre ?: estado.inmobiliaria?.nombre ?: "").uppercase(Formato.argentina), Estilo(13f, Peso.semibold, 0.5f / 13f),
                    Color.White.copy(alpha = 0.9f), Modifier.weight(1f), lineas = 1,
                )
                if (estado.escribe) BotonPortada("plus", "Cargar", hacer = cargar)
                // Claro / oscuro en un toque (también arriba de «Más», con «Automático»).
                BotonPortada(if (oscuro) "sun.max" else "moon", if (oscuro) "Pasar a modo claro" else "Pasar a modo oscuro") {
                    Haptico.seleccion()
                    estado.tema = if (oscuro) "claro" else "oscuro"
                }
                BotonPortada("magnifyingglass", "Buscar") { estado.abrir(ABuscar()) }
                BotonPortada("bell", "Avisos", insignia = estado.avisosPendientes) { estado.abrir(AAvisos()) }
            }
            Spacer(Modifier.weight(1f))
            Texto(fecha, Estilo.subhead, C.texto2)
            Texto(saludo, Estilo(32f, Peso.extrabold, -0.03f), C.texto, Modifier.semantics { heading() }, lineas = 2, escalaMinima = 0.8f)
            Texto(resumen, Estilo.subhead, C.texto2, Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun BotonPortada(icono: String, etiqueta: String, insignia: Int = 0, hacer: () -> Unit) {
    Tocable(alTocar = hacer, descripcion = etiqueta) {
        Box(Modifier.size(40.dp)) {
            Box(Modifier.matchParentSize().clip(CircleShape).background(Color.Black.copy(alpha = 0.35f)), contentAlignment = Alignment.Center) {
                Icono(icono, 18f, C.oro, peso = Peso.semibold)
            }
            if (insignia > 0) Box(Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-4).dp)) { InsigniaRoja(n = insignia) }
        }
    }
}

/** «Tu cartera»: las propiedades recién movidas, con foto, para mandarlas o abrirlas. */
@Composable
fun CarteraVisual(props: List<Propiedad>, total: Int) {
    val estado = LocalEstado.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // HStack(alignment: .firstTextBaseline)
        Row {
            Box(Modifier.weight(1f).alignByBaseline()) { TituloBloque(texto = "Tu cartera") }
            Texto(if (total > 0) "Ver las $total" else "Ver todas", Estilo.footnoteFuerte, C.accion, Modifier.alignByBaseline().clickable { estado.pestana = Pestana.propiedades })
        }
        Row(
            Modifier.sangrar(16.dp).horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top,
        ) {
            props.take(10).forEach { p ->
                key(p.id) {
                    Tocable(alTocar = { estado.abrir(AFichaPropiedad(id = p.id)) }) {
                        Column(Modifier.width(156.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Box(Modifier.size(156.dp, 112.dp).clip(RoundedCornerShape(16.dp))) { Foto(url = p.miniatura, modifier = Modifier.matchParentSize()) }
                            Texto(p.precioTexto, Estilo(15f, Peso.bold), C.texto, Modifier.padding(top = 4.dp))
                            Texto(p.nombre, Estilo.footnote, C.texto2, lineas = 1)
                        }
                    }
                }
            }
        }
    }
}

/** «Este mes»: lo que se movió, con el objetivo si hay uno. */
@Composable
fun EsteMes(n: Panel.Numeros?, objetivo: Objetivo?) {
    val estado = LocalEstado.current
    fun variacion(a: Int?, b: Int?): String {
        if (a == null || b == null || b <= 0) return "cerradas"
        val d = a - b
        return if (d == 0) "cerradas · igual que el mes pasado" else "cerradas · ${if (d > 0) "+" else ""}$d vs. el mes pasado"
    }
    @Composable
    fun CifraMes(v: String, t: String, modifier: Modifier) {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Texto(v, Estilo(24f, Peso.extrabold), C.texto)
            Texto(t, Estilo.footnote, C.texto3, lineas = 2)
        }
    }
    Tocable(alTocar = { estado.abrir(ASeccion(clave = "informe")) }) {
        Column(Modifier.fillMaxWidth().tarjeta().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row {
                Texto("Este mes", Estilo.subheadFuerte, C.texto2)
                Spacer(Modifier.weight(1f))
                Texto("Ver informe", Estilo.footnoteFuerte, C.accion)
            }
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row {
                    CifraMes("${n?.contactosNuevosMes ?: 0}", "consultas nuevas", Modifier.weight(1f))
                    CifraMes("${n?.cerradasMes ?: 0}", variacion(n?.cerradasMes, n?.cerradasMesAnterior), Modifier.weight(1f))
                }
                Row {
                    CifraMes("${n?.operacionesAbiertas ?: 0}", "operaciones abiertas", Modifier.weight(1f))
                    CifraMes("${n?.propiedadesNuevasMes ?: 0}", "propiedades nuevas", Modifier.weight(1f))
                }
            }
            val meta = objetivo?.meta
            if (objetivo != null && meta != null && meta > 0) {
                val av = objetivo.avance ?: 0.0
                Texto("Objetivo: ${Formato.numero(meta.toInt())} · vas ${Formato.numero(av.toInt())} (${(av / meta * 100).roundToInt()} %)", Estilo.footnote, C.texto2)
                val pista = C.pista
                val oro = C.oro
                Canvas(Modifier.fillMaxWidth().height(8.dp)) {
                    val r = androidx.compose.ui.geometry.CornerRadius(size.height / 2)
                    drawRoundRect(pista, cornerRadius = r)
                    drawRoundRect(oro, size = androidx.compose.ui.geometry.Size(size.width * min(1.0, av / meta).toFloat(), size.height), cornerRadius = r)
                }
            }
        }
    }
}

/** «Todo Corredor»: las áreas principales a un toque (el resto, en «Más»). */
@Composable
fun TodoCorredor() {
    val estado = LocalEstado.current
    val areas = buildList<Triple<String, String, Any>> {
        add(Triple("calendar", "Agenda", AAgendaDia()))
        add(Triple("person.2", "Contactos", AContactos()))
        add(Triple("rectangle.split.3x1", "Operaciones", AOperaciones()))
        add(Triple("dollarsign.circle", "Cobranzas", ACobranzas()))
        add(Triple("doc.text", "Contratos", AContratos()))
        if (estado.esDuenio) {
            add(Triple("building.columns", "Consorcios", ASeccion(clave = "consorcios")))
            add(Triple("globe", "Mi web", ASeccion(clave = "web")))
            add(Triple("chart.bar", "Informe", ASeccion(clave = "informe")))
        } else {
            add(Triple("checklist", "Actas", ASeccion(clave = "inventarios")))
            add(Triple("flag", "Objetivos", ASeccion(clave = "objetivos")))
            add(Triple("square.grid.2x2", "Más", ASeccion(clave = "mas")))
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        TituloBloque(texto = "Todo Corredor")
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            areas.chunked(4).forEach { fila ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    fila.forEach { (icono, nombre, destino) ->
                        key(nombre) {
                            Tocable(alTocar = { estado.abrir(destino) }, modifier = Modifier.weight(1f)) {
                                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Box(Modifier.size(58.dp).sombraE1().clip(RoundedCornerShape(17.dp)).background(C.superficie), contentAlignment = Alignment.Center) {
                                        Icono(icono, 21f, C.accion, peso = Peso.medium)
                                    }
                                    Texto(nombre, Estilo(12f, Peso.semibold), C.texto2, lineas = 1, escalaMinima = 0.8f)
                                }
                            }
                        }
                    }
                    repeat(4 - fila.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** Dónde está la portada del Inicio (para mostrar la franja de la hora cuando ya pasó). En Compose lo dice el scroll
 *  de InicioRaiz; queda el nombre para el par con iOS. */
object AlturaPortada {
    const val defaultValue = 0f
}
