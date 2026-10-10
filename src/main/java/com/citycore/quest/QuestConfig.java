package com.citycore.quest;

import com.citycore.economy.MarketService;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

/**
 * Quêtes procédurales d'un PNJ (section <clé>.quests de config.yml).
 *
 * <pre>
 * quests:
 *   quantity-multiplier: {1: 1.0, 2: 1.5, 3: 2.0, 4: 2.5, 5: 3.0}
 *   main:
 *     xp-reward-per-level: 25
 *     objectives-count: 2
 *     item-pool:
 *       - {material: OAK_LOG, base-amount: 64}
 *       - {material: BREAD, base-amount: 16, value: 0.95, min-level: 2}
 *   special:
 *     xp-reward-per-level: 60
 *     objectives-count: 2
 *     item-pool: [...]
 *     entity-pool:
 *       - {entity: ZOMBIE, base-amount: 8, value: 2}
 * </pre>
 *
 * Récompense = Σ (quantité × valeur unitaire) × prime (npc-quests.*-reward-premium).
 * La valeur unitaire vient du prix de rachat du PNJ acheteur, ou du champ "value".
 */
public class QuestConfig {

    private final JavaPlugin    plugin;
    private final String        configKey;
    private final MarketService market;

    private final List<QuestPoolEntry> mainItemPool      = new ArrayList<>();
    private final List<QuestPoolEntry> specialItemPool   = new ArrayList<>();
    private final List<QuestPoolEntry> specialEntityPool = new ArrayList<>();
    private final Map<Integer, Double> multipliers       = new LinkedHashMap<>();

    private int mainObjectivesCount;
    private int specialObjectivesCount;
    private int mainXpRewardPerLevel;
    private int specialXpRewardPerLevel;

    public int getMainXpRewardPerLevel()    { return mainXpRewardPerLevel; }
    public int getSpecialXpRewardPerLevel() { return specialXpRewardPerLevel; }

    public QuestConfig(JavaPlugin plugin, String configKey, MarketService market) {
        this.plugin    = plugin;
        this.configKey = configKey;
        this.market    = market;
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

        var multSection = config.getConfigurationSection(base + ".quantity-multiplier");
        if (multSection != null) {
            for (String key : multSection.getKeys(false)) {
                try {
                    multipliers.put(Integer.parseInt(key.trim()), multSection.getDouble(key));
                } catch (NumberFormatException e) {
                    plugin.getLogger().warning("[" + configKey + "] clé multiplier invalide : '" + key + "'");
                }
            }
        }

        mainObjectivesCount     = config.getInt(base + ".main.objectives-count", 2);
        mainXpRewardPerLevel    = config.getInt(base + ".main.xp-reward-per-level", 25);
        loadPool(config.getMapList(base + ".main.item-pool"), mainItemPool, false);

        specialObjectivesCount  = config.getInt(base + ".special.objectives-count", 2);
        specialXpRewardPerLevel = config.getInt(base + ".special.xp-reward-per-level", 60);
        loadPool(config.getMapList(base + ".special.item-pool"), specialItemPool, false);
        loadPool(config.getMapList(base + ".special.entity-pool"), specialEntityPool, true);
    }

    /** Le PNJ propose-t-il des quêtes ? */
    public boolean hasMainPool()    { return !mainItemPool.isEmpty(); }
    public boolean hasSpecialPool() { return !specialItemPool.isEmpty() || !specialEntityPool.isEmpty(); }

    /* =========================
       GÉNÉRATION
       ========================= */

    public QuestDefinition generateMain(int npcLevel) {
        List<QuestPoolEntry> pool = eligible(mainItemPool, npcLevel);
        List<QuestObjective> objectives = pick(pool, mainObjectivesCount, multiplier(npcLevel));
        return build("main", objectives, market.cfg().mainRewardPremium(), false);
    }

    public QuestDefinition generateSpecial(int npcLevel) {
        List<QuestPoolEntry> pool = new ArrayList<>(eligible(specialItemPool, npcLevel));
        pool.addAll(eligible(specialEntityPool, npcLevel));
        List<QuestObjective> objectives = pick(pool, specialObjectivesCount, multiplier(npcLevel));
        return build("special", objectives, market.cfg().specialRewardPremium(), true);
    }

    /** @return null si aucune entrée n'est disponible à ce niveau */
    private QuestDefinition build(String prefix, List<QuestObjective> objectives,
                                  double premium, boolean special) {
        if (objectives.isEmpty()) return null;
        double value = 0;
        for (QuestObjective obj : objectives) value += obj.amount() * unitValue(obj);
        int reward = Math.max(1, (int) Math.round(value * premium));

        boolean hasKill = objectives.stream().anyMatch(QuestObjective::isEntityObjective);
        QuestType type  = hasKill ? QuestType.KILL_ENTITIES : QuestType.COLLECT_ITEMS;
        return new QuestDefinition(
                prefix + "_" + configKey + "_" + System.currentTimeMillis(),
                buildDescription(objectives),
                type,
                objectives,
                new QuestReward(reward),
                special
        );
    }

    /* =========================
       VALEURS
       ========================= */

    /** Valeur unitaire d'un objectif (prix de rachat de référence ou "value" du pool). */
    private double unitValue(QuestObjective obj) {
        for (QuestPoolEntry e : allEntries()) {
            boolean same = obj.isMaterialObjective()
                    ? e.material() == obj.material()
                    : obj.isEntityObjective() && e.entity() == obj.entity();
            if (same) return valueOf(e);
        }
        return obj.isMaterialObjective() ? market.unitValue(obj.material()) : 0;
    }

    private double valueOf(QuestPoolEntry e) {
        if (e.value() > 0) return e.value();
        if (e.isEntity())  return 2.0; // prime par défaut
        return market.unitValue(e.material());
    }

    /**
     * Récompense maximale par unité livrée, par matériau (contrôle anti-arbitrage).
     */
    public Map<Material, Double> unitRewards() {
        Map<Material, Double> map = new EnumMap<>(Material.class);
        for (QuestPoolEntry e : mainItemPool) {
            map.merge(e.material(), valueOf(e) * market.cfg().mainRewardPremium(), Math::max);
        }
        for (QuestPoolEntry e : specialItemPool) {
            map.merge(e.material(), valueOf(e) * market.cfg().specialRewardPremium(), Math::max);
        }
        return map;
    }

    /** Entrées dont la valeur est introuvable (ni "value", ni prix de rachat). */
    public List<String> unpricedEntries() {
        List<String> list = new ArrayList<>();
        for (QuestPoolEntry e : allEntries()) {
            if (!e.isEntity() && valueOf(e) <= 0) list.add(configKey + " / " + e.material());
        }
        return list;
    }

    private List<QuestPoolEntry> allEntries() {
        List<QuestPoolEntry> all = new ArrayList<>(mainItemPool);
        all.addAll(specialItemPool);
        all.addAll(specialEntityPool);
        return all;
    }

    /* =========================
       HELPERS
       ========================= */

    private double multiplier(int npcLevel) {
        return multipliers.getOrDefault(npcLevel, 1.0);
    }

    private List<QuestPoolEntry> eligible(List<QuestPoolEntry> pool, int npcLevel) {
        return pool.stream().filter(e -> e.minLevel() <= npcLevel).toList();
    }

    private List<QuestObjective> pick(List<QuestPoolEntry> pool, int count, double mult) {
        List<QuestPoolEntry> shuffled = new ArrayList<>(pool);
        Collections.shuffle(shuffled);
        List<QuestObjective> result = new ArrayList<>();
        for (int i = 0; i < Math.min(count, shuffled.size()); i++) {
            QuestPoolEntry entry = shuffled.get(i);
            int amount = (int) Math.max(1, Math.round(entry.baseAmount() * mult));
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
                sb.append(obj.amount()).append("x ").append(com.citycore.integration.ItemIds.label(obj.material()));
            } else {
                sb.append("Tuer ").append(obj.amount()).append(" ").append(formatName(obj.entity().name()));
            }
        }
        return sb.toString();
    }

    private void loadPool(List<Map<?, ?>> raw, List<QuestPoolEntry> target, boolean entities) {
        for (Map<?, ?> map : raw) {
            try {
                int baseAmt  = Integer.parseInt(String.valueOf(map.get("base-amount")));
                double value = map.containsKey("value") ? Double.parseDouble(String.valueOf(map.get("value"))) : 0;
                int minLevel = map.containsKey("min-level") ? Integer.parseInt(String.valueOf(map.get("min-level"))) : 1;
                if (entities) {
                    EntityType entity = EntityType.valueOf(String.valueOf(map.get("entity")).toUpperCase(Locale.ROOT));
                    target.add(new QuestPoolEntry(null, entity, baseAmt, value, minLevel));
                } else {
                    String id = String.valueOf(map.get("material"));
                    Material mat = com.citycore.integration.ItemIds.resolve(id);
                    // Objet d'un mod absent : l'entrée est simplement écartée (habitant inactif)
                    if (mat == null && com.citycore.integration.ItemIds.isModId(id)) continue;
                    if (mat == null) throw new IllegalArgumentException("matériau inconnu " + id);
                    target.add(new QuestPoolEntry(mat, null, baseAmt, value, minLevel));
                }
            } catch (Exception e) {
                plugin.getLogger().warning("[" + configKey + "] entrée de quête invalide : " + e.getMessage());
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

    public record QuestPoolEntry(Material material, EntityType entity, int baseAmount,
                                 double value, int minLevel) {
        public boolean isEntity() { return entity != null; }
    }
}
