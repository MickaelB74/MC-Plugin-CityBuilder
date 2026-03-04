package com.citycore.quest.personal;

import org.bukkit.Material;

/**
 * Définition d'une quête personnelle attachée à un NPC.
 *
 * Calquée sur CityQuest mais orientée joueur individuel :
 * chaque joueur a sa propre progression.
 *
 * @param id          Identifiant unique global (ex : "jack_reach_summit")
 * @param displayName Nom affiché en GUI (avec codes couleur)
 * @param description Description courte
 * @param icon        Icône dans l'inventaire
 * @param validator   Condition de validation (lambda)
 * @param trigger     Événement déclencheur
 * @param targetAmount Nombre de fois à valider (généralement 1)
 * @param reward      Coins donnés au joueur à la complétion
 */
public record NPCPersonalQuest(
        String id,
        String displayName,
        String description,
        Material icon,
        PersonalQuestValidator validator,
        PersonalQuestDefinition.TriggerType trigger,
        int targetAmount,
        int reward
) {
    /** Factory standard — la plupart des quêtes perso sont one-shot */
    public static NPCPersonalQuest of(
            String id,
            String displayName,
            String description,
            Material icon,
            PersonalQuestDefinition.TriggerType trigger,
            PersonalQuestValidator validator,
            int reward
    ) {
        return new NPCPersonalQuest(id, displayName, description, icon, validator, trigger, 1, reward);
    }

    /** Factory avec targetAmount personnalisé (ex : explorer 5 chunks) */
    public static NPCPersonalQuest withAmount(
            String id,
            String displayName,
            String description,
            Material icon,
            PersonalQuestDefinition.TriggerType trigger,
            PersonalQuestValidator validator,
            int targetAmount,
            int reward
    ) {
        return new NPCPersonalQuest(id, displayName, description, icon, validator, trigger, targetAmount, reward);
    }
}