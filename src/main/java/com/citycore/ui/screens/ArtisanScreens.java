package com.citycore.ui.screens;

import com.citycore.building.Building;
import com.citycore.building.BuildingType;
import com.citycore.economy.ItemTags;
import com.citycore.economy.MarketService;
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
            portrait(c, false);

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

            bigButton(c, 1, "btn_logis", "§e🏠 Choisir son logis",
                    List.of("§7Ateliers libres de la ville,", "§7ou réservés à ce personnage.", "",
                            "§eCliquez pour choisir"),
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
            info(c, 7, "§e🏠 Ateliers disponibles", List.of(
                    "§7Ateliers sans habitant, libres",
                    "§7ou réservés à " + plain(npc.displayName) + ".",
                    "§7Créez-en un : §f/city build new",
                    "§fatelier " + npc.skinId + " §7(ou chez Alderic)", "",
                    "§6■ §7réservé à " + plain(npc.displayName) + "   §a■ §7atelier libre"));
            close(c);

            int start = page * GRID;
            for (int i = 0; i < GRID && start + i < list.size(); i++) {
                Building b = list.get(start + i);
                boolean reserved = npc.tag.equals(b.reservedFor());
                c.sprite(reserved ? "ring_gold" : b.hasNpcPoint() ? "ring_green" : "ring_amber", sx(col(i)), sy(row(i)));
                List<String> lore = List.of(
                        reserved ? "§6Atelier réservé à " + plain(npc.displayName) : "§aAtelier libre",
                        "§7Zone : §fX(" + b.x1() + "§7→§f" + b.x2() + "§7) Z(§f" + b.z1() + "§7→§f" + b.z2() + "§7)",
                        b.heightLine(), "",
                        b.hasNpcPoint() ? "§a✔ Point NPC défini" : "§c✘ Aucun point NPC", "",
                        "§eCliquez pour installer §f" + plain(npc.displayName));
                item(col(i), row(i), UiItems.shown(BuildingType.ATELIER.icon, "§e🏛 " + b.name(), lore, reserved),
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
