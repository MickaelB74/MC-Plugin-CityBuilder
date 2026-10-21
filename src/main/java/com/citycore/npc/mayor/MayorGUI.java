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
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

public class MayorGUI implements NPCGui {

    public static final String GUI_TITLE = ChatColor.GOLD + CityNPC.MAYOR.displayName + " — Maire";

    public static final int SLOT_INFO        = 0;
    public static final int SLOT_BUILDINGS   = 2;
    public static final int SLOT_ECONOMY     = 4;
    public static final int SLOT_EXPAND      = 6;
    public static final int SLOT_FOLLOW      = 8;
    public static final int SLOT_POPULATION  = 3;
    /** Parcelles : revendiquer ou rendre celle où l'on se tient, voir les limites de la ville. */
    public static final int SLOT_PARCELS     = 7;
    /** Événements (plan « Raids ») : raids, sièges, fêtes. */
    public static final int SLOT_EVENTS      = 5;

    /** Ouvre l'écran des événements (raids) ; null = case absente. */
    private java.util.function.Consumer<Player> events;

    public void setEvents(java.util.function.Consumer<Player> events) {
        this.events = events;
    }

    /** Besoins et satisfaction (phase 6) : bilan affiché dans le chat (menu classique) ; null = case absente. */
    private java.util.function.Supplier<List<String>> populationReport;

    public void setPopulationReport(java.util.function.Supplier<List<String>> report) {
        this.populationReport = report;
    }

    private final CityManager      cityManager;
    private final NPCManager       npcManager;
    private final MayorBuildingGUI buildingGUI;
    private final MayorEconomyGUI  economyGUI;
    private final MayorQuestGUI    questGUI;
    private final CityQuestManager cityQuestManager;
    private final BuildingManager  buildingManager;
    private final JavaPlugin       plugin;
    /** Écrans illustrés « Valmont » (null = menus classiques uniquement). */
    private com.citycore.ui.screens.MayorScreens themed;

    public void setThemed(com.citycore.ui.screens.MayorScreens themed) {
        this.themed = themed;
        buildingGUI.setThemed(themed);
        economyGUI.setThemed(themed);
        questGUI.setThemed(themed);
    }

    public MayorGUI(CityManager cityManager, NPCManager npcManager,
                    BuildingManager buildingManager, Economy economy,
                    MayorQuestGUI questGUI, CityQuestManager cityQuestManager, JavaPlugin plugin) {
        this.cityManager      = cityManager;
        this.npcManager       = npcManager;
        this.buildingGUI      = new MayorBuildingGUI(buildingManager, plugin);
        this.economyGUI       = new MayorEconomyGUI(cityManager, economy, cityQuestManager);
        this.questGUI         = questGUI;
        this.cityQuestManager = cityQuestManager;
        this.buildingManager  = buildingManager;
        this.plugin           = plugin;
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
                expand(player);
                open(player);
            }
            case SLOT_EVENTS -> {
                if (events != null) events.accept(player);
            }
            case SLOT_POPULATION -> {
                if (populationReport == null) return;
                player.closeInventory();
                populationReport.get().forEach(player::sendMessage);
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

    /** Achat d'un emplacement de chunk (Agrandir) : messages du résultat, sans rouvrir de menu. */
    public void expand(Player player) {
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
            player.sendMessage("§7💡 Déposez des coins dans la caisse : " + CityNPC.MAYOR.displayName
                    + " §7→ §e« Trésor »§7.");
        }
    }

    public void open(Player player) {
        if (themed != null && themed.openMain(player)) return; // joueur équipé du pack
        City city = cityManager.getCity();
        if (city == null) return;

        CityTier tier         = CityTier.fromLevel(cityQuestManager.getCityLevel());
        boolean  tierComplete = cityQuestManager.isTierComplete(tier);

        Inventory inv = Bukkit.createInventory(null, 9, GUI_TITLE);

        ItemStack filler = makeItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 9; i++) inv.setItem(i, filler);

        // ── INFO + QUÊTES ────────────────────────────────────────
        inv.setItem(SLOT_INFO, makeItem(
                tierComplete ? Material.NETHER_STAR : Material.BOOK,
                tierComplete ? "§6📖 Infos de la ville §l[PRÊT]" : "§6📖 Infos de la ville",
                List.of(
                        "§7Niveau  : §f" + tier.level,
                        "§7Palier  : " + tier.tierName,
                        "§7" + tier.chapter,
                        "§7Taille  : §f" + city.getClaimedChunks()
                                + " §7/ §f" + city.getMaxChunks(),
                        "",
                        tierComplete
                                ? "§a✔ Toutes les quêtes complètes !"
                                : "§7Quêtes du palier disponibles.",
                        "",
                        "§8Vue d'ensemble : menu Progrès (touche L),",
                        "§8onglet « La Renaissance de Valmont ».",
                        "",
                        "§eCliquez pour ouvrir les quêtes"
                )));

        // ── BÂTIMENTS ────────────────────────────────────────────
        inv.setItem(SLOT_BUILDINGS, makeItem(Material.BRICKS, "§a🏗 Bâtiments",
                List.of("§7Gérez les bâtiments de la ville.",
                        "", "§eCliquez pour ouvrir")));

        // ── POPULATION (phase 6) ──────────────────────────────────
        if (populationReport != null) {
            List<String> report = populationReport.get();
            List<String> lore = new ArrayList<>(report.subList(1, report.size()));
            lore.add("");
            lore.add("§eCliquez : bilan dans le chat");
            inv.setItem(SLOT_POPULATION, makeItem(Material.SUNFLOWER, report.get(0), lore));
        }

        // ── ÉCONOMIE ─────────────────────────────────────────────
        inv.setItem(SLOT_ECONOMY, makeItem(Material.GOLD_INGOT, "§6💰 Trésor",
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

        // ── ÉVÉNEMENTS (raids, sièges, fêtes) ───────────────────
        if (events != null) {
            inv.setItem(SLOT_EVENTS, makeItem(Material.BELL, "§6📅 Événements",
                    List.of("§7Raids, sièges et fêtes de Valmont.", "", "§eCliquez pour ouvrir")));
        }

        // ── PARCELLES ────────────────────────────────────────────
        inv.setItem(SLOT_PARCELS, makeItem(Material.FILLED_MAP, "§e🗺 Parcelles de la ville",
                parcelLore(player, city)));

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

    /* =========================
       PARCELLES (chunks de la ville)
       ========================= */

    /**
     * Bulle des parcelles (menu classique et barre « Parcelles » de l'écran illustré) : compte,
     * parcelle où se tient le joueur, gestes possibles.
     */
    public List<String> parcelLore(Player player, City city) {
        List<String> lore = new ArrayList<>(List.of(
                "§7Revendiquées : §f" + city.getClaimedChunks(),
                "§7Emplacements : §f" + city.getMaxChunks(),
                com.citycore.city.ParcelActions.statusHere(cityManager, player),
                "",
                "§eClic gauche §7: revendiquer la parcelle où vous êtes",
                "§eClic droit §7: voir les limites de la ville (5 s)",
                "§eMaj + clic droit §7: rendre la parcelle où vous êtes",
                "",
                "§8Plus loin ? Demandez à Alderic de vous",
                "§8suivre, puis parlez-lui sur place."));
        return lore;
    }

    /** Clic sur les parcelles : gauche = revendiquer, droit = limites, Maj + droit = rendre. */
    public void handleParcelClick(Player player, org.bukkit.event.inventory.ClickType click) {
        if (click.isRightClick() && click.isShiftClick()) {
            com.citycore.city.ParcelActions.unclaimHere(cityManager, buildingManager, player);
        } else if (click.isRightClick()) {
            com.citycore.city.ParcelActions.showBorders(plugin, cityManager, player);
            return; // le menu se ferme pour laisser voir les limites
        } else {
            com.citycore.city.ParcelActions.claimHere(cityManager, player);
        }
        // Rouvert au tick suivant : compte et état de la parcelle à jour
        Bukkit.getScheduler().runTask(plugin, () -> { if (player.isOnline()) open(player); });
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