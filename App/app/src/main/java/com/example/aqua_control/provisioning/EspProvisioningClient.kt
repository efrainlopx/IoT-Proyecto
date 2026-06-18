package com.example.aqua_control.provisioning

import com.example.aqua_control.config.ProjectConfig
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class EspProvisioningRequest(
    val esp32SetupHost: String,
    val wifiSsid: String,
    val wifiPassword: String,
    val mqttHost: String,
    val mqttPort: Int = ProjectConfig.MQTT_PORT,
    val mqttUser: String = ProjectConfig.MQTT_USERNAME,
    val mqttPassword: String = ProjectConfig.MQTT_PASSWORD,
)

class EspProvisioningClient {
    fun send(request: EspProvisioningRequest, callback: Callback) {
        Thread {
            val result = runCatching { sendBlocking(request) }
            result.onSuccess { callback.onSuccess(it) }
                .onFailure { callback.onError(it.localizedMessage ?: "No se pudo configurar el ESP32") }
        }.start()
    }

    private fun sendBlocking(request: EspProvisioningRequest): String {
        val endpoint = buildEndpoint(request.esp32SetupHost)
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 6000
            readTimeout = 8000
            doOutput = true
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
        }

        val body = formBody(
            "ssid" to request.wifiSsid,
            "password" to request.wifiPassword,
            "mqtt_host" to request.mqttHost,
            "mqtt_port" to request.mqttPort.toString(),
            "mqtt_user" to request.mqttUser,
            "mqtt_password" to request.mqttPassword,
        )

        OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
            writer.write(body)
        }

        val code = connection.responseCode
        val response = readResponse(connection)
        connection.disconnect()

        if (code !in 200..299) {
            error(response.ifBlank { "ESP32 respondio HTTP $code" })
        }
        return response.ifBlank { "Configuracion enviada. El ESP32 se reiniciara." }
    }

    private fun buildEndpoint(hostInput: String): String {
        val host = hostInput.trim().ifBlank { ProjectConfig.DEFAULT_ESP32_SETUP_HOST }
        val base = when {
            host.startsWith("http://", ignoreCase = true) -> host
            host.startsWith("https://", ignoreCase = true) -> host
            else -> "http://$host"
        }.trimEnd('/')
        return "$base/config"
    }

    private fun formBody(vararg values: Pair<String, String>): String = values.joinToString("&") { (key, value) ->
        "${key.urlEncode()}=${value.urlEncode()}"
    }

    private fun String.urlEncode(): String = URLEncoder.encode(this, Charsets.UTF_8.name())

    private fun readResponse(connection: HttpURLConnection): String {
        val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
        return stream?.bufferedReader()?.use(BufferedReader::readText)?.trim().orEmpty()
    }

    interface Callback {
        fun onSuccess(message: String)
        fun onError(message: String)
    }
}
