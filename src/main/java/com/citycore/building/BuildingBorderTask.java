package com.citycore.building;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.session.SessionManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class BuildingBorderTask {

    private final JavaPlugin      plugin;
    private final BuildingManager buildingManager;

    private final Map<UUID, BukkitTask> activeTasks = new HashMap<>();

    public BuildingBorderTask(JavaPlugin plugin, BuildingManager buildingManager) {
        this.plugin          = plugin;
        this.buildingManager = buildingManager;
    }

    public boolean isShowing(Player player) {
        return activeTasks.containsKey(player.getUniqueId());
    }

    public void toggle(Player player) {
        UUID uuid = player.getUniqueId();

        if (activeTasks.containsKey(uuid)) {
            // ── Arrête l'affichage ────────────────────────────────
            activeTasks.get(uuid).cancel();
            activeTasks.remove(uuid);
            clearCUI(player);
            player.sendMessage("§7🏛 Bordures des bâtiments §7masquées.");

        } else {
            // ── Démarre l'affichage ───────────────────────────────
            player.sendMessage("§a🏛 Bordures des bâtiments §aaffichées.");
            player.sendMessage("§7Refaites §f/city build show §7pour masquer.");

            // Affiche immédiatement
            renderAllBuildings(player);

            // Rafraîchit toutes les 3 secondes
            // (nécessaire car le CUI se reset si le joueur bouge loin)
            BukkitTask task = new BukkitRunnable() {
                @Override
                public void run() {
                    if (!player.isOnline()) {
                        cancel();
                        activeTasks.remove(uuid);
                        return;
                    }
                    renderAllBuildings(player);
                }
            }.runTaskTimer(plugin, 60L, 60L);

            activeTasks.put(uuid, task);
        }
    }

    /**
     * Envoie les sélections CUI de tous les bâtiments au joueur.
     * WorldEdit CUI supporte plusieurs régions via des packets répétés.
     */
    private void renderAllBuildings(Player player) {
        List<Building> buildings = buildingManager.getAllBuildings();
        if (buildings.isEmpty()) return;

        try {
            com.sk89q.worldedit.entity.Player wePlayer =
                    BukkitAdapter.adapt(player);
            SessionManager sessionManager =
                    WorldEdit.getInstance().getSessionManager();
            LocalSession session = sessionManager.get(wePlayer);

            for (int i = 0; i < buildings.size(); i++) {
                Building b = buildings.get(i);
                if (!b.world().equals(player.getWorld().getName())) continue;

                int groundY = player.getWorld().getHighestBlockYAt(
                        (b.x1() + b.x2()) / 2,
                        (b.z1() + b.z2()) / 2);

                // ✅ Même Y pour pos1 et pos2 — surface d'un seul bloc
                BlockVector3 pos1 = BlockVector3.at(b.x1(), groundY, b.z1());
                BlockVector3 pos2 = BlockVector3.at(b.x2(), groundY, b.z2());

                com.sk89q.worldedit.world.World weWorld =
                        BukkitAdapter.adapt(player.getWorld());

                if (i == 0) {
                    session.setRegionSelector(weWorld,
                            new com.sk89q.worldedit.regions.selector
                                    .CuboidRegionSelector(weWorld, pos1, pos2));
                    session.dispatchCUIEvent(wePlayer,
                            new com.sk89q.worldedit.internal.cui
                                    .SelectionShapeEvent("cuboid"));
                    session.dispatchCUIEvent(wePlayer,
                            new com.sk89q.worldedit.internal.cui
                                    .SelectionPointEvent(0, pos1, 0));
                    session.dispatchCUIEvent(wePlayer,
                            new com.sk89q.worldedit.internal.cui
                                    .SelectionPointEvent(1, pos2, 0));
                } else {
                    sendRawCUIPacket(player, "s|cuboid");
                    sendRawCUIPacket(player, "p|0|" + b.x1() + "|"
                            + groundY + "|" + b.z1() + "|0");
                    sendRawCUIPacket(player, "p|1|" + b.x2() + "|"
                            + groundY + "|" + b.z2() + "|0");
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Erreur CUI WorldEdit : " + e.getMessage());
        }
    }

    /**
     * Efface la sélection CUI du joueur.
     */
    private void clearCUI(Player player) {
        try {
            com.sk89q.worldedit.entity.Player wePlayer =
                    BukkitAdapter.adapt(player);
            SessionManager sessionManager =
                    WorldEdit.getInstance().getSessionManager();
            LocalSession session = sessionManager.get(wePlayer);
            session.getRegionSelector(
                    BukkitAdapter.adapt(player.getWorld())).clear();
            session.dispatchCUIEvent(wePlayer,
                    new com.sk89q.worldedit.internal.cui
                            .SelectionShapeEvent("cuboid"));
        } catch (Exception e) {
            plugin.getLogger().warning(
                    "Erreur clear CUI : " + e.getMessage());
        }
    }

    /**
     * Envoie un packet CUI brut via le channel plugin WorldEdit.
     */
    private void sendRawCUIPacket(Player player, String data) {
        player.sendPluginMessage(plugin,
                "worldedit:cui",
                data.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}