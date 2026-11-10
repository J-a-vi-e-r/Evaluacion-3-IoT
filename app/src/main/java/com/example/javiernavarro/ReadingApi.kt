package com.example.javiernavarro

import com.amplifyframework.auth.cognito.AWSCognitoAuthSession
import com.amplifyframework.auth.options.AuthFetchSessionOptions
import com.amplifyframework.core.Amplify
import java.net.URL
import java.util.concurrent.Executors
import javax.net.ssl.HttpsURLConnection
import org.json.JSONObject

/** HTTPS con access token; el cuerpo de una orden se conserva idéntico al renovar sesión. */
object ReadingApi {
    private const val BASE = "https://5p25yz6pzh.execute-api.us-east-1.amazonaws.com/prod"
    private val io = Executors.newSingleThreadExecutor()
    data class Failure(val message: String, val requiresLogin: Boolean = false)

    fun fetch(success: (Reading) -> Unit, failure: (Failure) -> Unit) {
        request("/lecturas/ultima", null, { doc ->
            success(Reading.parse(doc.getJSONObject("lectura").toString()))
        }, failure)
    }
    fun send(command: Command, execute: Boolean, success: (CommandReceipt) -> Unit, failure: (Failure) -> Unit) {
        request("/comandos", command.json(execute).toString(), { doc ->
            success(CommandReceipt.parse(doc.getJSONObject("comando"), command.id))
        }, failure)
    }
    private fun request(path: String, payload: String?, success: (JSONObject) -> Unit,
                        failure: (Failure) -> Unit, force: Boolean = false) {
        val options = AuthFetchSessionOptions.builder().forceRefresh(force).build()
        Amplify.Auth.fetchAuthSession(options, { session ->
            val token = (session as? AWSCognitoAuthSession)?.userPoolTokensResult?.value?.accessToken
            if (!session.isSignedIn || token.isNullOrBlank()) {
                failure(Failure("La sesión no está disponible. Inicia sesión con conexión a Internet.", true))
            } else io.execute {
                var connection: HttpsURLConnection? = null
                try {
                    connection = URL(BASE + path).openConnection() as HttpsURLConnection
                    connection.requestMethod = if (payload == null) "GET" else "POST"
                    connection.instanceFollowRedirects = false
                    connection.connectTimeout = 10000
                    connection.readTimeout = 20000
                    connection.useCaches = false
                    connection.setRequestProperty("Accept", "application/json")
                    connection.setRequestProperty("Authorization", "Bearer " + token)
                    if (payload != null) {
                        connection.doOutput = true
                        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                        connection.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
                    }
                    val status = connection.responseCode
                    if (status == 401 && !force) {
                        connection.disconnect()
                        request(path, payload, success, failure, true)
                        return@execute
                    }
                    if (status != 200) {
                        val message = when (status) {
                            400 -> "Solicitud no válida. Revisa la fecha y hora automáticas del teléfono."
                            401 -> "La API rechazó la sesión. Revisa el autorizador o vuelve a iniciar sesión."
                            403 -> "La API rechazó los permisos. Revisa el ámbito de autorización."
                            404 -> "No hay datos o la ruta " + path + " no está desplegada en prod."
                            409 -> "El identificador de la solicitud ya se utilizó con otros datos."
                            500, 502, 503, 504 -> "El servicio no pudo completar la operación. Revisa Lambda y el laboratorio."
                            else -> "El servicio respondió HTTP " + status + "."
                        }
                        failure(Failure(message, status == 401))
                    } else {
                        val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { input ->
                            val text = StringBuilder()
                            val chunk = CharArray(2048)
                            var size = input.read(chunk)
                            while (size != -1) {
                                require(text.length + size <= 65536) { "Respuesta demasiado grande" }
                                text.append(chunk, 0, size)
                                size = input.read(chunk)
                            }
                            text.toString()
                        }
                        val response = JSONObject(body)
                        require(response.opt("ok") == true)
                        success(response)
                    }
                } catch (_: Exception) {
                    failure(Failure("No se pudo confirmar la operación. Se conserva el respaldo; revisa Internet y el laboratorio."))
                } finally { connection?.disconnect() }
            }
        }, { error -> failure(Failure(AuthMessages.describe(error))) })
    }
}
