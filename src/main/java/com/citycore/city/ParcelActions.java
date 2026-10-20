package com.citycore.city;

import com.citycore.building.BuildingManager;
import com.citycore.chunk.ChunkParticleTask;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Parcelles de la ville (chunks revendiqués) : revendiquer ou rendre la parcelle où se tient le
 * joueur, voir les limites de la ville. Partagé par le menu d'Alderic (barre « Parcelles ») et
 * par les commandes d'administration /city claim, unclaim et map.
 */
public final class ParcelActions {

    private ParcelActions() {}

    /** Revendique la parcelle où se tient le joueur ; true si c'est fait (sinon il est prévenu). */
    public static boolean claimHere(CityManager city, Player player) {
        if (!city.isCityInitialized()) {
            player.sendMessage("§c❌ Aucune ville fondée.");
            return false;
        }
        Chunk chunk = player.getLocation().getChunk();
        if (city.isChunkClaimed(chunk)) {
            player.sendMessage("§c❌ Cette parcelle appartient déjà à la ville.");
            return false;
        }
        if (!city.isAdjacentToClaimed(chunk)) {
            player.sendMessage("§c❌ Cette parcelle doit toucher une parcelle de la ville.");
            return false;
        }
        if (!city.canClaimChunk()) {
            player.sendMessage("§c❌ Plus d'emplacement libre §7(§f" + city.getClaimedChunkCount()
                    + "§7/§f" + city.getMaxChunks() + "§7).");
            player.sendMessage("§7💡 Alderic peut §eagrandir la ville §7: +1 emplacement pour §6"
                    + city.getNextExpandPrice() + " coins §7(caisse).");
            return false;
        }
        city.claimChunk(chunk);
        player.sendMessage("§a✅ Parcelle revendiquée ! §7(§f" + city.getClaimedChunkCount()
                + "§7/§f" + city.getMaxChunks() + "§7)");
        return true;
    }

    /**
     * Rend la parcelle où se tient le joueur ; refusé pour la dernière parcelle et pour une
     * parcelle où se trouve (une partie d') un bâtiment, qui sortirait sinon de la ville.
     */
    public static boolean unclaimHere(CityManager city, BuildingManager buildings, Player player) {
        if (!city.isCityInitialized()) {
            player.sendMessage("§c❌ Aucune ville fondée.");
            return false;
        }
        Chunk chunk = player.getLocation().getChunk();
        if (!city.isChunkClaimed(chunk)) {
            player.sendMessage("§c❌ Cette parcelle n'appartient pas à la ville.");
            return false;
        }
        if (city.getClaimedChunkCount() <= 1) {
            player.sendMessage("§c❌ Impossible de rendre la dernière parcelle.");
            return false;
        }
        if (buildings != null && hasBuilding(buildings, chunk)) {
            player.sendMessage("§c❌ Un bâtiment de la ville occupe cette parcelle : supprimez-le d'abord"
                    + " §7(Alderic, « Bâtiments »)§c.");
            return false;
        }
        city.unclaimChunk(chunk);
        player.sendMessage("§e🗑 Parcelle rendue. §7(§f" + city.getClaimedChunkCount()
                + "§7/§f" + city.getMaxChunks() + "§7)");
        return true;
    }

    /** Limites de la ville en particules dorées pendant 5 secondes (menu fermé au tick suivant). */
    public static void showBorders(JavaPlugin plugin, CityManager city, Player player) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!city.isCityInitialized()) {
                player.sendMessage("§c❌ Aucune ville fondée.");
                return;
            }
            player.closeInventory();
            new ChunkParticleTask(plugin, city, player).runForSeconds(5);
            player.sendMessage("§b🗺 Limites de la ville affichées pendant §f5 secondes§b.");
        });
    }

    /** Une ligne de bulle : la parcelle où se tient le joueur (à la ville, libre et voisine…). */
    public static String statusHere(CityManager city, Player player) {
        Chunk chunk = player.getLocation().getChunk();
        if (city.isChunkClaimed(chunk)) return "§7Ici : §aparcelle de la ville";
        if (!city.isAdjacentToClaimed(chunk)) return "§7Ici : §cparcelle trop éloignée de la ville";
        return city.canClaimChunk() ? "§7Ici : §eparcelle libre, voisine de la ville"
                : "§7Ici : §6parcelle voisine §7(plus d'emplacement libre)";
    }

    private static boolean hasBuilding(BuildingManager buildings, Chunk chunk) {
        int x = chunk.getX() << 4, z = chunk.getZ() << 4;
        return buildings.overlapsExisting(chunk.getWorld().getName(), x, z, x + 15, z + 15);
    }
}
