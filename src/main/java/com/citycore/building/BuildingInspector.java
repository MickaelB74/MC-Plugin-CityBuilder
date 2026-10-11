package com.citycore.building;

import com.citycore.npc.CityNPC;
import org.bukkit.Bukkit;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Bed;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Hanging;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Inspection des constructions (phase 2 du plan « Valmont vivante »).
 *
 * Lit les blocs d'un bâtiment (copie des chunks : rapide, sans objet Block par bloc), vérifie ses
 * exigences (blocs requis du type ou de l'artisan, porte, toit, lumière) et calcule son niveau :
 * 0 = incomplet, 1 = conforme, 2 à 5 selon les points de détail (variété des blocs, décorations,
 * blocs bonus du métier, surface couverte). Les résultats sont gardés en cache ; un bloc posé ou
 * cassé dans un bâtiment relance son inspection une demi-seconde plus tard. Pas de message à
 * chaque bloc : seul le joueur qui a fait changer le niveau est prévenu (titre à la montée,
 * barre d'action à la baisse) ; le détail est dans l'écran « Son atelier » de l'artisan. Le
 * niveau de l'atelier d'un artisan agrandit son entrepôt (voir {@link #capacityMultiplier(CityNPC)}).
 */
public class BuildingInspector implements Listener {

    /** Délai entre une modification d'un bâtiment et sa nouvelle inspection (regroupe les poses rapides). */
    private static final long RECHECK_DELAY_TICKS = 10L;

    private final JavaPlugin           plugin;
    private final BuildingManager      buildings;
    private final BuildingRequirements requirements;

    /** Résultats par bâtiment, puis par exigences appliquées (son type, ou un artisan candidat). */
    private final Map<Integer, Map<String, Inspection>> cache = new HashMap<>();
    /** Niveau de l'atelier de chaque artisan installé (tag → niveau), pour la capacité de l'entrepôt. */
    private final Map<String, Integer> npcLevels = new HashMap<>();
    private final Map<Integer, BukkitTask> pending = new HashMap<>();
    /** Dernier joueur qui a modifié chaque bâtiment : lui seul est prévenu d'un changement de niveau. */
    private final Map<Integer, UUID> actors = new HashMap<>();

    private List<Building> known = List.of(); // bâtiments connus (repérage des blocs modifiés)
    private long knownAt;

    private static final Set<Material> LIGHTS  = lights();
    private static final Set<Material> NATURAL = natural();

    public BuildingInspector(JavaPlugin plugin, BuildingManager buildings, BuildingRequirements requirements) {
        this.plugin       = plugin;
        this.buildings    = buildings;
        this.requirements = requirements;
        // Niveaux enregistrés à la dernière inspection : l'entrepôt garde son bonus après un redémarrage
        for (Building b : buildings.getAllBuildings()) {
            if (b.npcTag() != null) npcLevels.put(b.npcTag(), b.level());
        }
    }

    public BuildingRequirements requirements() {
        return requirements;
    }

    /* =========================
       INSPECTION
       ========================= */

    /** Inspection d'un bâtiment selon ses propres exigences (type, ou artisan de l'atelier). */
    public Inspection inspect(Building b) {
        return inspect(b, requirements.specFor(b), BuildingCreation.label(b.type(), b.reservedNpc()));
    }

    /** Inspection d'un atelier selon les exigences d'un artisan qui pourrait s'y installer. */
    public Inspection inspectFor(Building b, CityNPC npc) {
        return inspect(b, requirements.specFor(npc), "§6Atelier de " + npc.displayName);
    }

    /** Résultat en cache, sinon inspection (charge les chunks du bâtiment au besoin). */
    private Inspection inspect(Building b, BuildingRequirements.Spec spec, String target) {
        Map<String, Inspection> byBuilding = cache.computeIfAbsent(b.id(), k -> new HashMap<>());
        Inspection cached = byBuilding.get(spec.key());
        if (cached != null) return cached;
        Inspection result = scan(b, spec, target);
        byBuilding.put(spec.key(), result);
        // Exigences propres au bâtiment (et non celles d'un candidat) : son niveau est retenu
        if (spec.key().equals(requirements.specFor(b).key())) remember(b, result);
        return result;
    }

    /**
     * Résultat sans charger de chunk : en cache, ou inspecté si toute la zone est chargée,
     * sinon null (listes d'Alderic : on n'impose pas le chargement de toute la ville).
     */
    public Inspection peek(Building b) {
        Map<String, Inspection> byBuilding = cache.get(b.id());
        BuildingRequirements.Spec spec = requirements.specFor(b);
        if (byBuilding != null && byBuilding.containsKey(spec.key())) return byBuilding.get(spec.key());
        World world = Bukkit.getWorld(b.world());
        if (world == null) return null;
        for (int cx = b.x1() >> 4; cx <= b.x2() >> 4; cx++) {
            for (int cz = b.z1() >> 4; cz <= b.z2() >> 4; cz++) {
                if (!world.isChunkLoaded(cx, cz)) return null;
            }
        }
        return inspect(b);
    }

    /** Oublie les résultats d'un bâtiment (bloc modifié, type changé, bâtiment supprimé). */
    public void invalidate(int buildingId) {
        cache.remove(buildingId);
    }

    /** Garde le niveau d'un bâtiment (base de données, entrepôt de son artisan). */
    private void remember(Building b, Inspection result) {
        if (b.level() != result.level()) buildings.setLevel(b.id(), result.level());
        if (b.npcTag() != null) npcLevels.put(b.npcTag(), result.level());
    }

    /** Un artisan vient de s'installer : son entrepôt suit désormais le niveau de cet atelier. */
    public void onAssigned(CityNPC npc, Building b) {
        invalidate(b.id());
        Building fresh = buildings.getById(b.id());
        if (fresh != null) npcLevels.put(npc.tag, inspect(fresh).level());
    }

    /** Un artisan a perdu son atelier : plus de bonus d'entrepôt. */
    public void onEvicted(String npcTag) {
        npcLevels.remove(npcTag);
    }

    /** Multiplicateur de capacité de l'entrepôt d'un artisan (1 sans atelier ou atelier incomplet). */
    public double capacityMultiplier(CityNPC npc) {
        Integer level = npcLevels.get(npc.tag);
        return level == null ? 1.0 : requirements.storageMultiplier(level);
    }

    /** Niveau connu de l'atelier d'un artisan (null s'il n'en a pas). */
    public Integer atelierLevel(CityNPC npc) {
        return npcLevels.get(npc.tag);
    }

    /** Inspection à jour (blocs relus) de l'atelier d'un artisan, sans notification ; null s'il n'en a pas. */
    public Inspection freshAtelierInspection(CityNPC npc) {
        Building b = buildings.getAssignedBuilding(npc.tag);
        return b == null ? null : recheck(b.id(), null);
    }

    /** Inspection de l'atelier où l'artisan est installé (null s'il n'en a pas). */
    public Inspection atelierInspection(CityNPC npc) {
        Building b = buildings.getAssignedBuilding(npc.tag);
        return b == null ? null : inspect(b);
    }

    /** Lignes de bulle sur l'atelier d'un artisan installé : nom, niveau, bonus d'entrepôt ou manques. */
    public List<String> atelierLore(CityNPC npc) {
        Inspection insp = atelierInspection(npc);
        if (insp == null) return List.of();
        List<String> lines = new ArrayList<>();
        lines.add("§7Atelier : §f" + insp.building().name() + " §8— " + insp.levelLabel());
        if (!insp.compliant()) {
            lines.add("§c⚠ Il manque : §f" + insp.missing());
        } else if (insp.level() > 1) {
            lines.add("§7Entrepôt : §a+" + Math.round((requirements.storageMultiplier(insp.level()) - 1) * 100) + " %");
        }
        return lines;
    }

    /** Ce que demande l'atelier d'un artisan, en une phrase (dialogue, piste de recrutement). */
    public String requestSentence(CityNPC npc) {
        BuildingRequirements.Spec spec = requirements.specFor(npc);
        String blocks  = BuildingRequirements.sentence(spec.required());
        String general = BuildingRequirements.generalSentence(spec);
        if (blocks.isEmpty()) return general;
        return general.isEmpty() ? blocks : blocks + ", avec " + general;
    }

    /* =========================
       LECTURE DES BLOCS
       ========================= */

    private Inspection scan(Building b, BuildingRequirements.Spec spec, String target) {
        World world = Bukkit.getWorld(b.world());
        if (world == null) {
            return new Inspection(b, target, List.of(new Inspection.Check("monde « " + b.world() + " » introuvable",
                    false, "", Material.BARRIER)), List.of(), 0, 0, 0, 0, 0, 0, 0, -1);
        }
        // Hauteur : estimée une fois si le bâtiment n'en a pas encore
        int yMin, yMax;
        if (b.hasHeight()) {
            yMin = b.yMin();
            yMax = b.yMax();
        } else {
            int[] h = BuildingHeights.estimate(world, b.x1(), b.z1(), b.x2(), b.z2(), null, null);
            buildings.setHeight(b.id(), h[0], h[1]);
            yMin = h[0];
            yMax = h[1];
        }
        yMin = Math.max(world.getMinHeight(), yMin);
        yMax = Math.min(world.getMaxHeight() - 1, Math.max(yMin, yMax));

        // Copie des chunks couverts par la zone (lecture rapide)
        Map<Long, ChunkSnapshot> snapshots = new HashMap<>();
        for (int cx = b.x1() >> 4; cx <= b.x2() >> 4; cx++) {
            for (int cz = b.z1() >> 4; cz <= b.z2() >> 4; cz++) {
                snapshots.put(key(cx, cz), world.getChunkAt(cx, cz).getChunkSnapshot(false, false, false));
            }
        }

        Map<Material, Integer> counts = new HashMap<>();
        int covered = 0, open = 0;
        for (int x = b.x1(); x <= b.x2(); x++) {
            for (int z = b.z1(); z <= b.z2(); z++) {
                ChunkSnapshot snap = snapshots.get(key(x >> 4, z >> 4));
                int lx = x & 15, lz = z & 15;
                Material[] column = new Material[yMax - yMin + 1];
                for (int y = yMin; y <= yMax; y++) {
                    Material m = snap.getBlockType(lx, y, lz);
                    column[y - yMin] = m;
                    if (m.isAir()) continue;
                    // Lits et portes occupent deux blocs : on compte la tête du lit et le bas de la porte
                    if (Tag.BEDS.isTagged(m) || Tag.DOORS.isTagged(m)) {
                        BlockData data = snap.getBlockData(lx, y, lz);
                        if (data instanceof Bed bed && bed.getPart() != Bed.Part.HEAD) continue;
                        if (data instanceof Bisected half && half.getHalf() != Bisected.Half.BOTTOM) continue;
                    }
                    counts.merge(m, 1, Integer::sum);
                }
                // Toit : au point le plus bas où l'on peut se tenir, y a-t-il un bloc solide au-dessus ?
                int spot = -1;
                for (int i = 1; i + 1 < column.length; i++) {
                    if (solid(column[i - 1]) && !column[i].isSolid() && !column[i + 1].isSolid()) { spot = i; break; }
                }
                if (spot < 0) continue; // mur, pilier, sol plein : ni dedans ni dehors
                boolean roofed = false;
                for (int i = spot + 2; i < column.length && !roofed; i++) roofed = solid(column[i]);
                if (roofed) covered++; else open++;
            }
        }

        // Objets accrochés et porte-armures : des décorations
        BoundingBox box = new BoundingBox(b.x1(), yMin, b.z1(), b.x2() + 1, yMax + 1, b.z2() + 1);
        int hanging = 0;
        for (Entity e : world.getNearbyEntities(box)) if (e instanceof Hanging || e instanceof ArmorStand) hanging++;

        // Exigences
        List<Inspection.Check> checks = new ArrayList<>();
        for (BuildingRequirements.Need n : spec.required()) {
            int have = sum(counts, n.materials());
            checks.add(new Inspection.Check(n.label(), have >= n.count(), have + "/" + n.count(), icon(n.materials())));
        }
        if (spec.doors() > 0) {
            int doors = sum(counts, Tag.DOORS.getValues());
            checks.add(new Inspection.Check(spec.doors() == 1 ? "une porte" : spec.doors() + " portes",
                    doors >= spec.doors(), doors + "/" + spec.doors(), Material.OAK_DOOR));
        }
        int roofPercent = covered + open == 0 ? 0 : Math.round(100f * covered / (covered + open));
        if (spec.roofPercent() > 0) {
            checks.add(new Inspection.Check("un toit", roofPercent >= spec.roofPercent(),
                    roofPercent + " % du sol couvert, " + spec.roofPercent() + " % requis", Material.SPRUCE_STAIRS));
        }
        if (spec.lights() > 0) {
            int lights = sum(counts, LIGHTS);
            checks.add(new Inspection.Check(spec.lights() + " source" + (spec.lights() > 1 ? "s" : "") + " de lumière",
                    lights >= spec.lights(), lights + "/" + spec.lights(), Material.LANTERN));
        }
        boolean compliant = checks.stream().allMatch(Inspection.Check::ok);

        // Points de détail
        BuildingRequirements.Points p = requirements.points();
        long distinct = counts.keySet().stream().filter(m -> !NATURAL.contains(m)).count();
        int variety = (int) Math.min(p.varietyMax(), distinct * p.variety());
        int decor = hanging;
        for (Material m : requirements.decorations()) decor += Math.min(p.decorationPerType(), counts.getOrDefault(m, 0));
        decor = Math.min(p.decorationMax(), decor * p.decoration());
        int bonus = 0;
        List<Inspection.Check> bonusChecks = new ArrayList<>();
        for (BuildingRequirements.Need n : spec.bonus()) {
            boolean present = sum(counts, n.materials()) > 0;
            if (present) bonus += p.bonus();
            bonusChecks.add(new Inspection.Check(n.name(), present, present ? "+" + p.bonus() + " pts" : "", icon(n.materials())));
        }
        int surface = Math.min(p.surfaceMax(), covered / 10 * p.surface());
        int points = variety + decor + bonus + surface;

        int level = 0, floor = 0, next = -1;
        if (compliant) {
            level = 1;
            for (Map.Entry<Integer, Integer> e : requirements.levels().entrySet()) {
                if (points >= e.getValue()) level = Math.max(level, e.getKey());
            }
            level = Math.min(5, level);
            Integer reached = requirements.levels().get(level);
            floor = reached == null ? 0 : reached;
            Map.Entry<Integer, Integer> nextStep = requirements.levels().higherEntry(level);
            next = nextStep == null || level >= 5 ? -1 : nextStep.getValue();
        }
        return new Inspection(b, target, List.copyOf(checks), List.copyOf(bonusChecks), level, points,
                variety, decor, bonus, surface, floor, next);
    }

    /**
     * Icône d'un groupe de blocs : l'objet au nom le plus court (« ANVIL », « RED_BED »,
     * « CANDLE »…) ; un bloc sans objet (eau, chaudron de lave) prend son seau.
     */
    private static Material icon(Set<Material> materials) {
        Material best = null;
        for (Material m : materials) {
            Material item = m.isItem() ? m : m == Material.WATER ? Material.WATER_BUCKET
                    : m == Material.LAVA_CAULDRON ? Material.LAVA_BUCKET : null;
            if (item == null) continue;
            if (best == null || item.name().length() < best.name().length()
                    || (item.name().length() == best.name().length() && item.name().compareTo(best.name()) < 0)) {
                best = item;
            }
        }
        return best != null ? best : Material.PAPER;
    }

    private static boolean solid(Material m) {
        return m.isSolid() && !Tag.LEAVES.isTagged(m);
    }

    private static int sum(Map<Material, Integer> counts, Set<Material> materials) {
        int total = 0;
        for (Material m : materials) total += counts.getOrDefault(m, 0);
        return total;
    }

    private static long key(int cx, int cz) {
        return ((long) cx << 32) ^ (cz & 0xffffffffL);
    }

    /* =========================
       NOUVELLE INSPECTION APRÈS UNE MODIFICATION
       ========================= */

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) { touched(event.getBlock(), event.getPlayer()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) { touched(event.getBlock(), event.getPlayer()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) { touched(event.getBlock(), event.getPlayer()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) { touched(event.getBlock(), event.getPlayer()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHangingPlace(HangingPlaceEvent event) {
        touched(event.getEntity().getLocation().getBlock(), event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakEvent event) {
        Player remover = event instanceof org.bukkit.event.hanging.HangingBreakByEntityEvent byEntity
                && byEntity.getRemover() instanceof Player p ? p : null;
        touched(event.getEntity().getLocation().getBlock(), remover);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityPlace(EntityPlaceEvent event) {
        touched(event.getEntity().getLocation().getBlock(), event.getPlayer());
    }

    /**
     * Un bloc a changé : si c'est dans un bâtiment, nouvelle inspection une demi-seconde plus
     * tard (plusieurs blocs posés d'affilée ne coûtent qu'une lecture). Le joueur qui a modifié
     * le bâtiment sera prévenu s'il change de niveau.
     */
    private void touched(Block block, Player actor) {
        Building b = buildingAt(block);
        if (b == null) return;
        int id = b.id();
        if (actor != null) actors.put(id, actor.getUniqueId());
        if (pending.containsKey(id)) return;
        pending.put(id, Bukkit.getScheduler().runTaskLater(plugin, () -> {
            pending.remove(id);
            UUID who = actors.remove(id);
            recheck(id, who == null ? null : Bukkit.getPlayer(who));
        }, RECHECK_DELAY_TICKS));
    }

    private Building buildingAt(Block block) {
        long now = System.currentTimeMillis();
        if (now - knownAt > 5000) {
            known = buildings.getAllBuildings();
            knownAt = now;
        }
        String world = block.getWorld().getName();
        for (Building b : known) {
            if (!b.world().equals(world)) continue;
            if (block.getX() < b.x1() || block.getX() > b.x2() || block.getZ() < b.z1() || block.getZ() > b.z2()) continue;
            if (b.hasHeight() && (block.getY() < b.yMin() - 1 || block.getY() > b.yMax() + 1)) continue;
            return b;
        }
        return null;
    }

    /** Les bâtiments ont changé (création, suppression, type) : la liste de repérage est relue. */
    public void refreshBuildings() {
        knownAt = 0;
    }

    /**
     * Nouvelle inspection d'un bâtiment. Pas de message à chaque bloc : seul le joueur donné est
     * prévenu, et seulement si le niveau change (titre et son quand il monte, barre d'action
     * quand il baisse). Le détail se consulte dans le menu de l'artisan (« Son atelier ») ou avec
     * /city build inspect.
     * @param actor joueur à prévenir d'un changement de niveau (null = personne)
     * @return le nouveau résultat, ou null si le bâtiment n'existe plus
     */
    public Inspection recheck(int buildingId, Player actor) {
        Building b = buildings.getById(buildingId);
        invalidate(buildingId);
        if (b == null) return null;
        int before = b.level();
        Inspection now = inspect(b);
        if (now.level() != before) {
            plugin.getLogger().info(b.name() + " : niveau " + before + " → " + now.level());
            if (actor != null && actor.isOnline()) notifyLevel(actor, b, before, now);
        }
        return now;
    }

    /** Montée de niveau : titre + son ; baisse : simple barre d'action (aucun message dans le chat). */
    private static void notifyLevel(Player player, Building b, int before, Inspection now) {
        if (now.level() > before) {
            String title = before == 0 ? "§a" + b.name() + " est conforme" : "§6" + b.name();
            player.sendTitle(title, now.levelLabel(), 5, 50, 15);
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.2f);
        } else {
            com.citycore.util.TypewriterUtil.actionBar(player, now.compliant()
                    ? "§e" + b.name() + " §7: niveau " + before + " → " + now.level() + " " + Inspection.stars(now.level())
                    : "§c⚠ " + b.name() + " : incomplet §7— il manque §f" + now.missing());
        }
    }

    /* =========================
       RAPPORT DANS LE CHAT
       ========================= */

    /** Rapport complet d'une inspection (commande /city build inspect, création, changement de type). */
    public static void report(Player player, Inspection insp) {
        Building b = insp.building();
        player.sendMessage("§6🏗 Inspection de §e" + b.name() + " §7(" + insp.target() + "§7)");
        for (String line : insp.lore()) player.sendMessage("  " + line);
    }

    /* =========================
       ENSEMBLES DE BLOCS
       ========================= */

    /** Blocs qui éclairent (torches, lanternes, bougies, feux de camp, lampes…). */
    private static Set<Material> lights() {
        Set<Material> set = new HashSet<>();
        for (String name : List.of("TORCH", "WALL_TORCH", "SOUL_TORCH", "SOUL_WALL_TORCH", "REDSTONE_TORCH",
                "REDSTONE_WALL_TORCH", "LANTERN", "SOUL_LANTERN", "GLOWSTONE", "SEA_LANTERN", "SHROOMLIGHT",
                "JACK_O_LANTERN", "END_ROD", "REDSTONE_LAMP", "BEACON", "CONDUIT", "LIGHT", "OCHRE_FROGLIGHT",
                "VERDANT_FROGLIGHT", "PEARLESCENT_FROGLIGHT", "COPPER_BULB", "EXPOSED_COPPER_BULB",
                "WEATHERED_COPPER_BULB", "OXIDIZED_COPPER_BULB", "WAXED_COPPER_BULB", "WAXED_EXPOSED_COPPER_BULB",
                "WAXED_WEATHERED_COPPER_BULB", "WAXED_OXIDIZED_COPPER_BULB")) {
            Material m = Material.matchMaterial(name);
            if (m != null) set.add(m);
        }
        set.addAll(tag("candles"));
        set.addAll(tag("campfires"));
        return set;
    }

    /** Blocs naturels : ne comptent pas dans la variété (terre, pierre, sable, eau, feuillage…). */
    private static Set<Material> natural() {
        Set<Material> set = new HashSet<>();
        for (String name : List.of("AIR", "CAVE_AIR", "VOID_AIR", "WATER", "LAVA", "BEDROCK", "GRAVEL", "CLAY",
                "SNOW", "SNOW_BLOCK", "ICE", "SHORT_GRASS", "GRASS", "TALL_GRASS", "FERN", "LARGE_FERN", "DEAD_BUSH",
                "VINE", "SEAGRASS", "TALL_SEAGRASS", "KELP", "KELP_PLANT", "SUGAR_CANE", "MOSS_BLOCK", "MOSS_CARPET",
                "SWEET_BERRY_BUSH", "PINK_PETALS", "LILY_PAD", "BROWN_MUSHROOM", "RED_MUSHROOM")) {
            Material m = Material.matchMaterial(name);
            if (m != null) set.add(m);
        }
        for (String tag : List.of("dirt", "base_stone_overworld", "base_stone_nether", "sand", "leaves", "flowers",
                "saplings", "coal_ores", "iron_ores", "copper_ores", "gold_ores", "redstone_ores", "lapis_ores",
                "diamond_ores", "emerald_ores")) {
            set.addAll(tag(tag));
        }
        return set;
    }

    private static Set<Material> tag(String name) {
        Tag<Material> tag = Bukkit.getTag(Tag.REGISTRY_BLOCKS, NamespacedKey.minecraft(name.toLowerCase(Locale.ROOT)), Material.class);
        return tag == null ? Set.of() : tag.getValues();
    }
}
