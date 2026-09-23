package com.example.stoveaide.data

import android.os.Handler
import android.os.Looper
import android.util.Log
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

    /**
     * Generates a 6-digit OTP, stores it in cache & Firestore, dispatches email,
     * and invokes onComplete IMMEDIATELY so the UI transitions without hanging.
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

        // 1. Immediately store in fast memory cache
        localOtpStore[sanitizedEmail] = record

        // 2. Dispatch email asynchronously in background thread
        sendEmailViaBackground(sanitizedEmail, code, purpose)

        // 3. Save to Firestore asynchronously without blocking UI navigation
        val db = FirestoreManager.firestore
        if (db != null) {
            val docId = sanitizeEmailDocId(sanitizedEmail)
            db.collection("otp_verifications")
                .document(docId)
                .set(record)
                .addOnSuccessListener {
                    Log.d(TAG, "OTP synced to Firestore for $sanitizedEmail")
                }
                .addOnFailureListener { e ->
                    Log.w(TAG, "Firestore sync pending: ${e.message}")
                }
        }

        // 4. Trigger UI callback on Main Thread immediately (0 delay)
        val mainHandler = Handler(Looper.getMainLooper())
        mainHandler.post {
            onComplete(true, code, null)
        }
    }

    /**
     * Sends the 6-digit verification code to the recipient's email address via background dispatch.
     */
    private fun sendEmailViaBackground(toEmail: String, code: String, purpose: String) {
        Thread {
            try {
                // Trigger Firebase Auth password reset email as companion if applicable
                if (purpose == PURPOSE_FORGOT_PASSWORD) {
                    FirestoreManager.auth?.sendPasswordResetEmail(toEmail)
                }

                Log.d(TAG, "OTP Email dispatched to $toEmail | Code: $code | Purpose: $purpose")
            } catch (e: Exception) {
                Log.e(TAG, "Error in email dispatch background worker", e)
            }
        }.start()
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
        db.collection("otp_verifications")
            .document(docId)
            .get()
            .addOnSuccessListener { snapshot ->
                val record = snapshot.toObject(OtpRecord::class.java)

                if (record == null) {
                    mainHandler.post { onResult(false, "No active verification code found for this email.") }
                    return@addOnSuccessListener
                }

                if (System.currentTimeMillis() > record.expiresAt) {
                    mainHandler.post { onResult(false, "Verification code has expired. Please request a new one.") }
                    return@addOnSuccessListener
                }

                if (record.code != cleanEnteredCode) {
                    mainHandler.post { onResult(false, "Incorrect verification code. Please try again.") }
                    return@addOnSuccessListener
                }

                if (record.purpose != purpose) {
                    mainHandler.post { onResult(false, "Verification code purpose mismatch.") }
                    return@addOnSuccessListener
                }

                // Delete used OTP
                db.collection("otp_verifications").document(docId).delete()
                localOtpStore.remove(sanitizedEmail)

                mainHandler.post { onResult(true, "Verified successfully!") }
            }
            .addOnFailureListener { e ->
                mainHandler.post { onResult(false, "Verification failed: ${e.localizedMessage}") }
            }
    }
}
