package com.citycore.npc;

import com.citycore.building.Building;
import com.citycore.building.BuildingManager;
import com.citycore.city.CityManager;
import com.citycore.quest.city.FindNpcQuestManager;
import com.citycore.util.TypewriterUtil;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Vehicle;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

public class NPCCityArrivalTask {

    private final NPCManager              npcManager;
    private final NPCNotificationManager  notificationManager;
    private final NPCDataManager          dataManager;
    private final CityManager             cityManager;
    private final JavaPlugin              plugin;
    private final BuildingManager         buildingManager;
    private final FindNpcQuestManager     findNpcQuestManager;
    private final Random                  random = new Random();

    private final Set<CityNPC> blockedNPCs    = new HashSet<>();
    // Empêche de lancer plusieurs navigations en attente pour le même NPC
    private final Set<CityNPC> pendingNavigation = new HashSet<>();

    public NPCCityArrivalTask(NPCManager npcManager, NPCDataManager dataManager,
                              CityManager cityManager, JavaPlugin plugin,
                              BuildingManager buildingManager,
                              NPCNotificationManager notificationManager,
                              FindNpcQuestManager findNpcQuestManager) {
        this.npcManager          = npcManager;
        this.notificationManager = notificationManager;
        this.dataManager         = dataManager;
        this.cityManager         = cityManager;
        this.plugin              = plugin;
        this.buildingManager     = buildingManager;
        this.findNpcQuestManager = findNpcQuestManager;
    }

    public void start() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (CityNPC type : CityNPC.values()) {
                    if (type == CityNPC.MAYOR) continue;

                    NPC npc = npcManager.getNPC(type);
                    if (npc == null || !npc.isSpawned()) continue;

                    NPCState state = dataManager.getState(type);

                    switch (state) {
                        case WANDERER -> handleWanderer(type, npc);
                        case ARRIVED  -> handleArrived(type, npc);
                        case ASSIGNED -> handleAssigned(type, npc);
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    /* =========================
       WANDERER — détection entrée ville
       ========================= */

    private void handleWanderer(CityNPC type, NPC npc) {
        Chunk chunk = npc.getEntity().getLocation().getChunk();
        if (!cityManager.isChunkClaimed(chunk)) return;

        // ✅ Transition WANDERER → ARRIVED
        dataManager.setState(type, NPCState.ARRIVED);
        cityManager.addResident();
        findNpcQuestManager.onNpcArrived(type);
        npcManager.setWandering(type, true);
        notificationManager.notifyAll(type, plugin.getServer(), npcManager);
        plugin.getLogger().info(type.displayName + " est arrivé dans la ville !");

        // ✅ Stop le suivi automatiquement
        npc.getEntity().getWorld().getPlayers().forEach(p -> {
            if (npcManager.isFollowing(p, type)) {
                npcManager.stopFollowing(p, type);
                p.sendMessage("§a" + type.displayName + " §aest arrivé dans la ville !");
            }
        });

        // Dialogue city_arrival
        List<String> lines = type.getDialogue("city_arrival");
        if (lines.isEmpty()) return;

        npc.getEntity().getLocation().getWorld().getPlayers().stream()
                .filter(p -> p.getLocation().distance(npc.getEntity().getLocation()) <= 16)
                .forEach(p -> TypewriterUtil.play(plugin, p, lines, null));
    }

    /* =========================
       ARRIVED — balade aléatoire dans la ville
       Si hors ville → retour vers la ville
       ========================= */

    private void handleArrived(CityNPC type, NPC npc) {
        if (npcManager.isFollowingSomeone(type)) return; // accompagne un joueur
        if (!npcManager.isWandering(type)) return;
        if (npc.getNavigator().isPaused()) return;
        if (npc.getNavigator().isNavigating()) return;
        // ✅ FIX : évite de lancer deux navigations async simultanées
        if (pendingNavigation.contains(type)) return;

        Location npcLoc = npc.getEntity().getLocation();
        Chunk chunk     = npcLoc.getChunk();

        if (!cityManager.isChunkClaimed(chunk)) {
            // Hors ville : retour vers le centre (marche si c'est près, sinon téléportation)
            Location cityCenter = getCityCenter(type, npcLoc.getWorld());
            if (cityCenter != null) SafeNavigation.walkTo(npc, cityCenter, 0.6f, true);
        } else {
            // Dans la ville : balade vers un chunk claimé déjà chargé, à portée de marche
            Location randomTarget = getRandomCityLocation(npcLoc.getWorld(), npcLoc);
            if (randomTarget != null) SafeNavigation.walkTo(npc, randomTarget, 0.4f, false);
        }
    }

    /* =========================
       ASSIGNED — assigné à un bâtiment
       ========================= */

    private void handleAssigned(CityNPC type, NPC npc) {
        if (npcManager.isFollowingSomeone(type)) return; // accompagne un joueur
        if (npc.getNavigator().isPaused()) return;

        Building building = buildingManager.getAssignedBuilding(type.tag);
        if (building == null) return;

        World    world  = npc.getEntity().getWorld();
        Location npcLoc = npc.getEntity().getLocation();
        Location target;

        // Bâtiment dans des chunks non chargés : lire ses blocs les chargerait de force, chaque
        // seconde. Avec un point PNJ connu, on l'y envoie directement ; sinon on attend.
        if (!buildingLoaded(world, building)) {
            if (building.hasNpcPoint() && !pendingNavigation.contains(type)) {
                Location point = new Location(world, building.npcX(), building.npcY(), building.npcZ(),
                        getFacingYaw(building), 0f);
                if (npcLoc.distanceSquared(point) < 4) return; // déjà à son poste
                pendingNavigation.add(type);
                world.getChunkAtAsync(point).whenComplete((c, err) -> Bukkit.getScheduler().runTask(plugin, () -> {
                    pendingNavigation.remove(type);
                    if (c != null && npc.isSpawned() && !npcManager.isFollowingSomeone(type)) {
                        npc.getNavigator().cancelNavigation();
                        npc.teleport(point, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.PLUGIN);
                    }
                }));
            }
            return;
        }

        if (building.hasNpcPoint()) {
            Location defined = new Location(world,
                    building.npcX(), building.npcY(), building.npcZ());
            if (isAccessible(world, defined)) {
                target = defined;
                if (blockedNPCs.remove(type))
                    notificationManager.setBlocked(type, npcManager, false);
            } else {
                target = findClosestAccessible(world, building, defined);
                if (blockedNPCs.add(type))
                    notificationManager.setBlocked(type, npcManager, true);
            }
        } else {
            target = findAccessibleLocation(world, building, npcLoc);
        }

        if (target == null) return;
        if (npcLoc.distanceSquared(target) < 0.1) return;

        // ✅ Téléportation si très proche
        if (npcLoc.distanceSquared(target) < 1) {
            float    yaw      = getFacingYaw(building);
            Location finalTarget = target.clone();
            finalTarget.setYaw(yaw);
            finalTarget.setPitch(0);

            npc.getNavigator().cancelNavigation();
            npc.getEntity().teleport(finalTarget);

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!npc.isSpawned()) return;
                Location loc = npc.getEntity().getLocation().clone();
                loc.setYaw(yaw);
                loc.setPitch(0);
                npc.getEntity().teleport(loc);
            }, 5L);
            return;
        }

        if (npc.getNavigator().isNavigating()) return;

        npc.getNavigator().getDefaultParameters().distanceMargin(0.1f);
        // Chemin court dans une zone chargée ; trop loin : téléportation directe à son poste
        SafeNavigation.Result result = SafeNavigation.walkTo(npc, target, 0.5f, true);
        if (result == SafeNavigation.Result.TELEPORTED) {
            float yaw = getFacingYaw(building);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!npc.isSpawned()) return;
                Location loc = npc.getEntity().getLocation().clone();
                loc.setYaw(yaw);
                loc.setPitch(0);
                npc.getEntity().teleport(loc);
            }, 2L);
            return;
        }
        if (result != SafeNavigation.Result.WALKING) return;

        // Rappel de fin de chemin : ajouté APRÈS setTarget, sur les paramètres de CE trajet
        // (avant, il s'ajoutait aux paramètres par défaut et s'accumulait à chaque trajet)
        npc.getNavigator().getLocalParameters()
                .addSingleUseCallback(cancelReason -> {
                    if (cancelReason != null) return;
                    if (!npc.isSpawned()) return;

                    float    yaw      = getFacingYaw(building);
                    Location finalLoc = npc.getEntity().getLocation().clone();
                    finalLoc.setYaw(yaw);
                    finalLoc.setPitch(0);

                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (npc.isSpawned()) npc.getEntity().teleport(finalLoc);
                    }, 2L);
                });
    }

    /* =========================
       HELPERS
       ========================= */

    /** Tous les chunks du bâtiment sont-ils chargés (lecture de ses blocs sans chargement forcé) ? */
    private boolean buildingLoaded(World world, Building building) {
        for (int cx = building.x1() >> 4; cx <= building.x2() >> 4; cx++) {
            for (int cz = building.z1() >> 4; cz <= building.z2() >> 4; cz++) {
                if (!world.isChunkLoaded(cx, cz)) return false;
            }
        }
        return true;
    }

    private float getFacingYaw(Building building) {
        if (building.npcYaw() == null) return 0f;
        float yaw = ((building.npcYaw() % 360) + 360) % 360;
        if (yaw > 180) yaw -= 360;
        if (yaw >= -45  && yaw < 45)   return 0f;
        if (yaw >= 45   && yaw < 135)  return 90f;
        if (yaw >= 135  || yaw < -135) return 180f;
        return -90f;
    }

    private boolean isAccessible(World world, Location loc) {
        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();
        return  world.getBlockAt(x, y - 1, z).getType().isSolid()
                && !world.getBlockAt(x, y,     z).getType().isSolid()
                && !world.getBlockAt(x, y + 1, z).getType().isSolid();
    }

    private Location findClosestAccessible(World world, Building building, Location origin) {
        Location best     = null;
        double   bestDist = Double.MAX_VALUE;

        for (int x = building.x1(); x <= building.x2(); x++) {
            for (int z = building.z1(); z <= building.z2(); z++) {
                int      y         = world.getHighestBlockYAt(x, z);
                Location candidate = new Location(world, x + 0.5, y + 1, z + 0.5);
                if (!isAccessible(world, candidate)) continue;
                double dist = origin.distanceSquared(candidate);
                if (dist < bestDist) { bestDist = dist; best = candidate; }
            }
        }
        return best;
    }

    private boolean isInBuilding(Location loc, Building building) {
        return loc.getBlockX() >= building.x1() && loc.getBlockX() <= building.x2()
                && loc.getBlockZ() >= building.z1() && loc.getBlockZ() <= building.z2();
    }

    private Location findAccessibleLocation(World world, Building building, Location npcLoc) {
        Location best     = null;
        double   bestDist = Double.MAX_VALUE;

        for (int x = building.x1(); x <= building.x2(); x++) {
            for (int z = building.z1(); z <= building.z2(); z++) {
                int      y         = world.getHighestBlockYAt(x, z);
                Location candidate = new Location(world, x + 0.5, y + 1, z + 0.5);

                if (!world.getBlockAt(x, y,     z).getType().isSolid()) continue;
                if ( world.getBlockAt(x, y + 1, z).getType().isSolid()) continue;
                if ( world.getBlockAt(x, y + 2, z).getType().isSolid()) continue;

                double dist = npcLoc.distanceSquared(candidate);
                if (dist < bestDist) { bestDist = dist; best = candidate; }
            }
        }
        return best;
    }

    /**
     * Retourne une position aléatoire dans un chunk claimé ET DÉJÀ CHARGÉ proche du NPC.
     * ✅ FIX : on filtre les chunks non chargés pour éviter getHighestBlockYAt() sur
     * des chunks absents, ce qui pouvait causer des micro-freezes.
     */
    private Location getRandomCityLocation(World world, Location near) {
        List<long[]> claimed = cityManager.getClaimedChunkCoords(world.getName());
        if (claimed.isEmpty()) return null;

        // ✅ FIX : on ne travaille QUE sur les chunks déjà en mémoire
        List<long[]> loaded = claimed.stream()
                .filter(c -> world.isChunkLoaded((int) c[0], (int) c[1]))
                .collect(Collectors.toList());

        if (loaded.isEmpty()) return null;

        loaded.sort((a, b) -> {
            double distA = Math.pow(a[0] * 16 - near.getX(), 2)
                    + Math.pow(a[1] * 16 - near.getZ(), 2);
            double distB = Math.pow(b[0] * 16 - near.getX(), 2)
                    + Math.pow(b[1] * 16 - near.getZ(), 2);
            return Double.compare(distA, distB);
        });

        int    pick  = random.nextInt(Math.min(5, loaded.size()));
        long[] chunk = loaded.get(pick);

        for (int attempt = 0; attempt < 20; attempt++) {
            int x    = (int) (chunk[0] * 16 + random.nextInt(16));
            int z    = (int) (chunk[1] * 16 + random.nextInt(16));
            int highY = world.getHighestBlockYAt(x, z);
            int y    = highY;

            while (y > 0) {
                org.bukkit.block.Block floor  = world.getBlockAt(x, y,     z);
                org.bukkit.block.Block above  = world.getBlockAt(x, y + 1, z);
                org.bukkit.block.Block above2 = world.getBlockAt(x, y + 2, z);
                if (floor.getType().isSolid()
                        && !floor.isLiquid()
                        && !isVegetation(floor.getType())
                        && above.getType().isAir()
                        && above2.getType().isAir()) {
                    return new Location(world, x + 0.5, y + 1, z + 0.5);
                }
                y--;
            }
        }
        return null;
    }

    private boolean isVegetation(org.bukkit.Material mat) {
        String name = mat.name();
        return name.contains("LEAVES") || name.contains("LOG")
                || name.contains("WOOD")   || name.contains("GRASS")
                || name.contains("FERN")   || name.contains("FLOWER")
                || name.contains("BUSH")   || name.contains("BAMBOO")
                || mat == org.bukkit.Material.SNOW
                || mat == org.bukkit.Material.CACTUS;
    }

    /**
     * Retourne le centre géographique de la ville (moyenne des chunks claimés), ou null si son
     * chunk n'est pas encore chargé : il est alors chargé en arrière-plan pour un prochain passage.
     */
    private Location getCityCenter(CityNPC type, World world) {
        List<long[]> claimed = cityManager.getClaimedChunkCoords(world.getName());
        if (claimed.isEmpty()) return null;

        double avgX = claimed.stream().mapToLong(c -> c[0]).average().orElse(0) * 16 + 8;
        double avgZ = claimed.stream().mapToLong(c -> c[1]).average().orElse(0) * 16 + 8;
        int cx = (int) Math.floor(avgX) >> 4, cz = (int) Math.floor(avgZ) >> 4;
        if (!world.isChunkLoaded(cx, cz)) {
            if (pendingNavigation.add(type)) {
                world.getChunkAtAsync(cx, cz).whenComplete((c, err) ->
                        Bukkit.getScheduler().runTask(plugin, () -> pendingNavigation.remove(type)));
            }
            return null;
        }
        // Sol naturel : un centre placé sur la cime d'un arbre serait inaccessible
        Location ground = com.citycore.util.GroundFinder.groundAt(world, (int) Math.floor(avgX), (int) Math.floor(avgZ));
        if (ground != null) return ground;
        double y = world.getHighestBlockYAt((int) avgX, (int) avgZ, org.bukkit.HeightMap.MOTION_BLOCKING_NO_LEAVES) + 1;
        return new Location(world, avgX, y, avgZ);
    }
}