package com.citycore.quest.personal;

/**
 * Définition d'une quête personnalisée.
 *
 * Le validator est une lambda hardcodée dans {@link PersonalQuestRegistry}.
 * Seul l'id est persisté en base — le validator est relinké au chargement.
 *
 * @param id          Identifiant unique (ex : "reach_summit", "swim_ocean")
 * @param displayName Nom affiché dans la GUI
 * @param description Description courte
 * @param trigger     Type d'événement qui déclenche la vérification
 * @param validator   Condition de validation
 * @param targetAmount  Nombre de fois que la condition doit être remplie
 */
public record PersonalQuestDefinition(
        String id,
        String displayName,
        String description,
        TriggerType trigger,
        PersonalQuestValidator validator,
        int targetAmount
) {
    /**
     * Types de déclencheurs disponibles.
     * Le QuestListener écoute l'événement correspondant
     * et appelle validator.validate() pour chaque quête PERSONAL active.
     */
    public enum TriggerType {
        MOVE,        // PlayerMoveEvent (position, altitude, biome…)
        KILL,        // EntityDeathEvent (entité tuée par le joueur)
        COLLECT,     // PlayerPickupItemEvent / InventoryCloseEvent
        CRAFT,       // CraftItemEvent
        SUMMIT,      // Déclenché directement par SummitListener (event externe)
        ADVANCEMENT  // AdvancementDoneEvent — pour des conditions Minecraft natives
    }
}