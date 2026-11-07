package ar.com.corredor.app.pantallas.consorcios

import ar.com.corredor.app.app.PantallaApilada
import ar.com.corredor.app.nucleo.Fecha

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import ar.com.corredor.app.app.BarraDe
import ar.com.corredor.app.app.LocalEstado
import ar.com.corredor.app.nucleo.Archivo
import ar.com.corredor.app.nucleo.ArchivoPrevio
import ar.com.corredor.app.nucleo.Documentos
import ar.com.corredor.app.nucleo.ErrorApi
import ar.com.corredor.app.nucleo.VistaPrevia
import ar.com.corredor.app.nucleo.Formato
import ar.com.corredor.app.nucleo.opcional
import ar.com.corredor.app.nucleo.subir
import ar.com.corredor.app.tema.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs

// Clon de Pantallas/Consorcios/Liquidar.swift.

// MARK: - Liquidar expensas

/** Lo que salió al emitir: los mails encolados (null: no salieron) y los WhatsApp (`LiquidarConsorcio.Emision`). */
data class Emision(val encolados: Int, val sinEmail: List<String>?, val whatsapp: ConsorcioWhatsApp.EnvioPeriodo?)

/** Un gasto para cargar o corregir (`LiquidarConsorcio.EdicionGasto`). */
data class EdicionGasto(val g: GastoExpensas? = null, val comprobante: Archivo? = null) {
    val id: String get() = g?.id ?: comprobante?.id ?: "nuevo"
}

/** El período liquidado antes, para comparar. */
private data class Anterior(val periodo: String, val total: Double, val gastos: List<GastoExpensas>)

/** Una fila del prorrateo. */
private data class FilaP(val id: String, val u: String, val p: String, val c: Double, val o: Double, val s: Double, val t: Double)

/** Liquidar expensas: 1 Gastos → 2 Prorrateo → 3 Emitir → Listo. Emitir es lo único que se confirma (no se deshace);
 *  cargar, corregir o sacar un gasto pasa al toque, con deshacer. `pedido`: «AAAA-MM» pedido; null es el que toca. */
@Composable
fun LiquidarConsorcio(consorcio: String, pedido: String? = null) {
    val estado = LocalEstado.current
    val ctx = LocalContext.current
    val alcance = rememberCoroutineScope()
    var fijo by remember { mutableStateOf<String?>(null) }
    var c by remember { mutableStateOf<ConsorcioAPI?>(null) }
    var periodo by remember { mutableStateOf("") }
    var ficha by remember { mutableStateOf<PeriodoAPI?>(null) }
    var prorrateo by remember { mutableStateOf<ProrrateoExpensas?>(null) }
    var anterior by remember { mutableStateOf<Anterior?>(null) }
    var unidades by remember { mutableStateOf<List<UnidadAPI>>(emptyList()) }
    var deudores by remember { mutableStateOf<List<DeudorAPI>>(emptyList()) }
    var proveedores by remember { mutableStateOf<List<ProveedorConsorcio>>(emptyList()) }
    var reglamento by remember { mutableStateOf<ReglamentoConsorcioR?>(null) }
    var error by remember { mutableStateOf<ErrorApi?>(null) }
    var cargado by remember { mutableStateOf(false) }
    var enProrrateo by remember { mutableStateOf(false) }
    var emitiendo by remember { mutableStateOf(false) }
    var listo by remember { mutableStateOf<Emision?>(null) }
    var sacados by remember { mutableStateOf<Set<String>>(emptySet()) }
    var gasto by remember { mutableStateOf<EdicionGasto?>(null) }
    var desdeFactura by remember { mutableStateOf(false) }
    var subiendo by remember { mutableStateOf(false) }
    var todas by remember { mutableStateOf(false) }
    var abriendo by remember { mutableStateOf(false) }
    var previo by remember { mutableStateOf<ArchivoPrevio?>(null) }

    val mes = Cons.nombreMes(periodo)
    val liquidado = ficha?.let { it.estado != "borrador" } ?: false
    val paso = if (listo != null || liquidado) 4 else if (emitiendo) 3 else if (enProrrateo) 2 else 1
    val gastos = (ficha?.gastos ?: emptyList()).filter { it.id !in sacados }
    val total = gastos.sumOf { it.monto }

    suspend fun cargar() {
        val b = "/v1/consorcios/$consorcio"
        try {
            val x: ConsorcioAPI = estado.api.get(b)
            val ps: List<PeriodoAPI> = estado.api.get("$b/periodos", listOf("limite" to "24"))
            unidades = estado.api.opcional<List<UnidadAPI>>("$b/unidades") ?: emptyList()
            deudores = estado.api.opcional<List<DeudorAPI>>("$b/deudores") ?: emptyList()
            proveedores = runCatching { ProveedoresConsorcio.todos(estado.api) }.getOrElse { if (it is CancellationException) throw it; emptyList() }
            reglamento = estado.api.opcional<ReglamentoConsorcioR>("$b/reglamento")
            val p = fijo ?: pedido
            val elegido: Pair<String, PeriodoAPI?> = if (p != null && Regex("^\\d{4}-\\d{2}$").containsMatchIn(p)) {
                p to ps.firstOrNull { it.periodo == p }
            } else Cons.aLiquidar(ps)
            periodo = elegido.first
            val actual = elegido.second
            if (actual != null) {
                ficha = estado.api.get("$b/periodos/${actual.id}")
                prorrateo = if (actual.estado == "borrador") estado.api.opcional<ProrrateoExpensas>("$b/periodos/${actual.id}/prorrateo") else null
            } else {
                ficha = null
                prorrateo = null
            }
            val a = Cons.anterior(ps, periodo)
            anterior = if (a != null) {
                val fa = estado.api.opcional<PeriodoAPI>("$b/periodos/${a.id}")
                Anterior(a.periodo, a.total ?: 0.0, fa?.gastos ?: emptyList())
            } else null
            sacados = emptySet()
            c = x
            cargado = true
            error = null
        } catch (e: CancellationException) { throw e } catch (e: Exception) {
            if (e is ErrorApi && !cargado) error = e
        }
    }

    suspend fun abrir() {
        abriendo = true
        try {
            estado.api.hacer("POST", "/v1/consorcios/$consorcio/periodos", mapOf("periodo" to periodo))
            fijo = periodo
            estado.version += 1
        } catch (e: CancellationException) { throw e } catch (e: Exception) { estado.avisar(e) }
        abriendo = false
    }

    /** Sacar un gasto: se esconde al toque y el «Deshacer» lo vuelve a cargar igual. */
    fun sacar(g: GastoExpensas) {
        val f = ficha ?: return
        sacados = sacados + g.id
        val api = estado.api
        val base = "/v1/consorcios/$consorcio/periodos/${f.id}/gastos"
        alcance.launch {
            try {
                api.hacer("DELETE", "$base/${g.id}")
                estado.avisar("Sacaste «${g.descripcion}»") {
                    estado.tarea {
                        try {
                            api.hacer("POST", base, DatosGasto(g.descripcion, g.tipo ?: "ordinaria", g.monto, g.columnaId, g.proveedorId, g.documentoId, g.fecha))
                        } catch (e: CancellationException) { throw e } catch (e: Exception) { estado.avisar(e) }
                        estado.version += 1
                    }
                }
                estado.version += 1
            } catch (e: CancellationException) { throw e } catch (e: Exception) {
                sacados = sacados - g.id
                estado.avisar(e)
            }
        }
    }

    /** La foto o el PDF de una factura: se sube y se abre la hoja con el comprobante puesto. */
    suspend fun factura(x: ArchivoElegidoConsorcio) {
        subiendo = true
        try {
            val a = estado.api.subir(x.datos, x.nombre, x.tipo, "documento")
            gasto = EdicionGasto(g = null, comprobante = a)
        } catch (e: CancellationException) { throw e } catch (e: Exception) { estado.avisar(e) }
        subiendo = false
    }

    suspend fun guardarReglamento() {
        try {
            reglamento = estado.api.enviar("PUT", "/v1/consorcios/$consorcio/reglamento", vacioConsorcio)
            estado.avisar("Guardamos los coeficientes del reglamento")
        } catch (e: CancellationException) { throw e } catch (e: Exception) { estado.avisar(e) }
    }

    /** Liquida el período (todo o nada, en la API) y manda los recibos por mail y por WhatsApp. */
    suspend fun emitir(f: PeriodoAPI): Boolean {
        val base = "/v1/consorcios/$consorcio/periodos/${f.id}"
        try {
            estado.api.hacer("POST", "$base/liquidar")
        } catch (e: CancellationException) { throw e } catch (e: Exception) {
            val t = (e as? ErrorApi)?.detalle ?: "Probá de nuevo."
            estado.sugerir("No se emitió · $t", "Reintentar") { estado.tarea { emitir(f) } }
            return false
        }
        // Ya está liquidado: si el envío falla, se reintenta desde Expensas (no se vuelve a liquidar).
        val envio = runCatching { estado.api.enviar<EnvioMailExpensas, Map<String, String>>("POST", "$base/enviar", vacioConsorcio) }
            .getOrElse { if (it is CancellationException) throw it; null }
        val wa = runCatching { ConsorcioWhatsApp.liquidaciones(estado.api, consorcio = consorcio, periodo = f.id) }
            .getOrElse { if (it is CancellationException) throw it; null }
        Haptico.exito()
        listo = Emision(envio?.encolados ?: 0, envio?.let { it.sinEmail ?: emptyList() }, wa)
        emitiendo = false
        fijo = periodo
        estado.version += 1
        return true
    }

    suspend fun imprimir() {
        val f = ficha ?: return
        try {
            val u = Documentos.expensas(ctx, consorcio = consorcio, periodo = f.id, unidad = null, api = estado.api)
            previo = ArchivoPrevio(u, "application/pdf")
        } catch (e: CancellationException) { throw e } catch (e: Exception) { estado.avisar(e) }
    }

    fun filas(c: ConsorcioAPI): List<FilaP> {
        val general = c.general
        val deuda = deudores.associate { it.unidadId to it.total }
        val reparto = (prorrateo?.unidades ?: emptyList()).associate { it.unidadId to it.total }
        return unidades.sortedWith { a, b -> Cons.ordenUnidad.compare(a.etiqueta, b.etiqueta) }.map { u ->
            val o = reparto[u.id] ?: 0.0
            val s = deuda[u.id] ?: 0.0
            FilaP(u.id, u.etiqueta, u.propietario ?: "Sin propietario", general?.let { u.coeficientes?.get(it.id) ?: 0.0 } ?: 0.0, o, s, o + s)
        }
    }

    fun textoEnvio(l: Emision?): String {
        val conMail = unidades.count { it.propietarioEmail != null }
        val sinContacto = unidades.count { it.propietarioEmail == null && it.propietarioTelefono == null }
        val n = l?.encolados ?: conMail
        val imp = l?.sinEmail?.size ?: sinContacto
        if (l != null && l.sinEmail == null) return "Quedó liquidado. Los mails no salieron: mandalos desde Expensas cuando vuelva la señal."
        val wa = l?.whatsapp?.enviadas ?: 0
        val partes = listOfNotNull(
            if (n > 0) "${Cons.plural(n, "liquidación enviada", "liquidaciones enviadas")} por mail" else null,
            if (wa > 0) "$wa por WhatsApp" else null,
            if (imp > 0) (if (imp == 1) "1 lista" else "$imp listas") + " para imprimir" else null,
        )
        val sinNumero = if (l?.whatsapp?.sinWhatsApp == true) " Para mandarlas también por WhatsApp, conectá el número de la inmobiliaria." else ""
        return "${Cons.lista(partes)}. Cada propietario tiene su link con el detalle y los comprobantes.$sinNumero"
    }

    LaunchedEffect(estado.version) { cargar() }

    PantallaApilada(if (periodo.isEmpty()) "Liquidar" else "Expensas de $mes") {
        Refrescable({ cargar() }, Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                val x = c
                val e = error
                if (x != null && cargado) {
                    // Cabecera
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Texto("${x.nombre} · ${Cons.plural(x.metricas?.unidades ?: unidades.size, "unidad", "unidades")}", Estilo.footnote, C.texto2)
                        if (ficha != null) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf("Gastos" to 1, "Prorrateo" to 2, "Emitir" to 3).forEach { (t, n) ->
                                    val on = paso == n
                                    val hecho = paso > n
                                    Row(Modifier.semantics(mergeDescendants = true) { selected = on }, horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Box(Modifier.size(20.dp).clip(CircleShape).background(if (on || hecho) C.accion else C.hundida), contentAlignment = Alignment.Center) {
                                            Texto(if (hecho) "✓" else "$n", Estilo(12f, Peso.bold), if (on || hecho) C.sobreAccion else C.texto2)
                                        }
                                        Texto(t, Estilo.footnoteFuerte, if (on) C.texto else C.texto2)
                                    }
                                }
                            }
                        }
                    }
                    val f = ficha
                    if (f == null) {
                        EstadoVacio("doc.text", "Empezá la liquidación de $mes",
                            "Abrí el período y cargá los gastos del mes: sueldos, servicios, abonos y arreglos. Después ves cómo se reparte entre las unidades antes de emitir.",
                            accion = if (estado.escribe) ((if (abriendo) "Abriendo…" else "Abrir $mes") to { alcance.launch { abrir() } }) else null)
                    } else if (paso == 4) {
                        // 4 · Listo
                        Column(Modifier.fillMaxWidth().tarjeta().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Icono("checkmark.seal.fill", 44f, C.okTinta)
                            Texto("${Cons.NombreMes(periodo)} liquidado", Estilo.titulo3, C.texto)
                            Texto(textoEnvio(listo), Estilo.texto14, C.texto2, alinear = TextAlign.Center)
                            Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(C.accion))
                            BotonHundido("Imprimir para la cartelera", { alcance.launch { imprimir() } }, simbolo = "printer")
                            BotonAccion("Volver al consorcio", { estado.volver() })
                        }
                    } else if (paso == 1 || (paso == 3 && !enProrrateo)) {
                        // 1 · Gastos
                        val sinComprobante = gastos.filter { it.documentoId == null }
                        BloqueConsorcio("Gastos del período · ${gastos.size}") {
                            if (gastos.isEmpty()) VacioConsorcio("Todavía no hay gastos en $mes. Cargá el primero o sumá la foto de la factura.")
                            gastos.forEachIndexed { i, g ->
                                key(g.id) {
                                    MenuContextual(
                                        if (estado.escribe) listOf(
                                            OpcionDeMenu("Corregir", "pencil") { gasto = EdicionGasto(g = g) },
                                            OpcionDeMenu("Sacar", "trash", destructiva = true) { sacar(g) },
                                        ) else emptyList(),
                                        alTocar = { if (estado.escribe) gasto = EdicionGasto(g = g) },
                                    ) {
                                        RenglonConsorcio(primero = i == 0) {
                                            IconoTono(if (g.documentoId != null) Cons.iconoGasto(g) else "exclamationmark.triangle", if (g.documentoId != null) Tono.neutro else Tono.oro)
                                            DosLineasConsorcio(g.descripcion, listOfNotNull(g.proveedor, if (g.documentoId != null) "con comprobante" else "sin comprobante").joinToString(" · "))
                                            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                Texto(Cons.cifra(g.monto), Estilo.footnoteFuerte, C.texto)
                                                Texto(Cons.rubroGasto(g), Estilo.footnote, C.texto3)
                                            }
                                        }
                                    }
                                }
                            }
                            if (estado.escribe) {
                                RenglonConsorcio {
                                    BotonLabelConsorcio("Cargar un gasto", "plus") { gasto = EdicionGasto() }
                                    Spacer(Modifier.weight(1f))
                                    BotonLabelConsorcio(if (subiendo) "Subiendo…" else "Desde la factura", "doc.viewfinder", habilitado = !subiendo) { desdeFactura = true }
                                }
                            }
                            RenglonConsorcio {
                                Texto("Total del período", Estilo.subheadFuerte, C.texto, Modifier.weight(1f))
                                Texto(Formato.plata(total), Estilo(20f, Peso.bold), C.texto)
                            }
                        }
                        if (sinComprobante.isNotEmpty()) {
                            val nombres = sinComprobante.take(3).joinToString(", ") { it.descripcion.lowercase().replace(Regex("^arreglo"), "el arreglo") }
                            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.avisoFondo).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icono("doc.text", 14f, C.avisoTinta)
                                TextoRico(buildAnnotatedString {
                                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(if (sinComprobante.size == 1) "1 gasto sin comprobante" else "${sinComprobante.size} gastos sin comprobante") }
                                    append(": $nombres. Podés liquidar igual; queda marcado en la liquidación.")
                                }, Estilo.texto14, C.avisoTinta)
                            }
                        }
                        anterior?.let { a ->
                            // «Contra octubre»: el total y los dos gastos que más subieron (o que son nuevos).
                            val clave: (String) -> String = { it.lowercase().split("·")[0].trim() }
                            val antes = mutableMapOf<String, Double>()
                            for (g in a.gastos) antes[clave(g.descripcion)] = (antes[clave(g.descripcion)] ?: 0.0) + g.monto
                            val ahora = mutableMapOf<String, Double>()
                            for (g in gastos) ahora[clave(g.descripcion)] = (ahora[clave(g.descripcion)] ?: 0.0) + g.monto
                            val subas = ahora.map { it.key to (it.value - (antes[it.key] ?: 0.0)) }.filter { it.second > 0 }.sortedByDescending { it.second }.take(2).map { it.first }
                            val v = Cons.variacion(total, a.total)
                            Column(Modifier.fillMaxWidth().tarjeta().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Texto("Contra ${Cons.nombreMes(a.periodo)}", Estilo.subheadFuerte, C.texto)
                                Row(Modifier.fillMaxWidth()) {
                                    Texto(Cons.NombreMes(a.periodo), Estilo.texto14, C.texto2, Modifier.weight(1f))
                                    Texto(Formato.plata(a.total), Estilo.texto14, C.texto2)
                                }
                                Row(Modifier.fillMaxWidth()) {
                                    Texto(Cons.NombreMes(periodo), Estilo.texto14, C.texto, Modifier.weight(1f))
                                    Texto(Formato.plata(total), Estilo.texto14.con(Peso.bold), C.texto)
                                }
                                if (v.isNotEmpty()) {
                                    Texto(v + (if (subas.isEmpty()) "" else " · ${subas.joinToString(" y ")}"), Estilo.footnoteFuerte, if (total < a.total) C.okTinta else C.avisoTinta)
                                }
                            }
                        }
                        BotonAccion("Ver el prorrateo", { enProrrateo = true }, habilitado = gastos.isNotEmpty())
                    } else {
                        // 2 · Prorrateo
                        val fs = filas(x)
                        val visibles = if (todas) fs else fs.take(10)
                        val suma = x.general?.let { Cons.sumaCoeficientes(unidades, it.id) } ?: 0.0
                        BloqueConsorcio("Prorrateo") {
                            visibles.forEachIndexed { i, fp ->
                                key(fp.id) {
                                    RenglonConsorcio(primero = i == 0) {
                                        Texto(fp.u, Estilo.filaLista, C.texto, Modifier.widthIn(min = 44.dp))
                                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            Texto(fp.p, Estilo.texto14, C.texto, lineas = 1)
                                            Texto("Coef. ${Cons.coeficiente(fp.c)} · Ordinarias ${Cons.cifra(fp.o)} · Saldo ant. ${if (fp.s > 0) Cons.cifra(fp.s) else "—"}",
                                                Estilo.footnote, if (fp.s > 0) C.errorTinta else C.texto2)
                                        }
                                        Texto(Cons.cifra(fp.t), Estilo.footnoteFuerte, C.texto)
                                    }
                                }
                            }
                            RenglonConsorcio {
                                if (fs.size > 10 && !todas) {
                                    Texto("… y ${Cons.plural(fs.size - 10, "unidad más", "unidades más")}", Estilo.footnoteFuerte, C.accion, Modifier.clickable { todas = true })
                                    Texto("·", Estilo.footnote, C.texto3)
                                }
                                Texto("coeficientes suman ${Cons.coeficiente(suma)}", Estilo.footnote, C.texto2)
                            }
                        }
                        // Qué revisar
                        val conDeuda = fs.count { it.s > 0 }
                        val vence = Cons.vencimiento(periodo, x.diaVencimiento)
                        val vence2 = Cons.vencimiento(periodo, x.diaSegundoVencimiento)
                        Column(Modifier.fillMaxWidth().tarjeta().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Texto("Qué revisar", Estilo.subheadFuerte, C.texto)
                            if (conDeuda > 0) {
                                ItemRevisar(false, "${Cons.plural(conDeuda, "unidad con deuda", "unidades con deuda")}: se suma con interés del ${Cons.coma(x.interesMensual ?: 0.0)} % mensual.")
                            } else {
                                ItemRevisar(true, "Nadie debe meses anteriores.")
                            }
                            val r = reglamento
                            if (abs(suma - 100) >= 0.005) {
                                ItemRevisar(false, "Los coeficientes suman ${Cons.coeficiente(suma)}: revisalos en Unidades.")
                            } else if (r != null && r.guardadoEn != null && r.sinCambios == true) {
                                ItemRevisar(true, "Coeficientes del reglamento: sin cambios.")
                            } else if (r != null && r.guardadoEn != null) {
                                val unidadesCambio = (r.cambios ?: emptyList()).mapNotNull { it.unidad }.toSet()
                                val n = unidadesCambio.size
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    ItemRevisar(false, (if (n == 1) "Cambió el coeficiente de ${unidadesCambio.firstOrNull() ?: ""}" else "Cambiaron los coeficientes de $n unidades") +
                                        " desde el reglamento: revisalos en Unidades o tomalos como los nuevos.")
                                    if (estado.escribe) Texto("Tomalos como los nuevos", Estilo.footnoteFuerte, C.accion, Modifier.clickable { alcance.launch { guardarReglamento() } })
                                }
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    ItemRevisar(true, "Los coeficientes suman 100 %. Guardalos como los del reglamento para que te avisemos si cambian.")
                                    if (estado.escribe) Texto("Guardalos como los del reglamento", Estilo.footnoteFuerte, C.accion, Modifier.clickable { alcance.launch { guardarReglamento() } })
                                }
                            }
                            if (vence != null) {
                                ItemRevisar(true, "Vencimiento: ${Cons.diaBarraMes(vence)}${vence2?.let { " · 2.º vto ${Cons.diaBarraMes(it)} con recargo" } ?: ""}.")
                            } else {
                                ItemRevisar(false, "Sin día de vencimiento: cargalo en los datos del consorcio.", oro = true)
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(Modifier.weight(1f)) { BotonHundido("Volver a gastos", { enProrrateo = false }) }
                            if (estado.escribe) Box(Modifier.weight(1f)) { BotonAccion("Liquidar $mes", { emitiendo = true }) }
                        }
                    }
                } else if (e != null) {
                    EstadoError("la liquidación", e) { alcance.launch { cargar() } }
                } else EsqueletoFilas(filas = 6)
            }
        }
    }
    Hoja(gasto != null, { gasto = null }) {
        val f = ficha
        val g = gasto
        if (f != null && g != null) key(g.id) {
            HojaGastoConsorcio(consorcio, f.id, g.g, g.comprobante, proveedores, c?.columnas ?: emptyList()) { x -> sacar(x) }
        }
    }
    // El `.interactiveDismissDisabled(mandando)` lo maneja la hoja: mientras emite no se cierra.
    var mandandoEmision by remember { mutableStateOf(false) }
    Hoja(emitiendo, { if (!mandandoEmision) emitiendo = false }) {
        val x = c
        val f = ficha
        if (x != null && f != null) {
            HojaEmitirExpensas(mes, Cons.nombreMes(Cons.mesSiguiente(periodo)), unidades, prorrateo?.total ?: total, Cons.vencimiento(periodo, x.diaVencimiento),
                alMandar = { mandandoEmision = it }) { emitir(f) }
        }
    }
    ElegirArchivoConsorcio(desdeFactura, { desdeFactura = false }) { a -> a.firstOrNull()?.let { x -> alcance.launch { factura(x) } } }
    VistaPrevia(previo) { previo = null }
}

/** Un renglón de «Qué revisar»: el tilde o el «!» y el texto. */
@Composable
private fun ItemRevisar(ok: Boolean, t: String, oro: Boolean = false) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
        Icono(if (ok) "checkmark.circle" else "exclamationmark.circle", 14f, if (ok) C.okTinta else if (oro) C.avisoTinta else C.errorTinta, Modifier.padding(top = 2.dp))
        Texto(t, Estilo.texto14, C.texto)
    }
}

/** «Vas a emitir <mes>»: todo lo que sale y a quién, el tilde «Revisé…» y «Emitir y mandar». */
@Composable
fun HojaEmitirExpensas(
    mes: String,
    mesSiguiente: String,
    unidades: List<UnidadAPI>,
    total: Double,
    vence: String?,
    alMandar: (Boolean) -> Unit = {},
    emitir: suspend () -> Boolean,
) {
    val cerrar = LocalCerrarHoja.current
    val alcance = rememberCoroutineScope()
    var revisado by remember { mutableStateOf(false) }
    var mandando by remember { mutableStateOf(false) }
    val conContacto = unidades.count { it.propietarioEmail != null || it.propietarioTelefono != null }
    val sin = unidades.filter { it.propietarioEmail == null && it.propietarioTelefono == null }.sortedWith { a, b -> Cons.ordenUnidad.compare(a.etiqueta, b.etiqueta) }.map { it.etiqueta }

    MarcoHoja("Vas a emitir $mes", cancelar = null) {
        Texto("Esto no se deshace: si hay un error, se corrige con una nota de crédito en $mesSiguiente.", Estilo.texto14, C.texto2)
        Column(Modifier.fillMaxWidth().tarjeta()) {
            RenglonConsorcio(primero = true) {
                Texto("${Cons.plural(unidades.size, "liquidación", "liquidaciones")}${vence?.let { " · vence el ${Cons.diaBarraMes(it)}" } ?: ""}", Estilo.texto14, C.texto2, Modifier.weight(1f))
                Texto(Formato.plata(total), Estilo.filaLista, C.texto)
            }
            RenglonConsorcio {
                Texto("Se mandan por WhatsApp y mail", Estilo.texto14, C.texto2, Modifier.weight(1f))
                Texto(Cons.plural(conContacto, "propietario", "propietarios"), Estilo.filaLista, C.texto)
            }
            if (sin.isNotEmpty()) {
                RenglonConsorcio {
                    Texto("Sin contacto · van impresas", Estilo.texto14, C.avisoTinta, Modifier.weight(1f))
                    Texto("${sin.size} (${Cons.lista(sin)})", Estilo.filaLista, C.avisoTinta, alinear = TextAlign.End)
                }
            }
        }
        Interruptor("Revisé los gastos y el prorrateo", revisado, { revisado = it })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) { BotonHundido("Revisar de nuevo", { cerrar() }) }
            Box(Modifier.weight(1f)) {
                BotonAccion(if (mandando) "Emitiendo…" else "Emitir y mandar", {
                    alcance.launch {
                        mandando = true
                        alMandar(true)
                        emitir()
                        mandando = false
                        alMandar(false)
                    }
                }, habilitado = revisado && !mandando)
            }
        }
    }
}

/** Lo que va en POST/PUT …/gastos (`HojaGastoConsorcio.Datos` en iOS). */
@Serializable
data class DatosGasto(
    val descripcion: String, val tipo: String, val monto: Double, val columnaId: String?, val proveedorId: String?, val documentoId: String?, val fecha: String?,
)

/** Cargar o corregir un gasto del período: de qué es, monto, fecha, proveedor (o uno nuevo con rubro), tipo, columna y el
 *  comprobante (foto o PDF). Sin comprobante se puede liquidar igual. */
@Composable
fun HojaGastoConsorcio(
    consorcio: String,
    periodo: String,
    gasto: GastoExpensas?,
    comprobante: Archivo?,
    proveedores: List<ProveedorConsorcio>,
    columnas: List<ColumnaAPI>,
    alSacar: (GastoExpensas) -> Unit,
) {
    val estado = LocalEstado.current
    val ctx = LocalContext.current
    val cerrar = LocalCerrarHoja.current
    val alcance = rememberCoroutineScope()
    val hoy = LocalDate.now(Formato.zona)
    var descripcion by remember { mutableStateOf(gasto?.descripcion ?: "") }
    var monto by remember { mutableStateOf(gasto?.monto) }
    var fecha by remember { mutableStateOf(gasto?.fecha?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() } ?: hoy) }
    var proveedor by remember { mutableStateOf(gasto?.proveedorId ?: "") }
    var nuevoProveedor by remember { mutableStateOf("") }
    var rubro by remember { mutableStateOf("") }
    var tipo by remember { mutableStateOf(gasto?.tipo ?: "ordinaria") }
    var columna by remember { mutableStateOf(gasto?.columnaId ?: "") }
    var documento by remember {
        mutableStateOf<Pair<String, String>?>(
            comprobante?.let { it.id to (it.nombre ?: "Comprobante") } ?: gasto?.documentoId?.let { it to "Comprobante" },
        )
    }
    var elegir by remember { mutableStateOf(false) }
    var subiendo by remember { mutableStateOf(false) }
    var guardando by remember { mutableStateOf(false) }
    var previo by remember { mutableStateOf<ArchivoPrevio?>(null) }

    val valido = descripcion.trim().isNotEmpty() && descripcion.length <= 200 && (monto ?: 0.0) > 0 &&
        (proveedor != "otro" || nuevoProveedor.trim().isNotEmpty())

    suspend fun subir(x: ArchivoElegidoConsorcio) {
        subiendo = true
        try {
            val a = estado.api.subir(x.datos, x.nombre, x.tipo, "documento")
            documento = a.id to (a.nombre ?: x.nombre)
        } catch (e: CancellationException) { throw e } catch (e: Exception) { estado.avisar(e) }
        subiendo = false
    }

    suspend fun ver(id: String) {
        try {
            previo = Documentos.archivoConsorcio(ctx, id, estado.api)
        } catch (e: CancellationException) { throw e } catch (e: Exception) { estado.avisar(e) }
    }

    suspend fun guardar() {
        guardando = true
        try {
            var pid: String? = proveedor.ifEmpty { null }
            if (proveedor == "otro") {
                val p = ProveedoresConsorcio.crear(estado.api, ProveedoresConsorcio.Nuevo(nombre = nuevoProveedor.trim(), rubro = rubro.noVacio()))
                pid = p.id
                proveedor = p.id
            }
            val d = DatosGasto(descripcion.trim(), tipo, Cons.redondo(monto ?: 0.0), columna.noVacio(), pid, documento?.first, fecha.toString())
            val base = "/v1/consorcios/$consorcio/periodos/$periodo/gastos"
            if (gasto != null) {
                estado.api.hacer("PUT", "$base/${gasto.id}", d)
                estado.avisar("Gasto corregido")
            } else {
                estado.api.hacer("POST", base, d)
                estado.avisar("Gasto cargado")
            }
            estado.version += 1
            cerrar()
        } catch (e: CancellationException) { throw e } catch (e: Exception) { estado.avisar(e) }
        guardando = false
    }

    MarcoHoja(
        if (gasto == null) "Cargar un gasto" else "Corregir el gasto", confirmar = "Guardar", confirmarHabilitado = valido && !guardando,
        alConfirmar = { alcance.launch { guardar() } },
    ) {
        SeccionConsorcio {
            CampoConsorcio("De qué es") { CampoTextoConsorcio(descripcion, { descripcion = it }, "Abono del ascensor, factura de luz…") }
            CampoConsorcio("Monto") { CampoNumeroConsorcio(monto, { monto = it }, "Monto") }
            RenglonFechaConsorcio("Fecha", fecha, { fecha = it })
        }
        SeccionConsorcio {
            RenglonSelectorConsorcio("Proveedor", listOf("" to "Sin proveedor") + proveedores.map { it.id to it.conRubro } + listOf("otro" to "Otro (escribilo)…"), proveedor) { proveedor = it }
            if (proveedor == "otro") {
                CampoConsorcio("Nombre del proveedor") { CampoTextoConsorcio(nuevoProveedor, { nuevoProveedor = it }) }
                CampoConsorcio("Rubro (opcional)") { CampoTextoConsorcio(rubro, { rubro = it }, "Plomería") }
            }
            RenglonSelectorConsorcio("Tipo", listOf("ordinaria" to "Ordinaria (todos los meses)", "extraordinaria" to "Extraordinaria (obra, arreglo grande)"), tipo) { tipo = it }
            if (columnas.size > 1) {
                RenglonSelectorConsorcio("Columna", listOf("" to "General") + columnas.filter { it.porDefecto != true }.map { it.id to it.nombre }, columna) { columna = it }
            }
        }
        SeccionConsorcio("Comprobante (opcional)") {
            val d = documento
            if (d != null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icono("checkmark.circle.fill", 17f, C.okTinta)
                    Texto(d.second, Estilo.subheadFuerte, C.texto, Modifier.weight(1f), lineas = 1)
                    Texto("Ver", Estilo.body, C.accion, Modifier.clickable { alcance.launch { ver(d.first) } })
                    Texto("Cambiar", Estilo.body, C.accion, Modifier.clickable { documento = null })
                }
            } else {
                BotonLabelConsorcio(if (subiendo) "Subiendo…" else "Sumar la factura · foto o PDF", "doc.badge.plus", habilitado = !subiendo, estilo = Estilo.body) { elegir = true }
                Texto("Sin comprobante se puede liquidar igual: queda marcado en la liquidación.", Estilo.footnote, C.texto2)
            }
        }
        if (gasto != null) {
            SeccionConsorcio {
                Texto("Sacar este gasto", Estilo.body, C.error, Modifier.fillMaxWidth().clickable { cerrar(); alSacar(gasto) }.padding(vertical = 4.dp))
            }
        }
    }
    ElegirArchivoConsorcio(elegir, { elegir = false }) { a -> a.firstOrNull()?.let { x -> alcance.launch { subir(x) } } }
    VistaPrevia(previo) { previo = null }
}
