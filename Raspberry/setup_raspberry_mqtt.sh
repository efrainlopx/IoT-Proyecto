#!/usr/bin/env bash
set -euo pipefail

HOSTNAME_TARGET="${HOSTNAME_TARGET:-aqua-pi}"
MQTT_USER="${MQTT_USER:-IoTProyecto}"
MQTT_PASSWORD="${MQTT_PASSWORD:-HOLA}"
PROJECT_DIR="${PROJECT_DIR:-$HOME/IoT-Proyecto}"

if [[ ! -d "$PROJECT_DIR" ]]; then
  echo "No existe PROJECT_DIR=$PROJECT_DIR"
  echo "Clona o copia el proyecto en la Raspberry antes de correr este script."
  exit 1
fi

echo "Configurando hostname: $HOSTNAME_TARGET"
sudo hostnamectl set-hostname "$HOSTNAME_TARGET"

echo "Instalando dependencias base"
sudo apt update
sudo apt install -y docker.io avahi-daemon mosquitto-clients
sudo systemctl enable --now docker avahi-daemon

if ! docker compose version >/dev/null 2>&1; then
  echo "Docker Compose no esta disponible como 'docker compose'. Instala el plugin de compose para tu distribucion."
  exit 1
fi

cd "$PROJECT_DIR"
mkdir -p mosquitto/config mosquitto/data mosquitto/log

if [[ ! -f mosquitto/config/password.txt ]]; then
  echo "Creando usuario MQTT $MQTT_USER"
  docker run --rm \
    -v "$PWD/mosquitto/config:/mosquitto/config" \
    eclipse-mosquitto:2 \
    mosquitto_passwd -c -b /mosquitto/config/password.txt "$MQTT_USER" "$MQTT_PASSWORD"
else
  echo "Ya existe mosquitto/config/password.txt; no se sobrescribe."
fi

sudo chown 1883:1883 mosquitto/config/password.txt
sudo chmod 600 mosquitto/config/password.txt

echo "Levantando Mosquitto"
docker compose up -d

echo "Estado del contenedor"
docker compose ps

echo "Direcciones de la Raspberry"
hostname -I

echo "Prueba local MQTT"
mosquitto_pub -h 127.0.0.1 -u "$MQTT_USER" -P "$MQTT_PASSWORD" -t tinaco/estado -m raspberry_mqtt_ready

echo "Listo. Desde la app puedes usar $HOSTNAME_TARGET.local o la IP Ethernet de la Raspberry."
