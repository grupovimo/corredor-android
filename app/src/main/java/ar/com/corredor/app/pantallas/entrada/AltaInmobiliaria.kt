package ar.com.corredor.app.pantallas.entrada

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ar.com.corredor.app.app.LocalEstado
import ar.com.corredor.app.nucleo.AAltaPropiedad
import ar.com.corredor.app.nucleo.ASeccion
import ar.com.corredor.app.nucleo.Alta
import ar.com.corredor.app.nucleo.Api
import ar.com.corredor.app.nucleo.ErrorApi
import ar.com.corredor.app.nucleo.Formato
import ar.com.corredor.app.nucleo.cargarEstructura
import ar.com.corredor.app.nucleo.opcional
import ar.com.corredor.app.pantallas.web.BorradorWeb
import ar.com.corredor.app.pantallas.web.ElegirColor
import ar.com.corredor.app.pantallas.web.MarcoPrevia
import ar.com.corredor.app.pantallas.web.SubirImagenMarca
import ar.com.corredor.app.pantallas.web.hexColor
import ar.com.corredor.app.pantallas.web.poner
import ar.com.corredor.app.pantallas.web.texto
import ar.com.corredor.app.tema.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

// Clon de Pantallas/Entrada/AltaInmobiliaria.swift.

private val rubrosTodos = listOf(Triple("ventas", "Ventas", "tag"), Triple("alquileres", "Alquileres", "key"), Triple("temporada", "Temporada", "beach.umbrella"),
    Triple("emprendimientos", "Emprendimientos", "building"), Triple("consorcios", "Consorcios", "building.columns"))
/** Los colores de siempre (después de los del logo). */
private val principales = listOf(0x16443B, 0x1E3A5F, 0x7C2D12, 0x4A2C5A, 0x1F5A2A, 0xB45309)
private val acentos = listOf(0xF2BB57, 0xE9DCC3, 0x7FC4AD, 0xF28B7D)
private val legalesCampos = listOf(Triple("cuit", "CUIT", "30-71234567-1"), Triple("matricula", "Matrícula", "CUCICBA 1234"), Triple("colegio", "Colegio", "Opcional"),
    Triple("corredorResponsable", "Corredor responsable", "Opcional"), Triple("razonSocial", "Razón social", "Opcional"))

@Serializable private data class EstructuraAlta(val alta: Alta)
@Serializable private data class PerfilInmo(val rubros: List<String>? = null, val zonas: List<String>? = null)
@Serializable private data class InmoAlta(val legales: Map<String, String?>? = null, val perfil: PerfilInmo? = null)
@Serializable private data class CuerpoLegales(val legales: Map<String, String>)
@Serializable private data class CuerpoPerfil(val perfil: PerfilRubros)
@Serializable private data class PerfilRubros(val rubros: List<String>, val zonas: List<String>)
@Serializable private data class CuerpoReescribir(val campo: String = "frase", val texto: String)
@Serializable private data class Reescrito(val texto: String)
@Serializable private data class CuerpoTerminada(val altaTerminada: Boolean = true)

/** El alta de la inmobiliaria, paso por paso (los mismos que el panel web; los da la API): logo, colores y frase,
 *  portada, contacto, datos legales, qué hacen y dónde, el arranque y «Así quedó tu web». Obligatorios: el logo y el
 *  WhatsApp (sin ellos no se entra). Lo demás tiene «Más tarde» y queda en el porcentaje. Cada paso guarda al seguir: si
 *  se cierra, se retoma en el mismo lugar. La marca va al borrador de la web y se publica en el último paso.
 *  `inicial`: el paso en el que abre (null: el primero que falta). */
@Composable
fun AltaInmobiliaria(inicial: String? = null) {
    val estado = LocalEstado.current
    val cerrar = LocalCerrarHoja.current
    val alcance = rememberCoroutineScope()
    var alta by remember { mutableStateOf<Alta?>(null) }
    var paso by remember { mutableStateOf("") }
    var marca by remember { mutableStateOf<Map<String, JsonElement>>(emptyMap()) }
    var en by remember { mutableStateOf<String?>(null) }
    var legales by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var rubros by remember { mutableStateOf<Set<String>>(emptySet()) }
    var zonas by remember { mutableStateOf<List<String>>(emptyList()) }
    var zonaNueva by remember { mutableStateOf("") }
    var sugeridos by remember { mutableStateOf<List<Int>>(emptyList()) }
    var guardando by remember { mutableStateOf(false) }
    var reescribiendo by remember { mutableStateOf(false) }
    var previa by remember { mutableStateOf<String?>(null) }
    var recarga by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<ErrorApi?>(null) }

    val pasos: List<Alta.Paso> = alta?.pasos?.filter { it.clave != "nombre" } ?: emptyList()
    val indice = pasos.indexOfFirst { it.clave == paso }.coerceAtLeast(0)
    val actual: Alta.Paso? = pasos.firstOrNull { it.clave == paso }
    /** Lo mínimo para entrar (el logo y el WhatsApp), con lo cargado acá aunque la API no lo sepa todavía. */
    val minimoListo = marca.texto("logoUrl").isNotEmpty() && marca.texto("whatsapp").isNotEmpty()
    val siguiente: String? = if (indice + 1 < pasos.size) pasos[indice + 1].clave else null

    fun ponerMarca(k: String, v: String) { marca = marca.toMutableMap().apply { poner(k, v) } }

    fun ir(clave: String?) {
        if (clave == null) return
        paso = clave
    }

    fun sumarZona() {
        val z = zonaNueva.trim()
        if (z.isNotEmpty() && z !in zonas) zonas = zonas + z
        zonaNueva = ""
    }

    suspend fun cargar() {
        error = null
        try {
            val e: EstructuraAlta = estado.api.get("/v1/panel/estructura")
            val b = BorradorWeb.leer(estado.api)
            val i: InmoAlta = estado.api.get("/v1/inmobiliaria")
            alta = e.alta
            marca = b.marca
            en = b.en
            legales = (i.legales ?: emptyMap()).mapNotNull { (k, v) -> v?.let { k to it } }.toMap()
            rubros = (i.perfil?.rubros ?: emptyList()).toSet()
            zonas = i.perfil?.zonas ?: emptyList()
            if (paso.isEmpty()) {
                val ps = e.alta.pasos.filter { it.clave != "nombre" }
                paso = ps.firstOrNull { it.clave == inicial }?.clave ?: ps.firstOrNull { !it.hecho }?.clave ?: ps.lastOrNull()?.clave ?: ""
            }
        } catch (x: CancellationException) { throw x } catch (x: Exception) { error = x as? ErrorApi ?: ErrorApi.desconocido }
    }

    /** El porcentaje y los pasos hechos, al día con la API. */
    suspend fun refrescarAlta() {
        estado.api.opcional<EstructuraAlta>("/v1/panel/estructura")?.let { alta = it.alta }
    }

    suspend fun guardarMarca(): Boolean {
        return try {
            val b = BorradorWeb.guardar(estado.api, marca, en)
            en = b.en
            recarga += 1
            true
        } catch (x: CancellationException) { throw x } catch (x: Exception) {
            if (x is ErrorApi && x.codigo == "borrador_cambiado") {
                estado.avisar("Tu web se cambió desde otro lado. Trajimos lo último.", ok = false)
                runCatching { BorradorWeb.leer(estado.api) }.getOrNull()?.let { marca = it.marca; en = it.en }
            } else estado.avisar(x)
            false
        }
    }

    suspend fun seguir(a: Alta.Paso) {
        if (a.clave == "logo" && marca.texto("logoUrl").isEmpty()) return estado.avisar("Subí tu logo para seguir.", ok = false)
        if (a.clave == "contacto" && marca.texto("whatsapp").isEmpty()) return estado.avisar("Poné tu WhatsApp para seguir: ahí te escriben desde la web.", ok = false)
        guardando = true
        var ok = true
        when (a.clave) {
            "logo", "marca", "portada", "contacto" -> ok = guardarMarca()
            "legales" -> try { estado.api.hacer("PATCH", "/v1/inmobiliaria", CuerpoLegales(legales)) } catch (x: CancellationException) { throw x } catch (x: Exception) { estado.avisar(x); ok = false }
            "rubros" -> {
                sumarZona()
                try { estado.api.hacer("PATCH", "/v1/inmobiliaria", CuerpoPerfil(PerfilRubros(rubros.sorted(), zonas))) } catch (x: CancellationException) { throw x } catch (x: Exception) { estado.avisar(x); ok = false }
            }
            else -> {}
        }
        if (ok) {
            refrescarAlta()
            ir(siguiente)
        }
        guardando = false
    }

    suspend fun frasePorIA() {
        reescribiendo = true
        val base = if (marca.texto("frase").isEmpty()) "Propiedades en ${if (zonas.isEmpty()) "tu zona" else zonas.joinToString(", ")} con ${estado.inmobiliaria?.nombre ?: "nosotros"}" else marca.texto("frase")
        try {
            val r: Reescrito = estado.api.enviar("POST", "/v1/web/reescribir", CuerpoReescribir(texto = base))
            marca = marca + ("frase" to JsonPrimitive(r.texto))
        } catch (x: CancellationException) { throw x } catch (x: Exception) { estado.avisar(x) }
        reescribiendo = false
    }

    /** «Publicar mi web»: publica el borrador (si hay) y da el alta por terminada. */
    suspend fun terminar() {
        guardando = true
        try {
            val b = BorradorWeb.leer(estado.api)
            if (b.hayBorrador) BorradorWeb.publicar(estado.api)
            estado.api.hacer("PATCH", "/v1/inmobiliaria", CuerpoTerminada())
            estado.cargarEstructura()
            Haptico.exito()
            estado.avisar("¡Tu web está publicada!")
            cerrar()
        } catch (x: CancellationException) { throw x } catch (x: Exception) { estado.avisar(x) }
        guardando = false
    }

    LaunchedEffect(Unit) { cargar() }

    @Composable
    fun CampoTexto(etiqueta: String, ejemplo: String, k: String, teclado: KeyboardType = KeyboardType.Text, valor: String? = null, alCambiar: ((String) -> Unit)? = null) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Texto(etiqueta, Estilo.label, C.texto)
            if (alCambiar != null) {
                CampoCorredor(valor ?: "", alCambiar, ejemplo, teclado = teclado, mayusculas = if (teclado == KeyboardType.Email) KeyboardCapitalization.None else KeyboardCapitalization.Sentences)
            } else {
                CampoMarca(marca, k, { ponerMarca(k, it) }, ejemplo, teclado = teclado)
            }
        }
    }

    @Composable
    fun TarjetaArranque(icono: String, titulo: String, texto: String, hacer: () -> Unit) {
        Tocable({
            // Se hace ahí mismo: el alta se cierra y vuelve desde «Completar». Lo que abre (MIRA es otra pantalla completa)
            // espera a que termine de cerrarse.
            cerrar()
            estado.tarea {
                delay(450)
                hacer()
            }
        }) {
            Row(Modifier.fillMaxWidth().tarjeta().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                IconoTono(icono, Tono.verde, tam = 42.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Texto(titulo, Estilo.filaLista, C.texto)
                    Texto(texto, Estilo.footnote, C.texto2)
                }
                Icono("chevron.right", 13f, C.texto3)
            }
        }
    }

    @Composable
    fun Contenido(clave: String) {
        when (clave) {
            "logo" -> SubirImagenMarca("Subí tu logo", marca["logoUrl"].texto) { u, colores ->
                var m = marca + ("logoUrl" to JsonPrimitive(u))
                sugeridos = colores
                // Su color más fuerte queda elegido (se cambia en el paso que sigue).
                if (m.texto("colorPrimario").isEmpty()) colores.firstOrNull()?.let { c -> m = m + ("colorPrimario" to JsonPrimitive(c.hexColor)) }
                marca = m
                alcance.launch { guardarMarca() }
            }
            "marca" -> Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {
                ElegirColor("Color principal", sugeridos + principales, marca.texto("colorPrimario")) { ponerMarca("colorPrimario", it) }
                ElegirColor("Color de acento", acentos + sugeridos.drop(1), marca.texto("colorSecundario")) { ponerMarca("colorSecundario", it) }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Texto("Frase de la portada", Estilo.filaLista, C.texto)
                    CampoMarca(marca, "frase", { ponerMarca("frase", it) }, "Tu próximo hogar en Palermo", lineasMin = 2, lineasMax = 3)
                    BotonSecundario(if (reescribiendo) "Escribiendo…" else "Escribila con IA", { alcance.launch { frasePorIA() } }, simbolo = "sparkles", habilitado = !reescribiendo)
                }
            }
            "portada" -> SubirImagenMarca("Subí la foto de portada", marca["portadaUrl"].texto, ancha = true) { u, _ ->
                marca = marca + ("portadaUrl" to JsonPrimitive(u))
                alcance.launch { guardarMarca() }
            }
            "contacto" -> Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                CampoTexto("WhatsApp", "11 5555 0000", "whatsapp", teclado = KeyboardType.Phone)
                CampoTexto("Teléfono", "Opcional", "telefono", teclado = KeyboardType.Phone)
                CampoTexto("Mail", "Opcional", "email", teclado = KeyboardType.Email)
                CampoTexto("Dirección", "Opcional", "direccion")
                CampoTexto("Horario", "Lunes a viernes de 10 a 18", "horario")
            }
            "legales" -> Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                legalesCampos.forEach { (k, n, p) ->
                    key(k) {
                        CampoTexto(n, p, k, teclado = if (k == "cuit") KeyboardType.Phone else KeyboardType.Text, valor = legales[k] ?: "") { legales = legales + (k to it) }
                    }
                }
                Texto(Formato.sinViudas("Van en el pie de tu web y en los recibos. En CABA y Provincia, la matrícula es obligatoria."), Estilo.footnote, C.texto2)
            }
            "rubros" -> Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                FlujoChips(rubrosTodos, rubros, { rubros = it })
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Texto("Dónde trabajan", Estilo.filaLista, C.texto)
                    if (zonas.isNotEmpty()) {
                        FlujoChips(zonas.map { Triple(it, it, "xmark") }, zonas.toSet(), {}, alQuitar = { z -> zonas = zonas.filter { it != z } })
                    }
                    CampoCorredor(zonaNueva, { zonaNueva = it }, "Palermo, Belgrano, Pilar…", accion = ImeAction.Done, alAccion = { sumarZona() })
                }
                Texto(Formato.sinViudas("Así la app te muestra sólo lo que usás. Lo cambiás cuando quieras."), Estilo.footnote, C.texto2)
            }
            "arranque" -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TarjetaArranque("viewfinder", "Sacale una foto al cartel", "MIRA carga la propiedad sola.") { estado.miraAbierta = true }
                TarjetaArranque("house", "Cargá una propiedad", "Fotos, precio y listo.") { estado.abrir(AAltaPropiedad()) }
                TarjetaArranque("square.and.arrow.down", "Traé tu Excel", "Tu cartera de otro sistema.") { estado.abrir(ASeccion(clave = "importar")) }
                TarjetaArranque("person.badge.plus", "Invitá a tu equipo", "Cada agente con su cartera.") { estado.abrir(ASeccion(clave = "equipo")) }
                TarjetaArranque("bubble.left.and.bubble.right", "Conectá WhatsApp", "Las consultas en una bandeja.") { estado.abrir(ASeccion(clave = "integraciones")) }
                Texto("Lo que no hagas ahora queda en tu Inicio.", Estilo.footnote, C.texto2)
            }
            "publicar" -> {
                LaunchedEffect(Unit) { previa = runCatching { BorradorWeb.urlPrevia(estado.api, estado.inmobiliaria?.slug ?: "") }.getOrElse { if (it is CancellationException) throw it; null } }
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    MarcoPrevia(previa, recarga, 480.dp) { }
                    estado.inmobiliaria?.let { "${it.slug}.${runCatching { android.net.Uri.parse(Api.web()).host }.getOrNull() ?: "corredor.com.ar"}" }?.let { w ->
                        Row(Modifier.fillMaxWidth().tarjeta().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icono("globe", 17f, C.accion)
                            Texto(w, Estilo.subheadFuerte, C.texto, Modifier.weight(1f), lineas = 1)
                            Texto("Copiar", Estilo.subheadFuerte, C.accion, Modifier.clickable { estado.copiar("https://$w"); estado.avisar("Link copiado") })
                        }
                    }
                }
            }
            else -> {}
        }
    }

    Box(Modifier.fillMaxSize()) {
        FondoApp(Modifier.matchParentSize())
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            // «Ir al inicio» (ToolbarItem .cancellationAction), cuando ya está lo mínimo.
            Box(Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 8.dp)) {
                if (minimoListo || alta?.minimoListo == true) {
                    Box(Modifier.align(Alignment.CenterStart).heightIn(min = 44.dp).clickable(onClick = cerrar).padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
                        Texto("Ir al inicio", Estilo.body, C.accion)
                    }
                }
            }
            val a = actual
            val e = error
            if (a != null) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).imePadding().padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    // Progreso
                    Row(Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = "Paso ${indice + 1} de ${pasos.size}. Tu inmobiliaria al ${alta?.completo ?: 0} por ciento" },
                        horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        pasos.forEachIndexed { i, p ->
                            Box(Modifier.weight(1f).height(4.dp).clip(CircleShape).background(if (i == indice) C.oro else if (p.hecho) C.accion else C.linea))
                        }
                        Texto("${alta?.completo ?: 0}%", Estilo(13f, Peso.bold), C.texto2, Modifier.padding(start = 6.dp), numeros = true)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Texto(if (a.obligatorio) "OBLIGATORIO" else "PASO ${indice + 1} DE ${pasos.size}", Estilo(12f, Peso.bold, 0.8f / 12f), C.oroTinta)
                        Texto(a.titulo, Estilo.tituloPaso, C.texto, Modifier.semantics { heading() })
                        Texto(Formato.sinViudas(a.texto), Estilo.subhead, C.texto2)
                    }
                    Contenido(a.clave)
                }
                // Los botones (.safeAreaInset(edge: .bottom)).
                Column(Modifier.fillMaxWidth().background(C.fondo.copy(alpha = 0.92f)).padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (a.clave == "publicar") {
                        BotonPrincipal(if (guardando) "Publicando…" else "Publicar mi web", { alcance.launch { terminar() } }, habilitado = !guardando, simbolo = "paperplane.fill")
                    } else {
                        BotonPrincipal(if (guardando) "Guardando…" else "Seguir", { alcance.launch { seguir(a) } }, habilitado = !guardando) {
                            TextoIcono(if (guardando) "Guardando…" else "Seguir", "arrow.right", C.sobreAccion)
                        }
                        if (!a.obligatorio) BotonTexto("Más tarde", { ir(siguiente) }, alto = 44.dp, color = C.texto2)
                    }
                    if (indice > 0) BotonTexto("Atrás", { ir(pasos[indice - 1].clave) }, alto = 40.dp, color = C.texto2)
                }
            } else if (e != null) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { EstadoError("el alta", e) { alcance.launch { cargar() } } }
            } else {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { Rueda() }
            }
        }
    }
}

/** Un campo de la marca: el texto se escribe tal cual y a la marca va recortado (`marca.poner`); si la marca cambia de
 *  afuera (la frase por IA, otro aparato), el campo la toma. */
@Composable
private fun CampoMarca(marca: Map<String, JsonElement>, k: String, alCambiar: (String) -> Unit, ejemplo: String, teclado: KeyboardType = KeyboardType.Text, lineasMin: Int = 1, lineasMax: Int = 1) {
    var t by remember(k) { mutableStateOf(marca.texto(k)) }
    val deAfuera = marca.texto(k)
    LaunchedEffect(deAfuera) { if (deAfuera != t.trim()) t = deAfuera }
    CampoCorredor(t, { t = it; alCambiar(it) }, ejemplo, lineasMin = lineasMin, lineasMax = lineasMax, teclado = teclado,
        mayusculas = if (teclado == KeyboardType.Email) KeyboardCapitalization.None else KeyboardCapitalization.Sentences)
}

/** Chips que se eligen tocándolos (rubros) o se quitan (zonas), en filas que bajan solas. `alQuitar`: tocar quita (el
 *  ícono es la cruz) en vez de elegir. */
@Composable
fun FlujoChips(items: List<Triple<String, String, String>>, elegidos: Set<String>, alCambiar: (Set<String>) -> Unit, alQuitar: ((String) -> Unit)? = null) {
    FlujoFilas(8.dp) {
        items.forEach { (clave, texto, icono) ->
            key(clave) {
                val on = alQuitar == null && clave in elegidos
                Tocable({
                    Haptico.seleccion()
                    if (alQuitar != null) alQuitar(clave) else if (on) alCambiar(elegidos - clave) else alCambiar(elegidos + clave)
                }, Modifier.semantics { selected = on }) {
                    Row(
                        Modifier.heightIn(min = 44.dp).clip(CircleShape).background(if (on) C.accion else C.superficie)
                            .border(1.dp, if (on) Color.Transparent else C.linea2, CircleShape).padding(horizontal = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icono(icono, 15f, if (on) C.sobreAccion else C.texto)
                        Texto(texto, Estilo.subheadFuerte, if (on) C.sobreAccion else C.texto)
                    }
                }
            }
        }
    }
}

/** Acomoda las vistas en filas: cuando no entran, bajan a la siguiente (el Layout de iOS). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FlujoFilas(espacio: Dp = 8.dp, contenido: @Composable () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(espacio), verticalArrangement = Arrangement.spacedBy(espacio)) { contenido() }
}

/** «Seguir →»: el texto y el ícono después (el LabelStyle `TextoIcono` de iOS). */
@Composable
fun TextoIcono(texto: String, simbolo: String, color: Color, estilo: Estilo = Estilo.headline) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Texto(texto, estilo, color, lineas = 1)
        Icono(simbolo, estilo.tamano, color)
    }
}
