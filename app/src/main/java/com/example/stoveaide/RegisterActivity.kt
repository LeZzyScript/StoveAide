package com.example.stoveaide

import android.content.Intent
import android.os.Bundle
import android.text.Html
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.stoveaide.data.FirestoreManager
import com.example.stoveaide.databinding.ActivityRegisterBinding
import com.example.stoveaide.models.UserProfile

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

        binding.btnSubmitRegister.setOnClickListener {
            performRegistration()
        }
    }

    private fun performRegistration() {
        val firstName = binding.etFirstName.text.toString().trim()
        val lastName = binding.etLastName.text.toString().trim()
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()
        val confirmPassword = binding.etConfirmPassword.text.toString().trim()

        if (firstName.isEmpty()) {
            binding.etFirstName.error = "Please enter your first name"
            binding.etFirstName.requestFocus()
            return
        }

        if (lastName.isEmpty()) {
            binding.etLastName.error = "Please enter your last name"
            binding.etLastName.requestFocus()
            return
        }

        if (email.isEmpty()) {
            binding.etEmail.error = "Please enter your email"
            binding.etEmail.requestFocus()
            return
        }

        if (password.length < 6) {
            binding.etPassword.error = "Password must be at least 6 characters"
            binding.etPassword.requestFocus()
            return
        }

        if (confirmPassword.isEmpty()) {
            binding.etConfirmPassword.error = "Please confirm your password"
            binding.etConfirmPassword.requestFocus()
            return
        }

        if (password != confirmPassword) {
            binding.etConfirmPassword.error = "Passwords do not match"
            binding.etConfirmPassword.requestFocus()
            return
        }

        setLoading(true)

        val fullName = "$firstName $lastName".trim()

        val auth = FirestoreManager.auth
        if (auth != null) {
            auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val firebaseUser = task.result?.user
                        val userProfile = UserProfile(
                            uid = firebaseUser?.uid ?: "",
                            firstName = firstName,
                            lastName = lastName,
                            fullName = fullName,
                            email = email
                        )
                        // Save to Cloud Firestore
                        FirestoreManager.saveUserProfile(userProfile) { success, error ->
                            setLoading(false)
                            if (success) {
                                Toast.makeText(this, "Account created successfully! Please log in.", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(this, "Account created! (Firestore sync pending: $error)", Toast.LENGTH_LONG).show()
                            }
                            navigateToLogin()
                        }
                    } else {
                        setLoading(false)
                        Toast.makeText(this, "Registration failed: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                    }
                }
        } else {
            // Local Demo Mode fallback
            setLoading(false)
            Toast.makeText(this, "Account created (Demo Mode). Please log in.", Toast.LENGTH_SHORT).show()
            navigateToLogin()
        }
    }

    private fun navigateToLogin() {
        FirestoreManager.auth?.signOut()
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finishAffinity()
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

