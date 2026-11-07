package ar.com.corredor.app.pantallas.mas

import ar.com.corredor.app.app.PantallaApilada

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import ar.com.corredor.app.app.BarraDe
import ar.com.corredor.app.app.BotonBarra
import ar.com.corredor.app.app.Estado
import ar.com.corredor.app.app.LocalEstado
import ar.com.corredor.app.nucleo.*
import ar.com.corredor.app.pantallas.agenda.LicenciasAgenda
import ar.com.corredor.app.pantallas.propiedades.Propiedad
import ar.com.corredor.app.tema.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.UUID

// Clon de Pantallas/Mas/Equipo.swift.

private fun String?.lleno(): String? = this?.takeIf { it.isNotBlank() }

/** 42E Equipo: los roles explicados en palabras, las personas con su rol (el dueño lo cambia ahí mismo,
 *  con «Deshacer») y su cartera, las invitaciones pendientes con «Reenviar», invitar con el link y las
 *  licencias. */
@Composable
fun Equipo() {
    val estado = LocalEstado.current
    val alcance = rememberCoroutineScope()
    var equipo by remember { mutableStateOf<List<Miembro>>(emptyList()) }
    var invitaciones by remember { mutableStateOf<List<EquipoTipos.Invitacion>>(emptyList()) }
    var licencias by remember { mutableStateOf<List<Licencia>>(emptyList()) }
    var cargado by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<ErrorApi?>(null) }
    var invitando by remember { mutableStateOf(false) }
    var verLicencias by remember { mutableStateOf(false) }
    var confirmar by remember { mutableStateOf<Confirmacion?>(null) }
    /** El rol elegido mientras se guarda (como la web: se ve al toque). */
    val rolesEnCurso = remember { mutableStateMapOf<String, String>() }
    val yo = estado.usuario?.id ?: ""

    suspend fun cargar() {
        try {
            val e: List<Miembro> = estado.api.get("/v1/equipo")
            equipo = e
            if (estado.esDuenio) invitaciones = estado.api.opcional<List<EquipoTipos.Invitacion>>("/v1/invitaciones") ?: emptyList()
            val hoy = Formato.diaISO(Instant.now())
            licencias = estado.api.opcional<List<Licencia>>("/v1/licencias", listOf("desde" to hoy, "hasta" to Formato.diaISO(Instant.now().plusSeconds(180L * 86400)))) ?: emptyList()
            cargado = true
            error = null
        } catch (e: CancellationException) { throw e } catch (e: ErrorApi) { if (!cargado) error = e } catch (_: Exception) {}
    }

    @Serializable data class CuerpoRol(val rol: String)
    @Serializable data class CuerpoInvitacion(val email: String, val rol: String)

    suspend fun cambiarRol(m: Miembro, r: String) {
        val antes = rolesEnCurso[m.usuarioId] ?: m.rol ?: "agente"
        if (r == antes) return
        rolesEnCurso[m.usuarioId] = r
        try {
            estado.api.hacer("PATCH", "/v1/equipo/${m.usuarioId}", CuerpoRol(r))
            cargar()
            rolesEnCurso.remove(m.usuarioId)
            estado.avisar("${EquipoTipos.nombreCorto(m)} ahora es ${EquipoTipos.nombreRol[r] ?: r}") {
                alcance.launch {
                    try { estado.api.hacer("PATCH", "/v1/equipo/${m.usuarioId}", CuerpoRol(antes)); cargar() }
                    catch (e: CancellationException) { throw e } catch (e: Exception) { estado.avisar(e) }
                }
            }
        } catch (e: CancellationException) { throw e } catch (e: Exception) {
            rolesEnCurso.remove(m.usuarioId)
            estado.avisar(e)
        }
    }

    suspend fun sacar(m: Miembro) {
        try {
            estado.api.hacer("DELETE", "/v1/equipo/${m.usuarioId}")
            estado.avisar("${EquipoTipos.nombreCorto(m)} ya no está en el equipo. Lo suyo quedó sin agente.")
            cargar()
        } catch (e: CancellationException) { throw e } catch (e: Exception) { estado.avisar(e) }
    }

    /** Sacar del equipo no vuelve solo (hay que invitarla de nuevo): se confirma. */
    fun pedirSacar(m: Miembro) {
        confirmar = Confirmacion(
            titulo = "¿Sacás a ${EquipoTipos.nombreCorto(m)} del equipo?",
            texto = "Deja de entrar a la inmobiliaria y lo que tenía asignado queda sin agente. Para que vuelva, la tenés que invitar de nuevo.",
            boton = "Sacar del equipo", no = "No, dejarla",
            hacer = { alcance.launch { sacar(m) } },
        )
    }

    suspend fun reenviar(email: String, rol: String) {
        try {
            estado.api.hacer("POST", "/v1/invitaciones", CuerpoInvitacion(email, rol))
            estado.avisar("Invitación reenviada a $email")
            cargar()
        } catch (e: CancellationException) { throw e } catch (e: Exception) { estado.avisar(e) }
    }

    suspend fun cancelar(i: EquipoTipos.Invitacion) {
        try {
            estado.api.hacer("DELETE", "/v1/invitaciones/${i.id}")
            cargar()
            estado.avisar("Invitación a ${i.email} cancelada") { alcance.launch { reenviar(i.email, i.rol) } }
        } catch (e: CancellationException) { throw e } catch (e: Exception) { estado.avisar(e) }
    }

    LaunchedEffect(estado.version) { cargar() }

    PantallaApilada("Equipo", acciones = {
        BotonBarra(if (licencias.isEmpty()) "Licencias" else "Licencias · ${licencias.size}") { verLicencias = true }
        if (estado.esDuenio) IconoBarra("person.badge.plus", "Invitar") { invitando = true }
    }) {
        when {
            cargado -> Refrescable({ cargar() }) {
                ListaAgrupada {
                    SeccionLista {
                        val detalle = Formato.cantidad(equipo.size, "persona", "personas") +
                            (if (invitaciones.isEmpty()) "" else " · " + Formato.cantidad(invitaciones.size, "invitación pendiente", "invitaciones pendientes"))
                        FilaLista { Texto(detalle, Estilo.footnote, C.texto2) }
                        EquipoTipos.roles.forEach { r ->
                            Separador()
                            FilaLista {
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Texto(r.second, Estilo.filaLista, if (r.first == "duenio") C.okTinta else C.texto)
                                    Texto(Formato.sinViudas(r.third), Estilo.footnote, C.texto2)
                                }
                            }
                        }
                    }
                    SeccionLista("Personas") {
                        equipo.forEachIndexed { i, m ->
                            if (i > 0) Separador()
                            FilaMiembro(m, rolesEnCurso[m.usuarioId] ?: m.rol ?: "agente", yo, estado,
                                alCambiarRol = { r -> alcance.launch { cambiarRol(m, r) } }, alSacar = { pedirSacar(m) })
                        }
                    }
                    if (invitaciones.isNotEmpty()) {
                        SeccionLista("Invitaciones pendientes") {
                            invitaciones.forEachIndexed { n, i ->
                                if (n > 0) Separador()
                                FilaInvitacion(i, estado,
                                    alCancelar = { alcance.launch { cancelar(i) } },
                                    alReenviar = { alcance.launch { reenviar(i.email, i.rol) } })
                            }
                        }
                    }
                }
            }
            error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EstadoError("el equipo", error!!) { alcance.launch { cargar() } }
            }
            else -> Box(Modifier.fillMaxSize().padding(16.dp)) { EsqueletoFilas(5) }
        }
    }

    Hoja(invitando, { invitando = false }, alturas = listOf(Altura.media, Altura.grande)) {
        InvitarAlEquipo(alCerrar = { invitando = false }) { cargar() }
    }
    Hoja(verLicencias, { verLicencias = false }, alturas = listOf(Altura.grande)) {
        LicenciasAgenda()
    }
    VistaConfirmacion(confirmar) { confirmar = null }
}

/** Los tipos y textos de Equipo (los `static` de la vista en iOS). */
object EquipoTipos {
    /** `GET /v1/invitaciones` (equipo.Invitacion). */
    @Serializable
    data class Invitacion(val id: String, val email: String, val rol: String, val venceEn: FechaApi? = null, val creadaEn: FechaApi? = null, val link: String? = null)

    val nombreRol: Map<String, String> = mapOf("duenio" to "Dueño", "agente" to "Agente", "lector" to "Lector")
    val roles: List<Triple<String, String, String>> = listOf(
        Triple("duenio", "Dueño", "Ve y cambia todo: cobranzas, rendiciones, equipo, web e integraciones."),
        Triple("agente", "Agente", "Su cartera y lo que no tiene dueño. Registra cobros; no emite rendiciones."),
        Triple("lector", "Lector", "Ve y descarga sin modificar. Para el contador o un socio."),
    )

    fun nombre(m: Miembro): String = m.completo
    fun nombreCorto(m: Miembro): String = m.nombre.lleno() ?: m.email ?: ""

    /** «12 propiedades · Palermo y Belgrano», «Administra todo», «Ve y descarga». */
    fun cartera(m: Miembro): String {
        if (m.rol == "duenio") return "Administra todo"
        if (m.rol == "lector") return "Ve y descarga"
        val n = m.propiedades ?: 0
        if (n == 0) return "Sin propiedades asignadas"
        val b = m.barrios ?: emptyList()
        val barrios = if (b.size == 2) "${b[0]} y ${b[1]}" else b.firstOrNull()
        return Formato.cantidad(n, "propiedad", "propiedades") + (barrios?.let { " · $it" } ?: "")
    }
}

/** Un botón con ícono de la barra (ToolbarItem con Image). */
@Composable
private fun IconoBarra(simbolo: String, etiqueta: String, alTocar: () -> Unit) {
    Box(Modifier.size(44.dp).clickable(onClick = alTocar).semantics { contentDescription = etiqueta }, contentAlignment = Alignment.Center) {
        Icono(simbolo, 19f, C.accion)
    }
}

@Composable
private fun FilaMiembro(m: Miembro, rol: String, yo: String, estado: Estado, alCambiarRol: (String) -> Unit, alSacar: () -> Unit) {
    val ctx = LocalContext.current
    FilaLista {
        AvatarPersona(EquipoTipos.nombre(m), m.foto, 40.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            val t3 = C.texto3
            TextoRico(buildAnnotatedString {
                append(EquipoTipos.nombre(m))
                withStyle(SpanStyle(color = t3)) { append(if (m.usuarioId == yo) " · vos" else "") }
            }, Estilo.filaLista, C.texto)
            m.email?.let { Texto(it, Estilo.footnote, C.texto2, lineas = 1) }
            Texto("${EquipoTipos.nombreRol[rol] ?: "Agente"} · ${EquipoTipos.cartera(m)}", Estilo.footnote, C.texto3)
            if (m.rol == "agente" && (m.propiedades ?: 0) > 0) {
                Texto("Ver cartera", Estilo.footnoteFuerte, C.accion, Modifier.padding(top = 2.dp)
                    .clickable { estado.abrir(ACarteraAgente(m.usuarioId, EquipoTipos.nombre(m))) })
            }
        }
        val opciones = buildList {
            if (estado.esDuenio && m.usuarioId != yo) {
                listOf("duenio" to "Dueño", "agente" to "Agente", "lector" to "Lector").forEach { (k, t) ->
                    add(OpcionDeMenu(t, if (rol == k) "checkmark" else "") { alCambiarRol(k) })
                }
            }
            if (m.rol != "lector") add(OpcionDeMenu("Tarjeta para imprimir", "person.text.rectangle") { estado.abrir(ASeccion("tarjeta-agente", m.usuarioId)) })
            m.telefono?.let { t -> add(OpcionDeMenu("WhatsApp", "message") { Afuera.whatsapp(t) }) }
            if (estado.esDuenio && m.usuarioId != yo) add(OpcionDeMenu("Sacar del equipo…", "person.badge.minus", destructiva = true) { alSacar() })
        }
        MenuBoton(opciones, "Más acciones de ${EquipoTipos.nombre(m)}") {
            Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) { Icono("ellipsis.circle", 20f, C.accion) }
        }
    }
}

@Composable
private fun FilaInvitacion(i: EquipoTipos.Invitacion, estado: Estado, alCancelar: () -> Unit, alReenviar: () -> Unit) {
    FilaLista {
        val borde = C.linea2
        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(40.dp)) {
                val g = 1.5.dp.toPx()
                drawCircle(borde, radius = size.minDimension / 2 - g / 2, style = Stroke(g, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx()))))
            }
            Texto("?", Estilo(15f, Peso.bold), C.texto3)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Texto(i.email, Estilo.filaLista, C.texto, lineas = 1)
            Texto(listOfNotNull(i.venceEn?.let { "vence ${Relativa.de(it)}" }, EquipoTipos.nombreRol[i.rol]).joinToString(" · "), Estilo.footnote, C.texto2)
            i.creadaEn?.let { Texto("Invitación enviada ${Relativa.de(it)}", Estilo.footnote, C.texto3) }
            Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Texto("Cancelar", Estilo.footnoteFuerte, C.errorTinta, Modifier.clickable(onClick = alCancelar))
                Texto("Reenviar", Estilo.footnoteFuerte, C.oroTinta, Modifier.clickable(onClick = alReenviar))
                i.link?.let { l ->
                    ConIcono("Compartir", "square.and.arrow.up", Estilo.footnoteFuerte, modifier = Modifier.clickable { estado.compartirTexto(l) })
                }
            }
        }
    }
}

// MARK: - Invitar

/** «Invitar al equipo»: el mail y el rol (con lo que ve cada uno). Después, el link para copiar o mandar
 *  por WhatsApp («vale 7 días»). */
@Composable
fun InvitarAlEquipo(alCerrar: () -> Unit, alInvitar: suspend () -> Unit) {
    val estado = LocalEstado.current
    val ctx = LocalContext.current
    val alcance = rememberCoroutineScope()
    var mail by remember { mutableStateOf("") }
    var rol by remember { mutableStateOf("agente") }
    var enviando by remember { mutableStateOf(false) }
    var campos by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var link by remember { mutableStateOf<String?>(null) }

    @Serializable data class Cuerpo(val email: String, val rol: String)

    suspend fun invitar() {
        val m = mail.trim().lowercase()
        campos = emptyMap()
        if (!Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$").matches(m)) {
            campos = mapOf("email" to "No parece un mail.")
            return
        }
        enviando = true
        try {
            val d = estado.api.pedirConCampos("POST", "/v1/invitaciones", Cuerpo(m, rol), envio = UUID.randomUUID().toString())
            val i = Api.json.decodeFromString<EquipoTipos.Invitacion>(d)
            link = i.link ?: ""
            alInvitar()
        } catch (e: CancellationException) { throw e } catch (e: Exception) { campos = estado.camposDe(e) } finally { enviando = false }
    }

    Column(Modifier.fillMaxSize()) {
        BarraHoja("Invitar al equipo",
            izquierda = if (link == null) ({ BotonHoja("Cancelar") { alCerrar() } }) else null,
            derecha = {
                if (link != null) BotonHoja("Listo", fuerte = true) { alCerrar() }
                else if (enviando) Box(Modifier.padding(horizontal = 12.dp)) { Rueda() }
                else BotonHoja("Mandar invitación", fuerte = true, habilitado = mail.trim().isNotEmpty()) { alcance.launch { invitar() } }
            })
        ListaAgrupada {
            Texto(Formato.sinViudas("Le llega un mail con el link. También podés copiarlo y mandarlo por WhatsApp."), Estilo.footnote, C.texto2, Modifier.padding(horizontal = 16.dp))
            val l = link
            if (l != null) {
                SeccionLista {
                    FilaLista { ConIcono("Invitación lista. El link vale 7 días.", "checkmark.circle.fill", color = C.okTinta) }
                    Separador()
                    FilaLista { Texto(l, Estilo.footnote, C.texto, monoespaciado = true) }
                    Separador()
                    FilaLista({ copiar(ctx, l); estado.avisar("Link copiado. Mandalo por WhatsApp.") }) { ConIcono("Copiar", "doc.on.doc") }
                    Separador()
                    FilaLista({ Afuera.whatsapp(null, texto = l) }) { ConIcono("Mandar por WhatsApp", "message") }
                    Separador()
                    FilaLista({ estado.compartirTexto(l) }) { ConIcono("Compartir", "square.and.arrow.up") }
                }
            } else {
                SeccionLista("Mail") {
                    FilaLista {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Entrada(mail, { mail = it }, "nombre@gmail.com", Modifier.semantics { contentDescription = "Mail" }, teclado = KeyboardType.Email, sinCorrector = true)
                            ErrorDeCampo(campos["email"])
                        }
                    }
                }
                SeccionLista("Rol") {
                    Box(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Segmentado(listOf("agente" to "Agente", "lector" to "Lector", "duenio" to "Dueño"), rol, { rol = it })
                    }
                    Separador()
                    FilaLista { Texto(Formato.sinViudas(EquipoTipos.roles.firstOrNull { it.first == rol }?.third ?: ""), Estilo.footnote, C.texto2) }
                }
            }
        }
    }
}

// MARK: - Cartera de un agente

/** «Ver cartera» de un agente: sus propiedades (GET /v1/propiedades?agente=). */
@Serializable
data class ACarteraAgente(val usuario: String, val nombre: String)

@Composable
fun CarteraAgente(usuario: String, nombre: String) {
    val estado = LocalEstado.current
    val alcance = rememberCoroutineScope()
    var props by remember { mutableStateOf<List<Propiedad>?>(null) }
    var error by remember { mutableStateOf<ErrorApi?>(null) }

    suspend fun cargar() {
        try {
            props = estado.api.todas<Propiedad>("/v1/propiedades", listOf("agente" to usuario))
            error = null
        } catch (e: CancellationException) { throw e } catch (e: ErrorApi) { if (props == null) error = e } catch (_: Exception) {}
    }
    LaunchedEffect(estado.version) { cargar() }

    PantallaApilada("Cartera de ${nombre.split(" ").firstOrNull()?.takeIf { it.isNotEmpty() } ?: nombre}") {
        Refrescable({ cargar() }) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val p = props
                if (p != null) {
                    if (p.isEmpty()) Box(Modifier.padding(top = 50.dp)) { EstadoVacio("house", "Sin propiedades asignadas") }
                    p.forEach { x ->
                        Tocable({ estado.abrir(AFichaPropiedad(x.id)) }) {
                            Row(Modifier.fillMaxWidth().tarjeta(relleno = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Foto(x.miniatura, Modifier.size(72.dp).clip(RoundedCornerShape(12.dp)))
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Texto(x.precioTexto, Estilo.filaLista, C.texto)
                                    Texto(x.nombre, Estilo.footnote, C.texto, lineas = 1)
                                    Texto(x.resumen, Estilo.footnote, C.texto2, lineas = 2)
                                }
                            }
                        }
                    }
                } else if (error != null) {
                    Box(Modifier.padding(top = 40.dp)) { EstadoError("la cartera", error!!) { alcance.launch { cargar() } } }
                } else EsqueletoFilas(5)
            }
        }
    }
}
