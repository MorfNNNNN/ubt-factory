package ru.ubt.familyshield.shared

import kotlinx.serialization.json.Json

/** Single JSON configuration shared by the app and the server so both sides agree on the wire format. */
val ShieldJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
    classDiscriminator = "kind"
}
