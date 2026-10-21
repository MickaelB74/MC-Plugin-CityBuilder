package com.citycore.npc.villager;

import com.citycore.building.Building;
import com.citycore.building.BuildingManager;
import com.citycore.city.CityManager;
import com.citycore.economy.ItemTags;
import com.citycore.economy.MarketService;
import com.citycore.npc.*;
import com.citycore.npc.villager.VillagerConfig.SellPrice;
import com.citycore.npc.villager.VillagerConfig.ShopItem;
import com.citycore.player.PlayerDataManager;
import com.citycore.quest.QuestGUI;
import com.citycore.util.TypewriterUtil;
import net.citizensnpcs.api.event.NPCRightClickEvent;
import net.citizensnpcs.api.npc.NPC;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public class VillagerListener implements Listener {

    private final CityNPC                npcType;
    private final VillagerGUI            gui;
    private final NPCManager             npcManager;
    private final NPCDataManager         dataManager;
    private final Economy                economy;
    private final CityManager            cityManager;
    private final IntroductionManager    introManager;
    private final JavaPlugin             plugin;
    private final QuestGUI               questGUI;
    private final NPCNotificationManager notificationManager;
    private final BuildingManager        buildingManager;
    private final PlayerDataManager      playerDataManager;
    private final MarketService          market;
    /** Inspection des constructions (phase 2) : un artisan ne s'installe que dans un atelier conforme. */
    private com.citycore.building.BuildingInspector inspector;

    public void setInspector(com.citycore.building.BuildingInspector inspector) {
        this.inspector = inspector;
    }

    public com.citycore.building.BuildingInspector inspector() {
        return inspector;
    }

    public VillagerListener(CityNPC npcType, VillagerGUI gui, NPCManager npcManager,
                            NPCDataManager dataManager, Economy economy,
                            CityManager cityManager, IntroductionManager introManager,
                            QuestGUI questGUI, JavaPlugin plugin,
                            NPCNotificationManager notificationManager,
                            BuildingManager buildingManager, PlayerDataManager playerDataManager,
                            MarketService market) {
        this.npcType             = npcType;
        this.gui                 = gui;
        this.npcManager          = npcManager;
        this.dataManager         = dataManager;
        this.economy             = economy;
        this.cityManager         = cityManager;
        this.introManager        = introManager;
        this.plugin              = plugin;
        this.questGUI            = questGUI;
        this.notificationManager = notificationManager;
        this.buildingManager     = buildingManager;
        this.playerDataManager   = playerDataManager;
        this.market              = market;
    }

    @EventHandler
    public void onNPCRightClick(NPCRightClickEvent event) {
        CityNPC type = npcManager.getNPCType(event.getNPC());
        if (type != npcType) return;

        Player player = event.getClicker();
        NPC npc = npcManager.getNPC(type);

        notificationManager.clearNotification(player.getUniqueId(), type,
                plugin.getServer(), npcManager);

        // Activé pour les tests : menu complet tout de suite, sans rencontre ni suivi vers la ville
        if (com.citycore.npc.TestActivation.applies(type, player)) {
            gui.open(player);
            return;
        }

        // Stop la balade si en ville sans logis (ARRIVED ou HOMELESS) — face au joueur
        if (dataManager.getState(type).needsHome() && npc != null) {
            npc.getNavigator().cancelNavigation();
            facePlayer(npc, player);
        }

        if (!introManager.hasSeenIntro(player.getUniqueId(), npcType)) {
            introManager.markIntroSeen(player.getUniqueId(), npcType);

            if (dataManager.getState(npcType) == NPCState.WANDERER) {
                npcManager.startFollowing(player, npcType);
                player.sendMessage("§a" + npcType.displayName
                        + " §avous suit vers la ville !");
            }

            List<String> lines = npcType.getDialogue("first_meeting");
            if (!lines.isEmpty()) {
                TypewriterUtil.play(plugin, player, lines,
                        () -> gui.open(player));
                return;
            }
        }

        gui.open(player);
    }

    @EventHandler
    public void onPlayerJoin(org.bukkit.event.player.PlayerJoinEvent event) {
        // Rafraîchit le hologramme pour ce joueur
        notificationManager.refreshOnLogin(event.getPlayer().getUniqueId(),
                npcType, npcManager, plugin.getServer());
    }

    /**
     * Tourne le NPC vers le joueur.
     */
    private void facePlayer(NPC npc, Player player) {
        if (!npc.isSpawned()) return;

        // Stop complet de la navigation
        npc.getNavigator().cancelNavigation();
        npc.getNavigator().setPaused(true);

        Location npcLoc    = npc.getEntity().getLocation().clone();
        Location playerLoc = player.getLocation();
        double dx = playerLoc.getX() - npcLoc.getX();
        double dz = playerLoc.getZ() - npcLoc.getZ();
        float yaw = (float)(Math.toDegrees(Math.atan2(-dx, dz)));
        npcLoc.setYaw(yaw);
        npcLoc.setPitch(0);
        npc.teleport(npcLoc,
                org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.PLUGIN);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String title = event.getView().getTitle();
        if (!isOurGui(title)) return;

        event.setCancelled(true);
        // Les clics dans l'inventaire du joueur ne déclenchent rien
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null) return;
        int slot = event.getRawSlot();

        // ── GUI Wanderer ─────────────────────────────────────────
        if (VillagerGUI.titleWanderer(npcType).equals(title)) {
            if (slot == 8) toggleFollow(player);
            return;
        }

        // ── GUI Arrived ──────────────────────────────────────────
        if (VillagerGUI.titleArrived(npcType).equals(title)) {
            switch (slot) {
                case 4 -> gui.openBuildingSearch(player, freeBuildings()); // Bouton recherche
                case 8 -> toggleWander(player);                            // Toggle marche
            }
            return;
        }

        // ── GUI BuildingSearch ───────────────────────────────────
        if (VillagerGUI.titleBuildingSearch(npcType).equals(title)) {
            handleBuildingSearch(player, clicked, slot, event.getView().getTopInventory().getSize());
            return;
        }

        // ── Sous-menus ───────────────────────────────────────────
        if (VillagerGUI.titleSell(npcType).equals(title)) {
            if (slot == VillagerGUI.SLOT_BACK) { gui.open(player); return; }
            handleSell(player, clicked, event.isRightClick());
            return;
        }
        if (VillagerGUI.titleInventory(npcType).equals(title)) {
            if (slot == VillagerGUI.SLOT_BACK) { gui.open(player); return; }
            handleStockPurchase(player, clicked, event.isShiftClick());
            return;
        }
        if (VillagerGUI.titleShop(npcType).equals(title)) {
            if (slot == VillagerGUI.SLOT_BACK) { gui.open(player); return; }
            handleShopPurchase(player, clicked);
            return;
        }

        // ── Menu principal ──────────────────────────────────────
        switch (slot) {
            case VillagerGUI.SLOT_SELL      -> gui.openSell(player);
            case VillagerGUI.SLOT_INVENTORY -> gui.openInventory(player);
            case VillagerGUI.SLOT_SHOP      -> gui.openShop(player);
            case VillagerGUI.SLOT_FOLLOW    -> toggleFollow(player);
            case VillagerGUI.SLOT_QUESTS -> questGUI.open(player);
            case VillagerGUI.SLOT_JOB    -> toggleJob(player);
        }
    }

    private boolean isOurGui(String title) {
        return VillagerGUI.titleWanderer(npcType).equals(title)
                || VillagerGUI.titleArrived(npcType).equals(title)
                || VillagerGUI.titleBuildingSearch(npcType).equals(title)
                || VillagerGUI.titleSell(npcType).equals(title)
                || VillagerGUI.titleInventory(npcType).equals(title)
                || VillagerGUI.titleShop(npcType).equals(title)
                || title.startsWith(npcType.displayName + " §8— §7" + npcType.function);
    }

    /* =========================
       BÂTIMENTS
       ========================= */

    private void handleBuildingSearch(Player player, ItemStack clicked, int slot, int size) {
        // Bouton retour — dernier slot
        if (slot == size - 1) {
            gui.open(player);
            return;
        }
        if (clicked.getType() != Material.BRICKS) return;

        // Récupère le nom du bâtiment depuis le displayName
        String rawName = clicked.getItemMeta().getDisplayName();
        String buildingName = rawName.replace("§e🏛 ", "").trim();

        Building target = buildingManager.getAllBuildings().stream()
                .filter(b -> b.name().equals(buildingName))
                .findFirst().orElse(null);

        if (target == null) return;
        assignBuilding(player, target);
    }

    /**
     * Logis possibles pour ce PNJ : les ateliers sans habitant, libres ou réservés à lui
     * (ceux qui lui sont réservés d'abord). Habitations, mairie et « Divers » sont exclus.
     */
    public List<Building> freeBuildings() {
        return buildingManager.freeAteliersFor(npcType);
    }

    /**
     * Installe le PNJ dans un bâtiment libre (étape 3 du recrutement).
     * Le bâtiment est relu au moment du clic : la liste affichée a pu changer entre-temps
     * (bâtiment supprimé, ou attribué à un autre habitant par un autre joueur).
     */
    public void assignBuilding(Player player, Building chosen) {
        if (dataManager.getState(npcType) == NPCState.ASSIGNED) {
            player.sendMessage("§7" + npcType.displayName + " §7est déjà installé.");
            player.closeInventory();
            return;
        }
        Building target = buildingManager.getAllBuildings().stream()
                .filter(b -> b.id() == chosen.id())
                .findFirst().orElse(null);
        if (target == null) {
            player.sendMessage("§c❌ Ce bâtiment n'existe plus.");
            gui.openBuildingSearch(player, freeBuildings());
            return;
        }
        if (target.npcTag() != null) {
            player.sendMessage("§c❌ Ce bâtiment a déjà un NPC assigné.");
            gui.openBuildingSearch(player, freeBuildings());
            return;
        }
        // Le type a pu changer entre-temps (/city build type, autre joueur)
        if (!target.acceptsArtisan(npcType)) {
            player.sendMessage("§c❌ §e" + target.name() + " §cn'est plus un atelier disponible pour "
                    + npcType.displayName + "§c.");
            gui.openBuildingSearch(player, freeBuildings());
            return;
        }
        // Inspection : l'atelier doit contenir ce que demande ce personnage (phase 2)
        if (inspector != null) {
            inspector.invalidate(target.id()); // état du bâtiment à l'instant du clic
            com.citycore.building.Inspection insp = inspector.inspectFor(target, npcType);
            if (!insp.compliant()) {
                player.sendMessage("§c❌ " + npcType.displayName + " §cne peut pas s'installer dans §e"
                        + target.name() + "§c : il manque §f" + insp.missing() + "§c.");
                gui.openBuildingSearch(player, freeBuildings());
                return;
            }
        }

        // Assigne directement le NPC au bâtiment (état changé seulement si l'écriture a réussi)
        if (!buildingManager.assignNPC(target.id(), npcType.tag)) {
            player.sendMessage("§c❌ Impossible d'installer " + npcType.displayName + " §cici.");
            return;
        }
        boolean wasHomeless = dataManager.getState(npcType) == NPCState.HOMELESS;
        dataManager.setState(npcType, NPCState.ASSIGNED);
        if (inspector != null) inspector.onAssigned(npcType, target); // entrepôt selon le niveau de l'atelier
        if (wasHomeless) {
            player.sendMessage("§a▶ " + npcType.displayName + " §aretrouve un atelier : ses quêtes reprennent.");
        }

        // Fin de la conversation : le PNJ cesse sa balade et rejoint son point de travail.
        // (Il avait été mis en pause pour faire face au joueur ; sans cela il restait figé.)
        if (npcManager.isFollowing(player, npcType)) npcManager.stopFollowing(player, npcType);
        npcManager.setWandering(npcType, false);
        NPC citizen = npcManager.getNPC(npcType);
        if (citizen != null && citizen.isSpawned()) {
            citizen.getNavigator().setPaused(false);
            citizen.getNavigator().cancelNavigation();
        }

        // Dialogue building_assign (variante si l'habitant est lié à un mod absent, ou enrichi par
        // un mod facultatif présent : building_assign_mod)
        List<String> lines = gui.isAvailable() ? npcType.getDialogue("building_assign") : List.of();
        if (gui.isAvailable() && !npcType.optionalMods.isEmpty()
                && npcType.optionalMods.stream().allMatch(com.citycore.integration.ModIntegrations::present)
                && !npcType.getDialogue("building_assign_mod").isEmpty()) {
            lines = npcType.getDialogue("building_assign_mod");
        }
        if (lines.isEmpty()) lines = npcType.getDialogue("building_assign_unavailable");
        if (lines.isEmpty()) lines = npcType.getDialogue("building_assign");
        if (!lines.isEmpty()) {
            TypewriterUtil.play(plugin, player, lines, null);
        }

        notificationManager.notifyAll(npcType, plugin.getServer(), npcManager);

        player.closeInventory();
        player.sendMessage("§a✅ §e" + plainName()
                + " §aassigné au bâtiment §e" + target.name() + "§a !");
    }

    /* =========================
       VENTE (JOUEUR → PNJ)
       ========================= */

    private void handleSell(Player player, ItemStack clicked, boolean oneLot) {
        Material mat = materialOf(clicked);
        if (mat != null) sell(player, mat, oneLot);
    }

    /** Vend tout (ou un lot) d'une ressource au PNJ. */
    public void sell(Player player, Material mat, boolean oneLot) {
        SellPrice sp = gui.getConfig().getSellPrice(mat);
        if (sp == null) return;

        boolean employee = market.isEmployee(player, npcType);
        int owned = ItemTags.countUntagged(player, mat);
        MarketService.SellQuote quote = market.quoteSell(npcType, mat, owned,
                oneLot ? 1 : Integer.MAX_VALUE, employee);

        if (quote.lots() == 0) {
            int stock = dataManager.getInventoryAmount(npcType, mat);
            if (sp.capacity() > 0 && stock + sp.quantity() > sp.capacity()) {
                player.sendMessage("§c❌ " + npcType.displayName
                        + " §cn'a plus de place pour stocker ça. Revenez plus tard.");
            } else {
                player.sendMessage("§c❌ Il faut au moins §f" + sp.quantity()
                        + " " + VillagerGUI.formatName(mat) + " §cnon achetés à un PNJ.");
            }
            gui.openSell(player);
            return;
        }

        int removed = ItemTags.removeUntagged(player, mat, quote.items());
        if (removed < quote.items()) {
            // Inventaire modifié entre-temps : on rend ce qui a été retiré
            if (removed > 0) ItemTags.give(player, new ItemStack(mat, removed));
            gui.openSell(player);
            return;
        }

        dataManager.addToInventory(npcType, mat, quote.items());
        if (quote.coins() > 0) economy.depositPlayer(player, quote.coins());

        int xpGained = quote.lots() * gui.getConfig().getXpPerStack()
                + quote.coins() * gui.getConfig().getXpPerCoin();
        player.sendMessage("§a✅ Vendu §f" + quote.items() + " " + VillagerGUI.formatName(mat)
                + " §a: §6+" + quote.coins() + " coins §7(§b+" + xpGained + " XP§7)");
        grantXp(player, xpGained);
        gui.openSell(player);
    }

    /* =========================
       ENTREPÔT (STOCK → JOUEUR)
       ========================= */

    private void handleStockPurchase(Player player, ItemStack clicked, boolean stack) {
        Material mat = materialOf(clicked);
        if (mat != null) takeStock(player, mat, stack);
    }

    /**
     * Prend dans l'entrepôt du PNJ, gratuitement : un lot, ou une pile entière ({@code stack}).
     * L'entrepôt est la réserve de la ville (y déposer est payé, s'y servir ne coûte rien) ; l'objet
     * est marqué non revendable, sans quoi on pourrait le revendre aussitôt au PNJ.
     */
    public void takeStock(Player player, Material mat, boolean stack) {
        SellPrice sp = gui.getConfig().getSellPrice(mat);
        if (sp == null) return;

        int stock = dataManager.getInventoryAmount(npcType, mat);
        int wanted = stack ? Math.max(sp.quantity(), mat.getMaxStackSize()) : sp.quantity();
        int amount = Math.min(stock, wanted);
        if (amount <= 0) {
            player.sendMessage("§c❌ Réserve vide.");
            return;
        }
        if (!dataManager.removeFromInventory(npcType, mat, amount)) return;

        ItemStack taken = new ItemStack(mat, amount);
        ItemTags.tagStock(taken, plainName());
        ItemTags.give(player, taken);
        player.sendMessage("§a✅ Pris dans la réserve : §f" + amount + " " + VillagerGUI.formatName(mat)
                + " §8(gratuit, non revendable)");
        gui.openInventory(player);
    }

    /* =========================
       BOUTIQUE (FABRICATION → JOUEUR)
       ========================= */

    private void handleShopPurchase(Player player, ItemStack clicked) {
        String key = gui.readEntry(clicked);
        if (key != null) buyShopItem(player, key);
    }

    /** Achète un article de la boutique (fabriqué à partir des stocks de la ville). */
    public void buyShopItem(Player player, String key) {
        ShopItem item = gui.getConfig().getShopItem(key);
        if (item == null) return;

        if (item.level() > dataManager.getLevel(npcType)) {
            player.sendMessage("§c🔒 Article débloqué au niveau "
                    + VillagerGUI.getLevelName(item.level()) + "§c.");
            return;
        }
        if (market.availableCount(npcType, item) < 1) {
            player.sendMessage("§c❌ Rupture de stock : il manque des matières premières"
                    + " ou l'article doit se reconstituer.");
            return;
        }

        int price = market.shopPrice(item, market.isEmployee(player, npcType));
        if (!economy.has(player, price)) {
            player.sendMessage("§c❌ Fonds insuffisants. Nécessaire : §f" + price + " coins");
            return;
        }
        if (!market.consume(npcType, item)) return;
        economy.withdrawPlayer(player, price);
        ItemTags.give(player, market.createStack(item, plainName()));

        int xpGained = price * gui.getConfig().getXpPerCoin();
        String name = item.displayName() != null ? item.displayName()
                : VillagerGUI.formatName(item.material());
        player.sendMessage("§a✅ Acheté §f" + item.quantity() + "x " + name
                + " §apour §6" + price + " coins §7(§b+" + xpGained + " XP§7)");
        grantXp(player, xpGained);
        gui.openShop(player);
    }

    /* =========================
       JOB
       ========================= */

    public void toggleJob(Player player) {
        CityNPC currentJob = playerDataManager.getJob(player.getUniqueId());

        if (npcType == currentJob) {
            playerDataManager.clearJob(player.getUniqueId());
            player.sendMessage("§7Vous avez quitté votre job chez §e"
                    + npcType.displayName + "§7.");
        } else if (currentJob != null) {
            player.sendMessage("§c❌ Vous travaillez déjà pour §e"
                    + currentJob.displayName + "§c.");
        } else {
            playerDataManager.setJob(player.getUniqueId(), npcType);
            player.sendMessage("§a✅ Vous travaillez maintenant pour §e"
                    + npcType.displayName + "§a !");
        }
        gui.open(player);
    }

    /* =========================
       XP (PNJ + JOUEUR)
       ========================= */

    private void grantXp(Player player, int xpGained) {
        if (xpGained <= 0) return;
        boolean levelUp = dataManager.addXP(npcType, xpGained, gui.getConfig().getXpThresholds());

        int playerLevelsGained = playerDataManager.addXP(player.getUniqueId(), xpGained);
        if (playerLevelsGained > 0) {
            int lvl = playerDataManager.getLevel(player.getUniqueId());
            player.sendMessage("§a🎉 §eVous §aêtes passé niveau §e" + lvl + "§a !");
            player.sendTitle("§6⬆ Niveau " + lvl, "§7" + xpGained + " XP accumulés", 10, 40, 10);
        }

        if (levelUp) {
            player.sendMessage("§a🎉 §e" + npcType.displayName + " §aest passé "
                    + VillagerGUI.getLevelName(dataManager.getLevel(npcType))
                    + " §a! De nouveaux articles sont disponibles.");
            List<String> lines = npcType.getDialogue("level_up");
            if (!lines.isEmpty()) TypewriterUtil.play(plugin, player, lines, null);
        }
    }

    /* =========================
       FERMETURE
       ========================= */

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        String title = event.getView().getTitle();

        boolean isOurGUI = VillagerGUI.titleWanderer(npcType).equals(title)
                || VillagerGUI.titleArrived(npcType).equals(title)
                || VillagerGUI.titleBuildingSearch(npcType).equals(title)
                || title.startsWith(npcType.displayName + " §8— §7" + npcType.function);

        if (!isOurGUI) return;

        NPCState state = dataManager.getState(npcType);
        if (state != NPCState.WANDERER) {
            NPC npc = npcManager.getNPC(npcType);
            if (npc != null && npc.isSpawned()) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    // Reprend la navigation (balade en ville, ou trajet vers son point de travail)
                    npc.getNavigator().setPaused(false);
                    npc.getNavigator().cancelNavigation();
                }, 2L);
            }
        }
    }

    /* =========================
       HELPERS
       ========================= */

    private Material materialOf(ItemStack icon) {
        String entry = gui.readEntry(icon);
        return entry == null ? null : com.citycore.integration.ItemIds.resolve(entry);
    }

    private String plainName() {
        return npcType.displayName.replaceAll("§.", "");
    }

    public void toggleFollow(Player player) {
        if (npcManager.isFollowing(player, npcType)) {
            npcManager.stopFollowing(player, npcType);
            player.sendMessage(npcType.displayName + " §7s'est arrêté de vous suivre.");
            if (dataManager.getState(npcType).needsHome()) {
                NPC npc = npcManager.getNPC(npcType);
                if (npc != null && npc.isSpawned())
                    npc.getNavigator().cancelNavigation();
            }
        } else {
            npcManager.startFollowing(player, npcType);
            player.sendMessage(npcType.displayName + " §avous suit désormais.");
        }
        gui.open(player);
    }

    /** Active ou arrête la balade du PNJ dans la ville. */
    public void toggleWander(Player player) {
        boolean current = npcManager.isWandering(npcType);
        npcManager.setWandering(npcType, !current);
        player.sendMessage(!current
                ? "§a🚶 " + npcType.displayName + " §ase balade."
                : "§7🚶 " + npcType.displayName + " §7s'est arrêté.");
        gui.open(player);
    }
}
