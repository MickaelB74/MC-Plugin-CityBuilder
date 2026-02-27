package com.citycore.quest.city;

import com.citycore.city.CityManager;
import com.citycore.npc.CityNPC;
import com.citycore.npc.NPCDataManager;
import com.citycore.npc.NPCManager;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.*;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public class FindNpcQuestManager {

    private static final double MAX_DISTANCE = 500.0;
    private static final int    COMPASS_SLOT = 8;
    private static final long   TASK_TICKS   = 20L;

    private final JavaPlugin       plugin;
    private final NPCManager       npcManager;
    private final NPCDataManager   npcDataManager;
    private final CityManager      cityManager;
    private final CityQuestManager questManager;

    // questId → dernière position connue du NPC
    private final Map<String, Location>   activeQuestLocations = new HashMap<>();
    // questId → NPC caché au démarrage de la quête (évite getNPC() en continu)
    private final Map<String, NPC>        activeQuestNpcs      = new HashMap<>();
    private final Map<String, BukkitTask> npcTrackerTasks      = new HashMap<>();
    private final Map<UUID, String>       playerActiveQuest    = new HashMap<>();
    private final Map<UUID, BossBar>      playerBossBars       = new HashMap<>();

    public FindNpcQuestManager(JavaPlugin plugin, NPCManager npcManager,
                               NPCDataManager npcDataManager,
                               CityManager cityManager,
                               CityQuestManager questManager) {
        this.plugin         = plugin;
        this.npcManager     = npcManager;
        this.npcDataManager = npcDataManager;
        this.cityManager    = cityManager;
        this.questManager   = questManager;
    }

    /* =========================
       DÉCLENCHEMENT QUÊTE
       ========================= */

    public void triggerQuest(CityQuest quest, Player trigger) {
        if (isQuestActive(quest.id())) {
            playerActiveQuest.put(trigger.getUniqueId(), quest.id());
            giveQuestItem(trigger, quest);
            createBossBar(trigger, quest);
            return;
        }

        if (questManager.isCompleted(quest.id())) {
            trigger.sendMessage("§a✔ Cette quête est déjà complétée !");
            return;
        }

        // Récupère ou spawne le NPC — un seul appel à getNPC ici
        NPC npc = npcManager.getNPC(quest.targetNpc());
        Location spawnLoc;

        if (npc != null && npc.isSpawned() && npc.getEntity() != null) {
            spawnLoc = npc.getEntity().getLocation();
        } else {
            spawnLoc = findSafeLocation(quest.spawnRadius(), trigger.getWorld());
            if (spawnLoc == null) {
                trigger.sendMessage("§c❌ Impossible de trouver un endroit sûr.");
                return;
            }
            npc = npcManager.spawnNPC(quest.targetNpc(), spawnLoc);
        }

        activeQuestLocations.put(quest.id(), spawnLoc);
        // Cache le NPC — plus besoin de getNPC() ensuite
        activeQuestNpcs.put(quest.id(), npc);

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendMessage("");
            p.sendMessage("§e🗺 Quête ville : §f" + quest.displayName());
            p.sendMessage("§7Ramenez §e" + quest.targetNpc().displayName + " §7en ville !");
            p.sendMessage("");
            playerActiveQuest.put(p.getUniqueId(), quest.id());
            giveQuestItem(p, quest);
            createBossBar(p, quest);
        }

        startNpcTrackerTask(quest);
    }

    /* =========================
       COMPLÉTION QUÊTE
       ========================= */

    public void onNpcArrived(CityNPC npc) {
        CityTier tier = CityTier.fromLevel(questManager.getCityLevel());
        for (CityQuest quest : tier.quests) {
            if (quest.type() != CityQuestType.FIND_NPC) continue;
            if (quest.targetNpc() != npc) continue;
            if (questManager.isCompleted(quest.id())) continue;

            questManager.setProgress(quest.id(), 1, true);
            activeQuestLocations.remove(quest.id());
            activeQuestNpcs.remove(quest.id());

            BukkitTask task = npcTrackerTasks.remove(quest.id());
            if (task != null) task.cancel();

            for (Player p : Bukkit.getOnlinePlayers()) {
                removeQuestItem(p);
                destroyBossBar(p);
                playerActiveQuest.remove(p.getUniqueId());
                p.sendMessage("");
                p.sendMessage("§a🎉 Quête complétée : " + quest.displayName());
                p.sendMessage("§a" + npc.displayName + " §aest arrivé en ville !");
                p.sendMessage("");
            }
            break;
        }
    }

    /* =========================
       ITEM DE QUÊTE
       ========================= */

    public void giveQuestItem(Player player, CityQuest quest) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta  meta = item.getItemMeta();
        meta.setDisplayName("§e🗺 " + quest.displayName());
        meta.setLore(List.of(
                "§7Trouver §e" + quest.targetNpc().displayName,
                "§7Suivez la barre en haut de l'écran."
        ));
        item.setItemMeta(meta);
        player.getInventory().setItem(COMPASS_SLOT, item);
    }

    private void removeQuestItem(Player player) {
        ItemStack item = player.getInventory().getItem(COMPASS_SLOT);
        if (item == null) return;
        if (!item.hasItemMeta() || !item.getItemMeta().hasDisplayName()) return;
        if (item.getItemMeta().getDisplayName().contains("🗺"))
            player.getInventory().setItem(COMPASS_SLOT, null);
    }

    /* =========================
       BOSSBAR
       ========================= */

    public void createBossBar(Player player, CityQuest quest) {
        destroyBossBar(player);
        BossBar bar = Bukkit.createBossBar(
                "§e🗺 " + ChatColor.stripColor(quest.targetNpc().displayName),
                BarColor.YELLOW,
                BarStyle.SOLID
        );
        bar.setProgress(0.005);
        bar.addPlayer(player);
        playerBossBars.put(player.getUniqueId(), bar);
    }

    /**
     * Appelé par PlayerMoveEvent — utilise le NPC caché, pas getNPC().
     */
    public void onPlayerMoved(Player player) {
        String questId = playerActiveQuest.get(player.getUniqueId());
        if (questId == null) return;

        // Récupère la position depuis le cache — le NPC caché la met à jour via la task
        Location npcLoc = activeQuestLocations.get(questId);
        if (npcLoc == null) return;

        BossBar bar = playerBossBars.get(player.getUniqueId());
        if (bar == null) return;

        double dist     = player.getLocation().distance(npcLoc);
        double progress = Math.max(0.005, Math.min(1.0, 1.0 - (dist / MAX_DISTANCE)));

        bar.setProgress(progress);
        bar.setColor(dist < 20 ? BarColor.GREEN : dist < 100 ? BarColor.YELLOW : BarColor.RED);
    }

    private void destroyBossBar(Player player) {
        BossBar bar = playerBossBars.remove(player.getUniqueId());
        if (bar != null) {
            bar.removePlayer(player);
            bar.setVisible(false);
        }
    }

    /* =========================
       TASK — met à jour activeQuestLocations depuis le NPC caché
       ========================= */

    private void startNpcTrackerTask(CityQuest quest) {
        BukkitTask task = new BukkitRunnable() {
            private int     ticksWithoutNpc = 0;
            private boolean everFound       = false;

            @Override
            public void run() {
                // Utilise le NPC caché — zéro appel à getNPC()
                NPC npc = activeQuestNpcs.get(quest.id());

                if (npc != null && npc.isSpawned() && npc.getEntity() != null) {
                    everFound       = true;
                    ticksWithoutNpc = 0;
                    activeQuestLocations.put(quest.id(), npc.getEntity().getLocation());
                    return;
                }

                // NPC temporairement non spawné
                if (npc != null) {
                    ticksWithoutNpc++;
                } else {
                    // NPC null = quête terminée ou nettoyée, on arrête
                    cancel();
                    npcTrackerTasks.remove(quest.id());
                    return;
                }
            }
        }.runTaskTimer(plugin, 40L, TASK_TICKS);

        npcTrackerTasks.put(quest.id(), task);
    }

    /* =========================
       SPAWN SAFE
       ========================= */

    private Location findSafeLocation(int radius, World world) {
        Location center = getCityCenter(world);
        if (center == null) return null;
        Random random = new Random();
        for (int attempt = 0; attempt < 50; attempt++) {
            double angle = random.nextDouble() * 2 * Math.PI;
            double dist  = (radius / 2.0) + random.nextDouble() * (radius / 2.0);
            int x = (int) (center.getX() + Math.cos(angle) * dist);
            int z = (int) (center.getZ() + Math.sin(angle) * dist);
            if (!world.isChunkLoaded(x >> 4, z >> 4)) world.loadChunk(x >> 4, z >> 4);
            int y = world.getHighestBlockYAt(x, z);
            Location loc = new Location(world, x + 0.5, y + 1, z + 0.5);
            if (isSafe(loc)) return loc;
        }
        return null;
    }

    private boolean isSafe(Location loc) {
        World world = loc.getWorld();
        if (world == null) return false;
        org.bukkit.block.Block floor = world.getBlockAt(loc.getBlockX(), loc.getBlockY() - 1, loc.getBlockZ());
        if (!floor.getType().isSolid() || floor.isLiquid()) return false;
        org.bukkit.block.Block feet = world.getBlockAt(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        org.bukkit.block.Block head = world.getBlockAt(loc.getBlockX(), loc.getBlockY() + 1, loc.getBlockZ());
        if (!feet.getType().isAir() || !head.getType().isAir()) return false;
        if (cityManager.isChunkClaimed(feet.getChunk())) return false;
        return true;
    }

    private Location getCityCenter(World world) {
        List<long[]> chunks = cityManager.getClaimedChunkCoords(world.getName());
        if (chunks.isEmpty()) return null;
        double avgX = chunks.stream().mapToLong(c -> c[0]).average().orElse(0) * 16 + 8;
        double avgZ = chunks.stream().mapToLong(c -> c[1]).average().orElse(0) * 16 + 8;
        return new Location(world, avgX, world.getHighestBlockYAt((int) avgX, (int) avgZ) + 1, avgZ);
    }

    /* =========================
       UTILITAIRES
       ========================= */

    public boolean isQuestActive(String questId) {
        return activeQuestLocations.containsKey(questId);
    }

    public Location getActiveLocation(String questId) {
        return activeQuestLocations.get(questId);
    }

    public void onPlayerJoin(Player player) {
        for (Map.Entry<String, Location> entry : activeQuestLocations.entrySet()) {
            String questId = entry.getKey();
            CityTier tier  = CityTier.fromLevel(questManager.getCityLevel());
            for (CityQuest quest : tier.quests) {
                if (!quest.id().equals(questId)) continue;
                if (questManager.isCompleted(questId)) continue;
                playerActiveQuest.put(player.getUniqueId(), questId);
                giveQuestItem(player, quest);
                createBossBar(player, quest);
                player.sendMessage("§7🗺 Quête en cours : §e" + quest.displayName());
            }
        }
    }

    public void onPlayerQuit(Player player) {
        destroyBossBar(player);
        playerActiveQuest.remove(player.getUniqueId());
    }

    public void cleanup() {
        npcTrackerTasks.values().forEach(BukkitTask::cancel);
        npcTrackerTasks.clear();
        activeQuestLocations.clear();
        activeQuestNpcs.clear();
        playerActiveQuest.clear();
        playerBossBars.forEach((uuid, bar) -> bar.setVisible(false));
        playerBossBars.clear();
    }
}