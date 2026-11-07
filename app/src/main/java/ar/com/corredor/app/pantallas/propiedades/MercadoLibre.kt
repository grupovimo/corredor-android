package ar.com.corredor.app.pantallas.propiedades

import ar.com.corredor.app.app.PantallaApilada

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import ar.com.corredor.app.app.LocalEstado
import ar.com.corredor.app.nucleo.*
import ar.com.corredor.app.pantallas.inicio.Cifra
import ar.com.corredor.app.tema.*
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import java.text.Normalizer
import java.time.Instant
import kotlin.math.abs
import kotlin.math.roundToInt

// Clon de Pantallas/Propiedades/MercadoLibre.swift.

/** `GET /v1/mercadolibre`: la cuenta conectada. */
@Serializable
data class ConexionML(
    val disponible: Boolean? = null,
    val conectada: Boolean = false,
    val apodo: String? = null,
    val reautorizar: Boolean? = null,
    val preguntasSinResponder: Int? = null,
)

/** Una pregunta de Mercado Libre para la lista (GET /v1/mercadolibre/preguntas). */
@Serializable
data class PreguntaListaML(
    val id: String,
    val propiedadId: String? = null,
    val propiedad: String? = null,
    val texto: String = "",
    val estado: String? = null,
    val creadaEn: FechaApi? = null,
    val compradorApodo: String? = null,
    val visitasSemana: Int? = null,
)

/** Cómo se arregla un error de Mercado Libre (web: lib/mercadolibre/modelo.ts `arregloDe`): un número de la ficha acá
 *  mismo, o ir a la ficha a cargar lo que falta. */
data class ArregloML(
    val falta: String,
    val porque: String,
    val campo: String? = null,
    val etiqueta: String? = null,
    val enFicha: String? = null,
) {
    companion object {
        fun de(error: String?): ArregloML {
            val e = Normalizer.normalize(error ?: "", Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").lowercase(Formato.argentina)
            fun hay(p: String): Boolean = Regex(p).containsMatchIn(e)
            if (hay("superficie|total_area|covered_area|m2|metros")) {
                return ArregloML(falta = "la superficie total", porque = "Es obligatoria para publicar en Mercado Libre.", campo = "superficie", etiqueta = "m² totales")
            }
            if (hay("ambientes|rooms")) return ArregloML(falta = "los ambientes", porque = "Mercado Libre los pide para esta categoría.", campo = "ambientes", etiqueta = "Ambientes")
            if (hay("dormitorio|bedrooms")) return ArregloML(falta = "los dormitorios", porque = "Mercado Libre los pide para esta categoría.", campo = "dormitorios", etiqueta = "Dormitorios")
            if (hay("bano|full_bathrooms|bathrooms")) return ArregloML(falta = "los baños", porque = "Mercado Libre los pide para esta categoría.", campo = "banos", etiqueta = "Baños")
            if (hay("precio|price")) return ArregloML(falta = "el precio", porque = "Mercado Libre no publica sin precio.", campo = "precio", etiqueta = "Precio")
            if (hay("foto|picture")) return ArregloML(falta = "una foto", porque = "Mercado Libre no publica avisos sin fotos.", enFicha = "Cargar fotos")
            if (hay("ubicacion|direccion|location|address|lat")) {
                return ArregloML(falta = "la ubicación", porque = "Mercado Libre necesita la dirección y el barrio.", enFicha = "Completar la dirección")
            }
            if (hay("titulo|title")) return ArregloML(falta = "un título más claro", porque = "Mercado Libre rechazó el título.", enFicha = "Cambiar el título")
            if (hay("bloque|moderad|infraccion|forbidden|under_review")) {
                return ArregloML(falta = "una revisión de Mercado Libre", porque = "El aviso quedó en revisión; se resuelve en Mercado Libre.")
            }
            return ArregloML(falta = "un dato del aviso", porque = "Revisá la ficha y volvé a publicar.", enFicha = "Revisar la ficha")
        }

        /** «+12 %», «−8 %» contra la semana anterior. */
        fun variacion(semana: Int?, anterior: Int?): Pair<String, Boolean>? {
            if (semana == null || anterior == null || anterior <= 0) return null
            val p = ((semana - anterior).toDouble() / anterior * 100).let { if (it >= 0) Math.floor(it + 0.5) else Math.ceil(it - 0.5) }.toInt()
            return "${if (p > 0) "+" else if (p < 0) "−" else ""}${abs(p)} %" to (p >= 0)
        }

        val estados: Map<String, Pair<String, Tono>> = mapOf(
            "publicada" to ("Activa" to Tono.verde), "pausada" to ("Pausada" to Tono.oro), "finalizada" to ("Finalizada" to Tono.neutro),
            "error" to ("Con error" to Tono.rojo), "pendiente" to ("Publicando" to Tono.azul),
        )
    }
}

/** 32E Mercado Libre: «¿quién está esperando y qué publicación tiene un problema?». Las preguntas sin responder (cada
 *  una abre la respuesta armada), los errores explicados con el dato que falta cargado ahí mismo («Guardar y publicar»),
 *  y las publicaciones ordenadas por las visitas de la semana. */
@Composable
fun PantallaMercadoLibre() {
    val estado = LocalEstado.current
    val ctx = LocalContext.current
    var conexion by remember { mutableStateOf<ConexionML?>(null) }
    var preguntas by remember { mutableStateOf<List<PreguntaListaML>>(emptyList()) }
    var pubs by remember { mutableStateOf<List<PublicacionLista>>(emptyList()) }
    var resumen by remember { mutableStateOf<ResumenPublicaciones?>(null) }
    var fichas by remember { mutableStateOf<Map<String, Propiedad>>(emptyMap()) }
    var equipo by remember { mutableStateOf<List<Miembro>>(emptyList()) }
    var error by remember { mutableStateOf<ErrorApi?>(null) }
    var valores by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var arreglando by remember { mutableStateOf<Set<String>>(emptySet()) }
    var erroresCampo by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    val errores = pubs.filter { it.estado == "error" }
    val publicaciones = pubs.filter { it.estado != "error" }.sortedWith(
        compareByDescending<PublicacionLista> { it.visitasSemana ?: it.visitas ?: 0 }.thenByDescending { it.visitas ?: 0 },
    )

    suspend fun cargar() {
        try {
            val c: ConexionML = estado.api.get("/v1/mercadolibre")
            conexion = c
            error = null
            if (!c.conectada) return
            preguntas = (estado.api.opcional<List<PreguntaListaML>>("/v1/mercadolibre/preguntas", listOf("estado" to "sin_responder", "limite" to "100")) ?: emptyList())
                .sortedBy { it.creadaEn ?: Instant.MIN }
            pubs = estado.api.opcional<List<PublicacionLista>>("/v1/publicaciones", listOf("portal" to "mercadolibre", "limite" to "500")) ?: emptyList()
            resumen = estado.api.opcional<ResumenPublicaciones>("/v1/publicaciones/resumen")
            if (equipo.isEmpty()) equipo = estado.api.opcional<List<Miembro>>("/v1/equipo") ?: emptyList()
            for (id in preguntas.mapNotNull { it.propiedadId }.toSet()) {
                if (fichas[id] != null) continue
                estado.api.opcional<Propiedad>("/v1/propiedades/$id")?.let { fichas = fichas + (id to it) }
            }
        } catch (e: CancellationException) { throw e } catch (e: ErrorApi) { error = e } catch (e: Exception) {}
    }

    /** Guarda el número en la ficha (PUT con la propiedad entera) y vuelve a publicar. */
    suspend fun arreglar(x: PublicacionLista, campo: String) {
        val v = (valores[x.propiedadId] ?: "").replace(".", "").replace(",", ".").toDoubleOrNull() ?: 0.0
        if (v <= 0) { erroresCampo = erroresCampo + (x.propiedadId to "Poné un número."); return }
        arreglando = arreglando + x.propiedadId
        erroresCampo = erroresCampo - x.propiedadId
        @Serializable data class R(val estado: String? = null)
        try {
            var p: Propiedad = estado.api.get("/v1/propiedades/${x.propiedadId}")
            p = when (campo) {
                "superficie" -> p.copy(superficie = v)
                "cubierta" -> p.copy(cubierta = v)
                "ambientes" -> p.copy(ambientes = v.toInt())
                "dormitorios" -> p.copy(dormitorios = v.toInt())
                "banos" -> p.copy(banos = v.toInt())
                else -> p.copy(precio = v)
            }
            estado.api.hacer("PUT", "/v1/propiedades/${p.id}", p)
            val raw = estado.api.pedir("POST", "/v1/propiedades/${p.id}/publicaciones/mercadolibre")
            val r = runCatching { Api.json.decodeFromString<R>(String(raw)) }.getOrNull()
            if (r?.estado == "error") estado.avisar("Se guardó, pero Mercado Libre todavía no lo acepta", ok = false)
            else estado.avisar("${p.direccion ?: "La propiedad"} se publicó en Mercado Libre")
            estado.version += 1
        } catch (e: CancellationException) {
            throw e
        } catch (e: ErrorApi) {
            erroresCampo = erroresCampo + (x.propiedadId to (if (e.detalle.isEmpty()) "No se publicó. Probá de nuevo." else e.detalle))
        } catch (e: Exception) {
            erroresCampo = erroresCampo + (x.propiedadId to "No se publicó. Probá de nuevo.")
        } finally {
            arreglando = arreglando - x.propiedadId
        }
    }

    LaunchedEffect(estado.version) { cargar() }

    @Composable
    fun desconectada(c: ConexionML) {
        Box(Modifier.padding(top = 30.dp)) {
            EstadoVacio(
                icono = "storefront",
                titulo = if (c.reautorizar == true) "Hay que volver a conectar Mercado Libre" else "Las preguntas de Mercado Libre van a entrar acá",
                texto = "Las preguntas llegan con la respuesta armada.",
                accion = if (estado.esDuenio && c.disponible != false) ("Conectar Mercado Libre" to { estado.abrir(ASeccion(clave = "integraciones")) })
                else ("Ver integraciones" to { estado.abrir(ASeccion(clave = "integraciones")) }),
            )
        }
    }

    @Composable
    fun cifras(activas: Int, pausadas: Int) {
        val ml = resumen?.portales?.firstOrNull { it.portal == "mercadolibre" }
        val vieja = preguntas.mapNotNull { it.creadaEn }.minOrNull()
        val variacion = ArregloML.variacion(ml?.visitasSemana, ml?.visitasSemanaAnterior)
        val total = pubs.sumOf { it.visitas ?: 0 }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) { Cifra(etiqueta = "Sin responder", valor = "${preguntas.size}") }
                Box(Modifier.weight(1f)) { Cifra(etiqueta = "Con error", valor = "${errores.size}") }
                Box(Modifier.weight(1f)) { Cifra(etiqueta = "Activas", valor = "$activas") }
                Box(Modifier.weight(1f)) { Cifra(etiqueta = "Pausadas", valor = "$pausadas") }
            }
            if (vieja != null) Texto("La más vieja: ${Formato.hace(vieja)}", Estilo.footnote, C.avisoTinta)
            val s = ml?.visitasSemana
            if (s != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Texto("${Formato.numero(s)} visitas a publicaciones esta semana", Estilo.footnoteFuerte, C.texto)
                    if (variacion != null) Texto(variacion.first, Estilo.footnoteFuerte, if (variacion.second) C.okTinta else C.errorTinta)
                }
            } else if (total > 0) {
                Texto("${Formato.numero(total)} visitas a publicaciones en total", Estilo.footnoteFuerte, C.texto)
            }
        }
    }

    fun lineaPregunta(q: PreguntaListaML): String {
        val f = q.propiedadId?.let { fichas[it] }
        val agente = f?.agenteId?.let { a -> equipo.firstOrNull { it.usuarioId == a }?.completo?.split(" ")?.firstOrNull() }
        val visitas = q.visitasSemana ?: pubs.firstOrNull { it.propiedadId == q.propiedadId }?.visitasSemana
        val total = pubs.firstOrNull { it.propiedadId == q.propiedadId }?.visitas
        return listOfNotNull(
            f?.ambientes?.let { "$it amb" }, f?.superficie?.let { "${Formato.numero(it.toInt())} m²" }, agente,
            visitas?.let { "${Formato.numero(it)} visitas esta semana" } ?: total?.let { "${Formato.numero(it)} visitas" },
        ).joinToString(" · ")
    }

    @Composable
    fun filaPregunta(q: PreguntaListaML, primera: Boolean) {
        val f = q.propiedadId?.let { fichas[it] }
        Column {
            if (!primera) Box(Modifier.fillMaxWidth().height(0.5.dp).background(C.linea))
            Tocable({ estado.abrir(APreguntaML(id = q.id)) }) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                    Foto(f?.miniatura, Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Texto(
                            listOfNotNull(f?.titulo ?: q.propiedad ?: "Publicación", f?.let { ff -> ff.precio?.let { p -> if (ff.precioOculto == true) null else Formato.plata(p, ff.moneda ?: "USD") } })
                                .joinToString(" · "),
                            Estilo.filaLista, C.texto, lineas = 1,
                        )
                        val l = lineaPregunta(q)
                        if (l.isNotEmpty()) Texto(l, Estilo.footnote, C.texto2, lineas = 1)
                        Texto("«${q.texto}»${q.compradorApodo?.let { " · usuario $it" } ?: ""}", Estilo.subhead, C.texto, lineas = 3)
                    }
                    Column(Modifier.padding(start = 4.dp), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        q.creadaEn?.let { Texto(Formato.hace(it), Estilo(12f, Peso.regular), C.texto3) }
                        Texto("Responder", Estilo(13f, Peso.bold), C.accion)
                    }
                }
            }
        }
    }

    @Composable
    fun tarjetaError(x: PublicacionLista) {
        val a = ArregloML.de(x.error)
        val corto = (x.propiedad ?: "La propiedad").split(",").firstOrNull() ?: ""
        val partes = a.falta.split(" ", limit = 2)
        Column(Modifier.fillMaxWidth().tarjeta().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                IconoTono(icono = "exclamationmark.circle", tono = Tono.rojo, tam = 36.dp)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Texto("$corto no se publicó", Estilo.filaLista, C.texto)
                    Texto("Mercado Libre dice: ${x.error ?: ""}", Estilo(12f, Peso.regular), C.texto2, lineas = 3, monoespaciado = true)
                }
            }
            TextoRico(
                buildAnnotatedString {
                    append("Falta ${partes.firstOrNull() ?: ""} ")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(if (partes.size > 1) partes[1] else "") }
                    append(". ${a.porque}")
                },
                Estilo.subhead, C.texto,
            )
            val campo = a.campo
            val enFicha = a.enFicha
            if (campo != null && estado.escribe) {
                val malo = erroresCampo[x.propiedadId] != null
                val forma = RoundedCornerShape(12.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.weight(1f).height(44.dp).clip(forma).background(C.superficie).border(1.dp, if (malo) C.error else C.linea2, forma).padding(horizontal = 12.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Entrada(valores[x.propiedadId] ?: "", { valores = valores + (x.propiedadId to it) }, a.etiqueta ?: "", estilo = Estilo.body16, teclado = KeyboardType.Decimal)
                    }
                    BotonTonal(
                        if (x.propiedadId in arreglando) "Publicando…" else "Guardar y publicar", { estado.tarea { arreglar(x, campo) } }, alto = 44.dp,
                        habilitado = !(valores[x.propiedadId] ?: "").isEmpty() && x.propiedadId !in arreglando,
                    )
                }
                erroresCampo[x.propiedadId]?.let { MensajeError(it) }
            } else if (enFicha != null && estado.escribe) {
                BotonTonal(enFicha, {
                    estado.abrir(if (enFicha == "Cargar fotos") AEditarEnPaso(id = x.propiedadId, paso = "fotos") else AAltaPropiedad(editar = x.propiedadId))
                })
            }
        }
    }

    @Composable
    fun filaPublicacion(x: PublicacionLista, primera: Boolean) {
        val (t, tono) = ArregloML.estados[x.estado] ?: (x.estado.capitalizado() to Tono.neutro)
        Column {
            if (!primera) Box(Modifier.fillMaxWidth().height(0.5.dp).background(C.linea))
            Row(
                Modifier.fillMaxWidth().heightIn(min = 54.dp).clickable { estado.abrir(AFichaPropiedad(id = x.propiedadId)) }.padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Texto(x.propiedad ?: "Publicación", Estilo.filaLista, C.texto, lineas = 1)
                    Texto(x.visitasSemana?.let { "${Formato.numero(it)} visitas esta semana" } ?: "${Formato.numero(x.visitas ?: 0)} visitas", Estilo.footnote, C.texto2)
                }
                Box(Modifier.height(24.dp).clip(Capsula).background(tono.fondo).padding(horizontal = 9.dp), contentAlignment = Alignment.Center) {
                    Texto(t, Estilo(12f, Peso.bold), tono.tinta)
                }
                val u = x.url
                if (u != null) {
                    Tocable({ abrirUrl(ctx, u) }, descripcion = "Ver en Mercado Libre") { Icono("arrow.up.right.square", 17f, C.accion) }
                }
            }
        }
    }

    @Composable
    fun conectada(c: ConexionML) {
        val activas = pubs.count { it.estado == "publicada" }
        val pausadas = pubs.count { it.estado == "pausada" }
        Texto("Cuenta ${c.apodo ?: ""} · ${Formato.cantidad(activas, "publicación activa", "publicaciones activas")}", Estilo.subhead, C.texto2)
        cifras(activas, pausadas)
        // Preguntas
        TituloBloque(texto = "Preguntas sin responder", cuenta = if (preguntas.isEmpty()) null else "${preguntas.size}")
        if (preguntas.isEmpty()) {
            Row(Modifier.fillMaxWidth().tarjeta().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                IconoTono(icono = "checkmark.bubble", tono = Tono.verde, tam = 36.dp)
                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Texto("No hay preguntas esperando", Estilo.filaLista, C.texto)
                    Texto("Las preguntas entran acá con la respuesta armada.", Estilo.footnote, C.texto2)
                }
            }
        } else {
            Column(Modifier.fillMaxWidth().tarjeta()) { preguntas.forEachIndexed { i, q -> filaPregunta(q, primera = i == 0) } }
        }
        // Errores
        if (errores.isNotEmpty()) {
            TituloBloque(texto = "Con error", cuenta = "${errores.size}")
            errores.forEach { x -> tarjetaError(x) }
        }
        // Publicaciones
        TituloBloque(texto = "Publicaciones", cuenta = "${publicaciones.size}")
        if (publicaciones.isEmpty()) {
            Texto("Cada propiedad se publica desde su ficha.", Estilo.footnote, C.texto2)
        } else {
            Column(Modifier.fillMaxWidth().tarjeta()) { publicaciones.forEachIndexed { i, x -> filaPublicacion(x, primera = i == 0) } }
        }
    }

    PantallaApilada("Mercado Libre") {
        Refrescable({ cargar() }, Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                val c = conexion
                val e = error
                if (c != null) {
                    if (!c.conectada) desconectada(c) else conectada(c)
                } else if (e != null) {
                    Box(Modifier.padding(top = 40.dp)) { EstadoError("Mercado Libre", e) { estado.tarea { cargar() } } }
                } else EsqueletoFilas(filas = 5)
            }
        }
    }
}
