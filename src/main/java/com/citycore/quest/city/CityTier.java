package com.citycore.quest.city;

import com.citycore.npc.CityNPC;
import org.bukkit.Material;

import java.util.List;

/**
 * Paliers de progression de la ville.
 * Chaque palier correspond à un niveau de ville.
 * Pour passer au palier suivant, toutes les quêtes du palier doivent être complétées.
 */
public enum CityTier {

    TIER_1(1, "§7Village", List.of(
            CityQuest.standard(
                    "t1_claim_chunks",
                    "§7Étendre le territoire",
                    "Claimez §f3 chunks §7pour agrandir votre village.",
                    Material.GRASS_BLOCK,
                    CityQuestType.CLAIM_CHUNKS,
                    3
            ),
            CityQuest.standard(
                    "t1_build_building",
                    "§7Premier bâtiment",
                    "Construisez §f1 bâtiment §7dans la ville.",
                    Material.BRICKS,
                    CityQuestType.BUILD_BUILDINGS,
                    1
            ),
            CityQuest.standard(
                    "t1_deposit_coins",
                    "§7Fonds de départ",
                    "Déposez §f200 coins §7dans la caisse de la ville.",
                    Material.GOLD_NUGGET,
                    CityQuestType.DEPOSIT_COINS,
                    200
            ),
            CityQuest.findNpc(
                    "t2_find_npc",
                    "§aPremier habitant",
                    "Trouvez §e" + CityNPC.STONEMASON.displayName + " §7et ramenez-le en ville.",
                    Material.COMPASS,
                    CityNPC.STONEMASON,
                    300
            )
    )),

    TIER_2(2, "§aBourgade", List.of(
            CityQuest.standard(
                    "t2_claim_chunks",
                    "§aAgrandissement",
                    "Claimez §f6 chunks §7au total.",
                    Material.GRASS_BLOCK,
                    CityQuestType.CLAIM_CHUNKS,
                    6
            ),
            CityQuest.standard(
                    "t2_build_buildings",
                    "§aQuartier résidentiel",
                    "Construisez §f3 bâtiments §7dans la ville.",
                    Material.BRICKS,
                    CityQuestType.BUILD_BUILDINGS,
                    3
            ),
            CityQuest.standard(
                    "t2_deposit_coins",
                    "§aTrésor municipal",
                    "Déposez §f1000 coins §7dans la caisse.",
                    Material.GOLD_INGOT,
                    CityQuestType.DEPOSIT_COINS,
                    1000
            )
    )),

    TIER_3(3, "§6Cité", List.of(
            CityQuest.standard(
                    "t3_claim_chunks",
                    "§6Grande expansion",
                    "Claimez §f12 chunks §7au total.",
                    Material.GRASS_BLOCK,
                    CityQuestType.CLAIM_CHUNKS,
                    12
            ),
            CityQuest.standard(
                    "t3_build_buildings",
                    "§6Infrastructure",
                    "Construisez §f6 bâtiments.",
                    Material.BRICKS,
                    CityQuestType.BUILD_BUILDINGS,
                    6
            ),
            CityQuest.standard(
                    "t3_deposit_coins",
                    "§6Économie florissante",
                    "Déposez §f5000 coins §7dans la caisse.",
                    Material.GOLD_BLOCK,
                    CityQuestType.DEPOSIT_COINS,
                    5000
            )
    ));

    public final int            level;
    public final String         tierName;
    public final List<CityQuest> quests;

    CityTier(int level, String tierName, List<CityQuest> quests) {
        this.level    = level;
        this.tierName = tierName;
        this.quests   = quests;
    }

    /** Retourne le palier correspondant à un niveau de ville. */
    public static CityTier fromLevel(int level) {
        for (CityTier tier : values()) {
            if (tier.level == level) return tier;
        }
        return TIER_1;
    }

    /** Retourne le palier suivant, ou null si déjà au max. */
    public CityTier next() {
        CityTier[] vals = values();
        for (int i = 0; i < vals.length - 1; i++) {
            if (vals[i] == this) return vals[i + 1];
        }
        return null;
    }

    public boolean isMaxTier() {
        return next() == null;
    }
}