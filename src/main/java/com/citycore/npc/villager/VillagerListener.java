package com.citycore.npc.villager;

import com.citycore.city.CityManager;
import com.citycore.npc.CityNPC;
import com.citycore.npc.IntroductionManager;
import com.citycore.npc.NPCDataManager;
import com.citycore.npc.NPCManager;
import com.citycore.quest.QuestGUI;
import com.citycore.util.TypewriterUtil;
import net.citizensnpcs.api.event.NPCRightClickEvent;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;

public class VillagerListener implements Listener {

    private final CityNPC             npcType;
    private final VillagerGUI         gui;
    private final NPCManager          npcManager;
    private final NPCDataManager      dataManager;
    private final Economy             economy;
    private final CityManager         cityManager;
    private final IntroductionManager introManager;
    private final JavaPlugin          plugin;
    private final QuestGUI            questGUI;

    public VillagerListener(CityNPC npcType, VillagerGUI gui, NPCManager npcManager,
                            NPCDataManager dataManager, Economy economy,
                            CityManager cityManager, IntroductionManager introManager,
                            QuestGUI questGUI, JavaPlugin plugin) {
        this.npcType      = npcType;
        this.gui          = gui;
        this.npcManager   = npcManager;
        this.dataManager  = dataManager;
        this.economy      = economy;
        this.cityManager  = cityManager;
        this.introManager = introManager;
        this.plugin       = plugin;
        this.questGUI     = questGUI;
    }

    @EventHandler
    public void onNPCRightClick(NPCRightClickEvent event) {
        if (!npcManager.isNPCType(event.getNPC(), npcType)) return;
        Player player = event.getClicker();

        if (!introManager.hasSeenIntro(player.getUniqueId(), npcType)) {
            introManager.markIntroSeen(player.getUniqueId(), npcType);
            TypewriterUtil.play(plugin, player, npcType.introLines, () -> {
                if (player.isOnline()) gui.open(player);
            });
        } else {
            gui.open(player);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String title = event.getView().getTitle();

        // ── Menu principal ──────────────────────────────────────
        if (title.startsWith(npcType.displayName + " §8— §7" + npcType.function)) {
            event.setCancelled(true);
            if (event.getCurrentItem() == null) return;

            switch (event.getSlot()) {
                case VillagerGUI.SLOT_SELL      -> gui.openSell(player);
                case VillagerGUI.SLOT_INVENTORY -> gui.openInventory(player);
                case VillagerGUI.SLOT_SHOP      -> gui.openShop(player);
                case VillagerGUI.SLOT_FOLLOW    -> {
                    if (npcManager.isFollowing(player, npcType)) {
                        npcManager.stopFollowing(player, npcType);
                        player.sendMessage(npcType.displayName + " §7s'est arrêté de vous suivre.");
                    } else {
                        npcManager.startFollowing(player, npcType);
                        player.sendMessage(npcType.displayName + " §avous suit désormais.");
                    }
                    gui.open(player);
                }
                case VillagerGUI.SLOT_QUESTS -> questGUI.open(player);
            }
            return;
        }

        // ── Sous-menu vendre ────────────────────────────────────
        if (VillagerGUI.titleSell(npcType).equals(title)) {
            event.setCancelled(true);
            if (event.getCurrentItem() == null) return;
            if (event.getSlot() == VillagerGUI.SLOT_BACK) { gui.open(player); return; }

            Material mat = event.getCurrentItem().getType();
            if (!gui.getConfig().isSellable(mat)) return;

            VillagerConfig.SellPrice sp = gui.getConfig().getSellPrice(mat);
            if (sp == null) return;

            int earned = gui.sellMaterial(player, mat);
            if (earned == -1) {
                player.sendMessage("§c❌ Pas assez de §f" + formatName(mat)
                        + "§c. Nécessaire : §f" + sp.quantity() + " items");
            } else {
                economy.depositPlayer(player, earned);

                int setsSold = earned / sp.price();
                int xpGained = (setsSold * gui.getConfig().getXpPerStack())
                        + (earned  * gui.getConfig().getXpPerCoin());

                boolean levelUp = dataManager.addXP(npcType, xpGained,
                        gui.getConfig().getXpThresholds());

                player.sendMessage("§a✅ Vendu ! §6+" + earned + " coins §7(§b+" + xpGained + " XP§7)");
                if (levelUp) {
                    player.sendMessage("§a🎉 §e" + npcType.displayName
                            + " §aest passé niveau §e" + dataManager.getLevel(npcType) + "§a !");
                }
            }
            gui.openSell(player);
            return;
        }

        // ── Sous-menu inventaire ────────────────────────────────
        if (VillagerGUI.titleInventory(npcType).equals(title)) {
            event.setCancelled(true);
            if (event.getCurrentItem() == null) return;
            if (event.getSlot() == VillagerGUI.SLOT_BACK) { gui.open(player); return; }

            Material mat = event.getCurrentItem().getType();
            if (mat == Material.ORANGE_STAINED_GLASS_PANE || mat == Material.BARRIER) return;

            int buybackPrice = gui.getConfig().getCityBuybackPrice(mat);
            int stock        = dataManager.getInventoryAmount(npcType, mat);

            if (stock < 64) {
                player.sendMessage("§c❌ Stock insuffisant (moins d'un stack).");
                return;
            }

            // Débite la caisse de la ville
            if (!cityManager.canAfford(buybackPrice)) {
                player.sendMessage("§c❌ La caisse de la ville n'a pas assez de coins.");
                player.sendMessage("§7Nécessaire : §6" + buybackPrice + " coins");
                return;
            }

            cityManager.removeCityCoins(buybackPrice);
            dataManager.removeFromInventory(npcType, mat, 64);

            // Donne le stack au joueur
            player.getInventory().addItem(new ItemStack(mat, 64));
            player.sendMessage("§a✅ Racheté 1 stack de §f" + formatName(mat) + "§a !");
            player.sendMessage("§7Caisse ville : §6-" + buybackPrice + " coins");

            gui.openInventory(player);
            return;
        }

        // ── Sous-menu boutique ──────────────────────────────────
        if (VillagerGUI.titleShop(npcType).equals(title)) {
            event.setCancelled(true);
            if (event.getCurrentItem() == null) return;
            if (event.getSlot() == VillagerGUI.SLOT_BACK) { gui.open(player); return; }

            Material mat = event.getCurrentItem().getType();
            // Ignore filler et items verrouillés (affichés en verre gris)
            if (mat == Material.BLUE_STAINED_GLASS_PANE
                    || mat == Material.GRAY_STAINED_GLASS_PANE
                    || mat == Material.BARRIER) return;

            int currentLevel = dataManager.getLevel(npcType);

            // Cherche dans tous les niveaux <= currentLevel
            VillagerConfig.ShopItem item = null;
            for (int lvl = 1; lvl <= currentLevel; lvl++) {
                item = gui.getConfig().getShopItemsForLevel(lvl).stream()
                        .filter(i -> i.material() == mat)
                        .findFirst()
                        .orElse(null);
                if (item != null) break;
            }

            if (item == null) return;

            if (!economy.has(player, item.price())) {
                player.sendMessage("§c❌ Fonds insuffisants. Nécessaire : §f"
                        + item.price() + " coins");
                return;
            }

            economy.withdrawPlayer(player, item.price());
            player.getInventory().addItem(new ItemStack(mat, item.quantity()));

            int xpGained = item.price() * gui.getConfig().getXpPerCoin();
            boolean levelUp = dataManager.addXP(npcType, xpGained,
                    gui.getConfig().getXpThresholds());

            player.sendMessage("§a✅ Acheté §f" + item.quantity() + "x "
                    + formatName(mat) + " §apour §6" + item.price()
                    + " coins §7(§b+" + xpGained + " XP§7)");
            if (levelUp) {
                player.sendMessage("§a🎉 §e" + npcType.displayName
                        + " §aest passé niveau §e"
                        + VillagerGUI.getLevelName(dataManager.getLevel(npcType)) + "§a !");
            }

            gui.openShop(player);
        }
    }

    private String formatName(Material mat) {
        StringBuilder sb = new StringBuilder();
        for (String word : mat.name().split("_")) {
            sb.append(Character.toUpperCase(word.charAt(0)))
                    .append(word.substring(1).toLowerCase()).append(" ");
        }
        return sb.toString().trim();
    }
}