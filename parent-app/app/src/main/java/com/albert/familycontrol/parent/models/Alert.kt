package com.albert.familycontrol.parent.models

data class Alert(
    val key: String = "",
    val type: String = "",
    val title: String = "",
    val body: String = "",
    val ts: Long = 0L,
    val image: String? = null
)
