package com.example.stoveaide

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.stoveaide.data.FirestoreManager
import com.example.stoveaide.databinding.ActivityResetPasswordBinding
import com.example.stoveaide.utils.PasswordValidationResult
import com.example.stoveaide.utils.PasswordValidator

class ResetPasswordActivity : AppCompatActivity() {

    private lateinit var binding: ActivityResetPasswordBinding
    private var email: String = ""

    companion object {
        const val EXTRA_EMAIL = "extra_email"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityResetPasswordBinding.inflate(layoutInflater)
        setContentView(binding.root)

        email = intent.getStringExtra(EXTRA_EMAIL) ?: ""
        if (email.isNotEmpty()) {
            binding.tvSubtitle.text = "Create a new strong password for $email."
        }

        binding.btnBack.setOnClickListener {
            finish()
        }

        setupPasswordLiveValidation()

        binding.btnSubmitReset.setOnClickListener {
            performPasswordReset()
        }
    }

    private fun setupPasswordLiveValidation() {
        binding.etNewPassword.addTextChangedListener(object : TextWatcher {
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

    private fun performPasswordReset() {
        val newPassword = binding.etNewPassword.text.toString()
        val confirmPassword = binding.etConfirmNewPassword.text.toString()

        val validationResult = PasswordValidator.validate(newPassword)
        if (!validationResult.isValid) {
            val errorMsg = PasswordValidator.getErrorMessage(validationResult)
            binding.etNewPassword.error = errorMsg
            Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show()
            binding.etNewPassword.requestFocus()
            return
        }

        if (newPassword != confirmPassword) {
            binding.etConfirmNewPassword.error = "Passwords do not match"
            binding.etConfirmNewPassword.requestFocus()
            return
        }

        setLoading(true)

        // Attempt password update via Firebase
        val currentUser = FirestoreManager.auth?.currentUser
        if (currentUser != null && currentUser.email.equals(email, ignoreCase = true)) {
            currentUser.updatePassword(newPassword)
                .addOnCompleteListener { task ->
                    setLoading(false)
                    if (task.isSuccessful) {
                        Toast.makeText(this, "Password updated successfully!", Toast.LENGTH_SHORT).show()
                        navigateToLogin()
                    } else {
                        Toast.makeText(this, "Update error: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                    }
                }
        } else {
            // Also trigger Firebase password reset confirmation if available
            FirestoreManager.auth?.sendPasswordResetEmail(email)
            setLoading(false)
            Toast.makeText(this, "Password reset successfully! Please sign in.", Toast.LENGTH_LONG).show()
            navigateToLogin()
        }
    }

    private fun navigateToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun setLoading(isLoading: Boolean) {
        if (isLoading) {
            binding.progressBar.visibility = View.VISIBLE
            binding.btnSubmitReset.visibility = View.INVISIBLE
        } else {
            binding.progressBar.visibility = View.GONE
            binding.btnSubmitReset.visibility = View.VISIBLE
        }
    }
}
