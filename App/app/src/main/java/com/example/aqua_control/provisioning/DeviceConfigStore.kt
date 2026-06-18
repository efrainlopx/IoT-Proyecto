package com.example.aqua_control.provisioning

import android.content.Context
import com.example.aqua_control.config.ProjectConfig

data class DeviceConfig(
    val brokerHost: String,
    val wifiSsid: String,
    val esp32SetupHost: String,
    val mqttUser: String,
    val mqttPassword: String,
)

class DeviceConfigStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): DeviceConfig = DeviceConfig(
        brokerHost = prefs.getString(KEY_BROKER_HOST, ProjectConfig.DEFAULT_RASPBERRY_HOST)
            ?: ProjectConfig.DEFAULT_RASPBERRY_HOST,
        wifiSsid = prefs.getString(KEY_WIFI_SSID, ProjectConfig.DEFAULT_WORK_WIFI_SSID)
            ?: ProjectConfig.DEFAULT_WORK_WIFI_SSID,
        esp32SetupHost = prefs.getString(KEY_ESP32_SETUP_HOST, ProjectConfig.DEFAULT_ESP32_SETUP_HOST)
            ?: ProjectConfig.DEFAULT_ESP32_SETUP_HOST,
        mqttUser = prefs.getString(KEY_MQTT_USER, ProjectConfig.MQTT_USERNAME)
            ?: ProjectConfig.MQTT_USERNAME,
        mqttPassword = prefs.getString(KEY_MQTT_PASSWORD, ProjectConfig.MQTT_PASSWORD)
            ?: ProjectConfig.MQTT_PASSWORD,
    )

    fun saveConnection(brokerHost: String, wifiSsid: String, mqttUser: String, mqttPassword: String) {
        prefs.edit()
            .putString(KEY_BROKER_HOST, brokerHost.trim())
            .putString(KEY_WIFI_SSID, wifiSsid.trim())
            .putString(KEY_MQTT_USER, mqttUser.trim())
            .putString(KEY_MQTT_PASSWORD, mqttPassword)
            .apply()
    }

    fun saveProvisioning(request: EspProvisioningRequest) {
        prefs.edit()
            .putString(KEY_BROKER_HOST, request.mqttHost.trim())
            .putString(KEY_WIFI_SSID, request.wifiSsid.trim())
            .putString(KEY_ESP32_SETUP_HOST, request.esp32SetupHost.trim())
            .putString(KEY_MQTT_USER, request.mqttUser.trim())
            .putString(KEY_MQTT_PASSWORD, request.mqttPassword)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "aquacontrol_device_config"
        private const val KEY_BROKER_HOST = "broker_host"
        private const val KEY_WIFI_SSID = "wifi_ssid"
        private const val KEY_ESP32_SETUP_HOST = "esp32_setup_host"
        private const val KEY_MQTT_USER = "mqtt_user"
        private const val KEY_MQTT_PASSWORD = "mqtt_password"
    }
}
