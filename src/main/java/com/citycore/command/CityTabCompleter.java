package com.citycore.command;

import com.citycore.city.CityManager;
import com.citycore.npc.CityNPC;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class CityTabCompleter implements TabCompleter {

    private final CityManager cityManager;

    public CityTabCompleter(CityManager cityManager) {
        this.cityManager = cityManager;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command,
                                      String alias, String[] args) {
        if (!(sender instanceof Player player)) return new ArrayList<>();

        // ── Niveau 1 : sous-commandes ────────────────────────────
        if (args.length == 1) {
            return Arrays.stream(CitySubCommand.values())
                    .map(c -> c.label)
                    .filter(label -> label.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        // ── Niveau 2 ─────────────────────────────────────────────
        if (args.length == 2) {
            return switch (args[0].toLowerCase()) {
                case "deposit" -> List.of("100", "500", "1000", "5000");
                case "create"  -> cityManager.isCityInitialized()
                        ? new ArrayList<>()
                        : List.of("<nom_de_ville>");
                case "npc"     -> {
                    // Liste tous les types NPC sauf MAYOR
                    yield Arrays.stream(CityNPC.values())
                            .filter(n -> n != CityNPC.MAYOR)
                            .map(n -> n.tag.replace("citycore_", ""))
                            .collect(Collectors.toList());
                }
                default -> new ArrayList<>();
            };
        }

        // ── Niveau 3 ─────────────────────────────────────────────
        if (args.length == 3 && args[0].equalsIgnoreCase("npc")) {
            // Vérifie que args[1] est un type NPC valide
            boolean validType = Arrays.stream(CityNPC.values())
                    .filter(n -> n != CityNPC.MAYOR)
                    .anyMatch(n -> n.tag.replace("citycore_", "")
                            .equalsIgnoreCase(args[1]));

            if (!validType) return new ArrayList<>();

            List<String> actions = new ArrayList<>(List.of("spawn"));
            if (player.isOp()) {
                actions.add("levelUp");
                actions.add("levelDown");
            }
            return actions;
        }

        return new ArrayList<>();
    }
}