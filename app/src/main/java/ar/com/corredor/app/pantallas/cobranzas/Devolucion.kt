package ar.com.corredor.app.pantallas.cobranzas

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import ar.com.corredor.app.app.*
import ar.com.corredor.app.nucleo.*
import ar.com.corredor.app.pantallas.propiedades.Propiedad
import ar.com.corredor.app.tema.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

/** La devolución comparada con la entrega (web 14E): «¿qué cambió y quién lo paga?». Cada ítem que
 *  cambió muestra la foto de la entrega al lado de la de hoy; lo que paga la parte inquilina se
 *  descuenta del depósito y sale en el acta de devolución. Lo que se elige queda en la API (contra el
 *  acta de entrada): lo ve cualquiera del equipo y entra solo si otro lo cambia. */
@Composable
fun Devolucion(propiedad: String) {
    val estado = LocalEstado.current
    val ctx = LocalContext.current
    val alcance = rememberCoroutineScope()
    var prop by remember { mutableStateOf<Propiedad?>(null) }
    var items by remember { mutableStateOf<List<ItemInventario>>(emptyList()) }
    var entrada by remember { mutableStateOf<Acta?>(null) }
    var salida by remember { mutableStateOf<Acta?>(null) }
    var contrato by remember { mutableStateOf<ContratoAPI?>(null) }
    val decisiones = remember { mutableStateMapOf<String, DecisionDevolucion>() }
    var cargado by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<ErrorApi?>(null) }
    var vista by remember { mutableStateOf("cambios") }
    var espera by remember { mutableStateOf<Job?>(null) }
    var mandando by remember { mutableStateOf(false) }

    val cambios = Inv.comparar(entrada?.items ?: emptyList(), items.map { Inv.comoActa(it) })
    val total = entrada?.items?.size ?: 0
    val moneda = contrato?.moneda ?: "ARS"
    val inquilina = contrato?.contactoNombre?.takeIf { it.isNotBlank() }

    fun decision(c: Inv.Cambio): DecisionDevolucion =
        decisiones[c.clave] ?: DecisionDevolucion(if (c.desgaste) "desgaste" else if (c.empeoro) "inquilino" else "dueno", null)

    val cobrados = cambios.filter { decision(it).quien == "inquilino" && (decision(it).importe ?: 0.0) > 0 }
    val descuentos = cobrados.sumOf { decision(it).importe ?: 0.0 }
    val devolver = contrato?.deposito?.let { maxOf(0.0, it - descuentos) }
    /** Lo que no cambió (para «Todo»). */
    val iguales = run {
        val conCambio = cambios.mapNotNull { c -> c.entrega?.let { Inv.clave(it.ambiente, it.descripcion) } }.toSet()
        (entrada?.items ?: emptyList()).filter { Inv.clave(it.ambiente, it.descripcion) !in conCambio }
    }

    /** Se guarda solo, al ratito de elegir (el importe se escribe de a una cifra). */
    fun guardar() {
        espera?.cancel()
        val e = entrada ?: return
        val ruta = "/v1/propiedades/$propiedad/actas/${e.id}/devolucion"
        espera = alcance.launch {
            delay(600)
            val todo: Map<String, DecisionDevolucion> = decisiones.toMap()
            try {
                estado.cola.enviar("PUT", ruta, todo, etiqueta = "Devolución de ${prop?.direccion ?: ""}")
            } catch (x: CancellationException) { throw x } catch (x: Exception) {
                Inv.errorConReintento(estado, x) { guardar() }
            }
            espera = null
        }
    }

    fun elegir(c: Inv.Cambio, quien: String) {
        decisiones[c.clave] = decision(c).copy(quien = quien)
        guardar()
    }

    fun importe(c: Inv.Cambio, texto: String) {
        val n = texto.filter { it.isDigit() }.toDoubleOrNull() ?: 0.0
        decisiones[c.clave] = decision(c).copy(importe = if (n > 0) n else null)
        guardar()
    }

    suspend fun cargar() {
        val p: Propiedad
        val l: List<ItemInventario>
        val actas: List<Acta>
        try {
            p = estado.api.get("/v1/propiedades/$propiedad")
            l = estado.api.get("/v1/propiedades/$propiedad/inventario")
            actas = estado.api.get("/v1/propiedades/$propiedad/actas")
        } catch (e: CancellationException) { throw e } catch (e: ErrorApi) {
            if (!cargado) error = e
            return
        } catch (e: Exception) { return }
        prop = p
        items = l
        val e = actas.firstOrNull { it.tipo == "entrada" }
        entrada = e
        salida = actas.firstOrNull { it.tipo == "salida" && (e?.contratoId == null || it.contratoId == e.contratoId) }
        if (e != null) {
            val d: Map<String, DecisionDevolucion> = estado.api.opcional("/v1/propiedades/$propiedad/actas/${e.id}/devolucion") ?: emptyMap()
            // Lo que otro cambió entra solo, salvo que justo haya algo propio por guardar.
            if (espera == null) { decisiones.clear(); decisiones.putAll(d) }
            val c = e.contratoId
            if (c != null) {
                contrato = estado.api.opcional("/v1/contratos/$c")
            } else {
                val ks: List<ContratoAPI> = estado.api.opcional("/v1/contratos", listOf("propiedad" to propiedad, "limite" to "5")) ?: emptyList()
                contrato = ks.firstOrNull()
            }
        }
        error = null
        cargado = true
    }

    /** Labra el acta de salida con las observaciones armadas (cada descuento con su importe y quién lo
     *  paga, y la cuenta del depósito) y lleva a firmarla. */
    suspend fun mandar() {
        val e = entrada ?: return
        mandando = true
        try {
            val lineas = cobrados.map { "${Inv.rotulo(it.ambiente, it.descripcion)}: ${Formato.plata(decision(it).importe ?: 0.0, moneda)} a cargo de la parte inquilina." }
            val otros = cambios.filter { decision(it).quien != "inquilino" }.map {
                "${Inv.rotulo(it.ambiente, it.descripcion)}: ${if (decision(it).quien == "desgaste") "desgaste por uso, no se descuenta" else "a cargo del dueño"}."
            }
            var pie = ""
            contrato?.deposito?.let { dep ->
                pie = "Depósito ${Formato.plata(dep, moneda)}; descuentos ${Formato.plata(descuentos, moneda)}; a devolver ${Formato.plata(devolver ?: 0.0, moneda)}."
            }
            val obs = (lineas + otros + listOf(pie)).filter { it.isNotEmpty() }.joinToString("\n")
            val a: Acta = estado.api.enviar("POST", "/v1/propiedades/$propiedad/actas",
                CuerpoSalida(contratoId = e.contratoId, observaciones = if (obs.isEmpty()) null else obs.take(5000)))
            estado.version += 1
            estado.abrir(AFirmas(id = "$propiedad/${a.id}"))
        } catch (x: CancellationException) { throw x } catch (x: Exception) {
            Inv.errorConReintento(estado, x) { alcance.launch { mandar() } }
        } finally { mandando = false }
    }

    suspend fun actaPDF() {
        val s = salida ?: return
        val a: Acta = estado.api.opcional("/v1/propiedades/$propiedad/actas/${s.id}") ?: s
        val inmo: InmobiliariaActa? = estado.api.opcional("/v1/inmobiliaria")
        var prop2: ContactoActa? = null
        (contrato?.propietarioId ?: prop?.propietarioId)?.let { pid -> prop2 = estado.api.opcional("/v1/contactos/$pid") }
        val nombreInmo = inmo?.nombre ?: estado.inmobiliaria?.nombre ?: ""
        val u = Documentos.actaFirmada(a, prop?.direccion ?: prop?.nombre ?: "", prop?.barrio, inmo, contrato?.contactoNombre,
            prop2?.nombre ?: contrato?.propietarioNombre, "Por ${nombreInmo.replace("Propiedades", "Prop.")}", estado.api)
        if (u != null) verArchivo(ctx, u) else estado.avisar("No pudimos armar el PDF. Probá de nuevo.", ok = false)
    }

    // MARK: Textos

    /** «12/03/26». */
    fun fechaCorta(s: String?): String {
        val n = Inv.numerica(s)
        return if (n.length == 10) n.take(6) + n.takeLast(2) else n
    }

    fun tituloEstado(i: Acta.Item): String = Inv.nombre(i.estado) + (if ((i.cantidad ?: 1) > 1) " · ${i.cantidad ?: 1}" else "")

    fun textoHoy(c: Inv.Cambio): String {
        val h = c.hoy ?: return "Hoy · no está"
        val e = c.entrega
        if (e != null && (h.cantidad ?: 1) != (e.cantidad ?: 1)) return "Hoy · ${Formato.cantidad(h.cantidad ?: 1, "unidad", "unidades")}"
        return "Hoy · ${Inv.nombre(h.estado)}"
    }

    fun grave(c: Inv.Cambio): Boolean {
        val h = c.hoy ?: return true
        val e = c.entrega ?: return false
        return (h.cantidad ?: 1) < (e.cantidad ?: 1) || (Inv.grupo(h.estado) == "malo" && Inv.grupo(e.estado) == "bueno")
    }

    fun lineaContrato(): String {
        var s = prop?.direccion ?: prop?.nombre ?: ""
        inquilina?.let { s += " · inquilina $it" }
        contrato?.let { k -> s += " · contrato ${(k.inicio ?: "").take(4)}-${(k.fin ?: "").take(4)}" }
        return s
    }

    LaunchedEffect(estado.version) { cargar() }

    @Composable
    fun figura(foto: String?, pie: String, tinta: Color, grave: Boolean, modifier: Modifier) {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(
                Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(RoundedCornerShape(12.dp))
                    .border(2.dp, if (grave) C.error else Color.Transparent, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (foto != null) Foto(urlDeFoto(foto, ancho = 500), Modifier.fillMaxSize())
                else Box(Modifier.fillMaxSize().background(C.hundida), contentAlignment = Alignment.Center) { Icono("photo", 20f, C.texto3) }
            }
            Texto(pie, Estilo(12f, Peso.semibold), tinta, lineas = 2)
        }
    }

    PantallaApilada("Devolución", acciones = if (salida != null) ({ BotonBarraActa("arrow.down.doc", "Acta PDF") { alcance.launch { actaPDF() } } }) else null) {
        Refrescable({ cargar() }) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                val err = error
                val ent = entrada
                if (!cargado && err != null) {
                    EstadoError("la devolución", err) { alcance.launch { cargar() } }
                } else if (!cargado) {
                    EsqueletoFilas(filas = 5)
                } else if (ent == null) {
                    Box(Modifier.padding(top = 30.dp)) {
                        EstadoVacio("checklist", "Todavía no hay acta de entrega",
                            "Para comparar la devolución hace falta la recorrida de entrega con su acta.",
                            accion = "Hacer la recorrida de entrega" to { estado.abrir(ARecorrida(id = propiedad, tipo = "entrega")) })
                    }
                } else {
                    // Cabeza
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Texto(lineaContrato(), Estilo.footnote, C.texto2)
                        Texto("Devolución · ${Formato.cantidad(cambios.size, "cambio", "cambios")} de ${Formato.cantidad(total, "ítem", "ítems")}",
                            Estilo(22f, Peso.bold, -0.02f), C.texto)
                    }
                    Segmentado(listOf("cambios" to "Sólo cambios ${cambios.size}", "todo" to "Todo $total"), vista, { vista = it })
                    // Cambios
                    if (cambios.isEmpty()) {
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.okFondo).padding(14.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top,
                        ) {
                            Icono("checkmark.circle.fill", 22f, C.okTinta)
                            BasicText(buildAnnotatedString {
                                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("Está todo como en la entrega.") }
                                append(" Si recorriste la casa, labrá el acta de devolución y se devuelve el depósito entero.")
                            }, style = Estilo.texto14.ts().copy(color = C.texto))
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            cambios.forEach { c ->
                                Column(Modifier.fillMaxWidth().tarjeta().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                                        figura(c.entrega?.fotosUrl?.firstOrNull(),
                                            c.entrega?.let { "Entrega ${fechaCorta(ent.fecha)} · ${tituloEstado(it)}" } ?: "No estaba en la entrega",
                                            C.texto2, false, Modifier.weight(1f))
                                        figura(c.hoy?.fotosUrl?.lastOrNull(), textoHoy(c), if (grave(c)) C.errorTinta else C.avisoTinta, grave(c), Modifier.weight(1f))
                                    }
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Texto(Inv.rotulo(c.ambiente, c.descripcion), Estilo.subheadFuerte, C.texto)
                                        c.hoy?.notas?.takeIf { it.isNotBlank() }?.let { Texto(it, Estilo.footnote, C.texto2) }
                                    }
                                    if (c.entrega != null) {
                                        // ¿Quién paga?
                                        val d = decision(c)
                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Texto("¿Quién paga?", Estilo.footnoteFuerte, C.texto2)
                                            Box(Modifier.deshabilitado(!estado.escribe)) {
                                                Segmentado(
                                                    if (c.desgaste) listOf("inquilino" to "Inquilina", "desgaste" to "Desgaste") else listOf("inquilino" to "Inquilina", "dueno" to "Dueño"),
                                                    d.quien, { elegir(c, it) },
                                                )
                                            }
                                            if (d.quien == "inquilino") {
                                                Row(
                                                    Modifier.fillMaxWidth().heightIn(min = 46.dp).clip(RoundedCornerShape(12.dp)).background(C.hundida).padding(horizontal = 14.dp),
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
                                                ) {
                                                    Texto(moneda, Estilo.subheadFuerte, C.texto2)
                                                    CampoCobro(d.importe?.let { Formato.numero(it.toInt()) } ?: "", { importe(c, it) }, "0",
                                                        Modifier.weight(1f).semantics { contentDescription = "Importe a descontar" },
                                                        teclado = KeyboardType.Number, estilo = Estilo.body16, marco = false, habilitado = estado.escribe)
                                                }
                                            } else {
                                                Texto("no se descuenta", Estilo.footnote, C.texto3)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (vista == "todo") {
                        Column(Modifier.fillMaxWidth().tarjeta()) {
                            iguales.forEachIndexed { i, it ->
                                if (i > 0) LineaFila()
                                Row(
                                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(C.hundida)) {
                                        it.fotosUrl?.firstOrNull()?.let { f -> Foto(urlDeFoto(f), Modifier.fillMaxSize()) }
                                    }
                                    Texto(Inv.rotulo(it.ambiente, it.descripcion), Estilo.texto14, C.texto, Modifier.weight(1f), lineas = 2)
                                    Texto("Igual · ${Inv.nombre(it.estado)}", Estilo.footnote, C.texto2)
                                }
                            }
                        }
                    }
                    // Depósito
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(C.verde800).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        val blanco = Color.White
                        Texto("DEPÓSITO EN GARANTÍA", Estilo(12f, Peso.bold, 0.05f), C.oro)
                        val dep = contrato?.deposito
                        if (dep != null) {
                            Row { Texto("Depósito", Estilo.subhead, blanco, Modifier.weight(1f)); Texto(Formato.plata(dep, moneda), Estilo.subhead, blanco) }
                            Row {
                                Texto("Descuentos (${cobrados.size})", Estilo.subhead, blanco, Modifier.weight(1f))
                                Texto(if (descuentos > 0) "−" + Formato.numero(descuentos.toInt()) else "0", Estilo.subhead, blanco)
                            }
                            Box(Modifier.fillMaxWidth().height(1.dp).background(blanco.copy(alpha = 0.2f)))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Texto("A devolver${inquilina?.let { " a ${Inv.primerNombre(it)}" } ?: ""}", Estilo(15f, Peso.bold), blanco, Modifier.weight(1f))
                                Texto(Formato.numero((devolver ?: 0.0).toInt()), Estilo(22f, Peso.bold), blanco)
                            }
                        } else {
                            Texto(Formato.sinViudas("El contrato no tiene el depósito cargado. Los descuentos (${Formato.plata(descuentos, moneda)}) igual salen en el acta."),
                                Estilo.subhead, blanco)
                        }
                    }
                    // Antes de firmar
                    Column(Modifier.fillMaxWidth().tarjeta().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Texto("Antes de firmar", Estilo.subheadFuerte, C.texto)
                        Texto(Formato.sinViudas("Las fotos de la entrega y las de hoy van al acta, ítem por ítem."), Estilo.footnote, C.texto2)
                        Texto(Formato.sinViudas("Lo que se descuenta sale en el acta de devolución con su importe."), Estilo.footnote, C.texto2)
                    }
                    // Botones
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val s = salida
                        if (s != null) {
                            BotonPrincipal("Ver el acta de devolución", { estado.abrir(AFirmas(id = "$propiedad/${s.id}")) })
                            BotonHundido("Acta PDF", { alcance.launch { actaPDF() } }, simbolo = "arrow.down.doc")
                        } else if (estado.escribe) {
                            BotonPrincipal(if (mandando) "Labrando el acta…" else "Mandar acta para firmar", { alcance.launch { mandar() } }, habilitado = !mandando)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            BotonHundido("Recorrida de devolución", { estado.abrir(ARecorrida(id = propiedad, tipo = "devolucion")) }, Modifier.weight(1f), simbolo = "figure.walk")
                            BotonHundido("Acta de entrega", { estado.abrir(AFirmas(id = "$propiedad/${ent.id}")) }, Modifier.weight(1f), simbolo = "doc.text")
                        }
                    }
                }
            }
        }
    }
}

@Serializable private data class CuerpoSalida(val tipo: String = "salida", val contratoId: String? = null, val observaciones: String? = null)
