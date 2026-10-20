package com.citycore.ui.screens;

import com.citycore.npc.CityNPC;
import com.citycore.npc.NPCManager;
import com.citycore.quest.QuestHUD;
import com.citycore.quest.personal.StoryJournal;
import com.citycore.ui.UiCanvas;
import com.citycore.ui.UiItems;
import com.citycore.ui.UiKit;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static com.citycore.ui.UiLayout.*;

/**
 * Journal de quêtes illustré (/city quests), en deux onglets :
 *
 * « Suivi » : quels habitants apparaissent dans le suivi des quêtes, où le panneau se place,
 * et où le suivi s'affiche. Rangées 1 à 3 : un habitant ayant une quête en cours par case (anneau
 * vert = suivi, gris = masqué) ; rangée 4 : trois positions du panneau ; rangée 5 : mode
 * d'affichage (visible, accroché à la liste des joueurs, masqué ; gardé dans les préférences du
 * joueur), tout suivre.
 *
 * « Récits » : les récits (quêtes personnelles) de chaque personnage. Rangées 1 et 2 : les
 * survivants, rangée 3 : les légendes, dans l'ordre des chapitres (plus de 9 légendes : 8 par
 * page, la dernière case de la rangée passe à la page suivante). Cadenas = pas encore à
 * Valmont ; anneau doré = récit à accepter, ambre = en cours, vert = à jour, rouge = mod absent.
 * Un clic sur un personnage installé ouvre l'écran de ses récits (retour vers le journal).
 * Rangée 4 : bilan ; rangée 5 : barre des récits accomplis.
 */
public final class QuestTrackingScreen {

    /** Cases de la grille (rangées 1 à 3). */
    private static final int CELLS = 27;

    private final UiKit        kit;
    private final QuestHUD     hud;
    private final NPCManager   npcManager;
    private final StoryJournal journal;
    /** Menus classiques des deux onglets (pack absent, ou dessin impossible). */
    private Consumer<Player> classicTracking = p -> {};
    private Consumer<Player> classicStories  = p -> {};

    public QuestTrackingScreen(UiKit kit, QuestHUD hud, NPCManager npcManager, StoryJournal journal) {
        this.kit        = kit;
        this.hud        = hud;
        this.npcManager = npcManager;
        this.journal    = journal;
    }

    public void setClassic(Consumer<Player> tracking, Consumer<Player> stories) {
        this.classicTracking = tracking;
        this.classicStories  = stories;
    }

    /** Ouvre l'onglet « Suivi » ; false si le joueur n'a pas le pack (menu classique à la place). */
    public boolean open(Player player) {
        return kit.active(player) && kit.show(new Tracking(player));
    }

    /** Ouvre l'onglet « Récits » ; false si le joueur n'a pas le pack (menu classique à la place). */
    public boolean openStories(Player player) {
        return kit.active(player) && kit.show(new Stories(player));
    }

    /** Onglet « Suivi », ou son menu classique. */
    private void tracking(Player player) {
        if (!open(player)) classicTracking.accept(player);
    }

    /** Onglet « Récits », ou son menu classique. */
    private void stories(Player player) {
        if (!openStories(player)) classicStories.accept(player);
    }

    /** En-tête commun : onglets SUIVI (colonnes 0-2) et RÉCITS (3-6), l'onglet ouvert en doré. */
    private abstract class JournalMenu extends ValmontMenu {

        JournalMenu(Player p) {
            super(QuestTrackingScreen.this.kit, p);
        }

        void tabs(UiCanvas c, boolean onTracking) {
            c.sprite(onTracking ? "tab_suivi_on" : "tab_suivi_off", sx(0), sy(0));
            c.sprite(onTracking ? "tab_recits_off" : "tab_recits_on", sx(3), sy(0));
            zone(0, 0, 3, 1, "§6📋 Suivi des quêtes", onTracking
                            ? List.of("§7Onglet ouvert.")
                            : List.of("§7Quêtes suivies, position du panneau", "§7et affichage du suivi.",
                            "", "§e▶ Cliquez pour ouvrir"),
                    onTracking ? null : t -> tracking(player));
            zone(3, 0, 4, 1, "§5✦ Récits des habitants", onTracking
                            ? List.of("§7Où vous en êtes dans les récits", "§7de chaque personnage.",
                            "", "§e▶ Cliquez pour ouvrir")
                            : List.of("§7Onglet ouvert."),
                    onTracking ? t -> stories(player) : null);
        }
    }

    /* =========================
       ONGLET « SUIVI »
       ========================= */

    private final class Tracking extends JournalMenu {

        Tracking(Player p) {
            super(p);
        }

        @Override
        protected void draw(UiCanvas c) {
            c.background("bg_tracking");
            nameplate(c, "Journal de quêtes", null);
            tabs(c, true);
            info(c, 7, "§6📋 Suivi des quêtes", List.of(
                    "§7Cliquez sur un habitant pour afficher",
                    "§7ou masquer ses quêtes dans le suivi.",
                    "", "§7En bas : position du panneau,",
                    "§7affichage du suivi (visible, accroché",
                    "§7à la liste des joueurs, masqué),",
                    "§7et tout suivre d'un coup.",
                    "", "§8/city quests toggle : changer l'affichage",
                    "§8/city quests recits : onglet Récits"));
            close(c);

            npcs(c);
            positions(c);
            mode(c);

            // Tout suivre
            c.sprite("track_all", sx(6), sy(5));
            zone(6, 5, 3, 1, "§9✔ Tout suivre", List.of("§7Affiche de nouveau les quêtes", "§7de tous les habitants."),
                    t -> { hud.trackAll(player); open(); });
        }

        /**
         * Mode d'affichage du suivi (cases 0 à 5 de la rangée 5) : visible, accroché à la liste des
         * joueurs (Tab), masqué. Clic gauche = mode suivant, clic droit = précédent.
         */
        private void mode(UiCanvas c) {
            QuestHUD.Mode current = hud.mode(player);
            c.sprite(switch (current) {
                case VISIBLE  -> "hud_on";
                case ACCROCHE -> "hud_tab";
                case MASQUE   -> "hud_off";
            }, sx(0), sy(5));
            zone(0, 5, 6, 1, QuestHUD.modeTitle(current), QuestHUD.modeLore(current, true, true), t -> {
                hud.setMode(player, t.isRightClick() ? current.previous() : current.next());
                open();
            });
        }

        /** Habitants ayant une quête en cours : tête + anneau d'état, clic = suivre / masquer. */
        private void npcs(UiCanvas c) {
            UUID uuid = player.getUniqueId();
            List<CityNPC> list = hud.npcsWithActiveQuest(player);
            if (list.isEmpty()) {
                c.textCentered("Aucune quête en cours", 88, rowText(2), CREAM, true);
                zone(0, 2, 9, 1, "§7Aucune quête en cours",
                        List.of("§7Acceptez une quête auprès d'un", "§7habitant pour la suivre ici."), null);
                return;
            }
            for (int i = 0; i < Math.min(CELLS, list.size()); i++) {
                CityNPC npc = list.get(i);
                int col = i % 9, row = 1 + i / 9;
                boolean tracked = hud.isNpcTracked(uuid, npc);
                String name = plain(npc.displayName);
                c.sprite(tracked ? "ring_green" : "ring_gray", sx(col), sy(row));
                List<String> lore = List.of("§7" + npc.function, "",
                        tracked ? "§a● Suivi §7— affiché dans le suivi" : "§8○ Masqué §7— absent du suivi",
                        tracked ? "§e▶ Cliquez pour masquer" : "§a▶ Cliquez pour suivre");
                item(col, row, UiItems.head(npcManager.resolveSkin(npc), npc.tag,
                                (tracked ? "§a✔ " : "§7✘ ") + "§f" + name, lore),
                        t -> { hud.setNpcTracked(uuid, npc, !tracked); open(); });
            }
        }

        /** Trois positions du panneau ; cadre doré sur la position choisie. */
        private void positions(UiCanvas c) {
            String[] sprites = {"pos_bord", "pos_gauche", "pos_pres"};
            String[] names = {"§6◀ Bord de l'écran", "§6Marge", "§6▶ Contre l'inventaire"};
            List<List<String>> details = List.of(
                    List.of("§7Le panneau touche le bord gauche", "§7(écran 1080p, interface à l'échelle 4)."),
                    List.of("§7Un peu plus au centre : pour les", "§7écrans 1440p, 720p ou 1366 x 768."),
                    List.of("§7Juste à gauche de l'inventaire :", "§7écrans 16:10 ou interface agrandie."));
            int current = hud.panelPosition(player);
            for (int k = 0; k < sprites.length; k++) {
                int index = k;
                c.sprite(sprites[k], sx(3 * k), sy(4));
                List<String> lore = new java.util.ArrayList<>(details.get(k));
                lore.add("§8Panneau du suivi visible (le suivi accroché");
                lore.add("§8se place sous le panneau de la cité, Tab).");
                lore.add("");
                lore.add(k == current ? "§a✔ Position actuelle" : "§e▶ Cliquez pour choisir");
                zone(3 * k, 4, 3, 1, names[k], lore, k == current ? null : t -> {
                    hud.setPanelPosition(player, index);
                    open();
                });
            }
            c.sprite("sel54", sx(3 * current), sy(4));
        }
    }

    /* =========================
       ONGLET « RÉCITS »
       ========================= */

    private final class Stories extends JournalMenu {

        /** Page des légendes (rangée 3), quand elles ne tiennent pas sur une rangée. */
        private final int legendPage;

        Stories(Player p) {
            this(p, 0);
        }

        Stories(Player p, int legendPage) {
            super(p);
            this.legendPage = legendPage;
        }

        @Override
        protected void draw(UiCanvas c) {
            c.background("bg_journal");
            nameplate(c, "Journal de quêtes", null);
            tabs(c, false);
            info(c, 7, "§5✦ Récits des habitants", List.of(
                    "§7Les récits (quêtes personnelles) de",
                    "§7chaque personnage, et où vous en êtes.",
                    "", "§7Rangées 1 et 2 : survivants de Valmont", "§7Rangée 3 : légendes de passage",
                    "", "§e● §7récit à accepter    §6● §7récit en cours",
                    "§a● §7à jour    §c● §7mod absent",
                    "§8● §7en ville, pas encore installé",
                    "§8🔒 §7pas encore à Valmont",
                    "", "§7Cliquez sur un personnage installé",
                    "§7pour ouvrir ses récits.",
                    "", "§8Raccourci : /city quests recits"));
            close(c);

            List<StoryJournal.Entry> entries = journal.entries(player.getUniqueId());
            int survivors = (int) entries.stream().filter(e -> !e.legend()).count();
            int legends = entries.size() - survivors;
            int legendStart = (survivors + 8) / 9 * 9; // les légendes commencent une rangée plus bas
            // Plus de légendes que de cases sur la rangée : 8 par page, la 9e case change de page
            int legendRoom = CELLS - legendStart;
            boolean paged = legends > legendRoom;
            int perPage = paged ? legendRoom - 1 : legendRoom;
            int pages = paged ? (legends + perPage - 1) / perPage : 1;
            int page = Math.floorMod(legendPage, pages);
            int s = 0, l = 0;
            for (StoryJournal.Entry e : entries) {
                if (!e.legend()) {
                    int index = s++;
                    if (index < legendStart) cell(c, e, index % 9, 1 + index / 9);
                    continue;
                }
                int rank = l++ - page * perPage;
                if (rank < 0 || rank >= perPage) continue;
                int index = legendStart + rank;
                cell(c, e, index % 9, 1 + index / 9);
            }
            if (paged) {
                int index = CELLS - 1;
                int next = (page + 1) % pages;
                c.sprite("ic_next", sx(index % 9), sy(1 + index / 9));
                zone(index % 9, 1 + index / 9, 1, 1, "§7Autres légendes ▶ §8(" + (page + 1) + "/" + pages + ")",
                        List.of("§7" + legends + " légendes de passage :", "§7cliquez pour voir la page " + (next + 1) + "."),
                        t -> kit.show(new Stories(player, next)));
            }
            summary(c, entries);
        }

        /** Une case : anneau d'état, tête du personnage (ou cadenas), clic = ses récits. */
        private void cell(UiCanvas c, StoryJournal.Entry e, int col, int row) {
            String title = StoryJournal.title(e);
            List<String> lore = StoryJournal.lore(e);
            switch (e.status()) {
                case LOCKED -> {
                    c.sprite("ring_gray", sx(col), sy(row));
                    c.sprite("lock", sx(col) + 3, sy(row) + 3);
                    zone(col, row, 1, 1, title, lore, null);
                }
                case IN_TOWN, MOD_MISSING -> {
                    c.sprite(e.status() == StoryJournal.Status.MOD_MISSING ? "ring_red" : "ring_gray", sx(col), sy(row));
                    item(col, row, UiItems.head(npcManager.resolveSkin(e.npc()), e.npc().tag, title, lore), null);
                }
                case AVAILABLE -> {
                    c.sprite(e.toAccept() > 0 ? "ring_gold" : e.active() > 0 ? "ring_amber" : "ring_green",
                            sx(col), sy(row));
                    item(col, row, UiItems.head(npcManager.resolveSkin(e.npc()), e.npc().tag, title, lore),
                            t -> journal.open(player, e.npc(), () -> stories(player)));
                }
            }
        }

        /** Rangée 4 : récits à accepter et en cours ; rangée 5 : récits accomplis, tous personnages. */
        private void summary(UiCanvas c, List<StoryJournal.Entry> entries) {
            int done = 0, total = 0, toAccept = 0, active = 0;
            boolean installed = false;
            for (StoryJournal.Entry e : entries) {
                done += e.done();
                total += e.total();
                if (e.clickable()) {
                    installed = true;
                    toAccept += e.toAccept();
                    active += e.active();
                }
            }
            String line = toAccept + active > 0 ? toAccept + " à accepter · " + active + " en cours"
                    : installed ? "Récits à jour" : "Aucun habitant installé";
            c.textCentered(c.fit(line, 150, true), 88, rowText(4), CREAM, true);
            zone(0, 4, 9, 1, "§6✦ Bilan", List.of(
                    "§e" + toAccept + " §7récit" + (toAccept > 1 ? "s" : "") + " à accepter",
                    "§6" + active + " §7récit" + (active > 1 ? "s" : "") + " en cours",
                    "", "§7Un récit s'accepte dans l'écran", "§7des récits du personnage."), null);

            bar(c, total == 0 ? 0 : (double) done / total, total > 0 && done >= total,
                    "Récits " + done + " / " + total);
            zone(0, 5, 9, 1, "§5✦ Récits accomplis : §f" + done + " §7/ §f" + total,
                    List.of("§7Tous personnages confondus.", "§7Un récit se fait une seule fois."), null);
        }
    }
}
