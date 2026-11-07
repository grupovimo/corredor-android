package ar.com.corredor.app.pantallas.propiedades

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ar.com.corredor.app.app.BarraDe
import ar.com.corredor.app.app.LocalEstado
import ar.com.corredor.app.nucleo.*
import ar.com.corredor.app.pantallas.cartera.Contacto
import ar.com.corredor.app.tema.*
import coil3.compose.AsyncImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.time.Instant

// Clon de Pantallas/Propiedades/FichaHojas.swift.

@Serializable private data class CuerpoEnvioFicha(val contactoId: String, val canal: String = "whatsapp")
@Serializable private data class EnvioFicha(val id: String, val enviadaEn: FechaApi? = null)

/** 3F Mandar la ficha (web: la hoja «Mandar {calle}»): a la persona que vino de su ficha, a los que buscan algo así
 *  (con «coincide 87 %» o «Se la mandaste hace 2 d»), a cualquier otro contacto, o a los tres primeros de una
 *  («Mandar a 3», uno detrás de otro). Cada envío queda anotado, con deshacer. Además: copiar el link, la hoja del
 *  sistema, la ficha A4 y la tarjeta para la visita. */
@Composable
fun HojaCompartir(
    propiedad: Propiedad,
    web: String?,
    interesados: List<Interesado> = emptyList(),
    /** La persona para la que se abrió (el «Mandar» de la ficha de un contacto). */
    destinatario: Contacto? = null,
) {
    val estado = LocalEstado.current
    val ctx = LocalContext.current
    val cerrar = LocalCerrarHoja.current
    val alcance = rememberCoroutineScope()
    var lista by remember { mutableStateOf<List<Interesado>>(emptyList()) }
    var q by remember { mutableStateOf("") }
    var encontrados by remember { mutableStateOf<List<Contacto>>(emptyList()) }
    val mandadas = remember { mutableStateMapOf<String, Instant>() }
    /** «Mandar a 3»: los que faltan, de a uno (WhatsApp abre de a una conversación). */
    var enCola by remember { mutableStateOf<List<Interesado>>(emptyList()) }
    var previo by remember { mutableStateOf<ArchivoPrevio?>(null) }

    val link = "${web ?: ""}/propiedades/${propiedad.slug ?: propiedad.id}"
    val calle = (propiedad.direccion ?: propiedad.nombre).split(",").firstOrNull() ?: propiedad.nombre
    fun enviada(i: Interesado): Instant? = mandadas[i.contactoId] ?: i.enviadaEn
    val sinMandar = lista.filter { enviada(it) == null }
    val texto = "${propiedad.nombre} · ${propiedad.precioTexto}\n${propiedad.resumen}"

    fun copiarLink() {
        copiar(ctx, link)
        estado.avisar("Link copiado. Pegalo donde quieras mandarlo.")
    }

    suspend fun anotar(contactoId: String, primero: String) {
        try {
            val e: EnvioFicha = estado.api.enviar("POST", "/v1/propiedades/${propiedad.id}/envios", CuerpoEnvioFicha(contactoId))
            mandadas[contactoId] = e.enviadaEn ?: Instant.now()
            estado.version += 1
            estado.avisar("Anotamos que se la mandaste a $primero") {
                estado.tarea {
                    try {
                        estado.api.hacer("DELETE", "/v1/propiedades/${propiedad.id}/envios/${e.id}")
                        mandadas.remove(contactoId)
                        estado.version += 1
                    } catch (x: CancellationException) { throw x } catch (x: Exception) { estado.avisar(x) }
                }
            }
        } catch (x: CancellationException) {
            throw x
        } catch (x: ErrorApi) {
            // Sin señal, el envío se anota cuando vuelva (el WhatsApp igual salió).
            if (x.sinConexion) {
                try { estado.cola.enviar("POST", "/v1/propiedades/${propiedad.id}/envios", CuerpoEnvioFicha(contactoId), etiqueta = "Ficha a $primero") } catch (y: CancellationException) { throw y } catch (y: Exception) {}
            }
        } catch (x: Exception) {
            // Si no se anota, el WhatsApp igual salió.
        }
    }

    /** WhatsApp con el link, y el envío anotado (con deshacer, por si no se llegó a mandar). */
    fun mandar(nombre: String, telefono: String?, contactoId: String?) {
        val primero = nombre.split(" ").firstOrNull() ?: nombre
        Afuera.whatsapp(telefono, texto = "Hola $primero, te paso esta propiedad que te puede interesar: ${propiedad.direccion ?: propiedad.nombre} · ${propiedad.precioTexto}\n$link")
        if (contactoId == null) return
        alcance.launch { anotar(contactoId, primero) }
    }

    fun mandarSiguiente() {
        val c = enCola.firstOrNull() ?: return
        enCola = enCola.drop(1)
        mandar(c.nombre, c.telefono, c.contactoId)
    }

    fun mandarATodos() {
        enCola = sinMandar.take(3)
        mandarSiguiente()
    }

    LaunchedEffect(Unit) {
        lista = interesados
        if (lista.isEmpty()) {
            lista = estado.api.opcional<List<Interesado>>("/v1/propiedades/${propiedad.id}/interesados", listOf("sinVisita" to "si", "limite" to "20")) ?: emptyList()
        }
    }
    LaunchedEffect(q) {
        if (q.trim().length < 2) { encontrados = emptyList(); return@LaunchedEffect }
        delay(250)
        encontrados = estado.api.opcional<List<Contacto>>("/v1/contactos", listOf("limite" to "6", "q" to q)) ?: emptyList()
    }

    Column(
        Modifier.fillMaxSize().background(C.hundida).verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Cabecera
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Foto(url = propiedad.miniatura, modifier = Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)))
            Column(Modifier.weight(1f)) {
                Texto("Mandar $calle", Estilo.subheadFuerte, C.texto, lineas = 1)
                Texto("Le llega por WhatsApp con el link de tu web.", Estilo.footnote, C.texto2, lineas = 2)
            }
            Tocable({ cerrar() }, descripcion = "Cerrar") {
                Box(Modifier.size(32.dp).clip(CircleShape).background(C.superficie), contentAlignment = Alignment.Center) {
                    Icono("xmark", 14f, C.texto2)
                }
            }
        }
        if (destinatario != null) {
            val d = destinatario
            TarjetaCompartir {
                FilaContactoCompartir(d.nombre, mandadas[d.id]?.let { "Se la mandaste ${Formato.hace(it)}" } ?: (d.telefono ?: "sin teléfono"), primera = true) {
                    mandar(d.nombre, d.telefono, d.id)
                }
            }
        }
        enCola.firstOrNull()?.let { siguiente ->
            BotonPrincipal(
                "Seguí con ${siguiente.nombre.split(" ").firstOrNull() ?: siguiente.nombre} (${enCola.size} más)",
                { mandarSiguiente() }, simbolo = "arrow.right.circle.fill",
            )
        }
        if (lista.isNotEmpty()) {
            Texto("BUSCAN ALGO ASÍ", Estilo(13f, Peso.bold, 0.5f / 13f), C.texto2)
            TarjetaCompartir {
                lista.take(5).forEachIndexed { i, c ->
                    val det = enviada(c)?.let { "Se la mandaste ${Formato.hace(it)}" }
                        ?: "${c.telefono ?: "sin teléfono"} · coincide ${Formato.porcentaje(Math.round((c.puntaje ?: 0.0) * 100).toDouble())}"
                    FilaContactoCompartir(c.nombre, det, primera = i == 0) { mandar(c.nombre, c.telefono, c.contactoId) }
                }
            }
        }
        Buscador("Buscar otro contacto", q, { q = it })
        if (encontrados.isNotEmpty()) {
            TarjetaCompartir {
                encontrados.forEachIndexed { i, c ->
                    FilaContactoCompartir(c.nombre, mandadas[c.id]?.let { "Se la mandaste ${Formato.hace(it)}" } ?: (c.telefono ?: "sin teléfono"), primera = i == 0) {
                        mandar(c.nombre, c.telefono, c.id)
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BotonHundido("Copiar link", { copiarLink() }, Modifier.weight(1f), simbolo = "link")
            if (sinMandar.size > 1) {
                BotonHundido("Mandar a ${minOf(3, sinMandar.size)}", { mandarATodos() }, Modifier.weight(1f), simbolo = "square.and.arrow.up")
            }
        }
        // Las apps
        Row {
            BotonAppCompartir("square.and.arrow.up", null, "Más", hex(0x8E8E93)) { estado.compartirTexto("$texto\n$link") }
            BotonAppCompartir(null, "logo_whatsapp", "WhatsApp", hex(0x25D366)) { Afuera.whatsapp(null, texto = texto + "\n" + link) }
            BotonAppCompartir("envelope.fill", null, "Mail", hex(0x1D7CF2)) {
                abrirUrl(ctx, "mailto:?subject=${Uri.encode(propiedad.nombre)}&body=$link")
            }
            Spacer(Modifier.weight(1f))
        }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.superficie)) {
            FilaHojaCompartir("Imprimir ficha A4", "printer") {
                alcance.launch { FichaImpresa.a4(propiedad, api = estado.api)?.let { previo = ArchivoPrevio(it) } }
            }
            Box(Modifier.fillMaxWidth().height(0.5.dp).background(C.linea))
            FilaHojaCompartir("Tarjeta para dejar en la visita", "rectangle.portrait.on.rectangle.portrait") {
                cerrar()
                estado.abrir(ATarjetaPropiedad(id = propiedad.id))
            }
        }
    }
    VistaPrevia(previo) { previo = null }
}

@Composable
private fun TarjetaCompartir(contenido: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.superficie), content = contenido)
}

@Composable
private fun FilaContactoCompartir(nombre: String, detalle: String, primera: Boolean, hacer: () -> Unit) {
    Tocable(hacer, Modifier.fillMaxWidth()) {
        Column {
            if (!primera) Box(Modifier.fillMaxWidth().height(0.5.dp).background(C.linea))
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AvatarIniciales(nombre, tam = 36.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Texto(nombre, Estilo.filaLista, C.texto, lineas = 1)
                    Texto(detalle, Estilo.footnote, C.texto2, lineas = 1)
                }
                Icono("bubble.left.fill", 17f, C.okTinta)
            }
        }
    }
}

@Composable
private fun BotonAppCompartir(icono: String?, logo: String?, texto: String, color: Color, hacer: () -> Unit) {
    Column(Modifier.width(72.dp).clickable(onClick = hacer), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(60.dp).clip(RoundedCornerShape(15.dp)).background(color), contentAlignment = Alignment.Center) {
            if (icono != null) Icono(icono, 26f, Color.White)
            else if (!LogoDeLaApp(logo ?: "", Modifier.width(34.dp), tinte = Color.White)) Icono("bubble.left.fill", 26f, Color.White)
        }
        Texto(texto, Estilo(12f, Peso.medium), C.texto)
    }
}

@Composable
private fun FilaHojaCompartir(t: String, icono: String, hacer: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(48.dp).clickable(onClick = hacer).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Texto(t, Estilo.body16, C.texto, Modifier.weight(1f))
        Icono(icono, 17f, C.texto)
    }
}

// PARA EL NÚCLEO: los logos del bundle de iOS (LogoWhatsApp, LogoMercadoLibre, IconoRedondo) como drawables
// (logo_whatsapp, logo_mercadolibre, icono_redondo). Se buscan por nombre para que compile aunque falten; devuelve
// si lo dibujó.
@SuppressLint("DiscouragedApi")
@Composable
internal fun LogoDeLaApp(nombre: String, modifier: Modifier = Modifier, tinte: Color? = null, recorte: ContentScale = ContentScale.Fit): Boolean {
    val ctx = LocalContext.current
    val id = remember(nombre) { ctx.resources.getIdentifier(nombre, "drawable", ctx.packageName) }
    if (id == 0) return false
    Image(painterResource(id), null, modifier, contentScale = recorte, colorFilter = tinte?.let { ColorFilter.tint(it) })
    return true
}

/** 9C·err El error de un portal, explicado y con el arreglo: si falta la superficie o el precio, se carga ahí y
 *  «Guardar y volver a publicar»; si son las fotos, «Sumar fotos»; si no, «Editar la ficha». */
@Composable
fun HojaArreglar(
    propiedad: Propiedad,
    pub: PublicacionPortal,
    /** Los otros lugares donde está publicada (se actualizan también). */
    otros: List<String>,
) {
    val estado = LocalEstado.current
    val cerrar = LocalCerrarHoja.current
    val alcance = rememberCoroutineScope()
    var valor by remember { mutableStateOf("") }
    var guardando by remember { mutableStateOf(false) }

    val campo: String? = run {
        val e = (pub.error ?: "").lowercase()
        when {
            Regex("superficie|area|surface").containsMatchIn(e) -> "superficie"
            Regex("precio|price").containsMatchIn(e) -> "precio"
            Regex("foto|picture|imagen").containsMatchIn(e) -> "fotos"
            else -> null
        }
    }
    val partes: Pair<String, String> = run {
        val e = pub.error ?: ""
        val i = e.indexOf(". ")
        if (i >= 0) (e.substring(0, i) + ".") to e.substring(i + 2) else e to ""
    }

    suspend fun arreglar() {
        guardando = true
        try {
            if (campo == "superficie" || campo == "precio") {
                val n = valor.replace(".", "").replace(",", ".").toDoubleOrNull() ?: 0.0
                if (n <= 0) {
                    estado.avisar(if (campo == "superficie") "Poné la superficie total en m²." else "Poné el precio.", ok = false)
                    return
                }
                var p: Propiedad = estado.api.get("/v1/propiedades/${propiedad.id}")
                p = if (campo == "superficie") p.copy(superficie = n) else p.copy(precio = n)
                estado.api.hacer("PUT", "/v1/propiedades/${p.id}", p)
            }
            estado.api.hacer("POST", "/v1/propiedades/${propiedad.id}/publicaciones/${pub.portal}")
            estado.version += 1
            cerrar()
            estado.avisar("Guardada y mandada de nuevo")
        } catch (e: CancellationException) { throw e } catch (e: Exception) { estado.avisar(e) } finally { guardando = false }
    }

    Column(Modifier.fillMaxSize().padding(start = 20.dp, end = 20.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Grabber() }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            IconoTono("exclamationmark.triangle", Tono.rojo)
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Texto("${pub.nombre} no la publicó", Estilo.tituloHojaChico, C.texto)
                Texto("${propiedad.direccion ?: propiedad.nombre}${pub.actualizadoEn?.let { " · ${Formato.hace(it)}" } ?: ""}", Estilo.footnote, C.texto2)
            }
        }
        TextoRico(
            buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(partes.first) }
                append(" " + partes.second)
                append(if (campo == "superficie" && propiedad.cubierta != null) " Tenés cargados ${Formato.numero((propiedad.cubierta ?: 0.0).toInt())} m² cubiertos." else "")
            },
            Estilo.subhead, C.texto,
        )
        when (campo) {
            "superficie" -> Campo("Superficie total") {
                Entrada(valor, { valor = it }, propiedad.cubierta?.let { Math.round(it * 1.08).toString() } ?: "50", Modifier.weight(1f), teclado = KeyboardType.Decimal)
                Texto("m²", Estilo.body, C.texto2)
            }
            "precio" -> Campo("Precio") { Entrada(valor, { valor = it }, "", Modifier.weight(1f), teclado = KeyboardType.Number) }
            "fotos" -> BotonHundido("Sumar fotos", { cerrar(); estado.abrir(AEditarEnPaso(id = propiedad.id, paso = "fotos")) }, simbolo = "camera")
            else -> BotonHundido("Editar la ficha", { cerrar(); estado.abrir(AAltaPropiedad(editar = propiedad.id)) }, simbolo = "pencil")
        }
        if (otros.isNotEmpty()) Texto("También se actualiza en ${Cartera.lista(otros)}.", Estilo.footnote, C.texto2)
        Spacer(Modifier.weight(1f))
        BotonPrincipal(
            if (guardando) "Guardando…" else (if (campo == "superficie" || campo == "precio") "Guardar y volver a publicar" else "Volver a publicar"),
            { alcance.launch { arreglar() } }, habilitado = !guardando,
        )
    }
}

/** El logo de un portal (la rr para la web). */
@Composable
fun LogoPortal(portal: String, modifier: Modifier = Modifier) {
    val forma = RoundedCornerShape(8.dp)
    Box(
        modifier.size(32.dp).clip(forma).background(Color.White).border(0.5.dp, Color.Black.copy(alpha = 0.1f), forma),
        contentAlignment = Alignment.Center,
    ) {
        val dibujado = when (portal) {
            "web" -> LogoDeLaApp("icono_redondo", Modifier.fillMaxSize())
            "mercadolibre" -> LogoDeLaApp("logo_mercadolibre", Modifier.fillMaxSize().padding(3.dp))
            else -> false
        }
        if (!dibujado) Texto(portal.take(1).uppercase(), Estilo.headline, C.verde700)
    }
}

/** 3E Galería: fotos a pantalla completa, deslizables, con la tira de miniaturas. */
@Composable
fun Galeria(id: String, indice: Int = 0) {
    val estado = LocalEstado.current
    val cerrar = LocalCerrarHoja.current
    val alcance = rememberCoroutineScope()
    var fotos by remember { mutableStateOf<List<String>>(emptyList()) }
    val paginas = rememberPagerState(initialPage = indice) { fotos.size }
    val tira = rememberLazyListState()
    val actual = paginas.currentPage

    BarraDe(oculta = true)
    LaunchedEffect(Unit) {
        estado.api.opcional<Propiedad>("/v1/propiedades/$id")?.let { fotos = it.fotosUrl ?: emptyList() }
    }
    LaunchedEffect(actual, fotos.size) {
        if (fotos.isNotEmpty()) tira.animateScrollToItem(maxOf(0, actual - 2))
    }

    Column(Modifier.fillMaxSize().background(Color.Black).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Tocable({ cerrar() }, descripcion = "Cerrar") {
                Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) { Icono("xmark", 20f, Color.White) }
            }
            Spacer(Modifier.weight(1f))
            Texto(if (fotos.isEmpty()) "" else "${actual + 1} de ${fotos.size}", Estilo(16f, Peso.bold), Color.White)
            Spacer(Modifier.weight(1f))
            val u = fotos.getOrNull(actual)
            if (u != null) {
                Tocable({ estado.compartirTexto(u) }, descripcion = "Compartir") {
                    Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) { Icono("square.and.arrow.up", 19f, Color.White) }
                }
            } else Spacer(Modifier.size(44.dp))
        }
        HorizontalPager(paginas, Modifier.weight(1f).fillMaxWidth()) { i ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Rueda(Color.White)
                AsyncImage(fotos[i], null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            }
        }
        LazyRow(
            state = tira, modifier = Modifier.padding(top = 12.dp, bottom = 40.dp),
            contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            itemsIndexed(fotos) { i, f ->
                val forma = RoundedCornerShape(8.dp)
                Box(
                    Modifier.size(60.dp, 52.dp).alpha(if (i == actual) 1f else 0.55f).clip(forma).background(Color.White.copy(alpha = 0.1f))
                        .border(2.dp, if (i == actual) Color.White else Color.Transparent, forma)
                        .clickable { alcance.launch { paginas.animateScrollToPage(i) } },
                ) {
                    AsyncImage(f + (if (f.contains("?")) "&" else "?") + "tam=chica", null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                }
            }
        }
    }
}

/** `array[safe: i]` de iOS. */
fun <T> List<T>.seguro(i: Int): T? = getOrNull(i)

