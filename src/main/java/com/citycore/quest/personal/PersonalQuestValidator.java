package com.citycore.quest.personal;

import org.bukkit.entity.Player;

/**
 * Interface fonctionnelle représentant la condition de validation
 * d'une quête personnalisée.
 *
 * Appelée à chaque TriggerType correspondant (MOVE, KILL, etc.)
 * pour déterminer si la condition est remplie.
 */
@FunctionalInterface
public interface PersonalQuestValidator {

    /**
     * @param player  Le joueur concerné
     * @param context Contexte optionnel (ex : entité tuée, item crafté…) — peut être null
     * @return true si la condition est satisfaite
     */
    boolean validate(Player player, Object context);
}