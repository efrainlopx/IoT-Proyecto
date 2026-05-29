# AquaControl IoT

Sistema IoT para el monitoreo y control del llenado de tinacos mediante una aplicación móvil.

## Descripción

El proyecto propone un prototipo capaz de medir el nivel de agua de un recipiente que representa un tinaco y controlar un motor pequeño que simula una bomba de agua.

La comunicación entre los dispositivos se realizará mediante MQTT. Una Raspberry Pi 4 con Ubuntu 22.04 ejecutará un contenedor Docker con Eclipse Mosquitto, el cual funcionará como broker central de mensajes.

## Tecnologías consideradas

* Raspberry Pi 4 con Ubuntu 22.04.
* Docker y Docker Compose.
* Eclipse Mosquitto como broker MQTT.
* ESP32 programada en C++ mediante Arduino IDE.
* Sensor ultrasónico para medir el nivel de agua.
* Motor pequeño para simular una bomba.
* Aplicación móvil desarrollada en Kotlin con Java 21.0.10.

## Arquitectura inicial

```text
Aplicación móvil Kotlin
        │
        │ MQTT / Wi-Fi
        ▼
Raspberry Pi 4
Docker + Eclipse Mosquitto
        ▲
        │ MQTT / Wi-Fi
        │
ESP32 + Sensor ultrasónico + Motor
```

## Servicio incluido actualmente

En esta etapa, el repositorio contiene la configuración necesaria para ejecutar el broker MQTT Mosquitto mediante Docker Compose.

## Ejecución

Crear localmente el archivo de credenciales MQTT:

```bash
docker run --rm -it \
  -v "$PWD/mosquitto/config:/mosquitto/config" \
  eclipse-mosquitto:2 \
  mosquitto_passwd -c /mosquitto/config/password.txt aquacontrol
```

Levantar el contenedor:

```bash
docker compose up -d
```

Verificar el servicio:

```bash
docker compose ps
docker compose logs mosquitto
```

Detener el servicio:

```bash
docker compose down
```

## Seguridad

El archivo `mosquitto/config/password.txt` no se incluye en el repositorio, ya que contiene las credenciales necesarias para conectarse al broker MQTT.

