package com.example.aqua_control.config

object ProjectConfig {
    const val DEFAULT_RASPBERRY_HOST = "192.168.1.127"
    const val DEFAULT_RASPBERRY_MDNS = "aqua-pi.local"
    const val DEFAULT_RASPBERRY_IP = DEFAULT_RASPBERRY_HOST
    const val DEFAULT_WORK_WIFI_SSID = ""
    const val DEFAULT_HOTSPOT_SSID = "AquaControl-Setup"
    const val DEFAULT_ESP32_SETUP_HOST = "192.168.4.1"
    const val DEFAULT_ESP32_SETUP_PASSWORD = "aquapi123"

    const val MQTT_PORT = 1883
    const val MQTT_USERNAME = "IoTProyecto"
    const val MQTT_PASSWORD = "HOLA"

    const val TOPIC_ALL = "tinaco/#"
    const val TOPIC_DISTANCE = "tinaco/distancia"
    const val TOPIC_LEVEL = "tinaco/nivel"
    const val TOPIC_STATUS = "tinaco/estado"
    const val TOPIC_COMMAND = "tinaco/comando"

    const val COMMAND_ON = "ON"
    const val COMMAND_OFF = "OFF"
    const val COMMAND_RESET_CONFIG = "RESET_CONFIG"

    const val DEFAULT_AUTO_THRESHOLD_PERCENT = 30f
    const val FULL_LEVEL_PERCENT = 100f

    const val DEFAULT_LOGIN_USER = "admin"
    const val DEFAULT_LOGIN_PASSWORD = "admin123"
}
