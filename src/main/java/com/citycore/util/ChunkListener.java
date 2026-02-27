package com.citycore.util;

import com.citycore.CityCoreHUD;
import com.citycore.city.City;
import com.citycore.city.CityManager;
import com.citycore.npc.CityNPC;
import com.citycore.npc.NPCManager;
import com.citycore.quest.city.FindNpcQuestManager;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class ChunkListener implements Listener {

    private final CityManager cityManager;
    private final NPCManager  npcManager;
    private final CityCoreHUD cityHUD;
    private final JavaPlugin  plugin;
    private final FindNpcQuestManager findNpcQuestManager;


    public ChunkListener(CityManager cityManager, NPCManager npcManager,
                         CityCoreHUD cityHUD, JavaPlugin plugin, FindNpcQuestManager findNpcQuestManager) {
        this.cityManager = cityManager;
        this.npcManager  = npcManager;
        this.cityHUD     = cityHUD;
        this.plugin      = plugin;
        this.findNpcQuestManager = findNpcQuestManager;
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
        if (wasInCity && !isInCity && npcManager.isFollowing(player, CityNPC.MAYOR)) {
            npcManager.stopFollowing(player, CityNPC.MAYOR);
            player.sendMessage("§6" + CityNPC.MAYOR.displayName + " §7: §o\"Je vous attends là, je dois surveiller la ville...\"");
            player.sendActionBar("§c🌲 " + CityNPC.MAYOR.displayName + " ne peut pas quitter la ville.");
        } else if (wasInCity && !isInCity) {
            player.sendActionBar("§c🌲 Quitter le territoire de la ville");
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            cityHUD.updatePlayer(e.getPlayer());
            findNpcQuestManager.onPlayerJoin(e.getPlayer()); // ✅
        }, 20L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        cityHUD.clearPlayer(e.getPlayer());
    }
}