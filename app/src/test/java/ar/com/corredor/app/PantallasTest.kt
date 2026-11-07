package ar.com.corredor.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import ar.com.corredor.app.app.Abrir
import ar.com.corredor.app.app.Barra
import ar.com.corredor.app.app.Bloqueo
import ar.com.corredor.app.app.Estado
import ar.com.corredor.app.app.LocalBarra
import ar.com.corredor.app.app.LocalEstado
import ar.com.corredor.app.app.ModoPrueba
import ar.com.corredor.app.app.Principal
import ar.com.corredor.app.app.Sesion
import ar.com.corredor.app.nucleo.AAgendaDia
import ar.com.corredor.app.nucleo.AAltaPropiedad
import ar.com.corredor.app.nucleo.AAvisos
import ar.com.corredor.app.nucleo.ABuscar
import ar.com.corredor.app.nucleo.ACaptadas
import ar.com.corredor.app.nucleo.AChat
import ar.com.corredor.app.nucleo.ACita
import ar.com.corredor.app.nucleo.ACobranzas
import ar.com.corredor.app.nucleo.AConfigAvisos
import ar.com.corredor.app.nucleo.AContactos
import ar.com.corredor.app.nucleo.AContratos
import ar.com.corredor.app.nucleo.AFichaContacto
import ar.com.corredor.app.nucleo.AFichaContrato
import ar.com.corredor.app.nucleo.AFichaOperacion
import ar.com.corredor.app.nucleo.AFichaPropiedad
import ar.com.corredor.app.nucleo.AFirmas
import ar.com.corredor.app.nucleo.AGaleria
import ar.com.corredor.app.nucleo.AInventario
import ar.com.corredor.app.nucleo.AOperaciones
import ar.com.corredor.app.nucleo.APerfil
import ar.com.corredor.app.nucleo.APreguntaML
import ar.com.corredor.app.nucleo.ARecibo
import ar.com.corredor.app.nucleo.ARendicion
import ar.com.corredor.app.nucleo.ASeccion
import ar.com.corredor.app.nucleo.Api
import ar.com.corredor.app.nucleo.AvisoCentro
import ar.com.corredor.app.nucleo.PaginaAvisos
import ar.com.corredor.app.nucleo.Pestana
import ar.com.corredor.app.nucleo.cargarEstructura
import ar.com.corredor.app.pantallas.agenda.AgendaRaiz
import ar.com.corredor.app.pantallas.agenda.AltaRapidaCita
import ar.com.corredor.app.pantallas.agenda.FichaCita
import ar.com.corredor.app.pantallas.agenda.LicenciasAgenda
import ar.com.corredor.app.pantallas.agenda.NuevaCita
import ar.com.corredor.app.pantallas.cartera.Contacto
import ar.com.corredor.app.pantallas.cartera.Contactos
import ar.com.corredor.app.pantallas.cartera.EditarContacto
import ar.com.corredor.app.pantallas.cartera.FichaContacto
import ar.com.corredor.app.pantallas.cartera.FichaOperacion
import ar.com.corredor.app.pantallas.cartera.ImportarContactos
import ar.com.corredor.app.pantallas.cartera.Operacion
import ar.com.corredor.app.pantallas.cartera.Operaciones
import ar.com.corredor.app.pantallas.cartera.PlataOperacion
import ar.com.corredor.app.pantallas.cartera.SeCayo
import ar.com.corredor.app.pantallas.cobranzas.AAltaContrato
import ar.com.corredor.app.pantallas.cobranzas.ACobrar
import ar.com.corredor.app.pantallas.cobranzas.AContratosFiltro
import ar.com.corredor.app.pantallas.cobranzas.ADevolucion
import ar.com.corredor.app.pantallas.cobranzas.ARecordarMes
import ar.com.corredor.app.pantallas.cobranzas.ARecorrida
import ar.com.corredor.app.pantallas.cobranzas.ARegistrarCobro
import ar.com.corredor.app.pantallas.cobranzas.ARendicionVista
import ar.com.corredor.app.pantallas.cobranzas.AltaContrato
import ar.com.corredor.app.pantallas.cobranzas.AltaContratoPantalla
import ar.com.corredor.app.pantallas.cobranzas.Cobranzas
import ar.com.corredor.app.pantallas.cobranzas.Contratos
import ar.com.corredor.app.pantallas.cobranzas.Devolucion
import ar.com.corredor.app.pantallas.cobranzas.FichaContrato
import ar.com.corredor.app.pantallas.cobranzas.Firmas
import ar.com.corredor.app.pantallas.cobranzas.HojaCobro
import ar.com.corredor.app.pantallas.cobranzas.HojaMensajeWhatsApp
import ar.com.corredor.app.pantallas.cobranzas.Inventario
import ar.com.corredor.app.pantallas.cobranzas.ListaActas
import ar.com.corredor.app.pantallas.cobranzas.PedidoMensaje
import ar.com.corredor.app.pantallas.cobranzas.ReciboVista
import ar.com.corredor.app.pantallas.cobranzas.RecordarATodos
import ar.com.corredor.app.pantallas.cobranzas.RendicionVista
import ar.com.corredor.app.pantallas.consorcios.AFichaConsorcioEn
import ar.com.corredor.app.pantallas.consorcios.ALiquidarConsorcio
import ar.com.corredor.app.pantallas.consorcios.ATrabajoConsorcio
import ar.com.corredor.app.pantallas.consorcios.Consorcios
import ar.com.corredor.app.pantallas.consorcios.FichaConsorcio
import ar.com.corredor.app.pantallas.consorcios.FichaTrabajoConsorcio
import ar.com.corredor.app.pantallas.consorcios.LiquidarConsorcio
import ar.com.corredor.app.pantallas.entrada.AltaInmobiliaria
import ar.com.corredor.app.pantallas.entrada.Bienvenida
import ar.com.corredor.app.pantallas.entrada.CrearOtraInmobiliaria
import ar.com.corredor.app.pantallas.entrada.ElegirInmobiliaria
import ar.com.corredor.app.pantallas.entrada.Entrada
import ar.com.corredor.app.pantallas.entrada.IngresoMail
import ar.com.corredor.app.pantallas.entrada.PegarInvitacion
import ar.com.corredor.app.pantallas.entrada.RecuperarAcceso
import ar.com.corredor.app.pantallas.entrada.VerInvitacion
import ar.com.corredor.app.pantallas.informes.EditarMetas
import ar.com.corredor.app.pantallas.inicio.Avisos
import ar.com.corredor.app.pantallas.inicio.BuscarTodo
import ar.com.corredor.app.pantallas.inicio.ConfigAvisos
import ar.com.corredor.app.pantallas.inicio.HojaResponderAviso
import ar.com.corredor.app.pantallas.inicio.HorarioSilencio
import ar.com.corredor.app.pantallas.inicio.InicioRaiz
import ar.com.corredor.app.pantallas.mas.AutorizarIA
import ar.com.corredor.app.pantallas.mas.CarteraAgente
import ar.com.corredor.app.pantallas.mas.FirmaYSesiones
import ar.com.corredor.app.pantallas.mas.MasRaiz
import ar.com.corredor.app.pantallas.mas.MiPerfil
import ar.com.corredor.app.pantallas.mas.TarjetaAgente
import ar.com.corredor.app.pantallas.mas.TusInmobiliarias
import ar.com.corredor.app.pantallas.mensajes.Chat
import ar.com.corredor.app.pantallas.mensajes.GuardarNumero
import ar.com.corredor.app.pantallas.mensajes.GuardarRespuesta
import ar.com.corredor.app.pantallas.mensajes.MensajesRaiz
import ar.com.corredor.app.pantallas.mensajes.RespuestaML
import ar.com.corredor.app.pantallas.mensajes.VincularPropiedad
import ar.com.corredor.app.pantallas.mira.ARevisionIds
import ar.com.corredor.app.pantallas.mira.Captadas
import ar.com.corredor.app.pantallas.mira.RevisionIA
import ar.com.corredor.app.pantallas.propiedades.AArreglarPropiedad
import ar.com.corredor.app.pantallas.propiedades.ACartera
import ar.com.corredor.app.pantallas.propiedades.AEditarEnPaso
import ar.com.corredor.app.pantallas.propiedades.AMandarPropiedad
import ar.com.corredor.app.pantallas.propiedades.AMercadoLibre
import ar.com.corredor.app.pantallas.propiedades.AReserva
import ar.com.corredor.app.pantallas.propiedades.AReservaTemporada
import ar.com.corredor.app.pantallas.propiedades.ATarjetaPropiedad
import ar.com.corredor.app.pantallas.propiedades.AltaPropiedad
import ar.com.corredor.app.pantallas.propiedades.FichaPropiedad
import ar.com.corredor.app.pantallas.propiedades.FiltrosCartera
import ar.com.corredor.app.pantallas.propiedades.Galeria
import ar.com.corredor.app.pantallas.propiedades.MapaCartera
import ar.com.corredor.app.pantallas.propiedades.PantallaMercadoLibre
import ar.com.corredor.app.pantallas.propiedades.PantallaReserva
import ar.com.corredor.app.pantallas.propiedades.PropiedadesRaiz
import ar.com.corredor.app.pantallas.propiedades.Propiedad
import ar.com.corredor.app.pantallas.propiedades.TarjetaVisita
import ar.com.corredor.app.pantallas.secciones.Seccion
import ar.com.corredor.app.pantallas.temporada.CheckinTemporada
import ar.com.corredor.app.pantallas.web.MiWeb
import ar.com.corredor.app.tema.C
import ar.com.corredor.app.tema.Estilo
import ar.com.corredor.app.tema.PantallasMostradas
import ar.com.corredor.app.tema.TemaCorredor
import ar.com.corredor.app.tema.Texto
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.lang.ref.WeakReference

/** La API simulada (ApiFalsa en CorredorTests/PantallasTests.swift): responde por ruta, sin mirar el método ni la query,
 *  con pruebas/respuestas.json (las mismas respuestas que iOS). Lo que no está: 404 con `{}`. */
class ApiDePrueba(private val tabla: Map<String, String?>) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val req = chain.request()
        val cuerpo = tabla[req.url.encodedPath]
        val codigo = if (cuerpo == null) 404 else 200
        return Response.Builder().request(req).protocol(Protocol.HTTP_1_1).code(codigo).message(if (codigo == 200) "OK" else "No existe")
            .body((cuerpo ?: "{}").toResponseBody("application/json".toMediaType())).build()
    }

    companion object {
        /** Las respuestas de iOS (setUp de PantallasTests.swift): ruta → JSON. Las claves con «_» son comentarios. */
        val respuestas: Map<String, String> by lazy {
            val raiz = Api.json.parseToJsonElement(ApiDePrueba::class.java.getResource("/pruebas/respuestas.json")!!.readText()).jsonObject
            raiz.filterKeys { !it.startsWith("_") }.mapValues { Api.json.encodeToString(JsonElement.serializer(), it.value) }
        }
    }
}

/** Clon de CorredorTests/PantallasTests.swift: monta cada pantalla contra la API simulada, en una ventana de 393 × 852 dp
 *  como la de iOS, y guarda la captura en claro y oscuro (build/capturas/<Nombre>-claro|oscuro.png, el mismo nombre que
 *  iOS). Falla si una pantalla se rompe al montarse. Al sumar una pantalla en iOS, sumarla acá. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-xxhdpi")
class PantallasTest {
    @get:Rule val compose = createComposeRule()

    /** Lo que la API simulada responde en `ruta`, leído con el modelo de la app. */
    private inline fun <reified T> respuesta(ruta: String): T =
        Api.json.decodeFromString(ApiDePrueba.respuestas[ruta] ?: error("La API simulada no tiene $ruta"))

    /** El Estado de iOS en montarEn: la sesión de Mariana en Luna Propiedades con el rol pedido. `extra` pisa respuestas;
     *  `respuestas` reemplaza la tabla entera (testInicioSinNada). */
    private fun estado(rol: String = "duenio", extra: Map<String, String?> = emptyMap(), respuestas: Map<String, String?>? = null): Estado {
        ModoPrueba.activo = true
        ar.com.corredor.app.nucleo.Preferencias.borrarTodo()
        val tabla = respuestas ?: (ApiDePrueba.respuestas + extra)
        val api = Api("https://api.prueba", "x", OkHttpClient.Builder().addInterceptor(ApiDePrueba(tabla)).build())
        val e = Estado(api)
        e.contexto = RuntimeEnvironment.getApplication()
        e.sesion = Sesion(
            Sesion.Usuario(id = "u1", email = "mariana@luna.test", nombre = "Mariana", apellido = "Luna"), "i1", rol,
            listOf(Sesion.Membresia("i1", "luna", "Luna Propiedades", rol)),
        )
        e.fase = Estado.Fase.adentro
        Abrir.estado = WeakReference(e)
        return e
    }

    /** Deja correr la carga: las respuestas llegan por el hilo de OkHttp y vuelven al principal. */
    private fun asentar(ms: Long) {
        repeat(12) {
            Thread.sleep(60)
            compose.mainClock.advanceTimeBy(ms / 12)
            compose.waitForIdle()
        }
    }

    /** Monta la vista con el Estado (en una pila, como el NavigationStack de iOS: la barra con el título que pone la
     *  pantalla) y captura claro y oscuro. */
    private fun montar(nombre: String, e: Estado = estado(), espera: Long = 1500, vista: @Composable () -> Unit) {
        // El acento es global: sin esto, la marca que cargó la pantalla anterior teñía la siguiente.
        ar.com.corredor.app.tema.Acento.actual = null
        val tema = mutableStateOf("claro")
        compose.setContent {
            TemaCorredor(tema.value) {
                val barra = remember { Barra() }
                CompositionLocalProvider(LocalEstado provides e, LocalBarra provides barra) {
                    Column(Modifier.fillMaxSize().background(C.fondo)) {
                        if (!barra.oculta && !barra.titulo.isNullOrEmpty() && !barra.grande) {
                            Box(Modifier.fillMaxWidth().statusBarsPadding().height(44.dp)) {
                                Texto(barra.titulo ?: "", Estilo.headline, C.texto, Modifier.align(Alignment.Center), lineas = 1)
                            }
                        }
                        Box(Modifier.weight(1f).fillMaxWidth()) { vista() }
                    }
                }
            }
        }
        for (t in listOf("claro", "oscuro")) {
            tema.value = t
            asentar(espera)
            compose.onRoot().captureRoboImage("build/capturas/$nombre-$t.png")
        }
    }

    // MARK: El mapa

    /** El mapa a nivel barrio con tres propiedades. */
    @Test fun mapa() {
        val json = """
        [{"id": "p1", "titulo": "Mendoza 2350, 5.º A", "precio": 172000, "moneda": "USD", "lat": -34.5627, "lng": -58.4563},
         {"id": "p2", "titulo": "Thames 2240", "precio": 611800, "moneda": "ARS", "lat": -34.5870, "lng": -58.4250},
         {"id": "p3", "titulo": "Gorriti 5530", "precio": 340000, "moneda": "USD", "lat": -34.5850, "lng": -58.4380}]
        """
        val props = Api.json.decodeFromString<List<Propiedad>>(json)
        montar("Mapa", espera = 2000) { MapaCartera(props, emptyMap(), modifier = Modifier.height(600.dp).padding(16.dp)) }
    }

    // MARK: Las pantallas de la tanda 1

    @Test fun inicioDuena() = montar("InicioDuena") { InicioRaiz() }
    @Test fun inicioAgente() = montar("InicioAgente", estado("agente")) { InicioRaiz() }
    @Test fun inicioSinNada() = montar("InicioSinNada", estado("agente", respuestas = mapOf("/v1/panel" to "{}"))) { InicioRaiz() }
    @Test fun buscar() = montar("Buscar") { BuscarTodo(texto = "Mendoza") }
    @Test fun avisos() = montar("Avisos") { Avisos() }
    @Test fun configAvisos() = montar("ConfigAvisos") { ConfigAvisos() }
    /** APerfil abre MiPerfil. */
    @Test fun perfil() = montar("Perfil") { MiPerfil() }
    @Test fun propiedades() = montar("Propiedades") { PropiedadesRaiz() }
    @Test fun ficha() = montar("Ficha") { FichaPropiedad(id = "p1") }
    @Test fun galeria() = montar("Galeria") { Galeria(id = "p1", indice = 0) }
    @Test fun alta() = montar("Alta") { AltaPropiedad(editar = null) }
    @Test fun altaEditar() = montar("AltaEditar") { AltaPropiedad(editar = "p1") }
    @Test fun captadas() = montar("Captadas") { Captadas() }
    @Test fun mensajes() = montar("Mensajes") { MensajesRaiz() }
    @Test fun chat() = montar("Chat") { Chat(id = "w1") }
    @Test fun ml() = montar("ML") { RespuestaML(id = "q1") }
    @Test fun agenda() = montar("Agenda") { AgendaRaiz(apilada = true) }
    @Test fun mas() = montar("Mas") { MasRaiz() }
    /** «Más» con la estructura de la API ya cargada (Mi inmobiliaria, Herramientas, el porcentaje). */
    @Test fun masConEstructura() {
        val e = estado()
        montar("MasConEstructura", e) {
            LaunchedEffect(Unit) { e.cargarEstructura() }
            MasRaiz()
        }
    }

    // El alta en cada paso (los da la API: los mismos que el panel).
    @Test fun altaLogo() = montar("Alta-logo") { AltaInmobiliaria(inicial = "logo") }
    @Test fun altaMarca() = montar("Alta-marca") { AltaInmobiliaria(inicial = "marca") }
    @Test fun altaPortada() = montar("Alta-portada") { AltaInmobiliaria(inicial = "portada") }
    @Test fun altaContacto() = montar("Alta-contacto") { AltaInmobiliaria(inicial = "contacto") }
    @Test fun altaLegales() = montar("Alta-legales") { AltaInmobiliaria(inicial = "legales") }
    @Test fun altaRubros() = montar("Alta-rubros") { AltaInmobiliaria(inicial = "rubros") }
    @Test fun altaArranque() = montar("Alta-arranque") { AltaInmobiliaria(inicial = "arranque") }
    @Test fun altaPublicar() = montar("Alta-publicar") { AltaInmobiliaria(inicial = "publicar") }

    @Test fun miWeb() = montar("MiWeb", espera = 2000) { MiWeb() }
    /** La bienvenida de quien se suma a un equipo (foto, WhatsApp, avisos y apariencia). */
    @Test fun bienvenida() = montar("Bienvenida", estado("agente")) { Bienvenida() }
    @Test fun masAgente() = montar("MasAgente", estado("agente")) { MasRaiz() }
    @Test fun cita() = montar("Cita") { FichaCita(id = "a1") }
    @Test fun nuevaCita() = montar("NuevaCita") { NuevaCita() }
    @Test fun contactos() = montar("Contactos") { Contactos() }
    @Test fun fichaContacto() = montar("FichaContacto") { FichaContacto(id = "c1") }
    @Test fun operaciones() = montar("Operaciones") { Operaciones() }
    @Test fun fichaOperacion() = montar("FichaOperacion") { FichaOperacion(id = "o1") }
    @Test fun cobranzas() = montar("Cobranzas") { Cobranzas() }
    @Test fun contratos() = montar("Contratos") { Contratos() }
    @Test fun fichaContrato() = montar("FichaContrato") { FichaContrato(id = "k1") }
    @Test fun recibo() = montar("Recibo") { ReciboVista(contrato = "k1", cobro = "b1") }
    @Test fun rendicion() = montar("Rendicion") { RendicionVista(contrato = "k1", id = "r1") }
    @Test fun altaContrato() = montar("AltaContrato") { AltaContrato() }
    @Test fun inventario() = montar("Inventario") { Inventario(propiedad = "p1") }
    @Test fun firmas() = montar("Firmas") { Firmas(clave = "p1/a1") }

    // MARK: Las secciones (testSecciones en iOS: una captura por clave)

    private fun seccion(clave: String, id: String? = null) =
        montar(if (id == null || id == "x1") "Seccion-$clave" else "Seccion-$clave-$id", espera = 1000) { Seccion(clave = clave, id = id) }

    @Test fun seccionMas() = seccion("mas")
    @Test fun seccionConfiguracion() = seccion("configuracion")
    @Test fun seccionEquipo() = seccion("equipo")
    @Test fun seccionIntegraciones() = seccion("integraciones")
    @Test fun seccionRespuestas() = seccion("respuestas")
    @Test fun seccionProveedores() = seccion("proveedores")
    @Test fun seccionObjetivos() = seccion("objetivos")
    @Test fun seccionInventarios() = seccion("inventarios")
    @Test fun seccionRendiciones() = seccion("rendiciones")
    @Test fun seccionPapelera() = seccion("papelera")
    @Test fun seccionActividad() = seccion("actividad")
    @Test fun seccionRevision() = seccion("revision")
    @Test fun seccionInforme() = seccion("informe")
    @Test fun seccionIndices() = seccion("indices")
    @Test fun seccionWeb() = seccion("web")
    @Test fun seccionDifusion() = seccion("difusion")
    @Test fun seccionConsorcios() = seccion("consorcios")
    @Test fun seccionIa() = seccion("ia")
    @Test fun seccionImportar() = seccion("importar")
    @Test fun seccionNoExiste() = seccion("no-existe")
    @Test fun seccionPlan() = seccion("plan")
    @Test fun seccionSuscripcion() = seccion("suscripcion")
    @Test fun seccionInmobiliaria() = seccion("inmobiliaria")
    @Test fun seccionWhatsapp() = seccion("whatsapp")
    @Test fun seccionMercadolibre() = seccion("mercadolibre")
    @Test fun seccionPlantillas() = seccion("plantillas")
    @Test fun seccionEditor() = seccion("editor")
    @Test fun seccionInvitaciones() = seccion("invitaciones")
    @Test fun seccionEmpezar() = seccion("empezar")
    @Test fun seccionEmprendimientos() = seccion("emprendimientos")
    @Test fun seccionTemporada() = seccion("temporada")
    @Test fun seccionReservas() = seccion("reservas")
    @Test fun seccionFirma() = seccion("firma")
    // Las que cuelgan de una propiedad, un contrato, un consorcio o un emprendimiento.
    @Test fun seccionDocumentosPropiedad() = seccion("documentos-propiedad", "x1")
    @Test fun seccionDocumentosContrato() = seccion("documentos-contrato", "x1")
    @Test fun seccionBienes() = seccion("bienes", "x1")
    @Test fun seccionReglamento() = seccion("reglamento", "x1")
    @Test fun seccionEnvios() = seccion("envios", "x1")
    @Test fun seccionGrabaciones() = seccion("grabaciones", "x1")
    @Test fun seccionReservasPropiedad() = seccion("reservas-propiedad", "x1")
    @Test fun seccionEmprendimiento() = seccion("emprendimiento", "x1")
    @Test fun seccionConsorcio() = seccion("consorcio", "x1")
    // Las de la tanda 2, con ids que la API simulada conoce.
    @Test fun seccionActasP1() = seccion("actas", "p1")
    @Test fun seccionTarjetaAgente() = seccion("tarjeta-agente", "u2")
    @Test fun seccionAutorizarIA() = seccion("autorizar-ia", "sol_abcdefghijklmn")
    @Test fun seccionInvitacion() = seccion("invitacion", "tok_abcdefghijklmnopqrstuv")
    @Test fun seccionConsorcioE1() = seccion("consorcio", "e1")
    @Test fun seccionVistasP1() = seccion("vistas", "p1")
    @Test fun seccionTarifasP1() = seccion("tarifas", "p1")
    @Test fun seccionReservasPropiedadP1() = seccion("reservas-propiedad", "p1")
    @Test fun seccionActividadIA() = seccion("actividad", "ia")

    @Test fun entrada() = montar("Entrada") { Entrada() }
    @Test fun elegirInmobiliaria() = montar("ElegirInmobiliaria") { ElegirInmobiliaria() }
    @Test fun bloqueo() = montar("Bloqueo", espera = 1000) { Bloqueo() }
    @Test fun principal() = montar("Principal") { Principal() }

    // MARK: Tanda 2: pantallas y hojas nuevas

    // Propiedades, MIRA, temporada (parte 1)
    @Test fun altaBorrador() = montar("AltaBorrador") { AltaPropiedad(editar = null, borrador = "b1") }
    @Test fun altaEnPaso() = montar("AltaEnPaso") { AltaPropiedad(editar = "p1", enPaso = "fotos") }
    @Test fun fichaArreglar() = montar("FichaArreglar") { FichaPropiedad(id = "p1", arreglarAlAbrir = true) }
    @Test fun fichaMandar() = montar("FichaMandar") { FichaPropiedad(id = "p1", compartirAlAbrir = true, compartirCon = "c1") }
    @Test fun reserva() = montar("Reserva") { PantallaReserva(id = "r1") }
    @Test fun checkinTemporada() = montar("CheckinTemporada") { CheckinTemporada(id = "r1") }
    @Test fun mercadoLibre() = montar("MercadoLibre") { PantallaMercadoLibre() }
    @Test fun tarjetaVisita() = montar("TarjetaVisita") { TarjetaVisita(id = "p1") }
    @Test fun carteraBarrio() = montar("CarteraBarrio") { PropiedadesRaiz(inicial = FiltrosCartera(barrio = listOf("Belgrano")), titulo = "Más en Belgrano") }
    @Test fun revisionIds() = montar("RevisionIds") { RevisionIA(ids = "b1") }

    // Mensajes, agenda, contactos, operaciones, avisos (parte 2)
    @Test fun altaRapidaCita() = montar("AltaRapidaCita") { AltaRapidaCita(alFormulario = {}) }
    @Test fun licenciasAgenda() = montar("LicenciasAgenda") { LicenciasAgenda() }
    @Test fun importarContactos() = montar("ImportarContactos") { ImportarContactos() }
    @Test fun seCayo() = montar("SeCayo") { SeCayo(propiedad = "Mendoza 2350", contacto = "Valeria", alElegir = {}) }
    @Test fun plataOperacion() {
        val o = respuesta<Operacion>("/v1/operaciones/o1")
        montar("PlataOperacion") { PlataOperacion(o = o, agente = "Sofía") }
    }
    @Test fun vincularPropiedad() = montar("VincularPropiedad") { VincularPropiedad(tiene = true, alElegir = {}) }
    @Test fun guardarNumero() = montar("GuardarNumero") { GuardarNumero(waId = "5491144712290", nombre = "", tipo = "comprador", guardar = { _, _ -> true }) }
    @Test fun guardarRespuesta() = montar("GuardarRespuesta") { GuardarRespuesta(texto = "Hola", alGuardar = {}) }
    @Test fun hojaResponderAviso() {
        val pagina = respuesta<PaginaAvisos>("/v1/avisos")
        val aviso = pagina.avisos.first()
        val accion = AvisoCentro.Accion(accion = "responder", titulo = "Responder", entrada = "texto")
        montar("HojaResponderAviso") { HojaResponderAviso(aviso = aviso, accion = accion, mandar = { null }) }
    }
    @Test fun horarioSilencio() = montar("HorarioSilencio") { HorarioSilencio(desde = 21, hasta = 8, domingos = true, guardar = { _, _, _ -> }) }
    @Test fun editarMetas() = montar("EditarMetas") {
        EditarMetas(desde = "2026-11-01", hasta = "2026-11-30", mes = "noviembre", agentes = emptyList(), filas = emptyMap(), equipo = emptyList(), honorarios = null, cerrar = {})
    }
    @Test fun editarContacto() {
        val c = respuesta<Contacto>("/v1/contactos/c1")
        montar("EditarContacto") { EditarContacto(contacto = c, soloBusca = true) }
    }
    @Test fun nuevoContacto() = montar("NuevoContacto") { EditarContacto() }

    // Cobranzas y contratos (parte 3)
    @Test fun registrarCobro() = montar("RegistrarCobro") { Cobranzas(registrar = true, contrato = "k1") }
    @Test fun contratosAjustan() = montar("ContratosAjustan") { Contratos(vista = "ajustan") }
    @Test fun fichaContratoRecordar() = montar("FichaContratoRecordar") { FichaContrato(id = "k1", recordar = "2026-11") }
    @Test fun renovarContrato() = montar("RenovarContrato") { AltaContrato(renovar = "k1") }
    @Test fun altaContratoBorrador() = montar("AltaContratoBorrador") { AltaContratoPantalla(destino = AAltaContrato(borrador = "b1")) }
    @Test fun rendicionPropietario() = montar("RendicionPropietario") { RendicionVista(contrato = "k1", id = "r1", verComoPropietario = true) }
    @Test fun hojaCobro() = montar("HojaCobro") {
        HojaCobro(item = ACobrar(contratoId = "k1", contactoId = "c1", nombre = "Lucía Méndez", propiedad = "Gorriti 4820", periodo = "2026-11-01",
            esperado = 540000.0, moneda = "ARS", vence = "2026-11-10", punitorioDiario = 0.5))
    }
    @Test fun recordarATodos() = montar("RecordarATodos") { RecordarATodos(deudores = emptyList()) }
    @Test fun hojaMensajeWhatsApp() = montar("HojaMensajeWhatsApp") {
        HojaMensajeWhatsApp(pedido = PedidoMensaje(titulo = "Recordarle a Lucía", contactoId = "c1", nombre = "Lucía Méndez", texto = "¡Hola Lucía!", para = "recordatorio"))
    }

    // Consorcios (parte 3b)
    @Test fun consorcios() = montar("Consorcios") { Consorcios() }
    private fun fichaConsorcio(p: String) = montar("FichaConsorcio-$p") { FichaConsorcio(id = "e1", pestanaInicial = p) }
    @Test fun fichaConsorcioResumen() = fichaConsorcio("resumen")
    @Test fun fichaConsorcioUnidades() = fichaConsorcio("unidades")
    @Test fun fichaConsorcioExpensas() = fichaConsorcio("expensas")
    @Test fun fichaConsorcioTrabajos() = fichaConsorcio("trabajos")
    @Test fun fichaConsorcioProveedores() = fichaConsorcio("proveedores")
    @Test fun fichaConsorcioPersonal() = fichaConsorcio("personal")
    @Test fun fichaConsorcioNovedades() = fichaConsorcio("novedades")
    @Test fun liquidarConsorcio() = montar("LiquidarConsorcio") { LiquidarConsorcio(consorcio = "e1") }
    @Test fun trabajoConsorcio() = montar("TrabajoConsorcio") { FichaTrabajoConsorcio(consorcio = "e1", id = "t1") }

    // Inventarios y actas (parte 3c)
    @Test fun listaActas() = montar("ListaActas") { ListaActas() }
    @Test fun recorridaDevolucion() = montar("RecorridaDevolucion") { Inventario(propiedad = "p1", tipoPedido = "devolucion") }
    @Test fun devolucion() = montar("Devolucion") { Devolucion(propiedad = "p1") }

    // Inicio, Más y cuenta (partes 4, 4a y 4b)
    @Test fun tusInmobiliarias() = montar("TusInmobiliarias") { TusInmobiliarias() }
    @Test fun tarjetaAgente() = montar("TarjetaAgente") { TarjetaAgente(usuario = "u2") }
    @Test fun autorizarIA() = montar("AutorizarIA") { AutorizarIA(solicitud = "sol_abcdefghijklmn") }
    @Test fun carteraAgente() = montar("CarteraAgente") { CarteraAgente(usuario = "u2", nombre = "Martín Ríos") }
    @Test fun verInvitacion() = montar("VerInvitacion", estado("agente")) { VerInvitacion(token = "tok_abcdefghijklmnopqrstuv") }
    @Test fun pegarInvitacion() = montar("PegarInvitacion") { PegarInvitacion() }
    @Test fun crearOtraInmobiliaria() = montar("CrearOtraInmobiliaria") { CrearOtraInmobiliaria() }
    @Test fun miPerfilAgente() = montar("MiPerfilAgente", estado("agente")) { MiPerfil() }
    @Test fun firmaYSesiones() = montar("FirmaYSesiones") { FirmaYSesiones() }
    @Test fun recuperarAcceso() = montar("RecuperarAcceso") { RecuperarAcceso(email = "") }
    @Test fun ingresoMail() = montar("IngresoMail") { IngresoMail() }

    // MARK: Toda la navegación

    /** Cada destino apilado con `estado.abrir` (como lo hace la app) tiene que mostrar su pantalla y no el «Esta pantalla
     *  todavía no está» de Registro. Si una pantalla tira una excepción, el test falla con el nombre del destino. */
    @Test fun testNavegacionTotal() {
        val e = estado()
        compose.setContent {
            TemaCorredor("claro") { CompositionLocalProvider(LocalEstado provides e) { Principal() } }
        }
        asentar(1500)

        val claves = listOf("mas", "configuracion", "equipo", "integraciones", "respuestas", "proveedores", "objetivos", "inventarios", "rendiciones",
            "papelera", "actividad", "revision", "informe", "indices", "web", "difusion", "emprendimientos", "temporada", "reservas",
            "consorcios", "importar", "ia", "firma", "plan", "suscripcion", "inmobiliaria", "whatsapp", "mercadolibre", "plantillas",
            "editor", "invitaciones", "empezar")
        val conId = listOf("vistas", "tarifas", "emprendimiento", "consorcio", "envios", "grabaciones", "reservas-propiedad", "documentos-propiedad",
            "documentos-contrato", "bienes", "reglamento", "actas", "invitacion")
        val destinos = mutableListOf<Pair<String, Any>>(
            "FichaPropiedad" to AFichaPropiedad(id = "p1"), "Galeria" to AGaleria(id = "p1"), "AltaPropiedad" to AAltaPropiedad(),
            "Captadas" to ACaptadas(), "Chat" to AChat(id = "c1"), "PreguntaML" to APreguntaML(id = "m1"), "Agenda" to AAgendaDia(),
            "Cita" to ACita(id = "ci1"), "Contactos" to AContactos(), "FichaContacto" to AFichaContacto(id = "k1"), "Operaciones" to AOperaciones(),
            "FichaOperacion" to AFichaOperacion(id = "o1"), "Cobranzas" to ACobranzas(), "Contratos" to AContratos(),
            "FichaContrato" to AFichaContrato(id = "t1"), "Recibo" to ARecibo(contrato = "t1", cobro = "b1"), "Rendicion" to ARendicion(contrato = "t1", id = "r1"),
            "Inventario" to AInventario(id = "p1"), "Firmas" to AFirmas(id = "a1"), "Avisos" to AAvisos(), "ConfigAvisos" to AConfigAvisos(),
            "Perfil" to APerfil(), "Buscar" to ABuscar(texto = "Mendoza"),
            // Los de la tanda 2 (propiedades, cobranzas, inventarios, consorcios y secciones con id).
            "AltaPropiedad-borrador" to AAltaPropiedad(borrador = "b1"), "AvisoResponder" to AAvisos(aviso = "v1", responder = "responder"),
            "ArreglarPropiedad" to AArreglarPropiedad(id = "p1"), "MandarPropiedad" to AMandarPropiedad(id = "p1", contacto = "c1"),
            "EditarEnPaso" to AEditarEnPaso(id = "p1", paso = "fotos"), "Reserva" to AReserva(id = "r1"), "ReservaTemporada" to AReservaTemporada(id = "r1"),
            "MercadoLibre" to AMercadoLibre(), "TarjetaPropiedad" to ATarjetaPropiedad(id = "p1"), "Cartera" to ACartera(barrio = "Belgrano"),
            "RevisionIds" to ARevisionIds(ids = "b1"), "RegistrarCobro" to ARegistrarCobro(contrato = "k1"),
            "RecordarMes" to ARecordarMes(contrato = "k1", periodo = "2026-11"), "ContratosFiltro" to AContratosFiltro(vista = "ajustan"),
            "AltaContrato" to AAltaContrato(borrador = "b1"), "RendicionVista" to ARendicionVista(contrato = "k1", id = "r1"),
            "Recorrida" to ARecorrida(id = "p1", tipo = "devolucion"), "Devolucion" to ADevolucion(id = "p1"),
            "LiquidarConsorcio" to ALiquidarConsorcio(consorcio = "e1"), "TrabajoConsorcio" to ATrabajoConsorcio(consorcio = "e1", id = "t1"),
            "FichaConsorcioEn" to AFichaConsorcioEn(id = "e1", pestana = "unidades", pedido = "deben"),
            "Seccion-tarjeta-agente" to ASeccion(clave = "tarjeta-agente", id = "u2"),
            "Seccion-autorizar-ia" to ASeccion(clave = "autorizar-ia", id = "sol_abcdefghijklmn"),
            // ACarteraAgente no va: lo abre Equipo, como en iOS.
        )
        destinos += claves.map { "Seccion-$it" to ASeccion(clave = it) }
        destinos += conId.map { "Seccion-$it" to ASeccion(clave = it, id = "x1") }

        fun mostro(d: Any): Boolean {
            e.pilas.values.forEach { it.clear() }
            e.pestana = Pestana.inicio
            asentar(300)
            val antes = PantallasMostradas.cuenta
            compose.runOnIdle { e.abrir(d) }
            asentar(1200)
            val pendiente = compose.onAllNodesWithText("Esta pantalla todavía no está").fetchSemanticsNodes().isNotEmpty()
            return PantallasMostradas.cuenta != antes && !pendiente
        }

        val rotos = mutableListOf<String>()
        for ((nombre, d) in destinos) {
            println("NAVEGACION abre $nombre")
            val ok = try {
                mostro(d) || mostro(d)
            } catch (t: Throwable) {
                fail("NAVEGACION: $nombre tiró ${t::class.simpleName}: ${t.message}\n${t.stackTraceToString().take(3000)}")
                false
            }
            if (!ok) rotos += nombre
        }
        assertEquals("Estos destinos no muestran su pantalla: $rotos", emptyList<String>(), rotos)
    }
}
