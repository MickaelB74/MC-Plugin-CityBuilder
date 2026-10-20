package com.citycore.siege;

import com.citycore.util.GroundFinder;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.function.Predicate;

/**
 * Escouade d'assaillants d'un siège : quelques combattants (4 à 8) autour d'un meneur, arrivés par
 * le même point d'entrée, qui avancent ensemble sur leur propre voie du front. Chaque escouade a
 * ses objectifs successifs : percer la lisière en face de son point d'entrée, avancer dans la
 * ville en gardant une partie de son décalage, puis marcher sur la mairie. Tant qu'il n'y a
 * personne à combattre, les membres tiennent leur place dans la formation (en coin derrière le
 * meneur, les tireurs en retrait) ; le meneur attend un instant ses traînards, et ne part pas
 * seul plus loin tant que ses hommes se battent.
 *
 * Les membres ne sont pas tenus ici : à chaque passage, l'armée (ou le siège, pour les monstres)
 * regroupe ses combattants par escouade et en fait le relevé (meneur, centre, dispersion).
 */
final class Squad {

    /** Distance (blocs) à laquelle le meneur tient un objectif pour atteint. */
    private static final double REACHED = 6;
    /** Dispersion moyenne (blocs) au-delà de laquelle le meneur attend ses traînards. */
    private static final double STRAGGLE = 7;
    /** Attente maximale du meneur (ms) : il repart même si des traînards sont restés en arrière. */
    private static final long MAX_WAIT_MS = 6000;
    /** Attente maximale pendant que ses hommes se battent (ms) : une mêlée sans issue (cible hors d'atteinte) ne le fige pas. */
    private static final long MAX_FIGHT_WAIT_MS = 20000;

    final int id;
    final Location entry;               // point d'entrée (hors de la ville)
    private final List<Location> goals; // percée en face de son entrée, cœur de la ville, mairie
    private int stage;

    // Relevé du passage en cours
    LivingEntity leader;
    private double cx, cy, cz;
    private World world;
    private int size, engaged;
    private double spread;
    private long waitingSince;

    Squad(int id, Location entry, List<Location> goals) {
        this.id = id;
        this.entry = entry;
        this.goals = goals;
    }

    /**
     * Objectif du moment pour un meneur en leaderAt : le suivant dès que celui-ci est atteint ;
     * null s'il n'y en a aucun (l'appelant prend alors la mairie).
     */
    Location goal(Location leaderAt) {
        while (stage < goals.size() - 1 && near(leaderAt, goals.get(stage), REACHED)) stage++;
        return goals.isEmpty() ? null : goals.get(stage);
    }

    /** Objectif en cours (sans le faire avancer). */
    Location currentGoal() {
        return goals.isEmpty() ? null : goals.get(Math.min(stage, goals.size() - 1));
    }

    /**
     * Relevé d'un passage : meneur, centre de l'escouade, et dispersion de ceux qui sont en
     * formation (inFormation : sans cible) autour du meneur.
     */
    void survey(List<? extends LivingEntity> bodies, LivingEntity leader, Predicate<LivingEntity> inFormation) {
        this.leader = leader;
        size = bodies.size();
        engaged = 0;
        cx = cy = cz = 0;
        double far = 0;
        int counted = 0;
        Location lead = leader != null ? leader.getLocation() : null;
        for (LivingEntity b : bodies) {
            Location l = b.getLocation();
            world = l.getWorld();
            cx += l.getX();
            cy += l.getY();
            cz += l.getZ();
            if (b == leader) continue;
            if (!inFormation.test(b)) { engaged++; continue; }
            if (lead != null && l.getWorld().equals(lead.getWorld())) {
                far += Math.sqrt(sq(l.getX() - lead.getX()) + sq(l.getZ() - lead.getZ()));
                counted++;
            }
        }
        if (size > 0) { cx /= size; cy /= size; cz /= size; }
        spread = counted == 0 ? 0 : far / counted;
    }

    /** Membres présents au dernier relevé. */
    int size() {
        return size;
    }

    /** Centre de l'escouade au dernier relevé (null si elle est vide). */
    Location center() {
        return size == 0 || world == null ? null : new Location(world, cx, cy, cz);
    }

    /**
     * Le meneur (sans adversaire) doit-il s'arrêter ? Oui tant que la moitié de ses hommes au
     * moins se battent (il ne part pas seul plus loin : l'escouade tient sa mêlée ; 20 s au plus),
     * ou si ceux qui sont en formation sont trop dispersés (6 s au plus) ; ensuite il repart.
     */
    boolean shouldWait(long now) {
        boolean fighting = engaged > 0 && engaged * 2 >= size - 1;
        if (!fighting && spread <= STRAGGLE) { waitingSince = 0; return false; }
        if (waitingSince == 0) waitingSince = now;
        return now - waitingSince < (fighting ? MAX_FIGHT_WAIT_MS : MAX_WAIT_MS);
    }

    /**
     * Place d'un membre dans la formation, autour du meneur et tournée vers l'objectif : les
     * combattants au contact en coin de part et d'autre du meneur (index 0, 1, 2… alternés à
     * droite et à gauche, un peu plus en arrière à chaque rang), les tireurs 5 blocs en retrait.
     * Au sol, près de la hauteur du meneur ; null si la place n'est pas praticable (le membre
     * suit alors le meneur).
     */
    Location slot(int index, boolean ranged) {
        if (leader == null || !leader.isValid()) return null;
        Location lead = leader.getLocation();
        Location goal = currentGoal();
        Vector forward = goal != null && goal.getWorld().equals(lead.getWorld())
                ? goal.toVector().subtract(lead.toVector()).setY(0) : lead.getDirection().setY(0);
        if (forward.lengthSquared() < 1e-4) forward = new Vector(1, 0, 0);
        forward.normalize();
        Vector right = new Vector(-forward.getZ(), 0, forward.getX());
        int rank = index / 2;
        double side = index % 2 == 0 ? 1 : -1;
        double lateral = ranged ? side * (1.2 + 2.0 * rank) : side * 1.6 * (rank + 1);
        double back = ranged ? 5.0 + 1.5 * (rank / 2) : 1.2 + 0.8 * rank;
        Location at = lead.clone().add(right.multiply(lateral)).subtract(forward.multiply(back));
        World w = lead.getWorld();
        int x = at.getBlockX(), z = at.getBlockZ();
        if (!w.isChunkLoaded(x >> 4, z >> 4)) return null;
        return GroundFinder.standAt(w, x, lead.getBlockY(), z);
    }

    private static boolean near(Location a, Location b, double r) {
        return a != null && b != null && a.getWorld().equals(b.getWorld())
                && sq(a.getX() - b.getX()) + sq(a.getZ() - b.getZ()) <= r * r;
    }

    private static double sq(double v) {
        return v * v;
    }
}
