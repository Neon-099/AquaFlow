package com.aquaflow

import android.os.Bundle
import android.os.CountDownTimer
import android.util.Patterns
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.aquaflow.utils.AuthApi

class ForgotPasswordPage : AppCompatActivity() {
    private val etEmail by lazy { findViewById<EditText>(R.id.et_reset_email) }
    private val etCode by lazy { findViewById<EditText>(R.id.et_reset_code) }
    private val etNewPassword by lazy { findViewById<EditText>(R.id.et_new_password) }
    private val etConfirmPassword by lazy { findViewById<EditText>(R.id.et_confirm_new_password) }
    private val btnSendCode by lazy { findViewById<Button>(R.id.btn_send_code) }
    private val btnResetPassword by lazy { findViewById<Button>(R.id.btn_reset_password) }
    private val tvMessage by lazy { findViewById<TextView>(R.id.tv_reset_message) }
    private val tvCooldown by lazy { findViewById<TextView>(R.id.tv_reset_cooldown) }
    private val progressBar by lazy { findViewById<ProgressBar>(R.id.progress_reset) }
    private val requestContainer by lazy { findViewById<View>(R.id.container_request) }
    private val resetContainer by lazy { findViewById<View>(R.id.container_reset) }
    private val tvResendCode by lazy { findViewById<TextView>(R.id.tv_resend_code) }
    private val cbShowPasswords by lazy { findViewById<android.widget.CheckBox>(R.id.cb_show_reset_passwords) }

    private var cooldownTimer: CountDownTimer? = null
    private var cooldownSeconds = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.forgot_password_page)

        val savedEmail = getSharedPreferences("auth", MODE_PRIVATE).getString("email", null)
        if (!savedEmail.isNullOrBlank()) {
            etEmail.setText(savedEmail)
        }

        btnSendCode.setOnClickListener { handleSendCode() }
        btnResetPassword.setOnClickListener { handleResetPassword() }
        findViewById<TextView>(R.id.btn_back_to_login).setOnClickListener { finish() }
        tvResendCode.setOnClickListener { handleSendCode() }
        cbShowPasswords.setOnCheckedChangeListener { _, isChecked ->
            setPasswordVisibility(etNewPassword, isChecked)
            setPasswordVisibility(etConfirmPassword, isChecked)
        }
        setStepRequest()
    }

    override fun onDestroy() {
        super.onDestroy()
        cooldownTimer?.cancel()
    }

    private fun handleSendCode() {
        clearFieldErrors()
        val email = etEmail.text.toString().trim()
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etEmail.error = "Enter a valid email"
            return
        }

        setLoading(true)
        AuthApi.forgotPassword(email) { result ->
            runOnUiThread {
                setLoading(false)
                result.onSuccess { message ->
                    tvMessage.text = message ?: "Reset instructions sent. Check your email for the code."
                    tvMessage.visibility = View.VISIBLE
                    setStepReset()
                    startCooldown(10 * 60)
                }.onFailure { err ->
                    showToast(err.message ?: "Could not send reset email")
                }
            }
        }
    }

    private fun handleResetPassword() {
        clearFieldErrors()
        val code = etCode.text.toString().trim()
        val newPassword = etNewPassword.text.toString().trim()
        val confirm = etConfirmPassword.text.toString().trim()

        if (code.isBlank()) {
            etCode.error = "Reset code required"
            return
        }
        if (newPassword.isBlank()) {
            etNewPassword.error = "New password required"
            return
        }
        if (newPassword != confirm) {
            etConfirmPassword.error = "Passwords do not match"
            return
        }

        setLoading(true)
        AuthApi.resetPassword(code, newPassword) { result ->
            runOnUiThread {
                setLoading(false)
                result.onSuccess { message ->
                    showToast(message ?: "Password reset successful. You can now sign in.")
                    finish()
                }.onFailure { err ->
                    showToast(err.message ?: "Reset failed")
                }
            }
        }
    }

    private fun setStepRequest() {
        requestContainer.visibility = View.VISIBLE
        resetContainer.visibility = View.GONE
        tvMessage.visibility = View.GONE
        tvResendCode.visibility = View.GONE
    }

    private fun setStepReset() {
        requestContainer.visibility = View.GONE
        resetContainer.visibility = View.VISIBLE
        tvResendCode.visibility = View.VISIBLE
    }

    private fun setLoading(isLoading: Boolean) {
        progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        btnSendCode.isEnabled = !isLoading && cooldownSeconds <= 0
        btnResetPassword.isEnabled = !isLoading
        tvResendCode.isEnabled = !isLoading && cooldownSeconds <= 0
    }

    private fun startCooldown(seconds: Int) {
        cooldownTimer?.cancel()
        cooldownSeconds = seconds
        tvCooldown.visibility = View.VISIBLE
        btnSendCode.isEnabled = false
        tvResendCode.isEnabled = false

        cooldownTimer = object : CountDownTimer((seconds * 1000).toLong(), 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                cooldownSeconds = (millisUntilFinished / 1000L).toInt()
                val min = (cooldownSeconds / 60).toString().padStart(2, '0')
                val sec = (cooldownSeconds % 60).toString().padStart(2, '0')
                tvCooldown.text = "You can request a new code in $min:$sec"
            }

            override fun onFinish() {
                cooldownSeconds = 0
                tvCooldown.visibility = View.GONE
                btnSendCode.isEnabled = true
                tvResendCode.isEnabled = true
            }
        }.start()
    }

    private fun clearFieldErrors() {
        etEmail.error = null
        etCode.error = null
        etNewPassword.error = null
        etConfirmPassword.error = null
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun setPasswordVisibility(input: EditText, isVisible: Boolean) {
        val selection = input.text?.length ?: 0
        input.inputType = if (isVisible) {
            android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        } else {
            android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        input.setSelection(selection)
    }
}
