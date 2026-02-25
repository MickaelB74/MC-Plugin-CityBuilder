package com.citycore.command;

import com.citycore.building.Building;
import com.citycore.building.BuildingManager;
import com.citycore.city.CityManager;
import com.citycore.npc.CityNPC;
import com.citycore.npc.NPCManager;
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
    private final NPCManager npcManager;

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
                case "deposit" -> List.of("100", "500", "1000", "5000");
                case "create"  -> cityManager.isCityInitialized()
                        ? new ArrayList<>()
                        : List.of("<nom_de_ville>");
                case "npc"     -> Arrays.stream(CityNPC.values())
                        .filter(n -> n != CityNPC.MAYOR)
                        .map(n -> n.tag.replace("citycore_", ""))
                        .collect(Collectors.toList());
                case "quests"  -> List.of("toggle");
                case "build"   -> List.of("new", "show", "remove", "assign", "skip");
                default        -> new ArrayList<>();
            };
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("npc")) {
            if (!player.isOp()) return new ArrayList<>();
            boolean validType = Arrays.stream(CityNPC.values())
                    .filter(n -> n != CityNPC.MAYOR)
                    .anyMatch(n -> n.tag.replace("citycore_", "")
                            .equalsIgnoreCase(args[1]));
            if (!validType) return new ArrayList<>();
            return new ArrayList<>(List.of("spawn", "levelUp", "levelDown", "remove"));
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("build")) {
            return switch (args[1].toLowerCase()) {
                case "new"    -> List.of("<nom>");
                case "remove" -> {
                    List<String> names = buildingManager.getAllBuildings()
                            .stream()
                            .map(Building::name)
                            .collect(Collectors.toList());
                    names.add("all");
                    yield names;
                }
                case "assign" -> buildingManager.getAllBuildings()
                        .stream()
                        .map(Building::name)
                        .collect(Collectors.toList());
                default -> new ArrayList<>();
            };
        }

        if (args.length == 4 && args[0].equalsIgnoreCase("build")
                && args[1].equalsIgnoreCase("assign")) {
            return Arrays.stream(CityNPC.values())
                    .filter(n -> n != CityNPC.MAYOR)
                    .filter(n -> npcManager.isSpawned(n))
                    .filter(n -> !buildingManager.isNPCAlreadyAssigned(n.tag))
                    .map(n -> n.tag.replace("citycore_", ""))
                    .collect(Collectors.toList());
        }

        return new ArrayList<>();
    }
}