package ar.com.corredor.app.pantallas.consorcios

import ar.com.corredor.app.app.PantallaApilada

import ar.com.corredor.app.nucleo.Formato

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import ar.com.corredor.app.app.BarraDe
import ar.com.corredor.app.app.BotonBarraIcono
import ar.com.corredor.app.app.LocalEstado
import ar.com.corredor.app.nucleo.opcional
import ar.com.corredor.app.tema.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.ZoneId

// Clon de Pantallas/Consorcios/Bienes.swift.

// MARK: - Bienes del consorcio

@Serializable
data class BienAPI(
    val id: String,
    val nombre: String,
    val categoria: String? = null,
    val ubicacion: String? = null,
    val cantidad: Int? = null,
    val estado: String? = null,
    val proximoService: String? = null,
    val responsable: String? = null,
    val notas: String? = null,
) {
    companion object {
        val categorias = listOf("matafuego" to "Matafuego", "ascensor" to "Ascensor", "bomba" to "Bomba", "tanque" to "Tanque", "luminaria" to "Luminaria",
            "motor" to "Motor", "mobiliario" to "Mobiliario", "herramienta" to "Herramienta", "otro" to "Otro")
        val estados = listOf("ok" to "En orden", "a_revisar" to "A revisar", "vencido" to "Vencido", "baja" to "De baja")
    }
}

@Serializable
private data class CuerpoBien(
    val nombre: String, val categoria: String, val ubicacion: String?, val cantidad: Int, val estado: String,
    val proximoService: String?, val responsable: String?, val notas: String?,
)

/** Los bienes del edificio (matafuegos, ascensores, bombas…) con su estado y el próximo service. */
@Composable
fun BienesConsorcio(consorcio: String) {
    val estado = LocalEstado.current
    val alcance = rememberCoroutineScope()
    var lista by remember { mutableStateOf<List<BienAPI>>(emptyList()) }
    var editando by remember { mutableStateOf<BienAPI?>(null) }

    suspend fun cargar() { lista = estado.api.opcional<List<BienAPI>>("/v1/consorcios/$consorcio/bienes") ?: emptyList() }

    suspend fun guardar(b: BienAPI) {
        val c = CuerpoBien(b.nombre, b.categoria ?: "otro", b.ubicacion.noVacio(), b.cantidad ?: 1, b.estado ?: "ok",
            b.proximoService, b.responsable.noVacio(), b.notas.noVacio())
        try {
            if (b.id.isEmpty()) estado.api.hacer("POST", "/v1/consorcios/$consorcio/bienes", c) else estado.api.hacer("PUT", "/v1/consorcios/$consorcio/bienes/${b.id}", c)
            cargar()
        } catch (e: CancellationException) { throw e } catch (e: Exception) { estado.avisar(e) }
    }

    fun nuevo() = BienAPI(id = "", nombre = "", categoria = "matafuego", cantidad = 1, estado = "ok")

    LaunchedEffect(estado.version) { cargar() }

    PantallaApilada("Bienes", acciones = if (estado.escribe) ({ BotonBarraIcono("plus", "Sumar") { editando = nuevo() } }) else null) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (lista.isEmpty()) {
                EstadoVacio("fire.extinguisher", "Sin bienes cargados", "Matafuegos, ascensores, bombas: con su próximo service, te avisamos antes.",
                    accion = if (estado.escribe) ("Sumar un bien" to { editando = nuevo() }) else null)
            }
            if (lista.isNotEmpty()) Column(Modifier.fillMaxWidth().tarjeta()) {
                lista.forEachIndexed { i, b ->
                    key(b.id) {
                        if (i > 0) Separador()
                        FilaDeslizable(derecha = if (estado.escribe) listOf(AccionFila("Borrar", "trash", C.error) {
                            alcance.launch {
                                try { estado.api.hacer("DELETE", "/v1/consorcios/$consorcio/bienes/${b.id}"); cargar() } catch (e: CancellationException) { throw e } catch (e: Exception) { estado.avisar(e) }
                            }
                        }) else emptyList()) {
                            Tocable({ editando = b }) {
                                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Texto(b.nombre + (b.cantidad?.let { if (it > 1) " × $it" else "" } ?: ""), Estilo.filaLista, C.texto)
                                        Texto(listOfNotNull(b.ubicacion, b.proximoService?.let { "service: $it" }).joinToString(" · "), Estilo.footnote, C.texto2)
                                    }
                                    val t: Tono = if (b.estado == "vencido") Tono.rojo else if (b.estado == "a_revisar") Tono.oro else if (b.estado == "baja") Tono.neutro else Tono.verde
                                    Box(Modifier.height(24.dp).clip(CircleShape).background(t.fondo).padding(horizontal = 9.dp), contentAlignment = Alignment.Center) {
                                        Texto(BienAPI.estados.firstOrNull { it.first == b.estado }?.second ?: "En orden", Estilo(12f, Peso.bold), t.tinta)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    Hoja(editando != null, { editando = null }) {
        editando?.let { b -> key(b.id, b.nombre) { FormBien(b) { x -> alcance.launch { guardar(x) } } } }
    }
}

@Composable
fun FormBien(b0: BienAPI, guardar: (BienAPI) -> Unit) {
    val cerrar = LocalCerrarHoja.current
    var b by remember { mutableStateOf(b0) }
    val zona = Formato.zona
    MarcoHoja(
        if (b.id.isEmpty()) "Bien nuevo" else b.nombre, confirmar = "Guardar", confirmarHabilitado = b.nombre.isNotEmpty(),
        alConfirmar = { guardar(b); cerrar() },
    ) {
        SeccionConsorcio {
            CampoTextoConsorcio(b.nombre, { b = b.copy(nombre = it) }, "Nombre")
            RenglonSelectorConsorcio("Categoría", BienAPI.categorias, b.categoria ?: "otro") { b = b.copy(categoria = it) }
            CampoTextoConsorcio(b.ubicacion ?: "", { b = b.copy(ubicacion = it) }, "Dónde está")
            Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
                Texto("Cantidad: ${b.cantidad ?: 1}", Estilo.body, C.texto, Modifier.weight(1f))
                Pasador(b.cantidad ?: 1, { b = b.copy(cantidad = it) }, 1..500)
            }
            RenglonSelectorConsorcio("Estado", BienAPI.estados, b.estado ?: "ok") { b = b.copy(estado = it) }
            RenglonFechaConsorcio("Próximo service", b.proximoService?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() } ?: LocalDate.now(zona), { b = b.copy(proximoService = it.toString()) })
            CampoTextoConsorcio(b.responsable ?: "", { b = b.copy(responsable = it) }, "Responsable")
            CampoTextoConsorcio(b.notas ?: "", { b = b.copy(notas = it) }, "Notas", lineas = 1, lineasMaximas = 6)
        }
    }
}
