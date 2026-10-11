package com.citycore.journeymap;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

/**
 * Mod serveur « CityCore — JourneyMap » : dessine les bordures de la ville du plugin CityCore sur
 * la carte JourneyMap des joueurs (carte plein écran et minicarte).
 *
 * Le plugin écrit {@code plugins/CityCore/carte.json} ; ce mod le relit et envoie la zone à
 * chaque joueur par l'API serveur de JourneyMap. Côté joueurs, JourneyMap suffit (6.0.5 ou plus).
 */
@Mod(CityCoreJourneyMap.MOD_ID)
public final class CityCoreJourneyMap {

    public static final String MOD_ID = "citycore_journeymap";
    static final Logger LOGGER = LogUtils.getLogger();

    public CityCoreJourneyMap(IEventBus modBus) {
        // Sans JourneyMap, rien à faire : aucune classe de JourneyMap n'est chargée
        if (!ModList.get().isLoaded("journeymap")) {
            LOGGER.warn("JourneyMap absent du serveur : les bordures de la ville ne seront pas envoyées");
            return;
        }
        NeoForge.EVENT_BUS.register(new MapSync());
    }
}
