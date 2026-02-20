package com.citycore.npc;

import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.npc.NPCRegistry;
import net.citizensnpcs.trait.HologramTrait;
import net.citizensnpcs.trait.SkinTrait;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class NPCManager {

    private final JavaPlugin plugin;
    private final Set<UUID> followingPlayers = new HashSet<>();

    public NPCManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /* =========================
       SPAWN GÉNÉRIQUE
       ========================= */

    /**
     * Méthode générique — peut spawner n'importe quel CityNPC.
     * Exemple futur : spawnNPC(CityNPC.BLACKSMITH, player)
     */
    public NPC spawnNPC(CityNPC type, Location location) {
        NPCRegistry registry = CitizensAPI.getNPCRegistry();
        NPC npc = registry.createNPC(EntityType.PLAYER, type.displayName);
        npc.data().set(type.tag, true);

        // Skin AVANT spawn
        SkinTrait skinTrait = npc.getOrAddTrait(SkinTrait.class);
        skinTrait.setSkinPersistent(type.skinId, type.skinSignature, type.skinValue);

        // Hologramme
        HologramTrait hologram = npc.getOrAddTrait(HologramTrait.class);
        hologram.addLine(type.hologramLine());

        npc.spawn(location);

        // Refresh SkinsRestorer pour les clients crackés
        Bukkit.getScheduler().runTaskLater(plugin, () ->
                forceSkinsRestorerRefresh(npc, type), 20L);

        return npc;
    }

    /* =========================
       SPAWN MAIRE (helper)
       ========================= */

    /**
     * Spawne le maire 2 blocs devant le joueur, face à lui.
     */
    public NPC spawnMayor(Player player) {
        Location loc = player.getLocation().clone();
        loc.add(loc.getDirection().normalize().multiply(2));
        loc.setY(Math.floor(loc.getY() + 1));

        Location mayorLoc = loc.clone();
        mayorLoc.setYaw((player.getLocation().getYaw() + 180) % 360);
        mayorLoc.setPitch(0);

        return spawnNPC(CityNPC.MAYOR, mayorLoc);
    }

    /* =========================
       FIND / CHECK / REMOVE
       ========================= */

    public NPC getNPC(CityNPC type) {
        for (NPC npc : CitizensAPI.getNPCRegistry()) {
            if (npc.data().has(type.tag)) return npc;
        }
        return null;
    }

    public boolean isNPCType(NPC npc, CityNPC type) {
        return npc.data().has(type.tag);
    }

    /**
     * Retrouve le type d'un NPC Citizens depuis ses données.
     * Utile dans les listeners pour identifier qui on clique.
     */
    public CityNPC getNPCType(NPC npc) {
        for (CityNPC type : CityNPC.values()) {
            if (npc.data().has(type.tag)) return type;
        }
        return null;
    }

    public void removeNPC(CityNPC type) {
        NPC npc = getNPC(type);
        if (npc != null) npc.destroy();
    }

    /* =========================
       SKINSRESTORER REFRESH
       ========================= */

    private void forceSkinsRestorerRefresh(NPC npc, CityNPC type) {
        if (!npc.isSpawned()) return;
        if (Bukkit.getPluginManager().getPlugin("SkinsRestorer") == null) return;

        try {
            Bukkit.dispatchCommand(
                    Bukkit.getConsoleSender(),
                    "sr set " + npc.getEntity().getName() + " " + type.skinId
            );
            plugin.getLogger().info("✅ Skin " + type.skinId + " appliqué via SkinsRestorer.");
        } catch (Exception e) {
            plugin.getLogger().warning("⚠ SkinsRestorer refresh échoué : " + e.getMessage());
        }
    }

    /* =========================
       MODE SUIVI (maire)
       ========================= */

    public void startFollowing(Player player) {
        NPC mayor = getNPC(CityNPC.MAYOR);
        if (mayor == null || !mayor.isSpawned()) return;

        mayor.getNavigator().getDefaultParameters()
                .range(50f)
                .speedModifier(0.8f)
                .distanceMargin(2.0);

        mayor.getNavigator().setTarget(player, false);
        followingPlayers.add(player.getUniqueId());
    }

    public void stopFollowing(Player player) {
        NPC mayor = getNPC(CityNPC.MAYOR);
        if (mayor != null && mayor.isSpawned()) {
            mayor.getNavigator().cancelNavigation();
        }
        followingPlayers.remove(player.getUniqueId());
    }

    public boolean isFollowing(Player player) {
        return followingPlayers.contains(player.getUniqueId());
    }
}