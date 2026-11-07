package ar.com.corredor.app.pantallas.web

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import ar.com.corredor.app.app.Estado
import ar.com.corredor.app.app.LocalEstado
import ar.com.corredor.app.nucleo.*
import ar.com.corredor.app.pantallas.mas.*
import ar.com.corredor.app.tema.*
import coil3.compose.SubcomposeAsyncImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

// Clon de Pantallas/Web/Marca.swift.
// Las piezas para editar la marca y la web desde el celular: el borrador (lo mismo que edita el panel
// web, sin publicar hasta «Publicar»), la vista previa de la web real con el borrador, y la subida del
// logo y la portada. Las usan el alta (Pantallas/Entrada/Alta.swift) y Mi web.

// MARK: - JSON libre (JSONLibre de iOS → JsonElement de kotlinx)

/** `.texto` de JSONLibre: el texto, o null si no es un texto. */
val JsonElement?.texto: String? get() = (this as? JsonPrimitive)?.takeIf { it.isString }?.content

/** `j["k"]` de JSONLibre: el campo de un objeto, o null. */
operator fun JsonElement?.get(k: String): JsonElement? = (this as? JsonObject)?.get(k)

/** `.numero` de JSONLibre. */
val JsonElement?.numero: Double? get() = (this as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull

/** `.bool` de JSONLibre. */
val JsonElement?.bool: Boolean? get() = (this as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull

/** `.objeto` de JSONLibre. */
val JsonElement?.objeto: Map<String, JsonElement>? get() = this as? JsonObject

/** `.lista` de JSONLibre. */
val JsonElement?.lista: List<JsonElement>? get() = this as? JsonArray

fun jTexto(s: String): JsonElement = JsonPrimitive(s)
fun jNumero(n: Double): JsonElement = JsonPrimitive(n)
fun jBool(b: Boolean): JsonElement = JsonPrimitive(b)
fun jObjeto(o: Map<String, JsonElement>): JsonElement = JsonObject(o)
fun jLista(l: List<JsonElement>): JsonElement = JsonArray(l)

/** El borrador de la web en la API: leer, guardar (con el `en` que se leyó: si otro lo guardó en el
 *  medio, 409) y publicar o descartar. */
object BorradorWeb {
    suspend fun leer(api: Api): Borrador = api.get("/v1/web/borrador")

    @Serializable private data class Cuerpo(val marca: Map<String, JsonElement>, val si: String? = null)

    /** Guarda la marca entera. Devuelve el borrador nuevo; con 409 (`borrador_cambiado`), el error. */
    suspend fun guardar(api: Api, marca: Map<String, JsonElement>, si: String?): Borrador =
        api.enviar("PUT", "/v1/web/borrador", Cuerpo(marca, si))

    suspend fun publicar(api: Api) { api.hacer("POST", "/v1/web/publicar") }
    suspend fun descartar(api: Api) { api.hacer("DELETE", "/v1/web/borrador") }

    @Serializable private data class T(val token: String)

    /** La dirección de la vista previa: siempre el subdominio (con dominio propio la cookie sería de
     *  terceros), con un token de 15 minutos. */
    suspend fun urlPrevia(api: Api, slug: String): String {
        val t: T = api.enviar("POST", "/v1/web/previa", emptyMap<String, String>())
        val host = runCatching { android.net.Uri.parse(Api.web()).host }.getOrNull() ?: "corredor.com.ar"
        return "https://$slug.$host/?previa=${t.token}"
    }
}

/** Un texto de la marca ("" si no está). */
fun Map<String, JsonElement>.texto(k: String): String = this[k].texto ?: ""

/** Pone un texto (vacío lo saca). */
fun MutableMap<String, JsonElement>.poner(k: String, v: String) {
    val t = v.trim()
    if (t.isEmpty()) remove(k) else this[k] = JsonPrimitive(t)
}

/** La web de la inmobiliaria con el borrador, dentro de la app. Tocar una zona marcada (logo, portada,
 *  frase, una sección, el contacto) avisa cuál (`alEditar`) en vez de navegar. `recarga` cambia cada
 *  vez que se guarda el borrador, para volver a pedirla. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebPrevia(url: String?, recarga: Int, alEditar: (String) -> Unit, modifier: Modifier = Modifier) {
    val editar by rememberUpdatedState(alEditar)
    val estado = remember { arrayOfNulls<String>(1) } // la clave cargada (Coordinador.cargada)
    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                contentDescription = "Vista previa de tu web"
                webViewClient = WebViewClient()
                // window.webkit.messageHandlers.corredor.postMessage({editar}) de iOS: la web llama a
                // `corredor.postMessage(JSON)` en Android.
                addJavascriptInterface(object {
                    @JavascriptInterface
                    fun postMessage(m: String) {
                        val zona = runCatching { JSONObject(m).optString("editar") }.getOrNull()?.takeIf { it.isNotEmpty() } ?: return
                        post { Haptico.seleccion(); editar(zona) }
                    }
                }, "corredor")
                // Sin datos guardados entre visitas (websiteDataStore = .nonPersistent()).
                android.webkit.CookieManager.getInstance().removeAllCookies(null)
            }
        },
        modifier = modifier,
        update = { w ->
            val u = url ?: return@AndroidView
            val clave = "$u#$recarga"
            if (estado[0] != clave) {
                // El token va sólo la primera vez (queda en la cookie); después alcanza con recargar.
                if (estado[0]?.startsWith(u) == true && w.url != null) w.reload() else w.loadUrl(u)
                estado[0] = clave
            }
        },
    )
}

/** La vista previa dentro de un marco de teléfono, con la etiqueta «Tocá lo que quieras cambiar». */
@Composable
fun MarcoPrevia(url: String?, recarga: Int, alto: Dp = 520.dp, alEditar: (String) -> Unit) {
    val forma = RoundedCornerShape(26.dp)
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier.fillMaxWidth().height(alto).sombra(forma, Color.Black.copy(alpha = 0.18f), 16.dp, y = 10.dp)
                .clip(forma).border(6.dp, C.texto.copy(alpha = 0.85f), forma),
            contentAlignment = Alignment.Center,
        ) {
            if (url == null) Rueda()
            WebPrevia(url, recarga, alEditar, Modifier.fillMaxSize().padding(6.dp))
        }
        ConIcono("Tocá lo que quieras cambiar", "hand.tap", Estilo.footnote, C.texto2)
    }
}

/** Elegir y subir una imagen de la marca (logo o portada): galería o cámara, recortada y comprimida.
 *  Devuelve la URL pública y, para el logo, sus colores (para sugerirlos). */
@Composable
fun SubirImagenMarca(titulo: String, url: String?, ancha: Boolean = false, alSubir: (String, List<Int>) -> Unit) {
    val estado = LocalEstado.current
    val ctx = LocalContext.current
    val alcance = rememberCoroutineScope()
    var subiendo by remember { mutableStateOf(false) }
    val elegir = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        alcance.launch {
            subiendo = true
            try {
                val d = withContext(Dispatchers.IO) { runCatching { ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull() }
                val img = d?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
                if (d == null || img == null) { estado.avisar("No pudimos leer la imagen.", ok = false); return@launch }
                // El logo va en PNG (puede tener transparencia); la portada, en JPG achicada.
                val (datos, nombre, tipo) = withContext(Dispatchers.Default) {
                    if (ancha) Triple(achicada(img, 2000).comprimir(Bitmap.CompressFormat.JPEG, 85), "portada.jpg", "image/jpeg")
                    else Triple(achicada(img, 1000).comprimir(Bitmap.CompressFormat.PNG, 100), "logo.png", "image/png")
                }
                try {
                    val a = estado.api.subir(datos, nombre, tipo, "marca")
                    val u = a.url ?: return@launch
                    alSubir(u, if (ancha) emptyList() else withContext(Dispatchers.Default) { coloresMarca(img) })
                } catch (e: CancellationException) { throw e } catch (e: Exception) { estado.avisar(e) }
            } finally { subiendo = false }
        }
    }
    val forma = RoundedCornerShape(M.rTarjeta)
    val linea = C.linea2
    Column(
        Modifier.fillMaxWidth().heightIn(min = if (ancha) 220.dp else 200.dp).clip(forma).background(C.superficie)
            .drawBehind {
                drawRoundRect(linea, style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx()))),
                    cornerRadius = CornerRadius(M.rTarjeta.toPx()))
            }
            .clickable(enabled = !subiendo) { elegir.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
    ) {
        if (url != null && url.startsWith("http")) {
            SubcomposeAsyncImage(
                url, null,
                (if (ancha) Modifier.fillMaxWidth().heightIn(max = 170.dp) else Modifier.widthIn(max = 150.dp).heightIn(max = 110.dp)).clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Fit, loading = { Rueda() },
            )
        } else {
            Icono(if (ancha) "photo.on.rectangle.angled" else "photo.badge.plus", 38f, C.accion, peso = Peso.light)
        }
        Texto(if (subiendo) "Subiendo…" else (if (url == null) titulo else "Cambiar"), Estilo.filaLista, C.texto, alinear = TextAlign.Center)
        Texto(if (ancha) "Una foto horizontal y luminosa" else "JPG o PNG, con fondo transparente si tenés", Estilo.footnote, C.texto2, alinear = TextAlign.Center)
    }
}

private fun Bitmap.comprimir(f: Bitmap.CompressFormat, q: Int): ByteArray = ByteArrayOutputStream().also { compress(f, q, it) }.toByteArray()

/** La imagen con su lado más largo en `lado` píxeles como mucho (UIImage.achicada). */
fun achicada(img: Bitmap, lado: Int): Bitmap {
    val m = max(img.width, img.height)
    if (m <= lado) return img
    return Bitmap.createScaledBitmap(img, img.width * lado / m, img.height * lado / m, true)
}

/** Los colores de un logo, para sugerirlos (la misma cuenta que el panel: web/src/lib/web/colores.ts):
 *  se agrupan los píxeles parecidos, sin casi blancos, casi negros ni grises, y se descartan los
 *  parecidos a uno ya elegido. El más usado primero. */
fun coloresMarca(img: Bitmap, cuantos: Int = 4): List<Int> {
    val lado = 48
    val chica = Bitmap.createScaledBitmap(img, lado, lado, true)
    val px = IntArray(lado * lado)
    chica.getPixels(px, 0, lado, 0, 0, lado, lado)
    data class G(var n: Double, var r: Double, var v: Double, var a: Double)
    val grupos = LinkedHashMap<Int, G>()
    for (c in px) {
        val alfa = (c ushr 24) and 0xFF
        if (alfa < 128) continue
        // Android da los colores sin premultiplicar.
        val r = ((c shr 16) and 0xFF).toDouble(); val v = ((c shr 8) and 0xFF).toDouble(); val a = (c and 0xFF).toDouble()
        val mx = max(r, max(v, a)); val mn = min(r, min(v, a))
        if ((mx > 240 && mn > 225) || mx < 22 || mx - mn < 24) continue
        val k = ((r.toInt() shr 5) shl 6) or ((v.toInt() shr 5) shl 3) or (a.toInt() shr 5)
        val x = grupos.getOrPut(k) { G(0.0, 0.0, 0.0, 0.0) }
        x.n += 1; x.r += r; x.v += v; x.a += a
    }
    val elegidos = mutableListOf<Triple<Double, Double, Double>>()
    for (x in grupos.values.sortedByDescending { it.n }) {
        val c = Triple(x.r / x.n, x.v / x.n, x.a / x.n)
        if (elegidos.any { sqrt((it.first - c.first).pow(2) + (it.second - c.second).pow(2) + (it.third - c.third).pow(2)) < 70 }) continue
        elegidos.add(c)
        if (elegidos.size == cuantos) break
    }
    return elegidos.map { Colores.hex(it) }
}

/** «#1e3a5f» (UInt32.hexColor). */
val Int.hexColor: String get() = "#%06x".format(this and 0xFFFFFF)

/** Elegir un color de la marca: los sugeridos (los del logo primero) y el selector del sistema. */
@Composable
fun ElegirColor(titulo: String, sugeridos: List<Int>, hex: String, alCambiar: (String) -> Unit) {
    // Sin repetidos (el del logo puede coincidir con uno de los de siempre).
    val unicos = sugeridos.distinct()
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Texto(titulo, Estilo.filaLista, C.texto)
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            unicos.forEach { c ->
                val elegido = hex.lowercase() == c.hexColor
                Box(
                    Modifier.size(50.dp).clip(CircleShape).border(2.5.dp, if (elegido) C.texto else Color.Transparent, CircleShape)
                        .clickable { alCambiar(c.hexColor); Haptico.seleccion() }
                        .semantics { contentDescription = "Usar el color ${c.hexColor}"; selected = elegido }.padding(3.dp),
                ) {
                    Box(Modifier.fillMaxSize().clip(CircleShape).background(Colores.color(c)).border(1.dp, Color.Black.copy(alpha = 0.1f), CircleShape))
                }
            }
            // ColorPicker("Otro color").labelsHidden(): la muestra; al tocarla, el selector.
            SelectorDeColor("Otro color", Colores.rgb(hex)?.hexColor ?: 0x16443B.hexColor, soloMuestra = true) { alCambiar(it.lowercase()) }
        }
    }
}

/** Lo que se abre al tocar una parte de la web en la vista previa: el logo, la portada, la frase, el
 *  contacto o «nosotros». Cambia la marca de Mi web; `alListo` la guarda en el borrador. */
@Composable
fun EditorZonaWeb(zona: String, marca: Map<String, String>, alCambiar: (Map<String, String>) -> Unit, alListo: () -> Unit, cerrar: () -> Unit) {
    val titulo = mapOf("logo" to "Tu logo", "portada" to "La portada", "frase" to "La frase de la portada", "contacto" to "Contacto", "nosotros" to "Nosotros")[zona] ?: "Tu web"
    fun poner(k: String, v: String) = alCambiar(marca + (k to v))
    @Composable
    fun campo(k: String, placeholder: String, teclado: KeyboardType = KeyboardType.Text, lineas: Int = 1, lineasMaximas: Int = lineas) {
        FilaLista {
            Entrada(marca[k] ?: "", { poner(k, it) }, placeholder, Modifier.weight(1f), estilo = Estilo.body16, teclado = teclado,
                lineas = lineas, lineasMaximas = lineasMaximas,
                mayusculas = if (teclado == KeyboardType.Email) KeyboardCapitalization.None else KeyboardCapitalization.Sentences)
        }
    }
    Column(Modifier.fillMaxSize()) {
        BarraHoja(titulo, derecha = { BotonHoja("Listo", fuerte = true) { alListo(); cerrar() } })
        ListaAgrupada {
            when (zona) {
                "logo" -> SubirImagenMarca("Subí tu logo", marca["logoUrl"]) { u, _ -> alCambiar(marca + ("logoUrl" to u)); alListo(); cerrar() }
                "portada" -> {
                    SubirImagenMarca("Subí la foto de portada", marca["portadaUrl"], ancha = true) { u, _ -> alCambiar(marca + ("portadaUrl" to u)); alListo(); cerrar() }
                    SeccionLista("Frase") { campo("frase", "Tu próximo hogar en Palermo", lineas = 2, lineasMaximas = 3) }
                }
                "frase" -> SeccionLista { campo("frase", "Tu próximo hogar en Palermo", lineas = 2, lineasMaximas = 4) }
                "contacto" -> SeccionLista {
                    campo("whatsapp", "WhatsApp", KeyboardType.Phone); Separador()
                    campo("telefono", "Teléfono", KeyboardType.Phone); Separador()
                    campo("email", "Mail", KeyboardType.Email); Separador()
                    campo("direccion", "Dirección"); Separador()
                    campo("horario", "Horario")
                }
                "nosotros" -> SeccionLista {
                    campo("nosotrosTitulo", "Título"); Separador()
                    campo("nosotros", "Quiénes son, desde cuándo, en qué barrios trabajan.", lineas = 3, lineasMaximas = 10)
                }
                else -> SeccionLista { FilaLista { Texto("Esta parte se cambia en Mi web, más abajo.", Estilo.body, C.texto2) } }
            }
        }
    }
}

// MARK: - Colores (Acento y ColorPicker)

/** Las cuentas de color de `Acento` (rgb, hex, contraste, mezclar) sobre enteros 0xRRGGBB: las de tema/Acento.kt. */
object Colores {
    fun rgb(texto: String?): Int? = Acento.rgb(texto)?.let { Acento.hex(it) }
    fun canales(n: Int) = Acento.rgb(n).let { Triple(it.r, it.g, it.b) }
    fun hex(c: Triple<Double, Double, Double>): Int = Acento.hex(Acento.Rgb(c.first, c.second, c.third))
    fun luminancia(n: Int): Double = Acento.luminancia(Acento.rgb(n))
    fun contraste(a: Int, b: Int): Double = Acento.contraste(a, b)
    fun mezclar(a: Int, b: Int, t: Double): Int = Acento.mezclar(a, b, t)
    fun color(n: Int): Color = ar.com.corredor.app.tema.hex(n)
}

