package com.citycore.companion.discord;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * Suit {@code plugins/CityCore/discord/journal.jsonl}, écrit par le plugin CityCore (classe
 * {@code DiscordFeed}) : une ligne JSON par fait, numérotée ({@code id} croissant, y compris d'un
 * démarrage du serveur à l'autre). Les dernières lignes sont gardées en mémoire ; le bot demande
 * « tout ce qui suit le numéro N » et peut attendre qu'une ligne arrive ({@link #after}).
 *
 * Le fichier n'est lu que depuis la dernière position connue ; s'il a été raccourci par le plugin
 * (journal trop gros), il est relu en entier et les numéros déjà vus sont ignorés.
 */
final class Journal {

    /** Lignes gardées en mémoire : ce que le bot peut rattraper après une absence. */
    private static final int KEPT = 500;
    /** Lecture bornée : un fichier énorme (jamais en pratique) est repris par sa fin. */
    private static final int MAX_READ = 2 * 1024 * 1024;

    private final Object lock = new Object();
    private final ArrayDeque<JsonObject> entries = new ArrayDeque<>();
    private long offset;
    private long lastId;
    private boolean closed;

    /** Numéro de la dernière ligne lue (0 : aucune). */
    long lastId() {
        synchronized (lock) {
            return lastId;
        }
    }

    /** Lit ce qui a été ajouté au fichier depuis le dernier passage ; renvoie le nombre de lignes nouvelles. */
    int poll(Path file) throws IOException {
        long size = Files.exists(file) ? Files.size(file) : 0;
        if (size < offset) offset = 0; // fichier raccourci ou recréé : relu depuis le début
        if (size == offset) return 0;
        if (size - offset > MAX_READ) offset = size - MAX_READ;

        byte[] data = new byte[(int) (size - offset)];
        int read = 0;
        try (SeekableByteChannel channel = Files.newByteChannel(file, StandardOpenOption.READ)) {
            channel.position(offset);
            ByteBuffer buffer = ByteBuffer.wrap(data);
            while (buffer.hasRemaining()) {
                int n = channel.read(buffer);
                if (n < 0) break;
                read += n;
            }
        }
        // Seules les lignes terminées sont prises : la dernière peut être en cours d'écriture
        int end = -1;
        for (int i = read - 1; i >= 0; i--) {
            if (data[i] == '\n') {
                end = i;
                break;
            }
        }
        if (end < 0) return 0;
        offset += end + 1;

        List<JsonObject> fresh = new ArrayList<>();
        for (String line : new String(data, 0, end, StandardCharsets.UTF_8).split("\n")) {
            if (line.isBlank()) continue;
            try {
                JsonObject o = JsonParser.parseString(line).getAsJsonObject();
                if (o.has("id") && o.has("canal")) fresh.add(o);
            } catch (RuntimeException ignored) {
                // Ligne abîmée (début d'un fichier repris par sa fin) : ignorée
            }
        }
        int added = 0;
        synchronized (lock) {
            for (JsonObject o : fresh) {
                long id = o.get("id").getAsLong();
                if (id <= lastId) continue;
                lastId = id;
                entries.addLast(o);
                added++;
            }
            while (entries.size() > KEPT) entries.removeFirst();
            if (added > 0) lock.notifyAll();
        }
        return added;
    }

    /**
     * Lignes dont le numéro dépasse {@code id}, dans l'ordre. S'il n'y en a pas, attend au plus
     * {@code waitMs} qu'il en arrive (le bot garde ainsi une demande ouverte : il est servi dès
     * qu'il se passe quelque chose).
     */
    List<JsonObject> after(long id, long waitMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + waitMs;
        synchronized (lock) {
            while (!closed && lastId <= id) {
                long left = deadline - System.currentTimeMillis();
                if (left <= 0) break;
                lock.wait(left);
            }
            List<JsonObject> out = new ArrayList<>();
            for (JsonObject o : entries) {
                if (o.get("id").getAsLong() > id) out.add(o);
            }
            return out;
        }
    }

    /** Arrêt du serveur : les demandes en attente sont rendues tout de suite. */
    void close() {
        synchronized (lock) {
            closed = true;
            lock.notifyAll();
        }
    }
}
