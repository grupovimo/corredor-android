package ar.com.corredor.app.pantallas.temporada

import ar.com.corredor.app.app.PantallaApilada

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import ar.com.corredor.app.app.BarraDe
import ar.com.corredor.app.app.BotonBarra
import ar.com.corredor.app.app.BotonBarraIcono
import ar.com.corredor.app.app.LocalEstado
import ar.com.corredor.app.nucleo.AAltaPropiedad
import ar.com.corredor.app.nucleo.AFichaPropiedad
import ar.com.corredor.app.nucleo.ASeccion
import ar.com.corredor.app.nucleo.ErrorApi
import ar.com.corredor.app.nucleo.Fecha
import ar.com.corredor.app.nucleo.FechaApi
import ar.com.corredor.app.nucleo.Formato
import ar.com.corredor.app.nucleo.JSONLibre
import ar.com.corredor.app.nucleo.opcional
import ar.com.corredor.app.nucleo.subir
import ar.com.corredor.app.pantallas.propiedades.AReservaTemporada
import ar.com.corredor.app.pantallas.propiedades.ErrorConCampos
import ar.com.corredor.app.pantallas.propiedades.Propiedad
import ar.com.corredor.app.pantallas.propiedades.conCamposComo
import ar.com.corredor.app.tema.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.UUID

// Clon de Pantallas/Temporada/Temporada.swift.

/** Una reserva de temporario (GET /v1/temporada/reservas, …/{id}; dominio/temporada.Reserva). */
@Serializable
data class ReservaTemporada(
    val id: String,
    val propiedadId: String? = null,
    val propiedadTitulo: String? = null,
    val contactoId: String? = null,
    val contactoNombre: String? = null,
    val huespedNombre: String? = null,
    val huespedTelefono: String? = null,
    val huespedes: Int? = null,
    val entrada: String,
    val salida: String,
    val estado: String? = null,
    val monto: Double? = null,
    val moneda: String? = null,
    val sena: Double? = null,
    val saldo: Double? = null,
    val noches: Int? = null,
    val notas: String? = null,
    val checkin: Checkin? = null,
    val llavesEn: FechaApi? = null,
    /** huesped · dueno (uso del dueño: las noches bloqueadas, sin huésped ni plata). */
    val motivo: String? = null,
) {
    @Serializable
    data class Checkin(val saldo: Boolean = false, val deposito: Boolean = false, val inventario: Boolean = false, val dni: List<String> = emptyList())

    val quien: String get() = contactoNombre ?: huespedNombre ?: "Huésped"
    val deDueno: Boolean get() = motivo == "dueno"
}

/** GET /v1/temporada/calendario: por casa, las reservas que caen en la ventana y las noches tomadas. */
@Serializable
data class CalendarioTemporada(val desde: String, val hasta: String, val dias: Int? = null, val propiedades: List<Casa>) {
    @Serializable
    data class Ocupacion(val id: String, val entrada: String, val salida: String, val estado: String, val huesped: String? = null, val motivo: String? = null)

    @Serializable
    data class Casa(val id: String, val numero: Int? = null, val titulo: String? = null, val nochesOcupadas: Int, val ocupacion: Double? = null, val reservas: List<Ocupacion>)
}

/** Una casa de temporario con lo que hace falta para el calendario, las reservas y las tarifas. */
data class CasaTemporada(
    val id: String,
    val direccion: String,
    val foto: String?,
    val ambientes: Int?,
    val destaque: String?,
    val huespedes: Int?,
    val ciudad: String?,
    val precio: Double?,
    val moneda: String,
    val periodo: String?,
    val nochesOcupadas: Int,
    val reservas: List<CalendarioTemporada.Ocupacion>,
    val tarifas: List<TarifaAPI>,
)

/** Las fechas sin hora del temporario (web: temp-fechas.ts): sumar días, contar noches, la tarifa de cada noche, la
 *  cotización y los huecos. */
object TempFechas {
    private fun fecha(d: String): LocalDate? {
        val p = d.take(10).split("-").mapNotNull { it.toIntOrNull() }
        if (p.size != 3) return null
        return runCatching { LocalDate.of(p[0], p[1], p[2]) }.getOrNull()
    }

    private fun texto(f: LocalDate): String = String.format(Locale.US, "%04d-%02d-%02d", f.year, f.monthValue, f.dayOfMonth)

    fun sumarDias(d: String, n: Int): String {
        val f = fecha(d) ?: return d
        return texto(f.plusDays(n.toLong()))
    }

    /** Noches entre la entrada y la salida (la salida no cuenta). */
    fun noches(entrada: String, salida: String): Int {
        val a = fecha(entrada) ?: return 0
        val b = fecha(salida) ?: return 0
        return ChronoUnit.DAYS.between(a, b).toInt()
    }

    /** 0 domingo … 6 sábado. */
    fun diaSemana(d: String): Int = fecha(d)?.let { it.dayOfWeek.value % 7 } ?: 0
    val inicialDia: List<Char> = "DLMMJVS".toList()
    private val mesesNombre = listOf("enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre")

    /** «Diciembre – enero», «Noviembre». */
    fun meses(desde: String, hasta: String): String {
        val a = (desde.drop(5).take(2).toIntOrNull() ?: 1) - 1
        val b = (hasta.drop(5).take(2).toIntOrNull() ?: 1) - 1
        val t = if (a == b) mesesNombre[a] else "${mesesNombre[a]} – ${mesesNombre[b]}"
        return Formato.mayuscula(t)
    }

    /** La temporada de verano: «2026‑27» de julio a junio (con guion que no corta). */
    fun temporadaDe(d: String): String {
        val y = d.take(4).toIntOrNull() ?: 2026
        val m = d.drop(5).take(2).toIntOrNull() ?: 1
        val desde = if (m >= 7) y else y - 1
        return "$desde‑${String.format(Locale.US, "%02d", (desde + 1) % 100)}"
    }

    /** «12/1». */
    fun corto(d: String): String = "${d.drop(8).take(2).toIntOrNull() ?: 0}/${d.drop(5).take(2)}"

    /** La tarifa que rige una noche (la última cargada gana si se pisan). */
    fun tarifaDe(ts: List<TarifaAPI>, d: String): TarifaAPI? {
        var t: TarifaAPI? = null
        for (x in ts) if (x.desde <= d && d <= x.hasta) t = x
        return t
    }

    fun porNoche(t: TarifaAPI): Double = when (t.periodo ?: "temporada") {
        "semana" -> t.precio / 7
        "quincena" -> t.precio / 15
        "mes" -> t.precio / 30
        "temporada" -> t.precio / maxOf(1, noches(t.desde, sumarDias(t.hasta, 1))).toDouble()
        else -> t.precio
    }

    data class Cotizacion(val noches: Int, val total: Double?, val moneda: String, val temporada: String?, val minimo: Int?)

    /** Cuánto sale una estadía: noche por noche la tarifa que rige; las noches sin tarifa van al precio por noche de la
     *  casa (`base`), si tiene. */
    fun cotizar(ts: List<TarifaAPI>, entrada: String, salida: String, base: Pair<Double?, String>?): Cotizacion {
        val n = noches(entrada, salida)
        var total = 0.0
        var falta = false
        var moneda = base?.second ?: ts.firstOrNull()?.moneda ?: "USD"
        val cuenta = mutableMapOf<String, Int>()
        var minimo: Int? = null
        for (i in 0 until maxOf(0, n)) {
            val d = sumarDias(entrada, i)
            val t = tarifaDe(ts, d)
            if (t != null) {
                total += porNoche(t)
                moneda = t.moneda ?: moneda
                cuenta[t.nombre ?: ""] = (cuenta[t.nombre ?: ""] ?: 0) + 1
                t.nochesMinimas?.let { m -> if (n < m) minimo = maxOf(minimo ?: 0, m) }
            } else {
                val p = base?.first
                if (p != null) total += p else falta = true
            }
        }
        val temporada = cuenta.entries.sortedByDescending { it.value }.firstOrNull()?.key?.takeIf { it.isNotEmpty() }
        return Cotizacion(n, if (falta) null else Math.round(total).toDouble(), moneda, temporada, minimo)
    }

    /** Los huecos libres de menos de `tope` noches entre reservas, dentro de la ventana. */
    fun huecos(rs: List<CalendarioTemporada.Ocupacion>, desde: String, hasta: String, tope: Int = 4): Int {
        val r = rs.sortedBy { it.entrada }
        var n = 0
        for (i in 1 until r.size) {
            val libre = noches(r[i - 1].salida, r[i].entrada)
            if (libre > 0 && libre < tope && r[i - 1].salida >= desde && r[i].entrada <= hasta) n += 1
        }
        return n
    }
}

/** Lo que abre la hoja de una reserva nueva: la casa y las fechas ya puestas. */
data class PedidoReservaTemporada(val casa: String, val entrada: String, val salida: String) {
    val id: String get() = casa + entrada
}

private const val DIAS = 31
private val ANCHO = 34.dp

/** 15E Alquiler temporario: «¿qué casa está libre y cuánto cobro esa semana?». Una fila por casa y 31 días a lo ancho
 *  (con las reservas, el uso del dueño, hoy, los fines de semana y la temporada de cada día); tocar un día libre reserva
 *  desde ese día. Arriba, lo de hoy (check-in y check-out) y las señas pendientes; abajo, la lista de las reservas. */
@Composable
fun Temporada() {
    val estado = LocalEstado.current
    val alcance = rememberCoroutineScope()
    val hoy = Formato.hoy().toString()
    var desde by remember { mutableStateOf(hoy) }
    var cal by remember { mutableStateOf<CalendarioTemporada?>(null) }
    var casas by remember { mutableStateOf<List<CasaTemporada>>(emptyList()) }
    var deHoy by remember { mutableStateOf<List<ReservaTemporada>>(emptyList()) }
    var reservas by remember { mutableStateOf<List<ReservaTemporada>>(emptyList()) }
    var error by remember { mutableStateOf<ErrorApi?>(null) }
    var filtro by remember { mutableIntStateOf(0) }
    var nueva by remember { mutableStateOf<PedidoReservaTemporada?>(null) }
    var tarifas by remember { mutableStateOf(false) }

    val hasta = TempFechas.sumarDias(desde, DIAS - 1)
    val ventana = (0 until DIAS).map { TempFechas.sumarDias(desde, it) }

    suspend fun cargar() {
        try {
            val c: CalendarioTemporada = estado.api.get("/v1/temporada/calendario", listOf("desde" to desde, "hasta" to hasta))
            val props: List<Propiedad> = estado.api.opcional<List<Propiedad>>("/v1/propiedades", listOf("operacion" to "temporario", "limite" to "200")) ?: emptyList()
            val porId = props.associateBy { it.id }.toMutableMap()
            for (x in c.propiedades) if (porId[x.id] == null) estado.api.opcional<Propiedad>("/v1/propiedades/${x.id}")?.let { porId[x.id] = it }
            val cs = mutableListOf<CasaTemporada>()
            for (x in c.propiedades) {
                val p = porId[x.id]
                val ts = estado.api.opcional<List<TarifaAPI>>("/v1/propiedades/${x.id}/tarifas") ?: emptyList()
                val huespedes = (p?.ficha?.get("temporario")?.get("huespedes") as? JSONLibre.numero)?.v?.toInt()
                cs.add(CasaTemporada(x.id, p?.direccion ?: x.titulo ?: "Casa", p?.miniatura, p?.ambientes,
                    p?.comodidades?.firstOrNull { it in listOf("Pileta", "Parrilla", "Vista al mar") }, huespedes,
                    p?.ciudad ?: p?.barrio, p?.precio, p?.moneda ?: "USD", p?.periodo, x.nochesOcupadas, x.reservas, ts))
            }
            casas = cs
            cal = c
            error = null
            deHoy = estado.api.opcional<List<ReservaTemporada>>("/v1/temporada/reservas",
                listOf("desde" to TempFechas.sumarDias(hoy, -1), "hasta" to hoy, "estado" to "pendiente,confirmada")) ?: emptyList()
            val rs = estado.api.opcional<List<ReservaTemporada>>("/v1/temporada/reservas", listOf("limite" to "1000")) ?: emptyList()
            reservas = rs.sortedBy { it.entrada }
        } catch (e: CancellationException) { throw e } catch (e: Exception) {
            if (e is ErrorApi) error = e
        }
    }

    val ciudad: String? = run {
        val cs = casas.mapNotNull { it.ciudad }
        cs.maxByOrNull { a -> cs.count { it == a } }
    }
    val pendientes = casas.flatMap { c -> c.reservas.filter { it.estado == "pendiente" && it.motivo != "dueno" } }

    /** Las temporadas que aparecen, de la más barata a la más cara: azul, oro, rojo. */
    val temporadas: List<Pair<TarifaAPI, Tono>> = run {
        val m = mutableMapOf<String, TarifaAPI>()
        for (c in casas) for (t in c.tarifas) { val k = t.nombre ?: ""; if (m[k] == null || t.precio < (m[k]?.precio ?: 0.0)) m[k] = t }
        val xs = m.values.sortedBy { it.precio }
        val tonos = listOf(Tono.azul, Tono.oro, Tono.rojo)
        xs.mapIndexed { i, t -> t to tonos[if (xs.size == 1) 1 else Math.round(i.toDouble() / maxOf(1, xs.size - 1).toDouble() * 2).toInt()] }
    }

    fun tonoDia(d: String): Tono? {
        val t = TempFechas.tarifaDe(casas.flatMap { it.tarifas }, d) ?: return null
        return temporadas.firstOrNull { it.first.nombre == t.nombre }?.second
    }

    fun direccion(r: ReservaTemporada): String = casas.firstOrNull { it.id == r.propiedadId }?.direccion ?: r.propiedadTitulo ?: "Casa"

    /** Tocar un día libre: la reserva desde ese día, con las noches mínimas de la temporada (2 o más). */
    fun reservarDesde(c: CasaTemporada, d: String) {
        val t = TempFechas.tarifaDe(c.tarifas, d)
        nueva = PedidoReservaTemporada(c.id, d, TempFechas.sumarDias(d, maxOf(2, t?.nochesMinimas ?: 2)))
    }

    LaunchedEffect(estado.version, desde) { cargar() }
    LaunchedEffect(filtro) {
        if (filtro == 2 && reservas.none { it.salida < hoy }) {
            val pasadas = estado.api.opcional<List<ReservaTemporada>>("/v1/temporada/reservas", listOf("pasadas" to "si", "limite" to "1000")) ?: emptyList()
            reservas = pasadas.sortedBy { it.entrada }
        }
    }

    PantallaApilada("Temporada", acciones = if (estado.escribe && casas.isNotEmpty()) ({
        BotonBarra("Tarifas") { tarifas = true }
        BotonBarraIcono("plus", "Nueva reserva") { nueva = PedidoReservaTemporada(casas.firstOrNull()?.id ?: "", hoy, TempFechas.sumarDias(hoy, 3)) }
    }) else null) {
        Refrescable({ cargar() }, Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                val e = error
                if (cal != null) {
                    // Cabecera
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Texto("Temporada ${TempFechas.temporadaDe(desde)}${ciudad?.let { " · $it" } ?: ""}", Estilo.titulo3, C.texto)
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(C.superficie), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(40.dp, 36.dp).clickable { desde = TempFechas.sumarDias(desde, -DIAS) }.semantics { contentDescription = "31 días antes" },
                                contentAlignment = Alignment.Center) { Icono("chevron.left", 17f, C.accion) }
                            Spacer(Modifier.weight(1f))
                            Texto(TempFechas.meses(desde, hasta), Estilo.subheadFuerte, C.texto)
                            Spacer(Modifier.weight(1f))
                            Box(Modifier.size(40.dp, 36.dp).clickable { desde = TempFechas.sumarDias(desde, DIAS) }.semantics { contentDescription = "31 días después" },
                                contentAlignment = Alignment.Center) { Icono("chevron.right", 17f, C.accion) }
                        }
                        if (desde != hoy) Texto("Volver a hoy", Estilo.footnoteFuerte, C.accion, Modifier.clickable { desde = hoy })
                    }
                    if (casas.isEmpty()) {
                        Box(Modifier.padding(top = 20.dp)) {
                            EstadoVacio("beach.umbrella", "Todavía no hay casas de temporada",
                                "Una propiedad aparece acá cuando su operación es temporario o cuando además se alquila por temporada.",
                                accion = if (estado.escribe) ("Cargar propiedad" to { estado.abrir(AAltaPropiedad()) }) else null)
                        }
                    } else {
                        // Hoy
                        val entran = deHoy.filter { it.entrada == hoy }
                        val salen = deHoy.filter { it.salida == hoy }
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            TituloBloque("Hoy")
                            Column(Modifier.fillMaxWidth().tarjeta()) {
                                if (entran.isEmpty() && salen.isEmpty()) FilaHoy("calendar.badge.checkmark", Tono.neutro, "Sin entradas ni salidas", null, primera = true) {}
                                entran.forEachIndexed { i, r -> key(r.id) { FilaHoy("key", Tono.oro, direccion(r), "Check-in · ${r.quien}", primera = i == 0) { estado.abrir(AReservaTemporada(r.id)) } } }
                                salen.forEachIndexed { i, r ->
                                    key(r.id) { FilaHoy("rectangle.portrait.and.arrow.right", Tono.azul, direccion(r), "Check-out · ${r.quien}", primera = entran.isEmpty() && i == 0) { estado.abrir(AReservaTemporada(r.id)) } }
                                }
                                FilaHoy("hourglass", Tono.oro, "Señas pendientes", "${pendientes.size}", primera = false) {
                                    pendientes.firstOrNull()?.let { estado.abrir(AReservaTemporada(it.id)) }
                                }
                            }
                        }
                        // Leyenda
                        val ocupado = if (casas.isEmpty()) 0.0 else casas.sumOf { it.nochesOcupadas }.toDouble() / (casas.size * DIAS).toDouble()
                        val huecos = casas.sumOf { TempFechas.huecos(it.reservas, desde, hasta) }
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            temporadas.forEach { (t, tono) ->
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(9.dp).clip(CircleShape).background(tono.tinta))
                                    Texto("${t.nombre ?: ""} · ${Formato.plata(t.precio, t.moneda ?: "USD")}${t.nochesMinimas?.let { " · mín. $it noches" } ?: ""}", Estilo.footnote, C.texto2)
                                }
                            }
                            TextoRico(buildAnnotatedString {
                                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(Formato.porcentaje(Math.round(ocupado * 100).toDouble())) }
                                append(" ocupado · ")
                                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("$huecos ${if (huecos == 1) "hueco" else "huecos"}") }
                                append(" de menos de 4 noches")
                            }, Estilo.footnote, C.texto)
                        }
                        // Calendario
                        Row(Modifier.fillMaxWidth().tarjeta().padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
                            Column(Modifier.width(140.dp).padding(start = 10.dp)) {
                                Box(Modifier.height(40.dp), contentAlignment = Alignment.CenterStart) { Texto("Casa", Estilo.footnoteFuerte, C.texto2) }
                                casas.forEach { c -> key(c.id) { Box(Modifier.height(56.dp), contentAlignment = Alignment.CenterStart) { InfoCasa(c) } } }
                            }
                            Column(Modifier.weight(1f).horizontalScroll(rememberScrollState())) {
                                Row(Modifier.height(40.dp)) { ventana.forEach { d -> CabezaDia(d, hoy, tonoDia(d)) } }
                                casas.forEach { c -> key(c.id) { FilaCasa(c, ventana, desde) { d -> if (estado.escribe) reservarDesde(c, d) } } }
                            }
                        }
                        Texto(Formato.sinViudas("Tocá un día libre de una casa para reservar desde ese día."), Estilo.footnote, C.texto2)
                        // Lista
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            TituloBloque("Reservas")
                            Segmentado(listOf(0 to "Próximas", 1 to "Adentro", 2 to "Pasadas"), filtro, { filtro = it })
                            val l = reservas.filter {
                                when (filtro) {
                                    0 -> it.entrada >= hoy
                                    1 -> it.entrada <= hoy && it.salida >= hoy
                                    else -> it.salida < hoy
                                }
                            }
                            if (l.isEmpty()) {
                                Texto("No hay reservas acá.", Estilo.footnote, C.texto2, Modifier.padding(horizontal = 4.dp))
                            } else {
                                Column(Modifier.fillMaxWidth().tarjeta()) {
                                    l.forEachIndexed { i, r ->
                                        key(r.id) {
                                            if (i > 0) Box(Modifier.fillMaxWidth().height(0.5.dp).background(C.linea))
                                            Tocable({ estado.abrir(AReservaTemporada(r.id)) }) { FilaReservaTemporada(r) }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else if (e != null) {
                    Box(Modifier.padding(top = 30.dp)) { EstadoError("la temporada", e) { alcance.launch { cargar() } } }
                } else EsqueletoFilas(filas = 5)
            }
        }
    }
    Hoja(nueva != null, { nueva = null }) {
        nueva?.let { n -> key(n.id) { NuevaReservaTemporada(casas, n.casa, n.entrada, n.salida) } }
    }
    Hoja(tarifas, { tarifas = false }) { HojaTarifasTemporada(casas) }
}

@Composable
private fun FilaHoy(icono: String, tono: Tono, t: String, d: String?, primera: Boolean, ir: () -> Unit) {
    val linea = C.linea
    Tocable(ir) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 52.dp).drawBehind { if (!primera) drawRect(linea, size = androidx.compose.ui.geometry.Size(size.width, 0.5.dp.toPx())) }
                .padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            IconoTono(icono, tono, tam = 34.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Texto(t, Estilo.filaLista, C.texto, lineas = 1)
                if (d != null) Texto(d, Estilo.footnote, if (tono == Tono.oro) C.avisoTinta else C.texto2)
            }
        }
    }
}

@Composable
private fun InfoCasa(c: CasaTemporada) {
    val estado = LocalEstado.current
    Tocable({ estado.abrir(AFichaPropiedad(c.id)) }) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(34.dp).clip(RoundedCornerShape(8.dp))) { Foto(c.foto, Modifier.matchParentSize()) }
            Column(Modifier.weight(1f)) {
                Texto(c.direccion, Estilo(13f, Peso.semibold), C.texto, lineas = 1)
                Texto(listOfNotNull(c.ambientes?.let { "$it amb" }, c.destaque?.lowercase(), c.huespedes?.let { "$it p." }).joinToString(" · "), Estilo(11f, Peso.regular), C.texto2, lineas = 1)
            }
        }
    }
}

@Composable
private fun CabezaDia(d: String, hoy: String, tono: Tono?) {
    val w = TempFechas.diaSemana(d)
    val color = if (d == hoy) C.sobreAccion else (if (w == 0 || w == 6) C.texto else C.texto2)
    Box(Modifier.width(ANCHO), contentAlignment = Alignment.Center) {
        Column(
            Modifier.size(ANCHO - 2.dp, 36.dp).clip(RoundedCornerShape(8.dp)).background(if (d == hoy) C.accion else (tono?.fondo ?: Color.Transparent)),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(1.dp, Alignment.CenterVertically),
        ) {
            Texto(TempFechas.inicialDia[w].toString(), Estilo(10f, Peso.semibold), color)
            Texto("${d.drop(8).toIntOrNull() ?: 0}", Estilo(13f, Peso.bold), color)
        }
    }
}

@Composable
private fun FilaCasa(c: CasaTemporada, ventana: List<String>, desde: String, tocarLibre: (String) -> Unit) {
    val estado = LocalEstado.current
    val linea = C.linea
    val hundida = C.hundida
    Box(Modifier.height(56.dp).drawBehind { drawRect(linea, size = androidx.compose.ui.geometry.Size(size.width, 0.5.dp.toPx())) }) {
        Row {
            ventana.forEach { d ->
                val ocupado = c.reservas.any { it.entrada <= d && d < it.salida }
                val w = TempFechas.diaSemana(d)
                Box(
                    Modifier.size(ANCHO, 56.dp).background(if (w == 0 || w == 6) hundida.copy(alpha = hundida.alpha * 0.6f) else Color.Transparent)
                        .drawBehind { drawRect(linea, size = androidx.compose.ui.geometry.Size(0.5.dp.toPx(), size.height)) }
                        .clickable { if (!ocupado) tocarLibre(d) }
                        .semantics { contentDescription = if (ocupado) "$d ocupado" else "$d libre, tocá para reservar" },
                )
            }
        }
        c.reservas.forEach { r ->
            key(r.id) {
                val a = maxOf(0, TempFechas.noches(desde, r.entrada))
                val b = minOf(DIAS, TempFechas.noches(desde, r.salida))
                if (b > a) {
                    val dueno = r.motivo == "dueno"
                    val n = TempFechas.noches(r.entrada, r.salida)
                    val texto = if (dueno) "Uso del dueño" else if (r.estado == "pendiente") "Seña pendiente${r.huesped?.let { " · $it" } ?: ""}" else "${r.huesped ?: "Reservada"}${if (n >= 7) " · $n n." else ""}"
                    val tinta = if (dueno) C.texto2 else if (r.estado == "confirmada") C.sobreAccion else C.avisoTinta
                    val fondo = if (dueno) C.hundida else if (r.estado == "confirmada") C.accion else C.avisoFondo
                    val borde = if (r.estado == "pendiente" && !dueno) C.avisoTinta.copy(alpha = 0.5f) else Color.Transparent
                    Tocable({ estado.abrir(AReservaTemporada(r.id)) }, Modifier.align(Alignment.CenterStart).offset(x = ANCHO * a + 2.dp)) {
                        Row(
                            Modifier.size(ANCHO * (b - a) - 4.dp, 30.dp).clip(RoundedCornerShape(8.dp)).background(fondo)
                                .drawBehind {
                                    drawRoundRect(borde, cornerRadius = CornerRadius(8.dp.toPx()), style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.dp.toPx()))))
                                }.padding(horizontal = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icono(if (dueno) "nosign" else if (r.estado == "confirmada") "person.fill" else "hourglass", 11f, tinta)
                            Texto(texto, Estilo(11f, Peso.bold), tinta, lineas = 1)
                        }
                    }
                }
            }
        }
    }
}

/** Una reserva en la lista: el huésped (o «Uso del dueño»), la casa, las fechas y el saldo. */
@Composable
fun FilaReservaTemporada(r: ReservaTemporada) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        IconoTono(
            if (r.deDueno) "nosign" else if (r.estado == "confirmada") "person.fill" else if (r.estado == "cancelada") "xmark" else "hourglass",
            if (r.deDueno) Tono.neutro else if (r.estado == "confirmada") Tono.verde else if (r.estado == "cancelada") Tono.neutro else Tono.oro, tam = 38.dp,
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Texto(if (r.deDueno) "Uso del dueño" else r.quien, Estilo.filaLista, C.texto)
            Texto("${r.propiedadTitulo ?: ""} · ${TempFechas.corto(r.entrada)} al ${TempFechas.corto(r.salida)}${if (r.deDueno) "" else " · ${Formato.cantidad(r.huespedes ?: 1, "persona", "personas")}"}",
                Estilo.footnote, C.texto2, lineas = 2)
            val s = r.saldo
            if (!r.deDueno && r.estado == "pendiente") Texto("Seña pendiente", Estilo(12f, Peso.bold), C.avisoTinta)
            else if (s != null && s > 0 && !r.deDueno) Texto("Saldo ${Formato.plata(s, r.moneda ?: "ARS")}", Estilo(12f, Peso.bold), C.errorTinta)
        }
        Chevron()
    }
}

@Serializable
private data class HuespedReserva(
    val propiedadId: String, val huespedNombre: String, val huespedTelefono: String?, val huespedes: Int?,
    val entrada: String, val salida: String, val estado: String, val monto: Double?, val moneda: String, val sena: Double?,
)

@Serializable
private data class DuenoReserva(val propiedadId: String, val motivo: String = "dueno", val entrada: String, val salida: String, val notas: String?)

/** Nueva reserva de temporada (o uso del dueño): la casa y las fechas ya puestas, la cotización sola según las tarifas
 *  de cada noche, el huésped y la seña. Con seña queda confirmada; sin seña, pendiente. Se crea al toque, con deshacer. */
@Composable
fun NuevaReservaTemporada(casas: List<CasaTemporada>, casaInicial: String, entradaInicial: String, salidaInicial: String) {
    val estado = LocalEstado.current
    val cerrar = LocalCerrarHoja.current
    val alcance = rememberCoroutineScope()
    var casa by remember { mutableStateOf(casaInicial) }
    var entrada by remember { mutableStateOf(entradaInicial) }
    var salida by remember { mutableStateOf(salidaInicial) }
    var motivo by remember { mutableStateOf("huesped") }
    var nombre by remember { mutableStateOf("") }
    var telefono by remember { mutableStateOf("") }
    var huespedes by remember { mutableStateOf<Int?>(null) }
    var total by remember { mutableStateOf<Double?>(null) }
    var totalPropio by remember { mutableStateOf(false) }
    var moneda by remember { mutableStateOf("USD") }
    var sena by remember { mutableStateOf<Double?>(null) }
    var errores by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var enviando by remember { mutableStateOf(false) }
    val envio = remember { UUID.randomUUID().toString() }

    val elegida = casas.firstOrNull { it.id == casa }
    val cot: TempFechas.Cotizacion? = if (elegida != null && salida > entrada)
        TempFechas.cotizar(elegida.tarifas, entrada, salida, if (elegida.periodo == "noche") (elegida.precio to elegida.moneda) else null) else null

    fun recotizar() {
        if (totalPropio) return
        total = cot?.total
        moneda = cot?.moneda ?: elegida?.moneda ?: "USD"
    }

    LaunchedEffect(casa, entrada, salida) { totalPropio = false; recotizar() }

    suspend fun crear() {
        val c = elegida
        if (c == null || salida <= entrada) { errores = mapOf("salida" to "La salida tiene que ser después de la entrada."); return }
        enviando = true
        errores = emptyMap()
        val dueno = motivo == "dueno"
        val n = TempFechas.noches(entrada, salida)
        try {
            val r: ReservaTemporada = if (dueno) {
                estado.api.conCamposComo("POST", "/v1/temporada/reservas", DuenoReserva(propiedadId = casa, entrada = entrada, salida = salida, notas = nombre.ifEmpty { null }), envio)
            } else {
                estado.api.conCamposComo("POST", "/v1/temporada/reservas",
                    HuespedReserva(casa, nombre.trim(), telefono.ifEmpty { null }, huespedes, entrada, salida, if ((sena ?: 0.0) > 0) "confirmada" else "pendiente", total, moneda, sena), envio)
            }
            estado.version += 1
            cerrar()
            estado.avisar(if (dueno) "Bloqueada ${c.direccion} · $n noches de uso del dueño" else "Reservada ${c.direccion} · $n noches") {
                estado.tarea {
                    try { estado.api.hacer("DELETE", "/v1/temporada/reservas/${r.id}"); estado.version += 1 } catch (e: CancellationException) { throw e } catch (e: Exception) { estado.avisar(e) }
                }
            }
        } catch (e: CancellationException) { throw e } catch (e: ErrorConCampos) {
            errores = e.campos
        } catch (e: Exception) { estado.avisar(e) }
        enviando = false
    }

    @Composable
    fun error(k: String) { errores[k]?.let { Box(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) { MensajeError(it) } } }

    MarcoHoja(
        if (motivo == "dueno") "Uso del dueño" else "Nueva reserva",
        confirmar = if (enviando) (if (motivo == "dueno") "Bloqueando…" else "Reservando…") else (if (motivo == "dueno") "Bloquear" else "Reservar"),
        confirmarHabilitado = !(enviando || elegida == null), alConfirmar = { alcance.launch { crear() } },
    ) {
        Bloque(pie = if (motivo == "dueno") "Las noches quedan bloqueadas en el calendario, sin huésped ni cobro." else "Con seña queda confirmada y ocupa la casa; sin seña, pendiente.") {
            Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) { Segmentado(listOf("huesped" to "Huésped", "dueno" to "Uso del dueño"), motivo, { motivo = it }) }
        }
        Bloque {
            RenglonSelector("Casa", casas.map { it.id to it.direccion }, casa, { casa = it })
            Separador()
            RenglonFecha("Entrada", diaTemporada(entrada), { entrada = it.toString() })
            error("entrada")
            Separador()
            RenglonFecha("Salida", diaTemporada(salida), { salida = it.toString() })
            error("salida")
            if (cot != null && motivo == "huesped") {
                Separador()
                Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Texto("${Formato.cantidad(cot.noches, "noche", "noches")}${cot.total?.let { " · ${Formato.plata(it, cot.moneda)}" } ?: ""}${cot.temporada?.let { " · temporada ${it.lowercase()}" } ?: ""}",
                        Estilo.subheadFuerte, C.texto)
                    cot.minimo?.let { m -> Texto("Esa temporada pide mínimo $m noches.", Estilo.footnote, C.avisoTinta) }
                    if (cot.total == null) Texto("Faltan tarifas para alguna noche: poné el total a mano.", Estilo.footnote, C.avisoTinta)
                }
            }
        }
        if (motivo == "dueno") {
            Bloque("Nota (opcional)") {
                RenglonCampo("", nombre, { nombre = it }, "Viene con la familia")
                error("notas")
            }
        } else {
            Bloque("Huésped") {
                RenglonCampo("", nombre, { nombre = it }, "Huésped")
                (errores["huespedNombre"] ?: errores["contactoId"])?.let { Box(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) { MensajeError(it) } }
                Separador()
                RenglonCampo("", telefono, { telefono = it }, "Teléfono (opcional)", teclado = KeyboardType.Phone)
                Separador()
                Renglon(huespedes?.let { Formato.cantidad(it, "persona", "personas") } ?: "Personas (opcional)") {
                    Pasador(huespedes ?: 0, { huespedes = if (it == 0) null else it }, 0..(elegida?.huespedes ?: 40))
                }
                error("huespedes")
            }
            Bloque("Plata") {
                Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) { Segmentado(listOf("USD" to "USD", "ARS" to "ARS"), moneda, { moneda = it }) }
                Separador()
                RenglonPesos("Total", total, { total = it; totalPropio = true }, moneda = "")
                error("monto")
                Separador()
                RenglonPesos("Seña recibida", sena, { sena = it }, moneda = "")
                error("sena")
            }
        }
    }
}

@Serializable
private data class ContactoTelefono(val telefono: String? = null)

@Serializable
private data class CuerpoCheckin(val saldo: Boolean, val deposito: Boolean, val inventario: Boolean, val dni: List<String>, val llaves: Boolean?)

@Serializable
private data class EstadoReserva(val estado: String)

/** 15C Check-in de una reserva: el estado («Check-in hoy 14:00», «En la casa», «Check-out hoy»…), el huésped con su
 *  WhatsApp, la lista de la puerta (saldo, depósito, los DNI con la cámara, inventario rápido) y «Entregar llaves», con
 *  deshacer. Cada tilde se guarda sola. El uso del dueño muestra las noches y «Liberar las noches». */
@Composable
fun CheckinTemporada(id: String) {
    val estado = LocalEstado.current
    val ctx = LocalContext.current
    val alcance = rememberCoroutineScope()
    var r by remember { mutableStateOf<ReservaTemporada?>(null) }
    var casa by remember { mutableStateOf<Propiedad?>(null) }
    var telefono by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<ErrorApi?>(null) }
    var subiendo by remember { mutableStateOf(false) }

    val hoy = Formato.hoy().toString()
    fun temporario(k: String): JSONLibre? = casa?.ficha?.get("temporario")?.get(k)
    val checkIn = temporario("checkIn")?.texto
    val checkOut = temporario("checkOut")?.texto
    val deposito = (temporario("deposito") as? JSONLibre.numero)?.v

    suspend fun cargar() {
        try {
            val x: ReservaTemporada = estado.api.get("/v1/temporada/reservas/$id")
            r = x
            error = null
            x.propiedadId?.let { p -> casa = estado.api.opcional<Propiedad>("/v1/propiedades/$p") }
            x.contactoId?.let { c -> telefono = estado.api.opcional<ContactoTelefono>("/v1/contactos/$c")?.telefono }
        } catch (e: CancellationException) { throw e } catch (e: Exception) {
            if (e is ErrorApi) error = e
        }
    }

    /** Manda la lista entera de la puerta (la API la reemplaza) y, si viene, las llaves. */
    suspend fun guardar(saldo: Boolean? = null, deposito: Boolean? = null, inventario: Boolean? = null, dni: List<String>? = null, llaves: Boolean? = null): Boolean {
        val antes = r ?: return false
        var l = antes.checkin ?: ReservaTemporada.Checkin()
        if (saldo != null) l = l.copy(saldo = saldo)
        if (deposito != null) l = l.copy(deposito = deposito)
        if (inventario != null) l = l.copy(inventario = inventario)
        if (dni != null) l = l.copy(dni = dni)
        var nueva = antes.copy(checkin = l)
        if (llaves != null) nueva = nueva.copy(llavesEn = if (llaves) Instant.now() else null)
        r = nueva
        return try {
            val x: ReservaTemporada = estado.api.enviar("PUT", "/v1/temporada/reservas/${antes.id}/checkin", CuerpoCheckin(l.saldo, l.deposito, l.inventario, l.dni, llaves))
            r = x
            true
        } catch (e: CancellationException) { throw e } catch (e: Exception) {
            r = antes
            estado.avisar(e)
            false
        }
    }

    suspend fun entregarLlaves(x: ReservaTemporada) {
        if (guardar(llaves = true)) {
            Haptico.exito()
            estado.avisar("Llaves entregadas a ${x.quien.split(" ").firstOrNull() ?: x.quien}") { alcance.launch { guardar(llaves = false) } }
        }
    }

    suspend fun subirDNI(fotos: List<ByteArray>) {
        if (fotos.isEmpty()) return
        subiendo = true
        try {
            val ids = mutableListOf<String>()
            for (f in fotos) {
                val jpg = withContext(Dispatchers.Default) {
                    BitmapFactory.decodeByteArray(f, 0, f.size)?.let { b -> ByteArrayOutputStream().also { b.compress(Bitmap.CompressFormat.JPEG, 80, it) }.toByteArray() } ?: f
                }
                if (jpg.size > (25 shl 20)) { estado.avisar("El archivo pasa de 25 MB.", ok = false); continue }
                ids.add(estado.api.subir(jpg, "dni-${System.currentTimeMillis() / 1000}.jpg", "image/jpeg", "documento").id)
            }
            guardar(dni = (r?.checkin?.dni ?: emptyList()) + ids)
        } catch (e: CancellationException) { throw e } catch (e: Exception) { estado.avisar(e) }
        subiendo = false
    }

    suspend fun liberar(x: ReservaTemporada) {
        try {
            val y: ReservaTemporada = estado.api.enviar("PATCH", "/v1/temporada/reservas/${x.id}", EstadoReserva("cancelada"))
            r = y
            estado.version += 1
            estado.avisar("Liberadas ${Formato.cantidad(x.noches ?: 0, "noche", "noches")} de ${casa?.direccion ?: "la casa"}") {
                alcance.launch {
                    try { val z: ReservaTemporada = estado.api.enviar("PATCH", "/v1/temporada/reservas/${x.id}", EstadoReserva("confirmada")); r = z; estado.version += 1 }
                    catch (e: CancellationException) { throw e } catch (e: Exception) { estado.avisar(e) }
                }
            }
        } catch (e: CancellationException) { throw e } catch (e: Exception) { estado.avisar(e) }
    }

    suspend fun poner(x: ReservaTemporada, e: String) {
        try {
            val y: ReservaTemporada = estado.api.enviar("PATCH", "/v1/temporada/reservas/${x.id}", EstadoReserva(e))
            r = y
            estado.version += 1
            estado.avisar(if (e == "cancelada") "Reserva cancelada" else "Reserva confirmada") {
                alcance.launch {
                    try { val z: ReservaTemporada = estado.api.enviar("PATCH", "/v1/temporada/reservas/${x.id}", EstadoReserva(x.estado ?: "pendiente")); r = z; estado.version += 1 }
                    catch (ex: CancellationException) { throw ex } catch (ex: Exception) { estado.avisar(ex) }
                }
            }
        } catch (ex: CancellationException) { throw ex } catch (ex: Exception) { estado.avisar(ex) }
    }

    fun titular(x: ReservaTemporada): String {
        val h = checkIn?.let { " $it" } ?: ""
        if (x.deDueno) return if (x.estado == "cancelada") "Bloqueo liberado" else "Uso del dueño"
        if (x.estado == "cancelada") return "Reserva cancelada"
        if (x.entrada == hoy) return "Check-in hoy$h"
        if (x.entrada > hoy) return "Check-in el ${Fecha.dia(x.entrada)?.let { Formato.fechaLarga(it) } ?: x.entrada}$h"
        if (x.salida > hoy) return "En la casa"
        if (x.salida == hoy) return "Check-out hoy${checkOut?.let { " $it" } ?: ""}"
        return "Terminó la estadía"
    }

    fun rango(x: ReservaTemporada): String =
        if (x.entrada.drop(5).take(2) == x.salida.drop(5).take(2)) "${x.entrada.drop(8).toIntOrNull() ?: 0} al ${TempFechas.corto(x.salida)}"
        else "${TempFechas.corto(x.entrada)} al ${TempFechas.corto(x.salida)}"

    val camara = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { b ->
        if (b != null) {
            val d = ByteArrayOutputStream().also { b.compress(Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray()
            alcance.launch { subirDNI(listOf(d)) }
        }
    }
    val fototeca = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { uris ->
        if (uris.isNotEmpty()) alcance.launch {
            val datos = mutableListOf<ByteArray>()
            for (u in uris) {
                try { withContext(Dispatchers.IO) { ctx.contentResolver.openInputStream(u)?.use { it.readBytes() } }?.let { datos.add(it) } }
                catch (e: CancellationException) { throw e } catch (e: Exception) { estado.avisar(e) }
            }
            subirDNI(datos)
        }
    }

    LaunchedEffect(estado.version) { cargar() }

    PantallaApilada("Check-in") {
        ConBarraAccion(
            botones = {
                val x = r
                if (x != null && estado.escribe) {
                    if (x.deDueno) {
                        BotonHundido("Liberar las noches", { alcance.launch { liberar(x) } }, simbolo = "lock.open", habilitado = x.estado != "cancelada")
                    } else if (x.llavesEn != null) {
                        BotonHundido("Llaves entregadas · ${Formato.horaDe(x.llavesEn)}", { alcance.launch { guardar(llaves = false) } }, simbolo = "checkmark.circle")
                    } else {
                        BotonPrincipal("Entregar llaves", { alcance.launch { entregarLlaves(x) } }, habilitado = x.estado != "cancelada", simbolo = "key.fill")
                    }
                }
            },
            modifier = Modifier,
        ) { abajo ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                val x = r
                val e = error
                if (x != null) {
                    Column(Modifier.padding(bottom = maxOf(100.dp, abajo)), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        // Portada
                        Box(Modifier.fillMaxWidth().height(200.dp)) {
                            Foto(casa?.portada, Modifier.matchParentSize())
                            Box(Modifier.matchParentSize().background(Brush.verticalGradient(0.5f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.65f))))
                            Column(Modifier.align(Alignment.BottomStart).padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Texto(titular(x), Estilo.subheadFuerte, C.oro)
                                Texto(casa?.direccion ?: x.propiedadTitulo ?: "", Estilo.titulo3, Color.White)
                            }
                        }
                        if (x.deDueno) {
                            Row(Modifier.padding(horizontal = 16.dp).fillMaxWidth().tarjeta().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                IconoTono("nosign", Tono.neutro, tam = 44.dp)
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                    Texto("Uso del dueño", Estilo.filaLista, C.texto)
                                    Texto("${rango(x)} · ${Formato.cantidad(x.noches ?: 0, "noche", "noches")}${x.notas?.let { " · $it" } ?: ""}", Estilo.footnote, C.texto2)
                                }
                            }
                        } else {
                            // Huésped
                            Row(Modifier.padding(horizontal = 16.dp).fillMaxWidth().tarjeta().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                AvatarIniciales(x.quien, tam = 44.dp, fondo = C.azulFondo, tinta = C.azulTinta)
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                    Texto(x.quien, Estilo.filaLista, C.texto)
                                    Texto(listOfNotNull(x.huespedes?.let { Formato.cantidad(it, "persona", "personas") }, rango(x), Formato.cantidad(x.noches ?: 0, "noche", "noches")).joinToString(" · "),
                                        Estilo.footnote, C.texto2)
                                }
                                (telefono ?: x.huespedTelefono)?.let { t ->
                                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(C.okFondo).clickable { Afuera.whatsapp(t) }
                                        .semantics { contentDescription = "Escribirle a ${x.quien} por WhatsApp" }, contentAlignment = Alignment.Center) {
                                        Icono("bubble.left.fill", 17f, C.okTinta)
                                    }
                                }
                            }
                            // Lista de la puerta
                            val l = x.checkin ?: ReservaTemporada.Checkin()
                            val personas = x.huespedes
                            val dniListo = l.dni.isNotEmpty() && (personas == null || l.dni.size >= (personas ?: 0))
                            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Column(Modifier.fillMaxWidth().tarjeta()) {
                                    Tilde(l.saldo, "Saldo cobrado", x.saldo?.let { Formato.plata(it, x.moneda ?: "ARS") }, primera = true) { alcance.launch { guardar(saldo = !l.saldo) } }
                                    Tilde(l.deposito, "Depósito en garantía", deposito?.let { Formato.plata(it, temporario("moneda")?.texto ?: x.moneda ?: "ARS") }, primera = false) {
                                        alcance.launch { guardar(deposito = !l.deposito) }
                                    }
                                    Box(Modifier.fillMaxWidth().height(0.5.dp).background(C.linea))
                                    MenuBoton(listOf(
                                        OpcionDeMenu("Sacar la foto", "camera") { runCatching { camara.launch(null) } },
                                        OpcionDeMenu("Elegir de Fotos", "photo.on.rectangle") { fototeca.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                                    ), "DNI") {
                                        FilaTilde(dniListo, "DNI ${personas?.let { "de los $it" } ?: "de los huéspedes"}${if (l.dni.isEmpty()) "" else " · ${Formato.cantidad(l.dni.size, "foto", "fotos")}"}",
                                            null, icono = if (subiendo) null else "camera.fill", cargando = subiendo)
                                    }
                                    Box(Modifier.fillMaxWidth().height(0.5.dp).background(C.linea))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(Modifier.padding(start = 14.dp).clickable { alcance.launch { guardar(inventario = !l.inventario) } }.semantics { contentDescription = "Inventario rápido hecho" }) {
                                            Caja(l.inventario)
                                        }
                                        Tocable({ x.propiedadId?.let { estado.abrir(ASeccion(clave = "actas", id = it)) } }, Modifier.weight(1f)) {
                                            Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                                Texto("Inventario rápido", Estilo.body16, C.accion, Modifier.weight(1f))
                                                Texto("desde el anterior", Estilo.footnote, C.texto3)
                                            }
                                        }
                                    }
                                }
                                Texto("Check-out el ${Fecha.dia(x.salida)?.let { Formato.fechaLarga(it) } ?: x.salida}${checkOut?.let { " a las $it" } ?: ""}.", Estilo.footnote, C.texto2,
                                    Modifier.padding(horizontal = 4.dp))
                            }
                        }
                        // Accesos
                        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            TituloBloque("Esta reserva")
                            Column(Modifier.fillMaxWidth().tarjeta()) {
                                Acceso("calendar", "Calendario", primera = true) { estado.abrir(ASeccion(clave = "temporada")) }
                                x.propiedadId?.let { p ->
                                    Acceso("checklist", "Inventario de la casa") { estado.abrir(ASeccion(clave = "actas", id = p)) }
                                    Acceso("house", "Ficha de la casa") { estado.abrir(AFichaPropiedad(p)) }
                                }
                                if (estado.escribe && !x.deDueno && x.estado != "cancelada") {
                                    if (x.estado == "pendiente") Acceso("checkmark.circle", "Confirmar la reserva") { alcance.launch { poner(x, "confirmada") } }
                                    Acceso("xmark.circle", "Cancelar la reserva") { alcance.launch { poner(x, "cancelada") } }
                                }
                            }
                        }
                    }
                } else if (e != null) {
                    Box(Modifier.padding(top = 60.dp)) { EstadoError("la reserva", e) { alcance.launch { cargar() } } }
                } else Box(Modifier.padding(16.dp)) { EsqueletoFilas(filas = 4) }
            }
        }
    }
}

@Composable
private fun Caja(on: Boolean) {
    Box(
        Modifier.size(26.dp).clip(RoundedCornerShape(7.dp)).background(if (on) C.accion else Color.Transparent)
            .border(1.5.dp, if (on) Color.Transparent else C.linea2, RoundedCornerShape(7.dp)),
        contentAlignment = Alignment.Center,
    ) { if (on) Icono("checkmark", 14f, C.sobreAccion) }
}

@Composable
private fun FilaTilde(on: Boolean, t: String, valor: String?, icono: String? = null, cargando: Boolean = false) {
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Caja(on)
        Texto(t, Estilo.body16, C.texto, Modifier.weight(1f))
        if (valor != null) Texto(valor, Estilo.subheadFuerte, C.texto)
        if (cargando) Rueda() else if (icono != null) Icono(icono, 17f, C.okTinta)
    }
}

@Composable
private fun Tilde(on: Boolean, t: String, valor: String?, primera: Boolean, hacer: () -> Unit) {
    if (!primera) Box(Modifier.fillMaxWidth().height(0.5.dp).background(C.linea))
    Tocable(hacer, Modifier.semantics { selected = on }) { FilaTilde(on, t, valor) }
}

@Composable
private fun Acceso(i: String, t: String, primera: Boolean = false, hacer: () -> Unit) {
    if (!primera) Box(Modifier.fillMaxWidth().height(0.5.dp).background(C.linea))
    Tocable(hacer) {
        Row(Modifier.fillMaxWidth().heightIn(min = 50.dp).padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(24.dp), contentAlignment = Alignment.Center) { Icono(i, 17f, C.accion) }
            Texto(t, Estilo.body16, C.texto, Modifier.weight(1f))
            Chevron()
        }
    }
}
