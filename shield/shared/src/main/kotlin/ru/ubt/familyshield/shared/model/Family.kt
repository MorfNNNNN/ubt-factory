package ru.ubt.familyshield.shared.model

import kotlinx.serialization.Serializable

@Serializable
enum class MemberRole {
    /** Adult who manages the family (parent of a child or child of an elderly parent). */
    GUARDIAN,

    /** Child or teenager device. */
    CHILD,

    /** Elderly relative device. */
    ELDER;

    val isProtected: Boolean get() = this != GUARDIAN
}

@Serializable
enum class AgeBand(val minAge: Int, val maxAge: Int) {
    UNDER_7(0, 6),
    AGE_7_9(7, 9),
    AGE_10_12(10, 12),
    AGE_13_15(13, 15),
    AGE_16_17(16, 17);

    val isTeen: Boolean get() = minAge >= 13

    companion object {
        fun forAge(age: Int): AgeBand = entries.firstOrNull { age in it.minAge..it.maxAge } ?: AGE_16_17
    }
}

@Serializable
data class MemberProfile(
    val deviceId: String,
    val role: MemberRole,
    val displayName: String,
    val avatar: String = "",
    val birthYear: Int? = null,
    val deviceModel: String = "",
    val androidVersion: Int = 0,
    val appVersion: String = "",
    /** Guardian's phone number for SOS and "call family" buttons. Never sent to the server in clear text. */
    val phone: String? = null,
)

@Serializable
data class TrustedContact(
    val name: String,
    val phone: String,
    val isSosTarget: Boolean = true,
    val photoEmoji: String = "",
)

@Serializable
data class GeoPoint(val lat: Double, val lon: Double)

@Serializable
data class Geofence(
    val id: String,
    val name: String,
    val center: GeoPoint,
    val radiusMeters: Int = 200,
    val notifyOnEnter: Boolean = true,
    val notifyOnExit: Boolean = true,
)
