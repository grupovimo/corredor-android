package ar.com.corredor.app.pantallas.propiedades

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ar.com.corredor.app.app.BarraDe
import ar.com.corredor.app.app.LocalEstado
import ar.com.corredor.app.nucleo.*
import ar.com.corredor.app.pantallas.agenda.NuevaCita
import ar.com.corredor.app.pantallas.cartera.Contacto
import ar.com.corredor.app.pantallas.cartera.Operacion
import ar.com.corredor.app.pantallas.cobranzas.ContratoAPI
import ar.com.corredor.app.tema.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import kotlin.math.ceil
import kotlin.math.roundToInt

// Clon de Pantallas/Propiedades/Ficha.swift.

/** Un movimiento del historial (GET /v1/actividad?entidad=propiedad&entidadId=). */
@Serializable
data class RegistroActividad(
    val id: Int,
    val actorNombre: String? = null,
    val accion: String,
    val creadoEn: FechaApi? = null,
) {
    companion object {
        val acciones = mapOf(
            "crear" to "La cargó", "editar" to "Editó la ficha", "duplicar" to "La duplicó", "soltar" to "La dejó libre",
            "borrar" to "La mandó a la papelera", "restaurar" to "La sacó de la papelera", "enviar_ficha" to "Mandó la ficha",
        )
    }
}

/** Una persona de la pestaña Interesados: de una operación abierta, con lo que pasa con ella. */
data class PersonaFicha(
    val id: String,
    val nombre: String,
    val detalle: String,
    val tono: Tono,
    val responder: Boolean,
    val contactoId: String?,
)

@Serializable private data class CuerpoDestacada(val destacada: Boolean)
@Serializable private data class CuerpoEstadoPropiedad(val estado: String)
@Serializable private data class CuerpoPublicada(val publicada: Boolean)
@Serializable private data class PropiedadNueva(val id: String)

/** Un `try?` de iOS para lo que no tira la ficha. */
private suspend fun <T> quizas(bloque: suspend () -> T): T? =
    try { bloque() } catch (e: CancellationException) { throw e } catch (e: Exception) { null }

/** Una fila de «Dónde está publicada». */
private data class FilaPortal(
    val clave: String,
    val nombre: String,
    /** ok · err · off · pausa */
    val estado: String,
    val detalle: String,
    val pub: PublicacionPortal? = null,
)

/** 3D Ficha de una propiedad (web 02 · 9E/9C): las fotos, el estado, el precio y lo que pasó con él, los datos, dónde
 *  está publicada (con arreglar el error de un portal), las vistas por día, la gente (interesados, visitas, reservas,
 *  documentos, historial), el propietario y la barra al pulgar. Cada parte que no llega queda vacía: la ficha no se cae
 *  entera. */
@Composable
fun FichaPropiedad(
    id: String,
    /** Abrir con la hoja de «Arreglar» del portal con error. */
    arreglarAlAbrir: Boolean = false,
    /** Abrir con la hoja de compartir (y, con `compartirCon`, esa persona arriba). */
    compartirAlAbrir: Boolean = false,
    compartirCon: String? = null,
) {
    val estado = LocalEstado.current
    val ctx = LocalContext.current
    val cerrar = LocalCerrarHoja.current
    val alcance = rememberCoroutineScope()

    var p by remember { mutableStateOf<Propiedad?>(null) }
    var senal by remember { mutableStateOf<Senal?>(null) }
    var pubs by remember { mutableStateOf<List<PublicacionPortal>>(emptyList()) }
    var vistas by remember { mutableStateOf<VistasPropiedadRespuesta?>(null) }
    var interesados by remember { mutableStateOf<List<Interesado>>(emptyList()) }
    var operaciones by remember { mutableStateOf<List<Operacion>>(emptyList()) }
    var citas by remember { mutableStateOf<List<Cita>>(emptyList()) }
    var reservas by remember { mutableStateOf<List<ReservaAPI>>(emptyList()) }
    var historial by remember { mutableStateOf<List<RegistroActividad>>(emptyList()) }
    var propietario by remember { mutableStateOf<Contacto?>(null) }
    var destinatario by remember { mutableStateOf<Contacto?>(null) }
    var contratos by remember { mutableStateOf<List<ContratoAPI>>(emptyList()) }
    var documentos by remember { mutableIntStateOf(0) }
    var web by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<ErrorApi?>(null) }
    var compartir by remember { mutableStateOf(false) }
    var agendar by remember { mutableStateOf(false) }
    var reservar by remember { mutableStateOf(false) }
    var arreglando by remember { mutableStateOf<PublicacionPortal?>(null) }
    var portalEnUso by remember { mutableStateOf<String?>(null) }
    var pestana by remember { mutableStateOf("interesados") }
    var todos by remember { mutableStateOf(false) }
    var cambiarEstado by remember { mutableStateOf(false) }
    var previo by remember { mutableStateOf<ArchivoPrevio?>(null) }
    var yaAbrio by remember { mutableStateOf(false) }

    val lector = !estado.escribe
    val link = "${web ?: ""}/propiedades/${p?.slug ?: id}"

    // MARK: Lo que se calcula

    fun lineaPrecio(p: Propiedad): String? {
        val antes = senal?.precioAnterior ?: return null
        val ahora = p.precio ?: return null
        val f = senal?.precioCambio ?: return null
        if (antes == ahora) return null
        return "${if (antes > ahora) "bajó" else "subió"} de ${Formato.numero(antes.toInt())} ${Formato.hace(f)}"
    }

    val visitasProp = citas.filter { it.tipo == "visita" && it.estado != "cancelada" }
    val abiertas = operaciones.filter { it.etapa != "cierre" }
    val proximaVisita = visitasProp.filter { it.estado == "pendiente" && it.empiezaEn > Instant.now().minusSeconds(7200) }.minByOrNull { it.empiezaEn }

    fun portales(p: Propiedad): List<FilaPortal> = PortalesCartera.todos.map { pt ->
        if (pt.clave == "web") {
            return@map FilaPortal(
                "web", pt.nombre, if (p.publicada == true) "ok" else "off",
                if (p.publicada == true) link.replace("https://", "").replace("http://", "") else "No publicada",
            )
        }
        val x = pubs.firstOrNull { it.portal == pt.clave }
        if (x == null || x.estado == "finalizada" || x.estado == "pendiente") {
            return@map FilaPortal(pt.clave, pt.nombre, "off", "No publicada", pubs.firstOrNull { it.portal == pt.clave })
        }
        if (x.estado == "error") return@map FilaPortal(pt.clave, pt.nombre, "err", "No la publicó · ${x.actualizadoEn?.let { Formato.hace(it) } ?: ""}", x)
        if (x.estado == "pausada") return@map FilaPortal(pt.clave, pt.nombre, "pausa", "Pausada", x)
        val det = if (pt.clave == "mercadolibre") {
            val n = senal?.sinResponder ?: 0
            "Activa" + (if (n > 0) " · ${Formato.cantidad(n, "pregunta sin responder", "preguntas sin responder")}" else "")
        } else {
            "En el feed · actualizada ${(x.sincronizadaEn ?: x.actualizadoEn)?.let { Formato.hace(it) } ?: ""}"
        }
        FilaPortal(pt.clave, pt.nombre, "ok", det, x)
    }

    fun otrosPortales(portal: String): List<String> {
        val p = p ?: return emptyList()
        return portales(p).filter { it.estado == "ok" && it.clave != portal }.map { if (it.clave == "web") "tu web" else it.nombre }
    }

    val semana: Int = run {
        val desde = Instant.now().minusSeconds(7 * 86400L)
        (vistas?.dias ?: emptyList()).filter { (Fecha.dia(it.dia) ?: Instant.MIN) > desde }.sumOf { it.cantidad }
    }

    /** Las últimas dos semanas (13 días), con la marca del cambio de precio. */
    data class Barras(val dias: List<Pair<String, Int>>, val max: Int, val corte: Int)
    val barras: Barras = run {
        val por = HashMap<String, Int>()
        (vistas?.dias ?: emptyList()).forEach { por[it.dia] = (por[it.dia] ?: 0) + it.cantidad }
        val dias = (12 downTo 0).map { i ->
            val k = Formato.diaISO(Instant.now().minusSeconds(i * 86400L))
            k to (por[k] ?: 0)
        }
        val corte = senal?.precioCambio?.let { Formato.diaISO(it) }
        val i = corte?.let { c -> dias.indexOfFirst { it.first >= c }.takeIf { it >= 0 } } ?: -1
        Barras(dias, maxOf(1, dias.maxOfOrNull { it.second } ?: 1), if (p?.let { lineaPrecio(it) } != null) i else -1)
    }

    val subaVistas: Int? = run {
        val b = barras
        if (b.corte <= 0) return@run null
        val antes = b.dias.subList(0, b.corte)
        val despues = b.dias.subList(b.corte, b.dias.size)
        val a = antes.sumOf { it.second }.toDouble() / antes.size
        val d = despues.sumOf { it.second }.toDouble() / despues.size
        if (a > 0 && d > a) ((d - a) / a * 100).roundToInt() else null
    }

    val personas: List<PersonaFicha> = run {
        val out = mutableListOf<PersonaFicha>()
        val ahora = Instant.now()
        for (o in abiertas) {
            val c = visitasProp.firstOrNull { it.contactoId != null && it.contactoId == o.contactoId }
            val nombre = o.contactoNombre ?: o.titulo ?: "Interesado"
            if (c != null && c.estado == "pendiente" && c.empiezaEn > ahora.minusSeconds(7200)) {
                out.add(PersonaFicha(o.id, nombre, "Visita ${Senal.cuando(c.empiezaEn)}", Tono.oro, false, o.contactoId))
            } else if (c != null && c.estado == "hecha") {
                out.add(PersonaFicha(o.id, nombre, "Visitó el ${ReservaAPI.corta(Formato.diaISO(c.empiezaEn))}${c.notas?.let { " · «$it»" } ?: ""}", Tono.neutro, false, o.contactoId))
            } else if (o.etapa == "nuevo") {
                out.add(PersonaFicha(o.id, nombre, "Escribió ${o.creadaEn?.let { Formato.hace(it) } ?: ""} · sin responder", Tono.rojo, true, o.contactoId))
            } else {
                out.add(PersonaFicha(o.id, nombre, "Última novedad ${o.actualizadoEn?.let { Formato.hace(it) } ?: ""}", Tono.neutro, false, o.contactoId))
            }
        }
        fun orden(t: Tono) = if (t == Tono.oro) 0 else if (t == Tono.rojo) 1 else 3
        out.sortedBy { orden(it.tono) }
    }

    /** Los que buscan algo así y no tienen una operación con esta propiedad. */
    val buscan = interesados.filter { i -> abiertas.none { it.contactoId == i.contactoId } }

    val resumenSemana: String = run {
        val visitasSemana = visitasProp.count { val d = diasCalendario(it.empiezaEn, Instant.now()); d in 0..7 }
        "${Formato.cantidad(semana, "vista", "vistas")}, ${Formato.cantidad(visitasSemana, "visita", "visitas")}, ${Formato.cantidad(abiertas.size, "consulta", "consultas")}"
    }

    // MARK: Cargar y cambiar

    suspend fun cargar() {
        try {
            val x: Propiedad = estado.api.get("/v1/propiedades/$id")
            p = x
            error = null
        } catch (e: CancellationException) { throw e } catch (e: ErrorApi) { error = e; return } catch (e: Exception) { return }
        // El resto no tira la ficha: lo que no llega queda vacío.
        senal = quizas { Senal.de(listOf(id), estado.api) }?.get(id)
        pubs = estado.api.opcional<List<PublicacionPortal>>("/v1/propiedades/$id/publicaciones") ?: emptyList()
        vistas = estado.api.opcional<VistasPropiedadRespuesta>("/v1/propiedades/$id/vistas")
        interesados = estado.api.opcional<List<Interesado>>("/v1/propiedades/$id/interesados", listOf("sinVisita" to "si", "limite" to "20")) ?: emptyList()
        operaciones = estado.api.opcional<List<Operacion>>("/v1/operaciones", listOf("propiedad" to id, "limite" to "50")) ?: emptyList()
        citas = estado.api.opcional<List<Cita>>("/v1/agenda", listOf("propiedad" to id, "rango" to "todas", "limite" to "50")) ?: emptyList()
        reservas = estado.api.opcional<List<ReservaAPI>>("/v1/propiedades/$id/reservas") ?: emptyList()
        historial = estado.api.opcional<List<RegistroActividad>>("/v1/actividad", listOf("entidad" to "propiedad", "entidadId" to id, "limite" to "20")) ?: emptyList()
        val pid = p?.propietarioId
        propietario = if (pid != null) estado.api.opcional<Contacto>("/v1/contactos/$pid") else null
        if (compartirCon != null && destinatario == null) destinatario = estado.api.opcional<Contacto>("/v1/contactos/$compartirCon")
        contratos = (estado.api.opcional<List<ContratoAPI>>("/v1/contratos", listOf("propiedad" to id)) ?: emptyList())
            .filter { it.estado != "rescindido" && it.estado != "finalizado" }
        estado.api.opcional<List<JSONLibre>>("/v1/documentos", listOf("propiedad" to id))?.let { documentos = it.size }
        if (web == null) web = estado.api.opcional<Inmobiliaria>("/v1/inmobiliaria")?.web
        if (!yaAbrio) {
            yaAbrio = true
            if (compartirAlAbrir) compartir = true
            if (arreglarAlAbrir) arreglando = pubs.firstOrNull { it.estado == "error" }
        }
    }

    /** Fijar o desfijar, con aviso y deshacer. */
    fun fijar(pr: Propiedad) {
        val nueva = !(pr.destacada ?: false)
        p = p?.copy(destacada = nueva)
        Haptico.seleccion()
        alcance.launch {
            try {
                estado.cola.enviar("PATCH", "/v1/propiedades/${pr.id}", CuerpoDestacada(nueva), etiqueta = "Fijar ${pr.nombre}")
                estado.version += 1
                estado.avisar(if (nueva) "Fijada en el lateral" else "Desfijada") {
                    estado.tarea {
                        try { estado.api.hacer("PATCH", "/v1/propiedades/${pr.id}", CuerpoDestacada(!nueva)); estado.version += 1 }
                        catch (e: CancellationException) { throw e } catch (e: Exception) { estado.avisar(e) }
                    }
                }
            } catch (e: CancellationException) { throw e } catch (e: Exception) { p = p?.copy(destacada = !nueva); estado.avisar(e) }
        }
    }

    suspend fun ponerEstado(pr: Propiedad, nuevo: String) {
        val antes = pr.estado ?: "disponible"
        try {
            estado.cola.enviar("PATCH", "/v1/propiedades/${pr.id}", CuerpoEstadoPropiedad(nuevo), etiqueta = "Estado de ${pr.nombre}")
            estado.version += 1
            estado.avisar("Quedó ${Propiedad.estados.firstOrNull { it.first == nuevo }?.second?.lowercase(Formato.argentina) ?: nuevo}") {
                estado.tarea {
                    try { estado.api.hacer("PATCH", "/v1/propiedades/${pr.id}", CuerpoEstadoPropiedad(antes)); estado.version += 1 }
                    catch (e: CancellationException) { throw e } catch (e: Exception) { estado.avisar(e) }
                }
            }
        } catch (e: CancellationException) { throw e } catch (e: Exception) { estado.avisar(e) }
    }

    /** Publicar en un portal (o en la web), con deshacer: saca la publicación (o la despublica). */
    suspend fun publicar(portal: String, pr: Propiedad) {
        portalEnUso = portal
        val nombre = PublicacionPortal.nombres[portal] ?: portal
        try {
            if (portal == "web") estado.api.hacer("PATCH", "/v1/propiedades/${pr.id}", CuerpoPublicada(true))
            else estado.api.hacer("POST", "/v1/propiedades/${pr.id}/publicaciones/$portal")
            estado.version += 1
            estado.avisar("Publicada en $nombre") {
                estado.tarea {
                    try {
                        if (portal == "web") estado.api.hacer("PATCH", "/v1/propiedades/${pr.id}", CuerpoPublicada(false))
                        else estado.api.hacer("DELETE", "/v1/propiedades/${pr.id}/publicaciones/$portal")
                        estado.version += 1
                    } catch (e: CancellationException) { throw e } catch (e: Exception) { estado.avisar(e) }
                }
            }
        } catch (e: CancellationException) { throw e } catch (e: Exception) { estado.avisar(e) } finally { portalEnUso = null }
    }

    suspend fun despublicarWeb(pr: Propiedad) {
        try {
            estado.api.hacer("PATCH", "/v1/propiedades/${pr.id}", CuerpoPublicada(false))
            estado.version += 1
            estado.avisar("Despublicada de tu web") {
                estado.tarea {
                    try { estado.api.hacer("PATCH", "/v1/propiedades/${pr.id}", CuerpoPublicada(true)); estado.version += 1 }
                    catch (e: CancellationException) { throw e } catch (e: Exception) { estado.avisar(e) }
                }
            }
        } catch (e: CancellationException) { throw e } catch (e: Exception) { estado.avisar(e) }
    }

    suspend fun pausar(portal: String) {
        portalEnUso = portal
        try {
            estado.api.hacer("POST", "/v1/propiedades/$id/publicaciones/$portal/pausar")
            estado.version += 1
            estado.avisar("Pausada en ${PublicacionPortal.nombres[portal] ?: portal}") {
                estado.tarea {
                    try { estado.api.hacer("POST", "/v1/propiedades/$id/publicaciones/$portal"); estado.version += 1 }
                    catch (e: CancellationException) { throw e } catch (e: Exception) { estado.avisar(e) }
                }
            }
        } catch (e: CancellationException) { throw e } catch (e: Exception) { estado.avisar(e) } finally { portalEnUso = null }
    }

    suspend fun sacarDe(portal: String) {
        portalEnUso = portal
        try {
            estado.api.hacer("DELETE", "/v1/propiedades/$id/publicaciones/$portal")
            estado.version += 1
            estado.avisar("Sacada de ${PublicacionPortal.nombres[portal] ?: portal}") {
                estado.tarea {
                    try { estado.api.hacer("POST", "/v1/propiedades/$id/publicaciones/$portal"); estado.version += 1 }
                    catch (e: CancellationException) { throw e } catch (e: Exception) { estado.avisar(e) }
                }
            }
        } catch (e: CancellationException) { throw e } catch (e: Exception) { estado.avisar(e) } finally { portalEnUso = null }
    }

    suspend fun duplicar(pr: Propiedad) {
        try {
            val n: PropiedadNueva = estado.api.enviar("POST", "/v1/propiedades/${pr.id}/duplicar", emptyMap<String, String>())
            estado.version += 1
            estado.sugerir("Duplicada sin publicar", "Ver", AFichaPropiedad(id = n.id))
        } catch (e: CancellationException) { throw e } catch (e: Exception) { estado.avisar(e) }
    }

    /** A la papelera al toque; «Deshacer» la restaura de la papelera (la web hace lo mismo). */
    suspend fun borrar(pr: Propiedad) {
        try {
            estado.api.hacer("DELETE", "/v1/propiedades/${pr.id}")
            estado.version += 1
            cerrar()
            val pid = pr.id
            estado.avisar("${(pr.direccion ?: pr.nombre).split(",").firstOrNull() ?: pr.nombre} fue a la papelera") {
                estado.tarea {
                    try {
                        estado.api.hacer("POST", "/v1/papelera/propiedad/$pid/restaurar")
                        estado.version += 1
                        estado.abrir(AFichaPropiedad(id = pid))
                    } catch (e: CancellationException) { throw e } catch (e: Exception) { estado.avisar(e) }
                }
            }
        } catch (e: CancellationException) { throw e } catch (e: Exception) { estado.avisar(e) }
    }

    suspend fun confirmarVisita(v: Cita, pr: Propiedad) {
        var tel: String? = null
        v.contactoId?.let { c -> tel = estado.api.opcional<Contacto>("/v1/contactos/$c")?.telefono }
        Afuera.whatsapp(tel, texto = "Te confirmo la visita ${Senal.cuando(v.empiezaEn)} en ${pr.direccion ?: pr.nombre}.")
    }

    fun abrirPersona(x: PersonaFicha) {
        val c = x.contactoId
        if (c != null) estado.abrir(AFichaContacto(id = c)) else estado.abrir(AFichaOperacion(id = x.id))
    }

    LaunchedEffect(estado.version) { cargar() }

    BarraDe(oculta = true)

    Box(Modifier.fillMaxSize()) {
        FondoApp()
        val pv = p
        val ev = error
        if (pv != null) {
            Refrescable({ cargar() }, Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    HeroeFicha(pv, alSumarFotos = { if (estado.escribe) estado.abrir(AEditarEnPaso(id = pv.id, paso = "fotos")) }) { i ->
                        estado.abrir(AGaleria(id = pv.id, indice = i))
                    }
                    Column(
                        Modifier.fillMaxWidth().offset(y = (-22).dp).clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)).background(C.fondo)
                            .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 120.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        // Cabeza
                        CabezaFicha(pv, senal, lugar(pv), lineaPrecio(pv), cargada(pv, senal))
                        proximaVisita?.let { v ->
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(M.rTarjeta)).background(C.avisoFondo).padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Icono("calendar", 20f, C.avisoTinta)
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                    Texto("Visita ${Senal.cuando(v.empiezaEn)}", Estilo.subheadFuerte, C.texto)
                                    Texto(v.contactoNombre ?: v.titulo ?: "", Estilo.footnote, C.texto2)
                                }
                                BotonTonal("Confirmar", { alcance.launch { confirmarVisita(v, pv) } })
                            }
                        }
                        val ds = datosFicha(pv)
                        if (ds.isNotEmpty()) CuatroDatos(ds)
                        if (!(pv.comodidades ?: emptyList()).isEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.weight(1f)) { TituloBloque("Comodidades") }
                                    if (estado.escribe) BotonLinkFicha("Editar", Estilo.subheadFuerte, C.accion) { estado.abrir(AEditarEnPaso(id = pv.id, paso = "comodidades")) }
                                }
                                ChipsComodidades(pv.comodidades ?: emptyList())
                            }
                        }
                        pv.descripcion?.nonEmpty?.let { Texto(it, Estilo.body16, C.texto, lineas = 8) }
                        if (contratos.isNotEmpty()) {
                            // Su contrato vigente, para no tener que buscarlo en otra pestaña.
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                TituloBloque("Contrato")
                                Column(Modifier.fillMaxWidth().tarjeta()) {
                                    contratos.forEachIndexed { i, k ->
                                        val det = listOfNotNull(
                                            k.monto?.let { Formato.plata(it, k.moneda ?: "ARS") },
                                            k.fin?.let { Fecha.dia(it) }?.let { "hasta ${Formato.mesDe(it)} ${it.atZone(Formato.zona).year}" },
                                        ).joinToString(" · ")
                                        FilaPersonaFicha("Contrato con ${k.contactoNombre ?: "el inquilino"}", det, Tono.neutro, primera = i == 0) { estado.abrir(AFichaContrato(id = k.id)) }
                                    }
                                }
                            }
                        }
                        // Dónde está publicada
                        val filas = portales(pv)
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            TituloBloque("Dónde está publicada", cuenta = Formato.cantidad(semana, "vista", "vistas") + " esta semana")
                            Texto("Publicada en ${filas.count { it.estado == "ok" }} de 4", Estilo.footnote, C.texto2, Modifier.padding(horizontal = 4.dp))
                            Column(Modifier.fillMaxWidth().tarjeta()) {
                                filas.forEachIndexed { i, x ->
                                    val opciones = buildList {
                                        if (estado.escribe && x.estado == "ok") {
                                            if (x.clave == "web") {
                                                add(OpcionDeMenu("Despublicar de tu web", "eye.slash") { alcance.launch { despublicarWeb(pv) } })
                                            } else {
                                                add(OpcionDeMenu("Pausar en ${x.nombre}", "pause.circle") { alcance.launch { pausar(x.clave) } })
                                                add(OpcionDeMenu("Sacar de ${x.nombre}", "xmark.circle", destructiva = true) { alcance.launch { sacarDe(x.clave) } })
                                            }
                                        }
                                        x.pub?.url?.let { u -> add(OpcionDeMenu("Ver el aviso", "safari") { abrirUrl(ctx, u) }) }
                                    }
                                    MenuContextual(opciones, alTocar = {}) {
                                        Column {
                                            if (i > 0) Box(Modifier.fillMaxWidth().height(0.5.dp).background(C.linea))
                                            Row(
                                                Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 14.dp, vertical = 10.dp),
                                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                                            ) {
                                                LogoPortal(x.clave, Modifier.size(32.dp))
                                                Column(Modifier.weight(1f)) {
                                                    Texto(x.nombre, Estilo.filaLista, C.texto)
                                                    Texto(x.detalle, Estilo.footnote, if (x.estado == "err") C.errorTinta else C.texto2, lineas = 2)
                                                }
                                                if (portalEnUso == x.clave) Rueda() else if (!lector) {
                                                    when {
                                                        x.estado == "ok" && x.clave == "web" -> BotonLinkFicha("Copiar link", Estilo.footnoteFuerte, C.accion) {
                                                            copiar(ctx, link); estado.avisar("Link copiado. Pegalo donde quieras mandarlo.")
                                                        }
                                                        x.estado == "ok" && x.clave == "mercadolibre" && (senal?.sinResponder ?: 0) > 0 ->
                                                            BotonLinkFicha("Responder", Estilo.footnoteFuerte, C.accion) { estado.abrir(AMercadoLibre()) }
                                                        x.estado == "ok" -> Icono("checkmark.circle.fill", 22f, C.okTinta)
                                                        x.estado == "err" -> Box(
                                                            Modifier.height(30.dp).clip(CircleShape).background(C.errorFondo).clickable { arreglando = x.pub }.padding(horizontal = 10.dp),
                                                            contentAlignment = Alignment.Center,
                                                        ) { Texto("Arreglar", Estilo(13f, Peso.bold), C.errorTinta) }
                                                        else -> BotonTonal("Publicar", { alcance.launch { publicar(x.clave, pv) } }, alto = 30.dp)
                                                    }
                                                } else if (x.estado == "ok") {
                                                    Icono("checkmark.circle.fill", 22f, C.okTinta)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        // Vistas por día
                        val b = barras
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.weight(1f)) { TituloBloque("Vistas por día") }
                                subaVistas?.let { s -> Texto("+$s${Formato.fino}% desde que bajó el precio", Estilo.footnoteFuerte, C.okTinta) }
                            }
                            Column(Modifier.fillMaxWidth().tarjeta().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    Modifier.fillMaxWidth().height(90.dp).semantics {
                                        contentDescription = "Vistas en la web de los últimos 13 días: ${Formato.cantidad(b.dias.sumOf { it.second }, "vista", "vistas")}"
                                    },
                                    verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    b.dias.forEachIndexed { i, d ->
                                        if (i == b.corte && i > 0) Box(Modifier.width(2.dp).height(90.dp).background(C.oro))
                                        Box(
                                            Modifier.weight(1f).height(maxOf(4f, d.second.toFloat() / b.max * 90f).dp).clip(RoundedCornerShape(3.dp))
                                                .background(if (b.corte > 0 && i >= b.corte) C.okTinta else C.accion.copy(alpha = 0.55f)),
                                        )
                                    }
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    val chico = Estilo(12f, Peso.regular)
                                    Texto("hace 2 semanas", chico, C.texto3)
                                    Spacer(Modifier.weight(1f))
                                    val l = lineaPrecio(pv)
                                    if (b.corte > 0 && l != null) {
                                        Texto(if (l.startsWith("bajó")) "bajó a ${pv.precioTexto}" else "subió a ${pv.precioTexto}", chico, C.avisoTinta)
                                        Spacer(Modifier.weight(1f))
                                    }
                                    Texto("hoy", chico, C.texto3)
                                }
                            }
                            BotonLinkFicha("Ver las vistas de 90 días", Estilo.footnoteFuerte, C.accion, Modifier.padding(horizontal = 4.dp)) {
                                estado.abrir(ASeccion(clave = "vistas", id = pv.id))
                            }
                        }
                        if (lector) {
                            Row(Modifier.fillMaxWidth().tarjeta().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                IconoTono("lock", Tono.neutro)
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Texto("Tu rol es Lector", Estilo.subheadFuerte, C.texto)
                                    Texto(Formato.sinViudas("Podés verla y compartirla, pero el precio y el estado los cambia el agente o la dueña."), Estilo.footnote, C.texto2)
                                }
                            }
                        }
                        // La gente
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ChipFiltro(texto = "Interesados${if (abiertas.isEmpty()) "" else " ${abiertas.size}"}", activo = pestana == "interesados") { pestana = "interesados" }
                                ChipFiltro(texto = "Visitas${if (visitasProp.isEmpty()) "" else " ${visitasProp.size}"}", activo = pestana == "visitas") { pestana = "visitas" }
                                ChipFiltro(texto = "Reservas${if (reservas.isEmpty()) "" else " ${reservas.size}"}", activo = pestana == "reservas") { pestana = "reservas" }
                                ChipFiltro(texto = "Docs${if (documentos == 0) "" else " $documentos"}", activo = pestana == "docs") { pestana = "docs" }
                                ChipFiltro(texto = "Historial", activo = pestana == "historial") { pestana = "historial" }
                            }
                            Column(Modifier.fillMaxWidth().tarjeta()) {
                                when (pestana) {
                                    "visitas" -> if (visitasProp.isEmpty()) {
                                        VaciaFicha("Sin visitas todavía.", if (estado.escribe) "Agendá la primera" to { agendar = true } else null)
                                    } else {
                                        visitasProp.forEachIndexed { i, c ->
                                            FilaPersonaFicha(
                                                c.contactoNombre ?: c.titulo ?: "Visita",
                                                "${if (c.estado == "hecha") "Visitó" else "Visita"} ${Senal.cuando(c.empiezaEn)}${c.notas?.let { " · «$it»" } ?: ""}",
                                                Tono.neutro, primera = i == 0,
                                            ) { estado.abrir(ACita(id = c.id)) }
                                        }
                                    }
                                    "reservas" -> if (reservas.isEmpty()) {
                                        VaciaFicha("Sin reservas.", if (estado.escribe) "Reservar" to { reservar = true } else null)
                                    } else {
                                        reservas.forEachIndexed { i, r ->
                                            Column(Modifier.fillMaxWidth().clickable { estado.abrir(AReserva(id = r.id)) }) {
                                                if (i > 0) Box(Modifier.fillMaxWidth().height(0.5.dp).background(C.linea))
                                                FilaReserva(r)
                                            }
                                        }
                                    }
                                    "docs" -> FilaPersonaFicha(
                                        "Documentos", if (documentos == 0) "Autorización, escritura, planos" else Formato.cantidad(documentos, "documento", "documentos"),
                                        Tono.neutro, primera = true,
                                    ) { estado.abrir(ASeccion(clave = "documentos-propiedad", id = pv.id)) }
                                    "historial" -> if (historial.isEmpty()) VaciaFicha("Sin movimientos todavía.") else {
                                        historial.forEachIndexed { i, h ->
                                            Column(Modifier.fillMaxWidth()) {
                                                if (i > 0) Box(Modifier.fillMaxWidth().height(0.5.dp).background(C.linea))
                                                Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                                    Texto(RegistroActividad.acciones[h.accion] ?: h.accion, Estilo.filaLista, C.texto)
                                                    Texto("${h.actorNombre ?: "Corredor"} · ${h.creadoEn?.let { Formato.hace(it) } ?: ""}", Estilo.footnote, C.texto2)
                                                }
                                            }
                                        }
                                    }
                                    else -> {
                                        val ps = personas
                                        if (ps.isEmpty()) VaciaFicha("Todavía nadie preguntó. Las consultas aparecen acá.")
                                        (if (todos) ps else ps.take(3)).forEachIndexed { i, x ->
                                            Column(Modifier.fillMaxWidth()) {
                                                if (i > 0) Box(Modifier.fillMaxWidth().height(0.5.dp).background(C.linea))
                                                Row(
                                                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                                                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                                                ) {
                                                    AvatarIniciales(x.nombre, tam = 36.dp)
                                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                                        Texto(x.nombre, Estilo.filaLista, C.texto)
                                                        Texto(
                                                            x.detalle, Estilo(13f, if (x.tono == Tono.neutro) Peso.regular else Peso.semibold),
                                                            if (x.tono == Tono.neutro) C.texto2 else x.tono.tinta, lineas = 2,
                                                        )
                                                    }
                                                    if (x.responder) {
                                                        BotonTonal("Responder", { abrirPersona(x) }, alto = 30.dp)
                                                    } else {
                                                        Tocable({ abrirPersona(x) }, descripcion = "Escribirle a ${x.nombre}") {
                                                            Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(C.hundida), contentAlignment = Alignment.Center) {
                                                                Icono("bubble.left", 17f, C.accion)
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                        if (buscan.isNotEmpty()) {
                                            Box(Modifier.fillMaxWidth().height(0.5.dp).background(C.linea))
                                            Row(
                                                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                                            ) {
                                                IconoTono("sparkles", Tono.violeta, tam = 36.dp)
                                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                                    Texto(Formato.cantidad(buscan.size, "contacto busca algo así", "contactos buscan algo así"), Estilo.filaLista, C.texto)
                                                    Texto(resumenBusca(pv), Estilo.footnote, C.texto2)
                                                }
                                                BotonTonal("Mandarles", { compartir = true }, alto = 30.dp)
                                            }
                                        }
                                        if (ps.size > 3) {
                                            Box(Modifier.fillMaxWidth().height(0.5.dp).background(C.linea))
                                            Box(Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable { todos = !todos }, contentAlignment = Alignment.Center) {
                                                Texto(if (todos) "Ver menos" else "Ver los ${ps.size}", Estilo.subheadFuerte, C.accion)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        // Propietario
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            TituloBloque("Propietario")
                            Column(Modifier.fillMaxWidth().tarjeta()) {
                                val d = propietario
                                if (d != null) {
                                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        AvatarIniciales(d.nombre, tam = 36.dp)
                                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                            Texto(d.nombre, Estilo.filaLista, C.texto)
                                            Texto(d.telefono ?: d.email ?: "Sin teléfono cargado", Estilo.footnote, C.texto2)
                                        }
                                        Tocable({ if (d.telefono != null) Afuera.whatsapp(d.telefono) else estado.abrir(AFichaContacto(id = d.id)) }, descripcion = "Escribirle a ${d.nombre}") {
                                            Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(C.hundida), contentAlignment = Alignment.Center) {
                                                Icono("bubble.left", 17f, C.accion)
                                            }
                                        }
                                    }
                                    Row(
                                        Modifier.padding(start = 10.dp, end = 10.dp, bottom = 10.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(C.azulFondo).padding(12.dp),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    ) {
                                        Icono("doc.text.magnifyingglass", 17f, C.azulTinta)
                                        Texto("Mandale el resumen de la semana: $resumenSemana.", Estilo.footnote, C.texto, Modifier.weight(1f))
                                        BotonLinkFicha("Mandar", Estilo.footnoteFuerte, C.azulTinta) {
                                            Afuera.whatsapp(d.telefono, texto = "Hola ${d.nombre.split(" ").firstOrNull() ?: d.nombre}, te paso cómo vino ${pv.direccion ?: pv.nombre} esta semana: $resumenSemana. $link")
                                        }
                                    }
                                } else {
                                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Texto("Sin propietario cargado.", Estilo.footnote, C.texto2)
                                        if (estado.escribe) BotonLinkFicha("Sumalo desde la ficha", Estilo.footnoteFuerte, C.accion) { estado.abrir(AAltaPropiedad(editar = pv.id)) }
                                        Spacer(Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                        // Más
                        if (estado.escribe) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                TituloBloque("Más")
                                Column(Modifier.fillMaxWidth().tarjeta()) {
                                    FilaAccionFicha("arrow.triangle.2.circlepath", "Cambiar el estado") { cambiarEstado = true }
                                    if (pv.operacion == "temporario" || pv.tambienTemporario == true) {
                                        FilaAccionFicha("calendar.badge.clock", "Tarifas y disponibilidad") { estado.abrir(ASeccion(clave = "tarifas", id = pv.id)) }
                                    }
                                    FilaAccionFicha("paperplane", "A quién se la mandaste") { estado.abrir(ASeccion(clave = "envios", id = pv.id)) }
                                    FilaAccionFicha("waveform", "Recorridas dictadas") { estado.abrir(ASeccion(clave = "grabaciones", id = pv.id)) }
                                    FilaAccionFicha("plus.square.on.square", "Duplicar") { alcance.launch { duplicar(pv) } }
                                    FilaAccionFicha("trash", "Mandar a la papelera", rojo = true) { alcance.launch { borrar(pv) } }
                                }
                            }
                            Confirmacion(
                                cambiarEstado, "Estado de la propiedad",
                                opciones = Propiedad.estados.map { (k, n) -> OpcionConfirmacion(n) { alcance.launch { ponerEstado(pv, k) } } },
                                alCerrar = { cambiarEstado = false }, verTitulo = false,
                            )
                        }
                    }
                }
            }
            // Arriba
            Row(Modifier.align(Alignment.TopCenter).fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CirculoFicha("chevron.left", "Volver") { cerrar() }
                Spacer(Modifier.weight(1f))
                if (estado.escribe) {
                    CirculoFicha("pencil", "Editar") { estado.abrir(AAltaPropiedad(editar = pv.id)) }
                    CirculoFicha(if (pv.destacada == true) "pin.fill" else "pin", if (pv.destacada == true) "Desfijar" else "Fijar en el lateral") { fijar(pv) }
                }
                CirculoFicha("square.and.arrow.up", "Compartir") { compartir = true }
                MenuBoton(buildList {
                    if (estado.escribe) {
                        add(OpcionDeMenu("Editar", "pencil") { estado.abrir(AAltaPropiedad(editar = pv.id)) })
                        if (pv.operacion != "temporario") add(OpcionDeMenu("Reservar", "bookmark") { reservar = true })
                        add(OpcionDeMenu(if (pv.destacada == true) "Desfijar" else "Fijar en el lateral", "pin") { fijar(pv) })
                    }
                    add(OpcionDeMenu("Inventario y actas", "checklist") { estado.abrir(ASeccion(clave = "actas", id = pv.id)) })
                    senal?.reserva?.id?.let { r -> add(OpcionDeMenu("Reserva", "bookmark.fill") { estado.abrir(AReserva(id = r)) }) }
                    add(OpcionDeMenu("Ficha para imprimir", "printer") {
                        alcance.launch { FichaImpresa.a4(pv, api = estado.api)?.let { previo = ArchivoPrevio(it) } }
                    })
                    add(OpcionDeMenu("Tarjeta para la visita", "rectangle.portrait.on.rectangle.portrait") { estado.abrir(ATarjetaPropiedad(id = pv.id)) })
                    pv.barrio?.let { b -> add(OpcionDeMenu("Más en $b", "mappin.and.ellipse") { estado.abrir(ACartera(barrio = b)) }) }
                    if (estado.escribe) {
                        add(OpcionDeMenu("Duplicar", "plus.square.on.square") { alcance.launch { duplicar(pv) } })
                        add(OpcionDeMenu("Mandar a la papelera", "trash", destructiva = true) { alcance.launch { borrar(pv) } })
                    }
                }, "Más") {
                    Box(Modifier.size(40.dp).clip(CircleShape).background(VeloCirculo), contentAlignment = Alignment.Center) {
                        Icono("ellipsis", 18f, Color.White)
                    }
                }
            }
            // La barra al pulgar
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(C.fondo.copy(alpha = 0.94f)).navigationBarsPadding()) {
                Box(Modifier.fillMaxWidth().height(0.5.dp).background(C.linea))
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Tocable({ compartir = true }, Modifier.weight(1f)) {
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(14.dp)).background(hex(0x25D366)),
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (!LogoDeLaApp("logo_whatsapp", Modifier.size(20.dp), tinte = hex(0x0B3D1F))) Icono("bubble.left.fill", 18f, hex(0x0B3D1F))
                            Texto("Mandar a un interesado", Estilo(16f, Peso.bold), hex(0x0B3D1F), lineas = 1, escalaMinima = 0.85f)
                        }
                    }
                    if (estado.escribe) CuadradoFicha("calendar", "Agendar visita") { agendar = true }
                    propietario?.telefono?.let { t -> CuadradoFicha("phone", "Llamar al propietario") { Afuera.llamar(t) } }
                    if (estado.escribe) CuadradoFicha("camera", "Sumar fotos") { estado.abrir(AEditarEnPaso(id = pv.id, paso = "fotos")) }
                }
            }
        } else if (ev != null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { EstadoError("la propiedad", ev) { alcance.launch { cargar() } } }
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Rueda() }
        }
    }

    // Las hojas
    val pv = p
    Hoja(compartir, { compartir = false }, alturas = listOf(Altura.media, Altura.grande)) {
        if (pv != null) HojaCompartir(pv, web, interesados = buscan, destinatario = destinatario)
    }
    Hoja(agendar, { agendar = false }) { if (pv != null) NuevaCita(propiedad = pv) }
    Hoja(reservar, { reservar = false }) {
        if (pv != null) {
            NuevaReservaVenta(
                propiedadId = pv.id, titulo = (pv.direccion ?: pv.nombre).split(",").firstOrNull() ?: pv.nombre, moneda = pv.moneda ?: "USD",
            ) { r -> estado.abrir(AReserva(id = r.id)) }
        }
    }
    Hoja(arreglando != null, { arreglando = null }, alturas = listOf(Altura.media, Altura.grande)) {
        val x = arreglando
        if (pv != null && x != null) HojaArreglar(pv, x, otrosPortales(x.portal))
    }
    VistaPrevia(previo) { previo = null }
}

private val VeloCirculo = Color(0.08f, 0.08f, 0.08f, 0.6f)

private fun lugar(p: Propiedad): String =
    listOfNotNull(p.barrio, p.ciudad?.takeIf { it != p.barrio && it != "CABA" }).joinToString(" · ")

/** «LP-0412 · cargada por Ana el 12/10». */
private fun cargada(p: Propiedad, senal: Senal?): String {
    val fecha = p.creadaEn?.let { f -> val z = f.atZone(Formato.zona); "%02d/%02d".format(z.dayOfMonth, z.monthValue) } ?: ""
    val por = senal?.cargadaPor?.let { "por ${it.split(" ").firstOrNull() ?: it} " } ?: ""
    return listOfNotNull(senal?.codigo, "cargada ${por}el $fecha").joinToString(" · ")
}

private fun resumenBusca(p: Propiedad): String = listOfNotNull(
    p.barrio, p.ambientes?.let { "$it amb" },
    p.precio?.let { "hasta ${Formato.plata(ceil((it * 1.05) / 1000) * 1000, p.moneda ?: "USD")}" },
).joinToString(", ")

/** Los cuatro datos con su detalle: «3 amb. · 2 dormitorios», «120 m² · 95 cubiertos», «2 baños · 1 cochera», «A estrenar · al frente». */
private fun datosFicha(p: Propiedad): List<Triple<String, String, String>> {
    val d = mutableListOf<Triple<String, String, String>>()
    p.ambientes?.takeIf { it > 0 }?.let { a -> d.add(Triple("door.left.hand.open", "$a amb.", p.dormitorios?.let { Formato.cantidad(it, "dormitorio", "dormitorios") } ?: "")) }
    (p.superficie ?: p.cubierta)?.let { s ->
        val det = if (p.superficie != null && p.cubierta != null) "${Formato.numero((p.cubierta ?: 0.0).toInt())} cubiertos" else if (p.superficie != null) "totales" else "cubiertos"
        d.add(Triple("ruler", "${Formato.numero(s.toInt())} m²", det))
    }
    p.banos?.takeIf { it > 0 }?.let { b -> d.add(Triple("bathtub", Formato.cantidad(b, "baño", "baños"), p.cocheras?.let { Formato.cantidad(it, "cochera", "cocheras") } ?: "")) }
    p.antiguedad?.let { a ->
        val disp = p.ficha?.get("unidad")?.get("disposicion")?.texto?.let { "al $it" } ?: ""
        d.add(Triple("calendar", if (a == 0) "A estrenar" else "$a ${if (a == 1) "año" else "años"}", disp))
    }
    return d
}

@Composable
private fun HeroeFicha(p: Propiedad, alSumarFotos: () -> Unit, alTocar: (Int) -> Unit) {
    val fotos = p.fotosUrl ?: emptyList()
    if (fotos.isEmpty()) {
        Tocable(alSumarFotos, Modifier.fillMaxWidth()) {
            Column(
                Modifier.fillMaxWidth().height(300.dp).background(C.hundida),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
            ) {
                Icono("camera.badge.plus", 34f, C.accion)
                Texto("Sumá fotos", Estilo.subheadFuerte, C.accion)
                Texto("Sin fotos no sale en los portales", Estilo.footnote, C.texto2)
            }
        }
    } else {
        val paginas = rememberPagerState { fotos.size }
        Box(Modifier.fillMaxWidth().height(380.dp).semantics { contentDescription = "Ver las fotos" }) {
            HorizontalPager(paginas, Modifier.fillMaxSize()) { i ->
                Tocable({ alTocar(i) }, Modifier.fillMaxSize()) { Foto(url = fotos[i], modifier = Modifier.fillMaxSize()) }
            }
            Box(
                Modifier.matchParentSize().background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.35f), 0.3f to Color.Transparent, 0.7f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.35f),
                    ),
                ),
            )
            if (fotos.size > 1) {
                Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 30.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    fotos.indices.forEach { i ->
                        Box(Modifier.size(7.dp).clip(CircleShape).background(Color.White.copy(alpha = if (i == paginas.currentPage) 1f else 0.4f)))
                    }
                }
            }
            Row(
                Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 34.dp).height(26.dp).clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Icono("photo.on.rectangle", 13f, Color.White)
                Texto("${paginas.currentPage + 1} / ${fotos.size}", Estilo(13f, Peso.semibold), Color.White)
            }
        }
    }
}

@Composable
private fun CabezaFicha(p: Propiedad, senal: Senal?, lugar: String, lineaPrecio: String?, cargada: String) {
    val (est, tono) = Cartera.estado(p)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.height(24.dp).clip(CircleShape).background(tono.fondo).padding(horizontal = 9.dp), contentAlignment = Alignment.Center) {
                Texto(est, Estilo(12f, Peso.bold), tono.tinta)
            }
            Texto("${if (p.operacion == "temporario") "Temporario" else Formato.mayuscula(p.operacionTexto)} · ${p.tipoTexto}", Estilo.footnote, C.texto2)
        }
        Cartera.atencion(p, senal)?.let { a -> if (a.second != Tono.neutro) Texto(a.first, Estilo.footnoteFuerte, a.second.tinta) }
        Texto(p.direccion ?: p.nombre, Estilo.titulo3, C.texto)
        lugar.nonEmpty?.let { Texto(it, Estilo.subhead, C.texto2) }
        Texto(p.precioTexto, Estilo(30f, Peso.extrabold, -0.03f), C.texto)
        p.expensas?.takeIf { it > 0 }?.let { Texto("expensas ${Formato.plata(it, "ARS")}", Estilo.footnote, C.texto2) }
        lineaPrecio?.let { Texto(it, Estilo.footnoteFuerte, C.okTinta) }
        Texto(cargada, Estilo(12f, Peso.regular), C.texto3)
    }
}

@Composable
private fun CuatroDatos(datos: List<Triple<String, String, String>>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        datos.chunked(2).forEach { par ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                par.forEach { (icono, valor, detalle) ->
                    Row(
                        Modifier.weight(1f).heightIn(min = 58.dp).tarjeta(14.dp).padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(Modifier.width(24.dp), contentAlignment = Alignment.Center) { Icono(icono, 19f, C.okTinta) }
                        Column(Modifier.weight(1f)) {
                            Texto(valor, Estilo.subheadFuerte, C.texto, lineas = 1, escalaMinima = 0.8f)
                            if (detalle.isNotEmpty()) Texto(detalle, Estilo(12f, Peso.regular), C.texto2, lineas = 1)
                        }
                    }
                }
                if (par.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** Un botón de texto suelto (Button("…").estilo(…).foregroundStyle(…)). */
@Composable
private fun BotonLinkFicha(texto: String, estilo: Estilo, color: Color, modifier: Modifier = Modifier, hacer: () -> Unit) {
    Box(modifier.clickable(onClick = hacer).padding(vertical = 4.dp)) { Texto(texto, estilo, color) }
}

@Composable
private fun VaciaFicha(t: String, accion: Pair<String, () -> Unit>? = null) {
    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Texto(t, Estilo.footnote, C.texto2)
        accion?.let { BotonLinkFicha(it.first, Estilo.footnoteFuerte, C.accion) { it.second() } }
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun FilaPersonaFicha(t: String, d: String, tono: Tono, primera: Boolean, ir: () -> Unit) {
    Tocable(ir, Modifier.fillMaxWidth()) {
        Column {
            if (!primera) Box(Modifier.fillMaxWidth().height(0.5.dp).background(C.linea))
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AvatarIniciales(t, tam = 36.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Texto(t, Estilo.filaLista, C.texto, lineas = 1)
                    Texto(d, Estilo.footnote, if (tono == Tono.neutro) C.texto2 else tono.tinta, lineas = 2)
                }
                Chevron()
            }
        }
    }
}

@Composable
private fun CirculoFicha(icono: String, etiqueta: String, hacer: () -> Unit) {
    Tocable(hacer, descripcion = etiqueta) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(VeloCirculo), contentAlignment = Alignment.Center) {
            Icono(icono, 18f, Color.White)
        }
    }
}

@Composable
private fun CuadradoFicha(icono: String, etiqueta: String, hacer: () -> Unit) {
    Tocable(hacer, descripcion = etiqueta) {
        Box(Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(C.hundida), contentAlignment = Alignment.Center) {
            Icono(icono, 19f, C.texto)
        }
    }
}

@Composable
private fun FilaAccionFicha(icono: String, texto: String, rojo: Boolean = false, hacer: () -> Unit) {
    Tocable(hacer, Modifier.fillMaxWidth()) {
        Column {
            Box(Modifier.fillMaxWidth().height(0.5.dp).background(C.linea))
            Row(Modifier.fillMaxWidth().heightIn(min = 50.dp).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.width(24.dp), contentAlignment = Alignment.Center) { Icono(icono, 19f, if (rojo) C.error else C.accion) }
                Texto(texto, Estilo.body16, if (rojo) C.error else C.texto, Modifier.weight(1f))
                if (!rojo) Chevron()
            }
        }
    }
}

/** Chips que bajan de renglón (las comodidades). */
@Composable
fun ChipsComodidades(textos: List<String>) {
    FlujoLayout(espacio = 6.dp) {
        textos.forEach { t ->
            Box(
                Modifier.height(30.dp).clip(CircleShape).background(C.superficie).border(1.dp, C.linea2, CircleShape).padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center,
            ) { Texto(t, Estilo(13f, Peso.semibold), C.texto) }
        }
    }
}

/** Un layout que acomoda las vistas de izquierda a derecha y baja de renglón cuando no entran. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FlujoLayout(espacio: Dp = 6.dp, modifier: Modifier = Modifier, contenido: @Composable () -> Unit) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(espacio), verticalArrangement = Arrangement.spacedBy(espacio)) { contenido() }
}

