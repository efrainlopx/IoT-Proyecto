# Provisionamiento AquaControl: Raspberry, ESP32 y app

Este flujo evita dejar el WiFi fijo dentro del codigo del ESP32. El usuario solo conecta los componentes a la luz, configura una vez desde la app y despues el sistema arranca solo.

## Estado actual de la Raspberry

La Raspberry esta accesible por Ethernet en:

```text
192.168.1.127
```

Tambien se recomienda dejar un nombre local:

```text
aqua-pi.local
```

Si `aqua-pi.local` no resuelve desde Android o ESP32, usa directamente la IP `192.168.1.127`.

## 1. Preparar Raspberry

Desde la PC:

```bash
ssh efrain@192.168.1.127
cd ~/IoT-Proyecto
```

Ejecutar el script del repo:

```bash
chmod +x Raspberry/setup_raspberry_mqtt.sh
HOSTNAME_TARGET=aqua-pi MQTT_USER=IoTProyecto MQTT_PASSWORD=HOLA ./Raspberry/setup_raspberry_mqtt.sh
```

El script:

- Instala Docker, Docker Compose, Avahi y clientes Mosquitto.
- Configura el hostname `aqua-pi`.
- Crea `mosquitto/config/password.txt` si no existe.
- Levanta el contenedor `aquacontrol-mqtt`.
- Publica un mensaje de prueba en `tinaco/estado`.

Para verificar manualmente:

```bash
docker compose ps
docker compose logs -f mosquitto
```

## 2. Cargar firmware al ESP32

Abrir en Arduino IDE:

```text
ESP32/ESP32_Prueba_Sensor_Ultrasonico/Sensor_Ultrasonico_ESP32/Sensor_Ultrasonico_ESP32.ino
```

Librerias necesarias:

- `PubSubClient`
- Las librerias `WiFi`, `WebServer` y `Preferences` vienen con el core de ESP32.

Seleccionar:

- Placa ESP32 correspondiente.
- Puerto USB del ESP32.
- Monitor Serial a `115200` baudios.

Subir el firmware.

## 3. Primer arranque del ESP32

Si el ESP32 no tiene WiFi guardado, crea una red temporal:

```text
SSID: AquaControl-Setup
Password: aquapi123
IP portal: 192.168.4.1
```

El Monitor Serial debe mostrar:

```text
Portal de configuracion activo
SSID setup: AquaControl-Setup
IP setup: 192.168.4.1
```

## 4. Configurar ESP32 desde la app

En el celular:

1. Conectarse al WiFi `AquaControl-Setup`.
2. Abrir AquaControl.
3. Entrar con `admin / admin123` si aun no creaste otro usuario.
4. En `Configuracion inicial ESP32`, llenar:

| Campo | Valor recomendado |
| :--- | :--- |
| Host del portal ESP32 | `192.168.4.1` |
| WiFi al que se conectara el ESP32 | SSID real de tu router/hotspot |
| Contrasena WiFi | contrasena de esa red |
| Host/IP Raspberry o broker | `192.168.1.127` o `aqua-pi.local` |
| Usuario MQTT | `IoTProyecto` |
| Contrasena MQTT | `HOLA` |

5. Pulsar `Enviar configuracion al ESP32`.
6. El ESP32 guarda los datos y se reinicia.
7. Volver a conectar el celular al WiFi normal.
8. Pulsar `Conectar` en la app.

## 5. Operacion normal

Cuando ya quedo configurado:

- La Raspberry ejecuta Mosquitto.
- El ESP32 se conecta al WiFi guardado.
- El ESP32 publica:
  - `tinaco/distancia`
  - `tinaco/nivel`
  - `tinaco/estado`
- La app se conecta al broker y muestra el nivel del tinaco.
- La app envia comandos por `tinaco/comando`:
  - `ON`
  - `OFF`
  - `RESET_CONFIG`

## 6. Borrar configuracion del ESP32

Desde la app, con MQTT conectado, usar:

```text
Borrar WiFi guardado del ESP32
```

Eso envia:

```text
RESET_CONFIG
```

El ESP32 borra credenciales y vuelve a crear `AquaControl-Setup`.

Si MQTT no esta disponible, desde una terminal conectada a `AquaControl-Setup` tambien se puede ejecutar:

```bash
curl -X POST http://192.168.4.1/reset
```
