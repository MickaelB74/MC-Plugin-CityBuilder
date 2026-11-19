package com.citycore.discord;

import com.citycore.building.BuildingManager;
import com.citycore.city.City;
import com.citycore.city.CityManager;
import com.citycore.inhabitant.InhabitantManager;
import com.citycore.needs.NeedsManager;
import com.citycore.npc.CityNPC;
import com.citycore.player.PlayerDataManager;
import com.citycore.quest.city.CityQuest;
import com.citycore.quest.city.CityQuestManager;
import com.citycore.quest.city.CityTier;
import com.citycore.raid.RaidManager;
import com.citycore.siege.EventCalendar;
import com.citycore.siege.SiegeManager;
import com.citycore.util.DatabaseManager;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.citycore.discord.DiscordFeed.plain;
import static com.citycore.discord.DiscordFeed.quote;

/**
 * État de la ville et des joueurs pour Discord : le fichier
 * {@code plugins/CityCore/discord/etat.json}, relu par le mod compagnon et servi au bot, qui en
 * tire la carte de la ville (salon « Valmont ») et une carte par joueur (salon « joueurs »).
 *
 * Recalculé à intervalle régulier sur le fil principal (base SQLite partagée, quelques requêtes
 * légères) ; écrit en arrière-plan, et seulement s'il a changé. La bourse des joueurs absents
 * n'est relue qu'à leur déconnexion et toutes les dix minutes.
 */
public final class DiscordState implements Listener {

    /** Durée de vie de la fiche d'un joueur absent avant d'être recalculée. */
    private static final long OFFLINE_TTL_MS = 10 * 60_000L;

    private final JavaPlugin        plugin;
    private final DatabaseManager   db;
    private final CityManager       city;
    private final CityQuestManager  cityQuests;
    private final Economy           economy;
    private final BuildingManager   buildings;
    private final InhabitantManager inhabitants;
    private final NeedsManager      needs;
    private final SiegeManager      siege;
    private final RaidManager       raids;
    private final EventCalendar     calendar;
    private final Path              file;

    /** Dernier contenu écrit (évite de réécrire un fichier identique). */
    private String last;
    /** Fiches des joueurs absents : uuid → {fiche JSON, date du calcul}. */
    private final Map<UUID, Object[]> offline = new HashMap<>();

    public DiscordState(JavaPlugin plugin, DatabaseManager db, CityManager city, CityQuestManager cityQuests,
                        Economy economy, BuildingManager buildings, InhabitantManager inhabitants,
                        NeedsManager needs, SiegeManager siege, RaidManager raids, EventCalendar calendar) {
        this.plugin      = plugin;
        this.db          = db;
        this.city        = city;
        this.cityQuests  = cityQuests;
        this.economy     = economy;
        this.buildings   = buildings;
        this.inhabitants = inhabitants;
        this.needs       = needs;
        this.siege       = siege;
        this.raids       = raids;
        this.calendar    = calendar;
        this.file        = DiscordFeed.folder(plugin).resolve("etat.json");
    }

    public void start() {
        long period = 20L * Math.max(2, plugin.getConfig().getInt("discord.etat-secondes", 10));
        Bukkit.getScheduler().runTaskTimer(plugin, this::export, 100L, period);
    }

    /** Un joueur qui arrive ou s'en va : sa fiche est recalculée au prochain passage. */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        offline.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        offline.remove(event.getPlayer().getUniqueId());
    }

    private void export() {
        String json;
        try {
            json = build();
        } catch (RuntimeException e) {
            plugin.getLogger().warning("Discord : état non calculé (" + e + ")");
            return;
        }
        if (json.equals(last)) return;
        last = json;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> write(json));
    }

    /* ───────────────────────── Contenu ───────────────────────── */

    private String build() {
        return "{\n  \"format\": 1,\n  \"ville\": " + cityJson() + ",\n  \"joueurs\": [" + playersJson() + "]\n}\n";
    }

    private String cityJson() {
        if (!city.isCityInitialized()) return "{\"fondee\": false}";
        City c = city.getCity();
        int level = cityQuests.getCityLevel();
        CityTier tier = CityTier.fromLevel(level);

        // Quêtes du chapitre en cours (hors bonus)
        int quests = 0, done = 0;
        for (CityQuest q : tier.quests) {
            if (q.optional()) continue;
            quests++;
            if (cityQuests.isCompleted(q.id())) done++;
        }
        int[] sieges = siegeResults();
        int raidWins = 0;
        for (int n = 1; n <= 7; n++) raidWins += raids.victories(n);
        long living = inhabitants.living().stream().filter(i -> !i.test).count();

        StringBuilder b = new StringBuilder("{\"fondee\": true");
        field(b, "nom", c.getName());
        field(b, "niveau", level);
        field(b, "palier", plain(tier.tierName));
        field(b, "chapitre", tier.chapter);
        field(b, "caisse", c.getCoins());
        field(b, "parcelles", c.getClaimedChunks());
        field(b, "parcellesMax", c.getMaxChunks());
        field(b, "habitants", (int) living);
        field(b, "lits", inhabitants.capacity());
        field(b, "satisfaction", needs.satisfaction());
        field(b, "artisans", settledArtisans());
        field(b, "batiments", buildings.getAllBuildings().size());
        field(b, "garde", inhabitants.guards().size());
        field(b, "recrues", inhabitants.trainees().size());
        field(b, "quetes", done);
        field(b, "quetesTotal", quests);
        field(b, "siegesGagnes", sieges[0]);
        field(b, "siegesPerdus", sieges[1]);
        field(b, "raidsGagnes", raidWins);
        b.append(", \"siegeEnCours\": ").append(siege.running());
        b.append(", \"raidEnCours\": ").append(raids.running());
        b.append(", \"calendrier\": [");
        boolean first = true;
        List<String> lines = calendar.listing();
        for (int i = 1; i < lines.size(); i++) { // la première ligne est le titre du planning
            // « (dans 3 h 12) » change chaque minute : retiré pour ne pas réécrire la carte sans cesse
            String line = plain(lines.get(i)).replaceAll("\\s*\\(dans [^)]*\\)", "");
            if (line.isBlank()) continue;
            if (!first) b.append(", ");
            first = false;
            b.append(quote(line));
        }
        return b.append("]}").toString();
    }

    /** Artisans et légendes arrivés en ville (tout personnage qui n'erre plus, hors Alderic). */
    private int settledArtisans() {
        try (PreparedStatement ps = db.getConnection().prepareStatement(
                "SELECT COUNT(*) FROM npc_data WHERE state != 'WANDERER' AND npc_tag != ?")) {
            ps.setString(1, CityNPC.MAYOR.tag);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            return 0;
        }
    }

    /** {sièges repoussés, sièges perdus}. */
    private int[] siegeResults() {
        int[] out = new int[2];
        try (PreparedStatement ps = db.getConnection().prepareStatement(
                "SELECT victory, COUNT(*) FROM siege_results GROUP BY victory")) {
            ResultSet rs = ps.executeQuery();
            while (rs.next()) out[rs.getInt(1) == 1 ? 0 : 1] = rs.getInt(2);
        } catch (SQLException ignored) {
            // Table absente d'une base très ancienne : zéro partout
        }
        return out;
    }

    /** Tous les joueurs connus du plugin (table players), connectés ou non. */
    private String playersJson() {
        StringBuilder b = new StringBuilder();
        long now = System.currentTimeMillis();
        try (PreparedStatement ps = db.getConnection().prepareStatement(
                "SELECT uuid, xp, level, job FROM players ORDER BY level DESC, xp DESC")) {
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                UUID id;
                try {
                    id = UUID.fromString(rs.getString("uuid"));
                } catch (IllegalArgumentException e) {
                    continue;
                }
                String entry = playerJson(id, rs.getInt("xp"), rs.getInt("level"), rs.getString("job"), now);
                if (entry == null) continue;
                if (!b.isEmpty()) b.append(",");
                b.append("\n    ").append(entry);
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Discord : joueurs non lus (" + e.getMessage() + ")");
        }
        return b.isEmpty() ? "" : b + "\n  ";
    }

    private String playerJson(UUID id, int xp, int level, String jobTag, long now) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(id);
        boolean online = player.isOnline();
        if (!online) {
            Object[] cached = offline.get(id);
            if (cached != null && now - (long) cached[1] < OFFLINE_TTL_MS) return (String) cached[0];
        }
        String name = player.getName();
        if (name == null) return null; // jamais venu sur ce serveur (ligne de test)
        CityNPC job = jobTag == null ? null : CityNPC.fromTag(jobTag);
        double balance;
        try {
            balance = economy.getBalance(player);
        } catch (RuntimeException e) {
            balance = 0; // compte inconnu du plugin d'économie
        }
        StringBuilder b = new StringBuilder("{\"uuid\": ").append(quote(id.toString()));
        field(b, "nom", name);
        b.append(", \"enLigne\": ").append(online);
        field(b, "niveau", level);
        field(b, "xp", xp);
        field(b, "xpRequis", PlayerDataManager.xpForNextLevel(level));
        field(b, "metier", job == null ? "" : plain(job.displayName));
        field(b, "bourse", (int) balance);
        b.append(", \"vuLe\": ").append(player.getLastPlayed());
        String json = b.append('}').toString();
        if (!online) offline.put(id, new Object[]{json, now});
        return json;
    }

    private static void field(StringBuilder b, String key, String value) {
        b.append(", \"").append(key).append("\": ").append(quote(value == null ? "" : value));
    }

    private static void field(StringBuilder b, String key, int value) {
        b.append(", \"").append(key).append("\": ").append(value);
    }

    /* ───────────────────────── Fichier ───────────────────────── */

    /** Écriture atomique : le mod ne lit jamais un fichier à moitié écrit. */
    private void write(String json) {
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling("etat.json.tmp");
            Files.writeString(tmp, json, StandardCharsets.UTF_8);
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicUnsupported) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Discord : etat.json non écrit (" + e.getMessage() + ")");
        }
    }
}
