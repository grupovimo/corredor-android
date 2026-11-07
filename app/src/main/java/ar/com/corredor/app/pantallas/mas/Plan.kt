package ar.com.corredor.app.pantallas.mas

import ar.com.corredor.app.app.PantallaApilada

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ar.com.corredor.app.app.Estado
import ar.com.corredor.app.app.LocalEstado
import ar.com.corredor.app.nucleo.*
import ar.com.corredor.app.tema.*
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.text.NumberFormat
import java.time.Instant
import java.time.Period
import java.time.ZoneId
import java.util.Currency

// Clon de Pantallas/Mas/Plan.swift. El App Store (StoreKit 2) pasa a Google Play (Play Billing) con los mismos ids de
// producto: la compra lleva la inmobiliaria como obfuscatedAccountId y su token va a la API
// (`POST /v1/suscripcion/google`), que la une a la suscripción como hace con las de Apple.

// MARK: - Google Play
// La tienda es la de nucleo/Suscripcion.kt (`Tienda.compartida`, el `Tienda` de Nucleo/Suscripcion.swift).

/** «14 días», «1 mes» (Product.SubscriptionPeriod.texto), desde el período ISO de Play («P14D»). */
private fun textoPeriodo(iso: String): String {
    val p = runCatching { Period.parse(iso) }.getOrNull() ?: return iso
    return when {
        p.years > 0 -> if (p.years == 1) "1 año" else "${p.years} años"
        p.months > 0 -> if (p.months == 1) "1 mes" else "${p.months} meses"
        p.days % 7 == 0 && p.days > 0 -> { val w = p.days / 7; if (w == 1) "1 semana" else "$w semanas" }
        else -> if (p.days == 1) "1 día" else "${p.days} días"
    }
}

/** Cuándo termina la prueba gratis (`Calendar.date(byAdding:)`). */
private fun despuesDe(iso: String): Instant {
    val p = runCatching { Period.parse(iso) }.getOrNull() ?: return Instant.now()
    return java.time.ZonedDateTime.now(ZoneId.systemDefault()).plus(p).toInstant()
}

// MARK: - Tu plan

/** «Tu plan», como un carrito: cómo estás hoy, mensual o anual, los tres planes como tarjetas para
 *  elegir y el ticket con lo que pagás hoy, cuándo es el primer cobro y cuánto después. Se paga con
 *  Google Play (los precios son los de la tienda de la cuenta de Google); la letra chica, la que pide la tienda. */
@Composable
fun PantallaPlan() {
    val estado = LocalEstado.current
    val ctx = LocalContext.current
    val alcance = rememberCoroutineScope()
    val tienda = Tienda.compartida
    var ciclo by remember { mutableStateOf("anio") }
    var elegido by remember { mutableStateOf("profesional") }
    var comprando by remember { mutableStateOf(false) }
    var restaurando by remember { mutableStateOf(false) }
    var inicializado by remember { mutableStateOf(false) }
    var confirmarBaja by remember { mutableStateOf(false) }
    var bajando by remember { mutableStateOf(false) }
    val s = estado.suscripcion

    fun producto(p: Suscripcion.Plan, c: String): Producto? = tienda.productos[p.producto(c)]

    /** El precio de la tienda (según la cuenta de Google); sin la tienda, el de lista. */
    fun precio(p: Suscripcion.Plan, c: String): String = producto(p, c)?.precioTexto ?: Formato.plata(p.precio(c))

    fun ahorro(p: Suscripcion.Plan): String? {
        val m = producto(p, "mes"); val a = producto(p, "anio")
        if (m != null && a != null) {
            val x = m.precio * 12 - a.precio
            return if (x > 0) m.formatear(x) else null
        }
        return Formato.plata(p.precioMes * 12 - p.precioAnio)
    }

    fun porMes(p: Suscripcion.Plan): String {
        val a = producto(p, "anio")
        if (a != null) return a.formatear(a.precio / 12, decimales = 2)
        return Formato.plata(Math.round(p.precioAnio / 12).toDouble())
    }

    /** «12 meses × $X»: lo que serían doce meses sueltos (con la tienda, su precio). */
    fun doceMeses(p: Suscripcion.Plan): String? {
        val m = producto(p, "mes")
        if (m != null) return m.formatear(m.precio * 12)
        return Formato.plata(p.precioMes * 12)
    }

    suspend fun comprar(plan: Suscripcion.Plan) {
        val p = producto(plan, ciclo) ?: return
        val inmo = estado.sesion?.inmobiliariaId ?: return
        comprando = true
        try {
            when (val r = tienda.comprar(p, inmo)) {
                is Tienda.Resultado.listo -> {
                    estado.suscripcion = r.s
                    Haptico.exito()
                    estado.avisar("¡Listo! Tu plan ${plan.nombre} quedó activo.")
                }
                Tienda.Resultado.pendiente -> estado.avisar("La compra quedó pendiente de aprobación. Cuando se apruebe, tu plan se activa solo.")
                Tienda.Resultado.cancelada -> {}
            }
        } catch (e: CancellationException) { throw e } catch (e: Exception) {
            estado.avisar(e)
        } finally { comprando = false }
    }

    suspend fun restaurar() {
        restaurando = true
        try {
            val x = tienda.restaurar()
            if (x != null) {
                estado.suscripcion = x
                estado.avisar("Listo, recuperamos tu plan.")
            } else {
                estado.avisar("No encontramos compras de Corredor en esta cuenta de Google.", ok = false)
            }
        } catch (e: CancellationException) { throw e } catch (e: Exception) {
            estado.avisar(e)
        } finally { restaurando = false }
    }

    suspend fun darDeBaja() {
        bajando = true
        try {
            val n: Suscripcion = estado.api.enviar("POST", "/v1/suscripcion/baja", emptyMap<String, String>())
            estado.suscripcion = n
            estado.avisar("Listo, no se renueva más. Seguís con todo hasta el ${Formato.fechaLarga(n.vigenteHasta)}.")
        } catch (e: CancellationException) { throw e } catch (e: Exception) { estado.avisar(e) } finally { bajando = false; confirmarBaja = false }
    }

    LaunchedEffect(s?.planes?.size) {
        val x = s
        if (x == null) { estado.cargarSuscripcion(); return@LaunchedEffect }
        if (!inicializado) {
            inicializado = true
            (x.esperando ?: x.plan)?.let { elegido = it; ciclo = x.ciclo }
        }
        tienda.cargar(x.planes.flatMap { listOf(it.appleMes, it.appleAnio) })
    }

    PantallaApilada("Tu plan") {
        Refrescable({ estado.cargarSuscripcion() }) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                if (s != null) {
                    CabeceraPlan(s, estado.esDuenio, ctx)
                    if (!estado.esDuenio) {
                        NotaPlan("person.badge.key", "El plan lo elige y lo paga el dueño de la inmobiliaria. Si necesitás algo que tu plan no trae, pedíselo.")
                    } else if (s.conMercadoPago) {
                        NotaPlan("creditcard", "Tu plan se paga con Mercado Pago. Para pasarlo a Google Play, primero da de baja el débito acá abajo.")
                    }
                    Segmentado(listOf("mes" to "Mensual", "anio" to "Anual · 2 meses gratis"), ciclo, { ciclo = it })
                    s.planes.forEach { p ->
                        TarjetaPlan(p, s, elegido == p.id, ciclo, precio(p, ciclo), if (ciclo == "anio") porMes(p) else "", if (ciclo == "anio") ahorro(p) else null) {
                            elegido = p.id
                            Haptico.seleccion()
                        }
                    }
                    if (estado.esDuenio && s.planes.isNotEmpty()) {
                        // El ticket
                        val plan = s.planes.firstOrNull { it.id == elegido } ?: s.planes[0]
                        val prod = producto(plan, ciclo)
                        val gratis = prod?.periodoGratis
                        val primerCobro = gratis?.let { despuesDe(it) } ?: Instant.now()
                        val cada = if (ciclo == "anio") "año" else "mes"
                        val mismo = s.plan == plan.id && s.ciclo == ciclo && s.renueva && !s.soloLectura
                        val sobran = if (plan.agentes > 0) maxOf(0, s.agentes.usados - plan.agentes) else 0
                        val precioTxt = precio(plan, ciclo)
                        Column(Modifier.fillMaxWidth().tarjeta().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Texto("Resumen", Estilo.total, C.texto)
                            LineaTicket("Corredor ${plan.nombre}", if (ciclo == "anio") "anual" else "mensual", fuerte = true)
                            LineaTicket("Personas que cargan", if (plan.agentes == 0) "sin tope" else if (plan.agentes == 1) "1" else "hasta ${plan.agentes}")
                            if (ciclo == "anio") doceMeses(plan)?.let { LineaTicket("12 meses × ${precio(plan, "mes")}", it) }
                            if (ciclo == "anio") ahorro(plan)?.let { LineaTicket("2 meses de regalo", "−$it", tono = C.okTinta) }
                            LineaTicket("Total por $cada", precioTxt, fuerte = true)
                            Rayita()
                            Row(Modifier.fillMaxWidth()) {
                                Texto("Hoy pagás", Estilo.headline, C.texto, Modifier.weight(1f).alignByBaseline())
                                Texto(if (gratis != null) (prod?.formatear(0.0) ?: "0") else precioTxt, Estilo.montoMeta, C.texto, Modifier.alignByBaseline(), numeros = true)
                            }
                            if (gratis != null) {
                                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(C.okFondo).padding(10.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icono("gift", 13f, C.okTinta)
                                    Texto(Formato.sinViudas("${textoPeriodo(gratis)} gratis: el primer cobro es el ${Formato.fechaLarga(primerCobro)}."), Estilo.footnote, C.okTinta)
                                }
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                DatoTicket("Primer cobro", if (gratis == null) "Hoy · $precioTxt" else "${Formato.fechaLarga(primerCobro)} · $precioTxt")
                                DatoTicket("Después", "$precioTxt cada $cada")
                                DatoTicket("Medio de pago", "Tu cuenta de Google (Google Play)")
                            }
                            if (sobran > 0) {
                                Row(Modifier.clip(RoundedCornerShape(10.dp)).background(C.avisoFondo).padding(10.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                                    Icono("person.2", 13f, C.oroTinta)
                                    Texto(Formato.sinViudas("Hoy cargan ${s.agentes.usados} personas y este plan es para ${Formato.cantidad(plan.agentes, "persona", "personas")}. Pasá ${Formato.cantidad(sobran, "persona", "personas")} a lector en Equipo o elegí un plan más grande."),
                                        Estilo.footnote, C.oroTinta)
                                }
                            }
                            if (!s.conMercadoPago) {
                                BotonPrincipal(
                                    if (mismo) "Es tu plan actual" else if (gratis != null) "Empezar gratis" else "Suscribirme por $precioTxt",
                                    { alcance.launch { comprar(plan) } }, Modifier.padding(top = 4.dp),
                                    habilitado = !(prod == null || mismo || sobran > 0 || comprando), cargando = comprando,
                                )
                                if (prod == null && !tienda.cargando) {
                                    Texto(Formato.sinViudas("Pronto vas a poder elegir tu plan desde el teléfono. Mientras, seguís usando Corredor como hasta ahora."), Estilo.caption, C.texto3)
                                }
                                if (restaurando) Box(Modifier.fillMaxWidth().height(40.dp), contentAlignment = Alignment.Center) { Rueda() }
                                else BotonTexto("Restaurar compras", { alcance.launch { restaurar() } }, alto = 40.dp)
                            }
                            Texto(Formato.sinViudas("La suscripción se cobra a tu cuenta de Google y se renueva sola cada $cada al mismo precio, salvo que la des de baja al menos 24 horas antes de que termine el período, desde Google Play › Pagos y suscripciones › Suscripciones. Al vencer, tu inmobiliaria queda en sólo lectura y nada se borra."),
                                Estilo.caption, C.texto3)
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                Texto("Términos de uso", Estilo.caption, C.primarioTexto, Modifier.clickable { abrirEnNavegador(ctx, Api.web().toString().trimEnd('/') + "/terminos") })
                                Texto("Privacidad", Estilo.caption, C.primarioTexto, Modifier.clickable { abrirEnNavegador(ctx, Api.web().toString().trimEnd('/') + "/privacidad") })
                            }
                        }
                    }
                    if (estado.esDuenio && s.cobros.isNotEmpty()) CobrosPlan(s)
                    if (estado.esDuenio && s.medio == "mercadopago" && s.renueva) {
                        // Baja del débito de Mercado Pago
                        Column(Modifier.fillMaxWidth().tarjeta().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (!confirmarBaja) {
                                BotonTexto("Dar de baja el débito", { confirmarBaja = true }, alto = 40.dp, color = C.errorTinta)
                            } else {
                                Texto(Formato.sinViudas("No se cobra más. Seguís con todo hasta el ${Formato.fechaLarga(s.vigenteHasta)}; después, sólo lectura."), Estilo.footnote, C.texto)
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    BotonSecundario("Mejor no", { confirmarBaja = false })
                                    BotonDestructivo("Dar de baja", { alcance.launch { darDeBaja() } }, Modifier.weight(1f), habilitado = !bajando, cargando = bajando)
                                }
                            }
                        }
                    }
                } else {
                    Box(Modifier.fillMaxWidth().heightIn(min = 300.dp), contentAlignment = Alignment.Center) { Rueda() }
                }
            }
        }
    }
}

// MARK: Cómo estás hoy

@Composable
private fun CabeceraPlan(s: Suscripcion, duenio: Boolean, ctx: Context) {
    val tono = if (s.estado == "vencida") Tono.rojo else if (s.estado == "prueba" && s.diasRestantes <= 3) Tono.oro else Tono.verde
    val titulo = when (s.estado) {
        "prueba" -> "Tenés todo Corredor gratis hasta el ${Formato.fechaLarga(s.pruebaHasta)}."
        "activa" -> "Próximo cobro: ${Formato.fechaLarga(s.proximoCobro ?: s.vigenteHasta)}${s.importe?.let { " · ${Formato.plata(it, s.moneda ?: "ARS")}" } ?: ""}."
        "baja" -> "Tu plan no se renueva: vale hasta el ${Formato.fechaLarga(s.vigenteHasta)}."
        else -> "Tu inmobiliaria está en sólo lectura."
    }
    val detalle = when (s.estado) {
        "prueba" -> "Elegí tu plan cuando quieras. Si no elegís, después queda en sólo lectura: nada se borra."
        "activa" -> when (s.medio) {
            "google" -> "Se paga con Google Play, desde el teléfono."
            "apple" -> "Se paga con el App Store, desde el iPhone."
            else -> "Se debita solo con Mercado Pago."
        }
        "baja" -> "Elegí un plan para seguir sin cortes. Lo que ya pagaste no se pierde."
        else -> "Todo lo tuyo sigue acá y lo podés ver. Para volver a cargar, elegí un plan: se activa en el momento."
    }
    val personas = s.agentes.cupo?.let { c -> "${s.agentes.usados} de $c ${if (c == 1) "persona" else "personas"} que cargan" }
        ?: Formato.cantidad(s.agentes.usados, "persona carga", "personas cargan")
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).tarjeta()) {
        Box(Modifier.width(4.dp).fillMaxHeight().background(tono.tinta))
        Column(Modifier.weight(1f).padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Texto(s.nombreEstado, Estilo.caption, tono.tinta, Modifier.clip(Capsula).background(tono.fondo).padding(horizontal = 10.dp, vertical = 4.dp))
            Texto(titulo, Estilo.titulo3, C.texto)
            Texto(Formato.sinViudas(detalle), Estilo.footnote, C.texto2)
            Row(Modifier.padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icono("person.2", 13f, C.texto3)
                Texto(personas, Estilo.footnote, C.texto2)
            }
            // «Administrar la suscripción» (manageSubscriptionsSheet): la de Google Play, en Play.
            if (s.medio == "google" && s.renueva && duenio) {
                Box(Modifier.padding(top = 4.dp)) {
                    BotonTonal("Administrar la suscripción", {
                        abrirUrl(ctx, "https://play.google.com/store/account/subscriptions?package=${ctx.packageName}")
                    })
                }
            }
        }
    }
}

@Composable
private fun NotaPlan(icono: String, texto: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
        Icono(icono, 17f, C.texto3)
        Texto(Formato.sinViudas(texto), Estilo.footnote, C.texto2, Modifier.weight(1f))
    }
}

// MARK: Los planes

@Composable
private fun TarjetaPlan(p: Suscripcion.Plan, s: Suscripcion, on: Boolean, ciclo: String, precio: String, porMes: String, ahorro: String?, alTocar: () -> Unit) {
    val actual = s.plan == p.id && !s.soloLectura
    val forma = RoundedCornerShape(M.rTarjeta)
    Tocable(alTocar, Modifier.semantics { selected = on }) {
        Column(
            Modifier.fillMaxWidth().sombra(forma, C.sombra.copy(alpha = 0.32f), 11.dp, y = 10.dp).clip(forma).background(C.superficie)
                .border(if (on) 2.dp else 1.dp, if (on) C.accion else C.linea, forma).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(20.dp).clip(Capsula).border(if (on) 6.dp else 2.dp, if (on) C.accion else C.linea2, Capsula))
                Texto(p.nombre, Estilo.total, C.texto)
                if (actual) ChipPlan("Tu plan", Tono.verde) else if (p.destacado) ChipPlan("El más elegido", Tono.oro)
            }
            Texto(p.resumen, Estilo.footnote, C.texto2)
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                Texto(precio, Estilo.montoMeta, C.texto, Modifier.alignByBaseline(), numeros = true)
                Texto(if (ciclo == "anio") "/año" else "/mes", Estilo.footnote, C.texto3, Modifier.alignByBaseline())
            }
            if (ciclo == "anio") {
                Texto("Equivale a $porMes por mes${ahorro?.let { " · ahorrás $it" } ?: ""}", Estilo.caption, C.texto3)
            }
            Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                p.incluye.forEach { x ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                        Icono("checkmark", 13f, C.accion)
                        Texto(x, Estilo.footnote, C.texto2)
                    }
                }
            }
        }
    }
}

@Composable
private fun ChipPlan(t: String, tono: Tono) {
    Texto(t, Estilo.caption, tono.tinta, Modifier.clip(Capsula).background(tono.fondo).padding(horizontal = 8.dp, vertical = 3.dp))
}

// MARK: El ticket

@Composable
private fun LineaTicket(a: String, b: String, fuerte: Boolean = false, tono: Color? = null) {
    val e = if (fuerte) Estilo.fila else Estilo.subhead
    val c = tono ?: (if (fuerte) C.texto else C.texto2)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Texto(a, e, c, Modifier.weight(1f).padding(end = 8.dp))
        Texto(b, e, c, numeros = true)
    }
}

@Composable
private fun DatoTicket(t: String, v: String) {
    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Texto(t, Estilo.caption, C.texto3)
        Texto(v, Estilo.footnote, C.texto)
    }
}

// MARK: Pagos

@Composable
private fun CobrosPlan(s: Suscripcion) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TituloBloque("Tus pagos")
        Column(Modifier.fillMaxWidth().tarjeta()) {
            s.cobros.forEachIndexed { i, c ->
                if (i > 0) Box(Modifier.fillMaxWidth().padding(start = 48.dp).height(0.5.dp).background(C.linea))
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(22.dp), contentAlignment = Alignment.Center) { Icono(if (c.medio == "apple") "apple.logo" else "creditcard", 17f, C.texto3) }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Texto(c.detalle, Estilo.footnote, C.texto, lineas = 2)
                        Texto(Formato.dia(c.creadoEn), Estilo.caption, C.texto3)
                    }
                    c.importe?.let { Texto(Formato.plata(it, c.moneda ?: "ARS"), Estilo.footnote, C.texto, numeros = true) }
                }
            }
        }
    }
}

/** La línea punteada del ticket. */
@Composable
private fun Rayita() {
    val c = C.linea2
    Canvas(Modifier.fillMaxWidth().height(1.dp)) {
        drawLine(c, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())))
    }
}

/** La franja de la suscripción en el Inicio y en «Más»: la última semana de prueba, el plan que no se
 *  renueva o el sólo lectura, con el botón a «Tu plan». */
@Composable
fun AvisoPlan() {
    val estado = LocalEstado.current
    val s = estado.suscripcion ?: return
    if (!s.pideAtencion) return
    val vencida = s.estado == "vencida"
    val tono = if (vencida) Tono.rojo else if (s.diasRestantes <= 3 || s.estado == "baja") Tono.oro else Tono.verde
    val texto = when (s.estado) {
        "vencida" -> if (estado.esDuenio) "Tu prueba terminó: estás en sólo lectura. Todo sigue acá." else "La inmobiliaria está en sólo lectura."
        "baja" -> "Tu plan no se renueva: termina el ${Formato.dia(s.vigenteHasta)}."
        else -> if (s.diasRestantes <= 1) "Hoy es el último día de tu prueba gratis." else "Te quedan ${s.diasRestantes} días de prueba gratis."
    }
    Tocable({ estado.abrir(ASeccion("plan")) }) {
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(tono.fondo).padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icono(if (vencida) "lock" else "hourglass", 17f, tono.tinta)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Texto(texto, Estilo.subheadFuerte, tono.tinta)
                Texto(if (estado.esDuenio) (if (s.estado == "baja") "Renovar" else "Elegí tu plan") else "Ver los planes", Estilo.footnote, tono.tinta.copy(alpha = 0.85f))
            }
            Icono("chevron.right", 13f, tono.tinta)
        }
    }
}
