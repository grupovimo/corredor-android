package ar.com.corredor.app.pantallas.mas

import ar.com.corredor.app.app.PantallaApilada

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import ar.com.corredor.app.app.LocalEstado
import ar.com.corredor.app.nucleo.*
import ar.com.corredor.app.tema.*
import coil3.compose.AsyncImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// Clon de Pantallas/Mas/ConfigInmobiliaria.swift.

// MARK: - Configuración

/** Lo estático de ConfigInmobiliaria (la ficha de la API, los rubros, las zonas y los campos legales). */
object ConfigInmobiliaria {
    /** `GET /v1/inmobiliaria` (inmobiliarias.Inmobiliaria). */
    @Serializable
    data class Ficha(
        val id: String,
        val slug: String? = null,
        val nombre: String,
        val dominio: String? = null,
        val marca: JsonElement? = null, // JSONLibre de iOS
        val zonaHoraria: String? = null,
        val legales: Map<String, String?>? = null,
        val cbu: String? = null,
        val web: String? = null,
        val perfil: Perfil? = null,
    ) {
        @Serializable data class Perfil(val rubros: List<String>? = null, val zonas: List<String>? = null)
    }

    val rubros = mapOf("ventas" to "Ventas", "alquileres" to "Alquileres", "temporada" to "Temporada", "emprendimientos" to "Emprendimientos", "consorcios" to "Consorcios")

    /** Las zonas de la Argentina, como la web. Si guardaron otra, también aparece. */
    val zonas: List<Pair<String, String>> = listOf(
        "America/Argentina/Buenos_Aires" to "Buenos Aires (CABA y Provincia)",
        "America/Argentina/Cordoba" to "Córdoba, Santa Fe y el centro",
        "America/Argentina/Mendoza" to "Mendoza",
        "America/Argentina/Salta" to "Salta",
        "America/Argentina/Jujuy" to "Jujuy",
        "America/Argentina/Tucuman" to "Tucumán",
        "America/Argentina/Catamarca" to "Catamarca",
        "America/Argentina/La_Rioja" to "La Rioja",
        "America/Argentina/San_Juan" to "San Juan",
        "America/Argentina/San_Luis" to "San Luis",
        "America/Argentina/Rio_Gallegos" to "Santa Cruz",
        "America/Argentina/Ushuaia" to "Tierra del Fuego",
    )

    /** Un campo legal: clave de la API, etiqueta, largo máximo y ayuda. */
    data class CampoLegal(val clave: String, val nombre: String, val max: Int, val ayuda: String?)

    /** Los campos legales: clave de la API, etiqueta, largo máximo y ayuda. */
    val camposLegales: List<CampoLegal> = listOf(
        CampoLegal("razonSocial", "Razón social", 160, "Como figura en AFIP."), CampoLegal("cuit", "CUIT", 20, null),
        CampoLegal("corredorResponsable", "Corredor responsable", 120, null), CampoLegal("matricula", "Matrícula", 40, null),
        CampoLegal("colegio", "Colegio o jurisdicción", 160, null),
    )

    /** Un CBU o CVU de 22 números, o un alias de 6 a 20 letras, números, puntos o guiones (la regla de la API). */
    fun cbuValido(c: String): Boolean {
        if (Regex("^\\d{22}$").matches(c)) return true
        return Regex("^[A-Za-z0-9.-]{6,20}$").matches(c) && Regex("[A-Za-z]").containsMatchIn(c)
    }

    fun opcionesZona(f: Ficha): List<Pair<String, String>> {
        val z = f.zonaHoraria ?: ""
        if (z.isEmpty() || zonas.any { it.first == z }) return zonas
        return listOf(z to z) + zonas
    }
}

/** 41 Inmobiliaria: los datos (nombre, zona horaria, CBU o alias para cobrar), la marca (resumen: se edita en Mi
 *  web), qué hacen y dónde, y los datos legales que la web exhibe en el pie. Los cambia sólo el dueño. */
@Composable
fun ConfigInmobiliaria() {
    val estado = LocalEstado.current
    val ctx = LocalContext.current
    val alcance = rememberCoroutineScope()
    var ficha by remember { mutableStateOf<ConfigInmobiliaria.Ficha?>(null) }
    var error by remember { mutableStateOf<ErrorApi?>(null) }
    var nombre by remember { mutableStateOf("") }
    var cbu by remember { mutableStateOf("") }
    var zona by remember { mutableStateOf("America/Argentina/Buenos_Aires") }
    var legales by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var guardando by remember { mutableStateOf<String?>(null) }
    var guardado by remember { mutableStateOf<String?>(null) }
    var campos by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    val duenio = estado.esDuenio

    suspend fun cargar() {
        try {
            val f: ConfigInmobiliaria.Ficha = estado.api.get("/v1/inmobiliaria")
            ficha = f
            nombre = f.nombre
            cbu = f.cbu ?: ""
            zona = f.zonaHoraria?.takeIf { it.isNotBlank() } ?: zona
            legales = (f.legales ?: emptyMap()).mapNotNull { (k, v) -> v?.let { k to it } }.toMap()
            error = null
        } catch (e: CancellationException) { throw e } catch (e: ErrorApi) { if (ficha == null) error = e } catch (e: Exception) {}
    }

    @Serializable data class CuerpoDatos(val nombre: String, val zonaHoraria: String, val cbu: String)
    @Serializable data class CuerpoLegales(val legales: Map<String, String>)

    suspend fun guardarDatos() {
        val n = nombre.trim()
        val c = cbu.trim()
        val nuevos = mutableMapOf<String, String>()
        campos = emptyMap()
        guardado = null
        if (n.isEmpty()) nuevos["nombre"] = "Poné el nombre de la inmobiliaria."
        if (c.isNotEmpty() && !ConfigInmobiliaria.cbuValido(c)) nuevos["cbu"] = "El CBU tiene 22 números; el alias, de 6 a 20 letras, números, puntos o guiones."
        if (nuevos.isNotEmpty()) {
            campos = nuevos
            return estado.avisar(if (nuevos["cbu"] != null) "Revisá el CBU o alias." else "Revisá los datos.", ok = false)
        }
        guardando = "datos"
        try {
            estado.api.pedirConCampos("PATCH", "/v1/inmobiliaria", CuerpoDatos(n, zona, c))
            guardado = "datos"
            estado.avisar("Guardado")
        } catch (e: CancellationException) { throw e } catch (e: Exception) { campos = estado.camposDe(e) } finally { guardando = null }
    }

    suspend fun guardarLegales() {
        campos = emptyMap()
        guardado = null
        guardando = "legales"
        val v = mutableMapOf<String, String>()
        for (cl in ConfigInmobiliaria.camposLegales) v[cl.clave] = (legales[cl.clave] ?: "").trim()
        try {
            estado.api.pedirConCampos("PATCH", "/v1/inmobiliaria", CuerpoLegales(v))
            guardado = "legales"
            estado.avisar("Guardado")
        } catch (e: CancellationException) { throw e } catch (e: Exception) { campos = estado.camposDe(e) } finally { guardando = null }
    }

    /** «Guardado» y el botón, como el pie de cada tarjeta de la web. */
    @Composable
    fun pie(cual: String, hacer: () -> Unit) {
        Separador()
        FilaLista {
            if (guardado == cual) ConIcono("Guardado", "checkmark.icloud", Estilo.footnote, C.okTinta)
            Spacer(Modifier.weight(1f))
            if (guardando == cual) Rueda() else BotonHoja("Guardar", fuerte = true, habilitado = guardando == null, alTocar = hacer)
        }
    }

    // MARK: Datos

    @Composable
    fun datos(f: ConfigInmobiliaria.Ficha) {
        SeccionLista("Datos") {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 11.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Entrada(nombre, { v -> nombre = v.take(120) }, "Nombre de la inmobiliaria", habilitado = duenio)
                ErrorDeCampo(campos["nombre"])
            }
            val w = f.web
            if (!w.isNullOrEmpty()) {
                Separador()
                Column(Modifier.padding(horizontal = 16.dp, vertical = 11.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Texto("Tu web", Estilo.footnoteFuerte, C.texto2)
                    Row(Modifier.clickable { abrirUrl(ctx, w) }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icono("arrow.up.right.square", 13f, C.accion)
                        Texto(w.replace("https://", "").replace("http://", ""), Estilo.footnote, C.accion, monoespaciado = true)
                    }
                    TextoRico(buildAnnotatedString {
                        withStyle(SpanStyle(color = C.texto2)) { append("El dominio propio se conecta en ") }
                        withStyle(SpanStyle(color = C.accion)) { append("Mi web") }
                        withStyle(SpanStyle(color = C.texto2)) { append(".") }
                    }, Estilo.footnote, C.texto2, Modifier.clickable { estado.abrir(ASeccion(clave = "web")) })
                }
            }
            Separador()
            Column(Modifier.padding(horizontal = 16.dp, vertical = 11.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Entrada(cbu, { v -> cbu = v.take(22) }, "luna.propiedades", Modifier.semantics { contentDescription = "CBU o alias para cobrar" }, habilitado = duenio, sinCorrector = true,
                    mayusculas = KeyboardCapitalization.None, monoespaciado = true)
                Texto("Sale en los recibos y en los recordatorios de pago.", Estilo.footnote, C.texto3)
                ErrorDeCampo(campos["cbu"])
            }
            Separador()
            Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth().deshabilitado(!duenio), verticalAlignment = Alignment.CenterVertically) {
                    // El Picker de iOS: la etiqueta en un renglón y el valor a la derecha, que es el que se acorta.
                    Texto("Zona horaria", Estilo.body, C.texto, lineas = 1)
                    Box(Modifier.weight(1f).padding(start = 12.dp), contentAlignment = Alignment.CenterEnd) {
                        SelectorMenu(ConfigInmobiliaria.opcionesZona(f), zona, { zona = it }, descripcion = "Zona horaria")
                    }
                }
                Texto("Con ella se calcula «hoy» en la agenda, los vencimientos y los avisos.", Estilo.footnote, C.texto3)
                ErrorDeCampo(campos["zonaHoraria"])
            }
            if (duenio) pie("datos") { alcance.launch { guardarDatos() } }
        }
    }

    // MARK: Qué hacen y dónde

    @Composable
    fun queHacen(f: ConfigInmobiliaria.Ficha) {
        SeccionLista("Qué hacen y dónde") {
            val rubros = (f.perfil?.rubros ?: emptyList()).map { ConfigInmobiliaria.rubros[it] ?: it }
            Box(Modifier.padding(horizontal = 16.dp, vertical = 11.dp)) {
                if (rubros.isEmpty()) {
                    Texto(Formato.sinViudas("Todavía no elegiste. Mientras tanto, la app y el panel muestran todo."), Estilo.subhead, C.texto2)
                } else {
                    val zonas = f.perfil?.zonas ?: emptyList()
                    Texto(Formato.sinViudas(rubros.joinToString(" · ") + (if (zonas.isEmpty()) "" else " · en ${zonas.joinToString(", ")}") + ". La app y el panel muestran sólo lo que usás."),
                        Estilo.subhead, C.texto2)
                }
            }
            if (duenio) {
                Separador()
                FilaLista({ estado.altaAbierta = PasoAlta(clave = "rubros") }) { Texto("Cambiar", Estilo.body, C.accion) }
            }
        }
    }

    // MARK: Marca

    @Composable
    fun marca(f: ConfigInmobiliaria.Ficha) {
        val m = remember(f.marca, f.nombre) { MarcaResumen(f.marca, f.nombre) }
        SeccionLista("Marca") {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (m.logo != null) {
                    AsyncImage(m.logo, null, Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(Color.White).semantics { contentDescription = "Logo de ${f.nombre}" },
                        contentScale = ContentScale.Fit)
                } else {
                    Box(Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(m.primario), contentAlignment = Alignment.Center) {
                        Texto(m.inicial, Estilo(20f, Peso.bold), m.acento)
                    }
                }
                Row(Modifier.semantics { contentDescription = "Colores de la web" }, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(m.primario, m.acento, m.papel).forEach { c ->
                        Box(Modifier.size(26.dp).clip(CircleShape).background(c).border(1.dp, C.linea, CircleShape))
                    }
                }
            }
            Separador()
            Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().height(120.dp).clip(RoundedCornerShape(14.dp)).background(m.primario)) {
                if (m.portada != null) AsyncImage(m.portada, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)))))
                Texto(m.frase, Estilo(17f, Peso.bold), Color.White, Modifier.align(Alignment.BottomStart).padding(12.dp))
            }
            Separador()
            FilaLista({ estado.abrir(ASeccion(clave = "web")) }) { Texto("Editar en Mi web", Estilo.body, C.accion) }
        }
    }

    // MARK: Legales

    @Composable
    fun seccionLegales() {
        SeccionLista("Datos legales", "Van en el pie de tu web y en los recibos. En CABA y Provincia, la matrícula es obligatoria.") {
            ConfigInmobiliaria.camposLegales.forEachIndexed { i, cl ->
                if (i > 0) Separador()
                Column(Modifier.padding(horizontal = 16.dp, vertical = 11.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Entrada(
                            legales[cl.clave] ?: "", { v -> legales = legales + (cl.clave to v.take(cl.max)) },
                            if (cl.clave == "cuit") "30-71584209-9" else if (cl.clave == "colegio") "CUCICBA" else cl.nombre,
                            Modifier.weight(1f).semantics { contentDescription = cl.nombre },
                            teclado = if (cl.clave == "cuit" || cl.clave == "matricula") KeyboardType.Ascii else KeyboardType.Text,
                            monoespaciado = cl.clave == "cuit", habilitado = duenio,
                        )
                        if (cl.clave == "colegio" && duenio) {
                            MenuBoton(listOf(
                                OpcionDeMenu("CUCICBA", "") { legales = legales + ("colegio" to "CUCICBA") },
                                OpcionDeMenu("Colegio de Martilleros y Corredores Públicos de …", "") { legales = legales + ("colegio" to "Colegio de Martilleros y Corredores Públicos de ") },
                            ), "Sugerencias") {
                                Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) { Icono("chevron.down.circle", 17f, C.accion) }
                            }
                        }
                    }
                    cl.ayuda?.let { Texto(it, Estilo.footnote, C.texto3) }
                    ErrorDeCampo(campos["legales." + cl.clave] ?: campos[cl.clave])
                }
            }
            if (duenio) pie("legales") { alcance.launch { guardarLegales() } }
        }
    }

    LaunchedEffect(estado.version) { cargar() }

    PantallaApilada("Inmobiliaria") {
        val f = ficha
        val err = error
        if (f != null) {
            ListaAgrupada {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Texto(Formato.sinViudas("Los datos de ${f.nombre}: los que ve tu equipo y los que muestra tu web."), Estilo.footnote, C.texto2)
                    if (!duenio) {
                        AvisoRol("Tu rol es ${if (estado.rol == "lector") "Lector" else "Agente"}: podés ver estos datos. Los cambia el dueño o la dueña de la inmobiliaria.")
                    }
                }
                datos(f)
                queHacen(f)
                marca(f)
                seccionLegales()
            }
        } else if (err != null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EstadoError("los datos de la inmobiliaria", err) { alcance.launch { cargar() } }
            }
        } else {
            Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.TopCenter) { EsqueletoFilas(6) }
        }
    }
}

/** La marca de la web, resumida (marcaDe de lib/sitio/marca.ts): colores con los nombres que usó cada versión del
 *  editor, y los de ejemplo (Luna Propiedades) si falta algo. */
class MarcaResumen(marca: JsonElement?, nombre: String) {
    val primario: Color
    val acento: Color
    val papel: Color
    val logo: String?
    val portada: String?
    val frase: String
    val inicial: String

    init {
        fun texto(o: JsonElement?, claves: List<String>): String? {
            for (k in claves) {
                // nonEmpty de iOS: descarta lo que es sólo espacios, pero devuelve el texto tal cual.
                val t = ((o as? JsonObject)?.get(k) as? JsonPrimitive)?.takeIf { it.isString }?.content
                if (t != null && t.isNotBlank()) return t
            }
            return null
        }
        val colores = (marca as? JsonObject)?.get("colores")
        primario = color(texto(marca, listOf("colorPrimario", "primario", "primaryColor", "color")) ?: texto(colores, listOf("primario"))) ?: hexColor(0x1E3A5F)
        acento = color(texto(marca, listOf("colorSecundario", "secundario", "secondaryColor", "acento", "accentColor")) ?: texto(colores, listOf("secundario", "acento"))) ?: hexColor(0xE9DCC3)
        papel = color(texto(marca, listOf("colorFondo", "fondo", "papel")) ?: texto(colores, listOf("fondo"))) ?: hexColor(0xFAF8F3)
        logo = texto(marca, listOf("logoUrl", "logo"))?.takeIf { it.startsWith("https://") }
        portada = texto(marca, listOf("portadaUrl", "portada", "heroImageUrl", "foto"))?.takeIf { it.startsWith("https://") }
        frase = texto(marca, listOf("frase", "tagline", "titulo")) ?: "Tu próximo lugar"
        inicial = (nombre.trim().firstOrNull()?.toString() ?: "·").uppercase()
    }

    private companion object {
        fun hexColor(n: Long): Color = Color(0xFF000000 or n)

        /** Acento.rgb + Acento.hex de iOS: «#1E5749», «1e5749» o «#abc» → el color; si no, nil. */
        fun color(s: String?): Color? = colorDeHex(s)
    }
}
