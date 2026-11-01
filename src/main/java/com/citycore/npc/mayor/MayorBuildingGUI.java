package com.citycore.npc.mayor;

import com.citycore.building.Building;
import com.citycore.building.BuildingManager;
import com.citycore.building.BuildingType;
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

/**
 * Bâtiments de la ville chez Alderic : seulement les bâtiments principaux (habitations, ateliers,
 * mairie, divers). Les annexes de travail se créent et se suppriment chez leur artisan (Actions).
 * Supprimer : bouton de la liste, puis clic sur le bâtiment, puis confirmation.
 */
public class MayorBuildingGUI {

    public static final String GUI_TITLE         = ChatColor.DARK_GREEN + "Bâtiments de la ville";
    public static final String GUI_TITLE_DELETE  = ChatColor.DARK_RED   + "Supprimer un bâtiment";
    /** Confirmation de suppression (menu classique) : préfixe suivi du nom du bâtiment. */
    public static final String GUI_TITLE_CONFIRM = ChatColor.DARK_RED   + "Supprimer · ";

    public static final int SLOT_ADD    = 3;
    public static final int SLOT_DELETE = 5;
    public static final int SLOT_BACK   = 4;

    /** Confirmation (menu classique, 27 cases) : annuler, le bâtiment, confirmer. */
    private static final int CONFIRM_CANCEL = 11;
    private static final int CONFIRM_INFO   = 13;
    private static final int CONFIRM_OK     = 15;

    private static final int CONTENT_ROWS = 3;

    private final BuildingManager buildingManager;
    private final JavaPlugin      plugin;
    /** Bâtiment dont la suppression attend confirmation, par joueur (menu classique). */
    private final java.util.Map<java.util.UUID, Integer> pendingDelete = new java.util.HashMap<>();
    private com.citycore.ui.screens.MayorScreens themed;

    void setThemed(com.citycore.ui.screens.MayorScreens themed) {
        this.themed = themed;
    }

    /** Création et changement de type (choix dans le chat sans le pack), inspection détaillée. */
    private com.citycore.building.BuildingCreation  creation;
    private com.citycore.building.BuildingInspector inspector;

    public void setCreation(com.citycore.building.BuildingCreation creation,
                            com.citycore.building.BuildingInspector inspector) {
        this.creation  = creation;
        this.inspector = inspector;
    }

    public MayorBuildingGUI(BuildingManager buildingManager, JavaPlugin plugin) {
        this.buildingManager = buildingManager;
        this.plugin          = plugin;
    }

    /** Bâtiments gérés par Alderic : tous sauf les annexes de travail (gérées chez leur artisan). */
    public List<Building> mainBuildings() {
        return buildingManager.getAllBuildings().stream()
                .filter(b -> b.type() != BuildingType.ANNEXE).toList();
    }

    /** Ce qu'entraîne la suppression d'un bâtiment (ligne de la confirmation). */
    public static String deletionEffect(Building b) {
        return switch (b.type()) {
            case HABITATION -> "§cSes habitants quittent la ville.";
            case ATELIER    -> "§cSon artisan perd son atelier.";
            case MAIRIE     -> "§cLa ville n'a plus de mairie.";
            default         -> "§7Rien d'autre ne change.";
        };
    }

    // ── Menu principal : liste des bâtiments ─────────────────────────────────

    public void open(Player player) {
        if (themed != null && themed.openBuildings(player)) return;
        List<Building> buildings = mainBuildings();

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
                            "§eClic gauche §7: voir les bordures",
                            "§eClic droit §7: changer le type",
                            "§eMaj + clic §7: inspection détaillée"
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
        List<Building> buildings = mainBuildings();

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
                            "§cCliquez pour supprimer §8(confirmation)"
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

    /** Confirmation de la suppression d'un bâtiment (menu classique). */
    private void openConfirm(Player player, Building b) {
        pendingDelete.put(player.getUniqueId(), b.id());
        Inventory inv = Bukkit.createInventory(null, 27, GUI_TITLE_CONFIRM + ChatColor.stripColor(b.name()));
        ItemStack filler = makeItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 27; i++) inv.setItem(i, filler);
        List<String> effects = List.of("§7Retiré de la ville (ses blocs", "§7restent en place).",
                deletionEffect(b), "", "§c§lDéfinitif.");
        inv.setItem(CONFIRM_CANCEL, makeItem(Material.LIME_CONCRETE, "§a◀ Annuler", List.of("§7Le bâtiment est conservé.")));
        inv.setItem(CONFIRM_INFO, makeItem(b.type().icon, "§c" + b.name(), effects));
        inv.setItem(CONFIRM_OK, makeItem(Material.RED_CONCRETE, "§c✖ Confirmer la suppression", effects));
        // Ouvert au tick suivant : appelé pendant le clic sur la liste
        plugin.getServer().getScheduler().runTask(plugin, () -> player.openInventory(inv));
    }

    // ── Handlers de clic ─────────────────────────────────────────────────────

    /**
     * @return true si géré, false = retour menu maire
     */
    public boolean handleMainClick(Player player, int slot, org.bukkit.event.inventory.ClickType click) {
        // ── Clic sur un bâtiment de la liste (slots 0 à 26) ──────
        // Gauche : bordures ; droit : changer le type (boutons dans le chat) ; Maj : inspection
        if (slot < 27) {
            List<Building> buildings = mainBuildings();
            if (slot >= buildings.size()) return true;
            Building b = buildings.get(slot);
            if (click.isShiftClick()) {
                inspect(player, b);
            } else if (click.isRightClick() && creation != null) {
                plugin.getServer().getScheduler().runTask(plugin, () -> creation.beginRetype(player, b));
            } else {
                showBorders(player, b);
            }
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
        // Directement (et non par /city build new, réservée aux administrateurs)
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (creation != null) creation.begin(player);
        });
    }

    /** Inspection détaillée d'un bâtiment dans le chat (même rapport que /city build inspect). */
    public void inspect(Player player, Building b) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            player.closeInventory();
            if (inspector == null) return;
            com.citycore.building.Inspection insp = inspector.recheck(b.id(), player);
            if (insp != null) com.citycore.building.BuildingInspector.report(player, insp);
        });
    }

    /**
     * Clic dans la liste de suppression : ouvre la confirmation du bâtiment cliqué.
     * @return true si géré
     */
    public boolean handleDeleteClick(Player player, int slot) {
        if (slot == 27 + SLOT_BACK) {
            open(player);
            return true;
        }
        if (slot < 27) {
            List<Building> buildings = mainBuildings();
            if (slot < buildings.size()) openConfirm(player, buildings.get(slot));
            return true;
        }
        return true;
    }

    /** Clic dans la confirmation (menu classique) : supprimer, ou revenir à la liste de suppression. */
    public boolean handleConfirmClick(Player player, int slot) {
        if (slot != CONFIRM_OK && slot != CONFIRM_CANCEL) return true;
        Integer id = pendingDelete.remove(player.getUniqueId());
        Building b = id == null ? null : buildingManager.getAllBuildings().stream()
                .filter(x -> x.id() == id).findFirst().orElse(null);
        if (slot == CONFIRM_OK && b != null) delete(player, b);
        else plugin.getServer().getScheduler().runTask(plugin, () -> openDelete(player));
        return true;
    }

    /**
     * Supprime un bâtiment principal (après confirmation) puis rouvre la liste de suppression.
     * Une annexe de travail est refusée : elle se supprime chez son artisan.
     */
    public void delete(Player player, Building b) {
        if (b.type() == BuildingType.ANNEXE) {
            player.sendMessage("§c❌ Une annexe de travail se supprime chez son artisan (Actions).");
        } else if (buildingManager.removeByName(b.name())) {
            player.sendMessage("§a✅ Bâtiment §e" + b.name() + " §asupprimé.");
        } else {
            player.sendMessage("§c❌ Erreur lors de la suppression.");
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> openDelete(player));
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
