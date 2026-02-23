package com.citycore.building;

import com.citycore.city.CityManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.UUID;

public class BuildingListener implements Listener {

    public static final Material SELECTION_TOOL = Material.STICK;

    private final BuildingSession session;
    private final BuildingManager buildingManager;
    private final CityManager     cityManager;     // ✅ ajouté

    public BuildingListener(BuildingSession session, BuildingManager buildingManager,
                            CityManager cityManager) {
        this.session         = session;
        this.buildingManager = buildingManager;
        this.cityManager     = cityManager;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() == EquipmentSlot.OFF_HAND) return;

        Player player = event.getPlayer();
        UUID uuid     = player.getUniqueId();

        if (!session.isActive(uuid)) return;
        if (player.getInventory().getItemInMainHand().getType()
                != SELECTION_TOOL) return;
        if (event.getClickedBlock() == null) return;

        event.setCancelled(true);
        Location loc = event.getClickedBlock().getLocation();

        // ✅ Vérifie que le bloc est dans un chunk claimé par la ville
        if (!cityManager.isChunkClaimed(loc.getChunk())) {
            player.sendMessage("§c❌ Ce bloc est en dehors de la ville !");
            player.sendMessage("§7Vous ne pouvez définir un bâtiment que dans "
                    + "les chunks claimés.");
            return;
        }

        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            session.setPos1(uuid, loc);
            player.sendMessage("§a✔ §7Coin 1 : §f"
                    + loc.getBlockX() + "§7, §f" + loc.getBlockZ());
            showCornerParticle(player, loc, true);

        } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            session.setPos2(uuid, loc);
            player.sendMessage("§a✔ §7Coin 2 : §f"
                    + loc.getBlockX() + "§7, §f" + loc.getBlockZ());
            showCornerParticle(player, loc, false);
        }

        if (session.isComplete(uuid)) {
            // ✅ Vérifie que TOUTE la zone est dans des chunks claimés
            Location p1 = session.getPos1(uuid);
            Location p2 = session.getPos2(uuid);

            if (!isZoneFullyClaimed(player.getWorld(), p1, p2)) {
                player.sendMessage(
                        "§c❌ La zone sélectionnée dépasse les chunks claimés !");
                player.sendMessage("§7Les deux coins doivent être entièrement "
                        + "dans la ville.");
                // Reset seulement la sélection, garde la session active
                session.setPos1(uuid, null);
                session.setPos2(uuid, null);
                return;
            }

            String name = session.getPendingName(uuid);
            buildingManager.createBuilding(
                    name,
                    player.getWorld().getName(),
                    p1.getBlockX(), p1.getBlockZ(),
                    p2.getBlockX(), p2.getBlockZ()
            );

            player.sendMessage("§a✅ Bâtiment §e" + name + " §acréé !");
            player.sendMessage("§7Zone : §f("
                    + p1.getBlockX() + "§7, §f" + p1.getBlockZ()
                    + "§7) → §f("
                    + p2.getBlockX() + "§7, §f" + p2.getBlockZ() + "§7)");

            session.clear(uuid);
            player.getInventory().setItemInMainHand(null);
        }
    }

    /**
     * Vérifie que tous les chunks couverts par la zone sont claimés.
     */
    private boolean isZoneFullyClaimed(World world, Location p1, Location p2) {
        int chunkX1 = Math.min(p1.getBlockX(), p2.getBlockX()) >> 4;
        int chunkX2 = Math.max(p1.getBlockX(), p2.getBlockX()) >> 4;
        int chunkZ1 = Math.min(p1.getBlockZ(), p2.getBlockZ()) >> 4;
        int chunkZ2 = Math.max(p1.getBlockZ(), p2.getBlockZ()) >> 4;

        for (int cx = chunkX1; cx <= chunkX2; cx++) {
            for (int cz = chunkZ1; cz <= chunkZ2; cz++) {
                if (!cityManager.isChunkClaimed(world.getChunkAt(cx, cz))) {
                    return false;
                }
            }
        }
        return true;
    }

    private void showCornerParticle(Player player, Location loc, boolean isFirst) {
        World world = loc.getWorld();
        org.bukkit.Particle particle = isFirst
                ? org.bukkit.Particle.FLAME
                : org.bukkit.Particle.END_ROD;
        for (int y = world.getMinHeight(); y < world.getMaxHeight(); y += 8) {
            world.spawnParticle(particle,
                    loc.getBlockX() + 0.5, y, loc.getBlockZ() + 0.5,
                    3, 0, 0, 0, 0);
        }
    }
}