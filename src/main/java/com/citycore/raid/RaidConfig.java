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
 * Section raids de config.yml (plan « Raids ») : mondes des raids et repérage des cibles, camp,
 * rassemblement, sécurité des joueurs, niveaux, défenseurs et leur force, objectifs, récompenses.
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
    /** Gabarits de camp des mods (un au hasard parmi ceux présents), vide = camp du plugin. */
    public final List<String> campModels;
    /** Petits bateaux des mods pour le camp sur l'eau (un au hasard), vide = radeau du plugin. */
    public final List<Boat> campBoats;

    /**
     * Bateau du camp sur l'eau.
     *
     * @param waterline couches du gabarit sous la surface de l'eau (la coque qui trempe)
     */
    public record Boat(String id, int waterline) {}
    /** Skin du héraut du camp : {value, signature} (mineskin.org), ou null (skin de Minecraft). */
    public final String[] heraldSkin;
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
    /** Force des défenseurs, par niveau. */
    private final Map<Integer, Strength> strengths = new LinkedHashMap<>();

    /** Défenseurs : nombre × (1 + perPlayer × (joueurs - 1)), au plus maxAlive vivants. */
    public final double perPlayer;
    public final int maxAlive;
    /** Objectifs : part de la garnison à abattre, rayon de la place dégagée, part du chrono du bonus éclair. */
    public final double garrisonPart;
    public final int bellRadius;
    public final double eclairFraction;
    /** Objectif « tenir » : secondes à tenir la place, rayon de la place. */
    public final int holdSeconds;
    public final int holdRadius;
    private final Map<String, Double> weights = new LinkedHashMap<>();
    /** Récompenses : bonus par bonus réussi, renommée par victoire et plafond, force par victoire et plafond. */
    public final double bonusRate;
    public final double renownPerVictory;
    public final double renownMax;
    public final double strengthPerVictory;
    public final double strengthMax;

    /**
     * Force des défenseurs d'un niveau.
     *
     * @param armor aucune, cuir, maille, fer, diamant ou netherite
     */
    public record Strength(double health, double damage, String armor) {}

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
        ConfigurationSection skin = camp == null ? null : camp.getConfigurationSection("heraut-skin");
        String skinValue = skin == null ? "" : skin.getString("value", "").trim();
        heraldSkin = skinValue.isEmpty() ? null : new String[]{skinValue, skin.getString("signature", "").trim()};
        campModels = camp == null || !camp.contains("modeles")
                ? List.of("valarian_conquest:bandit_camp")
                : List.copyOf(camp.getStringList("modeles"));
        List<Boat> boats = new java.util.ArrayList<>();
        if (camp == null || !camp.contains("navires")) {
            boats.add(new Boat("kaisyn:ships/village_ocean/pieces/small_ship/small_ship_4", 2));
            boats.add(new Boat("kaisyn:ships/village_ocean/pieces/escort/escort_1", 1));
            boats.add(new Boat("kaisyn:ships/village_ocean/pieces/escort/escort_2", 1));
        } else {
            for (Map<?, ?> m : camp.getMapList("navires")) {
                Object id = m.get("id"), line = m.get("flottaison");
                if (id == null) continue;
                int wl = line instanceof Number n ? n.intValue() : 1;
                boats.add(new Boat(id.toString().trim(), Math.max(0, Math.min(8, wl))));
            }
        }
        campBoats = List.copyOf(boats);
        ConfigurationSection gather = root.getConfigurationSection("rassemblement");
        gatherRadius  = Math.max(3, gather == null ? 10 : gather.getInt("rayon", 10));
        gatherSeconds = Math.max(5, gather == null ? 30 : gather.getInt("secondes", 30));
        minPlayers    = Math.max(1, gather == null ? 1 : gather.getInt("joueurs-min", 1));
        bilanSeconds  = Math.max(0, root.getInt("bilan-secondes", 20));
        ConfigurationSection players = root.getConfigurationSection("joueurs");
        respawnInvulnerableSeconds = Math.max(0, players == null ? 3 : players.getInt("invulnerable-reapparition-secondes", 3));
        blockedCommands = players == null ? List.of() : players.getStringList("commandes-bloquees").stream()
                .map(c -> c.trim().toLowerCase(Locale.ROOT).replaceFirst("^/", "")).toList();

        ConfigurationSection defs = root.getConfigurationSection("defenseurs");
        perPlayer = Math.max(0, defs == null ? 0.5 : defs.getDouble("par-joueur", 0.5));
        maxAlive  = Math.max(5, defs == null ? 60 : defs.getInt("max", 60));
        double[] hp = {1.0, 1.0, 1.1, 1.2, 1.3, 1.4, 1.5};
        double[] dmg = {1.0, 1.0, 1.1, 1.15, 1.2, 1.25, 1.3};
        String[] arm = {"aucune", "aucune", "cuir", "cuir", "maille", "maille", "fer"};
        ConfigurationSection force = root.getConfigurationSection("force");
        for (int n = 1; n <= 7; n++) {
            ConfigurationSection f = force == null ? null : force.getConfigurationSection(String.valueOf(n));
            strengths.put(n, new Strength(f == null ? hp[n - 1] : f.getDouble("vie", hp[n - 1]),
                    f == null ? dmg[n - 1] : f.getDouble("degats", dmg[n - 1]),
                    f == null ? arm[n - 1] : f.getString("armure", arm[n - 1]).trim().toLowerCase(Locale.ROOT)));
        }
        ConfigurationSection obj = root.getConfigurationSection("objectifs");
        garrisonPart   = Math.max(0.1, Math.min(1.0, obj == null ? 0.8 : obj.getDouble("garnison-part", 0.8)));
        bellRadius     = Math.max(4, obj == null ? 16 : obj.getInt("cloche-rayon", 16));
        eclairFraction = Math.max(0.1, Math.min(1.0, obj == null ? 0.5 : obj.getDouble("eclair", 0.5)));
        holdSeconds    = Math.max(10, obj == null ? 45 : obj.getInt("tenir-secondes", 45));
        holdRadius     = Math.max(3, obj == null ? 8 : obj.getInt("tenir-rayon", 8));
        ConfigurationSection w = obj == null ? null : obj.getConfigurationSection("poids");
        for (String k : List.of("garnison", "cloche", "bannieres", "cages", "liberer", "chef", "aborder", "maitre", "tenir", "tours")) {
            double d = k.equals("garnison") ? 3 : k.equals("chef") || k.equals("maitre") || k.equals("tours") ? 2 : 1;
            weights.put(k, Math.max(0.1, w == null ? d : w.getDouble(k, d)));
        }
        ConfigurationSection rw = root.getConfigurationSection("recompenses");
        bonusRate          = rw == null ? 0.15 : rw.getDouble("bonus", 0.15);
        renownPerVictory   = rw == null ? 0.05 : rw.getDouble("renommee-par-victoire", 0.05);
        renownMax          = rw == null ? 0.25 : rw.getDouble("renommee-max", 0.25);
        strengthPerVictory = rw == null ? 0.05 : rw.getDouble("force-par-victoire", 0.05);
        strengthMax        = rw == null ? 0.25 : rw.getDouble("force-max", 0.25);

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

    public Strength strength(int n) {
        return strengths.get(Math.max(1, Math.min(7, n)));
    }

    /** Poids d'un objectif dans la défiance. */
    public double weight(String objective) {
        return weights.getOrDefault(objective, 1.0);
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
