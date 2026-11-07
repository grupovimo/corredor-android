package ar.com.corredor.app.pantallas.cobranzas

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import ar.com.corredor.app.app.*
import ar.com.corredor.app.nucleo.*
import ar.com.corredor.app.pantallas.propiedades.Inmobiliaria
import ar.com.corredor.app.tema.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlin.math.roundToInt

/** El link público de cada recibo: se pide una sola vez por cobro (uno nuevo invalida el anterior). */
object LinksRecibo {
    private val links = mutableMapOf<String, String>()

    @Serializable private data class L(val url: String)

    suspend fun de(api: Api, contrato: String, cobro: String): String {
        links[cobro]?.let { return it }
        val l: L = api.enviar("POST", "/v1/contratos/$contrato/cobros/$cobro/compartir", emptyMap<String, String>())
        links[cobro] = l.url
        return l.url
    }
}

/** Lo que pide un TextField(value:, format: .number) con .numberPad: sólo dígitos; vacío es nil. */
private fun numeroDe(t: String): Double? = t.filter { it.isDigit() }.takeIf { it.isNotEmpty() }?.toDoubleOrNull()
private fun textoDe(n: Double?): String = n?.let { if (it == Math.floor(it)) it.toLong().toString() else it.toString() } ?: ""

/** «Registrar un cobro y mandar el recibo» (prototipo P). Dos pasos en la misma hoja:
 *  1. el cobro: el desglose del recibo (el alquiler del contrato, las expensas que se escriben, los
 *     punitorios por los días de atraso según la cláusula, que se pueden sacar, y otros conceptos), el
 *     total (se toca para poner lo que pagó si pagó una parte), el medio y «Registrar ARS …»;
 *  2. el recibo listo, con el sello PAGADO, y el mensaje para mandarlo por WhatsApp con el link.
 *  Registrar no pregunta nada: la fila se va y queda «Deshacer». Con `item.cobro` abre directo el
 *  paso 2 (el «Mandar recibo» de un cobro que ya estaba). */
@Composable
fun HojaCobro(
    item: ACobrar,
    ocultar: ((String) -> Unit)? = null,
    mostrar: ((String) -> Unit)? = null,
) {
    val estado = LocalEstado.current
    val cerrar = LocalCerrarHoja.current
    val ctx = LocalContext.current
    val alcance = rememberCoroutineScope()

    var medio by remember(item.id) { mutableStateOf("transferencia") }
    var total by remember(item.id) { mutableStateOf<Double?>(null) }
    var editando by remember(item.id) { mutableStateOf(false) }
    var expensas by remember(item.id) { mutableStateOf<Double?>(null) }
    var conPunitorios by remember(item.id) { mutableStateOf(true) }
    val otros = remember(item.id) { mutableStateListOf<HojaCobro.Otro>() }
    var fecha by remember(item.id) { mutableStateOf(Formato.hoy()) }
    var cobro by remember(item.id) { mutableStateOf<Cobro?>(null) }
    var registradoEn by remember(item.id) { mutableStateOf<Instant?>(null) }
    var url by remember(item.id) { mutableStateOf<String?>(null) }
    var texto by remember(item.id) { mutableStateOf("") }
    var enviado by remember(item.id) { mutableStateOf(false) }
    var inmo by remember(item.id) { mutableStateOf<Inmobiliaria?>(null) }
    val enTotal = remember { FocusRequester() }

    val hoy = Formato.hoy().toString()
    val diasAtraso = if (item.vence < hoy) maxOf(0, Cobranzas.diasEntre(item.vence, hoy)) else 0
    val punitorios = run {
        val p = item.punitorioDiario
        if (p == null || p <= 0 || diasAtraso <= 0) 0.0 else Math.round(item.esperado * p * diasAtraso / 100).toDouble()
    }
    val textoDias = if (diasAtraso == 1) "1 día" else "$diasAtraso días"
    val conceptos: List<Cobro.Concepto> = buildList {
        add(Cobro.Concepto("Alquiler", item.esperado))
        expensas?.let { e -> if (e > 0) add(Cobro.Concepto("Expensas ordinarias", e)) }
        if (punitorios > 0 && conPunitorios) add(Cobro.Concepto("Punitorios $textoDias", punitorios))
        for (o in otros) { val m = o.monto; if (m != null && m > 0 && o.concepto.trim().isNotEmpty()) add(Cobro.Concepto(o.concepto.trim(), m)) }
    }
    val suma = conceptos.sumOf { it.monto }
    val monto = total ?: suma
    val parcial = total != null && Math.round(total!!).toDouble() != Math.round(suma).toDouble()
    val mes = TextosCobro.nombreMes(item.periodo)
    val n = TextosCobro.primerNombre(item.nombre)

    suspend fun prepararLink(c: Cobro) {
        if (c.id.isEmpty()) return
        try {
            val u = LinksRecibo.de(estado.api, item.contratoId, c.id)
            url = u
            texto = TextosCobro.recibo(item.nombre, c.periodo, u)
        } catch (e: CancellationException) { throw e } catch (e: Exception) {
            estado.avisar("No pudimos armar el link del recibo. Probá de nuevo.", ok = false)
        }
    }

    fun registrar() {
        if (monto <= 0) return
        val desglose = if (parcial || conceptos.size < 2) emptyList() else conceptos
        val soloMonto: Double? = if (parcial || (conceptos.size < 2 && total != null)) monto else null
        registradoEn = Instant.now()
        // Paso 2 al toque; el cobro llega por detrás.
        cobro = Cobro(id = "", periodo = item.periodo, monto = monto, moneda = item.moneda, medio = medio, compartido = false, conceptos = desglose)
        val pagado = if (Formato.diaISO(fecha) == hoy) null else Formato.diaISO(fecha)
        val id = "${item.contratoId}|${item.periodo.take(7)}"
        Cobrar.registrar(estado, item, monto = soloMonto, medio = medio, conceptos = desglose, pagadoEl = pagado,
            ocultar = { ocultar?.invoke(id) },
            mostrar = { mostrar?.invoke(id); cerrar() },
            alListo = { c ->
                cobro = c
                alcance.launch { prepararLink(c) }
            })
    }

    LaunchedEffect(item.id) {
        inmo = Documentos.inmobiliaria(estado.api)
        item.cobro?.let { c ->
            cobro = c
            registradoEn = Instant.now()
            prepararLink(c)
        }
    }

    Column(Modifier.fillMaxSize().background(C.superficie)) {
        BarraHoja(if (cobro == null) "Cobro de ${item.nombre ?: ""}" else "Recibo listo", if (cobro == null) "Cancelar" else "Cerrar", { cerrar() })
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val c = cobro
            if (c != null) {
                // MARK: 2. El recibo
                ReciboPapel(c, item.nombre, item.propiedad, inmo?.nombre ?: "", registradoEn)
                val u = url
                if (u != null) {
                    MensajeWhatsApp(item.contactoId, item.telefono, item.nombre, texto, { texto = it }, "recibo",
                        listOf(n, TextosCobro.nombreMes(c.periodo), Formato.plata(c.monto, c.moneda), u),
                        "Quedó en la ficha de $n y del contrato.", enviado, { enviado = it },
                        alListo = { estado.version += 1; cerrar() }) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            BotonHundido("Copiar link", {
                                copiar(ctx, u)
                                estado.avisar("Link copiado. Pegalo donde quieras mandarlo.")
                            }, Modifier.weight(1f))
                            BotonHundido("PDF", { abrirUrl(ctx, "$u?imprimir=1") }, Modifier.weight(1f))
                        }
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        CargandoCobro()
                        Texto("Armando el link del recibo…", Estilo.footnote, C.texto2)
                    }
                }
            } else {
                // MARK: 1. El cobro
                Texto("${Formato.mayuscula(mes)} · ${item.propiedad ?: ""}", Estilo.texto14, C.texto2)
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.hundida.copy(alpha = 0.6f))) {
                    Renglon(primero = true) {
                        Texto("Alquiler", Estilo.subhead, C.texto)
                        Relleno()
                        Texto(Formato.numero(item.esperado.roundToInt()), Estilo(15f, Peso.semibold), C.texto)
                    }
                    Renglon {
                        Texto("Expensas ordinarias", Estilo.subhead, C.texto)
                        Relleno()
                        CampoCobro(textoDe(expensas), { expensas = numeroDe(it) }, "Sumar", Modifier.widthIn(max = 140.dp)
                            .semantics { contentDescription = "Expensas ordinarias" }, teclado = KeyboardType.Number, estilo = Estilo.subhead,
                            marco = false, alinear = TextAlign.End)
                    }
                    if (punitorios > 0) {
                        LineaFila()
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { conPunitorios = !conPunitorios }.semantics { selected = conPunitorios }.padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icono(if (conPunitorios) "checkmark.square.fill" else "square", 17f, if (conPunitorios) C.accion else C.texto3)
                            Texto("Punitorios · $textoDias al ${TextosCobro.numero(item.punitorioDiario ?: 0.0)} %", Estilo.subhead,
                                if (conPunitorios) C.texto else C.texto3, Modifier.weight(1f))
                            BasicText(Formato.numero(punitorios.toInt()), style = Estilo(15f, Peso.semibold).ts().copy(
                                color = if (conPunitorios) C.texto else C.texto3,
                                textDecoration = if (!conPunitorios) TextDecoration.LineThrough else null))
                        }
                    }
                    otros.forEachIndexed { i, o ->
                        Renglon {
                            CampoCobro(o.concepto, { t -> otros[i] = o.copy(concepto = if (t.length > 80) t.take(80) else t) }, "Concepto",
                                Modifier.weight(1f), estilo = Estilo.subhead, marco = false)
                            CampoCobro(textoDe(o.monto), { t -> otros[i] = o.copy(monto = numeroDe(t)) }, "0",
                                Modifier.widthIn(max = 110.dp).semantics { contentDescription = "Monto de ${if (o.concepto.isEmpty()) "el concepto" else o.concepto}" },
                                teclado = KeyboardType.Number, estilo = Estilo.subhead, marco = false, alinear = TextAlign.End)
                            Box(Modifier.size(32.dp).clickable { otros.removeAll { it.id == o.id } }.semantics { contentDescription = "Sacar el concepto" },
                                contentAlignment = Alignment.Center) { Icono("xmark", 15f, C.texto3) }
                        }
                    }
                    LineaFila()
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(enabled = otros.size < 9) { otros.add(HojaCobro.Otro()) }
                            .deshabilitado(otros.size >= 9).padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Etiqueta("Otro concepto", "plus", Estilo.subheadFuerte, C.accion)
                    }
                    // El total
                    Box(Modifier.fillMaxWidth().height(1.dp).background(C.linea2))
                    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Texto(if (parcial) "Pagó" else "Total", Estilo(17f, Peso.bold), C.texto)
                        Relleno()
                        if (editando) {
                            Texto(item.moneda, Estilo.headline, C.texto2)
                            CampoCobro(textoDe(total), { total = numeroDe(it) }, "", Modifier.widthIn(max = 160.dp).focusRequester(enTotal)
                                .semantics { contentDescription = "Lo que pagó" }, teclado = KeyboardType.Number, estilo = Estilo.headline,
                                marco = false, alinear = TextAlign.End)
                            LaunchedEffect(Unit) { runCatching { enTotal.requestFocus() } }
                        } else {
                            Box(Modifier.clickable { total = total ?: suma; editando = true }
                                .semantics { contentDescription = "Cambiar el total: ${Formato.plata(monto, item.moneda)}" }) {
                                Texto(Formato.plata(monto, item.moneda), Estilo(20f, Peso.bold), C.texto)
                            }
                        }
                    }
                }
                Segmentado(listOf("transferencia" to "Transferencia", "efectivo" to "Efectivo", "mercadopago" to "Mercado Pago"), medio, { medio = it })
                // DatePicker(in: ...Date()): no se puede elegir un día que todavía no llegó.
                Box(Modifier.padding(horizontal = 4.dp)) {
                    RenglonFecha("Pagó el", fecha, { fecha = if (it.isAfter(Formato.hoy())) Formato.hoy() else it })
                }
                BotonPrincipal("Registrar ${Formato.plata(monto, item.moneda)}", { registrar() }, habilitado = monto > 0, simbolo = "dollarsign.circle")
                Texto("¿Pagó una parte? Tocá el total y escribí lo que pagó.", Estilo.footnote, C.texto2, Modifier.fillMaxWidth(), alinear = TextAlign.Center)
            }
        }
    }
}

/** Lo que HojaCobro declara adentro en iOS. */
object HojaCobro {
    data class Otro(val id: String = UUID.randomUUID().toString(), val concepto: String = "", val monto: Double? = null)
}

/** Un renglón del desglose (subhead, 48 de alto, la línea arriba). */
@Composable
private fun Renglon(primero: Boolean = false, contenido: @Composable RowScope.() -> Unit) {
    Column {
        if (!primero) LineaFila()
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp), content = contenido)
    }
}

/** El recibo en papel: membrete, «Recibí de X», el importe, «Alquiler y expensas de <mes> ·
 *  propiedad · medio», el sello PAGADO y cuándo. */
@Composable
fun ReciboPapel(cobro: Cobro, nombre: String?, propiedad: String?, inmobiliaria: String, cuando: Instant?) {
    val tinta = hex(0x18201D)
    val gris = hex(0x6B6457)
    val linea = hex(0xE3DDCF)
    val verde = hex(0x2F9E6F)
    val concepto = run {
        val conExpensas = (cobro.conceptos ?: emptyList()).any { it.concepto.lowercase().startsWith("expensas") }
        var t = "${if (conExpensas) "Alquiler y expensas" else "Alquiler"} de ${TextosCobro.nombreMes(cobro.periodo)} · ${propiedad ?: ""}"
        cobro.medio?.let { m -> t += " · ${ReciboVista.medioNombre(m).lowercase()}" }
        t
    }
    val forma = RoundedCornerShape(16.dp)
    Column(
        Modifier.fillMaxWidth().clip(forma).background(hex(0xFAF8F3)).border(BorderStroke(1.dp, linea), forma).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).clip(CircleShape).background(hex(0x1E3A5F)), contentAlignment = Alignment.Center) {
                Texto(inmobiliaria.take(1).uppercase(), Estilo(15f, Peso.bold), hex(0xE9DCC3))
            }
            Texto(inmobiliaria, Estilo(15f, Peso.bold), hex(0x1E3A5F))
            Relleno()
            cobro.numero?.let { Texto(String.format(java.util.Locale.ROOT, "0001-%05d", it), Estilo(12f, Peso.regular), gris) }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(linea))
        Texto("Recibí de ${nombre ?: ""}", Estilo.footnote, gris)
        Texto(Formato.plata(cobro.monto, cobro.moneda), Estilo(30f, Peso.bold, tracking = -0.03f), tinta)
        Texto(Formato.sinViudas(concepto), Estilo.texto14, hex(0x4D5853))
        val l = cobro.conceptos
        if (l != null && l.size > 1) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                l.forEach { x ->
                    Row {
                        Texto(x.concepto, Estilo.footnote, tinta)
                        Relleno()
                        Texto(Formato.plata(x.monto, cobro.moneda), Estilo.footnote, tinta)
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Texto(cuando?.let { "${ReciboPapel.dia(it)} · ${Formato.horaDe(it)}" } ?: Fecha.fecha(cobro.pagadoEl)?.let { Formato.diaCompleto(it) } ?: "",
                Estilo(12f, Peso.regular), gris)
            Relleno()
            Box(Modifier.rotate(-6f).border(BorderStroke(2.dp, verde), RoundedCornerShape(6.dp)).padding(horizontal = 10.dp, vertical = 4.dp)) {
                Texto("PAGADO", Estilo(15f, Peso.extrabold, tracking = 2f / 15f), verde)
            }
        }
    }
}

object ReciboPapel {
    /** «jueves 12/11». */
    fun dia(f: Instant): String = DateTimeFormatter.ofPattern("EEEE dd/MM", Formato.argentina).withZone(Formato.zona).format(f)
}

// MARK: - 10D Recibo

/** 10D El recibo de un cobro ya registrado (desde avisos, la ficha o lo cobrado): el papel, el
 *  mensaje para mandarlo por WhatsApp con el link, «Copiar link» y «PDF». */
@Composable
fun ReciboVista(contrato: String, cobro: String, enHoja: Boolean = false) {
    val estado = LocalEstado.current
    val cerrar = LocalCerrarHoja.current
    val ctx = LocalContext.current
    val alcance = rememberCoroutineScope()
    var c by remember { mutableStateOf<Cobro?>(null) }
    var k by remember { mutableStateOf<ContratoAPI?>(null) }
    var inmo by remember { mutableStateOf<Inmobiliaria?>(null) }
    var url by remember { mutableStateOf<String?>(null) }
    var texto by remember { mutableStateOf("") }
    var enviado by remember { mutableStateOf(false) }
    var noEsta by remember { mutableStateOf(false) }

    /** El link se pide una sola vez por cobro (uno nuevo invalida el anterior). */
    suspend fun armarLink(x: Cobro) {
        try {
            val u = LinksRecibo.de(estado.api, contrato, x.id)
            url = u
            texto = TextosCobro.recibo(k?.contactoNombre, x.periodo, u)
        } catch (e: CancellationException) { throw e } catch (e: Exception) { estado.avisar(e) }
    }

    LaunchedEffect(Unit) { PantallasMostradas.cuenta += 1 }
    LaunchedEffect(contrato, cobro) {
        val l: List<MesContrato> = estado.api.opcional("/v1/contratos/$contrato/cobros") ?: emptyList()
        c = l.mapNotNull { it.cobro }.firstOrNull { it.id == cobro }
        noEsta = c == null
        k = estado.api.opcional("/v1/contratos/$contrato")
        inmo = Documentos.inmobiliaria(estado.api)
    }

    val cuerpo: @Composable ColumnScope.() -> Unit = {
        Column(
            Modifier.fillMaxSize().background(C.superficie).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val x = c
            if (x != null) {
                ReciboPapel(x, k?.contactoNombre, k?.propiedadTitulo, inmo?.nombre ?: "", null)
                val u = url
                if (u != null) {
                    val n = TextosCobro.primerNombre(k?.contactoNombre)
                    MensajeWhatsApp(k?.contactoId, null, k?.contactoNombre, texto, { texto = it }, "recibo",
                        listOf(n, TextosCobro.nombreMes(x.periodo), Formato.plata(x.monto, x.moneda), u),
                        "Quedó en la ficha de $n y del contrato.", enviado, { enviado = it },
                        alListo = { estado.version += 1; if (enHoja) cerrar() else enviado = false }) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            BotonHundido("Copiar link", {
                                copiar(ctx, u)
                                estado.avisar("Link copiado. Pegalo donde quieras mandarlo.")
                            }, Modifier.weight(1f))
                            BotonHundido("PDF", { abrirUrl(ctx, "$u?imprimir=1") }, Modifier.weight(1f))
                        }
                    }
                } else if (estado.escribe) {
                    BotonPrincipal("Mandar por WhatsApp", { alcance.launch { armarLink(x) } }, simbolo = "bubble.left.fill")
                }
                BotonTexto("Imprimir en el teléfono", {
                    alcance.launch { Documentos.recibo(x, contrato = k, api = estado.api)?.let { verArchivo(ctx, it) } }
                }, simbolo = "printer")
            } else if (noEsta) {
                EstadoVacio("doc.text", "Ese recibo ya no está", texto = "El cobro se deshizo o se borró.",
                    accion = "Ver el contrato" to { estado.abrir(AFichaContrato(id = contrato)) })
            } else {
                Box(Modifier.fillMaxWidth().padding(top = 60.dp), contentAlignment = Alignment.Center) { CargandoCobro() }
            }
        }
    }

    if (enHoja) {
        Column(Modifier.fillMaxSize().background(C.superficie)) {
            BarraHoja("Recibo listo", cancelar = null, alCancelar = {}, confirmar = "Listo", alConfirmar = { cerrar() })
            cuerpo()
        }
    } else {
        PantallaApilada("Recibo listo") { Column(Modifier.fillMaxSize().background(C.superficie)) { cuerpo() } }
    }
}

object ReciboVista {
    fun medioNombre(m: String): String =
        mapOf("transferencia" to "Transferencia", "efectivo" to "Efectivo", "mercadopago" to "Mercado Pago")[m] ?: m

    fun medioTexto(m: String): String =
        mapOf("transferencia" to "transferencia", "efectivo" to "efectivo", "mercadopago" to "Mercado Pago")[m] ?: m

    fun mes(p: String): String {
        val d = Fecha.fecha(p.take(7) + "-01") ?: return p
        return "${Formato.mesDe(d)} ${d.year}"
    }
}
