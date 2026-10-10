package com.citycore.npc.mayor;

import com.citycore.city.CityManager;
import com.citycore.economy.ItemTags;
import com.citycore.npc.NPCDataManager;
import com.citycore.npc.NPCState;
import com.citycore.npc.villager.VillagerGUI;
import com.citycore.quest.city.*;
import com.citycore.util.TypewriterUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

public class MayorQuestGUI {

    private final CityQuestManager    questManager;
    private final FindNpcQuestManager findNpcQuestManager;
    private final CityManager         cityManager;
    private final NPCDataManager      npcDataManager;
    private final JavaPlugin          plugin;
    /** Identifiant de quête stocké dans l'icône (la liste peut changer pendant que le menu est ouvert). */
    private final NamespacedKey       questKey;
    private com.citycore.ui.screens.MayorScreens themed;

    void setThemed(com.citycore.ui.screens.MayorScreens themed) {
        this.themed = themed;
    }

    public MayorQuestGUI(CityQuestManager questManager, FindNpcQuestManager findNpcQuestManager,
                         CityManager cityManager, NPCDataManager npcDataManager, JavaPlugin plugin) {
        this.questManager        = questManager;
        this.findNpcQuestManager = findNpcQuestManager;
        this.cityManager         = cityManager;
        this.npcDataManager      = npcDataManager;
        this.plugin              = plugin;
        this.questKey            = new NamespacedKey(plugin, "city_quest");
    }

    /** Début de titre commun à tous les paliers (détection indépendante du niveau). */
    public static final String TITLE_PREFIX = ChatColor.DARK_PURPLE + "Quêtes — ";

    public static String title(CityTier tier) {
        return TITLE_PREFIX
                + ChatColor.stripColor(tier.tierName)
                + " (Niv." + tier.level + ")";
    }

    /** Taille du menu : lignes de quêtes + barre du bas. */
    private static int sizeFor(int quests) {
        int rows = Math.max(1, Math.min(5, (int) Math.ceil(quests / 9.0)));
        return (rows + 1) * 9;
    }

    public void open(Player player) {
        if (themed != null && themed.openQuests(player)) return;
        CityTier tier = CityTier.fromLevel(questManager.getCityLevel());
        questManager.syncCurrentTier();

        List<CityQuest> quests = questManager.displayedQuests();
        int size = sizeFor(quests.size());
        int base = size - 9;
        Inventory inv = Bukkit.createInventory(null, size, title(tier));

        // ── Barre du bas ─────────────────────────────────────────
        ItemStack filler = makeItem(Material.PURPLE_STAINED_GLASS_PANE, " ", List.of());
        for (int i = base; i < size; i++) inv.setItem(i, filler);

        // ── Quêtes ───────────────────────────────────────────────
        for (int i = 0; i < Math.min(quests.size(), base); i++) {
            ItemStack icon = buildQuestItem(player, quests.get(i));
            ItemMeta meta = icon.getItemMeta();
            meta.getPersistentDataContainer().set(questKey, PersistentDataType.STRING, quests.get(i).id());
            icon.setItemMeta(meta);
            inv.setItem(i, icon);
        }

        // ── Chapitre (récit d'Alderic) ───────────────────────────
        List<String> storyLore = new ArrayList<>();
        for (String line : tier.story) {
            storyLore.add("§7" + ChatColor.stripColor(TypewriterUtil.resolve(line, player))
                    .replace("Alderic : ", ""));
        }
        storyLore.add("");
        storyLore.add("§eCliquez pour réécouter Alderic");
        inv.setItem(base, makeItem(Material.WRITABLE_BOOK, "§6📜 " + tier.chapter, storyLore));

        // ── Bouton améliorer ville ───────────────────────────────
        inv.setItem(base + 4, buildUpgradeButton(tier));

        // ── Bouton retour ────────────────────────────────────────
        inv.setItem(base + 8, makeItem(Material.ARROW,
                "§7◀ Retour", List.of("§7Retourner au menu du Maire")));

        player.openInventory(inv);
    }

    /* =========================
       ITEMS DE QUÊTE
       ========================= */

    public ItemStack buildQuestItem(Player player, CityQuest quest) {
        boolean done   = questManager.isCompleted(quest.id());
        int     target = questManager.target(quest);
        int     current = done ? target : questManager.computeCurrentValue(quest);

        List<String> lore = new ArrayList<>();
        lore.add("§7" + questManager.description(quest));
        if (quest.optional()) lore.add("§8Bonus : ne bloque pas l'amélioration.");
        lore.add("");

        if (done) {
            lore.add("§a✔ Complétée !");
            return makeItem(Material.LIME_DYE, "§a✔ " + ChatColor.stripColor(quest.displayName()), lore);
        }

        switch (quest.type()) {
            case FIND_NPC -> {
                boolean active = findNpcQuestManager.isQuestActive(quest.id());
                lore.add(step(current >= 1 || active, "Trouver " + quest.targetNpc().displayName));
                lore.add(step(current >= 2, "Le ramener en ville"));
                lore.add(step(current >= 3, "L'installer dans un bâtiment"));
                lore.add("");
                if (current >= 2) {
                    lore.add("§7Parlez-lui pour choisir son bâtiment.");
                } else if (active || current >= 1) {
                    lore.add("§eCliquez pour récupérer la piste.");
                } else {
                    lore.add("§eCliquez pour lancer les recherches.");
                }
            }
            case DELIVER_ITEMS -> {
                lore.add("§7Livré : §f" + current + " §7/ §f" + target);
                lore.add(progressBar(current, target));
                lore.add("");
                int owned = ItemTags.countAny(player, quest.materials());
                lore.add("§eClic gauche §7: livrer depuis votre sac §8(" + owned + ")");
                CityQuestManager.Requisition quote = questManager.requisition(quest, false);
                if (quote.available() > 0) {
                    lore.add("§eShift + clic droit §7: réquisitionner");
                    lore.add("§8  " + quote.available() + " en stock chez les artisans");
                    lore.add("§8  " + quote.taken() + " finançables : §6" + quote.cost()
                            + " coins §8(caisse)");
                } else {
                    lore.add("§8Aucun stock d'artisan à réquisitionner.");
                }
            }
            case NPC_LEVEL -> {
                boolean installed = npcDataManager.getState(quest.targetNpc()) == NPCState.ASSIGNED;
                lore.add("§7Rang actuel : " + (installed
                        ? VillagerGUI.getLevelName(current) : "§cnon installé"));
                lore.add("§7Rang requis : " + VillagerGUI.getLevelName(target));
                lore.add(progressBar(current, target));
            }
            case STOCK_LEVEL -> {
                lore.add("§7Stock de " + quest.targetNpc().displayName + " §7: §f"
                        + current + " §7/ §f" + target);
                lore.add(progressBar(current, target));
                lore.add("§8Vendez-lui ces ressources pour remplir ses réserves.");
            }
            default -> {
                lore.add("§7Progression : §f" + current + " §7/ §f" + target);
                lore.add(progressBar(current, target));
            }
        }
        return makeItem(quest.icon(), "§e" + ChatColor.stripColor(quest.displayName()), lore);
    }

    public ItemStack buildUpgradeButton(CityTier tier) {
        if (tier.isMaxTier()) {
            return makeItem(Material.BEACON, "§6⭐ Niveau maximum atteint !",
                    List.of("§7Votre cité est la capitale des Marches.",
                            "§7Les hauts faits restent à accomplir."));
        }
        int cost     = questManager.upgradeCost(tier);
        int treasury = cityManager.getCityCoins();
        int chunks   = questManager.rewardChunks(tier);

        if (!questManager.isTierComplete(tier)) {
            long remaining = tier.quests.stream()
                    .filter(q -> !q.optional())
                    .filter(q -> !questManager.isCompleted(q.id()))
                    .count();
            return makeItem(Material.BARRIER, "§c⬆ Améliorer la ville",
                    List.of("§cComplétez toutes les quêtes d'abord.",
                            "§7Quêtes restantes : §c" + remaining,
                            "",
                            "§7Charte : §6" + cost + " coins §7(caisse : §6" + treasury + "§7)"));
        }
        boolean canPay = treasury >= cost;
        return makeItem(canPay ? Material.NETHER_STAR : Material.GOLD_NUGGET,
                "§a⬆ Améliorer la ville → " + tier.next().tierName,
                List.of("§7Toutes les quêtes sont complètes !",
                        "",
                        "§7Charte : §6" + cost + " coins §7(payée par la caisse)",
                        "§7Caisse : §6" + treasury + " coins",
                        "§7Récompense : §f+" + chunks + " emplacements de chunk",
                        "",
                        canPay ? "§aCliquez pour passer au chapitre suivant"
                                : "§cIl manque §6" + (cost - treasury) + " coins §cdans la caisse"));
    }

    /* =========================
       CLICS
       ========================= */

    /**
     * @param inventorySize taille du menu ouvert (la barre du bas en dépend)
     * @return false si retour au menu du maire demandé
     */
    public boolean handleClick(Player player, int slot, boolean shiftRight,
                               ItemStack clicked, int inventorySize) {
        CityTier tier = CityTier.fromLevel(questManager.getCityLevel());
        int base = inventorySize - 9;

        if (slot == base + 8) return false;

        if (slot == base) {
            playStory(player);
            return true;
        }

        if (slot == base + 4) {
            handleUpgrade(player, tier);
            return true;
        }

        if (slot < 0 || slot >= base || clicked == null || !clicked.hasItemMeta()) return true;
        String questId = clicked.getItemMeta().getPersistentDataContainer()
                .get(questKey, PersistentDataType.STRING);
        return clickQuest(player, questId, shiftRight);
    }

    /** Réécoute le récit d'Alderic pour le chapitre en cours. */
    public void playStory(Player player) {
        player.closeInventory();
        TypewriterUtil.play(plugin, player, CityTier.fromLevel(questManager.getCityLevel()).story, null);
    }

    /** Signe la charte (passage au palier suivant) si tout est prêt. */
    public void upgrade(Player player) {
        handleUpgrade(player, CityTier.fromLevel(questManager.getCityLevel()));
    }

    /**
     * Clic sur une quête : lance/reprend une recherche, livre, réquisitionne.
     * @return toujours true (menu géré)
     */
    public boolean clickQuest(Player player, String questId, boolean shiftRight) {
        CityQuest quest = questManager.displayedQuests().stream()
                .filter(q -> q.id().equals(questId))
                .findFirst().orElse(null);
        if (quest == null || questManager.isCompleted(quest.id())) {
            open(player); // la liste a changé entre-temps
            return true;
        }

        switch (quest.type()) {
            case FIND_NPC -> {
                if (questManager.computeCurrentValue(quest) >= 2) {
                    player.sendMessage("§7" + quest.targetNpc().displayName
                            + " §7est en ville : parlez-lui pour l'installer dans un bâtiment.");
                    return true;
                }
                player.closeInventory();
                boolean alreadyActive = findNpcQuestManager.isQuestActive(quest.id());
                // Lance la recherche, ou reprend la piste en cours (suivi du joueur inclus)
                findNpcQuestManager.triggerQuest(quest, player);
                if (alreadyActive) {
                    player.sendMessage("§7🗺 Recherche en cours : §e" + ChatColor.stripColor(quest.displayName()));
                }
            }
            case DELIVER_ITEMS -> {
                if (shiftRight) {
                    CityQuestManager.Requisition r = questManager.requisition(quest, true);
                    if (r.taken() > 0) {
                        player.sendMessage("§a✅ Réquisition : §f" + r.taken()
                                + " §aunités prises dans les stocks des artisans pour §6"
                                + r.cost() + " coins §a(caisse).");
                    } else {
                        player.sendMessage("§c❌ Rien à réquisitionner (stocks vides ou caisse insuffisante).");
                    }
                } else {
                    int delivered = questManager.deliver(player, quest);
                    if (delivered > 0) {
                        player.sendMessage("§a✅ Vous livrez §f" + delivered + " §aunités au chantier.");
                        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_YES, 1f, 1f);
                    } else {
                        player.sendMessage("§c❌ Vous n'avez rien de ce qui est demandé.");
                    }
                }
                open(player);
            }
            default -> { }
        }
        return true;
    }

    private void handleUpgrade(Player player, CityTier tier) {
        switch (questManager.upgradeCityLevel()) {
            case MAX_LEVEL  -> { }
            case INCOMPLETE -> player.sendMessage("§c❌ Complétez d'abord toutes les quêtes !");
            case NO_FUNDS   -> player.sendMessage("§c❌ La caisse ne peut pas payer la charte ("
                    + questManager.upgradeCost(tier) + " coins). §7💡 §e/city deposit <montant>");
            case SUCCESS    -> {
                CityTier next = tier.next();
                player.closeInventory();
                // Ferme les menus de quêtes ouverts chez les autres joueurs (contenu périmé)
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getOpenInventory().getTitle().startsWith(TITLE_PREFIX)
                            || com.citycore.ui.screens.MayorScreens.isCityQuestScreen(
                                    p.getOpenInventory().getTopInventory().getHolder())) {
                        p.closeInventory();
                    }
                }
                for (Player p : Bukkit.getOnlinePlayers()) {
                    p.sendTitle(next.tierName, "§7" + next.chapter, 10, 70, 20);
                    p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
                    p.sendMessage("");
                    p.sendMessage("§a🎉 La ville devient " + next.tierName + " §a(niveau §e"
                            + next.level + "§a) !");
                    p.sendMessage("§7+" + questManager.rewardChunks(tier)
                            + " emplacements de chunk offerts.");
                    p.sendMessage("");
                    TypewriterUtil.play(plugin, p, next.story, null);
                }
                return;
            }
        }
        open(player);
    }

    /* =========================
       HELPERS
       ========================= */

    private String step(boolean done, String label) {
        return (done ? "§a✔ " : "§8○ ") + "§7" + label;
    }

    private String progressBar(int current, int target) {
        int filled = target <= 0 ? 10 : (int) ((double) current / target * 10);
        filled = Math.max(0, Math.min(filled, 10));
        return "§a" + "▬".repeat(filled) + "§8" + "▬".repeat(10 - filled);
    }

    private ItemStack makeItem(Material mat, String name, List<String> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta  meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS,
                ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        item.setItemMeta(meta);
        return item;
    }
}
