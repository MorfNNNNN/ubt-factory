package ru.ubt.familyshield.core.billing

import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import ru.rustore.sdk.core.tasks.Task
import ru.rustore.sdk.pay.RuStorePayClient
import ru.rustore.sdk.pay.model.AppUserId
import ru.rustore.sdk.pay.model.PreferredPurchaseType
import ru.rustore.sdk.pay.model.Product
import ru.rustore.sdk.pay.model.ProductId
import ru.rustore.sdk.pay.model.ProductPurchase
import ru.rustore.sdk.pay.model.ProductPurchaseParams
import ru.rustore.sdk.pay.model.ProductPurchaseStatus
import ru.rustore.sdk.pay.model.ProductType
import ru.rustore.sdk.pay.model.PurchaseAvailabilityResult
import ru.rustore.sdk.pay.model.RuStorePaymentException
import ru.rustore.sdk.pay.model.SdkTheme
import ru.rustore.sdk.pay.model.SubscriptionPurchase
import ru.rustore.sdk.pay.model.SubscriptionPurchaseStatus
import ru.ubt.familyshield.BuildConfig
import ru.ubt.familyshield.core.net.ApiClient
import ru.ubt.familyshield.core.prefs.SettingsStore
import ru.ubt.familyshield.shared.billing.Entitlement
import ru.ubt.familyshield.shared.billing.EntitlementToken
import ru.ubt.familyshield.shared.billing.Feature
import ru.ubt.familyshield.shared.billing.PlanLimits
import ru.ubt.familyshield.shared.billing.Products
import ru.ubt.familyshield.shared.billing.Tier
import ru.ubt.familyshield.shared.crypto.B64
import ru.ubt.familyshield.shared.crypto.Signatures
import ru.ubt.familyshield.shared.protocol.VerifyPurchaseRequest
import java.security.PublicKey
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * The family's current plan. Only server-signed tokens count (verified offline with the public key
 * compiled into the app), so a tampered client cannot grant itself premium features.
 */
class EntitlementRepository(
    private val settings: SettingsStore,
    private val api: ApiClient,
    scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val publicKey: PublicKey? = BuildConfig.ENTITLEMENT_PUBLIC_KEY.takeIf { it.isNotBlank() }
        ?.let { runCatching { Signatures.publicKeyFromX509(B64.decode(it)) }.getOrNull() }

    private fun decode(token: String?, devPremium: Boolean): Entitlement {
        if (devPremium) return Entitlement(tier = Tier.FAMILY_MAX, issuedAt = clock())
        val key = publicKey ?: return Entitlement.FREE
        return token?.let { EntitlementToken.verify(it, key) } ?: Entitlement.FREE
    }

    val entitlement: StateFlow<Entitlement> = settings.state.map { decode(it.entitlementToken, it.devPremium) }
        .stateIn(scope, SharingStarted.Eagerly, decode(settings.current.entitlementToken, settings.current.devPremium))

    val current: Entitlement get() = entitlement.value

    fun has(feature: Feature): Boolean = current.has(feature, clock())

    fun limits(): PlanLimits = current.limits(clock())

    val tier: Tier get() = current.effectiveTier(clock())

    suspend fun refresh() {
        if (settings.current.deviceToken == null) return
        runCatching { api.entitlement() }.onSuccess { settings.setEntitlementToken(it) }
    }

    suspend fun store(token: String) = settings.setEntitlementToken(token)

    /** Free users get a limited number of manual checks per day. Returns false when the limit is reached. */
    suspend fun consumeManualCheck(today: String): Boolean {
        val limit = limits().manualChecksPerDay
        if (limit == Int.MAX_VALUE) return true
        val s = settings.current
        val used = if (s.manualCheckDate == today) s.manualCheckCount else 0
        if (used >= limit) return false
        settings.setManualChecks(today, used + 1)
        return true
    }

    fun manualChecksLeft(today: String): Int {
        val limit = limits().manualChecksPerDay
        if (limit == Int.MAX_VALUE) return Int.MAX_VALUE
        val s = settings.current
        return (limit - if (s.manualCheckDate == today) s.manualCheckCount else 0).coerceAtLeast(0)
    }
}

data class StoreProduct(
    val id: String,
    val title: String,
    val description: String,
    val priceLabel: String,
    val priceKopecks: Int?,
    val isSubscription: Boolean,
)

sealed class PurchaseOutcome {
    data object Success : PurchaseOutcome()
    data object Cancelled : PurchaseOutcome()
    data class Pending(val message: String) : PurchaseOutcome()
    data class Failed(val message: String) : PurchaseOutcome()
}

private suspend fun <T> Task<T>.awaitResult(): T = suspendCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
}

/** RuStore Pay SDK wrapper: products, purchase, restore, then server verification. */
class BillingManager(
    private val settings: SettingsStore,
    private val api: ApiClient,
    private val entitlements: EntitlementRepository,
) {
    private val tag = "Billing"

    private val client: RuStorePayClient? by lazy { runCatching { RuStorePayClient.instance }.onFailure { Log.w(tag, "Pay SDK unavailable", it) }.getOrNull() }

    /** Fallback prices shown before the store answers (the store's price always wins). */
    val fallbackPrices: Map<String, String> = mapOf(
        Products.PREMIUM_MONTH to "349 ₽/мес",
        Products.PREMIUM_YEAR to "2 490 ₽/год",
        Products.FAMILY_MAX_MONTH to "549 ₽/мес",
        Products.FAMILY_MAX_YEAR to "3 990 ₽/год",
        Products.PREMIUM_LIFETIME to "6 990 ₽",
        Products.PACK_ELDER to "1 490 ₽",
        Products.PACK_KIDS to "1 490 ₽",
    )

    suspend fun isAvailable(): Boolean {
        val c = client ?: return false
        return runCatching { c.getPurchaseInteractor().getPurchaseAvailability().awaitResult() is PurchaseAvailabilityResult.Available }.getOrDefault(false)
    }

    suspend fun products(): List<StoreProduct> {
        val c = client ?: return emptyList()
        return runCatching {
            c.getProductInteractor().getProducts(Products.all.map { ProductId(it) }).awaitResult().map { it.toStore() }
        }.onFailure { Log.w(tag, "getProducts failed", it) }.getOrDefault(emptyList())
    }

    private fun Product.toStore() = StoreProduct(
        id = productId.value,
        title = title.value,
        description = description?.value.orEmpty(),
        priceLabel = amountLabel.value,
        priceKopecks = price?.value,
        isSubscription = type == ProductType.SUBSCRIPTION,
    )

    suspend fun purchase(productId: String): PurchaseOutcome {
        val c = client ?: return PurchaseOutcome.Failed("Оплата через RuStore недоступна на этом устройстве")
        val params = ProductPurchaseParams(
            productId = ProductId(productId),
            appUserId = settings.current.deviceId?.let { AppUserId(it) },
        )
        return try {
            val result = c.getPurchaseInteractor().purchase(params, PreferredPurchaseType.ONE_STEP, SdkTheme.LIGHT, null).awaitResult()
            verify(productId, result.purchaseId.value, result.invoiceId?.value, result.sandbox)
        } catch (e: RuStorePaymentException.ProductPurchaseCancelled) {
            PurchaseOutcome.Cancelled
        } catch (e: RuStorePaymentException.ProductPurchaseException) {
            PurchaseOutcome.Failed("Оплата не прошла. Попробуйте ещё раз или выберите другой способ оплаты.")
        } catch (e: Exception) {
            Log.w(tag, "purchase failed", e)
            PurchaseOutcome.Failed(e.message ?: "Не удалось оформить покупку")
        }
    }

    private suspend fun verify(productId: String, purchaseId: String, invoiceId: String?, sandbox: Boolean): PurchaseOutcome = try {
        val token = api.verifyPurchase(VerifyPurchaseRequest(productId, purchaseId, invoiceId, sandbox = sandbox))
        entitlements.store(token)
        PurchaseOutcome.Success
    } catch (e: Exception) {
        Log.w(tag, "verification failed", e)
        PurchaseOutcome.Pending("Оплата прошла, подтверждаем покупку. Если доступ не открылся, нажмите «Восстановить покупки».")
    }

    /** Re-verifies every active purchase (new phone, reinstall, or a delayed confirmation). */
    suspend fun restore(): Int {
        val c = client ?: return 0
        val purchases = runCatching { c.getPurchaseInteractor().getPurchases().awaitResult() }.getOrElse {
            Log.w(tag, "getPurchases failed", it)
            return 0
        }
        var restored = 0
        for (p in purchases) {
            val (productId, active) = when (p) {
                is SubscriptionPurchase -> p.productId.value to (p.status == SubscriptionPurchaseStatus.ACTIVE || p.status == SubscriptionPurchaseStatus.PAUSED)
                is ProductPurchase -> p.productId.value to (p.status == ProductPurchaseStatus.CONFIRMED || p.status == ProductPurchaseStatus.PAID)
                else -> continue
            }
            if (!active || productId !in Products.all) continue
            if (verify(productId, p.purchaseId.value, p.invoiceId?.value, p.sandbox) == PurchaseOutcome.Success) restored++
        }
        entitlements.refresh()
        return restored
    }

    /** Must be called from the activity that handles the payment deep link (bank apps, SBP). */
    fun handleIntent(intent: Intent) {
        runCatching { client?.getIntentInteractor()?.proceedIntent(intent, SdkTheme.LIGHT) }
    }
}
