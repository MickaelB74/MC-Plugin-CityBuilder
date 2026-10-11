package com.citycore.journeymap;

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
 * Tient les cartes des joueurs à jour : relit {@code carte.json} quand il change, calcule les
 * contours et les envoie à chaque joueur (à la connexion, au changement de dimension, et à chaque
 * modification des parcelles). Tout se passe sur le fil du serveur (événements NeoForge).
 */
final class MapSync {

    /** Relecture du fichier (s'il a changé) : toutes les 5 s. */
    private static final int CHECK_TICKS = 20 * 5;
    /** Renvoi de sécurité à tous les joueurs : toutes les 2 min (JourneyMap remplace, sans doublon). */
    private static final int REFRESH_TICKS = 20 * 120;
    /** Délais d'envoi après la connexion : JourneyMap du joueur doit d'abord avoir démarré. */
    private static final int[] LOGIN_DELAYS = {20 * 5, 20 * 30};
    private static final int DIMENSION_DELAY = 20 * 2;
    /** Hauteur des points (sans effet sur le tracé vu de dessus). */
    private static final int Y = 64;
    /** Zoom de JourneyMap : visible du plus éloigné (plein écran) au plus rapproché. */
    private static final int MIN_ZOOM = 2, MAX_ZOOM = 16384;
    private static final int DISPLAY_ORDER = 1000;

    private final Path file = FMLPaths.GAMEDIR.get().resolve("plugins").resolve("CityCore").resolve("carte.json");

    /** Envoi différé à un joueur. */
    private record Pending(UUID player, int due) {}

    private final List<Pending> pending = new ArrayList<>();
    private List<ServerPolygon> overlays = List.of();
    private Set<String> shownIds = Set.of();
    /** Date et taille du fichier lu en dernier (−1 : absent) ; MIN_VALUE : pas encore lu. */
    private long stamp = Long.MIN_VALUE;
    private int tick;
    private boolean sendFailureLogged;

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        tick++;
        if (tick % CHECK_TICKS == 0) reloadIfChanged(server);
        if (tick % REFRESH_TICKS == 0) {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) send(p);
        }
        // Envois différés (connexion, changement de dimension)
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
        pending.add(new Pending(player.getUUID(), tick + DIMENSION_DELAY));
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
    }

    /** Fichier modifié (parcelle ajoutée ou rendue, réglage changé) : contours recalculés et renvoyés. */
    private void reloadIfChanged(MinecraftServer server) {
        long now;
        try {
            now = Files.exists(file) ? Files.getLastModifiedTime(file).toMillis() * 31 + Files.size(file) : -1;
        } catch (Exception e) {
            return;
        }
        if (now == stamp) return;
        List<ServerPolygon> next;
        try {
            next = now == -1 ? List.of() : build(CityMap.parse(Files.readString(file, StandardCharsets.UTF_8)));
        } catch (Exception e) {
            // Fichier en cours d'écriture ou abîmé : on garde la carte actuelle, nouvel essai dans 5 s
            CityCoreJourneyMap.LOGGER.warn("carte.json illisible ({}), carte inchangée", e.toString());
            return;
        }
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
                        api.getOverlayApi().remove(p, CityCoreJourneyMap.MOD_ID, id);
                    } catch (Throwable ignored) {
                        // Joueur sans JourneyMap : rien à retirer
                    }
                }
            }
            send(p);
        }
        CityCoreJourneyMap.LOGGER.info("Bordures de la ville : {} zone(s) envoyée(s) aux cartes JourneyMap",
                next.size());
    }

    /** Envoie la zone de la ville à un joueur (remplace la version précédente sur sa carte). */
    private void send(ServerPlayer player) {
        IServerAPI api = CityCoreJmPlugin.api();
        if (api == null || overlays.isEmpty()) return;
        try {
            api.getOverlayApi().show(player, CityCoreJourneyMap.MOD_ID, overlays.toArray(new ServerPolygon[0]));
        } catch (Throwable t) {
            // Par exemple un joueur sans JourneyMap : on le signale une seule fois
            if (!sendFailureLogged) {
                sendFailureLogged = true;
                CityCoreJourneyMap.LOGGER.warn("Envoi de la carte impossible à {} : {}",
                        player.getGameProfile().getName(), t.toString());
            }
        }
    }

    /** Une superposition par dimension : toutes les zones de la ville, avec leurs trous. */
    @SuppressWarnings("deprecation")
    static List<ServerPolygon> build(CityMap map) {
        List<ServerPolygon> result = new ArrayList<>();
        OverlayShapeProps props = new OverlayShapeProps(
                map.color(), clamp01(map.fill()),
                map.color(), Math.max(0, map.width()), clamp01(map.stroke()),
                DISPLAY_ORDER, MIN_ZOOM, MAX_ZOOM,
                EnumSet.allOf(Context.UI.class), EnumSet.allOf(Context.MapType.class),
                map.name(), map.info());
        for (Map.Entry<String, List<long[]>> zone : map.zones().entrySet()) {
            ResourceKey<Level> dimension;
            try {
                dimension = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(zone.getKey()));
            } catch (Exception e) {
                CityCoreJourneyMap.LOGGER.warn("Dimension inconnue dans carte.json : {}", zone.getKey());
                continue;
            }
            List<OverlayPolygon> polygons = new ArrayList<>();
            for (ChunkOutline.Shape shape : ChunkOutline.of(zone.getValue())) {
                List<OverlayPoints> holes = new ArrayList<>();
                for (ChunkOutline.Ring hole : shape.holes()) holes.add(points(hole));
                // Pas de trou : null, comme dans l'exemple officiel de l'API
                polygons.add(new OverlayPolygon(points(shape.outer()), holes.isEmpty() ? null : holes));
            }
            if (polygons.isEmpty()) continue;
            String id = "ville-" + zone.getKey().replace(':', '-').replace('/', '-');
            result.add(new ServerPolygon(id, dimension, polygons, props));
        }
        return result;
    }

    /** Coins d'une boucle au format de JourneyMap ({@code BlockPos.asLong}). */
    private static OverlayPoints points(ChunkOutline.Ring ring) {
        List<Long> packed = new ArrayList<>(ring.points().size());
        for (int[] p : ring.points()) packed.add(BlockPos.asLong(p[0], Y, p[1]));
        return new OverlayPoints(packed);
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }
}
