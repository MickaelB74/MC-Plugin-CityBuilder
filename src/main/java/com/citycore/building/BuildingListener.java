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
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.UUID;

public class BuildingListener implements Listener {

    public static final Material SELECTION_TOOL = Material.STICK;

    private final BuildingSession session;
    private final BuildingManager buildingManager;
    private final CityManager     cityManager;

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

        // ── Phase 2 : sélection point NPC ────────────────────────
        if (session.isNpcPointPhase(uuid)) {
            if (player.getInventory().getItemInMainHand().getType()
                    != NPC_POINT_TOOL) return;
            if (event.getClickedBlock() == null) return;
            event.setCancelled(true);

            if (event.getAction() != Action.LEFT_CLICK_BLOCK) return;

            Location loc = event.getClickedBlock().getLocation();

            // Vérifie que le point est dans la zone du bâtiment
            BuildingSession.PendingBuilding pending = session.getPendingBuilding(uuid);
            int minX = Math.min(pending.x1(), pending.x2());
            int maxX = Math.max(pending.x1(), pending.x2());
            int minZ = Math.min(pending.z1(), pending.z2());
            int maxZ = Math.max(pending.z1(), pending.z2());

            if (loc.getBlockX() < minX || loc.getBlockX() > maxX
                    || loc.getBlockZ() < minZ || loc.getBlockZ() > maxZ) {
                player.sendMessage("§c❌ Le point NPC doit être dans la zone du bâtiment !");
                return;
            }

            double npcY = loc.getWorld().getHighestBlockYAt(
                    loc.getBlockX(), loc.getBlockZ()) + 1;
            Location npcPt = new Location(loc.getWorld(),
                    loc.getBlockX() + 0.5, npcY, loc.getBlockZ() + 0.5);

            // Crée le bâtiment avec le point NPC
            buildingManager.createBuilding(
                    pending.name(),
                    pending.world(),
                    pending.x1(), pending.z1(),
                    pending.x2(), pending.z2(),
                    npcPt.getX(), npcPt.getY(), npcPt.getZ()
            );

            player.sendMessage("§a✅ Bâtiment §e" + pending.name() + " §acréé !");
            player.sendMessage("§d📍 Point NPC : §fX" + loc.getBlockX()
                    + " §7Z" + loc.getBlockZ());

            showNpcPointParticle(player, loc);
            session.clear(uuid);
            player.getInventory().setItemInMainHand(null);
            return;
        }

        // ── Phase 1 : sélection zone ──────────────────────────────
        if (!session.isActive(uuid)) return;
        if (player.getInventory().getItemInMainHand().getType()
                != SELECTION_TOOL) return;
        if (event.getClickedBlock() == null) return;

        event.setCancelled(true);
        Location loc = event.getClickedBlock().getLocation();

        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            if (!cityManager.isChunkClaimed(loc.getChunk())) {
                player.sendMessage("§c❌ Ce bloc est en dehors de la ville !");
                return;
            }
            session.setPos1(uuid, loc);
            player.sendMessage("§a✔ §7Coin 1 : §fX" + loc.getBlockX()
                    + " §7Z" + loc.getBlockZ());
            showCornerParticle(player, loc, true);

        } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            if (!cityManager.isChunkClaimed(loc.getChunk())) {
                player.sendMessage("§c❌ Ce bloc est en dehors de la ville !");
                return;
            }
            session.setPos2(uuid, loc);
            player.sendMessage("§a✔ §7Coin 2 : §fX" + loc.getBlockX()
                    + " §7Z" + loc.getBlockZ());
            showCornerParticle(player, loc, false);
        }

        // Zone complète → crée le bâtiment sans point NPC + donne l'outil NPC
        if (session.isComplete(uuid)) {
            Location p1 = session.getPos1(uuid);
            Location p2 = session.getPos2(uuid);

            if (!isZoneFullyClaimed(player.getWorld(), p1, p2)) {
                player.sendMessage("§c❌ La zone dépasse les chunks claimés !");
                session.setPos1(uuid, null);
                session.setPos2(uuid, null);
                return;
            }

            if (buildingManager.overlapsExisting(
                    player.getWorld().getName(),
                    p1.getBlockX(), p1.getBlockZ(),
                    p2.getBlockX(), p2.getBlockZ())) {
                player.sendMessage("§c❌ Cette zone empiète sur un bâtiment existant !");
                session.setPos1(uuid, null);
                session.setPos2(uuid, null);
                return;
            }

            String name = session.getPendingName(uuid);
            player.sendMessage("§a✅ Zone définie pour §e" + name + "§a !");
            player.sendMessage("§7Zone : §fX(" + p1.getBlockX() + "§7→§f"
                    + p2.getBlockX() + "§7) Z(§f" + p1.getBlockZ()
                    + "§7→§f" + p2.getBlockZ() + "§7)");

            // ✅ Passe en phase 2 — stocke les données en attente
            session.startNpcPointPhase(uuid, name, player.getWorld().getName(),
                    p1.getBlockX(), p1.getBlockZ(),
                    p2.getBlockX(), p2.getBlockZ());

            // ✅ Remplace le bâton par l'outil de point NPC
            player.getInventory().setItemInMainHand(null);
            player.getInventory().addItem(makeNpcPointTool());

            player.sendMessage("§d📍 §7Clic gauche pour définir le point du NPC.");
            player.sendMessage("§8(ou /city build skip pour ignorer)");
        }
    }

    public static final Material NPC_POINT_TOOL = Material.BLAZE_ROD;

    private ItemStack makeNpcPointTool() {
        ItemStack item = new ItemStack(NPC_POINT_TOOL);
        ItemMeta meta  = item.getItemMeta();
        meta.setDisplayName("§d📍 Point NPC");
        meta.setLore(List.of(
                "§7Clic gauche §f: Définir le point du NPC",
                "§8Le NPC se rendra sur ce point",
                "§8quand il sera assigné au bâtiment."
        ));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    private void showNpcPointParticle(Player player, Location loc) {
        loc.getWorld().spawnParticle(org.bukkit.Particle.HAPPY_VILLAGER,
                loc.getBlockX() + 0.5,
                loc.getWorld().getHighestBlockYAt(loc.getBlockX(), loc.getBlockZ()) + 1,
                loc.getBlockZ() + 0.5,
                10, 0.3, 0.3, 0.3, 0);
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