package com.example.javiernavarro

import org.json.JSONObject

data class Reading(
    val json: String,
    val temperature: Double?,
    val air: Double?,
    val soil: Double,
    val sampledAt: Long?,
    val receivedAt: Long,
    val automatic: Boolean,
    val watering: Boolean,
    val modelled: Boolean,
    val alerts: List<String>,
    val commandId: String? = null,
    val commandResult: String? = null,
    val controlReady: Boolean = false,
    val controlUntil: Long = 0L
) {
    fun isRecent(now: Long = System.currentTimeMillis()): Boolean =
        controlReady && controlUntil - now in 0L..45000L

    companion object {
        fun parse(json: String): Reading {
            val doc = JSONObject(json)
            require(doc.getString("device_id") == "AgroRiegoESP32") { "Dispositivo inesperado" }
            fun number(key: String): Double? =
                (doc.opt(key) as? Number)?.toDouble()?.takeIf { it.isFinite() }
            fun flag(key: String): Boolean =
                doc.opt(key) as? Boolean ?: throw IllegalArgumentException("Falta " + key)
            val valid = flag("sensor_dht_valido")
            val temperature = number("temperatura")
            val air = number("humedad_aire")
            val soil = number("humedad_suelo")
            require(soil != null && soil in 0.0..100.0) { "Humedad de suelo inválida" }
            require(!valid || (temperature != null && temperature in -40.0..80.0 && air != null && air in 0.0..100.0)) {
                "Lectura ambiental incompleta"
            }
            val received = (doc.opt("recibido_en") as? Number)?.toLong()
            require(received != null && received > 0) { "Falta la fecha de recepción" }
            val mode = doc.getString("modo")
            require(mode == "automatico" || mode == "manual") { "Modo inválido" }
            val alerts = mutableListOf<String>()
            if (!valid) alerts.add("El DHT22 informa una lectura ausente o inválida.")
            if (flag("alerta_calor")) alerts.add("Calor: revisa ventilación, sombra y las necesidades de la planta.")
            if (flag("alerta_frio")) alerts.add("Frío: revisa la protección de la planta.")
            if (flag("alerta_aire_seco")) alerts.add("Humedad del aire baja.")
            if (flag("alerta_aire_humedo")) alerts.add("Humedad del aire alta.")
            return Reading(json, if (valid) temperature else null, if (valid) air else null, soil,
                (doc.opt("timestamp") as? Number)?.toLong()?.takeIf { it > 0 },
                received, mode == "automatico", flag("riego_activo"),
                flag("humedad_suelo_modelada"), alerts,
                (doc.opt("comando_id") as? String)?.takeIf { it.isNotBlank() },
                doc.opt("resultado_comando") as? String,
                doc.opt("control_disponible") == true,
                (doc.opt("control_valido_hasta_ms") as? Number)?.toLong() ?: 0L)
        }
    }
}
