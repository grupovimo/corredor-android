package ar.com.corredor.app.nucleo

import kotlinx.serialization.Serializable

/** El problem+json de la API cuando rechaza un pedido: el título, el detalle y el error de cada campo (`issues`, con el
 *  camino del campo y el mensaje), para mostrarlo debajo de él como la web. */
@Serializable
data class ProblemaApi(
    val code: String? = null,
    val title: String? = null,
    val detail: String? = null,
    val issues: List<Campo>? = null,
) {
    @Serializable
    data class Campo(val path: String? = null, val message: String? = null)
}
