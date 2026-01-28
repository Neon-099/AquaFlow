package com.aquaflow

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast

import com.google.android.material.button.MaterialButton

class OrderFormPage : AppCompatActivity() {
    private var quantity = 1
    private var pricePerGallon = 25.0 // Example price

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.order_form)

        setupWaterTypeDropdown()
        setupQuantityControls()

        findViewById<MaterialButton>(R.id.btnPlaceOrder).setOnClickListener {
            validateAndSubmit()
        }
    }

    private fun setupWaterTypeDropdown() {
        val waterTypes = arrayOf("Purified (Slim)", "Purified (Round)", "Alkaline", "Distilled")
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, waterTypes)
        val autocomplete = findViewById<AutoCompleteTextView>(R.id.spinnerWaterType)

        autocomplete.setAdapter(adapter)
        autocomplete.setOnItemClickListener { _, _, position, _ ->
            // Change price based on selection
            pricePerGallon = when(position) {
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
        findViewById<TextView>(R.id.tvTotalPrice).text = String.format("₱ %.2f", total)
    }

    private fun validateAndSubmit() {
        val address = findViewById<EditText>(R.id.etAddress).text.toString()
        val type = findViewById<AutoCompleteTextView>(R.id.spinnerWaterType).text.toString()

        if (type.isEmpty()) {
            Toast.makeText(this, "Please select water type", Toast.LENGTH_SHORT).show()
            return
        }
        if (address.isEmpty()) {
            Toast.makeText(this, "Please enter delivery address", Toast.LENGTH_SHORT).show()
            return
        }

        // Proceed to Server Submission or Payment Page
        Toast.makeText(this, "Order Placed Successfully!", Toast.LENGTH_LONG).show()
        finish()
    }
}