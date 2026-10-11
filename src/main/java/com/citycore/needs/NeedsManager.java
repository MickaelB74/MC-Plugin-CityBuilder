package com.citycore.needs;

import com.citycore.building.Building;
import com.citycore.building.BuildingInspector;
import com.citycore.building.Inspection;
import com.citycore.inhabitant.InhabitantManager;
import com.citycore.inhabitant.InhabitantManager.Inhabitant;
import com.citycore.needs.NeedsConfig.Need;
import com.citycore.npc.CityNPC;
import com.citycore.npc.NPCDataManager;
import com.citycore.npc.NPCState;
import com.citycore.quest.city.CityTier;
import com.citycore.util.DatabaseManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Besoins et satisfaction des habitants (phase 6 du plan « Valmont vivante »).
 *
 * Quatre besoins, chacun de 0 à 100 % :
 *  - nourriture : à chaque aube, chaque habitant mange des rations prises dans l'entrepôt
 *    d'Hélène (cultures) et de Rosalie (viandes) ;
 *  - sécurité : gardes par habitant, et issue des derniers sièges ;
 *  - santé : à chaque aube, des remèdes pris dans l'entrepôt de Frère Anselme ;
 *  - confort : niveau des maisons où vivent les habitants.
 * Un besoin ne compte qu'une fois son artisan installé (le confort compte dès qu'il y a des
 * habitants). La satisfaction est leur moyenne pondérée ; c'est un bonus : elle accélère les
 * arrivées et augmente l'impôt quand elle est haute, les ralentit un peu quand elle est basse,
 * sans jamais faire partir personne (le but n'est pas la gestion).
 * Nourriture et santé sont lissées d'une aube à l'autre (moitié hier, moitié aujourd'hui).
 */
public final class NeedsManager {

    /** État d'un besoin : compté ou non (artisan absent), valeur, détail, conseils. */
    public record Status(Need need, boolean counted, int value, String detail, List<String> help) {}

    private final JavaPlugin        plugin;
    private final DatabaseManager   db;
    private final InhabitantManager inhabitants;
    private final NPCDataManager    npcData;
    private final BuildingInspector inspector;
    private final NeedsConfig       config;

    private int food = -1, care = -1;               // valeurs lissées (‑1 = pas encore de repas)
    private String foodLast = "", careLast = "";   // « 48/64 rations » du dernier repas
    private List<Status> cache = List.of();
    private int satisfaction = -1;
    private long cachedAt;

    public NeedsManager(JavaPlugin plugin, DatabaseManager db, InhabitantManager inhabitants,
                        NPCDataManager npcData, BuildingInspector inspector) {
        this.plugin      = plugin;
        this.db          = db;
        this.inhabitants = inhabitants;
        this.npcData     = npcData;
        this.inspector   = inspector;
        this.config      = new NeedsConfig(plugin);
    }

    /** Valeurs enregistrées, branchement sur l'aube des habitants (repas, soins, impôt, arrivées). */
    public void start() {
        food     = Integer.parseInt(state("besoins.nourriture", "-1"));
        care     = Integer.parseInt(state("besoins.sante", "-1"));
        foodLast = state("besoins.nourriture.dernier", "");
        careLast = state("besoins.sante.dernier", "");
        inhabitants.setDawnListener(this::onDawn);
        inhabitants.setTaxMultiplier(() -> satisfaction() < 0 ? 1.0 : config.taxFactor(satisfaction()));
        inhabitants.setArrivalFactor(() -> satisfaction() < 0 ? 1.0 : config.arrivalFactor(satisfaction()));
    }

    public NeedsConfig config() {
        return config;
    }

    /** Consommation abstraite (% par heure) d'un artisan qui nourrit ou soigne les habitants, sinon null. */
    public Double abstractConsumption(CityNPC npc) {
        if (config.foods().containsKey(npc)) return config.foodAbstractConsumption();
        if (config.remedies().containsKey(npc)) return config.careAbstractConsumption();
        return null;
    }

    /* =========================
       L'AUBE : REPAS, SOINS, DÉPARTS
       ========================= */

    /** Appelé à chaque aube, juste avant l'impôt : repas, soins, bilan. */
    public void onDawn() {
        List<Inhabitant> people = people();
        if (people.isEmpty()) return;
        int n = people.size();
        if (foodCounted()) {
            double[] got = consume(config.foods(), config.rationsPerInhabitant() * n);
            food = smooth(food, got);
            foodLast = fmt(got[0]) + "/" + fmt(got[1]) + " rations";
        }
        if (careCounted()) {
            double[] got = consume(config.remedies(), config.carePerInhabitant() * n);
            care = smooth(care, got);
            careLast = fmt(got[0]) + "/" + fmt(got[1]) + " soins";
        }
        saveState("besoins.nourriture", String.valueOf(food));
        saveState("besoins.sante", String.valueOf(care));
        saveState("besoins.nourriture.dernier", foodLast);
        saveState("besoins.sante.dernier", careLast);
        cachedAt = 0; // recalcul immédiat
        int sat = satisfaction();
        StringBuilder sb = new StringBuilder("§6🌅 Aube à Valmont : §fsatisfaction " + sat + " %§7 (");
        List<String> parts = new ArrayList<>();
        for (Status s : statuses()) parts.add(s.need().label.toLowerCase() + " " + (s.counted() ? s.value() + " %" : "—"));
        sb.append(String.join(", ", parts)).append(")");
        if (foodCounted() && !foodLast.isEmpty()) sb.append(" §8· repas : ").append(foodLast);
        Bukkit.broadcastMessage(sb.toString());
    }

    /** Valeur lissée : moitié la précédente, moitié celle du jour (la première fois : celle du jour). */
    private static int smooth(int old, double[] got) {
        int today = got[1] <= 0 ? 100 : (int) Math.round(100 * Math.min(1, got[0] / got[1]));
        return old < 0 ? today : (int) Math.round(0.5 * old + 0.5 * today);
    }

    /**
     * Prend dans les entrepôts des artisans installés de quoi couvrir needed (en rations ou soins),
     * proportionnellement à ce que contient chaque entrepôt. @return {obtenu, demandé}
     */
    private double[] consume(Map<CityNPC, Map<Material, Double>> table, double needed) {
        record Stock(CityNPC npc, Material mat, int amount, double value) {}
        List<Stock> stocks = new ArrayList<>();
        double total = 0;
        for (Map.Entry<CityNPC, Map<Material, Double>> e : table.entrySet()) {
            if (npcData.getState(e.getKey()) != NPCState.ASSIGNED) continue;
            for (Map.Entry<Material, Double> item : e.getValue().entrySet()) {
                int amount = npcData.getInventoryAmount(e.getKey(), item.getKey());
                if (amount <= 0 || item.getValue() <= 0) continue;
                stocks.add(new Stock(e.getKey(), item.getKey(), amount, item.getValue()));
                total += amount * item.getValue();
            }
        }
        if (needed <= 0) return new double[]{0, 0};
        double share = total <= 0 ? 0 : Math.min(1, needed / total);
        double got = 0;
        for (Stock s : stocks) {
            int take = (int) Math.min(s.amount(), Math.ceil(s.amount() * share));
            if (take <= 0) continue;
            npcData.removeUpTo(s.npc(), s.mat(), take);
            got += take * s.value();
        }
        return new double[]{Math.min(got, needed), needed};
    }

    /* =========================
       BESOINS ET SATISFACTION
       ========================= */

    /** Habitants pris en compte (vivants, gardes et recrues compris ; pas les gardes de test). */
    private List<Inhabitant> people() {
        return inhabitants.living().stream().filter(i -> !i.test).toList();
    }

    private boolean foodCounted() {
        return config.foods().keySet().stream().anyMatch(this::installed);
    }

    private boolean careCounted() {
        return config.remedies().keySet().stream().anyMatch(this::installed);
    }

    private boolean installed(CityNPC npc) {
        return npcData.getState(npc) == NPCState.ASSIGNED;
    }

    /** Les quatre besoins (relus au plus toutes les 5 s). */
    public List<Status> statuses() {
        long now = System.currentTimeMillis();
        if (now - cachedAt < 5000 && !cache.isEmpty()) return cache;
        cachedAt = now;
        List<Inhabitant> people = people();
        List<Status> list = new ArrayList<>();
        list.add(foodStatus(people.size()));
        list.add(securityStatus(people));
        list.add(careStatus(people.size()));
        list.add(comfortStatus(people));
        cache = List.copyOf(list);

        // Satisfaction : moyenne pondérée des besoins comptés
        double sum = 0, weights = 0;
        for (Status s : cache) {
            if (!s.counted()) continue;
            sum += s.value() * config.weight(s.need());
            weights += config.weight(s.need());
        }
        satisfaction = people.isEmpty() || weights <= 0 ? -1 : (int) Math.round(sum / weights);
        return cache;
    }

    /** Satisfaction 0-100 %, ou -1 s'il n'y a pas d'habitant. */
    public int satisfaction() {
        statuses();
        return satisfaction;
    }

    /** « 72 % » (panneau Tab), « — » sans habitant. */
    public String satisfactionLine() {
        int s = satisfaction();
        return s < 0 ? "—" : s + " %";
    }

    private Status foodStatus(int pop) {
        List<String> help = new ArrayList<>(List.of("§7Chaque habitant mange §f" + fmt(config.rationsPerInhabitant())
                        + " §7rations à l'aube,", "§7prises dans les entrepôts d'§eHélène",
                "§7(cultures) et de §cRosalie §7(viandes).", "§7Vendez-leur de la nourriture !"));
        if (!foodCounted()) {
            return new Status(Need.NOURRITURE, false, -1, "Hélène ou Rosalie doit être installée", help);
        }
        int value = food < 0 ? projected(config.foods(), config.rationsPerInhabitant() * pop) : food;
        String detail = foodLast.isEmpty() ? "réserves pour " + days(config.foods(), config.rationsPerInhabitant() * pop)
                : "dernier repas : " + foodLast + " · réserves : " + days(config.foods(), config.rationsPerInhabitant() * pop);
        return new Status(Need.NOURRITURE, pop > 0, value, detail, help);
    }

    private Status careStatus(int pop) {
        List<String> help = new ArrayList<>(List.of("§7Chaque habitant reçoit §f" + fmt(config.carePerInhabitant())
                        + " §7soin à l'aube, pris dans", "§7l'entrepôt de §5Frère Anselme §7(miel,",
                "§7verrues du Nether, poudre lumineuse…)."));
        if (!careCounted()) {
            return new Status(Need.SANTE, false, -1, "Frère Anselme doit être installé", help);
        }
        int value = care < 0 ? projected(config.remedies(), config.carePerInhabitant() * pop) : care;
        String detail = careLast.isEmpty() ? "réserves pour " + days(config.remedies(), config.carePerInhabitant() * pop)
                : "derniers soins : " + careLast + " · réserves : " + days(config.remedies(), config.carePerInhabitant() * pop);
        return new Status(Need.SANTE, pop > 0, value, detail, help);
    }

    private Status securityStatus(List<Inhabitant> people) {
        long guards = people.stream().filter(i -> i.state == InhabitantManager.State.SOLDAT).count();
        int pop = people.size();
        double guardScore = pop == 0 ? 100 : Math.min(100, 100.0 * guards * config.inhabitantsPerGuard() / pop);
        List<Boolean> sieges = lastSieges();
        Integer record = sieges.isEmpty() ? null
                : (int) Math.round(100.0 * sieges.stream().filter(b -> b).count() / sieges.size());
        int value = record == null ? (int) Math.round(guardScore)
                : (int) Math.round((100 - config.siegeWeight()) / 100.0 * guardScore + config.siegeWeight() / 100.0 * record);
        String detail = guards + " garde(s) pour " + pop + " habitant(s) (1 pour " + fmt(config.inhabitantsPerGuard())
                + " attendu)" + (record == null ? "" : " · derniers sièges : " + sieges.stream().filter(b -> b).count()
                + "/" + sieges.size() + " repoussés");
        List<String> help = List.of("§7Formez des gardes chez §9Roland §7: 1 garde", "§7pour " + fmt(config.inhabitantsPerGuard())
                + " habitants donne 100 %.", "§7Les §f" + config.siegesKept() + " §7derniers sièges comptent pour §f"
                + config.siegeWeight() + " %§7 :", "§7une défaite fait chuter la sécurité.");
        if (!installed(CityNPC.GUARD)) return new Status(Need.SECURITE, false, -1, "Roland doit être installé", help);
        return new Status(Need.SECURITE, pop > 0, value, detail, help);
    }

    private Status comfortStatus(List<Inhabitant> people) {
        double sum = 0;
        int counted = 0;
        for (Inhabitant inh : people) {
            Building home = inhabitants.home(inh);
            if (home == null) continue;
            sum += config.comfort(houseLevel(home));
            counted++;
        }
        int value = counted == 0 ? 100 : (int) Math.round(sum / counted);
        List<String> help = List.of("§7Selon le niveau des maisons habitées", "§7(inspection : variété, décor, surface",
                "§7couverte). Niveau 1 : §f" + config.comfort(1) + " %§7, niveau 5 : §f" + config.comfort(5) + " %§7.",
                "§7Embellissez les habitations !");
        return new Status(Need.CONFORT, !people.isEmpty(), value, counted + " habitant(s) logé(s), niveau moyen des maisons", help);
    }

    /** Niveau d'une habitation (dernière inspection, sinon niveau enregistré). */
    public int houseLevel(Building b) {
        Inspection insp = inspector.peek(b);
        return insp != null ? insp.level() : b.level();
    }

    /** Avant le premier repas : ce que donnerait le prochain, d'après les réserves. */
    private int projected(Map<CityNPC, Map<Material, Double>> table, double needed) {
        if (needed <= 0) return 100;
        return (int) Math.round(100 * Math.min(1, available(table) / needed));
    }

    /** Rations (ou soins) disponibles dans les entrepôts des artisans installés. */
    private double available(Map<CityNPC, Map<Material, Double>> table) {
        double total = 0;
        for (Map.Entry<CityNPC, Map<Material, Double>> e : table.entrySet()) {
            if (!installed(e.getKey())) continue;
            for (Map.Entry<Material, Double> item : e.getValue().entrySet()) {
                total += npcData.getInventoryAmount(e.getKey(), item.getKey()) * item.getValue();
            }
        }
        return total;
    }

    /** « 3 aubes », « moins d'une aube ». */
    private String days(Map<CityNPC, Map<Material, Double>> table, double perDawn) {
        if (perDawn <= 0) return "—";
        double d = available(table) / perDawn;
        return d < 1 ? "moins d'une aube" : (int) d + " aube" + (d >= 2 ? "s" : "");
    }

    /* =========================
       SIÈGES (sécurité)
       ========================= */

    /** Issue d'un siège (victoire ou défaite) : compte dans la sécurité. */
    public void recordSiege(boolean victory, int tier) {
        try (PreparedStatement ps = db.getConnection().prepareStatement(
                "INSERT INTO siege_results (at, victory, tier) VALUES (?, ?, ?)")) {
            ps.setLong(1, System.currentTimeMillis());
            ps.setInt(2, victory ? 1 : 0);
            ps.setInt(3, tier);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        cachedAt = 0;
    }

    /** Derniers sièges (true = repoussé), du plus récent au plus ancien. */
    private List<Boolean> lastSieges() {
        List<Boolean> list = new ArrayList<>();
        try (PreparedStatement ps = db.getConnection().prepareStatement(
                "SELECT victory FROM siege_results ORDER BY at DESC LIMIT ?")) {
            ps.setInt(1, config.siegesKept());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(rs.getInt("victory") == 1);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    /* =========================
       BILANS (commandes)
       ========================= */

    /** /city habitants besoins : satisfaction, chaque besoin, effets. */
    public List<String> report() {
        List<String> lines = new ArrayList<>();
        int sat = satisfaction();
        lines.add("§6😊 Satisfaction des habitants : §f" + (sat < 0 ? "— (aucun habitant)" : sat + " %"));
        for (Status s : statuses()) {
            lines.add("§7• §f" + s.need().label + " §8(poids " + config.weight(s.need()) + ") §7: "
                    + (s.counted() ? color(s.value()) + s.value() + " %" : "§8non compté") + " §8— " + s.detail());
        }
        if (sat >= 0) {
            double arrival = config.arrivalFactor(sat);
            lines.add("§7Effets : arrivées " + (arrival <= 0 ? "§carrêtées" : "§f×" + fmt(1 / arrival) + " §7(plus rapides au-dessus de 1)")
                    + "§7, impôt §f×" + fmt(config.taxFactor(sat)) + "§7. Personne ne part, même mécontent.");
        }
        return lines;
    }

    /** /city eco check : revenus de l'impôt face aux chartes restantes. */
    public List<String> economyLines(int cityLevel, int taxPerInhabitant) {
        long civilians = inhabitants.civilians().stream().filter(i -> !i.test).count();
        int sat = satisfaction();
        double factor = sat < 0 ? 1 : config.taxFactor(sat);
        long perDawn = Math.round(civilians * taxPerInhabitant * factor);
        long remaining = 0;
        for (CityTier t : CityTier.values()) if (t.level >= cityLevel) remaining += t.upgradeCost;
        long total = 0;
        for (CityTier t : CityTier.values()) total += t.upgradeCost;
        List<String> lines = new ArrayList<>();
        lines.add("§6📊 Impôt : §f" + perDawn + " coins par aube §7(" + civilians + " habitants × " + taxPerInhabitant
                + " × " + fmt(factor) + "), soit ≈ §f" + perDawn * 3 + " §7par heure (une aube toutes les 20 min).");
        lines.add("§7Chartes : §f" + fmt(remaining) + " §7coins restants sur §f" + fmt(total)
                + (perDawn > 0 ? " §7≈ §f" + Math.round(remaining / (perDawn * 3.0)) + " h §7d'impôt seul." : "."));
        return lines;
    }

    private static String color(int v) {
        return v >= 70 ? "§a" : v >= 40 ? "§6" : "§c";
    }

    private static String fmt(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.format(java.util.Locale.FRANCE, "%.1f", v);
    }

    /* =========================
       ÉTAT ENREGISTRÉ (table city_state)
       ========================= */

    private String state(String key, String def) {
        try (PreparedStatement ps = db.getConnection().prepareStatement("SELECT value FROM city_state WHERE key = ?")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString("value") : def;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return def;
        }
    }

    private void saveState(String key, String value) {
        try (PreparedStatement ps = db.getConnection().prepareStatement(
                "INSERT INTO city_state (key, value) VALUES (?, ?) ON CONFLICT(key) DO UPDATE SET value = excluded.value")) {
            ps.setString(1, key);
            ps.setString(2, value);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /** Pour l'écran « Population » : niveau de satisfaction par besoin (valeur, ou -1 s'il ne compte pas). */
    public Map<Need, Integer> values() {
        Map<Need, Integer> map = new EnumMap<>(Need.class);
        for (Status s : statuses()) map.put(s.need(), s.counted() ? s.value() : -1);
        return map;
    }
}
