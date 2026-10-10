package com.citycore.quest.city;

import com.citycore.city.CityManager;
import com.citycore.npc.CityNPC;
import com.citycore.npc.NPCDataManager;
import com.citycore.npc.NPCManager;
import com.citycore.util.GroundFinder;
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
    /** Quêtes dont le lieu d'apparition est en cours de recherche (en arrière-plan). */
    private final Set<String>             searching            = new HashSet<>();
    /** Essais de la recherche du lieu d'apparition. */
    private static final int              SPAWN_ATTEMPTS       = 48;

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

        // PNJ déjà présent dans le monde (même dans un chunk déchargé) : on part de lui
        NPC npc = npcManager.getNPC(quest.targetNpc());
        if (npc != null) {
            Location where = npc.isSpawned() && npc.getEntity() != null
                    ? npc.getEntity().getLocation() : npc.getStoredLocation();
            if (where != null) {
                startQuest(quest, npc, where);
                return;
            }
        }

        // Sinon, lieu d'apparition cherché en arrière-plan : charger (voire générer) des chunks
        // lointains sur le fil principal gelait le serveur
        String name = ChatColor.stripColor(quest.targetNpc().displayName);
        if (!searching.add(quest.id())) {
            trigger.sendMessage("§7🗺 On cherche encore la trace de " + name + "...");
            return;
        }
        trigger.sendMessage("§7🗺 On cherche la trace de " + name + "...");
        findSafeLocationAsync(quest.spawnRadius(), trigger.getWorld(), quest.biomes(), spawnLoc -> {
            searching.remove(quest.id());
            if (isQuestActive(quest.id()) || questManager.isCompleted(quest.id())) return;
            if (spawnLoc == null) {
                if (trigger.isOnline()) trigger.sendMessage("§c❌ Impossible de trouver un endroit sûr (explorez davantage autour de la ville).");
                return;
            }
            NPC existing = npcManager.getNPC(quest.targetNpc());
            NPC spawned  = existing != null ? existing : npcManager.spawnNPC(quest.targetNpc(), spawnLoc);
            startQuest(quest, spawned, spawnLoc);
        });
    }

    /** Lance la piste : PNJ placé, annonce, objet de quête et barre pour chaque joueur. */
    private void startQuest(CityQuest quest, NPC npc, Location spawnLoc) {
        questManager.markRecruitStarted(quest);

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

    /**
     * Étape 2 du recrutement : le PNJ est entré en ville. La quête se termine quand il
     * est installé dans un bâtiment (étape 3, détectée par la synchronisation).
     */
    public void onNpcArrived(CityNPC npc) {
        for (CityQuest quest : questManager.displayedQuests()) {
            if (quest.type() != CityQuestType.FIND_NPC) continue;
            if (quest.targetNpc() != npc) continue;
            if (questManager.isCompleted(quest.id())) continue;

            questManager.setProgress(quest.id(), 2, false);
            activeQuestLocations.remove(quest.id());
            activeQuestNpcs.remove(quest.id());

            BukkitTask task = npcTrackerTasks.remove(quest.id());
            if (task != null) task.cancel();

            for (Player p : Bukkit.getOnlinePlayers()) {
                // Seuls les joueurs qui suivaient CETTE piste la perdent (une autre recherche
                // peut être en cours en parallèle)
                if (quest.id().equals(playerActiveQuest.get(p.getUniqueId()))) {
                    removeQuestItem(p);
                    destroyBossBar(p);
                    playerActiveQuest.remove(p.getUniqueId());
                }
                p.sendMessage("");
                p.sendMessage("§a🏰 " + npc.displayName + " §aest arrivé en ville !");
                p.sendMessage("§7Parlez-lui pour l'installer dans un bâtiment et terminer : "
                        + quest.displayName());
                p.sendMessage("");
            }
            break;
        }
    }

    /* =========================
       ITEM DE QUÊTE
       ========================= */

    /**
     * Donne la « piste » de recherche. Elle va dans le dernier emplacement de la barre
     * seulement s'il est libre : on n'écrase jamais l'objet d'un joueur.
     */
    public void giveQuestItem(Player player, CityQuest quest) {
        removeQuestItem(player); // une seule piste à la fois
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta  meta = item.getItemMeta();
        meta.setDisplayName("§e🗺 " + quest.displayName());
        meta.setLore(List.of(
                "§7Trouver §e" + quest.targetNpc().displayName,
                "§7Suivez la barre en haut de l'écran."
        ));
        item.setItemMeta(meta);
        ItemStack current = player.getInventory().getItem(COMPASS_SLOT);
        if (current == null || current.getType().isAir()) {
            player.getInventory().setItem(COMPASS_SLOT, item);
        } else {
            player.getInventory().addItem(item).values().forEach(rest ->
                    player.sendMessage("§7🗺 Inventaire plein : suivez la barre en haut de l'écran."));
        }
    }

    /** Retire la piste où qu'elle soit dans l'inventaire. */
    private void removeQuestItem(Player player) {
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item == null || item.getType() != Material.PAPER) continue;
            if (!item.hasItemMeta() || !item.getItemMeta().hasDisplayName()) continue;
            if (item.getItemMeta().getDisplayName().contains("🗺")) player.getInventory().setItem(i, null);
        }
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

        // Autre dimension (Nether, End) : distance non mesurable, piste « froide »
        if (!player.getWorld().equals(npcLoc.getWorld())) {
            bar.setProgress(0.005);
            bar.setColor(BarColor.RED);
            return;
        }

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

    /**
     * Cherche en arrière-plan un point sûr hors de la ville, de préférence dans un des biomes du
     * personnage (le pêcheur sur une plage, le forgeron dans la neige…).
     *
     * Chaque essai charge son chunk de façon asynchrone puis l'examine sur le fil principal ;
     * les chunks jamais générés sont ignorés (générer du terrain avec des mods prend parfois
     * plusieurs secondes par chunk). Le premier point sûr hors biome sert de repli.
     */
    private void findSafeLocationAsync(int radius, World world, Set<org.bukkit.block.Biome> biomes,
                                       java.util.function.Consumer<Location> done) {
        double[] center = getCityCenterXZ(world);
        if (center == null) {
            done.accept(null);
            return;
        }
        Random random = new Random();
        List<int[]> candidates = new ArrayList<>();
        for (int attempt = 0; attempt < SPAWN_ATTEMPTS; attempt++) {
            double angle = random.nextDouble() * 2 * Math.PI;
            double dist  = (radius / 2.0) + random.nextDouble() * (radius / 2.0);
            candidates.add(new int[]{(int) (center[0] + Math.cos(angle) * dist), (int) (center[1] + Math.sin(angle) * dist)});
        }
        tryCandidate(world, biomes, candidates, 0, null, done);
    }

    private void tryCandidate(World world, Set<org.bukkit.block.Biome> biomes, List<int[]> candidates,
                              int index, Location fallback, java.util.function.Consumer<Location> done) {
        // Essais épuisés : repli sur un point sûr hors biome (ou rien)
        int i = index;
        while (i < candidates.size() && !world.isChunkGenerated(candidates.get(i)[0] >> 4, candidates.get(i)[1] >> 4)) i++;
        if (i >= candidates.size()) {
            done.accept(fallback);
            return;
        }
        int[] c = candidates.get(i);
        int next = i + 1;
        world.getChunkAtAsync(c[0] >> 4, c[1] >> 4, false).whenComplete((chunk, error) ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Location loc = chunk == null ? null : GroundFinder.groundAt(world, c[0], c[1]);
                    Location keep = fallback;
                    if (loc != null && isSafe(loc)) {
                        // Sol naturel (sous les arbres, jamais sur leur cime) dans un biome du personnage
                        if (biomes.isEmpty() || biomes.contains(world.getBiome(c[0], loc.getBlockY(), c[1]))) {
                            done.accept(loc);
                            return;
                        }
                        if (keep == null) keep = loc;
                    }
                    tryCandidate(world, biomes, candidates, next, keep, done);
                }));
    }

    private boolean isSafe(Location loc) {
        World world = loc.getWorld();
        if (world == null) return false;
        org.bukkit.block.Block floor = world.getBlockAt(loc.getBlockX(), loc.getBlockY() - 1, loc.getBlockZ());
        if (!floor.getType().isSolid() || floor.isLiquid()) return false;
        org.bukkit.block.Block feet = world.getBlockAt(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        org.bukkit.block.Block head = world.getBlockAt(loc.getBlockX(), loc.getBlockY() + 1, loc.getBlockZ());
        // Herbes hautes et fleurs acceptées ; feuillage, tronc et liquide refusés
        if (!feet.isPassable() || feet.isLiquid() || !head.isPassable() || head.isLiquid()) return false;
        if (org.bukkit.Tag.LEAVES.isTagged(floor.getType()) || org.bukkit.Tag.LOGS.isTagged(floor.getType())) return false;
        if (cityManager.isChunkClaimed(feet.getChunk())) return false;
        return true;
    }

    /** Centre de la ville (x, z), sans lire de bloc (aucun chunk à charger). */
    private double[] getCityCenterXZ(World world) {
        List<long[]> chunks = cityManager.getClaimedChunkCoords(world.getName());
        if (chunks.isEmpty()) return null;
        double avgX = chunks.stream().mapToLong(c -> c[0]).average().orElse(0) * 16 + 8;
        double avgZ = chunks.stream().mapToLong(c -> c[1]).average().orElse(0) * 16 + 8;
        return new double[]{avgX, avgZ};
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
            for (CityQuest quest : questManager.displayedQuests()) {
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