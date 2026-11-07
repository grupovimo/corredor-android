package ar.com.corredor.app.pantallas.propiedades

import ar.com.corredor.app.app.PantallaApilada

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ar.com.corredor.app.app.LocalEstado
import ar.com.corredor.app.nucleo.*
import ar.com.corredor.app.tema.*
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable

// Clon de Pantallas/Propiedades/Portales.swift.

/** GET /v1/publicaciones/resumen: cada portal con sus cuentas, las propiedades para difundir y las visitas de la web. */
@Serializable
data class ResumenPublicaciones(
    val portales: List<Portal>? = null,
    /** Las disponibles que se pueden difundir y, de ellas, las que faltan en algún portal (ids). */
    val difundibles: Int? = null,
    val faltan: List<String>? = null,
    val reservadas: Int? = null,
    val webVisitasSemana: Int? = null,
    val webVisitasSemanaAnterior: Int? = null,
) {
    @Serializable
    data class Portal(
        val portal: String,
        val activos: Int? = null,
        val pausados: Int? = null,
        val conError: Int? = null,
        val pendientes: Int? = null,
        val visitas: Int? = null,
        val visitasSemana: Int? = null,
        val visitasSemanaAnterior: Int? = null,
        val feed: String? = null,
        val leidoEn: FechaApi? = null,
    )

    val publicadas: Int get() = (portales ?: emptyList()).sumOf { it.activos ?: 0 }
    val conError: Int get() = (portales ?: emptyList()).sumOf { it.conError ?: 0 }
}

/** Una publicación de la lista (GET /v1/publicaciones). */
@Serializable
data class PublicacionLista(
    val id: String,
    val propiedadId: String,
    val propiedad: String? = null,
    val portal: String,
    val estado: String,
    val url: String? = null,
    val error: String? = null,
    val visitas: Int? = null,
    val visitasSemana: Int? = null,
    val consultas: Int? = null,
    val publicadaEn: FechaApi? = null,
)

/** 32b Difusión a portales: «¿mi cartera está donde tiene que estar?». Una tarjeta por portal: Zonaprop y Argenprop por
 *  feed (el link se copia una vez), Mercado Libre con su cuenta y los errores, y la web. «Publicar las N que faltan» manda
 *  en lote a los portales por feed, con deshacer. Abajo, todas las publicaciones con pausar y republicar. */
@Composable
fun Portales() {
    val estado = LocalEstado.current
    val ctx = LocalContext.current
    var r by remember { mutableStateOf<ResumenPublicaciones?>(null) }
    var pubs by remember { mutableStateOf<List<PublicacionLista>?>(null) }
    var ml by remember { mutableStateOf<ConexionML?>(null) }
    var error by remember { mutableStateOf<ErrorApi?>(null) }
    var portal by remember { mutableStateOf("todos") }
    var publicando by remember { mutableStateOf(false) }

    val faltan = r?.faltan?.size ?: 0
    val erroresML = (pubs ?: emptyList()).filter { it.portal == "mercadolibre" && it.estado == "error" }

    suspend fun cargar() {
        try {
            r = estado.api.get("/v1/publicaciones/resumen")
            error = null
        } catch (e: CancellationException) { throw e } catch (e: ErrorApi) { error = e } catch (e: Exception) {}
        ml = estado.api.opcional<ConexionML>("/v1/mercadolibre")
        pubs = estado.api.opcional<List<PublicacionLista>>("/v1/publicaciones", listOf("limite" to "500")) ?: pubs ?: emptyList()
    }

    suspend fun lote(ids: List<String>, deshacer: Boolean): Pair<Int, List<String>> {
        var hechas = 0
        val fallas = mutableListOf<String>()
        for (id in ids.take(500)) {
            for (p in listOf("zonaprop", "argenprop")) {
                try {
                    estado.api.hacer(if (deshacer) "DELETE" else "POST", "/v1/propiedades/$id/publicaciones/$p")
                    hechas += 1
                } catch (e: CancellationException) { throw e } catch (e: ErrorApi) {
                    if (e.detalle !in fallas) fallas.add(e.detalle)
                } catch (e: Exception) {}
            }
        }
        return hechas to fallas
    }

    /** Publicar en lote en Zonaprop y Argenprop (los portales por feed), con deshacer. */
    suspend fun publicarFaltan() {
        val ids = r?.faltan ?: emptyList()
        if (ids.isEmpty()) return
        publicando = true
        try {
            val (hechas, fallas) = lote(ids, deshacer = false)
            estado.version += 1
            if (hechas <= 0) {
                estado.sugerir(fallas.firstOrNull() ?: "No se publicó", "Reintentar") { estado.tarea { publicarFaltan() } }
                return
            }
            estado.avisar("${Formato.cantidad(ids.size, "propiedad", "propiedades")} en Zonaprop y Argenprop${if (fallas.isEmpty()) "" else " · algunas no"}") {
                estado.tarea {
                    lote(ids, deshacer = true)
                    estado.version += 1
                }
            }
        } finally { publicando = false }
    }

    suspend fun accion(p: PublicacionLista, que: String) {
        val ruta = "/v1/propiedades/${p.propiedadId}/publicaciones/${p.portal}"
        fun volver() {
            estado.tarea {
                try { estado.api.hacer("POST", ruta); estado.version += 1 } catch (e: CancellationException) { throw e } catch (e: Exception) { estado.avisar(e) }
            }
        }
        try {
            when (que) {
                "pausar" -> {
                    estado.api.hacer("POST", "$ruta/pausar")
                    estado.avisar("Pausada") { volver() }
                }
                "sacar" -> {
                    estado.api.hacer("DELETE", ruta)
                    estado.avisar("Sacada de ${PublicacionPortal.nombres[p.portal] ?: p.portal}") { volver() }
                }
                else -> {
                    estado.api.hacer("POST", ruta)
                    estado.avisar("Publicando")
                }
            }
            estado.version += 1
        } catch (e: CancellationException) { throw e } catch (e: Exception) { estado.avisar(e) }
    }

    LaunchedEffect(estado.version) { cargar() }

    @Composable
    fun encabezadoPortal(logo: String, nombre: String, detalle: String, chip: String, tono: Tono) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) { LogoPortal(portal = logo) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Texto(nombre, Estilo.filaLista, C.texto)
                Texto(detalle, Estilo.footnote, C.texto2)
            }
            Box(Modifier.height(24.dp).clip(Capsula).background(tono.fondo).padding(horizontal = 9.dp), contentAlignment = Alignment.Center) {
                Texto(chip, Estilo(12f, Peso.bold, relativo = Relativo.caption), tono.tinta)
            }
        }
    }

    @Composable
    fun tarjetaFeed(k: String, r: ResumenPublicaciones) {
        val p = r.portales?.firstOrNull { it.portal == k }
        val nombre = if (k == "zonaprop") "Zonaprop" else "Argenprop"
        val leido = p?.leidoEn
        Column(Modifier.fillMaxWidth().tarjeta().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            encabezadoPortal(
                k, nombre, leido?.let { "${Formato.cantidad(p.activos ?: 0, "aviso activo", "avisos activos")} · leído ${Formato.hace(it)}" } ?: "Todavía no leyó el feed",
                if (leido != null) "Al día" else "Sin conectar", if (leido != null) Tono.verde else Tono.oro,
            )
            val feed = p?.feed
            if (feed != null) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(C.hundida).padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
                ) {
                    Texto(
                        feed.replace("https://", "").replace("http://", "").split("?clave=").firstOrNull() ?: feed,
                        Estilo(12f, Peso.regular), C.texto2, Modifier.weight(1f), lineas = 1, monoespaciado = true, cortarAlMedio = true,
                    )
                    BotonTonal("Copiar", { copiar(ctx, feed); estado.avisar("Copiado") }, alto = 30.dp)
                    Tocable({ compartirTexto(ctx, feed) }, descripcion = "Compartir el link del feed") { Icono("square.and.arrow.up", 17f, C.accion) }
                }
                Texto(
                    if (leido != null) "Pegalo una vez en tu cuenta de $nombre; lo leen solos cada pocas horas." else "Paso que falta: pegarlo en $nombre › Mi cuenta › Integraciones.",
                    Estilo.footnote, if (leido != null) C.texto2 else C.avisoTinta,
                )
            } else {
                Texto("El link del feed lo copia la dueña o el dueño de la inmobiliaria (lleva una clave).", Estilo.footnote, C.texto2)
            }
        }
    }

    @Composable
    fun tarjetaML(r: ResumenPublicaciones) {
        val p = r.portales?.firstOrNull { it.portal == "mercadolibre" }
        val conectada = ml?.conectada == true
        val det = if (conectada) "${Formato.cantidad(p?.activos ?: 0, "aviso activo", "avisos activos")}${if (erroresML.isEmpty()) "" else " · ${erroresML.size} con error"}" else "Sin cuenta conectada"
        Column(Modifier.fillMaxWidth().tarjeta().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Tocable({ estado.abrir(AMercadoLibre()) }) {
                encabezadoPortal(
                    "mercadolibre", "Mercado Libre", det, if (erroresML.isNotEmpty()) "Revisar" else if (conectada) "Al día" else "Sin conectar",
                    if (erroresML.isNotEmpty()) Tono.rojo else if (conectada) Tono.verde else Tono.oro,
                )
            }
            if (erroresML.isNotEmpty()) {
                erroresML.take(3).forEach { x ->
                    Tocable({ estado.abrir(AMercadoLibre()) }) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icono("exclamationmark.circle.fill", 15f, C.errorTinta)
                            Texto("${(x.propiedad ?: "").split(",").firstOrNull() ?: ""}: falta ${ArregloML.de(x.error).falta}", Estilo.footnote, C.texto, Modifier.weight(1f), lineas = 1)
                            Texto("Arreglar", Estilo.footnoteFuerte, C.errorTinta)
                        }
                    }
                }
            } else if (!conectada) {
                Texto("Se conecta con el usuario de la inmobiliaria y cada propiedad se publica desde su ficha.", Estilo.footnote, C.texto2)
                BotonTonal("Conectar Mercado Libre", { estado.abrir(AMercadoLibre()) })
            } else {
                Texto("Cada propiedad se publica desde su ficha; las preguntas llegan a la Bandeja.", Estilo.footnote, C.texto2)
            }
        }
    }

    @Composable
    fun tarjetaWeb(r: ResumenPublicaciones) {
        Column(Modifier.fillMaxWidth().tarjeta().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            encabezadoPortal(
                "web", "Tu web",
                "${Formato.cantidad(r.difundibles ?: 0, "publicada", "publicadas")}${if ((r.webVisitasSemana ?: 0) > 0) " · ${Formato.cantidad(r.webVisitasSemana ?: 0, "visita esta semana", "visitas esta semana")}" else ""}",
                "Al día", Tono.verde,
            )
            Texto("Todo lo disponible sale solo en tu web. Los borradores no.", Estilo.footnote, C.texto2)
            val v = r.webVisitasSemana; val antes = r.webVisitasSemanaAnterior
            if (v != null && antes != null && antes > 0) {
                ArregloML.variacion(v, antes)?.let { x -> Texto("${x.first} contra la semana anterior", Estilo.footnoteFuerte, if (x.second) C.okTinta else C.errorTinta) }
            }
        }
    }

    @Composable
    fun fila(p: PublicacionLista, primera: Boolean) {
        val (t, tono) = ArregloML.estados[p.estado] ?: (p.estado.capitalizado() to Tono.neutro)
        val opciones: List<OpcionDeMenu> = buildList {
            add(OpcionDeMenu("Ver la propiedad", "house") { estado.abrir(AFichaPropiedad(id = p.propiedadId)) })
            p.url?.let { u -> add(OpcionDeMenu("Ver en el portal", "safari") { abrirUrl(ctx, u) }) }
            if (estado.escribe) {
                when (p.estado) {
                    "publicada" -> {
                        add(OpcionDeMenu("Pausar", "pause.circle") { estado.tarea { accion(p, "pausar") } })
                        add(OpcionDeMenu("Sacar del portal", "xmark.circle", destructiva = true) { estado.tarea { accion(p, "sacar") } })
                    }
                    "error" -> {
                        add(OpcionDeMenu("Arreglar", "wrench") { estado.abrir(AArreglarPropiedad(id = p.propiedadId)) })
                        add(OpcionDeMenu("Reintentar", "arrow.clockwise") { estado.tarea { accion(p, "publicar") } })
                    }
                    else -> add(OpcionDeMenu("Publicar", "arrow.up.circle") { estado.tarea { accion(p, "publicar") } })
                }
            }
        }
        Column {
            if (!primera) Box(Modifier.fillMaxWidth().height(0.5.dp).background(C.linea))
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                LogoPortal(portal = p.portal)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Texto(p.propiedad ?: "Propiedad", Estilo.filaLista, C.texto, lineas = 1)
                    Texto(
                        p.error ?: listOfNotNull(t, p.visitasSemana?.let { "$it visitas esta semana" }, p.consultas?.let { Formato.cantidad(it, "consulta", "consultas") }).joinToString(" · "),
                        Estilo.footnote, if (p.estado == "error") C.errorTinta else if (tono == Tono.neutro) C.texto2 else tono.tinta, lineas = 2,
                    )
                }
                MenuBoton(opciones, "Más acciones") { Icono("ellipsis.circle", 22f, C.accion) }
            }
        }
    }

    @Composable
    fun lista() {
        val ps = pubs
        if (ps == null) { EsqueletoFilas(filas = 6); return }
        val l = ps.filter { portal == "todos" || (if (portal == "error") it.estado == "error" else it.portal == portal) }
        if (l.isEmpty()) {
            Box(Modifier.padding(top = 20.dp)) {
                EstadoVacio(
                    icono = "megaphone", titulo = "Todavía no publicaste en portales",
                    texto = "Conectá Mercado Libre y los portales, y publicás desde la ficha de cada propiedad sin cargar dos veces.",
                    accion = if (estado.esDuenio) ("Conectar" to { estado.abrir(ASeccion(clave = "integraciones")) }) else null,
                    secundaria = "Ver la cartera" to { estado.pestana = Pestana.propiedades },
                )
            }
        } else {
            Column(Modifier.fillMaxWidth().tarjeta()) { l.forEachIndexed { i, p -> fila(p, primera = i == 0) } }
        }
    }

    PantallaApilada("Difusión a portales") {
        Refrescable({ cargar() }, Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                val rr = r
                val e = error
                if (rr != null) {
                    Texto(
                        "${Formato.cantidad(rr.difundibles ?: 0, "propiedad para difundir", "propiedades para difundir")}${if ((rr.reservadas ?: 0) > 0) " · las reservadas siguen publicadas, marcadas" else ""}",
                        Estilo.subhead, C.texto2,
                    )
                    if (faltan > 0 && estado.escribe) {
                        BotonPrincipal(
                            if (publicando) "Publicando…" else "Publicar ${if (faltan == 1) "la que falta" else "las $faltan que faltan"}",
                            { estado.tarea { publicarFaltan() } }, habilitado = !publicando, simbolo = "megaphone",
                        )
                    }
                    listOf("zonaprop", "argenprop").forEach { k -> tarjetaFeed(k, rr) }
                    tarjetaML(rr)
                    tarjetaWeb(rr)
                    val v = rr.portales?.firstOrNull { it.portal == "mercadolibre" }?.visitas
                    if (v != null && v > 0) Texto("${Formato.numero(v)} visitas a las publicaciones de Mercado Libre.", Estilo.footnote, C.texto2)
                } else if (e != null) {
                    Box(Modifier.padding(top = 30.dp)) { EstadoError("la difusión", e) { estado.tarea { cargar() } } }
                }
                Box(Modifier.padding(top = 6.dp)) { TituloBloque(texto = "Publicaciones") }
                Row(Modifier.carrusel(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("todos" to "Todos", "error" to "Con error", "mercadolibre" to "Mercado Libre", "zonaprop" to "Zonaprop", "argenprop" to "Argenprop").forEach { (k, n) ->
                        ChipFiltro(n, portal == k) { portal = k }
                    }
                }
                lista()
            }
        }
    }
}
