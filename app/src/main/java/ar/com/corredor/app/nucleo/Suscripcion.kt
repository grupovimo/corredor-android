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

/** La suscripción de la inmobiliaria (GET /v1/suscripcion): tres planes, mensual o anual, 14 días gratis y sólo lectura
 *  al vencer. En el teléfono se paga con Google Play (Play Billing, los mismos ids que la App Store); en la web, con
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
    val soloLectura: Boolean = false,
    val enPrueba: Boolean = false,
    val agentes: Agentes = Agentes(),
    val planes: List<Plan> = emptyList(),
    val mercadoPago: Boolean = false,
    val cobros: List<Cobro> = emptyList(),
) {
    @Serializable
    data class Plan(
        val id: String,
        val nombre: String,
        val resumen: String = "",
        val agentes: Int = 0,
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
    data class Cobro(val id: String, val medio: String = "", val detalle: String = "", val importe: Double? = null, val moneda: String? = null, val creadoEn: FechaApi = Instant.EPOCH)

    val planActual: Plan? get() = planes.firstOrNull { it.id == plan }

    /** «Prueba gratis · 9 días», «Plan Profesional · anual», «Sólo lectura». */
    val nombreEstado: String get() = when (estado) {
        "prueba" -> if (diasRestantes == 1) "Prueba gratis · último día" else "Prueba gratis · $diasRestantes días"
        "activa" -> "Plan ${planActual?.nombre ?: ""} · ${if (ciclo == "anio") "anual" else "mensual"}"
        "baja" -> "Plan ${planActual?.nombre ?: ""} · sin renovar"
        else -> "Sólo lectura"
    }

    /** Hay que avisarle arriba (Inicio, Más): la última semana de prueba, sin renovar o vencida. */
    val pideAtencion: Boolean get() = estado == "vencida" || (estado == "prueba" && diasRestantes <= 7) || (estado == "baja" && diasRestantes <= 7)

    /** Se paga con Mercado Pago desde la web (no se puede cambiar desde el teléfono). */
    val conMercadoPago: Boolean get() = medio == "mercadopago" && renueva && !soloLectura
}

/** Un producto de Google Play (el Product de StoreKit): su id, el precio como lo escribe Play y los días gratis de la
 *  oferta de entrada, si tiene. */
data class Producto(val id: String, val displayPrice: String, val micros: Long, val oferta: String, val diasGratis: Int?, val detalle: ProductDetails)

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

    private var cliente: BillingClient? = null
    private var esperando: CompletableDeferred<Pair<BillingResult, List<Purchase>?>>? = null
    private var escuchando = false
    private val alcance = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private suspend fun conectado(): BillingClient? {
        val ctx = contexto ?: return null
        val c = cliente ?: BillingClient.newBuilder(ctx.applicationContext)
            .setListener { r, compras ->
                val e = esperando
                if (e != null && !e.isCompleted) e.complete(r to compras)
                // Compras que llegan solas (pendientes que se aprobaron): se registran igual.
                else if (r.responseCode == BillingClient.BillingResponseCode.OK) alcance.launch { compras?.forEach { runCatching { procesar(it) } } }
            }
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .enableAutoServiceReconnection()
            .build().also { cliente = it }
        if (c.isReady) return c
        val listo = CompletableDeferred<Boolean>()
        c.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(r: BillingResult) { listo.complete(r.responseCode == BillingClient.BillingResponseCode.OK) }
            override fun onBillingServiceDisconnected() { if (!listo.isCompleted) listo.complete(false) }
        })
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
                val o = d.subscriptionOfferDetails?.firstOrNull() ?: return@forEach
                val fases = o.pricingPhases.pricingPhaseList
                val fase = fases.lastOrNull() ?: return@forEach
                val gratis = fases.firstOrNull { it.priceAmountMicros == 0L }?.billingPeriod?.let { diasDe(it) }
                productos[d.productId] = Producto(d.productId, fase.formattedPrice, fase.priceAmountMicros, o.offerToken, gratis, d)
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
        if (escuchando || ModoPrueba.activo) return
        escuchando = true
        alcance.launch {
            runCatching {
                val c = conectado() ?: return@runCatching
                c.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build())
                    .purchasesList.filter { !it.isAcknowledged }.forEach { procesar(it) }
            }
        }
    }

    sealed class Resultado {
        data class listo(val s: Suscripcion) : Resultado()
        data object cancelada : Resultado()
        data object pendiente : Resultado()
    }

    suspend fun comprar(producto: Producto, inmobiliaria: String): Resultado {
        val act = actividad?.get() ?: return Resultado.cancelada
        val c = conectado() ?: throw ErrorApi.desconocido
        val e = CompletableDeferred<Pair<BillingResult, List<Purchase>?>>().also { esperando = it }
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(producto.detalle).setOfferToken(producto.oferta).build()))
            .setObfuscatedAccountId(inmobiliaria)
            .build()
        val inicio = c.launchBillingFlow(act, params)
        if (inicio.responseCode != BillingClient.BillingResponseCode.OK) { esperando = null; return Resultado.cancelada }
        val (r, compras) = e.await()
        esperando = null
        if (r.responseCode != BillingClient.BillingResponseCode.OK) return Resultado.cancelada
        val compra = compras?.firstOrNull { it.products.contains(producto.id) } ?: return Resultado.cancelada
        if (compra.purchaseState == Purchase.PurchaseState.PENDING) return Resultado.pendiente
        val s = procesar(compra) ?: return Resultado.pendiente
        return Resultado.listo(s)
    }

    /** «Restaurar compras»: vuelve a mandar a la API lo que Google Play tiene vigente en esta cuenta. */
    suspend fun restaurar(): Suscripcion? {
        val c = conectado() ?: return null
        val r = c.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build())
        var ultima: Suscripcion? = null
        r.purchasesList.forEach { p -> runCatching { procesar(p) }.getOrNull()?.let { ultima = it } }
        return ultima
    }

    @Serializable private data class Cuerpo(val producto: String, val token: String)

    /** Manda a la API una compra de Play y la reconoce (si no, Play la devuelve a los tres días). */
    private suspend fun procesar(compra: Purchase): Suscripcion? {
        if (compra.purchaseState != Purchase.PurchaseState.PURCHASED) return null
        val api = api ?: return null
        val producto = compra.products.firstOrNull() ?: return null
        val s: Suscripcion = api.enviar("POST", "/v1/suscripcion/google", Cuerpo(producto, compra.purchaseToken))
        if (!compra.isAcknowledged) cliente?.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(compra.purchaseToken).build())
        return s
    }
}
