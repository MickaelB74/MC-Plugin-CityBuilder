package com.citycore.npc.mayor;

import com.citycore.npc.*;
import com.citycore.util.TypewriterUtil;
import net.citizensnpcs.api.event.NPCRightClickEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class MayorListener implements Listener {

    private final NPCManager npcManager;
    private final NPCGuiRegistry guiRegistry;
    private final IntroductionManager introManager;
    private final JavaPlugin plugin;

    public MayorListener(NPCManager npcManager, NPCGuiRegistry guiRegistry, IntroductionManager introManager,
                         JavaPlugin plugin) {
        this.npcManager  = npcManager;
        this.guiRegistry = guiRegistry;
        this.introManager  = introManager;
        this.plugin        = plugin;
    }

    @EventHandler
    public void onNPCRightClick(NPCRightClickEvent event) {
        CityNPC type = npcManager.getNPCType(event.getNPC());
        if (type == null) return;

        NPCGui gui = guiRegistry.get(type);
        if (gui == null) return;

        Player player = event.getClicker();

        if (!introManager.hasSeenIntro(player.getUniqueId(), type)) {
            // Première rencontre — joue l'intro puis ouvre le GUI
            introManager.markIntroSeen(player.getUniqueId(), type);
            TypewriterUtil.play(plugin, player, type.introLines, () -> {
                if (player.isOnline()) gui.open(player);
            });
        } else {
            // Déjà vu — ouvre directement le GUI
            gui.open(player);
        }
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