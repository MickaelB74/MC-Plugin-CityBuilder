package com.citycore.building;

import org.bukkit.Location;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class BuildingSession {

    private final Map<UUID, Location> pos1         = new HashMap<>();
    private final Map<UUID, Location> pos2         = new HashMap<>();
    private final Map<UUID, String>   pendingName  = new HashMap<>();
    private final Map<UUID, PendingBuilding> pendingBuilding = new HashMap<>();

    // Phase 1
    public void setPos1(UUID uuid, Location loc) {
        if (loc == null) pos1.remove(uuid); else pos1.put(uuid, loc);
    }
    public void setPos2(UUID uuid, Location loc) {
        if (loc == null) pos2.remove(uuid); else pos2.put(uuid, loc);
    }
    public void setPendingName(UUID uuid, String name) { pendingName.put(uuid, name); }

    public Location getPos1(UUID uuid) { return pos1.get(uuid); }
    public Location getPos2(UUID uuid) { return pos2.get(uuid); }
    public String   getPendingName(UUID uuid) { return pendingName.get(uuid); }

    public boolean hasPos1(UUID uuid) { return pos1.containsKey(uuid); }
    public boolean hasPos2(UUID uuid) { return pos2.containsKey(uuid); }
    public boolean isActive(UUID uuid) { return pendingName.containsKey(uuid); }
    public boolean isComplete(UUID uuid) { return hasPos1(uuid) && hasPos2(uuid); }

    // Phase 2
    public void startNpcPointPhase(UUID uuid, String name, String world,
                                   int x1, int z1, int x2, int z2) {
        pendingBuilding.put(uuid, new PendingBuilding(name, world, x1, z1, x2, z2));
        // Nettoie phase 1 mais garde pendingBuilding
        pos1.remove(uuid);
        pos2.remove(uuid);
        pendingName.remove(uuid);
    }

    public boolean isNpcPointPhase(UUID uuid) {
        return pendingBuilding.containsKey(uuid);
    }

    public PendingBuilding getPendingBuilding(UUID uuid) {
        return pendingBuilding.get(uuid);
    }

    public void clear(UUID uuid) {
        pos1.remove(uuid);
        pos2.remove(uuid);
        pendingName.remove(uuid);
        pendingBuilding.remove(uuid);
    }

    public record PendingBuilding(String name, String world,
                                  int x1, int z1, int x2, int z2) {}
}