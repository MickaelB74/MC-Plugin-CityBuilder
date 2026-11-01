package com.citycore.npc;

import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.trait.HologramTrait;

import java.util.List;

/**
 * Noms des PNJ de la cité (artisans, Alderic, habitants, gardes, héraut des raids) sur fond noir,
 * comme les titres des artisans et les minuteurs de travail.
 *
 * L'étiquette de nom de Citizens (fond à peine visible) est masquée ; le nom devient la première
 * ligne de l'hologramme, tout en bas, à la place de l'étiquette : il s'affiche donc exactement
 * comme les titres. Les soldats ennemis gardent l'étiquette de Citizens.
 */
public final class NpcNameplates {

    private NpcNameplates() {}

    /** Nom sur fond noir pour ce PNJ (avant ou après son apparition ; sans effet s'il est déjà en place). */
    public static void apply(NPC npc) {
        npc.data().setPersistent(NPC.Metadata.NAMEPLATE_VISIBLE, false);
        HologramTrait hologram = npc.getOrAddTrait(HologramTrait.class);
        List<String> lines = hologram.getLines();
        if (lines.isEmpty() || !lines.get(0).equals(npc.getRawName())) hologram.insertLine(0, npc.getRawName());
    }

    /** Vide l'hologramme d'un PNJ en gardant son nom en première ligne (à la place de clear()). */
    public static void clearKeepingName(NPC npc, HologramTrait hologram) {
        hologram.clear();
        hologram.addLine(npc.getRawName());
    }
}
