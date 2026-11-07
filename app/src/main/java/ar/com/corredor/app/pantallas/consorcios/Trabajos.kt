package ar.com.corredor.app.pantallas.consorcios

import ar.com.corredor.app.app.PantallaApilada
import ar.com.corredor.app.nucleo.compartirTexto

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ar.com.corredor.app.app.BarraDe
import ar.com.corredor.app.app.LocalEstado
import ar.com.corredor.app.nucleo.ArchivoPrevio
import ar.com.corredor.app.nucleo.Documentos
import ar.com.corredor.app.nucleo.ErrorApi
import ar.com.corredor.app.nucleo.VistaPrevia
import ar.com.corredor.app.nucleo.Formato
import ar.com.corredor.app.nucleo.opcional
import ar.com.corredor.app.nucleo.subir
import ar.com.corredor.app.tema.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.ZoneId

// Clon de Pantallas/Consorcios/Trabajos.swift.

// MARK: - Trabajos

/** Trabajos y reclamos: lo abierto primero, con quién lo arregla y en qué está; cada uno abre su ficha con fotos y
 *  estado. Abiertos o cerrados. */
@Composable
fun PestanaTrabajosConsorcio(c: ConsorcioAPI, pedido: String?, alCambiarPedido: (String?) -> Unit) {
    val estado = LocalEstado.current
    val alcance = rememberCoroutineScope()
    var cerrados by remember { mutableStateOf(false) }
    var trabajos by remember { mutableStateOf<List<TrabajoAPI>?>(null) }
    var error by remember { mutableStateOf<ErrorApi?>(null) }
    var nuevo by remember { mutableStateOf(false) }

    suspend fun cargar() {
        try {
            trabajos = estado.api.get("/v1/consorcios/${c.id}/trabajos",
                listOf("limite" to "200", "estado" to if (cerrados) "hecho,cancelado" else "pendiente,aprobado,en_curso"))
            error = null
        } catch (e: CancellationException) { throw e } catch (e: Exception) {
            if (e is ErrorApi && trabajos == null) error = e
        }
    }

    fun detalle(t: TrabajoAPI): String = listOfNotNull(
        t.unidad?.let { "reclamo de $it" }, t.proveedor ?: "sin proveedor", t.presupuesto?.let { "presupuesto ${Formato.plata(it)}" },
        if (t.venceEl != null && t.estado != "hecho") "va el ${Cons.diaBarraMes(t.venceEl)}" else null, Cons.haceDias(Cons.diasDesde(t.creadoEn)),
    ).joinToString(" · ")

    LaunchedEffect(estado.version, cerrados) { cargar() }
    LaunchedEffect(pedido) {
        if (pedido == "nueva") { if (estado.escribe) nuevo = true; alCambiarPedido(null) }
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            ChipFiltro("Abiertos", !cerrados) { cerrados = false }
            ChipFiltro("Cerrados", cerrados) { cerrados = true }
            Spacer(Modifier.weight(1f))
            if (estado.escribe) BotonBordeadoConsorcio("Nuevo", "plus", lleno = C.accion) { nuevo = true }
        }
        val l = trabajos
        val e = error
        if (l != null) {
            Column(Modifier.fillMaxWidth().tarjeta()) {
                if (l.isEmpty()) {
                    VacioConsorcio(if (cerrados) "Todavía no cerraste ningún trabajo." else "Sin trabajos abiertos. Cuando un vecino reclame algo, cargalo con fotos y seguilo hasta cerrarlo.")
                }
                l.forEachIndexed { i, t ->
                    key(t.id) {
                        val ch = Cons.chipTrabajo(t)
                        Tocable({ estado.abrir(ATrabajoConsorcio(consorcio = c.id, id = t.id)) }) {
                            RenglonConsorcio(primero = i == 0) {
                                IconoTono(Cons.iconoTrabajo(t), if (t.prioridad == "alta") Tono.rojo else Tono.azul)
                                DosLineasConsorcio(t.titulo, detalle(t))
                                ChipConsorcio(ch.first, ch.second)
                            }
                        }
                    }
                }
            }
        } else if (e != null) {
            EstadoError("los trabajos", e) { alcance.launch { cargar() } }
        } else EsqueletoFilas(filas = 4)
    }
    Hoja(nuevo, { nuevo = false }) {
        HojaTrabajoConsorcio(c, null) { t -> estado.abrir(ATrabajoConsorcio(consorcio = c.id, id = t.id)) }
    }
}

// MARK: - Ficha del trabajo

@Serializable
private data class PasoTrabajo(val estado: String)

@Serializable
private data class AdjTrabajo(val archivoId: String)

/** Un trabajo o reclamo: los cuatro pasos (Abierto · Presupuesto · En obra · Cerrado) que cambian al toque con deshacer;
 *  las fotos con la cámara; el proveedor a un toque por WhatsApp; y «Avisar a los vecinos», que deja el aviso en el
 *  libro de novedades y lo comparte. */
@Composable
fun FichaTrabajoConsorcio(consorcio: String, id: String) {
    val estado = LocalEstado.current
    val ctx = LocalContext.current
    val alcance = rememberCoroutineScope()
    var t by remember { mutableStateOf<TrabajoAPI?>(null) }
    var c by remember { mutableStateOf<ConsorcioAPI?>(null) }
    var error by remember { mutableStateOf<ErrorApi?>(null) }
    var local by remember { mutableStateOf<String?>(null) }
    var sacadas by remember { mutableStateOf<Set<String>>(emptySet()) }
    var sumar by remember { mutableStateOf(false) }
    var subiendo by remember { mutableIntStateOf(0) }
    var editando by remember { mutableStateOf(false) }
    var avisando by remember { mutableStateOf(false) }
    var previo by remember { mutableStateOf<ArchivoPrevio?>(null) }

    val ruta = "/v1/consorcios/$consorcio/trabajos/$id"

    suspend fun cargar() {
        try {
            val x: TrabajoAPI = estado.api.get(ruta)
            if (c == null) c = estado.api.get("/v1/consorcios/$consorcio")
            t = x
            local = null
            sacadas = emptySet()
            error = null
        } catch (e: CancellationException) { throw e } catch (e: Exception) {
            if (e is ErrorApi && t == null) error = e
        }
    }

    suspend fun cambiar(nuevo: String) {
        val t0 = t ?: return
        val antes = local ?: t0.estado
        if (nuevo == antes) return
        local = nuevo
        try {
            estado.api.enviar<TrabajoAPI, PasoTrabajo>("PATCH", ruta, PasoTrabajo(nuevo))
            val nombre = Cons.pasosTrabajo.firstOrNull { it.first == nuevo }?.second ?: (if (nuevo == "cancelado") "Cancelado" else nuevo)
            val api = estado.api
            val r = ruta
            estado.avisar("${t0.titulo} · $nombre") {
                estado.tarea {
                    try {
                        api.enviar<TrabajoAPI, PasoTrabajo>("PATCH", r, PasoTrabajo(antes))
                        estado.version += 1
                    } catch (e: CancellationException) { throw e } catch (e: Exception) { estado.avisar(e) }
                }
            }
            estado.version += 1
        } catch (e: CancellationException) { throw e } catch (e: Exception) {
            local = null
            val d = (e as? ErrorApi)?.detalle ?: "Probá de nuevo."
            estado.sugerir("No se guardó · $d", "Reintentar") { estado.tarea { cambiar(nuevo) } }
        }
    }

    suspend fun subir(archivos: List<ArchivoElegidoConsorcio>) {
        for (a in archivos) {
            subiendo += 1
            try {
                val x = estado.api.subir(a.datos, a.nombre, a.tipo, if (a.tipo.startsWith("image/")) "foto" else "documento")
                estado.api.enviar<TrabajoAPI, AdjTrabajo>("POST", "$ruta/adjuntos", AdjTrabajo(x.id))
            } catch (e: CancellationException) { throw e } catch (e: Exception) {
                if (e is ErrorApi && e.sinConexion) {
                    estado.avisar("Sin señal: sacá las fotos de nuevo cuando vuelva.", ok = false)
                    subiendo -= 1
                    break
                }
                estado.avisar("No se subió ${a.nombre} · ${(e as? ErrorApi)?.detalle ?: "probá de nuevo"}", ok = false)
            }
            subiendo -= 1
        }
        estado.version += 1
    }

    fun sacar(a: AdjuntoTrabajo) {
        sacadas = sacadas + a.archivoId
        val api = estado.api
        val r = ruta
        alcance.launch {
            try {
                api.hacer("DELETE", "$r/adjuntos/${a.archivoId}")
                estado.avisar("Sacaste ${if (a.tipo.startsWith("image/")) "una foto" else a.nombre}") {
                    estado.tarea {
                        try { api.enviar<TrabajoAPI, AdjTrabajo>("POST", "$r/adjuntos", AdjTrabajo(a.archivoId)) } catch (e: CancellationException) { throw e } catch (e: Exception) { estado.avisar(e) }
                        estado.version += 1
                    }
                }
            } catch (e: CancellationException) { throw e } catch (e: Exception) {
                sacadas = sacadas - a.archivoId
                estado.avisar(e)
            }
        }
    }

    suspend fun ver(archivo: String) {
        try {
            previo = Documentos.archivoConsorcio(ctx, archivo, estado.api)
        } catch (e: CancellationException) { throw e } catch (e: Exception) { estado.avisar(e) }
    }

    LaunchedEffect(estado.version) { cargar() }

    val titulo = t?.titulo ?: "Trabajo"
    val t1 = t
    PantallaApilada(titulo, acciones = if (estado.escribe && t1 != null) ({
        MenuBoton(
            buildList {
                add(OpcionDeMenu("Editar el trabajo", "pencil") { editando = true })
                if (t1.estado != "cancelado") add(OpcionDeMenu("Cancelar el trabajo", "xmark.circle", destructiva = true) { alcance.launch { cambiar("cancelado") } })
            },
            "Más acciones",
        ) { Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) { Icono("ellipsis.circle", 19f, C.accion) } }
    }) else null) {
        Refrescable({ cargar() }, Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                val t0 = t
                val c0 = c
                val e = error
                if (t0 != null && c0 != null) {
                    val estadoT = local ?: t0.estado
                    val i = Cons.pasosTrabajo.indexOfFirst { it.first == estadoT }
                    Texto(c0.nombreCorto, Estilo.footnote, C.texto2)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Cons.pasosTrabajo.forEachIndexed { n, p ->
                            val on = n == i
                            val hecho = i >= 0 && n < i
                            Tocable({ alcance.launch { cambiar(p.first) } }, Modifier.weight(1f).semantics { selected = on }, habilitado = estado.escribe) {
                                Box(Modifier.fillMaxWidth().heightIn(min = 36.dp).clip(CircleShape).background(if (on) C.accion else if (hecho) C.okFondo else C.hundida),
                                    contentAlignment = Alignment.Center) {
                                    Texto(p.second, Estilo(13f, Peso.semibold), if (on) C.sobreAccion else if (hecho) C.okTinta else C.texto2, lineas = 1, escalaMinima = 0.8f)
                                }
                            }
                        }
                    }
                    if (estadoT == "cancelado") Texto("Este trabajo está cancelado. Tocá un paso para reabrirlo.", Estilo.footnote, C.avisoTinta)
                    // Fotos
                    val adj = (t0.adjuntos ?: emptyList()).filter { it.archivoId !in sacadas }
                    val imagenes = adj.filter { it.tipo.startsWith("image/") }
                    val otros = adj.filter { !it.tipo.startsWith("image/") }
                    val celdas: List<AdjuntoTrabajo?> = imagenes + (if (estado.escribe) listOf(null) else emptyList())
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        val columnas = maxOf(1, ((this@BoxWithConstraints.maxWidth + 8.dp) / (96.dp + 8.dp)).toInt())
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            celdas.chunked(columnas).forEach { fila ->
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    fila.forEach { a ->
                                        Box(Modifier.weight(1f).height(96.dp)) {
                                            if (a != null) {
                                                ImagenConsorcio(a.archivoId, Modifier.matchParentSize().clip(RoundedCornerShape(12.dp)).clickable { alcance.launch { ver(a.archivoId) } })
                                                if (estado.escribe) {
                                                    Box(Modifier.align(Alignment.TopEnd).padding(4.dp).size(26.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.5f))
                                                        .clickable { sacar(a) }.semantics { contentDescription = "Sacar esta foto" }, contentAlignment = Alignment.Center) {
                                                        Icono("xmark", 13f, Color.White)
                                                    }
                                                }
                                            } else {
                                                val linea2 = C.linea2
                                                Box(
                                                    Modifier.matchParentSize().drawBehind {
                                                        drawRoundRect(linea2, cornerRadius = CornerRadius(12.dp.toPx()), style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 5.dp.toPx()))))
                                                    }.clickable { sumar = true }.semantics { contentDescription = "Sumar fotos" },
                                                    contentAlignment = Alignment.Center,
                                                ) { if (subiendo > 0) Rueda() else Icono("camera", 24f, C.texto2) }
                                            }
                                        }
                                    }
                                    repeat(columnas - fila.size) { Spacer(Modifier.weight(1f)) }
                                }
                            }
                        }
                    }
                    otros.forEach { a ->
                        key(a.archivoId) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                BotonLabelConsorcio(a.nombre, "doc", estilo = Estilo.subheadFuerte) { alcance.launch { ver(a.archivoId) } }
                                Spacer(Modifier.weight(1f))
                                if (estado.escribe) Box(Modifier.size(36.dp).clickable { sacar(a) }.semantics { contentDescription = "Sacar ${a.nombre}" }, contentAlignment = Alignment.Center) {
                                    Icono("xmark.circle.fill", 17f, C.texto3)
                                }
                            }
                        }
                    }
                    // Proveedor
                    val p = t0.proveedor
                    if (p != null) {
                        val dias = Cons.diasDesde(t0.creadoEn)
                        val det: Pair<String, Boolean> = run {
                            if (estadoT == "pendiente") return@run "Presupuesto pedido ${Cons.haceDias(dias)}" to true
                            val v = t0.venceEl
                            if (v != null && estadoT != "hecho") return@run "Va el ${Cons.diaBarraMes(v)}${t0.presupuesto?.let { " · ${Formato.plata(it)}" } ?: ""}" to false
                            t0.presupuesto?.let { return@run "Presupuesto ${Formato.plata(it)}" to false }
                            (t0.proveedorRubro ?: "Proveedor") to false
                        }
                        Row(Modifier.fillMaxWidth().tarjeta().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconoTono(Cons.iconoRubro(t0.proveedorRubro), Tono.neutro)
                            DosLineasConsorcio(p, det.first, tonoDetalle = if (det.second) C.avisoTinta else C.texto2)
                            t0.proveedorTelefono.noVacio()?.let { tel ->
                                            Box(Modifier.size(44.dp).clickable { Afuera.whatsapp(tel, "Hola, te escribo de la administración del ${c0.nombre} por «${t0.titulo}».") }
                                    .semantics { contentDescription = "Escribirle a $p por WhatsApp" }, contentAlignment = Alignment.Center) {
                                    Imagen(Imagenes.logoWhatsApp, Modifier.size(24.dp))
                                }
                            }
                        }
                    } else {
                        Tocable({ if (estado.escribe) editando = true }) {
                            Row(Modifier.fillMaxWidth().tarjeta().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                IconoTono("wrench.and.screwdriver", Tono.neutro)
                                DosLineasConsorcio("Sin proveedor", "Tocá para elegir quién lo arregla.")
                            }
                        }
                    }
                    t0.descripcion.noVacio()?.let { d ->
                        Column(Modifier.fillMaxWidth().tarjeta().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Texto(listOf(t0.unidad?.let { "Reclamó $it" } ?: "Lo cargó la administración", t0.creadoEn?.let { Cons.diaBarraMes(it.take(10)) } ?: "")
                                .filter { it.isNotEmpty() }.joinToString(" · "), Estilo.footnote, C.texto2)
                            Texto(d, Estilo.body16, C.texto)
                        }
                    }
                    if (estado.escribe) Box(Modifier.padding(top = 6.dp)) { BotonAccion("Avisar a los vecinos", { avisando = true }) }
                } else if (e != null) {
                    EstadoError("el trabajo", e) { alcance.launch { cargar() } }
                } else EsqueletoFilas(filas = 5)
            }
        }
    }
    Hoja(editando, { editando = false }) {
        val c0 = c; val t0 = t
        if (c0 != null && t0 != null) HojaTrabajoConsorcio(c0, t0) { }
    }
    Hoja(avisando, { avisando = false }) {
        val c0 = c; val t0 = t
        if (c0 != null && t0 != null) HojaAvisoVecinos(c0, t0)
    }
    ElegirArchivoConsorcio(sumar, { sumar = false }, tiposArchivo = listOf("image/*", "application/pdf"), multiple = true) { a -> alcance.launch { subir(a) } }
    VistaPrevia(previo) { previo = null }
}

// MARK: - Alta y edición

@Serializable
private data class DatosTrabajo(
    val titulo: String, val descripcion: String?, val unidadId: String?, val proveedorId: String?, val estado: String, val prioridad: String,
    val presupuesto: Double?, val costoReal: Double?, val responsable: String?, val venceEl: String?, val hechoEl: String?,
)

/** Los datos de un trabajo o reclamo (alta y edición): qué pasa, el detalle o lo que dijo el vecino, quién lo reclamó,
 *  la prioridad, el proveedor (o uno nuevo), el presupuesto y el día que va. */
@Composable
fun HojaTrabajoConsorcio(c: ConsorcioAPI, t: TrabajoAPI?, alCrear: (TrabajoAPI) -> Unit) {
    val estado = LocalEstado.current
    val cerrar = LocalCerrarHoja.current
    val alcance = rememberCoroutineScope()
    val hoy = LocalDate.now(Formato.zona)
    val vence = t?.venceEl?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() }
    var titulo by remember { mutableStateOf(t?.titulo ?: "") }
    var descripcion by remember { mutableStateOf(t?.descripcion ?: "") }
    var unidad by remember { mutableStateOf(t?.unidadId ?: "") }
    var prioridad by remember { mutableStateOf(t?.prioridad ?: "media") }
    var proveedor by remember { mutableStateOf(t?.proveedorId ?: "") }
    var nuevoProveedor by remember { mutableStateOf("") }
    var rubro by remember { mutableStateOf("") }
    var telefono by remember { mutableStateOf("") }
    var presupuesto by remember { mutableStateOf(t?.presupuesto) }
    var conDia by remember { mutableStateOf(vence != null) }
    var dia by remember { mutableStateOf(vence ?: hoy) }
    var unidades by remember { mutableStateOf<List<UnidadAPI>>(emptyList()) }
    var proveedores by remember { mutableStateOf<List<ProveedorConsorcio>>(emptyList()) }
    var guardando by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        unidades = estado.api.opcional<List<UnidadAPI>>("/v1/consorcios/${c.id}/unidades") ?: emptyList()
        proveedores = runCatching { ProveedoresConsorcio.todos(estado.api) }.getOrElse { if (it is CancellationException) throw it; emptyList() }
    }

    suspend fun guardar() {
        guardando = true
        try {
            var pid: String? = proveedor.ifEmpty { null }
            if (proveedor == "otro") {
                val p = ProveedoresConsorcio.crear(estado.api, ProveedoresConsorcio.Nuevo(nombre = nuevoProveedor.trim(), rubro = rubro.noVacio(), telefono = telefono.noVacio()))
                pid = p.id
                proveedor = p.id
                proveedores = proveedores + p
            }
            val d = DatosTrabajo(titulo.trim(), descripcion.noVacio(), unidad.noVacio(), pid, t?.estado ?: "pendiente", prioridad,
                presupuesto?.let { Cons.redondo(it) }, t?.costoReal, t?.responsable, if (conDia) dia.toString() else null, t?.hechoEl)
            val base = "/v1/consorcios/${c.id}/trabajos"
            if (t != null) {
                estado.api.enviar<TrabajoAPI, DatosTrabajo>("PUT", "$base/${t.id}", d)
                estado.avisar("Trabajo guardado")
                estado.version += 1
                cerrar()
            } else {
                val n: TrabajoAPI = estado.api.enviar("POST", base, d)
                estado.version += 1
                cerrar()
                alCrear(n)
            }
        } catch (e: CancellationException) { throw e } catch (e: Exception) { estado.avisar(e) }
        guardando = false
    }

    MarcoHoja(
        if (t == null) "Nuevo trabajo o reclamo" else "Editar el trabajo", confirmar = if (t == null) "Cargar" else "Guardar",
        confirmarHabilitado = !(titulo.trim().isEmpty() || (proveedor == "otro" && nuevoProveedor.trim().isEmpty()) || guardando),
        alConfirmar = { alcance.launch { guardar() } },
    ) {
        SeccionConsorcio {
            CampoConsorcio("Qué pasa") { CampoTextoConsorcio(titulo, { titulo = it }, "Filtración en cochera") }
            CampoConsorcio("Detalle o lo que dijo el vecino (opcional)") {
                CampoTextoConsorcio(descripcion, { descripcion = it }, "«Gotea sobre mi cochera desde el lunes.»", lineas = 3, lineasMaximas = 6)
            }
            RenglonSelectorConsorcio("Lo reclamó", listOf("" to "El edificio (común)") + unidades.sortedWith { a, b -> Cons.ordenUnidad.compare(a.etiqueta, b.etiqueta) }.map { it.id to it.etiqueta }, unidad) { unidad = it }
            RenglonSelectorConsorcio("Prioridad", listOf("alta" to "Alta", "media" to "Media", "baja" to "Baja"), prioridad) { prioridad = it }
        }
        SeccionConsorcio {
            RenglonSelectorConsorcio("Proveedor", listOf("" to "Todavía nadie") + proveedores.map { it.id to it.conRubro } + listOf("otro" to "Otro (cargarlo)…"), proveedor) { proveedor = it }
            if (proveedor == "otro") {
                CampoConsorcio("Nombre del proveedor") { CampoTextoConsorcio(nuevoProveedor, { nuevoProveedor = it }) }
                CampoConsorcio("Rubro (opcional)") { CampoTextoConsorcio(rubro, { rubro = it }, "Plomería") }
                CampoConsorcio("Celular (opcional)") { CampoTextoConsorcio(telefono, { telefono = it }, teclado = KeyboardType.Phone) }
            }
            CampoConsorcio("Presupuesto (opcional)") { CampoNumeroConsorcio(presupuesto, { presupuesto = it }, "Monto") }
            Interruptor("Va el día", conDia, { conDia = it })
            if (conDia) RenglonFechaConsorcio("Día", dia, { dia = it })
        }
    }
}

// MARK: - Avisar a los vecinos

@Serializable
private data class AvisoNovedad(val tipo: String = "aviso", val estado: String = "resuelto", val titulo: String, val descripcion: String?, val unidadId: String?)

/** «Avisar a los vecinos»: el texto armado según el estado queda en el libro de novedades y se comparte con la hoja del
 *  sistema (el grupo del edificio). */
@Composable
fun HojaAvisoVecinos(c: ConsorcioAPI, t: TrabajoAPI) {
    val estado = LocalEstado.current
    val ctx = LocalContext.current
    val cerrar = LocalCerrarHoja.current
    val alcance = rememberCoroutineScope()

    val textoAviso: String = run {
        val tit = t.titulo.take(1).lowercase() + t.titulo.drop(1)
        val medio = if (t.estado == "hecho") "Ya quedó resuelto."
        else if (t.proveedor != null) "Ya está ${t.proveedor}${t.venceEl?.let { " y va el ${Cons.diaBarraMes(it)}" } ?: " viéndolo"}."
        else "Ya lo estamos viendo."
        "Vecinos de ${c.nombreCorto}: $tit. $medio Cualquier cosa, escribannos. La administración."
    }
    var texto by remember { mutableStateOf(textoAviso) }
    var guardando by remember { mutableStateOf(false) }

    suspend fun anotar() {
        guardando = true
        val x = texto.trim()
        try {
            estado.api.hacer("POST", "/v1/consorcios/${c.id}/novedades", AvisoNovedad(titulo = x.take(200), descripcion = if (x.length > 200) x else null, unidadId = t.unidadId))
            estado.avisar("Aviso anotado en el libro de novedades.")
            estado.version += 1
            // La hoja de compartir del sistema; al volver, se cierra (como el sheet de iOS).
            compartirTexto(ctx, x)
            cerrar()
        } catch (e: CancellationException) { throw e } catch (e: Exception) { estado.avisar(e) }
        guardando = false
    }

    MarcoHoja("Avisar a los vecinos", confirmar = "Anotar y compartir", confirmarHabilitado = !(texto.trim().isEmpty() || guardando),
        alConfirmar = { alcance.launch { anotar() } }) {
        SeccionConsorcio {
            Texto("Queda en el libro de novedades y lo compartís en el grupo del edificio.", Estilo.footnote, C.texto2)
            CampoConsorcio("Aviso") { CampoTextoConsorcio(texto, { texto = it }, lineas = 5, lineasMaximas = 12) }
        }
    }
}
