package ar.com.corredor.app.pantallas.informes

import ar.com.corredor.app.app.PantallaApilada

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ar.com.corredor.app.app.LocalEstado
import ar.com.corredor.app.nucleo.*
import ar.com.corredor.app.pantallas.mas.*
import ar.com.corredor.app.tema.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

// Clon de Pantallas/Informes/Indices.swift.

/** Las cuentas de los índices, como la web (lib/contratos/indices.ts). */
object CuentasIndices {
    @Serializable
    data class Punto(val periodo: String, val valor: Double)

    /** El último valor publicado hasta esa fecha. */
    fun valorEn(serie: List<Punto>, fecha: String): Punto? {
        var v: Punto? = null
        for (p in serie) { if (p.periodo > fecha) break; v = p }
        return v
    }

    /** El factor desde el primer día de un mes (yyyy-MM) hasta el último dato. */
    fun factorDesde(codigo: String, serie: List<Punto>, mes: String): Double? {
        if (serie.isEmpty()) return null
        if (codigo == "ipc") {
            // Los meses posteriores al de partida (el aumento del mes de partida ya está en el alquiler).
            return serie.filter { it.periodo.take(7) > mes }.fold(1.0) { a, p -> a * (1 + p.valor / 100) }
        }
        val base = valorEn(serie, "$mes-01") ?: return null
        if (base.valor <= 0) return null
        val u = serie.lastOrNull() ?: return null
        return u.valor / base.valor
    }

    private fun restarMeses(fecha: String, n: Int): String {
        val d = Ritmo.dia(fecha) ?: return fecha
        return d.minusMonths(n.toLong()).toString()
    }

    /** La variación de los últimos `meses` (ICL y UVA: punta a punta; IPC: acumulada). */
    fun variacion(codigo: String, serie: List<Punto>, meses: Int): Double? {
        val u = serie.lastOrNull() ?: return null
        if (codigo == "ipc") {
            val ult = serie.takeLast(meses)
            return if (ult.size < meses) null else ult.fold(1.0) { a, p -> a * (1 + p.valor / 100) } - 1
        }
        val b = valorEn(serie, restarMeses(u.periodo, meses)) ?: return null
        if (b.valor <= 0) return null
        return u.valor / b.valor - 1
    }

    /** Un valor por mes (el último del mes anterior o el último dato), los últimos `n` meses. */
    fun mensuales(codigo: String, serie: List<Punto>, n: Int): List<Double> {
        if (codigo == "ipc") return serie.takeLast(n).map { it.valor }
        val u = serie.lastOrNull()?.periodo ?: return emptyList()
        val du = Ritmo.dia(u) ?: return emptyList()
        val out = mutableListOf<Double>()
        for (k in (n - 1) downTo 0) {
            var f = u
            if (k > 0) {
                val ini = du.withDayOfMonth(1)
                f = ini.plusMonths((-k + 1).toLong()).minusDays(1).toString()
            }
            valorEn(serie, f)?.let { out.add(it.valor) }
        }
        return out
    }
}

/** Los índices de ajuste, como la página del panel (25E): ICL, IPC y UVA con su valor en su forma, la fuente,
 *  cuándo se actualizó, la variación y los últimos 6 meses; el aviso si el IPC del mes pasado todavía no salió; y la
 *  calculadora de alquiler con «copiar para WhatsApp». */
@Serializable
data class Ind(val codigo: String, val ultimoPeriodo: String? = null, val ultimoValor: Double? = null, val actualizadoEn: FechaApi? = null) {
    val id: String get() = codigo
}

private val fuenteIndices = mapOf("icl" to "BCRA · diario", "ipc" to "INDEC · mensual", "uva" to "BCRA · diario")

private fun decIndice(n: Double, d: Int = 2): String {
    val f = NumberFormat.getNumberInstance(Formato.argentina)
    f.minimumFractionDigits = d
    f.maximumFractionDigits = d
    return f.format(n)
}

private fun pctIndice(n: Double): String = "${if (n > 0) "+" else ""}${decIndice(n * 100, 1)}${Formato.fino}%"
private fun nombreMesIndice(m: String): String = m.drop(5).take(2).toIntOrNull()?.let { Ritmo.meses[max(0, min(11, it - 1))] } ?: m
private fun etiquetaMes(m: String): String = "${Formato.mayuscula(nombreMesIndice(m))} ${m.take(4)}"

@Composable
fun Indices() {
    val estado = LocalEstado.current
    val ctx = LocalContext.current
    val alcance = rememberCoroutineScope()
    var estados by remember { mutableStateOf<List<Ind>?>(null) }
    var series by remember { mutableStateOf<Map<String, List<CuentasIndices.Punto>>>(emptyMap()) }
    var ajustanConIPC by remember { mutableStateOf(0) }
    var error by remember { mutableStateOf<ErrorApi?>(null) }
    var alquiler by remember { mutableStateOf("600.000") }
    var desde by remember { mutableStateOf("") }
    var indice by remember { mutableStateOf("icl") }

    val hoy = Formato.diaISO(Instant.now())

    /** Los últimos 24 meses (sin el actual), del más nuevo al más viejo. */
    val meses: List<String> = Ritmo.dia(hoy)?.let { d -> (1..24).map { k -> d.minusMonths(k.toLong()).toString().take(7) } } ?: emptyList()
    val mesPasado = meses.firstOrNull()?.let { "$it-01" } ?: hoy
    val ipcDemorado = estados?.firstOrNull { it.codigo == "ipc" }?.ultimoPeriodo?.let { it < mesPasado } ?: false

    LaunchedEffect(meses.size) { if (desde.isEmpty()) desde = if (meses.size > 5) meses[5] else (meses.firstOrNull() ?: "") }

    suspend fun cargar() {
        try {
            estados = estado.api.get<List<Ind>>("/v1/indices")
            error = null
        } catch (e: CancellationException) { throw e } catch (e: ErrorApi) {
            if (estados == null) error = e
            return
        } catch (e: Exception) { return }
        val d = Ritmo.dia(hoy) ?: return
        val desdeSerie = d.minusMonths(25).withDayOfMonth(1).toString()
        val s = mutableMapOf<String, List<CuentasIndices.Punto>>()
        for (c in listOf("icl", "ipc", "uva")) {
            s[c] = estado.api.opcional<List<CuentasIndices.Punto>>("/v1/indices/$c", listOf("desde" to desdeSerie)) ?: emptyList()
        }
        series = s
        // JSONLibre de iOS: el contrato del evento, libre.
        @Serializable data class Evento(val contratoId: String, val tipo: String, val contrato: JsonElement? = null)
        val ev = estado.api.opcional<List<Evento>>("/v1/contratos/vencimientos", listOf("dias" to "60")) ?: emptyList()
        ajustanConIPC = ev.filter { e ->
            e.tipo == "ajuste" && ((e.contrato as? JsonObject)?.get("ajusteIndice")?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() } == "ipc")
        }.map { it.contratoId }.toSet().size
    }

    @Composable
    fun avisoIPC() {
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.avisoFondo).padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icono("hourglass", 20f, C.avisoTinta)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Texto("El IPC de ${nombreMesIndice(mesPasado)} todavía no salió", Estilo.subheadFuerte, C.texto)
                Texto(Formato.sinViudas((if (ajustanConIPC > 0) "${if (ajustanConIPC == 1) "1 contrato ajusta" else "$ajustanConIPC contratos ajustan"} con ese dato. " else "") +
                    "Mostramos un estimado con el último valor y te avisamos cuando lo publique el INDEC."), Estilo.footnote, C.texto2)
            }
        }
    }

    fun actualizado(f: Instant?): String {
        if (f == null) return "Sin datos todavía"
        val z = Formato.zona
        return if (f.atZone(z).toLocalDate() == LocalDate.now(z)) "Actualizado hoy ${Formato.horaDe(f)}" else "Actualizado el ${Formato.dia(f)}"
    }

    @Composable
    fun tarjeta(c: String, e: Ind?) {
        val serie = series[c] ?: emptyList()
        val barras = CuentasIndices.mensuales(c, serie, 6)
        val maximo = max(barras.maxOrNull() ?: 0.0001, 0.0001)
        val minimo = if (c == "ipc") 0.0 else (barras.minOrNull() ?: 0.0) * 0.9
        val v6 = CuentasIndices.variacion(c, serie, 6)
        val v12 = CuentasIndices.variacion(c, serie, 12)
        val valor = e?.ultimoValor?.let { if (c == "ipc") "${decIndice(it, 1)}${Formato.fino}%" else if (c == "uva") "$${Formato.fino}${decIndice(it)}" else decIndice(it) } ?: "—"
        val detalle = if (c == "ipc")
            (e?.ultimoPeriodo?.let { "${Formato.mayuscula(nombreMesIndice(it))}${v12?.let { v -> " · ${decIndice(v * 100, 1)}${Formato.fino}% interanual" } ?: ""}" } ?: "")
        else (v6?.let { "${pctIndice(it)} en 6 meses" } ?: "")
        Column(Modifier.fillMaxWidth().tarjeta().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Texto(c.uppercase(), Estilo.headline, C.texto, Modifier.weight(1f))
                Texto(fuenteIndices[c] ?: "", Estilo.footnote, C.texto3)
            }
            Texto(valor, Estilo.montoMeta, C.texto, numeros = true)
            if (detalle.isNotEmpty()) Texto(detalle, Estilo(14f, if (c == "ipc") Peso.regular else Peso.semibold), if (c == "ipc") C.texto2 else C.okTinta)
            if (barras.isEmpty()) {
                Texto("Sin datos todavía", Estilo.footnote, C.texto3)
            } else {
                Row(Modifier.fillMaxWidth().height(48.dp).semantics { invisibleToUser() }, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
                    val opacidades = listOf(0.18f, 0.18f, 0.3f, 0.45f, 0.65f, 1f)
                    barras.forEachIndexed { i, b ->
                        val alto = max(6.0, (b - minimo) / max(maximo - minimo, 0.0001) * 48).coerceAtMost(48.0)
                        Box(Modifier.weight(1f).height(alto.dp).clip(RoundedCornerShape(4.dp))
                            .background(C.verde700.copy(alpha = opacidades[max(0, 6 - barras.size + i).coerceAtMost(5)])))
                    }
                }
            }
            Texto(actualizado(e?.actualizadoEn), Estilo(12f, Peso.regular), C.texto3)
        }
    }

    val monto: Double? = alquiler.filter { it.isDigit() }.toDoubleOrNull()?.takeIf { it > 0 }
    val factor: Double? = if (desde.isEmpty()) null else CuentasIndices.factorDesde(indice, series[indice] ?: emptyList(), desde)
    val resultado: Double? = if (monto != null && factor != null) (monto * factor).roundToLong().toDouble() else null

    @Composable
    fun calculadora() {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(C.verde700).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ConIcono("Calculadora de alquiler", "function", Estilo.headline, Color.White)
            Etiquetado("Alquiler") {
                Row(Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(12.dp)).background(C.superficie).padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Texto("ARS", Estilo.label, C.texto2)
                    Entrada(alquiler, { v ->
                        val n = v.filter { it.isDigit() }
                        alquiler = if (n.isEmpty()) "" else Formato.numero(n.toLongOrNull()?.coerceAtMost(Int.MAX_VALUE.toLong())?.toInt() ?: 0)
                    }, "", Modifier.weight(1f).semantics { contentDescription = "Alquiler en pesos" }, estilo = Estilo.body16, teclado = KeyboardType.Number, numeros = true)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f)) {
                    Etiquetado("Desde") {
                        Box(Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(RoundedCornerShape(12.dp)).background(C.superficie), contentAlignment = Alignment.Center) {
                            SelectorMenu(meses.map { it to etiquetaMes(it) }, desde, { desde = it }, "Desde")
                        }
                    }
                }
                Box(Modifier.weight(1f)) {
                    Etiquetado("Índice") {
                        Box(Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(RoundedCornerShape(12.dp)).background(C.superficie), contentAlignment = Alignment.Center) {
                            SelectorMenu(listOf("icl" to "ICL", "ipc" to "IPC", "uva" to "UVA"), indice, { indice = it }, "Índice")
                        }
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Texto("Hoy sería", Estilo.footnote, Color.White.copy(alpha = 0.8f))
                Texto(resultado?.let { Formato.plata(it, "ARS") } ?: "—", Estilo.montoMeta, C.oro, numeros = true)
                if (resultado != null && factor != null && monto != null) {
                    BotonTonal("${pctIndice(factor - 1)} · copiar para WhatsApp", {
                        copiar(ctx, "Un alquiler de ${Formato.plata(monto, "ARS")} desde ${etiquetaMes(desde)}, ajustado por ${indice.uppercase()}, hoy sería ${Formato.plata(resultado, "ARS")} (${pctIndice(factor - 1)}).")
                        estado.avisar("Copiado. Pegalo en WhatsApp.")
                    })
                } else {
                    Texto("Falta el dato del índice para ese mes", Estilo.footnote, Color.White.copy(alpha = 0.8f))
                }
            }
        }
    }

    PantallaApilada("Índices") {
        Refrescable({ cargar() }) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Texto(Formato.sinViudas("Sólo consulta. Cada índice dice su fuente y cuándo se actualizó."), Estilo.subhead, C.texto2)
                val l = estados
                val e = error
                if (l != null) {
                    if (ipcDemorado) avisoIPC()
                    listOf("icl", "ipc", "uva").forEach { c -> tarjeta(c, l.firstOrNull { it.codigo == c }) }
                    calculadora()
                } else if (e != null) {
                    Box(Modifier.padding(top = 30.dp)) { EstadoError("los índices", e) { alcance.launch { cargar() } } }
                } else {
                    EsqueletoFilas(3)
                }
            }
        }
    }
    LaunchedEffect(estado.version) { cargar() }
}
