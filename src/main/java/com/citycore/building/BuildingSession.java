package com.citycore.building;

import org.bukkit.Location;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class BuildingSession {

    private final Map<UUID, Location> pos1         = new HashMap<>();
    private final Map<UUID, Location> pos2         = new HashMap<>();
    private final Map<UUID, Pending>  pending      = new HashMap<>();
    private final Map<UUID, PendingBuilding> pendingBuilding = new HashMap<>();

    /** Bâtiment en cours de sélection : nom, type et artisan pour qui l'atelier est réservé. */
    public record Pending(String name, BuildingType type, String reservedFor) {}

    // Phase 1
    public void setPos1(UUID uuid, Location loc) {
        if (loc == null) pos1.remove(uuid); else pos1.put(uuid, loc);
    }
    public void setPos2(UUID uuid, Location loc) {
        if (loc == null) pos2.remove(uuid); else pos2.put(uuid, loc);
    }
    public void setPending(UUID uuid, String name, BuildingType type, String reservedFor) {
        pending.put(uuid, new Pending(name, type, reservedFor));
    }

    public Location getPos1(UUID uuid) { return pos1.get(uuid); }
    public Location getPos2(UUID uuid) { return pos2.get(uuid); }
    public Pending  getPending(UUID uuid) { return pending.get(uuid); }
    public String   getPendingName(UUID uuid) {
        Pending p = pending.get(uuid);
        return p == null ? null : p.name();
    }

    public boolean hasPos1(UUID uuid) { return pos1.containsKey(uuid); }
    public boolean hasPos2(UUID uuid) { return pos2.containsKey(uuid); }
    public boolean isActive(UUID uuid) { return pending.containsKey(uuid); }
    public boolean isComplete(UUID uuid) { return hasPos1(uuid) && hasPos2(uuid); }

    // Phase 2
    public void startNpcPointPhase(UUID uuid, PendingBuilding building) {
        pendingBuilding.put(uuid, building);
        // Nettoie phase 1 mais garde pendingBuilding
        pos1.remove(uuid);
        pos2.remove(uuid);
        pending.remove(uuid);
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
        pending.remove(uuid);
        pendingBuilding.remove(uuid);
    }

    /** Zone choisie, en attente du point du PNJ. */
    public record PendingBuilding(String name, String world,
                                  int x1, int z1, int x2, int z2,
                                  BuildingType type, String reservedFor,
                                  int yMin, int yMax) {}
}
