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
        val fullName = binding.etFullName.text.toString().trim()
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()

        if (fullName.isEmpty()) {
            binding.etFullName.error = "Please enter your full name"
            return
        }

        if (email.isEmpty()) {
            binding.etEmail.error = "Please enter your email"
            return
        }

        if (password.length < 6) {
            binding.etPassword.error = "Password must be at least 6 characters"
            return
        }

        setLoading(true)

        val auth = FirestoreManager.auth
        if (auth != null) {
            auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val firebaseUser = task.result?.user
                        val userProfile = UserProfile(
                            uid = firebaseUser?.uid ?: "",
                            fullName = fullName,
                            email = email
                        )
                        // Save to Cloud Firestore
                        FirestoreManager.saveUserProfile(userProfile) { success, error ->
                            setLoading(false)
                            if (success) {
                                Toast.makeText(this, "Account created successfully!", Toast.LENGTH_SHORT).show()
                                startActivity(Intent(this, MainActivity::class.java))
                                finishAffinity()
                            } else {
                                Toast.makeText(this, "Firestore error: $error", Toast.LENGTH_LONG).show()
                                startActivity(Intent(this, MainActivity::class.java))
                                finishAffinity()
                            }
                        }
                    } else {
                        setLoading(false)
                        Toast.makeText(this, "Registration failed: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                    }
                }
        } else {
            // Local Demo Mode fallback
            setLoading(false)
            Toast.makeText(this, "Account created (Demo Mode)", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, MainActivity::class.java))
            finishAffinity()
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
