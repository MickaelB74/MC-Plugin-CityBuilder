package com.citycore.jobs;

import com.citycore.building.Building;
import com.citycore.building.BuildingInspector;
import com.citycore.building.BuildingManager;
import com.citycore.building.Inspection;
import com.citycore.economy.MarketService;
import com.citycore.inhabitant.InhabitantManager;
import com.citycore.inhabitant.InhabitantManager.Inhabitant;
import com.citycore.inhabitant.WorkPlace;
import com.citycore.npc.CityNPC;
import com.citycore.npc.NPCDataManager;
import com.citycore.npc.NPCState;
import com.citycore.npc.villager.VillagerConfig;
import com.citycore.npc.villager.VillagerConfig.SellPrice;
import org.bukkit.Material;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Emplois : les habitants travaillent pour les artisans et remplissent leurs entrepôts, pour que
 * la ville tourne sans que les joueurs aient à tout fournir.
 *
 * Chaque artisan installé peut embaucher des habitants civils (menu commun, Actions → Employés),
 * dans la limite de son rang. Ils travaillent dans son annexe de travail (bâtiment « Annexe »
 * créé depuis ses Actions, avec ses exigences et ses niveaux). Le jour seulement, quand un joueur
 * est connecté, chaque employé produit à chaque cycle (plus court quand l'annexe monte de niveau)
 * par-cycle exemplaires (8 par défaut) de chaque objet que l'artisan rachète : la ville récolte
 * pour les joueurs, qui se servent gratuitement dans l'entrepôt.
 *
 * Garde-fous d'équilibrage :
 *  - nombre d'employés limité par le rang de l'artisan, cycle de plusieurs minutes, travail le
 *    jour seulement ;
 *  - un objet de valeur est produit plus rarement (chance = valeur-pleine / valeur de l'objet),
 *    un trophée (au-delà de valeur-max) jamais ;
 *  - plus de production au-delà de plafond % de la capacité de l'entrepôt : les ventes des
 *    joueurs gardent leur prix ;
 *  - aucune pièce, aucune expérience d'artisan ; la production ne sert qu'à garnir l'entrepôt.
 */
public final class JobsManager implements WorkPlace {

    private final JavaPlugin        plugin;
    private final BuildingManager   buildings;
    private final BuildingInspector inspector;
    private final NPCDataManager    npcData;
    private final MarketService     market;
    private final InhabitantManager inhabitants;
    private final JobsConfig        config;

    /** Annexes par artisan, relues au plus toutes les 5 s (le comportement des employés les lit souvent). */
    private final Map<CityNPC, Building> annexes = new EnumMap<>(CityNPC.class);
    private long annexesAt;

    public JobsManager(JavaPlugin plugin, BuildingManager buildings, BuildingInspector inspector,
                       NPCDataManager npcData, MarketService market, InhabitantManager inhabitants) {
        this.plugin      = plugin;
        this.buildings   = buildings;
        this.inspector   = inspector;
        this.npcData     = npcData;
        this.market      = market;
        this.inhabitants = inhabitants;
        this.config      = new JobsConfig(plugin);
    }

    public void start() {
        inhabitants.setWorkPlace(this);
    }

    public JobsConfig config() {
        return config;
    }

    /* =========================
       EMBAUCHE
       ========================= */

    /** Employés au plus pour cet artisan (selon son rang). */
    public int maxEmployees(CityNPC boss) {
        return config.maxEmployees(npcData.getLevel(boss));
    }

    /** Embauche d'un habitant civil par un artisan ; null si c'est fait, sinon la raison du refus. */
    public String hire(int inhabitantId, CityNPC boss) {
        if (npcData.getState(boss) != NPCState.ASSIGNED) return plain(boss) + " doit être installé dans son atelier.";
        int max = maxEmployees(boss);
        if (inhabitants.employees(boss).size() >= max) {
            return plain(boss) + " emploie déjà " + max + " habitant(s) (rang " + npcData.getLevel(boss) + ").";
        }
        Inhabitant inh = inhabitants.get(inhabitantId);
        if (inh == null || inh.state != InhabitantManager.State.HABITANT || inh.test) return "Cet habitant n'est plus disponible.";
        CityNPC previous = inhabitants.employerOf(inh);
        String err = inhabitants.setEmployer(inhabitantId, boss);
        if (err != null) return err;
        org.bukkit.Bukkit.broadcastMessage("§3⚒ " + inh.name + " §7travaille désormais pour " + boss.displayName
                + (previous != null && previous != boss ? " §8(quitte " + plain(previous) + ")" : "")
                + (annex(boss) == null ? " §8— il faut encore lui créer une annexe de travail" : ""));
        return null;
    }

    /** Renvoi d'un employé ; null si c'est fait, sinon la raison du refus. */
    public String fire(int inhabitantId) {
        Inhabitant inh = inhabitants.get(inhabitantId);
        CityNPC boss = inh == null ? null : inhabitants.employerOf(inh);
        if (boss == null) return "Cet habitant n'a pas d'emploi.";
        String err = inhabitants.setEmployer(inhabitantId, null);
        if (err == null) org.bukkit.Bukkit.broadcastMessage("§7⚒ " + inh.name + " §7ne travaille plus pour " + boss.displayName + "§7.");
        return err;
    }

    /**
     * Supprime l'annexe de travail d'un artisan (la zone redevient libre) ; ses employés gardent
     * leur emploi et attendent une nouvelle annexe. @return le nom de l'annexe supprimée, ou null
     */
    public String deleteAnnex(CityNPC boss) {
        Building annex = annex(boss);
        if (annex == null) return null;
        buildings.deleteBuilding(annex.id());
        annexesAt = 0;               // relecture des annexes
        inspector.refreshBuildings();
        return annex.name();
    }

    /* =========================
       LIEU DE TRAVAIL (WorkPlace)
       ========================= */

    @Override
    public Building annex(CityNPC boss) {
        long now = System.currentTimeMillis();
        if (now - annexesAt > 5000) {
            annexes.clear();
            for (Building b : buildings.getAllBuildings()) {
                if (b.type() != com.citycore.building.BuildingType.ANNEXE || b.reservedNpc() == null) continue;
                annexes.putIfAbsent(b.reservedNpc(), b);
            }
            annexesAt = now;
        }
        return annexes.get(boss);
    }

    /** Niveau d'une annexe (dernière inspection, sinon niveau enregistré). */
    public int annexLevel(Building annex) {
        Inspection insp = inspector.peek(annex);
        return insp != null ? insp.level() : annex.level();
    }

    @Override
    public String blocked(CityNPC boss) {
        if (npcData.getState(boss) != NPCState.ASSIGNED) return plain(boss) + " sans atelier";
        Building annex = annex(boss);
        if (annex == null) return "pas d'annexe de travail";
        if (annexLevel(annex) <= 0) return "annexe incomplète";
        return null;
    }

    @Override
    public long cycleMs(CityNPC boss) {
        if (blocked(boss) != null) return -1;
        return config.cycleMs(annexLevel(annex(boss)));
    }

    /**
     * Un cycle d'un employé : pour chaque objet que l'artisan rachète, +par-cycle exemplaire(s)
     * s'il vaut au plus valeur-pleine l'unité, sinon avec une chance valeur-pleine / valeur ;
     * jamais au-delà de valeur-max, ni au-delà de plafond % de la capacité de l'entrepôt.
     */
    @Override
    public int produce(CityNPC boss) {
        VillagerConfig vc = market.config(boss);
        if (vc == null) return 0;
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        int total = 0;
        for (Map.Entry<Material, SellPrice> e : vc.getSellPrices().entrySet()) {
            SellPrice sp = e.getValue();
            double unit = sp.price() / (double) Math.max(1, sp.quantity());
            if (unit > config.maxValue()) continue; // trophée : jamais produit
            double expected = config.perCycle() * Math.min(1, config.fullValue() / unit);
            int amount = (int) Math.floor(expected) + (rnd.nextDouble() < expected - Math.floor(expected) ? 1 : 0);
            if (amount <= 0) continue;
            if (sp.capacity() > 0) {
                int stock = npcData.getInventoryAmount(boss, e.getKey());
                int ceiling = (int) Math.floor(sp.capacity() * config.ceiling() / 100.0);
                amount = Math.min(amount, ceiling - stock);
                if (amount <= 0) continue;
            }
            npcData.addToInventory(boss, e.getKey(), amount);
            total += amount;
        }
        return total;
    }

    /** Outil tenu par les employés : celui du métier, sinon le premier objet que l'artisan rachète. */
    @Override
    public Material tool(CityNPC boss) {
        return switch (boss) {
            case WOODCUTTER -> Material.IRON_AXE;
            case FARMER     -> Material.IRON_HOE;
            case STONEMASON -> Material.STONE_PICKAXE;
            case MINER      -> Material.IRON_PICKAXE;
            case FISHERMAN  -> Material.FISHING_ROD;
            case BLACKSMITH -> Material.IRON_INGOT;
            case WEAVER     -> Material.SHEARS;
            case MONK       -> Material.GLASS_BOTTLE;
            default         -> workMaterial(boss);
        };
    }

    /** Matière des particules : le premier objet que l'artisan rachète. */
    @Override
    public Material workMaterial(CityNPC boss) {
        VillagerConfig vc = market.config(boss);
        if (vc != null) {
            for (Material m : vc.getSellPrices().keySet()) if (m.isItem()) return m;
        }
        return Material.OAK_PLANKS;
    }

    /* =========================
       BILANS
       ========================= */

    /** Production moyenne d'un cycle d'un employé (objets), entrepôt sous le plafond. */
    public double expectedPerCycle(CityNPC boss) {
        VillagerConfig vc = market.config(boss);
        if (vc == null) return 0;
        double total = 0;
        for (SellPrice sp : vc.getSellPrices().values()) {
            double unit = sp.price() / (double) Math.max(1, sp.quantity());
            if (unit <= config.maxValue()) total += config.perCycle() * Math.min(1, config.fullValue() / unit);
        }
        return total;
    }

    /** /city emplois : chaque artisan qui emploie ou a une annexe. */
    public List<String> report() {
        List<String> lines = new ArrayList<>();
        lines.add("§3⚒ Emplois de Valmont");
        int count = 0;
        for (CityNPC boss : CityNPC.values()) {
            if (boss == CityNPC.MAYOR) continue;
            List<Inhabitant> staff = inhabitants.employees(boss);
            Building annex = annex(boss);
            if (staff.isEmpty() && annex == null) continue;
            count++;
            String blocked = blocked(boss);
            long cycle = cycleMs(boss);
            lines.add("§7• " + boss.displayName + " §7: §f" + staff.size() + "/" + maxEmployees(boss) + " §7employé(s)"
                    + (annex == null ? " §8· pas d'annexe" : " §8· annexe niv. " + annexLevel(annex))
                    + (blocked != null ? " §c· " + blocked : " §8· cycle " + clock(cycle) + ", ≈ "
                    + String.format(java.util.Locale.FRANCE, "%.1f", expectedPerCycle(boss)) + " objets")
                    + (staff.isEmpty() ? "" : " §8(" + String.join(", ", staff.stream().map(i -> i.name).toList()) + ")"));
        }
        if (count == 0) lines.add("§8Aucun employé : Actions d'un artisan installé → Employés.");
        return lines;
    }

    public static String clock(long ms) {
        long s = Math.max(0, ms) / 1000;
        return String.format("%d:%02d", s / 60, s % 60);
    }

    private static String plain(CityNPC npc) {
        return npc.displayName.replaceAll("§.", "");
    }
}
