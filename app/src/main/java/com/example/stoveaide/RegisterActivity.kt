package com.example.stoveaide

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.text.Editable
import android.text.Html
import android.text.TextWatcher
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.stoveaide.data.FirestoreManager
import com.example.stoveaide.data.OtpManager
import com.example.stoveaide.databinding.ActivityRegisterBinding
import com.example.stoveaide.utils.PasswordValidationResult
import com.example.stoveaide.utils.PasswordValidator

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Format footer link
        binding.tvLoginLink.text = Html.fromHtml("Already have an account? <font color='#38A1FF'>Log in</font>", Html.FROM_HTML_MODE_LEGACY)

        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.tvLoginLink.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }

        setupPasswordLiveValidation()

        binding.btnSubmitRegister.setOnClickListener {
            initiateRegistration()
        }
    }

    private fun setupPasswordLiveValidation() {
        binding.etPassword.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val password = s?.toString() ?: ""
                val result = PasswordValidator.validate(password)
                updateRuleIndicators(result)
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun updateRuleIndicators(result: PasswordValidationResult) {
        updateSingleRule(binding.ivRuleLength, binding.tvRuleLength, result.hasMinLength)
        updateSingleRule(binding.ivRuleUpper, binding.tvRuleUpper, result.hasUppercase)
        updateSingleRule(binding.ivRuleLower, binding.tvRuleLower, result.hasLowercase)
        updateSingleRule(binding.ivRuleDigit, binding.tvRuleDigit, result.hasDigit)
        updateSingleRule(binding.ivRuleSymbol, binding.tvRuleSymbol, result.hasSpecialChar)
    }

    private fun updateSingleRule(icon: ImageView, label: TextView, isSatisfied: Boolean) {
        val colorRes = if (isSatisfied) R.color.rule_valid else R.color.rule_invalid
        val iconRes = if (isSatisfied) R.drawable.ic_check_circle else R.drawable.ic_circle_dot
        val color = ContextCompat.getColor(this, colorRes)

        icon.setImageResource(iconRes)
        label.setTextColor(color)
    }

    private fun initiateRegistration() {
        val fullName = binding.etFullName.text.toString().trim()
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString()
        val confirmPassword = binding.etConfirmPassword.text.toString()

        if (fullName.isEmpty()) {
            binding.etFullName.error = "Please enter your full name"
            binding.etFullName.requestFocus()
            return
        }

        if (email.isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.etEmail.error = "Please enter a valid email address"
            binding.etEmail.requestFocus()
            return
        }

        val validationResult = PasswordValidator.validate(password)
        if (!validationResult.isValid) {
            val errorMsg = PasswordValidator.getErrorMessage(validationResult)
            binding.etPassword.error = errorMsg
            Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show()
            binding.etPassword.requestFocus()
            return
        }

        if (password != confirmPassword) {
            binding.etConfirmPassword.error = "Passwords do not match"
            binding.etConfirmPassword.requestFocus()
            return
        }

        setLoading(true)

        // Generate and send 6-digit OTP to user's email
        OtpManager.generateAndSendOtp(email, OtpManager.PURPOSE_REGISTRATION) { success, code, error ->
            setLoading(false)
            if (success) {
                Toast.makeText(this, "6-digit code sent to $email! (Code: $code)", Toast.LENGTH_LONG).show()
                val intent = Intent(this, OtpVerificationActivity::class.java).apply {
                    putExtra(OtpVerificationActivity.EXTRA_EMAIL, email)
                    putExtra(OtpVerificationActivity.EXTRA_PURPOSE, OtpManager.PURPOSE_REGISTRATION)
                    putExtra(OtpVerificationActivity.EXTRA_FULL_NAME, fullName)
                    putExtra(OtpVerificationActivity.EXTRA_PASSWORD, password)
                }
                startActivity(intent)
            } else {
                Toast.makeText(this, "Failed to send verification code: $error", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun setLoading(isLoading: Boolean) {
        if (isLoading) {
            binding.progressBar.visibility = View.VISIBLE
            binding.btnSubmitRegister.visibility = View.INVISIBLE
        } else {
            binding.progressBar.visibility = View.GONE
            binding.btnSubmitRegister.visibility = View.VISIBLE
        }
    }
}
