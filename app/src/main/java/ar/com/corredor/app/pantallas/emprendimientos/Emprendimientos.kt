package ar.com.corredor.app.pantallas.emprendimientos

// Clon de Pantallas/Emprendimientos/Emprendimientos.swift.

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import ar.com.corredor.app.app.*
import ar.com.corredor.app.nucleo.*
import ar.com.corredor.app.pantallas.cartera.Operacion
import ar.com.corredor.app.pantallas.inicio.TarjetaAvance
import ar.com.corredor.app.pantallas.propiedades.AReserva
import ar.com.corredor.app.pantallas.propiedades.Inmobiliaria
import ar.com.corredor.app.pantallas.propiedades.NuevaReservaVenta
import ar.com.corredor.app.pantallas.propiedades.ReservaAPI
import ar.com.corredor.app.pantallas.propiedades.Senal
import ar.com.corredor.app.tema.*
import coil3.compose.AsyncImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlin.math.ceil

// MARK: - Lotes y masterplan de un emprendimiento

/** Crear lotes en serie (manzana, del 1 al 40, superficie, precio). Va en una hoja. */
@Composable
fun NuevosLotes(emprendimiento: String, cerrar: () -> Unit = LocalCerrarHoja.current) {
    val estado = LocalEstado.current
    var manzana by remember { mutableStateOf("A") }
    var desde by remember { mutableStateOf(1) }
    var hasta by remember { mutableStateOf(20) }
    var superficie by remember { mutableStateOf("") }
    var frente by remember { mutableStateOf("") }
    var fondo by remember { mutableStateOf("") }
    var precio by remember { mutableStateOf("") }
    var moneda by remember { mutableStateOf("USD") }

    @Serializable data class CuerpoLotes(val manzana: String, val desde: Int, val hasta: Int, val superficie: Double? = null, val frente: Double? = null, val fondo: Double? = null, val precio: Double? = null, val moneda: String)
    @Serializable data class R(val creados: Int? = null, val salteados: Int? = null)

    BarraHoja(
        "Lotes en serie",
        izquierda = { BotonBarra("Cancelar") { cerrar() } },
        derecha = {
            BotonBarra("Crear", fuerte = true) {
                estado.tarea {
                    try {
                        val r: R = estado.api.enviar("POST", "/v1/emprendimientos/$emprendimiento/lotes",
                            CuerpoLotes(manzana, desde, hasta, numeroDe(superficie), numeroDe(frente), numeroDe(fondo), numeroDe(precio), moneda))
                        val salteados = r.salteados ?: 0
                        estado.avisar("Creamos ${Formato.cantidad(r.creados ?: 0, "lote", "lotes")}${if (salteados > 0) " · $salteados ya estaban" else ""}")
                        estado.version += 1
                        cerrar()
                    } catch (e: CancellationException) { throw e } catch (e: Exception) { estado.avisar(e) }
                }
            }
        },
    )
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(C.superficie)) {
            FilaForm { Entrada(manzana, { manzana = it }, "Manzana", Modifier.fillMaxWidth()) }
            Separador()
            FilaForm {
                Texto("Desde el lote $desde", Estilo.body, C.texto, Modifier.weight(1f))
                Pasador(desde, { desde = it; if (hasta < it) hasta = it }, rango = 1..999)
            }
            Separador()
            FilaForm {
                Texto("Hasta el lote $hasta", Estilo.body, C.texto, Modifier.weight(1f))
                Pasador(hasta, { hasta = it }, rango = desde..999)
            }
            Separador()
            FilaForm { Entrada(superficie, { superficie = it }, "Superficie m²", Modifier.fillMaxWidth(), teclado = KeyboardType.Decimal) }
            Separador()
            FilaForm { Entrada(frente, { frente = it }, "Frente m", Modifier.fillMaxWidth(), teclado = KeyboardType.Decimal) }
            Separador()
            FilaForm { Entrada(fondo, { fondo = it }, "Fondo m", Modifier.fillMaxWidth(), teclado = KeyboardType.Decimal) }
            Separador()
            FilaForm { Segmentado(listOf("USD" to "USD", "ARS" to "ARS"), moneda, { moneda = it }, Modifier.fillMaxWidth(), descripcion = "Moneda") }
            Separador()
            FilaForm { Entrada(precio, { precio = it }, "Precio", Modifier.fillMaxWidth(), teclado = KeyboardType.Decimal) }
        }
    }
}

/** El masterplan: el plano con cada lote marcado en el color de su estado. Tocando el plano se ubica el lote elegido
 *  (como en el editor de la web). `ver`: «disponible», «reservado» o «vendido»: lo demás se atenúa. */
@Composable
fun MasterplanVista(
    emprendimiento: String,
    imagen: String?,
    unidades: List<UnidadEmp>,
    elegido: String? = null,
    ver: String? = null,
    alElegir: (String) -> Unit = {},
) {
    val estado = LocalEstado.current
    val ctx = LocalContext.current
    var marcas by remember { mutableStateOf<List<Triple<String, Double, Double>>>(emptyList()) }
    // El archivo del plano como lo guarda la API: el PUT reemplaza el masterplan entero, así que va siempre (si no,
    // mover una marca borraba el plano).
    var imagenId by remember { mutableStateOf<String?>(null) }
    var ubicando by remember { mutableStateOf<String?>(null) }

    @Serializable data class Marca(val propiedadId: String, val x: Double, val y: Double)
    @Serializable data class CuerpoMaster(val imagen: String? = null, val lotes: List<Marca>)
    @Serializable data class MP(val imagen: String? = null, val lotes: List<Marca>? = null)
    @Serializable data class E(val masterplan: MP? = null)

    suspend fun guardar(nueva: String? = null) {
        try {
            estado.api.hacer("PUT", "/v1/emprendimientos/$emprendimiento/masterplan",
                CuerpoMaster(nueva ?: imagenId, marcas.map { Marca(it.first, it.second, it.third) }))
            if (nueva != null) imagenId = nueva
        } catch (e: CancellationException) { throw e } catch (e: Exception) { estado.avisar(e) }
    }

    val plano = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { u: Uri? ->
        if (u == null) return@rememberLauncherForActivityResult
        estado.tarea {
            val d = withContext(Dispatchers.IO) { Fotos.achicar(ctx, u, lado = 4096, calidad = 85) } ?: return@tarea
            val a = try { estado.api.subir(d, nombre = "plano.jpg", tipo = "image/jpeg", uso = "foto") } catch (e: CancellationException) { throw e } catch (e: Exception) { null }
            if (a != null) {
                guardar(a.id)
                estado.version += 1
            }
        }
    }

    LaunchedEffect(emprendimiento) {
        val e = estado.api.opcional<E>("/v1/emprendimientos/$emprendimiento")
        if (e != null) {
            marcas = (e.masterplan?.lotes ?: emptyList()).map { Triple(it.propiedadId, it.x, it.y) }
            imagenId = e.masterplan?.imagen
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { TituloBloque("Masterplan") }
            if (estado.escribe) {
                Tocable({ plano.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { _ ->
                    Texto(if (imagen == null) "Subir el plano" else "Cambiar el plano", Estilo.footnoteFuerte, C.accion)
                }
            }
        }
        if (imagen != null) {
            var tam by remember { mutableStateOf(IntSize.Zero) }
            val densidad = LocalDensity.current
            BoxWithConstraints(
                Modifier.fillMaxWidth().aspectRatio(1.4f).clip(RoundedCornerShape(14.dp))
                    .pointerInput(ubicando) {
                        detectTapGestures { punto ->
                            val id = ubicando ?: return@detectTapGestures
                            if (size.width == 0 || size.height == 0) return@detectTapGestures
                            marcas = marcas.filter { it.first != id } + Triple(id, (punto.x / size.width * 100).toDouble(), (punto.y / size.height * 100).toDouble())
                            ubicando = null
                            estado.tarea { guardar() }
                        }
                    },
            ) {
                val ancho = this@BoxWithConstraints.maxWidth
                val alto = this@BoxWithConstraints.maxHeight
                Box(Modifier.fillMaxSize().background(C.hundida))
                AsyncImage(imagen, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                marcas.forEach { m ->
                    val p = unidades.firstOrNull { it.id == m.first }
                    val v = p?.vista ?: "disponible"
                    val es = elegido == m.first
                    Box(
                        Modifier.offset(ancho * (m.second / 100).toFloat() - 11.dp, alto * (m.third / 100).toFloat() - 11.dp)
                            .size(22.dp).alpha(if (ver != null && ver != v) 0.3f else 1f)
                            .clip(CircleShape).background(colorMasterplan(v))
                            .border(if (es) 3.dp else 2.dp, if (es) C.oro else Color.White, CircleShape)
                            .clickable { alElegir(m.first) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Texto(p?.lote ?: "", Estilo(9f, Peso.bold), if (v == "vendido") Color.White else C.texto, lineas = 1)
                    }
                }
            }
            if (estado.escribe) {
                SelectorMenu(
                    listOf("" to "Elegí un lote para ubicarlo") + unidades.map { it.id to it.nombre(loteo = true) },
                    ubicando ?: "", { ubicando = it.ifEmpty { null } }, descripcion = "Ubicar un lote",
                )
                if (ubicando != null) Texto("Tocá el plano donde está el lote.", Estilo.footnote, C.avisoTinta)
            }
        }
    }
}

/** Los colores de la grilla: disponible verde claro, reservado oro, vendido gris oscuro (MasterplanVista.color). */
@Composable
fun colorMasterplan(vista: String): Color = when (vista) {
    "vendido" -> C.texto2
    "reservado" -> C.oro
    else -> C.okFondo
}

/** Un emprendimiento (GET /v1/emprendimientos, /{id}; dominio/emprendimientos.Emprendimiento). */
@Serializable
data class EmprendimientoAPI(
    val id: String,
    val slug: String? = null,
    val nombre: String,
    val masterplanUrl: String? = null,
    val tipo: String? = null,
    val estado: String? = null,
    val descripcion: String? = null,
    val direccion: String? = null,
    val barrio: String? = null,
    val avance: Double? = null,
    val entrega: String? = null,
    val desarrolladora: String? = null,
    val precioDesde: Double? = null,
    val moneda: String? = null,
    val publicado: Boolean? = null,
    val fotosUrl: List<String>? = null,
    val unidades: Cuentas? = null,
) {
    @Serializable
    data class Cuentas(val total: Int = 0, val disponibles: Int = 0, val reservadas: Int = 0, val vendidas: Int = 0)

    val esLoteo: Boolean get() = tipo == "loteo" || tipo == "barrio_cerrado"

    /** «entrega dic. 2027». */
    val entregaTexto: String?
        get() {
            val e = entrega ?: return null
            val p = e.split("-").mapNotNull { it.toIntOrNull() }
            if (p.size < 2 || p[1] !in 1..12) return null
            val meses = listOf("ene.", "feb.", "mar.", "abr.", "may.", "jun.", "jul.", "ago.", "sep.", "oct.", "nov.", "dic.")
            return "entrega ${meses[p[1] - 1]} ${p[0]}"
        }
}

/** Una unidad (lote o departamento) de un emprendimiento (GET /v1/emprendimientos/{id}/unidades). */
@Serializable
data class UnidadEmp(
    val id: String,
    val numero: Int? = null,
    val titulo: String? = null,
    val tipo: String? = null,
    val operacion: String? = null,
    val estado: String? = null,
    val precio: Double? = null,
    val moneda: String? = null,
    val superficie: Double? = null,
    val ambientes: Int? = null,
    val manzana: String? = null,
    val lote: String? = null,
    val piso: Int? = null,
    val depto: String? = null,
    val agenteId: String? = null,
    val fotoUrl: String? = null,
) {
    /** Vendida o alquilada cuentan como vendido; suspendida, como reservada (no se ofrece). */
    val vista: String
        get() = when (estado) {
            "vendida", "alquilada" -> "vendido"
            "reservada", "suspendida" -> "reservado"
            else -> "disponible"
        }

    /** Piso y depto: de la ficha o, si no vino, del título («5.º B · Cabildo 3100»). */
    val pisoDepto: Pair<Int?, String>
        get() {
            if (piso != null) return piso to (depto ?: "")
            val t = titulo
            if (t != null) {
                val m = Regex("""^(\d+)\s*\.?\s*º\s*([A-Za-z0-9]+)""").find(t)
                if (m != null) {
                    val s = m.value
                    val n = s.takeWhile { it.isDigit() }.toIntOrNull() ?: 0
                    val d = s.split("º").last().trim().uppercase()
                    return n to d
                }
            }
            return null to (lote ?: numero?.toString() ?: "")
        }

    /** «Lote 21» o «5.º B». */
    fun nombre(loteo: Boolean): String {
        if (loteo) return "Lote ${lote ?: numero?.toString() ?: ""}"
        val (p, d) = pisoDepto
        return p?.let { "$it.º $d" } ?: (titulo ?: "Unidad")
    }

    val precioTexto: String get() = precio?.let { Formato.plata(it, moneda ?: "USD") } ?: "Consultar"

    companion object {
        val nombresVista = mapOf("disponible" to "Disponible", "reservado" to "Reservado", "vendido" to "Vendido")
        fun tono(vista: String): Tono = if (vista == "vendido") Tono.neutro else if (vista == "reservado") Tono.oro else Tono.verde
    }
}

/** Emprendimientos: la lista, cada uno con su avance y la cuenta de lotes o unidades. */
@Composable
fun Emprendimientos() {
    val estado = LocalEstado.current
    var lista by remember { mutableStateOf<List<EmprendimientoAPI>?>(null) }
    var error by remember { mutableStateOf<ErrorApi?>(null) }
    var nuevo by remember { mutableStateOf(false) }
    var nombre by remember { mutableStateOf("") }

    suspend fun cargar() {
        try {
            lista = estado.api.get("/v1/emprendimientos", query = listOf("limite" to "50"))
            error = null
        } catch (e: CancellationException) { throw e } catch (e: ErrorApi) { error = e } catch (e: Exception) {}
    }

    // La API acepta preventa, en_obra o terminado (emprendimientos.Estados).
    @Serializable data class CuerpoEmprendimiento(val nombre: String, val tipo: String = "edificio", val estado: String = "preventa")

    LaunchedEffect(estado.version) { cargar() }

    PantallaApilada("Emprendimientos", grande = true, acciones = if (estado.escribe) ({ BotonBarraIcono("plus", "Nuevo emprendimiento") { nuevo = true } }) else null) {
        Refrescable({ cargar() }) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val l = lista
                val err = error
                if (l != null) {
                    if (l.isEmpty()) {
                        Box(Modifier.padding(top = 40.dp)) {
                            EstadoVacio(
                                icono = "building", titulo = "Así se va a ver tu primer loteo",
                                texto = "Con el masterplan de lotes o las unidades por piso, y cuánto se vendió. Cargá las unidades como propiedades y sumalas al emprendimiento.",
                                accion = if (estado.escribe) ("Cargar propiedad" to { estado.abrir(AAltaPropiedad()) }) else null,
                                secundaria = if (estado.escribe) ("Cargar emprendimiento" to { nuevo = true }) else null,
                            )
                        }
                    }
                    l.forEach { e ->
                        Tocable({ estado.abrir(ASeccion(clave = "emprendimiento", id = e.id)) }) { _ ->
                            Row(
                                Modifier.fillMaxWidth().tarjeta().padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Box(Modifier.size(72.dp).clip(RoundedCornerShape(12.dp))) { Foto(e.fotosUrl?.firstOrNull(), Modifier.fillMaxSize()) }
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Texto(e.nombre, Estilo(16f, Peso.bold), C.texto)
                                    Texto(
                                        listOfNotNull(e.barrio, e.entregaTexto, e.precioDesde?.let { "desde ${Formato.plata(it, e.moneda ?: "USD")}" }).joinToString(" · "),
                                        Estilo.footnote, C.texto2,
                                    )
                                    e.unidades?.let { u ->
                                        Texto(
                                            "${Formato.numero(u.total)} ${if (e.esLoteo) "lotes" else "unid."} · ${u.disponibles} disp. · ${u.reservadas} res. · ${u.vendidas} vend.",
                                            Estilo(12f, Peso.semibold), C.texto2,
                                        )
                                    }
                                    val a = e.avance
                                    if (a != null && a > 0) Box(Modifier.padding(top = 4.dp)) { BarraAvance(a / 100, C.oro) }
                                }
                                Chevron()
                            }
                        }
                    }
                } else if (err != null) {
                    Box(Modifier.padding(top = 40.dp)) { EstadoError("los emprendimientos", err) { estado.tarea { cargar() } } }
                } else EsqueletoFilas(filas = 3, miniatura = 72.dp)
            }
        }
    }

    // .alert con un TextField.
    AlertaConCampo(
        visible = nuevo,
        titulo = "Emprendimiento nuevo", texto = nombre, alCambiar = { nombre = it }, placeholder = "Nombre",
        cancelar = { nuevo = false },
        boton = "Crear", aceptar = {
            nuevo = false
            estado.tarea {
                try {
                    estado.api.hacer("POST", "/v1/emprendimientos", CuerpoEmprendimiento(nombre))
                    nombre = ""
                    estado.version += 1
                } catch (e: CancellationException) { throw e } catch (e: Exception) { estado.avisar(e) }
            }
        },
    )
}

/** 12E/12C Un emprendimiento: el masterplan es la pantalla. Loteo: la grilla de lotes (de a 8 por fila, con la calle al
 *  medio) o el plano con los lotes encima; edificio: una fila por piso. Tocá un lote y abajo aparece su ficha con la
 *  acción (reservar, ver la reserva o la operación) y «Mandar por WhatsApp». Arriba, la leyenda con las cuentas y lo
 *  vendido; «Ver» atenúa lo que no es de ese estado. */
@Composable
fun FichaEmprendimiento(id: String) {
    val estado = LocalEstado.current
    var id by remember { mutableStateOf(id) }
    var e by remember { mutableStateOf<EmprendimientoAPI?>(null) }
    var lista by remember { mutableStateOf<List<EmprendimientoAPI>>(emptyList()) }
    var unidades by remember { mutableStateOf<List<UnidadEmp>>(emptyList()) }
    var senales by remember { mutableStateOf<Map<String, Senal>>(emptyMap()) }
    var reservas by remember { mutableStateOf<Map<String, ReservaAPI>>(emptyMap()) }
    var error by remember { mutableStateOf<ErrorApi?>(null) }
    var elegidoId by remember { mutableStateOf<String?>(null) }
    var ver by remember { mutableStateOf<String?>(null) }
    var lotes by remember { mutableStateOf(false) }
    var reservando by remember { mutableStateOf<UnidadEmp?>(null) }
    var web by remember { mutableStateOf<String?>(null) }
    var buscando by remember { mutableStateOf(false) }

    val ex = e
    val loteo = when {
        ex == null -> false
        ex.esLoteo -> true
        ex.tipo == "edificio" -> false
        else -> unidades.any { it.lote != null }
    }
    val elegida = unidades.firstOrNull { it.id == elegidoId } ?: unidades.firstOrNull { it.vista == "disponible" } ?: unidades.firstOrNull()
    val cuenta = unidades.groupingBy { it.vista }.eachCount()

    suspend fun cargar() {
        try {
            val x: EmprendimientoAPI = estado.api.get("/v1/emprendimientos/$id")
            e = x
            error = null
            unidades = estado.api.opcional<List<UnidadEmp>>("/v1/emprendimientos/$id/unidades") ?: emptyList()
            lista = estado.api.opcional<List<EmprendimientoAPI>>("/v1/emprendimientos", query = listOf("limite" to "50")) ?: lista
            try { senales = Senal.de(unidades.map { it.id }, estado.api) } catch (e: CancellationException) { throw e } catch (e: Exception) {}
            val rs: List<ReservaAPI> = estado.api.opcional("/v1/reservas", query = listOf("estado" to "activa", "limite" to "200")) ?: emptyList()
            val deEste = unidades.map { it.id }.toSet()
            reservas = rs.filter { it.propiedadId in deEste }.groupBy { it.propiedadId }.mapValues { it.value.first() }
            if (web == null) web = estado.api.opcional<Inmobiliaria>("/v1/inmobiliaria")?.web
        } catch (x: CancellationException) { throw x } catch (x: ErrorApi) { error = x } catch (x: Exception) {}
    }

    fun nota(u: UnidadEmp): String = when (u.vista) {
        "reservado" -> {
            val r = reservas[u.id]
            if (r == null) "Reservado. La reserva no está cargada en Corredor: miralo en la ficha." else {
                val sena = r.sena?.let { " · seña ${Formato.plata(it, r.senaMoneda ?: "USD")}" } ?: ""
                var vence = ""
                r.faltan?.let { n -> vence = if (n < 0) " · venció" else if (n == 0) " · vence hoy" else if (n == 1) " · vence mañana" else " · vence el ${ReservaAPI.corta(r.venceEl)}" }
                "Reservado por ${r.quien}$sena$vence."
            }
        }
        "vendido" -> "Vendido. La operación tiene el boleto y las cuotas."
        else -> {
            val n = senales[u.id]?.interesados ?: 0
            val v = senales[u.id]?.vistasSemana ?: 0
            if (n > 0) "${Formato.cantidad(n, "interesado lo consulta", "interesados lo consultan")}${if (v > 0) " · ${Formato.cantidad(v, "vista", "vistas")} en la web esta semana" else ""}."
            else if (v > 0) "${Formato.cantidad(v, "vista", "vistas")} en la web esta semana."
            else "Nadie lo consultó todavía. Mandalo a quien busque algo así."
        }
    }

    suspend fun accion(u: UnidadEmp) {
        when (u.vista) {
            "disponible" -> reservando = u
            "reservado" -> {
                val r = reservas[u.id]
                if (r != null) estado.abrir(AReserva(id = r.id)) else estado.abrir(AFichaPropiedad(id = u.id))
            }
            else -> {
                buscando = true
                try {
                    val ops: List<Operacion> = estado.api.opcional("/v1/operaciones", query = listOf("propiedad" to u.id)) ?: emptyList()
                    val o = ops.firstOrNull()
                    if (o != null) estado.abrir(AFichaOperacion(id = o.id)) else estado.abrir(AFichaPropiedad(id = u.id))
                } finally { buscando = false }
            }
        }
    }

    fun mandar(u: UnidadEmp, e: EmprendimientoAPI) {
        val link = if (e.publicado == true) web?.let { "$it/emprendimientos/${e.slug ?: e.id}" } else null
        val sub = if (loteo) u.manzana?.let { " · Manzana $it" } ?: "" else ""
        val texto = listOfNotNull("${u.nombre(loteo)}$sub · ${e.nombre}", u.superficie?.let { "${Formato.numero(it.toInt())} m²" }, u.precioTexto, link)
            .joinToString("\n")
        Afuera.whatsapp(null, texto = texto)
    }

    @Serializable data class Cerrar(val resultado: String = "caida", val motivo: String = "Deshecha al crearla")

    /** Recién reservado: aviso con deshacer (cierra la reserva como caída). */
    fun reservada(u: UnidadEmp, r: ReservaAPI) {
        val antes = u.estado
        unidades = unidades.map { if (it.id == u.id) it.copy(estado = "reservada") else it }
        reservas = reservas + (u.id to r)
        estado.avisar("${u.nombre(loteo)} reservado · vence el ${r.venceEl?.let { ReservaAPI.corta(it) } ?: "—"}") {
            unidades = unidades.map { if (it.id == u.id) it.copy(estado = antes) else it }
            reservas = reservas - u.id
            estado.tarea {
                try { estado.api.hacer("POST", "/v1/reservas/${r.id}/cerrar", Cerrar()); estado.version += 1 }
                catch (x: CancellationException) { throw x } catch (x: Exception) { estado.avisar(x) }
            }
        }
    }

    LaunchedEffect(estado.version, id) { cargar() }

    PantallaApilada(ex?.nombre ?: "Emprendimiento") {
        Box(Modifier.fillMaxSize()) {
            Refrescable({ cargar() }) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    val err = error
                    if (ex != null) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            // Cabecera.
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                if (lista.size > 1) {
                                    MenuBoton(lista.map { x ->
                                        OpcionDeMenu("${x.nombre} · ${Formato.numero(x.unidades?.total ?: 0)} ${if (x.esLoteo) "lotes" else "unid."}", "") { elegidoId = null; ver = null; id = x.id }
                                    }, "Emprendimientos") {
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Texto("Emprendimientos · ${lista.size}", Estilo.footnoteFuerte, C.accion)
                                            Icono("chevron.up.chevron.down", 11f, C.accion, peso = Peso.bold)
                                        }
                                    }
                                }
                                Box(Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(M.rDestacada))) {
                                    Foto(ex.fotosUrl?.firstOrNull(), Modifier.fillMaxSize())
                                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.5f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.6f))))
                                    Column(Modifier.align(Alignment.BottomStart).padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Texto("${ex.nombre}${ex.barrio?.let { " · $it" } ?: ""}", Estilo.titulo3, Color.White)
                                        val sub = listOfNotNull(ex.barrio, ex.entregaTexto, ex.precioDesde?.let { "desde ${Formato.plata(it, ex.moneda ?: "USD")}" }).joinToString(" · ")
                                        if (sub.isNotEmpty()) Texto(sub, Estilo.footnote, Color.White)
                                    }
                                }
                                listOfNotNull(ex.direccion, ex.desarrolladora).joinToString(" · ").takeIf { it.isNotBlank() }?.let { Texto(it, Estilo.subhead, C.texto2) }
                            }
                            if (unidades.isEmpty()) {
                                EstadoVacio(
                                    icono = "building.2", titulo = if (loteo) "Así se va a ver el masterplan" else "Así se van a ver las unidades",
                                    texto = "Cada ${if (loteo) "lote" else "unidad"} es una propiedad del emprendimiento. Cargalas y aparecen acá con su estado.",
                                    accion = if (estado.escribe) ("Cargar propiedad" to { estado.abrir(AAltaPropiedad()) }) else null,
                                    secundaria = if (estado.escribe && loteo) ("Crear lotes en serie" to { lotes = true }) else null,
                                )
                            } else {
                                Leyenda(unidades, cuenta, ex, loteo)
                                // Filtro «Ver».
                                Row(Modifier.carrusel(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    ChipFiltro(if (loteo) "Todos los lotes · ${unidades.size}" else "Todas las unidades · ${unidades.size}", ver == null) { ver = null }
                                    ChipFiltro("Disponibles · ${cuenta["disponible"] ?: 0}", ver == "disponible") { ver = "disponible" }
                                    ChipFiltro("Reservados · ${cuenta["reservado"] ?: 0}", ver == "reservado") { ver = "reservado" }
                                    ChipFiltro("Vendidos · ${cuenta["vendido"] ?: 0}", ver == "vendido") { ver = "vendido" }
                                }
                                ParaHacer(reservas)
                                // Masterplan.
                                val tocar = { u: UnidadEmp -> Haptico.seleccion(); elegidoId = u.id }
                                if (loteo) {
                                    if (ex.masterplanUrl != null) {
                                        MasterplanVista(id, ex.masterplanUrl, unidades, elegida?.id, ver) { elegidoId = it }
                                    } else GrillaLotes(unidades, ex, elegida?.id, ver, loteo, tocar)
                                } else Pisos(unidades, elegida?.id, ver, loteo, tocar)
                                if (elegida != null) FichaUnidad(
                                    elegida, loteo, buscando, nota(elegida),
                                    accion = { estado.tarea { accion(elegida) } }, mandar = { mandar(elegida, ex) },
                                )
                            }
                            val a = ex.avance
                            if (a != null && a > 0) {
                                TarjetaAvance(
                                    titulo = "Avance de obra", cifra = "${a.toInt()} %", avance = a / 100, degrade = listOf(hex(0xE9AD44), hex(0xF2BB57)),
                                    pie = ex.entrega?.let { "Entrega: $it" } ?: "Sin fecha de entrega",
                                )
                            }
                            ex.descripcion?.let { Texto(it, Estilo.body16, C.texto) }
                            if (estado.escribe && loteo && unidades.isNotEmpty()) BotonHundido("Crear lotes en serie", { lotes = true })
                        }
                    } else if (err != null) {
                        Box(Modifier.padding(top = 60.dp)) { EstadoError("el emprendimiento", err) { estado.tarea { cargar() } } }
                    } else Box(Modifier.fillMaxWidth().padding(top = 60.dp), contentAlignment = Alignment.Center) { Rueda() }
                }
            }
        }
    }

    Hoja(lotes, { lotes = false }) { NuevosLotes(id, cerrar = { lotes = false }) }
    val r = reservando
    Hoja(r != null, { reservando = null }) {
        if (r != null) NuevaReservaVenta(propiedadId = r.id, titulo = r.nombre(loteo), moneda = r.moneda ?: "USD", alReservar = { x -> reservada(r, x) })
    }
}

@Composable
private fun Leyenda(unidades: List<UnidadEmp>, cuenta: Map<String, Int>, e: EmprendimientoAPI, loteo: Boolean) {
    val vendidas = unidades.filter { it.vista == "vendido" }
    val monto = vendidas.sumOf { it.precio ?: 0.0 }
    val moneda = unidades.firstOrNull { it.precio != null }?.moneda ?: e.moneda ?: "USD"
    val pct = if (unidades.isEmpty()) 0.0 else vendidas.size.toDouble() / unidades.size * 100
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            listOf("disponible", "reservado", "vendido").forEach { k ->
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Cuadro(k, 14f)
                    val c2 = C.texto2
                    TextoRico(buildAnnotatedString {
                        withStyle(SpanStyle(color = c2)) { append("${UnidadEmp.nombresVista[k] ?: k} ") }
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("${cuenta[k] ?: 0}") }
                    }, Estilo.footnote, C.texto)
                }
            }
        }
        Texto(
            "Vendido el ${Formato.porcentaje(Math.round(pct).toDouble())}${if (monto > 0) " · ${Formato.plata(monto, moneda)} en ${if (loteo) "lotes" else "unidades"} vendidos" else ""}",
            Estilo.footnoteFuerte, C.texto,
        )
    }
}

/** Liso disponible, rayado reservado, lleno con tilde vendido. */
@Composable
private fun Cuadro(vista: String, tam: Float) {
    val forma = RoundedCornerShape(3.dp)
    when (vista) {
        "vendido" -> Box(Modifier.size(tam.dp).clip(forma).background(C.texto2), contentAlignment = Alignment.Center) { Icono("checkmark", tam * 0.6f, Color.White, peso = Peso.bold) }
        "reservado" -> Box(Modifier.size(tam.dp).clip(forma).border(1.5.dp, C.oro, forma)) { Rayado(Modifier.fillMaxSize(), paso = 2f) }
        else -> Box(Modifier.size(tam.dp).clip(forma).background(C.okFondo).border(1.dp, C.okTinta.copy(alpha = 0.5f), forma))
    }
}

/** Las reservas del emprendimiento que vencen («vence hoy / mañana / en N días»). */
@Composable
private fun ParaHacer(reservas: Map<String, ReservaAPI>) {
    val estado = LocalEstado.current
    val vencen = reservas.values.filter { it.venceEl != null }.sortedBy { it.venceEl ?: "" }.take(5)
    if (vencen.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TituloBloque("Para hacer")
        Column(Modifier.fillMaxWidth().tarjeta()) {
            vencen.forEachIndexed { i, r ->
                val n = r.faltan ?: 0
                if (i > 0) Box(Modifier.fillMaxWidth().height(0.5.dp).background(C.linea))
                Tocable({ estado.abrir(AReserva(id = r.id)) }) { _ ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        IconoTono("hourglass", Tono.oro, tam = 34.dp)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Texto(r.propiedadTitulo ?: "Reserva", Estilo.filaLista, C.texto, lineas = 1)
                            Texto(
                                if (n < 0) "La reserva venció" else if (n == 0) "La reserva vence hoy" else if (n == 1) "La reserva vence mañana" else "Vence en $n días",
                                Estilo.footnote, if (n <= 1) C.avisoTinta else C.texto2,
                            )
                        }
                        Chevron()
                    }
                }
            }
        }
    }
}

// MARK: Masterplan

@Composable
private fun BotonUnidad(u: UnidadEmp, texto: String, elegidaId: String?, ver: String?, loteo: Boolean, tocar: (UnidadEmp) -> Unit, modifier: Modifier) {
    val v = u.vista
    val elegido = elegidaId == u.id
    val forma = RoundedCornerShape(6.dp)
    Tocable({ tocar(u) }, modifier.semantics { selected = elegido },
        descripcion = "${u.nombre(loteo)}, ${(UnidadEmp.nombresVista[v] ?: v).lowercase()}") { _ ->
        Box(
            Modifier.fillMaxWidth().height(38.dp).alpha(if (ver != null && ver != v) 0.3f else 1f).clip(forma)
                .then(when (v) { "vendido" -> Modifier.background(C.texto2); "reservado" -> Modifier; else -> Modifier.background(C.okFondo) })
                .border(if (elegido) 2.5.dp else 1.dp, if (elegido) C.oro else if (v == "reservado") C.oro.copy(alpha = 0.6f) else Color.Transparent, forma),
            contentAlignment = Alignment.Center,
        ) {
            if (v == "reservado") Rayado(Modifier.fillMaxSize(), paso = 3f)
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                Texto(texto, Estilo(12f, Peso.bold), if (v == "vendido") Color.White else C.texto, lineas = 1, escalaMinima = 0.7f)
                if (v == "vendido") Icono("checkmark", 9f, Color.White, peso = Peso.bold)
            }
        }
    }
}

/** La grilla de lotes: de a 8 por fila, con la calle al medio cuando hay cuatro filas o más. */
@Composable
private fun GrillaLotes(unidades: List<UnidadEmp>, e: EmprendimientoAPI, elegidaId: String?, ver: String?, loteo: Boolean, tocar: (UnidadEmp) -> Unit) {
    val filas = unidades.chunked(8)
    val corte = if (filas.size >= 4) ceil(filas.size / 2.0).toInt() else -1
    Column(Modifier.fillMaxWidth().tarjeta().padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        filas.forEachIndexed { i, fila ->
            if (i == corte) {
                Box(Modifier.fillMaxWidth().heightIn(min = 26.dp).clip(RoundedCornerShape(4.dp)).background(C.hundida), contentAlignment = Alignment.Center) {
                    Texto((e.direccion ?: "Calle").uppercase(), Estilo(11f, Peso.bold, 1.5f / 11f), C.texto3)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                fila.forEach { u -> BotonUnidad(u, u.lote ?: u.numero?.toString() ?: "", elegidaId, ver, loteo, tocar, Modifier.weight(1f)) }
                if (fila.size < 8) repeat(8 - fila.size) { Spacer(Modifier.weight(1f).height(38.dp)) }
            }
        }
    }
}

/** Un edificio, de arriba hacia abajo: una fila por piso y una caja por depto. */
@Composable
private fun Pisos(unidades: List<UnidadEmp>, elegidaId: String?, ver: String?, loteo: Boolean, tocar: (UnidadEmp) -> Unit) {
    val m = unidades.groupBy { it.pisoDepto.first ?: -1 }
    val orden = m.keys.sortedDescending()
    Box(Modifier.fillMaxWidth().tarjeta()) {
        Column(Modifier.horizontalScroll(rememberScrollState()).padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            orden.forEach { piso ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Texto(if (piso >= 0) "$piso.º" else "—", Estilo(12f, Peso.bold), C.texto2, Modifier.width(34.dp))
                    (m[piso] ?: emptyList()).sortedWith { a, b -> compararNatural(a.pisoDepto.second, b.pisoDepto.second) }.forEach { u ->
                        BotonUnidad(u, u.pisoDepto.second, elegidaId, ver, loteo, tocar, Modifier.width(52.dp))
                    }
                }
            }
        }
    }
}

// MARK: Ficha de la unidad

@Composable
private fun FichaUnidad(u: UnidadEmp, loteo: Boolean, buscando: Boolean, nota: String, accion: () -> Unit, mandar: () -> Unit) {
    val estado = LocalEstado.current
    val v = u.vista
    val sub = if (loteo) u.manzana?.let { "Manzana $it" } else u.ambientes?.let { "$it amb" }
    val cta = if (v == "vendido") "Ver la operación" to "doc.text" else if (v == "reservado") "Ver la reserva" to "bookmark" else (if (loteo) "Reservar este lote" else "Reservar") to "bookmark.fill"
    Column(Modifier.fillMaxWidth().tarjeta().padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.fillMaxWidth().height(140.dp).clip(RoundedCornerShape(14.dp))) {
            Foto(u.fotoUrl, Modifier.fillMaxSize())
            val t = UnidadEmp.tono(v)
            Box(Modifier.padding(10.dp).height(24.dp).clip(Capsula).background(t.fondo).padding(horizontal = 9.dp), contentAlignment = Alignment.Center) {
                Texto(UnidadEmp.nombresVista[v] ?: v, Estilo(12f, Peso.bold), t.tinta)
            }
        }
        // HStack(alignment: .firstTextBaseline).
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Texto(u.nombre(loteo), Estilo.titulo3, C.texto, Modifier.alignByBaseline())
            if (sub != null) Texto(sub, Estilo.subhead, C.texto2, Modifier.alignByBaseline())
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Caja("Superficie", u.superficie?.let { "${Formato.numero(it.toInt())} m²" } ?: "—", Modifier.weight(1f))
            Caja("Precio", u.precioTexto, Modifier.weight(1f))
        }
        Texto(nota, Estilo.subhead, C.texto2)
        if (estado.escribe || v != "disponible") {
            BotonPrincipal(if (buscando) "Buscando…" else cta.first, accion, habilitado = !buscando, simbolo = cta.second)
        }
        BotonHundido("Mandar ${if (loteo) "el lote" else "la unidad"} por WhatsApp", mandar, simbolo = "square.and.arrow.up")
        Tocable({ estado.abrir(AFichaPropiedad(id = u.id)) }) { _ -> Texto("La ficha de la propiedad", Estilo.footnoteFuerte, C.accion) }
    }
}

@Composable
private fun Caja(k: String, v: String, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(12.dp)).background(C.hundida).padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Texto(k, Estilo.footnote, C.texto2)
        Texto(v, Estilo.subheadFuerte, C.texto)
    }
}

// MARK: - Piezas de este archivo

/** localizedStandardCompare: los números dentro del texto se comparan como números («2» antes que «10»). */
private fun compararNatural(a: String, b: String): Int {
    val r = Regex("""\d+|\D+""")
    val x = r.findAll(a).map { it.value }.toList()
    val y = r.findAll(b).map { it.value }.toList()
    for (i in 0 until minOf(x.size, y.size)) {
        val p = x[i]; val q = y[i]
        val c = if (p[0].isDigit() && q[0].isDigit()) (p.toBigInteger().compareTo(q.toBigInteger())) else p.compareTo(q, ignoreCase = true)
        if (c != 0) return c
    }
    return x.size - y.size
}

/** TextField(value:format: .number): «1.234,5» o «1234.5» → número. */
private fun numeroDe(s: String): Double? {
    val t = s.trim().replace(" ", "")
    if (t.isEmpty()) return null
    val normal = if (t.contains(',')) t.replace(".", "").replace(',', '.') else t
    return normal.toDoubleOrNull()
}

/** ProgressView(value:).tint(…). */
@Composable
private fun BarraAvance(valor: Double, color: Color) {
    Box(Modifier.fillMaxWidth().height(4.dp).clip(Capsula).background(C.hundida)) {
        Box(Modifier.fillMaxWidth(valor.coerceIn(0.0, 1.0).toFloat()).fillMaxHeight().clip(Capsula).background(color))
    }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.FilaForm(contenido: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, content = contenido)
}
