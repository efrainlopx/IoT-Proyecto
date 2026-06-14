# Prueba ESP32 con sensor ultrasonico e integracion MQTT

## Objetivo

Documentar el avance realizado con el sensor ultrasonico en la ESP32.

Primero se hizo una prueba local usando solo la ESP32 y el sensor ultrasonico. En esa prueba se verifico en el Monitor Serial del Arduino IDE que el sensor midiera correctamente la distancia en centimetros.

Despues, como el sensor ya funcionaba correctamente, se integro con la prueba MQTT que ya se tenia entre la ESP32 y la Raspberry Pi.

El resultado final es:

```text
ESP32 mide distancia -> calcula nivel -> ajusta LED y motor por PWM -> publica datos por MQTT a Raspberry Pi
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
Sensor ultrasonico -> ESP32 -> LED GPIO26
                         |
                         +-> Motor GPIO27 + TIP120
                         |
                         v
                    Hotspot Wi-Fi -> Raspberry Pi -> Mosquitto MQTT
```

Componentes utilizados:

- ESP32 Dev Module.
- Sensor ultrasonico.
- Raspberry Pi con Mosquitto en Docker.
- LED conectado al GPIO26.
- Motor conectado mediante TIP120 en el GPIO27.
- Fuente externa de 5V para el motor.
- Diodo de proteccion para el motor.
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
- Control de brillo del LED por PWM en el GPIO26.
- Control del motor por PWM en el GPIO27.
- Recepcion de comandos `ON` y `OFF` desde MQTT para activar o apagar el modo automatico del motor.

## Pines del sensor ultrasonico

En el codigo se configuraron estos pines:

| Sensor | ESP32 |
| :--- | :--- |
| VCC | 5V |
| GND | GND |
| TRIG | GPIO 5 |
| ECHO | GPIO 18 |

Si se usa un HC-SR04 comun, el pin `ECHO` puede entregar 5V. La ESP32 trabaja con 3.3V en sus GPIO, por lo que se recomienda usar un divisor de voltaje o un sensor compatible con 3.3V.

## LED indicador en GPIO26

Se agrego un LED conectado al GPIO26 para representar visualmente el estado de llenado.

Conexion recomendada:

```text
GPIO26 -> resistencia 220 ohms o 330 ohms -> anodo LED
catodo LED -> GND
```

El LED funciona con PWM:

- Cuando el sensor detecta la distancia de lleno, el LED se apaga.
- Conforme la distancia aumenta, el brillo del LED se incrementa gradualmente.
- Cuando el sensor detecta la distancia de vacio, el LED alcanza su brillo normal/maximo.

Con las medidas actuales:

| Estado | Distancia | Brillo LED |
| :--- | :--- | :--- |
| Lleno | `5 cm` o menos | `0/255` |
| Intermedio | Entre `5 cm` y `30 cm` | Brillo proporcional |
| Vacio | `30 cm` o mas | `255/255` |

El brillo se calcula con base en la distancia:

```text
brillo = ((distanciaActual - distanciaLleno) / (distanciaVacio - distanciaLleno)) * 255
```

Tambien se ajusto el Monitor Serial para que, cuando el sensor marque lleno y el LED este apagado, no imprima mensajes tan seguido. En ese estado reporta una vez al detectar lleno y despues aproximadamente una vez por minuto.

## Modulo del motor en GPIO27

Se agrego el modulo del motor usando un transistor TIP120 como etapa de potencia. El GPIO27 de la ESP32 no alimenta directamente el motor; solo controla la base del TIP120.

Conexiones realizadas:

```text
ESP32 GPIO27 -> resistencia 470 ohms -> Base TIP120

TIP120:
Base      <- resistencia desde GPIO27
Colector  <- negativo del motor
Emisor    -> GND

Motor:
Positivo  -> +5V externo
Negativo  -> Colector TIP120

Diodo de proteccion:
Catodo    -> +5V
Anodo     -> Colector TIP120

GND:
GND fuente 5V externa -> GND ESP32
```

Datos principales del codigo:

```cpp
const int PIN_MOTOR = 27;
const int frecuenciaPwmMotor = 5000;
const int resolucionPwmMotor = 8;
const int potenciaMotorMaxima = 255;
const int potenciaMotorMinimaGiro = 45;
const int potenciaMotorArranque = 140;
const unsigned long duracionPulsoArranqueMotorMs = 300;
```

El motor tambien funciona con PWM:

- Con distancia de lleno, el motor queda apagado.
- Conforme la distancia aumenta, la potencia del motor sube de forma proporcional.
- Con distancia de vacio, el motor llega a potencia maxima.
- El comando `ON` activa el modo automatico del motor por sensor.
- El comando `OFF` apaga el motor y desactiva el modo automatico.

Como el motor no arrancaba con PWM bajo, se agrego un pulso de arranque:

```text
Si el motor esta apagado y el PWM calculado es bajo:
1. Se aplica un pulso de arranque de 140/255 durante 300 ms.
2. Despues baja al PWM calculado.
3. Si el PWM calculado es menor a 45, se usa 45 como minimo de giro.
```

Esto permite que el motor empiece a moverse sin esperar a que el PWM calculado llegue aproximadamente a 100.

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
| `tinaco/estado` | Estado de conexion, sensor, LED o motor |
| `tinaco/comando` | Comandos `ON` y `OFF` enviados hacia la ESP32 |

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

Para activar el control automatico del motor por PWM:

```bash
docker exec -it aquacontrol-mqtt mosquitto_pub \
  -h localhost \
  -p 1883 \
  -u IoTProyecto \
  -P "CONTRASEÑA_MOSQUITTO" \
  -t "tinaco/comando" \
  -m "ON"
```

Para apagar el motor y desactivar el control automatico por sensor:

```bash
docker exec -it aquacontrol-mqtt mosquitto_pub \
  -h localhost \
  -p 1883 \
  -u IoTProyecto \
  -P "CONTRASEÑA_MOSQUITTO" \
  -t "tinaco/comando" \
  -m "OFF"
```

Comando de apagado usado durante la prueba con la contrasena configurada:

```bash
docker exec -it aquacontrol-mqtt mosquitto_pub \
  -h localhost \
  -p 1883 \
  -u IoTProyecto \
  -P "HOLA" \
  -t "tinaco/comando" \
  -m "OFF"
```

Con `OFF`, el motor queda apagado aunque el sensor detecte la distancia de vacio. Para que vuelva a cambiar su potencia segun la distancia, se debe enviar `ON`.

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
Distancia: 10.24 cm | Nivel: 79.0 % | Brillo LED: 53/255 | Motor PWM: 53/255 | Motor auto: ON | Estado sensor: dentro del rango
Publicado en tinaco/distancia y tinaco/nivel
```

Al recibir comandos desde MQTT:

```text
Mensaje recibido en topic: tinaco/comando
Contenido: ON
Comando recibido: Activar PWM automatico del motor en GPIO27
```

```text
Mensaje recibido en topic: tinaco/comando
Contenido: OFF
Comando recibido: Apagar motor en GPIO27
```

## Comprobacion final

La prueba se considera correcta si:

- La ESP32 se conecta al hotspot.
- La ESP32 se conecta al broker MQTT de la Raspberry Pi.
- El Monitor Serial muestra distancia real en centimetros.
- La Raspberry recibe datos en `tinaco/distancia` y `tinaco/nivel`.
- La ESP32 recibe comandos publicados en `tinaco/comando`.
- El LED en GPIO26 cambia de brillo segun la distancia medida.
- El motor en GPIO27 cambia su potencia por PWM cuando el modo automatico esta activo.
- El comando `OFF` apaga el motor aunque el sensor detecte distancia de vacio.
- El comando `ON` reactiva el control automatico del motor por PWM.
