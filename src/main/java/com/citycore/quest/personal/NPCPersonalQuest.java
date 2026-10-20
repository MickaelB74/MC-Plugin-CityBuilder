package com.citycore.quest.personal;

import com.citycore.integration.ModIntegrations;
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
 * @param modGate     Condition de mod : null = toujours proposé ; « tide » = seulement si le mod
 *                    est présent ; « !irons_spellbooks » = seulement s'il est absent (repli)
 * @param delivery    Objets à rapporter au personnage (récits « Rapporter… »), null sinon
 */
public record NPCPersonalQuest(
        String id,
        String displayName,
        String description,
        Material icon,
        PersonalQuestValidator validator,
        PersonalQuestDefinition.TriggerType trigger,
        int targetAmount,
        int reward,
        String modGate,
        Delivery delivery
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
        return new NPCPersonalQuest(id, displayName, description, icon, validator, trigger, 1, reward, null, null);
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
        return new NPCPersonalQuest(id, displayName, description, icon, validator, trigger, targetAmount, reward,
                null, null);
    }

    /**
     * Récit « Rapporter… » : les objets (non achetés à un PNJ, non pris dans une réserve) sont
     * remis au personnage depuis ses récits, quand le joueur les a tous sur lui.
     */
    public static NPCPersonalQuest deliver(
            String id,
            String displayName,
            String description,
            Material icon,
            Delivery delivery,
            int reward
    ) {
        return new NPCPersonalQuest(id, displayName, description, icon, (player, ctx) -> delivery.has(player),
                PersonalQuestDefinition.TriggerType.DELIVER, 1, reward, null, delivery);
    }

    /** Copie proposée seulement quand ce mod est présent (contenu facultatif). */
    public NPCPersonalQuest onlyWith(String modId) {
        return new NPCPersonalQuest(id, displayName, description, icon, validator, trigger, targetAmount, reward,
                modId, delivery);
    }

    /** Copie proposée seulement quand ce mod est absent (contenu de repli). */
    public NPCPersonalQuest onlyWithout(String modId) {
        return new NPCPersonalQuest(id, displayName, description, icon, validator, trigger, targetAmount, reward,
                "!" + modId, delivery);
    }

    /** Le récit est-il proposé sur ce serveur (condition de mod remplie) ? */
    public boolean offered() {
        return ModIntegrations.gate(modGate);
    }

    /** Récit « Rapporter… » ? */
    public boolean isDelivery() {
        return delivery != null;
    }
}
