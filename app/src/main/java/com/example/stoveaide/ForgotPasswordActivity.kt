package com.example.stoveaide

import android.content.Intent
import android.os.Bundle
import android.text.Html
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.stoveaide.data.OtpManager
import com.example.stoveaide.databinding.ActivityForgotPasswordBinding

class ForgotPasswordActivity : AppCompatActivity() {

    private lateinit var binding: ActivityForgotPasswordBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityForgotPasswordBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.tvRememberPassword.text = Html.fromHtml("Remembered your password? <font color='#38A1FF'>Log in</font>", Html.FROM_HTML_MODE_LEGACY)

        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.tvRememberPassword.setOnClickListener {
            finish()
        }

        binding.btnSendCode.setOnClickListener {
            initiateForgotPassword()
        }
    }

    private fun initiateForgotPassword() {
        val email = binding.etEmail.text.toString().trim()

        if (email.isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.etEmail.error = "Please enter a valid email address"
            binding.etEmail.requestFocus()
            return
        }

        setLoading(true)

        // Generate and dispatch 6-digit OTP code for password reset
        OtpManager.generateAndSendOtp(email, OtpManager.PURPOSE_FORGOT_PASSWORD) { success, code, error ->
            setLoading(false)
            if (success) {
                Toast.makeText(this, "6-digit reset code sent to $email! (Code: $code)", Toast.LENGTH_LONG).show()
                val intent = Intent(this, OtpVerificationActivity::class.java).apply {
                    putExtra(OtpVerificationActivity.EXTRA_EMAIL, email)
                    putExtra(OtpVerificationActivity.EXTRA_PURPOSE, OtpManager.PURPOSE_FORGOT_PASSWORD)
                }
                startActivity(intent)
                finish()
            } else {
                Toast.makeText(this, "Failed to send reset code: $error", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun setLoading(isLoading: Boolean) {
        if (isLoading) {
            binding.progressBar.visibility = View.VISIBLE
            binding.btnSendCode.visibility = View.INVISIBLE
        } else {
            binding.progressBar.visibility = View.GONE
            binding.btnSendCode.visibility = View.VISIBLE
        }
    }
}
