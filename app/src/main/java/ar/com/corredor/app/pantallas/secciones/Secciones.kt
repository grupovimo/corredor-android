package ar.com.corredor.app.pantallas.secciones

import ar.com.corredor.app.app.PantallaApilada

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
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.unit.dp
import ar.com.corredor.app.app.LocalEstado
import ar.com.corredor.app.nucleo.*
import ar.com.corredor.app.pantallas.cobranzas.FichasMandadas
import ar.com.corredor.app.pantallas.cobranzas.Grabaciones
import ar.com.corredor.app.pantallas.cobranzas.Inventario
import ar.com.corredor.app.pantallas.cobranzas.ListaActas
import ar.com.corredor.app.pantallas.cobranzas.RendicionesListas
import ar.com.corredor.app.pantallas.consorcios.BienesConsorcio
import ar.com.corredor.app.pantallas.consorcios.Consorcios
import ar.com.corredor.app.pantallas.consorcios.FichaConsorcio
import ar.com.corredor.app.pantallas.consorcios.ReglamentoConsorcio
import ar.com.corredor.app.pantallas.documentos.DocumentosLista
import ar.com.corredor.app.pantallas.emprendimientos.Emprendimientos
import ar.com.corredor.app.pantallas.emprendimientos.FichaEmprendimiento
import ar.com.corredor.app.pantallas.entrada.CrearOtraInmobiliaria
import ar.com.corredor.app.pantallas.entrada.PegarInvitacion
import ar.com.corredor.app.pantallas.entrada.VerInvitacion
import ar.com.corredor.app.pantallas.informes.Indices
import ar.com.corredor.app.pantallas.informes.Informe
import ar.com.corredor.app.pantallas.informes.Objetivos
import ar.com.corredor.app.pantallas.mas.*
import ar.com.corredor.app.pantallas.mira.RevisionIA
import ar.com.corredor.app.pantallas.propiedades.Importar
import ar.com.corredor.app.pantallas.propiedades.PantallaMercadoLibre
import ar.com.corredor.app.pantallas.propiedades.Portales
import ar.com.corredor.app.pantallas.propiedades.Reservas
import ar.com.corredor.app.pantallas.propiedades.VistasPropiedad
import ar.com.corredor.app.pantallas.temporada.TarifasTemporada
import ar.com.corredor.app.pantallas.temporada.Temporada
import ar.com.corredor.app.pantallas.web.MiWeb
import ar.com.corredor.app.pantallas.web.get
import ar.com.corredor.app.pantallas.web.lista
import ar.com.corredor.app.pantallas.web.objeto
import ar.com.corredor.app.pantallas.web.texto
import ar.com.corredor.app.tema.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import java.time.Instant

// Clon de Pantallas/Secciones/Secciones.swift.
// `destinosSecciones()` de iOS (navigationDestination(for: ASeccion.self)) lo hace app/Registro.kt: cada
// `ASeccion(clave, id)` abre `Seccion(clave, id)`.

/** Elige la pantalla de cada sección. Lo no dibujado se arma con las piezas del handoff (tarjetas,
 *  filas, chips, hojas), nunca con diseño nuevo. */
@Composable
fun Seccion(clave: String, id: String? = null) {
    when (clave) {
        "mas" -> MasRaiz()
        "plan", "suscripcion" -> PantallaPlan()
        "configuracion", "inmobiliaria" -> ConfigInmobiliaria()
        "equipo" -> Equipo()
        "integraciones", "whatsapp" -> Integraciones()
        "mercadolibre" -> PantallaMercadoLibre()
        "respuestas" -> RespuestasGuardadas()
        "proveedores" -> Proveedores()
        "objetivos" -> Objetivos()
        "inventarios" -> ListaActas()
        "actas" -> if (id != null) Inventario(propiedad = id)
        "rendiciones" -> RendicionesListas()
        "papelera" -> Papelera()
        "actividad" -> Actividad(filtro = id)
        "revision" -> RevisionIA(ids = id)
        "informe" -> Informe()
        "indices" -> Indices()
        "vistas" -> if (id != null) VistasPropiedad(propiedad = id)
        "tarifas" -> if (id != null) TarifasTemporada(propiedad = id)
        "web", "plantillas", "editor" -> MiWeb()
        "difusion" -> Portales()
        "emprendimientos" -> Emprendimientos()
        "emprendimiento" -> if (id != null) FichaEmprendimiento(id = id)
        "temporada" -> Temporada()
        "reservas" -> Reservas()
        "consorcios" -> Consorcios()
        "consorcio" -> if (id != null) FichaConsorcio(id = id)
        "envios" -> if (id != null) FichasMandadas(propiedad = id)
        "grabaciones" -> if (id != null) Grabaciones(propiedad = id)
        "reservas-propiedad" -> if (id != null) Reservas(ruta = "/v1/propiedades/$id/reservas")
        "importar" -> Importar(retomar = id)
        "documentos-propiedad" -> if (id != null) DocumentosLista(de = "propiedad", id = id)
        "documentos-contrato" -> if (id != null) DocumentosLista(de = "contrato", id = id)
        "bienes" -> if (id != null) BienesConsorcio(consorcio = id)
        "reglamento" -> if (id != null) ReglamentoConsorcio(consorcio = id)
        "ia" -> CorredorEnTuIA()
        "firma" -> FirmaYSesiones()
        "tarjeta-agente" -> if (id != null) TarjetaAgente(usuario = id)
        "autorizar-ia" -> if (id != null) AutorizarIA(solicitud = id)
        "invitacion" -> if (id != null) VerInvitacion(token = id)
        "invitaciones" -> PegarInvitacion()
        "empezar" -> CrearOtraInmobiliaria()
        else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            FondoApp()
            EstadoVacio("questionmark.folder", "No encontramos esta sección")
        }
    }
}

// MARK: - Todo

// MARK: - Lista genérica

/** Una lista de lo que devuelva la API (cualquier forma): toma el título, el detalle y la fecha de los
 *  campos habituales. Para secciones de consulta (actividad, vistas, portales). */
@Composable
fun ListaGenerica(
    titulo: String,
    ruta: String,
    icono: String,
    vacio: String,
    acciones: ((JsonElement) -> List<Pair<String, () -> Unit>>)? = null,
) {
    val estado = LocalEstado.current
    val alcance = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<JsonElement>?>(null) }
    var error by remember { mutableStateOf<ErrorApi?>(null) }

    suspend fun cargar() {
        try {
            val d = estado.api.pedir("GET", ruta, query = listOf("limite" to "200"))
            val j = Api.json.parseToJsonElement(d)
            items = j.lista ?: j.objeto?.values?.firstOrNull { it.lista != null }?.lista ?: emptyList()
            error = null
        } catch (e: CancellationException) { throw e } catch (e: ErrorApi) { error = e } catch (e: Exception) { items = emptyList() }
    }

    fun abrir(x: JsonElement) {
        x["propiedadId"].texto?.let { return estado.abrir(AFichaPropiedad(id = it)) }
        x["contactoId"].texto?.let { return estado.abrir(AFichaContacto(id = it)) }
        x["contratoId"].texto?.let { return estado.abrir(AFichaContrato(id = it)) }
    }

    LaunchedEffect(estado.version) { cargar() }
    PantallaApilada(titulo) {
        Refrescable({ cargar() }) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // .navigationBarTitleDisplayMode(.large): el título grande arriba de la lista.
                Texto(titulo, Estilo.tituloGrande, C.texto)
                val l = items
                val e = error
                if (l != null) {
                    if (l.isEmpty()) Box(Modifier.padding(top = 50.dp)) { EstadoVacio(icono, vacio) }
                    Column(Modifier.fillMaxWidth().tarjeta()) {
                        l.forEachIndexed { i, x ->
                            if (i > 0) Box(Modifier.fillMaxWidth().height(0.5.dp).background(C.linea))
                            Row(
                                Modifier.fillMaxWidth().clickable { abrir(x) }.padding(horizontal = 14.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top,
                            ) {
                                IconoTono(icono, Tono.verde, 38.dp)
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Texto(ListaGenerica.titulo(x), Estilo.filaLista, C.texto)
                                    ListaGenerica.detalle(x)?.let { Texto(it, Estilo.footnote, C.texto2, lineas = 3) }
                                    ListaGenerica.fecha(x)?.let { Texto(Formato.cuando(it), Estilo(12f, Peso.regular), C.texto3) }
                                    val a = acciones?.invoke(x)
                                    if (!a.isNullOrEmpty()) {
                                        Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                            a.forEach { (t, h) -> Texto(t, Estilo.footnoteFuerte, C.accion, Modifier.clickable(onClick = h)) }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else if (e != null) {
                    EstadoError(titulo.lowercase(Formato.argentina), e) { alcance.launch { cargar() } }
                } else {
                    EsqueletoFilas(filas = 6)
                }
            }
        }
    }
}

object ListaGenerica {
    fun titulo(x: JsonElement): String {
        for (k in listOf("titulo", "nombre", "descripcion", "propiedadTitulo", "propiedad", "texto", "accion", "codigo", "portal")) {
            x[k].texto?.takeIf { it.trim().isNotEmpty() }?.let { return it }
        }
        return "—"
    }

    fun detalle(x: JsonElement): String? {
        val partes = listOf("detalle", "contactoNombre", "estado", "entidad", "autorNombre", "agenteNombre", "direccion", "barrio", "motivo")
            .mapNotNull { x[it].texto?.takeIf { t -> t.trim().isNotEmpty() } }
        return if (partes.isEmpty()) null else partes.take(3).joinToString(" · ")
    }

    fun fecha(x: JsonElement): Instant? {
        for (k in listOf("ocurrioEn", "creadoEn", "creadaEn", "borradoEn", "actualizadoEn", "en", "fecha")) {
            val t = x[k].texto ?: continue
            (Fecha.iso(t) ?: Fecha.dia(t))?.let { return it }
        }
        return null
    }
}

/** Un título y un texto (respuestas guardadas, notas). */
@Composable
fun EditorTexto(tituloInicial: String, textoInicial: String, guardar: (String, String) -> Unit, cerrar: () -> Unit) {
    var titulo by remember { mutableStateOf(tituloInicial) }
    var texto by remember { mutableStateOf(textoInicial) }
    Column(Modifier.fillMaxSize()) {
        BarraHoja("",
            izquierda = { BotonHoja("Cancelar") { cerrar() } },
            derecha = { BotonHoja("Guardar", fuerte = true, habilitado = titulo.isNotEmpty() && texto.isNotEmpty()) { guardar(titulo, texto); cerrar() } })
        ListaAgrupada {
            SeccionLista {
                FilaLista { Entrada(titulo, { titulo = it }, "Nombre", Modifier.weight(1f), estilo = Estilo.body16) }
                Separador()
                FilaLista { Entrada(texto, { texto = it }, "Texto", Modifier.weight(1f), estilo = Estilo.body16, lineas = 5, lineasMaximas = 14) }
            }
        }
    }
}

/** Una URL para abrir en una hoja (`.sheet(item:)`). */
data class URLHoja(val url: String) { val id: String get() = url }
