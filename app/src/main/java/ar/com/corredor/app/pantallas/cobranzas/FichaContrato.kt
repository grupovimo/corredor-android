package ar.com.corredor.app.pantallas.cobranzas

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import ar.com.corredor.app.app.*
import ar.com.corredor.app.nucleo.*
import ar.com.corredor.app.tema.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.Instant
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/** GET /v1/contratos/{id}/ajuste: el alquiler de hoy (`vigente`), el próximo ajuste, el cronograma y a
 *  quién ya se le avisó (parte → cuándo, RFC 3339). */
@Serializable
data class AjusteContrato(
    val indice: String? = null,
    val base: Double? = null,
    val vigente: Double? = null,
    val vigenteEstimado: Boolean? = null,
    val ultimoAjuste: String? = null,
    val proximo: Proximo? = null,
    val cronograma: List<Paso>? = null,
    val faltanDatos: Boolean? = null,
    val indiceUltimoPeriodo: String? = null,
    val avisos: Map<String, String>? = null,
) {
    @Serializable data class Proximo(val fecha: String, val monto: Double? = null)
    @Serializable data class Paso(val fecha: String, val monto: Double? = null, val factor: Double? = null, val futuro: Boolean? = null, val estimado: Boolean? = null)
}

/** Un documento del contrato (GET /v1/documentos?contrato=). */
@Serializable
data class DocumentoContrato(
    val id: String,
    val archivoId: String? = null,
    val nombre: String,
    val tipo: String? = null,
    val bytes: Int? = null,
    val creadoEn: String? = null,
)

@Stable
private class FichaEstado {
    var k by mutableStateOf<ContratoAPI?>(null)
    var meses by mutableStateOf<List<MesContrato>>(emptyList())
    var rendiciones by mutableStateOf<List<RendicionAPI>>(emptyList())
    var ajuste by mutableStateOf<AjusteContrato?>(null)
    var serie by mutableStateOf<List<FichaContrato.Punto>>(emptyList())
    var documentos by mutableStateOf<List<DocumentoContrato>>(emptyList())
    var sacados by mutableStateOf<Set<String>>(emptySet())
    var inquilino by mutableStateOf<FichaContrato.Persona?>(null)
    var propietario by mutableStateOf<FichaContrato.Persona?>(null)
    var cobrando by mutableStateOf<ACobrar?>(null)
    var mensaje by mutableStateOf<PedidoMensaje?>(null)
    var renovando by mutableStateOf(false)
    var error by mutableStateOf<ErrorApi?>(null)
    var recordado = false
    var rindiendo by mutableStateOf<String?>(null)
    var subiendo by mutableStateOf(false)

    val moneda: String get() = k?.moneda ?: "ARS"
    val ultimos: List<MesContrato> get() = meses.takeLast(6).reversed()
    val debe: List<MesContrato> get() = meses.filter { it.estado == "atrasado" }
    val nInq: String get() = TextosCobro.primerNombre(inquilino?.nombre ?: k?.contactoNombre)
    val nProp: String get() = TextosCobro.primerNombre(propietario?.nombre ?: k?.propietarioNombre)
}

@Serializable private data class CuerpoContactoFicha(val contactoId: String)
@Serializable private data class ConvFicha(val id: String)
@Serializable private data class DocFicha(val archivoId: String, val contratoId: String, val nombre: String)
@Serializable private data class ParteFicha(val parte: String)
@Serializable private data class PeriodoFicha(val periodo: String)
@Serializable private data class NuevaFicha(val id: String)
@Serializable private data class EstadoFicha(val estado: String)

/** La ficha de un contrato (22E): el plazo, el próximo ajuste calculado y explicado, el historial de
 *  ajustes, los cobros de los últimos meses (con recordar, cobrar, rendir y el recibo), las partes,
 *  las condiciones y los documentos. */
@Composable
fun FichaContrato(
    id: String,
    /** «AAAA-MM»: abre el recordatorio de ese mes (el centro de avisos manda acá). */
    recordar: String? = null,
) {
    val estado = LocalEstado.current
    val ctx = LocalContext.current
    val alcance = rememberCoroutineScope()
    val s = remember(id) { FichaEstado() }
    val hoy = Formato.hoy().toString()

    fun aCobrar(m: MesContrato, cobro: Cobro? = null): ACobrar {
        val k = s.k
        return ACobrar(contratoId = id, contactoId = k?.contactoId, nombre = k?.contactoNombre, telefono = s.inquilino?.telefono, propiedad = k?.propiedadTitulo,
            periodo = m.periodo, esperado = m.cobro?.monto ?: m.esperado ?: 0.0, moneda = s.moneda, vence = m.vence ?: m.periodo,
            punitorioDiario = k?.punitorioDiario, cobro = cobro)
    }

    /** Sin mes: la deuda más vieja o el próximo mes pendiente; si no hay nada, «Está todo cobrado». */
    fun cobrar(m: MesContrato?) {
        val mes = m ?: s.debe.firstOrNull() ?: s.ultimos.reversed().firstOrNull { it.estado == "pendiente" }
        if (mes == null) {
            estado.avisar("Está todo cobrado hasta este mes.")
            return
        }
        s.cobrando = aCobrar(mes)
    }

    /** El mensaje salió: queda anotado a quién se le avisó (POST …/ajuste/avisos {parte}). */
    suspend fun anotarAviso(parte: String) {
        val antes = s.ajuste?.avisos
        s.ajuste = s.ajuste?.copy(avisos = (s.ajuste?.avisos ?: emptyMap()) + (parte to Instant.now().toString()))
        try {
            estado.api.hacer("POST", "/v1/contratos/$id/ajuste/avisos", ParteFicha(parte))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            s.ajuste = s.ajuste?.copy(avisos = antes)
            estado.sugerir("El mensaje salió, pero no quedó anotado el aviso.", "Reintentar") { alcance.launch { anotarAviso(parte) } }
        }
    }

    fun avisarAjuste(parte: String) {
        val k = s.k ?: return
        val a = s.ajuste ?: return
        val p = a.proximo ?: return
        val nuevo = p.monto ?: return
        val persona = if (parte == "inquilino") s.inquilino else s.propietario
        val nombre = persona?.nombre ?: (if (parte == "inquilino") k.contactoNombre else k.propietarioNombre)
        val n = TextosCobro.primerNombre(nombre)
        val vigente = a.vigente ?: k.monto ?: 0.0
        val moneda = s.moneda
        s.mensaje = PedidoMensaje(
            titulo = "Avisarle a ${if (n.isEmpty()) (if (parte == "inquilino") "el inquilino" else "el propietario") else n}",
            descripcion = "${k.propiedadTitulo ?: ""} · ajusta el ${TextosCobro.fechaCortita(p.fecha)}",
            contactoId = persona?.id ?: (if (parte == "inquilino") k.contactoId else k.propietarioId), telefono = persona?.telefono, nombre = nombre,
            texto = TextosCobro.ajuste(nombre, k.ajusteIndice ?: "", p.fecha, vigente, nuevo, moneda),
            para = "ajuste", parametros = listOf(n, TextosCobro.fechaCortita(p.fecha), Formato.plata(vigente, moneda), Formato.plata(nuevo, moneda)),
            alEnviar = { alcance.launch { anotarAviso(parte) } },
        )
    }

    fun recordarMes(periodo: String) {
        val k = s.k ?: return
        val m = s.meses.firstOrNull { it.periodo.startsWith(periodo) }
        val importe = m?.esperado ?: s.ajuste?.vigente ?: k.monto ?: 0.0
        val vence = m?.vence ?: "$periodo-${"%02d".format(k.diaVencimiento ?: 1)}"
        val moneda = s.moneda
        s.mensaje = PedidoMensaje(
            titulo = "Recordarle a ${if (s.nInq.isEmpty()) "el inquilino" else s.nInq}",
            descripcion = "${Formato.mayuscula(TextosCobro.nombreMes(periodo))} · ${k.propiedadTitulo ?: ""} · ${Formato.plata(importe, moneda)}",
            contactoId = k.contactoId, telefono = s.inquilino?.telefono, nombre = k.contactoNombre,
            texto = TextosCobro.recordatorio(k.contactoNombre, "$periodo-01", importe, moneda, vence, hoy),
            para = "recordatorio", parametros = listOf(s.nInq, TextosCobro.nombreMes(periodo), Formato.plata(importe, moneda), TextosCobro.fechaCortita(vence)),
            listo = "Quedó en la ficha de ${s.nInq} y en la bandeja.",
        )
    }

    /** Preparar la rendición de un mes cobrado y abrirla para revisarla. */
    suspend fun rendir(periodo: String) {
        s.rindiendo = periodo
        try {
            val n: NuevaFicha = estado.api.enviar("POST", "/v1/contratos/$id/rendiciones", PeriodoFicha(periodo.take(7)))
            estado.version += 1
            estado.abrir(ARendicion(contrato = id, id = n.id))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            estado.avisar(e)
        } finally {
            s.rindiendo = null
        }
    }

    suspend fun rescindir() {
        try {
            estado.api.hacer("PATCH", "/v1/contratos/$id", EstadoFicha("rescindido"))
            estado.version += 1
            estado.avisar("Contrato rescindido") {
                alcance.launch {
                    try { estado.api.hacer("PATCH", "/v1/contratos/$id", EstadoFicha("vigente")) } catch (e: CancellationException) { throw e } catch (e: Exception) { estado.avisar(e) }
                    estado.version += 1
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            estado.avisar(e)
        }
    }

    /** El chat de WhatsApp con la parte (en la bandeja de Corredor; sin número conectado, el teléfono). */
    fun chat(contacto: String, telefono: String?) {
        alcance.launch {
            try {
                val x: ConvFicha = estado.api.enviar("POST", "/v1/whatsapp/conversaciones", CuerpoContactoFicha(contacto))
                estado.abrir(AChat(id = x.id))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (telefono != null) Afuera.whatsapp(telefono) else estado.avisar(e)
            }
        }
    }

    suspend fun abrirDoc(d: DocumentoContrato) {
        val a = d.archivoId ?: return
        try {
            val f = Documentos.archivo("/v1/archivos/$a/contenido", nombre = d.nombre, api = estado.api)
            verArchivo(ctx, f, if (d.tipo?.contains("pdf") == true || d.nombre.lowercase().endsWith(".pdf")) "application/pdf" else (d.tipo ?: "*/*"))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            estado.avisar(e)
        }
    }

    /** Sacar un documento (a la papelera); «Deshacer» lo vuelve a sumar con el mismo archivo. */
    fun sacar(d: DocumentoContrato) {
        s.sacados = s.sacados + d.id
        val pedido = CompletableDeferred<Boolean>()
        alcance.launch {
            try {
                estado.api.hacer("DELETE", "/v1/documentos/${d.id}")
                pedido.complete(true)
            } catch (e: CancellationException) {
                pedido.complete(false)
                throw e
            } catch (e: Exception) {
                s.sacados = s.sacados - d.id
                estado.avisar("No se sacó · ${(e as? ErrorApi)?.detalle ?: ""}", ok = false)
                pedido.complete(false)
            }
        }
        estado.avisar("Sacaste «${d.nombre}»") {
            alcance.launch {
                if (!pedido.await()) return@launch
                val a = d.archivoId ?: return@launch
                try {
                    estado.api.hacer("POST", "/v1/documentos", DocFicha(a, id, d.nombre))
                } catch (e: CancellationException) { throw e } catch (e: Exception) { estado.avisar(e) }
                s.sacados = s.sacados - d.id
                estado.version += 1
            }
        }
    }

    suspend fun subir(datos: ByteArray, nombre: String, tipo: String) {
        s.subiendo = true
        try {
            val a = estado.api.subir(datos, nombre = nombre, tipo = tipo, uso = "documento")
            estado.api.hacer("POST", "/v1/documentos", DocFicha(a.id, id, a.nombre ?: nombre))
            estado.version += 1
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            estado.avisar("No se guardó el documento · ${(e as? ErrorApi)?.detalle ?: ""}", ok = false)
        } finally {
            s.subiendo = false
        }
    }

    suspend fun cargar() {
        try {
            val c: ContratoAPI = estado.api.get("/v1/contratos/$id")
            s.meses = estado.api.opcional("/v1/contratos/$id/cobros") ?: emptyList()
            s.rendiciones = estado.api.opcional("/v1/contratos/$id/rendiciones") ?: emptyList()
            s.ajuste = estado.api.opcional("/v1/contratos/$id/ajuste")
            s.documentos = estado.api.opcional("/v1/documentos", listOf("contrato" to id)) ?: emptyList()
            s.sacados = emptySet()
            s.inquilino = c.contactoId?.let { estado.api.opcional<FichaContrato.Persona>("/v1/contactos/$it") }
            s.propietario = c.propietarioId?.let { estado.api.opcional<FichaContrato.Persona>("/v1/contactos/$it") }
            val i = c.ajusteIndice
            val a = s.ajuste
            if (i != null && i != "fijo" && a != null && a.proximo != null) {
                val desde = (a.ultimoAjuste ?: c.inicio ?: "").take(8) + "01"
                s.serie = estado.api.opcional("/v1/indices/$i", listOf("desde" to desde)) ?: emptyList()
            }
            s.k = c
            s.error = null
            if (recordar != null && !s.recordado && estado.escribe) {
                s.recordado = true
                recordarMes(recordar.take(7))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: ErrorApi) {
            if (s.k == null) s.error = e
        } catch (e: Exception) {
        }
    }

    LaunchedEffect(estado.version) { cargar() }

    val elegirArchivos = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { us ->
        alcance.launch {
            for (u in us) {
                val a = leerUriAlta(ctx, u)
                if (a == null) estado.avisar("No se pudo leer el archivo.", ok = false) else subir(a.first, a.second, a.third)
            }
        }
    }
    val elegirFoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { u ->
        if (u != null) alcance.launch {
            val a = leerUriAlta(ctx, u) ?: return@launch
            subir(aJpegAlta(a.first), "Foto ${Formato.hoy().toString()}.jpg", "image/jpeg")
        }
    }

    PantallaApilada("Contrato") {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            val k = s.k
            val error = s.error
            if (k != null) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (estado.escribe) VistaConsejo(ConsejoCobrar())
                    CabeceraFicha(k, estado.escribe, cobrar = { cobrar(null) }, renovar = { s.renovando = true })
                    val a = s.ajuste
                    val p = a?.proximo
                    if (a != null && p != null) TarjetaAjusteFicha(s, k, a, p, estado.escribe, hoy, ::avisarAjuste)
                    if (a != null && !a.cronograma.isNullOrEmpty()) HistorialFicha(k, a)
                    if (k.tipo == "alquiler") {
                        BloqueCobrosFicha(s, k, estado.escribe,
                            verCobranzas = { estado.abrir(ACobranzas) },
                            rendicion = { r -> estado.abrir(ARendicion(contrato = id, id = r.id)) },
                            rendir = { p2 -> alcance.launch { rendir(p2) } },
                            recibo = { m, c -> if (c.compartido == true) estado.abrir(ARecibo(contrato = id, cobro = c.id)) else s.cobrando = aCobrar(m, c) },
                            recordar = { recordarMes(it) },
                            cobrar = { cobrar(it) })
                    }
                    PartesFicha(s, k,
                        abrirContacto = { estado.abrir(AFichaContacto(id = it)) },
                        chat = { c, t -> chat(c, t) },
                        llamar = { Afuera.llamar(k.garanteTelefono) })
                    CondicionesFicha(k, s.moneda)
                    BloqueDocumentosFicha(s, estado.escribe,
                        subirArchivo = { elegirArchivos.launch(arrayOf("application/pdf", "image/*")) },
                        subirFoto = { elegirFoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        abrir = { alcance.launch { abrirDoc(it) } },
                        sacar = { sacar(it) })
                    if (k.tipo != "venta" && s.rendiciones.isNotEmpty()) BloqueRendicionesFicha(s) { r -> estado.abrir(ARendicion(contrato = id, id = r.id)) }
                    // Otras acciones.
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        k.propiedadId?.let { p2 -> BotonHundido("Inventario y actas", { estado.abrir(AInventario(id = p2)) }) }
                        BotonHundido("Ver o imprimir el contrato", {
                            alcance.launch { Documentos.contrato(k, api = estado.api)?.let { verArchivo(ctx, it) } }
                        })
                        if (estado.escribe && k.estado != "rescindido") {
                            BotonTexto("Rescindir", { alcance.launch { rescindir() } }, color = C.error)
                        }
                    }
                }
            } else if (error != null) {
                Box(Modifier.padding(top = 40.dp)) { EstadoError("el contrato", error) { alcance.launch { cargar() } } }
            } else {
                Box(Modifier.fillMaxWidth().padding(top = 60.dp), contentAlignment = Alignment.Center) { CargandoCobro() }
            }
        }
    }

    Hoja(visible = s.renovando, alCerrar = { s.renovando = false }) { AltaContrato(renovar = id) }
    HojaDe(s.cobrando, { s.cobrando = null }) { c -> HojaCobro(item = c) }
    hojaMensaje(s.mensaje) { s.mensaje = null }
}

// MARK: Cabecera

private fun plazoFicha(k: ContratoAPI): String {
    val ini = k.inicio ?: ""
    val fmt: (String) -> String = { "${TextosCobro.fechaCortita(it)}/${it.take(4)}" }
    val p = mutableListOf(if (k.tipo == "alquiler") "Alquiler" else "Venta")
    val fin = k.fin
    if (fin != null) {
        p.add("${AltaContrato.mesesEntre(ini, fin)} meses")
        p.add("del ${fmt(ini)} al ${fmt(fin)}")
    } else {
        p.add("desde el ${fmt(ini)}")
    }
    return p.joinToString(" · ")
}

@Composable
private fun CabeceraFicha(k: ContratoAPI, escribe: Boolean, cobrar: () -> Unit, renovar: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Texto(plazoFicha(k), Estilo.footnote, C.texto2)
            Texto(k.propiedadTitulo ?: "Contrato sin propiedad", Estilo.titulo, C.texto)
        }
        if (escribe) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (k.tipo == "alquiler") BotonHundido("Registrar cobro", cobrar, Modifier.weight(1f), simbolo = "dollarsign.circle")
                BotonHundido("Renovar", renovar, Modifier.weight(1f), simbolo = "arrow.triangle.2.circlepath")
            }
        }
    }
}

// MARK: Próximo ajuste

@Composable
private fun TarjetaAjusteFicha(s: FichaEstado, k: ContratoAPI, a: AjusteContrato, p: AjusteContrato.Proximo, escribe: Boolean, hoy: String, avisar: (String) -> Unit) {
    val moneda = s.moneda
    val dias = Cobranzas.diasEntre(hoy, p.fecha)
    val cuando = if (dias <= 0) "hoy" else if (dias == 1) "mañana" else "en $dias días"
    val vigente = a.vigente ?: k.monto ?: 0.0
    val sube = if ((p.monto ?: 0.0) > 0 && vigente > 0) (p.monto!! / vigente - 1) else 0.0
    val claro = hex(0xCFE0D8)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(M.rDestacada)).texturaVerde(C.verde800).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Etiqueta("Próximo ajuste · $cuando · ${Fecha.fecha(p.fecha)?.let { Formato.diaCompleto(it) } ?: p.fecha}", "chart.line.uptrend.xyaxis", Estilo.footnoteFuerte, C.oro)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Texto("Hoy", Estilo.caption, claro)
                Texto(Formato.plata(vigente, moneda), Estilo.subheadFuerte, Color.White)
            }
            Icono("arrow.right", 20f, C.oro)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Texto("Desde ${TextosCobro.nombreMes(p.fecha)}", Estilo.caption, claro)
                Texto(p.monto?.let { Formato.plata(it, moneda) } ?: "a calcular", Estilo.titulo3, C.oro)
            }
            if (p.monto != null) Texto("+${FichaContrato.dec(sube * 100, 1)} %", Estilo.subheadFuerte, Color.White)
        }
        ExplicacionFicha(s, k, a, p)
        if (escribe && p.monto != null) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (k.contactoId != null) {
                    BotonPrincipal("Avisarle a ${if (s.nInq.isEmpty()) "el inquilino" else s.nInq}", { avisar("inquilino") }, simbolo = "bubble.left.fill")
                }
                if (k.propietarioId != null) {
                    val forma = RoundedCornerShape(12.dp)
                    Box(Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(forma).border(BorderStroke(1.dp, Color.White.copy(alpha = 0.5f)), forma)
                        .clickable { avisar("propietario") }, contentAlignment = Alignment.Center) {
                        Texto("Avisar a ${if (s.nProp.isEmpty()) "el propietario" else s.nProp}", Estilo.subheadFuerte, Color.White)
                    }
                }
            }
        }
        AvisadosFicha(s)
    }
}

/** «Cómo se calcula.» (ICL y UVA: el coeficiente del día; IPC: la inflación acumulada; fijo). */
@Composable
private fun ExplicacionFicha(s: FichaEstado, k: ContratoAPI, a: AjusteContrato, p: AjusteContrato.Proximo) {
    val moneda = s.moneda
    val vigente = a.vigente ?: k.monto ?: 0.0
    val m = p.monto
    val indice = k.ajusteIndice
    val tinta = hex(0xE8EFE9)
    val caja = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.08f)).padding(12.dp)
    if (m != null && vigente > 0 && indice != null) {
        val desde = a.ultimoAjuste ?: k.inicio ?: ""
        val factor = m / vigente
        val nombre = TextosCobro.nombreIndice[indice] ?: ""
        val cuenta = "${Formato.plata(vigente, moneda)} × ${FichaContrato.dec(factor, 3)}."
        Column(caja, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (indice == "fijo") {
                TextoComoFicha("Aumento fijo del ${TextosCobro.numero(k.ajustePorcentaje ?: 0.0)} % ${k.indiceCada.split("fijo ").last()}. $cuenta", tinta)
            } else if (indice == "ipc") {
                TextoComoFicha("IPC acumulado de ${TextosCobro.nombreMes(desde)} a ${TextosCobro.nombreMes(p.fecha)} = ${FichaContrato.dec(factor, 3)}. $cuenta", tinta)
                if (a.cronograma?.lastOrNull()?.estimado == true) NotaEstimadoFicha(indice)
            } else {
                val v0 = FichaContrato.valorEn(s.serie, desde)
                val v1 = FichaContrato.valorEn(s.serie, p.fecha)
                if (v0 != null && v1 != null) {
                    val estimado = v1.periodo < p.fecha
                    TextoComoFicha("$nombre del ${TextosCobro.fechaCortita(p.fecha)} (${if (estimado) "estimado " else ""}${FichaContrato.dec(v1.valor)}) dividido el $nombre del ${TextosCobro.fechaCortita(desde)} (${FichaContrato.dec(v0.valor)}) = ${FichaContrato.dec(factor, 3)}. $cuenta", tinta)
                    if (estimado) NotaEstimadoFicha(indice)
                }
            }
        }
    } else if (a.faltanDatos == true) {
        Texto("Todavía no salió el dato del índice para calcularlo. Te avisamos cuando se publique.", Estilo.footnote, tinta, caja)
    }
}

@Composable
private fun TextoComoFicha(resto: String, color: Color) {
    BasicText(buildAnnotatedString {
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("Cómo se calcula. ") }
        append(resto)
    }, style = Estilo.footnote.ts().copy(color = color))
}

@Composable
private fun NotaEstimadoFicha(indice: String) {
    Texto("El valor final se confirma cuando ${if (indice == "ipc") "el INDEC publique el IPC del mes" else "el BCRA publique el índice del día"}; si cambia, te avisamos.",
        Estilo.footnote, hex(0xCFE0D8))
}

@Composable
private fun AvisadosFicha(s: FichaEstado) {
    val av = s.ajuste?.avisos ?: emptyMap()
    val ya = listOfNotNull(
        av["inquilino"]?.let { "${if (s.nInq.isEmpty()) "El inquilino" else s.nInq} el ${FichaContrato.cortita(it)}" },
        av["propietario"]?.let { "${if (s.nProp.isEmpty()) "el propietario" else s.nProp} el ${FichaContrato.cortita(it)}" },
    )
    if (ya.isNotEmpty()) {
        Etiqueta(Formato.mayuscula(ya.joinToString(" y ")) + " ya ${if (ya.size == 2) "saben" else "sabe"} del ajuste.", "checkmark.circle", Estilo.footnote, hex(0x9FD6C2))
    }
}

// MARK: Historial de ajustes

private fun barrasFicha(k: ContratoAPI, a: AjusteContrato): List<FichaContrato.Barra> {
    val pasos = mutableListOf(Triple(k.inicio ?: "", a.base ?: k.monto, false))
    pasos += (a.cronograma ?: emptyList()).map { Triple(it.fecha, it.monto, it.futuro == true) }
    val ultimos = pasos.filter { it.second != null }.takeLast(6)
    val vigente = ultimos.indexOfLast { !it.third }
    val tonos = listOf(0.18, 0.3, 0.45, 0.62, 0.8, 1.0)
    return ultimos.mapIndexed { i, p ->
        val t = tonos.size - ultimos.size + i
        FichaContrato.Barra(p.first, p.second ?: 0.0, p.third, i == vigente, if (t >= 0 && t < tonos.size) tonos[t] else tonos[0])
    }
}

@Composable
private fun HistorialFicha(k: ContratoAPI, a: AjusteContrato) {
    val bs = barrasFicha(k, a)
    val desdeInicio = if ((a.base ?: 0.0) > 0) ((a.vigente ?: 0.0) / (a.base ?: 1.0) - 1) * 100 else 0.0
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { TituloBloque("Historial de ajustes · ${k.indiceCada}") }
            Texto("${if (desdeInicio >= 0) "+" else ""}${desdeInicio.roundToInt()} % desde el inicio", Estilo.footnote, C.texto2)
        }
        // Swift Charts (BarMark con la anotación arriba y el mes abajo): barras en Compose.
        val tope = maxOf(1.0, bs.maxOfOrNull { it.monto } ?: 1.0)
        Row(Modifier.fillMaxWidth().height(198.dp).tarjeta().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
            bs.forEach { b ->
                Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                    Texto(Formato.numero(b.monto.roundToInt()), Estilo(10f, Peso.semibold), if (b.futuro) C.oroTinta else C.texto2, lineas = 1, escalaMinima = 0.6f)
                    Spacer(Modifier.height(2.dp))
                    Box(Modifier.fillMaxWidth().weight(maxOf(0.01f, (b.monto / tope).toFloat()), fill = true)
                        .clip(RoundedCornerShape(6.dp)).background(if (b.futuro) C.oro else C.accion.copy(alpha = b.opacidad.toFloat())))
                    if (b.monto < tope) Spacer(Modifier.weight(maxOf(0.01f, (1 - b.monto / tope).toFloat())))
                    Spacer(Modifier.height(4.dp))
                    Texto(b.etiqueta, Estilo(10f, Peso.regular), C.texto3, lineas = 2, alinear = TextAlign.Center)
                }
            }
        }
    }
}

// MARK: Cobros mes a mes

@Composable
private fun BloqueCobrosFicha(
    s: FichaEstado, k: ContratoAPI, escribe: Boolean, verCobranzas: () -> Unit, rendicion: (RendicionAPI) -> Unit, rendir: (String) -> Unit,
    recibo: (MesContrato, Cobro) -> Unit, recordar: (String) -> Unit, cobrar: (MesContrato) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { TituloBloque("Cobros") }
            BotonLink("Ver cobranzas", Estilo.subheadFuerte, C.accion, alTocar = verCobranzas)
        }
        Column(Modifier.fillMaxWidth().tarjeta()) {
            s.ultimos.forEachIndexed { i, m ->
                if (i > 0) LineaFila()
                FilaMesFicha(s, k, m, escribe, rendicion, rendir, recibo, recordar, cobrar)
            }
            if (s.ultimos.isEmpty()) Texto("Todavía no hay cobros.", Estilo.texto14, C.texto3, Modifier.fillMaxWidth().padding(14.dp))
        }
    }
}

private fun detalleMesFicha(k: ContratoAPI, m: MesContrato, r: RendicionAPI?): String {
    val c = m.cobro
    if (c != null) {
        return "Pagó el ${TextosCobro.fechaCortita(c.pagadoEl ?: "")}" + (r?.let { " · rendición ${FichaContrato.estadoRendicion[it.estado ?: ""] ?: ""}" } ?: "")
    }
    return when (m.estado) {
        "atrasado" -> "Venció el ${TextosCobro.fechaCortita(m.vence ?: "")}"
        "sin_registro" -> if (k.estado == "rescindido") "Sin cobro registrado" else "Sin cobro registrado · de antes de Corredor"
        else -> "Vence el ${TextosCobro.fechaCortita(m.vence ?: "")}"
    }
}

@Composable
private fun FilaMesFicha(
    s: FichaEstado, k: ContratoAPI, m: MesContrato, escribe: Boolean, rendicion: (RendicionAPI) -> Unit, rendir: (String) -> Unit,
    recibo: (MesContrato, Cobro) -> Unit, recordar: (String) -> Unit, cobrar: (MesContrato) -> Unit,
) {
    val r = s.rendiciones.firstOrNull { it.periodo.take(7) == m.periodo.take(7) }
    val (icono, color) = when (m.estado) {
        "cobrado" -> "checkmark.circle.fill" to hex(0x2F9E6F)
        "atrasado" -> "exclamationmark.circle" to C.error
        "sin_registro" -> "clock.arrow.circlepath" to C.texto2
        else -> "clock" to C.oroTinta
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icono(icono, 20f, color)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Texto(Formato.mayuscula(TextosCobro.nombreMes(m.periodo)), Estilo.filaLista, C.texto)
            Texto(detalleMesFicha(k, m, r), Estilo.footnote, if (m.estado == "atrasado") C.errorTinta else C.texto2, lineas = 2)
        }
        Texto(Formato.numero((m.cobro?.monto ?: m.esperado ?: 0.0).roundToInt()), Estilo.subheadFuerte, C.texto)
        // Acciones del mes.
        val c = m.cobro
        if (c != null) {
            val opciones = buildList {
                if (r != null) add(OpcionDeMenu("Rendición", "doc.plaintext") { rendicion(r) })
                else if (escribe) add(OpcionDeMenu("Rendir", "doc.plaintext") { rendir(m.periodo) })
                if (c.compartido == true) add(OpcionDeMenu("Recibo enviado", "checkmark.circle") { recibo(m, c) })
                else add(OpcionDeMenu("Recibo", "doc.text") { recibo(m, c) })
            }
            MenuBoton(opciones, "Recibo y rendición de ${TextosCobro.nombreMes(m.periodo)}") {
                Row(Modifier.widthIn(min = 44.dp).heightIn(min = 44.dp), horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically) {
                    if (s.rindiendo == m.periodo) CargandoCobro()
                    if (c.compartido == true) Icono("checkmark.circle", 17f, C.azulTinta, descripcion = "Recibo enviado")
                    Icono("ellipsis.circle", 20f, C.texto2)
                }
            }
        } else if (escribe) {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                if (k.contactoId != null && m.estado != "sin_registro") {
                    Box(Modifier.size(40.dp, 44.dp).clickable { recordar(m.periodo.take(7)) }.semantics { contentDescription = "Recordar por WhatsApp" },
                        contentAlignment = Alignment.Center) { Icono("bubble.left", 18f, C.texto2) }
                }
                Box(Modifier.height(32.dp).clip(RoundedCornerShape(50)).background(C.hundida).clickable { cobrar(m) }.padding(horizontal = 10.dp),
                    contentAlignment = Alignment.Center) { Texto("Cobrado", Estilo(13f, Peso.semibold), C.texto) }
            }
        }
    }
}

// MARK: Partes

@Composable
private fun PartesFicha(s: FichaEstado, k: ContratoAPI, abrirContacto: (String) -> Unit, chat: (String, String?) -> Unit, llamar: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TituloBloque("Partes")
        Column(Modifier.fillMaxWidth().tarjeta()) {
            k.contactoNombre?.let { n ->
                val que = if (s.debe.isEmpty()) "" else " · debe ${if (s.debe.size > 1) "${s.debe.size} meses" else TextosCobro.nombreMes(s.debe.lastOrNull()?.periodo ?: "")}"
                ParteFicha(n, (if (k.tipo == "venta") "Comprador" else "Inquilino") + que, s.debe.isNotEmpty(), true, k.contactoId, abrirContacto) {
                    k.contactoId?.let { chat(it, s.inquilino?.telefono) }
                }
            }
            k.propietarioNombre?.let { n ->
                val ult = s.rendiciones.firstOrNull()?.let { " · última rendición, ${TextosCobro.nombreMes(it.periodo)}" } ?: ""
                ParteFicha(n, "Propietario$ult", false, k.contactoNombre == null, k.propietarioId, abrirContacto) {
                    k.propietarioId?.let { chat(it, s.propietario?.telefono) }
                }
            }
            k.garanteNombre?.let { n ->
                ParteFicha(n, "Garante" + (k.garanteNotas?.let { " · $it" } ?: ""), false, k.contactoNombre == null && k.propietarioNombre == null,
                    null, abrirContacto, icono = if (k.garanteTelefono == null) null else "phone", accion = llamar)
            }
            if (k.contactoNombre == null && k.propietarioNombre == null && k.garanteNombre == null) {
                Texto("Este contrato todavía no tiene partes cargadas.", Estilo.texto14, C.texto3, Modifier.fillMaxWidth().padding(14.dp))
            }
        }
    }
}

@Composable
private fun ParteFicha(
    nombre: String, detalle: String, rojo: Boolean, primera: Boolean, contacto: String?, abrirContacto: (String) -> Unit,
    icono: String? = "bubble.left", accion: () -> Unit,
) {
    if (!primera) LineaFila()
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f).clickable(enabled = contacto != null) { contacto?.let(abrirContacto) }, horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically) {
            AvatarIniciales(nombre, tam = 36.dp, fondo = if (rojo) C.errorFondo else C.crema, tinta = if (rojo) C.errorTinta else C.primarioTexto)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Texto(nombre, Estilo.filaLista, C.texto)
                Texto(detalle, if (rojo) Estilo.footnote.con(Peso.semibold) else Estilo.footnote, if (rojo) C.errorTinta else C.texto2, lineas = 2)
            }
        }
        if (icono != null) {
            Box(Modifier.size(44.dp).clickable(onClick = accion).semantics { contentDescription = if (icono == "phone") "Llamar a $nombre" else "WhatsApp con $nombre" },
                contentAlignment = Alignment.Center) { Icono(icono, 18f, C.accion) }
        }
    }
}

// MARK: Condiciones

@Composable
private fun CondicionesFicha(k: ContratoAPI, moneda: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TituloBloque("Condiciones")
        Column(Modifier.fillMaxWidth().tarjeta()) {
            if (k.tipo == "alquiler") {
                DatoFicha("Vence el pago", "día ${k.diaVencimiento ?: 1} de cada mes", primera = true)
                k.punitorioDiario?.let { p -> if (p > 0) DatoFicha("Punitorios", "${TextosCobro.numero(p)} % diario") }
                DatoFicha("Ajuste", k.indiceCada)
            }
            DatoFicha(if (k.tipo == "alquiler") "Alquiler inicial" else "Monto", k.monto?.let { Formato.plata(it, moneda) } ?: "—", primera = k.tipo != "alquiler")
            k.deposito?.let { d ->
                if (d > 0) {
                    val meses = if ((k.monto ?: 0.0) > 0) " (${TextosCobro.numero(Math.round(d / (k.monto ?: 1.0) * 10) / 10.0)} ${if (d == k.monto) "mes" else "meses"})" else ""
                    DatoFicha("Depósito", Formato.plata(d, moneda) + meses)
                }
            }
            k.honorarios?.let { h -> if (h > 0) DatoFicha("Honorarios", "${TextosCobro.numero(h)} % del total del contrato") }
            k.comision?.let { c ->
                if (c > 0) DatoFicha(if (k.tipo == "alquiler") "Administración" else "Comisión", "${TextosCobro.numero(c)} %${if (k.tipo == "alquiler") " de lo cobrado" else ""}")
            }
            k.notas?.takeIf { it.isNotBlank() }?.let { DatoFicha("Notas", it) }
        }
    }
}

@Composable
private fun DatoFicha(l: String, v: String, primera: Boolean = false) {
    if (!primera) LineaFila()
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.Top) {
        Texto(l, Estilo.subhead, C.texto2)
        Spacer(Modifier.weight(1f).widthIn(min = 8.dp))
        Texto(v, Estilo.subhead.con(Peso.semibold), C.texto, Modifier.widthIn(max = 230.dp), alinear = TextAlign.End)
    }
}

// MARK: Documentos

@Composable
private fun BloqueDocumentosFicha(s: FichaEstado, escribe: Boolean, subirArchivo: () -> Unit, subirFoto: () -> Unit, abrir: (DocumentoContrato) -> Unit, sacar: (DocumentoContrato) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { TituloBloque("Documentos") }
            if (escribe) {
                MenuBoton(listOf(OpcionDeMenu("Un archivo (PDF)", "doc") { subirArchivo() }, OpcionDeMenu("Una foto", "photo") { subirFoto() }), "Subir") {
                    Etiqueta("Subir", "plus", Estilo.subheadFuerte, C.accion, Modifier.heightIn(min = 32.dp))
                }
            }
        }
        Column(Modifier.fillMaxWidth().tarjeta()) {
            val visibles = s.documentos.filter { it.id !in s.sacados }
            visibles.forEachIndexed { i, d ->
                if (i > 0) LineaFila()
                FilaDocumentoFicha(d, escribe, abrir, sacar)
            }
            if (s.subiendo) {
                Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    CargandoCobro(); Texto("Subiendo…", Estilo.footnote, C.texto2)
                }
            }
            if (visibles.isEmpty() && !s.subiendo) {
                Texto(if (escribe) "Tocá «Subir» para sumar el contrato firmado, el acta o el DNI." else "Sin documentos.", Estilo.texto14, C.texto3,
                    Modifier.fillMaxWidth().padding(14.dp))
            }
        }
    }
}

@Composable
private fun FilaDocumentoFicha(d: DocumentoContrato, escribe: Boolean, abrir: (DocumentoContrato) -> Unit, sacar: (DocumentoContrato) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f).clickable { abrir(d) }, horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(28.dp), contentAlignment = Alignment.Center) { Icono(if (d.tipo?.contains("pdf") == true) "doc.richtext" else "photo", 17f, C.azulTinta) }
            Column(Modifier.weight(1f).padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Texto(d.nombre, Estilo.filaLista, C.texto, lineas = 1)
                Texto(listOfNotNull(Fecha.iso(d.creadoEn)?.let { Formato.cuando(it) }, d.bytes?.let { tamanoArchivoAlta(it.toLong()) }).joinToString(" · "), Estilo.footnote, C.texto2)
            }
        }
        if (escribe) {
            MenuBoton(listOf(OpcionDeMenu("Abrir", "arrow.up.right.square") { abrir(d) }, OpcionDeMenu("Sacar", "trash", destructiva = true) { sacar(d) }),
                "Opciones de ${d.nombre}") {
                Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) { Icono("ellipsis.circle", 17f, C.texto2) }
            }
        }
    }
}

// MARK: Rendiciones

@Composable
private fun BloqueRendicionesFicha(s: FichaEstado, abrir: (RendicionAPI) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TituloBloque("Rendiciones al propietario")
        Column(Modifier.fillMaxWidth().tarjeta()) {
            s.rendiciones.forEachIndexed { i, r ->
                if (i > 0) LineaFila()
                Row(Modifier.fillMaxWidth().heightIn(min = 54.dp).clickable { abrir(r) }.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Texto(Formato.mayuscula(ReciboVista.mes(r.periodo)), Estilo.filaLista, C.texto)
                        Texto(RendicionAPI.estadoTexto(r.estado), Estilo.footnote, if (r.estado == "pendiente") C.avisoTinta else C.texto2)
                    }
                    Texto(Formato.plata(r.neto ?: 0.0, r.moneda ?: "ARS"), Estilo.body16.con(Peso.semibold), C.texto)
                    Spacer(Modifier.width(8.dp))
                    Chevron()
                }
            }
        }
    }
}

// MARK: Lo estático

object FichaContrato {
    @Serializable data class Persona(val id: String, val nombre: String? = null, val telefono: String? = null)
    @Serializable data class Punto(val periodo: String, val valor: Double)

    data class Barra(val fecha: String, val monto: Double, val futuro: Boolean, val hoy: Boolean, val opacidad: Double) {
        val id: String get() = fecha
        val etiqueta: String get() = mesBarra(fecha) + (if (hoy) " · hoy" else if (futuro) " · estimado" else "")
    }

    val estadoRendicion: Map<String, String> = mapOf("pendiente" to "para revisar", "liquidada" to "emitida", "pagada" to "transferida")

    fun primerNombre(n: String?): String = TextosCobro.primerNombre(n)

    /** «15/03» (de un instante RFC 3339). */
    fun cortita(f: String): String {
        val d = Fecha.iso(f)?.atZone(Formato.zona)?.toLocalDate() ?: return ""
        return "%02d/%02d".format(d.dayOfMonth, d.monthValue)
    }

    /** «1,25» con los decimales justos. */
    fun dec(n: Double, d: Int = 2): String {
        val patron = if (d <= 0) "0" else "0." + "0".repeat(d)
        return DecimalFormat(patron, DecimalFormatSymbols(Formato.argentina)).format(n)
    }

    /** «2026-12-01» → «dic 26». */
    fun mesBarra(fecha: String): String {
        val d = Fecha.fecha(fecha) ?: return fecha
        return DateTimeFormatter.ofPattern("MMM", Formato.argentina).format(d).replace(".", "") + " " + fecha.drop(2).take(2)
    }

    /** El último valor publicado en o antes de una fecha (como la API: indices.coeficienteEn). */
    fun valorEn(serie: List<Punto>, f: String): Punto? {
        var v: Punto? = null
        for (p in serie) {
            if (p.periodo > f) break
            v = p
        }
        return v
    }
}
