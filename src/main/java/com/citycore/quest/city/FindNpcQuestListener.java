package com.citycore.quest.city;

import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;

public class FindNpcQuestListener implements Listener {

    private final FindNpcQuestManager findNpcQuestManager;

    public FindNpcQuestListener(FindNpcQuestManager findNpcQuestManager) {
        this.findNpcQuestManager = findNpcQuestManager;
    }

    /** Bloque toute interaction avec l'item de quête. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onQuestItemInteract(PlayerInteractEvent event) {
        if (isQuestItem(event.getPlayer().getInventory().getItemInMainHand())
                || isQuestItem(event.getPlayer().getInventory().getItemInOffHand())) {
            event.setCancelled(true);
        }
    }

    /** Empêche de mettre l'item en offhand. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSwapHand(PlayerSwapHandItemsEvent event) {
        if (isQuestItem(event.getMainHandItem()) || isQuestItem(event.getOffHandItem()))
            event.setCancelled(true);
    }

    /**
     * Met à jour la BossBar à chaque changement de bloc.
     * Filtre les simples rotations de caméra.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) return;

        findNpcQuestManager.onPlayerMoved(event.getPlayer());
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        findNpcQuestManager.onPlayerJoin(event.getPlayer());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        findNpcQuestManager.onPlayerQuit(event.getPlayer());
    }

    private boolean isQuestItem(ItemStack item) {
        if (item == null || item.getType() != Material.PAPER) return false;
        if (!item.hasItemMeta() || !item.getItemMeta().hasDisplayName()) return false;
        return item.getItemMeta().getDisplayName().contains("🗺");
    }
}