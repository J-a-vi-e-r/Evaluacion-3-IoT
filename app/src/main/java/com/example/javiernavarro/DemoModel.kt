package com.example.javiernavarro

/** Fixed fixtures for UI exploration. These are never presented as ESP32 readings. */
data class DemoReading(val temperature: Int, val airHumidity: Int, val soilHumidity: Int, val label: String)
data class DemoThresholds(val startBelow: Int = 35, val stopAt: Int = 55, val hotAt: Int = 32) {
    fun isValid(): Boolean = startBelow in 1..99 && stopAt in 2..100 && startBelow < stopAt && hotAt in 1..60
}

object DemoModel {
    val scenarios = listOf(
        DemoReading(26, 62, 48, "condiciones estables"),
        DemoReading(34, 35, 22, "calor y tierra seca"),
        DemoReading(24, 70, 68, "tierra húmeda")
    )

    /** Hysteresis: start below the lower limit and stop at the upper limit. */
    fun automaticPump(reading: DemoReading, thresholds: DemoThresholds, wasRunning: Boolean): Boolean = when {
        reading.soilHumidity >= thresholds.stopAt -> false
        reading.soilHumidity < thresholds.startBelow -> true
        else -> wasRunning
    }
}
