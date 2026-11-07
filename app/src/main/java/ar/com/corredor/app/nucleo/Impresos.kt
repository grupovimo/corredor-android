package ar.com.corredor.app.nucleo

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import ar.com.corredor.app.pantallas.cobranzas.Cobro
import ar.com.corredor.app.pantallas.cobranzas.ComprobanteRendicion
import ar.com.corredor.app.pantallas.cobranzas.ContratoAPI
import ar.com.corredor.app.pantallas.cobranzas.ReciboVista
import ar.com.corredor.app.pantallas.cobranzas.RendicionAPI
import ar.com.corredor.app.pantallas.cobranzas.TextosCobro
import ar.com.corredor.app.pantallas.propiedades.Inmobiliaria
import ar.com.corredor.app.pantallas.propiedades.Propiedad
import ar.com.corredor.app.pantallas.secciones.ListaGenerica
import ar.com.corredor.app.tema.diaCompleto
import ar.com.corredor.app.tema.dia
import ar.com.corredor.app.tema.plata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// Clon de Nucleo/Impresos.swift: los impresos (ficha A4, recibo, rendición, contrato, acta, expensas, recibo de sueldo,
// informe) se arman en el teléfono como PDF (PdfDocument de Android), con los mismos datos que la web: nada se abre en
// el navegador (que no tiene la sesión de la app). Se ven con el visor del sistema, que ya trae compartir e imprimir.

/** Un documento para imprimir: encabezado, bloques de filas (clave · valor), texto libre, foto y firmas. */
data class Documento(
    val titulo: String,
    val subtitulo: String? = null,
    val numero: String? = null,
    val foto: Bitmap? = null,
    val bloques: List<Bloque> = emptyList(),
    val firmas: List<Firma> = emptyList(),
    val pie: String? = null,
) {
    data class Bloque(
        val titulo: String? = null,
        val filas: List<Pair<String, String>> = emptyList(),
        val texto: String? = null,
        val total: Pair<String, String>? = null,
    )

    data class Firma(val parte: String, val nombre: String? = null, val trazo: String? = null)
}

/** Arma el PDF (A4, 595 × 842 pt) en varias páginas si hace falta y lo deja en un archivo temporal. */
object Impresor {
    /** A4 en puntos. */
    const val ancho = 595
    const val alto = 842
    val tam: Pair<Int, Int> = ancho to alto

    /** El contexto de la app (lo pone CorredorApp): los PDF van a cache/compartir. */
    internal var contexto: Context? = null

    private val tinta = 0xFF18201D.toInt()
    private val gris = 0xFF6B6457.toInt()
    private val linea = 0xFFE3DDCF.toInt()

    fun pdf(d: Documento, inmobiliaria: String, nombre: String): File? {
        val ctx = contexto ?: return null
        val paginas = paginar(d)
        val carpeta = File(ctx.cacheDir, "compartir").apply { mkdirs() }
        val archivo = File(carpeta, nombre.replace("/", "-") + ".pdf")
        val doc = PdfDocument()
        return try {
            paginas.forEachIndexed { i, p ->
                val pagina = doc.startPage(PdfDocument.PageInfo.Builder(ancho, alto, i + 1).create())
                dibujar(pagina.canvas, inmobiliaria, d, p, i + 1, paginas.size)
                doc.finishPage(pagina)
            }
            archivo.outputStream().use { doc.writeTo(it) }
            archivo
        } catch (e: Exception) {
            null
        } finally {
            doc.close()
        }
    }

    /** Reparte los bloques en páginas por cantidad de renglones (unos 34 por página, menos en la primera si lleva foto). */
    fun paginar(d: Documento): List<List<Documento.Bloque>> {
        val paginas = mutableListOf(mutableListOf<Documento.Bloque>())
        var usados = if (d.foto == null) 0 else 12
        val tope = 34
        for (b in d.bloques) {
            var resto = b
            while (true) {
                val texto = (resto.texto?.length ?: 0) / 90 + (if (resto.texto == null) 0 else 1)
                val altoB = 2 + resto.filas.size + texto + (if (resto.total == null) 0 else 2)
                if (usados + altoB <= tope || usados == 0) {
                    if (usados + altoB > tope && resto.filas.size > tope - usados - 2) {
                        val entran = max(1, tope - usados - 2)
                        paginas.last().add(resto.copy(filas = resto.filas.take(entran), total = null))
                        resto = resto.copy(filas = resto.filas.drop(entran), titulo = resto.titulo?.let { "$it (sigue)" })
                        paginas.add(mutableListOf())
                        usados = 0
                        continue
                    }
                    paginas.last().add(resto)
                    usados += altoB
                    break
                }
                paginas.add(mutableListOf())
                usados = 0
            }
        }
        return paginas
    }

    private fun pintura(tam: Float, color: Int, negrita: Boolean = false, media: Boolean = false) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = tam
        this.color = color
        typeface = when {
            negrita -> Typeface.DEFAULT_BOLD
            media -> Typeface.create("sans-serif-medium", Typeface.NORMAL)
            else -> Typeface.DEFAULT
        }
    }

    private fun texto(c: Canvas, s: String, x: Float, y: Float, p: TextPaint, ancho: Int, alinear: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL): Float {
        val l = StaticLayout.Builder.obtain(s, 0, s.length, p, max(1, ancho)).setAlignment(alinear).setLineSpacing(2f, 1f).build()
        c.save(); c.translate(x, y); l.draw(c); c.restore()
        return l.height.toFloat()
    }

    /** Una página A4: la marca de la inmobiliaria arriba, los bloques y el pie «Hecha con Corredor». */
    private fun dibujar(c: Canvas, inmobiliaria: String, d: Documento, pagina: List<Documento.Bloque>, numero: Int, total: Int) {
        c.drawColor(android.graphics.Color.WHITE)
        val m = 36f
        val util = (ancho - 2 * m).toInt()
        var y = m
        // La marca: la inicial en un círculo, el nombre en versalitas y el número a la derecha.
        val circulo = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1E3A5F.toInt() }
        c.drawCircle(m + 17, y + 17, 17f, circulo)
        val inicial = pintura(15f, 0xFFE9DCC3.toInt(), negrita = true).apply { textAlign = Paint.Align.CENTER }
        c.drawText(inmobiliaria.take(1), m + 17, y + 22.5f, inicial)
        val marca = pintura(13f, 0xFF1E3A5F.toInt(), negrita = true).apply { letterSpacing = 0.05f }
        c.drawText(inmobiliaria.uppercase(Formato.argentina), m + 44, y + 21.5f, marca)
        d.numero?.let { n -> c.drawText(n, ancho - m, y + 21f, pintura(11f, gris).apply { textAlign = Paint.Align.RIGHT }) }
        y += 34 + 14
        c.drawRect(m, y, ancho - m, y + 1, Paint().apply { color = linea })
        y += 1 + 14
        if (numero == 1) {
            y += texto(c, d.titulo, m, y, pintura(22f, tinta, negrita = true), util)
            d.subtitulo?.let { y += 3 + texto(c, it, m, y + 3, pintura(12f, gris), util) }
            y += 14
            d.foto?.let { f ->
                val destino = RectF(m, y, ancho - m, y + 210)
                val escala = max(destino.width() / f.width, destino.height() / f.height)
                val w = destino.width() / escala; val h = destino.height() / escala
                val origen = android.graphics.Rect(((f.width - w) / 2).toInt(), ((f.height - h) / 2).toInt(), ((f.width + w) / 2).toInt(), ((f.height + h) / 2).toInt())
                c.save()
                c.clipPath(Path().apply { addRoundRect(destino, 8f, 8f, Path.Direction.CW) })
                c.drawBitmap(f, origen, destino, Paint(Paint.FILTER_BITMAP_FLAG))
                c.restore()
                y += 210 + 14
            }
        }
        val etiqueta = pintura(11f, gris)
        val valor = pintura(11f, tinta, media = true)
        val rayita = Paint().apply { color = linea }
        for (b in pagina) {
            b.titulo?.let { t ->
                y += 4
                y += texto(c, t.uppercase(Formato.argentina), m, y, pintura(10f, gris, negrita = true).apply { letterSpacing = 0.05f }, util) + 5
            }
            for ((k, v) in b.filas) {
                val hv = texto(c, v, m + util * 0.4f, y, valor, (util * 0.6f).toInt(), Layout.Alignment.ALIGN_OPPOSITE)
                val hk = texto(c, k, m, y, etiqueta, (util * 0.4f - 12).toInt())
                y += max(hv, hk) + 3
                c.drawRect(m, y, ancho - m, y + 0.5f, rayita)
                y += 5
            }
            b.texto?.let { y += texto(c, it, m, y, pintura(11f, tinta), util) + 5 }
            b.total?.let { (k, v) ->
                y += 4
                val p = pintura(13f, tinta, negrita = true)
                c.drawText(k, m, y + 13, p)
                c.drawText(v, ancho - m, y + 13, TextPaint(p).apply { textAlign = Paint.Align.RIGHT })
                y += 20
            }
            y += 9
        }
        if (numero == total && d.firmas.isNotEmpty()) {
            y += 16
            val n = d.firmas.size
            val separacion = 16f
            val w = (util - separacion * (n - 1)) / n
            d.firmas.forEachIndexed { i, f ->
                val x = m + i * (w + separacion)
                f.trazo?.let { trazo(c, it, RectF(x, y, x + w, y + 50)) }
                c.drawRect(x, y + 54, x + w, y + 54.7f, Paint().apply { color = tinta })
                val centro = pintura(10f, tinta, negrita = true).apply { textAlign = Paint.Align.CENTER }
                c.drawText(f.nombre ?: f.parte, x + w / 2, y + 68, centro)
                c.drawText(f.parte, x + w / 2, y + 80, pintura(9f, gris).apply { textAlign = Paint.Align.CENTER })
            }
        }
        val pie = pintura(9f, gris)
        c.drawText(d.pie ?: "Hecha con Corredor", m, alto - m + 4, pie)
        if (total > 1) c.drawText("$numero de $total", ancho - m, alto - m + 4, TextPaint(pie).apply { textAlign = Paint.Align.RIGHT })
    }

    /** Una firma guardada (path SVG sobre 600 × 200: «M x y L x y…»), centrada en el recuadro. */
    private fun trazo(c: Canvas, trazo: String, r: RectF) {
        val esc = min(r.width() / 600f, r.height() / 200f)
        val dx = r.left + (r.width() - 600 * esc) / 2; val dy = r.top + (r.height() - 200 * esc) / 2
        val t = trazo.split(' ', ',').filter { it.isNotEmpty() }
        val p = Path()
        var i = 0
        while (i < t.size) {
            val k = t[i]
            val x = t.getOrNull(i + 1)?.toFloatOrNull(); val yy = t.getOrNull(i + 2)?.toFloatOrNull()
            if ((k == "M" || k == "L") && x != null && yy != null) {
                if (k == "M") p.moveTo(dx + x * esc, dy + yy * esc) else p.lineTo(dx + x * esc, dy + yy * esc)
                i += 3
            } else i += 1
        }
        c.drawPath(p, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = tinta; style = Paint.Style.STROKE; strokeWidth = 2.5f; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        })
    }
}

/** Un archivo para la vista previa (`.sheet(item:)` con QuickLook en iOS). */
data class ArchivoPrevio(val archivo: File, val tipo: String = "application/pdf") { val id: String get() = archivo.absolutePath }

/** Abre un archivo armado en la app con el visor del sistema (QuickLook en iOS: ver, compartir, imprimir). */
fun VistaPrevia(ctx: Context, a: ArchivoPrevio) {
    val uri = FileProvider.getUriForFile(ctx, "ar.com.corredor.app.archivos", a.archivo)
    val ver = Intent(Intent.ACTION_VIEW).setDataAndType(uri, a.tipo).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { ctx.startActivity(Intent.createChooser(ver, a.archivo.nameWithoutExtension).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/** .vistaPrevia($archivo) de iOS: cuando hay un archivo, lo abre en el visor y lo suelta. */
@Composable
fun VistaPrevia(archivo: ArchivoPrevio?, alCerrar: () -> Unit) {
    val ctx = LocalContext.current
    LaunchedEffect(archivo) {
        archivo?.let { VistaPrevia(ctx, it); alCerrar() }
    }
}

/** Los documentos de Corredor, armados con los datos de la API. */
object Documentos {
    suspend fun inmobiliaria(api: Api): Inmobiliaria? = api.opcional("/v1/inmobiliaria")

    suspend fun foto(url: String?): Bitmap? {
        if (url.isNullOrEmpty()) return null
        return withContext(Dispatchers.IO) {
            runCatching {
                Api.cliente().newCall(Request.Builder().url(url).build()).execute().use { r -> r.body?.bytes()?.let { BitmapFactory.decodeByteArray(it, 0, it.size) } }
            }.getOrNull()
        }
    }

    /** La ficha A4 de una propiedad (lo que se manda o se imprime para una visita). */
    suspend fun ficha(p: Propiedad, api: Api): File? {
        val inmo = inmobiliaria(api)
        val datos = mutableListOf("Operación" to p.operacionTexto.split(" ").joinToString(" ") { Formato.mayuscula(it) }, "Tipo" to p.tipoTexto)
        p.ambientes?.let { datos.add("Ambientes" to "$it") }
        p.dormitorios?.let { datos.add("Dormitorios" to "$it") }
        p.banos?.let { datos.add("Baños" to "$it") }
        p.cocheras?.let { datos.add("Cocheras" to "$it") }
        p.superficie?.let { datos.add("Superficie total" to "${Formato.numero(it.toInt())} m²") }
        p.cubierta?.let { datos.add("Cubierta" to "${Formato.numero(it.toInt())} m²") }
        p.expensas?.let { datos.add("Expensas" to Formato.plata(it, "ARS")) }
        if (p.aptoCredito == true) datos.add("Apto crédito" to "Sí")
        val bloques = mutableListOf(Documento.Bloque("Datos", datos))
        p.descripcion?.nonEmpty?.let { bloques.add(Documento.Bloque("Descripción", texto = it)) }
        val extras = (p.comodidades ?: emptyList()) + (p.caracteristicas ?: emptyList()) + (p.servicios ?: emptyList())
        if (extras.isNotEmpty()) bloques.add(Documento.Bloque("Comodidades", texto = extras.joinToString(" · ")))
        val contacto = mutableListOf<Pair<String, String>>()
        val w = inmo?.web; val s = p.slug
        if (w != null && s != null) contacto.add("Ficha online" to "${w.replace("https://", "")}/propiedades/$s")
        if (contacto.isNotEmpty()) bloques.add(Documento.Bloque("Más fotos y consultas", contacto))
        val d = Documento(p.nombre, listOfNotNull(p.precioTexto, p.barrio).joinToString(" · "),
            p.numero?.let { "LP-${String.format("%04d", it)}" }, foto(p.portada), bloques)
        return Impresor.pdf(d, inmo?.nombre ?: "Corredor", "Ficha ${p.nombre}")
    }

    /** El recibo de un cobro. */
    suspend fun recibo(c: Cobro, contrato: ContratoAPI?, api: Api): File? {
        val inmo = inmobiliaria(api)
        var filas = (c.conceptos ?: emptyList()).map { it.concepto to Formato.plata(it.monto, c.moneda) }
        if (filas.isEmpty()) filas = listOf("Alquiler" to Formato.plata(c.monto, c.moneda))
        val pago = listOfNotNull(c.pagadoEl?.let { Fecha.dia(it) }?.let { "Pagado el ${Formato.diaCompleto(it)}" }, c.medio?.let { "por ${ReciboVista.medioTexto(it)}" }).joinToString(" ")
        val bloques = mutableListOf(
            Documento.Bloque("Conceptos", filas, total = "Total cobrado" to Formato.plata(c.monto, c.moneda)),
            Documento.Bloque(texto = pago.ifEmpty { null }),
        )
        inmo?.cbu?.let { bloques.add(Documento.Bloque(texto = "CBU o alias: $it")) }
        val d = Documento(contrato?.contactoNombre ?: "Recibo", "${contrato?.propiedadTitulo ?: ""} · ${ReciboVista.mes(c.periodo)}",
            c.numero?.let { "Recibo N.º " + String.format("0001-%05d", it) }, bloques = bloques)
        return Impresor.pdf(d, inmo?.nombre ?: "Corredor", "Recibo ${c.periodo}")
    }

    /** La rendición de cuentas al propietario (07 · 48 «Rendición · A4»), con los datos del comprobante de la API (GET
     *  …/rendiciones/{rid}/imprimir): el propietario, la dirección, el detalle, «Neto transferido» o «Neto a transferir»,
     *  el próximo ajuste y las notas. */
    suspend fun rendicion(c: ComprobanteRendicion, items: List<RendicionAPI.Item>? = null, neto: Double? = null, api: Api): File? {
        val m = c.moneda
        val filas = mutableListOf("Alquiler cobrado" to Formato.numero(c.cobrado.roundToInt()))
        c.comision?.takeIf { it > 0 }?.let { x -> filas.add("Administración ${TextosCobro.numero(c.comisionPorcentaje ?: 0.0)} %" to "−" + Formato.numero(x.roundToInt())) }
        for (it in items ?: c.items ?: emptyList()) filas.add(it.descripcion to (if (it.monto < 0) "" else "−") + Formato.numero(abs(it.monto).roundToInt()))
        val partes = mutableListOf<Pair<String, String>>()
        c.propietario?.let { partes.add("Propietario" to it) }
        (c.propiedadDireccion?.nonEmpty ?: c.propiedadTitulo)?.let { partes.add("Propiedad" to it) }
        val bloques = mutableListOf<Documento.Bloque>()
        if (partes.isNotEmpty()) bloques.add(Documento.Bloque(null, partes))
        bloques.add(Documento.Bloque("Detalle", filas, total = (if (c.estado == "pagada") "Neto transferido" else "Neto a transferir") to Formato.plata(neto ?: c.neto, m)))
        val pie = mutableListOf<String>()
        c.proximo?.let { p ->
            var t = "Próximo ajuste: ${TextosCobro.nombreIndice[p.indice] ?: "índice"}, ${TextosCobro.fechaCortita(p.fecha)}"
            p.monto?.let { x -> t += ", alquiler ${if (p.estimado == true) "estimado " else ""}${Formato.plata(x, m)}" }
            pie.add("$t.")
        }
        c.notas?.nonEmpty?.let { pie.add(it) }
        if (pie.isNotEmpty()) bloques.add(Documento.Bloque(texto = pie.joinToString(" ")))
        val mes = TextosCobro.nombreMes(c.periodo).take(3).uppercase(Formato.argentina) + " " + c.periodo.take(4)
        val d = Documento("Rendición de cuentas", c.inmobiliaria?.nombre,
            RendicionAPI.numeroTexto(c.numero)?.let { "N.º $it · $mes" } ?: mes, bloques = bloques)
        val inmo = inmobiliaria(api)
        return Impresor.pdf(d, c.inmobiliaria?.nombre ?: inmo?.nombre ?: "Corredor", "Rendición ${c.periodo.take(7)}")
    }

    /** El contrato: partes, plazo, monto, ajuste y condiciones. */
    suspend fun contrato(k: ContratoAPI, api: Api): File? {
        val inmo = inmobiliaria(api)
        val m = k.moneda ?: "ARS"
        val partes = mutableListOf<Pair<String, String>>()
        k.contactoNombre?.let { partes.add((if (k.tipo == "venta") "Comprador" else "Inquilino") to it) }
        k.propietarioNombre?.let { partes.add("Propietario" to it) }
        k.garanteNombre?.let { partes.add("Garante" to it + (k.garanteTelefono?.let { t -> " · $t" } ?: "") + (k.garanteNotas?.let { n -> " · $n" } ?: "")) }
        val cond = mutableListOf(
            "Desde" to (k.inicio?.let { Fecha.dia(it) }?.let { Formato.diaCompleto(it) } ?: "—"),
            "Hasta" to (k.fin?.let { Fecha.dia(it) }?.let { Formato.diaCompleto(it) } ?: "—"),
        )
        k.monto?.let { cond.add((if (k.tipo == "venta") "Precio" else "Alquiler inicial") to Formato.plata(it, m)) }
        if (k.tipo != "venta") cond.add("Ajuste" to k.ajusteTexto)
        k.diaVencimiento?.let { cond.add("Vence" to "El $it de cada mes") }
        k.deposito?.let { cond.add("Depósito" to Formato.plata(it, m)) }
        k.honorarios?.let { cond.add("Honorarios" to "${TextosCobro.numero(it)} % del total del contrato") }
        k.punitorioDiario?.let { cond.add("Punitorios" to "${TextosCobro.numero(it)} % diario") }
        k.comision?.let { cond.add("Administración" to "${Formato.numero(it.toInt())} % de lo cobrado") }
        val bloques = mutableListOf(Documento.Bloque("Partes", partes), Documento.Bloque("Condiciones", cond))
        k.notas?.nonEmpty?.let { bloques.add(Documento.Bloque("Notas", texto = it)) }
        val d = Documento(k.propiedadTitulo ?: "Contrato", if (k.tipo == "venta") "Boleto de compraventa · resumen" else "Contrato de locación · resumen",
            bloques = bloques, firmas = listOf(Documento.Firma(if (k.tipo == "venta") "Comprador" else "Inquilino", k.contactoNombre),
                Documento.Firma("Propietario", k.propietarioNombre)))
        return Impresor.pdf(d, inmo?.nombre ?: "Corredor", "Contrato ${k.propiedadTitulo ?: ""}")
    }

    /** Cualquier respuesta de la API (liquidación de expensas, recibo de sueldo): las listas como bloques y los campos
     *  sueltos como filas. */
    suspend fun generico(titulo: String, subtitulo: String?, json: JSONLibre, api: Api, nombre: String): File? {
        val inmo = inmobiliaria(api)
        val bloques = mutableListOf<Documento.Bloque>()
        val sueltos = mutableListOf<Pair<String, String>>()
        if (json is JSONLibre.objeto) {
            for ((k, v) in json.v.toSortedMap()) {
                when (v) {
                    is JSONLibre.cadena -> if (!k.endsWith("Id") && k != "id") sueltos.add(etiqueta(k) to v.v)
                    is JSONLibre.numero -> sueltos.add(etiqueta(k) to Formato.numero(v.v.toInt()))
                    is JSONLibre.lista -> {
                        val filas = v.v.take(200).map { x ->
                            val monto = listOf("monto", "total", "importe", "saldo", "neto", "subtotal").firstNotNullOfOrNull { (x[it] as? JSONLibre.numero)?.v?.let { n -> Formato.plata(n) } }
                            ListaGenerica.titulo(x.elemento) to (monto ?: ListaGenerica.detalle(x.elemento) ?: "")
                        }
                        if (filas.isNotEmpty()) bloques.add(Documento.Bloque(etiqueta(k), filas))
                    }
                    else -> {}
                }
            }
        }
        if (sueltos.isNotEmpty()) bloques.add(0, Documento.Bloque(null, sueltos))
        return Impresor.pdf(Documento(titulo, subtitulo, bloques = bloques), inmo?.nombre ?: "Corredor", nombre)
    }

    fun etiqueta(k: String): String {
        val s = StringBuilder()
        for (c in k) if (c.isUpperCase()) s.append(" ").append(c.lowercaseChar()) else s.append(c)
        return s.take(1).toString().uppercase(Formato.argentina) + s.drop(1)
    }

    /** Un archivo crudo de la API (el F931 para AFIP) guardado para compartir. */
    suspend fun archivo(ruta: String, nombre: String, api: Api): File {
        val d = api.pedir("GET", ruta)
        val ctx = Impresor.contexto ?: throw ErrorApi.desconocido
        val u = File(File(ctx.cacheDir, "compartir").apply { mkdirs() }, nombre)
        withContext(Dispatchers.IO) { u.writeBytes(d) }
        return u
    }
}

