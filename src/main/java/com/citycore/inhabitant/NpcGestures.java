package com.citycore.inhabitant;

import com.citycore.integration.ModIntegrations;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.trait.ScoreboardTrait;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

import java.util.HashSet;

/**
 * Gestes de combat des PNJ (gardes, soldats ennemis) : le bras bouge comme dans Minecraft, et une
 * étiquette d'entité signale le geste au mod serveur « CityCore — Compagnon », module Epic Fight
 * (companions/citycore-companion), qui la retire au tick suivant et joue l'animation d'Epic Fight
 * correspondante (enchaînement de l'arme tenue, tir de l'arc ou de l'arbalète).
 *
 * Sans ce mod, l'étiquette reste posée sans effet (une seule par geste : jamais d'accumulation).
 * Le plugin ne dépend d'aucun mod : l'étiquette est le seul lien entre les deux.
 */
public final class NpcGestures {

    /** Étiquettes lues par le mod (mêmes valeurs que NpcAnimator.TAG_ATTACK / TAG_SHOT). */
    public static final String TAG_ATTACK = "citycore.geste.attaque";
    public static final String TAG_SHOT   = "citycore.geste.tir";

    private NpcGestures() {
    }

    /** Coup porté (mêlée, entraînement à l'arme). */
    public static void attack(LivingEntity body) {
        body.swingMainHand();
        body.addScoreboardTag(TAG_ATTACK);
    }

    /** Flèche ou carreau tiré (entraînement des archers compris). */
    public static void shot(LivingEntity body) {
        body.swingMainHand();
        body.addScoreboardTag(TAG_SHOT);
    }

    /**
     * Réglages d'un PNJ de la ville, avant son apparition. Avec Epic Fight, Citizens ne fait plus
     * « balancer le bras » au PNJ quand il apparaît chez un joueur (réglage reset-yaw-on-spawn :
     * Citizens recale ainsi l'orientation du corps) : Epic Fight transforme ce balancement en
     * animation de frappe (« creuser ») qui pouvait rester bloquée en boucle chez le joueur, surtout
     * à sa connexion après un redémarrage, quand tous les PNJ apparaissent en même temps. Sans Epic
     * Fight, le réglage de Citizens s'applique comme avant.
     */
    public static void prepare(NPC npc) {
        if (ModIntegrations.present("epicfight")) {
            npc.data().setPersistent(NPC.Metadata.RESET_YAW_ON_SPAWN, false);
        } else {
            npc.data().remove(NPC.Metadata.RESET_YAW_ON_SPAWN);
        }
    }

    /**
     * Retire les étiquettes de geste restées sur un PNJ apparu (serveur arrêté avant que le mod les
     * lise, PNJ réapparu après un chunk déchargé) : sans cela, le mod jouerait un coup sans cible.
     * Citizens garde une copie des étiquettes de l'entité (trait « scoreboardtrait ») et la remet à
     * chaque apparition : la copie est nettoyée aussi. true si une étiquette a été retirée.
     */
    public static boolean clearLeftovers(NPC npc) {
        if (npc == null || !npc.isSpawned()) return false;
        Entity body = npc.getEntity();
        boolean removed = body.removeScoreboardTag(TAG_ATTACK);
        removed |= body.removeScoreboardTag(TAG_SHOT);
        if (removed) {
            ScoreboardTrait trait = npc.getTraitNullable(ScoreboardTrait.class);
            if (trait != null) trait.setTags(new HashSet<>(body.getScoreboardTags()));
        }
        return removed;
    }
}
