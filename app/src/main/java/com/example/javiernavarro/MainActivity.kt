package com.example.javiernavarro

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.widget.Toast
import com.amplifyframework.core.Amplify
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import java.util.Locale

class MainActivity : BaseScreenActivity() {
    private var opening = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showScreen(R.layout.activity_main)
        val email = findViewById<TextInputEditText>(R.id.etEmail)
        val password = findViewById<TextInputEditText>(R.id.etPassword)
        val login = findViewById<MaterialButton>(R.id.btnLogin)
        email.setText(intent.getStringExtra("email") ?: "")
        fun ready(): Boolean {
            if (AgroApplication.authReady) return true
            Toast.makeText(this, "No se pudo configurar el acceso. Revisa amplifyconfiguration.json y vuelve a abrir la app.", Toast.LENGTH_LONG).show()
            return false
        }
        login.setOnClickListener {
            val address = email.text.toString().trim().lowercase(Locale.ROOT)
            when {
                !Patterns.EMAIL_ADDRESS.matcher(address).matches() -> email.error = "Ingresa un correo válido."
                password.text.isNullOrEmpty() -> password.error = "Ingresa tu contraseña."
                ready() -> {
                    login.isEnabled = false
                    login.text = "Ingresando…"
                    Amplify.Auth.signIn(address, password.text.toString(), { result -> runOnUiThread {
                        if (isFinishing || isDestroyed) return@runOnUiThread
                        password.text?.clear(); login.isEnabled = true; login.text = "Iniciar sesión"
                        when {
                            result.isSignedIn -> openCloud()
                            result.nextStep.signInStep.name == "CONFIRM_SIGN_UP" -> AuthDialogs.confirmEmail(this, address)
                            result.nextStep.signInStep.name == "RESET_PASSWORD" -> AuthDialogs.resetPassword(this, address)
                            else -> Toast.makeText(this, "Cognito solicita un paso adicional: " + result.nextStep.signInStep.name, Toast.LENGTH_LONG).show()
                        }
                    } }, { error -> runOnUiThread {
                        if (isFinishing || isDestroyed) return@runOnUiThread
                        password.text?.clear(); login.isEnabled = true; login.text = "Iniciar sesión"
                        Toast.makeText(this, AuthMessages.describe(error), Toast.LENGTH_LONG).show()
                    } })
                }
            }
        }
        findViewById<MaterialButton>(R.id.tvRegistrarse).setOnClickListener {
            if (ready()) startActivity(Intent(this, RegistroActivity::class.java))
        }
        findViewById<MaterialButton>(R.id.btnRecuperar).setOnClickListener {
            if (ready()) AuthDialogs.resetPassword(this, email.text.toString().trim())
        }
        findViewById<MaterialButton>(R.id.btnConfirmarCuenta).setOnClickListener {
            if (ready()) AuthDialogs.confirmEmail(this, email.text.toString().trim())
        }
        findViewById<MaterialButton>(R.id.btnPrivacidad).setOnClickListener { AuthDialogs.privacy(this) }
        findViewById<MaterialButton>(R.id.btnDemo).setOnClickListener {
            startActivity(Intent(this, DemoDashboardActivity::class.java))
        }
        if (AgroApplication.authReady) {
            // Consulta local: permite abrir el respaldo cuando se vuelve sin Internet.
            Amplify.Auth.getCurrentUser({ runOnUiThread { openCloud() } }, { })
        }
    }
    private fun openCloud() {
        if (opening || isFinishing || isDestroyed) return
        opening = true
        startActivity(Intent(this, DashboardActivity::class.java))
        finish()
    }
}
