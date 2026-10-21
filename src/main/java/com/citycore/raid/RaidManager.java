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
import java.util.function.IntConsumer;
import java.util.function.Supplier;

/**
 * Raids de Valmont (plan « Raids », docs/PLAN_RAIDS.md) : monde réservé, sites, catalogue
 * raids.yml, déroulé d'une expédition.
 *
 * Phase R0 : monde de surface avec ses structures naturelles, essais (repérage d'un exemplaire
 * naturel, génération de sa zone en tâche de fond, balayage). Phase R2 : catalogue et contrôle des
 * mods. Phase R1 : une expédition (lancée en commande, les écrans d'Alderic viendront en R3) —
 * repérage du site, camp et héraut, rassemblement autour d'Alderic, départ des joueurs du cercle,
 * assaut au chrono du niveau (sans objectifs avant R4), bilan, retour de chacun chez lui.
 */
public final class RaidManager {

    private final JavaPlugin plugin;
    private final ArenaScanner scanner;
    private final RaidParticipants participants;
    private final RaidHerald herald = new RaidHerald();

    private RaidConfig config;
    private RaidCatalog catalog;
    private RaidWorld surface;
    private RaidSites sites;

    /** Placements possibles des structures dans le monde de surface (null : réponse indisponible). */
    private NaturalPlacements placements;
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
    }

    public void setMayorSpot(Supplier<Location> mayorSpot) { this.mayorSpot = mayorSpot; }
    public void setMayor(Supplier<Location> at, Runnable stay) { this.mayorAt = at; this.mayorStay = stay; }
    public void setSiegeRunning(BooleanSupplier siegeRunning) { this.siegeRunning = siegeRunning; }

    /** Écouteur des joueurs dans le monde des raids (à enregistrer). */
    public org.bukkit.event.Listener participants() { return participants; }

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
        herald.remove();
        if (sites != null) sites.releaseAll();
        if (surface != null && config.resetAtStartup) surface.discard();
    }

    public RaidConfig config()   { return config; }
    public RaidCatalog catalog() { return catalog; }
    public RaidWorld surface()   { return surface; }
    public RaidSites sites()     { return sites; }
    public boolean busy()        { return busy; }
    public Arena lastArena()     { return lastArena; }
    RaidRun run()                { return run; }

    /** Un des mondes des raids ? */
    public boolean isRaidWorld(World w) {
        return surface != null && surface.is(w);
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
    private void scout(CommandSender who, String id, RaidTarget target, Structure structure, int margin,
                       IntConsumer progress, BooleanSupplier wanted, Consumer<Scouted> ok, Consumer<String> fail) {
        World w = surface.world();
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
        scout(who, id, target, structure, config.margin, progressBar(who, "Génération du site"), () -> true, s -> {
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
     * @param arg clé de raids.yml ou identifiant de la structure
     * @return null si l'expédition est lancée, sinon la raison du refus
     */
    public String startRaid(CommandSender sender, String arg, boolean here) {
        if (surface == null || sites == null || surface.world() == null) return "Raids désactivés (raids.active) ou monde indisponible.";
        if (run != null) return "Une expédition est déjà en cours (contre " + run.target.name() + ").";
        if (busy) return "Un essai est en cours : attendez la fin.";
        if (siegeRunning.getAsBoolean()) return "Un siège est en cours : l'expédition attendra la fin du siège.";
        RaidTarget target = catalog.get(arg);
        if (target == null) target = catalog.byStructure(arg.contains(":") ? arg : "minecraft:" + arg);
        if (target == null) return "Cible inconnue : " + arg + " (clé de raids.yml ou identifiant de structure).";
        RaidCatalog.Hidden hidden = catalog.reason(target);
        if (hidden != null) return "Cible indisponible : " + hidden.group() + ".";
        if (target.otherDimension()) return "Les cibles du Nether et de l'End auront leurs propres mondes (phase R9).";
        Structure structure = structure(target.mainStructure());
        if (structure == null) return "Structure inconnue sur ce serveur : " + target.mainStructure() + ".";
        if (placements != null && Boolean.FALSE.equals(placements.canGenerate(structure))) {
            return target.name() + " ne naît jamais dans le monde des raids (biomes de mods absents).";
        }
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
        run = r;
        tell(sender, "§6⚔ Expédition contre §f" + target.name() + " §8(" + target.mainStructure() + ", niveau "
                + r.level.number() + " — " + r.level.name() + ")§6 : les éclaireurs repèrent le terrain…");
        Bukkit.broadcastMessage("§6⚔ Les éclaireurs de Valmont partent repérer §f" + target.name() + "§6…");
        int margin = Math.max(config.margin, config.campDistance + 8); // le camp tient dans la zone générée
        String name = target.name();
        scout(sender, target.mainStructure(), target, structure, margin, pct -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (!isRaidWorld(p.getWorld())) {
                    p.sendActionBar(Component.text("⚔ Les éclaireurs repèrent le terrain : " + pct + " %", NamedTextColor.GOLD));
                }
            }
        }, () -> run == r && !r.cancelled, s -> siteReady(r, s, sender), err -> {
            if (run == r) run = null;
            tell(sender, "§c❌ Expédition annulée : " + err + ".");
            Bukkit.broadcastMessage("§7⚔ L'expédition contre " + name + " n'aura pas lieu : les éclaireurs n'ont pas trouvé le terrain.");
        });
        return null;
    }

    /** Site prêt : camp, héraut, puis rassemblement. */
    private void siteReady(RaidRun r, Scouted s, CommandSender sender) {
        r.site = s.site();
        r.arena = s.arena();
        r.siteKey = s.key();
        r.camp = RaidCamp.build(surface.world(), s.site().box(), config.campDistance);
        if (r.camp == null) {
            tell(sender, "§c❌ Expédition annulée : pas de place pour le camp autour de la cible.");
            cleanup(r, "pas de place pour le camp");
            return;
        }
        herald.spawn(r.camp.herald());
        plugin.getLogger().info("⚔ [Raids] Camp de l'expédition contre " + r.target.name() + " : x " + r.camp.spawn().getBlockX()
                + ", y " + r.camp.spawn().getBlockY() + ", z " + r.camp.spawn().getBlockZ() + (r.camp.water() ? " (radeau)" : "") + ".");
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
                updateBar(r, now, "§c⚔ " + r.target.name() + " §7· ", r.assaultMillis);
                if (r.enrolled.stream().noneMatch(this::inRaidWorld)) finish(r, "plus aucun joueur engagé");
                else if (now >= r.phaseEndsAt) finish(r, "temps écoulé");
            }
            case BILAN -> {
                updateBar(r, now, "§6⚔ Retour à Valmont dans §f", config.bilanSeconds * 1000L);
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
            });
        }
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

    /** Fin de l'assaut (chrono écoulé, plus personne) : bilan, le temps de souffler. */
    private void finish(RaidRun r, String reason) {
        r.state = RaidRun.State.BILAN;
        r.endReason = reason;
        r.phaseEndsAt = System.currentTimeMillis() + config.bilanSeconds * 1000L;
        for (UUID id : r.enrolled) {
            Player p = Bukkit.getPlayer(id);
            if (p == null || !isRaidWorld(p.getWorld())) continue;
            p.sendTitle("§6⚔ Fin de l'expédition", "§7" + capitalize(reason), 10, 60, 20);
            p.playSound(p.getLocation(), Sound.BLOCK_BELL_USE, 1f, 0.8f);
            p.sendMessage("§6⚔ Fin de l'expédition §7(" + reason + ") : retour à Valmont dans " + config.bilanSeconds
                    + " s. Le héraut peut vous ramener tout de suite.");
        }
        plugin.getLogger().info("⚔ [Raids] Fin de l'assaut contre " + r.target.name() + " : " + reason + ".");
        if (config.bilanSeconds == 0) bringBack(r);
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
        if (r.site != null && surface.world() != null) {
            int removed = 0;
            for (Entity e : surface.world().getNearbyEntities(r.site.box().clone().expand(config.margin))) {
                if (e instanceof Player) continue;
                if (e.getScoreboardTags().contains(ArenaScanner.TAG_DEFENDER) || e.getScoreboardTags().contains(ArenaScanner.TAG_CIVIL)) {
                    e.remove();
                    removed++;
                }
            }
            if (removed > 0) plugin.getLogger().info("⚔ [Raids] " + removed + " occupant(s) de la cible retiré(s).");
        }
        if (r.siteKey >= 0) sites.release(r.siteKey);
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
