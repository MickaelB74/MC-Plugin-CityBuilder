package com.citycore.raid;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Une cible du catalogue (raids.yml) : une structure à prendre, ou une ville composée de
 * plusieurs structures (village agrandi, tours, siège du pouvoir).
 *
 * @param key        clé dans raids.yml (« commanderie-du-temple »)
 * @param name       nom affiché
 * @param type       type de cible
 * @param level      niveau de difficulté (1 à 7)
 * @param terrain    terrain où la structure naît (indicatif ; nether ou end : autre monde des raids)
 * @param pieces     structures posées : la pièce « centre » d'abord
 * @param size       taille de la pièce principale (« 66×36×80 »), vide si inconnue
 * @param occupants  occupants lus dans les gabarits (texte indicatif)
 * @param defenders  provenance des défenseurs
 * @param objectives objectifs (déduits du type et du niveau si raids.yml n'en donne pas)
 * @param bonuses    bonus possibles (epargner, sans-perte, eclair)
 * @param mods       mods requis (identifiants de mod-integrations), d'après les espaces de noms
 * @param underground repaire enfoui (camp en surface)
 * @param floating   structure posée dans le ciel
 * @param time       heure imposée (null = celle du monde)
 * @param city       plan d'une ville fortifiée (null pour les autres cibles)
 * @param enabled    false : retirée du catalogue dans raids.yml (active: false)
 */
public record RaidTarget(String key, String name, Type type, int level, List<String> terrain,
                         List<Piece> pieces, String size, String occupants, Defenders defenders,
                         List<String> objectives, List<String> bonuses, Set<String> mods,
                         boolean underground, boolean floating, Long time, City city, boolean enabled) {

    /** Terrains d'un autre monde des raids (phase R9). */
    public static final String NETHER = "nether";
    public static final String END    = "end";

    /** Types de cibles (PLAN_RAIDS §4.2). */
    public enum Type {
        CAMP("camp", "Camp", "⛺"),
        AVANT_POSTE("avant-poste", "Avant-poste", "🗼"),
        VILLAGE("village", "Village", "🏘"),
        VILLE("ville", "Ville", "🏙"),
        FORT("fort", "Fort", "🏰"),
        CHATEAU("chateau", "Château", "🏯"),
        ABORDAGE("abordage", "Abordage", "⚓"),
        REPAIRE("repaire", "Repaire", "🕸");

        public final String key;
        public final String label;
        public final String icon;

        Type(String key, String label, String icon) {
            this.key   = key;
            this.label = label;
            this.icon  = icon;
        }

        public static Type parse(String s) {
            if (s == null) return null;
            String k = s.trim().toLowerCase(Locale.ROOT).replace('é', 'e').replace('â', 'a').replace('_', '-');
            for (Type t : values()) if (t.key.equals(k)) return t;
            return null;
        }
    }

    /** Une structure posée et son rôle (centre, tour, siege). */
    public record Piece(String id, String role) {}

    /**
     * Provenance des défenseurs (§5.3).
     *
     * @param source     gabarit (occupants posés avec la structure), armee (soldats des factions des
     *                   sièges), creatures (identifiants de {@code creatures})
     * @param complement le plugin complète jusqu'au minimum du niveau
     * @param creatures  créatures ajoutées (source creatures, ou complément)
     * @param replace    occupants d'origine retirés et remplacés
     * @param master     maître du repaire à abattre (null = aucun)
     * @param chief      chef à abattre quand c'est une créature précise (null = chef du plugin)
     */
    public record Defenders(String source, boolean complement, List<String> creatures, boolean replace,
                            String master, String chief) {}

    /**
     * Ville fortifiée (§7.8, refaite sans /place) : un village né de la génération, entouré par le
     * plugin de tours d'une faction et du siège du pouvoir (gabarits des mods).
     *
     * @param villages villages possibles (structures qui naissent), un au hasard parmi ceux du monde
     * @param tower    tour de la faction (gabarit)
     * @param towers   nombre de tours (3 au plus)
     * @param seat     siège du pouvoir (gabarit), du côté opposé au camp
     */
    public record City(List<String> villages, String tower, int towers, String seat) {}

    /** Structure principale (celle qui se pose au centre). */
    public String mainStructure() {
        return pieces.isEmpty() ? null : pieces.get(0).id();
    }

    /** Le terrain ne compte que le Nether ou l'End : cible d'un monde des raids à part. */
    public boolean otherDimension() {
        return !terrain.isEmpty() && terrain.stream().allMatch(t -> t.equals(NETHER) || t.equals(END));
    }
}
