package com.citycore.ui.screens;

import com.citycore.npc.CityNPC;
import com.citycore.raid.RaidConfig;
import com.citycore.raid.RaidManager;
import com.citycore.raid.RaidTarget;
import com.citycore.ui.UiCanvas;
import com.citycore.ui.UiItems;
import com.citycore.ui.UiKit;
import com.citycore.ui.UiMenu;
import com.citycore.util.TypewriterUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static com.citycore.ui.UiLayout.*;

/**
 * Écrans des raids chez Alderic (PLAN_RAIDS §1.1, phase R3) : Événements (raids, sièges en
 * consultation, fêtes à venir) → Difficulté (7 niveaux, cadenas, conditions, recharge) → Cibles
 * du niveau (filtres par type, pages, « au hasard ») → Fiche (objectifs, chrono, récompense,
 * joueurs dans le cercle, « Sonner le rassemblement »).
 *
 * Écrans illustrés avec le pack « Valmont » ; sans le pack, menus classiques du même parcours.
 */
public final class RaidScreens {

    private final UiKit kit;
    private final RaidManager raids;
    /** Retour à l'accueil de la mairie. */
    private final Consumer<Player> mayor;
    /** Planning des événements (sièges), lignes de EventCalendar#listing. */
    private Supplier<List<String>> calendar = List::of;

    private static final String MAYOR_NAME = ChatColor.stripColor(CityNPC.MAYOR.displayName);
    private static final String[] ROMAN = {"I", "II", "III", "IV", "V", "VI", "VII"};

    public RaidScreens(UiKit kit, RaidManager raids, Consumer<Player> mayor) {
        this.kit   = kit;
        this.raids = raids;
        this.mayor = mayor;
    }

    public void setCalendar(Supplier<List<String>> calendar) {
        this.calendar = calendar;
    }

    /** Badge « raid disponible » sur le bouton Événements de l'accueil. */
    public boolean raidAvailable() {
        return raids.raidAvailable();
    }

    /* =========================
       POINTS D'ENTRÉE (illustré si le pack est là, classique sinon)
       ========================= */

    public void openEvents(Player p) {
        if (kit.active(p) && kit.show(new Events(p))) return;
        new ClassicEvents(p).open();
    }

    private void openLevels(Player p) {
        if (kit.active(p) && kit.show(new Levels(p))) return;
        new ClassicLevels(p).open();
    }

    private void openTargets(Player p, int level, RaidTarget.Type filter, int page) {
        if (kit.active(p) && kit.show(new Targets(p, level, filter, page))) return;
        new ClassicTargets(p, level, filter, page).open();
    }

    private void openFiche(Player p, RaidTarget t, RaidTarget.Type filter, int page) {
        if (kit.active(p)) {
            Fiche f = new Fiche(p, t, filter, page);
            if (kit.show(f)) {
                f.live();
                return;
            }
        }
        ClassicFiche f = new ClassicFiche(p, t, filter, page);
        f.open();
        f.live();
    }

    /* =========================
       CONTENU PARTAGÉ (bulles des deux versions)
       ========================= */

    private static String roman(int n) {
        return n >= 1 && n <= ROMAN.length ? ROMAN[n - 1] : String.valueOf(n);
    }

    private static Material levelIcon(int n) {
        return switch (n) {
            case 1 -> Material.WOODEN_SWORD;
            case 2 -> Material.STONE_SWORD;
            case 3 -> Material.IRON_SWORD;
            case 4 -> Material.GOLDEN_SWORD;
            case 5 -> Material.DIAMOND_SWORD;
            case 6 -> Material.NETHERITE_SWORD;
            default -> Material.NETHER_STAR;
        };
    }

    private static Material typeIcon(RaidTarget.Type type) {
        if (type == null) return Material.COMPASS;
        return switch (type) {
            case CAMP -> Material.CAMPFIRE;
            case AVANT_POSTE -> Material.CROSSBOW;
            case VILLAGE -> Material.BELL;
            case VILLE -> Material.LANTERN;
            case FORT -> Material.STONE_BRICKS;
            case CHATEAU -> Material.CHISELED_STONE_BRICKS;
            case ABORDAGE -> Material.OAK_BOAT;
            case REPAIRE -> Material.COBWEB;
        };
    }

    /** Cibles jouables d'un niveau (les autres arrivent avec les phases suivantes). */
    private List<RaidTarget> playable(int level) {
        return raids.targets(level).stream().filter(t -> RaidManager.notYet(t) == null).toList();
    }

    /** Bulle d'un niveau : nom, conditions de déblocage, cibles, défenseurs, chrono, récompense, recharge. */
    private List<String> levelLore(int n) {
        RaidConfig.Level l = raids.level(n);
        RaidConfig c = raids.config();
        int chapter = raids.cityChapter(), wins = raids.victories(n);
        int all = raids.targets(n).size(), ok = playable(n).size();
        String lock = raids.levelLock(n);
        long left = raids.cooldownLeft(n);
        List<String> lore = new ArrayList<>();
        lore.add("§7« " + l.name() + " »");
        lore.add("");
        lore.add((chapter >= l.chapter() ? "§a✔ " : "§c✘ ") + "§7Chapitre " + roman(l.chapter()) + " de Valmont");
        if (n > 1) lore.add((raids.victories(n - 1) > 0 ? "§a✔ " : "§c✘ ") + "§7Une victoire au niveau " + roman(n - 1));
        lore.add("");
        lore.add("§7Cibles : §f" + ok + (all > ok ? " §8(+" + (all - ok) + " bientôt)" : ""));
        lore.add("§7Défenseurs : §f" + l.defenders()[0] + "–" + l.defenders()[1] + " §8(plus nombreux avec chaque joueur)");
        lore.add("§7Chrono : §f" + l.chronoMinutes() + " min" + (l.reinforcements() > 0 ? " §8· " + l.reinforcements()
                + " vague" + (l.reinforcements() > 1 ? "s" : "") + " de renforts" : ""));
        lore.add("§7Récompense : §6" + l.cityReward() + " §7caisse · §6" + l.playerReward() + " §7par combattant");
        if (wins > 0) {
            lore.add("§7Victoires : §f" + wins + " §8(renommée +" + Math.round(Math.min(c.renownMax, wins * c.renownPerVictory) * 100)
                    + " %, adversaires plus coriaces)");
        }
        lore.add(left > 0 ? "§6⏳ Les troupes se reposent encore " + clockLong(left) : "§a✔ Troupes prêtes");
        lore.add("");
        lore.add(lock != null ? "§c🔒 Fermé : " + lock : "§eCliquez : voir les cibles");
        if (lock != null) lore.add("§8Cliquez pour voir les cibles quand même");
        return lore;
    }

    private static String clockLong(long millis) {
        long m = (millis + 59_999) / 60_000;
        return m >= 60 ? m / 60 + " h " + String.format("%02d", m % 60) : m + " min";
    }

    /** Bulle d'une carte de cible. */
    private List<String> cardLore(RaidTarget t, Map<String, Integer> takes) {
        RaidConfig.Level l = raids.level(t.level());
        List<String> lore = new ArrayList<>();
        lore.add("§7" + t.type().icon + " " + t.type().label + " §8· " + mods(t));
        if (t.occupants() != null && !t.occupants().isBlank()) lore.add("§7Occupants : §f" + t.occupants());
        lore.add("§7Chrono : §f" + l.chronoMinutes() + " min §8· §7Récompense : §6" + l.cityReward() + " §7+ §6" + l.playerReward());
        int n = takes.getOrDefault(t.key(), 0);
        if (n > 0) lore.add("§6🏆 Prise ×" + n);
        String later = RaidManager.notYet(t);
        lore.add("");
        lore.add(later != null ? "§8" + capitalize(later) + "." : "§eCliquez : fiche de la cible");
        return lore;
    }

    private static String mods(RaidTarget t) {
        if (t.mods() == null || t.mods().isEmpty()) return "jeu de base";
        List<String> names = new ArrayList<>();
        for (String m : t.mods()) names.add(capitalize(m.replace('_', ' ')));
        return String.join(", ", names);
    }

    /** Objectifs que l'assaut sait suivre (les autres viendront avec les phases suivantes). */
    private List<String> objectivesLore(RaidTarget t) {
        RaidConfig c = raids.config();
        List<String> lore = new ArrayList<>();
        for (String o : t.objectives()) {
            String line = switch (o) {
                case "garnison" -> "Abattre la garnison (" + Math.round(c.garrisonPart * 100) + " %)";
                case "cloche" -> "Sonner la cloche, place dégagée";
                case "bannieres" -> "Arracher les bannières";
                case "cages" -> "Détruire les cages à monstres";
                case "liberer" -> "Libérer les captifs";
                case "chef" -> "Abattre le chef §c(obligatoire)";
                case "maitre" -> "Vaincre le maître des lieux §c(obligatoire)";
                case "aborder" -> "Aborder le navire";
                case "tenir" -> "Tenir la place " + c.holdSeconds + " s";
                case "tours" -> "Prendre les tours de la faction" + (t.city() != null ? " (" + t.city().towers() + ")" : "");
                default -> null;
            };
            if (line != null) lore.add("§e• §f" + line);
        }
        if (lore.isEmpty()) lore.add("§e• §fAbattre la garnison");
        lore.add("§8Un objectif dont la cible n'a pas l'élément");
        lore.add("§8(cloche, bannières…) n'est pas demandé.");
        return lore;
    }

    private List<String> rewardLore(RaidTarget t) {
        RaidConfig c = raids.config();
        RaidConfig.Level l = raids.level(t.level());
        int wins = raids.victories(t.level());
        List<String> lore = new ArrayList<>();
        lore.add("§7Caisse de Valmont : §6+" + l.cityReward() + " coins");
        lore.add("§7Chaque combattant : §6+" + l.playerReward() + " coins");
        lore.add("§7Bonus : §f+" + Math.round(c.bonusRate * 100) + " % §7chacun :");
        for (String b : t.bonuses()) {
            lore.add("§e• §f" + switch (b) {
                case "epargner" -> "épargner les habitants";
                case "sans-perte" -> "aucun combattant mort";
                case "eclair" -> "victoire en moins de " + Math.round(l.chronoMinutes() * c.eclairFraction) + " min";
                default -> b;
            });
        }
        if (wins > 0) lore.add("§7Renommée du niveau : §f+" + Math.round(Math.min(c.renownMax, wins * c.renownPerVictory) * 100) + " %");
        lore.add("");
        lore.add("§8Défaite : rien de perdu, et les troupes");
        lore.add("§8se reposent deux fois moins longtemps.");
        return lore;
    }

    /** Joueurs dans le cercle d'Alderic : tête, compte, noms. */
    private ItemStack circleItem() {
        List<Player> around = raids.aroundMayor();
        List<String> lore = new ArrayList<>();
        lore.add("§7Joueurs à moins de " + raids.config().gatherRadius + " blocs d'Alderic :");
        if (around.isEmpty()) lore.add("§8personne");
        for (int i = 0; i < Math.min(10, around.size()); i++) lore.add("§f• " + around.get(i).getName());
        if (around.size() > 10) lore.add("§8+ " + (around.size() - 10));
        lore.add("");
        lore.add("§8Au départ, ceux qui sont dans le cercle partent.");
        ItemStack head = UiItems.shown(Material.PLAYER_HEAD, "§6Dans le cercle : §f" + around.size(), lore, false);
        head.setAmount(Math.max(1, Math.min(64, around.size())));
        return head;
    }

    /** Clic sur « Sonner le rassemblement ». */
    private void gather(Player p, RaidTarget t) {
        String why = raids.cannotGather(t);
        if (why != null) {
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.6f, 1f);
            p.sendMessage("§7⚔ Pas d'expédition maintenant : " + why + ".");
            return;
        }
        // Menu fermé et réponse tout de suite : la recherche du terrain, une demi-seconde plus tard,
        // fige le serveur quelques secondes (sans retour, on recliquerait)
        p.closeInventory();
        String err = raids.gather(p, t);
        if (err != null) {
            p.sendMessage("§c⚔ " + err);
            return;
        }
        p.sendTitle("§6🔔 Rassemblement", "§7Les éclaireurs partent repérer " + t.name() + "…", 5, 60, 10);
        p.playSound(p.getLocation(), Sound.BLOCK_BELL_USE, 1f, 1.2f);
        TypewriterUtil.play(kit.plugin(), p, List.of("§6Alderic §f: §oJe fais sonner la cloche ! Les éclaireurs partent devant."), null);
    }

    private void random(Player p, int level, RaidTarget.Type filter, List<RaidTarget> list) {
        List<RaidTarget> ok = list.stream().filter(t -> RaidManager.notYet(t) == null).toList();
        if (ok.isEmpty()) {
            p.sendMessage("§7⚔ Aucune cible jouable ici pour l'instant.");
            return;
        }
        openFiche(p, ok.get(ThreadLocalRandom.current().nextInt(ok.size())), filter, 0);
    }

    private static String capitalize(String s) {
        return s == null || s.isEmpty() ? "" : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** Types présents parmi les cibles d'un niveau, dans l'ordre du catalogue. */
    private static List<RaidTarget.Type> typesOf(List<RaidTarget> list) {
        List<RaidTarget.Type> types = new ArrayList<>();
        for (RaidTarget.Type type : RaidTarget.Type.values()) {
            if (list.stream().anyMatch(t -> t.type() == type)) types.add(type);
        }
        return types;
    }

    /* =========================
       ÉCRANS ILLUSTRÉS
       ========================= */

    private abstract class RaidMenu extends ValmontMenu {

        RaidMenu(Player p) {
            super(RaidScreens.this.kit, p);
        }

        void portrait(UiCanvas c, List<String> lore) {
            kit.portraits().draw(c, CityNPC.MAYOR, false);
            zone(0, 1, 4, 4, CityNPC.MAYOR.displayName + " §8— §7Maire", lore, null);
        }

        void bigButton(UiCanvas c, int row, String sprite, String name, List<String> lore, Runnable action) {
            c.sprite(sprite, sx(4), sy(row));
            zone(4, row, 5, 1, name, lore, action == null ? null : t -> action.run());
        }

        /** Plaque des rangées 1 à 4 avec un texte (raccourci au besoin). */
        void textPlate(UiCanvas c, int row, String text, boolean coin, String name, List<String> lore) {
            c.sprite("plate", sx(4), sy(row));
            int x = sx(4) + 6;
            if (coin) {
                c.sprite("coin", sx(4) + 7, sy(row) + 5);
                x = sx(4) + 19;
            }
            c.text(c.fit(text, sx(4) + 86 - x, true), x, rowText(row), GOLD, true);
            zone(4, row, 5, 1, name, lore, null);
        }
    }

    /** Événements : raids, sièges (consultation), fêtes (à venir). */
    private final class Events extends RaidMenu {

        Events(Player p) { super(p); }

        @Override
        protected void draw(UiCanvas c) {
            c.background("bg_character");
            nameplate(c, MAYOR_NAME, "Événements");
            c.text("ÉVÉNEMENTS", STATE_X, rowText(0), GOLD, true);
            back(c, 6, "Mairie", () -> mayor.accept(player));
            info(c, 7, "§6📅 Les événements de Valmont", List.of("§7Raids : la ville lève une expédition",
                    "§7contre une cible lointaine.", "§7Sièges : une armée vient assiéger Valmont.",
                    "§7Fêtes : bientôt."));
            close(c);
            portrait(c, List.of("§8§oAlderic tient le registre des", "§8§oévénements de la ville."));

            List<String> raidLore = new ArrayList<>(List.of("§7Partez à plusieurs prendre une place forte,",
                    "§7un camp ou un repaire, au chrono.", ""));
            raidLore.addAll(raids.status());
            raidLore.add("");
            raidLore.add(raids.ready() ? "§eCliquez : choisir la difficulté" : "§cLes raids sont fermés pour l'instant.");
            bigButton(c, 1, "btn_raids", "§c⚔ Raids", raidLore, raids.ready() ? () -> openLevels(player) : null);
            if (raids.raidAvailable()) c.sprite("badge_new", sx(8) + 4, sy(1) + 4);

            List<String> siegeLore = new ArrayList<>(List.of("§7Consultation : le calendrier de Valmont.", ""));
            siegeLore.addAll(calendar.get());
            bigButton(c, 2, "btn_sieges", "§9🛡 Sièges", siegeLore, null);
            bigButton(c, 3, "btn_fetes_off", "§8🎉 Fêtes", List.of("§7Bientôt : fêtes et foires de Valmont."), null);

            String bar = raids.running() ? "EXPÉDITION EN COURS" : "CHAPITRE " + roman(Math.min(7, Math.max(1, raids.cityChapter())));
            if (raids.running()) bar(c, 1, true, bar); else bar(c, 0, false, bar);
            zone(0, 5, 9, 1, raids.running() ? "§6⚔ Expédition en cours" : "§7Aucune expédition en cours", raids.status(), null);
        }
    }

    /** Difficulté : les 7 niveaux. */
    private final class Levels extends RaidMenu {

        Levels(Player p) { super(p); }

        @Override
        protected void draw(UiCanvas c) {
            c.background("bg_grid");
            nameplate(c, MAYOR_NAME, "Raids");
            back(c, 0, "Événements", () -> openEvents(player));
            c.sprite("rib_raids", sx(1), sy(0));
            info(c, 7, "§c⚔ Difficulté", List.of("§7Chaque niveau s'ouvre avec le chapitre", "§7de Valmont et une victoire au niveau",
                    "§7précédent. Après chaque raid, les", "§7troupes de ce niveau se reposent.", "",
                    "§a■ §7prêt  §6■ §7au repos  §e■ §7déjà gagné  §8🔒 §7fermé"));
            close(c);

            int total = 0;
            for (int n = 1; n <= 7; n++) {
                int col = n;
                String lock = raids.levelLock(n);
                long left = raids.cooldownLeft(n);
                int wins = raids.victories(n);
                total += wins;
                c.textCentered(roman(n), sx(col) + 9, rowText(1), lock == null ? GOLD : MUTED, true);
                int level = n;
                if (lock != null) {
                    c.sprite("lock", sx(col) + 3, sy(2) + 3);
                    zone(col, 2, 1, 1, "§8🔒 Niveau " + roman(n) + " — " + raids.level(n).name(), levelLore(n),
                            t -> openTargets(player, level, null, 0));
                    continue;
                }
                c.sprite(left > 0 ? "ring_amber" : wins > 0 ? "ring_gold" : "ring_green", sx(col), sy(2));
                ItemStack icon = UiItems.shown(levelIcon(n), "§6Niveau " + roman(n) + " — " + raids.level(n).name(),
                        levelLore(n), wins > 0);
                icon.setAmount(Math.max(1, Math.min(64, playable(n).size())));
                item(col, 2, icon, t -> openTargets(player, level, null, 0));
            }
            c.text(c.fit("CHAPITRE " + roman(Math.min(7, Math.max(1, raids.cityChapter()))) + " · " + total + " VICTOIRE"
                    + (total > 1 ? "S" : ""), 150, true), sx(0) + 6, rowText(5), GOLD, true);
        }
    }

    /** Cibles d'un niveau : grille paginée, filtres par type, au hasard. */
    private final class Targets extends RaidMenu {

        private final int level;
        private final RaidTarget.Type filter;
        private final int page;

        Targets(Player p, int level, RaidTarget.Type filter, int page) {
            super(p);
            this.level  = level;
            this.filter = filter;
            this.page   = page;
        }

        @Override
        protected void draw(UiCanvas c) {
            List<RaidTarget> all = raids.targets(level);
            List<RaidTarget> list = filter == null ? all : all.stream().filter(t -> t.type() == filter).toList();
            Map<String, Integer> takes = raids.takes();
            int pg = clampPage(page, list.size());

            c.background("bg_grid");
            nameplate(c, MAYOR_NAME, "Niveau " + roman(level));
            back(c, 0, "Difficulté", () -> openLevels(player));
            c.sprite("rib_raids", sx(1), sy(0));
            info(c, 7, "§6Niveau " + roman(level) + " — " + raids.level(level).name(), levelLore(level));
            close(c);

            for (int i = 0; i < GRID && pg * GRID + i < list.size(); i++) {
                RaidTarget t = list.get(pg * GRID + i);
                int col = i % 9, row = 1 + i / 9;
                boolean later = RaidManager.notYet(t) != null;
                int n = takes.getOrDefault(t.key(), 0);
                if (later) c.sprite("ring_gray", sx(col), sy(row));
                else if (n > 0) c.sprite("ring_gold", sx(col), sy(row));
                item(col, row, UiItems.shown(typeIcon(t.type()), (later ? "§8" : "§f") + t.name(), cardLore(t, takes), n > 0),
                        later ? null : click -> openFiche(player, t, filter, pg));
            }
            if (list.isEmpty()) {
                item(0, 1, UiItems.blank("§7Aucune cible", List.of("§7Aucune cible de ce niveau avec les", "§7mods du serveur.")), null);
            }

            // Pied de page : pages, filtres (tous + types du niveau), au hasard
            pager(c, pg, pages(list.size()), p -> openTargets(player, level, filter, p));
            List<RaidTarget.Type> types = typesOf(all);
            List<RaidTarget.Type> tabs = new ArrayList<>();
            tabs.add(null);
            tabs.addAll(types.subList(0, Math.min(5, types.size())));
            for (int k = 0; k < tabs.size(); k++) {
                RaidTarget.Type type = tabs.get(k);
                boolean on = type == filter;
                long count = type == null ? all.size() : all.stream().filter(t -> t.type() == type).count();
                if (on) c.sprite("ring_gold", sx(1 + k), sy(5));
                item(1 + k, 5, UiItems.shown(typeIcon(type), (on ? "§6" : "§f") + (type == null ? "Toutes les cibles"
                                : type.icon + " " + type.label) + " §8(" + count + ")",
                                List.of(on ? "§aFiltre en cours" : "§eCliquez : filtrer"), on),
                        t -> openTargets(player, level, type, 0));
            }
            c.sprite("ic_random", sx(7), sy(5));
            zone(7, 5, 1, 1, "§5🎲 Au hasard", List.of("§7Une cible tirée au sort", "§7parmi celles affichées."),
                    t -> random(player, level, filter, list));
        }
    }

    /** Fiche d'une cible : détails, joueurs dans le cercle, « Sonner le rassemblement ». */
    private final class Fiche extends RaidMenu {

        private final RaidTarget target;
        private final RaidTarget.Type filter;
        private final int page;
        private BukkitTask task;

        Fiche(Player p, RaidTarget target, RaidTarget.Type filter, int page) {
            super(p);
            this.target = target;
            this.filter = filter;
            this.page   = page;
        }

        @Override
        protected void draw(UiCanvas c) {
            RaidConfig.Level l = raids.level(target.level());
            c.background("bg_character");
            nameplate(c, MAYOR_NAME, "Expédition");
            String lv = "NIV. " + roman(target.level());
            int right = sx(6) - 6;
            c.text(c.fit(target.name().toUpperCase(java.util.Locale.ROOT), right - c.width(lv, true) - 6 - STATE_X, true),
                    STATE_X, rowText(0), GOLD, true);
            c.textRight(lv, right, rowText(0), CREAM, true);
            zone(0, 0, 6, 1, "§6" + target.name(), List.of("§7Niveau " + roman(target.level()) + " — " + l.name()), null);
            back(c, 6, "Cibles", () -> openTargets(player, target.level(), filter, page));
            List<String> details = new ArrayList<>();
            details.add("§7" + target.type().icon + " " + target.type().label + " §8· " + mods(target));
            if (target.occupants() != null && !target.occupants().isBlank()) details.add("§7Occupants : §f" + target.occupants());
            if (target.size() != null && !target.size().isBlank()) details.add("§7Taille : §f" + target.size());
            int n = raids.takes().getOrDefault(target.key(), 0);
            if (n > 0) details.add("§6🏆 Déjà prise ×" + n);
            info(c, 7, "§6" + target.name(), details);
            close(c);
            portrait(c, List.of("§8§o« Rassemblez-vous autour de moi :", "§8§oau signal, l'expédition part. »"));

            textPlate(c, 1, target.type().label.toUpperCase(java.util.Locale.ROOT), false, "§f" + target.type().icon + " "
                    + target.type().label, details);
            List<String> obj = new ArrayList<>(List.of("§7Faites tomber la défiance de l'adversaire :"));
            obj.addAll(objectivesLore(target));
            obj.add("");
            obj.add("§7Défenseurs : §f" + l.defenders()[0] + "–" + l.defenders()[1] + " §8(plus avec chaque joueur)");
            textPlate(c, 2, "CHRONO " + l.chronoMinutes() + " MIN", false, "§e⚔ Objectifs", obj);
            textPlate(c, 3, "GAIN " + fmt(l.cityReward()), true, "§6💰 Récompense", rewardLore(target));

            String why = raids.cannotGather(target);
            List<String> lore = new ArrayList<>(List.of("§7Annonce l'expédition à tout Valmont :",
                    "§7au bout de " + raids.config().gatherSeconds + " s, les joueurs dans le cercle",
                    "§7d'Alderic partent au camp, face à la cible.", ""));
            lore.add(why == null ? "§aCliquez pour sonner le rassemblement" : "§c" + capitalize(why) + ".");
            bigButton(c, 4, why == null ? "btn_rassembler" : "btn_rassembler_off",
                    why == null ? "§a🔔 Sonner le rassemblement" : "§8🔔 Sonner le rassemblement", lore,
                    () -> gather(player, target));

            bar(c, 0, false, "CERCLE D'ALDERIC · " + raids.config().gatherRadius + " BLOCS");
            zone(1, 5, 8, 1, "§6Cercle du rassemblement", List.of("§7Les joueurs à moins de " + raids.config().gatherRadius
                    + " blocs", "§7d'Alderic partent avec l'expédition."), null);
            item(0, 5, circleItem(), null);
        }

        /** Compte des joueurs dans le cercle, revu chaque seconde tant que la fiche est ouverte. */
        void live() {
            task = Bukkit.getScheduler().runTaskTimer(kit.plugin(), () -> {
                if (!player.isOnline() || player.getOpenInventory().getTopInventory().getHolder() != this) {
                    task.cancel();
                    return;
                }
                refresh(0, 5, circleItem());
            }, 20L, 20L);
        }

        @Override
        protected void onClose() {
            if (task != null) task.cancel();
        }
    }

    /* =========================
       MENUS CLASSIQUES (joueur sans le pack)
       ========================= */

    private abstract class Classic extends UiMenu {

        private final String title;

        Classic(Player p, String title) {
            super(RaidScreens.this.kit, p);
            this.title = title;
        }

        @Override
        protected Component windowTitle(UiCanvas canvas) {
            return Component.text(title, NamedTextColor.DARK_GRAY);
        }

        void backButton(String label, Runnable action) {
            item(0, 5, UiItems.shown(Material.ARROW, "§7← " + label, List.of(), false), t -> action.run());
            item(8, 5, UiItems.shown(Material.BARRIER, "§c✖ Fermer", List.of(), false), t -> player.closeInventory());
        }
    }

    private final class ClassicEvents extends Classic {

        ClassicEvents(Player p) { super(p, "Alderic — Événements"); }

        @Override
        protected void draw(UiCanvas c) {
            List<String> raidLore = new ArrayList<>(raids.status());
            raidLore.add("");
            raidLore.add(raids.ready() ? "§eCliquez : choisir la difficulté" : "§cLes raids sont fermés pour l'instant.");
            item(2, 2, UiItems.shown(Material.IRON_SWORD, "§c⚔ Raids", raidLore, raids.raidAvailable()),
                    raids.ready() ? t -> openLevels(player) : null);
            List<String> siegeLore = new ArrayList<>(List.of("§7Consultation : le calendrier de Valmont.", ""));
            siegeLore.addAll(calendar.get());
            item(4, 2, UiItems.shown(Material.SHIELD, "§9🛡 Sièges", siegeLore, false), null);
            item(6, 2, UiItems.shown(Material.GRAY_DYE, "§8🎉 Fêtes", List.of("§7Bientôt : fêtes et foires de Valmont."), false), null);
            backButton("Mairie", () -> mayor.accept(player));
        }
    }

    private final class ClassicLevels extends Classic {

        ClassicLevels(Player p) { super(p, "Alderic — Raids : difficulté"); }

        @Override
        protected void draw(UiCanvas c) {
            for (int n = 1; n <= 7; n++) {
                int level = n;
                boolean locked = raids.levelLock(n) != null;
                ItemStack icon = UiItems.shown(locked ? Material.IRON_BARS : levelIcon(n),
                        (locked ? "§8🔒 " : "§6") + "Niveau " + roman(n) + " — " + raids.level(n).name(), levelLore(n),
                        raids.victories(n) > 0);
                icon.setAmount(Math.max(1, Math.min(64, playable(n).size())));
                item(n, 2, icon, t -> openTargets(player, level, null, 0));
            }
            backButton("Événements", () -> openEvents(player));
        }
    }

    private final class ClassicTargets extends Classic {

        private final int level;
        private final RaidTarget.Type filter;
        private final int page;

        ClassicTargets(Player p, int level, RaidTarget.Type filter, int page) {
            super(p, "Raids — niveau " + roman(level));
            this.level  = level;
            this.filter = filter;
            this.page   = page;
        }

        @Override
        protected void draw(UiCanvas c) {
            List<RaidTarget> all = raids.targets(level);
            List<RaidTarget> list = filter == null ? all : all.stream().filter(t -> t.type() == filter).toList();
            Map<String, Integer> takes = raids.takes();
            int pg = ValmontMenu.clampPage(page, list.size());
            // Rangée 0 : filtres, au hasard
            List<RaidTarget.Type> tabs = new ArrayList<>();
            tabs.add(null);
            List<RaidTarget.Type> types = typesOf(all);
            tabs.addAll(types.subList(0, Math.min(6, types.size())));
            for (int k = 0; k < tabs.size(); k++) {
                RaidTarget.Type type = tabs.get(k);
                boolean on = type == filter;
                item(k, 0, UiItems.shown(typeIcon(type), (on ? "§6" : "§f") + (type == null ? "Toutes les cibles"
                        : type.icon + " " + type.label), List.of(on ? "§aFiltre en cours" : "§eCliquez : filtrer"), on),
                        t -> openTargets(player, level, type, 0));
            }
            item(8, 0, UiItems.shown(Material.AMETHYST_SHARD, "§5🎲 Au hasard", List.of("§7Une cible tirée au sort."), false),
                    t -> random(player, level, filter, list));
            // Rangées 1 à 4 : les cibles
            for (int i = 0; i < ValmontMenu.GRID && pg * ValmontMenu.GRID + i < list.size(); i++) {
                RaidTarget t = list.get(pg * ValmontMenu.GRID + i);
                boolean later = RaidManager.notYet(t) != null;
                item(i % 9, 1 + i / 9, UiItems.shown(later ? Material.GRAY_DYE : typeIcon(t.type()), (later ? "§8" : "§f") + t.name(),
                        cardLore(t, takes), takes.getOrDefault(t.key(), 0) > 0), later ? null : click -> openFiche(player, t, filter, pg));
            }
            int pages = ValmontMenu.pages(list.size());
            if (pg > 0) item(3, 5, UiItems.shown(Material.PAPER, "§7◀ Page précédente", List.of(), false),
                    t -> openTargets(player, level, filter, pg - 1));
            if (pg < pages - 1) item(5, 5, UiItems.shown(Material.PAPER, "§7Page suivante ▶", List.of(), false),
                    t -> openTargets(player, level, filter, pg + 1));
            backButton("Difficulté", () -> openLevels(player));
        }
    }

    private final class ClassicFiche extends Classic {

        private final RaidTarget target;
        private final RaidTarget.Type filter;
        private final int page;
        private BukkitTask task;

        ClassicFiche(Player p, RaidTarget target, RaidTarget.Type filter, int page) {
            super(p, "Expédition — " + target.name());
            this.target = target;
            this.filter = filter;
            this.page   = page;
        }

        @Override
        protected void draw(UiCanvas c) {
            RaidConfig.Level l = raids.level(target.level());
            List<String> details = new ArrayList<>();
            details.add("§7Niveau " + roman(target.level()) + " — " + l.name() + " §8· " + mods(target));
            if (target.occupants() != null && !target.occupants().isBlank()) details.add("§7Occupants : §f" + target.occupants());
            details.add("§7Chrono : §f" + l.chronoMinutes() + " min");
            item(1, 1, UiItems.shown(typeIcon(target.type()), "§6" + target.type().icon + " " + target.name(), details, false), null);
            item(3, 1, UiItems.shown(Material.TARGET, "§e⚔ Objectifs", objectivesLore(target), false), null);
            item(5, 1, UiItems.shown(Material.GOLD_INGOT, "§6💰 Récompense", rewardLore(target), false), null);
            item(7, 1, circleItem(), null);
            String why = raids.cannotGather(target);
            item(4, 3, UiItems.shown(why == null ? Material.BELL : Material.GRAY_DYE,
                    (why == null ? "§a" : "§8") + "🔔 Sonner le rassemblement",
                    List.of(why == null ? "§aCliquez pour sonner le rassemblement" : "§c" + capitalize(why) + "."), why == null),
                    t -> gather(player, target));
            backButton("Cibles", () -> openTargets(player, target.level(), filter, page));
        }

        void live() {
            task = Bukkit.getScheduler().runTaskTimer(kit.plugin(), () -> {
                if (!player.isOnline() || player.getOpenInventory().getTopInventory().getHolder() != this) {
                    task.cancel();
                    return;
                }
                refresh(7, 1, circleItem());
            }, 20L, 20L);
        }

        @Override
        protected void onClose() {
            if (task != null) task.cancel();
        }
    }
}
