package com.citycore.npc.mayor;

import com.citycore.building.BuildingManager;
import com.citycore.city.City;
import com.citycore.city.CityManager;
import com.citycore.npc.CityNPC;
import com.citycore.npc.NPCGui;
import com.citycore.npc.NPCManager;
import com.citycore.quest.city.CityQuestManager;
import com.citycore.quest.city.CityTier;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public class MayorGUI implements NPCGui {

    public static final String GUI_TITLE = ChatColor.GOLD + CityNPC.MAYOR.displayName + " — Maire";

    public static final int SLOT_INFO      = 0;
    public static final int SLOT_BUILDINGS = 2;
    public static final int SLOT_ECONOMY   = 4;
    public static final int SLOT_EXPAND    = 6;
    public static final int SLOT_FOLLOW    = 8;

    private final CityManager      cityManager;
    private final NPCManager       npcManager;
    private final MayorBuildingGUI buildingGUI;
    private final MayorEconomyGUI  economyGUI;
    private final MayorQuestGUI    questGUI;
    private final CityQuestManager cityQuestManager;

    public MayorGUI(CityManager cityManager, NPCManager npcManager,
                    BuildingManager buildingManager, Economy economy,
                    MayorQuestGUI questGUI, CityQuestManager cityQuestManager) {
        this.cityManager      = cityManager;
        this.npcManager       = npcManager;
        this.buildingGUI      = new MayorBuildingGUI(buildingManager);
        this.economyGUI       = new MayorEconomyGUI(cityManager, economy, cityQuestManager);
        this.questGUI         = questGUI;
        this.cityQuestManager = cityQuestManager;
    }

    public MayorBuildingGUI getBuildingGUI()    { return buildingGUI; }
    public MayorEconomyGUI  getEconomyGUI()     { return economyGUI; }
    public MayorQuestGUI    getQuestGUI()        { return questGUI; }
    public CityQuestManager getCityQuestManager(){ return cityQuestManager; }

    @Override public String getTitle() { return GUI_TITLE; }

    @Override
    public void handleClick(Player player, int slot) {
        switch (slot) {
            case SLOT_INFO -> questGUI.open(player);
            case SLOT_BUILDINGS -> buildingGUI.open(player);
            case SLOT_ECONOMY   -> economyGUI.open(player);
            case SLOT_EXPAND -> {
                int price = cityManager.getNextExpandPrice();
                CityManager.ExpandResult result = cityManager.expandMaxChunks();
                if (result.success()) {
                    player.sendMessage("§a✅ Capacité étendue ! Max : §f"
                            + result.newMaxChunks());
                    player.sendMessage("§7Caisse restante : §6"
                            + result.newBalance() + " coins");
                } else {
                    int missing = price - cityManager.getCityCoins();
                    player.sendMessage("§c❌ Fonds insuffisants. Il manque §f"
                            + missing + " coins§c.");
                    player.sendMessage("§7💡 §e/city deposit <montant>");
                }
                open(player);
            }
            case SLOT_FOLLOW -> {
                if (npcManager.isFollowing(player, CityNPC.MAYOR)) {
                    npcManager.stopFollowing(player, CityNPC.MAYOR);
                    player.sendMessage(CityNPC.MAYOR.displayName
                            + " §7s'est arrêté de vous suivre.");
                } else {
                    npcManager.startFollowing(player, CityNPC.MAYOR);
                    player.sendMessage(CityNPC.MAYOR.displayName
                            + " §avous suit désormais.");
                }
                open(player);
            }
        }
    }

    public void open(Player player) {
        City city = cityManager.getCity();
        if (city == null) return;

        CityTier tier         = CityTier.fromLevel(cityQuestManager.getCityLevel());
        boolean  tierComplete = cityQuestManager.isTierComplete(tier);

        Inventory inv = Bukkit.createInventory(null, 9, GUI_TITLE);

        ItemStack filler = makeItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 9; i++) inv.setItem(i, filler);

        // ── INFO ─────────────────────────────────────────────────
        inv.setItem(SLOT_INFO, makeItem(Material.BOOK, "§6📖 Informations",
                List.of(
                        "§7Niveau : §f" + city.getLevel(),
                        "§7Caisse : §6" + city.getCoins() + " coins",
                        "§7Chunks : §f" + city.getClaimedChunks()
                                + " §7/ §f" + city.getMaxChunks(),
                        "", "§eCliquez pour afficher"
                )));

        // ── INFO + QUÊTES (fusionné) ─────────────────────────────
        inv.setItem(SLOT_INFO, makeItem(
                tierComplete ? Material.NETHER_STAR : Material.BOOK,
                tierComplete ? "§6📖 Infos de la ville §l[PRÊT]" : "§6📖 Infos de la ville",
                List.of(
                        "§7Niveau  : §f" + city.getLevel(),
                        "§7Palier  : " + tier.tierName,
                        "§7Taille  : §f" + city.getClaimedChunks()
                                + " §7/ §f" + city.getMaxChunks(),
                        "",
                        tierComplete
                                ? "§a✔ Toutes les quêtes complètes !"
                                : "§7Quêtes du palier disponibles.",
                        "",
                        "§eCliquez pour ouvrir les quêtes"
                )));

        // ── BÂTIMENTS ────────────────────────────────────────────
        inv.setItem(SLOT_BUILDINGS, makeItem(Material.BRICKS, "§a🏗 Bâtiments",
                List.of("§7Gérez les bâtiments de la ville.",
                        "", "§eCliquez pour ouvrir")));

        // ── ÉCONOMIE ─────────────────────────────────────────────
        inv.setItem(SLOT_ECONOMY, makeItem(Material.GOLD_INGOT, "§6💰 Économie",
                List.of(
                        "§7Caisse : §6" + cityManager.getCityCoins() + " coins",
                        "", "§eCliquez pour déposer"
                )));

        // ── EXPAND ───────────────────────────────────────────────
        int     price     = cityManager.getNextExpandPrice();
        int     balance   = cityManager.getCityCoins();
        boolean canAfford = cityManager.canAfford(price);
        inv.setItem(SLOT_EXPAND, makeItem(
                canAfford ? Material.EMERALD : Material.BARRIER,
                "§6🗺 Agrandir la ville",
                List.of(
                        "§7Ajoute §f+1 slot §7de chunk",
                        "", "§7Prix   : §6" + price + " coins",
                        "§7Caisse : §6" + balance + " coins",
                        "", canAfford ? "§aCliquez pour acheter"
                                : "§cFonds insuffisants"
                )));

        // ── FOLLOW ───────────────────────────────────────────────
        boolean following = npcManager.isFollowing(player, CityNPC.MAYOR);
        inv.setItem(SLOT_FOLLOW, makeItem(
                following ? Material.REDSTONE : Material.LIME_DYE,
                following ? "§c⛔ Arrêter de suivre" : "§a👣 Demander de suivre",
                List.of(
                        following ? "§7" + CityNPC.MAYOR.displayName + " arrêtera de vous suivre."
                                : "§7" + CityNPC.MAYOR.displayName + " vous suivra",
                        "", "§eCliquez pour "
                                + (following ? "arrêter" : "activer")
                )));

        player.openInventory(inv);
    }

    private ItemStack makeItem(Material mat, String name, List<String> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta  meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack makeItem(Material mat, String name) {
        return makeItem(mat, name, List.of());
    }
}