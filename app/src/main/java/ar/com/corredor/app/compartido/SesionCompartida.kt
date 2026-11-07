package ar.com.corredor.app.compartido

import android.content.Context
import ar.com.corredor.app.nucleo.Api
import ar.com.corredor.app.nucleo.Preferencias

/** La sesión compartida entre la app, los widgets, la visita en curso y las acciones de los avisos: el token va en el
 *  Llavero y la URL de la API en las preferencias. En Android corren en el mismo proceso. */
object SesionCompartida {
    fun guardar(token: String?, api: String) {
        if (token != null) Llavero.guardar(token, "token")
        Preferencias.ponerTexto("api", api)
    }

    val token: String? get() = Llavero.leer("token")

    /** Una Api con la sesión compartida, fuera de la app abierta (null sin sesión). */
    fun api(ctx: Context): Api? {
        Llavero.contexto = Llavero.contexto ?: ctx.applicationContext
        Preferencias.contexto = Preferencias.contexto ?: ctx.applicationContext
        val t = Llavero.leer("token") ?: return null
        return Api(Preferencias.texto("api") ?: Api.urlConfigurada(), t)
    }
}
