package com.citycore.quest.personal;

import com.citycore.building.Building;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;

import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Ce que les récits ont besoin de savoir de la ville, sans dépendre de ses gestionnaires : les
 * récits sont déclarés dans des énumérations (CityNPC), bien avant la création des gestionnaires.
 * Branché au démarrage par CityCore ; sans branchement, chaque question a une réponse neutre
 * (pas de siège, pas de bâtiment, ville inconnue).
 */
public final class QuestHooks {

    private QuestHooks() {}

    private static volatile Predicate<Entity> siegeEnemy = e -> false;
    private static volatile BiFunction<World, Location, Building> buildingAt = (w, l) -> null;
    private static volatile Function<World, double[]> cityCenter = w -> null;

    /** Assaillant d'un siège en cours (monstre de vague ou soldat de l'armée ennemie). */
    public static void setSiegeEnemy(Predicate<Entity> predicate) {
        siegeEnemy = predicate == null ? e -> false : predicate;
    }

    /** Bâtiment de la ville à un endroit (zone x/z et hauteur), ou null. */
    public static void setBuildingAt(BiFunction<World, Location, Building> lookup) {
        buildingAt = lookup == null ? (w, l) -> null : lookup;
    }

    /** Centre de la ville (x, z) dans un monde, ou null si la ville n'y est pas. */
    public static void setCityCenter(Function<World, double[]> center) {
        cityCenter = center == null ? w -> null : center;
    }

    public static boolean isSiegeEnemy(Entity e) {
        try {
            return e != null && siegeEnemy.test(e);
        } catch (RuntimeException ex) {
            return false;
        }
    }

    public static Building buildingAt(Location loc) {
        if (loc == null || loc.getWorld() == null) return null;
        try {
            return buildingAt.apply(loc.getWorld(), loc);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    /** Distance horizontale au centre de la ville, ou -1 si elle est inconnue (autre monde, pas de ville). */
    public static double distanceToCity(Location loc) {
        if (loc == null || loc.getWorld() == null) return -1;
        double[] c;
        try {
            c = cityCenter.apply(loc.getWorld());
        } catch (RuntimeException ex) {
            return -1;
        }
        if (c == null) return -1;
        double dx = loc.getX() - c[0], dz = loc.getZ() - c[1];
        return Math.sqrt(dx * dx + dz * dz);
    }
}
