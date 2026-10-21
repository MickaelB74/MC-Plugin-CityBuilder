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
 * l'avancement de ses étapes (PLAN_RAIDS §1.2 à §1.4). Phase R1 : pas encore d'objectifs ni de
 * défiance ; l'assaut dure le chrono du niveau, puis bilan et retour.
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
    Arena arena;
    RaidCamp.Camp camp;
    /** Joueurs partis au camp, encore engagés (ni rentrés ni partis d'eux-mêmes). */
    final Set<UUID> enrolled = new LinkedHashSet<>();
    final Map<UUID, Integer> deaths = new HashMap<>();
    /** Fin de l'étape en cours (rassemblement, assaut, bilan). */
    long phaseEndsAt;
    long assaultMillis;
    String endReason;
    BossBar bar;
    BukkitTask task;

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
