package com.citycore.inhabitant;

import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

import java.util.List;

/**
 * Front d'un siège (phase 4) : le côté attaqué de la ville, où la garde se met en position dès
 * l'alerte et pendant les répits entre deux vagues.
 *
 * @param rally   point de ralliement, dans la ville au bord attaqué
 * @param enemy   point d'apparition central des assaillants (hors de la ville) : les gardes lui font face
 * @param melee   postes des gardes au contact, en première ligne (du centre vers les ailes)
 * @param ranged  postes des archers, quelques blocs en retrait
 * @param across  direction (unitaire, horizontale) le long de la lisière attaquée : sert à situer
 *                chaque poste et chaque point d'entrée sur la largeur du front (secteurs)
 * @param entries points d'entrée des assaillants, répartis sur la largeur du front (de gauche à droite)
 */
public record SiegeFront(Location rally, Location enemy, List<Location> melee, List<Location> ranged,
                         Vector across, List<Location> entries) {

    /** Position d'un point sur la largeur du front (blocs, 0 = face au ralliement). */
    public double lateral(Location at) {
        return (at.getX() - rally.getX()) * across.getX() + (at.getZ() - rally.getZ()) * across.getZ();
    }

    /**
     * Escouade d'assaillants vue par la garde (relevé du moment) : chaque groupe de gardes va à la
     * rencontre de la plus proche de son secteur.
     *
     * @param id      numéro de l'escouade dans la vague (stable tant qu'elle existe)
     * @param center  centre de l'escouade
     * @param members assaillants vivants de l'escouade
     */
    public record Band(int id, Location center, List<LivingEntity> members) {}
}
