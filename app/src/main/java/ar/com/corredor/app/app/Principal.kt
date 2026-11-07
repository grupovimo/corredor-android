package ar.com.corredor.app.app

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import ar.com.corredor.app.nucleo.AAgendaDia
import ar.com.corredor.app.nucleo.AAltaPropiedad
import ar.com.corredor.app.nucleo.AAvisos
import ar.com.corredor.app.nucleo.ABuscar
import ar.com.corredor.app.nucleo.ACobranzas
import ar.com.corredor.app.nucleo.ASeccion
import ar.com.corredor.app.nucleo.Guia
import ar.com.corredor.app.nucleo.Lugares
import ar.com.corredor.app.nucleo.Pestana
import ar.com.corredor.app.nucleo.opcional
import ar.com.corredor.app.pantallas.agenda.NuevaCita
import ar.com.corredor.app.pantallas.cartera.EditarContacto
import ar.com.corredor.app.pantallas.cobranzas.AltaContrato
import ar.com.corredor.app.pantallas.entrada.AltaInmobiliaria
import ar.com.corredor.app.pantallas.entrada.Bienvenida
import ar.com.corredor.app.pantallas.mira.MiraRaiz
import ar.com.corredor.app.tema.*
import kotlinx.coroutines.delay
import java.time.Instant
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

// Clon de App/Principal.swift.

/** La barra de navegación de una pantalla apilada (la nativa de iOS): lo que la pantalla le pone con BarraDe. */
class Barra {
    var titulo by mutableStateOf<String?>(null)
    var acciones by mutableStateOf<(@Composable RowScope.() -> Unit)?>(null)
    var izquierda by mutableStateOf<(@Composable RowScope.() -> Unit)?>(null)
    var oculta by mutableStateOf(false)
    /** `.navigationBarTitleDisplayMode(.large)`: el título va grande en la pantalla y no en la barra. */
    var grande by mutableStateOf(false)
    /** Con `grande`, cuánto del título grande se escondió al desplazar (0 a 1): pasado el final, el título chico
     *  aparece en la barra, como en iOS. */
    var colapso by mutableFloatStateOf(0f)
}

val LocalBarra = staticCompositionLocalOf { Barra() }

/** .navigationTitle / .toolbar / .toolbar(.hidden) de la pantalla que se ve (.pantallaApilada("Título") en iOS).
 *  `izquierda` reemplaza el «‹ volver» (ToolbarItem(placement: .cancellationAction)); `acciones`, lo de la derecha. */
@Composable
fun BarraDe(titulo: String? = null, oculta: Boolean = false, izquierda: (@Composable RowScope.() -> Unit)? = null, acciones: (@Composable RowScope.() -> Unit)? = null, grande: Boolean = false) {
    val b = LocalBarra.current
    SideEffect { b.titulo = titulo; b.oculta = oculta; b.acciones = acciones; b.izquierda = izquierda; b.grande = grande }
}

/** `.pantallaApilada(titulo)` de iOS (Tema/Cabecera.swift): el fondo con grano (FondoApp) y el título chico en la barra
 *  nativa, con lo de `.toolbar`: `acciones` a la derecha (.primaryAction) e `izquierda` en lugar del «‹ volver»
 *  (.cancellationAction). `grande`: `.navigationBarTitleDisplayMode(.large)` (el título grande arriba del contenido y la
 *  barra sin título). Es la única pieza de pantalla apilada: todas las pantallas pasan por acá. */
@Composable
fun PantallaApilada(
    titulo: String,
    modifier: Modifier = Modifier,
    grande: Boolean = false,
    izquierda: (@Composable RowScope.() -> Unit)? = null,
    acciones: (@Composable RowScope.() -> Unit)? = null,
    contenido: @Composable BoxScope.() -> Unit,
) {
    BarraDe(titulo, izquierda = izquierda, acciones = acciones, grande = grande)
    Box(modifier.fillMaxSize()) {
        FondoApp()
        if (grande) {
            // El título grande se va con el desplazamiento y vuelve al llegar arriba (`.large` en iOS): lo que se corre
            // para arriba primero lo esconde; para abajo, lo muestra sólo con el contenido ya arriba (o con el envión).
            val b = LocalBarra.current
            val tiron = remember { Tiron() }
            val conexion = remember(b, tiron) { TituloQueSeAchica(b, tiron) }
            CompositionLocalProvider(LocalTiron provides tiron) {
                Column(Modifier.fillMaxSize().nestedScroll(conexion)) {
                    Box(Modifier.fillMaxWidth().clipToBounds().layout { m, c ->
                        val p = m.measure(c)
                        conexion.alto = p.height.toFloat()
                        val corrido = conexion.corrido.coerceIn(0f, p.height.toFloat()).roundToInt()
                        layout(p.width, p.height - corrido) { p.place(0, -corrido) }
                    }) {
                        Texto(titulo, Estilo.tituloGrande, C.texto, Modifier.padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 8.dp), lineas = 1)
                    }
                    Box(Modifier.weight(1f).fillMaxWidth(), content = contenido)
                }
            }
        } else contenido()
    }
}

/** El desplazamiento del título grande de [PantallaApilada]: `corrido` es cuánto se escondió (0 a `alto`) y
 *  `posicion`, cuánto se corrió el contenido (0 arriba, negativo al bajar). Lo tirado de un [Refrescable] ([Tiron])
 *  se recoge antes de esconder el título. */
private class TituloQueSeAchica(private val barra: Barra, private val tiron: Tiron) : NestedScrollConnection {
    var alto by mutableFloatStateOf(0f)
    var corrido by mutableFloatStateOf(0f)
    private var posicion = 0f

    private fun correr(d: Float): Float {
        val antes = corrido
        corrido = (corrido - d).coerceIn(0f, alto)
        barra.colapso = if (alto > 0f) corrido / alto else 0f
        return antes - corrido
    }

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        val y = available.y
        if (y < 0f && corrido < alto && tiron.tirado <= 0f) return Offset(0f, correr(y))
        if (y > 0f && corrido > 0f && posicion >= -0.5f) return Offset(0f, correr(y))
        return Offset.Zero
    }

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        posicion = minOf(0f, posicion + consumed.y)
        // El envión que llegó arriba con lo que sobra muestra el título.
        if (available.y > 0f && corrido > 0f) return Offset(0f, correr(available.y))
        return Offset.Zero
    }
}

/** Un botón de texto de la barra (Button("Listo") en un ToolbarItem): Acción, 17. */
@Composable
fun BotonBarra(texto: String, fuerte: Boolean = false, habilitado: Boolean = true, cargando: Boolean = false, alTocar: () -> Unit) {
    Box(Modifier.defaultMinSize(minHeight = 44.dp).clickable(enabled = habilitado && !cargando, onClick = alTocar).padding(horizontal = 8.dp).alpha(if (habilitado) 1f else 0.4f),
        contentAlignment = Alignment.Center) {
        if (cargando) Rueda() else Texto(texto, if (fuerte) Estilo.headline else Estilo.body, C.accion)
    }
}

/** Un botón con ícono de la barra (los ToolbarItem con Label o Image). `habilitado = false` es `.disabled`. */
@Composable
fun BotonBarraIcono(simbolo: String, etiqueta: String, habilitado: Boolean = true, alTocar: () -> Unit) {
    // Apagado (`.disabled` en iOS): gris, no toca y TalkBack lo dice atenuado.
    Box(Modifier.size(44.dp).clickable(enabled = habilitado, onClick = alTocar).semantics { contentDescription = etiqueta; if (!habilitado) disabled() },
        contentAlignment = Alignment.Center) {
        Icono(simbolo, 19f, if (habilitado) C.accion else C.texto3)
    }
}

/** La barra nativa de las pantallas apiladas, con la tipografía y los colores del handoff: back con chevron y el título
 *  del padre en Acción, título 17/600 (Apariencia en iOS). */
@Composable
fun BarraNavegacion(b: Barra, atras: String?, volver: () -> Unit) {
    if (b.oculta) return
    Box(Modifier.fillMaxWidth().background(tema(0xF3EFE6, 0x0E1714).actual).statusBarsPadding().height(44.dp)) {
        Row(Modifier.align(Alignment.CenterStart).padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            val izq = b.izquierda
            if (izq != null) izq() else {
                Row(Modifier.defaultMinSize(minHeight = 44.dp).clickable(onClick = volver).padding(horizontal = 4.dp).semantics { contentDescription = atras ?: "Atrás" },
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Icono("chevron.left", 19f, C.accion)
                    if (atras != null) Texto(atras, Estilo.body, C.accion, Modifier.widthIn(max = 110.dp), lineas = 1)
                }
            }
        }
        // Con el título grande, el chico aparece cuando aquel terminó de esconderse.
        b.titulo?.let { Texto(it, Estilo.headline, tema(0x18201D, 0xE8EFE9).actual, Modifier.align(Alignment.Center).widthIn(max = 200.dp)
            .graphicsLayer { alpha = if (b.grande) ((b.colapso - 0.7f) / 0.3f).coerceIn(0f, 1f) else 1f }, lineas = 1, alinear = TextAlign.Center) }
        Row(Modifier.align(Alignment.CenterEnd).padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) { b.acciones?.invoke(this) }
    }
}

/** La app adentro (handoff · Navegación): cinco destinos (Inicio · Propiedades · MIRA · Mensajes · Más). MIRA es el
 *  botón oro elevado del centro y abre el abanico de acciones rápidas. Cada pestaña tiene su pila; Contactos,
 *  Operaciones, Cobranzas, Actas, Avisos y Perfil se apilan desde Inicio. */
@Composable
fun Principal() {
    val estado = LocalEstado.current
    val ciclo = LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val activa = ciclo.value.isAtLeast(Lifecycle.State.RESUMED)

    Box(Modifier.fillMaxSize().background(C.fondo)) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                // Las cuatro pilas, una encima de otra (sólo se ve la elegida y cada una guarda su estado). Se componen
                // las visitadas.
                val visitadas = remember { mutableStateListOf(estado.pestana) }
                if (estado.pestana != Pestana.mira && estado.pestana !in visitadas) visitadas.add(estado.pestana)
                listOf(Pestana.inicio, Pestana.propiedades, Pestana.mensajes, Pestana.mas).filter { it in visitadas }.forEach { p ->
                    key(p) {
                        val visible = estado.pestana == p
                        val a by animateFloatAsState(if (visible) 1f else 0f, tween(180), label = "pestana")
                        Box(Modifier.fillMaxSize().zIndex(if (visible) 1f else 0f).alpha(a).deshabilitado(!visible, a)) { Pila(p) }
                    }
                }
                BackHandler(estado.pila(estado.pestana).isNotEmpty()) { estado.volver() }
                // El abanico del botón del medio, sobre el contenido (la barra queda a la vista).
                androidx.compose.animation.AnimatedVisibility(estado.accionesAbiertas, Modifier.zIndex(3f), enter = fadeIn(), exit = fadeOut()) { AccionesRapidas() }
                BackHandler(estado.accionesAbiertas) { estado.accionesAbiertas = false }
            }
            BarraPestanas()
        }

        AnimatedVisibility(estado.aviso != null, Modifier.align(Alignment.BottomCenter).zIndex(5f).navigationBarsPadding()
            .padding(start = 12.dp, end = 12.dp, bottom = 96.dp),
            enter = slideInVertically(spring()) { it } + fadeIn(), exit = slideOutVertically(spring()) { it } + fadeOut()) {
            estado.aviso?.let { a -> VistaAviso(a) { estado.aviso = null } }
        }

        Box(Modifier.zIndex(10f)) { Recorrido(estado.mostrarRecorrido, { estado.mostrarRecorrido = it }) }

        // Las hojas de alta que abre una acción rápida.
        val h = estado.hojaRapida
        Hoja(h != null, { estado.hojaRapida = null }) {
            when (h) {
                HojaRapida.contacto -> EditarContacto()
                HojaRapida.visita -> NuevaCita(dia = Instant.now())
                HojaRapida.contrato -> AltaContrato()
                null -> {}
            }
        }

        // MIRA, el alta de la inmobiliaria y la bienvenida, a pantalla completa (fullScreenCover).
        PantallaCompleta(estado.miraAbierta, { estado.miraAbierta = false }) { MiraRaiz() }
        val alta = estado.altaAbierta
        PantallaCompleta(alta != null, { estado.altaAbierta = null }) { alta?.let { AltaInmobiliaria(inicial = it.clave) } }
        PantallaCompleta(estado.bienvenidaAbierta, { estado.bienvenidaAbierta = false; estado.mostrarRecorrido = true }) { Bienvenida() }
    }

    LaunchedEffect(Unit) {
        // La primera vez (en cualquier aparato): quien se suma a un equipo pasa antes por su bienvenida (al cerrarla sigue
        // el recorrido); el dueño, directo al recorrido (el alta va aparte).
        if (!ModoPrueba.activo && estado.usuario?.tourVisto != true) {
            val g = estado.api.opcional<Guia>("/v1/panel/guia")
            if (g != null && g.tourVisto != true) {
                if (estado.esDuenio) estado.mostrarRecorrido = true else estado.bienvenidaAbierta = true
            }
        }
    }
    // La suscripción (avisos de prueba y sólo lectura) y las insignias, al día con el tiempo real.
    LaunchedEffect(estado.version) {
        estado.cargarSuscripcion()
        Insignias.compartidas.refrescar(estado)
    }
    // Las insignias: al volver y cada minuto.
    LaunchedEffect(activa) {
        while (activa) {
            delay(60_000)
            Insignias.compartidas.refrescar(estado)
        }
    }
    // En vivo (app/Vivo.kt) con la app adelante; se corta al ir al fondo y vuelve al volver.
    LaunchedEffect(activa, estado.sesion?.inmobiliariaId) {
        if (activa) estado.escucharEnVivo()
    }
    // Lo que quedó pendiente de un link, un aviso o un atajo con la app cerrada.
    LaunchedEffect(Unit) { Abrir.pendiente?.let { Abrir.pendiente = null; estado.ir(it) } }
}

/** fullScreenCover de iOS: sube desde abajo, tapa todo (también la barra) y «atrás» lo cierra (salvo `cerrable = false`). */
@Composable
fun PantallaCompleta(visible: Boolean, alCerrar: () -> Unit, cerrable: Boolean = true, contenido: @Composable () -> Unit) {
    AnimatedVisibility(visible, Modifier.zIndex(8f), enter = slideInVertically(tween(350)) { it }, exit = slideOutVertically(tween(300)) { it }) {
        // Antes que el contenido: así un BackHandler de la pantalla (que se registra después) le gana a éste.
        // `cerrable = false` es `.interactiveDismissDisabled()`: «atrás» no la cierra.
        BackHandler(enabled = cerrable) { alCerrar() }
        CompositionLocalProvider(LocalCerrarHoja provides alCerrar) {
            Box(Modifier.fillMaxSize().background(C.fondo).clickable(remember { MutableInteractionSource() }, null) {}) { contenido() }
        }
    }
}

/** El nombre de la raíz de cada pestaña (el «‹ Inicio» de lo apilado encima). */
private fun nombreDe(p: Pestana): String = when (p) {
    Pestana.inicio -> "Inicio"
    Pestana.propiedades -> "Propiedades"
    Pestana.mensajes -> "Mensajes"
    Pestana.mas -> "Más"
    Pestana.mira -> "MIRA"
}

/** Una pila (NavigationStack): la raíz sin barra y las de adentro con la barra nativa. Se componen todas (cada una guarda
 *  su estado, como en iOS) y se ve la última, que entra deslizándose. */
@Composable
private fun Pila(p: Pestana) {
    val estado = LocalEstado.current
    val pila = estado.pila(p)
    val barras = remember { mutableStateListOf<Barra>() }
    while (barras.size < pila.size) barras.add(Barra())
    Box(Modifier.fillMaxSize()) {
        val enRaiz = pila.isEmpty()
        Box(Modifier.fillMaxSize().statusBarsPadding().deshabilitado(!enRaiz, if (enRaiz) 1f else 0f)) { Registro.Raiz(p) }
        pila.forEachIndexed { i, d ->
            key(i, d) {
                val arriba = i == pila.size - 1
                val barra = barras[i]
                val atras = if (i == 0) nombreDe(p) else barras.getOrNull(i - 1)?.titulo
                val ancho = with(LocalDensity.current) { 400.dp.toPx() }
                val entrada = remember { Animatable(if (animacionesActivasSinComposicion) ancho else 0f) }
                LaunchedEffect(Unit) { entrada.animateTo(0f, tween(300)) }
                CompositionLocalProvider(LocalBarra provides barra, LocalCerrarHoja provides { estado.volver() }) {
                    Column(Modifier.fillMaxSize().zIndex(i + 1f).graphicsLayer { translationX = entrada.value }.background(C.fondo)
                        .deshabilitado(!arriba, if (arriba) 1f else 0f)) {
                        BarraNavegacion(barra, atras) { estado.volver() }
                        Box(Modifier.weight(1f).fillMaxWidth().then(if (barra.oculta) Modifier.statusBarsPadding() else Modifier)) { Registro.Destino(d) }
                    }
                }
            }
        }
    }
}

/** Para arrancar las animaciones de entrada (la de la pila respeta «Reducir movimiento» con su duración del sistema). */
private const val animacionesActivasSinComposicion = true

/** La barra de pestañas del handoff, con el botón de MIRA (58, degradé oro, aro del color de fondo). */
@Composable
fun BarraPestanas() {
    val estado = LocalEstado.current
    val items = listOf(
        Triple(Pestana.inicio, "Inicio", "house"),
        Triple(Pestana.propiedades, "Propiedades", "building.2"),
        Triple(Pestana.mira, "MIRA", ""),
        Triple(Pestana.mensajes, "Mensajes", "bubble.left.and.bubble.right"),
        Triple(Pestana.mas, "Más", "square.grid.2x2"),
    )
    val fondo = tema(0xFAF8F2, 0x111314, alfa = 0.94f, alfaOscuro = 0.92f).actual
    Column(Modifier.fillMaxWidth().background(fondo)) {
        Box(Modifier.fillMaxWidth().height(0.5.dp).background(C.linea))
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 4.dp).padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            items.forEach { (p, nombre, icono) ->
                val activa = estado.pestana == p
                Tocable({
                    if (p == Pestana.mira) {
                        Haptico.impacto()
                        estado.accionesAbiertas = !estado.accionesAbiertas
                    } else if (estado.pestana == p) {
                        estado.accionesAbiertas = false
                        // Tocar la pestaña activa vuelve al principio.
                        estado.pila(p).clear()
                    } else {
                        Haptico.seleccion()
                        estado.accionesAbiertas = false
                        estado.pestana = p
                    }
                }, Modifier.weight(1f).heightIn(min = 50.dp).semantics {
                    contentDescription = if (p == Pestana.mira) (if (estado.accionesAbiertas) "Cerrar las acciones rápidas" else "Acciones rápidas: captar, cargar, cobrar, editar la web") else nombre
                    selected = activa
                }) {
                    if (p == Pestana.mira) {
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            BotonMira(abierto = estado.accionesAbiertas)
                            Texto("Crear", Estilo.tab, C.texto3)
                        }
                    } else {
                        val color = if (activa) C.accion else C.texto3
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Box(Modifier.height(27.dp), contentAlignment = Alignment.Center) {
                                Icono(if (activa) "$icono.fill" else icono, 22f, color, peso = Peso.medium)
                                // .overlay(alignment: .topTrailing) con .offset(x: 14, y: -4).
                                val n = insignia(estado, p)
                                if (n != null && n > 0) Box(Modifier.matchParentSize(), contentAlignment = Alignment.TopEnd) {
                                    Box(Modifier.layout { m, c ->
                                        val q = m.measure(c.copy(minWidth = 0, minHeight = 0, maxWidth = Constraints.Infinity))
                                        layout(0, 0) { q.place(-q.width + 14.dp.roundToPx(), (-4).dp.roundToPx()) }
                                    }) { InsigniaRoja(n) }
                                }
                            }
                            Texto(nombre, Estilo.tab, color, lineas = 1)
                        }
                    }
                }
            }
        }
    }
}

/** Mensajes: la bandeja (WhatsApp sin leer y preguntas de Mercado Libre); Más: los cobros atrasados. */
private fun insignia(estado: Estado, p: Pestana): Int? = when (p) {
    Pestana.mensajes -> estado.sinLeer
    Pestana.mas -> Insignias.compartidas.cobranzas
    else -> null
}

/** El botón oro de MIRA: 58, degradé #f7cb73 → #f2bb57 → #e9ad44, aro de 4 del fondo y el «+» en verde 0f3530 (gira a
 *  «×» con el abanico abierto). */
@Composable
fun BotonMira(tam: androidx.compose.ui.unit.Dp = 58.dp, abierto: Boolean = false) {
    val giro by animateFloatAsState(if (abierto) 45f else 0f, spring(dampingRatio = 0.7f), label = "mira")
    // Como en iOS (frame tam, aro con padding(-4) y .padding(.top, -18)): ocupa tam de ancho y tam − 18 de alto, y el
    // botón con su aro sube 18 por encima.
    Box(Modifier.layout { m, _ ->
        val lado = (tam + 8.dp).roundToPx()
        val p = m.measure(Constraints.fixed(lado, lado))
        layout(tam.roundToPx(), (tam - 18.dp).roundToPx()) { p.place(-4.dp.roundToPx(), -(22.dp).roundToPx()) }
    }.sombra(CircleShape, hex(0x7A5200, 0.7f), 11.dp, y = 12.dp).clip(CircleShape).background(C.fondo).padding(4.dp)
        .clip(CircleShape).background(Brush.verticalGradient(0f to hex(0xF7CB73), 0.55f to hex(0xF2BB57), 1f to hex(0xE9AD44)))
        .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape), contentAlignment = Alignment.Center) {
        Icono("plus", 26f, C.verde800, Modifier.rotate(giro))
    }
}

/** Las cuatro esquinas del visor de MIRA (3 de grosor, radio 3). */
@Composable
fun Visor(modifier: Modifier = Modifier, color: Color = C.verde800, grosor: androidx.compose.ui.unit.Dp = 3.dp) {
    Canvas(modifier) {
        val w = size.width; val h = size.height; val l = w / 3
        val g = grosor.toPx()
        val trazo = Stroke(g, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun esquina(a: Offset, b: Offset, c: Offset) {
            drawPath(androidx.compose.ui.graphics.Path().apply { moveTo(a.x, a.y); lineTo(b.x, b.y); lineTo(c.x, c.y) }, color, style = trazo)
        }
        esquina(Offset(0f, l), Offset(0f, 0f), Offset(l, 0f))
        esquina(Offset(w - l, 0f), Offset(w, 0f), Offset(w, l))
        esquina(Offset(0f, h - l), Offset(0f, h), Offset(l, h))
        esquina(Offset(w - l, h), Offset(w, h), Offset(w, h - l))
    }
}

/** 1D «Corredor está bloqueada»: tapa todo hasta identificarse con la huella o la cara. */
@Composable
fun Bloqueo() {
    val estado = LocalEstado.current
    Box(Modifier.fillMaxSize().clickable(remember { MutableInteractionSource() }, null) {}) {
        FondoVerde(patron = 0.08)
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Imagen(Imagenes.iconoRedondo, Modifier.size(84.dp).sombra(RoundedCornerShape(20.dp), Color.Black.copy(alpha = 0.6f), 20.dp, y = 20.dp).clip(RoundedCornerShape(20.dp)), ContentScale.Fit)
            Texto("Corredor está bloqueada", Estilo(22f, Peso.bold, relativo = Relativo.title2), Color.White)
            Texto("Para cuidar los datos de tus clientes.", Estilo.subhead, hex(0xCFE0D8))
            Tocable({ estado.tarea { estado.desbloquear() } }, Modifier.padding(top = 24.dp), descripcion = "Desbloquear con tu huella o tu cara") {
                Box(Modifier.size(96.dp).clip(RoundedCornerShape(28.dp)).background(Color.White.copy(alpha = 0.08f))
                    .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(28.dp)), contentAlignment = Alignment.Center) {
                    Icono("faceid", 50f, C.oro)
                }
            }
            Texto("Tocá para usar tu huella o tu cara", Estilo.subheadFuerte, Color.White)
        }
    }
    LaunchedEffect(Unit) { estado.desbloquear() }
}

/** Las hojas de alta que abre una acción rápida. */
enum class HojaRapida { contacto, visita, contrato; val id: String get() = name }

/** El abanico del botón del medio: círculos en arco con las acciones rápidas (MIRA es una más). Llega a cosas que no
 *  están a mano en las pestañas, como editar la web. Las acciones las da la API (las mismas que el panel, sin las de
 *  rubros que la inmobiliaria no usa). */
@Composable
fun AccionesRapidas() {
    val estado = LocalEstado.current
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    data class Accion(val icono: String, val nombre: String, val destacada: Boolean = false, val hacer: () -> Unit)

    fun accion(clave: String, nombre: String): Accion? {
        val e = estado
        val icono = Lugares.rapidas[clave] ?: "circle"
        return when (clave) {
            "captar" -> Accion(icono, nombre, destacada = true) { e.miraAbierta = true }
            "propiedad" -> Accion(icono, nombre) { e.abrir(AAltaPropiedad()) }
            "contacto" -> Accion(icono, nombre) { e.hojaRapida = HojaRapida.contacto }
            "visita" -> Accion(icono, nombre) { e.hojaRapida = HojaRapida.visita }
            "contrato" -> Accion(icono, nombre) { e.hojaRapida = HojaRapida.contrato }
            "cobrar" -> Accion(icono, nombre) { e.abrir(ACobranzas()) }
            "web" -> Accion(icono, nombre) { e.abrir(ASeccion("web")) }
            "buscar" -> Accion(icono, nombre) { e.abrir(ABuscar()) }
            "agenda" -> Accion(icono, nombre) { e.abrir(AAgendaDia()) }
            "avisos" -> Accion(icono, nombre) { e.abrir(AAvisos()) }
            else -> null
        }
    }

    // Las de la API; sin ella todavía, las de siempre según el rol.
    val rapidas: List<Pair<String, String>> = estado.estructura?.rapidas?.map { it.clave to it.nombre } ?: if (estado.escribe) {
        listOf("captar" to "Captar", "propiedad" to "Propiedad", "contacto" to "Contacto", "visita" to "Visita", "contrato" to "Contrato", "cobrar" to "Cobrar") +
            (if (estado.esDuenio) listOf("web" to "Mi web") else emptyList())
    } else {
        listOf("buscar" to "Buscar", "agenda" to "Agenda", "avisos" to "Avisos")
    }
    val lista = rapidas.mapNotNull { accion(it.first, it.second) }

    fun cerrar() { estado.accionesAbiertas = false }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val ancho = maxWidth.value; val alto = maxHeight.value
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)).clickable(remember { MutableInteractionSource() }, null) { cerrar() }
            .semantics { contentDescription = "Cerrar las acciones rápidas" })
        val puntos = posicionesAcciones(lista.size, ancho, alto)
        val origen = Offset(ancho / 2, alto + 6)
        val densidad = LocalDensity.current.density
        lista.forEachIndexed { i, a ->
            // Cada una sale 25 ms después de la anterior (.delay(Double(i) * 0.025) en iOS).
            var sale by remember { mutableStateOf(false) }
            LaunchedEffect(visible) { delay(i * 25L); sale = visible }
            val t by animateFloatAsState(if (sale) 1f else 0f, spring(dampingRatio = 0.65f, stiffness = 300f), label = "accion$i")
            val destino = puntos[i]
            val x = origen.x + (destino.x - origen.x) * t; val y = origen.y + (destino.y - origen.y) * t
            // .position(punto) centra la acción entera (círculo y nombre) en el punto.
            Box(Modifier.offset { IntOffset(((x - tamanoAccion.first / 2) * densidad).roundToInt(), ((y - tamanoAccion.second / 2) * densidad).roundToInt()) }
                .size(tamanoAccion.first.dp, tamanoAccion.second.dp).graphicsLayer { scaleX = 0.3f + 0.7f * t; scaleY = 0.3f + 0.7f * t; alpha = t }) {
                Tocable({ Haptico.seleccion(); cerrar(); a.hacer() }, Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.size(56.dp).sombra(CircleShape, Color.Black.copy(alpha = 0.3f), 10.dp, y = 6.dp).clip(CircleShape)
                            .background(if (a.destacada) C.oro else C.superficie), contentAlignment = Alignment.Center) {
                            Icono(a.icono, 21f, if (a.destacada) C.verde800 else C.accion, peso = Peso.semibold)
                        }
                        // .lineLimit(1).fixedSize(): el nombre entero aunque pase el ancho, con la sombra de la letra.
                        androidx.compose.foundation.text.BasicText(a.nombre, Modifier.wrapContentWidth(unbounded = true),
                            style = Estilo(12f, Peso.bold, relativo = Relativo.caption).ts().copy(color = Color.White,
                                shadow = androidx.compose.ui.graphics.Shadow(Color.Black.copy(alpha = 0.5f), blurRadius = 3f * densidad)), maxLines = 1, softWrap = false)
                    }
                }
            }
        }
    }
}

/** El tamaño de cada acción: el círculo de 56 y el nombre abajo (ancho, alto). */
val tamanoAccion: Pair<Float, Float> = 76f to (56f + 6f + 16f)

/** Dónde va cada acción (el centro de su círculo con el nombre), sobre el botón del medio. `alto` es lo que queda arriba
 *  de la barra. Hasta cuatro, en arco de 160° a 20° (en las puntas el nombre caía debajo de la barra). Con más no entran
 *  en un arco sin pisarse a 375: van en dos filas apenas curvas (la de abajo con la mitad más uno). */
fun posicionesAcciones(n: Int, ancho: Float, alto: Float): List<Offset> {
    if (n <= 0) return emptyList()
    val base = alto - 20 - tamanoAccion.second / 2
    if (n <= 4) {
        val radio = min(ancho / 2 - tamanoAccion.first / 2 - 12, 140f)
        val centro = Offset(ancho / 2, base + radio * 0.34f)
        return (0 until n).map { i ->
            val t = if (n > 1) i.toDouble() / (n - 1) else 0.5
            val angulo = (160 - 140 * t) * PI / 180
            Offset((centro.x + cos(angulo) * radio).toFloat(), (centro.y - sin(angulo) * radio).toFloat())
        }
    }
    val abajo = (n + 1) / 2
    val paso = min((ancho - 24) / abajo, 88f)
    fun fila(cuantos: Int, y: Float): List<Offset> = (0 until cuantos).map { i ->
        val x = ancho / 2 + (i - (cuantos - 1) / 2f) * paso
        // Apenas curva: las del medio un poco más arriba.
        val d = abs(x - ancho / 2) / maxOf(ancho / 2, 1f)
        Offset(x, y - 12 * (1 - d * d))
    }
    return fila(n - abajo, base - tamanoAccion.second - 18) + fila(abajo, base)
}
