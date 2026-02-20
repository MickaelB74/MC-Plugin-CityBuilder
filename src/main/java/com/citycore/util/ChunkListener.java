package com.citycore.util;

import com.citycore.city.City;
import com.citycore.city.CityManager;
import com.citycore.npc.NPCManager;
import org.bukkit.Chunk;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

public class ChunkListener implements Listener {

    private final CityManager cityManager;
    private final NPCManager npcManager;

    public ChunkListener(CityManager cityManager, NPCManager npcManager) {
        this.cityManager = cityManager;
        this.npcManager = npcManager;
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (event.getFrom().getChunk().equals(event.getTo().getChunk())) return;

        Player player = event.getPlayer();
        Chunk from = event.getFrom().getChunk();
        Chunk to   = event.getTo().getChunk();

        boolean wasInCity = cityManager.isChunkClaimed(from);
        boolean isInCity  = cityManager.isChunkClaimed(to);

        // Entrée dans la ville
        if (!wasInCity && isInCity) {
            City city = cityManager.getCity();
            String name = (city != null) ? city.getName() : "la Ville";
            player.sendActionBar("§a🏰 Territoire de §e" + name);
        }

        // Sortie de la ville alors qu'Alderic suit le joueur
        if (wasInCity && !isInCity && npcManager.isFollowing(player)) {
            npcManager.stopFollowing(player);
            player.sendMessage("§6Alderic §7: §o\"Je vous attends là, je dois surveiller la ville...\"");
            player.sendActionBar("§c🌲 Alderic ne peut pas quitter la ville.");
        } else if (wasInCity && !isInCity) {
            player.sendActionBar("§c🌲 Quitter le territoire de la ville");
        }
    }
}