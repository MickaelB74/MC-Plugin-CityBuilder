package com.citycore.discordbot;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Configuration du bot : fichier {@code bot.properties} du dossier d'où il est lancé. Au premier
 * lancement, le fichier d'exemple (commenté) est déposé à cet endroit et le bot s'arrête en
 * demandant de le remplir.
 */
final class Config {

    static final Path FILE = Path.of("bot.properties");

    private final Properties values = new Properties();

    /** Charge le fichier ; renvoie null s'il vient d'être créé (à remplir avant de relancer). */
    static Config load() throws IOException {
        if (!Files.exists(FILE)) {
            try (InputStream example = Config.class.getResourceAsStream("/bot.exemple.properties")) {
                if (example == null) throw new IOException("fichier d'exemple absent du jar");
                Files.copy(example, FILE);
            }
            return null;
        }
        Config config = new Config();
        try (Reader reader = Files.newBufferedReader(FILE, StandardCharsets.UTF_8)) {
            config.values.load(reader);
        }
        return config;
    }

    String get(String key, String fallback) {
        String v = values.getProperty(key);
        return v == null || v.isBlank() ? fallback : v.trim();
    }

    String get(String key) {
        return get(key, "");
    }

    int number(String key, int fallback, int min) {
        try {
            return Math.max(min, Integer.parseInt(get(key, String.valueOf(fallback))));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** Couleur « F1CF5E » ou « #F1CF5E » → 0xF1CF5E. */
    int color(String key, int fallback) {
        try {
            return Integer.parseInt(get(key).replace("#", ""), 16) & 0xFFFFFF;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
