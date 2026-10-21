package com.citycore.raid;

import com.citycore.util.DatabaseManager;
import com.citycore.util.TypewriterUtil;
import net.citizensnpcs.api.npc.NPC;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.generator.structure.GeneratedStructure;
import org.bukkit.generator.structure.Structure;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.BoundingBox;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.BiConsumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * Raids de Valmont (plan « Raids », docs/PLAN_RAIDS.md) : monde réservé, sites, catalogue
 * raids.yml, déroulé d'une expédition.
 *
 * Phase R0 : monde de surface avec ses structures naturelles, essais (repérage d'un exemplaire
 * naturel, génération de sa zone en tâche de fond, balayage). Phase R2 : catalogue et contrôle des
 * mods. Phase R1 : une expédition — repérage du site, camp et héraut, rassemblement autour
 * d'Alderic, départ des joueurs du cercle, assaut au chrono du niveau, bilan, retour de chacun chez
 * lui. Phases R4 et R5 ({@link RaidAssault}) : défenseurs selon le niveau, objectifs et défiance,
 * renforts et chef, victoire (capitulation) ou défaite, récompenses, renommée, recharges et
 * déblocage des niveaux ({@link RaidProgress}).
 */
public final class RaidManager {

    private final JavaPlugin plugin;
    private final ArenaScanner scanner;
    private final RaidParticipants participants;
    private final RaidHerald herald = new RaidHerald();
    private final RaidAssault assault;
    private final RaidProgress progress;
    /** Récompenses : caisse de la ville, et bourse d'un joueur (Vault). */
    private IntConsumer cityPurse = n -> { };
    private BiConsumer<UUID, Double> playerPurse = (id, n) -> { };

    private RaidConfig config;
    private RaidCatalog catalog;
    private RaidWorld surface;
    private RaidSites sites;

    /** Placements possibles des structures dans le monde de surface (null : réponse indisponible). */
    private NaturalPlacements placements;

    /** Monde des raids du Nether ou de l'End (R9) : monde, sites, placements possibles. */
    private record Dim(RaidWorld world, RaidSites sites, NaturalPlacements placements) {}
    private Dim nether, end;
    /** Un essai à la fois. */
    private boolean busy;
    private Arena lastArena;
    /** L'expédition en cours (une seule à la fois), ou null. */
    private RaidRun run;

    /**
     * Point de retour de secours (décision du 21/10/2026) : auprès d'Alderic, le maire — après une
     * déconnexion, un plantage, un arrêt du serveur, une remise à zéro du monde, une mort hors
     * raid. Null s'il n'y a pas encore de maire : point d'apparition du monde principal.
     */
    private Supplier<Location> mayorSpot = () -> null;
    /** Où se tient Alderic (centre du rassemblement), ou null. */
    private Supplier<Location> mayorAt = () -> null;
    /** Alderic cesse de suivre un joueur : il reste au centre du cercle. */
    private Runnable mayorStay = () -> { };
    /** Un siège est-il en cours (pas d'expédition pendant un siège) ? */
    private BooleanSupplier siegeRunning = () -> false;
    /** Position d'avant /city raid tp, par joueur (retour par /city raid retour ; en mémoire). */
    private final java.util.Map<UUID, Location> returnPoints = new java.util.HashMap<>();
    /** Joueurs morts au moment du retour : ils rentrent à leur réapparition. */
    private final java.util.Map<UUID, Location> homeOnRespawn = new java.util.HashMap<>();

    public RaidManager(JavaPlugin plugin, DatabaseManager db) {
        this.plugin       = plugin;
        this.scanner      = new ArenaScanner(plugin);
        this.participants = new RaidParticipants(plugin, db, this);
        this.assault      = new RaidAssault(plugin, this);
        this.progress     = new RaidProgress(plugin, db);
    }

    public void setMayorSpot(Supplier<Location> mayorSpot) { this.mayorSpot = mayorSpot; }
    public void setMayor(Supplier<Location> at, Runnable stay) { this.mayorAt = at; this.mayorStay = stay; }
    public void setSiegeRunning(BooleanSupplier siegeRunning) { this.siegeRunning = siegeRunning; }
    /** Palier de la ville (chapitre) : déblocage des niveaux. */
    public void setCityLevel(IntSupplier level) { progress.setCityLevel(level); }
    /** Armée ennemie des raids (soldats des factions des sièges), créée au premier besoin. */
    public void setArmy(Supplier<com.citycore.siege.EnemyArmy> factory, Supplier<com.citycore.siege.SiegeConfig> config) {
        assault.setArmy(factory, config);
    }
    public void setRewards(IntConsumer city, BiConsumer<UUID, Double> player) {
        this.cityPurse = city;
        this.playerPurse = player;
    }

    /** Écouteur des joueurs dans le monde des raids (à enregistrer). */
    public org.bukkit.event.Listener participants() { return participants; }
    /** Écouteur de l'assaut : défenseurs abattus, bannières, cages, cloche, captifs (à enregistrer). */
    public org.bukkit.event.Listener assault() { return assault; }

    /** Auprès d'Alderic, sinon point d'apparition du monde principal ; jamais dans un monde des raids. */
    public Location fallbackReturn() {
        Location at = null;
        try {
            at = mayorSpot.get();
        } catch (RuntimeException e) {
            plugin.getLogger().warning("⚔ [Raids] Position d'Alderic illisible : " + e.getMessage());
        }
        if (at != null && at.getWorld() != null && !isRaidWorld(at.getWorld())) return at;
        return surface != null ? surface.mainWorld().getSpawnLocation() : Bukkit.getWorlds().get(0).getSpawnLocation();
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
        // Raid interrompu (plantage, arrêt) : ses joueurs reviendront auprès d'Alderic
        int stale = participants.clearReturns();
        if (stale > 0) plugin.getLogger().info("⚔ [Raids] " + stale + " position(s) de retour d'un raid interrompu oubliée(s) :"
                + " retour auprès d'Alderic à la reconnexion.");
        if (!config.active) {
            plugin.getLogger().info(catalog.summary() + " — raids désactivés (raids.active: false).");
            return;
        }
        surface = new RaidWorld(plugin, config, config.surface);
        surface.setFallback(this::fallbackReturn);
        sites = new RaidSites(plugin, config, surface);
        World w = surface.open(config.resetAtStartup);
        if (w != null) placements = checkPlacements(w, t -> !t.otherDimension());
        // Nether et End (R9) : leurs propres mondes, remis à zéro de la même façon
        nether = openDim(config.nether, RaidTarget.NETHER);
        end = openDim(config.end, RaidTarget.END);
        plugin.getLogger().info(catalog.summary() + (w == null ? " — monde des raids indisponible." : ""));
    }

    private Dim openDim(RaidConfig.WorldSpec spec, String terrain) {
        if (!spec.active()) return null;
        RaidWorld rw = new RaidWorld(plugin, config, spec);
        rw.setFallback(this::fallbackReturn);
        World w = rw.open(config.resetAtStartup);
        if (w == null) return null;
        NaturalPlacements p = checkPlacements(w, t -> t.otherDimension() && t.terrain().contains(terrain));
        return new Dim(rw, new RaidSites(plugin, config, rw), p);
    }

    /** Monde des raids d'une cible : surface, Nether ou End (null : monde désactivé ou absent). */
    private Dim dimOf(RaidTarget t) {
        if (!t.otherDimension()) return surface == null ? null : new Dim(surface, sites, placements);
        Dim d = t.terrain().contains(RaidTarget.NETHER) ? nether : end;
        return d == null || d.world().world() == null ? null : d;
    }

    /**
     * Cibles dont la structure ne naîtra jamais dans le monde des raids (aucun de ses biomes n'y
     * existe) : masquées dès le démarrage, avec la raison.
     */
    private NaturalPlacements checkPlacements(World w, java.util.function.Predicate<RaidTarget> belongs) {
        StringBuilder error = new StringBuilder();
        NaturalPlacements found = NaturalPlacements.of(w, error);
        if (found == null) {
            plugin.getLogger().warning("⚔ [Raids] Placements des structures de " + w.getName() + " illisibles (" + error
                    + ") : les cibles sans placement ne se verront qu'à leur recherche.");
            return null;
        }
        int never = 0;
        for (RaidTarget t : catalog.all()) {
            if (!belongs.test(t) || !catalog.isAvailable(t)) continue;
            if (t.city() != null ? villageFor(t, found) == null
                    : Boolean.FALSE.equals(canGenerate(found, structure(t.mainStructure())))) {
                catalog.markNeverGenerates(t);
                never++;
            }
        }
        if (never > 0) plugin.getLogger().info("⚔ [Raids] " + never + " cible(s) sans placement dans " + w.getName()
                + " (structures qui ne naissent que dans des biomes de mods absents).");
        return found;
    }

    private static Boolean canGenerate(NaturalPlacements p, Structure s) {
        return s == null || p == null ? null : p.canGenerate(s);
    }

    /**
     * Ville fortifiée : un de ses villages qui naît dans ce monde (au hasard), ou null s'il n'y en a
     * aucun. Sans réponse des placements, tout village connu du serveur est possible.
     */
    private static String villageFor(RaidTarget t, NaturalPlacements p) {
        List<String> ok = new ArrayList<>();
        for (String id : t.city().villages()) {
            Structure s = structure(id);
            if (s != null && !Boolean.FALSE.equals(canGenerate(p, s))) ok.add(id);
        }
        return ok.isEmpty() ? null : ok.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(ok.size()));
    }

    private static Structure structure(String id) {
        NamespacedKey key = id == null ? null : NamespacedKey.fromString(id.toLowerCase(Locale.ROOT));
        return key == null ? null : Registry.STRUCTURE.get(key);
    }

    /**
     * Arrêt du serveur : expédition interrompue (héraut et barre retirés ; les joueurs sont renvoyés
     * auprès d'Alderic avec le monde), tickets rendus ; avec reinitialiser-au-demarrage, le monde
     * des raids est jeté (déchargé sans sauvegarde, dossier effacé).
     */
    public void shutdown() {
        if (run != null) {
            if (run.task != null) run.task.cancel();
            if (run.bar != null) run.bar.removeAll();
            run = null;
        }
        assault.clear();
        herald.remove();
        if (sites != null) sites.releaseAll();
        if (surface != null && config.resetAtStartup) surface.discard();
        for (Dim d : new Dim[]{nether, end}) {
            if (d == null) continue;
            d.sites().releaseAll();
            if (config.resetAtStartup) d.world().discard();
        }
    }

    public RaidConfig config()   { return config; }
    public RaidCatalog catalog() { return catalog; }
    public RaidWorld surface()   { return surface; }
    public RaidSites sites()     { return sites; }
    public boolean busy()        { return busy; }
    public Arena lastArena()     { return lastArena; }
    RaidRun run()                { return run; }
    RaidProgress progress()      { return progress; }

    /** Un des mondes des raids ? */
    public boolean isRaidWorld(World w) {
        return (surface != null && surface.is(w)) || (nether != null && nether.world().is(w)) || (end != null && end.world().is(w));
    }

    boolean isHerald(NPC npc) {
        return herald.is(npc);
    }

    /** « Raids : 58 cibles (58 disponibles) » pour /city mods (null = jeu de base). */
    public String modLine(String modId) {
        return catalog == null ? "" : catalog.modLine(modId);
    }

    /** Pourquoi un siège ne peut pas commencer maintenant (une expédition est en cours), ou null. */
    public String blocksSiege() {
        RaidRun r = run;
        return r == null ? null : "une expédition est en cours (contre " + r.target.name() + ")";
    }

    /* =========================
       REPÉRAGE D'UN SITE (essais et expéditions)
       ========================= */

    /** Site repéré, généré et balayé ; {@code key} : clé de ses tickets de chunk. */
    private record Scouted(RaidSites.Site site, GeneratedStructure generated, Arena arena, int key) {}

    /**
     * Repère l'exemplaire naturel non exploré le plus proche d'un point de départ neuf, génère
     * son chunk de départ puis toute sa boîte et {@code margin} blocs autour (en tâche de fond),
     * et la balaie. Rien n'est posé : la structure est celle que son mod a fait naître. Chaque étape
     * vérifie {@code wanted} (expédition arrêtée entre-temps : on s'arrête là, tickets rendus).
     */
    private void scout(CommandSender who, World w, RaidSites sites, String id, RaidTarget target, Structure structure, int margin,
                       IntConsumer progress, BooleanSupplier wanted, Consumer<Scouted> ok, Consumer<String> fail) {
        sites.locate(structure, id, found -> {
            plugin.getLogger().info("⚔ [Raids] Recherche de " + id + " en " + found.millis() + " ms : "
                    + (found.error() == null ? "x " + found.start().getBlockX() + ", z " + found.start().getBlockZ()
                    + " (" + found.how() + ")" : found.error()));
            if (!wanted.getAsBoolean()) return;
            if (found.error() != null) {
                if (target != null) catalog.markNotFound(target);
                fail.accept(found.error());
                return;
            }
            Location start = found.start();
            int key = sites.history().size() + 1;
            tell(who, "§7Repérée à §fx " + start.getBlockX() + ", z " + start.getBlockZ() + " §8(" + found.how()
                    + ", " + found.millis() + " ms)§7 : génération du départ…");
            sites.whenLoaded(w, start.getBlockX() >> 4, start.getBlockZ() >> 4, err -> {
                if (!wanted.getAsBoolean()) return;
                if (err != null) {
                    fail.accept("génération impossible : " + err);
                    return;
                }
                GeneratedStructure generated = sites.generatedAt(start, structure);
                if (generated == null) {
                    fail.accept("chunk de départ généré, mais aucune structure " + id + " n'y est enregistrée");
                    return;
                }
                BoundingBox box = generated.getBoundingBox();
                long t0 = System.currentTimeMillis();
                tell(who, "§7Structure de §f" + size(box) + "§7 : génération de sa zone (marge " + margin + " blocs)…");
                sites.prepare(key, box, margin, progress, err2 -> {
                    if (!wanted.getAsBoolean()) {
                        sites.release(key);
                        return;
                    }
                    if (err2 != null) {
                        sites.release(key);
                        fail.accept("génération impossible : " + err2);
                        return;
                    }
                    RaidSites.Site site = sites.record(id, start, box, found.millis());
                    tell(who, "§7Site prêt en " + seconds(System.currentTimeMillis() - t0) + " ; balayage…");
                    scanner.scan(id, w, generated, arena -> {
                        plugin.getLogger().info("⚔ [Raids] Balayage de " + id + " (site n°" + site.number() + ") :");
                        for (String line : arena.describe()) plugin.getLogger().info("⚔ [Raids]   " + line);
                        if (!wanted.getAsBoolean()) {
                            sites.release(key);
                            return;
                        }
                        ok.accept(new Scouted(site, generated, arena, key));
                    });
                });
            });
        });
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
        if (run != null) return "Une expédition est en cours : /city raid stop d'abord.";
        sites.reset();
        World w = surface.open(true);
        lastArena = null;
        return w == null ? "Monde recréé sans succès (voir la console)." : null;
    }

    /**
     * Essai (/city raid essai) : un site repéré, généré et balayé, sans expédition.
     *
     * @return null si l'essai est lancé (la suite arrive par messages), sinon la raison du refus
     */
    public String trial(CommandSender who, String id) {
        if (surface == null || sites == null) return "Raids désactivés (raids.active) ou monde indisponible.";
        if (busy) return "Un essai est déjà en cours.";
        if (run != null) return "Une expédition est en cours : attendez son retour.";
        if (surface.world() == null) return "Monde des raids absent (voir la console).";
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
        scout(who, surface.world(), sites, id, target, structure, config.margin, progressBar(who, "Génération du site"), () -> true, s -> {
            busy = false;
            lastArena = s.arena();
            sites.release(s.key()); // l'essai est fini : rien n'est gardé chargé (un joueur sur place charge ce qu'il voit)
            tell(who, "§6⚔ Balayage §8(détail en console)§6 :");
            for (String line : s.arena().describe()) tell(who, "§7  " + line);
            tellTp(who, s.site());
        }, err -> {
            busy = false;
            tell(who, "§c❌ " + capitalize(err) + ".");
        });
        return null;
    }

    /** Avancement dans la barre d'action du joueur qui a lancé l'essai. */
    private static IntConsumer progressBar(CommandSender who, String label) {
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
     * et sur le fil principal si elle n'aboutit pas ; l'entrée dans le monde des raids est permise
     * le temps qu'elle dure. Le résultat est noté en console.
     */
    private void move(Player player, Location to, Consumer<Boolean> done) {
        // Chunk d'arrivée déjà prêt (site : chargé en tâche de fond ; retour : terrain existant) : lecture seule
        to.getChunk();
        String from = player.getWorld().getName();
        UUID id = player.getUniqueId();
        participants.allow(id);
        player.teleportAsync(to).whenComplete((async, err) -> Bukkit.getScheduler().runTask(plugin, () -> {
            boolean arrived = player.isOnline() && player.getWorld().equals(to.getWorld());
            boolean sync = false;
            if (!arrived && player.isOnline()) {
                sync = player.teleport(to);
                arrived = player.getWorld().equals(to.getWorld());
            }
            participants.disallow(id);
            plugin.getLogger().info("⚔ [Raids] Téléportation de " + player.getName() + " : " + from + " → "
                    + to.getWorld().getName() + " (" + to.getBlockX() + ", " + to.getBlockY() + ", " + to.getBlockZ() + ") "
                    + (arrived ? "réussie" : "en échec") + " (tâche de fond : " + (err != null ? "erreur " + err.getMessage() : async)
                    + (sync ? ", fil principal : true" : "") + ").");
            done.accept(arrived);
        }));
    }

    /* =========================
       PHASE R1 : EXPÉDITION
       ========================= */

    /**
     * /city raid start : une expédition contre une cible du catalogue. Le site est repéré et
     * généré d'abord (« les éclaireurs repèrent le terrain »), puis le rassemblement commence
     * autour d'Alderic — ou de l'administrateur avec {@code here} (« ici »).
     *
     * @param arg   clé de raids.yml ou identifiant de la structure
     * @param force administration : sans tenir compte du déblocage ni de la recharge du niveau
     * @return null si l'expédition est lancée, sinon la raison du refus
     */
    public String startRaid(CommandSender sender, String arg, boolean here, boolean force) {
        if (surface == null || sites == null || surface.world() == null) return "Raids désactivés (raids.active) ou monde indisponible.";
        if (run != null) return "Une expédition est déjà en cours (contre " + run.target.name() + ").";
        if (busy) return "Un essai est en cours : attendez la fin.";
        if (siegeRunning.getAsBoolean()) return "Un siège est en cours : l'expédition attendra la fin du siège.";
        RaidTarget target = catalog.get(arg);
        if (target == null) target = catalog.byStructure(arg.contains(":") ? arg : "minecraft:" + arg);
        if (target == null) return "Cible inconnue : " + arg + " (clé de raids.yml ou identifiant de structure).";
        RaidCatalog.Hidden hidden = catalog.reason(target);
        if (hidden != null) return "Cible indisponible : " + hidden.group() + ".";
        Dim dim = dimOf(target);
        if (dim == null) return "Le monde des raids " + (target.terrain().contains(RaidTarget.NETHER) ? "du Nether" : "de l'End")
                + " est désactivé ou indisponible (raids.mondes).";
        // Ville fortifiée : un village qui naît ici, au hasard parmi les siens
        String mainId = target.city() != null ? villageFor(target, dim.placements()) : target.mainStructure();
        if (mainId == null) return target.name() + " : aucun de ses villages ne naît dans le monde des raids.";
        Structure structure = structure(mainId);
        if (structure == null) return "Structure inconnue sur ce serveur : " + mainId + ".";
        if (dim.placements() != null && Boolean.FALSE.equals(dim.placements().canGenerate(structure))) {
            return target.name() + " ne naît jamais dans le monde des raids (biomes de mods absents).";
        }
        String closed = force ? null : levelClosed(target.level());
        if (closed != null) return closed;
        Location center;
        String leader;
        if (here) {
            if (!(sender instanceof Player p)) return "« ici » : en jeu seulement.";
            center = p.getLocation();
            leader = p.getName();
        } else {
            center = mayorAt.get();
            leader = "Alderic";
            if (center == null || center.getWorld() == null) {
                return "Alderic est introuvable : ajoutez « ici » pour rassembler l'expédition autour de vous.";
            }
        }
        if (isRaidWorld(center.getWorld())) return "Le rassemblement se fait à Valmont, pas dans le monde des raids.";

        RaidRun r = new RaidRun(target, config.level(target.level()), center.clone(), leader);
        r.world = dim.world().world();
        r.sites = dim.sites();
        run = r;
        // Lancée par un joueur auprès d'Alderic : le détail technique du repérage va à la console
        CommandSender tech = sender instanceof Player p && !p.hasPermission(com.citycore.command.CityPermissions.ADMIN_RAID)
                ? Bukkit.getConsoleSender() : sender;
        tell(tech, "§6⚔ Expédition contre §f" + target.name() + " §8(" + target.mainStructure() + ", niveau "
                + r.level.number() + " — " + r.level.name() + ")§6 : les éclaireurs repèrent le terrain…");
        Bukkit.broadcastMessage("§6⚔ Les éclaireurs de Valmont partent repérer §f" + target.name()
                + "§6… §8(le serveur peut marquer une courte pause)");
        // Camp d'un mod (camp de bandits…) : la zone générée autour de la cible doit le contenir
        r.campModel = RaidCamp.pick(config.campModels, plugin.getLogger());
        int reach = r.campModel != null ? r.campModel.reach() : 4;
        int margin = Math.max(config.margin, config.campDistance + reach + 6);
        if (target.city() != null) {
            // Tours et siège sont posés hors du village : la zone générée doit les contenir
            int span = 0;
            for (String id : List.of(target.city().tower(), target.city().seat())) {
                org.bukkit.structure.Structure t = RaidTemplates.load(id);
                if (t != null) span = Math.max(span, RaidTemplates.span(t));
            }
            margin = Math.max(margin, span + 20);
        }
        String name = target.name();
        int zoneMargin = margin;
        RaidTarget aim = target;
        Structure kind = structure;
        // Recherche une demi-seconde plus tard : la recherche du jeu fige le serveur quelques
        // secondes, le menu d'Alderic se ferme et l'annonce part d'abord (pas de clics répétés)
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (run != r || r.cancelled) return;
            scout(tech, r.world, r.sites, mainId, aim, kind, zoneMargin, pct -> {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (!isRaidWorld(p.getWorld())) {
                        p.sendActionBar(Component.text("⚔ Les éclaireurs repèrent le terrain : " + pct + " %", NamedTextColor.GOLD));
                    }
                }
            }, () -> run == r && !r.cancelled, s -> siteReady(r, s, tech), err -> {
                if (run == r) run = null;
                tell(tech, "§c❌ Expédition annulée : " + err + ".");
                Bukkit.broadcastMessage("§7⚔ L'expédition contre " + name + " n'aura pas lieu : les éclaireurs n'ont pas trouvé le terrain.");
            });
        }, 10L);
        return null;
    }

    /** Site prêt : camp, héraut, puis rassemblement. */
    private void siteReady(RaidRun r, Scouted s, CommandSender sender) {
        r.site = s.site();
        r.arena = s.arena();
        r.siteKey = s.key();
        r.camp = RaidCamp.build(r.world, s.site().box(), config.campDistance, r.campModel);
        if (r.camp == null) {
            tell(sender, "§c❌ Expédition annulée : pas de place pour le camp autour de la cible.");
            cleanup(r, "pas de place pour le camp");
            return;
        }
        herald.spawn(r.camp.herald(), config.heraldSkin);
        // Ville fortifiée (R6) : siège du pouvoir et tours de la faction autour du village
        if (r.target.city() != null) {
            r.city = RaidCity.build(r.world, r.arena.box(), r.camp, r.target.city(), plugin.getLogger());
            if (r.city != null) r.arena = r.arena.extend(r.city.zone(), r.city.banners(), r.city.doors(), r.city.containers());
            plugin.getLogger().info("⚔ [Raids] Ville " + r.target.name() + " : " + (r.city == null ? "ni tour ni siège posés"
                    : r.city.towers().size() + " tour(s)" + (r.city.seat() != null ? " et le siège du pouvoir" : ", sans siège"))
                    + " autour de " + s.site().structure() + ".");
        }
        assault.prepare(r); // coffres vidés, golems retirés, cages neutralisées sous le niveau IV
        // Cible flottante (château dans le ciel) : tour d'échelles jusqu'à son bord le plus proche
        if (r.target.floating()) {
            Location landing = RaidLair.climb(r.world, r.arena, r.camp);
            plugin.getLogger().info("⚔ [Raids] Cible flottante " + r.target.name() + " : " + (landing == null
                    ? "aucun palier repéré (pas de tour)" : "tour d'échelles jusqu'à y " + landing.getBlockY()) + ".");
        }
        // Repaire enfoui (R8) : puits à échelles depuis le camp, galerie jusqu'à la première salle
        if (r.target.underground()) {
            r.lairEntry = RaidLair.dig(r.world, r.arena, r.camp);
            plugin.getLogger().info("⚔ [Raids] Repaire enfoui " + r.target.name() + " : " + (r.lairEntry == null
                    ? "aucune salle repérée (pas de galerie)" : "galerie creusée jusqu'à x " + r.lairEntry.getBlockX()
                    + ", y " + r.lairEntry.getBlockY() + ", z " + r.lairEntry.getBlockZ()) + ".");
        }
        plugin.getLogger().info("⚔ [Raids] Camp de l'expédition contre " + r.target.name() + " : x " + r.camp.spawn().getBlockX()
                + ", y " + r.camp.spawn().getBlockY() + ", z " + r.camp.spawn().getBlockZ() + (r.camp.water() ? " (radeau)"
                : r.camp.model() != null ? " (" + r.camp.model() + ")" : " (camp du plugin)") + ".");
        beginGathering(r);
    }

    /** Rassemblement : annonce, Alderic reste sur place, cercle de particules, compte à rebours. */
    private void beginGathering(RaidRun r) {
        r.state = RaidRun.State.RASSEMBLEMENT;
        r.phaseEndsAt = System.currentTimeMillis() + config.gatherSeconds * 1000L;
        mayorStay.run();
        String join = r.leader.equals("Alderic") ? "rejoignez Alderic" : "rejoignez " + r.leader;
        Bukkit.broadcastMessage("§6⚔ " + r.leader + " lève une expédition contre §f" + r.target.name() + "§6 : "
                + join + " (" + config.gatherSeconds + " s) !");
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendTitle("§6⚔ Expédition", "§f" + r.target.name() + " §7— " + join, 10, 60, 20);
            p.playSound(p.getLocation(), Sound.BLOCK_BELL_USE, 1f, 1f);
        }
        r.task = Bukkit.getScheduler().runTaskTimer(plugin, () -> tick(r), 10L, 10L);
    }

    /** Une demi-seconde du raid en cours. */
    private void tick(RaidRun r) {
        if (run != r) {
            if (r.task != null) r.task.cancel();
            return;
        }
        long now = System.currentTimeMillis();
        switch (r.state) {
            case RASSEMBLEMENT -> {
                drawCircle(r);
                List<Player> inCircle = inCircle(r);
                long left = Math.max(0, (r.phaseEndsAt - now + 999) / 1000);
                for (Player p : r.gather.getWorld().getPlayers()) {
                    if (p.getLocation().distanceSquared(r.gather) > Math.pow(config.gatherRadius + 40, 2)) continue;
                    p.sendActionBar(Component.text("⚔ Départ contre " + r.target.name() + " dans " + left + " s — dans le cercle : "
                            + inCircle.size(), NamedTextColor.GOLD));
                }
                if (now >= r.phaseEndsAt) deploy(r, inCircle);
            }
            case ASSAUT -> {
                if (++r.ticks % 4 == 0) {
                    assault.aggro(r);
                    assault.reinforce(r);
                }
                assault.mark(r); // place à tenir : cercle et colonne de lumière
                updateAssaultBar(r, now);
                if (r.enrolled.stream().noneMatch(this::inRaidWorld)) finish(r, "plus aucun joueur engagé", false);
                else if (now >= r.phaseEndsAt) finish(r, "temps écoulé", false);
            }
            case BILAN -> {
                updateBar(r, now, (r.victory ? "§6⚔ Victoire · " : "§7⚔ ") + "§6Retour à Valmont dans §f", config.bilanSeconds * 1000L);
                if (now >= r.phaseEndsAt || r.enrolled.stream().noneMatch(this::inRaidWorld)) bringBack(r);
            }
            default -> { }
        }
    }

    private boolean inRaidWorld(UUID id) {
        Player p = Bukkit.getPlayer(id);
        return p != null && isRaidWorld(p.getWorld());
    }

    /** Joueurs dans le cercle du rassemblement : même monde, vivants, pas spectateurs. */
    private List<Player> inCircle(RaidRun r) {
        List<Player> out = new ArrayList<>();
        double r2 = (double) config.gatherRadius * config.gatherRadius;
        for (Player p : r.gather.getWorld().getPlayers()) {
            if (p.isDead() || p.getGameMode() == GameMode.SPECTATOR) continue;
            if (p.getLocation().distanceSquared(r.gather) <= r2) out.add(p);
        }
        return out;
    }

    /** Cercle doré autour du centre du rassemblement. */
    private void drawCircle(RaidRun r) {
        World w = r.gather.getWorld();
        Particle.DustOptions gold = new Particle.DustOptions(Color.fromRGB(241, 207, 94), 1.4f);
        int points = Math.max(24, config.gatherRadius * 6);
        for (int i = 0; i < points; i++) {
            double a = 2 * Math.PI * i / points;
            w.spawnParticle(Particle.DUST, r.gather.getX() + Math.cos(a) * config.gatherRadius, r.gather.getY() + 0.3,
                    r.gather.getZ() + Math.sin(a) * config.gatherRadius, 1, 0, 0, 0, 0, gold);
        }
    }

    /**
     * Fin du compte à rebours : les joueurs du cercle sont enrôlés (position de retour notée), puis
     * partent au camp, face à la cible. Trop peu de joueurs : l'expédition est annulée.
     */
    private void deploy(RaidRun r, List<Player> players) {
        if (players.size() < config.minPlayers) {
            Bukkit.broadcastMessage("§7⚔ L'expédition contre " + r.target.name() + " est annulée : "
                    + (players.isEmpty() ? "personne dans le cercle." : "il fallait au moins " + config.minPlayers + " joueurs."));
            cleanup(r, "personne dans le cercle");
            return;
        }
        r.state = RaidRun.State.ASSAUT;
        r.assaultMillis = r.level.chronoMinutes() * 60_000L;
        r.phaseEndsAt = System.currentTimeMillis() + r.assaultMillis;
        r.bar = Bukkit.createBossBar("", BarColor.RED, BarStyle.SOLID); // rouge : le panneau de quêtes masque les barres blanches
        int i = 0;
        for (Player p : players) {
            participants.saveReturn(p, r.id);
            r.enrolled.add(p.getUniqueId());
            r.participants.add(p.getUniqueId());
            r.bar.addPlayer(p);
            Location to = spread(r.camp.spawn(), i++);
            move(p, to, ok -> {
                if (!ok) {
                    r.enrolled.remove(p.getUniqueId());
                    participants.deleteReturn(p.getUniqueId());
                    if (r.bar != null) r.bar.removePlayer(p);
                    p.sendMessage("§c⚔ Départ impossible (téléportation refusée par le serveur).");
                    return;
                }
                p.sendTitle("§6⚔ " + r.target.name(), "§7" + r.level.name() + " · " + r.level.chronoMinutes() + " min", 10, 60, 20);
                p.playSound(p.getLocation(), Sound.EVENT_RAID_HORN, 1f, 1f); // cor du départ
                p.sendMessage("§7⚔ Le camp est votre point de départ : vous y reviendrez après une mort, avec toutes vos"
                        + " affaires. Le héraut vous ramènera à Valmont quand vous le voudrez.");
                objectiveLines(r).forEach(p::sendMessage);
                if (r.objective("tenir") != null) {
                    p.sendMessage("§7⚑ La place à tenir est marquée d'un cercle doré et d'une colonne de lumière : restez-y "
                            + config.holdSeconds + " s, sans défenseur dans le cercle (il rougit quand l'un d'eux y est).");
                }
                if (r.target.underground() && r.lairEntry != null) {
                    p.sendMessage("§7⚔ Le repaire est sous terre : un puits à échelles, marqué d'une lanterne, descend du camp.");
                } else if (r.camp.water()) {
                    p.sendMessage("§7⚓ Des barques sont amarrées au radeau : à l'abordage !");
                } else if (r.target.floating()) {
                    p.sendMessage("§7⚔ La cible flotte dans le ciel : une tour d'échelles, marquée d'une lanterne, y monte depuis le sol.");
                }
            });
        }
        // Radeau (abordage) : une barque par joueur, et une de plus, amarrées autour
        if (r.camp.water()) {
            int boats = Math.min(r.camp.moorings().size(), players.size() + 1);
            for (int b = 0; b < boats; b++) {
                Location m = r.camp.moorings().get(b);
                m.getWorld().spawn(m, org.bukkit.entity.Boat.class, boat -> boat.addScoreboardTag(RaidCamp.TAG_CAMP));
            }
        }
        // Défenseurs (force du niveau, plus nombreux avec les joueurs et la renommée) et objectifs
        assault.begin(r, players.size(), progress.victories(r.level.number()));
        updateAssaultBar(r, System.currentTimeMillis());
        Bukkit.broadcastMessage("§6⚔ L'expédition part : §f" + players.size() + " joueur" + (players.size() > 1 ? "s" : "")
                + "§6 contre " + r.target.name() + ".");
        plugin.getLogger().info("⚔ [Raids] Expédition contre " + r.target.name() + " : " + players.size() + " joueur(s), "
                + r.level.chronoMinutes() + " min.");
    }

    /** Places au camp, autour du point de départ (le plancher fait 5 × 5). */
    private static Location spread(Location spawn, int i) {
        int[][] offsets = {{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {-1, -1}, {1, -1}, {-1, 1}};
        int[] o = offsets[i % offsets.length];
        return spawn.clone().add(o[0], 0, o[1]);
    }

    private void updateBar(RaidRun r, long now, String prefix, long total) {
        if (r.bar == null) return;
        long left = Math.max(0, r.phaseEndsAt - now);
        r.bar.setTitle(prefix + clock(left));
        r.bar.setProgress(total <= 0 ? 0 : Math.max(0, Math.min(1, (double) left / total)));
        for (UUID id : r.enrolled) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && !r.bar.getPlayers().contains(p)) r.bar.addPlayer(p); // reconnecté
        }
    }

    /**
     * Fin de l'assaut : victoire (défiance à zéro, l'adversaire capitule) ou défaite (chrono écoulé,
     * plus personne) ; bilan, récompenses, recharge du niveau, puis le temps de souffler.
     */
    private void finish(RaidRun r, String reason, boolean victory) {
        long now = System.currentTimeMillis();
        long duration = Math.max(0, r.assaultMillis - Math.max(0, r.phaseEndsAt - now));
        r.state = RaidRun.State.BILAN;
        r.endReason = reason;
        r.victory = victory;
        r.phaseEndsAt = now + config.bilanSeconds * 1000L;
        if (victory) assault.capitulate(r);
        List<String> bonuses = bonuses(r, duration);
        List<String> bilan = settle(r, duration, bonuses);
        progress.recordEnd(r, victory, duration, String.join(",", bonuses));
        if (r.bar != null) r.bar.setColor(victory ? BarColor.YELLOW : BarColor.RED);
        for (UUID id : r.participants) {
            Player p = Bukkit.getPlayer(id);
            if (p == null) continue;
            bilan.forEach(p::sendMessage);
            if (!isRaidWorld(p.getWorld())) continue;
            if (victory) {
                p.sendTitle("§6⚔ Victoire !", "§f" + r.target.name() + " §7capitule", 10, 70, 20);
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
            } else {
                p.sendTitle("§7⚔ Fin de l'expédition", "§7" + capitalize(reason), 10, 60, 20);
                p.playSound(p.getLocation(), Sound.BLOCK_BELL_USE, 1f, 0.6f);
            }
            p.sendMessage("§7⚔ Retour à Valmont dans " + config.bilanSeconds + " s. Le héraut peut vous ramener tout de suite.");
        }
        Bukkit.broadcastMessage(victory ? "§6🏆 Victoire ! L'expédition de Valmont a pris §f" + r.target.name() + "§6."
                : "§7⚔ L'expédition contre " + r.target.name() + " rentre sans victoire (" + reason + ").");
        plugin.getLogger().info("⚔ [Raids] Fin de l'assaut contre " + r.target.name() + " : " + (victory ? "victoire" : "défaite")
                + " (" + reason + "), " + clock(duration) + ", défiance " + r.defiance() + " %, " + r.defendersKilled + "/"
                + r.defendersTotal + " défenseur(s) abattu(s)" + (bonuses.isEmpty() ? "" : ", bonus " + String.join(", ", bonuses)) + ".");
        if (config.bilanSeconds == 0) bringBack(r);
    }

    /** Bonus réussis (victoire seulement) : épargner les civils, sans perte, éclair. */
    private List<String> bonuses(RaidRun r, long duration) {
        List<String> out = new ArrayList<>();
        if (!r.victory) return out;
        if (r.target.bonuses().contains("epargner") && r.civiliansKilled == 0) out.add("épargner");
        if (r.target.bonuses().contains("sans-perte") && r.deaths.isEmpty()) out.add("sans perte");
        if (r.target.bonuses().contains("eclair") && duration <= r.assaultMillis * config.eclairFraction) out.add("éclair");
        return out;
    }

    /**
     * Bilan dans le chat ; en cas de victoire, récompenses : caisse de la ville et bourse de chaque
     * combattant (parti au camp et resté jusqu'au bout, ou qui a abattu au moins un défenseur),
     * + bonus par bonus réussi, + renommée du niveau.
     */
    private List<String> settle(RaidRun r, long duration, List<String> bonuses) {
        List<String> out = new ArrayList<>();
        out.add("§6⚔ Bilan — §f" + r.target.name() + " §8(niveau " + RaidProgress.roman(r.level.number()) + " — " + r.level.name() + ")");
        out.add((r.victory ? "§a§lVictoire" : "§7§lDéfaite") + " §7· durée §f" + clock(duration) + " §7· défiance §f" + r.defiance()
                + " %§7 · défenseurs abattus §f" + r.defendersKilled + "/" + r.defendersTotal);
        for (RaidRun.Objective o : r.objectives) {
            out.add((o.complete() ? " §a✔ " : " §8✘ ") + "§7" + o.label
                    + (o.total > 1 ? " §8(" + Math.min(o.done, o.total) + "/" + o.total + ")" : ""));
        }
        List<java.util.Map.Entry<UUID, Integer>> best = new ArrayList<>(r.kills.entrySet());
        best.sort((a, b) -> b.getValue() - a.getValue());
        if (!best.isEmpty()) {
            List<String> names = new ArrayList<>();
            for (int i = 0; i < Math.min(3, best.size()); i++) {
                String n = Bukkit.getOfflinePlayer(best.get(i).getKey()).getName();
                names.add((n == null ? "?" : n) + " (" + best.get(i).getValue() + ")");
            }
            out.add("§7Meilleurs combattants : §f" + String.join(", ", names));
        }
        if (!r.victory) {
            out.add("§7Pas de récompense cette fois ; le niveau se recharge deux fois plus vite.");
            return out;
        }
        double mult = (1 + config.bonusRate * bonuses.size()) * (1 + r.renown);
        int city = (int) Math.round(r.level.cityReward() * mult);
        int each = (int) Math.round(r.level.playerReward() * mult);
        List<UUID> paid = new ArrayList<>();
        for (UUID id : r.participants) {
            if (r.enrolled.contains(id) || r.kills.getOrDefault(id, 0) > 0) paid.add(id);
        }
        try {
            cityPurse.accept(city);
            for (UUID id : paid) playerPurse.accept(id, (double) each);
        } catch (RuntimeException e) {
            plugin.getLogger().warning("⚔ [Raids] Récompense non versée : " + e.getMessage());
        }
        if (!bonuses.isEmpty()) {
            out.add("§7Bonus : §f" + String.join(", ", bonuses) + " §8(+" + Math.round(config.bonusRate * bonuses.size() * 100) + " %)");
        }
        if (r.renown > 0) out.add("§7Renommée du niveau : §f+" + Math.round(r.renown * 100) + " %");
        out.add("§6Récompense : §f+" + city + " coins §7à la caisse de Valmont, §f+" + each + " coins §7par combattant"
                + " §8(" + paid.size() + ")");
        return out;
    }

    /**
     * Un objectif a avancé (défenseur abattu, bannière, cage, cloche, captif) : annonce de ceux qui
     * viennent d'être remplis, victoire quand tous le sont (au tick suivant : hors de l'événement).
     */
    void progressed(RaidRun r) {
        if (run != r || r.state != RaidRun.State.ASSAUT) return;
        for (RaidRun.Objective o : r.objectives) {
            if (!o.complete() || o.announced) continue;
            o.announced = true;
            for (UUID id : r.enrolled) {
                Player p = Bukkit.getPlayer(id);
                if (p == null || !isRaidWorld(p.getWorld())) continue;
                p.sendTitle("", "§a✔ " + o.label, 5, 40, 10);
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.2f);
            }
        }
        updateAssaultBar(r, System.currentTimeMillis());
        if (r.allComplete()) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (run == r && r.state == RaidRun.State.ASSAUT) finish(r, "l'adversaire capitule", true);
            });
        }
    }

    /** Barre rouge de l'assaut : défiance de l'adversaire et chrono. */
    private void updateAssaultBar(RaidRun r, long now) {
        if (r.bar == null) return;
        int d = r.defiance();
        r.bar.setTitle("§c⚔ " + r.target.name() + " §7· Défiance §f" + d + " % §7· " + clock(Math.max(0, r.phaseEndsAt - now)));
        r.bar.setProgress(Math.max(0, Math.min(1, d / 100.0)));
        for (UUID id : r.enrolled) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && !r.bar.getPlayers().contains(p)) r.bar.addPlayer(p); // reconnecté
        }
    }

    /* =========================
       SUIVI DES QUÊTES (QuestHUD) : défiance et objectifs en tête
       ========================= */

    /** Libellé court d'un objectif pour le panneau de quêtes. */
    private static String shortLabel(RaidRun.Objective o) {
        return switch (o.key) {
            case "garnison" -> "Garnison";
            case "cloche" -> "Cloche";
            case "bannieres" -> "Bannières";
            case "cages" -> "Cages";
            case "liberer" -> "Captifs";
            case "tours" -> "Tours";
            case "chef" -> "Chef";
            default -> o.label;
        };
    }

    /** Lignes du panneau illustré pour un joueur engagé (assaut, bilan), vide sinon. */
    public List<com.citycore.ui.UiHud.Row> hudRows(Player p) {
        RaidRun r = run;
        List<com.citycore.ui.UiHud.Row> rows = new ArrayList<>();
        if (r == null || !r.deployed() || !r.isEnrolled(p.getUniqueId())) return rows;
        rows.add(com.citycore.ui.UiHud.Row.head("⚔ " + shorten(r.target.name(), 20), com.citycore.ui.UiHud.GOLD));
        if (r.state == RaidRun.State.BILAN) {
            rows.add(com.citycore.ui.UiHud.Row.plain(r.victory ? "Victoire !" : "Fin de l'expédition",
                    r.victory ? com.citycore.ui.UiHud.GREEN : com.citycore.ui.UiHud.CREAM));
            return rows;
        }
        int d = r.defiance();
        rows.add(com.citycore.ui.UiHud.Row.bar("Défiance", com.citycore.ui.UiHud.CREAM, d + " %", com.citycore.ui.UiHud.CREAM,
                d / 100.0, true));
        for (RaidRun.Objective o : r.objectives) {
            if (o.complete()) rows.add(com.citycore.ui.UiHud.Row.plain("✔ " + shortLabel(o), com.citycore.ui.UiHud.GREEN));
            else rows.add(com.citycore.ui.UiHud.Row.value("• " + shortLabel(o), o.total > 1 ? o.done + "/" + o.total : "à faire",
                    o.mandatory ? com.citycore.ui.UiHud.GOLD : com.citycore.ui.UiHud.CREAM));
        }
        return rows;
    }

    /** Lignes du tableau de droite (sans pack) pour un joueur engagé, vide sinon. */
    public List<String> hudLines(Player p) {
        RaidRun r = run;
        List<String> lines = new ArrayList<>();
        if (r == null || !r.deployed() || !r.isEnrolled(p.getUniqueId())) return lines;
        lines.add("§c⚔ " + shorten(r.target.name(), 18));
        if (r.state == RaidRun.State.BILAN) {
            lines.add(r.victory ? "§aVictoire !" : "§7Fin de l'expédition");
        } else {
            lines.add("§7Défiance §f" + r.defiance() + " %");
            for (RaidRun.Objective o : r.objectives) {
                lines.add((o.complete() ? "§a✔ §f" : "§7• §f") + shortLabel(o)
                        + (o.complete() || o.total <= 1 ? "" : " §f" + o.done + "§7/§f" + o.total));
            }
        }
        lines.add("§r  ");
        return lines;
    }

    private static String shorten(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    /** Objectifs de l'expédition (message à l'arrivée au camp, /city raid). */
    List<String> objectiveLines(RaidRun r) {
        List<String> out = new ArrayList<>();
        if (r.objectives.isEmpty()) return out;
        out.add("§6⚔ Objectifs §7(la défiance de l'adversaire tombe à chaque objectif rempli) :");
        for (RaidRun.Objective o : r.objectives) {
            out.add((o.complete() ? " §a✔ " : " §e• ") + "§f" + o.label
                    + (o.total > 1 ? " §7" + Math.min(o.done, o.total) + "/" + o.total : "")
                    + (o.mandatory ? " §c(obligatoire)" : ""));
        }
        return out;
    }

    /**
     * Pourquoi un niveau n'est pas ouvert (chapitre, victoire au niveau précédent, recharge), ou
     * null s'il l'est.
     */
    public String levelClosed(int number) {
        RaidConfig.Level level = config.level(number);
        String lock = progress.lockReason(level);
        if (lock != null) return "Niveau " + RaidProgress.roman(number) + " fermé : " + lock + ".";
        long left = progress.cooldownUntil(number) - System.currentTimeMillis();
        if (left > 0) return "Niveau " + RaidProgress.roman(number) + " en recharge : encore " + duration(left) + ".";
        return null;
    }

    /** « 1 h 05 », « 12 min » */
    static String duration(long millis) {
        long m = (millis + 59_999) / 60_000;
        return m >= 60 ? m / 60 + " h " + String.format("%02d", m % 60) : m + " min";
    }

    /* =========================
       ÉCRANS D'ALDERIC (phase R3) : Événements → Raids → Difficulté → Cibles → Fiche
       ========================= */

    /** Les raids sont-ils ouverts (activés, monde des raids prêt) ? */
    public boolean ready() {
        return config != null && config.active && surface != null && surface.world() != null && catalog != null;
    }

    public RaidConfig.Level level(int number) { return config.level(number); }

    /** Une expédition est-elle en cours (repérage, rassemblement, assaut ou bilan) ? */
    public boolean running() { return run != null; }

    /** Cibles proposées aux joueurs pour un niveau : disponibles avec les mods installés, monde de surface. */
    public List<RaidTarget> targets(int level) {
        if (catalog == null) return List.of();
        return catalog.byLevel(level).stream().filter(t -> catalog.isAvailable(t) && dimOf(t) != null).toList();
    }

    /** Pourquoi le niveau n'est pas débloqué (chapitre de la ville, victoire au niveau précédent), ou null. */
    public String levelLock(int number) {
        return progress.lockReason(config.level(number));
    }

    /** Recharge restante du niveau (millisecondes), 0 s'il n'y en a pas. */
    public long cooldownLeft(int number) {
        return Math.max(0, progress.cooldownUntil(number) - System.currentTimeMillis());
    }

    public int victories(int number) { return progress.victories(number); }

    /** Victoires par cible (« prise ×3 »). */
    public java.util.Map<String, Integer> takes() { return progress.takes(); }

    /** Chapitre de la ville (palier). */
    public int cityChapter() { return progress.cityLevel(); }

    /** Cibles que les phases suivantes rendront jouables (en mer, enfouies, villes fortifiées), ou null. */
    public static String notYet(RaidTarget t) {
        return null;
    }

    /** Pourquoi le rassemblement ne peut pas sonner maintenant contre cette cible (bouton grisé), ou null. */
    public String cannotGather(RaidTarget t) {
        if (!ready()) return "les raids sont fermés pour l'instant";
        if (run != null) return "une expédition est déjà en cours (contre " + run.target.name() + ")";
        if (busy) return "les éclaireurs sont occupés ailleurs";
        if (siegeRunning.getAsBoolean()) return "un siège est en cours";
        String lock = levelLock(t.level());
        if (lock != null) return lock;
        long left = cooldownLeft(t.level());
        if (left > 0) return "les troupes se reposent encore " + duration(left);
        String later = notYet(t);
        if (later != null) return later;
        Dim dim = dimOf(t);
        if (dim == null) return "le monde des raids de cette cible est fermé";
        boolean found = t.city() != null ? villageFor(t, dim.placements()) != null
                : !Boolean.FALSE.equals(canGenerate(dim.placements(), structure(t.mainStructure())))
                && structure(t.mainStructure()) != null;
        if (!found) return "les éclaireurs ne trouveront pas cette cible dans les terres des raids";
        return null;
    }

    /** Joueurs dans le cercle du rassemblement autour d'Alderic (même monde, vivants, pas spectateurs). */
    public List<Player> aroundMayor() {
        Location at = mayorAt.get();
        List<Player> out = new ArrayList<>();
        if (at == null || at.getWorld() == null || config == null) return out;
        double r2 = (double) config.gatherRadius * config.gatherRadius;
        for (Player p : at.getWorld().getPlayers()) {
            if (p.isDead() || p.getGameMode() == GameMode.SPECTATOR || p.hasMetadata("NPC")) continue;
            if (p.getLocation().distanceSquared(at) <= r2) out.add(p);
        }
        return out;
    }

    /**
     * « Sonner le rassemblement » (fiche d'une cible chez Alderic) : les éclaireurs partent, puis
     * le rassemblement commence autour d'Alderic.
     *
     * @return null si c'est lancé, sinon la raison
     */
    public String gather(Player p, RaidTarget t) {
        String why = cannotGather(t);
        if (why != null) return capitalize(why) + ".";
        return startRaid(p, t.key(), false, false);
    }

    /** Un raid est-il prêt à partir (badge de l'accueil de la mairie) ? */
    public boolean raidAvailable() {
        if (!ready() || run != null || siegeRunning.getAsBoolean()) return false;
        for (int n = 1; n <= 7; n++) {
            if (levelLock(n) != null || cooldownLeft(n) > 0) continue;
            for (RaidTarget t : targets(n)) if (notYet(t) == null) return true;
        }
        return false;
    }

    /** /city raid recharge (administration) : recharges de tous les niveaux levées. */
    public void resetCooldowns() {
        progress.resetCooldowns();
    }

    /** /city raid debloquer (administration) : une victoire comptée à chaque niveau sous {@code level}. */
    public void unlock(int level) {
        progress.unlock(level);
    }

    /** Retour de chacun à sa position d'origine (auprès d'Alderic si elle est perdue), puis nettoyage. */
    private void bringBack(RaidRun r) {
        for (UUID id : new ArrayList<>(r.enrolled)) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && isRaidWorld(p.getWorld())) sendHome(r, p, "§7⚔ Retour à Valmont.");
            else {
                r.enrolled.remove(id);
                participants.deleteReturn(id); // hors ligne : retour auprès d'Alderic à la reconnexion
            }
        }
        cleanup(r, r.endReason == null ? "retour" : r.endReason);
    }

    /** Un joueur rentre chez lui : position notée au départ, sinon auprès d'Alderic. */
    private void sendHome(RaidRun r, Player p, String message) {
        UUID id = p.getUniqueId();
        Location back = participants.returnOf(id);
        Location to = back != null ? back : fallbackReturn();
        r.enrolled.remove(id);
        participants.deleteReturn(id);
        if (r.bar != null) r.bar.removePlayer(p);
        if (p.isDead()) {
            homeOnRespawn.put(id, to); // mort pendant le bilan : il rentre en réapparaissant
            return;
        }
        move(p, to, ok -> p.sendMessage(ok ? message : "§c⚔ Retour refusé par le serveur : demandez à un administrateur."));
    }

    /** Où réapparaît un joueur mort au moment du retour (null : rien de prévu). */
    Location takeHomeOnRespawn(UUID id) {
        return homeOnRespawn.remove(id);
    }

    /**
     * Fin de l'expédition : héraut et barre retirés, occupants de la cible retirés (le monde est
     * jetable, mais rien du raid ne reste), tickets de chunk rendus.
     */
    private void cleanup(RaidRun r, String reason) {
        if (r.task != null) r.task.cancel();
        if (r.bar != null) r.bar.removeAll();
        herald.remove();
        assault.clear(); // soldats de l'armée ennemie retirés
        if (r.site != null && r.world != null) {
            int removed = 0;
            BoundingBox zone = r.arena != null ? r.arena.box() : r.site.box();
            for (Entity e : r.world.getNearbyEntities(zone.clone().expand(config.margin + 16))) {
                if (e instanceof Player) continue;
                if (e.getScoreboardTags().contains(ArenaScanner.TAG_DEFENDER) || e.getScoreboardTags().contains(ArenaScanner.TAG_CIVIL)
                        || e.getScoreboardTags().contains(RaidCamp.TAG_CAMP)) {
                    e.remove();
                    removed++;
                }
            }
            if (removed > 0) plugin.getLogger().info("⚔ [Raids] " + removed + " occupant(s) de la cible retiré(s).");
        }
        if (r.siteKey >= 0 && r.sites != null) r.sites.release(r.siteKey);
        if (run == r) run = null;
        plugin.getLogger().info("⚔ [Raids] Expédition contre " + r.target.name() + " terminée (" + reason + ").");
    }

    /**
     * /city raid stop (administration) : pendant le repérage ou le rassemblement, l'expédition est
     * annulée ; pendant l'assaut ou le bilan, chacun rentre tout de suite.
     */
    public boolean stopRaid() {
        RaidRun r = run;
        if (r == null) return false;
        if (r.deployed()) {
            r.endReason = "arrêtée par l'administration";
            bringBack(r);
        } else {
            r.cancelled = true;
            Bukkit.broadcastMessage("§7⚔ L'expédition contre " + r.target.name() + " est annulée.");
            cleanup(r, "annulée");
        }
        return true;
    }

    /**
     * Retraite (héraut, /city raid quitter) : le joueur rentre ; l'expédition continue sans lui.
     *
     * @return null si c'est fait, sinon la raison du refus
     */
    public String retreat(Player p) {
        RaidRun r = run;
        if (r == null || !r.deployed() || !r.isEnrolled(p.getUniqueId()) || !isRaidWorld(p.getWorld())) {
            return "Vous n'êtes pas en expédition.";
        }
        sendHome(r, p, r.state == RaidRun.State.BILAN ? "§7⚔ Retour à Valmont."
                : "§7⚔ Vous rentrez à Valmont ; l'expédition continue sans vous.");
        return null;
    }

    /** Un joueur engagé a quitté le monde des raids par un autre moyen : il est rentré de lui-même. */
    void leftOnTheirOwn(Player p) {
        RaidRun r = run;
        if (r == null || !r.isEnrolled(p.getUniqueId())) return;
        r.enrolled.remove(p.getUniqueId());
        participants.deleteReturn(p.getUniqueId());
        if (r.bar != null) r.bar.removePlayer(p);
    }

    /** Clic sur le héraut : retraite pendant l'assaut, retour pendant le bilan. */
    void heraldClicked(Player p) {
        RaidRun r = run;
        boolean engaged = r != null && r.deployed() && r.isEnrolled(p.getUniqueId());
        if (!engaged) {
            TypewriterUtil.play(plugin, p, List.of("§6Héraut §f: §oJe ne sers que l'expédition, voyageur."), null);
            return;
        }
        boolean bilan = r.state == RaidRun.State.BILAN;
        String line = bilan ? "§6Héraut §f: §oC'est fini pour aujourd'hui. On rentre à Valmont ?"
                : "§6Héraut §f: §oLe camp tient bon. Si vous voulez rentrer à Valmont, on vous escorte.";
        TypewriterUtil.play(plugin, p, List.of(line), () -> {
            if (!p.isOnline()) return;
            p.sendMessage(Component.text(bilan ? "[Rentrer à Valmont]" : "[Battre en retraite]", NamedTextColor.GOLD)
                    .clickEvent(ClickEvent.runCommand("/city raid quitter"))
                    .hoverEvent(HoverEvent.showText(Component.text(bilan ? "Retour à Valmont maintenant"
                            : "Rentrer à Valmont ; l'expédition continue sans vous"))));
        });
    }

    /** /city raid (joueurs) : l'expédition en cours. */
    public List<String> status() {
        List<String> out = new ArrayList<>();
        RaidRun r = run;
        if (r == null) {
            out.add("§7⚔ Aucune expédition en cours.");
            return out;
        }
        long left = Math.max(0, r.phaseEndsAt - System.currentTimeMillis());
        String step = switch (r.state) {
            case PREPARATION -> "les éclaireurs repèrent le terrain";
            case RASSEMBLEMENT -> "rassemblement autour de " + r.leader + ", départ dans " + clock(left);
            case ASSAUT -> "assaut, " + clock(left) + " restantes";
            case BILAN -> "retour à Valmont dans " + clock(left);
        };
        out.add("§6⚔ Expédition contre §f" + r.target.name() + " §8(niveau " + r.level.number() + " — " + r.level.name() + ")");
        out.add("§7" + capitalize(step) + (r.enrolled.isEmpty() ? "" : " · " + r.enrolled.size() + " joueur(s) engagé(s)") + ".");
        if (r.state == RaidRun.State.ASSAUT) {
            out.add("§7Défiance de l'adversaire : §f" + r.defiance() + " %§7 · défenseurs abattus §f" + r.defendersKilled + "/" + r.defendersTotal);
            out.addAll(objectiveLines(r));
        }
        return out;
    }

    /** /city raid zones : monde, recherches, essais et expédition en cours. */
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
        if (run != null) status().forEach(to::sendMessage);
        if (sites.history().isEmpty()) {
            to.sendMessage("§8Aucun site depuis le démarrage : /city raid essai <structure>.");
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

    /** « 11:42 » */
    private static String clock(long millis) {
        long s = (millis + 999) / 1000;
        return String.format("%02d:%02d", s / 60, s % 60);
    }

    private static String capitalize(String s) {
        return s == null || s.isEmpty() ? "" : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static void tell(CommandSender who, String msg) {
        if (who instanceof Player p && !p.isOnline()) return;
        who.sendMessage(msg);
    }
}
