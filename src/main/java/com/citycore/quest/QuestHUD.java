package com.citycore.quest;

import com.citycore.npc.CityNPC;
import com.citycore.npc.NPCDataManager;
import com.citycore.ui.UiHud;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.*;

import java.util.*;

import static com.citycore.ui.UiLayout.*;

/**
 * Suivi des quêtes des PNJ à l'écran.
 *
 * - Joueur équipé du pack « Valmont » : panneau compact à gauche de l'écran, dessiné dans le
 *   titre d'une barre de boss invisible ;
 * - sinon : tableau classique à droite (scoreboard).
 *
 * Préférences du joueur (gardées dans ses données, donc après un redémarrage) : panneau masqué,
 * PNJ non suivis, position du panneau. Elles se règlent avec /city quests.
 */
public class QuestHUD {

    private final JavaPlugin     plugin;
    private final QuestManager   questManager;
    private final NPCDataManager dataManager;
    private final List<QuestGUI> questGUIs;

    // Préférences (données persistantes du joueur)
    private final NamespacedKey keyHidden;
    private final NamespacedKey keyUntracked;
    private final NamespacedKey keyPosition;

    /** Interfaces illustrées « Valmont » (null tant qu'elles ne sont pas prêtes). */
    private com.citycore.ui.UiKit ui;
    /** Panneau illustré de chaque joueur équipé du pack (titre d'une barre de boss invisible). */
    private final Map<UUID, BossBar> panels = new HashMap<>();
    /** Joueurs dont le tableau de droite a déjà été retiré au profit du panneau. */
    private final Set<UUID> sidebarCleared = new HashSet<>();

    public void setUi(com.citycore.ui.UiKit ui) {
        this.ui = ui;
    }

    /** Mods du serveur : les quêtes d'un habitant lié à un mod absent ne s'affichent pas. */
    private com.citycore.integration.ModIntegrations integrations;

    public void setIntegrations(com.citycore.integration.ModIntegrations integrations) {
        this.integrations = integrations;
    }

    private boolean hiddenByMod(CityNPC npc) {
        return integrations != null && !integrations.isAvailable(npc);
    }

    public QuestHUD(JavaPlugin plugin, QuestManager questManager,
                    NPCDataManager dataManager, List<QuestGUI> questGUIs) {
        this.plugin       = plugin;
        this.questManager = questManager;
        this.dataManager  = dataManager;
        this.questGUIs    = questGUIs;
        this.keyHidden    = new NamespacedKey(plugin, "quest_hud_hidden");
        this.keyUntracked = new NamespacedKey(plugin, "quest_hud_untracked");
        this.keyPosition  = new NamespacedKey(plugin, "quest_panel_pos");
    }

    public void startUpdating() {
        new BukkitRunnable() {
            @Override
            public void run() {
                // Joueurs partis : leurs panneaux sont oubliés
                panels.keySet().removeIf(id -> Bukkit.getPlayer(id) == null);
                sidebarCleared.removeIf(id -> Bukkit.getPlayer(id) == null);
                for (Player player : Bukkit.getOnlinePlayers()) {
                    updateHUD(player);
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    /* =========================
       AFFICHAGE GLOBAL (/city quests toggle)
       ========================= */

    public boolean isHidden(Player player) {
        Byte hidden = player.getPersistentDataContainer().get(keyHidden, PersistentDataType.BYTE);
        return hidden != null && hidden == 1;
    }

    /** Affiche ou masque le suivi des quêtes (panneau ou tableau de droite). */
    public void setHidden(Player player, boolean hidden) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        if (hidden) pdc.set(keyHidden, PersistentDataType.BYTE, (byte) 1);
        else pdc.remove(keyHidden);
        if (hidden) {
            clearPanel(player);
            player.setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard());
        } else {
            updateHUD(player);
        }
    }

    public void toggle(Player player) {
        boolean hide = !isHidden(player);
        setHidden(player, hide);
        player.sendMessage(hide ? "§7📋 Suivi des quêtes §7masqué. §8(/city quests toggle pour le rétablir)"
                : "§a📋 Suivi des quêtes §aaffiché.");
    }

    /* =========================
       POSITION DU PANNEAU
       ========================= */

    /** Indice dans {@code QP_LEFT} : 0 = bord de l'écran, 1 = à gauche, 2 = contre l'inventaire. */
    public int panelPosition(Player player) {
        Integer pos = player.getPersistentDataContainer().get(keyPosition, PersistentDataType.INTEGER);
        return pos == null ? 0 : Math.max(0, Math.min(QP_LEFT.length - 1, pos));
    }

    public void setPanelPosition(Player player, int position) {
        int pos = Math.max(0, Math.min(QP_LEFT.length - 1, position));
        if (pos == 0) player.getPersistentDataContainer().remove(keyPosition);
        else player.getPersistentDataContainer().set(keyPosition, PersistentDataType.INTEGER, pos);
        updateHUD(player);
    }

    /* =========================
       SUIVI PAR PNJ
       ========================= */

    /** PNJ masqués (tags) du joueur. */
    private Set<String> untracked(Player player) {
        String raw = player.getPersistentDataContainer().get(keyUntracked, PersistentDataType.STRING);
        if (raw == null || raw.isBlank()) return new HashSet<>();
        return new HashSet<>(Arrays.asList(raw.split(",")));
    }

    private void saveUntracked(Player player, Set<String> tags) {
        tags.removeIf(String::isBlank);
        if (tags.isEmpty()) player.getPersistentDataContainer().remove(keyUntracked);
        else player.getPersistentDataContainer().set(keyUntracked, PersistentDataType.STRING,
                String.join(",", new TreeSet<>(tags)));
    }

    /**
     * Affiche (true) ou masque (false) les quêtes d'un PNJ pour un joueur en ligne.
     */
    public void setNpcTracked(UUID uuid, CityNPC npc, boolean tracked) {
        Player player = Bukkit.getPlayer(uuid);
        if (player == null) return;
        Set<String> hidden = untracked(player);
        boolean changed = tracked ? hidden.remove(npc.tag) : hidden.add(npc.tag);
        if (!changed) return;
        saveUntracked(player, hidden);
        updateHUD(player);
    }

    /** Les quêtes de ce PNJ sont-elles affichées pour ce joueur ? */
    public boolean isNpcTracked(UUID uuid, CityNPC npc) {
        Player player = Bukkit.getPlayer(uuid);
        return player == null || !untracked(player).contains(npc.tag);
    }

    /** Suit de nouveau tous les PNJ. */
    public void trackAll(Player player) {
        player.getPersistentDataContainer().remove(keyUntracked);
        updateHUD(player);
    }

    /** PNJ ayant au moins une quête en cours pour ce joueur (ordre des menus). */
    public List<CityNPC> npcsWithActiveQuest(Player player) {
        Set<String> active = questManager.getActiveQuestKeys(player.getUniqueId());
        List<CityNPC> list = new ArrayList<>();
        if (active.isEmpty()) return list;
        for (QuestGUI gui : questGUIs) {
            CityNPC npc = gui.getNpcType();
            if (hiddenByMod(npc)) continue;
            if (active.contains(QuestManager.activeKey(npc, false))
                    || active.contains(QuestManager.activeKey(npc, true))) list.add(npc);
        }
        return list;
    }

    /* =========================
       MISE À JOUR
       ========================= */

    public void updateHUD(Player player) {
        if (isHidden(player)) return;

        // Joueur équipé du pack : panneau illustré à gauche, à la place du tableau de droite
        if (ui != null && ui.active(player)) {
            showPanel(player);
            return;
        }
        removePanel(player);

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
                Component.text("Quêtes actives")
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
        Set<String> hidden = untracked(player);
        List<String> lines = new ArrayList<>();

        // Une seule requête pour savoir quels PNJ ont une quête active
        Set<String> active = questManager.getActiveQuestKeys(uuid);
        if (active.isEmpty()) return lines;

        for (QuestGUI gui : questGUIs) {
            CityNPC npc = gui.getNpcType();

            // Si ce NPC est masqué pour ce joueur (ou lié à un mod absent), on saute
            if (hidden.contains(npc.tag) || hiddenByMod(npc)) continue;

            QuestDefinition main    = active.contains(QuestManager.activeKey(npc, false))
                    ? questManager.getActiveQuest(uuid, npc, false) : null;
            QuestDefinition special = active.contains(QuestManager.activeKey(npc, true))
                    ? questManager.getActiveQuest(uuid, npc, true) : null;

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

            String label = obj.isMaterialObjective() ? com.citycore.integration.ItemIds.label(obj.material())
                    : obj.isEntityObjective() ? formatName(obj.entity().name()) : "?";

            lines.add((done ? "§a✔ " : "§7• ") + "§f"
                    + truncate(label, 14) + " "
                    + (done ? "§a" : "§f") + current + "§7/§f" + required);
        }

        return lines;
    }

    /* =========================
       PANNEAU ILLUSTRÉ (pack « Valmont »)
       ========================= */

    private void showPanel(Player player) {
        UUID uuid = player.getUniqueId();
        if (sidebarCleared.add(uuid)) {
            player.setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard()); // retire le tableau de droite
        }
        List<UiHud.Row> rows = buildRows(player);
        BossBar bar = panels.get(uuid);
        if (bar == null) {
            bar = BossBar.bossBar(Component.empty(), 0f, BossBar.Color.WHITE, BossBar.Overlay.PROGRESS);
            panels.put(uuid, bar);
            player.showBossBar(bar);
        }
        bar.name(rows.isEmpty() ? Component.empty() : panel(rows, QP_LEFT[panelPosition(player)]));
    }

    /**
     * Vide le panneau sans retirer sa barre : elle garde sa place en tête des barres de boss,
     * et le panneau ne saute pas sous une autre barre (dragon…) quand il revient.
     */
    private void clearPanel(Player player) {
        BossBar bar = panels.get(player.getUniqueId());
        if (bar != null) bar.name(Component.empty());
    }

    /** Retire la barre (joueur revenu aux menus classiques ou sans pack). */
    private void removePanel(Player player) {
        BossBar bar = panels.remove(player.getUniqueId());
        if (bar != null) player.hideBossBar(bar);
        sidebarCleared.remove(player.getUniqueId());
    }

    /** Panneau compact : bandeau « Quêtes », une ligne par PNJ et par objectif. */
    private Component panel(List<UiHud.Row> rows, int left) {
        com.citycore.ui.UiCanvas c = ui.canvas(0);
        UiHud.panel(c, left, QP_TOP, QP_OFFSET, "Quêtes", rows, HUD_MAX_ROWS);
        c.end(0); // avance totale nulle : le titre de la barre reste centré pile au milieu
        return c.build();
    }

    /** Mêmes informations que le tableau de droite, en lignes de panneau. */
    private List<UiHud.Row> buildRows(Player player) {
        UUID uuid = player.getUniqueId();
        Set<String> hidden = untracked(player);
        List<UiHud.Row> rows = new ArrayList<>();
        Set<String> active = questManager.getActiveQuestKeys(uuid);
        if (active.isEmpty()) return rows;

        for (QuestGUI gui : questGUIs) {
            CityNPC npc = gui.getNpcType();
            if (hidden.contains(npc.tag) || hiddenByMod(npc)) continue;
            QuestDefinition main    = active.contains(QuestManager.activeKey(npc, false))
                    ? questManager.getActiveQuest(uuid, npc, false) : null;
            QuestDefinition special = active.contains(QuestManager.activeKey(npc, true))
                    ? questManager.getActiveQuest(uuid, npc, true) : null;
            if (main == null && special == null) continue;

            rows.add(UiHud.Row.head(npc.displayName.replaceAll("§.", ""), UiHud.GOLD));
            if (main != null) addObjectives(rows, uuid, npc, main, false);
            if (special != null) addObjectives(rows, uuid, npc, special, true);
        }
        if (rows.size() > HUD_MAX_ROWS) {
            int rest = rows.size() - (HUD_MAX_ROWS - 1);
            rows = new ArrayList<>(rows.subList(0, HUD_MAX_ROWS - 1));
            rows.add(UiHud.Row.plain("+ " + rest + " ligne" + (rest > 1 ? "s" : ""), UiHud.CREAM));
        }
        return rows;
    }

    private void addObjectives(List<UiHud.Row> rows, UUID uuid, CityNPC npc, QuestDefinition quest, boolean isSpecial) {
        if (questManager.isReadyToValidate(uuid, npc, isSpecial)) {
            rows.add(UiHud.Row.plain("★ Voir " + npc.displayName.replaceAll("§.", "") + " !", UiHud.GREEN));
            return;
        }
        Map<String, Integer> progress = questManager.getProgress(uuid, npc, isSpecial);
        for (QuestObjective obj : quest.objectives()) {
            int current  = progress.getOrDefault(obj.id(), 0);
            int required = Math.max(1, obj.amount());
            boolean done = current >= required;
            String label = obj.isMaterialObjective() ? com.citycore.integration.ItemIds.label(obj.material())
                    : obj.isEntityObjective() ? formatName(obj.entity().name()) : "?";
            int color = done ? UiHud.GREEN : isSpecial ? UiHud.PURPLE : UiHud.CREAM;
            rows.add(UiHud.Row.bar(label, color, Math.min(current, required) + "/" + required,
                    done ? UiHud.GREEN : UiHud.GOLD, (double) current / required, false));
        }
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
