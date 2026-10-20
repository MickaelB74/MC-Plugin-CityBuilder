package com.citycore.command;

import org.bukkit.command.CommandSender;

/**
 * Droits des commandes /city (nœuds déclarés dans plugin.yml).
 *
 * Principe : un joueur passe par les habitants de Valmont (Alderic, les artisans, Roland) pour
 * tout ce qu'ils savent faire ; la commande ne lui laisse que les consultations et le confort
 * (infos, bordures, journal de quêtes, style des menus…). Les administrateurs ont tout, y compris
 * les actions de test, et la console les sous-commandes qui n'ont besoin ni d'une position ni
 * d'un menu.
 *
 * {@code citycore.admin} (op par défaut) contient tous les nœuds {@code citycore.admin.*} : une
 * nouvelle sous-commande d'administration peut simplement exiger {@link #ADMIN}.
 */
public final class CityPermissions {

    /** Commandes de joueur : consultations et confort (vrai par défaut). */
    public static final String PLAYER = "citycore.player";

    /** Tout CityCore : administration et tests (op par défaut). */
    public static final String ADMIN = "citycore.admin";

    /** Fondation, parcelles, agrandissement, caisse en commande (sans Alderic). */
    public static final String ADMIN_VILLE      = "citycore.admin.ville";
    /** Bâtiments en commande : liste, création, type, suppression, installation, hauteur. */
    public static final String ADMIN_BATIMENTS  = "citycore.admin.batiments";
    /** PNJ : apparition, niveau, suppression. */
    public static final String ADMIN_PNJ        = "citycore.admin.pnj";
    /** Métier et niveau des joueurs. */
    public static final String ADMIN_JOUEURS    = "citycore.admin.joueurs";
    /** Contrôle de l'économie des PNJ. */
    public static final String ADMIN_ECO        = "citycore.admin.eco";
    /** Habitants, emplois et garde : actions de test et rechargement des réglages. */
    public static final String ADMIN_HABITANTS  = "citycore.admin.habitants";
    /** Sièges et calendrier des événements : lancer, arrêter, recharger. */
    public static final String ADMIN_EVENEMENTS = "citycore.admin.evenements";
    /** Prototype de la phase 0 (et ses messages de suivi). */
    public static final String ADMIN_PROTO      = "citycore.admin.proto";

    private CityPermissions() {}

    /** Peut-il utiliser la sous-commande (au moins sa partie « joueur ») ? */
    public static boolean canUse(CommandSender sender, CitySubCommand sub) {
        return sender.hasPermission(sub.rights.use());
    }

    /** A-t-il les actions d'administration (et de test) de la sous-commande ? */
    public static boolean isAdmin(CommandSender sender, CitySubCommand sub) {
        return sub.rights.admin() != null && sender.hasPermission(sub.rights.admin());
    }

    /**
     * Refus : indique le PNJ à aller voir quand l'action passe par un habitant (une ligne par
     * « \n »), sinon que l'action est réservée aux administrateurs.
     */
    public static void deny(CommandSender sender, String npcHint) {
        if (npcHint == null || npcHint.isBlank()) {
            sender.sendMessage("§c❌ Réservé aux administrateurs.");
            return;
        }
        sender.sendMessage("§6🏘 Cette action passe par les habitants de Valmont :");
        for (String line : npcHint.split("\n")) sender.sendMessage("§7➜ " + line);
    }
}
