package com.example.javiernavarro

import android.content.Context

/** Una lectura por usuario; nunca sustituye el estado real por un valor simulado. */
class ReadingCache(context: Context, userId: String) {
    private val store = EncryptedUserStore(context, userId, "agro_reading_cache")
    fun save(reading: Reading): Boolean = store.write(reading.json)
    fun load(): Reading? = try { store.read()?.let { Reading.parse(it) } } catch (_: Exception) { null }
    fun clear() { store.clear() }
}
