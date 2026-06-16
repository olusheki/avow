package com.avow.app.data.history

data class VowSession(
    val id: Int = 0,
    val startTimeMillis: Long,
    val endTimeMillis: Long,
    val durationSeconds: Long,
    val pickups: Int,
    val allowedScreenTimeMs: Long,
    val zenScore: Int
)
