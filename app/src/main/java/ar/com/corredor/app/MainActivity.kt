package ar.com.corredor.app

import android.Manifest
import android.content.Intent
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

/** La única actividad (el WindowGroup de CorredorApp.swift y el Delegado de App/Push.swift): arma el Estado, recibe los
 *  links (`corredor://…`, `https://corredor.com.ar/…`), la vuelta de Clerk (`ar.com.corredor.app://callback`) y los
 *  avisos tocados, pide los permisos y la huella, y vuelve a bloquear la app si estuvo más de un minuto afuera con la
 *  huella prendida. Es FragmentActivity por BiometricPrompt. */
class MainActivity : FragmentActivity() {
    private lateinit var estado: Estado
    /** Si la Custom Tab volvió con una vuelta (si no, la persona la cerró: «cancelado» en iOS). */
    private var conVuelta = false
    private var abrioPestana = false
    private var enSegundoPlano: Long? = null
    private var permiso: CompletableDeferred<Boolean>? = null
    private val pedirPermiso = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> permiso?.complete(ok); permiso = null }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT))
        Haptico.vista = WeakReference(window.decorView)
        estado = Estado()
        estado.contexto = applicationContext
        estado.abrirPestana = { url -> abrirPestana(url) }
        estado.alPedirPermisoPush = { pedirPermisoAvisos() }
        estado.alPedirPermiso = { p -> pedir(p) }
        estado.alConfirmarIdentidad = { motivo -> confirmar(motivo) }
        Abrir.estado = WeakReference(estado)
        ActividadVisita.api = estado.api
        Tienda.compartida.actividad = WeakReference(this)
        // Con la app abierta, un aviso que llega pone al día las listas (willPresent en iOS).
        EnPrimerPlano.alAviso = { lifecycleScope.launch { estado.version += 1 } }
        atender(intent)
        setContent {
            TemaCorredor(estado.tema) {
                CompositionLocalProvider(LocalEstado provides estado) { Raiz(estado) }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        atender(intent)
    }

    override fun onStop() {
        super.onStop()
        enSegundoPlano = System.currentTimeMillis()
    }

    override fun onStart() {
        super.onStart()
        if (!::estado.isInitialized) return
        // Con la huella prendida, volver después de un minuto pide identificarse (1D).
        val d = enSegundoPlano
        if (estado.conFaceID && estado.fase == Estado.Fase.adentro && d != null && System.currentTimeMillis() - d > 60_000 && !abrioPestana) estado.bloqueada = true
        enSegundoPlano = null
        if (estado.fase == Estado.Fase.adentro && d != null) {
            estado.cola.subir()
            estado.version += 1
        }
    }

    override fun onResume() {
        super.onResume()
        // Se cerró la Custom Tab sin terminar: como cancelar la hoja web en iOS.
        if (abrioPestana && !conVuelta) estado.clerkCancelado()
        abrioPestana = false
    }

    private fun abrirPestana(url: String) {
        conVuelta = false
        abrioPestana = true
        val colores = CustomTabColorSchemeParams.Builder().setToolbarColor(0xFFF3EFE6.toInt()).build()
        CustomTabsIntent.Builder().setShowTitle(true).setDefaultColorSchemeParams(colores).build().launchUrl(this, Uri.parse(url))
    }

    /** La vuelta de Clerk, un aviso tocado (con su acción) o un link de la app. */
    private fun atender(i: Intent?) {
        val u = i?.data ?: return
        if (u.scheme == Clerk.ESQUEMA) {
            conVuelta = true
            estado.vueltaClerk(u.getQueryParameter("rotating_token_nonce"))
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
        // «Llamar» de una consulta: el aviso no trae el teléfono; se abre la consulta.
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
        permiso = d
        pedirPermiso.launch(p)
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
        val p = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { d.complete(true) }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) { d.complete(false) }
        })
        p.authenticate(BiometricPrompt.PromptInfo.Builder().setTitle("Corredor").setSubtitle(motivo).setAllowedAuthenticators(permitidos).build())
        return d.await()
    }
}
