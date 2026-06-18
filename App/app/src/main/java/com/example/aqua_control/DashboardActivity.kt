package com.example.aqua_control

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import com.example.aqua_control.config.ProjectConfig
import com.example.aqua_control.model.ControlMode
import com.example.aqua_control.mqtt.AquaMqttClient
import com.example.aqua_control.provisioning.DeviceConfigStore
import com.example.aqua_control.provisioning.EspProvisioningClient
import com.example.aqua_control.provisioning.EspProvisioningRequest
import com.example.aqua_control.ui.TankLevelView
import java.util.Locale

class DashboardActivity : Activity(), AquaMqttClient.Listener {
    private lateinit var mqttClient: AquaMqttClient
    private lateinit var configStore: DeviceConfigStore
    private val provisioningClient = EspProvisioningClient()

    private lateinit var inputRaspberryIp: EditText
    private lateinit var inputHotspotName: EditText
    private lateinit var inputEsp32SetupHost: EditText
    private lateinit var inputWifiSsid: EditText
    private lateinit var inputWifiPassword: EditText
    private lateinit var inputMqttUser: EditText
    private lateinit var inputMqttPassword: EditText
    private lateinit var inputAutoThreshold: EditText
    private lateinit var radioAutomatic: RadioButton
    private lateinit var radioManual: RadioButton
    private lateinit var textConnectionStatus: TextView
    private lateinit var textNetworkSummary: TextView
    private lateinit var textLevel: TextView
    private lateinit var textDistance: TextView
    private lateinit var textEspStatus: TextView
    private lateinit var textModeStatus: TextView
    private lateinit var textLastMessage: TextView
    private lateinit var textProvisioningStatus: TextView
    private lateinit var tankLevelView: TankLevelView
    private lateinit var buttonManualOn: Button
    private lateinit var buttonManualOff: Button
    private lateinit var buttonProvisionEsp32: Button

    private var connected = false
    private var subscribed = false
    private var levelPercent: Float? = null
    private var distanceCm: Float? = null
    private var controlMode = ControlMode.AUTOMATIC
    private var autoCommandSent = false
    private var manualMotorRunning = false
    private var lastPublishedCommand: String? = null
    private var pendingCommandMessage: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        mqttClient = AquaMqttClient(this)
        configStore = DeviceConfigStore(this)
        bindViews()
        configureInitialState()
        configureActions()
    }

    override fun onDestroy() {
        mqttClient.disconnect()
        super.onDestroy()
    }

    private fun bindViews() {
        inputRaspberryIp = findViewById(R.id.inputRaspberryIp)
        inputHotspotName = findViewById(R.id.inputHotspotName)
        inputEsp32SetupHost = findViewById(R.id.inputEsp32SetupHost)
        inputWifiSsid = findViewById(R.id.inputWifiSsid)
        inputWifiPassword = findViewById(R.id.inputWifiPassword)
        inputMqttUser = findViewById(R.id.inputMqttUser)
        inputMqttPassword = findViewById(R.id.inputMqttPassword)
        inputAutoThreshold = findViewById(R.id.inputAutoThreshold)
        radioAutomatic = findViewById(R.id.radioAutomatic)
        radioManual = findViewById(R.id.radioManual)
        textConnectionStatus = findViewById(R.id.textConnectionStatus)
        textNetworkSummary = findViewById(R.id.textNetworkSummary)
        textLevel = findViewById(R.id.textLevel)
        textDistance = findViewById(R.id.textDistance)
        textEspStatus = findViewById(R.id.textEspStatus)
        textModeStatus = findViewById(R.id.textModeStatus)
        textLastMessage = findViewById(R.id.textLastMessage)
        textProvisioningStatus = findViewById(R.id.textProvisioningStatus)
        tankLevelView = findViewById(R.id.tankLevelView)
        buttonManualOn = findViewById(R.id.buttonManualOn)
        buttonManualOff = findViewById(R.id.buttonManualOff)
        buttonProvisionEsp32 = findViewById(R.id.buttonProvisionEsp32)
    }

    private fun configureInitialState() {
        val savedConfig = configStore.load()
        inputRaspberryIp.setText(savedConfig.brokerHost)
        inputHotspotName.setText(savedConfig.wifiSsid)
        inputEsp32SetupHost.setText(savedConfig.esp32SetupHost)
        inputWifiSsid.setText(savedConfig.wifiSsid)
        inputMqttUser.setText(savedConfig.mqttUser)
        inputMqttPassword.setText(savedConfig.mqttPassword)
        inputAutoThreshold.setText(ProjectConfig.DEFAULT_AUTO_THRESHOLD_PERCENT.toInt().toString())
        updateNetworkSummary()
        setConnectionStatus("Desconectado", R.color.warning)
        setProvisioningStatus("Para configurar el ESP32, conecta el celular a AquaControl-Setup y envia estos datos.", false)
        setModeStatus("Modo automatico listo. Conecta MQTT para controlar el tinaco.")
        updateControls()
    }

    private fun configureActions() {
        findViewById<Button>(R.id.buttonConnect).setOnClickListener {
            val brokerHost = brokerHostInput()
            val wifiSsid = wifiSsidInput()
            val mqttUser = mqttUserInput()
            val mqttPassword = mqttPasswordInput()
            configStore.saveConnection(brokerHost, wifiSsid, mqttUser, mqttPassword)
            updateNetworkSummary()
            mqttClient.connect(brokerHost, mqttUser, mqttPassword)
        }
        findViewById<Button>(R.id.buttonDisconnect).setOnClickListener {
            mqttClient.disconnect()
            connected = false
            subscribed = false
            setConnectionStatus("Desconectado", R.color.warning)
            setLastMessage("MQTT desconectado")
            updateControls()
        }
        buttonProvisionEsp32.setOnClickListener { sendEsp32Provisioning() }
        findViewById<RadioGroup>(R.id.radioControlMode).setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.radioAutomatic -> activateAutomaticMode()
                R.id.radioManual -> activateManualMode()
            }
        }
        findViewById<Button>(R.id.buttonApplyAuto).setOnClickListener {
            radioAutomatic.isChecked = true
            activateAutomaticMode()
        }
        buttonManualOn.setOnClickListener {
            radioManual.isChecked = true
            manualMotorRunning = true
            requestMotorCommand(ProjectConfig.COMMAND_ON, "Manual: motor encendido por el usuario", force = true)
            updateControls()
        }
        buttonManualOff.setOnClickListener {
            manualMotorRunning = false
            requestMotorCommand(ProjectConfig.COMMAND_OFF, "Manual: motor apagado por el usuario", force = true)
            updateControls()
        }
        findViewById<Button>(R.id.buttonResetEsp32Config).setOnClickListener {
            requestMotorCommand(ProjectConfig.COMMAND_RESET_CONFIG, "ESP32: borrar configuracion WiFi y volver a modo setup", force = true)
        }
    }

    private fun sendEsp32Provisioning() {
        val request = EspProvisioningRequest(
            esp32SetupHost = inputEsp32SetupHost.text.toString(),
            wifiSsid = wifiSsidInput(),
            wifiPassword = inputWifiPassword.text.toString(),
            mqttHost = brokerHostInput(),
            mqttUser = mqttUserInput(),
            mqttPassword = mqttPasswordInput(),
        )

        if (request.wifiSsid.isBlank()) {
            setProvisioningStatus("Ingresa el nombre de la red WiFi a la que se conectara el ESP32.", true)
            return
        }
        if (request.mqttHost.isBlank()) {
            setProvisioningStatus("Ingresa el host/IP de la Raspberry. Para tu red actual puedes usar 192.168.1.127.", true)
            return
        }

        inputHotspotName.setText(request.wifiSsid)
        configStore.saveProvisioning(request)
        updateNetworkSummary()
        buttonProvisionEsp32.isEnabled = false
        setProvisioningStatus("Enviando configuracion a ${request.esp32SetupHost.ifBlank { ProjectConfig.DEFAULT_ESP32_SETUP_HOST }}...", false)

        provisioningClient.send(
            request,
            object : EspProvisioningClient.Callback {
                override fun onSuccess(message: String) = runOnUiThread {
                    buttonProvisionEsp32.isEnabled = true
                    setProvisioningStatus("$message Despues vuelve a conectar el celular al WiFi normal y pulsa Conectar MQTT.", false)
                }

                override fun onError(message: String) = runOnUiThread {
                    buttonProvisionEsp32.isEnabled = true
                    setProvisioningStatus("No se pudo configurar el ESP32: $message", true)
                }
            },
        )
    }

    private fun activateAutomaticMode() {
        controlMode = ControlMode.AUTOMATIC
        manualMotorRunning = false
        val threshold = autoThresholdPercent()
        setModeStatus("Automatico activo: encendera al ${threshold.formatPercent()}% y apagara al llenarse.")
        evaluateControlRules()
        updateControls()
    }

    private fun activateManualMode() {
        controlMode = ControlMode.MANUAL
        autoCommandSent = false
        manualMotorRunning = false
        requestMotorCommand(ProjectConfig.COMMAND_OFF, "Manual seleccionado: motor apagado hasta que pulses ON")
        updateControls()
    }

    private fun evaluateControlRules() {
        val level = levelPercent ?: return
        val threshold = autoThresholdPercent()

        if (level >= ProjectConfig.FULL_LEVEL_PERCENT) {
            autoCommandSent = false
            manualMotorRunning = false
            requestMotorCommand(ProjectConfig.COMMAND_OFF, "Tinaco lleno: motor apagado por seguridad")
            updateControls()
            return
        }

        if (controlMode == ControlMode.AUTOMATIC) {
            if (level <= threshold && !autoCommandSent) {
                autoCommandSent = true
                requestMotorCommand(
                    ProjectConfig.COMMAND_ON,
                    "Automatico: nivel ${level.formatPercent()}%, motor activado hasta llenar",
                )
            } else if (autoCommandSent) {
                setModeStatus("Automatico: motor activo hasta que el tinaco llegue a 100%")
            } else {
                setModeStatus("Automatico: esperando nivel <= ${threshold.formatPercent()}%")
            }
        } else if (manualMotorRunning) {
            setModeStatus("Manual: motor encendido. Se apagara al llegar a 100%")
        } else {
            setModeStatus("Manual: motor apagado")
        }
    }

    private fun requestMotorCommand(command: String, message: String, force: Boolean = false) {
        if (!connected || !subscribed) {
            setModeStatus("MQTT no esta listo. Comando pendiente no enviado.")
            return
        }
        if (!force && lastPublishedCommand == command) {
            setModeStatus(message)
            return
        }
        pendingCommandMessage = message
        mqttClient.publishCommand(command)
    }

    private fun handleIncomingMessage(topic: String, payload: String) {
        setLastMessage("$topic = $payload")
        when (topic) {
            ProjectConfig.TOPIC_DISTANCE -> {
                distanceCm = payload.firstFloatOrNull()
                textDistance.text = distanceCm?.let { "Distancia: ${String.format(Locale.US, "%.1f", it)} cm" }
                    ?: "Distancia: sin lectura"
            }
            ProjectConfig.TOPIC_LEVEL -> {
                levelPercent = payload.firstFloatOrNull()?.coerceIn(0f, 100f)
                val level = levelPercent
                tankLevelView.setLevelPercent(level)
                textLevel.text = level?.let { "${it.formatPercent()}%" } ?: "--%"
                evaluateControlRules()
            }
            ProjectConfig.TOPIC_STATUS -> {
                textEspStatus.text = "ESP32: ${payload.ifBlank { "sin datos" }}"
            }
        }
    }

    private fun autoThresholdPercent(): Float {
        return inputAutoThreshold.text.toString().firstFloatOrNull()
            ?.coerceIn(1f, 99f)
            ?: ProjectConfig.DEFAULT_AUTO_THRESHOLD_PERCENT
    }

    private fun updateNetworkSummary() {
        val wifi = wifiSsidInput().ifBlank { "WiFi sin definir" }
        val broker = brokerHostInput().ifBlank { ProjectConfig.DEFAULT_RASPBERRY_HOST }
        textNetworkSummary.text = "WiFi $wifi | Broker $broker"
    }

    private fun updateControls() {
        val ready = connected && subscribed
        buttonManualOn.isEnabled = ready && controlMode == ControlMode.MANUAL
        buttonManualOff.isEnabled = ready && controlMode == ControlMode.MANUAL
        buttonManualOn.alpha = if (buttonManualOn.isEnabled) 1f else 0.45f
        buttonManualOff.alpha = if (buttonManualOff.isEnabled) 1f else 0.45f
    }

    private fun brokerHostInput(): String = inputRaspberryIp.text.toString().trim()
        .ifBlank { ProjectConfig.DEFAULT_RASPBERRY_HOST }

    private fun wifiSsidInput(): String {
        return inputWifiSsid.text.toString().trim()
            .ifBlank { inputHotspotName.text.toString().trim() }
    }

    private fun mqttUserInput(): String = inputMqttUser.text.toString().trim()
        .ifBlank { ProjectConfig.MQTT_USERNAME }

    private fun mqttPasswordInput(): String = inputMqttPassword.text.toString()
        .ifBlank { ProjectConfig.MQTT_PASSWORD }

    private fun setConnectionStatus(text: String, colorRes: Int) {
        textConnectionStatus.text = text
        textConnectionStatus.setTextColor(getColor(colorRes))
    }

    private fun setProvisioningStatus(text: String, isError: Boolean) {
        textProvisioningStatus.text = text
        textProvisioningStatus.setTextColor(getColor(if (isError) R.color.danger else R.color.muted_ink))
    }

    private fun setModeStatus(text: String) {
        textModeStatus.text = text
    }

    private fun setLastMessage(text: String) {
        textLastMessage.text = "Ultimo MQTT: $text"
    }

    override fun onConnecting(uri: String) = runOnUiThread {
        connected = false
        subscribed = false
        setConnectionStatus("Conectando", R.color.warning)
        setLastMessage("Conectando a $uri")
        updateControls()
    }

    override fun onConnected(uri: String) = runOnUiThread {
        connected = true
        setConnectionStatus("Conectado", R.color.success)
        setLastMessage("Conectado a $uri")
        updateControls()
    }

    override fun onSubscribed(topic: String) = runOnUiThread {
        connected = true
        subscribed = true
        setConnectionStatus("MQTT listo", R.color.success)
        setLastMessage("Suscrito a $topic")
        evaluateControlRules()
        updateControls()
    }

    override fun onDisconnected(message: String) = runOnUiThread {
        connected = false
        subscribed = false
        setConnectionStatus("Reconectando", R.color.warning)
        setLastMessage(message)
        updateControls()
    }

    override fun onMessage(topic: String, payload: String) = runOnUiThread {
        handleIncomingMessage(topic, payload)
    }

    override fun onCommandPublished(command: String) = runOnUiThread {
        lastPublishedCommand = command
        val status = pendingCommandMessage ?: "Comando $command publicado"
        pendingCommandMessage = null
        setModeStatus(status)
        setLastMessage("${ProjectConfig.TOPIC_COMMAND} = $command")
    }

    override fun onError(message: String) = runOnUiThread {
        setConnectionStatus("Error", R.color.danger)
        setLastMessage(message)
        setModeStatus(message)
        updateControls()
    }
}

private fun String.firstFloatOrNull(): Float? = Regex("-?\\d+(?:\\.\\d+)?")
    .find(this)
    ?.value
    ?.toFloatOrNull()

private fun Float.formatPercent(): String = String.format(Locale.US, "%.0f", this)
