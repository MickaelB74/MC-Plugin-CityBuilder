package com.citycore.npc.villager;

import com.citycore.building.Building;
import com.citycore.economy.ItemTags;
import com.citycore.economy.MarketService;
import com.citycore.npc.CityNPC;
import com.citycore.npc.NPCDataManager;
import com.citycore.npc.NPCManager;
import com.citycore.npc.NPCState;
import com.citycore.npc.villager.VillagerConfig.Requirement;
import com.citycore.npc.villager.VillagerConfig.SellPrice;
import com.citycore.npc.villager.VillagerConfig.ShopItem;
import com.citycore.player.PlayerDataManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
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
    public static String titleInventory(CityNPC npc) { return "§8[" + npc.displayName + "§8] §6Entrepôt"; }
    public static String titleShop(CityNPC npc)      { return "§8[" + npc.displayName + "§8] §bBoutique"; }

    // Slots menu principal
    public static final int SLOT_SELL      = 5;
    public static final int SLOT_INVENTORY = 4;
    public static final int SLOT_SHOP      = 3;
    public static final int SLOT_FOLLOW    = 8;
    public static final int SLOT_QUESTS    = 7;
    public static final int SLOT_BACK      = 8;
    public static final int SLOT_JOB       = 1;

    private final CityNPC           npcType;
    private final VillagerConfig    config;
    private final NPCDataManager    dataManager;
    private final NPCManager        npcManager;
    private final PlayerDataManager playerDataManager;
    private final MarketService     market;
    private final NamespacedKey     entryKey;
    private final JavaPlugin        plugin;
    /** Écrans illustrés « Valmont » (null = menus classiques uniquement). */
    private com.citycore.ui.screens.ArtisanScreens themed;
    /** Mods du serveur : un habitant lié à un mod absent n'ouvre pas son menu. */
    private com.citycore.integration.ModIntegrations integrations;

    public void setIntegrations(com.citycore.integration.ModIntegrations integrations) {
        this.integrations = integrations;
    }

    /** L'habitant peut-il commercer et confier des quêtes (mod requis présent) ? */
    public boolean isAvailable() {
        return integrations == null || integrations.isAvailable(npcType);
    }

    public void setThemed(com.citycore.ui.screens.ArtisanScreens themed) {
        this.themed = themed;
    }

    /**
     * Inspection des constructions (phase 2) : exigences des ateliers proposés, état de son atelier.
     * Pas de message dans le chat : tout est dans les menus (écran « Son atelier » avec le pack).
     */
    private com.citycore.building.BuildingInspector inspector;

    public void setInspector(com.citycore.building.BuildingInspector inspector) {
        this.inspector = inspector;
    }

    public VillagerGUI(CityNPC npcType, VillagerConfig config,
                       NPCDataManager dataManager, NPCManager npcManager,
                       PlayerDataManager playerDataManager, MarketService market,
                       JavaPlugin plugin) {
        this.npcType           = npcType;
        this.config            = config;
        this.dataManager       = dataManager;
        this.npcManager        = npcManager;
        this.playerDataManager = playerDataManager;
        this.market            = market;
        this.entryKey          = new NamespacedKey(plugin, "gui_entry");
        this.plugin            = plugin;
    }

    /* =========================
       OPEN — route selon état
       ========================= */

    public void open(Player player) {
        // Activé pour les tests (/city npc … activer) : menu complet pour un administrateur, sans atelier
        NPCState state = com.citycore.npc.TestActivation.menuState(npcType, dataManager.getState(npcType), player);
        // Installé mais lié à un mod absent : un dialogue remplace le menu (recrutement et
        // installation restent possibles, voir ModIntegrations)
        if (!isAvailable() && state == NPCState.ASSIGNED) {
            explainUnavailable(player);
            return;
        }
        if (themed != null && themed.openMain(player)) return; // joueur équipé du pack
        switch (state) {
            case WANDERER -> openWanderer(player);
            case ARRIVED  -> openArrived(player);
            case HOMELESS -> {
                // Bâtiment supprimé : gestion fermée jusqu'au nouveau logis, rien n'est perdu
                player.sendMessage("§6🏚 " + npcType.displayName + " §6n'a plus d'atelier. §7Choisissez-lui un"
                        + " nouveau logis : ses quêtes, son stock et son rang l'attendent.");
                openArrived(player);
            }
            case ASSIGNED -> openAssigned(player);
        }
    }

    /**
     * Dialogue de l'habitant (mod_missing), puis une ligne hors personnage qui nomme le mod.
     * Un clic pendant le dialogue ne le relance pas.
     */
    private void explainUnavailable(Player player) {
        if (player.getOpenInventory().getTopInventory().getType() != org.bukkit.event.inventory.InventoryType.CRAFTING) {
            player.closeInventory();
        }
        if (com.citycore.util.TypewriterUtil.isTalking(player)) return;
        List<String> lines = npcType.getDialogue("mod_missing");
        if (lines.isEmpty()) {
            lines = List.of(npcType.displayName + " §f: §oJe ne peux rien pour vous ici... pas encore.");
        }
        // « (Le mod Tide n'est pas installé…) », ou « Les mods A et B ne sont pas tous les deux installés… »
        String message = com.citycore.integration.ModIntegrations.unavailableMessage(npcType, plainName());
        com.citycore.util.TypewriterUtil.play(plugin, player, lines, () -> player.sendMessage(message));
    }

    /* =========================
       GUI 1 — WANDERER
       ========================= */

    private void openWanderer(Player player) {
        Inventory inv = Bukkit.createInventory(null, 9, titleWanderer(npcType));
        fillFiller(inv, 9, Material.GRAY_STAINED_GLASS_PANE);

        List<String> lore = new ArrayList<>(backstoryLore());
        lore.add("");
        lore.add("§7Ce personnage n'a pas encore");
        lore.add("§7rejoint votre ville.");
        lore.add("");
        lore.add("§8Invitez-le à visiter la ville.");
        inv.setItem(0, makeItem(Material.COMPASS,
                "§7" + plainName() + " — §f" + npcType.function, lore));

        // Bouton suivi
        boolean following = npcManager.isFollowing(player, npcType);
        inv.setItem(8, makeItem(
                following ? Material.REDSTONE : Material.LIME_DYE,
                following ? "§c⛔ Arrêter de suivre" : "§a👣 Inviter à suivre",
                List.of(following
                        ? "§7Cliquez pour arrêter"
                        : "§7Cliquez pour que " + plainName()
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

        List<String> lore = new ArrayList<>(backstoryLore());
        lore.add("");
        lore.add("§7Ce personnage a rejoint votre ville !");
        lore.add("§7Assignez-lui un bâtiment pour");
        lore.add("§7débloquer son commerce.");
        lore.add("");
        lore.add("§8« Chercher un bâtiment » : ses ateliers possibles.");
        inv.setItem(0, makeItem(Material.COMPASS, "§a" + plainName() + " — Dans la ville", lore));

        // Bouton recherche bâtiment
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

    // Sous-menu bâtiments disponibles
    public static String titleBuildingSearch(CityNPC npc) {
        return "§8[" + npc.displayName + "§8] §eBâtiments";
    }

    public void openBuildingSearch(Player player, List<Building> availableBuildings) {
        if (themed != null && themed.openBuildings(player, availableBuildings)) return;
        int size = Math.max(9, (int) Math.ceil((availableBuildings.size() + 1) / 9.0) * 9);
        size = Math.min(size, 54);

        Inventory inv = Bukkit.createInventory(null, size, titleBuildingSearch(npcType));
        fillFiller(inv, size, Material.GREEN_STAINED_GLASS_PANE);

        if (availableBuildings.isEmpty()) {
            inv.setItem(4, makeItem(Material.BARRIER,
                    "§cAucun atelier disponible",
                    List.of(
                            "§7Aucun atelier libre ou réservé",
                            "§7à ce personnage.",
                            "",
                            "§8Créez-en un chez Alderic :",
                            "§8« Bâtiments » → « Ajouter un bâtiment »"
                    )));
        } else {
            int slot = 0;
            for (Building building : availableBuildings) {
                if (slot == size - 1) break; // Réserve le dernier slot pour retour
                List<String> lore = new ArrayList<>(List.of(
                        npcType.tag.equals(building.reservedFor())
                                ? "§6Atelier réservé à " + plainName() : "§aAtelier libre",
                        "§7Zone : §fX(" + building.x1() + "§7→§f"
                                + building.x2() + "§7) Z(§f"
                                + building.z1() + "§7→§f"
                                + building.z2() + "§7)",
                        ""));
                // Inspection selon les exigences de ce personnage (phase 2)
                com.citycore.building.Inspection insp = inspector == null ? null
                        : inspector.inspectFor(building, npcType);
                if (insp != null) {
                    lore.addAll(insp.lore());
                    lore.add("");
                }
                lore.add(insp == null || insp.compliant()
                        ? "§eCliquez pour assigner §f" + plainName()
                        : "§cIncomplet : complétez-le d'abord");
                inv.setItem(slot++, makeItem(Material.BRICKS, "§e🏛 " + building.name(), lore));
            }
        }

        inv.setItem(size - 1, makeItem(Material.ARROW, "§7← Retour", List.of()));
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

        // Tête avec niveau + histoire
        List<String> headLore = new ArrayList<>();
        headLore.add(buildStarLevel(level));
        headLore.add(buildNativeBar(level, xp, xpNext));
        headLore.add("§7XP : §f" + xp + (xpNext > 0 ? " §7/ §f" + xpNext : " §7(max)"));
        // Son atelier : niveau, ce qui manque pour le suivant (phase 2 ; détail complet avec le pack)
        com.citycore.building.Inspection atelier = inspector == null ? null : inspector.freshAtelierInspection(npcType);
        if (atelier != null) {
            headLore.add("");
            headLore.add("§7Atelier : §f" + atelier.building().name());
            headLore.addAll(atelier.lore());
        }
        headLore.add("");
        headLore.addAll(backstoryLore());
        inv.setItem(0, makeItem(Material.PLAYER_HEAD,
                npcType.displayName + " §8— §7" + npcType.function, headLore));

        inv.setItem(SLOT_JOB, makeJobButton(player));
        inv.setItem(SLOT_SELL, makeItem(Material.EMERALD, "§a💰 Vendre",
                List.of("§7Vendez vos ressources.",
                        "§7Le prix baisse quand son",
                        "§7entrepôt se remplit.")));
        inv.setItem(SLOT_INVENTORY, makeItem(Material.CHEST, "§6📦 Entrepôt",
                List.of("§7Réserves de " + npcType.displayName + "§7.",
                        "§7Servez-vous gratuitement",
                        "§7(objets non revendables).")));
        inv.setItem(SLOT_SHOP, makeItem(Material.GOLD_INGOT, "§b🛒 Boutique",
                List.of("§7Ses fabrications et raretés.",
                        "§7Produites à partir des stocks",
                        "§7de la ville.",
                        "",
                        "§7Niveau actuel : " + getLevelName(level))));
        inv.setItem(SLOT_QUESTS, makeItem(Material.WRITABLE_BOOK, "§d📜 Quêtes",
                List.of("§7Quêtes principales et spéciales",
                        "§7de " + npcType.displayName + "§7.",
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
        if (themed != null && themed.openSell(player)) return;
        Map<Material, SellPrice> prices = config.getSellPrices();
        int size = menuSize(prices.size());
        Inventory inv = Bukkit.createInventory(null, size, titleSell(npcType));
        fillFiller(inv, size, Material.GREEN_STAINED_GLASS_PANE);

        int slot = 0;
        for (Map.Entry<Material, SellPrice> entry : prices.entrySet()) {
            if (slot == SLOT_BACK) slot++;
            if (slot >= size) break;
            Material mat = entry.getKey();
            inv.setItem(slot++, withEntry(makeItem(mat, "§f" + formatName(mat),
                    sellLore(player, mat, entry.getValue())), mat.name()));
        }

        inv.setItem(SLOT_BACK, makeItem(Material.ARROW, "§7← Retour", List.of()));
        player.openInventory(inv);
    }

    /** Bulle d'une ressource à vendre (prix, demande, stock, quantités du joueur). */
    public List<String> sellLore(Player player, Material mat, SellPrice sp) {
        boolean employee = market.isEmployee(player, npcType);
        int stock  = dataManager.getInventoryAmount(npcType, mat);
        int owned  = ItemTags.countUntagged(player, mat);
        int tagged = ItemTags.countTagged(player, mat);
        MarketService.SellQuote all = market.quoteSell(npcType, mat, owned, Integer.MAX_VALUE, employee);

        List<String> lore = new ArrayList<>();
        lore.add("§7Prix plein : §6" + sp.price() + " coins §7/ " + sp.quantity());
        lore.add("§7Prix actuel : §6" + format(market.currentLotPrice(npcType, mat, employee))
                + " coins §7/ " + sp.quantity());
        lore.add("§7Demande : " + market.demandLabel(stock, sp.capacity()));
        lore.add(market.stockBar(stock, sp.capacity()));
        if (employee) lore.add("§a💼 Prime d'employé : §f+" + market.cfg().jobSellBonus() + "%");
        lore.add("");
        lore.add("§7Vous avez : §f" + owned);
        if (all.lots() > 0) {
            lore.add("§7Tout vendre : §f" + all.items() + " §7→ §6" + all.coins() + " coins");
            lore.add("");
            lore.add("§eClic gauche §7: tout vendre");
            lore.add("§eClic droit §7: vendre 1 lot");
        } else if (sp.capacity() > 0 && stock + sp.quantity() > sp.capacity()) {
            lore.add("");
            lore.add("§cEntrepôt plein — revenez plus tard");
        } else {
            lore.add("");
            lore.add("§cIl faut au moins " + sp.quantity() + " items");
        }
        if (tagged > 0) lore.add("§8" + tagged + " item(s) achetés à un PNJ : non revendables");
        return lore;
    }

    /* =========================
       SOUS-MENU ENTREPÔT
       ========================= */

    public void openInventory(Player player) {
        if (themed != null && themed.openStock(player)) return;
        Map<Material, SellPrice> prices = config.getSellPrices();
        int size = menuSize(prices.size());
        Inventory inv = Bukkit.createInventory(null, size, titleInventory(npcType));
        fillFiller(inv, size, Material.ORANGE_STAINED_GLASS_PANE);

        int slot = 0;
        for (Map.Entry<Material, SellPrice> entry : prices.entrySet()) {
            if (slot == SLOT_BACK) slot++;
            if (slot >= size) break;
            Material mat   = entry.getKey();
            int      stock = dataManager.getInventoryAmount(npcType, mat);
            ItemStack icon = makeItem(stock > 0 ? mat : Material.GRAY_STAINED_GLASS_PANE,
                    (stock > 0 ? "§f" : "§8") + formatName(mat), stockLore(player, mat, entry.getValue()));
            inv.setItem(slot++, withEntry(icon, mat.name()));
        }

        if (prices.isEmpty()) {
            inv.setItem(4, makeItem(Material.BARRIER, "§cAucune réserve",
                    List.of("§7Ce personnage ne stocke rien.")));
        }

        inv.setItem(SLOT_BACK, makeItem(Material.ARROW, "§7← Retour", List.of()));
        player.openInventory(inv);
    }

    /** Bulle d'une réserve de l'entrepôt (stock ; on s'y sert gratuitement). */
    public List<String> stockLore(Player player, Material mat, SellPrice sp) {
        int stock = dataManager.getInventoryAmount(npcType, mat);
        int stack = Math.max(sp.quantity(), mat.getMaxStackSize());
        List<String> lore = new ArrayList<>();
        lore.add(market.stockBar(stock, sp.capacity()));
        lore.add("");
        lore.add("§aGratuit §7: réserve de la ville");
        lore.add("§8Dépôt payé par le PNJ : "
                + format(market.currentLotPrice(npcType, mat, false)) + " / " + sp.quantity());
        lore.add("");
        if (stock <= 0) {
            lore.add("§cRéserve vide");
        } else {
            lore.add("§eClic §7: prendre " + Math.min(stock, sp.quantity()));
            if (stock > sp.quantity()) lore.add("§eMaj + clic §7: prendre " + Math.min(stock, stack));
            lore.add("§8Objets pris : non revendables");
        }
        return lore;
    }

    /* =========================
       SOUS-MENU BOUTIQUE
       ========================= */

    public void openShop(Player player) {
        if (themed != null && themed.openShop(player)) return;
        int currentLevel = dataManager.getLevel(npcType);
        List<ShopItem> entries = config.getAllShopItems();

        int size = menuSize(entries.size());
        Inventory inv = Bukkit.createInventory(null, size, titleShop(npcType));
        fillFiller(inv, size, Material.BLUE_STAINED_GLASS_PANE);

        int slot = 0;
        for (ShopItem item : entries) {
            if (slot == SLOT_BACK) slot++;
            if (slot >= size) break;

            boolean unlocked = item.level() <= currentLevel;
            List<String> lore = shopLore(player, item);
            ItemStack icon;
            if (!unlocked) {
                icon = makeItem(Material.GRAY_STAINED_GLASS_PANE, "§8🔒 " + displayName(item), lore);
            } else {
                icon = makeItem(item.material(), "§f" + displayName(item), lore);
                if (!item.enchantments().isEmpty()) {
                    // Effet brillant sur l'icône
                    ItemMeta meta = icon.getItemMeta();
                    meta.setEnchantmentGlintOverride(true);
                    icon.setItemMeta(meta);
                }
            }
            inv.setItem(slot++, withEntry(icon, item.key()));
        }

        if (entries.isEmpty()) {
            inv.setItem(4, makeItem(Material.BARRIER, "§cBoutique vide",
                    List.of("§7Aucun article configuré.")));
        }

        inv.setItem(SLOT_BACK, makeItem(Material.ARROW, "§7← Retour", List.of()));
        player.openInventory(inv);
    }

    /** Bulle d'un article de boutique (niveau, prix, ingrédients, disponibilité). */
    public List<String> shopLore(Player player, ShopItem item) {
        int currentLevel = dataManager.getLevel(npcType);
        boolean employee = market.isEmployee(player, npcType);
        boolean unlocked = item.level() <= currentLevel;
        List<String> lore = new ArrayList<>();
        lore.add(getLevelName(item.level()) + (unlocked ? " §a✔" : " §c✘"));
        lore.addAll(item.lore());
        lore.addAll(enchantLore(item));
        lore.add("");

        if (!unlocked) {
            lore.add("§cDébloqué au niveau " + getLevelName(item.level()));
            lore.add("§8Commercez avec " + plainName() + " pour");
            lore.add("§8débloquer cet article.");
            return lore;
        }
        int finalPrice = market.shopPrice(item, employee);
        if (employee && finalPrice < item.price()) {
            lore.add("§7Prix : §4§m" + item.price() + "§r §6" + finalPrice + " coins");
            lore.add("§a💼 Réduction employé : §f-" + market.cfg().jobShopDiscount() + "%");
        } else {
            lore.add("§7Prix : §6" + finalPrice + " coins");
        }
        lore.add("§7Quantité : §fx" + item.quantity());

        for (Requirement r : item.requires()) {
            CityNPC supplier = market.supplierOf(npcType, r);
            int have = market.stockOf(supplier, r.materials());
            boolean ok = have >= r.amount();
            String who = supplier == null ? "§c?"
                    : supplier == npcType ? "son stock"
                    : "stock " + supplier.displayName.replaceAll("§.", "");
            lore.add((ok ? "§a✔ " : "§c✘ ") + "§7" + r.amount() + "× "
                    + tokenName(r) + " §8(" + who + " : " + have + ")");
        }
        int available = market.availableCount(npcType, item);
        if (item.supply() != null) {
            int supply = market.getSupply(npcType, item);
            long next = market.minutesUntilRestock(npcType, item);
            lore.add("§7Disponible : §f" + supply + "§7/§f" + item.supply().max()
                    + (next > 0 ? " §8(+1 dans " + next + " min)" : ""));
        }
        lore.add("");
        lore.add(available > 0 ? "§eCliquez pour acheter" : "§cRupture de stock");
        return lore;
    }

    /** Clé d'entrée stockée dans l'icône (matériau ou clé d'article), ou null. */
    public String readEntry(ItemStack icon) {
        if (icon == null || !icon.hasItemMeta()) return null;
        return icon.getItemMeta().getPersistentDataContainer().get(entryKey, PersistentDataType.STRING);
    }

    /* =========================
       HELPERS
       ========================= */

    private ItemStack withEntry(ItemStack icon, String value) {
        ItemMeta meta = icon.getItemMeta();
        meta.getPersistentDataContainer().set(entryKey, PersistentDataType.STRING, value);
        icon.setItemMeta(meta);
        return icon;
    }

    public List<String> backstoryLore() {
        List<String> lore = new ArrayList<>();
        for (String line : npcType.backstory) lore.add("§8§o" + line);
        return lore;
    }

    private List<String> enchantLore(ShopItem item) {
        List<String> lore = new ArrayList<>();
        for (Map.Entry<Enchantment, Integer> e : item.enchantments().entrySet()) {
            lore.add("§9" + formatName(e.getKey().getKey().getKey().toUpperCase()) + " " + roman(e.getValue()));
        }
        if (item.potion() != null) lore.add("§9Potion : " + formatName(item.potion().name()));
        if (item.book() != null)   lore.add("§9« " + item.book().title() + " »");
        return lore;
    }

    public String displayName(ShopItem item) {
        return item.displayName() != null ? item.displayName() : formatName(item.material());
    }

    private String tokenName(Requirement r) {
        // Un seul objet (vanilla ou mod) : son nom ; un groupe, un tag ou un motif : « … (au choix) »
        if (r.materials().size() == 1) return formatName(r.materials().iterator().next());
        String token = r.token().startsWith("#") ? r.token().substring(1) : r.token();
        String name = token.contains("*") || token.contains(":") || token.contains(",")
                ? formatName(r.materials().iterator().next()) : formatName(token);
        return name + " §8(au choix)§7";
    }

    private int menuSize(int entries) {
        // +1 pour le bouton retour (slot 8), max 54
        return Math.min(54, Math.max(18, (int) Math.ceil((entries + 1) / 9.0) * 9));
    }

    private String plainName() {
        return npcType.displayName.replaceAll("§.", "");
    }

    private static String roman(int n) {
        return switch (n) {
            case 1 -> "I"; case 2 -> "II"; case 3 -> "III"; case 4 -> "IV"; case 5 -> "V";
            default -> String.valueOf(n);
        };
    }

    private static String format(double v) {
        return v >= 10 ? String.valueOf(Math.round(v)) : String.format(java.util.Locale.FRANCE, "%.1f", v);
    }

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
        int xpNeededLevel = Math.max(1, xpNext - xpCurrent);
        int barLength     = 10;
        int filled        = Math.max(0, Math.min(
                (int) ((double) xpInLevel / xpNeededLevel * barLength), barLength));

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
            case 3  -> "§bCompagnon";
            case 4  -> "§6Expert";
            default -> "§4Maître";
        };
    }

    public int getXpForNextLevel(int level) {
        return config.getXpThresholds().getOrDefault(level + 1, -1);
    }

    public CityNPC        getNpcType() { return npcType; }
    public VillagerConfig getConfig()  { return config; }

    private void fillFiller(Inventory inv, int size, Material mat) {
        ItemStack filler = makeItem(mat, " ");
        for (int i = 0; i < size; i++) inv.setItem(i, filler);
    }

    public static String formatName(Material mat) {
        return com.citycore.integration.ItemIds.label(mat); // noms français des objets de mods
    }

    public static String formatName(String raw) {
        StringBuilder sb = new StringBuilder();
        for (String word : raw.split("_")) {
            if (word.isEmpty()) continue;
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
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS,
                ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack makeJobButton(Player player) {
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
                            "§a+" + market.cfg().jobSellBonus() + "% §7sur vos ventes à ce PNJ",
                            "§a-" + market.cfg().jobShopDiscount() + "% §7sur vos achats chez lui",
                            "",
                            "§eCliquez pour accepter."
                    ));
        }
    }

    private ItemStack makeItem(Material mat, String name) {
        return makeItem(mat, name, List.of());
    }
}
