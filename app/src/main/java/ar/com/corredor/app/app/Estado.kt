package ar.com.corredor.app.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.core.content.FileProvider
import ar.com.corredor.app.BuildConfig
import ar.com.corredor.app.compartido.Llavero
import ar.com.corredor.app.compartido.SesionCompartida
import ar.com.corredor.app.nucleo.ASeccion
import ar.com.corredor.app.nucleo.Api
import ar.com.corredor.app.nucleo.Clerk
import ar.com.corredor.app.nucleo.ErrorApi
import ar.com.corredor.app.nucleo.Estructura
import ar.com.corredor.app.nucleo.Guardado
import ar.com.corredor.app.nucleo.PasoAlta
import ar.com.corredor.app.nucleo.Pestana
import ar.com.corredor.app.nucleo.Preferencias
import ar.com.corredor.app.nucleo.Ruta
import ar.com.corredor.app.nucleo.Suscripcion
import ar.com.corredor.app.nucleo.Tienda
import ar.com.corredor.app.nucleo.cargarEstructura
import ar.com.corredor.app.nucleo.intentar
import ar.com.corredor.app.nucleo.opcional
import ar.com.corredor.app.nucleo.ponerAcento
import ar.com.corredor.app.pantallas.entrada.ElegirInmobiliaria
import ar.com.corredor.app.tema.Acento
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.io.File
import java.time.Instant
import java.util.UUID

// Clon de App/Estado.swift.

/** La sesión como la devuelve la API (`GET /v1/auth/sesion`, `PUT /v1/auth/inmobiliaria`). */
@Serializable
data class Sesion(
    val usuario: Usuario,
    val inmobiliariaId: String? = null,
    val rol: String? = null,
    val inmobiliarias: List<Membresia> = emptyList(),
) {
    @Serializable
    data class Usuario(
        val id: String,
        val email: String = "",
        val nombre: String = "",
        val apellido: String = "",
        val foto: String? = null,
        val tourVisto: Boolean? = null,
    ) {
        val completo: String get() = listOf(nombre, apellido).filter { it.isNotEmpty() }.joinToString(" ").ifEmpty { email }
    }

    @Serializable
    data class Membresia(val inmobiliariaId: String, val slug: String = "", val nombre: String = "", val rol: String = "") {
        val id: String get() = inmobiliariaId
    }
}

/** Lo que devuelve `POST /v1/auth/clerk`: el token y la sesión. */
@Serializable
data class Ingreso(
    val token: String,
    val usuario: Sesion.Usuario,
    val inmobiliariaId: String? = null,
    val rol: String? = null,
    val inmobiliarias: List<Sesion.Membresia> = emptyList(),
) {
    val sesion: Sesion get() = Sesion(usuario, inmobiliariaId, rol, inmobiliarias)
}

/** La invitación abierta para ver antes de aceptarla (la hoja de `Raiz`). */
data class InvitacionAbierta(val token: String) { val id: String get() = token }

/** El aviso de abajo (handoff · «Deshacer en vez de confirmar»): 3 s, 5 s con «Deshacer», 6 s con una sugerencia. */
data class Aviso(
    val texto: String,
    val ok: Boolean = true,
    val deshacer: (() -> Unit)? = null,
    /** La sugerencia del paso siguiente («Publicar», «Cargar contrato»): su texto y qué hace. */
    val accion: Pair<String, () -> Unit>? = null,
    val id: String = UUID.randomUUID().toString(),
)

/** El estado de la app: la sesión (token en el Llavero), la inmobiliaria y el rol, la pestaña y la navegación, el aviso
 *  con «Deshacer» y la cola sin conexión. Lo comparten todas las pantallas (`LocalEstado.current`). */
class Estado(api: Api? = null) {
    enum class Fase {
        arrancando,
        /** 1A–1B: bienvenida e ingreso con Google o con el mail. */
        afuera,
        /** 1C: trabaja en más de una inmobiliaria (o en ninguna todavía). */
        eligiendo,
        adentro,
    }

    var fase by mutableStateOf(Fase.arrancando)
    val api: Api = api ?: Api(Api.urlConfigurada(), ModoPrueba.token ?: Llavero.leer("token"))
    var sesion by mutableStateOf<Sesion?>(null)
    var aviso by mutableStateOf<Aviso?>(null)
    /** Pantalla bloqueada con la huella o la cara (1D) hasta que la persona se identifique. */
    var bloqueada by mutableStateOf(false)
    /** La pestaña elegida y lo apilado en cada una (NavigationPath en iOS). */
    var pestana by mutableStateOf(Pestana.inicio)
    val pilas: Map<Pestana, SnapshotStateList<Any>> = Pestana.entries.associateWith { mutableStateListOf<Any>() }
    /** MIRA se abre a pantalla completa desde el botón del centro. */
    var miraAbierta by mutableStateOf(false)
    /** Se sube después de cada cambio para que las listas se vuelvan a pedir. */
    var version by mutableIntStateOf(0)
    /** Insignias de la barra (mensajes sin leer, avisos). */
    var sinLeer by mutableIntStateOf(0)
    var avisosPendientes by mutableIntStateOf(0)
    /** El abanico de acciones rápidas del botón del medio (MIRA, cargar, cobrar, editar la web…). */
    var accionesAbiertas by mutableStateOf(false)
    /** La hoja de alta que abre una acción rápida (contacto, visita, contrato). */
    var hojaRapida by mutableStateOf<HojaRapida?>(null)
    /** El recorrido de la primera vez (también se vuelve a ver desde «Más»). */
    var mostrarRecorrido by mutableStateOf(false)
    /** Conectado al stream de cambios (app/Vivo.kt); sin él, el chat vuelve a consultar cada tanto. */
    var enVivo by mutableStateOf(false)
    /** Mi inmobiliaria, Herramientas, el abanico y el alta, como los arma la API (nucleo/Estructura.kt). */
    var estructura by mutableStateOf<Estructura?>(null)
    /** Sube cuando cambia el acento de la marca: la raíz se vuelve a pintar con el color nuevo. */
    var marcaVersion by mutableIntStateOf(0)
    /** El asistente del alta abierto (en un paso, o en el primero que falta). */
    var altaAbierta by mutableStateOf<PasoAlta?>(null)
    /** La bienvenida de quien se suma a un equipo (su foto, su WhatsApp, los avisos y la apariencia). */
    var bienvenidaAbierta by mutableStateOf(false)
    /** La pestaña Propiedades arranca con este agente («yo»: la bienvenida y `/propiedades?agente=yo`); se usa una vez. */
    var carteraDe by mutableStateOf<String?>(null)
    /** La invitación que se está viendo antes de aceptarla (el token del link), en una hoja sobre todo. */
    var invitacionAbierta by mutableStateOf<InvitacionAbierta?>(null)
    /** El ingreso en modo «Creá tu cuenta» (el link `ingresar?alta=1` o el de crear la web). */
    var ingresoAlta by mutableStateOf(false)
    /** «¿No podés entrar?» abierto desde un link (`ingresar/recuperar`). */
    var recuperarAbierto by mutableStateOf(false)
    /** El nombre que trae el link de crear la web (`empezar?nombre=`), para no tener que escribirlo de nuevo. */
    var nombreSugerido by mutableStateOf<String?>(null)
    /** La tarea que junta los cambios en vivo (app/Vivo.kt). */
    internal var recargaVivo: Job? = null
    val cola: Cola
    var tokenPush by mutableStateOf<String?>(null)
        private set
    private var avisoTarea: Job? = null
    private val alcance = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    // Lo que pone MainActivity: lo que en iOS da el sistema (hojas web, permisos, Face ID, compartir).
    var contexto: Context? = null
    /** Abre una Custom Tab (la hoja web del sistema: SFSafariViewController / ASWebAuthenticationSession). */
    var abrirPestana: (String) -> Unit = { url -> contexto?.let { c -> runCatching { CustomTabsIntent.Builder().build().apply { intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }.launchUrl(c, Uri.parse(url)) } } }
    /** El diálogo del sistema para los avisos. */
    var alPedirPermisoPush: suspend () -> Boolean = { true }
    /** Un permiso del sistema (la cámara, el micrófono, la ubicación, los contactos). */
    var alPedirPermiso: suspend (String) -> Boolean = { true }
    /** Varios permisos en un solo diálogo (la ubicación: precisa y aproximada); true si dio al menos uno. */
    var alPedirPermisos: suspend (Array<String>) -> Boolean = { true }
    /** La huella, la cara o el PIN del teléfono (LAContext.evaluatePolicy). */
    var alConfirmarIdentidad: suspend (String) -> Boolean = { true }

    /** Entrar con la huella o la cara (1D). */
    private var faceID by mutableStateOf(Preferencias.bool("faceID"))
    var conFaceID: Boolean
        get() = faceID
        set(v) { faceID = v; Preferencias.ponerBool("faceID", v) }

    fun ponerFaceID(v: Boolean) { conFaceID = v }

    /** El tema elegido: sistema, claro u oscuro (@AppStorage("tema") en iOS). */
    private var temaElegido by mutableStateOf(Preferencias.texto("tema") ?: "sistema")
    var tema: String
        get() = temaElegido
        set(v) { temaElegido = v; Preferencias.ponerTexto("tema", v) }

    fun ponerTema(v: String) { tema = v }

    init {
        this.api.alPerderSesion = { alcance.launch { sesionPerdida() } }
        cola = Cola(this.api, alcance)
        cola.alSubir = { version += 1 }
    }

    val usuario: Sesion.Usuario? get() = sesion?.usuario
    val inmobiliaria: Sesion.Membresia? get() = sesion?.inmobiliarias?.firstOrNull { it.inmobiliariaId == sesion?.inmobiliariaId }
    val rol: String get() = sesion?.rol ?: "lector"
    val esDuenio: Boolean get() = rol == "duenio"
    /** Agente o dueño: puede cargar y cambiar cosas (el lector sólo mira). */
    val escribe: Boolean get() = rol != "lector"

    /** La suscripción de la inmobiliaria (prueba, plan, sólo lectura). Se recarga con el tiempo real. */
    var suscripcion by mutableStateOf<Suscripcion?>(null)

    suspend fun cargarSuscripcion() {
        if (sesion?.inmobiliariaId == null) return
        api.opcional<Suscripcion>("/v1/suscripcion")?.let { suscripcion = it }
    }

    /** Corre algo en el alcance de la app (Task { … } en iOS). */
    fun tarea(bloque: suspend CoroutineScope.() -> Unit): Job = alcance.launch(block = bloque)

    // MARK: Arranque

    suspend fun arrancar() {
        if (api.token == null) { fase = Fase.afuera; return }
        // La marca guardada, para arrancar ya con el logo y el color de la inmobiliaria.
        Guardado.leer<Estructura>("estructura")?.first?.let { e ->
            estructura = e
            ponerAcento(Acento.de(e.marca?.colorPrimario, e.marca?.colorSecundario))
        }
        try {
            cargarSesion()
            if (conFaceID) bloqueada = true
            entrarSegunInmobiliaria()
        } catch (e: CancellationException) {
            throw e
        } catch (e: ErrorApi) {
            // Sólo un 401 cierra la sesión. Sin señal, o si la API falla (500, 502, un corte), se entra igual con lo
            // guardado (E4: «Sin conexión»); con la API caída, además se avisa.
            if (e.estado == 401) { olvidarSesion(); fase = Fase.afuera } else entrarConLoGuardado(conAviso = !e.sinConexion)
        } catch (e: Exception) {
            entrarConLoGuardado(conAviso = true)
        }
    }

    /** El arranque sin la sesión fresca de la API: la última guardada, sin borrar el token. */
    private fun entrarConLoGuardado(conAviso: Boolean) {
        sesion = Guardado.leer<Sesion>("sesion")?.first
        if (conFaceID) bloqueada = true
        fase = if (sesion?.inmobiliariaId == null) Fase.afuera else Fase.adentro
        if (!conAviso) return
        if (fase == Fase.adentro) avisar("No pudimos conectar con Corredor. Ves lo último guardado.", ok = false)
        else avisar("Corredor no responde. Probá de nuevo en un rato.", ok = false)
    }

    private suspend fun cargarSesion() {
        val s: Sesion = api.get("/v1/auth/sesion")
        sesion = s
        Guardado.guardar(s, "sesion")
        SesionCompartida.guardar(api.token, api.base)
    }

    private fun entrarSegunInmobiliaria() {
        val s = sesion ?: run { fase = Fase.afuera; return }
        val t = invitacionPendiente
        if (t != null && s.inmobiliariaId == null) {
            // Recién entra y lo invitaron: la invitación se ve (y se acepta) sobre la pantalla de elegir.
            fase = Fase.eligiendo
            invitacionPendiente = null
            invitacionAbierta = InvitacionAbierta(t)
        } else if (s.inmobiliariaId == null || (s.inmobiliarias.size > 1 && !Preferencias.bool("eligio-${s.usuario.id}"))) {
            fase = Fase.eligiendo
        } else {
            fase = Fase.adentro
            Spotlight.indexarSecciones(duenio = esDuenio)
            tarea { despuesDeEntrar() }
        }
    }

    private suspend fun despuesDeEntrar() {
        invitacionPendiente?.let { t ->
            invitacionPendiente = null
            invitacionAbierta = InvitacionAbierta(t)
        }
        // Las compras de Google Play que llegan solas (renovaciones, otro teléfono) van a la API.
        Tienda.compartida.api = api
        Tienda.compartida.escuchar()
        cargarEstructura()
        cargarSuscripcion()
        if (!ModoPrueba.activo) pedirPermisoPush()
        cola.subir()
    }

    // MARK: Ingreso (1B)

    /** La vuelta de la Custom Tab de Clerk: el nonce, o CANCELADO si la persona la cerró. */
    private var vuelta: CompletableDeferred<String?>? = null

    /** «Seguir con Apple» no va en Android (sólo Google). */
    suspend fun ingresarConApple(): Unit = throw Clerk.ErrorClerk("sin_apple")

    suspend fun ingresarConGoogle() {
        val clerk = Clerk.crear(BuildConfig.CLERK_PUBLICABLE, api.http) ?: throw Clerk.ErrorClerk("sin_clave")
        val url = clerk.iniciar()
        val vuelta = abrirHojaWeb(url)
        canjear(clerk.completar(Uri.parse(vuelta).getQueryParameter("rotating_token_nonce") ?: ""))
    }

    /** La hoja web del sistema (HojaWeb con ASWebAuthenticationSession en iOS): abre `url` en una Custom Tab y espera
     *  la vuelta a `ar.com.corredor.app://…` (Clerk, conectar Mercado Libre). Devuelve el link de vuelta entero; si la
     *  persona la cierra, ErrorClerk «cancelado». */
    suspend fun abrirHojaWeb(url: String): String {
        val d = CompletableDeferred<String?>()
        vuelta = d
        abrirPestana(url)
        val r = d.await()
        vuelta = null
        if (r == null || r == CANCELADO) throw Clerk.ErrorClerk("cancelado")
        return r
    }

    /** MainActivity: llegó la vuelta (`ar.com.corredor.app://callback`). */
    fun vueltaClerk(url: String) { vuelta?.complete(url) }

    /** MainActivity: la Custom Tab se cerró sin vuelta (ASWebAuthenticationSessionError.canceledLogin). */
    fun clerkCancelado() { vuelta?.complete(CANCELADO) }
    val esperandoClerk: Boolean get() = vuelta != null

    /** El JWT de una sesión de Clerk (ingreso con mail y contraseña, recuperar el acceso) → la sesión. */
    suspend fun ingresarConJWT(jwt: String) { canjear(jwt) }

    @Serializable private data class CuerpoToken(val token: String)

    private suspend fun canjear(jwt: String) {
        val i: Ingreso = api.enviar("POST", "/v1/auth/clerk", CuerpoToken(jwt))
        Llavero.guardar(i.token, "token")
        api.token = i.token
        Guardado.borrarTodo()
        sesion = i.sesion
        Guardado.guardar(i.sesion, "sesion")
        SesionCompartida.guardar(i.token, api.base)
        entrarSegunInmobiliaria()
    }

    /** Una invitación pendiente de aceptar (el link llegó antes de entrar). */
    var invitacionPendiente: String?
        get() = Preferencias.texto("invitacion")
        set(v) = Preferencias.ponerTexto("invitacion", v)

    /** El link de una invitación (corredor.com.ar/invitacion/<token>): sin sesión se guarda para después de entrar; con
     *  sesión se muestra (de dónde es, con qué rol, a qué mail) antes de aceptarla. */
    fun abrirInvitacion(token: String) {
        if (api.token == null || fase == Fase.afuera || fase == Fase.arrancando) {
            invitacionPendiente = token
            if (api.token == null || fase == Fase.afuera) avisar("Entrá con tu cuenta y te mostramos la invitación.")
            return
        }
        invitacionAbierta = InvitacionAbierta(token)
    }

    /** Acepta la invitación y entra a esa inmobiliaria (después, `VerInvitacion` abre la bienvenida). */
    suspend fun aceptarInvitacion(token: String) {
        val antes = sesion?.inmobiliarias?.map { it.id }?.toSet() ?: emptySet()
        api.hacer("POST", "/v1/invitaciones/aceptar", CuerpoToken(token))
        invitacionPendiente = null
        cargarSesion()
        sesion?.inmobiliarias?.firstOrNull { it.id !in antes }?.let { nueva ->
            elegir(nueva)
            avisar("Ya estás en ${nueva.nombre} como ${ElegirInmobiliaria.rol(nueva.rol).lowercase()}")
        }
    }

    /** «No soy yo»: salir y volver a entrar con la cuenta del mail invitado, sin perder la invitación. */
    suspend fun otraCuenta(invitacion: String) {
        invitacionAbierta = null
        salir()
        invitacionPendiente = invitacion
    }

    @Serializable private data class CuerpoInmobiliaria(val inmobiliariaId: String)

    /** 1C: elegir (o cambiar) la inmobiliaria. */
    suspend fun elegir(m: Sesion.Membresia) {
        val s: Sesion = api.enviar("PUT", "/v1/auth/inmobiliaria", CuerpoInmobiliaria(m.inmobiliariaId))
        if (s.inmobiliariaId != sesion?.inmobiliariaId) {
            Guardado.borrarTodo()
            pilas.values.forEach { it.clear() }
            estructura = null
            ponerAcento(null)
        }
        sesion = s
        Guardado.guardar(s, "sesion")
        Preferencias.ponerBool("eligio-${s.usuario.id}", true)
        fase = Fase.adentro
        Spotlight.indexarSecciones(duenio = esDuenio)
        version += 1
        tarea { despuesDeEntrar() }
    }

    @Serializable private data class CuerpoCrear(val nombre: String, val slug: String)
    @Serializable private data class Creada(val id: String)

    /** Crear la primera inmobiliaria desde la app (la cuenta nueva sin ninguna), o otra teniendo ya una: la persona queda
     *  como dueña y la sesión pasa a la nueva. `slug`: la dirección de la web (vacía, la arma la API). */
    suspend fun crearInmobiliaria(nombre: String, slug: String = "") {
        val c: Creada = api.enviar("POST", "/v1/inmobiliarias", CuerpoCrear(nombre, slug))
        cargarSesion()
        sesion?.inmobiliarias?.firstOrNull { it.inmobiliariaId == c.id }?.let { elegir(it) }
    }

    suspend fun salir() {
        tokenPush?.let { t -> intentar { api.hacer("DELETE", "/v1/push/dispositivos/$t") } }
        intentar { api.hacer("POST", "/v1/auth/salir") }
        olvidarSesion()
        fase = Fase.afuera
    }

    private fun sesionPerdida() {
        olvidarSesion()
        fase = Fase.afuera
        avisar("Tu sesión venció. Entrá de nuevo.", ok = false)
    }

    private fun olvidarSesion() {
        estructura = null
        ponerAcento(null)
        altaAbierta = null
        Llavero.borrar("token")
        api.token = null
        sesion = null
        pilas.values.forEach { it.clear() }
        Guardado.borrarTodo()
        cola.vaciar()
        Insignias.compartidas.olvidar()
        SesionCompartida.guardar(null, api.base)
    }

    // MARK: Navegación

    /** Lo apilado en una pestaña (la NavigationPath de iOS). */
    fun pila(p: Pestana): SnapshotStateList<Any> = pilas.getValue(p)

    /** Ir a una pestaña y apilar un destino (desde cualquier pantalla, un push o un widget). */
    fun ir(r: Ruta) {
        if (r.pestana == Pestana.mira) {
            miraAbierta = true
            r.destino?.let { d -> pila(Pestana.inicio).add(d); pestana = Pestana.inicio }
            return
        }
        pestana = r.pestana
        r.destino?.let { d -> pila(r.pestana).apply { clear(); add(d) } }
    }

    /** Apila un destino (`AFichaPropiedad(id)`, `ASeccion("plan")`…) en la pestaña que se ve o en `en`. */
    fun abrir(destino: Any, en: Pestana? = null) {
        pila(en ?: pestana).add(destino)
    }

    /** Vuelve una pantalla (dismiss de una apilada). */
    fun volver() { pila(pestana).removeLastOrNull() }

    // MARK: Avisos

    /** El aviso de abajo. Con `deshacer`, 5 s para arrepentirse (handoff · Comportamiento). */
    fun avisar(texto: String, ok: Boolean = true, deshacer: (() -> Unit)? = null) {
        val a = Aviso(texto, ok, deshacer)
        aviso = a
        avisoTarea?.cancel()
        avisoTarea = alcance.launch {
            delay(if (deshacer == null) 3000 else 5000)
            if (aviso?.id == a.id) aviso = null
        }
    }

    /** El paso siguiente, en el momento justo: después de cargar, captar, cerrar o cobrar, el aviso de abajo propone lo
     *  que suele venir después y lleva ahí en un toque (dura 6 s). */
    fun sugerir(texto: String, boton: String, destino: Any) {
        sugerir(texto, boton, hacer = { abrir(destino) })
    }

    fun sugerir(texto: String, boton: String, hacer: () -> Unit) {
        val a = Aviso(texto, ok = true, accion = boton to hacer)
        aviso = a
        avisoTarea?.cancel()
        avisoTarea = alcance.launch {
            delay(6000)
            if (aviso?.id == a.id) aviso = null
        }
    }

    /** El error de un pedido, dicho en palabras. */
    fun avisar(e: Throwable) {
        // 402: la prueba terminó, el plan no trae eso o no hay lugar en el equipo: lleva a «Tu plan».
        if (e is ErrorApi && e.estado == 402) {
            sugerir(e.detalle.ifEmpty { e.titulo }, "Ver planes", ASeccion("plan"))
            tarea { cargarSuscripcion() }
            return
        }
        if (e is ErrorApi) avisar(e.detalle.ifEmpty { e.titulo }, ok = false) else avisar("Algo salió mal. Probá de nuevo.", ok = false)
    }

    // MARK: Lo del sistema (openURL, ShareLink, UIPasteboard)

    /** openURL de un link cualquiera (https, tel:, mailto:, wa.me). Llamar abre el marcador. */
    fun abrirUrl(url: String) {
        contexto?.let { ar.com.corredor.app.nucleo.abrirUrl(it, url) }
    }

    /** ShareLink de un texto (o un link). */
    fun compartirTexto(texto: String, asunto: String? = null) {
        contexto?.let { ar.com.corredor.app.nucleo.compartirTexto(it, texto, asunto) }
    }

    /** ShareLink de un archivo (un PDF, una placa, un CSV). Va por el FileProvider (cache/compartir). */
    fun compartirArchivo(archivo: File, tipo: String, texto: String? = null) {
        contexto?.let { ar.com.corredor.app.nucleo.compartirArchivo(it, archivo, tipo, texto) }
    }

    /** UIPasteboard.general.string = … */
    fun copiar(texto: String) {
        contexto?.let { ar.com.corredor.app.nucleo.copiar(it, texto) }
    }

    // MARK: Face ID (1D)

    suspend fun desbloquear() {
        if (alConfirmarIdentidad("Entrar a Corredor")) bloqueada = false
    }

    /** La huella, la cara o el PIN para lo sensible (borrar la cuenta). */
    suspend fun confirmarIdentidad(motivo: String): Boolean = alConfirmarIdentidad(motivo)

    // MARK: Push

    /** Pide el permiso de avisos (Android 13+) y, si está, registra el token de FCM. */
    suspend fun pedirPermisoPush() {
        val ctx = contexto ?: return
        if (!alPedirPermisoPush()) return
        Push.token(ctx)?.let { registrarTokenPush(it) }
    }

    /** «Avisos en este dispositivo · Apagar» (perfil): este teléfono deja de recibir sin tocar los ajustes del sistema ni
     *  lo que se elige en «Avisos». */
    var pushApagado: Boolean
        get() = Preferencias.bool("pushApagado")
        set(v) = Preferencias.ponerBool("pushApagado", v)

    /** Apaga los avisos de este teléfono (lo borra de la API). */
    suspend fun apagarPush() {
        tokenPush?.let { api.hacer("DELETE", "/v1/push/dispositivos/$it") }
        pushApagado = true
    }

    /** Los vuelve a prender (pide el permiso si hace falta y anota el token). */
    suspend fun prenderPush() {
        pushApagado = false
        tokenPush?.let { registrarTokenPush(it) }
        pedirPermisoPush()
    }

    @Serializable
    data class Dispositivo(
        val token: String,
        /** Android: el token es de FCM (la API lo manda por Firebase y no por APNs). */
        val plataforma: String = "android",
        val entorno: String = if (BuildConfig.DEBUG) "sandbox" else "produccion",
    )

    suspend fun registrarTokenPush(token: String) {
        tokenPush = token
        // Apagados en este teléfono desde el perfil: no se vuelve a anotar solo.
        if (pushApagado) return
        intentar { api.hacer("POST", "/v1/push/dispositivos", Dispositivo(token)) }
    }

    companion object {
        private const val CANCELADO = "\u0000cancelado"
    }
}

val LocalEstado = staticCompositionLocalOf<Estado> { error("Falta el Estado") }

/** La cola sin conexión (handoff · «Sin conexión»): lo que se carga sin señal queda guardado en disco con su estado
 *  visible («2 cambios en cola») y se sube solo cuando vuelve la red. Cada cambio va con su Idempotency-Key, así la API
 *  no lo duplica si se reintenta. */
class Cola(private val api: Api, private val alcance: CoroutineScope) {
    @Serializable
    data class Cambio(
        val id: String = UUID.randomUUID().toString(),
        val metodo: String,
        val ruta: String,
        /** El cuerpo en JSON (el Data de iOS). */
        val cuerpo: String? = null,
        /** Lo que se le muestra a la persona: «Alta de Gorriti 4820», «Cobro de Lucía Méndez». */
        val etiqueta: String,
        val creado: Long = Instant.now().toEpochMilli(),
        val intentos: Int = 0,
        val error: String? = null,
    )

    var cambios by mutableStateOf(Guardado.leer<List<Cambio>>(CLAVE)?.first ?: emptyList())
        private set
    var subiendo by mutableStateOf(false)
        private set
    var alSubir: (() -> Unit)? = null

    /** Manda el cambio ya; si no hay señal, queda en la cola y devuelve `false`. */
    suspend inline fun <reified C> enviar(metodo: String, ruta: String, cuerpo: C?, etiqueta: String): Boolean =
        enviarCrudo(metodo, ruta, cuerpo?.let { Api.json.encodeToString(it) }, etiqueta)

    suspend fun enviarCrudo(metodo: String, ruta: String, cuerpo: String?, etiqueta: String): Boolean {
        val c = Cambio(metodo = metodo, ruta = ruta, cuerpo = cuerpo, etiqueta = etiqueta)
        return try {
            api.pedir(metodo, ruta, cuerpo = cuerpo?.toByteArray(), envio = c.id)
            true
        } catch (e: ErrorApi) {
            if (!e.sinConexion) throw e
            cambios = cambios + c.copy(intentos = 1)
            guardar()
            false
        }
    }

    fun subir() {
        if (subiendo || cambios.isEmpty()) return
        subiendo = true
        alcance.launch {
            try {
                for (c in cambios.toList()) {
                    try {
                        api.pedir(c.metodo, c.ruta, cuerpo = c.cuerpo?.toByteArray(), envio = c.id)
                        cambios = cambios.filter { it.id != c.id }
                        alSubir?.invoke()
                    } catch (e: ErrorApi) {
                        if (e.sinConexion) break
                        // La API lo rechazó (no es la señal): queda con el motivo para que se vea y se corrija.
                        cambios = cambios.map { if (it.id == c.id) it.copy(intentos = it.intentos + 1, error = e.detalle) else it }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                    }
                }
                guardar()
            } finally {
                subiendo = false
            }
        }
    }

    fun descartar(c: Cambio) {
        cambios = cambios.filter { it.id != c.id }
        guardar()
    }

    fun vaciar() {
        cambios = emptyList()
        guardar()
    }

    private fun guardar() { Guardado.guardar(cambios, CLAVE) }

    companion object { private const val CLAVE = "cola" }
}

/** HojaWeb de iOS (Nucleo/Clerk.swift): la hoja web del sistema para un ida y vuelta (conectar Mercado Libre). */
class HojaWeb {
    suspend fun abrir(url: String): String = Abrir.estado?.get()?.abrirHojaWeb(url) ?: throw Clerk.ErrorClerk("clerk_no_responde")
}
