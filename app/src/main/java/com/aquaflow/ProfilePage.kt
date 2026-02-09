package com.aquaflow

import android.content.Intent
import android.os.Bundle
import android.view.View
import com.google.android.material.button.MaterialButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomnavigation.BottomNavigationView


data class UserProfile(
    val name: String,
    val phone: String,
    val profileImageId: Int // Resource ID for sample data
)


class ProfilePage : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.page_profile)

        // 1. Create Sample Data
        val currentUser = UserProfile(
            name = "Alex Johnson",
            phone = "+63917 123 4567",
            profileImageId = R.drawable.ic_profile // Ensure this exists in your res/drawable
        )

        // 2. Bind Header Data
        bindHeader(currentUser)

        // 3. Setup Middle Menu Layouts
        setupMenuOptions()

        // 4. Logout Logic
        findViewById<MaterialButton>(R.id.btnLogout).setOnClickListener {
            Toast.makeText(this, "Logging out...", Toast.LENGTH_SHORT).show()
            // Add your navigation to LoginActivity here
        }

        setupBottomNavigation()
    }

//    private fun bindHeader(user: UserProfile) {
//        val tvName = findViewById<TextView>(R.id.tvUserName)
//        val tvPhone = findViewById<TextView>(R.id.tvUserPhone)
//        val ivProfile = findViewById<ImageView>(R.id.ivUserProfile)
//
//        tvName.text = user.name
//        tvPhone.text = user.phone
//        ivProfile.setImageResource(user.profileImageId)
//    }

    private fun bindHeader(user: UserProfile) {
        val tvName = findViewById<TextView>(R.id.tvUserName)
        val tvPhone = findViewById<TextView>(R.id.tvUserPhone)

        // The ?. means "Only do this if the view was actually found"
        tvName?.text = user.name
        tvPhone?.text = user.phone
    }
    private fun setupMenuOptions() {
        // Delivery Addresses
        configureRow(
            rowId = R.id.rowAddress,
            label = "Delivery Addresses",
            iconRes = R.drawable.ic_location
        ) {
            Toast.makeText(this, "Opening Addresses", Toast.LENGTH_SHORT).show()
        }

        // Payment Methods
        configureRow(
            rowId = R.id.rowPayment,
            label = "Payment Methods",
            iconRes = R.drawable.ic_profile_mail
        ) {
            Toast.makeText(this, "Opening Payments", Toast.LENGTH_SHORT).show()
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
}