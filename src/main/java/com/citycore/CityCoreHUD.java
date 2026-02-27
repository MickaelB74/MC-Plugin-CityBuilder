package com.citycore;

import com.citycore.city.City;
import com.citycore.city.CityManager;
import com.citycore.npc.CityNPC;
import com.citycore.player.PlayerDataManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public class CityCoreHUD {

    private final JavaPlugin  plugin;
    private final CityManager cityManager;
    private final Economy     economy;
    private final PlayerDataManager playerDataManager;
    private BukkitTask task;

    public CityCoreHUD(JavaPlugin plugin, CityManager cityManager, Economy economy, PlayerDataManager playerDataManager) {
        this.plugin      = plugin;
        this.cityManager = cityManager;
        this.economy     = economy;
        this.playerDataManager = playerDataManager;
    }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                updatePlayer(player);
            }
        }, 0L, 40L); // toutes les 2 secondes
    }

    public void stop() {
        if (task != null) { task.cancel(); task = null; }
    }

    public void updatePlayer(Player player) {
        // ── Données joueur ───────────────────────────────────────
        int    coins       = (int) economy.getBalance(player);
        int    playerLevel = playerDataManager.getLevel(player.getUniqueId());
        int    playerXP    = playerDataManager.getXP(player.getUniqueId());
        int    xpRequired  = PlayerDataManager.xpForNextLevel(playerLevel);
        CityNPC job        = playerDataManager.getJob(player.getUniqueId());
        String jobName     = job != null
                ? job.displayName.replaceAll("§.", "")
                : "—";

        // ── Données ville ────────────────────────────────────────
        City   city      = cityManager.isCityInitialized() ? cityManager.getCity() : null;
        String cityLevel = city != null ? String.valueOf(city.getLevel())          : "—";
        String cityCoins = city != null ? String.valueOf(city.getCoins())          : "—";
        String cityPop   = city != null ? String.valueOf(city.getResidentCount())  : "—";
        String citySize   = city != null ? city.getClaimedChunks() + "/" + city.getMaxChunks()  : "—";

        Component header = Component.text()
                .append(Component.newline())

                // ── Titre ────────────────────────────────────────
                .append(Component.text("✦ ", NamedTextColor.DARK_GRAY))
                .append(Component.text("MyCivilisation", NamedTextColor.GOLD))
                .append(Component.text(" ✦", NamedTextColor.DARK_GRAY))
                .append(Component.newline())
                .append(Component.newline())

                // ── Section Joueur ───────────────────────────────
                .append(Component.text("— ⚔ Joueur —", NamedTextColor.YELLOW))
                .append(Component.newline())
                .append(Component.text("Niveau : ", NamedTextColor.GRAY))
                .append(Component.text(playerLevel, NamedTextColor.YELLOW))
                .append(Component.text(" §b(" + playerXP + "/" + xpRequired + " XP)", NamedTextColor.DARK_GRAY))
                .append(Component.text("   Argent : ", NamedTextColor.GRAY))
                .append(Component.text(coins + " 🪙", NamedTextColor.GOLD))
                .append(Component.text("   Métier : ", NamedTextColor.GRAY))
                .append(Component.text(jobName, NamedTextColor.AQUA))
                .append(Component.newline())
                .append(Component.newline())

                // ── Section Ville ────────────────────────────────
                .append(Component.text("— 🏰 Ville —", NamedTextColor.GREEN))
                .append(Component.newline())
                .append(Component.text("Niveau : ", NamedTextColor.GRAY))
                .append(Component.text(cityLevel, NamedTextColor.GREEN))
                .append(Component.text("   Habitants : ", NamedTextColor.GRAY))
                .append(Component.text(cityPop, NamedTextColor.WHITE))
                .append(Component.text("   Coffre : ", NamedTextColor.GRAY))
                .append(Component.text(cityCoins + " 🪙", NamedTextColor.GOLD))
                .append(Component.text("   Taille : ", NamedTextColor.GRAY))
                .append(Component.text(citySize, NamedTextColor.WHITE))
                .append(Component.newline())

                .build();

        player.sendPlayerListHeaderAndFooter(header, Component.empty());
    }

    public void clearPlayer(Player player) {
        player.sendPlayerListHeaderAndFooter(Component.empty(), Component.empty());
    }
}