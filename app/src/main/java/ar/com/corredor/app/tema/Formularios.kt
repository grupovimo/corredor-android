package ar.com.corredor.app.tema

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ar.com.corredor.app.nucleo.Formato
import java.time.LocalDate

// Form y Section de SwiftUI con el estilo agrupado de iOS (y los colores de Corredor): un Formulario que se desplaza,
// Bloques con su título y su pie, y los renglones de siempre (texto, valor, campo, interruptor, selector, botón).

/** Form { … } de iOS: fondo con grano, los bloques uno abajo del otro con aire entre ellos y lugar para el teclado.
 *  `abajo`: lo que queda libre al final (la barra de pestañas o la de acción). */
@Composable
fun Formulario(modifier: Modifier = Modifier, abajo: Dp = 24.dp, contenido: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 12.dp, bottom = abajo),
        verticalArrangement = Arrangement.spacedBy(22.dp), content = contenido,
    )
}

/** Section { … } header: { … } footer: { … }: una tarjeta con rótulo y renglones separados por línea. */
@Composable
fun Bloque(titulo: String = "", pie: String = "", vararg filas: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        if (titulo.isNotEmpty()) RotuloBloque(titulo)
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(M.rControl)).background(C.superficie)) {
            filas.forEachIndexed { i, f ->
                if (i > 0) Box(Modifier.padding(start = 16.dp).fillMaxWidth().height(1.dp).background(C.linea))
                f()
            }
        }
        if (pie.isNotEmpty()) PieBloque(pie)
    }
}

/** La misma tarjeta con el contenido libre (los separadores entre renglones, con `Separador()`). */
@Composable
fun Bloque(titulo: String = "", pie: String = "", modifier: Modifier = Modifier, contenido: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        if (titulo.isNotEmpty()) RotuloBloque(titulo)
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(M.rControl)).background(C.superficie), content = contenido)
        if (pie.isNotEmpty()) PieBloque(pie)
    }
}

/** El encabezado de una Section (13, en mayúsculas como iOS). */
@Composable
fun RotuloBloque(texto: String) {
    Texto(texto.uppercase(Formato.argentina), Estilo.footnote, C.texto2, Modifier.padding(horizontal = 16.dp), tracking = 0.2f)
}

/** El pie de una Section (13 en tinta 2). */
@Composable
fun PieBloque(texto: String) {
    Texto(Formato.sinViudas(texto), Estilo.footnote, C.texto2, Modifier.padding(horizontal = 16.dp))
}

/** Un renglón «título … valor» dentro de un Bloque (con ícono opcional y lo que vaya a la derecha). */
@Composable
fun Renglon(titulo: String, detalle: String = "", simbolo: String? = null, colorSimbolo: Color? = null, alTocar: (() -> Unit)? = null, derecha: @Composable RowScope.() -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).then(if (alTocar != null) Modifier.clickable(onClick = alTocar) else Modifier)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (simbolo != null) Box(Modifier.width(26.dp), contentAlignment = Alignment.Center) { Icono(simbolo, 18f, colorSimbolo ?: C.accion) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Texto(titulo, Estilo.body16, C.texto)
            if (detalle.isNotEmpty()) Texto(detalle, Estilo.footnote, C.texto2)
        }
        derecha()
    }
}

/** LabeledContent("Título", value: "Valor"): el título a la izquierda y el valor en tinta 2 a la derecha. */
@Composable
fun RenglonValor(titulo: String, valor: String, simbolo: String? = null) {
    Renglon(titulo, simbolo = simbolo) { Texto(valor, Estilo.body16, C.texto2, Modifier.widthIn(max = 220.dp), alinear = TextAlign.End, numeros = true) }
}

/** Un NavigationLink en un Form: el renglón con el chevron. */
@Composable
fun RenglonNavegacion(titulo: String, detalle: String = "", simbolo: String? = null, valor: String? = null, alTocar: () -> Unit) {
    Renglon(titulo, detalle, simbolo, alTocar = alTocar) {
        if (valor != null) Texto(valor, Estilo.body16, C.texto2, lineas = 1)
        Chevron()
    }
}

/** Un Button en un Form (texto en Acción, o rojo con role: .destructive). */
@Composable
fun RenglonBoton(texto: String, destructivo: Boolean = false, simbolo: String? = null, habilitado: Boolean = true, cargando: Boolean = false, alTocar: () -> Unit) {
    val color = if (destructivo) C.error else C.accion
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(enabled = habilitado && !cargando, onClick = alTocar).alpha(if (habilitado) 1f else 0.4f)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (simbolo != null) Icono(simbolo, 17f, color)
        Texto(texto, Estilo.body16, color, Modifier.weight(1f))
        if (cargando) Rueda()
    }
}

/** Un número que se cambia con − y + (Stepper con su texto). */
@Composable
fun Numerito(titulo: String, valor: Int, alCambiar: (Int) -> Unit, rango: IntRange, detalle: String = "", paso: Int = 1, unidad: (Int) -> String = { "$it" }) {
    Renglon(titulo, detalle) {
        Texto(unidad(valor), Estilo.body16, C.texto2, numeros = true)
        Pasador(valor, alCambiar, rango, paso)
    }
}

/** Toggle en un Form. */
@Composable
fun RenglonInterruptor(titulo: String, activo: Boolean, alCambiar: (Boolean) -> Unit, detalle: String = "", habilitado: Boolean = true) {
    Box(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) { Interruptor(titulo, activo, alCambiar, habilitado = habilitado, detalle = detalle.ifEmpty { null }) }
}

/** Picker en un Form (estilo menú): el título y lo elegido en Acción con ⌃⌄. */
@Composable
fun <T> RenglonSelector(titulo: String, opciones: List<Pair<T, String>>, elegido: T, alElegir: (T) -> Unit, simbolo: String? = null) {
    Renglon(titulo, simbolo = simbolo) { SelectorMenu(opciones, elegido, alElegir, titulo) }
}

/** Un TextField en un Form: el título chico arriba (si hay) y el campo con su placeholder. */
@Composable
fun RenglonCampo(
    titulo: String,
    texto: String,
    alCambiar: (String) -> Unit,
    placeholder: String = "",
    teclado: KeyboardType = KeyboardType.Text,
    multilinea: Boolean = false,
    secreto: Boolean = false,
    error: String? = null,
    habilitado: Boolean = true,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (titulo.isNotEmpty()) Texto(titulo, Estilo.footnoteFuerte, C.texto2)
        Entrada(texto, alCambiar, placeholder.ifEmpty { if (titulo.isEmpty()) "" else titulo }, Modifier.fillMaxWidth(), estilo = Estilo.body16,
            teclado = teclado, secreto = secreto, lineas = 1, lineasMaximas = if (multilinea) 8 else 1, habilitado = habilitado)
        if (!error.isNullOrEmpty()) Texto(error, Estilo.footnote, C.errorTinta)
    }
}

/** Un monto que se escribe como número (TextField con .keyboardType(.numberPad) y el prefijo de la moneda). */
@Composable
fun RenglonPesos(titulo: String, valor: Double?, alCambiar: (Double?) -> Unit, moneda: String = "$") {
    val tinta = C.texto
    val acento = C.accion
    Renglon(titulo) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Texto(moneda, Estilo.body16, C.texto2)
            Spacer(Modifier.width(4.dp))
            BasicTextField(valor?.let { if (Math.rint(it) == it) it.toLong().toString() else it.toString().replace('.', ',') } ?: "",
                { t -> alCambiar(t.filter { it.isDigit() || it == ',' }.replace(',', '.').take(14).toDoubleOrNull()) },
                Modifier.widthIn(min = 40.dp, max = 140.dp), textStyle = Estilo.body16.ts(true).copy(color = tinta, textAlign = TextAlign.End),
                singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), cursorBrush = SolidColor(acento))
        }
    }
}

/** DatePicker en un Form con un interruptor para dejarlo vacío (`fecha` null: sin fecha). */
@Composable
fun RenglonFechaOpcional(titulo: String, fecha: LocalDate?, alCambiar: (LocalDate?) -> Unit, minimo: LocalDate? = null) {
    Column {
        RenglonInterruptor(titulo, fecha != null, { alCambiar(if (it) LocalDate.now() else null) })
        if (fecha != null) RenglonFecha("Fecha", fecha, { alCambiar(it) }, minimo = minimo)
    }
}

/** El botón chico de la barra de una hoja o una pantalla con un ícono en un círculo (cerrar, más). */
@Composable
fun BotonCirculo(simbolo: String, etiqueta: String, tam: Dp = 30.dp, alTocar: () -> Unit) {
    Box(Modifier.size(tam).clip(CircleShape).background(C.segmentado).clickable(onClick = alTocar).semantics { contentDescription = etiqueta }, contentAlignment = Alignment.Center) {
        Icono(simbolo, tam.value * 0.42f, C.texto2)
    }
}
