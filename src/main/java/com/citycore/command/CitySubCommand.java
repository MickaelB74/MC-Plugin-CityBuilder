package com.citycore.command;

import org.bukkit.command.CommandSender;

import java.util.List;

import static com.citycore.command.CityPermissions.*;

public enum CitySubCommand {

    // Droits : voir Rights plus bas. Sans droits précisés, une sous-commande est réservée à citycore.admin.
    CREATE("create",   "create",                          "Faire renaître Valmont ici (fondation)",
            Rights.admin(ADMIN_VILLE)),
    CLAIM("claim",     "claim",                           "Claim le chunk actuel",
            Rights.admin(ADMIN_VILLE).npc("Placez-vous sur la parcelle voulue (demandez à §6Alderic §7de vous suivre),"
                    + "\nparlez-lui, puis §fclic gauche §7sur la barre « Parcelles » de son menu.")),
    UNCLAIM("unclaim", "unclaim",                         "Retire le claim du chunk actuel",
            Rights.admin(ADMIN_VILLE).npc("Parlez à §6Alderic §7sur la parcelle à rendre :"
                    + "\n§fMaj + clic droit §7sur la barre « Parcelles » de son menu.")),
    EXPAND("expand",   "expand",                          "Agrandir la capacité de chunks (sans passer par Alderic)",
            Rights.admin(ADMIN_VILLE).npc("Parlez à §6Alderic §7(Maire) → « Agrandir la ville ».").alsoConsole()),
    DEPOSIT("deposit", "deposit <montant>",               "Déposer des coins dans la caisse (sans passer par Alderic)",
            Rights.admin(ADMIN_VILLE).npc("Parlez à §6Alderic §7(Maire) → « Trésor » → « Déposer des coins ».")),
    INFO("info",       "info",                            "Afficher les infos de la ville",
            Rights.player().alsoConsole()),
    MAP("map",         "map",                             "Afficher les bordures de chunks",
            Rights.player()),
    PROGRESSION("progression", "progression",             "Progrès de la ville : onglet du menu Progrès (touche L)",
            Rights.player(), "progres", "avancement", "chroniques"),
    LOCATE("locate", "locate [personnage]", "Où sont-ils ? Carte de Valmont et position des personnages découverts",
            Rights.player(), "localiser", "ou", "où"),
    NPC("npc",         "npc <type|tous> <spawn|levelUp|levelDown|remove|activer|desactiver|menu>",
            "Gérer les NPCs ; tests : menu complet sans atelier (activer, desactiver, menu)",
            Rights.admin(ADMIN_PNJ).alsoConsole()),
    QUESTS("quests", "quests [toggle [visible|accroche|masque]|recits]",
            "Journal de quêtes : suivi, récits des habitants ; affichage du suivi (visible, accroché à la liste Tab, masqué)",
            Rights.player()),
    BUILD("build", "build [new [type] [artisan] [nom]|inspect [nom]|type <nom> <type> [artisan]|hauteur <nom> <auto|sol|bas haut>|show|remove <nom|all>|assign <bâtiment> <npc>|skip]",
            "Gérer les bâtiments de la ville (habitation, atelier, mairie, divers)",
            Rights.mixed(ADMIN_BATIMENTS).forPlayers("build <inspect [nom]|show>",
                    "Inspecter un bâtiment, afficher les bordures (gestion : chez Alderic, « Bâtiments »)")),
    JOB("job", "job <joueur> <npc|clear>", "Gérer le job d'un joueur",
            Rights.admin(ADMIN_JOUEURS).alsoConsole()),
    PLAYER("player", "player <joueur> <levelUp|levelDown|setLevel>", "Gérer le niveau d'un joueur",
            Rights.admin(ADMIN_JOUEURS).alsoConsole()),
    ECO("eco", "eco check", "Contrôler l'économie des PNJ : arbitrages",
            Rights.admin(ADMIN_ECO).alsoConsole()),
    UI("ui", "ui [valmont|classique]", "Style des menus des habitants (illustrés ou classiques)",
            Rights.player(), "interface", "menus"),
    HABITANTS("habitants", "habitants [list|besoins|arrivee|kill <nom>|impot|aube|deuil|recharger]",
            "Habitants de la ville : lits, besoins et satisfaction ; tests : arrivées, deuils, impôt",
            Rights.mixed(ADMIN_HABITANTS).alsoConsole().forPlayers("habitants [list|besoins]",
                    "Habitants de la ville : lits, besoins et satisfaction"), "population"),
    SIEGE("siege", "siege [start [palier] [monstres|banniere]|stop|next]",
            "Sièges de la ville : état ; lancer, arrêter, passer à la vague suivante",
            Rights.mixed(ADMIN_EVENEMENTS).alsoConsole().forPlayers("siege", "État du siège en cours"), "siège"),
    EVENT("event", "event [list|start <type>|stop|recharger]",
            "Calendrier des événements (sièges…) : planning et prochain rendez-vous ; lancer, arrêter, recharger",
            Rights.mixed(ADMIN_EVENEMENTS).alsoConsole().forPlayers("event [list]",
                    "Calendrier des événements (sièges…) : planning et prochain rendez-vous"),
            "evenements", "événements"),
    EMPLOIS("emplois", "emplois [list|produire|recharger]",
            "Emplois des habitants chez les artisans : employés, annexes, cycles (produire, recharger)",
            Rights.mixed(ADMIN_HABITANTS).alsoConsole().forPlayers("emplois [list]",
                    "Emplois des habitants (embauches : chez l'artisan, Actions → Employés)"),
            "jobs", "travail"),
    MODS("mods", "mods",
            "Mods du serveur : présents ou absents, et les personnages qui en dépendent",
            Rights.admin(ADMIN).alsoConsole(), "integrations"),
    CARTE("carte", "carte [etat|recharger]",
            "Parcelles de la ville sur les cartes JourneyMap : état, relire les réglages",
            Rights.admin(ADMIN).alsoConsole(), "journeymap"),
    GARDE("garde", "garde [list|former <habitant> <rôle>|renvoyer <nom>|fin|kill <nom>|test [n] [rôle]|test retirer]",
            "Garde de la ville : formations, gardes en patrouille, gardes de test",
            Rights.mixed(ADMIN_HABITANTS).alsoConsole().forPlayers("garde [list]",
                    "Garde de la ville (formations : chez Roland, Actions → La garde)"), "guard"),
    RAID("raid", "raid [start <cible> [ici]|stop|quitter|essai <structure>|zones|tp [essai]|retour|purge|catalogue [niveau]|inconnues]",
            "Raids (expéditions de Valmont) : état, retraite ; lancer, arrêter, essais, catalogue, structures inconnues",
            Rights.mixed(ADMIN_RAID).alsoConsole().forPlayers("raid [quitter]", "Expédition en cours ; rentrer à Valmont"),
            "raids", "expedition", "expédition"),
    PROTO("proto", "proto <habitant|soldat|former|zombie|foule|tps|skins|clear>",
            "Prototype temporaire : PNJ blessables, foule, combat",
            Rights.admin(ADMIN_PROTO));


    public final String label;
    public final String usage;
    public final String description;
    public final List<String> aliases;
    /** Qui peut l'utiliser, et comment (voir {@link Rights}). */
    public final Rights rights;

    /** Sous-commande sans droits précisés : réservée aux administrateurs (citycore.admin). */
    CitySubCommand(String label, String usage, String description, String... aliases) {
        this(label, usage, description, Rights.admin(ADMIN), aliases);
    }

    CitySubCommand(String label, String usage, String description, Rights rights, String... aliases) {
        this.label       = label;
        this.usage       = usage;
        this.description = description;
        this.rights      = rights;
        this.aliases     = List.of(aliases);
    }

    public static CitySubCommand fromLabel(String label) {
        for (CitySubCommand cmd : values()) {
            if (cmd.label.equalsIgnoreCase(label)) return cmd;
            for (String alias : cmd.aliases) {
                if (alias.equalsIgnoreCase(label)) return cmd;
            }
        }
        return null;
    }

    /** Usage affiché dans l'aide : la partie « joueur » seulement pour qui n'a pas les actions d'admin. */
    public String usageFor(CommandSender sender) {
        return rights.playerUsage() == null || CityPermissions.isAdmin(sender, this) ? usage : rights.playerUsage();
    }

    public String descriptionFor(CommandSender sender) {
        return rights.playerDescription() == null || CityPermissions.isAdmin(sender, this)
                ? description : rights.playerDescription();
    }

    /**
     * Droits d'une sous-commande.
     *
     * @param use               nœud requis pour l'utiliser (au moins sa partie « joueur »)
     * @param admin             nœud de ses actions d'administration et de test (null = aucune)
     * @param npcHint           PNJ à aller voir quand un joueur n'a pas le droit (lignes séparées par « \n »)
     * @param console           utilisable depuis la console (ni position ni menu nécessaires)
     * @param playerUsage       usage montré à un joueur sans les actions d'admin (null = le même)
     * @param playerDescription description montrée à ce joueur (null = la même)
     */
    public record Rights(String use, String admin, String npcHint, boolean console,
                         String playerUsage, String playerDescription) {

        /** Ouverte aux joueurs (consultation, confort), sans action d'administration. */
        static Rights player() {
            return new Rights(CityPermissions.PLAYER, null, null, false, null, null);
        }

        /** Consultation ouverte aux joueurs ; les autres actions demandent le nœud {@code admin}. */
        static Rights mixed(String admin) {
            return new Rights(CityPermissions.PLAYER, admin, null, false, null, null);
        }

        /** Réservée au nœud {@code admin} ; un joueur est renvoyé vers le PNJ s'il y en a un ({@link #npc}). */
        static Rights admin(String admin) {
            return new Rights(admin, admin, null, false, null, null);
        }

        Rights npc(String hint) {
            return new Rights(use, admin, hint, console, playerUsage, playerDescription);
        }

        Rights alsoConsole() {
            return new Rights(use, admin, npcHint, true, playerUsage, playerDescription);
        }

        Rights forPlayers(String usage, String description) {
            return new Rights(use, admin, npcHint, console, usage, description);
        }
    }
}
