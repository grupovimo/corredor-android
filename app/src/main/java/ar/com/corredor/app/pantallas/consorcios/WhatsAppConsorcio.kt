package ar.com.corredor.app.pantallas.consorcios

import android.net.Uri
import ar.com.corredor.app.nucleo.Api
import ar.com.corredor.app.nucleo.ErrorApi
import ar.com.corredor.app.nucleo.Formato
import ar.com.corredor.app.nucleo.opcional
import ar.com.corredor.app.tema.plata
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable

// Clon de Pantallas/Consorcios/WhatsAppConsorcio.swift.

/** Mandar un WhatsApp desde el número de la inmobiliaria, como `mandarWhatsApp` de la web (lib/server/contratos.ts): con
 *  el número conectado abre (o retoma) la conversación y manda el texto; fuera de la ventana de 24 h, la plantilla
 *  aprobada que sirva, con los datos en orden. Sin el número conectado o sin plantilla, devuelve el wa.me para abrir
 *  desde el teléfono. */
object ConsorcioWhatsApp {
    sealed class Envio {
        data object porCorredor : Envio()
        data class waMe(val url: String) : Envio()
    }

    @Serializable data class Numero(val conectado: Boolean? = null)
    @Serializable data class Conversacion(val id: String, val enVentana: Boolean? = null)
    @Serializable data class Plantilla(val nombre: String, val idioma: String, val categoria: String? = null, val parametros: Int? = null)
    @Serializable data class Contacto(val nombre: String? = null, val telefono: String? = null)

    @Serializable private data class AbrirC(val contactoId: String)
    @Serializable private data class M(val texto: String)
    @Serializable private data class DP(val nombre: String, val idioma: String, val parametros: List<String>)
    @Serializable private data class E(val contactoId: String, val plantilla: DP)

    /** Para qué es: elige la plantilla fuera de 24 h (los mismos patrones que la web). */
    val patrones: Map<String, String> = mapOf(
        "recordatorio" to "recordatorio_(pago|cobro|alquiler)|cobr|pago|vencim|alquiler",
        "expensas" to "expensa|liquidaci|consorci|recib",
    )

    suspend fun conectado(api: Api): Boolean = try {
        val n: Numero = api.get("/v1/whatsapp/numero")
        n.conectado == true
    } catch (e: CancellationException) { throw e } catch (e: Exception) { false }

    fun waMe(telefono: String?, texto: String): String {
        val n = (telefono ?: "").filter { it.isDigit() }
        return Uri.parse("https://wa.me/$n").buildUpon().apply { if (texto.isNotEmpty()) appendQueryParameter("text", texto) }.build().toString()
    }

    /** Manda el texto a un contacto. `conectado`: si ya se sabe (en un envío masivo se pregunta una vez). */
    suspend fun mandar(api: Api, contactoId: String, texto: String, para: String, parametros: List<String>, conectado: Boolean? = null): Envio {
        val c: Contacto = api.get("/v1/contactos/$contactoId")
        val tel = c.telefono.noVacio()
            ?: throw ErrorApi(422, "sin_telefono", "Sin teléfono", "${c.nombre ?: "El contacto"} no tiene teléfono cargado.")
        val hay = conectado ?: conectado(api)
        if (!hay) return Envio.waMe(waMe(tel, texto))
        val conv: Conversacion = api.enviar("POST", "/v1/whatsapp/conversaciones", AbrirC(contactoId))
        if (conv.enVentana == true) {
            api.hacer("POST", "/v1/whatsapp/conversaciones/${conv.id}/mensajes", M(texto))
            return Envio.porCorredor
        }
        val plantillas: List<Plantilla> = api.opcional<List<Plantilla>>("/v1/whatsapp/plantillas") ?: emptyList()
        val re = Regex(patrones[para] ?: patrones["recordatorio"]!!)
        val sirve: (Plantilla) -> Boolean = { re.containsMatchIn(it.nombre) }
        val p = plantillas.firstOrNull { (it.categoria ?: "").uppercase() == "UTILITY" && sirve(it) } ?: plantillas.firstOrNull(sirve)
            ?: return Envio.waMe(waMe(tel, texto))
        val n = p.parametros ?: 0
        val ps = (0 until n).map { if (it < parametros.size) parametros[it] else "" }
        api.hacer("POST", "/v1/whatsapp/plantillas/enviar", E(contactoId, DP(p.nombre, p.idioma, ps)))
        return Envio.porCorredor
    }

    /** Lo que salió por WhatsApp de un período. */
    data class EnvioPeriodo(var enviadas: Int = 0, val sinTelefono: MutableList<String> = mutableListOf(), var sinWhatsApp: Boolean = false, val fallidas: MutableList<String> = mutableListOf())

    /** Manda por WhatsApp la liquidación del período a cada propietario con teléfono, con un link nuevo al recibo (como
     *  `liquidacionesPorWhatsApp` de la web). Sin el número conectado no sale nada. */
    suspend fun liquidaciones(api: Api, consorcio: String, periodo: String): EnvioPeriodo {
        val ficha: PeriodoAPI = api.get("/v1/consorcios/$consorcio/periodos/$periodo")
        val out = EnvioPeriodo()
        val hay = conectado(api)
        for (l in ficha.liquidaciones ?: emptyList()) {
            val pid = l.propietarioId
            if (pid == null || l.telefono.noVacio() == null) {
                out.sinTelefono.add(l.unidad)
                continue
            }
            if (!hay) { out.sinWhatsApp = true; continue }
            try {
                val link: LinkConsorcio = api.enviar("POST", "/v1/consorcios/$consorcio/liquidaciones/${l.id}/link", vacioConsorcio)
                val mes = Cons.nombreMes(l.periodo)
                val total = Formato.plata(Math.rint(l.total).let { if (l.total - Math.floor(l.total) == 0.5) Math.ceil(l.total) else it })
                val nombre = (l.propietario ?: "").split(" ").firstOrNull { it.isNotEmpty() } ?: "Hola"
                val texto = "Hola $nombre, ya está la liquidación de expensas de $mes de la unidad ${l.unidad}: $total. El detalle, los comprobantes y cómo pagar: ${link.url}"
                val r = mandar(api, contactoId = pid, texto = texto, para = "expensas", parametros = listOf(nombre, mes, l.unidad, total, link.url), conectado = true)
                if (r is Envio.porCorredor) out.enviadas += 1 else out.fallidas.add(l.unidad)
            } catch (e: CancellationException) { throw e } catch (e: Exception) {
                out.fallidas.add(l.unidad)
            }
        }
        return out
    }
}
