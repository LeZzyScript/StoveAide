package com.example.stoveaide.views

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.stoveaide.data.FirestoreManager
import com.example.stoveaide.data.LocalAuthManager
import com.example.stoveaide.databinding.ActivityResetPasswordBinding
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
        val localAuth = LocalAuthManager(this)
        localAuth.updatePassword(email, password)
        localAuth.authenticate(email, password)

        val auth = FirestoreManager.auth
        val currentUser = auth?.currentUser
        if (currentUser != null && currentUser.email.equals(email, ignoreCase = true)) {
            currentUser.updatePassword(password)
                .addOnCompleteListener {
                    finalizeResetPassword(email)
                }
        } else if (auth != null) {
            // Also trigger Firebase's official password reset link according to Firebase's system
            auth.sendPasswordResetEmail(email)
            finalizeResetPassword(email)
        } else {
            finalizeResetPassword(email)
        }
    }

    private fun finalizeResetPassword(email: String) {
        val localAuth = LocalAuthManager(this)
        localAuth.setCurrentEmail(email)

        val db = FirestoreManager.firestore
        if (db != null) {
            db.collection("users").whereEqualTo("email", email).get()
                .addOnSuccessListener { snapshot ->
                    val existing = snapshot.documents.firstOrNull()?.toObject(com.example.stoveaide.models.UserProfile::class.java)
                    if (existing != null) {
                        localAuth.saveUserProfile(existing)
                    }
                    for (doc in snapshot.documents) {
                        doc.reference.update("passwordLastChanged", System.currentTimeMillis())
                    }
                }
        }

        // Pre-cache profile across Auth/Firestore before navigating to MainActivity
        FirestoreManager.loadCurrentUserProfile(this) { profile ->
            localAuth.saveUserProfile(profile)
        }

        setLoading(false)
        Toast.makeText(this, "Password updated successfully! Welcome back.", Toast.LENGTH_SHORT).show()
        val dashboardIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(dashboardIntent)
        finish()
    }

    private fun setLoading(isLoading: Boolean) {
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.btnResetPassword.visibility = if (isLoading) View.INVISIBLE else View.VISIBLE
    }
}
