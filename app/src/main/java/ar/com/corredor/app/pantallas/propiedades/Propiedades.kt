package ar.com.corredor.app.pantallas.propiedades

import ar.com.corredor.app.pantallas.mira.Ubicacion

import ar.com.corredor.app.app.PantallaApilada
import ar.com.corredor.app.app.VistaConsejo
import ar.com.corredor.app.app.ConsejoMandar

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import ar.com.corredor.app.R
import ar.com.corredor.app.app.LocalEstado
import ar.com.corredor.app.nucleo.*
import ar.com.corredor.app.pantallas.agenda.NuevaCita
import ar.com.corredor.app.pantallas.cartera.Contacto
import ar.com.corredor.app.pantallas.emprendimientos.EmprendimientoAPI
import ar.com.corredor.app.tema.*
import coil3.compose.AsyncImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.time.Instant
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sinh

// Clon de Pantallas/Propiedades/Propiedades.swift.

/** La ficha con la hoja de «Arreglar» abierta (el error de un portal, 9C·err; la web: `?arreglar=`). */
@Serializable data class AArreglarPropiedad(val id: String)
/** La ficha con la hoja de compartir abierta; con `contacto`, para mandársela a esa persona (el «Mandar» de la ficha de
 *  un contacto; la web: `?compartir=whatsapp&contacto=<id>`). */
@Serializable data class AMandarPropiedad(val id: String, val contacto: String? = null)
/** La edición de una propiedad abierta en un paso («fotos», «comodidades», «portales»; la web: `?paso=`). */
@Serializable data class AEditarEnPaso(val id: String, val paso: String)
/** Una reserva de venta o alquiler (la web: /reservas/[id]). */
@Serializable data class AReserva(val id: String)
/** Una reserva de temporario: el check-in (la web: /temporada/reservas/[id]). */
@Serializable data class AReservaTemporada(val id: String)
/** Mercado Libre (32E): preguntas, errores explicados y publicaciones. Sin campos en iOS (`AMercadoLibre()`); el
 *  parámetro es sólo para que sea un data class comparable. */
@Serializable data class AMercadoLibre(val nada: Int = 0)
/** La tarjeta de 85 × 55 mm de una propiedad, para dejar en la visita. */
@Serializable data class ATarjetaPropiedad(val id: String)
/** La cartera filtrada por barrio («Más en Belgrano», desde la ficha). */
@Serializable data class ACartera(val barrio: String)

// `destinosPropiedades()` de iOS: lo mapea app/Registro.kt.
//   AFichaPropiedad      → FichaPropiedad(id = f.id)
//   AArreglarPropiedad   → FichaPropiedad(id = f.id, arreglarAlAbrir = true)
//   AMandarPropiedad     → FichaPropiedad(id = f.id, compartirAlAbrir = true, compartirCon = f.contacto)
//   AGaleria             → Galeria(id = g.id, indice = g.indice)
//   AAltaPropiedad       → AltaPropiedad(editar = a.editar, borrador = a.borrador)
//   AEditarEnPaso        → AltaPropiedad(editar = a.id, enPaso = a.paso)
//   AReserva             → PantallaReserva(id = r.id)
//   AReservaTemporada    → CheckinTemporada(id = r.id)
//   AMercadoLibre        → PantallaMercadoLibre()
//   ATarjetaPropiedad    → TarjetaVisita(id = t.id)
//   ACartera             → PropiedadesRaiz(inicial = FiltrosCartera(barrio = listOf(c.barrio)), titulo = "Más en ${c.barrio}")

/** 3A–3C Propiedades (la cartera, web 02 · 8E/8C): lista o mapa, con las vistas que son tareas arriba (con su cuenta),
 *  los filtros en la API, el orden «por lo que necesita atención», la selección de varias para acciones en lote y las
 *  vistas guardadas. `titulo`: apilada (no como pestaña), con la barra de navegación y este título. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PropiedadesRaiz(inicial: FiltrosCartera? = null, titulo: String? = null) {
    val estado = LocalEstado.current
    val ctx = LocalContext.current
    var vista by remember { mutableStateOf(0) }
    var filtros by remember { mutableStateOf(inicial ?: FiltrosCartera()) }
    var busqueda by remember { mutableStateOf("") }
    var props by remember { mutableStateOf<List<Propiedad>?>(null) }
    var senales by remember { mutableStateOf<Map<String, Senal>>(emptyMap()) }
    var resumen by remember { mutableStateOf(ResumenCartera()) }
    var equipo by remember { mutableStateOf<List<Miembro>>(emptyList()) }
    var emprendimientos by remember { mutableStateOf<List<EmprendimientoAPI>>(emptyList()) }
    var error by remember { mutableStateOf<ErrorApi?>(null) }
    /** La hora de la copia guardada (cuando no se pudo traer la cartera). */
    var copiaEn by remember { mutableStateOf<Instant?>(null) }
    var viendoCopia by remember { mutableStateOf(false) }
    var web by remember { mutableStateOf<String?>(null) }
    var compartir by remember { mutableStateOf<Propiedad?>(null) }
    /** «Agendar visita» de una fila: la nueva visita con esa propiedad ya puesta. */
    var agendar by remember { mutableStateOf<Propiedad?>(null) }
    var guardadas by remember { mutableStateOf(VistaGuardada.todas()) }
    var seleccionando by remember { mutableStateOf(false) }
    var marcadas by remember { mutableStateOf<Set<String>>(emptySet()) }
    var hojaFiltros by remember { mutableStateOf(false) }
    var hojaPrecioVarias by remember { mutableStateOf(false) }
    var hojaMandarVarias by remember { mutableStateOf(false) }
    var hojaGuardar by remember { mutableStateOf(false) }
    var nombreVista by remember { mutableStateOf("") }
    var masAbierto by remember { mutableStateOf(false) }
    var exportando by remember { mutableStateOf<File?>(null) }
    /** El «Borrar la vista» (el contextMenu del chip de una vista guardada). */
    var borrarDe by remember { mutableStateOf<VistaGuardada?>(null) }

    val esDuenioOAdmin = estado.rol == "duenio" || estado.rol == "admin"

    // MARK: Lista

    /** Las filas, ordenadas por lo que necesita atención (y después por la última cambiada). */
    val filas: List<Propiedad>? = props?.let { l ->
        val ahora = Instant.now()
        l.map { it to Cartera.prioridad(it, senales[it.id], ahora) }
            .sortedWith { a, b ->
                if (a.second != b.second) b.second.compareTo(a.second)
                else (b.first.actualizadoEn ?: Instant.MIN).compareTo(a.first.actualizadoEn ?: Instant.MIN)
            }
            .map { it.first }
    }

    suspend fun cargar() {
        try {
            val q = filtros.query + listOf("unidades" to "no", "orden" to "actualizada")
            val l: List<Propiedad> = estado.api.todas("/v1/propiedades", q)
            props = l
            error = null
            viendoCopia = false
            estado.api.opcional<ResumenCartera>("/v1/propiedades/resumen")?.let { resumen = it }
            // Las señales no tiran la cartera: si no vienen, la lista se ve igual.
            try { senales = Senal.de(l.map { it.id }, estado.api) } catch (e: CancellationException) { throw e } catch (e: Exception) {}
            if (filtros.vacio) {
                // Spotlight.indexar(propiedades:) no tiene equivalente en Android.
                Guardado.guardar(l, "propiedades")
                Guardado.guardar(senales, "propiedades-senales")
            }
            if (equipo.isEmpty()) estado.api.opcional<List<Miembro>>("/v1/equipo")?.let { equipo = it }
            estado.api.opcional<List<EmprendimientoAPI>>("/v1/emprendimientos")?.let { emprendimientos = it }
            if (web == null) web = estado.api.opcional<Inmobiliaria>("/v1/inmobiliaria")?.web
        } catch (e: CancellationException) {
            throw e
        } catch (e: ErrorApi) {
            error = e
            copiaEn = Guardado.leer<List<Propiedad>>("propiedades")?.second
        } catch (e: Exception) {
        }
    }

    LaunchedEffect(estado.carteraDe) {
        val a = estado.carteraDe
        if (titulo == null && a != null) { filtros = filtros.copy(agente = a); estado.carteraDe = null }
    }
    LaunchedEffect(estado.version, filtros) { cargar() }
    LaunchedEffect(busqueda) {
        delay(300)
        if (busqueda.trim() != filtros.q) filtros = filtros.copy(q = busqueda.trim())
    }

    fun marcar(id: String) {
        Haptico.seleccion()
        marcadas = if (id in marcadas) marcadas - id else marcadas + id
    }

    fun agenteDe(p: Propiedad): String? {
        val a = p.agenteId ?: return null
        return equipo.firstOrNull { it.usuarioId == a }?.completo ?: "Agente"
    }

    fun elegidas(): List<Propiedad> = (props ?: emptyList()).filter { it.id in marcadas }

    fun asignar(m: Miembro) {
        val ps = elegidas()
        // El cuerpo manda `agenteId` aunque sea null (la API lo saca del agente).
        fun cuerpo(agenteId: String?) = buildJsonObject { put("agenteId", JsonPrimitive(agenteId)) }
        estado.tarea {
            try {
                for (p in ps) estado.api.hacer("PATCH", "/v1/propiedades/${p.id}", cuerpo(m.usuarioId))
                marcadas = emptySet()
                seleccionando = false
                estado.version += 1
                val nombre = m.completo.split(" ").firstOrNull() ?: m.completo
                estado.avisar("${if (ps.size == 1) "Asignada" else "Asignadas"} a $nombre") {
                    estado.tarea {
                        try {
                            for (p in ps) estado.api.hacer("PATCH", "/v1/propiedades/${p.id}", cuerpo(p.agenteId))
                            estado.version += 1
                        } catch (e: CancellationException) { throw e } catch (e: Exception) { estado.avisar(e) }
                    }
                }
            } catch (e: CancellationException) { throw e } catch (e: Exception) { estado.avisar(e) }
        }
    }

    @Serializable data class CuerpoPublicada(val publicada: Boolean)

    fun despublicarVarias() {
        val ps = elegidas()
        estado.tarea {
            try {
                for (p in ps) estado.api.hacer("PATCH", "/v1/propiedades/${p.id}", CuerpoPublicada(false))
                marcadas = emptySet()
                seleccionando = false
                estado.version += 1
                estado.avisar(if (ps.size == 1) "Despublicada de tu web" else "Despublicadas de tu web") {
                    estado.tarea {
                        try {
                            for (p in ps) estado.api.hacer("PATCH", "/v1/propiedades/${p.id}", CuerpoPublicada(p.publicada ?: true))
                            estado.version += 1
                        } catch (e: CancellationException) { throw e } catch (e: Exception) { estado.avisar(e) }
                    }
                }
            } catch (e: CancellationException) { throw e } catch (e: Exception) { estado.avisar(e) }
        }
    }

    /** Cambia el precio en % (redondeo a la centena), con deshacer (vuelve a poner cada propiedad como estaba). */
    suspend fun cambiarPrecios(pct: Double) {
        val ids = marcadas.toList()
        val viejas = mutableListOf<Propiedad>()
        try {
            for (id in ids) {
                val p: Propiedad = estado.api.get("/v1/propiedades/$id")
                val precio = p.precio ?: continue
                viejas.add(p)
                val nueva = p.copy(precio = redondear((precio * (1 + pct / 100)) / 100) * 100)
                estado.api.hacer("PUT", "/v1/propiedades/$id", nueva)
            }
            marcadas = emptySet()
            seleccionando = false
            estado.version += 1
            val n = viejas.size
            val verbo = if (pct < 0) (if (n == 1) "bajó" else "bajaron") else (if (n == 1) "subió" else "subieron")
            estado.avisar("${Formato.cantidad(n, "precio", "precios")} $verbo ${Formato.porcentaje(abs(pct))}") {
                estado.tarea {
                    try {
                        for (p in viejas) estado.api.hacer("PUT", "/v1/propiedades/${p.id}", p)
                        estado.version += 1
                    } catch (e: CancellationException) { throw e } catch (e: Exception) { estado.avisar(e) }
                }
            }
        } catch (e: CancellationException) { throw e } catch (e: Exception) { estado.avisar(e) }
    }

    /** Un mensaje de WhatsApp con todas las elegidas y sus links. */
    fun mandarA(c: Contacto) {
        val ps = elegidas()
        val base = web ?: ""
        val primero = c.nombre.split(" ").firstOrNull() ?: c.nombre
        val texto = "Hola $primero, te paso ${if (ps.size == 1) "una propiedad que te puede interesar" else "unas propiedades que te pueden interesar"}:\n" +
            ps.joinToString("\n\n") { "${it.direccion ?: it.nombre} · ${it.precioTexto}\n$base/propiedades/${it.slug ?: it.id}" }
        Afuera.whatsapp(c.telefono, texto)
        hojaMandarVarias = false
        marcadas = emptySet()
        seleccionando = false
    }

    // MARK: Vistas guardadas, copia y exportar

    fun guardarVista(nombre: String) {
        val n = nombre.trim()
        if (n.isEmpty()) return
        guardadas = guardadas.filter { it.nombre != n } + VistaGuardada(n, filtros)
        VistaGuardada.guardar(guardadas)
        estado.avisar("Vista «$n» guardada")
    }

    fun borrarVista(g: VistaGuardada) {
        val antes = guardadas
        guardadas = guardadas.filter { it.nombre != g.nombre }
        VistaGuardada.guardar(guardadas)
        estado.avisar("Vista «${g.nombre}» borrada") {
            guardadas = antes
            VistaGuardada.guardar(antes)
        }
    }

    fun verCopia() {
        val (l, _) = Guardado.leer<List<Propiedad>>("propiedades") ?: return
        props = l
        senales = Guardado.leer<Map<String, Senal>>("propiedades-senales")?.first ?: emptyMap()
        viendoCopia = true
    }

    suspend fun exportar() {
        try {
            val d = estado.api.pedir("GET", "/v1/exportar/propiedades", listOf("formato" to "xlsx"))
            val u = archivoParaCompartir(ctx, "propiedades-${Formato.diaISO(Instant.now())}.xlsx")
            withContext(Dispatchers.IO) { u.writeBytes(d) }
            exportando = u
        } catch (e: CancellationException) { throw e } catch (e: Exception) { estado.avisar(e) }
    }

    /** Publicar o despublicar en la web (como el panel): despublicada queda como borrador. */
    fun publicar(p: Propiedad) {
        val nuevo = p.publicada == false
        estado.tarea {
            try {
                estado.cola.enviar("PATCH", "/v1/propiedades/${p.id}", CuerpoPublicada(nuevo), etiqueta = "${if (nuevo) "Publicar" else "Despublicar"} ${p.nombre}")
                estado.version += 1
                estado.avisar(if (nuevo) "Publicada en tu web" else "Despublicada: quedó como borrador") {
                    estado.tarea {
                        try {
                            estado.api.hacer("PATCH", "/v1/propiedades/${p.id}", CuerpoPublicada(!nuevo))
                            estado.version += 1
                        } catch (e: CancellationException) { throw e } catch (e: Exception) { estado.avisar(e) }
                    }
                }
            } catch (e: CancellationException) { throw e } catch (e: Exception) { estado.avisar(e) }
        }
    }

    // MARK: Cabecera, vistas y filtros

    val acciones: List<AccionCabecera> = buildList {
        add(AccionCabecera(icono = if (vista == 1) "list.bullet" else "map", etiqueta = if (vista == 1) "Ver la lista" else "Ver el mapa") { vista = if (vista == 1) 0 else 1 })
        if (estado.escribe) {
            add(AccionCabecera(icono = "viewfinder", etiqueta = "Captar con MIRA") { estado.miraAbierta = true })
            add(AccionCabecera(icono = "plus.circle", etiqueta = "Cargar propiedad") { estado.abrir(AAltaPropiedad()) })
            add(AccionCabecera(icono = if (seleccionando) "checkmark.circle.fill" else "checkmark.circle", etiqueta = if (seleccionando) "Terminar la selección" else "Seleccionar") {
                seleccionando = !seleccionando
                if (!seleccionando) marcadas = emptySet()
            })
        }
        add(AccionCabecera(icono = "ellipsis.circle", etiqueta = "Más") { masAbierto = true })
    }

    /** Los filtros puestos, que se sacan con un toque. */
    val puestos: List<Pair<String, String>> = buildList {
        val ops = Propiedad.operaciones.associate { it.first to (if (it.first == "temporario") "Temporario" else it.second) }
        val tipos = Propiedad.tipos.associate { it.first to it.second }
        val ests = Propiedad.estados.associate { it.first to it.second }
        if (filtros.operacion.size > 1) add("operacion" to filtros.operacion.joinToString(", ") { ops[it] ?: it })
        if (filtros.tipo.isNotEmpty()) add("tipo" to filtros.tipo.joinToString(", ") { tipos[it] ?: it })
        if (filtros.estado.isNotEmpty()) add("estado" to filtros.estado.joinToString(", ") { ests[it] ?: it })
        if (filtros.barrio.isNotEmpty()) add("barrio" to filtros.barrio.joinToString(", "))
        if (filtros.precioDesde != null || filtros.precioHasta != null) {
            add("precio" to listOfNotNull(
                filtros.precioDesde?.toIntOrNull()?.let { "desde ${Formato.numero(it)}" },
                filtros.precioHasta?.toIntOrNull()?.let { "hasta ${Formato.numero(it)}" },
            ).joinToString(" "))
        }
        val a = filtros.agente
        if (a != null && a != "yo" && a != "libre") add("agente" to (equipo.firstOrNull { it.usuarioId == a }?.completo ?: "Agente"))
    }

    fun sacar(k: String) {
        filtros = when (k) {
            "operacion" -> filtros.copy(operacion = emptyList())
            "tipo" -> filtros.copy(tipo = emptyList())
            "estado" -> filtros.copy(estado = emptyList())
            "barrio" -> filtros.copy(barrio = emptyList())
            "precio" -> filtros.copy(precioDesde = null, precioHasta = null)
            else -> filtros.copy(agente = null)
        }
    }

    @Composable
    fun chipsVistas() {
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (resumen.conError > 0 || filtros.portal != null) {
                ChipFiltro("${resumen.conError} con error", filtros.portal == "error") { filtros = filtros.copy(portal = if (filtros.portal == null) "error" else null) }
            }
            if (resumen.sinPublicar > 0 || filtros.publicada != null) {
                ChipFiltro(Formato.cantidad(resumen.sinPublicar, "borrador", "borradores"), filtros.publicada == "no") { filtros = filtros.copy(publicada = if (filtros.publicada == null) "no" else null) }
            }
            if (resumen.sinVistas > 0 || filtros.sinVistas != null) {
                ChipFiltro("Sin visitas hace 30 días · ${resumen.sinVistas}", filtros.sinVistas != null) { filtros = filtros.copy(sinVistas = if (filtros.sinVistas == null) "30" else null) }
            }
            listOf("venta" to "Venta", "alquiler" to "Alquiler", "temporario" to "Temporario").forEach { (k, n) ->
                ChipFiltro(n, filtros.operacion == listOf(k)) { filtros = filtros.copy(operacion = if (filtros.operacion == listOf(k)) emptyList() else listOf(k)) }
            }
            if (resumen.destacadas > 0 || filtros.destacada) {
                ChipFiltro("Fijadas · ${resumen.destacadas}", filtros.destacada) { filtros = filtros.copy(destacada = !filtros.destacada) }
            }
            guardadas.forEach { g ->
                ChipFiltro(g.nombre, filtros == g.filtros, alMantener = { borrarDe = g }) { filtros = g.filtros; busqueda = g.filtros.q }
            }
            ChipFiltro("Filtros", false) { hojaFiltros = true }
        }
    }

    @Composable
    fun chipsPuestos() {
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            puestos.forEach { (k, t) ->
                Tocable({ sacar(k) }, descripcion = "Sacar el filtro $t") {
                    Row(
                        Modifier.height(32.dp).clip(Capsula).background(C.accion).padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Texto(t, Estilo(13f, Peso.semibold), C.sobreAccion, lineas = 1)
                        Icono("xmark", 11f, C.sobreAccion)
                    }
                }
            }
            Tocable({ filtros = FiltrosCartera(); busqueda = "" }) { Texto("Sacar los filtros", Estilo.footnoteFuerte, C.accion) }
        }
    }

    @Composable
    fun encabezado() {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CabeceraPestana(titulo = "Propiedades", acciones = acciones)
            Buscador("Calle, barrio o código", busqueda, { busqueda = it }, Modifier.padding(horizontal = 16.dp))
            Segmentado(
                listOf("yo" to "Mías · ${resumen.mias}", "libre" to "Sin agente · ${resumen.sinAgente}", "todas" to "Todas"),
                if (filtros.agente == "yo" || filtros.agente == "libre") filtros.agente!! else "todas",
                { filtros = filtros.copy(agente = if (it == "todas") null else it) },
                Modifier.padding(horizontal = 16.dp),
            )
            chipsVistas()
            if (puestos.isNotEmpty()) chipsPuestos()
        }
    }

    @Composable
    fun vacio() {
        if (resumen.total == 0 && filtros.vacio) {
            // El VStack de 12 del listado, donde iOS apila el vacío y el «Importar de Excel».
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                EstadoVacio(
                    icono = "building.2", titulo = "Así se va a ver tu primera propiedad",
                    texto = "Sacale una foto al cartel o cargala a mano.",
                    accion = if (estado.escribe) ("Captar con MIRA" to { estado.miraAbierta = true }) else null,
                    secundaria = if (estado.escribe) ("Cargar a mano" to { estado.abrir(AAltaPropiedad()) }) else null,
                )
                if (estado.escribe) BotonTexto("Importar de Excel", { estado.abrir(ASeccion(clave = "importar")) }, Modifier.fillMaxWidth())
            }
        } else {
            EstadoVacio(
                icono = "line.3.horizontal.decrease.circle", titulo = "Ninguna propiedad con estos filtros",
                texto = "Probá sacar alguno o buscá por calle, barrio o código.",
                accion = "Sacar los filtros" to { filtros = FiltrosCartera(); busqueda = "" },
            )
        }
    }

    @Composable
    fun errorCarga(e: ErrorApi) {
        EstadoVacio(
            icono = "exclamationmark.triangle", titulo = "No pudimos traer la cartera",
            texto = copiaEn?.let { "Te mostramos la copia de las ${Formato.horaDe(it)}. Lo que cambies se guarda y se manda al volver." }
                ?: (if (e.sinConexion) "No hay internet. Lo que cargaste está a salvo." else "Puede ser la conexión o un problema nuestro. Lo que cargaste está a salvo."),
            accion = "Reintentar" to { estado.tarea { cargar() } },
            secundaria = if (copiaEn != null) ("Ver la copia" to { verCopia() }) else null,
        )
    }

    /** Las fijadas (destacadas), con lo que pasa en cada una. */
    @Composable
    fun fijadas() {
        val ds = (props ?: emptyList()).filter { it.destacada == true }.take(3)
        if (ds.isEmpty()) return
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TituloBloque(texto = "Fijadas")
            Column(Modifier.fillMaxWidth().tarjeta()) {
                ds.forEachIndexed { i, p ->
                    val (detalle, tono) = fijadaPropiedad(p, senales[p.id])
                    if (i > 0) Box(Modifier.fillMaxWidth().height(0.5.dp).background(C.linea))
                    Tocable({ estado.abrir(AFichaPropiedad(id = p.id)) }) {
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 14.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Foto(p.miniatura, Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                Texto((p.direccion ?: p.nombre).split(",").firstOrNull() ?: p.nombre, Estilo.filaLista, C.texto, lineas = 1)
                                if (detalle.isNotEmpty()) Texto(detalle, Estilo.footnote, tono?.tinta ?: C.texto2)
                            }
                            Chevron()
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun filaAcceso(t: String, i: String, cuenta: String, primera: Boolean = false, hacer: () -> Unit) {
        Column {
            if (!primera) Box(Modifier.fillMaxWidth().height(0.5.dp).background(C.linea))
            Tocable(hacer) {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconoTono(icono = i, tono = Tono.verde, tam = 34.dp)
                    Texto(t, Estilo.filaLista, C.texto, Modifier.weight(1f))
                    Texto(cuenta, Estilo.subhead, C.texto2)
                    Chevron()
                }
            }
        }
    }

    /** Los accesos a Venta, Alquiler, Temporario y Emprendimientos (con la cuenta de unidades). */
    @Composable
    fun accesos() {
        val unidades = emprendimientos.sumOf { it.unidades?.total ?: 0 }
        Column(Modifier.padding(horizontal = 16.dp).padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TituloBloque(texto = "Secciones")
            Column(Modifier.fillMaxWidth().tarjeta()) {
                filaAcceso("Venta", "tag", "${resumen.venta}", primera = true) { filtros = filtros.copy(operacion = listOf("venta")) }
                filaAcceso("Alquiler", "key", "${resumen.alquiler}") { filtros = filtros.copy(operacion = listOf("alquiler")) }
                filaAcceso("Temporario", "beach.umbrella", "${resumen.temporario}") { estado.abrir(ASeccion(clave = "temporada")) }
                filaAcceso("Emprendimientos", "building", if (unidades > 0) "${emprendimientos.size} · $unidades unidades" else "${emprendimientos.size}") {
                    estado.abrir(ASeccion(clave = "emprendimientos"))
                }
            }
        }
    }

    // MARK: Fila

    @Composable
    fun fila(p: Propiedad) {
        val s = senales[p.id]
        val mandar = AccionFila("Mandar", "square.and.arrow.up", hex(0x25D366)) { compartir = p }
        val derecha: List<AccionFila> = if (estado.escribe && !seleccionando) listOf(
            AccionFila(
                if (p.publicada == false) "Publicar" else "Despublicar", if (p.publicada == false) "arrow.up.circle" else "eye.slash", hex(0x8E8E93),
            ) { publicar(p) },
            mandar,
        ) else if (seleccionando) emptyList() else listOf(mandar)
        val menu: List<OpcionDeMenu> = buildList {
            add(OpcionDeMenu("Mandar la ficha", "square.and.arrow.up") { compartir = p })
            if (estado.escribe) {
                add(OpcionDeMenu("Agendar visita", "calendar.badge.plus") { agendar = p })
                add(OpcionDeMenu("Editar", "pencil") { estado.abrir(AAltaPropiedad(editar = p.id)) })
                add(OpcionDeMenu(if (p.publicada == false) "Publicar" else "Despublicar", if (p.publicada == false) "arrow.up.circle" else "eye.slash") { publicar(p) })
                add(OpcionDeMenu("Seleccionar", "checkmark.circle") { seleccionando = true; marcar(p.id) })
            }
        }
        val forma = RoundedCornerShape(M.rTarjeta)
        Box(Modifier.fillMaxWidth().e2(forma).clip(forma)) {
            FilaDeslizable(derecha = derecha, anchoAccion = 75.dp) {
                Box {
                    MenuContextual(menu, alTocar = { if (seleccionando) marcar(p.id) else estado.abrir(AFichaPropiedad(id = p.id)) }) {
                        FilaCartera(p = p, s = s, agente = agenteDe(p), marcada = if (seleccionando) p.id in marcadas else null)
                    }
                    if (!seleccionando) {
                        Row(Modifier.align(Alignment.BottomEnd).padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (s?.error != null && estado.escribe) {
                                Tocable({ estado.abrir(AArreglarPropiedad(id = p.id)) }) {
                                    Box(Modifier.height(30.dp).clip(Capsula).background(C.errorFondo).padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
                                        Texto("Arreglar", Estilo(13f, Peso.bold), C.errorTinta)
                                    }
                                }
                            }
                            BotonMandar { compartir = p }
                        }
                    }
                }
            }
        }
    }

    // MARK: Selección (acciones en lote)

    @Composable
    fun botoneraSeleccion() {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Texto(
                if (marcadas.isEmpty()) "Tocá las propiedades para elegirlas" else Formato.cantidad(marcadas.size, "seleccionada", "seleccionadas"),
                Estilo.subheadFuerte, C.texto,
            )
            Row(Modifier.horizontalScroll(rememberScrollState()).deshabilitado(marcadas.isEmpty()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (estado.esDuenio) {
                    MenuBoton(equipo.map { m -> OpcionDeMenu(m.completo, "") { asignar(m) } }, "Asignar agente") {
                        EtiquetaTonal("Asignar agente", "person.badge.plus")
                    }
                }
                BotonTonal("Cambiar precio", { hojaPrecioVarias = true }, simbolo = "tag")
                BotonTonal("Mandar a un interesado", { hojaMandarVarias = true }, simbolo = "square.and.arrow.up")
                BotonTonal("Despublicar", { despublicarVarias() }, simbolo = "eye.slash")
                Tocable({ marcadas = emptySet(); seleccionando = false }, descripcion = "Deseleccionar") { EtiquetaTonal("", "xmark") }
            }
        }
    }

    @Composable
    fun listado(abajo: Dp) {
        Refrescable({ cargar() }, Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp + abajo), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                encabezado()
                val err = error
                val lista = filas
                if (err != null && !viendoCopia) {
                    Box(Modifier.padding(top = 20.dp)) { errorCarga(err) }
                } else if (lista != null) {
                    val ce = copiaEn
                    if (viendoCopia && ce != null) {
                        Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icono("icloud.slash", 13f, C.texto2)
                            Texto("Copia de las ${Formato.horaDe(ce)}.", Estilo.footnote, C.texto2)
                            Tocable({ estado.tarea { cargar() } }) { Texto("Reintentar", Estilo.footnoteFuerte, C.accion) }
                        }
                    }
                    if (lista.isEmpty()) {
                        Box(Modifier.padding(top = 30.dp)) { vacio() }
                    } else {
                        Texto(
                            "${Formato.numero(lista.size)} de ${Formato.numero(resumen.total)} · ordenadas por lo que necesita atención",
                            Estilo.footnote, C.texto2, Modifier.padding(horizontal = 20.dp),
                        )
                        if (filtros.vacio && !viendoCopia) fijadas()
                        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            VistaConsejo(ConsejoMandar())
                            lista.forEach { p -> fila(p) }
                            Texto(
                                "Tocá el logo de WhatsApp para mandarla; mantené apretada una fila para más",
                                Estilo.footnote, C.texto3, Modifier.fillMaxWidth().padding(vertical = 6.dp), alinear = TextAlign.Center,
                            )
                            Texto(
                                "W web · ML Mercado Libre · ZP Zonaprop · AP Argenprop · tachado = no publicada",
                                Estilo.footnote, C.texto3, Modifier.fillMaxWidth(), alinear = TextAlign.Center,
                            )
                        }
                        if (filtros.vacio) accesos()
                    }
                } else {
                    EsqueletoFilas(filas = 5, miniatura = 92.dp, modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        }
    }

    // MARK: Cuerpo

    // .pantallaApilada(titulo) si viene apilada (ACartera: «Más en Palermo»), o .pantallaRaiz() en la pestaña.
    var altoBotonera by remember { mutableStateOf(0.dp) }
    val densidad = LocalDensity.current
    val cuerpo: @Composable BoxScope.() -> Unit = {
        val lista = filas
        if (vista == 1 && lista != null && lista.isNotEmpty()) {
            // El mapa no va dentro del scroll: ocupa todo el alto que queda (hasta la barra de pestañas).
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                encabezado()
                MapaCartera(
                    props = lista, senales = senales, titulo = filtros.barrio.firstOrNull() ?: "Tu cartera",
                    compartir = { compartir = it },
                    modifier = Modifier.weight(1f).padding(horizontal = 16.dp).padding(bottom = 12.dp + (if (seleccionando) altoBotonera else 0.dp)),
                )
            }
        } else {
            listado(if (seleccionando) altoBotonera else 0.dp)
        }
        if (seleccionando) {
            Box(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .onSizeChanged { altoBotonera = with(densidad) { it.height.toDp() } }
                    .background(C.fondo.copy(alpha = 0.94f)).navigationBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 8.dp),
                contentAlignment = Alignment.Center,
            ) { botoneraSeleccion() }
        }
    }
    if (titulo != null) PantallaApilada(titulo, contenido = cuerpo) else Box(Modifier.fillMaxSize()) { FondoApp(); cuerpo() }

    // MARK: Hojas, alertas y menú

    val compartirP = compartir
    Hoja(visible = compartirP != null, alCerrar = { compartir = null }, alturas = listOf(Altura.media, Altura.grande)) {
        if (compartirP != null) HojaCompartir(propiedad = compartirP, web = web)
    }
    val agendarP = agendar
    Hoja(visible = agendarP != null, alCerrar = { agendar = null }) {
        if (agendarP != null) NuevaCita(propiedad = agendarP)
    }
    Hoja(visible = hojaFiltros, alCerrar = { hojaFiltros = false }) {
        HojaFiltrosCartera(filtros = filtros, alCambiar = { filtros = it }, resumen = resumen, equipo = equipo, conAgentes = esDuenioOAdmin)
    }
    Hoja(visible = hojaPrecioVarias, alCerrar = { hojaPrecioVarias = false }, alturas = listOf(Altura.media)) {
        HojaPrecioVarias(cuantas = marcadas.size) { pct -> estado.tarea { cambiarPrecios(pct) } }
    }
    Hoja(visible = hojaMandarVarias, alCerrar = { hojaMandarVarias = false }) {
        HojaMandarVarias { c -> mandarA(c) }
    }
    val x = exportando
    Hoja(visible = x != null, alCerrar = { exportando = null }, altoFijo = 160.dp) {
        if (x != null) {
            Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                BotonTexto("Compartir ${x.name}", {
                    compartirArchivo(ctx, x, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                }, simbolo = "square.and.arrow.up")
            }
        }
    }
    AlertaConCampo(
        visible = hojaGuardar, titulo = "Guardar esta vista", mensaje = "Queda arriba, con las demás, en este teléfono.",
        texto = nombreVista, alCambiar = { nombreVista = it }, placeholder = "Belgrano en venta",
        cancelar = { hojaGuardar = false; nombreVista = "" }, boton = "Guardar",
        aceptar = { guardarVista(nombreVista); nombreVista = ""; hojaGuardar = false },
    )
    Confirmacion(
        visible = masAbierto, titulo = "Propiedades",
        opciones = buildList {
            add(OpcionConfirmacion("Descargar en Excel") { estado.tarea { exportar() } })
            if (!filtros.vacio) add(OpcionConfirmacion("Guardar esta vista") { hojaGuardar = true })
            if (estado.escribe) add(OpcionConfirmacion("Importar de Excel") { estado.abrir(ASeccion(clave = "importar")) })
        },
        alCerrar = { masAbierto = false }, volver = "Cancelar", verTitulo = false,
    )
    val borrarG = borrarDe
    Confirmacion(
        visible = borrarG != null, titulo = borrarG?.nombre ?: "",
        opciones = listOf(OpcionConfirmacion("Borrar la vista", destructiva = true) { borrarG?.let { borrarVista(it) } }),
        alCerrar = { borrarDe = null }, volver = "Cancelar", verTitulo = false,
    )
}

/** «Fijadas»: lo que pasa en cada una (`PropiedadesRaiz.fijada` en iOS). */
fun fijadaPropiedad(p: Propiedad, s: Senal?): Pair<String, Tono?> {
    s?.proximaVisita?.let { v -> return "visita ${Senal.cuando(v)}" to Tono.oro }
    s?.venceReserva?.let { v ->
        val d = diasCalendario(Instant.now(), v)
        return "reserva vence ${if (d <= 0) "hoy" else if (d == 1) "mañana" else "en $d días"}" to Tono.rojo
    }
    if (s?.error != null) return "error en un portal" to Tono.rojo
    val n = s?.interesados
    if (n != null && n > 0) return "$n interesados" to null
    return (p.barrio ?: "") to null
}

/** El chip de estado de cada fila (3A): lo más importante que le pasa a la propiedad (`PropiedadesRaiz.chip` en iOS). */
fun chipPropiedad(p: Propiedad, s: Senal?): Pair<String, Tono> {
    Cartera.atencion(p, s)?.let { a -> return a.first to (if (a.second == Tono.neutro) Tono.verde else a.second) }
    return Cartera.estado(p)
}

/** `.rounded()` de Swift: a la mitad, lejos del cero. */
private fun redondear(x: Double): Double = if (x >= 0) floor(x + 0.5) else ceil(x - 0.5)

/** Una tarjeta de la cartera: foto, precio con su nota, dirección, el resumen (o «captada con MIRA»), las siglas de los
 *  portales, la línea de atención, los interesados y el agente. `marcada`: en la selección, si está marcada (null fuera
 *  de la selección). */
@Composable
fun FilaCartera(p: Propiedad, s: Senal? = null, agente: String? = null, marcada: Boolean? = null) {
    val nota = Cartera.notaPrecio(p, s)
    Row(Modifier.fillMaxWidth().background(C.superficie).padding(10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.size(92.dp).clip(RoundedCornerShape(13.dp))) {
            Foto(p.miniatura, Modifier.fillMaxSize())
            if (marcada != null) {
                Box(Modifier.padding(6.dp).clip(CircleShape).background(Color.Black.copy(alpha = if (marcada) 0f else 0.25f))) {
                    Icono(if (marcada) "checkmark.circle.fill" else "circle", 24f, if (marcada) C.accion else Color.White)
                }
            }
        }
        Column(Modifier.weight(1f).padding(top = 2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Texto(p.precioTexto, Estilo(17f, Peso.bold, -0.01f), C.texto, Modifier.alignBy(FirstBaseline))
                if (nota.first.isNotEmpty()) Texto(nota.first, Estilo.footnote, if (nota.second == Tono.neutro) C.texto2 else nota.second.tinta, Modifier.alignBy(FirstBaseline), lineas = 1)
            }
            Texto(p.direccion ?: p.nombre, Estilo(15f, Peso.medium), C.texto, lineas = 1)
            Texto(
                if (p.deMira) listOf(p.resumen, "captada con MIRA").filter { it.isNotEmpty() }.joinToString(" · ") else p.resumen,
                Estilo.footnote, C.texto2, lineas = 2,
            )
            Box(Modifier.padding(top = 2.dp)) { SiglasPortales(p, s) }
            val a = Cartera.atencion(p, s)
            if (a != null) {
                Row(
                    Modifier.padding(end = 44.dp).padding(top = 2.dp).height(24.dp).clip(Capsula).background(a.second.fondo).padding(horizontal = 9.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically,
                ) {
                    a.third?.let { Icono(it, 12f, a.second.tinta) }
                    Texto(a.first, Estilo(12f, Peso.bold), a.second.tinta, lineas = 1)
                }
            } else {
                val (e, t) = Cartera.estado(p)
                Box(Modifier.padding(top = 2.dp).height(24.dp).clip(Capsula).background(t.fondo).padding(horizontal = 9.dp), contentAlignment = Alignment.Center) {
                    Texto(e, Estilo(12f, Peso.bold), t.tinta)
                }
            }
            val inter = Cartera.interesados(s)
            Texto(
                listOf("Interesados: ${inter.first}", agente?.let { it.split(" ").firstOrNull() ?: it } ?: "Asignar").joinToString(" · "),
                Estilo(12f, if (inter.second) Peso.bold else Peso.regular), if (inter.second) C.avisoTinta else C.texto3,
                Modifier.padding(end = 44.dp), lineas = 1,
            )
        }
    }
}

@Composable
private fun SiglasPortales(p: Propiedad, s: Senal?) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        PortalesCartera.todos.forEach { pt ->
            val e = PortalesCartera.estado(p, s, pt.clave)
            val tinta = if (e == "ok") C.okTinta else if (e == "err") C.errorTinta else C.texto3
            val fondo = if (e == "ok") C.okFondo else if (e == "err") C.errorFondo else C.hundida
            Box(
                Modifier.height(18.dp).clip(RoundedCornerShape(5.dp)).background(fondo).padding(horizontal = 5.dp)
                    .semantics { contentDescription = "${pt.nombre}: ${if (e == "ok") "publicada" else if (e == "err") "con error" else "no publicada"}" },
                contentAlignment = Alignment.Center,
            ) {
                BasicText(
                    pt.sigla,
                    style = Estilo(11f, Peso.bold).ts().copy(color = tinta, textDecoration = if (e == "off") TextDecoration.LineThrough else null),
                )
            }
        }
    }
}

/** Los filtros de la cartera: operación, tipo, estado, barrio (con la cuenta), precio y agente. En iOS edita un Binding
 *  en vivo; acá cada toque llama a `alCambiar`. */
@Composable
fun HojaFiltrosCartera(filtros: FiltrosCartera, alCambiar: (FiltrosCartera) -> Unit, resumen: ResumenCartera, equipo: List<Miembro>, conAgentes: Boolean) {
    val cerrar = LocalCerrarHoja.current
    var desde by remember { mutableStateOf(filtros.precioDesde ?: "") }
    var hasta by remember { mutableStateOf(filtros.precioHasta ?: "") }

    @Composable
    fun marca(texto: String, puesta: Boolean, hacer: () -> Unit) {
        Tocable(hacer, hundir = false) {
            Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Texto(texto, Estilo.body, C.texto, Modifier.weight(1f))
                if (puesta) Icono("checkmark", 17f, C.accion)
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        BarraHoja(
            titulo = "Filtros",
            cancelar = "Sacar los filtros", alCancelar = { alCambiar(FiltrosCartera(q = filtros.q)); desde = ""; hasta = "" },
            confirmar = "Filtrar", alConfirmar = {
                alCambiar(filtros.copy(precioDesde = desde.filter { it.isDigit() }.nonEmpty, precioHasta = hasta.filter { it.isDigit() }.nonEmpty))
                cerrar()
            },
        )
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            SeccionLista("Operación") {
                listOf("venta" to "Venta", "alquiler" to "Alquiler", "temporario" to "Temporario").forEach { (k, n) ->
                    marca(n, k in filtros.operacion) { alCambiar(filtros.copy(operacion = FiltrosCartera.alternar(filtros.operacion, k))) }
                }
            }
            SeccionLista("Tipo") {
                Propiedad.tipos.forEach { (k, n) -> marca(n, k in filtros.tipo) { alCambiar(filtros.copy(tipo = FiltrosCartera.alternar(filtros.tipo, k))) } }
            }
            SeccionLista("Estado") {
                Propiedad.estados.forEach { (k, n) -> marca(n, k in filtros.estado) { alCambiar(filtros.copy(estado = FiltrosCartera.alternar(filtros.estado, k))) } }
            }
            if (resumen.barrios.isNotEmpty()) {
                SeccionLista("Barrio") {
                    resumen.barrios.forEach { b ->
                        marca("${b.barrio} · ${b.cantidad}", b.barrio in filtros.barrio) { alCambiar(filtros.copy(barrio = FiltrosCartera.alternar(filtros.barrio, b.barrio))) }
                    }
                }
            }
            SeccionLista("Precio") {
                Entrada(desde, { desde = it }, "Desde (100.000)", Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(horizontal = 16.dp), teclado = KeyboardType.Number)
                Entrada(hasta, { hasta = it }, "Hasta (200.000)", Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(horizontal = 16.dp), teclado = KeyboardType.Number)
            }
            if (conAgentes) {
                SeccionLista("Agente") {
                    marca("Mías", filtros.agente == "yo") { alCambiar(filtros.copy(agente = if (filtros.agente == "yo") null else "yo")) }
                    equipo.forEach { m ->
                        marca(m.completo, filtros.agente == m.usuarioId) { alCambiar(filtros.copy(agente = if (filtros.agente == m.usuarioId) null else m.usuarioId)) }
                    }
                    marca("Sin agente", filtros.agente == "libre") { alCambiar(filtros.copy(agente = if (filtros.agente == "libre") null else "libre")) }
                }
            }
        }
    }
}

/** «Cambiar el precio de N»: en porcentaje (−5 baja un 5 %), redondeado a la centena. */
@Composable
fun HojaPrecioVarias(cuantas: Int, cambiar: (Double) -> Unit) {
    val cerrar = LocalCerrarHoja.current
    var porcentaje by remember { mutableStateOf("-5") }
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Grabber() }
        Texto("Cambiar el precio de $cuantas", Estilo.tituloHojaChico, C.texto)
        Texto("En porcentaje: −5 baja un 5${Formato.fino}%. Se redondea a la centena.", Estilo.subhead, C.texto2)
        Campo(label = "Porcentaje") { Entrada(porcentaje, { porcentaje = it }, "-5", Modifier.weight(1f), teclado = KeyboardType.Text) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            BotonTexto("Cancelar", { cerrar() }, Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            BotonPrincipal("Cambiar", {
                val n = porcentaje.replace(",", ".").replace("−", "-").toDoubleOrNull() ?: 0.0
                if (!n.isFinite() || n == 0.0) return@BotonPrincipal
                cerrar()
                cambiar(n)
            }, Modifier.weight(1f))
        }
    }
}

/** «Mandar a un interesado»: buscar el contacto; le llega por WhatsApp con el link de cada una. */
@Composable
fun HojaMandarVarias(elegir: (Contacto) -> Unit) {
    val estado = LocalEstado.current
    var q by remember { mutableStateOf("") }
    var contactos by remember { mutableStateOf<List<Contacto>>(emptyList()) }
    LaunchedEffect(q) {
        delay(250)
        contactos = estado.api.opcional<List<Contacto>>("/v1/contactos", listOf("limite" to "8", "q" to q)) ?: emptyList()
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Grabber() }
        Texto("Mandar a un interesado", Estilo.tituloHojaChico, C.texto)
        Texto("Le llega por WhatsApp con el link de cada una.", Estilo.subhead, C.texto2)
        Buscador("Nombre o teléfono", q, { q = it })
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            Column(Modifier.fillMaxWidth().tarjeta()) {
                contactos.forEach { c ->
                    Tocable({ elegir(c) }, habilitado = c.telefono != null) {
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 14.dp).alpha(if (c.telefono == null) 0.5f else 1f),
                            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AvatarIniciales(nombre = c.nombre, tam = 36.dp)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                Texto(c.nombre, Estilo.filaLista, C.texto)
                                Texto(c.telefono ?: "sin teléfono", Estilo.footnote, C.texto2)
                            }
                            Icono("bubble.left.fill", 17f, C.okTinta)
                        }
                    }
                }
            }
        }
    }
}

/** 3C El mapa de la cartera: a nivel barrio, apagado y sin lugares de interés (como el handoff), con los precios como
 *  pines y la tarjeta de la elegida abajo (con «a N cuadras de vos», Mandar y Ficha). Arriba, el barrio filtrado (o
 *  «Tu cartera») y cuántas están ubicadas. En Android: teselas de CARTO (claro u oscuro, sin lugares de interés)
 *  en lugar de MapKit. */
@Composable
fun MapaCartera(
    props: List<Propiedad>,
    senales: Map<String, Senal>,
    titulo: String = "Tu cartera",
    compartir: (Propiedad) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val estado = LocalEstado.current
    val ctx = LocalContext.current
    var elegida by remember { mutableStateOf<String?>(null) }
    val camara = remember { CamaraMapa() }
    val ubicacion = remember { Ubicacion() }
    val conLugar = props.filter { it.lat != null && it.lng != null }
    val lugares = conLugar
    var tamano by remember { mutableStateOf(0f to 0f) }

    /** Todas las propiedades a la vista, con margen, y nunca más cerca que unas 12 cuadras. */
    fun encuadrar() {
        val ps = conLugar.mapNotNull { p -> if (p.lat != null && p.lng != null) p.lat to p.lng else null }
        if (ps.isEmpty()) {
            ubicacion.ultima?.let { camara.lat = it.latitude; camara.lng = it.longitude; camara.zoom = 14.0 }
            return
        }
        val minLat = ps.minOf { it.first }; val maxLat = ps.maxOf { it.first }
        val minLng = ps.minOf { it.second }; val maxLng = ps.maxOf { it.second }
        camara.encuadrar((minLat + maxLat) / 2, (minLng + maxLng) / 2, max((maxLat - minLat) * 1.3, 0.012), max((maxLng - minLng) * 1.3, 0.012), tamano.first, tamano.second)
    }

    /** «a 4 cuadras de vos», «a 12 km de vos» (cuadras de 100 m). */
    fun cuadras(p: Propiedad): String? {
        val yo = ubicacion.ultima ?: return null
        val lat = p.lat ?: return null; val lng = p.lng ?: return null
        val r = FloatArray(1)
        Location.distanceBetween(yo.latitude, yo.longitude, lat, lng, r)
        val m = r[0].toDouble()
        if (m > 5000) return "a ${Formato.numero((m / 1000).roundToInt())} km de vos"
        val c = max(1, (m / 100).roundToInt())
        return "a $c ${if (c == 1) "cuadra" else "cuadras"} de vos"
    }

    // Ubicacion de Mira (CLLocationManager en iOS): pide el permiso la primera vez.
    LaunchedEffect(Unit) { ubicacion.pedir(ctx) { estado.alPedirPermiso(Manifest.permission.ACCESS_FINE_LOCATION) } }
    LaunchedEffect(props.map { it.id }, tamano) { elegida = null; encuadrar() }

    val forma = RoundedCornerShape(M.rDestacada)
    BoxWithConstraints(modifier.fillMaxSize().e2(forma).clip(forma).background(C.hundida)) {
        val w = this@BoxWithConstraints.maxWidth.value; val h = this@BoxWithConstraints.maxHeight.value
        LaunchedEffect(w, h) { tamano = w to h }
        MapaTeselas(
            camara = camara, ancho = w, alto = h, alTocarFondo = { elegida = null },
            yo = ubicacion.ultima?.let { it.latitude to it.longitude },
            pines = lugares.map { p ->
                PinMapa(p.id, p.lat ?: 0.0, p.lng ?: 0.0) {
                    val activa = elegida == p.id
                    Tocable({ elegida = p.id }, descripcion = p.nombre) {
                        Box(
                            Modifier.height(32.dp).sombra(Capsula, Color.Black.copy(alpha = 0.25f), 6.dp, y = 4.dp).clip(Capsula)
                                .background(if (activa) C.accion else C.superficie).padding(horizontal = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) { Texto(cortoMapa(p), Estilo(13f, Peso.bold), if (activa) C.sobreAccion else C.texto, lineas = 1) }
                    }
                }
            },
        )
        // Lo que en MapKit son los controles: ir a mi ubicación.
        Box(Modifier.align(Alignment.TopEnd).padding(10.dp)) {
            Tocable({
                val yo = ubicacion.ultima
                if (yo != null) { camara.lat = yo.latitude; camara.lng = yo.longitude; camara.zoom = max(camara.zoom, 15.0) }
                else estado.tarea { ubicacion.pedir(ctx) { estado.alPedirPermiso(Manifest.permission.ACCESS_FINE_LOCATION) } }
            }, descripcion = "Mi ubicación") {
                Box(Modifier.size(40.dp).e1(RoundedCornerShape(10.dp)).clip(RoundedCornerShape(10.dp)).background(C.superficie), contentAlignment = Alignment.Center) {
                    Icono("location", 17f, C.accion)
                }
            }
        }
        val sin = props.size - lugares.size
        Row(
            Modifier.align(Alignment.TopStart).padding(10.dp).e1(Capsula).height(26.dp).clip(Capsula).background(C.superficie).padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            Texto("$titulo · ${Formato.cantidad(lugares.size, "propiedad", "propiedades")}", Estilo(12f, Peso.bold), C.texto2, lineas = 1)
            if (sin > 0 && lugares.isNotEmpty()) Texto("· $sin sin ubicar", Estilo(12f, Peso.bold), C.texto3, lineas = 1)
        }
        val p = lugares.firstOrNull { it.id == elegida }
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
            AnimatedVisibility(p != null, enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
                if (p != null) Box(Modifier.padding(10.dp)) {
                    Row(
                        Modifier.fillMaxWidth().sombra(RoundedCornerShape(18.dp), Color.Black.copy(alpha = 0.3f), 14.dp, y = 10.dp)
                            .clip(RoundedCornerShape(18.dp)).background(C.superficie).padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Foto(p.miniatura, Modifier.size(76.dp).clip(RoundedCornerShape(12.dp)))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Texto(p.direccion ?: p.nombre, Estilo.subheadFuerte, C.texto, lineas = 1)
                            Texto(
                                listOfNotNull(p.precioTexto, p.ambientes?.let { "$it amb" }, p.superficie?.let { "${Formato.numero(it.toInt())} m²" }).joinToString(" · "),
                                Estilo.footnote, C.texto2, lineas = 1,
                            )
                            cuadras(p)?.let { Texto(it, Estilo.footnote, C.okTinta) }
                            Row(Modifier.padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                BotonTonal("Mandar", { compartir(p) }, alto = 30.dp, simbolo = "square.and.arrow.up")
                                BotonTonal("Ficha", { estado.abrir(AFichaPropiedad(id = p.id)) }, alto = 30.dp)
                            }
                        }
                    }
                }
            }
            if (p == null && lugares.isEmpty()) {
                Box(Modifier.padding(16.dp).clip(RoundedCornerShape(14.dp)).background(C.superficie).padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Texto(Formato.sinViudas("Ninguna tiene ubicación todavía. Se ubican solas con la dirección."), Estilo.footnote, C.texto2, alinear = TextAlign.Center)
                }
            }
        }
    }
}

/** «USD 172 k», «ARS 612 k» (`MapaCartera.corto` en iOS). */
fun cortoMapa(p: Propiedad): String {
    val n = p.precio
    if (n == null || p.precioOculto == true) return "Consultar"
    val m = p.moneda ?: "USD"
    if (n >= 1_000_000) return "$m ${String.format(java.util.Locale.US, "%.1f", n / 1_000_000).replace(".", ",")} M"
    if (n >= 1000) return "$m ${(n / 1000).toInt()} k"
    return "$m ${n.toInt()}"
}

/** Mandar la ficha: el logo de WhatsApp en un círculo de 36 pt, visible en cada fila y en la tarjeta del mapa (deslizar
 *  y mantener apretado siguen andando, pero no se descubren solos). */
@Composable
fun BotonMandar(mandar: () -> Unit) {
    Tocable(mandar, descripcion = "Mandar la ficha") {
        Box(Modifier.size(36.dp).clip(CircleShape).background(hex(0x25D366).copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            Imagen(R.drawable.logo_whatsapp, Modifier.size(22.dp))
        }
    }
}

// MARK: - Piezas propias de esta pantalla (lo que SwiftUI da hecho)

/** e1 del handoff (`sombraE1()` en iOS): radio 1, y 1, 7 %. */
@Composable
private fun Modifier.e1(forma: Shape): Modifier = this.sombra(forma, C.sombra.copy(alpha = 0.07f), 1.dp, y = 1.dp)

/** e2 del handoff (`sombraE2()` en iOS): la de las tarjetas. */
@Composable
private fun Modifier.e2(forma: Shape): Modifier =
    this.sombra(forma, C.sombra.copy(alpha = 0.32f), 11.dp, y = 10.dp).sombra(forma, C.sombra.copy(alpha = 0.07f), 1.dp, y = 1.dp)

/** Un botón tonal sin acción propia, para ponerlo de etiqueta de un MenuBoton (que ya es tocable). */
@Composable
private fun EtiquetaTonal(texto: String, simbolo: String?) {
    Row(
        Modifier.heightIn(min = 34.dp).clip(RoundedCornerShape(10.dp)).background(C.crema).padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically,
    ) {
        if (simbolo != null) Icono(simbolo, 13f, C.primarioTinta)
        if (texto.isNotEmpty()) Texto(texto, Estilo(14f, Peso.bold), C.primarioTinta, lineas = 1)
    }
}

// MARK: - Mapa
// El mapa de teselas (MapKit en iOS) es el de tema/Mapa.kt (CamaraMapa, PinMapa, MapaTeselas).
