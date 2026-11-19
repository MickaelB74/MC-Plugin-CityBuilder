package com.citycore.companion.discord;

import com.citycore.companion.CityCoreCompanion;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Petit serveur HTTP de l'API du compagnon : une demande par connexion, réponses en JSON, accès
 * réservé au porteur du jeton (en-tête {@code Authorization: Bearer <jeton>}).
 *
 * Écrit sur les sockets de base de Java plutôt qu'avec {@code com.sun.net.httpserver} : ce module
 * du JDK n'est pas toujours visible des mods selon la façon dont le serveur est lancé. Les besoins
 * sont minimes (trois routes, un seul client : le bot).
 */
final class ApiServer {

    /** Réponse d'une route : code HTTP et corps JSON. */
    record Response(int status, String json) {
        static Response ok(String json) { return new Response(200, json); }
        static Response error(int status, String message) {
            return new Response(status, "{\"erreur\": \"" + message + "\"}");
        }
    }

    /** Une route : méthode, chemin, paramètres de l'adresse, corps de la demande. */
    interface Router {
        Response route(String method, String path, Map<String, String> query, String body) throws Exception;
    }

    private static final int MAX_HEADER = 8 * 1024;
    private static final int MAX_BODY = 16 * 1024;
    private static final int READ_TIMEOUT_MS = 10_000;
    /** Demandes traitées en même temps (le bot en garde une ouverte en attente d'événements). */
    private static final int MAX_CLIENTS = 12;

    private final byte[] token;
    private final Router router;
    private final Runnable onAuthorized;
    private ServerSocket socket;
    private ExecutorService pool;
    private volatile boolean running;
    private boolean refusalLogged;

    /**
     * @param onAuthorized appelé à chaque demande munie du bon jeton (le bot est là)
     */
    ApiServer(String token, Router router, Runnable onAuthorized) {
        this.token = token.getBytes(StandardCharsets.UTF_8);
        this.router = router;
        this.onAuthorized = onAuthorized;
    }

    void start(String address, int port) throws IOException {
        socket = new ServerSocket();
        socket.setReuseAddress(true);
        socket.bind(new InetSocketAddress(InetAddress.getByName(address), port), 16);
        AtomicInteger number = new AtomicInteger();
        pool = new ThreadPoolExecutor(0, MAX_CLIENTS, 30, TimeUnit.SECONDS, new SynchronousQueue<>(), r -> {
            Thread t = new Thread(r, "CityCore-API-" + number.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
        running = true;
        Thread acceptor = new Thread(this::accept, "CityCore-API");
        acceptor.setDaemon(true);
        acceptor.start();
    }

    void stop() {
        running = false;
        try {
            if (socket != null) socket.close();
        } catch (IOException ignored) {
            // Déjà fermé
        }
        if (pool != null) pool.shutdownNow();
    }

    private void accept() {
        while (running) {
            Socket client;
            try {
                client = socket.accept();
            } catch (IOException e) {
                if (running) CityCoreCompanion.LOGGER.warn("API Discord : connexion refusée ({})", e.toString());
                continue;
            }
            try {
                pool.execute(() -> serve(client));
            } catch (RejectedExecutionException busy) {
                close(client); // trop de demandes en même temps : le bot réessaiera
            }
        }
    }

    private void serve(Socket client) {
        try (client) {
            client.setSoTimeout(READ_TIMEOUT_MS);
            InputStream in = client.getInputStream();
            OutputStream out = client.getOutputStream();
            Response response;
            try {
                response = handle(in);
            } catch (IOException e) {
                return; // demande incomplète ou client parti
            } catch (Exception e) {
                CityCoreCompanion.LOGGER.warn("API Discord : demande en erreur ({})", e.toString());
                response = Response.error(500, "erreur interne");
            }
            byte[] body = response.json().getBytes(StandardCharsets.UTF_8);
            String head = "HTTP/1.1 " + response.status() + " " + reason(response.status()) + "\r\n"
                    + "Content-Type: application/json; charset=utf-8\r\n"
                    + "Content-Length: " + body.length + "\r\n"
                    + "Cache-Control: no-store\r\n"
                    + "Connection: close\r\n\r\n";
            out.write(head.getBytes(StandardCharsets.US_ASCII));
            out.write(body);
            out.flush();
        } catch (IOException ignored) {
            // Client parti avant la réponse
        }
    }

    private Response handle(InputStream in) throws Exception {
        String head = readHead(in);
        String[] lines = head.split("\r\n");
        String[] request = lines[0].split(" ");
        if (request.length < 2) return Response.error(400, "demande invalide");
        String method = request[0].toUpperCase(Locale.ROOT);

        Map<String, String> headers = new HashMap<>();
        for (int i = 1; i < lines.length; i++) {
            int colon = lines[i].indexOf(':');
            if (colon > 0) headers.put(lines[i].substring(0, colon).trim().toLowerCase(Locale.ROOT), lines[i].substring(colon + 1).trim());
        }

        // Jeton : comparaison à durée constante
        String auth = headers.getOrDefault("authorization", "");
        String given = auth.regionMatches(true, 0, "Bearer ", 0, 7) ? auth.substring(7).trim() : "";
        if (!MessageDigest.isEqual(given.getBytes(StandardCharsets.UTF_8), token)) {
            if (!refusalLogged) {
                refusalLogged = true;
                CityCoreCompanion.LOGGER.warn("API Discord : demande refusée (jeton absent ou incorrect). "
                        + "Le jeton du bot doit être celui de discord.jeton (config/citycore_companion.toml).");
            }
            return Response.error(401, "jeton absent ou incorrect");
        }
        onAuthorized.run();

        String body = "";
        if (method.equals("POST")) {
            int length;
            try {
                length = Integer.parseInt(headers.getOrDefault("content-length", "0"));
            } catch (NumberFormatException e) {
                return Response.error(400, "longueur invalide");
            }
            if (length < 0 || length > MAX_BODY) return Response.error(413, "corps trop long");
            body = new String(in.readNBytes(length), StandardCharsets.UTF_8);
        }

        String target = request[1];
        int mark = target.indexOf('?');
        String path = mark < 0 ? target : target.substring(0, mark);
        Map<String, String> query = new HashMap<>();
        if (mark >= 0) {
            for (String pair : target.substring(mark + 1).split("&")) {
                int eq = pair.indexOf('=');
                if (eq > 0) query.put(pair.substring(0, eq), URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8));
            }
        }
        return router.route(method, path, query, body);
    }

    /** Lit la ligne de demande et les en-têtes (jusqu'à la ligne vide). */
    private static String readHead(InputStream in) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(512);
        int state = 0; // progression dans « \r\n\r\n »
        while (state < 4) {
            int b = in.read();
            if (b < 0) throw new IOException("demande incomplète");
            buffer.write(b);
            if (buffer.size() > MAX_HEADER) throw new IOException("en-têtes trop longs");
            boolean expected = (state % 2 == 0) ? b == '\r' : b == '\n';
            state = expected ? state + 1 : (b == '\r' ? 1 : 0);
        }
        return buffer.toString(StandardCharsets.ISO_8859_1);
    }

    private static String reason(int status) {
        return switch (status) {
            case 200 -> "OK";
            case 400 -> "Bad Request";
            case 401 -> "Unauthorized";
            case 404 -> "Not Found";
            case 405 -> "Method Not Allowed";
            case 413 -> "Payload Too Large";
            case 503 -> "Service Unavailable";
            default  -> "Error";
        };
    }

    private static void close(Socket client) {
        try {
            client.close();
        } catch (IOException ignored) {
            // Rien à faire
        }
    }
}
