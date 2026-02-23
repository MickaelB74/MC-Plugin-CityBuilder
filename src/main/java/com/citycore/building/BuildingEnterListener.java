package com.citycore.building;

import com.citycore.city.CityManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class BuildingEnterListener implements Listener {

    private final BuildingManager buildingManager;
    private final CityManager     cityManager;

    // Dernier bâtiment connu par joueur pour éviter le spam
    private final Map<UUID, Integer> lastBuilding = new HashMap<>();

    public BuildingEnterListener(BuildingManager buildingManager,
                                 CityManager cityManager) {
        this.buildingManager = buildingManager;
        this.cityManager     = cityManager;
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        // Optimisation — ignore si le joueur n'a pas changé de bloc
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) return;

        Player player = event.getPlayer();
        UUID uuid     = player.getUniqueId();
        String world  = player.getWorld().getName();
        int x         = event.getTo().getBlockX();
        int z         = event.getTo().getBlockZ();

        Building building = buildingManager.getBuildingAt(world, x, z);

        if (building != null) {
            // Joueur dans un bâtiment
            int lastId = lastBuilding.getOrDefault(uuid, -1);

            if (lastId != building.id()) {
                // Vient d'entrer dans ce bâtiment
                lastBuilding.put(uuid, building.id());

                String cityName = cityManager.isCityInitialized()
                        ? cityManager.getCityName()
                        : "Ville";

                player.sendTitle(
                        "§e" + cityName,
                        "§f" + building.name(),
                        10, 40, 20 // fadeIn, stay, fadeOut en ticks
                );
            }
        } else {
            // Joueur hors de tout bâtiment
            lastBuilding.remove(uuid);
        }
    }
}