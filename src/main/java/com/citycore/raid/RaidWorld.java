package com.citycore.raid;

import net.kyori.adventure.util.TriState;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * Un monde réservé aux raids (PLAN_RAIDS §2.1 à §2.3) : monde Bukkit créé par le plugin avec
 * {@link WorldCreator}, jetable (pas de sauvegarde automatique), effacé et recréé au démarrage et
 * par /city raid purge. Ses structures naissent de la génération, comme dans un monde neuf (les
 * cibles y sont repérées, voir {@link RaidSites}).
 *
 * Sous Youer, le monde peut déjà être chargé avant le plugin (Youer note les mondes Bukkit dans
 * youer-config/worlds.yml) : il est alors vidé de ses joueurs et déchargé avant l'effacement.
 */
public final class RaidWorld {

    private final JavaPlugin plugin;
    private final RaidConfig config;
    private final RaidConfig.WorldSpec spec;
    private World world;
    /** Dernier effacement : réussi, ou raison de l'échec (journal, /city raid zones). */
    private String lastReset = "jamais";

    /** Où renvoyer les joueurs d'un monde des raids qu'on décharge (auprès d'Alderic, voir RaidManager). */
    private java.util.function.Supplier<Location> fallback;

    public RaidWorld(JavaPlugin plugin, RaidConfig config, RaidConfig.WorldSpec spec) {
        this.plugin = plugin;
        this.config = config;
        this.spec   = spec;
    }

    public void setFallback(java.util.function.Supplier<Location> fallback) {
        this.fallback = fallback;
    }

    public RaidConfig.WorldSpec spec() { return spec; }

    /** Le monde, ou null s'il n'est pas (encore) créé. */
    public World world() {
        if (world != null && Bukkit.getWorld(world.getUID()) == null) world = null; // déchargé entre-temps
        return world;
    }

    public boolean is(World w) {
        return w != null && w.getName().equalsIgnoreCase(spec.name());
    }

    /** Clé de la dimension pour « execute in » (minecraft:citycore_raids sur Paper). */
    public String dimensionKey() {
        World w = world();
        return w == null ? "minecraft:" + spec.name() : w.getKey().toString();
    }

    public String lastReset() { return lastReset; }

    /**
     * Crée le monde, après l'avoir effacé si {@code wipe} (démarrage avec
     * reinitialiser-au-demarrage, /city raid purge).
     *
     * @return le monde, ou null si la création a échoué
     */
    public World open(boolean wipe) {
        World loaded = Bukkit.getWorld(spec.name());
        File folder = loaded != null ? loaded.getWorldFolder() : new File(Bukkit.getWorldContainer(), spec.name());
        if (wipe) {
            if (loaded != null) {
                evacuate(loaded);
                if (!Bukkit.unloadWorld(loaded, false)) {
                    lastReset = "échec du déchargement (monde gardé tel quel)";
                    plugin.getLogger().warning("⚔ [Raids] Impossible de décharger " + spec.name()
                            + " pour l'effacer : le monde est gardé tel quel.");
                    world = loaded;
                    applyRules(world);
                    return world;
                }
            }
            String err = delete(folder);
            lastReset = err == null ? "effacé au " + (loaded != null ? "déchargement" : "démarrage") : err;
            if (err != null) plugin.getLogger().warning("⚔ [Raids] " + spec.name() + " : " + err);
        } else if (loaded != null) {
            world = loaded;
            applyRules(world);
            return world;
        }

        long t0 = System.currentTimeMillis();
        WorldCreator creator = new WorldCreator(spec.name())
                .environment(spec.environment())
                .seed(spec.seed())
                .type(WorldType.NORMAL)
                .generateStructures(true) // les cibles naissent de la génération des mods, adaptées au terrain
                .keepSpawnLoaded(TriState.FALSE);
        try {
            world = creator.createWorld();
        } catch (RuntimeException | LinkageError e) {
            plugin.getLogger().severe("⚔ [Raids] Création du monde " + spec.name() + " impossible : " + e);
            world = null;
            return null;
        }
        if (world == null) {
            plugin.getLogger().severe("⚔ [Raids] Création du monde " + spec.name() + " refusée par le serveur.");
            return null;
        }
        applyRules(world);
        plugin.getLogger().info("⚔ [Raids] Monde " + spec.name() + " prêt en " + (System.currentTimeMillis() - t0)
                + " ms (dimension " + world.getKey() + ", dossier " + world.getWorldFolder().getPath()
                + ", " + lastReset + ", structures naturelles : " + (world.canGenerateStructures() ? "oui" : "NON") + ").");
        return world;
    }

    /**
     * Arrêt du serveur : joueurs renvoyés au monde principal, monde déchargé **sans sauvegarde**
     * (le serveur n'écrit pas ses chunks en s'arrêtant) puis son dossier effacé : rien du monde
     * des raids ne reste sur le disque. Si le déchargement est refusé (joueur resté dans le
     * monde), le dossier est gardé et effacé au démarrage suivant.
     */
    public void discard() {
        World w = world();
        if (w == null) return;
        File folder = w.getWorldFolder();
        evacuate(w);
        boolean unloaded;
        try {
            unloaded = Bukkit.unloadWorld(w, false);
        } catch (RuntimeException | LinkageError e) {
            plugin.getLogger().warning("⚔ [Raids] Déchargement de " + spec.name() + " à l'arrêt en erreur : " + e);
            unloaded = false;
        }
        if (!unloaded) {
            plugin.getLogger().info("⚔ [Raids] " + spec.name() + " gardé jusqu'au prochain démarrage (effacé alors).");
            return;
        }
        world = null;
        String err = delete(folder);
        plugin.getLogger().info("⚔ [Raids] " + spec.name() + " déchargé sans sauvegarde et " + (err == null ? "effacé." : err + "."));
    }

    /**
     * Règles du monde (§2.2) : rien n'apparaît ni ne se déclenche de lui-même (raids illageois,
     * patrouilles, marchands), heure et météo figées, inventaire gardé à la mort.
     */
    private void applyRules(World w) {
        w.setAutoSave(false); // monde jetable
        w.setDifficulty(config.difficulty);
        w.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        w.setGameRule(GameRule.DO_PATROL_SPAWNING, false);
        w.setGameRule(GameRule.DO_TRADER_SPAWNING, false);
        w.setGameRule(GameRule.DO_INSOMNIA, false);
        w.setGameRule(GameRule.DISABLE_RAIDS, true); // Mauvais présage d'un capitaine pillard : pas de raid illageois
        w.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        w.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        w.setGameRule(GameRule.KEEP_INVENTORY, true); // décision : inventaire et expérience gardés à la mort
        w.setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, false);
        w.setGameRule(GameRule.MOB_GRIEFING, false);
        w.setGameRule(GameRule.DO_FIRE_TICK, false);
        w.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false);
        w.setGameRule(GameRule.SPAWN_CHUNK_RADIUS, 0); // pas de chunks d'apparition gardés en mémoire
        w.setTime(config.time);
        w.setStorm(false);
        w.setThundering(false);
        w.setClearWeatherDuration(Integer.MAX_VALUE);
    }

    /** Joueurs du monde renvoyés à Valmont, auprès d'Alderic (avant un déchargement). */
    private void evacuate(World w) {
        Location to = fallback != null ? fallback.get() : null;
        if (to == null) to = mainWorld().getSpawnLocation();
        for (Player p : w.getPlayers()) {
            p.teleport(to);
            p.sendMessage("§7⚔ Le monde des raids est remis à zéro : retour à Valmont.");
        }
    }

    /** Monde principal : le premier monde normal qui n'est pas ce monde des raids. */
    public World mainWorld() {
        for (World w : Bukkit.getWorlds()) {
            if (w.getEnvironment() == World.Environment.NORMAL && !is(w)) return w;
        }
        return Bukkit.getWorlds().get(0);
    }

    /** Supprime le dossier du monde ; renvoie null si c'est fait, sinon la raison. */
    private static String delete(File folder) {
        if (!folder.exists()) return null;
        try (Stream<Path> walk = Files.walk(folder.toPath())) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    throw new IllegalStateException(p.getFileName() + " : " + e.getMessage(), e);
                }
            });
            return null;
        } catch (IOException | IllegalStateException e) {
            return "effacement incomplet (" + e.getMessage() + ")";
        }
    }
}
