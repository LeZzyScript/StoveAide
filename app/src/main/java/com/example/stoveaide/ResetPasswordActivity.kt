package com.example.stoveaide

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.stoveaide.databinding.ActivityResetPasswordBinding
import com.example.stoveaide.data.LocalAuthManager
import com.example.stoveaide.utils.PasswordValidator

class ResetPasswordActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_EMAIL = "extra_email"
    }

    private lateinit var binding: ActivityResetPasswordBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityResetPasswordBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }
        binding.btnResetPassword.setOnClickListener { resetPassword() }
    }

    private fun resetPassword() {
        val password = binding.etPassword.text.toString()
        val confirmPassword = binding.etConfirmPassword.text.toString()
        val validationResult = PasswordValidator.validate(password)

        if (!validationResult.isValid) {
            val errorMessage = PasswordValidator.getErrorMessage(validationResult)
            binding.etPassword.error = errorMessage
            binding.etPassword.requestFocus()
            return
        }

        if (password != confirmPassword) {
            binding.etConfirmPassword.error = "Passwords do not match"
            binding.etConfirmPassword.requestFocus()
            return
        }

        val email = intent.getStringExtra(EXTRA_EMAIL).orEmpty()
        if (email.isBlank()) {
            Toast.makeText(
                this,
                "Please start the password reset process again.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        setLoading(true)
        val updated = LocalAuthManager(this).updatePassword(email, password)
        setLoading(false)
        if (updated) {
            Toast.makeText(this, "Password updated successfully.", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
        } else {
            Toast.makeText(this, "No local account was found for this email.", Toast.LENGTH_LONG).show()
        }
    }

    private fun setLoading(isLoading: Boolean) {
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.btnResetPassword.visibility = if (isLoading) View.INVISIBLE else View.VISIBLE
    }
}