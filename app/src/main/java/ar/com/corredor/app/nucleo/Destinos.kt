package ar.com.corredor.app.nucleo

import ar.com.corredor.app.pantallas.cobranzas.AAltaContrato
import ar.com.corredor.app.pantallas.cobranzas.AContratosFiltro
import ar.com.corredor.app.pantallas.cobranzas.ADevolucion
import ar.com.corredor.app.pantallas.cobranzas.ARecordarMes
import ar.com.corredor.app.pantallas.cobranzas.ARecorrida
import ar.com.corredor.app.pantallas.cobranzas.ARegistrarCobro
import ar.com.corredor.app.pantallas.cobranzas.ARendicionVista
import ar.com.corredor.app.pantallas.consorcios.AFichaConsorcioEn
import ar.com.corredor.app.pantallas.consorcios.ALiquidarConsorcio
import ar.com.corredor.app.pantallas.consorcios.ATrabajoConsorcio
import ar.com.corredor.app.pantallas.mira.ARevisionIds
import ar.com.corredor.app.pantallas.propiedades.AArreglarPropiedad
import ar.com.corredor.app.pantallas.propiedades.AEditarEnPaso
import ar.com.corredor.app.pantallas.propiedades.AMandarPropiedad
import ar.com.corredor.app.pantallas.propiedades.AMercadoLibre
import ar.com.corredor.app.pantallas.propiedades.AReserva
import ar.com.corredor.app.pantallas.propiedades.AReservaTemporada
import kotlinx.serialization.Serializable
import java.net.URLDecoder

// Clon de Nucleo/Destinos.swift: los destinos de navegación de toda la app. Viven acá (y no en cada área) para que
// cualquier pantalla pueda abrir la ficha de otra: el Inicio abre una propiedad, un chat abre el contacto. Se abren con
// `estado.abrir(destino)` y app/Registro.kt dice qué pantalla muestra cada uno.

/** 3D Ficha de una propiedad. */
@Serializable data class AFichaPropiedad(val id: String)
/** 3E Galería de fotos de una propiedad, desde la foto `indice`. */
@Serializable data class AGaleria(val id: String, val indice: Int = 0)
/** 4A Alta rápida de una propiedad. `editar`: el id de una propiedad; `borrador`: el id de uno de /v1/borradores para
 *  seguirlo (el de la web, el de otro aparato o el que dejó MIRA). */
@Serializable data class AAltaPropiedad(val editar: String? = null, val borrador: String? = null)
/** 5I Lo captado con MIRA (la cola y los borradores). */
@Serializable class ACaptadas { override fun equals(other: Any?) = other is ACaptadas; override fun hashCode() = 11 }
/** 6B Un chat de WhatsApp. */
@Serializable data class AChat(val id: String)
/** 6D Una pregunta de Mercado Libre. */
@Serializable data class APreguntaML(val id: String)
/** 7A Agenda en un día (yyyy-MM-dd); null es hoy. */
@Serializable data class AAgendaDia(val dia: String? = null)
/** Una cita de la agenda. */
@Serializable data class ACita(val id: String)
/** 8A Los contactos; `agente: "libre"` muestra sólo las consultas sin dueño (`/contactos?agente=libre`). */
@Serializable data class AContactos(val agente: String? = null)
/** 8B Ficha de un contacto. */
@Serializable data class AFichaContacto(val id: String)
/** 9A Tablero de operaciones. */
@Serializable class AOperaciones { override fun equals(other: Any?) = other is AOperaciones; override fun hashCode() = 12 }
/** 9C Ficha de una operación. */
@Serializable data class AFichaOperacion(val id: String)
/** 10A Por cobrar. */
@Serializable class ACobranzas { override fun equals(other: Any?) = other is ACobranzas; override fun hashCode() = 13 }
/** Los contratos (la lista) y la ficha de uno. */
@Serializable class AContratos { override fun equals(other: Any?) = other is AContratos; override fun hashCode() = 14 }
@Serializable data class AFichaContrato(val id: String)
/** 10D Recibo de un cobro. */
@Serializable data class ARecibo(val contrato: String, val cobro: String)
/** Rendición al propietario. */
@Serializable data class ARendicion(val contrato: String, val id: String)
/** 11A Recorrida de inventario y 11B firmas. */
@Serializable data class AInventario(val id: String)
@Serializable data class AFirmas(val id: String)
/** 12A Avisos y 12B su configuración. */
@Serializable data class AAvisos(val aviso: String? = null, val responder: String? = null)
@Serializable class AConfigAvisos { override fun equals(other: Any?) = other is AConfigAvisos; override fun hashCode() = 15 }
/** 13A Perfil. */
@Serializable class APerfil { override fun equals(other: Any?) = other is APerfil; override fun hashCode() = 16 }
/** 2C Búsqueda global. */
@Serializable data class ABuscar(val texto: String = "")
/** Lo que el handoff no dibuja y el panel sí tiene (configuración, equipo, consorcios, Mi web, informe,
 *  emprendimientos, temporada…): una sección por `clave`, con un `id` opcional. */
@Serializable data class ASeccion(val clave: String, val id: String? = null)

/** Las cinco pestañas. La quinta es «Más» (todo lo demás, por áreas); la agenda se abre desde el Inicio y desde «Más»
 *  (decisión del 5/11: con todo el panel en la app, no entraba en el handoff). */
enum class Pestana { inicio, propiedades, mira, mensajes, mas }

/** Adónde lleva un push, un widget, un atajo o un link `corredor://`: la pestaña y lo que se apila encima. */
data class Ruta(val pestana: Pestana, val destino: Any? = null) {
    companion object {
        /** `propiedad/123` (el `ruta` del push) o `corredor://propiedad/123`. También acepta rutas del panel web
         *  (`/propiedades/123`, `/mensajes/abc`), que son las que manda la API en los avisos.
         *  `/propiedades/nueva?borrador=<id>` sigue ese borrador. */
        fun de(texto: String): Ruta? {
            val sinAncla = texto.substringBefore('#')
            val consulta = sinAncla.substringAfter('?', "")
            val items = consulta.split('&').filter { it.isNotEmpty() }.map { par ->
                val k = par.substringBefore('=')
                val v = par.substringAfter('=', "")
                decodificar(k) to decodificar(v)
            }
            fun param(n: String): String? = items.firstOrNull { it.first == n }?.second?.nonEmpty
            val borrador = param("borrador")
            var t = sinAncla.substringBefore('?')
            if (t.startsWith("corredor://")) {
                t = t.removePrefix("corredor://")
            } else if (t.startsWith("https://")) {
                val resto = t.removePrefix("https://")
                val host = resto.substringBefore('/')
                if (host != "corredor.com.ar" && host != "www.corredor.com.ar") return null
                t = if (resto.contains('/')) "/" + resto.substringAfter('/') else ""
                // Un link del panel: lo que es sólo de la web (o `?web=1`) se abre en la web dentro de la app (Vinculos).
                if (Vinculos.soloWeb(t) || items.any { it.first == "web" }) return Vinculos.web(texto)
            }
            val p = t.split('/').filter { it.isNotEmpty() }
            val a = p.firstOrNull() ?: return null
            val id = p.getOrNull(1)
            return when (a) {
                "inicio" -> Ruta(Pestana.inicio)
                "propiedad", "propiedades" -> when {
                    id == "nueva" -> Ruta(Pestana.propiedades, AAltaPropiedad(borrador = borrador))
                    // `?arreglar=<portal>`: la ficha con la hoja de «Arreglar» abierta.
                    id != null && param("arreglar") != null -> Ruta(Pestana.propiedades, AArreglarPropiedad(id = id))
                    // `/propiedades/<id>/editar?paso=fotos`: el alta en ese paso.
                    id != null && p.size > 2 && p[2] == "editar" && param("paso") != null -> Ruta(Pestana.propiedades, AEditarEnPaso(id = id, paso = param("paso")!!))
                    // `?compartir=whatsapp&contacto=<id>`: la ficha con la hoja de mandar abierta.
                    id != null && param("compartir") == "whatsapp" -> Ruta(Pestana.propiedades, AMandarPropiedad(id = id, contacto = param("contacto")))
                    else -> Ruta(Pestana.propiedades, id?.let { AFichaPropiedad(it) })
                }
                "mira" -> Ruta(Pestana.mira, if (id == "captadas") ACaptadas() else null)
                "chat", "mensajes", "whatsapp" -> Ruta(Pestana.mensajes, id?.let { AChat(it) })
                // `/mercadolibre/preguntas?pregunta=<id>` (los avisos) o `ml/<id>` (el push).
                "ml", "mercadolibre" -> if (id == "preguntas" || id == null) {
                    Ruta(Pestana.mensajes, param("pregunta")?.let { APreguntaML(it) } ?: AMercadoLibre())
                } else {
                    Ruta(Pestana.mensajes, APreguntaML(id))
                }
                // `/agenda/<id>` o `/agenda?cita=<id>` (los avisos): esa cita; si no, el día.
                "agenda" -> Ruta(Pestana.inicio, (id ?: param("cita"))?.let { ACita(it) } ?: AAgendaDia())
                "mas" -> Ruta(Pestana.mas)
                "contacto", "contactos" -> Ruta(Pestana.inicio, id?.let { AFichaContacto(it) } ?: AContactos(agente = if (param("agente") == "libre") "libre" else null))
                "operacion", "operaciones" -> Ruta(Pestana.inicio, id?.let { AFichaOperacion(it) } ?: AOperaciones())
                // `/cobranzas?registrar=1&contrato=<id>`: la hoja de «Registrar cobro» abierta.
                "cobranzas" -> if (param("registrar") == "1") Ruta(Pestana.inicio, ARegistrarCobro(contrato = param("contrato"))) else Ruta(Pestana.inicio, ACobranzas())
                "contrato", "contratos" -> when {
                    id == "nuevo" -> Ruta(Pestana.inicio, AAltaContrato(borrador = borrador, renovar = param("renovar")))
                    // `/contratos/<id>?recordar=AAAA-MM`: el recordatorio de ese mes.
                    id != null && param("recordar") != null -> Ruta(Pestana.inicio, ARecordarMes(contrato = id, periodo = param("recordar")!!))
                    id != null -> Ruta(Pestana.inicio, AFichaContrato(id))
                    param("vista") != null || param("indice") != null -> Ruta(Pestana.inicio, AContratosFiltro(vista = param("vista"), indice = param("indice")))
                    else -> Ruta(Pestana.inicio, AContratos())
                }
                // `/rendiciones/<id>?contrato=<c>` (y `&vista=1`, como la ve el propietario).
                "rendicion", "rendiciones" -> {
                    val c = param("contrato")
                    if (id != null && c != null) {
                        Ruta(Pestana.inicio, if (param("vista") == "1") ARendicionVista(contrato = c, id = id) else ARendicion(contrato = c, id = id))
                    } else Ruta(Pestana.mas, ASeccion("rendiciones"))
                }
                // `/inventarios/<prop>/actas/<acta>` (las firmas), `/inventarios/<prop>/devolucion`,
                // `/inventarios/<prop>?tipo=entrega|devolucion` (la recorrida en ese modo) o la recorrida.
                "inventario", "inventarios" -> when {
                    id != null && p.size > 3 && p[2] == "actas" -> Ruta(Pestana.inicio, AFirmas("$id/${p[3]}"))
                    id != null && p.size > 2 && p[2] == "devolucion" -> Ruta(Pestana.inicio, ADevolucion(id = id))
                    id != null && param("tipo") != null -> Ruta(Pestana.inicio, ARecorrida(id = id, tipo = param("tipo")!!))
                    id != null -> Ruta(Pestana.inicio, AInventario(id))
                    else -> Ruta(Pestana.mas, ASeccion("inventarios"))
                }
                // `/avisos/configurar` (el mail del informe): qué avisos llegan. `/avisos?aviso=<id>&responder=<accion>`:
                // ese aviso (y su respuesta) en vez de la lista.
                "avisos" -> if (id == "configurar") Ruta(Pestana.inicio, AConfigAvisos())
                    else Ruta(Pestana.inicio, AAvisos(aviso = param("aviso"), responder = param("responder")))
                "perfil" -> Ruta(Pestana.inicio, APerfil())
                "buscar" -> Ruta(Pestana.inicio, ABuscar(param("q") ?: ""))
                "reserva", "reservas" -> Ruta(Pestana.mas, id?.let { AReserva(id = it) } ?: ASeccion("reservas"))
                "temporada" -> if (id == "reservas" && p.size > 2) Ruta(Pestana.mas, AReservaTemporada(id = p[2])) else Ruta(Pestana.mas, ASeccion("temporada"))
                // Configuración: cada sección por su nombre (`/configuracion/equipo`, `/configuracion/ia`…).
                "configuracion" -> {
                    val sub = id?.takeIf { it in listOf("equipo", "integraciones", "ia", "proveedores", "respuestas") }
                    Ruta(Pestana.mas, ASeccion(sub ?: "configuracion"))
                }
                // `/invitacion/<token>`: la invitación para ver antes de aceptar.
                "invitacion" -> Ruta(Pestana.mas, ASeccion(if (id == null) "invitaciones" else "invitacion", id))
                // `/conectar-ia/autorizar?solicitud=<id>`: el permiso que pide el asistente.
                "conectar-ia" -> param("solicitud")?.let { Ruta(Pestana.mas, ASeccion("autorizar-ia", it)) } ?: Ruta(Pestana.mas, ASeccion("ia"))
                // Rutas del panel con id (los avisos traen las de la web): la ficha, no la lista.
                // `/consorcios/<id>/liquidar`, `/consorcios/<id>/trabajos/<t>` y `/consorcios/<id>/<pestaña>`.
                "consorcios", "consorcio" -> if (id != null && p.size > 2) {
                    when {
                        p[2] == "liquidar" -> Ruta(Pestana.mas, ALiquidarConsorcio(consorcio = id, periodo = param("periodo")))
                        p[2] == "trabajos" && p.size > 3 -> Ruta(Pestana.mas, ATrabajoConsorcio(consorcio = id, id = p[3]))
                        else -> {
                            val pedido: String? = when {
                                param("vista") == "deudores" -> "deben"
                                param("unidad") != null -> "unidad:${param("unidad")}"
                                param("nueva") == "1" || param("nuevo") == "1" -> "nueva"
                                param("recibo") != null -> "recibo:${param("recibo")}"
                                param("liquidar") == "1" -> "liquidar"
                                else -> null
                            }
                            Ruta(Pestana.mas, AFichaConsorcioEn(id = id, pestana = p[2], pedido = pedido))
                        }
                    }
                } else {
                    Ruta(Pestana.mas, ASeccion(if (id == null) "consorcios" else "consorcio", id))
                }
                // `/revision?ids=a,b`: sólo esos borradores.
                "revision" -> Ruta(Pestana.mas, param("ids")?.let { ARevisionIds(ids = it) } ?: ASeccion("revision"))
                // `/importar?id=<vista previa>`: retomarla.
                "importar" -> Ruta(Pestana.mas, ASeccion("importar", param("id")))
                "emprendimientos", "emprendimiento" -> Ruta(Pestana.mas, ASeccion(if (id == null) "emprendimientos" else "emprendimiento", id))
                // Otra sección de Más por su nombre; si la app no la tiene, la web dentro de la app.
                else -> if (a in Vinculos.secciones) Ruta(Pestana.mas, ASeccion(a, id)) else Vinculos.web(Vinculos.base + "/" + p.joinToString("/"))
            }
        }

        /** Como los queryItems de URLComponents: decodifica los %XX y deja el «+» como está (URLDecoder lo hace espacio). */
        private fun decodificar(s: String): String = runCatching { URLDecoder.decode(s.replace("+", "%2B"), "UTF-8") }.getOrDefault(s)
    }
}

/** Ruta(texto:) de iOS: null si el texto no lleva a ningún lado. */
fun Ruta(texto: String): Ruta? = Ruta.de(texto)
