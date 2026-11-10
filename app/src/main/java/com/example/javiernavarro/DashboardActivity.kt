package com.example.javiernavarro

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.amplifyframework.auth.cognito.result.AWSCognitoAuthSignOutResult
import com.amplifyframework.core.Amplify
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/** Panel de lecturas de AWS. Los actuadores se muestran tal como los informa la ESP32. */
class DashboardActivity : BaseScreenActivity() {
    private val handler = Handler(Looper.getMainLooper())
    private var active = false
    private var generation = 0L
    private var inFlight = false
    private var delayMs = 15000L
    private var cache: ReadingCache? = null
    private var outbox: CommandOutbox? = null
    private var commandBusy = false
    private var awaitingId: String? = null
    private var awaitingSince = 0L
    private var commandStatus = "Elige automático o manual. Riego manual limitado a 20 segundos."
    private var pendingCount = 0
    private var reading: Reading? = null
    private var online = false
    private var detail = "Conectando con el servicio…"
    private val poll = Runnable { refresh() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showScreen(R.layout.activity_dashboard)
        text(R.id.tvSubtitulo, "Lecturas enviadas por tu ESP32 y guardadas en AWS.")
        text(R.id.tvDispositivo, "AgroRiegoESP32")
        text(R.id.tvOrigen, "TELEMETRÍA DE WOKWI")
        findViewById<MaterialButton>(R.id.btnAutomatico).setOnClickListener { issue("automatico") }
        findViewById<MaterialButton>(R.id.btnManual).setOnClickListener { issue("manual", false) }
        findViewById<MaterialButton>(R.id.btnRiego).setOnClickListener {
            issue("manual", !(reading?.watering ?: false))
        }
        findViewById<MaterialButton>(R.id.btnEscenario).apply {
            text = "Actualizar lecturas"
            setOnClickListener { handler.removeCallbacks(poll); refresh() }
        }
        findViewById<MaterialButton>(R.id.btnSalir).apply {
            text = "Cerrar sesión"
            setOnClickListener { signOut() }
        }
        findViewById<View>(R.id.btnAjustes).setOnClickListener { account() }
        render()
    }

    override fun onResume() {
        super.onResume()
        active = true
        val current = ++generation
        if (!AgroApplication.authReady) { toLogin(); return }
        Amplify.Auth.getCurrentUser({ user -> runOnUiThread {
            if (!active || current != generation || isDestroyed) return@runOnUiThread
            cache = ReadingCache(this, user.userId)
            outbox = CommandOutbox(this, user.userId)
            updatePendingCount()
            if (reading == null) reading = cache?.load()
            online = false
            detail = if (reading == null) "Consultando tu primera lectura…" else "Respaldo local · comprobando conexión…"
            render()
            refresh()
        } }, { runOnUiThread { if (active && current == generation) toLogin() } })
    }

    override fun onStop() {
        active = false
        generation++
        inFlight = false
        handler.removeCallbacks(poll)
        super.onStop()
    }

    private fun refresh() {
        if (!active || inFlight || cache == null) return
        handler.removeCallbacks(poll)
        inFlight = true
        val current = generation
        findViewById<MaterialButton>(R.id.btnEscenario).isEnabled = false
        ReadingApi.fetch({ next -> runOnUiThread {
            if (!active || current != generation || isDestroyed) return@runOnUiThread
            inFlight = false; online = true; reading = next; delayMs = 15000L
            detail = if (cache?.save(next) == true) "API conectada · respaldo local actualizado."
                else "API conectada · no se pudo guardar el respaldo local."
            checkAcknowledgement(next)
            render()
            syncRecords()
            handler.postDelayed(poll, if (awaitingId != null) 2000L else delayMs)
        } }, { failure -> runOnUiThread {
            if (!active || current != generation || isDestroyed) return@runOnUiThread
            inFlight = false; online = false; detail = failure.message
            delayMs = (delayMs * 2).coerceAtMost(60000L)
            checkAcknowledgement(null)
            render()
            handler.postDelayed(poll, if (awaitingId != null) 2000L else delayMs)
        } })
    }

    private fun recent(): Boolean = reading?.isRecent() == true

    private fun updatePendingCount() {
        try { pendingCount = outbox?.pending()?.size ?: 0 }
        catch (_: Exception) {
            outbox = null
            commandStatus = "No se pudo abrir la cola cifrada. No se borró su contenido; reinicia la app."
        }
    }

    private fun issue(mode: String, watering: Boolean = false) {
        if (!active || commandBusy) return
        val queue = outbox ?: return
        val command = Command.create(mode, watering)
        try { queue.append(command) }
        catch (error: Exception) {
            toast(if (pendingCount >= 20) "Hay 20 registros pendientes. Revisa el aviso de sincronización: la conexión y la ruta /comandos deben estar disponibles."
                else "No se pudo guardar la solicitud. No se envió ninguna orden.")
            return
        }
        awaitingId = null
        updatePendingCount()
        if (online) send(command, true, queue)
        else {
            commandStatus = "Solicitud guardada sin ejecutar. Al recuperar conexión se sincronizará solo como registro."
            render()
            toast("Registro local guardado. No cambia el riego.")
        }
    }

    private fun syncRecords() {
        if (!active || !online || commandBusy) return
        val queue = outbox ?: return
        val command = try { queue.pending().firstOrNull() }
            catch (_: Exception) { updatePendingCount(); render(); return }
        if (command != null) send(command, false, queue)
    }

    private fun send(command: Command, execute: Boolean, queue: CommandOutbox) {
        commandBusy = true
        val current = generation
        if (execute) commandStatus = "Enviando orden; el estado cambiará cuando lo informe la ESP32…"
        render()
        ReadingApi.send(command, execute, { receipt -> runOnUiThread {
            var removed = true
            try { queue.remove(command.id) } catch (_: Exception) { removed = false }
            commandBusy = false
            if (!active || current != generation || isDestroyed) return@runOnUiThread
            updatePendingCount()
            if (execute || awaitingId == null) commandStatus = when (receipt.status) {
                "publicado" -> "Broker recibió la orden. Esperando confirmación de la ESP32…"
                "registro_sin_ejecucion" -> "Registro sincronizado con DynamoDB. No se ejecutó el riego."
                "caducado_sin_ejecucion" -> "Orden caducada: se guardó el registro sin ejecutarla."
                "sin_dispositivo_reciente" -> "Sin ESP32 reciente: registro guardado sin ejecutar. Revisa Wokwi."
                "envio_no_confirmado" -> if (receipt.code in listOf("AccessDeniedException", "UnauthorizedException"))
                    "AWS rechazó el permiso de Lambda para publicar la orden: " + receipt.code
                    else "No se confirmó la publicación de la orden. Revisa la ESP32 antes de repetir."
                else -> "Registro guardado; ejecución sin confirmar. Revisa la ESP32 antes de repetir."
            }
            if (receipt.status == "publicado" && execute) {
                awaitingId = command.id
                awaitingSince = SystemClock.elapsedRealtime()
            }
            if (!removed) commandStatus += " La copia local se conserva: no se pudo confirmar su limpieza."
            render()
            handler.removeCallbacks(poll)
            handler.postDelayed(poll, if (awaitingId != null) 1000L else 15000L)
            if (removed) handler.postDelayed({ syncRecords() }, 500L)
        } }, { failure -> runOnUiThread {
            commandBusy = false
            if (!active || current != generation || isDestroyed) return@runOnUiThread
            updatePendingCount()
            commandStatus = failure.message + " El registro local se conserva. " +
                if (execute) "La ejecución no está confirmada; comprueba el estado antes de repetir."
                else "Se reintentará su sincronización sin ejecutar la orden."
            render()
        } })
    }

    private fun checkAcknowledgement(value: Reading?) {
        val id = awaitingId ?: return
        if (value?.commandId == id && recent()) {
            commandStatus = when (value.commandResult) {
                "aceptado", "repetido_sin_reactivar" -> "ESP32 confirmó la orden. El panel muestra su estado informado."
                else -> "La ESP32 respondió: " + (value.commandResult ?: "resultado desconocido")
            }
            awaitingId = null
        } else if (SystemClock.elapsedRealtime() - awaitingSince > 60000L) {
            commandStatus = "Sin confirmación en 60 segundos. Revisa Wokwi y el estado antes de repetir la orden."
            awaitingId = null
        }
    }

    private fun render() {
        val value = reading
        val recent = value?.isRecent() == true
        text(R.id.tvConexion, when {
            value == null -> "ESPERANDO LECTURAS"
            !online -> "RESPALDO LOCAL"
            else -> "API CONECTADA"
        })
        text(R.id.tvActualizacion, value?.sampledAt?.let {
            "Medición: " + DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM).format(Date(it * 1000L))
        } ?: "Sin fecha de medición disponible")
        text(R.id.tvEscenario, detail)
        findViewById<MaterialButton>(R.id.btnEscenario).isEnabled = !inFlight
        text(R.id.tvTemp, number(value?.temperature, "°C"))
        text(R.id.tvAire, number(value?.air, "%"))
        text(R.id.tvSuelo, number(value?.soil, "%"))
        findViewById<ProgressBar>(R.id.progressSuelo).progress = value?.soil?.toInt()?.coerceIn(0, 100) ?: 0
        text(R.id.tvTempEstado, when {
            value?.temperature == null -> "Sin lectura válida del DHT22"
            !recent || !online -> "Valor de la última medición"
            value.temperature >= 32 -> "Alerta de calor"
            value.temperature <= 5 -> "Alerta de frío"
            else -> "Dentro de los umbrales del prototipo"
        })
        text(R.id.tvAireEstado, "Humedad del aire informada por el DHT22")
        text(R.id.tvSueloEstado, if (value?.modelled == true)
            "Humedad virtual: modelo de respuesta al riego" else "Humedad informada por la ESP32")
        text(R.id.tvModoDescripcion, if (value == null) "Esperando el modo informado por la ESP32."
            else "Modo informado: " + (if (value.automatic) "automático." else "manual."))
        text(R.id.tvControlPendiente, commandStatus + "\nRegistros locales pendientes: " + pendingCount)
        for (id in listOf(R.id.btnRiego, R.id.btnManual, R.id.btnAutomatico)) {
            findViewById<MaterialButton>(id).apply {
                isEnabled = outbox != null && !commandBusy
                alpha = if (isEnabled) 1f else 0.65f
            }
        }
        text(R.id.tvRiegoEstado, when {
            value == null -> "Estado del riego desconocido"
            !recent || !online -> "Último estado: " + if (value.watering) "riego activo" else "riego detenido"
            value.watering -> "La ESP32 informa riego activo"
            else -> "La ESP32 informa riego detenido"
        })
        text(R.id.btnRiego, if (value?.watering == true) "Detener riego · manual" else "Regar 20 s · manual")
        if (value != null) {
            selected(R.id.btnAutomatico, value.automatic)
            selected(R.id.btnManual, !value.automatic)
        }
        val notices = value?.alerts ?: emptyList()
        text(R.id.tvAviso, when {
            value == null -> "Todavía no hay una lectura"
            !online || !recent -> "Avisos de la última lectura"
            notices.isNotEmpty() -> "Revisa las condiciones del invernadero"
            else -> "Sin alertas en la última lectura"
        })
        val historical = if (!online || !recent) "Estos datos no confirman el estado actual del invernadero.\n\n" else ""
        text(R.id.tvAvisoDetalle, historical + if (value == null)
            "Activa el laboratorio y la simulación de Wokwi. El panel se actualizará al recibir una lectura válida."
            else if (notices.isEmpty()) "La ESP32 no informó alertas en esta medición."
            else notices.joinToString("\n"))
        findViewById<View>(R.id.panelAviso).setBackgroundResource(
            if (notices.isNotEmpty() || !recent || !online) R.drawable.bg_notice else R.drawable.bg_sage)
        text(R.id.tvPie, "Consulta cada 15 s; cada 2 s al esperar confirmación. Sin conexión, las solicitudes se guardan como registros y no se ejecutan después.")
    }

    private fun account() {
        MaterialAlertDialogBuilder(this).setTitle("Cuenta y privacidad")
            .setItems(arrayOf("Ver aviso de privacidad", "Consultar mis datos de cuenta", "Eliminar mi cuenta", "Cerrar sesión")) { _, which ->
                when (which) {
                    0 -> AuthDialogs.privacy(this)
                    1 -> Amplify.Auth.fetchUserAttributes({ attributes -> runOnUiThread {
                        if (isFinishing || isDestroyed) return@runOnUiThread
                        MaterialAlertDialogBuilder(this).setTitle("Datos de tu cuenta")
                            .setMessage(attributes.joinToString("\n") { it.key.keyString + ": " + it.value })
                            .setPositiveButton("Cerrar", null).show()
                    } }, { error -> runOnUiThread { toast(AuthMessages.describe(error)) } })
                    2 -> MaterialAlertDialogBuilder(this).setTitle("Eliminar tu cuenta")
                        .setMessage("Se eliminará tu cuenta de Cognito, el respaldo y los registros pendientes de este teléfono. Las lecturas y registros operativos compartidos del invernadero se conservarán. Esta operación no se puede deshacer.")
                        .setNegativeButton("Cancelar", null).setPositiveButton("Eliminar cuenta") { _, _ ->
                            Amplify.Auth.deleteUser({ runOnUiThread {
                                val cleaned = runCatching { cache?.clear(); outbox?.clear() }.isSuccess
                                if (!cleaned) toast("Cuenta eliminada. No se pudo limpiar el respaldo local: borra los datos de la app en Android.")
                                toLogin()
                            } }, { error -> runOnUiThread { toast(AuthMessages.describe(error)) } })
                        }.show()
                    3 -> signOut()
                }
            }.show()
    }

    private fun signOut() {
        handler.removeCallbacks(poll)
        Amplify.Auth.signOut { result -> runOnUiThread {
            if (isFinishing || isDestroyed) return@runOnUiThread
            if (result is AWSCognitoAuthSignOutResult.FailedSignOut) {
                toast("No se pudo cerrar la sesión. Vuelve a intentarlo.")
            } else {
                if (runCatching { cache?.clear() }.isFailure)
                    toast("Sesión cerrada. No se pudo limpiar la lectura: borra los datos de la app si necesitas retirarla.")
                toLogin()
            }
        } }
    }
    private fun toLogin() {
        active = false
        generation++
        handler.removeCallbacks(poll)
        startActivity(Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        finish()
    }
    private fun toast(message: String) {
        if (!isFinishing && !isDestroyed) Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
    private fun number(value: Double?, unit: String): String =
        if (value == null) "—" else String.format(Locale.getDefault(), "%.1f %s", value, unit)
    private fun text(id: Int, value: String) { findViewById<TextView>(id).text = value }
    private fun selected(id: Int, selected: Boolean) {
        findViewById<MaterialButton>(id).apply {
            backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this@DashboardActivity,
                if (selected) R.color.forest else R.color.sage))
            setTextColor(ContextCompat.getColor(this@DashboardActivity, if (selected) R.color.white else R.color.forest))
            isSelected = selected
        }
    }
}
