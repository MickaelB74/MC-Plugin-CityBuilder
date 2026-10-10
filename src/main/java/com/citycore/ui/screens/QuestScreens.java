package com.citycore.ui.screens;

import com.citycore.economy.MarketService;
import com.citycore.npc.CityNPC;
import com.citycore.npc.NPCDataManager;
import com.citycore.npc.villager.VillagerGUI;
import com.citycore.quest.QuestDefinition;
import com.citycore.quest.QuestGUI;
import com.citycore.quest.QuestListener;
import com.citycore.quest.QuestObjective;
import com.citycore.quest.personal.NPCPersonalQuest;
import com.citycore.quest.personal.NPCPersonalQuestGUI;
import com.citycore.quest.personal.NPCPersonalQuestManager;
import com.citycore.ui.UiCanvas;
import com.citycore.ui.UiItems;
import com.citycore.ui.UiKit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static com.citycore.ui.UiLayout.*;

/**
 * Écrans illustrés des quêtes d'un PNJ (principale et spéciale) et des récits (quêtes personnelles)
 * d'un artisan. La logique reste celle de {@link QuestListener} et {@link QuestGUI}.
 */
public final class QuestScreens {

    private final UiKit                   kit;
    private final NPCDataManager          data;
    private final NPCPersonalQuestManager personal;
    private final MarketService           market;
    private final Map<CityNPC, QuestGUI>    quests    = new EnumMap<>(CityNPC.class);
    private final Map<CityNPC, VillagerGUI> villagers = new EnumMap<>(CityNPC.class);
    private QuestListener actions;

    public QuestScreens(UiKit kit, NPCDataManager data, NPCPersonalQuestManager personal, MarketService market) {
        this.kit      = kit;
        this.data     = data;
        this.personal = personal;
        this.market   = market;
    }

    /** Associe les menus classiques d'un PNJ (retour vers son accueil, récits). */
    public void register(QuestGUI questGUI, VillagerGUI villagerGUI) {
        quests.put(questGUI.getNpcType(), questGUI);
        villagers.put(questGUI.getNpcType(), villagerGUI);
        questGUI.setThemed(this);
        if (questGUI.getPersonalQuestGUI() != null) questGUI.getPersonalQuestGUI().setThemed(this);
    }

    /** Actions des quêtes (créées après les écrans). */
    public void setActions(QuestListener actions) {
        this.actions = actions;
    }

    public boolean openQuests(Player p, QuestGUI gui) {
        return actions != null && kit.active(p) && kit.show(new Commands(p, gui));
    }

    public boolean openPersonal(Player p, NPCPersonalQuestGUI personalGUI) {
        QuestGUI gui = quests.get(personalGUI.getNpc());
        return gui != null && actions != null && kit.active(p) && kit.show(new Stories(p, gui, personalGUI));
    }

    /* =========================
       COMMANDES
       ========================= */

    private final class Commands extends ValmontMenu {

        private final QuestGUI gui;
        private final CityNPC  npc;

        Commands(Player p, QuestGUI gui) {
            super(QuestScreens.this.kit, p);
            this.gui = gui;
            this.npc = gui.getNpcType();
        }

        @Override
        protected void draw(UiCanvas c) {
            c.background("bg_quests");
            nameplate(c, plain(npc.displayName), npc.function);
            back(c, 0, "Retour", () -> {
                VillagerGUI v = villagers.get(npc);
                if (v != null) v.open(player); else player.closeInventory();
            });
            c.sprite("rib_quetes_pnj", sx(1), sy(0));
            info(c, 7, "§d📜 Quêtes de " + npc.displayName, List.of(
                    "§7Principale : repos de §f" + market.cfg().mainCooldownMinutes() + " min §7après validation",
                    "§7Spéciale : repos de §f" + market.cfg().specialCooldownMinutes() + " min",
                    "", "§7Chaque joueur a ses propres quêtes.",
                    "§7Les objets achetés à un PNJ ne comptent pas.",
                    "§7Fermez votre inventaire pour mettre", "§7la progression à jour."));
            close(c);

            card(c, false);
            card(c, true);

            int level = data.getLevel(npc);
            boolean stories = gui.hasPersonalQuests(level);
            int count = npc.getPersonalQuestsForLevel(level).size();
            int total = npc.getAllPersonalQuests().size();
            c.sprite(stories ? "vb_recits" : "vb_recits_off", sx(4), sy(1));
            zone(4, 1, 1, 4, stories ? "§5✦ Récits de " + npc.displayName : "§8✦ Récits",
                    stories ? List.of("§7" + count + " récit" + (count > 1 ? "s" : "") + " débloqué"
                                    + (count > 1 ? "s" : "") + " sur " + total + ", à votre nom.", "", "§a▶ Cliquez pour consulter")
                            : List.of("§7Aucun récit pour le moment.", "§8De nouveaux récits s'ouvrent",
                            "§8quand " + plain(npc.displayName) + " monte en rang."),
                    stories ? t -> actions.openPersonalQuests(player, gui) : null);
        }

        /** Carte d'une quête : récompense, deux objectifs, bouton d'action. */
        private void card(UiCanvas c, boolean special) {
            QuestGUI.Card card = gui.card(player, special);
            QuestDefinition q = card.quest();
            int col0 = special ? 5 : 0, x0 = sx(col0), right = x0 + 68;
            String title = special ? "§d✦ Quête Spéciale" : "§9📖 Quête Principale";

            // En-tête : récompense, repos ou rien
            List<String> headerLore;
            if (q != null) {
                String reward = fmt(q.reward().coins());
                int tw = c.width(reward, true);
                c.sprite("coin", right - tw - 11, sy(1) + 5);
                c.text(reward, right - tw, rowText(1), GOLD, true);
                ItemMeta meta = gui.buildQuestItem(player, q, special ? Material.NETHER_STAR : Material.BOOK,
                        card.active()).getItemMeta();
                headerLore = meta.getLore();
            } else if (card.cooldownMs() > 0) {
                long minutes = Math.max(1, (card.cooldownMs() + 59_999) / 60_000);
                c.textRight(minutes + " MIN", right, rowText(1), CREAM, true);
                headerLore = List.of("§7" + npc.displayName + " §7prépare", "§7sa prochaine quête.", "",
                        "§eDisponible dans " + minutes + " min");
            } else {
                c.textRight("AUCUNE", right, rowText(1), CREAM, true);
                headerLore = List.of("§7Aucune quête de ce type", "§7pour le moment.");
            }
            zone(col0, 1, 4, 1, title, headerLore, null);

            // Objectifs (deux au plus)
            if (q != null) {
                List<QuestObjective> objectives = q.objectives();
                for (int i = 0; i < Math.min(2, objectives.size()); i++) {
                    QuestObjective o = objectives.get(i);
                    int row = 2 + i;
                    int current = card.active() ? card.progress().getOrDefault(o.id(), 0) : 0;
                    int required = o.amount();
                    boolean done = card.active() && current >= required;
                    String label = "§f" + plain(gui.buildObjectiveLabel(o));
                    List<String> lore = List.of("§7Progression : §f" + Math.min(current, required) + " §7/ §f" + required);
                    item(col0, row, UiItems.shown(iconOf(o), label, lore, false), null);
                    c.text(fmt(Math.min(current, required)) + " / " + fmt(required) + (done ? " ✔" : ""),
                            x0 + 22, rowTop(row), done ? 0x2E6B22 : INK, false);
                    int seg = (int) Math.round(Math.min(1, (double) current / Math.max(1, required)) * QUEST_SEGMENTS);
                    c.repeat("seg_green", x0 + 22, sy(row) + 12, seg);
                    zone(col0 + 1, row, 3, 1, label, lore, null);
                }
            }

            // Bouton d'action
            String sprite;
            String name;
            List<String> lore;
            Consumer<ClickType> action = null;
            if (q == null) {
                sprite = card.cooldownMs() > 0 ? "qb_repos" : "qb_aucune";
                name = card.cooldownMs() > 0 ? "§7⏳ En repos" : "§7Aucune quête";
                lore = headerLore;
            } else if (!card.active()) {
                sprite = "qb_accepter";
                name = "§a▶ Accepter la quête";
                lore = List.of("§7" + q.description(), "", "§7Récompense : §6" + q.reward().coins() + " coins");
                action = t -> actions.clickQuest(player, gui, special);
            } else if (card.ready()) {
                sprite = "qb_valider";
                name = "§6★ Valider la quête";
                lore = List.of("§7Tout est réuni !", "§7Récompense : §6" + q.reward().coins() + " coins");
                action = t -> actions.clickQuest(player, gui, special);
            } else {
                sprite = "qb_encours";
                name = "§e⏳ Quête en cours";
                lore = List.of("§7Rassemblez les objets demandés.", "§7Fermez votre inventaire pour",
                        "§7mettre la progression à jour.");
                action = t -> actions.clickQuest(player, gui, special);
            }
            c.sprite(sprite, x0, sy(4));
            zone(col0, 4, 4, 1, name, lore, action);
        }
    }

    /** Icône d'un objectif : l'objet demandé, l'œuf de la créature à chasser… */
    private static Material iconOf(QuestObjective o) {
        if (o.isMaterialObjective() && o.material() != null && o.material().isItem()) return o.material();
        if (o.isEntityObjective() && o.entity() != null) {
            Material egg = Material.matchMaterial(o.entity().name() + "_SPAWN_EGG");
            return egg != null ? egg : Material.IRON_SWORD;
        }
        if (o.isBiomeObjective()) return Material.FILLED_MAP;
        return Material.WRITABLE_BOOK;
    }

    /* =========================
       RÉCITS (QUÊTES PERSONNELLES)
       ========================= */

    private final class Stories extends ValmontMenu {

        private final QuestGUI            gui;
        private final NPCPersonalQuestGUI personalGUI;
        private final CityNPC             npc;

        Stories(Player p, QuestGUI gui, NPCPersonalQuestGUI personalGUI) {
            super(QuestScreens.this.kit, p);
            this.gui         = gui;
            this.personalGUI = personalGUI;
            this.npc         = personalGUI.getNpc();
        }

        @Override
        protected void draw(UiCanvas c) {
            c.background("bg_personal");
            nameplate(c, plain(npc.displayName), npc.function);
            Runnable journal = personalGUI.journalBack(player); // ouvert depuis le journal de quêtes
            if (journal != null) back(c, 0, "Journal de quêtes", journal);
            else back(c, 0, "Quêtes", () -> gui.open(player));
            c.sprite("rib_recits", sx(1), sy(0));
            info(c, 7, "§5✦ Récits de " + npc.displayName, List.of(
                    "§7Quêtes personnelles : elles sont à", "§7votre nom et ne se font qu'une fois.",
                    "§7De nouveaux récits s'ouvrent quand", "§7" + plain(npc.displayName) + " monte en rang.", "",
                    "§a✔ §7accompli   §6● §7en cours   §e! §7à accepter"));
            close(c);

            UUID uuid = player.getUniqueId();
            int level = data.getLevel(npc);
            List<CityNPC.RankedQuest> all = npc.getAllPersonalQuests();
            for (int i = 0; i < Math.min(5, all.size()); i++) {
                CityNPC.RankedQuest ranked = all.get(i);
                NPCPersonalQuest quest = ranked.quest();
                int row = 1 + i, index = i;
                if (ranked.level() > level) {
                    lockedRow(c, row, quest, ranked.level());
                    continue;
                }
                boolean done   = personal.isCompleted(uuid, npc, quest.id());
                boolean active = personal.isActive(uuid, npc, quest.id());
                int progress   = personal.getProgress(uuid, npc, quest.id());
                int target     = Math.max(1, quest.targetAmount());

                ItemMeta tip = personalGUI.buildQuestItem(uuid, quest).getItemMeta();
                List<String> lore = new ArrayList<>(tip.getLore());
                if (!done && !active) { lore.add(""); lore.add("§a▶ Cliquez pour accepter"); }
                Consumer<ClickType> click = t -> actions.clickPersonalQuest(player, gui, index);

                ItemStack icon = UiItems.shown(done ? Material.LIME_DYE : quest.icon(), tip.getDisplayName(), lore, false);
                item(0, row, icon, click);
                c.text(c.fit(plain(quest.displayName()), 118, false), sx(1) + 4, rowTop(row),
                        done ? 0x2E6B22 : INK, false);
                int seg = done ? STORY_SEGMENTS : (int) Math.round(Math.min(1, (double) progress / target) * STORY_SEGMENTS);
                c.repeat("seg_thin", sx(1) + 5, sy(row) + 13, seg);
                c.sprite(done ? "badge_check" : active ? "badge_progress" : "badge_new", sx(8) + 4, sy(row) + 4);
                zone(1, row, 8, 1, tip.getDisplayName(), lore, click);
            }
        }

        /** Récit d'un rang supérieur : cadenas, titre illisible, rang requis ; aucun clic. */
        private void lockedRow(UiCanvas c, int row, NPCPersonalQuest quest, int rank) {
            String rankName = plain(VillagerGUI.getLevelName(rank));
            c.sprite("lock", sx(0) + 3, sy(row) + 3);
            int rankWidth = c.width(rankName, false);
            c.textRight(rankName, sx(8) - 2, rowTop(row), MUTED, false);
            c.text(c.fit(CityNPC.obfuscate(plain(quest.displayName())), 118 - rankWidth - 6, false),
                    sx(1) + 4, rowTop(row), MUTED, false);
            zone(0, row, 9, 1, "§8🔒 §7Récit verrouillé", List.of(
                    "§8§k" + plain(quest.displayName()), "",
                    "§7Se débloque quand " + npc.displayName,
                    "§7atteint le rang " + VillagerGUI.getLevelName(rank) + "§7."), null);
        }
    }
}
