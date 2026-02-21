package com.citycore.command;

public enum CitySubCommand {

    CREATE("create",   "create <nom>",                    "Fonder une nouvelle ville"),
    CLAIM("claim",     "claim",                           "Claim le chunk actuel"),
    UNCLAIM("unclaim", "unclaim",                         "Retire le claim du chunk actuel"),
    EXPAND("expand",   "expand",                          "Agrandir la capacité de chunks"),
    DEPOSIT("deposit", "deposit <montant>",               "Déposer des coins dans la caisse"),
    INFO("info",       "info",                            "Afficher les infos de la ville"),
    MAP("map",         "map",                             "Afficher les bordures de chunks"),
    NPC("npc",         "npc <type> <spawn|levelUp|levelDown>", "Gérer les NPCs"),
    QUESTS("quests", "quests toggle", "Afficher/masquer le HUD des quêtes");

    public final String label;
    public final String usage;
    public final String description;

    CitySubCommand(String label, String usage, String description) {
        this.label       = label;
        this.usage       = usage;
        this.description = description;
    }

    public static CitySubCommand fromLabel(String label) {
        for (CitySubCommand cmd : values()) {
            if (cmd.label.equalsIgnoreCase(label)) return cmd;
        }
        return null;
    }
}