package ar.com.corredor.app.nucleo

import android.app.Activity
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ar.com.corredor.app.app.ModoPrueba
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.lang.ref.WeakReference
import java.time.Instant

/** La suscripción de la inmobiliaria (GET /v1/suscripcion): tres planes (Agente, Inmobiliaria y Equipo), mensual o anual
 *  (diez meses), 14 días gratis con el plan Inmobiliaria y sólo lectura al vencer. Los topes (personas, propiedades
 *  activas, contratos, MIRA) los aplica la API: acá se muestran. En el teléfono se paga con Google Play (Play Billing, los mismos ids que la App Store); en la web, con
 *  Mercado Pago. La API une todo. */
@Serializable
data class Suscripcion(
    /** prueba | activa | baja | vencida */
    val estado: String,
    val plan: String? = null,
    val ciclo: String = "mes",
    val medio: String? = null,
    val pruebaHasta: FechaApi = Instant.EPOCH,
    val pagadoHasta: FechaApi? = null,
    val vigenteHasta: FechaApi = Instant.EPOCH,
    val diasRestantes: Int = 0,
    val renueva: Boolean = false,
    val proximoCobro: FechaApi? = null,
    val importe: Double? = null,
    val moneda: String? = null,
    val esperando: String? = null,
    /** Una baja de plan ya autorizada (Mercado Pago): el actual sigue hasta `desde` y ahí entra éste. */
    val proximo: Proximo? = null,
    val soloLectura: Boolean = false,
    val enPrueba: Boolean = false,
    /** Cuándo se borran los datos si no elige un plan (sólo vencida y con la purga prendida; si no, null). */
    val borraEn: FechaApi? = null,
    val agentes: Agentes = Agentes(),
    /** El plan que rige hoy (en la prueba, Inmobiliaria) y lo usado de cada tope. Una API vieja no los manda. */
    val planVigente: String? = null,
    val personasExtra: Int = 0,
    val topes: Topes? = null,
    val planes: List<Plan> = emptyList(),
    val mercadoPago: Boolean = false,
    /** Si la API confirma compras de Google Play (sin la cuenta de Play configurada, no: la compra daría 409
     *  google_apagado después de cobrarse, y Play la reintegra). */
    val googlePlay: Boolean = false,
    val cobros: List<Cobro> = emptyList(),
) {
    @Serializable
    data class Plan(
        val id: String,
        val nombre: String,
        val resumen: String = "",
        val agentes: Int = 0,
        /** Lo que cuesta cada persona más (0: el plan no suma personas). No se compra en la tienda. */
        val personaExtraMes: Double = 0.0,
        /** Topes del plan (0: sin tope). */
        val propiedades: Int = 0,
        val contratos: Int = 0,
        val mira: Int = 0,
        val precioMes: Double = 0.0,
        val precioAnio: Double = 0.0,
        val incluye: List<String> = emptyList(),
        val destacado: Boolean = false,
        val appleMes: String = "",
        val appleAnio: String = "",
    ) {
        /** El id del producto de la tienda (el mismo en la App Store y en Google Play). */
        fun producto(ciclo: String): String = if (ciclo == "anio") appleAnio else appleMes
        fun precio(ciclo: String): Double = if (ciclo == "anio") precioAnio else precioMes
    }

    @Serializable
    data class Agentes(val usados: Int = 0, val cupo: Int? = null)

    @Serializable
    data class Proximo(val plan: String, val ciclo: String = "mes", val desde: FechaApi = Instant.EPOCH)

    /** Lo usado de un recurso con tope (tope null: sin tope). */
    @Serializable
    data class Uso(val usados: Int = 0, val tope: Int? = null)

    @Serializable
    data class Topes(val propiedades: Uso = Uso(), val contratos: Uso = Uso(), val mira: Uso = Uso())

    /** Lo que pasa el tope si se elige `p`: personas, propiedades activas y contratos que quedan en sólo lectura (nada
     *  se borra). */
    data class DeMas(val personas: Int, val propiedades: Int, val contratos: Int) {
        val alguno: Boolean get() = personas + propiedades + contratos > 0
    }

    /** `conExtras`: cuenta las personas pagas aparte; una compra en la tienda las pone en 0 (Apple y Google no venden
     *  personas extra), así que el ticket de la app las deja afuera. */
    fun deMas(p: Plan, conExtras: Boolean = true): DeMas {
        fun de(u: Uso?, t: Int): Int = if (u == null || t <= 0) 0 else maxOf(0, u.usados - t)
        val extras = if (conExtras && p.personaExtraMes > 0) personasExtra else 0
        return DeMas(maxOf(0, agentes.usados - p.agentes - extras), de(topes?.propiedades, p.propiedades), de(topes?.contratos, p.contratos))
    }

    /** Lo que pasa el tope del plan que rige (después de bajar de plan): se ve, no se borra. */
    val fueraDelPlan: DeMas get() {
        fun de(u: Uso?): Int = u?.tope?.let { maxOf(0, u.usados - it) } ?: 0
        return DeMas(agentes.cupo?.let { maxOf(0, agentes.usados - it) } ?: 0, de(topes?.propiedades), de(topes?.contratos))
    }

    /** «6 propiedades y 2 contratos» (lo que quedó fuera del plan), o null si nada. */
    val textoFuera: String? get() {
        val f = fueraDelPlan
        val partes = buildList {
            if (f.propiedades > 0) add(if (f.propiedades == 1) "1 propiedad" else "${f.propiedades} propiedades")
            if (f.contratos > 0) add(if (f.contratos == 1) "1 contrato" else "${f.contratos} contratos")
            if (f.personas > 0) add(if (f.personas == 1) "1 persona" else "${f.personas} personas")
        }
        if (partes.isEmpty()) return null
        return if (partes.size == 1) partes[0] else partes.dropLast(1).joinToString(", ") + " y " + partes.last()
    }

    /** El tope ya lleno de un recurso (personas, propiedades, contratos): lo que se cargue de más no entra. Con el plan
     *  que da más lugar (como la API: el más chico más grande que el que rige). */
    data class TopeLleno(val usados: Int, val tope: Int, val plan: String, val siguiente: Plan?)

    fun topeLleno(recurso: String): TopeLleno? {
        val u = when (recurso) {
            "personas" -> Uso(agentes.usados, agentes.cupo)
            "propiedades" -> topes?.propiedades
            else -> topes?.contratos
        } ?: return null
        val t = u.tope ?: return null
        if (u.usados < t) return null
        fun tope(p: Plan): Int = when (recurso) { "personas" -> p.agentes; "propiedades" -> p.propiedades; else -> p.contratos }
        val i = planes.indexOfFirst { it.id == (planVigente ?: plan) }.coerceAtLeast(0)
        val actual = planes.getOrNull(i)
        val sig = planes.drop(i + 1).firstOrNull { p -> actual == null || tope(p) == 0 || tope(p) > tope(actual) } ?: planes.lastOrNull()
        return TopeLleno(u.usados, t, planQueRige?.nombre ?: "", sig)
    }

    @Serializable
    data class Cobro(val id: String, val medio: String = "", val detalle: String = "", val importe: Double? = null, val moneda: String? = null, val creadoEn: FechaApi = Instant.EPOCH)

    val planActual: Plan? get() = planes.firstOrNull { it.id == plan }
    val planQueRige: Plan? get() = planes.firstOrNull { it.id == (planVigente ?: plan) }

    /** «Prueba gratis · 9 días», «Plan Inmobiliaria · anual», «Sólo lectura». */
    val nombreEstado: String get() = when (estado) {
        "prueba" -> if (diasRestantes == 1) "Prueba gratis · último día" else "Prueba gratis · $diasRestantes días"
        "activa" -> "Plan ${planActual?.nombre ?: ""} · ${if (ciclo == "anio") "anual" else "mensual"}"
        "baja" -> "Plan ${planActual?.nombre ?: ""} · sin renovar"
        else -> "Sólo lectura"
    }

    /** Hay que avisarle arriba (Inicio, Más): la última semana de prueba, sin renovar, vencida o con algo fuera del plan
     *  (bajó de plan con más de lo que entra). */
    val pideAtencion: Boolean get() = estado == "vencida" || (estado == "prueba" && diasRestantes <= 7) || (estado == "baja" && diasRestantes <= 7) ||
        textoFuera != null

    /** Se paga con Mercado Pago desde la web (no se puede cambiar desde el teléfono). */
    val conMercadoPago: Boolean get() = medio == "mercadopago" && renueva && !soloLectura

    /** Se renueva con un medio que no es Google Play (Mercado Pago u otra tienda): desde acá no se compra, porque Play
     *  cobraría y la API rechazaría la compra. Se administra donde se contrató. */
    val pagaAfuera: Boolean get() = renueva && !soloLectura && !medio.isNullOrEmpty() && medio != "google"

    /** Con qué se paga, dicho como en la cabecera de «Tu plan». */
    val comoSePaga: String get() = when (medio) {
        "google" -> "Se paga con Google Play, desde el teléfono."
        "mercadopago" -> "Se debita solo con Mercado Pago."
        else -> "Se administra donde lo contrataste."
    }
}

/** Un producto de Google Play (el Product de StoreKit): su id, el precio como lo escribe Play y los días gratis de la
 *  oferta de entrada, si tiene. */
data class Producto(
    val id: String,
    /** El precio como lo escribe Play (displayPrice). */
    val displayPrice: String,
    val micros: Long,
    /** La oferta a comprar (con la prueba gratis si le corresponde a esta cuenta). */
    val oferta: String,
    val diasGratis: Int?,
    val detalle: ProductDetails,
    /** La moneda de la tienda de la cuenta de Google (ARS, USD). */
    val moneda: String = "ARS",
    /** La prueba gratis (`P14D`, `P1M`): Play sólo la devuelve si la cuenta puede usarla. */
    val periodoGratis: String? = null,
    /** Cada cuánto se cobra (`P1M`, `P1Y`): para saber si un cambio de plan sube o baja. */
    val periodoCobro: String = "P1M",
) {
    val precioTexto: String get() = displayPrice
    val precio: Double get() = micros / 1_000_000.0

    /** Un monto en la moneda de la tienda (Product.priceFormatStyle de StoreKit). */
    fun formatear(n: Double, decimales: Int? = null): String {
        val f = java.text.NumberFormat.getCurrencyInstance(Formato.argentina)
        runCatching { f.currency = java.util.Currency.getInstance(moneda) }
        if (decimales != null) { f.minimumFractionDigits = 0; f.maximumFractionDigits = decimales }
        return f.format(n)
    }
}

/** Google Play (la Tienda de StoreKit en iOS): los productos de los planes, la compra (con la inmobiliaria como
 *  obfuscatedAccountId) y las compras que llegan solas (renovaciones, otro teléfono). Cada una se le manda a la API
 *  (`POST /v1/suscripcion/google {producto, token}`) y recién ahí se reconoce. */
class Tienda private constructor() {
    companion object {
        val compartida = Tienda()
    }

    /** El contexto de la app (lo pone CorredorApp) y la actividad para la hoja de compra (la pone MainActivity). */
    var contexto: Context? = null
    var actividad: WeakReference<Activity>? = null

    val productos = mutableStateMapOf<String, Producto>()
    var cargando by mutableStateOf(false)
        private set
    /** Quién recibe las compras (la API de la sesión). */
    var api: Api? = null
    /** Sólo el dueño paga el plan: con otro rol, la API rechaza la compra (403) y quedaría sin reconocer dando vueltas.
     *  Se pone al entrar a cada inmobiliaria. */
    var esDuenio = false

    private var cliente: BillingClient? = null
    private var esperando: CompletableDeferred<Pair<BillingResult, List<Purchase>?>>? = null
    private var escuchando = false
    private var conectando: CompletableDeferred<Boolean>? = null
    private val alcance = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private suspend fun conectado(): BillingClient? {
        val ctx = contexto ?: return null
        val c = cliente ?: BillingClient.newBuilder(ctx.applicationContext)
            .setListener { r, compras ->
                val e = esperando
                if (e != null && !e.isCompleted) e.complete(r to compras)
                // Compras que llegan solas (pendientes que se aprobaron): se registran igual (si es el dueño).
                else if (r.responseCode == BillingClient.BillingResponseCode.OK && esDuenio) alcance.launch { compras?.forEach { runCatching { procesar(it) } } }
            }
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .enableAutoServiceReconnection()
            .build().also { cliente = it }
        if (c.isReady) return c
        // Si ya hay una conexión en curso (escuchar() al entrar y enseguida Tu plan), se espera esa:
        // un segundo startConnection lo rechaza BillingClient.
        val listo = conectando?.takeIf { !it.isCompleted } ?: CompletableDeferred<Boolean>().also { d ->
            conectando = d
            c.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(r: BillingResult) { d.complete(r.responseCode == BillingClient.BillingResponseCode.OK) }
                override fun onBillingServiceDisconnected() { if (!d.isCompleted) d.complete(false) }
            })
        }
        return if (listo.await()) c else null
    }

    /** Trae de Play los productos que falten. */
    suspend fun cargar(ids: List<String>) {
        val faltan = ids.filter { it.isNotEmpty() }.distinct()
        if (faltan.isEmpty() || productos.keys.containsAll(faltan) || ModoPrueba.activo) return
        cargando = true
        try {
            val c = conectado() ?: return
            val p = QueryProductDetailsParams.newBuilder().setProductList(faltan.map {
                QueryProductDetailsParams.Product.newBuilder().setProductId(it).setProductType(BillingClient.ProductType.SUBS).build()
            }).build()
            val r = runCatching { c.queryProductDetails(p) }.getOrNull() ?: return
            r.productDetailsList.orEmpty().forEach { d ->
                val ofertas = d.subscriptionOfferDetails.orEmpty()
                // La oferta con la prueba gratis (precio 0 en la primera fase), si Play la da; si no, la base.
                val conGratis = ofertas.firstOrNull { o -> o.pricingPhases.pricingPhaseList.firstOrNull()?.priceAmountMicros == 0L }
                val o = conGratis ?: ofertas.firstOrNull() ?: return@forEach
                val fases = o.pricingPhases.pricingPhaseList
                val fase = fases.lastOrNull() ?: return@forEach
                val periodo = fases.firstOrNull { it.priceAmountMicros == 0L }?.billingPeriod
                productos[d.productId] = Producto(d.productId, fase.formattedPrice, fase.priceAmountMicros, o.offerToken, periodo?.let { diasDe(it) }, d,
                    moneda = fase.priceCurrencyCode, periodoGratis = periodo, periodoCobro = fase.billingPeriod)
            }
        } finally {
            cargando = false
        }
    }

    /** «P14D», «P2W», «P1M» → días. */
    private fun diasDe(periodo: String): Int? {
        val m = Regex("^P(\\d+)([DWMY])$").find(periodo) ?: return null
        val n = m.groupValues[1].toInt()
        return when (m.groupValues[2]) { "D" -> n; "W" -> n * 7; "M" -> n * 30; else -> n * 365 }
    }

    /** Escucha las compras que no vienen de una compra hecha acá (y las que quedaron sin reconocer). */
    fun escuchar() {
        if (escuchando || ModoPrueba.activo || !esDuenio) return
        escuchando = true
        alcance.launch {
            runCatching {
                val c = conectado() ?: return@runCatching
                c.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build())
                    .purchasesList.filter { !it.isAcknowledged }.forEach { procesar(it) }
            }
        }
    }

    /** `listo`: la API ya la registró; la suscripción se vuelve a pedir (GET /v1/suscripcion). */
    sealed class Resultado {
        data object listo : Resultado()
        data object cancelada : Resultado()
        data object pendiente : Resultado()
    }

    suspend fun comprar(producto: Producto, inmobiliaria: String): Resultado {
        val act = actividad?.get() ?: return Resultado.cancelada
        val c = conectado() ?: throw ErrorApi.desconocido
        // Cambiar de plan o de ciclo reemplaza la suscripción vigente de esta inmobiliaria: si no, quedan dos cobrando.
        val anterior = vigente(c, inmobiliaria, salvo = producto.id)?.also { cargar(it.products) }
        val e = CompletableDeferred<Pair<BillingResult, List<Purchase>?>>().also { esperando = it }
        val armado = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(producto.detalle).setOfferToken(producto.oferta).build()))
            .setObfuscatedAccountId(inmobiliaria)
        if (anterior != null) {
            armado.setSubscriptionUpdateParams(BillingFlowParams.SubscriptionUpdateParams.newBuilder()
                .setOldPurchaseToken(anterior.purchaseToken)
                .setSubscriptionReplacementMode(modoDeCambio(anterior, producto))
                .build())
        }
        val params = armado.build()
        val inicio = c.launchBillingFlow(act, params)
        if (inicio.responseCode != BillingClient.BillingResponseCode.OK) { esperando = null; return Resultado.cancelada }
        val (r, compras) = e.await()
        esperando = null
        if (r.responseCode != BillingClient.BillingResponseCode.OK) return Resultado.cancelada
        val compra = compras?.firstOrNull { it.products.contains(producto.id) } ?: return Resultado.cancelada
        if (compra.purchaseState == Purchase.PurchaseState.PENDING) return Resultado.pendiente
        return if (procesar(compra)) Resultado.listo else Resultado.pendiente
    }

    /** La suscripción de Google Play vigente de esta inmobiliaria con otro producto (otro plan u otro ciclo). Sólo la
     *  de esta inmobiliaria: con la misma cuenta de Google se puede pagar más de una. */
    private suspend fun vigente(c: BillingClient, inmobiliaria: String, salvo: String): Purchase? {
        val compras = runCatching {
            c.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()).purchasesList
        }.getOrNull().orEmpty()
        return compras.firstOrNull {
            it.purchaseState == Purchase.PurchaseState.PURCHASED && it.accountIdentifiers?.obfuscatedAccountId == inmobiliaria && salvo !in it.products
        }
    }

    /** Subir (cuesta más por día): se cobra ya la diferencia y sigue la fecha de cobro (CHARGE_PRORATED_PRICE).
     *  Bajar o pasar a anual: lo pagado se acredita como tiempo del plan nuevo (WITH_TIME_PRORATION). Si no se sabe el
     *  precio del plan anterior, lo segundo, que Play acepta siempre. */
    private fun modoDeCambio(anterior: Purchase, nuevo: Producto): Int {
        val viejo = anterior.products.firstNotNullOfOrNull { productos[it] }
        val porDia = { p: Producto -> p.micros.toDouble() / (diasDe(p.periodoCobro) ?: 30) }
        return if (viejo != null && porDia(nuevo) > porDia(viejo)) BillingFlowParams.SubscriptionUpdateParams.ReplacementMode.CHARGE_PRORATED_PRICE
        else BillingFlowParams.SubscriptionUpdateParams.ReplacementMode.WITH_TIME_PRORATION
    }

    /** «Restaurar compras»: vuelve a mandar a la API lo que Google Play tiene vigente en esta cuenta. `true` si la API
     *  registró alguna (la suscripción se vuelve a pedir después). */
    suspend fun restaurar(): Boolean {
        val c = conectado() ?: throw ErrorApi.desconocido
        val r = c.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build())
        var alguna = false
        r.purchasesList.forEach { p -> if (runCatching { procesar(p) }.getOrDefault(false)) alguna = true }
        return alguna
    }

    @Serializable private data class Cuerpo(val producto: String, val token: String)

    /** Manda a la API una compra de Play y la reconoce (si no, Play la devuelve a los tres días). Lo que devuelve la API
     *  no se lee acá: que no se entienda la respuesta no puede dejar la compra sin reconocer. */
    private suspend fun procesar(compra: Purchase): Boolean {
        if (compra.purchaseState != Purchase.PurchaseState.PURCHASED) return false
        val api = api ?: return false
        val producto = compra.products.firstOrNull() ?: return false
        api.pedir("POST", "/v1/suscripcion/google", cuerpo = Api.json.encodeToString(Cuerpo(producto, compra.purchaseToken)).toByteArray())
        if (!compra.isAcknowledged) cliente?.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(compra.purchaseToken).build())
        return true
    }
}
