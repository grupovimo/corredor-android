package ar.com.corredor.app.tema

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ar.com.corredor.app.app.LocalEstado
import ar.com.corredor.app.nucleo.APerfil
import ar.com.corredor.app.nucleo.Fecha
import ar.com.corredor.app.nucleo.Formato
import coil3.compose.SubcomposeAsyncImage
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs

/** Una acción de ícono de la cabecera (buscar, avisos, nueva propiedad…), con insignia opcional. */
data class AccionCabecera(val icono: String, val etiqueta: String, val insignia: Int? = null, val hacer: () -> Unit) {
    val id: String get() = etiqueta
}

/** La cabecera grande de una pestaña (handoff · `h.grande`): fila de 44 con las acciones y el avatar con aro oro, el
 *  «sobre» en versalitas 13/600, el título 34/800 y, si hay, la caja de búsqueda. `logo`: el de la inmobiliaria (su
 *  marca de Mi web), a la izquierda del renglón de arriba. */
@Composable
fun CabeceraPestana(
    titulo: String,
    sobre: String? = null,
    acciones: List<AccionCabecera> = emptyList(),
    avatar: Boolean = false,
    busca: String? = null,
    alBuscar: (() -> Unit)? = null,
    logo: String? = null,
) {
    val estado = LocalEstado.current
    @Composable
    fun botones() {
        Row(Modifier.offset(x = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            acciones.forEach { a ->
                Tocable(a.hacer, Modifier.size(44.dp), descripcion = a.etiqueta) {
                    Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                        Icono(a.icono, 21f, C.accion)
                        val n = a.insignia
                        if (n != null && n > 0) Box(Modifier.align(Alignment.TopEnd).offset(x = (-3).dp, y = 3.dp)) { InsigniaRoja(n) }
                    }
                }
            }
            if (avatar) {
                Tocable({ estado.abrir(APerfil()) }, Modifier.size(44.dp), descripcion = "Tu perfil") {
                    Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                        Box(Modifier.size(41.dp).clip(CircleShape).background(C.oro).padding(1.5.dp).clip(CircleShape).background(C.fondo).padding(2.dp)
                            .clip(CircleShape).background(C.verde700), contentAlignment = Alignment.Center) {
                            Texto(Formato.iniciales(estado.usuario?.completo ?: "C"), Estilo(13f, Peso.bold, relativo = Relativo.caption), C.oro)
                        }
                    }
                }
            }
        }
    }

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 6.dp)) {
        // Con renglón de arriba (fecha, inmobiliaria), los botones van ahí; sin él, en el renglón del título.
        if (sobre != null) {
            Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) { Spacer(Modifier.weight(1f)); botones() }
        } else {
            Spacer(Modifier.height(12.dp))
        }
        if (sobre != null) {
            Row(Modifier.padding(bottom = if (logo == null) 0.dp else 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                if (logo != null) {
                    val forma = RoundedCornerShape(9.dp)
                    SubcomposeAsyncImage(logo, null, Modifier.size(34.dp).clip(forma).background(C.superficie).border(1.dp, C.linea, forma),
                        contentScale = ContentScale.Fit, loading = { Box(Modifier.fillMaxSize().background(C.hundida)) })
                }
                Texto(sobre.uppercase(Formato.argentina), Estilo(13f, Peso.semibold, relativo = Relativo.footnote), C.texto2, lineas = 1, tracking = 13f * 0.04f)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Texto(titulo, Estilo.tituloGrande, C.texto, Modifier.weight(1f, fill = sobre == null).semantics { heading() }, lineas = 1, escalaMinima = 0.7f)
            if (sobre == null) botones()
        }
        if (busca != null) {
            Tocable({ alBuscar?.invoke() }, Modifier.padding(top = 10.dp)) {
                Row(Modifier.fillMaxWidth().height(38.dp).clip(RoundedCornerShape(11.dp)).background(C.hundida).padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icono("magnifyingglass", 17f, C.texto3)
                    Texto(busca, Estilo.body, C.texto3, Modifier.weight(1f), lineas = 1)
                    Icono("mic", 17f, C.texto3)
                }
            }
        }
    }
}

/** La insignia roja de las cabeceras y la barra (#e5484d, 18 de alto). */
@Composable
fun InsigniaRoja(n: Int) {
    Box(Modifier.defaultMinSize(18.dp, 18.dp).clip(Capsula).background(hex(0xE5484D)).padding(horizontal = 5.dp), contentAlignment = Alignment.Center) {
        Texto(if (n > 99) "99+" else "$n", Estilo(11f, Peso.bold, relativo = Relativo.caption2), Color.White, numeros = true)
    }
}

/** El título de un bloque («Para hacer ahora», con la cuenta a la derecha): 20/700 y 15 en tinta 2. */
@Composable
fun TituloBloque(texto: String, cuenta: String? = null) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp).semantics { heading() }, verticalAlignment = Alignment.Bottom) {
        Texto(texto, Estilo.titulo3, C.texto, Modifier.weight(1f))
        if (cuenta != null) Texto(cuenta, Estilo.subhead, C.texto2)
    }
}

/** Los tonos del handoff (g, o, b, v, r y neutro): fondo y tinta. */
enum class Tono {
    verde, oro, azul, violeta, rojo, neutro;

    val fondo: Color @Composable get() = when (this) {
        verde -> C.okFondo
        oro -> C.avisoFondo
        azul -> C.azulFondo
        violeta -> C.violetaFondo
        rojo -> C.errorFondo
        neutro -> C.hundida
    }

    val tinta: Color @Composable get() = when (this) {
        verde -> C.okTinta
        oro -> C.avisoTinta
        azul -> C.azulTinta
        violeta -> C.violetaTinta
        rojo -> C.errorTinta
        neutro -> C.texto2
    }
}

/** El ícono de una fila: 40×40, radio 12, en el tono. */
@Composable
fun IconoTono(icono: String, tono: Tono, tam: Dp = 40.dp) {
    Box(Modifier.size(tam).clip(RoundedCornerShape(12.dp)).background(tono.fondo), contentAlignment = Alignment.Center) {
        Icono(icono, tam.value * 0.5f, tono.tinta)
    }
}

/** Cuántas pantallas se mostraron: las pruebas lo usan para verificar que cada destino abre su pantalla. */
object PantallasMostradas {
    var cuenta = 0
}

/** El fondo de la app: `Fondo` con el grano en mosaico (multiplicado en claro, al 55 % en oscuro). Va detrás de toda la
 *  pantalla (.background(FondoApp()) en iOS). */
@Composable
fun FondoApp(modifier: Modifier = Modifier) {
    val oscuro = LocalOscuro.current
    val ctx = LocalContext.current
    val densidad = LocalDensity.current.density
    val fondo = C.fondo
    val grano = remember(oscuro) {
        runCatching { BitmapFactory.decodeResource(ctx.resources, if (oscuro) Imagenes.granoClaro else Imagenes.grano) }.getOrNull()
    }
    val pintura = remember(grano, densidad) {
        grano?.let { b ->
            android.graphics.Paint().apply {
                shader = BitmapShader(b, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT).apply {
                    // El @3x de iOS: 660 px son 220 pt.
                    setLocalMatrix(android.graphics.Matrix().apply { setScale(densidad / 3f, densidad / 3f) })
                }
                if (oscuro) alpha = (0.55f * 255).toInt() else xfermode = PorterDuffXfermode(PorterDuff.Mode.MULTIPLY)
            }
        }
    }
    LaunchedEffect(Unit) { PantallasMostradas.cuenta += 1 }
    Box(modifier.fillMaxSize().background(fondo).drawBehind {
        val p = pintura ?: return@drawBehind
        drawIntoCanvas { it.nativeCanvas.drawRect(0f, 0f, size.width, size.height, p) }
    })
}

/** «ARS 540.000», «USD 172.000» (sin decimales si es entero). */
fun Formato.plata(n: Double, moneda: String = "ARS"): String {
    val s = DecimalFormatSymbols(argentina)
    val f = DecimalFormat(if (Math.rint(n) == n) "#,##0" else "#,##0.##", s)
    return "$moneda ${f.format(n)}"
}

fun Formato.plata(n: Int, moneda: String = "ARS"): String = plata(n.toDouble(), moneda)

/** «ARS 1,84 M» para las cifras del dueño. */
fun Formato.plataCorta(n: Double, moneda: String = "ARS"): String {
    if (abs(n) >= 1_000_000) {
        val f = DecimalFormat("#,##0.##", DecimalFormatSymbols(argentina))
        return "$moneda ${f.format(n / 1_000_000)} M"
    }
    return plata(n, moneda)
}

/** «jueves 12 de noviembre» (en la hora del teléfono). */
fun Formato.diaCompleto(f: Instant): String =
    DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", argentina).withZone(ZoneId.systemDefault()).format(f)

/** «yyyy-MM-dd» en la hora del teléfono (lo que piden los filtros de la API). */
fun Formato.diaISO(f: Instant): String = f.atZone(ZoneId.systemDefault()).toLocalDate().toString()

/** Abre WhatsApp con un número y un texto (o el teléfono, o Mapas). */
object Afuera {
    /** El contexto de la app (lo pone CorredorApp). */
    internal var contexto: Context? = null

    private fun abrir(u: Uri, marcar: Boolean = false) {
        val c = contexto ?: return
        val i = Intent(if (marcar) Intent.ACTION_DIAL else Intent.ACTION_VIEW, u).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { c.startActivity(i) }
    }

    fun whatsapp(telefono: String?, texto: String = "") {
        val n = (telefono ?: "").filter { it.isDigit() }
        val b = Uri.parse("https://wa.me/$n").buildUpon()
        if (texto.isNotEmpty()) b.appendQueryParameter("text", texto)
        abrir(b.build())
    }

    /** Abre el marcador con el número (llamar directo pide permiso). */
    fun llamar(telefono: String?) {
        val n = (telefono ?: "").filter { it.isDigit() || it == '+' }
        abrir(Uri.parse("tel:$n"), marcar = true)
    }

    /** Cómo llegar: la app de mapas del teléfono con la dirección. */
    fun mapas(direccion: String) {
        abrir(Uri.parse("geo:0,0?q=" + Uri.encode(direccion)))
    }
}

/** «2026-11-12» → la fecha a las 12 del mediodía en la hora del teléfono (sin corrimientos de huso). */
fun Fecha.dia(s: String): Instant? {
    val p = s.take(10).split("-").mapNotNull { it.toIntOrNull() }
    if (p.size != 3) return null
    return runCatching { LocalDate.of(p[0], p[1], p[2]).atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant() }.getOrNull()
}

/** Un campo con su etiqueta arriba (13/600 en tinta 2). */
@Composable
fun Etiquetado(etiqueta: String, modifier: Modifier = Modifier, contenido: @Composable ColumnScope.() -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Texto(etiqueta, Estilo.footnoteFuerte, C.texto2)
        contenido()
    }
}

