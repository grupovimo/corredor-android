package ar.com.corredor.app.pantallas.cartera

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import java.time.Duration
import java.time.Instant
import java.util.UUID

// Clon de Pantallas/Cartera/FichaContacto.swift.

/** Un renglón de «Todo con Valeria» (web: lib/crm/linea.ts). */
data class ItemLinea(
    val id: String,
    val grupo: Grupo,
    val en: Instant,
    val icono: String,
    val tono: Tono,
    val titulo: String,
    val detalle: String? = null,
    val burbuja: String? = null,
    val mio: Boolean = false,
    /** Las notas van entre comillas. */
    val cita: Boolean = false,
) {
    enum class Grupo { mensajes, visitas, notas, otros }

    companion object {
        /** Hoy, Ayer, Esta semana, Semana pasada, Antes (y lo que viene, arriba: Próximo). */
        fun grupoDeFecha(en: Instant, ahora: Instant = Instant.now()): String {
            val d = CuentasInicio.diasEntre(en, ahora)
            if (d < 0) return "Próximo"
            if (d == 0) return "Hoy"
            if (d == 1) return "Ayer"
            val dow = Formato.diaDe(ahora).dayOfWeek.value - 1
            if (d <= dow) return "Esta semana"
            if (d <= dow + 7) return "Semana pasada"
            return "Antes"
        }
    }
}

object FichaContacto {
    data class AgendarDesdeFicha(val dia: Instant, val hora: String?) {
        val id: String get() = "${dia.epochSecond}${hora ?: ""}"
    }

    val vistas: List<Pair<ItemLinea.Grupo?, String>> = listOf(
        null to "Todo", ItemLinea.Grupo.mensajes to "Mensajes", ItemLinea.Grupo.visitas to "Visitas", ItemLinea.Grupo.notas to "Notas",
    )

    /** «12/11». */
    fun diaMes(f: Instant): String {
        val d = Formato.diaDe(f)
        return String.format("%02d/%02d", d.dayOfMonth, d.monthValue)
    }
}

/** 8B Ficha de un contacto, como la del panel (17C): WhatsApp, llamar y agendar a un toque; «¿Cómo le
 *  fue?» después de una visita; qué busca; lo que le puede interesar con su estado y «Mandar»; los
 *  datos; y «Todo con Valeria» (mensajes, visitas, operaciones —también las caídas—, notas y por dónde
 *  llegó) con el filtro, la nota escrita o dictada y «Guardar y agendar». */
@Composable
fun FichaContacto(id: String) {
    val estado = LocalEstado.current
    val ctx = LocalContext.current
    val cerrar = rememberVolver()
    val alcance = rememberCoroutineScope()
    var c by remember { mutableStateOf<Contacto?>(null) }
    var error by remember { mutableStateOf<ErrorApi?>(null) }
    var props by remember { mutableStateOf<List<PropiedadDeContacto>>(emptyList()) }
    var linea by remember { mutableStateOf<List<ItemLinea>>(emptyList()) }
    var visitaReciente by remember { mutableStateOf<Cita?>(null) }
    var equipo by remember { mutableStateOf<List<Miembro>>(emptyList()) }
    var vista by remember { mutableStateOf<ItemLinea.Grupo?>(null) }
    var nota by remember { mutableStateOf("") }
    var guardando by remember { mutableStateOf(false) }
    var contando by remember { mutableStateOf(false) }
    var editando by remember { mutableStateOf<String?>(null) }
    var agendar by remember { mutableStateOf<FichaContacto.AgendarDesdeFicha?>(null) }
    var nuevaOp by remember { mutableStateOf(false) }
    val dictado = rememberDictado()
    var base by remember { mutableStateOf("") }
    var dictandoDesde by remember { mutableStateOf<Instant?>(null) }

    val primer = Charla.primerNombre(c?.nombre)
    val yo = estado.usuario?.id ?: ""
    val nombres: Map<String, String> = equipo.associate { it.usuarioId to it.completo }

    /** Mensajes, visitas, operaciones, notas y por dónde llegó; lo más nuevo arriba. */
    fun armarLinea(c: Contacto, notas: List<NotaContacto>, citas: List<Cita>, ops: List<Operacion>, mensajes: List<MensajeWA>): List<ItemLinea> {
        val quien = { id: String?, n: String? -> if (id != null && id == yo) "vos" else Charla.primerNombre(id?.let { nombres[it] } ?: n) }
        val items = mutableListOf<ItemLinea>()
        val pre = mapOf("visita" to "Visita a", "llamado" to "Llamado por", "firma" to "Firma de", "tasacion" to "Tasación de", "otro" to "")
        for (x in citas) {
            val prop = x.propiedadTitulo ?: ""
            val p = pre[x.tipo ?: ""] ?: ""
            val titulo = if (prop.isNotEmpty() && p.isNotEmpty()) "$p $prop · ${Formato.horaDe(x.empiezaEn)}" else "${x.titulo ?: x.tipoTexto} · ${Formato.horaDe(x.empiezaEn)}"
            val con = quien(x.agenteId, x.agenteNombre)
            val est = mapOf("hecha" to "hecha", "cancelada" to "cancelada")[x.estado ?: ""] ?: (if (x.recordadoEn != null) "le mandamos el recordatorio" else "")
            items.add(
                ItemLinea(
                    id = "c" + x.id, grupo = ItemLinea.Grupo.visitas, en = x.empiezaEn, icono = if (x.tipo == "firma") "signature" else "calendar",
                    tono = if (x.estado == "cancelada") Tono.neutro else Tono.oro, titulo = titulo,
                    detalle = listOfNotNull(if (con.isEmpty()) null else "con $con", est.nonEmpty).joinToString(" · ").nonEmpty,
                ),
            )
        }
        val etapa = mapOf("nuevo" to "Nueva", "contactado" to "Contactado", "visita" to "En visita", "reserva" to "Reservó", "cierre" to "Cerró")
        for (o in ops) {
            val prop = o.nombre
            val caida = o.caidaEn != null
            val sena = o.sena?.let { " · ${Formato.plata(it, o.senaMoneda ?: o.moneda ?: "USD")}" } ?: ""
            val en = o.caidaEn ?: o.actualizadoEn ?: Instant.now()
            val con = quien(o.agenteId, o.agenteNombre)
            val detalle = listOfNotNull(
                Formato.dia(en),
                if (caida) o.caidaMotivo else if ((o.valor ?: 0.0) > 0) "ofrece ${Formato.plata(o.valor ?: 0.0, o.moneda ?: "USD")}" else null,
                if (con.isEmpty()) null else "con $con",
            ).joinToString(" · ")
            items.add(
                ItemLinea(
                    id = "o" + o.id, grupo = ItemLinea.Grupo.otros, en = en,
                    icono = if (caida) "heart.slash" else if (o.etapa == "reserva") "bookmark" else if (o.etapa == "cierre") "hands.sparkles" else "rectangle.split.3x1",
                    tono = if (caida) Tono.rojo else if (o.etapa == "reserva") Tono.oro else if (o.etapa == "cierre") Tono.verde else Tono.azul,
                    titulo = if (caida) "Se cayó $prop" else "${etapa[o.etapa] ?: o.etapa} $prop${if (o.etapa == "reserva") sena else ""}", detalle = detalle,
                ),
            )
        }
        for (n in notas) {
            val autor = quien(n.autorId, null)
            val titulo = if (n.tipo == "llamada") "Llamada · ${Formato.horaDe(n.ocurrioEn)}" else if (autor == "vos") "Nota tuya" else if (autor.isEmpty()) "Nota" else "Nota de $autor"
            items.add(
                ItemLinea(
                    id = "n" + n.id, grupo = ItemLinea.Grupo.notas, en = n.ocurrioEn,
                    icono = if (n.tipo == "llamada") "phone" else if (n.tipo == "visita") "calendar.badge.checkmark" else "note.text",
                    tono = Tono.neutro, titulo = titulo, detalle = n.texto, cita = true,
                ),
            )
        }
        for (m in mensajes) {
            val t = m.texto?.nonEmpty ?: continue
            items.add(
                ItemLinea(
                    id = "m" + m.id, grupo = ItemLinea.Grupo.mensajes, en = m.ocurrioEn, icono = "bubble.left", tono = Tono.verde,
                    titulo = "WhatsApp · ${Formato.horaDe(m.ocurrioEn)}", burbuja = t, mio = m.saliente,
                ),
            )
        }
        items.add(
            ItemLinea(
                id = "llego", grupo = ItemLinea.Grupo.otros, en = c.creadoEn ?: Instant.now(), icono = "globe", tono = Tono.azul,
                titulo = c.origen?.nonEmpty?.let { "Llegó por $it" } ?: "Lo cargaron en Corredor", detalle = c.creadoEn?.let { Formato.dia(it) },
            ),
        )
        return items.sortedByDescending { it.en }
    }

    suspend fun cargar() {
        try {
            c = estado.api.get<Contacto>("/v1/contactos/$id")
            error = null
        } catch (e: ErrorApi) {
            if (c == null) error = e
            return
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return
        }
        val cc = c ?: return
        val p: List<PropiedadDeContacto> = estado.api.opcional<List<PropiedadDeContacto>>("/v1/contactos/$id/propiedades") ?: emptyList()
        props = p.take(6)
        if (equipo.isEmpty()) equipo = estado.api.opcional<List<Miembro>>("/v1/equipo") ?: emptyList()
        val notas: List<NotaContacto> = estado.api.opcional<List<NotaContacto>>("/v1/contactos/$id/notas") ?: emptyList()
        val citas: List<Cita> = estado.api.opcional<List<Cita>>("/v1/agenda", listOf("contacto" to id, "rango" to "todas", "limite" to "60")) ?: emptyList()
        val activas: List<Operacion> = estado.api.opcional<List<Operacion>>("/v1/operaciones", listOf("contacto" to id)) ?: emptyList()
        val caidas: List<Operacion> = estado.api.opcional<List<Operacion>>("/v1/operaciones", listOf("contacto" to id, "caidas" to "si")) ?: emptyList()
        @Serializable data class Conv(val id: String)
        val convs: List<Conv> = estado.api.opcional<List<Conv>>("/v1/whatsapp/conversaciones", listOf("contacto" to id, "limite" to "1")) ?: emptyList()
        var mensajes: List<MensajeWA> = emptyList()
        convs.firstOrNull()?.let { cv -> mensajes = estado.api.opcional<List<MensajeWA>>("/v1/whatsapp/conversaciones/${cv.id}/mensajes", listOf("limite" to "20")) ?: emptyList() }
        linea = armarLinea(cc, notas = notas, citas = citas, ops = activas + caidas, mensajes = mensajes)
        val ahora = Instant.now()
        visitaReciente = citas.filter { it.tipo == "visita" && it.estado == "pendiente" && it.terminaEn <= ahora && Duration.between(it.terminaEn, ahora).seconds < 6 * 3600 }
            .maxByOrNull { it.terminaEn }
    }

    /** Guarda la nota (con «Deshacer», que la borra). Sin señal queda en la cola. */
    suspend fun anotarNota(): Boolean {
        dictado.parar()
        val t = nota.trim()
        if (t.isEmpty()) return false
        @Serializable data class Cuerpo(val tipo: String = "nota", val texto: String)
        guardando = true
        try {
            val n: NotaContacto = estado.api.enviar("POST", "/v1/contactos/$id/notas", Cuerpo(texto = t.take(5000)))
            nota = ""
            estado.avisar("Nota guardada") {
                alcance.launch {
                    try {
                        estado.api.hacer("DELETE", "/v1/contactos/$id/notas/${n.id}")
                        estado.version += 1
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        estado.avisar(e)
                    }
                }
            }
            cargar()
            return true
        } catch (e: ErrorApi) {
            if (e.sinConexion) {
                return try {
                    estado.cola.enviar("POST", "/v1/contactos/$id/notas", Cuerpo(texto = t), etiqueta = "Nota en ${c?.nombre ?: ""}")
                    nota = ""
                    estado.avisar("Sin señal · la nota se guarda cuando vuelva")
                    true
                } catch (e2: CancellationException) {
                    throw e2
                } catch (e2: Exception) {
                    estado.avisar(e2)
                    false
                }
            }
            estado.avisarReintentar(Estado.texto(e, si = "No se guardó")) { alcance.launch { anotarNota() } }
            return false
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            estado.avisarReintentar(Estado.texto(e, si = "No se guardó")) { alcance.launch { anotarNota() } }
            return false
        } finally {
            guardando = false
        }
    }

    suspend fun contar(v: Cita, como: String) {
        @Serializable data class EstadoCita(val estado: String = "hecha")
        @Serializable data class Nota(val tipo: String = "visita", val texto: String)
        val frase = mapOf("le_gusto" to "Le gustó", "duda" to "Tiene dudas", "no_va" to "No va")[como] ?: ""
        contando = true
        try {
            estado.api.hacer("PATCH", "/v1/agenda/${v.id}", EstadoCita())
            val texto = "$frase ${v.propiedadTitulo ?: v.titulo ?: ""}".trim()
            estado.api.hacer("POST", "/v1/contactos/$id/notas", Nota(texto = texto))
            estado.avisar("Anotado en la ficha")
            estado.version += 1
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            estado.avisar(e)
        } finally {
            contando = false
        }
    }

    /** A la papelera; «Deshacer» la restaura. */
    suspend fun borrar() {
        val n = c?.nombre ?: "El contacto"
        try {
            estado.api.hacer("DELETE", "/v1/contactos/$id")
            cerrar()
            estado.avisar("$n fue a la papelera") {
                alcance.launch {
                    try {
                        estado.api.hacer("POST", "/v1/papelera/contacto/$id/restaurar")
                        estado.version += 1
                        estado.abrir(AFichaContacto(id = id))
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
        }
    }

    LaunchedEffect(estado.version) { cargar() }
    LaunchedEffect(dictado.texto) {
        val t = dictado.texto
        if (dictado.grabando) nota = listOf(base, t).filter { it.isNotEmpty() }.joinToString(" ")
    }
    LaunchedEffect(dictado.grabando) { dictandoDesde = if (dictado.grabando) Instant.now() else null }
    // .userActivity (Handoff y Spotlight) no tiene equivalente en Android.

    // MARK: Partes

    @Composable
    fun Pill(t: String, f: androidx.compose.ui.graphics.Color, tinta: androidx.compose.ui.graphics.Color) {
        Box(Modifier.height(24.dp).clip(CircleShape).background(f).padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
            Texto(t, Estilo(12f, Peso.semibold), tinta)
        }
    }

    @Composable
    fun Cabecera(c: Contacto) {
        val t = Contactos.tono(c.tipo)
        val agente = c.agenteId?.let { nombres[it] }
        val origen = listOfNotNull(
            agente?.let { "de ${Charla.primerNombre(it)}" },
            c.origen?.nonEmpty?.let { o -> "llegó por $o${c.creadoEn?.let { " el ${FichaContacto.diaMes(it)}" } ?: ""}" },
        ).joinToString(" · ")
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(84.dp).clip(CircleShape).background(t.fondo), contentAlignment = Alignment.Center) {
                Texto(Formato.iniciales(c.nombre), Estilo(30f, Peso.bold), t.tinta)
            }
            Texto(c.nombre, Estilo(26f, Peso.bold, -0.02f), C.texto, alinear = TextAlign.Center)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Pill(TextosContacto.rol(c.tipo), C.azulFondo, C.azulTinta)
                c.etiquetas?.firstOrNull()?.let { Pill(it, C.hundida, C.texto2) }
            }
            if (origen.isNotEmpty()) Texto(origen, Estilo.footnote, C.texto2)
        }
    }

    @Composable
    fun Accion(i: String, t: String, modifier: Modifier, habilitado: Boolean = true, hacer: () -> Unit) {
        Tocable(alTocar = hacer, modifier = modifier.alpha(if (habilitado) 1f else 0.5f), habilitado = habilitado) {
            Column(Modifier.fillMaxWidth().heightIn(min = 64.dp).tarjeta(14.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically)) {
                Icono(i, 21f, C.accion)
                Texto(t, Estilo(12f, Peso.semibold), C.accion)
            }
        }
    }

    @Composable
    fun Acciones(c: Contacto) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tocable(alTocar = { alcance.launch { Contactos.abrirChat(c.id, estado = estado) } }, modifier = Modifier.weight(1f)) {
                Column(
                    Modifier.fillMaxWidth().heightIn(min = 64.dp).clip(RoundedCornerShape(14.dp)).background(C.accion),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
                ) {
                    Icono("bubble.left.fill", 21f, C.sobreAccion)
                    Texto("WhatsApp", Estilo(12f, Peso.semibold), C.sobreAccion)
                }
            }
            Accion("phone.fill", "Llamar", Modifier.weight(1f), habilitado = c.telefono != null) { Afuera.llamar(c.telefono) }
            if (estado.escribe) Accion("calendar", "Agendar", Modifier.weight(1f)) { agendar = FichaContacto.AgendarDesdeFicha(dia = Instant.now(), hora = null) }
            c.email?.let { m -> Accion("envelope.fill", "Mail", Modifier.weight(1f)) { abrirMail(ctx, m) } }
        }
    }

    /** «¿Cómo le fue en Mendoza 2350?»: la visita pasa a hecha y queda la nota. */
    @Composable
    fun ComoFue(v: Cita) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(C.avisoFondo).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                Icono("questionmark.circle.fill", 22f, C.avisoTinta)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Texto("¿Cómo le fue en ${v.propiedadTitulo ?: v.titulo ?: "la visita"}?", Estilo.subheadFuerte, C.texto)
                    Texto("La visita terminó ${Formato.hace(v.terminaEn)}", Estilo.footnote, C.texto2)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("le_gusto" to "Le gustó", "duda" to "Duda", "no_va" to "No va").forEach { (k, t) ->
                    BotonTonal(t, { alcance.launch { contar(v, k) } }, habilitado = !contando)
                }
            }
        }
    }

    @Composable
    fun TarjetaBusca(c: Contacto) {
        val busca = TextosContacto.buscaLarga(c)
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(C.okFondo).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Texto("QUÉ BUSCA", Estilo(12f, Peso.bold, 0.5f / 12f), C.okTinta)
            Texto(if (busca.isEmpty()) "Todavía no sabemos qué busca $primer" else busca, Estilo.headline, C.texto)
            FlowLayout(espacio = 6.dp) {
                (c.buscaDetalles ?: emptyList()).forEach { d ->
                    Box(Modifier.height(28.dp).clip(CircleShape).background(C.superficie).padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
                        Texto(d, Estilo(13f, Peso.medium), C.texto)
                    }
                }
                if (estado.escribe) {
                    Box(Modifier.height(28.dp).clickable { editando = "busca" }.padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
                        Texto(if (busca.isEmpty()) "Cargar qué busca" else "Editar", Estilo(13f, Peso.bold), C.accion)
                    }
                }
            }
        }
    }

    @Composable
    fun LeInteresan(c: Contacto) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TituloBloque(texto = "Le pueden interesar", cuenta = "${props.size} de tu cartera")
            Column(Modifier.fillMaxWidth().tarjeta()) {
                props.forEachIndexed { i, p ->
                    key(p.id) {
                        val (e, tono) = p.estadoTexto
                        Row(Modifier.fillMaxWidth().lineaArriba(i > 0).padding(horizontal = 12.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Tocable(alTocar = { estado.abrir(AFichaPropiedad(id = p.propiedadId)) }, modifier = Modifier.weight(1f)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(56.dp, 42.dp).clip(RoundedCornerShape(8.dp))) {
                                        Foto(url = p.miniatura(estado.api.base.toString(), inmobiliaria = estado.sesion?.inmobiliariaId), modifier = Modifier.matchParentSize())
                                    }
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                        Texto(p.nombre.split(",").firstOrNull() ?: "", Estilo.filaLista, C.texto, lineas = 1)
                                        Texto(e, Estilo.footnote, if (tono == Tono.verde) C.okTinta else if (tono == Tono.oro) C.avisoTinta else C.texto2, lineas = 1)
                                    }
                                }
                            }
                            if ((p.estado ?: "nueva") == "nueva" && estado.escribe) {
                                BotonTonal("Mandar", { estado.abrir(AMandarPropiedad(id = p.propiedadId, contacto = c.id)) })
                            } else if (p.precio != null) {
                                Texto(p.precioTexto, Estilo(13f, Peso.bold), C.texto)
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun Dato(l: String, v: String, primera: Boolean, tocar: () -> Unit) {
        Tocable(alTocar = tocar, modifier = Modifier.lineaArriba(!primera)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Texto(l, Estilo.body16, C.texto2)
                Spacer(Modifier.width(12.dp))
                Texto(v, Estilo.body16, C.texto, Modifier.weight(1f), alinear = TextAlign.End)
            }
        }
    }

    @Composable
    fun Datos(c: Contacto) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TituloBloque(texto = "Datos")
            Column(Modifier.fillMaxWidth().tarjeta()) {
                Dato("Celular", c.telefono ?: "—", primera = true) { if (c.telefono != null) Afuera.llamar(c.telefono) }
                Dato("Mail", c.email ?: "—", primera = false) { c.email?.let { abrirMail(ctx, it) } }
                Dato("Etiquetas", if ((c.etiquetas ?: emptyList()).isEmpty()) "—" else (c.etiquetas ?: emptyList()).joinToString(", "), primera = false) {
                    if (estado.escribe) editando = "todo"
                }
            }
        }
    }

    // MARK: Todo con …

    val grupos: List<Pair<String, List<ItemLinea>>> = run {
        val out = mutableListOf<Pair<String, MutableList<ItemLinea>>>()
        for (it in linea) {
            if (vista != null && it.grupo != vista) continue
            val g = ItemLinea.grupoDeFecha(it.en)
            if (out.lastOrNull()?.first == g) out.last().second.add(it) else out.add(g to mutableListOf(it))
        }
        out.map { it.first to it.second.toList() }
    }

    @Composable
    fun Anotar() {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                Box(Modifier.padding(top = 3.dp)) { Icono("square.and.pencil", 17f, C.texto3) }
                val estilo = Estilo.body16
                BasicTextField(
                    value = nota, onValueChange = { nota = it }, minLines = 1, maxLines = 6,
                    textStyle = estilo.ts().copy(color = C.texto), cursorBrush = SolidColor(C.accion), modifier = Modifier.weight(1f),
                    decorationBox = { campo -> Box { if (nota.isEmpty()) Texto("Anotá algo de la charla…", estilo, C.texto3); campo() } },
                )
                Tocable(alTocar = {
                    if (dictado.grabando) dictado.parar() else { base = nota; dictado.empezar(base = nota) }
                }, descripcion = if (dictado.grabando) "Parar el dictado" else "Dictar la nota") {
                    Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) { Icono(if (dictado.grabando) "mic.fill" else "mic", 20f, if (dictado.grabando) C.error else C.accion) }
                }
            }
            val desde = dictandoDesde
            if (dictado.grabando && desde != null) {
                var ahora by remember { mutableStateOf(Instant.now()) }
                LaunchedEffect(desde) { while (true) { delay(1000); ahora = Instant.now() } }
                val s = maxOf(0, Duration.between(desde, ahora).seconds.toInt())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icono("waveform", 15f, C.error)
                    Texto("Escuchando… ${s / 60}:${String.format("%02d", s % 60)}", Estilo.footnote, C.texto2)
                }
            }
        }
    }

    @Composable
    fun Renglon(it: ItemLinea, ultimo: Boolean) {
        val linea2 = C.linea2
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(30.dp).clip(CircleShape).background(it.tono.fondo), contentAlignment = Alignment.Center) { Icono(it.icono, 14f, it.tono.tinta) }
                if (!ultimo) Box(Modifier.width(2.dp).weight(1f).background(linea2))
            }
            Column(Modifier.weight(1f).padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Texto(it.titulo, Estilo.subheadFuerte, C.texto)
                val b = it.burbuja
                val d = it.detalle
                if (b != null) {
                    val oscuro = LocalOscuro.current
                    Box(Modifier.clip(RoundedCornerShape(12.dp)).background(if (it.mio) (if (oscuro) hex(0x1F3B33) else hex(0xDCEBE3)) else C.hundida).padding(horizontal = 10.dp, vertical = 7.dp)) {
                        Texto(b, Estilo.texto14, C.texto)
                    }
                } else if (d != null) {
                    Texto(if (it.cita) "«$d»" else d, Estilo.texto14, C.texto2)
                }
            }
        }
    }

    @Composable
    fun TodoCon(c: Contacto) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            TituloBloque(texto = "Todo con $primer")
            Segmentado(opciones = FichaContacto.vistas, elegido = vista, alElegir = { vista = it })
            Column(Modifier.fillMaxWidth().tarjeta().padding(14.dp)) {
                if (estado.escribe) Box(Modifier.padding(bottom = 12.dp)) { Anotar() }
                grupos.forEach { (titulo, items) ->
                    key(titulo) {
                        Texto(titulo.uppercase(Formato.argentina), Estilo(12f, Peso.bold, 0.5f / 12f), C.texto3, Modifier.padding(vertical = 6.dp))
                        items.forEachIndexed { i, it -> key(it.id) { Renglon(it, ultimo = i == items.size - 1) } }
                    }
                }
                if (grupos.isEmpty()) Texto("Todavía no hay nada con $primer en esta vista.", Estilo.texto14, C.texto3)
            }
        }
    }

    /** «Guardar y agendar sábado» si la nota dice un día; si no, «Guardar». */
    @Composable
    fun PieNota() {
        val e = Entender.entender(nota)
        val d = e.dia
        val f = Fecha.fecha(d)
        if (d != null && f != null) {
            val texto = if (f == Formato.hoy()) "hoy" else if (f == Formato.hoy().plusDays(1)) "mañana" else AgendaRaizTextos.nombreDia(f)
            BotonPrincipal(if (guardando) "Guardando…" else "Guardar y agendar $texto", {
                alcance.launch { if (anotarNota()) agendar = FichaContacto.AgendarDesdeFicha(dia = mediodia(f), hora = e.hora) }
            }, habilitado = !guardando)
        } else {
            BotonPrincipal(if (guardando) "Guardando…" else "Guardar", { alcance.launch { anotarNota() } }, habilitado = !guardando)
        }
    }

    // MARK: Pantalla

    val cc = c
    PantallaApilada(
        titulo = "",
        acciones = {
            if (estado.escribe && cc != null) {
                MenuBoton(
                    opciones = listOf(
                        OpcionDeMenu("Editar", "pencil") { editando = "todo" },
                        OpcionDeMenu("Agendar visita", "calendar") { agendar = FichaContacto.AgendarDesdeFicha(dia = Instant.now(), hora = null) },
                        OpcionDeMenu("Nueva operación", "rectangle.split.3x1") { nuevaOp = true },
                        OpcionDeMenu("Mandar a la papelera", "trash", destructiva = true) { alcance.launch { borrar() } },
                    ),
                    descripcion = "Más",
                ) { Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) { Icono("ellipsis.circle", 20f, C.accion) } }
            }
        },
    ) {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
                val e = error
                if (cc != null) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Cabecera(cc)
                        Acciones(cc)
                        val v = visitaReciente
                        if (v != null && estado.escribe) ComoFue(v)
                        TarjetaBusca(cc)
                        if (props.isNotEmpty()) LeInteresan(cc)
                        Datos(cc)
                        TodoCon(cc)
                    }
                } else if (e != null) {
                    Box(Modifier.padding(top = 40.dp)) { EstadoError(que = "el contacto", error = e) { alcance.launch { cargar() } } }
                } else {
                    Box(Modifier.padding(16.dp)) { EsqueletoFilas(filas = 5) }
                }
            }
            // .safeAreaInset(edge: .bottom): el botón de guardar la nota, siempre a la vista.
            if (nota.trim().isNotEmpty()) {
                Box(Modifier.fillMaxWidth().background(C.fondo.copy(alpha = 0.95f)).navigationBarsPadding().imePadding().padding(horizontal = 20.dp, vertical = 8.dp)) { PieNota() }
            }
        }
    }

    val ed = editando
    Hoja(visible = ed != null && cc != null, alCerrar = { editando = null }, alturas = listOf(Altura.grande)) {
        if (cc != null && ed != null) key(ed) { EditarContacto(contacto = cc, soloBusca = ed == "busca") }
    }
    var ultimoAgendar by remember { mutableStateOf<FichaContacto.AgendarDesdeFicha?>(null) }
    if (agendar != null) ultimoAgendar = agendar
    Hoja(visible = agendar != null && cc != null, alCerrar = { agendar = null }, alturas = listOf(Altura.grande)) {
        val a = ultimoAgendar
        if (cc != null && a != null) key(a.id) { NuevaCita(contacto = cc.id to cc.nombre, dia = a.dia, hora = a.hora) }
    }
    Hoja(visible = nuevaOp && cc != null, alCerrar = { nuevaOp = false }, alturas = listOf(Altura.grande)) {
        if (cc != null) NuevaOperacion(contacto = Resultado(id = cc.id, tipo = "contacto", titulo = cc.nombre, subtitulo = null))
    }
}

// MARK: - Alta y edición

/** Un campo de número con su etiqueta, para los formularios (`TextField(value:format: .number)`). */
private fun numeroDe(t: String): Double? = t.filter { it.isDigit() }.toDoubleOrNull()

/** Alta o edición de un contacto (también lo trae de la agenda del teléfono). Al editar, el PUT lleva
 *  el contacto entero con lo cambiado encima (si no, la API borra lo que esta pantalla no muestra).
 *  `soloBusca`: sólo «Qué busca» (la tarjeta de la ficha). */
@Composable
fun EditarContacto(contacto: Contacto? = null, soloBusca: Boolean = false) {
    val estado = LocalEstado.current
    val cerrar = LocalCerrarHoja.current
    val alcance = rememberCoroutineScope()
    var c by remember { mutableStateOf(contacto ?: Contacto(id = "", nombre = "", tipo = "comprador")) }
    var barrios by remember { mutableStateOf((contacto?.buscaBarrios ?: emptyList()).joinToString(", ")) }
    var detalles by remember { mutableStateOf((contacto?.buscaDetalles ?: emptyList()).joinToString(", ")) }
    var etiquetas by remember { mutableStateOf((contacto?.etiquetas ?: emptyList()).joinToString(", ")) }
    var desdeTexto by remember { mutableStateOf(contacto?.buscaDesde?.let { it.toLong().toString() } ?: "") }
    var hastaTexto by remember { mutableStateOf(contacto?.buscaHasta?.let { it.toLong().toString() } ?: "") }
    var equipo by remember { mutableStateOf<List<Miembro>>(emptyList()) }
    var guardando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    /** Los errores por campo que devolvió la API (problem+json `issues`). */
    var errores by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    val agenda = rememberContactos { nombre, telefono, email ->
        c = c.copy(nombre = nombre, telefono = telefono.nonEmpty, email = email.nonEmpty, origen = c.origen ?: "Agenda del celular")
    }

    LaunchedEffect(Unit) { if (contacto != null && !soloBusca) equipo = estado.api.opcional<List<Miembro>>("/v1/equipo") ?: emptyList() }

    suspend fun guardar() {
        guardando = true
        val lista = { t: String -> t.split(",").map { it.trim() }.filter { it.isNotEmpty() } }
        c = c.copy(buscaBarrios = lista(barrios), buscaDetalles = lista(detalles), buscaDesde = numeroDe(desdeTexto), buscaHasta = numeroDe(hastaTexto))
        if (!soloBusca) c = c.copy(etiquetas = lista(etiquetas))
        @Serializable data class Cuerpo(
            val nombre: String, val telefono: String?, val email: String?, val tipo: String?, val origen: String?, val notas: String?,
            val buscaOperacion: String?, val buscaTipo: String?, val buscaMoneda: String?, val buscaDesde: Double?, val buscaHasta: Double?,
            val buscaAmbientes: Int?, val buscaBarrios: List<String>?, val buscaDetalles: List<String>?, val etiquetas: List<String>?, val agenteId: String?,
        )
        val cuerpo = Cuerpo(
            nombre = c.nombre.trim(), telefono = c.telefono, email = c.email, tipo = c.tipo, origen = c.origen, notas = c.notas,
            buscaOperacion = c.buscaOperacion, buscaTipo = c.buscaTipo, buscaMoneda = c.buscaMoneda, buscaDesde = c.buscaDesde, buscaHasta = c.buscaHasta,
            buscaAmbientes = c.buscaAmbientes, buscaBarrios = c.buscaBarrios, buscaDetalles = c.buscaDetalles, etiquetas = c.etiquetas ?: emptyList(),
            agenteId = if (contacto == null) estado.usuario?.id else c.agenteId,
        )
        val metodo = if (contacto == null) "POST" else "PUT"
        val ruta = contacto?.let { "/v1/contactos/${it.id}" } ?: "/v1/contactos"
        try {
            // Primero directo (para ver los errores por campo); sin señal, a la cola.
            val campos = estado.api.hacerValidando(metodo, ruta, cuerpo)
            if (campos.isNotEmpty()) {
                errores = campos
                error = "Revisá los datos marcados."
                return
            }
            if (contacto != null) estado.avisar("Guardado") else estado.sugerir("Contacto guardado", "Agendar", AAgendaDia())
            estado.version += 1
            cerrar()
        } catch (e: ErrorApi) {
            if (e.sinConexion) {
                try {
                    estado.cola.enviar(metodo, ruta, cuerpo, etiqueta = if (contacto == null) "Alta de ${c.nombre}" else "Cambios en ${c.nombre}")
                    estado.avisar("Sin señal · se guarda cuando vuelva")
                    estado.version += 1
                    cerrar()
                } catch (e2: CancellationException) {
                    throw e2
                } catch (e2: Exception) {
                    error = Estado.texto(e2, si = "No se guardó.")
                }
            } else {
                error = Estado.texto(e, si = "No se guardó.")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = Estado.texto(e, si = "No se guardó.")
        } finally {
            guardando = false
        }
    }

    @Composable
    fun Seccion(titulo: String? = null, pie: String? = null, contenido: @Composable ColumnScope.() -> Unit) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (titulo != null) Texto(titulo.uppercase(Formato.argentina), Estilo(13f, Peso.regular), C.texto2, Modifier.padding(horizontal = 16.dp))
            Column(Modifier.fillMaxWidth().tarjeta(12.dp).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = contenido)
            if (pie != null) Texto(pie, Estilo.footnote, C.texto2, Modifier.padding(horizontal = 16.dp))
        }
    }

    @Composable
    fun FilaSelector(titulo: String, contenido: @Composable () -> Unit) {
        Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
            Texto(titulo, Estilo.body, C.texto, Modifier.weight(1f))
            contenido()
        }
    }

    Column(Modifier.fillMaxSize()) {
        BarraHoja(
            titulo = if (soloBusca) "Qué busca" else (if (contacto == null) "Contacto nuevo" else "Editar contacto"),
            izquierda = { BotonHoja("Cancelar", alTocar = { cerrar() }) },
        ) {
            val habilitado = c.nombre.trim().isNotEmpty() && !guardando
            Texto("Guardar", Estilo.headline, if (habilitado) C.accion else C.texto3, Modifier.clickable(enabled = habilitado) { alcance.launch { guardar() } })
        }
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            error?.let { MensajeError(texto = it) }
            if (!soloBusca) {
                Seccion {
                    CampoCorredor(c.nombre, { c = c.copy(nombre = it) }, placeholder = "Nombre", mayusculas = KeyboardCapitalization.Words)
                    errores["nombre"]?.let { MensajeError(texto = it) }
                    CampoCorredor(c.telefono ?: "", { c = c.copy(telefono = it.nonEmpty) }, placeholder = "Celular", teclado = KeyboardType.Phone)
                    errores["telefono"]?.let { MensajeError(texto = it) }
                    CampoCorredor(c.email ?: "", { c = c.copy(email = it.nonEmpty) }, placeholder = "Mail", teclado = KeyboardType.Email, mayusculas = KeyboardCapitalization.None)
                    errores["email"]?.let { MensajeError(texto = it) }
                    FilaSelector("Es") { SelectorMenu(opciones = TextosContacto.tiposElegir, elegido = c.tipo ?: "comprador", alElegir = { c = c.copy(tipo = it) }, descripcion = "Es") }
                    if (contacto != null) {
                        FilaSelector("De") {
                            SelectorMenu(
                                opciones = listOf("" to "Sin agente") + equipo.filter { it.rol != "lector" }.map { it.usuarioId to it.completo },
                                elegido = c.agenteId ?: "", alElegir = { c = c.copy(agenteId = it.nonEmpty) }, descripcion = "De",
                            )
                        }
                    }
                    CampoCorredor(c.origen ?: "", { c = c.copy(origen = it.nonEmpty) }, placeholder = "Llegó por (Zonaprop, WhatsApp, un cartel…)")
                    if (contacto == null) {
                        Tocable(alTocar = agenda) { ConIcono("Traer de la agenda del teléfono", "person.crop.circle", Estilo.body, C.accion, Modifier.heightIn(min = 44.dp)) }
                    }
                }
                Seccion(titulo = "Etiquetas", pie = "Separadas por comas: Con crédito, Con mascotas") {
                    CampoCorredor(etiquetas, { etiquetas = it }, placeholder = "Etiquetas")
                }
            }
            Seccion(titulo = "Qué busca") {
                FilaSelector("Quiere") {
                    SelectorMenu(
                        opciones = listOf("" to "—", "venta" to "Comprar", "alquiler" to "Alquilar", "temporario" to "Temporada"),
                        elegido = c.buscaOperacion ?: "", alElegir = { c = c.copy(buscaOperacion = it.nonEmpty) }, descripcion = "Quiere",
                    )
                }
                FilaSelector("Tipo") {
                    SelectorMenu(
                        opciones = listOf("" to "—") + Propiedad.tipos,
                        elegido = c.buscaTipo ?: "", alElegir = { c = c.copy(buscaTipo = it.nonEmpty) }, descripcion = "Tipo",
                    )
                }
                Segmentado(opciones = listOf("USD" to "USD", "ARS" to "ARS"), elegido = c.buscaMoneda ?: "USD", alElegir = { c = c.copy(buscaMoneda = it) })
                CampoCorredor(desdeTexto, { desdeTexto = it.filter { ch -> ch.isDigit() } }, placeholder = "Desde", teclado = KeyboardType.Number)
                CampoCorredor(hastaTexto, { hastaTexto = it.filter { ch -> ch.isDigit() } }, placeholder = "Hasta", teclado = KeyboardType.Number)
                // Stepper("Ambientes: …", in: 0...10)
                Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
                    Texto("Ambientes: ${c.buscaAmbientes?.toString() ?: "—"}", Estilo.body, C.texto, Modifier.weight(1f))
                    val n = c.buscaAmbientes ?: 0
                    Row(Modifier.clip(RoundedCornerShape(9.dp)).background(C.segmentado)) {
                        Tocable(alTocar = { val x = maxOf(0, n - 1); c = c.copy(buscaAmbientes = if (x == 0) null else x) }, habilitado = n > 0, descripcion = "Menos") {
                            Box(Modifier.size(46.dp, 32.dp), contentAlignment = Alignment.Center) { Icono("minus", 15f, if (n > 0) C.texto else C.texto3) }
                        }
                        Tocable(alTocar = { val x = minOf(10, n + 1); c = c.copy(buscaAmbientes = x) }, habilitado = n < 10, descripcion = "Más") {
                            Box(Modifier.size(46.dp, 32.dp), contentAlignment = Alignment.Center) { Icono("plus", 15f, if (n < 10) C.texto else C.texto3) }
                        }
                    }
                }
                CampoCorredor(barrios, { barrios = it }, placeholder = "Barrios (Belgrano, Núñez)")
            }
            Seccion(pie = "Con balcón, Apto crédito, Hasta 30 años") {
                CampoCorredor(detalles, { detalles = it }, placeholder = "Además pide")
            }
            if (!soloBusca) {
                Seccion(titulo = "Notas") {
                    CampoCorredor(c.notas ?: "", { c = c.copy(notas = it.nonEmpty) }, placeholder = "Notas internas", lineasMin = 1, lineasMax = 8)
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

/** Como `hacer`, pero si la API rechaza los datos (400/422) devuelve los errores por campo
 *  (problem+json `issues`: path → message) en vez de tirar el error. Sin señal tira `ErrorApi.sinRed`.
 *  En iOS es una extensión de Api que vive en esta pantalla (FichaContacto.swift). */
suspend inline fun <reified C> Api.hacerValidando(metodo: String, ruta: String, cuerpo: C): Map<String, String> =
    try {
        pedir(metodo, ruta, cuerpo = Api.json.encodeToString(cuerpo).toByteArray(), envio = UUID.randomUUID().toString())
        emptyMap()
    } catch (e: ErrorApi) {
        if ((e.estado == 400 || e.estado == 422) && e.campos.isNotEmpty()) e.campos else throw e
    }
