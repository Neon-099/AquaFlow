package com.aquaflow

import android.content.Intent
import android.os.Bundle
import android.view.View
import com.google.android.material.button.MaterialButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.aquaflow.utils.AuthApi
import com.aquaflow.utils.AuthResult
import com.google.android.material.bottomnavigation.BottomNavigationView


class ProfilePage : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.page_profile)

        bindHeaderFromPrefs()
        setupMenuOptions()
        setupEditButton()

        findViewById<MaterialButton>(R.id.btnLogout).setOnClickListener {
            val prefs = getSharedPreferences("auth", MODE_PRIVATE)
            prefs.edit().clear().apply()
            Toast.makeText(this, "Logged out", Toast.LENGTH_SHORT).show()
            val intent = Intent(this, AuthPage::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }

        setupBottomNavigation()
    }

    override fun onResume() {
        super.onResume()
        loadProfile()
    }

    private fun bindHeaderFromPrefs() {
        val prefs = getSharedPreferences("auth", MODE_PRIVATE)
        val name = prefs.getString("name", "").orEmpty()
        val phone = prefs.getString("phone", "").orEmpty()
        val address = prefs.getString("address", "").orEmpty()
        val payment = prefs.getString("paymentMethod", "COD").orEmpty()

        findViewById<TextView>(R.id.tvUserName)?.text = name.ifBlank { "Not available yet" }
        findViewById<TextView>(R.id.tvUserPhone)?.text = phone.ifBlank { "Not available yet" }
        updateRowLabels(address, payment)
    }

    private fun updateRowLabels(address: String, paymentMethod: String) {
        val addressLabel = if (address.isBlank()) "Delivery Addresses" else "Delivery Address · $address"
        val paymentLabel = "Payment Method · ${if (paymentMethod.isBlank()) "COD" else paymentMethod}"

        val rowAddress = findViewById<View>(R.id.rowAddress)
        val rowPayment = findViewById<View>(R.id.rowPayment)
        rowAddress?.findViewById<TextView>(R.id.tvOptionLabel)?.text = addressLabel
        rowPayment?.findViewById<TextView>(R.id.tvOptionLabel)?.text = paymentLabel
    }

    private fun setupEditButton() {
        findViewById<View>(R.id.btnEdit)?.setOnClickListener {
            showEditNamePhoneDialog()
        }
    }

    private fun setupMenuOptions() {
        // Delivery Addresses
        configureRow(
            rowId = R.id.rowAddress,
            label = "Delivery Addresses",
            iconRes = R.drawable.ic_location
        ) {
            showEditAddressDialog()
        }

        // Payment Methods
        configureRow(
            rowId = R.id.rowPayment,
            label = "Payment Methods",
            iconRes = R.drawable.ic_profile_mail
        ) {
            showPaymentMethodDialog()
        }

        // Notifications
        configureRow(
            rowId = R.id.rowNotification,
            label = "Notifications",
            iconRes = R.drawable.ic_profile_notifications
        ) {
            Toast.makeText(this, "Notification Settings", Toast.LENGTH_SHORT).show()
        }

        // Help Center
        configureRow(
            rowId = R.id.rowHelp,
            label = "Help Center",
            iconRes = R.drawable.ic_profile_help
        ) {
            Toast.makeText(this, "Opening Help Center", Toast.LENGTH_SHORT).show()
        }

        // Edit phone from header
        findViewById<View>(R.id.tvUserPhone)?.setOnClickListener {
            showEditPhoneDialog()
        }
    }

    /**
     * Helper function to reduce repetitive code for the middle section rows
     */
//    private fun configureRow(rowId: Int, label: String, iconRes: Int, onClick: () -> Unit) {
//        val rowView = findViewById<View>(rowId)
//        rowView.findViewById<TextView>(R.id.tvOptionLabel).text = label
//        rowView.findViewById<ImageView>(R.id.ivIcon).setImageResource(iconRes)
//        rowView.setOnClickListener { onClick() }
//    }

    private fun configureRow(rowId: Int, label: String, iconRes: Int, onClick: () -> Unit) {
        val rowView = findViewById<View>(rowId)
        if (rowView == null) {
            android.util.Log.e("ProfilePage", "Could not find row with ID: $rowId")
            return
        }

        val labelView = rowView.findViewById<TextView>(R.id.tvOptionLabel)
        val iconView = rowView.findViewById<ImageView>(R.id.ivIcon)

        labelView?.text = label
        iconView?.setImageResource(iconRes)
        rowView.setOnClickListener { onClick() }
    }

    private fun setupBottomNavigation() {
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNavigation)
        // Handle navigation clicks
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_profile -> true
                R.id.navigation_orders -> {
                    startActivity(Intent(this, OrderPage::class.java))
                    true
                }
                R.id.navigation_messages -> {
                    // Navigate to Messages
                    startActivity(Intent(this, MessagePage::class.java))
                    true
                }
                R.id.navigation_home -> {
                    // Navigate to Profile
                    startActivity(Intent(this, HomePage::class.java))
                    true
                }
                else -> false
            }
        }
    }

    private fun loadProfile() {
        val token = getSharedPreferences("auth", MODE_PRIVATE).getString("token", null)
        if (token.isNullOrBlank()) return
        AuthApi.getMe(token) { result ->
            runOnUiThread {
                result.onSuccess { auth ->
                    applyAuthToUi(auth)
                    saveAuth(auth)
                }.onFailure {
                    Toast.makeText(this, "Profile details are not available right now.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun applyAuthToUi(auth: AuthResult) {
        findViewById<TextView>(R.id.tvUserName)?.text = auth.name?.ifBlank { "Not available yet" } ?: "Not available yet"
        findViewById<TextView>(R.id.tvUserPhone)?.text = auth.phone?.ifBlank { "Not available yet" } ?: "Not available yet"
        updateRowLabels(auth.address.orEmpty(), getSharedPreferences("auth", MODE_PRIVATE).getString("paymentMethod", "COD").orEmpty())
    }

    private fun saveAuth(auth: AuthResult) {
        val prefs = getSharedPreferences("auth", MODE_PRIVATE)
        prefs.edit()
            .putString("name", auth.name)
            .putString("phone", auth.phone)
            .putString("address", auth.address)
            .apply()
    }

    private fun showEditNameDialog() {
        showEditNamePhoneDialog()
    }

    private fun showEditNamePhoneDialog() {
        val prefs = getSharedPreferences("auth", MODE_PRIVATE)
        val currentName = prefs.getString("name", "").orEmpty()
        val currentPhone = prefs.getString("phone", "").orEmpty()

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 8, 24, 0)
        }

        val nameInput = android.widget.EditText(this).apply {
            setText(currentName)
            hint = "Full name"
        }

        val phoneInput = android.widget.EditText(this).apply {
            setText(currentPhone)
            hint = "Phone number"
            inputType = android.text.InputType.TYPE_CLASS_PHONE
        }

        container.addView(nameInput)
        container.addView(phoneInput)

        AlertDialog.Builder(this)
            .setTitle("Edit Name & Phone")
            .setView(container)
            .setPositiveButton("Save") { _, _ ->
                val name = nameInput.text.toString().trim()
                val phone = phoneInput.text.toString().trim()
                if (name.isBlank() && phone.isBlank()) return@setPositiveButton
                if (phone.isNotBlank() && !phone.matches(Regex("^\\d{11}$"))) {
                    Toast.makeText(this, "Phone number must be exactly 11 digits", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                updateProfile(name = name.ifBlank { null }, phone = phone.ifBlank { null })
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showEditAddressDialog() {
        val current = getSharedPreferences("auth", MODE_PRIVATE).getString("address", "").orEmpty()
        val spinner = android.widget.Spinner(this).apply {
            adapter = android.widget.ArrayAdapter.createFromResource(
                this@ProfilePage,
                R.array.address_options,
                android.R.layout.simple_spinner_dropdown_item
            )
        }
        val index = (spinner.adapter as android.widget.ArrayAdapter<String>).getPosition(current)
        if (index >= 0) spinner.setSelection(index)

        AlertDialog.Builder(this)
            .setTitle("Edit Address")
            .setView(spinner)
            .setPositiveButton("Save") { _, _ ->
                val value = spinner.selectedItem?.toString().orEmpty().trim()
                if (value.isNotBlank()) updateProfile(address = value)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showPaymentMethodDialog() {
        val options = arrayOf("COD", "GCASH")
        val prefs = getSharedPreferences("auth", MODE_PRIVATE)
        val current = prefs.getString("paymentMethod", "COD").orEmpty()
        val checked = options.indexOf(current).coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle("Payment Method")
            .setSingleChoiceItems(options, checked, null)
            .setPositiveButton("Save") { dialog, _ ->
                val list = (dialog as AlertDialog).listView
                val selected = options[list.checkedItemPosition]
                prefs.edit().putString("paymentMethod", selected).apply()
                updateRowLabels(prefs.getString("address", "").orEmpty(), selected)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateProfile(name: String? = null, address: String? = null, phone: String? = null) {
        val token = getSharedPreferences("auth", MODE_PRIVATE).getString("token", null)
        if (token.isNullOrBlank()) return
        AuthApi.updateProfile(token, name, address, phone) { result ->
            runOnUiThread {
                result.onSuccess { auth ->
                    applyAuthToUi(auth)
                    saveAuth(auth)
                    Toast.makeText(this, "Profile updated", Toast.LENGTH_SHORT).show()
                }.onFailure {
                    Toast.makeText(this, it.message ?: "Update failed", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
