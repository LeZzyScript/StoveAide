package com.example.stoveaide.views

import android.content.Intent
import android.os.Bundle
import android.text.Html
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.stoveaide.data.FirestoreManager
import com.example.stoveaide.data.LocalAuthManager
import com.example.stoveaide.databinding.ActivityLoginBinding

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Format footer link text
        binding.tvRegisterLink.text = Html.fromHtml("New here? <font color='#38A1FF'>Create an account</font>", Html.FROM_HTML_MODE_LEGACY)

        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.tvRegisterLink.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
            finish()
        }

        binding.tvForgotPassword.setOnClickListener {
            startActivity(Intent(this, ForgotPasswordActivity::class.java))
        }

        binding.btnSubmitLogin.setOnClickListener {
            performLogin()
        }
    }

    private fun performLogin() {
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()

        if (email.isEmpty()) {
            binding.etEmail.error = "Please enter your email"
            return
        }

        if (password.isEmpty()) {
            binding.etPassword.error = "Please enter your password"
            return
        }

        setLoading(true)
        val auth = FirestoreManager.auth
        if (auth == null) {
            setLoading(false)
            Toast.makeText(this, "Firebase Authentication is unavailable.", Toast.LENGTH_LONG).show()
            return
        }

        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val localAuth = LocalAuthManager(this)
                    localAuth.updatePassword(email, password)
                    localAuth.authenticate(email, password)
                    localAuth.setCurrentEmail(email)

                    // Pre-cache profile so MainActivity and DashboardFragment load it instantly
                    FirestoreManager.loadCurrentUserProfile(this) { profile ->
                        localAuth.saveUserProfile(profile)
                    }

                    setLoading(false)
                    Toast.makeText(this, "Welcome back to StoveAide!", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this, MainActivity::class.java))
                    finishAffinity()
                } else {
                    setLoading(false)
                    Toast.makeText(
                        this,
                        task.exception?.localizedMessage ?: "Login failed. Check your email and password.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
    }

    private fun setLoading(isLoading: Boolean) {
        if (isLoading) {
            binding.progressBar.visibility = View.VISIBLE
            binding.btnSubmitLogin.visibility = View.INVISIBLE
        } else {
            binding.progressBar.visibility = View.GONE
            binding.btnSubmitLogin.visibility = View.VISIBLE
        }
    }
}
