package com.citycore.discordbot;

import net.dv8tion.jda.api.utils.data.DataObject;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Client de l'API du mod compagnon (module Discord du serveur Minecraft) : état du serveur et de
 * la ville, journal des événements, envoi d'un message aux joueurs. Chaque demande porte le jeton.
 */
final class ApiClient {

    /** Durée pendant laquelle le serveur garde une demande d'événements ouverte (secondes). */
    static final int WAIT_SECONDS = 25;

    private final HttpClient http = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private final String base;
    private final String token;

    ApiClient(String base, String token) {
        this.base = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        this.token = token;
    }

    /** {@code GET /api/v1/etat} : serveur, dernier numéro d'événement, état écrit par le plugin. */
    DataObject state() throws IOException, InterruptedException {
        return send(request("/api/v1/etat", 10).GET().build());
    }

    /**
     * {@code GET /api/v1/evenements} : ce qui suit le numéro {@code after} (réponse retenue par le
     * serveur tant qu'il n'y a rien, {@link #WAIT_SECONDS} au plus) ; {@code after} négatif :
     * seulement le dernier numéro connu.
     */
    DataObject events(long after) throws IOException, InterruptedException {
        String path = after < 0 ? "/api/v1/evenements"
                : "/api/v1/evenements?apres=" + after + "&attente=" + WAIT_SECONDS;
        return send(request(path, WAIT_SECONDS + 15).GET().build());
    }

    /** {@code POST /api/v1/chat} : message affiché aux joueurs ; renvoie le nombre de joueurs connectés. */
    int chat(String author, String message) throws IOException, InterruptedException {
        String body = DataObject.empty().put("auteur", author).put("message", message).toString();
        DataObject answer = send(request("/api/v1/chat", 10)
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build());
        return answer.getInt("joueurs", 0);
    }

    private HttpRequest.Builder request(String path, int timeoutSeconds) {
        return HttpRequest.newBuilder(URI.create(base + path))
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .header("Authorization", "Bearer " + token);
    }

    private DataObject send(HttpRequest request) throws IOException, InterruptedException {
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() == 401) throw new RefusedException();
        if (response.statusCode() != 200) throw new IOException("réponse " + response.statusCode() + " de l'API");
        try {
            return DataObject.fromJson(response.body());
        } catch (RuntimeException e) {
            throw new IOException("réponse illisible de l'API");
        }
    }

    /** Le serveur répond mais refuse le jeton : erreur de configuration, à signaler clairement. */
    static final class RefusedException extends IOException {
        RefusedException() {
            super("jeton refusé par l'API");
        }
    }
}
