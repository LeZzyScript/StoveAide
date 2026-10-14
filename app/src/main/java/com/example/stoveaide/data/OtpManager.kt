package com.example.stoveaide.data

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.Source
import kotlin.random.Random

object OtpManager {
    private const val TAG = "OtpManager"
    const val PURPOSE_REGISTRATION = "REGISTRATION"
    const val PURPOSE_FORGOT_PASSWORD = "FORGOT_PASSWORD"

    // In-memory cache for instantaneous validation and offline reliability
    private val localOtpStore = mutableMapOf<String, OtpRecord>()

    data class OtpRecord(
        val email: String = "",
        val code: String = "",
        val purpose: String = "",
        val expiresAt: Long = 0L,
        val createdAt: Long = System.currentTimeMillis()
    )

    private fun sanitizeEmailDocId(email: String): String {
        return email.trim().lowercase().replace(".", "_").replace("@", "_at_")
    }

    /** Generates and persists an OTP before sending it and reporting success. */
    fun generateAndSendOtp(
        email: String,
        purpose: String,
        onComplete: (success: Boolean, code: String, error: String?) -> Unit
    ) {
        val sanitizedEmail = email.trim().lowercase()
        val code = String.format("%06d", Random.nextInt(100000, 1000000))
        val expiresAt = System.currentTimeMillis() + (10 * 60 * 1000) // 10 minutes validity

        val record = OtpRecord(
            email = sanitizedEmail,
            code = code,
            purpose = purpose,
            expiresAt = expiresAt,
            createdAt = System.currentTimeMillis()
        )

        // 1. Immediately store in fast memory cache
        localOtpStore[sanitizedEmail] = record

        // 2. Save to Firestore in the background (we don't wait for this to complete before sending email)
        val db = FirestoreManager.firestore
        if (db != null) {
            val docId = sanitizeEmailDocId(sanitizedEmail)
            db.collection("otp_verifications")
                .document(docId)
                .set(record)
                .addOnSuccessListener {
                    Log.d(TAG, "OTP synced to Firestore for $sanitizedEmail")
                }
                .addOnFailureListener { error ->
                    Log.e(TAG, "Failed to persist OTP for $sanitizedEmail", error)
                    // Note: We don't remove from local cache here because we might still want to use it for verification?
                    // We'll rely on the local cache and the fact that we can still verify from local cache.
                }
        }

        // 3. Send email in background
        sendEmailViaBackground(sanitizedEmail, code, purpose, onComplete)
    }

    /**
     * Dispatches OTP email asynchronously via EmailSender (JavaMail SMTP).
     */
    private fun sendEmailViaBackground(
        toEmail: String,
        code: String,
        purpose: String,
        onComplete: (success: Boolean, code: String, error: String?) -> Unit
    ) {
        Log.d(TAG, "Dispatching OTP email to $toEmail | Purpose: $purpose via EmailSender")
        EmailSender.sendOtpEmail(toEmail, code, purpose) { success, err ->
            if (success) {
                Log.d(TAG, "OTP email successfully delivered to $toEmail")
            } else {
                Log.w(TAG, "Email dispatch result: $err")
                localOtpStore.remove(toEmail)
                FirestoreManager.firestore?.collection("otp_verifications")
                    ?.document(sanitizeEmailDocId(toEmail))?.delete()
            }
            Handler(Looper.getMainLooper()).post {
                onComplete(success, if (success) code else "", err)
            }
        }
    }

    /**
     * Verifies the 6-digit OTP entered by user.
     */
    fun verifyOtp(
        email: String,
        enteredCode: String,
        purpose: String,
        onResult: (isValid: Boolean, message: String) -> Unit
    ) {
        val sanitizedEmail = email.trim().lowercase()
        val cleanEnteredCode = enteredCode.trim()
        val mainHandler = Handler(Looper.getMainLooper())

        // 1. Immediate validation against fast memory cache
        val cached = localOtpStore[sanitizedEmail]
        if (cached != null) {
            if (System.currentTimeMillis() > cached.expiresAt) {
                mainHandler.post { onResult(false, "Verification code has expired. Please request a new one.") }
                return
            }
            if (cached.code == cleanEnteredCode && cached.purpose == purpose) {
                localOtpStore.remove(sanitizedEmail)
                
                // Clean up Firestore doc in background
                FirestoreManager.firestore?.collection("otp_verifications")
                    ?.document(sanitizeEmailDocId(sanitizedEmail))?.delete()

                mainHandler.post { onResult(true, "Verification successful!") }
                return
            } else if (cached.code != cleanEnteredCode) {
                mainHandler.post { onResult(false, "Incorrect verification code. Please check and try again.") }
                return
            }
        }

        // 2. Fallback to Firestore if not in memory
        val db = FirestoreManager.firestore
        if (db == null) {
            mainHandler.post { onResult(false, "Invalid verification code. Please request a new one.") }
            return
        }

        val docId = sanitizeEmailDocId(sanitizedEmail)
        val otpDocument = db.collection("otp_verifications").document(docId)
        otpDocument.get(Source.CACHE)
            .addOnSuccessListener { snapshot ->
                if (snapshot.exists()) {
                    validateStoredOtp(snapshot, sanitizedEmail, cleanEnteredCode, purpose, onResult)
                } else {
                    otpDocument.get()
                        .addOnSuccessListener { serverSnapshot ->
                            validateStoredOtp(serverSnapshot, sanitizedEmail, cleanEnteredCode, purpose, onResult)
                        }
                        .addOnFailureListener { error ->
                            mainHandler.post { onResult(false, "Verification failed: ${error.localizedMessage}") }
                        }
                }
            }
            .addOnFailureListener {
                otpDocument.get()
                    .addOnSuccessListener { snapshot ->
                        validateStoredOtp(snapshot, sanitizedEmail, cleanEnteredCode, purpose, onResult)
                    }
                    .addOnFailureListener { error ->
                        mainHandler.post { onResult(false, "Verification failed: ${error.localizedMessage}") }
                    }
            }
    }

    private fun validateStoredOtp(
        snapshot: DocumentSnapshot,
        email: String,
        enteredCode: String,
        purpose: String,
        onResult: (isValid: Boolean, message: String) -> Unit
    ) {
        val record = snapshot.toObject(OtpRecord::class.java)
        val errorMessage = when {
            record == null -> "No active verification code found for this email."
            System.currentTimeMillis() > record.expiresAt -> "Verification code has expired. Please request a new one."
            record.code != enteredCode -> "Incorrect verification code. Please try again."
            record.purpose != purpose -> "Verification code purpose mismatch."
            else -> null
        }

        if (errorMessage != null) {
            Handler(Looper.getMainLooper()).post { onResult(false, errorMessage) }
            return
        }

        snapshot.reference.delete()
        localOtpStore.remove(email)
        Handler(Looper.getMainLooper()).post { onResult(true, "Verified successfully!") }
    }
}
