package com.citycore.command;

import com.citycore.building.Building;
import com.citycore.building.BuildingCreation;
import com.citycore.building.BuildingManager;
import com.citycore.building.BuildingType;
import com.citycore.city.CityManager;
import com.citycore.npc.CityNPC;
import com.citycore.npc.NPCManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class CityTabCompleter implements TabCompleter {

    private final CityManager     cityManager;
    private final BuildingManager buildingManager;
    private final NPCManager      npcManager;

    private BuildingCreation buildingCreation;

    public void setBuildingCreation(BuildingCreation buildingCreation) {
        this.buildingCreation = buildingCreation;
    }

    public CityTabCompleter(CityManager cityManager,
                            BuildingManager buildingManager, NPCManager npcManager) {
        this.cityManager     = cityManager;
        this.buildingManager = buildingManager;
        this.npcManager      = npcManager;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command,
                                      String alias, String[] args) {
        if (!(sender instanceof Player player)) return new ArrayList<>();

        if (args.length == 1) {
            return Arrays.stream(CitySubCommand.values())
                    .map(c -> c.label)
                    .filter(label -> label.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2) {
            return switch (args[0].toLowerCase()) {
                case "deposit"  -> List.of("100", "500", "1000", "5000");
                case "create"   -> new ArrayList<>(); // la ville s'appelle Valmont : rien à saisir
                case "npc"      -> Arrays.stream(CityNPC.values())
                        .filter(n -> n != CityNPC.MAYOR)
                        .map(n -> n.tag.replace("citycore_", ""))
                        .collect(Collectors.toList());
                case "quests"   -> List.of("toggle", "recits");
                case "ui"       -> List.of("valmont", "classique");
                case "eco"      -> player.isOp() ? List.of("check") : new ArrayList<>();
                case "proto"    -> player.isOp()
                        ? List.of("habitant", "soldat", "former", "zombie", "foule", "tps", "skins", "clear")
                        : new ArrayList<>();
                case "build"    -> List.of("new", "inspect", "type", "hauteur", "show", "remove", "assign", "skip");
                case "player"   -> player.isOp()
                        ? Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .collect(Collectors.toList())
                        : new ArrayList<>();
                case "job"      -> player.isOp()
                        ? Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .collect(Collectors.toList())
                        : new ArrayList<>();
                default         -> new ArrayList<>();
            };
        }

        if (args.length == 3) {
            return switch (args[0].toLowerCase()) {
                case "npc" -> {
                    if (!player.isOp()) yield new ArrayList<>();
                    boolean validType = Arrays.stream(CityNPC.values())
                            .filter(n -> n != CityNPC.MAYOR)
                            .anyMatch(n -> n.tag.replace("citycore_", "")
                                    .equalsIgnoreCase(args[1]));
                    yield validType
                            ? List.of("spawn", "levelUp", "levelDown", "remove")
                            : new ArrayList<>();
                }
                case "build" -> switch (args[1].toLowerCase()) {
                    case "new"    -> Arrays.stream(BuildingType.values())
                            .map(BuildingType::key)
                            .filter(k -> k.startsWith(args[2].toLowerCase()))
                            .collect(Collectors.toList());
                    case "remove" -> {
                        List<String> names = buildingManager.getAllBuildings()
                                .stream().map(Building::name)
                                .collect(Collectors.toList());
                        names.add("all");
                        yield names;
                    }
                    case "assign", "type", "hauteur", "inspect" -> buildingManager.getAllBuildings()
                            .stream().map(Building::name)
                            .collect(Collectors.toList());
                    default -> new ArrayList<>();
                };
                case "player" -> player.isOp()
                        ? List.of("levelUp", "levelDown", "setLevel")
                        : new ArrayList<>();
                case "job" -> player.isOp()
                        ? Arrays.stream(CityNPC.values())
                        .filter(n -> n != CityNPC.MAYOR)
                        .map(n -> n.tag.replace("citycore_", ""))
                        .collect(Collectors.toList())
                        : new ArrayList<>();
                default -> new ArrayList<>();
            };
        }

        if (args.length == 4) {
            return switch (args[0].toLowerCase()) {
                case "build" -> switch (args[1].toLowerCase()) {
                    case "assign" -> Arrays.stream(CityNPC.values())
                            .filter(n -> n != CityNPC.MAYOR)
                            .filter(n -> npcManager.isSpawned(n))
                            .filter(n -> !buildingManager.isNPCAlreadyAssigned(n.tag))
                            .map(n -> n.skinId)
                            .collect(Collectors.toList());
                    // /city build new atelier <artisan|libre> (puis le nom)
                    case "new" -> {
                        if (BuildingType.parse(args[2]) != BuildingType.ATELIER) yield List.of("<nom>");
                        List<String> keys = new ArrayList<>(foundArtisans(args[3]));
                        if ("libre".startsWith(args[3].toLowerCase())) keys.add(0, "libre");
                        yield keys;
                    }
                    case "type" -> Arrays.stream(BuildingType.values())
                            .map(BuildingType::key)
                            .filter(k -> k.startsWith(args[3].toLowerCase()))
                            .collect(Collectors.toList());
                    case "hauteur" -> List.of(String.valueOf(player.getLocation().getBlockY()));
                    default -> new ArrayList<String>();
                };
                case "player" -> {
                    if (!player.isOp()) yield new ArrayList<>();
                    yield args[2].equalsIgnoreCase("setLevel")
                            ? List.of("1", "5", "10", "20", "50")
                            : new ArrayList<>();
                }
                default -> new ArrayList<>();
            };
        }

        // /city build type <nom> atelier <artisan>
        if (args.length == 5 && args[0].equalsIgnoreCase("build") && args[1].equalsIgnoreCase("type")
                && BuildingType.parse(args[3]) == BuildingType.ATELIER) {
            return foundArtisans(args[4]);
        }
        if (args.length == 5 && args[0].equalsIgnoreCase("build") && args[1].equalsIgnoreCase("new")
                && BuildingType.parse(args[2]) == BuildingType.ATELIER) {
            return List.of("<nom>");
        }
        if (args.length == 5 && args[0].equalsIgnoreCase("build") && args[1].equalsIgnoreCase("hauteur")) {
            return List.of(String.valueOf(player.getLocation().getBlockY() + 8));
        }

        return new ArrayList<>();
    }

    /** Prénoms des artisans déjà trouvés (seuls ceux-là peuvent recevoir un atelier réservé). */
    private List<String> foundArtisans(String prefix) {
        return BuildingCreation.artisans().stream()
                .filter(n -> buildingCreation == null || buildingCreation.isFound(n))
                .map(n -> n.skinId)
                .filter(k -> k.startsWith(prefix.toLowerCase()))
                .collect(Collectors.toList());
    }
}