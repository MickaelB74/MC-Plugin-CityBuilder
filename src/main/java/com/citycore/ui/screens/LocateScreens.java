package com.citycore.ui.screens;

import com.citycore.city.CityManager;
import com.citycore.npc.CityNPC;
import com.citycore.npc.NPCDataManager;
import com.citycore.npc.NPCManager;
import com.citycore.ui.UiCanvas;
import com.citycore.ui.UiItems;
import com.citycore.ui.UiKit;
import com.citycore.ui.UiMap;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import static com.citycore.ui.UiLayout.*;

/**
 * Où sont-ils ? (/city locate) : les personnages de Valmont à gauche (cadenas tant qu'aucun joueur
 * ne les a rencontrés et qu'ils ne sont pas arrivés en ville : la règle des ateliers), une carte vue
 * du ciel à droite. Un personnage rencontré mais pas encore venu se retrouve ainsi sur la carte. Un clic
 * sur un personnage centre la carte sur lui et affiche ses coordonnées ; clic gauche / droit sur
 * la carte : zoom avant / arrière ; bouton « carte » de l'en-tête : vue d'ensemble de Valmont.
 *
 * La carte est dessinée par le plugin (couleurs des cartes du jeu, voir {@link UiMap}) : limites
 * de la ville en or, habitants en rouge, personnage choisi en jaune, le joueur en blanc. Sans le
 * pack, la même chose est donnée en texte dans le chat.
 */
public final class LocateScreens {

    /** Personnages par page : 3 colonnes x 4 rangées. */
    private static final int PER_PAGE = 12;
    /** Échelles proposées (blocs par case de la carte), du plus près au plus loin. */
    private static final int[] SCALES = {1, 2, 3, 4, 6, 8, 12, 16, 24, 32};
    /** Échelle quand on choisit un personnage : 106 x 70 blocs autour de lui. */
    private static final int FOCUS_SCALE = 2;

    // Couleurs de la carte
    private static final int OUTLINE = 0x24160B, BORDER = 0xF1CF5E;
    private static final int NPC_DOT = 0xD8402F, SELECTED = 0xFFD84A, YOU = 0xFFFFFF;

    private final UiKit          kit;
    private final NPCManager     npcs;
    private final NPCDataManager data;
    private final CityManager    city;
    /** Personnage trouvé : rencontré par un joueur, arrivé ou installé (même règle que les ateliers). */
    private final Predicate<CityNPC> found;

    public LocateScreens(UiKit kit, NPCManager npcs, NPCDataManager data, CityManager city, Predicate<CityNPC> found) {
        this.kit   = kit;
        this.npcs  = npcs;
        this.data  = data;
        this.city  = city;
        this.found = found;
    }

    /** Ouvre l'écran (centré sur {@code focus} s'il est découvert) ; false sans le pack. */
    public boolean open(Player player, CityNPC focus) {
        if (!kit.active(player)) return false;
        return kit.show(new Screen(player, focus != null && discovered(focus) ? focus : null));
    }

    /** Sans le pack : la même chose en texte, dans le chat. */
    public void report(Player player) {
        player.sendMessage("§6🗺 Où sont-ils ?");
        for (CityNPC npc : ordered()) {
            String name = plain(npc.displayName);
            if (!discovered(npc)) {
                player.sendMessage("§8🔒 " + name + " §8— pas encore découvert");
                continue;
            }
            Location loc = location(npc);
            player.sendMessage("§f" + name + " §8· §7" + npc.function + " §8— "
                    + (loc == null ? "§7position inconnue" : "§f" + coords(loc) + " §8(" + where(player, loc) + ")"));
        }
    }

    /** Personnage désigné par son nom (« thorvald », « jacksparrow », « Hélène »…), ou null. */
    public CityNPC find(String name) {
        String wanted = key(name);
        if (wanted.isEmpty()) return null;
        for (CityNPC npc : CityNPC.values()) if (key(plain(npc.displayName)).equals(wanted)) return npc;
        for (CityNPC npc : CityNPC.values()) if (key(plain(npc.displayName)).startsWith(wanted)) return npc;
        return null;
    }

    /** Noms proposés à la complétion de /city locate. */
    public static List<String> names() {
        List<String> names = new ArrayList<>();
        for (CityNPC npc : CityNPC.values()) names.add(plain(npc.displayName).toLowerCase(Locale.FRENCH).replace(" ", ""));
        return names;
    }

    public boolean discovered(CityNPC npc) {
        // Le maire apparaît à la fondation de la ville (son état n'est pas toujours enregistré)
        if (npc == CityNPC.MAYOR) return city.isCityInitialized();
        return found.test(npc);
    }

    /** Où en est le personnage avec la ville (ligne de sa bulle). */
    private String status(CityNPC npc) {
        if (npc == CityNPC.MAYOR) return "§aÀ la mairie de Valmont";
        return switch (data.getState(npc)) {
            case ASSIGNED -> "§aInstallé à Valmont";
            case ARRIVED  -> "§eÀ Valmont, sans logis";
            case HOMELESS -> "§eÀ Valmont, sans logis (bâtiment disparu)";
            case WANDERER -> "§7Rencontré, pas encore venu à Valmont";
        };
    }

    /* =========================
       DONNÉES
       ========================= */

    /** Personnages dans l'ordre de la liste : découverts d'abord, puis ceux encore attendus. */
    private List<CityNPC> ordered() {
        List<CityNPC> found = new ArrayList<>(), locked = new ArrayList<>();
        for (CityNPC npc : CityNPC.values()) (discovered(npc) ? found : locked).add(npc);
        found.addAll(locked);
        return found;
    }

    /** Position du personnage (entité présente, sinon dernière position connue), ou null. */
    private Location location(CityNPC type) {
        NPC npc = npcs.getNPC(type);
        if (npc == null) return null;
        if (npc.isSpawned() && npc.getEntity() != null) return npc.getEntity().getLocation();
        Location stored = npc.getStoredLocation();
        return stored == null || stored.getWorld() == null ? null : stored;
    }

    /** Monde de la ville : celui qui compte le plus de parcelles (null si aucune). */
    private World cityWorld() {
        World best = null;
        int most = 0;
        for (World w : Bukkit.getWorlds()) {
            int n = city.getClaimedChunkCoords(w.getName()).size();
            if (n > most) { most = n; best = w; }
        }
        return best;
    }

    private Set<Long> claimed(World world) {
        Set<Long> set = new HashSet<>();
        for (long[] c : city.getClaimedChunkCoords(world.getName())) set.add(chunkKey((int) c[0], (int) c[1]));
        return set;
    }

    private static long chunkKey(int cx, int cz) {
        return ((long) cx << 32) | (cz & 0xFFFFFFFFL);
    }

    private static String coords(Location l) {
        return "X " + l.getBlockX() + " · Y " + l.getBlockY() + " · Z " + l.getBlockZ();
    }

    /** Distance au joueur, ou monde où se trouve le personnage. */
    private static String where(Player player, Location loc) {
        if (loc.getWorld() != player.getWorld()) return worldLabel(loc.getWorld());
        int d = (int) Math.round(player.getLocation().distance(loc));
        return d + (d > 1 ? " blocs" : " bloc");
    }

    private static String worldLabel(World world) {
        return switch (world.getEnvironment()) {
            case NETHER  -> "Nether";
            case THE_END -> "End";
            default      -> world.getName();
        };
    }

    private static String plain(String s) {
        return s == null ? "" : s.replaceAll("§.", "").trim();
    }

    /** Nom réduit à ses lettres, sans accents ni espaces (comparaison des noms). */
    private static String key(String s) {
        String n = Normalizer.normalize(s == null ? "" : s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return n.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    /* =========================
       ÉCRAN
       ========================= */

    private final class Screen extends ValmontMenu {

        private CityNPC selected;
        private int page;
        /** Blocs par case ; 0 = échelle automatique (vue d'ensemble ajustée à la ville). */
        private int scale;

        Screen(Player player, CityNPC selected) {
            super(LocateScreens.this.kit, player);
            this.selected = selected;
            this.scale = selected == null ? 0 : FOCUS_SCALE;
            if (selected != null) page = ordered().indexOf(selected) / PER_PAGE;
        }

        @Override
        protected void draw(UiCanvas c) {
            c.background("bg_locate");
            nameplate(c, "Où sont-ils ?", null);
            List<CityNPC> list = ordered();
            Map<CityNPC, Location> where = new EnumMap<>(CityNPC.class);
            for (CityNPC npc : list) {
                if (!discovered(npc)) continue;
                Location loc = location(npc);
                if (loc != null) where.put(npc, loc);
            }

            // En-tête : personnage choisi (ou vue d'ensemble), boutons
            String banner = selected == null ? "Vue d'ensemble" : plain(selected.displayName) + " · " + selected.function;
            c.textCentered(c.fit(banner, 120, true), sx(0) + 63, rowText(0), CREAM, true);
            c.sprite("ic_map", sx(7), sy(0));
            zone(7, 0, 1, 1, "§3🗺 Vue d'ensemble", List.of("§7Toute la ville de Valmont", "§7et ses habitants."),
                    t -> { selected = null; scale = 0; open(); });
            close(c);

            list(c, list, where);
            plaque(c, map(c, where));
        }

        /** Grille des personnages (colonnes 0 à 2) et pages (rangée 5). */
        private void list(UiCanvas c, List<CityNPC> list, Map<CityNPC, Location> where) {
            int pages = Math.max(1, (list.size() + PER_PAGE - 1) / PER_PAGE);
            page = Math.max(0, Math.min(page, pages - 1));
            for (int k = 0; k < PER_PAGE; k++) {
                int i = page * PER_PAGE + k;
                if (i >= list.size()) break;
                CityNPC npc = list.get(i);
                int col = k % 3, row = 1 + k / 3;
                String name = plain(npc.displayName);
                if (!discovered(npc)) {
                    c.sprite("ring_gray", sx(col), sy(row));
                    c.sprite("lock", sx(col) + 3, sy(row) + 3);
                    zone(col, row, 1, 1, "§8🔒 §7" + name, List.of("§8" + npc.function, "",
                            "§7Pas encore découvert : il", "§7apparaîtra ici dès qu'un", "§7joueur l'aura rencontré."), null);
                    continue;
                }
                Location loc = where.get(npc);
                c.sprite(npc == selected ? "ring_gold" : loc != null ? "ring_green" : "ring_gray", sx(col), sy(row));
                List<String> lore = new ArrayList<>();
                lore.add("§7" + npc.function);
                lore.add(status(npc));
                lore.add("");
                if (loc == null) {
                    lore.add("§7Position inconnue.");
                } else {
                    lore.add("§f" + coords(loc));
                    lore.add("§8" + where(player, loc));
                }
                lore.add("");
                lore.add(npc == selected ? "§e▶ Clic : actualiser sa position" : "§e▶ Clic : le montrer sur la carte");
                item(col, row, UiItems.head(npcs.resolveSkin(npc), npc.tag, "§6" + name, lore),
                        t -> { selected = npc; scale = FOCUS_SCALE; open(); });
            }
            boolean prev = page > 0, next = page < pages - 1;
            c.sprite(prev ? "ic_prev" : "ic_prev_off", sx(0), sy(5));
            c.sprite(next ? "ic_next" : "ic_next_off", sx(2), sy(5));
            if (prev) zone(0, 5, 1, 1, "§7◀ Page précédente", List.of(), t -> { page--; open(); });
            if (next) zone(2, 5, 1, 1, "§7Page suivante ▶", List.of(), t -> { page++; open(); });
            c.smallCentered((page + 1) + "/" + pages, sx(1) + 9, LOC_PAGE_Y, CREAM);
        }

        /**
         * Carte (colonnes 3 à 8, rangées 1 à 4) : terrain, limites de la ville, marques.
         *
         * @return les deux lignes de la plaque des coordonnées
         */
        private String[] map(UiCanvas c, Map<CityNPC, Location> where) {
            Location focus = selected == null ? null : where.get(selected);
            UiMap.Area area;
            String[] plaque;
            if (focus != null) {
                int sc = scale > 0 ? scale : FOCUS_SCALE;
                area = UiMap.Area.centered(focus.getWorld(), focus.getX(), focus.getZ(), sc, LOC_MAP_COLS, LOC_MAP_ROWS);
                plaque = new String[]{plain(selected.displayName) + " · " + where(player, focus), coords(focus)};
            } else {
                area = overview();
                plaque = selected != null
                        ? new String[]{plain(selected.displayName), "Position inconnue"}
                        : new String[]{"1 case = " + area.scale() + (area.scale() > 1 ? " blocs" : " bloc"),
                                "Vous : " + coords(player.getLocation())};
            }

            int[][] px = UiMap.terrain(area);
            border(px, area);
            for (Map.Entry<CityNPC, Location> e : where.entrySet()) {
                if (e.getKey() != selected) mark(px, area, e.getValue(), 1, NPC_DOT);
            }
            mark(px, area, player.getLocation(), 1, YOU);
            if (focus != null) mark(px, area, focus, 2, SELECTED);
            for (int r = 0; r < area.rows(); r++) c.pixelRow("px2", LOC_MAP_X, LOC_MAP_Y + LOC_CELL * r, px[r]);

            int cx = area.blockX(area.cols() / 2), cz = area.blockZ(area.rows() / 2);
            zone(3, 1, 6, 4, "§6🗺 " + (selected == null ? "Valmont" : plain(selected.displayName)), List.of(
                    "§7Échelle : §f1 case = " + area.scale() + (area.scale() > 1 ? " blocs" : " bloc"),
                    "§7Centre : §fX " + cx + " · Z " + cz,
                    "",
                    "§6■ §7limites de Valmont",
                    "§c■ §7habitants   §e■ §7personnage choisi",
                    "§f■ §7vous",
                    "§8Parchemin : zone pas encore chargée",
                    "",
                    "§e▶ Clic gauche : zoom avant",
                    "§e▶ Clic droit : zoom arrière"),
                    t -> zoom(area.scale(), t.isRightClick() ? 1 : -1));
            return plaque;
        }

        /** Vue d'ensemble : toute la ville (ou les alentours du joueur s'il n'y a pas de ville). */
        private UiMap.Area overview() {
            World world = cityWorld();
            if (world == null) {
                Location l = player.getLocation();
                return UiMap.Area.centered(l.getWorld(), l.getX(), l.getZ(), scale > 0 ? scale : 4, LOC_MAP_COLS, LOC_MAP_ROWS);
            }
            int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
            for (long[] ch : city.getClaimedChunkCoords(world.getName())) {
                minX = Math.min(minX, (int) ch[0]); maxX = Math.max(maxX, (int) ch[0]);
                minZ = Math.min(minZ, (int) ch[1]); maxZ = Math.max(maxZ, (int) ch[1]);
            }
            // Ville + un chunk de marge tout autour
            double width = (maxX - minX + 3) * 16.0, height = (maxZ - minZ + 3) * 16.0;
            int sc = scale;
            if (sc <= 0) {
                int needed = (int) Math.ceil(Math.max(width / LOC_MAP_COLS, height / LOC_MAP_ROWS));
                sc = SCALES[SCALES.length - 1];
                for (int s : SCALES) if (s >= needed) { sc = s; break; }
            }
            double cx = (minX + maxX + 1) * 8.0, cz = (minZ + maxZ + 1) * 8.0;
            return UiMap.Area.centered(world, cx, cz, sc, LOC_MAP_COLS, LOC_MAP_ROWS);
        }

        /** Limites de la ville : cases d'une parcelle revendiquée qui touchent une case hors de la ville. */
        private void border(int[][] px, UiMap.Area area) {
            Set<Long> claimed = claimed(area.world());
            if (claimed.isEmpty()) return;
            boolean[][] in = new boolean[area.rows() + 2][area.cols() + 2];
            for (int r = -1; r <= area.rows(); r++) {
                for (int c = -1; c <= area.cols(); c++) {
                    in[r + 1][c + 1] = claimed.contains(chunkKey(area.blockX(c) >> 4, area.blockZ(r) >> 4));
                }
            }
            for (int r = 0; r < area.rows(); r++) {
                for (int c = 0; c < area.cols(); c++) {
                    if (in[r + 1][c + 1] && !(in[r][c + 1] && in[r + 2][c + 1] && in[r + 1][c] && in[r + 1][c + 2])) {
                        px[r][c] = BORDER;
                    }
                }
            }
        }

        /** Marque en croix (bras de {@code arm} cases) sur un carré sombre, si elle tombe sur la carte. */
        private void mark(int[][] px, UiMap.Area area, Location loc, int arm, int color) {
            if (loc == null || loc.getWorld() != area.world()) return;
            int col = area.col(loc.getX()), row = area.row(loc.getZ());
            for (int dr = -arm; dr <= arm; dr++) {
                for (int dc = -arm; dc <= arm; dc++) {
                    if (!area.inside(col + dc, row + dr)) continue;
                    px[row + dr][col + dc] = dr == 0 || dc == 0 ? color : OUTLINE;
                }
            }
        }

        /** Zoom avant (-1) ou arrière (+1) d'une échelle. */
        private void zoom(int current, int step) {
            int idx = 0;
            for (int i = 0; i < SCALES.length; i++) if (SCALES[i] <= current) idx = i;
            idx = Math.max(0, Math.min(SCALES.length - 1, idx + step));
            if (SCALES[idx] == current) return;
            scale = SCALES[idx];
            open();
        }

        /** Plaque de la rangée 5 : deux lignes de petite police. */
        private void plaque(UiCanvas c, String[] lines) {
            int center = sx(3) + 54;
            c.smallCentered(c.fitSmall(lines[0], 102), center, LOC_LINE1_Y, GOLD);
            c.smallCentered(c.fitSmall(lines[1], 102), center, LOC_LINE2_Y, CREAM);
        }
    }
}
