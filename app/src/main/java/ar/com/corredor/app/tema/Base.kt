package ar.com.corredor.app.tema

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ar.com.corredor.app.R

// Lo que SwiftUI da hecho y Compose sin Material no: Text, Image(systemName:), Image de los assets, .shadow, los
// estilos de botón que se hunden (Presionable) y Reducir movimiento. Lo usan todas las piezas y pantallas.

/** El texto de la app (Text de SwiftUI). `escalaMinima`: .minimumScaleFactor (achica la letra hasta entrar). Con
 *  `escalaMinima` y más de un renglón, primero achica para entrar en uno (el «ARS 85.000» no se parte en dos) y sólo si
 *  ni achicado entra, lo parte. Nunca deja una palabra sola en el último renglón (ver [viuda]). */
@Composable
fun Texto(
    texto: String,
    estilo: Estilo = Estilo.body,
    color: Color = C.texto,
    modifier: Modifier = Modifier,
    lineas: Int = Int.MAX_VALUE,
    alinear: TextAlign = TextAlign.Start,
    numeros: Boolean = false,
    escalaMinima: Float? = null,
    interlineado: Float = 0f,
    /** .monospaced() de iOS (códigos, números de cuenta). */
    monoespaciado: Boolean = false,
    /** .tracking(n) extra en puntos (la caption en versalitas). */
    tracking: Float? = null,
    /** .truncationMode(.middle) de iOS (links largos, en un renglón). */
    cortarAlMedio: Boolean = false,
) {
    var ts = estilo.ts(numeros)
    if (interlineado > 0f) ts = ts.copy(lineHeight = (estilo.tamano * 1.2f + interlineado).sp)
    if (monoespaciado) ts = ts.copy(fontFamily = FontFamily.Monospace)
    if (tracking != null) ts = ts.copy(letterSpacing = tracking.sp)
    val estiloFinal = ts.copy(color = color, textAlign = alinear)
    val corte = if (cortarAlMedio) TextOverflow.MiddleEllipsis else TextOverflow.Ellipsis
    val achicar = escalaMinima?.let { TextAutoSize.StepBased((estilo.tamano * it).sp, estilo.tamano.sp, 0.5.sp) }
    // La viuda arreglada: el índice del espacio que se volvió duro (se recalcula si cambia el texto).
    var pegado by remember(texto) { mutableStateOf<Int?>(null) }
    val base = sinPuntoSuelto(texto)
    val mostrado = pegado?.let { base.substring(0, it) + '\u00A0' + base.substring(it + 1) } ?: base
    val alMedir: (TextLayoutResult) -> Unit = { r -> if (pegado == null && lineas > 1) viuda(r)?.let { pegado = it } }
    if (escalaMinima != null && lineas > 1) {
        val medidor = rememberTextMeasurer()
        Layout(content = {
            BasicText(mostrado, style = estiloFinal, maxLines = 1, overflow = corte, autoSize = achicar)
            BasicText(mostrado, style = estiloFinal, maxLines = lineas, overflow = corte, autoSize = achicar, onTextLayout = alMedir)
        }, modifier = modifier) { m, c ->
            val natural = medidor.measure(mostrado, estiloFinal, maxLines = 1, softWrap = false).size.width
            val p = (if (natural * escalaMinima <= c.maxWidth) m[0] else m[1]).measure(c)
            layout(p.width, p.height) { p.place(0, 0) }
        }
        return
    }
    BasicText(
        mostrado, modifier,
        style = estiloFinal,
        maxLines = lineas, overflow = corte,
        autoSize = achicar,
        onTextLayout = alMedir,
    )
}

/** El espacio de la posición `i` como espacio duro, sin perder los estilos ni los links. */
private fun duro(a: AnnotatedString, i: Int): AnnotatedString = a.subSequence(0, i) + AnnotatedString("\u00A0") + a.subSequence(i + 1, a.length)

/** «Propiedad · Venta · Palermo»: el «·» va pegado a lo de antes, para que ningún renglón empiece con un punto suelto. */
fun sinPuntoSuelto(s: String): String = if (s.contains(" · ")) s.replace(" · ", "\u00A0· ") else s

/** Si el último renglón quedó con una sola palabra (una viuda), el índice del espacio que la separa de la anterior,
 *  para volverlo duro y que bajen juntas: lo que hace `Formato.sinViudas` en iOS, pero sólo cuando pasa y con el ancho
 *  real (las dos tienen que entrar en un renglón, y en el de arriba tiene que quedar algo). */
fun viuda(r: TextLayoutResult): Int? {
    val n = r.lineCount
    if (n < 2 || r.hasVisualOverflow) return null
    val t = r.layoutInput.text.text
    val ini = r.getLineStart(n - 1)
    val fin = r.getLineEnd(n - 1, visibleEnd = true)
    if (ini < 2 || fin <= ini) return null
    val ultima = t.substring(ini, fin).trim()
    if (ultima.isEmpty() || ultima.any { it == ' ' || it == '\u00A0' }) return null
    // El renglón tiene que haber cortado en un espacio (no en un salto de párrafo ni en medio de una palabra).
    var s = ini - 1
    while (s > 0 && t[s] == ' ' && t[s - 1] == ' ') s--
    if (t[s] != ' ') return null
    // La anteúltima palabra y que en su renglón quede algo más.
    var p = s
    while (p > 0 && t[p - 1] != ' ' && t[p - 1] != '\n' && t[p - 1] != '\u00A0') p--
    if (p <= r.getLineStart(n - 2)) return null
    val anteultima = kotlin.math.abs(r.getHorizontalPosition(s, true) - r.getHorizontalPosition(p, true))
    val sola = r.getLineRight(n - 1) - r.getLineLeft(n - 1)
    return if (anteultima + sola + anteultima / maxOf(1, s - p) <= r.size.width) s else null
}

/** Texto con partes de distinto estilo (Text + Text en iOS). */
@Composable
fun TextoRico(
    texto: AnnotatedString,
    estilo: Estilo = Estilo.body,
    color: Color = C.texto,
    modifier: Modifier = Modifier,
    lineas: Int = Int.MAX_VALUE,
    alinear: TextAlign = TextAlign.Start,
    numeros: Boolean = false,
    escalaMinima: Float? = null,
    /** El color del subrayado de las partes con `TextDecoration.Underline` (el `.underline(color:)` de iOS, el dorado del
     *  resaltado de búsqueda). Sin él, el subrayado va del color del texto. */
    subrayado: Color? = null,
) {
    var pegado by remember(texto) { mutableStateOf<Int?>(null) }
    // Los espacios se vuelven duros pedazo a pedazo (subSequence + plus): cada parte conserva su estilo y sus links. Con
    // estilos de párrafo no se toca (partirlos los volvería dos párrafos).
    val arreglable = texto.paragraphStyles.isEmpty()
    val base = remember(texto) {
        if (!arreglable || !texto.text.contains(" · ")) texto else {
            var a = texto
            var i = a.text.indexOf(" · ")
            while (i >= 0) { a = duro(a, i); i = a.text.indexOf(" · ", i + 1) }
            a
        }
    }
    val mostrado = pegado?.let { duro(base, it) } ?: base
    val alMedir: (TextLayoutResult) -> Unit = { r -> if (arreglable && pegado == null && lineas > 1) viuda(r)?.let { pegado = it } }
    if (subrayado == null) {
        BasicText(mostrado, modifier, style = estilo.ts(numeros).copy(color = color, textAlign = alinear), maxLines = lineas, overflow = TextOverflow.Ellipsis,
            onTextLayout = alMedir,
            autoSize = escalaMinima?.let { TextAutoSize.StepBased((estilo.tamano * it).sp, estilo.tamano.sp, 0.5.sp) })
        return
    }
    // Las partes subrayadas se dibujan sin el subrayado del texto y con una raya propia del color pedido.
    val rangos = remember(mostrado) { mostrado.spanStyles.filter { it.item.textDecoration?.contains(TextDecoration.Underline) == true }.map { it.start until it.end } }
    val limpio = remember(mostrado) {
        AnnotatedString(mostrado.text, mostrado.spanStyles.map { r ->
            if (r.item.textDecoration?.contains(TextDecoration.Underline) == true) AnnotatedString.Range(r.item.copy(textDecoration = TextDecoration.None), r.start, r.end) else r
        }, mostrado.paragraphStyles)
    }
    var disposicion by remember { mutableStateOf<TextLayoutResult?>(null) }
    BasicText(limpio, modifier.drawBehind {
        val d = disposicion ?: return@drawBehind
        val grosor = 1.dp.toPx()
        rangos.forEach { r ->
            if (r.isEmpty() || r.first >= d.layoutInput.text.length) return@forEach
            val fin = minOf(r.last + 1, d.layoutInput.text.length)
            val l0 = d.getLineForOffset(r.first); val l1 = d.getLineForOffset(fin - 1)
            for (l in l0..l1) {
                if (l >= d.lineCount) break
                val x0 = if (l == l0) d.getHorizontalPosition(r.first, true) else d.getLineLeft(l)
                val x1 = if (l == l1) d.getHorizontalPosition(fin, true) else d.getLineRight(l)
                val y = d.getLineBaseline(l) + 2.dp.toPx()
                drawLine(subrayado, Offset(minOf(x0, x1), y), Offset(maxOf(x0, x1), y), grosor)
            }
        }
    }, style = estilo.ts(numeros).copy(color = color, textAlign = alinear), maxLines = lineas, overflow = TextOverflow.Ellipsis,
        onTextLayout = { disposicion = it; alMedir(it) },
        autoSize = escalaMinima?.let { TextAutoSize.StepBased((estilo.tamano * it).sp, estilo.tamano.sp, 0.5.sp) })
}

private val simbolosContorno = FontFamily(Font(R.font.simbolos_contorno))
private val simbolosRelleno = FontFamily(Font(R.font.simbolos_relleno))

/** Un ícono por su nombre de SF Symbol (Image(systemName:) en iOS). «.fill» elige la variante rellena. `tam` es el
 *  .font(.system(size:)) de iOS. */
@Composable
fun Icono(simbolo: String, tam: Float = 17f, color: Color = C.texto, modifier: Modifier = Modifier, descripcion: String? = null, peso: Peso = Peso.regular) {
    val partes = simbolo.split('.')
    val relleno = "fill" in partes
    val nombre = partes.filter { it != "fill" }.joinToString(".")
    val c = Simbolos.mapa[nombre] ?: Simbolos.mapa[simbolo] ?: Simbolos.mapa[nombre.substringBeforeLast('.')] ?: ' '
    // Los símbolos de Material son un poco más chicos que los de SF a igual tamaño de letra.
    val base = TextStyle(fontFamily = if (relleno) simbolosRelleno else simbolosContorno, fontSize = (tam * 1.18f).sp, color = color, lineHeight = (tam * 1.18f).sp)
    val m = modifier.then(if (descripcion != null) Modifier.semantics { contentDescription = descripcion } else Modifier)
    // La letra de los símbolos tiene un solo peso: el `.font(.system(size:, weight:))` de iOS se hace con un trazo encima
    // del mismo color, más grueso cuanto más pesado (light queda como regular: la letra no tiene un trazo más fino).
    val trazo = when (peso) { Peso.light, Peso.regular -> 0f; Peso.medium -> 0.035f; Peso.semibold -> 0.05f; Peso.bold -> 0.07f; Peso.extrabold -> 0.09f }
    if (trazo == 0f) { BasicText(c.toString(), m, style = base); return }
    androidx.compose.foundation.layout.Box(m) {
        BasicText(c.toString(), style = base)
        BasicText(c.toString(), style = base.copy(drawStyle = Stroke(width = with(LocalDensity.current) { (tam * trazo).dp.toPx() }, join = StrokeJoin.Round)))
    }
}

/** Una imagen de la app (Image("Simbolo") en iOS): los drawables que vienen de Assets.xcassets. */
@Composable
fun Imagen(id: Int, modifier: Modifier = Modifier, recorte: ContentScale = ContentScale.Fit, descripcion: String? = null) {
    Image(painterResource(id), descripcion, modifier, contentScale = recorte)
}

/** Las imágenes de Assets.xcassets por su nombre de iOS (Image("LogoWhatsApp")). */
object Imagenes {
    val grano = R.drawable.grano
    val granoClaro = R.drawable.grano_claro
    val iconoRedondo = R.drawable.icono_redondo
    val lanzamiento = R.drawable.lanzamiento
    val logoGoogle = R.drawable.logo_google
    val logoMercadoLibre = R.drawable.logo_mercadolibre
    val logoWhatsApp = R.drawable.logo_whatsapp
    val patron = R.drawable.patron
    val simbolo = R.drawable.simbolo

    /** Image("Nombre") de iOS → el drawable (0 si no está). */
    fun de(nombre: String): Int = when (nombre) {
        "Grano" -> grano
        "GranoClaro" -> granoClaro
        "IconoRedondo" -> iconoRedondo
        "Lanzamiento" -> lanzamiento
        "LogoGoogle" -> logoGoogle
        "LogoMercadoLibre" -> logoMercadoLibre
        "LogoWhatsApp" -> logoWhatsApp
        "Patron" -> patron
        "Simbolo" -> simbolo
        else -> 0
    }
}

/** Sombra como la de SwiftUI (.shadow(color:radius:x:y:)), dibujada detrás de la forma. */
fun Modifier.sombra(forma: Shape, color: Color, radio: Dp, x: Dp = 0.dp, y: Dp = 0.dp): Modifier = drawBehind {
    if (color.alpha == 0f) return@drawBehind
    val contorno = forma.createOutline(size, layoutDirection, this)
    drawIntoCanvas { canvas ->
        val paint = Paint()
        val fp = paint.asFrameworkPaint()
        fp.isAntiAlias = true
        fp.color = android.graphics.Color.TRANSPARENT
        fp.setShadowLayer(radio.toPx().coerceAtLeast(0.5f), x.toPx(), y.toPx(), color.toArgb())
        canvas.drawOutline(contorno, paint)
    }
}

/** Un botón de SwiftUI con su estilo: `contenido` recibe si está apretado; se hunde (Presionable: 0,97 y 85 %) y se
 *  apaga si no está habilitado. */
@Composable
fun Tocable(
    alTocar: () -> Unit,
    modifier: Modifier = Modifier,
    habilitado: Boolean = true,
    hundir: Boolean = true,
    descripcion: String? = null,
    contenido: @Composable (apretado: Boolean) -> Unit,
) {
    val fuente = remember { MutableInteractionSource() }
    val apretado by fuente.collectIsPressedAsState()
    val escala by animateFloatAsState(if (apretado && hundir) 0.97f else 1f, spring(dampingRatio = 0.6f, stiffness = 600f), label = "hundir")
    androidx.compose.foundation.layout.Box(
        modifier.scale(escala).alpha(if (apretado && hundir) 0.85f else 1f)
            .clickable(fuente, null, enabled = habilitado, role = Role.Button, onClick = alTocar)
            .then(if (descripcion != null) Modifier.semantics { contentDescription = descripcion } else Modifier),
    ) { contenido(apretado) }
}

/** Si el sistema tiene las animaciones prendidas (accessibilityReduceMotion en iOS, al revés). */
@Composable
fun animacionesActivas(): Boolean {
    val vista = LocalView.current
    return remember(vista) {
        android.provider.Settings.Global.getFloat(vista.context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
    }
}

/** .disabled de SwiftUI sobre un bloque entero: se apaga y se traga los toques (sólo alpha no alcanza). */
fun Modifier.deshabilitado(si: Boolean, alfa: Float = 0.5f): Modifier = if (!si) this else this.alpha(alfa).pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
    }
}
