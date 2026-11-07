package ar.com.corredor.app.tema

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ar.com.corredor.app.nucleo.Formato
import coil3.compose.AsyncImage
import java.lang.ref.WeakReference

// Clon de Tema/Piezas.swift: las piezas del handoff, para armar cada pantalla (y lo que el handoff no dibuja) con lo
// mismo. Más lo que en iOS da el sistema (TextField, Toggle suelto, Spacer).

val Capsula = RoundedCornerShape(50)
fun redondeado(r: Dp) = RoundedCornerShape(r)

// MARK: - Tarjetas y filas

/** .tarjeta(radio:relleno:) de Piezas: fondo `surface`, sin sombra, anillo de 1 pt `card-ring`. (La de siempre,
 *  `.tarjeta()`, es la de Tokens: superficie, radio 18 y e2.) */
@Composable
fun Modifier.tarjeta(radio: Dp = 20.dp, relleno: Dp): Modifier {
    val forma = RoundedCornerShape(radio)
    return this.clip(forma).background(C.superficie).border(1.dp, C.anillo, forma).padding(relleno)
}

/** Margen horizontal de la pantalla (20, o 16 en SE). */
@Composable
fun Modifier.margenPantalla(): Modifier = this.padding(horizontal = margen)

/** Separador de fila con inset donde arranca el texto. */
@Composable
fun Separador(inset: Dp = 16.dp) {
    Box(Modifier.fillMaxWidth().padding(start = inset).height(1.dp).background(C.linea))
}

/** El chevron de fila (9 × 15). */
@Composable
fun Chevron(ancho: Float = 9f) {
    Icono("chevron.right", ancho * 1.45f, C.chevron)
}

/** Label de sección (14/600, `text-2`). */
@Composable
fun EtiquetaSeccion(texto: String, modifier: Modifier = Modifier) {
    Texto(texto, Estilo.label, C.texto2, modifier.fillMaxWidth().semantics { heading() })
}

/** Título de sección (21/700) con acción opcional a la derecha. */
@Composable
fun TituloSeccion(texto: String, accion: String? = null, alTocar: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth()) {
        Texto(texto, Estilo.tituloSeccion, C.texto, Modifier.weight(1f).alignByBaseline().semantics { heading() })
        if (accion != null && alTocar != null) {
            Texto(accion, Estilo.subheadFuerte, C.primarioTexto, Modifier.alignByBaseline().clickable(onClick = alTocar))
        }
    }
}

/** Encabezado de las pantallas raíz: Large Title 34 y un botón redondo de 40 a la derecha. */
@Composable
fun EncabezadoGrande(titulo: String, derecha: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Texto(titulo, Estilo.tituloGrande, C.texto, Modifier.weight(1f).semantics { heading() })
        Row(Modifier.padding(bottom = 2.dp), verticalAlignment = Alignment.CenterVertically, content = derecha)
    }
}

/** Botón redondo de 40 (campana, filtro, escanear) en blanco con anillo. */
@Composable
fun BotonRedondo(simbolo: String, etiqueta: String, punto: Boolean = false, accion: () -> Unit) {
    Tocable(accion, Modifier.size(44.dp), descripcion = etiqueta) {
        Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(C.superficie).border(1.dp, C.linea, CircleShape), contentAlignment = Alignment.Center) {
                Icono(simbolo, 18f, C.texto)
            }
            if (punto) {
                val sup = C.superficie
                Box(Modifier.align(Alignment.TopEnd).offset(x = (-7).dp, y = 6.dp).size(13.dp).clip(CircleShape).background(sup).padding(2.dp).clip(CircleShape).background(C.error))
            }
        }
    }
}

// MARK: - Botones

/** Botón principal: Acción (verde; oro en oscuro) con SobreAccion, 52 de alto, radio 12 continuo. */
@Composable
fun BotonPrincipal(
    texto: String,
    alTocar: () -> Unit,
    modifier: Modifier = Modifier,
    alto: Dp = 52.dp,
    habilitado: Boolean = true,
    lleno: Boolean = true,
    simbolo: String? = null,
    /** ProgressView() en lugar del texto. */
    cargando: Boolean = false,
    contenido: (@Composable RowScope.() -> Unit)? = null,
) {
    val forma = RoundedCornerShape(M.rControl)
    val accion = C.accion
    val tinta = C.sobreAccion
    Tocable(alTocar, modifier, habilitado, hundir = false) { apretado ->
        Row(
            Modifier.alpha(if (habilitado) (if (apretado) 0.85f else 1f) else 0.5f)
                .sombra(forma, C.sombraPrimario, 8.dp, y = 6.dp)
                .then(if (lleno) Modifier.fillMaxWidth() else Modifier).heightIn(min = alto)
                .clip(forma).background(accion).padding(horizontal = 22.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically,
        ) {
            if (cargando) Rueda(tinta) else if (contenido != null) contenido() else {
                if (simbolo != null) Icono(simbolo, 17f, tinta)
                Texto(texto, Estilo.headline, tinta, lineas = 1)
            }
        }
    }
}

/** Botón de texto (`primary-text`, 17/600). */
@Composable
fun BotonTexto(texto: String, alTocar: () -> Unit, modifier: Modifier = Modifier, alto: Dp = 52.dp, color: Color = C.primarioTexto, habilitado: Boolean = true, simbolo: String? = null) {
    Tocable(alTocar, modifier, habilitado, hundir = false) { apretado ->
        Row(
            Modifier.fillMaxWidth().heightIn(min = alto).alpha(if (!habilitado) 0.5f else if (apretado) 0.6f else 1f).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically,
        ) {
            if (simbolo != null) Icono(simbolo, 16f, color)
            Texto(texto, Estilo.headline, color, alinear = TextAlign.Center)
        }
    }
}

/** Botón tonal (`cream` con `primary-ink`). */
@Composable
fun BotonTonal(texto: String, alTocar: () -> Unit, modifier: Modifier = Modifier, alto: Dp = 34.dp, simbolo: String? = null, habilitado: Boolean = true) {
    Tocable(alTocar, modifier, habilitado, hundir = false) { apretado ->
        Row(
            Modifier.heightIn(min = alto).alpha(if (!habilitado) 0.5f else if (apretado) 0.7f else 1f).clip(RoundedCornerShape(10.dp)).background(C.crema).padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically,
        ) {
            if (simbolo != null) Icono(simbolo, 13f, C.primarioTinta)
            Texto(texto, Estilo(14f, Peso.bold, relativo = Relativo.footnote), C.primarioTinta, lineas = 1)
        }
    }
}

/** Botón secundario: blanco con borde `line-2` y texto `primary-text`. */
@Composable
fun BotonSecundario(texto: String, alTocar: () -> Unit, modifier: Modifier = Modifier, alto: Dp = 46.dp, simbolo: String? = null, habilitado: Boolean = true, lleno: Boolean = false) {
    val forma = RoundedCornerShape(M.rControl)
    Tocable(alTocar, modifier, habilitado, hundir = false) { apretado ->
        Row(
            Modifier.then(if (lleno) Modifier.fillMaxWidth() else Modifier).heightIn(min = alto).alpha(if (!habilitado) 0.5f else if (apretado) 0.7f else 1f)
                .clip(forma).background(C.superficie).border(1.dp, C.linea2, forma).padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically,
        ) {
            if (simbolo != null) Icono(simbolo, 16f, C.primarioTexto)
            Texto(texto, Estilo.fila, C.primarioTexto, lineas = 1)
        }
    }
}

/** Botón destructivo (`error-bg` con `error-ink`, 700). */
@Composable
fun BotonDestructivo(texto: String, alTocar: () -> Unit, modifier: Modifier = Modifier, habilitado: Boolean = true, simbolo: String? = null, cargando: Boolean = false) {
    Tocable(alTocar, modifier, habilitado, hundir = false) { apretado ->
        Row(
            Modifier.fillMaxWidth().heightIn(min = 52.dp).alpha(if (!habilitado) 0.5f else if (apretado) 0.7f else 1f).clip(RoundedCornerShape(M.rControl)).background(C.errorFondo),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically,
        ) {
            if (cargando) Rueda(C.errorTinta) else if (simbolo != null) Icono(simbolo, 17f, C.errorTinta)
            Texto(texto, Estilo(17f, Peso.bold, relativo = Relativo.headline), C.errorTinta, lineas = 1)
        }
    }
}

/** Acción rápida en `cream-2` (Llamar, WhatsApp, Copiar mail; Ver web, Compartir, QR). */
@Composable
fun AccionRapida(simbolo: String, texto: String, modifier: Modifier = Modifier, alto: Dp = 58.dp, accion: () -> Unit) {
    Tocable(accion, modifier) {
        Column(
            Modifier.fillMaxWidth().heightIn(min = alto).clip(RoundedCornerShape(14.dp)).background(C.crema2),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
        ) {
            Icono(simbolo, 18f, C.primarioTexto)
            Texto(texto, Estilo.footnoteFuerte, C.primarioTinta, lineas = 1, escalaMinima = 0.8f)
        }
    }
}

// MARK: - Chips, etiquetas, insignias

/** Insignia numérica (fondo `error`). */
@Composable
fun Insignia(n: Int) {
    Box(Modifier.defaultMinSize(22.dp, 22.dp).clip(Capsula).background(C.error).padding(horizontal = 7.dp), contentAlignment = Alignment.Center) {
        Texto(if (n > 99) "99+" else "$n", Estilo(13f, Peso.bold, relativo = Relativo.footnote), Color.White, numeros = true)
    }
}

// MARK: - Segmentado y buscador

/** Control segmentado (36 de alto, radio 11/8): Picker(.segmented) de iOS. */
@Composable
fun <V> Segmentado(opciones: List<Pair<V, String>>, elegido: V, alElegir: (V) -> Unit, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxWidth().height(36.dp).clip(RoundedCornerShape(11.dp)).background(C.segmentado).padding(3.dp)) {
        val n = opciones.size.coerceAtLeast(1)
        val ancho = maxWidth / n
        val i = opciones.indexOfFirst { it.first == elegido }.coerceAtLeast(0)
        val x by animateDpAsState(ancho * i, tween(220), label = "segmento")
        if (opciones.any { it.first == elegido }) {
            Box(Modifier.offset(x = x).width(ancho).fillMaxHeight().sombra(RoundedCornerShape(8.dp), C.verde800.copy(alpha = 0.12f), 1.5.dp, y = 1.dp)
                .clip(RoundedCornerShape(8.dp)).background(C.segmentoElegido))
        }
        Row(Modifier.fillMaxSize()) {
            opciones.forEach { (v, t) ->
                val activo = v == elegido
                Box(
                    Modifier.weight(1f).fillMaxHeight().clickable(remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, null) {
                        alElegir(v); Haptico.seleccion()
                    }.semantics { selected = activo },
                    contentAlignment = Alignment.Center,
                ) { Texto(t, Estilo.label, if (activo) C.texto else C.texto2, lineas = 1) }
            }
        }
    }
}

/** Buscador (40 de alto, radio 12). */
@Composable
fun Buscador(placeholder: String, texto: String, alCambiar: (String) -> Unit, modifier: Modifier = Modifier, alBuscar: () -> Unit = {}) {
    Row(
        modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(12.dp)).background(C.segmentado).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icono("magnifyingglass", 15f, C.texto3)
        Box(Modifier.weight(1f)) {
            Entrada(texto, alCambiar, placeholder, estilo = Estilo.body16, accionTeclado = ImeAction.Search, alEnviar = alBuscar, sinCorrector = true)
        }
        if (texto.isNotEmpty()) {
            Box(Modifier.clickable { alCambiar("") }.semantics { contentDescription = "Borrar la búsqueda" }) { Icono("xmark.circle.fill", 17f, C.texto3) }
        }
    }
}

/** Un TextField de SwiftUI: el texto, el placeholder (prompt) en `text-3` y el teclado. Va adentro de Campo, Buscador,
 *  un renglón de un Bloque, etc. `lineas`/`lineasMaximas`: el `axis: .vertical` con `.lineLimit(a...b)`. `secreto`:
 *  SecureField. */
@Composable
fun Entrada(
    texto: String,
    alCambiar: (String) -> Unit,
    placeholder: String = "",
    modifier: Modifier = Modifier,
    estilo: Estilo = Estilo.body,
    color: Color = C.texto,
    teclado: KeyboardType = KeyboardType.Text,
    secreto: Boolean = false,
    accionTeclado: ImeAction = ImeAction.Default,
    alEnviar: () -> Unit = {},
    sinCorrector: Boolean = false,
    mayusculas: KeyboardCapitalization = KeyboardCapitalization.Sentences,
    lineas: Int = 1,
    lineasMaximas: Int = lineas,
    alinear: TextAlign = TextAlign.Start,
    numeros: Boolean = false,
    alEnfocar: (Boolean) -> Unit = {},
    habilitado: Boolean = true,
    /** .font(.system(design: .monospaced)). */
    monoespaciado: Boolean = false,
) {
    Box(modifier, contentAlignment = Alignment.CenterStart) {
        if (texto.isEmpty() && placeholder.isNotEmpty()) Texto(placeholder, estilo, C.texto3, Modifier.fillMaxWidth(), lineas = lineasMaximas, alinear = alinear)
        BasicTextField(
            texto, alCambiar, Modifier.fillMaxWidth().onFocusChanged { alEnfocar(it.isFocused) },
            enabled = habilitado,
            textStyle = estilo.ts(numeros).copy(color = color, textAlign = alinear).let { if (monoespaciado) it.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace) else it },
            singleLine = lineasMaximas == 1, minLines = lineas, maxLines = lineasMaximas,
            visualTransformation = if (secreto) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(
                capitalization = if (teclado == KeyboardType.Email || teclado == KeyboardType.Uri || secreto) KeyboardCapitalization.None else mayusculas,
                autoCorrectEnabled = !(sinCorrector || secreto || teclado == KeyboardType.Email),
                keyboardType = teclado, imeAction = accionTeclado,
            ),
            keyboardActions = KeyboardActions(onAny = { alEnviar() }),
            cursorBrush = SolidColor(C.accion),
        )
    }
}

/** Campo de formulario (52 de alto, radio 14) con label 13/600 arriba. */
@Composable
fun Campo(label: String, error: Boolean = false, enfocado: Boolean = false, modifier: Modifier = Modifier, alto: Dp = 52.dp, contenido: @Composable RowScope.() -> Unit) {
    val forma = RoundedCornerShape(14.dp)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Texto(label, Estilo.footnoteFuerte, C.texto2)
        Row(
            Modifier.fillMaxWidth().height(alto).clip(forma).background(C.superficie)
                .border(if (error || enfocado) 1.5.dp else 1.dp, if (error) C.error else if (enfocado) C.acento else C.linea2, forma)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically, content = contenido,
        )
    }
}

/** Mensaje de error de campo, con el círculo «!» de 17. */
@Composable
fun MensajeError(texto: String) {
    Row(Modifier.semantics(mergeDescendants = true) {}, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.padding(top = 1.dp).size(17.dp).clip(CircleShape).background(C.error), contentAlignment = Alignment.Center) {
            Texto("!", Estilo(12f, Peso.bold, relativo = Relativo.caption), Color.White)
        }
        Texto(Formato.sinViudas(texto), Estilo.texto14, C.errorTinta)
    }
}

// MARK: - Miniaturas y avatares

/** El rayado diagonal de los placeholders de fotos. */
@Composable
fun Rayado(modifier: Modifier = Modifier, paso: Float = 5f) {
    val r1 = C.rayado1; val r2 = C.rayado2
    val densidad = LocalDensity.current.density
    Canvas(modifier) {
        drawRect(r2)
        val p = paso * densidad
        val diag = size.width + size.height
        var x = -size.height
        while (x < diag) {
            val path = Path().apply {
                moveTo(x, 0f); lineTo(x + p, 0f); lineTo(x + p + size.height, size.height); lineTo(x + size.height, size.height); close()
            }
            drawPath(path, r1)
            x += p * 2 * 1.4142f
        }
    }
}

/** La URL de una foto de la API: https, o el id de Unsplash de la demo. */
fun urlDeFoto(f: String?, ancho: Int = 300): String? {
    if (f.isNullOrEmpty()) return null
    if (f.startsWith("https://")) return f
    if (Regex("^[\\w-]+$").matches(f)) return "https://images.unsplash.com/photo-$f?auto=format&fit=crop&w=$ancho&q=70"
    return null
}

/** Miniatura de una foto: la foto, o el rayado mientras carga o si no tiene. */
@Composable
fun Miniatura(foto: String?, tam: Dp = 48.dp, alto: Dp? = null, radio: Dp = 12.dp, modifier: Modifier = Modifier) {
    Box(modifier.size(tam, alto ?: tam).clip(RoundedCornerShape(radio))) {
        Rayado(Modifier.matchParentSize())
        urlDeFoto(foto, (tam.value * 3).toInt())?.let { AsyncImage(it, null, Modifier.matchParentSize(), contentScale = ContentScale.Crop) }
    }
}

/** Avatar de iniciales (inmobiliaria: `cream` + `primary-text`; contacto: verde). */
@Composable
fun AvatarIniciales(nombre: String, tam: Dp = 40.dp, radio: Dp? = null, fondo: Color = C.crema, tinta: Color = C.primarioTexto) {
    Box(Modifier.size(tam).clip(if (radio != null) RoundedCornerShape(radio) else CircleShape).background(fondo), contentAlignment = Alignment.Center) {
        Texto(Formato.iniciales(nombre), Estilo(tam.value * 0.35f, Peso.bold), tinta)
    }
}

/** Colores del avatar de una inmobiliaria o un contacto, estables por nombre (crema, verde, azul, violeta). */
@Composable
fun coloresAvatar(nombre: String): Pair<Color, Color> {
    val paleta = listOf(C.crema to C.primarioTexto, C.okFondo to C.okTinta, C.azulFondo to C.azulTinta, C.violetaFondo to C.violetaTinta)
    var h = 0
    nombre.codePoints().forEach { h = (h * 31 + it) and 0xFFFF }
    return paleta[h % paleta.size]
}

// MARK: - Hojas

/** Grabber de hoja (36 × 5). */
@Composable
fun Grabber() {
    Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(36.dp, 5.dp).clip(Capsula).background(C.linea2))
    }
}

// MARK: - Interruptor

/** Toggle de 51 × 31 en `ok`, con su texto. Ocupa todo el ancho: para el Toggle sin texto (.labelsHidden()), `Llave`. */
@Composable
fun Interruptor(texto: String, prendido: Boolean, alCambiar: (Boolean) -> Unit, modifier: Modifier = Modifier, habilitado: Boolean = true, detalle: String? = null) {
    Row(modifier.fillMaxWidth().heightIn(min = 44.dp).clickable(enabled = habilitado) { alCambiar(!prendido) }.alpha(if (habilitado) 1f else 0.5f),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Texto(texto, Estilo.body16, C.texto)
            if (!detalle.isNullOrEmpty()) Texto(detalle, Estilo.footnote, C.texto2)
        }
        Llave(prendido, alCambiar, habilitado = habilitado)
    }
}

/** El interruptor suelto (Toggle con .labelsHidden()). */
@Composable
fun Llave(prendido: Boolean, alCambiar: (Boolean) -> Unit, color: Color = C.ok, habilitado: Boolean = true, descripcion: String? = null) {
    val x by animateDpAsState(if (prendido) 20.dp else 0.dp, tween(180), label = "llave")
    // Como el Toggle de iOS: el lector dice el nombre (el label escondido) y si está prendido.
    val lector = Modifier.semantics {
        role = androidx.compose.ui.semantics.Role.Switch
        toggleableState = androidx.compose.ui.state.ToggleableState(prendido)
        if (descripcion != null) contentDescription = descripcion
    }
    Box(Modifier.size(51.dp, 31.dp).then(lector).clip(Capsula).background(if (prendido) color else C.linea2).clickable(enabled = habilitado) { alCambiar(!prendido); Haptico.seleccion() }.padding(2.dp)) {
        Box(Modifier.offset(x = x).size(27.dp).sombra(CircleShape, Color.Black.copy(alpha = 0.15f), 4.dp, y = 2.dp).clip(CircleShape).background(Color.White))
    }
}

// MARK: - Hápticos

/** Los hápticos de iOS con su equivalente de Android. La vista se fija una vez en MainActivity. */
object Haptico {
    internal var vista: WeakReference<View>? = null
    private fun h(c: Int) { vista?.get()?.performHapticFeedback(c) }
    fun exito() = h(if (android.os.Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY)
    fun error() = h(if (android.os.Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS)
    fun impacto() = h(HapticFeedbackConstants.VIRTUAL_KEY)
    fun seleccion() = h(if (android.os.Build.VERSION.SDK_INT >= 34) HapticFeedbackConstants.SEGMENT_TICK else HapticFeedbackConstants.CLOCK_TICK)
}

// MARK: - Barra de acción

/** La acción principal de una pantalla (Guardar, Publicar…) fija abajo, siempre a la vista (.barraAccion en iOS):
 *  el contenido se corre para arriba y al bajar del todo no queda tapado. `contenido` recibe el alto de la barra (para
 *  dejarlo libre al final de la lista). */
@Composable
fun ConBarraAccion(
    botones: @Composable ColumnScope.() -> Unit,
    modifier: Modifier = Modifier,
    contenido: @Composable BoxScope.(abajo: Dp) -> Unit,
) {
    var alto by remember { mutableIntStateOf(0) }
    val densidad = LocalDensity.current
    val fondo = C.fondo
    Box(modifier.fillMaxSize()) {
        contenido(with(densidad) { alto.toDp() })
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().onSizeChanged { alto = it.height }
                .background(Brush.verticalGradient(0f to fondo.copy(alpha = 0f), 0.3f to fondo))
                .navigationBarsPadding().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp), content = botones,
        )
    }
}

/** Espacio que empuja (Spacer() de SwiftUI dentro de un HStack o VStack). */
@Composable
fun RowScope.Empujar(min: Dp = 0.dp) = Spacer(Modifier.weight(1f).width(min))

@Composable
fun ColumnScope.Empujar(min: Dp = 0.dp) = Spacer(Modifier.weight(1f).height(min))

/** .padding(.horizontal, -margen): una fila que se sale del margen de la pantalla hasta los bordes (los carruseles). */
@Composable
fun Modifier.sinMargen(): Modifier = this.sangrar(margen)

/** .padding(.horizontal, -n): se estira `n` de cada lado, más allá de lo que le da el padre. */
fun Modifier.sangrar(n: Dp): Modifier = this.layout { medible, restricciones ->
    val extra = n.roundToPx() * 2
    val ancho = restricciones.maxWidth + extra
    val p = medible.measure(restricciones.copy(minWidth = ancho, maxWidth = ancho))
    layout(restricciones.maxWidth, p.height) { p.place(-extra / 2, 0) }
}
