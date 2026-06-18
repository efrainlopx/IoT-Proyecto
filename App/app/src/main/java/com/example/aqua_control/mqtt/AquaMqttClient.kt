package com.example.aqua_control.mqtt

import com.example.aqua_control.config.ProjectConfig
import org.eclipse.paho.client.mqttv3.IMqttActionListener
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.IMqttToken
import org.eclipse.paho.client.mqttv3.MqttAsyncClient
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttException
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence

class AquaMqttClient(private val listener: Listener) {
    private var client: MqttAsyncClient? = null
    private var currentBrokerUri: String = ""
    private var subscriptionRequested = false

    val isReady: Boolean
        get() = client?.isConnected == true

    fun connect(
        hostInput: String,
        username: String = ProjectConfig.MQTT_USERNAME,
        mqttPassword: String = ProjectConfig.MQTT_PASSWORD,
    ) {
        val brokerUri = buildBrokerUri(hostInput)
        if (brokerUri == null) {
            listener.onError("Ingresa el host o IP de la Raspberry Pi")
            return
        }

        disconnect()
        currentBrokerUri = brokerUri
        subscriptionRequested = false
        listener.onConnecting(brokerUri)

        val mqttClient = try {
            MqttAsyncClient(
                brokerUri,
                "AquaControlAndroid-${System.currentTimeMillis()}",
                MemoryPersistence(),
            )
        } catch (exception: MqttException) {
            listener.onError("No se pudo crear el cliente MQTT: ${exception.safeMessage}")
            return
        }

        client = mqttClient
        mqttClient.setCallback(object : MqttCallbackExtended {
            override fun connectComplete(reconnect: Boolean, serverURI: String?) {
                if (client !== mqttClient) return
                listener.onConnected(serverURI ?: currentBrokerUri)
                subscribeToTinaco(mqttClient)
            }

            override fun connectionLost(cause: Throwable?) {
                if (client !== mqttClient) return
                subscriptionRequested = false
                listener.onDisconnected("Conexion MQTT perdida: ${cause.safeMessage}")
            }

            override fun messageArrived(topic: String?, message: MqttMessage?) {
                if (topic == null || message == null) return
                listener.onMessage(topic, message.payload.toString(Charsets.UTF_8).trim())
            }

            override fun deliveryComplete(token: IMqttDeliveryToken?) = Unit
        })

        val options = MqttConnectOptions().apply {
            val normalizedUser = username.trim()
            if (normalizedUser.isNotBlank()) {
                userName = normalizedUser
                password = mqttPassword.toCharArray()
            }
            isCleanSession = true
            isAutomaticReconnect = true
            connectionTimeout = 8
            keepAliveInterval = 20
        }

        try {
            mqttClient.connect(
                options,
                null,
                mqttActionListener(
                    onSuccess = {
                        if (client !== mqttClient) return@mqttActionListener
                        listener.onConnected(currentBrokerUri)
                        subscribeToTinaco(mqttClient)
                    },
                    onFailure = { exception ->
                        if (client !== mqttClient) return@mqttActionListener
                        listener.onError("No se pudo conectar al broker: ${exception.safeMessage}")
                    },
                ),
            )
        } catch (exception: MqttException) {
            listener.onError("Error al iniciar conexion: ${exception.safeMessage}")
        }
    }

    fun disconnect() {
        val activeClient = client
        client = null
        subscriptionRequested = false
        if (activeClient != null) {
            try {
                if (activeClient.isConnected) activeClient.disconnect()
            } catch (_: MqttException) {
            }
            try {
                activeClient.close()
            } catch (_: MqttException) {
            }
        }
    }

    fun publishCommand(command: String) {
        val activeClient = client
        if (activeClient?.isConnected != true) {
            listener.onError("MQTT no esta conectado")
            return
        }

        val message = MqttMessage(command.toByteArray(Charsets.UTF_8)).apply {
            qos = 1
            isRetained = false
        }

        try {
            activeClient.publish(
                ProjectConfig.TOPIC_COMMAND,
                message,
                null,
                mqttActionListener(
                    onSuccess = { listener.onCommandPublished(command) },
                    onFailure = { exception -> listener.onError("No se pudo publicar $command: ${exception.safeMessage}") },
                ),
            )
        } catch (exception: MqttException) {
            listener.onError("Error publicando $command: ${exception.safeMessage}")
        }
    }

    private fun subscribeToTinaco(mqttClient: MqttAsyncClient) {
        if (subscriptionRequested) return
        subscriptionRequested = true
        try {
            mqttClient.subscribe(
                ProjectConfig.TOPIC_ALL,
                1,
                null,
                mqttActionListener(
                    onSuccess = { listener.onSubscribed(ProjectConfig.TOPIC_ALL) },
                    onFailure = { exception ->
                        subscriptionRequested = false
                        listener.onError("Conecto, pero fallo la suscripcion: ${exception.safeMessage}")
                    },
                ),
            )
        } catch (exception: MqttException) {
            subscriptionRequested = false
            listener.onError("Error al suscribirse a ${ProjectConfig.TOPIC_ALL}: ${exception.safeMessage}")
        }
    }

    private fun buildBrokerUri(hostInput: String): String? {
        val trimmed = hostInput.trim()
        if (trimmed.isBlank()) return null

        val normalized = when {
            trimmed.startsWith("mqtt://", ignoreCase = true) -> "tcp://${trimmed.substringAfter("://")}"
            trimmed.startsWith("tcp://", ignoreCase = true) -> trimmed
            trimmed.startsWith("ssl://", ignoreCase = true) -> trimmed
            else -> "tcp://$trimmed"
        }
        val authority = normalized.substringAfter("://")
        return if (authority.contains(":")) normalized else "$normalized:${ProjectConfig.MQTT_PORT}"
    }

    interface Listener {
        fun onConnecting(uri: String)
        fun onConnected(uri: String)
        fun onSubscribed(topic: String)
        fun onDisconnected(message: String)
        fun onMessage(topic: String, payload: String)
        fun onCommandPublished(command: String)
        fun onError(message: String)
    }
}

private val Throwable?.safeMessage: String
    get() = this?.localizedMessage?.takeIf { it.isNotBlank() } ?: "sin detalle"

private inline fun mqttActionListener(
    crossinline onSuccess: () -> Unit,
    crossinline onFailure: (Throwable?) -> Unit,
): IMqttActionListener = object : IMqttActionListener {
    override fun onSuccess(asyncActionToken: IMqttToken?) = onSuccess()

    override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) = onFailure(exception)
}
