package com.example.stoveaide.views

import android.os.Bundle
import android.text.Html
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.stoveaide.data.FirestoreManager
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
        val email = binding.etEmail.text.toString().trim().lowercase()

        if (email.isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.etEmail.error = "Please enter a valid email address"
            binding.etEmail.requestFocus()
            return
        }

        setLoading(true)
        val auth = FirestoreManager.auth
        if (auth == null) {
            setLoading(false)
            Toast.makeText(this, "Firebase Authentication is unavailable.", Toast.LENGTH_LONG).show()
            return
        }

        auth.sendPasswordResetEmail(email).addOnCompleteListener { task ->
            setLoading(false)
            if (task.isSuccessful) {
                AlertDialog.Builder(this)
                    .setTitle("Check your email")
                    .setMessage(
                        "If an account exists for this email, Firebase has sent a password reset link. " +
                            "Follow the link, then return here to log in with your new password."
                    )
                    .setPositiveButton("Go to login") { _, _ -> finish() }
                    .show()
            } else {
                Toast.makeText(
                    this,
                    task.exception?.localizedMessage ?: "Unable to send the reset email. Please try again.",
                    Toast.LENGTH_LONG
                ).show()
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
