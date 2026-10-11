package com.citycore;

import com.citycore.city.City;
import com.citycore.city.CityManager;
import com.citycore.npc.CityNPC;
import com.citycore.player.PlayerDataManager;
import com.citycore.quest.city.CityQuestManager;
import com.citycore.ui.UiHud;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;

import static com.citycore.ui.UiLayout.*;

public class CityCoreHUD {

    private final JavaPlugin         plugin;
    private final CityManager        cityManager;
    private final Economy            economy;
    private final PlayerDataManager  playerDataManager;
    private final CityQuestManager   cityQuestManager; // ← AJOUT
    private BukkitTask task;
    /** Interfaces illustrées « Valmont » (null tant qu'elles ne sont pas prêtes). */
    private com.citycore.ui.UiKit ui;

    /** Population « 12/16 » (habitants / lits, phase 3) ; null = nombre d'artisans installés. */
    private java.util.function.Supplier<String> population;

    /** Satisfaction des habitants (phase 6) : 0-100, ou -1 sans habitant ; null = ligne absente. */
    private java.util.function.IntSupplier satisfaction;

    public void setSatisfaction(java.util.function.IntSupplier satisfaction) {
        this.satisfaction = satisfaction;
    }

    public void setPopulation(java.util.function.Supplier<String> population) {
        this.population = population;
    }

    public void setUi(com.citycore.ui.UiKit ui) {
        this.ui = ui;
    }

    public CityCoreHUD(JavaPlugin plugin, CityManager cityManager, Economy economy,
                       PlayerDataManager playerDataManager, CityQuestManager cityQuestManager) {
        this.plugin            = plugin;
        this.cityManager       = cityManager;
        this.economy           = economy;
        this.playerDataManager = playerDataManager;
        this.cityQuestManager  = cityQuestManager; // ← AJOUT
    }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (Bukkit.getOnlinePlayers().isEmpty()) return;
            int cityLevel = cityQuestManager.getCityLevel(); // une requête par passage
            for (Player player : Bukkit.getOnlinePlayers()) {
                updatePlayer(player, cityLevel);
            }
        }, 0L, 40L); // toutes les 2 secondes
    }

    public void stop() {
        if (task != null) { task.cancel(); task = null; }
    }

    public void updatePlayer(Player player) {
        updatePlayer(player, cityQuestManager.getCityLevel());
    }

    private void updatePlayer(Player player, int cityLevelValue) {
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
        // CORRECTION : le niveau de la ville provient de city_level (CityQuestManager),
        // pas de la table city — c'est lui qui est mis à jour lors du level-up via le GUI maire.
        String cityLevel = String.valueOf(cityLevelValue);
        String cityCoins = city != null ? String.valueOf(city.getCoins())          : "—";
        String cityPop   = city == null ? "—" : population != null ? population.get()
                : String.valueOf(city.getResidentCount());
        String citySize  = city != null ? city.getClaimedChunks() + "/" + city.getMaxChunks() : "—";

        // Joueur équipé du pack : panneaux dessinés de part et d'autre de la liste des joueurs
        if (ui != null && ui.active(player)) {
            player.sendPlayerListHeaderAndFooter(pixelHeader(player.getName(), playerLevel, playerXP, xpRequired,
                    coins, job == null ? null : jobName, city, cityLevelValue), Component.empty());
            return;
        }

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
                .append(Component.text(satisfaction == null || satisfaction.getAsInt() < 0 ? ""
                        : " (" + satisfaction.getAsInt() + " % satisfaits)", NamedTextColor.GRAY))
                .append(Component.text("   Coffre : ", NamedTextColor.GRAY))
                .append(Component.text(cityCoins + " 🪙", NamedTextColor.GOLD))
                .append(Component.text("   Taille : ", NamedTextColor.GRAY))
                .append(Component.text(citySize, NamedTextColor.WHITE))
                .append(Component.newline())

                .build();

        player.sendPlayerListHeaderAndFooter(header, Component.empty());
    }

    /* =========================
       EN-TÊTE ILLUSTRÉ (pack « Valmont »)
       ========================= */

    /** Titre de la plaque centrale. */
    private static final String SERVER_TITLE = "MyCivilisation";

    /**
     * En-tête de la liste des joueurs : une seule ligne d'avance nulle porte la plaque du titre
     * (centre), le panneau de la cité (à gauche) et celui du joueur (à droite) ; la ligne vide
     * suivante réserve la hauteur de la plaque. Les joueurs connectés restent listés au milieu,
     * entre les deux panneaux.
     */
    private Component pixelHeader(String playerName, int level, int xp, int xpRequired, int coins,
                                  String jobName, City city, int cityLevel) {
        com.citycore.ui.UiCanvas c = ui.canvas(0);
        UiHud.panel(c, -HUD_W / 2, 0, TAB_OFFSET, SERVER_TITLE, List.of(), 0);

        // Cité : palier et niveau, habitants, trésor, parcelles
        List<UiHud.Row> cityRows = new ArrayList<>();
        String cityTitle;
        if (city == null) {
            cityTitle = "La cité";
            cityRows.add(UiHud.Row.plain("Pas encore fondée", UiHud.CREAM));
        } else {
            cityTitle = city.getName();
            String tier = com.citycore.quest.city.CityTier.fromLevel(cityLevel).tierName.replaceAll("§.", "");
            cityRows.add(new UiHud.Row(UiHud.Kind.HEAD, tier, UiHud.CREAM, "Niv. " + cityLevel, UiHud.GOLD, 0, false));
            // Habitants (phase 3) : vivants / lits des habitations conformes ; avant, les artisans installés
            cityRows.add(UiHud.Row.value("Habitants", population != null ? population.get()
                    : String.valueOf(city.getResidentCount()), UiHud.CREAM));
            // Satisfaction (phase 6) : barre et pourcentage (vert ≥ 70 %, ambre ≥ 40 %, rouge en dessous)
            if (satisfaction != null) {
                int sat = satisfaction.getAsInt();
                cityRows.add(UiHud.Row.bar("Satisfaction", UiHud.MUTED, sat < 0 ? "—" : sat + " %",
                        sat < 0 ? UiHud.MUTED : sat >= 70 ? UiHud.GREEN : sat >= 40 ? UiHud.GOLD : UiHud.RED,
                        Math.max(0, sat) / 100.0, false));
            }
            cityRows.add(UiHud.Row.value("Trésor", fmt(city.getCoins()), UiHud.GOLD));
            int max = Math.max(1, city.getMaxChunks());
            cityRows.add(UiHud.Row.bar("Parcelles", UiHud.MUTED, city.getClaimedChunks() + "/" + city.getMaxChunks(),
                    UiHud.CREAM, city.getClaimedChunks() / (double) max, false));
        }
        UiHud.panel(c, -TAB_GAP - HUD_W, 0, TAB_OFFSET, cityTitle, cityRows, TAB_ROWS);

        // Joueur : niveau, expérience, bourse, métier
        List<UiHud.Row> playerRows = List.of(
                new UiHud.Row(UiHud.Kind.HEAD, "Niveau", UiHud.CREAM, String.valueOf(level), UiHud.GOLD, 0, false),
                UiHud.Row.bar("XP", UiHud.MUTED, xp + "/" + xpRequired, UiHud.CREAM,
                        xp / (double) Math.max(1, xpRequired), true),
                UiHud.Row.value("Bourse", fmt(coins), UiHud.GOLD),
                UiHud.Row.value("Métier", jobName == null ? "Aucun" : jobName, UiHud.CREAM));
        UiHud.panel(c, TAB_GAP, 0, TAB_OFFSET, playerName, playerRows, TAB_ROWS);

        c.end(0); // avance nulle : la ligne reste centrée et ne repousse pas la liste des joueurs
        return Component.text().append(c.build())
                .append(Component.text("\n".repeat(TAB_EXTRA_LINES))).build();
    }

    private static String fmt(long n) {
        String s = Long.toString(Math.abs(n));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            if (i > 0 && (s.length() - i) % 3 == 0) sb.append(' ');
            sb.append(s.charAt(i));
        }
        return (n < 0 ? "-" : "") + sb;
    }

    public void clearPlayer(Player player) {
        player.sendPlayerListHeaderAndFooter(Component.empty(), Component.empty());
    }
}