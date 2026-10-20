package com.citycore.city;

import com.citycore.integration.ModIntegrations;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Publie les parcelles de la ville pour les cartes des mods (JourneyMap) : le fichier
 * {@code plugins/CityCore/carte.json} décrit le nom, le style du tracé et les chunks revendiqués
 * de chaque dimension. Le mod serveur « CityCore — Compagnon » (dossier {@code companions/}) le
 * relit et dessine la ville sur la carte de chaque joueur (chaque parcelle teintée, contour
 * extérieur, nom, quadrillage facultatif) ; le plugin n'a ainsi aucune dépendance vers un mod.
 *
 * Le fichier est réécrit dès qu'une parcelle est revendiquée ou rendue (et à la fondation), et
 * revu toutes les 30 s par sécurité (nom, habitants de la bulle, réglages) ; il n'est réécrit que
 * s'il a changé. Réglages : section {@code carte} de config.yml, relus par
 * {@code /city carte recharger}.
 */
public final class CityMapExport {

    private static final long PERIOD_TICKS = 20L * 30;
    /** Couleur par défaut : l'or de Valmont (interfaces, titres). */
    private static final String DEFAULT_COLOR = "F1CF5E";
    /** Identifiants des mods, pour le bilan de /city carte. */
    private static final String COMPANION_MOD = "citycore_companion";
    private static final String JOURNEYMAP_MOD = "journeymap";

    private final JavaPlugin  plugin;
    private final CityManager city;
    private final Path        file;
    /** Dernier contenu écrit (évite de réécrire un fichier identique). */
    private String last;
    /** Réglages de la section carte. */
    private Settings settings;
    /** Parcelles publiées au dernier export, et date de la dernière écriture. */
    private int exported;
    private long writtenAt;
    /** Un export est déjà prévu au prochain tick (plusieurs claims d'affilée = une écriture). */
    private boolean exportScheduled;

    /**
     * Section {@code carte} de config.yml.
     *
     * @param gridColor couleur du quadrillage (RRGGBB), ou null pour la couleur de la ville
     */
    private record Settings(boolean enabled, String name, String color, double fill, double stroke, double width,
                            boolean minimap, boolean grid, String gridColor, double gridOpacity, double gridWidth) {}

    public CityMapExport(JavaPlugin plugin, CityManager city) {
        this.plugin = plugin;
        this.city   = city;
        this.file   = plugin.getDataFolder().toPath().resolve("carte.json");
        reload();
    }

    /** Relit la section carte de config.yml (sur le disque) ; la carte suit au prochain export. */
    public void reload() {
        java.io.File cfg = new java.io.File(plugin.getDataFolder(), "config.yml");
        ConfigurationSection root = cfg.exists() ? YamlConfiguration.loadConfiguration(cfg) : plugin.getConfig();
        String gridColor = root.getString("carte.quadrillage-couleur", "");
        settings = new Settings(
                root.getBoolean("carte.activee", true),
                root.getString("carte.nom", ""),
                normalizeColor(root.getString("carte.couleur", DEFAULT_COLOR), DEFAULT_COLOR),
                root.getDouble("carte.remplissage", 0.15),
                root.getDouble("carte.bordure", 0.9),
                root.getDouble("carte.epaisseur", 2.0),
                root.getBoolean("carte.minicarte", true),
                root.getBoolean("carte.quadrillage", true),
                gridColor == null || gridColor.isBlank() ? null : normalizeColor(gridColor, null),
                root.getDouble("carte.quadrillage-opacite", 0.35),
                root.getDouble("carte.quadrillage-epaisseur", 1.0));
    }

    /** Bilan pour /city carte. */
    public List<String> report() {
        Settings s = settings;
        List<String> lines = new ArrayList<>();
        lines.add("§6§l🗺 Parcelles de la ville sur les cartes (JourneyMap)");
        lines.add(s.enabled() ? "§7État : §aactivées §7(couleur §f#" + s.color() + "§7"
                + (s.grid() ? ", quadrillage" : "") + (s.minimap() ? ", minicarte" : ", plein écran seulement") + "§7)"
                : "§7État : §cdésactivées §7(carte.activee: false)");
        lines.add("§7Parcelles publiées : §f" + exported + (writtenAt == 0 ? ""
                : " §8(fichier écrit il y a " + Math.max(0, (System.currentTimeMillis() - writtenAt) / 1000) + " s)"));
        lines.add("§7Fichier : §f" + plugin.getDataFolder().getName() + "/carte.json §8— lu par le mod serveur "
                + COMPANION_MOD);
        // Liste des mods lue par réflexion : « non détecté » si elle n'est pas visible depuis un plugin
        lines.add("§7Mod compagnon : " + presence(COMPANION_MOD) + " §8· §7JourneyMap (serveur) : " + presence(JOURNEYMAP_MOD));
        lines.add("§8Réécrit à chaque claim ou unclaim (revu toutes les 30 s) ; le mod l'envoie aux joueurs dans les 2 s.");
        lines.add("§8Journal du serveur : « API serveur de JourneyMap reçue » puis « Ville sur les cartes JourneyMap ».");
        return lines;
    }

    private static String presence(String modId) {
        return ModIntegrations.modLoaderReports(modId) ? "§aprésent" : "§7non détecté";
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::export, 60L, PERIOD_TICKS);
    }

    /**
     * Les parcelles ont changé (claim, unclaim, fondation) : export au prochain tick, une seule
     * fois même si plusieurs parcelles changent dans le même tick.
     */
    public void requestExport() {
        if (exportScheduled) return;
        exportScheduled = true;
        Bukkit.getScheduler().runTask(plugin, () -> {
            exportScheduled = false;
            export();
        });
    }

    /** Recalcule le contenu (thread principal : base de données) puis l'écrit en arrière-plan. */
    public void export() {
        String json = build();
        if (json.equals(last)) return;
        last = json;
        writtenAt = System.currentTimeMillis();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> write(json));
    }

    /** Contenu du fichier : ville désactivée ou absente → aucune zone (le mod efface la carte). */
    String build() {
        Settings s = settings;
        boolean enabled = s.enabled() && city.isCityInitialized();
        String name  = s.name();
        if (name == null || name.isBlank()) name = city.isCityInitialized() ? city.getCityName() : "Ville";

        StringBuilder zones = new StringBuilder();
        int total = 0;
        if (enabled) {
            for (World w : Bukkit.getWorlds()) {
                List<long[]> chunks = city.getClaimedChunkCoords(w.getName());
                if (chunks.isEmpty()) continue;
                total += chunks.size();
                if (!zones.isEmpty()) zones.append(",\n");
                zones.append("    {\"dimension\": ").append(quote(w.getKey().toString())).append(", \"chunks\": [");
                for (int i = 0; i < chunks.size(); i++) {
                    if (i > 0) zones.append(", ");
                    zones.append('[').append(chunks.get(i)[0]).append(", ").append(chunks.get(i)[1]).append(']');
                }
                zones.append("]}");
            }
        }
        exported = total;
        // Bulle au survol de la zone : nom, parcelles et habitants
        int residents = city.getResidentCount();
        String info = name + " — " + total + (total > 1 ? " parcelles" : " parcelle")
                + " · " + residents + (residents > 1 ? " habitants" : " habitant");

        return "{\n"
                + "  \"format\": 2,\n"
                + "  \"nom\": " + quote(name) + ",\n"
                + "  \"info\": " + quote(info) + ",\n"
                + "  \"couleur\": " + quote(s.color()) + ",\n"
                + "  \"remplissage\": " + number(s.fill()) + ",\n"
                + "  \"bordure\": " + number(s.stroke()) + ",\n"
                + "  \"epaisseur\": " + number(s.width()) + ",\n"
                + "  \"minicarte\": " + s.minimap() + ",\n"
                + "  \"quadrillage\": {\"actif\": " + s.grid()
                + ", \"couleur\": " + quote(s.gridColor() == null ? s.color() : s.gridColor())
                + ", \"opacite\": " + number(s.gridOpacity())
                + ", \"epaisseur\": " + number(s.gridWidth()) + "},\n"
                + "  \"zones\": [" + (zones.isEmpty() ? "" : "\n" + zones + "\n  ") + "]\n"
                + "}\n";
    }

    /** Écriture atomique : le mod ne lit jamais un fichier à moitié écrit. */
    private void write(String json) {
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling("carte.json.tmp");
            Files.writeString(tmp, json, StandardCharsets.UTF_8);
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicUnsupported) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            plugin.getLogger().warning("carte.json non écrit : " + e.getMessage());
        }
    }

    /** « #F1CF5E », « f1cf5e » ou « F1CF5E » → « F1CF5E » ; valeur invalide → valeur donnée. */
    static String normalizeColor(String raw, String fallback) {
        if (raw == null) return fallback;
        String c = raw.trim().replace("#", "").toUpperCase(Locale.ROOT);
        return c.matches("[0-9A-F]{6}") ? c : fallback;
    }

    /** Nombre JSON borné à [0, 10] (opacités 0–1, épaisseurs en pixels), point décimal. */
    private static String number(double v) {
        double clamped = Math.max(0, Math.min(10, v));
        return String.format(Locale.ROOT, "%.3f", clamped);
    }

    /** Chaîne JSON échappée (le nom de la ville est choisi par les joueurs). */
    static String quote(String s) {
        StringBuilder b = new StringBuilder("\"");
        for (char ch : s.toCharArray()) {
            switch (ch) {
                case '"'  -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                case '\n' -> b.append("\\n");
                case '\r' -> b.append("\\r");
                case '\t' -> b.append("\\t");
                default   -> {
                    if (ch < 0x20) b.append(String.format("\\u%04x", (int) ch));
                    else b.append(ch);
                }
            }
        }
        return b.append('"').toString();
    }
}
