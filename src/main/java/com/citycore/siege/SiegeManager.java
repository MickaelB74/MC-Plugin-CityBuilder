package com.citycore.siege;

import com.citycore.building.Building;
import com.citycore.building.BuildingManager;
import com.citycore.building.BuildingType;
import com.citycore.city.CityManager;
import com.citycore.economy.MarketService;
import com.citycore.inhabitant.Engagements;
import com.citycore.inhabitant.InhabitantManager;
import com.citycore.inhabitant.SiegeFront;
import com.citycore.npc.SafeNavigation;
import com.citycore.util.GroundFinder;
import com.citycore.npc.CityNPC;
import com.citycore.npc.NPCDataManager;
import com.citycore.npc.NPCState;
import com.citycore.npc.villager.VillagerConfig;
import com.citycore.npc.villager.VillagerGUI;
import com.citycore.quest.city.CityTier;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.AbstractSkeleton;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Evoker;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Pillager;
import org.bukkit.entity.Player;
import org.bukkit.entity.Raider;
import org.bukkit.entity.Witch;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.IntSupplier;

/**
 * Sièges de Valmont (phase 4 du plan « Valmont vivante ») : des vagues d'assaillants attaquent la
 * ville. Deux types, tirés au hasard (sieges.types) : des **monstres**, ou l'**armée d'une
 * faction ennemie** (« bannière » : des soldats PNJ comme la garde, voir EnemyArmy).
 *
 * Les assaillants apparaissent hors des chunks revendiqués, d'un côté de la lisière (annoncé), et
 * s'en prennent aux habitants et aux gardes ; faute de cible, ils marchent sur la mairie. Dès
 * l'alerte et pendant les répits, la garde prend position sur ce front (première ligne au
 * contact, archers en retrait) ; à la fin du siège, elle rentre en ville et reprend ses rondes.
 * Aucun bloc n'est touché dans la ville et les joueurs y restent invulnérables.
 *
 * Champ de bataille : chaque vague arrive progressivement (sieges.arrivee-secondes), par petites
 * escouades (Squad) réparties sur plusieurs points d'entrée le long du front (sieges.largeur-front,
 * sieges.points-entree). Chaque escouade avance sur sa propre voie, en formation derrière son
 * meneur ; pas plus de sieges.escouades.max-par-cible assaillants au contact d'un même défenseur.
 * La garde, répartie en groupes, va à la rencontre des escouades les plus proches de son secteur.
 *
 * Victoire : toutes les vagues repoussées dans le temps imparti → récompense dans la caisse et XP
 * pour Roland. Défaite (temps écoulé) : les assaillants se retirent sans rien rapporter.
 * Les assaillants ne sont jamais enregistrés avec le monde : un redémarrage en plein siège ne
 * laisse personne derrière lui.
 */
public final class SiegeManager implements Listener {

    private enum Phase { IDLE, PREPARING, WAVE, PAUSE }

    private enum Result { VICTORY, DEFEAT, STOPPED, ABANDONED }

    /** Côté attaqué : apparition (hors de la ville), ralliement et postes de la garde (dans la ville). */
    private record Side(Location spawn, Location rally, String direction, SiegeFront front) {}

    /** Arrivée prévue d'un assaillant : quand, avec quelle escouade (champion, porte-étendard). */
    private record Arrival(long due, Squad squad, boolean champion, boolean standard) {}

    private final JavaPlugin        plugin;
    private final CityManager       city;
    private final BuildingManager   buildings;
    private final InhabitantManager inhabitants;
    private final NPCDataManager    npcData;
    private final MarketService     market;
    private final IntSupplier       cityLevel;
    private final SiegeConfig       config;
    private final EnemyArmy         army;
    private final com.citycore.city.ClaimCache claims; // chunks de la ville (tireurs : entrer d'abord)
    private final NamespacedKey     key;

    private Phase phase = Phase.IDLE;
    private SiegeConfig.Kind kind = SiegeConfig.Kind.MONSTRES;
    private SiegeConfig.Faction faction;
    private int   tier, totalWaves, wave, waveTotal, pendingRespawn, sideFailures, ticks;
    private int   lastCountdown = -1; // dernière seconde du compte à rebours affichée
    private long  deadline, phaseEndsAt;
    private Side  side;
    private Location hall;          // mairie (relue au début du siège)
    private UUID  championId;
    private String championName;
    private boolean championAlive;
    private BossBar bar;
    private BukkitTask task, arrivalTask;
    private final Map<UUID, Mob> alive = new LinkedHashMap<>();
    private final Deque<Arrival> arrivals = new ArrayDeque<>();   // assaillants de la vague encore en route (par date)
    private final List<Squad> squads = new ArrayList<>();         // escouades de la vague (de gauche à droite)
    private final Map<UUID, Squad> packOf = new HashMap<>();      // monstre → son escouade
    private final Engagements tally = new Engagements();          // monstres au contact de chaque défenseur
    private final Map<String, Integer> playerKills = new HashMap<>();
    private int guardKills, civiliansLost, guardsLost;
    private java.util.function.BiConsumer<Boolean, Integer> resultListener; // (victoire, palier) : sécurité (phase 6)
    private List<Building> buildingList = List.of(); // bâtiments (zones à éviter), relus au plus toutes les 5 s
    private long buildingsReadAt;

    public SiegeManager(JavaPlugin plugin, CityManager city, BuildingManager buildings, InhabitantManager inhabitants,
                        NPCDataManager npcData, MarketService market, IntSupplier cityLevel) {
        this.plugin      = plugin;
        this.city        = city;
        this.buildings   = buildings;
        this.inhabitants = inhabitants;
        this.npcData     = npcData;
        this.market      = market;
        this.cityLevel   = cityLevel;
        this.config      = new SiegeConfig(plugin);
        this.claims      = new com.citycore.city.ClaimCache(city);
        this.army        = new EnemyArmy(plugin, inhabitants, config, claims::isClaimed);
        this.key         = new NamespacedKey(plugin, "siege");
        // La garde prend position sur le front ; elle combat aussi les soldats ennemis
        inhabitants.setSiegeFront(() -> running() && side != null ? side.front() : null);
        inhabitants.setHostileNpc(army::isSoldier);
        // Vague en cours : la garde sort de la ville, par groupes, à la rencontre des escouades
        inhabitants.setSiegeAssault(() -> phase == Phase.WAVE, this::bands);
        inhabitants.setDeathListener((inh, wasGuard) -> {
            if (!running()) return;
            if (wasGuard) guardsLost++; else civiliansLost++;
        });
        army.setObjective(() -> hall != null ? hall : side != null ? side.rally() : null);
        army.setKillListener(this::armyKill);
    }

    /** Démarrage du serveur : assaillants d'un siège interrompu (arrêt brutal) retirés. */
    public void start() {
        plugin.getServer().getPluginManager().registerEvents(army, plugin);
        army.start();
        for (World w : Bukkit.getWorlds()) {
            for (Entity e : w.getEntities()) if (isAttacker(e)) e.remove();
        }
    }

    /** Arrêt du serveur : le siège en cours s'arrête sans rien laisser. */
    public void shutdown() {
        if (running()) end(Result.STOPPED, false);
    }

    public SiegeConfig config() {
        return config;
    }

    /** Prévenu de l'issue de chaque siège mené jusqu'au bout : (victoire, palier). */
    /** Raison d'empêcher un siège de commencer (expédition en cours, plan « Raids »), ou null. */
    private java.util.function.Supplier<String> blocker = () -> null;

    public void setBlocker(java.util.function.Supplier<String> blocker) {
        this.blocker = blocker;
    }

    public void setResultListener(java.util.function.BiConsumer<Boolean, Integer> listener) {
        this.resultListener = listener;
    }

    /** Relit les réglages des sièges (et les uniformes ennemis). */
    public void reload() {
        config.reload();
        army.reloadSkins();
    }

    public boolean running() {
        return phase != Phase.IDLE;
    }

    /** Le siège comme type d'événement du calendrier (« siege » dans le planning). */
    public EventCalendar.Event asEvent() {
        return new EventCalendar.Event() {
            @Override public String label()     { return "Siège"; }
            @Override public String readiness() { return SiegeManager.this.readiness(); }
            @Override public String start()     { return begin(null, false, null); }
            @Override public boolean running()  { return SiegeManager.this.running(); }
            @Override public boolean stop()     { return SiegeManager.this.stop(); }
        };
    }

    /** Pendant un siège (si sieges.joueurs-blesses) : cet attaquant peut blesser les joueurs en ville. */
    public boolean woundsPlayers(Entity damager) {
        return running() && config.playersWounded() && (isAttacker(damager) || army.isSoldier(damager));
    }

    /** Assaillant d'un siège en cours : monstre d'une vague ou soldat de l'armée ennemie (récits de Musashi). */
    public boolean isSiegeEnemy(Entity e) {
        return running() && (isAttacker(e) || army.isSoldier(e));
    }

    /** Monstre marqué comme assaillant d'un siège. */
    public boolean isAttacker(Entity e) {
        return e.getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }

    /* =========================
       LANCEMENT, ARRÊT
       ========================= */

    /**
     * Raison pour laquelle un siège programmé ne peut pas avoir lieu, ou null s'il le peut
     * (calendrier : palier de la ville, territoire, difficulté).
     */
    public String readiness() {
        World w = cityWorld();
        if (w == null) return "Valmont n'a pas encore de territoire";
        if (w.getDifficulty() == Difficulty.PEACEFUL) return "difficulté « paisible » : aucun monstre ne peut apparaître";
        int level = cityLevel.getAsInt();
        if (level < config.firstTier()) {
            return "Valmont est encore trop modeste (" + chapter(level) + ") : sièges à partir du " + chapter(config.firstTier());
        }
        return null;
    }

    /**
     * Lance un siège. @param forcedTier palier imposé (commande de test), ou null = palier de la
     * ville ; @param manual true = lancé par un admin (pas de palier minimal) ; @param forcedKind
     * type imposé, ou null = tiré au hasard (sieges.types)
     * @return null si le siège commence, sinon la raison du refus
     */
    public String begin(Integer forcedTier, boolean manual, SiegeConfig.Kind forcedKind) {
        if (running()) return "Un siège est déjà en cours.";
        String blocked = blocker.get();
        if (blocked != null) return "Pas de siège maintenant : " + blocked + ".";
        if (Bukkit.getOnlinePlayers().isEmpty()) return "Aucun joueur connecté.";
        reload();
        World w = cityWorld();
        if (w == null) return "Valmont n'a pas encore de territoire.";
        String notReady = readiness();
        if (notReady != null && (!manual || notReady.startsWith("difficulté"))) {
            return Character.toUpperCase(notReady.charAt(0)) + notReady.substring(1) + ".";
        }
        kind = forcedKind != null ? forcedKind : config.randomKind();
        faction = kind == SiegeConfig.Kind.BANNIERE ? config.randomFaction() : null;
        tier = Math.max(1, Math.min(10, forcedTier != null ? forcedTier : cityLevel.getAsInt()));
        totalWaves = config.waves(tier);
        wave = waveTotal = pendingRespawn = sideFailures = 0;
        alive.clear();
        arrivals.clear();
        squads.clear();
        packOf.clear();
        playerKills.clear();
        guardKills = civiliansLost = guardsLost = 0;
        championId = null;
        championName = null;
        championAlive = false;
        hall = townHall();
        long now = System.currentTimeMillis();
        phaseEndsAt = now + config.prepSeconds() * 1000L;
        deadline = phaseEndsAt + config.durationMs();
        side = pickSide();
        phase = Phase.PREPARING;

        bar = Bukkit.createBossBar("", BarColor.RED, BarStyle.SOLID); // rouge : le panneau de quêtes masque les barres blanches
        for (Player p : Bukkit.getOnlinePlayers()) bar.addPlayer(p);
        updateBar(now);
        String from = side != null ? " par " + side.direction() : "";
        String who = enemyName();
        Bukkit.broadcastMessage("§c⚔ §lSiège de Valmont ! §7" + chapter(tier) + " — " + capitalize(who) + " §7("
                + totalWaves + " vagues) arrive" + from + " dans §f" + config.prepSeconds() + " s§7. La garde prend position.");
        for (Player p : Bukkit.getOnlinePlayers()) p.sendTitle("§c⚔ Siège !", "§7" + capitalize(plain(who)) + from, 10, 60, 20);
        SiegeSounds.alarm(plugin, hall, side != null ? side.spawn() : null, kind == SiegeConfig.Kind.BANNIERE);
        if (npcData.getState(CityNPC.GUARD) != NPCState.WANDERER && !inhabitants.guards().isEmpty()) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> Bukkit.broadcastMessage(CityNPC.GUARD.displayName
                    + " §f: §o« Gardes, à vos postes" + (side != null ? " ! Ils viennent par " + side.direction() : "") + " ! »"), 40L);
        }
        lastCountdown = -1;
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
        arrivalTask = Bukkit.getScheduler().runTaskTimer(plugin, this::spawnArrivals, 5L, 5L); // arrivées en filet
        plugin.getLogger().info("Siège lancé : " + kind.key + (faction != null ? " (" + faction.name() + ")" : "")
                + ", palier " + tier + ", " + totalWaves + " vagues.");
        return null;
    }

    /** Arrêt par un admin : les assaillants sont retirés, aucune récompense. */
    public boolean stop() {
        if (!running()) return false;
        end(Result.STOPPED, true);
        return true;
    }

    /** Test : la vague en cours est retirée, comme si elle avait été repoussée (ou l'attente écourtée). */
    public boolean skipWave() {
        if (phase != Phase.WAVE) {
            if (phase == Phase.PREPARING || phase == Phase.PAUSE) { phaseEndsAt = 0; return true; }
            return false;
        }
        for (Mob m : alive.values()) m.remove();
        alive.clear();
        army.clear();
        arrivals.clear(); // ceux qui étaient encore en route ne viendront pas
        pendingRespawn = 0;
        waveCleared(System.currentTimeMillis());
        return true;
    }

    /* =========================
       DÉROULEMENT (chaque seconde)
       ========================= */

    private void tick() {
        ticks++;
        long now = System.currentTimeMillis();
        if (Bukkit.getOnlinePlayers().isEmpty()) { end(Result.ABANDONED, true); return; }
        if (now > deadline) { end(Result.DEFEAT, true); return; }
        switch (phase) {
            case PREPARING, PAUSE -> {
                if (side == null) side = pickSide(); // la garde se met en position dès qu'un côté est trouvé
                if (now >= phaseEndsAt) {
                    startWave();
                } else {
                    // Compte à rebours : « 3, 2, 1 » à l'écran (sans son : seuls le début, la fin et la
                    // chute de la bannière sont sonores)
                    int secs = (int) ((phaseEndsAt - now + 999) / 1000);
                    if (secs <= 3 && secs != lastCountdown) {
                        lastCountdown = secs;
                        for (Player p : Bukkit.getOnlinePlayers()) p.sendTitle("", "§c" + secs, 0, 22, 0);
                    }
                }
            }
            case WAVE -> {
                prune();
                respawnPending();
                retarget();
                if (left() == 0) waveCleared(now);
            }
            default -> { }
        }
        if (running()) updateBar(now);
    }

    /** Assaillants encore à abattre dans la vague (présents, encore en route, et ceux à faire réapparaître). */
    private int left() {
        return alive.size() + army.alive() + arrivals.size() + pendingRespawn;
    }

    private void startWave() {
        if (side == null) {
            // Aucun point d'apparition chargé hors de la ville : on réessaie quelques secondes
            if (++sideFailures >= 10) {
                Bukkit.broadcastMessage("§7⚔ Les assaillants ne trouvent pas de chemin jusqu'à Valmont : le siège est levé.");
                end(Result.STOPPED, false);
            }
            return;
        }
        sideFailures = 0;
        wave++;
        int count = config.attackers(tier, Bukkit.getOnlinePlayers().size());
        boolean last = wave == totalWaves;
        String champion = last ? plannedChampion() : null;
        planArrivals(count, champion != null);
        waveTotal = alive.size() + army.alive() + arrivals.size();
        phase = Phase.WAVE;
        String units = kind == SiegeConfig.Kind.BANNIERE ? "soldats" : "assaillants";
        Bukkit.broadcastMessage("§c⚔ Vague " + wave + "/" + totalWaves + " : §f" + waveTotal + " " + units + " §carrivent par "
                + side.direction() + (squads.size() > 1 ? ", §7en " + squads.size() + " escouades sur tout le front§c" : "")
                + " !" + (champion != null ? " §4" + champion + " §cferme la marche." : ""));
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendTitle("", "§cVague " + wave + "/" + totalWaves + " §7— par " + side.direction(), 5, 40, 10);
        }
        lastCountdown = -1;
        spawnArrivals(); // la première escouade arrive tout de suite
    }

    /** Nom du champion de la dernière vague à ce palier (armée ou monstres), ou null. */
    private String plannedChampion() {
        if (kind == SiegeConfig.Kind.BANNIERE) {
            SiegeConfig.Unit u = config.armyChampion(tier);
            return u != null ? u.name() : null;
        }
        SiegeConfig.Champion c = config.champion(tier);
        return c != null ? c.name() : null;
    }

    /**
     * Plan d'arrivée d'une vague : les count assaillants sont répartis en escouades de
     * sieges.escouades.min à max, chacune affectée à un point d'entrée (réparties sur toute la
     * largeur du front), et leurs apparitions étalées sur sieges.arrivee-secondes : les escouades
     * du centre d'abord, puis celles des ailes, quelques soldats à la fois. Le porte-étendard
     * ouvre la première escouade ; le champion (champion vrai) mène la dernière.
     */
    private void planArrivals(int count, boolean champion) {
        arrivals.clear();
        squads.clear();
        packOf.clear();
        SiegeFront f = side.front();
        List<Location> entries = f.entries().isEmpty() ? List.of(side.spawn()) : f.entries();
        int avg = Math.max(1, (config.squadMin() + config.squadMax()) / 2);
        int n = Math.max((int) Math.round((double) count / avg), (int) Math.ceil((double) count / config.squadMax()));
        n = Math.max(1, Math.min(n, count));
        // Escouades de gauche à droite : chacune son point d'entrée, réparties sur toute la largeur
        for (int j = 0; j < n; j++) {
            int e = (int) ((j + 0.5) * entries.size() / n); // plus d'escouades que d'entrées : elles se partagent les entrées, en ordre
            Location entry = entries.get(Math.max(0, Math.min(entries.size() - 1, e)));
            squads.add(new Squad(j, entry, goalsFor(entry)));
        }
        // Ordre d'arrivée : du centre vers les ailes
        List<Squad> order = new ArrayList<>(squads);
        order.sort(Comparator.comparingDouble(s -> Math.abs(f.lateral(s.entry))));
        long now = System.currentTimeMillis(), window = config.arrivalMs();
        long spacing = window / n;                                         // entre deux escouades
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        int base = count / n, extra = count % n;
        for (int k = 0; k < n; k++) {
            Squad sq = order.get(k);
            int size = base + (k < extra ? 1 : 0) + (champion && k == n - 1 ? 1 : 0);
            long start = now + k * spacing + (k == 0 || spacing < 3 ? 0 : rnd.nextLong(spacing / 3));
            long gap = window == 0 ? 0 : Math.max(250, Math.min(900, spacing / Math.max(1, size)));
            int bearer = champion && n == 1 ? 1 : 0; // une seule escouade : le porte-étendard suit le champion
            for (int m = 0; m < size; m++) {
                boolean leadsLast = champion && k == n - 1 && m == 0;                  // le champion mène la dernière
                boolean standard = kind == SiegeConfig.Kind.BANNIERE && k == 0 && m == bearer;
                arrivals.add(new Arrival(start + m * gap, sq, leadsLast, standard));
            }
        }
        List<Arrival> sorted = new ArrayList<>(arrivals);
        sorted.sort(Comparator.comparingLong(Arrival::due));
        arrivals.clear();
        arrivals.addAll(sorted);
    }

    /**
     * Objectifs d'une escouade venue par entry : percer la lisière en face de son entrée (une
     * douzaine de blocs dans la ville), puis avancer vers le cœur de la ville en gardant une
     * partie de son décalage (les escouades ne convergent pas toutes au même point), enfin la
     * mairie. Points au sol, hors des bâtiments ; ceux qui ne conviennent pas sont sautés.
     */
    private List<Location> goalsFor(Location entry) {
        SiegeFront f = side.front();
        double lat = f.lateral(entry);
        Vector inward = f.rally().toVector().subtract(f.enemy().toVector()).setY(0);
        if (inward.lengthSquared() < 1e-4) inward = new Vector(0, 0, 0); else inward.normalize();
        List<Location> goals = new ArrayList<>();
        Location lane = f.rally().clone().add(f.across().clone().multiply(lat));
        Location breach = cityGround(lane.clone().add(inward.clone().multiply(4)), f.rally().getBlockY());
        if (breach != null) goals.add(breach);
        Location end = hall != null ? hall : f.rally().clone().add(inward.clone().multiply(24));
        Location from = breach != null ? breach : lane;
        Location toward = end.clone().add(f.across().clone().multiply(lat * 0.4));
        Location heart = cityGround(from.clone().add(toward.toVector().subtract(from.toVector()).multiply(0.5)),
                (from.getBlockY() + end.getBlockY()) / 2);
        if (heart != null) goals.add(heart);
        if (hall != null) goals.add(hall);
        return goals;
    }

    /** Point au sol près de at (à peu près à la hauteur y), hors des bâtiments, chunk chargé ; null sinon. */
    private Location cityGround(Location at, int y) {
        World w = at.getWorld();
        for (int[] d : new int[][]{{0, 0}, {2, 0}, {-2, 0}, {0, 2}, {0, -2}, {3, 3}, {-3, -3}}) {
            int x = at.getBlockX() + d[0], z = at.getBlockZ() + d[1];
            if (!w.isChunkLoaded(x >> 4, z >> 4) || insideBuilding(w.getName(), x, z)) continue;
            Location g = GroundFinder.standAt(w, x, y, z);
            if (g == null) g = GroundFinder.groundAt(w, x, z); // relief : le sol naturel de la colonne
            if (g != null && !insideBuilding(w.getName(), g.getBlockX(), g.getBlockZ())) return g;
        }
        return null;
    }

    /** Arrivées dues (toutes les 5 ticks) : quelques assaillants à la fois, à leur point d'entrée. */
    private void spawnArrivals() {
        if (phase != Phase.WAVE) return;
        long now = System.currentTimeMillis();
        for (int i = 0; i < 4 && !arrivals.isEmpty() && arrivals.peekFirst().due() <= now; i++) {
            Arrival a = arrivals.pollFirst();
            spawnOne(a.squad(), a.champion(), a.standard());
        }
    }

    /**
     * Un assaillant du type du siège, au point d'entrée de son escouade (squad null : au point
     * d'apparition central) ; champion : celui de la dernière vague (s'il y en a un à ce palier).
     */
    private void spawnOne(Squad squad, boolean champion, boolean standard) {
        if (side == null) return;
        Location base = squad != null && SafeNavigation.isLoaded(squad.entry) ? squad.entry : side.spawn();
        Location at = spotNear(base, 3);
        if (at == null) at = base;
        if (kind == SiegeConfig.Kind.BANNIERE) {
            SiegeConfig.Unit unit = champion ? config.armyChampion(tier) : config.randomUnit(tier);
            if (unit == null) return;
            if (army.spawn(unit, at, faction, champion, standard, squad) != null && champion) {
                championName = unit.name();
                championAlive = true;
                championEnters(at);
            }
            return;
        }
        SiegeConfig.Champion champ = champion ? config.champion(tier) : null;
        if (champion && champ == null) return;
        EntityType type = champ != null ? champ.type() : config.randomMonster(tier);
        if (type.getEntityClass() == null) return;
        Entity e = at.getWorld().spawn(at, type.getEntityClass(), false, ent -> configure(ent, champ));
        if (!(e instanceof Mob m) || !m.isValid()) {
            if (e != null) e.remove();
            return;
        }
        alive.put(m.getUniqueId(), m);
        if (squad != null) packOf.put(m.getUniqueId(), squad);
        if (champ != null) {
            championId = m.getUniqueId();
            championName = champ.name();
            championAlive = true;
            championEnters(at);
        }
    }

    /** Moment clé : le champion entre dans la bataille (annonce et cor de guerre venu de lui). */
    private void championEnters(Location at) {
        Bukkit.broadcastMessage("§4⚔ " + championName + " §centre dans la bataille" + (side != null ? " par " + side.direction() : "") + " !");
        SiegeSounds.warHorn(at, kind == SiegeConfig.Kind.BANNIERE);
    }

    private void waveCleared(long now) {
        squads.clear();
        packOf.clear();
        if (wave >= totalWaves) {
            end(Result.VICTORY, true);
            return;
        }
        phase = Phase.PAUSE;
        phaseEndsAt = now + config.pauseSeconds() * 1000L;
        side = pickSide(); // la vague suivante peut venir d'ailleurs : la garde change de front
        Bukkit.broadcastMessage("§a⚔ Vague " + wave + "/" + totalWaves + " repoussée ! §7La suivante arrive"
                + (side != null ? " par " + side.direction() : "") + " dans " + config.pauseSeconds() + " s.");
    }

    /** Fin du siège : bilan, récompense (victoire), assaillants retirés, la garde rentre en ville. */
    private void end(Result result, boolean announce) {
        if (task != null) task.cancel();
        task = null;
        if (arrivalTask != null) arrivalTask.cancel();
        arrivalTask = null;
        for (Mob m : alive.values()) if (m.isValid()) m.remove();
        alive.clear();
        army.clear();
        arrivals.clear();
        squads.clear();
        packOf.clear();
        tally.clear();
        for (World w : Bukkit.getWorlds()) {
            for (Entity e : w.getEntities()) if (isAttacker(e)) e.remove(); // transformés, égarés
        }
        if (bar != null) bar.removeAll();
        bar = null;
        phase = Phase.IDLE;
        Location show = hall != null ? hall : side != null ? side.rally() : null; // feux d'artifice de la victoire
        side = null;
        inhabitants.siegeEnded(); // retour en ville, puis rondes habituelles
        if (!announce) return;

        switch (result) {
            case VICTORY -> {
                int coins = config.rewardCoins(tier);
                int total = city.addCityCoins(coins);
                Bukkit.broadcastMessage("§6🏆 §lVictoire ! §6Valmont a repoussé " + plain(enemyName()) + ". §f+" + coins
                        + " coins §7(caisse : " + total + ")");
                rewardRoland();
                for (Player p : Bukkit.getOnlinePlayers()) p.sendTitle("§6Victoire !", "§7Valmont a tenu bon", 10, 70, 20);
                SiegeSounds.victory(plugin, show);
            }
            case DEFEAT -> {
                Bukkit.broadcastMessage("§8⚔ Le temps est écoulé : " + plain(enemyName()) + " se retire avec son butin. "
                        + "§7Pas de récompense cette fois.");
                for (Player p : Bukkit.getOnlinePlayers()) p.sendTitle("§8Siège perdu", "§7Les assaillants se retirent", 10, 70, 20);
                SiegeSounds.defeat(hall);
            }
            case STOPPED -> Bukkit.broadcastMessage("§7⚔ Le siège est interrompu.");
            case ABANDONED -> plugin.getLogger().info("Siège abandonné : plus aucun joueur connecté.");
        }
        if (result == Result.VICTORY || result == Result.DEFEAT) {
            summary();
            if (resultListener != null) resultListener.accept(result == Result.VICTORY, tier);
        }
    }

    /** Bilan : meilleurs défenseurs, ennemis abattus par la garde, pertes. */
    private void summary() {
        List<Map.Entry<String, Integer>> best = new ArrayList<>(playerKills.entrySet());
        best.sort(Map.Entry.<String, Integer>comparingByValue().reversed());
        StringBuilder sb = new StringBuilder("§7Bilan : ");
        if (best.isEmpty()) sb.append("§8aucun assaillant abattu par un joueur");
        for (int i = 0; i < Math.min(3, best.size()); i++) {
            if (i > 0) sb.append("§7, ");
            sb.append("§f").append(best.get(i).getKey()).append(" §7(").append(best.get(i).getValue()).append(")");
        }
        sb.append("§7 ; la garde : §f").append(guardKills);
        sb.append("§7 ; pertes : §f").append(civiliansLost).append(" habitant(s), ").append(guardsLost).append(" garde(s)");
        Bukkit.broadcastMessage(sb.toString());
    }

    /** XP de Roland (s'il a rejoint la ville) : une victoire fait progresser le capitaine de la garde. */
    private void rewardRoland() {
        if (npcData.getState(CityNPC.GUARD) == NPCState.WANDERER) return;
        VillagerConfig vc = market.config(CityNPC.GUARD);
        if (vc == null) return;
        int xp = config.rewardXp(tier);
        boolean levelUp = npcData.addXP(CityNPC.GUARD, xp, vc.getXpThresholds());
        Bukkit.broadcastMessage("§9🛡 " + CityNPC.GUARD.displayName + " §7gagne §b+" + xp + " XP§7.");
        if (levelUp) {
            Bukkit.broadcastMessage("§a🎉 §e" + CityNPC.GUARD.displayName + " §aest passé "
                    + VillagerGUI.getLevelName(npcData.getLevel(CityNPC.GUARD)) + "§a ! §7(la garde peut grandir)");
        }
    }

    /** « des monstres », « l'Ost du Corbeau » (couleur de la faction). */
    private String enemyName() {
        return kind == SiegeConfig.Kind.BANNIERE && faction != null ? faction.color() + faction.name() : "§cdes monstres";
    }

    /* =========================
       MONSTRES
       ========================= */

    /**
     * Réglages d'un monstre avant son apparition : marque, jamais enregistré avec le monde, pas
     * de disparition naturelle, ni ramassage d'objets ; ne brûle pas au soleil ; arme de base
     * (non lâchée) ; champion nommé, lumineux et coriace.
     */
    private void configure(Entity e, SiegeConfig.Champion champ) {
        e.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        e.setPersistent(false);
        if (!(e instanceof Mob m)) return;
        m.setRemoveWhenFarAway(false);
        m.setCanPickupItems(false);
        AttributeInstance follow = m.getAttribute(Attribute.GENERIC_FOLLOW_RANGE);
        if (follow != null) follow.setBaseValue(Math.max(follow.getBaseValue(), 48));
        if (m instanceof Zombie z) z.setShouldBurnInDay(false);
        if (m instanceof AbstractSkeleton s) s.setShouldBurnInDay(false);
        if (m instanceof Raider r) {
            r.setCanJoinRaid(false);
            r.setPatrolLeader(false);
        }
        Material weapon = switch (e.getType()) {
            case SKELETON, STRAY, BOGGED -> Material.BOW;
            case PILLAGER -> Material.CROSSBOW;
            case VINDICATOR -> Material.IRON_AXE;
            case PIGLIN_BRUTE -> Material.GOLDEN_AXE;
            case WITHER_SKELETON -> Material.STONE_SWORD;
            default -> null;
        };
        if (champ != null) {
            if (champ.weapon() != null) weapon = champ.weapon();
            m.setCustomName("§4" + champ.name());
            m.setCustomNameVisible(true);
            m.setGlowing(true);
            AttributeInstance max = m.getAttribute(Attribute.GENERIC_MAX_HEALTH);
            if (max != null) {
                max.setBaseValue(champ.health());
                m.setHealth(champ.health());
            }
        }
        EntityEquipment eq = m.getEquipment();
        if (eq != null && weapon != null) {
            eq.setItemInMainHand(new ItemStack(weapon));
            eq.setItemInMainHandDropChance(0f);
        }
    }

    /** Monstres et soldats morts ou disparus (chunk déchargé) : ces derniers réapparaîtront du côté attaqué. */
    private void prune() {
        alive.values().removeIf(m -> {
            if (m.isDead()) return true;
            if (!m.isValid()) { pendingRespawn++; return true; }
            return false;
        });
        packOf.keySet().retainAll(alive.keySet());
        pendingRespawn += army.prune();
    }

    /** Assaillants disparus (chunk déchargé) : ils reviennent par le point d'entrée d'une escouade de la vague. */
    private void respawnPending() {
        for (int i = 0; i < 5 && pendingRespawn > 0; i++) {
            pendingRespawn--;
            spawnOne(squads.isEmpty() ? null : squads.get(ThreadLocalRandom.current().nextInt(squads.size())), false, false);
        }
    }

    /**
     * Cibles des monstres (chaque seconde), par meutes (escouades) : chacun choisit un habitant ou
     * un garde proche (32 blocs), de préférence près de sa meute, que pas plus de
     * sieges.escouades.max-par-cible monstres n'attaquent déjà au contact, et avance vers lui tant
     * qu'il n'est pas à portée (les pillards et les sorcières, qui ne se déplacent pas d'eux-mêmes
     * vers une cible lointaine, restaient plantés). Sans cible, le meneur marche vers l'objectif
     * de sa meute (par étapes au sol) et les autres tiennent leur place derrière lui.
     */
    private void retarget() {
        List<LivingEntity> defenders = inhabitants.defenders();
        Location fallback = hall != null ? hall : side != null ? side.rally() : null;
        long now = System.currentTimeMillis();
        // Un passage : monstres regroupés par meute, mêlées en cours recomptées
        Map<Squad, List<Mob>> packs = new LinkedHashMap<>();
        tally.clear();
        for (Mob m : alive.values()) {
            if (!m.isValid()) continue;
            Squad sq = packOf.get(m.getUniqueId());
            if (sq != null) packs.computeIfAbsent(sq, k -> new ArrayList<>()).add(m);
            LivingEntity t = m.getTarget();
            if (!ranged(m) && t != null && t.isValid() && !t.isDead()) tally.add(t);
        }
        Map<UUID, Integer> slots = new HashMap<>();
        for (Map.Entry<Squad, List<Mob>> e : packs.entrySet()) {
            Mob lead = null;
            for (Mob m : e.getValue()) if (leadership(m) > leadership(lead)) lead = m;
            int melee = 0, far = 0;
            for (Mob m : e.getValue()) slots.put(m.getUniqueId(), m == lead ? -1 : ranged(m) ? far++ : melee++);
            e.getKey().survey(e.getValue(), lead, b -> ((Mob) b).getTarget() == null);
        }
        int cap = config.maxPerTarget();
        for (Mob m : alive.values()) {
            if (!m.isValid()) continue;
            Squad sq = packOf.get(m.getUniqueId());
            int slot = slots.getOrDefault(m.getUniqueId(), -1);
            if (ranged(m) && !claims.isClaimed(m.getLocation())) {
                // Tireur hors de la ville (squelette, pillard…) : il y entre d'abord avec sa meute, comme
                // tous les assaillants, au lieu de tirer de loin depuis la lisière
                if (m.getTarget() != null) m.setTarget(null);
                advance(m, sq, slot, fallback, now);
                continue;
            }
            LivingEntity t = m.getTarget();
            boolean valid = t != null && t.isValid() && !t.isDead() && t.getWorld().equals(m.getWorld())
                    && t.getLocation().distanceSquared(m.getLocation()) < 32 * 32;
            boolean due = Math.floorMod(m.getUniqueId().hashCode() + ticks, 2) == 0; // choix revu toutes les 2 s
            boolean player = t instanceof Player p && !p.hasMetadata("NPC");          // un joueur qu'il a pris à partie : il le garde
            if (!valid || (!player && (due || (!ranged(m) && !tally.acceptable(t, cap))))) {
                LivingEntity pick = tally.pick(m.getLocation(), defenders, 32, ranged(m) ? 0 : cap, t,
                        sq != null ? sq.center() : null, 16);
                if (pick != t) {
                    if (!ranged(m)) tally.swap(t != null && t.isValid() && !t.isDead() ? t : null, pick);
                    m.setTarget(pick); // null : il rejoint sa meute (le jeu peut lui en redonner une)
                }
                t = pick;
            }
            if (t != null) {
                double reach = ranged(m) ? 10 : 2.5;
                if (m.getLocation().distance(t.getLocation()) > reach || !m.hasLineOfSight(t)) m.getPathfinder().moveTo(t, 1.0);
                continue;
            }
            advance(m, sq, slot, fallback, now);
        }
    }

    /** Aptitude d'un monstre à mener sa meute : le champion, sinon un combattant au contact, sinon un tireur. */
    private int leadership(Mob m) {
        if (m == null) return -1;
        return m.getUniqueId().equals(championId) ? 3 : ranged(m) ? 1 : 2;
    }

    /**
     * Monstre sans cible : le meneur (ou un monstre isolé) marche vers l'objectif de sa meute par
     * étapes au sol, en attendant un instant ses traînards ; les autres tiennent leur place dans
     * la formation (Squad.slot), sans relancer leur chemin tant que leur place bouge peu.
     */
    private void advance(Mob m, Squad sq, int slot, Location fallback, long now) {
        if (sq != null && sq.leader != null && sq.leader != m && sq.leader.isValid()) {
            Location place = sq.slot(Math.max(0, slot), ranged(m));
            if (place == null) place = sq.leader.getLocation();
            if (!place.getWorld().equals(m.getWorld())) return;
            double d2 = place.distanceSquared(m.getLocation());
            if (d2 <= 2.5 * 2.5) return;
            com.destroystokyo.paper.entity.Pathfinder.PathResult path = m.getPathfinder().getCurrentPath();
            Location end = path != null ? path.getFinalPoint() : null;
            if (end != null && end.getWorld().equals(place.getWorld()) && end.distanceSquared(place) < 3 * 3) return;
            m.getPathfinder().moveTo(place, d2 > 8 * 8 ? 1.2 : 1.0); // en retard : il presse le pas
            return;
        }
        if (sq != null && sq.size() > 1 && sq.shouldWait(now)) { m.getPathfinder().stopPathfinding(); return; }
        Location goal = sq != null ? sq.goal(m.getLocation()) : null;
        if (goal == null) goal = fallback;
        if (goal == null || !goal.getWorld().equals(m.getWorld())) return;
        Location step = stepToward(m.getLocation(), goal, 24);
        if (step != null && step.distanceSquared(m.getLocation()) > 9) {
            m.getPathfinder().moveTo(step, sq != null && sq.size() > 1 ? 0.85 : 1.0);
        }
    }

    /**
     * Escouades présentes (monstres ou soldats), pour la garde : membres vivants et centre de
     * chacune. Les assaillants sans escouade forment un groupe à part (numéro -1).
     */
    private List<SiegeFront.Band> bands() {
        Map<Squad, List<LivingEntity>> by = new LinkedHashMap<>();
        for (Mob m : alive.values()) {
            if (m.isValid() && !m.isDead()) by.computeIfAbsent(packOf.get(m.getUniqueId()), k -> new ArrayList<>()).add(m);
        }
        army.collect(by);
        List<SiegeFront.Band> out = new ArrayList<>(by.size());
        for (Map.Entry<Squad, List<LivingEntity>> e : by.entrySet()) {
            List<LivingEntity> members = e.getValue();
            double x = 0, y = 0, z = 0;
            for (LivingEntity b : members) {
                Location l = b.getLocation();
                x += l.getX();
                y += l.getY();
                z += l.getZ();
            }
            int n = members.size();
            out.add(new SiegeFront.Band(e.getKey() == null ? -1 : e.getKey().id,
                    new Location(members.get(0).getWorld(), x / n, y / n, z / n), members));
        }
        return out;
    }

    /** Monstre qui attaque à distance : il s'arrête à portée de tir. */
    private static boolean ranged(Mob m) {
        return m instanceof AbstractSkeleton || m instanceof Pillager || m instanceof Witch || m instanceof Evoker;
    }

    /**
     * Étape vers une destination : la destination si elle est à moins de max blocs, sinon un point
     * au sol à max blocs dans sa direction (chunk chargé) ; null si ce point n'est pas praticable.
     */
    static Location stepToward(Location from, Location goal, double max) {
        Vector to = goal.toVector().subtract(from.toVector());
        double dist = to.length();
        if (dist <= max) return goal;
        Location mid = from.clone().add(to.multiply(max / dist));
        World w = from.getWorld();
        if (!w.isChunkLoaded(mid.getBlockX() >> 4, mid.getBlockZ() >> 4)) return null;
        for (int r = 0; r <= 4; r += 2) {
            for (int[] d : new int[][]{{0, 0}, {r, 0}, {-r, 0}, {0, r}, {0, -r}}) {
                Location g = EnemyArmy.ground(w, mid.getBlockX() + d[0], mid.getBlockZ() + d[1]);
                if (g != null) return g;
            }
        }
        return null;
    }

    /* =========================
       CÔTÉ ATTAQUÉ ET FRONT
       ========================= */

    /**
     * Côté attaqué : un chunk de la lisière (proche des joueurs de préférence), un point hors de la
     * ville au-delà, chargé et au sol, et les postes de la garde le long de ce bord ; null si rien
     * ne convient pour l'instant.
     */
    private Side pickSide() {
        World w = cityWorld();
        if (w == null) return null;
        List<long[]> chunks = city.getClaimedChunkCoords(w.getName());
        if (chunks.isEmpty()) return null;
        Set<Long> claimed = new HashSet<>();
        double sx = 0, sz = 0;
        for (long[] c : chunks) {
            claimed.add(chunkKey((int) c[0], (int) c[1]));
            sx += c[0] * 16 + 8;
            sz += c[1] * 16 + 8;
        }
        double centerX = sx / chunks.size(), centerZ = sz / chunks.size();

        // Bords de la ville : chunk revendiqué + direction vers un voisin qui ne l'est pas
        List<int[]> edges = new ArrayList<>();
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (long[] c : chunks) {
            for (int[] d : dirs) {
                if (!claimed.contains(chunkKey((int) c[0] + d[0], (int) c[1] + d[1]))) {
                    edges.add(new int[]{(int) c[0], (int) c[1], d[0], d[1]});
                }
            }
        }
        Collections.shuffle(edges);
        // Les plus proches d'un joueur d'abord : le combat a lieu là où l'on joue
        Map<int[], Double> near = new HashMap<>();
        for (int[] e : edges) {
            double best = Double.MAX_VALUE;
            for (Player p : w.getPlayers()) {
                double dx = p.getLocation().getX() - (e[0] * 16 + 8), dz = p.getLocation().getZ() - (e[1] * 16 + 8);
                best = Math.min(best, dx * dx + dz * dz);
            }
            near.put(e, best);
        }
        edges.sort(Comparator.comparingDouble(near::get));
        int pool = Math.max(3, edges.size() / 3);
        List<int[]> candidates = new ArrayList<>(edges.subList(0, Math.min(pool, edges.size())));
        Collections.shuffle(candidates);
        candidates.addAll(edges); // repli : n'importe quel bord

        int out = 8 + config.spawnDistance();
        for (int[] e : candidates) {
            int bx = e[0] * 16 + 8 + e[2] * out, bz = e[1] * 16 + 8 + e[3] * out;
            if (!w.isChunkLoaded(bx >> 4, bz >> 4) || claimed.contains(chunkKey(bx >> 4, bz >> 4))) continue;
            Location spawn = spotNear(new Location(w, bx, 0, bz), 4);
            if (spawn == null || claimed.contains(chunkKey(spawn.getBlockX() >> 4, spawn.getBlockZ() >> 4))) continue;
            Location rally = EnemyArmy.ground(w, e[0] * 16 + 8, e[1] * 16 + 8);
            if (rally == null) rally = new Location(w, e[0] * 16 + 8.5, w.getHighestBlockYAt(e[0] * 16 + 8, e[1] * 16 + 8) + 1, e[1] * 16 + 8.5);
            List<Location> entries = entryPoints(w, claimed, new Location(w, bx + 0.5, spawn.getY(), bz + 0.5), e[2], e[3], spawn);
            SiegeFront front = buildFront(w, claimed, e[0], e[1], e[2], e[3], rally, spawn, entries);
            return new Side(spawn, rally, compass(spawn.getX() - centerX, spawn.getZ() - centerZ), front);
        }
        return null;
    }

    /**
     * Points d'entrée des assaillants : sieges.points-entree points répartis sur sieges.largeur-front
     * blocs le long de la lisière attaquée, de part et d'autre de center (hors de la ville).
     * Chacun au sol (GroundFinder : ni eau, ni lave, ni cime d'arbre), dans un chunk chargé et non
     * revendiqué : un point qui tombe dans la ville (lisière en escalier) est repoussé vers
     * l'extérieur, un point introuvable est sauté. De gauche à droite le long du front ; au moins
     * fallback s'il n'y en a aucun.
     */
    private List<Location> entryPoints(World w, Set<Long> claimed, Location center, int dx, int dz, Location fallback) {
        int n = config.entryPoints();
        double width = config.frontWidth();
        int px = dz != 0 ? 1 : 0, pz = dx != 0 ? 1 : 0; // la largeur longe la lisière
        List<Location> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            double t = n == 1 ? 0 : -width / 2 + width * i / (n - 1);
            Location found = null;
            for (int push = 0; push <= 16 && found == null; push += 4) {
                int x = (int) Math.floor(center.getX() + px * t + dx * push);
                int z = (int) Math.floor(center.getZ() + pz * t + dz * push);
                if (!w.isChunkLoaded(x >> 4, z >> 4) || claimed.contains(chunkKey(x >> 4, z >> 4))) continue;
                found = spotNear(new Location(w, x, 0, z), 3);
            }
            if (found == null || claimed.contains(chunkKey(found.getBlockX() >> 4, found.getBlockZ() >> 4))) continue;
            boolean close = false;
            for (Location o : out) close |= o.distanceSquared(found) < 5 * 5;
            if (!close) out.add(found);
        }
        if (out.isEmpty()) out.add(fallback);
        return out;
    }

    /**
     * Postes de la garde le long du bord attaqué (ce chunk et ses voisins de lisière de part et
     * d'autre, assez pour couvrir sieges.largeur-front) : au contact à 2 et 4 blocs de la lisière,
     * archers à 7 et 9 blocs, un poste tous les 2 blocs, au sol, hors des bâtiments ; chaque rangée
     * du centre vers les ailes.
     */
    private SiegeFront buildFront(World w, Set<Long> claimed, int cx, int cz, int dx, int dz, Location rally, Location enemy,
                                  List<Location> entries) {
        int px = dz != 0 ? 1 : 0, pz = dx != 0 ? 1 : 0; // la ligne longe la lisière
        int reach = Math.max(1, (int) Math.ceil((config.frontWidth() / 2.0 - 8) / 16.0));
        List<int[]> line = new ArrayList<>();
        line.add(new int[]{cx, cz});
        for (int sgn : new int[]{-1, 1}) {
            for (int k = 1; k <= reach; k++) { // chunks de lisière contigus, tournés du même côté
                int nx = cx + sgn * k * px, nz = cz + sgn * k * pz;
                if (!claimed.contains(chunkKey(nx, nz)) || claimed.contains(chunkKey(nx + dx, nz + dz))) break;
                line.add(new int[]{nx, nz});
            }
        }
        List<Location> melee = new ArrayList<>(row(w, line, dx, dz, 2, rally));
        melee.addAll(row(w, line, dx, dz, 4, rally));
        List<Location> ranged = new ArrayList<>(row(w, line, dx, dz, 7, rally));
        ranged.addAll(row(w, line, dx, dz, 9, rally));
        return new SiegeFront(rally, enemy, List.copyOf(melee), List.copyOf(ranged), new Vector(px, 0, pz), List.copyOf(entries));
    }

    /** Une rangée de postes à depth blocs de la lisière, du centre (rally) vers les ailes. */
    private List<Location> row(World w, List<int[]> line, int dx, int dz, int depth, Location center) {
        List<Location> out = new ArrayList<>();
        for (int[] c : line) {
            for (int i = 1; i < 16; i += 2) {
                int x, z;
                if (dx != 0) {
                    int border = dx > 0 ? c[0] * 16 + 15 : c[0] * 16;
                    x = border - dx * depth;
                    z = c[1] * 16 + i;
                } else {
                    int border = dz > 0 ? c[1] * 16 + 15 : c[1] * 16;
                    z = border - dz * depth;
                    x = c[0] * 16 + i;
                }
                if (!w.isChunkLoaded(x >> 4, z >> 4) || insideBuilding(w.getName(), x, z)) continue;
                Location g = EnemyArmy.ground(w, x, z);
                if (g != null) out.add(g);
            }
        }
        out.sort(Comparator.comparingDouble(l -> l.distanceSquared(center)));
        return out;
    }

    /** Point (x, z) dans la zone d'un bâtiment (liste relue au plus toutes les 5 s : c'est une requête en base). */
    private boolean insideBuilding(String world, int x, int z) {
        long now = System.currentTimeMillis();
        if (now - buildingsReadAt > 5000) {
            buildingList = buildings.getAllBuildings();
            buildingsReadAt = now;
        }
        for (Building b : buildingList) {
            if (b.world().equals(world) && x >= b.x1() && x <= b.x2() && z >= b.z1() && z <= b.z2()) return true;
        }
        return false;
    }

    /**
     * Point au sol sûr (GroundFinder : ni eau, ni lave, ni cime d'arbre), hors de la ville, à moins
     * de radius blocs de center (chunks chargés) ; null si rien ne convient en quelques essais.
     */
    private Location spotNear(Location center, int radius) {
        World w = center.getWorld();
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (int attempt = 0; attempt < 10; attempt++) {
            int x = center.getBlockX() + (attempt == 0 ? 0 : rnd.nextInt(-radius, radius + 1));
            int z = center.getBlockZ() + (attempt == 0 ? 0 : rnd.nextInt(-radius, radius + 1));
            if (!w.isChunkLoaded(x >> 4, z >> 4) || claims.isClaimed(w, x >> 4, z >> 4)) continue;
            Location l = GroundFinder.groundAt(w, x, z);
            if (l != null) return l;
        }
        return null;
    }

    /** « le nord », « le sud-est »… (nord = Z négatif, est = X positif). */
    private static String compass(double dx, double dz) {
        double angle = Math.toDegrees(Math.atan2(-dz, dx)); // 0 = est, 90 = nord
        String[] names = {"l'est", "le nord-est", "le nord", "le nord-ouest", "l'ouest", "le sud-ouest", "le sud", "le sud-est"};
        int sector = (int) Math.floorMod(Math.round(angle / 45.0), 8);
        return names[sector];
    }

    /** Monde de la ville : celui de ses chunks revendiqués. */
    private World cityWorld() {
        for (World w : Bukkit.getWorlds()) {
            if (!city.getClaimedChunkCoords(w.getName()).isEmpty()) return w;
        }
        return null;
    }

    /** Centre de la mairie (au sol), ou null s'il n'y en a pas. */
    private Location townHall() {
        for (Building b : buildings.getAllBuildings()) {
            if (b.type() != BuildingType.MAIRIE) continue;
            World w = Bukkit.getWorld(b.world());
            if (w == null) continue;
            int x = (b.x1() + b.x2()) / 2, z = (b.z1() + b.z2()) / 2;
            double y = b.refY() != null ? b.refY() + 1 : w.getHighestBlockYAt(x, z) + 1;
            return new Location(w, x + 0.5, y, z + 0.5);
        }
        return null;
    }

    /* =========================
       BARRE ET ÉTAT
       ========================= */

    private void updateBar(long now) {
        if (bar == null) return;
        String clock = clock(deadline - now);
        String from = side != null ? "par " + side.direction() : "";
        String title = kind == SiegeConfig.Kind.BANNIERE && faction != null
                ? faction.color() + capitalize(faction.name()) : "§cSiège de Valmont";
        switch (phase) {
            case PREPARING -> {
                bar.setTitle("§c⚔ " + title + " §7— arrive " + from + " dans §f" + clock(phaseEndsAt - now)
                        + " §7· la garde prend position");
                bar.setProgress(fraction(phaseEndsAt - now, config.prepSeconds() * 1000L));
            }
            case PAUSE -> {
                bar.setTitle("§a⚔ Vague " + wave + "/" + totalWaves + " repoussée §7— la suivante arrive " + from
                        + " dans §f" + clock(phaseEndsAt - now));
                bar.setProgress(fraction(phaseEndsAt - now, config.pauseSeconds() * 1000L));
            }
            case WAVE -> {
                int left = left();
                String units = kind == SiegeConfig.Kind.BANNIERE ? " soldat" : " assaillant";
                bar.setTitle("§c⚔ " + title + " §7· vague " + wave + "/" + totalWaves + " · §f" + left + units + (left > 1 ? "s" : "")
                        + " §7· " + from + (championAlive ? " §7· §4" + championName : "") + " §7· §f" + clock + " §7restantes");
                bar.setProgress(fraction(left, Math.max(1, waveTotal)));
            }
            default -> { }
        }
    }

    private static double fraction(long part, long whole) {
        return Math.max(0, Math.min(1, (double) part / Math.max(1, whole)));
    }

    private static String clock(long ms) {
        long s = Math.max(0, ms) / 1000;
        return String.format("%d:%02d", s / 60, s % 60);
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return "";
        int i = 0;
        while (i + 1 < s.length() && s.charAt(i) == '§') i += 2; // codes de couleur en tête
        return i < s.length() ? s.substring(0, i) + Character.toUpperCase(s.charAt(i)) + s.substring(i + 1) : s;
    }

    private static String plain(String s) {
        return s == null ? "" : s.replaceAll("§.", "");
    }

    /** « Chapitre IV » d'après le palier. */
    public static String chapter(int level) {
        String c = CityTier.fromLevel(level).chapter;
        int dash = c.indexOf(" —");
        return dash > 0 ? c.substring(0, dash) : c;
    }

    /** État du siège pour /city siege. */
    public List<String> status() {
        List<String> lines = new ArrayList<>();
        if (!running()) {
            String why = readiness();
            lines.add("§7⚔ Aucun siège en cours." + (why != null ? " §8(" + why + ")" : ""));
            lines.add("§8Prochains sièges : /city event list");
            return lines;
        }
        long now = System.currentTimeMillis();
        lines.add("§c⚔ Siège en cours §7— " + plain(enemyName()) + ", " + chapter(tier) + ", vague " + Math.max(wave, 1) + "/"
                + totalWaves + (side != null ? ", par " + side.direction() : "") + ", " + clock(deadline - now) + " restantes");
        lines.add("§7Assaillants : §f" + left() + (phase == Phase.WAVE ? " §7/ §f" + waveTotal : "")
                + " §7; abattus par la garde : §f" + guardKills + " §7; pertes : §f" + civiliansLost + " habitant(s), "
                + guardsLost + " garde(s)");
        if (side != null) {
            lines.add("§7Front : §f" + side.front().entries().size() + " §7point(s) d'entrée sur §f" + config.frontWidth()
                    + " §7blocs ; §f" + side.front().melee().size() + " §7postes au contact, §f"
                    + side.front().ranged().size() + " §7postes d'archers");
        }
        if (phase == Phase.WAVE && !squads.isEmpty()) {
            lines.add("§7Escouades : §f" + squads.size() + (arrivals.isEmpty() ? "" : " §7; encore en route : §f" + arrivals.size()));
        }
        return lines;
    }

    /* =========================
       ÉVÉNEMENTS
       ========================= */

    /** Monstre assaillant abattu : compté pour le bilan (joueur ou garde) ; annonce du champion. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(EntityDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (!isAttacker(dead)) return;
        alive.remove(dead.getUniqueId());
        Player killer = dead.getKiller();
        credit(killer);
        if (dead.getUniqueId().equals(championId)) championFell(killer);
    }

    /** Soldat ennemi abattu (armée « bannière »). */
    private void armyKill(Player killer, boolean champion, boolean standard) {
        if (!running()) return;
        credit(killer);
        if (champion) championFell(killer);
        if (standard && faction != null) {
            Bukkit.broadcastMessage("§6⚔ La bannière de " + faction.color() + faction.name() + " §6est tombée"
                    + (killer != null && !killer.hasMetadata("NPC") ? " §7(" + killer.getName() + ")" : "") + "§6 !");
            SiegeSounds.standardFalls();
        }
    }

    private void credit(Player killer) {
        if (killer == null) return;
        if (killer.hasMetadata("NPC")) guardKills++;
        else playerKills.merge(killer.getName(), 1, Integer::sum);
    }

    private void championFell(Player killer) {
        championAlive = false;
        Bukkit.broadcastMessage("§6⚔ §4" + championName + " §6est tombé" + (killer != null && !killer.hasMetadata("NPC")
                ? " sous les coups de §f" + killer.getName() : "") + "§6 !");
    }

    /** Zombie noyé, squelette gelé… : la nouvelle créature reste un assaillant. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTransform(EntityTransformEvent event) {
        if (!isAttacker(event.getEntity())) return;
        alive.remove(event.getEntity().getUniqueId());
        Squad pack = packOf.remove(event.getEntity().getUniqueId()); // il reste dans sa meute
        for (Entity e : event.getTransformedEntities()) {
            e.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
            e.setPersistent(false);
            if (running() && e instanceof Mob m) {
                alive.put(m.getUniqueId(), m);
                if (pack != null) packOf.put(m.getUniqueId(), pack);
            }
        }
    }

    /** Assaillant d'un ancien siège rechargé avec son chunk : retiré. */
    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        for (Entity e : event.getEntities()) {
            if (isAttacker(e) && !alive.containsKey(e.getUniqueId())) e.remove();
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (bar != null) bar.addPlayer(event.getPlayer());
    }

    private static long chunkKey(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }
}
