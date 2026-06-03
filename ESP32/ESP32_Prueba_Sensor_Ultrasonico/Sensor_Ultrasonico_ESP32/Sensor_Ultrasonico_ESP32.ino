#include <WiFi.h>
#include <PubSubClient.h>

// ==========================
// DATOS DEL HOTSPOT
// ==========================
const char* ssid = "prueba";
const char* password = "holahola";

// ==========================
// DATOS DEL BROKER MQTT
// Raspberry Pi en hotspot
// ==========================
const char* mqtt_server = "10.152.254.168";
const int mqtt_port = 1883;
const char* mqtt_user = "IoTProyecto";
const char* mqtt_password = "HOLA";

// ==========================
// PINES DEL SENSOR ULTRASONICO
// ==========================
const int PIN_TRIG = 5;
const int PIN_ECHO = 18;

// ==========================
// MEDIDAS DEL TINACO SIMULADO
// ==========================
const float distanciaVacio = 30.0;
const float distanciaLleno = 5.0;
const float distanciaMinSensor = 2.5;
const float distanciaMaxSensor = 30.0;
const unsigned long timeoutUltrasonicoUs = 30000;
const unsigned long intervaloPublicacionMs = 3000;

// ==========================
// TOPICS MQTT
// ==========================
const char* topic_distancia = "tinaco/distancia";
const char* topic_nivel = "tinaco/nivel";
const char* topic_estado = "tinaco/estado";
const char* topic_comando = "tinaco/comando";

WiFiClient espClient;
PubSubClient client(espClient);

unsigned long ultimoEnvio = 0;

float medirDistanciaCm() {
  digitalWrite(PIN_TRIG, LOW);
  delayMicroseconds(2);

  digitalWrite(PIN_TRIG, HIGH);
  delayMicroseconds(10);
  digitalWrite(PIN_TRIG, LOW);

  unsigned long duracion = pulseIn(PIN_ECHO, HIGH, timeoutUltrasonicoUs);

  if (duracion == 0) {
    return -1.0;
  }

  return duracion * 0.0343 / 2.0;
}

float medirPromedioCm(int muestras) {
  float suma = 0.0;
  int lecturasValidas = 0;

  for (int i = 0; i < muestras; i++) {
    float distancia = medirDistanciaCm();

    if (distancia > 0) {
      suma += distancia;
      lecturasValidas++;
    }

    delay(60);
  }

  if (lecturasValidas == 0) {
    return -1.0;
  }

  return suma / lecturasValidas;
}

float calcularNivelPorcentaje(float distanciaActual) {
  float nivel = ((distanciaVacio - distanciaActual) / (distanciaVacio - distanciaLleno)) * 100.0;

  if (nivel < 0.0) {
    nivel = 0.0;
  } else if (nivel > 100.0) {
    nivel = 100.0;
  }

  return nivel;
}

void conectarWiFi() {
  Serial.println();
  Serial.print("Conectando al WiFi: ");
  Serial.println(ssid);

  WiFi.mode(WIFI_STA);
  WiFi.begin(ssid, password);

  while (WiFi.status() != WL_CONNECTED) {
    delay(500);
    Serial.print(".");
  }

  Serial.println();
  Serial.println("WiFi conectado correctamente");
  Serial.print("IP de la ESP32: ");
  Serial.println(WiFi.localIP());
}

void callback(char* topic, byte* payload, unsigned int length) {
  Serial.print("Mensaje recibido en topic: ");
  Serial.println(topic);

  String mensaje = "";

  for (unsigned int i = 0; i < length; i++) {
    mensaje += (char)payload[i];
  }

  mensaje.trim();

  Serial.print("Contenido: ");
  Serial.println(mensaje);

  if (String(topic) == topic_comando) {
    if (mensaje == "ON") {
      Serial.println("Comando recibido: Encender bomba");
      // Aqui despues puedes activar un relevador o motor.
    } else if (mensaje == "OFF") {
      Serial.println("Comando recibido: Apagar bomba");
      // Aqui despues puedes apagar el relevador o motor.
    } else {
      Serial.println("Comando no reconocido");
    }
  }
}

void conectarMQTT() {
  while (!client.connected()) {
    Serial.print("Conectando a MQTT... ");

    String clientId = "ESP32_Tinaco_";
    clientId += String(random(0xffff), HEX);

    bool conectado = client.connect(
      clientId.c_str(),
      mqtt_user,
      mqtt_password,
      topic_estado,
      0,
      true,
      "offline"
    );

    if (conectado) {
      Serial.println("conectado");

      client.publish(topic_estado, "online", true);
      client.subscribe(topic_comando);

      Serial.print("Suscrito a: ");
      Serial.println(topic_comando);
    } else {
      Serial.print("fallo, rc=");
      Serial.print(client.state());
      Serial.println(" | Reintentando en 5 segundos");
      delay(5000);
    }
  }
}

void publicarLecturas() {
  float distancia = medirPromedioCm(5);

  if (distancia < 0) {
    Serial.println("Sin lectura: revisa cableado, alimentacion o posicion del sensor");
    client.publish(topic_estado, "sin_lectura_sensor");
    return;
  }

  float nivel = calcularNivelPorcentaje(distancia);

  String mensajeDistancia = String(distancia, 2);
  String mensajeNivel = String(nivel, 1);

  client.publish(topic_distancia, mensajeDistancia.c_str());
  client.publish(topic_nivel, mensajeNivel.c_str());

  Serial.print("Distancia: ");
  Serial.print(mensajeDistancia);
  Serial.print(" cm | Nivel: ");
  Serial.print(mensajeNivel);
  Serial.print(" % | Estado sensor: ");

  if (distancia < distanciaMinSensor) {
    Serial.println("fuera de rango, demasiado cerca");
  } else if (distancia > distanciaMaxSensor) {
    Serial.println("fuera de rango, demasiado lejos");
  } else {
    Serial.println("dentro del rango");
  }

  Serial.print("Publicado en ");
  Serial.print(topic_distancia);
  Serial.print(" y ");
  Serial.println(topic_nivel);
}

void setup() {
  Serial.begin(115200);
  delay(1000);

  pinMode(PIN_TRIG, OUTPUT);
  pinMode(PIN_ECHO, INPUT);
  digitalWrite(PIN_TRIG, LOW);

  Serial.println("ESP32 + Sensor ultrasonico + MQTT");
  Serial.println("---------------------------------");
  Serial.print("Distancia vacio: ");
  Serial.print(distanciaVacio);
  Serial.println(" cm");
  Serial.print("Distancia lleno: ");
  Serial.print(distanciaLleno);
  Serial.println(" cm");

  conectarWiFi();

  client.setServer(mqtt_server, mqtt_port);
  client.setCallback(callback);

  conectarMQTT();
}

void loop() {
  if (WiFi.status() != WL_CONNECTED) {
    conectarWiFi();
  }

  if (!client.connected()) {
    conectarMQTT();
  }

  client.loop();

  if (millis() - ultimoEnvio >= intervaloPublicacionMs) {
    ultimoEnvio = millis();
    publicarLecturas();
  }
}
