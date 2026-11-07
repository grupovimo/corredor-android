package ar.com.corredor.app.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ar.com.corredor.app.nucleo.Formato
import ar.com.corredor.app.nucleo.Preferencias
import ar.com.corredor.app.tema.C
import ar.com.corredor.app.tema.Estilo
import ar.com.corredor.app.tema.Icono
import ar.com.corredor.app.tema.Texto
import ar.com.corredor.app.tema.tarjeta
import java.time.Duration
import java.time.Instant

// Clon de App/Consejos.swift. En Android no hay TipKit: las mismas reglas a mano. Consejos de una sola vez: lo que la
// app hace y no se descubre solo. Cada uno aparece en su pantalla, una vez, y se espacian (uno por día, como
// .displayFrequency(.daily)) para no amontonar. «Ver el recorrido de nuevo» (Más) los vuelve a mostrar.

/** Un consejo (un Tip): título, mensaje y el ícono. */
open class Consejo(val id: String, val titulo: String, val mensaje: String, val imagen: String)

object Consejos {
    private const val FRECUENCIA_HORAS = 24L

    /** Apagados en las pruebas (Tips.hideAllTipsForTesting en iOS). */
    fun configurar() {}

    /** Los vuelve a mostrar todos (con el recorrido). */
    fun reiniciar() {
        listOf("mandar", "cargar", "mas", "cobrar", "escanear", "siri").forEach { Preferencias.ponerBool("consejo-$it", false) }
        Preferencias.ponerTexto("consejo-ultimo", null)
        Preferencias.ponerTexto("consejo-actual", null)
    }

    fun puedeMostrar(c: Consejo): Boolean {
        if (ModoPrueba.activo) return false
        if (Preferencias.bool("consejo-${c.id}")) return false
        val ultimo = Preferencias.texto("consejo-ultimo")?.let { runCatching { Instant.parse(it) }.getOrNull() }
        val mostrandoEste = Preferencias.texto("consejo-actual") == c.id
        return mostrandoEste || ultimo == null || Duration.between(ultimo, Instant.now()).toHours() >= FRECUENCIA_HORAS
    }

    fun marcarVisto(c: Consejo) {
        val antes = Preferencias.texto("consejo-actual")
        Preferencias.ponerTexto("consejo-actual", c.id)
        if (Preferencias.texto("consejo-ultimo") == null || antes != c.id) Preferencias.ponerTexto("consejo-ultimo", Instant.now().toString())
    }

    fun cerrar(c: Consejo) {
        Preferencias.ponerBool("consejo-${c.id}", true)
        Preferencias.ponerTexto("consejo-actual", null)
        Preferencias.ponerTexto("consejo-ultimo", Instant.now().toString())
    }
}

/** Lista de propiedades: el logo de WhatsApp manda la ficha. */
class ConsejoMandar : Consejo("mandar", "Mandala en un toque", "Tocá el logo de WhatsApp para mandar la ficha. Mantené apretada una fila para editarla o pausarla.", "paperplane")

/** Inicio: el «+» carga cualquier cosa. */
class ConsejoCargar : Consejo("cargar", "Cargá cualquier cosa desde acá", "Propiedades, contactos, visitas, operaciones y contratos, sin buscar la sección.", "plus.circle")

/** Más: todo lo que no está en las pestañas. */
class ConsejoMas : Consejo("mas", "Todo lo demás está en «Más»", "Mi inmobiliaria (tu web, tus datos, tu equipo, tu plan) y las herramientas: cobros, contratos, consorcios.", "square.grid.2x2")

/** Ficha del contrato: cobrar y mandar el recibo. */
class ConsejoCobrar : Consejo("cobrar", "Cobrá y mandá el recibo", "Registrá el cobro y el recibo sale solo, listo para WhatsApp. Después le rendís al dueño.", "dollarsign.circle")

/** Documentos: escanear con la cámara. */
class ConsejoEscanear : Consejo("escanear", "Escaneá el contrato firmado", "Con la cámara: lo recortamos, lo pasamos a PDF y queda guardado acá.", "doc.viewfinder")

/** Perfil: los atajos y el widget (Siri en iOS). */
class ConsejoSiri : Consejo("siri", "Tus atajos, a mano", "Mantené apretado el ícono de Corredor para captar con MIRA o ver las consultas. Y sumá el widget a tu pantalla de inicio.", "mic")

/** Un consejo en tarjeta (TipView en iOS), con la cruz para cerrarlo. Si no corresponde, no ocupa lugar. */
@Composable
fun VistaConsejo(c: Consejo, modifier: Modifier = Modifier) {
    var visible by remember(c.id) { mutableStateOf(Consejos.puedeMostrar(c)) }
    if (!visible) return
    remember(c.id) { Consejos.marcarVisto(c); true }
    Row(modifier.fillMaxWidth().tarjeta().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icono(c.imagen, 24f, C.accion)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Texto(c.titulo, Estilo.headline, C.texto)
            Texto(Formato.sinViudas(c.mensaje), Estilo.subhead, C.texto2)
        }
        Box(Modifier.size(28.dp).clip(CircleShape).clickable { Consejos.cerrar(c); visible = false }.semantics { contentDescription = "Cerrar" },
            contentAlignment = androidx.compose.ui.Alignment.Center) { Icono("xmark", 14f, C.texto3) }
    }
}

/** TipView(ConsejoMandar()) de iOS. */
@Composable
fun TipView(c: Consejo, modifier: Modifier = Modifier) = VistaConsejo(c, modifier)
