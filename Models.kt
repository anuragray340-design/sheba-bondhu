package com.sheba.bondhu

data class Provider(
    val id: String = "",
    val ownerUid: String = "",
    val name: String = "",
    val category: String = "",
    val phone: String = "",
    val address: String = "",
    val photoUrl: String = "",
    val description: String = "",
    val status: String = "pending",
    val createdAt: Long = 0L
)

data class UserProfile(
    val uid: String = "",
    val email: String = "",
    val displayName: String = "",
    val phone: String = "",
    val address: String = "",
    val role: String = "provider"
)
