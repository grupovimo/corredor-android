package ar.com.corredor.app.pantallas.propiedades

import ar.com.corredor.app.app.PantallaApilada

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import ar.com.corredor.app.app.LocalEstado
import ar.com.corredor.app.nucleo.Fecha
import ar.com.corredor.app.nucleo.Formato
import ar.com.corredor.app.nucleo.opcional
import ar.com.corredor.app.pantallas.inicio.Cifra
import ar.com.corredor.app.tema.*
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

// Clon de Pantallas/Propiedades/Vistas.swift.

// MARK: - Vistas de una propiedad

/** Vistas de una propiedad en la web: el total y el gráfico por día de los últimos 90 días. */
@Composable
fun VistasPropiedad(propiedad: String) {
    val estado = LocalEstado.current
    var r by remember { mutableStateOf<VistasPropiedadRespuesta?>(null) }
    val dias = r?.dias ?: emptyList()

    /** Las vistas desde hace `n` días (por fecha: los días sin vistas no vienen). */
    fun ultimos(n: Int): Int {
        val desde = LocalDate.now(Formato.zona).minusDays(n.toLong()).atStartOfDay(Formato.zona).toInstant()
        return dias.filter { (Fecha.dia(it.dia) ?: Instant.MIN) > desde }.sumOf { it.cantidad }
    }

    LaunchedEffect(Unit) {
        r = estado.api.opcional<VistasPropiedadRespuesta>("/v1/propiedades/$propiedad/vistas") ?: VistasPropiedadRespuesta(total = 0, dias = emptyList())
    }

    PantallaApilada("Vistas") {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) { Cifra(etiqueta = "Desde siempre", valor = Formato.numero(r?.total ?: 0)) }
                Box(Modifier.weight(1f)) { Cifra(etiqueta = "Últimos 90 días", valor = Formato.numero(dias.sumOf { it.cantidad })) }
                Box(Modifier.weight(1f)) { Cifra(etiqueta = "Últimos 7 días", valor = Formato.numero(ultimos(7))) }
            }
            Box(Modifier.fillMaxWidth().tarjeta().padding(14.dp)) {
                GraficoVistasPorDia(dias, Modifier.fillMaxWidth().height(220.dp))
            }
            if (r != null && dias.isEmpty()) Texto("Todavía nadie la vio. Publicala y compartila para que llegue.", Estilo.subhead, C.texto2)
        }
    }
}

/** `VistasPropiedad.Dia` de iOS. */
@Serializable
data class VistasPropiedadDia(val dia: String, val cantidad: Int) {
    val id: String get() = dia
}

/** `GET /v1/propiedades/{id}/vistas`: el total de siempre y los días (sólo los que tuvieron vistas). */
@Serializable
data class VistasPropiedadRespuesta(val total: Int? = null, val dias: List<VistasPropiedadDia>? = null)

/** El BarMark por día de Swift Charts: una barra por día (con radio 3) en el color de la acción, del primer al último
 *  día con vistas, con los ejes automáticos de Charts: las cantidades a la derecha con su línea de guía y las fechas
 *  abajo (los meses si el rango pasa de un mes, si no cada semana con el día). */
@Composable
private fun GraficoVistasPorDia(dias: List<VistasPropiedadDia>, modifier: Modifier) {
    val color = C.accion
    val linea = C.linea
    val rotulo = C.texto3
    val medidor = rememberTextMeasurer()
    val estiloRotulo = Estilo(10f, Peso.regular, relativo = Relativo.caption2).ts(numeros = true).copy(color = rotulo)
    val fechas = dias.mapNotNull { d -> Fecha.dia(d.dia)?.let { it.atZone(ZoneId.systemDefault()).toLocalDate() to d.cantidad } }
    val mes = remember { DateTimeFormatter.ofPattern("MMM", Formato.argentina) }
    val diaMes = remember { DateTimeFormatter.ofPattern("d MMM", Formato.argentina) }
    Canvas(modifier.semantics { contentDescription = fechas.joinToString(", ") { "${diaMes.format(it.first)}: ${it.second}" } }) {
        val maximo = (fechas.maxOfOrNull { it.second } ?: 0).coerceAtLeast(1)
        // Las marcas del eje de cantidades: 0 y pasos «redondos» hasta pasar el máximo (como Charts).
        val crudo = maximo / 4.0
        val potencia = Math.pow(10.0, Math.floor(Math.log10(crudo.coerceAtLeast(1.0))))
        val paso = listOf(1.0, 2.0, 2.5, 5.0, 10.0).map { it * potencia }.first { it >= crudo }.let { Math.ceil(it).toInt().coerceAtLeast(1) }
        val tope = ((maximo + paso - 1) / paso) * paso
        val marcas = (0..tope step paso).toList()
        val anchoY = marcas.maxOf { medidor.measure(Formato.numero(it), estiloRotulo).size.width }.toFloat() + 6.dp.toPx()
        val altoX = medidor.measure("0", estiloRotulo).size.height.toFloat() + 6.dp.toPx()
        val ancho = size.width - anchoY
        val alto = size.height - altoX
        // Las líneas de guía horizontales y sus rótulos a la derecha.
        marcas.forEach { m ->
            val y = alto - alto * m / tope
            drawLine(linea, Offset(0f, y), Offset(ancho, y), strokeWidth = 1.dp.toPx())
            val t = medidor.measure(Formato.numero(m), estiloRotulo)
            drawText(t, topLeft = Offset(ancho + 6.dp.toPx(), (y - t.size.height / 2f).coerceIn(0f, alto - t.size.height / 2f)))
        }
        if (fechas.isEmpty()) return@Canvas
        val primero = fechas.minOf { it.first }
        val ultimo = fechas.maxOf { it.first }
        val total = ChronoUnit.DAYS.between(primero, ultimo) + 1
        val columna = ancho / total
        val barra = (columna * 0.7f).coerceAtLeast(1f)
        // Las marcas de fecha: el primer día de cada mes, o cada 7 días si el rango es corto.
        val porMes = total > 31
        var d = if (porMes) primero.withDayOfMonth(1).let { if (it < primero) it.plusMonths(1) else it } else primero
        while (!d.isAfter(ultimo)) {
            val x = ChronoUnit.DAYS.between(primero, d) * columna
            drawLine(linea, Offset(x, 0f), Offset(x, alto + 3.dp.toPx()), strokeWidth = 1.dp.toPx())
            val t = medidor.measure(if (porMes) mes.format(d) else diaMes.format(d), estiloRotulo)
            if (x + t.size.width <= ancho + anchoY) drawText(t, topLeft = Offset(x + 2.dp.toPx(), alto + 4.dp.toPx()))
            d = if (porMes) d.plusMonths(1) else d.plusDays(7)
        }
        fechas.forEach { (dia, n) ->
            val i = ChronoUnit.DAYS.between(primero, dia)
            val h = alto * n / tope
            drawRoundRect(
                color, Offset(i * columna + (columna - barra) / 2, alto - h), Size(barra, h),
                CornerRadius(3.dp.toPx(), 3.dp.toPx()),
            )
        }
    }
}
