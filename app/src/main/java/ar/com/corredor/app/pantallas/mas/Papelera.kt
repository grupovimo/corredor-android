package ar.com.corredor.app.pantallas.mas

import ar.com.corredor.app.app.PantallaApilada

import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ar.com.corredor.app.app.LocalEstado
import ar.com.corredor.app.nucleo.*
import ar.com.corredor.app.pantallas.propiedades.AReservaTemporada
import ar.com.corredor.app.pantallas.propiedades.diasCalendario
import ar.com.corredor.app.tema.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.time.Instant

// Clon de Pantallas/Mas/Papelera.swift.

/** Algo de la papelera (GET /v1/papelera; transversal.Borrado). */
@Serializable
data class BorradoAPI(
    val entidad: String,
    val id: String,
    val titulo: String,
    val detalle: String? = null,
    val borradoEn: FechaApi,
    /** Quién lo mandó a la papelera (de la auditoría). */
    val borradoPor: String? = null,
) {
    val clave: String get() = entidad + id
}

/** Lo estático de Papelera. */
object Papelera {
    /** El ícono, el nombre y adónde lleva cada tipo. */
    val entidades: Map<String, Pair<String, String>> = mapOf(
        "propiedad" to ("house" to "Propiedad"), "contacto" to ("person" to "Contacto"), "operacion" to ("rectangle.split.3x1" to "Operación"),
        "cita" to ("calendar" to "Cita"), "emprendimiento" to ("building" to "Emprendimiento"), "consorcio" to ("building.columns" to "Consorcio"),
        "unidad" to ("door.left.hand.closed" to "Unidad"), "contrato" to ("doc.text" to "Contrato"), "documento" to ("doc" to "Documento"),
        "reserva_temporada" to ("beach.umbrella" to "Reserva de temporada"),
    )

    fun detalle(b: BorradoAPI): String {
        val pasaron = diasCalendario(b.borradoEn, Instant.now())
        val quedan = 30 - pasaron
        return listOf(b.detalle, b.borradoPor?.split(" ")?.firstOrNull(), if (pasaron == 0) "hoy" else Formato.hace(b.borradoEn), if (quedan <= 27) "quedan ${maxOf(quedan, 0)}" else null)
            .mapNotNull { it?.takeIf { s -> s.isNotBlank() } }.joinToString(" · ")
    }

    fun destino(b: BorradoAPI): Any? = when (b.entidad) {
        "propiedad" -> AFichaPropiedad(id = b.id)
        "contacto" -> AFichaContacto(id = b.id)
        "operacion" -> AFichaOperacion(id = b.id)
        "cita" -> ACita(id = b.id)
        "emprendimiento" -> ASeccion(clave = "emprendimiento", id = b.id)
        "consorcio" -> ASeccion(clave = "consorcio", id = b.id)
        "contrato" -> AFichaContrato(id = b.id)
        "reserva_temporada" -> AReservaTemporada(id = b.id)
        else -> null
    }
}

/** La papelera: lo que se borra queda 30 días. Recuperarlo lo deja como estaba (sólo quien escribe); el dueño también
 *  lo puede borrar del todo (menos cobros y liquidaciones, que son historial). */
@Composable
fun Papelera() {
    val estado = LocalEstado.current
    val alcance = rememberCoroutineScope()
    val atras = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    val cerrar: () -> Unit = { atras?.onBackPressed() }
    var lista by remember { mutableStateOf<List<BorradoAPI>?>(null) }
    var error by remember { mutableStateOf<ErrorApi?>(null) }
    var ocultos by remember { mutableStateOf<Set<String>>(emptySet()) }
    var borrando by remember { mutableStateOf<BorradoAPI?>(null) }
    val visibles = (lista ?: emptyList()).filter { it.clave !in ocultos }

    suspend fun cargar() {
        try {
            lista = estado.api.get<List<BorradoAPI>>("/v1/papelera")
            ocultos = emptySet()
            error = null
        } catch (e: CancellationException) { throw e } catch (e: ErrorApi) { error = e } catch (e: Exception) {}
    }

    suspend fun recuperar(b: BorradoAPI) {
        ocultos = ocultos + b.clave
        try {
            estado.api.hacer("POST", "/v1/papelera/${b.entidad}/${b.id}/restaurar")
            estado.version += 1
            val d = Papelera.destino(b)
            if (d != null) estado.sugerir("Recuperaste ${b.titulo}", "Ver", d) else estado.avisar("Recuperaste ${b.titulo}")
        } catch (e: CancellationException) { throw e } catch (e: Exception) {
            ocultos = ocultos - b.clave
            estado.sugerir("No se recuperó · ${(e as? ErrorApi)?.detalle ?: "probá de nuevo"}", "Reintentar") { alcance.launch { recuperar(b) } }
        }
    }

    suspend fun borrarDelTodo(b: BorradoAPI) {
        borrando = null
        try {
            estado.api.hacer("DELETE", "/v1/papelera/${b.entidad}/${b.id}")
            ocultos = ocultos + b.clave
            estado.avisar("${b.titulo} se borró del todo")
        } catch (e: CancellationException) { throw e } catch (e: Exception) { estado.avisar(e) }
    }

    @Composable
    fun fila(b: BorradoAPI, primera: Boolean) {
        val e = Papelera.entidades[b.entidad] ?: ("trash" to b.entidad)
        Box {
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconoTono(e.first, Tono.neutro, tam = 36.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Texto(b.titulo, Estilo.filaLista, C.texto, lineas = 1)
                    Texto("${e.second} · ${Papelera.detalle(b)}", Estilo.footnote, C.texto2, lineas = 2)
                }
                if (estado.esDuenio && b.entidad !in listOf("cobro", "liquidacion")) {
                    Box(Modifier.size(36.dp).clickable { borrando = b }.semantics { contentDescription = "Borrar del todo ${b.titulo}" }, contentAlignment = Alignment.Center) {
                        Icono("trash.slash", 17f, C.error)
                    }
                }
                if (estado.escribe) {
                    BotonTonal("Recuperar", { alcance.launch { recuperar(b) } }, alto = 32.dp)
                }
            }
            if (!primera) Box(Modifier.align(Alignment.TopStart).fillMaxWidth().height(0.5.dp).background(C.linea))
        }
    }

    LaunchedEffect(estado.version) { cargar() }

    PantallaApilada("Papelera") {
        Refrescable({ cargar() }) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Texto(Formato.sinViudas("Lo que se borra queda 30 días. Recuperarlo lo deja como estaba, con todo lo que tenía."), Estilo.subhead, C.texto2)
                val err = error
                if (lista != null) {
                    if (visibles.isEmpty()) {
                        Box(Modifier.padding(top = 30.dp)) {
                            EstadoVacio(
                                icono = "trash", titulo = "La papelera está vacía",
                                texto = "Lo que borres en los próximos días aparece acá y lo podés recuperar durante 30 días.",
                                accion = "Volver" to cerrar,
                            )
                        }
                    } else {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Texto(if (visibles.size == 1) "1 cosa" else "${visibles.size} cosas", Estilo.subheadFuerte, C.texto, Modifier.weight(1f))
                            Texto("se vacía a los 30 días", Estilo.footnote, C.texto2)
                        }
                        Column(Modifier.fillMaxWidth().tarjeta()) {
                            visibles.forEachIndexed { i, b -> key(b.clave) { fila(b, i == 0) } }
                        }
                    }
                } else if (err != null) {
                    Box(Modifier.padding(top = 30.dp)) { EstadoError("la papelera", err) { alcance.launch { cargar() } } }
                } else {
                    EsqueletoFilas(5)
                }
            }
        }
    }

    val b = borrando
    ar.com.corredor.app.tema.Confirmacion(
        visible = b != null, titulo = "¿Borrás del todo ${b?.titulo ?: ""}?", mensaje = "Sale de la papelera y no se puede recuperar.",
        opciones = listOf(OpcionConfirmacion("Borrar del todo", destructiva = true) { if (b != null) alcance.launch { borrarDelTodo(b) } }),
        alCerrar = { borrando = null }, volver = "No",
    )
}
