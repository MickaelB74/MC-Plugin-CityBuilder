package com.citycore.raid;

import com.citycore.integration.ItemIds;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.entity.AbstractVillager;
import org.bukkit.entity.Ambient;
import org.bukkit.entity.Animals;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Golem;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.WaterMob;
import org.bukkit.generator.structure.GeneratedStructure;
import org.bukkit.generator.structure.StructurePiece;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.BoundingBox;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Balayage d'une cible générée (PLAN_RAIDS §2.7) → {@link Arena}.
 *
 * La boîte et les pièces sont celles que le jeu a enregistrées pour la structure
 * ({@link GeneratedStructure}) : les points remarquables (cloches, bannières, cages…) ne sont
 * cherchés que dans les pièces, jamais dans le terrain autour. Les blocs sont lus dans des
 * instantanés des chunks ({@link ChunkSnapshot}) hors du fil principal ; les entités sont relevées
 * sur le fil principal et classées par clé de type ({@code EntityType#getKey}, valable pour les
 * créatures des mods sous Youer).
 */
public final class ArenaScanner {

    /** Étiquette des défenseurs d'un raid (retirés au nettoyage). */
    public static final String TAG_DEFENDER = "citycore.raid";
    /** Étiquette des civils d'un raid (à épargner). */
    public static final String TAG_CIVIL = "citycore.raid.civil";

    private static final Set<String> GATES = Set.of(
            "valarian_conquest:wooden_gate", "valarian_conquest:gate_entity", "valarian_conquest:drawbridge");
    /** Engins de Valarian Conquest qui ne tirent pas d'eux-mêmes (la baliste, elle, défend). */
    private static final Set<String> ENGINES = Set.of(
            "valarian_conquest:cannon", "valarian_conquest:catapult", "valarian_conquest:battering_ram",
            "valarian_conquest:carriage");
    private static final Set<String> GOLEMS = Set.of("minecraft:iron_golem", "minecraft:snow_golem");
    private static final Set<String> CIVILIANS = Set.of(
            "minecraft:villager", "minecraft:wandering_trader", "minecraft:allay",
            "valarian_conquest:male_citizen", "valarian_conquest:female_citizen");
    /** Créatures des mods de combat : défenseurs même si le serveur hybride ne les voit pas comme monstres. */
    private static final Set<String> HOSTILE_NAMESPACES = Set.of(
            "valarian_conquest", "ethans_custom_mod", "irons_spellbooks", "mowziesmobs");

    /** Catégories de blocs repérés. */
    private enum Poi { BELL, BANNER, SPAWNER, CONTAINER, DOOR, BED, NONE }

    private final JavaPlugin plugin;
    /** Catégorie de chaque bloc, calculée une fois sur le fil principal (les étiquettes de blocs y sont lues). */
    private Map<Material, Poi> poiCache;

    public ArenaScanner(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Lit les pièces de la structure (hors du fil principal), relève les entités de sa boîte, puis
     * rend l'arène sur le fil principal. Les chunks de la boîte doivent être chargés.
     */
    public void scan(String structure, World w, GeneratedStructure generated, Consumer<Arena> done) {
        if (poiCache == null) poiCache = buildPoiCache();
        Map<Material, Poi> pois = poiCache;
        BoundingBox box = generated.getBoundingBox();
        List<BoundingBox> pieces = new ArrayList<>();
        for (StructurePiece piece : generated.getPieces()) pieces.add(piece.getBoundingBox());
        if (pieces.isEmpty()) pieces.add(box);
        Map<Long, ChunkSnapshot> chunks = new HashMap<>();
        for (int cx = (int) Math.floor(box.getMinX()) >> 4; cx <= (int) Math.floor(box.getMaxX()) >> 4; cx++) {
            for (int cz = (int) Math.floor(box.getMinZ()) >> 4; cz <= (int) Math.floor(box.getMaxZ()) >> 4; cz++) {
                if (w.isChunkLoaded(cx, cz)) {
                    chunks.put(Chunk.getChunkKey(cx, cz), w.getChunkAt(cx, cz).getChunkSnapshot(false, false, false));
                }
            }
        }
        int minY = w.getMinHeight(), maxY = w.getMaxHeight();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            // Une erreur ne doit pas laisser l'essai « en cours » : arène sans points remarquables
            Blocks blocks;
            try {
                blocks = read(pieces, chunks, pois, minY, maxY);
            } catch (RuntimeException e) {
                plugin.getLogger().warning("⚔ [Raids] Lecture des blocs de " + structure + " en erreur : " + e);
                blocks = new Blocks();
            }
            Blocks result = blocks;
            Bukkit.getScheduler().runTask(plugin, () -> {
                Arena arena;
                try {
                    arena = finish(structure, w, box, result);
                } catch (RuntimeException e) {
                    plugin.getLogger().warning("⚔ [Raids] Relevé des entités de " + structure + " en erreur : " + e);
                    arena = new Arena(structure, w, box, w.getSpawnLocation(), List.of(), List.of(), List.of(),
                            List.of(), List.of(), 0, List.of(), List.of(), List.of(), List.of(), new LinkedHashMap<>());
                }
                done.accept(arena);
            });
        });
    }

    /** Points remarquables lus dans les pièces. */
    private static final class Blocks {
        final List<int[]> bells = new ArrayList<>(), banners = new ArrayList<>(), spawners = new ArrayList<>(),
                containers = new ArrayList<>(), doors = new ArrayList<>();
        int bedBlocks;
    }

    private static Blocks read(List<BoundingBox> pieces, Map<Long, ChunkSnapshot> chunks, Map<Material, Poi> pois,
                               int minY, int maxY) {
        Blocks b = new Blocks();
        Set<Long> seen = new HashSet<>(); // pièces qui se recouvrent : un bloc compte une fois
        for (BoundingBox piece : pieces) {
            int x0 = (int) Math.floor(piece.getMinX()), x1 = (int) Math.ceil(piece.getMaxX()) - 1;
            int z0 = (int) Math.floor(piece.getMinZ()), z1 = (int) Math.ceil(piece.getMaxZ()) - 1;
            int y0 = Math.max(minY, (int) Math.floor(piece.getMinY())), y1 = Math.min(maxY - 1, (int) Math.ceil(piece.getMaxY()) - 1);
            for (int x = x0; x <= x1; x++) {
                for (int z = z0; z <= z1; z++) {
                    ChunkSnapshot snap = chunks.get(Chunk.getChunkKey(x >> 4, z >> 4));
                    if (snap == null) continue;
                    for (int y = y0; y <= y1; y++) {
                        Material m = snap.getBlockType(x & 15, y, z & 15);
                        Poi poi = pois.get(m);
                        if (poi == null) continue;
                        long key = ((long) x & 0x3FFFFFFL) | (((long) z & 0x3FFFFFFL) << 26) | (((long) y & 0xFFFL) << 52);
                        if (!seen.add(key)) continue;
                        int[] at = {x, y, z};
                        switch (poi) {
                            case BELL -> b.bells.add(at);
                            case BANNER -> b.banners.add(at);
                            case SPAWNER -> b.spawners.add(at);
                            case CONTAINER -> b.containers.add(at);
                            case DOOR -> {
                                // une porte compte deux blocs : on garde celui du bas
                                if (y == minY || snap.getBlockType(x & 15, y - 1, z & 15) != m) b.doors.add(at);
                            }
                            case BED -> b.bedBlocks++;
                            case NONE -> { }
                        }
                    }
                }
            }
        }
        return b;
    }

    /** Arène à partir de la boîte et des blocs, et relevé des entités de la boîte (fil principal). */
    private Arena finish(String structure, World w, BoundingBox box, Blocks b) {
        Location center;
        if (!b.bells.isEmpty()) {
            int[] bell = b.bells.get(0);
            center = new Location(w, bell[0] + 0.5, bell[1], bell[2] + 0.5);
        } else {
            int cx = (int) box.getCenterX(), cz = (int) box.getCenterZ();
            center = new Location(w, cx + 0.5, w.getHighestBlockYAt(cx, cz, HeightMap.MOTION_BLOCKING_NO_LEAVES) + 1, cz + 0.5);
        }

        Map<String, Map<String, Integer>> byKind = new LinkedHashMap<>();
        for (String kind : List.of("défenseurs", "civils", "golems", "portes (entités)", "engins", "animaux", "décor", "autres")) {
            byKind.put(kind, new TreeMap<>());
        }
        List<UUID> defenders = new ArrayList<>(), civilians = new ArrayList<>(), golems = new ArrayList<>(), gates = new ArrayList<>();
        for (Entity e : w.getNearbyEntities(box.clone().expand(2))) {
            if (e instanceof Player || e.hasMetadata("NPC")) continue; // joueurs, PNJ Citizens
            String key = ItemIds.keyOf(e.getType());
            if (key == null) key = e.getType().name().toLowerCase(java.util.Locale.ROOT);
            String kind = kind(e, key);
            byKind.get(kind).merge(key, 1, Integer::sum);
            switch (kind) {
                case "défenseurs" -> { defenders.add(e.getUniqueId()); e.addScoreboardTag(TAG_DEFENDER); }
                case "civils" -> { civilians.add(e.getUniqueId()); e.addScoreboardTag(TAG_CIVIL); }
                case "golems" -> golems.add(e.getUniqueId());
                case "portes (entités)" -> gates.add(e.getUniqueId());
                default -> { }
            }
        }
        return new Arena(structure, w, box, center, locations(w, b.bells), locations(w, b.banners),
                locations(w, b.spawners), locations(w, b.containers), locations(w, b.doors), b.bedBlocks / 2,
                defenders, civilians, golems, gates, byKind);
    }

    /** Catégorie d'une entité de la cible. */
    private static String kind(Entity e, String key) {
        if (GATES.contains(key)) return "portes (entités)";
        if (ENGINES.contains(key)) return "engins";
        if (GOLEMS.contains(key) || (e instanceof Golem && !(e instanceof Enemy))) return "golems";
        if (CIVILIANS.contains(key) || e instanceof AbstractVillager) return "civils";
        if (!(e instanceof LivingEntity) || e instanceof ArmorStand) return "décor";
        if (e instanceof Enemy || e instanceof Monster) return "défenseurs";
        String ns = key.contains(":") ? key.substring(0, key.indexOf(':')) : NamespacedKey.MINECRAFT;
        if (HOSTILE_NAMESPACES.contains(ns)) return "défenseurs";
        if (e instanceof Animals || e instanceof WaterMob || e instanceof Ambient) return "animaux";
        return e instanceof Mob ? "défenseurs" : "autres";
    }

    private static List<Location> locations(World w, List<int[]> points) {
        List<Location> out = new ArrayList<>(points.size());
        for (int[] p : points) out.add(new Location(w, p[0], p[1], p[2]));
        return out;
    }

    /**
     * Catégorie de chaque bloc : étiquettes du jeu pour le jeu de base, clé du bloc pour les mods
     * (« valarian_conquest:red_hanging_banner » est une bannière).
     */
    private static Map<Material, Poi> buildPoiCache() {
        Map<Material, Poi> map = new EnumMap<>(Material.class);
        for (Material m : Material.values()) {
            if (m.isLegacy() || !m.isBlock()) continue;
            Poi poi = Poi.NONE;
            if (m == Material.BELL) poi = Poi.BELL;
            else if (m == Material.SPAWNER) poi = Poi.SPAWNER;
            else if (Tag.BANNERS.isTagged(m)) poi = Poi.BANNER;
            else if (Tag.DOORS.isTagged(m)) poi = Poi.DOOR;
            else if (Tag.BEDS.isTagged(m)) poi = Poi.BED;
            else if (m == Material.CHEST || m == Material.TRAPPED_CHEST || m == Material.BARREL
                    || m == Material.DECORATED_POT || Tag.SHULKER_BOXES.isTagged(m)) poi = Poi.CONTAINER;
            else {
                NamespacedKey key = ItemIds.keyOf(m);
                String path = key == null || NamespacedKey.MINECRAFT.equals(key.getNamespace()) ? null : key.getKey();
                if (path != null && path.contains("banner")) poi = Poi.BANNER;
                else if (path != null && (path.endsWith("_door") || path.equals("door"))) poi = Poi.DOOR;
            }
            if (poi != Poi.NONE) map.put(m, poi);
        }
        return map;
    }
}
