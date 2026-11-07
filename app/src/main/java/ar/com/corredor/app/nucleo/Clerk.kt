package ar.com.corredor.app.nucleo

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/**
 * Ingreso con Google por Clerk, la identidad compartida de Grupo Vimo, sin el SDK: se habla con la Frontend API en
 * modo nativo (`_is_native=1`), con el token del cliente de Clerk en `Authorization`. Es el mismo flujo que
 * Nucleo/Clerk.swift en iOS (y que Caigo y Tu Local Web en Android):
 *
 * - Google: `oauth_google` con `redirect_url = ar.com.corredor.app://callback`, abierto en una Custom Tab (la
 *   ASWebAuthenticationSession de iOS); la vuelta trae `rotating_token_nonce`. Lo arma [iniciar] y lo cierra [completar].
 * - «Seguir con Apple» no va en Android (sólo Google).
 *
 * Se pide el JWT de la sesión de Clerk, se cierra esa sesión (Corredor sigue con la propia) y el JWT va a
 * `POST /v1/auth/clerk`, que lo verifica y devuelve la sesión. La clave publicable es la de la configuración
 * (BuildConfig.CLERK_PUBLICABLE, la CLERK_PUBLICABLE de iOS).
 */
class Clerk private constructor(val fapi: String, private val http: OkHttpClient) {
    class ErrorClerk(val codigo: String) : Exception(codigo)

    @Serializable internal data class Verificacion(val status: String? = null, val external_verification_redirect_url: String? = null)
    @Serializable internal data class Factor(val strategy: String? = null, val email_address_id: String? = null)
    @Serializable internal data class Recurso(
        val id: String? = null,
        val status: String? = null,
        val created_session_id: String? = null,
        val first_factor_verification: Verificacion? = null,
        val supported_first_factors: List<Factor>? = null,
        val missing_fields: List<String>? = null,
    )
    @Serializable internal data class ErrorFapi(val code: String? = null)
    @Serializable internal data class Respuesta(val response: Recurso? = null, val jwt: String? = null, val errors: List<ErrorFapi>? = null)

    /** El intento de Google en curso: el token del cliente y el id del intento (para la vuelta de la Custom Tab). */
    private var cliente: String = ""
    private var intento: String = ""

    private suspend fun pedir(ruta: String, metodo: String = "GET", cuerpo: Map<String, String>? = null, cliente: String? = null, query: Consulta = emptyList()): Pair<Respuesta, String> =
        Companion.pedir(http, fapi, ruta, metodo, cuerpo, cliente, query)

    /** Con el intento ya creado: si quedó «transferable» (persona nueva en Clerk) lo pasa a alta, pide el JWT de la
     *  sesión y la cierra. */
    private suspend fun terminar(r: Respuesta, cliente: String): String {
        var resp = r
        var c = cliente
        var sesion = resp.response?.created_session_id ?: ""
        if (sesion.isEmpty() && resp.response?.first_factor_verification?.status == "transferable") {
            val (r2, c2) = pedir("/client/sign_ups", "POST", mapOf("transfer" to "true"), c)
            resp = r2; c = c2
            sesion = resp.response?.created_session_id ?: ""
        }
        if (!Regex("^sess_[A-Za-z0-9]+$").matches(sesion)) throw ErrorClerk("cancelado")
        val (t, clienteT) = pedir("/client/sessions/$sesion/tokens", "POST", cliente = c)
        val jwt = t.jwt?.takeIf { it.isNotEmpty() } ?: throw ErrorClerk("clerk_no_responde")
        runCatching { pedir("/client/sessions/$sesion/remove", "POST", cliente = clienteT) }
        return jwt
    }

    /** Google, paso 1: crea el intento y devuelve la URL a abrir en la Custom Tab. */
    suspend fun iniciar(): String {
        val (r, c) = pedir("/client/sign_ins", "POST", mapOf("strategy" to "oauth_google", "redirect_url" to VUELTA))
        val url = r.response?.first_factor_verification?.external_verification_redirect_url
        val i = r.response?.id
        if (url.isNullOrBlank() || i == null || !Regex("^sia_[A-Za-z0-9]+$").matches(i)) throw ErrorClerk("clerk_no_responde")
        cliente = c
        intento = i
        return url
    }

    /** Google, paso 2: con el nonce de la vuelta (`rotating_token_nonce`), el JWT de sesión de Clerk. */
    suspend fun completar(nonce: String?): String {
        if (intento.isEmpty()) throw ErrorClerk("cancelado")
        val q = if (!nonce.isNullOrEmpty()) listOf("rotating_token_nonce" to nonce) else emptyList()
        return try {
            val (r2, c2) = pedir("/client/sign_ins/$intento", cliente = cliente, query = q)
            terminar(r2, c2)
        } catch (e: ErrorClerk) {
            if (e.codigo != "external_account_not_found") throw e
            // Alguien nuevo vuelve sin nonce: va directo al alta.
            val (r3, c3) = pedir("/client/sign_ups", "POST", mapOf("transfer" to "true"), cliente)
            terminar(r3, c3)
        } finally {
            intento = ""
        }
    }

    companion object {
        const val VERSION_FAPI = "2025-11-10"
        const val ESQUEMA = "ar.com.corredor.app"
        const val VUELTA = "ar.com.corredor.app://callback"

        /** Clerk(publicable:) de iOS: null si la clave no sirve. */
        fun crear(publicable: String?, http: OkHttpClient = Api.cliente()): Clerk? = fapiDe(publicable)?.let { Clerk(it, http) }

        /** La Frontend API sale de la clave publicable: pk_<entorno>_<base64("host$")>. */
        fun fapiDe(publicable: String?): String? {
            val p = publicable ?: return null
            val partes = p.split("_", limit = 3)
            if (partes.size != 3 || partes[0] != "pk" || (partes[1] != "live" && partes[1] != "test")) return null
            var b = partes[2].replace('-', '+').replace('_', '/')
            while (b.length % 4 != 0) b += "="
            val host = runCatching { String(decodificarBase64(b)) }.getOrNull()?.removeSuffix("$") ?: return null
            return if (Regex("^[a-z0-9.-]+\\.[a-z]{2,}$", RegexOption.IGNORE_CASE).matches(host)) "https://$host" else null
        }

        /** Base64 de Android en la app y de Java en los tests (android.util.Base64 no anda sin el teléfono). */
        private fun decodificarBase64(b: String): ByteArray =
            runCatching { java.util.Base64.getDecoder().decode(b) }.getOrElse { Base64.decode(b, Base64.DEFAULT) }

        /** El cuerpo application/x-www-form-urlencoded, ordenado por clave (como el de iOS). */
        fun formulario(d: Map<String, String>): String =
            d.toSortedMap().entries.joinToString("&") { "${it.key}=${java.net.URLEncoder.encode(it.value, "UTF-8").replace("+", "%20").replace("*", "%2A").replace("%7E", "~")}" }

        /** Un pedido a la Frontend API: la respuesta y el token del cliente (Clerk lo rota: vale el último que mandó). */
        internal suspend fun pedir(http: OkHttpClient, fapi: String, ruta: String, metodo: String = "GET", cuerpo: Map<String, String>? = null,
                                   cliente: String? = null, query: Consulta = emptyList()): Pair<Respuesta, String> = withContext(Dispatchers.IO) {
            val url = "$fapi/v1$ruta".toHttpUrl().newBuilder().apply {
                query.forEach { (k, v) -> addQueryParameter(k, v) }
                addQueryParameter("_is_native", "1")
                addQueryParameter("__clerk_api_version", VERSION_FAPI)
            }.build()
            val b = Request.Builder().url(url)
            if (!cliente.isNullOrEmpty()) b.header("Authorization", cliente)
            val body = when {
                cuerpo != null -> FormBody.Builder().apply { cuerpo.toSortedMap().forEach { (k, v) -> add(k, v) } }.build()
                metodo == "POST" || metodo == "PATCH" -> FormBody.Builder().build()
                else -> null
            }
            b.method(metodo, body)
            val res = try { http.newCall(b.build()).execute() } catch (e: IOException) { throw ErrorClerk("sin_conexion") }
            res.use {
                val texto = it.body?.string().orEmpty()
                val r = runCatching { Api.json.decodeFromString<Respuesta>(texto) }.getOrElse { Respuesta() }
                if (!it.isSuccessful) throw ErrorClerk(r.errors?.firstOrNull()?.code ?: "clerk_no_responde")
                val nuevo = it.header("authorization").orEmpty()
                r to (nuevo.ifEmpty { cliente.orEmpty() })
            }
        }
    }
}
