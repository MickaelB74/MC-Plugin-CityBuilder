package com.citycore.companion;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Réglages du mod : fichier {@code config/citycore_companion.toml} du serveur (créé au premier
 * démarrage, relu automatiquement quand il est modifié). Une section par module : {@code epicfight}
 * (animations des PNJ), {@code journeymap} (parcelles de la ville sur la carte) et {@code discord}
 * (API interrogée par le bot Discord).
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
    public static final ModConfigSpec.IntValue     RESET_SECONDS;
    public static final ModConfigSpec.BooleanValue FORCE_EPIC_MODE;
    public static final ModConfigSpec.BooleanValue LOG;

    /* ── JourneyMap ── */
    public static final ModConfigSpec.BooleanValue JM_ENABLED;
    public static final ModConfigSpec.ConfigValue<String> JM_FILE;
    public static final ModConfigSpec.IntValue     JM_CHECK_SECONDS;
    public static final ModConfigSpec.IntValue     JM_RESEND_SECONDS;
    public static final ModConfigSpec.BooleanValue JM_LOG;

    /* ── Discord ── */
    public static final ModConfigSpec.BooleanValue DC_ENABLED;
    public static final ModConfigSpec.ConfigValue<String> DC_ADDRESS;
    public static final ModConfigSpec.IntValue     DC_PORT;
    public static final ModConfigSpec.ConfigValue<String> DC_TOKEN;
    public static final ModConfigSpec.ConfigValue<String> DC_FOLDER;
    public static final ModConfigSpec.ConfigValue<String> DC_SERVER_NAME;
    public static final ModConfigSpec.BooleanValue DC_LOG;

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
        RESET_SECONDS = b.comment("Remise à zéro (secondes) de l'animation d'un PNJ immobile sans geste depuis ce délai, chez",
                        "les joueurs qui le voient : un geste resté bloqué en boucle s'arrête ; 0 = jamais")
                .defineInRange("remiseAZero", 20, 0, 600);
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

        b.comment("API du bot Discord (citycore-discord-bot) : état du serveur et de la ville, journal des",
                        "événements, messages de Discord vers le jeu. Le plugin CityCore écrit ce qui est servi",
                        "(section discord de son config.yml). Port, adresse et jeton sont lus au démarrage du serveur.")
                .push("discord");
        DC_ENABLED = b.comment("false : l'API n'est pas ouverte")
                .define("actif", true);
        DC_ADDRESS = b.comment("Adresse d'écoute : 127.0.0.1 = seulement cette machine (bot sur le même ordinateur) ;",
                        "0.0.0.0 = toutes les interfaces (bot sur une autre machine : ouvrez le port au bot seulement)")
                .define("adresse", "127.0.0.1");
        DC_PORT = b.comment("Port d'écoute de l'API")
                .defineInRange("port", 8787, 1024, 65535);
        DC_TOKEN = b.comment("Jeton exigé à chaque demande (à recopier dans la configuration du bot) ;",
                        "vide : un jeton est tiré au hasard et écrit ici au démarrage du serveur")
                .define("jeton", "");
        DC_FOLDER = b.comment("Dossier écrit par le plugin, relatif au dossier du serveur")
                .define("dossier", "plugins/CityCore/discord");
        DC_SERVER_NAME = b.comment("Nom du serveur affiché par le bot (salon vocal de statut) ; vide = celui de la liste des serveurs (MOTD)")
                .define("nomServeur", "");
        DC_LOG = b.comment("Journal détaillé (lignes lues, fichiers illisibles) : pour les tests")
                .define("journal", false);
        b.pop();

        SPEC = b.build();
    }

    private Settings() {
    }
}
