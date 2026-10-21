package com.citycore.raid;

import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.MemoryNPCDataStore;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.npc.NPCRegistry;
import net.citizensnpcs.trait.LookClose;
import net.citizensnpcs.trait.SkinTrait;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;

/**
 * Héraut du camp (PLAN_RAIDS §2.8) : PNJ Citizens d'un registre en mémoire, jamais enregistré
 * (rien ne reste après un arrêt du serveur). Il propose de battre en retraite pendant le raid, de
 * rentrer à Valmont pendant le bilan.
 */
final class RaidHerald {

    static final String NAME = "§6Héraut de Valmont";

    private NPCRegistry registry;
    private NPC npc;

    /**
     * Fait apparaître le héraut (un seul à la fois).
     *
     * @param skin {value, signature} de son skin (raids.camp.heraut-skin), ou null
     */
    void spawn(Location at, String[] skin) {
        remove();
        if (registry == null) registry = CitizensAPI.createAnonymousNPCRegistry(new MemoryNPCDataStore());
        npc = registry.createNPC(EntityType.PLAYER, NAME);
        npc.setProtected(true);
        npc.getOrAddTrait(LookClose.class).lookClose(true);
        // Skin avant l'apparition (sinon le PNJ apparaît d'abord avec un skin par défaut)
        SkinTrait trait = npc.getOrAddTrait(SkinTrait.class);
        if (skin != null) trait.setSkinPersistent("citycore_heraut", skin[1], skin[0]);
        else trait.setFetchDefaultSkin(false);
        com.citycore.inhabitant.NpcGestures.prepare(npc); // pas de balancement de bras à l'apparition (Epic Fight)
        npc.spawn(at);
    }

    /** Ce PNJ est-il le héraut ? */
    boolean is(NPC other) {
        return npc != null && other != null && other.getId() == npc.getId() && other.getOwningRegistry() == registry;
    }

    void remove() {
        if (npc != null) {
            npc.destroy();
            npc = null;
        }
    }
}
