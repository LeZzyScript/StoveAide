package com.example.stoveaide

import android.content.Intent
import android.os.Bundle
import android.text.Html
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.stoveaide.data.FirestoreManager
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
            handleForgotPassword()
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
        if (auth != null) {
            auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener { task ->
                    setLoading(false)
                    if (task.isSuccessful) {
                        Toast.makeText(this, "Welcome back to StoveAide!", Toast.LENGTH_SHORT).show()
                        startActivity(Intent(this, MainActivity::class.java))
                        finishAffinity()
                    } else {
                        Toast.makeText(this, "Login failed: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                    }
                }
        } else {
            setLoading(false)
            Toast.makeText(this, "Logged in (Demo Mode)", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, MainActivity::class.java))
            finishAffinity()
        }
    }

    private fun handleForgotPassword() {
        val email = binding.etEmail.text.toString().trim()
        if (email.isEmpty()) {
            Toast.makeText(this, "Enter your email address to reset password", Toast.LENGTH_SHORT).show()
            return
        }

        FirestoreManager.auth?.sendPasswordResetEmail(email)
            ?.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Toast.makeText(this, "Password reset email sent to $email", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, "Error: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
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
