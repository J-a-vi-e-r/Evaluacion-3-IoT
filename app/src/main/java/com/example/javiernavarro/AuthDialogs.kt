package com.example.javiernavarro

import android.text.InputType
import android.util.Patterns
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.amplifyframework.core.Amplify
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.Locale

object AuthDialogs {
    private fun ui(activity: AppCompatActivity, action: () -> Unit) {
        activity.runOnUiThread { if (!activity.isFinishing && !activity.isDestroyed) action() }
    }
    private fun message(a: AppCompatActivity, text: String) { Toast.makeText(a, text, Toast.LENGTH_LONG).show() }
    private fun box(a: AppCompatActivity) = LinearLayout(a).apply {
        orientation = LinearLayout.VERTICAL
        val pad = (24 * resources.displayMetrics.density).toInt()
        setPadding(pad, pad / 2, pad, 0)
    }
    private fun field(a: AppCompatActivity, parent: LinearLayout, label: String, type: Int, value: String = ""): EditText {
        parent.addView(TextView(a).apply { text = label })
        return EditText(a).apply {
            inputType = type; setText(value); isSingleLine = true
            minHeight = (56 * resources.displayMetrics.density).toInt()
            contentDescription = label
            isSaveEnabled = false
            parent.addView(this)
        }
    }
    private fun email(field: EditText): String? {
        val value = field.text.toString().trim().lowercase(Locale.ROOT)
        return if (Patterns.EMAIL_ADDRESS.matcher(value).matches()) value
        else { field.error = "Ingresa un correo válido."; null }
    }
    fun confirmEmail(a: AppCompatActivity, initial: String, complete: () -> Unit = {}) {
        val form = box(a)
        val address = field(a, form, "Correo de la cuenta", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS, initial)
        val code = field(a, form, "Código recibido por correo", InputType.TYPE_CLASS_NUMBER)
        val d = MaterialAlertDialogBuilder(a).setTitle("Confirmar correo")
            .setMessage("Introduce el código de verificación. Revisa también spam.")
            .setView(form).setPositiveButton("Confirmar", null)
            .setNeutralButton("Reenviar código", null).setNegativeButton("Cerrar", null).create()
        d.setOnShowListener {
            val confirm = d.getButton(android.app.AlertDialog.BUTTON_POSITIVE)
            val resend = d.getButton(android.app.AlertDialog.BUTTON_NEUTRAL)
            confirm.setOnClickListener {
                val target = email(address) ?: return@setOnClickListener
                val value = code.text.toString().trim()
                if (value.isBlank()) { code.error = "Ingresa el código."; return@setOnClickListener }
                confirm.isEnabled = false
                Amplify.Auth.confirmSignUp(target, value, { result -> ui(a) {
                    confirm.isEnabled = true
                    if (result.isSignUpComplete) {
                        code.text.clear(); d.dismiss()
                        message(a, "Correo confirmado. Ya puedes iniciar sesión."); complete()
                    } else message(a, "Cognito todavía requiere completar la confirmación.")
                } }, { error -> ui(a) { confirm.isEnabled = true; message(a, AuthMessages.describe(error)) } })
            }
            resend.setOnClickListener {
                val target = email(address) ?: return@setOnClickListener
                resend.isEnabled = false
                Amplify.Auth.resendSignUpCode(target,
                    { ui(a) { resend.isEnabled = true; message(a, "Revisa tu correo para obtener el código.") } },
                    { error -> ui(a) { resend.isEnabled = true; message(a, AuthMessages.describe(error)) } })
            }
        }
        d.show()
    }
    fun resetPassword(a: AppCompatActivity, initial: String) {
        val form = box(a)
        val address = field(a, form, "Correo de la cuenta", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS, initial)
        val d = MaterialAlertDialogBuilder(a).setTitle("Recuperar contraseña")
            .setMessage("Solicita un código de recuperación para tu cuenta.")
            .setView(form).setPositiveButton("Solicitar código", null).setNegativeButton("Cancelar", null).create()
        d.setOnShowListener {
            val send = d.getButton(android.app.AlertDialog.BUTTON_POSITIVE)
            send.setOnClickListener {
                val target = email(address) ?: return@setOnClickListener
                send.isEnabled = false
                Amplify.Auth.resetPassword(target, { result -> ui(a) {
                    send.isEnabled = true; d.dismiss()
                    if (result.isPasswordReset) message(a, "La recuperación ya se completó.")
                    else finishReset(a, target)
                } }, { error -> ui(a) { send.isEnabled = true; message(a, AuthMessages.describe(error)) } })
            }
        }
        d.show()
    }
    private fun finishReset(a: AppCompatActivity, email: String) {
        val form = box(a)
        val code = field(a, form, "Código recibido por correo", InputType.TYPE_CLASS_NUMBER)
        val pass = field(a, form, "Nueva contraseña", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        val repeat = field(a, form, "Repite la nueva contraseña", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        val d = MaterialAlertDialogBuilder(a).setTitle("Nueva contraseña")
            .setMessage("Usa 8 o más caracteres, mayúsculas, minúsculas, números y un símbolo.")
            .setView(form).setPositiveButton("Guardar contraseña", null).setNegativeButton("Cancelar", null).create()
        d.setOnShowListener {
            val save = d.getButton(android.app.AlertDialog.BUTTON_POSITIVE)
            save.setOnClickListener {
                when {
                    code.text.isBlank() -> code.error = "Ingresa el código."
                    pass.text.length < 8 -> pass.error = "Usa al menos 8 caracteres."
                    pass.text.toString() != repeat.text.toString() -> repeat.error = "Las contraseñas no coinciden."
                    else -> {
                        save.isEnabled = false
                        Amplify.Auth.confirmResetPassword(email, pass.text.toString(), code.text.toString().trim(),
                            { ui(a) { pass.text.clear(); repeat.text.clear(); code.text.clear()
                                d.dismiss(); message(a, "Contraseña actualizada. Inicia sesión.") } },
                            { error -> ui(a) { save.isEnabled = true; message(a, AuthMessages.describe(error)) } })
                    }
                }
            }
        }
        d.show()
    }
    fun privacy(a: AppCompatActivity) {
        MaterialAlertDialogBuilder(a).setTitle("Privacidad del prototipo")
            .setMessage("AgroRiego es un prototipo académico para monitorear un invernadero compartido.\n\n" +
                "Solicitamos correo y contraseña para crear la cuenta, verificarla y recuperar el acceso. No solicitamos RUT ni teléfono. Cognito gestiona la autenticación; la app no guarda tu contraseña.\n\n" +
                "Las cuentas se gestionan en Amazon Cognito en us-east-1, Estados Unidos. Las mediciones de la ESP32 se almacenan en DynamoDB y no incluyen tu correo. Los usuarios registrados consultan y controlan el mismo invernadero. Los registros operativos de órdenes tampoco incluyen tu correo.\n\n" +
                "El teléfono conserva una última lectura cifrada para consultarla sin Internet y hasta 20 solicitudes pendientes cifradas. Al volver Internet, las solicitudes pendientes se sincronizan como registros sin activar el riego. La lectura se elimina al cerrar sesión; los pendientes se conservan por cuenta hasta sincronizarse o eliminar la cuenta. El SDK de autenticación gestiona los tokens de sesión.\n\n" +
                "Puedes consultar tus datos y eliminar la cuenta desde Cuenta y privacidad. Eliminar la cuenta borra sus pendientes locales; no elimina las mediciones ni los registros operativos compartidos del invernadero.\n\n" +
                "Este aviso describe el prototipo. Antes de usarlo con terceros deben definirse el responsable, contacto, plazo de conservación y el procedimiento completo de atención de derechos.")
            .setPositiveButton("Entendido", null).show()
    }
}
