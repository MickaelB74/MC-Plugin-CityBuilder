package com.citycore.quest;

import com.citycore.npc.CityNPC;
import com.citycore.npc.NPCDataManager;
import com.citycore.player.PlayerPrefs;
import com.citycore.ui.UiHud;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.*;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;

import static com.citycore.ui.UiLayout.*;

/**
 * Suivi des quêtes des PNJ à l'écran, selon le mode choisi par chaque joueur :
 *
 * - Visible : joueur équipé du pack « Valmont » : panneau compact à gauche de l'écran, dessiné
 *   dans le titre d'une barre de boss invisible ; sinon tableau classique à droite (scoreboard) ;
 * - Accroché : rien en permanence à l'écran ; le suivi s'affiche avec la liste des joueurs (touche
 *   Tab) : panneau « Quêtes » sous celui de la cité (pack), ou lignes sous la liste (sans pack).
 *   Le serveur ne sait pas quand Tab est enfoncé : l'en-tête de la liste est tenu à jour à chaque
 *   progression (voir {@link #tabRows}, rafraîchi par CityCoreHUD) ;
 * - Masqué : rien.
 *
 * Préférences du joueur (base SQLite, voir {@link PlayerPrefs}) : mode d'affichage, PNJ non suivis,
 * position du panneau. Elles se règlent dans le journal de quêtes (ouvert depuis l'écran des quêtes
 * d'un habitant, ou /city quests) ; /city quests toggle fait défiler les trois modes.
 */
public class QuestHUD {

    /** Affichage du suivi des quêtes ; l'ordre des valeurs est celui du défilement. */
    public enum Mode {
        VISIBLE("visible", "visible"),
        ACCROCHE("accroche", "accroché"),
        MASQUE("masque", "masqué");

        /** Valeur rangée dans les préférences du joueur. */
        public final String key;
        /** Libellé des messages (minuscules). */
        public final String label;

        Mode(String key, String label) {
            this.key   = key;
            this.label = label;
        }

        public Mode next() {
            return values()[(ordinal() + 1) % values().length];
        }

        public Mode previous() {
            return values()[(ordinal() + values().length - 1) % values().length];
        }

        /** Mode d'une valeur enregistrée ou saisie (« accroché », « tab », « off »…) ; null si inconnue. */
        public static Mode parse(String value) {
            if (value == null) return null;
            String k = value.trim().toLowerCase(Locale.FRENCH).replace('é', 'e').replace('è', 'e');
            return switch (k) {
                case "visible", "affiche", "on" -> VISIBLE;
                case "accroche", "tab", "liste" -> ACCROCHE;
                case "masque", "cache", "off"   -> MASQUE;
                default -> null;
            };
        }
    }

    /** Lignes de texte du suivi accroché, sans le pack (pied de la liste des joueurs). */
    private static final int TAB_TEXT_LINES = 15;

    private final JavaPlugin     plugin;
    private final QuestManager   questManager;
    private final NPCDataManager dataManager;
    private final List<QuestGUI> questGUIs;
    private final PlayerPrefs    prefs;

    /** Interfaces illustrées « Valmont » (null tant qu'elles ne sont pas prêtes). */
    private com.citycore.ui.UiKit ui;
    /** Panneau illustré de chaque joueur équipé du pack (titre d'une barre de boss invisible). */
    private final Map<UUID, BossBar> panels = new HashMap<>();
    /** Joueurs dont le tableau de droite a déjà été retiré au profit du panneau. */
    private final Set<UUID> sidebarCleared = new HashSet<>();
    /** Suivi accroché : dernier contenu envoyé dans la liste des joueurs (absent = pas accroché). */
    private final Map<UUID, TabQuests> tab = new HashMap<>();
    /** Renvoie l'en-tête et le pied de la liste des joueurs d'un joueur (CityCoreHUD). */
    private Consumer<Player> tabRefresher;

    /** Contenu du suivi accroché : lignes du panneau (pack) ou lignes de texte (sans pack). */
    private record TabQuests(List<UiHud.Row> rows, List<String> lines) {}

    public void setUi(com.citycore.ui.UiKit ui) {
        this.ui = ui;
    }

    /** Mods du serveur : les quêtes d'un habitant lié à un mod absent ne s'affichent pas. */
    private com.citycore.integration.ModIntegrations integrations;

    public void setIntegrations(com.citycore.integration.ModIntegrations integrations) {
        this.integrations = integrations;
    }

    /** Liste des joueurs à renvoyer quand le suivi accroché change (en-tête et pied, CityCoreHUD). */
    public void setTabRefresher(Consumer<Player> tabRefresher) {
        this.tabRefresher = tabRefresher;
    }

    /** Expédition en cours (plan « Raids ») : défiance et objectifs en tête du suivi, vide sinon. */
    private Function<Player, List<UiHud.Row>> raidRows = p -> List.of();
    private Function<Player, List<String>> raidLines = p -> List.of();

    public void setRaid(Function<Player, List<UiHud.Row>> rows, Function<Player, List<String>> lines) {
        this.raidRows = rows;
        this.raidLines = lines;
    }

    private boolean hiddenByMod(CityNPC npc) {
        return integrations != null && !integrations.isAvailable(npc);
    }

    /** Artisan sans logis (bâtiment supprimé) : ses quêtes sont en pause et ne s'affichent pas. */
    private boolean paused(CityNPC npc) {
        return dataManager.getState(npc) == com.citycore.npc.NPCState.HOMELESS;
    }

    public QuestHUD(JavaPlugin plugin, QuestManager questManager,
                    NPCDataManager dataManager, List<QuestGUI> questGUIs, PlayerPrefs prefs) {
        this.plugin       = plugin;
        this.questManager = questManager;
        this.dataManager  = dataManager;
        this.questGUIs    = questGUIs;
        this.prefs        = prefs;
    }

    public void startUpdating() {
        new BukkitRunnable() {
            @Override
            public void run() {
                // Joueurs partis : leurs panneaux sont oubliés
                panels.keySet().removeIf(id -> Bukkit.getPlayer(id) == null);
                sidebarCleared.removeIf(id -> Bukkit.getPlayer(id) == null);
                tab.keySet().removeIf(id -> Bukkit.getPlayer(id) == null);
                for (Player player : Bukkit.getOnlinePlayers()) {
                    updateHUD(player);
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    /* =========================
       MODE D'AFFICHAGE (journal de quêtes, /city quests toggle)
       ========================= */

    /** Mode choisi par le joueur (visible par défaut). */
    public Mode mode(Player player) {
        Mode mode = Mode.parse(prefs.get(player, PlayerPrefs.QUEST_HUD_MODE));
        return mode == null ? Mode.VISIBLE : mode;
    }

    /** Change le mode (gardé dans les préférences) et met l'écran et la liste des joueurs à jour. */
    public void setMode(Player player, Mode mode) {
        prefs.set(player, PlayerPrefs.QUEST_HUD_MODE, mode.key);
        if (mode == Mode.ACCROCHE) hideBar(player);  // barre du panneau retirée : rien ne reste à l'écran
        if (mode == Mode.MASQUE) clearPanel(player); // panneau vidé, la barre garde sa place
        if (mode != Mode.VISIBLE) {
            player.setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard()); // tableau de droite retiré
        }
        if (mode != Mode.ACCROCHE && tab.remove(player.getUniqueId()) != null) refreshTab(player);
        updateHUD(player);
    }

    /** Passe au mode suivant (ou précédent) ; renvoie le nouveau mode. */
    public Mode cycle(Player player, boolean forward) {
        Mode current = mode(player);
        Mode mode = forward ? current.next() : current.previous();
        setMode(player, mode);
        return mode;
    }

    /** /city quests toggle [mode] : mode donné, sinon le suivant ; confirmation dans le chat. */
    public void toggle(Player player, Mode chosen) {
        Mode mode = chosen != null ? chosen : mode(player).next();
        setMode(player, mode);
        announce(player, mode);
    }

    public void toggle(Player player) {
        toggle(player, null);
    }

    /** Message de confirmation d'un changement de mode. */
    public void announce(Player player, Mode mode) {
        boolean pack = ui != null && ui.active(player);
        switch (mode) {
            case VISIBLE -> player.sendMessage("§a📋 Suivi des quêtes : §fvisible §7(" + (pack ? "panneau à gauche"
                    : "tableau à droite") + " de l'écran).");
            case ACCROCHE -> player.sendMessage(Component.text("📋 Suivi des quêtes : ", NamedTextColor.AQUA)
                    .append(Component.text("accroché", NamedTextColor.WHITE))
                    .append(Component.text(" — il s'affiche avec la liste des joueurs (", NamedTextColor.GRAY))
                    .append(Component.keybind("key.playerlist", NamedTextColor.YELLOW))
                    .append(Component.text(").", NamedTextColor.GRAY)));
            case MASQUE -> player.sendMessage("§7📋 Suivi des quêtes : §fmasqué§7.");
        }
        player.sendMessage("§8Choix gardé dans vos préférences. Pour le changer : journal de quêtes"
                + " (écran Quêtes d'un habitant) ou /city quests toggle.");
    }

    /** Nom du bouton du mode (journal de quêtes illustré ou classique). */
    public static String modeTitle(Mode mode) {
        return switch (mode) {
            case VISIBLE  -> "§a✔ Suivi des quêtes : visible";
            case ACCROCHE -> "§b📋 Suivi des quêtes : accroché";
            case MASQUE   -> "§7✘ Suivi des quêtes : masqué";
        };
    }

    /** Bulle du bouton du mode : les trois modes (l'actuel marqué), puis les clics. */
    public static List<String> modeLore(Mode current, boolean pack, boolean rightClick) {
        List<String> lore = new ArrayList<>();
        lore.add("§7Où s'affichent les quêtes suivies :");
        for (Mode m : Mode.values()) {
            String detail = switch (m) {
                case VISIBLE  -> pack ? "panneau à gauche de l'écran" : "tableau à droite de l'écran";
                case ACCROCHE -> "avec la liste des joueurs (Tab)";
                case MASQUE   -> "nulle part";
            };
            String name = m.label.substring(0, 1).toUpperCase(Locale.FRENCH) + m.label.substring(1);
            lore.add(m == current ? "§a▶ §f" + name + " §7— " + detail : "§8   " + name + " — " + detail);
        }
        lore.add("");
        lore.add("§e▶ Clic" + (rightClick ? " gauche" : "") + " : " + current.next().label);
        if (rightClick) lore.add("§e◀ Clic droit : " + current.previous().label);
        lore.add("§8Choix gardé dans vos préférences.");
        return lore;
    }

    /* =========================
       POSITION DU PANNEAU
       ========================= */

    /** Indice dans {@code QP_LEFT} : 0 = bord de l'écran, 1 = à gauche, 2 = contre l'inventaire. */
    public int panelPosition(Player player) {
        int pos = prefs.getInt(player, PlayerPrefs.QUEST_PANEL_POS, 0);
        return Math.max(0, Math.min(QP_LEFT.length - 1, pos));
    }

    public void setPanelPosition(Player player, int position) {
        int pos = Math.max(0, Math.min(QP_LEFT.length - 1, position));
        prefs.set(player, PlayerPrefs.QUEST_PANEL_POS, pos == 0 ? null : String.valueOf(pos));
        updateHUD(player);
    }

    /* =========================
       SUIVI PAR PNJ
       ========================= */

    /** PNJ masqués (tags) du joueur. */
    private Set<String> untracked(Player player) {
        String raw = prefs.get(player, PlayerPrefs.QUEST_UNTRACKED);
        if (raw == null || raw.isBlank()) return new HashSet<>();
        return new HashSet<>(Arrays.asList(raw.split(",")));
    }

    private void saveUntracked(Player player, Set<String> tags) {
        tags.removeIf(String::isBlank);
        prefs.set(player, PlayerPrefs.QUEST_UNTRACKED, tags.isEmpty() ? null : String.join(",", new TreeSet<>(tags)));
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
        prefs.set(player, PlayerPrefs.QUEST_UNTRACKED, null);
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
            if ((active.contains(QuestManager.activeKey(npc, false))
                    || active.contains(QuestManager.activeKey(npc, true))) && !paused(npc)) list.add(npc);
        }
        return list;
    }

    /* =========================
       MISE À JOUR
       ========================= */

    public void updateHUD(Player player) {
        UUID uuid = player.getUniqueId();
        boolean pack = ui != null && ui.active(player);
        Mode mode = mode(player);

        // Accroché : rien ne reste à l'écran, le suivi passe dans la liste des joueurs ; elle n'est
        // renvoyée que si son contenu change (progression, quête acceptée ou validée…)
        if (mode == Mode.ACCROCHE) {
            hideBar(player);
            if (sidebarCleared.add(uuid)) player.setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard());
            TabQuests now = pack ? new TabQuests(tabPanelRows(player), null) : new TabQuests(null, tabTextLines(player));
            if (!now.equals(tab.put(uuid, now))) refreshTab(player);
            return;
        }
        if (tab.remove(uuid) != null) refreshTab(player); // le suivi quitte la liste des joueurs
        if (mode == Mode.MASQUE) return;

        // Joueur équipé du pack : panneau illustré à gauche, à la place du tableau de droite
        if (pack) {
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
        List<String> lines = new ArrayList<>(raidLines.apply(player)); // expédition en cours d'abord

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
            if (paused(npc)) continue; // sans logis : quêtes en pause

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

            String label = com.citycore.quest.QuestConfig.label(npc, obj); // famille (« Laine »), créature de mod…

            lines.add((done ? "§a✔ " : "§7• ") + "§f"
                    + truncate(label, 14) + " "
                    + (done ? "§a" : "§f") + current + "§7/§f" + required);
        }

        return lines;
    }

    /* =========================
       SUIVI ACCROCHÉ À LA LISTE DES JOUEURS (Tab)
       ========================= */

    /**
     * Lignes du panneau « Quêtes » de la liste des joueurs (suivi accroché, joueur équipé du pack) ;
     * null si le suivi n'y est pas accroché. Lu par CityCoreHUD à chaque envoi de l'en-tête.
     */
    public List<UiHud.Row> tabRows(Player player) {
        TabQuests t = tab.get(player.getUniqueId());
        return t == null ? null : t.rows();
    }

    /** Pied de la liste des joueurs (suivi accroché, joueur sans le pack) ; vide sinon. */
    public Component tabFooter(Player player) {
        TabQuests t = tab.get(player.getUniqueId());
        if (t == null || t.lines() == null) return Component.empty();
        TextComponent.Builder footer = Component.text()
                .append(Component.newline())
                .append(Component.text("— 📋 Quêtes —", NamedTextColor.GOLD));
        for (String line : t.lines()) {
            footer.append(Component.newline()).append(LegacyComponentSerializer.legacySection().deserialize(line));
        }
        return footer.append(Component.newline()).build();
    }

    /** Panneau accroché : mêmes lignes que le panneau de gauche, ou une ligne « aucune quête ». */
    private List<UiHud.Row> tabPanelRows(Player player) {
        List<UiHud.Row> rows = buildRows(player, TAB_QUEST_ROWS);
        if (rows.isEmpty()) rows = List.of(UiHud.Row.plain(noQuestText(player), UiHud.MUTED));
        return rows;
    }

    /** Lignes du pied de la liste (sans pack) : celles du tableau de droite, raccourcies au besoin. */
    private List<String> tabTextLines(Player player) {
        List<String> lines = new ArrayList<>(buildLines(player));
        while (!lines.isEmpty() && lines.get(lines.size() - 1).replaceAll("§.", "").isBlank()) {
            lines.remove(lines.size() - 1); // pas de ligne vide en bas
        }
        if (lines.isEmpty()) return List.of("§7" + noQuestText(player));
        if (lines.size() > TAB_TEXT_LINES) {
            int rest = lines.size() - (TAB_TEXT_LINES - 1);
            lines = new ArrayList<>(lines.subList(0, TAB_TEXT_LINES - 1));
            lines.add("§7+ " + rest + " ligne" + (rest > 1 ? "s" : ""));
        }
        return lines;
    }

    /** Aucune quête affichée : aucune en cours, ou toutes masquées / en pause. */
    private String noQuestText(Player player) {
        return questManager.getActiveQuestKeys(player.getUniqueId()).isEmpty()
                ? "Aucune quête en cours" : "Aucune quête suivie";
    }

    private void refreshTab(Player player) {
        if (tabRefresher != null && player.isOnline()) tabRefresher.accept(player);
    }

    /* =========================
       PANNEAU ILLUSTRÉ (pack « Valmont »)
       ========================= */

    private void showPanel(Player player) {
        UUID uuid = player.getUniqueId();
        if (sidebarCleared.add(uuid)) {
            player.setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard()); // retire le tableau de droite
        }
        List<UiHud.Row> rows = buildRows(player, HUD_MAX_ROWS);
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
        hideBar(player);
        sidebarCleared.remove(player.getUniqueId());
    }

    /**
     * Retire la barre du panneau sans toucher au tableau de droite (suivi accroché) : les autres
     * barres de boss (sièges, dragon…) reprennent leur place habituelle en haut de l'écran.
     */
    private void hideBar(Player player) {
        BossBar bar = panels.remove(player.getUniqueId());
        if (bar != null) player.hideBossBar(bar);
    }

    /** Panneau compact : bandeau « Quêtes », une ligne par PNJ et par objectif. */
    private Component panel(List<UiHud.Row> rows, int left) {
        com.citycore.ui.UiCanvas c = ui.canvas(0);
        UiHud.panel(c, left, QP_TOP, QP_OFFSET, "Quêtes", rows, HUD_MAX_ROWS);
        c.end(0); // avance totale nulle : le titre de la barre reste centré pile au milieu
        return c.build();
    }

    /** Mêmes informations que le tableau de droite, en lignes de panneau (maxRows au plus). */
    private List<UiHud.Row> buildRows(Player player, int maxRows) {
        UUID uuid = player.getUniqueId();
        Set<String> hidden = untracked(player);
        List<UiHud.Row> rows = new ArrayList<>(raidRows.apply(player)); // expédition en cours d'abord
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
            if (paused(npc)) continue; // sans logis : quêtes en pause

            rows.add(UiHud.Row.head(npc.displayName.replaceAll("§.", ""), UiHud.GOLD));
            if (main != null) addObjectives(rows, uuid, npc, main, false);
            if (special != null) addObjectives(rows, uuid, npc, special, true);
        }
        if (rows.size() > maxRows) {
            int rest = rows.size() - (maxRows - 1);
            rows = new ArrayList<>(rows.subList(0, maxRows - 1));
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
            String label = com.citycore.quest.QuestConfig.label(npc, obj); // famille (« Laine »), créature de mod…
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
