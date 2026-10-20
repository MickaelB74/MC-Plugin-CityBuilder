package com.citycore.companion;

import com.citycore.companion.epicfight.EpicFightModule;
import com.citycore.companion.journeymap.JourneyMapModule;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;

/**
 * Mod serveur « CityCore — Compagnon » : ce que le plugin CityCore (Paper, sans dépendance vers
 * un mod) ne peut pas faire seul. Deux modules indépendants, chacun actif seulement si son mod est
 * installé sur le serveur :
 * <ul>
 *   <li><b>Epic Fight</b> ({@code epicfight/}) : les PNJ du plugin reçoivent les animations
 *       d'Epic Fight, comme les monstres (marche, course, coups, tirs) ;</li>
 *   <li><b>JourneyMap</b> ({@code journeymap/}) : les parcelles de la ville (fichier
 *       {@code plugins/CityCore/carte.json} écrit par le plugin) s'affichent sur la carte plein écran
 *       et la minicarte des joueurs, par l'API serveur de JourneyMap.</li>
 * </ul>
 * Isolation : aucune classe d'Epic Fight ou de JourneyMap n'est chargée quand le mod correspondant
 * manque. Seuls {@link EpicFightModule} et {@link JourneyMapModule} sont nommés ici ; ils ne
 * touchent à aucune classe des mods dans leur signature et ne sont appelés qu'après le test de
 * présence. Les classes qui utilisent les mods ne sont nommées que dans le corps de leur méthode
 * {@code start()}.
 * Rien à installer chez les joueurs en plus d'Epic Fight et de JourneyMap.
 */
@Mod(CityCoreCompanion.MOD_ID)
public final class CityCoreCompanion {

    public static final String MOD_ID = "citycore_companion";
    public static final Logger LOGGER = LogUtils.getLogger();

    public CityCoreCompanion(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, Settings.SPEC, "citycore_companion.toml");

        // Un module qui ne trouve pas les classes attendues (autre version du mod) s'éteint seul :
        // le compagnon ne doit jamais empêcher le serveur de démarrer
        if (ModList.get().isLoaded("epicfight")) {
            try {
                EpicFightModule.start();
            } catch (LinkageError e) {
                LOGGER.warn("Epic Fight incompatible avec le module des animations ({}) : module éteint, "
                        + "le serveur continue normalement.", e.toString());
            }
        } else {
            LOGGER.info("Epic Fight absent du serveur : module des animations des PNJ éteint");
        }

        if (ModList.get().isLoaded("journeymap")) {
            try {
                JourneyMapModule.start();
            } catch (LinkageError e) {
                LOGGER.warn("JourneyMap incompatible avec le module de la carte ({}) : module éteint, "
                        + "le serveur continue normalement.", e.toString());
            }
        } else {
            LOGGER.warn("JourneyMap absent du serveur : module de la carte éteint (installez JourneyMap 6.0.5 "
                    + "ou plus dans le dossier mods du serveur pour afficher la ville sur les cartes)");
        }
    }
}
