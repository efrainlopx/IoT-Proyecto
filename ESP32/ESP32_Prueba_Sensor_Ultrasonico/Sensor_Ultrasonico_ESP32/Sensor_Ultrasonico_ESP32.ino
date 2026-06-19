#include <WiFi.h>
#include <WebServer.h>
#include <Preferences.h>
#include <PubSubClient.h>

// ==========================
// PORTAL DE CONFIGURACION ESP32
// ==========================
const char* setup_ap_ssid = "AquaControl-Setup";
const char* setup_ap_password = "aquapi123";
IPAddress setup_ap_ip(192, 168, 4, 1);
IPAddress setup_ap_gateway(192, 168, 4, 1);
IPAddress setup_ap_subnet(255, 255, 255, 0);

// ==========================
// MQTT POR DEFECTO
// ==========================
const char* mqtt_user_default = "IoTProyecto";
const char* mqtt_password_default = "HOLA";
const int mqtt_port_default = 1883;

// ==========================
// PINES DE HARDWARE
// ==========================
const int PIN_TRIG = 5;
const int PIN_ECHO = 18;
const int PIN_LED = 26;
const int PIN_MOTOR = 27;
const int PIN_BOTON_SETUP = 0; // Boton BOOT en la mayoria de placas ESP32.

// ==========================
// MEDIDAS DEL TINACO SIMULADO
// ==========================
const float distanciaVacio = 30.0;
const float distanciaLleno = 5.0;
const float distanciaMinSensor = 2.5;
const float distanciaMaxSensor = 30.0;
const float nivelMinimoMotorAlto = 20.0;
const unsigned long timeoutUltrasonicoUs = 30000;
const unsigned long intervaloPublicacionMs = 3000;
const unsigned long intervaloSerialLlenoMs = 60000;
const unsigned long tiempoMaximoConexionWifiMs = 20000;
const unsigned long intervaloReintentoMqttMs = 5000;
const unsigned long tiempoPresionSetupMs = 2500;
const int maxIntentosMqttFallidosSetup = 6;
const int frecuenciaPwmLed = 5000;
const int resolucionPwmLed = 8;
const int brilloLedMaximo = 255;
const int frecuenciaPwmMotor = 5000;
const int resolucionPwmMotor = 8;
const int potenciaMotorMaxima = 255;
const int potenciaMotorMinimaGiro = 45;
const int potenciaMotorArranque = 140;
const unsigned long duracionPulsoArranqueMotorMs = 300;

// ==========================
// TOPICS MQTT
// ==========================
const char* topic_distancia = "tinaco/distancia";
const char* topic_nivel = "tinaco/nivel";
const char* topic_estado = "tinaco/estado";
const char* topic_comando = "tinaco/comando";

WiFiClient espClient;
PubSubClient client(espClient);
WebServer setupServer(80);
Preferences preferences;

String wifiSsid = "";
String wifiPassword = "";
String mqttHost = "";
String mqttUser = mqtt_user_default;
String mqttPassword = mqtt_password_default;
int mqttPort = mqtt_port_default;

unsigned long ultimoEnvio = 0;
unsigned long ultimoReporteSerial = 0;
unsigned long ultimoIntentoMqtt = 0;
int intentosMqttFallidos = 0;
bool setupPortalActivo = false;
bool ultimoEstadoLlenoConLedApagado = false;
bool modoSeguroActivo = false;
int brilloLedActual = 0;
bool controlMotorPorSensorActivo = false;
int potenciaMotorActual = 0;

void aplicarPotenciaMotor(int potencia);
void activarModoSeguro(const char* motivo);
void iniciarPortalConfiguracion(const char* motivo);

void cargarConfiguracion() {
  preferences.begin("aquacontrol", false);
  wifiSsid = preferences.getString("wifi_ssid", "");
  wifiPassword = preferences.getString("wifi_pass", "");
  mqttHost = preferences.getString("mqtt_host", "");
  mqttPort = preferences.getInt("mqtt_port", mqtt_port_default);
  mqttUser = preferences.getString("mqtt_user", mqtt_user_default);
  mqttPassword = preferences.getString("mqtt_pass", mqtt_password_default);
}

bool configuracionCompleta() {
  return wifiSsid.length() > 0 && mqttHost.length() > 0;
}

String normalizarArg(const String& nombre) {
  String valor = setupServer.arg(nombre);
  valor.trim();
  return valor;
}

void guardarConfiguracionDesdePortal() {
  wifiSsid = normalizarArg("ssid");
  wifiPassword = setupServer.arg("password");
  mqttHost = normalizarArg("mqtt_host");
  mqttPort = normalizarArg("mqtt_port").toInt();
  mqttUser = normalizarArg("mqtt_user");
  mqttPassword = setupServer.arg("mqtt_password");

  if (mqttPort <= 0) {
    mqttPort = mqtt_port_default;
  }
  if (mqttUser.length() == 0) {
    mqttUser = mqtt_user_default;
  }
  if (mqttPassword.length() == 0) {
    mqttPassword = mqtt_password_default;
  }

  preferences.putString("wifi_ssid", wifiSsid);
  preferences.putString("wifi_pass", wifiPassword);
  preferences.putString("mqtt_host", mqttHost);
  preferences.putInt("mqtt_port", mqttPort);
  preferences.putString("mqtt_user", mqttUser);
  preferences.putString("mqtt_pass", mqttPassword);
}

void borrarConfiguracion() {
  preferences.clear();
  wifiSsid = "";
  wifiPassword = "";
  mqttHost = "";
  mqttPort = mqtt_port_default;
  mqttUser = mqtt_user_default;
  mqttPassword = mqtt_password_default;
}

bool botonSetupPresionadoAlArrancar() {
  if (digitalRead(PIN_BOTON_SETUP) != LOW) {
    return false;
  }

  Serial.println("Boton BOOT detectado. Mantenlo presionado para borrar configuracion...");
  unsigned long inicio = millis();

  while (millis() - inicio < tiempoPresionSetupMs) {
    if (digitalRead(PIN_BOTON_SETUP) != LOW) {
      Serial.println("BOOT liberado antes de tiempo. Se conserva la configuracion.");
      return false;
    }
    delay(50);
  }

  Serial.println("Configuracion borrada por boton BOOT.");
  borrarConfiguracion();
  return true;
}

void responderPortalInicio() {
  String html = "<!doctype html><html><head><meta charset='utf-8'><title>AquaControl Setup</title></head>";
  html += "<body style='font-family:sans-serif;padding:24px'>";
  html += "<h1>AquaControl ESP32</h1>";
  html += "<p>Usa la app para enviar SSID, contrasena WiFi y broker MQTT.</p>";
  html += "<p>Endpoint: POST /config</p>";
  html += "<p>AP: ";
  html += setup_ap_ssid;
  html += " | IP: ";
  html += WiFi.softAPIP().toString();
  html += "</p></body></html>";
  setupServer.send(200, "text/html", html);
}

void responderEstadoPortal() {
  String estado = "{";
  estado += "\"setup_portal\":" + String(setupPortalActivo ? "true" : "false") + ",";
  estado += "\"configured\":" + String(configuracionCompleta() ? "true" : "false") + ",";
  estado += "\"wifi_status\":" + String(WiFi.status()) + ",";
  estado += "\"wifi_ssid\":\"" + wifiSsid + "\",";
  estado += "\"mqtt_host\":\"" + mqttHost + "\",";
  estado += "\"ip\":\"" + WiFi.localIP().toString() + "\"";
  estado += "}";
  setupServer.send(200, "application/json", estado);
}

void recibirConfiguracionPortal() {
  String ssidRecibido = normalizarArg("ssid");
  String brokerRecibido = normalizarArg("mqtt_host");

  if (ssidRecibido.length() == 0 || brokerRecibido.length() == 0) {
    setupServer.send(400, "text/plain", "Faltan ssid o mqtt_host");
    return;
  }

  guardarConfiguracionDesdePortal();
  setupServer.send(200, "text/plain", "Configuracion guardada. El ESP32 se reiniciara.");
  delay(1000);
  ESP.restart();
}

void reiniciarConfiguracionPortal() {
  borrarConfiguracion();
  setupServer.send(200, "text/plain", "Configuracion borrada. El ESP32 se reiniciara en modo setup.");
  delay(1000);
  ESP.restart();
}

void iniciarPortalConfiguracion(const char* motivo) {
  if (setupPortalActivo) {
    return;
  }

  aplicarPotenciaMotor(0);
  modoSeguroActivo = true;
  setupPortalActivo = true;

  WiFi.disconnect();
  WiFi.mode(WIFI_AP);
  WiFi.softAPConfig(setup_ap_ip, setup_ap_gateway, setup_ap_subnet);
  WiFi.softAP(setup_ap_ssid, setup_ap_password);

  setupServer.on("/", HTTP_GET, responderPortalInicio);
  setupServer.on("/status", HTTP_GET, responderEstadoPortal);
  setupServer.on("/config", HTTP_POST, recibirConfiguracionPortal);
  setupServer.on("/reset", HTTP_POST, reiniciarConfiguracionPortal);
  setupServer.begin();

  Serial.println();
  Serial.println("Portal de configuracion activo");
  Serial.print("Motivo: ");
  Serial.println(motivo);
  Serial.print("SSID setup: ");
  Serial.println(setup_ap_ssid);
  Serial.print("Password setup: ");
  Serial.println(setup_ap_password);
  Serial.print("IP setup: ");
  Serial.println(WiFi.softAPIP());
}

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

int calcularBrilloLed(float distanciaActual) {
  if (distanciaActual <= distanciaLleno) {
    return 0;
  }

  if (distanciaActual >= distanciaVacio) {
    return brilloLedMaximo;
  }

  float proporcion = (distanciaActual - distanciaLleno) / (distanciaVacio - distanciaLleno);
  return (int)(proporcion * brilloLedMaximo + 0.5);
}

int calcularPotenciaMotor(float distanciaActual) {
  if (distanciaActual <= distanciaLleno) {
    return 0;
  }

  if (distanciaActual >= distanciaVacio) {
    return potenciaMotorMaxima;
  }

  float proporcion = (distanciaActual - distanciaLleno) / (distanciaVacio - distanciaLleno);
  return (int)(proporcion * potenciaMotorMaxima + 0.5);
}

void aplicarBrilloLed(int brillo) {
  brilloLedActual = brillo;
  analogWrite(PIN_LED, brilloLedActual);
}

void aplicarPotenciaMotor(int potencia) {
  potenciaMotorActual = potencia;
  analogWrite(PIN_MOTOR, potenciaMotorActual);
}

void activarModoSeguro(const char* motivo) {
  controlMotorPorSensorActivo = false;
  aplicarPotenciaMotor(0);
  modoSeguroActivo = true;

  Serial.print("Modo seguro activo: ");
  Serial.println(motivo);

  if (client.connected()) {
    client.publish(topic_estado, motivo);
  }
}

void aplicarPotenciaMotorConArranque(int potenciaObjetivo) {
  if (potenciaObjetivo <= 0) {
    aplicarPotenciaMotor(0);
    return;
  }

  int potenciaAjustada = potenciaObjetivo;

  if (potenciaAjustada < potenciaMotorMinimaGiro) {
    potenciaAjustada = potenciaMotorMinimaGiro;
  }

  if (potenciaMotorActual == 0 && potenciaAjustada < potenciaMotorArranque) {
    analogWrite(PIN_MOTOR, potenciaMotorArranque);
    delay(duracionPulsoArranqueMotorMs);
  }

  aplicarPotenciaMotor(potenciaAjustada);
}

bool debeReportarSerial(bool tinacoLlenoConLedApagado) {
  unsigned long ahora = millis();

  if (!tinacoLlenoConLedApagado) {
    ultimoEstadoLlenoConLedApagado = false;
    ultimoReporteSerial = ahora;
    return true;
  }

  if (!ultimoEstadoLlenoConLedApagado) {
    ultimoEstadoLlenoConLedApagado = true;
    ultimoReporteSerial = ahora;
    return true;
  }

  if (ultimoReporteSerial == 0 || ahora - ultimoReporteSerial >= intervaloSerialLlenoMs) {
    ultimoReporteSerial = ahora;
    return true;
  }

  return false;
}

bool conectarWiFi() {
  if (!configuracionCompleta()) {
    return false;
  }

  Serial.println();
  Serial.print("Conectando al WiFi: ");
  Serial.println(wifiSsid);

  activarModoSeguro("modo_seguro_wifi_desconectado");

  WiFi.mode(WIFI_STA);
  WiFi.begin(wifiSsid.c_str(), wifiPassword.c_str());

  unsigned long inicio = millis();
  while (WiFi.status() != WL_CONNECTED && millis() - inicio < tiempoMaximoConexionWifiMs) {
    delay(500);
    Serial.print(".");
  }

  if (WiFi.status() != WL_CONNECTED) {
    Serial.println();
    Serial.println("No se pudo conectar al WiFi configurado");
    return false;
  }

  Serial.println();
  Serial.println("WiFi conectado correctamente");
  Serial.print("IP de la ESP32: ");
  Serial.println(WiFi.localIP());
  Serial.print("Broker MQTT: ");
  Serial.print(mqttHost);
  Serial.print(":");
  Serial.println(mqttPort);
  return true;
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
      controlMotorPorSensorActivo = true;
      modoSeguroActivo = false;
      Serial.println("Comando recibido: Activar PWM automatico del motor en GPIO27");
      client.publish(topic_estado, "motor_pwm_auto_on");
    } else if (mensaje == "OFF") {
      controlMotorPorSensorActivo = false;
      modoSeguroActivo = false;
      aplicarPotenciaMotor(0);
      Serial.println("Comando recibido: Apagar motor en GPIO27");
      client.publish(topic_estado, "motor_off");
    } else if (mensaje == "RESET_CONFIG") {
      client.publish(topic_estado, "config_reset_restart");
      delay(500);
      borrarConfiguracion();
      ESP.restart();
    } else {
      Serial.println("Comando no reconocido");
    }
  }
}

void conectarMQTT(bool forzar = false) {
  if (setupPortalActivo || WiFi.status() != WL_CONNECTED) {
    return;
  }
  if (!forzar && millis() - ultimoIntentoMqtt < intervaloReintentoMqttMs) {
    return;
  }

  ultimoIntentoMqtt = millis();
  activarModoSeguro("modo_seguro_mqtt_desconectado");
  Serial.print("Conectando a MQTT... ");

  String clientId = "ESP32_Tinaco_";
  clientId += String(random(0xffff), HEX);

  bool conectado = client.connect(
    clientId.c_str(),
    mqttUser.c_str(),
    mqttPassword.c_str(),
    topic_estado,
    0,
    true,
    "offline");

  if (conectado) {
    modoSeguroActivo = false;
    intentosMqttFallidos = 0;
    Serial.println("conectado");

    client.publish(topic_estado, "online", true);
    client.subscribe(topic_comando);

    Serial.print("Suscrito a: ");
    Serial.println(topic_comando);
  } else {
    intentosMqttFallidos++;
    Serial.print("fallo, rc=");
    Serial.println(client.state());

    if (intentosMqttFallidos >= maxIntentosMqttFallidosSetup) {
      Serial.println("MQTT no conecta. Abriendo portal para reconfigurar broker.");
      iniciarPortalConfiguracion("mqtt_no_conecta_reconfigurar_broker");
    }
  }
}

void publicarLecturas() {
  if (!client.connected()) {
    return;
  }

  float distancia = medirPromedioCm(5);

  if (distancia < 0) {
    Serial.println("Sin lectura: revisa cableado, alimentacion o posicion del sensor");
    activarModoSeguro("modo_seguro_sensor_sin_lectura");
    client.publish(topic_distancia, "error");
    return;
  }

  float nivel = calcularNivelPorcentaje(distancia);
  int brilloCalculado = calcularBrilloLed(distancia);
  bool distanciaFueraDeRango = distancia < distanciaMinSensor || distancia > distanciaMaxSensor;

  if (distanciaFueraDeRango) {
    aplicarBrilloLed(brilloCalculado);
    activarModoSeguro("modo_seguro_distancia_fuera_de_rango");

    String mensajeDistancia = String(distancia, 2);
    String mensajeNivel = String(nivel, 1);

    client.publish(topic_distancia, mensajeDistancia.c_str());
    client.publish(topic_nivel, mensajeNivel.c_str());

    Serial.print("Distancia fuera de rango: ");
    Serial.print(distancia, 2);
    Serial.println(" cm | Motor apagado por seguridad");
    return;
  }

  int potenciaMotorCalculada = 0;

  if (controlMotorPorSensorActivo) {
    if (nivel <= nivelMinimoMotorAlto) {
      potenciaMotorCalculada = potenciaMotorMaxima;
    } else {
      potenciaMotorCalculada = calcularPotenciaMotor(distancia);
    }
  }

  aplicarBrilloLed(brilloCalculado);
  aplicarPotenciaMotorConArranque(potenciaMotorCalculada);

  String mensajeDistancia = String(distancia, 2);
  String mensajeNivel = String(nivel, 1);

  client.publish(topic_distancia, mensajeDistancia.c_str());
  client.publish(topic_nivel, mensajeNivel.c_str());

  bool tinacoLlenoConLedApagado = distancia <= distanciaLleno && brilloLedActual == 0;

  if (!debeReportarSerial(tinacoLlenoConLedApagado)) {
    return;
  }

  Serial.print("Distancia: ");
  Serial.print(mensajeDistancia);
  Serial.print(" cm | Nivel: ");
  Serial.print(mensajeNivel);
  Serial.print(" % | Brillo LED: ");
  Serial.print(brilloLedActual);
  Serial.print("/255 | Motor PWM: ");
  Serial.print(potenciaMotorActual);
  Serial.print("/255 | Motor auto: ");
  Serial.print(controlMotorPorSensorActivo ? "ON" : "OFF");
  Serial.print(" | Estado sensor: ");

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

void configurarHardware() {
  pinMode(PIN_TRIG, OUTPUT);
  pinMode(PIN_ECHO, INPUT);
  pinMode(PIN_LED, OUTPUT);
  pinMode(PIN_MOTOR, OUTPUT);
  pinMode(PIN_BOTON_SETUP, INPUT_PULLUP);
  analogWriteResolution(PIN_LED, resolucionPwmLed);
  analogWriteFrequency(PIN_LED, frecuenciaPwmLed);
  analogWriteResolution(PIN_MOTOR, resolucionPwmMotor);
  analogWriteFrequency(PIN_MOTOR, frecuenciaPwmMotor);

  digitalWrite(PIN_TRIG, LOW);
  aplicarBrilloLed(0);
  aplicarPotenciaMotor(0);
}

void imprimirArranque() {
  Serial.println("ESP32 + Sensor ultrasonico + MQTT");
  Serial.println("---------------------------------");
  Serial.println("Provisionamiento WiFi por AquaControl-Setup");
  Serial.println("PWM del LED configurado en GPIO26");
  Serial.println("Motor configurado en GPIO27");
  Serial.print("Distancia vacio: ");
  Serial.print(distanciaVacio);
  Serial.println(" cm");
  Serial.print("Distancia lleno: ");
  Serial.print(distanciaLleno);
  Serial.println(" cm");
}

void setup() {
  Serial.begin(115200);
  delay(1000);

  configurarHardware();
  imprimirArranque();
  cargarConfiguracion();

  if (botonSetupPresionadoAlArrancar()) {
    iniciarPortalConfiguracion("configuracion_borrada_por_boton_boot");
    return;
  }

  if (!configuracionCompleta()) {
    iniciarPortalConfiguracion("sin_configuracion_guardada");
    return;
  }

  if (!conectarWiFi()) {
    iniciarPortalConfiguracion("wifi_configurado_no_disponible");
    return;
  }

  client.setServer(mqttHost.c_str(), mqttPort);
  client.setCallback(callback);
  conectarMQTT(true);
}

void loop() {
  if (setupPortalActivo) {
    setupServer.handleClient();
    delay(2);
    return;
  }

  if (WiFi.status() != WL_CONNECTED) {
    activarModoSeguro("modo_seguro_wifi_desconectado");
    if (!conectarWiFi()) {
      iniciarPortalConfiguracion("wifi_desconectado_no_reconecta");
    }
    return;
  }

  if (!client.connected()) {
    conectarMQTT();
    return;
  }

  client.loop();

  if (millis() - ultimoEnvio >= intervaloPublicacionMs) {
    ultimoEnvio = millis();
    publicarLecturas();
  }
}
