package com.aquaflow

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class AuthPage : AppCompatActivity() {

    // --- View Binding ---
    private val tabLogin by lazy { findViewById<TextView>(R.id.tab_login) }
    private val tabSignup by lazy { findViewById<TextView>(R.id.tab_signup) }
    private val btnSubmit by lazy { findViewById<Button>(R.id.btn_submit) }
    private val tvSubtitle by lazy { findViewById<TextView>(R.id.tv_subtitle) }
    private val tvForgot by lazy { findViewById<TextView>(R.id.tv_forgot_password) }
    private val progressBar by lazy { findViewById<ProgressBar>(R.id.progress_bar) }

    // Input Fields
    private val etEmail by lazy { findViewById<EditText>(R.id.et_email) }
    private val etPassword by lazy { findViewById<EditText>(R.id.et_password) }
    private val etName by lazy { findViewById<EditText>(R.id.et_name) }
    private val etAddress by lazy { findViewById<EditText>(R.id.et_address) }

    // Sign-up Specific Labels (for visibility toggling)
    private val signupLabels by lazy {
        listOf(
            findViewById<TextView>(R.id.label_name),
            findViewById<TextView>(R.id.label_address),
            etName,
            etAddress
        )
    }

    private var isLoginMode = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.auth_page)

        setupListeners()
        toggleUIState(true) // Default to Login
    }

    private fun setupListeners() {
        tabLogin.setOnClickListener { toggleUIState(true) }
        tabSignup.setOnClickListener { toggleUIState(false) }
        btnSubmit.setOnClickListener { validateAndSubmit() }
    }

    // --- UI Rendering Logic ---
    private fun toggleUIState(isLogin: Boolean) {
        isLoginMode = isLogin

        // Tab Styling
        val activeColor = Color.DKGRAY
        val inactiveColor = Color.LTGRAY

        tabLogin.apply {
            setTextColor(if (isLogin) activeColor else inactiveColor)
            setBackgroundResource(if (isLogin) R.drawable.tab_active_bg else 0)
        }
        tabSignup.apply {
            setTextColor(if (isLogin) inactiveColor else activeColor)
            setBackgroundResource(if (isLogin) 0 else R.drawable.tab_active_bg)
        }

        // Content Updates
        tvSubtitle.text = if (isLogin) "Fast delivery to your doorstep" else "Create an account to start ordering"
        btnSubmit.text = if (isLogin) "Log In →" else "Create Account →"
        tvForgot.visibility = if (isLogin) View.VISIBLE else View.GONE

        // Toggle Sign-up Fields
        val visibility = if (isLogin) View.GONE else View.VISIBLE
        signupLabels.forEach { it.visibility = visibility }
    }

    // --- Business & Validation Logic ---
    private fun validateAndSubmit() {
        val email = etEmail.text.toString().trim()
        val password = etPassword.text.toString().trim()

        // Common Validation
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etEmail.error = "Please enter a valid email address"
            return
        }
        if (password.length < 6) {
            etPassword.error = "Password must be at least 6 characters"
            return
        }

        // Mode-Specific Validation
        if (!isLoginMode) {
            if (etName.text.isBlank()) { etName.error = "Name is required"; return }
            if (etAddress.text.isBlank()) { etAddress.error = "Address is required"; return }
        }

        executeNetworkRequest(email, password)
    }

    // --- Backend Integration Hook ---
    private fun executeNetworkRequest(email: String, pass: String) {
        setLoading(true)

        // Data Package for Backend
        val payload = mutableMapOf("email" to email, "password" to pass)
        if (!isLoginMode) {
            payload["name"] = etName.text.toString()
            payload["address"] = etAddress.text.toString()
        }

        /** * INTEGRATION TIP:
         * This is where you will call your ViewModel or Repository.
         * Example: authViewModel.login(payload)
         */

        // Mocking a network response
        btnSubmit.postDelayed({
            setLoading(false)
            Toast.makeText(this, "Success! Backend data ready: $payload", Toast.LENGTH_LONG).show()
            val intent = Intent(this, HomePage::class.java)
            startActivity(intent)
            finish()
        }, 1500)
    }

    private fun setLoading(isLoading: Boolean) {
        progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        btnSubmit.isEnabled = !isLoading
        btnSubmit.text = if (isLoading) "" else (if (isLoginMode) "Log In →" else "Create Account →")
    }
}