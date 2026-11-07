package ar.com.corredor.app.pantallas.cobranzas

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ar.com.corredor.app.app.*
import ar.com.corredor.app.nucleo.*
import ar.com.corredor.app.pantallas.mensajes.ElegirPropiedad
import ar.com.corredor.app.pantallas.propiedades.Propiedad
import ar.com.corredor.app.tema.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** Una propiedad de alquiler o temporada con su inventario y sus actas (web: (app)/inventarios). */
data class FilaInventario(
    val id: String,
    val direccion: String,
    val barrio: String?,
    val foto: String?,
    val items: Int,
    val ambientes: Int,
    val entrada: ActaCorta?,
    val salida: ActaCorta?,
) {
    data class ActaCorta(val id: String, val fecha: String?, val firmas: Int)
}

/** Inventarios: las propiedades de alquiler y temporada (con o sin inventario), cada una con su foto,
 *  cuántos ambientes e ítems tiene, sus actas en chips y el botón de lo que toca (Devolución, Recorrer
 *  o Empezar). Vistas: Todas, Con inventario, Sin inventario y Para devolver; «Para hacer» lleva a las
 *  actas con firmas pendientes. */
@Composable
fun ListaActas() {
    val estado = LocalEstado.current
    val alcance = rememberCoroutineScope()
    var filas by remember { mutableStateOf<List<FilaInventario>?>(null) }
    var error by remember { mutableStateOf<ErrorApi?>(null) }
    var vista by remember { mutableStateOf("todas") }
    var elegir by remember { mutableStateOf(false) }

    val l = filas ?: emptyList()
    val cuenta = mapOf(
        "todas" to l.size, "con" to l.count { it.items > 0 }, "sin" to l.count { it.items == 0 },
        "devolver" to l.count { it.entrada != null && it.salida == null },
    )
    val visibles = l.filter { f ->
        when (vista) {
            "con" -> f.items > 0
            "sin" -> f.items == 0
            "devolver" -> f.entrada != null && f.salida == null
            else -> true
        }
    }
    val sinFirmar = l.filter { f -> (f.entrada?.let { it.firmas < 3 } ?: false) || (f.salida?.let { it.firmas < 3 } ?: false) }

    /** Como la web: las propiedades de alquiler y temporada, y de cada una su inventario y sus actas,
     *  todo en paralelo. */
    suspend fun cargar() {
        val q = listOf("operacion" to "alquiler,temporario", "orden" to "actualizada", "limite" to "60")
        val props: List<Propiedad> = try {
            estado.api.get("/v1/propiedades", q + ("unidades" to "no"))
        } catch (e: CancellationException) { throw e } catch (e: ErrorApi) {
            if (e.sinConexion) { if (filas == null) error = e; return }
            try { estado.api.get("/v1/propiedades", q) } catch (e2: CancellationException) { throw e2 } catch (e2: ErrorApi) {
                if (filas == null) error = e2; return
            } catch (e2: Exception) { return }
        } catch (e: Exception) { return }
        val api = estado.api
        val armadas = coroutineScope {
            props.map { p ->
                async {
                    val items: List<ItemInventario> = api.opcional("/v1/propiedades/${p.id}/inventario") ?: emptyList()
                    val actas: List<Acta> = api.opcional("/v1/propiedades/${p.id}/actas") ?: emptyList()
                    val e = actas.firstOrNull { it.tipo == "entrada" }
                    val s = actas.firstOrNull { it.tipo == "salida" }
                    FilaInventario(
                        p.id, p.direccion ?: p.nombre, p.barrio, urlDeFoto(p.fotosUrl?.firstOrNull()),
                        items.size, items.map { it.ambiente }.toSet().size,
                        e?.let { FilaInventario.ActaCorta(it.id, it.fecha, it.firmas?.size ?: 0) },
                        s?.let { FilaInventario.ActaCorta(it.id, it.fecha, it.firmas?.size ?: 0) },
                    )
                }
            }.awaitAll()
        }
        val porId = armadas.associateBy { it.id }
        filas = props.mapNotNull { porId[it.id] }
        error = null
    }

    LaunchedEffect(estado.version) { cargar() }

    @Composable
    fun chip(t: String, tono: Tono, hacer: (() -> Unit)?) {
        Box(
            Modifier.heightIn(min = 26.dp).clip(CircleShape).background(tono.fondo).clickable(enabled = hacer != null) { hacer?.invoke() }
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center,
        ) { Texto(t, Estilo(12f, Peso.semibold), tono.tinta) }
    }

    PantallaApilada("Inventarios", grande = true, acciones = if (estado.escribe) ({ BotonBarraActa("plus", "Elegir una propiedad") { elegir = true } }) else null) {
        Refrescable({ cargar() }) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Texto(Formato.sinViudas("Se hace caminando: un ambiente por pantalla, y el acta sale sola."), Estilo.subhead, C.texto2)
                val err = error
                if (filas != null) {
                    // Chips
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("todas" to "Todas", "con" to "Con inventario", "sin" to "Sin inventario", "devolver" to "Para devolver").forEach { (k, t) ->
                            val on = vista == k
                            Box(
                                Modifier.height(36.dp).clip(CircleShape).background(if (on) C.accion else C.superficie)
                                    .clickable { vista = k; Haptico.seleccion() }.semantics { selected = on }.padding(horizontal = 14.dp),
                                contentAlignment = Alignment.Center,
                            ) { Texto("$t · ${cuenta[k] ?: 0}", Estilo(14f, Peso.semibold), if (on) C.sobreAccion else C.texto) }
                        }
                    }
                    if (sinFirmar.isNotEmpty()) {
                        // Para hacer
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            TituloBloque("Para hacer")
                            Column(Modifier.fillMaxWidth().tarjeta()) {
                                sinFirmar.take(5).forEachIndexed { i, f ->
                                    val a = if (f.salida?.let { it.firmas < 3 } == true) f.salida else f.entrada
                                    if (i > 0) LineaFila()
                                    Row(
                                        Modifier.fillMaxWidth().clickable { a?.let { estado.abrir(AFirmas(id = "${f.id}/${it.id}")) } }
                                            .padding(horizontal = 14.dp, vertical = 10.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        IconoTono("signature", Tono.oro)
                                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            Texto(f.direccion, Estilo.filaLista, C.texto)
                                            Texto("Acta con firmas pendientes", Estilo.footnote, C.avisoTinta)
                                        }
                                        Chevron()
                                    }
                                }
                            }
                        }
                    }
                    if (visibles.isEmpty()) {
                        Box(Modifier.padding(top = 24.dp)) {
                            EstadoVacio("checklist", if (vista == "devolver") "No hay devoluciones pendientes" else "Todavía no hay inventarios",
                                "El inventario se arma en la entrega: ambiente por ambiente, con foto y estado de cada ítem.",
                                accion = if (estado.escribe) ("Elegir una propiedad" to { elegir = true }) else null)
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            visibles.forEach { f ->
                                Column(Modifier.fillMaxWidth().tarjeta().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Row(
                                        Modifier.fillMaxWidth().clickable { estado.abrir(AInventario(id = f.id)) }.semantics { contentDescription = "Recorrer ${f.direccion}" },
                                        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Box(Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                                            if (f.foto != null) Foto(f.foto, Modifier.fillMaxSize())
                                            else Box(Modifier.fillMaxSize().background(C.hundida), contentAlignment = Alignment.Center) { Icono("camera", 20f, C.texto3) }
                                        }
                                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            Texto(f.direccion, Estilo.filaLista, C.texto, lineas = 2)
                                            Texto(
                                                if (f.items > 0) "${Formato.cantidad(f.ambientes, "ambiente", "ambientes")} · ${Formato.cantidad(f.items, "ítem", "ítems")}"
                                                else "Sin inventario${f.barrio?.let { " · $it" } ?: ""}",
                                                Estilo.footnote, C.texto2,
                                            )
                                        }
                                    }
                                    // Chips de actas
                                    if (f.entrada != null || f.salida != null) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            f.entrada?.let { e ->
                                                chip("Entrega ${Inv.numerica(e.fecha)}${if (e.firmas < 3) " · faltan firmas" else ""}", if (e.firmas < 3) Tono.oro else Tono.verde) {
                                                    estado.abrir(AFirmas(id = "${f.id}/${e.id}"))
                                                }
                                            }
                                            val s = f.salida
                                            if (s != null) {
                                                chip("Devolución ${Inv.numerica(s.fecha)}", if (s.firmas < 3) Tono.oro else Tono.verde) { estado.abrir(AFirmas(id = "${f.id}/${s.id}")) }
                                            } else if (f.entrada != null) {
                                                chip("Sin acta de devolución", Tono.azul, null)
                                            }
                                        }
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        if (f.entrada != null && f.salida == null) {
                                            BotonHundido("Devolución", { estado.abrir(ADevolucion(id = f.id)) }, Modifier.weight(1f), simbolo = "arrow.left.arrow.right")
                                        }
                                        if (f.items > 0) {
                                            BotonHundido("Recorrer", { estado.abrir(AInventario(id = f.id)) }, Modifier.weight(1f), simbolo = "figure.walk")
                                        } else if (estado.escribe) {
                                            BotonAccion("Empezar", { estado.abrir(AInventario(id = f.id)) }, Modifier.weight(1f), simbolo = "plus")
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else if (err != null) {
                    EstadoError("los inventarios", err) { alcance.launch { cargar() } }
                } else {
                    EsqueletoFilas(filas = 5)
                }
            }
        }
    }

    Hoja(elegir, { elegir = false }) {
        ElegirPropiedad { p -> estado.abrir(AInventario(id = p.id)) }
    }
}
