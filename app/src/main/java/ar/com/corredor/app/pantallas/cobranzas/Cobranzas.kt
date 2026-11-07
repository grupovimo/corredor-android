package ar.com.corredor.app.pantallas.cobranzas

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import ar.com.corredor.app.app.*
import ar.com.corredor.app.nucleo.*
import ar.com.corredor.app.pantallas.informes.GraficoCobranzaMeses
import ar.com.corredor.app.tema.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

// Los destinos de Cobranzas, contratos, rendiciones y actas (`destinosCobranzas()` en iOS). Los registra
// app/Registro.kt:
//   ACobranzas → Cobranzas()
//   ARegistrarCobro → Cobranzas(registrar = true, contrato = r.contrato)
//   AContratos → Contratos()
//   AContratosFiltro → Contratos(vista = f.vista, indice = f.indice)
//   AFichaContrato → FichaContrato(id = c.id)
//   ARecordarMes → FichaContrato(id = c.contrato, recordar = c.periodo)
//   AAltaContrato → AltaContratoPantalla(destino = a)
//   ARecibo → ReciboVista(contrato = r.contrato, cobro = r.cobro)
//   ARendicion → RendicionVista(contrato = r.contrato, id = r.id)
//   ARendicionVista → RendicionVista(contrato = r.contrato, id = r.id, verComoPropietario = true)
//   AInventario → Inventario(propiedad = i.id)
//   AFirmas → Firmas(clave = f.id)
//   y los de inventarios (InventarioComun.kt).

/** «Registrar cobro» con la hoja abierta (`/cobranzas?registrar=1&contrato=<id>` de los avisos y la
 *  búsqueda): el cobro de ese contrato, o «¿De quién es el cobro?». */
@Serializable data class ARegistrarCobro(val contrato: String? = null)
/** La ficha del contrato con el recordatorio de un mes abierto (`/contratos/<id>?recordar=AAAA-MM`). */
@Serializable data class ARecordarMes(val contrato: String, val periodo: String)
/** Los contratos con un filtro (`/contratos?vista=ajustan|vencen|todos`, `?indice=icl`). */
@Serializable data class AContratosFiltro(val vista: String? = null, val indice: String? = null)
/** El alta de un contrato a pantalla completa: seguir un borrador (`/contratos/nuevo?borrador=`) o
 *  renovar (`?renovar=`). */
@Serializable data class AAltaContrato(val borrador: String? = null, val renovar: String? = null, val leer: Boolean = false)
/** La rendición «como la ve el propietario» (`/rendiciones/<id>?contrato=<c>&vista=1`). */
@Serializable data class ARendicionVista(val contrato: String, val id: String)

/** Una fila de cobranzas del mes (GET /v1/cobranzas). */
@Serializable
data class FilaCobranza(
    val contratoId: String,
    val propiedadTitulo: String? = null,
    val contactoNombre: String? = null,
    val propietarioNombre: String? = null,
    val agenteId: String? = null,
    val monto: Double,
    val moneda: String,
    val estimado: Boolean? = null,
    val vence: String? = null,
    val estado: String? = null,
    val cobro: Cobro? = null,
    val comision: Double? = null,
    val punitorioDiario: Double? = null,
) {
    val id: String get() = contratoId
}

@Serializable
data class Cobro(
    val id: String,
    val periodo: String,
    val monto: Double,
    val moneda: String,
    val pagadoEl: String? = null,
    val notas: String? = null,
    val medio: String? = null,
    val compartido: Boolean? = null,
    val numero: Int? = null,
    val conceptos: List<Concepto>? = null,
) {
    @Serializable data class Concepto(val concepto: String, val monto: Double)
}

/** Un mes del contrato (GET /v1/contratos/{id}/cobros): lo esperado y, si se cobró, el cobro. Estado:
 *  cobrado, pendiente, atrasado (se debe) o sin_registro (vencido sin cobro, de antes de Corredor). */
@Serializable
data class MesContrato(
    val periodo: String,
    val vence: String? = null,
    val esperado: Double? = null,
    val estimado: Boolean? = null,
    val estado: String? = null,
    val cobro: Cobro? = null,
)

@Serializable
data class MesCobranzas(
    val periodo: String,
    val cobradas: List<FilaCobranza> = emptyList(),
    val pendientes: List<FilaCobranza> = emptyList(),
    val totalCobrado: Map<String, Double>? = null,
    val totalPendiente: Map<String, Double>? = null,
    val totalComision: Map<String, Double>? = null,
)

/** Un mes anterior sin cobrar (GET /v1/cobranzas/atrasadas?mes=). */
@Serializable
data class Atraso(
    val contratoId: String,
    val periodo: String,
    val vence: String,
    val monto: Double,
    val moneda: String,
    val estimado: Boolean? = null,
    val propiedadTitulo: String? = null,
    val contactoId: String? = null,
    val contactoNombre: String? = null,
    val agenteId: String? = null,
    val punitorioDiario: Double? = null,
)

/** Lo que debe un alquiler (GET /v1/contratos/deudas): los meses, del más viejo al más nuevo, con la
 *  forma de la API (`Deuda` del núcleo tiene otra forma para los meses). */
@Serializable
data class DeudaContrato(
    val contratoId: String,
    val propiedadTitulo: String? = null,
    val contactoId: String? = null,
    val contactoNombre: String? = null,
    val moneda: String? = null,
    val punitorioDiario: Double? = null,
    val meses: List<MesContrato> = emptyList(),
    val total: Double? = null,
    val estimado: Boolean? = null,
)

/** Lo que hace falta para cobrar un mes de un contrato. */
data class ACobrar(
    val contratoId: String,
    val contactoId: String?,
    val nombre: String?,
    val telefono: String? = null,
    val propiedad: String?,
    /** «AAAA-MM» o «AAAA-MM-01». */
    val periodo: String,
    val esperado: Double,
    val moneda: String,
    val vence: String,
    /** La cláusula de punitorios del contrato (% por día de atraso). */
    val punitorioDiario: Double?,
    /** Con un cobro ya registrado, la hoja abre directo en el recibo («Mandar recibo»). */
    val cobro: Cobro? = null,
) {
    val id: String get() = "$contratoId|${periodo.take(7)}|${cobro?.id ?: ""}"
}

// MARK: - 10A–10B Por cobrar

/** Una fila de «Deben» o de «Vencen el…»: un contrato y un mes (los atrasados de antes también deben). */
data class FilaDeuda(
    val contratoId: String,
    val periodo: String,
    val propiedad: String?,
    val nombre: String?,
    val contactoId: String?,
    val monto: Double,
    val moneda: String,
    val vence: String,
    val estado: String,
    val cobro: Cobro?,
    val punitorioDiario: Double?,
) {
    val pagadoEl: String? get() = cobro?.pagadoEl
    val id: String get() = "$contratoId|${periodo.take(7)}"

    val aCobrar: ACobrar
        get() = ACobrar(contratoId = contratoId, contactoId = contactoId, nombre = nombre, propiedad = propiedad, periodo = periodo,
            esperado = monto, moneda = moneda, vence = vence, punitorioDiario = punitorioDiario)
}

/** Lo que Cobranzas tiene de `static` en iOS. */
object Cobranzas {
    enum class TonoFila { rojo, neutro }

    fun pct(c: MesCobranzas?, moneda: String): Int {
        val tc = c?.totalCobrado?.get(moneda) ?: 0.0
        val tp = c?.totalPendiente?.get(moneda) ?: 0.0
        return if (tc + tp > 0) (tc / (tc + tp) * 100).roundToInt() else 0
    }

    fun diasEntre(desde: String, hasta: String): Int {
        val a = Fecha.fecha(desde) ?: return 0
        val b = Fecha.fecha(hasta) ?: return 0
        return ChronoUnit.DAYS.between(a, b).toInt()
    }

    /** «lunes 16». */
    fun diaCorto(f: LocalDate): String = DateTimeFormatter.ofPattern("EEEE d", Formato.argentina).format(f)

    fun vence(s: String?): Pair<String, Boolean> {
        val d = Fecha.fecha(s) ?: return "" to false
        val dias = ChronoUnit.DAYS.between(Formato.hoy(), d).toInt()
        if (dias < 0) return "vencido hace ${Formato.cantidad(-dias, "día", "días")}" to true
        if (dias == 0) return "vence hoy" to true
        return "vence el ${d.dayOfMonth}" to false
    }
}

/** 10A–10B Cobranzas del mes (23E, y P en el celular): quién debe (también de meses anteriores), qué
 *  vence pronto, agrupado por día, y qué se cobró. Deslizar a la derecha cobra, a la izquierda recuerda. */
@Composable
fun Cobranzas(
    /** «Registrar cobro» abierto al entrar (de los avisos o la búsqueda) y el contrato, si viene. */
    registrar: Boolean = false,
    contrato: String? = null,
) {
    val estado = LocalEstado.current
    val ctx = LocalContext.current
    val alcance = rememberCoroutineScope()
    var mes by remember { mutableStateOf<MesCobranzas?>(null) }
    var previo by remember { mutableStateOf<MesCobranzas?>(null) }
    var atrasadas by remember { mutableStateOf<List<Atraso>>(emptyList()) }
    var contactos by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var periodo by remember { mutableStateOf(Formato.hoy()) }
    var cobrando by remember { mutableStateOf<ACobrar?>(null) }
    var mensaje by remember { mutableStateOf<PedidoMensaje?>(null) }
    var todos by remember { mutableStateOf(false) }
    var elegir by remember { mutableStateOf(false) }
    var cargarContrato by remember { mutableStateOf(false) }
    var ocultos by remember { mutableStateOf<Set<String>>(emptySet()) }
    var error by remember { mutableStateOf<ErrorApi?>(null) }
    var bajandoExcel by remember { mutableStateOf(false) }
    var pedidoHecho by remember { mutableStateOf(false) }

    val hoy = Formato.hoy().toString()
    val nombreMes = TextosCobro.nombreMes(mes?.periodo ?: Formato.diaISO(periodo))
    val Mes = Formato.mayuscula(nombreMes)
    val moneda = run {
        val ms = (mes?.totalCobrado ?: emptyMap()).keys.toList() + (mes?.totalPendiente ?: emptyMap()).keys.toList()
        if (ms.contains("ARS") || ms.isEmpty()) "ARS" else ms[0]
    }
    val enCurso = (mes?.periodo?.take(7) ?: "") == hoy.take(7)

    fun fila(f: FilaCobranza, p: String): FilaDeuda =
        FilaDeuda(contratoId = f.contratoId, periodo = p, propiedad = f.propiedadTitulo, nombre = f.contactoNombre, contactoId = contactos[f.contratoId],
            monto = f.monto, moneda = f.moneda, vence = f.vence ?: p, estado = f.estado ?: "pendiente", cobro = f.cobro, punitorioDiario = f.punitorioDiario)

    val visibles: List<FilaDeuda> = mes?.let { m -> m.pendientes.map { fila(it, m.periodo) }.filter { it.id !in ocultos } } ?: emptyList()
    val deben: List<FilaDeuda> = run {
        val viejas = atrasadas.map { a ->
            FilaDeuda(contratoId = a.contratoId, periodo = a.periodo, propiedad = a.propiedadTitulo, nombre = a.contactoNombre, contactoId = a.contactoId,
                monto = a.monto, moneda = a.moneda, vence = a.vence, estado = "atrasado", cobro = null, punitorioDiario = a.punitorioDiario)
        }.filter { it.id !in ocultos }
        viejas + visibles.filter { it.estado == "atrasado" }
    }
    val porVencer = visibles.filter { it.estado != "atrasado" }
    /** Lo que vence pronto, agrupado por día («Vencen el lunes 16»). */
    val grupos: List<Pair<String, List<FilaDeuda>>> = porVencer.groupBy { it.vence }.toSortedMap().map { it.key to it.value }
    val cobradas = (mes?.cobradas ?: emptyList()).map { fila(it, mes?.periodo ?: "") }
    val cobradasHoy = cobradas.filter { it.pagadoEl == hoy }
    val listaCobradas = if (cobradasHoy.isEmpty()) cobradas.takeLast(6).reversed() else cobradasHoy

    fun sumar(fs: List<FilaDeuda>): Double = fs.filter { it.moneda == moneda }.sumOf { it.monto }
    val totalCobrado = (mes?.totalCobrado?.get(moneda) ?: 0.0) +
        sumar((mes?.pendientes ?: emptyList()).map { fila(it, mes?.periodo ?: "") }.filter { it.id in ocultos })
    val totalMes = (mes?.totalCobrado?.get(moneda) ?: 0.0) + (mes?.totalPendiente?.get(moneda) ?: 0.0)
    val deuda = sumar(deben)
    val deudaDelMes = sumar(deben.filter { it.periodo == mes?.periodo })
    val pct = if (totalMes > 0) totalCobrado / totalMes else 0.0
    val pctDeuda = if (totalMes > 0) deudaDelMes / totalMes else 0.0

    fun atraso(f: FilaDeuda): String {
        if (f.periodo != mes?.periodo) return "debe ${TextosCobro.mesConAnio(f.periodo, hoy)}"
        val d = Cobranzas.diasEntre(f.vence, hoy)
        return if (d <= 0) "vence hoy" else if (d == 1) "venció hace 1 día" else "venció hace $d días"
    }

    fun recordar(f: FilaDeuda) {
        val n = TextosCobro.primerNombre(f.nombre)
        mensaje = PedidoMensaje(
            titulo = "Recordarle a ${if (n.isEmpty()) "el inquilino" else n}",
            descripcion = "${Formato.mayuscula(TextosCobro.mesConAnio(f.periodo, hoy))} · ${f.propiedad ?: ""} · ${Formato.plata(f.monto, f.moneda)}",
            contactoId = f.contactoId, nombre = f.nombre,
            texto = TextosCobro.recordatorio(f.nombre, f.periodo, f.monto, f.moneda, f.vence, hoy),
            para = "recordatorio",
            parametros = listOf(n, TextosCobro.nombreMes(f.periodo), Formato.plata(f.monto, f.moneda), TextosCobro.fechaCortita(f.vence)),
            listo = "Quedó en la ficha de $n y en la bandeja.",
        )
    }

    /** Deslizar «Cobrar»: el cobro del monto esperado sin preguntar (la fila se va, queda «Deshacer» y
     *  «Mandar recibo» en el aviso). */
    fun cobrarYa(f: FilaDeuda) {
        val n = TextosCobro.primerNombre(f.nombre)
        Cobrar.registrar(estado, f.aCobrar, texto = "Cobrado · ${if (n.isEmpty()) (f.propiedad ?: "") else n}",
            ocultar = { ocultos = ocultos + f.id }, mostrar = { ocultos = ocultos - f.id },
            recibo = { c -> cobrando = f.aCobrar.copy(cobro = c, esperado = c.monto) })
    }

    suspend fun bajarExcel() {
        bajandoExcel = true
        val p = (mes?.periodo ?: Formato.diaISO(periodo)).take(7)
        try {
            val u = Documentos.archivo("/v1/cobranzas/excel?mes=$p", nombre = "cobranzas-$p.xlsx", api = estado.api)
            verArchivo(ctx, u, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
        } catch (e: CancellationException) { throw e } catch (e: Exception) { estado.avisar(e) } finally { bajandoExcel = false }
    }

    /** `?registrar=1&contrato=<id>`: abre el cobro de ese contrato, o «¿De quién es el cobro?». */
    fun abrirPedido(deben: List<FilaDeuda>, visibles: List<FilaDeuda>) {
        if (!registrar || pedidoHecho || !estado.escribe) return
        pedidoHecho = true
        val f = contrato?.let { c -> (deben + visibles).firstOrNull { it.contratoId == c } }
        if (f != null) cobrando = f.aCobrar else elegir = true
    }

    suspend fun cargar() {
        val p = Formato.diaISO(periodo).take(7)
        try {
            val m: MesCobranzas = estado.api.get("/v1/cobranzas", listOf("mes" to p))
            val deMes = m.periodo.take(7)
            atrasadas = estado.api.opcional<List<Atraso>>("/v1/cobranzas/atrasadas", listOf("mes" to deMes)) ?: emptyList()
            val cs: List<ContratoAPI> = try { estado.api.todas("/v1/contratos", listOf("tipo" to "alquiler")) } catch (e: CancellationException) { throw e } catch (e: Exception) { emptyList() }
            contactos = cs.mapNotNull { k -> k.contactoId?.let { k.id to it } }.distinctBy { it.first }.toMap()
            Fecha.fecha("$deMes-01")?.minusMonths(1)?.let { ant ->
                previo = estado.api.opcional("/v1/cobranzas", listOf("mes" to Formato.diaISO(ant).take(7)))
            }
            // Lo que vuelve de la API ya está al día: se olvida lo que se ocultó a mano.
            val pend = (m.pendientes.map { "${it.contratoId}|${m.periodo.take(7)}" } + atrasadas.map { "${it.contratoId}|${it.periodo.take(7)}" }).toSet()
            ocultos = ocultos.filter { it in pend }.toSet()
            mes = m
            error = null
            // Las filas con lo recién traído (las de arriba se calcularon con el estado anterior).
            val vs = m.pendientes.map { fila(it, m.periodo) }.filter { it.id !in ocultos }
            val viejas = atrasadas.map { a ->
                FilaDeuda(a.contratoId, a.periodo, a.propiedadTitulo, a.contactoNombre, a.contactoId, a.monto, a.moneda, a.vence, "atrasado", null, a.punitorioDiario)
            }.filter { it.id !in ocultos }
            abrirPedido(viejas + vs.filter { it.estado == "atrasado" }, vs)
        } catch (e: CancellationException) {
            throw e
        } catch (e: ErrorApi) {
            if (mes == null) error = e
        } catch (e: Exception) {
        }
    }

    LaunchedEffect(estado.version, Formato.diaISO(periodo).take(7)) { cargar() }

    PantallaApilada("Cobranzas · $nombreMes", grande = true, acciones = {
        MenuBoton((-6..0).map { d ->
            val f = Formato.hoy().plusMonths(d.toLong())
            OpcionDeMenu(Formato.mayuscula("${Formato.mesDe(f)} ${f.year}"), "") { periodo = f }
        }, Mes) {
            Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) { Icono("calendar", 19f, C.accion, descripcion = Mes) }
        }
        MenuBoton(buildList {
            if (estado.escribe) add(OpcionDeMenu("Registrar cobro", "dollarsign.circle") { elegir = true })
            add(OpcionDeMenu("Excel", "square.and.arrow.down", habilitado = !bajandoExcel) { alcance.launch { bajarExcel() } })
        }, "Más") {
            Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) { Icono("ellipsis.circle", 19f, C.accion, descripcion = "Más") }
        }
    }) {
        Refrescable({ cargar() }) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                val m = mes
                if (m != null) {
                    ResumenCobranzas(totalCobrado, totalMes, moneda, pct, pctDeuda, deben.size, deuda, nombreMes)
                    if (m.pendientes.isEmpty() && m.cobradas.isEmpty() && atrasadas.isEmpty()) {
                        Box(Modifier.padding(top = 20.dp)) {
                            EstadoVacio("dollarsign.circle", "Todavía no hay alquileres para cobrar",
                                texto = "Cargá un contrato y los cobros se arman solos.",
                                accion = if (estado.escribe) ("Cargar contrato" to { cargarContrato = true }) else null)
                        }
                    } else {
                        // Deben
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Texto("DEBEN · ${deben.size}", Estilo(13f, Peso.bold, tracking = 0.5f / 13f), C.errorTinta)
                                Texto(Formato.plata(deuda, moneda), Estilo.footnoteFuerte, C.texto2)
                                Relleno()
                                if (deben.size > 1) BotonLink("Recordar a todos", Estilo.subheadFuerte, C.accion) { todos = true }
                            }
                            Column(Modifier.fillMaxWidth().tarjeta()) {
                                deben.forEachIndexed { i, f ->
                                    FilaDeudor(f, primera = i == 0, tono = Cobranzas.TonoFila.rojo, atraso = atraso(f),
                                        cobrar = { cobrando = f.aCobrar }, cobrarYa = { cobrarYa(f) }, recordar = { recordar(f) })
                                }
                                if (deben.isEmpty()) {
                                    Etiqueta("Nadie debe $nombreMes. Todo al día.", "checkmark.circle.fill", Estilo.subheadFuerte, C.okTinta,
                                        Modifier.fillMaxWidth().padding(14.dp))
                                }
                            }
                            if (deben.isNotEmpty()) {
                                Texto("Deslizá a la derecha para cobrar, a la izquierda para recordar.", Estilo.footnote, C.texto3,
                                    Modifier.fillMaxWidth(), alinear = TextAlign.Center)
                            }
                        }
                        // Vencen el…
                        grupos.forEach { (vence, fs) ->
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Texto("Vencen el ${Fecha.fecha(vence)?.let { Formato.diaCompleto(it) } ?: vence}", Estilo.subheadFuerte, C.texto)
                                    Relleno()
                                    Texto("${fs.size} · ${Formato.plata(sumar(fs), moneda)}", Estilo.footnote, C.texto2)
                                }
                                Column(Modifier.fillMaxWidth().tarjeta()) {
                                    fs.forEachIndexed { i, f ->
                                        FilaDeudor(f, primera = i == 0, tono = Cobranzas.TonoFila.neutro,
                                            atraso = "vence ${Fecha.fecha(f.vence)?.let { Cobranzas.diaCorto(it) } ?: f.vence}",
                                            cobrar = { cobrando = f.aCobrar }, cobrarYa = { cobrarYa(f) }, recordar = { recordar(f) })
                                    }
                                }
                            }
                        }
                        // Cobrado
                        if (listaCobradas.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Texto(if (cobradasHoy.isEmpty()) "Cobrado en $nombreMes" else "Cobrado hoy", Estilo.subheadFuerte, C.texto)
                                    Relleno()
                                    Texto("${listaCobradas.size} · ${Formato.plata(sumar(listaCobradas), moneda)}", Estilo.footnote, C.texto2)
                                }
                                Column(Modifier.fillMaxWidth().tarjeta()) {
                                    listaCobradas.forEachIndexed { i, f ->
                                        FilaCobrada(f, primera = i == 0) { k -> cobrando = f.aCobrar.copy(cobro = k, esperado = k.monto) }
                                    }
                                }
                            }
                        }
                    }
                    PanelSeccion(m, previo, moneda, Mes, enCurso) { d -> periodo = d }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BotonHundido("Contratos", { estado.abrir(AContratos) }, Modifier.weight(1f))
                        BotonHundido("Rendiciones", { estado.abrir(ASeccion(clave = "rendiciones")) }, Modifier.weight(1f))
                    }
                    GraficoCobranzaMeses()
                } else if (error != null) {
                    EstadoError("las cobranzas", error!!) { alcance.launch { cargar() } }
                } else {
                    EsqueletoFilas(filas = 5)
                }
            }
        }
    }

    HojaDe(cobrando, { cobrando = null }) { c -> HojaCobro(c, ocultar = { ocultos = ocultos + it }, mostrar = { ocultos = ocultos - it }) }
    hojaMensaje(mensaje) { mensaje = null }
    Hoja(todos, { todos = false }) { RecordarATodos(deben) }
    Hoja(elegir, { elegir = false }, alturas = listOf(Altura.media, Altura.grande)) {
        // «¿De quién es el cobro?»
        BarraHoja("¿De quién es el cobro?", "Cerrar", { elegir = false })
        Texto("$Mes · lo que falta cobrar", Estilo.footnote, C.texto2, Modifier.fillMaxWidth().padding(bottom = 4.dp), alinear = TextAlign.Center)
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp)) {
            SeccionCobro {
                if (visibles.isEmpty()) {
                    Texto("No queda nada por cobrar en $nombreMes.", Estilo.body, C.texto2, Modifier.padding(16.dp))
                }
                visibles.forEachIndexed { i, f ->
                    if (i > 0) LineaFila(16.dp)
                    FilaToque(alTocar = {
                        elegir = false
                        alcance.launch { delay(350); cobrando = f.aCobrar }
                    }) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Texto(f.nombre ?: "Sin inquilino", Estilo.filaLista, C.texto)
                            Texto(f.propiedad ?: "", Estilo.footnote, C.texto2)
                        }
                        Texto(Formato.numero(f.monto.roundToInt()), Estilo(17f, Peso.semibold), C.texto)
                    }
                }
            }
        }
    }
    Hoja(cargarContrato, { cargarContrato = false }) { AltaContrato() }
}

// MARK: Resumen

@Composable
private fun ResumenCobranzas(totalCobrado: Double, totalMes: Double, moneda: String, pct: Double, pctDeuda: Double, cuantosDeben: Int, deuda: Double, nombreMes: String) {
    val claro = hex(0xCFE0D8)
    Column(
        // C.verde800 con el Patron al 6 % y el GranoClaro en mosaico.
        Modifier.fillMaxWidth().sombraE3(RoundedCornerShape(M.rDestacada)).clip(RoundedCornerShape(M.rDestacada)).texturaVerde(C.verde800, patron = 0.06).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Texto(Formato.plata(totalCobrado, moneda), Estilo(32f, Peso.bold, tracking = -0.03f), Color.White)
        Texto("Cobrado ${Formato.plata(totalCobrado, moneda)} de ${Formato.numero(totalMes.roundToInt())} · ${(pct * 100).roundToInt()} %",
            Estilo.texto14, claro)
        val a by animateFloatAsState(pct.toFloat(), spring(), label = "cobrado")
        val b by animateFloatAsState(pctDeuda.toFloat(), spring(), label = "deuda")
        BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 6.dp).height(8.dp)
            .semantics { contentDescription = "Cobrado ${(pct * 100).roundToInt()} %, vencido ${(pctDeuda * 100).roundToInt()} %" }) {
            val ancho = this@BoxWithConstraints.maxWidth
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                Box(Modifier.width(ancho * a.coerceAtLeast(0f)).fillMaxHeight8().clip(RoundedCornerShape(50)).background(C.oro))
                Box(Modifier.width(ancho * b.coerceAtLeast(0f)).fillMaxHeight8().clip(RoundedCornerShape(50)).background(hex(0xF29B92)))
                Box(Modifier.weight(1f).fillMaxHeight8().clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.14f)))
            }
        }
        Texto(if (cuantosDeben == 0) "Nadie debe $nombreMes" else "${Formato.cantidad(cuantosDeben, "inquilino debe", "inquilinos deben")} ${Formato.plata(deuda, moneda)}",
            Estilo.subheadFuerte, if (cuantosDeben == 0) claro else hex(0xF7C3BC), Modifier.padding(top = 4.dp))
    }
}

private fun Modifier.fillMaxHeight8(): Modifier = this.height(8.dp)

// MARK: Filas

@Composable
private fun FilaDeudor(f: FilaDeuda, primera: Boolean, tono: Cobranzas.TonoFila, atraso: String, cobrar: () -> Unit, cobrarYa: () -> Unit, recordar: () -> Unit) {
    val estado = LocalEstado.current
    Column {
        if (!primera) LineaFila()
        FilaDeslizable(
            izquierda = if (estado.escribe) listOf(AccionFila("Cobrar", "dollarsign.circle", hex(0x2F9E6F)) { cobrarYa() }) else emptyList(),
            derecha = listOf(AccionFila("Recordar", "bell.badge", C.oro) { recordar() }), anchoAccion = 112.dp,
        ) {
            MenuContextual(buildList {
                if (estado.escribe) add(OpcionDeMenu("Cobrar", "dollarsign.circle") { cobrar() })
                add(OpcionDeMenu("Recordar", "bell.badge") { recordar() })
                add(OpcionDeMenu("Ver el contrato", "doc.text") { estado.abrir(AFichaContrato(id = f.contratoId)) })
            }, alTocar = { if (estado.escribe) cobrar() else estado.abrir(AFichaContrato(id = f.contratoId)) }) {
                Row(
                    Modifier.fillMaxWidth().background(C.superficie).padding(start = 14.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically,
                ) {
                    // El monto va abajo del nombre: con el monto, la campana y «Cobrar» en la misma fila, al nombre le
                    // quedaban 100 de ancho («Lucía / Méndez») y el atraso se perdía en «Gorriti 4820 · …».
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Texto(f.nombre ?: "Sin inquilino", Estilo(16f, Peso.semibold), C.texto, lineas = 1)
                        Texto(Formato.plata(f.monto, f.moneda), Estilo(15f, Peso.bold), C.texto, lineas = 1, numeros = true)
                        val rojo = C.error; val gris = C.texto2
                        TextoRico(
                            buildAnnotatedString {
                                append("${f.propiedad ?: ""} · ")
                                withStyle(SpanStyle(color = if (tono == Cobranzas.TonoFila.rojo) rojo else gris, fontWeight = FontWeight.SemiBold)) { append(atraso) }
                            },
                            Estilo.footnote, gris, lineas = 2,
                        )
                    }
                    Box(Modifier.size(44.dp).clickable(onClick = recordar), contentAlignment = Alignment.Center) {
                        Icono("bell.badge", 20f, C.texto2, descripcion = "Mandar recordatorio por WhatsApp")
                    }
                    if (estado.escribe) {
                        Box(Modifier.height(34.dp).clip(RoundedCornerShape(50)).background(C.accion).clickable(onClick = cobrar).padding(horizontal = 12.dp),
                            contentAlignment = Alignment.Center) {
                            Texto("Cobrar", Estilo(14f, Peso.semibold), C.sobreAccion)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilaCobrada(f: FilaDeuda, primera: Boolean, mandarRecibo: (Cobro) -> Unit) {
    val estado = LocalEstado.current
    Column {
        if (!primera) LineaFila()
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icono("checkmark.circle.fill", 22f, hex(0x2F9E6F))
            Column(Modifier.weight(1f).clickable { f.cobro?.let { k -> estado.abrir(ARecibo(contrato = f.contratoId, cobro = k.id)) } }) {
                Texto(f.nombre ?: "Sin inquilino", Estilo.filaLista, C.texto)
                Texto(f.propiedad ?: "", Estilo.footnote, C.texto2, lineas = 1)
            }
            Texto(Formato.numero((f.cobro?.monto ?: f.monto).roundToInt()), Estilo(15f, Peso.bold), C.texto)
            val k = f.cobro
            if (k?.compartido == true) {
                Icono("checkmark.circle", 18f, C.azulTinta, descripcion = "Recibo enviado")
            } else if (k != null) {
                BotonLink("Mandar recibo", Estilo(13f, Peso.semibold), C.accion) { mandarRecibo(k) }
            }
        }
    }
}

// MARK: Panel de la sección (medios de cobro y el mes)

@Composable
private fun PanelSeccion(mes: MesCobranzas?, previo: MesCobranzas?, moneda: String, Mes: String, enCurso: Boolean, irA: (LocalDate) -> Unit) {
    val medios: List<Pair<String, Int>> = run {
        val m = mutableMapOf<String, Int>()
        for (f in mes?.cobradas ?: emptyList()) f.cobro?.medio?.let { x -> m[x] = (m[x] ?: 0) + 1 }
        listOf("transferencia", "efectivo", "mercadopago").mapNotNull { k -> m[k]?.let { k to it } }
    }
    val conMedio = medios.sumOf { it.second }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (conMedio > 0) {
            TituloBloque("Medios de cobro")
            Column(Modifier.fillMaxWidth().tarjeta()) {
                medios.forEachIndexed { i, m ->
                    if (i > 0) LineaFila()
                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.width(24.dp), contentAlignment = Alignment.Center) {
                            Icono(if (m.first == "transferencia") "building.columns" else if (m.first == "efectivo") "banknote" else "qrcode", 17f,
                                if (m.first == "mercadopago") C.azulTinta else C.texto2)
                        }
                        Column(Modifier.weight(1f)) {
                            Texto(ReciboVista.medioNombre(m.first), Estilo.filaLista, C.texto)
                            Texto("${m.second} este mes", Estilo.footnote, C.texto2)
                        }
                        Texto("${(m.second.toDouble() / conMedio * 100).roundToInt()} %", Estilo.subheadFuerte, C.texto)
                    }
                }
            }
        }
        TituloBloque("Mes")
        Column(Modifier.fillMaxWidth().tarjeta()) {
            FilaMes(Mes, if (enCurso) "en curso" else "${Cobranzas.pct(mes, moneda)} % cobrado", activo = true) {}
            if (previo != null) {
                LineaFila()
                FilaMes(Formato.mayuscula(TextosCobro.nombreMes(previo.periodo)), "${Cobranzas.pct(previo, moneda)} % cobrado", activo = false) {
                    Fecha.fecha(previo.periodo.take(7) + "-15")?.let(irA)
                }
            }
        }
    }
}

@Composable
private fun FilaMes(t: String, d: String, activo: Boolean, hacer: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(enabled = !activo, onClick = hacer).padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(24.dp), contentAlignment = Alignment.Center) { Icono("calendar", 17f, if (activo) C.accion else C.texto2) }
        Column(Modifier.weight(1f)) {
            Texto(t, Estilo.filaLista, C.texto)
            Texto(d, Estilo.footnote, C.texto2)
        }
        if (!activo) Chevron()
    }
}

/** Registrar un cobro con el patrón del prototipo P: la fila se va al toque, queda el aviso con
 *  «Deshacer» y el pedido va por detrás. Deshacer borra el cobro (vuelve la deuda); si el pedido
 *  falla, la fila vuelve y el aviso ofrece «Reintentar». Sin señal, queda en la cola. */
object Cobrar {
    @Serializable
    data class Cuerpo(
        val periodo: String,
        val monto: Double? = null,
        val medio: String? = null,
        val conceptos: List<Cobro.Concepto>? = null,
        val pagadoEl: String? = null,
    )

    fun registrar(
        estado: Estado,
        a: ACobrar,
        monto: Double? = null,
        medio: String? = null,
        conceptos: List<Cobro.Concepto> = emptyList(),
        pagadoEl: String? = null,
        texto: String = "Cobro registrado",
        ocultar: (() -> Unit)? = null,
        mostrar: (() -> Unit)? = null,
        recibo: ((Cobro) -> Unit)? = null,
        alListo: ((Cobro) -> Unit)? = null,
    ): CompletableDeferred<Cobro?> {
        ocultar?.invoke()
        val cuerpo = Cuerpo(periodo = a.periodo.take(7), monto = if (conceptos.isEmpty()) monto else null, medio = medio,
            conceptos = if (conceptos.isEmpty()) null else conceptos, pagadoEl = pagadoEl)
        val ruta = "/v1/contratos/${a.contratoId}/cobros"
        val pedido = CompletableDeferred<Cobro>()
        estado.tarea {
            try { pedido.complete(estado.api.enviar<Cobro, Cuerpo>("POST", ruta, cuerpo)) } catch (e: Throwable) { pedido.completeExceptionally(e) }
        }
        val caja = CajaCobro()
        estado.avisar(texto, deshacer = {
            caja.deshecho = true
            mostrar?.invoke()
            estado.tarea {
                val c = runCatching { pedido.await() }.getOrNull()
                if (c != null) {
                    try { estado.api.hacer("DELETE", "$ruta/${c.id}") } catch (e: CancellationException) { throw e } catch (e: Exception) {
                        estado.avisar("No se pudo deshacer · ${(e as? ErrorApi)?.detalle ?: ""}", ok = false)
                    }
                }
                estado.version += 1
            }
        })
        val avisoId = estado.aviso?.id
        val resultado = CompletableDeferred<Cobro?>()
        estado.tarea {
            try {
                val c = pedido.await()
                if (caja.deshecho) { resultado.complete(null); return@tarea }
                // «Deshacer» y «Mandar recibo» juntos en el aviso, como la web (si sigue siendo este aviso).
                if (recibo != null && estado.aviso?.id == avisoId) estado.aviso = estado.aviso?.copy(accion = "Mandar recibo" to { recibo(c) })
                alListo?.invoke(c)
                Haptico.exito()
                estado.version += 1
                resultado.complete(c)
            } catch (e: CancellationException) {
                resultado.complete(null)
                throw e
            } catch (e: ErrorApi) {
                if (caja.deshecho) { resultado.complete(null); return@tarea }
                if (e.sinConexion) {
                    try {
                        estado.cola.enviar("POST", ruta, cuerpo, etiqueta = "Cobro de ${a.nombre ?: a.propiedad ?: ""}")
                        estado.avisar("Sin señal · el cobro queda en cola")
                    } catch (e2: CancellationException) { throw e2 } catch (e2: Exception) { mostrar?.invoke(); estado.avisar(e2) }
                } else {
                    fallo(estado, a, e, monto, medio, conceptos, pagadoEl, texto, ocultar, mostrar, recibo, alListo)
                }
                resultado.complete(null)
            } catch (e: Exception) {
                if (caja.deshecho) { resultado.complete(null); return@tarea }
                fallo(estado, a, e, monto, medio, conceptos, pagadoEl, texto, ocultar, mostrar, recibo, alListo)
                resultado.complete(null)
            }
        }
        return resultado
    }

    private fun fallo(
        estado: Estado, a: ACobrar, e: Exception, monto: Double?, medio: String?, conceptos: List<Cobro.Concepto>, pagadoEl: String?, texto: String,
        ocultar: (() -> Unit)?, mostrar: (() -> Unit)?, recibo: ((Cobro) -> Unit)?, alListo: ((Cobro) -> Unit)?,
    ) {
        mostrar?.invoke()
        val n = TextosCobro.primerNombre(a.nombre)
        val detalle = (e as? ErrorApi)?.detalle ?: ""
        estado.sugerir("No se guardó el cobro de ${if (n.isEmpty()) "este contrato" else n}. $detalle", "Reintentar") {
            registrar(estado, a, monto, medio, conceptos, pagadoEl, texto, ocultar, mostrar, recibo, alListo)
        }
    }
}

/** Si el cobro se deshizo antes de que vuelva el pedido. */
class CajaCobro { @Volatile var deshecho = false }

/** «Recordar a todos» (23E): un WhatsApp por persona con su monto y su mes (uno solo aunque deba
 *  varios meses), con la vista previa y el estado de cada envío. Con el WhatsApp de la inmobiliaria
 *  conectado salen de a uno desde Corredor; sin él, cada fila abre su wa.me. */
@Composable
fun RecordarATodos(deudores: List<FilaDeuda>) {
    val estado = LocalEstado.current
    val cerrar = LocalCerrarHoja.current
    val ctx = LocalContext.current
    val alcance = rememberCoroutineScope()
    val estados = remember { mutableStateMapOf<String, RecordarATodos.EstadoEnvio>() }
    var mandando by remember { mutableStateOf(false) }
    var conectado by remember { mutableStateOf(false) }
    val telefonos = remember { mutableStateMapOf<String, String>() }

    fun texto(ds: List<FilaDeuda>): String {
        val d = ds.firstOrNull() ?: return ""
        if (ds.size == 1) return TextosCobro.recordatorio(d.nombre, d.periodo, d.monto, d.moneda, d.vence)
        return TextosCobro.recordatorioVarios(d.nombre, ds.map { it.periodo }, ds.sumOf { it.monto }, d.moneda)
    }

    val porPersona: List<RecordarATodos.Persona> = run {
        val orden = mutableListOf<String>()
        val m = mutableMapOf<String, MutableList<FilaDeuda>>()
        for (d in deudores) {
            val k = d.contactoId ?: d.id
            if (m[k] == null) orden.add(k)
            m.getOrPut(k) { mutableListOf() }.add(d)
        }
        orden.map { k -> RecordarATodos.Persona(k, m[k] ?: emptyList(), texto(m[k] ?: emptyList())) }
    }
    val enviados = porPersona.count { estados[it.id] == RecordarATodos.EstadoEnvio.enviado }

    fun abrir(p: RecordarATodos.Persona, tel: String?) {
        val u = tel?.let { WhatsAppCorredor.waMe(it, p.texto) }
        if (u == null) {
            val n = TextosCobro.primerNombre(p.ds.firstOrNull()?.nombre)
            estado.avisar("${if (n.isEmpty()) "Esta persona" else n} no tiene teléfono cargado.", ok = false)
            return
        }
        abrirUrl(ctx, u)
        estados[p.id] = RecordarATodos.EstadoEnvio.enviado
    }

    suspend fun mandarTodos() {
        mandando = true
        var fallaron = 0
        for (p in porPersona) {
            val d = p.ds.firstOrNull() ?: continue
            if (estados[p.id] == RecordarATodos.EstadoEnvio.enviado) continue
            val c = d.contactoId
            if (c == null) { estados[p.id] = RecordarATodos.EstadoEnvio.sinTelefono; continue }
            estados[p.id] = RecordarATodos.EstadoEnvio.mandando
            val total = p.ds.sumOf { it.monto }
            try {
                val u = WhatsAppCorredor.mandar(estado.api, c, p.texto, "recordatorio",
                    listOf(TextosCobro.primerNombre(d.nombre), p.ds.joinToString(" y ") { TextosCobro.nombreMes(it.periodo) },
                        Formato.plata(total, d.moneda), TextosCobro.fechaCortita(d.vence)))
                if (u != null) abrirUrl(ctx, u)
                estados[p.id] = RecordarATodos.EstadoEnvio.enviado
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                estados[p.id] = RecordarATodos.EstadoEnvio.error
                fallaron += 1
            }
        }
        mandando = false
        val ok = porPersona.count { estados[it.id] == RecordarATodos.EstadoEnvio.enviado }
        if (fallaron > 0) {
            estado.avisar("Salieron $ok de ${porPersona.size}. ${if (fallaron == 1) "Uno no salió" else "$fallaron no salieron"}: probá de nuevo.", ok = false)
        } else {
            estado.avisar("Listo: ${Formato.cantidad(ok, "recordatorio enviado", "recordatorios enviados")}.")
        }
    }

    LaunchedEffect(Unit) {
        conectado = WhatsAppCorredor.conectado(estado.api)
        for (p in porPersona) {
            val c = p.ds.firstOrNull()?.contactoId ?: continue
            if (telefonos[c] != null) continue
            val x: WhatsAppCorredor.Contacto? = estado.api.opcional("/v1/contactos/$c")
            x?.telefono?.takeIf { it.isNotBlank() }?.let { telefonos[c] = it }
        }
    }

    Column(Modifier.fillMaxSize().background(C.fondo)) {
        BarraHoja("Recordar a todos", "Cerrar", { cerrar() })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)) {
            SeccionCobro("${Formato.cantidad(porPersona.size, "persona", "personas")} · un WhatsApp a cada una, con su monto") {
                porPersona.forEachIndexed { i, p ->
                    if (i > 0) LineaFila(16.dp)
                    val d = p.ds.firstOrNull()
                    val e = estados[p.id] ?: RecordarATodos.EstadoEnvio.esperando
                    val tel = d?.contactoId?.let { telefonos[it] }
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        AvatarIniciales(d?.nombre ?: "?", tam = 36.dp)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Texto(d?.nombre ?: "Sin inquilino", Estilo.filaLista, C.texto)
                            Texto(p.texto, Estilo.footnote, C.texto2)
                        }
                        Spacer(Modifier.width(4.dp))
                        when (e) {
                            RecordarATodos.EstadoEnvio.mandando -> CargandoCobro()
                            RecordarATodos.EstadoEnvio.enviado -> Icono("checkmark.circle.fill", 17f, C.azulTinta, descripcion = "Enviado")
                            RecordarATodos.EstadoEnvio.error -> Icono("exclamationmark.circle.fill", 17f, C.error, descripcion = "No salió")
                            RecordarATodos.EstadoEnvio.sinTelefono -> Texto("Sin contacto", Estilo.caption, C.texto3)
                            RecordarATodos.EstadoEnvio.esperando -> if (!conectado) {
                                BotonLink(if (tel == null) "Sin teléfono" else "Abrir", Estilo.subheadFuerte, C.accion, habilitado = tel != null) { abrir(p, tel) }
                            }
                        }
                    }
                }
            }
        }
        if (conectado) {
            Box(Modifier.fillMaxWidth().background(C.fondo).padding(16.dp)) {
                BotonPrincipal(
                    if (mandando) "Mandando ${enviados + 1} de ${porPersona.size}…"
                    else if (enviados == porPersona.size && enviados > 0) "Listo"
                    else "Mandar a ${Formato.cantidad(porPersona.size, "persona", "personas")}",
                    { if (enviados == porPersona.size && enviados > 0) cerrar() else alcance.launch { mandarTodos() } },
                    habilitado = !mandando,
                )
            }
        } else {
            Texto("Sin el WhatsApp de la inmobiliaria conectado, cada uno se abre en tu WhatsApp.", Estilo.footnote, C.texto2,
                Modifier.fillMaxWidth().background(C.fondo).padding(16.dp), alinear = TextAlign.Center)
        }
    }
}

/** Lo que RecordarATodos declara adentro en iOS. */
object RecordarATodos {
    enum class EstadoEnvio { esperando, mandando, enviado, error, sinTelefono }
    data class Persona(val id: String, val ds: List<FilaDeuda>, val texto: String)
}
