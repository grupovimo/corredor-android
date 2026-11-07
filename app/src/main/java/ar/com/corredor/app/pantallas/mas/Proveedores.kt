package ar.com.corredor.app.pantallas.mas

import ar.com.corredor.app.app.PantallaApilada

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ar.com.corredor.app.app.LocalEstado
import ar.com.corredor.app.nucleo.*
import ar.com.corredor.app.tema.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.util.UUID

// Clon de Pantallas/Mas/Proveedores.swift.

/** Lo estático de Proveedores (el modelo, los rubros y el detalle de cada uno). */
object Proveedores {
    /** `GET /v1/proveedores` (consorcios.Proveedor). */
    @Serializable
    data class Prov(
        val id: String, val nombre: String, val cuit: String? = null, val rubro: String? = null, val telefono: String? = null, val email: String? = null,
        val notas: String? = null, val trabajos: Int? = null, val edificios: Int? = null,
    )

    /** Un rubro: clave, texto, ícono y color. */
    data class Rubro(val clave: String, val texto: String, val icono: String, val color: @Composable () -> Color)

    /** Rubro → texto, ícono y color (los del 44: plomería azul, electricidad oro, el resto gris). */
    val rubros: List<Rubro> = listOf(
        Rubro("plomeria", "Plomería", "drop") { C.azulTinta }, Rubro("electricidad", "Electricidad", "bolt") { C.oroTinta },
        Rubro("gas", "Gas", "flame") { C.errorTinta }, Rubro("herreria", "Herrería", "door.garage.closed") { C.texto2 },
        Rubro("pintura", "Pintura", "paintbrush") { C.violetaTinta }, Rubro("limpieza", "Limpieza", "sparkles") { C.okTinta },
        Rubro("ascensores", "Ascensores", "arrow.up.arrow.down.square") { C.texto2 }, Rubro("cerrajeria", "Cerrajería", "key") { C.texto2 },
        Rubro("albanileria", "Albañilería", "hammer") { C.texto2 }, Rubro("jardineria", "Jardinería", "leaf") { C.okTinta },
        Rubro("otro", "Otro", "wrench.and.screwdriver") { C.texto2 },
    )

    fun rubro(p: Prov): Rubro = rubros.firstOrNull { it.clave == (p.rubro ?: "").lowercase() } ?: rubros[rubros.size - 1]

    /** «falta CUIT» (en rojo), «sin trabajos todavía» o «8 trabajos · 2 edificios». */
    fun detalle(p: Prov): Pair<String, Boolean> {
        if (p.cuit.isNullOrBlank()) return "falta CUIT" to true
        val t = p.trabajos ?: 0
        if (t == 0) return "sin trabajos todavía" to false
        val e = p.edificios ?: 0
        return (Formato.cantidad(t, "trabajo", "trabajos") + (if (e > 1) " · $e edificios" else "")) to false
    }
}

/** 44 Proveedores (plomeros, electricistas…): los comparten todos los consorcios, cada uno con los trabajos que hizo.
 *  Cargan y corrigen el dueño y los agentes; borra el dueño; el lector sólo mira. */
@Composable
fun Proveedores() {
    val estado = LocalEstado.current
    val alcance = rememberCoroutineScope()
    var lista by remember { mutableStateOf<List<Proveedores.Prov>?>(null) }
    var error by remember { mutableStateOf<ErrorApi?>(null) }
    var editando by remember { mutableStateOf<Proveedores.Prov?>(null) }

    suspend fun cargar() {
        try {
            lista = estado.api.get<List<Proveedores.Prov>>("/v1/proveedores", listOf("limite" to "500"))
            error = null
        } catch (e: CancellationException) { throw e } catch (e: ErrorApi) { if (lista == null) error = e } catch (e: Exception) {}
    }

    @Composable
    fun vacio() {
        val accion: Pair<String, () -> Unit>? = if (estado.escribe) ("Cargar el primero" to { editando = Proveedores.Prov(id = "", nombre = "") }) else null
        Column(Modifier.fillMaxSize().padding(top = 30.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().alpha(0.5f).tarjeta()) {
                listOf("Plomería Rossi" to "8 trabajos · 2 edificios", "Electricidad Peralta" to "5 trabajos").forEach { (t, d) ->
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Texto(t, Estilo.filaLista, C.texto3, Modifier.weight(1f))
                        Texto(d, Estilo.footnote, C.texto3)
                    }
                }
            }
            EstadoVacio(
                icono = "wrench.and.screwdriver", titulo = "Todavía no cargaste proveedores",
                texto = "Plomeros, electricistas, cerrajeros: los cargás una vez y los usás en todos los consorcios.",
                accion = accion,
            )
        }
    }

    @Composable
    fun listado(l: List<Proveedores.Prov>) {
        Refrescable({ cargar() }) {
            ListaAgrupada {
                Texto("Los comparten todos tus consorcios. Cada uno, con los trabajos que hizo.", Estilo.footnote, C.texto2, Modifier.padding(horizontal = 16.dp))
                SeccionLista {
                    l.forEachIndexed { i, p ->
                        if (i > 0) Separador(54.dp)
                        val r = Proveedores.rubro(p)
                        val d = Proveedores.detalle(p)
                        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(
                                Modifier.weight(1f).clickable { editando = p }.semantics { contentDescription = "Ver ${p.nombre}" }.padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                IconoFila(r.icono, r.color())
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                    Texto(p.nombre, Estilo.filaLista, C.texto)
                                    Texto(d.first, Estilo.footnote, if (d.second) C.errorTinta else C.texto2)
                                }
                            }
                            if (!p.telefono.isNullOrBlank()) {
                                Box(
                                    Modifier.size(44.dp).clickable { Afuera.whatsapp(p.telefono) }.semantics { contentDescription = "Escribirle a ${p.nombre} por WhatsApp" },
                                    contentAlignment = Alignment.Center,
                                ) { ImagenAsset("logo_whatsapp", Modifier.size(22.dp)) }
                            }
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(estado.version) { cargar() }

    PantallaApilada("Proveedores", acciones = if (estado.escribe) ({ BotonHojaIcono("plus", "Nuevo") { editando = Proveedores.Prov(id = "", nombre = "") } }) else null) {
        val l = lista
        val err = error
        if (l != null) {
            if (l.isEmpty()) vacio() else listado(l)
        } else if (err != null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { EstadoError("los proveedores", err) { alcance.launch { cargar() } } }
        } else {
            Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.TopCenter) { EsqueletoFilas(6) }
        }
    }

    val e = editando
    Hoja(e != null, { editando = null }) {
        if (e != null) androidx.compose.runtime.key(e.id) { FormProveedor(e, { cargar() }, cerrar = { editando = null }) }
    }
}

/** La hoja de un proveedor (nuevo o editar): nombre, rubro, CUIT, teléfono, mail y notas, con el error de cada campo.
 *  El lector la ve bloqueada; borrar es del dueño y se confirma. */
@Composable
fun FormProveedor(inicial: Proveedores.Prov, alGuardar: suspend () -> Unit, cerrar: () -> Unit = LocalCerrarHoja.current) {
    val estado = LocalEstado.current
    val alcance = rememberCoroutineScope()
    var p by remember { mutableStateOf(inicial) }
    var campos by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var guardando by remember { mutableStateOf(false) }
    var confirmar by remember { mutableStateOf<Confirmacion?>(null) }
    val nuevo = p.id.isEmpty()

    @Serializable data class Cuerpo(val nombre: String, val cuit: String?, val rubro: String?, val telefono: String?, val email: String?, val notas: String?)

    suspend fun guardar() {
        campos = emptyMap()
        val nombre = p.nombre.trim()
        if (nombre.isEmpty()) { campos = mapOf("nombre" to "Poné el nombre del proveedor."); return }
        guardando = true
        fun n(s: String?): String? = s?.trim()?.takeIf { it.isNotEmpty() }
        val c = Cuerpo(nombre, n(p.cuit), n(p.rubro), n(p.telefono), n(p.email), n(p.notas))
        try {
            if (nuevo) estado.api.pedirConCampos("POST", "/v1/proveedores", c, envio = UUID.randomUUID().toString())
            else estado.api.pedirConCampos("PUT", "/v1/proveedores/${p.id}", c)
            estado.avisar(if (nuevo) "Proveedor cargado" else "Proveedor guardado")
            alGuardar()
            cerrar()
        } catch (e: CancellationException) { throw e } catch (e: Exception) { campos = estado.camposDe(e) } finally { guardando = false }
    }

    suspend fun borrar() {
        try {
            estado.api.hacer("DELETE", "/v1/proveedores/${p.id}")
            estado.avisar("${p.nombre} ya no está en tus proveedores")
            alGuardar()
            cerrar()
        } catch (e: CancellationException) { throw e } catch (e: Exception) { estado.avisar(e) }
    }

    @Composable
    fun campo(contenido: @Composable () -> Unit) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 11.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { contenido() }
    }

    Column(Modifier.fillMaxWidth()) {
        BarraHoja(
            if (nuevo) "Proveedor nuevo" else p.nombre,
            izquierda = { BotonHoja("Cancelar") { cerrar() } },
            derecha = if (estado.escribe) ({
                if (guardando) Box(Modifier.padding(horizontal = 12.dp)) { Rueda() } else BotonHoja("Guardar", fuerte = true, habilitado = !guardando) { alcance.launch { guardar() } }
            }) else null,
        )
        ListaAgrupada {
            if (!nuevo) {
                val d = Proveedores.detalle(p)
                SeccionLista { Texto(d.first, Estilo.footnote, if (d.second) C.errorTinta else C.texto2, Modifier.padding(horizontal = 16.dp, vertical = 11.dp)) }
            }
            SeccionLista(modifier = Modifier.deshabilitado(!estado.escribe)) {
                campo {
                    Entrada(p.nombre, { v -> p = p.copy(nombre = v.take(160)) }, "Nombre")
                    ErrorDeCampo(campos["nombre"])
                }
                Separador()
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Texto("Rubro", Estilo.body, C.texto, Modifier.weight(1f))
                    SelectorMenu(listOf("" to "Sin rubro") + Proveedores.rubros.map { it.clave to it.texto }, (p.rubro ?: "").lowercase(), { p = p.copy(rubro = it) }, descripcion = "Rubro")
                }
                Separador()
                campo {
                    Entrada(p.cuit ?: "", { p = p.copy(cuit = it) }, "20-12345678-9", teclado = KeyboardType.Ascii)
                    Texto("Para los comprobantes de las expensas.", Estilo.footnote, C.texto3)
                    ErrorDeCampo(campos["cuit"])
                }
                Separador()
                campo {
                    Entrada(p.telefono ?: "", { p = p.copy(telefono = it) }, "Teléfono o WhatsApp", teclado = KeyboardType.Phone)
                    ErrorDeCampo(campos["telefono"])
                }
                Separador()
                campo {
                    Entrada(p.email ?: "", { p = p.copy(email = it) }, "Mail", teclado = KeyboardType.Email, sinCorrector = true, mayusculas = KeyboardCapitalization.None)
                    ErrorDeCampo(campos["email"])
                }
                Separador()
                campo { Entrada(p.notas ?: "", { p = p.copy(notas = it) }, "Notas", lineas = 3, lineasMaximas = 6) }
            }
            if (!nuevo && estado.esDuenio) {
                SeccionLista {
                    FilaLista({
                        confirmar = Confirmacion("¿Borrás a ${p.nombre}?", "Los trabajos y gastos que ya tiene quedan, pero sin proveedor. No vuelve a la lista.",
                            "Borrar", hacer = { alcance.launch { borrar() } })
                    }) { Texto("Borrar", Estilo.body, C.error) }
                }
            }
        }
    }
    VistaConfirmacion(confirmar) { confirmar = null }
}
