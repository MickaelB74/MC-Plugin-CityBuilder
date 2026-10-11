package com.citycore.ui.screens;

import com.citycore.building.Building;
import com.citycore.building.BuildingType;
import com.citycore.economy.ItemTags;
import com.citycore.economy.MarketService;
import com.citycore.inhabitant.GuardConfig;
import com.citycore.inhabitant.InhabitantManager;
import com.citycore.inhabitant.InhabitantManager.Inhabitant;
import com.citycore.npc.CityNPC;
import com.citycore.npc.NPCDataManager;
import com.citycore.npc.NPCManager;
import com.citycore.npc.NPCState;
import com.citycore.npc.villager.VillagerConfig;
import com.citycore.npc.villager.VillagerConfig.SellPrice;
import com.citycore.npc.villager.VillagerConfig.ShopItem;
import com.citycore.npc.villager.VillagerGUI;
import com.citycore.npc.villager.VillagerListener;
import com.citycore.player.PlayerDataManager;
import com.citycore.quest.QuestGUI;
import com.citycore.ui.UiCanvas;
import com.citycore.ui.UiItems;
import com.citycore.ui.UiKit;
import com.citycore.util.TypewriterUtil;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static com.citycore.ui.UiLayout.*;

/**
 * Écrans illustrés d'un artisan : accueil (selon son état de recrutement), choix du logis,
 * vente, entrepôt et boutique. Toute la logique (prix, stocks, XP, messages) reste celle des
 * menus classiques : les boutons appellent les mêmes actions de {@link VillagerListener}.
 */
public final class ArtisanScreens {

    private final UiKit             kit;
    private final CityNPC           npc;
    private final VillagerGUI       gui;
    private final VillagerConfig    config;
    private final NPCDataManager    data;
    private final NPCManager        npcManager;
    private final PlayerDataManager players;
    private final MarketService     market;
    private final VillagerListener  actions;
    private final QuestGUI          questGUI;
    private InhabitantManager       inhabitants; // garde (phase 5) : menu « Actions » de Roland

    public ArtisanScreens(UiKit kit, CityNPC npc, VillagerGUI gui, NPCDataManager data, NPCManager npcManager,
                          PlayerDataManager players, MarketService market, VillagerListener actions,
                          QuestGUI questGUI) {
        this.kit        = kit;
        this.npc        = npc;
        this.gui        = gui;
        this.config     = gui.getConfig();
        this.data       = data;
        this.npcManager = npcManager;
        this.players    = players;
        this.market     = market;
        this.actions    = actions;
        this.questGUI   = questGUI;
    }

    /** Habitants et garde de la ville (écran « La garde » chez Roland). */
    public void setInhabitants(InhabitantManager inhabitants) {
        this.inhabitants = inhabitants;
    }

    /* =========================
       POINTS D'ENTRÉE (false = menu classique)
       ========================= */

    public boolean openMain(Player p) {
        if (!kit.active(p)) return false;
        return kit.show(switch (data.getState(npc)) {
            case WANDERER -> new Wanderer(p);
            // Sans logis (bâtiment supprimé) : même écran qu'à l'arrivée, la gestion attend un atelier
            case ARRIVED, HOMELESS -> new Arrived(p);
            case ASSIGNED -> new Main(p);
        });
    }

    public boolean openBuildings(Player p, List<Building> free) {
        return kit.active(p) && kit.show(new Buildings(p, free, currentPage(p, Buildings.class)));
    }

    public boolean openSell(Player p)  { return kit.active(p) && kit.show(new Sell(p, currentPage(p, Sell.class))); }

    public boolean openStock(Player p) { return kit.active(p) && kit.show(new Stock(p, currentPage(p, Stock.class))); }

    public boolean openShop(Player p)  { return kit.active(p) && kit.show(new Shop(p, currentPage(p, Shop.class))); }

    /** Écran à plusieurs pages. */
    private interface Paged {
        int page();
    }

    /**
     * Page à rouvrir : celle déjà affichée si le joueur regarde ce même écran (rafraîchissement
     * après une vente ou un achat), sinon la première.
     */
    private int currentPage(Player p, Class<?> screen) {
        Object holder = p.getOpenInventory().getTopInventory().getHolder();
        if (screen.isInstance(holder) && holder instanceof Paged paged
                && ((ArtisanMenu) holder).owner() == this) return paged.page();
        return 0;
    }

    /* =========================
       BASE COMMUNE
       ========================= */

    private abstract class ArtisanMenu extends ValmontMenu {

        ArtisanMenu(Player p) {
            super(ArtisanScreens.this.kit, p);
        }

        void title(UiCanvas c) {
            nameplate(c, plain(npc.displayName), npc.function);
        }

        /** Fin de conversation : le PNJ reprend sa balade ou rejoint son point de travail. */
        @Override
        protected void onClose() {
            if (data.getState(npc) == NPCState.WANDERER) return;
            NPC citizen = npcManager.getNPC(npc);
            if (citizen == null || !citizen.isSpawned()) return;
            Bukkit.getScheduler().runTaskLater(kit.plugin(), () -> {
                // Toujours dans un menu de ce PNJ : la conversation continue
                if (player.isOnline() && player.getOpenInventory().getTopInventory().getHolder() instanceof ArtisanMenu m
                        && m.owner() == ArtisanScreens.this) return;
                citizen.getNavigator().setPaused(false);
                citizen.getNavigator().cancelNavigation();
            }, 2L);
        }

        ArtisanScreens owner() {
            return ArtisanScreens.this;
        }

        void portrait(UiCanvas c, boolean wanderer) {
            kit.portraits().draw(c, npc, wanderer);
            zone(0, 1, 4, 4, npc.displayName + " §8— §7" + npc.function, gui.backstoryLore(), null);
        }

        void bigButton(UiCanvas c, int row, String sprite, String name, List<String> lore, Consumer<ClickType> action) {
            c.sprite(sprite, sx(4), sy(row));
            zone(4, row, 5, 1, name, lore, action);
        }

        void followIcon(UiCanvas c, int col) {
            boolean following = npcManager.isFollowing(player, npc);
            c.sprite(following ? "ic_follow_stop" : "ic_follow", sx(col), sy(0));
            zone(col, 0, 1, 1, following ? "§c⛔ Arrêter de suivre" : "§a👣 Demander de suivre",
                    List.of(following ? "§7" + npc.displayName + " §7restera ici."
                            : "§7" + npc.displayName + " §7vous accompagnera."),
                    t -> actions.toggleFollow(player));
        }

        void storyIcon(UiCanvas c, int col) {
            c.sprite("ic_story", sx(col), sy(0));
            zone(col, 0, 1, 1, "§6📖 Son histoire", storyLore(), t -> tellStory(player));
        }

        List<String> storyLore() {
            List<String> lore = new ArrayList<>(gui.backstoryLore());
            lore.add("");
            lore.add("§eCliquez pour l'écouter");
            return lore;
        }

        /** Grille : ligne et colonne de la i-ème case (rangées 1 à 4). */
        int col(int i) { return i % 9; }

        int row(int i) { return 1 + i / 9; }
    }

    private void tellStory(Player p) {
        p.closeInventory();
        List<String> lines = npc.getDialogue("first_meeting");
        if (!lines.isEmpty()) TypewriterUtil.play(kit.plugin(), p, lines, null);
    }

    /* =========================
       ACCUEIL — PNJ INSTALLÉ
       ========================= */

    private final class Main extends ArtisanMenu {

        Main(Player p) { super(p); }

        @Override
        protected void draw(UiCanvas c) {
            c.background("bg_character");
            title(c);

            int level = data.getLevel(npc);
            int xp    = data.getXP(npc);
            int next  = gui.getXpForNextLevel(level);
            String levelName = plain(VillagerGUI.getLevelName(level));
            c.text(levelName, STATE_X, rowText(0), GOLD, true);
            c.sprite("stars_" + Math.max(0, Math.min(5, level)), STARS_X, STARS_Y);
            List<String> levelLore = new ArrayList<>();
            levelLore.add("§7XP : §f" + xp + (next > 0 ? " §7/ §f" + next : " §7(max)"));
            levelLore.add("");
            levelLore.addAll(gui.backstoryLore());
            zone(0, 0, 6, 1, "§6" + levelName + " §7— rang " + level + " / 5", levelLore, null);

            jobIcon(c);
            followIcon(c, 7);
            close(c);
            // Portrait : son histoire, l'état de son atelier, et un clic ouvre la gestion du bâtiment
            kit.portraits().draw(c, npc, false);
            List<String> portraitLore = new ArrayList<>(gui.backstoryLore());
            if (actions.inspector() != null) {
                portraitLore.add("");
                portraitLore.addAll(actions.inspector().atelierLore(npc));
            }
            portraitLore.add("");
            portraitLore.add("§eCliquez : gestion §7(logis, actions)");
            zone(0, 1, 4, 4, npc.displayName + " §8— §7" + npc.function, portraitLore,
                    t -> kit.show(new Manage(player)));

            bigButton(c, 1, "btn_vendre", "§a💰 Vendre",
                    List.of("§7Vendez vos ressources.", "§7Le prix baisse quand son", "§7entrepôt se remplit."),
                    t -> gui.openSell(player));
            bigButton(c, 2, "btn_entrepot", "§6📦 Entrepôt",
                    List.of("§7Réserves de " + npc.displayName + "§7.", "§7Rachetez-lui ses matières",
                            "§7premières (non revendables)."),
                    t -> gui.openInventory(player));
            bigButton(c, 3, "btn_boutique", "§b🛒 Boutique",
                    List.of("§7Ses fabrications et raretés.", "§7Produites à partir des stocks",
                            "§7de la ville.", "", "§7Niveau actuel : " + VillagerGUI.getLevelName(level)),
                    t -> gui.openShop(player));
            bigButton(c, 4, "btn_quetes", "§d📜 Quêtes",
                    List.of("§7Quêtes principales et spéciales", "§7de " + npc.displayName + "§7."),
                    t -> questGUI.open(player));

            // Barre d'expérience de l'atelier
            int floor = config.getXpThresholds().getOrDefault(level, 0);
            boolean max = next <= 0;
            double fraction = max ? 1 : (double) (xp - floor) / Math.max(1, next - floor);
            bar(c, fraction, max, max ? "NIVEAU MAXIMUM" : fmt(xp) + " / " + fmt(next) + " XP");
            zone(0, 5, 9, 1, "§bExpérience de l'atelier",
                    List.of("§7Ventes, achats et quêtes", "§7font progresser " + npc.displayName + "§7.",
                            "", "§7XP : §f" + xp + (max ? " §7(max)" : " §7/ §f" + next)), null);
        }

        void jobIcon(UiCanvas c) {
            CityNPC current = players.getJob(player.getUniqueId());
            String sprite = current == npc ? "ic_job_quit" : current != null ? "ic_job_locked" : "ic_job";
            c.sprite(sprite, sx(6), sy(0));
            ItemMeta tip = gui.makeJobButton(player).getItemMeta();
            zone(6, 0, 1, 1, tip.getDisplayName(), tip.getLore(), t -> actions.toggleJob(player));
        }
    }

    /* =========================
       SON ATELIER — inspection détaillée (phase 2)
       Rangée 1 : exigences ✔/✘ ; rangée 2 : bonus du métier ; rangée 3 : points de détail ;
       pied de page : ce qu'il faut pour le niveau suivant. Les blocs sont relus à l'ouverture.
       ========================= */

    private final class AtelierView extends ArtisanMenu {

        AtelierView(Player p) { super(p); }

        @Override
        protected void draw(UiCanvas c) {
            com.citycore.building.BuildingInspector inspector = actions.inspector();
            com.citycore.building.Inspection insp = inspector == null ? null : inspector.freshAtelierInspection(npc);

            c.background("bg_grid");
            title(c);
            back(c, 0, "Gestion", () -> kit.show(new Manage(player)));
            c.sprite("rib_atelier", sx(1), sy(0));
            close(c);
            pager(c, 0, 1, p -> { });

            if (insp == null) {
                info(c, 7, "§e🏠 Son atelier", List.of("§7Aucun atelier : choisissez-lui", "§7un logis."));
                footer(c, false, "AUCUN ATELIER");
                return;
            }
            List<String> help = new ArrayList<>(List.of("§f" + insp.building().name() + " §8— " + insp.levelLabel(), "",
                    "§7Rangée 1 : §fce qu'il doit contenir", "§7Rangée 2 : §fbonus du métier §8(+points)",
                    "§7Rangée 3 : §fpoints de détail", "",
                    "§7Chaque niveau au-delà du 1er agrandit", "§7son entrepôt de §f+"
                            + Math.round(inspector.requirements().storageBonus() * 100) + " %§7."));
            info(c, 7, "§e🏠 Son atelier", help);

            // Rangée 1 : exigences
            List<com.citycore.building.Inspection.Check> checks = insp.checks();
            for (int i = 0; i < Math.min(9, checks.size()); i++) {
                com.citycore.building.Inspection.Check ch = checks.get(i);
                c.sprite(ch.ok() ? "ring_green" : "ring_red", sx(i), sy(1));
                item(i, 1, UiItems.shown(ch.icon(), (ch.ok() ? "§a✔ " : "§c✘ ") + "§f" + ch.label(),
                        List.of("§7" + ch.detail(), ch.ok() ? "§aFait" : "§cÀ ajouter dans l'atelier"), false), null);
            }

            // Rangée 2 : bonus du métier
            List<com.citycore.building.Inspection.Check> bonus = insp.bonusChecks();
            int perBonus = inspector.requirements().points().bonus();
            for (int i = 0; i < Math.min(9, bonus.size()); i++) {
                com.citycore.building.Inspection.Check ch = bonus.get(i);
                c.sprite(ch.ok() ? "ring_green" : "ring_gray", sx(i), sy(2));
                item(i, 2, UiItems.shown(ch.icon(), (ch.ok() ? "§a★ " : "§7☆ ") + "§f" + ch.label(),
                        List.of("§7Bonus du métier : §f+" + perBonus + " pts",
                                ch.ok() ? "§aPrésent" : "§7Ajoutez-en un dans l'atelier"), ch.ok()), null);
            }

            // Rangée 3 : points de detail
            com.citycore.building.BuildingRequirements.Points pts = inspector.requirements().points();
            pointTile(c, 0, Material.BRICKS, "§fVariété", insp.variety(), pts.varietyMax(),
                    List.of("§7+" + pts.variety() + " pt par type de bloc de construction", "§7différent (terre, pierre brute, eau",
                            "§7et feuillage ne comptent pas)."));
            pointTile(c, 1, Material.FLOWER_POT, "§fDécor", insp.decorations(), pts.decorationMax(),
                    List.of("§7Pots de fleurs, tapis, bannières, bougies,", "§7panneaux, trappes, lanternes, chaînes,",
                            "§7bibliothèques, poteries ; cadres, tableaux,", "§7porte-armures. §f" + pts.decorationPerType()
                                    + " §7du même type au plus."));
            pointTile(c, 2, Material.EMERALD, "§fBonus du métier", insp.bonus(), perBonus * bonus.size(),
                    List.of("§7Blocs de la rangée du dessus :", "§7+" + perBonus + " pts chacun."));
            pointTile(c, 3, Material.OAK_PLANKS, "§fSurface couverte", insp.surface(), pts.surfaceMax(),
                    List.of("§7+" + pts.surface() + " pt par 10 blocs de sol", "§7sous un toit."));
            List<String> totalLore = new ArrayList<>(List.of("§7Total : §f" + insp.points() + " pts", ""));
            for (var e : inspector.requirements().levels().entrySet()) {
                totalLore.add((insp.level() >= e.getKey() ? "§a✔ " : "§8○ ") + "§7Niveau " + e.getKey() + " : §f"
                        + e.getValue() + " pts");
            }
            if (!insp.compliant()) totalLore.add("§cExigences de la rangée 1 d'abord.");
            c.sprite("ring_gold", sx(5), sy(3));
            item(5, 3, UiItems.shown(Material.NETHER_STAR, "§6" + insp.points() + " points", totalLore, false), null);

            // Pied de page : la prochaine étape
            String next = !insp.compliant() ? "INCOMPLET : " + insp.checks().stream().filter(ch -> !ch.ok()).count() + " À AJOUTER"
                    : insp.nextLevelPoints() < 0 ? "NIVEAU MAXIMAL ★★★★★"
                    : "NIV. " + insp.level() + " → " + (insp.level() + 1) + " : +" + insp.nextLevelPoints() + " PTS";
            footer(c, false, next);
            zone(1, 5, 7, 1, insp.levelLabel(), insp.lore(), null);
        }

        /** Case de points : icône, valeur sur le maximum (anneau vert au maximum). */
        private void pointTile(UiCanvas c, int col, Material icon, String name, int value, int max, List<String> help) {
            c.sprite(value >= max && max > 0 ? "ring_green" : "ring_amber", sx(col), sy(3));
            List<String> lore = new ArrayList<>(List.of("§f" + value + " §7/ §f" + max + " pts", ""));
            lore.addAll(help);
            item(col, 3, UiItems.shown(icon, name, lore, false), null);
        }
    }

    /* =========================
       GESTION DU BÂTIMENT — variante de l'accueil (clic sur le portrait)
       Le menu de droite ne vend plus : Logis (son atelier), Actions (la garde chez Roland),
       Son histoire. Un clic sur le portrait revient à l'accueil.
       ========================= */

    private final class Manage extends ArtisanMenu {

        Manage(Player p) { super(p); }

        @Override
        protected void draw(UiCanvas c) {
            c.background("bg_character_gestion");
            title(c);
            c.text("GESTION", STATE_X, rowText(0), CREAM, true);
            zone(0, 0, 6, 1, "§e⚙ Gestion du bâtiment", List.of("§7Son atelier et ses actions.",
                    "§7Cliquez sur le portrait pour revenir", "§7à son commerce."), null);
            followIcon(c, 7);
            close(c);

            kit.portraits().draw(c, npc, false);
            zone(0, 1, 4, 4, npc.displayName + " §8— §7" + npc.function,
                    List.of("§eCliquez : retour au commerce §7(vente, entrepôt…)"), t -> gui.open(player));

            com.citycore.building.BuildingInspector inspector = actions.inspector();
            List<String> logisLore = new ArrayList<>();
            if (inspector != null) logisLore.addAll(inspector.atelierLore(npc));
            logisLore.addAll(List.of("", "§7Exigences, bonus du métier et", "§7points de détail de son atelier.",
                    "", "§eCliquez : ce qu'il faut pour monter de niveau"));
            bigButton(c, 1, "btn_logis", "§e🏠 Logis", logisLore, t -> kit.show(new AtelierView(player)));

            List<String> actionLore = new ArrayList<>();
            if (npc == CityNPC.GUARD && inhabitants != null) {
                int rank = inhabitants.guardRank();
                actionLore.addAll(List.of("§7Former des habitants de la ville", "§7à la garde (soldat, archer…).", "",
                        "§7Garde : §f" + inhabitants.guardSize() + " §7/ §f" + inhabitants.guard().maxGuards(rank),
                        "§7Formations : §f" + inhabitants.trainees().size() + " §7/ §f" + inhabitants.guard().maxTraining(rank)));
            } else {
                actionLore.add("§7Aucune action pour l'instant.");
            }
            bigButton(c, 2, "btn_actions", "§c🛡 Actions", actionLore, t -> kit.show(new ActionsView(player)));
            bigButton(c, 3, "btn_histoire", "§6📖 Son histoire", storyLore(), t -> tellStory(player));

            // Barre : progression de l'atelier vers le niveau suivant
            com.citycore.building.Inspection insp = inspector == null ? null : inspector.atelierInspection(npc);
            if (insp == null) {
                bar(c, 0, false, "AUCUN ATELIER");
            } else if (!insp.compliant()) {
                bar(c, 0, false, "ATELIER INCOMPLET");
            } else if (insp.nextLevelPoints() < 0) {
                bar(c, 1, true, "ATELIER NIVEAU MAXIMAL");
            } else {
                double fraction = (double) (insp.points() - insp.levelFloor())
                        / Math.max(1, insp.nextThreshold() - insp.levelFloor());
                bar(c, fraction, false, "ATELIER : " + insp.points() + " / " + insp.nextThreshold() + " PTS");
            }
            zone(0, 5, 9, 1, "§bProgression de l'atelier",
                    insp == null ? List.of("§7Aucun atelier.") : insp.lore(), null);
        }
    }

    /* =========================
       ACTIONS — ce que le personnage peut faire pour la ville
       ========================= */

    private final class ActionsView extends ArtisanMenu {

        ActionsView(Player p) { super(p); }

        @Override
        protected void draw(UiCanvas c) {
            c.background("bg_grid");
            title(c);
            back(c, 0, "Gestion", () -> kit.show(new Manage(player)));
            c.sprite("rib_actions", sx(1), sy(0));
            info(c, 7, "§c🛡 Actions de " + plain(npc.displayName),
                    List.of("§7Services que ce personnage rend", "§7à toute la ville (communs à tous", "§7les joueurs)."));
            close(c);

            if (npc != CityNPC.GUARD || inhabitants == null) {
                footer(c, false, "AUCUNE ACTION POUR L'INSTANT");
                return;
            }
            int rank = inhabitants.guardRank();
            GuardConfig guard = inhabitants.guard();
            c.sprite("ring_purple", sx(0), sy(1));
            item(0, 1, UiItems.shown(Material.SHIELD, "§9🛡 La garde", List.of(
                    "§7Choisissez parmi les habitants qui", "§7former, et à quel rôle.", "",
                    "§7Garde : §f" + inhabitants.guardSize() + " §7/ §f" + guard.maxGuards(rank),
                    "§7Formations : §f" + inhabitants.trainees().size() + " §7/ §f" + guard.maxTraining(rank),
                    "", "§eCliquez pour ouvrir"), false), t -> kit.show(new GuardView(player, 0)));
            footer(c, false, guardFooter());
        }
    }

    /** « GARDE 3/4 · FORMATION 1/1 ». */
    private String guardFooter() {
        int rank = inhabitants.guardRank();
        return "GARDE " + inhabitants.guardSize() + "/" + inhabitants.guard().maxGuards(rank)
                + " · FORMATION " + inhabitants.trainees().size() + "/" + inhabitants.guard().maxTraining(rank);
    }

    /** Tête de l'habitant (skin, ou uniforme pour un garde). */
    private ItemStack inhabitantHead(Inhabitant inh, String name, List<String> lore) {
        String key = inh.state == InhabitantManager.State.SOLDAT ? "soldats_" + inh.uniform : "habitants_" + inh.skin;
        return UiItems.head(inhabitants.skinTexture(inh), key, name, lore);
    }

    /* =========================
       LA GARDE — liste commune des gardes, recrues et habitants (chez Roland)
       Violet : garde ; ambre : en formation ; vert : habitant à former ; gris : plus de place.
       ========================= */

    private final class GuardView extends ArtisanMenu implements Paged {

        private final int page;

        @Override
        public int page() { return page; }

        GuardView(Player p, int page) {
            super(p);
            this.page = page;
        }

        @Override
        protected void draw(UiCanvas c) {
            c.background("bg_grid");
            title(c);
            back(c, 0, "Actions", () -> kit.show(new ActionsView(player)));
            c.sprite("rib_garde", sx(1), sy(0));
            int rank = inhabitants.guardRank();
            GuardConfig guard = inhabitants.guard();
            info(c, 7, "§9🛡 La garde de Valmont", List.of(
                    "§7Cliquez sur un habitant pour le", "§7former, puis choisissez son rôle.",
                    "§7Il s'entraîne chez Roland (temps", "§7compté quand un joueur est en ligne).", "",
                    "§7Les gardes patrouillent dans la", "§7ville, jour et nuit, hors des", "§7bâtiments.", "",
                    "§7Rang " + rank + " de Roland : §f" + guard.maxGuards(rank) + " §7gardes, §f"
                            + guard.maxTraining(rank) + " §7formation(s) à la fois.", "",
                    "§5■ §7garde  §6■ §7en formation  §a■ §7à former"));
            close(c);

            List<Inhabitant> roster = inhabitants.guardRoster();
            String blocked = inhabitants.canRecruit();
            int pg = clampPage(page, roster.size());
            for (int i = 0; i < GRID && pg * GRID + i < roster.size(); i++) {
                Inhabitant inh = roster.get(pg * GRID + i);
                GuardConfig.Role role = guard.role(inh.role);
                List<String> lore = new ArrayList<>();
                String ring;
                Consumer<ClickType> click;
                switch (inh.state) {
                    case SOLDAT -> {
                        ring = "ring_purple";
                        lore.add("§9" + (role != null ? role.name() : "Garde") + " §7de la garde");
                        String hp = inhabitants.healthLine(inh);
                        if (hp != null) lore.add(hp);
                        lore.add("§7Lit : §f" + inhabitants.homeName(inh));
                        lore.addAll(List.of("", "§cMaj + clic droit §7: le rendre", "§7à la vie civile"));
                        click = t -> dismiss(t, inh);
                    }
                    case FORMATION -> {
                        ring = "ring_amber";
                        lore.add("§6Recrue §7: " + (role != null ? role.name() : "?"));
                        lore.add("§7Encore §f" + InhabitantManager.minutes(inh.trainingLeft) + " §7de formation");
                        if (rank == 0) lore.add("§c⏸ En pause : Roland n'a pas d'atelier");
                        lore.add("§7Lit : §f" + inhabitants.homeName(inh));
                        lore.addAll(List.of("", "§cMaj + clic droit §7: arrêter", "§7la formation"));
                        click = t -> dismiss(t, inh);
                    }
                    default -> {
                        ring = blocked == null ? "ring_green" : "ring_gray";
                        lore.add("§7Habitant de §f" + inhabitants.homeName(inh));
                        lore.add("");
                        lore.add(blocked == null ? "§eCliquez : choisir son rôle" : "§8" + blocked);
                        click = t -> {
                            String why = inhabitants.canRecruit();
                            if (why != null) player.sendMessage("§c❌ " + why);
                            else kit.show(new RoleView(player, inh.id));
                        };
                    }
                }
                c.sprite(ring, sx(col(i)), sy(row(i)));
                item(col(i), row(i), inhabitantHead(inh, "§f" + inh.name, lore), click);
            }
            if (roster.isEmpty()) {
                info(c, 6, "§7Personne à former", List.of("§7Aucun habitant en ville : il en", "§7arrive un par lit d'habitation."));
            }
            pager(c, pg, pages(roster.size()), p -> kit.show(new GuardView(player, p)));
            footer(c, false, guardFooter());
        }

        /** Maj + clic droit : le garde (ou la recrue) redevient habitant. */
        private void dismiss(ClickType t, Inhabitant inh) {
            if (t != ClickType.SHIFT_RIGHT) return;
            String err = inhabitants.dismiss(inh.id);
            if (err != null) player.sendMessage("§c❌ " + err);
            kit.show(new GuardView(player, page));
        }
    }

    /* =========================
       RÔLE — choix du rôle d'une recrue (verrouillé selon le rang de Roland)
       ========================= */

    private final class RoleView extends ArtisanMenu {

        private final int inhabitantId;

        RoleView(Player p, int inhabitantId) {
            super(p);
            this.inhabitantId = inhabitantId;
        }

        @Override
        protected void draw(UiCanvas c) {
            Inhabitant inh = inhabitants.get(inhabitantId);
            c.background("bg_grid");
            title(c);
            back(c, 0, "La garde", () -> kit.show(new GuardView(player, 0)));
            c.sprite("rib_role", sx(1), sy(0));
            close(c);
            if (inh == null || inh.state != InhabitantManager.State.HABITANT) {
                footer(c, false, "HABITANT INDISPONIBLE");
                return;
            }
            item(7, 0, inhabitantHead(inh, "§f" + inh.name, List.of("§7Habitant de §f" + inhabitants.homeName(inh),
                    "", "§7Choisissez le rôle qu'il apprendra", "§7chez Roland.")), null);

            int rank = inhabitants.guardRank();
            List<GuardConfig.Role> roles = inhabitants.guard().roles();
            for (int i = 0; i < Math.min(GRID, roles.size()); i++) {
                GuardConfig.Role role = roles.get(i);
                List<String> lore = new ArrayList<>(List.of(
                        "§7Combat : §f" + (role.ranged() ? "à distance (arc)" : "au contact"),
                        "§7Vie : §f" + fmtHealth(role.health()),
                        "§7Formation : §f" + InhabitantManager.minutes(role.trainingMs()), "§7Équipement :"));
                lore.addAll(equipment(role));
                lore.add("");
                if (role.rank() > rank) {
                    lore.add("§c🔒 Roland doit être au rang " + role.rank());
                    c.sprite("ring_gray", sx(col(i)), sy(row(i)));
                    c.sprite("lock", sx(col(i)) + 3, sy(row(i)) + 3);
                    item(col(i), row(i), UiItems.blank("§8🔒 " + role.name(), lore), null);
                    continue;
                }
                lore.add("§eCliquez : former " + inh.name);
                c.sprite("ring_green", sx(col(i)), sy(row(i)));
                item(col(i), row(i), UiItems.shown(role.weapon() != null ? role.weapon() : Material.IRON_SWORD,
                        "§f" + role.name(), lore, false), t -> {
                    String err = inhabitants.train(inhabitantId, role.key());
                    if (err != null) {
                        player.sendMessage("§c❌ " + err);
                        kit.show(new RoleView(player, inhabitantId));
                    } else {
                        kit.show(new GuardView(player, 0));
                    }
                });
            }
            footer(c, false, "FORMER : " + inh.name.toUpperCase());
        }

        /** « 15 ❤ » (2 points de vie par cœur). */
        private String fmtHealth(double health) {
            double hearts = health / 2;
            return (hearts == Math.floor(hearts) ? String.valueOf((int) hearts) : String.valueOf(hearts)) + " ❤";
        }

        /** Une ligne par objet du rôle (arme, puis armure). */
        private List<String> equipment(GuardConfig.Role role) {
            List<String> lines = new ArrayList<>();
            for (Material m : new Material[]{role.weapon(), role.helmet(), role.chest(), role.legs(), role.boots()}) {
                if (m != null) lines.add("§8• §f" + VillagerGUI.formatName(m));
            }
            return lines.isEmpty() ? List.of("§8• §7aucun") : lines;
        }
    }

    /* =========================
       ACCUEIL — PNJ RENCONTRÉ HORS DE LA VILLE
       ========================= */

    private final class Wanderer extends ArtisanMenu {

        Wanderer(Player p) { super(p); }

        @Override
        protected void draw(UiCanvas c) {
            c.background("bg_character");
            title(c);
            c.text("ÉTRANGER", STATE_X, rowText(0), CREAM, true);
            zone(0, 0, 6, 1, "§7" + plain(npc.displayName) + " — §f" + npc.function,
                    List.of("§7Ce personnage n'a pas encore", "§7rejoint votre ville.", "",
                            "§8Invitez-le à vous suivre."), null);
            storyIcon(c, 6);
            followIcon(c, 7);
            close(c);
            portrait(c, true);

            boolean following = npcManager.isFollowing(player, npc);
            bigButton(c, 1, following ? "btn_arreter" : "btn_suivre",
                    following ? "§c⛔ Attendez ici" : "§a👣 Suivez-moi",
                    List.of(following ? "§7Il s'arrête où il est." : "§7Il vous suit jusqu'à la ville.",
                            "§8Il nage, monte dans votre barque", "§8et vous rejoint s'il est distancé."),
                    t -> actions.toggleFollow(player));
            bigButton(c, 2, "btn_histoire", "§6📖 Son histoire", storyLore(), t -> tellStory(player));

            bar(c, 1 / 3.0, false, "MENEZ-LE EN VILLE");
            zone(0, 5, 9, 1, "§eRecrutement", List.of("§a✔ §71. Le rencontrer", "§8○ §72. Le ramener en ville",
                    "§8○ §73. L'installer dans un bâtiment"), null);
        }
    }

    /* =========================
       ACCUEIL — PNJ ARRIVÉ EN VILLE
       ========================= */

    private final class Arrived extends ArtisanMenu {

        Arrived(Player p) { super(p); }

        @Override
        protected void draw(UiCanvas c) {
            // Sans logis : installé autrefois, son bâtiment a été supprimé (tout est conservé, en pause)
            boolean homeless = data.getState(npc) == NPCState.HOMELESS;
            c.background("bg_character");
            title(c);
            if (homeless) {
                c.text("SANS LOGIS", STATE_X, rowText(0), RED, true);
                zone(0, 0, 6, 1, "§c" + plain(npc.displayName) + " — Sans logis",
                        List.of("§7Son atelier a été supprimé.", "§7Choisissez-lui un nouveau logis pour",
                                "§7rouvrir son commerce.", "",
                                "§6⏸ Quêtes en pause §7: rang, stock et", "§7quêtes en cours sont conservés."), null);
            } else {
                c.text("EN VILLE", STATE_X, rowText(0), GREEN, true);
                zone(0, 0, 6, 1, "§a" + plain(npc.displayName) + " — Dans la ville",
                        List.of("§7Ce personnage a rejoint votre ville !", "§7Choisissez-lui un bâtiment pour",
                                "§7ouvrir son commerce."), null);
            }
            storyIcon(c, 6);
            followIcon(c, 7);
            close(c);
            portrait(c, false);

            List<String> logisLore = new ArrayList<>(List.of("§7Ateliers libres de la ville,", "§7ou réservés à ce personnage.",
                    "", "§7Son atelier doit contenir :"));
            if (actions.inspector() != null) logisLore.addAll(com.citycore.building.BuildingRequirements.lore(
                    actions.inspector().requirements().specFor(npc)));
            logisLore.addAll(List.of("", "§eCliquez pour choisir"));
            bigButton(c, 1, "btn_logis", "§e🏠 Choisir son logis", logisLore,
                    t -> gui.openBuildingSearch(player, actions.freeBuildings()));
            boolean wandering = npcManager.isWandering(npc);
            bigButton(c, 2, wandering ? "btn_rester" : "btn_promener",
                    wandering ? "§7🚩 Restez ici" : "§a🚶 Promenez-vous",
                    List.of(wandering ? "§7Il cesse de se balader." : "§7Il se balade dans la ville."),
                    t -> actions.toggleWander(player));
            bigButton(c, 3, "btn_histoire", "§6📖 Son histoire", storyLore(), t -> tellStory(player));

            if (homeless) {
                bar(c, 1, false, "EN PAUSE : NOUVEAU LOGIS");
                zone(0, 5, 9, 1, "§6⏸ En attente d'un logis", List.of("§a✔ §71. Le rencontrer",
                        "§a✔ §72. Le ramener en ville", "§a✔ §73. L'installer §8(atelier supprimé)", "",
                        "§7Ses quêtes reprendront dès qu'il", "§7aura un nouvel atelier."), null);
            } else {
                bar(c, 2 / 3.0, false, "CHOISISSEZ SON LOGIS");
                zone(0, 5, 9, 1, "§eRecrutement", List.of("§a✔ §71. Le rencontrer", "§a✔ §72. Le ramener en ville",
                        "§8○ §73. L'installer dans un bâtiment"), null);
            }
        }
    }

    /* =========================
       CHOIX DU LOGIS
       ========================= */

    private final class Buildings extends ArtisanMenu implements Paged {

        private final List<Building> list;
        private final int page;

        @Override
        public int page() { return page; }

        Buildings(Player p, List<Building> list, int page) {
            super(p);
            this.list = list;
            this.page = clampPage(page, list.size());
        }

        @Override
        protected void draw(UiCanvas c) {
            c.background("bg_grid");
            title(c);
            back(c, 0, "Retour", () -> gui.open(player));
            c.sprite("rib_logis", sx(1), sy(0));
            // Ce que demande ce personnage (phase 2 : inspection des constructions)
            com.citycore.building.BuildingInspector inspector = actions.inspector();
            List<String> help = new ArrayList<>(List.of("§7Son atelier doit contenir :"));
            if (inspector != null) help.addAll(com.citycore.building.BuildingRequirements.lore(
                    inspector.requirements().specFor(npc)));
            help.addAll(List.of("", "§7Ateliers sans habitant, libres ou", "§7réservés à " + plain(npc.displayName)
                            + ". Créez-en un chez Alderic", "§7ou : §f/city build new atelier " + npc.skinId, "",
                    "§a■ §7conforme  §6■ §7réservé à lui  §c■ §7incomplet"));
            info(c, 7, "§e🏠 Atelier de " + plain(npc.displayName), help);
            close(c);

            int start = page * GRID;
            for (int i = 0; i < GRID && start + i < list.size(); i++) {
                Building b = list.get(start + i);
                boolean reserved = npc.tag.equals(b.reservedFor());
                com.citycore.building.Inspection insp = inspector == null ? null : inspector.inspectFor(b, npc);
                boolean ok = insp == null || insp.compliant();
                c.sprite(!ok ? "ring_red" : reserved ? "ring_gold" : "ring_green", sx(col(i)), sy(row(i)));
                List<String> lore = new ArrayList<>();
                lore.add(reserved ? "§6Atelier réservé à " + plain(npc.displayName) : "§aAtelier libre");
                lore.add("§7Zone : §fX(" + b.x1() + "§7→§f" + b.x2() + "§7) Z(§f" + b.z1() + "§7→§f" + b.z2() + "§7)");
                if (insp != null) {
                    lore.add("");
                    lore.addAll(insp.lore());
                }
                lore.add("");
                lore.add(ok ? "§eCliquez pour installer §f" + plain(npc.displayName)
                        : "§cIncomplet : complétez-le d'abord");
                item(col(i), row(i), UiItems.shown(BuildingType.ATELIER.icon, "§e🏛 " + b.name(), lore, reserved && ok),
                        t -> actions.assignBuilding(player, b));
            }
            pager(c, page, pages(list.size()), p -> kit.show(new Buildings(player, actions.freeBuildings(), p)));
            footer(c, false, list.isEmpty() ? "AUCUN ATELIER DISPONIBLE"
                    : list.size() + (list.size() > 1 ? " ATELIERS DISPONIBLES" : " ATELIER DISPONIBLE"));
        }
    }

    /* =========================
       VENTE
       ========================= */

    private final class Sell extends ArtisanMenu implements Paged {

        private final int page;

        @Override
        public int page() { return page; }

        Sell(Player p, int page) {
            super(p);
            this.page = page;
        }

        @Override
        protected void draw(UiCanvas c) {
            c.background("bg_grid");
            title(c);
            back(c, 0, "Retour", () -> gui.open(player));
            c.sprite("rib_vendre", sx(1), sy(0));
            info(c, 7, "§a💰 Vendre à " + npc.displayName, List.of(
                    "§eClic gauche §7: tout vendre", "§eClic droit §7: vendre 1 lot", "",
                    "§a■ §7prix plein   §6■ §7prix réduit (entrepôt garni)",
                    "§c■ §7entrepôt plein   §8■ §7pas assez d'objets", "",
                    "§8Les objets achetés à un PNJ ne se revendent pas."));
            close(c);

            List<Map.Entry<Material, SellPrice>> entries = new ArrayList<>(config.getSellPrices().entrySet());
            int pg = clampPage(page, entries.size());
            boolean employee = market.isEmployee(player, npc);
            for (int i = 0; i < GRID && pg * GRID + i < entries.size(); i++) {
                Material  mat = entries.get(pg * GRID + i).getKey();
                SellPrice sp  = entries.get(pg * GRID + i).getValue();
                int stock = data.getInventoryAmount(npc, mat);
                int owned = ItemTags.countUntagged(player, mat);
                MarketService.SellQuote all = market.quoteSell(npc, mat, owned, Integer.MAX_VALUE, employee);
                boolean full = sp.capacity() > 0 && stock + sp.quantity() > sp.capacity();
                String ring = all.lots() > 0
                        ? (market.demandFactor(stock, sp.capacity()) >= 0.999 ? "ring_green" : "ring_amber")
                        : full ? "ring_red" : "ring_gray";
                c.sprite(ring, sx(col(i)), sy(row(i)));
                item(col(i), row(i), UiItems.shown(mat, "§f" + VillagerGUI.formatName(mat),
                        gui.sellLore(player, mat, sp), false), t -> actions.sell(player, mat, t.isRightClick()));
            }
            pager(c, pg, pages(entries.size()), p -> kit.show(new Sell(player, p)));
            footer(c, true, "BOURSE : " + fmt(balance()));
        }
    }

    /* =========================
       ENTREPÔT
       ========================= */

    private final class Stock extends ArtisanMenu implements Paged {

        private final int page;

        @Override
        public int page() { return page; }

        Stock(Player p, int page) {
            super(p);
            this.page = page;
        }

        @Override
        protected void draw(UiCanvas c) {
            c.background("bg_grid");
            title(c);
            back(c, 0, "Retour", () -> gui.open(player));
            c.sprite("rib_entrepot", sx(1), sy(0));
            info(c, 7, "§6📦 Entrepôt de " + npc.displayName, List.of(
                    "§7Matières premières stockées,", "§7revendues au prix de revente.", "",
                    "§a■ §7lot disponible   §6■ §7stock entamé   §c■ §7vide", "",
                    "§8Objets achetés ici : non revendables."));
            close(c);

            List<Map.Entry<Material, SellPrice>> entries = new ArrayList<>(config.getSellPrices().entrySet());
            int pg = clampPage(page, entries.size());
            for (int i = 0; i < GRID && pg * GRID + i < entries.size(); i++) {
                Material  mat = entries.get(pg * GRID + i).getKey();
                SellPrice sp  = entries.get(pg * GRID + i).getValue();
                int stock = data.getInventoryAmount(npc, mat);
                c.sprite(stock >= sp.quantity() ? "ring_green" : stock > 0 ? "ring_amber" : "ring_red",
                        sx(col(i)), sy(row(i)));
                item(col(i), row(i), UiItems.shown(mat, (stock > 0 ? "§f" : "§8") + VillagerGUI.formatName(mat),
                        gui.stockLore(player, mat, sp), false), t -> actions.buyStock(player, mat));
            }
            pager(c, pg, pages(entries.size()), p -> kit.show(new Stock(player, p)));
            footer(c, true, "BOURSE : " + fmt(balance()));
        }
    }

    /* =========================
       BOUTIQUE
       ========================= */

    private final class Shop extends ArtisanMenu implements Paged {

        private final int page;

        @Override
        public int page() { return page; }

        Shop(Player p, int page) {
            super(p);
            this.page = page;
        }

        @Override
        protected void draw(UiCanvas c) {
            c.background("bg_grid");
            title(c);
            back(c, 0, "Retour", () -> gui.open(player));
            c.sprite("rib_boutique", sx(1), sy(0));
            info(c, 7, "§b🛒 Boutique de " + npc.displayName, List.of(
                    "§7Articles fabriqués à partir", "§7des stocks des artisans.", "",
                    "§a■ §7disponible   §c■ §7rupture   §8🔒 §7rang requis"));
            close(c);

            List<ShopItem> entries = config.getAllShopItems();
            int pg = clampPage(page, entries.size());
            int level = data.getLevel(npc);
            String seller = plain(npc.displayName);
            for (int i = 0; i < GRID && pg * GRID + i < entries.size(); i++) {
                ShopItem item = entries.get(pg * GRID + i);
                List<String> lore = gui.shopLore(player, item);
                if (item.level() > level) {
                    c.sprite("ring_gray", sx(col(i)), sy(row(i)));
                    c.sprite("lock", sx(col(i)) + 3, sy(row(i)) + 3);
                    item(col(i), row(i), UiItems.blank("§8🔒 " + gui.displayName(item), lore),
                            t -> actions.buyShopItem(player, item.key()));
                    continue;
                }
                boolean available = market.availableCount(npc, item) > 0;
                c.sprite(available ? "ring_green" : "ring_red", sx(col(i)), sy(row(i)));
                // Aperçu fidèle de l'article (potion, livre, enchantements), bulle de la boutique
                ItemStack icon = market.createStack(item, seller);
                ItemMeta meta = icon.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName("§f" + gui.displayName(item));
                    meta.setLore(lore);
                    meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
                    icon.setItemMeta(meta);
                }
                icon.setAmount(Math.max(1, Math.min(icon.getMaxStackSize(), item.quantity())));
                item(col(i), row(i), icon, t -> actions.buyShopItem(player, item.key()));
            }
            pager(c, pg, pages(entries.size()), p -> kit.show(new Shop(player, p)));
            footer(c, true, "BOURSE : " + fmt(balance()));
        }
    }
}
