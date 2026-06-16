# Graph Report - /tmp/iot-graphify-code  (2026-06-15)

## Corpus Check
- cluster-only mode — file stats not available

## Summary
- 57 nodes · 111 edges · 14 communities (11 shown, 3 thin omitted)
- Extraction: 98% EXTRACTED · 2% INFERRED · 0% AMBIGUOUS · INFERRED: 2 edges (avg confidence: 0.8)
- Token cost: 0 input · 0 output

## Community Hubs (Navigation)
- [[_COMMUNITY_Community 0|Community 0]]
- [[_COMMUNITY_Community 1|Community 1]]
- [[_COMMUNITY_Community 2|Community 2]]
- [[_COMMUNITY_Community 3|Community 3]]
- [[_COMMUNITY_Community 4|Community 4]]
- [[_COMMUNITY_Community 5|Community 5]]
- [[_COMMUNITY_Community 6|Community 6]]
- [[_COMMUNITY_Community 7|Community 7]]
- [[_COMMUNITY_Community 8|Community 8]]

## God Nodes (most connected - your core abstractions)
1. `AquaControlContent()` - 12 edges
2. `AquaControlMqttController` - 12 edges
3. `AquaControlUiState` - 9 edges
4. `String` - 9 edges
5. `Color` - 8 edges
6. `LevelCard()` - 7 edges
7. `AquaControlScreen()` - 6 edges
8. `StatCard()` - 6 edges
9. `WaterGauge()` - 5 edges
10. `TelemetryCards()` - 5 edges

## Surprising Connections (you probably didn't know these)
- `AquaControlPreview()` --calls--> `AquaControlTheme()`  [INFERRED]
  App/app/src/main/java/com/example/aqua_control/MainActivity.kt → App/app/src/main/java/com/example/aqua_control/ui/theme/Theme.kt

## Import Cycles
- None detected.

## Communities (14 total, 3 thin omitted)

### Community 0 - "Community 0"
Cohesion: 0.32
Nodes (6): AquaControlMqttController, AquaControlScreen(), mqttActionListener(), IMqttActionListener, MqttAsyncClient, StateFlow

### Community 1 - "Community 1"
Cohesion: 0.46
Nodes (8): Boolean, AquaControlUiState, LevelCard(), levelColor(), ProgressBar(), WaterGauge(), Color, Float

### Community 2 - "Community 2"
Cohesion: 0.25
Nodes (6): Boolean, AquaControlPreview(), MainActivity, Bundle, ComponentActivity, AquaControlTheme()

### Community 3 - "Community 3"
Cohesion: 0.57
Nodes (7): AquaControlContent(), ConnectionCard(), Header(), MotorControlCard(), StatusPill(), AquaControlUiState, String

### Community 4 - "Community 4"
Cohesion: 0.70
Nodes (5): DistanceCard(), StatCard(), StatusCard(), TelemetryCards(), Modifier

### Community 5 - "Community 5"
Cohesion: 0.50
Nodes (3): ConnectionStatus, MotorCommand, TopicCard()

## Knowledge Gaps
- **6 isolated node(s):** `Bundle`, `StateFlow`, `ConnectionStatus`, `MotorCommand`, `IMqttActionListener` (+1 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **3 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `AquaControlMqttController` connect `Community 0` to `Community 8`, `Community 3`, `Community 5`?**
  _High betweenness centrality (0.130) - this node is a cross-community bridge._
- **Why does `AquaControlPreview()` connect `Community 2` to `Community 1`, `Community 3`, `Community 5`?**
  _High betweenness centrality (0.062) - this node is a cross-community bridge._
- **Why does `AquaControlScreen()` connect `Community 0` to `Community 2`, `Community 3`, `Community 5`?**
  _High betweenness centrality (0.061) - this node is a cross-community bridge._
- **What connects `Bundle`, `StateFlow`, `ConnectionStatus` to the rest of the system?**
  _6 weakly-connected nodes found - possible documentation gaps or missing edges._