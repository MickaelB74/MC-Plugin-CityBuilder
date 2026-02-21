package com.citycore.util;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;

public class TypewriterUtil {

    private static final int CHARS_PER_TICK = 2;
    private static final int DELAY_BETWEEN_LINES = 50; // 2.5 secondes pour lire la ligne complète

    public static void play(JavaPlugin plugin, Player player,
                            List<String> lines, Runnable onFinish) {
        playLine(plugin, player, lines, 0, onFinish);
    }

    private static void playLine(JavaPlugin plugin, Player player,
                                 List<String> lines, int lineIndex, Runnable onFinish) {
        if (lineIndex >= lines.size()) {
            // Efface l'ActionBar et lance le callback
            player.sendActionBar("");
            if (onFinish != null) {
                new BukkitRunnable() {
                    @Override public void run() { onFinish.run(); }
                }.runTaskLater(plugin, 20L);
            }
            return;
        }

        String fullLine = lines.get(lineIndex);

        new BukkitRunnable() {
            int charIndex = 0;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel();
                    return;
                }

                charIndex += CHARS_PER_TICK;

                if (charIndex >= fullLine.length()) {
                    // Ligne complète — affiche sans curseur et attend avant la suivante
                    player.sendActionBar(fullLine);
                    cancel();

                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            playLine(plugin, player, lines, lineIndex + 1, onFinish);
                        }
                    }.runTaskLater(plugin, DELAY_BETWEEN_LINES);

                } else {
                    // Frappe en cours — affiche le texte partiel avec curseur
                    player.sendActionBar(fullLine.substring(0, charIndex) + "§7▌");
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }
}