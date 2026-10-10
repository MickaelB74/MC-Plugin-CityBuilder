package com.citycore.ui.screens;

import com.citycore.building.Building;
import com.citycore.building.BuildingManager;
import com.citycore.city.City;
import com.citycore.city.CityManager;
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
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

import static com.citycore.ui.UiLayout.*;

/**
 * Écrans illustrés d'Alderic : accueil de la mairie, quêtes de la cité et charte, bâtiments,
 * trésor. Les boutons appellent les actions des menus classiques du maire.
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
            kit.portraits().draw(c, CityNPC.MAYOR, false);
            List<String> lore = new ArrayList<>();
            for (String line : CityNPC.MAYOR.backstory) lore.add("§8§o" + line);
            zone(0, 1, 4, 4, CityNPC.MAYOR.displayName + " §8— §7Maire", lore, null);
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
            portrait(c);

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

            bar(c, (double) claimed / max, claimed >= max, "PARCELLES " + claimed + " / " + data.getMaxChunks());
            zone(0, 5, 9, 1, "§eParcelles de la ville",
                    List.of("§7Revendiquées : §f" + claimed, "§7Emplacements : §f" + data.getMaxChunks(),
                            "§8Agrandissez pour revendiquer davantage."), null);
        }
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
                            : List.of("§7Cliquez sur un bâtiment pour", "§7voir ses bordures 5 secondes.", "",
                            "§6■ §7habité par un artisan"));
            close(c);

            List<Building> list = buildings.getAllBuildings();
            for (int i = 0; i < Math.min(GRID, list.size()); i++) {
                Building b = list.get(i);
                int col = i % 9, row = 1 + i / 9;
                if (deleting) c.sprite("ring_red", sx(col), sy(row));
                else if (b.npcTag() != null) c.sprite("ring_gold", sx(col), sy(row));
                List<String> lore = new ArrayList<>(List.of(
                        "§7Monde : §f" + b.world(),
                        "§7Zone  : §f" + b.x1() + "," + b.z1() + " §7→ §f" + b.x2() + "," + b.z2(),
                        "§7NPC   : " + buildingGUI().occupant(b), ""));
                lore.add(deleting ? "§cCliquez pour supprimer" : "§eCliquez pour voir les bordures");
                item(col, row, UiItems.shown(deleting ? Material.TNT : Material.BRICKS,
                                (deleting ? "§c" : "§e") + b.name(), lore, false),
                        t -> { if (deleting) buildingGUI().delete(player, b); else buildingGUI().showBorders(player, b); });
            }

            if (deleting) {
                c.sprite("fb_retour", sx(0), sy(5));
                zone(0, 5, 4, 1, "§7◀ Retour", List.of("§7Retourner à la liste"), t -> buildingGUI().open(player));
            } else {
                c.sprite("fb_ajouter", sx(0), sy(5));
                zone(0, 5, 4, 1, "§a➕ Ajouter un bâtiment", List.of("§7Entrez le nom du bâtiment",
                        "§7puis sélectionnez la zone avec WorldEdit.", "", "§eCliquez pour saisir le nom"),
                        t -> buildingGUI().promptAdd(player));
                c.sprite("fb_supprimer", sx(5), sy(5));
                zone(5, 5, 4, 1, "§c➖ Supprimer un bâtiment", List.of("§7Sélectionnez un bâtiment à retirer"),
                        t -> buildingGUI().openDelete(player));
            }
        }
    }
}
