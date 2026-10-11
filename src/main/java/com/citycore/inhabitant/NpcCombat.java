package com.citycore.inhabitant;

import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.util.Vector;

/**
 * Coups entre combattants « de type joueur » (habitants, gardes, soldats ennemis, joueurs).
 *
 * Pour Minecraft, ces PNJ sont des joueurs : un coup ou une flèche de l'un sur l'autre passe par
 * le contrôle PvP et n'a aucun effet quand le PvP est coupé sur le serveur. Les coups sont donc
 * appliqués ici directement : dégâts d'une attaque de créature (l'armure compte, aucun contrôle
 * PvP), recul, son, et l'attaquant est retenu comme auteur (bilan du siège, riposte).
 */
public final class NpcCombat {

    /** Portée d'un coup au contact (blocs). */
    public static final double REACH = 2.7;

    /** Attaquant du coup en train d'être porté (lu par la protection des joueurs en ville). */
    private static LivingEntity striking;

    private NpcCombat() {}

    /** Attaquant du coup en cours, ou null : le dégât reçu n'a pas d'auteur pour Minecraft. */
    public static LivingEntity striking() {
        return striking;
    }

    /**
     * Porte un coup de attacker sur victim. Le dégât part de la position de l'attaquant : le jeu en
     * tire le recul. (Sans point d'origine, le recul calculé par le serveur Youer devenait une
     * vitesse invalide — NaN — qui faisait planter le serveur à l'envoi aux joueurs.)
     */
    public static void strike(LivingEntity attacker, LivingEntity victim, double amount) {
        if (victim.isDead() || !victim.isValid() || amount <= 0) return;
        if (attacker instanceof Player p) victim.setKiller(p); // auteur du coup : garde, soldat ennemi ou joueur
        DamageSource source;
        try {
            source = DamageSource.builder(DamageType.MOB_ATTACK).withDamageLocation(attacker.getLocation()).build();
        } catch (RuntimeException e) {
            source = DamageSource.builder(DamageType.MOB_ATTACK).build(); // repli : la vitesse est assainie juste après
        }
        striking = attacker;
        try {
            victim.damage(amount, source);
        } finally {
            striking = null;
        }
        sanitize(victim);
    }

    /**
     * Vitesse invalide (NaN, infinie) remise à zéro tout de suite : envoyée telle quelle aux
     * joueurs, elle fait planter le serveur (« x not finite »).
     */
    public static void sanitize(Entity e) {
        Vector v = e.getVelocity();
        if (!Double.isFinite(v.getX()) || !Double.isFinite(v.getY()) || !Double.isFinite(v.getZ())) {
            e.setVelocity(new Vector());
        }
    }

    /** Dégâts d'un coup au contact : attaque du combattant (arme en main comprise), sinon d'après son arme. */
    public static double meleeDamage(LivingEntity attacker) {
        AttributeInstance attack = attacker.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE);
        double value = attack == null ? 1 : attack.getValue();
        if (value > 1.5) return value;
        EntityEquipment eq = attacker.getEquipment();
        Material hand = eq == null ? Material.AIR : eq.getItemInMainHand().getType();
        return switch (hand) {
            case WOODEN_SWORD, GOLDEN_SWORD -> 4;
            case STONE_SWORD -> 5;
            case IRON_SWORD -> 6;
            case DIAMOND_SWORD -> 7;
            case NETHERITE_SWORD -> 8;
            case IRON_AXE, STONE_AXE -> 7;
            case DIAMOND_AXE, NETHERITE_AXE -> 9;
            default -> 2;
        };
    }

    /** Dégâts d'une flèche : arbalète 6, arc 4,5. */
    public static double arrowDamage(LivingEntity shooter) {
        EntityEquipment eq = shooter.getEquipment();
        return eq != null && eq.getItemInMainHand().getType() == Material.CROSSBOW ? 6 : 4.5;
    }
}
