package com.citycore.companion.epicfight;

import com.citycore.companion.CityCoreCompanion;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Entrée du module Epic Fight, appelée seulement quand Epic Fight est installé : c'est ici, et
 * seulement ici, que la classe qui utilise Epic Fight ({@link NpcAnimator}) est nommée. Sans
 * Epic Fight, cette classe n'est jamais chargée.
 */
public final class EpicFightModule {

    private EpicFightModule() {
    }

    public static void start() {
        NeoForge.EVENT_BUS.register(new NpcAnimator());
        CityCoreCompanion.LOGGER.info("Epic Fight présent : les PNJ de CityCore seront animés");
    }
}
