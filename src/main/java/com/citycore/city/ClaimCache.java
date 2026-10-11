package com.citycore.city;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.HashSet;
import java.util.Set;

/**
 * Chunks revendiqués par la ville, relus au plus toutes les 5 secondes : les déplacements des
 * habitants testent un chunk à chaque pas sans interroger la base de données à chaque fois.
 */
public final class ClaimCache {

    private static final long TTL_MS = 5000;

    private final CityManager cityManager;
    private final Set<Long> claimed = new HashSet<>();
    private String world;
    private long   loadedAt;

    public ClaimCache(CityManager cityManager) {
        this.cityManager = cityManager;
    }

    public boolean isClaimed(Location loc) {
        return loc.getWorld() != null && isClaimed(loc.getWorld(), loc.getBlockX() >> 4, loc.getBlockZ() >> 4);
    }

    public boolean isClaimed(World w, int chunkX, int chunkZ) {
        long now = System.currentTimeMillis();
        if (!w.getName().equals(world) || now - loadedAt > TTL_MS) {
            claimed.clear();
            for (long[] c : cityManager.getClaimedChunkCoords(w.getName())) claimed.add(key((int) c[0], (int) c[1]));
            world = w.getName();
            loadedAt = now;
        }
        return claimed.contains(key(chunkX, chunkZ));
    }

    /** Chunk revendiqué au bord de la ville (un de ses voisins ne l'est pas). */
    public boolean isEdge(World w, int chunkX, int chunkZ) {
        return isClaimed(w, chunkX, chunkZ) && (!isClaimed(w, chunkX + 1, chunkZ) || !isClaimed(w, chunkX - 1, chunkZ)
                || !isClaimed(w, chunkX, chunkZ + 1) || !isClaimed(w, chunkX, chunkZ - 1));
    }

    private static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }
}
