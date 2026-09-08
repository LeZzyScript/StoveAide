package com.example.stoveaide.data

import android.util.Log
import com.example.stoveaide.models.StoveData
import com.example.stoveaide.models.UserProfile
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

object FirestoreManager {
    private const val TAG = "FirestoreManager"
    
    val auth: FirebaseAuth?
        get() = try { FirebaseAuth.getInstance() } catch (e: Exception) { null }

    val firestore: FirebaseFirestore?
        get() = try { FirebaseFirestore.getInstance() } catch (e: Exception) { null }

    /**
     * Save user profile to Firestore `users/{uid}` collection
     */
    fun saveUserProfile(user: UserProfile, onResult: (Boolean, String?) -> Unit) {
        val db = firestore
        if (db == null) {
            Log.w(TAG, "Firestore not initialized. Operating in local mode.")
            onResult(true, null)
            return
        }

        db.collection("users")
            .document(user.uid)
            .set(user)
            .addOnSuccessListener {
                Log.d(TAG, "UserProfile saved to Firestore successfully!")
                onResult(true, null)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error saving UserProfile to Firestore", e)
                onResult(false, e.localizedMessage)
            }
    }

    /**
     * Fetch user profile from Firestore `users/{uid}`
     */
    fun getUserProfile(uid: String, onResult: (UserProfile?) -> Unit) {
        val db = firestore
        if (db == null) {
            onResult(UserProfile(uid = uid, firstName = "Juan", lastName = "Dela Cruz", fullName = "Juan Dela Cruz", email = "juan@example.com"))
            return
        }

        db.collection("users")
            .document(uid)
            .get()
            .addOnSuccessListener { snapshot ->
                val profile = snapshot.toObject(UserProfile::class.java)
                onResult(profile ?: UserProfile(uid = uid, firstName = "Juan", lastName = "Dela Cruz", fullName = "Juan Dela Cruz"))
            }
            .addOnFailureListener {
                onResult(UserProfile(uid = uid, firstName = "Juan", lastName = "Dela Cruz", fullName = "Juan Dela Cruz"))
            }
    }

    /**
     * Listen to real-time IoT stove sensor updates from Firestore `stoves/{stoveId}`
     */
    fun listenToStoveData(stoveId: String, onUpdate: (StoveData) -> Unit) {
        val db = firestore
        if (db == null) {
            onUpdate(StoveData(stoveId = stoveId))
            return
        }

        db.collection("stoves")
            .document(stoveId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error listening to stove data", error)
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    val data = snapshot.toObject(StoveData::class.java)
                    if (data != null) {
                        onUpdate(data)
                        return@addSnapshotListener
                    }
                }
                
                // Initialize stove doc if not present
                val initialData = StoveData(stoveId = stoveId)
                db.collection("stoves").document(stoveId).set(initialData)
                onUpdate(initialData)
            }
    }

    /**
     * Reset/Acknowledge cooking check-in timer
     */
    fun checkInStove(stoveId: String, currentMinutes: Int = 0) {
        val db = firestore ?: return
        db.collection("stoves").document(stoveId)
            .update(
                mapOf(
                    "activeMinutes" to currentMinutes,
                    "lastCheckInTimestamp" to System.currentTimeMillis(),
                    "status" to "NORMAL"
                )
            )
    }

    /**
     * Emergency LPG shutoff valve update in Firestore
     */
    fun toggleLpgValve(stoveId: String, openState: Boolean) {
        val db = firestore ?: return
        db.collection("stoves").document(stoveId)
            .update(
                mapOf(
                    "lpgValveOpen" to openState,
                    "status" to if (!openState) "EMERGENCY_SHUTOFF" else "NORMAL"
                )
            )
    }
}
