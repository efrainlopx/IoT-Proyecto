# Graph Report - .  (2026-06-17)

## Corpus Check
- cluster-only mode — file stats not available

## Summary
- 159 nodes · 256 edges · 15 communities (11 shown, 4 thin omitted)
- Extraction: 100% EXTRACTED · 0% INFERRED · 0% AMBIGUOUS
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `a8d8a1c4`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- [[_COMMUNITY_Community 0|Community 0]]
- [[_COMMUNITY_Community 1|Community 1]]
- [[_COMMUNITY_Community 2|Community 2]]
- [[_COMMUNITY_Community 4|Community 4]]
- [[_COMMUNITY_Community 5|Community 5]]
- [[_COMMUNITY_Community 6|Community 6]]
- [[_COMMUNITY_Community 7|Community 7]]
- [[_COMMUNITY_Community 8|Community 8]]
- [[_COMMUNITY_Community 12|Community 12]]
- [[_COMMUNITY_Community 13|Community 13]]
- [[_COMMUNITY_Community 14|Community 14]]
- [[_COMMUNITY_Community 15|Community 15]]

## God Nodes (most connected - your core abstractions)
1. `DashboardActivity` - 33 edges
2. `String` - 14 edges
3. `Prueba ESP32 con sensor ultrasonico e integracion MQTT` - 14 edges
4. `AquaMqttClient` - 11 edges
5. `String` - 11 edges
6. `Arquitectura de prueba` - 11 edges
7. `MainActivity` - 10 edges
8. `AquaControl IoT` - 9 edges
9. `AuthDatabaseHelper` - 8 edges
10. `Listener` - 8 edges

## Surprising Connections (you probably didn't know these)
- `DashboardActivity` --references--> `AquaMqttClient`  [EXTRACTED]
  App/app/src/main/java/com/example/aqua_control/DashboardActivity.kt → App/app/src/main/java/com/example/aqua_control/mqtt/AquaMqttClient.kt

## Import Cycles
- None detected.

## Communities (15 total, 4 thin omitted)

### Community 0 - "Community 0"
Cohesion: 0.23
Nodes (7): Boolean, String, IMqttActionListener, AquaMqttClient, Listener, mqttActionListener(), MqttAsyncClient

### Community 1 - "Community 1"
Cohesion: 0.15
Nodes (14): Activity, Boolean, Bundle, EditText, Float, Int, String, TextView (+6 more)

### Community 2 - "Community 2"
Cohesion: 0.20
Nodes (9): android, Boolean, Bundle, EditText, String, TextView, MainActivity, AuthDatabaseHelper (+1 more)

### Community 4 - "Community 4"
Cohesion: 0.11
Nodes (17): 1. Entrar a la carpeta del proyecto, 1. Prueba ESP32 con Raspberry Pi mediante MQTT, 2. Crear el usuario MQTT, 2. Prueba ESP32 con sensor ultrasonico, 3. Corregir permisos del archivo de contraseña, 3. Integracion ESP32 + sensor ultrasonico + MQTT, 4. Levantar el broker MQTT, 5. Verificar que el contenedor esté activo (+9 more)

### Community 5 - "Community 5"
Cohesion: 0.21
Nodes (10): Boolean, Int, String, AuthResult, AuthDatabaseHelper, AuthResult, Invalid, Success (+2 more)

### Community 8 - "Community 8"
Cohesion: 0.12
Nodes (16): Archivo utilizado en la ESP32, Arquitectura de prueba, Comprobacion final, Envio de comandos desde Raspberry Pi, LED indicador en GPIO26, Medidas del tinaco simulado, Modulo del motor en GPIO27, Objetivo (+8 more)

### Community 12 - "Community 12"
Cohesion: 0.13
Nodes (14): 10. Tópicos MQTT utilizados, 11. Comprobación final, 1. Conectar Raspberry Pi y ESP32 a la misma red, 2. Verificar la IP de la Raspberry Pi, 3. Verificar que Mosquitto esté activo, 4. Escuchar mensajes MQTT desde la Raspberry Pi, 5. Configurar la ESP32 en Arduino IDE, 6. Código de prueba para la ESP32 (+6 more)

### Community 13 - "Community 13"
Cohesion: 0.29
Nodes (4): Float, Canvas, TankLevelView, View

## Knowledge Gaps
- **63 isolated node(s):** `EditText`, `RadioButton`, `TextView`, `TankLevelView`, `Button` (+58 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **4 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `DashboardActivity` connect `Community 1` to `Community 0`?**
  _High betweenness centrality (0.089) - this node is a cross-community bridge._
- **Why does `AquaMqttClient` connect `Community 0` to `Community 1`?**
  _High betweenness centrality (0.061) - this node is a cross-community bridge._
- **What connects `EditText`, `RadioButton`, `TextView` to the rest of the system?**
  _63 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Community 1` be split into smaller, more focused modules?**
  _Cohesion score 0.14793741109530584 - nodes in this community are weakly interconnected._
- **Should `Community 4` be split into smaller, more focused modules?**
  _Cohesion score 0.1111111111111111 - nodes in this community are weakly interconnected._
- **Should `Community 8` be split into smaller, more focused modules?**
  _Cohesion score 0.11764705882352941 - nodes in this community are weakly interconnected._
- **Should `Community 12` be split into smaller, more focused modules?**
  _Cohesion score 0.13333333333333333 - nodes in this community are weakly interconnected._