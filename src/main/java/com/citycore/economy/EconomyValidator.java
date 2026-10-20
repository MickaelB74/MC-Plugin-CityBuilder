package com.citycore.economy;

import com.citycore.npc.CityNPC;
import com.citycore.npc.villager.VillagerConfig;
import com.citycore.npc.villager.VillagerConfig.Requirement;
import com.citycore.npc.villager.VillagerConfig.SellPrice;
import com.citycore.npc.villager.VillagerConfig.ShopItem;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.*;

import java.util.*;

/**
 * Contrôle anti-arbitrage, exécuté au démarrage (et via /city eco check).
 *
 * Pour chaque matériau qu'un PNJ rachète, on calcule le coût minimal pour l'obtenir
 * en achetant chez les PNJ (boutique) puis en le craftant / cuisant / taillant
 * avec les vraies recettes du serveur. Si ce coût est inférieur au prix de rachat
 * (bonus d'employé compris, réduction d'employé comprise), un joueur pourrait
 * acheter → transformer → revendre avec profit : on le signale.
 *
 * Même contrôle pour les récompenses des quêtes de collecte.
 *
 * L'entrepôt n'entre pas dans le calcul : on s'y sert gratuitement, mais ce qu'on y prend (et tout
 * ce qu'on en fabrique, voir {@link TagPropagation}) reste marqué non revendable.
 */
public class EconomyValidator {

    private final MarketService market;

    public EconomyValidator(MarketService market) {
        this.market = market;
    }

    /**
     * @param questUnitRewards récompense maximale par unité livrée, par matériau
     * @return liste des alertes (vide = économie saine)
     */
    public List<String> run(Map<Material, Double> questUnitRewards) {
        EconomyConfig cfg = market.cfg();
        double margin   = 1 + cfg.validatorMargin() / 100.0;
        double bonus    = 1 + cfg.jobSellBonus() / 100.0;
        List<String> warnings = new ArrayList<>();

        // ── 1. Prix de rachat maximum (vente joueur → PNJ) ───────
        Map<Material, Double> buyMax = new EnumMap<>(Material.class);
        Map<Material, String> buyer  = new EnumMap<>(Material.class);
        for (Map.Entry<CityNPC, VillagerConfig> e : market.configs().entrySet()) {
            for (Map.Entry<Material, SellPrice> sp : e.getValue().getSellPrices().entrySet()) {
                double unit = sp.getValue().unitValue() * bonus;
                if (unit > buyMax.getOrDefault(sp.getKey(), 0.0)) {
                    buyMax.put(sp.getKey(), unit);
                    buyer.put(sp.getKey(), e.getValue().getConfigKey());
                }
            }
        }

        // ── 2. Coût d'achat minimum en boutique (PNJ → joueur), réduction d'employé incluse ──
        Map<Material, Double> cost = new EnumMap<>(Material.class);
        for (Map.Entry<CityNPC, VillagerConfig> e : market.configs().entrySet()) {
            VillagerConfig c = e.getValue();
            for (ShopItem item : c.getAllShopItems()) {
                checkShopItem(c, item, warnings);
                if (item.isSpecial()) continue; // enchanté / potion / livre : item différent
                double unit = (double) market.shopPrice(item, true) / item.quantity();
                cost.merge(item.material(), unit, Math::min);
            }
        }

        // ── 3. Propagation par les recettes du serveur ───────────
        List<RecipeEdge> edges = collectRecipes();
        for (int pass = 0; pass < 10; pass++) {
            boolean changed = false;
            for (RecipeEdge edge : edges) {
                double c = edge.unitCost(cost);
                if (c < cost.getOrDefault(edge.output(), Double.MAX_VALUE) - 1e-9) {
                    cost.put(edge.output(), c);
                    changed = true;
                }
            }
            if (!changed) break;
        }

        // ── 4. Contrôles ─────────────────────────────────────────
        for (Map.Entry<Material, Double> e : buyMax.entrySet()) {
            Double c = cost.get(e.getKey());
            if (c != null && c < e.getValue() * margin) {
                warnings.add(String.format(Locale.ROOT,
                        "Arbitrage : %s racheté jusqu'à %.3f/u par %s, mais obtenable pour %.3f/u",
                        e.getKey(), e.getValue(), buyer.get(e.getKey()), c));
            }
        }
        for (Map.Entry<Material, Double> e : questUnitRewards.entrySet()) {
            Double c = cost.get(e.getKey());
            if (c != null && c < e.getValue() * margin) {
                warnings.add(String.format(Locale.ROOT,
                        "Quête rentable à l'achat : %s récompensé %.3f/u, obtenable pour %.3f/u",
                        e.getKey(), e.getValue(), c));
            }
        }
        return warnings;
    }

    /* =========================
       COHÉRENCE DES ARTICLES
       ========================= */

    private void checkShopItem(VillagerConfig c, ShopItem item, List<String> warnings) {
        if (item.price() <= 0) {
            warnings.add("Article gratuit : " + c.getConfigKey() + " / " + item.material());
        }
        for (Requirement r : item.requires()) {
            if (!r.isOwnStock() && market.npcByKey(r.supplierKey()) == null) {
                warnings.add("Fournisseur inconnu '" + r.supplierKey() + "' pour "
                        + c.getConfigKey() + " / " + item.material());
                continue;
            }
            VillagerConfig supplier = r.isOwnStock() ? c : market.config(market.npcByKey(r.supplierKey()));
            boolean stocked = supplier != null
                    && r.materials().stream().anyMatch(supplier::isSellable);
            if (!stocked) {
                warnings.add("Ingrédient jamais en stock : " + r.token() + " chez "
                        + (r.isOwnStock() ? c.getConfigKey() : r.supplierKey())
                        + " (article " + c.getConfigKey() + " / " + item.material() + ")");
            }
        }
    }

    /* =========================
       RECETTES
       ========================= */

    /** Une recette : {@code yield} × output à partir d'un ingrédient par emplacement. */
    private record RecipeEdge(Material output, int yield, List<Set<Material>> inputs) {
        double unitCost(Map<Material, Double> cost) {
            double total = 0;
            for (Set<Material> choice : inputs) {
                double best = Double.MAX_VALUE;
                for (Material m : choice) {
                    Double c = cost.get(m);
                    if (c != null && c < best) best = c;
                }
                if (best == Double.MAX_VALUE) return Double.MAX_VALUE; // ingrédient non achetable
                total += best;
            }
            return total / Math.max(1, yield);
        }
    }

    private List<RecipeEdge> collectRecipes() {
        List<RecipeEdge> edges = new ArrayList<>();
        Iterator<Recipe> it = Bukkit.recipeIterator();
        while (it.hasNext()) {
            Recipe recipe;
            try {
                recipe = it.next();
            } catch (Exception e) {
                continue;
            }
            ItemStack result = recipe.getResult();
            if (result == null || result.getType().isAir()) continue;

            List<RecipeChoice> choices = new ArrayList<>();
            if (recipe instanceof ShapedRecipe shaped) {
                Map<Character, RecipeChoice> map = shaped.getChoiceMap();
                for (String row : shaped.getShape()) {
                    for (char ch : row.toCharArray()) {
                        RecipeChoice rc = map.get(ch);
                        if (rc != null) choices.add(rc);
                    }
                }
            } else if (recipe instanceof ShapelessRecipe shapeless) {
                choices.addAll(shapeless.getChoiceList());
            } else if (recipe instanceof CookingRecipe<?> cooking) {
                choices.add(cooking.getInputChoice());
            } else if (recipe instanceof StonecuttingRecipe cutting) {
                choices.add(cutting.getInputChoice());
            } else if (recipe instanceof SmithingTransformRecipe smithing) {
                choices.add(smithing.getTemplate());
                choices.add(smithing.getBase());
                choices.add(smithing.getAddition());
            } else {
                continue; // recettes spéciales (feux d'artifice, bannières…)
            }

            List<Set<Material>> inputs = new ArrayList<>();
            boolean valid = true;
            for (RecipeChoice rc : choices) {
                Set<Material> mats = materialsOf(rc);
                if (mats.isEmpty()) { valid = false; break; }
                inputs.add(mats);
            }
            if (valid && !inputs.isEmpty()) {
                edges.add(new RecipeEdge(result.getType(), result.getAmount(), inputs));
            }
        }
        return edges;
    }

    private Set<Material> materialsOf(RecipeChoice rc) {
        Set<Material> set = EnumSet.noneOf(Material.class);
        if (rc instanceof RecipeChoice.MaterialChoice mc) {
            set.addAll(mc.getChoices());
        } else if (rc instanceof RecipeChoice.ExactChoice ec) {
            ec.getChoices().forEach(i -> set.add(i.getType()));
        }
        return set;
    }
}
