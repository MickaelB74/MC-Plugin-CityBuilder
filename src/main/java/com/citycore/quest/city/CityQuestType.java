package com.citycore.quest.city;

/**
 * Types de quêtes disponibles pour la progression de la ville.
 */
public enum CityQuestType {

    /** Construire N bâtiments (count = buildingManager.getAllBuildings().size()) */
    BUILD_BUILDINGS,

    /** Déposer N coins dans la caisse (valeur cumulée en BDD) */
    DEPOSIT_COINS,

    /** Claim N chunks */
    CLAIM_CHUNKS,

    /** Trouver/ramener un NPC spécifique (non implémenté — placeholder) */
    FIND_NPC
}