package com.sheba.bondhu

import android.net.Uri
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await

class FirebaseRepository {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()

    fun currentUid(): String? = auth.currentUser?.uid
    fun currentEmail(): String = auth.currentUser?.email.orEmpty()
    fun isSignedIn(): Boolean = auth.currentUser != null
    fun signOut() = auth.signOut()

    suspend fun register(email: String, password: String) {
        val result = auth.createUserWithEmailAndPassword(email.trim(), password).await()
        val user = result.user ?: error("অ্যাকাউন্ট তৈরি করা যায়নি")
        db.collection("users").document(user.uid).set(
            mapOf(
                "uid" to user.uid,
                "email" to email.trim(),
                "displayName" to "",
                "phone" to "",
                "address" to "",
                "role" to "provider"
            )
        ).await()
    }

    suspend fun login(email: String, password: String) {
        auth.signInWithEmailAndPassword(email.trim(), password).await()
    }

    suspend fun saveProfile(name: String, phone: String, address: String) {
        val uid = currentUid() ?: error("আগে লগইন করুন")
        db.collection("users").document(uid).set(
            mapOf(
                "uid" to uid,
                "email" to currentEmail(),
                "displayName" to name.trim(),
                "phone" to phone.trim(),
                "address" to address.trim()
            ),
            SetOptions.merge()
        ).await()
    }

    suspend fun uploadPhoto(uri: Uri): String {
        val uid = currentUid() ?: error("আগে লগইন করুন")
        val ref = storage.reference.child("provider-photos/$uid/${System.currentTimeMillis()}.jpg")
        ref.putFile(uri).await()
        return ref.downloadUrl.await().toString()
    }

    suspend fun saveProvider(provider: Provider, existingId: String? = null): String {
        val uid = currentUid() ?: error("আগে লগইন করুন")
        val collection = db.collection("providers")
        val doc = existingId?.let { collection.document(it) } ?: collection.document()
        val data = mapOf(
            "ownerUid" to uid,
            "name" to provider.name.trim(),
            "category" to provider.category.trim(),
            "phone" to provider.phone.trim(),
            "address" to provider.address.trim(),
            "photoUrl" to provider.photoUrl,
            "description" to provider.description.trim(),
            "status" to if (existingId == null) "pending" else provider.status,
            "createdAt" to (if (existingId == null) System.currentTimeMillis() else provider.createdAt)
        )
        if (existingId == null) doc.set(data).await()
        else doc.update(data).await()
        return doc.id
    }

    fun listenApproved(onUpdate: (List<Provider>) -> Unit, onError: (String) -> Unit): ListenerRegistration {
        return db.collection("providers")
            .whereEqualTo("status", "approved")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onError(error.localizedMessage ?: "সেবা লোড করা যায়নি")
                    return@addSnapshotListener
                }
                onUpdate(snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(Provider::class.java)?.copy(id = doc.id)
                }.orEmpty())
            }
    }

    fun listenMyProviders(onUpdate: (List<Provider>) -> Unit, onError: (String) -> Unit): ListenerRegistration {
        val uid = currentUid() ?: return ListenerRegistration { }
        return db.collection("providers")
            .whereEqualTo("ownerUid", uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onError(error.localizedMessage ?: "তথ্য লোড করা যায়নি")
                    return@addSnapshotListener
                }
                onUpdate(snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(Provider::class.java)?.copy(id = doc.id)
                }.orEmpty())
            }
    }

    suspend fun deleteMyProvider(provider: Provider) {
        val uid = currentUid() ?: error("আগে লগইন করুন")
        require(provider.ownerUid == uid) { "নিজের তথ্যই শুধু মুছতে পারবেন" }
        db.collection("providers").document(provider.id).delete().await()
    }

    suspend fun getIsAdmin(): Boolean {
        val user = auth.currentUser ?: return false
        return user.getIdToken(true).await().claims["admin"] == true
    }

    fun listenPending(onUpdate: (List<Provider>) -> Unit, onError: (String) -> Unit): ListenerRegistration {
        return db.collection("providers")
            .whereEqualTo("status", "pending")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onError(error.localizedMessage ?: "অনুমোদনের তালিকা লোড করা যায়নি")
                    return@addSnapshotListener
                }
                onUpdate(snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(Provider::class.java)?.copy(id = doc.id)
                }.orEmpty())
            }
    }

    suspend fun approveProvider(providerId: String) {
        check(getIsAdmin()) { "শুধু অ্যাডমিন অনুমোদন করতে পারবেন" }
        db.collection("providers").document(providerId).update("status", "approved").await()
    }

    suspend fun rejectProvider(providerId: String) {
        check(getIsAdmin()) { "শুধু অ্যাডমিন বাতিল করতে পারবেন" }
        db.collection("providers").document(providerId).delete().await()
    }

    suspend fun adminDeleteProvider(providerId: String) {
        check(getIsAdmin()) { "শুধু অ্যাডমিন মুছতে পারবেন" }
        db.collection("providers").document(providerId).delete().await()
    }
}
