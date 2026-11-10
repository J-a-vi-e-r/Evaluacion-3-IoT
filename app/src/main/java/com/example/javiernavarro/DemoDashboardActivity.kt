package com.example.javiernavarro

import android.content.res.ColorStateList
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** Demonstration UI only. No AWS endpoint or hardware command is called here. */
class DemoDashboardActivity : BaseScreenActivity() {
    private val preferences by lazy { getSharedPreferences("agro_demo_ui", MODE_PRIVATE) }
    private var scenario = 0
    private var automatic = true
    private var pumpRunning = false
    private var thresholds = DemoThresholds()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showScreen(R.layout.activity_dashboard)
        findViewById<View>(R.id.btnAjustes).contentDescription = "Ajustar umbrales de demostración"
        scenario = preferences.getInt("scenario", 0).coerceIn(0, DemoModel.scenarios.lastIndex)
        automatic = preferences.getBoolean("automatic", true)
        val stored = DemoThresholds(preferences.getInt("start", 35), preferences.getInt("stop", 55), preferences.getInt("hot", 32))
        thresholds = if (stored.isValid()) stored else DemoThresholds()
        // Manual test state survives screen rotation, never a fresh app session.
        pumpRunning = savedInstanceState?.getBoolean("pumpRunning") ?: false
        findViewById<View>(R.id.btnSalir).setOnClickListener { finish() }
        findViewById<View>(R.id.btnAjustes).setOnClickListener { showThresholds() }
        findViewById<MaterialButton>(R.id.btnAutomatico).setOnClickListener {
            if (!automatic) { automatic = true; pumpRunning = false }
            render()
        }
        findViewById<MaterialButton>(R.id.btnManual).setOnClickListener {
            if (automatic) { automatic = false; pumpRunning = false }
            render()
        }
        findViewById<MaterialButton>(R.id.btnRiego).setOnClickListener {
            if (!automatic) { pumpRunning = !pumpRunning; render() }
        }
        findViewById<MaterialButton>(R.id.btnEscenario).setOnClickListener {
            scenario = (scenario + 1) % DemoModel.scenarios.size
            render()
        }
        render()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("pumpRunning", pumpRunning)
        super.onSaveInstanceState(outState)
    }

    private fun render() {
        val reading = DemoModel.scenarios[scenario]
        if (automatic) pumpRunning = DemoModel.automaticPump(reading, thresholds, pumpRunning)
        val hot = reading.temperature >= thresholds.hotAt
        val dry = reading.soilHumidity < thresholds.startBelow
        text(R.id.tvTemp, "${reading.temperature} °C")
        text(R.id.tvAire, "${reading.airHumidity} %")
        text(R.id.tvSuelo, "${reading.soilHumidity} %")
        text(R.id.tvTempEstado, if (hot) "Sobre el umbral" else "Bajo el umbral de calor")
        findViewById<TextView>(R.id.tvTempEstado).setTextColor(color(if (hot) R.color.amber else R.color.green))
        text(R.id.tvAireEstado, "Referencia ambiental")
        findViewById<ProgressBar>(R.id.progressSuelo).progress = reading.soilHumidity
        text(R.id.tvSueloEstado, when {
            dry -> "Tierra seca según el umbral de prueba"
            pumpRunning -> "Riego de prueba en curso"
            else -> "Humedad suficiente según el umbral de prueba"
        })
        text(R.id.tvModoDescripcion, if (automatic)
            "En esta demo, el riego inicia bajo ${thresholds.startBelow} % y se detiene al llegar a ${thresholds.stopAt} %."
            else "Prueba encender o detener el riego con el botón. No se envían órdenes a ningún dispositivo.")
        text(R.id.tvRiegoEstado, if (pumpRunning) "Riego de prueba activo" else "Riego en espera")
        val button = findViewById<MaterialButton>(R.id.btnRiego)
        button.isEnabled = !automatic
        button.text = when {
            automatic -> "Controlado por el modo automático"
            pumpRunning -> "Detener riego de prueba"
            else -> "Activar riego de prueba"
        }
        button.alpha = if (automatic) 0.65f else 1f
        select(R.id.btnAutomatico, automatic)
        select(R.id.btnManual, !automatic)
        val notices = mutableListOf<String>()
        if (hot) notices.add("Temperatura de ejemplo de ${reading.temperature} °C: supera o alcanza el límite de ${thresholds.hotAt} °C. Revisa las necesidades de ventilación y sombra de la planta.")
        if (dry) notices.add(if (pumpRunning) "La tierra está bajo ${thresholds.startBelow} %. El riego de prueba está activo." else "La tierra está bajo ${thresholds.startBelow} %. El riego manual de prueba está detenido.")
        text(R.id.tvAviso, when {
            hot && dry -> "Calor y tierra seca"
            hot -> "Revisar temperatura"
            dry -> "Revisar humedad de la tierra"
            else -> "Condiciones dentro de los umbrales"
        })
        text(R.id.tvAvisoDetalle, if (notices.isEmpty()) "No hay alertas en este ejemplo. Los umbrales son orientativos y deben ajustarse a la planta que elijas." else notices.joinToString("\n\n"))
        findViewById<View>(R.id.panelAviso).setBackgroundResource(if (hot || dry) R.drawable.bg_notice else R.drawable.bg_sage)
        text(R.id.tvEscenario, "Ejemplo ${scenario + 1} de ${DemoModel.scenarios.size} · ${reading.label}")
        preferences.edit().putInt("scenario", scenario).putBoolean("automatic", automatic).apply()
    }

    private fun showThresholds() {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (24 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad / 2, pad, 0)
        }
        fun field(label: String, value: Int): EditText {
            content.addView(TextView(this).apply { text = label; setTextColor(color(R.color.ink)); textSize = 13f })
            val input = EditText(this).apply {
                setText(value.toString()); inputType = InputType.TYPE_CLASS_NUMBER
                minHeight = (56 * resources.displayMetrics.density).toInt()
                setSelectAllOnFocus(true)
                contentDescription = label
            }
            content.addView(input)
            return input
        }
        val start = field("Iniciar riego bajo esta humedad (%)", thresholds.startBelow)
        val stop = field("Detener riego al llegar a (%)", thresholds.stopAt)
        val hot = field("Avisar calor desde (°C)", thresholds.hotAt)
        val error = TextView(this).apply { setTextColor(color(R.color.amber)); textSize = 12f }
        content.addView(error)
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("Umbrales de demostración")
            .setMessage("Valores de prueba, sin calibración para una planta específica. Solo afectan esta interfaz.")
            .setView(content)
            .setPositiveButton("Guardar", null)
            .setNegativeButton("Cancelar", null)
            .setNeutralButton("Restablecer", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val first = start.text.toString().toIntOrNull()
                val last = stop.text.toString().toIntOrNull()
                val heat = hot.text.toString().toIntOrNull()
                val next = if (first != null && last != null && heat != null) DemoThresholds(first, last, heat) else null
                if (next == null || !next.isValid()) {
                    error.text = "Usa humedades entre 1 y 100 %, con inicio menor que parada, y temperatura entre 1 y 60 °C."
                } else {
                    thresholds = next
                    pumpRunning = false
                    preferences.edit().putInt("start", next.startBelow).putInt("stop", next.stopAt).putInt("hot", next.hotAt).apply()
                    render()
                    dialog.dismiss()
                }
            }
            dialog.getButton(android.app.AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                start.setText("35"); stop.setText("55"); hot.setText("32"); error.text = ""
            }
        }
        dialog.show()
    }

    private fun text(id: Int, value: String) { findViewById<TextView>(id).text = value }
    private fun color(id: Int): Int = ContextCompat.getColor(this, id)
    private fun select(id: Int, selected: Boolean) {
        findViewById<MaterialButton>(id).apply {
            backgroundTintList = ColorStateList.valueOf(color(if (selected) R.color.forest else R.color.sage))
            setTextColor(color(if (selected) R.color.white else R.color.forest))
            isSelected = selected
        }
    }
}
