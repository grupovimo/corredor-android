package ar.com.corredor.app.pantallas.cartera

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ar.com.corredor.app.app.*
import ar.com.corredor.app.nucleo.*
import ar.com.corredor.app.pantallas.agenda.*
import ar.com.corredor.app.pantallas.inicio.*
import ar.com.corredor.app.pantallas.mensajes.*
import ar.com.corredor.app.pantallas.propiedades.*
import ar.com.corredor.app.tema.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.Instant
import java.time.YearMonth

// Clon de Pantallas/Cartera/Operaciones.swift.

object Operaciones {
    data class Conflicto(val prop: String, val quien: String?, val hasta: String?, val mio: String, val id: String)
}

/** Si un instante cae en este mes (en Buenos Aires). */
private fun esteMesBA(f: Instant?): Boolean = f != null && YearMonth.from(Formato.diaDe(f)) == YearMonth.from(Formato.hoy())

/** 9A–9B Tablero de operaciones, como el del panel (18C): las cuatro columnas como pestañas con su
 *  cuenta y su total, el filtro por agente y por tipo, «N abiertas · USD X en juego», la nota de cada
 *  tarjeta, «Pasar a …» (o deslizar a la derecha), mantener apretado para «Mover a…», la seña al llegar
 *  a Reserva, «Se cayó» con su motivo, el aviso si otro la movió antes y las caídas con «Reabrir». */
@Composable
fun Operaciones() {
    val estado = LocalEstado.current
    val alcance = rememberCoroutineScope()
    var columna by remember { mutableIntStateOf(0) }
    var caidasVista by remember { mutableStateOf(false) }
    var agente by remember { mutableStateOf("") }
    var tipo by remember { mutableStateOf("") }
    var ops by remember { mutableStateOf<List<Operacion>?>(null) }
    var caidas by remember { mutableStateOf<List<Operacion>>(emptyList()) }
    var equipo by remember { mutableStateOf<List<Miembro>>(emptyList()) }
    var error by remember { mutableStateOf<ErrorApi?>(null) }
    var nueva by remember { mutableStateOf(false) }
    var cayendo by remember { mutableStateOf<Operacion?>(null) }
    var pideSena by remember { mutableStateOf<String?>(null) }
    var senaValor by remember { mutableStateOf("") }
    var conflicto by remember { mutableStateOf<Operaciones.Conflicto?>(null) }
    var sinPermiso by remember { mutableStateOf<Operacion?>(null) }
    var moviendo by remember { mutableStateOf<Operacion?>(null) }

    val puedeMover = estado.escribe
    val visibles: List<Operacion> = (ops ?: emptyList()).filter { agente.isEmpty() || (if (agente == "libre") it.agenteId == null else it.agenteId == agente) }
    val abiertas = visibles.filter { it.etapa != "cierre" }

    /** El tablero no trae las caídas: van aparte con ?caidas=si. */
    suspend fun cargar() {
        val q = mutableListOf<Pair<String, String>>()
        if (tipo.isNotEmpty()) q.add("operacion" to tipo)
        try {
            ops = estado.api.get("/v1/operaciones", q + listOf("limite" to "500"))
            error = null
        } catch (e: ErrorApi) {
            if (ops == null) error = e
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
        caidas = estado.api.opcional<List<Operacion>>("/v1/operaciones", listOf("caidas" to "si", "limite" to "100")) ?: caidas
        if (equipo.isEmpty()) equipo = estado.api.opcional<List<Miembro>>("/v1/equipo") ?: emptyList()
    }

    fun reemplazar(o: Operacion) {
        ops = ops?.map { if (it.id == o.id) o else it }
    }

    /** Mover a otra columna (con la etapa que vio la persona, para que la API sepa si otro la movió). */
    suspend fun mover(o: Operacion, col: Int, deshaciendo: Boolean = false) {
        if (!puedeMover) { sinPermiso = o; return }
        val destino = Operacion.columnas[col]
        val antes = o.etapa
        reemplazar(o.copy(etapa = destino.clave))
        Haptico.impacto()
        if (destino.clave == "reserva" && o.sena == null && !deshaciendo) { pideSena = o.id; senaValor = "" } else if (pideSena == o.id) pideSena = null
        @Serializable data class Cuerpo(val etapa: String, val desde: String)
        try {
            val r: Operacion = estado.api.enviar("POST", "/v1/operaciones/${o.id}/mover", Cuerpo(etapa = destino.clave, desde = antes))
            reemplazar(r)
            if (deshaciendo) { estado.avisar("Listo, volvió a su etapa"); return }
            if (destino.clave == "cierre" && o.propiedadOperacion == "alquiler") {
                estado.sugerir("${o.nombre} llegó al cierre", "Hacer contrato", AFichaOperacion(id = o.id))
            } else {
                estado.avisar("${o.nombre} pasó a ${destino.texto}") {
                    alcance.launch { mover(r, Operacion.columna(antes), deshaciendo = true) }
                }
            }
            estado.version += 1
        } catch (e: ErrorApi) {
            reemplazar(o)
            if (pideSena == o.id) pideSena = null
            if (e.codigo == "movida") {
                val h: List<EventoOperacion> = estado.api.opcional<List<EventoOperacion>>("/v1/operaciones/${o.id}/historial") ?: emptyList()
                val ultimo = h.sortedByDescending { it.creadoEn }.firstOrNull { it.tipo == "etapa" }
                val hasta = ultimo?.datos.texto("hasta")?.let { Operacion.textoColumna(it) }
                conflicto = Operaciones.Conflicto(prop = o.nombre, quien = ultimo?.autorNombre, hasta = hasta, mio = destino.texto, id = o.id)
                cargar()
            } else if (e.estado == 403) {
                sinPermiso = o
            } else {
                estado.avisarReintentar(if (e.detalle.isEmpty()) "No se movió" else e.detalle) { alcance.launch { mover(o, col) } }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            reemplazar(o)
        }
    }

    suspend fun guardarSena(o: Operacion) {
        pideSena = null
        val v = senaValor.filter { it.isDigit() }.toDoubleOrNull()
        if (v == null || v <= 0) return
        try {
            estado.api.hacer("PATCH", "/v1/operaciones/${o.id}", JsonObject(mapOf("sena" to JsonPrimitive(v), "senaMoneda" to JsonPrimitive(o.moneda ?: "USD"))))
            estado.avisar("Seña ${Formato.plata(v, o.moneda ?: "USD")} cargada")
            estado.version += 1
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            estado.avisar(e)
        }
    }

    suspend fun caer(o: Operacion, motivo: String) {
        @Serializable data class Cuerpo(val motivo: String)
        ops = ops?.filter { it.id != o.id }
        try {
            estado.api.hacer("POST", "/v1/operaciones/${o.id}/caer", Cuerpo(motivo = motivo))
            estado.avisar("${o.nombre} se cayó · ${motivo.lowercase()}") {
                alcance.launch {
                    try {
                        estado.api.hacer("POST", "/v1/operaciones/${o.id}/reabrir")
                        estado.version += 1
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        estado.avisar(e)
                    }
                }
            }
            estado.version += 1
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            estado.avisar(e)
            cargar()
        }
    }

    suspend fun reabrir(o: Operacion) {
        try {
            estado.api.hacer("POST", "/v1/operaciones/${o.id}/reabrir")
            estado.avisar("${o.nombre} volvió al tablero")
            caidasVista = false
            estado.version += 1
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            estado.avisar(e)
        }
    }

    LaunchedEffect(estado.version, tipo) { cargar() }

    fun menuItems(o: Operacion): List<OpcionDeMenu> = listOf(
        OpcionDeMenu("Abrir", "arrow.up.right") { estado.abrir(AFichaOperacion(id = o.id)) },
        // Un submenú en iOS («Mover … a…»): acá abre la lista de etapas.
        OpcionDeMenu("Mover ${o.nombre.split(",").firstOrNull() ?: ""} a…", "arrow.left.arrow.right") { moviendo = o },
        OpcionDeMenu("Se cayó", "heart.slash", destructiva = true) { cayendo = o },
    )

    // MARK: Piezas

    @Composable
    fun ChipAgente(id: String, nombre: String) {
        val on = agente == id
        Tocable(alTocar = { Haptico.seleccion(); agente = id }, modifier = Modifier.semantics { selected = on }) {
            Row(
                Modifier.height(36.dp).clip(CircleShape).background(if (on) C.accion else C.superficie).padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                if (id.isEmpty()) Texto("∗", Estilo.footnoteFuerte, if (on) C.sobreAccion else C.texto) else {
                    Box(Modifier.size(22.dp).clip(CircleShape).background(C.hundida), contentAlignment = Alignment.Center) {
                        Texto(Formato.iniciales(nombre), Estilo(10f, Peso.bold), C.texto)
                    }
                }
                Texto(if (id.isEmpty()) nombre else Charla.primerNombre(nombre), Estilo(14f, Peso.semibold), if (on) C.sobreAccion else C.texto)
            }
        }
    }

    @Composable
    fun CuerpoTarjeta(o: Operacion, nota: Pair<String, Boolean>) {
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(56.dp).clip(RoundedCornerShape(12.dp))) {
                Foto(url = urlFotoPropiedad(o.propiedadFotoId, estado.api.base.toString(), estado.sesion?.inmobiliariaId), modifier = Modifier.matchParentSize())
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Texto(o.nombre, Estilo(16f, Peso.bold), C.texto, lineas = 1)
                o.tipoTexto.nonEmpty?.let { Texto(it, Estilo(12f, Peso.semibold), C.texto3) }
                Texto(
                    listOfNotNull(o.contactoNombre, if ((o.valor ?: 0.0) > 0) Formato.plata(o.valor ?: 0.0, o.moneda ?: "USD") else null).joinToString(" · "),
                    Estilo.texto14, C.texto2, lineas = 1,
                )
                Texto(nota.first, Estilo(12f, if (nota.second) Peso.bold else Peso.regular), if (nota.second) C.avisoTinta else C.texto3)
            }
            val a = o.agenteNombre
            if (a != null) {
                Box(Modifier.size(28.dp).clip(CircleShape).background(C.hundida), contentAlignment = Alignment.Center) { Texto(Formato.iniciales(a), Estilo(11f, Peso.bold), C.texto) }
            } else {
                val linea2 = C.linea2
                Box(
                    Modifier.size(28.dp).semantics { contentDescription = "Sin agente" }.drawBehind {
                        drawCircle(linea2, radius = size.minDimension / 2 - 0.75.dp.toPx(), style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx()))))
                    },
                )
            }
        }
    }

    /** «¿Cuánto dejó de seña?» en la misma tarjeta, al pasar a Reserva. */
    @Composable
    fun PedirSena(o: Operacion) {
        Column(Modifier.fillMaxWidth().background(C.avisoFondo).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Texto("¿Cuánto dejó de seña?", Estilo.subheadFuerte, C.avisoTinta)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(12.dp)).background(C.superficie).padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically,
                ) {
                    Texto(o.moneda ?: "USD", Estilo.label, C.texto2)
                    val estilo = Estilo.body16
                    BasicTextField(
                        value = senaValor, onValueChange = { senaValor = it }, singleLine = true,
                        textStyle = estilo.ts().copy(color = C.texto), cursorBrush = SolidColor(C.accion),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f),
                        decorationBox = { campo -> Box { if (senaValor.isEmpty()) Texto(if (o.moneda == "ARS") "300.000" else "3.000", estilo, C.texto3); campo() } },
                    )
                }
                BotonTonal("Listo", { alcance.launch { guardarSena(o) } }, alto = 44.dp)
            }
            Texto("Después", Estilo.footnoteFuerte, C.texto2, Modifier.clickable { pideSena = null })
        }
    }

    @Composable
    fun Tarjeta(o: Operacion) {
        val col = Operacion.columna(o.etapa)
        val nota = o.nota()
        val derecha: List<AccionFila> = emptyList()
        val izquierda: List<AccionFila> = if (puedeMover && col < 3) listOf(AccionFila("Pasar", "arrow.right", C.verde700) { alcance.launch { mover(o, col + 1) } }) else emptyList()
        Column(Modifier.fillMaxWidth().tarjeta()) {
            MenuContextual(opciones = if (puedeMover) menuItems(o) else emptyList(), alTocar = { estado.abrir(AFichaOperacion(id = o.id)) }) {
                FilaDeslizable(izquierda = izquierda, derecha = derecha) { CuerpoTarjeta(o, nota) }
            }
            if (pideSena == o.id) PedirSena(o)
            if (puedeMover) {
                Row(Modifier.fillMaxWidth().lineaArriba().height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
                    if (col < 3) {
                        Tocable(alTocar = { alcance.launch { mover(o, col + 1) } }, modifier = Modifier.weight(1f)) {
                            Box(Modifier.fillMaxWidth().heightIn(min = 46.dp), contentAlignment = Alignment.Center) {
                                ConIcono("Pasar a ${Operacion.columnas[col + 1].texto}", "arrow.right", Estilo(15f, Peso.semibold), C.accion)
                            }
                        }
                    } else {
                        Box(Modifier.weight(1f).heightIn(min = 46.dp), contentAlignment = Alignment.Center) { Texto("Cerrada", Estilo(15f, Peso.semibold), C.okTinta) }
                    }
                    Box(Modifier.width(0.5.dp).fillMaxHeight().background(C.linea))
                    MenuBoton(opciones = menuItems(o), descripcion = "Más de ${o.nombre}") {
                        Box(Modifier.size(56.dp, 46.dp), contentAlignment = Alignment.Center) { Icono("ellipsis", 17f, C.texto2) }
                    }
                }
            }
        }
    }

    @Composable
    fun AvisoConflicto(c: Operaciones.Conflicto) {
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.azulFondo).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
            IconoTono(icono = "arrow.triangle.2.circlepath", tono = Tono.azul, tam = 38.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Texto("${c.quien?.let { Charla.primerNombre(it) } ?: "Alguien"} la movió${c.hasta?.let { " a $it" } ?: ""} hace un segundo", Estilo.subheadFuerte, C.texto)
                Texto(Formato.sinViudas("Tu cambio a ${c.mio} no se aplicó. Quedó lo de ${if (c.quien != null) "esa persona" else "la otra persona"}."), Estilo.footnote, C.texto2)
                Box(Modifier.padding(top = 2.dp)) { BotonTonal("Ver qué cambió", { estado.abrir(AFichaOperacion(id = c.id)) }) }
            }
            Tocable(alTocar = { conflicto = null }, descripcion = "Cerrar") { Icono("xmark", 15f, C.texto3) }
        }
    }

    @Composable
    fun HojaSinPermiso(o: Operacion) {
        val quien = o.agenteNombre?.let { Charla.primerNombre(it) }
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp).padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Grabber() }
            ConIcono("${o.nombre} es una operación de ${quien ?: "otra persona"}", "lock", Estilo.tituloHojaChico, C.texto)
            Texto(Formato.sinViudas("Podés verla y comentar. Para moverla de etapa, pedíselo a ${quien ?: "quien la lleva"} o a la dueña de la inmobiliaria."), Estilo.body16, C.texto2)
            Spacer(Modifier.weight(1f))
            BotonPrincipal("Comentar", { sinPermiso = null; estado.abrir(AFichaOperacion(id = o.id)) })
        }
    }

    @Composable
    fun ListaCaidas() {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TituloBloque(texto = "Se cayeron", cuenta = "${Formato.numero(caidas.size)} en total")
            Column(Modifier.fillMaxWidth().tarjeta()) {
                caidas.forEachIndexed { i, o ->
                    key(o.id) {
                        Row(Modifier.fillMaxWidth().lineaArriba(i > 0).padding(horizontal = 12.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Tocable(alTocar = { estado.abrir(AFichaOperacion(id = o.id)) }, modifier = Modifier.weight(1f)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    IconoTono(icono = "heart.slash", tono = Tono.rojo, tam = 36.dp)
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                        Texto(o.nombre, Estilo.filaLista, C.texto, lineas = 1)
                                        Texto(listOfNotNull(o.caidaEn?.let { Formato.dia(it) }, o.caidaMotivo, o.contactoNombre).joinToString(" · "), Estilo.footnote, C.texto2, lineas = 2)
                                    }
                                }
                            }
                            if (puedeMover) BotonTonal("Reabrir", { alcance.launch { reabrir(o) } })
                        }
                    }
                }
                if (caidas.isEmpty()) Texto("No se cayó ninguna.", Estilo.subhead, C.texto3, Modifier.padding(20.dp))
            }
        }
    }

    /** «Por agente» y «Este mes» (el panel de sección de la web). */
    @Composable
    fun EsteMesBloque() {
        val todas = ops ?: emptyList()
        val cerradas = todas.filter { it.etapa == "cierre" && esteMesBA(it.actualizadoEn) }
        val honorarios = cerradas.fold(0.0) { n, o ->
            if ((o.moneda ?: "USD") == "USD") n + (o.valor ?: 0.0) * ((o.honorariosComprador ?: 0.0) + (o.honorariosVendedor ?: 0.0)) / 100 else n
        }
        val caidasMes = caidas.filter { esteMesBA(it.caidaEn) }
        val motivos = caidasMes.mapNotNull { it.caidaMotivo }.groupingBy { it }.eachCount()
        val comun = motivos.maxByOrNull { it.value }
        val conOps = equipo.filter { p -> todas.any { it.agenteId == p.usuarioId } }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (conOps.isNotEmpty()) {
                TituloBloque(texto = "Por agente")
                Column(Modifier.fillMaxWidth().tarjeta()) {
                    conOps.forEachIndexed { i, p ->
                        key(p.id) {
                            val n = todas.count { it.agenteId == p.usuarioId && it.etapa != "cierre" }
                            Tocable(alTocar = { agente = p.usuarioId }, modifier = Modifier.lineaArriba(i > 0)) {
                                Row(Modifier.fillMaxWidth().heightIn(min = 46.dp).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Texto(p.completo, Estilo.filaLista, C.texto, Modifier.weight(1f))
                                    Texto(Formato.cantidad(n, "abierta", "abiertas"), Estilo.footnote, C.texto2)
                                }
                            }
                        }
                    }
                }
            }
            TituloBloque(texto = "Este mes")
            Column(Modifier.fillMaxWidth().tarjeta()) {
                Row(Modifier.fillMaxWidth().heightIn(min = 46.dp).padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    ConIcono("Cerradas", "hands.sparkles", Estilo.filaLista, C.texto, Modifier.weight(1f))
                    if (honorarios > 0) Texto("${Formato.plataCorta(honorarios, "USD")} honorarios", Estilo.footnote, C.okTinta)
                    Texto(Formato.numero(cerradas.size), Estilo.filaLista, C.texto)
                }
                Tocable(alTocar = { caidasVista = true }, modifier = Modifier.lineaArriba()) {
                    Row(Modifier.fillMaxWidth().heightIn(min = 46.dp).padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        ConIcono("Caídas", "heart.slash", Estilo.filaLista, C.errorTinta, Modifier.weight(1f))
                        if (comun != null) Texto("${Formato.numero(comun.value)} por ${comun.key.lowercase()}", Estilo.footnote, C.texto2)
                        Texto(Formato.numero(caidasMes.size), Estilo.filaLista, C.texto)
                    }
                }
            }
        }
    }

    // MARK: Pantalla

    PantallaApilada(
        titulo = if (estado.rol == "agente") "Mis operaciones" else "Operaciones",
        acciones = {
            if (puedeMover) {
                Tocable(alTocar = { nueva = true }, descripcion = "Nueva operación") { Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) { Icono("plus", 20f, C.accion) } }
            }
        },
    ) {
        Refrescable(alRefrescar = { cargar() }) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 8.dp, bottom = 30.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (ops != null) {
                    Texto(
                        "${Formato.numero(abiertas.size)} abiertas · ${Formato.plataCorta(Operacion.enJuego(abiertas), "USD")} en juego",
                        Estilo.subhead, C.texto2, Modifier.padding(horizontal = 16.dp),
                    )
                }
                // Filtros y pestañas
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        ChipAgente("", "Todos")
                        equipo.filter { m -> m.rol != "duenio" || (ops ?: emptyList()).any { it.agenteId == m.usuarioId } }.forEach { p -> key(p.id) { ChipAgente(p.usuarioId, p.completo) } }
                        Box(Modifier.padding(horizontal = 4.dp).size(1.dp, 22.dp).background(C.linea2))
                        listOf("" to "Todo", "venta" to "Venta", "alquiler" to "Alquiler", "temporario" to "Temporario").forEach { (v, t) ->
                            key("t$v") { ChipFiltro(texto = t, activo = tipo == v, hacer = { tipo = v }) }
                        }
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Operacion.columnas.forEachIndexed { i, c ->
                            key(i) {
                                val cards = visibles.filter { it.etapa in c.etapas }
                                ChipFiltro(texto = "${c.texto} ${cards.size}", activo = columna == i && !caidasVista, hacer = { columna = i; caidasVista = false })
                            }
                        }
                        ChipFiltro(texto = "Caídas", activo = caidasVista, hacer = { caidasVista = true })
                    }
                }
                conflicto?.let { c -> Box(Modifier.padding(horizontal = 16.dp)) { AvisoConflicto(c) } }
                // Contenido
                val o = ops
                val e = error
                if (o != null) {
                    if (caidasVista) {
                        Box(Modifier.padding(horizontal = 16.dp)) { ListaCaidas() }
                    } else if (o.isEmpty() && agente.isEmpty() && tipo.isEmpty()) {
                        Box(Modifier.padding(top = 30.dp)) {
                            EstadoVacio(
                                icono = "rectangle.split.3x1", titulo = "Una operación nace cuando alguien pide visita", texto = "O la creás vos.",
                                accion = if (puedeMover) ("Nueva operación" to { nueva = true }) else null,
                                secundaria = "Ver contactos" to { estado.abrir(AContactos()) },
                            )
                        }
                    } else {
                        val c = Operacion.columnas[columna]
                        val cards = visibles.filter { it.etapa in c.etapas }
                        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Texto("${cards.size} · ${Formato.plataCorta(Operacion.enJuego(cards), "USD")}", Estilo.footnote, C.texto2)
                            if (cards.isEmpty()) {
                                val linea2 = C.linea2
                                Box(
                                    Modifier.fillMaxWidth().drawBehind {
                                        drawRoundRect(linea2, cornerRadius = CornerRadius(18.dp.toPx()), style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))))
                                    }.padding(28.dp),
                                    contentAlignment = Alignment.Center,
                                ) { Texto("Nada en ${c.texto}.", Estilo.subhead, C.texto3) }
                            }
                            cards.forEach { x -> key(x.id) { Tarjeta(x) } }
                            if (puedeMover && cards.isNotEmpty()) {
                                Texto(
                                    "Mantené apretada una operación para moverla, o deslizala a la derecha para pasarla.", Estilo.footnote, C.texto3,
                                    Modifier.fillMaxWidth().padding(vertical = 4.dp), alinear = TextAlign.Center,
                                )
                            }
                        }
                    }
                } else if (e != null) {
                    Box(Modifier.padding(top = 30.dp)) { EstadoError(que = "las operaciones", error = e) { alcance.launch { cargar() } } }
                } else {
                    Box(Modifier.padding(horizontal = 16.dp)) { EsqueletoFilas(filas = 3, miniatura = 56.dp) }
                }
                if (ops?.isEmpty() == false) Box(Modifier.padding(horizontal = 16.dp)) { EsteMesBloque() }
            }
        }
    }

    // «Mover … a…»: las otras etapas.
    val mv = moviendo
    Confirmacion(
        visible = mv != null, titulo = "Mover ${mv?.nombre?.split(",")?.firstOrNull() ?: ""} a…", alCerrar = { moviendo = null },
        opciones = if (mv == null) emptyList() else {
            val col = Operacion.columna(mv.etapa)
            Operacion.columnas.withIndex().filter { it.index != col }.map { (i, c) ->
                OpcionConfirmacion(if (i == 0) "Volver a Nueva" else (if (i < col) "Volver a ${c.texto}" else c.texto)) { alcance.launch { mover(mv, i) } }
            }
        },
    )
    Hoja(visible = nueva, alCerrar = { nueva = false }, alturas = listOf(Altura.grande)) { NuevaOperacion() }
    var ultimaCayendo by remember { mutableStateOf<Operacion?>(null) }
    if (cayendo != null) ultimaCayendo = cayendo
    Hoja(visible = cayendo != null, alCerrar = { cayendo = null }, alturas = listOf(Altura.media, Altura.grande)) {
        ultimaCayendo?.let { o ->
            key(o.id) { SeCayo(propiedad = o.nombre.split(",").firstOrNull() ?: "", contacto = o.contactoNombre) { m -> alcance.launch { caer(o, m) } } }
        }
    }
    var ultimaSinPermiso by remember { mutableStateOf<Operacion?>(null) }
    if (sinPermiso != null) ultimaSinPermiso = sinPermiso
    Hoja(visible = sinPermiso != null, alCerrar = { sinPermiso = null }, alturas = listOf(Altura.media)) {
        ultimaSinPermiso?.let { HojaSinPermiso(it) }
    }
}

/** «¿Por qué se cayó Gorriti 5530?»: el motivo de un toque (o escrito) y qué pasa después. */
@Composable
fun SeCayo(propiedad: String, contacto: String?, alElegir: (String) -> Unit) {
    val cerrar = LocalCerrarHoja.current
    var elegido by remember { mutableStateOf("") }
    var otro by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp).padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Grabber() }
        ConIcono("¿Por qué se cayó $propiedad?", "heart.slash", Estilo.tituloHojaChico, C.texto)
        FlowLayout(espacio = 8.dp) {
            Operacion.motivos.forEach { m ->
                val on = elegido == m
                Tocable(alTocar = { elegido = m; otro = "" }, modifier = Modifier.semantics { selected = on }) {
                    Box(
                        Modifier.height(40.dp).clip(CircleShape).background(if (on) C.accion else C.superficie)
                            .border(1.dp, if (on) Color.Transparent else C.linea2, CircleShape).padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) { Texto(m, Estilo(14f, Peso.semibold), if (on) C.sobreAccion else C.texto) }
                }
            }
        }
        Etiquetado("Otro motivo") { CampoCorredor(otro, { otro = it }) }
        Texto(
            Formato.sinViudas("${contacto?.let { "$it queda" } ?: "El interesado queda"} como interesado y le seguimos mandando lo que le encaje. El motivo alimenta el informe del mes."),
            Estilo.footnote, C.texto2,
        )
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BotonSecundario("Cancelar", { cerrar() }, Modifier.weight(1f))
            BotonDestructivo("Se cayó", {
                val m = otro.trim().nonEmpty ?: elegido
                if (m.isNotEmpty()) {
                    cerrar()
                    alElegir(m)
                }
            }, Modifier.weight(1f), habilitado = !(elegido.isEmpty() && otro.trim().isEmpty()))
        }
    }
}

/** Una operación nueva: propiedad, interesado, lo que ofrece y quién la lleva (puede venir con el
 *  contacto o la propiedad ya elegidos). */
@Composable
fun NuevaOperacion(contacto: Resultado? = null, propiedad: Propiedad? = null) {
    val estado = LocalEstado.current
    val cerrar = LocalCerrarHoja.current
    val alcance = rememberCoroutineScope()
    var prop by remember { mutableStateOf(propiedad) }
    var con by remember { mutableStateOf(contacto) }
    var valorTexto by remember { mutableStateOf("") }
    var moneda by remember { mutableStateOf(propiedad?.moneda ?: "USD") }
    var lleva by remember { mutableStateOf(estado.usuario?.id ?: "") }
    var titulo by remember { mutableStateOf("") }
    var equipo by remember { mutableStateOf<List<Miembro>>(emptyList()) }
    var elegirP by remember { mutableStateOf(false) }
    var elegirC by remember { mutableStateOf(false) }
    var guardando by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { equipo = estado.api.opcional<List<Miembro>>("/v1/equipo") ?: emptyList() }

    /** «Mendoza 2350 → Valeria». */
    val tituloArmado = listOf(prop?.let { it.nombre.split(",").firstOrNull() ?: "" }, con?.let { Charla.primerNombre(it.titulo) })
        .mapNotNull { it?.nonEmpty }.joinToString(" → ").nonEmpty ?: "Operación nueva"
    val valor: Double? = valorTexto.filter { it.isDigit() }.toDoubleOrNull()

    suspend fun crear() {
        @Serializable data class Cuerpo(
            val titulo: String, val etapa: String = "nuevo", val valor: Double, val moneda: String,
            val contactoId: String?, val propiedadId: String?, val agenteId: String?,
        )
        guardando = true
        try {
            estado.cola.enviar(
                "POST", "/v1/operaciones",
                Cuerpo(titulo = (titulo.nonEmpty ?: tituloArmado).take(120), valor = valor ?: prop?.precio ?: 0.0, moneda = moneda, contactoId = con?.id, propiedadId = prop?.id, agenteId = lleva.nonEmpty),
                etiqueta = "Operación nueva",
            )
            estado.avisar("Operación creada")
            estado.version += 1
            cerrar()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            estado.avisar(e)
        } finally {
            guardando = false
        }
    }

    @Composable
    fun FilaValor(titulo: String, valor: String, alTocar: () -> Unit) {
        Tocable(alTocar = alTocar) {
            Row(Modifier.fillMaxWidth().heightIn(min = 46.dp), verticalAlignment = Alignment.CenterVertically) {
                Texto(titulo, Estilo.body, C.texto, Modifier.weight(1f))
                Texto(valor, Estilo.body, C.texto2, lineas = 1)
                Spacer(Modifier.width(6.dp))
                Chevron()
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        BarraHoja(titulo = "Nueva operación", izquierda = { BotonHoja("Cancelar", alTocar = { cerrar() }) }) {
            val habilitado = !((prop == null && con == null) || guardando)
            Texto("Crear", Estilo.headline, if (habilitado) C.accion else C.texto3, Modifier.clickable(enabled = habilitado) { alcance.launch { crear() } })
        }
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Column(Modifier.fillMaxWidth().tarjeta(12.dp).padding(horizontal = 12.dp, vertical = 4.dp)) {
                FilaValor("Propiedad", prop?.nombre ?: "Elegí") { elegirP = true }
                Box(Modifier.lineaArriba()) { FilaValor("Interesado", con?.titulo ?: "Elegí") { elegirC = true } }
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Texto("OFRECE", Estilo(13f, Peso.regular), C.texto2, Modifier.padding(horizontal = 16.dp))
                Column(Modifier.fillMaxWidth().tarjeta(12.dp).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Segmentado(opciones = listOf("USD" to "USD", "ARS" to "ARS"), elegido = moneda, alElegir = { moneda = it })
                    CampoCorredor(valorTexto, { valorTexto = it.filter { ch -> ch.isDigit() } }, placeholder = "Valor", teclado = KeyboardType.Number)
                }
            }
            Column(Modifier.fillMaxWidth().tarjeta(12.dp).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
                    Texto("La lleva", Estilo.body, C.texto, Modifier.weight(1f))
                    SelectorMenu(
                        opciones = listOf("" to "Sin agente") + equipo.filter { it.rol != "lector" }.map { it.usuarioId to it.completo },
                        elegido = lleva, alElegir = { lleva = it }, descripcion = "La lleva",
                    )
                }
                CampoCorredor(titulo, { titulo = it }, placeholder = "Título (si no, «$tituloArmado»)")
            }
        }
    }

    Hoja(visible = elegirP, alCerrar = { elegirP = false }, alturas = listOf(Altura.grande)) {
        ElegirPropiedad { p ->
            prop = p
            moneda = p.moneda ?: moneda
            if (valor == null) valorTexto = p.precio?.toLong()?.toString() ?: ""
        }
    }
    Hoja(visible = elegirC, alCerrar = { elegirC = false }, alturas = listOf(Altura.grande)) {
        ElegirContacto { con = it }
    }
}
