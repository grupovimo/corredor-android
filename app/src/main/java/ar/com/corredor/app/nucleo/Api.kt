package ar.com.corredor.app.nucleo

import android.content.Context
import ar.com.corredor.app.BuildConfig
import ar.com.corredor.app.app.ModoPrueba
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.net.URLEncoder
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Un error de la API (application/problem+json) o de la red, con lo que hay que decirle a la persona. */
data class ErrorApi(
    val estado: Int,
    val codigo: String,
    val titulo: String,
    val detalle: String,
    /** Ingreso con contraseña: intentos que quedan antes de pausarlo. */
    val restantes: Int? = null,
    /** Segundos para volver a probar (429). */
    val reintentarEn: Int? = null,
    /** Los errores por campo del problem+json (`issues: [{path, message}]`): «direccion» → «Poné la dirección.». */
    val campos: Map<String, String> = emptyMap(),
    /** En un 402 del plan (tope_plan, plan_insuficiente): el plan que lo habilita ("" si no hay uno) y el recurso con
     *  tope. «Ver planes» abre Tu plan con ése elegido. */
    val plan: String = "",
    val recurso: String = "",
) : Exception(detalle) {
    val sinConexion: Boolean get() = codigo == "sin_conexion"
    /** Lo frena el plan (402): se arregla eligiendo un plan, no reintentando. */
    val delPlan: Boolean get() = estado == 402
    val sinSesion: Boolean get() = estado == 401 && codigo == "sin_sesion"

    companion object {
        val sinRed = ErrorApi(0, "sin_conexion", "Sin conexión", "No hay internet. Revisá la conexión y probá de nuevo.")
        val desconocido = ErrorApi(0, "interno", "Algo salió mal", "Probá de nuevo en un rato.")
    }
}

/** El ErrorApi de cualquier error (lo que en iOS es `catch let e as ErrorApi` y si no `.desconocido`). */
fun errorDe(e: Throwable): ErrorApi = e as? ErrorApi ?: ErrorApi.desconocido

private suspend fun Call.esperar(): Response = suspendCancellableCoroutine { cont ->
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) = cont.resumeWithException(e)
        override fun onResponse(call: Call, response: Response) = cont.resume(response)
    })
    cont.invokeOnCancellation { runCatching { cancel() } }
}

/** Una URLQueryItem de iOS: `"limite" to "200"`. */
typealias Consulta = List<Pair<String, String>>

/** El cliente de la API de Corredor (crm-inmobiliaria, rama corredor, api/), clon de Nucleo/Api.swift. Va directo
 *  contra api.corredor.com.ar con la sesión propia en `Authorization: Bearer`. */
class Api(val base: String, @Volatile var token: String? = null, val http: OkHttpClient = cliente()) {
    /** Se llama cuando la API dice que la sesión ya no vale (401 sin_sesion): la app vuelve al ingreso. */
    var alPerderSesion: (suspend () -> Unit)? = null
    /** Se llama cuando la API dice que no hay inmobiliaria elegida (409 sin_inmobiliaria: se fue del equipo o la
     *  borraron): la app recarga la sesión y lleva a elegir otra. */
    var alPerderInmobiliaria: (suspend () -> Unit)? = null

    @Serializable
    private data class Problema(
        val status: Int? = null, val code: String? = null, val title: String? = null, val detail: String? = null,
        val restantes: Int? = null, val issues: List<ProblemaApi.Campo>? = null, val plan: String? = null, val recurso: String? = null,
    )

    /** La URL de `ruta`. La ruta puede traer su propia query (`/v1/x?y=z`, como las acciones de los avisos). */
    fun url(ruta: String, query: Consulta = emptyList()): String =
        (base.trimEnd('/') + ruta).toHttpUrl().newBuilder().apply { query.forEach { (k, v) -> addQueryParameter(k, v) } }.build().toString()

    /** El pedido crudo: la respuesta tal cual (Data en iOS). `envio`: la Idempotency-Key (la misma clave no aplica dos
     *  veces la escritura). */
    suspend fun pedir(metodo: String, ruta: String, query: Consulta = emptyList(), cuerpo: ByteArray? = null, envio: String? = null): ByteArray {
        val r = Request.Builder().url(url(ruta, query)).header("Accept", "application/json")
        token?.let { r.header("Authorization", "Bearer $it") }
        envio?.let { r.header("Idempotency-Key", it) }
        val cuerpoHttp = cuerpo?.toRequestBody("application/json".toMediaType())
            ?: if (metodo in listOf("POST", "PUT", "PATCH")) ByteArray(0).toRequestBody(null) else null
        r.method(metodo, cuerpoHttp)
        val (codigo, datos, reintentar) = try {
            withContext(Dispatchers.IO) {
                http.newCall(r.build()).apply { timeout().timeout(espera(ruta), TimeUnit.SECONDS) }.esperar().use { Triple(it.code, it.body?.bytes() ?: ByteArray(0), it.header("Retry-After")) }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            throw ErrorApi.sinRed
        }
        if (codigo in 200..299) return datos
        val p = runCatching { json.decodeFromString<Problema>(String(datos)) }.getOrElse { Problema() }
        val campos = (p.issues ?: emptyList()).mapNotNull { c -> c.path?.let { it to (c.message ?: "Revisá este dato.") } }
            .groupBy({ it.first }, { it.second }).mapValues { it.value.first() }
        val e = ErrorApi(codigo, p.code ?: "interno", p.title ?: "Algo salió mal", p.detail ?: "Probá de nuevo en un rato.", p.restantes,
            reintentar?.toIntOrNull(), campos, p.plan ?: "", p.recurso ?: "")
        reaccionar(e.estado, e.codigo)
        throw e
    }

    /** Lo que la app hace sola con ciertos errores, en cualquier pedido: 401 sin_sesion → al ingreso; 409
     *  sin_inmobiliaria → a elegir la inmobiliaria. */
    internal suspend fun reaccionar(estado: Int, codigo: String) {
        if (token == null) return
        if (estado == 401 && codigo == "sin_sesion") alPerderSesion?.invoke()
        if (estado == 409 && codigo == "sin_inmobiliaria") alPerderInmobiliaria?.invoke()
    }

    suspend inline fun <reified T> get(ruta: String, query: Consulta = emptyList()): T = decodificar(pedir("GET", ruta, query))

    /** Una lista entera, de a 200 (el tope de la API) con `desde`. Corta cuando una página viene corta o no trae nada
     *  nuevo (si el endpoint no pagina, no repite). `id`: lo que identifica a cada uno (el Identifiable de iOS). */
    suspend inline fun <reified T> todas(ruta: String, query: Consulta = emptyList(), crossinline id: (T) -> Any? = { it }): List<T> {
        val todos = mutableListOf<T>()
        val vistos = HashSet<Any?>()
        for (vuelta in 0 until 50) {
            val pagina: List<T> = get(ruta, query + listOf("limite" to "200", "desde" to todos.size.toString()))
            val nuevos = pagina.filter { vistos.add(id(it)) }
            todos += nuevos
            if (pagina.size < 200 || nuevos.isEmpty()) break
        }
        return todos
    }

    /** `envio`: la Idempotency-Key (la misma que se usa si después va a la cola sin conexión). */
    suspend inline fun <reified T, reified B> enviar(metodo: String, ruta: String, cuerpo: B, envio: String? = null): T =
        decodificar(pedir(metodo, ruta, cuerpo = json.encodeToString(cuerpo).toByteArray(), envio = envio))

    /** Un pedido sin respuesta (204). */
    suspend inline fun <reified B> hacer(metodo: String, ruta: String, cuerpo: B) {
        pedir(metodo, ruta, cuerpo = json.encodeToString(cuerpo).toByteArray())
    }

    suspend fun hacer(metodo: String, ruta: String) { pedir(metodo, ruta) }

    inline fun <reified T> decodificar(d: ByteArray): T = try {
        json.decodeFromString<T>(String(d))
    } catch (e: Exception) {
        if (BuildConfig.DEBUG) android.util.Log.w("Api", "no pude leer ${T::class.simpleName}: $e")
        throw ErrorApi.desconocido
    }

    companion object {
        /** La API de la configuración (BuildConfig.API, de cr.api en local.properties). En las pruebas se pisa con
         *  ModoPrueba.api. */
        fun urlConfigurada(): String = ModoPrueba.api ?: BuildConfig.API.ifEmpty { "https://api.corredor.com.ar" }

        /** La web de Corredor (para los links públicos: fichas, recibos, la web de la inmobiliaria). */
        fun web(): String = BuildConfig.WEB.ifEmpty { "https://corredor.com.ar" }

        val json = Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true; encodeDefaults = true }

        /** Api.codificador y Api.decodificador de iOS (el mismo Json). */
        val codificador get() = json
        val decodificador get() = json

        fun cliente(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).writeTimeout(60, TimeUnit.SECONDS)
            .build()

        /** Las rutas que llaman a la IA (Api.rutasLentas de iOS): el asistente puede dar varias vueltas con herramientas y
         *  el servidor le da hasta 50 s; cortar a los 20 dejaba un «sin red» con lo gastado igual. */
        val rutasLentas = setOf("/v1/asistente", "/v1/mira/extraer", "/v1/web/reescribir")

        /** Cuánto se espera cada pedido entero (Api.espera de iOS): 60 s los de la IA, 20 los demás. */
        fun espera(ruta: String): Long = if (ruta.substringBefore('?') in rutasLentas) 60 else 20
    }
}

/** Un GET que puede fallar sin cortar todo (try? await api.get en iOS). */
suspend inline fun <reified T> Api.opcional(ruta: String, query: Consulta = emptyList()): T? =
    try { get<T>(ruta, query) } catch (e: CancellationException) { throw e } catch (e: Exception) { null }

/** Un pedido que puede fallar sin cortar todo (try? await api.hacer en iOS): true si salió. */
suspend inline fun intentar(bloque: () -> Unit): Boolean =
    try { bloque(); true } catch (e: CancellationException) { throw e } catch (e: Exception) { false }

/** Un cuerpo vacío (`{}`). */
@Serializable
class Vacio

/** Lo último que se trajo de cada lista, para verlo sin conexión (handoff 5a: «Ves lo guardado hoy, 14:20»). Un
 *  archivo por clave en la caché, con la fecha en que se guardó. */
object Guardado {
    internal var contexto: Context? = null
    private val carpeta: File? get() = contexto?.let { File(it.cacheDir, "guardado").apply { mkdirs() } }
    private fun archivo(clave: String) = carpeta?.let { File(it, URLEncoder.encode(clave, "UTF-8")) }

    inline fun <reified T> guardar(valor: T, clave: String) {
        val texto = runCatching { Api.json.encodeToString(valor) }.getOrNull() ?: return
        escribir(clave, texto)
    }

    fun escribir(clave: String, texto: String) { runCatching { archivo(clave)?.writeText(texto) } }

    inline fun <reified T> leer(clave: String): Pair<T, Instant>? {
        val (texto, fecha) = crudo(clave) ?: return null
        return runCatching { Api.json.decodeFromString<T>(texto) to fecha }.getOrNull()
    }

    fun crudo(clave: String): Pair<String, Instant>? {
        val f = archivo(clave)?.takeIf { it.exists() } ?: return null
        return runCatching { f.readText() to Instant.ofEpochMilli(f.lastModified()) }.getOrNull()
    }

    /** Al cerrar sesión o cambiar de inmobiliaria no queda nada de la anterior. */
    fun borrarTodo() { carpeta?.deleteRecursively() }
}

/** Un archivo subido (POST /v1/archivos?uso=foto|documento|audio|adjunto|marca). */
@Serializable
data class Archivo(val id: String, val nombre: String? = null, val tipo: String? = null, val bytes: Int? = null, val url: String? = null)

/** Sube un archivo (multipart, campo `archivo`). */
suspend fun Api.subir(datos: ByteArray, nombre: String, tipo: String, uso: String): Archivo {
    // Los topes de la API por uso (dominio/archivos): se cortan acá antes de subir de balde.
    val tope = mapOf("foto" to 15, "cartel" to 15, "marca" to 5)[uso] ?: 25
    if (datos.size > (tope shl 20)) throw ErrorApi(422, "muy_grande", "Archivo muy grande", "El archivo pasa de $tope MB.")
    return decodificar(multipart("/v1/archivos", listOf("uso" to uso), datos, nombre, tipo))
}

@Serializable
private data class ProblemaArchivo(val detail: String? = null, val title: String? = null, val code: String? = null)

/** Un POST multipart (campo `archivo`) a cualquier ruta: la respuesta cruda. */
suspend fun Api.multipart(ruta: String, query: Consulta, datos: ByteArray, nombre: String, tipo: String): ByteArray {
    val cuerpo = MultipartBody.Builder("corredor-${UUID.randomUUID()}").setType(MultipartBody.FORM)
        .addFormDataPart("archivo", nombre, datos.toRequestBody(tipo.toMediaType())).build()
    val r = Request.Builder().url(url(ruta, query)).post(cuerpo).header("Accept", "application/json")
    token?.let { r.header("Authorization", "Bearer $it") }
    val lento = http.newBuilder().readTimeout(120, TimeUnit.SECONDS).writeTimeout(120, TimeUnit.SECONDS).build()
    val (codigo, d) = try {
        withContext(Dispatchers.IO) { lento.newCall(r.build()).esperar().use { it.code to (it.body?.bytes() ?: ByteArray(0)) } }
    } catch (e: CancellationException) {
        throw e
    } catch (e: IOException) {
        throw ErrorApi.sinRed
    }
    if (codigo !in 200..299) {
        val p = runCatching { Api.json.decodeFromString<ProblemaArchivo>(String(d)) }.getOrNull()
        // La sesión vencida (o sin inmobiliaria) también se nota al subir un archivo, como en los demás pedidos.
        reaccionar(codigo, p?.code ?: "")
        throw ErrorApi(codigo, "archivo", p?.title ?: "No se subió", p?.detail ?: "No se pudo subir el archivo.")
    }
    return d
}
