package ar.com.corredor.app.pantallas.mas

import ar.com.corredor.app.app.PantallaApilada

import android.annotation.SuppressLint
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ar.com.corredor.app.app.LocalEstado
import ar.com.corredor.app.nucleo.*
import ar.com.corredor.app.tema.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// Clon de Pantallas/Mas/Integraciones.swift.

private fun String?.lleno(): String? = this?.takeIf { it.isNotBlank() }

/** 43 Integraciones: WhatsApp, Mercado Libre, los feeds de Zonaprop y Argenprop, los avisos push y
 *  Corredor en tu IA. Cada una dice su estado, qué hace y cómo se conecta (tocándola). Conectar y
 *  desconectar es del dueño; el resto ve el estado. */
object Integraciones {
    /** `GET /v1/whatsapp/numero` (whatsapp.Numero). */
    @Serializable
    data class Numero(val conectado: Boolean? = null, val numeroId: String? = null, val telefono: String? = null, val conectadoEn: FechaApi? = null,
                      val verificacion: String? = null, val estado: String? = null)

    /** `GET /v1/mercadolibre`. */
    @Serializable
    data class CuentaML(val disponible: Boolean? = null, val conectada: Boolean? = null, val apodo: String? = null, val reautorizar: Boolean? = null,
                        val preguntasSinResponder: Int? = null)

    /** `GET /v1/inmobiliaria/feeds`: un feed por portal, con la URL (lleva la clave) y la última lectura. */
    @Serializable
    data class Feed(val portal: String, val url: String, val leidoEn: FechaApi? = null, val avisos: Int? = null) { val id: String get() = portal }

    enum class Clave { whatsapp, mercadolibre, zonaprop, argenprop, push; val id: String get() = name }

    data class QueHace(val titulo: String, val texto: String, val pasos: List<String>)

    /** Qué hace cada una y cómo se conecta (los textos de la web). */
    val queHace: Map<Clave, QueHace> = mapOf(
        Clave.whatsapp to QueHace("WhatsApp Business",
            "Las consultas de tu número entran a la Bandeja, cada una con su dueño, y se contestan desde Corredor. Fuera de las 24 horas sólo salen plantillas aprobadas por Meta.",
            listOf("En Meta Business, creá la app y sumá WhatsApp con tu número.",
                "Creá un usuario del sistema con permiso sobre la cuenta y generá su token permanente.",
                "Pegá acá el identificador del número y el token: los probamos con Meta antes de guardarlos.")),
        Clave.mercadolibre to QueHace("Mercado Libre",
            "Publicás desde la ficha de cada propiedad y las preguntas llegan a la Bandeja para contestarlas desde Corredor. Si una publicación falla, te decimos qué le falta.",
            listOf("Tocá «Conectar»: vas a Mercado Libre.", "Entrá con la cuenta de la inmobiliaria y autorizá a Corredor.", "Volvés acá con la cuenta conectada.")),
        Clave.zonaprop to QueHace("Zonaprop",
            "Zonaprop lee tu feed varias veces por día: publica las propiedades que elegiste difundir ahí y saca las que ya no están. Las consultas llegan por mail y a la Bandeja.",
            listOf("Copiá la URL de tu feed.", "En tu cuenta de Zonaprop, andá a «Integraciones» y pegala como feed XML.", "Cuando la lea por primera vez, acá dice «Activo».")),
        Clave.argenprop to QueHace("Argenprop",
            "Argenprop lee tu feed y publica las propiedades que elegiste difundir ahí. Lo que cambiás en Corredor se actualiza solo en la próxima lectura.",
            listOf("Copiá la URL de tu feed.", "En tu cuenta de Argenprop, pegala donde pide la URL del feed.", "Cuando la lea por primera vez, acá dice «Activo».")),
        Clave.push to QueHace("Avisos push",
            "Cada persona los activa en su celular o su compu: una consulta nueva, una visita que está por empezar, un cobro vencido. Cada uno elige cuáles quiere.",
            listOf("Entrá a tu perfil desde el dispositivo donde querés recibirlos.", "Tocá «Activar avisos» y aceptá el permiso del navegador.")),
    )

    /** El estado de una fila: un texto con su tono o un botón. */
    sealed class EstadoFila {
        data class texto(val t: String, val tono: Tono) : EstadoFila()
        data class boton(val t: String) : EstadoFila()
        data object nada : EstadoFila()
    }

    data class Fila(val clave: Clave, val nombre: String, val logo: String?, val icono: String, val detalle: String, val estado: EstadoFila) {
        val id: String get() = clave.name
    }

    fun filas(numero: Numero?, ml: CuentaML?, erroresML: Int, feeds: List<Feed>, push: Pair<Int, Int>, duenio: Boolean): List<Fila> {
        val f = mutableListOf<Fila>()
        val n = numero
        f.add(Fila(Clave.whatsapp, "WhatsApp", "LogoWhatsApp", "message",
            if (n?.conectado == true) "${n.telefono ?: "Número conectado"} · ${Formato.cantidad(push.second, "persona", "personas")}"
            else "Las consultas entran a la Bandeja y respondés desde Corredor",
            if (n?.conectado == true) EstadoFila.texto("Conectado", Tono.verde) else EstadoFila.boton("Conectar")))
        val detalleML: String
        val estadoML: EstadoFila
        if (ml?.conectada != true) {
            detalleML = if (ml?.disponible == false) "Todavía no está habilitado en Corredor" else "Publicá y contestá preguntas desde Corredor"
            estadoML = if (ml?.disponible == false) EstadoFila.texto("Sin habilitar", Tono.neutro) else EstadoFila.boton("Conectar")
        } else {
            val preguntas = ml.preguntasSinResponder ?: 0
            detalleML = when {
                ml.reautorizar == true -> "Mercado Libre pide volver a conectar"
                erroresML > 0 -> Formato.cantidad(erroresML, "publicación con error", "publicaciones con error")
                else -> "${ml.apodo.lleno() ?: "Cuenta conectada"}${if (preguntas > 0) " · ${Formato.cantidad(preguntas, "pregunta", "preguntas")} sin responder" else ""}"
            }
            estadoML = if (ml.reautorizar == true || erroresML > 0) EstadoFila.texto("Revisar", Tono.oro) else EstadoFila.texto("Conectado", Tono.verde)
        }
        f.add(Fila(Clave.mercadolibre, "Mercado Libre", null, "storefront", detalleML, estadoML))
        for ((clave, nombre) in listOf(Clave.zonaprop to "Zonaprop", Clave.argenprop to "Argenprop")) {
            val x = feeds.firstOrNull { it.portal == clave.name }
            val leido = x?.leidoEn
            f.add(Fila(clave, "$nombre · feed", null, "dot.radiowaves.up.forward",
                leido?.let { "leído ${Relativa.de(it)} · ${Formato.cantidad(x?.avisos ?: 0, "aviso", "avisos")}" } ?: "Pegá la URL del feed en tu cuenta de $nombre",
                if (leido != null) EstadoFila.texto("Activo", Tono.verde) else if (duenio) EstadoFila.boton("Conectar") else EstadoFila.nada))
        }
        f.add(Fila(Clave.push, "Avisos push", null, "bell.badge",
            "${push.first} de ${Formato.cantidad(push.second, "persona", "personas")} los activaron", EstadoFila.nada))
        return f
    }
}

@Composable
fun Integraciones() {
    val estado = LocalEstado.current
    var numero by remember { mutableStateOf<Integraciones.Numero?>(null) }
    var ml by remember { mutableStateOf<Integraciones.CuentaML?>(null) }
    var erroresML by remember { mutableIntStateOf(0) }
    var feeds by remember { mutableStateOf<List<Integraciones.Feed>>(emptyList()) }
    var push by remember { mutableStateOf(0 to 0) }
    var ia by remember { mutableStateOf(false) }
    var cargado by remember { mutableStateOf(false) }
    var abierta by remember { mutableStateOf<Integraciones.Clave?>(null) }

    @Serializable data class IA(val habilitado: Boolean? = null)

    suspend fun cargar() {
        val api = estado.api
        numero = api.opcional<Integraciones.Numero>("/v1/whatsapp/numero")
        ml = api.opcional<Integraciones.CuentaML>("/v1/mercadolibre")
        // JSONLibre de iOS
        val errores: List<JsonElement> = api.opcional<List<JsonElement>>("/v1/publicaciones", listOf("portal" to "mercadolibre", "estado" to "error", "limite" to "50")) ?: emptyList()
        erroresML = errores.size
        // Sólo el dueño: la URL lleva la clave.
        if (estado.esDuenio) feeds = api.opcional<List<Integraciones.Feed>>("/v1/inmobiliaria/feeds") ?: emptyList()
        val equipo: List<Miembro> = api.opcional<List<Miembro>>("/v1/equipo") ?: emptyList()
        push = equipo.count { it.avisosPush == true } to equipo.size
        val a = api.opcional<IA>("/v1/ia/ajustes")
        ia = a?.habilitado ?: false
        cargado = true
    }

    LaunchedEffect(estado.version) { cargar() }
    val filas = Integraciones.filas(numero, ml, erroresML, feeds, push, estado.esDuenio)

    PantallaApilada("Integraciones") {
        if (cargado) {
            Refrescable({ cargar() }) {
                ListaAgrupada {
                    Texto(Formato.sinViudas("Cada una dice su estado y qué hace. Tocala para ver cómo se conecta."), Estilo.footnote, C.texto2, Modifier.padding(horizontal = 16.dp))
                    SeccionLista {
                        filas.forEachIndexed { i, f ->
                            if (i > 0) Separador()
                            FilaLista({ abierta = f.clave }) { FilaIntegracion(f) }
                        }
                        Separador()
                        FilaLista({ estado.abrir(ASeccion("ia")) }) {
                            FilaIntegracion(Integraciones.Fila(Integraciones.Clave.push, "Corredor en tu IA", "Simbolo", "sparkles", "Tu asistente lee tu cartera y arma borradores",
                                Integraciones.EstadoFila.texto(if (ia) "Habilitada" else "Apagada", if (ia) Tono.verde else Tono.neutro)))
                        }
                    }
                }
            }
        } else {
            Box(Modifier.fillMaxSize().padding(16.dp)) { EsqueletoFilas(6) }
        }
    }

    val c = abierta
    Hoja(c != null, { abierta = null }, alturas = listOf(Altura.media, Altura.grande)) {
        if (c != null) {
            HojaIntegracion(c, filas.firstOrNull { it.clave == c }, numero, ml, erroresML, feeds.firstOrNull { it.portal == c.name },
                alCambiar = { cargar() }, alCerrar = { abierta = null })
        }
    }
}

/** Un logo de los assets (Image("LogoWhatsApp")): el drawable con su nombre en minúsculas y guiones bajos. */
@SuppressLint("DiscouragedApi")
@Composable
private fun LogoDeAssets(nombre: String, icono: String) {
    val ctx = LocalContext.current
    // Los logos «LogoWhatsApp» y «Simbolo» de iOS (tema/Base.kt › Imagenes).
    val clave = nombre.replace(Regex("([a-z])([A-Z])"), "$1_$2").lowercase()
    val id = remember(clave) { Imagenes.de(nombre).takeIf { it != 0 } ?: ctx.resources.getIdentifier(clave, "drawable", ctx.packageName) }
    if (id != 0) Image(painterResource(id), null, Modifier.size(28.dp), contentScale = ContentScale.Fit)
    else Box(Modifier.width(28.dp), contentAlignment = Alignment.Center) { Icono(icono, 20f, C.okTinta) }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.FilaIntegracion(f: Integraciones.Fila) {
    if (f.logo != null) LogoDeAssets(f.logo, f.icono)
    else Box(Modifier.width(28.dp), contentAlignment = Alignment.Center) { Icono(f.icono, 20f, C.okTinta) }
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Texto(f.nombre, Estilo.filaLista, C.texto)
        Texto(f.detalle, Estilo.footnote, C.texto2)
    }
    when (val e = f.estado) {
        is Integraciones.EstadoFila.texto -> Box(Modifier.height(24.dp).clip(Capsula).background(e.tono.fondo).padding(horizontal = 9.dp), contentAlignment = Alignment.Center) {
            Texto(e.t, Estilo.chip, e.tono.tinta, lineas = 1)
        }
        is Integraciones.EstadoFila.boton -> Box(Modifier.height(30.dp).clip(Capsula).background(C.accion).padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
            Texto(e.t, Estilo.footnoteFuerte, C.sobreAccion, lineas = 1)
        }
        Integraciones.EstadoFila.nada -> {}
    }
}

// MARK: - La hoja de cada integración

/** Qué hace, cómo se conecta y los botones de cada integración (la `Hoja` de la web). */
@Composable
fun HojaIntegracion(
    clave: Integraciones.Clave,
    fila: Integraciones.Fila?,
    numero: Integraciones.Numero?,
    ml: Integraciones.CuentaML?,
    erroresML: Int,
    feed: Integraciones.Feed?,
    alCambiar: suspend () -> Unit,
    alCerrar: () -> Unit,
) {
    val estado = LocalEstado.current
    val ctx = LocalContext.current
    val alcance = rememberCoroutineScope()
    var ocupado by remember { mutableStateOf(false) }
    var verificacion by remember { mutableStateOf<String?>(null) }
    var confirmar by remember { mutableStateOf<Confirmacion?>(null) }
    val que = Integraciones.queHace[clave] ?: Integraciones.QueHace("", "", emptyList())
    val duenio = estado.esDuenio
    val conectada = (clave == Integraciones.Clave.whatsapp && numero?.conectado == true) || (clave == Integraciones.Clave.mercadolibre && ml?.conectada == true)

    suspend fun hacer(metodo: String, ruta: String, ok: String) {
        ocupado = true
        try {
            estado.api.hacer(metodo, ruta)
            estado.avisar(ok)
            alCambiar()
            alCerrar()
        } catch (e: CancellationException) { throw e } catch (e: Exception) { estado.avisar(e) } finally { ocupado = false }
    }

    @Serializable data class L(val url: String)
    @Serializable data class CuerpoVuelta(val codigo: String, val estado: String)

    /** Conectar (o volver a conectar) Mercado Libre: su hoja de autorización y, a la vuelta, el canje del código. */
    suspend fun conectarML() {
        ocupado = true
        try {
            val l: L = try { estado.api.enviar("POST", "/v1/mercadolibre/conectar", emptyMap<String, String>()) } catch (e: CancellationException) { throw e } catch (e: Exception) { estado.avisar(e); return }
            if (!l.url.startsWith("http")) { estado.avisar("No se pudo abrir Mercado Libre.", ok = false); return }
            val vuelta: String = try { HojaWeb().abrir(l.url) } catch (e: CancellationException) { throw e } catch (e: Exception) {
                if ((e as? Clerk.ErrorClerk)?.codigo != "cancelado") estado.avisar("Mercado Libre no autorizó la conexión. Probá conectar de nuevo desde el panel.", ok = false)
                return
            }
            val u = android.net.Uri.parse(vuelta)
            if (u.getQueryParameter("error").lleno() != null) {
                estado.avisar("Mercado Libre no autorizó la conexión. Probá conectar de nuevo desde el panel.", ok = false); return
            }
            val code = u.getQueryParameter("code").lleno()
            val st = u.getQueryParameter("state").lleno()
            if (code == null || st == null) {
                estado.avisar("Faltan datos de Mercado Libre en el link. Probá conectar de nuevo desde el panel.", ok = false); return
            }
            try {
                val r: Integraciones.CuentaML = estado.api.enviar("POST", "/v1/mercadolibre/vuelta", CuerpoVuelta(code, st))
                estado.avisar("Listo: conectaste la cuenta${r.apodo.lleno()?.let { " $it" } ?: ""} de Mercado Libre.")
                alCambiar()
                alCerrar()
            } catch (e: CancellationException) { throw e } catch (e: Exception) { estado.avisar(e) }
        } finally { ocupado = false }
    }

    androidx.compose.foundation.layout.Column(Modifier.fillMaxSize()) {
        BarraHoja(que.titulo, izquierda = { BotonHoja("Cerrar") { alCerrar() } })
        ListaAgrupada {
            SeccionLista {
                fila?.detalle?.let { FilaLista { Texto(it, Estilo.footnote, C.texto2) }; Separador() }
                FilaLista { Texto(Formato.sinViudas(que.texto), Estilo.subhead, C.texto) }
            }
            if (!conectada) {
                SeccionLista {
                    que.pasos.forEachIndexed { n, p ->
                        if (n > 0) Separador()
                        FilaLista {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                                Box(Modifier.size(22.dp).clip(CircleShape).background(C.accion), contentAlignment = Alignment.Center) {
                                    Texto("${n + 1}", Estilo(13f, Peso.bold), C.sobreAccion)
                                }
                                Texto(Formato.sinViudas(p), Estilo.subhead, C.texto, Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
            if (feed != null) {
                SeccionLista(pie = "La URL lleva una clave: no la publiques. Si se filtró, generá otra y volvé a pegarla en los portales.") {
                    FilaLista {
                        Texto(feed.url, Estilo.footnote, C.texto, Modifier.weight(1f), lineas = 3, monoespaciado = true)
                        ConIcono("Copiar", "doc.on.doc", Estilo.body, modifier = Modifier.clickable {
                            copiar(ctx, feed.url)
                            estado.avisar("URL copiada. Pegala en el portal.")
                        })
                    }
                }
            }
            if (clave == Integraciones.Clave.whatsapp && numero?.conectado != true && duenio) {
                FormWhatsApp { n ->
                    verificacion = n.verificacion
                    estado.avisar("WhatsApp conectado")
                    alCambiar()
                }
            }
            verificacion?.let { v ->
                SeccionLista {
                    FilaLista { Texto("Token de verificación del webhook (se muestra una sola vez):", Estilo.footnote, C.texto2) }
                    Separador()
                    FilaLista { Texto(v, Estilo.footnote, C.texto, monoespaciado = true) }
                    Separador()
                    FilaLista({ copiar(ctx, v); estado.avisar("Copiado") }) { ConIcono("Copiar", "doc.on.doc") }
                }
            }
            if (!duenio && clave != Integraciones.Clave.push) {
                SeccionLista { FilaLista { ConIcono("La conecta el dueño o la dueña de la inmobiliaria.", "lock", Estilo.footnote, C.texto2) } }
            }
            SeccionLista {
                when {
                    clave == Integraciones.Clave.whatsapp && duenio && numero?.conectado == true ->
                        FilaLista({
                            confirmar = Confirmacion("¿Desconectás WhatsApp?",
                                "Las consultas dejan de entrar a la Bandeja. Para volver, hay que pegar el token de nuevo.",
                                "Desconectar", hacer = { alcance.launch { hacer("DELETE", "/v1/whatsapp/numero", "WhatsApp desconectado") } })
                        }) { Texto("Desconectar", Estilo.body, C.error) }
                    clave == Integraciones.Clave.mercadolibre && duenio && ml?.disponible != false ->
                        if (ml?.conectada == true) {
                            if (erroresML > 0) {
                                FilaLista({ alCerrar(); estado.abrir(ASeccion("difusion")) }) { Texto("Ver la publicación", Estilo.body, C.accion) }
                                Separador()
                            }
                            if (ml.reautorizar == true) {
                                FilaLista({ alcance.launch { conectarML() } }, habilitado = !ocupado) { Texto("Volver a conectar", Estilo.body, C.accion) }
                            } else {
                                FilaLista({
                                    confirmar = Confirmacion("¿Desconectás Mercado Libre?",
                                        "Tus avisos quedan en Mercado Libre pero sin sincronizar, y las preguntas dejan de llegar. Para volver, autorizás de nuevo.",
                                        "Desconectar", hacer = { alcance.launch { hacer("DELETE", "/v1/mercadolibre", "Mercado Libre desconectado") } })
                                }) { Texto("Desconectar", Estilo.body, C.error) }
                            }
                        } else {
                            FilaLista({ alcance.launch { conectarML() } }, habilitado = !ocupado) { ConIcono("Conectar", "arrow.up.right.square") }
                        }
                    feed != null && duenio ->
                        FilaLista({ alcance.launch { hacer("POST", "/v1/inmobiliaria/clave-feed", "Clave nueva: volvé a pegar la URL en los portales") } }, habilitado = !ocupado) {
                            if (ocupado) Rueda() else Texto("Generar otra clave", Estilo.body, C.accion)
                        }
                    clave == Integraciones.Clave.push ->
                        FilaLista({ alCerrar(); estado.abrir(APerfil) }) { Texto("Ir a mi perfil", Estilo.body, C.accion) }
                    else -> {}
                }
            }
        }
    }
    VistaConfirmacion(confirmar) { confirmar = null }
}

// MARK: - Conectar el número de WhatsApp

/** Los datos del número de Meta (el identificador, la cuenta, el token y el secreto de la app), con el
 *  error de cada campo. Al conectar la API los prueba con Meta («Probando con Meta…»). */
@Composable
fun FormWhatsApp(alConectar: suspend (Integraciones.Numero) -> Unit) {
    val estado = LocalEstado.current
    val alcance = rememberCoroutineScope()
    var numeroId by remember { mutableStateOf("") }
    var cuentaId by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    var secreto by remember { mutableStateOf("") }
    var enviando by remember { mutableStateOf(false) }
    var campos by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    @Serializable data class Cuerpo(val numeroId: String, val cuentaId: String, val token: String, val appSecreto: String)

    suspend fun conectar() {
        enviando = true
        campos = emptyMap()
        try {
            val d = estado.api.pedirConCampos("PUT", "/v1/whatsapp/numero", Cuerpo(numeroId.trim(), cuentaId.trim(), token.trim(), secreto.trim()))
            val n = Api.json.decodeFromString<Integraciones.Numero>(d)
            alConectar(n)
            estado.version += 1
        } catch (e: CancellationException) { throw e } catch (e: Exception) { campos = estado.camposDe(e) } finally { enviando = false }
    }

    @Composable
    fun campo(t: String, valor: String, alCambiar: (String) -> Unit, k: String, ayuda: String? = null) {
        FilaLista {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Entrada(valor, alCambiar, t, teclado = KeyboardType.Number, monoespaciado = true, sinCorrector = true)
                ayuda?.let { Texto(it, Estilo.footnote, C.texto3) }
                ErrorDeCampo(campos[k])
            }
        }
    }

    SeccionLista(pie = "Lo sacás de Meta for Developers › tu app › WhatsApp › Configuración de la API.") {
        campo("Identificador del número de teléfono", numeroId, { numeroId = it }, "numeroId")
        Separador()
        campo("Identificador de la cuenta de WhatsApp Business", cuentaId, { cuentaId = it }, "cuentaId", ayuda = "Para ver tus plantillas aprobadas.")
        Separador()
        FilaLista {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Entrada(token, { token = it }, "Token permanente", secreto = true)
                ErrorDeCampo(campos["token"])
            }
        }
        Separador()
        FilaLista {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Entrada(secreto, { secreto = it }, "Clave secreta de la app (opcional)", secreto = true)
                Texto("Si usás una app de Meta propia.", Estilo.footnote, C.texto3)
                ErrorDeCampo(campos["appSecreto"])
            }
        }
        Separador()
        FilaLista({ alcance.launch { conectar() } }, habilitado = !(enviando || numeroId.isEmpty() || token.isEmpty())) {
            if (enviando) {
                Rueda()
                Texto("Probando con Meta…", Estilo.body, C.texto2)
            } else Texto("Conectar", Estilo.headline, C.accion)
        }
    }
}

/** El alta del número desde otra pantalla (los primeros pasos, MIRA): la misma hoja de Integraciones. */
@Composable
fun ConectarWhatsApp(alCerrar: () -> Unit) {
    val estado = LocalEstado.current
    var verificacion by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize()) {
        BarraHoja("Conectar WhatsApp", izquierda = { BotonHoja(if (verificacion == null) "Cancelar" else "Listo") { alCerrar() } })
        ListaAgrupada {
            val v = verificacion
            if (v != null) {
                SeccionLista {
                    FilaLista { ConIcono("WhatsApp conectado", "checkmark.circle.fill", color = C.okTinta) }
                    Separador()
                    FilaLista { Texto("Token de verificación del webhook (se muestra una sola vez):", Estilo.footnote, C.texto2) }
                    Separador()
                    FilaLista { Texto(v, Estilo.footnote, C.texto, monoespaciado = true) }
                }
            } else {
                FormWhatsApp { n ->
                    estado.sugerir("WhatsApp conectado", "Respuestas", ASeccion("respuestas"))
                    val x = n.verificacion.lleno()
                    if (x != null) verificacion = x else alCerrar()
                }
            }
        }
    }
}
