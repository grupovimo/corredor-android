package ar.com.corredor.app.pantallas.informes

import ar.com.corredor.app.app.PantallaApilada

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ar.com.corredor.app.app.BotonBarra
import ar.com.corredor.app.app.LocalEstado
import ar.com.corredor.app.nucleo.*
import ar.com.corredor.app.pantallas.cartera.Operacion
import ar.com.corredor.app.pantallas.mas.*
import ar.com.corredor.app.pantallas.propiedades.Propiedad
import ar.com.corredor.app.tema.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

// Clon de Pantallas/Informes/Objetivos.swift.

/** Cuentas de los objetivos, como la web (lib/crm/objetivos.ts): la raya de dónde debería ir hoy (los días hábiles
 *  que pasaron del período) y el estado de cada meta contra esa raya. */
object Ritmo {
    data class Metrica(val clave: String, val texto: String)

    val metricas = listOf(Metrica("propiedades_captadas", "Captaciones"), Metrica("visitas_hechas", "Visitas"), Metrica("operaciones_cerradas", "Cierres"))
    val meses = listOf("enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre")

    /** «2026-11-12» → la fecha (Fecha.dia en iOS). */
    internal fun dia(s: String): LocalDate? {
        val p = s.take(10).split("-").mapNotNull { it.toIntOrNull() }
        if (p.size != 3) return null
        return runCatching { LocalDate.of(p[0], p[1], p[2]) }.getOrNull()
    }

    private fun habil(d: LocalDate): Boolean = d.dayOfWeek != DayOfWeek.SUNDAY && d.dayOfWeek != DayOfWeek.SATURDAY

    /** Días hábiles (lunes a viernes) entre dos fechas (yyyy-MM-dd), inclusive. */
    fun habiles(desde: String, hasta: String): Int {
        var d = dia(desde) ?: return 0
        val h = dia(hasta) ?: return 0
        var n = 0
        var vueltas = 0
        while (!d.isAfter(h) && vueltas < 800) {
            if (habil(d)) n += 1
            d = d.plusDays(1)
            vueltas += 1
        }
        return n
    }

    data class DelRitmo(val esperado: Double, val quedan: Int)

    /** Qué parte del período tendría que estar hecha hoy (0 a 1) y cuántos días hábiles quedan. */
    fun ritmo(desde: String, hasta: String, hoy: String): DelRitmo {
        val total = habiles(desde, hasta)
        if (total <= 0) return DelRitmo(1.0, 0)
        // Entender.sumar(hoy, -1) en iOS: el día anterior.
        val ayer = dia(hoy)?.minusDays(1)?.toString() ?: hoy
        val pasados = if (hoy <= desde) 0 else habiles(desde, if (ayer < hasta) ayer else hasta)
        // Hoy cuenta como medio día si es hábil (a la mañana todavía no se espera lo de hoy entero).
        val hoyCuenta = if (hoy >= desde && hoy <= hasta && (dia(hoy)?.let(::habil) ?: false)) 0.5 else 0.0
        return DelRitmo(min(1.0, (pasados + hoyCuenta) / total), max(0, total - pasados - (if (hoyCuenta > 0) 1 else 0)))
    }

    /** «cumplido», «adelante», «al día» o «atrás». */
    fun estado(avance: Double, meta: Double, esperado: Double): String {
        val p = if (meta > 0) avance / meta else 1.0
        if (p >= 1) return "cumplido"
        if (p + 1e-9 < esperado) return "atrás"
        return if (p >= esperado + 0.15) "adelante" else "al día"
    }

    /** Cuántas le faltan para ir al día. */
    fun faltan(avance: Double, meta: Double, esperado: Double): Int = max(0, ceil(meta * esperado - avance - 1e-9).toInt())
}

/** Una meta de una persona (o del equipo) en una métrica, con su objetivo si lo tiene. */
data class MetaFila(val id: String?, val metrica: String, val texto: String, val avance: Double, val meta: Double)

/** Objetivos del mes, como la página del panel (20E y 20C): «Tu noviembre» con la meta que más atrás va, lo que
 *  falta para ir al día y qué hacer; el tablero del equipo con la raya del día, la zona de cada uno y «Para ayudar»;
 *  la fila «Equipo» y los honorarios; y «Editar metas» para la dueña. */
@Composable
fun Objetivos() {
    val estado = LocalEstado.current
    val alcance = rememberCoroutineScope()
    var objetivos by remember { mutableStateOf<List<Objetivo>?>(null) }
    var equipo by remember { mutableStateOf<List<Miembro>>(emptyList()) }
    var sugerencias by remember { mutableStateOf<List<Sugerencia>>(emptyList()) }
    var ops by remember { mutableStateOf<List<Operacion>>(emptyList()) }
    var props by remember { mutableStateOf<List<Propiedad>>(emptyList()) }
    var error by remember { mutableStateOf<ErrorApi?>(null) }
    var editar by remember { mutableStateOf(false) }

    val hoy = Formato.diaISO(Instant.now())
    val desde = objetivos?.firstOrNull()?.desde ?: (hoy.take(8) + "01")
    val hasta = objetivos?.firstOrNull()?.hasta ?: run {
        val ini = Ritmo.dia(desde) ?: LocalDate.now()
        ini.plusMonths(1).minusDays(1).toString()
    }
    val mes = desde.drop(5).take(2).toIntOrNull()?.let { Ritmo.meses[max(0, min(11, it - 1))] } ?: ""
    val ritmo = Ritmo.ritmo(desde, hasta, hoy)

    fun fila(agente: String?): List<MetaFila> = Ritmo.metricas.map { m ->
        val o = (objetivos ?: emptyList()).firstOrNull { it.agenteId == agente && it.metrica == m.clave }
        if (o != null) MetaFila(o.id, m.clave, m.texto, o.avance ?: 0.0, o.meta ?: 0.0)
        else MetaFila(null, m.clave, m.texto, 0.0, 0.0)
    }

    val agentes = equipo.filter { p -> (objetivos ?: emptyList()).any { it.agenteId == p.usuarioId } || p.rol == "agente" }

    /** Los barrios donde tiene más propiedades. */
    fun zona(id: String): String {
        val n = props.filter { it.agenteId == id }.mapNotNull { it.barrio }.groupingBy { it }.eachCount()
        val bs = n.entries.sortedByDescending { it.value }.map { it.key }
        return if (bs.size > 1) "${bs[0]} y ${bs[1]}" else (bs.firstOrNull() ?: "")
    }

    /** La acción concreta para quien va atrás: su primera sugerencia, una reserva sin seña o las nuevas sin contactar. */
    fun ayuda(id: String): Pair<String, Any>? {
        val s = sugerencias.firstOrNull { it.agenteId == id }
        if (s != null) {
            val d: Any = s.operacionId?.let { AFichaOperacion(id = it) } ?: s.contactoId?.let { AFichaContacto(id = it) }
                ?: s.propiedadId?.let { AFichaPropiedad(id = it) } ?: AOperaciones
            return listOf(s.destacado, s.motivo).mapNotNull { it?.trim()?.takeIf { t -> t.isNotEmpty() } }.joinToString(": ") to d
        }
        val o = ops.firstOrNull { it.agenteId == id && it.etapa == "reserva" && it.sena == null }
        if (o != null) return "${o.nombre.split(",").firstOrNull() ?: ""}: reserva sin seña cargada" to AFichaOperacion(id = o.id)
        val nuevas = ops.count { it.agenteId == id && it.etapa == "nuevo" }
        if (nuevas > 0) return "${if (nuevas == 1) "Una operación nueva" else "$nuevas operaciones nuevas"} sin contactar" to AOperaciones
        return null
    }

    val honorariosMeta = (objetivos ?: emptyList()).firstOrNull { it.agenteId == null && (it.metrica == "honorarios_usd" || it.metrica == "honorarios_ars") }
    val honorariosMes = run {
        val zona = Formato.zona
        val ahora = Instant.now().atZone(zona)
        ops.filter { o ->
            o.etapa == "cierre" && (o.moneda ?: "USD") == "USD" &&
                (o.actualizadoEn?.atZone(zona)?.let { it.year == ahora.year && it.monthValue == ahora.monthValue } ?: false)
        }.sumOf { (it.valor ?: 0.0) * ((it.honorariosComprador ?: 0.0) + (it.honorariosVendedor ?: 0.0)) / 100 }
    }

    suspend fun cargar() {
        try {
            objetivos = estado.api.get<List<Objetivo>>("/v1/objetivos", listOf("vigentes" to "si"))
            error = null
        } catch (e: CancellationException) { throw e } catch (e: ErrorApi) {
            if (objetivos == null) error = e
            return
        } catch (e: Exception) { return }
        equipo = estado.api.opcional<List<Miembro>>("/v1/equipo") ?: equipo
        sugerencias = estado.api.opcional<List<Sugerencia>>("/v1/sugerencias") ?: emptyList()
        ops = estado.api.opcional<List<Operacion>>("/v1/operaciones", listOf("limite" to "500")) ?: emptyList()
        props = estado.api.opcional<List<Propiedad>>("/v1/propiedades", listOf("limite" to "200", "orden" to "actualizada")) ?: emptyList()
    }

    fun bien(m: MetaFila): Boolean = Ritmo.estado(m.avance, m.meta, ritmo.esperado) in listOf("cumplido", "adelante", "al día")

    @Composable
    fun barra(m: MetaFila, alto: Dp = 8.dp, sobreVerde: Boolean = false) {
        BoxWithConstraints(
            Modifier.fillMaxWidth().height(alto).semantics {
                contentDescription = "${Formato.numero(m.avance.toInt())} de ${Formato.numero(m.meta.toInt())}, ${Ritmo.estado(m.avance, m.meta, ritmo.esperado)}"
            },
            contentAlignment = Alignment.CenterStart,
        ) {
            val ancho = maxWidth
            Box(Modifier.fillMaxSize().clip(Capsula).background(if (sobreVerde) Color.White.copy(alpha = 0.25f) else C.hundida))
            Box(Modifier.width(ancho * (if (m.meta > 0) min(1.0, m.avance / m.meta) else 0.0).toFloat()).fillMaxHeight().clip(Capsula)
                .background(if (sobreVerde) C.oro else (if (bien(m)) C.verde700 else C.oro)))
            // La raya sobresale 3 arriba y 3 abajo de la barra (frame(height: alto + 6) en un ZStack de `alto`).
            Box(Modifier.offset(x = ancho * ritmo.esperado.toFloat() - 1.dp).width(2.dp).requiredHeight(alto + 6.dp)
                .background(if (sobreVerde) Color.White else C.texto))
        }
    }

    @Composable
    fun cabecera() {
        val mio = estado.usuario?.let { u -> fila(u.id).any { it.meta > 0 } } ?: false
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Texto(if (mio) "Tu $mes" else "Objetivos de $mes", Estilo.titulo, C.texto)
            Texto((if (ritmo.quedan > 0) "Quedan ${Formato.cantidad(ritmo.quedan, "día hábil", "días hábiles")}" else "Último día del período") +
                " · la raya marca dónde deberían ir hoy", Estilo.subhead, C.texto2)
        }
    }

    /** «Tu noviembre»: la meta que más atrás va, lo que falta y qué hacer; las otras, chicas. */
    @Composable
    fun tuMes(yo: String) {
        val metas = fila(yo).filter { it.meta > 0 }
        val peor = metas.minByOrNull { it.avance / it.meta - ritmo.esperado } ?: return
        val e = Ritmo.estado(peor.avance, peor.meta, ritmo.esperado)
        val falta = Ritmo.faltan(peor.avance, peor.meta, ritmo.esperado)
        val a = ayuda(yo)
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(C.verde700).padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Texto("${peor.texto} · ${if (e == "atrás") "vas atrás" else e}".uppercase(Formato.argentina), Estilo(12f, Peso.bold, 0.5f / 12f), C.oro)
            // HStack(alignment: .firstTextBaseline, spacing: 6).
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Texto(Formato.numero(peor.avance.toInt()), Estilo.montoMeta, Color.White, Modifier.alignByBaseline(), numeros = true)
                Texto("de ${Formato.numero(peor.meta.toInt())}", Estilo.subhead, Color.White.copy(alpha = 0.8f), Modifier.alignByBaseline())
            }
            barra(peor, alto = 10.dp, sobreVerde = true)
            Texto((if (falta > 0) "Te falta ${Formato.numero(falta)} para ir al día." else "¡Vas bien!") + (a?.let { " ${it.first.trimEnd('.')}." } ?: ""), Estilo.subhead, Color.White)
            if (a != null) BotonTonal("Ver qué hacer", { estado.abrir(a.second) }, alto = 44.dp)
        }
        val otras = metas.filter { it != peor }
        if (otras.isNotEmpty()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                otras.forEach { m ->
                    Column(Modifier.weight(1f).tarjeta(16.dp).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Texto(m.texto, Estilo.footnote, C.texto2)
                        Texto("${Formato.numero(m.avance.toInt())} de ${Formato.numero(m.meta.toInt())}", Estilo.headline, C.texto)
                        barra(m, alto = 6.dp)
                        Texto(Ritmo.estado(m.avance, m.meta, ritmo.esperado), Estilo(12f, Peso.semibold), if (bien(m)) C.okTinta else C.avisoTinta)
                    }
                }
            }
        }
    }

    /** El tablero del equipo: una tarjeta por agente con sus tres metas, su zona y «Para ayudar»; y el total. */
    @Composable
    fun tablero() {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            TituloBloque("El equipo")
            agentes.forEach { p ->
                val metas = fila(p.usuarioId)
                val a = ayuda(p.usuarioId)
                Column(Modifier.fillMaxWidth().tarjeta().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        AvatarIniciales(p.completo, 36.dp)
                        Column {
                            Texto(p.completo, Estilo.filaLista, C.texto)
                            val z = zona(p.usuarioId)
                            if (z.isNotEmpty()) Texto(z, Estilo.footnote, C.texto2)
                        }
                    }
                    metas.forEach { m ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Texto(m.texto, Estilo.footnote, C.texto2, Modifier.weight(1f))
                                if (m.meta > 0) {
                                    Texto("${Formato.numero(m.avance.toInt())} de ${Formato.numero(m.meta.toInt())} · ${Ritmo.estado(m.avance, m.meta, ritmo.esperado)}",
                                        Estilo(13f, Peso.semibold), if (bien(m)) C.texto else C.avisoTinta)
                                } else {
                                    Texto("${Formato.numero(m.avance.toInt())} · sin meta", Estilo.footnote, C.texto3)
                                }
                            }
                            if (m.meta > 0) barra(m)
                        }
                    }
                    if (a != null) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Texto(a.first, Estilo.footnote, C.avisoTinta, Modifier.weight(1f))
                            BotonTonal("Ver", { estado.abrir(a.second) })
                        }
                    } else if (metas.all { it.meta == 0.0 || bien(it) }) {
                        Texto("Al día", Estilo.footnote, C.okTinta)
                    }
                }
            }
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(C.hundida).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Texto("Equipo", Estilo.filaLista, C.texto)
                fila(null).forEach { m ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Texto(m.texto, Estilo.footnote, C.texto2, Modifier.weight(1f))
                        Texto("${Formato.numero(m.avance.toInt())}${if (m.meta > 0) " de ${Formato.numero(m.meta.toInt())} · ${Ritmo.estado(m.avance, m.meta, ritmo.esperado)}" else ""}",
                            Estilo(13f, Peso.semibold), C.texto)
                    }
                }
                val h = honorariosMeta
                if (h != null) {
                    Texto("${Formato.plata(h.avance ?: 0.0, if (h.metrica == "honorarios_ars") "ARS" else "USD")} de ${Formato.numero((h.meta ?: 0.0).toInt())} en honorarios", Estilo.footnote, C.texto2)
                } else if (honorariosMes > 0) {
                    Texto("${Formato.plata(honorariosMes, "USD")} en honorarios", Estilo.footnote, C.texto2)
                }
            }
        }
    }

    val acciones: (@Composable RowScope.() -> Unit)? =
        if (estado.esDuenio && objetivos?.isEmpty() == false) { { BotonBarra("Editar metas") { editar = true } } } else null
    PantallaApilada("Objetivos", acciones = acciones) {
        Refrescable({ cargar() }) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                val o = objetivos
                val e = error
                if (o != null) {
                    cabecera()
                    if (o.isEmpty()) {
                        Box(Modifier.padding(top = 30.dp)) {
                            EstadoVacio("flag", "Todavía no hay metas este mes",
                                "Poné cuántas captaciones, visitas y cierres esperás de cada uno. El avance se cuenta solo con lo que pasa en Corredor.",
                                accion = if (estado.esDuenio) ("Poner metas" to { editar = true }) else null)
                        }
                    } else {
                        val yo = estado.usuario?.id
                        if (yo != null && fila(yo).any { it.meta > 0 }) tuMes(yo)
                        tablero()
                    }
                } else if (e != null) {
                    Box(Modifier.padding(top = 30.dp)) { EstadoError("los objetivos", e) { alcance.launch { cargar() } } }
                } else {
                    EsqueletoFilas(4)
                }
            }
        }
    }
    LaunchedEffect(estado.version) { cargar() }
    Hoja(editar, { editar = false }, alturas = listOf(Altura.grande)) {
        EditarMetas(desde, hasta, mes, agentes, agentes.associate { it.usuarioId to fila(it.usuarioId) }, fila(null), honorariosMeta) { editar = false }
    }
}

/** «Metas de noviembre»: la grilla del equipo (cada agente × captaciones, visitas y cierres, la fila Equipo y los
 *  honorarios del equipo). Cambiar un número edita la meta; 0 o vacío la borra. */
@Composable
fun EditarMetas(
    desde: String,
    hasta: String,
    mes: String,
    agentes: List<Miembro>,
    filas: Map<String, List<MetaFila>>,
    equipo: List<MetaFila>,
    honorarios: Objetivo?,
    cerrar: () -> Unit,
) {
    val estado = LocalEstado.current
    val alcance = rememberCoroutineScope()
    val valores = remember { mutableStateMapOf<String, String>() }
    var honorariosValor by remember { mutableStateOf("") }
    var moneda by remember { mutableStateOf("USD") }
    var guardando by remember { mutableStateOf(false) }

    fun clave(agente: String?, metrica: String) = "${agente ?: "equipo"}:$metrica"

    LaunchedEffect(Unit) {
        val v = mutableMapOf<String, String>()
        for (p in agentes) for (m in filas[p.usuarioId] ?: emptyList()) v[clave(p.usuarioId, m.metrica)] = if (m.meta > 0) Formato.numero(m.meta.toInt()) else ""
        for (m in equipo) v[clave(null, m.metrica)] = if (m.meta > 0) Formato.numero(m.meta.toInt()) else ""
        valores.clear(); valores.putAll(v)
        honorariosValor = honorarios?.meta?.let { Formato.numero(it.toInt()) } ?: ""
        moneda = if (honorarios?.metrica == "honorarios_ars") "ARS" else "USD"
    }

    data class Cambio(val id: String?, val agenteId: String?, val metrica: String, val meta: Double, val antes: Double)
    @Serializable data class Cuerpo(val agenteId: String?, val metrica: String, val meta: Double, val desde: String, val hasta: String)

    fun numero(s: String): Double = s.filter { it.isDigit() }.toDoubleOrNull() ?: 0.0

    suspend fun guardar() {
        val cambios = mutableListOf<Cambio>()
        for (p in agentes) {
            for (m in filas[p.usuarioId] ?: emptyList()) cambios.add(Cambio(m.id, p.usuarioId, m.metrica, numero(valores[clave(p.usuarioId, m.metrica)] ?: ""), m.meta))
        }
        for (m in equipo) cambios.add(Cambio(m.id, null, m.metrica, numero(valores[clave(null, m.metrica)] ?: ""), m.meta))
        // La meta de honorarios en su moneda (cambiar de moneda borra la otra).
        val metrica = if (moneda == "ARS") "honorarios_ars" else "honorarios_usd"
        val n = numero(honorariosValor)
        if (honorarios != null && honorarios.metrica != metrica) {
            cambios.add(Cambio(honorarios.id, null, honorarios.metrica ?: "", 0.0, honorarios.meta ?: 0.0))
            cambios.add(Cambio(null, null, metrica, n, 0.0))
        } else {
            cambios.add(Cambio(honorarios?.id, null, metrica, n, honorarios?.meta ?: 0.0))
        }
        guardando = true
        try {
            for (c in cambios.filter { it.meta != it.antes }) {
                val cuerpo = Cuerpo(c.agenteId, c.metrica, c.meta, desde, hasta)
                if (c.id != null && c.meta > 0) estado.api.hacer("PUT", "/v1/objetivos/${c.id}", cuerpo)
                else if (c.id != null) estado.api.hacer("DELETE", "/v1/objetivos/${c.id}")
                else if (c.meta > 0) estado.api.hacer("POST", "/v1/objetivos", cuerpo)
            }
            estado.avisar("Metas guardadas")
            estado.version += 1
            cerrar()
        } catch (e: CancellationException) { throw e } catch (e: Exception) { estado.avisar(e) } finally { guardando = false }
    }

    @Composable
    fun seccion(nombre: String, id: String?) {
        SeccionLista(nombre) {
            Ritmo.metricas.forEachIndexed { i, m ->
                if (i > 0) Separador()
                FilaLista {
                    Texto(m.texto, Estilo.body, C.texto, Modifier.weight(1f))
                    Entrada(
                        valores[clave(id, m.clave)] ?: "", { valores[clave(id, m.clave)] = it }, "—",
                        Modifier.width(90.dp).semantics { contentDescription = "${m.texto} de $nombre" },
                        teclado = KeyboardType.Number, alinear = TextAlign.End, numeros = true,
                    )
                }
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        BarraHoja("Metas de $mes",
            izquierda = { BotonHoja("Cancelar") { cerrar() } },
            derecha = { BotonHoja("Guardar", fuerte = true, habilitado = !guardando) { alcance.launch { guardar() } } })
        ListaAgrupada {
            agentes.forEach { p -> seccion(p.completo, p.usuarioId) }
            seccion("Equipo", null)
            SeccionLista("Honorarios del equipo") {
                Box(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Segmentado(listOf("USD" to "USD", "ARS" to "ARS"), moneda, { moneda = it }, descripcion = "Moneda")
                }
                Separador()
                FilaLista {
                    Entrada(honorariosValor, { honorariosValor = it }, "—", Modifier.weight(1f), teclado = KeyboardType.Number, numeros = true)
                }
            }
        }
    }
}
