package com.citycore.city;

import com.citycore.util.DatabaseManager;
import org.bukkit.Chunk;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class CityManager {

    private final DatabaseManager db;
    private final JavaPlugin plugin;

    private static final int    DEFAULT_BASE_PRICE   = 50;
    private static final double DEFAULT_MULTIPLIER   = 1.03;
    private static final int    DEFAULT_MAX_PRICE    = 300;
    private static final int    DEFAULT_FREE_CHUNKS  = 10;

    /** Observateurs des parcelles (carte des mods…), prévenus après chaque claim, unclaim ou fondation. */
    private final List<Runnable> claimListeners = new ArrayList<>();

    public CityManager(DatabaseManager db, JavaPlugin plugin) {
        this.db     = db;
        this.plugin = plugin;
        ensureBonusColumn();
        ensureGiftColumn();
    }

    /** S'abonner aux changements de parcelles (appelé sur le fil qui a fait le changement). */
    public void addClaimListener(Runnable listener) {
        claimListeners.add(listener);
    }

    private void claimsChanged() {
        for (Runnable listener : claimListeners) {
            try {
                listener.run();
            } catch (RuntimeException e) {
                plugin.getLogger().warning("Suivi des parcelles : " + e);
            }
        }
    }

    /** Colonne mayor_gift : le cadeau de fondation d'Alderic n'est versé qu'une fois. */
    private void ensureGiftColumn() {
        try {
            db.execute("ALTER TABLE city ADD COLUMN mayor_gift INTEGER NOT NULL DEFAULT 0");
            // Base existante : une ville qui a déjà des emplacements a reçu le cadeau
            db.execute("UPDATE city SET mayor_gift = 1 WHERE max_chunks > 1");
        } catch (SQLException ignored) {
            // Colonne déjà présente
        }
    }

    public boolean isMayorGiftGiven() {
        // Pas de ville ou erreur : on considère le cadeau donné (jamais en double)
        return db.queryInt("SELECT mayor_gift FROM city WHERE id = 1", "mayor_gift", 1) == 1;
    }

    public void markMayorGiftGiven() {
        try {
            db.execute("UPDATE city SET mayor_gift = 1 WHERE id = 1");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Colonne bonus_chunks : emplacements offerts (création, première rencontre du maire,
     * récompenses de palier). Ils ne comptent pas dans le prix des agrandissements.
     */
    private void ensureBonusColumn() {
        try {
            db.execute("ALTER TABLE city ADD COLUMN bonus_chunks INTEGER NOT NULL DEFAULT 0");
            // Base existante : on considère les emplacements de départ comme offerts
            PreparedStatement ps = db.getConnection().prepareStatement(
                    "UPDATE city SET bonus_chunks = MIN(max_chunks, ?)");
            ps.setInt(1, 1 + getFreeChunks());
            ps.executeUpdate();
            ps.close();
        } catch (SQLException ignored) {
            // Colonne déjà présente
        }
    }

    /* =========================
       VILLE
       ========================= */

    public boolean isCityInitialized() {
        return db.queryInt("SELECT COUNT(*) AS n FROM city", "n", 0) > 0;
    }

    public void initializeCity(String name) {
        try {
            PreparedStatement ps = db.getConnection().prepareStatement("""
                INSERT INTO city (id, name, level, coins, max_chunks, residents, bonus_chunks)
                VALUES (1, ?, 1, 0, 1, 0, 1)
            """);
            ps.setString(1, name);
            ps.executeUpdate();
            ps.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        claimsChanged();
    }

    /* =========================
       ÉCONOMIE VILLE
       ========================= */

    public int getCityCoins() {
        return db.queryInt("SELECT coins FROM city WHERE id = 1", "coins", 0);
    }

    public int addCityCoins(int amount) {
        if (amount <= 0) return getCityCoins();
        try {
            PreparedStatement ps = db.getConnection().prepareStatement(
                    "UPDATE city SET coins = coins + ? WHERE id = 1");
            ps.setInt(1, amount);
            ps.executeUpdate();
            ps.close();
            return getCityCoins();
        } catch (SQLException e) {
            e.printStackTrace();
            return -1;
        }
    }

    public boolean removeCityCoins(int amount) {
        if (amount <= 0) return true;
        if (getCityCoins() < amount) return false;
        try {
            PreparedStatement ps = db.getConnection().prepareStatement(
                    "UPDATE city SET coins = coins - ? WHERE id = 1");
            ps.setInt(1, amount);
            ps.executeUpdate();
            ps.close();
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean canAfford(int amount) {
        return getCityCoins() >= amount;
    }

    /* =========================
       HABITANTS
       ========================= */

    public int getResidentCount() {
        return db.queryInt("SELECT residents FROM city WHERE id = 1", "residents", 0);
    }

    public void addResident() {
        try {
            PreparedStatement ps = db.getConnection().prepareStatement(
                    "UPDATE city SET residents = residents + 1 WHERE id = 1");
            ps.executeUpdate();
            ps.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void removeResident() {
        try {
            PreparedStatement ps = db.getConnection().prepareStatement(
                    "UPDATE city SET residents = MAX(0, residents - 1) WHERE id = 1");
            ps.executeUpdate();
            ps.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void setResidentCount(int count) {
        try {
            PreparedStatement ps = db.getConnection().prepareStatement(
                    "UPDATE city SET residents = ? WHERE id = 1");
            ps.setInt(1, Math.max(0, count));
            ps.executeUpdate();
            ps.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /* =========================
       EXPANSION
       ========================= */

    public int getNextExpandPrice() {
        var cfg          = plugin.getConfig();
        int  basePrice   = cfg.getInt("city-expand.base-price",  DEFAULT_BASE_PRICE);
        double mult      = cfg.getDouble("city-expand.multiplier", DEFAULT_MULTIPLIER);
        int  maxPrice    = cfg.getInt("city-expand.max-price",   DEFAULT_MAX_PRICE);

        // Nombre de slots déjà achetés (hors slots offerts : départ, maire, paliers)
        int paidSlots = Math.max(0, getMaxChunks() - getBonusChunks());

        // Prix = basePrice * multiplier^paidSlots, plafonné à maxPrice
        double price = basePrice * Math.pow(mult, paidSlots);
        return Math.min(maxPrice, (int) Math.round(price));
    }

    public int getFreeChunks() {
        return plugin.getConfig().getInt("city-expand.free-chunks", DEFAULT_FREE_CHUNKS);
    }

    public ExpandResult expandMaxChunks() {
        int price   = getNextExpandPrice();
        int balance = getCityCoins();

        if (balance < price) return new ExpandResult(false, price, balance, 0);

        try {
            db.getConnection().setAutoCommit(false);

            PreparedStatement debit = db.getConnection().prepareStatement(
                    "UPDATE city SET coins = coins - ? WHERE id = 1");
            debit.setInt(1, price);
            debit.executeUpdate();
            debit.close();

            PreparedStatement expand = db.getConnection().prepareStatement(
                    "UPDATE city SET max_chunks = max_chunks + 1 WHERE id = 1");
            expand.executeUpdate();
            expand.close();

            db.getConnection().commit();
            db.getConnection().setAutoCommit(true);

            return new ExpandResult(true, price, balance - price, getMaxChunks());

        } catch (SQLException e) {
            e.printStackTrace();
            try { db.getConnection().rollback(); db.getConnection().setAutoCommit(true); }
            catch (SQLException ignored) {}
            return new ExpandResult(false, price, balance, 0);
        }
    }

    public record ExpandResult(boolean success, int price, int newBalance, int newMaxChunks) {}

    /* =========================
       CHUNKS
       ========================= */

    public void claimChunk(Chunk chunk) {
        try {
            PreparedStatement ps = db.getConnection().prepareStatement("""
                INSERT OR IGNORE INTO claimed_chunks (world, x, z) VALUES (?, ?, ?)
            """);
            ps.setString(1, chunk.getWorld().getName());
            ps.setInt(2, chunk.getX());
            ps.setInt(3, chunk.getZ());
            ps.executeUpdate();
            ps.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        claimsChanged();
    }

    public void unclaimChunk(Chunk chunk) {
        try {
            PreparedStatement ps = db.getConnection().prepareStatement(
                    "DELETE FROM claimed_chunks WHERE world = ? AND x = ? AND z = ?");
            ps.setString(1, chunk.getWorld().getName());
            ps.setInt(2, chunk.getX());
            ps.setInt(3, chunk.getZ());
            ps.executeUpdate();
            ps.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        claimsChanged();
    }

    public boolean isChunkClaimed(Chunk chunk) {
        try {
            PreparedStatement ps = db.getConnection().prepareStatement(
                    "SELECT 1 FROM claimed_chunks WHERE world = ? AND x = ? AND z = ?");
            ps.setString(1, chunk.getWorld().getName());
            ps.setInt(2, chunk.getX());
            ps.setInt(3, chunk.getZ());
            ResultSet rs = ps.executeQuery();
            boolean claimed = rs.next();
            ps.close();
            return claimed;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean isAdjacentToClaimed(Chunk chunk) {
        String world = chunk.getWorld().getName();
        int x = chunk.getX();
        int z = chunk.getZ();
        int[][] neighbors = {{x+1,z},{x-1,z},{x,z+1},{x,z-1}};
        try {
            PreparedStatement ps = db.getConnection().prepareStatement(
                    "SELECT 1 FROM claimed_chunks WHERE world = ? AND x = ? AND z = ?");
            for (int[] n : neighbors) {
                ps.setString(1, world);
                ps.setInt(2, n[0]);
                ps.setInt(3, n[1]);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) { ps.close(); return true; }
            }
            ps.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public int getClaimedChunkCount() {
        return db.queryInt("SELECT COUNT(*) AS n FROM claimed_chunks", "n", 0);
    }

    public int getMaxChunks() {
        return db.queryInt("SELECT max_chunks FROM city WHERE id = 1", "max_chunks", 0);
    }

    public boolean canClaimChunk() {
        return getClaimedChunkCount() < getMaxChunks();
    }

    public List<long[]> getClaimedChunkCoords(String worldName) {
        List<long[]> result = new ArrayList<>();
        try {
            PreparedStatement ps = db.getConnection().prepareStatement(
                    "SELECT x, z FROM claimed_chunks WHERE world = ?");
            ps.setString(1, worldName);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) result.add(new long[]{rs.getLong("x"), rs.getLong("z")});
            ps.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return result;
    }

    /* =========================
       INFOS VILLE
       ========================= */

    public City getCity() {
        try (Statement st = db.getConnection().createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT name, level, coins, max_chunks, residents FROM city WHERE id = 1")) {
            if (!rs.next()) return null;
            return new City(
                    rs.getString("name"),
                    rs.getInt("level"),
                    rs.getInt("coins"),
                    getClaimedChunkCount(),
                    rs.getInt("max_chunks"),
                    rs.getInt("residents")
            );
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    public String getCityName() {
        return db.queryString("SELECT name FROM city WHERE id = 1", "name", "Ville");
    }

    public int getBonusChunks() {
        return db.queryInt("SELECT bonus_chunks FROM city WHERE id = 1", "bonus_chunks", 0);
    }

    /** Ajoute des emplacements offerts (n'augmentent pas le prix des agrandissements). */
    public void addBonusChunks(int amount) {
        try {
            PreparedStatement ps = db.getConnection().prepareStatement(
                    "UPDATE city SET max_chunks = max_chunks + ?, bonus_chunks = bonus_chunks + ? WHERE id = 1");
            ps.setInt(1, amount);
            ps.setInt(2, amount);
            ps.executeUpdate();
            ps.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void addMaxChunks(int amount) {
        try {
            PreparedStatement ps = db.getConnection().prepareStatement(
                    "UPDATE city SET max_chunks = max_chunks + ? WHERE id = 1");
            ps.setInt(1, amount);
            ps.executeUpdate();
            ps.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}