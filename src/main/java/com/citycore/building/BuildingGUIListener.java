package com.citycore.building;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class BuildingGUIListener implements Listener {

    private final BuildingManager buildingManager;
    private final JavaPlugin      plugin;

    public BuildingGUIListener(BuildingManager buildingManager, JavaPlugin plugin) {
        this.buildingManager = buildingManager;
        this.plugin          = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        if (!BuildingGUI.TITLE.equals(event.getView().getTitle())) return;

        event.setCancelled(true);

        // L'icône dépend du type du bâtiment : on le reconnaît à son nom
        if (event.getCurrentItem() == null) return;
        if (!event.getCurrentItem().hasItemMeta()) return;
        String rawName = event.getCurrentItem().getItemMeta().getDisplayName();
        if (rawName == null || !rawName.startsWith(BuildingGUI.NAME_PREFIX)) return;
        String name    = rawName.substring(BuildingGUI.NAME_PREFIX.length()).trim();

        buildingManager.getAllBuildings().stream()
                .filter(b -> b.name().equals(name))
                .findFirst()
                .ifPresent(building -> {
                    // Ferme le GUI au prochain tick (obligatoire depuis un InventoryClickEvent)
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        player.closeInventory();
                        new BuildingParticleTask(plugin, player, building).runForSeconds(5);
                        player.sendMessage("§b🏛 §7Bordures de §f" + building.name()
                                + " §7affichées pendant §f5 secondes§7.");
                    });
                });
    }
}