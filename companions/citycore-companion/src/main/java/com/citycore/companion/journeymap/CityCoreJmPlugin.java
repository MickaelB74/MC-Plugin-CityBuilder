package com.citycore.companion.journeymap;

import com.citycore.companion.CityCoreCompanion;
import journeymap.api.v2.common.JourneyMapPlugin;
import journeymap.api.v2.server.IServerAPI;
import journeymap.api.v2.server.IServerPlugin;

/**
 * Point d'entrée côté JourneyMap : JourneyMap trouve cette classe grâce à l'annotation et lui
 * donne son API serveur au chargement des mods. Jamais chargée si JourneyMap est absent (seul
 * JourneyMap la cherche).
 */
@JourneyMapPlugin(apiVersion = "2.0.0")
public final class CityCoreJmPlugin implements IServerPlugin {

    /** API serveur de JourneyMap (null tant que JourneyMap ne l'a pas donnée). */
    private static volatile IServerAPI api;

    /** Constructeur sans argument exigé par JourneyMap. */
    public CityCoreJmPlugin() {
    }

    @Override
    public void initialize(IServerAPI jmServerApi) {
        api = jmServerApi;
        CityCoreCompanion.LOGGER.info("API serveur de JourneyMap reçue : la ville sera affichée sur les cartes");
    }

    @Override
    public String getModId() {
        return CityCoreCompanion.MOD_ID;
    }

    static IServerAPI api() {
        return api;
    }
}
