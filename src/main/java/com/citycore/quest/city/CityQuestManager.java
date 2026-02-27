package com.citycore.quest.city;

import com.citycore.building.BuildingManager;
import com.citycore.city.CityManager;
import com.citycore.npc.NPCDataManager;
import com.citycore.npc.NPCState;
import com.citycore.util.DatabaseManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public class CityQuestManager {

    private final DatabaseManager db;
    private final CityManager     cityManager;
    private final BuildingManager buildingManager;
    private final NPCDataManager  npcDataManager;
    private final JavaPlugin      plugin;

    public CityQuestManager(DatabaseManager db, CityManager cityManager,
                            BuildingManager buildingManager,
                            NPCDataManager npcDataManager, JavaPlugin plugin) {
        this.db              = db;
        this.cityManager     = cityManager;
        this.buildingManager = buildingManager;
        this.npcDataManager  = npcDataManager;
        this.plugin          = plugin;
    }

    /* =========================
       NIVEAU VILLE
       ========================= */

    public int getCityLevel() {
        try {
            ResultSet rs = db.getConnection()
                    .createStatement()
                    .executeQuery("SELECT level FROM city_level WHERE id = 1");
            if (rs.next()) return rs.getInt("level");
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 1;
    }

    public void setCityLevel(int level) {
        try {
            PreparedStatement ps = db.getConnection().prepareStatement(
                    "UPDATE city_level SET level = ? WHERE id = 1");
            ps.setInt(1, level);
            ps.executeUpdate();
            ps.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /* =========================
       PROGRESSION QUÊTE
       ========================= */

    public int getProgress(String questId) {
        try {
            PreparedStatement ps = db.getConnection().prepareStatement(
                    "SELECT current_val FROM city_quest_progress WHERE quest_id = ?");
            ps.setString(1, questId);
            ResultSet rs = ps.executeQuery();
            int val = rs.next() ? rs.getInt("current_val") : 0;
            ps.close();
            return val;
        } catch (SQLException e) {
            e.printStackTrace();
            return 0;
        }
    }

    public boolean isCompleted(String questId) {
        try {
            PreparedStatement ps = db.getConnection().prepareStatement(
                    "SELECT completed FROM city_quest_progress WHERE quest_id = ?");
            ps.setString(1, questId);
            ResultSet rs = ps.executeQuery();
            boolean done = rs.next() && rs.getInt("completed") == 1;
            ps.close();
            return done;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public void setProgress(String questId, int value, boolean completed) {
        try {
            PreparedStatement ps = db.getConnection().prepareStatement("""
                INSERT INTO city_quest_progress (quest_id, current_val, completed)
                VALUES (?, ?, ?)
                ON CONFLICT(quest_id)
                DO UPDATE SET current_val = excluded.current_val,
                              completed   = excluded.completed
            """);
            ps.setString(1, questId);
            ps.setInt(2, value);
            ps.setInt(3, completed ? 1 : 0);
            ps.executeUpdate();
            ps.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /* =========================
       VALEUR ACTUELLE PAR TYPE
       ========================= */

    /**
     * Calcule la valeur actuelle d'une quête selon son type.
     */
    public int computeCurrentValue(CityQuest quest) {
        return switch (quest.type()) {
            case CLAIM_CHUNKS    -> cityManager.getClaimedChunkCount();
            case BUILD_BUILDINGS -> buildingManager.getAllBuildings().size();
            case DEPOSIT_COINS   -> getProgress(quest.id()); // cumulatif — géré manuellement
            case FIND_NPC        -> getProgress(quest.id()); // placeholder
        };
    }

    /**
     * Synchronise la progression de toutes les quêtes du palier actuel.
     */
    public void syncCurrentTier() {
        CityTier tier = CityTier.fromLevel(getCityLevel());
        for (CityQuest quest : tier.quests) {
            if (isCompleted(quest.id())) continue;
            if (quest.type() == CityQuestType.DEPOSIT_COINS) continue; // cumulatif
            if (quest.type() == CityQuestType.FIND_NPC) continue;      // manuel

            int current = computeCurrentValue(quest);
            boolean done = current >= quest.targetValue();
            setProgress(quest.id(), current, done);
        }
    }

    /**
     * Ajoute des coins déposés (pour DEPOSIT_COINS cumulatif).
     */
    public void onCoinsDeposited(int amount) {
        CityTier tier = CityTier.fromLevel(getCityLevel());
        for (CityQuest quest : tier.quests) {
            if (quest.type() != CityQuestType.DEPOSIT_COINS) continue;
            if (isCompleted(quest.id())) continue;

            int newVal = getProgress(quest.id()) + amount;
            boolean done = newVal >= quest.targetValue();
            setProgress(quest.id(), newVal, done);
        }
    }

    /**
     * Vérifie si toutes les quêtes du palier actuel sont complètes.
     */
    public boolean isTierComplete(CityTier tier) {
        for (CityQuest quest : tier.quests) {
            if (quest.type() == CityQuestType.FIND_NPC) continue; // skip placeholder
            if (!isCompleted(quest.id())) return false;
        }
        return true;
    }

    /**
     * Monte la ville d'un niveau.
     * @return true si succès, false si déjà au max
     */
    public boolean upgradeCityLevel() {
        CityTier current = CityTier.fromLevel(getCityLevel());
        if (current.isMaxTier()) return false;
        setCityLevel(current.next().level);
        return true;
    }

    /* =========================
       TASK TEMPS RÉEL
       ========================= */

    public void startSyncTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                syncCurrentTier();
            }
        }.runTaskTimerAsynchronously(plugin, 20L, 100L); // toutes les 5 secondes
    }
}