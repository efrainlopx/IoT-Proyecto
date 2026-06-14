package com.example.aqua_control

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.aqua_control.ui.theme.AquaControlTheme
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.eclipse.paho.client.mqttv3.IMqttActionListener
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.IMqttToken
import org.eclipse.paho.client.mqttv3.MqttAsyncClient
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttException
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence

private const val MQTT_PORT = 1883
private const val MQTT_USERNAME = "IoTProyecto"
private const val MQTT_PASSWORD = "HOLA"
private const val TOPIC_ALL = "tinaco/#"
private const val TOPIC_DISTANCE = "tinaco/distancia"
private const val TOPIC_LEVEL = "tinaco/nivel"
private const val TOPIC_STATUS = "tinaco/estado"
private const val TOPIC_COMMAND = "tinaco/comando"
private const val COMMAND_ON = "ON"
private const val COMMAND_OFF = "OFF"

private val DeepOcean = Color(0xFF042A2B)
private val Ocean = Color(0xFF075E63)
private val Aqua = Color(0xFF21C6B8)
private val Foam = Color(0xFFEAFBF8)
private val Ink = Color(0xFF0B1F25)
private val MutedInk = Color(0xFF587178)
private val Warning = Color(0xFFFFB703)
private val Danger = Color(0xFFE54B4B)
private val Success = Color(0xFF2DBE7E)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AquaControlTheme {
                AquaControlScreen()
            }
        }
    }
}

@Composable
private fun AquaControlScreen() {
    val mqttController = remember { AquaControlMqttController() }
    val state by mqttController.uiState.collectAsState()
    var host by rememberSaveable { mutableStateOf("") }

    DisposableEffect(mqttController) {
        onDispose { mqttController.disconnect() }
    }

    AquaControlContent(
        state = state,
        host = host,
        onHostChange = { host = it },
        onConnect = { mqttController.connect(host) },
        onDisconnect = mqttController::disconnect,
        onCommand = mqttController::publishCommand,
    )
}

@Composable
private fun AquaControlContent(
    state: AquaControlUiState,
    host: String,
    onHostChange: (String) -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onCommand: (String) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(DeepOcean, Ocean, Color(0xFFBDEBE4)),
                ),
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Header(state)
            ConnectionCard(
                state = state,
                host = host,
                onHostChange = onHostChange,
                onConnect = onConnect,
                onDisconnect = onDisconnect,
            )
            LevelCard(state)
            TelemetryCards(state)
            MotorControlCard(state = state, onCommand = onCommand)
            TopicCard()
        }
    }
}

@Composable
private fun Header(state: AquaControlUiState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "AquaControl IoT",
                style = MaterialTheme.typography.headlineLarge,
                color = Color.White,
                fontWeight = FontWeight.Black,
            )
            Text(
                text = "Monitoreo MQTT del tinaco",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.78f),
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        StatusPill(state.connectionLabel, state.connectionColor)
    }
}

@Composable
private fun ConnectionCard(
    state: AquaControlUiState,
    host: String,
    onHostChange: (String) -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
) {
    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(containerColor = Color.White.copy(alpha = 0.96f)),
        shape = RoundedCornerShape(28.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "Broker MQTT",
                style = MaterialTheme.typography.titleLarge,
                color = Ink,
                fontWeight = FontWeight.Bold,
            )
            OutlinedTextField(
                value = host,
                onValueChange = onHostChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("IP de la Raspberry Pi") },
                placeholder = { Text("Ej. 192.168.1.42") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                supportingText = { Text("Se usara tcp://<IP>:$MQTT_PORT con usuario $MQTT_USERNAME") },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onConnect,
                    enabled = state.connectionStatus != ConnectionStatus.CONNECTING,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Ocean),
                ) {
                    Text(if (state.connectionStatus == ConnectionStatus.CONNECTING) "Conectando" else "Conectar")
                }
                OutlinedButton(
                    onClick = onDisconnect,
                    enabled = state.connectionStatus != ConnectionStatus.DISCONNECTED,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Desconectar")
                }
            }
            if (state.brokerUri.isNotBlank()) {
                Text(
                    text = state.brokerUri,
                    color = MutedInk,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            state.errorMessage?.let { error ->
                Surface(
                    color = Danger.copy(alpha = 0.10f),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Danger.copy(alpha = 0.25f)),
                ) {
                    Text(
                        text = error,
                        modifier = Modifier.padding(12.dp),
                        color = Danger,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun LevelCard(state: AquaControlUiState) {
    val rawLevel = state.levelPercent?.coerceIn(0f, 100f)
    val progress by animateFloatAsState(
        targetValue = (rawLevel ?: 0f) / 100f,
        label = "NivelTinaco",
    )

    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(containerColor = Color(0xFFF7FFFD)),
        shape = RoundedCornerShape(32.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WaterGauge(progress = progress, hasData = rawLevel != null)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Nivel del tinaco", color = MutedInk, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = rawLevel?.let { String.format(Locale.US, "%.0f%%", it) } ?: "--%",
                    color = Ink,
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Black,
                )
                ProgressBar(progress = progress, color = levelColor(rawLevel))
                Text(
                    text = when {
                        rawLevel == null -> "Esperando tinaco/nivel"
                        rawLevel >= 100f -> "Lleno: la ESP32 debe apagar el motor"
                        rawLevel <= 20f -> "Nivel bajo: PWM alto si automatico esta activo"
                        else -> "Rango operativo normal"
                    },
                    color = MutedInk,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun WaterGauge(progress: Float, hasData: Boolean) {
    Canvas(
        modifier = Modifier
            .width(110.dp)
            .height(174.dp),
    ) {
        val stroke = 4.dp.toPx()
        val tankSize = Size(width = size.width * 0.72f, height = size.height * 0.86f)
        val topLeft = Offset(x = (size.width - tankSize.width) / 2f, y = size.height * 0.07f)
        val radius = CornerRadius(26.dp.toPx(), 26.dp.toPx())
        val innerTopLeft = Offset(topLeft.x + stroke, topLeft.y + stroke)
        val innerSize = Size(tankSize.width - stroke * 2f, tankSize.height - stroke * 2f)
        val fillHeight = innerSize.height * progress.coerceIn(0f, 1f)

        if (hasData && fillHeight > 0f) {
            drawRoundRect(
                brush = Brush.verticalGradient(listOf(Aqua, Color(0xFF0B8FA0))),
                topLeft = Offset(innerTopLeft.x, innerTopLeft.y + innerSize.height - fillHeight),
                size = Size(innerSize.width, fillHeight),
                cornerRadius = radius,
            )
        }
        drawRoundRect(
            color = Color.White.copy(alpha = 0.82f),
            topLeft = topLeft,
            size = tankSize,
            cornerRadius = radius,
            style = Stroke(width = stroke),
        )
        drawRoundRect(
            color = Ocean.copy(alpha = 0.45f),
            topLeft = topLeft,
            size = tankSize,
            cornerRadius = radius,
            style = Stroke(width = 1.dp.toPx()),
        )
    }
}

@Composable
private fun ProgressBar(progress: Float, color: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(14.dp)
            .clip(RoundedCornerShape(50))
            .background(Color(0xFFD9ECE8)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .background(color),
        )
    }
}

@Composable
private fun TelemetryCards(state: AquaControlUiState) {
    BoxWithConstraints {
        if (maxWidth > 620.dp) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DistanceCard(state, Modifier.weight(1f))
                StatusCard(state, Modifier.weight(1f))
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DistanceCard(state, Modifier.fillMaxWidth())
                StatusCard(state, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun DistanceCard(state: AquaControlUiState, modifier: Modifier = Modifier) {
    StatCard(
        title = "Distancia actual",
        value = state.distanceCm?.let { String.format(Locale.US, "%.1f cm", it) } ?: "-- cm",
        caption = "Topic: $TOPIC_DISTANCE",
        accent = Aqua,
        modifier = modifier,
    )
}

@Composable
private fun StatusCard(state: AquaControlUiState, modifier: Modifier = Modifier) {
    StatCard(
        title = "Estado ESP32",
        value = state.espStatus.ifBlank { "Sin datos" },
        caption = state.lastUpdateLabel,
        accent = state.connectionColor,
        modifier = modifier,
    )
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    caption: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(
        modifier = modifier,
        colors = CardDefaults.elevatedCardColors(containerColor = Color.White.copy(alpha = 0.94f)),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(accent),
            )
            Text(title, color = MutedInk, style = MaterialTheme.typography.titleMedium)
            Text(
                value,
                color = Ink,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(caption, color = MutedInk, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun MotorControlCard(state: AquaControlUiState, onCommand: (String) -> Unit) {
    val ready = state.isReady

    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(containerColor = Color.White.copy(alpha = 0.96f)),
        shape = RoundedCornerShape(28.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Control del motor",
                        style = MaterialTheme.typography.titleLarge,
                        color = Ink,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = state.motorLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MutedInk,
                    )
                }
                StatusPill(if (ready) "Listo" else "Sin MQTT", if (ready) Success else Warning)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { onCommand(COMMAND_ON) },
                    enabled = ready,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Success),
                ) {
                    Text("ON")
                }
                Button(
                    onClick = { onCommand(COMMAND_OFF) },
                    enabled = ready,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Danger),
                ) {
                    Text("OFF")
                }
            }
            Text(
                text = "Publica en $TOPIC_COMMAND. ON activa el modo automatico; OFF apaga y desactiva automatico.",
                style = MaterialTheme.typography.bodySmall,
                color = MutedInk,
            )
        }
    }
}

@Composable
private fun TopicCard() {
    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(containerColor = DeepOcean.copy(alpha = 0.78f)),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Suscripcion", color = Color.White, style = MaterialTheme.typography.titleMedium)
            Text(TOPIC_ALL, color = Aqua, fontWeight = FontWeight.Bold)
            Text(
                text = "Lectura: $TOPIC_DISTANCE, $TOPIC_LEVEL, $TOPIC_STATUS",
                color = Color.White.copy(alpha = 0.72f),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun StatusPill(label: String, color: Color) {
    val animatedColor by animateColorAsState(targetValue = color, label = "StatusPill")
    Surface(
        shape = RoundedCornerShape(50),
        color = animatedColor.copy(alpha = 0.16f),
        border = BorderStroke(1.dp, animatedColor.copy(alpha = 0.42f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(animatedColor),
            )
            Text(
                text = label,
                color = animatedColor,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

private fun levelColor(level: Float?): Color = when {
    level == null -> MutedInk
    level <= 20f -> Warning
    level >= 100f -> Danger
    else -> Success
}

private class AquaControlMqttController {
    private val _uiState = MutableStateFlow(AquaControlUiState())
    val uiState: StateFlow<AquaControlUiState> = _uiState.asStateFlow()

    private var client: MqttAsyncClient? = null

    fun connect(hostInput: String) {
        val brokerUri = buildBrokerUri(hostInput)
        if (brokerUri == null) {
            _uiState.update { it.copy(errorMessage = "Ingresa la IP de la Raspberry Pi") }
            return
        }

        closeClient()
        _uiState.update {
            it.copy(
                connectionStatus = ConnectionStatus.CONNECTING,
                brokerUri = brokerUri,
                errorMessage = null,
                subscribed = false,
            )
        }

        val newClient = try {
            MqttAsyncClient(
                brokerUri,
                "AquaControlAndroid-${System.currentTimeMillis()}",
                MemoryPersistence(),
            )
        } catch (exception: MqttException) {
            _uiState.update {
                it.copy(
                    connectionStatus = ConnectionStatus.ERROR,
                    errorMessage = "No se pudo crear el cliente MQTT: ${exception.safeMessage}",
                )
            }
            return
        }

        client = newClient
        newClient.setCallback(object : MqttCallbackExtended {
            override fun connectComplete(reconnect: Boolean, serverURI: String?) {
                if (client !== newClient) return
                if (reconnect) {
                    _uiState.update {
                        it.copy(
                            connectionStatus = ConnectionStatus.CONNECTED,
                            brokerUri = serverURI ?: brokerUri,
                            errorMessage = null,
                            subscribed = false,
                        )
                    }
                    subscribeToTinaco(newClient)
                }
            }

            override fun connectionLost(cause: Throwable?) {
                if (client !== newClient) return
                _uiState.update {
                    it.copy(
                        connectionStatus = ConnectionStatus.RECONNECTING,
                        subscribed = false,
                        errorMessage = "Conexion MQTT perdida: ${cause.safeMessage}",
                    )
                }
            }

            override fun messageArrived(topic: String?, message: MqttMessage?) {
                if (topic == null || message == null) return
                val payload = message.payload.toString(Charsets.UTF_8).trim()
                handleMessage(topic, payload)
            }

            override fun deliveryComplete(token: IMqttDeliveryToken?) = Unit
        })

        val options = MqttConnectOptions().apply {
            userName = MQTT_USERNAME
            password = MQTT_PASSWORD.toCharArray()
            isCleanSession = true
            isAutomaticReconnect = true
            connectionTimeout = 8
            keepAliveInterval = 20
        }

        try {
            newClient.connect(
                options,
                null,
                mqttActionListener(
                    onSuccess = {
                        if (client !== newClient) return@mqttActionListener
                        _uiState.update {
                            it.copy(
                                connectionStatus = ConnectionStatus.CONNECTED,
                                errorMessage = null,
                                subscribed = false,
                            )
                        }
                        subscribeToTinaco(newClient)
                    },
                    onFailure = { exception ->
                        if (client !== newClient) return@mqttActionListener
                        _uiState.update {
                            it.copy(
                                connectionStatus = ConnectionStatus.ERROR,
                                subscribed = false,
                                errorMessage = "No se pudo conectar al broker: ${exception.safeMessage}",
                            )
                        }
                    },
                ),
            )
        } catch (exception: MqttException) {
            _uiState.update {
                it.copy(
                    connectionStatus = ConnectionStatus.ERROR,
                    subscribed = false,
                    errorMessage = "Error al iniciar conexion: ${exception.safeMessage}",
                )
            }
        }
    }

    fun disconnect() {
        closeClient()
        _uiState.update {
            it.copy(
                connectionStatus = ConnectionStatus.DISCONNECTED,
                brokerUri = "",
                errorMessage = null,
                subscribed = false,
            )
        }
    }

    fun publishCommand(command: String) {
        val currentClient = client
        if (currentClient?.isConnected != true) {
            _uiState.update { it.copy(errorMessage = "MQTT no esta conectado") }
            return
        }

        val message = MqttMessage(command.toByteArray(Charsets.UTF_8)).apply {
            qos = 1
            isRetained = false
        }

        try {
            currentClient.publish(
                TOPIC_COMMAND,
                message,
                null,
                mqttActionListener(
                    onSuccess = {
                        _uiState.update {
                            it.copy(
                                lastMotorCommand = MotorCommand.fromPayload(command),
                                errorMessage = null,
                            )
                        }
                    },
                    onFailure = { exception ->
                        _uiState.update {
                            it.copy(errorMessage = "No se pudo publicar $command: ${exception.safeMessage}")
                        }
                    },
                ),
            )
        } catch (exception: MqttException) {
            _uiState.update { it.copy(errorMessage = "Error publicando $command: ${exception.safeMessage}") }
        }
    }

    private fun subscribeToTinaco(mqttClient: MqttAsyncClient) {
        try {
            mqttClient.subscribe(
                TOPIC_ALL,
                1,
                null,
                mqttActionListener(
                    onSuccess = {
                        if (client !== mqttClient) return@mqttActionListener
                        _uiState.update { it.copy(subscribed = true, errorMessage = null) }
                    },
                    onFailure = { exception ->
                        if (client !== mqttClient) return@mqttActionListener
                        _uiState.update {
                            it.copy(
                                subscribed = false,
                                errorMessage = "Conecto, pero fallo la suscripcion $TOPIC_ALL: ${exception.safeMessage}",
                            )
                        }
                    },
                ),
            )
        } catch (exception: MqttException) {
            _uiState.update {
                it.copy(errorMessage = "Error al suscribirse a $TOPIC_ALL: ${exception.safeMessage}")
            }
        }
    }

    private fun handleMessage(topic: String, payload: String) {
        _uiState.update { current ->
            when (topic) {
                TOPIC_DISTANCE -> current.copy(
                    distanceCm = payload.firstFloatOrNull(),
                    lastTopic = topic,
                    lastPayload = payload,
                    errorMessage = null,
                )

                TOPIC_LEVEL -> current.copy(
                    levelPercent = payload.firstFloatOrNull(),
                    lastTopic = topic,
                    lastPayload = payload,
                    errorMessage = null,
                )

                TOPIC_STATUS -> current.copy(
                    espStatus = payload,
                    lastTopic = topic,
                    lastPayload = payload,
                    errorMessage = null,
                )

                else -> current.copy(
                    lastTopic = topic,
                    lastPayload = payload,
                    errorMessage = null,
                )
            }
        }
    }

    private fun closeClient() {
        val currentClient = client
        client = null
        if (currentClient != null) {
            try {
                if (currentClient.isConnected) currentClient.disconnect()
            } catch (_: MqttException) {
                // Closing is best-effort during lifecycle changes or reconnects.
            }
            try {
                currentClient.close()
            } catch (_: MqttException) {
                // Closing is best-effort during lifecycle changes or reconnects.
            }
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
        return if (authority.contains(":")) normalized else "$normalized:$MQTT_PORT"
    }
}

private data class AquaControlUiState(
    val connectionStatus: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    val brokerUri: String = "",
    val subscribed: Boolean = false,
    val distanceCm: Float? = null,
    val levelPercent: Float? = null,
    val espStatus: String = "Sin datos",
    val lastMotorCommand: MotorCommand = MotorCommand.UNKNOWN,
    val lastTopic: String = "",
    val lastPayload: String = "",
    val errorMessage: String? = null,
) {
    val isReady: Boolean
        get() = connectionStatus == ConnectionStatus.CONNECTED && subscribed

    val connectionLabel: String
        get() = when (connectionStatus) {
            ConnectionStatus.DISCONNECTED -> "Desconectado"
            ConnectionStatus.CONNECTING -> "Conectando"
            ConnectionStatus.CONNECTED -> if (subscribed) "MQTT listo" else "Conectado"
            ConnectionStatus.RECONNECTING -> "Reconectando"
            ConnectionStatus.ERROR -> "Error"
        }

    val connectionColor: Color
        get() = when (connectionStatus) {
            ConnectionStatus.CONNECTED -> if (subscribed) Success else Warning
            ConnectionStatus.CONNECTING, ConnectionStatus.RECONNECTING -> Warning
            ConnectionStatus.ERROR -> Danger
            ConnectionStatus.DISCONNECTED -> Color.White.copy(alpha = 0.72f)
        }

    val motorLabel: String
        get() = when (lastMotorCommand) {
            MotorCommand.AUTO_ON -> "Ultimo comando: ON, modo automatico solicitado"
            MotorCommand.OFF -> "Ultimo comando: OFF, motor apagado solicitado"
            MotorCommand.UNKNOWN -> "Sin comando enviado desde esta app"
        }

    val lastUpdateLabel: String
        get() = if (lastTopic.isBlank()) "Esperando tinaco/estado" else "Ultimo: $lastTopic = $lastPayload"
}

private enum class ConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    RECONNECTING,
    ERROR,
}

private enum class MotorCommand {
    UNKNOWN,
    AUTO_ON,
    OFF;

    companion object {
        fun fromPayload(payload: String): MotorCommand = when (payload.trim().uppercase(Locale.US)) {
            COMMAND_ON -> AUTO_ON
            COMMAND_OFF -> OFF
            else -> UNKNOWN
        }
    }
}

private val Throwable?.safeMessage: String
    get() = this?.localizedMessage?.takeIf { it.isNotBlank() } ?: "sin detalle"

private fun String.firstFloatOrNull(): Float? = Regex("-?\\d+(?:\\.\\d+)?")
    .find(this)
    ?.value
    ?.toFloatOrNull()

private inline fun mqttActionListener(
    crossinline onSuccess: () -> Unit,
    crossinline onFailure: (Throwable?) -> Unit,
): IMqttActionListener = object : IMqttActionListener {
    override fun onSuccess(asyncActionToken: IMqttToken?) = onSuccess()

    override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) = onFailure(exception)
}

@Preview(showBackground = true, widthDp = 390)
@Composable
private fun AquaControlPreview() {
    AquaControlTheme {
        AquaControlContent(
            state = AquaControlUiState(
                connectionStatus = ConnectionStatus.CONNECTED,
                brokerUri = "tcp://192.168.1.42:1883",
                subscribed = true,
                distanceCm = 12.4f,
                levelPercent = 70f,
                espStatus = "Sensor OK / automatico activo",
                lastMotorCommand = MotorCommand.AUTO_ON,
                lastTopic = TOPIC_LEVEL,
                lastPayload = "70",
            ),
            host = "192.168.1.42",
            onHostChange = {},
            onConnect = {},
            onDisconnect = {},
            onCommand = {},
        )
    }
}
