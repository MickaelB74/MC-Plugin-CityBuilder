package com.citycore.building;

import com.citycore.npc.CityNPC;
import com.citycore.util.ChatInputManager;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Création et changement de type des bâtiments (phase 1 du plan « Valmont vivante »).
 *
 * Parcours : type (et artisan pour un atelier) → nom, demandé dans le chat avec un nom proposé
 * selon le type → bâton de sélection de la zone → point du PNJ. Le choix du type passe par le
 * menu illustré, ou par des boutons cliquables dans le chat pour un joueur sans le pack.
 * Règles : une seule mairie ; un artisan n'a qu'un atelier ; on ne réserve un atelier qu'à un
 * personnage déjà trouvé (rencontré par un joueur).
 */
public class BuildingCreation {

    /** Ouvre le choix du type en menu illustré ; false = menu indisponible (chat à la place). */
    public interface TypeChooser {
        boolean open(Player player);
    }

    /** Longueur maximale d'un nom de bâtiment. */
    private static final int NAME_MAX = 24;

    /** Nom proposé pour l'atelier de chaque personnage (clé = prénom, voir CityNPC.skinId). */
    private static final Map<String, String> ATELIER_NAMES = Map.ofEntries(
            Map.entry("bastien", "Cabane_du_bucheron"), Map.entry("helene", "Grange"),
            Map.entry("brennan", "Atelier_du_tailleur"), Map.entry("martin", "Ponton"),
            Map.entry("godefroy", "Mine"), Map.entry("rosalie", "Taverne"),
            Map.entry("thorvald", "Forge"), Map.entry("agathe", "Teinturerie"),
            Map.entry("edmond", "Ecuries"), Map.entry("roland", "Caserne"),
            Map.entry("anselme", "Apothicairerie"), Map.entry("isabeau", "Scriptorium"),
            Map.entry("leon", "Verrerie"), Map.entry("beatrice", "Orfevrerie"),
            Map.entry("robin", "Repaire"), Map.entry("jacksparrow", "Capitainerie"),
            Map.entry("indiana", "Cabinet_de_curiosites"), Map.entry("docbrown", "Laboratoire"),
            Map.entry("gandalf", "Tour_du_mage"), Map.entry("geralt", "Antre_du_sorceleur"),
            Map.entry("harold", "Nid_de_dragon"), Map.entry("robinson", "Cabane_du_naufrage"),
            Map.entry("achab", "Baleiniere"), Map.entry("obelix", "Hutte_gauloise"),
            Map.entry("musashi", "Dojo"), Map.entry("geppetto", "Echoppe_d_ebeniste"),
            Map.entry("ragnar", "Chantier_naval"), Map.entry("jeanne", "Quartier_general"),
            Map.entry("hercule", "Palestre"));

    private final BuildingManager    buildings;
    private final BuildingSession    session;
    private final Predicate<CityNPC> found;
    private TypeChooser chooser;

    /**
     * Choix ouverts dans le chat (joueur sans le pack) : leurs boutons relancent /city build new
     * ou /city build type, réservées aux administrateurs ; un joueur peut les utiliser tant que le
     * choix qu'Alderic lui a proposé est ouvert (5 minutes). building = null : création.
     */
    private record ChatChoice(String building, long until) {}

    private static final long CHAT_CHOICE_MS = 5 * 60_000L;

    private final Map<UUID, ChatChoice> chatChoices = new HashMap<>();

    /**
     * @param found un personnage a-t-il été trouvé (rencontré, arrivé ou installé) ? Seuls
     *              ceux-là peuvent recevoir un atelier réservé.
     */
    public BuildingCreation(BuildingManager buildings, BuildingSession session, Predicate<CityNPC> found) {
        this.buildings = buildings;
        this.session   = session;
        this.found     = found;
    }

    public void setChooser(TypeChooser chooser) {
        this.chooser = chooser;
    }

    /** Inspection des constructions (phase 2) : rapport après création ou changement de type. */
    private BuildingInspector inspector;

    public void setInspector(BuildingInspector inspector) {
        this.inspector = inspector;
    }

    /** Une ligne : niveau du bâtiment, ou ce qu'il faut encore y mettre (détail : /city build inspect). */
    private void reportInspection(Player player, String name) {
        if (inspector == null) return;
        inspector.refreshBuildings();
        Building b = buildings.getByName(name);
        if (b == null) return;
        Inspection insp = inspector.recheck(b.id(), null);
        if (insp == null) return;
        player.sendMessage("§6🏗 " + insp.summary());
        player.sendMessage("§8Détail : /city build inspect " + b.name()
                + (b.type() == BuildingType.ATELIER ? " — ou l'écran « Son atelier » de l'artisan" : ""));
    }

    /** Exigences des bâtiments (null avant l'inspection) : affichées dans les écrans de choix. */
    public BuildingRequirements requirements() {
        return inspector == null ? null : inspector.requirements();
    }

    /** Le personnage a-t-il été trouvé (rencontré par un joueur, arrivé ou installé) ? */
    public boolean isFound(CityNPC npc) {
        return found.test(npc);
    }

    /* =========================
       CRÉATION : TYPE → NOM → ZONE
       ========================= */

    /** Étape 1 : choix du type (menu illustré, ou boutons dans le chat). */
    public void begin(Player player) {
        if (chooser != null && chooser.open(player)) return;
        sendChatChoices(player);
    }

    /** Étape 2 : type (et artisan d'un atelier) choisi → nom demandé dans le chat. */
    public void askName(Player player, BuildingType type, CityNPC owner) {
        if (!checkType(player, type, owner, -1)) return;
        String suggestion = suggestedName(type, owner);
        TextComponent hint = new TextComponent(TextComponent.fromLegacyText("§7Un seul mot (les espaces deviennent « _ »). Exemple : "));
        hint.addExtra(button("§e[" + suggestion + "]", ClickEvent.Action.SUGGEST_COMMAND, suggestion,
                List.of("Cliquez pour reprendre ce nom,", "puis validez avec Entrée.")));
        ChatInputManager.prompt(player, namePrompt(type, owner), hint, typed -> {
            String name = cleanName(typed);
            if (name.isEmpty()) {
                player.sendMessage("§c❌ Nom vide.");
                askName(player, type, owner);
            } else if (buildings.nameExists(name)) {
                player.sendMessage("§c❌ Un bâtiment s'appelle déjà §f" + name + "§c. Choisissez un autre nom.");
                askName(player, type, owner);
            } else {
                choose(player, name, type, owner);
            }
        });
    }

    /** Étape 3 : type et nom choisis → bâton de sélection de la zone. */
    public void choose(Player player, String name, BuildingType type, CityNPC owner) {
        name = cleanName(name);
        if (!checkName(player, name) || !checkType(player, type, owner, -1)) return;
        chatChoices.remove(player.getUniqueId()); // type et nom choisis : le choix du chat est clos
        session.clear(player.getUniqueId());
        session.setPending(player.getUniqueId(), name, type, owner != null ? owner.tag : null);

        ItemStack stick = new ItemStack(BuildingListener.SELECTION_TOOL);
        ItemMeta meta   = stick.getItemMeta();
        meta.setDisplayName("§e🏗 Sélection : §f" + name);
        meta.setLore(List.of(
                "§7Type : " + label(type, owner),
                "§7Clic gauche §f: Coin 1",
                "§7Clic droit §f: Coin 2",
                "§8Cliquez au sol : le bas de la zone",
                "§8est pris sur les coins cliqués."
        ));
        stick.setItemMeta(meta);
        player.getInventory().addItem(stick);
        player.sendMessage("§a✅ Mode sélection activé pour §e" + name + " §7(" + label(type, owner) + "§7) !");
        player.sendMessage("§7Cliquez les deux coins de la zone, au sol, avec le bâton.");
    }

    /**
     * Étape finale : enregistre le bâtiment. Si une règle a été enfreinte entre-temps (une
     * mairie ou l'atelier de cet artisan créés par un autre joueur), le bâtiment est gardé sous
     * un type compatible et le joueur est prévenu.
     */
    public void finish(Player player, BuildingSession.PendingBuilding p,
                       Double npcX, Double npcY, Double npcZ, Float npcYaw) {
        BuildingType type = p.type();
        String reserved   = p.reservedFor();
        if (type == BuildingType.MAIRIE && buildings.findMairie(-1) != null) {
            player.sendMessage("§6⚠ Une mairie a été créée entre-temps : §e" + p.name() + " §6sera de type Divers.");
            type = BuildingType.DIVERS;
        }
        if (type == BuildingType.ATELIER && reserved != null && buildings.findReservedFor(reserved, -1) != null) {
            player.sendMessage("§6⚠ Cet artisan a déjà un atelier : §e" + p.name() + " §6sera un atelier libre.");
            reserved = null;
        }
        if (type == BuildingType.ANNEXE && (reserved == null || buildings.findAnnex(reserved, -1) != null)) {
            player.sendMessage("§6⚠ Cet artisan a déjà une annexe : §e" + p.name() + " §6sera de type Divers.");
            type = BuildingType.DIVERS;
            reserved = null;
        }
        buildings.createBuilding(p.name(), p.world(), p.x1(), p.z1(), p.x2(), p.z2(),
                npcX, npcY, npcZ, npcYaw, type, reserved, p.yMin(), p.yMax(), p.refY());
        CityNPC owner = reserved != null ? CityNPC.fromTag(reserved) : null;
        player.sendMessage("§a✅ Bâtiment §e" + p.name() + " §acréé §7(" + label(type, owner)
                + "§7, sol Y " + p.refY() + ")§a !");
        player.sendMessage("§8Tout ce qui est construit de " + BuildingHeights.depth() + " blocs sous le sol à "
                + BuildingHeights.height() + " blocs au-dessus compte (terrain naturel exclu de la variété).");
        reportInspection(player, p.name());
    }

    /* =========================
       CHANGEMENT DE TYPE
       ========================= */

    /** Change le type d'un bâtiment existant ; renvoie true si c'est fait. */
    public boolean retype(Player player, Building b, BuildingType type, CityNPC owner) {
        if (b.npcTag() != null) {
            // Un artisan habite ici : le bâtiment reste son atelier
            CityNPC tenant = CityNPC.fromTag(b.npcTag());
            if (type != BuildingType.ATELIER || (owner != null && !owner.tag.equals(b.npcTag()))) {
                player.sendMessage("§c❌ " + (tenant != null ? tenant.displayName : b.npcDisplayTag())
                        + " §chabite §e" + b.name() + "§c : ce bâtiment reste son atelier.");
                return false;
            }
            owner = tenant;
        } else if (!checkType(player, type, owner, b.id())) {
            return false;
        }
        buildings.setType(b.id(), type, owner != null ? owner.tag : null);
        chatChoices.remove(player.getUniqueId());
        player.sendMessage("§a✅ §e" + b.name() + " §a: " + label(type, owner) + "§a.");
        reportInspection(player, b.name()); // nouvelles exigences : nouveau niveau
        return true;
    }

    /* =========================
       RÈGLES
       ========================= */

    private boolean checkName(Player player, String name) {
        if (name == null || name.isBlank()) {
            player.sendMessage("§c❌ Nom de bâtiment vide.");
            return false;
        }
        if (buildings.nameExists(name)) {
            player.sendMessage("§c❌ Bâtiment §f" + name + " §cdéjà existant.");
            return false;
        }
        return true;
    }

    /**
     * Une seule mairie ; un artisan n'a qu'un atelier et doit avoir été trouvé
     * (excludeId = bâtiment modifié, -1 sinon).
     */
    public boolean checkType(Player player, BuildingType type, CityNPC owner, int excludeId) {
        if (type == BuildingType.MAIRIE) {
            Building mairie = buildings.findMairie(excludeId);
            if (mairie != null) {
                player.sendMessage("§c❌ La ville a déjà une mairie : §e" + mairie.name() + "§c.");
                return false;
            }
        }
        if (type == BuildingType.ANNEXE) {
            if (owner == null) {
                player.sendMessage("§c❌ Une annexe de travail se crée depuis les Actions d'un artisan.");
                return false;
            }
            Building other = buildings.findAnnex(owner.tag, excludeId);
            if (other != null) {
                player.sendMessage("§c❌ " + owner.displayName + " §ca déjà une annexe : §e" + other.name() + "§c.");
                return false;
            }
        }
        if (type == BuildingType.ATELIER && owner != null) {
            if (!isFound(owner)) {
                player.sendMessage("§c❌ " + owner.displayName + " §cn'a pas encore été trouvé : "
                        + "impossible de lui réserver un atelier.");
                return false;
            }
            Building other = buildings.findReservedFor(owner.tag, excludeId);
            if (other != null) {
                player.sendMessage("§c❌ " + owner.displayName + " §ca déjà un atelier : §e" + other.name() + "§c.");
                return false;
            }
        }
        return true;
    }

    /* =========================
       CHOIX DANS LE CHAT (joueur sans le pack)
       ========================= */

    /**
     * Changement de type depuis la liste classique d'Alderic (clic droit) : boutons dans le chat,
     * comme pour une création. (Avec le pack, c'est l'écran illustré qui s'en charge.)
     */
    public void beginRetype(Player player, Building b) {
        player.closeInventory();
        sendChatChoices(player, b);
    }

    /** Un choix du chat proposé par Alderic est-il ouvert pour ce bâtiment (null : une création) ? */
    public boolean hasChatChoice(Player player, String building) {
        ChatChoice choice = chatChoices.get(player.getUniqueId());
        if (choice == null) return false;
        if (System.currentTimeMillis() > choice.until()) {
            chatChoices.remove(player.getUniqueId());
            return false;
        }
        return building == null ? choice.building() == null
                : choice.building() != null && choice.building().equalsIgnoreCase(building);
    }

    private void sendChatChoices(Player player) {
        sendChatChoices(player, null);
    }

    /** Boutons du chat : type (et artisan d'un atelier) d'un nouveau bâtiment, ou d'un bâtiment existant. */
    private void sendChatChoices(Player player, Building existing) {
        chatChoices.put(player.getUniqueId(), new ChatChoice(existing == null ? null : existing.name(),
                System.currentTimeMillis() + CHAT_CHOICE_MS));
        String command = existing == null ? "/city build new " : "/city build type " + existing.name() + " ";
        if (existing == null) {
            player.sendMessage("§6🏗 Quel bâtiment construire ? §7(cliquez)");
        } else {
            CityNPC current = existing.reservedFor() == null ? null : CityNPC.fromTag(existing.reservedFor());
            player.sendMessage("§6🏗 Nouveau type de §e" + existing.name() + " §6? §7(cliquez ; actuel : "
                    + label(existing.type(), current) + "§7)");
        }
        TextComponent types = new TextComponent("  ");
        BuildingRequirements req = requirements();
        for (BuildingType type : BuildingType.choosable()) {
            String label = type == BuildingType.ATELIER ? "Atelier libre" : type.label;
            List<String> hover = new ArrayList<>(type.description);
            if (req != null) { // prérequis du type (phase 2)
                hover.add("");
                hover.add("§6Prérequis :");
                List<String> lines = BuildingRequirements.lore(req.specFor(type));
                hover.addAll(lines.isEmpty() ? List.of("aucun") : lines);
            }
            types.addExtra(button(type.color + "[" + label + "]", ClickEvent.Action.RUN_COMMAND,
                    command + type.key(), hover));
            types.addExtra(" ");
        }
        player.spigot().sendMessage(types);

        TextComponent artisans = new TextComponent(TextComponent.fromLegacyText("§7  Atelier réservé à : "));
        int locked = 0;
        for (CityNPC npc : artisans()) {
            if (!isFound(npc)) { locked++; continue; }
            List<String> hover = new ArrayList<>(List.of("Atelier de " + ChatColor.stripColor(npc.displayName), npc.function));
            if (req != null) { // ce que devra contenir son atelier (phase 2)
                hover.add("");
                hover.add("§6Son atelier devra contenir :");
                hover.addAll(BuildingRequirements.lore(req.specFor(npc)));
            }
            artisans.addExtra(button("§6[" + ChatColor.stripColor(npc.displayName) + "]", ClickEvent.Action.RUN_COMMAND,
                    command + BuildingType.ATELIER.key() + " " + npc.skinId, hover));
            artisans.addExtra(" ");
        }
        if (locked > 0) artisans.addExtra(new TextComponent(TextComponent.fromLegacyText(
                "§8(🔒 " + locked + " personnage" + (locked > 1 ? "s" : "") + " pas encore trouvé" + (locked > 1 ? "s" : "") + ")")));
        player.spigot().sendMessage(artisans);
    }

    private static TextComponent button(String text, ClickEvent.Action action, String value, List<String> hover) {
        TextComponent button = new TextComponent(TextComponent.fromLegacyText(text));
        button.setClickEvent(new ClickEvent(action, value));
        button.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new Text(new ComponentBuilder("§7" + String.join("\n§7", hover)).create())));
        return button;
    }

    /* =========================
       NOMS
       ========================= */

    /** Question posée selon le type (« Nom de l'atelier de Thorvald : »). */
    private static String namePrompt(BuildingType type, CityNPC owner) {
        return switch (type) {
            case HABITATION -> "§a🏠 §7Nom de la nouvelle §ahabitation §7:";
            case ATELIER    -> owner != null
                    ? "§6⚒ §7Nom de l'§6atelier de " + owner.displayName + " §8(" + owner.function + ")§7 :"
                    : "§6⚒ §7Nom du nouvel §6atelier libre §7:";
            case ANNEXE     -> "§3⚒ §7Nom de l'§3annexe de travail" + (owner != null ? " de " + owner.displayName : "") + " §7:";
            case MAIRIE     -> "§e🏛 §7Nom de la §emairie §7:";
            case DIVERS     -> "§7🏗 Nom du bâtiment §8(entrepôt, place, rempart…)§7 :";
        };
    }

    /** Nom proposé, rendu unique (« Maison », « Maison_2 »…). */
    private String suggestedName(BuildingType type, CityNPC owner) {
        String base = switch (type) {
            case HABITATION -> "Maison";
            case ATELIER    -> owner != null ? ATELIER_NAMES.getOrDefault(owner.skinId, "Atelier_de_" + owner.skinId) : "Atelier";
            case ANNEXE     -> owner != null ? "Annexe_de_" + owner.skinId : "Annexe";
            case MAIRIE     -> "Hotel_de_ville";
            case DIVERS     -> "Entrepot";
        };
        String name = base;
        for (int n = 2; buildings.nameExists(name); n++) name = base + "_" + n;
        return name;
    }

    /** Un seul mot (il passe dans des commandes), sans codes de couleur, 24 caractères au plus. */
    private static String cleanName(String typed) {
        String name = ChatColor.stripColor(typed == null ? "" : typed).trim().replaceAll("\\s+", "_");
        return name.length() > NAME_MAX ? name.substring(0, NAME_MAX) : name;
    }

    /* =========================
       OUTILS
       ========================= */

    /** « Habitation », « Atelier de Thorvald », « Atelier libre »… (coloré). */
    public static String label(BuildingType type, CityNPC owner) {
        if (type == BuildingType.ANNEXE) return owner != null ? type.color + "Annexe de " + owner.displayName : type.coloredLabel();
        if (type != BuildingType.ATELIER) return type.coloredLabel();
        return owner != null ? type.color + "Atelier de " + owner.displayName : type.color + "Atelier libre";
    }

    /** Artisans qui peuvent avoir un atelier (tous sauf le maire), dans l'ordre de la trame. */
    public static List<CityNPC> artisans() {
        // Ordre de la trame : les survivants chapitre par chapitre, puis les légendes de même
        // (les légendes liées aux mods sont déclarées après les autres dans CityNPC)
        List<CityNPC> survivors = new ArrayList<>(), legends = new ArrayList<>();
        for (com.citycore.quest.city.CityTier tier : com.citycore.quest.city.CityTier.values()) {
            for (com.citycore.quest.city.CityQuest q : tier.quests) {
                CityNPC npc = q.targetNpc();
                if (q.type() != com.citycore.quest.city.CityQuestType.FIND_NPC || npc == null
                        || survivors.contains(npc) || legends.contains(npc)) continue;
                (q.optional() ? legends : survivors).add(npc);
            }
        }
        List<CityNPC> list = new ArrayList<>(survivors);
        list.addAll(legends);
        for (CityNPC npc : CityNPC.values()) {
            if (npc != CityNPC.MAYOR && !list.contains(npc)) list.add(npc); // sans quête de recrutement
        }
        return list;
    }

    /** Artisan écrit dans une commande : prénom (« thorvald », « helene ») ou clé (« blacksmith »). */
    public static CityNPC parseArtisan(String text) {
        if (text == null) return null;
        String plain = Normalizer.normalize(text.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase();
        for (CityNPC npc : artisans()) {
            if (npc.skinId.equalsIgnoreCase(plain) || npc.tag.replace("citycore_", "").equalsIgnoreCase(plain)) {
                return npc;
            }
        }
        return null;
    }
}
