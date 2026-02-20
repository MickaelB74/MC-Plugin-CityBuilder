package com.citycore.npc;

import net.citizensnpcs.api.event.NPCRightClickEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

public class MayorListener implements Listener {

    private final NPCManager      npcManager;
    private final NPCGuiRegistry  guiRegistry;

    public MayorListener(NPCManager npcManager, NPCGuiRegistry guiRegistry) {
        this.npcManager  = npcManager;
        this.guiRegistry = guiRegistry;
    }

    @EventHandler
    public void onNPCRightClick(NPCRightClickEvent event) {
        CityNPC type = npcManager.getNPCType(event.getNPC());
        if (type == null) return;

        NPCGui gui = guiRegistry.get(type);
        if (gui == null) return;

        gui.open(event.getClicker());
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        NPCGui gui = guiRegistry.getByTitle(event.getView().getTitle());
        if (gui == null) return;

        event.setCancelled(true);
        if (event.getCurrentItem() == null) return;

        gui.handleClick(player, event.getSlot());
    }
}