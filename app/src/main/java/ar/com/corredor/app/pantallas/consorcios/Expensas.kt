package ar.com.corredor.app.pantallas.consorcios

import ar.com.corredor.app.nucleo.copiar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ar.com.corredor.app.app.LocalEstado
import ar.com.corredor.app.nucleo.ASeccion
import ar.com.corredor.app.nucleo.ArchivoPrevio
import ar.com.corredor.app.nucleo.Documentos
import ar.com.corredor.app.nucleo.ErrorApi
import ar.com.corredor.app.nucleo.VistaPrevia
import ar.com.corredor.app.nucleo.Formato
import ar.com.corredor.app.tema.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

// Clon de Pantallas/Consorcios/Expensas.swift.

// MARK: - Expensas

private val estadosPeriodo: Map<String, Pair<String, Tono>> = mapOf("borrador" to ("Cargando gastos" to Tono.oro), "liquidada" to ("Liquidada" to Tono.verde), "cerrada" to ("Cerrada" to Tono.neutro))

/** Expensas: los meses liquidados (y el que se está cargando) con su total, y el detalle del elegido: cada unidad con su
 *  recibo (A4 para imprimir y link público) y mandarlos por mail o por WhatsApp. */
@Composable
fun PestanaExpensasConsorcio(c: ConsorcioAPI, periodos: List<PeriodoAPI>) {
    val estado = LocalEstado.current
    val ctx = LocalContext.current
    val alcance = rememberCoroutineScope()
    var elegido by remember { mutableStateOf<String?>(null) }
    var ficha by remember { mutableStateOf<PeriodoAPI?>(null) }
    var error by remember { mutableStateOf<ErrorApi?>(null) }
    var mandando by remember { mutableStateOf(false) }
    var porWhatsApp by remember { mutableStateOf(false) }
    var imprimiendo by remember { mutableStateOf<String?>(null) }
    var previo by remember { mutableStateOf<ArchivoPrevio?>(null) }

    /** El elegido o, si no, el último liquidado. */
    val elegidoID: String? = elegido ?: periodos.firstOrNull { it.estado != "borrador" }?.id

    suspend fun cargar() {
        val id = elegidoID
        if (id == null) { ficha = null; return }
        try {
            ficha = estado.api.get("/v1/consorcios/${c.id}/periodos/$id")
            error = null
        } catch (e: CancellationException) { throw e } catch (e: Exception) {
            if (e is ErrorApi) { error = e; ficha = null }
        }
    }

    suspend fun enviar(f: PeriodoAPI) {
        mandando = true
        try {
            val r: EnvioMailExpensas = estado.api.enviar("POST", "/v1/consorcios/${c.id}/periodos/${f.id}/enviar", vacioConsorcio)
            val sin = r.sinEmail ?: emptyList()
            estado.avisar("${Cons.plural(r.encolados, "recibo sale", "recibos salen")} por mail${if (sin.isEmpty()) "" else " · ${sin.size} sin mail (${sin.joinToString(", ")})"}")
        } catch (e: CancellationException) { throw e } catch (e: Exception) {
            val t = (e as? ErrorApi)?.detalle ?: "Probá de nuevo."
            estado.sugerir("No se mandaron · $t", "Reintentar") { estado.tarea { enviar(f) } }
        }
        mandando = false
    }

    suspend fun whatsapp(f: PeriodoAPI) {
        porWhatsApp = true
        try {
            val w = ConsorcioWhatsApp.liquidaciones(estado.api, consorcio = c.id, periodo = f.id)
            if (w.sinWhatsApp && w.enviadas == 0) {
                estado.sugerir("Conectá el WhatsApp de la inmobiliaria para mandarlas por ahí", "Conectar", ASeccion(clave = "integraciones"))
            } else {
                val faltan = w.sinTelefono + w.fallidas
                estado.avisar("${Cons.plural(w.enviadas, "recibo sale", "recibos salen")} por WhatsApp${if (faltan.isEmpty()) "" else " · ${faltan.size} sin WhatsApp (${faltan.joinToString(", ")})"}")
            }
        } catch (e: CancellationException) { throw e } catch (e: Exception) {
            estado.sugerir("No se mandaron · WhatsApp no respondió. Probá de nuevo en un rato.", "Reintentar") { estado.tarea { whatsapp(f) } }
        }
        porWhatsApp = false
    }

    suspend fun link(l: LiquidacionExpensas) {
        try {
            val r: LinkConsorcio = estado.api.enviar("POST", "/v1/consorcios/${c.id}/liquidaciones/${l.id}/link", vacioConsorcio)
            copiar(ctx, r.url)
            estado.avisar("Link del recibo de ${l.unidad} copiado.")
        } catch (e: CancellationException) { throw e } catch (e: Exception) { estado.avisar(e) }
    }

    suspend fun imprimir(f: PeriodoAPI, unidad: String?) {
        imprimiendo = unidad ?: "todas"
        try {
            val u = Documentos.expensas(ctx, consorcio = c.id, periodo = f.id, unidad = unidad, api = estado.api)
            previo = ArchivoPrevio(u, "application/pdf")
        } catch (e: CancellationException) { throw e } catch (e: Exception) { estado.avisar(e) }
        imprimiendo = null
    }

    suspend fun cerrarPeriodo(f: PeriodoAPI) {
        try {
            estado.api.hacer("POST", "/v1/consorcios/${c.id}/periodos/${f.id}/cerrar")
            estado.avisar("Período cerrado")
            estado.version += 1
        } catch (e: CancellationException) { throw e } catch (e: Exception) { estado.avisar(e) }
    }

    LaunchedEffect(estado.version, elegidoID) { cargar() }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (periodos.isEmpty()) {
            EstadoVacio("doc.text", "Todavía no liquidaste expensas",
                "Cada mes cargás los gastos, mirás cómo se reparten y emitís. Acá quedan los meses liquidados con el recibo de cada unidad.",
                accion = if (estado.escribe) ("Liquidar ${Cons.nombreMes(Cons.aLiquidar(periodos).first)}" to { estado.abrir(ALiquidarConsorcio(consorcio = c.id)) }) else null)
        } else {
            // Meses
            BloqueConsorcio("Meses") {
                periodos.forEachIndexed { i, p ->
                    key(p.id) {
                        val e = estadosPeriodo[p.estado] ?: (p.estado to Tono.neutro)
                        Tocable({ if (p.estado == "borrador") estado.abrir(ALiquidarConsorcio(consorcio = c.id, periodo = p.periodo)) else elegido = p.id }, Modifier.semantics { selected = elegidoID == p.id }) {
                            RenglonConsorcio(primero = i == 0, modifier = Modifier.background(if (elegidoID == p.id) C.hundida else Color.Transparent)) {
                                DosLineasConsorcio(Cons.mesConAnio(p.periodo), if (p.estado == "borrador") "${Formato.plata(p.cargado ?: 0.0)} cargados" else Formato.plata(p.total ?: 0.0))
                                ChipConsorcio(e.first, e.second)
                                Chevron()
                            }
                        }
                    }
                }
            }
            val f = ficha
            val e = error
            if (f != null) {
                // Detalle
                val ls = (f.liquidaciones ?: emptyList()).sortedWith { a, b -> Cons.ordenUnidad.compare(a.unidad, b.unidad) }
                val gastos = f.gastos?.size ?: 0
                BloqueConsorcio("${Cons.mesConAnio(f.periodo)} · ${Formato.plata(f.total ?: 0.0)}", accion = "Imprimir" to { alcance.launch { imprimir(f, null) } }) {
                    RenglonConsorcio(primero = true) {
                        Texto("${Cons.plural(ls.size, "unidad", "unidades")} · ${Cons.plural(gastos, "gasto", "gastos")}${if ((f.fondoTotal ?: 0.0) > 0) " · fondo ${Formato.plata(f.fondoTotal ?: 0.0)}" else ""}",
                            Estilo.footnote, C.texto2)
                    }
                    if (estado.escribe) {
                        RenglonConsorcio {
                            BotonLabelConsorcio(if (porWhatsApp) "Mandando…" else "Por WhatsApp", "bubble.left", habilitado = !porWhatsApp) { alcance.launch { whatsapp(f) } }
                            Spacer(Modifier.weight(1f))
                            BotonLabelConsorcio(if (mandando) "Mandando…" else "Mandar por mail", "envelope", habilitado = !mandando) { alcance.launch { enviar(f) } }
                        }
                    }
                    ls.forEach { l ->
                        key(l.id) {
                            RenglonConsorcio {
                                Texto(l.unidad, Estilo.filaLista, C.texto, Modifier.widthIn(min = 44.dp))
                                DosLineasConsorcio(l.propietario ?: "Sin propietario",
                                    listOfNotNull(l.telefono.noVacio(), l.email.noVacio()).joinToString(" · ").noVacio() ?: "sin contacto · va impresa")
                                Texto(Cons.cifra(l.total), Estilo.footnoteFuerte, C.texto)
                                if (estado.escribe) {
                                    Box(Modifier.size(34.dp).clickable { alcance.launch { link(l) } }.semantics { contentDescription = "Copiar el link del recibo de ${l.unidad}" },
                                        contentAlignment = Alignment.Center) { Icono("link", 17f, C.accion) }
                                }
                                Box(Modifier.size(34.dp).clickable { alcance.launch { imprimir(f, l.unidadId) } }.semantics { contentDescription = "Imprimir el recibo de ${l.unidad}" },
                                    contentAlignment = Alignment.Center) {
                                    if (imprimiendo == l.unidadId) Rueda() else Icono("printer", 17f, C.accion)
                                }
                            }
                        }
                    }
                    if (f.estado == "liquidada" && estado.escribe) {
                        RenglonConsorcio {
                            Texto("Cerrar el período", Estilo.footnoteFuerte, C.texto2, Modifier.clickable { alcance.launch { cerrarPeriodo(f) } })
                        }
                    }
                }
            } else if (e != null) {
                EstadoError("el período", e) { alcance.launch { cargar() } }
            } else if (elegidoID != null) EsqueletoFilas(filas = 4)
        }
    }
    VistaPrevia(previo) { previo = null }
}
