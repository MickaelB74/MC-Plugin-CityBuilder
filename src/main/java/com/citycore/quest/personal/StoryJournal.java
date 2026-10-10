package com.citycore.quest.personal;

import com.citycore.integration.ModIntegrations;
import com.citycore.npc.CityNPC;
import com.citycore.npc.NPCDataManager;
import com.citycore.npc.villager.VillagerGUI;
import com.citycore.quest.QuestGUI;
import com.citycore.quest.city.CityQuest;
import com.citycore.quest.city.CityQuestType;
import com.citycore.quest.city.CityTier;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Journal des récits : où en est un joueur dans les récits (quêtes personnelles) de chaque
 * personnage. Sert l'onglet « Récits » du journal de quêtes (/city quests), illustré ou classique.
 *
 * Les personnages sont rangés dans l'ordre de la trame : les survivants, puis les légendes,
 * chacun dans l'ordre des chapitres où on les recrute. Un personnage pas encore à Valmont est
 * verrouillé ; un personnage installé ouvre l'écran de ses récits (le même que chez lui, avec un
 * retour vers le journal). Un personnage lié à un mod absent reste indisponible, comme chez lui.
 */
public final class StoryJournal {

    /** Où en est le personnage dans la ville. */
    public enum Status {
        /** Pas encore à Valmont (à retrouver) : verrouillé. */
        LOCKED,
        /** Arrivé en ville, pas encore installé dans un bâtiment. */
        IN_TOWN,
        /** Installé, mais son mod n'est pas sur ce serveur. */
        MOD_MISSING,
        /** Installé : ses récits sont consultables. */
        AVAILABLE
    }

    /** Un récit et son état pour le joueur. */
    public record Story(NPCPersonalQuest quest, int rank, boolean unlocked, boolean done, boolean active,
                        int progress) {
        /** À accepter : débloqué, ni en cours ni accompli. */
        public boolean open() {
            return unlocked && !done && !active;
        }
    }

    /** Un personnage du journal, ses récits dans l'ordre des rangs. */
    public record Entry(CityNPC npc, CityTier tier, boolean legend, Status status, int rank, List<Story> stories) {
        public int done()     { return (int) stories.stream().filter(Story::done).count(); }
        public int active()   { return (int) stories.stream().filter(s -> s.active() && !s.done()).count(); }
        public int toAccept() { return (int) stories.stream().filter(Story::open).count(); }
        public int total()    { return stories.size(); }
        /** Ses récits s'ouvrent depuis le journal. */
        public boolean clickable() { return status == Status.AVAILABLE; }
    }

    /** Place d'un personnage dans le journal : chapitre de recrutement, survivant ou légende. */
    private record Slot(CityNPC npc, CityTier tier, boolean legend) {}

    private final NPCDataManager          data;
    private final NPCPersonalQuestManager personal;
    private final ModIntegrations         integrations;
    private final Map<CityNPC, NPCPersonalQuestGUI> guis = new EnumMap<>(CityNPC.class);
    private final List<Slot> order;

    public StoryJournal(NPCDataManager data, NPCPersonalQuestManager personal,
                        ModIntegrations integrations, List<QuestGUI> questGUIs) {
        this.data         = data;
        this.personal     = personal;
        this.integrations = integrations;
        for (QuestGUI gui : questGUIs) {
            if (gui.getPersonalQuestGUI() != null) guis.put(gui.getNpcType(), gui.getPersonalQuestGUI());
        }
        this.order = buildOrder();
    }

    /** Survivants puis légendes, dans l'ordre des chapitres (quêtes de recrutement de la cité). */
    private static List<Slot> buildOrder() {
        List<Slot> survivors = new ArrayList<>(), legends = new ArrayList<>();
        Set<CityNPC> seen = EnumSet.noneOf(CityNPC.class);
        for (CityTier tier : CityTier.values()) {
            for (CityQuest q : tier.quests) {
                if (q.type() != CityQuestType.FIND_NPC || q.targetNpc() == null) continue;
                CityNPC npc = q.targetNpc();
                if (!npc.hasPersonalQuests() || !seen.add(npc)) continue;
                (q.optional() ? legends : survivors).add(new Slot(npc, tier, q.optional()));
            }
        }
        // Personnage à récits sans quête de recrutement (par sécurité) : après les survivants
        for (CityNPC npc : CityNPC.values()) {
            if (npc.hasPersonalQuests() && seen.add(npc)) survivors.add(new Slot(npc, null, false));
        }
        List<Slot> all = new ArrayList<>(survivors);
        all.addAll(legends);
        return List.copyOf(all);
    }

    /** Tous les personnages à récits et l'état de leurs récits pour ce joueur (récits lus en une requête). */
    public List<Entry> entries(UUID player) {
        Map<String, int[]> rows = personal.snapshot(player);
        List<Entry> list = new ArrayList<>(order.size());
        for (Slot slot : order) {
            CityNPC npc = slot.npc();
            Status status = status(npc);
            int level = data.getLevel(npc);
            List<Story> stories = new ArrayList<>();
            for (CityNPC.RankedQuest ranked : npc.getAllPersonalQuests()) {
                int[] row = rows.get(NPCPersonalQuestManager.cacheKey(npc, ranked.quest().id()));
                int state = row == null ? 0 : row[1];
                stories.add(new Story(ranked.quest(), ranked.level(),
                        status == Status.AVAILABLE && ranked.level() <= level,
                        state == 2, state == 1, row == null ? 0 : row[0]));
            }
            list.add(new Entry(npc, slot.tier(), slot.legend(), status, level, List.copyOf(stories)));
        }
        return list;
    }

    private Status status(CityNPC npc) {
        return switch (data.getState(npc)) {
            case ASSIGNED -> integrations == null || integrations.isAvailable(npc) ? Status.AVAILABLE : Status.MOD_MISSING;
            case ARRIVED  -> Status.IN_TOWN;
            default       -> Status.LOCKED;
        };
    }

    /** Ouvre l'écran des récits d'un personnage installé ; son bouton retour ramène au journal. */
    public boolean open(Player player, CityNPC npc, Runnable back) {
        NPCPersonalQuestGUI gui = guis.get(npc);
        if (gui == null) return false;
        gui.openFromJournal(player, back);
        return true;
    }

    /* =========================
       TEXTES (communs aux deux menus)
       ========================= */

    /** Nom affiché dans la bulle, précédé de l'état. */
    public static String title(Entry e) {
        String name = plain(e.npc().displayName);
        return switch (e.status()) {
            case LOCKED      -> "§8🔒 §7" + name;
            case IN_TOWN     -> "§7⌂ §f" + name;
            case MOD_MISSING -> "§c✘ §f" + name;
            case AVAILABLE   -> (e.legend() ? "§d★ " : "§a✦ ") + "§f" + name;
        };
    }

    /** Bulle d'un personnage : où il en est, puis la liste de ses récits s'il est installé. */
    public static List<String> lore(Entry e) {
        List<String> lore = new ArrayList<>();
        lore.add("§7" + e.npc().function + (e.status() == Status.AVAILABLE
                ? " §8· " + VillagerGUI.getLevelName(e.rank()) : ""));
        if (e.tier() != null) lore.add("§8" + e.tier().chapter);
        if (e.legend()) lore.add("§8Légende de passage (quête bonus)");
        lore.add("");
        ModIntegrations.Mod mod = ModIntegrations.requiredBy(e.npc());
        switch (e.status()) {
            case LOCKED -> {
                lore.add("§7Pas encore à Valmont.");
                lore.add("§7Ses récits s'ouvrent dès son installation.");
                lore.add("§8" + e.total() + " récits à découvrir");
                if (mod != null) lore.add("§8Personnage du mod " + mod.displayName);
            }
            case IN_TOWN -> {
                lore.add("§eEn ville, sans atelier.");
                lore.add("§7Ses récits s'ouvrent dès son");
                lore.add("§7installation dans un bâtiment.");
            }
            case MOD_MISSING -> {
                lore.add("§cMod absent : " + (mod == null ? "?" : mod.displayName));
                lore.add("§7Ses récits sont indisponibles");
                lore.add("§7sur ce serveur.");
            }
            case AVAILABLE -> {
                lore.add("§7Récits : §f" + e.done() + " §7accompli" + (e.done() > 1 ? "s" : "")
                        + " sur §f" + e.total());
                for (Story s : e.stories()) lore.add(storyLine(s));
                lore.add("");
                lore.add("§a▶ Cliquez pour ouvrir ses récits");
            }
        }
        return lore;
    }

    /** Une ligne par récit : ✔ accompli, ● en cours (avancement), ! à accepter, 🔒 rang requis. */
    private static String storyLine(Story s) {
        String name = plain(s.quest().displayName());
        if (s.done()) return "§a✔ §7" + name;
        if (s.active()) {
            int target = s.quest().targetAmount();
            return "§6● §f" + name + (target > 1 ? " §7(" + Math.min(s.progress(), target) + "/" + target + ")" : " §7(en cours)");
        }
        if (s.unlocked()) return "§e! §f" + name + " §7— à accepter";
        return "§8🔒 " + CityNPC.obfuscate(name) + " §8(" + plain(VillagerGUI.getLevelName(s.rank())) + ")";
    }

    private static String plain(String s) {
        return s == null ? "" : s.replaceAll("§.", "");
    }
}
