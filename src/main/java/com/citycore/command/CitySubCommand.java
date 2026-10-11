package com.citycore.command;

import java.util.List;

public enum CitySubCommand {

    CREATE("create",   "create",                          "Faire renaître Valmont ici (fondation)"),
    CLAIM("claim",     "claim",                           "Claim le chunk actuel"),
    UNCLAIM("unclaim", "unclaim",                         "Retire le claim du chunk actuel"),
    EXPAND("expand",   "expand",                          "Agrandir la capacité de chunks"),
    DEPOSIT("deposit", "deposit <montant>",               "Déposer des coins dans la caisse"),
    INFO("info",       "info",                            "Afficher les infos de la ville"),
    MAP("map",         "map",                             "Afficher les bordures de chunks"),
    PROGRESSION("progression", "progression",             "Progrès de la ville : onglet du menu Progrès (touche L)",
            "progres", "avancement", "chroniques"),
    NPC("npc",         "npc <type> <spawn|levelUp|levelDown>", "Gérer les NPCs"),
    QUESTS("quests", "quests [toggle|recits]", "Journal de quêtes : suivi, récits des habitants, afficher/masquer le panneau"),
    BUILD("build", "build [new [type] [artisan] [nom]|inspect [nom]|type <nom> <type> [artisan]|hauteur <nom> <auto|sol|bas haut>|show|remove <nom|all>]",
            "Gérer les bâtiments de la ville (habitation, atelier, mairie, divers)"),
    JOB("job", "job <joueur> <npc|clear>", "Gérer le job d'un joueur (op)"),
    PLAYER("player", "player <joueur> <levelUp|levelDown|setLevel>", "Gérer le niveau d'un joueur (op)"),
    ECO("eco", "eco check", "Contrôler l'économie des PNJ : arbitrages (op)"),
    UI("ui", "ui [valmont|classique]", "Style des menus des habitants (illustrés ou classiques)",
            "interface", "menus"),
    HABITANTS("habitants", "habitants [list|besoins|arrivee|kill <nom>|impot|aube|deuil|recharger]",
            "Habitants de la ville : lits, besoins et satisfaction, arrivées, deuils, impôt (actions de test : op)", "population"),
    SIEGE("siege", "siege [start [palier] [monstres|banniere]|stop|next]",
            "Sièges de la ville : état ; lancer, arrêter, passer à la vague suivante (op)", "siège"),
    EVENT("event", "event [list|start <type>|stop|recharger]",
            "Calendrier des événements (sièges…) : planning et prochain rendez-vous ; actions (op)",
            "evenements", "événements"),
    EMPLOIS("emplois", "emplois [list|produire|recharger]",
            "Emplois des habitants chez les artisans : employés, annexes, cycles (produire, recharger : op)",
            "jobs", "travail"),
    CARTE("carte", "carte [recharger]",
            "Bordures de la ville sur les cartes JourneyMap : état ; relire les réglages (op)", "journeymap"),
    GARDE("garde", "garde [list|former <habitant> <rôle>|renvoyer <nom>|fin|kill <nom>|test [n] [rôle]|test retirer]",
            "Garde de la ville : formations chez Roland, gardes en patrouille (fin, kill, test : op)", "guard"),
    PROTO("proto", "proto <habitant|soldat|former|zombie|foule|tps|skins|clear>",
            "Prototype temporaire : PNJ blessables, foule, combat (op)");


    public final String label;
    public final String usage;
    public final String description;
    public final List<String> aliases;

    CitySubCommand(String label, String usage, String description, String... aliases) {
        this.label       = label;
        this.usage       = usage;
        this.description = description;
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
}
