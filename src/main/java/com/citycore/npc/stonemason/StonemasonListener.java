package com.citycore.npc.stonemason;

import com.citycore.npc.CityNPC;
import com.citycore.npc.NPCManager;
import net.citizensnpcs.api.event.NPCRightClickEvent;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

public class StonemasonListener implements Listener {

    private final NPCManager       npcManager;
    private final StonemasonGUI    stonemasonGUI;
    private final StonemasonConfig config;
    private final Economy          economy;

    public StonemasonListener(NPCManager npcManager, StonemasonGUI stonemasonGUI,
                              StonemasonConfig config, Economy economy) {
        this.npcManager    = npcManager;
        this.stonemasonGUI = stonemasonGUI;
        this.config        = config;
        this.economy       = economy;
    }

    @EventHandler
    public void onNPCRightClick(NPCRightClickEvent event) {
        if (!npcManager.isNPCType(event.getNPC(), CityNPC.STONEMASON)) return;
        stonemasonGUI.open(event.getClicker());
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String title = event.getView().getTitle();

        // ── Menu principal ──────────────────────────────────────
        if (StonemasonGUI.GUI_TITLE_MAIN.equals(title)) {
            event.setCancelled(true);
            if (event.getCurrentItem() == null) return;

            switch (event.getSlot()) {
                case StonemasonGUI.SLOT_SELL -> stonemasonGUI.openSell(player);

                case StonemasonGUI.SLOT_BUY  -> stonemasonGUI.openBuy(player);

                case StonemasonGUI.SLOT_FOLLOW -> {
                    String name = CityNPC.STONEMASON.displayName;
                    if (npcManager.isFollowingMason(player)) {
                        npcManager.stopFollowingMason(player);
                        player.sendMessage(name + " §7s'est arrêté de vous suivre.");
                    } else {
                        npcManager.startFollowingMason(player);
                        player.sendMessage(name + " §avous suit désormais.");
                    }
                    stonemasonGUI.open(player); // Rafraîchit le bouton toggle
                }
            }
            return;
        }

        // ── Sous-menu vendre ────────────────────────────────────
        if (StonemasonGUI.GUI_TITLE_SELL.equals(title)) {
            event.setCancelled(true);
            if (event.getCurrentItem() == null) return;

            // Retour au menu principal
            if (event.getSlot() == StonemasonGUI.SLOT_BACK) {
                stonemasonGUI.open(player);
                return;
            }

            Material mat = event.getCurrentItem().getType();
            if (mat == Material.GRAY_STAINED_GLASS_PANE) return;
            if (!config.isBuyable(mat)) return;

            int earned = stonemasonGUI.sellMaterial(player, mat);
            if (earned == -1) {
                player.sendMessage("§c❌ Vous n'avez pas de stack complet de §f"
                        + event.getCurrentItem().getItemMeta().getDisplayName() + "§c.");
            } else {
                economy.depositPlayer(player, earned);
                player.sendMessage("§a✅ Vendu ! §6+" + earned
                        + " coins §adéposés dans votre poche.");
                player.sendMessage("§7Solde : §6"
                        + (int) economy.getBalance(player) + " coins");
            }

            // Rafraîchit le sous-menu avec les nouveaux stocks
            stonemasonGUI.openSell(player);
            return;
        }

        // ── Sous-menu acheter ───────────────────────────────────
        if (StonemasonGUI.GUI_TITLE_BUY.equals(title)) {
            event.setCancelled(true);
            if (event.getCurrentItem() == null) return;

            if (event.getSlot() == StonemasonGUI.SLOT_BACK) {
                stonemasonGUI.open(player);
            }
        }
    }
}