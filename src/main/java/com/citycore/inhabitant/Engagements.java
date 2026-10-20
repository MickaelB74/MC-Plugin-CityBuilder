package com.citycore.inhabitant;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Mêlées réparties (sièges) : combien de combattants d'un camp sont au contact de chaque
 * adversaire, et choix d'une cible qui évite de s'entasser à plus de « cap » sur le même.
 *
 * Un décompte par camp (garde, armée ennemie, monstres), refait à chaque passage à partir des
 * cibles en cours, puis tenu à jour quand un combattant change de cible : jamais de recherche
 * croisée de tous contre tous. Seuls les combattants au contact comptent (les tireurs non).
 */
public final class Engagements {

    /** Pénalité (blocs², ~3 blocs) par combattant déjà au contact : on préfère une cible moins entourée. */
    private static final double CROWD = 9;
    /** Bonus (blocs²) de la cible actuelle : on n'en change pas pour un écart de quelques blocs. */
    private static final double STICKY = 12;

    private final Map<UUID, Integer> melee = new HashMap<>();
    private final Location scratch = new Location(null, 0, 0, 0); // position relue sans allocation

    public void clear() {
        melee.clear();
    }

    public void add(LivingEntity target) {
        if (target != null) melee.merge(target.getUniqueId(), 1, Integer::sum);
    }

    public void remove(LivingEntity target) {
        if (target == null) return;
        melee.computeIfPresent(target.getUniqueId(), (k, n) -> n <= 1 ? null : n - 1);
    }

    /** Combattants au contact de target. */
    public int count(LivingEntity target) {
        return target == null ? 0 : melee.getOrDefault(target.getUniqueId(), 0);
    }

    /** Remplace la cible comptée d'un combattant (ancienne retirée, nouvelle ajoutée). */
    public void swap(LivingEntity from, LivingEntity to) {
        if (from == to) return;
        remove(from);
        add(to);
    }

    /**
     * Meilleure cible pour un combattant en from : la plus proche à moins de radius blocs, parmi
     * celles qui ont moins de cap combattants au contact (cap ≤ 0 : pas de limite, pour un tireur),
     * un peu moins attirante si elle est déjà entourée, et nettement moins si elle est hors de son
     * secteur (au-delà de sectorRadius blocs de sector, si sector n'est pas null). La cible
     * actuelle (current) ne compte pas son propre combattant et garde un léger avantage.
     *
     * @return la cible choisie, ou null si aucune ne convient (toutes trop loin ou trop entourées)
     */
    public LivingEntity pick(Location from, Iterable<? extends LivingEntity> candidates, double radius, int cap,
                             LivingEntity current, Location sector, double sectorRadius) {
        World w = from.getWorld();
        double r2 = radius * radius, s2 = sectorRadius * sectorRadius;
        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;
        for (LivingEntity c : candidates) {
            if (c == null || c.isDead() || !c.isValid() || !c.getWorld().equals(w)) continue;
            c.getLocation(scratch);
            double d2 = sq(scratch.getX() - from.getX()) + sq(scratch.getY() - from.getY()) + sq(scratch.getZ() - from.getZ());
            if (d2 > r2) continue;
            int around = count(c) - (c == current ? 1 : 0);
            if (cap > 0 && around >= cap) continue;
            double score = d2 + around * CROWD;
            if (sector != null) {
                double ds = sq(scratch.getX() - sector.getX()) + sq(scratch.getZ() - sector.getZ());
                if (ds > s2) score += ds - s2; // hors de son secteur : seulement faute de mieux
            }
            if (c == current) score -= STICKY;
            if (score < bestScore) { bestScore = score; best = c; }
        }
        return best;
    }

    /** current est-elle encore une cible acceptable (pas plus de cap autres combattants au contact) ? */
    public boolean acceptable(LivingEntity current, int cap) {
        return cap <= 0 || count(current) - 1 < cap;
    }

    private static double sq(double v) {
        return v * v;
    }
}
