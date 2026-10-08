package com.personal.caller.data

data class Contact(
    val id: String,
    val displayName: String,
    val phoneNumber: String?,
    val photoUri: String? = null
)
