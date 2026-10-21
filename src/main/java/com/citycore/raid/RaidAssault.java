package com.citycore.raid;

import com.citycore.integration.ItemIds;
import com.citycore.siege.EnemyArmy;
import com.citycore.siege.SiegeConfig;
import com.citycore.util.GroundFinder;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Allay;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BellRingEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.BlockInventoryHolder;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.loot.Lootable;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.BoundingBox;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * L'assaut d'une expédition (PLAN_RAIDS §5, phases R4 et R5) : défenseurs plus ou moins nombreux
 * et forts selon le niveau, objectifs qui font tomber la défiance de l'adversaire, et ce qui en
 * retire le butin (§6.3).
 *
 * <b>Défenseurs.</b> Les occupants hostiles de la structure (étiquetés au balayage) reçoivent la
 * force du niveau ; s'ils sont trop peu, le plugin complète jusqu'au nombre du niveau avec des
 * créatures du même genre. Une cible « armée » (village occupé, camp de tentes) reçoit une
 * garnison de soldats d'une faction des sièges (PNJ de l'armée ennemie, qui tiennent un poste) ;
 * une cible « créatures », les créatures de raids.yml. Nombre : défenseurs du niveau × (1 +
 * par-joueur × (joueurs - 1)), au plus max vivants ; la renommée du niveau les renforce encore.
 * Un chef (objectif obligatoire) aux niveaux qui en ont un.
 *
 * <b>Objectifs</b> (§5.2) : garnison, cloche (place dégagée), bannières, cages (niveau IV et plus,
 * repaires), captifs (allays), chef, maître du repaire (R8), navire abordé (R7), place tenue. Ceux
 * dont la cible n'a pas l'élément sont retirés ; la porte (entités de porte des mods, cassables ou
 * non : à vérifier) n'est pas suivie.
 */
final class RaidAssault implements Listener {

    private final JavaPlugin plugin;
    private final RaidManager raids;
    /** Armée ennemie des raids (instance à part de celle des sièges), créée au premier besoin. */
    private Supplier<EnemyArmy> armyFactory = () -> null;
    private Supplier<SiegeConfig> siegeConfig = () -> null;
    private EnemyArmy army;
    /** Positions des bannières et cages de la cible en cours (« x,y,z »). */
    private final Set<String> banners = new HashSet<>();
    private final Set<String> spawners = new HashSet<>();
    /** Palier des sièges correspondant à chaque niveau de raid (composition de l'armée). */
    private static final int[] SIEGE_TIER = {1, 2, 4, 5, 7, 8, 10};
    /** Assaut en cours : qui ajouter, force du niveau et facteur de vie (renforts). */
    private Recruit recruit;
    private RaidConfig.Strength strength;
    private double health = 1.0;

    RaidAssault(JavaPlugin plugin, RaidManager raids) {
        this.plugin = plugin;
        this.raids  = raids;
    }

    void setArmy(Supplier<EnemyArmy> factory, Supplier<SiegeConfig> config) {
        this.armyFactory = factory;
        this.siegeConfig = config;
    }

    private EnemyArmy army() {
        if (army == null) army = armyFactory.get();
        return army;
    }

    /* =========================
       PRÉPARATION (avant le rassemblement)
       ========================= */

    /**
     * Le site est prêt : coffres, tonneaux et pots vidés (tables de butin retirées), cadres et
     * porte-armures vidés, golems retirés ; cages remplacées par de l'air sous le niveau IV (sauf
     * repaires) — elles ne sont un objectif qu'à partir de là.
     */
    void prepare(RaidRun r) {
        Arena a = r.arena;
        World w = a.world();
        for (Location c : a.containers()) {
            BlockState st = c.getBlock().getState();
            if (st instanceof Lootable l && l.getLootTable() != null) {
                l.setLootTable(null);
                st.update(true, false);
            }
            if (c.getBlock().getState() instanceof BlockInventoryHolder h) h.getInventory().clear();
        }
        for (UUID g : a.golems()) {
            Entity e = Bukkit.getEntity(g);
            if (e != null) e.remove();
        }
        // « remplacer » (raids.yml) : l'équipage ou les habitants d'origine laissent la place à la
        // faction de la cible (navire pirate aux villageois du mod…)
        if (r.target.defenders().replace()) {
            List<UUID> originals = new ArrayList<>(a.defenders());
            originals.addAll(a.civilians());
            for (UUID id : originals) {
                Entity e = Bukkit.getEntity(id);
                if (e != null) e.remove();
            }
        }
        for (Entity e : w.getNearbyEntities(a.box())) {
            if (e instanceof ItemFrame f) f.setItem(null);
            else if (e instanceof ArmorStand s && s.getEquipment() != null) s.getEquipment().clear();
        }
        banners.clear();
        spawners.clear();
        for (Location b : a.banners()) banners.add(key(b));
        boolean cagesCount = r.level.number() >= 4 || r.target.type() == RaidTarget.Type.REPAIRE;
        for (Location s : a.spawners()) {
            if (cagesCount) spawners.add(key(s));
            else s.getBlock().setType(Material.AIR, false);
        }
    }

    /* =========================
       DÉPART : DÉFENSEURS ET OBJECTIFS
       ========================= */

    /** Les joueurs partent : défenseurs mis en place (force du niveau), objectifs posés. */
    void begin(RaidRun r, int players, int victories) {
        RaidConfig c = raids.config();
        r.renown   = Math.min(c.renownMax, victories * c.renownPerVictory);
        r.strength = Math.min(c.strengthMax, victories * c.strengthPerVictory);
        RaidConfig.Strength st = c.strength(r.level.number());
        double health = st.health() * (1 + r.strength);
        World w = r.arena.world();

        // Occupants hostiles de la structure : force du niveau
        List<LivingEntity> natural = new ArrayList<>();
        String masterType = r.target.defenders().master();
        for (UUID id : r.arena.defenders()) {
            if (Bukkit.getEntity(id) instanceof LivingEntity e && e.isValid() && !e.isDead()) {
                // Le maître (boss) garde sa vie de boss ; les autres prennent la force du niveau
                boolean master = masterType != null && masterType.equalsIgnoreCase(ItemIds.keyOf(e.getType()));
                if (master) strengthen(e, new RaidConfig.Strength(1.0, 1.0, "aucune"), 1.0);
                else strengthen(e, st, health);
                natural.add(e);
                r.defenders.add(id);
            }
        }
        // Nombre voulu : défenseurs du niveau, plus nombreux avec les joueurs (la renommée les rend
        // plus coriaces, pas plus nombreux)
        int[] range = r.level.defenders();
        double scale = 1 + c.perPlayer * Math.max(0, players - 1);
        int minimum = (int) Math.round(range[0] * scale);
        int wanted = (int) Math.round(ThreadLocalRandom.current().nextInt(range[0], Math.max(range[0], range[1]) + 1) * scale);
        RaidTarget.Defenders d = r.target.defenders();
        int goal = d.source().equals("gabarit") && !d.complement() ? minimum : wanted;
        // Ville fortifiée : une garnison par tour et la garde du siège, prises sur le maximum vivant
        int perTower = 3 + r.level.number() / 2, seatGuards = 4;
        int reserve = r.city == null ? 0 : r.city.towers().size() * perTower + (r.city.seat() != null ? seatGuards : 0);
        int toAdd = Math.max(0, Math.min(c.maxAlive - reserve, goal) - natural.size());

        Recruit recruit = recruit(r, natural);
        if (recruit.soldiers()) {
            r.faction = siegeConfig.get().randomFaction();
        }
        this.recruit  = recruit;
        this.strength = st;
        this.health   = health;
        for (int i = 0; i < toAdd; i++) {
            Location post = post(r);
            if (post == null) break;
            LivingEntity e = spawn(r, recruit, post, st, health, false);
            if (e != null) r.defenders.add(e.getUniqueId());
        }
        if (r.city != null) garrisonCity(r, recruit, st, health, perTower, seatGuards);
        // Chef (objectif obligatoire) : la créature nommée par la cible, sinon un champion
        if (r.target.objectives().contains("chef")) placeChief(r, natural, recruit, st, health);
        // Maître du repaire (objectif obligatoire) : celui de la structure, sinon posé au cœur
        if (r.target.objectives().contains("maitre") || r.target.defenders().master() != null) placeMaster(r, natural, recruit);
        r.defendersTotal = r.defenders.size();
        buildObjectives(r);
        plugin.getLogger().info("⚔ [Raids] Assaut contre " + r.target.name() + " : " + natural.size() + " occupant(s) de la"
                + " structure, " + (r.defendersTotal - natural.size()) + " ajouté(s) (" + recruit.describe() + "), force × "
                + String.format(java.util.Locale.ROOT, "%.2f", health) + (r.chief != null ? ", chef « " + r.chiefName + " »" : "") + ".");
    }

    /**
     * Qui ajouter : soldats d'une faction (cible « armée », ou complément d'une cible habitée par
     * des humains sans occupant hostile), créatures de raids.yml, ou créatures du même genre que
     * les occupants déjà là.
     *
     * @param types créatures (identifiants) quand ce ne sont pas des soldats
     */
    private record Recruit(boolean soldiers, List<String> types) {
        String describe() {
            return soldiers ? "soldats d'une faction" : String.join(", ", types);
        }
    }

    private Recruit recruit(RaidRun r, List<LivingEntity> natural) {
        RaidTarget.Defenders d = r.target.defenders();
        boolean armyReady = army() != null && siegeConfig.get() != null;
        if (d.source().equals("creatures") && !d.creatures().isEmpty()) return new Recruit(false, d.creatures());
        if (d.source().equals("armee")) {
            return armyReady ? new Recruit(true, List.of()) : new Recruit(false, List.of("minecraft:pillager", "minecraft:vindicator"));
        }
        // Complément d'un gabarit : du même genre que les occupants les plus nombreux — jamais le
        // maître, le chef ni un boss (le vaisseau ancien ne compte qu'un Warden : pas quinze)
        Map<String, Integer> count = new HashMap<>();
        for (LivingEntity e : natural) {
            String k = ItemIds.keyOf(e.getType());
            if (k == null || k.equalsIgnoreCase(d.master()) || k.equalsIgnoreCase(d.chief()) || bossLike(e)) continue;
            count.merge(k, 1, Integer::sum);
        }
        String common = count.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
        if (common != null) return new Recruit(false, List.of(common));
        if (r.target.type() == RaidTarget.Type.REPAIRE) return new Recruit(false, List.of("minecraft:zombie", "minecraft:skeleton"));
        return armyReady ? new Recruit(true, List.of()) : new Recruit(false, List.of("minecraft:pillager", "minecraft:vindicator"));
    }

    /**
     * Boss ou créature de taille de boss (Warden, Wither, gardien ancien, ravageur, boss des mods
     * à plus de 60 points de vie) : jamais pris comme modèle du complément.
     */
    private static boolean bossLike(LivingEntity e) {
        if (e instanceof org.bukkit.entity.Boss || e instanceof org.bukkit.entity.Warden
                || e instanceof org.bukkit.entity.ElderGuardian || e instanceof org.bukkit.entity.Ravager) return true;
        AttributeInstance max = e.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        return max != null && max.getBaseValue() > 60;
    }

    /** Un défenseur ajouté à son poste. */
    private LivingEntity spawn(RaidRun r, Recruit recruit, Location post, RaidConfig.Strength st, double health, boolean champion) {
        if (recruit.soldiers()) return soldier(r, post, post, champion);
        String id = recruit.types().get(ThreadLocalRandom.current().nextInt(recruit.types().size()));
        EntityType type = ItemIds.resolveEntity(id);
        if (type == null || !type.isSpawnable()) type = EntityType.PILLAGER;
        Entity e;
        try {
            e = post.getWorld().spawnEntity(post, type);
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("⚔ [Raids] Créature " + id + " impossible à faire apparaître : " + ex.getMessage());
            return null;
        }
        if (!(e instanceof LivingEntity body)) {
            e.remove();
            return null;
        }
        strengthen(body, st, champion ? health * 2 : health); // chef : deux fois la vie d'un défenseur
        return body;
    }

    /** Force du niveau : vie, dégâts, armure si elle n'en a pas ; ni butin, ni départ, ni ramassage. */
    private static void strengthen(LivingEntity e, RaidConfig.Strength st, double health) {
        AttributeInstance max = e.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (max != null && health != 1.0) {
            max.setBaseValue(max.getBaseValue() * health);
            e.setHealth(Math.min(max.getValue(), max.getBaseValue()));
        }
        AttributeInstance atk = e.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE);
        if (atk != null && st.damage() != 1.0) atk.setBaseValue(atk.getBaseValue() * st.damage());
        EntityEquipment eq = e.getEquipment();
        if (eq != null) {
            Material[] armor = armor(st.armor());
            if (armor != null) {
                if (empty(eq.getHelmet())) eq.setHelmet(new ItemStack(armor[0]));
                if (empty(eq.getChestplate())) eq.setChestplate(new ItemStack(armor[1]));
                if (empty(eq.getLeggings())) eq.setLeggings(new ItemStack(armor[2]));
                if (empty(eq.getBoots())) eq.setBoots(new ItemStack(armor[3]));
            }
            eq.setHelmetDropChance(0f);
            eq.setChestplateDropChance(0f);
            eq.setLeggingsDropChance(0f);
            eq.setBootsDropChance(0f);
            eq.setItemInMainHandDropChance(0f);
            eq.setItemInOffHandDropChance(0f);
        }
        if (e instanceof Mob m) {
            m.setRemoveWhenFarAway(false);
            m.setPersistent(true);
        }
        e.setCanPickupItems(false);
        e.addScoreboardTag(ArenaScanner.TAG_DEFENDER);
    }

    private static boolean empty(ItemStack item) {
        return item == null || item.getType().isAir();
    }

    private static Material[] armor(String tier) {
        return switch (tier) {
            case "cuir" -> new Material[]{Material.LEATHER_HELMET, Material.LEATHER_CHESTPLATE, Material.LEATHER_LEGGINGS, Material.LEATHER_BOOTS};
            case "maille" -> new Material[]{Material.CHAINMAIL_HELMET, Material.CHAINMAIL_CHESTPLATE, Material.CHAINMAIL_LEGGINGS, Material.CHAINMAIL_BOOTS};
            case "fer" -> new Material[]{Material.IRON_HELMET, Material.IRON_CHESTPLATE, Material.IRON_LEGGINGS, Material.IRON_BOOTS};
            case "diamant" -> new Material[]{Material.DIAMOND_HELMET, Material.DIAMOND_CHESTPLATE, Material.DIAMOND_LEGGINGS, Material.DIAMOND_BOOTS};
            case "netherite" -> new Material[]{Material.NETHERITE_HELMET, Material.NETHERITE_CHESTPLATE, Material.NETHERITE_LEGGINGS, Material.NETHERITE_BOOTS};
            default -> null;
        };
    }

    /**
     * Poste d'un défenseur : un endroit où se tenir à hauteur d'homme, près d'une porte ou de la
     * cloche une fois sur deux (au sol des rues, pas sur les toits), sinon une colonne au cœur de
     * la boîte ; jamais à moins de 14 blocs du camp des joueurs.
     */
    private Location post(RaidRun r) {
        World w = r.arena.world();
        BoundingBox box = r.city != null ? r.city.village() : r.arena.box(); // ville : la garnison du village
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        List<Location> anchors = new ArrayList<>(r.arena.doors());
        anchors.addAll(r.arena.bells());
        double mx = box.getWidthX() * 0.1, mz = box.getWidthZ() * 0.1;
        for (int attempt = 0; attempt < 24; attempt++) {
            Location at;
            if (!anchors.isEmpty() && (rnd.nextBoolean() || r.target.underground())) {
                Location a = anchors.get(rnd.nextInt(anchors.size()));
                int x = a.getBlockX() + rnd.nextInt(-3, 4), z = a.getBlockZ() + rnd.nextInt(-3, 4);
                if (!w.isChunkLoaded(x >> 4, z >> 4)) continue;
                at = GroundFinder.standAt(w, x, a.getBlockY(), z);
            } else {
                int x = (int) Math.floor(rnd.nextDouble(box.getMinX() + mx, Math.max(box.getMinX() + mx + 1, box.getMaxX() - mx)));
                int z = (int) Math.floor(rnd.nextDouble(box.getMinZ() + mz, Math.max(box.getMinZ() + mz + 1, box.getMaxZ() - mz)));
                if (!w.isChunkLoaded(x >> 4, z >> 4)) continue;
                at = atSea(r) ? deckAt(w, x, z, box) : r.target.underground() ? RaidLair.insideAt(w, x, z, box)
                        : GroundFinder.groundAt(w, x, z);
            }
            if (at == null) continue;
            if (r.camp != null && at.distanceSquared(r.camp.spawn()) < 14 * 14) continue;
            return at;
        }
        return heart(r);
    }

    /**
     * Cœur de la cible, au sol : la cloche (la place), sinon le sol au centre de la boîte (le centre
     * lui-même peut être en l'air ou dans un mur).
     */
    private static Location heart(RaidRun r) {
        World w = r.arena.world();
        if (r.target.underground() && r.lairEntry != null) return r.lairEntry.clone(); // fond de la galerie
        if (atSea(r)) {
            // Navire : le pont, au plus près du milieu (le centre est souvent le mât)
            Location c = r.arena.center();
            for (int ring = 0; ring <= 8; ring++) {
                for (int dx = -ring; dx <= ring; dx++) {
                    for (int dz = -ring; dz <= ring; dz++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) continue;
                        Location at = deckAt(w, c.getBlockX() + dx, c.getBlockZ() + dz, r.arena.box());
                        if (at != null) return at;
                    }
                }
            }
        }
        if (!r.arena.bells().isEmpty()) {
            Location b = r.arena.bells().get(0);
            for (int[] d : new int[][]{{2, 0}, {-2, 0}, {0, 2}, {0, -2}, {3, 3}, {-3, -3}}) {
                Location at = GroundFinder.standAt(w, b.getBlockX() + d[0], b.getBlockY(), b.getBlockZ() + d[1]);
                if (at != null) return at;
            }
        }
        Location c = r.arena.center();
        Location at = GroundFinder.standAt(w, c.getBlockX(), c.getBlockY(), c.getBlockZ());
        if (at == null) at = GroundFinder.groundAt(w, c.getBlockX(), c.getBlockZ());
        return at != null ? at : c.clone();
    }

    /**
     * Cible en mer (abordage), ou dans le Nether ou l'End (pas de « plus haut bloc » utile : toit de
     * bedrock, vide) : postes sur la première place libre de la boîte, renforts à bord.
     */
    private static boolean atSea(RaidRun r) {
        return r.target.type() == RaidTarget.Type.ABORDAGE || r.arena.world().getEnvironment() != World.Environment.NORMAL;
    }

    /**
     * Navire : la première place où se tenir au-dessus de la mer dans la colonne (pont, cale), pas
     * sur les voiles ni dans la mâture ; null s'il n'y en a pas.
     */
    static Location deckAt(World w, int x, int z, BoundingBox box) {
        int from = (int) Math.floor(box.getMinY()) + 1, to = (int) Math.ceil(box.getMaxY());
        if (w.getEnvironment() != World.Environment.THE_END) from = Math.max(from, w.getSeaLevel()); // au-dessus de la mer, de la lave
        for (int y = from; y < to; y++) {
            Block below = w.getBlockAt(x, y - 1, z), feet = w.getBlockAt(x, y, z), head = w.getBlockAt(x, y + 1, z);
            if (below.isLiquid() || !below.getType().isSolid()) continue;
            if (!feet.isPassable() || feet.isLiquid() || !head.isPassable() || head.isLiquid()) continue;
            return new Location(w, x + 0.5, y, z + 0.5);
        }
        return null;
    }

    /** Chef : la créature nommée par la cible (déjà là, sinon ajoutée), sinon un champion. */
    private void placeChief(RaidRun r, List<LivingEntity> natural, Recruit recruit, RaidConfig.Strength st, double health) {
        String wanted = r.target.defenders().chief();
        LivingEntity chief = null;
        // Ville fortifiée : le bailli se tient au rez-de-chaussée du siège du pouvoir
        Location at = r.city != null && r.city.seat() != null ? RaidCity.seatSpot(r.arena.world(), r.city.seat()) : null;
        if (at == null) at = heart(r);
        if (wanted != null) {
            for (LivingEntity e : natural) if (wanted.equalsIgnoreCase(ItemIds.keyOf(e.getType()))) { chief = e; break; }
            if (chief == null) chief = spawn(r, new Recruit(false, List.of(wanted)), at, st, health, true);
        } else {
            chief = spawn(r, recruit, at, st, health, true);
        }
        if (chief == null) return;
        r.defenders.add(chief.getUniqueId());
        r.chief = chief.getUniqueId();
        String base = chief.getCustomName() != null ? chief.getCustomName().replaceAll("§.", "") : ItemIds.entityLabel(chief.getType());
        String title = r.target.type() == RaidTarget.Type.VILLE ? "Bailli " : atSea(r) || r.faction != null ? "Capitaine " : "Chef ";
        r.chiefName = r.faction != null ? title + of(r.faction.name().replaceAll("§.", "")) : title + base;
        if (!(chief.hasMetadata("NPC"))) {
            chief.setCustomName("§4" + r.chiefName);
            chief.setCustomNameVisible(true);
        }
        chief.setGlowing(true);
    }

    /**
     * Maître du repaire : la créature de raids.yml (« maitre ») déjà dans la structure, sinon posée
     * au cœur du repaire ; à défaut (identifiant inconnu), un champion. Il garde sa vie de boss
     * (pas de renfort de vie), ne laisse rien et brille.
     */
    private void placeMaster(RaidRun r, List<LivingEntity> natural, Recruit recruit) {
        String wanted = r.target.defenders().master();
        LivingEntity master = null;
        RaidConfig.Strength plain = new RaidConfig.Strength(1.0, 1.0, "aucune");
        if (wanted != null) {
            for (LivingEntity e : natural) if (wanted.equalsIgnoreCase(ItemIds.keyOf(e.getType()))) { master = e; break; }
            if (master == null) {
                // Pas encore étiqueté (né du terrain, hors des pièces balayées) : cherché dans la boîte
                for (Entity e : r.arena.world().getNearbyEntities(r.arena.box().clone().expand(4))) {
                    if (e instanceof LivingEntity le && !(e instanceof Player) && wanted.equalsIgnoreCase(ItemIds.keyOf(e.getType()))) {
                        master = le;
                        strengthen(le, plain, 1.0);
                        break;
                    }
                }
            }
            if (master == null && ItemIds.resolveEntity(wanted) != null) {
                master = spawn(r, new Recruit(false, List.of(wanted)), heart(r), plain, 1.0, false);
            }
        }
        if (master == null) master = spawn(r, recruit, heart(r), strength, health, true);
        if (master == null) return;
        r.defenders.add(master.getUniqueId());
        r.master = master.getUniqueId();
        r.masterName = master.getCustomName() != null && !master.hasMetadata("NPC")
                ? master.getCustomName().replaceAll("§.", "") : ItemIds.entityLabel(master.getType());
        master.setGlowing(true);
    }

    /**
     * Ville fortifiée : garnison de chaque tour (étages et chemins de ronde), bannière de la faction
     * au sommet de chaque tour (elle compte parmi les bannières à arracher), garde du siège.
     */
    private void garrisonCity(RaidRun r, Recruit recruit, RaidConfig.Strength st, double health, int perTower, int seatGuards) {
        World w = r.arena.world();
        Material flag = r.faction != null ? r.faction.banner() : Material.RED_BANNER;
        for (RaidCity.Tower tower : r.city.towers()) {
            Set<UUID> garrison = new HashSet<>();
            for (int i = 0; i < perTower; i++) {
                Location at = RaidTemplates.randomStand(w, tower.box());
                if (at == null) continue;
                LivingEntity e = spawn(r, recruit, at, st, health, false);
                if (e == null) continue;
                r.defenders.add(e.getUniqueId());
                garrison.add(e.getUniqueId());
            }
            if (!garrison.isEmpty()) r.towerGarrisons.add(garrison);
            Block top = tower.top().getBlock();
            if (top.getType().isAir() && top.getRelative(org.bukkit.block.BlockFace.DOWN).getType().isSolid()) {
                top.setType(flag, false);
                banners.add(key(top.getLocation()));
            }
        }
        if (r.city.seat() == null) return;
        for (int i = 0; i < seatGuards; i++) {
            Location at = RaidTemplates.randomStand(w, r.city.seat());
            if (at == null) continue;
            LivingEntity e = spawn(r, recruit, at, st, health, false);
            if (e != null) r.defenders.add(e.getUniqueId());
        }
    }

    /** Un défenseur tombe ou disparaît : sa tour est prise quand toute sa garnison est tombée. */
    private void towerLost(RaidRun r, UUID id) {
        for (int i = 0; i < r.towerGarrisons.size(); i++) {
            Set<UUID> garrison = r.towerGarrisons.get(i);
            if (!garrison.remove(id) || !garrison.isEmpty() || !r.towersTaken.add(i)) continue;
            RaidRun.Objective o = r.objective("tours");
            if (o == null || o.complete()) continue;
            o.done++;
            announce(r, "§6⚔ Tour prise ! §7(" + o.done + "/" + o.total + ")", Sound.ENTITY_PLAYER_LEVELUP, 0.7f);
        }
    }

    /** Plafond d'un objectif à compter (bannières, cages, captifs) : 3 + niveau (4 à 10). */
    private static int cap(RaidRun r) {
        return 3 + r.level.number();
    }

    /** Objectifs de la cible présents sur place (§5.2) ; à défaut, la garnison. */
    private void buildObjectives(RaidRun r) {
        RaidConfig c = raids.config();
        Arena a = r.arena;
        int captives = 0;
        for (UUID id : a.civilians()) if (Bukkit.getEntity(id) instanceof Allay) captives++;
        for (String key : r.target.objectives()) {
            if (r.objective(key) != null) continue;
            switch (key) {
                case "garnison" -> {
                    if (r.defendersTotal > 0) r.objectives.add(new RaidRun.Objective("garnison", "Garnison abattue",
                            c.weight("garnison"), false, (int) Math.ceil(r.defendersTotal * c.garrisonPart)));
                }
                case "cloche" -> {
                    if (!a.bells().isEmpty()) r.objectives.add(new RaidRun.Objective("cloche", "Cloche sonnée", c.weight("cloche"), false, 1));
                }
                // Bannières, cages, captifs : au plus 3 + niveau à faire (le vaisseau du roi pirate
                // porte 257 bannières : n'importe lesquelles comptent, jusqu'au plafond)
                case "bannieres" -> {
                    if (!banners.isEmpty()) r.objectives.add(new RaidRun.Objective("bannieres", "Bannières arrachées",
                            c.weight("bannieres"), false, Math.min(banners.size(), cap(r))));
                }
                case "cages" -> {
                    if (!spawners.isEmpty()) r.objectives.add(new RaidRun.Objective("cages", "Cages détruites",
                            c.weight("cages"), false, Math.min(spawners.size(), cap(r))));
                }
                case "liberer" -> {
                    if (captives > 0) r.objectives.add(new RaidRun.Objective("liberer", "Captifs libérés", c.weight("liberer"),
                            false, Math.min(captives, cap(r))));
                }
                case "chef" -> {
                    if (r.chief != null) r.objectives.add(new RaidRun.Objective("chef", r.chiefName + " abattu", c.weight("chef"), true, 1));
                }
                case "aborder" -> {
                    if (r.target.type() == RaidTarget.Type.ABORDAGE) r.objectives.add(new RaidRun.Objective("aborder", "Navire abordé", c.weight("aborder"), false, 1));
                }
                case "maitre" -> {
                    if (r.master != null) r.objectives.add(new RaidRun.Objective("maitre", r.masterName + " vaincu", c.weight("maitre"), true, 1));
                }
                case "tours" -> {
                    if (!r.towerGarrisons.isEmpty()) r.objectives.add(new RaidRun.Objective("tours", "Tours prises",
                            c.weight("tours"), false, r.towerGarrisons.size()));
                }
                case "tenir" -> {
                    r.holdAt = openGround(r);
                    r.objectives.add(new RaidRun.Objective("tenir", "Place tenue", c.weight("tenir"), false, c.holdSeconds));
                }
                default -> { } // porte, tenir, maître, aborder : phases suivantes
            }
        }
        if (r.master != null && r.objective("maitre") == null) {
            r.objectives.add(new RaidRun.Objective("maitre", r.masterName + " vaincu", c.weight("maitre"), true, 1));
        }
        if (r.objectives.isEmpty() && r.defendersTotal > 0) {
            r.objectives.add(new RaidRun.Objective("garnison", "Garnison abattue", c.weight("garnison"), false,
                    (int) Math.ceil(r.defendersTotal * c.garrisonPart)));
        }
    }

    /* =========================
       PENDANT L'ASSAUT
       ========================= */

    /**
     * Toutes les deux secondes : un défenseur sans cible s'en prend au joueur engagé le plus proche
     * (20 blocs, en vue) — y compris les soldats des mods qui ne sont pas hostiles d'eux-mêmes.
     */
    void aggro(RaidRun r) {
        // Objectifs (aborder, tenir) : tout joueur engagé présent, sauf spectateur — un administrateur
        // en créatif qui teste compte aussi ; cibles des défenseurs : survie et aventure seulement
        List<Player> present = new ArrayList<>(), players = new ArrayList<>();
        for (UUID id : r.enrolled) {
            Player p = Bukkit.getPlayer(id);
            if (p == null || !raids.isRaidWorld(p.getWorld()) || p.isDead() || p.getGameMode() == org.bukkit.GameMode.SPECTATOR) continue;
            present.add(p);
            if (p.getGameMode() == org.bukkit.GameMode.SURVIVAL || p.getGameMode() == org.bukkit.GameMode.ADVENTURE) players.add(p);
        }
        sweep(r);
        if (present.isEmpty()) return;
        boarding(r, present);
        hold(r, present);
        if (players.isEmpty()) return;
        // Fin de garnison : les derniers défenseurs à abattre brillent (pas de chasse au dernier caché)
        // Tour presque prise : ses deux derniers soldats brillent
        for (Set<UUID> garrison : r.towerGarrisons) {
            if (garrison.size() > 2) continue;
            for (UUID id : garrison) {
                if (Bukkit.getEntity(id) instanceof LivingEntity e && !e.isGlowing()) e.setGlowing(true);
            }
        }
        RaidRun.Objective g = r.objective("garnison");
        boolean reveal = g != null && !g.complete() && g.total - g.done <= 3;
        for (UUID id : r.defenders) {
            if (reveal && Bukkit.getEntity(id) instanceof LivingEntity e && !e.isGlowing()) e.setGlowing(true);
        }
        for (UUID id : r.defenders) {
            if (!(Bukkit.getEntity(id) instanceof Mob m) || !m.isValid() || m.hasMetadata("NPC")) continue;
            LivingEntity t = m.getTarget();
            if (t != null && t.isValid() && !t.isDead()) continue;
            Player best = null;
            double bestD = 20 * 20;
            for (Player p : players) {
                if (!p.getWorld().equals(m.getWorld())) continue;
                double d = p.getLocation().distanceSquared(m.getLocation());
                if (d < bestD && m.hasLineOfSight(p)) { bestD = d; best = p; }
            }
            if (best != null) m.setTarget(best);
        }
    }

    /**
     * Renforts (§5.4) : quand la défiance passe sous 75, 50 puis 25 % (selon le nombre de vagues du
     * niveau : une vague sous 50 %, deux sous 67 et 33 %), une troupe d'un quart de la garnison
     * arrive du côté opposé au camp et marche sur le cœur de la cible.
     */
    void reinforce(RaidRun r) {
        int waves = r.level.reinforcements();
        if (waves <= 0 || r.waves >= waves || recruit == null) return;
        int threshold = 100 - (r.waves + 1) * 100 / (waves + 1);
        if (r.defiance() >= threshold) return;
        r.waves++;
        int n = Math.min(Math.max(2, (int) Math.ceil(r.defendersTotal * 0.25)), raids.config().maxAlive - r.defenders.size());
        if (n <= 0) return;
        Location from = atSea(r) || r.target.underground() ? heart(r) : farSide(r);
        Location heart = heart(r);
        Player nearest = null;
        int added = 0;
        for (int i = 0; i < n; i++) {
            Location at = from;
            // En mer : à bord (montés de la cale) ; sur terre : du côté opposé au camp
            Location g = atSea(r) || r.target.underground() ? post(r) : GroundFinder.groundAt(from.getWorld(), from.getBlockX()
                    + ThreadLocalRandom.current().nextInt(-3, 4), from.getBlockZ() + ThreadLocalRandom.current().nextInt(-3, 4));
            if (g != null) at = g;
            LivingEntity e = recruit.soldiers()
                    ? soldier(r, at, heart, false)
                    : spawn(r, recruit, at, strength, health, false);
            if (e == null) continue;
            r.defenders.add(e.getUniqueId());
            added++;
            if (e instanceof Mob m && !e.hasMetadata("NPC")) {
                if (nearest == null) nearest = nearestPlayer(r, at);
                if (nearest != null) m.setTarget(nearest);
            }
        }
        if (added == 0) return;
        announce(r, "§c⚔ Des renforts arrivent §7(" + added + ") §c: l'adversaire ne lâche rien !", Sound.EVENT_RAID_HORN, 0.7f);
        for (UUID id : r.enrolled) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && raids.isRaidWorld(p.getWorld())) p.sendTitle("", "§cDes renforts arrivent !", 5, 40, 10);
        }
    }

    /** Un soldat de l'armée ennemie qui tient {@code post} (renfort : le cœur de la cible). */
    private LivingEntity soldier(RaidRun r, Location at, Location post, boolean champion) {
        SiegeConfig sc = siegeConfig.get();
        int tier = SIEGE_TIER[r.level.number() - 1];
        SiegeConfig.Unit unit = champion && sc.armyChampion(tier) != null ? sc.armyChampion(tier) : sc.randomUnit(tier);
        // Chef : le champion des sièges (déjà robuste, 140 points de vie) garde sa vie ; une unité
        // ordinaire promue chef reçoit une fois et demie la vie du niveau
        double factor = !champion ? health : unit == sc.armyChampion(tier) ? 1.0 : health * 1.5;
        NPC npc = army().spawnDefender(unit, at, r.faction, champion, post, factor);
        if (npc == null || !(npc.getEntity() instanceof LivingEntity body)) return null;
        body.addScoreboardTag(ArenaScanner.TAG_DEFENDER);
        return body;
    }

    /** Bord de la cible opposé au camp, quelques blocs au-delà de sa boîte. */
    private static Location farSide(RaidRun r) {
        Location c = r.arena.center();
        BoundingBox box = r.arena.box();
        double dx = c.getX() - r.camp.spawn().getX(), dz = c.getZ() - r.camp.spawn().getZ();
        double len = Math.max(1e-3, Math.hypot(dx, dz));
        double reach = Math.hypot(box.getWidthX(), box.getWidthZ()) / 2 + 6;
        int x = (int) Math.floor(c.getX() + dx / len * reach), z = (int) Math.floor(c.getZ() + dz / len * reach);
        World w = c.getWorld();
        Location g = w.isChunkLoaded(x >> 4, z >> 4) ? GroundFinder.groundAt(w, x, z) : null;
        return g != null ? g : c.clone();
    }

    private Player nearestPlayer(RaidRun r, Location at) {
        Player best = null;
        double bestD = Double.MAX_VALUE;
        for (UUID id : r.enrolled) {
            Player p = Bukkit.getPlayer(id);
            if (p == null || !p.getWorld().equals(at.getWorld()) || p.isDead()) continue;
            double d = p.getLocation().distanceSquared(at);
            if (d < bestD) { bestD = d; best = p; }
        }
        return best;
    }

    /**
     * Défenseurs disparus sans mourir (le Warden s'enterre, une créature de mod s'évapore, un PNJ
     * retiré) : comptés comme vaincus, pour qu'aucun objectif ne devienne impossible.
     */
    private void sweep(RaidRun r) {
        boolean changed = false;
        for (UUID id : new ArrayList<>(r.defenders)) {
            Entity e = Bukkit.getEntity(id);
            if (e != null && e.isValid()) continue;
            if (e != null && e.isDead()) continue; // mort en cours : l'événement de mort s'en charge
            r.defenders.remove(id);
            r.defendersKilled++;
            towerLost(r, id);
            RaidRun.Objective g = r.objective("garnison");
            if (g != null) g.done = r.defendersKilled;
            if (id.equals(r.master)) {
                RaidRun.Objective m = r.objective("maitre");
                if (m != null) m.done = 1;
                announce(r, "§5⚔ " + r.masterName + " a fui les lieux : le repaire est à vous !", Sound.ENTITY_WARDEN_DIG, 1f);
            }
            if (id.equals(r.chief)) {
                RaidRun.Objective c = r.objective("chef");
                if (c != null) c.done = 1;
                announce(r, "§6⚔ " + r.chiefName + " a pris la fuite !", null, 1f);
            }
            changed = true;
        }
        if (changed) raids.progressed(r);
    }

    /** Abordage : un joueur engagé pose le pied sur le navire (hors de l'eau, hors d'une barque). */
    private void boarding(RaidRun r, List<Player> players) {
        RaidRun.Objective o = r.objective("aborder");
        if (o == null || o.complete()) return;
        BoundingBox ship = r.arena.box().clone().expand(1);
        for (Player p : players) {
            // Debout à bord : hors de l'eau, hors d'une barque, pas en vol, un bloc plein sous les pieds
            if (p.isInWater() || p.isInsideVehicle() || p.isFlying() || p.isGliding()) continue;
            Block under = p.getLocation().clone().subtract(0, 0.2, 0).getBlock();
            if (!p.isOnGround() && (!under.getType().isSolid() || under.isLiquid())) continue;
            if (!ship.contains(p.getLocation().toVector())) continue;
            if (r.camp != null && p.getLocation().distanceSquared(r.camp.spawn()) < 12 * 12) continue; // le radeau ne compte pas
            o.done = 1;
            announce(r, "§6⚓ À l'abordage ! §f" + p.getName() + " §6a posé le pied sur le navire.", Sound.ENTITY_GENERIC_SPLASH, 0.8f);
            raids.progressed(r);
            return;
        }
    }

    /**
     * « Tenir » : toutes les 2 s, si un joueur engagé est dans la place et qu'aucun défenseur n'y
     * est, la place compte 2 s de plus (un défenseur qui revient met le compte en pause).
     */
    private void hold(RaidRun r, List<Player> players) {
        RaidRun.Objective o = r.objective("tenir");
        if (o == null || o.complete() || r.holdAt == null) return;
        int radius = raids.config().holdRadius;
        double r2 = (double) radius * radius;
        List<Player> inside = new ArrayList<>();
        for (Player p : players) {
            if (p.getWorld().equals(r.holdAt.getWorld()) && p.getLocation().distanceSquared(r.holdAt) <= r2) inside.add(p);
        }
        if (inside.isEmpty()) return;
        int near = defendersNear(r, r.holdAt, radius);
        r.holdContested = near > 0;
        if (near > 0) {
            // Les défenseurs qui disputent la place brillent : on les trouve, même derrière un mur
            for (UUID id : r.defenders) {
                if (Bukkit.getEntity(id) instanceof LivingEntity e && e.isValid() && e.getWorld().equals(r.holdAt.getWorld())
                        && e.getLocation().distanceSquared(r.holdAt) <= r2 && !e.isGlowing()) e.setGlowing(true);
            }
            for (Player p : inside) p.sendActionBar(net.kyori.adventure.text.Component.text("⚑ Place disputée : " + near
                    + " défenseur" + (near > 1 ? "s" : "") + " dedans (ils brillent)", net.kyori.adventure.text.format.NamedTextColor.RED));
            return;
        }
        if (o.done == 0) announce(r, "§6⚑ Vous tenez la place : gardez-la " + o.total + " s !", Sound.EVENT_RAID_HORN, 1.2f);
        o.done = Math.min(o.total, o.done + 2);
        for (Player p : inside) p.sendActionBar(net.kyori.adventure.text.Component.text("⚑ Place tenue : " + o.done + " / "
                + o.total + " s", net.kyori.adventure.text.format.NamedTextColor.GOLD));
        raids.progressed(r);
    }

    /**
     * Place à tenir, à chaque demi-seconde tant qu'elle n'est pas tenue : cercle au sol (doré,
     * rouge quand un défenseur est dedans) et colonne de lumière visible de loin.
     */
    void mark(RaidRun r) {
        RaidRun.Objective o = r.objective("tenir");
        if (o == null || o.complete() || r.holdAt == null) return;
        World w = r.holdAt.getWorld();
        int radius = raids.config().holdRadius;
        Particle.DustOptions dust = new Particle.DustOptions(r.holdContested
                ? org.bukkit.Color.fromRGB(222, 80, 80) : org.bukkit.Color.fromRGB(241, 207, 94), 1.5f);
        int points = Math.max(24, radius * 6);
        for (int i = 0; i < points; i++) {
            double a = 2 * Math.PI * i / points;
            w.spawnParticle(Particle.DUST, r.holdAt.getX() + Math.cos(a) * radius, r.holdAt.getY() + 0.3,
                    r.holdAt.getZ() + Math.sin(a) * radius, 1, 0, 0, 0, 0, dust, true);
        }
        for (int dy = 0; dy < 24; dy += 2) {
            w.spawnParticle(Particle.END_ROD, r.holdAt.getX(), r.holdAt.getY() + dy, r.holdAt.getZ(), 1, 0.05, 0.4, 0.05, 0, null, true);
        }
    }

    /**
     * Place à tenir : un sol à ciel ouvert (cour, place), au plus près du cœur de la cible — pas un
     * toit ni un étage. La cloche d'abord quand il y en a une (la place du village) ; à défaut, le
     * cœur de la cible.
     */
    private static Location openGround(RaidRun r) {
        if (!r.arena.bells().isEmpty() || atSea(r) || r.target.underground()) return heart(r);
        World w = r.arena.world();
        BoundingBox box = r.arena.box();
        Location c = r.arena.center();
        int maxRing = (int) (Math.min(box.getWidthX(), box.getWidthZ()) * 0.35);
        for (int ring = 0; ring <= maxRing; ring += 2) {
            for (int dx = -ring; dx <= ring; dx += 2) {
                for (int dz = -ring; dz <= ring; dz += 2) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) continue;
                    int x = c.getBlockX() + dx, z = c.getBlockZ() + dz;
                    if (!w.isChunkLoaded(x >> 4, z >> 4)) continue;
                    // Le plus bas sol libre de la colonne dans la boîte, sous le ciel
                    int top = w.getHighestBlockYAt(x, z, org.bukkit.HeightMap.MOTION_BLOCKING_NO_LEAVES);
                    for (int y = (int) Math.floor(box.getMinY()) + 1; y <= Math.min(top + 1, (int) box.getMaxY()); y++) {
                        Block below = w.getBlockAt(x, y - 1, z), feet = w.getBlockAt(x, y, z), head = w.getBlockAt(x, y + 1, z);
                        if (below.isLiquid() || !below.getType().isSolid()) continue;
                        if (!feet.isPassable() || feet.isLiquid() || !head.isPassable() || head.isLiquid()) continue;
                        if (top >= y) break; // un toit, un étage au-dessus : colonne suivante
                        return new Location(w, x + 0.5, y, z + 0.5);
                    }
                }
            }
        }
        return heart(r);
    }

    /** Défenseurs encore debout dans {@code radius} blocs de {@code at}. */
    int defendersNear(RaidRun r, Location at, int radius) {
        int n = 0;
        double r2 = (double) radius * radius;
        for (UUID id : r.defenders) {
            Entity e = Bukkit.getEntity(id);
            if (e != null && e.isValid() && !e.isDead() && e.getWorld().equals(at.getWorld())
                    && e.getLocation().distanceSquared(at) <= r2) n++;
        }
        return n;
    }

    /** Victoire : les défenseurs restants jettent les armes et disparaissent. */
    void capitulate(RaidRun r) {
        for (UUID id : new ArrayList<>(r.defenders)) {
            Entity e = Bukkit.getEntity(id);
            if (e == null || e.hasMetadata("NPC")) continue;
            e.getWorld().spawnParticle(Particle.POOF, e.getLocation().add(0, 1, 0), 12, 0.3, 0.5, 0.3, 0.02);
            e.remove();
        }
        if (army != null) army.clear();
        r.defenders.clear();
    }

    /** Fin de l'expédition : soldats de l'armée retirés. */
    void clear() {
        if (army != null) army.clear();
        recruit = null;
        banners.clear();
        spawners.clear();
    }

    /* =========================
       ÉVÉNEMENTS
       ========================= */

    /** Un défenseur tombe (garnison, chef) ; un civil tué par un joueur (bonus « épargner »). */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(EntityDeathEvent event) {
        RaidRun r = raids.run();
        if (r == null || r.state != RaidRun.State.ASSAUT) return;
        LivingEntity dead = event.getEntity();
        UUID id = dead.getUniqueId();
        if (r.defenders.remove(id)) {
            r.defendersKilled++;
            towerLost(r, id);
            Player killer = dead.getKiller();
            if (killer != null) r.kills.merge(killer.getUniqueId(), 1, Integer::sum);
            RaidRun.Objective g = r.objective("garnison");
            if (g != null) g.done = r.defendersKilled;
            if (id.equals(r.master)) {
                RaidRun.Objective m = r.objective("maitre");
                if (m != null) m.done = 1;
                announce(r, "§5⚔ " + r.masterName + " est vaincu" + (killer != null ? " par " + killer.getName() : "") + " !",
                        Sound.ENTITY_WITHER_DEATH, 0.9f);
            }
            if (id.equals(r.chief)) {
                RaidRun.Objective c = r.objective("chef");
                if (c != null) c.done = 1;
                announce(r, "§6⚔ " + r.chiefName + " est tombé" + (killer != null ? " sous les coups de " + killer.getName() : "") + " !",
                        Sound.ENTITY_WITHER_DEATH, 0.6f);
            }
            raids.progressed(r);
        } else if (dead.getScoreboardTags().contains(ArenaScanner.TAG_CIVIL) && dead.getKiller() != null) {
            r.civiliansKilled++;
        }
    }

    /** Bannière arrachée, cage détruite. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        RaidRun r = raids.run();
        if (r == null || r.state != RaidRun.State.ASSAUT || !raids.isRaidWorld(event.getBlock().getWorld())) return;
        String k = key(event.getBlock().getLocation());
        if (banners.remove(k)) {
            RaidRun.Objective o = r.objective("bannieres");
            if (o != null && !o.complete()) {
                o.done++;
                announce(r, "§6⚔ Bannière arrachée §7(" + o.done + "/" + o.total + ")", Sound.ENTITY_ITEM_BREAK, 1f);
            }
            raids.progressed(r);
        } else if (spawners.remove(k)) {
            RaidRun.Objective o = r.objective("cages");
            if (o != null && !o.complete()) {
                o.done++;
                announce(r, "§6⚔ Cage détruite §7(" + o.done + "/" + o.total + ")", Sound.BLOCK_ANVIL_LAND, 0.6f);
            }
            raids.progressed(r);
        }
    }

    /** Cloche sonnée : seulement une fois la place dégagée. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBell(BellRingEvent event) {
        RaidRun r = raids.run();
        if (r == null || r.state != RaidRun.State.ASSAUT) return;
        RaidRun.Objective o = r.objective("cloche");
        if (o == null || o.complete()) return;
        Block bell = event.getBlock();
        boolean ours = r.arena.bells().stream().anyMatch(b -> key(b).equals(key(bell.getLocation())));
        if (!ours) return;
        int near = defendersNear(r, bell.getLocation(), raids.config().bellRadius);
        if (near > 0) {
            if (event.getEntity() instanceof Player p) {
                p.sendMessage("§c⚔ La place n'est pas dégagée : " + near + " défenseur" + (near > 1 ? "s" : "")
                        + " à moins de " + raids.config().bellRadius + " blocs.");
            }
            return;
        }
        o.done = 1;
        announce(r, "§6⚔ La cloche sonne : la place est à vous !", Sound.BLOCK_BELL_RESONATE, 1f);
        raids.progressed(r);
    }

    /** Captif libéré (allay en cage) : un clic dessus. */
    @EventHandler(ignoreCancelled = true)
    public void onFree(PlayerInteractEntityEvent event) {
        RaidRun r = raids.run();
        if (r == null || r.state != RaidRun.State.ASSAUT || !(event.getRightClicked() instanceof Allay allay)) return;
        if (!allay.getScoreboardTags().contains(ArenaScanner.TAG_CIVIL)) return;
        RaidRun.Objective o = r.objective("liberer");
        if (o == null || o.complete()) return;
        event.setCancelled(true);
        allay.getWorld().spawnParticle(Particle.HEART, allay.getLocation().add(0, 0.5, 0), 6, 0.3, 0.3, 0.3, 0);
        allay.remove();
        o.done++;
        announce(r, "§6⚔ Captif libéré §7(" + o.done + "/" + o.total + ")", Sound.ENTITY_ALLAY_ITEM_GIVEN, 1f);
        raids.progressed(r);
    }

    /** Les défenseurs morts-vivants ne brûlent pas au soleil (le raid se joue à midi). */
    @EventHandler(ignoreCancelled = true)
    public void onCombust(EntityCombustEvent event) {
        if (event.getClass() != EntityCombustEvent.class) return; // feu, lave, flèche enflammée : oui
        if (event.getEntity().getScoreboardTags().contains(ArenaScanner.TAG_DEFENDER)
                && raids.isRaidWorld(event.getEntity().getWorld())) event.setCancelled(true);
    }

    /** Message et son aux joueurs engagés (moments clés seulement). */
    private void announce(RaidRun r, String message, Sound sound, float pitch) {
        for (UUID id : r.enrolled) {
            Player p = Bukkit.getPlayer(id);
            if (p == null || !raids.isRaidWorld(p.getWorld())) continue;
            p.sendMessage(message);
            if (sound != null) p.playSound(p.getLocation(), sound, 0.8f, pitch);
        }
    }

    /** « de l'Ost du Corbeau », « des Lames de Grisval », « du Val », « de la Horde des Marais ». */
    private static String of(String name) {
        if (name.startsWith("les ")) return "des " + name.substring(4);
        if (name.startsWith("le ")) return "du " + name.substring(3);
        return "de " + name;
    }

    private static String key(Location l) {
        return l.getBlockX() + "," + l.getBlockY() + "," + l.getBlockZ();
    }
}
