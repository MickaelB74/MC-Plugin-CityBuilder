package com.citycore.raid;

import com.citycore.integration.ItemIds;
import org.bukkit.Location;
import org.bukkit.Registry;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * /city raid : consultation pour les joueurs ; essais et contrôles pour l'administration
 * (citycore.admin.raid). Phase R0 : essai (repérage d'un exemplaire naturel), zones, tp, retour,
 * purge ; phase R2 : catalogue, inconnues.
 */
public final class RaidCommands {

    /** Noms des niveaux (PLAN_RAIDS §4.1), du niveau I au niveau VII. */
    public static final String[] LEVEL_NAMES = {
            "Escarmouche", "Coup de main", "Expédition", "Assaut", "Offensive", "Grande offensive", "Légende"};
    private static final String[] ROMAN = {"I", "II", "III", "IV", "V", "VI", "VII"};

    private static final List<String> ADMIN_ACTIONS = List.of(
            "essai", "zones", "tp", "retour", "purge", "catalogue", "inconnues");

    private final RaidManager raids;

    public RaidCommands(RaidManager raids) {
        this.raids = raids;
    }

    /**
     * @param player le joueur, ou null depuis la console
     * @param admin  a-t-il citycore.admin.raid ?
     */
    public void handle(CommandSender sender, Player player, String[] args, boolean admin) {
        String action = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "";
        if (action.isEmpty() || action.equals("etat") || action.equals("état")) {
            sender.sendMessage("§7⚔ Aucune expédition en cours.");
            if (admin && raids.catalog() != null) {
                sender.sendMessage("§8" + raids.catalog().summary());
                sender.sendMessage("§8Actions : " + String.join(", ", ADMIN_ACTIONS) + ".");
            }
            return;
        }
        if (!admin) {
            com.citycore.command.CityPermissions.deny(sender, null);
            return;
        }
        switch (action) {
            case "essai", "poser", "place" -> {
                if (args.length < 3) {
                    sender.sendMessage("§cUsage : /city raid essai <structure> §8(repère un exemplaire naturel et génère sa zone)");
                    return;
                }
                String id = args[2].toLowerCase(Locale.ROOT);
                if (!id.contains(":")) id = "minecraft:" + id;
                String err = raids.trial(sender, id);
                if (err != null) sender.sendMessage("§c❌ " + err);
            }
            case "zones", "emplacements", "sites" -> raids.report(sender);
            case "tp" -> {
                if (player == null) {
                    sender.sendMessage("§cEn jeu seulement.");
                    return;
                }
                int number = 0;
                if (args.length >= 3) {
                    try {
                        number = Integer.parseInt(args[2]);
                    } catch (NumberFormatException e) {
                        sender.sendMessage("§cUsage : /city raid tp [numéro d'essai]");
                        return;
                    }
                }
                String err = raids.teleportTo(player, number);
                if (err != null) sender.sendMessage("§c❌ " + err);
            }
            case "retour", "back" -> {
                if (player == null) {
                    sender.sendMessage("§cEn jeu seulement.");
                    return;
                }
                raids.goBack(player);
            }
            case "purge" -> {
                String err = raids.purge();
                sender.sendMessage(err == null ? "§a✅ Monde des raids effacé et recréé (essais oubliés)."
                        : "§c❌ " + err);
            }
            case "catalogue", "catalog" -> catalogue(sender, args);
            case "inconnues", "unknown" -> unknown(sender);
            default -> sender.sendMessage("§cUsage : /city raid [" + String.join("|", ADMIN_ACTIONS) + "]");
        }
    }

    /** /city raid catalogue [niveau] : cibles par niveau, disponibles ou non et pourquoi (§3.3). */
    private void catalogue(CommandSender sender, String[] args) {
        RaidCatalog catalog = raids.catalog();
        if (catalog == null) return;
        sender.sendMessage("§6" + catalog.summary());
        catalog.loadWarnings().forEach(w -> sender.sendMessage("§e⚠ raids.yml : " + w));
        if (args.length < 3) {
            for (int level = 1; level <= 7; level++) {
                List<RaidTarget> list = catalog.byLevel(level);
                long ok = list.stream().filter(catalog::isAvailable).count();
                sender.sendMessage("§f" + ROMAN[level - 1] + " — " + LEVEL_NAMES[level - 1] + " §7: " + ok + "/" + list.size()
                        + " disponibles");
            }
            sender.sendMessage("§8Détail d'un niveau : /city raid catalogue <1-7>");
            return;
        }
        int level;
        try {
            level = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            level = 0;
        }
        if (level < 1 || level > 7) {
            sender.sendMessage("§cUsage : /city raid catalogue [1-7]");
            return;
        }
        List<RaidTarget> list = catalog.byLevel(level);
        sender.sendMessage("§6Niveau " + ROMAN[level - 1] + " — " + LEVEL_NAMES[level - 1] + " §7(" + list.size() + " cibles)");
        for (RaidTarget t : list) {
            RaidCatalog.Hidden h = catalog.reason(t);
            String where = t.type().icon + " " + t.type().label + ", " + String.join("/", t.terrain());
            sender.sendMessage((h == null ? "§a✔ " : "§c✘ ") + "§f" + t.name() + " §8(" + where + " · "
                    + t.mainStructure() + ")" + (h == null ? "" : " §c— " + h.group()));
        }
    }

    /** /city raid inconnues : structures du serveur ni au catalogue ni écartées, par espace de noms. */
    private void unknown(CommandSender sender) {
        RaidCatalog catalog = raids.catalog();
        if (catalog == null) return;
        List<String> list = catalog.unknownStructures();
        if (list.isEmpty()) {
            sender.sendMessage("§a✅ Aucune structure inconnue : toutes sont au catalogue (raids.yml) ou écartées (ignorees).");
            return;
        }
        Map<String, List<String>> byNs = new LinkedHashMap<>();
        for (String id : list) byNs.computeIfAbsent(ItemIds.namespace(id), k -> new ArrayList<>()).add(id.substring(id.indexOf(':') + 1));
        sender.sendMessage("§6⚔ " + list.size() + " structure(s) ni au catalogue ni écartée(s) §8(à classer dans raids.yml) :");
        byNs.forEach((ns, ids) -> sender.sendMessage("§f" + ns + " §7(" + ids.size() + ") : " + String.join(", ", ids)));
    }

    /** Complétion de /city raid … (args[0] = « raid »). */
    public List<String> complete(String[] args, boolean admin) {
        if (!admin) return new ArrayList<>();
        String last = args[args.length - 1].toLowerCase(Locale.ROOT);
        if (args.length == 2) return filter(ADMIN_ACTIONS, last);
        String action = args[1].toLowerCase(Locale.ROOT);
        if (args.length == 3) {
            return switch (action) {
                case "essai", "poser", "place" -> filter(structureIds(), last);
                case "catalogue", "catalog" -> List.of("1", "2", "3", "4", "5", "6", "7");
                case "tp" -> raids.sites() == null || raids.sites().history().isEmpty() ? List.of()
                        : java.util.stream.IntStream.rangeClosed(1, raids.sites().history().size()).mapToObj(String::valueOf).toList();
                default -> new ArrayList<>();
            };
        }
        return new ArrayList<>();
    }

    /** Structures du catalogue d'abord, puis toutes celles du serveur. */
    private List<String> structureIds() {
        Set<String> ids = new LinkedHashSet<>();
        if (raids.catalog() != null) ids.addAll(raids.catalog().structureIds());
        try {
            for (org.bukkit.generator.structure.Structure s : Registry.STRUCTURE) ids.add(s.getKey().toString());
        } catch (RuntimeException | LinkageError ignored) {
            // registre illisible : le catalogue suffit
        }
        return new ArrayList<>(ids);
    }

    private static List<String> filter(List<String> options, String prefix) {
        return options.stream().filter(o -> o.startsWith(prefix) || o.substring(o.indexOf(':') + 1).startsWith(prefix))
                .limit(60).collect(Collectors.toList());
    }
}
