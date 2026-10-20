package com.citycore.companion.journeymap;

import com.citycore.companion.CityCoreCompanion;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Entrée du module JourneyMap, appelée seulement quand JourneyMap est installé : c'est ici, et
 * seulement ici, que la classe qui utilise l'API de JourneyMap ({@link MapSync}) est nommée. Sans
 * JourneyMap, cette classe n'est jamais chargée (et {@link CityCoreJmPlugin} n'est cherchée que par
 * JourneyMap lui-même).
 *
 * Être installé ne suffit pas : l'API serveur v2 (tracés envoyés par le serveur) n'existe qu'à
 * partir de JourneyMap 6.0.5. Une version plus ancienne (ex. 6.0.0-beta.55) n'a pas ces classes :
 * enregistrer {@link MapSync} ferait alors planter le démarrage du serveur. On vérifie donc leur
 * présence avant, et le module se met simplement en veille si elles manquent.
 */
public final class JourneyMapModule {

    /** Classes de l'API v2 utilisées par le module (toutes absentes avant JourneyMap 6.0.5). */
    private static final String[] API_CLASSES = {
            "journeymap.api.v2.common.JourneyMapPlugin",
            "journeymap.api.v2.server.IServerAPI",
            "journeymap.api.v2.server.IServerPlugin",
            "journeymap.api.v2.server.overlay.OverlayPoints",
            "journeymap.api.v2.server.overlay.OverlayPolygon",
            "journeymap.api.v2.server.overlay.OverlayShapeProps",
            "journeymap.api.v2.server.overlay.ServerPolygon",
    };

    private JourneyMapModule() {
    }

    public static void start() {
        String missing = missingApiClass();
        if (missing != null) {
            CityCoreCompanion.LOGGER.warn("JourneyMap {} n'a pas l'API serveur v2 ({} introuvable) : module de la "
                    + "carte éteint. Installez JourneyMap 6.0.5 ou plus (6.0.9 conseillée) sur le serveur "
                    + "et chez les joueurs.", installedVersion(), missing);
            return;
        }
        try {
            NeoForge.EVENT_BUS.register(new MapSync());
        } catch (LinkageError e) {
            // API présente mais incompatible (autre version) : la carte s'éteint, le serveur continue
            CityCoreCompanion.LOGGER.warn("JourneyMap {} incompatible avec le module de la carte ({}) : module "
                    + "éteint, le serveur continue normalement.", installedVersion(), e.toString());
            return;
        }
        CityCoreCompanion.LOGGER.info("JourneyMap {} présent : les parcelles de la ville seront envoyées aux "
                + "cartes des joueurs", installedVersion());
    }

    /** Première classe de l'API v2 absente, ou null si toutes sont là (aucune n'est initialisée). */
    private static String missingApiClass() {
        ClassLoader loader = JourneyMapModule.class.getClassLoader();
        for (String name : API_CLASSES) {
            try {
                Class.forName(name, false, loader);
            } catch (ClassNotFoundException | LinkageError e) {
                return name;
            }
        }
        return null;
    }

    /** Version de JourneyMap installée, pour le journal. */
    private static String installedVersion() {
        return ModList.get().getModContainerById("journeymap")
                .map(c -> c.getModInfo().getVersion().toString())
                .orElse("?");
    }
}
