package com.citycore.companion.discord;

import net.neoforged.neoforge.common.NeoForge;

/**
 * Entrée du module Discord : l'API HTTP que le bot « citycore-discord-bot » interroge (état du
 * serveur et de la ville, journal des événements) et par laquelle il envoie les messages de
 * Discord aux joueurs. Le module ne dépend d'aucun autre mod ; il démarre avec le serveur
 * ({@link DiscordBridge}) et reste éteint si {@code discord.actif} est à false.
 */
public final class DiscordModule {

    private DiscordModule() {
    }

    public static void start() {
        NeoForge.EVENT_BUS.register(new DiscordBridge());
    }
}
