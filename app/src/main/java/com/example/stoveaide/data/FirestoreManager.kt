package com.example.stoveaide.data

import android.content.Context
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

    val realtimeDatabase: FirebaseDatabase?
        get() = try { FirebaseDatabase.getInstance(REALTIME_DATABASE_URL) } catch (e: Exception) { null }

    val rtdb: FirebaseDatabase?
        get() = realtimeDatabase

    fun listenToRealtimeMinutes(stoveId: String = "STOVE-PH-8842", onUpdate: (Int) -> Unit, onError: (String) -> Unit = {}) {
        realtimeDatabase?.getReference("stoves/$stoveId/activeMinutes")
            ?.addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    snapshot.getValue(Int::class.java)?.let(onUpdate)
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.w(TAG, "Error listening to Realtime Database minutes: ${error.message}")
                    onError(error.message)
                }
            })
    }

    fun updateRealtimeMinutes(minutes: Int, stoveId: String = "STOVE-PH-8842", onResult: (Boolean) -> Unit = {}) {
        val updateMap = mapOf<String, Any>("activeMinutes" to minutes)
        realtimeDatabase?.getReference("stoves/$stoveId")?.updateChildren(updateMap)
            ?.addOnCompleteListener { onResult(true) }
        firestore?.collection("stoves")?.document(stoveId)?.update(updateMap)
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

        val docId = if (user.uid.isNotBlank()) user.uid else user.email
        if (docId.isNotBlank()) {
            db.collection("users")
                .document(docId)
                .set(user)
                .addOnSuccessListener {
                    Log.d(TAG, "UserProfile saved to Firestore successfully!")
                    onResult(true, null)
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Error saving UserProfile to Firestore", e)
                    onResult(false, e.localizedMessage)
                }
        } else {
            onResult(true, null)
        }
    }

    /**
     * Resolves the current user's profile across Firebase Auth, Firestore, and LocalAuthManager.
     * Guarantees that whether a user creates an account, confirms a new password, or logs into an
     * existing account, their name, email, and initials update immediately and accurately on the UI.
     */
    fun loadCurrentUserProfile(context: Context, onResult: (UserProfile) -> Unit) {
        val localAuth = LocalAuthManager(context)
        val cached = localAuth.getUserProfile()
        if (cached != null && (cached.fullName.isNotBlank() || cached.firstName.isNotBlank())) {
            onResult(cached)
        }

        val currentUser = auth?.currentUser
        val email = currentUser?.email ?: localAuth.getCurrentEmail() ?: ""
        val uid = currentUser?.uid ?: ""

        val displayName = currentUser?.displayName ?: cached?.fullName ?: ""
        val nameParts = if (displayName.isNotBlank()) displayName.trim().split(" ") else emptyList()
        val derivedFirstName = when {
            nameParts.isNotEmpty() -> nameParts.first()
            cached?.firstName?.isNotBlank() == true -> cached.firstName
            email.isNotBlank() -> email.substringBefore("@").replace(Regex("[0-9_.]"), "").replaceFirstChar { it.uppercase() }.ifBlank { "User" }
            else -> "User"
        }
        val derivedLastName = when {
            nameParts.size > 1 -> nameParts.drop(1).joinToString(" ")
            cached?.lastName?.isNotBlank() == true -> cached.lastName
            else -> ""
        }
        val derivedFullName = when {
            displayName.isNotBlank() -> displayName
            cached?.fullName?.isNotBlank() == true -> cached.fullName
            email.isNotBlank() -> email.substringBefore("@")
            else -> "User"
        }

        val fallbackProfile = UserProfile(
            uid = uid.ifBlank { cached?.uid ?: "" },
            firstName = derivedFirstName,
            lastName = derivedLastName,
            fullName = derivedFullName,
            email = email,
            stoveDeviceId = cached?.stoveDeviceId ?: "STOVE-PH-8842"
        )

        val db = firestore
        if (db == null) {
            localAuth.saveUserProfile(fallbackProfile)
            onResult(fallbackProfile)
            return
        }

        // Try Firestore by UID first, then by email
        if (uid.isNotBlank()) {
            db.collection("users").document(uid).get()
                .addOnSuccessListener { snapshot ->
                    val profile = snapshot.toObject(UserProfile::class.java)
                    if (profile != null && (profile.fullName.isNotBlank() || profile.firstName.isNotBlank())) {
                        localAuth.saveUserProfile(profile)
                        onResult(profile)
                    } else {
                        fetchByEmail(db, email, localAuth, fallbackProfile, onResult)
                    }
                }
                .addOnFailureListener {
                    fetchByEmail(db, email, localAuth, fallbackProfile, onResult)
                }
        } else if (email.isNotBlank()) {
            fetchByEmail(db, email, localAuth, fallbackProfile, onResult)
        } else {
            onResult(fallbackProfile)
        }
    }

    private fun fetchByEmail(
        db: FirebaseFirestore,
        email: String,
        localAuth: LocalAuthManager,
        fallback: UserProfile,
        onResult: (UserProfile) -> Unit
    ) {
        if (email.isBlank()) {
            onResult(fallback)
            return
        }
        db.collection("users").whereEqualTo("email", email).get()
            .addOnSuccessListener { snapshot ->
                val profile = snapshot.documents.firstOrNull()?.toObject(UserProfile::class.java)
                if (profile != null && (profile.fullName.isNotBlank() || profile.firstName.isNotBlank())) {
                    localAuth.saveUserProfile(profile)
                    onResult(profile)
                } else {
                    localAuth.saveUserProfile(fallback)
                    onResult(fallback)
                }
            }
            .addOnFailureListener {
                localAuth.saveUserProfile(fallback)
                onResult(fallback)
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

    fun parseStoveDataFromRtdb(snapshot: DataSnapshot, defaultStoveId: String): StoveData {
        val sId = snapshot.child("stoveId").getValue(String::class.java) ?: defaultStoveId
        val status = snapshot.child("status").getValue(String::class.java) ?: "STANDBY"
        val flameDetected = snapshot.child("flameDetected").getValue(Boolean::class.java) ?: false
        val tempNum = snapshot.child("temperatureCelsius").value
        val temperatureCelsius = when (tempNum) {
            is Number -> tempNum.toFloat()
            else -> 0f
        }
        val gasNum = snapshot.child("gasLevelPpm").value
        val gasLevelPpm = when (gasNum) {
            is Number -> gasNum.toInt()
            else -> 0
        }
        val activeNum = snapshot.child("activeMinutes").value
        val activeMinutes = when (activeNum) {
            is Number -> activeNum.toInt()
            else -> 0
        }
        val maxAlertNum = snapshot.child("maxAlertMinutes").value
        val maxAlertMinutes = when (maxAlertNum) {
            is Number -> maxAlertNum.toInt()
            else -> 20
        }
        val lpgValveOpen = snapshot.child("lpgValveOpen").getValue(Boolean::class.java) ?: false
        val lastCheckIn = (snapshot.child("lastCheckInTimestamp").value as? Number)?.toLong() ?: 0L

        return StoveData(
            stoveId = sId,
            status = status,
            flameDetected = flameDetected,
            temperatureCelsius = temperatureCelsius,
            gasLevelPpm = gasLevelPpm,
            activeMinutes = activeMinutes,
            maxAlertMinutes = maxAlertMinutes,
            lpgValveOpen = lpgValveOpen,
            lastCheckInTimestamp = lastCheckIn
        )
    }

    /**
     * Listen to real-time IoT stove sensor updates from Realtime Database `stoves/{stoveId}`
     * and Cloud Firestore `stoves/{stoveId}` for instant bidirectional updates.
     */
    fun listenToStoveData(stoveId: String, onUpdate: (StoveData) -> Unit) {
        // 1. Listen to Realtime Database (instant updates from ESP32 & Web Demo)
        realtimeDatabase?.getReference("stoves/$stoveId")
            ?.addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        val stoveData = parseStoveDataFromRtdb(snapshot, stoveId)
                        onUpdate(stoveData)
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.w(TAG, "RTDB listenToStoveData error: ${error.message}")
                }
            })

        // 2. Listen to Cloud Firestore as complementary source
        val db = firestore
        if (db != null) {
            db.collection("stoves")
                .document(stoveId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.e(TAG, "Error listening to Firestore stove data", error)
                        return@addSnapshotListener
                    }

                    if (snapshot != null && snapshot.exists()) {
                        val data = snapshot.toObject(StoveData::class.java)
                        if (data != null) {
                            onUpdate(data)
                        }
                    }
                }
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
