package com.citycore.siege;

import com.citycore.building.Building;
import com.citycore.building.BuildingManager;
import com.citycore.building.BuildingType;
import com.citycore.city.CityManager;
import com.citycore.economy.MarketService;
import com.citycore.inhabitant.InhabitantManager;
import com.citycore.inhabitant.SiegeFront;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
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
    private BukkitTask task;
    private final Map<UUID, Mob> alive = new LinkedHashMap<>();
    private final Map<String, Integer> playerKills = new HashMap<>();
    private int guardKills, civiliansLost, guardsLost;

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
        // Vague en cours : la garde sort de la ville combattre les assaillants
        inhabitants.setSiegeAssault(() -> phase == Phase.WAVE, this::attackerBodies);
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

    /** Assaillants encore à abattre dans la vague (monstres, soldats, et ceux à faire réapparaître). */
    private int left() {
        return alive.size() + army.alive() + pendingRespawn;
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
        for (int i = 0; i < count; i++) spawnOne(false, kind == SiegeConfig.Kind.BANNIERE && i == 0);
        if (last) spawnOne(true, false);
        waveTotal = alive.size() + army.alive();
        phase = Phase.WAVE;
        String units = kind == SiegeConfig.Kind.BANNIERE ? "soldats" : "assaillants";
        Bukkit.broadcastMessage("§c⚔ Vague " + wave + "/" + totalWaves + " : §f" + waveTotal + " " + units + " §carrivent par "
                + side.direction() + " !" + (last && championAlive ? " §4" + championName + " §cles mène." : ""));
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendTitle("", "§cVague " + wave + "/" + totalWaves + " §7— par " + side.direction(), 5, 40, 10);
        }
        lastCountdown = -1;
    }

    /** Un assaillant du type du siège ; champion : celui de la dernière vague (s'il y en a un à ce palier). */
    private void spawnOne(boolean champion, boolean standard) {
        if (side == null) return;
        Location at = spotNear(side.spawn(), 6);
        if (at == null) at = side.spawn();
        if (kind == SiegeConfig.Kind.BANNIERE) {
            SiegeConfig.Unit unit = champion ? config.armyChampion(tier) : config.randomUnit(tier);
            if (unit == null) return;
            if (army.spawn(unit, at, faction, champion, standard) != null && champion) {
                championName = unit.name();
                championAlive = true;
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
        if (champ != null) {
            championId = m.getUniqueId();
            championName = champ.name();
            championAlive = true;
        }
    }

    private void waveCleared(long now) {
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
        for (Mob m : alive.values()) if (m.isValid()) m.remove();
        alive.clear();
        army.clear();
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
        if (result == Result.VICTORY || result == Result.DEFEAT) summary();
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
        pendingRespawn += army.prune();
    }

    private void respawnPending() {
        for (int i = 0; i < 5 && pendingRespawn > 0; i++) {
            pendingRespawn--;
            spawnOne(false, false);
        }
    }

    /**
     * Cibles des monstres (toutes les 2 s) : l'habitant ou le garde le plus proche (48 blocs), et
     * ils avancent vers lui tant qu'ils ne sont pas à portée (les pillards et les sorcières, qui
     * ne se déplacent pas d'eux-mêmes vers une cible lointaine, restaient plantés) ; sans cible,
     * ils marchent sur la mairie (ou le côté attaqué) par étapes au sol.
     */
    private void retarget() {
        List<LivingEntity> defenders = inhabitants.defenders();
        Location goal = hall != null ? hall : side != null ? side.rally() : null;
        for (Mob m : alive.values()) {
            if (ranged(m) && !claims.isClaimed(m.getLocation())) {
                // Tireur hors de la ville (squelette, pillard…) : il y entre d'abord, comme tous les
                // assaillants, au lieu de tirer de loin depuis la lisière
                if (m.getTarget() != null) m.setTarget(null);
                if (goal == null || !goal.getWorld().equals(m.getWorld())) continue;
                Location step = stepToward(m.getLocation(), goal, 24);
                if (step != null) m.getPathfinder().moveTo(step, 1.0);
                continue;
            }
            LivingEntity t = m.getTarget();
            boolean keep = t != null && t.isValid() && !t.isDead() && t.getWorld().equals(m.getWorld())
                    && t.getLocation().distanceSquared(m.getLocation()) < 48 * 48;
            if (!keep) {
                t = null;
                double bestDist = 48 * 48;
                for (LivingEntity d : defenders) {
                    if (!d.getWorld().equals(m.getWorld())) continue;
                    double dist = d.getLocation().distanceSquared(m.getLocation());
                    if (dist < bestDist) { bestDist = dist; t = d; }
                }
                if (t != null) m.setTarget(t);
            }
            if (t != null) {
                double reach = ranged(m) ? 10 : 2.5;
                if (m.getLocation().distance(t.getLocation()) > reach || !m.hasLineOfSight(t)) m.getPathfinder().moveTo(t, 1.0);
                continue;
            }
            if (goal == null || !goal.getWorld().equals(m.getWorld())) continue;
            Location step = stepToward(m.getLocation(), goal, 24);
            if (step != null && step.distanceSquared(m.getLocation()) > 9) m.getPathfinder().moveTo(step, 1.0);
        }
    }

    /** Assaillants présents (monstres et soldats) : cibles de la garde pendant l'assaut. */
    private List<LivingEntity> attackerBodies() {
        List<LivingEntity> list = new ArrayList<>(alive.values());
        list.addAll(army.bodies());
        return list;
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
            SiegeFront front = buildFront(w, claimed, e[0], e[1], e[2], e[3], rally, spawn);
            return new Side(spawn, rally, compass(spawn.getX() - centerX, spawn.getZ() - centerZ), front);
        }
        return null;
    }

    /**
     * Postes de la garde le long du bord attaqué (ce chunk et ses voisins de lisière, un de chaque
     * côté) : au contact à 2 et 4 blocs de la lisière, archers à 7 et 9 blocs, un poste tous les 2
     * blocs, au sol, hors des bâtiments ; chaque rangée du centre vers les ailes.
     */
    private SiegeFront buildFront(World w, Set<Long> claimed, int cx, int cz, int dx, int dz, Location rally, Location enemy) {
        int px = dz != 0 ? 1 : 0, pz = dx != 0 ? 1 : 0; // la ligne longe la lisière
        List<int[]> line = new ArrayList<>();
        line.add(new int[]{cx, cz});
        for (int sgn : new int[]{-1, 1}) {
            int nx = cx + sgn * px, nz = cz + sgn * pz;
            if (claimed.contains(chunkKey(nx, nz)) && !claimed.contains(chunkKey(nx + dx, nz + dz))) line.add(new int[]{nx, nz});
        }
        List<Location> melee = new ArrayList<>(row(w, line, dx, dz, 2, rally));
        melee.addAll(row(w, line, dx, dz, 4, rally));
        List<Location> ranged = new ArrayList<>(row(w, line, dx, dz, 7, rally));
        ranged.addAll(row(w, line, dx, dz, 9, rally));
        return new SiegeFront(rally, enemy, List.copyOf(melee), List.copyOf(ranged));
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

    private boolean insideBuilding(String world, int x, int z) {
        for (Building b : buildings.getAllBuildings()) {
            if (b.world().equals(world) && x >= b.x1() && x <= b.x2() && z >= b.z1() && z <= b.z2()) return true;
        }
        return false;
    }

    /** Point au sol, hors de l'eau et de la lave, à moins de radius blocs de center (chunks chargés). */
    private static Location spotNear(Location center, int radius) {
        World w = center.getWorld();
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (int attempt = 0; attempt < 10; attempt++) {
            int x = center.getBlockX() + (attempt == 0 ? 0 : rnd.nextInt(-radius, radius + 1));
            int z = center.getBlockZ() + (attempt == 0 ? 0 : rnd.nextInt(-radius, radius + 1));
            if (!w.isChunkLoaded(x >> 4, z >> 4)) continue;
            Location l = EnemyArmy.ground(w, x, z);
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
            lines.add("§7Front : §f" + side.front().melee().size() + " §7postes au contact, §f"
                    + side.front().ranged().size() + " §7postes d'archers");
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
        for (Entity e : event.getTransformedEntities()) {
            e.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
            e.setPersistent(false);
            if (running() && e instanceof Mob m) alive.put(m.getUniqueId(), m);
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
