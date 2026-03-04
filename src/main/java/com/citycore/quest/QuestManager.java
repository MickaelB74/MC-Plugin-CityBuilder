package com.citycore.quest;

import com.citycore.npc.CityNPC;
import com.citycore.quest.personal.PersonalQuestDefinition;
import com.citycore.quest.personal.PersonalQuestRegistry;
import com.citycore.util.DatabaseManager;
import org.bukkit.Material;
import org.bukkit.block.Biome;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

public class QuestManager {

    private final DatabaseManager db;
    private final JavaPlugin plugin;

    public QuestManager(DatabaseManager db, JavaPlugin plugin) {
        this.db = db;
        this.plugin = plugin;
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

    public QuestDefinition getActiveQuest(UUID playerUUID, CityNPC npc,
                                          boolean isSpecial) {
        try {
            PreparedStatement ps = db.getConnection().prepareStatement("""
            SELECT quest_data FROM quest_progress
            WHERE player_uuid = ? AND npc_tag = ? AND is_special = ?
            AND completed != 2
        """);
            ps.setString(1, playerUUID.toString());
            ps.setString(2, npc.tag);
            ps.setInt(3, isSpecial ? 1 : 0);
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) { ps.close(); return null; }
            QuestDefinition quest = deserializeQuest(rs.getString("quest_data"), isSpecial);
            ps.close();
            return quest;
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    public boolean hasActiveQuest(UUID playerUUID, CityNPC npc, boolean isSpecial) {
        try {
            PreparedStatement ps = db.getConnection().prepareStatement("""
            SELECT 1 FROM quest_progress
            WHERE player_uuid = ? AND npc_tag = ? AND is_special = ?
            AND completed != 2
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

    public Map<String, Integer> getProgress(UUID playerUUID, CityNPC npc, boolean isSpecial) {
        try {
            PreparedStatement ps = db.getConnection().prepareStatement("""
            SELECT progress FROM quest_progress
            WHERE player_uuid = ? AND npc_tag = ? AND is_special = ?
        """);
            // ✅ Supprimé "AND completed = 0" — même raison
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

    public boolean isReadyToValidate(UUID playerUUID, CityNPC npc, boolean isSpecial) {
        try {
            PreparedStatement ps = db.getConnection().prepareStatement("""
            SELECT completed FROM quest_progress
            WHERE player_uuid = ? AND npc_tag = ? AND is_special = ?
        """);
            ps.setString(1, playerUUID.toString());
            ps.setString(2, npc.tag);
            ps.setInt(3, isSpecial ? 1 : 0);
            ResultSet rs = ps.executeQuery();
            boolean ready = rs.next() && rs.getInt("completed") == 1;
            ps.close();
            return ready;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public void markReadyToValidate(UUID playerUUID, CityNPC npc, boolean isSpecial) {
        try {
            PreparedStatement ps = db.getConnection().prepareStatement("""
            UPDATE quest_progress SET completed = 1
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

    /**
     * Remet completed à 0 si le joueur perd des items après notification.
     */
    public void unmarkReadyToValidate(UUID playerUUID, CityNPC npc, boolean isSpecial) {
        try {
            PreparedStatement ps = db.getConnection().prepareStatement("""
            UPDATE quest_progress SET completed = 0
            WHERE player_uuid = ? AND npc_tag = ? AND is_special = ?
            AND completed = 1
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

    public void setProgress(UUID playerUUID, CityNPC npc, boolean isSpecial,
                            String objectiveId, int value) {
        Map<String, Integer> progress = getProgress(playerUUID, npc, isSpecial);
        if (progress.isEmpty()) return;

        progress.put(objectiveId, value);

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
    }

    public QuestDefinition getPendingQuest(UUID playerUUID, CityNPC npc,
                                           boolean isSpecial) {
        try {
            PreparedStatement ps = db.getConnection().prepareStatement("""
            SELECT quest_data FROM quest_progress
            WHERE player_uuid = ? AND npc_tag = ? AND is_special = ? AND completed = 2
        """);
            ps.setString(1, playerUUID.toString());
            ps.setString(2, npc.tag);
            ps.setInt(3, isSpecial ? 1 : 0);
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) { ps.close(); return null; }
            QuestDefinition quest = deserializeQuest(rs.getString("quest_data"), isSpecial);
            ps.close();
            return quest;
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    public void setPendingQuest(UUID playerUUID, CityNPC npc, QuestDefinition quest) {
        try {
            PreparedStatement ps = db.getConnection().prepareStatement("""
            INSERT OR REPLACE INTO quest_progress
            (player_uuid, npc_tag, is_special, quest_data, progress, completed)
            VALUES (?, ?, ?, ?, '', 2)
        """);
            ps.setString(1, playerUUID.toString());
            ps.setString(2, npc.tag);
            ps.setInt(3, quest.isSpecial() ? 1 : 0);
            ps.setString(4, serializeQuest(quest));
            ps.executeUpdate();
            ps.close();
        } catch (SQLException e) {
            e.printStackTrace();
            return;
        }
    }

    /**
     * Passe la quête pending en active (completed 2 → 0)
     */
    public void acceptPendingQuest(UUID playerUUID, CityNPC npc, boolean isSpecial,
                                   QuestDefinition quest) {
        try {
            PreparedStatement ps = db.getConnection().prepareStatement("""
            UPDATE quest_progress SET completed = 0, progress = ?
            WHERE player_uuid = ? AND npc_tag = ? AND is_special = ? AND completed = 2
        """);
            ps.setString(1, buildEmptyProgress(quest));
            ps.setString(2, playerUUID.toString());
            ps.setString(3, npc.tag);
            ps.setInt(4, isSpecial ? 1 : 0);
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

            } else if (obj.isEntityObjective()) {
                objs.append("E:").append(obj.entity().name())
                        .append(":").append(obj.amount());

            } else if (obj.isBiomeObjective()) {                // ← NOUVEAU
                objs.append("B:").append(obj.biome().name())
                        .append(":").append(obj.amount());

            } else if (obj.isPersonalObjective()) {             // ← NOUVEAU
                objs.append("P:").append(obj.personalId())
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
                String[] o = objRaw.split(":");
                int amount = Integer.parseInt(o[2]);

                switch (o[0]) {
                    case "M" -> objectives.add(
                            QuestObjective.ofMaterial(Material.valueOf(o[1]), amount));

                    case "E" -> objectives.add(
                            QuestObjective.ofEntity(EntityType.valueOf(o[1]), amount));

                    case "B" -> objectives.add(                 // ← NOUVEAU
                            QuestObjective.ofBiome(Biome.valueOf(o[1]), amount));

                    case "P" -> objectives.add(                 // ← NOUVEAU
                            QuestObjective.ofPersonal(o[1], amount));

                    default -> plugin.getLogger()
                            .warning("Objectif inconnu lors de la désérialisation : " + o[0]);
                }
            }
            return new QuestDefinition(id, desc, type, objectives,
                    new QuestReward(coins), isSpecial);

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /* =========================
       PROGRESSION EXPLORE_CHUNK
       Appelé depuis QuestListener.onPlayerMove()
       ========================= */

    /**
     * Tente d'incrémenter les objectifs EXPLORE_CHUNK pour un joueur
     * qui vient de changer de chunk.
     *
     * @param player   joueur
     * @param npc      NPC concerné
     * @param isSpecial quête principale ou spéciale
     * @param newBiome  biome du nouveau chunk
     */
    public void onChunkEntered(org.bukkit.entity.Player player, CityNPC npc,
                               boolean isSpecial, org.bukkit.block.Biome newBiome) {
        QuestDefinition active = getActiveQuest(player.getUniqueId(), npc, isSpecial);
        if (active == null || active.type() != QuestType.EXPLORE_CHUNK) return;

        for (QuestObjective obj : active.objectives()) {
            if (!obj.isBiomeObjective()) continue;
            if (obj.biome() != newBiome) continue;

            boolean allDone = incrementProgress(
                    player.getUniqueId(), npc, isSpecial, obj.id(), 1, active);

            if (allDone) {
                markReadyToValidate(player.getUniqueId(), npc, isSpecial);
                player.sendMessage("§a✅ Exploration terminée ! Revenez voir "
                        + npc.displayName + " §apour valider !");
            } else {
                Map<String, Integer> prog = getProgress(player.getUniqueId(), npc, isSpecial);
                int cur = prog.getOrDefault(obj.id(), 0);
                player.sendActionBar("§e🗺 Biome découvert §7(§f" + cur
                        + "§7/§f" + obj.amount() + "§7)");
            }
            break; // un seul objectif biome par quête explore
        }
    }

    /* =========================
       PROGRESSION PERSONAL_QUEST
       Appelé depuis QuestListener selon le TriggerType
       ========================= */

    /**
     * Vérifie et incrémente les objectifs PERSONAL_QUEST d'un joueur.
     *
     * @param player   joueur
     * @param npc      NPC concerné
     * @param isSpecial quête principale ou spéciale
     * @param trigger  type de déclencheur actuel
     * @param context  objet contextuel (entité tuée, item…) — peut être null
     */
    public void onPersonalTrigger(org.bukkit.entity.Player player, CityNPC npc,
                                  boolean isSpecial,
                                  PersonalQuestDefinition.TriggerType trigger,
                                  Object context) {
        QuestDefinition active = getActiveQuest(player.getUniqueId(), npc, isSpecial);
        if (active == null || active.type() != QuestType.PERSONAL_QUEST) return;

        for (QuestObjective obj : active.objectives()) {
            if (!obj.isPersonalObjective()) continue;

            PersonalQuestDefinition def = PersonalQuestRegistry.get(obj.personalId());
            if (def == null || def.trigger() != trigger) continue;
            if (!def.validator().validate(player, context)) continue;

            boolean allDone = incrementProgress(
                    player.getUniqueId(), npc, isSpecial, obj.id(), 1, active);

            if (allDone) {
                markReadyToValidate(player.getUniqueId(), npc, isSpecial);
                player.sendMessage("§a✅ " + def.displayName()
                        + " §acomplétée ! Revenez voir " + npc.displayName + " §apour valider !");
                player.playSound(player.getLocation(),
                        org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.2f);
            } else {
                Map<String, Integer> prog = getProgress(player.getUniqueId(), npc, isSpecial);
                int cur = prog.getOrDefault(obj.id(), 0);
                player.sendActionBar("§d✦ " + def.displayName()
                        + " §7(§f" + cur + "§7/§f" + obj.amount() + "§7)");
            }
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