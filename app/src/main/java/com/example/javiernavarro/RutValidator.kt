package com.example.javiernavarro

/** Chilean RUT format plus modulo-11 check digit. Does not identify a person. */
object RutValidator {
    fun isValid(raw: String): Boolean {
        val normalized = raw.trim().replace(".", "").uppercase()
        if (!Regex("^[0-9]{7,8}-[0-9K]$").matches(normalized)) return false
        val body = normalized.substringBefore('-')
        if (body.all { it == '0' }) return false
        var sum = 0
        var multiplier = 2
        for (digit in body.reversed()) {
            sum += digit.digitToInt() * multiplier
            multiplier = if (multiplier == 7) 2 else multiplier + 1
        }
        val expected = when (val result = 11 - sum % 11) {
            11 -> '0'
            10 -> 'K'
            else -> ('0'.code + result).toChar()
        }
        return normalized.last() == expected
    }
}
