package ar.com.corredor.app.pantallas.agenda

import ar.com.corredor.app.app.PantallaApilada
import ar.com.corredor.app.nucleo.Fecha

import android.content.Intent
import android.provider.CalendarContract
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import ar.com.corredor.app.app.BarraDe
import ar.com.corredor.app.app.BotonBarraIcono
import ar.com.corredor.app.app.LocalEstado
import ar.com.corredor.app.nucleo.AChat
import ar.com.corredor.app.nucleo.ACita
import ar.com.corredor.app.nucleo.AFichaContacto
import ar.com.corredor.app.nucleo.AFichaContrato
import ar.com.corredor.app.nucleo.AFichaOperacion
import ar.com.corredor.app.nucleo.AFichaPropiedad
import ar.com.corredor.app.nucleo.Cita
import ar.com.corredor.app.nucleo.ErrorApi
import ar.com.corredor.app.nucleo.FechaApi
import ar.com.corredor.app.nucleo.Formato
import ar.com.corredor.app.nucleo.JSONLibre
import ar.com.corredor.app.nucleo.Licencia
import ar.com.corredor.app.nucleo.Resultado
import ar.com.corredor.app.nucleo.nonEmpty
import ar.com.corredor.app.nucleo.opcional
import ar.com.corredor.app.pantallas.cobranzas.ARecordarMes
import ar.com.corredor.app.pantallas.informes.GraficoVisitasSemanas
import ar.com.corredor.app.pantallas.mensajes.Charla
import ar.com.corredor.app.pantallas.mensajes.ElegirPropiedad
import ar.com.corredor.app.pantallas.propiedades.Propiedad
import ar.com.corredor.app.tema.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import kotlin.math.abs

// Clon de Pantallas/Agenda/Agenda.swift.
// Los destinos de la Agenda (`destinosAgenda()` en iOS) los registra app/Registro.kt:
//   AAgendaDia → AgendaRaiz(diaInicial = d.dia?.let { Fecha.dia(it) }, apilada = true)
//   ACita → FichaCita(id = c.id)

/** Lo que va como franja arriba del día (y punto en la tira): vencimientos y ajustes de contratos, cobros vencidos y
 *  licencias del equipo. */
data class FranjaAgenda(
    val id: String,
    val dia: String,
    val texto: String,
    val largo: String? = null,
    val tono: Tono,
    val icono: String,
    val contrato: String? = null,
    val licencia: Boolean = false,
    val accion: String = "Ver",
    /** El mes (AAAA-MM) del cobro vencido: «Recordar» abre ese recordatorio. */
    val recordar: String? = null,
)

/** Un vencimiento o ajuste de un contrato (GET /v1/contratos/vencimientos). */
@Serializable
data class VencimientoContrato(val contratoId: String, val tipo: String, val fecha: String, val contrato: JSONLibre? = null)

/** Un mes de alquiler vencido y sin cobrar (GET /v1/contactos/deudas). */
@Serializable
data class DeudaContacto(val contratoId: String, val contactoId: String, val contactoNombre: String, val periodo: String, val vence: String)

/** Lo que en iOS son los `static func` de `AgendaRaiz` (los usan la Agenda, el alta rápida y la ficha de la cita). */
object AgendaRaizTextos {
    fun lunes(de: LocalDate): LocalDate = de.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    fun esTarea(c: Cita): Boolean = (c.tipo ?: "") !in listOf("visita", "firma", "tasacion")

    /** «en 2 h», «en 40 min». */
    fun falta(f: Instant): String {
        val s = ChronoUnit.SECONDS.between(Instant.now(), f).toInt()
        return if (s < 3600) "en ${maxOf(1, s / 60)}${Formato.fino}min" else "en ${s / 3600}${Formato.fino}h"
    }

    @Composable
    fun tono(c: Cita): Color = when (c.tipo) {
        "visita" -> C.oroTinta
        "tasacion" -> C.violetaTinta
        "firma" -> C.okTinta
        "llamado" -> C.azulTinta
        else -> C.texto2
    }

    fun letra(d: LocalDate): String = listOf("Do", "Lu", "Ma", "Mi", "Ju", "Vi", "Sá")[d.dayOfWeek.value % 7]

    fun nombreDia(d: LocalDate): String = listOf("domingo", "lunes", "martes", "miércoles", "jueves", "viernes", "sábado")[d.dayOfWeek.value % 7]

    fun nombreDia(f: Instant): String = nombreDia(Formato.diaDe(f))

    /** «Hoy, jueves 12», «Mañana, viernes 13», «Ayer, miércoles 11» o «sábado 14». */
    fun tituloDia(d: LocalDate): String {
        val hoy = Formato.hoy()
        val base = "${nombreDia(d)} ${d.dayOfMonth}"
        if (d == hoy) return "Hoy, $base"
        if (d == hoy.plusDays(1)) return "Mañana, $base"
        if (d == hoy.minusDays(1)) return "Ayer, $base"
        return base
    }
}

@Serializable
private data class CuerpoConversacion(val contactoId: String)

@Serializable
private data class ConvAgenda(val id: String)

@Serializable
private data class CuerpoEstadoCita(val estado: String)

/** 7A–7B Agenda, como la del panel: el día (con las franjas de vencimientos, cobros y licencias, las tareas con tilde y
 *  las visitas con «Ir» y el chat), la semana o el mes; mía o del equipo; y el alta rápida escrita como se dice. */
@Composable
fun AgendaRaiz(diaInicial: Instant? = null, apilada: Boolean = false) {
    val estado = LocalEstado.current
    val alcance = rememberCoroutineScope()
    var vista by remember { mutableStateOf("dia") }
    var de by remember { mutableStateOf("equipo") }
    var dia by remember { mutableStateOf(diaInicial?.let { Formato.diaDe(it) } ?: Formato.hoy()) }
    var citas by remember { mutableStateOf<List<Cita>?>(null) }
    var franjas by remember { mutableStateOf<List<FranjaAgenda>>(emptyList()) }
    var error by remember { mutableStateOf<ErrorApi?>(null) }
    var nueva by remember { mutableStateOf(false) }
    var licencias by remember { mutableStateOf(false) }

    val lunes = AgendaRaizTextos.lunes(dia)
    val hoy = Formato.hoy()

    /** El período a la vista: la semana, o el mes con sus semanas de borde. */
    val periodo: Pair<LocalDate, LocalDate> = if (vista == "mes") {
        val primero = dia.withDayOfMonth(1)
        val ultimo = primero.plusMonths(1).minusDays(1)
        AgendaRaizTextos.lunes(primero) to AgendaRaizTextos.lunes(ultimo).plusDays(6)
    } else lunes to lunes.plusDays(6)

    fun mover(n: Int) {
        dia = when (vista) {
            "mes" -> dia.plusMonths(n.toLong())
            "semana" -> dia.plusDays(7L * n)
            else -> dia.plusDays(n.toLong())
        }
    }

    fun franjasDe(d: LocalDate): List<FranjaAgenda> {
        val k = d.toString()
        return franjas.filter { it.dia == k }
    }

    fun diaDe(c: Cita): LocalDate = Formato.diaDe(c.empiezaEn)

    fun quien(c: Cita): String =
        if (c.agenteId != null && c.agenteId == estado.usuario?.id) "Vos" else (c.agenteNombre?.let { Charla.primerNombre(it) } ?: "Sin agente")

    /** Tocar un evento de la semana lleva al contacto o a la operación (o al día). */
    fun abrirEvento(c: Cita) {
        val x = c.contactoId
        val o = c.operacionId
        if (x != null) estado.abrir(AFichaContacto(x))
        else if (o != null) estado.abrir(AFichaOperacion(o))
        else { dia = diaDe(c); vista = "dia" }
    }

    suspend fun abrirChat(contacto: String) {
        try {
            val x: ConvAgenda = estado.api.enviar("POST", "/v1/whatsapp/conversaciones", CuerpoConversacion(contacto))
            estado.abrir(AChat(x.id))
        } catch (e: CancellationException) { throw e } catch (e: Exception) { estado.avisar(e) }
    }

    suspend fun tildar(c: Cita) {
        val nuevo = if (c.estado == "hecha") "pendiente" else "hecha"
        citas = citas?.map { if (it.id == c.id) it.copy(estado = nuevo) else it }
        try {
            estado.cola.enviar("PATCH", "/v1/agenda/${c.id}", CuerpoEstadoCita(nuevo), etiqueta = "Tarea ${if (nuevo == "hecha") "hecha" else "pendiente"}")
            if (nuevo == "hecha") {
                Haptico.exito()
                estado.avisar("Hecho · ${c.titulo ?: ""}") {
                    estado.tarea {
                        try {
                            estado.api.hacer("PATCH", "/v1/agenda/${c.id}", CuerpoEstadoCita("pendiente"))
                            estado.version += 1
                        } catch (e: CancellationException) { throw e } catch (e: Exception) { estado.avisar(e) }
                    }
                }
            }
            estado.version += 1
        } catch (e: CancellationException) { throw e } catch (e: Exception) {
            citas = citas?.map { if (it.id == c.id) it.copy(estado = c.estado) else it }
            estado.avisar(e)
        }
    }

    /** Vencimientos y ajustes de contratos, cobros vencidos y licencias del equipo. */
    suspend fun cargarFranjas(desde: String, hasta: String) {
        val f = mutableListOf<FranjaAgenda>()
        val vencs = estado.api.opcional<List<VencimientoContrato>>("/v1/contratos/vencimientos", listOf("dias" to "120")) ?: emptyList()
        for (v in vencs) {
            if (v.fecha < desde || v.fecha > hasta) continue
            val calle = v.contrato?.get("propiedadTitulo")?.texto?.let { " " + (it.split(",").firstOrNull() ?: "") } ?: ""
            val vence = v.tipo == "vencimiento"
            f.add(FranjaAgenda(v.contratoId + v.tipo + v.fecha, v.fecha, if (vence) "Vence alquiler$calle" else "Ajuste$calle",
                tono = if (vence) Tono.rojo else Tono.azul, icono = if (vence) "calendar.badge.exclamationmark" else "chart.line.uptrend.xyaxis", contrato = v.contratoId))
        }
        val deudas = estado.api.opcional<List<DeudaContacto>>("/v1/contactos/deudas", listOf("desde" to desde, "hasta" to hasta)) ?: emptyList()
        for (d in deudas) {
            val apellido = d.contactoNombre.split(" ").lastOrNull { it.isNotEmpty() } ?: d.contactoNombre
            f.add(FranjaAgenda("cobro" + d.contratoId + d.periodo, d.vence, "Cobro $apellido vencido", largo = "Cobro de ${d.contactoNombre} vencido",
                tono = Tono.rojo, icono = "dollarsign.circle", contrato = d.contratoId, accion = "Recordar", recordar = d.periodo.take(7)))
        }
        val hoyT = Formato.hoy().toString()
        val hasta60 = Formato.hoy().plusDays(60).toString()
        val lics = estado.api.opcional<List<Licencia>>("/v1/licencias", listOf("desde" to minOf(desde, hoyT), "hasta" to maxOf(hasta, hasta60))) ?: emptyList()
        val ausente = mapOf("licencia" to "de licencia", "vacaciones" to "de vacaciones", "enfermedad" to "de licencia", "tramite" to "de trámite")
        for (l in lics) {
            var d = maxOf(l.desde, desde)
            var vueltas = 0
            while (d <= l.hasta && d <= hasta && vueltas < 400) {
                val q = if (l.usuarioId == estado.usuario?.id) "Vos" else Charla.primerNombre(l.usuarioNombre)
                f.add(FranjaAgenda("licencia" + l.id + d, d, "$q ${ausente[l.tipo ?: ""] ?: "de licencia"}", tono = Tono.neutro, icono = "beach.umbrella", licencia = true))
                d = Entender.sumar(d, 1)
                vueltas += 1
            }
        }
        franjas = f
    }

    suspend fun cargar() {
        val (desde, hasta) = periodo
        val d = desde.toString()
        val h = hasta.toString()
        try {
            val q = mutableListOf("desde" to d, "hasta" to h, "limite" to "500")
            if (de == "mia") q.add("agente" to "yo")
            citas = estado.api.get("/v1/agenda", q)
            error = null
        } catch (e: CancellationException) { throw e } catch (e: Exception) {
            if (e is ErrorApi && citas == null) error = e
        }
        cargarFranjas(d, h)
    }

    LaunchedEffect(estado.version, periodo.first, vista, de) { cargar() }

    val acciones = buildList {
        add(AccionCabecera("chevron.left", "Anterior") { mover(-1) })
        add(AccionCabecera("chevron.right", "Siguiente") { mover(1) })
        if (dia != hoy) add(AccionCabecera("calendar.badge.clock", "Hoy") { dia = Formato.hoy() })
        add(AccionCabecera("beach.umbrella", "Licencias y ausencias") { licencias = true })
    }

    @Composable
    fun Franja(f: FranjaAgenda) {
        Tocable({
            val c = f.contrato
            val mes = f.recordar
            if (f.licencia) licencias = true else if (c != null && mes != null) estado.abrir(ARecordarMes(c, mes)) else if (c != null) estado.abrir(AFichaContrato(c))
        }) {
            Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(RoundedCornerShape(12.dp)).background(f.tono.fondo).padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icono(f.icono, 16f, f.tono.tinta, peso = Peso.semibold)
                Texto(f.largo ?: f.texto, Estilo.subheadFuerte, f.tono.tinta, Modifier.weight(1f))
                Texto(f.accion, Estilo(13f, Peso.bold), f.tono.tinta)
            }
        }
    }

    @Composable
    fun LineaAhora() {
        Row(Modifier.fillMaxWidth().semantics { contentDescription = "Ahora" }, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Texto(Formato.horaDe(Instant.now()), Estilo(12f, Peso.bold), hex(0xE5484D))
            Box(Modifier.weight(1f).height(2.dp).clip(CircleShape).background(hex(0xE5484D)))
        }
    }

    /** Una tarea: el tilde se marca y desmarca desde la lista, con Deshacer. */
    @Composable
    fun FilaTarea(c: Cita) {
        val hecha = c.estado == "hecha"
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Texto(Formato.horaDe(c.empiezaEn), Estilo(15f, Peso.bold), C.texto, Modifier.width(44.dp))
            Row(Modifier.weight(1f).sombraE2(RoundedCornerShape(16.dp)).clip(RoundedCornerShape(16.dp)).background(C.superficie).padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.clickable(enabled = estado.escribe) { alcance.launch { tildar(c) } }.semantics { contentDescription = "Hecho: ${c.titulo ?: ""}"; selected = hecha }) {
                    Icono(if (hecha) "checkmark.square.fill" else "square", 24f, if (hecha) hex(0x2F9E6F) else C.texto3)
                }
                Tocable({ estado.abrir(ACita(c.id)) }, Modifier.weight(1f)) {
                    val t = (c.titulo ?: c.tipoTexto) + (if (c.agenteId != estado.usuario?.id && c.agenteNombre != null) " · ${quien(c)}" else "")
                    androidx.compose.foundation.text.BasicText(t, Modifier.fillMaxWidth(),
                        style = Estilo.body16.ts().copy(color = if (hecha) C.texto3 else C.texto, textDecoration = if (hecha) TextDecoration.LineThrough else null))
                }
            }
        }
    }

    /** Una visita, firma o tasación: con propiedad, quién, «confirmada» o «en 2 h»; si está por empezar (menos de 3 h),
     *  «Ir» y el chat de WhatsApp dentro de Corredor. */
    @Composable
    fun Bloque_(c: Cita) {
        val tono = AgendaRaizTextos.tono(c)
        val falta = ChronoUnit.SECONDS.between(Instant.now(), c.empiezaEn)
        val prox = falta > 0 && falta < 3 * 3600
        val nombre = mapOf("visita" to "Visita", "firma" to "Firma", "tasacion" to "Tasación")[c.tipo ?: ""] ?: ""
        val titulo = c.propiedadTitulo?.let { "$nombre $it" } ?: (c.titulo ?: c.tipoTexto)
        val q = quien(c)
        val detalle = listOfNotNull(c.contactoNombre, if (q != "Vos") q else null,
            if (prox) AgendaRaizTextos.falta(c.empiezaEn) else (if (c.estado == "hecha") "hecha" else (if (c.recordar == true) "confirmada" else null))).joinToString(" · ")
        Row(Modifier.alpha(if (c.estado == "hecha") 0.7f else 1f), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.width(44.dp).padding(top = 12.dp)) {
                Texto(Formato.horaDe(c.empiezaEn), Estilo(15f, Peso.bold), C.texto)
                Texto(Formato.horaDe(c.terminaEn), Estilo(12f, Peso.regular), C.texto3)
            }
            Column(
                Modifier.weight(1f).sombraE2(RoundedCornerShape(16.dp)).clip(RoundedCornerShape(16.dp)).background(C.superficie)
                    .drawBehind { drawRect(tono, size = androidx.compose.ui.geometry.Size(4.dp.toPx(), size.height)) }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Tocable({ estado.abrir(ACita(c.id)) }) {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icono(c.icono, 14f, tono)
                            Texto(titulo, Estilo(14f, Peso.bold), tono)
                        }
                        if (detalle.isNotEmpty()) Texto(detalle, Estilo.texto14, C.texto2)
                    }
                }
                if (prox) {
                    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.weight(1f).heightIn(min = 44.dp).clip(RoundedCornerShape(12.dp)).background(C.accion).clickable { Afuera.mapas(c.lugar ?: c.propiedadTitulo ?: "") },
                            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                            Icono("location.fill", 14f, C.sobreAccion)
                            Texto("Ir", Estilo(14f, Peso.semibold), C.sobreAccion)
                        }
                        c.contactoId?.let { x ->
                            Box(Modifier.size(48.dp, 44.dp).clip(RoundedCornerShape(12.dp)).background(C.hundida).clickable { alcance.launch { abrirChat(x) } }
                                .semantics { contentDescription = "WhatsApp a ${c.contactoNombre ?: ""}" }, contentAlignment = Alignment.Center) {
                                Icono("bubble.left.fill", 17f, C.accion)
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun FilaCita(c: Cita) { if (AgendaRaizTextos.esTarea(c)) FilaTarea(c) else Bloque_(c) }

    @Composable
    fun Semana() {
        var arrastre by remember { mutableFloatStateOf(0f) }
        Row(Modifier.fillMaxWidth().pointerInput(Unit) {
            detectHorizontalDragGestures(onDragEnd = {
                val dias = if (arrastre < -40) 7 else if (arrastre > 40) -7 else 0
                if (dias != 0) dia = dia.plusDays(dias.toLong())
                arrastre = 0f
            }) { _, d -> arrastre += d / density }
        }) {
            for (i in 0 until 7) {
                val d = lunes.plusDays(i.toLong())
                val elegido = d == dia
                val n = (citas ?: emptyList()).count { diaDe(it) == d && it.estado != "cancelada" }
                val vence = franjasDe(d).any { !it.licencia }
                Tocable({ dia = d }, Modifier.weight(1f).semantics(mergeDescendants = true) {
                    contentDescription = "${Formato.diaCompleto(Formato.instante(d, 12))}, ${Formato.cantidad(n, "cita", "citas")}${if (vence) ", con vencimientos" else ""}"
                }) {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Texto(AgendaRaizTextos.letra(d), Estilo(12f, Peso.semibold), C.texto3)
                        Box(Modifier.size(38.dp).clip(CircleShape).background(if (elegido) C.accion else Color.Transparent), contentAlignment = Alignment.Center) {
                            Texto("${d.dayOfMonth}", Estilo(17f, Peso.semibold), if (elegido) C.sobreAccion else (if (d == hoy) C.accion else C.texto))
                        }
                        Row(Modifier.height(5.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            repeat(minOf(n, 3)) { Box(Modifier.size(5.dp).clip(CircleShape).background(if (elegido) C.accion else C.texto3)) }
                            if (vence) Box(Modifier.size(5.dp).clip(CircleShape).background(C.error))
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun VistaDia(l: List<Cita>) {
        val delDia = l.filter { diaDe(it) == dia }.sortedBy { it.empiezaEn }
        var arrastre by remember { mutableFloatStateOf(0f) }
        val ahora = Instant.now()
        Column(Modifier.fillMaxWidth().pointerInput(Unit) {
            detectHorizontalDragGestures(onDragEnd = {
                if (abs(arrastre) > 70) mover(if (arrastre < 0) 1 else -1)
                arrastre = 0f
            }) { _, d -> arrastre += d / density }
        }, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            franjasDe(dia).forEach { key(it.id) { Franja(it) } }
            if (delDia.isEmpty()) {
                Box(Modifier.padding(top = 20.dp)) {
                    EstadoVacio("calendar.badge.checkmark", if (dia == hoy) "Hoy no hay nada agendado" else "Nada agendado",
                        "Escribí «visita Mendoza 2350 con Valeria a las 17:30».", accion = if (estado.escribe) ("Agendar" to { nueva = true }) else null)
                }
            }
            val proxima = delDia.firstOrNull { it.empiezaEn > ahora }
            delDia.forEach { c ->
                key(c.id) {
                    if (dia == hoy && c == proxima) LineaAhora()
                    FilaCita(c)
                }
            }
            if (dia == hoy && delDia.isNotEmpty() && delDia.all { it.empiezaEn <= ahora }) LineaAhora()
        }
    }

    @Composable
    fun VistaSemana(l: List<Cita>) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            for (i in 0 until 7) {
                val d = lunes.plusDays(i.toLong())
                val delDia = l.filter { diaDe(it) == d }.sortedBy { it.empiezaEn }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Texto(Formato.mayuscula(Formato.diaCompleto(Formato.instante(d, 12))), Estilo.label, if (d == hoy) C.accion else C.texto2,
                        Modifier.clickable { dia = d; vista = "dia" }.padding(horizontal = 4.dp))
                    franjasDe(d).forEach { key(it.id) { Franja(it) } }
                    Column(Modifier.fillMaxWidth().tarjeta(16.dp)) {
                        if (delDia.isEmpty()) {
                            Box(Modifier.fillMaxWidth().heightIn(min = 46.dp).padding(horizontal = 14.dp), contentAlignment = Alignment.CenterStart) {
                                Texto("Libre", Estilo.texto14, C.texto3)
                            }
                        }
                        delDia.forEach { c ->
                            key(c.id) {
                                val linea = C.linea
                                val tono = AgendaRaizTextos.tono(c)
                                MenuContextual(listOf(OpcionDeMenu("Ver la cita", "calendar") { estado.abrir(ACita(c.id)) }), alTocar = { abrirEvento(c) }) {
                                    Row(Modifier.fillMaxWidth().heightIn(min = 46.dp)
                                        .drawBehind { drawRect(linea, topLeft = androidx.compose.ui.geometry.Offset(0f, size.height - 0.5.dp.toPx()), size = androidx.compose.ui.geometry.Size(size.width, 0.5.dp.toPx())) }
                                        .padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Box(Modifier.size(8.dp).clip(CircleShape).background(tono))
                                        Texto(Formato.horaDe(c.empiezaEn), Estilo(14f, Peso.bold), C.texto, Modifier.width(44.dp))
                                        androidx.compose.foundation.text.BasicText("${c.titulo ?: c.tipoTexto} · ${c.contactoNombre ?: quien(c)}", Modifier.weight(1f), maxLines = 1,
                                            style = Estilo.texto14.ts().copy(color = C.texto, textDecoration = if (c.estado == "hecha") TextDecoration.LineThrough else null))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /** El mes: las semanas que lo cubren, con hasta 3 citas por día, «y N más» y el primer vencimiento. */
    @Composable
    fun VistaMes(l: List<Cita>) {
        val p = periodo
        val total = maxOf(0, ChronoUnit.DAYS.between(p.first, p.second).toInt())
        val semanas = (0..total step 7).map { s -> (0 until 7).map { p.first.plusDays((s + it).toLong()) } }
        Column(Modifier.fillMaxWidth().tarjeta(16.dp)) {
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                listOf("L", "M", "M", "J", "V", "S", "D").forEach { x -> Texto(x, Estilo(12f, Peso.bold), C.texto3, Modifier.weight(1f), alinear = androidx.compose.ui.text.style.TextAlign.Center) }
            }
            semanas.forEach { sem ->
                val linea = C.linea
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).drawBehind { drawRect(linea, size = androidx.compose.ui.geometry.Size(size.width, 0.5.dp.toPx())) }, verticalAlignment = Alignment.Top) {
                    sem.forEach { d ->
                        val cs = l.filter { diaDe(it) == d }.sortedBy { it.empiezaEn }
                        val fuera = d.month != dia.month || d.year != dia.year
                        val v = franjasDe(d).firstOrNull { !it.licencia }
                        Tocable({ dia = d; vista = "dia" }, Modifier.weight(1f).semantics(mergeDescendants = true) {
                            contentDescription = "${Formato.diaCompleto(Formato.instante(d, 12))}, ${Formato.cantidad(cs.size, "cita", "citas")}"
                        }) {
                            Column(Modifier.fillMaxWidth().heightIn(min = 78.dp).alpha(if (fuera) 0.55f else 1f).padding(3.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Box(Modifier.size(22.dp).clip(CircleShape).background(if (d == hoy) C.accion else Color.Transparent), contentAlignment = Alignment.Center) {
                                    Texto("${d.dayOfMonth}", Estilo(13f, Peso.bold), if (d == hoy) C.sobreAccion else (if (fuera) C.texto3 else C.texto))
                                }
                                v?.let { Texto(it.texto, Estilo(9f, Peso.bold), it.tono.tinta, Modifier.clip(RoundedCornerShape(3.dp)).background(it.tono.fondo).padding(horizontal = 3.dp), lineas = 1) }
                                cs.take(3).forEach { c ->
                                    Texto("${Formato.horaDe(c.empiezaEn)} ${c.propiedadTitulo?.let { it.split(",").firstOrNull() ?: "" } ?: (c.titulo ?: "")}", Estilo(9f, Peso.semibold), AgendaRaizTextos.tono(c), lineas = 1)
                                }
                                if (cs.size > 3) Texto("y ${cs.size - 3} más", Estilo(9f, Peso.regular), C.texto3)
                            }
                        }
                    }
                }
            }
        }
    }

    if (apilada) BarraDe("") else BarraDe(oculta = true)
    Box(Modifier.fillMaxSize()) {
        FondoApp(Modifier.matchParentSize())
        Refrescable({ cargar() }, Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 80.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                CabeceraPestana("Agenda", sobre = AgendaRaizTextos.tituloDia(dia), acciones = acciones)
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Segmentado(listOf("dia" to "Día", "semana" to "Semana", "mes" to "Mes"), vista, { vista = it })
                    Segmentado(listOf("mia" to "Mía", "equipo" to "Equipo"), de, { de = it })
                    if (vista != "mes") Semana()
                    val cs = citas
                    val e = error
                    if (cs != null) {
                        val visibles = cs.filter { it.estado != "cancelada" }
                        when (vista) {
                            "mes" -> VistaMes(visibles)
                            "semana" -> { VistaSemana(visibles); GraficoVisitasSemanas() }
                            else -> VistaDia(visibles)
                        }
                    } else if (e != null) {
                        EstadoError("la agenda", e) { alcance.launch { cargar() } }
                    } else EsqueletoFilas(filas = 4)
                }
            }
        }
        if (estado.escribe) {
            Box(
                Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 20.dp, bottom = 20.dp).size(58.dp).sombraE3(CircleShape).clip(CircleShape).background(C.accion)
                    .clickable { nueva = true }.semantics { contentDescription = "Agendar" },
                contentAlignment = Alignment.Center,
            ) { Icono("plus", 26f, C.sobreAccion, peso = Peso.semibold) }
        }
    }
    Hoja(nueva, { nueva = false }) { NuevaCita(dia = Formato.instante(dia, 12)) }
    Hoja(licencias, { licencias = false }) { LicenciasAgenda() }
}

@Serializable
private data class CuerpoCita(
    val tipo: String,
    val estado: String = "pendiente",
    val titulo: String,
    val empiezaEn: FechaApi,
    val duracionMin: Int,
    val propiedadId: String?,
    val contactoId: String?,
    val lugar: String?,
    val notas: String?,
    val recordar: Boolean,
    val agenteId: String?,
    val operacionId: String?,
)

/** 7C Nueva visita o tarea: propiedad, con quién, día, la rueda de la hora y «Avisarle por WhatsApp». Sin `editar`, el
 *  alta rápida escrita como se dice; «Elegir campo por campo» pasa al formulario. */
@Composable
fun NuevaCita(
    propiedad: Propiedad? = null,
    contacto: Pair<String, String>? = null,
    dia: Instant = Instant.now(),
    editar: Cita? = null,
    /** La operación a la que queda vinculada (desde la ficha de la operación). */
    operacion: String? = null,
    /** La hora ya elegida («HH:mm»), si viene de otro lado. */
    hora: String? = null,
    /** Visita, tarea o recordatorio, para el alta rápida. */
    clase: String = "visita",
) {
    var formulario by remember { mutableStateOf(false) }
    if (editar == null && !formulario) {
        AltaRapidaCita(propiedad, contacto?.first, operacion, Formato.diaDe(dia).toString(), hora, clase) { formulario = true }
    } else {
        FormularioCita(propiedad, contacto, dia, editar, operacion, hora, clase)
    }
}

@Composable
private fun FormularioCita(propiedad: Propiedad?, contacto: Pair<String, String>?, dia: Instant, editar: Cita?, operacion: String?, hora: String?, clase: String) {
    val estado = LocalEstado.current
    val ctx = LocalContext.current
    val cerrar = LocalCerrarHoja.current
    val alcance = rememberCoroutineScope()

    fun cuandoInicial(): LocalDateTime {
        if (editar != null) return LocalDateTime.ofInstant(editar.empiezaEn, Formato.zona)
        val d = Formato.diaDe(dia)
        var h = minOf(20, LocalTime.now(Formato.zona).hour + 1)
        var m = 0
        val p = hora?.split(":")?.mapNotNull { it.toIntOrNull() }
        if (p != null && p.size == 2) { h = p[0]; m = p[1] }
        return d.atTime(h, m)
    }

    var tipo by remember { mutableStateOf(editar?.tipo ?: "visita") }
    var esTarea by remember { mutableIntStateOf(if (editar != null) (if (editar.tipo == "otro") 1 else 0) else (if (clase != "visita") 1 else 0)) }
    var prop by remember { mutableStateOf(propiedad) }
    var con by remember {
        mutableStateOf<Resultado?>(
            editar?.contactoId?.let { Resultado(it, "contacto", editar.contactoNombre ?: "") } ?: contacto?.let { Resultado(it.first, "contacto", it.second) },
        )
    }
    var cuando by remember { mutableStateOf(cuandoInicial()) }
    var duracion by remember { mutableIntStateOf(editar?.duracionMin ?: 60) }
    var titulo by remember { mutableStateOf(editar?.titulo ?: "") }
    var lugar by remember { mutableStateOf(editar?.lugar ?: "") }
    var notas by remember { mutableStateOf(editar?.notas ?: "") }
    var avisar by remember { mutableStateOf(if (editar != null) editar.recordar ?: false else true) }
    var calendario by remember { mutableStateOf(false) }
    var eligiendoProp by remember { mutableStateOf(false) }
    var eligiendoCon by remember { mutableStateOf(false) }
    var guardando by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val p = editar?.propiedadId
        if (p != null) estado.api.opcional<Propiedad>("/v1/propiedades/$p")?.let { prop = it }
    }

    /** Sumarla al calendario del teléfono (EKEventStore en iOS): la app de calendario con el evento cargado. */
    fun sumarAlCalendario(t: String) {
        val ini = cuando.atZone(Formato.zona).toInstant().toEpochMilli()
        val i = Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.Events.TITLE, t)
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, ini)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, ini + duracion * 60_000L)
            .putExtra(CalendarContract.Events.EVENT_LOCATION, prop?.direccion ?: lugar.nonEmpty)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { ctx.startActivity(i) }
    }

    suspend fun guardar() {
        guardando = true
        val t = if (esTarea == 1) "otro" else tipo
        val tit = if (esTarea == 1) titulo else "${Cita.tipos.firstOrNull { it.first == t }?.second ?: "Visita"} · ${prop?.nombre ?: lugar}"
        val c = CuerpoCita(t, editar?.estado ?: "pendiente", tit, cuando.atZone(Formato.zona).toInstant(), duracion, prop?.id, con?.id, lugar.nonEmpty, notas.nonEmpty,
            avisar && esTarea == 0, editar?.agenteId ?: prop?.agenteId ?: estado.usuario?.id, editar?.operacionId ?: operacion)
        try {
            if (editar != null) {
                estado.cola.enviar("PUT", "/v1/agenda/${editar.id}", c, etiqueta = "Cambios en $tit")
            } else {
                val subio = estado.cola.enviar("POST", "/v1/agenda", c, etiqueta = tit)
                val quien = (con?.titulo ?: "").split(" ").firstOrNull { it.isNotEmpty() }
                estado.avisar(if (!subio) "En cola · se agenda cuando vuelva la señal" else (if (avisar && quien != null) "Visita agendada · le avisamos a $quien" else "Agendada"))
            }
            if (calendario) sumarAlCalendario(tit)
            estado.version += 1
            cerrar()
        } catch (e: CancellationException) { throw e } catch (e: Exception) { estado.avisar(e) } finally { guardando = false }
    }

    @Composable
    fun Fila(l: String, v: String, primera: Boolean = false, hacer: () -> Unit) {
        val linea = C.linea
        Tocable(hacer) {
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).drawBehind { if (!primera) drawRect(linea, size = androidx.compose.ui.geometry.Size(size.width, 0.5.dp.toPx())) }
                .padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Texto(l, Estilo.subhead, C.texto2, Modifier.width(90.dp))
                Texto(v, Estilo(16f, Peso.medium), C.texto, Modifier.weight(1f), lineas = 1)
                Chevron()
            }
        }
    }

    Column(Modifier.fillMaxSize().background(C.superficie)) {
        Box(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 8.dp)) {
            Box(Modifier.align(Alignment.CenterStart).heightIn(min = 44.dp).clickable(onClick = cerrar).padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
                Texto("Cancelar", Estilo.body, C.accion)
            }
            Texto(if (editar == null) "Nueva" else "Editar", Estilo.headline, C.texto, Modifier.align(Alignment.Center))
            Box(Modifier.align(Alignment.CenterEnd).heightIn(min = 44.dp).clickable(enabled = !guardando) { alcance.launch { guardar() } }.padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center) {
                Texto(if (editar == null) "Agendar" else "Guardar", Estilo.headline.con(Peso.bold), if (guardando) C.texto3 else C.accion)
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Segmentado(listOf(0 to "Visita", 1 to "Tarea"), esTarea, { esTarea = it })
            if (esTarea == 0) {
                Segmentado(Cita.tipos.filter { it.first != "otro" }.map { it.first to it.second }, tipo, { tipo = it })
            } else {
                CampoCorredor(titulo, { titulo = it }, "Qué hay que hacer")
            }
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.hundida)) {
                Fila("Propiedad", prop?.nombre ?: "Elegí la propiedad", primera = true) { eligiendoProp = true }
                Fila("Con", con?.titulo ?: contacto?.second ?: "Elegí el contacto") { eligiendoCon = true }
                val linea = C.linea
                Column(Modifier.fillMaxWidth().drawBehind { drawRect(linea, size = androidx.compose.ui.geometry.Size(size.width, 0.5.dp.toPx())) }) {
                    RenglonFecha("Día", cuando.toLocalDate(), { cuando = it.atTime(cuando.toLocalTime()) })
                }
            }
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.hundida).padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                SelectorHora(cuando.hour * 60 + cuando.minute, { m -> cuando = cuando.toLocalDate().atTime(m / 60, m % 60) }, paso = 1)
            }
            Segmentado(listOf(15, 30, 45, 60, 90, 120).map { it to (if (it < 60) "$it min" else "${it / 60} h${if (it % 60 > 0) " ${it % 60}" else ""}") }, duracion, { duracion = it })
            if (prop == null) CampoCorredor(lugar, { lugar = it }, "Lugar (si no es en una propiedad)")
            CampoCorredor(notas, { notas = it }, "Notas", lineasMin = 2, lineasMax = 5)
            if (esTarea == 0 && (con != null || contacto != null)) {
                Row(Modifier.padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Imagen(Imagenes.logoWhatsApp, Modifier.size(22.dp))
                    Box(Modifier.weight(1f)) {
                        Interruptor("Avisarle a ${(con?.titulo ?: contacto?.second ?: "").split(" ").firstOrNull() ?: ""} por WhatsApp", avisar, { avisar = it })
                    }
                }
            }
            Row(Modifier.padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icono("calendar", 17f, C.texto)
                Box(Modifier.weight(1f)) { Interruptor("Sumarla al calendario del teléfono", calendario, { calendario = it }) }
            }
        }
    }
    Hoja(eligiendoProp, { eligiendoProp = false }) { ElegirPropiedad { prop = it } }
    Hoja(eligiendoCon, { eligiendoCon = false }) { ElegirContacto { con = it } }
}

@Serializable
private data class CuerpoContactoNuevo(val nombre: String, val tipo: String)

@Serializable
private data class ContactoNuevo(val id: String, val nombre: String)

@Serializable
private data class CtLista(val id: String, val nombre: String, val telefono: String? = null)

/** Elegir un contacto (o cargarlo nuevo con el nombre escrito). */
@Composable
fun ElegirContacto(tipo: String = "interesado", alElegir: (Resultado) -> Unit) {
    val estado = LocalEstado.current
    val cerrar = LocalCerrarHoja.current
    val alcance = rememberCoroutineScope()
    var q by remember { mutableStateOf("") }
    var res by remember { mutableStateOf<List<Resultado>>(emptyList()) }

    LaunchedEffect(q) {
        delay(200)
        res = if (q.length >= 2) {
            estado.api.opcional<List<Resultado>>("/v1/buscar", listOf("tipo" to "contacto", "q" to q)) ?: emptyList()
        } else {
            (estado.api.opcional<List<CtLista>>("/v1/contactos", listOf("limite" to "30")) ?: emptyList()).map { Resultado(it.id, "contacto", it.nombre, it.telefono) }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 8.dp)) {
            Box(Modifier.align(Alignment.CenterStart).heightIn(min = 44.dp).clickable(onClick = cerrar).padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
                Texto("Cancelar", Estilo.body, C.accion)
            }
            Texto("Elegí el contacto", Estilo.headline, C.texto, Modifier.align(Alignment.Center))
        }
        Buscador("Nombre o teléfono", q, { q = it }, Modifier.padding(horizontal = 16.dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).imePadding().padding(16.dp)) {
            Column(Modifier.fillMaxWidth().tarjeta()) {
                res.forEachIndexed { i, r ->
                    key(r.id) {
                        if (i > 0) Separador()
                        Tocable({ alElegir(r); cerrar() }) {
                            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                                Texto(r.titulo, Estilo.filaLista, C.texto)
                                r.subtitulo?.let { Texto(it, Estilo.footnote, C.texto2) }
                            }
                        }
                    }
                }
                if (q.length >= 2) {
                    if (res.isNotEmpty()) Separador()
                    Tocable({
                        alcance.launch {
                            try {
                                val n: ContactoNuevo = estado.api.enviar("POST", "/v1/contactos", CuerpoContactoNuevo(q, tipo))
                                alElegir(Resultado(n.id, "contacto", n.nombre))
                                cerrar()
                            } catch (e: CancellationException) { throw e } catch (e: Exception) { estado.avisar(e) }
                        }
                    }) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icono("person.badge.plus", 17f, C.accion)
                            Texto("Cargar «$q» como contacto nuevo", Estilo.body, C.accion)
                        }
                    }
                }
            }
        }
    }
}

@Serializable
private data class CtLlegando(val nombre: String? = null, val telefono: String? = null)

/** La ficha de una cita: cuándo, dónde, con quién; confirmar, marcar hecha, editar, cancelar o borrar. */
@Composable
fun FichaCita(id: String) {
    val estado = LocalEstado.current
    val alcance = rememberCoroutineScope()
    var c by remember { mutableStateOf<Cita?>(null) }
    var editando by remember { mutableStateOf(false) }

    suspend fun poner(nuevo: String) {
        val antes = c?.estado ?: "pendiente"
        try {
            estado.cola.enviar("PATCH", "/v1/agenda/$id", CuerpoEstadoCita(nuevo), etiqueta = "Cita ${FichaCitaTextos.estadoTexto(nuevo).lowercase()}")
            estado.version += 1
            if (nuevo == "hecha") Haptico.exito()
            estado.avisar("Cita ${FichaCitaTextos.estadoTexto(nuevo).lowercase()}") {
                estado.tarea {
                    try {
                        estado.api.hacer("PATCH", "/v1/agenda/$id", CuerpoEstadoCita(antes))
                        estado.version += 1
                    } catch (e: CancellationException) { throw e } catch (e: Exception) { estado.avisar(e) }
                }
            }
        } catch (e: CancellationException) { throw e } catch (e: Exception) { estado.avisar(e) }
    }

    fun llegando(x: Cita) {
        val cid = x.contactoId ?: return estado.avisar("La visita no tiene un contacto cargado.", ok = false)
        estado.tarea {
            val ct = estado.api.opcional<CtLlegando>("/v1/contactos/$cid")
            Afuera.whatsapp(ct?.telefono, "Hola ${ct?.nombre?.split(" ")?.firstOrNull() ?: ""}, estoy llegando a ${x.donde}.")
        }
    }

    fun borrar() {
        val tarea: Job = estado.tarea {
            delay(5000)
            try {
                estado.api.hacer("DELETE", "/v1/agenda/$id")
                estado.version += 1
            } catch (e: CancellationException) { throw e } catch (e: Exception) { estado.avisar(e) }
        }
        estado.volver()
        estado.avisar("Cita borrada") { tarea.cancel() }
    }

    LaunchedEffect(estado.version) { c = estado.api.opcional<Cita>("/v1/agenda/$id") }

    @Composable
    fun Enlace(i: String, t: String, primera: Boolean, hacer: () -> Unit) {
        val linea = C.linea
        Tocable(hacer) {
            Row(Modifier.fillMaxWidth().heightIn(min = 50.dp).drawBehind { if (!primera) drawRect(linea, size = androidx.compose.ui.geometry.Size(size.width, 0.5.dp.toPx())) }
                .padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(24.dp), contentAlignment = Alignment.Center) { Icono(i, 17f, C.accion) }
                Texto(t, Estilo.body16, C.texto, Modifier.weight(1f))
                Chevron()
            }
        }
    }

    PantallaApilada("Cita", acciones = if (estado.escribe) ({ BotonBarraIcono("trash", "Borrar") { borrar() } }) else null) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            val x = c
            if (x != null) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icono(x.icono, 13f, AgendaRaizTextos.tono(x))
                            Texto(x.tipoTexto, Estilo(13f, Peso.bold), AgendaRaizTextos.tono(x))
                        }
                        Texto(x.donde, Estilo.titulo, C.texto)
                        Texto("${Formato.mayuscula(Formato.diaCompleto(x.empiezaEn))} · ${Formato.horaDe(x.empiezaEn)} a ${Formato.horaDe(x.terminaEn)}", Estilo.body16, C.texto2)
                        Box(Modifier.padding(top = 4.dp).height(24.dp).clip(CircleShape).background(C.okFondo).padding(horizontal = 9.dp), contentAlignment = Alignment.Center) {
                            Texto(FichaCitaTextos.estadoTexto(x.estado), Estilo(12f, Peso.bold), C.okTinta)
                        }
                    }
                    Column(Modifier.fillMaxWidth().tarjeta()) {
                        x.propiedadId?.let { p -> Enlace("house", x.propiedadTitulo ?: "Propiedad", primera = true) { estado.abrir(AFichaPropiedad(p)) } }
                        x.contactoId?.let { ct -> Enlace("person", x.contactoNombre ?: "Contacto", primera = x.propiedadId == null) { estado.abrir(AFichaContacto(ct)) } }
                        x.operacionId?.let { o -> Enlace("rectangle.split.3x1", "La operación", primera = false) { estado.abrir(AFichaOperacion(o)) } }
                        Enlace("location", "Cómo llegar", primera = x.propiedadId == null && x.contactoId == null) { Afuera.mapas(x.lugar ?: x.propiedadTitulo ?: "") }
                    }
                    x.notas?.nonEmpty?.let { n -> Texto(n, Estilo.body16, C.texto, Modifier.fillMaxWidth().tarjeta().padding(14.dp)) }
                    if (estado.escribe) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (x.estado != "hecha") BotonAccion("Marcar como hecha", { alcance.launch { poner("hecha") } })
                            else BotonHundido("Volver a pendiente", { alcance.launch { poner("pendiente") } })
                            if (x.estado == "pendiente" || x.estado == null) BotonHundido("Estoy llegando", { llegando(x) })
                            BotonHundido("Editar", { editando = true })
                            if (x.estado != "cancelada") BotonTexto("Cancelar la cita", { alcance.launch { poner("cancelada") } }, color = C.error)
                        }
                    }
                }
            } else Box(Modifier.fillMaxWidth().padding(top = 60.dp), contentAlignment = Alignment.Center) { Rueda() }
        }
    }
    Hoja(editando, { editando = false }) { c?.let { NuevaCita(editar = it) } }
}

/** Los `static func` de `FichaCita` en iOS. */
object FichaCitaTextos {
    fun estadoTexto(e: String?): String = mapOf("pendiente" to "Pendiente", "hecha" to "Hecha", "cancelada" to "Cancelada")[e ?: ""] ?: "Pendiente"
}
