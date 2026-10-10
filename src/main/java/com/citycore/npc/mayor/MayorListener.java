package com.citycore.npc.mayor;

import com.citycore.city.CityManager;
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
    private final CityManager         cityManager;

    public MayorListener(NPCManager npcManager, NPCGuiRegistry guiRegistry,
                         IntroductionManager introManager, CityManager cityManager,
                         JavaPlugin plugin) {
        this.npcManager   = npcManager;
        this.guiRegistry  = guiRegistry;
        this.introManager = introManager;
        this.cityManager  = cityManager;
        this.plugin       = plugin;
    }

    /* =========================
       CLIC SUR LE MAIRE
       ========================= */

    @EventHandler
    public void onNPCRightClick(NPCRightClickEvent event) {
        CityNPC type = npcManager.getNPCType(event.getNPC());
        if (type == null) return;

        NPCGui gui = guiRegistry.get(type);
        if (gui == null) return;

        Player player = event.getClicker();

        if (!introManager.hasSeenIntro(player.getUniqueId(), type)) {
            introManager.markIntroSeen(player.getUniqueId(), type);
            TypewriterUtil.play(plugin, player, type.getDialogue("first_meeting"), () -> {
                if (!player.isOnline()) return;

                // ── Récompenses première rencontre : une seule fois pour la ville ──
                // (avant : à chaque nouveau joueur → +chunks et +coins illimités)
                if (type == CityNPC.MAYOR && !cityManager.isMayorGiftGiven()) {
                    cityManager.markMayorGiftGiven();
                    int chunks = cityManager.getFreeChunks();
                    int coins  = plugin.getConfig().getInt("city-expand.first-meeting-coins", 100);
                    cityManager.addBonusChunks(chunks);
                    cityManager.addCityCoins(coins);
                    player.sendMessage("§6" + CityNPC.MAYOR.displayName
                            + " §f: §o« Voici de quoi commencer... »");
                    player.sendMessage("§a✅ La ville a gagné §f+" + chunks + " emplacements de chunk"
                            + " §aet §6+" + coins + " coins§a.");
                }

                gui.open(player);
            });
        } else {
            gui.open(player);
        }
    }

    /* =========================
       CLICS INVENTAIRE
       ========================= */

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
        // Préfixe indépendant du niveau : un menu resté ouvert pendant une montée de palier
        // reste protégé (sinon ses icônes deviendraient récupérables)
        boolean isQuestGui    = title.startsWith(MayorQuestGUI.TITLE_PREFIX);

        if (!isMayorGui && !isBuildingGui && !isDeleteGui
                && !isEconomyGui && !isQuestGui) return;

        event.setCancelled(true);
        // Les clics dans l'inventaire du joueur ne déclenchent rien
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;
        if (event.getCurrentItem() == null) return;

        int slot = event.getRawSlot();

        // ── Sous-menu quêtes ──────────────────────────────────────
        if (isQuestGui) {
            if (mg == null) return;
            boolean shiftRight = event.isShiftClick() && event.isRightClick();
            boolean handled = mg.getQuestGUI().handleClick(player, slot, shiftRight,
                    event.getCurrentItem(), event.getView().getTopInventory().getSize());
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

    /* =========================
       HELPERS
       ========================= */

    private MayorGUI getMayorGUI() {
        NPCGui gui = guiRegistry.getByTitle(MayorGUI.GUI_TITLE);
        return gui instanceof MayorGUI mg ? mg : null;
    }
}