package com.citycore.progress;

import com.citycore.city.CityManager;
import com.citycore.npc.CityNPC;
import com.citycore.npc.IntroductionManager;
import com.citycore.npc.NPCDataManager;
import com.citycore.quest.city.CityQuest;
import com.citycore.quest.city.CityQuestManager;
import com.citycore.quest.city.CityQuestType;
import com.citycore.quest.city.CityTier;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * État de la progression de la ville, lu par l'onglet de progrès Minecraft (touche L).
 *
 * Tout est dérivé de l'état réel de la ville (niveau, quêtes, PNJ) : rien n'est stocké en double.
 * Les méthodes d'état reçoivent un {@link CityState} lu une seule fois par calcul (une requête).
 */
public class ProgressionService {

    /** Créatures légendaires : les abattre est un haut fait, même dans une quête obligatoire. */
    private static final Set<EntityType> LEGENDARY = EnumSet.of(
            EntityType.ENDER_DRAGON, EntityType.WITHER, EntityType.WARDEN, EntityType.ELDER_GUARDIAN);

    /** Icône métier des personnages (quand leur skin n'est pas disponible). */
    private static final Map<CityNPC, Material> ICONS = new EnumMap<>(CityNPC.class);
    static {
        ICONS.put(CityNPC.MAYOR, Material.WRITABLE_BOOK);
        ICONS.put(CityNPC.WOODCUTTER, Material.IRON_AXE);
        ICONS.put(CityNPC.FARMER, Material.HAY_BLOCK);
        ICONS.put(CityNPC.STONEMASON, Material.STONECUTTER);
        ICONS.put(CityNPC.FISHERMAN, Material.FISHING_ROD);
        ICONS.put(CityNPC.MINER, Material.DIAMOND_PICKAXE);
        ICONS.put(CityNPC.INNKEEPER, Material.CAKE);
        ICONS.put(CityNPC.BLACKSMITH, Material.SMITHING_TABLE);
        ICONS.put(CityNPC.WEAVER, Material.LOOM);
        ICONS.put(CityNPC.STABLEMASTER, Material.SADDLE);
        ICONS.put(CityNPC.GUARD, Material.SHIELD);
        ICONS.put(CityNPC.MONK, Material.BREWING_STAND);
        ICONS.put(CityNPC.SCRIBE, Material.LECTERN);
        ICONS.put(CityNPC.GLASSMAKER, Material.RED_STAINED_GLASS);
        ICONS.put(CityNPC.GOLDSMITH, Material.GOLD_INGOT);
        ICONS.put(CityNPC.ROBIN, Material.BOW);
        ICONS.put(CityNPC.JACKSPARROW, Material.TRIDENT);
        ICONS.put(CityNPC.INDIANA, Material.BRUSH);
        ICONS.put(CityNPC.DOCBROWN, Material.CLOCK);
        ICONS.put(CityNPC.GANDALF, Material.ENCHANTING_TABLE);
        ICONS.put(CityNPC.GERALT, Material.IRON_SWORD);
        ICONS.put(CityNPC.HAROLD, Material.DRAGON_EGG);
    }

    private final CityQuestManager    questManager;
    private final CityManager         cityManager;
    private final NPCDataManager      npcDataManager;
    private final IntroductionManager introManager;

    public ProgressionService(CityQuestManager questManager, CityManager cityManager,
                              NPCDataManager npcDataManager, IntroductionManager introManager) {
        this.questManager   = questManager;
        this.cityManager    = cityManager;
        this.npcDataManager = npcDataManager;
        this.introManager   = introManager;
    }

    public CityQuestManager questManager() { return questManager; }

    public boolean cityExists() {
        return cityManager.isCityInitialized();
    }

    public int cityLevel() {
        return questManager.getCityLevel();
    }

    public static Material iconOf(CityNPC npc) {
        return ICONS.getOrDefault(npc, Material.PLAYER_HEAD);
    }

    /* =========================
       STRUCTURE (indépendante de l'état)
       ========================= */

    /** Quêtes obligatoires d'un chapitre (hors bonus). */
    public List<CityQuest> requiredQuests(CityTier tier) {
        return tier.quests.stream().filter(q -> !q.optional()).toList();
    }

    /** Quête de recrutement d'un personnage. */
    public static boolean isRecruit(CityQuest quest) {
        return quest.type() == CityQuestType.FIND_NPC;
    }

    /** Haut fait : quête bonus (hors recrutement) ou chasse d'une créature légendaire. */
    public static boolean isFeat(CityQuest quest) {
        if (isRecruit(quest)) return false;
        return quest.optional() || (quest.type() == CityQuestType.KILL_MOBS
                && quest.entity() != null && LEGENDARY.contains(quest.entity()));
    }

    /* =========================
       ÉTAT
       ========================= */

    /**
     * Niveau de la ville et état de toutes ses quêtes (avancement, terminée), lus en une
     * requête : l'onglet de progrès en consulte plusieurs dizaines à chaque synchronisation.
     */
    public record CityState(int level, Map<String, int[]> quests) {
        boolean completed(String questId) {
            int[] s = quests.get(questId);
            return s != null && s[1] == 1;
        }

        int current(String questId) {
            int[] s = quests.get(questId);
            return s == null ? 0 : s[0];
        }
    }

    public CityState state() {
        return new CityState(cityLevel(), questManager.progressSnapshot());
    }

    public boolean isQuestDone(CityState state, CityTier tier, CityQuest quest) {
        // Un chapitre dépassé a forcément rempli ses quêtes obligatoires
        if (!quest.optional() && state.level() > tier.level) return true;
        return state.completed(quest.id());
    }

    /**
     * Étape de recrutement d'un personnage :
     * 0 inconnu, 1 rencontré (un joueur lui a parlé), 2 arrivé en ville, 3 installé.
     */
    public int recruitStage(CityNPC npc) {
        return switch (npcDataManager.getState(npc)) {
            case ASSIGNED, HOMELESS -> 3; // sans logis : l'installation reste acquise
            case ARRIVED  -> 2;
            default       -> introManager.hasAnyoneMet(npc) ? 1 : 0;
        };
    }

    /** Avancement d'une quête (objectif ou haut fait), de 0 à son objectif. */
    public int questProgress(CityState state, CityTier tier, CityQuest quest) {
        int target = questManager.target(quest);
        if (isQuestDone(state, tier, quest)) return target;
        if (state.level() < tier.level) return 0; // chapitre pas encore ouvert
        return Math.max(0, Math.min(target, state.current(quest.id())));
    }
}
