package ar.com.corredor.app.app

import ar.com.corredor.app.BuildConfig

/** Las pruebas de pantallas (Robolectric, app/src/test): la app arranca contra la API grabada, ya adentro, sin el
 *  recorrido, sin consejos, sin Play Billing y sin pedir permiso de avisos. Sólo existe en Debug: lo que sube a Play
 *  (Release) nunca entra en este modo (como `-ModoPruebaUI YES` en iOS). */
object ModoPrueba {
    private var prendido = false

    var activo: Boolean
        get() = BuildConfig.DEBUG && prendido
        set(v) { prendido = v }

    /** El token de la sesión de prueba (la API grabada no lo valida). */
    var token: String? = null
        get() = if (BuildConfig.DEBUG) field else null

    /** La API grabada (`-CorredorApi http://127.0.0.1:8787` en iOS). */
    var api: String? = null
        get() = if (BuildConfig.DEBUG) field else null
}
