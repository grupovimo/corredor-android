package ar.com.corredor.app.compartido

import android.content.Context
import android.util.Base64
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager

/** La sesión de Corredor en el teléfono (el Keychain de iOS): cifrada con Tink (AES-GCM, la clave vive en el Android
 *  Keystore), sólo en este aparato (sin copia de seguridad). Los widgets y los avisos corren en el mismo proceso. En las
 *  pruebas, sin contexto, queda en memoria. */
object Llavero {
    const val servicio = "ar.com.corredor.app.sesion"
    var contexto: Context? = null
    private val prefs get() = contexto?.getSharedPreferences(servicio, Context.MODE_PRIVATE)
    private val memoria = HashMap<String, String>()

    private val aead: Aead? by lazy {
        val c = contexto ?: return@lazy null
        runCatching {
            AeadConfig.register()
            AndroidKeysetManager.Builder()
                .withSharedPref(c, "cr_llavero", "cr_llaves")
                .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
                .withMasterKeyUri("android-keystore://cr_maestra")
                .build().keysetHandle.getPrimitive(RegistryConfiguration.get(), Aead::class.java)
        }.getOrNull()
    }

    fun guardar(valor: String, cuenta: String) {
        val a = aead ?: run { memoria[cuenta] = valor; return }
        prefs?.edit()?.putString(cuenta, Base64.encodeToString(a.encrypt(valor.toByteArray(), cuenta.toByteArray()), Base64.NO_WRAP))?.apply()
    }

    fun leer(cuenta: String): String? {
        val a = aead ?: return memoria[cuenta]
        val t = prefs?.getString(cuenta, null) ?: return null
        return runCatching { String(a.decrypt(Base64.decode(t, Base64.NO_WRAP), cuenta.toByteArray())) }.getOrNull()
    }

    fun borrar(cuenta: String) {
        memoria.remove(cuenta)
        prefs?.edit()?.remove(cuenta)?.apply()
    }
}
