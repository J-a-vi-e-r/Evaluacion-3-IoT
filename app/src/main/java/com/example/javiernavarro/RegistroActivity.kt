package com.example.javiernavarro

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.Toast
import com.amplifyframework.auth.AuthUserAttributeKey
import com.amplifyframework.auth.options.AuthSignUpOptions
import com.amplifyframework.core.Amplify
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.textfield.TextInputEditText
import java.util.Locale

class RegistroActivity : BaseScreenActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showScreen(R.layout.activity_registro)
        findViewById<View>(R.id.btnVolver).setOnClickListener { finish() }
        findViewById<View>(R.id.btnPrivacidad).setOnClickListener { AuthDialogs.privacy(this) }
        val email = findViewById<TextInputEditText>(R.id.etRegEmail)
        val password = findViewById<TextInputEditText>(R.id.etRegPassword)
        val confirmation = findViewById<TextInputEditText>(R.id.etRegConfirmPassword)
        val button = findViewById<MaterialButton>(R.id.btnRegistrarUsuario)
        button.setOnClickListener {
            val address = email.text.toString().trim().lowercase(Locale.ROOT)
            val pass = password.text.toString()
            when {
                !Patterns.EMAIL_ADDRESS.matcher(address).matches() -> email.error = "Ingresa un correo válido."
                pass.length < 8 -> password.error = "Usa 8 o más caracteres, mayúsculas, minúsculas, números y un símbolo."
                pass != confirmation.text.toString() -> confirmation.error = "Las contraseñas no coinciden."
                !findViewById<MaterialCheckBox>(R.id.checkPrivacidad).isChecked -> AuthDialogs.privacy(this)
                !AgroApplication.authReady -> Toast.makeText(this, "El acceso no está configurado.", Toast.LENGTH_LONG).show()
                else -> {
                    button.isEnabled = false; button.text = "Creando cuenta…"
                    val options = AuthSignUpOptions.builder().userAttribute(AuthUserAttributeKey.email(), address).build()
                    Amplify.Auth.signUp(address, pass, options, { result -> runOnUiThread {
                        if (isFinishing || isDestroyed) return@runOnUiThread
                        button.isEnabled = true; button.text = "Crear cuenta"
                        password.text?.clear(); confirmation.text?.clear()
                        fun toLogin() {
                            startActivity(Intent(this, MainActivity::class.java).putExtra("email", address)
                                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
                            finish()
                        }
                        if (result.isSignUpComplete) toLogin()
                        else AuthDialogs.confirmEmail(this, address) { toLogin() }
                    } }, { error -> runOnUiThread {
                        if (isFinishing || isDestroyed) return@runOnUiThread
                        button.isEnabled = true; button.text = "Crear cuenta"
                        password.text?.clear(); confirmation.text?.clear()
                        Toast.makeText(this, AuthMessages.describe(error), Toast.LENGTH_LONG).show()
                    } })
                }
            }
        }
    }
}
