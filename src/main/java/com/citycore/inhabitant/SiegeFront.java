package com.citycore.inhabitant;

import org.bukkit.Location;

import java.util.List;

/**
 * Front d'un siège (phase 4) : le côté attaqué de la ville, où la garde se met en position dès
 * l'alerte et pendant les répits entre deux vagues.
 *
 * @param rally  point de ralliement, dans la ville au bord attaqué
 * @param enemy  point d'apparition des assaillants (hors de la ville) : les gardes lui font face
 * @param melee  postes des gardes au contact, en première ligne (du centre vers les ailes)
 * @param ranged postes des archers, quelques blocs en retrait
 */
public record SiegeFront(Location rally, Location enemy, List<Location> melee, List<Location> ranged) {}
