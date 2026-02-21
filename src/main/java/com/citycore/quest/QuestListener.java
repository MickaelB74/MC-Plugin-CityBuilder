package com.citycore.quest;

import com.citycore.npc.CityNPC;
import com.citycore.npc.NPCDataManager;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class QuestListener implements Listener {

    private final List<QuestGUI>  questGUIs;
    private final QuestManager    questManager;
    private final NPCDataManager  dataManager;
    private final Economy         economy;

    public QuestListener(List<QuestGUI> questGUIs, QuestManager questManager,
                         NPCDataManager dataManager, Economy economy) {
        this.questGUIs    = questGUIs;
        this.questManager = questManager;
        this.dataManager  = dataManager;
        this.economy      = economy;
    }

    /* =========================
       KILL ENTITIES
       ========================= */

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        if (!(event.getEntity().getKiller() instanceof Player player)) return;
        String entityName = event.getEntityType().name();

        for (QuestGUI gui : questGUIs) {
            CityNPC npc = gui.getNpcType();
            tryIncrement(player, npc, gui, entityName, false);
            tryIncrement(player, npc, gui, entityName, true);
        }
    }

    /* =========================
       CRAFT ITEMS
       ========================= */

    @EventHandler
    public void onCraft(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String matName = event.getRecipe().getResult().getType().name();

        for (QuestGUI gui : questGUIs) {
            CityNPC npc = gui.getNpcType();
            tryIncrement(player, npc, gui, matName, false);
            tryIncrement(player, npc, gui, matName, true);
        }
    }

    /* =========================
       GUI — ACCEPTER / VALIDER
       ========================= */

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String title = event.getView().getTitle();

        for (QuestGUI gui : questGUIs) {
            if (!QuestGUI.titleQuests(gui.getNpcType()).equals(title)) continue;
            event.setCancelled(true);
            if (event.getCurrentItem() == null) return;

            int slot = event.getSlot();

            // Retour
            if (slot == QuestGUI.SLOT_BACK) {
                player.closeInventory();
                return;
            }

            if (slot != QuestGUI.SLOT_MAIN && slot != QuestGUI.SLOT_SPECIAL) return;

            boolean isSpecial = (slot == QuestGUI.SLOT_SPECIAL);
            UUID uuid         = player.getUniqueId();
            CityNPC npc       = gui.getNpcType();
            int npcLevel      = dataManager.getLevel(npc);

            QuestDefinition active = questManager.getActiveQuest(uuid, npc, isSpecial);

            if (active == null) {
                // ── Accepter ─────────────────────────────────────
                QuestDefinition generated = isSpecial
                        ? gui.getQuestConfig().generateSpecial(npcLevel)
                        : gui.getQuestConfig().generateMain(npcLevel);
                questManager.startQuest(uuid, npc, generated);
                player.sendMessage("§a✅ Quête acceptée : §f" + generated.description());
                gui.open(player);

            } else {
                // ── Vérifier complétable ──────────────────────────
                Map<String, Integer> progress = questManager.getProgress(
                        uuid, npc, isSpecial);
                boolean allDone = questManager.isAllCompleted(progress, active);

                if (!allDone) {
                    player.sendMessage("§c❌ Quête en cours — continuez à progresser !");
                    return;
                }

                // Vérifie et retire les items si COLLECT
                if (active.type() == QuestType.COLLECT_ITEMS) {
                    if (!hasAllItems(player, active)) {
                        player.sendMessage(
                                "§c❌ Il vous manque des items dans l'inventaire !");
                        gui.open(player);
                        return;
                    }
                    removeQuestItems(player, active);
                }

                // Récompense
                economy.depositPlayer(player, active.reward().coins());
                questManager.validateAndReset(uuid, npc, isSpecial);
                player.sendMessage("§a🎉 Quête terminée ! §6+"
                        + active.reward().coins() + " coins §aajoutés !");

                // Rouvre avec nouvelle quête générée
                gui.open(player);
            }
            return;
        }
    }

    /* =========================
       HELPERS
       ========================= */

    private void tryIncrement(Player player, CityNPC npc, QuestGUI gui,
                              String keyName, boolean isSpecial) {
        QuestDefinition active = questManager.getActiveQuest(
                player.getUniqueId(), npc, isSpecial);
        if (active == null) return;

        for (QuestObjective obj : active.objectives()) {
            String objKey = obj.isMaterialObjective()
                    ? obj.material().name() : obj.entity().name();
            if (!objKey.equals(keyName)) continue;

            boolean allDone = questManager.incrementProgress(
                    player.getUniqueId(), npc, isSpecial, obj.id(), 1, active);

            if (allDone) {
                player.sendMessage("§a✅ Objectifs remplis ! Revenez voir "
                        + npc.displayName + " §apour valider !");
            }
        }
    }

    private boolean hasAllItems(Player player, QuestDefinition quest) {
        for (QuestObjective obj : quest.objectives()) {
            if (!obj.isMaterialObjective()) continue;
            int count = 0;
            for (ItemStack item : player.getInventory().getContents()) {
                if (item != null && item.getType() == obj.material())
                    count += item.getAmount();
            }
            if (count < obj.amount()) return false;
        }
        return true;
    }

    private void removeQuestItems(Player player, QuestDefinition quest) {
        for (QuestObjective obj : quest.objectives()) {
            if (!obj.isMaterialObjective()) continue;
            int toRemove = obj.amount();
            for (ItemStack item : player.getInventory().getContents()) {
                if (item == null || item.getType() != obj.material()
                        || toRemove <= 0) continue;
                int take = Math.min(item.getAmount(), toRemove);
                item.setAmount(item.getAmount() - take);
                toRemove -= take;
            }
        }
    }
}