package com.citycore.discord;

import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Journal de ce qui se passe sur le serveur, à destination de Discord : chaque fait est une ligne
 * JSON ajoutée à {@code plugins/CityCore/discord/journal.jsonl}. Le mod serveur « CityCore —
 * Compagnon » relit ce fichier et le sert au bot Discord par son API ; le plugin n'a ainsi aucune
 * dépendance vers un mod, ni vers Discord (même principe que {@code carte.json}).
 *
 * Trois canaux, un par salon du bot :
 * <ul>
 *   <li>{@link #event} : la vie de la ville (sièges, raids, bâtiments, niveaux…) → salon des événements ;</li>
 *   <li>{@link #admin} : ce que font les opérateurs (commandes, modes de jeu) → salon d'administration ;</li>
 *   <li>{@link #chat} : messages de {@code /discord} et connexions → salon de discussion.</li>
 * </ul>
 * Les méthodes sont statiques (appelées d'un peu partout dans le plugin) et ne font rien tant que
 * {@link #start} n'a pas été appelé ou si {@code discord.actif} est à false. L'écriture se fait
 * sur un fil à part, dans l'ordre des appels.
 */
public final class DiscordFeed {

    /** Au-delà de cette taille, le journal est réduit à ses dernières lignes (le mod suit). */
    private static final long MAX_BYTES = 512 * 1024;
    private static final int  KEPT_LINES = 200;
    /** Le bot est considéré relié s'il a interrogé le mod dans ce délai (fichier « lien » du mod). */
    private static final long LINK_TIMEOUT_MS = 90_000;

    private static volatile DiscordFeed instance;

    private final JavaPlugin plugin;
    private final Path folder;
    private final Path journal;
    private final ExecutorService writer;
    /** Débuts de types à ne pas publier (discord.evenements-ignores). */
    private final List<String> ignored;
    private long lastId;
    private long written;
    private boolean failureLogged;

    private DiscordFeed(JavaPlugin plugin, List<String> ignored) {
        this.plugin  = plugin;
        this.folder  = plugin.getDataFolder().toPath().resolve("discord");
        this.journal = folder.resolve("journal.jsonl");
        this.ignored = ignored;
        this.writer  = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "CityCore-Discord");
            t.setDaemon(true);
            return t;
        });
        writer.execute(this::prepare);
    }

    /* ───────────────────────── Cycle de vie ───────────────────────── */

    /** Lit la section discord de config.yml ; sans effet si {@code discord.actif} est à false. */
    public static void start(JavaPlugin plugin) {
        ConfigurationSection cfg = plugin.getConfig();
        if (!cfg.getBoolean("discord.actif", true)) {
            plugin.getLogger().info("Discord : désactivé (discord.actif: false), rien n'est publié.");
            return;
        }
        instance = new DiscordFeed(plugin, cfg.getStringList("discord.evenements-ignores"));
    }

    /** Arrêt du plugin : les dernières lignes sont écrites avant de rendre la main. */
    public static void stop() {
        DiscordFeed feed = instance;
        instance = null;
        if (feed == null) return;
        feed.writer.shutdown();
        try {
            feed.writer.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Le journal est tenu (discord.actif). */
    public static boolean active() {
        return instance != null;
    }

    /** Dossier d'échange avec le mod compagnon ({@code plugins/CityCore/discord}). */
    public static Path folder(JavaPlugin plugin) {
        return plugin.getDataFolder().toPath().resolve("discord");
    }

    /**
     * Le bot Discord a interrogé le mod compagnon récemment : le mod tient à jour le fichier
     * {@code lien} du dossier d'échange à chaque demande du bot.
     */
    public static boolean linked() {
        DiscordFeed feed = instance;
        if (feed == null) return false;
        try {
            Path link = feed.folder.resolve("lien");
            return Files.exists(link)
                    && System.currentTimeMillis() - Files.getLastModifiedTime(link).toMillis() < LINK_TIMEOUT_MS;
        } catch (IOException e) {
            return false;
        }
    }

    /* ───────────────────────── Publication ───────────────────────── */

    /**
     * Un fait de la vie de la ville, pour le salon des événements.
     *
     * @param type  nature du fait, en « famille.detail » ({@code siege.victoire}, {@code batiment.creation}…) :
     *              le bot en tire la couleur de la carte, et {@code discord.evenements-ignores} filtre dessus
     * @param title titre de la carte (les codes de couleur du jeu sont retirés)
     * @param text  détail, ou null
     */
    public static void event(String type, String title, String text) {
        publish("evenement", type, title, text, null);
    }

    /** Une action d'administration (opérateur ou console), pour le salon d'administration. */
    public static void admin(String type, String who, String text) {
        publish("admin", type, null, text, who);
    }

    /** Un message de la discussion ou une connexion, pour le salon de discussion. */
    public static void chat(String type, String who, String text) {
        publish("chat", type, null, text, who);
    }

    private static void publish(String channel, String type, String title, String text, String who) {
        DiscordFeed feed = instance;
        if (feed == null || type == null) return;
        for (String prefix : feed.ignored) {
            if (!prefix.isBlank() && type.startsWith(prefix.trim())) return;
        }
        long id = feed.nextId();
        StringBuilder line = new StringBuilder(160)
                .append("{\"id\": ").append(id)
                .append(", \"date\": ").append(System.currentTimeMillis())
                .append(", \"canal\": ").append(quote(channel))
                .append(", \"type\": ").append(quote(type));
        if (title != null) line.append(", \"titre\": ").append(quote(plain(title)));
        if (text != null)  line.append(", \"texte\": ").append(quote(plain(text)));
        if (who != null)   line.append(", \"joueur\": ").append(quote(plain(who)));
        line.append("}\n");
        String json = line.toString();
        try {
            feed.writer.execute(() -> feed.append(json));
        } catch (java.util.concurrent.RejectedExecutionException ignoredAtShutdown) {
            // Plugin en cours d'arrêt : la ligne est perdue, sans conséquence
        }
    }

    /** Numéros croissants d'un démarrage à l'autre : l'heure, ou le précédent + 1. */
    private synchronized long nextId() {
        lastId = Math.max(lastId + 1, System.currentTimeMillis());
        return lastId;
    }

    /* ───────────────────────── Fichier (fil d'écriture) ───────────────────────── */

    private void prepare() {
        try {
            Files.createDirectories(folder);
            written = Files.exists(journal) ? Files.size(journal) : 0;
            if (written > MAX_BYTES) trim();
        } catch (IOException e) {
            warnOnce(e);
        }
    }

    private void append(String line) {
        try {
            byte[] bytes = line.getBytes(StandardCharsets.UTF_8);
            Files.write(journal, bytes, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            written += bytes.length;
            if (written > MAX_BYTES) trim();
        } catch (IOException e) {
            warnOnce(e);
        }
    }

    /** Ne garde que les dernières lignes : le mod repère le fichier raccourci et le relit. */
    private void trim() throws IOException {
        List<String> lines = Files.readAllLines(journal, StandardCharsets.UTF_8);
        List<String> kept = lines.subList(Math.max(0, lines.size() - KEPT_LINES), lines.size());
        Path tmp = journal.resolveSibling("journal.jsonl.tmp");
        Files.write(tmp, kept, StandardCharsets.UTF_8);
        try {
            Files.move(tmp, journal, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException atomicUnsupported) {
            Files.move(tmp, journal, StandardCopyOption.REPLACE_EXISTING);
        }
        written = Files.size(journal);
    }

    private void warnOnce(IOException e) {
        if (failureLogged) return;
        failureLogged = true;
        plugin.getLogger().warning("Discord : journal non écrit (" + e + ")");
    }

    /* ───────────────────────── Texte ───────────────────────── */

    /** Texte sans les codes de couleur du jeu (§a, §l…). */
    public static String plain(String s) {
        return s == null ? "" : ChatColor.stripColor(s).trim();
    }

    /** Chaîne JSON échappée. */
    static String quote(String s) {
        StringBuilder b = new StringBuilder(s.length() + 2).append('"');
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
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
