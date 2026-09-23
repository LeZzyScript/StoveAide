package com.example.stoveaide.data

import android.util.Log
import com.example.stoveaide.models.StoveData
import com.example.stoveaide.models.UserProfile
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.firestore.FirebaseFirestore

object FirestoreManager {
    private const val TAG = "FirestoreManager"
    private const val REALTIME_DATABASE_URL =
        "https://stoveaide-default-rtdb.asia-southeast1.firebasedatabase.app"
    
    val auth: FirebaseAuth?
        get() = try { FirebaseAuth.getInstance() } catch (e: Exception) { null }

    val firestore: FirebaseFirestore?
        get() = try { FirebaseFirestore.getInstance() } catch (e: Exception) { null }

    private val realtimeDatabase: FirebaseDatabase?
        get() = try { FirebaseDatabase.getInstance(REALTIME_DATABASE_URL) } catch (e: Exception) { null }

    fun listenToRealtimeMinutes(onUpdate: (Int) -> Unit, onError: (String) -> Unit = {}) {
        val reference = realtimeDatabase?.getReference("test/data")
        if (reference == null) {
            onError("Realtime Database is not initialized.")
            return
        }

        reference.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                snapshot.getValue(Int::class.java)?.let(onUpdate)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Error listening to Realtime Database", error.toException())
                onError(error.message)
            }
        })
    }

    fun updateRealtimeMinutes(minutes: Int, onResult: (Boolean) -> Unit = {}) {
        val reference = realtimeDatabase?.getReference("test/data")
        if (reference == null) {
            onResult(false)
            return
        }

        reference.setValue(minutes)
            .addOnSuccessListener { onResult(true) }
            .addOnFailureListener { error ->
                Log.e(TAG, "Error writing minutes to Realtime Database", error)
                onResult(false)
            }
    }

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
     * Fetch user profile from Firestore `users/{uid}` with intelligent Auth fallback
     */
    fun getUserProfile(uid: String, onResult: (UserProfile?) -> Unit) {
        val currentUser = auth?.currentUser
        val email = currentUser?.email ?: ""
        val displayName = currentUser?.displayName ?: ""

        val nameParts = if (displayName.isNotBlank()) displayName.trim().split(" ") else emptyList()
        val derivedFirstName = when {
            nameParts.isNotEmpty() -> nameParts.first()
            email.isNotBlank() -> email.substringBefore("@").replace(Regex("[0-9_.]"), "").replaceFirstChar { it.uppercase() }.ifBlank { "User" }
            else -> "User"
        }
        val derivedLastName = if (nameParts.size > 1) nameParts.drop(1).joinToString(" ") else ""
        val derivedFullName = when {
            displayName.isNotBlank() -> displayName
            email.isNotBlank() -> email.substringBefore("@")
            else -> "User"
        }

        val fallbackProfile = UserProfile(
            uid = uid,
            firstName = derivedFirstName,
            lastName = derivedLastName,
            fullName = derivedFullName,
            email = email
        )

        val db = firestore
        if (db == null) {
            onResult(fallbackProfile)
            return
        }

        db.collection("users")
            .document(uid)
            .get()
            .addOnSuccessListener { snapshot ->
                val profile = snapshot.toObject(UserProfile::class.java)
                if (profile != null && (profile.fullName.isNotBlank() || profile.firstName.isNotBlank())) {
                    onResult(profile)
                } else {
                    onResult(fallbackProfile)
                }
            }
            .addOnFailureListener {
                onResult(fallbackProfile)
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
     * Write a user-inputted active minutes value to Firestore as test data.
     * Used to verify that app → Firebase connectivity is working correctly.
     */
    fun setActiveMinutes(stoveId: String, minutes: Int, onResult: (Boolean) -> Unit) {
        val db = firestore
        if (db == null) {
            onResult(false)
            return
        }
        db.collection("stoves").document(stoveId)
            .update(mapOf("activeMinutes" to minutes))
            .addOnSuccessListener {
                Log.d(TAG, "activeMinutes set to $minutes via user input")
                onResult(true)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to set activeMinutes", e)
                // If doc doesn't exist yet, set it instead of update
                db.collection("stoves").document(stoveId)
                    .set(mapOf("activeMinutes" to minutes, "stoveId" to stoveId))
                    .addOnSuccessListener { onResult(true) }
                    .addOnFailureListener { onResult(false) }
            }
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
