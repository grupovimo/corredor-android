package ar.com.corredor.app.pantallas.informes

import ar.com.corredor.app.app.PantallaApilada

import android.content.Context
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ar.com.corredor.app.app.LocalEstado
import ar.com.corredor.app.nucleo.*
import ar.com.corredor.app.pantallas.cartera.Operacion
import ar.com.corredor.app.pantallas.mas.*
import ar.com.corredor.app.tema.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.time.Instant
import kotlin.math.max
import kotlin.math.roundToInt

// Clon de Pantallas/Informes/Informe.swift.

/** GET /v1/informe?dias= (panel.Informe). */
@Serializable
data class InformeAPI(
    val dias: Int? = null,
    val masVistas: List<Vista>? = null,
    val totalVistas: Int? = null,
    val origenes: List<Origen>? = null,
    val totalContactos: Int? = null,
    val embudo: List<Etapa>? = null,
    val cerradas: Int? = null,
    val diasPromedioCierre: Int? = null,
    val honorarios: List<Monto>? = null,
    val caidas: Int? = null,
    val motivoCaida: String? = null,
) {
    @Serializable data class Vista(val propiedadId: String, val titulo: String? = null, val vistas: Int? = null)
    @Serializable data class Origen(val origen: String? = null, val cantidad: Int? = null)
    @Serializable data class Etapa(val etapa: String, val cantidad: Int? = null)
    @Serializable data class Monto(val moneda: String, val monto: Double)
}

/** Informe y exportar, como la página del panel (45): el mes que va («4 cierres · USD 11.200 en honorarios · 92 %
 *  cobrado · 3 operaciones caídas por precio»), el embudo con cuánto tarda en cerrar, de dónde llegan, lo más visto
 *  en la web, el informe mensual de 4 páginas (de cualquier mes, en PDF o para imprimir) y cada listado a Excel. */
@Composable
fun Informe() {
    val estado = LocalEstado.current
    val ctx = LocalContext.current
    val alcance = rememberCoroutineScope()
    var inf by remember { mutableStateOf<InformeAPI?>(null) }
    var cobrado by remember { mutableStateOf<Int?>(null) }
    var error by remember { mutableStateOf<ErrorApi?>(null) }
    var exportando by remember { mutableStateOf<File?>(null) }
    var previo by remember { mutableStateOf<ArchivoPrevio?>(null) }
    var mesInforme by remember { mutableStateOf("") }
    var armando by remember { mutableStateOf(false) }

    val hoy = Formato.diaISO(Instant.now())
    val mes = TextosInforme.nombreMes(hoy)

    /** Los meses que se pueden pedir: el pasado (el que llega el día 1) y los 11 anteriores, y el que va. */
    val meses: List<String> = run {
        val actual = hoy.take(7)
        val l = mutableListOf(actual)
        var m = actual
        repeat(12) { m = TextosInforme.anterior(m); l.add(m) }
        l
    }
    LaunchedEffect(Unit) { if (mesInforme.isEmpty()) mesInforme = if (meses.size > 1) meses[1] else "" }

    /** «4 cierres · USD 11.200 en honorarios · 92 % cobrado · 3 operaciones caídas por precio». */
    fun resumen(i: InformeAPI): String {
        val p = mutableListOf(Formato.cantidad(i.cerradas ?: 0, "cierre", "cierres"))
        val h = i.honorarios
        if (!h.isNullOrEmpty()) p.add(h.joinToString(" + ") { Formato.plata(it.monto, it.moneda) } + " en honorarios")
        cobrado?.let { p.add("$it${Formato.fino}% cobrado") }
        val c = i.caidas
        if (c != null && c > 0) {
            p.add(Formato.cantidad(c, "operación caída", "operaciones caídas") + (i.motivoCaida?.let { " por ${it.lowercase()}" } ?: ""))
        }
        return p.joinToString(" · ") + "."
    }

    suspend fun cargar() {
        val dia = hoy.takeLast(2).toIntOrNull() ?: 30
        try {
            inf = estado.api.get<InformeAPI>("/v1/informe", listOf("dias" to "${max(1, dia)}"))
            error = null
        } catch (e: CancellationException) { throw e } catch (e: ErrorApi) { if (inf == null) error = e } catch (e: Exception) {}
        // JSONLibre de iOS: sólo se cuentan las filas.
        @Serializable data class Cob(val cobradas: List<JsonElement>? = null, val pendientes: List<JsonElement>? = null)
        val c = estado.api.opcional<Cob>("/v1/cobranzas")
        if (c != null) {
            val total = (c.cobradas?.size ?: 0) + (c.pendientes?.size ?: 0)
            cobrado = if (total > 0) ((c.cobradas?.size ?: 0).toDouble() / total * 100).roundToInt() else null
        }
    }

    suspend fun verInforme(m: String, imprimir: Boolean) {
        armando = true
        try {
            val d = InformeMensualPDF.juntar(estado.api, if (m.isEmpty()) null else m)
            val u = InformeMensualPDF.pdf(ctx, d)
            if (u == null) { estado.avisar("No pudimos armar el informe.", ok = false); return }
            if (imprimir) {
                imprimirPdf(ctx, u)
            } else {
                previo = ArchivoPrevio(u)
            }
        } catch (e: CancellationException) { throw e } catch (e: Exception) { estado.avisar(e) } finally { armando = false }
    }

    suspend fun exportarExcel(r: String) {
        try {
            // Sin ?formato=xlsx la API manda un CSV.
            val d = estado.api.bajar("/v1/exportar/$r", listOf("formato" to "xlsx"))
            val u = File(File(ctx.cacheDir, "compartir").apply { mkdirs() }, "$r-${Formato.diaISO(Instant.now())}.xlsx")
            withContext(Dispatchers.IO) { u.writeBytes(d) }
            exportando = u
        } catch (e: CancellationException) { throw e } catch (e: Exception) { estado.avisar(e) }
    }

    @Composable
    fun destacado(i: InformeAPI) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(C.verde700).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Texto("Informe de $mes", Estilo.headline, Color.White)
            Texto(resumen(i), Estilo.body16, Color.White.copy(alpha = 0.92f))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                BotonTonal("Ver informe", { alcance.launch { verInforme(hoy.take(7), imprimir = false) } }, alto = 40.dp)
                Tocable({ alcance.launch { exportarExcel("operaciones") } }) {
                    Box(Modifier.height(40.dp).border(1.dp, Color.White.copy(alpha = 0.6f), Capsula).padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                        ConIcono("Excel", "arrow.down.circle", Estilo.footnoteFuerte, Color.White)
                    }
                }
            }
        }
    }

    @Composable
    fun embudo(i: InformeAPI) {
        GraficoBarras("Embudo", i.diasPromedioCierre?.let { "cierra en ${Formato.cantidad(it, "día", "días")}" },
            (i.embudo ?: emptyList()).map { Operacion.etapaTexto(it.etapa) to (it.cantidad ?: 0).toDouble() }, hex(0x2F9E6F))
    }

    @Composable
    fun origenes(i: InformeAPI) {
        val o = (i.origenes ?: emptyList()).map { (it.origen ?: "Otro") to (it.cantidad ?: 0).toDouble() }
        if (o.isEmpty()) {
            Column(Modifier.fillMaxWidth().tarjeta().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Texto("De dónde llegan", Estilo.headline, C.texto)
                Texto(Formato.cantidad(i.totalContactos ?: 0, "contacto nuevo", "contactos nuevos"), Estilo.footnote, C.texto2)
                Texto("Este mes todavía no entraron contactos.", Estilo.subhead, C.texto3, Modifier.padding(top = 6.dp))
            }
        } else {
            GraficoOrigenes(o)
        }
    }

    @Composable
    fun masVistas(i: InformeAPI) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TituloBloque("Lo más visto en tu web", Formato.cantidad(i.totalVistas ?: 0, "visita", "visitas"))
            Column(Modifier.fillMaxWidth().tarjeta()) {
                val l = (i.masVistas ?: emptyList()).take(5)
                l.forEachIndexed { k, v ->
                    if (k > 0) Box(Modifier.fillMaxWidth().height(0.5.dp).background(C.linea))
                    Tocable({ estado.abrir(AFichaPropiedad(id = v.propiedadId)) }) {
                        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Texto(v.titulo ?: "Propiedad", Estilo.body16, C.texto, Modifier.weight(1f), lineas = 1)
                            Texto(Formato.numero(v.vistas ?: 0), Estilo.filaLista, C.texto, numeros = true)
                            Chevron()
                        }
                    }
                }
                if (l.isEmpty()) {
                    Texto(Formato.sinViudas("Cuando entren visitas a tu web, acá ves qué propiedades miran más."), Estilo.subhead, C.texto3, Modifier.padding(16.dp))
                }
            }
        }
    }

    /** El informe mensual de 4 páginas: de qué mes (sin elegir, el pasado), en PDF o para imprimir. */
    @Composable
    fun informeMensual() {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TituloBloque("Informe mensual")
            Column(Modifier.fillMaxWidth().tarjeta().padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SelectorMenu(meses.map { it to "${Formato.mayuscula(TextosInforme.nombreMes(it))} ${it.take(4)}" }, mesInforme, { mesInforme = it }, "Mes")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.weight(1f).deshabilitado(armando)) {
                        BotonHundido(if (armando) "Armando…" else "PDF", { alcance.launch { verInforme(mesInforme, imprimir = false) } }, simbolo = "doc.richtext")
                    }
                    Box(Modifier.weight(1f).deshabilitado(armando)) {
                        BotonHundido("Imprimir", { alcance.launch { verInforme(mesInforme, imprimir = true) } }, simbolo = "printer")
                    }
                }
            }
        }
    }

    @Composable
    fun exportar() {
        val listados = listOf(
            Triple("propiedades", "Cartera", "building.2"), Triple("contactos", "Contactos", "person.2"), Triple("operaciones", "Operaciones", "rectangle.split.3x1"),
            Triple("agenda", "Agenda del último año", "calendar"), Triple("contratos", "Contratos", "doc.text"), Triple("cobros", "Cobranzas", "dollarsign.circle"),
            Triple("reservas", "Reservas", "bookmark"), Triple("emprendimientos", "Emprendimientos", "building"), Triple("temporada", "Temporada", "beach.umbrella"),
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TituloBloque("Exportar a Excel")
            Column(Modifier.fillMaxWidth().tarjeta()) {
                listados.forEach { (r, n, i) ->
                    Box(Modifier.fillMaxWidth().height(0.5.dp).background(C.linea))
                    Tocable({ alcance.launch { exportarExcel(r) } }) {
                        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                            ConIcono(n, i, Estilo.body16, C.texto, Modifier.weight(1f))
                            Icono("tablecells", 16f, hex(0x1D6F42))
                        }
                    }
                }
            }
        }
    }

    PantallaApilada("Informe y exportar") {
        Refrescable({ cargar() }) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Texto("Cómo viene el mes y cada listado para bajar a Excel.", Estilo.subhead, C.texto2)
                val i = inf
                val e = error
                if (i != null) {
                    destacado(i)
                    embudo(i)
                    origenes(i)
                    masVistas(i)
                } else if (e != null) {
                    EstadoError("el informe", e) { alcance.launch { cargar() } }
                } else {
                    EsqueletoFilas(4)
                }
                informeMensual()
                exportar()
            }
        }
    }
    LaunchedEffect(estado.version) { cargar() }
    VistaPrevia(previo) { previo = null }
    // La hoja de compartir el Excel (ShareLink en una hoja de 160).
    Hoja(exportando != null, { exportando = null }, altoFijo = 160.dp) {
        val x = exportando
        Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
            if (x != null) {
                Tocable({ compartirArchivo(ctx, x, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet") }) {
                    ConIcono("Compartir ${x.name}", "square.and.arrow.up", Estilo.body, C.accion)
                }
            }
        }
    }
}

/** `Informe.num` y `Informe.valor` de iOS (un `object` con el mismo nombre que la pantalla). */
object Informe {
    /** Un número del JSON libre («—» si no hay); lo usan otras pantallas (Mi web). JSONLibre de iOS → JsonElement. */
    fun num(v: JsonElement?): String {
        val n = (v as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull ?: return "—"
        return Formato.numero(n.toInt())
    }

    fun valor(v: JsonElement?): Double = (v as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull ?: 0.0
}

