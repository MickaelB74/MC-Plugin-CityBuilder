package com.citycore.command;

public enum CitySubCommand {

    CREATE("create",  "create <nom>",    "Fonder la ville"),
    CLAIM("claim",    "claim",           "Claimer le chunk actuel"),
    UNCLAIM("unclaim","unclaim",         "Retirer le claim du chunk actuel"),
    EXPAND("expand",  "expand",          "Acheter un slot de chunk"),
    DEPOSIT("deposit","deposit <montant>","Déposer vos coins dans la caisse"),
    INFO("info",      "info",            "Infos de la ville"),
    MAP("map",        "map",             "Visualiser les chunks claimés"),
    SPAWN("spawn", "spawn <type>", "Faire apparaître un NPC villageois");

    public final String label;       // utilisé pour le switch et le tab
    public final String usage;       // affiché dans l'aide
    public final String description; // affiché dans l'aide

    CitySubCommand(String label, String usage, String description) {
        this.label = label;
        this.usage = usage;
        this.description = description;
    }

    /** Retrouve une sous-commande depuis le texte tapé par le joueur. */
    public static CitySubCommand from(String input) {
        for (CitySubCommand cmd : values()) {
            if (cmd.label.equalsIgnoreCase(input)) return cmd;
        }
        return null;
    }

    /** Retourne tous les labels (pour le TabCompleter). */
    public static java.util.List<String> labels() {
        return java.util.Arrays.stream(values())
                .map(c -> c.label)
                .toList();
    }
}