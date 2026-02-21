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
import java.util.HashMap;
import java.util.Map;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class NPCManager {

    private final JavaPlugin plugin;

    public NPCManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * À appeler dans onEnable — réattache la logique aux NPCs
     * déjà spawné par Citizens au redémarrage du serveur.
     */
    public void restoreNPCs() {
        int total = 0;
        int restored = 0;

        for (NPC npc : CitizensAPI.getNPCRegistry()) {
            total++;
            plugin.getLogger().info("NPC trouvé : id=" + npc.getId()
                    + " name=" + npc.getName()
                    + " hasMayorTag=" + npc.data().has(CityNPC.MAYOR.tag)
                    + " hasMasonTag=" + npc.data().has(CityNPC.STONEMASON.tag));

            CityNPC type = getNPCType(npc);
            if (type == null) {
                plugin.getLogger().info("  → Pas un NPC CityCore, ignoré.");
                continue;
            }

            if (!npc.isSpawned() && npc.getStoredLocation() != null) {
                npc.spawn(npc.getStoredLocation());
            }

            plugin.getLogger().info("  → Restauré : " + type.displayName);
            restored++;
        }

        plugin.getLogger().info("Bilan : " + total + " NPC(s) Citizens, "
                + restored + " restauré(s).");
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
        npc.data().setPersistent(type.tag, true);

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
   MODE SUIVI (générique)
   ========================= */

    private final Map<CityNPC, Set<UUID>> followingPlayers = new HashMap<>();

    public void startFollowing(Player player, CityNPC type) {
        NPC npc = getNPC(type);
        if (npc == null || !npc.isSpawned()) return;

        npc.getNavigator().getDefaultParameters()
                .range(50f)
                .speedModifier(0.8f)
                .distanceMargin(2.0);

        npc.getNavigator().setTarget(player, false);
        followingPlayers
                .computeIfAbsent(type, k -> new HashSet<>())
                .add(player.getUniqueId());
    }

    public void stopFollowing(Player player, CityNPC type) {
        NPC npc = getNPC(type);
        if (npc != null && npc.isSpawned()) {
            npc.getNavigator().cancelNavigation();
        }
        if (followingPlayers.containsKey(type)) {
            followingPlayers.get(type).remove(player.getUniqueId());
        }
    }

    public boolean isFollowing(Player player, CityNPC type) {
        return followingPlayers.containsKey(type)
                && followingPlayers.get(type).contains(player.getUniqueId());
    }
}