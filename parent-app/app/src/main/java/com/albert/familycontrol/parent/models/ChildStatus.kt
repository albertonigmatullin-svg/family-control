package com.albert.familycontrol.parent.models

data class ChildStatus(
    val online: Boolean = false,
    val battery: Int = 0,
    val screenOn: Boolean = false,
    val currentApp: String = "",
    val lastSeen: Long = 0L,
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val locationAccuracy: Float = 0f
)
