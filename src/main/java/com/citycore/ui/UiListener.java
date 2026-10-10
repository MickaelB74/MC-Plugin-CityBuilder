package com.citycore.ui;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Clics des menus « Valmont » : tout est bloqué (aucun objet ne peut être pris ni déposé),
 * puis l'action de la case cliquée s'exécute au tick suivant (ouvrir un autre inventaire
 * pendant l'événement de clic peut désynchroniser le client).
 */
public final class UiListener implements Listener {

    private final JavaPlugin plugin;

    public UiListener(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof UiMenu menu)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;
        if (event.getClick() == ClickType.DOUBLE_CLICK) return; // le 1er clic a déjà agi
        int slot = event.getRawSlot();
        ClickType type = event.getClick();
        Bukkit.getScheduler().runTask(plugin, () -> {
            // Menu fermé entre-temps : on n'agit plus
            if (player.getOpenInventory().getTopInventory().getHolder() != menu) return;
            menu.click(slot, type);
        });
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof UiMenu) event.setCancelled(true);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof UiMenu menu) menu.onClose();
    }
}
