package ar.com.corredor.app.app

import androidx.compose.runtime.Composable
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
import ar.com.corredor.app.nucleo.Pestana
import ar.com.corredor.app.pantallas.agenda.AgendaRaiz
import ar.com.corredor.app.pantallas.agenda.FichaCita
import ar.com.corredor.app.pantallas.cartera.Contactos
import ar.com.corredor.app.pantallas.cartera.FichaContacto
import ar.com.corredor.app.pantallas.cartera.FichaOperacion
import ar.com.corredor.app.pantallas.cartera.Operaciones
import ar.com.corredor.app.pantallas.cobranzas.AAltaContrato
import ar.com.corredor.app.pantallas.cobranzas.AContratosFiltro
import ar.com.corredor.app.pantallas.cobranzas.ADevolucion
import ar.com.corredor.app.pantallas.cobranzas.ARecordarMes
import ar.com.corredor.app.pantallas.cobranzas.ARecorrida
import ar.com.corredor.app.pantallas.cobranzas.ARegistrarCobro
import ar.com.corredor.app.pantallas.cobranzas.ARendicionVista
import ar.com.corredor.app.pantallas.cobranzas.AltaContratoPantalla
import ar.com.corredor.app.pantallas.cobranzas.Cobranzas
import ar.com.corredor.app.pantallas.cobranzas.Contratos
import ar.com.corredor.app.pantallas.cobranzas.Devolucion
import ar.com.corredor.app.pantallas.cobranzas.FichaContrato
import ar.com.corredor.app.pantallas.cobranzas.Firmas
import ar.com.corredor.app.pantallas.cobranzas.Inventario
import ar.com.corredor.app.pantallas.cobranzas.ReciboVista
import ar.com.corredor.app.pantallas.cobranzas.RendicionVista
import ar.com.corredor.app.pantallas.consorcios.AFichaConsorcioEn
import ar.com.corredor.app.pantallas.consorcios.ALiquidarConsorcio
import ar.com.corredor.app.pantallas.consorcios.ATrabajoConsorcio
import ar.com.corredor.app.pantallas.consorcios.FichaConsorcio
import ar.com.corredor.app.pantallas.consorcios.FichaTrabajoConsorcio
import ar.com.corredor.app.pantallas.consorcios.LiquidarConsorcio
import ar.com.corredor.app.pantallas.inicio.Avisos
import ar.com.corredor.app.pantallas.inicio.BuscarTodo
import ar.com.corredor.app.pantallas.inicio.ConfigAvisos
import ar.com.corredor.app.pantallas.inicio.InicioRaiz
import ar.com.corredor.app.pantallas.mas.ACarteraAgente
import ar.com.corredor.app.pantallas.mas.CarteraAgente
import ar.com.corredor.app.pantallas.mas.MasRaiz
import ar.com.corredor.app.pantallas.mas.MiPerfil
import ar.com.corredor.app.pantallas.mensajes.Chat
import ar.com.corredor.app.pantallas.mensajes.MensajesRaiz
import ar.com.corredor.app.pantallas.mensajes.RespuestaML
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
import ar.com.corredor.app.pantallas.propiedades.PantallaMercadoLibre
import ar.com.corredor.app.pantallas.propiedades.PantallaReserva
import ar.com.corredor.app.pantallas.propiedades.PropiedadesRaiz
import ar.com.corredor.app.pantallas.propiedades.TarjetaVisita
import ar.com.corredor.app.pantallas.secciones.Seccion
import ar.com.corredor.app.pantallas.temporada.CheckinTemporada
import ar.com.corredor.app.tema.Pendiente
import ar.com.corredor.app.tema.dia
import ar.com.corredor.app.nucleo.Fecha

// Une la navegación de Principal con las pantallas: cada `.navigationDestination(for: X.self)` de Corredor/** en iOS
// (Principal.destinos(): destinosInicio, destinosPropiedades, destinosMira, destinosMensajes, destinosAgenda,
// destinosCartera, destinosCobranzas, destinosSecciones, destinosConsorcios, y el de Equipo) con la misma pantalla y los
// mismos parámetros. Las ASeccion(clave:) las resuelve Seccion (pantallas/secciones/Secciones.kt).

object Registro {
    /** La raíz de cada pestaña. */
    @Composable
    fun Raiz(p: Pestana) {
        when (p) {
            Pestana.inicio -> InicioRaiz()
            Pestana.propiedades -> PropiedadesRaiz()
            Pestana.mensajes -> MensajesRaiz()
            else -> MasRaiz()
        }
    }

    /** La pantalla de un destino apilado. */
    @Composable
    fun Destino(d: Any) {
        when (d) {
            // destinosInicio (Pantallas/Inicio/InicioDestinos.swift)
            is ABuscar -> BuscarTodo(texto = d.texto)
            is AAvisos -> {
                // Un aviso puntual (`/avisos?aviso=<id>&responder=<acción>`): arriba y con su hoja.
                d.aviso?.let { Avisos.foco = it to d.responder }
                Avisos()
            }
            is AConfigAvisos -> ConfigAvisos()
            is APerfil -> MiPerfil()

            // destinosPropiedades (Pantallas/Propiedades/Propiedades.swift)
            is AFichaPropiedad -> FichaPropiedad(id = d.id)
            is AArreglarPropiedad -> FichaPropiedad(id = d.id, arreglarAlAbrir = true)
            is AMandarPropiedad -> FichaPropiedad(id = d.id, compartirAlAbrir = true, compartirCon = d.contacto)
            is AGaleria -> Galeria(id = d.id, indice = d.indice)
            is AAltaPropiedad -> AltaPropiedad(editar = d.editar, borrador = d.borrador)
            is AEditarEnPaso -> AltaPropiedad(editar = d.id, enPaso = d.paso)
            is AReserva -> PantallaReserva(id = d.id)
            is AReservaTemporada -> CheckinTemporada(id = d.id)
            is AMercadoLibre -> PantallaMercadoLibre()
            is ATarjetaPropiedad -> TarjetaVisita(id = d.id)
            is ACartera -> PropiedadesRaiz(inicial = FiltrosCartera(barrio = listOf(d.barrio)), titulo = "Más en ${d.barrio}")

            // destinosMira (Pantallas/Mira/Mira.swift)
            is ACaptadas -> Captadas()
            is ARevisionIds -> RevisionIA(ids = d.ids)

            // destinosMensajes (Pantallas/Mensajes/Mensajes.swift)
            is AChat -> Chat(id = d.id)
            is APreguntaML -> RespuestaML(id = d.id)

            // destinosAgenda (Pantallas/Agenda/Agenda.swift)
            is AAgendaDia -> AgendaRaiz(diaInicial = d.dia?.let { Fecha.dia(it) }, apilada = true)
            is ACita -> FichaCita(id = d.id)

            // destinosCartera (Pantallas/Cartera/Cartera.swift)
            is AContactos -> Contactos(libres = d.agente == "libre")
            is AFichaContacto -> FichaContacto(id = d.id)
            is AOperaciones -> Operaciones()
            is AFichaOperacion -> FichaOperacion(id = d.id)

            // destinosCobranzas (Pantallas/Cobranzas/Cobranzas.swift e InventarioComun.swift)
            is ACobranzas -> Cobranzas()
            is ARegistrarCobro -> Cobranzas(registrar = true, contrato = d.contrato)
            is AContratos -> Contratos()
            is AContratosFiltro -> Contratos(vista = d.vista, indice = d.indice)
            is AFichaContrato -> FichaContrato(id = d.id)
            is ARecordarMes -> FichaContrato(id = d.contrato, recordar = d.periodo)
            is AAltaContrato -> AltaContratoPantalla(destino = d)
            is ARecibo -> ReciboVista(contrato = d.contrato, cobro = d.cobro)
            is ARendicion -> RendicionVista(contrato = d.contrato, id = d.id)
            is ARendicionVista -> RendicionVista(contrato = d.contrato, id = d.id, verComoPropietario = true)
            is AInventario -> Inventario(propiedad = d.id)
            is AFirmas -> Firmas(clave = d.id)
            is ARecorrida -> Inventario(propiedad = d.id, tipoPedido = d.tipo)
            is ADevolucion -> Devolucion(propiedad = d.id)

            // destinosSecciones (Pantallas/Secciones/Secciones.swift)
            is ASeccion -> Seccion(clave = d.clave, id = d.id)

            // destinosConsorcios (Pantallas/Consorcios/ConsorcioComun.swift)
            is ALiquidarConsorcio -> LiquidarConsorcio(consorcio = d.consorcio, pedido = d.periodo)
            is ATrabajoConsorcio -> FichaTrabajoConsorcio(consorcio = d.consorcio, id = d.id)
            is AFichaConsorcioEn -> FichaConsorcio(id = d.id, pestanaInicial = d.pestana, pedidoInicial = d.pedido)

            // Pantallas/Mas/Equipo.swift
            is ACarteraAgente -> CarteraAgente(usuario = d.usuario, nombre = d.nombre)

            // Un destino sin pantalla (el triángulo amarillo de SwiftUI): no debería pasar.
            else -> Pendiente("Esta pantalla todavía no está")
        }
    }
}
