package com.example.javiernavarro

import android.app.Application
import com.amplifyframework.auth.cognito.AWSCognitoAuthPlugin
import com.amplifyframework.core.Amplify

class AgroApplication : Application() {
    companion object {
        @Volatile var authReady = false
            private set
    }

    override fun onCreate() {
        super.onCreate()
        try {
            Amplify.addPlugin(AWSCognitoAuthPlugin())
            Amplify.configure(applicationContext)
            authReady = true
        } catch (_: Exception) {
            // No registrar contraseñas, tokens ni datos personales.
            authReady = false
        }
    }
}
