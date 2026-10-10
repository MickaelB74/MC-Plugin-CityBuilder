package com.citycore.ui;

import com.citycore.npc.CityNPC;
import com.citycore.npc.NPCManager;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Base64;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.citycore.ui.UiLayout.*;

/**
 * Portraits des habitants : le buste (tête, torse, bras) est redessiné pixel par pixel à partir
 * du vrai skin du personnage, sur un décor lié à son métier.
 *
 * Les skins sont lus une fois (serveur de textures de Mojang) puis gardés en cache disque.
 * Sans skin connu ou sans accès réseau, une silhouette encapuchonnée le remplace.
 */
public final class UiPortraits {

    private static final Map<CityNPC, String> BACKDROPS = new EnumMap<>(CityNPC.class);
    static {
        BACKDROPS.put(CityNPC.MAYOR, "pf_mairie");
        BACKDROPS.put(CityNPC.WOODCUTTER, "pf_nature");
        BACKDROPS.put(CityNPC.FARMER, "pf_nature");
        BACKDROPS.put(CityNPC.STONEMASON, "pf_atelier");
        BACKDROPS.put(CityNPC.FISHERMAN, "pf_port");
        BACKDROPS.put(CityNPC.MINER, "pf_mine");
        BACKDROPS.put(CityNPC.INNKEEPER, "pf_taverne");
        BACKDROPS.put(CityNPC.BLACKSMITH, "pf_forge");
        BACKDROPS.put(CityNPC.WEAVER, "pf_atelier");
        BACKDROPS.put(CityNPC.STABLEMASTER, "pf_nature");
        BACKDROPS.put(CityNPC.GUARD, "pf_mairie");
        BACKDROPS.put(CityNPC.MONK, "pf_chapelle");
        BACKDROPS.put(CityNPC.SCRIBE, "pf_chapelle");
        BACKDROPS.put(CityNPC.GLASSMAKER, "pf_forge");
        BACKDROPS.put(CityNPC.GOLDSMITH, "pf_atelier");
        BACKDROPS.put(CityNPC.ROBIN, "pf_nature");
        BACKDROPS.put(CityNPC.JACKSPARROW, "pf_port");
        BACKDROPS.put(CityNPC.INDIANA, "pf_route");
        BACKDROPS.put(CityNPC.DOCBROWN, "pf_atelier");
        BACKDROPS.put(CityNPC.GANDALF, "pf_chapelle");
        BACKDROPS.put(CityNPC.GERALT, "pf_route");
        BACKDROPS.put(CityNPC.HAROLD, "pf_dragon");
    }

    private final JavaPlugin  plugin;
    private final NPCManager  npcManager;
    private final File        cacheDir;
    private final HttpClient  http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    /** Buste 16 x 20 en ARGB (0 = transparent), par personnage. */
    private final Map<CityNPC, int[]> busts = new ConcurrentHashMap<>();

    public UiPortraits(JavaPlugin plugin, NPCManager npcManager) {
        this.plugin     = plugin;
        this.npcManager = npcManager;
        this.cacheDir   = new File(plugin.getDataFolder(), "ui-pack/skins");
    }

    /** Charge en arrière-plan les skins de tous les personnages qui en ont un. */
    public void loadAsync() {
        cacheDir.mkdirs();
        for (CityNPC npc : CityNPC.values()) {
            String[] skin = npcManager.resolveSkin(npc);
            if (skin == null || skin[0] == null || skin[0].isBlank()) continue;
            String value = skin[0];
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> load(npc, value));
        }
    }

    private void load(CityNPC npc, String textureValue) {
        try {
            JsonObject root = JsonParser.parseString(new String(Base64.getDecoder().decode(textureValue),
                    StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject skin = root.getAsJsonObject("textures").getAsJsonObject("SKIN");
            String url = skin.get("url").getAsString().replace("http://", "https://");
            boolean slim = skin.has("metadata")
                    && "slim".equals(skin.getAsJsonObject("metadata").get("model").getAsString());
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(fetch(url)));
            if (image != null) busts.put(npc, bust(image, slim));
        } catch (Exception e) {
            plugin.getLogger().fine("Portrait de " + npc.skinId + " indisponible : " + e.getMessage());
        }
    }

    /** Télécharge une texture, avec cache disque (nom = empreinte de l'adresse). */
    private byte[] fetch(String url) throws Exception {
        StringBuilder name = new StringBuilder();
        for (byte b : MessageDigest.getInstance("SHA-1").digest(url.getBytes(StandardCharsets.UTF_8))) {
            name.append(String.format("%02x", b));
        }
        File cached = new File(cacheDir, name + ".png");
        if (cached.isFile()) return Files.readAllBytes(cached.toPath());
        HttpResponse<byte[]> response = http.send(HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(10)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200) throw new IllegalStateException("HTTP " + response.statusCode());
        Files.write(cached.toPath(), response.body());
        return response.body();
    }

    /**
     * Buste vu de face (16 x 20) : tête et chapeau, torse et veste, bras et manches.
     * Le bras droit du personnage apparaît à gauche de l'image.
     */
    static int[] bust(BufferedImage skin, boolean slim) {
        int[] out = new int[BUST_W * BUST_H];
        boolean legacy = skin.getHeight() < 64; // anciens skins 64 x 32 : bras gauche = miroir du droit
        int aw = slim ? 3 : 4;
        copy(skin, out, 8, 8, 8, 8, 4, 0, false);          // tête
        copy(skin, out, 40, 8, 8, 8, 4, 0, true);          // chapeau
        copy(skin, out, 20, 20, 8, 12, 4, 8, false);       // torse
        copy(skin, out, 44, 20, aw, 12, 4 - aw, 8, false); // bras droit
        if (legacy) {
            for (int y = 0; y < 12; y++) for (int x = 0; x < aw; x++) {
                set(out, 12 + x, 8 + y, rgb(skin, 44 + aw - 1 - x, 20 + y));
            }
        } else {
            copy(skin, out, 20, 36, 8, 12, 4, 8, true);        // veste
            copy(skin, out, 44, 36, aw, 12, 4 - aw, 8, true);  // manche droite
            copy(skin, out, 36, 52, aw, 12, 12, 8, false);     // bras gauche
            copy(skin, out, 52, 52, aw, 12, 12, 8, true);      // manche gauche
        }
        return out;
    }

    private static void copy(BufferedImage skin, int[] out, int sx, int sy, int w, int h,
                             int dx, int dy, boolean overlay) {
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = rgb(skin, sx + x, sy + y);
                if (overlay && (argb >>> 24) < 128) continue; // calque transparent : on garde la base
                set(out, dx + x, dy + y, argb);
            }
        }
    }

    private static int rgb(BufferedImage img, int x, int y) {
        if (x < 0 || y < 0 || x >= img.getWidth() || y >= img.getHeight()) return 0;
        return img.getRGB(x, y);
    }

    private static void set(int[] out, int x, int y, int argb) {
        if (x < 0 || y < 0 || x >= BUST_W || y >= BUST_H) return;
        out[y * BUST_W + x] = (argb >>> 24) < 128 ? 0 : (argb | 0xFF000000);
    }

    /* =========================
       DESSIN
       ========================= */

    /** Décor + buste (ou silhouette) dans le cadre du portrait. */
    public void draw(UiCanvas canvas, CityNPC npc, boolean wanderer) {
        canvas.sprite(wanderer ? "pf_route" : BACKDROPS.getOrDefault(npc, "pf_nature"), PORTRAIT_X, PORTRAIT_Y);
        int[] bust = busts.get(npc);
        if (bust == null) {
            canvas.sprite("silhouette", BUST_X, SILHOUETTE_Y);
            return;
        }
        int[] row = new int[BUST_W];
        for (int r = 0; r < BUST_H; r++) {
            System.arraycopy(bust, r * BUST_W, row, 0, BUST_W);
            canvas.pixelRow("px", BUST_X, BUST_Y + BUST_SCALE * r, row);
        }
    }
}
