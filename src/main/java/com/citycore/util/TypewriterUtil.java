package com.citycore.util;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Dialogues des PNJ affichés lettre par lettre dans la barre d'action (au-dessus de la barre
 * d'objets), et uniquement là : rien n'est recopié dans le chat.
 *
 * Un seul dialogue à la fois par joueur : un dialogue lancé pendant qu'un autre se joue attend
 * son tour. Pendant un dialogue, les autres messages de la barre d'action du plugin (entrée dans
 * la ville, progression d'une chasse…) sont ignorés pour ne pas couper la phrase en cours.
 */
public class TypewriterUtil {

    private static final int CHARS_PER_TICK = 2;
    private static final int DELAY_BETWEEN_LINES = 50; // 2,5 secondes pour lire la ligne complète

    /** Fournit le nom de la ville pour le marqueur {ville} des dialogues. */
    private static Supplier<String> cityNameSupplier = () -> "la ville";

    /** Dialogue en attente : ses lignes et l'action à lancer à la fin. */
    private record Dialogue(List<String> lines, Runnable onFinish) {}

    /** Joueurs en plein dialogue → dialogues suivants en attente. */
    private static final Map<UUID, Deque<Dialogue>> queues = new HashMap<>();

    public static void setCityNameSupplier(Supplier<String> supplier) {
        cityNameSupplier = supplier;
    }

    /** Remplace les marqueurs {ville} et {joueur} d'une ligne de dialogue. */
    public static String resolve(String line, Player player) {
        String result = line;
        if (result.contains("{ville}"))  result = result.replace("{ville}", cityNameSupplier.get());
        if (result.contains("{joueur}")) result = result.replace("{joueur}", player.getName());
        return result;
    }

    /** Le joueur écoute-t-il un dialogue ? */
    public static boolean isTalking(Player player) {
        return queues.containsKey(player.getUniqueId());
    }

    /**
     * Message de barre d'action du plugin : ignoré pendant un dialogue (la phrase en cours
     * resterait sinon coupée ou mélangée).
     */
    public static void actionBar(Player player, String message) {
        if (!isTalking(player)) player.sendActionBar(message);
    }

    public static void play(JavaPlugin plugin, Player player,
                            List<String> lines, Runnable onFinish) {
        if (lines == null || lines.isEmpty()) {
            if (onFinish != null) onFinish.run();
            return;
        }
        Deque<Dialogue> queue = queues.get(player.getUniqueId());
        if (queue != null) {
            queue.addLast(new Dialogue(lines, onFinish)); // un dialogue est en cours : il attend
            return;
        }
        queues.put(player.getUniqueId(), new ArrayDeque<>());
        playLine(plugin, player, new Dialogue(lines, onFinish), 0);
    }

    /** Fin d'un dialogue : action finale, puis dialogue suivant éventuel. */
    private static void finish(JavaPlugin plugin, Player player, Dialogue dialogue) {
        Deque<Dialogue> queue = queues.get(player.getUniqueId());
        Dialogue next = queue == null ? null : queue.pollFirst();
        if (next == null) {
            queues.remove(player.getUniqueId());
            player.sendActionBar("");
        }
        if (dialogue.onFinish() != null) {
            new BukkitRunnable() {
                @Override public void run() { dialogue.onFinish().run(); }
            }.runTaskLater(plugin, 20L);
        }
        if (next != null) {
            new BukkitRunnable() {
                @Override public void run() { playLine(plugin, player, next, 0); }
            }.runTaskLater(plugin, 20L);
        }
    }

    private static void playLine(JavaPlugin plugin, Player player, Dialogue dialogue, int lineIndex) {
        if (!player.isOnline()) {
            queues.remove(player.getUniqueId());
            return;
        }
        if (lineIndex >= dialogue.lines().size()) {
            finish(plugin, player, dialogue);
            return;
        }

        String fullLine = resolve(dialogue.lines().get(lineIndex), player);

        new BukkitRunnable() {
            int charIndex = 0;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    queues.remove(player.getUniqueId());
                    cancel();
                    return;
                }

                charIndex += CHARS_PER_TICK;

                if (charIndex >= fullLine.length()) {
                    // Ligne complète, sans curseur ; elle reste affichée le temps de la lire
                    player.sendActionBar(fullLine);
                    cancel();

                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            playLine(plugin, player, dialogue, lineIndex + 1);
                        }
                    }.runTaskLater(plugin, DELAY_BETWEEN_LINES);

                } else {
                    // Évite de couper un code couleur (§x) en deux
                    int cut = charIndex;
                    if (cut > 0 && fullLine.charAt(cut - 1) == '§') cut++;
                    player.sendActionBar(fullLine.substring(0, Math.min(cut, fullLine.length())) + "§7▌");
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }
}
