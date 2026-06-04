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
cd IoT-Proyecto.
```
### 2. Crear el usuario MQTT
```bash
docker run --rm -it \
  -v "$PWD/mosquitto/config:/mosquitto/config" \
  eclipse-mosquitto:2 \
  mosquitto_passwd -c /mosquitto/config/password.txt IoTProyecto
```
El comando solicitará escribir y confirmar una contraseña para el usuario:

IoTProyecto

### 3. Corregir permisos del archivo de contraseña
```bash
sudo chown 1883:1883 mosquitto/config/password.txt
sudo chmod 600 mosquitto/config/password.txt
```

Estos permisos permiten que el contenedor de Mosquitto lea el archivo password.txt sin exponerlo innecesariamente

### 4. Levantar el broker MQTT
```bash
docker compose up -d
```

### 5. Verificar que el contenedor esté activo
```bash
docker compose ps
```

El contenedor debe aparecer con estado Up:

```text
aquacontrol-mqtt   eclipse-mosquitto:2   Up   0.0.0.0:1883->1883/tcp
```

## Avances de pruebas realizadas

### 1. Prueba ESP32 con Raspberry Pi mediante MQTT

Primero se realizo una prueba de comunicacion entre la ESP32 y la Raspberry Pi usando Mosquitto como broker MQTT.

En esta prueba:

- La Raspberry Pi ejecuto Mosquitto dentro del contenedor Docker `aquacontrol-mqtt`.
- La ESP32 se conecto al mismo hotspot Wi-Fi que la Raspberry Pi.
- La ESP32 publico mensajes hacia la Raspberry mediante MQTT.
- La Raspberry pudo recibir los mensajes publicados por la ESP32.
- Tambien se probaron comandos enviados desde Raspberry hacia la ESP32 usando el topico `tinaco/comando`.

Los topicos principales usados en esta etapa fueron:

| Topico | Funcion |
| :--- | :--- |
| `tinaco/estado` | Estado de conexion de la ESP32 |
| `tinaco/nivel` | Nivel simulado publicado por la ESP32 |
| `tinaco/comando` | Comandos `ON` y `OFF` enviados hacia la ESP32 |

La documentacion completa de esta prueba esta en:

```text
Docs/prueba_esp32_raspberry_mqtt.md
```

El codigo usado para esta prueba esta en:

```text
ESP32/prueba_mqtt_esp32/prueba_mqtt_esp32.ino
```

### 2. Prueba ESP32 con sensor ultrasonico

Despues se hizo una prueba local usando solo la ESP32 y el sensor ultrasonico.

En esta etapa no se uso MQTT. El objetivo fue comprobar primero que el sensor estuviera bien conectado y que la ESP32 pudiera leer la distancia correctamente.

La prueba mostro las mediciones en el Monitor Serial del Arduino IDE a `115200` baudios.

Se valido un rango aproximado de:

```text
2.5 cm a 30 cm
```

Esta prueba fue exitosa porque el Monitor Serial mostro las lecturas reales en centimetros.

### 3. Integracion ESP32 + sensor ultrasonico + MQTT

Una vez confirmado que el sensor funcionaba correctamente, se integro con la comunicacion MQTT.

En esta version:

- La ESP32 mide distancia con el sensor ultrasonico.
- Calcula el nivel del tinaco simulado en porcentaje.
- Ajusta el brillo de un LED en `GPIO26` mediante PWM segun la distancia medida.
- Publica la distancia y el nivel hacia Mosquitto en la Raspberry Pi.
- Sigue escuchando comandos `ON` y `OFF` desde `tinaco/comando`.
- Reduce los mensajes del Monitor Serial cuando el tinaco esta lleno y el LED esta apagado.

El calculo del nivel usa dos medidas base:

```text
distanciaVacio = 30 cm
distanciaLleno = 5 cm
```

Formula usada:

```text
nivel = ((distanciaVacio - distanciaActual) / (distanciaVacio - distanciaLleno)) * 100
```

Los topicos MQTT usados en la integracion son:

| Topico | Funcion |
| :--- | :--- |
| `tinaco/distancia` | Distancia medida por el sensor, en cm |
| `tinaco/nivel` | Nivel calculado del tinaco, en porcentaje |
| `tinaco/estado` | Estado de conexion, sensor o LED |
| `tinaco/comando` | Comandos enviados hacia la ESP32 |

El LED en `GPIO26` funciona como indicador visual:

- Con distancia de lleno, el LED se apaga.
- Al aumentar la distancia, el brillo sube gradualmente.
- Con distancia de vacio, el LED llega a brillo maximo.

Los comandos MQTT tienen esta funcion:

| Comando | Funcion |
| :--- | :--- |
| `ON` | Activa el control automatico del LED por PWM |
| `OFF` | Apaga el LED y desactiva el control automatico |

Para escuchar los datos desde la Raspberry Pi se uso:

```bash
docker exec -it aquacontrol-mqtt mosquitto_sub \
  -h localhost \
  -p 1883 \
  -u IoTProyecto \
  -P "CONTRASEÑA_MOSQUITTO" \
  -t "tinaco/#" \
  -v
```

La documentacion completa de esta etapa esta en:

```text
Docs/prueba_esp32_ultrasonico_mqtt.md
```

El codigo usado para la prueba del sensor y la integracion MQTT esta en:

```text
ESP32/ESP32_Prueba_Sensor_Ultrasonico/Sensor_Ultrasonico_ESP32/Sensor_Ultrasonico_ESP32.ino
```
