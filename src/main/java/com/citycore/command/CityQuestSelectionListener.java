package com.citycore.command;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

/**
 * Listener dédié au GUI de sélection des quêtes (/city quests).
 * À enregistrer dans CityCore.onEnable().
 */
public class CityQuestSelectionListener implements Listener {

    private final CityQuestSelectionGUI gui;

    public CityQuestSelectionListener(CityQuestSelectionGUI gui) {
        this.gui = gui;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getView().getTitle().equals(CityQuestSelectionGUI.GUI_TITLE)) return;

        event.setCancelled(true);
        if (event.getCurrentItem() == null) return;

        gui.handleClick(player, event.getCurrentItem());
    }
}