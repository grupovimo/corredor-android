package ar.com.corredor.app.pantallas.propiedades

import ar.com.corredor.app.app.PantallaApilada

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import ar.com.corredor.app.app.LocalEstado
import ar.com.corredor.app.nucleo.*
import ar.com.corredor.app.tema.*
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import kotlin.math.max
import kotlin.math.min

// Clon de Pantallas/Propiedades/ImpresosPropiedad.swift.

// Los impresos de una propiedad (web: (impresos)/propiedades/[id]/ficha y /tarjeta): la ficha A4 para entregar (foto,
// dirección y precio, lo que tiene, dos fotos más, el agente con su teléfono y el QR a la ficha pública o al WhatsApp
// del agente) y la tarjeta de 85 × 55 mm que se deja en la visita. En iOS el PDF sale de la misma vista (ImageRenderer);
// acá la vista es Compose y el PDF se dibuja con el mismo diseño en un PdfDocument.

object FichaImpresa {
    data class Datos(
        val p: Propiedad,
        val inmobiliaria: String,
        val fotos: List<Bitmap>,
        val agente: String?,
        val telefono: String?,
        val qr: Bitmap?,
        val publica: Boolean,
    )

    /** Un QR nítido (sin suavizar) del texto. */
    fun qr(texto: String): Bitmap? = runCatching {
        val m = QRCodeWriter().encode(texto, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.MARGIN to 0))
        val escala = 10
        val w = m.width * escala; val h = m.height * escala
        val px = IntArray(w * h)
        for (y in 0 until h) for (x in 0 until w) px[y * w + x] = if (m.get(x / escala, y / escala)) android.graphics.Color.BLACK else android.graphics.Color.WHITE
        Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }.getOrNull()

    suspend fun datos(p: Propiedad, api: Api, cuantas: Int): Datos {
        val inmo = api.opcional<Inmobiliaria>("/v1/inmobiliaria")
        val equipo = api.opcional<List<Miembro>>("/v1/equipo") ?: emptyList()
        val m = equipo.firstOrNull { it.usuarioId == p.agenteId }
        val publica = if (p.publicada == true && p.slug != null) "${inmo?.web ?: Api.web()}/propiedades/${p.slug}" else null
        val wa = m?.telefono?.let { t -> "https://wa.me/${t.filter { it.isDigit() }}" }
        val fotos = mutableListOf<Bitmap>()
        for (u in (p.fotosUrl ?: emptyList()).take(cuantas)) Documentos.foto(u)?.let { fotos.add(it) }
        return Datos(
            p = p, inmobiliaria = inmo?.nombre ?: "Corredor", fotos = fotos, agente = m?.completo, telefono = m?.telefono,
            qr = (publica ?: wa)?.let { qr(it) }, publica = publica != null,
        )
    }

    /** La ficha A4 en PDF. */
    suspend fun a4(p: Propiedad, api: Api): File? {
        val d = datos(p, api, 3)
        return pdf(595, 842, "Ficha ${p.nombre}") { c -> dibujarA4(c, d) }
    }

    /** La tarjeta de 85 × 55 mm en PDF (241 × 156 pt). */
    suspend fun tarjeta(p: Propiedad, api: Api): File? {
        val d = datos(p, api, 1)
        return pdf(241, 156, "Tarjeta ${p.nombre}") { c -> dibujarTarjeta(c, d) }
    }

    /** El PDF en la caché que comparte el FileProvider (el contexto es el de la app, el que guarda Afuera). */
    suspend fun pdf(ancho: Int, alto: Int, nombre: String, dibujar: (android.graphics.Canvas) -> Unit): File? = withContext(Dispatchers.IO) {
        runCatching {
            val ctx = Afuera.contexto ?: return@runCatching null
            val doc = PdfDocument()
            val pagina = doc.startPage(PdfDocument.PageInfo.Builder(ancho, alto, 1).create())
            dibujar(pagina.canvas)
            doc.finishPage(pagina)
            val f = archivoParaCompartir(ctx, nombre.replace("/", "-") + ".pdf")
            f.outputStream().use { doc.writeTo(it) }
            doc.close()
            f
        }.getOrNull()
    }

    /** «Belgrano · 3 amb · 74 m² · 2 dorm. · 1 baño · apto crédito · expensas ARS 80.000». */
    fun linea(p: Propiedad): String {
        val credito = p.aptoCredito == true && !(p.comodidades ?: emptyList()).any { it.lowercase().contains("crédito") || it.lowercase().contains("credito") }
        return listOfNotNull(
            p.barrio, p.ambientes?.let { "$it amb" }, p.superficie?.let { "${Formato.numero(it.toInt())} m²" }, p.dormitorios?.let { "$it dorm." },
            p.banos?.let { Formato.cantidad(it, "baño", "baños") }, if (credito) "apto crédito" else null, p.expensas?.let { "expensas ${Formato.plata(it, "ARS")}" },
        ).joinToString(" · ")
    }

    /** Lo que dice el QR de la ficha A4. */
    fun leyendaA4(d: Datos): String {
        val n = d.p.fotosUrl?.size ?: 0
        return if (d.publica) (if (n > 1) "Escaneá para ver las $n fotos y consultar" else "Escaneá para ver la ficha y consultar") else "Escaneá y escribime"
    }

    /** Las dos líneas de la tarjeta: ambientes y metros, y las comodidades que entran (unos 20 caracteres). */
    fun lineasTarjeta(p: Propiedad): String {
        val linea1 = listOfNotNull(p.ambientes?.let { "$it amb" }, p.superficie?.let { "${Formato.numero(it.toInt())} m²" }).joinToString(" · ")
        val extras = (p.comodidades ?: emptyList()).map { it.lowercase() } + (if (p.aptoCredito == true) listOf("apto crédito") else emptyList())
        val linea2 = extras.fold(listOf<String>()) { acc, x -> if (acc.joinToString(" · ").length + x.length <= 20 || acc.isEmpty()) acc + x else acc }.joinToString(" · ")
        return linea1 + (if (linea2.isEmpty()) "" else "\n" + linea2)
    }

    fun leyendaTarjeta(d: Datos): String {
        val n = d.p.fotosUrl?.size ?: 0
        return if (n > 1) "$n fotos y consulta" else "Fotos y consulta"
    }

    // MARK: El dibujo del PDF

    private val crema = android.graphics.Color.rgb(0xEC, 0xE6, 0xDA)
    private val papel = android.graphics.Color.rgb(0xFA, 0xF8, 0xF3)
    private fun gris(b: Double) = android.graphics.Color.rgb((b * 255).toInt(), (b * 255).toInt(), (b * 255).toInt())

    private fun pintura(tam: Float, negrita: Boolean, color: Int) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = tam; this.color = color; typeface = if (negrita) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    }

    /** Un texto que se corta en renglones dentro de `ancho`; devuelve el alto que ocupó. */
    private fun texto(c: android.graphics.Canvas, s: String, x: Float, y: Float, ancho: Float, p: TextPaint, lineas: Int = Int.MAX_VALUE): Float {
        val l = StaticLayout.Builder.obtain(s, 0, s.length, p, max(1, ancho.toInt()))
            .setAlignment(Layout.Alignment.ALIGN_NORMAL).setMaxLines(lineas).setEllipsize(TextUtils.TruncateAt.END).build()
        c.save(); c.translate(x, y); l.draw(c); c.restore()
        return l.height.toFloat()
    }

    /** La foto llenando el rectángulo (scaledToFill) y recortada, con radio. */
    private fun foto(c: android.graphics.Canvas, b: Bitmap, r: RectF, radio: Float = 0f) {
        val escala = max(r.width() / b.width, r.height() / b.height)
        val w = r.width() / escala; val h = r.height() / escala
        val src = android.graphics.Rect(((b.width - w) / 2).toInt(), ((b.height - h) / 2).toInt(), ((b.width + w) / 2).toInt(), ((b.height + h) / 2).toInt())
        c.save()
        if (radio > 0) c.clipPath(android.graphics.Path().apply { addRoundRect(r, radio, radio, android.graphics.Path.Direction.CW) }) else c.clipRect(r)
        c.drawBitmap(b, src, r, Paint(Paint.FILTER_BITMAP_FLAG))
        c.restore()
    }

    private fun qrEn(c: android.graphics.Canvas, b: Bitmap, r: RectF) {
        c.drawBitmap(b, null, r, Paint()) // sin filtro: nítido (.interpolation(.none))
    }

    /** Una etiqueta blanca con radio 4 (el nombre de la inmobiliaria, el precio). */
    private fun etiqueta(c: android.graphics.Canvas, s: String, x: Float, abajo: Float, p: TextPaint, hx: Float, alto: Float) {
        val w = p.measureText(s) + hx * 2
        val r = RectF(x, abajo - alto, x + w, abajo)
        c.drawRoundRect(r, 4f, 4f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE })
        val fm = p.fontMetrics
        c.drawText(s, x + hx, r.centerY() - (fm.ascent + fm.descent) / 2, p)
    }

    /** La ficha A4 (07 · 48 «Ficha para entregar · A4»), igual que HojaFichaA4. */
    private fun dibujarA4(c: android.graphics.Canvas, d: Datos) {
        val p = d.p
        val ancho = 595f; val alto = 842f
        c.drawColor(papel)
        val cabeza = RectF(0f, 0f, ancho, 340f)
        c.drawRect(cabeza, Paint().apply { color = crema })
        d.fotos.firstOrNull()?.let { foto(c, it, cabeza) }
        val chico = pintura(10f, true, android.graphics.Color.BLACK)
        etiqueta(c, d.inmobiliaria.uppercase(Formato.argentina), 16f, 340f - 16f, chico, 8f, 10f + 8f)
        val m = 28f
        var y = 340f + m
        val adentro = ancho - m * 2
        val grande = pintura(20f, true, android.graphics.Color.BLACK)
        val precioAncho = grande.measureText(p.precioTexto)
        val hTitulo = texto(c, p.titulo ?: p.nombre, m, y, adentro - precioAncho - 12f, grande)
        c.drawText(p.precioTexto, ancho - m - precioAncho, y - grande.fontMetrics.ascent, grande)
        y += hTitulo + 12f
        y += texto(c, linea(p), m, y, adentro, pintura(11f, false, gris(0.3))) + 12f
        if (d.fotos.size > 1) {
            val n = min(3, d.fotos.size) - 1
            val w = (adentro - 8f * (n - 1)) / n
            for (i in 1 until min(3, d.fotos.size)) foto(c, d.fotos[i], RectF(m + (i - 1) * (w + 8f), y, m + (i - 1) * (w + 8f) + w, y + 150f), 4f)
            y += 150f + 12f
        }
        p.descripcion?.nonEmpty?.let { t -> texto(c, t, m, y, adentro, pintura(10.5f, false, android.graphics.Color.BLACK), 10) }
        if (d.qr != null || d.agente != null) {
            val abajo = alto - m
            var x = m
            d.qr?.let { q -> qrEn(c, q, RectF(m, abajo - 64f, m + 64f, abajo)); x += 64f + 12f }
            val a = d.agente
            val lineaAgente = a?.let { it + (d.telefono?.let { t -> " · $t" } ?: "") }
            val pa = pintura(11f, true, android.graphics.Color.BLACK)
            val pl = pintura(10f, false, gris(0.35))
            val hA = if (lineaAgente != null) 14f else 0f
            val hL = if (d.qr != null) 13f else 0f
            var yy = abajo - 32f - (hA + 2f + hL) / 2
            if (lineaAgente != null) { texto(c, lineaAgente, x, yy, ancho - m - x, pa, 1); yy += hA + 2f }
            if (d.qr != null) texto(c, leyendaA4(d), x, yy, ancho - m - x, pl, 1)
        }
    }

    /** La tarjeta de 85 × 55 mm (08 · T3 «se deja en la visita»), igual que HojaTarjeta. */
    private fun dibujarTarjeta(c: android.graphics.Canvas, d: Datos) {
        val p = d.p
        val ancho = 241f; val alto = 156f
        c.drawColor(papel)
        val izq = RectF(0f, 0f, ancho - 90f, alto)
        c.drawRect(izq, Paint().apply { color = crema })
        d.fotos.firstOrNull()?.let { foto(c, it, izq) }
        etiqueta(c, p.precioTexto, 8f, alto - 8f, pintura(7f, true, android.graphics.Color.BLACK), 5f, 15f)
        val x = ancho - 90f + 10f
        val w = 90f - 20f
        var y = 10f
        y += texto(c, p.titulo ?: p.nombre, x, y, w, pintura(7.5f, true, android.graphics.Color.BLACK), 3) + 3f
        texto(c, lineasTarjeta(p), x, y, w, pintura(6.5f, false, gris(0.4)))
        val q = d.qr
        if (q != null && d.publica) {
            val abajo = alto - 10f
            texto(c, leyendaTarjeta(d), x, abajo - 8f, w, pintura(6f, false, gris(0.4)), 1)
            qrEn(c, q, RectF(x, abajo - 8f - 3f - 44f, x + 44f, abajo - 8f - 3f))
        }
    }
}

/** La ficha A4 (07 · 48 «Ficha para entregar · A4»). Siempre en claro. */
@Composable
fun HojaFichaA4(d: FichaImpresa.Datos, modifier: Modifier = Modifier) {
    val p = d.p
    Column(modifier.background(hex(0xFAF8F3))) {
        Box(Modifier.fillMaxWidth().height(340.dp).clipToBounds().background(hex(0xECE6DA)), contentAlignment = Alignment.BottomStart) {
            d.fotos.firstOrNull()?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
            Box(Modifier.padding(16.dp).clip(RoundedCornerShape(4.dp)).background(Color.White).padding(horizontal = 8.dp, vertical = 4.dp)) {
                BasicText(d.inmobiliaria.uppercase(Formato.argentina), style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Black))
            }
        }
        Column(Modifier.fillMaxWidth().weight(1f).padding(28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                BasicText(p.titulo ?: p.nombre, Modifier.weight(1f), style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.Black))
                BasicText(p.precioTexto, style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.Black))
            }
            BasicText(FichaImpresa.linea(p), style = TextStyle(fontSize = 11.sp, color = Color(0.3f, 0.3f, 0.3f)))
            if (d.fotos.size > 1) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (i in 1 until minOf(3, d.fotos.size)) {
                        Image(d.fotos[i].asImageBitmap(), null, Modifier.weight(1f).height(150.dp).clip(RoundedCornerShape(4.dp)), contentScale = ContentScale.Crop)
                    }
                }
            }
            p.descripcion?.nonEmpty?.let { BasicText(it, style = TextStyle(fontSize = 10.5.sp, color = Color.Black), maxLines = 10, overflow = TextOverflow.Ellipsis) }
            Spacer(Modifier.weight(1f))
            if (d.qr != null || d.agente != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    d.qr?.let { Image(it.asImageBitmap(), null, Modifier.size(64.dp), filterQuality = FilterQuality.None) }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        d.agente?.let { a -> BasicText(a + (d.telefono?.let { " · $it" } ?: ""), style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Black)) }
                        if (d.qr != null) BasicText(FichaImpresa.leyendaA4(d), style = TextStyle(fontSize = 10.sp, color = Color(0.35f, 0.35f, 0.35f)))
                    }
                }
            }
        }
    }
}

/** La tarjeta de 85 × 55 mm (08 · T3 «se deja en la visita»). Siempre en claro. */
@Composable
fun HojaTarjeta(d: FichaImpresa.Datos, modifier: Modifier = Modifier) {
    val p = d.p
    val gris = Color(0.4f, 0.4f, 0.4f)
    Row(modifier.background(hex(0xFAF8F3))) {
        Box(Modifier.weight(1f).fillMaxHeight().clipToBounds().background(hex(0xECE6DA)), contentAlignment = Alignment.BottomStart) {
            d.fotos.firstOrNull()?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
            Box(Modifier.padding(8.dp).height(15.dp).clip(RoundedCornerShape(4.dp)).background(Color.White).padding(horizontal = 5.dp), contentAlignment = Alignment.Center) {
                BasicText(p.precioTexto, style = TextStyle(fontSize = 7.sp, fontWeight = FontWeight.Bold, color = Color.Black))
            }
        }
        Column(Modifier.width(90.dp).fillMaxHeight().padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            BasicText(p.titulo ?: p.nombre, style = TextStyle(fontSize = 7.5.sp, fontWeight = FontWeight.Bold, color = Color.Black), maxLines = 3, overflow = TextOverflow.Ellipsis)
            BasicText(FichaImpresa.lineasTarjeta(p), style = TextStyle(fontSize = 6.5.sp, color = gris))
            Spacer(Modifier.weight(1f))
            val q = d.qr
            if (q != null && d.publica) {
                Image(q.asImageBitmap(), null, Modifier.size(44.dp), filterQuality = FilterQuality.None)
                BasicText(FichaImpresa.leyendaTarjeta(d), style = TextStyle(fontSize = 6.sp, color = gris))
            }
        }
    }
}

/** La tarjeta para la visita: cómo queda y el PDF para imprimir o mandar. */
@Composable
fun TarjetaVisita(id: String) {
    val estado = LocalEstado.current
    val ctx = LocalContext.current
    var d by remember { mutableStateOf<FichaImpresa.Datos?>(null) }
    var error by remember { mutableStateOf<ErrorApi?>(null) }

    suspend fun cargar() {
        try {
            val p: Propiedad = estado.api.get("/v1/propiedades/$id")
            d = FichaImpresa.datos(p, estado.api, 1)
            error = null
        } catch (e: CancellationException) { throw e } catch (e: ErrorApi) { error = e } catch (e: Exception) {}
    }

    LaunchedEffect(Unit) { cargar() }

    PantallaApilada("Tarjeta para la visita") {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val dd = d
            val e = error
            if (dd != null) {
                val forma = RoundedCornerShape(8.dp)
                HojaTarjeta(
                    dd,
                    Modifier.width(340.dp).height(220.dp)
                        .sombra(forma, C.sombra.copy(alpha = 0.32f), 11.dp, y = 10.dp).sombra(forma, C.sombra.copy(alpha = 0.07f), 1.dp, y = 1.dp)
                        .clip(forma),
                )
                Texto(
                    Formato.sinViudas("85 × 55 mm, para dejar en la visita. El QR abre la ficha en tu web (sólo si está publicada)."),
                    Estilo.footnote, C.texto2, alinear = TextAlign.Center,
                )
                BotonPrincipal("Imprimir o mandar", { estado.tarea { FichaImpresa.tarjeta(dd.p, estado.api)?.let { verArchivo(ctx, it) } } }, simbolo = "printer")
                BotonHundido("La ficha A4", { estado.tarea { FichaImpresa.a4(dd.p, estado.api)?.let { verArchivo(ctx, it) } } }, Modifier.fillMaxWidth(), simbolo = "doc.richtext")
            } else if (e != null) {
                EstadoError("la propiedad", e) { estado.tarea { cargar() } }
            } else {
                Box(Modifier.padding(top = 60.dp)) { Rueda() }
            }
        }
    }
}
