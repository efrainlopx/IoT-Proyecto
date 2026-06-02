Contenido:

# Prueba de conexión ESP32 ↔ Raspberry Pi mediante MQTT

## Objetivo

Verificar que la ESP32 pueda comunicarse correctamente con la Raspberry Pi mediante MQTT, usando Mosquitto ejecutado en Docker.

Esta prueba valida dos sentidos de comunicación:

- ESP32 publica mensajes hacia la Raspberry Pi.
- Raspberry Pi envía comandos hacia la ESP32.

## Arquitectura de prueba

```text
ESP32 → Hotspot Wi-Fi → Raspberry Pi → Mosquitto MQTT
ESP32 ← Hotspot Wi-Fi ← Raspberry Pi ← Mosquitto MQTT
Componentes utilizados
Raspberry Pi con Ubuntu Server 22.04.
Docker y Docker Compose.
Mosquitto MQTT ejecutado en contenedor Docker.
ESP32 Dev Module.
Arduino IDE.
Librería PubSubClient.
Hotspot Wi-Fi del celular.
Estructura recomendada del proyecto
IoT-Proyecto/
├── README.md
├── compose.yaml
├── mosquitto/
│   ├── config/
│   │   └── mosquitto.conf
│   ├── data/
│   │   └── .gitkeep
│   └── log/
│       └── .gitkeep
├── esp32/
│   └── prueba_mqtt_esp32/
│       └── prueba_mqtt_esp32.ino
└── docs/
    └── prueba_esp32_raspberry_mqtt.md
1. Conectar Raspberry Pi y ESP32 a la misma red

Para esta prueba se utilizó el hotspot del celular.

La arquitectura queda así:

Hotspot del celular
├── Raspberry Pi
│   └── Mosquitto MQTT
└── ESP32
    └── Cliente MQTT

Es importante que ambos dispositivos estén conectados a la misma red Wi-Fi.

2. Verificar la IP de la Raspberry Pi

En la Raspberry Pi se ejecuta:

hostname -I

Ejemplo de salida:

10.152.254.168

En este caso, la IP de la Raspberry Pi es:

10.152.254.168

Esta IP debe colocarse en el código de la ESP32:

const char* mqtt_server = "10.152.254.168";

Si se cambia de red, por ejemplo del módem de casa al hotspot del celular, la IP puede cambiar. En ese caso se debe volver a ejecutar:

hostname -I
3. Verificar que Mosquitto esté activo

En la Raspberry Pi, entrar a la carpeta del proyecto:

cd ~/IoT-Proyecto

Verificar los contenedores activos:

docker ps

Resultado esperado:

CONTAINER ID   IMAGE                 STATUS          PORTS                                         NAMES
xxxxxxxxxxxx   eclipse-mosquitto:2   Up              0.0.0.0:1883->1883/tcp, [::]:1883->1883/tcp   aquacontrol-mqtt

El contenedor importante es:

aquacontrol-mqtt

El puerto importante es:

1883
4. Escuchar mensajes MQTT desde la Raspberry Pi

En la Raspberry Pi, ejecutar:

docker exec -it aquacontrol-mqtt mosquitto_sub \
  -h localhost \
  -p 1883 \
  -u IoTProyecto \
  -P "CONTRASEÑA_MQTT" \
  -t "tinaco/#"

Este comando deja a la Raspberry escuchando todos los mensajes publicados en tópicos que empiecen con:

tinaco/

Por ejemplo:

tinaco/estado
tinaco/nivel
tinaco/comando
tinaco/bomba
5. Configurar la ESP32 en Arduino IDE

En Arduino IDE seleccionar:

Board: ESP32 Dev Module
Port: /dev/ttyUSB0
Monitor Serial: 115200 baud

También se debe instalar la librería:

PubSubClient

Ruta en Arduino IDE:

Sketch → Include Library → Manage Libraries → PubSubClient → Install
6. Código de prueba para la ESP32

El código de la ESP32 debe incluir:

#include <WiFi.h>
#include <PubSubClient.h>

const char* ssid = "NOMBRE_DEL_HOTSPOT";
const char* password = "CONTRASEÑA_DEL_HOTSPOT";

const char* mqtt_server = "10.152.254.168";
const int mqtt_port = 1883;

const char* mqtt_user = "IoTProyecto";
const char* mqtt_password = "CONTRASEÑA_MQTT";

La ESP32 publica mensajes en:

tinaco/estado
tinaco/nivel

Y recibe comandos desde:

tinaco/comando
7. Resultado esperado al cargar el código en la ESP32

En el Monitor Serial de Arduino IDE debe aparecer algo similar a:

Conectando al WiFi...
WiFi conectado correctamente
IP de la ESP32: 10.152.254.xxx
Conectando a MQTT... conectado
Publicado en tinaco/nivel: 75

En la terminal de la Raspberry Pi donde se ejecutó mosquitto_sub, debe aparecer:

ESP32 conectada al broker MQTT
75
75
75

Esto confirma que la ESP32 puede publicar mensajes hacia la Raspberry Pi.

8. Enviar comandos desde Raspberry Pi hacia ESP32

Desde otra terminal en la Raspberry Pi, enviar comando de encendido:

docker exec -it aquacontrol-mqtt mosquitto_pub \
  -h localhost \
  -p 1883 \
  -u IoTProyecto \
  -P "CONTRASEÑA_MQTT" \
  -t "tinaco/comando" \
  -m "ON"

Enviar comando de apagado:

docker exec -it aquacontrol-mqtt mosquitto_pub \
  -h localhost \
  -p 1883 \
  -u IoTProyecto \
  -P "CONTRASEÑA_MQTT" \
  -t "tinaco/comando" \
  -m "OFF"
9. Resultado esperado en el Monitor Serial de la ESP32

Al enviar el comando ON, debe aparecer:

Mensaje recibido en topic: tinaco/comando
Contenido: ON
Comando recibido: Encender bomba

Al enviar el comando OFF, debe aparecer:

Mensaje recibido en topic: tinaco/comando
Contenido: OFF
Comando recibido: Apagar bomba

Esto confirma que la Raspberry Pi también puede enviar comandos hacia la ESP32.

10. Tópicos MQTT utilizados
Tópico	Función
tinaco/estado	Estado general de la ESP32
tinaco/nivel	Nivel simulado o calculado del tinaco
tinaco/comando	Comando enviado desde Raspberry hacia ESP32
tinaco/bomba	Estado de la bomba o actuador
11. Comprobación final

La prueba se considera correcta si se cumplen estas condiciones:

La Raspberry Pi está conectada al hotspot.
La ESP32 está conectada al mismo hotspot.
Mosquitto está activo en Docker.
La ESP32 publica mensajes en tinaco/nivel.
La Raspberry Pi recibe esos mensajes con mosquitto_sub.
La Raspberry Pi envía comandos ON y OFF.
La ESP32 recibe esos comandos en el Monitor Serial.
Conclusión

La conexión ESP32 ↔ Raspberry Pi mediante MQTT funciona correctamente.

Con esta prueba se validó una comunicación bidireccional entre la ESP32 y la Raspberry Pi usando Mosquitto en Docker. Esta base permite continuar con la integración del sensor ultrasónico, el cálculo del nivel de agua y el control de una bomba mediante comandos MQTT.
