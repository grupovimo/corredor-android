package ar.com.corredor.app.tema

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import ar.com.corredor.app.nucleo.Formato
import coil3.compose.AsyncImage

// Lo que SwiftUI da hecho y usan muchas pantallas: la barra de una hoja con NavigationStack, el `.alert` (con o sin
// campo), las Section de un List/Form agrupado, `Label`, los botones de las tarjetas para aprobar (BotonAccion y
// BotonHundido de Inicio.swift), el chip de filtro y la foto de la API (Propiedades.swift). Una sola pieza por cosa.

// MARK: - La barra de una hoja

/** La barra de una hoja con NavigationStack (`.navigationTitle` inline + `.toolbar`): el título 17/600 al centro, lo de
 *  cancelar a la izquierda (`.cancellationAction`) y lo de confirmar a la derecha (`.confirmationAction`). */
@Composable
fun BarraHoja(titulo: String, izquierda: (@Composable RowScope.() -> Unit)? = null, derecha: (@Composable RowScope.() -> Unit)? = null) {
    // Como la barra de iOS: el título centrado en el ancho que dejan los botones (el mismo a cada lado, el del más
    // ancho) y con «…» si no entra; antes se montaba sobre «Seguir después».
    androidx.compose.ui.layout.Layout(
        content = {
            Row(verticalAlignment = Alignment.CenterVertically) { izquierda?.invoke(this) }
            Texto(titulo, Estilo.headline, C.texto, lineas = 1, alinear = TextAlign.Center)
            Row(verticalAlignment = Alignment.CenterVertically) { derecha?.invoke(this) }
        },
        modifier = Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 8.dp),
    ) { medibles, limites ->
        val libre = limites.copy(minWidth = 0, minHeight = 0)
        val izq = medibles[0].measure(libre)
        val der = medibles[2].measure(libre)
        val lado = maxOf(izq.width, der.width) + 8.dp.roundToPx()
        val ancho = limites.maxWidth
        val tit = medibles[1].measure(libre.copy(maxWidth = (ancho - 2 * lado).coerceAtLeast(0)))
        val alto = limites.maxHeight
        layout(ancho, alto) {
            izq.placeRelative(0, (alto - izq.height) / 2)
            tit.placeRelative((ancho - tit.width) / 2, (alto - tit.height) / 2)
            der.placeRelative(ancho - der.width, (alto - der.height) / 2)
        }
    }
}

/** La misma barra con los botones de texto de siempre: «Cancelar»/«Cerrar» a la izquierda y la confirmación (600) a la
 *  derecha. `null` no pone el botón. */
@Composable
fun BarraHoja(
    titulo: String,
    cancelar: String?,
    alCancelar: () -> Unit,
    confirmar: String? = null,
    confirmarHabilitado: Boolean = true,
    cargando: Boolean = false,
    alConfirmar: () -> Unit = {},
) {
    BarraHoja(
        titulo,
        izquierda = cancelar?.let { t -> { BotonHoja(t, alTocar = alCancelar) } },
        derecha = confirmar?.let { t -> { if (cargando) Box(Modifier.padding(horizontal = 12.dp)) { Rueda() } else BotonHoja(t, fuerte = true, habilitado = confirmarHabilitado, alTocar = alConfirmar) } },
    )
}

/** Un botón de texto de la barra de una hoja (Button("Cerrar") en un ToolbarItem): en Acción; `fuerte` es el de
 *  `.confirmationAction` (600). */
@Composable
fun BotonHoja(texto: String, fuerte: Boolean = false, habilitado: Boolean = true, color: Color = C.accion, alTocar: () -> Unit) {
    Box(
        Modifier.heightIn(min = 44.dp).clickable(enabled = habilitado, onClick = alTocar).padding(horizontal = 8.dp).alpha(if (habilitado) 1f else 0.4f),
        contentAlignment = Alignment.Center,
    ) { Texto(texto, if (fuerte) Estilo.headline else Estilo.body, color, lineas = 1) }
}

/** Un botón con ícono de la barra (ToolbarItem con Image o Label con systemImage), en Acción. */
@Composable
fun BotonHojaIcono(simbolo: String, etiqueta: String, habilitado: Boolean = true, alTocar: () -> Unit) {
    Box(
        Modifier.size(44.dp).clickable(enabled = habilitado, onClick = alTocar).alpha(if (habilitado) 1f else 0.4f)
            .then(Modifier.semantics { contentDescription = etiqueta }),
        contentAlignment = Alignment.Center,
    ) { Icono(simbolo, 19f, C.accion) }
}

/** Una hoja con NavigationStack + toolbar: la barra (Cancelar a la izquierda, el título, la confirmación a la derecha),
 *  el contenido con scroll y, si hay, la barra de acción fija abajo (`.safeAreaInset(edge: .bottom)`). Va adentro de
 *  `Hoja`; «Cancelar» la cierra. */
@Composable
fun MarcoHoja(
    titulo: String,
    cancelar: String? = "Cancelar",
    confirmar: String? = null,
    confirmarHabilitado: Boolean = true,
    alConfirmar: () -> Unit = {},
    abajo: (@Composable ColumnScope.() -> Unit)? = null,
    contenido: @Composable ColumnScope.() -> Unit,
) {
    val cerrar = LocalCerrarHoja.current
    Column(Modifier.fillMaxSize()) {
        BarraHoja(
            titulo,
            izquierda = cancelar?.let { t -> { BotonHoja(t, alTocar = cerrar) } },
            derecha = confirmar?.let { t ->
                {
                    Box(
                        Modifier.heightIn(min = 44.dp).widthIn(max = 150.dp).clickable(enabled = confirmarHabilitado, onClick = alConfirmar).padding(horizontal = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) { Texto(t, Estilo.headline, if (confirmarHabilitado) C.accion else C.texto3, lineas = 2, alinear = TextAlign.End) }
                }
            },
        )
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp), content = contenido,
        )
        if (abajo != null) {
            Column(Modifier.fillMaxWidth().background(C.elevado).padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp), content = abajo)
        }
    }
}

// MARK: - .alert

/** El rol de un botón de `.alert` (Button(role:)). */
enum class RolBoton { normal, cancelar, destructivo }

/** Un botón de `.alert`: el texto, su rol y lo que hace (corre antes de cerrar). */
data class BotonAlerta(val titulo: String, val rol: RolBoton = RolBoton.normal, val habilitado: Boolean = true, val accion: () -> Unit = {})

/** El `.alert` de iOS: título, mensaje, (opcional) lo de adentro (un campo) y los botones; el de cancelar va primero a la
 *  izquierda (o abajo si son más de dos), como en iOS. */
@Composable
fun Alerta(
    visible: Boolean,
    titulo: String,
    mensaje: String = "",
    botones: List<BotonAlerta>,
    alCerrar: () -> Unit,
    contenido: (@Composable ColumnScope.() -> Unit)? = null,
) {
    if (!visible) return
    Dialog(onDismissRequest = alCerrar) {
        val forma = RoundedCornerShape(14.dp)
        Column(Modifier.widthIn(max = 300.dp).fillMaxWidth().clip(forma).background(C.elevado)) {
            Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 19.dp, bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (titulo.isNotEmpty()) Texto(titulo, Estilo.headline, C.texto, alinear = TextAlign.Center)
                if (mensaje.isNotEmpty()) Texto(mensaje, Estilo.footnote, C.texto, alinear = TextAlign.Center)
                if (contenido != null) Column(Modifier.fillMaxWidth().padding(top = 10.dp), content = contenido)
            }
            val ordenados = botones.sortedBy { if (it.rol == RolBoton.cancelar) 0 else 1 }
            @Composable
            fun boton(b: BotonAlerta, m: Modifier) {
                Box(
                    m.heightIn(min = 44.dp).clickable(enabled = b.habilitado) { b.accion(); alCerrar() }.alpha(if (b.habilitado) 1f else 0.4f),
                    contentAlignment = Alignment.Center,
                ) {
                    Texto(
                        b.titulo, if (b.rol == RolBoton.cancelar) Estilo.headline else Estilo.body,
                        if (b.rol == RolBoton.destructivo) C.error else C.accion, alinear = TextAlign.Center, lineas = 1,
                    )
                }
            }
            Box(Modifier.fillMaxWidth().height(0.5.dp).background(C.linea2))
            if (ordenados.size == 2) {
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                    boton(ordenados[0], Modifier.weight(1f))
                    Box(Modifier.width(0.5.dp).heightIn(min = 44.dp).background(C.linea2))
                    boton(ordenados[1], Modifier.weight(1f))
                }
            } else {
                // Más de dos: uno debajo del otro, el de cancelar al final (como iOS).
                val pila = botones.filter { it.rol != RolBoton.cancelar } + botones.filter { it.rol == RolBoton.cancelar }
                pila.forEachIndexed { i, b ->
                    if (i > 0) Box(Modifier.fillMaxWidth().height(0.5.dp).background(C.linea2))
                    boton(b, Modifier.fillMaxWidth())
                }
            }
        }
    }
}

/** El `.alert` con un TextField de iOS: título, mensaje, el campo, «Cancelar» y la acción. */
@Composable
fun AlertaConCampo(
    visible: Boolean,
    titulo: String,
    mensaje: String = "",
    texto: String,
    alCambiar: (String) -> Unit,
    placeholder: String = "",
    cancelar: () -> Unit,
    boton: String,
    aceptar: () -> Unit,
    teclado: KeyboardType = KeyboardType.Text,
    habilitado: Boolean = true,
) {
    Alerta(
        visible = visible, titulo = titulo, mensaje = mensaje, alCerrar = cancelar,
        botones = listOf(BotonAlerta("Cancelar", RolBoton.cancelar, accion = {}), BotonAlerta(boton, habilitado = habilitado, accion = aceptar)),
    ) {
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(C.superficie).border(0.5.dp, C.linea2, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp)) {
            Entrada(texto, alCambiar, placeholder, Modifier.fillMaxWidth(), estilo = Estilo.subhead, teclado = teclado)
        }
    }
}

// MARK: - List / Form agrupado

/** Un List/Form agrupado de iOS (`.insetGrouped`): scroll vertical, margen 16 y las secciones separadas. */
@Composable
fun ListaAgrupada(modifier: Modifier = Modifier, contenido: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp), content = contenido,
    )
}

/** Una Section de List/Form: el encabezado (13, en mayúsculas, tinta 2), las filas en una tarjeta (entre filas va
 *  `Separador()`) y el pie (13, tinta 2). */
@Composable
fun SeccionLista(encabezado: String? = null, pie: String? = null, modifier: Modifier = Modifier, radio: Dp = 12.dp, contenido: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (!encabezado.isNullOrEmpty()) Texto(encabezado.uppercase(Formato.argentina), Estilo.footnote, C.texto2, Modifier.padding(horizontal = 16.dp))
        Column(Modifier.fillMaxWidth().tarjeta(radio = radio), content = contenido)
        if (!pie.isNullOrEmpty()) Texto(Formato.sinViudas(pie), Estilo.footnote, C.texto2, Modifier.padding(horizontal = 16.dp))
    }
}

/** Una fila de List: 16 de margen y 44 de alto mínimo; con `alTocar` es un botón. */
@Composable
fun FilaLista(alTocar: (() -> Unit)? = null, habilitado: Boolean = true, modifier: Modifier = Modifier, contenido: @Composable RowScope.() -> Unit) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 44.dp)
            .then(if (alTocar != null) Modifier.clickable(enabled = habilitado, onClick = alTocar) else Modifier)
            .alpha(if (habilitado) 1f else 0.5f)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), content = contenido,
    )
}

/** `Label(texto, systemImage:)` de SwiftUI: el ícono y el texto en una fila. */
@Composable
fun ConIcono(
    texto: String,
    simbolo: String,
    estilo: Estilo = Estilo.body,
    color: Color = C.accion,
    modifier: Modifier = Modifier,
    colorIcono: Color = color,
    tamIcono: Float? = null,
    lineas: Int = Int.MAX_VALUE,
    espacio: Dp = 8.dp,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(espacio)) {
        Icono(simbolo, tamIcono ?: estilo.tamano, colorIcono)
        Texto(texto, estilo, color, lineas = lineas)
    }
}

// MARK: - Botones de las tarjetas (Inicio.swift: BotonAccion y BotonHundido, dos ButtonStyle)

/** Lleno de Acción: 15/600, 44 de alto, radio 12; apretado, al 80 %. */
@Composable
fun BotonAccion(texto: String, alTocar: () -> Unit, modifier: Modifier = Modifier, simbolo: String? = null, habilitado: Boolean = true) {
    Tocable(alTocar = alTocar, modifier = modifier, habilitado = habilitado, hundir = false) { apretado ->
        Row(
            Modifier.fillMaxWidth().heightIn(min = 44.dp).alpha(if (!habilitado) 0.5f else if (apretado) 0.8f else 1f).clip(RoundedCornerShape(12.dp))
                .background(C.accion),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically,
        ) {
            if (simbolo != null) Icono(simbolo, 15f, C.sobreAccion)
            Texto(texto, Estilo(15f, Peso.semibold), C.sobreAccion, alinear = TextAlign.Center)
        }
    }
}

/** Su par hundido: 15/600 en tinta, 44 de alto, fondo `hundida`, radio 12. Sin relleno y en dos renglones si no entra, como iOS. */
@Composable
fun BotonHundido(texto: String, alTocar: () -> Unit, modifier: Modifier = Modifier, simbolo: String? = null, habilitado: Boolean = true) {
    Tocable(alTocar = alTocar, modifier = modifier, habilitado = habilitado, hundir = false) { apretado ->
        Row(
            Modifier.fillMaxWidth().heightIn(min = 44.dp).alpha(if (!habilitado) 0.5f else if (apretado) 0.8f else 1f).clip(RoundedCornerShape(12.dp))
                .background(C.hundida),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically,
        ) {
            if (simbolo != null) Icono(simbolo, 15f, C.texto)
            Texto(texto, Estilo(15f, Peso.semibold), C.texto, alinear = TextAlign.Center)
        }
    }
}

// MARK: - Propiedades.swift: ChipFiltro y Foto

/** Un chip de filtro (36 de alto, cápsula; el activo en Acción). `alMantener`: el contextMenu de iOS (mantener
 *  apretado), para las vistas guardadas. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChipFiltro(texto: String, activo: Boolean, alMantener: (() -> Unit)? = null, hacer: () -> Unit) {
    val forma = Capsula
    Box(
        Modifier.height(36.dp)
            .sombra(forma, if (activo) Color.Transparent else C.sombra.copy(alpha = 0.08f), 1.dp, y = 1.dp)
            .clip(forma).background(if (activo) C.accion else C.superficie)
            .combinedClickable(onClick = { Haptico.seleccion(); hacer() }, onLongClick = alMantener?.let { m -> { Haptico.impacto(); m() } })
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Texto(texto, Estilo(14f, Peso.semibold), if (activo) C.sobreAccion else C.texto, lineas = 1)
    }
}

/** Una foto de la API con su lugar mientras carga: llena el lugar que le den y se recorta ahí. */
@Composable
fun Foto(url: String?, modifier: Modifier = Modifier) {
    Box(modifier.clipToBounds().background(C.hundida), contentAlignment = Alignment.Center) {
        Icono("photo", 17f, C.texto3)
        if (url != null) AsyncImage(url, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
}
