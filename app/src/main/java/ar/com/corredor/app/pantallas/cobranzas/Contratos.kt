package ar.com.corredor.app.pantallas.cobranzas

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import ar.com.corredor.app.app.*
import ar.com.corredor.app.nucleo.*
import ar.com.corredor.app.tema.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/** Un contrato (GET /v1/contratos). */
@Serializable
data class ContratoAPI(
    val id: String,
    val tipo: String? = null,
    val estado: String? = null,
    val propiedadId: String? = null,
    val contactoId: String? = null,
    val propietarioId: String? = null,
    val agenteId: String? = null,
    val inicio: String? = null,
    val fin: String? = null,
    val monto: Double? = null,
    val moneda: String? = null,
    val ajusteMeses: Int? = null,
    val ajusteIndice: String? = null,
    val ajustePorcentaje: Double? = null,
    val comision: Double? = null,
    val deposito: Double? = null,
    val diaVencimiento: Int? = null,
    val garanteNombre: String? = null,
    val garanteTelefono: String? = null,
    val garanteNotas: String? = null,
    val notas: String? = null,
    val honorarios: Double? = null,
    val punitorioDiario: Double? = null,
    val propiedadTitulo: String? = null,
    val contactoNombre: String? = null,
    val propietarioNombre: String? = null,
) {
    val ajusteTexto: String
        get() {
            val i = ajusteIndice ?: return "Sin ajuste"
            val m = ajusteMeses ?: return "Sin ajuste"
            return if (i == "fijo") "${Formato.numero((ajustePorcentaje ?: 0.0).toInt())} % cada $m meses" else "${i.uppercase()} cada $m meses"
        }

    /** «ICL · 6 m», «Fijo 12 % · 4 m», «Sin ajuste» (la etiqueta de la lista). */
    val etiquetaIndice: String
        get() {
            val m = ajusteMeses
            if (m == null || m <= 0) return "Sin ajuste"
            val i = ajusteIndice?.let { TextosCobro.nombreIndice[it] } ?: "Ajuste"
            val pct = if (ajusteIndice == "fijo" && (ajustePorcentaje ?: 0.0) > 0) " ${TextosCobro.numero(ajustePorcentaje ?: 0.0)} %" else ""
            return "$i$pct · $m m"
        }

    /** «ICL cada 6 meses», «12 % fijo cada 4 meses». */
    val indiceCada: String get() = TextosCobro.indiceCada(ajusteIndice, ajusteMeses, ajustePorcentaje)
}

/** Un ajuste o un vencimiento próximo (GET /v1/contratos/vencimientos?dias=90). */
@Serializable
data class EventoContrato(
    val contratoId: String,
    val tipo: String,
    val fecha: String,
    val dias: Int,
    val monto: Double? = null,
    val moneda: String? = null,
    /** A quién ya se le avisó este ajuste (inquilino, propietario). */
    val avisados: List<String>? = null,
)

/** Lo que Contratos declara adentro en iOS (los tipos y los static). */
object Contratos {
    sealed class Proximo {
        data class debe(val periodo: String, val vence: String, val monto: Double, val meses: Int, val total: Double) : Proximo()
        data class ajuste(val fecha: String, val dias: Int, val monto: Double?, val avisado: Boolean) : Proximo()
        data class vence(val fecha: String, val dias: Int) : Proximo()
        data class aldia(val ajuste: String?) : Proximo()
        data object terminado : Proximo()
    }

    data class Fila(val k: ContratoAPI, val proximo: Proximo, val orden: Double, val actual: Double) {
        val id: String get() = k.id
    }

    data class Prox(val texto: String, val icono: String, val color: Color, val cta: String?)

    /** «12 nov». */
    fun diaMes(f: LocalDate): String = DateTimeFormatter.ofPattern("d MMM", Formato.argentina).format(f).replace(".", "")

    /** La próxima fecha de ajuste según la periodicidad (sin monto: para «ajusta en marzo»). */
    fun proximaFechaAjuste(c: ContratoAPI, hoy: String): String? {
        val cada = c.ajusteMeses ?: return null
        if (cada <= 0) return null
        val ini = Fecha.fecha(c.inicio) ?: return null
        val dia = ini.dayOfMonth
        for (k in 1 until 400) {
            val primero = ini.withDayOfMonth(1).plusMonths((k * cada).toLong())
            val f = primero.withDayOfMonth(minOf(dia, primero.lengthOfMonth()))
            val s = Formato.diaISO(f)
            val fin = c.fin
            if (fin != null && s > fin) return null
            if (s > hoy) return s
        }
        return null
    }
}

/** Contratos (21E): ordenados por lo próximo (una deuda, un ajuste, un vencimiento), con el alquiler de
 *  hoy (el del mes, ya ajustado) y el botón para lo que haya que hacer: Cobrar, Avisar o Preguntar. */
@Composable
fun Contratos(
    /** `ajustan`, `vencen` o `todos` (también los que terminaron); null: vigentes y los que deben. */
    vista: String? = null,
    indice: String? = null,
) {
    val estado = LocalEstado.current
    val alcance = rememberCoroutineScope()
    var todos by remember { mutableStateOf<List<ContratoAPI>?>(null) }
    var deudas by remember { mutableStateOf<Map<String, DeudaContrato>>(emptyMap()) }
    var actual by remember { mutableStateOf<Map<String, Double>>(emptyMap()) }
    var ajustes by remember { mutableStateOf<Map<String, EventoContrato>>(emptyMap()) }
    var vencimientos by remember { mutableStateOf<Map<String, EventoContrato>>(emptyMap()) }
    var filtro by remember { mutableStateOf("vigentes") }
    var nuevo by remember { mutableStateOf(false) }
    var leerPDF by remember { mutableStateOf(false) }
    var cobrando by remember { mutableStateOf<ACobrar?>(null) }
    var mensaje by remember { mutableStateOf<PedidoMensaje?>(null) }
    var error by remember { mutableStateOf<ErrorApi?>(null) }
    var listo by remember { mutableStateOf(false) }

    val hoy = Formato.hoy().toString()

    val filas: List<Contratos.Fila> = (todos ?: emptyList()).map { c ->
        val aj = ajustes[c.id]
        val ve = vencimientos[c.id]
        val d = deudas[c.id]
        val viejo = d?.meses?.firstOrNull()
        val p: Contratos.Proximo
        val orden: Double
        if (d != null && viejo != null) {
            p = Contratos.Proximo.debe(viejo.periodo, viejo.vence ?: viejo.periodo, viejo.esperado ?: 0.0, d.meses.size, d.total ?: 0.0)
            orden = -1000.0 - d.meses.size
        } else if (c.estado != "vigente" && c.estado != null) {
            p = Contratos.Proximo.terminado
            orden = 10_000.0
        } else if (aj != null && aj.dias <= 30) {
            p = Contratos.Proximo.ajuste(aj.fecha, aj.dias, aj.monto, aj.avisados?.contains("inquilino") == true)
            orden = aj.dias.toDouble()
        } else if (ve != null) {
            p = Contratos.Proximo.vence(ve.fecha, ve.dias)
            orden = ve.dias.toDouble()
        } else {
            val f = if (c.tipo == "alquiler") Contratos.proximaFechaAjuste(c, hoy) else null
            p = Contratos.Proximo.aldia(f)
            orden = 1000.0 + (f?.let { Cobranzas.diasEntre(hoy, it).toDouble() } ?: 500.0)
        }
        Contratos.Fila(c, p, orden, actual[c.id] ?: c.monto ?: 0.0)
    }.sortedWith(compareBy<Contratos.Fila> { it.orden }.thenBy { it.k.propiedadTitulo ?: "" })

    fun deber(f: Contratos.Fila) = f.proximo is Contratos.Proximo.debe
    fun ajusta(f: Contratos.Fila) = f.proximo is Contratos.Proximo.ajuste

    val lista: List<Contratos.Fila> = run {
        var l = filas.filter { it.k.estado == "vigente" || it.k.estado == null || deber(it) || filtro == "todos" }
        when (filtro) {
            "ajustan" -> l = l.filter { ajusta(it) }
            "vencen" -> l = l.filter { vencimientos[it.id] != null }
            "icl", "ipc", "uva", "fijo" -> l = l.filter { it.k.ajusteIndice == filtro }
        }
        l
    }

    val vigentes = (todos ?: emptyList()).filter { it.estado == "vigente" }
    val ajustan30 = vigentes.count { (ajustes[it.id]?.dias ?: 99) <= 30 }
    val avisados30 = vigentes.count { k -> ajustes[k.id]?.let { it.dias <= 30 && it.avisados?.contains("inquilino") == true } ?: false }
    val vencen90 = vigentes.count { vencimientos[it.id] != null }
    val mensual: String = run {
        val m = mutableMapOf<String, Double>()
        for (f in filas) if (f.k.estado == "vigente" && f.k.tipo == "alquiler") { val mon = f.k.moneda ?: "ARS"; m[mon] = (m[mon] ?: 0.0) + f.actual }
        m.keys.sorted().joinToString(" + ") { mon ->
            val v = m[mon] ?: 0.0
            if (v >= 1_000_000) "$mon ${TextosCobro.numero(Math.round(v / 100_000).toDouble() / 10)} millones" else Formato.plata(v, mon)
        }
    }
    val nombreFiltro: String? = when (filtro) {
        "ajustan" -> "Ajustan en 30 días"
        "vencen" -> "Vencen en 90 días"
        "icl", "ipc", "uva" -> filtro.uppercase()
        "fijo" -> "Aumento fijo"
        "todos" -> "Todos"
        else -> null
    }

    fun prox(f: Contratos.Fila, error: Color, oro: Color, ok: Color, gris: Color): Contratos.Prox = when (val p = f.proximo) {
        is Contratos.Proximo.debe -> {
            val d = maxOf(0, Cobranzas.diasEntre(p.vence, hoy))
            val que = if (p.meses > 1) "${p.meses} meses" else TextosCobro.nombreMes(p.periodo)
            Contratos.Prox("Debe $que · ${if (d == 1) "1 día" else "$d días"}", "exclamationmark.circle", error, "Cobrar")
        }
        is Contratos.Proximo.ajuste -> {
            val dia = Fecha.fecha(p.fecha)?.let { Contratos.diaMes(it) } ?: p.fecha
            Contratos.Prox("Ajusta el $dia${p.monto?.let { " → ${Formato.plata(it, f.k.moneda ?: "ARS")}" } ?: ""}",
                if (p.avisado) "checkmark.circle" else "chart.line.uptrend.xyaxis", oro, if (p.avisado) null else "Avisar")
        }
        is Contratos.Proximo.vence -> Contratos.Prox("Vence el ${TextosCobro.fechaCortita(p.fecha)} · ¿renueva?", "calendar.badge.exclamationmark", oro, "Preguntar")
        is Contratos.Proximo.aldia -> Contratos.Prox(p.ajuste?.let { "Al día · ajusta en ${TextosCobro.nombreMes(it)}" } ?: "Al día", "checkmark.circle.fill", ok, null)
        Contratos.Proximo.terminado -> Contratos.Prox(if (f.k.estado == "rescindido") "Rescindido" else "Terminó", "clock.arrow.circlepath", gris, null)
    }

    /** El aviso del ajuste salió: queda anotado («4 ya avisados»). */
    suspend fun anotarAviso(id: String) {
        @Serializable data class Cuerpo(val parte: String = "inquilino")
        try {
            estado.api.hacer("POST", "/v1/contratos/$id/ajuste/avisos", Cuerpo())
            estado.version += 1
        } catch (e: CancellationException) { throw e } catch (e: Exception) {
            estado.sugerir("El mensaje salió, pero no quedó anotado el aviso.", "Reintentar") { alcance.launch { anotarAviso(id) } }
        }
    }

    /** Cobrar (desde el mes más viejo), Avisar el ajuste o Preguntar si renueva. */
    fun hacer(f: Contratos.Fila) {
        val k = f.k
        val n = TextosCobro.primerNombre(k.contactoNombre)
        val moneda = k.moneda ?: "ARS"
        val propiedad = k.propiedadTitulo ?: "Sin propiedad"
        when (val p = f.proximo) {
            is Contratos.Proximo.debe -> cobrando = ACobrar(contratoId = k.id, contactoId = k.contactoId, nombre = k.contactoNombre, propiedad = propiedad,
                periodo = p.periodo, esperado = p.monto, moneda = moneda, vence = p.vence, punitorioDiario = k.punitorioDiario)
            is Contratos.Proximo.ajuste -> {
                val hasta = p.monto ?: f.actual
                mensaje = PedidoMensaje(
                    titulo = "Avisarle a ${if (n.isEmpty()) "el inquilino" else n}", descripcion = "$propiedad · ajusta el ${TextosCobro.fechaCortita(p.fecha)}",
                    contactoId = k.contactoId, nombre = k.contactoNombre,
                    texto = TextosCobro.ajuste(k.contactoNombre, k.ajusteIndice ?: "", p.fecha, f.actual, hasta, moneda),
                    para = "ajuste", parametros = listOf(n, TextosCobro.fechaCortita(p.fecha), Formato.plata(f.actual, moneda), Formato.plata(hasta, moneda)),
                    alEnviar = { alcance.launch { anotarAviso(k.id) } })
            }
            is Contratos.Proximo.vence -> {
                val f2 = TextosCobro.fechaCortita(p.fecha, anio = true)
                mensaje = PedidoMensaje(
                    titulo = "Preguntarle a ${if (n.isEmpty()) "el inquilino" else n}", descripcion = "$propiedad · vence el $f2",
                    contactoId = k.contactoId, nombre = k.contactoNombre,
                    texto = "${TextosCobro.hola(k.contactoNombre)} El contrato de $propiedad vence el $f2. ¿Te interesa renovarlo? Avisanos y lo vemos.",
                    para = "recordatorio", parametros = listOf(n, propiedad, f2))
            }
            else -> {}
        }
    }

    suspend fun cargar() {
        try {
            val l: List<ContratoAPI> = estado.api.todas("/v1/contratos")
            val d: List<DeudaContrato> = estado.api.opcional("/v1/contratos/deudas") ?: emptyList()
            deudas = d.filter { it.meses.isNotEmpty() }.distinctBy { it.contratoId }.associateBy { it.contratoId }
            estado.api.opcional<MesCobranzas>("/v1/cobranzas")?.let { m ->
                actual = (m.pendientes + m.cobradas).distinctBy { it.contratoId }.associate { it.contratoId to it.monto }
            }
            val evs: List<EventoContrato> = estado.api.opcional("/v1/contratos/vencimientos", listOf("dias" to "90")) ?: emptyList()
            val a = mutableMapOf<String, EventoContrato>()
            val v = mutableMapOf<String, EventoContrato>()
            for (e in evs) {
                if (e.tipo == "ajuste") { if (a[e.contratoId] == null) a[e.contratoId] = e } else if (v[e.contratoId] == null) v[e.contratoId] = e
            }
            ajustes = a
            vencimientos = v
            todos = l
            error = null
        } catch (e: CancellationException) {
            throw e
        } catch (e: ErrorApi) {
            if (todos == null) error = e
        } catch (e: Exception) {
        }
    }

    LaunchedEffect(Unit) {
        if (!listo) {
            listo = true
            if (indice != null) filtro = indice else if (vista != null) filtro = vista
        }
    }
    LaunchedEffect(estado.version) { cargar() }

    PantallaApilada("Contratos de alquiler", grande = true, acciones = {
        if (estado.escribe) {
            MenuBoton(listOf(
                OpcionDeMenu("Nuevo contrato", "square.and.pencil") { leerPDF = false; nuevo = true },
                OpcionDeMenu("Subir el PDF y que lo lea", "doc.viewfinder") { leerPDF = true; nuevo = true },
            ), "Nuevo contrato") {
                Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) { Icono("plus", 19f, C.accion, descripcion = "Nuevo contrato") }
            }
        }
    }) {
        Refrescable({ cargar() }) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val t = todos
                if (t != null) {
                    if (t.isEmpty()) {
                        Box(Modifier.padding(top = 30.dp)) {
                            EstadoVacio("doc.text", "Todavía no hay contratos",
                                texto = "Corredor calcula cada ajuste y arma los cobros.",
                                accion = if (estado.escribe) ("Cargar contrato" to { leerPDF = false; nuevo = true }) else null,
                                secundaria = if (estado.escribe) ("Subir el PDF y que lo lea" to { leerPDF = true; nuevo = true }) else null)
                        }
                    } else {
                        // Tarjetas de arriba
                        val okTinta = C.okTinta
                        val subAjustan: AnnotatedString = when {
                            ajustan30 == 0 -> AnnotatedString("Ninguno en el mes")
                            avisados30 >= ajustan30 -> AnnotatedString(if (ajustan30 == 1) "Ya avisado" else "Todos ya avisados")
                            avisados30 > 0 -> {
                                val resto = ajustan30 - avisados30
                                buildAnnotatedString {
                                    append("$avisados30 ya ${if (avisados30 == 1) "avisado" else "avisados"} · ")
                                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = okTinta)) { append("avisar a ${if (resto == 1) "el otro" else "los otros $resto"}") }
                                }
                            }
                            else -> buildAnnotatedString {
                                append("Avisales antes · ")
                                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = okTinta)) { append("avisar a ${if (ajustan30 == 1) "ese" else "los $ajustan30"}") }
                            }
                        }
                        Row(Modifier.carrusel(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            TarjetaContratos("chart.line.uptrend.xyaxis", "Ajustan en 30 días", Formato.cantidad(ajustan30, "contrato", "contratos"), subAjustan, oro = true) { filtro = "ajustan" }
                            TarjetaContratos("calendar.badge.exclamationmark", "Vencen en 90 días", Formato.cantidad(vencen90, "contrato", "contratos"),
                                AnnotatedString(if (vencen90 > 0) "Preguntale a cada parte si renueva" else "Ninguno por ahora"), oro = false) { filtro = "vencen" }
                            TarjetaContratos("doc.text", "Vigentes", Formato.numero(vigentes.size),
                                AnnotatedString(if (mensual.isEmpty()) "Sin alquileres" else "$mensual por mes"), oro = false, hacer = null)
                        }
                        // Chips
                        Row(Modifier.carrusel(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("vigentes" to "Vigentes", "ajustan" to "Ajustan en 30 días", "vencen" to "Vencen en 90 días", "icl" to "ICL", "ipc" to "IPC",
                                "uva" to "UVA", "fijo" to "Fijo", "todos" to "Todos").forEach { c ->
                                val activo = filtro == c.first
                                Box(Modifier.height(32.dp).clip(RoundedCornerShape(50)).background(if (activo) C.accion else C.hundida)
                                    .semantics { selected = activo }.clickable { filtro = c.first }.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                                    Texto(c.second, Estilo.footnoteFuerte, if (activo) C.sobreAccion else C.texto)
                                }
                            }
                        }
                        if (nombreFiltro != null) {
                            Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                TextoRico(buildAnnotatedString {
                                    append("Mostrando: ")
                                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(nombreFiltro) }
                                }, Estilo.footnote, C.texto2)
                                Relleno()
                                BotonLink("Ver todos", Estilo.subheadFuerte, C.accion) { filtro = "vigentes" }
                            }
                        }
                        val err = C.errorTinta; val oroT = C.oroTinta; val okT = C.okTinta; val gris = C.texto3
                        Column(Modifier.fillMaxWidth().tarjeta()) {
                            lista.forEachIndexed { i, f ->
                                FilaContrato(f, prox(f, err, oroT, okT, gris), primera = i == 0) { hacer(f) }
                            }
                            if (lista.isEmpty()) {
                                Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Texto("No hay contratos con ese filtro.", Estilo.texto14, C.texto3)
                                    BotonLink("Ver todos", Estilo.subheadFuerte, C.accion) { filtro = "vigentes" }
                                }
                            }
                        }
                        // Panel de la sección
                        fun cuenta(i: String) = vigentes.count { it.ajusteIndice == i }
                        fun cada(i: String): String {
                            val ms = vigentes.filter { it.ajusteIndice == i }.mapNotNull { it.ajusteMeses }.toSet().filter { it > 0 }.sorted()
                            return if (ms.isEmpty()) "" else "cada ${ms.joinToString(" o ")} meses"
                        }
                        val indices = listOf("icl", "ipc", "uva", "fijo").filter { i -> i != "fijo" || cuenta(i) > 0 }
                        Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            TituloBloque("Por índice")
                            Column(Modifier.fillMaxWidth().tarjeta()) {
                                indices.forEachIndexed { n, i ->
                                    FilaPanel(if (i == "fijo") "Aumento fijo" else i.uppercase(), cada(i), "percent", "${cuenta(i)}", primera = n == 0) { filtro = i }
                                }
                            }
                            TituloBloque("Renovaciones")
                            Box(Modifier.fillMaxWidth().tarjeta()) {
                                FilaPanel("Este trimestre", "preguntá a las partes", "arrow.triangle.2.circlepath", "$vencen90", primera = true) { filtro = "vencen" }
                            }
                            TituloBloque("Consultas")
                            Box(Modifier.fillMaxWidth().tarjeta()) {
                                FilaPanel("Índices", "ICL, IPC y UVA", "chart.xyaxis.line", null, primera = true) { estado.abrir(ASeccion(clave = "indices")) }
                            }
                        }
                    }
                } else if (error != null) {
                    EstadoError("los contratos", error!!) { alcance.launch { cargar() } }
                } else {
                    EsqueletoFilas(filas = 5)
                }
            }
        }
    }

    Hoja(nuevo, { nuevo = false }) { AltaContrato(leer = leerPDF) }
    HojaDe(cobrando, { cobrando = null }) { c -> HojaCobro(c) }
    hojaMensaje(mensaje) { mensaje = null }
}

@Composable
private fun TarjetaContratos(icono: String, etiqueta: String, valor: String, sub: AnnotatedString, oro: Boolean, hacer: (() -> Unit)?) {
    Column(
        Modifier.width(220.dp).heightIn(min = 110.dp).clip(RoundedCornerShape(18.dp)).background(if (oro) C.avisoFondo else C.superficie)
            .clickable(enabled = hacer != null) { hacer?.invoke() }.padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Etiqueta(etiqueta, icono, Estilo.footnoteFuerte, if (oro) C.oroTinta else C.texto2)
        Texto(valor, Estilo.titulo3, C.texto)
        TextoRico(sub, Estilo.footnote, C.texto2, lineas = 2)
    }
}

@Composable
private fun FilaContrato(f: Contratos.Fila, p: Contratos.Prox, primera: Boolean, hacer: () -> Unit) {
    val estado = LocalEstado.current
    val k = f.k
    Column {
        if (!primera) LineaFila()
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).clickable { estado.abrir(AFichaContrato(id = k.id)) }, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Texto(k.propiedadTitulo ?: "Sin propiedad", Estilo.filaLista, C.texto, lineas = 1)
                Texto(listOfNotNull(k.contactoNombre ?: "Sin inquilino", k.propietarioNombre?.let { "prop. $it" }).joinToString(" · "),
                    Estilo.footnote, C.texto2, lineas = 1)
                Texto("${Formato.plata(f.actual, k.moneda ?: "ARS")} · ${if (k.tipo == "venta") "Venta" else k.etiquetaIndice}", Estilo.footnote, C.texto2, lineas = 1)
                Etiqueta(p.texto, p.icono, Estilo(12f, Peso.bold), p.color)
            }
            val cta = p.cta
            if (cta != null && estado.escribe) {
                Box(Modifier.height(34.dp).clip(RoundedCornerShape(50)).background(C.hundida).clickable(onClick = hacer).padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center) {
                    Texto(cta, Estilo(14f, Peso.semibold), C.texto)
                }
            } else {
                Chevron()
            }
        }
    }
}

@Composable
private fun FilaPanel(t: String, d: String, icono: String, n: String?, primera: Boolean, hacer: () -> Unit) {
    Column {
        if (!primera) LineaFila()
        Row(Modifier.fillMaxWidth().clickable(onClick = hacer).padding(horizontal = 14.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(24.dp), contentAlignment = Alignment.Center) { Icono(icono, 17f, C.texto2) }
            Column(Modifier.weight(1f)) {
                Texto(t, Estilo.filaLista, C.texto)
                if (d.isNotEmpty()) Texto(d, Estilo.footnote, C.texto2)
            }
            if (n != null) Texto(n, Estilo.subheadFuerte, C.texto)
            Chevron()
        }
    }
}
