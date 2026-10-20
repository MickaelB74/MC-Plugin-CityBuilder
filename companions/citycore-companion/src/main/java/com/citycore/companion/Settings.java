package com.citycore.companion;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Réglages du mod : fichier {@code config/citycore_companion.toml} du serveur (créé au premier
 * démarrage, relu automatiquement quand il est modifié). Une section par module : {@code epicfight}
 * (animations des PNJ) et {@code journeymap} (parcelles de la ville sur la carte).
 *
 * Classe sans dépendance à Epic Fight ni à JourneyMap : elle est chargée même quand ces mods
 * manquent.
 */
public final class Settings {

    public static final ModConfigSpec SPEC;

    /* ── Epic Fight ── */
    public static final ModConfigSpec.BooleanValue EF_ENABLED;
    public static final ModConfigSpec.DoubleValue  WALK_MIN;
    public static final ModConfigSpec.DoubleValue  RUN_MIN;
    public static final ModConfigSpec.BooleanValue ATTACKS;
    public static final ModConfigSpec.BooleanValue SHOTS;
    public static final ModConfigSpec.BooleanValue FORCE_EPIC_MODE;
    public static final ModConfigSpec.BooleanValue LOG;

    /* ── JourneyMap ── */
    public static final ModConfigSpec.BooleanValue JM_ENABLED;
    public static final ModConfigSpec.ConfigValue<String> JM_FILE;
    public static final ModConfigSpec.IntValue     JM_CHECK_SECONDS;
    public static final ModConfigSpec.IntValue     JM_RESEND_SECONDS;
    public static final ModConfigSpec.BooleanValue JM_LOG;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        b.comment("Animations Epic Fight des PNJ de CityCore (module actif seulement si Epic Fight est installé)")
                .push("epicfight");
        EF_ENABLED = b.comment("false : module éteint (les PNJ gardent le comportement d'Epic Fight sans le mod)")
                .define("actif", true);
        b.comment("Déplacements des PNJ (vitesses en blocs par tick ; 1 bloc/tick = 20 blocs/s)").push("deplacements");
        WALK_MIN = b.comment("Vitesse à partir de laquelle un PNJ marche (0,02 = 0,4 bloc/s)")
                .defineInRange("marche", 0.02, 0.001, 1.0);
        RUN_MIN = b.comment("Vitesse à partir de laquelle il court (animation de course) ; 0 = jamais")
                .defineInRange("course", 0.24, 0.0, 2.0);
        b.pop();
        b.comment("Gestes signalés par le plugin CityCore").push("gestes");
        ATTACKS = b.comment("Coups des gardes et des soldats ennemis : enchaînements d'Epic Fight selon l'arme")
                .define("attaques", true);
        SHOTS = b.comment("Tirs des archers et arbalétriers : animation de tir de l'arme tenue")
                .define("tirs", true);
        b.pop();
        FORCE_EPIC_MODE = b.comment("Annonce les PNJ en mode combat d'Epic Fight (modèle et animations d'Epic Fight)")
                .define("modeCombat", true);
        LOG = b.comment("Journal détaillé (PNJ trouvés, gestes joués) : pour les tests")
                .define("journal", false);
        b.pop();

        b.comment("Parcelles de la ville sur la carte JourneyMap des joueurs (module actif seulement si JourneyMap",
                        "est installé sur le serveur). Couleurs, opacités et quadrillage : section carte du config.yml",
                        "du plugin CityCore, qui écrit le fichier lu ici.")
                .push("journeymap");
        JM_ENABLED = b.comment("false : la ville disparaît des cartes et plus rien n'est envoyé")
                .define("actif", true);
        JM_FILE = b.comment("Fichier écrit par le plugin, relatif au dossier du serveur")
                .define("fichier", "plugins/CityCore/carte.json");
        JM_CHECK_SECONDS = b.comment("Intervalle (secondes) entre deux contrôles du fichier : la carte suit les claims dans ce délai")
                .defineInRange("verification", 2, 1, 60);
        JM_RESEND_SECONDS = b.comment("Renvoi de sécurité à tous les joueurs (secondes) : JourneyMap remplace la zone, sans doublon ; 0 = jamais")
                .defineInRange("renvoi", 120, 0, 3600);
        JM_LOG = b.comment("Journal détaillé (lectures du fichier, envois par joueur) : pour les tests")
                .define("journal", false);
        b.pop();

        SPEC = b.build();
    }

    private Settings() {
    }
}
