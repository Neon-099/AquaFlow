package com.aquaflow

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.aquaflow.utils.AuthApi
import com.aquaflow.utils.AuthResult
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton

class RiderProfilePage : AppCompatActivity() {
    private lateinit var tvUserName: TextView
    private lateinit var tvUserPhone: TextView
    private lateinit var tvMaxCapacityValue: TextView
    private lateinit var tvCurrentLoadValue: TextView
    private lateinit var tvActiveOrdersValue: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.page_rider_profile)

        bindHeader()
        bindCachedCapacity()
        setupMenuOptions()
        setupLogout()
        setupBottomNavigation()
    }

    override fun onResume() {
        super.onResume()
        loadRiderProfile()
    }

    private fun bindHeader() {
        val prefs = getSharedPreferences("auth", MODE_PRIVATE)
        val name = prefs.getString("name", "Rider") ?: "Rider"
        val phone = prefs.getString("phone", "") ?: ""

        tvUserName = findViewById(R.id.tvUserName)
        tvUserPhone = findViewById(R.id.tvUserPhone)
        tvMaxCapacityValue = findViewById(R.id.tvMaxCapacityValue)
        tvCurrentLoadValue = findViewById(R.id.tvCurrentLoadValue)
        tvActiveOrdersValue = findViewById(R.id.tvActiveOrdersValue)

        tvUserName.text = name
        tvUserPhone.text = phone
    }

    private fun bindCachedCapacity() {
        val prefs = getSharedPreferences("auth", MODE_PRIVATE)
        val cached = prefs.getInt("maxCapacityGallons", -1)
        tvMaxCapacityValue.text = if (cached > 0) "$cached gal" else "Not available yet"
        val cachedLoad = prefs.getInt("currentLoadGallons", -1)
        val cachedActive = prefs.getInt("activeOrdersCount", -1)
        tvCurrentLoadValue.text = if (cachedLoad >= 0) "$cachedLoad gal" else "Not available yet"
        tvActiveOrdersValue.text = if (cachedActive >= 0) "$cachedActive" else "Not available yet"
    }

    private fun loadRiderProfile() {
        val token = getSharedPreferences("auth", MODE_PRIVATE).getString("token", null)
        if (token.isNullOrBlank()) return
        AuthApi.getMe(token) { result ->
            runOnUiThread {
                result.onSuccess { auth ->
                    updateProfile(auth)
                }.onFailure {
                    showProfileError()
                }
            }
        }
    }

    private fun updateProfile(auth: AuthResult) {
        auth.name?.let { tvUserName.text = it }
        auth.phone?.let { tvUserPhone.text = it }
        val capacity = auth.maxCapacityGallons
        if (capacity != null && capacity > 0) {
            tvMaxCapacityValue.text = "$capacity gal"
            getSharedPreferences("auth", MODE_PRIVATE)
                .edit()
                .putInt("maxCapacityGallons", capacity)
                .apply()
        }
        auth.currentLoadGallons?.let { load ->
            if (load >= 0) {
                tvCurrentLoadValue.text = "$load gal"
                getSharedPreferences("auth", MODE_PRIVATE)
                    .edit()
                    .putInt("currentLoadGallons", load)
                    .apply()
            }
        }
        auth.activeOrdersCount?.let { count ->
            if (count >= 0) {
                tvActiveOrdersValue.text = "$count"
                getSharedPreferences("auth", MODE_PRIVATE)
                    .edit()
                    .putInt("activeOrdersCount", count)
                    .apply()
            }
        }
    }

    private fun showProfileError() {
        tvMaxCapacityValue.text = "Not available right now"
        tvCurrentLoadValue.text = "Not available right now"
        tvActiveOrdersValue.text = "Not available right now"
        Toast.makeText(this, "Profile details are not available right now.", Toast.LENGTH_SHORT).show()
    }

    private fun setupMenuOptions() {
        configureRow(
            rowId = R.id.rowNotification,
            label = "Notifications",
            iconRes = R.drawable.ic_profile_notifications
        ) {
            Toast.makeText(this, "Notification settings", Toast.LENGTH_SHORT).show()
        }

        configureRow(
            rowId = R.id.rowHelp,
            label = "Help Center",
            iconRes = R.drawable.ic_profile_help
        ) {
            Toast.makeText(this, "Opening Help Center", Toast.LENGTH_SHORT).show()
        }
    }

    private fun configureRow(rowId: Int, label: String, iconRes: Int, onClick: () -> Unit) {
        val rowView = findViewById<View>(rowId)
        if (rowView == null) {
            android.util.Log.e("RiderProfilePage", "Could not find row with ID: $rowId")
            return
        }
        val labelView = rowView.findViewById<TextView>(R.id.tvOptionLabel)
        val iconView = rowView.findViewById<ImageView>(R.id.ivIcon)
        labelView?.text = label
        iconView?.setImageResource(iconRes)
        rowView.setOnClickListener { onClick() }
    }

    private fun setupLogout() {
        findViewById<MaterialButton>(R.id.btnLogout).setOnClickListener {
            val prefs = getSharedPreferences("auth", MODE_PRIVATE)
            prefs.edit().clear().apply()
            Toast.makeText(this, "Logged out", Toast.LENGTH_SHORT).show()
            val intent = Intent(this, AuthPage::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }

    private fun setupBottomNavigation() {
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNavigation)
        bottomNav.selectedItemId = R.id.navigation_profile
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_home -> {
                    startActivity(Intent(this, RiderHomePage::class.java))
                    true
                }
                R.id.navigation_profile -> true
                R.id.navigation_orders -> {
                    startActivity(Intent(this, RiderOrderPage::class.java))
                    true
                }
                R.id.navigation_messages -> {
                    startActivity(Intent(this, RiderMessagePage::class.java))
                    true
                }
                else -> false
            }
        }
    }
}
 
