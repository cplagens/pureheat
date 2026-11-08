/*
 * PureHeat – Copyright 2026 Christian Plagens
 * Licensed under the Apache License, Version 2.0 (see LICENSE.md).
 * SPDX-License-Identifier: Apache-2.0
 */
package de.cplagens.pureheat.data.support

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClient.ProductType
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import de.cplagens.pureheat.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * What the "buy me a coffee" button shows. [price] is null when there is nothing to buy: not
 * installed from Google Play, Play unavailable, product not set up yet, or still connecting.
 */
data class CoffeeState(
    /** Localised price from Google Play, e.g. "1,99 €". */
    val price: String? = null,
    /** Paid with a delayed method (e.g. cash at a shop); completes when Play confirms it. */
    val pending: Boolean = false,
    /** A coffee was bought in this session. */
    val thanked: Boolean = false,
)

/**
 * A voluntary tip through Google Play Billing: one consumable in-app product ([PRODUCT_ID],
 * price set in the Play Console: 1.99 EUR / 1.99 USD). It unlocks nothing, so there is no
 * server check – a purchase is consumed right away so it can be bought again. Purchases that
 * completed while the app was closed (pending payments) are consumed on the next [connect];
 * Play refunds unconsumed purchases after three days.
 */
class CoffeeBilling(context: Context) {
    private val _state = MutableStateFlow(CoffeeState())
    val state: StateFlow<CoffeeState> = _state.asStateFlow()

    private var product: ProductDetails? = null

    private val client = BillingClient.newBuilder(context.applicationContext)
        .setListener { result, purchases -> onPurchasesUpdated(result, purchases) }
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        // The library reconnects by itself after a dropped connection.
        .enableAutoServiceReconnection()
        .build()

    fun connect() {
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode != BillingResponseCode.OK) {
                    debug("setup: ${result.responseCode} ${result.debugMessage}")
                    return
                }
                queryProduct()
                consumeOpenPurchases()
            }

            override fun onBillingServiceDisconnected() = Unit
        })
    }

    /** Opens Google Play's purchase sheet; the result arrives in [onPurchasesUpdated]. */
    fun buy(activity: Activity) {
        val details = product ?: return
        val params = BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(details).build()
        val result = client.launchBillingFlow(activity, BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(params)).build())
        if (result.responseCode != BillingResponseCode.OK) debug("launch: ${result.responseCode} ${result.debugMessage}")
    }

    fun close() = client.endConnection()

    private fun queryProduct() {
        val product = QueryProductDetailsParams.Product.newBuilder()
            .setProductId(PRODUCT_ID)
            .setProductType(ProductType.INAPP)
            .build()
        val params = QueryProductDetailsParams.newBuilder().setProductList(listOf(product)).build()
        client.queryProductDetailsAsync(params) { result, details ->
            val found = details.productDetailsList.firstOrNull { it.productId == PRODUCT_ID }
            val price = found?.oneTimePurchaseOfferDetailsList?.firstOrNull()?.formattedPrice
            if (result.responseCode != BillingResponseCode.OK || found == null || price == null) {
                debug("product: ${result.responseCode} ${result.debugMessage}")
                return@queryProductDetailsAsync
            }
            this.product = found
            _state.update { it.copy(price = price) }
        }
    }

    private fun consumeOpenPurchases() {
        val params = QueryPurchasesParams.newBuilder().setProductType(ProductType.INAPP).build()
        client.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode == BillingResponseCode.OK) purchases.forEach(::handle)
        }
    }

    private fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) {
        when (result.responseCode) {
            BillingResponseCode.OK -> purchases.orEmpty().forEach(::handle)
            // A previous purchase was never consumed (e.g. the app was killed): finish it now.
            BillingResponseCode.ITEM_ALREADY_OWNED -> consumeOpenPurchases()
            BillingResponseCode.USER_CANCELED -> Unit
            else -> debug("purchase: ${result.responseCode} ${result.debugMessage}")
        }
    }

    private fun handle(purchase: Purchase) {
        if (PRODUCT_ID !in purchase.products) return
        when (purchase.purchaseState) {
            Purchase.PurchaseState.PURCHASED -> {
                val params = ConsumeParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()
                client.consumeAsync(params) { result, _ ->
                    if (result.responseCode == BillingResponseCode.OK) {
                        _state.update { it.copy(pending = false, thanked = true) }
                    } else {
                        debug("consume: ${result.responseCode} ${result.debugMessage}")
                    }
                }
            }
            Purchase.PurchaseState.PENDING -> _state.update { it.copy(pending = true) }
            else -> Unit
        }
    }

    private fun debug(message: String) {
        if (BuildConfig.DEBUG) Log.d("PureHeat", "Billing $message")
    }

    companion object {
        /** In-app product in the Play Console (one-time, consumable). */
        const val PRODUCT_ID = "coffee"
    }
}
