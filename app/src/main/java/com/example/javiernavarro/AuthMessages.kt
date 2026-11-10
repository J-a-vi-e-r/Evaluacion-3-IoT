package com.example.javiernavarro

object AuthMessages {
    fun describe(error: Throwable): String {
        val names = generateSequence(error) { it.cause }.take(6)
            .joinToString(" ") { it.javaClass.simpleName }
        return when {
            "UserNotConfirmed" in names -> "Confirma tu correo antes de ingresar. Usa Confirmar mi correo."
            "UsernameExists" in names -> "Si ya registraste ese correo, confirma la cuenta o recupera tu contraseña."
            "InvalidPassword" in names -> "La contraseña no cumple la política. Usa 8 o más caracteres, mayúsculas, minúsculas, números y un símbolo."
            "CodeMismatch" in names -> "El código no coincide. Revisa el correo y vuelve a intentarlo."
            "ExpiredCode" in names -> "El código venció. Solicita uno nuevo."
            "LimitExceeded" in names || "TooMany" in names -> "Se alcanzó el límite de intentos. Espera un momento."
            "Network" in names || "IOException" in names || "UnknownHost" in names -> "No se pudo conectar. Revisa tu acceso a Internet."
            "NotAuthorized" in names || "UserNotFound" in names -> "No se pudo iniciar sesión. Revisa tus datos o recupera la contraseña."
            else -> "No se pudo completar la operación. Revisa la conexión y vuelve a intentarlo. Detalle: " + error.javaClass.simpleName
        }
    }
}
