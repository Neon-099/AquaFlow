package com.aquaflow

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.*
import android.text.InputType
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
    private val etPhone by lazy { findViewById<EditText>(R.id.et_phone) }
    private val spAddress by lazy { findViewById<Spinner>(R.id.sp_address) }
    private val etConfirmPassword by lazy { findViewById<EditText>(R.id.et_confirm_password) }
    private val ivTogglePassword by lazy { findViewById<ImageView>(R.id.iv_toggle_password) }
    private val ivToggleConfirmPassword by lazy { findViewById<ImageView>(R.id.iv_toggle_confirm_password) }
    private val confirmPasswordContainer by lazy { findViewById<FrameLayout>(R.id.confirm_password_container) }

    // Sign-up Specific Labels (for visibility toggling)
    private val signupLabels by lazy {
        listOf(
            findViewById<TextView>(R.id.label_name),
            etName,
            findViewById<TextView>(R.id.label_phone),
            etPhone,
            findViewById<TextView>(R.id.label_address),
            spAddress,
            findViewById<TextView>(R.id.label_confirm_password),
            confirmPasswordContainer
        )
    }

    private var isLoginMode = true
    private var isPasswordVisible = false
    private var isConfirmPasswordVisible = false

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
        ivTogglePassword.setOnClickListener {
            isPasswordVisible = !isPasswordVisible
            updatePasswordVisibility(etPassword, ivTogglePassword, isPasswordVisible)
        }
        ivToggleConfirmPassword.setOnClickListener {
            isConfirmPasswordVisible = !isConfirmPasswordVisible
            updatePasswordVisibility(etConfirmPassword, ivToggleConfirmPassword, isConfirmPasswordVisible)
        }
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
            if (etPhone.text.isBlank()) { etPhone.error = "Phone number is required"; return }
            if (spAddress.selectedItemPosition == 0) {
                Toast.makeText(this, "Please select a delivery address", Toast.LENGTH_LONG).show()
                return
            }
            val confirmPassword = etConfirmPassword.text.toString().trim()
            if (confirmPassword.isBlank()) { etConfirmPassword.error = "Confirm your password"; return }
            if (password != confirmPassword) {
                etConfirmPassword.error = "Passwords do not match"
                return
            }
        }

        executeNetworkRequest(email, password)
    }

    // --- Backend Integration Hook ---
    private fun executeNetworkRequest(email: String, pass: String) {
        setLoading(true)

        if (isLoginMode) {
            AuthApi.login(email, pass) { result ->
                handleAuthResult(result)
            }
        } else {
            val name = etName.text.toString().trim()
            val phone = etPhone.text.toString().trim()
            val address = spAddress.selectedItem.toString().trim()
            AuthApi.signup(email, pass, name, address, phone) { result ->
                handleAuthResult(result)
            }
        }
    }

    private fun setLoading(isLoading: Boolean) {
        progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        btnSubmit.isEnabled = !isLoading
        btnSubmit.text = if (isLoading) "" else (if (isLoginMode) "Log In →" else "Create Account →")
    }

    private fun handleAuthResult(result: Result<AuthResult>) {
        runOnUiThread {
            setLoading(false)
            result.onSuccess { auth ->
                saveAuth(auth)
                Toast.makeText(this, "Welcome ${auth.userEmail}", Toast.LENGTH_LONG).show()
                val intent = Intent(this, HomePage::class.java)
                startActivity(intent)
                finish()
            }.onFailure { err ->
                val message = err.message ?: "Authentication failed"
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun saveAuth(auth: AuthResult) {
        val prefs = getSharedPreferences("auth", MODE_PRIVATE)
        prefs.edit()
            .putString("token", auth.token)
            .putString("email", auth.userEmail)
            .putString("userId", auth.userId)
            .putString("name", auth.name)
            .putString("address", auth.address)
            .putString("phone", auth.phone)
            .apply()
    }

    private fun updatePasswordVisibility(
        editText: EditText,
        toggleView: ImageView,
        isVisible: Boolean
    ) {
        editText.inputType = if (isVisible) {
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        } else {
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        editText.setSelection(editText.text.length)
        toggleView.setImageResource(
            if (isVisible) R.drawable.ic_visibility_off else R.drawable.ic_visibility
        )
    }
}
