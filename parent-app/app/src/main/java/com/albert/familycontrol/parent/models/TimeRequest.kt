package com.albert.familycontrol.parent.models

data class TimeRequest(
    val key: String = "",
    val packageName: String = "",
    val appName: String = "",
    val requestedMinutes: Int = 30,
    val status: String = "pending",   // pending | approved | denied
    val ts: Long = 0L
)
