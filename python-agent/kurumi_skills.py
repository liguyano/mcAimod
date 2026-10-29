"""Kurumi skills: declarative capabilities backed by Java game tools.

Skills are Python orchestration functions, *not* privileged game operations.
Every primitive action still passes through the Java permission boundary.
"""
from __future__ import annotations
from dataclasses import dataclass
from typing import Any, Callable


@dataclass(frozen=True)
class Skill:
    name: str
    description: str
    tools: tuple[str, ...]
    run: Callable[..., Any]


class SkillRegistry:
    def __init__(self, call_tool: Callable[[str, list[Any]], Any],
                 allowed_tools: set[str], stopped: Callable[[], bool]):
        self._call_tool = call_tool
        self._allowed = frozenset(allowed_tools)
        self._stopped = stopped
        self._skills: dict[str, Skill] = {}

    def tool(self, name: str, *args: Any) -> Any:
        if self._stopped():
            raise InterruptedError("Kurumi emergency stop")
        if name not in self._allowed:
            raise PermissionError("Game tool not permitted: " + name)
        result = self._call_tool(name, list(args))
        if self._stopped():
            raise InterruptedError("Kurumi emergency stop")
        return result

    def register(self, skill: Skill) -> None:
        if not skill.name.isidentifier() or skill.name.startswith("_"):
            raise ValueError("Invalid skill identifier")
        if skill.name in self._skills:
            raise ValueError("Duplicate skill: " + skill.name)
        if not all(tool in self._allowed for tool in skill.tools):
            raise PermissionError("Skill requires unavailable game tools: " + skill.name)
        self._skills[skill.name] = skill

    def catalog(self) -> list[dict[str, Any]]:
        return [{"name": skill.name, "description": skill.description,
                 "tools": list(skill.tools)} for skill in self._skills.values()]

    def invoke(self, name: str, *args: Any) -> Any:
        if self._stopped():
            raise InterruptedError("Kurumi emergency stop")
        if name not in self._skills:
            raise KeyError("Unknown skill: " + name)
        skill = self._skills[name]
        result = skill.run(self, *args)
        if self._stopped():
            raise InterruptedError("Kurumi emergency stop")
        return result


def default_skills(registry: SkillRegistry) -> SkillRegistry:
    def status(r):
        return {"player": r.tool("get_player_status"),
                "position": r.tool("get_position"),
                "inventory": r.tool("get_inventory")}

    def navigate(r, x, y, z):
        return r.tool("goto", x, y, z)

    def harvest(r, block_id):
        return {"target": block_id, "action": r.tool("mine", block_id)}

    def collect(r):
        return r.tool("pickup_all")

    def inspect_chest(r):
        return r.tool("inspect_container")

    def store(r, item_id, count):
        return r.tool("put_item", item_id, count)

    def retrieve(r, item_id, count):
        return r.tool("take_item", item_id, count)

    def equip(r, item_id):
        return r.tool("equip_main_hand", item_id)

    def fight(r, entity_id):
        return r.tool("attack", entity_id)

    def rest(r):
        return r.tool("sleep_in_bed")

    def fishing(r):
        return r.tool("fish")

    def stop_fishing(r):
        return r.tool("stop_fishing")

    def examine(r):
        return {"entities": r.tool("scan_nearby_entities"),
                "blocks": r.tool("scan_nearby_blocks"),
                "drops": r.tool("scan_dropped_items")}

    for name, description, tools, fn in [
        ("status", "Read player status, coordinates and inventory", ("get_player_status","get_position","get_inventory"), status),
        ("navigate", "Navigate to x,y,z using Baritone", ("goto",), navigate),
        ("harvest", "Start mining a given Minecraft block identifier", ("mine",), harvest),
        ("collect_drops", "Pick up available dropped items", ("pickup_all",), collect),
        ("inspect_chest", "Inspect currently open container", ("inspect_container",), inspect_chest),
        ("store_item", "Put a specified quantity in the current container", ("put_item",), store),
        ("retrieve_item", "Take a specified quantity from the current container", ("take_item",), retrieve),
        ("equip", "Equip an item in the main hand", ("equip_main_hand",), equip),
        ("fight", "Attack a target Minecraft entity (subject to Java owner authorization)", ("attack",), fight),
        ("rest", "Use a nearby bed to sleep", ("sleep_in_bed",), rest),
        ("start_fishing", "Start fishing", ("fish",), fishing),
        ("stop_fishing", "Stop fishing", ("stop_fishing",), stop_fishing),
        ("examine_area", "Inspect nearby blocks, entities and dropped items", ("scan_nearby_entities","scan_nearby_blocks","scan_dropped_items"), examine),
    ]:
        if set(tools).issubset(registry._allowed):
            registry.register(Skill(name, description, tools, fn))
    return registry
