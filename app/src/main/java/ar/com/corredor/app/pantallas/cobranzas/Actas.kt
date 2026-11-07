package ar.com.corredor.app.pantallas.cobranzas

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ar.com.corredor.app.app.*
import ar.com.corredor.app.nucleo.*
import ar.com.corredor.app.pantallas.mensajes.AudioMensaje
import ar.com.corredor.app.pantallas.propiedades.Propiedad
import ar.com.corredor.app.tema.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.time.Instant

/** Un ítem del inventario de una propiedad. */
@Serializable
data class ItemInventario(
    val id: String,
    val ambiente: String,
    val descripcion: String,
    val cantidad: Int? = null,
    val estado: String,
    val notas: String? = null,
    val fotos: List<String>? = null,
    val fotosUrl: List<String>? = null,
) {
    companion object {
        val estados = listOf("nuevo" to "Nuevo", "muy_bueno" to "Muy bueno", "bueno" to "Bueno", "regular" to "Regular", "malo" to "Malo")
    }
}

/** Un acta con sus firmas. La API la llama «entrada» (entrega) o «salida» (devolución). */
@Serializable
data class Acta(
    val id: String,
    val propiedadId: String? = null,
    val contratoId: String? = null,
    val tipo: String? = null,
    val fecha: String? = null,
    val items: List<Item>? = null,
    val observaciones: String? = null,
    val firmas: Map<String, Firma>? = null,
    val propiedadTitulo: String? = null,
    val creadoEn: String? = null,
) {
    @Serializable
    data class Firma(val nombre: String? = null, val trazo: String? = null, val en: String? = null)

    /** Un ítem tal como quedó congelado al labrar el acta. */
    @Serializable
    data class Item(
        val ambiente: String,
        val descripcion: String,
        val cantidad: Int? = null,
        val estado: String,
        val notas: String? = null,
        val fotos: List<String>? = null,
        val fotosUrl: List<String>? = null,
    )

    val esSalida: Boolean get() = tipo == "salida"
    val tipoTexto: String get() = if (esSalida) "Devolución" else "Entrega"
}

/** 11A Recorrida por ambientes (web 14C): un ambiente por pantalla, el estado de cada ítem de un toque,
 *  la foto de cada ítem con la cámara y la nota de voz manteniendo el micrófono. Cada cambio se guarda
 *  solo; al final se labra el acta de entrega o, si ya hay entrada y falta la salida, se compara la
 *  devolución con la entrega. */
@Composable
fun Inventario(
    propiedad: String,
    /** «entrega» o «devolucion» para forzar el modo (web ?tipo=); null decide solo. */
    tipoPedido: String? = null,
) {
    val estado = LocalEstado.current
    val ctx = LocalContext.current
    val alcance = rememberCoroutineScope()
    val items = remember { mutableStateListOf<ItemInventario>() }
    var actas by remember { mutableStateOf<List<Acta>>(emptyList()) }
    var cargado by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<ErrorApi?>(null) }
    /** El contrato de la propiedad (el vigente o el último), para atar el acta de entrega. */
    var contrato by remember { mutableStateOf<ContratoAPI?>(null) }
    var prop by remember { mutableStateOf<Propiedad?>(null) }
    var ambiente by remember { mutableStateOf("") }
    /** El ítem elegido: la nota de voz va a ese. */
    var elegido by remember { mutableStateOf<String?>(null) }
    val subiendo = remember { mutableStateListOf<String>() }
    val durNota = remember { mutableStateMapOf<String, Int>() }
    var fotoDe by remember { mutableStateOf<String?>(null) }
    var camaraPara by remember { mutableStateOf<String?>(null) }
    var sumando by remember { mutableStateOf<String?>(null) }
    var nuevoItem by remember { mutableStateOf("") }
    var nuevoAmbiente by remember { mutableStateOf("") }
    var enviando by remember { mutableStateOf(false) }
    var labrando by remember { mutableStateOf(false) }
    val voz = remember { NotaDeVoz() }
    var apretando by remember { mutableStateOf(false) }
    var apretadoDesde by remember { mutableStateOf(0L) }
    var soltarIgnora by remember { mutableStateOf(false) }

    fun ambientes() = Inv.ambientes(items.map { it.ambiente })
    fun paso() = ambientes().indexOf(ambiente).let { if (it < 0) 0 else it }
    fun ultimo() = paso() >= ambientes().size - 1
    fun entrada() = actas.firstOrNull { it.tipo == "entrada" }
    fun salida() = actas.firstOrNull { it.tipo == "salida" }
    fun tipo(): String {
        if (tipoPedido == "entrega" || tipoPedido == "devolucion") return tipoPedido
        return if (entrada() != null && salida() == null) "devolucion" else "entrega"
    }
    fun delAmbiente() = items.filter { it.ambiente == ambiente }
    fun direccion() = prop?.direccion ?: prop?.nombre ?: ""
    fun textoSeguir(): String {
        if (!ultimo()) return "Seguir: ${ambientes()[paso() + 1]}"
        return if (tipo() == "devolucion") "Comparar con la entrega" else "Labrar el acta de entrega"
    }

    suspend fun cargar() {
        try {
            val p: Propiedad = estado.api.get("/v1/propiedades/$propiedad")
            val l: List<ItemInventario> = estado.api.get("/v1/propiedades/$propiedad/inventario")
            val a: List<Acta> = estado.api.get("/v1/propiedades/$propiedad/actas")
            prop = p
            items.clear(); items.addAll(l)
            actas = a
            error = null
            cargado = true
        } catch (e: CancellationException) { throw e } catch (e: ErrorApi) {
            if (!cargado) error = e
            return
        } catch (e: Exception) { return }
        val ks: List<ContratoAPI> = estado.api.opcional("/v1/contratos", listOf("propiedad" to propiedad, "limite" to "5")) ?: emptyList()
        contrato = ks.firstOrNull { it.estado == "vigente" } ?: ks.firstOrNull()
        if (ambiente.isEmpty() || ambiente !in ambientes()) ambiente = ambientes().firstOrNull() ?: ""
    }

    fun cuerpo(it: ItemInventario) = CuerpoItem(it.ambiente, it.descripcion, it.cantidad ?: 1, it.estado.ifEmpty { "bueno" },
        it.notas?.takeIf { n -> n.isNotBlank() }, it.fotos ?: emptyList())

    fun reemplazar(it: ItemInventario) {
        val i = items.indexOfFirst { x -> x.id == it.id }
        if (i >= 0) items[i] = it
    }

    /** Cambio optimista: se ve al toque; sin señal queda en la cola; si la API no lo guarda, vuelve
     *  atrás y ofrece «Reintentar». */
    suspend fun guardar(nuevo: ItemInventario, antes: ItemInventario) {
        reemplazar(nuevo)
        try {
            estado.cola.enviar("PUT", "/v1/propiedades/$propiedad/inventario/${nuevo.id}", cuerpo(nuevo), etiqueta = "Inventario de ${direccion()}")
        } catch (e: CancellationException) { throw e } catch (e: Exception) {
            reemplazar(antes)
            Inv.errorConReintento(estado, e) { alcance.launch { guardar(nuevo, antes) } }
        }
    }

    suspend fun poner(it: ItemInventario, nuevo: String) {
        elegido = it.id
        val actual = items.firstOrNull { x -> x.id == it.id } ?: return
        if (Inv.grupo(actual.estado) == nuevo) return
        val x = actual.copy(estado = nuevo)
        Haptico.seleccion()
        guardar(x, actual)
    }

    suspend fun guardarNotaEscrita(id: String) {
        val it = items.firstOrNull { x -> x.id == id } ?: return
        guardar(it, it.copy(notas = null))
    }

    suspend fun sumarFoto(d: ByteArray, id: String) {
        elegido = id
        subiendo.add(id)
        try {
            val f = estado.api.subir(d, nombre = "inventario.jpg", tipo = "image/jpeg", uso = "foto")
            val ahora = items.firstOrNull { it.id == id } ?: return
            val x = ahora.copy(fotos = (ahora.fotos ?: emptyList()) + f.id, fotosUrl = (ahora.fotosUrl ?: emptyList()) + (f.url ?: ""))
            guardar(x, ahora)
        } catch (e: CancellationException) { throw e } catch (e: ErrorApi) {
            if (e.sinConexion) estado.avisar("No se pudo subir la foto: no hay señal.", ok = false) else estado.avisar(e)
        } catch (e: Exception) { estado.avisar(e) } finally { subiendo.remove(id) }
    }

    suspend fun empezarNota() {
        val destino = elegido ?: delAmbiente().firstOrNull()?.id
        if (destino == null) {
            estado.avisar("Sumá un ítem primero: la nota va con un ítem.")
            return
        }
        elegido = destino
        voz.empezar(ctx, estado.alPedirPermiso)?.let { estado.avisar(it, ok = false) }
    }

    suspend fun terminarNota() {
        if (!voz.grabando) return
        val id = elegido
        val g = voz.terminar()
        if (id == null) return
        val i = items.firstOrNull { it.id == id } ?: return
        if (g.texto.isNotEmpty()) {
            durNota[id] = g.segundos
            guardar(i.copy(notas = g.texto.take(1000)), i)
        } else if (g.audio == null) {
            estado.avisar("No escuchamos nada. Probá de nuevo, más cerca.")
        }
        val u = g.audio ?: return
        try {
            val d = u.readBytes()
            val a = estado.api.subir(d, nombre = "nota.m4a", tipo = "audio/mp4", uso = "audio")
            estado.api.hacer("POST", "/v1/propiedades/$propiedad/grabaciones",
                GrabacionNota(if (g.texto.isEmpty()) "Nota de voz de ${i.descripcion}" else g.texto, a.id, g.segundos))
            durNota[id] = g.segundos
        } catch (e: CancellationException) { throw e } catch (e: Exception) {
            estado.avisar("El audio no se subió; la nota quedó escrita.")
        }
    }

    /** Sumar un ítem al ambiente que se ve o a uno nuevo; el aviso deja deshacerlo (lo borra). */
    suspend fun sumar(ambienteNuevo: Boolean) {
        val amb = if (ambienteNuevo || ambiente.isEmpty()) nuevoAmbiente.trim() else ambiente
        val desc = nuevoItem.trim()
        if (amb.isEmpty() || desc.isEmpty()) return
        enviando = true
        try {
            val it: ItemInventario = estado.api.enviar("POST", "/v1/propiedades/$propiedad/inventario", NuevoItem(amb, desc))
            items.add(it)
            nuevoItem = ""
            Haptico.exito()
            val api = estado.api
            val p = propiedad
            estado.avisar("${it.descripcion} sumado a $amb", deshacer = {
                alcance.launch {
                    try {
                        api.hacer("DELETE", "/v1/propiedades/$p/inventario/${it.id}")
                        items.removeAll { x -> x.id == it.id }
                    } catch (e: CancellationException) { throw e } catch (e: Exception) { estado.avisar(e) }
                }
            })
            if (amb != ambiente) {
                sumando = null
                nuevoAmbiente = ""
                ambiente = amb
            }
        } catch (e: CancellationException) { throw e } catch (e: Exception) { estado.avisar(e) } finally { enviando = false }
    }

    suspend fun terminar() {
        if (!ultimo()) {
            ambiente = ambientes()[paso() + 1]; elegido = null
            return
        }
        if (tipo() == "devolucion") {
            estado.abrir(ADevolucion(id = propiedad))
            return
        }
        labrando = true
        try {
            val a: Acta = estado.api.enviar("POST", "/v1/propiedades/$propiedad/actas", Labrar(contratoId = if (entrada() == null) contrato?.id else null))
            estado.version += 1
            estado.abrir(AFirmas(id = "$propiedad/${a.id}"))
        } catch (e: CancellationException) { throw e } catch (e: Exception) {
            Inv.errorConReintento(estado, e) { alcance.launch { terminar() } }
        } finally { labrando = false }
    }

    LaunchedEffect(estado.version) { cargar() }

    val fototeca = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val id = fotoDe
        if (uri != null && id != null) alcance.launch {
            val d = jpegDe(ctx, uri)
            if (d != null) sumarFoto(d, id)
        }
    }
    val camara = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { b ->
        val id = camaraPara
        camaraPara = null
        if (b != null && id != null) alcance.launch { sumarFoto(jpegDe(b), id) }
    }

    val alApretar by rememberUpdatedState {
        if (!apretando) {
            apretando = true
            apretadoDesde = System.currentTimeMillis()
            if (voz.grabando) {
                soltarIgnora = true
                alcance.launch { terminarNota() }
            } else {
                alcance.launch { empezarNota() }
            }
        }
    }
    val alSoltar by rememberUpdatedState {
        apretando = false
        if (soltarIgnora) soltarIgnora = false
        else if (System.currentTimeMillis() - apretadoDesde > 600) alcance.launch { terminarNota() }
    }

    PantallaApilada(if (tipo() == "devolucion") "Recorrida de devolución" else "Recorrida de entrega") {
        Column(Modifier.fillMaxSize()) {
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                val err = error
                if (!cargado && err != null) {
                    EstadoError("el inventario", err) { alcance.launch { cargar() } }
                } else if (!cargado) {
                    EsqueletoFilas(filas = 4)
                } else {
                    val ambs = ambientes()
                    // Cabeza
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Texto("${if (tipo() == "entrega") "Entrega" else "Devolución"} · ${direccion()}", Estilo.footnote, C.texto2, lineas = 1)
                                Texto(if (ambs.isEmpty()) "Primer ambiente" else ambiente, Estilo(22f, Peso.bold, -0.02f), C.texto)
                            }
                            if (ambs.isNotEmpty()) Texto("${paso() + 1} de ${ambs.size}", Estilo.footnote, C.texto2)
                        }
                        if (ambs.isNotEmpty()) {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                ambs.indices.forEach { k ->
                                    val p = paso()
                                    Box(Modifier.weight(1f).height(4.dp).clip(CircleShape).background(if (k < p) C.accion else if (k == p) C.oro else C.hundida))
                                }
                            }
                        }
                    }
                    if (ambs.isEmpty()) {
                        // Primer ambiente
                        Column(Modifier.fillMaxWidth().tarjeta().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Texto("Empezá por el primer ambiente", Estilo.subheadFuerte, C.texto)
                            Texto(Formato.sinViudas("Poné el ambiente y el primer ítem. Después sumás el resto caminando la casa."), Estilo.footnote, C.texto2)
                            if (estado.escribe) {
                                CampoCobro(nuevoAmbiente, { nuevoAmbiente = it }, "Ambiente (Cocina, Living…)", Modifier.fillMaxWidth())
                                CampoCobro(nuevoItem, { nuevoItem = it }, "Ítem (Anafe 4 hornallas)", Modifier.fillMaxWidth())
                                BotonPrincipal(if (enviando) "Sumando…" else "Sumar", { alcance.launch { sumar(ambienteNuevo = true) } },
                                    habilitado = !(enviando || nuevoAmbiente.isBlank() || nuevoItem.isBlank()))
                            }
                        }
                    } else {
                        // Chips de ambientes
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ambs.forEach { a ->
                                val n = items.count { it.ambiente == a }
                                val on = a == ambiente
                                Row(
                                    Modifier.height(36.dp).clip(CircleShape).background(if (on) C.accion else C.superficie)
                                        .clickable { ambiente = a; elegido = null }.padding(horizontal = 14.dp)
                                        .semantics { contentDescription = "$a, ${Formato.cantidad(n, "ítem", "ítems")}" },
                                    horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Texto(a, Estilo(14f, Peso.semibold), if (on) C.sobreAccion else C.texto)
                                    Texto("$n", Estilo(14f, Peso.semibold), if (on) C.sobreAccion.copy(alpha = 0.7f) else C.texto3)
                                }
                            }
                        }
                        // Lista de ítems
                        Column(Modifier.fillMaxWidth().tarjeta()) {
                            delAmbiente().forEachIndexed { i, it ->
                                if (i > 0) LineaFila()
                                FilaItemInventario(
                                    it, elegido == it.id, estado.escribe, subiendo.contains(it.id), voz, durNota[it.id],
                                    alElegir = { elegido = it.id },
                                    alSacarFoto = {
                                        elegido = it.id; camaraPara = it.id
                                        alcance.launch { if (estado.alPedirPermiso("camara")) camara.launch(null) else camaraPara = null }
                                    },
                                    alElegirFoto = {
                                        elegido = it.id; fotoDe = it.id
                                        fototeca.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                    },
                                    alPoner = { k -> alcance.launch { poner(it, k) } },
                                    alCambiarNota = { n -> val x = items.indexOfFirst { y -> y.id == it.id }; if (x >= 0) items[x] = items[x].copy(notas = n) },
                                    alTerminarNota = { alcance.launch { guardarNotaEscrita(it.id) } },
                                )
                            }
                        }
                        // Sumar
                        if (estado.escribe) {
                            val s = sumando
                            if (s != null) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (s == "ambiente") CampoCobro(nuevoAmbiente, { nuevoAmbiente = it }, "Ambiente nuevo (Baño de servicio)", Modifier.fillMaxWidth())
                                    CampoCobro(nuevoItem, { nuevoItem = it }, "Ítem (Alacena superior)", Modifier.fillMaxWidth())
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        BotonAccion(if (enviando) "Sumando…" else "Sumar", { alcance.launch { sumar(ambienteNuevo = s == "ambiente") } }, Modifier.weight(1f),
                                            habilitado = !(enviando || nuevoItem.isBlank() || (s == "ambiente" && nuevoAmbiente.isBlank())))
                                        BotonHundido("Cancelar", { sumando = null }, Modifier.weight(1f))
                                    }
                                }
                            } else {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    BotonHundido("Sumar ítem a $ambiente", { sumando = "item" }, Modifier.weight(1f), simbolo = "plus")
                                    BotonHundido("Otro ambiente", { sumando = "ambiente" }, Modifier.weight(1f))
                                }
                            }
                        }
                        Texto(Formato.sinViudas("Los ítems salen del inventario anterior de esta propiedad. Las fotos llevan fecha y hora."), Estilo.footnote, C.texto3)
                    }
                    // Actas
                    val ent = entrada(); val sal = salida()
                    if (actas.isNotEmpty() || (ent != null && sal == null)) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            TituloBloque("Actas")
                            Column(Modifier.fillMaxWidth().tarjeta()) {
                                actas.forEachIndexed { i, a ->
                                    val firmada = (a.firmas?.size ?: 0) >= 3
                                    if (i > 0) LineaFila()
                                    Row(
                                        Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable { estado.abrir(AFirmas(id = "$propiedad/${a.id}")) }.padding(horizontal = 14.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        IconoTono("doc.text", Tono.azul, 34.dp)
                                        Texto(if (a.tipo == "entrada") "Acta de entrega" else "Acta de devolución", Estilo.filaLista, C.texto, Modifier.weight(1f))
                                        Texto(if (firmada) "firmada" else "faltan firmas", Estilo.footnote, if (firmada) C.okTinta else C.avisoTinta)
                                        Chevron()
                                    }
                                }
                                if (ent != null && sal == null) {
                                    if (actas.isNotEmpty()) LineaFila()
                                    Row(
                                        Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable { estado.abrir(ADevolucion(id = propiedad)) }.padding(horizontal = 14.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        IconoTono("arrow.left.arrow.right", Tono.oro, 34.dp)
                                        Texto("Comparar con la entrega", Estilo.filaLista, C.texto, Modifier.weight(1f))
                                        Chevron()
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (cargado && ambientes().isNotEmpty()) {
                // Pie: el micrófono y seguir
                Row(
                    Modifier.fillMaxWidth().background(C.superficie.copy(alpha = 0.96f)).padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (estado.escribe) {
                        // Mantener apretado para grabar (o tocar para empezar y tocar para terminar).
                        Box(
                            Modifier.size(52.dp).clip(CircleShape).background(if (voz.grabando) C.error else C.hundida)
                                .semantics { contentDescription = if (voz.grabando) "Terminar la nota de voz" else "Mantener para grabar una nota de voz" }
                                .pointerInput(Unit) {
                                    detectTapGestures(onPress = {
                                        alApretar()
                                        tryAwaitRelease()
                                        alSoltar()
                                    })
                                },
                            contentAlignment = Alignment.Center,
                        ) { Icono("mic.fill", 24f, if (voz.grabando) Color.White else C.accion) }
                    }
                    BotonPrincipal(if (labrando) "Labrando el acta…" else textoSeguir(), { alcance.launch { terminar() } }, Modifier.weight(1f),
                        habilitado = !(labrando || (ultimo() && tipo() == "entrega" && !estado.escribe))) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Texto(if (labrando) "Labrando el acta…" else textoSeguir(), Estilo.headline, C.sobreAccion, lineas = 1, escalaMinima = 0.8f)
                            Icono("arrow.right", 17f, C.sobreAccion)
                        }
                    }
                }
            }
        }
    }
}

@Serializable private data class CuerpoItem(val ambiente: String, val descripcion: String, val cantidad: Int, val estado: String, val notas: String? = null, val fotos: List<String>)
@Serializable private data class GrabacionNota(val transcripcion: String, val audioId: String, val audioSegundos: Int)
@Serializable private data class NuevoItem(val ambiente: String, val descripcion: String, val estado: String = "bueno")
@Serializable private data class Labrar(val tipo: String = "entrada", val contratoId: String? = null)

/** Una fila de la recorrida: la foto (con el menú de cámara y fototeca), el nombre, los tres estados y la nota. */
@Composable
private fun FilaItemInventario(
    it: ItemInventario,
    esElegido: Boolean,
    escribe: Boolean,
    subiendo: Boolean,
    voz: NotaDeVoz,
    durNota: Int?,
    alElegir: () -> Unit,
    alSacarFoto: () -> Unit,
    alElegirFoto: () -> Unit,
    alPoner: (String) -> Unit,
    alCambiarNota: (String) -> Unit,
    alTerminarNota: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().background(if (esElegido) C.hundida.copy(alpha = 0.5f) else Color.Transparent).clickable(onClick = alElegir)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top,
    ) {
        // Foto
        val ultima = it.fotosUrl?.lastOrNull()
        val caja: @Composable () -> Unit = {
            Box(Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                if (ultima != null) Foto(urlDeFoto(ultima), Modifier.fillMaxSize())
                else Box(Modifier.fillMaxSize().background(C.hundida), contentAlignment = Alignment.Center) { Icono("camera", 20f, C.accion) }
                if (subiendo) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)), contentAlignment = Alignment.Center) { CargandoCobro(Color.White) }
            }
        }
        if (escribe) {
            MenuBoton(
                listOf(OpcionDeMenu("Sacar foto", "camera") { alSacarFoto() }, OpcionDeMenu("Elegir de la fototeca", "photo") { alElegirFoto() }),
                if (ultima == null) "Sacar una foto de ${it.descripcion}" else "Sumar otra foto de ${it.descripcion}",
            ) { caja() }
        } else caja()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Texto(it.descripcion + (if ((it.cantidad ?: 1) > 1) " · ${it.cantidad ?: 1}" else ""), Estilo(16f, Peso.semibold), C.texto)
            // Tres estados
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("bueno" to "Bueno", "regular" to "Regular", "malo" to "Malo").forEach { (k, n) ->
                    val on = Inv.grupo(it.estado) == k
                    val tinta = if (on) (if (k == "malo") C.errorTinta else if (k == "regular") C.avisoTinta else C.okTinta) else C.texto
                    val fondo = if (on) (if (k == "malo") C.errorFondo else if (k == "regular") C.avisoFondo else C.okFondo) else C.hundida
                    Row(
                        Modifier.weight(1f).heightIn(min = 38.dp).clip(RoundedCornerShape(10.dp)).background(fondo)
                            .clickable(enabled = escribe) { alPoner(k) },
                        horizontalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (on) Icono("checkmark", 11f, tinta)
                        Texto(n, Estilo(14f, if (on) Peso.bold else Peso.medium), tinta)
                    }
                }
            }
            // Nota
            if (voz.grabando && esElegido) {
                Etiqueta("${voz.texto.ifEmpty { "Escuchando…" }} · ${NotaDeVoz.duracion(voz.segundos)}", "waveform", Estilo.footnote, C.errorTinta)
            } else if (escribe) {
                val nota = it.notas ?: ""
                BasicTextField(
                    nota, alCambiarNota, Modifier.fillMaxWidth(),
                    textStyle = Estilo.texto14.ts().copy(color = C.texto2),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { alTerminarNota() }),
                    maxLines = 6,
                    cursorBrush = SolidColor(C.accion),
                    decorationBox = { campo ->
                        Box { if (nota.isEmpty()) Texto("Nota (opcional)", Estilo.texto14, C.texto3); campo() }
                    },
                )
                if (durNota != null) Etiqueta("Nota de voz · ${NotaDeVoz.duracion(durNota)}", "waveform", Estilo.footnote, C.texto3)
            } else {
                it.notas?.takeIf { n -> n.isNotBlank() }?.let { n -> Etiqueta("«$n»", "waveform", Estilo.footnote, C.texto2) }
            }
        }
    }
}

/** 11B–11C El acta (web 14): la hoja con el membrete (y la matrícula), los ítems congelados y las tres
 *  firmas. Una firma pendiente se toca y se firma acá (el teléfono pasa de mano en mano); «Pedir firma»
 *  le manda por WhatsApp a quien falta un link para firmar desde su celular (vence en 7 días; se puede
 *  dar de baja). Imprimir saca la misma hoja en PDF en cualquier momento. */
@Composable
fun Firmas(
    /** «propiedad/acta». */
    clave: String,
) {
    val estado = LocalEstado.current
    val ctx = LocalContext.current
    val alcance = rememberCoroutineScope()
    var acta by remember { mutableStateOf<Acta?>(null) }
    var error by remember { mutableStateOf<ErrorApi?>(null) }
    var prop by remember { mutableStateOf<Propiedad?>(null) }
    var inmo by remember { mutableStateOf<InmobiliariaActa?>(null) }
    var links by remember { mutableStateOf<List<LinkFirma>>(emptyList()) }
    var inquilino by remember { mutableStateOf<ContactoActa?>(null) }
    var propietario by remember { mutableStateOf<ContactoActa?>(null) }
    var firmando by remember { mutableStateOf<String?>(null) }
    var pidiendo by remember { mutableStateOf(false) }

    val propiedad = clave.split("/").firstOrNull() ?: ""
    val actaId = clave.split("/").lastOrNull() ?: ""
    val inmoNombre = inmo?.nombre ?: estado.inmobiliaria?.nombre ?: ""
    val inmoCorta = inmoNombre.replace("Propiedades", "Prop.")
    val partes = listOf(
        Firmas.Parte("inquilino", "Inquilina/o", inquilino?.nombre ?: "", inquilino?.telefono),
        Firmas.Parte("propietario", "Propietario", propietario?.nombre ?: "", propietario?.telefono),
        Firmas.Parte("inmobiliaria", "Por $inmoCorta", estado.usuario?.completo ?: "", null),
    )
    val pendiente = partes.firstOrNull { it.parte != "inmobiliaria" && acta?.firmas?.get(it.parte) == null && !it.telefono.isNullOrBlank() }
    val titulo = if (acta?.tipo == "salida") "Acta de devolución e inventario" else "Acta de entrega e inventario"

    fun tituloFirma(p: String): String =
        if (p == "inmobiliaria") "Firma por $inmoNombre" else "Firma ${if (p == "inquilino") "de la parte inquilina" else "del propietario"}"

    suspend fun cargar() {
        try {
            val a: Acta = estado.api.get("/v1/propiedades/$propiedad/actas/$actaId")
            acta = a
            error = null
        } catch (e: CancellationException) { throw e } catch (e: ErrorApi) {
            if (acta == null) error = e
            return
        } catch (e: Exception) { return }
        prop = estado.api.opcional("/v1/propiedades/$propiedad")
        inmo = estado.api.opcional("/v1/inmobiliaria")
        val l: List<LinkFirma> = estado.api.opcional("/v1/propiedades/$propiedad/actas/$actaId/links") ?: emptyList()
        links = l.filter { it.estado == "vigente" }
        var k: ContratoAPI? = null
        acta?.contratoId?.let { c -> k = estado.api.opcional("/v1/contratos/$c") }
        (k?.propietarioId ?: prop?.propietarioId)?.let { pid -> propietario = estado.api.opcional("/v1/contactos/$pid") }
        k?.contactoId?.let { cid -> inquilino = estado.api.opcional("/v1/contactos/$cid") }
        if (inquilino == null) k?.contactoNombre?.let { n -> inquilino = ContactoActa(k?.contactoId ?: "", n, null) }
    }

    suspend fun firmar(parte: String, nombre: String, trazo: String) {
        val ruta = "/v1/propiedades/$propiedad/actas/$actaId/firmas/$parte"
        try {
            val a: Acta = estado.api.enviar("PUT", ruta, CuerpoFirma(nombre, trazo))
            acta = a
            Haptico.exito()
            val api = estado.api
            estado.avisar("Firmó ${nombre.trim()}", deshacer = {
                alcance.launch {
                    try {
                        val b: Acta = api.enviar("DELETE", ruta, emptyMap<String, String>())
                        acta = b
                    } catch (e: CancellationException) { throw e } catch (e: Exception) { estado.avisar(e) }
                }
            })
        } catch (e: CancellationException) { throw e } catch (e: Exception) { estado.avisar(e) }
    }

    suspend fun borrarFirma(parte: String) {
        try {
            val a: Acta = estado.api.enviar("DELETE", "/v1/propiedades/$propiedad/actas/$actaId/firmas/$parte", emptyMap<String, String>())
            acta = a
        } catch (e: CancellationException) { throw e } catch (e: Exception) { estado.avisar(e) }
    }

    /** Un link nuevo para esa parte (revoca el anterior) y el WhatsApp a su teléfono con el link. */
    suspend fun pedirFirma(p: Firmas.Parte) {
        pidiendo = true
        try {
            val l: LinkFirma = estado.api.enviar("POST", "/v1/propiedades/$propiedad/actas/$actaId/firmas/${p.parte}/link", emptyMap<String, String>())
            val dir = prop?.direccion ?: prop?.nombre ?: ""
            val n = Inv.primerNombre(p.nombre)
            val texto = "Hola $n, te escribo de $inmoNombre. El acta de ${if (acta?.tipo == "entrada") "entrega" else "devolución"} de $dir está lista: falta tu firma. La podés firmar desde el celular acá: ${l.url ?: ""}"
            WhatsAppCorredor.waMe(p.telefono, texto)?.let { abrirUrl(ctx, it) }
            val todos: List<LinkFirma> = estado.api.opcional("/v1/propiedades/$propiedad/actas/$actaId/links") ?: emptyList()
            links = todos.filter { it.estado == "vigente" }
        } catch (e: CancellationException) { throw e } catch (e: Exception) {
            Inv.errorConReintento(estado, e) { alcance.launch { pedirFirma(p) } }
        } finally { pidiendo = false }
    }

    suspend fun darDeBaja(l: LinkFirma) {
        try {
            estado.api.hacer("DELETE", "/v1/propiedades/$propiedad/actas/$actaId/links/${l.id}")
            links = links.filter { it.id != l.id }
            estado.avisar("El link para firmar ya no abre")
        } catch (e: CancellationException) { throw e } catch (e: Exception) { estado.avisar(e) }
    }

    suspend fun imprimir() {
        val a = acta ?: return
        val u = Documentos.actaFirmada(a, prop?.direccion ?: prop?.nombre ?: "", prop?.barrio, inmo, inquilino?.nombre, propietario?.nombre,
            "Por $inmoCorta", estado.api)
        if (u != null) verArchivo(ctx, u) else estado.avisar("No pudimos armar el PDF. Probá de nuevo.", ok = false)
    }

    LaunchedEffect(estado.version) { cargar() }

    PantallaApilada(if (acta?.tipo == "salida") "Acta de devolución" else "Acta de entrega", acciones = if (acta != null) ({
        BotonBarraActa("printer", "Imprimir") { alcance.launch { imprimir() } }
    }) else null) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val a = acta
            val err = error
            if (a != null) {
                HojaActa(a, titulo, inmoNombre, inmo, prop, inquilino, propietario, partes, estado.escribe,
                    alFirmar = { firmando = it }, alBorrar = { p -> alcance.launch { borrarFirma(p) } })
                // Links vigentes
                partes.forEach { p ->
                    val l = links.firstOrNull { it.parte == p.parte && a.firmas?.get(p.parte) == null }
                    if (l != null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icono("link", 15f, C.azulTinta)
                            val vence = (Fecha.iso(l.venceEn)?.let { Inv.numerica(it) } ?: Inv.numerica(l.venceEn)).take(5)
                            Texto("Le mandamos el link a ${Inv.primerNombre(p.nombre).ifBlank { p.rotulo.lowercase() }} · vence el $vence",
                                Estilo.footnote, C.texto2, Modifier.weight(1f))
                            if (estado.escribe) BotonLink("Dar de baja", modifier = Modifier.heightIn(min = 44.dp)) { alcance.launch { darDeBaja(l) } }
                        }
                    }
                }
                // Acciones
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (estado.escribe && pendiente != null) {
                        BotonPrincipal(if (pidiendo) "Armando el link…" else "Pedir firma a ${Inv.primerNombre(pendiente.nombre)}",
                            { alcance.launch { pedirFirma(pendiente) } }, habilitado = !pidiendo, simbolo = "bubble.left")
                    }
                    if (estado.escribe) {
                        // Las partes sin teléfono cargado: el link igual, para mandarlo por donde sea.
                        partes.filter { it.parte != "inmobiliaria" && a.firmas?.get(it.parte) == null && it.parte != pendiente?.parte }.forEach { p ->
                            BotonTexto("Mandarle un link a ${p.nombre.takeIf { it.isNotBlank() }?.let { Inv.primerNombre(it) } ?: p.rotulo.lowercase()} para que firme desde su teléfono",
                                { alcance.launch { pedirFirma(p) } }, alto = 44.dp)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BotonHundido("Imprimir", { alcance.launch { imprimir() } }, Modifier.weight(1f), simbolo = "printer")
                        BotonHundido("Recorrida", { estado.abrir(AInventario(id = propiedad)) }, Modifier.weight(1f), simbolo = "figure.walk")
                    }
                    if (a.tipo == "entrada") {
                        BotonHundido("Comparar la devolución", { estado.abrir(ADevolucion(id = propiedad)) }, simbolo = "arrow.left.arrow.right")
                    }
                }
            } else if (err != null) {
                EstadoError("el acta", err) { alcance.launch { cargar() } }
            } else {
                EsqueletoFilas(filas = 6)
            }
        }
    }

    HojaDe(firmando, { firmando = null }) { p ->
        val parte = partes.firstOrNull { it.parte == p }
        HojaFirma(p, tituloFirma(p), parte?.nombre ?: "") { nombre, trazo -> alcance.launch { firmar(p, nombre, trazo) } }
    }
}

@Serializable private data class CuerpoFirma(val nombre: String, val trazo: String)

/** Lo estático de Firmas (las filas numeradas, los colores de la hoja). */
object Firmas {
    data class Parte(val parte: String, val rotulo: String, val nombre: String, val telefono: String?)
    data class FilaActa(val item: Acta.Item, val codigo: String)

    val tinta = hex(0x18201D)
    val gris = hex(0x6B6457)
    val linea = hex(0xE3DDCF)

    /** Las fotos del acta se numeran en orden: F-01, F-02… */
    fun filas(items: List<Acta.Item>): List<FilaActa> {
        var n = 0
        return items.map { i ->
            if ((i.fotos ?: emptyList()).isEmpty()) FilaActa(i, "—")
            else { n += 1; FilaActa(i, String.format(Formato.argentina, "F-%02d", n)) }
        }
    }

    fun textoItem(i: Acta.Item): String {
        var s = i.descripcion
        if ((i.cantidad ?: 1) > 1) s += " (${i.cantidad ?: 1})"
        i.notas?.takeIf { it.isNotBlank() }?.let { n -> s += " · " + n.take(1).lowercase() + n.drop(1) }
        return s
    }
}

/** La hoja del acta: membrete, título y lugar, partes, la tabla y las firmas. */
@Composable
private fun HojaActa(
    a: Acta,
    titulo: String,
    inmoNombre: String,
    inmo: InmobiliariaActa?,
    prop: Propiedad?,
    inquilino: ContactoActa?,
    propietario: ContactoActa?,
    partes: List<Firmas.Parte>,
    escribe: Boolean,
    alFirmar: (String) -> Unit,
    alBorrar: (String) -> Unit,
) {
    val tinta = Firmas.tinta; val gris = Firmas.gris; val linea = Firmas.linea
    Column(
        Modifier.fillMaxWidth().shadow(6.dp, RoundedCornerShape(6.dp)).clip(RoundedCornerShape(6.dp)).background(hex(0xFFFEFB)).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Membrete
        Column {
            Row(Modifier.padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(36.dp).clip(CircleShape).background(hex(0x1E3A5F)), contentAlignment = Alignment.Center) {
                    Texto(inmoNombre.take(1), Estilo(15f, Peso.bold), hex(0xE9DCC3))
                }
                Column(Modifier.weight(1f)) {
                    Texto(inmoNombre, Estilo(15f, Peso.bold), tinta)
                    inmo?.matricula?.let { Texto(it, Estilo(12f, Peso.regular), gris) }
                }
                BasicText("ACTA · ${Inv.numerica(a.fecha)}", style = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace, color = gris))
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(linea))
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Texto(titulo, Estilo(20f, Peso.bold, -0.02f), tinta)
            // La línea del lugar
            var s = prop?.direccion ?: prop?.nombre ?: ""
            prop?.barrio?.takeIf { it.isNotBlank() }?.let { s += " · $it" }
            Fecha.fecha(a.fecha)?.let { f ->
                s += " · ${Formato.diaCompleto(f)}"
                val c = Fecha.iso(a.creadoEn)
                if (c != null && c.atZone(Formato.zona).toLocalDate().toString() == (a.fecha ?: "").take(10)) s += ", ${Formato.horaDe(c)}"
            }
            Texto(s, Estilo(13f, Peso.regular), gris)
        }
        // Las partes
        val partesTexto = buildAnnotatedString {
            inquilino?.let { append("Inquilina/o: "); withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(it.nombre) }; append(". ") }
            propietario?.let { append("Propietario: "); withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(it.nombre) }; append(". ") }
            a.observaciones?.takeIf { it.isNotBlank() }?.let { append(it) }
        }
        BasicText(partesTexto, style = Estilo(14f, Peso.regular).ts().copy(color = tinta))
        // La tabla
        val filas = Firmas.filas(a.items ?: emptyList())
        Column {
            Row(Modifier.padding(vertical = 6.dp)) {
                Texto("AMBIENTE · ÍTEM", Estilo(11f, Peso.bold, 0.036f), gris, Modifier.weight(1f))
                Texto("ESTADO · FOTO", Estilo(11f, Peso.bold, 0.036f), gris)
            }
            if (filas.isEmpty()) {
                Texto("El inventario estaba vacío cuando se labró el acta.", Estilo(13f, Peso.regular), gris, Modifier.fillMaxWidth().padding(vertical = 10.dp))
            }
            filas.forEach { f ->
                val ojo = f.item.estado == "regular" || f.item.estado == "malo"
                Box(Modifier.fillMaxWidth().height(0.5.dp).background(linea))
                Row(
                    Modifier.fillMaxWidth().background(if (ojo) hex(0xFBF1DC) else Color.Transparent).padding(vertical = 7.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top,
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Texto(f.item.ambiente, Estilo(11f, Peso.semibold), gris)
                        Texto(Firmas.textoItem(f.item), Estilo(13f, Peso.regular), tinta)
                    }
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Texto(Inv.nombre(f.item.estado), Estilo(13f, if (ojo) Peso.bold else Peso.regular), tinta)
                        BasicText(f.codigo, style = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = gris))
                    }
                }
            }
        }
        // Las firmas
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
            partes.forEach { p ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val f = a.firmas?.get(p.parte)
                    val t = f?.trazo
                    if (f != null && t != null) {
                        MenuContextual(
                            if (escribe) listOf(OpcionDeMenu("Borrar la firma", "trash", destructiva = true) { alBorrar(p.parte) }) else emptyList(),
                            alTocar = {},
                        ) { TrazoFirma(t, Modifier.fillMaxWidth().height(54.dp), color = tinta) }
                    } else {
                        Box(
                            Modifier.fillMaxWidth().heightIn(min = 54.dp)
                                .drawBehind {
                                    drawRoundRect(linea, cornerRadius = CornerRadius(8.dp.toPx()),
                                        style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))))
                                }
                                .clickable(enabled = escribe) { alFirmar(p.parte) },
                            contentAlignment = Alignment.Center,
                        ) { Texto("Firma pendiente", Estilo(12f, Peso.semibold), C.azulTinta) }
                    }
                    Box(Modifier.fillMaxWidth().height(0.5.dp).background(tinta.copy(alpha = 0.4f)))
                    Texto(p.rotulo, Estilo(11f, Peso.regular), gris, lineas = 2, alinear = TextAlign.Center)
                }
            }
        }
    }
}

/** Una firma guardada (path SVG sobre 600 × 200). */
@Composable
fun TrazoFirma(trazo: String, modifier: Modifier = Modifier, color: Color = C.texto) {
    Canvas(modifier.semantics { contentDescription = "Firma" }) {
        val esc = minOf(size.width / 600f, size.height / 200f)
        val dx = (size.width - 600f * esc) / 2f
        val dy = (size.height - 200f * esc) / 2f
        val p = Path()
        TrazoFirma.comandos(trazo).forEach { (c, x, y) ->
            val px = dx + x * esc; val py = dy + y * esc
            if (c == 'M') p.moveTo(px, py) else p.lineTo(px, py)
        }
        drawPath(p, color, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

object TrazoFirma {
    /** Los «M x y» / «L x y» del path, en el lienzo de 600 × 200 (lo usa también el impresor del PDF). */
    fun comandos(trazo: String): List<Triple<Char, Float, Float>> {
        val t = trazo.split(' ', ',').filter { it.isNotEmpty() }
        val out = mutableListOf<Triple<Char, Float, Float>>()
        var i = 0
        while (i < t.size) {
            val c = t[i]
            val x = t.getOrNull(i + 1)?.toFloatOrNull()
            val y = t.getOrNull(i + 2)?.toFloatOrNull()
            if ((c == "M" || c == "L") && i + 2 < t.size && x != null && y != null) {
                out.add(Triple(c[0], x, y))
                i += 3
            } else i += 1
        }
        return out
    }
}

/** La hoja para firmar con el dedo: el nombre (completado con el de la parte) y el recuadro. */
@Composable
fun HojaFirma(
    parte: String,
    titulo: String = "",
    nombreInicial: String = "",
    alFirmar: (String, String) -> Unit,
) {
    val cerrar = LocalCerrarHoja.current
    var nombre by remember(parte) { mutableStateOf(nombreInicial) }
    val trazos = remember(parte) { mutableStateListOf<List<Offset>>() }
    val actual = remember(parte) { mutableStateListOf<Offset>() }
    var tam by remember { mutableStateOf(IntSize(600, 200)) }
    val colorTrazo = C.texto
    val fondoLienzo = if (LocalOscuro.current) hex(0x1A2420) else hex(0xFAF8F3)

    /** Los trazos como path SVG en el lienzo de 600 × 200 que guarda la API. */
    fun svg(): String {
        val ex = 600f / maxOf(tam.width.toFloat(), 1f)
        val ey = 200f / maxOf(tam.height.toFloat(), 1f)
        return trazos.filter { it.size > 1 }.joinToString(" ") { t ->
            t.mapIndexed { i, p -> "${if (i == 0) "M" else "L"} ${(p.x * ex).toInt()} ${(p.y * ey).toInt()}" }.joinToString(" ")
        }
    }

    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Texto(titulo.ifEmpty { "Firma del $parte" }, Estilo.titulo3, C.texto)
        Texto(Formato.sinViudas("Firmá con el dedo en el recuadro. Queda en el acta con la fecha y la hora."), Estilo.footnote, C.texto2)
        Etiquetado("Nombre de quien firma") {
            CampoCobro(nombre, { nombre = it }, "Nombre y apellido", Modifier.fillMaxWidth())
        }
        Canvas(
            Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(12.dp)).background(fondoLienzo)
                .onSizeChanged { tam = it }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { o -> actual.clear(); actual.add(o) },
                        onDrag = { c, _ -> actual.add(c.position) },
                        onDragEnd = { trazos.add(actual.toList()); actual.clear() },
                        onDragCancel = { trazos.add(actual.toList()); actual.clear() },
                    )
                },
        ) {
            (trazos + listOf(actual.toList())).filter { it.size > 1 }.forEach { t ->
                val p = Path().apply { moveTo(t[0].x, t[0].y); t.drop(1).forEach { lineTo(it.x, it.y) } }
                drawPath(p, colorTrazo, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
        BotonTexto("Borrar el trazo", { trazos.clear() }, alto = 40.dp, habilitado = trazos.isNotEmpty())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BotonHundido("Cancelar", { cerrar() }, Modifier.weight(1f))
            BotonAccion("Guardar la firma", {
                alFirmar(nombre.trim(), svg())
                cerrar()
            }, Modifier.weight(1f), habilitado = !(nombre.trim().length < 2 || trazos.isEmpty()))
        }
    }
}

@Serializable
data class GrabacionAPI(
    val id: String,
    val transcripcion: String? = null,
    val audioUrl: String? = null,
    val audioSegundos: Int? = null,
    val itemsCreados: Int? = null,
    val creadaPor: String? = null,
    /** La API la manda como `creadoEn`. */
    val creadoEn: String? = null,
) {
    val creadaEn: String? get() = creadoEn
}

@Serializable private data class CuerpoDictado(val transcripcion: String, val tipoActa: String? = null, val contratoId: String? = null, val armar: Boolean = true)
/** Con `armar`, la API arma los ítems desde el texto y responde {grabacion, items}. */
@Serializable private data class Guardada(val grabacion: GrabacionAPI, val items: List<ItemInventario>? = null)

/** Dictar la recorrida: se graba la voz, se transcribe en el teléfono y la API arma los ítems del
 *  inventario («living: paredes bien, una mancha de humedad en el techo…»). */
@Composable
fun DictarInventario(
    propiedad: String,
    /** «entrada» o «salida» para respaldar el acta (la API lo pide junto con el contrato); null si no hay contrato. */
    tipoActa: String? = null,
    contrato: String? = null,
    alTerminar: (Int) -> Unit,
) {
    val estado = LocalEstado.current
    val ctx = LocalContext.current
    val cerrar = LocalCerrarHoja.current
    val alcance = rememberCoroutineScope()
    // Dictado (Propiedades/Alta) en iOS: acá la misma NotaDeVoz (SpeechRecognizer es-AR).
    val dictado = remember { NotaDeVoz() }
    var enviando by remember { mutableStateOf(false) }

    suspend fun terminar() { dictado.terminar() }

    suspend fun enviar() {
        enviando = true
        try {
            val ok = tipoActa == "entrada" || tipoActa == "salida"
            val g: Guardada = estado.api.enviar("POST", "/v1/propiedades/$propiedad/grabaciones",
                CuerpoDictado(dictado.texto, if (ok && contrato != null) tipoActa else null, if (ok) contrato else null))
            Haptico.exito()
            alTerminar(g.grabacion.itemsCreados ?: g.items?.size ?: 0)
            cerrar()
        } catch (e: CancellationException) { throw e } catch (e: Exception) { estado.avisar(e) } finally { enviando = false }
    }

    Column(Modifier.fillMaxSize().background(C.fondo).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Texto("Dictá la recorrida", Estilo.titulo, C.texto)
        Texto(Formato.sinViudas("Ambiente por ambiente: qué hay y cómo está. Armamos los ítems del inventario."), Estilo.subhead, C.texto2)
        Box(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.hundida).padding(14.dp)) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Texto(dictado.texto.ifEmpty { "«Living: paredes bien, piso de madera con un rayón, una mancha de humedad en el techo…»" },
                    Estilo.body16, if (dictado.texto.isEmpty()) C.texto3 else C.texto, Modifier.fillMaxWidth())
            }
        }
        if (dictado.grabando) {
            BotonPrincipal("Terminar", { alcance.launch { terminar() } })
        } else if (dictado.texto.isEmpty()) {
            BotonPrincipal("Empezar a dictar", { alcance.launch { dictado.empezar(ctx, estado.alPedirPermiso) } }, simbolo = "mic.fill")
        } else {
            BotonPrincipal(if (enviando) "Armando los ítems…" else "Armar el inventario", { alcance.launch { enviar() } }, habilitado = !enviando)
            BotonTexto("Dictar de nuevo", { dictado.texto = ""; alcance.launch { dictado.empezar(ctx, estado.alPedirPermiso) } })
        }
    }
}

/** Las recorridas dictadas de una propiedad: escuchar el audio, leer la transcripción y cuántos ítems armó. */
@Composable
fun Grabaciones(propiedad: String) {
    val estado = LocalEstado.current
    var lista by remember { mutableStateOf<List<GrabacionAPI>>(emptyList()) }
    var dictando by remember { mutableStateOf(false) }

    LaunchedEffect(estado.version) { lista = estado.api.opcional("/v1/propiedades/$propiedad/grabaciones") ?: emptyList() }

    PantallaApilada("Recorridas dictadas", acciones = if (estado.escribe) ({ BotonBarraActa("mic", "Dictar") { dictando = true } }) else null) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (lista.isEmpty()) {
                Box(Modifier.padding(top = 40.dp)) {
                    EstadoVacio("waveform", "Sin recorridas dictadas", "Dictá la recorrida y armamos el inventario.",
                        accion = if (estado.escribe) ("Dictar" to { dictando = true }) else null)
                }
            }
            lista.forEach { g ->
                Column(Modifier.fillMaxWidth().tarjeta().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Texto(Fecha.iso(g.creadaEn)?.let { Formato.cuando(it) } ?: "", Estilo.footnote, C.texto3, Modifier.weight(1f))
                        g.itemsCreados?.let { n -> Texto(Formato.cantidad(n, "ítem", "ítems"), Estilo(12f, Peso.bold), C.okTinta) }
                    }
                    g.audioUrl?.let { u -> AudioMensaje(url = u) }
                    Texto(g.transcripcion ?: "", Estilo.texto14, C.texto)
                }
            }
        }
    }

    Hoja(dictando, { dictando = false }) {
        DictarInventario(propiedad) { n ->
            estado.version += 1
            if (n > 0) estado.sugerir("Armamos ${Formato.cantidad(n, "ítem", "ítems")} del inventario.", "Ver", AInventario(id = propiedad))
            else estado.avisar("Guardamos la recorrida, pero no encontramos ítems. Sumalos a mano en el inventario.", ok = false)
        }
    }
}

@Serializable
data class EnvioAPI(
    val id: String,
    val contactoId: String? = null,
    val contactoNombre: String? = null,
    val canal: String? = null,
    val enviadaPor: String? = null,
    val enviadaEn: String? = null,
)

/** A quién se le mandó la ficha, por dónde y cuándo. */
@Composable
fun FichasMandadas(propiedad: String) {
    val estado = LocalEstado.current
    val alcance = rememberCoroutineScope()
    var lista by remember { mutableStateOf<List<EnvioAPI>>(emptyList()) }

    suspend fun cargar() { lista = estado.api.opcional("/v1/propiedades/$propiedad/envios") ?: emptyList() }

    LaunchedEffect(estado.version) { cargar() }

    PantallaApilada("Fichas mandadas") {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (lista.isEmpty()) {
                EstadoVacio("paperplane", "Todavía no se la mandaste a nadie", "Cuando mandes la ficha por WhatsApp o por mail, queda acá.")
            }
            if (lista.isNotEmpty()) {
                Column(Modifier.fillMaxWidth().tarjeta(radio = 12.dp)) {
                    lista.forEachIndexed { i, e ->
                        if (i > 0) LineaFila(16.dp)
                        FilaDeslizable(
                            derecha = if (estado.escribe) listOf(AccionFila("Quitar", "trash", C.error) {
                                alcance.launch {
                                    runCatching { estado.api.hacer("DELETE", "/v1/propiedades/$propiedad/envios/${e.id}") }
                                    cargar()
                                }
                            }) else emptyList(),
                        ) {
                            FilaToque(alTocar = { e.contactoId?.let { c -> estado.abrir(AFichaContacto(id = c)) } }) {
                                Icono(if (e.canal == "email") "envelope" else "bubble.left", 17f, C.accion)
                                Column {
                                    Texto(e.contactoNombre ?: "Contacto", Estilo.filaLista, C.texto)
                                    Texto(listOfNotNull(e.enviadaPor, Fecha.iso(e.enviadaEn)?.let { Formato.cuando(it) }).joinToString(" · "), Estilo.footnote, C.texto2)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
