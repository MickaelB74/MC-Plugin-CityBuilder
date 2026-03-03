package com.citycore.npc.mayor;

import com.citycore.building.Building;
import com.citycore.building.BuildingManager;
import com.citycore.npc.CityNPC;
import com.citycore.building.BuildingParticleTask;
import com.citycore.util.ChatInputManager;
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

    public MayorBuildingGUI(BuildingManager buildingManager, JavaPlugin plugin) {
        this.buildingManager = buildingManager;
        this.plugin          = plugin;
    }

    // ── Menu principal : liste des bâtiments ─────────────────────────────────

    public void open(Player player) {
        List<Building> buildings = buildingManager.getAllBuildings();

        Inventory inv = Bukkit.createInventory(null, 36, GUI_TITLE);

        ItemStack filler = makeItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 27; i < 36; i++) inv.setItem(i, filler);

        int max = Math.min(buildings.size(), CONTENT_ROWS * 9);
        for (int i = 0; i < max; i++) {
            Building b = buildings.get(i);
            inv.setItem(i, makeItem(
                    Material.BRICKS,
                    "§e" + b.name(),
                    List.of(
                            "§7Monde : §f" + b.world(),
                            "§7Zone  : §f" + b.x1() + "," + b.z1()
                                    + " §7→ §f" + b.x2() + "," + b.z2(),
                            "§7NPC   : " + npcDisplayName(b),
                            "",
                            "§eCliquez pour voir les bordures"
                    )
            ));
        }

        inv.setItem(27 + SLOT_ADD, makeItem(
                Material.LIME_DYE,
                "§a➕ Ajouter un bâtiment",
                List.of(
                        "§7Entrez le nom du bâtiment",
                        "§7puis sélectionnez la zone avec WorldEdit",
                        "",
                        "§eCliquez pour saisir le nom"
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
            if (slot < buildings.size()) {
                Building b = buildings.get(slot);
                // Ferme au tick suivant pour éviter le bug Bukkit
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    player.closeInventory();
                    new BuildingParticleTask(plugin, player, b).runForSeconds(5);
                    player.sendMessage("§b🏛 §7Bordures de §f" + b.name()
                            + " §7affichées pendant §f5 secondes§7.");
                });
            }
            return true;
        }

        if (slot == 27 + SLOT_ADD) {
            ChatInputManager.prompt(
                    player,
                    "§a➕ §7Entrez le §enom §7du nouveau bâtiment :",
                    name -> {
                        if (buildingManager.nameExists(name)) {
                            player.sendMessage("§c❌ Un bâtiment nommé §e" + name + " §cexiste déjà.");
                            open(player);
                            return;
                        }
                        player.performCommand("city build new " + name);
                    }
            );
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
            if (slot < buildings.size()) {
                Building b = buildings.get(slot);
                boolean removed = buildingManager.removeByName(b.name());
                if (removed) {
                    player.sendMessage("§a✅ Bâtiment §e" + b.name() + " §asupprimé.");
                } else {
                    player.sendMessage("§c❌ Erreur lors de la suppression.");
                }
                openDelete(player);
            }
            return true;
        }
        return true;
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