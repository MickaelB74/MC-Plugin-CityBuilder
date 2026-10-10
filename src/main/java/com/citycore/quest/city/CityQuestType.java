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

    /**
     * Recruter un PNJ : le trouver (1), le ramener en ville (2), l'installer dans
     * un bâtiment (3). La quête est terminée à l'étape 3.
     */
    FIND_NPC,

    /** Livrer N items au maire (consommés), ou les réquisitionner dans les stocks des PNJ */
    DELIVER_ITEMS,

    /** Un PNJ doit atteindre un niveau (rang) donné */
    NPC_LEVEL,

    /** Le stock d'un PNJ doit atteindre N unités d'un matériau (réserves) */
    STOCK_LEVEL,

    /** Tuer N créatures d'un type (ou N monstres quelconques), tous joueurs confondus */
    KILL_MOBS
}
