package com.citycore.npc;

import com.citycore.util.DatabaseManager;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class NPCNotificationManager {

    private final DatabaseManager db;

    public NPCNotificationManager(DatabaseManager db) {
        this.db = db;
    }

    // Dans DatabaseManager — nouvelle table
    // CREATE TABLE IF NOT EXISTS npc_notifications (
    //     player_uuid TEXT NOT NULL,
    //     npc_tag     TEXT NOT NULL,
    //     has_notif   INTEGER NOT NULL DEFAULT 0,
    //     PRIMARY KEY (player_uuid, npc_tag)
    // )

    public boolean hasNotification(UUID playerUUID, CityNPC npc) {
        try {
            PreparedStatement ps = db.getConnection().prepareStatement("""
                SELECT has_notif FROM npc_notifications
                WHERE player_uuid = ? AND npc_tag = ?
            """);
            ps.setString(1, playerUUID.toString());
            ps.setString(2, npc.tag);
            ResultSet rs = ps.executeQuery();
            boolean has = rs.next() && rs.getInt("has_notif") == 1;
            ps.close();
            return has;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public void setNotification(UUID playerUUID, CityNPC npc, boolean value) {
        try {
            PreparedStatement ps = db.getConnection().prepareStatement("""
                INSERT INTO npc_notifications (player_uuid, npc_tag, has_notif)
                VALUES (?, ?, ?)
                ON CONFLICT(player_uuid, npc_tag)
                DO UPDATE SET has_notif = excluded.has_notif
            """);
            ps.setString(1, playerUUID.toString());
            ps.setString(2, npc.tag);
            ps.setInt(3, value ? 1 : 0);
            ps.executeUpdate();
            ps.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Notifie tous les joueurs en ligne pour un NPC donné.
     */
    public void notifyAll(CityNPC npc,
                          org.bukkit.Server server,
                          NPCManager npcManager) {
        server.getOnlinePlayers().forEach(p ->
                setNotification(p.getUniqueId(), npc, true));
        updateHologram(npc, npcManager, true);
    }

    /**
     * Retire la notification pour un joueur et met à jour
     * le hologramme si plus personne n'a de notif.
     */
    public void clearNotification(UUID playerUUID, CityNPC npc,
                                  org.bukkit.Server server,
                                  NPCManager npcManager) {
        setNotification(playerUUID, npc, false);

        // Vérifie si un joueur en ligne a encore une notif
        boolean anyStillHas = server.getOnlinePlayers().stream()
                .anyMatch(p -> hasNotification(p.getUniqueId(), npc));

        if (!anyStillHas) {
            updateHologram(npc, npcManager, false);
        }
    }

    /**
     * Met à jour le hologramme du NPC avec ou sans le "!".
     */
    public void updateHologram(CityNPC npc, NPCManager npcManager,
                               boolean showNotif) {
        net.citizensnpcs.api.npc.NPC citizensNPC = npcManager.getNPC(npc);
        if (citizensNPC == null || !citizensNPC.isSpawned()) return;

        net.citizensnpcs.trait.HologramTrait hologram =
                citizensNPC.getOrAddTrait(
                        net.citizensnpcs.trait.HologramTrait.class);

        // Reconstruit les lignes
        hologram.clear();
        hologram.addLine(npc.hologramLine());
        if (showNotif) {
            hologram.addLine("§c§l!");
        }

    }

    public void setBlocked(CityNPC npc, NPCManager npcManager, boolean blocked) {
        net.citizensnpcs.api.npc.NPC citizensNPC = npcManager.getNPC(npc);
        if (citizensNPC == null || !citizensNPC.isSpawned()) return;

        net.citizensnpcs.trait.HologramTrait hologram =
                citizensNPC.getOrAddTrait(
                        net.citizensnpcs.trait.HologramTrait.class);

        hologram.clear();
        hologram.addLine(npc.hologramLine());
        if (blocked) {
            hologram.addLine("§e§l/!\\"); // ✅ Triangle d'avertissement
        } else {
            // Vérifie si une notif normale est active
            boolean anyHas = npcManager.getNPC(npc) != null &&
                    citizensNPC.getEntity().getWorld().getPlayers().stream()
                            .anyMatch(p -> hasNotification(p.getUniqueId(), npc));
            if (anyHas) hologram.addLine("§c§l !");
        }
    }

    /**
     * Recalcule le hologramme au login d'un joueur.
     */
    public void refreshOnLogin(UUID playerUUID, CityNPC npc,
                               NPCManager npcManager,
                               org.bukkit.Server server) {
        boolean anyHas = server.getOnlinePlayers().stream()
                .anyMatch(p -> hasNotification(p.getUniqueId(), npc));
        updateHologram(npc, npcManager, anyHas || hasNotification(playerUUID, npc));
    }
}