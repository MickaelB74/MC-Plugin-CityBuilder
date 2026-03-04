package com.citycore.quest;

import com.citycore.npc.CityNPC;
import com.citycore.npc.NPCDataManager;
import com.citycore.npc.NPCManager;
import com.citycore.npc.NPCNotificationManager;
import com.citycore.npc.villager.VillagerConfig;
import com.citycore.npc.villager.VillagerGUI;
import com.citycore.quest.personal.NPCPersonalQuest;
import com.citycore.quest.personal.NPCPersonalQuestGUI;
import com.citycore.quest.personal.NPCPersonalQuestManager;
import com.citycore.quest.personal.PersonalQuestDefinition.TriggerType;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class QuestListener implements Listener {

    private final List<QuestGUI>             questGUIs;
    private final QuestManager               questManager;
    private final NPCPersonalQuestManager    personalQuestManager;
    private final NPCDataManager             dataManager;
    private final Economy                    economy;
    private final JavaPlugin                 plugin;
    private final QuestHUD                   questHUD;
    private final NPCManager                 npcManager;
    private final NPCNotificationManager     notificationManager;
    private final Map<CityNPC, VillagerConfig> villagerConfigs;

    // Déduplication chunks visités pour EXPLORE_CHUNK
    // clé : "uuid_chunkX_chunkZ"
    private final Set<String> visitedChunkKeys = new HashSet<>();

    public QuestListener(List<QuestGUI> questGUIs,
                         QuestManager questManager,
                         NPCPersonalQuestManager personalQuestManager,
                         NPCDataManager dataManager,
                         Economy economy,
                         JavaPlugin plugin,
                         Map<CityNPC, VillagerConfig> villagerConfigs,
                         QuestHUD questHUD,
                         NPCManager npcManager,
                         NPCNotificationManager notificationManager) {
        this.questGUIs            = questGUIs;
        this.questManager         = questManager;
        this.personalQuestManager = personalQuestManager;
        this.dataManager          = dataManager;
        this.economy              = economy;
        this.plugin               = plugin;
        this.villagerConfigs      = villagerConfigs;
        this.questHUD             = questHUD;
        this.npcManager           = npcManager;
        this.notificationManager  = notificationManager;
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

            // Synchronise la progression BDD avec l'inventaire réel
            int capped = Math.min(count, obj.amount());
            questManager.setProgress(player.getUniqueId(), npc, isSpecial,
                    obj.id(), capped);

            if (count < obj.amount()) allPresent = false;
        }

        if (allPresent) {
            questManager.markReadyToValidate(player.getUniqueId(), npc, isSpecial);
            notificationManager.notifyAll(npc, plugin.getServer(), npcManager);
            player.sendMessage("§a✅ Vous avez tout ce qu'il faut !");
            player.sendMessage("§7Retournez voir §e" + npc.displayName
                    + " §7pour valider votre quête !");
            player.playSound(player.getLocation(),
                    org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.5f);
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

        // ── Quêtes standards (KILL_ENTITIES) ──────────────────────
        for (QuestGUI gui : questGUIs) {
            CityNPC npc = gui.getNpcType();
            tryIncrementAmount(player, npc, entityName, false, 1);
            tryIncrementAmount(player, npc, entityName, true, 1);
        }

        // ── Quêtes personnelles (TriggerType.KILL) ────────────────
        for (QuestGUI gui : questGUIs) {
            CityNPC npc = gui.getNpcType();
            if (!npc.hasPersonalQuests()) continue;
            int npcLevel = dataManager.getLevel(npc);
            for (NPCPersonalQuest quest : npc.getPersonalQuestsForLevel(npcLevel)) {
                if (quest.trigger() != TriggerType.KILL) continue;
                triggerPersonalQuest(player, npc, quest, event.getEntity());
            }
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
       PLAYER MOVE — EXPLORE_CHUNK + PERSONAL(MOVE)
       ========================= */

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        // Ignore les rotations de caméra
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) return;

        Player player = event.getPlayer();
        UUID   uuid   = player.getUniqueId();

        // ── EXPLORE_CHUNK — déduplication par chunk ───────────────
        int cx = event.getTo().getChunk().getX();
        int cz = event.getTo().getChunk().getZ();
        org.bukkit.block.Biome biome = event.getTo().getBlock().getBiome();
        String chunkKey = uuid + "_" + cx + "_" + cz;

        if (!visitedChunkKeys.contains(chunkKey)) {
            visitedChunkKeys.add(chunkKey);
            for (QuestGUI gui : questGUIs) {
                CityNPC npc = gui.getNpcType();
                questManager.onChunkEntered(player, npc, false, biome);
                questManager.onChunkEntered(player, npc, true,  biome);
            }
        }

        // ── Quêtes personnelles (TriggerType.MOVE) ────────────────
        for (QuestGUI gui : questGUIs) {
            CityNPC npc = gui.getNpcType();
            if (!npc.hasPersonalQuests()) continue;
            int npcLevel = dataManager.getLevel(npc);
            for (NPCPersonalQuest quest : npc.getPersonalQuestsForLevel(npcLevel)) {
                if (quest.trigger() != TriggerType.MOVE) continue;
                triggerPersonalQuest(player, npc, quest, null);
            }
        }
    }

    /* =========================
       PLAYER QUIT — nettoyage mémoire
       ========================= */

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        String prefix = event.getPlayer().getUniqueId().toString() + "_";
        visitedChunkKeys.removeIf(k -> k.startsWith(prefix));
    }

    /* =========================
       GUI — ACCEPTER / VALIDER / PERSO
       ========================= */

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String title = event.getView().getTitle();

        // ── GUI principal du NPC ──────────────────────────────────
        for (QuestGUI gui : questGUIs) {
            if (!QuestGUI.titleQuests(gui.getNpcType()).equals(title)) continue;
            event.setCancelled(true);
            if (event.getCurrentItem() == null) return;

            int slot = event.getSlot();

            if (slot == QuestGUI.SLOT_BACK) {
                player.closeInventory();
                return;
            }

            // Bouton quêtes personnelles
            if (slot == QuestGUI.SLOT_PERSONAL) {
                NPCPersonalQuestGUI personalGUI = gui.getPersonalQuestGUI();
                if (personalGUI != null) {
                    player.closeInventory();
                    Bukkit.getScheduler().runTaskLater(plugin,
                            () -> personalGUI.open(player), 1L);
                }
                return;
            }

            if (slot != QuestGUI.SLOT_MAIN && slot != QuestGUI.SLOT_SPECIAL) return;

            boolean isSpecial = (slot == QuestGUI.SLOT_SPECIAL);
            UUID    uuid      = player.getUniqueId();
            CityNPC npc       = gui.getNpcType();
            int     npcLevel  = dataManager.getLevel(npc);

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

                // XP NPC
                VillagerConfig vConfig = villagerConfigs.get(npc);
                if (vConfig != null) {
                    int xpPerLevel = isSpecial
                            ? gui.getQuestConfig().getSpecialXpRewardPerLevel()
                            : gui.getQuestConfig().getMainXpRewardPerLevel();
                    int xpGained = xpPerLevel * npcLevel;

                    boolean levelUp = dataManager.addXP(npc, xpGained,
                            vConfig.getXpThresholds());

                    player.sendMessage("§a🎉 Quête validée ! §6+"
                            + active.reward().coins() + " coins §7| §b+"
                            + xpGained + " XP");

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

                // Génère immédiatement la prochaine pending
                QuestDefinition nextQuest = isSpecial
                        ? gui.getQuestConfig().generateSpecial(npcLevel)
                        : gui.getQuestConfig().generateMain(npcLevel);
                questManager.setPendingQuest(uuid, npc, nextQuest);

                gui.open(player);
            } else {
                // En cours
                player.sendMessage("§c❌ Quête en cours — continuez à progresser !");
            }
            return;
        }

        // ── GUI Quêtes Personnelles ───────────────────────────────
        for (QuestGUI gui : questGUIs) {
            NPCPersonalQuestGUI personalGUI = gui.getPersonalQuestGUI();
            if (personalGUI == null) continue;
            if (!NPCPersonalQuestGUI.title(personalGUI.getNpc()).equals(title)) continue;

            event.setCancelled(true);
            if (event.getCurrentItem() == null) return;

            int slot = event.getSlot();
            int size = event.getInventory().getSize();

            // Bouton retour
            if (slot == size - 9) {
                player.closeInventory();
                Bukkit.getScheduler().runTaskLater(plugin, () -> gui.open(player), 1L);
                return;
            }

            // Clic sur une quête — progression passive, pas d'action directe
            return;
        }
    }

    /* =========================
       HELPERS — QUÊTES PERSONNELLES
       ========================= */

    /**
     * Évalue le validator d'une quête perso et incrémente si validé.
     * Donne la récompense automatiquement à la complétion.
     */
    private void triggerPersonalQuest(Player player, CityNPC npc,
                                      NPCPersonalQuest quest, Object context) {
        if (personalQuestManager.isCompleted(player.getUniqueId(), npc, quest.id())) return;
        if (!quest.validator().validate(player, context)) return;

        boolean done = personalQuestManager.increment(player.getUniqueId(), npc, quest);
        if (done) {
            economy.depositPlayer(player, quest.reward());
            player.sendMessage("§a✅ " + quest.displayName()
                    + " §acomplétée ! §6+" + quest.reward() + " coins");
            player.sendMessage("§7Parlez à " + npc.displayName
                    + " §7pour voir vos autres quêtes disponibles.");
            player.playSound(player.getLocation(),
                    org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.2f);
        } else {
            int progress = personalQuestManager.getProgress(
                    player.getUniqueId(), npc, quest.id());
            if (quest.targetAmount() > 1) {
                player.sendActionBar("§5✦ " + quest.displayName()
                        + " §7(§f" + progress + "§7/§f" + quest.targetAmount() + "§7)");
            } else {
                player.sendActionBar("§5✦ " + quest.displayName() + " §7— en cours...");
            }
        }
    }

    /* =========================
       HELPERS — QUÊTES STANDARDS
       ========================= */

    private void tryIncrementAmount(Player player, CityNPC npc,
                                    String keyName, boolean isSpecial, int amount) {
        QuestDefinition active = questManager.getActiveQuest(
                player.getUniqueId(), npc, isSpecial);
        if (active == null) return;

        for (QuestObjective obj : active.objectives()) {
            if (!obj.isMaterialObjective() && !obj.isEntityObjective()) continue;
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