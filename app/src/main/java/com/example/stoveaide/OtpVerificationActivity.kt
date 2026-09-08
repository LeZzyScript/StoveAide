package com.example.stoveaide

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.stoveaide.data.FirestoreManager
import com.example.stoveaide.data.OtpManager
import com.example.stoveaide.databinding.ActivityOtpVerificationBinding
import com.example.stoveaide.models.UserProfile

class OtpVerificationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOtpVerificationBinding
    private var countDownTimer: CountDownTimer? = null

    private var email: String = ""
    private var purpose: String = OtpManager.PURPOSE_REGISTRATION
    private var fullName: String = ""
    private var password: String = ""

    companion object {
        const val EXTRA_EMAIL = "extra_email"
        const val EXTRA_PURPOSE = "extra_purpose"
        const val EXTRA_FULL_NAME = "extra_full_name"
        const val EXTRA_PASSWORD = "extra_password"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOtpVerificationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        email = intent.getStringExtra(EXTRA_EMAIL) ?: ""
        purpose = intent.getStringExtra(EXTRA_PURPOSE) ?: OtpManager.PURPOSE_REGISTRATION
        fullName = intent.getStringExtra(EXTRA_FULL_NAME) ?: ""
        password = intent.getStringExtra(EXTRA_PASSWORD) ?: ""

        binding.tvTargetEmail.text = email

        if (purpose == OtpManager.PURPOSE_FORGOT_PASSWORD) {
            binding.tvTitle.text = "Reset Password Verification"
            binding.tvSubtitle.text = "Enter the 6-digit code sent to verify your identity before resetting your password."
        }

        binding.btnBack.setOnClickListener {
            finish()
        }

        setupOtpInputLogic()
        startResendCountdown()

        binding.btnResendCode.setOnClickListener {
            resendCode()
        }

        binding.btnVerifyOtp.setOnClickListener {
            performVerification()
        }
    }

    private fun setupOtpInputLogic() {
        val digitBoxes = listOf(
            binding.etDigit1,
            binding.etDigit2,
            binding.etDigit3,
            binding.etDigit4,
            binding.etDigit5,
            binding.etDigit6
        )

        for (i in digitBoxes.indices) {
            val current = digitBoxes[i]

            current.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    if (s?.length == 1 && i < digitBoxes.size - 1) {
                        digitBoxes[i + 1].requestFocus()
                    }
                }
                override fun afterTextChanged(s: Editable?) {}
            })

            current.setOnKeyListener { _, keyCode, event ->
                if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DEL) {
                    if (current.text.isNullOrEmpty() && i > 0) {
                        digitBoxes[i - 1].requestFocus()
                        digitBoxes[i - 1].setText("")
                        return@setOnKeyListener true
                    }
                }
                false
            }
        }
    }

    private fun getEnteredOtp(): String {
        return "${binding.etDigit1.text}${binding.etDigit2.text}${binding.etDigit3.text}${binding.etDigit4.text}${binding.etDigit5.text}${binding.etDigit6.text}".trim()
    }

    private fun startResendCountdown() {
        binding.btnResendCode.visibility = View.GONE
        binding.tvResendTimer.visibility = View.VISIBLE

        countDownTimer?.cancel()
        countDownTimer = object : CountDownTimer(60000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val seconds = millisUntilFinished / 1000
                binding.tvResendTimer.text = String.format("Resend code in 00:%02d", seconds)
            }

            override fun onFinish() {
                binding.tvResendTimer.visibility = View.GONE
                binding.btnResendCode.visibility = View.VISIBLE
            }
        }.start()
    }

    private fun resendCode() {
        setLoading(true)
        OtpManager.generateAndSendOtp(email, purpose) { success, code, error ->
            setLoading(false)
            if (success) {
                Toast.makeText(this, "New 6-digit code sent! (Code: $code)", Toast.LENGTH_LONG).show()
                startResendCountdown()
            } else {
                Toast.makeText(this, "Failed to send code: $error", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun performVerification() {
        val enteredCode = getEnteredOtp()
        if (enteredCode.length < 6) {
            Toast.makeText(this, "Please enter all 6 digits of the code", Toast.LENGTH_SHORT).show()
            return
        }

        setLoading(true)
        OtpManager.verifyOtp(email, enteredCode, purpose) { isValid, message ->
            if (!isValid) {
                setLoading(false)
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                return@verifyOtp
            }

            // Successfully verified!
            if (purpose == OtpManager.PURPOSE_REGISTRATION) {
                completeRegistration()
            } else {
                // Purpose == FORGOT_PASSWORD
                setLoading(false)
                Toast.makeText(this, "Identity verified! Please choose a new password.", Toast.LENGTH_SHORT).show()
                val resetIntent = Intent(this, ResetPasswordActivity::class.java).apply {
                    putExtra(ResetPasswordActivity.EXTRA_EMAIL, email)
                }
                startActivity(resetIntent)
                finish()
            }
        }
    }

    private fun completeRegistration() {
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
                        FirestoreManager.saveUserProfile(userProfile) { success, _ ->
                            setLoading(false)
                            Toast.makeText(this, "Email verified! Welcome to StoveAide!", Toast.LENGTH_SHORT).show()
                            startActivity(Intent(this, MainActivity::class.java))
                            finishAffinity()
                        }
                    } else {
                        setLoading(false)
                        Toast.makeText(this, "Registration error: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                    }
                }
        } else {
            // Local Demo Mode
            setLoading(false)
            Toast.makeText(this, "Email verified! Welcome (Demo Mode)", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, MainActivity::class.java))
            finishAffinity()
        }
    }

    private fun setLoading(isLoading: Boolean) {
        if (isLoading) {
            binding.progressBar.visibility = View.VISIBLE
            binding.btnVerifyOtp.visibility = View.INVISIBLE
        } else {
            binding.progressBar.visibility = View.GONE
            binding.btnVerifyOtp.visibility = View.VISIBLE
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
    }
}
