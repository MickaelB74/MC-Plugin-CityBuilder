package com.citycore.npc.villager;

import com.citycore.npc.CityNPC;
import com.citycore.npc.NPCDataManager;
import com.citycore.npc.NPCManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class VillagerGUI {

    // Titres dynamiques — construits depuis CityNPC
    // Titres — format natif "Nom — Fonction"
    public static String titleMain(CityNPC npc, NPCDataManager dataManager) {
        return npc.displayName + " §8— §7" + npc.function + " §8— " + getLevelName(dataManager.getLevel(npc));
    }
    public static String titleSell(CityNPC npc)      { return "§8[" + npc.displayName + "§8] §aVendre"; }
    public static String titleInventory(CityNPC npc) { return "§8[" + npc.displayName + "§8] §6Inventaire"; }
    public static String titleShop(CityNPC npc)      { return "§8[" + npc.displayName + "§8] §bBoutique"; }

    // Slots menu principal
    public static final int SLOT_SELL      = 1;
    public static final int SLOT_INVENTORY = 3;
    public static final int SLOT_SHOP      = 5;
    public static final int SLOT_FOLLOW    = 7;
    public static final int SLOT_BACK      = 8;
    public static final int SLOT_QUESTS    = 6;

    private final CityNPC         npcType;
    private final VillagerConfig  config;
    private final NPCDataManager  dataManager;
    private final NPCManager      npcManager;

    public VillagerGUI(CityNPC npcType, VillagerConfig config,
                       NPCDataManager dataManager, NPCManager npcManager) {
        this.npcType     = npcType;
        this.config      = config;
        this.dataManager = dataManager;
        this.npcManager  = npcManager;
    }

   /* =========================
   MENU PRINCIPAL — avec niveau natif
   ========================= */

    public void open(Player player) {
        int level  = dataManager.getLevel(npcType);
        int xp     = dataManager.getXP(npcType);
        int xpNext = getXpForNextLevel(level);

        Inventory inv = Bukkit.createInventory(null, 9, titleMain(npcType, dataManager));
        fillFiller(inv, 9, Material.GRAY_STAINED_GLASS_PANE);

        // ✅ Tête de villageois avec niveau natif (étoiles)
        ItemStack head = new ItemStack(Material.VILLAGER_SPAWN_EGG);
        ItemMeta headMeta = head.getItemMeta();
        headMeta.setDisplayName(npcType.displayName + " §8— §7" + npcType.function);
        headMeta.setLore(List.of(
                buildStarLevel(level),           // ★★★☆ style natif
                buildNativeBar(level, xp, xpNext),
                "§7XP : §f" + xp + (xpNext > 0 ? " §7/ §f" + xpNext : " §7(max)")
        ));
        headMeta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        head.setItemMeta(headMeta);
        inv.setItem(0, head);

        inv.setItem(SLOT_SELL,      makeItem(Material.EMERALD,    "§a💰 Vendre",
                List.of("§7Vendez vos ressources", "§7et recevez des coins.")));
        inv.setItem(SLOT_INVENTORY, makeItem(Material.CHEST,      "§6📦 Inventaire",
                List.of("§7Items achetés par " + npcType.displayName, "§7Rachetables par la ville.")));
        inv.setItem(SLOT_SHOP,      makeItem(Material.GOLD_INGOT, "§b🛒 Boutique",
                List.of("§7Achetez des items", "§7Niveau actuel : §e" + level)));
        inv.setItem(SLOT_QUESTS, makeItem(Material.WRITABLE_BOOK,
                "§d📜 Quêtes",
                List.of("§7Quêtes principales et spéciales",
                        "§7de " + npcType.displayName + ".",
                        "",
                        "§eCliquez pour voir")));

        boolean following = npcManager.isFollowing(player, npcType);
        inv.setItem(SLOT_FOLLOW, makeItem(
                following ? Material.REDSTONE : Material.LIME_DYE,
                following ? "§c⛔ Arrêter de suivre" : "§a👣 Demander de suivre",
                List.of(following ? "§7Cliquez pour arrêter" : "§7Cliquez pour activer")
        ));

        player.openInventory(inv);
    }

    /**
     * Barre de progression style natif villageois Minecraft.
     * Verte pour XP actuel, grise pour le reste.
     */
    private String buildNativeBar(int level, int xp, int xpNext) {
        if (xpNext <= 0) return "§a▬▬▬▬▬▬▬▬▬▬ §8Niveau maximum";

        // XP du début du niveau actuel
        int xpCurrent = config.getXpThresholds().getOrDefault(level, 0);

        // Progression relative : de xpCurrent → xpNext
        int xpInLevel    = xp - xpCurrent;
        int xpNeededLevel = xpNext - xpCurrent;

        int barLength = 10;
        int filled = Math.min((int) ((double) xpInLevel / xpNeededLevel * barLength), barLength);

        StringBuilder bar = new StringBuilder();
        for (int i = 0; i < barLength; i++) {
            bar.append(i < filled ? "§a▬" : "§8▬");
        }
        return bar.toString();
    }

    /* =========================
   SOUS-MENU VENDRE — avec quantity
   ========================= */

    public void openSell(Player player) {
        Map<Material, VillagerConfig.SellPrice> prices = config.getSellPrices();
        int size = Math.max(18, (int) Math.ceil((prices.size() + 1) / 9.0) * 9);
        Inventory inv = Bukkit.createInventory(null, size, titleSell(npcType));
        fillFiller(inv, size, Material.GREEN_STAINED_GLASS_PANE);

        int slot = 0;
        for (Map.Entry<Material, VillagerConfig.SellPrice> entry : prices.entrySet()) {
            if (slot == SLOT_BACK) slot++;
            Material mat              = entry.getKey();
            VillagerConfig.SellPrice sp = entry.getValue();
            int playerCount           = countMaterial(player, mat);
            int fullSets              = playerCount / sp.quantity(); // sets selon la quantity config
            int total                 = fullSets * sp.price();

            List<String> lore = new ArrayList<>();
            lore.add("§7Prix : §6" + sp.price() + " coins §7/ " + sp.quantity() + " items");
            lore.add("");
            lore.add("§7Vos stocks : §f" + playerCount
                    + " §7(" + fullSets + "x" + sp.quantity() + ")");
            lore.add("§7Valeur totale : §6" + total + " coins");
            lore.add("");
            lore.add(fullSets > 0 ? "§eCliquez pour vendre" : "§cPas assez d'items");

            inv.setItem(slot++, makeItem(mat, "§f" + formatName(mat), lore));
        }

        inv.setItem(SLOT_BACK, makeItem(Material.ARROW, "§7← Retour", List.of()));
        player.openInventory(inv);
    }

    /* =========================
       SOUS-MENU INVENTAIRE
       ========================= */

    public void openInventory(Player player) {
        Map<Material, Integer> inventory = dataManager.getInventory(npcType);
        int size = Math.max(18, (int) Math.ceil((inventory.size() + 1) / 9.0) * 9);
        Inventory inv = Bukkit.createInventory(null, size, titleInventory(npcType));
        fillFiller(inv, size, Material.ORANGE_STAINED_GLASS_PANE);

        int slot = 0;
        for (Map.Entry<Material, Integer> entry : inventory.entrySet()) {
            if (slot == SLOT_BACK) slot++;
            Material mat    = entry.getKey();
            int amount      = entry.getValue();
            int buybackPrice = config.getCityBuybackPrice(mat);
            int stackCount  = amount / 64;
            int remainder   = amount % 64;

            List<String> lore = new ArrayList<>();
            lore.add("§7Stock : §f" + amount + " §7(" + stackCount + " stacks" +
                    (remainder > 0 ? " + " + remainder : "") + ")");
            lore.add("");
            lore.add("§7Rachat ville : §6" + buybackPrice + " coins§7/stack");
            lore.add("§7(§c-" + (int)((1 - config.getCityBuybackRatio()) * 100) + "% §7du prix vente)");
            lore.add("");
            lore.add(stackCount > 0 ? "§eCliquez pour racheter (1 stack)" : "§cStock insuffisant");

            inv.setItem(slot++, makeItem(mat, "§f" + formatName(mat), lore));
        }

        if (inventory.isEmpty()) {
            inv.setItem(4, makeItem(Material.BARRIER, "§cInventaire vide", List.of("§7Rien à racheter.")));
        }

        inv.setItem(SLOT_BACK, makeItem(Material.ARROW, "§7← Retour", List.of()));
        player.openInventory(inv);
    }

   /* =========================
   SOUS-MENU BOUTIQUE — avec quantity
   ========================= */

    public void openShop(Player player) {
        int currentLevel = dataManager.getLevel(npcType);

        // Collecte tous les items de tous les niveaux
        List<ShopEntry> allEntries = new ArrayList<>();
        for (int lvl = 1; lvl <= 4; lvl++) {
            for (VillagerConfig.ShopItem item : config.getShopItemsForLevel(lvl)) {
                allEntries.add(new ShopEntry(item, lvl));
            }
        }

        int size = Math.max(18, (int) Math.ceil((allEntries.size() + 1) / 9.0) * 9);
        Inventory inv = Bukkit.createInventory(null, size, titleShop(npcType));
        fillFiller(inv, size, Material.BLUE_STAINED_GLASS_PANE);

        int slot = 0;
        for (ShopEntry entry : allEntries) {
            if (slot == SLOT_BACK) slot++;

            VillagerConfig.ShopItem item = entry.item();
            int itemLevel                = entry.level();
            boolean unlocked             = itemLevel <= currentLevel;

            List<String> lore = new ArrayList<>();

            // Badge de niveau
            lore.add(VillagerGUI.getLevelName(itemLevel)
                    + (unlocked ? " §a✔" : " §c✘"));
            lore.add("");

            if (unlocked) {
                lore.add("§7Prix : §6" + item.price() + " coins");
                lore.add("§7Quantité : §fx" + item.quantity());
                lore.add("");
                lore.add("§eCliquez pour acheter");
            } else {
                lore.add("§cDébloqué au niveau §e" + getLevelName(itemLevel));
                lore.add("");
                lore.add("§8Continuez à commercer pour");
                lore.add("§8débloquer cet item.");
            }

            // Item verrouillé → affiché en barrière grise
            Material display = unlocked ? item.material() : Material.GRAY_STAINED_GLASS_PANE;
            String name      = unlocked
                    ? "§f" + formatName(item.material())
                    : "§8🔒 " + formatName(item.material());

            inv.setItem(slot++, makeItem(display, name, lore));
        }

        if (allEntries.isEmpty()) {
            inv.setItem(4, makeItem(Material.BARRIER, "§cBoutique vide",
                    List.of("§7Aucun item configuré.")));
        }

        inv.setItem(SLOT_BACK, makeItem(Material.ARROW, "§7← Retour", List.of()));
        player.openInventory(inv);
    }

    // Record interne pour associer item + niveau
    private record ShopEntry(VillagerConfig.ShopItem item, int level) {}

    /* =========================
       HELPERS
       ========================= */

    private String buildStarLevel(int level) {
        // Style natif villageois Minecraft
        return switch (level) {
            case 1 -> getLevelName(level) + " §7— ✦✧✧✧✧";
            case 2 -> getLevelName(level) + " §7— §e✦✦§7✧✧✧";
            case 3 -> getLevelName(level) + " §7— §e✦✦✦§7✧✧";
            case 4 -> getLevelName(level) + " §7— §e✦✦✦✦§7✧";
            default -> getLevelName(level) + " §7— §e✦✦✦✦✦";
        };
    }

    public static String getLevelName(int level) {
        return switch (level) {
            case 1 -> "§8Novice";
            case 2 -> "§7Apprenti";
            case 3 -> "§bJournalier";
            case 4 -> "§6Expert";
            default -> "§4Maître";
        };
    }

    private int getXpForNextLevel(int level) {
        Map<Integer, Integer> thresholds = config.getXpThresholds();
        return thresholds.getOrDefault(level + 1, -1);
    }

    private int countMaterial(Player player, Material mat) {
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == mat) count += item.getAmount();
        }
        return count;
    }

    /* =========================
   SELL MATERIAL — avec quantity
   ========================= */

    public int sellMaterial(Player player, Material mat) {
        VillagerConfig.SellPrice sp = config.getSellPrice(mat);
        if (sp == null) return -1;

        int setsSold = 0;
        int remaining = sp.quantity();

        // Collecte les items par sets de sp.quantity()
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null || item.getType() != mat) continue;
            remaining -= item.getAmount();
            if (remaining <= 0) {
                // On a assez pour un set — on retire tout et on vend
                // Simplifié : retire par sets complets
                break;
            }
        }

        // Compte combien de sets complets le joueur possède
        int totalCount = countMaterial(player, mat);
        int fullSets   = totalCount / sp.quantity();
        if (fullSets == 0) return -1;

        // Retire les items (fullSets * quantity)
        int toRemove = fullSets * sp.quantity();
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null || item.getType() != mat || toRemove <= 0) continue;
            int take = Math.min(item.getAmount(), toRemove);
            item.setAmount(item.getAmount() - take);
            toRemove -= take;
        }

        int earned = fullSets * sp.price();
        dataManager.addToInventory(npcType, mat, fullSets * sp.quantity());
        return earned;
    }

    public CityNPC getNpcType() { return npcType; }
    public VillagerConfig getConfig() { return config; }

    private void fillFiller(Inventory inv, int size, Material mat) {
        ItemStack filler = makeItem(mat, " ");
        for (int i = 0; i < size; i++) inv.setItem(i, filler);
    }

    private String formatName(Material mat) {
        StringBuilder sb = new StringBuilder();
        for (String word : mat.name().split("_")) {
            sb.append(Character.toUpperCase(word.charAt(0)))
                    .append(word.substring(1).toLowerCase()).append(" ");
        }
        return sb.toString().trim();
    }

    private ItemStack makeItem(Material mat, String name, List<String> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta  = item.getItemMeta();
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