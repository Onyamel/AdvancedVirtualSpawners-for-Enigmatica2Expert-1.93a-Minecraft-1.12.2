"""Генерация оригинальных пиксельных ресурсов и рецептов AVS."""
import json
import struct
import zlib
from pathlib import Path

MOD = "advancedvirtualspawners"
ROOT = Path(__file__).resolve().parents[1] / "src/main/resources/assets" / MOD
FACES = ("north", "south", "east", "west", "up", "down")
PALETTES = (
    ((29, 113, 72), (72, 246, 134), (191, 255, 211)),
    ((22, 93, 166), (54, 201, 255), (198, 249, 255)),
    ((87, 48, 157), (172, 112, 255), (230, 203, 255)),
    ((153, 31, 55), (255, 76, 101), (255, 203, 185)),
)
METAL = ((9, 16, 26), (17, 31, 45), (23, 43, 59), (35, 57, 73), (64, 88, 99))
CHROME = ((94, 125, 135), (153, 183, 187), (224, 242, 228))

def save(name, value):
    path = ROOT / name
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2) + "\n", encoding="utf-8")

def png(name, size, pixel):
    raw = bytearray()
    for y in range(size):
        raw.append(0)
        for x in range(size):
            color = pixel(x, y)
            raw.extend(color if len(color) == 4 else (*color, 255))

    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data))

    path = ROOT / ("textures/" + name + ".png")
    path.parent.mkdir(parents=True, exist_ok=True)
    header = struct.pack(">IIBBBBB", size, size, 8, 6, 0, 0, 0)
    path.write_bytes(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", header)
                     + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))

def surface(x, y, tier, palette, face):
    a, b = min(x, 31 - x), min(y, 31 - y)
    edge, corner = min(a, b), max(a, b)
    dark, light, hot = palette
    if face == "core":
        dx, dy = 15 - a, 15 - b
        radius = max(dx, dy)
        if radius <= 6:
            return (hot, light, dark, light, dark, METAL[4], METAL[0])[radius]
        if dx <= 1 and 7 <= dy <= 9:
            return light if dy == 8 else dark
        if dy in (1, 2) and 7 <= dx <= 9:
            return dark
        if dy in (6, 7) and 8 <= dx <= 9 + tier:
            return light if dy == 6 and dx <= 7 + tier else dark
        if a in (5, 6) or b in (6, 7):
            return METAL[2] if a == 5 or b == 6 else METAL[1]
        return METAL[0]
    if corner < 6 and edge < 3:
        return CHROME[2 if edge == 1 else 0]
    if edge == 0:
        return METAL[1]
    if edge <= 3:
        if edge == 2 and corner in range(7, 13):
            return dark
        return METAL[3 if edge == 1 else 2]
    if face == "side":
        return METAL[0 if b == 4 else 2]
    dx, dy = 15 - a, 15 - b
    radius = max(dx, dy)
    if radius <= 5:
        if face == "bottom":
            return dark if radius in (2, 4) else METAL[2]
        return (hot, hot, light, dark, METAL[0], METAL[4])[radius]
    if radius in (7, 8) and min(dx, dy) <= 4 + tier:
        return light if radius == 7 and face == "top" else dark
    if radius == 10 and min(dx, dy) <= 2:
        return METAL[4]
    return METAL[1 if radius >= 9 else 0]


def element(start, end, surfaces):
    x, y, z = start
    right, top, back = end
    uv = {
        "north": [16 - right, 16 - top, 16 - x, 16 - y],
        "south": [x, 16 - top, right, 16 - y],
        "east": [16 - back, 16 - top, 16 - z, 16 - y],
        "west": [z, 16 - top, back, 16 - y],
        "up": [x, z, right, back],
        "down": [x, 16 - back, right, 16 - z],
    }
    return {"from": start, "to": end, "faces": {
        face: {"uv": uv[face], "texture": "#" + texture} for face, texture in surfaces.items()
    }}


def housing():
    sides = dict.fromkeys(FACES[:4], "side")
    yield element([0, 0, 0], [16, 2.5, 16], {**sides, "down": "bottom", "up": "bottom"})
    yield element([0, 13.5, 0], [16, 16, 16], {**sides, "up": "top", "down": "bottom"})
    yield element([1.25, 2.5, 1.25], [14.75, 13.5, 14.75], dict.fromkeys(sides, "core"))
    for x in (0, 14):
        for z in (0, 14):
            yield element([x, 2.5, z], [x + 2, 13.5, z + 2], sides)

for tier, palette in enumerate(PALETTES, 1):
    name = f"spawner_{tier}"
    textures = {}
    for face in ("side", "top", "bottom", "core"):
        path = "blocks/" + name + "_" + face
        png(path, 32, lambda x, y: surface(x, y, tier - 1, palette, face))
        textures[face] = f"{MOD}:{path}"
    textures["particle"] = textures["side"]
    save(f"models/block/{name}.json", {"parent": "block/block", "textures": textures,
         "elements": list(housing())})
    save(f"models/item/{name}.json", {"parent": f"{MOD}:block/{name}"})
    save(f"blockstates/{name}.json", {"variants": {"normal": {"model": f"{MOD}:{name}"}}})

# Иконки улучшений хранятся в ресурсах как готовые PNG.
UPGRADES = ("speed", "beheading", "luck")
for name in UPGRADES:
    save(f"models/item/{name}.json", {"parent": "item/generated", "textures": {"layer0": f"{MOD}:items/{name}"}})

def ingredient(name, data=None):
    return {"item": name} if data is None else {"item": name, "data": data}

def recipe(name, pattern, keys):
    save("recipes/" + name + ".json", {"type": "minecraft:crafting_shaped",
         "pattern": pattern, "key": keys, "result": {"item": f"{MOD}:{name}"}})

save("recipes/_factories.json", {"ingredients": {"soul_vial": "ru.e2e.avs.SoulVialIngredientFactory"}})
recipe("spawner_1", ["CPC", "NES", "CZC"], {
    "C": ingredient("enderio:item_material", 18),
    "E": ingredient("enderio:block_powered_spawner", 0),
    **{key: {"type": f"{MOD}:soul_vial", "entity": f"minecraft:{mob}"}
       for key, mob in (("P", "spider"), ("Z", "zombie"), ("S", "skeleton"), ("N", "enderman"))}})

for tier, (previous, current) in enumerate(zip(
        ("draconic_core", "wyvern_core", "awakened_core"),
        ("wyvern_core", "awakened_core", "chaotic_core")), 2):
    recipe(f"spawner_{tier}", ["PCP", "CSC", "PCP"], {
        "P": ingredient(f"draconicevolution:{previous}"), "C": ingredient(f"draconicevolution:{current}"),
        "S": ingredient("draconicevolution:draconic_spawner", 0)})
capacitor = ingredient("enderio:item_basic_capacitor", 1)
recipe("speed", ["RCR", "CEC", "RCR"], {
    "R": ingredient("minecraft:redstone_block"), "C": capacitor, "E": ingredient("enderio:item_extract_speed_upgrade", 0)})
recipe("beheading", ["OWO", "PSP", "PPP"], {
    "O": ingredient("minecraft:obsidian"), "W": ingredient("minecraft:skull", 1),
    "S": ingredient("minecraft:soul_sand"), "P": ingredient("minecraft:ender_pearl")})
recipe("luck", ["LEL", "ECE", "LEL"], {
    "L": ingredient("minecraft:lapis_block"), "E": ingredient("minecraft:emerald_block"), "C": capacitor})

for lang, names, labels, output, power, rate, statuses in (
    ("en_us", ("Virtual Spawner", "Speed Upgrade", "Beheading Upgrade", "Luck Upgrade"),
     ("Speed", "Heads", "Luck"), "Output", "%s / %s RF", "%s RF/t • %s ticks",
     ("Insert a filled soul vial", "No supported soul", "Insufficient energy", "Processing souls", "Output blocked",
      "Mob loot disabled", "Loot generation failed")),
    ("ru_ru", ("Виртуальный спавнер", "Улучшение «Скорость»", "Улучшение «Палач»", "Улучшение «Удача»"),
     ("Скорость", "Палач", "Удача"), "Добыча", "%s / %s RF", "%s RF/т • %s тиков",
     ("Вставьте флакон с душой", "Нет подходящей души", "Недостаточно энергии", "Обработка душ", "Выход заполнен",
      "Добыча мобов отключена", "Ошибка расчёта добычи")),
):
    values = {f"tile.{MOD}.spawner_{tier}.name": f"{names[0]} {'I' * tier if tier < 4 else 'IV'}"
              for tier in range(1, 5)}
    for i, name in enumerate(UPGRADES):
        values[f"item.{MOD}.{name}.name"] = names[i + 1]
        values[f"avs.short.{name}"] = labels[i]
    states = ("empty", "invalid", "energy", "running", "output", "no_mob_loot", "loot_error")
    values.update(dict(zip(("avs.state." + name for name in states), statuses)))
    values.update({"avs.output": output, "avs.power": power, "avs.rate": rate})
    path = ROOT / "lang" / (lang + ".lang")
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("".join(f"{k}={v}\n" for k, v in values.items()), encoding="utf-8")
