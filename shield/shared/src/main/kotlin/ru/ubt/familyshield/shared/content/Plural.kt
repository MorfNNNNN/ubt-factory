package ru.ubt.familyshield.shared.content

import kotlin.math.abs

/** Russian plural form for [n]: plural(5, "запрос", "запроса", "запросов") = "запросов". */
fun plural(n: Int, one: String, few: String, many: String): String {
    val mod100 = abs(n) % 100
    val mod10 = mod100 % 10
    return when {
        mod100 in 11..14 -> many
        mod10 == 1 -> one
        mod10 in 2..4 -> few
        else -> many
    }
}
