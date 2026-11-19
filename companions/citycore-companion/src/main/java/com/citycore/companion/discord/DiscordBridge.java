package com.citycore.companion.discord;

import com.citycore.companion.CityCoreCompanion;
import com.citycore.companion.Settings;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Passerelle entre le serveur et le bot Discord : l'API HTTP du compagnon.
 * <ul>
 *   <li>{@code GET /api/v1/etat} : le serveur (nom, joueurs connectés) et l'état écrit par le
 *       plugin CityCore ({@code etat.json} : la ville, les joueurs) ;</li>
 *   <li>{@code GET /api/v1/evenements?apres=N&attente=S} : les lignes du journal du plugin
 *       ({@code journal.jsonl}) qui suivent le numéro N ; sans ligne nouvelle, la réponse attend
 *       jusqu'à S secondes qu'il s'en présente une ;</li>
 *   <li>{@code POST /api/v1/chat} {@code {"auteur": "...", "message": "..."}} : un message de
 *       Discord, affiché aux joueurs connectés.</li>
 * </ul>
 * Toutes les demandes portent le jeton de {@code discord.jeton} (créé au premier démarrage).
 * Les fichiers du plugin sont suivis sur un fil à part ; seuls le relevé des joueurs connectés et
 * l'affichage des messages se font sur le fil du serveur.
 */
final class DiscordBridge {

    /** Intervalle de contrôle des fichiers du plugin (ms). */
    private static final long POLL_MS = 500;
    /** Attente maximale d'une demande d'événements (secondes). */
    private static final int MAX_WAIT_SECONDS = 30;
    /** Le fichier « lien » (le bot est là) n'est pas réécrit plus souvent. */
    private static final long LINK_EVERY_MS = 15_000;
    private static final int MAX_AUTHOR = 40, MAX_MESSAGE = 300;

    /** Joueurs connectés, relevés sur le fil du serveur. */
    private record Live(List<String> names, int max, String motd) {}

    private MinecraftServer server;
    private ApiServer api;
    private Journal journal;
    private ScheduledExecutorService files;
    private Path folder;
    private volatile Live live = new Live(List.of(), 0, "");
    /** Contenu de etat.json (null : absent ou illisible) et son empreinte (date et taille). */
    private volatile JsonElement state = JsonNull.INSTANCE;
    private long stateStamp = Long.MIN_VALUE;
    private volatile long linkAt;
    private boolean fileWarned;
    /** Démarrage déjà tenté pour ce serveur (réussi ou non). */
    private boolean started;
    private int tick;

    /* ───────────────────────── Cycle de vie ───────────────────────── */

    @SubscribeEvent
    public void onStarted(ServerStartedEvent event) {
        start(event.getServer());
    }

    private void start(MinecraftServer minecraft) {
        if (started) return;
        started = true;
        if (!Settings.DC_ENABLED.get()) {
            CityCoreCompanion.LOGGER.info("Module Discord éteint (discord.actif = false)");
            return;
        }
        server = minecraft;
        folder = FMLPaths.GAMEDIR.get().resolve(Settings.DC_FOLDER.get());
        journal = new Journal();
        snapshot();

        String address = Settings.DC_ADDRESS.get();
        int port = Settings.DC_PORT.get();
        api = new ApiServer(token(), this::route, this::botSeen);
        try {
            api.start(address, port);
        } catch (IOException e) {
            api = null;
            CityCoreCompanion.LOGGER.warn("API Discord : impossible d'écouter sur {}:{} ({}) : module éteint. "
                    + "Changez discord.port si le port est déjà pris.", address, port, e.toString());
            return;
        }
        files = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "CityCore-Discord-Fichiers");
            t.setDaemon(true);
            return t;
        });
        files.scheduleWithFixedDelay(this::pollFiles, 0, POLL_MS, TimeUnit.MILLISECONDS);
        CityCoreCompanion.LOGGER.info("API Discord à l'écoute sur http://{}:{} (jeton : discord.jeton de "
                + "config/citycore_companion.toml) ; fichiers du plugin lus dans {}", address, port, folder);
    }

    @SubscribeEvent
    public void onStopping(ServerStoppingEvent event) {
        if (files != null) files.shutdownNow();
        if (journal != null) journal.close();
        if (api != null) api.stop();
        files = null;
        api = null;
        server = null;
        started = false;
    }

    /** Une fois par seconde : relevé des joueurs connectés pour les réponses de l'API. */
    @SubscribeEvent
    public void onTick(ServerTickEvent.Post event) {
        // Serveur hybride qui n'annoncerait pas son démarrage aux mods : l'API s'ouvre au premier tick
        if (!started) start(event.getServer());
        if (api == null || ++tick % 20 != 0) return;
        snapshot();
    }

    private void snapshot() {
        List<String> names = new ArrayList<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) names.add(p.getGameProfile().getName());
        live = new Live(List.copyOf(names), server.getPlayerList().getMaxPlayers(), String.valueOf(server.getMotd()));
    }

    /**
     * Jeton de l'API : celui des réglages, ou un nouveau, tiré au hasard et enregistré dans
     * {@code config/citycore_companion.toml}, quand il est vide.
     */
    private static String token() {
        String token = Settings.DC_TOKEN.get().trim();
        if (!token.isEmpty()) return token;
        byte[] random = new byte[24];
        new SecureRandom().nextBytes(random);
        token = HexFormat.of().formatHex(random);
        Settings.DC_TOKEN.set(token);
        Settings.DC_TOKEN.save();
        CityCoreCompanion.LOGGER.info("API Discord : jeton créé et enregistré (discord.jeton de "
                + "config/citycore_companion.toml) — à recopier dans la configuration du bot.");
        return token;
    }

    /* ───────────────────────── Fichiers du plugin ───────────────────────── */

    private void pollFiles() {
        try {
            int added = journal.poll(folder.resolve("journal.jsonl"));
            if (added > 0 && Settings.DC_LOG.get()) {
                CityCoreCompanion.LOGGER.info("Discord : {} ligne(s) du journal lue(s)", added);
            }
            Path file = folder.resolve("etat.json");
            long stamp = Files.exists(file) ? Files.getLastModifiedTime(file).toMillis() * 31 + Files.size(file) : -1;
            if (stamp != stateStamp) {
                state = stamp == -1 ? JsonNull.INSTANCE : JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
                stateStamp = stamp;
            }
            fileWarned = false;
        } catch (Exception e) {
            // Fichier en cours d'écriture : nouvel essai au prochain passage
            if (!fileWarned) {
                fileWarned = true;
                if (Settings.DC_LOG.get()) CityCoreCompanion.LOGGER.warn("Discord : fichier du plugin illisible ({})", e.toString());
            }
        }
    }

    /** Le bot vient d'interroger l'API : le plugin le sait par la date du fichier « lien ». */
    private void botSeen() {
        long now = System.currentTimeMillis();
        if (now - linkAt < LINK_EVERY_MS) return;
        linkAt = now;
        try {
            Files.createDirectories(folder);
            Files.writeString(folder.resolve("lien"), Long.toString(now), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            // Dossier du plugin absent (plugin non installé) : sans conséquence
        }
    }

    /* ───────────────────────── Routes ───────────────────────── */

    private ApiServer.Response route(String method, String path, Map<String, String> query, String body)
            throws InterruptedException {
        switch (path) {
            case "/api/v1/etat" -> {
                if (!method.equals("GET")) return ApiServer.Response.error(405, "GET attendu");
                return ApiServer.Response.ok(stateJson());
            }
            case "/api/v1/evenements" -> {
                if (!method.equals("GET")) return ApiServer.Response.error(405, "GET attendu");
                return ApiServer.Response.ok(eventsJson(query));
            }
            case "/api/v1/chat" -> {
                if (!method.equals("POST")) return ApiServer.Response.error(405, "POST attendu");
                return chat(body);
            }
            default -> {
                return ApiServer.Response.error(404, "route inconnue");
            }
        }
    }

    private String stateJson() {
        Live now = live;
        JsonObject srv = new JsonObject();
        String name = Settings.DC_SERVER_NAME.get().trim();
        // Sans nom réglé : celui de la liste des serveurs (MOTD), sans ses codes de couleur
        srv.addProperty("nom", name.isEmpty() ? now.motd().replaceAll("§.", "").trim() : name);
        srv.addProperty("joueurs", now.names().size());
        srv.addProperty("max", now.max());
        JsonArray online = new JsonArray();
        now.names().forEach(online::add);
        srv.add("enLigne", online);

        JsonObject root = new JsonObject();
        root.add("serveur", srv);
        root.addProperty("dernierEvenement", journal.lastId());
        root.add("citycore", state);
        return root.toString();
    }

    private String eventsJson(Map<String, String> query) throws InterruptedException {
        JsonObject root = new JsonObject();
        JsonArray list = new JsonArray();
        String after = query.get("apres");
        // Sans « apres » : seulement le dernier numéro (le bot démarre de là, sans rejouer le passé)
        if (after != null) {
            long id;
            int wait;
            try {
                id = Long.parseLong(after);
                wait = Integer.parseInt(query.getOrDefault("attente", "0"));
            } catch (NumberFormatException e) {
                id = Long.MAX_VALUE;
                wait = 0;
            }
            long waitMs = Math.max(0, Math.min(MAX_WAIT_SECONDS, wait)) * 1000L;
            for (JsonObject o : journal.after(id, waitMs)) list.add(o);
        }
        root.addProperty("dernier", journal.lastId());
        root.add("evenements", list);
        return root.toString();
    }

    /** Message venu de Discord ({@code /chat}) : affiché à tous les joueurs connectés. */
    private ApiServer.Response chat(String body) {
        String author, message;
        try {
            JsonObject o = JsonParser.parseString(body).getAsJsonObject();
            author = clean(o.get("auteur").getAsString(), MAX_AUTHOR);
            message = clean(o.get("message").getAsString(), MAX_MESSAGE);
        } catch (RuntimeException e) {
            return ApiServer.Response.error(400, "auteur et message attendus");
        }
        if (author.isEmpty() || message.isEmpty()) return ApiServer.Response.error(400, "auteur et message attendus");
        MinecraftServer srv = server;
        if (srv == null) return ApiServer.Response.error(503, "serveur en cours d'arrêt");

        Component line = Component.literal("[Discord] ").withStyle(ChatFormatting.BLUE)
                .append(Component.literal(author).withStyle(ChatFormatting.WHITE))
                .append(Component.literal(" : ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(message).withStyle(ChatFormatting.WHITE));
        srv.execute(() -> srv.getPlayerList().broadcastSystemMessage(line, false));
        return ApiServer.Response.ok("{\"ok\": true, \"joueurs\": " + live.names().size() + "}");
    }

    /** Texte d'une ligne, sans code de couleur ni caractère de contrôle, borné. */
    private static String clean(String raw, int max) {
        String s = raw.replaceAll("[\\p{Cntrl}§]", " ").replaceAll("\\s+", " ").trim();
        return s.length() > max ? s.substring(0, max) + "…" : s;
    }
}
