package com.momentum.app.domain

import kotlinx.serialization.Serializable

@Serializable
data class RecurrenceDayRule(
    val dayOfWeek: Int,
    val categories: List<String>,
    val defaultNotes: String? = null,
)

enum class HabitValence { POSITIVE, NEGATIVE }

enum class TrackingMode { BOOLEAN, COUNT, WEIGHT }

enum class SessionStatus { PLANNED, COMPLETED, MISSED }
