package com.citycore.siege;

import com.citycore.inhabitant.InhabitantManager;
import com.citycore.inhabitant.InhabitantSkins;
import com.citycore.inhabitant.NpcCombat;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import com.citycore.npc.SafeNavigation;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.ai.EntityTarget;
import net.citizensnpcs.api.event.NPCDeathEvent;
import net.citizensnpcs.api.npc.MemoryNPCDataStore;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.npc.NPCRegistry;
import net.citizensnpcs.api.trait.trait.Equipment;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Armée ennemie d'un siège « bannière » (phase 4) : des soldats PNJ (comme la garde) d'une
 * faction rivale, plus ou moins nombreux et puissants selon le palier. Ils s'en prennent aux
 * habitants et aux gardes (sinon aux joueurs proches), au contact ou à l'arc ; faute de cible, ils
 * marchent sur la mairie. Ni butin ni message de mort ; jamais enregistrés (registre en mémoire).
 * Un porte-étendard ouvre chaque vague, la bannière de sa faction sur la tête.
 */
public final class EnemyArmy implements Listener {

    /** Un soldat ennemi abattu : par qui (null = autre cause), champion ou porte-étendard. */
    public interface KillListener {
        void killed(Player killer, boolean champion, boolean standard);
    }

    private static final class Soldier {
        final NPC npc;
        final SiegeConfig.Unit unit;
        final boolean champion, standard;
        LivingEntity target;
        long lastPick, lastShot;

        long lastSwing;

        Soldier(NPC npc, SiegeConfig.Unit unit, boolean champion, boolean standard) {
            this.npc = npc;
            this.unit = unit;
            this.champion = champion;
            this.standard = standard;
        }
    }

    private static final long SHOT_COOLDOWN_MS = 1600;
    private static final double BOW_RANGE = 16;

    private final JavaPlugin        plugin;
    private final InhabitantManager inhabitants;
    private final SiegeConfig       config;
    private final InhabitantSkins   skins;
    private final java.util.function.Predicate<Location> inCity; // chunk revendiqué par la ville
    private NPCRegistry registry;
    private final Map<Integer, Soldier> soldiers = new LinkedHashMap<>();
    private Supplier<Location> objective = () -> null; // mairie, sinon côté attaqué
    private KillListener killListener;
    private int ticks;

    public EnemyArmy(JavaPlugin plugin, InhabitantManager inhabitants, SiegeConfig config,
                     java.util.function.Predicate<Location> inCity) {
        this.plugin      = plugin;
        this.inhabitants = inhabitants;
        this.config      = config;
        this.inCity      = inCity;
        this.skins       = new InhabitantSkins(plugin, "sieges.banniere.skins");
    }

    /** Registre des soldats (en mémoire) et comportement deux fois par seconde. */
    public void start() {
        registry = CitizensAPI.createAnonymousNPCRegistry(new MemoryNPCDataStore());
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 10L);
    }

    public void setObjective(Supplier<Location> objective) {
        this.objective = objective;
    }

    public void setKillListener(KillListener listener) {
        this.killListener = listener;
    }

    /**
     * Soldat de cette armée ennemie. Le isNPC de Citizens répond oui pour n'importe quel PNJ
     * (habitant, garde, artisan…) : on vérifie que le PNJ appartient bien à ce registre.
     */
    public boolean isSoldier(Entity e) {
        if (registry == null || e == null) return false;
        NPC npc = registry.getNPC(e);
        return npc != null && npc.getOwningRegistry() == registry;
    }

    public int alive() {
        return soldiers.size();
    }

    /** Corps des soldats présents (cibles de la garde pendant l'assaut). */
    public List<LivingEntity> bodies() {
        List<LivingEntity> list = new ArrayList<>();
        for (Soldier s : soldiers.values()) {
            if (s.npc.isSpawned() && s.npc.getEntity() instanceof LivingEntity body) list.add(body);
        }
        return list;
    }

    /** Distance (au carré) entre at et le soldat le plus proche ; MAX_VALUE s'il n'y en a pas. */
    public double nearestSq(Location at) {
        double best = Double.MAX_VALUE;
        for (Soldier s : soldiers.values()) {
            if (!s.npc.isSpawned() || !s.npc.getEntity().getWorld().equals(at.getWorld())) continue;
            best = Math.min(best, s.npc.getEntity().getLocation().distanceSquared(at));
        }
        return best;
    }

    /** Relit les uniformes ennemis (sieges.banniere.skins). */
    public void reloadSkins() {
        skins.reload();
    }

    /* =========================
       RECRUTEMENT, RETRAIT
       ========================= */

    /** Fait apparaître un soldat ; standard = porte-étendard (bannière de la faction sur la tête). */
    public NPC spawn(SiegeConfig.Unit unit, Location at, SiegeConfig.Faction faction, boolean champion, boolean standard) {
        if (registry == null || unit == null || at == null) return null;
        String color = champion ? "§4" : faction.color();
        NPC npc = registry.createNPC(EntityType.PLAYER, color + unit.name());
        npc.setProtected(false);                                  // joueurs et gardes peuvent le blesser
        npc.data().set(NPC.Metadata.TARGETABLE, true);
        npc.data().set(NPC.Metadata.DROPS_ITEMS, false);
        npc.data().set(NPC.Metadata.PATHFINDER_OPEN_DOORS, false); // une porte fermée protège les habitants
        if (champion) npc.data().set(NPC.Metadata.GLOWING, true);
        skins.apply(npc, skins.isEmpty() ? -1 : skins.nextIndex());
        Equipment eq = npc.getOrAddTrait(Equipment.class);
        eq.set(Equipment.EquipmentSlot.HAND, item(unit.weapon()));
        eq.set(Equipment.EquipmentSlot.HELMET, standard ? new ItemStack(faction.banner()) : item(unit.helmet()));
        eq.set(Equipment.EquipmentSlot.CHESTPLATE, item(unit.chest()));
        eq.set(Equipment.EquipmentSlot.LEGGINGS, item(unit.legs()));
        eq.set(Equipment.EquipmentSlot.BOOTS, item(unit.boots()));
        if (!npc.spawn(at) || !(npc.getEntity() instanceof LivingEntity body)) {
            npc.destroy();
            return null;
        }
        AttributeInstance max = body.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (max != null) {
            max.setBaseValue(unit.health());
            body.setHealth(unit.health());
        }
        npc.getNavigator().getDefaultParameters()
                .speedModifier(config.armySpeed())
                .range(48)
                .stuckAction((n, nav) -> false); // bloqué : il cherche un autre chemin (jamais téléporté chez l'habitant)
        soldiers.put(npc.getId(), new Soldier(npc, unit, champion, standard));
        return npc;
    }

    private static ItemStack item(Material m) {
        return new ItemStack(m == null ? Material.AIR : m);
    }

    /** Soldats disparus sans mourir (chunk déchargé) : retirés ; renvoie leur nombre (à faire réapparaître). */
    public int prune() {
        int vanished = 0;
        for (Soldier s : new ArrayList<>(soldiers.values())) {
            if (s.npc.isSpawned()) continue;
            soldiers.remove(s.npc.getId());
            s.npc.destroy();
            vanished++;
        }
        return vanished;
    }

    /** Fin du siège : tous les soldats sont retirés. */
    public void clear() {
        for (Soldier s : soldiers.values()) s.npc.destroy();
        soldiers.clear();
    }

    /* =========================
       COMPORTEMENT (deux fois par seconde)
       ========================= */

    private void tick() {
        if (soldiers.isEmpty()) return;
        ticks++;
        long now = System.currentTimeMillis();
        List<LivingEntity> defenders = inhabitants.defenders();
        for (Soldier s : new ArrayList<>(soldiers.values())) {
            if (!s.npc.isSpawned() || !(s.npc.getEntity() instanceof LivingEntity body)) continue;
            NpcCombat.sanitize(body); // vitesse invalide : remise à zéro avant l'envoi aux joueurs
            // Riposte : celui qui vient de le frapper (garde ou joueur, à moins de 16 blocs) devient sa cible
            Player attacker = body.getKiller();
            if (attacker != null && attacker != s.target && valid(attacker, body)
                    && attacker.getLocation().distanceSquared(body.getLocation()) < 16 * 16 && !isSoldier(attacker)) {
                s.target = attacker;
                s.lastPick = now;
            } else if (!valid(s.target, body) || now - s.lastPick > 2000) {
                s.target = pickTarget(body, defenders);
                s.lastPick = now;
            }
            if (s.target == null) { march(s, body); continue; }
            if (s.unit.ranged()) {
                double dist = body.getLocation().distance(s.target.getLocation());
                if (!inCity.test(body.getLocation())) {
                    // Hors de la ville : il y entre d'abord, comme tous les assaillants (pas de tir de loin
                    // depuis la lisière) ; en marchant, il ne tire que sur une cible proche
                    march(s, body);
                    if (dist <= 10 && body.hasLineOfSight(s.target)) shoot(s, body, now);
                    continue;
                }
                if (dist <= BOW_RANGE && body.hasLineOfSight(s.target)) {
                    if (s.npc.getNavigator().isNavigating()) s.npc.getNavigator().cancelNavigation();
                    s.npc.faceLocation(s.target.getEyeLocation());
                    shoot(s, body, now);
                } else {
                    follow(s, false);
                }
            } else {
                // Au contact : il suit sa cible et frappe à portée. Les coups sont portés ici : ses
                // cibles sont des « joueurs » pour Minecraft (habitants, gardes, joueurs), et le
                // contrôle PvP annulerait une attaque ordinaire
                follow(s, false);
                if (body.getLocation().distanceSquared(s.target.getLocation()) <= NpcCombat.REACH * NpcCombat.REACH
                        && now - s.lastSwing >= (s.champion ? 800 : 1000)) {
                    s.lastSwing = now;
                    s.npc.faceLocation(s.target.getEyeLocation());
                    com.citycore.inhabitant.NpcGestures.attack(body);
                    NpcCombat.strike(body, s.target, NpcCombat.meleeDamage(body));
                }
            }
        }
    }

    /** Suit sa cible (aggressive = frappe au contact). */
    private void follow(Soldier s, boolean aggressive) {
        EntityTarget current = s.npc.getNavigator().getEntityTarget();
        if (current != null && current.getTarget() == s.target && current.isAggressive() == aggressive) return;
        s.npc.getNavigator().setTarget(s.target, aggressive);
        s.npc.getNavigator().getLocalParameters().speedModifier(config.armySpeed() * 1.15f);
    }

    /** Cible : l'habitant ou le garde le plus proche (48 blocs), sinon un joueur proche (24 blocs). */
    private LivingEntity pickTarget(LivingEntity body, List<LivingEntity> defenders) {
        LivingEntity best = null;
        double bestDist = 48 * 48;
        for (LivingEntity d : defenders) {
            if (!d.getWorld().equals(body.getWorld())) continue;
            double dist = d.getLocation().distanceSquared(body.getLocation());
            if (dist < bestDist) { bestDist = dist; best = d; }
        }
        if (best != null) return best;
        bestDist = 24 * 24;
        for (Player p : body.getWorld().getPlayers()) {
            if (p.hasMetadata("NPC") || !fighting(p)) continue;
            double dist = p.getLocation().distanceSquared(body.getLocation());
            if (dist < bestDist) { bestDist = dist; best = p; }
        }
        return best;
    }

    private static boolean fighting(Player p) {
        return p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE;
    }

    private static boolean valid(LivingEntity t, LivingEntity from) {
        if (t == null || !t.isValid() || t.isDead() || !t.getWorld().equals(from.getWorld())) return false;
        if (t instanceof Player p && !p.hasMetadata("NPC") && !fighting(p)) return false;
        return t.getLocation().distanceSquared(from.getLocation()) < 56 * 56;
    }

    /** Pas de cible : en marche vers la mairie (ou le côté attaqué), par étapes au sol. */
    private void march(Soldier s, LivingEntity body) {
        if (s.npc.getNavigator().isNavigating() && s.npc.getNavigator().getEntityTarget() == null) return;
        Location goal = objective.get();
        if (goal == null || !goal.getWorld().equals(body.getWorld())) return;
        Location step = SiegeManager.stepToward(body.getLocation(), goal, 28);
        if (step != null && step.distanceSquared(body.getLocation()) > 9) {
            SafeNavigation.walkTo(s.npc, step, config.armySpeed(), false);
        }
    }

    /** Une flèche vers la cible (légère courbe), non ramassable, retirée ensuite. */
    private void shoot(Soldier s, LivingEntity body, long now) {
        if (now - s.lastShot < SHOT_COOLDOWN_MS) return;
        s.lastShot = now;
        Vector dir = s.target.getEyeLocation().toVector().subtract(body.getEyeLocation().toVector());
        double dist = dir.length();
        dir.normalize().multiply(1.9).add(new Vector(0, dist * 0.012, 0));
        Arrow arrow = body.launchProjectile(Arrow.class, dir);
        arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
        com.citycore.inhabitant.NpcGestures.shot(body);
        body.getWorld().playSound(body.getLocation(), s.unit.weapon() == Material.CROSSBOW
                ? Sound.ITEM_CROSSBOW_SHOOT : Sound.ENTITY_ARROW_SHOOT, 0.8f, 0.9f);
        Bukkit.getScheduler().runTaskLater(plugin, () -> { if (arrow.isValid()) arrow.remove(); }, 60L);
    }

    /* =========================
       ÉVÉNEMENTS
       ========================= */

    /**
     * Flèche d'un soldat ennemi : sur un autre soldat, sans effet ; sur un « joueur » (habitant,
     * garde, joueur), la blessure est appliquée ici (sinon annulée par le contrôle PvP).
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onArrowHit(ProjectileHitEvent event) {
        if (!(event.getEntity().getShooter() instanceof LivingEntity shooter) || !isSoldier(shooter)) return;
        if (!(event.getHitEntity() instanceof LivingEntity victim)) return;
        if (isSoldier(victim)) {
            event.setCancelled(true);
            event.getEntity().remove();
        } else if (victim instanceof Player) {
            event.setCancelled(true);
            event.getEntity().remove();
            NpcCombat.strike(shooter, victim, NpcCombat.arrowDamage(shooter));
        }
    }

    /**
     * Un joueur frappe un soldat ennemi alors que le PvP est coupé dans ce monde : le coup est
     * porté ici (dégâts de son arme selon la recharge de l'attaque, coup critique en chute).
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerAttack(PrePlayerAttackEntityEvent event) {
        Player player = event.getPlayer();
        if (player.hasMetadata("NPC") || player.getWorld().getPVP()) return;
        if (!(event.getAttacked() instanceof LivingEntity soldier) || !isSoldier(soldier)) return;
        event.setCancelled(true);
        float charge = player.getAttackCooldown();
        double damage = NpcCombat.meleeDamage(player) * (0.2 + charge * charge * 0.8);
        boolean critical = charge > 0.9f && player.getFallDistance() > 0 && !player.isOnGround();
        if (critical) {
            damage *= 1.5;
            soldier.getWorld().playSound(soldier.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f, 1f);
        }
        player.resetCooldown();
        NpcCombat.strike(player, soldier, damage);
    }

    /** Pas de tirs ni de coups entre soldats d'une même armée. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!isSoldier(event.getEntity())) return;
        Entity damager = event.getDamager();
        if (damager instanceof Projectile p && p.getShooter() instanceof Entity shooter) damager = shooter;
        if (isSoldier(damager)) event.setCancelled(true);
    }

    /** Soldat abattu : ni butin ni expérience ; signalé au siège (bilan, champion, porte-étendard). */
    @EventHandler
    public void onNpcDeath(NPCDeathEvent event) {
        if (registry == null || event.getNPC().getOwningRegistry() != registry) return;
        event.getDrops().clear();
        event.setDroppedExp(0);
        Soldier s = soldiers.remove(event.getNPC().getId());
        Player killer = event.getNPC().getEntity() instanceof LivingEntity body ? body.getKiller() : null;
        if (s != null && killListener != null) killListener.killed(killer, s.champion, s.standard);
        NPC npc = event.getNPC();
        Bukkit.getScheduler().runTask(plugin, () -> npc.destroy()); // après l'événement de mort
    }

    /** Pas de message de mort dans le chat (un PNJ de type joueur meurt comme un joueur). */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDeath(EntityDeathEvent event) {
        if (!isSoldier(event.getEntity())) return;
        event.getDrops().clear();
        event.setDroppedExp(0);
        if (event instanceof PlayerDeathEvent death) {
            death.setKeepInventory(true);
            death.setKeepLevel(true);
            death.setDeathMessage(null);
        }
    }

    /** Sol de la colonne (x, z) s'il est libre (utilisé pour les étapes de marche). */
    static Location ground(World w, int x, int z) {
        Block feet = w.getBlockAt(x, w.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES) + 1, z);
        Block below = feet.getRelative(0, -1, 0), head = feet.getRelative(0, 1, 0);
        if (!feet.isPassable() || feet.isLiquid() || !head.isPassable() || head.isLiquid()) return null;
        if (!below.getType().isSolid() || below.isLiquid()) return null;
        return feet.getLocation().add(0.5, 0, 0.5);
    }
}
