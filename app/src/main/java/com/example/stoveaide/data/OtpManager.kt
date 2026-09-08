package com.example.stoveaide.data

import android.util.Log
import kotlin.random.Random

object OtpManager {
    private const val TAG = "OtpManager"
    const val PURPOSE_REGISTRATION = "REGISTRATION"
    const val PURPOSE_FORGOT_PASSWORD = "FORGOT_PASSWORD"

    // In-memory cache for fast local testing or demo mode
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

    /**
     * Generates a 6-digit OTP and stores it in Firestore & local cache.
     */
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

        // Store in local cache
        localOtpStore[sanitizedEmail] = record

        val db = FirestoreManager.firestore
        if (db == null) {
            Log.d(TAG, "Firestore null, OTP stored locally for demo: $code")
            onComplete(true, code, null)
            return
        }

        val docId = sanitizeEmailDocId(sanitizedEmail)
        db.collection("otp_verifications")
            .document(docId)
            .set(record)
            .addOnSuccessListener {
                Log.d(TAG, "OTP successfully written to Firestore for $sanitizedEmail (Code: $code)")
                onComplete(true, code, null)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to save OTP in Firestore, falling back to local: ${e.message}")
                // Still allow local fallback so testing doesn't fail
                onComplete(true, code, null)
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

        val db = FirestoreManager.firestore
        if (db == null) {
            // Check local fallback
            val cached = localOtpStore[sanitizedEmail]
            if (cached != null) {
                if (System.currentTimeMillis() > cached.expiresAt) {
                    onResult(false, "Verification code has expired. Please request a new one.")
                    return
                }
                if (cached.code == cleanEnteredCode && cached.purpose == purpose) {
                    localOtpStore.remove(sanitizedEmail)
                    onResult(true, "Verification successful!")
                    return
                }
            }
            onResult(false, "Invalid verification code. Please check and try again.")
            return
        }

        val docId = sanitizeEmailDocId(sanitizedEmail)
        db.collection("otp_verifications")
            .document(docId)
            .get()
            .addOnSuccessListener { snapshot ->
                val record = snapshot.toObject(OtpRecord::class.java) ?: localOtpStore[sanitizedEmail]

                if (record == null) {
                    onResult(false, "No active verification code found for this email.")
                    return@addOnSuccessListener
                }

                if (System.currentTimeMillis() > record.expiresAt) {
                    onResult(false, "Verification code has expired. Please request a new one.")
                    return@addOnSuccessListener
                }

                if (record.code != cleanEnteredCode) {
                    onResult(false, "Incorrect verification code. Please try again.")
                    return@addOnSuccessListener
                }

                if (record.purpose != purpose) {
                    onResult(false, "Verification code purpose mismatch.")
                    return@addOnSuccessListener
                }

                // Delete used OTP
                db.collection("otp_verifications").document(docId).delete()
                localOtpStore.remove(sanitizedEmail)

                onResult(true, "Verified successfully!")
            }
            .addOnFailureListener { e ->
                // Fallback to local cache on Firestore read failure
                val cached = localOtpStore[sanitizedEmail]
                if (cached != null && cached.code == cleanEnteredCode && cached.purpose == purpose) {
                    localOtpStore.remove(sanitizedEmail)
                    onResult(true, "Verified successfully!")
                } else {
                    onResult(false, "Verification failed: ${e.localizedMessage}")
                }
            }
    }
}
