package com.citycore.raid;

import org.bukkit.Difficulty;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;

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
