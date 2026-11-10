package com.example.javiernavarro

import android.content.Context
import org.json.JSONArray

/** Hasta 20 solicitudes por cuenta. Nunca borra por error de red o de descifrado. */
class CommandOutbox(context: Context, userId: String) {
    private val store = EncryptedUserStore(context, userId, "agro_command_outbox")
    companion object {
        private val lock = Any()
    }

    fun pending(): List<Command> = synchronized(lock) {
        val doc = JSONArray(store.read() ?: "[]")
        require(doc.length() <= 20)
        (0 until doc.length()).map { Command.parse(doc.getJSONObject(it)) }
    }
    private fun save(values: List<Command>) {
        val doc = JSONArray()
        values.forEach { doc.put(it.json()) }
        check(store.write(doc.toString())) { "No se pudo guardar la cola cifrada." }
    }
    fun append(command: Command) = synchronized(lock) {
        val values = pending()
        check(values.size < 20) { "Hay 20 registros pendientes. Recupera Internet para sincronizarlos." }
        check(values.none { it.id == command.id })
        save(values + command)
    }
    fun remove(id: String) = synchronized(lock) { save(pending().filterNot { it.id == id }) }
    fun clear() = synchronized(lock) { store.clear() }
}
