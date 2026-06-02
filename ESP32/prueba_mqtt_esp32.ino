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

// Usuario y contraseña MQTT configurados en Mosquitto
const char* mqtt_user = "IoTProyecto";
const char* mqtt_password = "HOLA";

// ==========================
// CLIENTES WIFI Y MQTT
// ==========================
WiFiClient espClient;
PubSubClient client(espClient);

// ==========================
// TOPICS MQTT
// ==========================
const char* topic_estado = "tinaco/estado";
const char* topic_nivel = "tinaco/nivel";
const char* topic_comando = "tinaco/comando";

// ==========================
// FUNCIÓN PARA CONECTAR WIFI
// ==========================
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

// ==========================
// CALLBACK MQTT
// Recibe mensajes desde Raspberry
// ==========================
void callback(char* topic, byte* payload, unsigned int length) {
  Serial.print("Mensaje recibido en topic: ");
  Serial.println(topic);

  String mensaje = "";

  for (unsigned int i = 0; i < length; i++) {
    mensaje += (char)payload[i];
  }

  Serial.print("Contenido: ");
  Serial.println(mensaje);

  // Ejemplo de control por comando
  if (String(topic) == topic_comando) {
    if (mensaje == "ON") {
      Serial.println("Comando recibido: Encender bomba");
      // Aquí después puedes activar un relevador o motor
    } 
    else if (mensaje == "OFF") {
      Serial.println("Comando recibido: Apagar bomba");
      // Aquí después puedes apagar el relevador o motor
    }
  }
}

// ==========================
// FUNCIÓN PARA CONECTAR MQTT
// ==========================
void conectarMQTT() {
  while (!client.connected()) {
    Serial.print("Conectando a MQTT... ");

    String clientId = "ESP32_Tinaco_";
    clientId += String(random(0xffff), HEX);

    if (client.connect(clientId.c_str(), mqtt_user, mqtt_password)) {
      Serial.println("conectado");

      client.publish(topic_estado, "ESP32 conectada al broker MQTT");

      // La ESP32 se suscribe para recibir comandos
      client.subscribe(topic_comando);

      Serial.print("Suscrito a: ");
      Serial.println(topic_comando);
    } 
    else {
      Serial.print("fallo, rc=");
      Serial.print(client.state());
      Serial.println(" | Reintentando en 5 segundos");
      delay(5000);
    }
  }
}

// ==========================
// SETUP
// ==========================
void setup() {
  Serial.begin(115200);
  delay(1000);

  conectarWiFi();

  client.setServer(mqtt_server, mqtt_port);
  client.setCallback(callback);

  conectarMQTT();
}

// ==========================
// LOOP PRINCIPAL
// ==========================
void loop() {
  if (WiFi.status() != WL_CONNECTED) {
    conectarWiFi();
  }

  if (!client.connected()) {
    conectarMQTT();
  }

  client.loop();

  // Simulación del nivel del tinaco
  int nivel = 75;

  String mensajeNivel = String(nivel);

  client.publish(topic_nivel, mensajeNivel.c_str());

  Serial.print("Publicado en ");
  Serial.print(topic_nivel);
  Serial.print(": ");
  Serial.println(mensajeNivel);

  delay(3000);
}