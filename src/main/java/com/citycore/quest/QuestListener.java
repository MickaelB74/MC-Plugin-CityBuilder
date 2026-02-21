package com.citycore.quest;

import com.citycore.npc.CityNPC;
import com.citycore.npc.NPCDataManager;
import com.citycore.npc.villager.VillagerConfig;
import com.citycore.npc.villager.VillagerGUI;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class QuestListener implements Listener {

    private final List<QuestGUI>  questGUIs;
    private final QuestManager    questManager;
    private final NPCDataManager  dataManager;
    private final Economy         economy;
    private final JavaPlugin      plugin;
    private final QuestHUD questHUD;

    private final Map<CityNPC, VillagerConfig> villagerConfigs;

    public QuestListener(List<QuestGUI> questGUIs, QuestManager questManager,
                         NPCDataManager dataManager, Economy economy,
                         JavaPlugin plugin, Map<CityNPC, VillagerConfig> villagerConfigs, QuestHUD questHUD) {
        this.questGUIs    = questGUIs;
        this.questManager = questManager;
        this.dataManager  = dataManager;
        this.economy      = economy;
        this.plugin       = plugin;
        this.villagerConfigs = villagerConfigs;
        this.questHUD = questHUD;
    }

    /* =========================
       VÉRIFICATION INVENTAIRE
       Déclenché à chaque fermeture d'inventaire + login
       ========================= */

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        // 1 tick de délai pour que l'inventaire soit à jour
        Bukkit.getScheduler().runTaskLater(plugin, () ->
                checkInventoryProgress(player), 1L);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () ->
                checkInventoryProgress(event.getPlayer()), 20L);
    }

    private void checkInventoryProgress(Player player) {
        for (QuestGUI gui : questGUIs) {
            CityNPC npc = gui.getNpcType();
            checkQuestCompletion(player, npc, false);
            checkQuestCompletion(player, npc, true);
        }
    }

    private void checkQuestCompletion(Player player, CityNPC npc, boolean isSpecial) {
        QuestDefinition active = questManager.getActiveQuest(
                player.getUniqueId(), npc, isSpecial);
        if (active == null) return;
        if (active.type() != QuestType.COLLECT_ITEMS) return;
        if (questManager.isReadyToValidate(player.getUniqueId(), npc, isSpecial)) return;

        boolean allPresent = true;

        for (QuestObjective obj : active.objectives()) {
            if (!obj.isMaterialObjective()) continue;

            // Compte les items réels dans l'inventaire
            int count = 0;
            for (ItemStack item : player.getInventory().getContents()) {
                if (item != null && item.getType() == obj.material())
                    count += item.getAmount();
            }

            // ✅ Synchronise la progression BDD avec l'inventaire réel
            int capped = Math.min(count, obj.amount());
            questManager.setProgress(player.getUniqueId(), npc, isSpecial,
                    obj.id(), capped);

            if (count < obj.amount()) allPresent = false;
        }

        if (allPresent) {
            questManager.markReadyToValidate(player.getUniqueId(), npc, isSpecial);
            player.sendMessage("§a✅ Vous avez tout ce qu'il faut !");
            player.sendMessage("§7Retournez voir §e" + npc.displayName
                    + " §7pour valider votre quête !");
            player.playSound(player.getLocation(),
                    org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.5f);

            // Dans checkQuestCompletion après markReadyToValidate
            questManager.markReadyToValidate(player.getUniqueId(), npc, isSpecial);
            questHUD.updateHUD(player);
        }
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
            tryIncrementAmount(player, npc, entityName, false, 1);
            tryIncrementAmount(player, npc, entityName, true, 1);
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
            tryIncrementAmount(player, npc, matName, false, 1);
            tryIncrementAmount(player, npc, matName, true, 1);
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
                // Récupère la quête pending (jamais régénérée ici)
                QuestDefinition pending = questManager.getPendingQuest(uuid, npc, isSpecial);
                if (pending == null) {
                    // Ne devrait pas arriver — le GUI crée toujours une pending
                    player.sendMessage("§c❌ Erreur : aucune quête disponible.");
                    return;
                }

                // Accepte la pending → passe en active
                questManager.acceptPendingQuest(uuid, npc, isSpecial, pending);
                player.sendMessage("§a✅ Quête acceptée : §f" + pending.description());

                // Après acceptation
                questManager.acceptPendingQuest(uuid, npc, isSpecial, pending);
                questHUD.updateHUD(player);

                // Vérifie immédiatement si les items sont déjà en poche
                checkQuestCompletion(player, npc, isSpecial);
                gui.open(player);

            } else if (questManager.isReadyToValidate(uuid, npc, isSpecial)) {
                if (active.type() == QuestType.COLLECT_ITEMS) {
                    if (!hasAllItems(player, active)) {
                        questManager.unmarkReadyToValidate(uuid, npc, isSpecial);
                        player.sendMessage("§c❌ Il vous manque des items !");
                        player.sendMessage("§7Continuez à collecter et revenez.");
                        gui.open(player);
                        return;
                    }
                    removeQuestItems(player, active);
                }

                // Coins
                economy.depositPlayer(player, active.reward().coins());

                // ✅ XP NPC
                VillagerConfig vConfig = villagerConfigs.get(npc);
                if (vConfig != null) {
                    int xpPerLevel = isSpecial
                            ? gui.getQuestConfig().getSpecialXpRewardPerLevel()
                            : gui.getQuestConfig().getMainXpRewardPerLevel();
                    int xpGained  = xpPerLevel * npcLevel;

                    boolean levelUp = dataManager.addXP(npc, xpGained,
                            vConfig.getXpThresholds());

                    player.sendMessage("§a🎉 Quête validée ! §6+"
                            + active.reward().coins() + " coins §7| §b+"
                            + xpGained + " XP");

                    // Après validation
                    questManager.validateAndReset(uuid, npc, isSpecial);
                    questHUD.updateHUD(player);

                    if (levelUp) {
                        player.sendMessage("§a🎉 §e" + npc.displayName
                                + " §aest passé niveau §e"
                                + VillagerGUI.getLevelName(dataManager.getLevel(npc)) + "§a !");
                    }
                } else {
                    player.sendMessage("§a🎉 Quête validée ! §6+"
                            + active.reward().coins() + " coins§a !");
                }

                player.playSound(player.getLocation(),
                        org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
                questManager.validateAndReset(uuid, npc, isSpecial);

                // Après validateAndReset — génère immédiatement la prochaine pending
                QuestDefinition nextQuest = isSpecial
                        ? gui.getQuestConfig().generateSpecial(npcLevel)
                        : gui.getQuestConfig().generateMain(npcLevel);
                questManager.setPendingQuest(uuid, npc, nextQuest);

                gui.open(player);
            } else {
                // ── En cours ──────────────────────────────────────
                player.sendMessage("§c❌ Quête en cours — continuez à progresser !");
            }
            return;
        }
    }

    /* =========================
       HELPERS
       ========================= */

    private void tryIncrementAmount(Player player, CityNPC npc,
                                    String keyName, boolean isSpecial, int amount) {
        QuestDefinition active = questManager.getActiveQuest(
                player.getUniqueId(), npc, isSpecial);
        if (active == null) return;

        for (QuestObjective obj : active.objectives()) {
            String objKey = obj.isMaterialObjective()
                    ? obj.material().name() : obj.entity().name();
            if (!objKey.equals(keyName)) continue;

            boolean allDone = questManager.incrementProgress(
                    player.getUniqueId(), npc, isSpecial, obj.id(), amount, active);

            if (allDone) {
                player.sendMessage("§a✅ Objectifs remplis ! Revenez voir "
                        + npc.displayName + " §apour valider !");
                player.playSound(player.getLocation(),
                        org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.5f);
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