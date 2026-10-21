package com.citycore.raid;

import org.bukkit.Difficulty;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Section raids de config.yml (plan « Raids ») : mondes des raids et repérage des cibles. Les
 * réglages des phases suivantes (camp, rassemblement, niveaux, récompenses) s'y ajouteront.
 */
public final class RaidConfig {

    /**
     * Un monde des raids.
     *
     * @param id     surface, nether ou end (clé de la section mondes)
     * @param active false : monde jamais créé, ses cibles sont masquées
     */
    public record WorldSpec(String id, boolean active, String name, long seed, World.Environment environment) {}

    public final boolean active;
    public final WorldSpec surface;
    public final WorldSpec nether;
    public final WorldSpec end;
    public final boolean resetAtStartup;
    /** Blocs entre les points de départ de deux recherches successives. */
    public final int spacing;
    /** Rayon de la recherche d'un exemplaire naturel de la cible (chunks). */
    public final int searchRadius;
    /** Marge générée autour de la boîte de la cible (blocs : abords, camp). */
    public final int margin;
    public final long time;
    public final Difficulty difficulty;

    /** Blocs entre le bord de la cible et le camp. */
    public final int campDistance;
    /** Rassemblement : rayon du cercle autour d'Alderic, durée du compte à rebours, joueurs requis. */
    public final int gatherRadius;
    public final int gatherSeconds;
    public final int minPlayers;
    /** Temps pour souffler après la fin, avant le retour. */
    public final int bilanSeconds;
    /** Invulnérabilité après une réapparition au camp (secondes). */
    public final int respawnInvulnerableSeconds;
    /** Commandes refusées dans le monde des raids (sans « / », en minuscules). */
    public final List<String> blockedCommands;
    /** Niveaux 1 à 7. */
    private final Map<Integer, Level> levels = new LinkedHashMap<>();

    /**
     * Un niveau de difficulté (PLAN_RAIDS §4.1, §6.1).
     *
     * @param defenders défenseurs pour un joueur : minimum, maximum
     * @param chronoMinutes durée de l'assaut
     * @param reinforcements vagues de renfort
     * @param cityReward récompense dans la caisse de la ville ; @param playerReward par joueur
     */
    public record Level(int number, String name, int chapter, int[] defenders, int chronoMinutes, int reinforcements,
                        int cooldownMinutes, int cityReward, int playerReward) {}

    public RaidConfig(JavaPlugin plugin) {
        ConfigurationSection root = plugin.getConfig().getConfigurationSection("raids");
        if (root == null) root = plugin.getConfig().createSection("raids");
        active = root.getBoolean("active", true);

        ConfigurationSection worlds = root.getConfigurationSection("mondes");
        if (worlds == null) worlds = root.createSection("mondes");
        surface = world(worlds, "surface", "citycore_raids", 20261021L, World.Environment.NORMAL);
        nether  = world(worlds, "nether", "citycore_raids_nether", 20261022L, World.Environment.NETHER);
        end     = world(worlds, "end", "citycore_raids_end", 20261023L, World.Environment.THE_END);
        resetAtStartup = worlds.getBoolean("reinitialiser-au-demarrage", true);
        spacing        = Math.max(512, worlds.getInt("espacement", 4096));
        searchRadius   = Math.max(8, Math.min(256, worlds.getInt("rayon-recherche", 100)));
        margin         = Math.max(0, Math.min(96, worlds.getInt("marge", 32)));
        time           = worlds.getLong("heure", 6000L);
        Difficulty diff;
        try {
            diff = Difficulty.valueOf(worlds.getString("difficulte", "NORMAL").trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            diff = Difficulty.NORMAL;
        }
        difficulty = diff;

        ConfigurationSection camp = root.getConfigurationSection("camp");
        campDistance = Math.max(12, Math.min(64, camp == null ? 32 : camp.getInt("distance", 32)));
        ConfigurationSection gather = root.getConfigurationSection("rassemblement");
        gatherRadius  = Math.max(3, gather == null ? 10 : gather.getInt("rayon", 10));
        gatherSeconds = Math.max(5, gather == null ? 30 : gather.getInt("secondes", 30));
        minPlayers    = Math.max(1, gather == null ? 1 : gather.getInt("joueurs-min", 1));
        bilanSeconds  = Math.max(0, root.getInt("bilan-secondes", 20));
        ConfigurationSection players = root.getConfigurationSection("joueurs");
        respawnInvulnerableSeconds = Math.max(0, players == null ? 3 : players.getInt("invulnerable-reapparition-secondes", 3));
        blockedCommands = players == null ? List.of() : players.getStringList("commandes-bloquees").stream()
                .map(c -> c.trim().toLowerCase(Locale.ROOT).replaceFirst("^/", "")).toList();

        String[] names = {"Escarmouche", "Coup de main", "Expédition", "Assaut", "Offensive", "Grande offensive", "Légende"};
        int[] chronos = {6, 8, 10, 15, 18, 20, 25};
        ConfigurationSection lv = root.getConfigurationSection("niveaux");
        for (int n = 1; n <= 7; n++) {
            ConfigurationSection s = lv == null ? null : lv.getConfigurationSection(String.valueOf(n));
            List<Integer> def = s == null ? List.of() : s.getIntegerList("defenseurs");
            levels.put(n, new Level(n, s == null ? names[n - 1] : s.getString("nom", names[n - 1]),
                    s == null ? n + 1 : s.getInt("chapitre", n + 1),
                    def.size() >= 2 ? new int[]{def.get(0), def.get(1)} : new int[]{3, 6},
                    Math.max(1, s == null ? chronos[n - 1] : s.getInt("chrono-minutes", chronos[n - 1])),
                    s == null ? 0 : s.getInt("renforts", 0), s == null ? 30 : s.getInt("recharge-minutes", 30),
                    s == null ? 0 : s.getInt("caisse", 0), s == null ? 0 : s.getInt("joueur", 0)));
        }
    }

    /** Niveau 1 à 7 (borné). */
    public Level level(int n) {
        return levels.get(Math.max(1, Math.min(7, n)));
    }

    private static WorldSpec world(ConfigurationSection worlds, String id, String defName, long defSeed,
                                   World.Environment env) {
        ConfigurationSection s = worlds.getConfigurationSection(id);
        if (s == null) return new WorldSpec(id, true, defName, defSeed, env);
        // Nom en minuscules : c'est aussi la clé de la dimension (minecraft:<nom>)
        String name = s.getString("nom", defName).trim().toLowerCase(Locale.ROOT);
        return new WorldSpec(id, s.getBoolean("active", true), name.isEmpty() ? defName : name,
                s.getLong("graine", defSeed), env);
    }
}
