package com.citycore.inhabitant;

import org.bukkit.entity.LivingEntity;

/**
 * Gestes de combat des PNJ (gardes, soldats ennemis) : le bras bouge comme dans Minecraft, et une
 * étiquette d'entité signale le geste au mod serveur « CityCore — Epic Fight »
 * (companions/citycore-epicfight), qui la retire au tick suivant et joue l'animation d'Epic Fight
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
}
