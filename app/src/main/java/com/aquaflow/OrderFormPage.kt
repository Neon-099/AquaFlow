package com.aquaflow

import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.ImageButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isVisible

import com.aquaflow.utils.CreateOrderPayload
import com.aquaflow.utils.OrderApi

import com.google.android.material.button.MaterialButton
import org.json.JSONObject

class OrderFormPage : AppCompatActivity() {
    companion object {
        private const val PRICE_PER_GALLON = 15.0
        private const val DELIVERY_FEE = 5.0
        private const val GCASH_VAT_FEE = 3.0
        private const val GCASH_PENDING_INTENT_PREF = "gcash_payment_intent_id"
        private const val GCASH_PENDING_ORDER_PREF = "gcash_pending_order_data"
    }

    override fun onResume() {
        super.onResume()
        maybeFinalizePendingGcashOrder()
    }

    private var quantity = 1
    private var pricePerGallon = PRICE_PER_GALLON
    private lateinit var placeOrderButton: MaterialButton
    private var isAutoFinalizingGcashOrder = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.order_form)

        setupAddressDropdown()
        findViewById<android.widget.RadioButton>(R.id.rbCod).isChecked = true
        setupWaterTypeDropdown()
        setupQuantityControls()
        setupPaymentMethodWatcher()

        placeOrderButton = findViewById(R.id.btnPlaceOrder)
        placeOrderButton.setOnClickListener {
            validateAndSubmit()
        }
    }

    private fun setupAddressDropdown() {
        val addressField = findViewById<AutoCompleteTextView>(R.id.etAddress)
        val addressOptions = resources.getStringArray(R.array.address_options)
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, addressOptions)
        addressField.setAdapter(adapter)
        addressField.keyListener = null
        addressField.setOnClickListener { addressField.showDropDown() }
        addressField.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                addressField.showDropDown()
            }
        }

        val prefs = getSharedPreferences("auth", MODE_PRIVATE)
        val savedAddress = prefs.getString("address", null)?.trim().orEmpty()
        if (savedAddress.isNotEmpty() && addressOptions.contains(savedAddress)) {
            addressField.setText(savedAddress, false)
        } else if (addressOptions.isNotEmpty()) {
            addressField.setText(addressOptions.first(), false)
        }
    }

    private fun setupWaterTypeDropdown() {
        val waterTypes = arrayOf("Gallon (Slim)", "Gallon (Round)")
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, waterTypes)
        val autocomplete = findViewById<AutoCompleteTextView>(R.id.spinnerWaterType)

        autocomplete.setAdapter(adapter)
        autocomplete.setOnItemClickListener { _, _, _, _ ->
            pricePerGallon = PRICE_PER_GALLON
            updateTotalPrice()
        }
    }

    private fun setupPaymentMethodWatcher() {
        findViewById<RadioGroup>(R.id.rgPaymentMethod).setOnCheckedChangeListener { _, _ ->
            updateTotalPrice()
        }
    }

    private fun setupQuantityControls() {
        val tvQty = findViewById<TextView>(R.id.tvQuantity)

        findViewById<ImageButton>(R.id.btnPlus).setOnClickListener {
            quantity++
            tvQty.text = quantity.toString()
            updateTotalPrice()
        }

        findViewById<ImageButton>(R.id.btnMinus).setOnClickListener {
            if (quantity > 1) {
                quantity--
                tvQty.text = quantity.toString()
                updateTotalPrice()
            }
        }

        updateTotalPrice()
    }

    private fun updateTotalPrice() {
        val subtotal = quantity * pricePerGallon
        val selectedPaymentId = findViewById<RadioGroup>(R.id.rgPaymentMethod).checkedRadioButtonId
        val vatFee = if (selectedPaymentId == R.id.rbGcash) GCASH_VAT_FEE else 0.0
        val totalPrice = subtotal + DELIVERY_FEE + vatFee

        //INIT THE TEXT
        val gcashTextView = findViewById<TextView>(R.id.tvGcashMOP)
        if(selectedPaymentId === R.id.rbGcash){
            gcashTextView.isVisible = true
            gcashTextView.text = String.format("₱ %.2f", vatFee)
        }else{
            gcashTextView.isVisible = false
        }
        findViewById<TextView>(R.id.tvTotalPrice).text = String.format("₱ %.2f", totalPrice)
    }

    private fun validateAndSubmit() {
        val address = findViewById<AutoCompleteTextView>(R.id.etAddress).text.toString().trim()
        val type = findViewById<AutoCompleteTextView>(R.id.spinnerWaterType).text.toString().trim()
        val selectedPaymentId = findViewById<RadioGroup>(R.id.rgPaymentMethod).checkedRadioButtonId

        if (type.isEmpty()) {
            Toast.makeText(this, "Please select water type", Toast.LENGTH_SHORT).show(); return
        }
        if (address.isEmpty()) {
            Toast.makeText(this, "Please enter delivery address", Toast.LENGTH_SHORT).show(); return
        }
        if (selectedPaymentId == -1) {
            Toast.makeText(this, "Please select payment method", Toast.LENGTH_SHORT).show(); return
        }

        val paymentMethod = if (selectedPaymentId == R.id.rbGcash) "GCASH" else "COD"
        val gallonType = if (type.contains("Slim", true)) "SLIM" else "ROUND"
        val subtotal = quantity * pricePerGallon
        val vatFee = if (paymentMethod == "GCASH") GCASH_VAT_FEE else 0.0
        val totalAmount = subtotal + DELIVERY_FEE + vatFee

        val prefs = getSharedPreferences("auth", MODE_PRIVATE)
        val token = prefs.getString("token", null)
        if (token.isNullOrBlank()) {
            Toast.makeText(this, "Session expired. Please log in again.", Toast.LENGTH_LONG).show()
            return
        }
        val phone = prefs.getString("phone", null)?.trim().orEmpty()
        if (paymentMethod == "GCASH" && phone.isBlank()) {
            Toast.makeText(this, "Phone number is required for GCash payments. Please update your profile.", Toast.LENGTH_LONG).show()
            startActivity(Intent(this, ProfilePage::class.java))
            return
        }

        val payload = CreateOrderPayload(
            waterQuantity = quantity,
            gallonType = gallonType,
            totalAmount = totalAmount,
            paymentMethod = paymentMethod
        )

        placeOrderButton.isEnabled = false

        if (paymentMethod == "GCASH") {
            val pendingIntentId = prefs.getString(GCASH_PENDING_INTENT_PREF, null)
            if (pendingIntentId.isNullOrBlank()) {
                OrderApi.prepareGcashPayment(token, payload) { result ->
                    runOnUiThread {
                        placeOrderButton.isEnabled = true
                        result.onSuccess { prep ->
                            prefs.edit()
                                .putString(GCASH_PENDING_INTENT_PREF, prep.paymentIntentId)
                                .putString(
                                    GCASH_PENDING_ORDER_PREF,
                                    buildPendingOrderJson(quantity, gallonType, totalAmount)
                                )
                                .apply()
                            Toast.makeText(
                                this,
                                "Complete GCash payment, then tap Place Order again.",
                                Toast.LENGTH_LONG
                            ).show()
                            openPaymentUrl(prep.checkoutUrl)
                        }.onFailure {
                            Toast.makeText(this, it.message ?: "Failed to start GCash payment", Toast.LENGTH_LONG)
                                .show()
                        }
                    }
                }
                return
            }
        }

        val finalPayload = if (paymentMethod == "GCASH") {
            payload.copy(gcashPaymentIntentId = prefs.getString(GCASH_PENDING_INTENT_PREF, null))
        } else {
            payload
        }

        OrderApi.createOrder(token, finalPayload) { result ->
            runOnUiThread {
                placeOrderButton.isEnabled = true
                result.onSuccess { (order, _) ->
                    if (paymentMethod == "GCASH") {
                        clearPendingGcashOrder(prefs)
                    }
                    Toast.makeText(
                        this,
                        "Order ${order.orderCode ?: order.id} placed.",
                        Toast.LENGTH_LONG
                    ).show()
                    finish()
                }.onFailure {
                    Toast.makeText(this, it.message ?: "Failed to place order", Toast.LENGTH_LONG)
                        .show()
                }
            }
        }
    }

    private fun openPaymentUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        startActivity(intent)
    }

    private fun maybeFinalizePendingGcashOrder() {
        if (isAutoFinalizingGcashOrder) return
        val prefs = getSharedPreferences("auth", MODE_PRIVATE)
        val pendingIntentId = prefs.getString(GCASH_PENDING_INTENT_PREF, null)
        val pendingOrderJson = prefs.getString(GCASH_PENDING_ORDER_PREF, null)
        if (pendingIntentId.isNullOrBlank() || pendingOrderJson.isNullOrBlank()) return

        val pendingOrder = parsePendingGcashOrder(pendingOrderJson)
        if (pendingOrder == null) {
            clearPendingGcashOrder(prefs)
            return
        }

        val token = prefs.getString("token", null)
        if (token.isNullOrBlank()) {
            Toast.makeText(this, "Session expired. Please log in again.", Toast.LENGTH_LONG).show()
            return
        }
        val phone = prefs.getString("phone", null)?.trim().orEmpty()
        if (phone.isBlank()) {
            Toast.makeText(this, "Phone number is required for GCash payments. Please update your profile.", Toast.LENGTH_LONG).show()
            startActivity(Intent(this, ProfilePage::class.java))
            return
        }

        isAutoFinalizingGcashOrder = true
        placeOrderButton.isEnabled = false
        Toast.makeText(this, "Finalizing pending GCash payment...", Toast.LENGTH_SHORT).show()

        val payload = CreateOrderPayload(
            waterQuantity = pendingOrder.quantity,
            gallonType = pendingOrder.gallonType,
            totalAmount = pendingOrder.totalAmount,
            paymentMethod = "GCASH",
            gcashPaymentIntentId = pendingIntentId
        )

        OrderApi.createOrder(token, payload) { result ->
            runOnUiThread {
                placeOrderButton.isEnabled = true
                isAutoFinalizingGcashOrder = false
                result.onSuccess { (order, _) ->
                    clearPendingGcashOrder(prefs)
                    Toast.makeText(
                        this,
                        "Order ${order.orderCode ?: order.id} placed.",
                        Toast.LENGTH_LONG
                    ).show()
                    finish()
                }.onFailure {
                    Toast.makeText(
                        this,
                        it.message ?: "Failed to finalize GCash order. Tap Place Order to try again.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun buildPendingOrderJson(quantity: Int, gallonType: String, totalAmount: Double): String {
        return JSONObject().apply {
            put("quantity", quantity)
            put("gallon_type", gallonType)
            put("total_amount", totalAmount)
        }.toString()
    }

    private fun parsePendingGcashOrder(json: String): PendingGcashOrder? {
        return try {
            val root = JSONObject(json)
            val qty = root.optInt("quantity", -1).takeIf { it > 0 } ?: return null
            val gallonType = root.optString("gallon_type", "ROUND").takeIf { it.isNotBlank() } ?: "ROUND"
            val total = root.optDouble("total_amount", -1.0).takeIf { it >= 0 } ?: return null
            PendingGcashOrder(qty, gallonType, total)
        } catch (e: Exception) {
            null
        }
    }

    private fun clearPendingGcashOrder(prefs: SharedPreferences) {
        prefs.edit()
            .remove(GCASH_PENDING_INTENT_PREF)
            .remove(GCASH_PENDING_ORDER_PREF)
            .apply()
    }

    private data class PendingGcashOrder(val quantity: Int, val gallonType: String, val totalAmount: Double)
}
