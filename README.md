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

## Provisionamiento recomendado

El flujo actual evita dejar el WiFi fijo dentro del firmware del ESP32.

En primer arranque, el ESP32 crea una red temporal:

```text
SSID: AquaControl-Setup
Password: aquapi123
IP: 192.168.4.1
```

Desde la app Android se envia al ESP32:

- SSID del WiFi real.
- Contrasena del WiFi.
- Host/IP de la Raspberry o broker MQTT.
- Usuario y contrasena MQTT.

La Raspberry puede usarse por IP, por ejemplo:

```text
192.168.1.127
```

O por nombre local si Avahi/mDNS esta disponible:

```text
aqua-pi.local
```

Guia completa:

```text
Docs/provisionamiento_app_esp32_raspberry.md
```

Script de preparacion para Raspberry:

```text
Raspberry/setup_raspberry_mqtt.sh
```

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
- Controla un motor en `GPIO27` mediante TIP120 y PWM.
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
| `tinaco/estado` | Estado de conexion, sensor, LED o motor |
| `tinaco/comando` | Comandos enviados hacia la ESP32 |

El LED en `GPIO26` funciona como indicador visual:

- Con distancia de lleno, el LED se apaga.
- Al aumentar la distancia, el brillo sube gradualmente.
- Con distancia de vacio, el LED llega a brillo maximo.

Los comandos MQTT tienen esta funcion:

| Comando | Funcion |
| :--- | :--- |
| `ON` | Activa el control automatico del motor por PWM |
| `OFF` | Apaga el motor y desactiva el control automatico |

El modulo del motor quedo conectado asi:

```text
ESP32 GPIO27 -> resistencia 470 ohms -> Base TIP120
Colector TIP120 -> negativo del motor
Emisor TIP120 -> GND
Positivo del motor -> +5V externo
Diodo: catodo a +5V, anodo al colector TIP120
GND fuente 5V externa -> GND ESP32
```

Datos principales del codigo del motor:

```cpp
const int PIN_MOTOR = 27;
const int frecuenciaPwmMotor = 5000;
const int resolucionPwmMotor = 8;
const int potenciaMotorMaxima = 255;
const int potenciaMotorMinimaGiro = 45;
const int potenciaMotorArranque = 140;
const unsigned long duracionPulsoArranqueMotorMs = 300;
```

Se agrego un pulso de arranque para que el motor pueda comenzar a girar aunque el PWM calculado sea bajo. Si el motor esta apagado y debe arrancar con poca potencia, primero recibe un pulso de `140/255` durante `300 ms` y despues baja al PWM calculado.

Modulos de seguridad agregados:

| Condicion | Accion de seguridad |
| :--- | :--- |
| Sensor sin lectura | Apaga el motor y publica estado de modo seguro |
| Distancia fuera de rango | Apaga el motor y evita usar esa lectura para controlar el motor |
| Perdida de Wi-Fi | Apaga el motor mientras intenta reconectar |
| Perdida de MQTT | Apaga el motor mientras intenta reconectar al broker |
| Nivel `>= 100%` | Mantiene el motor apagado |
| Nivel `<= 20%` | Usa PWM alto/maximo cuando el modo automatico esta activo |

Los estados de seguridad se publican en `tinaco/estado` cuando la conexion MQTT esta disponible. En el codigo se usa una funcion de modo seguro para apagar el motor y desactivar el control automatico:

```cpp
activarModoSeguro("motivo");
```

El umbral configurado para activar potencia alta por nivel bajo es:

```cpp
const float nivelMinimoMotorAlto = 20.0;
```

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

Para apagar el motor desde la Raspberry Pi durante la prueba se uso:

```bash
docker exec -it aquacontrol-mqtt mosquitto_pub \
  -h localhost \
  -p 1883 \
  -u IoTProyecto \
  -P "HOLA" \
  -t "tinaco/comando" \
  -m "OFF"
```

La documentacion completa de esta etapa esta en:

```text
Docs/prueba_esp32_ultrasonico_mqtt.md
```

El firmware actual que se debe abrir en Arduino IDE esta en:

```text
ESP32/ESP32_Prueba_Sensor_Ultrasonico/Sensor_Ultrasonico_ESP32/Sensor_Ultrasonico_ESP32.ino
```

Ese sketch ya incluye portal de configuracion `AquaControl-Setup`, guarda WiFi/MQTT en memoria flash y conserva la lectura del sensor ultrasonico, LED, motor y topicos MQTT.

El sketch `ESP32/prueba_mqtt_esp32/prueba_mqtt_esp32.ino` queda solo como prueba historica de comunicacion MQTT con datos fijos.
