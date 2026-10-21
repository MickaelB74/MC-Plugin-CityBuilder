package com.citycore.raid;

import com.citycore.util.DatabaseManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.function.IntSupplier;

/**
 * Progression des raids (PLAN_RAIDS §4.1, §6.2, table raid_state) : victoires par niveau
 * (déblocage du niveau suivant, renommée), recharge par niveau pour tout le serveur, et historique
 * des expéditions (table raid_results).
 */
final class RaidProgress {

    private final JavaPlugin plugin;
    private final DatabaseManager db;
    /** Palier de la ville (chapitre). */
    private IntSupplier cityLevel = () -> 1;

    RaidProgress(JavaPlugin plugin, DatabaseManager db) {
        this.plugin = plugin;
        this.db     = db;
    }

    void setCityLevel(IntSupplier cityLevel) {
        this.cityLevel = cityLevel;
    }

    int cityLevel() {
        return cityLevel.getAsInt();
    }

    int victories(int level) {
        return queryInt("SELECT victoires FROM raid_state WHERE niveau = ?", level, 0);
    }

    /** Fin de la recharge du niveau (millisecondes), 0 s'il n'y en a pas. */
    long cooldownUntil(int level) {
        try (PreparedStatement ps = db.getConnection().prepareStatement("SELECT recharge_jusqu_a FROM raid_state WHERE niveau = ?")) {
            ps.setInt(1, level);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("⚔ [Raids] État des raids illisible : " + e.getMessage());
            return 0L;
        }
    }

    /**
     * Pourquoi ce niveau n'est pas ouvert (chapitre de la ville, pas de victoire au niveau
     * précédent), ou null. Déblocage : chapitre atteint et une victoire au niveau précédent.
     */
    String lockReason(RaidConfig.Level level) {
        if (cityLevel() < level.chapter()) return "il faut que Valmont atteigne le chapitre " + roman(level.chapter());
        if (level.number() > 1 && victories(level.number() - 1) == 0) {
            return "il faut d'abord une victoire au niveau " + roman(level.number() - 1);
        }
        return null;
    }

    /** Victoire ou défaite : recharge du niveau (moitié en cas de défaite), victoire comptée. */
    void recordEnd(RaidRun r, boolean victory, long durationMs, String bonuses) {
        long cooldown = r.level.cooldownMinutes() * 60_000L / (victory ? 1 : 2);
        int level = r.level.number();
        try (PreparedStatement ps = db.getConnection().prepareStatement(
                "INSERT INTO raid_state (niveau, victoires, recharge_jusqu_a) VALUES (?, ?, ?) "
                        + "ON CONFLICT(niveau) DO UPDATE SET victoires = victoires + excluded.victoires, recharge_jusqu_a = excluded.recharge_jusqu_a")) {
            ps.setInt(1, level);
            ps.setInt(2, victory ? 1 : 0);
            ps.setLong(3, System.currentTimeMillis() + cooldown);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("⚔ [Raids] État des raids non enregistré : " + e.getMessage());
        }
        try (PreparedStatement ps = db.getConnection().prepareStatement(
                "INSERT INTO raid_results (cible, niveau, resultat, joueurs, duree, defiance_finale, bonus, date) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
            ps.setString(1, r.target.key());
            ps.setInt(2, level);
            ps.setString(3, victory ? "victoire" : "defaite");
            ps.setInt(4, r.participants.size());
            ps.setLong(5, durationMs / 1000);
            ps.setInt(6, r.defiance());
            ps.setString(7, bonuses == null ? "" : bonuses);
            ps.setLong(8, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("⚔ [Raids] Résultat du raid non enregistré : " + e.getMessage());
        }
    }

    /** Victoires par cible (clé de raids.yml) : « prise ×3 » des écrans d'Alderic. */
    java.util.Map<String, Integer> takes() {
        java.util.Map<String, Integer> out = new java.util.HashMap<>();
        try (PreparedStatement ps = db.getConnection().prepareStatement(
                "SELECT cible, COUNT(*) FROM raid_results WHERE resultat = 'victoire' GROUP BY cible");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) out.put(rs.getString(1), rs.getInt(2));
        } catch (SQLException e) {
            plugin.getLogger().warning("⚔ [Raids] Historique des raids illisible : " + e.getMessage());
        }
        return out;
    }

    /** Administration : toutes les recharges levées. */
    void resetCooldowns() {
        execute("UPDATE raid_state SET recharge_jusqu_a = 0");
    }

    /** Administration : une victoire comptée à chaque niveau sous {@code level} (déblocage). */
    void unlock(int level) {
        for (int n = 1; n < level; n++) {
            try (PreparedStatement ps = db.getConnection().prepareStatement(
                    "INSERT INTO raid_state (niveau, victoires, recharge_jusqu_a) VALUES (?, 1, 0) "
                            + "ON CONFLICT(niveau) DO UPDATE SET victoires = MAX(victoires, 1)")) {
                ps.setInt(1, n);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().warning("⚔ [Raids] Déblocage non enregistré : " + e.getMessage());
            }
        }
    }

    private int queryInt(String sql, int level, int def) {
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setInt(1, level);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : def;
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("⚔ [Raids] État des raids illisible : " + e.getMessage());
            return def;
        }
    }

    private void execute(String sql) {
        try {
            db.execute(sql);
        } catch (SQLException e) {
            plugin.getLogger().warning("⚔ [Raids] État des raids non modifié : " + e.getMessage());
        }
    }

    static String roman(int n) {
        String[] r = {"I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
        return n >= 1 && n <= r.length ? r[n - 1] : String.valueOf(n);
    }
}
