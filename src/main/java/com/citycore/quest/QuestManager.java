package com.citycore.quest;

import com.citycore.npc.CityNPC;
import com.citycore.util.DatabaseManager;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

public class QuestManager {

    private final DatabaseManager db;

    public QuestManager(DatabaseManager db) {
        this.db = db;
    }

    /* =========================
       DÉMARRAGE
       ========================= */

    public void startQuest(UUID playerUUID, CityNPC npc, QuestDefinition quest) {
        try {
            PreparedStatement ps = db.getConnection().prepareStatement("""
                INSERT OR REPLACE INTO quest_progress
                (player_uuid, npc_tag, is_special, quest_data, progress, completed)
                VALUES (?, ?, ?, ?, ?, 0)
            """);
            ps.setString(1, playerUUID.toString());
            ps.setString(2, npc.tag);
            ps.setInt(3, quest.isSpecial() ? 1 : 0);
            ps.setString(4, serializeQuest(quest));
            ps.setString(5, buildEmptyProgress(quest));
            ps.executeUpdate();
            ps.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /* =========================
       LECTURE
       ========================= */

    public boolean hasActiveQuest(UUID playerUUID, CityNPC npc, boolean isSpecial) {
        try {
            PreparedStatement ps = db.getConnection().prepareStatement("""
                SELECT 1 FROM quest_progress
                WHERE player_uuid = ? AND npc_tag = ?
                AND is_special = ? AND completed = 0
            """);
            ps.setString(1, playerUUID.toString());
            ps.setString(2, npc.tag);
            ps.setInt(3, isSpecial ? 1 : 0);
            ResultSet rs = ps.executeQuery();
            boolean has = rs.next();
            ps.close();
            return has;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public QuestDefinition getActiveQuest(UUID playerUUID, CityNPC npc,
                                          boolean isSpecial) {
        try {
            PreparedStatement ps = db.getConnection().prepareStatement("""
                SELECT quest_data FROM quest_progress
                WHERE player_uuid = ? AND npc_tag = ?
                AND is_special = ? AND completed = 0
            """);
            ps.setString(1, playerUUID.toString());
            ps.setString(2, npc.tag);
            ps.setInt(3, isSpecial ? 1 : 0);
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) { ps.close(); return null; }
            QuestDefinition quest = deserializeQuest(
                    rs.getString("quest_data"), isSpecial);
            ps.close();
            return quest;
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    public Map<String, Integer> getProgress(UUID playerUUID, CityNPC npc,
                                            boolean isSpecial) {
        try {
            PreparedStatement ps = db.getConnection().prepareStatement("""
                SELECT progress FROM quest_progress
                WHERE player_uuid = ? AND npc_tag = ?
                AND is_special = ? AND completed = 0
            """);
            ps.setString(1, playerUUID.toString());
            ps.setString(2, npc.tag);
            ps.setInt(3, isSpecial ? 1 : 0);
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) { ps.close(); return new HashMap<>(); }
            Map<String, Integer> progress = parseProgress(rs.getString("progress"));
            ps.close();
            return progress;
        } catch (SQLException e) {
            e.printStackTrace();
            return new HashMap<>();
        }
    }

    /* =========================
       PROGRESSION
       ========================= */

    /**
     * Incrémente un objectif.
     * @return true si tous les objectifs sont maintenant remplis
     */
    public boolean incrementProgress(UUID playerUUID, CityNPC npc,
                                     boolean isSpecial, String objectiveId,
                                     int amount, QuestDefinition quest) {
        Map<String, Integer> progress = getProgress(playerUUID, npc, isSpecial);
        if (progress.isEmpty()) return false;

        progress.merge(objectiveId, amount, Integer::sum);

        // Plafonne à l'objectif max
        quest.objectives().stream()
                .filter(o -> o.id().equals(objectiveId))
                .findFirst()
                .ifPresent(o -> progress.put(objectiveId,
                        Math.min(progress.get(objectiveId), o.amount())));

        try {
            PreparedStatement ps = db.getConnection().prepareStatement("""
                UPDATE quest_progress SET progress = ?
                WHERE player_uuid = ? AND npc_tag = ? AND is_special = ?
            """);
            ps.setString(1, serializeProgress(progress));
            ps.setString(2, playerUUID.toString());
            ps.setString(3, npc.tag);
            ps.setInt(4, isSpecial ? 1 : 0);
            ps.executeUpdate();
            ps.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return isAllCompleted(progress, quest);
    }

    public boolean isAllCompleted(Map<String, Integer> progress,
                                  QuestDefinition quest) {
        for (QuestObjective obj : quest.objectives()) {
            if (progress.getOrDefault(obj.id(), 0) < obj.amount()) return false;
        }
        return true;
    }

    /* =========================
       VALIDATION / RESET
       ========================= */

    public void validateAndReset(UUID playerUUID, CityNPC npc, boolean isSpecial) {
        try {
            PreparedStatement ps = db.getConnection().prepareStatement("""
                DELETE FROM quest_progress
                WHERE player_uuid = ? AND npc_tag = ? AND is_special = ?
            """);
            ps.setString(1, playerUUID.toString());
            ps.setString(2, npc.tag);
            ps.setInt(3, isSpecial ? 1 : 0);
            ps.executeUpdate();
            ps.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /* =========================
       SÉRIALISATION
       Format : "id|desc|type|M:MAT:AMT,E:ENT:AMT|coins"
       ========================= */

    private String serializeQuest(QuestDefinition quest) {
        StringBuilder objs = new StringBuilder();
        for (QuestObjective obj : quest.objectives()) {
            if (objs.length() > 0) objs.append(",");
            if (obj.isMaterialObjective()) {
                objs.append("M:").append(obj.material().name())
                        .append(":").append(obj.amount());
            } else {
                objs.append("E:").append(obj.entity().name())
                        .append(":").append(obj.amount());
            }
        }
        return quest.id() + "|"
                + quest.description() + "|"
                + quest.type().name() + "|"
                + objs + "|"
                + quest.reward().coins();
    }

    private QuestDefinition deserializeQuest(String raw, boolean isSpecial) {
        try {
            String[] parts = raw.split("\\|");
            String id      = parts[0];
            String desc    = parts[1];
            QuestType type = QuestType.valueOf(parts[2]);
            int coins      = Integer.parseInt(parts[4]);

            List<QuestObjective> objectives = new ArrayList<>();
            for (String objRaw : parts[3].split(",")) {
                String[] o  = objRaw.split(":");
                int amount  = Integer.parseInt(o[2]);
                if (o[0].equals("M")) {
                    objectives.add(QuestObjective.ofMaterial(
                            Material.valueOf(o[1]), amount));
                } else {
                    objectives.add(QuestObjective.ofEntity(
                            EntityType.valueOf(o[1]), amount));
                }
            }
            return new QuestDefinition(id, desc, type, objectives,
                    new QuestReward(coins), isSpecial);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private String buildEmptyProgress(QuestDefinition quest) {
        StringBuilder sb = new StringBuilder();
        for (QuestObjective obj : quest.objectives()) {
            if (sb.length() > 0) sb.append(",");
            sb.append(obj.id()).append(":0");
        }
        return sb.toString();
    }

    private Map<String, Integer> parseProgress(String raw) {
        Map<String, Integer> map = new LinkedHashMap<>();
        if (raw == null || raw.isEmpty()) return map;
        for (String entry : raw.split(",")) {
            String[] parts = entry.split(":");
            if (parts.length == 2) {
                map.put(parts[0], Integer.parseInt(parts[1]));
            }
        }
        return map;
    }

    private String serializeProgress(Map<String, Integer> progress) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> e : progress.entrySet()) {
            if (sb.length() > 0) sb.append(",");
            sb.append(e.getKey()).append(":").append(e.getValue());
        }
        return sb.toString();
    }
}