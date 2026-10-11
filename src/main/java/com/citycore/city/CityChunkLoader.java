package com.citycore.city;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Garde la ville en vie où que soient les joueurs : tant qu'au moins un joueur est connecté,
 * chaque chunk revendiqué reste chargé et actif (ticket de chargement du plugin), comme si un
 * joueur s'y trouvait. Habitants, gardes, artisans, employés et sièges continuent donc de vivre
 * même si tout le monde est parti explorer. Quand plus personne n'est connecté, les tickets sont
 * rendus : le serveur se repose.
 *
 * Les chunks sont chargés en arrière-plan (sans à-coup) et la liste est revue toutes les 30 s
 * (nouvelles parcelles, parcelles rendues). Limite : les règles du jeu qui demandent un joueur à
 * proximité (pousse des cultures, apparition des monstres) ne changent pas.
 */
public final class CityChunkLoader implements Listener {

    private static final long PERIOD_TICKS = 20L * 30;

    private final JavaPlugin  plugin;
    private final CityManager city;
    /** Chunks tenus par monde (clé = x, z). */
    private final Map<String, Set<Long>> held = new java.util.HashMap<>();

    public CityChunkLoader(JavaPlugin plugin, CityManager city) {
        this.plugin = plugin;
        this.city   = city;
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::sync, 40L, PERIOD_TICKS);
    }

    /** Arrêt du serveur : tous les tickets sont rendus. */
    public void shutdown() {
        for (World w : Bukkit.getWorlds()) w.removePluginChunkTickets(plugin);
        held.clear();
    }

    /** Nombre de chunks de la ville tenus chargés (bilan). */
    public int heldCount() {
        return held.values().stream().mapToInt(Set::size).sum();
    }

    /** Met les tickets en accord avec les parcelles de la ville et la présence de joueurs. */
    public void sync() {
        boolean someone = !Bukkit.getOnlinePlayers().isEmpty();
        for (World w : Bukkit.getWorlds()) {
            Set<Long> wanted = new HashSet<>();
            if (someone) {
                for (long[] c : city.getClaimedChunkCoords(w.getName())) wanted.add(key((int) c[0], (int) c[1]));
            }
            Set<Long> current = held.computeIfAbsent(w.getName(), k -> new HashSet<>());
            // Parcelles rendues (ou plus personne en ligne) : le chunk peut se décharger
            for (Long k : new HashSet<>(current)) {
                if (wanted.contains(k)) continue;
                w.removePluginChunkTicket(x(k), z(k), plugin);
                current.remove(k);
            }
            // Nouvelles parcelles : chargées en arrière-plan, puis tenues
            for (Long k : wanted) {
                if (current.contains(k)) continue;
                current.add(k);
                int x = x(k), z = z(k);
                w.getChunkAtAsync(x, z).thenAccept((Chunk chunk) -> {
                    // Entre-temps, plus personne en ligne ou parcelle rendue : on ne tient pas le chunk
                    if (current.contains(k)) w.addPluginChunkTicket(x, z, plugin);
                });
            }
        }
    }

    /** Un joueur arrive : la ville se réveille aussitôt. */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, this::sync, 20L);
    }

    /** Le dernier joueur part : la ville s'endort (tickets rendus). */
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Collection<? extends org.bukkit.entity.Player> online = Bukkit.getOnlinePlayers();
            if (online.isEmpty()) sync();
        }, 20L);
    }

    private static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }

    private static int x(long key) {
        return (int) (key >> 32);
    }

    private static int z(long key) {
        return (int) key;
    }
}
