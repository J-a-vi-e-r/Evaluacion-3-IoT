package com.example.javiernavarro

import org.json.JSONObject
import java.util.UUID

/** La copia persistida SIEMPRE es un registro; solo el envío interactivo puede ejecutar. */
data class Command(val id: String, val mode: String, val created: Long, val watering: Boolean = false) {
    fun json(execute: Boolean = false): JSONObject = JSONObject().apply {
        put("id", id); put("modo", mode); put("creado_en", created); put("ejecutar", execute)
        if (mode == "manual") {
            put("riego", watering)
            if (watering) put("duracion_segundos", 20)
        }
    }
    companion object {
        fun create(mode: String, watering: Boolean = false): Command {
            require(mode == "automatico" || mode == "manual")
            return Command(UUID.randomUUID().toString(), mode, System.currentTimeMillis() / 1000, watering)
        }
        fun parse(doc: JSONObject): Command {
            val id = doc.getString("id")
            val mode = doc.getString("modo")
            val created = doc.getLong("creado_en")
            require(id.matches(Regex("[A-Za-z0-9_-]{8,64}")) && mode in listOf("automatico", "manual") && created > 0)
            require(doc.opt("ejecutar") == false)
            return Command(id, mode, created, if (mode == "manual") doc.getBoolean("riego") else false)
        }
    }
}

data class CommandReceipt(val id: String, val status: String, val code: String?) {
    companion object {
        fun parse(doc: JSONObject, expectedId: String): CommandReceipt {
            require(doc.getString("id") == expectedId)
            val status = doc.getString("estado")
            require(status in listOf("publicado", "registro_sin_ejecucion", "caducado_sin_ejecucion",
                "sin_dispositivo_reciente", "pendiente_publicacion", "enviando", "envio_no_confirmado"))
            return CommandReceipt(expectedId, status, doc.optString("codigo").takeIf { it.isNotBlank() })
        }
    }
}
