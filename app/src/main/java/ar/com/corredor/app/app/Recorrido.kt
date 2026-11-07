package ar.com.corredor.app.app

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import ar.com.corredor.app.nucleo.Formato
import ar.com.corredor.app.nucleo.Pestana
import ar.com.corredor.app.nucleo.intentar
import ar.com.corredor.app.tema.*
import kotlinx.serialization.Serializable

/** El recorrido de la primera vez (8 pasos, poco texto): qué hay en cada pestaña y lo que no se ve a primera vista (la
 *  plata, la IA, la web). Se marca visto en la API (`PATCH /v1/panel/guia {tourVisto}`), así no vuelve ni en la web ni
 *  en otro teléfono. */
@Composable
fun Recorrido(visible: Boolean, alCambiar: (Boolean) -> Unit) {
    val estado = LocalEstado.current
    if (!visible) return
    // Cada vez que se abre (también desde «Más»), desde el primer paso (en iOS la vista se arma de nuevo).
    var paso by remember { mutableIntStateOf(0) }

    fun terminar() {
        alCambiar(false)
        estado.pestana = Pestana.inicio
        estado.tarea { intentar { estado.api.hacer("PATCH", "/v1/panel/guia", GuiaVista()) } }
    }

    fun siguiente() {
        Haptico.seleccion()
        if (paso + 1 >= pasosRecorrido.size) return terminar()
        paso += 1
        val x = pasosRecorrido[paso].pestana
        if (x != Pestana.mira) estado.pestana = x
    }

    LaunchedEffect(Unit) { pasosRecorrido[paso].pestana.let { if (it != Pestana.mira) estado.pestana = it } }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)).clickable(remember { MutableInteractionSource() }, null) { siguiente() })
        AnimatedContent(paso, transitionSpec = { (slideInVertically(spring()) { it / 3 } + fadeIn()) togetherWith fadeOut() }, label = "recorrido") { i ->
            val p = pasosRecorrido[i]
            val forma = RoundedCornerShape(24.dp)
            Column(
                Modifier.navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 100.dp).fillMaxWidth().sombraE3(forma).clip(forma).background(C.superficie)
                    .clickable(remember { MutableInteractionSource() }, null) {}.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconoTono(p.icono, if (p.pestana == Pestana.mira) Tono.oro else Tono.verde, 44.dp)
                    Spacer(Modifier.weight(1f))
                    Texto("${i + 1} de ${pasosRecorrido.size}", Estilo.footnote, C.texto3)
                }
                Texto(p.titulo, Estilo.titulo3, C.texto)
                Texto(Formato.sinViudas(p.texto), Estilo.subhead, C.texto2)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BotonTexto("Saltear", { terminar() }, Modifier.width(110.dp), color = C.texto2)
                    BotonAccion(if (i == pasosRecorrido.size - 1) "Empezar" else "Siguiente", { siguiente() }, Modifier.weight(1f))
                }
            }
        }
    }
}

@Serializable private data class GuiaVista(val tourVisto: Boolean = true)

private data class PasoRecorrido(val icono: String, val titulo: String, val texto: String, val pestana: Pestana)

private val pasosRecorrido = listOf(
    PasoRecorrido("house", "Tu día, de un vistazo", "Lo que hay que hacer ahora, tu próxima visita y lo que encontramos por vos. Con el «+» cargás cualquier cosa.", Pestana.inicio),
    PasoRecorrido("building.2", "Tu cartera", "Cargá una propiedad en un minuto y mandala por WhatsApp con un toque.", Pestana.propiedades),
    PasoRecorrido("plus", "El botón del medio", "Captá un cartel con MIRA, cargá una propiedad o un contacto, cobrá o editá tu web, en un toque.", Pestana.mira),
    PasoRecorrido("bubble.left.and.bubble.right", "Todas las consultas juntas", "WhatsApp, Mercado Libre y tu web en una sola bandeja.", Pestana.mensajes),
    PasoRecorrido("dollarsign.circle", "La plata del negocio", "Cobrá los alquileres, mandá el recibo y rendile al dueño, todo desde el celular.", Pestana.inicio),
    PasoRecorrido("sparkles", "La IA trabaja por vos", "Te sugiere a quién mandarle qué propiedad y prepara respuestas. Vos aprobás.", Pestana.inicio),
    PasoRecorrido("globe", "Tu web y los portales", "Tus propiedades en tu web y en Mercado Libre y los portales, sin cargar dos veces.", Pestana.mas),
    PasoRecorrido("square.grid.2x2", "Todo lo demás, en «Más»", "Arriba, Mi inmobiliaria: tu web, tus datos, tu equipo y tu plan. Abajo, las herramientas. Y claro u oscuro, a un toque.", Pestana.mas),
)
