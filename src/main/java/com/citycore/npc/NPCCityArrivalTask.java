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
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class NPCCityArrivalTask {

    private final NPCManager      npcManager;
    private final NPCNotificationManager notificationManager;
    private final NPCDataManager  dataManager;
    private final CityManager     cityManager;
    private final JavaPlugin      plugin;
    private final BuildingManager buildingManager;
    private final FindNpcQuestManager findNpcQuestManager;
    private final Random          random = new Random();

    private final Set<CityNPC> blockedNPCs = new HashSet<>();

    public NPCCityArrivalTask(NPCManager npcManager, NPCDataManager dataManager,
                              CityManager cityManager, JavaPlugin plugin, BuildingManager buildingManager, NPCNotificationManager notificationManager, FindNpcQuestManager findNpcQuestManager) {
        this.npcManager      = npcManager;
        this.notificationManager = notificationManager;
        this.dataManager     = dataManager;
        this.cityManager     = cityManager;
        this.plugin          = plugin;
        this.buildingManager = buildingManager;
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
                        case ASSIGNED -> handleAssigned(type, npc); // ✅
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
        notificationManager.notifyAll(type,
                plugin.getServer(), npcManager);
        plugin.getLogger().info(type.displayName + " est arrivé dans la ville !");

        // ✅ Stop le suivi automatiquement
        npc.getEntity().getWorld().getPlayers().forEach(p -> {
            if (npcManager.isFollowing(p, type)) {
                npcManager.stopFollowing(p, type);
                p.sendMessage("§a" + type.displayName
                        + " §aest arrivé dans la ville !");
            }
        });

        // Dialogue city_arrival
        List<String> lines = type.getDialogue("city_arrival");
        if (lines.isEmpty()) return;

        npc.getEntity().getLocation().getWorld().getPlayers().stream()
                .filter(p -> p.getLocation().distance(
                        npc.getEntity().getLocation()) <= 16)
                .forEach(p -> TypewriterUtil.play(plugin, p, lines, null));
    }

    /* =========================
       ARRIVED — balade aléatoire dans la ville
       Si suivi actif → le suivi prend le dessus
       Si hors ville → retour vers la ville
       ========================= */

    private void handleArrived(CityNPC type, NPC npc) {
        if (!npcManager.isWandering(type)) return;

        if (npc.getNavigator().isPaused()) return;
        if (npc.getNavigator().isNavigating()) return;

        Location npcLoc = npc.getEntity().getLocation();
        Chunk chunk     = npcLoc.getChunk();

        if (!cityManager.isChunkClaimed(chunk)) {
            // ✅ Hors ville — retourne vers un chunk claimé
            Location cityCenter = getCityCenter(npcLoc.getWorld());
            if (cityCenter != null) {
                npc.getNavigator().getDefaultParameters()
                        .speedModifier(0.6f)
                        .range(200f);
                npc.getNavigator().setTarget(cityCenter);
            }
        } else {
            // ✅ Dans la ville — balade aléatoire
            Location randomTarget = getRandomCityLocation(npcLoc.getWorld(), npcLoc);
            if (randomTarget != null) {
                npc.getNavigator().getDefaultParameters()
                        .speedModifier(0.4f)
                        .range(300f);
                npc.getNavigator().setTarget(randomTarget);
            }
        }
    }

    /* =========================
       ASSIGNED — assigné a un batiment
       ========================= */

    private void handleAssigned(CityNPC type, NPC npc) {
        if (npc.getNavigator().isPaused()) return;

        Building building = buildingManager.getAssignedBuilding(type.tag);
        if (building == null) return;

        World world     = npc.getEntity().getWorld();
        Location npcLoc = npc.getEntity().getLocation();
        Location target;

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

        // ✅ Téléportation avant le check isNavigating
        if (npcLoc.distanceSquared(target) < 1) {
            float yaw = getFacingYaw(building);
            target.setYaw(yaw);
            target.setPitch(0);

            npc.getNavigator().cancelNavigation();
            npc.getEntity().teleport(target);

            // Force le yaw après
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!npc.isSpawned()) return;
                Location finalLoc = npc.getEntity().getLocation().clone();
                finalLoc.setYaw(yaw);
                finalLoc.setPitch(0);
                npc.getEntity().teleport(finalLoc);
            }, 5L);
            return;
        }

        // ✅ Check isNavigating seulement pour éviter de spammer setTarget
        if (npc.getNavigator().isNavigating()) return;

        npc.getNavigator().getDefaultParameters()
                .speedModifier(0.5f)
                .range(200f)
                .distanceMargin(0.1f);

        npc.getNavigator().getLocalParameters()
                .addSingleUseCallback(cancelReason -> {
                    if (cancelReason != null) return; // Navigation annulée — pas arrivé
                    if (!npc.isSpawned()) return;

                    float yaw = getFacingYaw(building);
                    Location finalLoc = npc.getEntity().getLocation().clone();
                    finalLoc.setYaw(yaw);
                    finalLoc.setPitch(0);

                    // Petit délai pour laisser Citizens finir
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        npc.getEntity().teleport(finalLoc);
                    }, 2L);
                });

        npc.getNavigator().setTarget(target);
    }

    private float getFacingYaw(Building building) {
        if (building.npcYaw() == null) return 0f;
        float yaw = ((building.npcYaw() % 360) + 360) % 360;
        if (yaw > 180) yaw -= 360;
        if (yaw >= -45 && yaw < 45)   return 0f;    // Sud
        if (yaw >= 45 && yaw < 135)   return 90f;   // Ouest
        if (yaw >= 135 || yaw < -135) return 180f;  // Nord
        return -90f;                                  // Est
    }

    private boolean isAccessible(World world, Location loc) {
        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();
        return world.getBlockAt(x, y - 1, z).getType().isSolid()
                && !world.getBlockAt(x, y, z).getType().isSolid()
                && !world.getBlockAt(x, y + 1, z).getType().isSolid();
    }

    /**
     * Cherche la case accessible la plus proche du point NPC défini.
     */
    private Location findClosestAccessible(World world, Building building,
                                           Location origin) {
        Location best   = null;
        double bestDist = Double.MAX_VALUE;

        for (int x = building.x1(); x <= building.x2(); x++) {
            for (int z = building.z1(); z <= building.z2(); z++) {
                int y = world.getHighestBlockYAt(x, z);
                Location candidate = new Location(world, x + 0.5, y + 1, z + 0.5);

                if (!isAccessible(world, candidate)) continue;

                double dist = origin.distanceSquared(candidate);
                if (dist < bestDist) {
                    bestDist = dist;
                    best     = candidate;
                }
            }
        }
        return best;
    }

    /**
     * Vérifie si une location est dans les limites X/Z du bâtiment.
     */
    private boolean isInBuilding(Location loc, Building building) {
        return loc.getBlockX() >= building.x1()
                && loc.getBlockX() <= building.x2()
                && loc.getBlockZ() >= building.z1()
                && loc.getBlockZ() <= building.z2();
    }

    /**
     * Cherche une case accessible dans le bâtiment.
     * Parcourt toutes les cases et retourne la première
     * où le sol est solide et l'espace au-dessus est libre.
     * Si aucune n'est parfaite, retourne la plus proche du NPC.
     */
    private Location findAccessibleLocation(World world, Building building,
                                            Location npcLoc) {
        Location best     = null;
        double bestDist   = Double.MAX_VALUE;

        for (int x = building.x1(); x <= building.x2(); x++) {
            for (int z = building.z1(); z <= building.z2(); z++) {
                int y = world.getHighestBlockYAt(x, z);

                Location candidate = new Location(world, x + 0.5, y + 1, z + 0.5);

                // ✅ Vérifie sol solide + 2 blocs libres au-dessus
                if (!world.getBlockAt(x, y, z).getType().isSolid()) continue;
                if (world.getBlockAt(x, y + 1, z).getType().isSolid()) continue;
                if (world.getBlockAt(x, y + 2, z).getType().isSolid()) continue;

                double dist = npcLoc.distanceSquared(candidate);
                if (dist < bestDist) {
                    bestDist = dist;
                    best     = candidate;
                }
            }
        }

        return best;
    }

    /* =========================
       HELPERS
       ========================= */

    /**
     * Retourne une position aléatoire dans un chunk claimé proche du NPC.
     */
    private Location getRandomCityLocation(World world, Location near) {
        List<long[]> claimed = cityManager.getClaimedChunkCoords(world.getName());
        if (claimed.isEmpty()) return null;

        // Prend un chunk claimé aléatoire parmi les 5 plus proches
        claimed.sort((a, b) -> {
            double distA = Math.pow(a[0] * 16 - near.getX(), 2)
                    + Math.pow(a[1] * 16 - near.getZ(), 2);
            double distB = Math.pow(b[0] * 16 - near.getX(), 2)
                    + Math.pow(b[1] * 16 - near.getZ(), 2);
            return Double.compare(distA, distB);
        });

        int pick = random.nextInt(Math.min(5, claimed.size()));
        long[] chunk = claimed.get(pick);

        // Position aléatoire dans le chunk
        double x = chunk[0] * 16 + random.nextInt(16);
        double z = chunk[1] * 16 + random.nextInt(16);
        double y = world.getHighestBlockYAt((int) x, (int) z) + 1;

        return new Location(world, x, y, z);
    }

    /**
     * Retourne le centre géographique de la ville (moyenne des chunks claimés).
     */
    private Location getCityCenter(World world) {
        List<long[]> claimed = cityManager.getClaimedChunkCoords(world.getName());
        if (claimed.isEmpty()) return null;

        double avgX = claimed.stream().mapToLong(c -> c[0]).average().orElse(0) * 16 + 8;
        double avgZ = claimed.stream().mapToLong(c -> c[1]).average().orElse(0) * 16 + 8;
        double y    = world.getHighestBlockYAt((int) avgX, (int) avgZ) + 1;

        return new Location(world, avgX, y, avgZ);
    }
}