#!/usr/bin/env python3
"""
Catalogue des habitants de CityCore : ce que chacun vend (boutique, par rang) et rachète
(sell-prices), en cases « icône + nom », dans une page HTML autonome (images incluses).

Sources (lecture seule) :
  - src/main/resources/config.yml          boutiques et rachats
  - src/main/java/.../npc/CityNPC.java     noms, métiers, mods requis des habitants
  - src/main/resources/integration/mod-labels.yml   noms français des objets de mods
  - jar du client Minecraft 1.21.1 (.minecraft)     textures, modèles, fr_fr.json
  - jars du dossier mods du serveur                 textures, modèles et langues des mods

Usage : python tools/catalogue_pnj.py [--server "F:/Minecraft Server V2"] [--out build/catalogue-pnj.html]
Les blocs sont dessinés en 3D comme dans l'inventaire (modèles du jeu), les objets à plat.
"""
import argparse
import base64
import glob
import html
import io
import json
import math
import os
import re
import sys
import zipfile

import yaml
from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SIZE = 96          # taille des icônes générées (affichées à 48 px)
SS = 3             # suréchantillonnage des blocs 3D (bords lissés)

# Couleurs des codes § de Minecraft (nom des habitants)
MC_COLORS = {
    "0": "#000000", "1": "#0000AA", "2": "#00AA00", "3": "#00AAAA", "4": "#AA0000", "5": "#AA00AA",
    "6": "#FFAA00", "7": "#AAAAAA", "8": "#555555", "9": "#5555FF", "a": "#55FF55", "b": "#55FFFF",
    "c": "#FF5555", "d": "#FF55FF", "e": "#FFFF55", "f": "#FFFFFF",
}

# Teintes appliquées par le jeu (feuillage, herbe, cuir, potions…)
FOLIAGE = {"birch_leaves": (0x80, 0xA7, 0x55), "spruce_leaves": (0x61, 0x99, 0x61)}
DEFAULT_FOLIAGE = (0x48, 0xB5, 0x18)
GRASS = (0x7C, 0xBD, 0x6B)
LEATHER = (0xA0, 0x65, 0x40)
POTION_COLORS = {
    "healing": 0xF82423, "swiftness": 0x33EBFF, "water_breathing": 0x98DAC0, "fire_resistance": 0xFF9900,
    "slow_falling": 0xF3CFB9, "leaping": 0xFDFF84, "strength": 0xFFC700, "regeneration": 0xCD5CAB,
    "night_vision": 0xC2FF66, "invisibility": 0xF6F6F6, "poison": 0x87A363, "weakness": 0x484D48,
    "slowness": 0x8BAFE0, "harming": 0xA9656A, "luck": 0x59C106, "turtle_master": 0x8E6D9C,
}

TAG_ICONS = {  # familles « #minecraft:… » : objet représentant et libellé
    "minecraft:wool": ("white_wool", "Laine (toutes couleurs)"),
    "minecraft:leaves": ("oak_leaves", "Feuilles (toutes essences)"),
    "minecraft:planks": ("oak_planks", "Planches (toutes essences)"),
    "minecraft:logs_that_burn": ("oak_log", "Bûches (toutes essences)"),
    "minecraft:logs": ("oak_log", "Bûches (toutes essences)"),
}
GROUP_ICONS = {"LOGS": "oak_log", "PLANKS": "oak_planks", "WOOL": "white_wool", "LEAVES": "oak_leaves"}

MOD_NAMES = {
    "dmr": "Dragon Mounts", "taxov": "Tax' Ocean Villager", "toughasnails": "Tough As Nails", "tide": "Tide",
    "wildernature": "Wilder Nature", "farmersdelight": "Farmer's Delight", "epicfight": "Epic Fight",
    "handcrafted": "Handcrafted", "furniture": "Furniture", "irons_spellbooks": "Iron's Spells",
    "smallships": "Small Ships", "valarian_conquest": "Valarian Conquest", "mowziesmobs": "Mowzie's Mobs",
    "brewery": "Let's Do Brewery", "farm_and_charm": "Farm & Charm",
}

ROMAN = ["", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"]


def strip_codes(s):
    return re.sub(r"§.", "", s or "").strip()


def first_color(s):
    m = re.search(r"§([0-9a-f])", s or "")
    return MC_COLORS.get(m.group(1)) if m else None


def split_id(item_id):
    """« OAK_LOG » / « minecraft:oak_log » / « dmr:blank_egg » → (espace de noms, chemin)."""
    s = item_id.strip()
    if ":" in s:
        ns, path = s.split(":", 1)
        return ns.lower(), path.lower()
    return "minecraft", s.lower()


# ============================================================
#  RESSOURCES (jars du client et des mods)
# ============================================================

class Assets:
    """Index des fichiers assets/… des jars ; le premier jar qui fournit un fichier l'emporte."""

    def __init__(self, jars):
        self.index = {}
        for jar in jars:
            try:
                z = zipfile.ZipFile(jar)
            except (OSError, zipfile.BadZipFile):
                continue
            for name in z.namelist():
                if name.startswith("assets/") and name not in self.index:
                    self.index[name] = z

    def read(self, path):
        z = self.index.get(path)
        return z.read(path) if z else None

    def json(self, path):
        data = self.read(path)
        if data is None:
            return None
        try:
            return json.loads(data.decode("utf-8-sig"))
        except ValueError:
            return None

    def texture(self, rl):
        """Texture « ns:chemin » (premier cadre si animée), ou None."""
        ns, path = rl_split(rl)
        data = self.read(f"assets/{ns}/textures/{path}.png")
        if data is None:
            return None
        try:
            img = Image.open(io.BytesIO(data)).convert("RGBA")
        except OSError:
            return None
        w, h = img.size
        if h > w and h % w == 0:
            img = img.crop((0, 0, w, w))
        return img


def rl_split(rl):
    rl = rl.strip()
    if ":" in rl:
        ns, path = rl.split(":", 1)
    else:
        ns, path = "minecraft", rl
    return ns, path


def rl_norm(rl):
    ns, path = rl_split(rl)
    return f"{ns}:{path}"


class Model:
    def __init__(self):
        self.parents = []      # chaîne des parents (normalisés)
        self.textures = {}
        self.elements = None
        self.gui = None
        self.loader = None

    def tex(self, ref, depth=0):
        """Résout « #var » jusqu'à un emplacement de texture."""
        if ref is None or depth > 10:
            return None
        if ref.startswith("#"):
            return self.tex(self.textures.get(ref[1:]), depth + 1)
        return ref

    @property
    def flat(self):
        return any(p.endswith("builtin/generated") for p in self.parents)

    @property
    def entity(self):
        return any(p.endswith("builtin/entity") for p in self.parents)


def load_model(assets, rl, model=None, depth=0):
    model = model or Model()
    cur = rl
    while cur and depth < 25:
        depth += 1
        norm = rl_norm(cur)
        ns, path = rl_split(norm)
        data = assets.json(f"assets/{ns}/models/{path}.json")
        model.parents.append(norm)
        if data is None:
            break
        # Modèle par perspective (NeoForge) : celui de l'inventaire, sinon le modèle de base
        gui = (data.get("perspectives") or {}).get("gui")
        if isinstance(gui, dict):
            absorb(model, gui)
            return load_model(assets, gui["parent"], model, depth) if gui.get("parent") else model
        absorb(model, data)
        if "base" in data and isinstance(data["base"], dict):
            absorb(model, data["base"])
            if data["base"].get("parent"):
                return load_model(assets, data["base"]["parent"], model, depth)
        cur = data.get("parent")
    return model


def absorb(model, data):
    for k, v in (data.get("textures") or {}).items():
        if isinstance(v, str):
            model.textures.setdefault(k, v)
    if model.elements is None and data.get("elements"):
        model.elements = data["elements"]
    if model.gui is None and isinstance(data.get("display"), dict) and "gui" in data["display"]:
        model.gui = data["display"]["gui"]
    if model.loader is None and data.get("loader"):
        model.loader = data["loader"]


# ============================================================
#  RENDU DES ICÔNES
# ============================================================

def rot_xyz(rx, ry, rz):
    """Matrice de rotation JOML rotationXYZ (degrés) : Rx · Ry · Rz."""
    def rx_m(a):
        c, s = math.cos(a), math.sin(a)
        return [[1, 0, 0], [0, c, -s], [0, s, c]]

    def ry_m(a):
        c, s = math.cos(a), math.sin(a)
        return [[c, 0, s], [0, 1, 0], [-s, 0, c]]

    def rz_m(a):
        c, s = math.cos(a), math.sin(a)
        return [[c, -s, 0], [s, c, 0], [0, 0, 1]]

    return mat_mul(mat_mul(rx_m(math.radians(rx)), ry_m(math.radians(ry))), rz_m(math.radians(rz)))


def mat_mul(a, b):
    return [[sum(a[i][k] * b[k][j] for k in range(3)) for j in range(3)] for i in range(3)]


def mat_vec(m, v):
    return [sum(m[i][k] * v[k] for k in range(3)) for i in range(3)]


def element_rotation(rot):
    """Rotation d'un élément de modèle (autour d'un axe, depuis une origine, avec mise à l'échelle)."""
    if not rot:
        return lambda p: p, lambda n: n
    axis = rot.get("axis", "y")
    a = math.radians(rot.get("angle", 0))
    origin = rot.get("origin", [8, 8, 8])
    c, s = math.cos(a), math.sin(a)
    if axis == "x":
        m = [[1, 0, 0], [0, c, -s], [0, s, c]]
        scale = [1, 1 / c, 1 / c] if rot.get("rescale") and abs(c) > 1e-6 else [1, 1, 1]
    elif axis == "y":
        m = [[c, 0, s], [0, 1, 0], [-s, 0, c]]
        scale = [1 / c, 1, 1 / c] if rot.get("rescale") and abs(c) > 1e-6 else [1, 1, 1]
    else:
        m = [[c, -s, 0], [s, c, 0], [0, 0, 1]]
        scale = [1 / c, 1 / c, 1] if rot.get("rescale") and abs(c) > 1e-6 else [1, 1, 1]

    def point(p):
        q = [p[i] - origin[i] for i in range(3)]
        q = mat_vec(m, q)
        return [q[i] * scale[i] + origin[i] for i in range(3)]

    return point, lambda n: mat_vec(m, n)


# Coins d'une face (haut-gauche, haut-droite, bas-droite, bas-gauche de la texture), normale, UV par défaut
def face_geometry(direction, f, t):
    x1, y1, z1 = f
    x2, y2, z2 = t
    if direction == "up":
        return [(x1, y2, z1), (x2, y2, z1), (x2, y2, z2), (x1, y2, z2)], (0, 1, 0), (x1, z1, x2, z2)
    if direction == "down":
        return [(x1, y1, z2), (x2, y1, z2), (x2, y1, z1), (x1, y1, z1)], (0, -1, 0), (x1, 16 - z2, x2, 16 - z1)
    if direction == "north":
        return [(x2, y2, z1), (x1, y2, z1), (x1, y1, z1), (x2, y1, z1)], (0, 0, -1), (16 - x2, 16 - y2, 16 - x1, 16 - y1)
    if direction == "south":
        return [(x1, y2, z2), (x2, y2, z2), (x2, y1, z2), (x1, y1, z2)], (0, 0, 1), (x1, 16 - y2, x2, 16 - y1)
    if direction == "west":
        return [(x1, y2, z1), (x1, y2, z2), (x1, y1, z2), (x1, y1, z1)], (-1, 0, 0), (z1, 16 - y2, z2, 16 - y1)
    if direction == "east":
        return [(x2, y2, z2), (x2, y2, z1), (x2, y1, z1), (x2, y1, z2)], (1, 0, 0), (16 - z2, 16 - y2, 16 - z1, 16 - y1)
    return None


def tinted(img, color, factor=1.0):
    r, g, b, a = img.split()
    cr, cg, cb = (c / 255 for c in color) if color else (1, 1, 1)
    r = r.point(lambda v: int(v * cr * factor))
    g = g.point(lambda v: int(v * cg * factor))
    b = b.point(lambda v: int(v * cb * factor))
    return Image.merge("RGBA", (r, g, b, a))


def render_block(assets, model, tint_color):
    """Rendu 3D d'un modèle à éléments, vu comme dans l'inventaire (display.gui)."""
    gui = model.gui or {"rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [0.625] * 3}
    rot = rot_xyz(*(gui.get("rotation") or [0, 0, 0]))
    tr = gui.get("translation") or [0, 0, 0]
    sc = gui.get("scale") or [1, 1, 1]
    big = SIZE * SS
    k = big / 16.0

    def project(p):
        q = [(p[i] - 8) * sc[i] for i in range(3)]
        q = mat_vec(rot, q)
        q = [q[i] + tr[i] for i in range(3)]
        return (big / 2 + k * q[0], big / 2 - k * q[1], q[2])

    faces = []
    for el in model.elements or []:
        f, t = el.get("from"), el.get("to")
        if not f or not t:
            continue
        rot_point, rot_normal = element_rotation(el.get("rotation"))
        for direction, face in (el.get("faces") or {}).items():
            geo = face_geometry(direction, f, t)
            if geo is None:
                continue
            corners, normal, default_uv = geo
            n = mat_vec(rot, rot_normal(list(normal)))
            if n[2] <= 1e-4:
                continue  # face tournée vers l'arrière
            pts = [project(rot_point(list(c))) for c in corners]
            depth = sum(p[2] for p in pts) / 4
            faces.append((depth, pts, n, face, default_uv))

    canvas = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    drawn = 0
    for depth, pts, n, face, default_uv in sorted(faces, key=lambda x: x[0]):
        ref = model.tex(face.get("texture"))
        tex = assets.texture(ref) if ref else None
        if tex is None:
            continue
        # UV en pixels : « uvpx » (boîtes d'entité, déjà en pixels) ou « uv » sur 16
        u = 1.0 if "uvpx" in face else tex.width / 16.0
        uv = face.get("uvpx") or face.get("uv") or default_uv
        uvc = [(uv[0], uv[1]), (uv[2], uv[1]), (uv[2], uv[3]), (uv[0], uv[3])]
        steps = int(face.get("rotation", 0)) // 90 % 4
        uvc = [uvc[(i - steps) % 4] for i in range(4)]
        uvc = [(a * u, b * u) for a, b in uvc]
        # Affine texture → écran par trois coins, inversée pour PIL (écran → texture)
        du = (uvc[1][0] - uvc[0][0], uvc[1][1] - uvc[0][1])
        dv = (uvc[3][0] - uvc[0][0], uvc[3][1] - uvc[0][1])
        ds1 = (pts[1][0] - pts[0][0], pts[1][1] - pts[0][1])
        ds3 = (pts[3][0] - pts[0][0], pts[3][1] - pts[0][1])
        det_s = ds1[0] * ds3[1] - ds3[0] * ds1[1]
        if abs(det_s) < 1e-6:
            continue
        # écran → (a, b) coordonnées de la face, puis (a, b) → texture
        inv = (ds3[1] / det_s, -ds3[0] / det_s, -ds1[1] / det_s, ds1[0] / det_s)
        A = (du[0] * inv[0] + dv[0] * inv[2], du[0] * inv[1] + dv[0] * inv[3])
        D = (du[1] * inv[0] + dv[1] * inv[2], du[1] * inv[1] + dv[1] * inv[3])
        x0, y0 = pts[0][0], pts[0][1]
        data = (A[0], A[1], uvc[0][0] - A[0] * x0 - A[1] * y0,
                D[0], D[1], uvc[0][1] - D[0] * x0 - D[1] * y0)
        light = 1.0 if n[1] > 0.5 else (0.5 if n[1] < -0.5 else (0.8 if n[0] < 0 else 0.62))
        src = tinted(tex, tint_color if "tintindex" in face else None, light)
        layer = src.transform((big, big), Image.Transform.AFFINE, data, resample=Image.Resampling.NEAREST,
                              fillcolor=(0, 0, 0, 0))
        mask = Image.new("L", (big, big), 0)
        ImageDraw.Draw(mask).polygon([(p[0], p[1]) for p in pts], fill=255)
        alpha = Image.composite(layer.getchannel("A"), Image.new("L", (big, big), 0), mask)
        layer.putalpha(alpha)
        canvas.alpha_composite(layer)
        drawn += 1
    if drawn == 0:
        return None
    return canvas.resize((SIZE, SIZE), Image.Resampling.LANCZOS)


def render_flat(layers):
    """Objet à plat : calques superposés (teinte éventuelle), agrandis sans flou."""
    base = None
    for img, color in layers:
        if img is None:
            continue
        if base is None:
            base = Image.new("RGBA", img.size, (0, 0, 0, 0))
        if img.size != base.size:
            img = img.resize(base.size, Image.Resampling.NEAREST)
        base.alpha_composite(tinted(img, color) if color else img)
    if base is None:
        return None
    w = base.width
    factor = max(1, SIZE // w)
    out = base.resize((w * factor, w * factor), Image.Resampling.NEAREST)
    if out.width != SIZE:
        canvas = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
        out.thumbnail((SIZE, SIZE), Image.Resampling.NEAREST)
        canvas.alpha_composite(out, ((SIZE - out.width) // 2, (SIZE - out.height) // 2))
        out = canvas
    return out


def block_tint(path):
    if "leaves" in path:
        return FOLIAGE.get(path, DEFAULT_FOLIAGE)
    if path in ("grass_block", "short_grass", "tall_grass", "fern", "large_fern", "vine", "lily_pad", "sugar_cane"):
        return GRASS
    return DEFAULT_FOLIAGE


def cube_model(top, side, front=None):
    """Cube simple (repli quand le modèle est introuvable ou dessiné par une entité)."""
    m = Model()
    m.textures = {"up": top, "side": side, "front": front or side}
    m.elements = [{"from": [0, 0, 0], "to": [16, 16, 16], "faces": {
        "up": {"texture": "#up"}, "north": {"texture": "#front"}, "east": {"texture": "#side"},
        "south": {"texture": "#side"}, "west": {"texture": "#side"}, "down": {"texture": "#up"}}}]
    return m


def entity_box(texture, u, v, w, h, d, frm, to, flip, faces=("up", "north", "south", "east", "west")):
    """
    Pavé d'un modèle d'entité (texOffs u, v ; taille w × h × d) placé dans l'espace du bloc.
    flip : modèle dessiné retourné (têtes, boîte de Shulker : x et y inversés) ; sinon à l'endroit
    (coffres, dont les textures sont retournées depuis la 1.15).
    """
    f4, f5, f6, f7, f8, f9 = u, u + d, u + d + w, u + d + w + w, u + d + w + d, u + d + w + d + w
    f10, f11, f12 = v, v + d, v + d + h
    if flip:
        uv = {"up": (f6, f11, f5, f10), "north": (f5, f11, f6, f12), "east": (f4, f11, f5, f12),
              "west": (f6, f11, f8, f12), "south": (f8, f11, f9, f12)}
    else:
        uv = {"up": (f6, f11, f7, f10), "north": (f6, f12, f5, f11), "east": (f8, f12, f6, f11),
              "west": (f5, f12, f4, f11), "south": (f9, f12, f8, f11)}
    return {"from": frm, "to": to, "faces": {k: {"texture": texture, "uvpx": uv[k]} for k in faces}}


def entity_model(parent_model, elements):
    m = Model()
    m.gui = parent_model.gui
    m.elements = elements
    return m


SKULLS = {  # texture, texOffs et taille de la tête, emplacement dans le bloc
    "skeleton_skull": ("entity/skeleton/skeleton", 0, 0, 8, 8, 8),
    "wither_skeleton_skull": ("entity/skeleton/wither_skeleton", 0, 0, 8, 8, 8),
    "zombie_head": ("entity/zombie/zombie", 0, 0, 8, 8, 8),
    "creeper_head": ("entity/creeper/creeper", 0, 0, 8, 8, 8),
    "piglin_head": ("entity/piglin/piglin", 0, 0, 10, 8, 8),
    "dragon_head": ("entity/enderdragon/dragon", 112, 30, 16, 16, 16),
}
DYES = {"white": 0xF9FFFE, "orange": 0xF9801D, "magenta": 0xC74EBD, "light_blue": 0x3AB3DA, "yellow": 0xFED83D,
        "lime": 0x80C71F, "pink": 0xF38BAA, "gray": 0x474F52, "light_gray": 0x9D9D97, "cyan": 0x169C9C,
        "purple": 0x8932B8, "blue": 0x3C44AA, "brown": 0x835432, "green": 0x5E7C16, "red": 0xB02E26, "black": 0x1D1D21}


def rgb(c):
    return (c >> 16) & 255, (c >> 8) & 255, c & 255


def flat_crop(tex, box, color=None):
    """Morceau d'une texture d'entité (bannière, bouclier) dessiné à plat, centré."""
    if tex is None:
        return None
    part = tex.crop(box)
    if color:
        part = tinted(part, color)
    factor = max(1, int(SIZE * 0.9 // max(part.size)))
    part = part.resize((part.width * factor, part.height * factor), Image.Resampling.NEAREST)
    canvas = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    canvas.alpha_composite(part, ((SIZE - part.width) // 2, (SIZE - part.height) // 2))
    return canvas


def entity_icon(assets, path, model):
    """Objets que le jeu dessine avec une entité (coffres, têtes, lits…) : modèle reconstitué."""
    mc = "minecraft:"
    if path in ("chest", "trapped_chest", "ender_chest"):
        tex = mc + "entity/chest/" + {"chest": "normal", "trapped_chest": "trapped", "ender_chest": "ender"}[path]
        return render_block(assets, entity_model(model, [
            entity_box(tex, 0, 19, 14, 10, 14, [1, 0, 1], [15, 10, 15], False),
            entity_box(tex, 0, 0, 14, 5, 14, [1, 9, 1], [15, 14, 15], False),
            entity_box(tex, 0, 0, 2, 4, 1, [7, 7, 15], [9, 11, 16], False)]), None)
    if path in SKULLS:
        tex, u, v, w, h, d = SKULLS[path]
        x0, z0 = (16 - w) / 2, (16 - d) / 2
        head = entity_box(mc + tex, u, v, w, h, d, [x0, 0, z0], [x0 + w, h, z0 + d], True)
        head["rotation"] = {"origin": [8, 8, 8], "axis": "y", "angle": 180}  # le visage vers le joueur
        return render_block(assets, entity_model(model, [head]), None)
    if path.endswith("shulker_box"):
        color = path[:-len("_shulker_box")]
        tex = mc + "entity/shulker/shulker" + (f"_{color}" if color else "")
        return render_block(assets, entity_model(model, [
            entity_box(tex, 0, 28, 16, 8, 16, [0, 0, 0], [16, 8, 16], True),
            entity_box(tex, 0, 0, 16, 12, 16, [0, 4, 0], [16, 16, 16], True)]), None)
    if path == "conduit":
        return render_block(assets, entity_model(model, [
            entity_box(mc + "entity/conduit/base", 0, 0, 6, 6, 6, [5, 5, 5], [11, 11, 11], True)]), None)
    if path.endswith("_bed"):
        tex = mc + "entity/bed/" + path[:-4]
        head = {"up": {"texture": tex, "uvpx": (6, 6, 22, 22), "rotation": 180},
                "north": {"texture": tex, "uvpx": (6, 0, 22, 6), "rotation": 180},
                "east": {"texture": tex, "uvpx": (22, 6, 28, 22), "rotation": 90},
                "west": {"texture": tex, "uvpx": (0, 6, 6, 22), "rotation": 270}}
        foot = {"up": {"texture": tex, "uvpx": (6, 28, 22, 44), "rotation": 180},
                "south": {"texture": tex, "uvpx": (22, 22, 38, 28), "rotation": 180},
                "east": {"texture": tex, "uvpx": (22, 28, 28, 44), "rotation": 90},
                "west": {"texture": tex, "uvpx": (0, 28, 6, 44), "rotation": 270}}
        legs = {"north": {"texture": tex, "uvpx": (53, 3, 56, 6)}, "east": {"texture": tex, "uvpx": (50, 3, 53, 6)},
                "west": {"texture": tex, "uvpx": (56, 3, 59, 6)}, "south": {"texture": tex, "uvpx": (59, 3, 62, 6)}}
        elements = [{"from": [0, 3, 0], "to": [16, 9, 16], "faces": head},
                    {"from": [0, 3, 16], "to": [16, 9, 32], "faces": foot}]
        for x, z in ((0, 0), (13, 0), (0, 29), (13, 29)):
            elements.append({"from": [x, 0, z], "to": [x + 3, 3, z + 3], "faces": legs})
        m = entity_model(model, elements)
        m.gui = {"rotation": [30, 160, 0], "translation": [0, 0, -4], "scale": [0.42] * 3}
        return render_block(assets, m, None)
    if path.endswith("_banner"):
        return flat_crop(assets.texture(mc + "entity/banner_base"), (1, 1, 21, 41), rgb(DYES.get(path[:-7], 0xF9FFFE)))
    if path == "shield":
        return flat_crop(assets.texture(mc + "entity/shield_base_nopattern"), (1, 1, 13, 23))
    if path == "decorated_pot":
        side, base = mc + "entity/decorated_pot/decorated_pot_side", mc + "entity/decorated_pot/decorated_pot_base"
        body = {k: {"texture": side, "uvpx": (1, 0, 15, 16)} for k in ("north", "south", "east", "west")}
        body["up"] = {"texture": base, "uvpx": (14, 13, 28, 27)}
        neck = {k: {"texture": base, "uvpx": (8, 8, 16, 11)} for k in ("north", "south", "east", "west")}
        neck["up"] = {"texture": base, "uvpx": (8, 0, 16, 8)}
        return render_block(assets, entity_model(model, [
            {"from": [1, 0, 1], "to": [15, 13, 15], "faces": body},
            {"from": [4, 13, 4], "to": [12, 16, 12], "faces": neck}]), None)
    return None


class IconMaker:
    def __init__(self, assets):
        self.assets = assets
        self.cache = {}
        self.missing = []

    def icon(self, ns, path, potion=None):
        key = (ns, path, potion)
        if key not in self.cache:
            img = None
            try:
                img = self._icon(ns, path, potion)
            except Exception as e:  # icône manquante : case avec initiales
                print(f"  ! {ns}:{path} : {e}", file=sys.stderr)
            if img is None:
                self.missing.append(f"{ns}:{path}")
            self.cache[key] = data_uri(img) if img is not None else None
        return self.cache[key]

    def _icon(self, ns, path, potion):
        model = load_model(self.assets, f"{ns}:item/{path}")
        if model.entity and ns == "minecraft":
            img = entity_icon(self.assets, path, model)
            if img is not None:
                return img
        # Modèle qui ne vaut que par ses variantes (thermomètre…) : celle du milieu
        if "layer0" not in model.textures and model.elements is None:
            data = self.assets.json(f"assets/{ns}/models/item/{path}.json") or {}
            overrides = [o.get("model") for o in data.get("overrides") or [] if o.get("model")]
            if overrides:
                model = load_model(self.assets, overrides[len(overrides) // 2])
        # Œufs de Dragon Mounts : forme de l'œuf de dragon, texture du mod
        if ns == "dmr" and path.endswith("egg") and model.elements is None:
            tex = next((k for k in self.assets.index if k.startswith("assets/dmr/textures/block/")
                        and path.replace("_egg", "") in k and k.endswith("egg.png")), None)
            if tex:
                egg = load_model(self.assets, "minecraft:block/dragon_egg")
                ref = "dmr:" + tex[len("assets/dmr/textures/"):-4]
                egg.textures = {"all": ref, "particle": ref, "texture": ref}
                egg.gui = None
                return render_block(self.assets, egg, None)
        if model.flat or (model.elements is None and "layer0" in model.textures):
            layers = []
            for i in range(5):
                ref = model.tex(model.textures.get(f"layer{i}")) if f"layer{i}" in model.textures else None
                if ref is None:
                    continue
                color = None
                if i == 0 and path.startswith("leather_"):
                    color = LEATHER
                if i == 0 and path in ("potion", "splash_potion", "lingering_potion", "tipped_arrow"):
                    base = (potion or "water").lower().replace("long_", "").replace("strong_", "")
                    c = POTION_COLORS.get(base, 0x385DC6)
                    color = ((c >> 16) & 255, (c >> 8) & 255, c & 255)
                layers.append((self.assets.texture(ref), color))
            img = render_flat(layers)
            if img is not None:
                return img
        if model.elements:
            img = render_block(self.assets, model, block_tint(path))
            if img is not None:
                return img
        # Replis : texture d'objet, de bloc, puis toute texture du modèle
        for ref in (f"{ns}:item/{path}", f"{ns}:block/{path}"):
            tex = self.assets.texture(ref)
            if tex is not None:
                if "/block/" in f"/{ref.split(':')[1]}":
                    return render_block(self.assets, cube_model(ref, ref), None)
                return render_flat([(tex, None)])
        for ref in [model.textures.get("particle")] + list(model.textures.values()):
            if ref and not ref.startswith("#"):
                tex = self.assets.texture(ref)
                if tex is not None:
                    return render_flat([(tex, None)])
        return None


def data_uri(img):
    buf = io.BytesIO()
    img.save(buf, "PNG", optimize=True)
    return "data:image/png;base64," + base64.b64encode(buf.getvalue()).decode("ascii")


# ============================================================
#  NOMS
# ============================================================

class Names:
    def __init__(self, vanilla, mod_lang, plugin_labels):
        self.vanilla = vanilla
        self.mod_lang = mod_lang
        self.plugin = plugin_labels

    def item(self, ns, path):
        if ns == "minecraft":
            return (self.vanilla.get(f"item.minecraft.{path}") or self.vanilla.get(f"block.minecraft.{path}")
                    or words(path))
        full = f"{ns}:{path}"
        if full in self.plugin:
            return self.plugin[full]
        if full.startswith("dmr:dragon_whistle."):
            color = full.split(".", 1)[1]
            return "Sifflet de dragon " + COLORS_FR.get(color, color)
        return (self.mod_lang.get(f"item.{ns}.{path}") or self.mod_lang.get(f"block.{ns}.{path}") or words(path))

    def enchant(self, key, level):
        name = self.vanilla.get(f"enchantment.minecraft.{key}") or words(key)
        level = int(level or 1)
        # Enchantements à un seul niveau : pas de chiffre (« Toucher de soie », « Raccommodage »)
        if level > 1 or key not in ("silk_touch", "mending", "infinity", "flame", "channeling", "multishot", "aqua_affinity"):
            name += " " + (self.vanilla.get(f"enchantment.level.{level}") or (ROMAN[level] if level < 11 else str(level)))
        return name

    def potion(self, item_path, potion):
        p = (potion or "water").lower()
        base = p.replace("long_", "").replace("strong_", "")
        name = (self.vanilla.get(f"item.minecraft.{item_path}.effect.{base}")
                or f"Potion ({words(base)})")
        if p.startswith("strong_"):
            name += " II"
        elif p.startswith("long_"):
            name += " (prolongée)"
        return name


COLORS_FR = {"white": "blanc", "black": "noir", "red": "rouge", "blue": "bleu", "green": "vert"}


def words(raw):
    return " ".join(w.capitalize() for w in re.split(r"[_.]", raw) if w)


def load_vanilla_lang(mc_dir):
    index = os.path.join(mc_dir, "assets", "indexes", "17.json")
    try:
        objects = json.load(open(index, encoding="utf-8"))["objects"]
        h = objects["minecraft/lang/fr_fr.json"]["hash"]
        return json.load(open(os.path.join(mc_dir, "assets", "objects", h[:2], h), encoding="utf-8"))
    except (OSError, KeyError, ValueError):
        print("  ! fr_fr.json du client introuvable : noms anglais", file=sys.stderr)
        return {}


def load_mod_lang(jars):
    out = {}
    for jar in jars:
        try:
            z = zipfile.ZipFile(jar)
        except (OSError, zipfile.BadZipFile):
            continue
        names = z.namelist()
        for lang in ("fr_fr", "en_us"):  # le français d'abord, l'anglais complète
            for n in names:
                if re.match(rf"assets/[^/]+/lang/{lang}\.json$", n) and not n.startswith("assets/minecraft/"):
                    try:
                        for k, v in json.loads(z.read(n).decode("utf-8-sig")).items():
                            if isinstance(v, str):
                                out.setdefault(k, v)
                    except ValueError:
                        pass
    return out


def server_mod_ids(jars):
    ids = set()
    for jar in jars:
        try:
            z = zipfile.ZipFile(jar)
            for n in ("META-INF/neoforge.mods.toml", "META-INF/mods.toml"):
                if n in z.namelist():
                    ids.update(re.findall(r'modId\s*=\s*"([^"]+)"', z.read(n).decode("utf-8", "replace")))
                    break
        except (OSError, zipfile.BadZipFile):
            continue
    return ids


# ============================================================
#  HABITANTS ET CONFIG
# ============================================================

def load_npcs(java_path):
    """Constantes de CityNPC : clé de config → nom, couleur, métier, chapitre, mods."""
    src = open(java_path, encoding="utf-8").read()
    npcs = {}
    order = []
    pattern = re.compile(r'^    ([A-Z_]+)\(\s*"citycore_[^"]*",\s*"([^"]*)",\s*"([^"]*)",\s*"([^"]*)"', re.M)
    for m in pattern.finditer(src):
        before = src[:m.start()].rstrip().splitlines()
        comment = before[-1].strip()[3:] if before and before[-1].strip().startswith("//") else ""
        end = re.compile(r"^    \)[,;]", re.M).search(src, m.end())
        # Dernier argument facultatif : mods requis (« dmr ») ou facultatifs (« +irons_spellbooks »)
        block = re.sub(r"//[^\n]*", "", src[m.end():end.start() if end else len(src)]).rstrip()
        mods = re.search(r'"([a-z_+, ]+)"\s*$', block)
        required, optional = [], []
        if mods and re.search(r"(PersonalQuestCatalog\.\w+\(\)|Map\.of\(\)),\s*\"[a-z_+, ]+\"\s*$", block):
            for mod in mods.group(1).split(","):
                mod = mod.strip()
                (optional if mod.startswith("+") else required).append(mod.lstrip("+"))
        key = m.group(4)
        npcs[key] = {
            "key": key, "name": strip_codes(m.group(2)), "color": first_color(m.group(2)) or "#e8e0c8",
            "role": strip_codes(m.group(3)), "chapter": comment, "required": required, "optional": optional,
        }
        order.append(key)
    return npcs, order


def gate(entry, item_ns, present):
    """Actif sur le serveur ? (avec-mod / sans-mod, mod de l'objet)."""
    reasons = []
    ok = True
    for mod in str(entry.get("avec-mod") or "").split(","):
        mod = mod.strip()
        if mod:
            reasons.append(f"avec {MOD_NAMES.get(mod, mod)}")
            ok &= mod in present
    for mod in str(entry.get("sans-mod") or "").split(","):
        mod = mod.strip()
        if mod:
            reasons.append(f"sans {MOD_NAMES.get(mod, mod)}")
            ok &= mod not in present
    if item_ns != "minecraft":
        ok &= item_ns in present
    return ok, reasons


def describe_token(token, names, npcs):
    """Ingrédient « habitant:MATERIAU » / « #LOGS » / « mod:objet » → (fournisseur, libellé)."""
    supplier = None
    if ":" in token and token.split(":", 1)[0] in npcs:
        supplier, token = token.split(":", 1)
    if token.startswith("#"):
        label = TAG_ICONS.get(token[1:].lower(), (None, None))[1] or words(token[1:].lower())
    else:
        ns, path = split_id(token)
        label = names.item(ns, path)
    return supplier, label


def item_entry(raw_id, names, icons, potion=None):
    """(espace de noms, libellé, icône) d'un identifiant de config (objet, tag ou famille)."""
    rid = raw_id.strip()
    if rid.startswith("#"):
        tag = rid[1:].lower()
        rep, label = TAG_ICONS.get(tag, (None, None))
        if rep is None:
            rep, label = tag.split(":")[-1], words(tag.split(":")[-1])
        return "minecraft", label, icons.icon("minecraft", rep)
    ns, path = split_id(rid)
    return ns, names.item(ns, path), icons.icon(ns, path, potion)


def build_catalogue(cfg, npcs, order, names, icons, present):
    out = []
    keys = [k for k in order if isinstance(cfg.get(k), dict)]
    keys += [k for k, v in cfg.items() if isinstance(v, dict) and ("shop" in v or "sell-prices" in v) and k not in keys]
    for key in keys:
        section = cfg.get(key)
        if not isinstance(section, dict) or not ("shop" in section or "sell-prices" in section):
            continue
        info = npcs.get(key, {"key": key, "name": key.capitalize(), "color": "#e8e0c8", "role": "",
                              "chapter": "", "required": [], "optional": []})
        npc_ok = all(m in present for m in info["required"])
        buys = []
        for raw, sp in (section.get("sell-prices") or {}).items():
            sp = sp or {}
            ns, label, icon = item_entry(str(raw), names, icons)
            ok, reasons = gate(sp, ns, present)
            price, qty = sp.get("price", 0), sp.get("quantity", 1)
            buys.append({
                "label": label, "icon": icon, "price": price, "qty": qty,
                "unit": round(price / qty, 3) if qty else 0, "capacity": sp.get("capacity"),
                "active": ok and npc_ok, "gates": reasons, "id": str(raw),
            })
        sells = []
        for level, items in sorted((section.get("shop") or {}).items(), key=lambda kv: int(kv[0])):
            for it in items or []:
                raw = str(it.get("material"))
                potion = it.get("potion")
                ns, label, icon = item_entry(raw, names, icons, potion)
                base_label = label
                details = []
                if potion:
                    label = names.potion(split_id(raw)[1], potion)
                ench = it.get("enchantments") or {}
                if ench:
                    details += [names.enchant(k, v) for k, v in ench.items()]
                custom = strip_codes(it.get("name")) if it.get("name") else None
                book = (it.get("book") or {}).get("title")
                if custom:
                    details.insert(0, base_label if not potion else label)
                    label = custom
                elif book:
                    label = book
                req = []
                for token, amount in (it.get("requires") or {}).items():
                    supplier, rlabel = describe_token(str(token), names, npcs)
                    sup = npcs.get(supplier, {}).get("name", supplier.capitalize()) if supplier else None
                    req.append(f"{amount} × {rlabel}" + (f" (stock de {sup})" if sup else ""))
                supply = it.get("supply") or {}
                ok, reasons = gate(it, ns, present)
                sells.append({
                    "label": label, "icon": icon, "price": it.get("price", 0), "qty": it.get("quantity", 1),
                    "level": int(level), "details": details, "requires": req,
                    "supply": [supply.get("max"), supply.get("restock-minutes")] if supply else None,
                    "lore": [strip_codes(l) for l in (it.get("lore") or [])],
                    "enchanted": bool(ench) or raw.upper() in ("ENCHANTED_BOOK", "EXPERIENCE_BOTTLE", "ENCHANTED_GOLDEN_APPLE", "NETHER_STAR"),
                    "active": ok and npc_ok, "gates": reasons, "id": raw,
                })
        out.append({**info, "active": npc_ok, "buys": buys, "sells": sells,
                    "requiredNames": [MOD_NAMES.get(m, m) for m in info["required"]],
                    "optionalNames": [MOD_NAMES.get(m, m) for m in info["optional"]]})
    return out


# ============================================================
#  PAGE
# ============================================================

PAGE = r"""<!doctype html>
<html lang="fr">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Catalogue des habitants</title>
<style>
:root {
  --bg: #17151c; --panel: #211e27; --panel2: #2a2631; --line: #3a3443; --text: #ece6d6; --muted: #a39b8b;
  --slot: #8b8b8b; --slot-hi: #ffffff; --slot-lo: #373737; --slot-in: #8b8b8b;
  --gold: #f2c14e; --green: #7fd36b; --tip: #100010f0; --tip-line: #5000ff55;
}
* { box-sizing: border-box; }
body { margin: 0; background: var(--bg); color: var(--text); font: 14px/1.4 system-ui, "Segoe UI", sans-serif; }
header.top { position: sticky; top: 0; z-index: 5; background: #17151ce8; backdrop-filter: blur(6px);
  border-bottom: 1px solid var(--line); padding: 12px 16px; display: flex; flex-wrap: wrap; gap: 10px 16px; align-items: center; }
header.top h1 { font-size: 18px; margin: 0 8px 0 0; letter-spacing: .3px; }
header.top h1 small { color: var(--muted); font-weight: 400; font-size: 13px; margin-left: 6px; }
.controls { display: flex; flex-wrap: wrap; gap: 8px; align-items: center; }
input[type=search] { background: var(--panel2); border: 1px solid var(--line); color: var(--text); border-radius: 8px;
  padding: 7px 10px; width: 240px; font: inherit; }
.seg { display: inline-flex; border: 1px solid var(--line); border-radius: 8px; overflow: hidden; }
.seg button { background: var(--panel); color: var(--muted); border: 0; padding: 7px 12px; font: inherit; cursor: pointer; }
.seg button + button { border-left: 1px solid var(--line); }
.seg button.on { background: var(--panel2); color: var(--text); box-shadow: inset 0 -2px 0 var(--gold); }
label.chk { display: inline-flex; gap: 6px; align-items: center; color: var(--muted); cursor: pointer; user-select: none; }
nav.npcs { display: flex; flex-wrap: wrap; gap: 6px; padding: 12px 16px 0; }
nav.npcs a { text-decoration: none; color: var(--text); background: var(--panel); border: 1px solid var(--line);
  border-left: 3px solid var(--c); border-radius: 6px; padding: 3px 9px; font-size: 13px; }
nav.npcs a.off { opacity: .45; }
main { padding: 8px 16px 48px; display: grid; gap: 18px; }
section.npc { background: var(--panel); border: 1px solid var(--line); border-radius: 12px; padding: 14px 16px 16px;
  border-top: 3px solid var(--c); scroll-margin-top: 80px; }
section.npc.off { opacity: .6; }
.npc-head { display: flex; flex-wrap: wrap; align-items: baseline; gap: 4px 12px; margin-bottom: 6px; }
.npc-head h2 { margin: 0; font-size: 20px; color: var(--c); text-shadow: 0 1px 0 #0008; }
.npc-head .role { color: var(--text); }
.npc-head .meta { color: var(--muted); font-size: 12.5px; }
.badge { display: inline-block; font-size: 11.5px; padding: 1px 7px; border-radius: 99px; border: 1px solid var(--line);
  color: var(--muted); background: var(--panel2); margin-left: 4px; white-space: nowrap; }
.badge.warn { color: #ffb4a8; border-color: #6b3a35; background: #3a2220; }
h3 { font-size: 13px; text-transform: uppercase; letter-spacing: .8px; color: var(--muted); margin: 14px 0 8px; font-weight: 600; }
h3 b { color: var(--text); }
.rank { display: flex; gap: 10px; align-items: flex-start; margin: 0 0 10px; }
.rank > .lv { flex: 0 0 64px; font-size: 12px; color: var(--muted); padding-top: 4px; }
.rank > .lv b { display: block; color: var(--gold); font-size: 13px; }
.grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(112px, 1fr)); gap: 8px; flex: 1; }
.tile { position: relative; background: var(--panel2); border: 1px solid var(--line); border-radius: 8px; padding: 8px 6px 7px;
  display: flex; flex-direction: column; align-items: center; text-align: center; gap: 5px; min-height: 128px; cursor: default; }
.tile:hover { border-color: #6d6478; background: #312c39; }
.tile.off { opacity: .38; filter: grayscale(.7); }
.slot { width: 56px; height: 56px; background: var(--slot-in); display: grid; place-items: center; position: relative;
  border: 2px solid; border-color: var(--slot-lo) var(--slot-hi) var(--slot-hi) var(--slot-lo); }
.slot img { width: 48px; height: 48px; display: block; }
.slot .ph { width: 40px; height: 40px; display: grid; place-items: center; background: #5a5a5a; color: #eee; font-weight: 700; font-size: 13px; }
.slot .qty { position: absolute; right: 1px; bottom: -2px; color: #fff; font: 700 15px/1 "Courier New", monospace;
  text-shadow: 2px 2px 0 #3f3f3f; }
.slot.glint::after { content: ""; position: absolute; inset: 4px; pointer-events: none; mix-blend-mode: screen;
  background: linear-gradient(115deg, transparent 30%, #a060ff66 45%, #c090ff99 50%, #a060ff66 55%, transparent 70%) 0 0/250% 250%;
  animation: glint 3.2s linear infinite; -webkit-mask: var(--m) center/contain no-repeat; mask: var(--m) center/contain no-repeat; }
@keyframes glint { from { background-position: 100% 0; } to { background-position: -150% 0; } }
.name { font-size: 12.5px; line-height: 1.25; word-break: break-word; }
.price { font-size: 12px; color: var(--gold); font-variant-numeric: tabular-nums; }
.price span { color: var(--muted); }
.flag { position: absolute; top: 5px; right: 5px; width: 8px; height: 8px; border-radius: 50%; background: #c9a227; }
.empty { color: var(--muted); font-style: italic; font-size: 13px; }
#tip { position: fixed; z-index: 20; pointer-events: none; max-width: 320px; background: var(--tip); color: #fff;
  border: 2px solid #25025a; outline: 2px solid #100010; border-radius: 3px; padding: 6px 9px; font-size: 13px; display: none;
  box-shadow: inset 0 0 0 1px #5000ff40; }
#tip .t { font-weight: 600; margin-bottom: 3px; }
#tip .g { color: #aaa; } #tip .y { color: var(--gold); } #tip .a { color: #55ffff; } #tip .p { color: #ff55ff; } #tip .r { color: #ff7a70; }
@media (max-width: 640px) {
  input[type=search] { width: 100%; }
  .controls { width: 100%; }
  .rank { flex-direction: column; gap: 4px; }
  .rank > .lv { flex: none; }
  .grid { grid-template-columns: repeat(auto-fill, minmax(96px, 1fr)); }
}
</style>
</head>
<body>
<header class="top">
  <h1>Habitants de Valmont <small id="count"></small></h1>
  <div class="controls">
    <input type="search" id="q" placeholder="Chercher un objet ou un habitant…" autocomplete="off">
    <div class="seg" id="mode">
      <button data-v="all" class="on">Tout</button><button data-v="sells">Vend</button><button data-v="buys">Achète</button>
    </div>
    <label class="chk"><input type="checkbox" id="hideOff" checked> Masquer l'inactif sur le serveur</label>
  </div>
</header>
<nav class="npcs" id="nav"></nav>
<main id="list"></main>
<div id="tip"></div>
<script>
const DATA = __DATA__;
const META = __META__;
const RANKS = ["", "Apprenti", "Compagnon", "Artisan", "Expert", "Maître"];
const $ = (s) => document.querySelector(s);
const tip = $("#tip");
const esc = (s) => String(s ?? "").replace(/[&<>"]/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" }[c]));
const fmt = (n) => Number(n).toLocaleString("fr-FR", { maximumFractionDigits: 3 });
const norm = (s) => String(s ?? "").normalize("NFD").replace(/[\u0300-\u036f]/g, "").toLowerCase();
let state = { q: "", mode: "all", hideOff: true };
try { Object.assign(state, JSON.parse(localStorage.getItem("catalogue-pnj") || "{}"), { q: "" }); } catch (e) {}
const save = () => { try { localStorage.setItem("catalogue-pnj", JSON.stringify({ mode: state.mode, hideOff: state.hideOff })); } catch (e) {} };

function slot(it, qty) {
  const initials = it.label.split(/\s+/).map((w) => w[0]).join("").slice(0, 2).toUpperCase();
  const img = it.icon ? `<img src="${it.icon}" alt="" loading="lazy">` : `<div class="ph">${esc(initials)}</div>`;
  const glint = it.enchanted && it.icon ? ` glint" style="--m:url('${it.icon}')` : "";
  return `<div class="slot${glint}">${img}${qty > 1 ? `<span class="qty">${qty}</span>` : ""}</div>`;
}

function tile(it, kind, npc, idx) {
  const off = !it.active ? " off" : "";
  const price = kind === "sells"
    ? `${fmt(it.price)} <span>coins</span>`
    : `${fmt(it.price)} <span>coins / ${it.qty}</span>`;
  const flag = it.gates && it.gates.length ? `<span class="flag" title="${esc(it.gates.join(", "))}"></span>` : "";
  return `<div class="tile${off}" data-k="${kind}" data-n="${npc}" data-i="${idx}">${flag}${slot(it, kind === "sells" ? it.qty : 0)}
    <div class="name">${esc(it.label)}</div><div class="price">${price}</div></div>`;
}

function matches(npc, it) {
  if (!state.q) return true;
  const q = norm(state.q);
  return norm(it.label).includes(q) || norm(it.id).includes(q) || (it.details || []).some((d) => norm(d).includes(q))
    || norm(npc.name).includes(q) || norm(npc.role).includes(q);
}

function render() {
  const out = [], nav = [];
  let shownItems = 0, shownNpcs = 0;
  DATA.forEach((npc, n) => {
    if (state.hideOff && !npc.active) return;
    const sells = npc.sells.map((it, i) => [it, i]).filter(([it]) => (!state.hideOff || it.active) && matches(npc, it));
    const buys = npc.buys.map((it, i) => [it, i]).filter(([it]) => (!state.hideOff || it.active) && matches(npc, it));
    const showS = state.mode !== "buys", showB = state.mode !== "sells";
    const count = (showS ? sells.length : 0) + (showB ? buys.length : 0);
    if (state.q && count === 0) return;
    shownItems += count; shownNpcs++;
    const id = "pnj-" + npc.key;
    nav.push(`<a href="#${id}" style="--c:${npc.color}" class="${npc.active ? "" : "off"}">${esc(npc.name)}</a>`);
    let h = `<section class="npc${npc.active ? "" : " off"}" id="${id}" style="--c:${npc.color}">
      <div class="npc-head"><h2>${esc(npc.name)}</h2><span class="role">${esc(npc.role)}</span>
      <span class="meta">${esc(npc.chapter)}</span>
      ${npc.requiredNames.map((m) => `<span class="badge${npc.active ? "" : " warn"}">requiert ${esc(m)}</span>`).join("")}
      ${npc.optionalNames.map((m) => `<span class="badge">enrichi par ${esc(m)}</span>`).join("")}</div>`;
    if (showS) {
      h += `<h3>Vend <b>${sells.length}</b></h3>`;
      if (!sells.length) h += `<div class="empty">Rien en boutique.</div>`;
      for (let lv = 1; lv <= 5; lv++) {
        const row = sells.filter(([it]) => it.level === lv);
        if (!row.length) continue;
        h += `<div class="rank"><div class="lv"><b>Rang ${lv}</b>${RANKS[lv]}</div><div class="grid">${row.map(([it, i]) => tile(it, "sells", n, i)).join("")}</div></div>`;
      }
    }
    if (showB) {
      h += `<h3>Achète <b>${buys.length}</b></h3>`;
      h += buys.length ? `<div class="grid">${buys.map(([it, i]) => tile(it, "buys", n, i)).join("")}</div>` : `<div class="empty">Ne rachète rien.</div>`;
    }
    out.push(h + `</section>`);
  });
  $("#list").innerHTML = out.join("") || `<div class="empty">Aucun résultat.</div>`;
  $("#nav").innerHTML = nav.join("");
  const plural = (n, w) => `${n} ${w}${n > 1 ? "s" : ""}`;
  $("#count").textContent = `${plural(shownNpcs, "habitant")} · ${plural(shownItems, "objet")}`;
  tip.style.display = "none";
}

function tipHtml(it, kind) {
  const l = [`<div class="t${it.enchanted ? " a" : ""}">${esc(it.label)}</div>`];
  (it.details || []).forEach((d) => l.push(`<div class="g">${esc(d)}</div>`));
  (it.lore || []).forEach((d) => l.push(`<div class="p">${esc(d)}</div>`));
  if (kind === "sells") {
    l.push(`<div class="y">${fmt(it.price)} coins pour ${it.qty}</div>`);
    if (it.qty > 1) l.push(`<div class="g">soit ${fmt(it.price / it.qty)} coins l'unité</div>`);
    if (it.supply) l.push(`<div class="g">Stock propre : ${it.supply[0]} max, +1 toutes les ${it.supply[1]} min</div>`);
    if (it.requires && it.requires.length) l.push(`<div class="g">Ingrédients :</div>` + it.requires.map((r) => `<div class="g">&nbsp;• ${esc(r)}</div>`).join(""));
  } else {
    l.push(`<div class="y">${fmt(it.price)} coins le lot de ${it.qty}</div>`, `<div class="g">soit ${fmt(it.unit)} coins l'unité</div>`);
    if (it.capacity) l.push(`<div class="g">Entrepôt : ${it.capacity}</div>`);
  }
  if (it.gates && it.gates.length) l.push(`<div class="${it.active ? "g" : "r"}">Seulement ${esc(it.gates.join(", "))}${it.active ? "" : " (inactif ici)"}</div>`);
  l.push(`<div class="g" style="opacity:.6">${esc(it.id)}</div>`);
  return l.join("");
}

document.addEventListener("mousemove", (e) => {
  const t = e.target.closest(".tile");
  if (!t) { tip.style.display = "none"; return; }
  const npc = DATA[+t.dataset.n], it = npc[t.dataset.k][+t.dataset.i];
  if (tip.dataset.key !== t.dataset.n + t.dataset.k + t.dataset.i) { tip.innerHTML = tipHtml(it, t.dataset.k); tip.dataset.key = t.dataset.n + t.dataset.k + t.dataset.i; }
  tip.style.display = "block";
  const w = tip.offsetWidth, h = tip.offsetHeight;
  let x = e.clientX + 14, y = e.clientY + 14;
  if (x + w > innerWidth - 8) x = e.clientX - w - 14;
  if (y + h > innerHeight - 8) y = innerHeight - h - 8;
  tip.style.left = Math.max(8, x) + "px"; tip.style.top = Math.max(8, y) + "px";
});

addEventListener("scroll", () => { tip.style.display = "none"; }, { passive: true });
document.addEventListener("mouseleave", () => { tip.style.display = "none"; });
$("#q").addEventListener("input", (e) => { state.q = e.target.value.trim(); render(); });
$("#hideOff").checked = state.hideOff;
$("#hideOff").addEventListener("change", (e) => { state.hideOff = e.target.checked; save(); render(); });
document.querySelectorAll("#mode button").forEach((b) => {
  b.classList.toggle("on", b.dataset.v === state.mode);
  b.addEventListener("click", () => { state.mode = b.dataset.v; save(); document.querySelectorAll("#mode button").forEach((x) => x.classList.toggle("on", x === b)); render(); });
});
render();
</script>
</body>
</html>
"""


def main():
    ap = argparse.ArgumentParser(description="Catalogue HTML des boutiques et rachats des habitants")
    ap.add_argument("--server", default=r"F:\Minecraft Server V2")
    ap.add_argument("--minecraft", default=os.path.expandvars(r"%APPDATA%\.minecraft"))
    ap.add_argument("--config", default=os.path.join(ROOT, "src", "main", "resources", "config.yml"))
    ap.add_argument("--out", default=os.path.join(ROOT, "build", "catalogue-pnj.html"))
    args = ap.parse_args()

    client_jar = os.path.join(args.minecraft, "versions", "1.21.1", "1.21.1.jar")
    mod_jars = sorted(glob.glob(os.path.join(args.server, "mods", "*.jar")))
    print(f"Ressources : client {'OK' if os.path.exists(client_jar) else 'ABSENT'}, {len(mod_jars)} mods")
    assets = Assets([client_jar] + mod_jars)
    present = server_mod_ids(mod_jars)

    labels_path = os.path.join(ROOT, "src", "main", "resources", "integration", "mod-labels.yml")
    plugin_labels = {}
    if os.path.exists(labels_path):
        y = yaml.safe_load(open(labels_path, encoding="utf-8")) or {}
        plugin_labels = {str(k).lower(): v for k, v in (y.get("objets") or {}).items()}
    names = Names(load_vanilla_lang(args.minecraft), load_mod_lang(mod_jars), plugin_labels)

    npcs, order = load_npcs(os.path.join(ROOT, "src", "main", "java", "com", "citycore", "npc", "CityNPC.java"))
    cfg = yaml.safe_load(open(args.config, encoding="utf-8"))
    icons = IconMaker(assets)
    data = build_catalogue(cfg, npcs, order, names, icons, present)

    meta = {"version": cfg.get("config-version")}
    page = (PAGE.replace("__DATA__", json.dumps(data, ensure_ascii=False).replace("</", "<\\/"))
            .replace("__META__", json.dumps(meta)))
    os.makedirs(os.path.dirname(args.out), exist_ok=True)
    with open(args.out, "w", encoding="utf-8") as f:
        f.write(page)
    total = sum(len(n["sells"]) + len(n["buys"]) for n in data)
    print(f"{len(data)} habitants, {total} cases, {len(icons.cache)} icônes "
          f"({len(set(icons.missing))} sans image) → {args.out} ({os.path.getsize(args.out) // 1024} Ko)")
    if icons.missing:
        print("Sans image : " + ", ".join(sorted(set(icons.missing))))


if __name__ == "__main__":
    main()
