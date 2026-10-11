package com.citycore.ui.screens;

import com.citycore.building.Building;
import com.citycore.building.BuildingCreation;
import com.citycore.building.BuildingManager;
import com.citycore.building.BuildingType;
import com.citycore.npc.CityNPC;
import com.citycore.npc.NPCManager;
import com.citycore.ui.UiCanvas;
import com.citycore.ui.UiItems;
import com.citycore.ui.UiKit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

import static com.citycore.ui.UiLayout.*;

/**
 * Écrans illustrés du type d'un bâtiment (phase 1 du plan « Valmont vivante ») : choix du type
 * (habitation, atelier, mairie, divers), puis de l'artisan pour qui l'atelier est réservé.
 * Servent à la création (le type d'abord, le nom est demandé ensuite dans le chat) et au
 * changement de type d'un bâtiment existant (clic droit dans la liste d'Alderic).
 * Les personnages pas encore trouvés sont verrouillés. Réutilisent les images du pack.
 */
public final class BuildingScreens {

    private final UiKit            kit;
    private final BuildingManager  buildings;
    private final BuildingCreation creation;
    private final NPCManager       npcManager;

    public BuildingScreens(UiKit kit, BuildingManager buildings, BuildingCreation creation, NPCManager npcManager) {
        this.kit        = kit;
        this.buildings  = buildings;
        this.creation   = creation;
        this.npcManager = npcManager;
    }

    /** Choix du type d'un nouveau bâtiment ; false = menu indisponible (choix dans le chat). */
    public boolean openNew(Player p) {
        return kit.active(p) && kit.show(new TypeChooser(p, null, null));
    }

    /** Changement de type d'un bâtiment existant ; {@code back} rouvre l'écran d'origine. */
    public boolean openRetype(Player p, Building building, Runnable back) {
        return kit.active(p) && kit.show(new TypeChooser(p, building, back));
    }

    /* =========================
       CHOIX DU TYPE
       ========================= */

    private final class TypeChooser extends ValmontMenu {

        private final Building existing; // null = création
        private final Runnable back;

        TypeChooser(Player p, Building existing, Runnable back) {
            super(BuildingScreens.this.kit, p);
            this.existing = existing;
            this.back     = back;
        }

        String title() {
            return existing == null ? "Nouveau bâtiment" : existing.name();
        }

        @Override
        protected void draw(UiCanvas c) {
            c.background("bg_grid");
            nameplate(c, title(), existing == null ? null : "Type");
            back(c, 0, existing == null ? "Annuler" : "Retour", this::leave);
            c.sprite("rib_batiments", sx(1), sy(0));
            List<String> help = new ArrayList<>(List.of(
                    "§7Le type range les bâtiments et décide",
                    "§7qui peut s'y installer :",
                    "§aHabitation §7: les habitants",
                    "§6Atelier §7: un artisan",
                    "§eMairie §7: Alderic (une seule)",
                    "§7Divers : personne"));
            if (existing == null) {
                help.add("");
                help.add("§8Le nom est demandé ensuite, dans le chat.");
            }
            info(c, 7, "§a🏗 Type du bâtiment", help);
            close(c);

            Building mairie = buildings.findMairie(existing == null ? -1 : existing.id());
            BuildingType[] types = BuildingType.values();
            for (int i = 0; i < types.length; i++) {
                BuildingType type = types[i];
                boolean current = existing != null && existing.type() == type;
                boolean blocked = type == BuildingType.MAIRIE && mairie != null;
                c.sprite(current ? "ring_gold" : blocked ? "ring_red" : "ring_green", sx(i), sy(1));

                List<String> lore = new ArrayList<>();
                for (String line : type.description) lore.add("§7" + line);
                lore.add("");
                if (blocked) {
                    lore.add("§cLa ville a déjà une mairie : §e" + mairie.name());
                } else if (current && type != BuildingType.ATELIER) {
                    lore.add("§6Type actuel");
                } else {
                    lore.add(type == BuildingType.ATELIER ? "§eCliquez pour choisir l'artisan" : "§eCliquez pour choisir");
                }
                item(i, 1, UiItems.shown(type.icon, type.coloredLabel(), lore, current),
                        blocked ? null : t -> pick(type));
            }

            pager(c, 0, 1, p -> { });
            footer(c, false, existing == null ? "QUEL BÂTIMENT CONSTRUIRE ?" : "CHANGER LE TYPE");
        }

        private void pick(BuildingType type) {
            if (type == BuildingType.ATELIER) {
                kit.show(new ArtisanChooser(player, this));
                return;
            }
            apply(type, null);
        }

        /** Applique le choix : demande du nom (création) ou changement de type. */
        void apply(BuildingType type, CityNPC owner) {
            if (existing == null) {
                creation.askName(player, type, owner); // ferme le menu et demande le nom dans le chat
            } else if (creation.retype(player, existing, type, owner)) {
                leave();
            }
        }

        void leave() {
            if (back != null) back.run();
            else player.closeInventory();
        }
    }

    /* =========================
       CHOIX DE L'ARTISAN D'UN ATELIER
       ========================= */

    private final class ArtisanChooser extends ValmontMenu {

        private final TypeChooser parent;

        ArtisanChooser(Player p, TypeChooser parent) {
            super(BuildingScreens.this.kit, p);
            this.parent = parent;
        }

        @Override
        protected void draw(UiCanvas c) {
            Building existing = parent.existing;
            int excludeId = existing == null ? -1 : existing.id();

            c.background("bg_grid");
            nameplate(c, parent.title(), "Atelier");
            back(c, 0, "Types", () -> kit.show(parent));
            c.sprite("rib_logis", sx(1), sy(0));
            info(c, 7, "§6🏠 Atelier de qui ?", List.of(
                    "§7Un atelier réservé n'accueille",
                    "§7que son artisan. Un atelier libre",
                    "§7reçoit le premier qui s'y installe.",
                    "",
                    "§a■ §7disponible   §c■ §7a déjà un atelier",
                    "§6■ §7réservation actuelle",
                    "§8🔒 pas encore trouvé"));
            close(c);

            // Case 0 : atelier libre
            boolean freeNow = existing != null && existing.type() == BuildingType.ATELIER && existing.reservedFor() == null;
            c.sprite(freeNow ? "ring_gold" : "ring_green", sx(0), sy(1));
            item(0, 1, UiItems.shown(Material.CRAFTING_TABLE, "§6Atelier libre",
                    List.of("§7Le premier artisan qui s'y", "§7installe le réserve.", "",
                            "§eCliquez pour choisir"), freeNow),
                    t -> parent.apply(BuildingType.ATELIER, null));

            // Cases suivantes : chaque artisan, dans l'ordre de la trame
            List<CityNPC> artisans = BuildingCreation.artisans();
            for (int i = 0; i < Math.min(GRID - 1, artisans.size()); i++) {
                CityNPC npc = artisans.get(i);
                int cell = i + 1, col = cell % 9, row = 1 + cell / 9;
                String name = ChatColor.stripColor(npc.displayName);

                // Pas encore trouvé : cadenas, aucune réservation possible
                if (!creation.isFound(npc)) {
                    c.sprite("ring_gray", sx(col), sy(row));
                    c.sprite("lock", sx(col) + 3, sy(row) + 3);
                    zone(col, row, 1, 1, "§8🔒 §7" + name, List.of("§7" + npc.function, "",
                            "§8Pas encore trouvé : retrouvez-le", "§8pour lui réserver un atelier."), null);
                    continue;
                }

                boolean current = existing != null && npc.tag.equals(existing.reservedFor());
                Building other = buildings.findReservedFor(npc.tag, excludeId);
                c.sprite(current ? "ring_gold" : other != null ? "ring_red" : "ring_green", sx(col), sy(row));

                List<String> lore = new ArrayList<>();
                lore.add("§7" + npc.function);
                lore.add("");
                if (other != null) {
                    lore.add("§cA déjà un atelier : §e" + other.name());
                } else if (current) {
                    lore.add("§6Réservation actuelle");
                } else {
                    lore.add("§eCliquez pour lui réserver l'atelier");
                }
                item(col, row, UiItems.head(npcManager.resolveSkin(npc), npc.tag, "§6Atelier de " + npc.displayName, lore),
                        other != null ? null : t -> parent.apply(BuildingType.ATELIER, npc));
            }

            pager(c, 0, 1, p -> { });
            footer(c, false, existing == null ? "ATELIER DE QUEL ARTISAN ?"
                    : "ATELIER : " + ChatColor.stripColor(existing.name()).toUpperCase());
        }
    }
}
