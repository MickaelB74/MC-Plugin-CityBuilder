package com.citycore.inhabitant;

import com.citycore.building.Building;
import com.citycore.building.BuildingInspector;
import com.citycore.building.BuildingManager;
import com.citycore.building.BuildingType;
import com.citycore.building.Inspection;
import com.citycore.city.CityManager;
import com.citycore.city.ClaimCache;
import com.citycore.npc.SafeNavigation;
import com.citycore.util.DatabaseManager;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.event.NPCDeathEvent;
import net.citizensnpcs.api.npc.MemoryNPCDataStore;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.npc.NPCRegistry;
import net.citizensnpcs.trait.SleepTrait;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Bed;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.BlockVector;

import java.io.File;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Habitants de la ville (phase 3 du plan « Valmont vivante »).
 *
 * Chaque lit d'une habitation conforme (voir BuildingInspector) accueille un habitant : un PNJ
 * Citizens de type joueur (skin de habitants.skins), gardé en base (table inhabitants) et recréé
 * à chaque démarrage. Toutes les quelques minutes, s'il reste un lit libre et qu'un joueur est
 * connecté, un nouvel habitant arrive à pied par la lisière. Le jour il flâne autour de sa maison,
 * la nuit il rentre dormir dans son lit ; jamais hors des chunks de la ville. Il peut être tué par
 * les monstres (jamais par les joueurs) : son lit reste en deuil quelque temps, puis un autre
 * habitant prend sa place. Un lit retiré fait partir son habitant. À chaque aube, chaque habitant
 * verse un impôt à la caisse. Au-delà d'un plafond, les habitants sont comptés mais pas affichés
 * (les plus proches des joueurs le sont).
 */
public class InhabitantManager implements Listener {

    public enum State { HABITANT, FORMATION, SOLDAT, MORT }

    /** Un habitant (ligne de la table inhabitants). */
    public static final class Inhabitant {
        public final int id;
        public final String name;
        public final int skin;
        public final int buildingId;
        public final String world;
        public final int bx, by, bz;
        public State state;
        public final long arrivedAt;
        public Long diedAt;

        Inhabitant(int id, String name, int skin, int buildingId, String world, int bx, int by, int bz,
                   State state, long arrivedAt, Long diedAt) {
            this.id = id; this.name = name; this.skin = skin; this.buildingId = buildingId; this.world = world;
            this.bx = bx; this.by = by; this.bz = bz; this.state = state; this.arrivedAt = arrivedAt; this.diedAt = diedAt;
        }

        public boolean alive() { return state != State.MORT; }

        /** Tête du lit, ou null si le monde n'est pas chargé. */
        public Location bed() {
            World w = Bukkit.getWorld(world);
            return w == null ? null : new Location(w, bx, by, bz);
        }

        String bedKey() { return key(world, bx, by, bz); }
    }

    /** Un lit libre d'une habitation conforme. */
    private record FreeBed(Building building, BlockVector bed) {}

    /** Prénoms médiévaux (aucun n'est celui d'un artisan). */
    private static final List<String> NAMES = List.of(
            "Jehan", "Margot", "Guillemette", "Thibault", "Perrine", "Gauthier", "Aliénor", "Enguerrand",
            "Mahaut", "Bertrand", "Clémence", "Arnaud", "Jacquette", "Renaud", "Blanche", "Héloïse", "Lancelin",
            "Ermengarde", "Odon", "Marion", "Huguet", "Sibylle", "Amaury", "Colette", "Raoul", "Ameline", "Baudouin",
            "Pernelle", "Foulques", "Jeanne", "Gilles", "Adeline", "Thierry", "Mathilde", "Bérenger", "Constance",
            "Aymeric", "Douce", "Évrard", "Gisèle", "Hugues", "Ida", "Josselin", "Mélisende", "Nicolas", "Ombeline",
            "Pierre", "Quentin", "Rixende", "Sanche", "Tiphaine", "Ursule", "Vivien", "Yolande", "Zacharie");

    private static final String NPC_KEY = "citycore_inhabitant";

    private final JavaPlugin        plugin;
    private final DatabaseManager   db;
    private final BuildingManager   buildings;
    private final BuildingInspector inspector;
    private final CityManager       city;
    private final ClaimCache        claims;
    private final InhabitantSkins   skins;

    private NPCRegistry registry;
    private final Map<Integer, Inhabitant> all = new LinkedHashMap<>();
    private final Map<Integer, NPC> npcs = new HashMap<>();          // habitant → PNJ (créé à la première apparition)
    private final Map<Integer, Long> idleSince = new HashMap<>();    // pause de promenade
    private final Set<Integer> sleeping = new HashSet<>();
    private final Map<Integer, Integer> bedsByBuilding = new HashMap<>(); // lits des habitations conformes (dernier relevé)
    private Map<Integer, Building> homes = new HashMap<>();             // bâtiments par id (relus toutes les 5 s)

    // Réglages (section habitants de config.yml)
    private float   walkSpeed = 0.4f;
    private long    strollPause = 6000;
    private int     strollRadius = 12;
    private long    arrivalMs = 5 * 60_000L;
    private long    mourningMs = 20 * 60_000L;
    private int     tax = 2;
    private int     maxVisible = 24;
    private boolean nightAttacks = true;

    private long nextArrivalAt;
    private long lastTaxDay = -1;
    private int  ticks;

    public InhabitantManager(JavaPlugin plugin, DatabaseManager db, BuildingManager buildings,
                             BuildingInspector inspector, CityManager city) {
        this.plugin    = plugin;
        this.db        = db;
        this.buildings = buildings;
        this.inspector = inspector;
        this.city      = city;
        this.claims    = new ClaimCache(city);
        this.skins     = new InhabitantSkins(plugin, InhabitantSkins.HABITANTS);
    }

    /* =========================
       DÉMARRAGE / ARRÊT
       ========================= */

    public void start() {
        loadSettings();
        registry = CitizensAPI.createAnonymousNPCRegistry(new MemoryNPCDataStore());
        load();
        nextArrivalAt = System.currentTimeMillis() + arrivalMs;
        validate(); // cache des maisons, départs en attente
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickBehaviour, 40L, 10L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickMaintenance, 60L, 100L);
        plugin.getLogger().info("Habitants : " + living().size() + " en ville, " + mourning() + " lit(s) en deuil.");
    }

    /** Arrêt du serveur : les PNJ disparaissent (ils sont recréés depuis la base au démarrage). */
    public void shutdown() {
        for (NPC npc : new ArrayList<>(npcs.values())) npc.destroy();
        npcs.clear();
    }

    /** Relit la section habitants de config.yml (fichier lu directement : /city habitants recharger). */
    public void loadSettings() {
        File file = new File(plugin.getDataFolder(), "config.yml");
        ConfigurationSection c = file.exists() ? YamlConfiguration.loadConfiguration(file) : plugin.getConfig();
        walkSpeed    = (float) Math.max(0.1, Math.min(1.5, c.getDouble("habitants.vitesse", 0.4)));
        strollPause  = Math.round(Math.max(0, Math.min(120, c.getDouble("habitants.pause-promenade", 6))) * 1000);
        strollRadius = Math.max(3, Math.min(48, c.getInt("habitants.rayon-promenade", 12)));
        arrivalMs    = Math.round(Math.max(0.1, c.getDouble("habitants.arrivee-minutes", 5)) * 60_000);
        mourningMs   = Math.round(Math.max(0, c.getDouble("habitants.remplacement-minutes", 20)) * 60_000);
        tax          = Math.max(0, c.getInt("habitants.impot", 2));
        maxVisible   = Math.max(0, c.getInt("habitants.max-visibles", 24));
        nightAttacks = c.getBoolean("habitants.attaques-nocturnes", true);
        skins.reload();
    }

    /* =========================
       BASE DE DONNÉES
       ========================= */

    private void load() {
        all.clear();
        try (Statement st = db.getConnection().createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM inhabitants ORDER BY id")) {
            while (rs.next()) {
                long died = rs.getLong("died_at");
                Long diedAt = rs.wasNull() ? null : died;
                State state;
                try { state = State.valueOf(rs.getString("state")); } catch (IllegalArgumentException e) { state = State.HABITANT; }
                Inhabitant inh = new Inhabitant(rs.getInt("id"), rs.getString("name"), rs.getInt("skin"),
                        rs.getInt("building_id"), rs.getString("world"), rs.getInt("bed_x"), rs.getInt("bed_y"),
                        rs.getInt("bed_z"), state, rs.getLong("arrived_at"), diedAt);
                all.put(inh.id, inh);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private Inhabitant insert(String name, int skin, Building b, BlockVector bed) {
        long now = System.currentTimeMillis();
        try (PreparedStatement ps = db.getConnection().prepareStatement("""
                INSERT INTO inhabitants (name, skin, building_id, world, bed_x, bed_y, bed_z, state, arrived_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, 'HABITANT', ?)""", Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setInt(2, skin);
            ps.setInt(3, b.id());
            ps.setString(4, b.world());
            ps.setInt(5, bed.getBlockX());
            ps.setInt(6, bed.getBlockY());
            ps.setInt(7, bed.getBlockZ());
            ps.setLong(8, now);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) return null;
                Inhabitant inh = new Inhabitant(keys.getInt(1), name, skin, b.id(), b.world(),
                        bed.getBlockX(), bed.getBlockY(), bed.getBlockZ(), State.HABITANT, now, null);
                all.put(inh.id, inh);
                return inh;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    private void saveDeath(Inhabitant inh) {
        try (PreparedStatement ps = db.getConnection().prepareStatement(
                "UPDATE inhabitants SET state = 'MORT', died_at = ? WHERE id = ?")) {
            ps.setLong(1, inh.diedAt);
            ps.setInt(2, inh.id);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void delete(Inhabitant inh) {
        all.remove(inh.id);
        try (PreparedStatement ps = db.getConnection().prepareStatement("DELETE FROM inhabitants WHERE id = ?")) {
            ps.setInt(1, inh.id);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /* =========================
       COMPTES
       ========================= */

    public List<Inhabitant> living() {
        return all.values().stream().filter(Inhabitant::alive).toList();
    }

    private int mourning() {
        return (int) all.values().stream().filter(i -> !i.alive()).count();
    }

    /**
     * Places : lits des habitations conformes (dernier relevé connu). Une maison devenue
     * incomplète garde ses habitants (ils ne partent pas) mais n'offre plus de place libre.
     */
    public int capacity() {
        int total = 0;
        for (Map.Entry<Integer, Integer> e : bedsByBuilding.entrySet()) {
            long inside = all.values().stream().filter(i -> i.buildingId == e.getKey()).count();
            total += (int) Math.max(e.getValue(), inside);
        }
        return total;
    }

    /** « 12/16 » : habitants vivants / lits des habitations conformes (panneau Tab). */
    public String populationLine() {
        return living().size() + "/" + capacity();
    }

    /** Bulle d'une habitation : « Habitants : 2 / 3 lits (1 en deuil) ». */
    public String occupancy(Building b) {
        if (b.type() != BuildingType.HABITATION) return null;
        long alive = all.values().stream().filter(i -> i.buildingId == b.id() && i.alive()).count();
        long dead  = all.values().stream().filter(i -> i.buildingId == b.id() && !i.alive()).count();
        Integer beds = bedsByBuilding.get(b.id());
        return "§7Habitants : §f" + alive + " §7/ §f" + (beds == null ? "?" : beds) + " lits"
                + (dead > 0 ? " §8(" + dead + " en deuil)" : "");
    }

    /* =========================
       ENTRETIEN (toutes les 5 secondes)
       ========================= */

    private void tickMaintenance() {
        refreshBeds();
        validate();
        endMourning(false);
        tickTax();
        tickVisibility();
        long now = System.currentTimeMillis();
        if (now >= nextArrivalAt && !Bukkit.getOnlinePlayers().isEmpty()) {
            nextArrivalAt = now + arrivalMs;
            arrive();
        }
    }

    /** Lits des habitations conformes, d'après l'inspection en cache (zones chargées seulement). */
    private void refreshBeds() {
        Set<Integer> habitations = new HashSet<>();
        for (Building b : buildings.getAllBuildings()) {
            if (b.type() != BuildingType.HABITATION) continue;
            habitations.add(b.id());
            Inspection insp = inspector.peek(b);
            if (insp != null) bedsByBuilding.put(b.id(), insp.compliant() ? insp.beds().size() : 0);
        }
        bedsByBuilding.keySet().retainAll(habitations);
    }

    /** Maison supprimée ou devenue autre chose, lit retiré : l'habitant s'en va. */
    private void validate() {
        Map<Integer, Building> byId = new HashMap<>();
        for (Building b : buildings.getAllBuildings()) byId.put(b.id(), b);
        homes = byId;
        for (Inhabitant inh : new ArrayList<>(all.values())) {
            Building home = byId.get(inh.buildingId);
            if (home == null || home.type() != BuildingType.HABITATION) {
                depart(inh, "sa maison n'existe plus");
                continue;
            }
            Location bed = inh.bed();
            if (bed == null || !bed.getWorld().isChunkLoaded(inh.bx >> 4, inh.bz >> 4)) continue;
            if (!Tag.BEDS.isTagged(bed.getBlock().getType())) depart(inh, "son lit a été retiré");
        }
    }

    /** Deuil terminé : le lit se libère pour un nouvel habitant. */
    private int endMourning(boolean now) {
        long t = System.currentTimeMillis();
        int ended = 0;
        for (Inhabitant inh : new ArrayList<>(all.values())) {
            if (inh.alive() || inh.diedAt == null) continue;
            if (now || t - inh.diedAt >= mourningMs) {
                delete(inh);
                ended++;
            }
        }
        return ended;
    }

    /** Impôt : à chaque nouveau jour de jeu (aube), un versement par habitant vivant. */
    private void tickTax() {
        World world = cityWorld();
        if (world == null) return;
        long day = world.getFullTime() / 24000L;
        if (lastTaxDay < 0) { lastTaxDay = day; return; }
        if (day <= lastTaxDay) return;
        lastTaxDay = day; // un seul versement même si le temps a sauté de plusieurs jours
        payTax();
    }

    /** Verse l'impôt de tous les habitants vivants ; renvoie le montant. */
    public int payTax() {
        int n = living().size();
        if (n == 0 || tax == 0) return 0;
        int amount = n * tax;
        int total = city.addCityCoins(amount);
        Bukkit.broadcastMessage("§6💰 Impôt de l'aube : §f" + n + " habitant" + (n > 1 ? "s" : "") + " §6→ §f+"
                + amount + " coins §7(caisse : " + total + ")");
        return amount;
    }

    private World cityWorld() {
        for (Building b : buildings.getAllBuildings()) {
            World w = Bukkit.getWorld(b.world());
            if (w != null) return w;
        }
        return Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
    }

    /* =========================
       ARRIVÉE, DÉPART, MORT
       ========================= */

    /** Un nouvel habitant s'installe dans un lit libre ; false s'il n'y en a pas. */
    public boolean arrive() {
        List<FreeBed> free = freeBeds();
        if (free.isEmpty()) return false;
        FreeBed fb = free.get(0);
        Inhabitant inh = insert(pickName(), skins.nextIndex(), fb.building(), fb.bed());
        if (inh == null) return false;
        long inHouse = all.values().stream().filter(i -> i.buildingId == fb.building().id()).count();
        Bukkit.broadcastMessage("§a🏠 " + inh.name + " §7s'installe à Valmont §8(" + fb.building().name()
                + ", lit " + inHouse + "/" + bedsByBuilding.getOrDefault(fb.building().id(), (int) inHouse) + ")");
        // Arrivée à pied depuis la lisière, si l'habitant peut être affiché
        if (spawnedCount() < maxVisible) {
            Location bed = inh.bed();
            Location from = bed == null ? null : edgePoint(bed);
            NPC npc = spawn(inh, from != null ? from : standNear(inh));
            if (npc != null && npc.isSpawned() && bed != null) SafeNavigation.walkTo(npc, standNear(inh), walkSpeed, true);
        }
        return true;
    }

    /** Lits libres des habitations conformes (un mort en deuil occupe encore le sien). */
    private List<FreeBed> freeBeds() {
        Set<String> taken = new HashSet<>();
        for (Inhabitant inh : all.values()) taken.add(inh.bedKey());
        List<FreeBed> free = new ArrayList<>();
        for (Building b : buildings.getAllBuildings()) {
            if (b.type() != BuildingType.HABITATION) continue;
            Inspection insp = inspector.inspect(b);
            bedsByBuilding.put(b.id(), insp.compliant() ? insp.beds().size() : 0);
            if (!insp.compliant()) continue;
            for (BlockVector bed : insp.beds()) {
                if (!taken.contains(key(b.world(), bed.getBlockX(), bed.getBlockY(), bed.getBlockZ()))) {
                    free.add(new FreeBed(b, bed));
                }
            }
        }
        return free;
    }

    private String pickName() {
        Set<String> used = new HashSet<>();
        for (Inhabitant inh : all.values()) used.add(inh.name);
        List<String> pool = new ArrayList<>(NAMES);
        java.util.Collections.shuffle(pool);
        for (String n : pool) if (!used.contains(n)) return n;
        String base = pool.get(0);
        for (int i = 2; ; i++) if (!used.contains(base + " " + i)) return base + " " + i; // plus de 55 habitants
    }

    /** L'habitant quitte la ville (lit retiré, maison supprimée). */
    private void depart(Inhabitant inh, String reason) {
        despawn(inh, true);
        delete(inh);
        if (inh.alive()) {
            Bukkit.broadcastMessage("§e🚪 " + inh.name + " §7quitte Valmont : " + reason + ".");
        }
    }

    /** Un habitant meurt : son lit reste en deuil, puis accueille quelqu'un d'autre. */
    private void die(Inhabitant inh) {
        inh.state  = State.MORT;
        inh.diedAt = System.currentTimeMillis();
        saveDeath(inh);
        sleeping.remove(inh.id);
        idleSince.remove(inh.id);
        Building home = buildings.getById(inh.buildingId);
        Bukkit.broadcastMessage("§c☠ " + inh.name + " §7a été tué" + (home != null ? " §8(" + home.name() + ")" : "")
                + "§7. Un nouvel habitant viendra dans " + Math.round(mourningMs / 60_000.0) + " min.");
        NPC npc = npcs.remove(inh.id);
        if (npc != null) Bukkit.getScheduler().runTask(plugin, () -> npc.destroy()); // après l'événement de mort
    }

    /* =========================
       AFFICHAGE (plafond de PNJ)
       ========================= */

    /** Les habitants les plus proches des joueurs sont affichés, dans la limite de max-visibles. */
    private void tickVisibility() {
        List<Inhabitant> candidates = new ArrayList<>();
        Map<Integer, Double> distance = new HashMap<>();
        for (Inhabitant inh : living()) {
            if (inh.state != State.HABITANT) continue; // formation, soldat : phase 5
            Location bed = inh.bed();
            if (bed == null || !bed.getWorld().isChunkLoaded(inh.bx >> 4, inh.bz >> 4)) {
                despawn(inh, false);
                continue;
            }
            double best = Double.MAX_VALUE;
            for (Player p : bed.getWorld().getPlayers()) best = Math.min(best, p.getLocation().distanceSquared(bed));
            distance.put(inh.id, best);
            candidates.add(inh);
        }
        candidates.sort(Comparator.comparingDouble(i -> distance.get(i.id)));
        for (int i = 0; i < candidates.size(); i++) {
            Inhabitant inh = candidates.get(i);
            NPC npc = npcs.get(inh.id);
            boolean shown = npc != null && npc.isSpawned();
            if (i < maxVisible) {
                if (!shown) spawn(inh, standNear(inh));
            } else if (shown) {
                despawn(inh, false);
            }
        }
    }

    private int spawnedCount() {
        int n = 0;
        for (NPC npc : npcs.values()) if (npc.isSpawned()) n++;
        return n;
    }

    /** Fait apparaître l'habitant à l'endroit donné (PNJ créé au premier besoin). */
    private NPC spawn(Inhabitant inh, Location at) {
        if (at == null) return null;
        NPC npc = npcs.get(inh.id);
        if (npc == null) {
            npc = registry.createNPC(EntityType.PLAYER, inh.name);
            npc.setProtected(false);                               // les monstres peuvent le blesser
            npc.data().set(NPC.Metadata.TARGETABLE, true);
            npc.data().set(NPC.Metadata.DROPS_ITEMS, false);
            npc.data().set(NPC.Metadata.PATHFINDER_OPEN_DOORS, true);
            npc.data().set(NPC_KEY, inh.id);
            skins.apply(npc, skins.size() == 0 || inh.skin < 0 ? -1 : Math.floorMod(inh.skin, skins.size()));
            npcs.put(inh.id, npc);
        }
        if (!npc.isSpawned()) npc.spawn(at);
        npc.getNavigator().getDefaultParameters().speedModifier(walkSpeed);
        return npc;
    }

    /** Retire le PNJ du monde (destroy = il ne reviendra pas). */
    private void despawn(Inhabitant inh, boolean destroy) {
        sleeping.remove(inh.id);
        idleSince.remove(inh.id);
        NPC npc = destroy ? npcs.remove(inh.id) : npcs.get(inh.id);
        if (npc == null) return;
        if (destroy) npc.destroy();
        else if (npc.isSpawned()) npc.despawn();
    }

    /* =========================
       VIE QUOTIDIENNE (deux fois par seconde)
       ========================= */

    private void tickBehaviour() {
        ticks++;
        long now = System.currentTimeMillis();
        for (Map.Entry<Integer, NPC> e : new ArrayList<>(npcs.entrySet())) {
            Inhabitant inh = all.get(e.getKey());
            NPC npc = e.getValue();
            if (inh == null || !inh.alive() || !npc.isSpawned()) continue;
            Location bed = inh.bed();
            if (bed == null) continue;
            Entity body = npc.getEntity();
            boolean walking = npc.getNavigator().isNavigating();
            boolean night = isNight(bed.getWorld());

            // Sorti de la ville (poussé, en fuite) : il rentre chez lui
            if (!claims.isClaimed(body.getLocation())) {
                wake(inh, npc);
                if (!walking) SafeNavigation.walkTo(npc, standNear(inh), walkSpeed, true);
                continue;
            }

            if (night) {
                // La nuit : il rentre se coucher
                if (sleeping.contains(inh.id)) continue;
                Location stand = standNear(inh);
                if (stand != null && body.getLocation().distanceSquared(stand) <= 2.5 * 2.5) {
                    sleep(inh, npc, bed);
                } else if (!walking && stand != null) {
                    SafeNavigation.walkTo(npc, stand, walkSpeed, true);
                }
                continue;
            }

            // Le jour : il se lève et flâne autour de sa maison
            wake(inh, npc);
            if (walking) { idleSince.remove(inh.id); continue; }
            long idle = idleSince.computeIfAbsent(inh.id, k -> now);
            long pause = strollPause / 2 + (strollPause == 0 ? 0 : Math.floorMod(inh.id * 7919L, strollPause));
            if (now - idle < pause) continue;
            Location target = randomSpot(bed, strollRadius);
            if (target != null && SafeNavigation.walkTo(npc, target, walkSpeed, false) == SafeNavigation.Result.WALKING) {
                idleSince.remove(inh.id);
            }
        }
        // Toutes les 2 secondes, la nuit : les monstres proches s'en prennent aux habitants
        if (nightAttacks && ticks % 4 == 0) provokeMonsters();
    }

    private static boolean isNight(World world) {
        long t = world.getTime();
        return t >= 12500 && t < 23500;
    }

    private void sleep(Inhabitant inh, NPC npc, Location bed) {
        try {
            npc.getNavigator().cancelNavigation();
            npc.getOrAddTrait(SleepTrait.class).setSleeping(bed.clone().add(0.5, 0, 0.5));
            sleeping.add(inh.id);
        } catch (Throwable t) {
            sleeping.add(inh.id); // sommeil indisponible : il reste près de son lit
        }
    }

    private void wake(Inhabitant inh, NPC npc) {
        if (!sleeping.remove(inh.id)) return;
        try {
            npc.getOrAddTrait(SleepTrait.class).setSleeping(null);
        } catch (Throwable ignored) {
        }
    }

    /** La nuit, un monstre sans cible près d'un habitant le prend pour cible. */
    private void provokeMonsters() {
        for (Map.Entry<Integer, NPC> e : npcs.entrySet()) {
            NPC npc = e.getValue();
            if (!npc.isSpawned() || !isNight(npc.getEntity().getWorld())) continue;
            for (Entity near : npc.getEntity().getNearbyEntities(16, 6, 16)) {
                if (near instanceof Monster monster && monster.getTarget() == null && !registry.isNPC(near)) {
                    monster.setTarget((org.bukkit.entity.LivingEntity) npc.getEntity());
                }
            }
        }
    }

    /* =========================
       POINTS DE PASSAGE
       ========================= */

    /** Endroit où se tenir près de son lit, dans sa maison (le lit lui-même en dernier recours). */
    private Location standNear(Inhabitant inh) {
        Location bed = inh.bed();
        if (bed == null) return null;
        Building home = homes.get(inh.buildingId); // cache relu toutes les 5 s (pas de requête à chaque pas)
        World w = bed.getWorld();
        Location best = null;
        double bestDist = Double.MAX_VALUE;
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                int x = inh.bx + dx, z = inh.bz + dz;
                if (home != null && (x < home.x1() || x > home.x2() || z < home.z1() || z > home.z2())) continue;
                for (int dy = 1; dy >= -1; dy--) {
                    Block feet = w.getBlockAt(x, inh.by + dy, z);
                    if (standable(feet)) {
                        double d = dx * dx + dz * dz + dy * dy;
                        if (d > 0 && d < bestDist) { bestDist = d; best = feet.getLocation().add(0.5, 0, 0.5); }
                        break;
                    }
                }
            }
        }
        return best != null ? best : bed.clone().add(0.5, 0.6, 0.5);
    }

    /** Point de la lisière (chunk revendiqué au bord de la ville) le plus proche, à portée de marche. */
    private Location edgePoint(Location home) {
        World w = home.getWorld();
        int hx = home.getBlockX() >> 4, hz = home.getBlockZ() >> 4;
        Location best = null;
        double bestDist = Double.MAX_VALUE;
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                int cx = hx + dx, cz = hz + dz;
                if ((dx == 0 && dz == 0) || !w.isChunkLoaded(cx, cz) || !claims.isEdge(w, cx, cz)) continue;
                double d = dx * dx + dz * dz;
                if (d >= bestDist) continue;
                Location spot = randomSpot(new Location(w, (cx << 4) + 8, home.getBlockY(), (cz << 4) + 8), 6);
                if (spot != null) { bestDist = d; best = spot; }
            }
        }
        return best;
    }

    /**
     * Point où se tenir au hasard à moins de radius blocs de center, dans un chunk revendiqué et
     * chargé, à peu près au niveau de center (pas sur les toits) ; null si rien trouvé.
     */
    private Location randomSpot(Location center, int radius) {
        World world = center.getWorld();
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (int attempt = 0; attempt < 10; attempt++) {
            int x = center.getBlockX() + rnd.nextInt(-radius, radius + 1);
            int z = center.getBlockZ() + rnd.nextInt(-radius, radius + 1);
            if (!world.isChunkLoaded(x >> 4, z >> 4) || !claims.isClaimed(world, x >> 4, z >> 4)) continue;
            for (int dy = 3; dy >= -4; dy--) {
                Block feet = world.getBlockAt(x, center.getBlockY() + dy, z);
                if (standable(feet)) return feet.getLocation().add(0.5, 0, 0.5);
            }
        }
        return null;
    }

    private static boolean standable(Block feet) {
        Block head = feet.getRelative(0, 1, 0), below = feet.getRelative(0, -1, 0);
        return feet.isPassable() && !feet.isLiquid() && head.isPassable() && !head.isLiquid()
                && below.getType().isSolid() && !Tag.BEDS.isTagged(below.getType());
    }

    /* =========================
       ÉVÉNEMENTS
       ========================= */

    /** Les joueurs ne peuvent pas blesser les habitants (seuls les monstres le peuvent). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (registry == null || !registry.isNPC(event.getEntity())) return;
        Entity damager = event.getDamager();
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) damager = shooter;
        if (damager instanceof Player && !registry.isNPC(damager)) event.setCancelled(true);
    }

    @EventHandler
    public void onNpcDeath(NPCDeathEvent event) {
        if (registry == null || event.getNPC().getOwningRegistry() != registry) return;
        event.getDrops().clear();
        event.setDroppedExp(0);
        Object id = event.getNPC().data().get(NPC_KEY);
        Inhabitant inh = id instanceof Integer i ? all.get(i) : null;
        if (inh != null && inh.alive()) die(inh);
    }

    /** Ni butin, ni expérience, ni message de mort dans le chat (un PNJ de type joueur meurt comme un joueur). */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDeath(EntityDeathEvent event) {
        if (registry == null || !registry.isNPC(event.getEntity())) return;
        event.getDrops().clear();
        event.setDroppedExp(0);
        if (event instanceof PlayerDeathEvent death) {
            death.setKeepInventory(true);
            death.setKeepLevel(true);
            death.setDeathMessage(null);
        }
    }

    /** Lit cassé : son habitant s'en va tout de suite (sans attendre l'entretien). */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBedBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!(block.getBlockData() instanceof Bed bed)) return;
        Block head = bed.getPart() == Bed.Part.HEAD ? block : block.getRelative(bed.getFacing());
        String k = key(block.getWorld().getName(), head.getX(), head.getY(), head.getZ());
        for (Inhabitant inh : new ArrayList<>(all.values())) {
            if (inh.bedKey().equals(k)) depart(inh, "son lit a été retiré");
        }
    }

    /* =========================
       COMMANDES (/city habitants)
       ========================= */

    /** Bilan : population, lits, deuils, puis chaque habitation et ses habitants. */
    public void list(Player player) {
        refreshBeds();
        player.sendMessage("§6🏘 Habitants de Valmont : §f" + populationLine() + " §7(lits des habitations conformes)"
                + (mourning() > 0 ? " §8— " + mourning() + " lit(s) en deuil" : ""));
        for (Building b : buildings.getAllBuildings()) {
            if (b.type() != BuildingType.HABITATION) continue;
            List<String> names = new ArrayList<>();
            long t = System.currentTimeMillis();
            for (Inhabitant inh : all.values()) {
                if (inh.buildingId != b.id()) continue;
                names.add(inh.alive() ? "§f" + inh.name
                        : "§8☠ " + inh.name + " (" + Math.max(0, (mourningMs - (t - inh.diedAt)) / 60_000) + " min)");
            }
            Integer beds = bedsByBuilding.get(b.id());
            player.sendMessage("§7• §e" + b.name() + " §7(" + (beds == null ? "?" : beds) + " lits"
                    + (beds != null && beds == 0 ? ", §cincomplète§7" : "") + ") : "
                    + (names.isEmpty() ? "§8personne" : String.join("§7, ", names)));
        }
        long wait = Math.max(0, nextArrivalAt - System.currentTimeMillis()) / 1000;
        player.sendMessage("§8Prochaine arrivée possible dans " + wait / 60 + " min " + wait % 60 + " s ; impôt : "
                + tax + " coin(s) par habitant à l'aube.");
    }

    /** Fait mourir un habitant (test) ; false s'il est introuvable. */
    public boolean kill(String name) {
        for (Inhabitant inh : all.values()) {
            if (!inh.alive() || !inh.name.equalsIgnoreCase(name)) continue;
            NPC npc = npcs.get(inh.id);
            if (npc != null && npc.isSpawned() && npc.getEntity() instanceof org.bukkit.entity.LivingEntity body) {
                body.setHealth(0); // mort « réelle » : mêmes événements qu'un coup de monstre
            } else {
                die(inh);
            }
            return true;
        }
        return false;
    }

    /** Fin immédiate de tous les deuils (test) ; renvoie le nombre de lits libérés. */
    public int endAllMourning() {
        return endMourning(true);
    }

    /** Noms des habitants vivants (complétion de commande). */
    public List<String> livingNames() {
        return living().stream().map(i -> i.name).toList();
    }

    private static String key(String world, int x, int y, int z) {
        return world + ":" + x + ":" + y + ":" + z;
    }
}
