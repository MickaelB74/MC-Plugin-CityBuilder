package com.citycore.npc;

import net.citizensnpcs.api.ai.NavigatorParameters;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.event.player.PlayerTeleportEvent;

/**
 * Déplacements des PNJ sans geler le serveur.
 *
 * Le pathfinder A* de Citizens (useNewPathfinder) met en cache, à sa création, TOUS les chunks
 * d'un carré de côté 2 × range centré sur le PNJ, et charge de force ceux qui ne le sont pas, sur
 * le fil principal. Une portée de 300 blocs représentait ~1 400 chunks : plus de 10 secondes de
 * blocage sur un serveur chargé de mods (rapport « The server has not responded »).
 *
 * Ici, la portée suit la distance réelle et reste courte, et l'on ne lance un chemin que si tout
 * le carré est déjà chargé. Au-delà, le PNJ est téléporté (arrivée « directe » à son poste).
 */
public final class SafeNavigation {

    /** Portée maximale d'un chemin (carré de 7 x 7 chunks au plus autour du PNJ). */
    public static final float MAX_RANGE = 48f;
    /** Portée minimale (évite un échec du pathfinder sur une cible très proche). */
    public static final float MIN_RANGE = 16f;
    /** Marge ajoutée à la distance (détours). */
    private static final float MARGIN = 12f;

    private SafeNavigation() {}

    /** Résultat d'une demande de déplacement. */
    public enum Result { WALKING, TELEPORTED, SKIPPED }

    /**
     * Fait marcher le PNJ vers {@code target} si c'est possible sans charger de chunk ; le
     * téléporte si la cible est trop loin pour un chemin court ; sinon ne fait rien (on
     * réessaiera au prochain passage).
     *
     * @param teleportIfFar téléporter quand la cible dépasse la portée maximale
     */
    public static Result walkTo(NPC npc, Location target, float speed, boolean teleportIfFar) {
        Entity body = npc.getEntity();
        if (body == null || target == null || target.getWorld() == null) return Result.SKIPPED;
        Location from = body.getLocation();
        if (!from.getWorld().equals(target.getWorld())) return Result.SKIPPED;
        if (!isLoaded(target)) return Result.SKIPPED;

        double distance = from.distance(target);
        float wanted = (float) Math.max(MIN_RANGE, distance + MARGIN);
        if (wanted > MAX_RANGE) {
            if (!teleportIfFar) return Result.SKIPPED;
            npc.getNavigator().cancelNavigation();
            npc.teleport(target, PlayerTeleportEvent.TeleportCause.PLUGIN);
            return Result.TELEPORTED;
        }
        float range = loadedRange(from, wanted);
        if (range < MIN_RANGE || range + 1e-3 < distance) return Result.SKIPPED; // zone pas entièrement chargée

        NavigatorParameters params = npc.getNavigator().getDefaultParameters();
        params.speedModifier(speed).range(range).useNewPathfinder(true);
        npc.getNavigator().setTarget(target);
        return Result.WALKING;
    }

    /**
     * Plus grande portée ≤ {@code wanted} dont le carré de chunks autour de {@code center} est
     * entièrement chargé (0 si même la portée minimale ne l'est pas).
     */
    public static float loadedRange(Location center, float wanted) {
        float range = Math.min(MAX_RANGE, wanted);
        while (range >= MIN_RANGE) {
            if (squareLoaded(center, range)) return range;
            range -= 8f;
        }
        return 0f;
    }

    private static boolean squareLoaded(Location center, float range) {
        World world = center.getWorld();
        int minX = (int) Math.floor(center.getX() - range) >> 4, maxX = (int) Math.floor(center.getX() + range) >> 4;
        int minZ = (int) Math.floor(center.getZ() - range) >> 4, maxZ = (int) Math.floor(center.getZ() + range) >> 4;
        for (int cx = minX; cx <= maxX; cx++) {
            for (int cz = minZ; cz <= maxZ; cz++) {
                if (!world.isChunkLoaded(cx, cz)) return false;
            }
        }
        return true;
    }

    public static boolean isLoaded(Location loc) {
        return loc.getWorld().isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4);
    }
}
