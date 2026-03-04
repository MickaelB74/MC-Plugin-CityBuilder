package com.citycore.quest;

import com.citycore.npc.CityNPC;
import com.citycore.npc.NPCDataManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.*;

import java.util.*;

public class QuestHUD {

    private final JavaPlugin     plugin;
    private final QuestManager   questManager;
    private final NPCDataManager dataManager;
    private final List<QuestGUI> questGUIs;

    /** Joueurs ayant masqué le HUD entier (/city quests toggle précédent). */
    private final Set<UUID> hiddenPlayers = new HashSet<>();

    /**
     * NPC masqués par joueur (via CityQuestSelectionGUI).
     * Si le tag d'un NPC est dans ce set, ses quêtes ne s'affichent pas
     * dans le scoreboard de ce joueur.
     */
    private final Map<UUID, Set<String>> hiddenNpcTags = new HashMap<>();

    public QuestHUD(JavaPlugin plugin, QuestManager questManager,
                    NPCDataManager dataManager, List<QuestGUI> questGUIs) {
        this.plugin       = plugin;
        this.questManager = questManager;
        this.dataManager  = dataManager;
        this.questGUIs    = questGUIs;
    }

    public void startUpdating() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    updateHUD(player);
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    /* =========================
       TOGGLE HUD GLOBAL
       ========================= */

    public void toggle(Player player) {
        UUID uuid = player.getUniqueId();
        if (hiddenPlayers.contains(uuid)) {
            hiddenPlayers.remove(uuid);
            player.sendMessage("§a📋 HUD quêtes §aactivé.");
            updateHUD(player);
        } else {
            hiddenPlayers.add(uuid);
            player.sendMessage("§7📋 HUD quêtes §7masqué.");
            player.setScoreboard(
                    Bukkit.getScoreboardManager().getNewScoreboard());
        }
    }

    /* =========================
       TOGGLE PAR NPC (CityQuestSelectionGUI)
       ========================= */

    /**
     * Active ou désactive l'affichage dans le scoreboard des quêtes d'un NPC
     * spécifique pour un joueur donné.
     *
     * @param uuid    UUID du joueur
     * @param npc     NPC concerné
     * @param tracked true = affiché, false = masqué
     */
    public void setNpcTracked(UUID uuid, CityNPC npc, boolean tracked) {
        Set<String> hidden = hiddenNpcTags.computeIfAbsent(uuid, k -> new HashSet<>());
        if (tracked) {
            hidden.remove(npc.tag);
        } else {
            hidden.add(npc.tag);
        }
        // Rafraîchit immédiatement le scoreboard du joueur si en ligne
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) updateHUD(player);
    }

    /** Vérifie si les quêtes d'un NPC sont affichées pour un joueur. */
    public boolean isNpcTracked(UUID uuid, CityNPC npc) {
        return !hiddenNpcTags.getOrDefault(uuid, Collections.emptySet()).contains(npc.tag);
    }

    /* =========================
       MISE À JOUR SCOREBOARD
       ========================= */

    public void updateHUD(Player player) {
        if (hiddenPlayers.contains(player.getUniqueId())) return;

        List<String> lines = buildLines(player);

        if (lines.isEmpty()) {
            player.setScoreboard(
                    Bukkit.getScoreboardManager().getNewScoreboard());
            return;
        }

        ScoreboardManager manager = Bukkit.getScoreboardManager();
        Scoreboard board          = manager.getNewScoreboard();

        Objective obj = board.registerNewObjective(
                "citycore_hud",
                Criteria.DUMMY,
                net.kyori.adventure.text.Component.text("Quêtes actives")
                        .color(net.kyori.adventure.text.format.NamedTextColor.GOLD)
                        .decorate(net.kyori.adventure.text.format.TextDecoration.BOLD));

        obj.setDisplaySlot(DisplaySlot.SIDEBAR);

        // Cache tous les scores via NumberFormat.blank() — API Paper
        Set<String> used = new HashSet<>();
        int score = lines.size();
        for (String line : lines) {
            String unique = makeUnique(line, used);
            used.add(unique);
            Score s = obj.getScore(unique);
            s.setScore(score--);
            s.numberFormat(io.papermc.paper.scoreboard.numbers.NumberFormat.blank());
        }

        player.setScoreboard(board);
    }

    private List<String> buildLines(Player player) {
        UUID uuid  = player.getUniqueId();
        Set<String> hidden = hiddenNpcTags.getOrDefault(uuid, Collections.emptySet());
        List<String> lines = new ArrayList<>();

        for (QuestGUI gui : questGUIs) {
            CityNPC npc = gui.getNpcType();

            // Si ce NPC est masqué pour ce joueur, on saute
            if (hidden.contains(npc.tag)) continue;

            QuestDefinition main    = questManager.getActiveQuest(uuid, npc, false);
            QuestDefinition special = questManager.getActiveQuest(uuid, npc, true);

            if (main == null && special == null) continue;

            lines.add(npc.displayName);

            if (main != null) {
                lines.addAll(buildObjectiveLines(uuid, npc, main, false));
            }
            if (special != null) {
                lines.add("§8 ─────────────");
                lines.addAll(buildObjectiveLines(uuid, npc, special, true));
            }

            lines.add("§r ");
        }

        return lines;
    }

    private List<String> buildObjectiveLines(UUID uuid, CityNPC npc,
                                             QuestDefinition quest,
                                             boolean isSpecial) {
        List<String> lines = new ArrayList<>();

        if (questManager.isReadyToValidate(uuid, npc, isSpecial)) {
            lines.add("§a★ Voir " + npc.displayName.replaceAll("§.", "") + " !");
            return lines;
        }

        Map<String, Integer> progress = questManager.getProgress(uuid, npc, isSpecial);

        for (QuestObjective obj : quest.objectives()) {
            int current  = progress.getOrDefault(obj.id(), 0);
            int required = obj.amount();
            boolean done = current >= required;

            String label = obj.isMaterialObjective()
                    ? formatName(obj.material().name())
                    : formatName(obj.entity().name());

            lines.add((done ? "§a✔ " : "§7• ") + "§f"
                    + truncate(label, 14) + " "
                    + (done ? "§a" : "§f") + current + "§7/§f" + required);
        }

        return lines;
    }

    private String makeUnique(String line, Set<String> used) {
        String result = line;
        int i = 0;
        while (used.contains(result)) {
            result = line + "§r" + " ".repeat(++i);
        }
        return result;
    }

    private String truncate(String s, int max) {
        return s.length() > max ? s.substring(0, max) + "." : s;
    }

    private String formatName(String name) {
        StringBuilder sb = new StringBuilder();
        for (String word : name.split("_")) {
            sb.append(Character.toUpperCase(word.charAt(0)))
                    .append(word.substring(1).toLowerCase()).append(" ");
        }
        return sb.toString().trim();
    }
}