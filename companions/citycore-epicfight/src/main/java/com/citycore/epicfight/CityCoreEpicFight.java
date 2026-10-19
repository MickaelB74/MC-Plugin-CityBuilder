package com.citycore.epicfight;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

/**
 * Mod serveur « CityCore — Epic Fight » : les PNJ du plugin CityCore reçoivent les animations
 * d'Epic Fight, comme les monstres.
 *
 * Pourquoi : Citizens fait des PNJ à skin des entités « joueur ». Epic Fight anime un joueur à
 * partir des touches de son clavier (envoyées par son jeu), qu'un PNJ n'a pas : il le croit
 * immobile et le laisse glisser. Ce mod mesure le déplacement réel de chaque PNJ et l'annonce aux
 * joueurs comme le ferait un clavier, et joue les coups et tirs que le plugin lui signale.
 * Rien à installer chez les joueurs (à part Epic Fight, déjà requis).
 */
@Mod(CityCoreEpicFight.MOD_ID)
public final class CityCoreEpicFight {

    public static final String MOD_ID = "citycore_epicfight";
    static final Logger LOGGER = LogUtils.getLogger();

    public CityCoreEpicFight(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, Settings.SPEC, "citycore_epicfight.toml");
        // Sans Epic Fight, rien à faire : aucune classe d'Epic Fight n'est chargée
        if (!ModList.get().isLoaded("epicfight")) {
            LOGGER.warn("Epic Fight absent du serveur : animations des PNJ désactivées");
            return;
        }
        NeoForge.EVENT_BUS.register(new NpcAnimator());
        LOGGER.info("Epic Fight présent : les PNJ de CityCore seront animés");
    }
}
