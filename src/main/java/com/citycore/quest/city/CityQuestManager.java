package com.citycore.quest.city;

import com.citycore.building.BuildingManager;
import com.citycore.city.CityManager;
import com.citycore.economy.ItemTags;
import com.citycore.economy.MarketService;
import com.citycore.npc.CityNPC;
import com.citycore.npc.NPCDataManager;
import com.citycore.npc.NPCState;
import com.citycore.npc.villager.VillagerConfig;
import com.citycore.npc.villager.VillagerConfig.SellPrice;
import com.citycore.util.DatabaseManager;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

public class CityQuestManager {

    /** Créatures comptées même sans tueur identifié (explosion de lit, cristaux…). */
    private static final Set<EntityType> BOSSES = EnumSet.of(
            EntityType.ENDER_DRAGON, EntityType.WITHER, EntityType.WARDEN, EntityType.ELDER_GUARDIAN);

    private final DatabaseManager db;
    private final CityManager     cityManager;
    private final BuildingManager buildingManager;
    private final NPCDataManager  npcDataManager;
    private final MarketService   market;
    private final JavaPlugin      plugin;

    public CityQuestManager(DatabaseManager db, CityManager cityManager,
                            BuildingManager buildingManager,
                            NPCDataManager npcDataManager, MarketService market,
                            JavaPlugin plugin) {
        this.db              = db;
        this.cityManager     = cityManager;
        this.buildingManager = buildingManager;
        this.npcDataManager  = npcDataManager;
        this.market          = market;
        this.plugin          = plugin;
    }

    /* =========================
       NIVEAU VILLE
       ========================= */

    public int getCityLevel() {
        return db.queryInt("SELECT level FROM city_level WHERE id = 1", "level", 1);
    }

    /** Découpage actuel de la trame (nombre de chapitres). */
    public static final int TIER_SCHEME = 10;

    /** Barème des parcelles : 1 = premier barème, 2 = grande ville (21/10/2026). */
    public static final int CHUNK_SCHEME = 2;
    /** Emplacements offerts par charte dans le premier barème (chapitres I à X). */
    private static final int[] OLD_REWARD_CHUNKS = {6, 6, 8, 8, 10, 12, 14, 16, 20, 0};
    /** Emplacements offerts par Alderic à la fondation dans le premier barème. */
    private static final int OLD_FREE_CHUNKS = 6;

    /**
     * Passage au barème « grande ville » : une ville déjà fondée reçoit les emplacements que le
     * nouveau barème lui aurait offerts en plus (cadeau d'Alderic et chartes déjà signées), pour
     * ne pas racheter ce qu'une ville neuve reçoit gratuitement. Une seule fois ; sans ville, le
     * barème est simplement noté. Les récompenses surchargées dans config.yml (city-tiers) ne
     * donnent rien de plus.
     */
    public void migrateChunkScheme() {
        try {
            db.execute("ALTER TABLE city_level ADD COLUMN chunk_scheme INTEGER NOT NULL DEFAULT 1");
        } catch (SQLException ignored) {
            // colonne déjà présente
        }
        int scheme = db.queryInt("SELECT chunk_scheme FROM city_level WHERE id = 1", "chunk_scheme", CHUNK_SCHEME);
        if (scheme >= CHUNK_SCHEME) return;
        int granted = 0;
        if (cityManager.isCityInitialized()) {
            if (cityManager.isMayorGiftGiven()) granted += Math.max(0, cityManager.getFreeChunks() - OLD_FREE_CHUNKS);
            int level = getCityLevel();
            for (CityTier tier : CityTier.values()) {
                if (tier.level >= level) break; // chartes déjà signées seulement
                int before = plugin.getConfig().getInt("city-tiers." + tier.level + ".reward-chunks",
                        OLD_REWARD_CHUNKS[tier.level - 1]);
                granted += Math.max(0, rewardChunks(tier) - before);
            }
            if (granted > 0) cityManager.addBonusChunks(granted);
        }
        try (PreparedStatement ps = db.getConnection().prepareStatement(
                "UPDATE city_level SET chunk_scheme = ? WHERE id = 1")) {
            ps.setInt(1, CHUNK_SCHEME);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        if (granted > 0) {
            plugin.getLogger().info("Parcelles : nouveau barème (grande ville), +" + granted
                    + " emplacements offerts à la ville pour les chartes déjà signées.");
        }
    }

    /**
     * Passage de l'ancienne trame (7 chapitres) à la nouvelle (10) : le niveau enregistré est
     * converti vers le chapitre équivalent. Les quêtes gardent leurs identifiants, donc leur
     * progression. Sans effet si la conversion est déjà faite.
     *
     * Ancien → nouveau : 1→1, 2→2, 3→3, 4→5 (bourg achevé : Fosse et Chaudron compris),
     * 5→6, 6→8 (foi et savoir achevés), 7→10 (dragon vaincu).
     */
    public void migrateTierScheme() {
        int scheme = db.queryInt("SELECT scheme FROM city_level WHERE id = 1", "scheme", TIER_SCHEME);
        if (scheme >= TIER_SCHEME) return;
        int old = getCityLevel();
        int mapped = switch (old) {
            case 1, 2, 3 -> old;
            case 4 -> 5;
            case 5 -> 6;
            case 6 -> 8;
            default -> old >= 7 ? 10 : 1;
        };
        if (mapped != old) setCityLevel(mapped);
        try (PreparedStatement ps = db.getConnection().prepareStatement(
                "UPDATE city_level SET scheme = ? WHERE id = 1")) {
            ps.setInt(1, TIER_SCHEME);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        if (mapped != old) {
            plugin.getLogger().info("Trame en " + TIER_SCHEME + " chapitres : niveau de la ville converti "
                    + old + " → " + mapped + " (« " + CityTier.fromLevel(mapped).chapter + " »).");
        }
    }

    public void setCityLevel(int level) {
        try {
            PreparedStatement ps = db.getConnection().prepareStatement(
                    "UPDATE city_level SET level = ? WHERE id = 1");
            ps.setInt(1, level);
            ps.executeUpdate();
            ps.close();
            // Garde la table city synchronisée (affichée par /city info)
            PreparedStatement ps2 = db.getConnection().prepareStatement(
                    "UPDATE city SET level = ? WHERE id = 1");
            ps2.setInt(1, level);
            ps2.executeUpdate();
            ps2.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /* =========================
       RÉGLAGES (surcharges config.yml)
       ========================= */

    /** Objectif effectif d'une quête (city-quests.targets.<id> ou valeur par défaut). */
    public int target(CityQuest quest) {
        if (quest.type() == CityQuestType.FIND_NPC) return 3;
        return plugin.getConfig().getInt("city-quests.targets." + quest.id(), quest.targetValue());
    }

    /** Description avec l'objectif effectif à la place de {n}. */
    public String description(CityQuest quest) {
        return quest.description().replace("{n}", String.valueOf(target(quest)));
    }

    public int upgradeCost(CityTier tier) {
        return plugin.getConfig().getInt("city-tiers." + tier.level + ".upgrade-cost", tier.upgradeCost);
    }

    public int rewardChunks(CityTier tier) {
        return plugin.getConfig().getInt("city-tiers." + tier.level + ".reward-chunks", tier.rewardChunks);
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

    /**
     * État de toutes les quêtes de la cité en une seule requête : identifiant →
     * {avancement, terminée (1) ou non (0)}. Sert aux lectures groupées (onglet de progrès).
     */
    public Map<String, int[]> progressSnapshot() {
        Map<String, int[]> snapshot = new HashMap<>();
        try (PreparedStatement ps = db.getConnection().prepareStatement(
                "SELECT quest_id, current_val, completed FROM city_quest_progress")) {
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                snapshot.put(rs.getString("quest_id"), new int[]{rs.getInt("current_val"), rs.getInt("completed")});
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return snapshot;
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
       QUÊTES AFFICHÉES
       ========================= */

    /**
     * Quêtes du palier actuel + quêtes bonus non terminées des paliers précédents
     * (un personnage secondaire reste recrutable après le passage de palier).
     */
    public List<CityQuest> displayedQuests() {
        int level = getCityLevel();
        List<CityQuest> list = new ArrayList<>(CityTier.fromLevel(level).quests);
        for (CityTier tier : CityTier.values()) {
            if (tier.level >= level) continue;
            for (CityQuest q : tier.quests) {
                if (q.optional() && !isCompleted(q.id())) list.add(q);
            }
        }
        return list;
    }

    /* =========================
       VALEUR ACTUELLE PAR TYPE
       ========================= */

    public int computeCurrentValue(CityQuest quest) {
        return switch (quest.type()) {
            case CLAIM_CHUNKS    -> cityManager.getClaimedChunkCount();
            case BUILD_BUILDINGS -> buildingManager.getAllBuildings().size();
            case DEPOSIT_COINS, DELIVER_ITEMS, KILL_MOBS -> getProgress(quest.id()); // cumulatifs
            case FIND_NPC        -> recruitStage(quest);
            case NPC_LEVEL       -> npcDataManager.getState(quest.targetNpc()).wasInstalled()
                    ? npcDataManager.getLevel(quest.targetNpc()) : 0;
            case STOCK_LEVEL     -> npcDataManager.getInventoryAmount(quest.targetNpc(), quest.material());
        };
    }

    /** 0 = pas commencée, 1 = PNJ apparu, 2 = arrivé en ville, 3 = installé. */
    private int recruitStage(CityQuest quest) {
        NPCState state = npcDataManager.getState(quest.targetNpc());
        if (state.wasInstalled()) return 3; // sans logis aujourd'hui : l'installation reste acquise
        if (state == NPCState.ARRIVED)  return 2;
        return Math.min(1, getProgress(quest.id()));
    }

    /** Le PNJ d'une quête de recrutement vient d'apparaître dans le monde. */
    public void markRecruitStarted(CityQuest quest) {
        if (!isCompleted(quest.id()) && getProgress(quest.id()) < 1) {
            setProgress(quest.id(), 1, false);
        }
    }

    /**
     * Synchronise la progression des quêtes calculées (chunks, bâtiments, rangs, stocks,
     * recrutements). Une quête terminée le reste.
     */
    public void syncCurrentTier() {
        for (CityQuest quest : displayedQuests()) {
            if (isCompleted(quest.id())) continue;
            switch (quest.type()) {
                case DEPOSIT_COINS, DELIVER_ITEMS, KILL_MOBS -> { continue; } // cumulatifs
                default -> {}
            }
            int current = computeCurrentValue(quest);
            setProgress(quest.id(), current, current >= target(quest));
        }
    }

    /** Ajoute des coins déposés (pour DEPOSIT_COINS cumulatif). */
    public void onCoinsDeposited(int amount) {
        for (CityQuest quest : displayedQuests()) {
            if (quest.type() != CityQuestType.DEPOSIT_COINS || isCompleted(quest.id())) continue;
            int newVal = getProgress(quest.id()) + amount;
            setProgress(quest.id(), newVal, newVal >= target(quest));
        }
    }

    /* =========================
       CHASSE (KILL_MOBS)
       ========================= */

    /**
     * Une créature est morte.
     * @param killedByPlayer tuée par un joueur
     * @param hostile        créature hostile (compte pour les quêtes "n'importe quel monstre")
     * @return quêtes ayant progressé (pour le retour au joueur)
     */
    public List<CityQuest> onEntityKilled(EntityType type, boolean killedByPlayer, boolean hostile) {
        List<CityQuest> progressed = new ArrayList<>();
        if (!killedByPlayer && !BOSSES.contains(type)) return progressed;
        for (CityQuest quest : displayedQuests()) {
            if (quest.type() != CityQuestType.KILL_MOBS || isCompleted(quest.id())) continue;
            boolean matches = quest.entity() == null ? hostile : quest.entity() == type;
            if (!matches) continue;
            int newVal = getProgress(quest.id()) + 1;
            setProgress(quest.id(), newVal, newVal >= target(quest));
            progressed.add(quest);
        }
        return progressed;
    }

    /* =========================
       LIVRAISONS (DELIVER_ITEMS)
       ========================= */

    /** Livre depuis l'inventaire du joueur (items achetés acceptés). @return quantité livrée */
    public int deliver(Player player, CityQuest quest) {
        if (isCompleted(quest.id())) return 0;
        int remaining = target(quest) - getProgress(quest.id());
        if (remaining <= 0) return 0;
        int delivered = ItemTags.removeAny(player, quest.materials(), remaining);
        if (delivered > 0) {
            int newVal = getProgress(quest.id()) + delivered;
            setProgress(quest.id(), newVal, newVal >= target(quest));
        }
        return delivered;
    }

    /**
     * Réquisition : le chantier puise dans les entrepôts des artisans (la réserve de la ville),
     * gratuitement, comme un joueur qui s'y sert ; les plus gros stocks d'abord.
     *
     * @param execute false = simple estimation
     */
    public Requisition requisition(CityQuest quest, boolean execute) {
        int remaining = target(quest) - getProgress(quest.id());
        if (remaining <= 0 || isCompleted(quest.id())) return new Requisition(0, 0);

        record Offer(CityNPC npc, Material mat, int stock) {}
        List<Offer> offers = new ArrayList<>();
        for (Map.Entry<CityNPC, VillagerConfig> e : market.configs().entrySet()) {
            for (Material mat : quest.materials()) {
                if (e.getValue().getSellPrice(mat) == null) continue;
                int stock = npcDataManager.getInventoryAmount(e.getKey(), mat);
                if (stock > 0) offers.add(new Offer(e.getKey(), mat, stock));
            }
        }
        offers.sort(Comparator.comparingInt(Offer::stock).reversed());

        int available = offers.stream().mapToInt(Offer::stock).sum();
        int taken = 0;
        for (Offer o : offers) {
            if (taken >= remaining) break;
            int take = Math.min(o.stock(), remaining - taken);
            if (execute) npcDataManager.removeFromInventory(o.npc(), o.mat(), take);
            taken += take;
        }

        if (execute && taken > 0) {
            int newVal = getProgress(quest.id()) + taken;
            setProgress(quest.id(), newVal, newVal >= target(quest));
        }
        return new Requisition(taken, Math.min(available, remaining));
    }

    /**
     * @param taken     quantité réquisitionnée (ou réquisitionnable si estimation)
     * @param available quantité disponible dans les stocks (plafonnée au reste à livrer)
     */
    public record Requisition(int taken, int available) {}

    /* =========================
       AMÉLIORATION DE LA VILLE
       ========================= */

    /** Toutes les quêtes non bonus du palier sont-elles terminées ? */
    public boolean isTierComplete(CityTier tier) {
        for (CityQuest quest : tier.quests) {
            if (quest.optional()) continue;
            if (!isCompleted(quest.id())) return false;
        }
        return true;
    }

    public enum UpgradeResult { SUCCESS, MAX_LEVEL, INCOMPLETE, NO_FUNDS }

    /**
     * Passe au palier suivant : paie la charte depuis la caisse et offre des chunks.
     */
    public UpgradeResult upgradeCityLevel() {
        CityTier current = CityTier.fromLevel(getCityLevel());
        if (current.isMaxTier())        return UpgradeResult.MAX_LEVEL;
        if (!isTierComplete(current))   return UpgradeResult.INCOMPLETE;
        int cost = upgradeCost(current);
        if (!cityManager.removeCityCoins(cost)) return UpgradeResult.NO_FUNDS;

        setCityLevel(current.next().level);
        int chunks = rewardChunks(current);
        if (chunks > 0) cityManager.addBonusChunks(chunks);
        return UpgradeResult.SUCCESS;
    }

    /* =========================
       TASK TEMPS RÉEL
       ========================= */

    /**
     * Synchronisation sur le fil principal : la connexion SQLite est partagée (transactions
     * manuelles, écritures concurrentes) ; ce sont quelques requêtes légères.
     */
    public void startSyncTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                syncCurrentTier();
            }
        }.runTaskTimer(plugin, 20L, 100L); // toutes les 5 secondes
    }
}
