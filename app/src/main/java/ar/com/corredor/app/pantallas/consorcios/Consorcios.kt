package ar.com.corredor.app.pantallas.consorcios

import ar.com.corredor.app.app.PantallaApilada
import ar.com.corredor.app.nucleo.copiar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import ar.com.corredor.app.app.BarraDe
import ar.com.corredor.app.app.BotonBarraIcono
import ar.com.corredor.app.app.LocalEstado
import ar.com.corredor.app.nucleo.ASeccion
import ar.com.corredor.app.nucleo.ArchivoPrevio
import ar.com.corredor.app.nucleo.Documentos
import ar.com.corredor.app.nucleo.ErrorApi
import ar.com.corredor.app.nucleo.VistaPrevia
import ar.com.corredor.app.nucleo.Formato
import ar.com.corredor.app.nucleo.errorDe
import ar.com.corredor.app.nucleo.opcional
import ar.com.corredor.app.nucleo.subir
import ar.com.corredor.app.pantallas.inicio.Cifra
import ar.com.corredor.app.tema.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.ceil

// Clon de Pantallas/Consorcios/Consorcios.swift.
// Consorcios: no los dibuja el handoff (decía «quedan en la web»), pero Nahuel los quiere en el
// celular. Se arman con las piezas del handoff: tarjetas de cifras, filas, chips y hojas, con las
// funciones y los textos del panel web ((app)/consorcios).

// MARK: - Lista

private val vistasConsorcios = listOf("morosos" to "Con morosos", "liquidar" to "Para liquidar", "reclamos" to "Con reclamos", "aldia" to "Al día")
private val enTextoConsorcios = mapOf("morosos" to "con morosos", "liquidar" to "para liquidar", "reclamos" to "con reclamos", "aldia" to "al día")

/** Un renglón de «Lo que hay que hacer». */
private data class ItemHacer(val id: String, val icono: String, val tono: Tono, val titulo: String, val detalle: String, val monto: Double? = null, val destino: Any)

/** Los edificios con lo que tiene cada uno (morosos, liquidar el mes, reclamos) y, abajo, «Lo que hay que hacer» en
 *  todos (como /consorcios en el celular). */
@Composable
fun Consorcios() {
    val estado = LocalEstado.current
    val alcance = rememberCoroutineScope()
    var lista by remember { mutableStateOf<List<ConsorcioAPI>?>(null) }
    var alertas by remember { mutableStateOf<List<AlertaConsorcio>>(emptyList()) }
    /** El período que toca liquidar en cada edificio (y si ya hay un borrador cargándose). */
    var liquidar by remember { mutableStateOf<Map<String, Pair<String, Boolean>>>(emptyMap()) }
    var vista by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<ErrorApi?>(null) }
    var nuevo by remember { mutableStateOf(false) }

    suspend fun cargar() {
        try {
            val l = todasConsorcio<ConsorcioAPI>(estado.api, "/v1/consorcios") { it.id }
            val a = estado.api.opcional<List<AlertaConsorcio>>("/v1/consorcios/alertas", listOf("limite" to "100")) ?: emptyList()
            // Los períodos de cada edificio, en paralelo (como la web): para saber qué toca liquidar.
            val api = estado.api
            val periodos: Map<String, List<PeriodoAPI>> = coroutineScope {
                l.map { c -> async { c.id to (api.opcional<List<PeriodoAPI>>("/v1/consorcios/${c.id}/periodos", listOf("limite" to "3")) ?: emptyList()) } }.awaitAll().toMap()
            }
            val liq = mutableMapOf<String, Pair<String, Boolean>>()
            for (c in l) Cons.liquidarEnLista(periodos[c.id] ?: emptyList())?.let { liq[c.id] = it }
            liquidar = liq
            alertas = a
            lista = l
            error = null
        } catch (e: CancellationException) { throw e } catch (e: Exception) {
            if (e is ErrorApi && lista == null) error = e
        }
    }

    fun enVista(c: ConsorcioAPI, v: String?): Boolean {
        val m = c.metricas
        return when (v) {
            "morosos" -> (m?.deudores ?: 0) > 0
            "liquidar" -> liquidar[c.id] != null
            "reclamos" -> (m?.trabajosPendientes ?: 0) > 0
            "aldia" -> (m?.deudores ?: 0) == 0 && liquidar[c.id] == null && (m?.trabajosPendientes ?: 0) == 0
            else -> true
        }
    }

    fun items(lista: List<ConsorcioAPI>): List<ItemHacer> {
        val out = mutableListOf<ItemHacer>()
        for (c in lista) {
            val l = liquidar[c.id] ?: continue
            out.add(ItemHacer("l" + c.id, "hammer", Tono.oro, "Liquidar ${Cons.nombreMes(l.first)}",
                "${c.nombreCorto} · ${if (l.second) "los gastos ya están cargándose" else "todavía sin gastos"}",
                destino = ALiquidarConsorcio(consorcio = c.id)))
        }
        alertas.forEachIndexed { i, a ->
            val (icono, tono, pestana, pedido) = when (a.tipo) {
                "morosidad" -> Cuatro("dollarsign.circle", Tono.rojo, "unidades", "deben")
                "trabajo" -> Cuatro("wrench.and.screwdriver", Tono.azul, "trabajos", null)
                "novedad" -> Cuatro("book", Tono.neutro, "novedades", null)
                else -> Cuatro("wrench.adjustable", Tono.oro, "resumen", null)
            }
            val corto = a.consorcio.replace(Regex("^consorcio\\s+", RegexOption.IGNORE_CASE), "")
            out.add(ItemHacer("a$i", icono, tono, Formato.mayuscula(a.texto), corto + (a.dias?.let { " · service en $it días" } ?: ""), a.monto,
                AFichaConsorcioEn(id = a.consorcioId, pestana = pestana, pedido = pedido)))
        }
        return out
    }

    LaunchedEffect(estado.version) { cargar() }

    PantallaApilada("Consorcios", grande = true, acciones = if (estado.escribe) ({ BotonBarraIcono("plus", "Nuevo consorcio") { nuevo = true } }) else null) {
        Refrescable({ cargar() }, Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val l = lista
                val e = error
                if (l != null) {
                    if (l.isEmpty()) {
                        Box(Modifier.padding(top = 40.dp)) {
                            EstadoVacio("building.2", "Todavía no administrás edificios",
                                "Cargá el consorcio con sus unidades y coeficientes. Después, cada mes, liquidás las expensas y le llega a cada propietario su link.",
                                accion = if (estado.escribe) ("Cargar el primer consorcio" to { nuevo = true }) else null)
                        }
                    } else {
                        val unidades = l.sumOf { it.metricas?.unidades ?: 0 }
                        Texto("${Cons.plural(l.size, "edificio", "edificios")} · $unidades u.", Estilo.footnote, C.texto2)
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ChipFiltro("Todos ${l.size}", vista == null) { vista = null }
                            vistasConsorcios.forEach { (k, t) ->
                                ChipFiltro("$t ${l.count { enVista(it, k) }}", vista == k) { vista = if (vista == k) null else k }
                            }
                        }
                        vista?.let { v ->
                            val texto2 = C.texto2; val accion = C.accion
                            TextoRico(buildAnnotatedString {
                                withStyle(SpanStyle(color = texto2)) { append("Edificios ${enTextoConsorcios[v] ?: ""} · ") }
                                withStyle(SpanStyle(color = accion, fontWeight = FontWeight.SemiBold)) { append("ver todos") }
                            }, Estilo.footnote, modifier = Modifier.clickable { vista = null })
                        }
                        val filtrados = l.filter { enVista(it, vista) }
                        filtrados.forEach { c -> key(c.id) { TarjetaConsorcio(c, liquidar[c.id]?.first) } }
                        if (filtrados.isEmpty()) Texto("Ningún edificio ${enTextoConsorcios[vista ?: ""] ?: ""} por ahora.", Estilo.footnote, C.texto2)
                        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Texto("Lo que hay que hacer", Estilo.titulo3, C.texto, Modifier.weight(1f))
                            if (estado.escribe) Texto("Nuevo consorcio", Estilo.subheadFuerte, C.accion, Modifier.clickable { nuevo = true })
                        }
                        val hs = items(l)
                        if (hs.isEmpty()) {
                            Row(Modifier.fillMaxWidth().tarjeta().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icono("checkmark.circle", 14f, C.okTinta)
                                Texto("Todo al día en tus edificios: sin morosos, sin reclamos abiertos y nada para liquidar.", Estilo.texto14, C.okTinta)
                            }
                        } else {
                            Column(Modifier.fillMaxWidth().tarjeta()) {
                                hs.forEachIndexed { i, h ->
                                    key(h.id) {
                                        Tocable({ estado.abrir(h.destino) }) {
                                            RenglonConsorcio(primero = i == 0) {
                                                IconoTono(h.icono, h.tono)
                                                DosLineasConsorcio(h.titulo, h.detalle)
                                                h.monto?.let { m -> if (m > 0) Texto(Formato.plata(m), Estilo.footnoteFuerte, C.texto) }
                                                Chevron()
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else if (e != null) {
                    EstadoError("los consorcios", e) { alcance.launch { cargar() } }
                } else EsqueletoFilas(filas = 4, miniatura = 56.dp)
            }
        }
    }
    Hoja(nuevo, { nuevo = false }) { EditarConsorcio(c = null) }
}

private data class Cuatro(val icono: String, val tono: Tono, val pestana: String, val pedido: String?)

@Composable
private fun TarjetaConsorcio(c: ConsorcioAPI, liquidar: String?) {
    val estado = LocalEstado.current
    Tocable({ estado.abrir(ASeccion(clave = "consorcio", id = c.id)) }) {
        Column(Modifier.fillMaxWidth().tarjeta()) {
            Box(Modifier.fillMaxWidth().height(96.dp), contentAlignment = Alignment.Center) {
                if (c.fotoUrl != null) Box(Modifier.matchParentSize()) { Foto(c.fotoUrl) } else {
                    Box(Modifier.matchParentSize().background(C.hundida))
                    Icono("building.2", 28f, C.texto3)
                }
            }
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Texto(c.nombreCorto, Estilo(16f, Peso.bold), C.texto)
                val u = c.metricas?.unidades ?: 0
                Texto("${c.barrio?.let { "$it · " } ?: ""}${if (u == 1) "1 unidad" else "$u unidades"}", Estilo.footnote, C.texto2)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Cons.chipsEdificio(c, liquidar).forEach { (t, tono) -> ChipConsorcio(t, tono) }
                }
            }
        }
    }
}

// MARK: - Alta y edición

@Serializable
private data class FotoCuerpo(val archivoId: String?)

@Serializable
private data class CuerpoConsorcio(
    val nombre: String, val direccion: String?, val cuit: String?, val cbu: String?, val diaVencimiento: Int?, val diaSegundoVencimiento: Int?,
    val interesMensual: Double, val modoExpensas: String, val fondoTipo: String, val fondoValor: Double, val notas: String?, val agenteId: String?,
)

/** Los datos del edificio (alta y edición): lo que sale en la liquidación y en el link de pago. En la edición, también
 *  la foto (la fachada de la tarjeta). */
@Composable
fun EditarConsorcio(c: ConsorcioAPI?) {
    val estado = LocalEstado.current
    val cerrar = LocalCerrarHoja.current
    val alcance = rememberCoroutineScope()
    var x by remember { mutableStateOf(c ?: ConsorcioAPI(id = "", nombre = "", diaVencimiento = 10, diaSegundoVencimiento = 20, interesMensual = 3.0)) }
    var guardando by remember { mutableStateOf(false) }
    var elegirFoto by remember { mutableStateOf(false) }
    var subiendoFoto by remember { mutableStateOf(false) }
    var fotoUrl by remember { mutableStateOf(c?.fotoUrl) }
    var fotoId by remember { mutableStateOf(c?.fotoId) }

    /** Lo que la API rechazaría, dicho antes (los días del 1 al 28; el 2.º después del 1.º). */
    val problema: String? = run {
        for (d in listOfNotNull(x.diaVencimiento, x.diaSegundoVencimiento)) if (d < 1 || d > 28) return@run "El día de vencimiento va del 1 al 28."
        val a = x.diaVencimiento; val b = x.diaSegundoVencimiento
        if (a != null && b != null && b <= a) return@run "El 2.º vencimiento va después del 1.º."
        val i = x.interesMensual
        if (i != null && (i < 0 || i > 100)) return@run "La tasa va de 0 a 100% mensual."
        null
    }

    suspend fun subirFoto(f: ArchivoElegidoConsorcio) {
        val c0 = c ?: return
        subiendoFoto = true
        try {
            val a = estado.api.subir(f.datos, f.nombre, f.tipo, "foto")
            val r: ConsorcioAPI = estado.api.enviar("PUT", "/v1/consorcios/${c0.id}/foto", FotoCuerpo(a.id))
            fotoUrl = r.fotoUrl
            fotoId = r.fotoId
            estado.version += 1
            estado.avisar("Foto del edificio guardada")
        } catch (e: CancellationException) { throw e } catch (e: Exception) { estado.avisar(e) }
        subiendoFoto = false
    }

    suspend fun sacarFoto() {
        val c0 = c ?: return
        val antes = fotoId
        try {
            estado.api.enviar<ConsorcioAPI, FotoCuerpo>("PUT", "/v1/consorcios/${c0.id}/foto", FotoCuerpo(null))
            fotoUrl = null
            fotoId = null
            estado.version += 1
            val api = estado.api
            estado.avisar("Sacaste la foto del edificio") {
                alcance.launch {
                    try {
                        api.enviar<ConsorcioAPI, FotoCuerpo>("PUT", "/v1/consorcios/${c0.id}/foto", FotoCuerpo(antes))
                        estado.version += 1
                    } catch (e: CancellationException) { throw e } catch (e: Exception) { estado.avisar(e) }
                }
            }
        } catch (e: CancellationException) { throw e } catch (e: Exception) { estado.avisar(e) }
    }

    suspend fun guardar() {
        guardando = true
        // El PUT reemplaza todo: se parte del consorcio entero y se cambia sólo lo editado (datos.ts).
        val cbu = x.cbu.noVacio()?.replace(Regex("\\s+(?=\\d)"), "")
        val b = CuerpoConsorcio(nombre = x.nombre.trim(), direccion = x.direccion.noVacio(), cuit = x.cuit.noVacio(), cbu = cbu,
            diaVencimiento = x.diaVencimiento, diaSegundoVencimiento = x.diaSegundoVencimiento, interesMensual = Cons.redondo(x.interesMensual ?: 0.0),
            modoExpensas = c?.modoExpensas ?: "prorrateo", fondoTipo = c?.fondoTipo ?: "ninguno", fondoValor = Cons.redondo(c?.fondoValor ?: 0.0),
            notas = c?.notas, agenteId = c?.agenteId)
        try {
            if (c != null) {
                estado.api.hacer("PUT", "/v1/consorcios/${c.id}", b)
                estado.avisar("Datos guardados")
                estado.version += 1
                cerrar()
            } else {
                val n: ConsorcioAPI = estado.api.enviar("POST", "/v1/consorcios", b)
                estado.version += 1
                cerrar()
                // Al crear, a cargar las unidades (como `?nueva=1` de la web).
                estado.abrir(AFichaConsorcioEn(id = n.id, pestana = "unidades", pedido = "nueva"))
            }
        } catch (e: CancellationException) { throw e } catch (e: Exception) { estado.avisar(e) }
        guardando = false
    }

    MarcoHoja(
        titulo = if (c == null) "Nuevo consorcio" else "Datos del consorcio",
        confirmar = if (c == null) "Crear y cargar unidades" else "Guardar",
        confirmarHabilitado = !(x.nombre.trim().isEmpty() || problema != null || guardando),
        alConfirmar = { alcance.launch { guardar() } },
    ) {
        if (c != null) SeccionConsorcio {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(120.dp, 72.dp).clip(RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                    if (fotoUrl != null) Box(Modifier.matchParentSize()) { Foto(fotoUrl) } else {
                        Box(Modifier.matchParentSize().background(C.hundida))
                        Icono("building.2", 17f, C.texto3)
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Texto("Foto del edificio", Estilo.subheadFuerte, C.texto)
                    Texto("La fachada que se ve en la tarjeta del consorcio.", Estilo.footnote, C.texto2)
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (subiendoFoto) Rueda() else Texto(if (fotoUrl == null) "Subir foto" else "Cambiar", Estilo.subheadFuerte, C.accion, Modifier.clickable { elegirFoto = true })
                        if (fotoUrl != null) Texto("Sacar", Estilo.subheadFuerte, C.error, Modifier.clickable { alcance.launch { sacarFoto() } })
                    }
                }
            }
        }
        SeccionConsorcio {
            CampoConsorcio("Nombre") { CampoTextoConsorcio(x.nombre, { x = x.copy(nombre = it) }, "Consorcio Cabildo 2620") }
            CampoConsorcio("Dirección y barrio (opcional)") { CampoTextoConsorcio(x.direccion ?: "", { x = x.copy(direccion = it) }, "Av. Cabildo 2620, Belgrano") }
            CampoConsorcio("CUIT del consorcio (opcional)") { CampoTextoConsorcio(x.cuit ?: "", { x = x.copy(cuit = it) }, "30-55912873-9", KeyboardType.Phone) }
            CampoConsorcio("CBU o alias para transferir (opcional)", ayuda = "Sale en el link de cada unidad, con el botón para copiarlo.") {
                CampoTextoConsorcio(x.cbu ?: "", { x = x.copy(cbu = it) }, "", KeyboardType.Ascii, sinCorrector = true)
            }
        }
        SeccionConsorcio {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CampoConsorcio("Vence el día", modifier = Modifier.weight(1f)) {
                    CampoNumeroConsorcio(x.diaVencimiento?.toDouble(), { x = x.copy(diaVencimiento = it?.toInt()) }, "10", decimal = false)
                }
                CampoConsorcio("2.º vencimiento", modifier = Modifier.weight(1f)) {
                    CampoNumeroConsorcio(x.diaSegundoVencimiento?.toDouble(), { x = x.copy(diaSegundoVencimiento = it?.toInt()) }, "20", decimal = false)
                }
            }
            CampoConsorcio("Interés por mora (% mensual)", ayuda = "Se suma a la deuda y es el recargo del 2.º vencimiento.") {
                CampoNumeroConsorcio(x.interesMensual, { x = x.copy(interesMensual = it) }, "3")
            }
            problema?.let { Texto(it, Estilo.footnote, C.error) }
        }
    }
    ElegirArchivoConsorcio(elegirFoto, { elegirFoto = false }, tiposArchivo = listOf("image/*")) { a -> a.firstOrNull()?.let { f -> alcance.launch { subirFoto(f) } } }
}

// MARK: - Ficha

/** Un edificio: la cabecera (CUIT, encargado y «Liquidar <mes>») y sus pestañas, como la web: Resumen, Unidades,
 *  Expensas, Trabajos, Proveedores, Personal y Libro de novedades. */
@Composable
fun FichaConsorcio(id: String, pestanaInicial: String = "resumen", pedidoInicial: String? = null) {
    val estado = LocalEstado.current
    val alcance = rememberCoroutineScope()
    var c by remember { mutableStateOf<ConsorcioAPI?>(null) }
    var empleados by remember { mutableStateOf<List<EmpleadoAPI>>(emptyList()) }
    var periodos by remember { mutableStateOf<List<PeriodoAPI>>(emptyList()) }
    var error by remember { mutableStateOf<ErrorApi?>(null) }
    var pestana by remember { mutableStateOf(pestanaInicial) }
    var pedido by remember { mutableStateOf(pedidoInicial) }
    var editando by remember { mutableStateOf(false) }

    suspend fun cargar() {
        val b = "/v1/consorcios/$id"
        try {
            val x: ConsorcioAPI = estado.api.get(b)
            empleados = estado.api.opcional<List<EmpleadoAPI>>("$b/empleados") ?: emptyList()
            periodos = (estado.api.opcional<List<PeriodoAPI>>("$b/periodos", listOf("limite" to "24")) ?: emptyList()).sortedByDescending { it.periodo }
            c = x
            error = null
        } catch (e: CancellationException) { throw e } catch (e: Exception) {
            if (e is ErrorApi && c == null) error = e
        }
    }

    /** Cambia de pestaña y le pasa lo que tiene que abrir (desde el Resumen). */
    fun ir(p: String, q: String?) {
        pedido = q
        pestana = p
    }

    LaunchedEffect(estado.version) { cargar() }

    val titulo = c?.nombre ?: "Consorcio"
    PantallaApilada(titulo, acciones = {
        MenuBoton(
            buildList {
                if (estado.escribe) add(OpcionDeMenu("Editar los datos del consorcio", "pencil") { editando = true })
                add(OpcionDeMenu("Bienes del edificio", "fire.extinguisher") { estado.abrir(ASeccion(clave = "bienes", id = id)) })
                add(OpcionDeMenu("Reglamento", "doc.text") { estado.abrir(ASeccion(clave = "reglamento", id = id)) })
            },
            "Más acciones",
        ) { Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) { Icono("ellipsis.circle", 19f, C.accion) } }
    }) {
        Refrescable({ cargar() }, Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                val x = c
                val e = error
                if (x != null) {
                    // Cabecera
                    val enc = Cons.encargado(empleados)
                    val rol = if ((enc?.categoria ?: "").contains("encargad", ignoreCase = true)) "encargado" else "personal"
                    val sub = listOfNotNull(x.cuit?.let { "CUIT $it" }, enc?.let { "$rol ${it.nombre}" }).joinToString(" · ")
                    val toca = Cons.aLiquidar(periodos).first
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (sub.isNotEmpty()) Texto(sub, Estilo.footnote, C.texto2)
                        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(Modifier.weight(1f)) { Cifra("Unidades", "${x.metricas?.unidades ?: 0}") }
                            Box(Modifier.weight(1f)) { Cifra("Deuda", Formato.plataCorta(x.metricas?.saldoAdeudado ?: 0.0)) }
                            Box(Modifier.weight(1f)) { Cifra("Reclamos", "${x.metricas?.trabajosPendientes ?: 0}") }
                        }
                        if (estado.escribe) BotonAccion("Liquidar ${Cons.nombreMes(toca)}", { estado.abrir(ALiquidarConsorcio(consorcio = x.id)) })
                    }
                    // Pestañas
                    val u = x.metricas?.unidades ?: 0
                    val t = x.metricas?.trabajosPendientes ?: 0
                    val l = listOf("resumen" to "Resumen", "unidades" to (if (u > 0) "Unidades $u" else "Unidades"), "expensas" to "Expensas",
                        "trabajos" to (if (t > 0) "Trabajos $t" else "Trabajos"), "proveedores" to "Proveedores", "personal" to "Personal",
                        "novedades" to "Libro de novedades")
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        l.forEach { (k, n) -> ChipFiltro(n, pestana == k) { pestana = k } }
                    }
                    // Contenido
                    when (pestana) {
                        "unidades" -> PestanaUnidadesConsorcio(x, pedido) { pedido = it }
                        "expensas" -> PestanaExpensasConsorcio(x, periodos)
                        "trabajos" -> PestanaTrabajosConsorcio(x, pedido) { pedido = it }
                        "proveedores" -> PestanaProveedoresConsorcio(x, periodos)
                        "personal" -> PestanaPersonalConsorcio(x, empleados, pedido) { pedido = it }
                        "novedades" -> PestanaNovedadesConsorcio(x, pedido) { pedido = it }
                        else -> ResumenConsorcio(x, empleados, periodos, ::ir)
                    }
                } else if (e != null) {
                    EstadoError("el consorcio", e) { alcance.launch { cargar() } }
                } else EsqueletoFilas(filas = 5)
            }
        }
    }
    Hoja(editando, { editando = false }) { EditarConsorcio(c) }
}

// MARK: - Resumen

/** Resumen del edificio: morosos (con «Intimar»), trabajos y reclamos abiertos, el personal con su recibo del mes y el
 *  F931, y lo último del libro de novedades. */
@Composable
fun ResumenConsorcio(c: ConsorcioAPI, empleados: List<EmpleadoAPI>, periodos: List<PeriodoAPI>, ir: (String, String?) -> Unit) {
    val estado = LocalEstado.current
    val ctx = LocalContext.current
    val alcance = rememberCoroutineScope()
    var morosos by remember { mutableStateOf<List<MorosoConsorcio>>(emptyList()) }
    var parcial by remember { mutableStateOf<Set<String>>(emptySet()) }
    var dias by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var trabajos by remember { mutableStateOf<List<TrabajoAPI>>(emptyList()) }
    var novedades by remember { mutableStateOf<List<NovedadAPI>>(emptyList()) }
    var recibos by remember { mutableStateOf<List<ReciboSueldoAPI>>(emptyList()) }
    var cargado by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<ErrorApi?>(null) }
    var intimar by remember { mutableStateOf(false) }
    var previo by remember { mutableStateOf<ArchivoPrevio?>(null) }

    suspend fun cargar() {
        val b = "/v1/consorcios/${c.id}"
        try {
            val d: List<DeudorAPI> = estado.api.get("$b/deudores")
            trabajos = estado.api.opcional<List<TrabajoAPI>>("$b/trabajos", listOf("estado" to "pendiente,aprobado,en_curso", "limite" to "6")) ?: emptyList()
            novedades = estado.api.opcional<List<NovedadAPI>>("$b/novedades", listOf("limite" to "3")) ?: emptyList()
            recibos = estado.api.opcional<List<ReciboSueldoAPI>>("$b/sueldos", listOf("limite" to "12")) ?: emptyList()
            val unidades: List<UnidadAPI> = estado.api.opcional<List<UnidadAPI>>("$b/unidades") ?: emptyList()
            val ultimaDe = mutableMapOf<String, Double>()
            val u = periodos.firstOrNull { it.estado != "borrador" }
            if (u != null) {
                estado.api.opcional<PeriodoAPI>("$b/periodos/${u.id}")?.let { f -> for (l in f.liquidaciones ?: emptyList()) ultimaDe[l.unidadId] = l.total }
            }
            val tel = unidades.associate { it.id to it.propietarioTelefono }
            morosos = d.map { MorosoConsorcio(it.unidadId, it.unidad, it.propietario, tel[it.unidadId], it.total) }
            dias = d.associate { it.unidadId to (it.dias ?: 0) }
            parcial = d.filter { (it.dias ?: 0) <= 31 && (ultimaDe[it.unidadId] ?: 0.0) > it.saldo }.map { it.unidadId }.toSet()
            cargado = true
            error = null
        } catch (e: CancellationException) { throw e } catch (e: Exception) {
            if (e is ErrorApi) error = e
        }
    }

    fun meses(m: MorosoConsorcio): String {
        if (m.unidadId in parcial) return "parcial"
        val n = maxOf(1, ceil((dias[m.unidadId] ?: 0).toDouble() / 30.5).toInt())
        return if (n == 1) "1 mes" else "$n meses"
    }

    fun cuando(f: String): String {
        val a = runCatching { LocalDate.parse(Cons.hoy) }.getOrNull() ?: return ""
        val b = runCatching { LocalDate.parse(f.take(10)) }.getOrNull() ?: return ""
        val n = ChronoUnit.DAYS.between(a, b).toInt()
        return when (n) {
            0 -> "hoy"
            1 -> "mañana"
            -1 -> "ayer"
            else -> if (n > 1) "el ${Cons.diaBarraMes(f)}" else "desde el ${Cons.diaBarraMes(f)}"
        }
    }

    fun detalle(t: TrabajoAPI): String {
        val d = Cons.diasDesde(t.creadoEn)
        val p = mutableListOf<String>()
        t.unidad?.let { p.add("reclamo de $it") }
        t.proveedor?.let { pr -> p.add(if (t.venceEl != null && t.estado != "pendiente") "$pr va ${cuando(t.venceEl)}" else pr) }
        if (t.estado == "pendiente") { p.add("presupuesto pendiente"); p.add(if (d == 1) "1 día" else "$d días") }
        return p.joinToString(" · ")
    }

    suspend fun f931(periodo: String?) {
        try {
            val ruta = "/v1/consorcios/${c.id}/sueldos/f931" + (periodo?.let { "?periodo=$it" } ?: "")
            val f = Documentos.archivo(ruta, periodo?.let { "sueldos-$it.csv" } ?: "sueldos-todos.csv", estado.api)
            previo = ArchivoPrevio(f, "text/csv")
        } catch (e: CancellationException) { throw e } catch (e: Exception) { estado.avisar(e) }
    }

    LaunchedEffect(estado.version) { cargar() }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        val e = error
        if (e != null && !cargado) {
            EstadoError("el resumen", e) { alcance.launch { cargar() } }
        } else if (!cargado) {
            EsqueletoFilas(filas = 4)
        } else {
            // Morosos
            val total = morosos.sumOf { it.total }
            BloqueConsorcio(
                if (morosos.isEmpty()) "Morosos" else "Morosos · ${Formato.plata(total)}", tono = if (morosos.isEmpty()) null else Tono.rojo,
                accion = if (morosos.isEmpty()) null else ((if (morosos.size == 1) "Intimar" else "Intimar a los ${morosos.size}") to { intimar = true }),
            ) {
                if (morosos.isEmpty()) VacioConsorcio("Nadie debe expensas. Cuando una unidad se atrase, aparece acá con lo que debe y el interés.")
                morosos.forEachIndexed { i, m ->
                    key(m.unidadId) {
                        Tocable({ ir("unidades", "unidad:${m.unidadId}") }) {
                            RenglonConsorcio(primero = i == 0) {
                                Texto(m.unidad, Estilo.filaLista, C.texto, Modifier.widthIn(min = 44.dp))
                                Texto("${m.propietario ?: "Sin propietario"} · ${meses(m)}", Estilo.footnote, C.texto2, Modifier.weight(1f))
                                Texto(Cons.cifra(m.total), Estilo.footnoteFuerte, C.errorTinta)
                            }
                        }
                    }
                }
            }
            // Trabajos
            BloqueConsorcio("Trabajos y reclamos", accion = if (estado.escribe) ("Nuevo" to { ir("trabajos", "nueva") }) else null) {
                if (trabajos.isEmpty()) VacioConsorcio("Sin trabajos abiertos. Cargá un reclamo con fotos desde el edificio y seguilo hasta cerrarlo.")
                trabajos.forEachIndexed { i, t ->
                    key(t.id) {
                        val ch = Cons.chipTrabajo(t)
                        Tocable({ estado.abrir(ATrabajoConsorcio(consorcio = c.id, id = t.id)) }) {
                            RenglonConsorcio(primero = i == 0) {
                                IconoTono(Cons.iconoTrabajo(t), if (t.prioridad == "alta") Tono.rojo else Tono.azul)
                                DosLineasConsorcio(t.titulo, detalle(t))
                                ChipConsorcio(ch.first, ch.second)
                            }
                        }
                    }
                }
            }
            // Personal
            val ultimo = recibos.firstOrNull()?.periodo
            val recibo: (String) -> ReciboSueldoAPI? = { emp -> recibos.firstOrNull { it.empleadoId == emp && it.periodo == ultimo } }
            BloqueConsorcio(ultimo?.let { "Personal · recibos de ${Cons.nombreMes(it)}" } ?: "Personal", accion = if (ultimo == null) ("Ver" to { ir("personal", null) }) else null) {
                if (empleados.isEmpty()) VacioConsorcio("Sin personal cargado. Sumá al encargado y Corredor arma cada mes su recibo y el archivo para el F931.")
                empleados.forEachIndexed { i, emp ->
                    key(emp.id) {
                        Tocable({ ir("personal", null) }) {
                            RenglonConsorcio(primero = i == 0) {
                                AvatarIniciales(emp.nombre, tam = 36.dp)
                                DosLineasConsorcio(
                                    emp.nombre + (emp.categoria?.let { " · " + it.lowercase().replace(" con vivienda", "").replace(" sin vivienda", "") } ?: ""),
                                    listOfNotNull(emp.notas, if ((emp.categoria ?: "").contains("con vivienda", ignoreCase = true)) "con vivienda" else null,
                                        Cons.antiguedad(emp.ingreso)?.let { "$it años" }).joinToString(" · "),
                                )
                                recibo(emp.id)?.bruto?.let { b -> Texto(Cons.cifra(b), Estilo.footnoteFuerte, C.texto) }
                            }
                        }
                    }
                }
                if (empleados.isNotEmpty()) {
                    RenglonConsorcio {
                        val r = recibo(empleados[0].id)
                        if (ultimo != null && r != null) {
                            BotonLabelConsorcio("Recibo de ${Cons.nombreMes(ultimo)}", "doc.text") { ir("personal", "recibo:${r.id}") }
                        } else if (estado.escribe) {
                            BotonLabelConsorcio("Liquidar sueldos", "doc.text") { ir("personal", "liquidar") }
                        }
                        Spacer(Modifier.weight(1f))
                        BotonLabelConsorcio("Exportar F931", "square.and.arrow.down") { alcance.launch { f931(ultimo) } }
                    }
                }
            }
            // Novedades
            BloqueConsorcio("Libro de novedades", accion = if (estado.escribe) ("Escribir" to { ir("novedades", "nueva") }) else null) {
                if (novedades.isEmpty()) VacioConsorcio("El libro está vacío. Anotá lo que pasa en el edificio: asambleas, arreglos, avisos a los vecinos.")
                novedades.forEachIndexed { i, n ->
                    key(n.id) {
                        Tocable({ ir("novedades", null) }) {
                            RenglonConsorcio(primero = i == 0) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Texto(listOfNotNull(n.ocurrioEl?.let { Cons.diaBarraMes(it) }, n.responsable).joinToString(" · "), Estilo.footnote, C.texto2)
                                    Texto(n.titulo, Estilo.texto14, C.texto)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    Hoja(intimar, { intimar = false }) { HojaIntimarMorosos(morosos, c.id, c.nombre) }
    VistaPrevia(previo) { previo = null }
}

// MARK: - Intimar

data class MorosoConsorcio(val unidadId: String, val unidad: String, val propietario: String?, val telefono: String?, val total: Double)

/** «Intimar a los morosos»: a cada uno, un WhatsApp con su saldo y el link de su cuenta corriente, que se arma al tocar.
 *  Sin celular cargado, el mensaje con el link se copia. Sale del teléfono de quien lo manda (wa.me). */
@Composable
fun HojaIntimarMorosos(morosos: List<MorosoConsorcio>, consorcioId: String, consorcio: String) {
    val estado = LocalEstado.current
    val ctx = LocalContext.current
    val cerrar = LocalCerrarHoja.current
    val alcance = rememberCoroutineScope()
    var links by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var armando by remember { mutableStateOf<String?>(null) }

    fun texto(m: MorosoConsorcio, url: String): String {
        val nombre = (m.propietario ?: "").split(" ").firstOrNull { it.isNotEmpty() } ?: ""
        val inmo = estado.inmobiliaria?.nombre ?: ""
        return "Hola${if (nombre.isEmpty()) "" else " $nombre"}, te escribimos${if (inmo.isEmpty()) "" else " de $inmo"} por las expensas de $consorcio, unidad ${m.unidad}. El saldo hoy es de ${Formato.plata(m.total)}, con el interés por mora. Acá tenés el detalle y cómo pagar: $url"
    }

    suspend fun armar(m: MorosoConsorcio) {
        armando = m.unidadId
        try {
            val ruta = "/v1/consorcios/$consorcioId/unidades/${m.unidadId}/link"
            val l: LinkConsorcio = estado.api.enviar("POST", ruta, vacioConsorcio)
            links = links + (m.unidadId to l.url)
            if (m.telefono.noVacio() == null) {
                copiar(ctx, texto(m, l.url))
                estado.avisar("Sin celular de ${m.propietario ?: m.unidad}: copiamos el mensaje con el link")
            }
        } catch (e: CancellationException) { throw e } catch (e: Exception) {
            estado.avisar(e)
        }
        armando = null
    }

    MarcoHoja("Intimar a los morosos", cancelar = null, confirmar = "Listo", alConfirmar = cerrar) {
        Texto("A cada uno, su saldo con interés y el link de su cuenta.", Estilo.texto14, C.texto2)
        Column(Modifier.fillMaxWidth().tarjeta()) {
            morosos.sortedWith { a, b -> Cons.ordenUnidad.compare(a.unidad, b.unidad) }.forEachIndexed { i, m ->
                key(m.unidadId) {
                    RenglonConsorcio(primero = i == 0) {
                        Texto(m.unidad, Estilo.filaLista, C.texto, Modifier.widthIn(min = 44.dp))
                        DosLineasConsorcio(m.propietario ?: "Sin propietario cargado", Formato.plata(m.total) + (if (m.telefono.noVacio() == null) " · sin celular" else ""))
                        val l = links[m.unidadId]
                        val t = m.telefono.noVacio()
                        if (l != null && t != null) {
                            BotonBordeadoConsorcio("WhatsApp", "bubble.left", lleno = Color(0xFF25D366)) { Afuera.whatsapp(t, texto(m, l)) }
                        } else {
                            BotonBordeadoConsorcio(
                                if (armando == m.unidadId) "Armando…" else if (t == null) "Copiar" else "Preparar",
                                if (armando == m.unidadId) null else if (t == null) "doc.on.doc" else "link",
                                habilitado = armando == null,
                            ) { alcance.launch { armar(m) } }
                        }
                    }
                }
            }
        }
    }
}
