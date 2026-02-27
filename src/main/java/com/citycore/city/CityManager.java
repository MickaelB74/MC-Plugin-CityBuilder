package com.citycore.city;

import com.citycore.util.DatabaseManager;
import org.bukkit.Chunk;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class CityManager {

    private final DatabaseManager db;

    private static final int EXPAND_BASE_PRICE = 200;

    public CityManager(DatabaseManager db) {
        this.db = db;
    }

    /* =========================
       VILLE
       ========================= */

    public boolean isCityInitialized() {
        try {
            ResultSet rs = db.getConnection()
                    .createStatement()
                    .executeQuery("SELECT COUNT(*) FROM city");
            rs.next();
            return rs.getInt(1) > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public void initializeCity(String name) {
        try {
            PreparedStatement ps = db.getConnection().prepareStatement("""
                INSERT INTO city (id, name, level, coins, max_chunks, residents)
                VALUES (1, ?, 1, 0, 1, 0)
            """);
            ps.setString(1, name);
            ps.executeUpdate();
            ps.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /* =========================
       ÉCONOMIE VILLE
       ========================= */

    public int getCityCoins() {
        try {
            ResultSet rs = db.getConnection()
                    .createStatement()
                    .executeQuery("SELECT coins FROM city WHERE id = 1");
            if (rs.next()) return rs.getInt("coins");
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
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
        try {
            ResultSet rs = db.getConnection()
                    .createStatement()
                    .executeQuery("SELECT residents FROM city WHERE id = 1");
            if (rs.next()) return rs.getInt("residents");
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
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
        return EXPAND_BASE_PRICE * getMaxChunks();
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
        try {
            ResultSet rs = db.getConnection()
                    .createStatement()
                    .executeQuery("SELECT COUNT(*) FROM claimed_chunks");
            rs.next();
            return rs.getInt(1);
        } catch (SQLException e) {
            e.printStackTrace();
            return 0;
        }
    }

    public int getMaxChunks() {
        try {
            ResultSet rs = db.getConnection()
                    .createStatement()
                    .executeQuery("SELECT max_chunks FROM city WHERE id = 1");
            if (rs.next()) return rs.getInt("max_chunks");
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
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
        try {
            ResultSet rs = db.getConnection()
                    .createStatement()
                    .executeQuery("SELECT name, level, coins, max_chunks, residents FROM city WHERE id = 1");
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
        try {
            ResultSet rs = db.getConnection()
                    .createStatement()
                    .executeQuery("SELECT name FROM city WHERE id = 1");
            if (rs.next()) return rs.getString("name");
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return "Ville";
    }
}