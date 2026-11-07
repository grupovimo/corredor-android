package ar.com.corredor.app.pantallas.mas

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ar.com.corredor.app.app.Consejos
import ar.com.corredor.app.app.Estado
import ar.com.corredor.app.app.Insignias
import ar.com.corredor.app.app.Sesion
import ar.com.corredor.app.app.LocalEstado
import ar.com.corredor.app.nucleo.*
import ar.com.corredor.app.pantallas.entrada.ElegirInmobiliaria
import ar.com.corredor.app.pantallas.propiedades.Inmobiliaria
import ar.com.corredor.app.tema.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import java.text.Normalizer

// Clon de Pantallas/Mas/Mas.swift.

/** Algo que el negocio todavía no usa, con su beneficio en una línea. */
data class Potencia(val icono: String, val titulo: String, val texto: String, val destino: Any) {
    val id: String get() = titulo
}

/** La pestaña «Más», en dos partes (decisión de Nahuel, 5/11): «Mi inmobiliaria» (la web, los datos, el equipo, el
 *  plan…) y «Herramientas» (todo lo del día a día, por áreas). Arriba de todo la apariencia (claro, oscuro o
 *  automático) y el buscador; la tarjeta de la inmobiliaria con el porcentaje del alta mientras falte algo. La
 *  estructura la arma la API (la misma que el panel web); sin ella todavía, la de siempre. */
@Composable
fun MasRaiz() {
    val estado = LocalEstado.current
    val ctx = LocalContext.current
    val alcance = rememberCoroutineScope()
    val oscuro = LocalOscuro.current
    // @AppStorage("tema") y @AppStorage("todasLasSecciones").
    val tema = estado.tema
    var todas by remember { mutableStateOf(Preferencias.bool("todasLasSecciones")) }
    var filtro by remember { mutableStateOf("") }
    /** Lo que pasa en cada sección, a la derecha de su fila («3 deben», «WhatsApp sin conectar»), por clave. */
    var estados by remember { mutableStateOf<Map<String, Pair<String, Tono>>>(emptyMap()) }
    var potenciar by remember { mutableStateOf<List<Potencia>>(emptyList()) }
    /** La web pública de la inmobiliaria («Ver la web»). */
    var web by remember { mutableStateOf<String?>(null) }
    /** «Tus inmobiliarias» (el «Cambiar» del menú del celular). */
    var cambiando by remember { mutableStateOf(false) }

    val partes = estado.estructura?.partes ?: MasRaiz.partesFijas(estado.esDuenio)
    val encontradas: List<Estructura.Item> = run {
        val f = MasRaiz.normal(filtro)
        if (f.isEmpty()) emptyList()
        else partes.flatMap { p -> p.grupos.flatMap { it.items } }.filter { MasRaiz.coincide(it, f) && Lugares.secciones[it.clave] != null }
    }

    /** El sol o la luna, para pasar de claro a oscuro en un toque (también arriba del Inicio). */
    val botonTema = AccionCabecera(
        icono = if (oscuro) "sun.max" else "moon",
        etiqueta = if (oscuro) "Pasar a modo claro" else "Pasar a modo oscuro",
    ) {
        Haptico.seleccion()
        estado.ponerTema(if (oscuro) "claro" else "oscuro")
    }

    /** Los contadores y lo que falta activar, con lo que ya da la API (sin pedidos pesados). */
    suspend fun cargar() {
        val api = estado.api
        val e = mutableMapOf<String, Pair<String, Tono>>()
        val guia: Guia? = api.opcional("/v1/panel/guia")
        val panel: Panel? = api.opcional("/v1/panel")
        val deudas: List<Deuda> = api.opcional("/v1/contratos/deudas") ?: emptyList()
        if (deudas.isNotEmpty()) e["cobranzas"] = "${deudas.size} ${if (deudas.size == 1) "debe" else "deben"}" to Tono.rojo
        panel?.numeros?.rendicionesListas?.let { n -> if (n > 0) e["rendiciones"] = "$n para rendir" to Tono.azul }
        panel?.numeros?.alquileresVigentes?.let { n -> if (n > 0) e["contratos"] = "$n vigentes" to Tono.neutro }
        panel?.numeros?.operacionesAbiertas?.let { n -> if (n > 0) e["operaciones"] = "$n abiertas" to Tono.neutro }
        guia?.contactos?.let { n -> if (n > 0) e["contactos"] = "$n" to Tono.neutro }
        if (estado.avisosPendientes > 0) e["avisos"] = "${estado.avisosPendientes}" to Tono.rojo
        api.opcional<JsonElement>("/v1/revision")?.let { j -> if (MasRaiz.cuenta(j) > 0) e["revision"] = "${MasRaiz.cuenta(j)} para revisar" to Tono.oro }
        api.opcional<JsonElement>("/v1/papelera")?.let { j -> if (MasRaiz.cuenta(j) > 0) e["papelera"] = "${MasRaiz.cuenta(j)}" to Tono.neutro }
        var iaConectada = true
        var consorcios = 1
        if (estado.esDuenio) {
            api.opcional<JsonElement>("/v1/ia/conexiones")?.let { j -> iaConectada = MasRaiz.cuenta(j) > 0 }
            api.opcional<JsonElement>("/v1/consorcios")?.let { j ->
                consorcios = MasRaiz.cuenta(j)
                if (consorcios > 0) e["consorcios"] = "$consorcios" to Tono.neutro
            }
            api.opcional<Borrador>("/v1/web/borrador")?.let { b -> if (b.hayBorrador) e["web"] = "Sin publicar" to Tono.oro }
        }
        val pasos = guia?.pasos
        if (pasos?.whatsapp == false) e["integraciones"] = "WhatsApp sin conectar" to Tono.oro
        if (e["web"] == null && pasos?.web == false) e["web"] = "Sin estrenar" to Tono.oro
        if (pasos?.portales == false) e["difusion"] = "Sin publicar" to Tono.oro
        if (pasos?.equipo == false) e["equipo"] = "Sólo vos" to Tono.neutro
        // Como el panel (lib/server/configuracion.ts): «6 · 1 invitación», «ML pide reconectar» o «ML con N
        // errores», «2 · 30 días» en la papelera.
        api.opcional<List<Miembro>>("/v1/equipo")?.let { personas ->
            var t = "${personas.size}"
            if (estado.esDuenio) {
                val inv = api.opcional<JsonElement>("/v1/invitaciones")
                if (inv != null && MasRaiz.cuenta(inv) > 0) t += " · ${Formato.cantidad(MasRaiz.cuenta(inv), "invitación", "invitaciones")}"
            }
            e["equipo"] = t to Tono.neutro
        }
        @Serializable data class ML(val conectada: Boolean? = null, val reautorizar: Boolean? = null)
        val ml: ML? = api.opcional("/v1/mercadolibre")
        if (ml?.conectada == true && ml.reautorizar == true) {
            e["integraciones"] = "ML pide reconectar" to Tono.oro
        } else {
            val j = api.opcional<JsonElement>("/v1/publicaciones", listOf("portal" to "mercadolibre", "estado" to "error", "limite" to "50"))
            if (j != null && MasRaiz.cuenta(j) > 0) e["integraciones"] = "ML con ${Formato.cantidad(MasRaiz.cuenta(j), "error", "errores")}" to Tono.oro
        }
        e["papelera"]?.first?.let { n -> e["papelera"] = "$n · 30 días" to Tono.neutro }
        api.opcional<Inmobiliaria>("/v1/inmobiliaria")?.let { i -> web = i.web?.takeIf { it.isNotEmpty() } }
        estado.suscripcion?.let { sus ->
            e["plan"] = sus.nombreEstado to (if (sus.estado == "vencida") Tono.rojo else if (sus.pideAtencion) Tono.oro else Tono.verde)
        }
        estados = e

        // Lo que se puede activar, sólo si la sección está a la vista (los rubros la pueden esconder).
        val visibles = partes.flatMap { p -> p.grupos.flatMap { g -> g.items.map { it.clave } } }.toSet()
        val x = mutableListOf<Potencia>()
        if (estado.esDuenio) {
            if (pasos?.whatsapp == false) x.add(Potencia("bubble.left.and.bubble.right", "Conectá tu WhatsApp", "Todas las consultas en una bandeja, con respuestas guardadas.", ASeccion(clave = "integraciones")))
            if ((guia?.contratos ?: 0) == 0 && "contratos" in visibles) x.add(Potencia("dollarsign.circle", "Cobrá los alquileres", "Quién debe, el recibo por WhatsApp y la rendición al dueño.", AContratos))
            if (pasos?.portales == false) x.add(Potencia("megaphone", "Publicá en portales", "Mercado Libre y los portales, sin cargar dos veces.", ASeccion(clave = "difusion")))
            if (!iaConectada) x.add(Potencia("sparkles", "Corredor en tu IA", "Preguntale a ChatGPT o Claude por tu cartera y tus cobros.", ASeccion(clave = "ia")))
            if (consorcios == 0 && "consorcios" in visibles) x.add(Potencia("building.columns", "Administrá consorcios", "Expensas, deudores y trabajos del edificio.", ASeccion(clave = "consorcios")))
            if (pasos?.equipo == false) x.add(Potencia("person.badge.plus", "Sumá a tu equipo", "Cada agente con su cartera y su agenda.", ASeccion(clave = "equipo")))
        }
        if ((guia?.propiedades ?: 0) < 5 && estado.escribe) x.add(Potencia("square.and.arrow.down", "Traé tu cartera", "Un Excel de tu sistema anterior y queda todo cargado.", ASeccion(clave = "importar")))
        potenciar = x
    }

    LaunchedEffect(estado.version) { cargar() }

    @Composable
    fun fila(it: Estructura.Item, icono: String) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 50.dp).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.width(28.dp), contentAlignment = Alignment.Center) { Icono(icono, 18f, C.accion, peso = Peso.medium) }
            // Text + Spacer(minLength: 0): el nombre usa todo lo que sobra (no la mitad).
            Texto(it.nombre, Estilo.filaLista, C.texto, Modifier.weight(1f))
            estados[it.clave]?.let { (t, tono) ->
                Texto(t, Estilo(13f, Peso.semibold), if (tono == Tono.neutro) C.texto3 else tono.tinta, lineas = 1)
            }
            Icono("chevron.right", 13f, C.texto3, peso = Peso.semibold)
        }
    }

    @Composable
    fun lista(items: List<Estructura.Item>) {
        Column(Modifier.fillMaxWidth().tarjeta()) {
            items.forEachIndexed { i, it ->
                val l = Lugares.secciones[it.clave]
                if (l != null) {
                    Box(Modifier.clickable { estado.abrir(l.second) }) {
                        fila(it, l.first)
                        if (i > 0) Box(Modifier.align(Alignment.TopStart).fillMaxWidth().padding(start = 54.dp).height(0.5.dp).background(C.linea))
                    }
                }
            }
        }
    }

    /** «Tu inmobiliaria al 70 %»: lo que falta del alta, a un toque. */
    @Composable
    fun completar(a: Alta) {
        val linea = C.linea; val accion = C.accion
        Tocable(
            { estado.altaAbierta = PasoAlta(clave = a.faltan.firstOrNull()) },
            descripcion = "Tu inmobiliaria está completa al ${a.completo} por ciento. Completar",
        ) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(M.rTarjeta)).background(C.crema).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.fillMaxSize()) {
                        val g = 5.dp.toPx()
                        drawCircle(linea, radius = (size.minDimension - g) / 2, style = Stroke(g))
                        drawArc(accion, -90f, 360f * a.completo / 100f, false,
                            topLeft = androidx.compose.ui.geometry.Offset(g / 2, g / 2),
                            size = androidx.compose.ui.geometry.Size(size.width - g, size.height - g),
                            style = Stroke(g, cap = StrokeCap.Round))
                    }
                    Texto("${a.completo}%", Estilo(13f, Peso.bold), C.texto)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Texto("Tu inmobiliaria al ${a.completo} %", Estilo.filaLista, C.texto)
                    Texto(Formato.sinViudas("Completala y tu web queda lista"), Estilo.footnote, C.texto2)
                }
                Icono("chevron.right", 14f, C.texto3, peso = Peso.semibold)
            }
        }
    }

    /** «Potenciá tu negocio»: lo que todavía no usa, en tarjetas que se deslizan, cada una con «Activar». */
    @Composable
    fun potencia() {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.padding(horizontal = 16.dp)) { TituloBloque("Potenciá tu negocio") }
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                potenciar.forEach { x ->
                    Column(Modifier.width(220.dp).height(200.dp).tarjeta().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconoTono(x.icono, Tono.verde, tam = 40.dp)
                        Texto(x.titulo, Estilo.filaLista, C.texto, lineas = 2)
                        Texto(Formato.sinViudas(x.texto), Estilo.footnote, C.texto2, lineas = 3)
                        Spacer(Modifier.weight(1f))
                        BotonAccion("Activar", { estado.abrir(x.destino) })
                    }
                }
            }
        }
    }

    /** La persona y su inmobiliaria: abre el perfil (cuenta, firma, Face ID, salir); «Cambiar» abre «Tus
     *  inmobiliarias» (pasar a otra, aceptar una invitación o crear otra), como el menú del celular. */
    @Composable
    fun persona() {
        Row(Modifier.fillMaxWidth().tarjeta().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) {
                Tocable({ estado.abrir(APerfil) }, descripcion = "Mi perfil y avisos") {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(48.dp).clip(CircleShape).background(C.verde700), contentAlignment = Alignment.Center) {
                            Texto(Formato.iniciales(estado.usuario?.completo ?: "C"), Estilo(16f, Peso.bold), C.oro)
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Texto(estado.usuario?.completo ?: "Tu cuenta", Estilo.filaLista, C.texto)
                            Texto(listOfNotNull(estado.inmobiliaria?.nombre, ElegirInmobiliaria.rol(estado.rol)).joinToString(" · "), Estilo.footnote, C.texto2, lineas = 1)
                        }
                    }
                }
            }
            Box(Modifier.heightIn(min = 44.dp).clickable { cambiando = true }.semantics { contentDescription = "Cambiar de inmobiliaria" }, contentAlignment = Alignment.Center) {
                Box(Modifier.height(34.dp).clip(Capsula).background(C.hundida).padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                    Texto("Cambiar", Estilo.footnoteFuerte, C.accion)
                }
            }
        }
    }

    /** «Ver la web»: la web pública de la inmobiliaria, se abre aparte. */
    @Composable
    fun verLaWeb(u: String) {
        Row(
            Modifier.fillMaxWidth().tarjeta().clickable { abrirUrl(ctx, u) }.semantics { contentDescription = "Ver la web (se abre aparte)" }
                .heightIn(min = 50.dp).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.width(28.dp), contentAlignment = Alignment.Center) { Icono("globe", 18f, C.accion, peso = Peso.medium) }
            Texto("Ver la web", Estilo.filaLista, C.texto, Modifier.weight(1f))
            Icono("arrow.up.right.square", 15f, C.texto3, peso = Peso.semibold)
        }
    }

    /** El pie del menú: quiénes están en la calle y si todo está guardado (o sin señal, en cola). */
    @Composable
    fun pie() {
        val ins = Insignias.compartidas
        Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (ins.enLaCalle.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy((-8).dp)) {
                        ins.enLaCalle.take(3).forEach { p ->
                            Box(Modifier.border(2.dp, C.fondo, CircleShape)) { AvatarIniciales(p.nombre, 26.dp) }
                        }
                    }
                    Texto("${ins.enLaCalle.size} en la calle", Estilo.footnoteFuerte, C.texto2)
                }
            }
            if (ins.sinRed || estado.cola.cambios.isNotEmpty()) {
                ConIcono("Sin señal · se envía al volver", "icloud.slash", Estilo.footnote, C.avisoTinta)
            } else {
                ConIcono("Todo guardado${ins.guardadoEn?.let { " · " + Formato.hace(it) } ?: ""}", "checkmark.icloud", Estilo.footnote, C.texto3)
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        FondoApp()
        Column(
            Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            CabeceraPestana(sobre = estado.inmobiliaria?.nombre, titulo = "Más", acciones = listOf(botonTema), logo = estado.estructura?.logo)
            val m = Modifier.padding(horizontal = 16.dp)
            Box(m) { persona() }
            Box(m.semantics { contentDescription = "Apariencia" }) {
                Segmentado(listOf("sistema" to "Automático", "claro" to "Claro", "oscuro" to "Oscuro"), tema, { estado.ponerTema(it) })
            }
            Box(m) { Buscador("Buscar una sección", filtro, { filtro = it }) }
            if (filtro.isEmpty()) {
                Box(m) { AvisoPlan() }
                val a = estado.estructura?.alta
                if (a != null && estado.esDuenio && a.completo < 100) Box(m) { completar(a) }
                if (potenciar.isNotEmpty()) potencia()
                partes.forEach { p ->
                    Column(m, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Texto(p.titulo, Estilo.tituloSeccion, C.texto, Modifier.padding(horizontal = 4.dp).semantics { heading() })
                        p.grupos.forEach { g ->
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (g.titulo.isNotEmpty()) TituloBloque(g.titulo)
                                lista(g.items)
                            }
                        }
                    }
                }
                if (estado.estructura?.escondido == true || todas) {
                    // Toggle(isOn:) { etiqueta }: cambia el interruptor (tocar el texto no hace nada, como en iOS).
                    Row(
                        m.fillMaxWidth().tarjeta().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Texto("Mostrar todas las secciones", Estilo.filaLista, C.texto)
                            Texto(Formato.sinViudas("Escondemos lo que no usás según tus rubros. Los cambiás en Datos de la inmobiliaria."), Estilo.footnote, C.texto2)
                        }
                        Llave(todas, { v ->
                            todas = v
                            Preferencias.ponerBool("todasLasSecciones", v)
                            alcance.launch { estado.cargarEstructura() }
                        }, color = C.accion, descripcion = "Mostrar todas las secciones")
                    }
                }
                web?.let { u -> Box(m) { verLaWeb(u) } }
                Box(
                    m.fillMaxWidth().heightIn(min = 44.dp).clickable {
                        Consejos.reiniciar()
                        estado.pestana = Pestana.inicio
                        estado.mostrarRecorrido = true
                    },
                    contentAlignment = Alignment.Center,
                ) { ConIcono("Ver el recorrido de nuevo", "play.circle", Estilo.subheadFuerte, C.accion) }
                Box(m) { pie() }
            } else if (encontradas.isEmpty()) {
                Box(m) {
                    EstadoVacio(
                        icono = "magnifyingglass", titulo = "No hay una sección «$filtro»", texto = "Probá con otra palabra, o buscá clientes y propiedades.",
                        accion = "Buscar «$filtro» en todo" to { estado.abrir(ABuscar(texto = filtro)) },
                    )
                }
            } else {
                Box(m) { lista(encontradas) }
            }
        }
    }

    Hoja(cambiando, { cambiando = false }, alturas = listOf(Altura.media, Altura.grande)) { TusInmobiliarias { cambiando = false } }
}

/** Lo estático de MasRaiz (las funciones `static` de iOS). */
object MasRaiz {
    fun normal(t: String): String =
        Normalizer.normalize(t.trim(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").lowercase()

    /** «Cobrar» encuentra Cobranzas, «expensas» Consorcios, «logo» Mi web: el nombre y sus palabras. */
    fun coincide(it: Estructura.Item, f: String): Boolean =
        normal(it.nombre).contains(f) || it.palabras.any { normal(it).contains(f) || f.contains(normal(it)) }

    /** Todas las secciones que se ven (para el buscador del Inicio): las de la estructura, o la guardada, o las de
     *  siempre; sólo las que la app sabe abrir. */
    fun secciones(e: Estructura?, duenio: Boolean): List<Estructura.Item> {
        val partes = (e ?: Guardado.leer<Estructura>("estructura")?.first)?.partes ?: partesFijas(duenio)
        return partes.flatMap { p -> p.grupos.flatMap { it.items } }.filter { Lugares.secciones[it.clave] != null }
    }

    /** Sin la API todavía (la primera vez sin señal): las mismas dos partes, sin esconder nada. */
    fun partesFijas(duenio: Boolean): List<Estructura.Parte> {
        fun it(c: String, n: String) = Estructura.Item(clave = c, nombre = n, palabras = emptyList())
        val mia = mutableListOf(it("web", "Mi web"), it("configuracion", "Datos de la inmobiliaria"), it("equipo", "Equipo y permisos"), it("plan", "Tu plan"))
        if (duenio) mia += listOf(it("integraciones", "Integraciones"), it("ia", "Corredor en tu IA"))
        mia.add(it("papelera", "Papelera"))
        return listOf(
            Estructura.Parte(clave = "inmobiliaria", titulo = "Mi inmobiliaria", grupos = listOf(Estructura.Grupo(titulo = "", items = mia))),
            Estructura.Parte(clave = "herramientas", titulo = "Herramientas", grupos = listOf(
                Estructura.Grupo(titulo = "Día a día", items = listOf(it("agenda", "Agenda"), it("avisos", "Avisos"), it("buscar", "Buscar en todo"))),
                Estructura.Grupo(titulo = "Cartera", items = listOf(it("contactos", "Contactos"), it("operaciones", "Operaciones"), it("reservas", "Reservas"),
                    it("emprendimientos", "Emprendimientos"), it("temporada", "Temporada"))),
                Estructura.Grupo(titulo = "Alquileres", items = listOf(it("cobranzas", "Cobranzas"), it("contratos", "Contratos"), it("rendiciones", "Rendiciones"),
                    it("inventarios", "Inventarios"), it("indices", "Índices"), it("consorcios", "Consorcios"),
                    it("proveedores", "Proveedores"))),
                Estructura.Grupo(titulo = "Difusión", items = listOf(it("difusion", "Portales"), it("respuestas", "Respuestas guardadas"), it("importar", "Importar la cartera"))),
                Estructura.Grupo(titulo = "Equipo", items = listOf(it("objetivos", "Objetivos"), it("informe", "Informe"), it("revision", "Revisión de la IA"), it("actividad", "Actividad"))),
            )),
        )
    }

    /** Cuántos elementos trae una respuesta: una lista, o un objeto con su lista adentro. */
    fun cuenta(j: JsonElement): Int = when (j) {
        is JsonArray -> j.size
        is JsonObject -> {
            var n: Int? = null
            for (k in listOf("items", "elementos", "filas", "lista", "datos", "resultados")) {
                val l = j[k]
                if (l is JsonArray) { n = l.size; break }
            }
            n ?: j.values.firstNotNullOfOrNull { (it as? JsonArray)?.size } ?: 0
        }
        else -> 0
    }
}

/** GET /v1/web/borrador: la marca con los cambios sin publicar (la misma que edita el panel). */
@Serializable
data class Borrador(
    val marca: Map<String, JsonElement> = emptyMap(), // [String: JSONLibre] de iOS
    val hayBorrador: Boolean = false,
    val en: String? = null,
)

/** «Tus inmobiliarias»: la actual con el tilde, las otras para pasar con un toque («Estás en …») y abajo aceptar una
 *  invitación o crear otra (lib/ui/shell/CambioInmobiliaria.svelte). */
@Composable
fun TusInmobiliarias(cerrar: () -> Unit = LocalCerrarHoja.current) {
    val estado = LocalEstado.current
    val alcance = rememberCoroutineScope()
    var enviando by remember { mutableStateOf(false) }
    val host = android.net.Uri.parse(Api.web().toString()).host ?: "corredor.com.ar"

    fun ir(d: Any) {
        cerrar()
        estado.abrir(d)
    }

    fun elegir(m: Sesion.Membresia, esta: Boolean) {
        if (esta) return cerrar()
        enviando = true
        alcance.launch {
            try {
                estado.elegir(m)
                cerrar()
                estado.avisar("Estás en ${m.nombre}")
            } catch (e: CancellationException) { throw e } catch (e: Exception) { estado.avisar(e) } finally { enviando = false }
        }
    }

    Column(Modifier.fillMaxWidth()) {
        BarraHoja("Tus inmobiliarias", izquierda = { BotonHoja("Cerrar") { cerrar() } })
        ListaAgrupada {
            SeccionLista {
                val lista = estado.sesion?.inmobiliarias ?: emptyList()
                lista.forEachIndexed { i, m ->
                    if (i > 0) Separador(66.dp)
                    val esta = m.id == estado.sesion?.inmobiliariaId
                    FilaLista({ elegir(m, esta) }, habilitado = !enviando, modifier = Modifier.semantics { selected = esta }) {
                        Box(Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(if (esta) C.verde700 else C.hundida), contentAlignment = Alignment.Center) {
                            Texto(m.nombre.take(1).uppercase(), Estilo(17f, Peso.bold), if (esta) C.oro else C.texto)
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Texto(m.nombre, Estilo.filaLista, C.texto)
                            Texto("${ElegirInmobiliaria.rol(m.rol)} · ${m.slug}.$host", Estilo.footnote, C.texto2, lineas = 1)
                        }
                        if (esta) Icono("checkmark.circle.fill", 17f, C.accion)
                    }
                }
            }
            SeccionLista {
                FilaLista({ ir(ASeccion(clave = "invitaciones")) }) { ConIcono("Aceptar una invitación", "envelope") }
                Separador(52.dp)
                FilaLista({ ir(ASeccion(clave = "empezar")) }) { ConIcono("Crear otra inmobiliaria", "plus.square.on.square") }
            }
        }
    }
}
