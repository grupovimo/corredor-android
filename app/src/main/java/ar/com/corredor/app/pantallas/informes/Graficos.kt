package ar.com.corredor.app.pantallas.informes

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import ar.com.corredor.app.app.LocalEstado
import ar.com.corredor.app.nucleo.*
import ar.com.corredor.app.pantallas.cobranzas.MesCobranzas
import ar.com.corredor.app.tema.*
import kotlinx.coroutines.delay
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.max
import kotlin.math.roundToInt

// Clon de Pantallas/Informes/Graficos.swift.
// Los gráficos de Corredor (en iOS, Swift Charts): la plata, las visitas y las métricas. Cada uno arranca en
// cero y crece al aparecer; los colores salen de la paleta (oro y verde de la marca, más los tonos). Acá se
// dibujan con Canvas: barras apiladas por mes y por semana, la dona y las barras horizontales.

/** Una tarjeta con título y su gráfico. */
@Composable
fun TarjetaGrafico(titulo: String, detalle: String? = null, contenido: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().tarjeta().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Texto(titulo, Estilo.subheadFuerte, C.texto)
            if (detalle != null) Texto(detalle, Estilo.footnote, C.texto3)
        }
        contenido()
    }
}

/** Crece desde cero al aparecer (para que el gráfico «se dibuje»): 0 → 1 con un resorte de 0,9 s y 0,1 s de espera. */
@Composable
private fun crecer(): Float {
    val anima = animacionesActivas()
    var listo by remember { mutableStateOf(!anima) }
    LaunchedEffect(Unit) { delay(100); listo = true }
    val p by animateFloatAsState(if (listo) 1f else 0f, spring(dampingRatio = 0.75f, stiffness = 60f), label = "crecer")
    return p
}

/** ProgressView() mientras carga. */
@Composable
private fun Cargando(alto: Int) {
    Box(Modifier.fillMaxWidth().heightIn(min = alto.dp), contentAlignment = Alignment.Center) { Rueda(C.texto3) }
}

/** La leyenda de abajo a la izquierda (chartLegend(position: .bottom, alignment: .leading)). */
@Composable
private fun Leyenda(series: List<Pair<String, Color>>) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        series.forEach { (n, c) ->
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(c))
                Texto(n, Estilo(12f, Peso.regular), C.texto2)
            }
        }
    }
}

/** Un tope «redondo» para el eje Y (como el dominio automático de Swift Charts) y sus marcas. */
private fun marcasEje(maximo: Double): List<Double> {
    if (maximo <= 0) return listOf(0.0)
    val crudo = maximo / 4
    val p = Math.pow(10.0, Math.floor(Math.log10(crudo)))
    val paso = listOf(1.0, 2.0, 2.5, 5.0, 10.0).map { it * p }.first { it >= crudo }
    val n = Math.ceil(maximo / paso).toInt().coerceAtLeast(1)
    return (0..n).map { it * paso }
}

/** Barras apiladas por columna (BarMark con el mismo x y otra serie): el eje X con su etiqueta cada `cadaX`, la grilla
 *  y las etiquetas del eje Y a la derecha. */
private fun DrawScope.barrasApiladas(
    columnas: List<List<Double>>,
    colores: List<Color>,
    etiquetasX: List<String?>,
    etiquetaY: (Double) -> String,
    progreso: Float,
    medidor: TextMeasurer,
    estiloEje: TextStyle,
    grilla: Color,
) {
    val maximo = columnas.maxOfOrNull { it.sum() } ?: 0.0
    val marcas = marcasEje(maximo)
    val tope = marcas.last().takeIf { it > 0 } ?: 1.0
    val anchoY = marcas.maxOf { medidor.measure(etiquetaY(it), estiloEje).size.width }.toFloat() + 6.dp.toPx()
    val altoX = medidor.measure("Mg", estiloEje).size.height.toFloat() + 4.dp.toPx()
    val area = Size(size.width - anchoY, size.height - altoX)
    marcas.forEach { m ->
        val y = area.height - (m / tope).toFloat() * area.height
        drawLine(grilla, Offset(0f, y), Offset(area.width, y), 0.5.dp.toPx())
        val t = medidor.measure(etiquetaY(m), estiloEje)
        drawText(t, topLeft = Offset(area.width + 6.dp.toPx(), (y - t.size.height / 2f).coerceIn(0f, size.height - t.size.height)))
    }
    val n = columnas.size.coerceAtLeast(1)
    val franja = area.width / n
    val ancho = franja * 0.6f
    val radio = CornerRadius(5.dp.toPx())
    columnas.forEachIndexed { i, serie ->
        val x = franja * i + (franja - ancho) / 2
        var base = area.height
        serie.forEachIndexed { k, v ->
            val alto = (v / tope).toFloat() * area.height * progreso
            if (alto > 0f) {
                drawRoundRect(colores[k % colores.size], Offset(x, base - alto), Size(ancho, alto), radio)
                base -= alto
            }
        }
        etiquetasX.getOrNull(i)?.let {
            val t = medidor.measure(it, estiloEje)
            drawText(t, topLeft = Offset((x + ancho / 2 - t.size.width / 2f).coerceIn(0f, size.width - t.size.width), area.height + 4.dp.toPx()))
        }
    }
}

// MARK: - Plata: cobrado y pendiente de los últimos 6 meses

private data class MesGrafico(val mes: LocalDate, val cobrado: Double, val pendiente: Double)

@Composable
fun GraficoCobranzaMeses() {
    val estado = LocalEstado.current
    var meses by remember { mutableStateOf<List<MesGrafico>>(emptyList()) }

    suspend fun cargar() {
        val r = mutableListOf<MesGrafico>()
        val hoy = LocalDate.now().withDayOfMonth(1)
        for (n in (0 until 6).reversed()) {
            val m = hoy.minusMonths(n.toLong())
            val p = m.toString().take(7)
            val x = estado.api.opcional<MesCobranzas>("/v1/cobranzas", listOf("mes" to p))
            r.add(MesGrafico(m, x?.totalCobrado?.get("ARS") ?: 0.0, x?.totalPendiente?.get("ARS") ?: 0.0))
        }
        meses = r
    }

    TarjetaGrafico("Los últimos 6 meses", "Cobrado y pendiente, en pesos") {
        if (meses.isEmpty()) {
            Cargando(180)
        } else {
            val progreso = crecer()
            val medidor = rememberTextMeasurer()
            val estiloEje = Estilo(10f, Peso.regular).ts(numeros = true).copy(color = C.texto3)
            val grilla = C.linea
            val cobrado = hex(0x2F9E6F)
            val pendiente = C.oro.copy(alpha = 0.55f)
            val formato = DateTimeFormatter.ofPattern("MMM", Formato.argentina)
            Canvas(Modifier.fillMaxWidth().height(160.dp).semantics { contentDescription = "Cobrado y pendiente de los últimos 6 meses" }) {
                barrasApiladas(
                    meses.map { listOf(it.cobrado, it.pendiente) }, listOf(cobrado, pendiente),
                    meses.map { formato.format(it.mes).replace(".", "") },
                    { Formato.plataCorta(it).replace("ARS ", "") }, progreso, medidor, estiloEje, grilla,
                )
            }
            Leyenda(listOf("Cobrado" to cobrado, "Pendiente" to pendiente))
        }
    }
    LaunchedEffect(estado.version) { cargar() }
}

// MARK: - Visitas por semana (últimas 8)

private data class SemanaGrafico(val semana: LocalDate, val hechas: Int, val otras: Int)

@Composable
fun GraficoVisitasSemanas() {
    val estado = LocalEstado.current
    var semanas by remember { mutableStateOf<List<SemanaGrafico>>(emptyList()) }

    suspend fun cargar() {
        val zona = ZoneId.systemDefault()
        val hoy = LocalDate.now()
        val inicio = hoy.with(DayOfWeek.MONDAY).minusWeeks(7)
        val citas: List<Cita> = try {
            estado.api.todas<Cita>("/v1/agenda", listOf("desde" to Formato.diaISO(inicio.atStartOfDay(zona).toInstant()), "hasta" to Formato.diaISO(Instant.now())))
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { emptyList() }
        val r = mutableListOf<SemanaGrafico>()
        for (n in 0 until 8) {
            val s = inicio.plusWeeks(n.toLong())
            val desde = s.atStartOfDay(zona).toInstant()
            val fin = s.plusDays(7).atStartOfDay(zona).toInstant()
            val deLaSemana = citas.filter { (it.tipo ?: "visita") == "visita" && !it.empiezaEn.isBefore(desde) && it.empiezaEn.isBefore(fin) }
            val hechas = deLaSemana.count { it.estado == "hecha" }
            r.add(SemanaGrafico(s, hechas, deLaSemana.size - hechas))
        }
        semanas = r
    }

    TarjetaGrafico("Visitas por semana", "Las últimas 8 semanas") {
        if (semanas.isEmpty()) {
            Cargando(160)
        } else {
            val progreso = crecer()
            val medidor = rememberTextMeasurer()
            val estiloEje = Estilo(10f, Peso.regular).ts(numeros = true).copy(color = C.texto3)
            val grilla = C.linea
            val hechas = C.accion
            val agendadas = C.oro.copy(alpha = 0.5f)
            val formato = DateTimeFormatter.ofPattern("d MMM", Formato.argentina)
            Canvas(Modifier.fillMaxWidth().height(140.dp).semantics { contentDescription = "Visitas por semana, las últimas 8 semanas" }) {
                barrasApiladas(
                    semanas.map { listOf(it.hechas.toDouble(), it.otras.toDouble()) }, listOf(hechas, agendadas),
                    // AxisMarks(values: .stride(by: .weekOfYear, count: 2)): una etiqueta cada dos semanas.
                    semanas.mapIndexed { i, s -> if (i % 2 == 0) formato.format(s.semana).replace(".", "") else null },
                    { Formato.numero(it.roundToInt()) }, progreso, medidor, estiloEje, grilla,
                )
            }
            Leyenda(listOf("Hechas" to hechas, "Agendadas" to agendadas))
        }
    }
    LaunchedEffect(estado.version) { cargar() }
}

// MARK: - Métricas del informe

/** De dónde vienen las consultas: una dona con la cantidad en el centro. */
@Composable
fun GraficoOrigenes(origenes: List<Pair<String, Double>>) {
    val colores = listOf(C.oro, hex(0x2F9E6F), hex(0x9CC2EF), hex(0xC9A8EF), hex(0xF29B92), C.texto3)
    val total = origenes.sumOf { it.second }
    TarjetaGrafico("De dónde vienen las consultas") {
        val progreso = crecer()
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.size(150.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    // SectorMark(innerRadius: .ratio(0.62), angularInset: 2): antes de crecer, todo el anillo es del primero.
                    val valores = origenes.mapIndexed { i, o -> o.second * progreso + (if (i == 0) 1.0 else 0.0) * (1 - progreso) }
                    val suma = valores.sum().takeIf { it > 0 } ?: 1.0
                    val grosor = size.minDimension / 2 * 0.38f
                    val radio = size.minDimension / 2 - grosor / 2
                    val centro = Offset(size.width / 2, size.height / 2)
                    val separacion = if (valores.count { it > 0 } > 1) 2f else 0f
                    var angulo = -90f
                    valores.forEachIndexed { i, v ->
                        val barrido = (v / suma * 360).toFloat()
                        if (barrido > separacion) {
                            drawArc(colores[i % colores.size], angulo + separacion / 2, barrido - separacion, false,
                                Offset(centro.x - radio, centro.y - radio), Size(radio * 2, radio * 2), style = Stroke(grosor))
                        }
                        angulo += barrido
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Texto("${total.toInt()}", Estilo(22f, Peso.extrabold), C.texto, numeros = true)
                    Texto("consultas", Estilo.footnote, C.texto3)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                origenes.take(6).forEachIndexed { i, o ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.size(9.dp).clip(CircleShape).background(colores[i % colores.size]))
                        Texto(o.first, Estilo.footnote, C.texto, Modifier.weight(1f), lineas = 1)
                        Texto(if (total > 0) "${(o.second / total * 100).roundToInt()} %" else "—", Estilo.footnoteFuerte, C.texto2)
                    }
                }
            }
        }
    }
}

/** Barras horizontales (el embudo de operaciones, las propiedades más vistas). */
@Composable
fun GraficoBarras(titulo: String, detalle: String? = null, filas: List<Pair<String, Double>>, color: Color = C.oro) {
    TarjetaGrafico(titulo, detalle) {
        val progreso = crecer()
        val medidor = rememberTextMeasurer()
        val estiloNombre = Estilo(12f, Peso.medium).ts().copy(color = C.texto2)
        val estiloNumero = Estilo.footnoteFuerte.ts(numeros = true).copy(color = C.texto2)
        // .foregroundStyle(color.gradient): el color con un leve degradé.
        val arriba = color.copy(alpha = color.alpha)
        val abajo = color.copy(alpha = color.alpha * 0.8f)
        Canvas(Modifier.fillMaxWidth().height((max(filas.size, 1) * 34).dp).semantics { contentDescription = titulo }) {
            val maximo = filas.maxOfOrNull { it.second }?.takeIf { it > 0 } ?: 1.0
            val anchoNombre = (filas.maxOfOrNull { medidor.measure(it.first, estiloNombre).size.width } ?: 0).toFloat() + 8.dp.toPx()
            val anchoNumero = (filas.maxOfOrNull { medidor.measure(Formato.numero(it.second.toInt()), estiloNumero).size.width } ?: 0).toFloat() + 6.dp.toPx()
            val franja = size.height / max(filas.size, 1)
            val alto = franja * 0.6f
            val disponible = (size.width - anchoNombre - anchoNumero).coerceAtLeast(1f)
            filas.forEachIndexed { i, f ->
                val y = franja * i + (franja - alto) / 2
                val n = medidor.measure(f.first, estiloNombre)
                drawText(n, topLeft = Offset(0f, franja * i + (franja - n.size.height) / 2))
                val w = (f.second / maximo).toFloat() * disponible * progreso
                if (w > 0f) {
                    drawRoundRect(Brush.verticalGradient(listOf(arriba, abajo), y, y + alto), Offset(anchoNombre, y), Size(w, alto), CornerRadius(5.dp.toPx()))
                }
                val t = medidor.measure(Formato.numero(f.second.toInt()), estiloNumero)
                drawText(t, topLeft = Offset(anchoNombre + w + 4.dp.toPx(), franja * i + (franja - t.size.height) / 2))
            }
        }
    }
}
