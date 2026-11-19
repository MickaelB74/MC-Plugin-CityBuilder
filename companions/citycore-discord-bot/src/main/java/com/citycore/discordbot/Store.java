package com.citycore.discordbot;

import net.dv8tion.jda.api.utils.data.DataObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Mémoire du bot d'un lancement à l'autre ({@code etat-bot.json}) : identifiants des salons qu'il
 * a créés, des messages qu'il met à jour (carte de la ville, une carte par joueur) et numéro du
 * dernier événement publié (pour reprendre où il s'était arrêté, sans doublon).
 */
final class Store {

    private static final Path FILE = Path.of("etat-bot.json");

    private DataObject data = DataObject.empty();

    static Store load() {
        Store store = new Store();
        if (Files.exists(FILE)) {
            try {
                store.data = DataObject.fromJson(Files.readString(FILE, StandardCharsets.UTF_8));
            } catch (Exception e) {
                System.err.println("etat-bot.json illisible (" + e.getMessage() + ") : le bot repart d'une mémoire vide.");
            }
        }
        return store;
    }

    synchronized String get(String key) {
        return data.getString(key, "");
    }

    synchronized void put(String key, String value) {
        if (value.equals(data.getString(key, ""))) return;
        data.put(key, value);
        save();
    }

    synchronized void remove(String key) {
        if (!data.hasKey(key)) return;
        data.remove(key);
        save();
    }

    /** Numéro du dernier événement publié ; −1 : aucun (premier lancement). */
    synchronized long lastEvent() {
        return data.getLong("dernierEvenement", -1);
    }

    synchronized void lastEvent(long id) {
        if (id == data.getLong("dernierEvenement", -1)) return;
        data.put("dernierEvenement", id);
        save();
    }

    private void save() {
        try {
            Path tmp = FILE.resolveSibling("etat-bot.json.tmp");
            Files.writeString(tmp, data.toPrettyString(), StandardCharsets.UTF_8);
            Files.move(tmp, FILE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            System.err.println("etat-bot.json non écrit : " + e.getMessage());
        }
    }
}
