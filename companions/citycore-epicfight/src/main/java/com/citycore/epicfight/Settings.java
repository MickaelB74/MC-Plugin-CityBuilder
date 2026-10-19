package com.citycore.epicfight;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Réglages du mod : fichier {@code config/citycore_epicfight.toml} du serveur (créé au premier
 * démarrage, relu automatiquement quand il est modifié).
 */
final class Settings {

    static final ModConfigSpec SPEC;

    static final ModConfigSpec.DoubleValue  WALK_MIN;
    static final ModConfigSpec.DoubleValue  RUN_MIN;
    static final ModConfigSpec.BooleanValue ATTACKS;
    static final ModConfigSpec.BooleanValue SHOTS;
    static final ModConfigSpec.BooleanValue FORCE_EPIC_MODE;
    static final ModConfigSpec.BooleanValue LOG;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
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
        SPEC = b.build();
    }

    private Settings() {
    }
}
