#!/usr/bin/env python3
"""Build an enriched project graph from Graphify output plus docs/config files.

Graphify already captured Kotlin/Gradle symbols in graphify-out/graph.json.
This script adds the project context that Graphify missed: Markdown docs,
Android XML resources, ESP32 sketches, Docker Compose, and Mosquitto config.
"""

from __future__ import annotations

import html
import json
import math
import re
import xml.etree.ElementTree as ET
from collections import Counter, defaultdict
from pathlib import Path
from typing import Iterable


ROOT = Path(__file__).resolve().parents[1]
OUT_DIR = ROOT / "graphify-out"
BASE_GRAPH_PATH = OUT_DIR / "graph.json"

ENRICHED_JSON = OUT_DIR / "graph_enriched.json"
ENRICHED_DOT = OUT_DIR / "graph_enriched.dot"
ENRICHED_SVG = OUT_DIR / "graph_enriched.svg"
ENRICHED_MMD = OUT_DIR / "graph_enriched.mmd"
ENRICHED_REPORT = OUT_DIR / "GRAPH_REPORT_ENRICHED.md"
ENRICHED_LABELS = OUT_DIR / ".graphify_labels_enriched.json"

ANDROID_NS = "{http://schemas.android.com/apk/res/android}"

COMMUNITIES = {
    "code_graphify": 0,
    "docs": 100,
    "android_xml": 101,
    "firmware": 102,
    "infra": 103,
    "mqtt": 104,
    "hardware": 105,
    "architecture": 106,
    "resource": 107,
}

COMMUNITY_LABELS = {
    100: "Docs and notes",
    101: "Android XML resources",
    102: "ESP32 firmware",
    103: "Docker and Mosquitto",
    104: "MQTT topics and commands",
    105: "Hardware",
    106: "Architecture",
    107: "Android resource symbols",
}

COMPONENT_PATTERNS = {
    "arch:android_app": ("Android app", r"\b(android|kotlin|aplicaci[oó]n movil|app)\b"),
    "arch:raspberry_pi": ("Raspberry Pi", r"\braspberry pi\b"),
    "arch:mosquitto_broker": ("Mosquitto MQTT broker", r"\b(mosquitto|broker mqtt)\b"),
    "arch:esp32": ("ESP32 controller", r"\besp32\b"),
    "hw:ultrasonic_sensor": ("Ultrasonic sensor", r"\b(sensor ultrasonico|ultras[oó]nico|hc-sr04)\b"),
    "hw:led_gpio26": ("LED indicator GPIO26", r"\bled\b|gpio26"),
    "hw:motor_gpio27": ("Motor pump GPIO27", r"\bmotor|bomba|gpio27|tip120\b"),
    "infra:docker": ("Docker Compose", r"\bdocker( compose)?\b"),
    "infra:arduino_ide": ("Arduino IDE", r"\barduino ide\b"),
    "lib:pubsubclient": ("PubSubClient", r"\bpubsubclient\b"),
}

TOPIC_RE = re.compile(r"\b(?:aquacontrol/)?tinaco/[A-Za-z0-9_#/-]+\b")
FILE_REF_RE = re.compile(
    r"\b(?:[A-Za-z0-9_.-]+/)+[A-Za-z0-9_.-]+"
    r"\.(?:md|txt|ino|xml|yaml|yml|conf|kt|kts|toml|keep)\b"
    r"|\b(?:README\.md|compose\.yaml)\b",
    re.IGNORECASE,
)


def rel(path: Path) -> str:
    return path.relative_to(ROOT).as_posix()


def slug(value: str) -> str:
    value = value.lower()
    value = re.sub(r"[^a-z0-9]+", "_", value)
    return value.strip("_") or "node"


def normalize_label(value: str) -> str:
    return value.strip().lower()


class GraphBuilder:
    def __init__(self, base_graph: dict) -> None:
        self.graph = base_graph
        self.graph.setdefault("nodes", [])
        self.graph.setdefault("links", [])
        self.graph.setdefault("hyperedges", [])
        self.nodes_by_id = {node["id"]: node for node in self.graph["nodes"]}
        self.link_keys = {
            (link.get("source"), link.get("target"), link.get("relation"), link.get("source_file", ""))
            for link in self.graph["links"]
        }
        self.base_node_count = len(self.graph["nodes"])
        self.base_link_count = len(self.graph["links"])
        self.resource_sources: dict[str, str] = {}
        self.resource_node_ids: dict[str, str] = {}
        self.path_node_ids: dict[str, str] = {}
        self.topic_node_ids: dict[str, str] = {}

    def add_node(
        self,
        node_id: str,
        label: str,
        *,
        file_type: str,
        source_file: str = "",
        source_location: str = "",
        origin: str = "enriched",
        community: int = COMMUNITIES["architecture"],
        **extra: object,
    ) -> str:
        if node_id in self.nodes_by_id:
            node = self.nodes_by_id[node_id]
            for key, value in extra.items():
                node.setdefault(key, value)
            return node_id

        node = {
            "label": label,
            "file_type": file_type,
            "source_file": source_file,
            "source_location": source_location,
            "_origin": origin,
            "community": community,
            "norm_label": normalize_label(label),
            "id": node_id,
        }
        node.update(extra)
        self.graph["nodes"].append(node)
        self.nodes_by_id[node_id] = node
        return node_id

    def add_link(
        self,
        source: str,
        target: str,
        relation: str,
        *,
        source_file: str = "",
        source_location: str = "",
        confidence: str = "EXTRACTED",
        weight: float = 1.0,
    ) -> None:
        key = (source, target, relation, source_file)
        if key in self.link_keys:
            return
        self.link_keys.add(key)
        self.graph["links"].append(
            {
                "relation": relation,
                "confidence": confidence,
                "source_file": source_file,
                "source_location": source_location,
                "weight": weight,
                "confidence_score": 1.0 if confidence == "EXTRACTED" else 0.8,
                "source": source,
                "target": target,
            }
        )

    def file_node(self, path: Path, file_type: str, community: int) -> str:
        rel_path = rel(path)
        node_id = f"file:{slug(rel_path)}"
        self.path_node_ids[rel_path.lower()] = node_id
        return self.add_node(
            node_id,
            rel_path,
            file_type=file_type,
            source_file=rel_path,
            source_location="L1",
            community=community,
        )

    def topic_node(self, topic: str) -> str:
        if topic in self.topic_node_ids:
            return self.topic_node_ids[topic]
        node_id = f"topic:{slug(topic)}"
        self.topic_node_ids[topic] = node_id
        self.add_node(
            node_id,
            topic,
            file_type="mqtt_topic",
            community=COMMUNITIES["mqtt"],
        )
        return node_id

    def command_node(self, command: str) -> str:
        node_id = f"mqtt_command:{slug(command)}"
        return self.add_node(
            node_id,
            command,
            file_type="mqtt_command",
            community=COMMUNITIES["mqtt"],
        )

    def component_node(self, node_id: str, label: str, *, community: int | None = None) -> str:
        if community is None:
            community = COMMUNITIES["architecture"] if node_id.startswith("arch:") else COMMUNITIES["hardware"]
        return self.add_node(node_id, label, file_type="component", community=community)

    def resource_node(self, resource: str, *, source_file: str = "") -> str:
        node_id = f"android_res:{slug(resource)}"
        self.resource_node_ids[resource] = node_id
        if source_file:
            self.resource_sources[resource] = source_file
        return self.add_node(
            node_id,
            resource,
            file_type="android_resource",
            source_file=source_file,
            source_location="L1" if source_file else "",
            community=COMMUNITIES["resource"],
        )


def read_text(path: Path) -> str:
    return path.read_text(encoding="utf-8", errors="replace")


def iter_project_files(patterns: Iterable[str]) -> list[Path]:
    found: list[Path] = []
    for pattern in patterns:
        found.extend(ROOT.glob(pattern))
    return sorted(
        path
        for path in found
        if path.is_file()
        and "build" not in path.parts
        and ".gradle" not in path.parts
        and ".git" not in path.parts
    )


def build_path_lookup() -> dict[str, str]:
    lookup: dict[str, str] = {}
    for path in ROOT.rglob("*"):
        if not path.is_file():
            continue
        parts = set(path.parts)
        if ".git" in parts or "build" in parts or ".gradle" in parts:
            continue
        rel_path = rel(path)
        lookup[rel_path.lower()] = rel_path
        lookup[path.name.lower()] = rel_path
    return lookup


def first_existing_id(builder: GraphBuilder, labels: Iterable[str]) -> str | None:
    wanted = {normalize_label(label) for label in labels}
    for node in builder.graph["nodes"]:
        if node.get("norm_label") in wanted or normalize_label(node.get("label", "")) in wanted:
            return node["id"]
    return None


def add_architecture(builder: GraphBuilder) -> None:
    android = builder.component_node("arch:android_app", "Android app")
    broker = builder.component_node("arch:mosquitto_broker", "Mosquitto MQTT broker")
    raspberry = builder.component_node("arch:raspberry_pi", "Raspberry Pi")
    esp32 = builder.component_node("arch:esp32", "ESP32 controller")
    sensor = builder.component_node("hw:ultrasonic_sensor", "Ultrasonic sensor")
    led = builder.component_node("hw:led_gpio26", "LED indicator GPIO26")
    motor = builder.component_node("hw:motor_gpio27", "Motor pump GPIO27")
    docker = builder.component_node("infra:docker", "Docker Compose", community=COMMUNITIES["infra"])

    for topic in ["tinaco/#", "tinaco/distancia", "tinaco/nivel", "tinaco/estado", "tinaco/comando"]:
        builder.topic_node(topic)

    builder.add_link(android, broker, "connects_to", confidence="INFERRED")
    builder.add_link(esp32, broker, "connects_to", confidence="INFERRED")
    builder.add_link(raspberry, broker, "hosts", confidence="INFERRED")
    builder.add_link(docker, broker, "runs", confidence="INFERRED")
    builder.add_link(esp32, sensor, "measures_with", confidence="INFERRED")
    builder.add_link(esp32, led, "controls", confidence="INFERRED")
    builder.add_link(esp32, motor, "controls", confidence="INFERRED")

    for topic in ["tinaco/distancia", "tinaco/nivel", "tinaco/estado"]:
        builder.add_link(esp32, builder.topic_node(topic), "publishes", confidence="INFERRED")
        builder.add_link(android, builder.topic_node(topic), "consumes", confidence="INFERRED")
        builder.add_link(builder.topic_node(topic), broker, "routed_by", confidence="INFERRED")
    builder.add_link(android, builder.topic_node("tinaco/comando"), "publishes", confidence="INFERRED")
    builder.add_link(esp32, builder.topic_node("tinaco/comando"), "subscribes", confidence="INFERRED")
    builder.add_link(android, builder.topic_node("tinaco/#"), "subscribes", confidence="INFERRED")

    main_activity = first_existing_id(builder, ["MainActivity", "MainActivity.kt"])
    if main_activity:
        builder.add_link(android, main_activity, "implemented_by", confidence="INFERRED")


def add_markdown_and_notes(builder: GraphBuilder) -> list[str]:
    path_lookup = build_path_lookup()
    included: list[str] = []
    docs = [
        ROOT / "README.md",
        *iter_project_files(["Docs/*.md", "Notas/*.txt"]),
    ]

    for path in docs:
        if not path.exists():
            continue
        included.append(rel(path))
        source_file = rel(path)
        text = read_text(path)
        file_type = "markdown" if path.suffix.lower() == ".md" else "text"
        file_id = builder.file_node(path, file_type, COMMUNITIES["docs"])

        for line_no, line in enumerate(text.splitlines(), start=1):
            heading = re.match(r"^(#{1,6})\s+(.+?)\s*$", line)
            if heading:
                title = heading.group(2).strip()
                heading_id = f"doc_section:{slug(source_file)}:{line_no}"
                builder.add_node(
                    heading_id,
                    title,
                    file_type="doc_section",
                    source_file=source_file,
                    source_location=f"L{line_no}",
                    community=COMMUNITIES["docs"],
                    level=len(heading.group(1)),
                )
                builder.add_link(file_id, heading_id, "contains_section", source_file=source_file, source_location=f"L{line_no}")

            for topic in TOPIC_RE.findall(line):
                topic_id = builder.topic_node(topic)
                builder.add_link(file_id, topic_id, "mentions_topic", source_file=source_file, source_location=f"L{line_no}")

            for command in re.findall(r"`(ON|OFF)`|\b(ON|OFF)\b", line):
                value = command[0] or command[1]
                command_id = builder.command_node(value)
                builder.add_link(file_id, command_id, "mentions_command", source_file=source_file, source_location=f"L{line_no}")

            for file_ref in FILE_REF_RE.findall(line):
                normalized = path_lookup.get(file_ref.lower())
                if not normalized:
                    continue
                target_path = ROOT / normalized
                if not target_path.exists():
                    continue
                target_type = {
                    ".md": "markdown",
                    ".txt": "text",
                    ".ino": "firmware",
                    ".xml": "xml",
                    ".yaml": "config",
                    ".yml": "config",
                    ".conf": "config",
                    ".kt": "code",
                    ".kts": "code",
                    ".toml": "config",
                    ".keep": "placeholder",
                }.get(target_path.suffix.lower(), "file")
                community = {
                    "markdown": COMMUNITIES["docs"],
                    "text": COMMUNITIES["docs"],
                    "firmware": COMMUNITIES["firmware"],
                    "xml": COMMUNITIES["android_xml"],
                    "config": COMMUNITIES["infra"],
                    "code": COMMUNITIES["code_graphify"],
                }.get(target_type, COMMUNITIES["architecture"])
                target_id = builder.file_node(target_path, target_type, community)
                builder.add_link(file_id, target_id, "references_file", source_file=source_file, source_location=f"L{line_no}")

        lower_text = text.lower()
        for component_id, (label, pattern) in COMPONENT_PATTERNS.items():
            if re.search(pattern, lower_text, flags=re.IGNORECASE):
                node_id = builder.component_node(
                    component_id,
                    label,
                    community=COMMUNITIES["infra"] if component_id.startswith("infra:") else None,
                )
                builder.add_link(file_id, node_id, "documents_component", source_file=source_file)

    return included


def android_attr(element: ET.Element, name: str) -> str | None:
    return element.attrib.get(f"{ANDROID_NS}{name}")


def resource_refs(value: str | None) -> list[str]:
    if not value:
        return []
    return [match.group(1) for match in re.finditer(r"@([A-Za-z0-9_./-]+)", value)]


def add_android_xml(builder: GraphBuilder) -> list[str]:
    included: list[str] = []
    xml_files = iter_project_files(["App/app/src/main/**/*.xml"])

    for path in xml_files:
        source_file = rel(path)
        included.append(source_file)
        file_id = builder.file_node(path, "xml", COMMUNITIES["android_xml"])
        try:
            root = ET.fromstring(read_text(path))
        except ET.ParseError as exc:
            error_id = f"xml_parse_error:{slug(source_file)}"
            builder.add_node(
                error_id,
                f"XML parse error: {path.name}",
                file_type="xml_error",
                source_file=source_file,
                community=COMMUNITIES["android_xml"],
                detail=str(exc),
            )
            builder.add_link(file_id, error_id, "has_parse_error", source_file=source_file)
            continue

        root_tag = root.tag.split("}")[-1]
        root_id = builder.add_node(
            f"xml_root:{slug(source_file)}",
            root_tag,
            file_type="xml_element",
            source_file=source_file,
            source_location="L1",
            community=COMMUNITIES["android_xml"],
        )
        builder.add_link(file_id, root_id, "has_root", source_file=source_file, source_location="L1")

        if path.name == "AndroidManifest.xml":
            app_id = builder.component_node("arch:android_app", "Android app")
            for permission in root.findall("uses-permission"):
                name = android_attr(permission, "name") or "unknown"
                perm_id = builder.add_node(
                    f"android_permission:{slug(name)}",
                    name,
                    file_type="android_permission",
                    source_file=source_file,
                    community=COMMUNITIES["android_xml"],
                )
                builder.add_link(file_id, perm_id, "declares_permission", source_file=source_file)
                builder.add_link(app_id, perm_id, "requires_permission", source_file=source_file)

            application = root.find("application")
            if application is not None:
                if android_attr(application, "usesCleartextTraffic") == "true":
                    cleartext_id = builder.add_node(
                        "android_network:cleartext_traffic",
                        "Cleartext TCP traffic enabled",
                        file_type="android_network_policy",
                        source_file=source_file,
                        community=COMMUNITIES["android_xml"],
                    )
                    builder.add_link(app_id, cleartext_id, "allows", source_file=source_file)

                for attr_value in application.attrib.values():
                    for resource in resource_refs(attr_value):
                        res_id = builder.resource_node(resource)
                        builder.add_link(file_id, res_id, "references_resource", source_file=source_file)

                for activity in application.findall("activity"):
                    activity_name = android_attr(activity, "name") or "activity"
                    activity_id = builder.add_node(
                        f"android_activity:{slug(activity_name)}",
                        activity_name,
                        file_type="android_activity",
                        source_file=source_file,
                        community=COMMUNITIES["android_xml"],
                    )
                    builder.add_link(app_id, activity_id, "declares_activity", source_file=source_file)
                    main_activity = first_existing_id(builder, ["MainActivity"])
                    if activity_name.endswith("MainActivity") and main_activity:
                        builder.add_link(activity_id, main_activity, "maps_to_code", source_file=source_file)
                    for attr_value in activity.attrib.values():
                        for resource in resource_refs(attr_value):
                            res_id = builder.resource_node(resource)
                            builder.add_link(activity_id, res_id, "references_resource", source_file=source_file)

        for element in root.iter():
            tag = element.tag.split("}")[-1]
            if tag in {"string", "color", "style"}:
                name = element.attrib.get("name")
                if not name:
                    continue
                resource = f"{tag}/{name}"
                res_id = builder.resource_node(resource, source_file=source_file)
                builder.add_link(file_id, res_id, "declares_resource", source_file=source_file)
                if tag == "style" and "parent" in element.attrib:
                    parent_id = builder.resource_node(f"style_parent/{element.attrib['parent']}")
                    builder.add_link(res_id, parent_id, "inherits_from", source_file=source_file)

            for attr_value in element.attrib.values():
                for resource in resource_refs(attr_value):
                    res_id = builder.resource_node(resource)
                    builder.add_link(file_id, res_id, "references_resource", source_file=source_file)

    return included


def find_function_for_line(function_lines: list[tuple[int, str]], line_no: int) -> str | None:
    current: str | None = None
    for fn_line, fn_id in function_lines:
        if fn_line <= line_no:
            current = fn_id
        else:
            break
    return current


def add_firmware(builder: GraphBuilder) -> list[str]:
    included: list[str] = []
    sketch_files = iter_project_files(["ESP32/**/*.ino"])

    for path in sketch_files:
        source_file = rel(path)
        included.append(source_file)
        text = read_text(path)
        lines = text.splitlines()
        sketch_id = builder.file_node(path, "firmware", COMMUNITIES["firmware"])
        esp32_id = builder.component_node("arch:esp32", "ESP32 controller")
        builder.add_link(esp32_id, sketch_id, "implemented_by", source_file=source_file, confidence="INFERRED")

        function_lines: list[tuple[int, str]] = []
        for line_no, line in enumerate(lines, start=1):
            match = re.match(r"\s*(?:void|float|int|bool|String)\s+([A-Za-z_][A-Za-z0-9_]*)\s*\(", line)
            if not match:
                continue
            name = match.group(1)
            function_id = f"firmware_fn:{slug(source_file)}:{slug(name)}"
            builder.add_node(
                function_id,
                f"{name}()",
                file_type="firmware_function",
                source_file=source_file,
                source_location=f"L{line_no}",
                community=COMMUNITIES["firmware"],
            )
            builder.add_link(sketch_id, function_id, "contains_function", source_file=source_file, source_location=f"L{line_no}")
            function_lines.append((line_no, function_id))

        topic_constants: dict[str, str] = {}
        for line_no, line in enumerate(lines, start=1):
            const_topic = re.search(r'const\s+char\*\s+([A-Za-z_][A-Za-z0-9_]*)\s*=\s*"([^"]+/[^"]*)"', line)
            if const_topic:
                name, topic = const_topic.groups()
                topic_constants[name] = topic
                topic_id = builder.topic_node(topic)
                builder.add_link(sketch_id, topic_id, "defines_topic_constant", source_file=source_file, source_location=f"L{line_no}")

            pin = re.search(r"const\s+int\s+(PIN_[A-Za-z0-9_]+)\s*=\s*(\d+)", line)
            if pin:
                name, gpio = pin.groups()
                label = f"{name} GPIO{gpio}"
                pin_id = builder.add_node(
                    f"gpio:{slug(name)}_{gpio}",
                    label,
                    file_type="gpio_pin",
                    source_file=source_file,
                    source_location=f"L{line_no}",
                    community=COMMUNITIES["hardware"],
                )
                builder.add_link(sketch_id, pin_id, "configures_pin", source_file=source_file, source_location=f"L{line_no}")
                if name == "PIN_TRIG" or name == "PIN_ECHO":
                    builder.add_link(builder.component_node("hw:ultrasonic_sensor", "Ultrasonic sensor"), pin_id, "wired_to", source_file=source_file)
                elif name == "PIN_LED":
                    builder.add_link(builder.component_node("hw:led_gpio26", "LED indicator GPIO26"), pin_id, "wired_to", source_file=source_file)
                elif name == "PIN_MOTOR":
                    builder.add_link(builder.component_node("hw:motor_gpio27", "Motor pump GPIO27"), pin_id, "wired_to", source_file=source_file)

            broker_ip = re.search(r'const\s+char\*\s+mqtt_server\s*=\s*"([^"]+)"', line)
            if broker_ip:
                endpoint_id = builder.add_node(
                    f"mqtt_endpoint:{slug(broker_ip.group(1))}",
                    broker_ip.group(1),
                    file_type="mqtt_endpoint",
                    source_file=source_file,
                    source_location=f"L{line_no}",
                    community=COMMUNITIES["mqtt"],
                )
                builder.add_link(sketch_id, endpoint_id, "uses_broker_endpoint", source_file=source_file, source_location=f"L{line_no}")

            safe_state = re.search(r'activarModoSeguro\("([^"]+)"\)', line)
            if safe_state:
                state_id = builder.add_node(
                    f"safe_state:{slug(safe_state.group(1))}",
                    safe_state.group(1),
                    file_type="firmware_safety_state",
                    source_file=source_file,
                    source_location=f"L{line_no}",
                    community=COMMUNITIES["firmware"],
                )
                owner = find_function_for_line(function_lines, line_no) or sketch_id
                builder.add_link(owner, state_id, "enters_safe_state", source_file=source_file, source_location=f"L{line_no}")

            for command in re.findall(r'"(ON|OFF)"', line):
                command_id = builder.command_node(command)
                owner = find_function_for_line(function_lines, line_no) or sketch_id
                builder.add_link(owner, command_id, "handles_command", source_file=source_file, source_location=f"L{line_no}")

        for line_no, line in enumerate(lines, start=1):
            publish = re.search(r"client\.publish\(([^,\)]+)", line)
            subscribe = re.search(r"client\.subscribe\(([^,\)]+)", line)
            for relation, match in (("publishes", publish), ("subscribes", subscribe)):
                if not match:
                    continue
                variable = match.group(1).strip()
                topic = topic_constants.get(variable)
                if not topic:
                    continue
                topic_id = builder.topic_node(topic)
                owner = find_function_for_line(function_lines, line_no) or sketch_id
                builder.add_link(owner, topic_id, relation, source_file=source_file, source_location=f"L{line_no}")

    return included


def add_infra(builder: GraphBuilder) -> list[str]:
    included: list[str] = []

    compose = ROOT / "compose.yaml"
    if compose.exists():
        included.append(rel(compose))
        source_file = rel(compose)
        file_id = builder.file_node(compose, "config", COMMUNITIES["infra"])
        service_id = builder.add_node(
            "docker_service:aquacontrol_mqtt",
            "aquacontrol-mqtt",
            file_type="docker_service",
            source_file=source_file,
            community=COMMUNITIES["infra"],
        )
        image_id = builder.add_node(
            "docker_image:eclipse_mosquitto_2",
            "eclipse-mosquitto:2",
            file_type="docker_image",
            source_file=source_file,
            community=COMMUNITIES["infra"],
        )
        port_id = builder.add_node(
            "tcp_port:1883",
            "TCP 1883",
            file_type="network_port",
            source_file=source_file,
            community=COMMUNITIES["mqtt"],
        )
        builder.add_link(file_id, service_id, "defines_service", source_file=source_file)
        builder.add_link(service_id, image_id, "uses_image", source_file=source_file)
        builder.add_link(service_id, port_id, "exposes", source_file=source_file)
        builder.add_link(builder.component_node("infra:docker", "Docker Compose", community=COMMUNITIES["infra"]), service_id, "runs_service", source_file=source_file)
        builder.add_link(builder.component_node("arch:mosquitto_broker", "Mosquitto MQTT broker"), port_id, "listens_on", source_file=source_file, confidence="INFERRED")

        for volume in [
            ("./mosquitto/config", "mosquitto/config/mosquitto.conf"),
            ("./mosquitto/data", "mosquitto/data/.gitkeep"),
            ("./mosquitto/log", "mosquitto/log/.gitkeep"),
        ]:
            volume_id = builder.add_node(
                f"docker_volume:{slug(volume[0])}",
                volume[0],
                file_type="docker_volume",
                source_file=source_file,
                community=COMMUNITIES["infra"],
            )
            builder.add_link(service_id, volume_id, "mounts_volume", source_file=source_file)
            target_path = ROOT / volume[1]
            if target_path.exists():
                target_id = builder.file_node(target_path, "config", COMMUNITIES["infra"])
                builder.add_link(volume_id, target_id, "contains", source_file=source_file)

    mosquitto_conf = ROOT / "mosquitto/config/mosquitto.conf"
    if mosquitto_conf.exists():
        included.append(rel(mosquitto_conf))
        source_file = rel(mosquitto_conf)
        file_id = builder.file_node(mosquitto_conf, "config", COMMUNITIES["infra"])
        broker_id = builder.component_node("arch:mosquitto_broker", "Mosquitto MQTT broker")
        builder.add_link(file_id, broker_id, "configures", source_file=source_file)
        text = read_text(mosquitto_conf)
        for line_no, line in enumerate(text.splitlines(), start=1):
            stripped = line.strip()
            if not stripped or stripped.startswith("#"):
                continue
            key = stripped.split()[0]
            value = stripped[len(key) :].strip()
            setting_id = builder.add_node(
                f"mosquitto_setting:{slug(key + ' ' + value)}",
                f"{key} {value}".strip(),
                file_type="mosquitto_setting",
                source_file=source_file,
                source_location=f"L{line_no}",
                community=COMMUNITIES["infra"] if key != "listener" else COMMUNITIES["mqtt"],
            )
            builder.add_link(broker_id, setting_id, "uses_setting", source_file=source_file, source_location=f"L{line_no}")
            if key == "listener" and value == "1883":
                builder.add_link(setting_id, "tcp_port:1883", "opens_port", source_file=source_file, source_location=f"L{line_no}")
            if key == "password_file":
                password_path = ROOT / value.lstrip("/")
                password_id = builder.add_node(
                    "mqtt_secret:password_file",
                    value,
                    file_type="mqtt_secret",
                    source_file=source_file,
                    source_location=f"L{line_no}",
                    community=COMMUNITIES["infra"],
                    note="Credential file is intentionally git-ignored.",
                )
                builder.add_link(setting_id, password_id, "reads_credentials", source_file=source_file, source_location=f"L{line_no}")
                if password_path.exists():
                    builder.add_link(password_id, file_id, "referenced_by_config", source_file=source_file)

    return included


def add_android_code_topic_edges(builder: GraphBuilder) -> None:
    main = ROOT / "App/app/src/main/java/com/example/aqua_control/MainActivity.kt"
    if not main.exists():
        return
    source_file = rel(main)
    main_activity = first_existing_id(builder, ["MainActivity.kt", "MainActivity"])
    android_id = builder.component_node("arch:android_app", "Android app")
    if main_activity:
        builder.add_link(android_id, main_activity, "implemented_by", source_file=source_file, confidence="INFERRED")

    text = read_text(main)
    constants: dict[str, str] = {}
    for line_no, line in enumerate(text.splitlines(), start=1):
        match = re.search(r'private\s+const\s+val\s+(TOPIC_[A-Z_]+)\s*=\s*"([^"]+)"', line)
        if match:
            constants[match.group(1)] = match.group(2)
            topic_id = builder.topic_node(match.group(2))
            builder.add_link(android_id, topic_id, "defines_topic_constant", source_file=source_file, source_location=f"L{line_no}")

    for name, topic in constants.items():
        relation = "subscribes" if name == "TOPIC_ALL" else "uses_topic"
        if name == "TOPIC_COMMAND":
            relation = "publishes"
        elif name in {"TOPIC_DISTANCE", "TOPIC_LEVEL", "TOPIC_STATUS"}:
            relation = "consumes"
        builder.add_link(android_id, builder.topic_node(topic), relation, source_file=source_file, confidence="INFERRED")


def generate_dot(builder: GraphBuilder) -> str:
    def esc(value: str) -> str:
        return value.replace("\\", "\\\\").replace('"', '\\"')

    lines = [
        "digraph AquaControlProject {",
        "  graph [rankdir=LR, bgcolor=\"white\", overlap=false, splines=true];",
        "  node [shape=box, style=\"rounded,filled\", fontname=\"Arial\", fontsize=10, fillcolor=\"#eef7f6\"];",
        "  edge [fontname=\"Arial\", fontsize=9, color=\"#587178\"];",
    ]
    for node in builder.graph["nodes"]:
        fill = {
            COMMUNITIES["docs"]: "#fff7d6",
            COMMUNITIES["android_xml"]: "#e9ecff",
            COMMUNITIES["firmware"]: "#e8f8e8",
            COMMUNITIES["infra"]: "#f3e8ff",
            COMMUNITIES["mqtt"]: "#ffe8e8",
            COMMUNITIES["hardware"]: "#f0f4f8",
            COMMUNITIES["architecture"]: "#dff7f3",
            COMMUNITIES["resource"]: "#ececec",
        }.get(node.get("community"), "#eef7f6")
        lines.append(f'  "{esc(node["id"])}" [label="{esc(node.get("label", node["id"]))}", fillcolor="{fill}"];')
    for link in builder.graph["links"]:
        source = link.get("source")
        target = link.get("target")
        if source not in builder.nodes_by_id or target not in builder.nodes_by_id:
            continue
        lines.append(f'  "{esc(source)}" -> "{esc(target)}" [label="{esc(link.get("relation", ""))}"];')
    lines.append("}")
    return "\n".join(lines) + "\n"


def generate_mermaid() -> str:
    return """flowchart LR
    Android[Android app / Jetpack Compose] -->|MQTT tcp://IP:1883| Broker[Mosquitto broker on Raspberry Pi]
    ESP32[ESP32 firmware] -->|publishes tinaco/distancia, tinaco/nivel, tinaco/estado| Broker
    Android -->|publishes tinaco/comando ON/OFF| Broker
    Broker -->|routes tinaco/comando| ESP32
    Broker -->|routes telemetry| Android
    ESP32 --> Sensor[Ultrasonic sensor GPIO5/GPIO18]
    ESP32 --> LED[LED PWM GPIO26]
    ESP32 --> Motor[Motor PWM GPIO27 + TIP120]
    Compose[compose.yaml] --> Broker
    MosquittoConf[mosquitto.conf] --> Broker
    Manifest[AndroidManifest.xml] -->|INTERNET + cleartext TCP| Android
    Docs[README + Docs + Notas] -.document.-> Android
    Docs -.document.-> ESP32
    Docs -.document.-> Broker
"""


def generate_svg(builder: GraphBuilder) -> str:
    """Generate a compact static SVG for the high-level enriched graph."""
    interesting = [
        "arch:android_app",
        "arch:mosquitto_broker",
        "arch:raspberry_pi",
        "arch:esp32",
        "infra:docker",
        "hw:ultrasonic_sensor",
        "hw:led_gpio26",
        "hw:motor_gpio27",
        "topic:tinaco_distancia",
        "topic:tinaco_nivel",
        "topic:tinaco_estado",
        "topic:tinaco_comando",
        "file:readme_md",
        "file:compose_yaml",
        "file:mosquitto_config_mosquitto_conf",
        "file:app_app_src_main_androidmanifest_xml",
    ]
    nodes = [node_id for node_id in interesting if node_id in builder.nodes_by_id]
    coords: dict[str, tuple[float, float]] = {}
    width, height = 1220, 760
    cx, cy, radius = width / 2, height / 2, 275
    for i, node_id in enumerate(nodes):
        angle = (2 * math.pi * i / max(1, len(nodes))) - math.pi / 2
        coords[node_id] = (cx + radius * math.cos(angle), cy + radius * math.sin(angle))

    def color(node_id: str) -> str:
        community = builder.nodes_by_id[node_id].get("community")
        return {
            COMMUNITIES["docs"]: "#fff1a8",
            COMMUNITIES["android_xml"]: "#cfd8ff",
            COMMUNITIES["firmware"]: "#c8f0c8",
            COMMUNITIES["infra"]: "#ead1ff",
            COMMUNITIES["mqtt"]: "#ffc9c9",
            COMMUNITIES["hardware"]: "#d6e2ea",
            COMMUNITIES["architecture"]: "#bdebe4",
        }.get(community, "#eafbf8")

    out = [
        '<svg xmlns="http://www.w3.org/2000/svg" width="1220" height="760" viewBox="0 0 1220 760">',
        '<rect width="1220" height="760" fill="#ffffff"/>',
        '<text x="40" y="44" font-family="Arial" font-size="26" font-weight="700" fill="#0b1f25">AquaControl enriched project graph</text>',
        '<text x="40" y="72" font-family="Arial" font-size="14" fill="#587178">High-level view generated from Graphify + Markdown + XML + ESP32 + Docker/Mosquitto files</text>',
        '<defs><marker id="arrow" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse"><path d="M 0 0 L 10 5 L 0 10 z" fill="#587178"/></marker></defs>',
    ]

    relevant_links = [
        link
        for link in builder.graph["links"]
        if link.get("source") in coords and link.get("target") in coords
    ]
    for link in relevant_links:
        sx, sy = coords[link["source"]]
        tx, ty = coords[link["target"]]
        out.append(
            f'<line x1="{sx:.1f}" y1="{sy:.1f}" x2="{tx:.1f}" y2="{ty:.1f}" '
            'stroke="#587178" stroke-width="1.4" marker-end="url(#arrow)" opacity="0.70"/>'
        )

    for node_id in nodes:
        x, y = coords[node_id]
        label = html.escape(builder.nodes_by_id[node_id].get("label", node_id))
        out.append(f'<rect x="{x - 82:.1f}" y="{y - 26:.1f}" width="164" height="52" rx="16" fill="{color(node_id)}" stroke="#0b1f25" stroke-opacity="0.22"/>')
        wrapped = label if len(label) <= 24 else label[:21] + "..."
        out.append(f'<text x="{x:.1f}" y="{y + 4:.1f}" text-anchor="middle" font-family="Arial" font-size="13" font-weight="700" fill="#0b1f25">{html.escape(wrapped)}</text>')

    out.append("</svg>\n")
    return "\n".join(out)


def generate_report(builder: GraphBuilder, included: dict[str, list[str]]) -> str:
    nodes_added = len(builder.graph["nodes"]) - builder.base_node_count
    links_added = len(builder.graph["links"]) - builder.base_link_count
    source_files = Counter(
        node.get("file_type", "unknown")
        for node in builder.graph["nodes"]
        if node.get("_origin") == "enriched"
    )
    relation_counts = Counter(link.get("relation", "unknown") for link in builder.graph["links"])

    graph_source_files = {
        node.get("source_file")
        for node in builder.graph["nodes"][: builder.base_node_count]
        if node.get("source_file")
    }
    manifest_files = sorted(graph_source_files)

    lines = [
        "# Enriched Graph Report - AquaControl IoT",
        "",
        "## Summary",
        f"- Base Graphify graph: {builder.base_node_count} nodes, {builder.base_link_count} links.",
        f"- Enriched graph: {len(builder.graph['nodes'])} nodes, {len(builder.graph['links'])} links.",
        f"- Added: {nodes_added} nodes, {links_added} links.",
        "- Outputs:",
        "  - `graphify-out/graph_enriched.json`",
        "  - `graphify-out/graph_enriched.dot`",
        "  - `graphify-out/graph_enriched.svg`",
        "  - `graphify-out/graph_enriched.mmd`",
        "  - `graphify-out/GRAPH_REPORT_ENRICHED.md`",
        "",
        "## What Graphify Originally Covered",
    ]
    if manifest_files:
        lines.extend(f"- `{path}`" for path in manifest_files)
    else:
        lines.append("- No Graphify manifest found.")

    lines.extend(
        [
            "",
            "## Added File Coverage",
        ]
    )
    for group, files in included.items():
        lines.append(f"- {group}: {len(files)} file(s)")
        lines.extend(f"  - `{file}`" for file in files)

    lines.extend(
        [
            "",
            "## Enriched Node Types",
        ]
    )
    lines.extend(f"- `{kind}`: {count}" for kind, count in sorted(source_files.items()))

    lines.extend(
        [
            "",
            "## Main Runtime Flow",
            "- Android app connects to Mosquitto over `tcp://<Raspberry-IP>:1883`.",
            "- Android subscribes to `tinaco/#` and consumes `tinaco/distancia`, `tinaco/nivel`, and `tinaco/estado`.",
            "- Android publishes `ON` / `OFF` commands to `tinaco/comando`.",
            "- ESP32 publishes sensor telemetry to `tinaco/distancia`, `tinaco/nivel`, and `tinaco/estado`.",
            "- ESP32 subscribes to `tinaco/comando` and controls motor PWM on GPIO27.",
            "- `compose.yaml` runs Eclipse Mosquitto and `mosquitto.conf` disables anonymous access.",
            "",
            "## Useful Cross-File Findings",
            "- The enriched graph adds structured Markdown sections, Android XML resource relationships, ESP32 firmware functions, Docker/Mosquitto runtime settings, and MQTT topic flow on top of the base Graphify extraction.",
            "- The Android app is now XML-based: `MainActivity` handles local login and `DashboardActivity` handles MQTT telemetry/control.",
            "- `Notas/Bitacora_Raspberry_MQTT.txt` still uses `aquacontrol/tinaco/nivel`, while current Android/ESP32 code uses `tinaco/nivel` and related `tinaco/*` topics.",
            "- `README.md` mentions Java 21.0.10, but `App/app/build.gradle.kts` currently compiles source/target as Java 11.",
            "- MQTT credentials are git-ignored in `mosquitto/config/password.txt`, but prototype credentials are hardcoded in Android and ESP32 source.",
            "- Broker IP examples differ by context: docs/basic sketch use `10.152.254.168`, integrated ESP32 sketch uses `10.71.193.168`, and Android expects manual IP input.",
            "- `AndroidManifest.xml` enables `android:usesCleartextTraffic=\"true\"`, which is required for plain TCP MQTT but should be revisited for production.",
            "",
            "## Top Relations",
        ]
    )
    lines.extend(f"- `{relation}`: {count}" for relation, count in relation_counts.most_common(18))
    lines.append("")
    return "\n".join(lines)


def main() -> None:
    if not BASE_GRAPH_PATH.exists():
        raise SystemExit(f"Missing base graph: {BASE_GRAPH_PATH}")

    base_graph = json.loads(BASE_GRAPH_PATH.read_text(encoding="utf-8"))
    builder = GraphBuilder(base_graph)
    add_architecture(builder)

    included = {
        "docs": add_markdown_and_notes(builder),
        "android_xml": add_android_xml(builder),
        "firmware": add_firmware(builder),
        "infra": add_infra(builder),
    }
    add_android_code_topic_edges(builder)

    enriched_labels = dict(COMMUNITY_LABELS)
    labels_path = OUT_DIR / ".graphify_labels.json"
    if labels_path.exists():
        for key, value in json.loads(labels_path.read_text(encoding="utf-8")).items():
            enriched_labels[int(key)] = value

    ENRICHED_JSON.write_text(json.dumps(builder.graph, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    ENRICHED_DOT.write_text(generate_dot(builder), encoding="utf-8")
    ENRICHED_MMD.write_text(generate_mermaid(), encoding="utf-8")
    ENRICHED_SVG.write_text(generate_svg(builder), encoding="utf-8")
    ENRICHED_REPORT.write_text(generate_report(builder, included), encoding="utf-8")
    ENRICHED_LABELS.write_text(json.dumps(enriched_labels, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")

    print(f"Wrote {ENRICHED_JSON.relative_to(ROOT)}")
    print(f"Wrote {ENRICHED_DOT.relative_to(ROOT)}")
    print(f"Wrote {ENRICHED_SVG.relative_to(ROOT)}")
    print(f"Wrote {ENRICHED_MMD.relative_to(ROOT)}")
    print(f"Wrote {ENRICHED_REPORT.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
