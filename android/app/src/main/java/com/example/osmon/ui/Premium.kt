// Premium.kt: Premium als Einmalkauf (Google Play Billing). Gegenstück: Premium.swift (StoreKit 2).
//
// Ein Produkt „osmon_premium“ (Typ: einmalig, nicht verbrauchbar), in der Play Console anzulegen.
// Kaufen und Wiederherstellen gibt es nur im Eltern-Bereich (hinter dem PIN). Premium schaltet frei:
// Zauberhut, Piratenhut, Monokel (Kleiderschrank) und das Spiel „Glühwürmchen“. Alles andere bleibt kostenlos,
// keine Werbung, kein Abo. Der Kaufstatus liegt nur auf dem Gerät (prefs) und wird beim Start mit Play abgeglichen.

package com.example.osmon.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
import com.example.osmon.PetSync

object PremiumStore {
    const val PRODUCT = "osmon_premium"
    private const val KEY = "premium.owned"
    private const val DEMO = "premium.demo"         // Bildschirmfotos/Test: Premium ohne Kauf

    fun isPremium(ctx: Context) = PetSync.prefs(ctx).let { it.getBoolean(KEY, false) || it.getBoolean(DEMO, false) }
    private fun set(ctx: Context, on: Boolean) {
        if (isPremium(ctx) == on) return
        PetSync.prefs(ctx).edit().putBoolean(KEY, on).apply()
        ExtrasEvents.bump()
    }

    private var client: BillingClient? = null
    private var details: ProductDetails? = null
    var onStatus: (String) -> Unit = {}

    private fun client(ctx: Context): BillingClient {
        client?.let { return it }
        val app = ctx.applicationContext
        return BillingClient.newBuilder(app)
            .setListener { r, list -> if (r.responseCode == BillingClient.BillingResponseCode.OK) list?.forEach { handle(app, it) }
                else if (r.responseCode == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) restore(app)
                else if (r.responseCode != BillingClient.BillingResponseCode.USER_CANCELED) onStatus("Kauf nicht möglich (${r.responseCode}).") }
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .build().also { client = it }
    }

    private fun connected(ctx: Context, then: (BillingClient) -> Unit) {
        val c = client(ctx)
        if (c.isReady) { then(c); return }
        c.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(r: BillingResult) {
                if (r.responseCode == BillingClient.BillingResponseCode.OK) then(c) else onStatus("Google Play ist gerade nicht erreichbar.")
            }
            override fun onBillingServiceDisconnected() {}
        })
    }

    private fun handle(ctx: Context, p: Purchase) {
        if (PRODUCT !in p.products) return
        when (p.purchaseState) {
            Purchase.PurchaseState.PURCHASED -> {
                // Erst nach Bestätigung bei Google freischalten: unbestätigte Käufe erstattet Google nach 3 Tagen.
                // Schlägt die Bestätigung fehl, versucht es restore() beim nächsten Start bzw. „Kauf wiederherstellen“ erneut.
                if (p.isAcknowledged) { set(ctx, true); onStatus("Danke! Premium ist freigeschaltet.") }
                else connected(ctx) { c ->
                    c.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(p.purchaseToken).build()) { r ->
                        if (r.responseCode == BillingClient.BillingResponseCode.OK) { set(ctx, true); onStatus("Danke! Premium ist freigeschaltet.") }
                        else onStatus("Kauf eingegangen, Bestätigung bei Google noch offen. Später „Kauf wiederherstellen“ tippen.")
                    }
                }
            }
            Purchase.PurchaseState.PENDING -> onStatus("Kauf wartet noch auf die Zahlung.")
            else -> {}
        }
    }

    /** Preis laden (z. B. „3,99 €“). */
    fun loadPrice(ctx: Context, done: (String?) -> Unit) = connected(ctx) { c ->
        val q = QueryProductDetailsParams.newBuilder().setProductList(listOf(
            QueryProductDetailsParams.Product.newBuilder().setProductId(PRODUCT).setProductType(BillingClient.ProductType.INAPP).build())).build()
        c.queryProductDetailsAsync(q) { _, list -> details = list.firstOrNull(); done(details?.oneTimePurchaseOfferDetails?.formattedPrice) }
    }

    fun buy(activity: Activity) = connected(activity) { c ->
        val d = details ?: run { onStatus("Produkt noch nicht geladen – bitte gleich noch einmal."); loadPrice(activity) {}; return@connected }
        activity.runOnUiThread {
            c.launchBillingFlow(activity, BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(
                BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(d).build())).build())
        }
    }

    /** Käufe mit Play abgleichen (Start, „Kauf wiederherstellen“). Rückerstattet = wieder aus. */
    fun restore(ctx: Context, report: Boolean = false) = connected(ctx) { c ->
        c.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()) { r, list ->
            if (r.responseCode != BillingClient.BillingResponseCode.OK) return@queryPurchasesAsync
            val owned = list.any { PRODUCT in it.products && it.purchaseState == Purchase.PurchaseState.PURCHASED }
            list.forEach { handle(ctx, it) }
            if (!owned) set(ctx, false)
            if (report) onStatus(if (owned) "Premium wiederhergestellt." else "Kein Kauf gefunden.")
        }
    }
}

private fun Context.activity(): Activity? = when (this) { is Activity -> this; is ContextWrapper -> baseContext.activity(); else -> null }

/** Karte im Eltern-Bereich: was Premium enthält, Preis, Kaufen, Wiederherstellen. */
@Composable
fun PremiumCard() {
    val ctx = LocalContext.current
    var price by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    val v = ExtrasEvents.version.intValue
    val owned = remember(v) { PremiumStore.isPremium(ctx) }
    LaunchedEffect(Unit) {
        PremiumStore.onStatus = { status = it }
        if (!owned) PremiumStore.loadPrice(ctx) { price = it }
    }
    Surface(shape = RoundedCornerShape(16.dp), color = Color(0xFFF3E8FF), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("💎", style = MaterialTheme.typography.headlineSmall); Spacer(Modifier.width(10.dp))
                Text(if (owned) "Premium ist freigeschaltet" else "Premium – einmal kaufen, für immer",
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = Color(0xFF3B1E6E))
            }
            Text("Enthält: 🧙 Zauberhut, 🏴‍☠️ Piratenhut, 🧐 Monokel und das Spiel ✨ Glühwürmchen. Kein Abo, keine Werbung. " +
                "Alles andere in OS-Mon bleibt kostenlos.", style = MaterialTheme.typography.bodySmall, color = Color(0xFF4A3470))
            if (!owned) Button(onClick = { ctx.activity()?.let { PremiumStore.buy(it) } }, enabled = price != null) {
                Text(price?.let { "Für $it kaufen" } ?: "Preis wird geladen …")
            }
            OutlinedButton(onClick = { PremiumStore.restore(ctx, report = true) }) { Text("Kauf wiederherstellen") }
            status?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = Color(0xFF3B1E6E)) }
        }
    }
}
