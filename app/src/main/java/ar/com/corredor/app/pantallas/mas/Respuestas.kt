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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ar.com.corredor.app.app.LocalEstado
import ar.com.corredor.app.nucleo.*
import ar.com.corredor.app.tema.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.util.UUID

// Clon de Pantallas/Mas/Respuestas.swift.

/** Lo estático de RespuestasGuardadas. */
object RespuestasGuardadas {
    /** `GET /v1/respuestas-guardadas`, con los usos. */
    @Serializable
    data class Respuesta(val id: String, val titulo: String, val texto: String, val usos: Int? = null)

    fun usos(r: Respuesta): String = if ((r.usos ?: 0) > 0) Formato.cantidad(r.usos ?: 0, "uso", "usos") else "sin usar"
}

/** Respuestas guardadas: los textos que se mandan con un toque desde un chat de WhatsApp. Se usan en Mensajes; acá se
 *  cargan, se corrigen y se borran (con cuántas veces se usó cada una). */
@Composable
fun RespuestasGuardadas() {
    val estado = LocalEstado.current
    val alcance = rememberCoroutineScope()
    var lista by remember { mutableStateOf<List<RespuestasGuardadas.Respuesta>?>(null) }
    var error by remember { mutableStateOf<ErrorApi?>(null) }
    var editando by remember { mutableStateOf<RespuestasGuardadas.Respuesta?>(null) }

    suspend fun cargar() {
        try {
            lista = estado.api.get<List<RespuestasGuardadas.Respuesta>>("/v1/respuestas-guardadas")
            error = null
        } catch (e: CancellationException) { throw e } catch (e: ErrorApi) { if (lista == null) error = e } catch (e: Exception) {}
    }

    @Composable
    fun vacio() {
        val accion: Pair<String, () -> Unit>? = if (estado.escribe) ("Guardar la primera" to { editando = RespuestasGuardadas.Respuesta(id = "", titulo = "", texto = "") }) else null
        Column(Modifier.fillMaxSize().padding(top = 30.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().alpha(0.5f).tarjeta()) {
                listOf("Requisitos para alquilar" to "12 usos", "Horario de visitas" to "7 usos").forEach { (t, d) ->
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Texto(t, Estilo.filaLista, C.texto3, Modifier.weight(1f))
                        Texto(d, Estilo.footnote, C.texto3)
                    }
                }
            }
            EstadoVacio(icono = "text.bubble", titulo = "Todavía no guardaste respuestas", texto = "Lo que escribís siempre: requisitos, horarios, cómo llegar.", accion = accion)
        }
    }

    @Composable
    fun listado(l: List<RespuestasGuardadas.Respuesta>) {
        Refrescable({ cargar() }) {
            ListaAgrupada {
                Texto("Las mandás con un toque desde cualquier chat de WhatsApp.", Estilo.footnote, C.texto2, Modifier.padding(horizontal = 16.dp))
                SeccionLista {
                    l.forEachIndexed { i, r ->
                        if (i > 0) Separador(54.dp)
                        key(r.id) {
                            Row(
                                Modifier.fillMaxWidth().clickable { editando = r }.semantics { contentDescription = "Ver «${r.titulo}»" }
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top,
                            ) {
                                IconoFila("text.bubble", C.okTinta)
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Texto(r.titulo, Estilo.filaLista, C.texto)
                                    Texto(r.texto, Estilo.footnote, C.texto2, lineas = 2)
                                }
                                Texto(RespuestasGuardadas.usos(r), Estilo.footnote, C.texto3, Modifier.padding(start = 4.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(estado.version) { cargar() }

    PantallaApilada("Respuestas guardadas", acciones = if (estado.escribe) ({ BotonHojaIcono("plus", "Nueva") { editando = RespuestasGuardadas.Respuesta(id = "", titulo = "", texto = "") } }) else null) {
        val l = lista
        val err = error
        if (l != null) {
            if (l.isEmpty()) vacio() else listado(l)
        } else if (err != null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { EstadoError("las respuestas guardadas", err) { alcance.launch { cargar() } } }
        } else {
            Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.TopCenter) { EsqueletoFilas(5) }
        }
    }

    val e = editando
    Hoja(e != null, { editando = null }) {
        if (e != null) key(e.id) { FormRespuesta(e, { cargar() }, cerrar = { editando = null }) }
    }
}

/** La hoja de una respuesta: nombre (60) y texto (1000), con el error de cada campo; cierra recién cuando se guardó.
 *  El lector la ve bloqueada; borrar se confirma. */
@Composable
fun FormRespuesta(inicial: RespuestasGuardadas.Respuesta, alGuardar: suspend () -> Unit, cerrar: () -> Unit = LocalCerrarHoja.current) {
    val estado = LocalEstado.current
    val alcance = rememberCoroutineScope()
    var r by remember { mutableStateOf(inicial) }
    var campos by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var guardando by remember { mutableStateOf(false) }
    var confirmar by remember { mutableStateOf<Confirmacion?>(null) }
    val nueva = r.id.isEmpty()

    @Serializable data class Cuerpo(val titulo: String, val texto: String)

    suspend fun guardar() {
        campos = emptyMap()
        guardando = true
        val c = Cuerpo(r.titulo.trim(), r.texto.trim())
        try {
            if (nueva) estado.api.pedirConCampos("POST", "/v1/respuestas-guardadas", c, envio = UUID.randomUUID().toString())
            else estado.api.pedirConCampos("PUT", "/v1/respuestas-guardadas/${r.id}", c)
            estado.avisar(if (nueva) "Respuesta lista para usar" else "Respuesta guardada")
            alGuardar()
            cerrar()
        } catch (e: CancellationException) { throw e } catch (e: Exception) { campos = estado.camposDe(e) } finally { guardando = false }
    }

    suspend fun borrar() {
        try {
            estado.api.hacer("DELETE", "/v1/respuestas-guardadas/${r.id}")
            estado.avisar("Borraste «${r.titulo}»")
            alGuardar()
            cerrar()
        } catch (e: CancellationException) { throw e } catch (e: Exception) { estado.avisar(e) }
    }

    Column(Modifier.fillMaxWidth()) {
        BarraHoja(
            if (nueva) "Respuesta nueva" else r.titulo,
            izquierda = { BotonHoja("Cancelar") { cerrar() } },
            derecha = if (estado.escribe) ({
                if (guardando) Box(Modifier.padding(horizontal = 12.dp)) { Rueda() }
                else BotonHoja("Guardar", fuerte = true, habilitado = !guardando && r.titulo.isNotBlank()) { alcance.launch { guardar() } }
            }) else null,
        )
        ListaAgrupada {
            if (!nueva) SeccionLista { Texto(RespuestasGuardadas.usos(r), Estilo.footnote, C.texto2, Modifier.padding(horizontal = 16.dp, vertical = 11.dp)) }
            SeccionLista(modifier = Modifier.deshabilitado(!estado.escribe)) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 11.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Entrada(r.titulo, { v -> r = r.copy(titulo = v.take(60)) }, "Requisitos para alquilar")
                    ErrorDeCampo(campos["titulo"])
                }
                Separador()
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 11.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Entrada(r.texto, { v -> r = r.copy(texto = v.take(1000)) }, "Texto", lineas = 6, lineasMaximas = 14)
                    Texto("${r.texto.length}/1000", Estilo.footnote, C.texto3, Modifier.fillMaxWidth(), alinear = TextAlign.End)
                    ErrorDeCampo(campos["texto"])
                }
            }
            if (!nueva && estado.escribe) {
                SeccionLista {
                    FilaLista({
                        confirmar = Confirmacion("¿Borrás «${r.titulo}»?", "Deja de aparecer en los chats. Lo que ya mandaste queda.", "Borrar",
                            hacer = { alcance.launch { borrar() } })
                    }) { Texto("Borrar", Estilo.body, C.error) }
                }
            }
        }
    }
    VistaConfirmacion(confirmar) { confirmar = null }
}
