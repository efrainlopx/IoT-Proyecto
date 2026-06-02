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

## Configuración local de credenciales MQTT

Por seguridad, el archivo `mosquitto/config/password.txt` no se incluye en el repositorio de GitHub, ya que contiene las credenciales del broker MQTT.

Después de clonar el proyecto en otra computadora o en la Raspberry Pi, se debe crear localmente el usuario y la contraseña MQTT.

### 1. Entrar a la carpeta del proyecto

```bash
cd IoT-Proyecto

### 2. Crear el usuario MQTT
docker run --rm -it \
  -v "$PWD/mosquitto/config:/mosquitto/config" \
  eclipse-mosquitto:2 \
  mosquitto_passwd -c /mosquitto/config/password.txt IoTProyecto

El comando solicitará escribir y confirmar una contraseña para el usuario:

IoTProyecto
### 3. Corregir permisos del archivo de contraseña
sudo chown 1883:1883 mosquitto/config/password.txt
sudo chmod 600 mosquitto/config/password.txt

Estos permisos permiten que el contenedor de Mosquitto lea el archivo password.txt sin exponerlo innecesariamente.

### 4. Levantar el broker MQTT
docker compose up -d

### 5. Verificar que el contenedor esté activo
docker compose ps

El contenedor debe aparecer con estado Up:

aquacontrol-mqtt   eclipse-mosquitto:2   Up   0.0.0.0:1883->1883/tcp
