package ar.com.corredor.app.nucleo

import ar.com.corredor.app.BuildConfig
import okhttp3.OkHttpClient

/** El ingreso con mail completo, como la web (web/src/lib/clerk.ts, «Con mail»): según la cuenta, contraseña o código
 *  por mail; alguien nuevo se da de alta con el código (y la contraseña, si la instancia la pide); y recuperar el acceso
 *  (código `reset_password_email_code`, contraseña nueva y cierre de las otras sesiones). Habla con la Frontend API de
 *  Clerk en modo nativo, igual que `Clerk` (nucleo/Clerk.kt). */
class ClerkMail private constructor(val fapi: String, private val http: OkHttpClient) {
    /** El paso que sigue en la pantalla. */
    sealed class Paso {
        data object clave : Paso()
        data class codigo(val alta: Boolean) : Paso()
        data class claveNueva(val alta: Boolean) : Paso()
        data class listo(val token: String) : Paso()
    }

    private var cliente = ""
    private var id = ""
    var alta = false
        private set

    private suspend fun pedir(ruta: String, metodo: String = "GET", cuerpo: Map<String, String>? = null, conCliente: Boolean = true): Clerk.Respuesta {
        val (r, nuevo) = Clerk.pedir(http, fapi, ruta, metodo, cuerpo, if (conCliente) cliente else null)
        if (nuevo.isNotEmpty()) cliente = nuevo
        return r
    }

    /** El JWT de la sesión de Clerk; la sesión de Clerk se cierra (Corredor sigue con la propia). */
    private suspend fun sesionDe(sesion: String?): String {
        if (sesion == null || !Regex("^sess_[A-Za-z0-9]+$").matches(sesion)) throw Clerk.ErrorClerk("cancelado")
        val t = pedir("/client/sessions/$sesion/tokens", "POST", emptyMap())
        val jwt = t.jwt?.takeIf { it.isNotEmpty() } ?: throw Clerk.ErrorClerk("clerk_no_responde")
        runCatching { pedir("/client/sessions/$sesion/remove", "POST", emptyMap()) }
        return jwt
    }

    /** Arranca con el mail: si tiene cuenta, ingreso (contraseña o código); si no, alta con código. */
    suspend fun empezar(email: String): Paso {
        cliente = ""
        return try {
            val r = pedir("/client/sign_ins", "POST", mapOf("identifier" to email), conCliente = false)
            id = r.response?.id ?: ""
            alta = false
            val factores = r.response?.supported_first_factors ?: emptyList()
            if (factores.any { it.strategy == "password" }) return Paso.clave
            val mail = factores.firstOrNull { it.strategy == "email_code" }?.email_address_id ?: throw Clerk.ErrorClerk("sin_mail")
            pedir("/client/sign_ins/$id/prepare_first_factor", "POST", mapOf("strategy" to "email_code", "email_address_id" to mail))
            Paso.codigo(alta = false)
        } catch (e: Clerk.ErrorClerk) {
            if (e.codigo != "form_identifier_not_found") throw e
            // Alta: el mail, y verificarlo con un código.
            cliente = ""
            val a = pedir("/client/sign_ups", "POST", mapOf("email_address" to email), conCliente = false)
            id = a.response?.id ?: ""
            alta = true
            pedir("/client/sign_ups/$id/prepare_verification", "POST", mapOf("strategy" to "email_code"))
            Paso.codigo(alta = true)
        }
    }

    /** Con la contraseña de la cuenta. */
    suspend fun entrarConClave(clave: String): Paso {
        val r = pedir("/client/sign_ins/$id/attempt_first_factor", "POST", mapOf("strategy" to "password", "password" to clave))
        return Paso.listo(sesionDe(r.response?.created_session_id))
    }

    /** El código que llegó por mail (ingreso, alta o recuperar). */
    suspend fun confirmarCodigo(codigo: String, recuperar: Boolean = false): Paso {
        val ruta = if (alta) "/client/sign_ups/$id/attempt_verification" else "/client/sign_ins/$id/attempt_first_factor"
        val solo = codigo.filter { it.isDigit() }
        val r = pedir(ruta, "POST", mapOf("strategy" to if (recuperar) "reset_password_email_code" else "email_code", "code" to solo))
        if (r.response?.status == "needs_new_password") return Paso.claveNueva(alta = false)
        // La instancia pide contraseña para el alta.
        if (alta && r.response?.status == "missing_requirements" && r.response.missing_fields?.contains("password") == true) return Paso.claveNueva(alta = true)
        return Paso.listo(sesionDe(r.response?.created_session_id))
    }

    /** La contraseña nueva: al recuperar (cierra las otras sesiones de Clerk) o la que pide el alta. */
    suspend fun guardarClave(clave: String): Paso {
        val r = if (alta) pedir("/client/sign_ups/$id", "PATCH", mapOf("password" to clave))
        else pedir("/client/sign_ins/$id/reset_password", "POST", mapOf("password" to clave, "sign_out_of_other_sessions" to "true"))
        return Paso.listo(sesionDe(r.response?.created_session_id))
    }

    /** Recuperar: arranca el ingreso con el mail y pide el código para la contraseña nueva. `false` si el mail no tiene
     *  cuenta o no usa contraseña (la pantalla dice lo mismo: no se revela quién está registrado). */
    suspend fun pedirRecuperar(email: String): Boolean {
        cliente = ""
        alta = false
        val r = try {
            pedir("/client/sign_ins", "POST", mapOf("identifier" to email), conCliente = false)
        } catch (e: Clerk.ErrorClerk) {
            if (e.codigo == "form_identifier_not_found") return false
            throw e
        }
        id = r.response?.id ?: ""
        val mail = r.response?.supported_first_factors?.firstOrNull { it.strategy == "reset_password_email_code" }?.email_address_id ?: return false
        pedir("/client/sign_ins/$id/prepare_first_factor", "POST", mapOf("strategy" to "reset_password_email_code", "email_address_id" to mail))
        return true
    }

    companion object {
        fun crear(publicable: String?, http: OkHttpClient = Api.cliente()): ClerkMail? = Clerk.fapiDe(publicable)?.let { ClerkMail(it, http) }

        /** Con la clave publicable de la configuración (la misma que usa `Estado` para Google). */
        fun configurado(): ClerkMail? = crear(BuildConfig.CLERK_PUBLICABLE)

        // MARK: Requisitos y mensajes (los mismos de la web)

        private val comunes = setOf(
            "1234567890", "12345678910", "qwertyuiop", "contraseña", "contrasena", "password12", "password123", "argentina1",
            "0123456789", "1111111111", "abcdefghij", "corredor123", "inmobiliaria", "iloveyou12", "boca123456", "river12345",
        )

        /** Los requisitos de la contraseña nueva que se tildan solos (2c): (largo, comun). */
        fun requisitos(clave: String): Pair<Boolean, Boolean> = (clave.length >= 10) to (clave.isNotEmpty() && !esComun(clave))

        private fun esComun(c: String): Boolean {
            val x = c.lowercase()
            if (x in comunes) return true
            if (x.length > 1 && x.all { it == x[0] }) return true
            return x.startsWith("0123456789") || x.startsWith("1234567890") || x.startsWith("9876543210")
        }

        /** El error de Clerk como lo lee la persona. */
        fun mensaje(codigo: String): String {
            val m = mapOf(
                "form_password_incorrect" to "La contraseña no es esa. Probá de nuevo o recuperala.",
                "form_code_incorrect" to "El código no es ese. Revisalo y probá de nuevo.",
                "verification_expired" to "El código venció. Pedí uno nuevo.",
                "form_password_pwned" to "Esa contraseña apareció en filtraciones. Elegí otra.",
                "form_password_length_too_short" to "La contraseña es muy corta.",
                "form_password_validation_failed" to "La contraseña no cumple los requisitos.",
                "form_param_format_invalid" to "Revisá el mail: no parece válido.",
                "form_identifier_exists" to "Ese mail ya tiene cuenta. Ingresá.",
                "too_many_requests" to "Hubo demasiados intentos. Probá de nuevo en unos minutos.",
                "strategy_for_user_invalid" to "Esa cuenta entra con Google: usá ese botón.",
                "sin_mail" to "Esa cuenta entra con Google: usá ese botón.",
                "sin_conexion" to "No pudimos conectar. Probá de nuevo.",
            )
            return m[codigo] ?: "No pudimos completar el ingreso. Probá de nuevo."
        }
    }
}
