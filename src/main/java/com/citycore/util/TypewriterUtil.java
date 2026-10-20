package com.citycore.util;

import com.citycore.ui.UiDialogue;
import net.kyori.adventure.text.Component;
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
 * Joueur équipé du pack : une réplique longue s'écrit sur deux lignes centrées (voir
 * {@link UiDialogue}), une page de deux lignes de plus seulement pour les très longues. Sans le
 * pack, la barre d'action ne tient qu'une ligne : la réplique est coupée entre deux mots et
 * affichée en plusieurs morceaux successifs (voir wrap).
 *
 * Un seul dialogue à la fois par joueur : un dialogue lancé pendant qu'un autre se joue attend
 * son tour. Pendant un dialogue, les autres messages de la barre d'action du plugin (entrée dans
 * la ville, progression d'une chasse…) sont ignorés pour ne pas couper la phrase en cours.
 */
public class TypewriterUtil {

    private static final int CHARS_PER_TICK = 2;
    private static final int DELAY_BETWEEN_LINES = 50; // 2,5 secondes pour lire la ligne complète

    /** Répliques sur deux lignes (joueurs équipés du pack) ; null = toujours en morceaux. */
    private static UiDialogue multiLine;

    public static void setMultiLine(UiDialogue dialogue) {
        multiLine = dialogue;
    }

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

    /**
     * Largeur maximale d'une réplique dans la barre d'action, en caractères visibles. La barre
     * d'action ne tient que sur une ligne : au-delà, la réplique sortirait de l'écran (interface à
     * l'échelle 4 en 1080p : environ 75 caractères). Réglable : dialogues.largeur de config.yml.
     */
    private static int maxWidth = 58;

    public static void setMaxWidth(int width) {
        maxWidth = Math.max(20, width);
    }

    /**
     * Coupe une réplique trop longue en morceaux affichés l'un après l'autre (le « retour à la
     * ligne » de la barre d'action) : coupure entre deux mots, le nom du personnage (« Thorvald : »)
     * et la mise en forme en cours (couleur, italique) repris en tête de chaque morceau, « … » à la
     * fin des morceaux non terminés.
     */
    static List<String> wrap(String line) {
        if (visibleLength(line) <= maxWidth) return List.of(line);
        // Nom du personnage : tout ce qui précède « : » (« §4Thorvald §f: ») est répété
        int colon = line.indexOf(": ");
        String speaker = colon > 0 && colon < 40 ? line.substring(0, colon + 2) : "";
        String body = line.substring(speaker.length());
        int room = Math.max(20, maxWidth - visibleLength(speaker) - 2);

        List<String> parts = new java.util.ArrayList<>();
        StringBuilder current = new StringBuilder();
        String carried = "";      // codes de mise en forme actifs au début du morceau
        String active = "";       // codes actifs au fil du texte
        int visible = 0;
        for (String word : body.split(" ", -1)) {
            int len = visibleLength(word);
            if (visible > 0 && visible + 1 + len > room) {
                parts.add(speaker + carried + current + " §8…");
                current.setLength(0);
                carried = active;
                visible = 0;
            }
            if (visible > 0) { current.append(' '); visible++; }
            current.append(word);
            visible += len;
            active = activeCodes(active, word);
        }
        parts.add(speaker + carried + current);
        return parts;
    }

    /** Nombre de caractères affichés (codes « §x » exclus). */
    private static int visibleLength(String s) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == '§' && i + 1 < s.length()) { i++; continue; }
            n++;
        }
        return n;
    }

    /** Codes de mise en forme actifs après un mot : une couleur remet à zéro, un style s'ajoute. */
    private static String activeCodes(String active, String word) {
        String codes = active;
        for (int i = 0; i + 1 < word.length(); i++) {
            if (word.charAt(i) != '§') continue;
            char c = Character.toLowerCase(word.charAt(i + 1));
            String code = "§" + c;
            if ("0123456789abcdefr".indexOf(c) >= 0) codes = c == 'r' ? "" : code;   // couleur ou remise à zéro
            else if ("klmno".indexOf(c) >= 0 && !codes.contains(code)) codes += code;  // gras, italique…
            i++;
        }
        return codes;
    }

    public static void play(JavaPlugin plugin, Player player,
                            List<String> lines, Runnable onFinish) {
        if (lines == null || lines.isEmpty()) {
            if (onFinish != null) onFinish.run();
            return;
        }
        // Répliques trop longues pour une ligne : pages de deux lignes (pack), sinon morceaux successifs
        boolean twoLines = multiLine != null && multiLine.supports(player);
        List<String> wrapped = new java.util.ArrayList<>();
        for (String line : lines) {
            String resolved = resolve(line, player);
            List<String> pages = twoLines ? multiLine.pages(resolved) : null;
            wrapped.addAll(pages != null ? pages : wrap(resolved));
        }
        lines = wrapped;
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
        // Page de deux lignes (pack) : écrite par la police décalée, caractère visible par caractère
        if (multiLine != null && fullLine.indexOf('\n') >= 0) {
            playPage(plugin, player, dialogue, lineIndex, fullLine);
            return;
        }

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

    /**
     * Page de deux lignes : même rythme qu'une ligne simple, puis la page reste affichée le temps
     * de la lire (plus longtemps qu'une ligne simple : elle est plus longue). Le jeu efface la barre
     * d'action 3 s après le dernier envoi : la page est renvoyée chaque seconde pendant la lecture.
     */
    private static void playPage(JavaPlugin plugin, Player player, Dialogue dialogue, int lineIndex, String page) {
        int total = UiDialogue.visibleLength(page);
        int hold  = DELAY_BETWEEN_LINES + total / 4;
        Component full = multiLine.render(page, total);
        new BukkitRunnable() {
            int revealed = 0;
            int held = -1;    // -1 : en cours d'écriture ; sinon ticks de lecture écoulés

            @Override
            public void run() {
                if (!player.isOnline()) {
                    queues.remove(player.getUniqueId());
                    cancel();
                    return;
                }
                if (held < 0) {
                    revealed += CHARS_PER_TICK;
                    if (revealed < total) {
                        player.sendActionBar(multiLine.render(page, revealed));
                        return;
                    }
                    held = 0;
                }
                if (held >= hold) {
                    cancel();
                    playLine(plugin, player, dialogue, lineIndex + 1);
                    return;
                }
                if (held % 20 == 0) player.sendActionBar(full);
                held++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }
}
