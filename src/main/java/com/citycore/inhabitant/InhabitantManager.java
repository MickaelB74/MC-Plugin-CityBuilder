package com.citycore.inhabitant;

import com.citycore.building.Building;
import com.citycore.building.BuildingInspector;
import com.citycore.building.BuildingManager;
import com.citycore.building.BuildingType;
import com.citycore.building.Inspection;
import com.citycore.city.CityManager;
import com.citycore.city.ClaimCache;
import com.citycore.npc.CityNPC;
import com.citycore.npc.NPCDataManager;
import com.citycore.npc.NPCManager;
import com.citycore.npc.NPCState;
import com.citycore.npc.SafeNavigation;
import com.citycore.util.DatabaseManager;
import com.citycore.util.GroundFinder;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.ai.EntityTarget;
import net.citizensnpcs.api.event.DespawnReason;
import net.citizensnpcs.api.event.NPCDeathEvent;
import net.citizensnpcs.api.npc.MemoryNPCDataStore;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.npc.NPCRegistry;
import net.citizensnpcs.api.trait.trait.Equipment;
import net.citizensnpcs.trait.EntityPoseTrait;
import net.citizensnpcs.trait.SleepTrait;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Bed;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Display;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Pose;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.BlockVector;
import org.bukkit.util.Vector;

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
 * Habitants de la ville (phase 3 du plan « Valmont vivante ») et leur garde (phase 5).
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
 *
 * Garde : chez Roland installé, on forme un habitant à un rôle (soldat, archer…, section soldats
 * de config.yml). La recrue s'entraîne à l'atelier de Roland (temps compté quand un joueur est
 * connecté, plusieurs recrues à la fois selon le rang de Roland), puis devient garde : uniforme
 * (soldats.skins), équipement et points de vie de son rôle. Les gardes patrouillent sur le
 * terrain de la ville, hors des bâtiments, de jour comme de nuit (ils ne dorment pas), et
 * attaquent les monstres (au contact, ou à l'arc ; pas plus de soldats.max-par-cible gardes au
 * contact du même). Pendant un siège, la garde se répartit en groupes, un par secteur du front,
 * qui vont chacun à la rencontre d'une escouade ennemie. Ils gardent leur lit (la population ne dépasse
 * pas le nombre de lits) et ne paient pas l'impôt. Un garde mort n'est pas remplacé : son lit
 * accueillera un nouvel habitant, qu'il faudra former à son tour.
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
        public String role;         // rôle de la garde (recrue ou garde), null pour un habitant
        public long trainingLeft;   // ms de formation restantes
        public int uniform = -1;    // numéro du skin d'uniforme (soldats.skins)
        public boolean test;        // garde de test (commande op) : sans lit, jamais enregistré
        public String employer;     // emploi : artisan pour qui il travaille (tag), null = sans emploi
        public long workLeft;       // ms avant la fin de son cycle de travail en cours

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
    /** Délai entre deux coups d'épée d'un garde contre un soldat ennemi. */
    private static final long SWING_COOLDOWN_MS = 900;
    /** Délai entre deux flèches d'un archer. */
    private static final long BOW_COOLDOWN_MS = 1500;

    private final JavaPlugin        plugin;
    private final DatabaseManager   db;
    private final BuildingManager   buildings;
    private final BuildingInspector inspector;
    private final CityManager       city;
    private final NPCDataManager    npcData;
    private final NPCManager        npcManager; // position de Roland (place des recrues)
    private final ClaimCache        claims;
    private final InhabitantSkins   skins;
    private final InhabitantSkins   uniforms;
    private final GuardConfig       guard;

    private NPCRegistry registry;
    private final Map<Integer, Inhabitant> all = new LinkedHashMap<>();
    private final Map<Integer, NPC> npcs = new HashMap<>();          // habitant → PNJ (créé à la première apparition)
    private final Map<Integer, Long> idleSince = new HashMap<>();    // pause de promenade / de ronde
    private final Map<Integer, Long> lastShot = new HashMap<>();     // dernier tir d'un archer
    private final Map<Integer, Long> lastSwing = new HashMap<>();    // dernier coup au contact (contre un soldat)
    private final Set<Integer> sleeping = new HashSet<>();
    private final Map<Integer, TextDisplay> timers = new HashMap<>(); // minuteur au-dessus d'une recrue
    private final Map<Integer, Long> awaySince = new HashMap<>();     // recrue pas encore à sa place depuis…
    private final Map<Integer, Integer> bedsByBuilding = new HashMap<>(); // lits des habitations conformes (dernier relevé)
    private Map<Integer, Building> homes = new HashMap<>();             // bâtiments par id (relus toutes les 5 s)
    private Building rolandBuilding;                                     // atelier de Roland (formation)
    private java.util.function.Supplier<SiegeFront> siegeFront;          // front d'un siège en cours (phase 4), sinon null
    private Runnable dawnListener;                                       // besoins (phase 6) : repas et soins de l'aube
    private WorkPlace workPlace;                                         // emplois : annexes de travail des artisans
    private final Map<Integer, String> lastGain = new HashMap<>();       // employé → « +11 objets » (affiché un instant)
    private final Map<Integer, Long> lastGainAt = new HashMap<>();
    private int workSaves;                                               // enregistrement des cycles toutes les 30 s
    private java.util.function.DoubleSupplier taxMultiplier = () -> 1.0;  // impôt selon la satisfaction
    private java.util.function.DoubleSupplier arrivalFactor = () -> 1.0;  // délai d'arrivée selon la satisfaction (0 = aucune)
    private java.util.function.Predicate<Entity> hostileNpc;             // soldat d'une armée ennemie (siège « bannière »)
    private java.util.function.BooleanSupplier siegeAssault;             // vague en cours : la garde sort combattre
    private java.util.function.Supplier<List<SiegeFront.Band>> siegeBands; // escouades du siège en cours
    private SiegeFront front;                                            // front relu à chaque passage
    private final Map<Integer, Location> posts = new HashMap<>();        // garde → son poste sur le front
    // Champ de bataille (siège) : groupes de la garde, escouades affrontées, mêlées en cours
    private Set<Integer> postedRoster = Set.of();                        // gardes placés au dernier calcul des postes
    private final Map<Integer, Integer> guardGroup = new HashMap<>();    // garde → son groupe
    private double[] groupLane = new double[0];                          // secteur de chaque groupe (position sur la largeur du front)
    private Location[] groupAnchor = new Location[0];                    // poste au centre de ce secteur
    private final Map<Integer, Integer> groupBand = new HashMap<>();     // groupe → escouade qu'il va affronter
    private boolean assaulting;                                          // vague en cours (relu à chaque passage)
    private List<SiegeFront.Band> bands = List.of();                     // escouades présentes (relevé du passage)
    private List<LivingEntity> attackersNow = List.of();                 // tous les assaillants présents
    private final Map<Integer, LivingEntity> fightTarget = new HashMap<>(); // garde → adversaire en cours
    private final Map<Integer, Long> nextPick = new HashMap<>();         // garde → prochain choix d'adversaire
    private final Map<Integer, Location> supportGoal = new HashMap<>();  // garde → place d'attente près d'une mêlée
    private final Engagements meleeTally = new Engagements();            // gardes au contact de chaque ennemi
    private final Map<Integer, Location> goals = new HashMap<>();        // garde → poste vers lequel il court
    private final Map<Integer, Location> returning = new HashMap<>();    // après un siège : retour vers son quartier
    private final Map<Integer, Long> returnUntil = new HashMap<>();
    private java.util.function.BiConsumer<Inhabitant, Boolean> deathListener; // (habitant, était garde)

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
    private long lastMaintenance;
    private int  ticks;

    public InhabitantManager(JavaPlugin plugin, DatabaseManager db, BuildingManager buildings,
                             BuildingInspector inspector, CityManager city, NPCDataManager npcData,
                             NPCManager npcManager) {
        this.plugin    = plugin;
        this.db        = db;
        this.buildings = buildings;
        this.inspector = inspector;
        this.city      = city;
        this.npcData   = npcData;
        this.npcManager = npcManager;
        this.claims    = new ClaimCache(city);
        this.skins     = new InhabitantSkins(plugin, InhabitantSkins.HABITANTS);
        this.uniforms  = new InhabitantSkins(plugin, InhabitantSkins.SOLDATS);
        this.guard     = new GuardConfig(plugin);
    }

    /* =========================
       DÉMARRAGE / ARRÊT
       ========================= */

    public void start() {
        loadSettings();
        registry = CitizensAPI.createAnonymousNPCRegistry(new MemoryNPCDataStore());
        load();
        nextArrivalAt = System.currentTimeMillis() + arrivalMs;
        lastMaintenance = System.currentTimeMillis();
        validate(); // cache des maisons, départs en attente
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickBehaviour, 40L, 10L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickMaintenance, 60L, 100L);
        plugin.getLogger().info("Habitants : " + living().size() + " en ville (dont " + guards().size()
                + " garde(s) et " + trainees().size() + " recrue(s)), " + mourning() + " lit(s) en deuil.");
    }

    /** Arrêt du serveur : les PNJ disparaissent (ils sont recréés depuis la base au démarrage). */
    public void shutdown() {
        for (TextDisplay d : timers.values()) d.remove();
        timers.clear();
        for (NPC npc : new ArrayList<>(npcs.values())) npc.destroy();
        npcs.clear();
    }

    /** Relit les sections habitants et soldats de config.yml (/city habitants recharger). */
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
        uniforms.reload();
        guard.reload();
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
                inh.role = rs.getString("role");
                inh.trainingLeft = rs.getLong("training_left");
                inh.uniform = rs.getInt("uniform");
                inh.employer = rs.getString("employer");
                inh.workLeft = rs.getLong("work_left");
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
        if (inh.test) return;
        try (PreparedStatement ps = db.getConnection().prepareStatement(
                "UPDATE inhabitants SET state = 'MORT', died_at = ? WHERE id = ?")) {
            ps.setLong(1, inh.diedAt);
            ps.setInt(2, inh.id);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /** Emploi : artisan employeur et cycle de travail en cours. */
    private void saveJob(Inhabitant inh) {
        if (inh.test) return;
        try (PreparedStatement ps = db.getConnection().prepareStatement(
                "UPDATE inhabitants SET employer = ?, work_left = ? WHERE id = ?")) {
            ps.setString(1, inh.employer);
            ps.setLong(2, inh.workLeft);
            ps.setInt(3, inh.id);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /** État de la garde : statut, rôle, formation restante, uniforme. */
    private void saveGuard(Inhabitant inh) {
        if (inh.test) return;
        try (PreparedStatement ps = db.getConnection().prepareStatement(
                "UPDATE inhabitants SET state = ?, role = ?, training_left = ?, uniform = ? WHERE id = ?")) {
            ps.setString(1, inh.state.name());
            ps.setString(2, inh.role);
            ps.setLong(3, inh.trainingLeft);
            ps.setInt(4, inh.uniform);
            ps.setInt(5, inh.id);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void delete(Inhabitant inh) {
        all.remove(inh.id);
        if (inh.test) return;
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

    /** Habitants vivants (gardes et recrues compris). */
    public List<Inhabitant> living() {
        return all.values().stream().filter(Inhabitant::alive).toList();
    }

    /** Habitants civils (ni garde, ni recrue) : ceux qu'on peut former. */
    public List<Inhabitant> civilians() {
        return all.values().stream().filter(i -> i.state == State.HABITANT).toList();
    }

    public List<Inhabitant> guards() {
        return all.values().stream().filter(i -> i.state == State.SOLDAT).toList();
    }

    public List<Inhabitant> trainees() {
        return all.values().stream().filter(i -> i.state == State.FORMATION).toList();
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
        return living().stream().filter(i -> !i.test).count() + "/" + capacity();
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
        long now = System.currentTimeMillis();
        long elapsed = now - lastMaintenance;
        lastMaintenance = now;
        refreshBeds();
        validate();
        endMourning(false);
        tickTax();
        tickTraining(elapsed);
        tickWork(elapsed);
        tickVisibility();
        if (now >= nextArrivalAt && !Bukkit.getOnlinePlayers().isEmpty()) {
            double factor = arrivalFactor.getAsDouble();
            if (factor <= 0) {
                nextArrivalAt = now + 60_000; // trop mécontents : plus personne ne vient (on revérifie dans 1 min)
            } else {
                nextArrivalAt = now + Math.round(arrivalMs * factor);
                arrive();
            }
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
        rolandBuilding = byId.values().stream().filter(b -> CityNPC.GUARD.tag.equals(b.npcTag())).findFirst().orElse(null);
        for (Inhabitant inh : new ArrayList<>(all.values())) {
            if (inh.test) continue; // garde de test : sans maison ni lit
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

    /** Impôt : à chaque nouveau jour de jeu (aube), un versement par habitant civil. */
    private void tickTax() {
        World world = cityWorld();
        if (world == null) return;
        long day = world.getFullTime() / 24000L;
        if (lastTaxDay < 0) { lastTaxDay = day; return; }
        if (day <= lastTaxDay) return;
        lastTaxDay = day; // un seul versement même si le temps a sauté de plusieurs jours
        dawn();
    }

    /** L'aube : repas et soins (besoins, phase 6), puis impôt selon la satisfaction. */
    public int dawn() {
        if (dawnListener != null) dawnListener.run();
        return payTax();
    }

    /** Verse l'impôt des habitants civils (les gardes et les recrues servent la ville) ; renvoie le montant. */
    public int payTax() {
        int n = (int) civilians().stream().filter(i -> !i.test).count();
        if (n == 0 || tax == 0) return 0;
        double factor = Math.max(0, taxMultiplier.getAsDouble());
        int amount = (int) Math.round(n * tax * factor);
        if (amount <= 0) {
            Bukkit.broadcastMessage("§6💰 Impôt de l'aube : §cles habitants ne paient rien §7(trop mécontents).");
            return 0;
        }
        int total = city.addCityCoins(amount);
        Bukkit.broadcastMessage("§6💰 Impôt de l'aube : §f" + n + " habitant" + (n > 1 ? "s" : "") + " §6→ §f+"
                + amount + " coins §7(caisse : " + total + ")" + (factor != 1.0 ? " §8(satisfaction : ×"
                + String.format(java.util.Locale.FRANCE, "%.1f", factor) + ")" : ""));
        return amount;
    }

    /** Impôt de base par habitant civil (habitants.impot). */
    public int taxPerInhabitant() {
        return tax;
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
        if (spawnedCivilians() < maxVisible) {
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
            Bukkit.broadcastMessage("§e🚪 " + label(inh) + " §7quitte Valmont : " + reason + ".");
        }
    }

    /** Un habitant (ou un garde) meurt : son lit reste en deuil, puis accueille quelqu'un d'autre. */
    private void die(Inhabitant inh) {
        String who = label(inh);
        boolean wasGuard = inh.state == State.SOLDAT || inh.state == State.FORMATION;
        if (deathListener != null) deathListener.accept(inh, wasGuard);
        inh.state  = State.MORT;
        inh.diedAt = System.currentTimeMillis();
        saveDeath(inh);
        sleeping.remove(inh.id);
        idleSince.remove(inh.id);
        awaySince.remove(inh.id);
        removeTimer(inh.id);
        Building home = homes.get(inh.buildingId);
        Bukkit.broadcastMessage("§c☠ " + who + " §7" + (wasGuard ? "est tombé" : "a été tué")
                + (home != null ? " §8(" + home.name() + ")" : "") + "§7. "
                + (inh.test ? "§8(garde de test)"
                   : wasGuard ? "Sa place dans la garde est libre : formez une nouvelle recrue chez Roland."
                   : "Un nouvel habitant viendra dans " + Math.round(mourningMs / 60_000.0) + " min."));
        NPC npc = npcs.remove(inh.id);
        if (npc != null) Bukkit.getScheduler().runTask(plugin, () -> npc.destroy()); // après l'événement de mort
        if (inh.test) all.remove(inh.id);
    }

    /** « Jehan », « l'archer Jehan »… */
    public String label(Inhabitant inh) {
        GuardConfig.Role role = guard.role(inh.role);
        if (role == null || (inh.state != State.SOLDAT && inh.state != State.FORMATION)) return inh.name;
        return inh.name + ", " + (inh.state == State.FORMATION ? "recrue " : "") + role.name().toLowerCase();
    }

    /* =========================
       LA GARDE (phase 5)
       ========================= */

    public GuardConfig guard() {
        return guard;
    }

    /** Rang de Roland s'il est installé dans son atelier (0 sinon : pas de formation possible). */
    public int guardRank() {
        return npcData.getState(CityNPC.GUARD) == NPCState.ASSIGNED ? npcData.getLevel(CityNPC.GUARD) : 0;
    }

    /** Taille actuelle de la garde (gardes + recrues). */
    public int guardSize() {
        return guards().size() + trainees().size();
    }

    /** Liste du menu de la garde : gardes, puis recrues, puis habitants civils (qu'on peut former). */
    public List<Inhabitant> guardRoster() {
        List<Inhabitant> list = new ArrayList<>(guards());
        list.addAll(trainees());
        list.addAll(civilians());
        return list;
    }

    /** Raison pour laquelle on ne peut pas lancer de formation maintenant, ou null si c'est possible. */
    public String canRecruit() {
        int rank = guardRank();
        if (rank == 0) return "Roland doit être installé dans son atelier pour former des recrues.";
        if (guardSize() >= guard.maxGuards(rank)) {
            return "La garde est complète (" + guard.maxGuards(rank) + " au rang " + rank + " de Roland).";
        }
        if (trainees().size() >= guard.maxTraining(rank)) {
            return "Roland forme déjà " + guard.maxTraining(rank) + " recrue(s) à la fois.";
        }
        return null;
    }

    /** Nom de la maison d'un habitant (« ? » si elle n'est plus connue). */
    public String homeName(Inhabitant inh) {
        if (inh.test) return "aucun (garde de test)";
        Building b = homes.get(inh.buildingId);
        return b == null ? "?" : b.name();
    }

    /** « Vie : 18 / 30 » si le PNJ est affiché, sinon null. */
    public String healthLine(Inhabitant inh) {
        NPC npc = npcs.get(inh.id);
        if (npc == null || !npc.isSpawned() || !(npc.getEntity() instanceof LivingEntity body)) return null;
        AttributeInstance max = body.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        return "§7Vie : §f" + (int) Math.ceil(body.getHealth()) + " §7/ §f" + (int) (max == null ? 20 : max.getValue());
    }

    /**
     * Forme un habitant civil à un rôle, à l'atelier de Roland. Le menu est commun : n'importe quel
     * joueur peut lancer une formation, dans la limite du rang de Roland.
     * @return null si c'est fait, sinon la raison du refus
     */
    public String train(int inhabitantId, String roleKey) {
        String blocked = canRecruit();
        if (blocked != null) return blocked;
        int rank = guardRank();
        Inhabitant inh = all.get(inhabitantId);
        if (inh == null || inh.state != State.HABITANT) return "Cet habitant n'est plus disponible.";
        GuardConfig.Role role = guard.role(roleKey);
        if (role == null) return "Rôle inconnu.";
        if (role.rank() > rank) return role.name() + " : Roland doit être au rang " + role.rank() + ".";
        inh.state = State.FORMATION;
        if (inh.employer != null) { // une recrue quitte son emploi
            inh.employer = null;
            inh.workLeft = 0;
            saveJob(inh);
        }
        inh.role = role.key();
        inh.trainingLeft = role.trainingMs();
        saveGuard(inh);
        NPC npc = npcs.get(inh.id);
        if (npc != null) {
            if (npc.isSpawned()) {
                wake(inh, npc);                          // formé en pleine nuit : il se lève
                npc.getNavigator().cancelNavigation();
            } else {
                clearSleep(npc);
            }
            trainingGear(npc, role);
        }
        awaySince.remove(inh.id);
        Bukkit.broadcastMessage("§9🛡 " + inh.name + " §7entre en formation chez Roland : §f" + role.name()
                + " §8(" + minutes(role.trainingMs()) + ")");
        return null;
    }

    /** Fin de formation : la recrue devient garde (uniforme, équipement). */
    private void promote(Inhabitant inh) {
        inh.state = State.SOLDAT;
        inh.trainingLeft = 0;
        if (inh.uniform < 0) inh.uniform = uniforms.nextIndex();
        saveGuard(inh);
        refreshNpc(inh);
        GuardConfig.Role role = guard.role(inh.role);
        Bukkit.broadcastMessage("§9🛡 " + inh.name + " §7a terminé sa formation : §f"
                + (role != null ? role.name() : "garde") + " §7de la garde de Valmont.");
    }

    /**
     * Rend un garde (ou une recrue) à la vie civile : il redevient habitant (même lit), sa place
     * dans la garde se libère. @return null si c'est fait, sinon la raison du refus
     */
    public String dismiss(int inhabitantId) {
        Inhabitant inh = all.get(inhabitantId);
        if (inh == null || (inh.state != State.SOLDAT && inh.state != State.FORMATION)) return "Ce n'est pas un garde.";
        String who = label(inh);
        if (inh.test) {
            despawn(inh, true);
            all.remove(inh.id);
            Bukkit.broadcastMessage("§7🛡 " + who + " §8(garde de test) §7est retiré.");
            return null;
        }
        inh.state = State.HABITANT;
        inh.role = null;
        inh.trainingLeft = 0;
        saveGuard(inh);
        refreshNpc(inh);
        Bukkit.broadcastMessage("§7🛡 " + who + " §7rend son uniforme et redevient habitant.");
        return null;
    }

    /* =========================
       GARDES DE TEST (commandes op)
       ========================= */

    private int nextTestId = -1; // identifiants négatifs : jamais en base

    /**
     * Garde de test, créé tout de suite devant le joueur : même comportement qu'un garde formé
     * chez Roland (rondes, front d'un siège, combats), sans lit ni formation, hors limite de la
     * garde, jamais enregistré (disparaît au redémarrage). @param roleKey rôle, ou null = le premier
     */
    public Inhabitant spawnTestGuard(Location at, String roleKey) {
        GuardConfig.Role role = guard.role(roleKey);
        if (role == null) role = guard.roles().get(0);
        Inhabitant inh = new Inhabitant(nextTestId--, pickName(), skins.nextIndex(), -1, at.getWorld().getName(),
                at.getBlockX(), at.getBlockY(), at.getBlockZ(), State.SOLDAT, System.currentTimeMillis(), null);
        inh.test = true;
        inh.role = role.key();
        inh.uniform = uniforms.nextIndex();
        all.put(inh.id, inh);
        spawn(inh, at);
        return inh;
    }

    /** Retire tous les gardes de test ; renvoie leur nombre. */
    public int removeTestGuards() {
        int n = 0;
        for (Inhabitant inh : new ArrayList<>(all.values())) {
            if (!inh.test) continue;
            despawn(inh, true);
            all.remove(inh.id);
            n++;
        }
        return n;
    }

    /** Fin immédiate des formations (test) ; renvoie le nombre de nouveaux gardes. */
    public int finishTrainingNow() {
        int n = 0;
        for (Inhabitant inh : trainees()) { promote(inh); n++; }
        return n;
    }

    /** Formation : temps compté seulement quand un joueur est connecté et que Roland a son atelier. */
    private void tickTraining(long elapsed) {
        if (Bukkit.getOnlinePlayers().isEmpty() || guardRank() == 0) return;
        for (Inhabitant inh : trainees()) {
            inh.trainingLeft -= elapsed;
            if (inh.trainingLeft <= 0) promote(inh);
            else saveGuard(inh);
        }
    }

    /** Texture du skin d'un habitant ou de son uniforme (têtes du menu de la garde), ou null. */
    public String[] skinTexture(Inhabitant inh) {
        if (inh.state == State.SOLDAT && inh.uniform >= 0 && !uniforms.isEmpty()) return uniforms.texture(inh.uniform);
        return skins.texture(inh.skin);
    }

    /** « 12 min », « 45 s ». */
    public static String minutes(long ms) {
        long s = Math.max(0, ms) / 1000;
        return s >= 60 ? (s + 59) / 60 + " min" : s + " s";
    }

    /** Recrée le PNJ d'un habitant qui change d'état (skin, équipement), là où il se trouve. */
    private void refreshNpc(Inhabitant inh) {
        NPC npc = npcs.get(inh.id);
        Location at = npc != null && npc.isSpawned() ? npc.getEntity().getLocation() : null;
        despawn(inh, true);
        if (at != null) spawn(inh, at);
    }

    /* =========================
       AFFICHAGE (plafond de PNJ)
       ========================= */

    /**
     * Les habitants civils les plus proches des joueurs sont affichés, dans la limite de
     * max-visibles ; gardes et recrues le sont toujours (peu nombreux), près des joueurs.
     */
    private void tickVisibility() {
        List<Inhabitant> candidates = new ArrayList<>();
        List<Inhabitant> guardList = new ArrayList<>();
        Map<Integer, Double> distance = new HashMap<>();
        for (Inhabitant inh : living()) {
            if (inh.state == State.SOLDAT) { guardList.add(inh); continue; }
            if (inh.state == State.FORMATION && showTrainee(inh)) continue;
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
        // Gardes : affichés jusqu'à soldats.max-visibles, les plus proches des joueurs d'abord
        int cap = guard.maxVisible();
        if (guardList.size() > cap) {
            Map<Integer, Double> near = new HashMap<>();
            for (Inhabitant inh : guardList) near.put(inh.id, distanceToPlayers(inh));
            guardList.sort(Comparator.comparingDouble(i -> near.get(i.id)));
        }
        for (int i = 0; i < guardList.size(); i++) {
            if (i < cap) showGuard(guardList.get(i));
            else despawn(guardList.get(i), false);
        }
    }

    /** Distance (au carré) entre un habitant (là où il est, sinon son lit) et le joueur le plus proche. */
    private double distanceToPlayers(Inhabitant inh) {
        NPC npc = npcs.get(inh.id);
        Location at = npc != null && npc.isSpawned() ? npc.getEntity().getLocation() : inh.bed();
        if (at == null) return Double.MAX_VALUE;
        double best = Double.MAX_VALUE;
        for (Player p : at.getWorld().getPlayers()) best = Math.min(best, p.getLocation().distanceSquared(at));
        return best;
    }

    /** Garde absent du monde : il reprend sa ronde près d'un joueur présent en ville. */
    private void showGuard(Inhabitant inh) {
        NPC npc = npcs.get(inh.id);
        if (npc != null && npc.isSpawned()) return;
        World w = Bukkit.getWorld(inh.world);
        if (w == null) return;
        Location spot = null;
        for (Player p : w.getPlayers()) {
            if (!claims.isClaimed(p.getLocation())) continue;
            spot = patrolPoint(p.getLocation(), 24);
            if (spot != null) break;
        }
        if (spot == null && w.isChunkLoaded(inh.bx >> 4, inh.bz >> 4)) spot = patrolPoint(inh.bed(), 24);
        if (spot != null) spawn(inh, spot);
    }

    /** Recrue : à l'atelier de Roland s'il est chargé ; false = traitée comme un habitant (chez elle). */
    private boolean showTrainee(Inhabitant inh) {
        Location spot = trainingSpot(inh);
        if (spot == null) return false;
        NPC npc = npcs.get(inh.id);
        if (npc == null || !npc.isSpawned()) spawn(inh, spot);
        return true;
    }

    private int spawnedCivilians() {
        int n = 0;
        for (Map.Entry<Integer, NPC> e : npcs.entrySet()) {
            Inhabitant inh = all.get(e.getKey());
            if (inh != null && inh.state == State.HABITANT && e.getValue().isSpawned()) n++;
        }
        return n;
    }

    /** Fait apparaître l'habitant à l'endroit donné (PNJ créé au premier besoin, selon son état). */
    private NPC spawn(Inhabitant inh, Location at) {
        if (at == null) return null;
        NPC npc = npcs.get(inh.id);
        if (npc == null) {
            npc = registry.createNPC(EntityType.PLAYER, inh.name);
            npc.setProtected(false);                               // les monstres peuvent le blesser
            npc.data().set(NPC.Metadata.TARGETABLE, true);
            npc.data().set(NPC.Metadata.DROPS_ITEMS, false);
            npc.data().set(NPC.Metadata.PATHFINDER_OPEN_DOORS, inh.state != State.SOLDAT); // les gardes restent dehors
            npc.data().set(NPC_KEY, inh.id);
            NpcGestures.prepare(npc); // pas de balancement de bras à l'apparition (Epic Fight)
            boolean uniformed = inh.state == State.SOLDAT && inh.uniform >= 0 && !uniforms.isEmpty();
            if (uniformed) uniforms.apply(npc, Math.floorMod(inh.uniform, uniforms.size()));
            else skins.apply(npc, skins.size() == 0 || inh.skin < 0 ? -1 : Math.floorMod(inh.skin, skins.size()));
            if (inh.state == State.SOLDAT) equip(npc, guard.role(inh.role)); // un habitant n'a pas d'équipement
            if (inh.state == State.FORMATION) trainingGear(npc, guard.role(inh.role));
            if (inh.state == State.HABITANT && inh.employer != null && workPlace != null) { // outil du métier
                CityNPC boss = CityNPC.fromTag(inh.employer);
                if (boss != null) npc.getOrAddTrait(Equipment.class).set(Equipment.EquipmentSlot.HAND, new ItemStack(workPlace.tool(boss)));
            }
            npcs.put(inh.id, npc);
        }
        if (!npc.isSpawned()) {
            if (!sleeping.contains(inh.id)) clearSleep(npc); // disparu en dormant (chunk déchargé) : il réapparaît debout
            npc.spawn(at);
            NpcGestures.clearLeftovers(npc); // geste posé avant sa disparition : jamais rejoué sans cible
        }
        npc.getNavigator().getDefaultParameters().speedModifier(inh.state == State.SOLDAT ? guard.patrolSpeed() : walkSpeed);
        GuardConfig.Role role = inh.state == State.SOLDAT ? guard.role(inh.role) : null;
        if (role != null && npc.getEntity() instanceof LivingEntity body) {
            AttributeInstance max = body.getAttribute(Attribute.GENERIC_MAX_HEALTH);
            if (max != null && max.getBaseValue() != role.health()) {
                max.setBaseValue(role.health());
                body.setHealth(role.health());
            }
        }
        return npc;
    }

    /** Équipement du rôle d'un garde (arme, armure). */
    private static void equip(NPC npc, GuardConfig.Role role) {
        Equipment eq = npc.getOrAddTrait(Equipment.class);
        eq.set(Equipment.EquipmentSlot.HAND, item(role == null ? null : role.weapon()));
        eq.set(Equipment.EquipmentSlot.HELMET, item(role == null ? null : role.helmet()));
        eq.set(Equipment.EquipmentSlot.CHESTPLATE, item(role == null ? null : role.chest()));
        eq.set(Equipment.EquipmentSlot.LEGGINGS, item(role == null ? null : role.legs()));
        eq.set(Equipment.EquipmentSlot.BOOTS, item(role == null ? null : role.boots()));
    }

    private static ItemStack item(Material m) {
        return new ItemStack(m == null ? Material.AIR : m); // emplacement vide : air (jamais null)
    }

    /** Retire le PNJ du monde (destroy = il ne reviendra pas sous cette forme). */
    private void despawn(Inhabitant inh, boolean destroy) {
        sleeping.remove(inh.id);
        idleSince.remove(inh.id);
        awaySince.remove(inh.id);
        removeTimer(inh.id);
        NPC npc = destroy ? npcs.remove(inh.id) : npcs.get(inh.id);
        if (npc == null) return;
        clearSleep(npc); // sinon Citizens le recoucherait à sa prochaine apparition
        if (destroy) npc.destroy();
        else if (npc.isSpawned()) npc.despawn();
    }

    /* =========================
       VIE QUOTIDIENNE (deux fois par seconde)
       ========================= */

    private void tickBehaviour() {
        ticks++;
        long now = System.currentTimeMillis();
        assignPosts();
        refreshBattle();
        for (Map.Entry<Integer, NPC> e : new ArrayList<>(npcs.entrySet())) {
            Inhabitant inh = all.get(e.getKey());
            NPC npc = e.getValue();
            if (inh == null || !inh.alive() || !npc.isSpawned()) continue;
            NpcCombat.sanitize(npc.getEntity()); // vitesse invalide : remise à zéro avant l'envoi aux joueurs
            switch (inh.state) {
                case SOLDAT    -> guardBehaviour(inh, npc, now);
                case FORMATION -> traineeBehaviour(inh, npc, now);
                default        -> {
                    if (inh.employer != null && workPlace != null) workerBehaviour(inh, npc, now);
                    else civilianBehaviour(inh, npc, now);
                }
            }
        }
        // Minuteurs dont la recrue n'est plus en formation ou plus affichée
        for (Integer id : new ArrayList<>(timers.keySet())) {
            Inhabitant inh = all.get(id);
            NPC npc = npcs.get(id);
            boolean keeps = inh != null && (inh.state == State.FORMATION || (inh.state == State.HABITANT && inh.employer != null));
            if (!keeps || npc == null || !npc.isSpawned()) removeTimer(id);
        }
        // Toutes les 2 secondes, la nuit : les monstres proches s'en prennent aux habitants
        if (nightAttacks && ticks % 4 == 0) provokeMonsters();
    }

    /** Habitant : flâne le jour autour de sa maison, dort la nuit dans son lit. */
    private void civilianBehaviour(Inhabitant inh, NPC npc, long now) {
        Location bed = inh.bed();
        if (bed == null) return;
        Entity body = npc.getEntity();
        boolean walking = npc.getNavigator().isNavigating();

        // Sorti de la ville (poussé, en fuite) : il rentre chez lui
        if (!claims.isClaimed(body.getLocation())) {
            wake(inh, npc);
            if (!walking) SafeNavigation.walkTo(npc, standNear(inh), walkSpeed, true);
            return;
        }

        if (isNight(bed.getWorld())) {
            // La nuit : il rentre se coucher
            if (sleeping.contains(inh.id)) return;
            Location stand = standNear(inh);
            if (stand != null && body.getLocation().distanceSquared(stand) <= 2.5 * 2.5) {
                sleep(inh, npc, bed);
            } else if (!walking && stand != null) {
                SafeNavigation.walkTo(npc, stand, walkSpeed, true);
            }
            return;
        }

        // Le jour : il se lève et flâne autour de sa maison
        wake(inh, npc);
        if (walking) { idleSince.remove(inh.id); return; }
        long idle = idleSince.computeIfAbsent(inh.id, k -> now);
        long pause = strollPause / 2 + (strollPause == 0 ? 0 : Math.floorMod(inh.id * 7919L, strollPause));
        if (now - idle < pause) return;
        Location target = randomSpot(bed, strollRadius);
        if (target != null && SafeNavigation.walkTo(npc, target, walkSpeed, false) == SafeNavigation.Result.WALKING) {
            idleSince.remove(inh.id);
        }
    }

    /**
     * Recrue : rejoint sa place à côté de Roland et s'y entraîne le temps de sa formation, jour et
     * nuit (gestes d'arme face à Roland), un minuteur au-dessus de la tête.
     */
    private void traineeBehaviour(Inhabitant inh, NPC npc, long now) {
        wake(inh, npc);
        Location spot = trainingSpot(inh);
        if (spot == null) { // atelier de Roland introuvable ou non chargé : il attend chez lui
            removeTimer(inh.id);
            civilianBehaviour(inh, npc, now);
            return;
        }
        showTimer(inh, npc, now);
        Entity body = npc.getEntity();
        if (!body.getWorld().equals(spot.getWorld()) || body.getLocation().distanceSquared(spot) > 1.0) {
            long away = awaySince.computeIfAbsent(inh.id, k -> now);
            if (now - away > 20_000) {
                // Chemin introuvable (porte, escalier, comptoir) depuis 20 s : il rejoint sa place
                npc.getNavigator().cancelNavigation();
                npc.teleport(spot, PlayerTeleportEvent.TeleportCause.PLUGIN);
                awaySince.remove(inh.id);
            } else if (!npc.getNavigator().isNavigating()
                    && SafeNavigation.walkTo(npc, spot, walkSpeed + 0.2f, true) == SafeNavigation.Result.WALKING) {
                npc.getNavigator().getLocalParameters().distanceMargin(0.3); // jusqu'à sa place exacte
            }
            return;
        }
        awaySince.remove(inh.id);
        if (npc.getNavigator().isNavigating()) npc.getNavigator().cancelNavigation();
        drill(inh, npc);
    }

    /** Arme d'entraînement d'une recrue : épée de bois (au contact) ou arc (à distance). */
    private static void trainingGear(NPC npc, GuardConfig.Role role) {
        boolean ranged = role != null && role.ranged();
        npc.getOrAddTrait(Equipment.class).set(Equipment.EquipmentSlot.HAND,
                new ItemStack(ranged ? Material.BOW : Material.WOODEN_SWORD));
    }

    /**
     * Exercice : face à Roland, la recrue frappe dans le vide (coup balayé) ou décoche à vide
     * (étincelles), trois temps sur quatre, à un rythme propre à chacune.
     */
    private void drill(Inhabitant inh, NPC npc) {
        LivingEntity body = (LivingEntity) npc.getEntity();
        Location roland = rolandLocation();
        if (roland != null) npc.faceLocation(roland.clone().add(0, 1.5, 0));
        int beat = Math.floorMod(ticks + inh.id, 4);
        if (beat == 3) return; // une pause dans le rythme
        GuardConfig.Role role = guard.role(inh.role);
        boolean ranged = role != null && role.ranged();
        if (ranged) NpcGestures.shot(body); else NpcGestures.attack(body); // geste animé par Epic Fight si présent
        World w = body.getWorld();
        Location front = body.getEyeLocation().add(body.getEyeLocation().getDirection().multiply(1.2)).subtract(0, 0.4, 0);
        if (ranged) {
            w.spawnParticle(Particle.CRIT, front, 5, 0.15, 0.15, 0.15, 0.1);
            if (beat == 0) w.playSound(body.getLocation(), Sound.ENTITY_ARROW_SHOOT, 0.35f, 1.3f);
        } else {
            w.spawnParticle(Particle.SWEEP_ATTACK, front, 1, 0, 0, 0, 0);
            if (beat == 0) w.playSound(body.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.35f, 1.2f);
        }
    }

    /* =========================
       MINUTEUR DE FORMATION (texte flottant au-dessus de la recrue)
       ========================= */

    /** Affiche ou déplace le minuteur de la recrue (au-dessus de son nom) et met à jour son texte. */
    private void showTimer(Inhabitant inh, NPC npc, long now) {
        Location above = npc.getEntity().getLocation().add(0, 2.45, 0);
        TextDisplay display = timers.get(inh.id);
        if (display == null || !display.isValid() || !display.getWorld().equals(above.getWorld())) {
            if (display != null) display.remove();
            display = above.getWorld().spawn(above, TextDisplay.class, d -> {
                d.setPersistent(false);                       // jamais enregistré avec le monde
                d.setBillboard(Display.Billboard.CENTER);     // toujours face au joueur
                d.setShadowed(true);
                d.setDefaultBackground(false);
                d.setBackgroundColor(Color.fromARGB(110, 0, 0, 0));
                d.setTeleportDuration(10);                    // suit la recrue en douceur
            });
            timers.put(inh.id, display);
        } else if (display.getLocation().distanceSquared(above) > 0.0025) {
            display.teleport(above);
        }
        display.setText(timerText(inh, now));
    }

    /** « ⚔ Soldat en formation » puis barre de progression et temps restant (ou « en pause »). */
    private String timerText(Inhabitant inh, long now) {
        GuardConfig.Role role = guard.role(inh.role);
        boolean counting = !Bukkit.getOnlinePlayers().isEmpty() && guardRank() > 0;
        // Le temps est décompté toutes les 5 s : on retire ce qui s'est écoulé depuis, pour un compte à rebours fluide
        long left = Math.max(0, inh.trainingLeft - (counting ? now - lastMaintenance : 0));
        long total = role != null ? Math.max(1, role.trainingMs()) : Math.max(1, left);
        int filled = (int) Math.round(10.0 * Math.min(1, Math.max(0, 1 - (double) left / total)));
        long sec = (left + 999) / 1000;
        String bar = "§a" + "▮".repeat(filled) + "§8" + "▮".repeat(10 - filled);
        return "§6⚔ " + (role != null ? role.name() : "Garde") + " §7en formation\n" + bar + " "
                + (counting ? "§f" : "§c⏸ ") + String.format("%d:%02d", sec / 60, sec % 60);
    }

    private void removeTimer(int id) {
        TextDisplay d = timers.remove(id);
        if (d != null) d.remove();
    }

    /**
     * Garde : attaque un ennemi proche dans la ville (monstre ou soldat d'une armée ennemie ; au
     * contact, ou à l'arc), sans s'entasser à plus de soldats.max-par-cible sur le même. Pendant
     * un siège, tient son poste sur le front (dans le secteur de son groupe), face à l'ennemi ;
     * pendant une vague, son groupe va à la rencontre d'une escouade. Après un siège, rentre vers
     * son quartier ; sinon fait sa ronde sur le terrain de la ville, hors des bâtiments. Jamais de
     * sommeil.
     */
    private void guardBehaviour(Inhabitant inh, NPC npc, long now) {
        GuardConfig.Role role = guard.role(inh.role);
        LivingEntity body = (LivingEntity) npc.getEntity();
        boolean walking = npc.getNavigator().isNavigating();

        boolean archer = role != null && role.ranged();
        Location post = posts.get(inh.id);
        // Assaut (vague en cours) : la garde sort de la ville, groupe par groupe, à la rencontre des
        // escouades ; hors vague, elle tient le front et ne combat que ce qui entre en ville (ou,
        // pour un archer à son poste, ce qui approche à portée de tir)
        boolean assault = post != null && assaulting;
        SiegeFront.Band band = assault ? bandOf(inh) : null;
        LivingEntity enemy = assault ? assaultTarget(inh, body, archer, band, now) : null;
        if (enemy == null) enemy = nearestEnemy(inh, body, archer, archer && post != null);
        setFight(inh, enemy, archer);
        if (enemy != null) {
            idleSince.remove(inh.id);
            supportGoal.remove(inh.id);
            if (assault) awaySince.remove(inh.id); // à l'assaut : pas de compte à rebours avant d'être ramené à son poste
            double dist = body.getLocation().distance(enemy.getLocation());
            if (archer) {
                if (dist <= guard.bowRange() && body.hasLineOfSight(enemy)) {
                    if (walking) npc.getNavigator().cancelNavigation();
                    goals.remove(inh.id);
                    npc.faceLocation(enemy.getEyeLocation());
                    shoot(inh, body, enemy, now);
                } else if (assault || claims.isClaimed(enemy.getLocation())) {
                    goals.remove(inh.id);
                    if (!walking) advance(npc, body, enemy.getLocation(), 1.1f); // jusqu'à portée de tir
                } else if (post != null && post.getWorld().equals(body.getWorld())) {
                    holdPost(inh, npc, body, post, now); // hors de portée et hors de la ville : il tient son poste
                }
            } else if (dist > 40) {
                // Ennemi lointain (assaut) : il y court par étapes, puis l'engage au contact
                goals.remove(inh.id);
                if (!walking || npc.getNavigator().getEntityTarget() != null) {
                    npc.getNavigator().cancelNavigation();
                    advance(npc, body, enemy.getLocation(), 1.2f);
                }
            } else {
                goals.remove(inh.id);
                boolean soldier = hostileNpc != null && hostileNpc.test(enemy);
                EntityTarget target = npc.getNavigator().getEntityTarget();
                if (target == null || target.getTarget() != enemy || target.isAggressive() == soldier) {
                    // Monstre : Citizens frappe à portée. Soldat ennemi (un « joueur » pour Minecraft) :
                    // il le suit et les coups sont portés ici (sinon le contrôle PvP les annule)
                    npc.getNavigator().setTarget(enemy, !soldier);
                    npc.getNavigator().getLocalParameters().speedModifier(1.2f).range(48); // assez loin pour sortir de la ville
                }
                if (soldier && body.getLocation().distanceSquared(enemy.getLocation()) <= NpcCombat.REACH * NpcCombat.REACH
                        && now - lastSwing.getOrDefault(inh.id, 0L) >= SWING_COOLDOWN_MS) {
                    lastSwing.put(inh.id, now);
                    npc.faceLocation(enemy.getEyeLocation());
                    NpcGestures.attack(body);
                    NpcCombat.strike(body, enemy, NpcCombat.meleeDamage(body));
                }
            }
            return;
        }

        // Plus d'ennemi en ville (mort, ou sorti de la ville) : il arrête la poursuite
        if (npc.getNavigator().getEntityTarget() != null) {
            npc.getNavigator().cancelNavigation();
            walking = false;
        }

        // Assaut : tous les ennemis de son escouade sont déjà assez entourés, il se tient prêt tout près
        if (band != null) {
            support(inh, npc, body, band, archer);
            return;
        }
        supportGoal.remove(inh.id);

        // Siège : en position sur le front dès l'alerte et pendant les répits, face à l'ennemi
        if (post != null && post.getWorld().equals(body.getWorld())) {
            holdPost(inh, npc, body, post, now);
            return;
        }

        // Sorti de la ville : retour sur le terrain de la ville
        if (!claims.isClaimed(body.getLocation())) {
            if (!walking) {
                Location back = homePoint(inh);
                if (back != null) SafeNavigation.walkTo(npc, back, guard.patrolSpeed() + 0.2f, true);
            }
            return;
        }

        // Après un siège : il rentre vers son quartier avant de reprendre sa ronde
        Location home = returning.get(inh.id);
        if (home != null) {
            if (now > returnUntil.getOrDefault(inh.id, 0L) || !home.getWorld().equals(body.getWorld())
                    || body.getLocation().distanceSquared(home) < 5 * 5) {
                returning.remove(inh.id);
                returnUntil.remove(inh.id);
            } else {
                if (!walking) moveTowards(npc, body, home, guard.patrolSpeed() + 0.2f);
                idleSince.remove(inh.id);
                return;
            }
        }

        // Au repos : se soigne doucement (1 PV toutes les 5 s)
        heal(body);

        // Ronde : un point du terrain de la ville, hors des bâtiments, puis une courte pause
        if (walking) { idleSince.remove(inh.id); return; }
        long idle = idleSince.computeIfAbsent(inh.id, k -> now);
        if (now - idle < 2000 + Math.floorMod(inh.id * 7919L, 3000)) return;
        Location target = patrolPoint(body.getLocation(), 24);
        if (target != null && SafeNavigation.walkTo(npc, target, guard.patrolSpeed(), false) == SafeNavigation.Result.WALKING) {
            idleSince.remove(inh.id);
        }
    }

    /**
     * Poste sur le front : il y court (sans finir sa ronde), par étapes ; s'il n'y est pas au
     * bout de 30 s (mur, rivière), il y est téléporté. Sur place : immobile, face à l'ennemi.
     */
    private void holdPost(Inhabitant inh, NPC npc, LivingEntity body, Location post, long now) {
        if (body.getLocation().distanceSquared(post) > 1.2 * 1.2) {
            long away = awaySince.computeIfAbsent(inh.id, k -> now);
            if (now - away > 30_000) {
                npc.getNavigator().cancelNavigation();
                npc.teleport(post, PlayerTeleportEvent.TeleportCause.PLUGIN);
                awaySince.remove(inh.id);
                return;
            }
            Location goal = goals.get(inh.id);
            boolean samePost = goal != null && goal.getWorld().equals(post.getWorld()) && goal.distanceSquared(post) < 1;
            if (!samePost) {
                npc.getNavigator().cancelNavigation(); // ronde interrompue : il part tout de suite
                goals.put(inh.id, post.clone());
            } else if (npc.getNavigator().isNavigating()) {
                return;
            }
            moveTowards(npc, body, post, 1.0f);
            if (npc.getNavigator().isNavigating() && body.getLocation().distanceSquared(post) <= 40 * 40) {
                npc.getNavigator().getLocalParameters().distanceMargin(0.5); // jusqu'à son poste exact
            }
            return;
        }
        awaySince.remove(inh.id);
        if (npc.getNavigator().isNavigating()) npc.getNavigator().cancelNavigation();
        if (front != null && ticks % 4 == 0) npc.faceLocation(front.enemy().clone().add(0, 1.5, 0));
        heal(body);
    }

    /**
     * Adversaire d'un garde pendant l'assaut : d'abord un assaillant qui le serre de près (4
     * blocs), puis un membre de l'escouade qu'affronte son groupe (le plus proche de lui), enfin
     * n'importe quel assaillant proche (16 blocs, portée de tir pour un archer) — toujours sans
     * dépasser soldats.max-par-cible gardes au contact du même ennemi (pas de limite pour un
     * archer). Le choix est revu toutes les ~1,5 s ; entre-temps il garde sa cible. null : aucun
     * adversaire libre.
     */
    private LivingEntity assaultTarget(Inhabitant inh, LivingEntity body, boolean archer, SiegeFront.Band band, long now) {
        LivingEntity current = fightTarget.get(inh.id);
        int cap = archer ? 0 : guard.maxPerTarget();
        if (current != null && current.isValid() && !current.isDead() && current.getWorld().equals(body.getWorld())
                && now < nextPick.getOrDefault(inh.id, 0L) && (archer || meleeTally.acceptable(current, cap))
                && current.getLocation().distanceSquared(body.getLocation()) < 48 * 48) {
            return current;
        }
        nextPick.put(inh.id, now + 1500 + Math.floorMod(inh.id * 37L, 500));
        Location at = body.getLocation();
        LivingEntity t = archer ? null : meleeTally.pick(at, attackersNow, 4, cap, current, null, 0);
        if (t == null && band != null) t = meleeTally.pick(at, band.members(), 96, cap, current, band.center(), 10);
        if (t == null) t = meleeTally.pick(at, attackersNow, archer ? guard.bowRange() : 16, cap, current, null, 0);
        return t;
    }

    /** Adversaire en cours d'un garde (null : aucun) ; les gardes au contact sont comptés par ennemi. */
    private void setFight(Inhabitant inh, LivingEntity enemy, boolean archer) {
        LivingEntity before = enemy == null ? fightTarget.remove(inh.id) : fightTarget.put(inh.id, enemy);
        if (!archer && before != enemy) meleeTally.swap(before, enemy);
    }

    /** Escouade qu'affronte le groupe de ce garde (null : aucune pour l'instant). */
    private SiegeFront.Band bandOf(Inhabitant inh) {
        Integer k = guardGroup.get(inh.id);
        Integer id = k == null ? null : groupBand.get(k);
        if (id == null) return null;
        for (SiegeFront.Band b : bands) if (b.id() == id) return b;
        return null;
    }

    /**
     * Assaut, sans adversaire libre (ceux de son escouade sont déjà assez entourés) : le garde se
     * tient prêt à quelques blocs de l'escouade qu'affronte son groupe, de son côté, un peu décalé
     * de ses voisins (pas d'entassement) ; un archer reste à portée de tir. Il ne relance son
     * chemin que si sa place s'est déplacée de quelques blocs.
     */
    private void support(Inhabitant inh, NPC npc, LivingEntity body, SiegeFront.Band band, boolean archer) {
        goals.remove(inh.id);
        idleSince.remove(inh.id);
        awaySince.remove(inh.id);
        Location c = band.center();
        Location at = body.getLocation();
        if (!c.getWorld().equals(at.getWorld())) return;
        Vector away = at.toVector().subtract(c.toVector()).setY(0);
        if (away.lengthSquared() < 1e-4) away = new Vector(1, 0, 0);
        away.normalize();
        double keep = archer ? Math.max(6, guard.bowRange() - 4) : 5;
        Vector aside = new Vector(-away.getZ(), 0, away.getX()).multiply((Math.floorMod(inh.id, 5) - 2) * 1.5);
        Location spot = c.clone().add(away.multiply(keep)).add(aside);
        double far = at.distance(spot);
        boolean walking = npc.getNavigator().isNavigating() && npc.getNavigator().getEntityTarget() == null;
        if (far <= 2) { // à sa place : face à la mêlée
            supportGoal.remove(inh.id);
            if (ticks % 4 == 0) npc.faceLocation(c.clone().add(0, 1.5, 0));
            return;
        }
        if (far > 40) { // mêlée lointaine : il y court par étapes
            supportGoal.remove(inh.id);
            if (!walking) {
                npc.getNavigator().cancelNavigation();
                advance(npc, body, spot, 1.1f);
            }
            return;
        }
        Location prev = supportGoal.get(inh.id);
        if (walking && prev != null && prev.getWorld().equals(spot.getWorld()) && prev.distanceSquared(spot) < 3 * 3) return;
        World w = spot.getWorld();
        if (!w.isChunkLoaded(spot.getBlockX() >> 4, spot.getBlockZ() >> 4)) return;
        Location stand = GroundFinder.standAt(w, spot.getBlockX(), c.getBlockY(), spot.getBlockZ());
        if (stand == null) return;
        if (SafeNavigation.walkTo(npc, stand, 1.1f, false) == SafeNavigation.Result.WALKING) supportGoal.put(inh.id, stand);
    }

    /**
     * Avance vers une cible hors de la ville (assaut) : directement à moins de 40 blocs, sinon par
     * étapes de 36 blocs au sol ; jamais téléporté (il marche au combat).
     */
    private void advance(NPC npc, LivingEntity body, Location target, float speed) {
        double d = body.getLocation().distance(target);
        Location step = target;
        if (d > 40) {
            Location mid = body.getLocation().add(target.toVector().subtract(body.getLocation().toVector()).multiply(36 / d));
            World w = mid.getWorld();
            step = null;
            if (w.isChunkLoaded(mid.getBlockX() >> 4, mid.getBlockZ() >> 4)) {
                Block feet = w.getBlockAt(mid.getBlockX(), w.getHighestBlockYAt(mid.getBlockX(), mid.getBlockZ(),
                        HeightMap.MOTION_BLOCKING_NO_LEAVES) + 1, mid.getBlockZ());
                if (standable(feet)) step = feet.getLocation().add(0.5, 0, 0.5);
            }
        }
        if (step != null) SafeNavigation.walkTo(npc, step, speed, false);
    }

    /** Vers une destination lointaine : par étapes de 36 blocs (portée d'un chemin), sinon téléporté. */
    private void moveTowards(NPC npc, LivingEntity body, Location target, float speed) {
        double d = body.getLocation().distance(target);
        Location step = target;
        if (d > 40) {
            Location mid = body.getLocation().add(target.toVector().subtract(body.getLocation().toVector()).multiply(36 / d));
            step = patrolPoint(mid, 4);
        }
        if (step == null || SafeNavigation.walkTo(npc, step, speed, false) != SafeNavigation.Result.WALKING) {
            SafeNavigation.walkTo(npc, target, speed, true); // aucun chemin court : il rejoint sa destination
        }
    }

    /** 1 PV toutes les 5 s au repos. */
    private void heal(LivingEntity body) {
        if (ticks % 10 != 0) return;
        AttributeInstance max = body.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (max != null && body.getHealth() < max.getValue()) body.setHealth(Math.min(max.getValue(), body.getHealth() + 1));
    }

    /** Point de ronde près de chez lui (hors des bâtiments), de plus en plus loin s'il le faut ; null sinon. */
    private Location homePoint(Inhabitant inh) {
        Location bed = inh.bed();
        if (bed == null) return null;
        for (int radius : new int[]{12, 24, 40}) {
            Location p = patrolPoint(bed, radius);
            if (p != null) return p;
        }
        return null;
    }

    /** Groupes de la garde au plus pendant un siège (secteurs du front). */
    private static final int MAX_GROUPS = 8;

    /**
     * Postes du front et groupes de la garde, recalculés quand le front change ou qu'un garde
     * arrive ou tombe (sinon rien à refaire). La garde est répartie en groupes
     * (soldats.groupe-taille gardes chacun, soldats et archers mêlés), chacun chargé d'un secteur
     * sur la largeur du front ; dans son secteur, les gardes au contact en première ligne, les
     * archers en retrait, un poste chacun, au plus près du centre du secteur. Sur un même front,
     * un garde reste dans son groupe ; une nouvelle recrue rejoint le plus petit.
     */
    private void assignPosts() {
        SiegeFront f = siegeFront == null ? null : siegeFront.get();
        if (f == null) {
            if (front != null) clearBattle();
            return;
        }
        List<Inhabitant> roster = guards();
        Set<Integer> ids = new HashSet<>();
        for (Inhabitant g : roster) ids.add(g.id);
        if (f == front && ids.equals(postedRoster)) return;
        boolean newFront = f != front || groupLane.length == 0;
        front = f;
        postedRoster = ids;
        posts.clear();
        List<Inhabitant> melee = new ArrayList<>(), ranged = new ArrayList<>();
        for (Inhabitant g : roster) {
            GuardConfig.Role r = guard.role(g.role);
            (r != null && r.ranged() ? ranged : melee).add(g);
        }
        if (newFront) {
            // Secteurs répartis sur la largeur couverte par les postes ; un seul : face au ralliement
            int count = Math.max(1, Math.min(MAX_GROUPS, (roster.size() + guard.groupSize() - 1) / guard.groupSize()));
            double lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
            for (List<Location> list : List.of(f.melee(), f.ranged())) {
                for (Location l : list) { double t = f.lateral(l); lo = Math.min(lo, t); hi = Math.max(hi, t); }
            }
            if (lo > hi) lo = hi = 0;
            groupLane = new double[count];
            groupAnchor = new Location[count];
            for (int k = 0; k < count; k++) {
                groupLane[k] = count == 1 ? Math.max(lo, Math.min(hi, 0)) : lo + (hi - lo) * (k + 0.5) / count;
                groupAnchor[k] = nearestOnFront(f, groupLane[k]);
            }
            guardGroup.clear();
            groupBand.clear();
            int i = 0;
            for (Inhabitant g : melee) guardGroup.put(g.id, i++ % count);
            for (Inhabitant g : ranged) guardGroup.put(g.id, i++ % count);
        } else {
            guardGroup.keySet().retainAll(ids);
            int[] sizes = new int[groupLane.length];
            for (int k : guardGroup.values()) sizes[k]++;
            for (Inhabitant g : roster) {
                if (guardGroup.containsKey(g.id)) continue;
                int smallest = 0;
                for (int k = 1; k < sizes.length; k++) if (sizes[k] < sizes[smallest]) smallest = k;
                guardGroup.put(g.id, smallest);
                sizes[smallest]++;
            }
        }
        placeGroups(melee, f.melee(), f.ranged());
        placeGroups(ranged, f.ranged(), f.melee());
    }

    /** Un poste par garde, dans le secteur de son groupe : le plus proche de son centre encore libre. */
    private void placeGroups(List<Inhabitant> who, List<Location> spots, List<Location> fallback) {
        List<Location> list = !spots.isEmpty() ? spots : !fallback.isEmpty() ? fallback
                : front.rally() != null ? List.of(front.rally()) : List.of(); // aucun poste praticable : au ralliement
        if (list.isEmpty() || who.isEmpty()) return;
        boolean[] taken = new boolean[list.size()];
        for (int k = 0; k < groupLane.length; k++) {
            double lane = groupLane[k];
            List<Integer> order = new ArrayList<>(list.size());
            for (int i = 0; i < list.size(); i++) order.add(i);
            order.sort(Comparator.comparingDouble(i -> Math.abs(front.lateral(list.get(i)) - lane))); // tri stable : 1re rangée d'abord
            int next = 0;
            for (Inhabitant g : who) {
                if (guardGroup.getOrDefault(g.id, 0) != k) continue;
                while (next < order.size() && taken[order.get(next)]) next++;
                int index = next < order.size() ? order.get(next) : order.get(Math.floorMod(g.id, order.size())); // plus de place : on double
                taken[index] = true;
                posts.put(g.id, list.get(index));
            }
        }
    }

    /** Poste du front le plus proche (sur la largeur) de la position lane ; le ralliement s'il n'y en a pas. */
    private static Location nearestOnFront(SiegeFront f, double lane) {
        Location best = f.rally();
        double bestGap = Double.MAX_VALUE;
        for (Location l : !f.melee().isEmpty() ? f.melee() : f.ranged()) {
            double gap = Math.abs(f.lateral(l) - lane);
            if (gap < bestGap) { bestGap = gap; best = l; }
        }
        return best;
    }

    /**
     * Relevé du champ de bataille, à chaque passage : vague en cours, escouades présentes (une
     * seule demande au siège), gardes au contact de chaque ennemi (recomptés à partir des
     * adversaires en cours), et escouade affrontée par chaque groupe.
     */
    private void refreshBattle() {
        assaulting = front != null && siegeAssault != null && siegeAssault.getAsBoolean();
        bands = assaulting && siegeBands != null ? siegeBands.get() : List.of();
        if (bands.isEmpty()) {
            attackersNow = List.of();
        } else {
            List<LivingEntity> everyone = new ArrayList<>();
            for (SiegeFront.Band b : bands) everyone.addAll(b.members());
            attackersNow = everyone;
        }
        meleeTally.clear();
        fightTarget.entrySet().removeIf(e -> {
            Inhabitant g = all.get(e.getKey());
            LivingEntity t = e.getValue();
            return g == null || g.state != State.SOLDAT || t == null || t.isDead() || !t.isValid();
        });
        for (Map.Entry<Integer, LivingEntity> e : fightTarget.entrySet()) {
            GuardConfig.Role r = guard.role(all.get(e.getKey()).role);
            if (r == null || !r.ranged()) meleeTally.add(e.getValue()); // seuls ceux au contact comptent
        }
        if (assaulting) matchBands(); else groupBand.clear();
    }

    /**
     * Chaque groupe de la garde va à la rencontre d'une escouade : la plus proche du centre de son
     * secteur, une escouade déjà prise par un autre groupe comptant comme plus lointaine (les
     * groupes se répartissent). Un groupe garde son escouade tant qu'elle existe, sauf si une
     * autre est nettement plus proche (revu toutes les 2 s).
     */
    private void matchBands() {
        if (bands.isEmpty() || groupLane.length == 0) { groupBand.clear(); return; }
        Set<Integer> present = new HashSet<>();
        for (SiegeFront.Band b : bands) present.add(b.id());
        groupBand.values().removeIf(id -> !present.contains(id));
        boolean due = ticks % 4 == 0;
        Map<Integer, Integer> load = new HashMap<>();
        for (int id : groupBand.values()) load.merge(id, 1, Integer::sum);
        for (int k = 0; k < groupLane.length; k++) {
            Integer current = groupBand.get(k);
            if (current != null && !due) continue;
            Location anchor = groupAnchor[k] != null ? groupAnchor[k] : front.rally();
            if (current != null) load.merge(current, -1, Integer::sum);
            int bestId = Integer.MIN_VALUE;
            double bestScore = Double.MAX_VALUE, currentScore = Double.MAX_VALUE;
            for (SiegeFront.Band b : bands) {
                if (!b.center().getWorld().equals(anchor.getWorld())) continue;
                double score = b.center().distance(anchor) + 24 * load.getOrDefault(b.id(), 0);
                if (current != null && b.id() == current) currentScore = score - 12; // il ne change pas pour si peu
                if (score < bestScore) { bestScore = score; bestId = b.id(); }
            }
            int chosen = current != null && currentScore <= bestScore ? current : bestId;
            if (chosen == Integer.MIN_VALUE) { groupBand.remove(k); continue; }
            groupBand.put(k, chosen);
            load.merge(chosen, 1, Integer::sum);
        }
    }

    /** Fin de siège (ou plus de front) : postes, groupes, escouades et mêlées oubliés. */
    private void clearBattle() {
        posts.clear();
        goals.clear();
        front = null;
        postedRoster = Set.of();
        guardGroup.clear();
        groupBand.clear();
        groupLane = new double[0];
        groupAnchor = new Location[0];
        assaulting = false;
        bands = List.of();
        attackersNow = List.of();
        nextPick.clear();
        supportGoal.clear();
    }

    /**
     * Ennemi vivant le plus proche, à portée de vue et dans la ville (ou n'importe où si
     * outside) : monstre hostile (zombie, squelette, pillard…) ou soldat d'une armée ennemie.
     * Un garde au contact laisse de côté un ennemi déjà serré par soldats.max-par-cible gardes.
     */
    private LivingEntity nearestEnemy(Inhabitant inh, LivingEntity from, boolean archer, boolean outside) {
        double r = guard.sight();
        int cap = archer ? 0 : guard.maxPerTarget();
        LivingEntity current = fightTarget.get(inh.id);
        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (Entity e : from.getNearbyEntities(r, 8, r)) {
            boolean soldier = hostileNpc != null && hostileNpc.test(e);
            boolean monster = e instanceof Enemy && e instanceof Mob && !e.hasMetadata("NPC");
            if ((!soldier && !monster) || !(e instanceof LivingEntity le) || le.isDead()) continue;
            if (!outside && !claims.isClaimed(le.getLocation())) continue;
            if (cap > 0 && meleeTally.count(le) - (le == current ? 1 : 0) >= cap) continue; // assez entouré
            double d = le.getLocation().distanceSquared(from.getLocation());
            if (d < bestDist) { bestDist = d; best = le; }
        }
        return best;
    }

    /** Une flèche vers la cible (légère courbe selon la distance), non ramassable, retirée ensuite. */
    private void shoot(Inhabitant inh, LivingEntity body, LivingEntity target, long now) {
        if (now - lastShot.getOrDefault(inh.id, 0L) < BOW_COOLDOWN_MS) return;
        lastShot.put(inh.id, now);
        Vector dir = target.getEyeLocation().toVector().subtract(body.getEyeLocation().toVector());
        double dist = dir.length();
        dir.normalize().multiply(1.9).add(new Vector(0, dist * 0.012, 0));
        Arrow arrow = body.launchProjectile(Arrow.class, dir);
        arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
        NpcGestures.shot(body);
        body.getWorld().playSound(body.getLocation(), Sound.ENTITY_ARROW_SHOOT, 0.8f, 1.0f);
        Bukkit.getScheduler().runTaskLater(plugin, () -> { if (arrow.isValid()) arrow.remove(); }, 60L);
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

    /**
     * Réveil : le trait « sommeil » est retiré (sinon Citizens recouche le PNJ à sa prochaine
     * apparition), il sort du lit, et s'il est encore couché un instant plus tard (réveil manqué
     * sur ce serveur), il réapparaît debout au même endroit.
     */
    private void wake(Inhabitant inh, NPC npc) {
        sleeping.remove(inh.id);
        if (!clearSleep(npc) || !npc.isSpawned()) return; // pas couché : rien à faire
        if (npc.getEntity() instanceof HumanEntity human && human.isSleeping()) {
            try { human.wakeup(false); } catch (Throwable ignored) { }
        }
        try { npc.getEntity().setPose(Pose.STANDING, false); } catch (Throwable ignored) { } // posture allongée levée
        Location stand = standNear(inh);
        if (stand != null) npc.teleport(stand, PlayerTeleportEvent.TeleportCause.PLUGIN); // il se lève à côté du lit
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!npc.isSpawned() || npcs.get(inh.id) != npc || sleeping.contains(inh.id)) return;
            Entity body = npc.getEntity();
            boolean lying = body.getPose() == Pose.SLEEPING || (body instanceof HumanEntity h && h.isSleeping());
            if (!lying) return;
            Location at = body.getLocation();
            npc.despawn(DespawnReason.PENDING_RESPAWN);
            npc.spawn(at);
        }, 5L);
    }

    /** Retire le trait « sommeil » du PNJ (réveil) ; false s'il ne l'avait pas. */
    private static boolean clearSleep(NPC npc) {
        SleepTrait trait = npc.getTraitNullable(SleepTrait.class);
        if (trait == null) return false;
        try { trait.setSleeping(null); } catch (Throwable ignored) { }
        npc.removeTrait(SleepTrait.class);
        // Le sommeil fixe aussi une posture « couché » (trait EntityPoseTrait de Citizens) : retirée aussi
        if (npc.hasTrait(EntityPoseTrait.class)) npc.removeTrait(EntityPoseTrait.class);
        return true;
    }

    /** La nuit, un monstre sans cible près d'un habitant (ou d'un garde) le prend pour cible. */
    private void provokeMonsters() {
        for (Map.Entry<Integer, NPC> e : npcs.entrySet()) {
            NPC npc = e.getValue();
            if (!npc.isSpawned() || !isNight(npc.getEntity().getWorld())) continue;
            for (Entity near : npc.getEntity().getNearbyEntities(16, 6, 16)) {
                if (near instanceof Monster monster && monster.getTarget() == null && !registry.isNPC(near)) {
                    monster.setTarget((LivingEntity) npc.getEntity());
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

    /**
     * Où se tient Roland : là où il est s'il se trouve dans son atelier, sinon son point de
     * travail ; null s'il n'a pas d'atelier ou si la zone n'est pas chargée.
     */
    private Location rolandLocation() {
        Building b = rolandBuilding;
        if (b == null) return null;
        World w = Bukkit.getWorld(b.world());
        if (w == null) return null;
        NPC roland = npcManager == null ? null : npcManager.getNPC(CityNPC.GUARD);
        if (roland != null && roland.isSpawned()) {
            Location at = roland.getEntity().getLocation();
            if (at.getWorld().equals(w) && at.getBlockX() >= b.x1() && at.getBlockX() <= b.x2()
                    && at.getBlockZ() >= b.z1() && at.getBlockZ() <= b.z2()) return at;
        }
        double x = b.hasNpcPoint() ? b.npcX() : (b.x1() + b.x2()) / 2.0 + 0.5;
        double z = b.hasNpcPoint() ? b.npcZ() : (b.z1() + b.z2()) / 2.0 + 0.5;
        if (!w.isChunkLoaded((int) Math.floor(x) >> 4, (int) Math.floor(z) >> 4)) return null;
        double y = b.hasNpcPoint() ? b.npcY()
                : b.refY() != null ? b.refY() + 1 : w.getHighestBlockYAt((int) Math.floor(x), (int) Math.floor(z)) + 1;
        return new Location(w, x, y, z);
    }

    /**
     * Place d'une recrue : à côté de Roland (à 2 blocs environ, dans son atelier), une place
     * différente pour chaque recrue ; null si Roland n'a pas d'atelier ou si sa zone n'est pas
     * chargée.
     */
    private Location trainingSpot(Inhabitant inh) {
        Location roland = rolandLocation();
        if (roland == null) return null;
        Building b = rolandBuilding;
        World w = roland.getWorld();
        int cx = roland.getBlockX(), cy = roland.getBlockY(), cz = roland.getBlockZ();
        List<Location> spots = new ArrayList<>();
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                int d2 = dx * dx + dz * dz;
                if (d2 < 2 || d2 > 9) continue; // ni collée à Roland, ni trop loin
                int x = cx + dx, z = cz + dz;
                if (x < b.x1() || x > b.x2() || z < b.z1() || z > b.z2()) continue;
                if (!w.isChunkLoaded(x >> 4, z >> 4)) continue;
                for (int dy = 1; dy >= -1; dy--) {
                    Block feet = w.getBlockAt(x, cy + dy, z);
                    if (standable(feet)) { spots.add(feet.getLocation().add(0.5, 0, 0.5)); break; }
                }
            }
        }
        if (spots.isEmpty()) return roland.getBlock().getLocation().add(0.5, 0, 0.5);
        // Les places à 2 blocs d'abord (face à Roland, à portée de voix), dans un ordre fixe
        spots.sort(Comparator.<Location>comparingDouble(l -> {
                    double dx = l.getX() - (cx + 0.5), dz = l.getZ() - (cz + 0.5);
                    return Math.abs(dx * dx + dz * dz - 4);
                })
                .thenComparingDouble(Location::getX)
                .thenComparingDouble(Location::getZ));
        int index = Math.max(0, trainees().indexOf(inh)); // une place par recrue, dans l'ordre d'arrivée
        return spots.get(index % spots.size());
    }

    /**
     * Point de ronde : au sol, à moins de radius blocs de center, dans un chunk revendiqué et
     * chargé, hors de la zone de tout bâtiment ; null si rien trouvé en quelques essais.
     */
    private Location patrolPoint(Location center, int radius) {
        if (center == null) return null;
        World world = center.getWorld();
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (int attempt = 0; attempt < 12; attempt++) {
            int x = center.getBlockX() + rnd.nextInt(-radius, radius + 1);
            int z = center.getBlockZ() + rnd.nextInt(-radius, radius + 1);
            if (!world.isChunkLoaded(x >> 4, z >> 4) || !claims.isClaimed(world, x >> 4, z >> 4)) continue;
            if (insideBuilding(world.getName(), x, z)) continue;
            Block feet = world.getBlockAt(x, world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES) + 1, z);
            if (standable(feet)) return feet.getLocation().add(0.5, 0, 0.5);
        }
        return null;
    }

    private boolean insideBuilding(String world, int x, int z) {
        for (Building b : homes.values()) {
            if (b.world().equals(world) && x >= b.x1() && x <= b.x2() && z >= b.z1() && z <= b.z2()) return true;
        }
        return false;
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

    /**
     * Seuls les monstres blessent habitants et gardes : coups et flèches des joueurs annulés. Les
     * gardes ne blessent que les monstres hostiles : ni joueur, ni habitant, ni animal (flèche perdue).
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (registry == null) return;
        Entity damager = event.getDamager();
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) damager = shooter;
        boolean enemyVictim  = hostileNpc != null && hostileNpc.test(event.getEntity());
        boolean enemyDamager = hostileNpc != null && hostileNpc.test(damager);
        if (ours(damager) && !(event.getEntity() instanceof Enemy) && !enemyVictim) {
            event.setCancelled(true); // coup ou flèche d'un garde sur autre chose qu'un ennemi
        } else if (ours(event.getEntity()) && damager instanceof Player && !enemyDamager) {
            event.setCancelled(true); // un joueur ne blesse pas les habitants (un soldat ennemi, si)
        }
    }

    /**
     * Flèche d'un garde sur un soldat ennemi : pour Minecraft, deux « joueurs » (contrôle PvP) ;
     * la blessure est donc appliquée ici et la flèche retirée.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onArrowHit(ProjectileHitEvent event) {
        if (!(event.getEntity().getShooter() instanceof LivingEntity shooter) || !ours(shooter)) return;
        if (!(event.getHitEntity() instanceof LivingEntity victim) || hostileNpc == null || !hostileNpc.test(victim)) return;
        event.setCancelled(true);
        event.getEntity().remove();
        NpcCombat.strike(shooter, victim, NpcCombat.arrowDamage(shooter));
    }

    /**
     * PNJ de ce gestionnaire (habitant, recrue, garde). Le isNPC de Citizens répond oui pour
     * n'importe quel PNJ (artisan, soldat ennemi…), quel que soit son registre.
     */
    private boolean ours(Entity e) {
        if (registry == null || e == null) return false;
        NPC npc = registry.getNPC(e);
        return npc != null && npc.getOwningRegistry() == registry;
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
        if (!ours(event.getEntity())) return;
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
       COMMANDES (/city habitants, /city garde)
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
                names.add(!inh.alive() ? "§8☠ " + inh.name + " (" + Math.max(0, (mourningMs - (t - inh.diedAt)) / 60_000) + " min)"
                        : inh.state == State.HABITANT ? "§f" + inh.name + (employerOf(inh) != null
                        ? " §3(⚒ " + plainName(employerOf(inh)) + ")" : "") : "§9" + label(inh));
            }
            Integer beds = bedsByBuilding.get(b.id());
            player.sendMessage("§7• §e" + b.name() + " §7(" + (beds == null ? "?" : beds) + " lits"
                    + (beds != null && beds == 0 ? ", §cincomplète§7" : "") + ") : "
                    + (names.isEmpty() ? "§8personne" : String.join("§7, ", names)));
        }
        long wait = Math.max(0, nextArrivalAt - System.currentTimeMillis()) / 1000;
        player.sendMessage("§8Prochaine arrivée possible dans " + wait / 60 + " min " + wait % 60 + " s ; impôt : "
                + tax + " coin(s) par habitant civil à l'aube.");
    }

    /** Bilan de la garde : taille, formations, gardes et recrues. */
    public void listGuard(Player player) {
        int rank = guardRank();
        player.sendMessage("§9🛡 Garde de Valmont : §f" + guardSize() + "/" + guard.maxGuards(rank)
                + " §7— formations : §f" + trainees().size() + "/" + guard.maxTraining(rank)
                + (rank == 0 ? " §c(Roland n'est pas installé)" : " §7(Roland rang " + rank + ")"));
        for (Inhabitant inh : guards()) player.sendMessage("§7• §9" + label(inh) + (inh.test ? " §8(test)" : ""));
        for (Inhabitant inh : trainees()) {
            player.sendMessage("§7• §e" + label(inh) + " §8(encore " + minutes(inh.trainingLeft) + ")");
        }
        player.sendMessage("§8Rôles : " + String.join(", ", guard.roles().stream()
                .map(r -> r.key() + (r.rank() > 1 ? " (rang " + r.rank() + ")" : "")).toList())
                + " — former : chez §6Roland§8, Actions → La garde");
    }

    /** Fait mourir un habitant ou un garde (test) ; false s'il est introuvable. */
    public boolean kill(String name) {
        Inhabitant inh = byName(name);
        if (inh == null) return false;
        NPC npc = npcs.get(inh.id);
        if (npc != null && npc.isSpawned() && npc.getEntity() instanceof LivingEntity body) {
            body.setHealth(0); // mort « réelle » : mêmes événements qu'un coup de monstre
        } else {
            die(inh);
        }
        return true;
    }

    /* =========================
       SIÈGES (phase 4)
       ========================= */

    /** Front d'un siège en cours (null hors siège) : les gardes y prennent position. */
    public void setSiegeFront(java.util.function.Supplier<SiegeFront> front) {
        this.siegeFront = front;
    }

    /**
     * Assaut : pendant une vague (assault vrai), les gardes quittent le front et sortent de la ville,
     * groupe par groupe, à la rencontre des escouades d'assaillants (bands, relues une fois par
     * passage) ; pendant l'alerte et les répits, ils tiennent le front.
     */
    public void setSiegeAssault(java.util.function.BooleanSupplier assault,
                                java.util.function.Supplier<List<SiegeFront.Band>> bands) {
        this.siegeAssault = assault;
        this.siegeBands = bands;
    }

    /** Reconnaît les soldats d'une armée ennemie : les gardes les combattent, ils peuvent blesser les habitants. */
    public void setHostileNpc(java.util.function.Predicate<Entity> hostile) {
        this.hostileNpc = hostile;
    }

    /** Fin d'un siège : les gardes quittent le front et rentrent vers leur quartier, puis reprennent leur ronde. */
    public void siegeEnded() {
        clearBattle();
        long until = System.currentTimeMillis() + 90_000;
        for (Inhabitant g : guards()) {
            NPC npc = npcs.get(g.id);
            if (npc == null || !npc.isSpawned()) continue;
            npc.getNavigator().cancelNavigation();
            awaySince.remove(g.id);
            Location home = homePoint(g);
            if (home != null) {
                returning.put(g.id, home);
                returnUntil.put(g.id, until);
            }
        }
    }

    /** Prévenu à chaque mort d'un habitant : (habitant, true s'il était garde ou recrue). */
    public void setDeathListener(java.util.function.BiConsumer<Inhabitant, Boolean> listener) {
        this.deathListener = listener;
    }

    /** Habitants, recrues et gardes affichés : les cibles des assaillants d'un siège. */
    public List<LivingEntity> defenders() {
        List<LivingEntity> list = new ArrayList<>();
        for (Map.Entry<Integer, NPC> e : npcs.entrySet()) {
            Inhabitant inh = all.get(e.getKey());
            NPC npc = e.getValue();
            if (inh != null && inh.alive() && npc.isSpawned() && npc.getEntity() instanceof LivingEntity body) list.add(body);
        }
        return list;
    }

    /* =========================
       EMPLOIS : LES HABITANTS TRAVAILLENT POUR LES ARTISANS
       Le jour, un employé se rend à l'annexe de travail de son artisan et y travaille : à chaque
       cycle (selon le niveau de l'annexe), l'entrepôt de l'artisan se remplit (JobsManager). La
       nuit, il rentre dormir. Un minuteur au-dessus de sa tête montre le prochain gain.
       ========================= */

    public void setWorkPlace(WorkPlace workPlace) {
        this.workPlace = workPlace;
    }

    /** Artisan employeur d'un habitant, ou null. */
    public CityNPC employerOf(Inhabitant inh) {
        return inh.employer == null ? null : CityNPC.fromTag(inh.employer);
    }

    /** Employés d'un artisan (habitants civils). */
    public List<Inhabitant> employees(CityNPC boss) {
        return all.values().stream()
                .filter(i -> i.state == State.HABITANT && !i.test && boss.tag.equals(i.employer)).toList();
    }

    /**
     * Embauche (boss non nul) ou renvoi (boss null) d'un habitant civil ; les limites (nombre
     * d'employés…) sont vérifiées par JobsManager. @return null si c'est fait, sinon la raison
     */
    public String setEmployer(int inhabitantId, CityNPC boss) {
        Inhabitant inh = all.get(inhabitantId);
        if (inh == null || inh.state != State.HABITANT || inh.test) return "Cet habitant n'est plus disponible.";
        inh.employer = boss == null ? null : boss.tag;
        inh.workLeft = 0;
        saveJob(inh);
        lastGain.remove(inh.id);
        removeTimer(inh.id);
        refreshNpc(inh); // outil du métier en main (ou rangé)
        return null;
    }

    /** Fin immédiate des cycles de travail en cours (test) ; renvoie le nombre d'objets produits. */
    public int finishWorkNow() {
        if (workPlace == null) return 0;
        int total = 0;
        for (Inhabitant inh : civilians()) {
            CityNPC boss = employerOf(inh);
            if (boss == null || inh.test || workPlace.cycleMs(boss) <= 0) continue;
            int made = workPlace.produce(boss);
            total += made;
            showGain(inh, made);
            inh.workLeft = workPlace.cycleMs(boss);
            saveJob(inh);
        }
        return total;
    }

    /**
     * Cycles de travail (toutes les 5 s) : seulement le jour, quand un joueur est connecté, que
     * l'artisan a son atelier et une annexe conforme, et que l'employé est sur place (un employé
     * non affiché — plafond d'affichage — est censé y être). Fin d'un cycle : production.
     */
    private void tickWork(long elapsed) {
        if (workPlace == null || Bukkit.getOnlinePlayers().isEmpty()) return;
        boolean save = ++workSaves % 6 == 0;
        for (Inhabitant inh : civilians()) {
            CityNPC boss = employerOf(inh);
            if (boss == null || inh.test) continue;
            long cycle = workPlace.cycleMs(boss);
            Location bed = inh.bed();
            if (cycle <= 0 || bed == null || isNight(bed.getWorld()) || !atWork(inh, boss)) continue;
            if (inh.workLeft <= 0 || inh.workLeft > cycle) inh.workLeft = cycle; // début, ou annexe améliorée
            inh.workLeft -= elapsed;
            if (inh.workLeft <= 0) {
                showGain(inh, workPlace.produce(boss));
                inh.workLeft += cycle;
                saveJob(inh);
            } else if (save) {
                saveJob(inh);
            }
        }
    }

    /** L'employé est-il à son poste ? Affiché : dans l'annexe (ou tout près) ; non affiché : oui. */
    private boolean atWork(Inhabitant inh, CityNPC boss) {
        NPC npc = npcs.get(inh.id);
        if (npc == null || !npc.isSpawned()) return true;
        Building annex = workPlace.annex(boss);
        if (annex == null) return false;
        Location at = npc.getEntity().getLocation();
        return at.getWorld().getName().equals(annex.world())
                && at.getBlockX() >= annex.x1() - 2 && at.getBlockX() <= annex.x2() + 2
                && at.getBlockZ() >= annex.z1() - 2 && at.getBlockZ() <= annex.z2() + 2;
    }

    private void showGain(Inhabitant inh, int made) {
        lastGain.put(inh.id, made > 0 ? "§a✔ +" + made + " objet" + (made > 1 ? "s" : "") + " à l'entrepôt"
                : "§7✔ entrepôt plein pour l'instant");
        lastGainAt.put(inh.id, System.currentTimeMillis());
    }

    /**
     * Employé, le jour : il se rend à sa place dans l'annexe de son artisan et y travaille
     * (gestes d'outil, particules du métier, sans son), minuteur au-dessus de la tête. Sans annexe,
     * il vit comme un habitant ordinaire ; la nuit, il rentre dormir.
     */
    private void workerBehaviour(Inhabitant inh, NPC npc, long now) {
        Location bed = inh.bed();
        CityNPC boss = employerOf(inh);
        Building annex = boss == null ? null : workPlace.annex(boss);
        if (bed == null || annex == null || isNight(bed.getWorld())) {
            removeTimer(inh.id);
            civilianBehaviour(inh, npc, now);
            return;
        }
        wake(inh, npc);
        Location spot = workSpot(inh, annex, boss);
        if (spot == null) { // annexe dans une zone non chargée : vie ordinaire en attendant
            removeTimer(inh.id);
            civilianBehaviour(inh, npc, now);
            return;
        }
        String blocked = workPlace.blocked(boss);
        showWorkTimer(inh, npc, boss, blocked, now);
        Entity body = npc.getEntity();
        if (!body.getWorld().equals(spot.getWorld()) || body.getLocation().distanceSquared(spot) > 1.0) {
            long away = awaySince.computeIfAbsent(inh.id, k -> now);
            if (now - away > 25_000) {
                // Chemin introuvable (porte, clôture, rivière) depuis 25 s : il rejoint sa place
                npc.getNavigator().cancelNavigation();
                npc.teleport(spot, PlayerTeleportEvent.TeleportCause.PLUGIN);
                awaySince.remove(inh.id);
            } else if (!npc.getNavigator().isNavigating()
                    && SafeNavigation.walkTo(npc, spot, walkSpeed, true) == SafeNavigation.Result.WALKING) {
                npc.getNavigator().getLocalParameters().distanceMargin(0.4);
            }
            return;
        }
        awaySince.remove(inh.id);
        if (npc.getNavigator().isNavigating()) npc.getNavigator().cancelNavigation();
        Location point = workPoint(annex);
        if (point != null && ticks % 4 == 0) npc.faceLocation(point.clone().add(0, -0.3, 0)); // tourné vers le point de travail
        if (blocked != null) return; // à son poste, mais à l'arrêt (annexe incomplète…)
        // Travail : un geste d'outil toutes les 1,5 s environ, des éclats de la matière du métier
        if (Math.floorMod(ticks + inh.id, 3) != 0) return;
        LivingEntity living = (LivingEntity) body;
        living.swingMainHand();
        Material mat = workPlace.workMaterial(boss);
        Location front = living.getEyeLocation().add(living.getEyeLocation().getDirection().setY(0).normalize().multiply(0.8))
                .subtract(0, 1.0, 0);
        World w = body.getWorld();
        if (mat.isBlock()) w.spawnParticle(Particle.BLOCK, front, 8, 0.25, 0.15, 0.25, 0, mat.createBlockData());
        else w.spawnParticle(Particle.ITEM, front, 6, 0.2, 0.15, 0.2, 0.02, new ItemStack(mat));
    }

    /** Point de travail de l'annexe (cliqué à sa création, sinon le centre de la zone), au niveau du sol. */
    private Location workPoint(Building annex) {
        World w = Bukkit.getWorld(annex.world());
        if (w == null) return null;
        int cx = annex.hasNpcPoint() ? (int) Math.floor(annex.npcX()) : (annex.x1() + annex.x2()) / 2;
        int cz = annex.hasNpcPoint() ? (int) Math.floor(annex.npcZ()) : (annex.z1() + annex.z2()) / 2;
        if (!w.isChunkLoaded(cx >> 4, cz >> 4)) return null;
        double cy = annex.hasNpcPoint() ? annex.npcY()
                : annex.refY() != null ? annex.refY() + 1 : w.getHighestBlockYAt(cx, cz) + 1;
        return new Location(w, cx + 0.5, cy, cz + 0.5);
    }

    /**
     * Place d'un employé : sur un cercle d'environ 2 blocs autour du point de travail, les
     * employés répartis à égale distance les uns des autres (jamais sur le point lui-même ni
     * l'un sur l'autre), dans l'annexe ; la place libre la plus proche de son angle.
     */
    private Location workSpot(Inhabitant inh, Building annex, CityNPC boss) {
        Location point = workPoint(annex);
        if (point == null) return null;
        World w = point.getWorld();
        int cx = point.getBlockX(), cy = point.getBlockY(), cz = point.getBlockZ();
        List<Inhabitant> staff = employees(boss);
        int n = Math.max(1, staff.size());
        int index = Math.max(0, staff.indexOf(inh));
        double angle = 2 * Math.PI * index / n;
        double tx = cx + 0.5 + 2.2 * Math.cos(angle), tz = cz + 0.5 + 2.2 * Math.sin(angle);

        // Places déjà prises par les employés précédents (dans l'ordre) : jamais deux sur la même
        java.util.Set<Long> taken = new java.util.HashSet<>();
        for (int k = 0; k < index; k++) {
            double a = 2 * Math.PI * k / n;
            Location other = nearestFree(w, annex, cx, cy, cz, cx + 0.5 + 2.2 * Math.cos(a), cz + 0.5 + 2.2 * Math.sin(a), taken);
            if (other != null) taken.add(((long) other.getBlockX() << 32) ^ (other.getBlockZ() & 0xffffffffL));
        }
        Location spot = nearestFree(w, annex, cx, cy, cz, tx, tz, taken);
        return spot != null ? spot : point;
    }

    /** Place libre (debout, dans l'annexe, ni le point ni une place prise) la plus proche de (tx, tz). */
    private Location nearestFree(World w, Building annex, int cx, int cy, int cz, double tx, double tz,
                                 java.util.Set<Long> taken) {
        Location best = null;
        double bestDist = Double.MAX_VALUE;
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                int x = cx + dx, z = cz + dz;
                if (dx == 0 && dz == 0) continue; // le point de travail lui-même
                if (x < annex.x1() || x > annex.x2() || z < annex.z1() || z > annex.z2()) continue;
                if (taken.contains(((long) x << 32) ^ (z & 0xffffffffL)) || !w.isChunkLoaded(x >> 4, z >> 4)) continue;
                double d = (x + 0.5 - tx) * (x + 0.5 - tx) + (z + 0.5 - tz) * (z + 0.5 - tz);
                if (d >= bestDist) continue;
                for (int dy = 2; dy >= -2; dy--) {
                    Block feet = w.getBlockAt(x, cy + dy, z);
                    if (standable(feet)) { bestDist = d; best = feet.getLocation().add(0.5, 0, 0.5); break; }
                }
            }
        }
        return best;
    }

    /** Minuteur de travail : « ⚒ Hélène — au travail », barre et temps avant le prochain gain. */
    private void showWorkTimer(Inhabitant inh, NPC npc, CityNPC boss, String blocked, long now) {
        String text;
        long cycle = workPlace.cycleMs(boss);
        String gain = lastGain.get(inh.id);
        if (gain != null && now - lastGainAt.getOrDefault(inh.id, 0L) < 4000) {
            text = "§3⚒ " + plainName(boss) + "\n" + gain;
        } else if (blocked != null || cycle <= 0) {
            text = "§3⚒ " + plainName(boss) + "\n§c⏸ " + (blocked != null ? blocked : "à l'arrêt");
        } else {
            boolean counting = !Bukkit.getOnlinePlayers().isEmpty() && atWork(inh, boss);
            long left = Math.max(0, (inh.workLeft <= 0 || inh.workLeft > cycle ? cycle : inh.workLeft)
                    - (counting ? now - lastMaintenance : 0));
            int filled = (int) Math.round(10.0 * Math.min(1, Math.max(0, 1 - (double) left / cycle)));
            long sec = (left + 999) / 1000;
            String bar = "§b" + "▮".repeat(filled) + "§8" + "▮".repeat(10 - filled);
            text = "§3⚒ " + plainName(boss) + " §7— " + (counting ? "au travail" : "en route") + "\n" + bar + " §f"
                    + String.format("%d:%02d", sec / 60, sec % 60);
        }
        Location above = npc.getEntity().getLocation().add(0, 2.45, 0);
        TextDisplay display = timers.get(inh.id);
        if (display == null || !display.isValid() || !display.getWorld().equals(above.getWorld())) {
            if (display != null) display.remove();
            display = above.getWorld().spawn(above, TextDisplay.class, d -> {
                d.setPersistent(false);
                d.setBillboard(Display.Billboard.CENTER);
                d.setShadowed(true);
                d.setDefaultBackground(false);
                d.setBackgroundColor(Color.fromARGB(110, 0, 0, 0));
                d.setTeleportDuration(10);
            });
            timers.put(inh.id, display);
        } else if (display.getLocation().distanceSquared(above) > 0.0025) {
            display.teleport(above);
        }
        display.setText(text);
    }

    private static String plainName(CityNPC npc) {
        return npc.displayName.replaceAll("§.", "");
    }


    /* =========================
       BESOINS (phase 6)
       ========================= */

    public void setDawnListener(Runnable listener) {
        this.dawnListener = listener;
    }

    public void setTaxMultiplier(java.util.function.DoubleSupplier multiplier) {
        this.taxMultiplier = multiplier;
    }

    public void setArrivalFactor(java.util.function.DoubleSupplier factor) {
        this.arrivalFactor = factor;
    }

    /** Lits en deuil (habitant mort, remplacement en attente). */
    public int mourningCount() {
        return mourning();
    }

    /** Millisecondes avant la prochaine arrivée possible (0 = dès qu'un lit est libre). */
    public long nextArrivalIn() {
        return Math.max(0, nextArrivalAt - System.currentTimeMillis());
    }

    /** Maison d'un habitant (bâtiment), ou null. */
    public Building home(Inhabitant inh) {
        return inh.test ? null : homes.get(inh.buildingId);
    }

    /** Habitant par son identifiant (menus), ou null. */
    public Inhabitant get(int id) {
        return all.get(id);
    }

    /** Habitant vivant par son nom (casse ignorée), ou null. */
    public Inhabitant byName(String name) {
        for (Inhabitant inh : all.values()) if (inh.alive() && inh.name.equalsIgnoreCase(name)) return inh;
        return null;
    }

    /** Fin immédiate de tous les deuils (test) ; renvoie le nombre de lits libérés. */
    public int endAllMourning() {
        return endMourning(true);
    }

    /** Noms des habitants vivants (complétion de commande). */
    public List<String> livingNames() {
        return living().stream().map(i -> i.name).toList();
    }

    /** Noms des habitants civils, qu'on peut former (complétion de /city garde former). */
    public List<String> civilianNames() {
        return civilians().stream().map(i -> i.name).toList();
    }

    /** Noms des gardes et des recrues (complétion de /city garde renvoyer). */
    public List<String> guardNames() {
        List<String> names = new ArrayList<>(guards().stream().map(i -> i.name).toList());
        trainees().forEach(i -> names.add(i.name));
        return names;
    }

    private static String key(String world, int x, int y, int z) {
        return world + ":" + x + ":" + y + ":" + z;
    }
}
