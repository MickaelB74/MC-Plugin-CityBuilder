package com.citycore.ui;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Pack de ressources « Valmont » : construit au démarrage à partir des images embarquées dans le
 * plugin, hébergé (mini serveur web intégré ou adresse externe) et proposé à chaque connexion.
 *
 * Il ne contient que des images de menus, une police et le modèle d'un objet invisible : aucune
 * texture de bloc ni d'objet existant n'est modifiée. Un joueur qui refuse le pack garde les
 * menus classiques.
 */
public final class UiPack implements Listener {

    /** Identifiant stable du pack (le client le remplace quand son empreinte change). */
    public static final UUID PACK_ID = UUID.nameUUIDFromBytes("citycore:ui-pack".getBytes(StandardCharsets.UTF_8));

    private static final String PACK_DIR    = "pack/";
    private static final String TEXTURE_DIR = "pack/assets/citycore/textures/ui/";
    private static final int    PACK_FORMAT = 34;                 // Minecraft 1.21 – 1.21.1
    private static final long   ZIP_TIME    = 1_577_836_800_000L; // date fixe : zip reproductible
    private static final Gson   GSON        = new GsonBuilder().disableHtmlEscaping().create();

    private final JavaPlugin    plugin;
    private final File          pluginJar;
    private final UiAtlas       atlas;
    private final NamespacedKey classicKey;
    private final boolean       enabled;
    private final boolean       required;
    private final Set<UUID>     ready = ConcurrentHashMap.newKeySet();

    private byte[]          zip;
    private byte[]          sha1;
    private String          sha1Hex = "";
    private String          url;
    private HttpServer      server;
    private ExecutorService httpPool;

    public UiPack(JavaPlugin plugin, File pluginJar, UiAtlas atlas) {
        this.plugin     = plugin;
        this.pluginJar  = pluginJar;
        this.atlas      = atlas;
        this.classicKey = new NamespacedKey(plugin, "ui_classic");
        this.enabled    = plugin.getConfig().getBoolean("ui-pack.enabled", true);
        this.required   = plugin.getConfig().getBoolean("ui-pack.required", false);
    }

    public boolean enabled() { return enabled && url != null; }

    /* =========================
       CHARGEMENT DES IMAGES
       ========================= */

    /** Lit les images du pack (dans le jar) : sprites et police. */
    public void loadAssets() throws IOException {
        BufferedImage font = null, fontShadow = null, fontSmall = null;
        try (JarFile jar = new JarFile(pluginJar)) {
            List<JarEntry> entries = new ArrayList<>(Collections.list(jar.entries()));
            entries.sort((a, b) -> a.getName().compareTo(b.getName()));
            for (JarEntry e : entries) {
                String name = e.getName();
                if (!name.startsWith(TEXTURE_DIR) || !name.endsWith(".png")) continue;
                String key = name.substring(TEXTURE_DIR.length(), name.length() - 4);
                BufferedImage img;
                try (InputStream in = jar.getInputStream(e)) { img = ImageIO.read(in); }
                switch (key) {
                    case "font"        -> font = img;
                    case "font_shadow" -> fontShadow = img;
                    case "font_small"  -> fontSmall = img;
                    default            -> atlas.register(key, img);
                }
            }
        }
        List<String> rows = new ArrayList<>();
        try (InputStream in = plugin.getResource("ui/font_chars.txt")) {
            if (in == null) throw new IOException("ui/font_chars.txt absent du plugin");
            // \R : fins de ligne Unix ou Windows (git peut convertir le fichier en CRLF)
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\\R")) {
                if (line.isEmpty()) continue;
                if (line.length() > 16) {
                    throw new IOException("ligne de police de " + line.length() + " caractères (16 au plus)");
                }
                rows.add(line);
            }
        }
        if (font == null || fontShadow == null || fontSmall == null) throw new IOException("police du pack absente");
        atlas.registerFont(rows, font, fontShadow);
        atlas.registerSmallFont(readRows("ui/font_small_chars.txt"), fontSmall);
    }

    /** Lignes de caractères d'une planche de police (fins de ligne Unix ou Windows). */
    private List<String> readRows(String resource) throws IOException {
        List<String> rows = new ArrayList<>();
        try (InputStream in = plugin.getResource(resource)) {
            if (in == null) throw new IOException(resource + " absent du plugin");
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\\R")) {
                if (line.isEmpty()) continue;
                if (line.length() > 16) throw new IOException(resource + " : ligne de " + line.length() + " caractères");
                rows.add(line);
            }
        }
        return rows;
    }

    /* =========================
       CONSTRUCTION DU ZIP
       ========================= */

    public void build() throws IOException {
        Map<String, byte[]> files = new TreeMap<>();
        try (JarFile jar = new JarFile(pluginJar)) {
            for (JarEntry e : Collections.list(jar.entries())) {
                if (e.isDirectory() || !e.getName().startsWith(PACK_DIR)) continue;
                try (InputStream in = jar.getInputStream(e)) {
                    files.put(e.getName().substring(PACK_DIR.length()), in.readAllBytes());
                }
            }
        }

        JsonObject meta = new JsonObject();
        JsonObject pack = new JsonObject();
        pack.addProperty("pack_format", PACK_FORMAT);
        pack.addProperty("description", "§6CityCore §7— menus des habitants de Valmont");
        meta.add("pack", pack);
        files.put("pack.mcmeta", json(meta));

        // Polices : fonds + espacements, puis une police par hauteur d'affichage
        UiFonts.generate(atlas).forEach((path, font) -> files.put(path, json(font)));

        // Objet invisible : modèle du papier inchangé + variante transparente
        JsonObject paper = new JsonObject();
        paper.addProperty("parent", "minecraft:item/generated");
        JsonObject tex = new JsonObject();
        tex.addProperty("layer0", "minecraft:item/paper");
        paper.add("textures", tex);
        JsonArray overrides = new JsonArray();
        JsonObject override = new JsonObject();
        JsonObject predicate = new JsonObject();
        predicate.addProperty("custom_model_data", UiItems.BLANK_MODEL);
        override.add("predicate", predicate);
        override.addProperty("model", "citycore:item/blank");
        overrides.add(override);
        paper.add("overrides", overrides);
        files.put("assets/minecraft/models/item/paper.json", json(paper));
        JsonObject blank = new JsonObject();
        blank.addProperty("parent", "minecraft:item/generated");
        JsonObject blankTex = new JsonObject();
        blankTex.addProperty("layer0", "citycore:item/blank");
        blank.add("textures", blankTex);
        files.put("assets/citycore/models/item/blank.json", json(blank));
        files.put("assets/citycore/textures/item/blank.png", png(new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB)));

        // Barre de boss blanche rendue transparente : elle sert de support au panneau
        // « Quêtes actives » (seul son titre, dessiné par la police, reste visible)
        byte[] clearBar = png(new BufferedImage(182, 5, BufferedImage.TYPE_INT_ARGB));
        files.put("assets/minecraft/textures/gui/sprites/boss_bar/white_background.png", clearBar);
        files.put("assets/minecraft/textures/gui/sprites/boss_bar/white_progress.png", clearBar);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(out)) {
            for (Map.Entry<String, byte[]> f : files.entrySet()) {
                ZipEntry entry = new ZipEntry(f.getKey());
                entry.setTime(ZIP_TIME);
                zos.putNextEntry(entry);
                zos.write(f.getValue());
                zos.closeEntry();
            }
        }
        zip = out.toByteArray();
        try {
            sha1 = MessageDigest.getInstance("SHA-1").digest(zip);
        } catch (Exception e) {
            throw new IOException(e);
        }
        StringBuilder hex = new StringBuilder();
        for (byte b : sha1) hex.append(String.format("%02x", b));
        sha1Hex = hex.toString();

        File dir = new File(plugin.getDataFolder(), "ui-pack");
        dir.mkdirs();
        Files.write(new File(dir, "CityCore-UI.zip").toPath(), zip);
        plugin.getLogger().info("🎨 Pack d'interface construit : " + atlas.sprites().size() + " images, "
                + (zip.length / 1024) + " Ko (plugins/" + plugin.getName() + "/ui-pack/CityCore-UI.zip).");
    }

    private static byte[] json(JsonObject o) {
        return GSON.toJson(o).getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] png(BufferedImage img) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    /* =========================
       HÉBERGEMENT
       ========================= */

    public void startHosting() {
        if (!enabled || zip == null) return;
        String external = plugin.getConfig().getString("ui-pack.url", "").trim();
        if (!external.isEmpty()) {
            url = external;
            plugin.getLogger().info("🎨 Pack d'interface proposé depuis " + url
                    + " (le fichier doit être identique à ui-pack/CityCore-UI.zip, empreinte " + sha1Hex + ").");
            return;
        }
        if (!plugin.getConfig().getBoolean("ui-pack.self-host", true)) {
            plugin.getLogger().warning("⚠ Pack d'interface : ni ui-pack.url ni hébergement intégré — menus classiques.");
            return;
        }
        int port = plugin.getConfig().getInt("ui-pack.port", 8164);
        String path = "/citycore-ui-" + sha1Hex.substring(0, 12) + ".zip";
        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/", exchange -> serve(exchange, path));
            httpPool = Executors.newFixedThreadPool(2, r -> {
                Thread t = new Thread(r, "CityCore-UI-pack");
                t.setDaemon(true);
                return t;
            });
            server.setExecutor(httpPool);
            server.start();
            url = "http://" + publicAddress() + ":" + port + path;
            plugin.getLogger().info("🎨 Pack d'interface servi sur " + url);
            plugin.getLogger().info("   Joueurs venant d'Internet : ouvrez le port " + port
                    + " et renseignez ui-pack.public-address (IP publique ou nom de domaine).");
        } catch (IOException e) {
            plugin.getLogger().warning("⚠ Port " + port + " indisponible pour le pack d'interface ("
                    + e.getMessage() + ") — changez ui-pack.port ou utilisez ui-pack.url. Menus classiques en attendant.");
        } catch (LinkageError e) {
            // Java sans le module jdk.httpserver (image minimale) : hébergement intégré impossible
            plugin.getLogger().warning("⚠ Hébergement intégré indisponible sur ce Java — déposez ui-pack/CityCore-UI.zip"
                    + " en ligne et renseignez ui-pack.url. Menus classiques en attendant.");
        }
    }

    private void serve(HttpExchange exchange, String path) throws IOException {
        try {
            if (!exchange.getRequestURI().getPath().equals(path)) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }
            exchange.getResponseHeaders().set("Content-Type", "application/zip");
            exchange.sendResponseHeaders(200, zip.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(zip); }
        } finally {
            exchange.close();
        }
    }

    private String publicAddress() {
        String configured = plugin.getConfig().getString("ui-pack.public-address", "").trim();
        if (!configured.isEmpty()) return configured;
        String ip = Bukkit.getIp();
        if (ip != null && !ip.isBlank() && !ip.equals("0.0.0.0")) return ip;
        try {
            Enumeration<NetworkInterface> nics = NetworkInterface.getNetworkInterfaces();
            while (nics.hasMoreElements()) {
                NetworkInterface nic = nics.nextElement();
                if (!nic.isUp() || nic.isLoopback() || nic.isVirtual()) continue;
                for (InetAddress addr : Collections.list(nic.getInetAddresses())) {
                    if (addr instanceof Inet4Address && addr.isSiteLocalAddress()) return addr.getHostAddress();
                }
            }
        } catch (Exception ignored) {
            // pas d'interface réseau lisible : adresse locale
        }
        return "127.0.0.1";
    }

    public void stop() {
        if (server != null) server.stop(0);
        if (httpPool != null) httpPool.shutdownNow();
    }

    /* =========================
       ENVOI ET ÉTAT PAR JOUEUR
       ========================= */

    /** Propose le pack aux joueurs déjà connectés (redémarrage du plugin). */
    public void offerToOnlinePlayers() {
        if (!enabled()) return;
        for (Player p : Bukkit.getOnlinePlayers()) if (!prefersClassic(p)) send(p);
    }

    public void send(Player player) {
        if (!enabled()) return;
        player.addResourcePack(PACK_ID, url, sha1,
                "§6Menus illustrés des habitants de Valmont§7 (aucun bloc modifié).", required);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!enabled() || prefersClassic(player)) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> { if (player.isOnline()) send(player); }, 20L);
    }

    private final Set<UUID> warned = new HashSet<>();

    @EventHandler
    public void onStatus(PlayerResourcePackStatusEvent event) {
        if (!PACK_ID.equals(event.getID())) return;
        Player player = event.getPlayer();
        switch (event.getStatus()) {
            case SUCCESSFULLY_LOADED -> ready.add(player.getUniqueId());
            case DECLINED -> {
                ready.remove(player.getUniqueId());
                player.sendMessage("§7Pack d'interface refusé : les menus classiques restent utilisés."
                        + " §8(/city ui valmont pour le reproposer)");
            }
            case FAILED_DOWNLOAD, INVALID_URL, FAILED_RELOAD, DISCARDED -> {
                ready.remove(player.getUniqueId());
                player.sendMessage("§cLe pack d'interface n'a pas pu être chargé : menus classiques.");
                if (warned.add(player.getUniqueId())) {
                    plugin.getLogger().warning("⚠ " + player.getName() + " : pack d'interface " + event.getStatus()
                            + " (adresse " + url + ") — vérifiez ui-pack.public-address / ui-pack.port.");
                }
            }
            default -> { }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        ready.remove(event.getPlayer().getUniqueId());
    }

    /** Le joueur voit-il les menus illustrés ? */
    public boolean isReady(Player player) {
        return enabled() && ready.contains(player.getUniqueId()) && !prefersClassic(player);
    }

    public boolean isLoaded(Player player) {
        return ready.contains(player.getUniqueId());
    }

    /* =========================
       PRÉFÉRENCE DU JOUEUR (/city ui)
       ========================= */

    public boolean prefersClassic(Player player) {
        return player.getPersistentDataContainer().has(classicKey, PersistentDataType.BYTE);
    }

    public void setClassic(Player player, boolean classic) {
        if (classic) player.getPersistentDataContainer().set(classicKey, PersistentDataType.BYTE, (byte) 1);
        else player.getPersistentDataContainer().remove(classicKey);
    }
}
