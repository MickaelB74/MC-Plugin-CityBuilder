package com.citycore.npc.villager;

import com.citycore.building.Building;
import com.citycore.npc.CityNPC;
import com.citycore.npc.NPCDataManager;
import com.citycore.npc.NPCManager;
import com.citycore.npc.NPCState;
import com.citycore.player.PlayerDataManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class VillagerGUI {

    // Titres
    public static String titleMain(CityNPC npc, NPCDataManager dataManager) {
        return npc.displayName + " §8— §7" + npc.function + " §8— "
                + getLevelName(dataManager.getLevel(npc));
    }
    public static String titleWanderer(CityNPC npc) {
        return "§8[" + npc.displayName + "§8] §7Inconnu";
    }
    public static String titleArrived(CityNPC npc) {
        return "§8[" + npc.displayName + "§8] §aAmical";
    }
    public static String titleSell(CityNPC npc)      { return "§8[" + npc.displayName + "§8] §aVendre"; }
    public static String titleInventory(CityNPC npc) { return "§8[" + npc.displayName + "§8] §6Inventaire"; }
    public static String titleShop(CityNPC npc)      { return "§8[" + npc.displayName + "§8] §bBoutique"; }

    // Slots menu principal
    public static final int SLOT_SELL      = 5;
    public static final int SLOT_INVENTORY = 4;
    public static final int SLOT_SHOP      = 3;
    public static final int SLOT_FOLLOW    = 8;
    public static final int SLOT_QUESTS    = 7;
    public static final int SLOT_BACK      = 8;
    public static final int SLOT_JOB       = 1;

    private final CityNPC        npcType;
    private final VillagerConfig config;
    private final NPCDataManager dataManager;
    private final NPCManager     npcManager;
    private final PlayerDataManager playerDataManager;
    private final JavaPlugin plugin;

    public VillagerGUI(CityNPC npcType, VillagerConfig config,
                       NPCDataManager dataManager, NPCManager npcManager, PlayerDataManager playerDataManager, JavaPlugin javaPlugin) {
        this.npcType     = npcType;
        this.config      = config;
        this.dataManager = dataManager;
        this.npcManager  = npcManager;
        this.playerDataManager = playerDataManager;
        this.plugin = javaPlugin;
    }

    /* =========================
       OPEN — route selon état
       ========================= */

    public void open(Player player) {
        NPCState state = dataManager.getState(npcType);
        switch (state) {
            case WANDERER -> openWanderer(player);
            case ARRIVED  -> openArrived(player);
            case ASSIGNED -> openAssigned(player);
        }
    }

    /* =========================
       GUI 1 — WANDERER
       ========================= */

    private void openWanderer(Player player) {
        Inventory inv = Bukkit.createInventory(null, 9, titleWanderer(npcType));
        fillFiller(inv, 9, Material.GRAY_STAINED_GLASS_PANE);

        inv.setItem(0, makeItem(Material.COMPASS,
                "§7" + npcType.displayName.replaceAll("§.", "") + " — Inconnu",
                List.of(
                        "§7Ce personnage n'a pas encore",
                        "§7rejoint votre ville.",
                        "",
                        "§8Invitez-le à visiter la ville."
                )));

        // Bouton suivi
        boolean following = npcManager.isFollowing(player, npcType);
        inv.setItem(8, makeItem(
                following ? Material.REDSTONE : Material.LIME_DYE,
                following ? "§c⛔ Arrêter de suivre" : "§a👣 Inviter à suivre",
                List.of(following
                        ? "§7Cliquez pour arrêter"
                        : "§7Cliquez pour que " + npcType.displayName.replaceAll("§.", "")
                        + " §7vous suive jusqu'à la ville.")
        ));

        player.openInventory(inv);
    }

    /* =========================
       GUI 2 — ARRIVED
       ========================= */

    private void openArrived(Player player) {
        Inventory inv = Bukkit.createInventory(null, 9, titleArrived(npcType));
        fillFiller(inv, 9, Material.GREEN_STAINED_GLASS_PANE);

        inv.setItem(0, makeItem(Material.COMPASS,
                "§a" + npcType.displayName.replaceAll("§.", "") + " — Dans la ville",
                List.of(
                        "§7Ce personnage a rejoint votre ville !",
                        "",
                        "§7Assignez-lui un bâtiment pour",
                        "§7débloquer ses fonctionnalités.",
                        "",
                        "§8/city build assign <bâtiment> "
                                + npcType.tag.replace("citycore_", "")
                )));

        // ✅ Bouton recherche bâtiment
        inv.setItem(4, makeItem(Material.SPYGLASS,
                "§e🔍 Chercher un bâtiment",
                List.of(
                        "§7Liste les bâtiments disponibles",
                        "",
                        "§eCliquez pour voir"
                )));

        // Toggle marche aléatoire
        boolean wandering = npcManager.isWandering(npcType);
        inv.setItem(8, makeItem(
                wandering ? Material.LIME_DYE : Material.GRAY_DYE,
                wandering ? "§a🚶 Marche aléatoire active" : "§7🚶 Marche aléatoire inactive",
                List.of(
                        wandering ? "§7Cliquez pour arrêter" : "§7Cliquez pour activer",
                        "",
                        "§8Le NPC se balade dans la ville"
                )
        ));

        player.openInventory(inv);
    }

    // ✅ Sous-menu bâtiments disponibles
    public static String titleBuildingSearch(CityNPC npc) {
        return "§8[" + npc.displayName + "§8] §eBâtiments";
    }

    public void openBuildingSearch(Player player,
                                   List<Building> availableBuildings) {
        int size = Math.max(9, (int) Math.ceil(
                (availableBuildings.size() + 1) / 9.0) * 9);
        size = Math.min(size, 54);

        Inventory inv = Bukkit.createInventory(null, size,
                titleBuildingSearch(npcType));
        fillFiller(inv, size, Material.GREEN_STAINED_GLASS_PANE);

        if (availableBuildings.isEmpty()) {
            inv.setItem(4, makeItem(Material.BARRIER,
                    "§cAucun bâtiment disponible",
                    List.of(
                            "§7Tous les bâtiments ont déjà",
                            "§7un NPC assigné.",
                            "",
                            "§8Créez un nouveau bâtiment avec",
                            "§8/city build new <nom>"
                    )));
        } else {
            int slot = 0;
            for (Building building : availableBuildings) {
                if (slot == size - 1) break; // Réserve le dernier slot pour retour
                inv.setItem(slot++, makeItem(Material.BRICKS,
                        "§e🏛 " + building.name(),
                        List.of(
                                "§7Zone : §fX(" + building.x1() + "§7→§f"
                                        + building.x2() + "§7) Z(§f"
                                        + building.z1() + "§7→§f"
                                        + building.z2() + "§7)",
                                "§7Monde : §f" + building.world(),
                                "",
                                building.hasNpcPoint()
                                        ? "§a✔ Point NPC défini"
                                        : "§c✘ Aucun point NPC",
                                "",
                                "§eCliquez pour assigner §f"
                                        + npcType.displayName.replaceAll("§.", "")
                        )));
            }
        }

        inv.setItem(size - 1, makeItem(Material.ARROW,
                "§7← Retour", List.of()));
        player.openInventory(inv);
    }

    /* =========================
       GUI 3 — ASSIGNED (GUI complet)
       ========================= */

    private void openAssigned(Player player) {
        int level  = dataManager.getLevel(npcType);
        int xp     = dataManager.getXP(npcType);
        int xpNext = getXpForNextLevel(level);

        Inventory inv = Bukkit.createInventory(null, 9, titleMain(npcType, dataManager));
        fillFiller(inv, 9, Material.GRAY_STAINED_GLASS_PANE);

        // Tête avec niveau
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta headMeta = head.getItemMeta();
        headMeta.setDisplayName(npcType.displayName + " §8— §7" + npcType.function);
        headMeta.setLore(List.of(
                buildStarLevel(level),
                buildNativeBar(level, xp, xpNext),
                "§7XP : §f" + xp + (xpNext > 0 ? " §7/ §f" + xpNext : " §7(max)")
        ));
        headMeta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        head.setItemMeta(headMeta);
        inv.setItem(0, head);
        inv.setItem(SLOT_JOB, makeJobButton(player));
        inv.setItem(SLOT_SELL, makeItem(Material.EMERALD, "§a💰 Vendre",
                List.of("§7Vendez vos ressources", "§7et recevez des coins.")));
        inv.setItem(SLOT_INVENTORY, makeItem(Material.CHEST, "§6📦 Inventaire",
                List.of("§7Items achetés par " + npcType.displayName,
                        "§7Rachetables par la ville.")));
        inv.setItem(SLOT_SHOP, makeItem(Material.GOLD_INGOT, "§b🛒 Boutique",
                List.of("§7Achetez des items",
                        "§7Niveau actuel : §e" + level)));
        inv.setItem(SLOT_QUESTS, makeItem(Material.WRITABLE_BOOK, "§d📜 Quêtes",
                List.of("§7Quêtes principales et spéciales",
                        "§7de " + npcType.displayName + ".",
                        "",
                        "§eCliquez pour voir")));

        boolean following = npcManager.isFollowing(player, npcType);
        inv.setItem(SLOT_FOLLOW, makeItem(
                following ? Material.REDSTONE : Material.LIME_DYE,
                following ? "§c⛔ Arrêter de suivre" : "§a👣 Demander de suivre",
                List.of(following ? "§7Cliquez pour arrêter"
                        : "§7Cliquez pour activer")
        ));

        player.openInventory(inv);
    }

    /* =========================
       SOUS-MENU VENDRE
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
            int fullSets              = playerCount / sp.quantity();
            int total                 = fullSets * sp.price();

            List<String> lore = new ArrayList<>();
            lore.add("§7Prix : §6" + sp.price() + " coins §7/ " + sp.quantity() + " items");
            lore.add("");
            lore.add("§7Vos stocks : §f" + playerCount
                    + " §7(" + fullSets + "x" + sp.quantity() + ")");
            lore.add("§7Valeur totale : §6" + total + " coins");
            boolean hasJob = npcType == playerDataManager.getJob(player.getUniqueId());
            if (hasJob) {
                int bonus = (int) Math.round(fullSets * sp.quantity() * config.getJobInventoryMultiplier());
                lore.add("§a💼 Bonus job : §f+"
                        + (bonus - fullSets * sp.quantity()) + " items stockés");
            }
            lore.add("");
            lore.add(fullSets > 0 ? "§eCliquez pour vendre" : "§cPas assez d'items");

            int taggedCount = countTaggedMaterial(player, mat); // méthode miroir qui compte les tagués
            if (taggedCount > 0) {
                lore.add("§c⚠ " + taggedCount + " item(s) récupérés §cnon revendables");
            }

            inv.setItem(slot++, makeItem(mat, "§f" + formatName(mat), lore));
        }

        inv.setItem(SLOT_BACK, makeItem(Material.ARROW, "§7← Retour", List.of()));
        player.openInventory(inv);
    }

    private int countTaggedMaterial(Player player, Material mat) {
        NamespacedKey key = new NamespacedKey(plugin, "buyback");
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null || item.getType() != mat) continue;
            ItemMeta meta = item.getItemMeta();
            if (meta != null && meta.getPersistentDataContainer()
                    .has(key, PersistentDataType.BYTE)) count += item.getAmount();
        }
        return count;
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
            Material mat     = entry.getKey();
            int amount       = entry.getValue();
            int stackCount   = amount / 64;
            int remainder    = amount % 64;

            List<String> lore = new ArrayList<>();
            lore.add("§7Stock : §f" + amount + " §7(" + stackCount + " stacks"
                    + (remainder > 0 ? " + " + remainder : "") + ")");
            lore.add("");
            lore.add("§aRachat : §fGratuit");
            lore.add("");
            lore.add(stackCount > 0
                    ? "§eCliquez pour récupérer (1 stack)"
                    : "§cStock insuffisant");

            inv.setItem(slot++, makeItem(mat, "§f" + formatName(mat), lore));
        }

        if (inventory.isEmpty()) {
            inv.setItem(4, makeItem(Material.BARRIER, "§cInventaire vide",
                    List.of("§7Rien à racheter.")));
        }

        inv.setItem(SLOT_BACK, makeItem(Material.ARROW, "§7← Retour", List.of()));
        player.openInventory(inv);
    }

    /* =========================
       SOUS-MENU BOUTIQUE
       ========================= */

    public void openShop(Player player) {
        int currentLevel = dataManager.getLevel(npcType);

        List<ShopEntry> allEntries = new ArrayList<>();
        for (int lvl = 1; lvl <= 5; lvl++) {
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
            lore.add(VillagerGUI.getLevelName(itemLevel)
                    + (unlocked ? " §a✔" : " §c✘"));
            lore.add("");

            if (unlocked) {
                boolean hasJob = npcType == playerDataManager.getJob(player.getUniqueId());
                int finalPrice = hasJob ? config.applyJobDiscount(item.price()) : item.price();

                if (hasJob && finalPrice < item.price()) {
                    lore.add("§7Prix : §4§m" + item.price() + "§r §6" + finalPrice + " coins");
                    lore.add("§a💼 Réduction employé : §f-" + config.getJobShopDiscount() + "%");
                } else {
                    lore.add("§7Prix : §6" + finalPrice + " coins");
                }
                lore.add("§7Quantité : §fx" + item.quantity());
                lore.add("");
                lore.add("§eCliquez pour acheter");
            } else {
                lore.add("§cDébloqué au niveau §e" + getLevelName(itemLevel));
                lore.add("");
                lore.add("§8Continuez à commercer pour");
                lore.add("§8débloquer cet item.");
            }

            Material display = unlocked
                    ? item.material()
                    : Material.GRAY_STAINED_GLASS_PANE;
            String name = unlocked
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

    private record ShopEntry(VillagerConfig.ShopItem item, int level) {}

    /* =========================
       SELL MATERIAL
       ========================= */

    public int sellMaterial(Player player, Material mat) {
        VillagerConfig.SellPrice sp = config.getSellPrice(mat);
        if (sp == null) return -1;

        int totalCount = countMaterial(player, mat);
        int fullSets   = totalCount / sp.quantity();
        if (fullSets == 0) return -1;

        // Retire les items du joueur (inchangé)
        int toRemove = fullSets * sp.quantity();
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null || item.getType() != mat || toRemove <= 0) continue;
            int take = Math.min(item.getAmount(), toRemove);
            item.setAmount(item.getAmount() - take);
            toRemove -= take;
        }

        int earned        = fullSets * sp.price();
        int itemsSold     = fullSets * sp.quantity();

        // Bonus job : le NPC reçoit plus d'items si le joueur travaille pour lui
        boolean hasJob = npcType == playerDataManager.getJob(player.getUniqueId());
        int itemsToStock = hasJob
                ? (int) Math.round(itemsSold * config.getJobInventoryMultiplier())
                : itemsSold;

        dataManager.addToInventory(npcType, mat, itemsToStock);
        return earned;
    }

    /* =========================
       HELPERS
       ========================= */

    private String buildStarLevel(int level) {
        return switch (level) {
            case 1  -> getLevelName(level) + " §7— ✦✧✧✧✧";
            case 2  -> getLevelName(level) + " §7— §e✦✦§7✧✧✧";
            case 3  -> getLevelName(level) + " §7— §e✦✦✦§7✧✧";
            case 4  -> getLevelName(level) + " §7— §e✦✦✦✦§7✧";
            default -> getLevelName(level) + " §7— §e✦✦✦✦✦";
        };
    }

    private String buildNativeBar(int level, int xp, int xpNext) {
        if (xpNext <= 0) return "§a▬▬▬▬▬▬▬▬▬▬ §8Niveau maximum";

        int xpCurrent     = config.getXpThresholds().getOrDefault(level, 0);
        int xpInLevel     = xp - xpCurrent;
        int xpNeededLevel = xpNext - xpCurrent;
        int barLength     = 10;
        int filled        = Math.min(
                (int)((double) xpInLevel / xpNeededLevel * barLength), barLength);

        StringBuilder bar = new StringBuilder();
        for (int i = 0; i < barLength; i++) {
            bar.append(i < filled ? "§a▬" : "§8▬");
        }
        return bar.toString();
    }

    public static String getLevelName(int level) {
        return switch (level) {
            case 1  -> "§8Novice";
            case 2  -> "§7Apprenti";
            case 3  -> "§bJournalier";
            case 4  -> "§6Expert";
            default -> "§4Maître";
        };
    }

    private int getXpForNextLevel(int level) {
        return config.getXpThresholds().getOrDefault(level + 1, -1);
    }

    private int countMaterial(Player player, Material mat) {
        NamespacedKey key = new NamespacedKey(plugin, "buyback");
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null || item.getType() != mat) continue;
            ItemMeta meta = item.getItemMeta();
            if (meta != null && meta.getPersistentDataContainer()
                    .has(key, PersistentDataType.BYTE)) continue; // item racheté, ignoré
            count += item.getAmount();
        }
        return count;
    }

    public CityNPC getNpcType()      { return npcType; }
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

    private ItemStack makeJobButton(Player player) {
        CityNPC currentJob = playerDataManager.getJob(player.getUniqueId());
        boolean hasThisJob = npcType == currentJob;

        if (hasThisJob) {
            return makeItem(Material.BARRIER,
                    "§c❌ Quitter le job",
                    List.of(
                            "§7Vous travaillez actuellement",
                            "§7pour §e" + npcType.displayName + "§7.",
                            "",
                            "§cCliquez pour quitter."
                    ));
        } else if (currentJob != null) {
            return makeItem(Material.GRAY_DYE,
                    "§8Job indisponible",
                    List.of(
                            "§7Vous travaillez déjà pour",
                            "§e" + currentJob.displayName + "§7.",
                            "",
                            "§8Quittez d'abord votre job actuel."
                    ));
        } else {
            return makeItem(Material.CRAFTING_TABLE,
                    "§a💼 Travailler pour " + npcType.displayName,
                    List.of(
                            "§7Rejoignez l'atelier de",
                            "§e" + npcType.displayName + "§7.",
                            "",
                            "§eCliquez pour accepter."
                    ));
        }
    }

    private ItemStack makeItem(Material mat, String name) {
        return makeItem(mat, name, List.of());
    }
}