package ar.com.corredor.app.pantallas.agenda

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import ar.com.corredor.app.app.Estado
import ar.com.corredor.app.app.LocalEstado
import ar.com.corredor.app.nucleo.Cita
import ar.com.corredor.app.nucleo.Fecha
import ar.com.corredor.app.nucleo.FechaApi
import ar.com.corredor.app.nucleo.Formato
import ar.com.corredor.app.nucleo.Licencia
import ar.com.corredor.app.nucleo.Miembro
import ar.com.corredor.app.nucleo.opcional
import ar.com.corredor.app.pantallas.mensajes.Charla
import ar.com.corredor.app.pantallas.mensajes.avisarReintentar
import ar.com.corredor.app.pantallas.mensajes.texto
import ar.com.corredor.app.pantallas.propiedades.Propiedad
import ar.com.corredor.app.pantallas.propiedades.rememberDictado
import ar.com.corredor.app.tema.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.Locale

// Clon de Pantallas/Agenda/AltaRapida.swift.

@Serializable
private data class PropAlta(val id: String, val titulo: String? = null, val direccion: String? = null, val estado: String? = null, val agenteId: String? = null, val barrio: String? = null)

@Serializable
private data class CtAlta(val id: String, val nombre: String, val agenteId: String? = null)

@Serializable
private data class CuerpoAlta(
    val tipo: String, val titulo: String, val empiezaEn: FechaApi, val duracionMin: Int,
    val propiedadId: String?, val contactoId: String?, val operacionId: String?, val agenteId: String?, val recordar: Boolean,
)

@Serializable
private data class CreadaAlta(val id: String)

/** El alta rápida de la agenda (la hoja del celular de la web, lib/crm/AltaRapida.svelte): Visita, Tarea o
 *  Recordatorio; «visita Gorriti 5530 con Inés el sábado a las 11» escrito o dictado, lo entendido marcado en colores
 *  (verde la propiedad, azul la persona, oro el cuándo), quién la hace (el agente del contacto o de la propiedad) y el
 *  aviso si ya tiene algo a esa hora. */
@Composable
fun AltaRapidaCita(
    propiedadPrevia: Propiedad? = null,
    contactoPrevio: String? = null,
    operacion: String? = null,
    diaPrevio: String? = null,
    horaPrevia: String? = null,
    clasePrevia: String = "visita",
    /** Pasar al formulario campo por campo. */
    alFormulario: () -> Unit,
) {
    val estado = LocalEstado.current
    val cerrar = LocalCerrarHoja.current
    val alcance = rememberCoroutineScope()
    var texto by remember { mutableStateOf("") }
    var clase by remember { mutableStateOf(clasePrevia) }
    var props by remember { mutableStateOf<List<Entender.Opcion>>(emptyList()) }
    var contactos by remember { mutableStateOf<List<Entender.Opcion>>(emptyList()) }
    var equipo by remember { mutableStateOf<List<Miembro>>(emptyList()) }
    var citasDelDia by remember { mutableStateOf<List<Cita>>(emptyList()) }
    var elAgente by remember { mutableStateOf<String?>(null) }
    var guardando by remember { mutableStateOf(false) }
    val dictado = rememberDictado()
    var base by remember { mutableStateOf("") }

    val tipoForzado = if (clase == "visita") "visita" else "otro"
    val e = Entender.entender(texto, propiedades = props, contactos = contactos, tipo = tipoForzado)
    val contacto: Entender.Opcion? = e.contacto ?: contactoPrevio?.let { id -> contactos.firstOrNull { it.id == id } }
    val propiedad: Entender.Opcion? = e.propiedad ?: propiedadPrevia?.let { Entender.Opcion(it.id, it.nombre, it.agenteId, it.barrio) }
    val dia: String? = e.dia ?: diaPrevio
    val hora: String? = e.hora ?: horaPrevia
    val yo = estado.usuario?.id ?: ""
    val agente = elAgente ?: contacto?.agenteId ?: propiedad?.agenteId ?: yo

    fun nombreDe(id: String?): String {
        if (id == yo) return "Vos"
        return Charla.primerNombre(equipo.firstOrNull { it.usuarioId == id }?.completo)
    }

    val fin: String? = run {
        val h = hora ?: return@run null
        val p = h.split(":").mapNotNull { it.toIntOrNull() }
        if (p.size != 2) return@run null
        val t = p[0] * 60 + p[1] + e.duracionMin
        String.format(Locale.US, "%02d:%02d", (t / 60) % 24, t % 60)
    }

    val cuando: String = run {
        val d0 = dia ?: return@run ""
        val hoy = Formato.hoy().toString()
        val d = if (d0 == hoy) "hoy" else if (d0 == Entender.sumar(hoy, 1)) "mañana"
        else Fecha.dia(d0)?.let { f -> "${AgendaRaizTextos.nombreDia(f).take(3)} ${Formato.diaDe(f).dayOfMonth}" } ?: d0
        listOfNotNull(d, hora?.let { h -> fin?.let { "$h a $it" } }).joinToString(" · ")
    }

    /** ¿Quien la hace ya tiene algo a esa hora? */
    val choque: Cita? = run {
        val d = dia ?: return@run null
        val h = hora ?: return@run null
        val ini = Entender.instante(d, h) ?: return@run null
        val f = ini.plusSeconds((e.duracionMin * 60).toLong())
        citasDelDia.firstOrNull { it.agenteId == agente && it.estado != "cancelada" && it.empiezaEn < f && it.terminaEn > ini }
    }

    suspend fun cargarOpciones() {
        val ps = estado.api.opcional<List<PropAlta>>("/v1/propiedades", listOf("limite" to "200", "orden" to "actualizada")) ?: emptyList()
        props = ps.filter { it.estado != "suspendida" }.map { Entender.Opcion(it.id, it.titulo ?: it.direccion ?: "", it.agenteId, it.barrio) }
        val cs = estado.api.opcional<List<CtAlta>>("/v1/contactos", listOf("limite" to "200")) ?: emptyList()
        contactos = cs.map { Entender.Opcion(it.id, it.nombre, it.agenteId, null) }
        equipo = estado.api.opcional<List<Miembro>>("/v1/equipo") ?: emptyList()
    }

    suspend fun cargarDia() {
        val d = dia
        if (d == null) { citasDelDia = emptyList(); return }
        citasDelDia = estado.api.opcional<List<Cita>>("/v1/agenda", listOf("desde" to d, "hasta" to d, "limite" to "500")) ?: emptyList()
    }

    suspend fun agendar() {
        dictado.parar()
        val d = dia
        val h = hora
        val empieza = if (d != null && h != null) Entender.instante(d, h) else null
        if ((texto.trim().isEmpty() && propiedad == null) || d == null || h == null || empieza == null) {
            estado.avisar(if (d == null) "Decí qué día (por ejemplo, «el sábado a las 11»)." else "Decí a qué hora (por ejemplo, «a las 11»).", ok = false)
            return
        }
        val tipo = tipoForzado
        val nombres = mapOf("visita" to "Visita", "tasacion" to "Tasación", "firma" to "Firma", "llamado" to "Llamar", "otro" to "")
        var titulo = propiedad?.let { "${nombres[tipo] ?: ""} ${it.titulo.split(",").firstOrNull() ?: ""}".trim() } ?: e.titulo
        if (titulo.isEmpty()) titulo = "Visita"
        if (clase == "recordatorio") titulo = "Recordatorio: $titulo"
        guardando = true
        try {
            val c: CreadaAlta = estado.api.enviar("POST", "/v1/agenda", CuerpoAlta(tipo, titulo.take(160), empieza, e.duracionMin,
                propiedad?.id, contacto?.id, operacion, agente.ifEmpty { null }, tipo == "visita" && contacto != null))
            val t = "Agendada · $cuando"
            estado.avisar(t) {
                estado.tarea {
                    try {
                        estado.api.hacer("DELETE", "/v1/agenda/${c.id}")
                        estado.version += 1
                    } catch (x: CancellationException) { throw x } catch (x: Exception) { estado.avisar(x) }
                }
            }
            Haptico.exito()
            estado.version += 1
            cerrar()
        } catch (x: CancellationException) { throw x } catch (x: Exception) { estado.avisar(x) } finally { guardando = false }
    }

    LaunchedEffect(Unit) { cargarOpciones() }
    LaunchedEffect(dia ?: "") { cargarDia() }
    DisposableEffect(Unit) { onDispose { dictado.parar() } }
    LaunchedEffect(dictado.texto) { if (dictado.grabando) texto = listOf(base, dictado.texto).filter { it.isNotEmpty() }.joinToString(" ") }

    Column(Modifier.fillMaxSize().imePadding().padding(start = 20.dp, end = 20.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Grabber() }
        Texto("Agendar", Estilo.tituloHojaChico, C.texto)
        Segmentado(listOf("visita" to "Visita", "tarea" to "Tarea", "recordatorio" to "Recordatorio"), clase, { clase = it })
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // El texto con lo entendido pintado, y el campo para escribir.
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                CampoCorredor(texto, { texto = it }, "visita Gorriti 5530 con Inés el sábado a las 11", lineasMin = 2, lineasMax = 4,
                    modifier = Modifier.semantics { contentDescription = "Qué, con quién y cuándo" })
                if (texto.isNotEmpty()) {
                    val ok = C.okTinta; val azul = C.azulTinta; val aviso = C.avisoTinta; val gris = C.texto2
                    TextoRico(buildAnnotatedString {
                        e.partes.forEach { p ->
                            when (p.clase) {
                                Entender.Clase.prop -> withStyle(SpanStyle(color = ok, fontWeight = FontWeight.Bold)) { append(p.t) }
                                Entender.Clase.persona -> withStyle(SpanStyle(color = azul, fontWeight = FontWeight.Bold)) { append(p.t) }
                                Entender.Clase.cuando -> withStyle(SpanStyle(color = aviso, fontWeight = FontWeight.Bold)) { append(p.t) }
                                null -> withStyle(SpanStyle(color = gris)) { append(p.t) }
                            }
                        }
                    }, Estilo.subhead)
                }
            }
            // Las filas
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.hundida)) {
                FilaAlta("house", propiedad?.let { listOfNotNull(it.titulo, it.barrio).joinToString(" · ") } ?: "Sin propiedad", primera = true)
                val linea = C.linea
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 46.dp).drawBehind { drawRect(linea, size = androidx.compose.ui.geometry.Size(size.width, 0.5.dp.toPx())) }.padding(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.width(22.dp), contentAlignment = Alignment.Center) { Icono("person", 17f, C.accion) }
                    Texto(contacto?.titulo ?: "Sin contacto", Estilo.body16, C.texto, Modifier.weight(1f))
                    if (contacto != null && clase == "visita") Texto("le mandamos confirmación", Estilo.footnote, C.texto3)
                }
                FilaAlta("clock", if (cuando.isEmpty()) "Decí el día y la hora" else Formato.mayuscula(cuando) + (if (agente == yo) "" else " · ${nombreDe(agente)}"), primera = false)
                choque?.let { c ->
                    Row(Modifier.fillMaxWidth().background(C.avisoFondo).padding(horizontal = 14.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icono("exclamationmark.triangle.fill", 17f, C.avisoTinta)
                        Texto("${nombreDe(agente)} tiene otra ${c.propiedadTitulo?.let { "cosa a esa hora en ${it.split(",").firstOrNull() ?: ""}" } ?: "cosa a esa hora"}.${if (agente != yo) " ¿La hacés vos?" else ""}",
                            Estilo.footnote, C.texto, Modifier.weight(1f))
                        if (agente != yo) BotonTonal("Sí", { elAgente = yo })
                    }
                }
            }
            Texto("Elegir campo por campo", Estilo.footnoteFuerte, C.accion, Modifier.clickable { alFormulario() })
        }
        Spacer(Modifier.weight(1f, fill = false))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(52.dp).clip(CircleShape).background(if (dictado.grabando) C.error else C.hundida)
                    .clickable { if (dictado.grabando) dictado.parar() else { base = texto; dictado.empezar(texto) } }
                    .semantics { contentDescription = if (dictado.grabando) "Parar el dictado" else "Dictar" },
                contentAlignment = Alignment.Center,
            ) { Icono("mic.fill", 24f, if (dictado.grabando) Color.White else C.accion) }
            BotonPrincipal(if (guardando) "Agendando…" else "Agendar", { alcance.launch { agendar() } }, Modifier.weight(1f), habilitado = !guardando)
        }
    }
}

@Composable
private fun FilaAlta(i: String, t: String, primera: Boolean) {
    val linea = C.linea
    Row(
        Modifier.fillMaxWidth().heightIn(min = 46.dp).drawBehind { if (!primera) drawRect(linea, size = androidx.compose.ui.geometry.Size(size.width, 0.5.dp.toPx())) }
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(22.dp), contentAlignment = Alignment.Center) { Icono(i, 17f, C.accion) }
        Texto(t, Estilo.body16, C.texto, Modifier.weight(1f), lineas = 2)
    }
}

@Serializable
private data class CuerpoLicencia(val usuarioId: String, val tipo: String, val desde: String, val hasta: String, val nota: String? = null)

/** «Licencias y ausencias» desde la agenda: quién falta y hasta cuándo. Cada uno carga la suya; la dueña, la de
 *  cualquiera. Quitar es al toque, con «Deshacer». */
@Composable
fun LicenciasAgenda() {
    val estado = LocalEstado.current
    val cerrar = LocalCerrarHoja.current
    val alcance = rememberCoroutineScope()
    var licencias by remember { mutableStateOf<List<Licencia>?>(null) }
    var quitadas by remember { mutableStateOf<Set<String>>(emptySet()) }
    var equipo by remember { mutableStateOf<List<Miembro>>(emptyList()) }
    var quien by remember { mutableStateOf("") }
    var tipo by remember { mutableStateOf("licencia") }
    var desde by remember { mutableStateOf(Formato.hoy()) }
    var hasta by remember { mutableStateOf(Formato.hoy()) }
    var error by remember { mutableStateOf<String?>(null) }
    var guardando by remember { mutableStateOf(false) }

    val yo = estado.usuario?.id ?: ""

    suspend fun cargar() {
        val hoy = Formato.hoy().toString()
        val l = estado.api.opcional<List<Licencia>>("/v1/licencias") ?: emptyList()
        licencias = l.filter { it.hasta >= hoy }
        equipo = estado.api.opcional<List<Miembro>>("/v1/equipo") ?: emptyList()
        if (quien.isEmpty()) quien = yo
    }

    suspend fun guardar() {
        val d = desde.toString()
        val h = hasta.toString()
        if (h < d) { error = "La vuelta tiene que ser después de la salida."; return }
        guardando = true
        try {
            val l: Licencia = estado.api.enviar("POST", "/v1/licencias", CuerpoLicencia(if (estado.esDuenio) quien else yo, tipo, d, h))
            val quienTexto = if (l.usuarioId == yo) "Quedaste" else "${Charla.primerNombre(l.usuarioNombre)} queda"
            estado.avisar("$quienTexto ${(LicenciasAgendaTextos.nombre[l.tipo ?: ""] ?: "de licencia").lowercase()} hasta el ${LicenciasAgendaTextos.corta(l.hasta)}") {
                estado.tarea {
                    try {
                        estado.api.hacer("DELETE", "/v1/licencias/${l.id}")
                        estado.version += 1
                    } catch (x: CancellationException) { throw x } catch (x: Exception) { estado.avisar("No se guardó", ok = false) }
                }
            }
            estado.version += 1
            cerrar()
        } catch (x: CancellationException) { throw x } catch (x: Exception) { error = Estado.texto(x) } finally { guardando = false }
    }

    suspend fun quitar(l: Licencia) {
        quitadas = quitadas + l.id
        try {
            estado.api.hacer("DELETE", "/v1/licencias/${l.id}")
            estado.avisar("Quitaste la licencia de ${if (l.usuarioId == yo) "vos" else Charla.primerNombre(l.usuarioNombre)}") {
                estado.tarea {
                    try {
                        estado.api.hacer("POST", "/v1/licencias", CuerpoLicencia(l.usuarioId, l.tipo ?: "licencia", l.desde, l.hasta, l.nota))
                        quitadas = quitadas - l.id
                        cargar()
                        estado.version += 1
                    } catch (x: CancellationException) { throw x } catch (x: Exception) { estado.avisar("No se guardó", ok = false) }
                }
            }
            estado.version += 1
        } catch (x: CancellationException) { throw x } catch (x: Exception) {
            quitadas = quitadas - l.id
            estado.avisarReintentar("No se guardó") { alcance.launch { quitar(l) } }
        }
    }

    LaunchedEffect(Unit) { cargar() }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Grabber() }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icono("beach.umbrella", 20f, C.texto)
            Texto("Licencias y ausencias", Estilo.tituloHojaChico, C.texto)
        }
        Texto(Formato.sinViudas("Quién falta y hasta cuándo. El Inicio lo muestra en «El equipo hoy»."), Estilo.subhead, C.texto2)
        // Lista
        val visibles = (licencias ?: emptyList()).filter { it.id !in quitadas }
        if (licencias == null) {
            EsqueletoFilas(filas = 2, miniatura = 36.dp)
        } else if (visibles.isNotEmpty()) {
            Column(Modifier.fillMaxWidth().tarjeta(14.dp)) {
                visibles.forEachIndexed { i, l ->
                    key(l.id) {
                        if (i > 0) Box(Modifier.fillMaxWidth().height(0.5.dp).background(C.linea))
                        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            AvatarIniciales(l.usuarioNombre ?: "", tam = 36.dp, fondo = C.errorFondo, tinta = C.errorTinta)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                Texto(if (l.usuarioId == yo) "Vos" else (l.usuarioNombre ?: ""), Estilo.filaLista, C.texto)
                                Texto("${LicenciasAgendaTextos.nombre[l.tipo ?: ""] ?: "De licencia"} · ${if (l.desde == l.hasta) "el ${LicenciasAgendaTextos.corta(l.desde)}" else "del ${LicenciasAgendaTextos.corta(l.desde)} al ${LicenciasAgendaTextos.corta(l.hasta)}"}",
                                    Estilo.footnote, C.texto2)
                            }
                            if (estado.esDuenio || l.usuarioId == yo) BotonTonal("Quitar", { alcance.launch { quitar(l) } })
                        }
                    }
                }
            }
        }
        // Alta
        if (estado.escribe) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (estado.esDuenio) {
                    Etiquetado("Quién") {
                        SelectorMenu(equipo.filter { it.rol != "lector" || it.usuarioId == yo }.map { p -> p.usuarioId to (if (p.usuarioId == yo) "${p.completo} (vos)" else p.completo) },
                            quien, { quien = it }, "Quién")
                    }
                }
                Etiquetado("Qué") { Segmentado(LicenciasAgendaTextos.tipos, tipo, { tipo = it }) }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Etiquetado("Desde", Modifier.weight(1f)) { RenglonFecha("", desde, { desde = it; if (hasta.isBefore(it)) hasta = it }) }
                    Etiquetado("Hasta", Modifier.weight(1f)) { RenglonFecha("", hasta, { hasta = it }, minimo = desde) }
                }
                error?.let { MensajeError(it) }
                // Como en iOS: «Cancelar» a su ancho y «Cargar» con el resto.
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    BotonSecundario("Cancelar", { cerrar() })
                    BotonPrincipal(if (guardando) "Cargando…" else "Cargar", { alcance.launch { guardar() } }, Modifier.weight(1f), habilitado = !guardando)
                }
            }
        }
    }
}

/** Los textos de `LicenciasAgenda` (sus `static let` en iOS). */
object LicenciasAgendaTextos {
    val tipos = listOf("licencia" to "Licencia", "vacaciones" to "Vacaciones", "enfermedad" to "Enfermedad", "tramite" to "Trámite")
    val nombre = mapOf("licencia" to "De licencia", "vacaciones" to "De vacaciones", "enfermedad" to "Por enfermedad", "tramite" to "Trámite")

    fun corta(d: String): String = Fecha.dia(d)?.let { Formato.dia(it) } ?: d
}
