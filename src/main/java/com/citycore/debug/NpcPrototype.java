package com.citycore.debug;

import com.citycore.city.CityManager;
import com.citycore.inhabitant.InhabitantSkins;
import com.citycore.npc.SafeNavigation;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.ai.EntityTarget;
import net.citizensnpcs.api.event.DespawnReason;
import net.citizensnpcs.api.event.NPCDamageEntityEvent;
import net.citizensnpcs.api.event.NPCDamageEvent;
import net.citizensnpcs.api.event.NPCDeathEvent;
import net.citizensnpcs.api.event.NPCSpawnEvent;
import net.citizensnpcs.api.npc.MemoryNPCDataStore;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.npc.NPCRegistry;
import net.citizensnpcs.api.trait.trait.Equipment;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Phase 0 du plan « Valmont vivante » (docs/PLAN_VILLE_VIVANTE.md) : prototype TEMPORAIRE.
 *
 * Vérifie sur le serveur hybride ce dont dépendent les habitants et les soldats :
 * PNJ Citizens de type joueur blessables (dégâts, mort sans butin, réapparition, transformation),
 * skins variés des habitants (liste habitants.skins de config.yml),
 * coût serveur d'une foule de PNJ qui se promènent, combat d'un PNJ armé contre un monstre.
 *
 * Les PNJ vivent dans un registre Citizens en mémoire : jamais sauvegardés, ils disparaissent
 * au redémarrage et ne sont pas vus par NPCManager (qui parcourt le registre principal).
 * À supprimer une fois les phases 3 et 5 lancées (commande /city proto).
 */
public class NpcPrototype implements Listener {

    private static final String PREFIX = "§8[§dProto§8] §7";

    /** Rayon dans lequel un soldat repère les monstres. */
    private static final double SOLDIER_SIGHT = 16;
    /** PV d'un soldat (un joueur en a 20). */
    private static final double SOLDIER_HEALTH = 30;

    private final JavaPlugin  plugin;
    private final CityManager cityManager;
    private NPCRegistry registry;            // créé au premier usage
    private InhabitantSkins skins;           // liste habitants.skins de config.yml
    private InhabitantSkins uniforms;        // liste soldats.skins : uniforme d'un habitant devenu soldat

    // Promenade des habitants (habitants.vitesse, pause-promenade, rayon-promenade de config.yml)
    private float walkSpeed  = 0.4f;         // 1 = vitesse d'un joueur ; les artisans flânent à 0,4
    private long  strollPause = 6000;        // ms d'arrêt entre deux trajets (± 50 %)
    private int   strollRadius = 12;         // blocs autour de la maison

    private final Set<Integer> soldiers = new HashSet<>();     // id des PNJ soldats
    private final Set<Integer> crowd    = new HashSet<>();     // id des PNJ de la foule (promenade, messages réduits)
    private final Map<Integer, Location> homes = new HashMap<>();  // habitant → point de départ (sa « maison »)
    private final Map<Integer, Long> idleSince = new HashMap<>();  // habitant arrêté depuis… (pause de promenade)
    private final Set<Long> claimed = new HashSet<>();             // chunks de la ville (cache, voir isClaimed)
    private String claimedWorld;
    private long   claimedAt;
    private final Set<UUID>    zombies  = new HashSet<>();     // zombies créés par le prototype
    private final Map<UUID, Integer> zombieTargets = new HashMap<>(); // zombie → PNJ visé
    private final Map<Integer, Long> lastDamageMsg = new HashMap<>(); // anti-spam des dégâts
    private final Set<Integer> deadOnce = new HashSet<>();      // PNJ morts (pour repérer une réapparition)

    private BukkitTask combatTask;
    private TickMonitor tickMonitor;

    public NpcPrototype(JavaPlugin plugin, CityManager cityManager) {
        this.plugin      = plugin;
        this.cityManager = cityManager;
    }

    /* =========================
       COMMANDE /city proto
       ========================= */

    public void handle(Player player, String[] args) {
        if (args.length < 2) {
            usage(player);
            return;
        }
        ensureStarted();
        switch (args[1].toLowerCase()) {
            case "habitant" -> {
                Location at = inFront(player, 3);
                if (!isClaimed(at)) {
                    player.sendMessage(PREFIX + "§cLes habitants vivent en ville : placez-vous dans un chunk revendiqué.");
                    return;
                }
                spawnInhabitant(player, at, true);
            }
            case "soldat"   -> spawnSoldier(player);
            case "former"   -> trainNearest(player);
            case "zombie"   -> spawnZombie(player);
            case "foule"    -> spawnCrowd(player, args.length >= 3 ? parse(args[2], 20) : 20);
            case "tps"      -> reportTps(player);
            case "skins"    -> reportSkins(player);
            case "clear"    -> player.sendMessage(PREFIX + "§a" + clear() + " §7PNJ et zombies supprimés.");
            default         -> usage(player);
        }
    }

    private void usage(Player player) {
        player.sendMessage(PREFIX + "§fUsage : §e/city proto <habitant|soldat|former|zombie|foule [n]|tps|skins|clear>");
        player.sendMessage("§8Prototype temporaire : PNJ blessables, foule, combat. Voir docs/PLAN_VILLE_VIVANTE.md");
    }

    /** Registre en mémoire + tâches du prototype, créés au premier usage seulement. */
    private void ensureStarted() {
        if (registry == null) registry = CitizensAPI.createAnonymousNPCRegistry(new MemoryNPCDataStore());
        if (skins == null) skins = new InhabitantSkins(plugin, InhabitantSkins.HABITANTS);
        if (uniforms == null) {
            uniforms = new InhabitantSkins(plugin, InhabitantSkins.SOLDATS);
            loadMovement();
        }
        if (combatTask == null) combatTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 10L, 10L);
        if (tickMonitor == null) tickMonitor = new TickMonitor(plugin);
    }

    /* =========================
       APPARITIONS
       ========================= */

    /** Habitant blessable : PNJ de type joueur, skin tiré de la liste habitants.skins. */
    private NPC spawnInhabitant(Player player, Location loc, boolean announce) {
        NPC npc = registry.createNPC(EntityType.PLAYER, "Habitant");
        makeVulnerable(npc);
        int skin = skins.apply(npc); // avant l'apparition, comme pour les artisans
        npc.spawn(loc);
        npc.getNavigator().getDefaultParameters().speedModifier(walkSpeed);
        homes.put(npc.getId(), loc.clone()); // il ne quitte pas la ville : s'il en sort, il revient ici
        if (announce) {
            player.sendMessage(PREFIX + "Habitant §f#" + npc.getId() + " §7apparu (blessable, "
                    + skinLabel(skin) + "). Faites §e/city proto zombie §7pour l'attaquer.");
        }
        return npc;
    }

    /** Soldat blessable : uniforme (soldats.skins), armure, épée, 30 PV, attaque les monstres. */
    private void spawnSoldier(Player player) {
        NPC npc = registry.createNPC(EntityType.PLAYER, "Soldat");
        makeVulnerable(npc);
        String skin = uniforms.isEmpty() ? skinLabel(skins.apply(npc)) + " (aucun uniforme défini)"
                : "uniforme n°" + (uniforms.apply(npc) + 1);
        equip(npc);
        npc.spawn(inFront(player, 3));
        setSoldierHealth(npc);
        soldiers.add(npc.getId());
        player.sendMessage(PREFIX + "Soldat §f#" + npc.getId() + " §7apparu (" + (int) SOLDIER_HEALTH
                + " PV, " + skin + "). Il attaque les monstres à moins de " + (int) SOLDIER_SIGHT + " blocs.");
    }

    /**
     * Formation : l'habitant du prototype le plus proche devient soldat. Il change de skin
     * (uniforme de soldats.skins, sauf liste vide), reçoit armure et épée, et réapparaît sur place.
     */
    private void trainNearest(Player player) {
        NPC npc = null;
        double best = 16 * 16;
        for (NPC candidate : registry) {
            if (soldiers.contains(candidate.getId()) || !candidate.isSpawned()
                    || !candidate.getEntity().getWorld().equals(player.getWorld())) continue;
            double d = candidate.getEntity().getLocation().distanceSquared(player.getLocation());
            if (d < best) { best = d; npc = candidate; }
        }
        if (npc == null) {
            player.sendMessage(PREFIX + "§cAucun habitant du prototype à moins de 16 blocs : §e/city proto habitant §cd'abord.");
            return;
        }
        Location at = npc.getEntity().getLocation();
        npc.getNavigator().cancelNavigation(); // fin de la promenade : il part en ronde
        homes.remove(npc.getId());
        idleSince.remove(npc.getId());
        npc.despawn(DespawnReason.PENDING_RESPAWN);
        npc.setName("Soldat");
        String skin = uniforms.isEmpty() ? "skin d'habitant gardé (aucun uniforme défini)"
                : "uniforme n°" + (uniforms.apply(npc) + 1);
        equip(npc);
        npc.spawn(at);
        npc.getNavigator().getDefaultParameters().speedModifier(1f); // un soldat court après les monstres
        setSoldierHealth(npc);
        crowd.remove(npc.getId());
        soldiers.add(npc.getId());
        player.sendMessage(PREFIX + "Habitant §f#" + npc.getId() + " §7formé : il devient §fSoldat §7(" + skin + ").");
    }

    /** Armure de fer complète et épée (rien ne tombe à sa mort, voir onEntityDeath). */
    private static void equip(NPC npc) {
        Equipment eq = npc.getOrAddTrait(Equipment.class);
        eq.set(Equipment.EquipmentSlot.HELMET, new ItemStack(Material.IRON_HELMET));
        eq.set(Equipment.EquipmentSlot.CHESTPLATE, new ItemStack(Material.IRON_CHESTPLATE));
        eq.set(Equipment.EquipmentSlot.LEGGINGS, new ItemStack(Material.IRON_LEGGINGS));
        eq.set(Equipment.EquipmentSlot.BOOTS, new ItemStack(Material.IRON_BOOTS));
        eq.set(Equipment.EquipmentSlot.HAND, new ItemStack(Material.IRON_SWORD));
    }

    private static void setSoldierHealth(NPC npc) {
        if (npc.getEntity() instanceof LivingEntity body) {
            AttributeInstance max = body.getAttribute(Attribute.GENERIC_MAX_HEALTH);
            if (max != null) {
                max.setBaseValue(SOLDIER_HEALTH);
                body.setHealth(SOLDIER_HEALTH);
            }
        }
    }

    /** Zombie (casqué pour ne pas brûler au soleil) qui vise le PNJ du prototype le plus proche. */
    private void spawnZombie(Player player) {
        NPC target = nearestNpc(player.getLocation());
        if (target == null) {
            player.sendMessage(PREFIX + "§cAucun PNJ du prototype à attaquer : §e/city proto habitant §cd'abord.");
            return;
        }
        Location at = target.getEntity().getLocation().clone()
                .add(player.getLocation().getDirection().setY(0).normalize().multiply(-6));
        at = ground(at);
        Zombie zombie = (Zombie) at.getWorld().spawnEntity(at, EntityType.ZOMBIE);
        zombie.getEquipment().setHelmet(new ItemStack(Material.IRON_HELMET));
        try {
            zombie.setShouldBurnInDay(false); // API Paper ; le casque suffit si le serveur hybride ne l'a pas
        } catch (Throwable ignored) {
        }
        zombie.setTarget((LivingEntity) target.getEntity());
        zombies.add(zombie.getUniqueId());
        zombieTargets.put(zombie.getUniqueId(), target.getId());
        player.sendMessage(PREFIX + "Zombie lancé sur §f" + target.getName() + " #" + target.getId() + "§7.");
    }

    private String skinLabel(int index) {
        return index < 0 ? "skin par défaut de Minecraft" : "skin n°" + (index + 1) + " de la liste";
    }

    /** Lit les réglages de promenade (section habitants de config.yml, relue dans le fichier). */
    private void loadMovement() {
        java.io.File file = new java.io.File(plugin.getDataFolder(), "config.yml");
        org.bukkit.configuration.ConfigurationSection c = file.exists()
                ? YamlConfiguration.loadConfiguration(file) : plugin.getConfig();
        walkSpeed    = (float) Math.max(0.1, Math.min(1.5, c.getDouble("habitants.vitesse", 0.4)));
        strollPause  = Math.round(Math.max(0, Math.min(120, c.getDouble("habitants.pause-promenade", 6))) * 1000);
        strollRadius = Math.max(3, Math.min(48, c.getInt("habitants.rayon-promenade", 12)));
    }

    /** Relit skins et réglages des habitants et soldats dans config.yml et en donne le bilan. */
    private void reportSkins(Player player) {
        skins.reload();
        uniforms.reload();
        loadMovement();
        player.sendMessage(PREFIX + "Promenade : vitesse §f" + fmt(walkSpeed) + "§7, pause §f"
                + fmt(strollPause / 1000.0) + " s§7, rayon §f" + strollRadius + " blocs §7(dans la ville uniquement).");
        player.sendMessage(PREFIX + "Skins d'habitants utilisables : §f" + skins.size() + ignoredLabel(skins));
        if (skins.isEmpty()) {
            player.sendMessage(PREFIX + "§8Liste vide : skins par défaut de Minecraft. Remplissez habitants.skins dans config.yml.");
        }
        player.sendMessage(PREFIX + "Uniformes de soldat utilisables : §f" + uniforms.size() + ignoredLabel(uniforms));
        if (uniforms.isEmpty()) {
            player.sendMessage(PREFIX + "§8Liste vide : un soldat garde son skin d'habitant. Remplissez soldats.skins dans config.yml.");
        }
    }

    private static String ignoredLabel(InhabitantSkins list) {
        return list.ignored() > 0 ? " §8(" + list.ignored() + " ligne(s) vide(s) ou incomplète(s))" : "";
    }

    /** Foule d'habitants qui se promènent en ville : mesure du coût serveur. */
    private void spawnCrowd(Player player, int count) {
        count = Math.max(1, Math.min(60, count));
        if (!isClaimed(player.getLocation())) {
            player.sendMessage(PREFIX + "§cLes habitants vivent en ville : placez-vous dans un chunk revendiqué.");
            return;
        }
        int spawned = 0;
        for (int i = 0; i < count; i++) {
            // Point d'apparition dans la ville, au niveau du sol autour du joueur
            Location loc = randomSpot(player.getLocation(), 12);
            if (loc == null) continue;
            NPC npc = spawnInhabitant(player, loc, false);
            crowd.add(npc.getId());
            spawned++;
        }
        player.sendMessage(PREFIX + "§f" + spawned + " §7habitants se promènent dans la ville (" + aliveCount()
                + " PNJ du prototype en jeu). Mesurez avec §e/city proto tps§7.");
    }

    /* =========================
       PROMENADE DES HABITANTS (dans la ville uniquement)
       ========================= */

    /**
     * Habitant sorti de la ville (poussé, en fuite) : il rentre chez lui. Habitant de la foule
     * arrêté depuis assez longtemps : il part vers un point au hasard autour de sa maison, choisi
     * seulement dans les chunks revendiqués. L'habitant seul (test des dégâts) ne se promène pas.
     */
    private void tickStrolls() {
        long now = System.currentTimeMillis();
        for (NPC npc : registry) {
            int id = npc.getId();
            Location home = homes.get(id);
            if (home == null || soldiers.contains(id) || !npc.isSpawned()) continue;
            boolean walking = npc.getNavigator().isNavigating();

            if (!isClaimed(npc.getEntity().getLocation())) {
                if (!walking) SafeNavigation.walkTo(npc, home, walkSpeed, true); // téléporté s'il est trop loin
                continue;
            }
            if (!crowd.contains(id)) continue;
            if (walking) { idleSince.remove(id); continue; }

            // Pause de promenade, variable (± 50 %) pour que la foule ne reparte pas d'un bloc
            long idle = idleSince.computeIfAbsent(id, k -> now);
            long pause = strollPause / 2 + (strollPause == 0 ? 0 : Math.floorMod(id * 7919L, strollPause));
            if (now - idle < pause) continue;
            Location target = randomSpot(home, strollRadius);
            if (target != null && SafeNavigation.walkTo(npc, target, walkSpeed, false) == SafeNavigation.Result.WALKING) {
                idleSince.remove(id);
            }
        }
    }

    /**
     * Point où un habitant peut se tenir, au hasard à moins de radius blocs de center, dans un
     * chunk revendiqué et chargé, à peu près au niveau de center (pas sur les toits). null si
     * aucun point trouvé en quelques essais.
     */
    private Location randomSpot(Location center, int radius) {
        World world = center.getWorld();
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (int attempt = 0; attempt < 10; attempt++) {
            int x = center.getBlockX() + rnd.nextInt(-radius, radius + 1);
            int z = center.getBlockZ() + rnd.nextInt(-radius, radius + 1);
            if (!world.isChunkLoaded(x >> 4, z >> 4) || !isClaimed(world, x >> 4, z >> 4)) continue;
            for (int dy = 3; dy >= -4; dy--) {
                int y = center.getBlockY() + dy;
                Block feet = world.getBlockAt(x, y, z), head = feet.getRelative(0, 1, 0), below = feet.getRelative(0, -1, 0);
                if (feet.isPassable() && !feet.isLiquid() && head.isPassable() && !head.isLiquid()
                        && below.getType().isSolid()) {
                    return new Location(world, x + 0.5, y, z + 0.5);
                }
            }
        }
        return null;
    }

    private boolean isClaimed(Location loc) {
        return isClaimed(loc.getWorld(), loc.getBlockX() >> 4, loc.getBlockZ() >> 4);
    }

    /** Chunk revendiqué par la ville ? Liste relue au plus toutes les 5 secondes (pas de requête par appel). */
    private boolean isClaimed(World world, int chunkX, int chunkZ) {
        long now = System.currentTimeMillis();
        if (!world.getName().equals(claimedWorld) || now - claimedAt > 5000) {
            claimed.clear();
            for (long[] c : cityManager.getClaimedChunkCoords(world.getName())) claimed.add(chunkKey((int) c[0], (int) c[1]));
            claimedWorld = world.getName();
            claimedAt = now;
        }
        return claimed.contains(chunkKey(chunkX, chunkZ));
    }

    private static long chunkKey(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }

    /** PNJ blessable : non protégé, ciblable par les monstres, sans butin. */
    private void makeVulnerable(NPC npc) {
        npc.setProtected(false);
        npc.data().set(NPC.Metadata.TARGETABLE, true);
        npc.data().set(NPC.Metadata.DROPS_ITEMS, false);
    }

    /* =========================
       COMBAT DES SOLDATS ET ZOMBIES DU PROTOTYPE
       ========================= */

    /** Deux fois par seconde : promenade des habitants, puis combat. */
    private void tick() {
        if (registry == null) return;
        tickStrolls();
        tickCombat();
    }

    /** Chaque soldat attaque le monstre le plus proche ; les zombies gardent leur cible. */
    private void tickCombat() {
        for (NPC npc : registry) {
            if (!soldiers.contains(npc.getId()) || !npc.isSpawned()) continue;
            EntityTarget current = npc.getNavigator().getEntityTarget();
            if (current != null && current.getTarget() != null && !current.getTarget().isDead()) continue;
            Monster enemy = nearestMonster(npc.getEntity());
            if (enemy != null) {
                npc.getNavigator().setTarget(enemy, true); // true = attaque au contact
                notifyOps("Soldat §f#" + npc.getId() + " §7engage un §f" + enemy.getType().name().toLowerCase()
                        + " §7à " + (int) enemy.getLocation().distance(npc.getEntity().getLocation()) + " blocs.");
            }
        }
        // Un zombie peut se détourner vers un joueur : on le remet sur le PNJ visé tant qu'il vit
        zombies.removeIf(id -> Bukkit.getEntity(id) == null);
        for (UUID id : zombies) {
            if (!(Bukkit.getEntity(id) instanceof Zombie zombie)) continue;
            NPC target = registry.getById(zombieTargets.getOrDefault(id, -1));
            if (target != null && target.isSpawned() && zombie.getTarget() != target.getEntity()) {
                zombie.setTarget((LivingEntity) target.getEntity());
            }
        }
    }

    private Monster nearestMonster(Entity from) {
        return from.getNearbyEntities(SOLDIER_SIGHT, 6, SOLDIER_SIGHT).stream()
                .filter(e -> e instanceof Monster m && !m.isDead())
                .map(e -> (Monster) e)
                .min(Comparator.comparingDouble(m -> m.getLocation().distanceSquared(from.getLocation())))
                .orElse(null);
    }

    /* =========================
       ÉVÉNEMENTS : CE QUE LE TEST DOIT RÉVÉLER
       ========================= */

    @EventHandler
    public void onDamage(NPCDamageEvent event) {
        NPC npc = event.getNPC();
        if (!ours(npc) || crowd.contains(npc.getId())) return;
        long now = System.currentTimeMillis();
        if (now - lastDamageMsg.getOrDefault(npc.getId(), 0L) < 1500) return; // 1 message / 1,5 s
        lastDamageMsg.put(npc.getId(), now);
        double damage = event.getEvent().getFinalDamage();
        double health = npc.getEntity() instanceof LivingEntity le ? le.getHealth() - damage : -1;
        notifyOps(npc.getName() + " §f#" + npc.getId() + " §7blessé : §c-" + fmt(damage)
                + " PV §7(" + event.getCause().name().toLowerCase() + "), reste §f" + fmt(Math.max(0, health))
                + (event.isCancelled() ? " §c[ANNULÉ par un autre plugin]" : ""));
    }

    @EventHandler
    public void onSoldierHit(NPCDamageEntityEvent event) {
        if (!ours(event.getNPC())) return;
        notifyOps("Soldat §f#" + event.getNPC().getId() + " §7frappe un §f"
                + event.getDamaged().getType().name().toLowerCase() + "§7.");
    }

    @EventHandler
    public void onDeath(NPCDeathEvent event) {
        NPC npc = event.getNPC();
        if (!ours(npc)) return;
        event.getDrops().clear(); // l'équipement d'un soldat ne tombe pas au sol
        event.setDroppedExp(0);
        deadOnce.add(npc.getId());
        notifyOps("§c☠ " + npc.getName() + " #" + npc.getId() + " est mort. §7Vérification d'une réapparition dans 10 s…");
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (npc.isSpawned()) {
                notifyOps("§e⚠ " + npc.getName() + " #" + npc.getId() + " est réapparu tout seul (à empêcher en phase 3).");
            } else {
                notifyOps("§a✔ " + npc.getName() + " #" + npc.getId() + " n'est pas réapparu. Suppression.");
            }
            soldiers.remove(npc.getId());
            crowd.remove(npc.getId());
            homes.remove(npc.getId());
            idleSince.remove(npc.getId());
            npc.destroy();
        }, 200L);
    }

    /**
     * Aucun butin ni expérience à la mort (armure et arme d'un soldat comprises), ni message de
     * mort dans le chat : un PNJ de type joueur meurt comme un joueur, inventaire répandu compris.
     * Priorité la plus haute : passe après Citizens, qui pourrait remplir la liste.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDeath(EntityDeathEvent event) {
        if (registry == null || !registry.isNPC(event.getEntity())) return;
        event.getDrops().clear();
        event.setDroppedExp(0);
        if (event instanceof PlayerDeathEvent death) {
            death.setKeepInventory(true); // rien n'est répandu au sol
            death.setKeepLevel(true);
            death.setDeathMessage(null);
        }
    }

    @EventHandler
    public void onRespawn(NPCSpawnEvent event) {
        if (ours(event.getNPC()) && deadOnce.contains(event.getNPC().getId())) {
            notifyOps("§e⚠ Réapparition de " + event.getNPC().getName() + " #" + event.getNPC().getId() + " après sa mort.");
        }
    }

    /** Garde-fou : aucune transformation d'un PNJ du prototype (on l'empêche et on le signale). */
    @EventHandler(ignoreCancelled = true)
    public void onTransform(EntityTransformEvent event) {
        if (registry == null || !registry.isNPC(event.getEntity())) return;
        event.setCancelled(true);
        notifyOps("§e⚠ Transformation annulée : " + event.getEntity().getName() + " → "
                + event.getTransformReason().name().toLowerCase() + " (à bloquer aussi en phase 3).");
    }

    private boolean ours(NPC npc) {
        return registry != null && npc.getOwningRegistry() == registry;
    }

    /* =========================
       MESURE DU SERVEUR
       ========================= */

    private void reportTps(Player player) {
        player.sendMessage(PREFIX + "PNJ du prototype en jeu : §f" + aliveCount() + " §7(foule : " + crowd.size() + ")");
        // Mesure propre au plugin (fonctionne même si le serveur hybride n'expose pas les TPS de Paper)
        player.sendMessage(PREFIX + "TPS mesurés (5 dernières s) : §f" + fmt(tickMonitor.tps()));
        try {
            double[] tps = Bukkit.getTPS();
            player.sendMessage(PREFIX + "TPS du serveur 1 / 5 / 15 min : §f" + fmt(tps[0]) + " / "
                    + fmt(tps[1]) + " / " + fmt(tps[2]) + " §7— tick moyen : §f" + fmt(Bukkit.getAverageTickTime()) + " ms");
        } catch (Throwable t) {
            player.sendMessage(PREFIX + "§8TPS de Paper indisponibles sur ce serveur (" + t.getClass().getSimpleName() + ").");
        }
    }

    private int aliveCount() {
        int alive = 0;
        for (NPC npc : registry) if (npc.isSpawned()) alive++;
        return alive;
    }

    /** Compte les ticks réels sur une fenêtre glissante de 100 ticks. */
    private static final class TickMonitor {
        private final Deque<Long> stamps = new ArrayDeque<>();

        TickMonitor(JavaPlugin plugin) {
            Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                stamps.addLast(System.nanoTime());
                if (stamps.size() > 101) stamps.removeFirst();
            }, 1L, 1L);
        }

        double tps() {
            if (stamps.size() < 2) return 20;
            double seconds = (stamps.peekLast() - stamps.peekFirst()) / 1e9;
            return Math.min(20, (stamps.size() - 1) / seconds);
        }
    }

    /* =========================
       NETTOYAGE
       ========================= */

    /** Supprime les PNJ et zombies du prototype ; renvoie leur nombre. */
    public int clear() {
        int count = 0;
        if (registry != null) {
            List<NPC> all = new ArrayList<>();
            registry.forEach(all::add);
            for (NPC npc : all) { npc.destroy(); count++; }
        }
        for (UUID id : zombies) {
            Entity e = Bukkit.getEntity(id);
            if (e != null) { e.remove(); count++; }
        }
        zombies.clear();
        zombieTargets.clear();
        soldiers.clear();
        crowd.clear();
        homes.clear();
        idleSince.clear();
        deadOnce.clear();
        return count;
    }

    /* =========================
       OUTILS
       ========================= */

    private NPC nearestNpc(Location from) {
        NPC best = null;
        double bestDist = Double.MAX_VALUE;
        for (NPC npc : registry) {
            if (!npc.isSpawned() || !npc.getEntity().getWorld().equals(from.getWorld())) continue;
            double d = npc.getEntity().getLocation().distanceSquared(from);
            if (d < bestDist) { bestDist = d; best = npc; }
        }
        return best;
    }

    private static Location inFront(Player player, double distance) {
        Location loc = player.getLocation().clone();
        loc.add(loc.getDirection().setY(0).normalize().multiply(distance));
        Location spot = ground(loc);
        spot.setYaw((player.getLocation().getYaw() + 180) % 360); // face au joueur
        return spot;
    }

    /** Pose le point sur le premier bloc solide sous lui (ou au-dessus s'il est dans le sol). */
    private static Location ground(Location loc) {
        World world = loc.getWorld();
        int x = loc.getBlockX(), z = loc.getBlockZ();
        int y = loc.getBlockY();
        while (y > world.getMinHeight() && world.getBlockAt(x, y - 1, z).isPassable()) y--;
        while (y < world.getMaxHeight() - 2 && !world.getBlockAt(x, y, z).isPassable()) y++;
        return new Location(world, x + 0.5, y, z + 0.5, loc.getYaw(), 0f);
    }

    private void notifyOps(String message) {
        plugin.getLogger().info("[Proto] " + message.replaceAll("§.", ""));
        // Suivi du prototype : pour qui a le droit de s'en servir (citycore.admin.proto, op par défaut)
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.hasPermission(com.citycore.command.CityPermissions.ADMIN_PROTO)) p.sendMessage(PREFIX + message);
        }
    }

    private static int parse(String s, int def) {
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return def; }
    }

    private static String fmt(double d) {
        return String.format(java.util.Locale.FRANCE, "%.1f", d);
    }
}
