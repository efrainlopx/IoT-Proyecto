# Prueba ESP32 con sensor ultrasonico e integracion MQTT

## Objetivo

Documentar el avance realizado con el sensor ultrasonico en la ESP32.

Primero se hizo una prueba local usando solo la ESP32 y el sensor ultrasonico. En esa prueba se verifico en el Monitor Serial del Arduino IDE que el sensor midiera correctamente la distancia en centimetros.

Despues, como el sensor ya funcionaba correctamente, se integro con la prueba MQTT que ya se tenia entre la ESP32 y la Raspberry Pi.

El resultado final es:

```text
ESP32 mide distancia -> calcula nivel -> publica datos por MQTT a Raspberry Pi
```

## Arquitectura de prueba

### Primera prueba: ESP32 con sensor ultrasonico

```text
Sensor ultrasonico -> ESP32 -> Monitor Serial Arduino IDE
```

Esta primera prueba sirvio para comprobar que:

- El sensor ultrasonico estaba bien conectado.
- La ESP32 podia leer la distancia en centimetros.
- El rango de lectura funcionaba para distancias aproximadas de `2.5 cm` a `30 cm`.
- El Monitor Serial mostraba valores reales y estables.

### Segunda prueba: ESP32 con sensor ultrasonico y MQTT

```text
Sensor ultrasonico -> ESP32 -> Hotspot Wi-Fi -> Raspberry Pi -> Mosquitto MQTT
```

Componentes utilizados:

- ESP32 Dev Module.
- Sensor ultrasonico.
- Raspberry Pi con Mosquitto en Docker.
- Arduino IDE.
- Libreria PubSubClient.
- Hotspot Wi-Fi de celular.

## Archivo utilizado en la ESP32

El codigo de la prueba del sensor y despues la integracion MQTT se trabajo en:

```text
ESP32/ESP32_Prueba_Sensor_Ultrasonico/Sensor_Ultrasonico_ESP32/Sensor_Ultrasonico_ESP32.ino
```

En la primera version, el sketch solo realizaba:

- Configuracion de pines `TRIG` y `ECHO`.
- Lectura de distancia en centimetros.
- Promedio de varias lecturas para estabilizar el valor.
- Impresion de resultados en el Monitor Serial.
- Validacion del rango esperado de `2.5 cm` a `30 cm`.

Despues se modifico el sketch para incluir tambien:

- Conexion Wi-Fi al hotspot.
- Conexion MQTT al broker Mosquitto de la Raspberry Pi.
- Lectura de distancia en centimetros.
- Calculo del nivel del tinaco en porcentaje.
- Publicacion de distancia y nivel por MQTT.
- Recepcion de comandos `ON` y `OFF` desde MQTT.

## Pines del sensor ultrasonico

En el codigo se configuraron estos pines:

| Sensor | ESP32 |
| :--- | :--- |
| VCC | 5V |
| GND | GND |
| TRIG | GPIO 5 |
| ECHO | GPIO 18 |

Si se usa un HC-SR04 comun, el pin `ECHO` puede entregar 5V. La ESP32 trabaja con 3.3V en sus GPIO, por lo que se recomienda usar un divisor de voltaje o un sensor compatible con 3.3V.

## Primera prueba: solo ESP32 y sensor ultrasonico

Antes de integrar MQTT, se cargo un codigo de prueba en la ESP32 para validar solamente el sensor.

En el Monitor Serial del Arduino IDE, a `115200` baudios, se esperaba ver lecturas como:

```text
Prueba de sensor ultrasonico con ESP32
Rango esperado: 2.5 cm a 30 cm
--------------------------------------
Distancia: 10.24 cm | Estado: dentro del rango
Distancia: 10.18 cm | Estado: dentro del rango
```

Esta prueba fue exitosa porque el sensor si mostro los centimetros correctamente en el Monitor Serial.

Una vez confirmado que la medicion funcionaba, se continuo con la integracion Wi-Fi + MQTT.

## Medidas del tinaco simulado

Para calcular el nivel se definieron dos medidas:

```cpp
const float distanciaVacio = 30.0;
const float distanciaLleno = 5.0;
```

Interpretacion:

- `distanciaVacio = 30.0`: distancia medida cuando el tinaco esta vacio.
- `distanciaLleno = 5.0`: distancia medida cuando el tinaco esta lleno.

El calculo usado es:

```cpp
nivel = ((distanciaVacio - distanciaActual) / (distanciaVacio - distanciaLleno)) * 100.0;
```

Ejemplo:

```text
distanciaActual = 10 cm
nivel = ((30 - 10) / (30 - 5)) * 100
nivel = 80 %
```

El codigo limita el resultado entre `0%` y `100%` para evitar valores fuera de rango si la lectura varia un poco.

## Topicos MQTT utilizados

| Topico | Funcion |
| :--- | :--- |
| `tinaco/distancia` | Distancia medida por el sensor, en cm |
| `tinaco/nivel` | Nivel calculado del tinaco, en porcentaje |
| `tinaco/estado` | Estado de conexion o estado del sensor |
| `tinaco/comando` | Comandos enviados hacia la ESP32 |

## Recepcion de datos en la Raspberry Pi

En la Raspberry Pi se uso Mosquitto dentro del contenedor Docker `aquacontrol-mqtt`.

Para escuchar todos los datos publicados por la ESP32 se ejecuta:

```bash
docker exec -it aquacontrol-mqtt mosquitto_sub \
  -h localhost \
  -p 1883 \
  -u IoTProyecto \
  -P "CONTRASEÑA_MOSQUITTO" \
  -t "tinaco/#" \
  -v
```

La opcion `-v` muestra el topico junto con el mensaje recibido.

Ejemplo de salida esperada:

```text
tinaco/estado online
tinaco/distancia 10.24
tinaco/nivel 79.0
tinaco/distancia 10.18
tinaco/nivel 79.3
```

## Envio de comandos desde Raspberry Pi

Para enviar un comando de encendido:

```bash
docker exec -it aquacontrol-mqtt mosquitto_pub \
  -h localhost \
  -p 1883 \
  -u IoTProyecto \
  -P "CONTRASEÑA_MOSQUITTO" \
  -t "tinaco/comando" \
  -m "ON"
```

Para enviar un comando de apagado:

```bash
docker exec -it aquacontrol-mqtt mosquitto_pub \
  -h localhost \
  -p 1883 \
  -u IoTProyecto \
  -P "CONTRASEÑA_MOSQUITTO" \
  -t "tinaco/comando" \
  -m "OFF"
```

## Resultado esperado en Arduino IDE

En el Monitor Serial a `115200` baudios debe verse algo similar a:

```text
ESP32 + Sensor ultrasonico + MQTT
---------------------------------
Distancia vacio: 30.00 cm
Distancia lleno: 5.00 cm
Conectando al WiFi: prueba
WiFi conectado correctamente
IP de la ESP32: 10.152.254.xxx
Conectando a MQTT... conectado
Suscrito a: tinaco/comando
Distancia: 10.24 cm | Nivel: 79.0 % | Estado sensor: dentro del rango
Publicado en tinaco/distancia y tinaco/nivel
```

Al recibir comandos desde MQTT:

```text
Mensaje recibido en topic: tinaco/comando
Contenido: ON
Comando recibido: Encender bomba
```

```text
Mensaje recibido en topic: tinaco/comando
Contenido: OFF
Comando recibido: Apagar bomba
```

## Comprobacion final

La prueba se considera correcta si:

- La ESP32 se conecta al hotspot.
- La ESP32 se conecta al broker MQTT de la Raspberry Pi.
- El Monitor Serial muestra distancia real en centimetros.
- La Raspberry recibe datos en `tinaco/distancia` y `tinaco/nivel`.
- La ESP32 recibe comandos publicados en `tinaco/comando`.
