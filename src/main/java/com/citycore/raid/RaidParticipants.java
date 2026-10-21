package com.citycore.raid;

import com.citycore.command.CityPermissions;
import com.citycore.util.DatabaseManager;
import net.citizensnpcs.api.event.NPCRightClickEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.world.PortalCreateEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Sécurité des joueurs dans le monde des raids (PLAN_RAIDS §2.9) : positions de retour (table
 * raid_returns), réapparition au camp après une mort (inventaire et expérience gardés, quelques
 * secondes d'invulnérabilité), reconnexion, entrée réservée au plugin, commandes de déplacement
 * refusées, pas de point d'apparition personnel ni de portail, héraut du camp.
 *
 * Hors d'un raid, personne ne se réveille dans ce monde : retour auprès d'Alderic (décision du
 * 21/10/2026 : connexion perdue, partie quittée, plantage, arrêt du serveur).
 */
final class RaidParticipants implements Listener {

    private final JavaPlugin plugin;
    private final DatabaseManager db;
    private final RaidManager raids;
    /** Joueurs que le plugin téléporte en ce moment (entrée dans le monde des raids permise). */
    private final Set<UUID> allowed = new HashSet<>();

    RaidParticipants(JavaPlugin plugin, DatabaseManager db, RaidManager raids) {
        this.plugin = plugin;
        this.db     = db;
        this.raids  = raids;
    }

    /* =========================
       POSITIONS DE RETOUR (raid_returns)
       ========================= */

    /** Position du joueur notée avant son départ au camp. */
    void saveReturn(Player p, long raidId) {
        Location l = p.getLocation();
        try (PreparedStatement ps = db.getConnection().prepareStatement(
                "INSERT OR REPLACE INTO raid_returns (uuid, world, x, y, z, yaw, pitch, raid_id, started_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            ps.setString(1, p.getUniqueId().toString());
            ps.setString(2, l.getWorld().getName());
            ps.setDouble(3, l.getX());
            ps.setDouble(4, l.getY());
            ps.setDouble(5, l.getZ());
            ps.setFloat(6, l.getYaw());
            ps.setFloat(7, l.getPitch());
            ps.setLong(8, raidId);
            ps.setLong(9, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("⚔ [Raids] Position de retour de " + p.getName() + " non enregistrée : " + e.getMessage());
        }
    }

    /** Position de retour notée, ou null (inconnue, ou monde disparu). */
    Location returnOf(UUID player) {
        try (PreparedStatement ps = db.getConnection().prepareStatement(
                "SELECT world, x, y, z, yaw, pitch FROM raid_returns WHERE uuid = ?")) {
            ps.setString(1, player.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                World w = Bukkit.getWorld(rs.getString("world"));
                if (w == null || raids.isRaidWorld(w)) return null;
                return new Location(w, rs.getDouble("x"), rs.getDouble("y"), rs.getDouble("z"),
                        rs.getFloat("yaw"), rs.getFloat("pitch"));
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("⚔ [Raids] Position de retour illisible : " + e.getMessage());
            return null;
        }
    }

    void deleteReturn(UUID player) {
        try (PreparedStatement ps = db.getConnection().prepareStatement("DELETE FROM raid_returns WHERE uuid = ?")) {
            ps.setString(1, player.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("⚔ [Raids] Position de retour non effacée : " + e.getMessage());
        }
    }

    /**
     * Démarrage : positions de retour d'un raid interrompu (plantage, arrêt) oubliées — ces
     * joueurs reviennent auprès d'Alderic.
     */
    int clearReturns() {
        try (PreparedStatement ps = db.getConnection().prepareStatement("DELETE FROM raid_returns")) {
            return ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("⚔ [Raids] Positions de retour non effacées : " + e.getMessage());
            return 0;
        }
    }

    /** Téléportation par le plugin : l'entrée dans le monde des raids est permise le temps qu'elle dure. */
    void allow(UUID player)    { allowed.add(player); }
    void disallow(UUID player) { allowed.remove(player); }

    /* =========================
       ÉVÉNEMENTS
       ========================= */

    /** Reconnexion : raid en cours et joueur engagé → camp ; sinon → auprès d'Alderic. */
    @EventHandler
    public void onSpawnLocation(org.spigotmc.event.player.PlayerSpawnLocationEvent event) {
        if (!raids.isRaidWorld(event.getSpawnLocation().getWorld())) return;
        Player p = event.getPlayer();
        RaidRun run = raids.run();
        if (run != null && run.deployed() && run.isEnrolled(p.getUniqueId()) && run.camp != null) {
            event.setSpawnLocation(run.camp.spawn());
            return;
        }
        event.setSpawnLocation(raids.fallbackReturn());
        deleteReturn(p.getUniqueId());
        plugin.getLogger().info("⚔ [Raids] " + p.getName() + " s'était déconnecté dans le monde des raids : retour auprès d'Alderic.");
    }

    /** Mort dans le monde des raids : inventaire et expérience gardés, rien ne tombe au sol. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent event) {
        Player p = event.getEntity();
        if (!raids.isRaidWorld(p.getWorld())) return;
        event.setKeepInventory(true);
        event.getDrops().clear();
        event.setKeepLevel(true);
        event.setDroppedExp(0);
        RaidRun run = raids.run();
        if (run != null && run.isEnrolled(p.getUniqueId())) run.deaths.merge(p.getUniqueId(), 1, Integer::sum);
    }

    /**
     * Réapparition : joueur engagé → au camp, à côté de la structure, avec quelques secondes
     * d'invulnérabilité (jamais au lit ni au point d'apparition du monde) ; sinon → auprès d'Alderic.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent event) {
        Player p = event.getPlayer();
        if (!raids.isRaidWorld(p.getWorld())) return;
        Location home = raids.takeHomeOnRespawn(p.getUniqueId());
        if (home != null) {
            event.setRespawnLocation(home); // mort au moment du retour : il rentre avec les autres
            return;
        }
        RaidRun run = raids.run();
        if (run != null && run.deployed() && run.isEnrolled(p.getUniqueId()) && run.camp != null) {
            event.setRespawnLocation(run.camp.spawn());
            int ticks = raids.config().respawnInvulnerableSeconds * 20;
            if (ticks > 0) Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (p.isOnline()) p.setNoDamageTicks(ticks);
            }, 1L);
            return;
        }
        event.setRespawnLocation(raids.fallbackReturn());
        deleteReturn(p.getUniqueId());
    }

    /**
     * Entrée dans le monde des raids : seulement par le plugin (et l'administration). Un joueur
     * engagé qui en sort par un autre moyen est considéré comme rentré de lui-même.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Location to = event.getTo();
        if (to == null || to.getWorld() == null) return;
        Player p = event.getPlayer();
        boolean fromRaid = raids.isRaidWorld(event.getFrom().getWorld());
        boolean toRaid = raids.isRaidWorld(to.getWorld());
        if (toRaid && !fromRaid && !allowed.contains(p.getUniqueId()) && !p.hasPermission(CityPermissions.ADMIN_RAID)) {
            event.setCancelled(true);
            p.sendMessage("§7⚔ Ce monde ne s'ouvre qu'avec une expédition levée par Alderic.");
            return;
        }
        if (fromRaid && !toRaid && !allowed.contains(p.getUniqueId())) raids.leftOnTheirOwn(p);
    }

    /** Commandes de déplacement refusées dans le monde des raids : on rentre par le héraut du camp. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player p = event.getPlayer();
        if (!raids.isRaidWorld(p.getWorld()) || p.hasPermission(CityPermissions.ADMIN_RAID)) return;
        String label = event.getMessage().length() > 1 ? event.getMessage().substring(1).split("\\s+")[0].toLowerCase() : "";
        if (label.contains(":")) label = label.substring(label.indexOf(':') + 1); // essentials:home → home
        if (!raids.config().blockedCommands.contains(label)) return;
        event.setCancelled(true);
        p.sendMessage("§c⚔ Pas pendant une expédition. §7Pour rentrer à Valmont, parlez au héraut du camp.");
    }

    /** Pas de point d'apparition personnel (lit, ancre) dans le monde des raids. */
    @EventHandler(ignoreCancelled = true)
    public void onSetSpawn(com.destroystokyo.paper.event.player.PlayerSetSpawnEvent event) {
        if (raids.isRaidWorld(event.getPlayer().getWorld())) event.setCancelled(true);
    }

    /** Aucun portail ne s'ouvre dans le monde des raids, ni n'en fait sortir. */
    @EventHandler(ignoreCancelled = true)
    public void onPortalCreate(PortalCreateEvent event) {
        if (raids.isRaidWorld(event.getWorld())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPortal(PlayerPortalEvent event) {
        if (raids.isRaidWorld(event.getFrom().getWorld())) event.setCancelled(true);
    }

    /** Clic sur le héraut du camp. */
    @EventHandler
    public void onHerald(NPCRightClickEvent event) {
        if (raids.isHerald(event.getNPC())) raids.heraldClicked(event.getClicker());
    }
}
