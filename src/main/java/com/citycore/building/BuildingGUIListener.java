package com.citycore.building;

import com.citycore.npc.CityNPC;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

public class BuildingGUIListener implements Listener {

    private final BuildingManager buildingManager;

    public BuildingGUIListener(BuildingManager buildingManager) {
        this.buildingManager = buildingManager;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!BuildingGUI.TITLE.equals(event.getView().getTitle())) return;

        event.setCancelled(true);
        if (event.getCurrentItem() == null) return;
        if (event.getCurrentItem().getType() == Material.GRAY_STAINED_GLASS_PANE) return;
        if (event.getCurrentItem().getType() == Material.BARRIER) return;

        String rawName = event.getCurrentItem().getItemMeta().getDisplayName();
        String name    = rawName.replace("§e🏛 ", "").trim();

        buildingManager.getAllBuildings().stream()
                .filter(b -> b.name().equals(name))
                .findFirst()
                .ifPresent(building -> {
                    String npcName = "§7Aucun";
                    if (building.npcTag() != null) {
                        CityNPC npc = CityNPC.fromTag(building.npcTag());
                        npcName = npc != null ? npc.displayName : building.npcDisplayTag();
                    }

                    player.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
                    player.sendMessage("§e🏛 " + building.name());
                    player.sendMessage("§7Monde : §f" + building.world());
                    player.sendMessage("§7Zone X : §f" + building.x1()
                            + " §7→ §f" + building.x2());
                    player.sendMessage("§7Zone Z : §f" + building.z1()
                            + " §7→ §f" + building.z2());
                    player.sendMessage("§7NPC assigné : " + npcName);
                    player.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
                });
    }
}