package com.citycore.command;

import com.citycore.city.CityManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class CityTabCompleter implements TabCompleter {

    private final CityManager cityManager;

    public CityTabCompleter(CityManager cityManager) {
        this.cityManager = cityManager;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command,
                                      String alias, String[] args) {

        if (!(sender instanceof Player)) return new ArrayList<>();

        if (args.length == 1) {
            String input = args[0].toLowerCase();
            return CitySubCommand.labels().stream()
                    .filter(s -> s.startsWith(input))
                    .toList();
        }

        if (args.length == 2) {
            return switch (args[0].toLowerCase()) {
                case "deposit"  -> List.of("100", "500", "1000", "5000");
                case "create"   -> cityManager.isCityInitialized()
                        ? new ArrayList<>()
                        : List.of("<nom_de_ville>");
                case "spawn" -> List.of("stonemason", "jacksparrow");
                default         -> new ArrayList<>();
            };
        }

        return new ArrayList<>();
    }
}