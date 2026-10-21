package com.citycore.raid;

import com.citycore.util.DatabaseManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.generator.structure.GeneratedStructure;
import org.bukkit.generator.structure.Structure;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.BoundingBox;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Raids de Valmont (plan « Raids », docs/PLAN_RAIDS.md) : monde réservé, sites, catalogue
 * raids.yml.
 *
 * Phase R0 (prototype, administration seulement) : monde de surface créé et remis à zéro au
 * démarrage, avec ses structures naturelles ; essais : repérage d'un exemplaire naturel de la
 * cible, génération de sa zone en tâche de fond, balayage journalisé. Phase R2 : catalogue et
 * contrôle des mods. Le déroulé d'un raid (rassemblement, assaut, récompenses) viendra avec les
 * phases suivantes.
 */
public final class RaidManager implements org.bukkit.event.Listener {

    private final JavaPlugin plugin;
    /** Base SQLite (retours des joueurs, résultats : phases suivantes). */
    private final DatabaseManager db;
    private final ArenaScanner scanner;

    private RaidConfig config;
    private RaidCatalog catalog;
    private RaidWorld surface;
    private RaidSites sites;

    /** Placements possibles des structures dans le monde de surface (null : réponse indisponible). */
    private NaturalPlacements placements;
    /** Un essai à la fois. */
    private boolean busy;
    private Arena lastArena;

    /**
     * Point de retour de secours (décision du 21/10/2026) : auprès d'Alderic, le maire — après une
     * déconnexion, un plantage, un arrêt du serveur, une remise à zéro du monde, une mort hors
     * raid. Null s'il n'y a pas encore de maire : point d'apparition du monde principal.
     */
    private java.util.function.Supplier<Location> mayorSpot = () -> null;
    /** Position d'avant /city raid tp, par joueur (retour par /city raid retour ; en mémoire). */
    private final java.util.Map<java.util.UUID, Location> returnPoints = new java.util.HashMap<>();

    public RaidManager(JavaPlugin plugin, DatabaseManager db) {
        this.plugin  = plugin;
        this.db      = db;
        this.scanner = new ArenaScanner(plugin);
    }

    public void setMayorSpot(java.util.function.Supplier<Location> mayorSpot) {
        this.mayorSpot = mayorSpot;
    }

    /** Auprès d'Alderic, sinon point d'apparition du monde principal ; jamais dans un monde des raids. */
    public Location fallbackReturn() {
        Location at = null;
        try {
            at = mayorSpot.get();
        } catch (RuntimeException e) {
            plugin.getLogger().warning("⚔ [Raids] Position d'Alderic illisible : " + e.getMessage());
        }
        if (at != null && at.getWorld() != null && !isRaidWorld(at.getWorld())) return at;
        return surface != null ? surface.mainWorld().getSpawnLocation() : org.bukkit.Bukkit.getWorlds().get(0).getSpawnLocation();
    }

    /**
     * Démarrage (un tick après l'activation : registres et mondes chargés) : catalogue et
     * contrôles, monde de surface remis à zéro, puis bilan.
     */
    public void start() {
        config  = new RaidConfig(plugin);
        catalog = new RaidCatalog(plugin, config);
        catalog.ensureFile();
        catalog.load();
        catalog.checkStatic();
        catalog.modWarnings().forEach(w -> plugin.getLogger().warning("⚔ [Raids] " + w));
        // Ancien classement des emplacements (pose par /place, abandonnée le 21/10/2026) : inutile
        File oldCache = new File(plugin.getDataFolder(), "raids-emplacements.yml");
        if (oldCache.exists() && oldCache.delete()) plugin.getLogger().info("⚔ [Raids] raids-emplacements.yml supprimé (plus utilisé).");
        if (!config.active) {
            plugin.getLogger().info(catalog.summary() + " — raids désactivés (raids.active: false).");
            return;
        }
        surface = new RaidWorld(plugin, config, config.surface);
        surface.setFallback(this::fallbackReturn);
        sites = new RaidSites(plugin, config, surface);
        World w = surface.open(config.resetAtStartup);
        if (w != null) checkPlacements(w);
        plugin.getLogger().info(catalog.summary() + (w == null ? " — monde des raids indisponible." : ""));
    }

    /**
     * Cibles dont la structure ne naîtra jamais dans le monde des raids (aucun de ses biomes n'y
     * existe) : masquées dès le démarrage, avec la raison.
     */
    private void checkPlacements(World w) {
        StringBuilder error = new StringBuilder();
        placements = NaturalPlacements.of(w, error);
        if (placements == null) {
            plugin.getLogger().warning("⚔ [Raids] Placements des structures illisibles (" + error
                    + ") : les cibles sans placement ne se verront qu'à leur recherche.");
            return;
        }
        int never = 0;
        for (RaidTarget t : catalog.all()) {
            if (t.otherDimension() || !catalog.isAvailable(t)) continue;
            Structure s = structure(t.mainStructure());
            if (s != null && Boolean.FALSE.equals(placements.canGenerate(s))) {
                catalog.markNeverGenerates(t);
                never++;
            }
        }
        if (never > 0) plugin.getLogger().info("⚔ [Raids] " + never + " cible(s) sans placement dans le monde des raids"
                + " (structures qui ne naissent que dans des biomes de mods absents).");
    }

    private static Structure structure(String id) {
        NamespacedKey key = id == null ? null : NamespacedKey.fromString(id.toLowerCase(Locale.ROOT));
        return key == null ? null : Registry.STRUCTURE.get(key);
    }

    /**
     * Arrêt du serveur : tickets rendus ; avec reinitialiser-au-demarrage, le monde des raids est
     * jeté (déchargé sans sauvegarde, dossier effacé) — il sera recréé de zéro au démarrage.
     */
    public void shutdown() {
        if (sites != null) sites.releaseAll();
        if (surface != null && config.resetAtStartup) surface.discard();
    }

    /**
     * Personne ne se réveille dans le monde des raids hors d'un raid (phase R0 : aucun raid ne se
     * déroule encore) : un joueur qui s'y était déconnecté (connexion perdue, partie quittée,
     * plantage, redémarrage) revient auprès d'Alderic.
     */
    @org.bukkit.event.EventHandler
    public void onSpawnLocation(org.spigotmc.event.player.PlayerSpawnLocationEvent event) {
        if (surface == null || !isRaidWorld(event.getSpawnLocation().getWorld())) return;
        event.setSpawnLocation(fallbackReturn());
        returnPoints.remove(event.getPlayer().getUniqueId());
        plugin.getLogger().info("⚔ [Raids] " + event.getPlayer().getName()
                + " s'était déconnecté dans le monde des raids : retour auprès d'Alderic.");
    }

    /** Mort dans le monde des raids hors d'un raid : réapparition auprès d'Alderic (inventaire gardé par la règle du monde). */
    @org.bukkit.event.EventHandler
    public void onRespawn(org.bukkit.event.player.PlayerRespawnEvent event) {
        if (surface == null || !isRaidWorld(event.getPlayer().getWorld())) return;
        event.setRespawnLocation(fallbackReturn());
        returnPoints.remove(event.getPlayer().getUniqueId());
    }

    public RaidConfig config()   { return config; }
    public RaidCatalog catalog() { return catalog; }
    public RaidWorld surface()   { return surface; }
    public RaidSites sites()     { return sites; }
    public boolean busy()        { return busy; }
    public Arena lastArena()     { return lastArena; }

    /** Un des mondes des raids ? */
    public boolean isRaidWorld(World w) {
        return surface != null && surface.is(w);
    }

    /** « Raids : 58 cibles (58 disponibles) » pour /city mods (null = jeu de base). */
    public String modLine(String modId) {
        return catalog == null ? "" : catalog.modLine(modId);
    }

    /* =========================
       PHASE R0 : ESSAIS (administration)
       ========================= */

    /**
     * Remise à zéro à chaud du monde de surface (/city raid purge) : joueurs renvoyés auprès
     * d'Alderic, monde effacé et recréé, essais oubliés.
     *
     * @return null si c'est fait, sinon la raison du refus
     */
    public String purge() {
        if (surface == null) return "Raids désactivés (raids.active) ou monde indisponible.";
        if (busy) return "Un essai est en cours : attendez la fin.";
        sites.reset();
        World w = surface.open(true);
        lastArena = null;
        return w == null ? "Monde recréé sans succès (voir la console)." : null;
    }

    /**
     * Essai (/city raid essai) : repérage de l'exemplaire naturel non exploré le plus proche d'un
     * point de départ neuf, génération de son chunk de départ puis de toute sa boîte (et d'une
     * marge) en tâche de fond, balayage. Rien n'est posé : la structure est celle que son mod a
     * fait naître.
     *
     * @return null si l'essai est lancé (la suite arrive par messages), sinon la raison du refus
     */
    public String trial(CommandSender who, String id) {
        if (surface == null || sites == null) return "Raids désactivés (raids.active) ou monde indisponible.";
        if (busy) return "Un essai est déjà en cours.";
        World w = surface.world();
        if (w == null) return "Monde des raids absent (voir la console).";
        Structure structure = structure(id);
        if (structure == null) return "Structure inconnue sur ce serveur : " + id + ".";
        RaidTarget target = catalog.byStructure(id);
        if (target != null && target.otherDimension()) return "Les cibles du Nether et de l'End auront leurs propres mondes (phase R9).";
        if (placements != null && Boolean.FALSE.equals(placements.canGenerate(structure))) {
            return id + " ne naît jamais dans le monde des raids : aucun de ses biomes n'y existe"
                    + " (biomes de mods absents du serveur, comme Terralith, Biomes O' Plenty ou Wythers).";
        }
        busy = true;
        tell(who, "§6⚔ Essai §f" + id + (target != null ? " §8(cible « " + target.name() + " »)" : " §8(hors catalogue)")
                + "§6 : recherche d'un exemplaire naturel §8(le serveur peut marquer un temps d'arrêt)§6…");
        sites.locate(structure, id, found -> afterLocate(who, id, target, structure, w, found));
        return null;
    }

    /** Suite d'un essai une fois l'exemplaire repéré (ou non) : génération du site, balayage. */
    private void afterLocate(CommandSender who, String id, RaidTarget target, Structure structure, World w,
                             RaidSites.Found found) {
        plugin.getLogger().info("⚔ [Raids] Recherche de " + id + " en " + found.millis() + " ms : "
                + (found.error() == null ? "x " + found.start().getBlockX() + ", z " + found.start().getBlockZ()
                + " (" + found.how() + ")" : found.error()));
        if (found.error() != null) {
            busy = false;
            if (target != null) catalog.markNotFound(target);
            tell(who, "§c❌ " + capitalize(found.error()) + ".");
            return;
        }
        Location start = found.start();
        int number = sites.history().size() + 1;
        tell(who, "§7Repérée à §fx " + start.getBlockX() + ", z " + start.getBlockZ() + " §8(" + found.how()
                + ", " + found.millis() + " ms)§7 : génération du départ…");
        sites.whenLoaded(w, start.getBlockX() >> 4, start.getBlockZ() >> 4, err -> {
            if (err != null) {
                busy = false;
                tell(who, "§c❌ Génération impossible : " + err + ".");
                return;
            }
            GeneratedStructure generated = sites.generatedAt(start, structure);
            if (generated == null) {
                busy = false;
                tell(who, "§c❌ Chunk de départ généré, mais aucune structure " + id + " n'y est enregistrée.");
                return;
            }
            BoundingBox box = generated.getBoundingBox();
            long t0 = System.currentTimeMillis();
            tell(who, "§7Structure de §f" + size(box) + "§7 : génération de sa zone (marge " + config.margin + " blocs)…");
            sites.prepare(number, box, config.margin, progressBar(who, "Génération du site"), err2 -> {
                if (err2 != null) {
                    busy = false;
                    sites.release(number);
                    tell(who, "§c❌ Génération impossible : " + err2 + ".");
                    return;
                }
                RaidSites.Site site = sites.record(id, start, box, found.millis());
                tell(who, "§7Site prêt en " + seconds(System.currentTimeMillis() - t0) + " ; balayage…");
                scanner.scan(id, w, generated, arena -> {
                    busy = false;
                    lastArena = arena;
                    sites.release(number); // l'essai est fini : rien n'est gardé chargé (un joueur sur place charge ce qu'il voit)
                    plugin.getLogger().info("⚔ [Raids] Balayage de " + id + " (essai n°" + site.number() + ") :");
                    for (String line : arena.describe()) plugin.getLogger().info("⚔ [Raids]   " + line);
                    tell(who, "§6⚔ Balayage §8(détail en console)§6 :");
                    for (String line : arena.describe()) tell(who, "§7  " + line);
                    tellTp(who, site);
                });
            });
        });
    }

    /** Avancement dans la barre d'action du joueur qui a lancé l'essai. */
    private static java.util.function.IntConsumer progressBar(CommandSender who, String label) {
        return pct -> {
            if (who instanceof Player p && p.isOnline()) {
                p.sendActionBar(Component.text("⚔ " + label + " : " + pct + " %", NamedTextColor.GOLD));
            }
        };
    }

    /** Lien cliquable « [Y aller] » vers un essai (joueur seulement). */
    private void tellTp(CommandSender who, RaidSites.Site site) {
        if (!(who instanceof Player p) || !p.isOnline()) return;
        p.sendMessage(tpLink(site).append(Component.text(" essai n°" + site.number() + " · " + site.structure(), NamedTextColor.GRAY)));
    }

    private static Component tpLink(RaidSites.Site site) {
        return Component.text("[Y aller]", NamedTextColor.GREEN)
                .clickEvent(ClickEvent.runCommand("/city raid tp " + site.number()))
                .hoverEvent(HoverEvent.showText(Component.text("Téléportation au bord de " + site.structure())));
    }

    /**
     * /city raid tp : au bord sud de la boîte d'un essai (le dernier par défaut), face à la cible.
     * Un terrain neuf est d'abord généré en tâche de fond (jamais sur le fil principal).
     *
     * @param number numéro d'essai, ou 0 pour le dernier
     * @return null si la téléportation est lancée, sinon la raison du refus
     */
    public String teleportTo(Player player, int number) {
        if (surface == null || surface.world() == null) return "Monde des raids absent.";
        List<RaidSites.Site> history = sites.history();
        RaidSites.Site site = number <= 0 ? (history.isEmpty() ? null : history.get(history.size() - 1)) : sites.site(number);
        if (site == null) return history.isEmpty() ? "Aucun essai depuis le démarrage (/city raid essai <structure>)."
                : "Essai inconnu : numéros 1 à " + history.size() + ".";
        World w = surface.world();
        int x = (int) site.box().getCenterX(), z = (int) site.box().getMaxZ() + 8;
        player.sendMessage("§7⚔ Préparation de l'arrivée…");
        sites.whenLoaded(w, x >> 4, z >> 4, err -> {
            if (!player.isOnline()) return;
            if (err != null) {
                player.sendMessage("§c❌ Téléportation impossible : " + err + ".");
                return;
            }
            teleport(player, new Location(w, x + 0.5, w.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES) + 1,
                    z + 0.5, 180f, 0f));
        });
        return null;
    }

    /**
     * /city raid tp : la position d'origine est notée (hors des mondes des raids) pour le retour,
     * puis téléportation ({@link #move}).
     */
    public void teleport(Player player, Location to) {
        if (!isRaidWorld(player.getWorld())) returnPoints.put(player.getUniqueId(), player.getLocation());
        move(player, to, ok -> player.sendMessage(ok
                ? "§7⚔ Téléportation dans le monde des raids §8(x " + to.getBlockX() + ", z " + to.getBlockZ()
                        + ")§7. Retour : §f/city raid retour§7."
                : "§c❌ Téléportation refusée par le serveur (voir la console)."));
    }

    /**
     * /city raid retour : position d'avant /city raid tp, sinon auprès d'Alderic (après un
     * redémarrage, la position d'avant n'est plus connue).
     */
    public void goBack(Player player) {
        Location back = returnPoints.get(player.getUniqueId());
        boolean known = back != null && back.getWorld() != null && !isRaidWorld(back.getWorld());
        Location to = known ? back : fallbackReturn();
        move(player, to, ok -> {
            if (ok) returnPoints.remove(player.getUniqueId());
            player.sendMessage(!ok ? "§c❌ Téléportation refusée par le serveur (voir la console)."
                    : known ? "§7⚔ Retour à votre position d'avant la téléportation."
                    : "§7⚔ Retour à Valmont, auprès d'Alderic.");
        });
    }

    /**
     * Téléportation fiable sous Youer : chunk d'arrivée déjà prêt, téléportation en tâche de fond,
     * et sur le fil principal si elle n'aboutit pas. Le résultat est noté en console.
     */
    private void move(Player player, Location to, java.util.function.Consumer<Boolean> done) {
        // Chunk d'arrivée déjà prêt (tp : chargé en tâche de fond ; retour : terrain existant) : lecture seule
        to.getChunk();
        String from = player.getWorld().getName();
        player.teleportAsync(to).whenComplete((async, err) -> org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
            boolean arrived = player.isOnline() && player.getWorld().equals(to.getWorld());
            boolean sync = false;
            if (!arrived && player.isOnline()) {
                sync = player.teleport(to);
                arrived = player.getWorld().equals(to.getWorld());
            }
            plugin.getLogger().info("⚔ [Raids] Téléportation de " + player.getName() + " : " + from + " → "
                    + to.getWorld().getName() + " (" + to.getBlockX() + ", " + to.getBlockY() + ", " + to.getBlockZ() + ") "
                    + (arrived ? "réussie" : "en échec") + " (tâche de fond : " + (err != null ? "erreur " + err.getMessage() : async)
                    + (sync ? ", fil principal : true" : "") + ").");
            done.accept(arrived);
        }));
    }

    /** /city raid zones : monde, recherches et essais depuis le démarrage. */
    public void report(CommandSender to) {
        if (surface == null || sites == null) {
            to.sendMessage("§7⚔ Raids désactivés (raids.active) ou monde indisponible.");
            return;
        }
        World w = surface.world();
        to.sendMessage("§6⚔ Sites des raids — §f" + surface.spec().name() + " §8(dimension " + surface.dimensionKey()
                + ", " + surface.lastReset() + ", structures naturelles)");
        to.sendMessage("§7Recherches : §f" + sites.searches() + "§7 · point de départ suivant : §fx "
                + (RaidSites.ORIGIN + (long) sites.searches() * config.spacing) + ", z " + RaidSites.ORIGIN
                + "§7 · rayon de recherche §f" + config.searchRadius + " chunks§7 · chunks gardés chargés : §f" + sites.heldChunks()
                + (w != null ? "§7 · chunks chargés dans le monde : §f" + w.getLoadedChunks().length : ""));
        if (sites.history().isEmpty()) {
            to.sendMessage("§8Aucun essai depuis le démarrage : /city raid essai <structure>.");
            return;
        }
        for (RaidSites.Site site : sites.history()) {
            String line = " §f#" + site.number() + " §7" + site.structure() + " §8— x " + site.start().getBlockX()
                    + ", z " + site.start().getBlockZ() + ", " + size(site.box()) + ", repérée en " + site.locateMillis() + " ms ";
            if (to instanceof Player p) p.sendMessage(Component.text(line.replaceAll("§.", ""), NamedTextColor.GRAY).append(tpLink(site)));
            else to.sendMessage(line);
        }
    }

    private static String size(BoundingBox box) {
        return (int) box.getWidthX() + "×" + (int) box.getHeight() + "×" + (int) box.getWidthZ();
    }

    private static String seconds(long millis) {
        return String.format(Locale.FRANCE, "%.1f s", millis / 1000.0);
    }

    private static String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static void tell(CommandSender who, String msg) {
        if (who instanceof Player p && !p.isOnline()) return;
        who.sendMessage(msg);
    }
}
