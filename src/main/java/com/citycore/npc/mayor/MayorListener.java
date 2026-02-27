package com.citycore.npc.mayor;

import com.citycore.npc.*;
import com.citycore.quest.city.CityTier;
import com.citycore.util.TypewriterUtil;
import net.citizensnpcs.api.event.NPCRightClickEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class MayorListener implements Listener {

    private final NPCManager          npcManager;
    private final NPCGuiRegistry      guiRegistry;
    private final IntroductionManager introManager;
    private final JavaPlugin          plugin;

    public MayorListener(NPCManager npcManager, NPCGuiRegistry guiRegistry,
                         IntroductionManager introManager, JavaPlugin plugin) {
        this.npcManager   = npcManager;
        this.guiRegistry  = guiRegistry;
        this.introManager = introManager;
        this.plugin       = plugin;
    }

    @EventHandler
    public void onNPCRightClick(NPCRightClickEvent event) {
        CityNPC type = npcManager.getNPCType(event.getNPC());
        if (type == null) return;

        NPCGui gui = guiRegistry.get(type);
        if (gui == null) return;

        Player player = event.getClicker();

        if (!introManager.hasSeenIntro(player.getUniqueId(), type)) {
            introManager.markIntroSeen(player.getUniqueId(), type);
            TypewriterUtil.play(plugin, player, type.getDialogue("first_meeting"),
                    () -> { if (player.isOnline()) gui.open(player); });
        } else {
            gui.open(player);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        String title = event.getView().getTitle();

        // ── Récupère le MayorGUI une seule fois ──────────────────
        MayorGUI mg = getMayorGUI();

        // ── Détection des GUIs ───────────────────────────────────
        boolean isMayorGui    = title.equals(MayorGUI.GUI_TITLE);
        boolean isBuildingGui = title.equals(MayorBuildingGUI.GUI_TITLE);
        boolean isDeleteGui   = title.equals(MayorBuildingGUI.GUI_TITLE_DELETE);
        boolean isEconomyGui  = title.equals(MayorEconomyGUI.GUI_TITLE);
        boolean isQuestGui = mg != null && title.equals(
                MayorQuestGUI.title(
                        CityTier.fromLevel(mg.getCityQuestManager().getCityLevel())));

        if (!isMayorGui && !isBuildingGui && !isDeleteGui
                && !isEconomyGui && !isQuestGui) return;

        event.setCancelled(true);
        if (event.getCurrentItem() == null) return;

        int slot = event.getSlot();

        // ── Sous-menu quêtes ──────────────────────────────────────
        if (isQuestGui) {
            if (mg == null) return;
            boolean handled = mg.getQuestGUI().handleClick(player, slot);
            if (!handled) mg.open(player);
            return;
        }

        // ── Sous-menu bâtiments (liste) ───────────────────────────
        if (isBuildingGui) {
            if (mg == null) return;
            boolean handled = mg.getBuildingGUI().handleMainClick(player, slot);
            if (!handled) mg.open(player);
            return;
        }

        // ── Sous-menu bâtiments (suppression) ────────────────────
        if (isDeleteGui) {
            if (mg == null) return;
            mg.getBuildingGUI().handleDeleteClick(player, slot);
            return;
        }

        // ── Sous-menu économie ────────────────────────────────────
        if (isEconomyGui) {
            if (mg == null) return;
            boolean handled = mg.getEconomyGUI().handleClick(player, slot);
            if (!handled) mg.open(player);
            return;
        }

        // ── Menu principal maire ──────────────────────────────────
        NPCGui gui = guiRegistry.getByTitle(title);
        if (gui == null) return;
        gui.handleClick(player, slot);
    }

    private MayorGUI getMayorGUI() {
        NPCGui gui = guiRegistry.getByTitle(MayorGUI.GUI_TITLE);
        return gui instanceof MayorGUI mg ? mg : null;
    }
}