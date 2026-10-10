package com.citycore.command;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

/**
 * Listener dédié au journal de quêtes classique (/city quests) : onglets « Suivi » et « Récits ».
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
        String title = event.getView().getTitle();
        boolean tracking = title.equals(CityQuestSelectionGUI.GUI_TITLE);
        if (!tracking && !title.equals(CityQuestSelectionGUI.STORIES_TITLE)) return;

        event.setCancelled(true);
        if (event.getCurrentItem() == null) return;
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;

        if (tracking) gui.handleClick(player, event.getCurrentItem());
        else gui.handleStoriesClick(player, event.getCurrentItem());
    }
}
