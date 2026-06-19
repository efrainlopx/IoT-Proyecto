# AquaControl IoT

AquaControl IoT es un prototipo para monitorear el nivel de agua de un tinaco y controlar una bomba mediante una app Android, una ESP32 y un broker MQTT ejecutado en Raspberry Pi.

El sistema mide la distancia con un sensor ultrasonico, calcula el porcentaje de llenado, publica los datos por MQTT y permite controlar el motor en modo automatico o manual desde la app.

## Arquitectura

```text
App Android
  - Login local
  - Configuracion ESP32
  - Visualizacion del tinaco
  - Control automatico/manual
        |
        | MQTT por WiFi
        v
Raspberry Pi 4
  - Ubuntu 22.04
  - Docker Compose
  - Eclipse Mosquitto
        ^
        | MQTT por WiFi
        |
ESP32
  - Sensor ultrasonico
  - LED PWM indicador
  - Motor por PWM
  - Portal de configuracion WiFi/MQTT
```

La Raspberry funciona como broker central. La app y la ESP32 deben estar en la misma red WiFi que la Raspberry o tener acceso a su IP.

## Componentes

- Raspberry Pi 4 con Ubuntu 22.04.
- Docker y Docker Compose.
- Eclipse Mosquitto como broker MQTT.
- ESP32 programada con Arduino IDE.
- Sensor ultrasonico HC-SR04 o equivalente.
- Motor DC para simular bomba.
- TIP120 o modulo de control para motor.
- App Android en Kotlin con interfaz XML.

## Estructura Del Proyecto

```text
App/                         Proyecto Android
Docs/                        Documentacion de pruebas y provisionamiento
ESP32/                       Firmware y pruebas ESP32
Raspberry/                   Script de preparacion para Raspberry
mosquitto/                   Configuracion Docker de Mosquitto
compose.yaml                 Servicio Mosquitto
Dockerfile / Gradle / XML    Recursos y configuracion de la app
```

Archivos principales:

```text
App/app/src/main/java/com/example/aqua_control/DashboardActivity.kt
App/app/src/main/java/com/example/aqua_control/provisioning/EspProvisioningClient.kt
ESP32/ESP32_Prueba_Sensor_Ultrasonico/Sensor_Ultrasonico_ESP32/Sensor_Ultrasonico_ESP32.ino
Raspberry/setup_raspberry_mqtt.sh
Docs/provisionamiento_app_esp32_raspberry.md
```

## Broker MQTT En Raspberry

Mosquitto corre dentro del contenedor `aquacontrol-mqtt`.

Levantar el broker:

```bash
docker compose up -d
```

Verificar estado:

```bash
docker compose ps
docker compose logs -f mosquitto
```

Detener el broker:

```bash
docker compose down
```

La configuracion usada por Mosquitto esta en:

```text
mosquitto/config/mosquitto.conf
```

El archivo de contrasenas MQTT se mantiene fuera de Git:

```text
mosquitto/config/password.txt
```

## Preparar Raspberry

En la Raspberry, dentro del proyecto:

```bash
cd ~/IoT-Proyecto
chmod +x Raspberry/setup_raspberry_mqtt.sh
HOSTNAME_TARGET=aqua-pi MQTT_USER=IoTProyecto MQTT_PASSWORD=HOLA ./Raspberry/setup_raspberry_mqtt.sh
```

El script realiza estas acciones:

- Configura el hostname, por ejemplo `aqua-pi`.
- Instala dependencias base disponibles.
- Activa Docker y Avahi.
- Crea el usuario MQTT si no existe.
- Ajusta permisos de `password.txt`.
- Levanta Mosquitto con Docker Compose.
- Ejecuta una publicacion MQTT local de prueba.

Para conocer la IP de la Raspberry:

```bash
hostname -I
ip a
```

Ejemplos de IP:

```text
192.168.1.127  Ethernet
192.168.1.130  WiFi
```

En la app y en el ESP32 se debe usar la IP que corresponda a la interfaz activa.

## Credenciales MQTT

Para el prototipo se usan estas credenciales:

```text
Usuario: IoTProyecto
Contrasena: HOLA
Puerto: 1883
```

Si se necesita crear el usuario manualmente:

```bash
docker run --rm -it \
  -v "$PWD/mosquitto/config:/mosquitto/config" \
  eclipse-mosquitto:2 \
  mosquitto_passwd -c /mosquitto/config/password.txt IoTProyecto
```

Corregir permisos:

```bash
sudo chown 1883:1883 mosquitto/config/password.txt
sudo chmod 600 mosquitto/config/password.txt
```

## Topicos MQTT

| Topico | Direccion | Funcion |
| :--- | :--- | :--- |
| `tinaco/estado` | ESP32 -> App | Estado de conexion, motor o modo seguro |
| `tinaco/distancia` | ESP32 -> App | Distancia medida por el sensor en centimetros |
| `tinaco/nivel` | ESP32 -> App | Nivel calculado del tinaco en porcentaje |
| `tinaco/comando` | App -> ESP32 | Comandos para motor o configuracion |

Comandos soportados:

| Comando | Funcion |
| :--- | :--- |
| `ON` | Activa el control del motor |
| `OFF` | Apaga el motor |
| `RESET_CONFIG` | Borra la configuracion WiFi/MQTT guardada en la ESP32 |

## Firmware ESP32

Firmware actual:

```text
ESP32/ESP32_Prueba_Sensor_Ultrasonico/Sensor_Ultrasonico_ESP32/Sensor_Ultrasonico_ESP32.ino
```

Librerias necesarias en Arduino IDE:

- `PubSubClient`.
- `WiFi`, `WebServer` y `Preferences`, incluidas con el core de ESP32.

El firmware incluye:

- Lectura de sensor ultrasonico.
- Calculo de nivel de tinaco.
- Publicacion MQTT de distancia, nivel y estado.
- Suscripcion a comandos MQTT.
- Control PWM de LED indicador en `GPIO26`.
- Control PWM de motor en `GPIO27`.
- Modo seguro ante fallas de WiFi, MQTT, sensor o distancia fuera de rango.
- Portal de configuracion WiFi/MQTT.
- Persistencia de configuracion en memoria flash con `Preferences`.

### Pines Usados

| Pin | Funcion |
| :--- | :--- |
| `GPIO5` | Trigger sensor ultrasonico |
| `GPIO18` | Echo sensor ultrasonico |
| `GPIO26` | LED PWM indicador |
| `GPIO27` | Motor PWM |
| `GPIO0` | Boton `BOOT` para borrar configuracion al arrancar |

### Calculo Del Nivel

Medidas base del prototipo:

```cpp
const float distanciaVacio = 30.0;
const float distanciaLleno = 5.0;
```

Formula:

```text
nivel = ((distanciaVacio - distanciaActual) / (distanciaVacio - distanciaLleno)) * 100
```

El resultado se limita entre `0%` y `100%`.

## Provisionamiento ESP32 Desde La App

El WiFi y la IP del broker no estan fijos en el firmware. La ESP32 se configura desde la app.

Si no tiene configuracion guardada, la ESP32 crea una red temporal:

```text
SSID: AquaControl-Setup
Password: aquapi123
Portal: 192.168.4.1
```

Flujo:

1. Energizar ESP32.
2. Conectar el celular a `AquaControl-Setup`.
3. Abrir la app.
4. Enviar SSID, contrasena WiFi, IP del broker y credenciales MQTT.
5. La ESP32 guarda los datos y se reinicia.
6. La ESP32 se conecta automaticamente al WiFi real y al broker MQTT.

Campos en la app:

```text
Host del portal ESP32: 192.168.4.1
WiFi al que se conectara el ESP32: <SSID de la red>
Contrasena WiFi: <contrasena de la red>
Host/IP Raspberry o broker: <IP de la Raspberry>
Usuario MQTT: IoTProyecto
Contrasena MQTT: HOLA
```

Ejemplo con Raspberry por WiFi:

```text
Host/IP Raspberry o broker: 192.168.1.130
```

Ejemplo con Raspberry por Ethernet:

```text
Host/IP Raspberry o broker: 192.168.1.127
```

## Recuperacion Del ESP32

Si cambia la IP del broker, el ESP32 puede conservar una IP anterior. Hay tres formas de recuperar la configuracion.

### Desde La App

Con MQTT conectado, pulsar:

```text
Borrar WiFi guardado del ESP32
```

Esto envia:

```text
RESET_CONFIG
```

La ESP32 borra su configuracion y vuelve a crear `AquaControl-Setup`.

### Desde El Portal

Si el celular esta conectado a `AquaControl-Setup`:

```bash
curl -X POST http://192.168.4.1/reset
```

### Con Boton BOOT

1. Desconectar la ESP32.
2. Mantener presionado `BOOT`.
3. Conectar energia o presionar `EN/RST`.
4. Mantener `BOOT` aproximadamente `2.5` segundos.
5. Soltar cuando el Monitor Serial indique que borro la configuracion.

El Monitor Serial debe mostrar:

```text
Configuracion borrada por boton BOOT.
Portal de configuracion activo
SSID setup: AquaControl-Setup
IP setup: 192.168.4.1
```

Ademas, si MQTT falla varias veces, el firmware abre automaticamente `AquaControl-Setup` para permitir reconfigurar el broker.

## App Android

La app esta desarrollada en Kotlin usando vistas XML.

Funciones principales:

- Login local con SQLite.
- Usuario inicial `admin / admin123`.
- Creacion de usuarios locales.
- Configuracion de broker MQTT.
- Configuracion inicial de ESP32 por HTTP.
- Visualizacion del tinaco con una vista personalizada.
- Lectura de nivel y distancia desde MQTT.
- Modo automatico.
- Modo manual.
- Boton para borrar configuracion guardada del ESP32.

### Conexion MQTT En La App

Despues de configurar la ESP32 y volver al WiFi normal, llenar:

```text
Host/IP Raspberry o broker: <IP de la Raspberry>
Red WiFi de trabajo: <SSID de la red>
```

Luego pulsar:

```text
Conectar
```

Cuando funciona, la app muestra:

```text
MQTT listo
Ultimo MQTT: Suscrito a tinaco/#
```

## Modos De Control

### Automatico

La app activa el motor cuando el nivel del tinaco baja al umbral configurado.

Valor por defecto:

```text
30%
```

El motor se apaga cuando el tinaco llega a `100%`.

### Manual

El usuario puede encender el motor desde la app con `Motor ON` y apagarlo con `Motor OFF`.

Aunque este en modo manual, la app manda apagar el motor cuando el nivel llega a `100%`.

## Seguridad Del Sistema

| Condicion | Accion |
| :--- | :--- |
| Sensor sin lectura | Apaga motor y publica modo seguro |
| Distancia fuera de rango | Apaga motor y publica estado |
| WiFi desconectado | Apaga motor mientras reconecta |
| MQTT desconectado | Apaga motor mientras reconecta |
| Tinaco lleno | Apaga motor |
| Broker no disponible | Abre portal de configuracion despues de varios fallos |

## Verificacion MQTT

Escuchar todos los mensajes desde la Raspberry:

```bash
docker exec -it aquacontrol-mqtt mosquitto_sub \
  -h localhost \
  -u IoTProyecto \
  -P HOLA \
  -t 'tinaco/#' \
  -v
```

Mensajes esperados:

```text
tinaco/estado online
tinaco/nivel 75.0
tinaco/distancia 12.34
```

Publicar comandos manualmente:

```bash
docker exec -it aquacontrol-mqtt mosquitto_pub \
  -h localhost \
  -u IoTProyecto \
  -P HOLA \
  -t tinaco/comando \
  -m ON
```

Apagar motor:

```bash
docker exec -it aquacontrol-mqtt mosquitto_pub \
  -h localhost \
  -u IoTProyecto \
  -P HOLA \
  -t tinaco/comando \
  -m OFF
```

Borrar configuracion ESP32:

```bash
docker exec -it aquacontrol-mqtt mosquitto_pub \
  -h localhost \
  -u IoTProyecto \
  -P HOLA \
  -t tinaco/comando \
  -m RESET_CONFIG
```

## Graphify

El proyecto incluye salida de Graphify para visualizar relaciones entre app, firmware, documentacion, XML y configuracion de infraestructura.

Archivos principales:

```text
graphify-out/graph.json
graphify-out/graph.html
graphify-out/graph_enriched.json
graphify-out/graph_enriched.svg
graphify-out/GRAPH_REPORT.md
graphify-out/GRAPH_REPORT_ENRICHED.md
```

Regenerar grafo:

```bash
graphify update . --force
graphify cluster-only . --graph graphify-out/graph.json --no-label
python3 tools/build_enriched_graph.py
```

## Flujo De Uso Completo

1. Levantar Mosquitto en la Raspberry:

```bash
cd ~/IoT-Proyecto
docker compose up -d
```

2. Energizar la ESP32.

3. Si es primer arranque, configurar desde `AquaControl-Setup`.

4. Conectar el celular a la red WiFi normal.

5. Abrir la app y entrar con:

```text
admin / admin123
```

6. Conectar MQTT usando la IP activa de la Raspberry.

7. Verificar que la app muestre:

```text
MQTT listo
ESP32: online
Nivel y distancia del tinaco
```

8. Usar modo automatico o manual para controlar el motor.

## Documentacion Adicional

```text
Docs/provisionamiento_app_esp32_raspberry.md
Docs/prueba_esp32_raspberry_mqtt.md
Docs/prueba_esp32_ultrasonico_mqtt.md
```
