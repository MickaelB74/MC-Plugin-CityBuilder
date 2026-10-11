package com.citycore.npc.mayor;

import com.citycore.building.Building;
import com.citycore.building.BuildingManager;
import com.citycore.npc.CityNPC;
import com.citycore.building.BuildingParticleTask;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public class MayorBuildingGUI {

    public static final String GUI_TITLE        = ChatColor.DARK_GREEN + "Bâtiments de la ville";
    public static final String GUI_TITLE_DELETE = ChatColor.DARK_RED   + "Supprimer un bâtiment";

    public static final int SLOT_ADD    = 3;
    public static final int SLOT_DELETE = 5;
    public static final int SLOT_BACK   = 4;

    private static final int CONTENT_ROWS = 3;

    private final BuildingManager buildingManager;
    private final JavaPlugin      plugin;
    private com.citycore.ui.screens.MayorScreens themed;

    void setThemed(com.citycore.ui.screens.MayorScreens themed) {
        this.themed = themed;
    }

    public MayorBuildingGUI(BuildingManager buildingManager, JavaPlugin plugin) {
        this.buildingManager = buildingManager;
        this.plugin          = plugin;
    }

    // ── Menu principal : liste des bâtiments ─────────────────────────────────

    public void open(Player player) {
        if (themed != null && themed.openBuildings(player)) return;
        List<Building> buildings = buildingManager.getAllBuildings();

        Inventory inv = Bukkit.createInventory(null, 36, GUI_TITLE);

        ItemStack filler = makeItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 27; i < 36; i++) inv.setItem(i, filler);

        int max = Math.min(buildings.size(), CONTENT_ROWS * 9);
        for (int i = 0; i < max; i++) {
            Building b = buildings.get(i);
            inv.setItem(i, makeItem(
                    b.type().icon,
                    "§e" + b.name(),
                    List.of(
                            b.typeLine(),
                            b.levelLine(),
                            "§7Zone  : §f" + b.x1() + "," + b.z1()
                                    + " §7→ §f" + b.x2() + "," + b.z2(),
                            b.heightLine(),
                            "§7NPC   : " + npcDisplayName(b),
                            "",
                            "§eCliquez pour voir les bordures",
                            "§8Type : /city build type " + b.name() + " <type>"
                    )
            ));
        }

        inv.setItem(27 + SLOT_ADD, makeItem(
                Material.LIME_DYE,
                "§a➕ Ajouter un bâtiment",
                List.of(
                        "§7Choisissez le type, donnez un nom,",
                        "§7puis délimitez la zone avec le bâton.",
                        "",
                        "§eCliquez pour choisir le type"
                )
        ));

        inv.setItem(27 + SLOT_DELETE, makeItem(
                Material.RED_DYE,
                "§c➖ Supprimer un bâtiment",
                List.of("§7Sélectionnez un bâtiment à retirer")
        ));

        inv.setItem(27 + SLOT_BACK, makeItem(
                Material.ARROW,
                "§7◀ Retour",
                List.of("§7Retourner au menu principal")
        ));

        player.openInventory(inv);
    }

    // ── Sous-menu suppression ─────────────────────────────────────────────────

    public void openDelete(Player player) {
        if (themed != null && themed.openDelete(player)) return;
        List<Building> buildings = buildingManager.getAllBuildings();

        Inventory inv = Bukkit.createInventory(null, 36, GUI_TITLE_DELETE);

        ItemStack filler = makeItem(Material.RED_STAINED_GLASS_PANE, " ");
        for (int i = 27; i < 36; i++) inv.setItem(i, filler);

        int max = Math.min(buildings.size(), CONTENT_ROWS * 9);
        for (int i = 0; i < max; i++) {
            Building b = buildings.get(i);
            inv.setItem(i, makeItem(
                    Material.TNT,
                    "§c" + b.name(),
                    List.of(
                            "§7Zone : §f" + b.x1() + "," + b.z1()
                                    + " §7→ §f" + b.x2() + "," + b.z2(),
                            "§7NPC  : " + npcDisplayName(b),
                            "",
                            "§cCliquez pour supprimer"
                    )
            ));
        }

        inv.setItem(27 + SLOT_BACK, makeItem(
                Material.ARROW,
                "§7◀ Retour",
                List.of("§7Retourner à la liste")
        ));

        player.openInventory(inv);
    }

    // ── Handlers de clic ─────────────────────────────────────────────────────

    /**
     * @return true si géré, false = retour menu maire
     */
    public boolean handleMainClick(Player player, int slot) {
        // ── Clic sur un bâtiment de la liste (slots 0 à 26) ──────
        if (slot < 27) {
            List<Building> buildings = buildingManager.getAllBuildings();
            if (slot < buildings.size()) showBorders(player, buildings.get(slot));
            return true;
        }

        if (slot == 27 + SLOT_ADD) {
            promptAdd(player);
            return true;
        }
        if (slot == 27 + SLOT_DELETE) {
            openDelete(player);
            return true;
        }
        if (slot == 27 + SLOT_BACK) {
            return false; // signale au listener de revenir au menu maire
        }
        return true;
    }

    /** Affiche les bordures d'un bâtiment pendant 5 secondes. */
    public void showBorders(Player player, Building b) {
        // Ferme au tick suivant pour éviter le bug Bukkit
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            player.closeInventory();
            new BuildingParticleTask(plugin, player, b).runForSeconds(5);
            player.sendMessage("§b🏛 §7Bordures de §f" + b.name()
                    + " §7affichées pendant §f5 secondes§7.");
        });
    }

    /**
     * Nouveau bâtiment : choix du type d'abord (menu ou boutons dans le chat), puis le nom est
     * demandé dans le chat avec une proposition adaptée au type (voir BuildingCreation).
     * Lancé au tick suivant : le menu classique appelle ceci pendant un événement de clic.
     */
    public void promptAdd(Player player) {
        plugin.getServer().getScheduler().runTask(plugin, () -> player.performCommand("city build new"));
    }

    /**
     * @return true si géré
     */
    public boolean handleDeleteClick(Player player, int slot) {
        if (slot == 27 + SLOT_BACK) {
            open(player);
            return true;
        }
        if (slot < 27) {
            List<Building> buildings = buildingManager.getAllBuildings();
            if (slot < buildings.size()) delete(player, buildings.get(slot));
            return true;
        }
        return true;
    }

    /** Supprime un bâtiment puis rouvre la liste de suppression. */
    public void delete(Player player, Building b) {
        boolean removed = buildingManager.removeByName(b.name());
        if (removed) {
            player.sendMessage("§a✅ Bâtiment §e" + b.name() + " §asupprimé.");
        } else {
            player.sendMessage("§c❌ Erreur lors de la suppression.");
        }
        openDelete(player);
    }

    /** Nom du PNJ installé dans un bâtiment (ou « Aucun »). */
    public String occupant(Building b) {
        return npcDisplayName(b);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private String npcDisplayName(Building b) {
        if (b.npcTag() == null) return "§7Aucun";
        CityNPC npc = CityNPC.fromTag(b.npcTag());
        return npc != null ? npc.displayName : b.npcDisplayTag();
    }

    private ItemStack makeItem(Material mat, String name, List<String> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta  meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack makeItem(Material mat, String name) {
        return makeItem(mat, name, List.of());
    }
}