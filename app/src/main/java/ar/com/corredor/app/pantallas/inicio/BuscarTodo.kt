package ar.com.corredor.app.pantallas.inicio

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import ar.com.corredor.app.app.*
import ar.com.corredor.app.nucleo.*
import ar.com.corredor.app.pantallas.cobranzas.*
import ar.com.corredor.app.pantallas.mas.*
import ar.com.corredor.app.pantallas.propiedades.*
import ar.com.corredor.app.tema.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// Clon de Pantallas/Inicio/BuscarTodo.swift.

object BuscarTodo {
    /** Un resultado abierto hace poco (los últimos 5). */
    @Serializable
    data class Reciente(val tipo: String, val id: String, val titulo: String)

    /** Los tipos que devuelve la API, en el orden de los grupos del panel (busqueda.ts · TIPOS). */
    val grupos: List<Triple<String, String, String>> = listOf(
        Triple("propiedad", "Propiedades", "building.2"), Triple("contacto", "Contactos", "person"), Triple("contrato", "Contratos", "doc.text"),
        Triple("consorcio", "Consorcios", "building.columns"), Triple("operacion", "Operaciones", "rectangle.split.3x1"),
        Triple("emprendimiento", "Emprendimientos", "building"), Triple("reserva", "Reservas", "bookmark"),
        Triple("reserva_temporada", "Temporario", "beach.umbrella"),
    )
    val filtros: List<Pair<String, String>> = listOf(
        "" to "Todo", "propiedad" to "Propiedades", "contacto" to "Contactos", "contrato" to "Contratos",
        "consorcio" to "Consorcios", "accion" to "Acciones",
    )

    /** Lo que se puede hacer desde la búsqueda (busqueda.ts · ACCIONES). */
    data class Accion(val clave: String, val texto: String, val icono: String, val palabras: String) {
        val id: String get() = clave
    }

    val acciones: List<Accion> = listOf(
        Accion("cobro", "Registrar cobro", "dollarsign.circle", "registrar cobro pago cobranza"),
        Accion("propiedad", "Cargar propiedad", "house", "cargar nueva propiedad alta"),
        Accion("mira", "Captar con MIRA", "viewfinder", "mira cartel foto captar"),
        Accion("visita", "Agendar visita", "calendar.badge.plus", "agendar visita cita"),
        Accion("contacto", "Nuevo contacto", "person.badge.plus", "nuevo contacto interesado persona"),
        Accion("inicio", "Ir a Inicio", "house", "ir inicio hoy"),
        Accion("propiedades", "Ir a Propiedades", "building.2", "ir propiedades cartera"),
        Accion("agenda", "Ir a Agenda", "calendar", "ir agenda calendario"),
    )

    /** Las acciones que coinciden con lo escrito (todas las palabras, como prefijos). */
    fun accionesPara(q: String): List<Accion> {
        val palabras = MasRaiz.normal(q).split(" ").filter { it.isNotEmpty() }
        if (palabras.isEmpty()) return emptyList()
        return acciones.filter { a ->
            val t = MasRaiz.normal(a.texto + " " + a.palabras).split(" ")
            palabras.all { p -> t.any { it.startsWith(p) } }
        }
    }

    /** La acción rápida de cada tipo (busqueda.ts · rapida). */
    fun rapida(tipo: String): Pair<String, String>? = when (tipo) {
        "propiedad" -> "Mandar la ficha por WhatsApp" to "paperplane"
        "contacto" -> "Escribirle por WhatsApp" to "bubble.left"
        "contrato" -> "Registrar cobro" to "dollarsign.circle"
        else -> null
    }

    // MARK: Recientes

    internal const val claveRecientes = "corredor_recientes"
    private val json = Json { ignoreUnknownKeys = true }

    fun leerRecientes(ctx: android.content.Context): List<Reciente> {
        val d = ajustes(ctx).getString(claveRecientes, null) ?: return emptyList()
        return runCatching { json.decodeFromString<List<Reciente>>(d) }.getOrElse { emptyList() }
    }

    internal fun guardarRecientes(ctx: android.content.Context, l: List<Reciente>) {
        runCatching { ajustes(ctx).edit().putString(claveRecientes, json.encodeToString(l)).apply() }
    }
}

/** 2C Buscar en todo (el ⌘K del panel, pantalla entera en el celular; lib/ui/shell/Buscador.svelte): los
 *  filtros por tipo, los resultados agrupados con lo escrito resaltado, las acciones que se pueden
 *  escribir («registrar cobro», «mira»), la acción rápida de cada resultado (mantener apretado o deslizar:
 *  mandar la ficha, escribirle, registrar el cobro), los recientes y las secciones de la app. */
@Composable
fun BuscarTodo(texto: String) {
    val estado = LocalEstado.current
    val ctx = LocalContext.current
    val cerrar = rememberVolver()
    val alcance = rememberCoroutineScope()
    val textoInicial = texto
    var texto by remember { mutableStateOf(textoInicial) }
    var filtro by remember { mutableStateOf("") }
    var resultados by remember { mutableStateOf<List<Resultado>>(emptyList()) }
    var buscando by remember { mutableStateOf(false) }
    var fallo by remember { mutableStateOf(false) }
    var recientes by remember { mutableStateOf(BuscarTodo.leerRecientes(ctx)) }
    val foco = remember { FocusRequester() }
    val focos = LocalFocusManager.current

    val q = texto.trim()
    /** Dos letras, o un número solo (el código o el número de la propiedad). */
    val buscable = q.length >= 2 || (q.isNotEmpty() && q.all { it.isDigit() })

    val accionesVisibles: List<BuscarTodo.Accion> = run {
        if (!(filtro.isEmpty() || filtro == "accion")) return@run emptyList()
        val base = if (q.isEmpty()) (if (filtro == "accion") BuscarTodo.acciones else emptyList()) else BuscarTodo.accionesPara(q)
        if (estado.escribe) base else base.filter { it.clave in listOf("inicio", "propiedades", "agenda") }
    }

    val sinResultados: List<String> = if (q.length < 2 || buscando || fallo) emptyList()
    else BuscarTodo.filtros.filter { f -> f.first.isNotEmpty() && f.first != "accion" && resultados.none { r -> r.tipo == f.first } }.map { it.second }

    fun recordar(x: Resultado) {
        val r = BuscarTodo.Reciente(tipo = x.tipo, id = x.id, titulo = x.titulo)
        recientes = (listOf(r) + recientes.filter { !(it.id == r.id && it.tipo == r.tipo) }).take(5)
        BuscarTodo.guardarRecientes(ctx, recientes)
    }

    fun abrir(x: Resultado) {
        recordar(x)
        when (x.tipo) {
            "propiedad" -> estado.abrir(AFichaPropiedad(id = x.id))
            "contacto" -> estado.abrir(AFichaContacto(id = x.id))
            "contrato" -> estado.abrir(AFichaContrato(id = x.id))
            "operacion" -> estado.abrir(AFichaOperacion(id = x.id))
            // Las claves de la ficha son en singular (las de la lista, en plural, ignoran el id).
            "emprendimiento", "consorcio" -> estado.abrir(ASeccion(clave = x.tipo, id = x.id))
            "reserva" -> estado.abrir(AReserva(id = x.id))
            "reserva_temporada" -> estado.abrir(AReservaTemporada(id = x.id))
            else -> Ruta.de("/${x.tipo}/${x.id}")?.destino?.let { estado.abrir(it) }
        }
    }

    fun hacerRapida(x: Resultado) {
        recordar(x)
        when (x.tipo) {
            "propiedad" -> estado.abrir(AMandarPropiedad(id = x.id))
            "contacto" -> alcance.launch {
                @Serializable data class T(val telefono: String? = null)
                try {
                    val c: T = estado.api.get("/v1/contactos/${x.id}")
                    if (c.telefono?.nonEmpty == null) estado.avisar("Este contacto no tiene teléfono.", ok = false) else Afuera.whatsapp(c.telefono)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    estado.avisar(e)
                }
            }
            "contrato" -> estado.abrir(ARegistrarCobro(contrato = x.id))
            else -> abrir(x)
        }
    }

    fun hacer(a: BuscarTodo.Accion) {
        when (a.clave) {
            "cobro" -> estado.abrir(ARegistrarCobro())
            "propiedad" -> estado.abrir(AAltaPropiedad())
            "mira" -> estado.miraAbierta = true
            "visita" -> estado.hojaRapida = HojaRapida.visita
            "contacto" -> estado.hojaRapida = HojaRapida.contacto
            "inicio" -> {
                estado.pila(Pestana.inicio).clear()
                estado.pestana = Pestana.inicio
            }
            "propiedades" -> estado.ir(Ruta(Pestana.propiedades))
            "agenda" -> estado.abrir(AAgendaDia())
            else -> {}
        }
    }

    LaunchedEffect(Unit) { runCatching { foco.requestFocus() } }
    LaunchedEffect(texto) {
        if (!buscable) {
            resultados = emptyList(); fallo = false; buscando = false
            return@LaunchedEffect
        }
        buscando = true
        delay(150)
        try {
            resultados = estado.api.get("/v1/buscar", listOf("q" to q))
            fallo = false
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            fallo = true
        }
        buscando = false
    }

    // MARK: Piezas

    @Composable
    fun Encabezado(t: String) {
        Texto(t.uppercase(Formato.argentina), Estilo(13f, Peso.bold, 0.5f / 13f), C.texto2, Modifier.padding(horizontal = 4.dp))
    }

    @Composable
    fun FilaSimple(icono: String, t: String) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 50.dp).padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(24.dp), contentAlignment = Alignment.Center) { Icono(icono, 19f, C.accion) }
            Texto(t, Estilo.filaLista, C.texto, Modifier.weight(1f), lineas = 1)
            Chevron()
        }
    }

    /** Lo escrito subrayado en oro al principio de cada palabra («Gorr|iti 4820»). */
    fun resaltado(titulo: String): AnnotatedString {
        val palabras = MasRaiz.normal(q).split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }
        if (palabras.isEmpty()) return AnnotatedString(titulo)
        return buildAnnotatedString {
            var palabra = StringBuilder()
            fun cerrarPalabra(w: String) {
                if (w.isEmpty()) return
                val p = palabras.firstOrNull { MasRaiz.normal(w).startsWith(it) }
                if (p != null && p.length <= w.length) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, textDecoration = TextDecoration.Underline)) { append(w.take(p.length)) }
                    append(w.drop(p.length))
                } else {
                    append(w)
                }
            }
            for (ch in titulo) {
                if (ch.isLetterOrDigit()) palabra.append(ch) else {
                    cerrarPalabra(palabra.toString())
                    palabra = StringBuilder()
                    append(ch.toString())
                }
            }
            cerrarPalabra(palabra.toString())
        }
    }

    @Composable
    fun FilaResultado(x: Resultado, icono: String) {
        val rapida = BuscarTodo.rapida(x.tipo)
        val menu = buildList {
            add(OpcionDeMenu("Abrir", "arrow.up.right") { abrir(x) })
            if (rapida != null && estado.escribe) add(OpcionDeMenu(rapida.first, rapida.second) { hacerRapida(x) })
        }
        MenuContextual(opciones = menu, alTocar = { abrir(x) }) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                if (x.tipo == "contacto") {
                    AvatarIniciales(nombre = x.titulo, tam = 34.dp, fondo = Tono.azul.fondo, tinta = Tono.azul.tinta)
                } else {
                    Box(Modifier.width(34.dp), contentAlignment = Alignment.Center) { Icono(icono, 19f, C.accion) }
                }
                Column(Modifier.weight(1f)) {
                    TextoRico(resaltado(x.titulo), Estilo.filaLista, C.texto, subrayado = C.oro)
                    x.subtitulo?.let { Texto(it, Estilo.footnote, C.texto2) }
                }
                if (rapida != null && estado.escribe) {
                    Tocable(alTocar = { hacerRapida(x) }, descripcion = rapida.first) {
                        Box(Modifier.size(40.dp, 44.dp), contentAlignment = Alignment.Center) { Icono(rapida.second, 16f, C.accion, peso = Peso.semibold) }
                    }
                } else {
                    Chevron()
                }
            }
        }
    }

    @Composable
    fun Grupo(titulo: String, icono: String, items: List<Resultado>) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Encabezado(titulo)
            Column(Modifier.fillMaxWidth().tarjeta(16.dp)) {
                items.forEachIndexed { i, x -> key(x.tipo + x.id) { Box(Modifier.lineaArriba(i > 0)) { FilaResultado(x, icono) } } }
            }
        }
    }

    @Composable
    fun BloqueRecientes() {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Encabezado("Recientes")
            Column(Modifier.fillMaxWidth().tarjeta(16.dp)) {
                recientes.forEachIndexed { i, r ->
                    key(r.tipo + r.id) {
                        Tocable(alTocar = { abrir(Resultado(id = r.id, tipo = r.tipo, titulo = r.titulo, subtitulo = null)) }, modifier = Modifier.lineaArriba(i > 0)) {
                            FilaSimple("clock.arrow.circlepath", r.titulo)
                        }
                    }
                }
            }
        }
    }

    /** Secciones y herramientas de la app («cobrar», «expensas», «dominio»): lo que la app hace, también se busca. */
    @Composable
    fun SeccionesApp() {
        val f = MasRaiz.normal(texto)
        val secciones = if (f.length < 2) emptyList() else MasRaiz.secciones(estado.estructura, duenio = estado.esDuenio).filter { MasRaiz.coincide(it, f) }
        if (secciones.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Encabezado("En la app")
                Column(Modifier.fillMaxWidth().tarjeta(16.dp)) {
                    secciones.take(6).forEachIndexed { i, s ->
                        key(s.clave) {
                            Tocable(alTocar = { Lugares.secciones[s.clave]?.second?.let { d -> estado.abrir(d) } }, modifier = Modifier.lineaArriba(i > 0)) {
                                FilaSimple(Lugares.secciones[s.clave]?.first ?: "square.grid.2x2", s.nombre)
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun BloqueAcciones() {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Encabezado("Acciones")
            Column(Modifier.fillMaxWidth().tarjeta(16.dp)) {
                accionesVisibles.forEachIndexed { i, a ->
                    key(a.id) { Tocable(alTocar = { hacer(a) }, modifier = Modifier.lineaArriba(i > 0)) { FilaSimple(a.icono, a.texto) } }
                }
            }
        }
    }

    // MARK: Pantalla

    // .scrollDismissesKeyboard(.immediately): al mover la lista se baja el teclado.
    val bajarTeclado = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: androidx.compose.ui.geometry.Offset, source: NestedScrollSource): androidx.compose.ui.geometry.Offset {
                if (source == NestedScrollSource.UserInput && available.y != 0f) focos.clearFocus()
                return androidx.compose.ui.geometry.Offset.Zero
            }
        }
    }

    // .toolbar(.hidden, for: .navigationBar): la barra es la del buscador.
    BarraDe(oculta = true)
    Box(Modifier.fillMaxSize()) {
        FondoApp()
        Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 16.dp).padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // La barra: el campo y «Cancelar».
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier.weight(1f).height(38.dp).clip(RoundedCornerShape(11.dp)).background(C.hundida).padding(horizontal = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icono("magnifyingglass", 17f, C.texto3)
                    val estilo = Estilo.body
                    BasicTextField(
                        value = texto, onValueChange = { texto = it }, singleLine = true,
                        textStyle = estilo.ts().copy(color = C.texto), cursorBrush = SolidColor(C.accion),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { focos.clearFocus() }),
                        modifier = Modifier.weight(1f).focusRequester(foco).semantics { contentDescription = "Buscar en Corredor" },
                        decorationBox = { campo ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (texto.isEmpty()) Texto("Propiedad, contacto, contrato…", estilo, C.texto3, lineas = 1)
                                campo()
                            }
                        },
                    )
                    if (texto.isNotEmpty()) {
                        Tocable(alTocar = { texto = "" }, descripcion = "Borrar") { Icono("xmark.circle.fill", 17f, C.texto3) }
                    }
                }
                Texto("Cancelar", Estilo.body, C.accion, Modifier.clickable { cerrar() })
            }
            Row(
                Modifier.sangrar(16.dp).horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BuscarTodo.filtros.forEach { f -> key(f.first) { ChipFiltro(texto = f.second, activo = filtro == f.first, hacer = { filtro = f.first }) } }
            }
            Column(
                Modifier.fillMaxWidth().weight(1f).nestedScroll(bajarTeclado).verticalScroll(rememberScrollState()).padding(bottom = 30.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                if (fallo) {
                    ConIcono("No pudimos buscar. Revisá la conexión y probá de nuevo.", "exclamationmark.icloud", Estilo.footnote, C.errorTinta, Modifier.padding(horizontal = 4.dp))
                }
                if (q.isEmpty() && filtro != "accion" && recientes.isNotEmpty()) BloqueRecientes()
                if (filtro.isEmpty()) SeccionesApp()
                BuscarTodo.grupos.forEach { (tipo, titulo, icono) ->
                    val items = resultados.filter { it.tipo == tipo && (filtro.isEmpty() || filtro == tipo) }
                    if (items.isNotEmpty()) key(tipo) { Grupo(titulo, icono, items) }
                }
                if (accionesVisibles.isNotEmpty()) BloqueAcciones()
                if (buscable && !buscando && !fallo && resultados.isEmpty() && accionesVisibles.isEmpty()) {
                    Box(Modifier.padding(top = 30.dp)) {
                        EstadoVacio(icono = "magnifyingglass", titulo = "No encontramos «$q»", texto = "Probá con la calle, el apellido o el número de la propiedad.")
                    }
                } else if (sinResultados.isNotEmpty() && resultados.isNotEmpty() && filtro.isEmpty()) {
                    Texto("Sin resultados en ${sinResultados.joinToString(", ")}", Estilo.footnote, C.texto3, Modifier.padding(horizontal = 4.dp))
                }
            }
        }
    }
}
