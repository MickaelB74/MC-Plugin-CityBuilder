package com.citycore.quest;

import com.citycore.economy.MarketService;
import com.citycore.npc.CityNPC;
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
 *       - {entity: "mowziesmobs:umvuthana,mowziesmobs:umvuthana_raptor", nom: "Guerriers Umvuthana", base-amount: 4, value: 12}
 * </pre>
 *
 * Objets et créatures : vanilla ou « mod:id ». Un objectif peut accepter toute une famille :
 * tag (« #minecraft:wool »), motif (« handcrafted:*_chair »), liste séparée par des virgules ;
 * « nom » donne alors son libellé (« Laine (toutes couleurs) »). Une entrée dont rien n'existe
 * sur le serveur (mod absent) est écartée ; « avec-mod » / « sans-mod » la réservent à un mod
 * présent ou absent (contenu facultatif).
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

    /** Entrées de toutes les quêtes, par habitant puis par objet / créature représentant (libellés, familles). */
    private static final Map<String, QuestPoolEntry> ENTRIES = new java.util.concurrent.ConcurrentHashMap<>();

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
        ENTRIES.keySet().removeIf(k -> k.startsWith(configKey + "|"));

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
        for (QuestPoolEntry e : allEntries()) ENTRIES.putIfAbsent(entryKey(configKey, e), e);
    }

    /* =========================
       FAMILLES ET LIBELLÉS (lus par le suivi, les menus et la validation)
       ========================= */

    private static String entryKey(String configKey, QuestPoolEntry e) {
        return configKey + "|" + (e.isEntity() ? "E:" + e.entity().name() : "M:" + e.material().name());
    }

    private static QuestPoolEntry entryOf(CityNPC npc, QuestObjective obj) {
        if (npc == null || obj == null) return null;
        String key = npc.skinId + "|" + (obj.isEntityObjective() ? "E:" + obj.entity().name()
                : obj.isMaterialObjective() ? "M:" + obj.material().name() : "?");
        return ENTRIES.get(key);
    }

    /** Objets acceptés pour un objectif (toute la famille si l'entrée en est une). */
    public static Set<Material> acceptedMaterials(CityNPC npc, QuestObjective obj) {
        QuestPoolEntry e = entryOf(npc, obj);
        if (e != null && e.materials() != null && !e.materials().isEmpty()) return e.materials();
        return obj.material() == null ? Set.of() : Set.of(obj.material());
    }

    /** Créatures acceptées pour un objectif de chasse. */
    public static Set<EntityType> acceptedEntities(CityNPC npc, QuestObjective obj) {
        QuestPoolEntry e = entryOf(npc, obj);
        if (e != null && e.entities() != null && !e.entities().isEmpty()) return e.entities();
        return obj.entity() == null ? Set.of() : Set.of(obj.entity());
    }

    /** Libellé d'un objectif (« Laine (toutes couleurs) », « Cultiste », « Oak Log »). */
    public static String label(CityNPC npc, QuestObjective obj) {
        QuestPoolEntry e = entryOf(npc, obj);
        if (e != null && e.label() != null) return e.label();
        if (obj.isMaterialObjective()) return com.citycore.integration.ItemIds.label(obj.material());
        if (obj.isEntityObjective()) return com.citycore.integration.ItemIds.entityLabel(obj.entity());
        return "?";
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
        Map<Material, Double> map = new HashMap<>(); // matériaux de mods : pas d'EnumMap
        for (QuestPoolEntry e : mainItemPool) {
            for (Material m : e.members()) map.merge(m, valueOf(e) * market.cfg().mainRewardPremium(), Math::max);
        }
        for (QuestPoolEntry e : specialItemPool) {
            for (Material m : e.members()) map.merge(m, valueOf(e) * market.cfg().specialRewardPremium(), Math::max);
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
            QuestPoolEntry e = find(obj);
            String label = e != null && e.label() != null ? e.label()
                    : obj.isMaterialObjective() ? com.citycore.integration.ItemIds.label(obj.material())
                    : com.citycore.integration.ItemIds.entityLabel(obj.entity());
            if (obj.isMaterialObjective()) {
                sb.append(obj.amount()).append("x ").append(label);
            } else {
                sb.append("Tuer ").append(obj.amount()).append(" ").append(label);
            }
        }
        return sb.toString();
    }

    private void loadPool(List<Map<?, ?>> raw, List<QuestPoolEntry> target, boolean entities) {
        for (Map<?, ?> map : raw) {
            try {
                // Contenu facultatif : objectif proposé seulement avec (ou sans) un mod
                if (!com.citycore.integration.ModIntegrations.allows(text(map.get("avec-mod")), text(map.get("sans-mod")))) continue;
                int baseAmt  = Integer.parseInt(String.valueOf(map.get("base-amount")));
                double value = map.containsKey("value") ? Double.parseDouble(String.valueOf(map.get("value"))) : 0;
                int minLevel = map.containsKey("min-level") ? Integer.parseInt(String.valueOf(map.get("min-level"))) : 1;
                String label = text(map.get("nom"));
                if (entities) {
                    String ids = String.valueOf(map.get("entity"));
                    Set<EntityType> types = new LinkedHashSet<>();
                    for (String id : ids.split(",")) {
                        EntityType type = com.citycore.integration.ItemIds.resolveEntity(id);
                        if (type != null) types.add(type);
                        else if (!skippable(id)) warnEntry("créature inconnue " + id.trim());
                    }
                    if (types.isEmpty()) continue; // créature d'un mod absent : entrée écartée
                    target.add(new QuestPoolEntry(null, types.iterator().next(), baseAmt, value, minLevel,
                            null, Set.copyOf(types), label));
                } else {
                    String id = String.valueOf(map.get("material"));
                    Set<Material> mats = new LinkedHashSet<>();
                    for (Material m : com.citycore.economy.MaterialGroups.resolveAny(id)) if (m.isItem()) mats.add(m);
                    // Objet d'un mod absent : l'entrée est simplement écartée (habitant inactif)
                    if (mats.isEmpty()) {
                        if (!skippable(id)) warnEntry("objet inconnu " + id);
                        continue;
                    }
                    boolean family = id.startsWith("#") || id.contains("*") || id.contains(",");
                    target.add(new QuestPoolEntry(mats.iterator().next(), null, baseAmt, value, minLevel,
                            family ? Collections.unmodifiableSet(mats) : null, null, label));
                }
            } catch (Exception e) {
                warnEntry(e.getMessage());
            }
        }
    }

    /**
     * Identifiant d'un mod absent : l'entrée s'écarte sans alerte. Mod présent ou jeu de base :
     * l'identifiant est faux (ou le mod a changé) et mérite un avertissement.
     */
    private static boolean skippable(String id) {
        String ns = com.citycore.integration.ItemIds.namespace(id.trim().replace("#", "").replace("-", ""));
        return !ns.equals("minecraft") && !com.citycore.integration.ModIntegrations.present(ns);
    }

    private void warnEntry(String message) {
        plugin.getLogger().warning("[" + configKey + "] entrée de quête invalide : " + message + " (ignorée)");
    }

    private static String text(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    /** Entrée d'origine d'un objectif généré par ce PNJ. */
    private QuestPoolEntry find(QuestObjective obj) {
        for (QuestPoolEntry e : allEntries()) {
            if (obj.isMaterialObjective() && !e.isEntity() && e.material() == obj.material()) return e;
            if (obj.isEntityObjective() && e.isEntity() && e.entity() == obj.entity()) return e;
        }
        return null;
    }

    private String formatName(String name) {
        StringBuilder sb = new StringBuilder();
        for (String word : name.split("_")) {
            sb.append(Character.toUpperCase(word.charAt(0)))
                    .append(word.substring(1).toLowerCase()).append(" ");
        }
        return sb.toString().trim();
    }

    /**
     * @param material  objet représentant (icône, identifiant de l'objectif), null pour une créature
     * @param entity    créature représentante, null pour un objet
     * @param materials famille d'objets acceptés (null = le seul représentant)
     * @param entities  créatures acceptées (null = la seule représentante)
     * @param label     libellé choisi dans config.yml (« nom »), null = nom de l'objet ou de la créature
     */
    public record QuestPoolEntry(Material material, EntityType entity, int baseAmount,
                                 double value, int minLevel, Set<Material> materials,
                                 Set<EntityType> entities, String label) {
        public boolean isEntity() { return entity != null; }

        /** Objets acceptés (le représentant seul hors famille). */
        public Set<Material> members() {
            if (materials != null) return materials;
            return material == null ? Set.of() : Set.of(material);
        }
    }
}
