package com.aquaflow

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.ImageButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast

import com.aquaflow.utils.CreateOrderPayload
import com.aquaflow.utils.OrderApi

import com.google.android.material.button.MaterialButton

class OrderFormPage : AppCompatActivity() {
    private var quantity = 1
    private var pricePerGallon = 15.0 // Example price

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.order_form)

        prefillCustomerAddress()
        findViewById<android.widget.RadioButton>(R.id.rbCod).isChecked = true
        setupWaterTypeDropdown()
        setupQuantityControls()

        findViewById<MaterialButton>(R.id.btnPlaceOrder).setOnClickListener {
            validateAndSubmit()
        }
    }

    private fun prefillCustomerAddress() {
        val prefs = getSharedPreferences("auth", MODE_PRIVATE)
        val savedAddress = prefs.getString("address", null)?.trim().orEmpty()
        if (savedAddress.isNotEmpty()) {
            findViewById<EditText>(R.id.etAddress).setText(savedAddress)
        }
    }

    private fun setupWaterTypeDropdown() {
        val waterTypes = arrayOf("Gallon (Slim)", "Gallon (Round)")
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, waterTypes)
        val autocomplete = findViewById<AutoCompleteTextView>(R.id.spinnerWaterType)

        autocomplete.setAdapter(adapter)
        autocomplete.setOnItemClickListener { _, _, position, _ ->
            // Change price based on selection
            pricePerGallon = when (position) {
                2 -> 45.0 // Alkaline is more expensive
                else -> 25.0
            }
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

        val total = quantity * pricePerGallon
        val totalPrice = total + 5
        findViewById<TextView>(R.id.tvTotalPrice).text = String.format("₱ %.2f", totalPrice)
    }

    private fun validateAndSubmit() {
        val address = findViewById<EditText>(R.id.etAddress).text.toString().trim()
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

        val prefs = getSharedPreferences("auth", MODE_PRIVATE)
        val token = prefs.getString("token", null)
        if (token.isNullOrBlank()) {
            Toast.makeText(this, "Session expired. Please log in again.", Toast.LENGTH_LONG).show()
            return
        }

        val payload = CreateOrderPayload(
            waterQuantity = quantity,
            gallonType = gallonType,
            totalAmount = quantity * pricePerGallon,
            paymentMethod = paymentMethod
        )

        findViewById<MaterialButton>(R.id.btnPlaceOrder).isEnabled = false
        OrderApi.createOrder(token, payload) { result ->
            runOnUiThread {
                findViewById<MaterialButton>(R.id.btnPlaceOrder).isEnabled = true
                result.onSuccess { (order, gcashUrl) ->
                    if (paymentMethod == "GCASH" && !gcashUrl.isNullOrBlank()) {
                        Toast.makeText(
                            this,
                            "Order created. Continue GCASH payment.",
                            Toast.LENGTH_LONG
                        ).show()
                        // open your WebView/payment page with gcashUrl
                    } else {
                        Toast.makeText(
                            this,
                            "Order ${order.orderCode ?: order.id} placed.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    finish()
                }.onFailure {
                    Toast.makeText(this, it.message ?: "Failed to place order", Toast.LENGTH_LONG)
                        .show()
                }
            }
        }
    }
}