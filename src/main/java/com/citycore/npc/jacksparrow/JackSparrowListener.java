package com.citycore.npc.jacksparrow;

import com.citycore.npc.CityNPC;
import com.citycore.npc.IntroductionManager;
import com.citycore.npc.NPCManager;
import com.citycore.util.TypewriterUtil;
import net.citizensnpcs.api.event.NPCRightClickEvent;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class JackSparrowListener implements Listener {

    private final NPCManager          npcManager;
    private final JackSparrowGUI      jackGUI;      // ← renommé
    private final JackSparrowConfig   config;
    private final Economy             economy;
    private final IntroductionManager introManager;
    private final JavaPlugin          plugin;

    public JackSparrowListener(NPCManager npcManager, JackSparrowGUI jackGUI,
                               JackSparrowConfig config, Economy economy,
                               IntroductionManager introManager, JavaPlugin plugin) {
        this.npcManager   = npcManager;
        this.jackGUI      = jackGUI;               // ← renommé
        this.config       = config;
        this.economy      = economy;
        this.introManager = introManager;
        this.plugin       = plugin;
    }

    @EventHandler // ← manquait
    public void onNPCRightClick(NPCRightClickEvent event) {
        if (!npcManager.isNPCType(event.getNPC(), CityNPC.JACKSPARROW)) return;
        Player player = event.getClicker();

        if (!introManager.hasSeenIntro(player.getUniqueId(), CityNPC.JACKSPARROW)) {
            introManager.markIntroSeen(player.getUniqueId(), CityNPC.JACKSPARROW);
            TypewriterUtil.play(plugin, player, CityNPC.JACKSPARROW.introLines, () -> {
                if (player.isOnline()) jackGUI.open(player);
            });
        } else {
            jackGUI.open(player);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String title = event.getView().getTitle();

        // ── Menu principal ──────────────────────────────────────
        if (JackSparrowGUI.GUI_TITLE_MAIN.equals(title)) {
            event.setCancelled(true);
            if (event.getCurrentItem() == null) return;

            switch (event.getSlot()) {
                case JackSparrowGUI.SLOT_SELL -> jackGUI.openSell(player);
                case JackSparrowGUI.SLOT_BUY  -> jackGUI.openBuy(player);
                case JackSparrowGUI.SLOT_FOLLOW -> {
                    String name = CityNPC.JACKSPARROW.displayName;
                    if (npcManager.isFollowing(player, CityNPC.JACKSPARROW)) {
                        npcManager.stopFollowing(player, CityNPC.JACKSPARROW);
                        player.sendMessage(name + " §7s'est arrêté de vous suivre.");
                    } else {
                        npcManager.startFollowing(player, CityNPC.JACKSPARROW);
                        player.sendMessage(name + " §avous suit désormais.");
                    }
                    jackGUI.open(player);
                }
            }
            return;
        }

        // ── Sous-menu vendre ────────────────────────────────────
        if (JackSparrowGUI.GUI_TITLE_SELL.equals(title)) {
            event.setCancelled(true);
            if (event.getCurrentItem() == null) return;

            if (event.getSlot() == JackSparrowGUI.SLOT_BACK) {
                jackGUI.open(player);
                return;
            }

            Material mat = event.getCurrentItem().getType();
            if (mat == Material.CYAN_STAINED_GLASS_PANE) return;
            if (!config.isBuyable(mat)) return;

            int earned = jackGUI.sellMaterial(player, mat);
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
            jackGUI.openSell(player);
            return;
        }

        // ── Sous-menu acheter ───────────────────────────────────
        if (JackSparrowGUI.GUI_TITLE_BUY.equals(title)) {
            event.setCancelled(true);
            if (event.getCurrentItem() == null) return;
            if (event.getSlot() == JackSparrowGUI.SLOT_BACK) jackGUI.open(player);
        }
    }
}