# Enriched Graph Report - AquaControl IoT

## Summary
- Base Graphify graph: 202 nodes, 332 links.
- Enriched graph: 440 nodes, 684 links.
- Added: 238 nodes, 352 links.
- Outputs:
  - `graphify-out/graph_enriched.json`
  - `graphify-out/graph_enriched.dot`
  - `graphify-out/graph_enriched.svg`
  - `graphify-out/graph_enriched.mmd`
  - `graphify-out/GRAPH_REPORT_ENRICHED.md`

## What Graphify Originally Covered
- `App/app/build.gradle.kts`
- `App/app/src/androidTest/java/com/example/aqua_control/ExampleInstrumentedTest.kt`
- `App/app/src/main/java/com/example/aqua_control/DashboardActivity.kt`
- `App/app/src/main/java/com/example/aqua_control/MainActivity.kt`
- `App/app/src/main/java/com/example/aqua_control/config/ProjectConfig.kt`
- `App/app/src/main/java/com/example/aqua_control/data/AuthDatabaseHelper.kt`
- `App/app/src/main/java/com/example/aqua_control/model/ControlMode.kt`
- `App/app/src/main/java/com/example/aqua_control/mqtt/AquaMqttClient.kt`
- `App/app/src/main/java/com/example/aqua_control/provisioning/DeviceConfigStore.kt`
- `App/app/src/main/java/com/example/aqua_control/provisioning/EspProvisioningClient.kt`
- `App/app/src/main/java/com/example/aqua_control/ui/TankLevelView.kt`
- `App/app/src/test/java/com/example/aqua_control/ExampleUnitTest.kt`
- `App/build.gradle.kts`
- `App/settings.gradle.kts`
- `Docs/provisionamiento_app_esp32_raspberry.md`
- `Docs/prueba_esp32_raspberry_mqtt.md`
- `Docs/prueba_esp32_ultrasonico_mqtt.md`
- `README.md`
- `Raspberry/setup_raspberry_mqtt.sh`

## Added File Coverage
- docs: 5 file(s)
  - `README.md`
  - `Docs/provisionamiento_app_esp32_raspberry.md`
  - `Docs/prueba_esp32_raspberry_mqtt.md`
  - `Docs/prueba_esp32_ultrasonico_mqtt.md`
  - `Notas/Bitacora_Raspberry_MQTT.txt`
- android_xml: 21 file(s)
  - `App/app/src/main/AndroidManifest.xml`
  - `App/app/src/main/res/drawable/button_danger.xml`
  - `App/app/src/main/res/drawable/button_outline.xml`
  - `App/app/src/main/res/drawable/button_primary.xml`
  - `App/app/src/main/res/drawable/button_success.xml`
  - `App/app/src/main/res/drawable/card_dark.xml`
  - `App/app/src/main/res/drawable/card_surface.xml`
  - `App/app/src/main/res/drawable/ic_launcher_background.xml`
  - `App/app/src/main/res/drawable/ic_launcher_foreground.xml`
  - `App/app/src/main/res/drawable/input_surface.xml`
  - `App/app/src/main/res/drawable/screen_background.xml`
  - `App/app/src/main/res/drawable/status_badge.xml`
  - `App/app/src/main/res/layout/activity_dashboard.xml`
  - `App/app/src/main/res/layout/activity_login.xml`
  - `App/app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`
  - `App/app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml`
  - `App/app/src/main/res/values/colors.xml`
  - `App/app/src/main/res/values/strings.xml`
  - `App/app/src/main/res/values/themes.xml`
  - `App/app/src/main/res/xml/backup_rules.xml`
  - `App/app/src/main/res/xml/data_extraction_rules.xml`
- firmware: 2 file(s)
  - `ESP32/ESP32_Prueba_Sensor_Ultrasonico/Sensor_Ultrasonico_ESP32/Sensor_Ultrasonico_ESP32.ino`
  - `ESP32/prueba_mqtt_esp32/prueba_mqtt_esp32.ino`
- infra: 2 file(s)
  - `compose.yaml`
  - `mosquitto/config/mosquitto.conf`

## Enriched Node Types
- `android_activity`: 2
- `android_network_policy`: 1
- `android_permission`: 1
- `android_resource`: 37
- `component`: 10
- `config`: 4
- `doc_section`: 69
- `docker_image`: 1
- `docker_service`: 1
- `docker_volume`: 3
- `firmware`: 2
- `firmware_function`: 33
- `firmware_safety_state`: 4
- `gpio_pin`: 4
- `markdown`: 4
- `mosquitto_setting`: 6
- `mqtt_command`: 2
- `mqtt_endpoint`: 1
- `mqtt_secret`: 1
- `mqtt_topic`: 7
- `network_port`: 1
- `text`: 2
- `xml`: 21
- `xml_element`: 21

## Main Runtime Flow
- Android app connects to Mosquitto over `tcp://<Raspberry-IP>:1883`.
- Android subscribes to `tinaco/#` and consumes `tinaco/distancia`, `tinaco/nivel`, and `tinaco/estado`.
- Android publishes `ON` / `OFF` commands to `tinaco/comando`.
- ESP32 publishes sensor telemetry to `tinaco/distancia`, `tinaco/nivel`, and `tinaco/estado`.
- ESP32 subscribes to `tinaco/comando` and controls motor PWM on GPIO27.
- `compose.yaml` runs Eclipse Mosquitto and `mosquitto.conf` disables anonymous access.

## Useful Cross-File Findings
- The enriched graph adds structured Markdown sections, Android XML resource relationships, ESP32 firmware functions, Docker/Mosquitto runtime settings, and MQTT topic flow on top of the base Graphify extraction.
- The Android app is now XML-based: `MainActivity` handles local login and `DashboardActivity` handles MQTT telemetry/control.
- `Notas/Bitacora_Raspberry_MQTT.txt` still uses `aquacontrol/tinaco/nivel`, while current Android/ESP32 code uses `tinaco/nivel` and related `tinaco/*` topics.
- `README.md` mentions Java 21.0.10, but `App/app/build.gradle.kts` currently compiles source/target as Java 11.
- MQTT credentials are git-ignored in `mosquitto/config/password.txt`, but prototype credentials are hardcoded in Android and ESP32 source.
- Broker IP examples differ by context: docs/basic sketch use `10.152.254.168`, integrated ESP32 sketch uses `10.71.193.168`, and Android expects manual IP input.
- `AndroidManifest.xml` enables `android:usesCleartextTraffic="true"`, which is required for plain TCP MQTT but should be revisited for production.

## Top Relations
- `calls`: 100
- `references`: 82
- `contains`: 81
- `contains_section`: 69
- `method`: 66
- `references_resource`: 48
- `documents_component`: 36
- `contains_function`: 33
- `has_root`: 21
- `declares_resource`: 21
- `mentions_topic`: 17
- `references_file`: 12
- `publishes`: 11
- `mentions_command`: 8
- `defines_topic_constant`: 7
- `inherits`: 6
- `handles_command`: 6
- `uses_setting`: 6
