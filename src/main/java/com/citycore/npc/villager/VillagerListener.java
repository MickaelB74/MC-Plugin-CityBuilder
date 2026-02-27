package com.citycore.npc.villager;

import com.citycore.building.Building;
import com.citycore.building.BuildingManager;
import com.citycore.city.CityManager;
import com.citycore.npc.*;
import com.citycore.player.PlayerDataManager;
import com.citycore.quest.QuestGUI;
import com.citycore.util.TypewriterUtil;
import net.citizensnpcs.api.event.NPCRightClickEvent;
import net.citizensnpcs.api.npc.NPC;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
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
    private final NPCNotificationManager notificationManager;
    private final BuildingManager buildingManager;
    private final PlayerDataManager playerDataManager;

    public VillagerListener(CityNPC npcType, VillagerGUI gui, NPCManager npcManager,
                            NPCDataManager dataManager, Economy economy,
                            CityManager cityManager, IntroductionManager introManager,
                            QuestGUI questGUI, JavaPlugin plugin, NPCNotificationManager notificationManager, BuildingManager buildingManager, PlayerDataManager playerDataManager) {
        this.npcType      = npcType;
        this.gui          = gui;
        this.npcManager   = npcManager;
        this.dataManager  = dataManager;
        this.economy      = economy;
        this.cityManager  = cityManager;
        this.introManager = introManager;
        this.plugin       = plugin;
        this.questGUI     = questGUI;
        this.notificationManager = notificationManager;
        this.buildingManager = buildingManager;
        this.playerDataManager = playerDataManager;
    }

    @EventHandler
    public void onNPCRightClick(NPCRightClickEvent event) {
        CityNPC type = npcManager.getNPCType(event.getNPC());
        if (type != npcType) return;

        Player player = event.getClicker();
        NPC npc = npcManager.getNPC(type);

        notificationManager.clearNotification(player.getUniqueId(), type,
                plugin.getServer(), npcManager);

        // ✅ Stop la balade si ARRIVED — face au joueur
        if (dataManager.getState(type) == NPCState.ARRIVED && npc != null) {
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

        // ✅ Stop complet de la navigation
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

        // ── GUI Wanderer ─────────────────────────────────────────
        if (VillagerGUI.titleWanderer(npcType).equals(title)) {
            event.setCancelled(true);
            if (event.getCurrentItem() == null) return;
            if (event.getSlot() == 8) handleFollowToggle(player);
            return;
        }

        // ── GUI Arrived ──────────────────────────────────────────
        if (VillagerGUI.titleArrived(npcType).equals(title)) {
            event.setCancelled(true);
            if (event.getCurrentItem() == null) return;

            switch (event.getSlot()) {
                case 4 -> { // Bouton recherche
                    List<Building> available = buildingManager.getAllBuildings()
                            .stream()
                            .filter(b -> b.npcTag() == null)
                            .collect(java.util.stream.Collectors.toList());
                    gui.openBuildingSearch(player, available);
                }
                case 8 -> { // Toggle marche
                    boolean current = npcManager.isWandering(npcType);
                    npcManager.setWandering(npcType, !current);
                    player.sendMessage(!current
                            ? "§a🚶 " + npcType.displayName + " §ase balade."
                            : "§7🚶 " + npcType.displayName + " §7s'est arrêté.");
                    gui.open(player);
                }
            }
            return;
        }

        // ── GUI BuildingSearch ───────────────────────────────────
        if (VillagerGUI.titleBuildingSearch(npcType).equals(title)) {
            event.setCancelled(true);
            if (event.getCurrentItem() == null) return;

            // Bouton retour — dernier slot
            if (event.getSlot() == event.getView().getTopInventory().getSize() - 1) {
                gui.open(player);
                return;
            }

            if (event.getCurrentItem().getType() == Material.BARRIER) return;
            if (event.getCurrentItem().getType() == Material.GREEN_STAINED_GLASS_PANE) return;

            // Récupère le nom du bâtiment depuis le displayName
            String rawName = event.getCurrentItem().getItemMeta().getDisplayName();
            String buildingName = rawName.replace("§e🏛 ", "").trim();

            Building target = buildingManager.getAllBuildings().stream()
                    .filter(b -> b.name().equals(buildingName))
                    .findFirst().orElse(null);

            if (target == null) return;
            if (target.npcTag() != null) {
                player.sendMessage("§c❌ Ce bâtiment a déjà un NPC assigné.");
                return;
            }

            // ✅ Assigne directement le NPC au bâtiment
            buildingManager.assignNPC(target.id(), npcType.tag);
            dataManager.setState(npcType, NPCState.ASSIGNED);

            // Dialogue building_assign
            List<String> lines = npcType.getDialogue("building_assign");
            if (!lines.isEmpty()) {
                TypewriterUtil.play(plugin, player, lines, null);
            }

            notificationManager.notifyAll(npcType, plugin.getServer(), npcManager);

            player.closeInventory();
            player.sendMessage("§a✅ §e"
                    + npcType.displayName.replaceAll("§.", "")
                    + " §aassigné au bâtiment §e" + target.name() + "§a !");
        }

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
                case VillagerGUI.SLOT_JOB -> {
                    CityNPC currentJob = playerDataManager.getJob(player.getUniqueId());

                    if (npcType == currentJob) {
                        // Quitter le job
                        playerDataManager.clearJob(player.getUniqueId());
                        player.sendMessage("§7Vous avez quitté votre job chez §e"
                                + npcType.displayName + "§7.");
                    } else if (currentJob != null) {
                        // Déjà un job ailleurs
                        player.sendMessage("§c❌ Vous travaillez déjà pour §e"
                                + currentJob.displayName + "§c.");
                    } else {
                        // Accepte le job
                        playerDataManager.setJob(player.getUniqueId(), npcType);
                        player.sendMessage("§a✅ Vous travaillez maintenant pour §e"
                                + npcType.displayName + "§a !");
                    }
                    gui.open(player);
                }
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

                // ✅ XP joueur — même montant que le NPC
                int playerLevelsGained = playerDataManager.addXP(
                        player.getUniqueId(), xpGained);

                if (playerLevelsGained > 0) {
                    player.sendMessage("§a🎉 §eVous §aêtes passé niveau §e"
                            + playerDataManager.getLevel(player.getUniqueId()) + "§a !");
                    player.sendTitle("§6⬆ Niveau "
                                    + playerDataManager.getLevel(player.getUniqueId()),
                            "§7" + xpGained + " XP accumulés", 10, 40, 10);
                }

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

            int stock = dataManager.getInventoryAmount(npcType, mat);

            if (stock < 64) {
                player.sendMessage("§c❌ Stock insuffisant (moins d'un stack).");
                return;
            }

            dataManager.removeFromInventory(npcType, mat, 64);

            ItemStack retrieved = new ItemStack(mat, 64);
            ItemMeta meta = retrieved.getItemMeta();
            // Tag via PersistentDataContainer (API Bukkit standard, pas besoin de NMS)
            meta.getPersistentDataContainer().set(
                    new NamespacedKey(plugin, "buyback"),
                    PersistentDataType.BYTE,
                    (byte) 1
            );
            retrieved.setItemMeta(meta);
            player.getInventory().addItem(retrieved);
            player.sendMessage("§a✅ Récupéré 1 stack de §f" + formatName(mat) + "§a !");

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

            boolean hasJob   = npcType == playerDataManager.getJob(player.getUniqueId());
            int finalPrice   = hasJob
                    ? gui.getConfig().applyJobDiscount(item.price())
                    : item.price();

            if (!economy.has(player, finalPrice)) {
                player.sendMessage("§c❌ Fonds insuffisants. Nécessaire : §f"
                        + finalPrice + " coins");
                return;
            }
            economy.withdrawPlayer(player, finalPrice);
            player.getInventory().addItem(new ItemStack(mat, item.quantity()));

            int xpGained = finalPrice * gui.getConfig().getXpPerCoin(); // XP basé sur le prix payé
            boolean levelUp = dataManager.addXP(npcType, xpGained,
                    gui.getConfig().getXpThresholds());

            // ✅ XP joueur
            int playerLevelsGained = playerDataManager.addXP(
                    player.getUniqueId(), xpGained);

            if (playerLevelsGained > 0) {
                player.sendMessage("§a🎉 §eVous §aêtes passé niveau §e"
                        + playerDataManager.getLevel(player.getUniqueId()) + "§a !");
            }

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

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        String title = event.getView().getTitle();

        boolean isOurGUI = VillagerGUI.titleWanderer(npcType).equals(title)
                || VillagerGUI.titleArrived(npcType).equals(title)
                || title.startsWith(npcType.displayName + " §8— §7" + npcType.function);

        if (!isOurGUI) return;

        if (dataManager.getState(npcType) == NPCState.ARRIVED) {
            boolean someoneFollowing = player.getWorld().getPlayers()
                    .stream().anyMatch(p -> npcManager.isFollowing(p, npcType));

            NPC npc = npcManager.getNPC(npcType);
            if (npc != null && npc.isSpawned()) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    // ✅ Reprend la navigation
                    npc.getNavigator().setPaused(false);
                    npc.getNavigator().cancelNavigation();
                    // La task reprend la balade au prochain cycle
                }, 2L);
            }
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

    private void handleFollowToggle(Player player) {
        if (npcManager.isFollowing(player, npcType)) {
            npcManager.stopFollowing(player, npcType);
            player.sendMessage(npcType.displayName + " §7s'est arrêté de vous suivre.");
            if (dataManager.getState(npcType) == NPCState.ARRIVED) {
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
}