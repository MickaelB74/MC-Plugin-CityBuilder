package com.citycore.util;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Met à jour config.yml quand le jar embarque une version plus récente.
 *
 * Bukkit n'écrase jamais un config.yml existant : il complète seulement les clés absentes
 * avec celles du jar, et de façon partielle (les listes oui, les sections non). On compare donc
 * la clé {@code config-version} du fichier à celle du jar :
 *  - fichier de la version 2 ou plus (économie actuelle) : les sections nouvelles sont ajoutées
 *    à la fin du fichier avec leurs commentaires, les réglages existants sont conservés ; une
 *    section dont le contenu a changé de nature (voir {@link #REPLACED_SECTIONS}) est remplacée
 *    par celle du jar ;
 *  - fichier plus ancien (configuration de test) : il est remplacé.
 * Dans les deux cas, une copie de l'ancien fichier est gardée.
 */
public final class ConfigUpdater {

    /** Première version dont le format permet un simple ajout des sections manquantes. */
    private static final int MERGEABLE_FROM = 2;
    private static final Pattern TOP_KEY = Pattern.compile("^([A-Za-z0-9_-]+):");

    /**
     * Sections remplacées par celles du jar en passant à une version (l'ancienne reste dans la
     * copie de sauvegarde). 6 : Jack Sparrow devient un personnage du mod Tax' Ocean Villager.
     */
    private static final java.util.Map<Integer, List<String>> REPLACED_SECTIONS = java.util.Map.of(
            6, List.of("jacksparrow"));

    /**
     * Valeurs par défaut changées en passant à une version : la ligne n'est remplacée que si elle
     * porte encore l'ancienne valeur par défaut (un réglage modifié à la main est conservé).
     * Chaque entrée : {expression de l'ancienne ligne, nouvelle valeur}.
     * 15 : la garde monte jusqu'à 50 (au lieu de 12), formations simultanées en conséquence.
     * 17 : trois emplacements à remplir pour les uniformes de l'armée ennemie (liste vide en 16).
     */
    private static final java.util.Map<Integer, List<String[]>> UPDATED_LINES = java.util.Map.of(
            15, List.of(
                    new String[]{"(?m)^  max: \\{1: 2, 2: 4, 3: 6, 4: 9, 5: 12\\}",
                            "  max: {1: 8, 2: 16, 3: 25, 4: 36, 5: 50}"},
                    new String[]{"(?m)^  formations: \\{1: 1, 2: 1, 3: 2, 4: 2, 5: 3\\}",
                            "  formations: {1: 2, 2: 3, 3: 4, 4: 5, 5: 6}"}),
            17, List.<String[]>of(
                    new String[]{"(?m)^    skins: \\[\\]$",
                            "    skins:\n"
                                    + "      - {name: \"\", value: \"\", signature: \"\"}\n"
                                    + "      - {name: \"\", value: \"\", signature: \"\"}\n"
                                    + "      - {name: \"\", value: \"\", signature: \"\"}"}));

    private ConfigUpdater() {}

    /** À appeler au tout début de onEnable, avant toute lecture de la config. */
    public static void ensureUpToDate(JavaPlugin plugin) {
        File file = new File(plugin.getDataFolder(), "config.yml");
        if (!file.exists()) {
            plugin.saveDefaultConfig();
            return;
        }

        // Fichier illisible (erreur de syntaxe YAML) : on n'y touche pas. Sans cette garde, il serait
        // lu comme vide (version 0) et remplacé par la configuration par défaut.
        YamlConfiguration current = new YamlConfiguration();
        try {
            current.load(file);
        } catch (Exception e) {
            String detail = String.valueOf(e.getMessage()).lines().findFirst().orElse("");
            plugin.getLogger().severe("config.yml contient une erreur (" + detail
                    + ") — mise à jour automatique annulée, corrigez le fichier puis redémarrez.");
            return;
        }

        int jarVersion  = jarVersion(plugin);
        int fileVersion = current.getInt("config-version", 0);
        if (fileVersion >= jarVersion) return;

        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        File backup = new File(plugin.getDataFolder(), "config-ancien-v" + fileVersion + "-" + stamp + ".yml");
        try {
            if (fileVersion >= MERGEABLE_FROM) {
                Files.copy(file.toPath(), backup.toPath());
                List<String> added = appendMissingSections(plugin, file, fileVersion, jarVersion);
                plugin.reloadConfig();
                plugin.getLogger().info("config.yml mis à jour (version " + fileVersion + " → " + jarVersion + ") : "
                        + (added.isEmpty() ? "aucune section ajoutée" : "sections ajoutées " + added)
                        + ", vos réglages sont conservés (copie : " + backup.getName() + ").");
            } else {
                Files.move(file.toPath(), backup.toPath());
                plugin.saveResource("config.yml", true);
                plugin.reloadConfig();
                plugin.getLogger().warning("config.yml obsolète (version " + fileVersion + " → " + jarVersion
                        + ") : sauvegardé sous " + backup.getName() + " et remplacé par la nouvelle configuration.");
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Impossible de mettre à jour config.yml : " + e.getMessage()
                    + " — supprimez plugins/" + plugin.getName() + "/config.yml puis redémarrez.");
        }
    }

    /**
     * Ajoute à la fin du fichier les sections de premier niveau absentes, avec le bloc de
     * commentaires qui les précède dans le config.yml du jar, puis met à jour config-version.
     *
     * @return noms des sections ajoutées
     */
    private static List<String> appendMissingSections(JavaPlugin plugin, File file, int fileVersion,
                                                      int jarVersion) throws IOException {
        String jarText;
        try (InputStream in = plugin.getResource("config.yml")) {
            if (in == null) throw new IOException("config.yml absent du plugin");
            jarText = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        String userText = Files.readString(file.toPath(), StandardCharsets.UTF_8);
        List<String> added = new ArrayList<>();
        String updated = merge(userText, jarText, fileVersion, jarVersion, added);
        Files.writeString(file.toPath(), updated, StandardCharsets.UTF_8);
        return added;
    }

    /**
     * Fusion d'un config.yml existant avec celui du jar (sans accès disque : testable à part).
     *
     * @param added reçoit les noms des sections ajoutées ou remplacées
     * @return le nouveau texte du fichier
     */
    static String merge(String userText, String jarText, int fileVersion, int jarVersion,
                        List<String> added) throws IOException {
        YamlConfiguration user = new YamlConfiguration();
        try {
            user.loadFromString(userText);
        } catch (org.bukkit.configuration.InvalidConfigurationException e) {
            throw new IOException("config.yml illisible : " + e.getMessage(), e);
        }

        String[] lines = jarText.split("\r?\n", -1);
        // Sections à remplacer pour les versions franchies
        java.util.Set<String> replaced = new java.util.LinkedHashSet<>();
        for (int v = fileVersion + 1; v <= jarVersion; v++) {
            for (String section : REPLACED_SECTIONS.getOrDefault(v, List.of())) {
                if (user.contains(section)) replaced.add(section);
            }
        }
        StringBuilder append = new StringBuilder();
        List<String> comments = new ArrayList<>();
        int i = 0;
        while (i < lines.length) {
            String line = lines[i];
            Matcher m = TOP_KEY.matcher(line);
            if (!m.find()) {
                // Commentaires collés à la section suivante ; une ligne vide les détache
                if (line.startsWith("#")) comments.add(line);
                else if (line.isBlank()) comments.clear();
                i++;
                continue;
            }
            String key = m.group(1);
            List<String> block = new ArrayList<>(comments);
            comments.clear();
            block.add(line);
            i++;
            // Contenu de la section : lignes indentées (et lignes vides suivies d'une ligne indentée)
            while (i < lines.length && (lines[i].startsWith(" ") || lines[i].startsWith("\t")
                    || (lines[i].isBlank() && i + 1 < lines.length && lines[i + 1].startsWith(" ")))) {
                block.add(lines[i]);
                i++;
            }
            if (!key.equals("config-version") && (!user.contains(key) || replaced.contains(key))) {
                append.append('\n').append(String.join("\n", block)).append('\n');
                added.add(replaced.contains(key) ? key + " (remplacée)" : key);
            }
        }

        String updated = userText.replaceFirst("(?m)^config-version:\\s*\\d+", "config-version: " + jarVersion);
        for (String section : replaced) updated = removeSection(updated, section);
        // Anciennes valeurs par défaut remplacées (seulement si elles n'ont pas été modifiées)
        for (int v = fileVersion + 1; v <= jarVersion; v++) {
            for (String[] change : UPDATED_LINES.getOrDefault(v, List.of())) {
                String before = updated;
                updated = updated.replaceFirst(change[0], Matcher.quoteReplacement(change[1]));
                // Libellé du journal : la première ligne seulement (une liste en compte plusieurs)
                if (!updated.equals(before)) added.add(change[1].trim().lines().findFirst().orElse("") + " (nouvelle valeur)");
            }
        }
        // Nouveaux habitants : leurs lignes de skin s'ajoutent sous npc-skins (section existante)
        String withSkins = addMissingChildren(updated, lines, user, "npc-skins");
        if (!withSkins.equals(updated)) added.add("npc-skins (nouveaux habitants)");
        updated = withSkins;
        // Nouveaux mods reconnus : leur clé s'ajoute sous mod-integrations (section existante)
        String withMods = addMissingChildren(updated, lines, user, "mod-integrations");
        if (!withMods.equals(updated)) added.add("mod-integrations (nouveaux mods)");
        updated = withMods;
        // Nouveaux réglages des habitants (vitesse, promenade…) : ajoutés sous habitants (section existante)
        String withInhabitants = addMissingChildren(updated, lines, user, "habitants");
        if (!withInhabitants.equals(updated)) added.add("habitants (nouveaux réglages)");
        updated = withInhabitants;
        // Réglages de la garde (taille, formations, rôles…) : ajoutés sous soldats (section existante)
        String withGuard = addMissingChildren(updated, lines, user, "soldats");
        if (!withGuard.equals(updated)) added.add("soldats (garde)");
        updated = withGuard;
        // Nouveaux réglages des sièges (types, armée ennemie…) et du calendrier : ajoutés sous leur section
        for (String section : new String[]{"sieges", "evenements"}) {
            String with = addMissingChildren(updated, lines, user, section);
            if (!with.equals(updated)) added.add(section + " (nouveaux réglages)");
            updated = with;
        }
        // Nouveaux réglages des bâtiments (hauteur, profondeur, terrain…) : ajoutés sous batiments
        String withBuildings = addMissingChildren(updated, lines, user, "batiments");
        if (!withBuildings.equals(updated)) added.add("batiments (nouveaux réglages)");
        updated = withBuildings;
        if (append.length() > 0) updated = updated.stripTrailing() + "\n" + append;
        return updated;
    }

    /**
     * Retire du texte une section de premier niveau (ses lignes indentées) et le bloc de
     * commentaires collé juste au-dessus ; elle est ensuite ajoutée à la fin dans sa version du jar.
     */
    private static String removeSection(String text, String section) {
        List<String> out = new ArrayList<>(List.of(text.split("\r?\n", -1)));
        int start = -1;
        for (int i = 0; i < out.size(); i++) {
            if (out.get(i).startsWith(section + ":")) { start = i; break; }
        }
        if (start < 0) return text;
        int end = start + 1;
        while (end < out.size() && (out.get(end).isBlank() || out.get(end).startsWith(" ") || out.get(end).startsWith("\t"))) {
            // une ligne vide suivie d'une ligne de premier niveau termine la section
            if (out.get(end).isBlank() && (end + 1 >= out.size() || !(out.get(end + 1).startsWith(" ") || out.get(end + 1).startsWith("\t")))) break;
            end++;
        }
        int from = start;
        while (from > 0 && out.get(from - 1).startsWith("#")) from--;
        out.subList(from, end).clear();
        return String.join("\n", out);
    }

    /**
     * Ajoute, juste sous la ligne « section: » du fichier, les clés enfants que le config.yml du
     * jar possède et pas le fichier : la ligne de la clé et ses lignes de suite plus indentées
     * (liste sur plusieurs lignes). Section absente : rien n'est fait.
     */
    private static String addMissingChildren(String text, String[] jarLines, YamlConfiguration user, String section) {
        if (!user.isConfigurationSection(section)) return text;
        List<String> missing = new ArrayList<>();
        boolean inside = false, copying = false;
        Pattern child = Pattern.compile("^  ([A-Za-z0-9_-]+):");
        for (String line : jarLines) {
            if (line.startsWith(section + ":")) { inside = true; continue; }
            if (!inside) continue;
            if (!line.startsWith("  ")) break;                       // fin de la section
            Matcher m = child.matcher(line);
            if (m.find()) {
                copying = !user.contains(section + "." + m.group(1));
                if (copying) missing.add(line);
            } else if (copying && line.startsWith("   ")) {
                missing.add(line);                                    // suite de la clé ajoutée
            } else if (!line.startsWith("   ")) {
                copying = false;                                      // commentaire de la section
            }
        }
        if (missing.isEmpty()) return text;
        Matcher header = Pattern.compile("(?m)^" + Pattern.quote(section) + ":[^\\n]*\\n").matcher(text);
        if (!header.find()) return text;
        return text.substring(0, header.end()) + String.join("\n", missing) + "\n" + text.substring(header.end());
    }

    private static int jarVersion(JavaPlugin plugin) {
        try (InputStream in = plugin.getResource("config.yml")) {
            if (in == null) return 0;
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8))
                    .getInt("config-version", 0);
        } catch (Exception e) {
            return 0;
        }
    }
}
