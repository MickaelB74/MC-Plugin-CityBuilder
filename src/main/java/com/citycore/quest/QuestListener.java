package com.citycore.quest;

import com.citycore.economy.ItemTags;
import com.citycore.economy.MarketService;
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
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.vehicle.VehicleMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class QuestListener implements Listener {

    private final List<QuestGUI>          questGUIs;
    private final QuestManager            questManager;
    private final NPCPersonalQuestManager personalQuestManager;
    private final NPCDataManager          dataManager;
    private final Economy                 economy;
    private final JavaPlugin              plugin;
    private final MarketService           market;
    private final QuestHUD                questHUD;
    private final NPCManager              npcManager;
    private final NPCNotificationManager  notificationManager;

    public QuestListener(List<QuestGUI> questGUIs,
                         QuestManager questManager,
                         NPCPersonalQuestManager personalQuestManager,
                         NPCDataManager dataManager,
                         Economy economy,
                         JavaPlugin plugin,
                         MarketService market,
                         QuestHUD questHUD,
                         NPCManager npcManager,
                         NPCNotificationManager notificationManager) {
        this.questGUIs            = questGUIs;
        this.questManager         = questManager;
        this.personalQuestManager = personalQuestManager;
        this.dataManager          = dataManager;
        this.economy              = economy;
        this.plugin               = plugin;
        this.market               = market;
        this.questHUD             = questHUD;
        this.npcManager           = npcManager;
        this.notificationManager  = notificationManager;
    }

    /* =========================
       VÉRIFICATION DES OBJECTIFS
       Fermeture d'inventaire, connexion, kill
       ========================= */

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        // 1 tick de délai pour que l'inventaire soit à jour
        Bukkit.getScheduler().runTaskLater(plugin, () -> checkAllProgress(player), 1L);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> checkAllProgress(event.getPlayer()), 20L);
    }

    private void checkAllProgress(Player player) {
        if (!player.isOnline()) return;
        Set<String> active = questManager.getActiveQuestKeys(player.getUniqueId());
        if (active.isEmpty()) return;
        for (QuestGUI gui : questGUIs) {
            CityNPC npc = gui.getNpcType();
            if (active.contains(QuestManager.activeKey(npc, false))) checkQuestCompletion(player, npc, false);
            if (active.contains(QuestManager.activeKey(npc, true)))  checkQuestCompletion(player, npc, true);
        }
    }

    /**
     * Met à jour les objectifs d'items (inventaire, items non achetés uniquement)
     * et marque la quête prête si tous les objectifs (items + kills) sont remplis.
     */
    private void checkQuestCompletion(Player player, CityNPC npc, boolean isSpecial) {
        UUID uuid = player.getUniqueId();
        QuestDefinition active = questManager.getActiveQuest(uuid, npc, isSpecial);
        if (active == null) return;
        if (questManager.isReadyToValidate(uuid, npc, isSpecial)) return;

        for (QuestObjective obj : active.objectives()) {
            if (!obj.isMaterialObjective()) continue;
            int count = ItemTags.countUntagged(player, obj.material());
            questManager.setProgress(uuid, npc, isSpecial, obj.id(), Math.min(count, obj.amount()));
        }

        Map<String, Integer> progress = questManager.getProgress(uuid, npc, isSpecial);
        if (!questManager.isAllCompleted(progress, active)) return;

        questManager.markReadyToValidate(uuid, npc, isSpecial);
        notificationManager.notifyAll(npc, plugin.getServer(), npcManager);
        player.sendMessage("§a✅ Quête remplie !");
        player.sendMessage("§7Retournez voir §e" + npc.displayName + " §7pour la valider.");
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.5f);
        questHUD.updateHUD(player);
    }

    /* =========================
       KILL ENTITIES
       ========================= */

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        if (!(event.getEntity().getKiller() instanceof Player player)) return;
        UUID uuid = player.getUniqueId();
        String entityName = event.getEntityType().name();

        // ── Quêtes standards : objectifs de chasse ────────────────
        Set<String> active = questManager.getActiveQuestKeys(uuid);
        for (QuestGUI gui : questGUIs) {
            CityNPC npc = gui.getNpcType();
            for (boolean special : new boolean[]{false, true}) {
                if (!active.contains(QuestManager.activeKey(npc, special))) continue;
                if (incrementKill(player, npc, special, entityName)) {
                    checkQuestCompletion(player, npc, special);
                }
            }
        }

        // ── Quêtes personnelles ───────────────────────────────────
        triggerActivePersonal(player, TriggerType.KILL, event.getEntity());
    }

    /** @return true si un objectif a progressé */
    private boolean incrementKill(Player player, CityNPC npc, boolean isSpecial, String entityName) {
        QuestDefinition quest = questManager.getActiveQuest(player.getUniqueId(), npc, isSpecial);
        if (quest == null) return false;
        if (questManager.isReadyToValidate(player.getUniqueId(), npc, isSpecial)) return false;
        boolean progressed = false;
        for (QuestObjective obj : quest.objectives()) {
            if (!obj.isEntityObjective() || !obj.entity().name().equals(entityName)) continue;
            questManager.incrementProgress(player.getUniqueId(), npc, isSpecial, obj.id(), 1, quest);
            int cur = questManager.getProgress(player.getUniqueId(), npc, isSpecial)
                    .getOrDefault(obj.id(), 0);
            com.citycore.util.TypewriterUtil.actionBar(player, "§c⚔ " + npc.displayName.replaceAll("§.", "")
                    + " §7— " + cur + "§7/§f" + obj.amount());
            progressed = true;
        }
        return progressed;
    }

    /* =========================
       DÉCLENCHEURS DES QUÊTES PERSONNELLES
       ========================= */

    /** Fabrications en cours de comptage (« uuid|matériau »), mesurées une fois par tick. */
    private final java.util.Set<String> pendingCrafts = new java.util.HashSet<>();

    /**
     * Artisanat : on compte ce qui est réellement arrivé dans l'inventaire au tick suivant
     * (un clic qui ne fabrique rien ne compte pas ; Maj+clic compte toutes les fabrications ;
     * les recettes spéciales comme les fusées sont prises en compte).
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onCraft(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getAction() == InventoryAction.NOTHING) return;
        ItemStack result = event.getInventory().getResult();
        if (result == null || result.getType().isAir()) return;
        if (personalQuestManager.getActiveEntries(player.getUniqueId()).isEmpty()) return;

        // Plusieurs clics dans le même tick : une seule mesure, à partir du premier état
        String key = player.getUniqueId() + "|" + result.getType();
        if (!pendingCrafts.add(key)) return;

        ItemStack snapshot = result.clone();
        int before = countWithCursor(player, snapshot.getType());
        Bukkit.getScheduler().runTask(plugin, () -> {
            pendingCrafts.remove(key);
            if (!player.isOnline()) return;
            int crafted = countWithCursor(player, snapshot.getType()) - before;
            if (crafted <= 0) return;
            int crafts = (int) Math.ceil((double) crafted / Math.max(1, snapshot.getAmount()));
            triggerActivePersonal(player, TriggerType.CRAFT, snapshot, crafts);
        });
    }

    private static int countWithCursor(Player player, Material type) {
        int count = 0;
        for (ItemStack s : player.getInventory().getStorageContents()) {
            if (s != null && s.getType() == type) count += s.getAmount();
        }
        ItemStack cursor = player.getItemOnCursor();
        if (cursor.getType() == type) count += cursor.getAmount();
        return count;
    }

    @EventHandler(ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;
        if (!(event.getCaught() instanceof Item caught)) return;
        triggerActivePersonal(event.getPlayer(), TriggerType.FISH, caught.getItemStack());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreed(EntityBreedEvent event) {
        if (!(event.getBreeder() instanceof Player player)) return;
        triggerActivePersonal(player, TriggerType.BREED, event.getEntity());
    }

    /** Échange avec un villageois (métiers des mods compris : pirates, marins…). */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTrade(io.papermc.paper.event.player.PlayerTradeEvent event) {
        triggerActivePersonal(event.getPlayer(), TriggerType.TRADE, event.getVillager());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(org.bukkit.event.block.BlockPlaceEvent event) {
        triggerActivePersonal(event.getPlayer(), TriggerType.PLACE, event.getBlockPlaced());
    }

    @EventHandler
    public void onAdvancement(PlayerAdvancementDoneEvent event) {
        triggerActivePersonal(event.getPlayer(), TriggerType.ADVANCEMENT, event.getAdvancement());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        // Ignore les rotations de caméra
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) return;
        triggerActivePersonal(event.getPlayer(), TriggerType.MOVE, null);
    }

    /** Un joueur à cheval ou en bateau ne déclenche pas PlayerMoveEvent : on relaie ici. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVehicleMove(VehicleMoveEvent event) {
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) return;
        for (org.bukkit.entity.Entity passenger : event.getVehicle().getPassengers()) {
            if (passenger instanceof Player player) triggerActivePersonal(player, TriggerType.MOVE, null);
        }
    }

    /**
     * Toutes les 2 secondes, relaie un déplacement pour les joueurs en selle. Les montures des
     * mods (dragons de Dragon Mounts…) ne déclenchent pas toujours VehicleMoveEvent : sans ce
     * relais, les quêtes de vol ne se valideraient jamais. Seules les quêtes personnelles
     * actives de type MOVE sont évaluées (cache mémoire).
     */
    public void startRidingTicker() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getVehicle() != null) triggerActivePersonal(player, TriggerType.MOVE, null);
            }
        }, 40L, 40L);
    }

    /**
     * Point d'entrée pour SummitListener : un joueur atteint le sommet d'une montagne.
     */
    public void onSummitReached(Player player) {
        triggerActivePersonal(player, TriggerType.SUMMIT, null);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        personalQuestManager.evict(uuid);
        for (QuestGUI gui : questGUIs) {
            if (gui.getPersonalQuestGUI() != null) gui.getPersonalQuestGUI().forget(uuid);
        }
    }

    /**
     * Évalue uniquement les quêtes personnelles ACTIVES du joueur (cache mémoire)
     * dont le déclencheur correspond.
     */
    private void triggerActivePersonal(Player player, TriggerType trigger, Object context) {
        triggerActivePersonal(player, trigger, context, 1);
    }

    /** @param times nombre d'occurrences (ex : 16 fabrications d'un coup) */
    private void triggerActivePersonal(Player player, TriggerType trigger, Object context, int times) {
        Set<String> entries = personalQuestManager.getActiveEntries(player.getUniqueId());
        if (entries.isEmpty()) return;
        for (String entry : List.copyOf(entries)) {
            int sep = entry.indexOf('|');
            CityNPC npc = CityNPC.fromTag(entry.substring(0, sep));
            if (npc == null) continue;
            NPCPersonalQuest quest = npc.getPersonalQuestById(entry.substring(sep + 1));
            if (quest == null || quest.trigger() != trigger) continue;
            triggerPersonalQuest(player, npc, quest, context, times);
        }
    }

    /* =========================
       GUI — ACCEPTER / VALIDER / PERSO
       ========================= */

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String title = event.getView().getTitle();

        // ── GUI principal des quêtes du PNJ ───────────────────────
        for (QuestGUI gui : questGUIs) {
            if (!QuestGUI.titleQuests(gui.getNpcType()).equals(title)) continue;
            event.setCancelled(true);
            if (event.getClickedInventory() != event.getView().getTopInventory()) return;
            if (event.getCurrentItem() == null) return;
            handleQuestClick(player, gui, event.getRawSlot());
            return;
        }

        // ── GUI Quêtes Personnelles ───────────────────────────────
        for (QuestGUI gui : questGUIs) {
            NPCPersonalQuestGUI personalGUI = gui.getPersonalQuestGUI();
            if (personalGUI == null) continue;
            if (!NPCPersonalQuestGUI.title(personalGUI.getNpc()).equals(title)) continue;

            event.setCancelled(true);
            if (event.getClickedInventory() != event.getView().getTopInventory()) return;
            if (event.getCurrentItem() == null) return;
            handlePersonalClick(player, gui, personalGUI, event.getRawSlot(),
                    event.getInventory().getSize());
            return;
        }
    }

    /** Accepte la quête proposée ou valide la quête prête (écrans illustrés). */
    public void clickQuest(Player player, QuestGUI gui, boolean special) {
        handleQuestClick(player, gui, special ? QuestGUI.SLOT_SPECIAL : QuestGUI.SLOT_MAIN);
    }

    /** Ouvre les quêtes personnelles du PNJ (écrans illustrés). */
    public void openPersonalQuests(Player player, QuestGUI gui) {
        handleQuestClick(player, gui, QuestGUI.SLOT_PERSONAL);
    }

    /** Accepte / consulte la quête personnelle n° {@code index} (écrans illustrés). */
    public void clickPersonalQuest(Player player, QuestGUI gui, int index) {
        NPCPersonalQuestGUI personal = gui.getPersonalQuestGUI();
        if (personal != null) handlePersonalClick(player, gui, personal, index, Integer.MAX_VALUE);
    }

    private void handleQuestClick(Player player, QuestGUI gui, int slot) {
        if (slot == QuestGUI.SLOT_BACK) {
            player.closeInventory();
            return;
        }

        // Bouton quêtes personnelles
        if (slot == QuestGUI.SLOT_PERSONAL) {
            NPCPersonalQuestGUI personalGUI = gui.getPersonalQuestGUI();
            if (personalGUI != null) {
                player.closeInventory();
                Bukkit.getScheduler().runTaskLater(plugin, () -> personalGUI.openFromNpc(player), 1L);
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
            QuestDefinition pending = questManager.getPendingQuest(uuid, npc, isSpecial);
            if (pending == null) {
                long remaining = questManager.getCooldownRemaining(uuid, npc, isSpecial);
                if (remaining > 0) {
                    player.sendMessage("§e⏳ Prochaine quête dans "
                            + Math.max(1, (remaining + 59_999) / 60_000) + " min.");
                }
                return;
            }

            // Accepte la pending → passe en active
            questManager.acceptPendingQuest(uuid, npc, isSpecial, pending);
            player.sendMessage("§a✅ Quête acceptée : §f" + pending.description());
            questHUD.updateHUD(player);

            // Vérifie immédiatement si les items sont déjà en poche
            checkQuestCompletion(player, npc, isSpecial);
            gui.open(player);
            return;
        }

        if (!questManager.isReadyToValidate(uuid, npc, isSpecial)) {
            player.sendMessage("§c❌ Quête en cours — continuez à progresser !");
            return;
        }

        // ── Validation ────────────────────────────────────────────
        if (!hasAllItems(player, active)) {
            questManager.unmarkReadyToValidate(uuid, npc, isSpecial);
            player.sendMessage("§c❌ Il vous manque des items (non achetés à un PNJ) !");
            gui.open(player);
            return;
        }
        deliverItems(player, npc, active);

        economy.depositPlayer(player, active.reward().coins());

        VillagerConfig vConfig = market.config(npc);
        int xpPerLevel = isSpecial
                ? gui.getQuestConfig().getSpecialXpRewardPerLevel()
                : gui.getQuestConfig().getMainXpRewardPerLevel();
        int xpGained = xpPerLevel * npcLevel;
        boolean levelUp = vConfig != null && dataManager.addXP(npc, xpGained, vConfig.getXpThresholds());

        player.sendMessage("§a🎉 Quête validée ! §6+" + active.reward().coins()
                + " coins §7| §b+" + xpGained + " XP " + npc.displayName);
        if (levelUp) {
            player.sendMessage("§a🎉 §e" + npc.displayName + " §aest passé "
                    + VillagerGUI.getLevelName(dataManager.getLevel(npc)) + "§a !");
        }
        player.playSound(player.getLocation(), org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);

        questManager.validateAndReset(uuid, npc, isSpecial);
        int cooldownMinutes = isSpecial
                ? market.cfg().specialCooldownMinutes()
                : market.cfg().mainCooldownMinutes();
        questManager.setCooldown(uuid, npc, isSpecial, cooldownMinutes * 60_000L);
        questHUD.updateHUD(player);
        gui.open(player);
    }

    private void handlePersonalClick(Player player, QuestGUI gui, NPCPersonalQuestGUI personalGUI,
                                     int slot, int size) {
        // Bouton retour : vers le journal de quêtes s'il a ouvert ces récits, sinon vers les quêtes du PNJ
        if (slot == size - 9) {
            Runnable journal = personalGUI.journalBack(player);
            player.closeInventory();
            Bukkit.getScheduler().runTaskLater(plugin, journal != null ? journal : () -> gui.open(player), 1L);
            return;
        }

        CityNPC npc = personalGUI.getNpc();
        int npcLevel = dataManager.getLevel(npc);
        List<NPCPersonalQuest> quests = npc.getPersonalQuestsForLevel(npcLevel);
        if (slot < 0 || slot >= quests.size()) return;

        NPCPersonalQuest quest = quests.get(slot);
        UUID uuid = player.getUniqueId();

        if (personalQuestManager.isCompleted(uuid, npc, quest.id())) {
            player.sendMessage("§a✔ Cette quête est déjà complétée !");
        } else if (personalQuestManager.isActive(uuid, npc, quest.id())) {
            player.sendMessage("§e⏳ Quête en cours : " + quest.displayName());
        } else {
            personalQuestManager.accept(uuid, npc, quest.id());
            player.sendMessage("§a✅ Quête acceptée : " + quest.displayName());
            player.sendMessage("§7" + quest.description());
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.5f);

            // Un succès Minecraft déjà obtenu valide la quête immédiatement
            if (quest.trigger() == TriggerType.ADVANCEMENT && quest.validator().validate(player, null)) {
                triggerPersonalQuest(player, npc, quest, null);
            }
            personalGUI.open(player); // refresh
        }
    }

    /* =========================
       HELPERS — QUÊTES PERSONNELLES
       ========================= */

    /**
     * Évalue le validator et incrémente si la quête est active.
     * Donne la récompense automatiquement à la complétion.
     */
    private void triggerPersonalQuest(Player player, CityNPC npc,
                                      NPCPersonalQuest quest, Object context) {
        triggerPersonalQuest(player, npc, quest, context, 1);
    }

    private void triggerPersonalQuest(Player player, CityNPC npc,
                                      NPCPersonalQuest quest, Object context, int times) {
        if (!personalQuestManager.isActive(player.getUniqueId(), npc, quest.id())) return;
        if (!quest.validator().validate(player, context)) return;

        boolean done = personalQuestManager.increment(player.getUniqueId(), npc, quest, times);
        if (done) {
            economy.depositPlayer(player, quest.reward());
            player.sendMessage("§a✅ " + quest.displayName()
                    + " §acomplétée ! §6+" + quest.reward() + " coins");
            player.sendMessage("§7Parlez à " + npc.displayName
                    + " §7pour voir vos autres quêtes disponibles.");
            player.playSound(player.getLocation(),
                    org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.2f);
        } else {
            int progress = personalQuestManager.getProgress(player.getUniqueId(), npc, quest.id());
            if (quest.targetAmount() > 1) {
                com.citycore.util.TypewriterUtil.actionBar(player, "§5✦ " + quest.displayName()
                        + " §7(§f" + progress + "§7/§f" + quest.targetAmount() + "§7)");
            } else {
                com.citycore.util.TypewriterUtil.actionBar(player, "§5✦ " + quest.displayName() + " §7— en cours...");
            }
        }
    }

    /* =========================
       HELPERS — QUÊTES STANDARDS
       ========================= */

    private boolean hasAllItems(Player player, QuestDefinition quest) {
        for (QuestObjective obj : quest.objectives()) {
            if (!obj.isMaterialObjective()) continue;
            if (ItemTags.countUntagged(player, obj.material()) < obj.amount()) return false;
        }
        return true;
    }

    /**
     * Retire les items livrés. Ceux que le PNJ stocke rejoignent son entrepôt :
     * ce qu'une quête demande, c'est de la marchandise dont il a besoin.
     */
    private void deliverItems(Player player, CityNPC npc, QuestDefinition quest) {
        VillagerConfig cfg = market.config(npc);
        for (QuestObjective obj : quest.objectives()) {
            if (!obj.isMaterialObjective()) continue;
            int removed = ItemTags.removeUntagged(player, obj.material(), obj.amount());
            if (cfg != null && cfg.isSellable(obj.material()) && removed > 0) {
                dataManager.addToInventory(npc, obj.material(), removed);
            }
        }
    }
}
