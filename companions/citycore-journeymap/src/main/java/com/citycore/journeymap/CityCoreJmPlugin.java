package com.citycore.journeymap;

import journeymap.api.v2.common.JourneyMapPlugin;
import journeymap.api.v2.server.IServerAPI;
import journeymap.api.v2.server.IServerPlugin;

/**
 * Point d'entrée côté JourneyMap : JourneyMap trouve cette classe grâce à l'annotation et lui
 * donne son API serveur au chargement des mods. Seule classe touchée par JourneyMap ; elle n'est
 * jamais chargée si JourneyMap est absent.
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
        CityCoreJourneyMap.LOGGER.info("API serveur de JourneyMap reçue : bordures de la ville activées");
    }

    @Override
    public String getModId() {
        return CityCoreJourneyMap.MOD_ID;
    }

    static IServerAPI api() {
        return api;
    }
}
