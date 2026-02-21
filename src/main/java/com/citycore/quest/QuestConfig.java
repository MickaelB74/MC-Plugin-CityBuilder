package com.citycore.quest;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class QuestConfig {

    private final JavaPlugin plugin;
    private final String     configKey;

    private final List<QuestPoolEntry> mainItemPool      = new ArrayList<>();
    private final List<QuestPoolEntry> specialItemPool   = new ArrayList<>();
    private final List<QuestPoolEntry> specialEntityPool = new ArrayList<>();
    private final Map<Integer, Double> multipliers       = new LinkedHashMap<>();

    private int mainRewardPerLevel;
    private int specialRewardPerLevel;
    private int mainObjectivesCount;
    private int specialObjectivesCount;

    public QuestConfig(JavaPlugin plugin, String configKey) {
        this.plugin    = plugin;
        this.configKey = configKey;
        reload();
    }

    public void reload() {
        mainItemPool.clear();
        specialItemPool.clear();
        specialEntityPool.clear();
        multipliers.clear();

        plugin.saveDefaultConfig();
        var config = plugin.getConfig();
        String base = configKey + ".quests";

        // Multipliers
        var multSection = config.getConfigurationSection(base + ".quantity-multiplier");
        if (multSection != null) {
            for (String key : multSection.getKeys(false)) {
                multipliers.put(Integer.parseInt(key), multSection.getDouble(key));
            }
        }

        // Main
        mainRewardPerLevel  = config.getInt(base + ".main.reward-per-level", 100);
        mainObjectivesCount = config.getInt(base + ".main.objectives-count", 2);
        loadItemPool(config.getMapList(base + ".main.item-pool"), mainItemPool);

        // Special
        specialRewardPerLevel  = config.getInt(base + ".special.reward-per-level", 300);
        specialObjectivesCount = config.getInt(base + ".special.objectives-count", 3);
        loadItemPool(config.getMapList(base + ".special.item-pool"), specialItemPool);
        loadEntityPool(config.getMapList(base + ".special.entity-pool"), specialEntityPool);

        plugin.getLogger().info("✅ Quêtes " + configKey + " — "
                + mainItemPool.size() + " items main, "
                + (specialItemPool.size() + specialEntityPool.size())
                + " entrées special.");
    }

    /* =========================
       GÉNÉRATION
       ========================= */

    public QuestDefinition generateMain(int npcLevel) {
        double mult = multipliers.getOrDefault(npcLevel, 1.0);
        List<QuestObjective> objectives = pickRandom(mainItemPool,
                mainObjectivesCount, mult);
        int reward = mainRewardPerLevel * npcLevel;
        return new QuestDefinition(
                "main_" + configKey + "_" + System.currentTimeMillis(),
                buildDescription(objectives),
                QuestType.COLLECT_ITEMS,
                objectives,
                new QuestReward(reward),
                false
        );
    }

    public QuestDefinition generateSpecial(int npcLevel) {
        double mult = multipliers.getOrDefault(npcLevel, 1.0);

        List<QuestPoolEntry> allSpecial = new ArrayList<>();
        allSpecial.addAll(specialItemPool);
        allSpecial.addAll(specialEntityPool);
        Collections.shuffle(allSpecial);

        int count = Math.min(specialObjectivesCount, allSpecial.size());
        List<QuestObjective> objectives = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            QuestPoolEntry entry = allSpecial.get(i);
            int amount = (int) Math.max(1, entry.baseAmount() * mult);
            objectives.add(entry.isEntity()
                    ? QuestObjective.ofEntity(entry.entity(), amount)
                    : QuestObjective.ofMaterial(entry.material(), amount));
        }

        boolean hasKill = objectives.stream().anyMatch(QuestObjective::isEntityObjective);
        QuestType type  = hasKill ? QuestType.KILL_ENTITIES : QuestType.COLLECT_ITEMS;
        int reward      = specialRewardPerLevel * npcLevel;

        return new QuestDefinition(
                "special_" + configKey + "_" + System.currentTimeMillis(),
                buildDescription(objectives),
                type,
                objectives,
                new QuestReward(reward),
                true
        );
    }

    /* =========================
       HELPERS
       ========================= */

    private List<QuestObjective> pickRandom(List<QuestPoolEntry> pool,
                                            int count, double mult) {
        List<QuestPoolEntry> shuffled = new ArrayList<>(pool);
        Collections.shuffle(shuffled);
        List<QuestObjective> result = new ArrayList<>();
        for (int i = 0; i < Math.min(count, shuffled.size()); i++) {
            QuestPoolEntry entry = shuffled.get(i);
            int amount = (int) Math.max(1, entry.baseAmount() * mult);
            result.add(entry.isEntity()
                    ? QuestObjective.ofEntity(entry.entity(), amount)
                    : QuestObjective.ofMaterial(entry.material(), amount));
        }
        return result;
    }

    private String buildDescription(List<QuestObjective> objectives) {
        StringBuilder sb = new StringBuilder();
        for (QuestObjective obj : objectives) {
            if (sb.length() > 0) sb.append(", ");
            if (obj.isMaterialObjective()) {
                sb.append(obj.amount()).append("x ")
                        .append(formatName(obj.material().name()));
            } else {
                sb.append("Tuer ").append(obj.amount()).append(" ")
                        .append(formatName(obj.entity().name()));
            }
        }
        return sb.toString();
    }

    private void loadItemPool(List<Map<?, ?>> raw, List<QuestPoolEntry> target) {
        for (Map<?, ?> map : raw) {
            try {
                Material mat = Material.valueOf(map.get("material").toString());
                int baseAmt  = Integer.parseInt(map.get("base-amount").toString());
                target.add(new QuestPoolEntry(mat, null, baseAmt));
            } catch (Exception e) {
                plugin.getLogger().warning("Item pool invalide : " + e.getMessage());
            }
        }
    }

    private void loadEntityPool(List<Map<?, ?>> raw, List<QuestPoolEntry> target) {
        for (Map<?, ?> map : raw) {
            try {
                EntityType entity = EntityType.valueOf(map.get("entity").toString());
                int baseAmt       = Integer.parseInt(map.get("base-amount").toString());
                target.add(new QuestPoolEntry(null, entity, baseAmt));
            } catch (Exception e) {
                plugin.getLogger().warning("Entity pool invalide : " + e.getMessage());
            }
        }
    }

    private String formatName(String name) {
        StringBuilder sb = new StringBuilder();
        for (String word : name.split("_")) {
            sb.append(Character.toUpperCase(word.charAt(0)))
                    .append(word.substring(1).toLowerCase()).append(" ");
        }
        return sb.toString().trim();
    }

    public record QuestPoolEntry(Material material, EntityType entity, int baseAmount) {
        public boolean isEntity() { return entity != null; }
    }
}