package com.citycore.companion.journeymap;

import com.citycore.companion.CityCoreCompanion;
import com.citycore.companion.Settings;
import journeymap.api.v2.client.display.Context;
import journeymap.api.v2.server.IServerAPI;
import journeymap.api.v2.server.overlay.OverlayPoints;
import journeymap.api.v2.server.overlay.OverlayPolygon;
import journeymap.api.v2.server.overlay.OverlayShapeProps;
import journeymap.api.v2.server.overlay.ServerPolygon;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Tient les cartes JourneyMap des joueurs à jour : relit {@code carte.json} quand il change (claim,
 * unclaim, création de la ville, réglage modifié), calcule les tracés et les envoie à chaque
 * joueur par l'API serveur de JourneyMap :
 * <ul>
 *   <li><b>la ville</b> : chaque parcelle remplie d'une teinte translucide, contour extérieur
 *       seulement (les côtés partagés entre parcelles disparaissent, les trous restent), nom de la
 *       ville et bulle au survol ;</li>
 *   <li><b>le quadrillage</b> (facultatif) : contour fin de chaque parcelle, sous la ville.</li>
 * </ul>
 * Envois : à chaque modification du fichier, après la connexion et un changement de dimension,
 * et par sécurité à intervalle régulier (JourneyMap remplace la version précédente, sans
 * doublon ; il ne garde rien d'une session à l'autre). Tout se passe sur le fil du serveur
 * (événements NeoForge).
 */
final class MapSync {

    /** Délais d'envoi après la connexion : JourneyMap du joueur doit d'abord avoir démarré. */
    private static final int[] LOGIN_DELAYS = {20 * 5, 20 * 30};
    /** Délais d'envoi après un changement de dimension. */
    private static final int[] DIMENSION_DELAYS = {20 * 2, 20 * 10};
    /** Sans API de JourneyMap une minute après le démarrage : avertissement (version trop ancienne ?). */
    private static final int API_WARNING_TICKS = 20 * 60;
    /** Hauteur des points (sans effet sur le tracé vu de dessus). */
    private static final int Y = 64;
    /** Zoom de JourneyMap : visible du plus éloigné (plein écran) au plus rapproché (UIState de l'API). */
    private static final int MIN_ZOOM = 2, MAX_ZOOM = 16384;
    /** Ordre d'affichage : la ville (remplissage + contour) passe au-dessus du quadrillage. */
    private static final int ORDER_GRID = 1000, ORDER_CITY = 1001;

    /** Envoi différé à un joueur. */
    private record Pending(UUID player, int due) {}

    private final List<Pending> pending = new ArrayList<>();
    /** Superpositions actuelles (toutes dimensions) et leurs identifiants. */
    private List<ServerPolygon> overlays = List.of();
    private Set<String> shownIds = Set.of();
    /** Date et taille du fichier lu en dernier (−1 : absent). */
    private long stamp = Long.MIN_VALUE;
    /** Fichier abîmé déjà signalé (même date et taille) : pas de nouvel avertissement. */
    private long badStamp = Long.MIN_VALUE;
    private boolean reloadNow = true;
    private boolean disabled;
    private boolean apiWarned;
    private boolean sendFailureLogged;
    private boolean pathWarned;
    /** Joueurs dont un envoi a échoué (signalé une fois chacun, en journal détaillé). */
    private final Set<UUID> failed = new HashSet<>();
    private int tick;

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        tick++;

        // Module éteint dans les réglages (journeymap.actif) : la ville disparaît des cartes
        if (!Settings.JM_ENABLED.get()) {
            if (!disabled) disable(server);
            return;
        }
        if (disabled) {
            disabled = false;
            reloadNow = true;
        }

        if (!apiWarned && tick >= API_WARNING_TICKS && CityCoreJmPlugin.api() == null) {
            apiWarned = true;
            CityCoreCompanion.LOGGER.warn("API serveur de JourneyMap non reçue : la ville n'apparaîtra pas sur les cartes "
                    + "(JourneyMap 6.0.5 ou plus requis sur le serveur)");
        }

        if (reloadNow || tick % (Settings.JM_CHECK_SECONDS.get() * 20) == 0) {
            reloadNow = false;
            reloadIfChanged(server);
        }
        int resend = Settings.JM_RESEND_SECONDS.get() * 20;
        if (resend > 0 && tick % resend == 0) {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) send(p);
        }
        // Envois différés (connexion, changement de dimension)
        if (pending.isEmpty()) return;
        for (Iterator<Pending> it = pending.iterator(); it.hasNext(); ) {
            Pending p = it.next();
            if (p.due() > tick) continue;
            it.remove();
            ServerPlayer player = server.getPlayerList().getPlayer(p.player());
            if (player != null) send(player);
        }
    }

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        for (int delay : LOGIN_DELAYS) pending.add(new Pending(player.getUUID(), tick + delay));
    }

    @SubscribeEvent
    public void onDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        for (int delay : DIMENSION_DELAYS) pending.add(new Pending(player.getUUID(), tick + delay));
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        pending.removeIf(p -> p.player().equals(id));
    }

    /** Serveur arrêté (partie solo relancée…) : tout repart de zéro. */
    @SubscribeEvent
    public void onStopped(ServerStoppedEvent event) {
        pending.clear();
        overlays = List.of();
        shownIds = Set.of();
        stamp = Long.MIN_VALUE;
        badStamp = Long.MIN_VALUE;
        reloadNow = true;
        failed.clear();
    }

    /* ───────────────────────── Fichier ───────────────────────── */

    /** Fichier modifié (parcelle ajoutée ou rendue, réglage changé) : tracés recalculés et renvoyés. */
    private void reloadIfChanged(MinecraftServer server) {
        Path file;
        long now;
        try {
            file = FMLPaths.GAMEDIR.get().resolve(Settings.JM_FILE.get());
            now = Files.exists(file) ? Files.getLastModifiedTime(file).toMillis() * 31 + Files.size(file) : -1;
        } catch (Exception e) {
            if (!pathWarned) {
                pathWarned = true;
                CityCoreCompanion.LOGGER.warn("Fichier de la carte inaccessible ({}) : réglage journeymap.fichier", e.toString());
            }
            return;
        }
        if (now == stamp) return;
        CityMap map = null;
        List<ServerPolygon> next;
        try {
            if (now == -1) {
                next = List.of();
            } else {
                map = CityMap.parse(Files.readString(file, StandardCharsets.UTF_8));
                next = build(map);
            }
        } catch (Exception e) {
            // Fichier en cours d'écriture ou abîmé : on garde la carte actuelle, nouvel essai au prochain contrôle
            if (now != badStamp) {
                badStamp = now;
                CityCoreCompanion.LOGGER.warn("{} illisible ({}), carte inchangée", file.getFileName(), e.toString());
            }
            return;
        }
        boolean first = stamp == Long.MIN_VALUE;
        stamp = now;
        Set<String> nextIds = new HashSet<>();
        for (ServerPolygon p : next) nextIds.add(p.overlayId());
        Set<String> removed = new HashSet<>(shownIds);
        removed.removeAll(nextIds);
        overlays = next;
        shownIds = nextIds;

        IServerAPI api = CityCoreJmPlugin.api();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (api != null) {
                for (String id : removed) {
                    try {
                        api.getOverlayApi().remove(p, CityCoreCompanion.MOD_ID, id);
                    } catch (Throwable ignored) {
                        // Joueur sans JourneyMap : rien à retirer
                    }
                }
            }
            send(p);
        }
        if (now == -1) {
            CityCoreCompanion.LOGGER.info(first
                    ? "{} absent pour l'instant (écrit par le plugin CityCore) : rien à afficher sur les cartes"
                    : "{} absent : la ville est retirée des cartes", file);
            return;
        }
        int chunks = 0;
        for (List<long[]> zone : map.zones().values()) chunks += zone.size();
        CityCoreCompanion.LOGGER.info("Ville sur les cartes JourneyMap : {} parcelle(s), {} tracé(s){}{}",
                chunks, next.size(), map.grid() != null ? " avec quadrillage" : "",
                api == null ? " — en attente de l'API de JourneyMap" : ", envoyés aux joueurs connectés");
    }

    /* ───────────────────────── Envois ───────────────────────── */

    /** Envoie les tracés de la ville à un joueur (remplace la version précédente sur sa carte). */
    private void send(ServerPlayer player) {
        IServerAPI api = CityCoreJmPlugin.api();
        if (api == null || overlays.isEmpty()) return;
        try {
            api.getOverlayApi().show(player, CityCoreCompanion.MOD_ID, overlays.toArray(new ServerPolygon[0]));
            if (Settings.JM_LOG.get()) {
                CityCoreCompanion.LOGGER.info("Carte envoyée à {} ({} tracé(s))", player.getGameProfile().getName(), overlays.size());
            }
        } catch (Throwable t) {
            // Par exemple un joueur sans JourneyMap, ou une version de JourneyMap sans superpositions serveur
            boolean newPlayer = failed.add(player.getUUID());
            if (!sendFailureLogged || (newPlayer && Settings.JM_LOG.get())) {
                sendFailureLogged = true;
                CityCoreCompanion.LOGGER.warn("Envoi de la carte impossible à {} : {}",
                        player.getGameProfile().getName(), t.toString());
            }
        }
    }

    /** Module éteint : la ville est retirée des cartes des joueurs connectés. */
    private void disable(MinecraftServer server) {
        disabled = true;
        IServerAPI api = CityCoreJmPlugin.api();
        if (api != null && !shownIds.isEmpty()) {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                try {
                    api.getOverlayApi().clearAll(p, CityCoreCompanion.MOD_ID);
                } catch (Throwable ignored) {
                    // Joueur sans JourneyMap : rien à retirer
                }
            }
        }
        pending.clear();
        overlays = List.of();
        shownIds = Set.of();
        stamp = Long.MIN_VALUE;
        CityCoreCompanion.LOGGER.info("Module JourneyMap éteint (journeymap.actif = false) : ville retirée des cartes");
    }

    /* ───────────────────────── Tracés ───────────────────────── */

    /**
     * Par dimension : la ville (une superposition : toutes ses zones, avec leurs trous) et, si
     * demandé, le quadrillage (une superposition : un carré par parcelle).
     */
    @SuppressWarnings("deprecation") // Context : déplacé dans l'API pour Minecraft 26.2, inchangé en 1.21.1
    static List<ServerPolygon> build(CityMap map) {
        Set<Context.UI> uis = map.minimap()
                ? EnumSet.allOf(Context.UI.class)
                : EnumSet.of(Context.UI.Fullscreen, Context.UI.Webmap);
        Set<Context.MapType> types = EnumSet.allOf(Context.MapType.class);
        String label = map.name() == null || map.name().isBlank() ? null : map.name();
        OverlayShapeProps cityProps = new OverlayShapeProps(
                map.color(), clamp01(map.fill()),
                map.color(), Math.max(0, map.width()), clamp01(map.stroke()),
                ORDER_CITY, MIN_ZOOM, MAX_ZOOM, uis, types, label, map.info());
        CityMap.Grid grid = map.grid();
        boolean withGrid = grid != null && grid.width() > 0 && grid.opacity() > 0;
        // Quadrillage : contour seul (remplissage transparent), sans nom ni bulle (celle de la ville suffit)
        OverlayShapeProps gridProps = !withGrid ? null : new OverlayShapeProps(
                grid.color(), 0f,
                grid.color(), grid.width(), clamp01(grid.opacity()),
                ORDER_GRID, MIN_ZOOM, MAX_ZOOM, uis, types, null, null);

        List<ServerPolygon> result = new ArrayList<>();
        for (Map.Entry<String, List<long[]>> zone : map.zones().entrySet()) {
            ResourceKey<Level> dimension;
            try {
                dimension = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(zone.getKey()));
            } catch (Exception e) {
                CityCoreCompanion.LOGGER.warn("Dimension inconnue dans carte.json : {}", zone.getKey());
                continue;
            }
            String dimId = zone.getKey().replace(':', '-').replace('/', '-');

            List<OverlayPolygon> polygons = new ArrayList<>();
            for (ChunkOutline.Shape shape : ChunkOutline.of(zone.getValue())) {
                List<OverlayPoints> holes = new ArrayList<>();
                for (ChunkOutline.Ring hole : shape.holes()) holes.add(points(hole.points()));
                // Pas de trou : null, comme dans l'exemple officiel de l'API
                polygons.add(new OverlayPolygon(points(shape.outer().points()), holes.isEmpty() ? null : holes));
            }
            if (polygons.isEmpty()) continue;
            result.add(new ServerPolygon("ville-" + dimId, dimension, polygons, cityProps));

            if (withGrid) {
                List<OverlayPolygon> squares = new ArrayList<>(zone.getValue().size());
                for (long[] c : zone.getValue()) squares.add(new OverlayPolygon(points(square(c)), null));
                result.add(new ServerPolygon("quadrillage-" + dimId, dimension, squares, gridProps));
            }
        }
        return result;
    }

    /**
     * Coins d'une parcelle (en blocs), dans le sens de JourneyMap pour un bord extérieur : sud-ouest,
     * sud-est, nord-est, nord-ouest ({@code PolygonHelper.createChunkPolygon} de l'API).
     */
    private static List<int[]> square(long[] chunk) {
        int x = (int) chunk[0] * 16, z = (int) chunk[1] * 16;
        return List.of(new int[]{x, z + 16}, new int[]{x + 16, z + 16}, new int[]{x + 16, z}, new int[]{x, z});
    }

    /** Coins d'une boucle au format de JourneyMap ({@code BlockPos.asLong}). */
    private static OverlayPoints points(List<int[]> corners) {
        List<Long> packed = new ArrayList<>(corners.size());
        for (int[] p : corners) packed.add(BlockPos.asLong(p[0], Y, p[1]));
        return new OverlayPoints(packed);
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }
}
