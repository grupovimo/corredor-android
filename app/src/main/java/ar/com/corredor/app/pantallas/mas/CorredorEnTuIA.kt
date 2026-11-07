package ar.com.corredor.app.pantallas.mas

import ar.com.corredor.app.app.PantallaApilada

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import ar.com.corredor.app.app.LocalEstado
import ar.com.corredor.app.nucleo.*
import ar.com.corredor.app.tema.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.ZoneId

// Clon de Pantallas/Mas/CorredorEnTuIA.swift.

// MARK: - Corredor en tu IA

/** Lo estático de CorredorEnTuIA (sus modelos, los pedidos de ejemplo y los textos de cada conexión). */
object CorredorEnTuIA {
    /** `GET /v1/ia/ajustes`: lo que eligió la dueña, el link para pegar y el aviso de la Ley 25.326 (texto). */
    @Serializable
    data class Ajustes(val habilitado: Boolean? = null, val agendar: Boolean? = null, val mover: Boolean? = null, val url: String? = null, val aviso: String? = null)

    /** `GET /v1/ia/conexiones`: de quién es, con qué asistente y qué permisos le dio. */
    @Serializable
    data class Conexion(
        val id: String,
        val usuarioId: String? = null,
        val personaNombre: String? = null,
        val rol: String? = null,
        val cliente: String? = null,
        val permisos: List<String>? = null,
        val creadaEn: FechaApi? = null,
        val usadaEn: FechaApi? = null,
    )

    /** `GET /v1/ia/registro` (mcp.Accion): una entrada de lo que hizo la IA. */
    @Serializable
    data class AccionIA(
        val id: Int,
        val personaNombre: String? = null,
        val cliente: String? = null,
        val herramienta: String? = null,
        val resultado: String? = null,
        val detalle: String? = null,
        val creadoEn: FechaApi,
    )

    val pedidos = listOf(
        "«¿Quién busca 3 ambientes en Belgrano hasta 180 mil y todavía no vio Mendoza 2350?»",
        "«Resumime cómo vino octubre para mandárselo a mi socio.»",
        "«Escribí la descripción de Soler 4120 con lo que anoté en la visita.»",
        "«¿Qué inquilinos deben más de un mes en el consorcio de Cabildo?»",
    )

    fun primerNombre(s: String?): String = s?.split(" ")?.firstOrNull { it.isNotEmpty() } ?: s ?: ""

    /** «leer + borradores + agendar · usado hoy». */
    fun detalle(c: Conexion): String {
        val permisos = (c.permisos ?: emptyList()).joinToString(" + ")
        val u = c.usadaEn
        val usada = if (u != null) {
            val zona = ZoneId.systemDefault()
            if (u.atZone(zona).toLocalDate() == Instant.now().atZone(zona).toLocalDate()) "usado hoy" else "usado ${Relativa.de(u)}"
        } else "todavía sin usar"
        return if (permisos.isEmpty()) usada else "$permisos · $usada"
    }

    @Serializable
    data class Cuerpo(val habilitado: Boolean, val agendar: Boolean, val mover: Boolean) {
        constructor(a: Ajustes) : this(a.habilitado ?: false, a.agendar ?: false, a.mover ?: false)
    }
}

/** 43b Corredor en tu IA: el dueño la habilita y elige lo opcional (agendar, mover); todos ven la dirección para pegar
 *  en su asistente, las conexiones (cada uno las suyas, el dueño todas) con «Desconectar», lo que hizo la IA hoy y qué
 *  se le puede pedir. */
@Composable
fun CorredorEnTuIA() {
    val estado = LocalEstado.current
    val ctx = LocalContext.current
    val alcance = rememberCoroutineScope()
    var conexiones by remember { mutableStateOf<List<CorredorEnTuIA.Conexion>>(emptyList()) }
    var hoy by remember { mutableStateOf<List<CorredorEnTuIA.AccionIA>>(emptyList()) }
    var ajustes by remember { mutableStateOf<CorredorEnTuIA.Ajustes?>(null) }
    var error by remember { mutableStateOf<ErrorApi?>(null) }
    var sacando by remember { mutableStateOf<Set<String>>(emptySet()) }
    val duenio = estado.esDuenio
    val habilitado = ajustes?.habilitado ?: false

    suspend fun cargar() {
        try {
            val a: CorredorEnTuIA.Ajustes = estado.api.get("/v1/ia/ajustes")
            ajustes = a
            error = null
        } catch (e: CancellationException) { throw e } catch (e: ErrorApi) { if (ajustes == null) error = e; return } catch (e: Exception) { return }
        estado.api.opcional<List<CorredorEnTuIA.Conexion>>("/v1/ia/conexiones")?.let { conexiones = it }
        estado.api.opcional<List<CorredorEnTuIA.AccionIA>>("/v1/ia/registro", listOf("desde" to Formato.diaISO(Instant.now()), "limite" to "6"))?.let { hoy = it }
    }

    suspend fun guardar(a: CorredorEnTuIA.Ajustes, antes: CorredorEnTuIA.Ajustes, texto: String) {
        try {
            val nuevos: CorredorEnTuIA.Ajustes = estado.api.enviar("PUT", "/v1/ia/ajustes", CorredorEnTuIA.Cuerpo(a))
            ajustes = nuevos
            estado.avisar(texto) {
                alcance.launch {
                    try {
                        val v: CorredorEnTuIA.Ajustes = estado.api.enviar("PUT", "/v1/ia/ajustes", CorredorEnTuIA.Cuerpo(antes))
                        ajustes = v
                    } catch (e: CancellationException) { throw e } catch (e: Exception) { estado.avisar(e) }
                }
            }
        } catch (e: CancellationException) { throw e } catch (e: Exception) {
            ajustes = antes
            val x = e as? ErrorApi
            estado.avisar("No se guardó · ${x?.let { it.detalle.ifEmpty { it.titulo } } ?: "Probá de nuevo."}", ok = false)
        }
    }

    /** Cada interruptor guarda al toque, con «Deshacer» (vuelve a los valores de antes). */
    fun cambiar(cambio: (CorredorEnTuIA.Ajustes, Boolean) -> CorredorEnTuIA.Ajustes, texto: (Boolean) -> String): (Boolean) -> Unit = { v ->
        val antes = ajustes
        if (antes != null) {
            val despues = cambio(antes, v)
            ajustes = despues
            alcance.launch { guardar(despues, antes, texto(v)) }
        }
    }

    suspend fun desconectar(c: CorredorEnTuIA.Conexion) {
        sacando = sacando + c.id
        try {
            estado.api.hacer("DELETE", "/v1/ia/conexiones/${c.id}")
            estado.avisar("Desconectaste ${c.cliente ?: "la IA"} de ${CorredorEnTuIA.primerNombre(c.personaNombre)}")
            cargar()
        } catch (e: CancellationException) { throw e } catch (e: Exception) { estado.avisar(e) } finally { sacando = sacando - c.id }
    }

    @Composable
    fun habilitar() {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 11.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            Icono(if (habilitado) "checkmark.circle.fill" else "power", 20f, if (habilitado) C.okTinta else C.texto2)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Texto(if (habilitado) "Habilitada para tu inmobiliaria" else "Apagada para tu inmobiliaria", Estilo.filaLista, C.texto)
                Texto(Formato.sinViudas(
                    if (habilitado) "Cada persona conecta su asistente con su propia cuenta y ve sólo lo que su rol permite."
                    else if (duenio) "Prendela para que tu equipo pueda conectar su asistente." else "La prende el dueño o la dueña de la inmobiliaria.",
                ), Estilo.footnote, C.texto2)
            }
            Llave(habilitado, cambiar({ a, v -> a.copy(habilitado = v) }) { if (it) "Corredor en tu IA, habilitada" else "Corredor en tu IA, apagada" },
                habilitado = duenio, descripcion = "Habilitar Corredor en tu IA")
        }
    }

    @Composable
    fun paso(n: Int, t: String) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier.size(24.dp).clip(CircleShape).background(if (n == 3) Color.Transparent else C.accion)
                    .then(if (n == 3) Modifier.border(1.5.dp, C.accion, CircleShape) else Modifier),
                contentAlignment = Alignment.Center,
            ) { Texto("$n", Estilo(13f, Peso.bold), if (n == 3) C.accion else C.sobreAccion) }
            Texto(t, Estilo.filaLista, C.texto, Modifier.weight(1f))
        }
    }

    @Composable
    fun permiso(icono: String, color: Color, t: String, d: String, prendido: Boolean, alCambiar: (Boolean) -> Unit, bloqueado: Boolean) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 11.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            IconoFila(icono, color)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Texto(t, Estilo.filaLista, C.texto)
                Texto(d, Estilo.footnote, C.texto2)
            }
            Llave(prendido, alCambiar, habilitado = !bloqueado, descripcion = t)
        }
    }

    @Composable
    fun pasos(a: CorredorEnTuIA.Ajustes) {
        SeccionLista(modifier = Modifier.alpha(if (habilitado) 1f else 0.6f)) {
            paso(1, "Copiá la dirección de tu servidor")
            val u = a.url
            if (u != null) {
                Separador()
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icono("server.rack", 17f, C.okTinta)
                    Texto(u, Estilo.footnote, C.texto, Modifier.weight(1f), lineas = 2, monoespaciado = true)
                    Tocable({
                        copiar(ctx, u)
                        estado.avisar("Dirección copiada. Pegala en tu asistente.")
                    }, Modifier.alpha(if (habilitado) 1f else 0.4f), habilitado = habilitado) {
                        ConIcono("Copiar", "doc.on.doc", Estilo.body, C.accion, modifier = Modifier.padding(vertical = 8.dp))
                    }
                }
            }
            Separador()
            paso(2, "Pegala en tu asistente como «conector» o «servidor MCP»")
            Separador()
            Texto(Formato.sinViudas("Te va a pedir que entres a Corredor y autorices. Usás tu cuenta de siempre: Google, Apple o mail."),
                Estilo.footnote, C.texto2, Modifier.padding(horizontal = 16.dp, vertical = 11.dp))
            Separador()
            paso(3, "Elegí qué puede hacer")
            Separador()
            permiso("eye", C.okTinta, "Leer cartera, contactos, agenda y contratos", "sólo lo que tu rol ve", true, {}, bloqueado = true)
            Separador()
            permiso("square.and.pencil", C.okTinta, "Armar borradores", "mensajes, propiedades, notas · vos los revisás y mandás", true, {}, bloqueado = true)
            Separador()
            permiso("calendar", C.oroTinta, "Agendar visitas", "con deshacer, como si lo hicieras vos", a.agendar ?: false,
                cambiar({ x, v -> x.copy(agendar = v) }) { if (it) "La IA puede agendar visitas" else "La IA ya no agenda visitas" }, bloqueado = !duenio || !habilitado)
            Separador()
            permiso("rectangle.split.3x1", C.oroTinta, "Mover operaciones del tablero", "con deshacer, como si lo hicieras vos", a.mover ?: false,
                cambiar({ x, v -> x.copy(mover = v) }) { if (it) "La IA puede mover operaciones" else "La IA ya no mueve operaciones" }, bloqueado = !duenio || !habilitado)
            Separador()
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 11.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                IconoFila("lock", C.texto2)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Texto("Mandar mensajes, cobrar, liquidar, borrar", Estilo.filaLista, C.texto)
                    Texto("nunca desde la IA: siempre lo confirma una persona en Corredor", Estilo.footnote, C.texto2)
                }
            }
            if (!duenio) {
                Separador()
                Texto(Formato.sinViudas("Lo opcional lo elige el dueño o la dueña. Al conectar, vos elegís qué le das a tu asistente."),
                    Estilo.footnote, C.texto2, Modifier.padding(horizontal = 16.dp, vertical = 11.dp))
            }
        }
    }

    /** El encabezado de una Section con algo a la derecha (HStack en el header). */
    @Composable
    fun encabezado(texto: String, derecha: @Composable () -> Unit) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Texto(texto.uppercase(Formato.argentina), Estilo.footnote, C.texto2, Modifier.weight(1f))
            derecha()
        }
    }

    // MARK: Conectados y registro

    @Composable
    fun conectados() {
        val personas = conexiones.mapNotNull { it.usuarioId }.toSet().size
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            encabezado("Conectados") {
                Texto((if (personas > 0) Formato.cantidad(personas, "persona", "personas") else "nadie todavía").uppercase(Formato.argentina), Estilo.footnote, C.texto2)
            }
            SeccionLista {
                if (conexiones.isEmpty()) {
                    Texto(Formato.sinViudas("Cuando alguien conecte su asistente, aparece acá con lo que le dio y cuándo lo usó."),
                        Estilo.footnote, C.texto2, Modifier.padding(horizontal = 16.dp, vertical = 11.dp))
                }
                conexiones.forEachIndexed { i, c ->
                    if (i > 0) Separador()
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        AvatarPersona(c.personaNombre ?: "", null, 32.dp)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Texto("${CorredorEnTuIA.primerNombre(c.personaNombre)} · ${c.cliente ?: "IA"}${if (c.rol == "lector") " (lector)" else ""}", Estilo.filaLista, C.texto)
                            Texto(CorredorEnTuIA.detalle(c), Estilo.footnote, C.texto2)
                        }
                        Tocable({ alcance.launch { desconectar(c) } }, habilitado = c.id !in sacando) {
                            Texto("Desconectar", Estilo.body, C.errorTinta, Modifier.alpha(if (c.id in sacando) 0.4f else 1f).padding(vertical = 8.dp))
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun registro() {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            encabezado("Lo que hizo la IA hoy") {
                Tocable({ estado.abrir(ASeccion(clave = "actividad", id = "ia")) }) { Texto("Ver todo", Estilo.footnote, C.accion) }
            }
            SeccionLista {
                if (hoy.isEmpty()) {
                    Texto(Formato.sinViudas("Hoy no la usó nadie. Todo lo que haga queda acá y en Actividad."), Estilo.footnote, C.texto2, Modifier.padding(horizontal = 16.dp, vertical = 11.dp))
                }
                hoy.forEachIndexed { i, x ->
                    if (i > 0) Separador()
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 11.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Texto(Formato.horaDe(x.creadoEn), Estilo.footnote, C.texto3, numeros = true)
                        TextoRico(buildAnnotatedString {
                            append(x.detalle ?: x.herramienta ?: "")
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(x.personaNombre?.let { " · ${CorredorEnTuIA.primerNombre(it)}" } ?: "") }
                        }, Estilo.footnote, if (x.resultado == "ok") C.texto else C.errorTinta, Modifier.weight(1f))
                    }
                }
            }
        }
    }

    @Composable
    fun contenido(a: CorredorEnTuIA.Ajustes) {
        Refrescable({ cargar() }) {
            ListaAgrupada {
                Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Texto("Conectá Corredor a tu IA", Estilo.titulo3, C.texto)
                    Texto(Formato.sinViudas("Tu asistente pasa a saber de tus propiedades, contactos, contratos y cobros."), Estilo.footnote, C.texto2)
                }
                SeccionLista { habilitar() }
                pasos(a)
                val aviso = a.aviso?.takeIf { it.isNotBlank() }
                if (aviso != null) {
                    SeccionLista {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 11.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icono("shield", 17f, C.azulTinta)
                            Texto(Formato.sinViudas(aviso), Estilo.footnote, C.texto2, Modifier.weight(1f))
                        }
                    }
                }
                conectados()
                registro()
                SeccionLista("Qué le podés pedir") {
                    CorredorEnTuIA.pedidos.forEach { p ->
                        Texto(p, Estilo.subhead, C.texto, Modifier.padding(horizontal = 16.dp, vertical = 11.dp))
                        Separador()
                    }
                    Texto(Formato.sinViudas("Las respuestas usan los datos de Corredor en ese momento; si algo no está cargado, la IA lo dice en vez de inventarlo."),
                        Estilo.footnote, C.texto2, Modifier.padding(horizontal = 16.dp, vertical = 11.dp))
                }
                SeccionLista {
                    FilaLista({ estado.abrir(ASeccion(clave = "revision")) }) { Texto("Revisión de la IA", Estilo.body, C.accion) }
                }
            }
        }
    }

    LaunchedEffect(estado.version) { cargar() }

    PantallaApilada("Corredor en tu IA") {
        val a = ajustes
        val err = error
        if (a != null) {
            contenido(a)
        } else if (err != null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { EstadoError("Corredor en tu IA", err) { alcance.launch { cargar() } } }
        } else {
            Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.TopCenter) { EsqueletoFilas(6) }
        }
    }
}
