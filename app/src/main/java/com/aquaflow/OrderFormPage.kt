package com.aquaflow

import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.ImageButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast

import com.aquaflow.utils.CreateOrderPayload
import com.aquaflow.utils.OrderApi

import com.google.android.material.button.MaterialButton

class OrderFormPage : AppCompatActivity() {
    companion object {
        private const val PRICE_PER_GALLON = 15.0
        private const val DELIVERY_FEE = 5.0
        private const val GCASH_VAT_FEE = 3.0
        private const val GCASH_PENDING_INTENT_PREF = "gcash_payment_intent_id"
    }

    private var quantity = 1
    private var pricePerGallon = PRICE_PER_GALLON

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.order_form)

        setupAddressDropdown()
        findViewById<android.widget.RadioButton>(R.id.rbCod).isChecked = true
        setupWaterTypeDropdown()
        setupQuantityControls()
        setupPaymentMethodWatcher()

        findViewById<MaterialButton>(R.id.btnPlaceOrder).setOnClickListener {
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

        val payload = CreateOrderPayload(
            waterQuantity = quantity,
            gallonType = gallonType,
            totalAmount = totalAmount,
            paymentMethod = paymentMethod
        )

        val placeOrderButton = findViewById<MaterialButton>(R.id.btnPlaceOrder)
        placeOrderButton.isEnabled = false

        if (paymentMethod == "GCASH") {
            val pendingIntentId = prefs.getString(GCASH_PENDING_INTENT_PREF, null)
            if (pendingIntentId.isNullOrBlank()) {
                OrderApi.prepareGcashPayment(token, payload) { result ->
                    runOnUiThread {
                        placeOrderButton.isEnabled = true
                        result.onSuccess { prep ->
                            prefs.edit().putString(GCASH_PENDING_INTENT_PREF, prep.paymentIntentId).apply()
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
                        prefs.edit().remove(GCASH_PENDING_INTENT_PREF).apply()
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
}
