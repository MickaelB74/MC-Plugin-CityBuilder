package com.citycore.economy;

import org.bukkit.configuration.file.FileConfiguration;

/**
 * Réglages globaux de l'économie des PNJ (sections npc-economy et npc-quests de config.yml).
 *
 * @param resaleMultiplier         prix de revente du stock = prix de rachat × multiplicateur
 * @param fullPriceUntil           % de remplissage de l'entrepôt sous lequel le PNJ paie plein tarif
 * @param minPricePercent          % du prix plein payé quand l'entrepôt est presque plein
 * @param consumptionPercentPerHour % de la capacité consommé par heure (joueurs connectés), 0 = off
 * @param jobSellBonus             bonus (%) sur les ventes de l'employé à son PNJ
 * @param jobShopDiscount          réduction (%) sur les achats de l'employé chez son PNJ
 * @param validatorMargin          marge minimale (%) exigée par le contrôle anti-arbitrage
 * @param mainCooldownMinutes      délai entre deux quêtes principales d'un même PNJ
 * @param specialCooldownMinutes   délai entre deux quêtes spéciales d'un même PNJ
 * @param mainRewardPremium        récompense = valeur des items demandés × prime
 * @param specialRewardPremium     idem pour les quêtes spéciales
 */
public record EconomyConfig(
        double resaleMultiplier,
        int    fullPriceUntil,
        int    minPricePercent,
        double consumptionPercentPerHour,
        int    jobSellBonus,
        int    jobShopDiscount,
        int    validatorMargin,
        int    mainCooldownMinutes,
        int    specialCooldownMinutes,
        double mainRewardPremium,
        double specialRewardPremium
) {
    public static EconomyConfig load(FileConfiguration cfg) {
        return new EconomyConfig(
                Math.max(1.0, cfg.getDouble("npc-economy.resale-multiplier", 2.0)),
                clamp(cfg.getInt("npc-economy.full-price-until", 40), 0, 99),
                clamp(cfg.getInt("npc-economy.min-price-percent", 35), 1, 100),
                Math.max(0, cfg.getDouble("npc-economy.consumption-percent-per-hour", 8)),
                clamp(cfg.getInt("npc-economy.job-sell-bonus", 10), 0, 100),
                clamp(cfg.getInt("npc-economy.job-shop-discount", 15), 0, 90),
                Math.max(0, cfg.getInt("npc-economy.validator-margin", 10)),
                Math.max(0, cfg.getInt("npc-quests.main-cooldown-minutes", 20)),
                Math.max(0, cfg.getInt("npc-quests.special-cooldown-minutes", 60)),
                Math.max(0, cfg.getDouble("npc-quests.main-reward-premium", 1.25)),
                Math.max(0, cfg.getDouble("npc-quests.special-reward-premium", 1.5))
        );
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}
