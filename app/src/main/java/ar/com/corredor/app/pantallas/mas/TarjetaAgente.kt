package ar.com.corredor.app.pantallas.mas

import ar.com.corredor.app.app.PantallaApilada

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import ar.com.corredor.app.app.Estado
import ar.com.corredor.app.app.LocalEstado
import ar.com.corredor.app.nucleo.*
import ar.com.corredor.app.tema.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import java.io.File
import java.net.URL

// Clon de Pantallas/Mas/TarjetaAgente.swift.

/** Lo estático de TarjetaAgente (sus modelos y los datos de la tarjeta). */
object TarjetaAgente {
    /** `GET /v1/equipo` (equipo.Miembro, con el WhatsApp). */
    @Serializable
    data class Persona(
        val usuarioId: String, val email: String? = null, val nombre: String? = null, val apellido: String? = null, val foto: String? = null,
        val telefono: String? = null, val whatsapp: String? = null, val rol: String? = null,
    )

    @Serializable
    data class ConBarrio(val id: String, val barrio: String? = null)

    /** `GET /v1/inmobiliaria`: lo que usa la tarjeta (el nombre y la marca). */
    @Serializable
    data class InmoTarjeta(val nombre: String? = null, val marca: JsonElement? = null) // marca: JSONLibre de iOS

    class Datos(
        val nombre: String,
        val email: String,
        val telefono: String?,
        val foto: Bitmap?,
        val rol: String,
        val barrios: List<String>,
        val inmobiliaria: String,
        val marca: MarcaResumen,
    ) {
        val linea: String get() = listOfNotNull(if (rol == "duenio") "Corredor inmobiliario" else "Agente", if (barrios.isEmpty()) null else barrios.joinToString(" y ")).joinToString(" · ")
    }
}

/** El símbolo de Corredor para el pie del PDF (Image("Simbolo")). */
private fun simbolo(ctx: Context): Bitmap? {
    val id = ctx.resources.getIdentifier("simbolo", "drawable", ctx.packageName)
    return if (id == 0) null else runCatching { BitmapFactory.decodeResource(ctx.resources, id) }.getOrNull()
}

/** El PDF del tamaño de la tarjeta (85 × 55 mm = 241 × 156 pt), dibujado igual que TarjetaImpresa. */
private fun pdfTarjeta(ctx: Context, estado: Estado, d: TarjetaAgente.Datos): File? {
    // ImageRenderer de iOS → acá se dibuja a mano con PdfDocument, igual que TarjetaImpresa.
    return try {
        val ancho = 241f
        val alto = 156f
        val doc = PdfDocument()
        val pagina = doc.startPage(PdfDocument.PageInfo.Builder(241, 156, 1).create())
        val c = pagina.canvas
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        c.drawColor(android.graphics.Color.WHITE)
        val izq = ancho * 0.36f
        p.color = d.marca.primario.toArgb()
        c.drawRect(0f, 0f, izq, alto, p)
        val f = d.foto
        if (f != null) {
            // scaledToFill + clipped.
            val escala = maxOf(izq / f.width, alto / f.height)
            val w = f.width * escala
            val h = f.height * escala
            c.save()
            c.clipRect(0f, 0f, izq, alto)
            c.drawBitmap(f, null, RectF((izq - w) / 2, (alto - h) / 2, (izq + w) / 2, (alto + h) / 2), p)
            c.restore()
        } else {
            p.color = d.marca.acento.toArgb()
            p.textSize = 26f
            p.typeface = Typeface.DEFAULT_BOLD
            p.textAlign = Paint.Align.CENTER
            c.drawText(Formato.iniciales(d.nombre), izq / 2, alto / 2 + 9f, p)
        }
        p.textAlign = Paint.Align.LEFT
        val x = izq + 10f
        // La marca: el círculo con la inicial y el nombre de la inmobiliaria.
        p.color = d.marca.primario.toArgb()
        c.drawCircle(x + 7f, 17f, 7f, p)
        p.color = d.marca.acento.toArgb(); p.textSize = 8f; p.typeface = Typeface.DEFAULT_BOLD; p.textAlign = Paint.Align.CENTER
        c.drawText(d.marca.inicial, x + 7f, 20f, p)
        p.textAlign = Paint.Align.LEFT; p.color = d.marca.primario.toArgb(); p.textSize = 7f; p.letterSpacing = 0.4f / 7f
        c.drawText(recortar(d.inmobiliaria.uppercase(), p, ancho - x - 24f - 10f), x + 18f, 19.5f, p)
        p.letterSpacing = 0f
        // El nombre y la línea.
        p.color = 0xFF18201D.toInt(); p.typeface = Typeface.DEFAULT_BOLD
        achicar(d.nombre, p, 13f, ancho - x - 10f) // minimumScaleFactor(0.7)
        c.drawText(recortar(d.nombre, p, ancho - x - 10f), x, 66f, p)
        p.color = 0xFF6B6457.toInt(); p.textSize = 7.5f; p.typeface = Typeface.DEFAULT
        c.drawText(recortar(d.linea, p, ancho - x - 10f), x, 78f, p)
        // Cómo encontrarlo.
        // .medium, con minimumScaleFactor(0.7).
        p.color = 0xFF18201D.toInt()
        p.typeface = if (android.os.Build.VERSION.SDK_INT >= 28) Typeface.create(Typeface.DEFAULT, 500, false) else Typeface.DEFAULT
        var y = 112f
        d.telefono?.let { achicar(it, p, 7f, ancho - x - 10f); c.drawText(recortar(it, p, ancho - x - 10f), x, y, p); y += 9f }
        achicar(d.email, p, 7f, ancho - x - 10f)
        c.drawText(recortar(d.email, p, ancho - x - 10f), x, y, p)
        p.typeface = Typeface.DEFAULT
        // «Hecha con Corredor».
        simbolo(ctx)?.let { s ->
            p.alpha = (0.6f * 255).toInt()
            c.drawBitmap(s, null, RectF(x, alto - 17f, x + 7f, alto - 10f), p)
            p.alpha = 255
        }
        p.color = 0xFF6B6457.toInt(); p.textSize = 5.5f
        c.drawText("Hecha con Corredor", x + 10f, alto - 11f, p)
        doc.finishPage(pagina)
        val carpeta = File(ctx.cacheDir, "compartir").apply { mkdirs() }
        val archivo = File(carpeta, "Tarjeta ${d.nombre}.pdf".replace("/", "-"))
        archivo.outputStream().use { doc.writeTo(it) }
        doc.close()
        archivo
    } catch (e: Exception) {
        estado.avisar("No pudimos armar la tarjeta.", ok = false)
        null
    }
}

/** Achica la letra hasta el 70 % para que `t` entre en `ancho` (minimumScaleFactor(0.7)). */
private fun achicar(t: String, p: Paint, tam: Float, ancho: Float) {
    p.textSize = tam
    while (p.textSize > tam * 0.7f && p.measureText(t) > ancho) p.textSize -= 0.25f
}

/** Corta el texto con «…» para que entre en `ancho` (lineLimit(1)). */
private fun recortar(t: String, p: Paint, ancho: Float): String {
    if (p.measureText(t) <= ancho) return t
    var s = t
    while (s.isNotEmpty() && p.measureText("$s…") > ancho) s = s.dropLast(1)
    return "$s…"
}

/** Tarjeta del agente · 85 × 55 mm (08 · T2 «se arma sola desde Equipo»): la foto, la marca de la inmobiliaria, el
 *  nombre, de qué barrios es (los de su cartera) y cómo encontrarlo. Se ve en pantalla y se comparte o imprime como
 *  PDF del tamaño de la tarjeta. */
@Composable
fun TarjetaAgente(usuario: String) {
    val estado = LocalEstado.current
    val ctx = LocalContext.current
    val alcance = rememberCoroutineScope()
    var datos by remember { mutableStateOf<TarjetaAgente.Datos?>(null) }
    var error by remember { mutableStateOf<ErrorApi?>(null) }
    var noEsta by remember { mutableStateOf(false) }

    suspend fun cargar() {
        try {
            val equipo: List<TarjetaAgente.Persona> = estado.api.get("/v1/equipo")
            val m = equipo.firstOrNull { it.usuarioId == usuario }
            if (m == null || m.rol == "lector") { noEsta = true; return }
            val inmo: TarjetaAgente.InmoTarjeta? = estado.api.opcional("/v1/inmobiliaria")
            val props: List<TarjetaAgente.ConBarrio> = estado.api.opcional("/v1/propiedades", listOf("agente" to usuario, "limite" to "200")) ?: emptyList()
            val cuenta = mutableMapOf<String, Int>()
            for (p in props) { val b = p.barrio?.takeIf { it.isNotBlank() }; if (b != null) cuenta[b] = (cuenta[b] ?: 0) + 1 }
            val barrios = cuenta.entries.sortedByDescending { it.value }.take(2).map { it.key }
            val nombre = listOfNotNull(m.nombre, m.apellido).filter { it.isNotBlank() }.joinToString(" ").takeIf { it.isNotBlank() } ?: m.email ?: ""
            val foto = Documentos.foto(m.foto)
            val tel = (m.whatsapp ?: m.telefono)?.replace(Regex("^\\s*\\+?54\\s*9?\\s*"), "")?.trim()
            datos = TarjetaAgente.Datos(nombre, m.email ?: "", tel?.takeIf { it.isNotBlank() }, foto, m.rol ?: "agente", barrios,
                inmo?.nombre ?: estado.inmobiliaria?.nombre ?: "", MarcaResumen(inmo?.marca, inmo?.nombre ?: ""))
            error = null
        } catch (e: CancellationException) { throw e } catch (e: ErrorApi) { error = e } catch (e: Exception) {}
    }

    LaunchedEffect(Unit) { cargar() }

    PantallaApilada(datos?.let { "Tarjeta · ${it.nombre}" } ?: "Tarjeta") {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            val d = datos
            val err = error
            if (d != null) {
                Box(Modifier.size(340.dp, 220.dp).sombraE2(RoundedCornerShape(10.dp)).clip(RoundedCornerShape(10.dp))) { TarjetaImpresa(d) }
                Texto(Formato.sinViudas("85 × 55 mm. Compartila o mandala a imprimir."), Estilo.footnote, C.texto2)
                BotonPrincipal("Compartir o imprimir", {
                    alcance.launch {
                        val f = withContext(Dispatchers.IO) { pdfTarjeta(ctx, estado, d) }
                        if (f != null) verArchivo(ctx, f)
                    }
                }, simbolo = "printer")
            } else if (noEsta) {
                Box(Modifier.padding(top = 60.dp)) {
                    EstadoVacio(icono = "person.crop.circle.badge.questionmark", titulo = "Esa persona no está en el equipo.",
                        accion = "Ver el equipo" to { estado.abrir(ASeccion(clave = "equipo")) })
                }
            } else if (err != null) {
                Box(Modifier.padding(top = 60.dp)) { EstadoError("la tarjeta", err) { alcance.launch { cargar() } } }
            } else {
                Esqueleto(ancho = 340.dp, alto = 220.dp, radio = 10.dp)
            }
        }
    }
}

/** La tarjeta en sí (siempre clara, como el papel): la foto a la izquierda y los datos con la marca. */
@Composable
fun TarjetaImpresa(d: TarjetaAgente.Datos) {
    val ctx = LocalContext.current
    // .font(.system(size:)) no sigue el tamaño de letra del sistema: las medidas van en puntos fijos.
    val escalaLetra = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.White)) {
        val e = this@BoxWithConstraints.maxWidth.value / 241f
        @Composable
        fun t(texto: String, tam: Float, color: Color, peso: FontWeight = FontWeight.Normal, lineas: Int = 1, tracking: Float = 0f, escala: Boolean = false, m: Modifier = Modifier) {
            BasicText(
                texto, m,
                style = TextStyle(fontSize = (tam / escalaLetra).sp, fontWeight = peso, color = color, letterSpacing = (tracking / escalaLetra).sp),
                maxLines = lineas, overflow = TextOverflow.Ellipsis,
                autoSize = if (escala) TextAutoSize.StepBased((tam * 0.7f / escalaLetra).sp, (tam / escalaLetra).sp, 0.5.sp) else null,
            )
        }
        Row(Modifier.fillMaxSize()) {
            Box(Modifier.width(this@BoxWithConstraints.maxWidth * 0.36f).fillMaxHeight().background(d.marca.primario), contentAlignment = Alignment.Center) {
                val f = d.foto
                if (f != null) Image(f.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else t(Formato.iniciales(d.nombre), 26 * e, d.marca.acento, FontWeight.Bold)
            }
            Column(Modifier.weight(1f).fillMaxHeight().padding((10 * e).dp), verticalArrangement = Arrangement.spacedBy((3 * e).dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy((4 * e).dp)) {
                    Box(Modifier.size((14 * e).dp).clip(CircleShape).background(d.marca.primario), contentAlignment = Alignment.Center) {
                        t(d.marca.inicial, 8 * e, d.marca.acento, FontWeight.Bold)
                    }
                    t(d.inmobiliaria.uppercase(), 7 * e, d.marca.primario, FontWeight.Bold, tracking = 0.4f)
                }
                Spacer(Modifier.weight(1f))
                t(d.nombre, 13 * e, hex(0x18201D), FontWeight.Bold, lineas = 2, escala = true)
                t(d.linea, 7.5f * e, hex(0x6B6457), lineas = 2)
                Spacer(Modifier.weight(1f))
                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    d.telefono?.let { t(it, 7 * e, hex(0x18201D), FontWeight.Medium, escala = true) }
                    t(d.email, 7 * e, hex(0x18201D), FontWeight.Medium, escala = true)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy((3 * e).dp)) {
                    val id = remember { ctx.resources.getIdentifier("simbolo", "drawable", ctx.packageName) }
                    if (id != 0) Image(androidx.compose.ui.res.painterResource(id), null, Modifier.size((7 * e).dp).alpha(0.6f), contentScale = ContentScale.Fit)
                    t("Hecha con Corredor", 5.5f * e, hex(0x6B6457))
                }
            }
        }
    }
}
