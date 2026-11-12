package ar.com.corredor.app

import android.Manifest
import android.content.Intent
import android.content.res.Configuration
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import ar.com.corredor.app.app.Abrir
import ar.com.corredor.app.app.ActividadVisita
import ar.com.corredor.app.app.CorredorApp
import ar.com.corredor.app.app.EnPrimerPlano
import ar.com.corredor.app.app.Estado
import ar.com.corredor.app.app.LocalEstado
import ar.com.corredor.app.app.Raiz
import ar.com.corredor.app.app.abrirLink
import ar.com.corredor.app.nucleo.Clerk
import ar.com.corredor.app.nucleo.Ruta
import ar.com.corredor.app.nucleo.Tienda
import ar.com.corredor.app.pantallas.inicio.Avisos
import ar.com.corredor.app.tema.Haptico
import ar.com.corredor.app.tema.TemaCorredor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

/** La única actividad (el WindowGroup de CorredorApp.swift y el Delegado de App/Push.swift): toma el Estado del proceso,
 *  recibe los links (`corredor://…`, `https://corredor.com.ar/…`), la vuelta de Clerk (`ar.com.corredor.app://callback`)
 *  y los avisos tocados, pide los permisos y la huella, y vuelve a bloquear la app si estuvo más de un minuto afuera
 *  con la huella prendida. Es FragmentActivity por BiometricPrompt.
 *
 *  El Estado no vive acá (`CorredorApp.estado`): si Android rehace la actividad, la sesión y la navegación siguen. Lo
 *  que está esperando una respuesta del sistema (un permiso, la huella, la Custom Tab) también vive en el proceso
 *  (`Pendientes`), para que la actividad nueva lo complete y nada quede colgado. */
class MainActivity : FragmentActivity() {
    private val estado: Estado get() = CorredorApp.estado

    /** Lo que espera al sistema mientras la actividad se rehace. */
    private object Pendientes {
        var permiso: CompletableDeferred<Boolean>? = null
        var permisos: CompletableDeferred<Boolean>? = null
        var identidad: CompletableDeferred<Boolean>? = null
        /** Se abrió la Custom Tab y si volvió con una vuelta (si no, la persona la cerró: «cancelado» en iOS). */
        var abrioPestana = false
        var conVuelta = false
        /** Cuándo se fue a segundo plano (para volver a bloquear con la huella). */
        var enSegundoPlano: Long? = null

        /** Completa con «no» lo que quedó esperando (la actividad se cerró del todo). */
        fun soltar() {
            permiso?.complete(false); permiso = null
            permisos?.complete(false); permisos = null
            identidad?.complete(false); identidad = null
        }
    }

    private var huella: BiometricPrompt? = null
    private val lanzadorPermiso = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        Pendientes.permiso?.complete(ok); Pendientes.permiso = null
    }
    private val lanzadorVarios = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        Pendientes.permisos?.complete(r.values.any { it }); Pendientes.permisos = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT))
        Haptico.vista = WeakReference(window.decorView)
        estado.contexto = applicationContext
        estado.abrirPestana = { url -> abrirPestana(url) }
        estado.alPedirPermisoPush = { pedirPermisoAvisos() }
        estado.alPedirPermiso = { p -> pedir(p) }
        estado.alPedirPermisos = { p -> pedirVarios(p) }
        estado.alConfirmarIdentidad = { motivo -> confirmar(motivo) }
        Abrir.estado = WeakReference(estado)
        ActividadVisita.api = estado.api
        Tienda.compartida.actividad = WeakReference(this)
        // Con la app abierta, un aviso que llega pone al día las listas (willPresent en iOS).
        EnPrimerPlano.alAviso = { lifecycleScope.launch { estado.version += 1 } }
        // Sólo la primera vez: si la actividad se rehace, el intent es el viejo y ya se atendió. Tampoco el que vuelve
        // a dar Android al abrir desde las recientes.
        val deRecientes = (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) != 0
        if (savedInstanceState == null && !deRecientes) atender(intent)
        setContent {
            TemaCorredor(estado.tema) {
                CompositionLocalProvider(LocalEstado provides estado) { Raiz(estado) }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // La huella no sobrevive a la actividad: se cancela y queda «no» (la pantalla de bloqueo deja reintentar).
        huella?.cancelAuthentication(); huella = null
        Pendientes.identidad?.complete(false); Pendientes.identidad = null
        // Si se rehace, el resultado del permiso le llega a la actividad nueva; si se cierra del todo, ya no llega.
        if (!isChangingConfigurations) {
            Pendientes.soltar()
            if (Pendientes.abrioPestana) { Pendientes.abrioPestana = false; estado.clerkCancelado() }
        }
    }

    /** Los cambios del manifest (`configChanges`: modo oscuro, letra, ventana) no rehacen la actividad: Compose se pinta
     *  de nuevo solo, y acá se vuelven a elegir los íconos de las barras para el modo nuevo. */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT))
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        atender(intent)
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) Pendientes.enSegundoPlano = System.currentTimeMillis()
    }

    override fun onStart() {
        super.onStart()
        // Con la huella prendida, volver después de un minuto pide identificarse (1D).
        val d = Pendientes.enSegundoPlano
        if (estado.conFaceID && estado.fase == Estado.Fase.adentro && d != null && System.currentTimeMillis() - d > 60_000 && !Pendientes.abrioPestana) estado.bloqueada = true
        Pendientes.enSegundoPlano = null
        if (estado.fase == Estado.Fase.adentro && d != null) {
            estado.cola.subir()
            estado.version += 1
        }
    }

    override fun onResume() {
        super.onResume()
        // Se cerró la Custom Tab sin terminar: como cancelar la hoja web en iOS.
        if (Pendientes.abrioPestana && !Pendientes.conVuelta) estado.clerkCancelado()
        Pendientes.abrioPestana = false
    }

    private fun abrirPestana(url: String) {
        Pendientes.conVuelta = false
        Pendientes.abrioPestana = true
        val colores = CustomTabColorSchemeParams.Builder().setToolbarColor(0xFFF3EFE6.toInt()).build()
        CustomTabsIntent.Builder().setShowTitle(true).setDefaultColorSchemeParams(colores).build().launchUrl(this, Uri.parse(url))
    }

    /** La vuelta de Clerk, un aviso tocado (con su acción) o un link de la app. */
    private fun atender(i: Intent?) {
        val u = i?.data ?: return
        if (u.scheme == Clerk.ESQUEMA) {
            Pendientes.conVuelta = true
            estado.vueltaClerk(u.toString())
            return
        }
        val accion = i.getStringExtra("accion")
        val aviso = i.getStringExtra("avisoId")
        i.removeExtra("accion"); i.removeExtra("avisoId")
        // La respuesta escrita en el aviso no salió: se abre el chat para mandarla a mano.
        if (accion == "fallo-respuesta") {
            Ruta(u.toString())?.let { estado.ir(it) }
            estado.avisar("No se mandó la respuesta. Probá desde el chat.", ok = false)
            return
        }
        // «Llamar» de una consulta sin `telefono` (con él, el aviso abre el marcador): como iOS, el contacto, para
        // llamarlo desde ahí.
        if (accion == "llamar") { abrirLink(estado, u); return }
        // Otra acción del aviso (las que pide la API): el aviso con esa acción abierta.
        if (accion != null && aviso != null && estado.fase == Estado.Fase.adentro) {
            Avisos.abrir(estado, aviso, accion)
            return
        }
        if (aviso != null && (u.host == "inicio" && u.pathSegments.isEmpty()) && estado.fase == Estado.Fase.adentro) {
            Avisos.abrir(estado, aviso, null)
            return
        }
        abrirLink(estado, u)
    }

    /** Un permiso del sistema: true si ya estaba o si lo dio. */
    private suspend fun pedir(p: String): Boolean {
        if (checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED) return true
        val d = CompletableDeferred<Boolean>()
        Pendientes.permiso?.complete(false)
        Pendientes.permiso = d
        lanzadorPermiso.launch(p)
        return d.await()
    }

    /** Varios permisos en un diálogo: true si ya estaba alguno o si dio alguno (la ubicación aproximada alcanza). */
    private suspend fun pedirVarios(p: Array<String>): Boolean {
        if (p.any { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }) return true
        val d = CompletableDeferred<Boolean>()
        Pendientes.permisos?.complete(false)
        Pendientes.permisos = d
        lanzadorVarios.launch(p)
        return d.await()
    }

    private suspend fun pedirPermisoAvisos(): Boolean {
        if (Build.VERSION.SDK_INT < 33) return true
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return true
        return pedir(Manifest.permission.POST_NOTIFICATIONS)
    }

    /** La huella, la cara o el PIN (.deviceOwnerAuthentication): sin nada configurado, deja pasar (como iOS). */
    private suspend fun confirmar(motivo: String): Boolean {
        val permitidos = if (Build.VERSION.SDK_INT >= 30) BIOMETRIC_STRONG or BIOMETRIC_WEAK or DEVICE_CREDENTIAL else BIOMETRIC_WEAK or DEVICE_CREDENTIAL
        if (BiometricManager.from(this).canAuthenticate(permitidos) != BiometricManager.BIOMETRIC_SUCCESS) return true
        val d = CompletableDeferred<Boolean>()
        Pendientes.identidad?.complete(false)
        Pendientes.identidad = d
        val p = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { d.complete(true) }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) { d.complete(false) }
        })
        huella = p
        p.authenticate(BiometricPrompt.PromptInfo.Builder().setTitle("Corredor").setSubtitle(motivo).setAllowedAuthenticators(permitidos).build())
        return try { d.await() } finally { if (Pendientes.identidad === d) Pendientes.identidad = null; if (huella === p) huella = null }
    }
}
