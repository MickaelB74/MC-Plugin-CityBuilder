package com.citycore.npc;

public enum NPCState {
    WANDERER,  // Spawné, pas encore dans la ville
    ARRIVED,   // Entré dans la ville au moins une fois
    ASSIGNED,  // Assigné à un bâtiment
    /**
     * Installé, puis son bâtiment a été supprimé : il attend un nouveau logis. Son écran de
     * gestion est fermé et ses quêtes sont en pause, mais rien n'est perdu (rang, stock, quêtes,
     * étape « installé » du recrutement). Un nouveau logis le repasse en ASSIGNED.
     */
    HOMELESS;

    /** A déjà été installé : l'étape 3 du recrutement reste acquise, même sans logis aujourd'hui. */
    public boolean wasInstalled() {
        return this == ASSIGNED || this == HOMELESS;
    }

    /** En ville sans logis (arrivé, ou installé dont le bâtiment a disparu) : il faut lui en choisir un. */
    public boolean needsHome() {
        return this == ARRIVED || this == HOMELESS;
    }
}
