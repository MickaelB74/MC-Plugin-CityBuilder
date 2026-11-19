package com.citycore.prologue;

import com.citycore.npc.NPCManager;
import com.citycore.util.DatabaseManager;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Réinitialisation du jeu (/city restart puis /city confirm) : Valmont repart du coffre de départ.
 *
 * L'état de la ville vit aussi dans la mémoire d'une vingtaine de gestionnaires (habitants,
 * parcelles, inspections, quêtes…) : plutôt que de les vider un à un, la partie est effacée au
 * redémarrage. La confirmation retire du monde ce qui appartient à la ville (PNJ Citizens, coffre
 * de départ), laisse une marque dans le dossier du plugin et arrête le serveur. Au démarrage
 * suivant, avant l'ouverture de la base, celle-ci est rangée dans sauvegardes/ et une base neuve
 * est créée ; les préférences des joueurs (menus, suivi des quêtes) y sont reprises.
 *
 * Ne sont pas touchés : les constructions du monde, les inventaires et l'argent (Vault) des joueurs.
 */
public class GameReset {

    private static final String MARKER = "reinitialisation-demandee.txt";
    private static final String BACKUPS = "sauvegardes";

    private final JavaPlugin plugin;
    private boolean applied; // la partie a été effacée à ce démarrage
    private File    backup;  // base archivée à ce démarrage (null = aucune)
    private boolean requested; // arrêt en cours pour réinitialisation

    public GameReset(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean applied() {
        return applied;
    }

    /* =========================
       AU DÉMARRAGE
       ========================= */

    /** Avant l'ouverture de la base : si une réinitialisation a été demandée, la partie est archivée. */
    public void applyIfRequested() {
        File folder = plugin.getDataFolder();
        File marker = new File(folder, MARKER);
        if (!marker.exists()) return;
        File db = new File(folder, "database.db");
        String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
        File dir = new File(folder, BACKUPS);
        File target = new File(dir, "database-avant-reinitialisation-" + stamp + ".db");
        try {
            Files.createDirectories(dir.toPath());
            if (db.exists()) {
                Files.move(db.toPath(), target.toPath());
                backup = target;
            }
            // Journal laissé par un arrêt brutal : il suit sa base (SQLite le cherche à côté d'elle)
            File journal = new File(folder, "database.db-journal");
            if (journal.exists()) Files.move(journal.toPath(), new File(dir, target.getName() + "-journal").toPath());
            Files.delete(marker.toPath());
        } catch (IOException e) {
            plugin.getLogger().severe("❌ Réinitialisation impossible (" + e + ") : la partie continue."
                    + " Elle sera retentée au prochain démarrage (supprimez " + MARKER + " pour l'annuler).");
            return;
        }
        applied = true;
        plugin.getLogger().warning("🔄 Réinitialisation : Valmont repart de zéro"
                + (backup != null ? " (ancienne partie : " + BACKUPS + "/" + backup.getName() + ")." : "."));
    }

    /** Après l'ouverture de la base neuve : préférences des joueurs reprises de la partie archivée. */
    public void restorePrefs(DatabaseManager db) {
        if (backup == null) return;
        try (PreparedStatement attach = db.getConnection().prepareStatement("ATTACH DATABASE ? AS ancienne")) {
            attach.setString(1, backup.getAbsolutePath());
            attach.execute();
        } catch (SQLException e) {
            plugin.getLogger().warning("Réinitialisation : préférences des joueurs non reprises (" + e.getMessage() + ").");
            return;
        }
        try (PreparedStatement copy = db.getConnection().prepareStatement(
                "INSERT OR IGNORE INTO player_prefs (uuid, pref, value) SELECT uuid, pref, value FROM ancienne.player_prefs")) {
            int copied = copy.executeUpdate();
            plugin.getLogger().info("Réinitialisation : " + copied + " préférence(s) de joueurs reprise(s).");
        } catch (SQLException e) {
            plugin.getLogger().warning("Réinitialisation : préférences des joueurs non reprises (" + e.getMessage() + ").");
        } finally {
            try {
                db.execute("DETACH DATABASE ancienne");
            } catch (SQLException ignored) {
                // déjà détachée
            }
        }
    }

    /** PNJ Citizens de la ville (maire, artisans, légendes) retirés du monde ; renvoie leur nombre. */
    public static int destroyCityNpcs(NPCManager npcManager) {
        List<NPC> found = new ArrayList<>();
        for (NPC npc : CitizensAPI.getNPCRegistry()) {
            if (npcManager.getNPCType(npc) != null) found.add(npc);
        }
        found.forEach(NPC::destroy);
        return found.size();
    }

    /* =========================
       CONFIRMATION (/city confirm)
       ========================= */

    /**
     * Pose la marque, prévient les joueurs, puis (3 s plus tard) retire la ville du monde,
     * déconnecte tout le monde et arrête le serveur.
     *
     * @param removeFromWorld PNJ de la ville et coffre de départ retirés juste avant l'arrêt
     * @return false si la marque n'a pas pu être posée (rien n'est fait)
     */
    public boolean request(String by, Runnable removeFromWorld) {
        if (requested) return true;
        try {
            Files.createDirectories(plugin.getDataFolder().toPath());
            Files.writeString(new File(plugin.getDataFolder(), MARKER).toPath(),
                    "Réinitialisation demandée par " + by + " le "
                            + new SimpleDateFormat("dd/MM/yyyy HH:mm:ss").format(new Date()) + System.lineSeparator()
                            + "Appliquée au prochain démarrage du serveur (supprimer ce fichier pour l'annuler)."
                            + System.lineSeparator(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            plugin.getLogger().severe("❌ Réinitialisation : marque impossible à écrire (" + e + ").");
            return false;
        }
        requested = true;
        plugin.getLogger().warning("🔄 Réinitialisation demandée par " + by + " : arrêt du serveur.");
        com.citycore.discord.DiscordFeed.admin("admin.reinitialisation", String.valueOf(by),
                "réinitialise la partie : le serveur s'arrête, une nouvelle aventure commencera au redémarrage");

        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage("§c🔄 Valmont s'efface… §7Une nouvelle aventure va commencer.");
        Bukkit.broadcastMessage("§7Le serveur s'arrête dans 3 secondes pour tout remettre à zéro.");
        Bukkit.broadcastMessage("");
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendTitle("§cValmont s'efface", "§7Le serveur redémarre", 10, 60, 10);
            p.playSound(p.getLocation(), Sound.BLOCK_BELL_RESONATE, 1f, 0.6f);
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            try {
                removeFromWorld.run();
            } catch (RuntimeException e) {
                plugin.getLogger().warning("Réinitialisation : nettoyage du monde incomplet (" + e + ").");
            }
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.kick(LegacyComponentSerializer.legacySection().deserialize(
                        "§6Valmont s'efface…\n\n§7Le serveur redémarre : une nouvelle aventure commence."));
            }
            Bukkit.shutdown();
        }, 60L);
        return true;
    }
}
