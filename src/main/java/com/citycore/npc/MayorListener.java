package com.citycore.npc;

import com.citycore.city.CityManager;
import com.citycore.npc.GUI.MayorGUI;
import net.citizensnpcs.api.event.NPCRightClickEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

public class MayorListener implements Listener {

    private final NPCManager  npcManager;
    private final MayorGUI mayorGUI;
    private final CityManager cityManager;

    public MayorListener(NPCManager npcManager, MayorGUI mayorGUI, CityManager cityManager) {
        this.npcManager  = npcManager;
        this.mayorGUI    = mayorGUI;
        this.cityManager = cityManager;
    }

    @EventHandler
    public void onNPCRightClick(NPCRightClickEvent event) {
        CityNPC type = npcManager.getNPCType(event.getNPC());
        if (type == null) return;

        switch (type) {
            case MAYOR -> mayorGUI.open(event.getClicker());
            // Futurs NPCs :
            // case BLACKSMITH -> blacksmithGUI.open(event.getClicker());
            // case MERCHANT   -> merchantGUI.open(event.getClicker());
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!MayorGUI.GUI_TITLE.equals(event.getView().getTitle())) return;
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getCurrentItem() == null) return;

        switch (event.getSlot()) {

            case MayorGUI.SLOT_INFO -> {
                player.closeInventory();
                com.citycore.city.City city = cityManager.getCity();
                if (city == null) return;
                player.sendMessage("§8§m--------------------");
                player.sendMessage("§6 " + CityNPC.MAYOR.displayName + " §8— §e" + city.getName());
                player.sendMessage("§8§m--------------------");
                player.sendMessage("§eNiveau  : §f" + city.getLevel());
                player.sendMessage("§eCaisse  : §6" + city.getCoins() + " coins");
                player.sendMessage("§eChunks  : §f" + city.getClaimedChunks() + " §7/ §f" + city.getMaxChunks());
                player.sendMessage("§eExpand  : §6" + cityManager.getNextExpandPrice() + " coins §7pour +1 slot");
                player.sendMessage("§8§m--------------------");
            }

            case MayorGUI.SLOT_FOLLOW -> {
                player.closeInventory();
                String name = CityNPC.MAYOR.displayName;
                if (npcManager.isFollowing(player)) {
                    npcManager.stopFollowing(player);
                    player.sendMessage(name + " §7s'est arrêté de vous suivre.");
                } else {
                    npcManager.startFollowing(player);
                    player.sendMessage(name + " §avous suit désormais.");
                }
                mayorGUI.open(player);
            }

            case MayorGUI.SLOT_EXPAND -> {
                player.closeInventory();
                int price = cityManager.getNextExpandPrice();
                CityManager.ExpandResult result = cityManager.expandMaxChunks();
                if (result.success()) {
                    player.sendMessage("§a✅ Capacité étendue ! Max chunks : §f" + result.newMaxChunks());
                    player.sendMessage("§7Caisse restante : §6" + result.newBalance() + " coins");
                } else {
                    int missing = price - cityManager.getCityCoins();
                    player.sendMessage("§c❌ Fonds insuffisants. Il manque §f" + missing + " coins§c.");
                    player.sendMessage("§7💡 §e/city deposit <montant> §7pour alimenter la caisse.");
                }
            }
        }
    }
}