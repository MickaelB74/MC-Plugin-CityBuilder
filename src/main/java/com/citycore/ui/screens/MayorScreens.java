package com.citycore.ui.screens;

import com.citycore.building.Building;
import com.citycore.building.BuildingManager;
import com.citycore.city.City;
import com.citycore.city.CityManager;
import com.citycore.inhabitant.InhabitantManager;
import com.citycore.needs.NeedsConfig;
import com.citycore.needs.NeedsConfig.Need;
import com.citycore.needs.NeedsManager;
import com.citycore.npc.CityNPC;
import com.citycore.npc.NPCManager;
import com.citycore.npc.mayor.MayorBuildingGUI;
import com.citycore.npc.mayor.MayorGUI;
import com.citycore.npc.mayor.MayorQuestGUI;
import com.citycore.quest.city.CityQuest;
import com.citycore.quest.city.CityQuestManager;
import com.citycore.quest.city.CityTier;
import com.citycore.ui.UiCanvas;
import com.citycore.ui.UiItems;
import com.citycore.ui.UiKit;
import com.citycore.util.TypewriterUtil;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

import static com.citycore.ui.UiLayout.*;

/**
 * Écrans illustrés d'Alderic : accueil de la mairie, quêtes de la cité et charte, bâtiments,
 * trésor, population (clic sur son portrait). Les boutons appellent les actions des menus
 * classiques du maire.
 */
public final class MayorScreens {

    private final UiKit            kit;
    private final MayorGUI         mayor;
    private final CityManager      city;
    private final CityQuestManager quests;
    private final NPCManager       npcManager;
    private final BuildingManager  buildings;

    public MayorScreens(UiKit kit, MayorGUI mayor, CityManager city, CityQuestManager quests,
                        NPCManager npcManager, BuildingManager buildings) {
        this.kit        = kit;
        this.mayor      = mayor;
        this.city       = city;
        this.quests     = quests;
        this.npcManager = npcManager;
        this.buildings  = buildings;
    }

    /** Écrans du type des bâtiments (clic droit dans la liste : changer le type). */
    private BuildingScreens screens;

    public void setBuildingScreens(BuildingScreens screens) {
        this.screens = screens;
    }

    /** Inspection des constructions (phase 2) : niveau et exigences dans la liste des bâtiments. */
    private com.citycore.building.BuildingInspector inspector;

    public void setInspector(com.citycore.building.BuildingInspector inspector) {
        this.inspector = inspector;
    }

    /** Habitants d'une habitation (« Habitants : 2 / 3 lits »), phase 3 ; null = rien à afficher. */
    private java.util.function.Function<Building, String> occupancy = b -> null;

    public void setOccupancy(java.util.function.Function<Building, String> occupancy) {
        this.occupancy = occupancy;
    }

    /** Besoins et satisfaction (phase 6) : écran « Population » ; null = pas d'écran. */
    private NeedsManager needs;
    private InhabitantManager inhabitants;

    public void setPopulation(NeedsManager needs, InhabitantManager inhabitants) {
        this.needs = needs;
        this.inhabitants = inhabitants;
    }

    /* =========================
       POINTS D'ENTRÉE (false = menu classique)
       ========================= */

    public boolean openMain(Player p)      { return ready(p) && kit.show(new Main(p)); }

    public boolean openQuests(Player p)    { return ready(p) && kit.show(new Quests(p)); }

    public boolean openBuildings(Player p) { return ready(p) && kit.show(new Buildings(p, false)); }

    public boolean openDelete(Player p)    { return ready(p) && kit.show(new Buildings(p, true)); }

    public boolean openTreasury(Player p)  { return ready(p) && kit.show(new Treasury(p)); }

    private boolean ready(Player p) {
        return kit.active(p) && city.getCity() != null;
    }

    /** L'inventaire ouvert est-il l'écran illustré des quêtes de la cité ? (fermé au changement de palier) */
    public static boolean isCityQuestScreen(Object holder) {
        return holder instanceof Quests;
    }

    private MayorQuestGUI    questGUI()    { return mayor.getQuestGUI(); }

    private MayorBuildingGUI buildingGUI() { return mayor.getBuildingGUI(); }

    private static final String MAYOR_NAME = ChatColor.stripColor(CityNPC.MAYOR.displayName);

    /* =========================
       BASE
       ========================= */

    private abstract class MayorMenu extends ValmontMenu {

        MayorMenu(Player p) {
            super(MayorScreens.this.kit, p);
        }

        void portrait(UiCanvas c) {
            portrait(c, null);
        }

        /** Portrait d'Alderic ; onClick non nul : un clic ouvre l'écran « Population ». */
        void portrait(UiCanvas c, Runnable onClick) {
            kit.portraits().draw(c, CityNPC.MAYOR, false);
            List<String> lore = new ArrayList<>();
            for (String line : CityNPC.MAYOR.backstory) lore.add("§8§o" + line);
            if (onClick != null) {
                int sat = needs.satisfaction();
                lore.add("");
                lore.add("§7Satisfaction des habitants : " + (sat < 0 ? "§8—" : color(sat) + sat + " %"));
                lore.add("§eCliquez : population et besoins");
            }
            zone(0, 1, 4, 4, CityNPC.MAYOR.displayName + " §8— §7Maire", lore, onClick == null ? null : t -> onClick.run());
        }

        void bigButton(UiCanvas c, int row, String sprite, String name, List<String> lore, Runnable action) {
            c.sprite(sprite, sx(4), sy(row));
            zone(4, row, 5, 1, name, lore, action == null ? null : t -> action.run());
        }

        /** Plaque de valeur (caisse, bourse) : « LIBELLÉ montant », ou pièce + montant si trop long. */
        void valuePlate(UiCanvas c, int row, String label, long amount, String name, List<String> lore) {
            c.sprite("plate", sx(4), sy(row));
            String text = label + " " + fmt(amount);
            if (c.width(text, true) <= 80) {
                c.text(text, sx(4) + 6, rowText(row), GOLD, true);
            } else {
                c.sprite("coin", sx(4) + 7, sy(row) + 5);
                c.text(c.fit(fmt(amount), 66, true), sx(4) + 19, rowText(row), GOLD, true);
            }
            zone(4, row, 5, 1, name, lore, null);
        }
    }

    /* =========================
       ACCUEIL DE LA MAIRIE
       ========================= */

    private final class Main extends MayorMenu {

        Main(Player p) { super(p); }

        @Override
        protected void draw(UiCanvas c) {
            City data = city.getCity();
            CityTier tier = CityTier.fromLevel(quests.getCityLevel());
            boolean complete = quests.isTierComplete(tier);
            int claimed = data.getClaimedChunks(), max = Math.max(1, data.getMaxChunks());

            c.background("bg_character");
            nameplate(c, MAYOR_NAME, "Maire");
            c.text(ChatColor.stripColor(tier.tierName), STATE_X, rowText(0), GOLD, true);
            c.textRight("NIV. " + tier.level, sx(6) - 6, rowText(0), CREAM, true);
            zone(0, 0, 6, 1, "§6" + ChatColor.stripColor(tier.tierName) + " §7— niveau " + tier.level,
                    List.of("§7" + tier.chapter, "§7Taille : §f" + claimed + " §7/ §f" + data.getMaxChunks()), null);

            // En-tête : récit, suivi, fermeture
            c.sprite("ic_story", sx(6), sy(0));
            List<String> story = new ArrayList<>();
            story.add("§7" + tier.chapter);
            story.add("");
            story.add("§eCliquez pour réécouter Alderic");
            zone(6, 0, 1, 1, "§6📜 Récit d'Alderic", story, t -> questGUI().playStory(player));
            boolean following = npcManager.isFollowing(player, CityNPC.MAYOR);
            c.sprite(following ? "ic_follow_stop" : "ic_follow", sx(7), sy(0));
            zone(7, 0, 1, 1, following ? "§c⛔ Arrêter de suivre" : "§a👣 Demander de suivre",
                    List.of(following ? "§7Alderic restera ici." : "§7Alderic vous accompagnera dans la ville."),
                    t -> mayor.handleClick(player, MayorGUI.SLOT_FOLLOW));
            close(c);
            portrait(c, needs == null ? null : () -> kit.show(new Population(player)));

            List<String> questLore = new ArrayList<>(List.of("§7Niveau  : §f" + tier.level,
                    "§7Palier  : " + tier.tierName, "§7" + tier.chapter, ""));
            questLore.add(complete ? "§a✔ Toutes les quêtes sont complètes !" : "§7Quêtes du palier disponibles.");
            bigButton(c, 1, "btn_quetes", complete ? "§6📜 Quêtes de la cité §l[PRÊT]" : "§6📜 Quêtes de la cité",
                    questLore, () -> mayor.handleClick(player, MayorGUI.SLOT_INFO));
            if (complete && !tier.isMaxTier()) c.sprite("badge_new", sx(8) + 4, sy(1) + 4);
            bigButton(c, 2, "btn_batiments", "§a🏗 Bâtiments",
                    List.of("§7Gérez les bâtiments de la ville."),
                    () -> mayor.handleClick(player, MayorGUI.SLOT_BUILDINGS));
            bigButton(c, 3, "btn_tresor", "§6💰 Trésor",
                    List.of("§7Caisse : §6" + fmt(city.getCityCoins()) + " coins", "§7Déposez des coins pour",
                            "§7financer chartes et parcelles."),
                    () -> mayor.handleClick(player, MayorGUI.SLOT_ECONOMY));
            int price = city.getNextExpandPrice();
            boolean afford = city.canAfford(price);
            bigButton(c, 4, afford ? "btn_agrandir" : "btn_agrandir_off", "§6🗺 Agrandir la ville",
                    List.of("§7Ajoute §f+1 emplacement §7de chunk", "", "§7Prix   : §6" + fmt(price) + " coins",
                            "§7Caisse : §6" + fmt(city.getCityCoins()) + " coins", "",
                            afford ? "§aCliquez pour acheter" : "§cFonds insuffisants"),
                    () -> mayor.handleClick(player, MayorGUI.SLOT_EXPAND));

            // Parcelles : revendiquer ou rendre celle où l'on se tient, voir les limites
            // (pour les joueurs, à la place de /city claim et unclaim, réservées aux administrateurs)
            bar(c, (double) claimed / max, claimed >= max, "PARCELLES " + claimed + " / " + data.getMaxChunks());
            zone(0, 5, 9, 1, "§e🗺 Parcelles de la ville", mayor.parcelLore(player, data), t -> mayor.handleParcelClick(player, t));
        }
    }

    /* =========================
       POPULATION — besoins et satisfaction des habitants (phase 6)
       Rangée 1 : les quatre besoins, la satisfaction et ses effets ; rangée 2 : la population ;
       rangées 3-4 : les habitations (niveau → confort).
       ========================= */

    private final class Population extends MayorMenu {

        Population(Player p) { super(p); }

        @Override
        protected void draw(UiCanvas c) {
            c.background("bg_grid");
            nameplate(c, MAYOR_NAME, "Population");
            back(c, 0, "Mairie", () -> mayor.open(player));
            c.sprite("rib_population", sx(1), sy(0));
            NeedsConfig cfg = needs.config();
            info(c, 7, "§6😊 Satisfaction des habitants", List.of(
                    "§7Moyenne des besoins comptés, pondérée :",
                    "§7nourriture §f" + cfg.weight(Need.NOURRITURE) + "§7, sécurité §f" + cfg.weight(Need.SECURITE)
                            + "§7, santé §f" + cfg.weight(Need.SANTE) + "§7, confort §f" + cfg.weight(Need.CONFORT) + "§7.",
                    "§7Un besoin ne compte qu'une fois", "§7son artisan installé.", "",
                    "§7C'est un bonus : haute, elle accélère", "§7les arrivées et augmente l'impôt ;",
                    "§7basse, elle les ralentit un peu.", "§7Personne ne part, même mécontent.", "",
                    "§a■ §7≥ 70 %  §6■ §7≥ 40 %  §c■ §7moins  §8■ §7non compté"));
            close(c);

            // Rangée 1 : les besoins…
            int col = 0;
            for (NeedsManager.Status s : needs.statuses()) {
                Material icon = switch (s.need()) {
                    case NOURRITURE -> Material.BREAD;
                    case SECURITE   -> Material.SHIELD;
                    case SANTE      -> Material.HONEY_BOTTLE;
                    case CONFORT    -> Material.RED_BED;
                };
                c.sprite(!s.counted() ? "ring_gray" : ring(s.value()), sx(col), sy(1));
                List<String> lore = new ArrayList<>();
                lore.add(s.counted() ? color(s.value()) + s.value() + " % §8(poids " + cfg.weight(s.need()) + ")"
                        : "§8Non compté : " + s.detail());
                if (s.counted()) for (String part : s.detail().split(" · ")) lore.add("§7" + part);
                lore.add("");
                lore.addAll(s.help());
                item(col, 1, UiItems.shown(icon, (s.counted() ? "§f" : "§8") + s.need().label, lore, false), null);
                col++;
            }

            // … la satisfaction et ses effets
            int sat = needs.satisfaction();
            c.sprite(sat < 0 ? "ring_gray" : sat >= 70 ? "ring_gold" : ring(sat), sx(5), sy(1));
            item(5, 1, UiItems.shown(Material.SUNFLOWER, "§6Satisfaction : " + (sat < 0 ? "—" : sat + " %"),
                    sat < 0 ? List.of("§7Aucun habitant pour l'instant.") : needs.report().subList(1, needs.report().size()), sat >= 70), null);

            double arrival = sat < 0 ? 1 : cfg.arrivalFactor(sat);
            long wait = inhabitants.nextArrivalIn() / 1000;
            c.sprite(arrival <= 0 ? "ring_red" : arrival < 1 ? "ring_green" : arrival > 1 ? "ring_amber" : "ring_gray", sx(6), sy(1));
            item(6, 1, UiItems.shown(Material.CLOCK, "§fArrivées d'habitants", List.of(
                    arrival <= 0 ? "§cPlus personne ne vient s'installer." : "§7Délai entre deux arrivées : §f×" + fmtFactor(arrival),
                    arrival > 0 ? "§7Prochaine possible dans §f" + wait / 60 + " min " + wait % 60 + " s" : "§7Améliorez les besoins !",
                    "", "§8Plus la satisfaction est haute,", "§8plus les arrivées sont rapides."), false), null);

            double taxFactor = sat < 0 ? 1 : cfg.taxFactor(sat);
            long civilians = inhabitants.civilians().stream().filter(i -> !i.test).count();
            long perDawn = Math.round(civilians * inhabitants.taxPerInhabitant() * taxFactor);
            c.sprite(taxFactor <= 0 ? "ring_red" : taxFactor > 1 ? "ring_green" : taxFactor < 1 ? "ring_amber" : "ring_gray", sx(7), sy(1));
            item(7, 1, UiItems.shown(Material.GOLD_NUGGET, "§6Impôt de l'aube", List.of(
                    "§7" + civilians + " habitant(s) × " + inhabitants.taxPerInhabitant() + " × §f" + fmtFactor(taxFactor),
                    "§7= §6" + perDawn + " coins §7par aube", "", "§8Gardes et recrues ne paient pas."), false), null);

            long employed = inhabitants.civilians().stream().filter(i -> !i.test && i.employer != null).count();
            c.sprite(employed > 0 ? "ring_green" : "ring_gray", sx(8), sy(1));
            item(8, 1, UiItems.shown(Material.IRON_HOE, "§3⚒ Employés : §f" + employed, List.of(
                    "§7Habitants qui travaillent pour les", "§7artisans et remplissent leurs entrepôts",
                    "§7(nourriture comprise).", "", "§8Chez un artisan : Actions → Employés."), false), null);

            // Rangée 2 : la population
            long pop = inhabitants.living().stream().filter(i -> !i.test).count();
            long guards = inhabitants.guards().stream().filter(i -> !i.test).count();
            populationTile(c, 0, Material.PLAYER_HEAD, "§fHabitants", String.valueOf(pop),
                    List.of("§7Vivants, gardes et recrues compris."));
            populationTile(c, 1, Material.WHITE_BED, "§fPlaces", String.valueOf(inhabitants.capacity()),
                    List.of("§7Lits des habitations conformes."));
            populationTile(c, 2, Material.IRON_SWORD, "§9Gardes", String.valueOf(guards),
                    List.of("§7Formés chez Roland."));
            populationTile(c, 3, Material.WOODEN_SWORD, "§6Recrues", String.valueOf(inhabitants.trainees().size()),
                    List.of("§7En formation chez Roland."));
            populationTile(c, 4, Material.WITHER_ROSE, "§8Deuils", String.valueOf(inhabitants.mourningCount()),
                    List.of("§7Lits dont l'habitant est mort,", "§7en attente d'un remplaçant."));

            // Rangées 3-4 : les habitations, leur niveau et leur confort
            List<Building> houses = buildings.getAllBuildings().stream()
                    .filter(b -> b.type() == com.citycore.building.BuildingType.HABITATION).toList();
            for (int i = 0; i < Math.min(18, houses.size()); i++) {
                Building b = houses.get(i);
                int level = needs.houseLevel(b);
                int comfort = cfg.comfort(level);
                int x = i % 9, y = 3 + i / 9;
                c.sprite(level == 0 ? "ring_red" : ring(comfort), sx(x), sy(y));
                List<String> lore = new ArrayList<>();
                lore.add(level == 0 ? "§c✘ Incomplète" : "§6Niveau " + level + " " + com.citycore.building.Inspection.stars(level));
                String occ = occupancy.apply(b);
                if (occ != null) lore.add(occ);
                lore.add("§7Confort : " + color(comfort) + comfort + " %");
                item(x, y, UiItems.shown(Material.RED_BED, "§e🏠 " + b.name(), lore, level >= 5), null);
            }
            if (houses.isEmpty()) {
                item(0, 3, UiItems.blank("§7Aucune habitation", List.of("§7Créez-en une chez Alderic :",
                        "§7Bâtiments, type « habitation »")), null);
            }
            footer(c, false, sat < 0 ? "AUCUN HABITANT" : "SATISFACTION " + sat + " % · " + pop + " HABITANT" + (pop > 1 ? "S" : ""));
        }

        private void populationTile(UiCanvas c, int col, Material icon, String name, String value, List<String> help) {
            c.sprite("ring_gray", sx(col), sy(2));
            List<String> lore = new ArrayList<>(List.of("§f" + value, ""));
            lore.addAll(help);
            ItemStack stack = UiItems.shown(icon, name + " : §f" + value, lore, false);
            int n = 0;
            try { n = Integer.parseInt(value); } catch (NumberFormatException ignored) { }
            stack.setAmount(Math.max(1, Math.min(64, n)));
            item(col, 2, stack, null);
        }
    }

    private static String ring(int v) {
        return v >= 70 ? "ring_green" : v >= 40 ? "ring_amber" : "ring_red";
    }

    private static String color(int v) {
        return v >= 70 ? "§a" : v >= 40 ? "§6" : "§c";
    }

    private static String fmtFactor(double f) {
        return f == Math.rint(f) ? String.valueOf((long) f) : String.format(java.util.Locale.FRANCE, "%.1f", f);
    }

    /* =========================
       TRÉSOR
       ========================= */

    private final class Treasury extends MayorMenu {

        Treasury(Player p) { super(p); }

        @Override
        protected void draw(UiCanvas c) {
            long treasury = city.getCityCoins();
            long purse = balance();
            int price = city.getNextExpandPrice();

            c.background("bg_character");
            nameplate(c, MAYOR_NAME, "Trésor");
            c.text("TRÉSOR DE LA VILLE", STATE_X, rowText(0), GOLD, true);
            c.sprite("ic_back", sx(6), sy(0));
            zone(6, 0, 1, 1, "§7← Retour", List.of("§7Retourner à la mairie"), t -> mayor.open(player));
            c.sprite("ic_purse", sx(7), sy(0));
            zone(7, 0, 1, 1, "§6💰 Votre bourse", List.of("§7Solde : §e" + fmt(purse) + " coins"), null);
            close(c);
            portrait(c);

            valuePlate(c, 1, "CAISSE", treasury, "§6💰 Caisse de la ville",
                    List.of("§7Solde actuel : §6" + fmt(treasury) + " coins"));
            valuePlate(c, 2, "BOURSE", purse, "§e👛 Votre bourse",
                    List.of("§7Votre argent : §e" + fmt(purse) + " coins"));
            bigButton(c, 3, "btn_deposer", "§a⬆ Déposer des coins",
                    List.of("§7Transférez vos coins", "§7dans la caisse de la ville.", "",
                            "§eCliquez pour saisir le montant"),
                    () -> mayor.getEconomyGUI().promptDeposit(player));

            bar(c, (double) treasury / Math.max(1, price), treasury >= price, "PARCELLE : " + fmt(price));
            zone(0, 5, 9, 1, "§eProchaine parcelle", List.of("§7Prix : §6" + fmt(price) + " coins",
                    "§7Caisse : §6" + fmt(treasury) + " coins"), null);
        }
    }

    /* =========================
       QUÊTES DE LA CITÉ
       ========================= */

    private final class Quests extends MayorMenu {

        Quests(Player p) { super(p); }

        @Override
        protected void draw(UiCanvas c) {
            CityTier tier = CityTier.fromLevel(quests.getCityLevel());
            quests.syncCurrentTier();
            List<CityQuest> list = quests.displayedQuests();

            c.background("bg_cityquests");
            nameplate(c, MAYOR_NAME, "Maire");
            back(c, 0, "Retour", () -> mayor.open(player));
            c.sprite("rib_quetes", sx(1), sy(0));
            List<String> chapter = new ArrayList<>();
            chapter.add("§7" + ChatColor.stripColor(tier.tierName) + " — niveau " + tier.level);
            chapter.add("");
            chapter.add("§a■ §7accomplie   §6■ §7en cours   §5■ §7bonus");
            info(c, 7, "§6📜 " + tier.chapter, chapter);
            close(c);

            for (int i = 0; i < Math.min(GRID, list.size()); i++) {
                CityQuest q = list.get(i);
                int col = i % 9, row = 1 + i / 9;
                boolean done = quests.isCompleted(q.id());
                int current = done ? quests.target(q) : quests.computeCurrentValue(q);
                String ring = done ? "ring_green" : q.optional() ? "ring_purple" : current > 0 ? "ring_amber" : null;
                if (ring != null) c.sprite(ring, sx(col), sy(row));
                ItemMeta meta = questGUI().buildQuestItem(player, q).getItemMeta();
                item(col, row, UiItems.shown(done ? Material.LIME_DYE : q.icon(), meta.getDisplayName(),
                                meta.getLore(), false),
                        t -> questGUI().clickQuest(player, q.id(), t.isShiftClick() && t.isRightClick()));
            }

            // Pied de page : récit et charte
            c.sprite("fb_recit", sx(0), sy(5));
            List<String> story = new ArrayList<>();
            for (String line : tier.story) {
                story.add("§7" + ChatColor.stripColor(TypewriterUtil.resolve(line, player)).replace("Alderic : ", ""));
            }
            story.add("");
            story.add("§eCliquez pour réécouter Alderic");
            zone(0, 5, 3, 1, "§6📜 " + tier.chapter, story, t -> questGUI().playStory(player));

            String charter = tier.isMaxTier() ? "fb_capitale"
                    : !quests.isTierComplete(tier) ? "fb_charte_lock"
                    : city.getCityCoins() >= quests.upgradeCost(tier) ? "fb_charte" : "fb_charte_fonds";
            c.sprite(charter, sx(3), sy(5));
            ItemMeta up = questGUI().buildUpgradeButton(tier).getItemMeta();
            zone(3, 5, 6, 1, up.getDisplayName(), up.getLore(), t -> questGUI().upgrade(player));
        }
    }

    /* =========================
       BÂTIMENTS (liste et suppression)
       ========================= */

    private final class Buildings extends MayorMenu {

        private final boolean deleting;

        Buildings(Player p, boolean deleting) {
            super(p);
            this.deleting = deleting;
        }

        @Override
        protected void draw(UiCanvas c) {
            c.background("bg_buildings");
            nameplate(c, MAYOR_NAME, "Maire");
            back(c, 0, deleting ? "Liste des bâtiments" : "Retour",
                    () -> { if (deleting) buildingGUI().open(player); else mayor.open(player); });
            c.sprite(deleting ? "rib_supprimer" : "rib_batiments", sx(1), sy(0));
            info(c, 7, deleting ? "§c➖ Supprimer un bâtiment" : "§a🏗 Bâtiments de la ville",
                    deleting ? List.of("§7Cliquez sur un bâtiment", "§7pour le supprimer.", "§c§lDéfinitif.")
                            : List.of("§7Rangés par type : habitations,", "§7ateliers, mairie, divers.", "",
                            "§eClic gauche §7: bordures 5 secondes", "§eClic droit §7: changer le type",
                            "§eMaj + clic §7: inspection détaillée", "",
                            "§6■ §7habité par un artisan  §c■ §7incomplet"));
            close(c);

            List<Building> list = buildings.getAllBuildings();
            for (int i = 0; i < Math.min(GRID, list.size()); i++) {
                Building b = list.get(i);
                int col = i % 9, row = 1 + i / 9;
                // Inspection (phase 2) : détail si la zone est chargée, sinon le dernier niveau connu
                com.citycore.building.Inspection insp = inspector == null || deleting ? null : inspector.peek(b);
                boolean incomplete = insp != null ? !insp.compliant() : b.level() == 0;
                if (deleting || incomplete) c.sprite("ring_red", sx(col), sy(row));
                else if (b.npcTag() != null) c.sprite("ring_gold", sx(col), sy(row));
                List<String> lore = new ArrayList<>(List.of(
                        b.typeLine(),
                        "§7Zone  : §f" + b.x1() + "," + b.z1() + " §7→ §f" + b.x2() + "," + b.z2(),
                        b.heightLine(),
                        "§7NPC   : " + buildingGUI().occupant(b)));
                String residents = occupancy.apply(b);
                if (residents != null) lore.add(residents);
                lore.add("");
                if (insp != null) {
                    lore.addAll(insp.lore());
                    lore.add("");
                } else if (!deleting) {
                    lore.add(b.levelLine());
                    lore.add("");
                }
                if (deleting) {
                    lore.add("§cCliquez pour supprimer");
                } else {
                    lore.add("§eClic gauche §7: voir les bordures");
                    if (screens != null) lore.add("§eClic droit §7: changer le type");
                    lore.add("§eMaj + clic §7: inspection détaillée");
                }
                item(col, row, UiItems.shown(deleting ? Material.TNT : b.type().icon,
                                (deleting ? "§c" : "§e") + b.name(), lore, false),
                        t -> {
                            if (deleting) buildingGUI().delete(player, b);
                            else if (t.isShiftClick()) buildingGUI().inspect(player, b);
                            else if (t.isRightClick() && screens != null) screens.openRetype(player, b, () -> openBuildings(player));
                            else buildingGUI().showBorders(player, b);
                        });
            }

            if (deleting) {
                c.sprite("fb_retour", sx(0), sy(5));
                zone(0, 5, 4, 1, "§7◀ Retour", List.of("§7Retourner à la liste"), t -> buildingGUI().open(player));
            } else {
                c.sprite("fb_ajouter", sx(0), sy(5));
                zone(0, 5, 4, 1, "§a➕ Ajouter un bâtiment", List.of("§7Choisissez le type, donnez un nom,",
                        "§7puis délimitez la zone avec le bâton.", "",
                        "§eCliquez pour choisir le type"),
                        t -> buildingGUI().promptAdd(player));
                c.sprite("fb_supprimer", sx(5), sy(5));
                zone(5, 5, 4, 1, "§c➖ Supprimer un bâtiment", List.of("§7Sélectionnez un bâtiment à retirer"),
                        t -> buildingGUI().openDelete(player));
            }
        }
    }
}
