package ar.com.corredor.app.pantallas.propiedades

import ar.com.corredor.app.nucleo.Api
import ar.com.corredor.app.nucleo.ErrorApi

// Clon de Pantallas/Propiedades/CamposApi.swift.

/** Un 400 de la API con los errores por campo (`issues: [{path, message}]` del problem+json): las hojas los muestran
 *  debajo de cada campo, como la web. */
class ErrorConCampos(
    val error: ErrorApi,
    /** Ruta → mensaje («direccion» → «Poné la dirección.»). */
    val campos: Map<String, String>,
) : Exception(error.detalle)

/** Como `enviar`/`hacer`, pero si la API rechaza los datos tira `ErrorConCampos` con cada campo. Los demás errores
 *  (sin red, 402, 409…) salen como `ErrorApi`, igual que siempre. */
suspend inline fun <reified C> Api.conCampos(metodo: String, ruta: String, cuerpo: C, envio: String? = null): ByteArray {
    try {
        return pedir(metodo, ruta, cuerpo = Api.json.encodeToString(cuerpo).toByteArray(), envio = envio)
    } catch (e: ErrorApi) {
        if (e.campos.isNotEmpty()) throw ErrorConCampos(e, e.campos)
        throw e
    }
}

/** `conCampos` que decodifica la respuesta (`conCampos(…, como: T.self)` en iOS). */
suspend inline fun <reified T, reified C> Api.conCamposComo(metodo: String, ruta: String, cuerpo: C, envio: String? = null): T {
    val d = conCampos(metodo, ruta, cuerpo, envio)
    return try { Api.json.decodeFromString<T>(String(d)) } catch (e: Exception) { throw ErrorApi.desconocido }
}
