package com.citycore.raid;

import org.bukkit.Location;
import org.bukkit.boss.BossBar;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Un raid en cours (un seul à la fois) : sa cible, son site, son camp, ses joueurs enrôlés et
 * l'avancement de ses étapes (PLAN_RAIDS §1.2 à §1.4) ; pendant l'assaut (R4, R5), ses défenseurs,
 * ses objectifs et la défiance de l'adversaire.
 */
final class RaidRun {

    /** Étapes : repérage du terrain, rassemblement autour d'Alderic, assaut, bilan avant le retour. */
    enum State { PREPARATION, RASSEMBLEMENT, ASSAUT, BILAN }

    /** Identifiant (début du raid), noté avec les positions de retour. */
    final long id = System.currentTimeMillis();
    final RaidTarget target;
    final RaidConfig.Level level;
    /** Centre du rassemblement : Alderic, ou l'administrateur (« ici »). */
    final Location gather;
    /** « Alderic » ou le nom de l'administrateur qui lève l'expédition. */
    final String leader;

    State state = State.PREPARATION;
    /** Arrêté pendant le repérage : les étapes suivantes n'ont pas lieu. */
    boolean cancelled;
    /** Clé des tickets de chunk du site (rendus au nettoyage). */
    int siteKey = -1;
    RaidSites.Site site;
    /** Monde des raids de la cible (surface, Nether, End) et ses sites. */
    org.bukkit.World world;
    RaidSites sites;
    Arena arena;
    RaidCamp.Camp camp;
    /** Ville fortifiée assemblée (village, tours, siège), ou null. */
    RaidCity.Built city;
    /** Garnison de chaque tour (une tour est prise quand la sienne est abattue), tours prises. */
    final java.util.List<Set<UUID>> towerGarrisons = new java.util.ArrayList<>();
    final Set<Integer> towersTaken = new java.util.HashSet<>();
    /** Gabarit de camp choisi au lancement (null : camp du plugin). */
    RaidCamp.Model campModel;
    /** Joueurs partis au camp, encore engagés (ni rentrés ni partis d'eux-mêmes). */
    final Set<UUID> enrolled = new LinkedHashSet<>();
    final Map<UUID, Integer> deaths = new HashMap<>();
    /** Fin de l'étape en cours (rassemblement, assaut, bilan). */
    long phaseEndsAt;
    long assaultMillis;
    String endReason;
    BossBar bar;
    BukkitTask task;

    /* ── Assaut (phases R4, R5) ── */
    /** Joueurs partis au camp (récompense : tous ceux qui sont partis, même rentrés avant la fin). */
    final Set<UUID> participants = new LinkedHashSet<>();
    /** Objectifs de la cible, dans l'ordre d'affichage. */
    final java.util.List<Objective> objectives = new java.util.ArrayList<>();
    /** Défenseurs (créatures) encore engagés, et ceux de l'armée ennemie (PNJ). */
    final Set<UUID> defenders = new java.util.HashSet<>();
    int defendersTotal;
    int defendersKilled;
    /** Défenseurs abattus par joueur (bilan : meilleurs combattants). */
    final Map<UUID, Integer> kills = new HashMap<>();
    int civiliansKilled;
    /** Chef de la cible (créature ou soldat), ou null. */
    UUID chief;
    String chiefName;
    /** Maître du repaire (boss, mage : objectif obligatoire), ou null. */
    UUID master;
    String masterName;
    /** Place à tenir (objectif « tenir »), et cœur d'un repaire enfoui (fond de la galerie creusée). */
    Location holdAt;
    Location lairEntry;
    /** Faction ennemie de la garnison « armée », ou null. */
    com.citycore.siege.SiegeConfig.Faction faction;
    /** Renommée du niveau au départ (bonus de récompense, force des défenseurs). */
    double renown;
    double strength;
    boolean victory;
    /** Place à tenir disputée au dernier relevé (un défenseur dedans) : cercle rouge, sinon doré. */
    boolean holdContested;
    /** Demi-secondes de l'assaut (combat des défenseurs toutes les 2 s), vagues de renforts envoyées. */
    int ticks;
    int waves;

    /**
     * Un objectif (PLAN_RAIDS §5.2) : {@code done} sur {@code total} (cloche, chef : 1).
     *
     * @param mandatory à faire pour la victoire : la défiance ne descend pas sous 10 % sans lui
     */
    static final class Objective {
        final String key;
        final String label;
        final double weight;
        final boolean mandatory;
        int total;
        int done;
        /** Annoncé rempli (titre et son une seule fois). */
        boolean announced;

        Objective(String key, String label, double weight, boolean mandatory, int total) {
            this.key       = key;
            this.label     = label;
            this.weight    = weight;
            this.mandatory = mandatory;
            this.total     = Math.max(1, total);
        }

        boolean complete() {
            return done >= total;
        }

        double progress() {
            return Math.min(1.0, (double) done / total);
        }
    }

    Objective objective(String key) {
        for (Objective o : objectives) if (o.key.equals(key)) return o;
        return null;
    }

    /** Défiance de l'adversaire : 100 % au départ, 0 quand tous les objectifs sont remplis. */
    int defiance() {
        double sum = 0, got = 0;
        boolean mandatoryLeft = false;
        for (Objective o : objectives) {
            sum += o.weight;
            got += o.weight * o.progress();
            if (o.mandatory && !o.complete()) mandatoryLeft = true;
        }
        if (sum <= 0) return 0;
        int d = (int) Math.ceil(100 * (1 - got / sum));
        return mandatoryLeft ? Math.max(10, d) : d;
    }

    boolean allComplete() {
        for (Objective o : objectives) if (!o.complete()) return false;
        return !objectives.isEmpty();
    }

    RaidRun(RaidTarget target, RaidConfig.Level level, Location gather, String leader) {
        this.target = target;
        this.level  = level;
        this.gather = gather;
        this.leader = leader;
    }

    boolean isEnrolled(UUID player) {
        return enrolled.contains(player);
    }

    /** Assaut ou bilan : les joueurs enrôlés sont au camp ou autour de la cible. */
    boolean deployed() {
        return state == State.ASSAUT || state == State.BILAN;
    }
}
