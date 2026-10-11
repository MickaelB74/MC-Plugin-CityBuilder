package com.citycore.building;

import com.citycore.npc.CityNPC;

/**
 * Bâtiment de la ville : une zone (x/z, et hauteur y_min → y_max) avec un type.
 *
 * @param npcTag      artisan installé (null = aucun)
 * @param type        type du bâtiment (phase 1 du plan « Valmont vivante »)
 * @param reservedFor artisan pour qui l'atelier est réservé (null = atelier libre) ;
 *                    renseigné aussi dès qu'un artisan s'y installe
 * @param yMin        bas de la zone (sol), null si pas encore estimé
 * @param yMax        haut de la zone (toit), null si pas encore estimé
 * @param level       niveau de construction (calculé à partir de la phase 2 ; 0 = non inspecté)
 * @param refY        point de définition (Y le plus bas des coins cliqués, au sol), null si inconnu ;
 *                    la zone va de batiments.profondeur blocs dessous à batiments.hauteur au-dessus
 */
public record Building(
        int id,
        String name,
        String world,
        int x1, int z1,
        int x2, int z2,
        String npcTag,
        Double npcX,
        Double npcY,
        Double npcZ,
        Float npcYaw,
        BuildingType type,
        String reservedFor,
        Integer yMin,
        Integer yMax,
        int level,
        Integer refY
) {
    public boolean hasNpcPoint() {
        return npcX != null && npcY != null && npcZ != null;
    }

    public String npcDisplayTag() {
        return npcTag != null ? npcTag.replace("citycore_", "") : "Aucun";
    }

    public boolean hasHeight() {
        return yMin != null && yMax != null;
    }

    /** Artisan pour qui l'atelier est réservé, ou null. */
    public CityNPC reservedNpc() {
        return reservedFor == null ? null : CityNPC.fromTag(reservedFor);
    }

    /** Cet artisan peut-il s'installer ici ? (atelier libre, ou réservé à lui) */
    public boolean acceptsArtisan(CityNPC npc) {
        return type == BuildingType.ATELIER && npcTag == null
                && (reservedFor == null || reservedFor.equals(npc.tag));
    }

    /** Ligne de bulle : « Type : Atelier — Thorvald » / « Atelier libre ». */
    public String typeLine() {
        if (type != BuildingType.ATELIER) return "§7Type : " + type.coloredLabel();
        CityNPC owner = reservedNpc();
        return "§7Type : " + type.coloredLabel() + (owner != null ? " §7de " + owner.displayName : " §8(libre)");
    }

    /** Ligne de bulle sur le niveau enregistré à la dernière inspection (phase 2). */
    public String levelLine() {
        return level > 0 ? "§7Niveau : " + Inspection.stars(level) + " §8(" + level + "/5)"
                : "§7Niveau : §cincomplet §8(ou pas encore inspecté)";
    }

    /** Ligne de bulle sur la hauteur (« Hauteur : Y 63 → 75 »). */
    public String heightLine() {
        return hasHeight() ? "§7Hauteur : §fY " + yMin + " §7→ §f" + yMax + (refY != null ? " §8(sol Y " + refY + ")" : "")
                : "§7Hauteur : §8non estimée";
    }
}
