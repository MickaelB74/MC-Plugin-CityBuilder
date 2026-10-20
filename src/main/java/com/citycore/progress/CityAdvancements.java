package com.citycore.progress;

import com.citycore.npc.CityNPC;
import com.citycore.npc.NPCManager;
import com.citycore.quest.city.CityQuest;
import com.citycore.quest.city.CityTier;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.papermc.paper.event.server.ServerResourcesReloadedEvent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Onglet de progrès Minecraft « La Renaissance de Valmont » (touche L) : la vue d'ensemble
 * de la cité. Il n'existe pas d'autre menu de progression.
 *
 * Arbre en quatre lignes parallèles, chacune dans l'ordre de la trame :
 *  - les chapitres I à X (objectifs listés au survol, avancement « 3/7 ») ;
 *  - les survivants de Valmont ; les légendes de passage (bonus) ; les hauts faits.
 * Chaque personnage et chaque haut fait se place sous la colonne de son chapitre (plusieurs
 * personnages du même chapitre l'un sous l'autre : trois légendes au chapitre X) : le dragon est
 * sous le chapitre IX. La hauteur de chaque ligne suit le chapitre le plus fourni. La bulle d'une
 * légende liée à un mod le nomme, et signale s'il manque au serveur.
 *
 * Pourquoi des lignes : Minecraft range les enfants d'un progrès dans un ensemble non ordonné,
 * dont l'ordre change à chaque démarrage. Une chaîne (un seul enfant par progrès) reste toujours
 * droite et ordonnée. Les positions sont en plus fixées par le plugin (voir {@link #applyLayout()}),
 * puis réappliquées à chaque connexion et après chaque rechargement des données : un autre
 * composant du serveur qui recalcule la disposition de Minecraft ne les efface pas durablement.
 *
 * Tout l'arbre est visible dès le départ, avec le statut de chaque étape :
 *  - cadre gris = à faire, cadre coloré = accompli ;
 *  - au survol, la barre de titre se remplit et affiche « 3/7 » (critères remplis) :
 *    chapitre = ouvert + chaque objectif + charte signée ;
 *    personnage = rencontré, arrivé en ville, installé ;
 *    haut fait = créatures abattues ou rang atteint.
 *
 * Minecraft cache normalement les progrès éloignés de ceux déjà obtenus. Pour tout montrer,
 * chaque feuille de l'arbre reçoit un enfant invisible (sans affichage) accordé à tous :
 * un descendant obtenu rend visibles tous ses ancêtres.
 *
 * La progression est collective : chaque joueur reçoit l'état de la ville (critères accordés
 * ou retirés pour coller à la réalité, par exemple après la suppression de la ville).
 */
public class CityAdvancements implements Listener {

    private static final long SYNC_PERIOD_TICKS = 100L; // 5 secondes
    /** Au-delà, un haut fait chiffré n'a qu'un critère (« 37/120 » ne tiendrait pas en critères). */
    private static final int  MAX_COUNTED_CRITERIA = 10;

    // Noms des critères (invisibles pour le joueur, seul leur nombre s'affiche)
    private static final String OPEN    = "ouvert";
    private static final String CHARTER = "charte";
    private static final String MET     = "rencontre";
    private static final String IN_TOWN = "en_ville";
    private static final String SETTLED = "installe";
    private static final String DONE    = "fait";
    private static final String SEEN    = "vu";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    // En-têtes des lignes de l'arbre
    private static final String ROW_CHAPTERS  = "row_chapters";
    private static final String ROW_SURVIVORS = "row_survivors";
    private static final String ROW_LEGENDS   = "row_legends";
    private static final String ROW_FEATS     = "row_feats";
    private static final String[] ROWS = {ROW_CHAPTERS, ROW_SURVIVORS, ROW_LEGENDS, ROW_FEATS};

    /** Nœud de l'arbre, dans l'ordre de chargement (parents d'abord). */
    private record Node(NamespacedKey key, NamespacedKey parent, String json,
                        List<String> criteria, boolean anchor) {}

    private final JavaPlugin         plugin;
    private final ProgressionService progress;
    private final NPCManager         npcManager;
    private final boolean            enabled;

    private List<Node> nodes = List.of();
    /** Positions fixées impossibles sur ce serveur (accès refusé) : disposition de Minecraft. */
    private boolean layoutBroken;
    /** Synchronisations successives sans ville (on attend 2 lectures avant de tout retirer). */
    private int     noCityStreak;
    /** Sélection d'onglet indisponible sur ce serveur : on n'essaie plus. */
    private boolean tabSelectionBroken;

    public CityAdvancements(JavaPlugin plugin, ProgressionService progress, NPCManager npcManager) {
        this.plugin     = plugin;
        this.progress   = progress;
        this.npcManager = npcManager;
        this.enabled    = plugin.getConfig().getBoolean("progression.advancements", true);
    }

    /* =========================
       INSTALLATION
       ========================= */

    /**
     * Enregistre l'arbre. À appeler au démarrage : sans joueur connecté, chaque chargement
     * est léger (le serveur recharge les progrès de tous les joueurs en ligne à chaque appel).
     */
    public void install() {
        if (!enabled) return;
        nodes = buildNodes();
        try {
            // Repart de zéro : retire nos progrès déjà présents (y compris ceux d'une version
            // précédente du plugin, dont l'arbre a pu changer)
            List<NamespacedKey> existing = new ArrayList<>();
            Bukkit.advancementIterator().forEachRemaining(a -> {
                if (a.getKey().getNamespace().equals(namespace())) existing.add(a.getKey());
            });
            for (int i = existing.size() - 1; i >= 0; i--) {
                Bukkit.getUnsafe().removeAdvancement(existing.get(i));
            }
            int loaded = loadMissing();
            applyLayout();
            plugin.getLogger().info("✅ Progrès « La Renaissance de Valmont » installés (" + loaded + "/"
                    + nodes.size() + ", dont " + anchorCount() + " invisibles pour tout afficher).");
        } catch (Exception e) {
            plugin.getLogger().warning("⚠ Impossible d'installer l'onglet de progrès : " + e.getMessage());
        }
    }

    /** Charge les nœuds absents. @return nombre de nœuds chargés */
    private int loadMissing() {
        int loaded = 0;
        for (Node n : nodes) {
            if (Bukkit.getAdvancement(n.key()) != null) continue;
            if (Bukkit.getUnsafe().loadAdvancement(n.key(), n.json()) != null) loaded++;
        }
        return loaded;
    }

    /**
     * /minecraft:reload reconstruit les progrès à partir des datapacks : ceux enregistrés par
     * l'API disparaissent. On recharge les manquants puis on resynchronise les joueurs.
     */
    @EventHandler
    public void onResourcesReloaded(ServerResourcesReloadedEvent event) {
        if (!enabled || nodes.isEmpty()) return;
        try {
            int loaded = loadMissing();
            applyLayout(); // le rechargement recalcule les positions, même si rien ne manquait
            if (loaded == 0) return;
            plugin.getLogger().info("✅ Progrès de la cité rechargés après un rechargement des données ("
                    + loaded + ").");
            Map<NamespacedKey, Set<String>> earned = earnedCriteria();
            Bukkit.getOnlinePlayers().forEach(p -> apply(p, earned));
        } catch (Exception e) {
            plugin.getLogger().warning("⚠ Rechargement de l'onglet de progrès impossible : " + e.getMessage());
        }
    }

    /* =========================
       SYNCHRONISATION
       ========================= */

    public void startSyncTask() {
        if (!enabled) return;
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (Bukkit.getOnlinePlayers().isEmpty()) return;
            // Pas de ville : on attend une seconde lecture avant de tout retirer (évite
            // qu'une erreur de lecture passagère efface puis réaccorde tout l'arbre)
            if (!progress.cityExists() && ++noCityStreak < 2) return;
            if (progress.cityExists()) noCityStreak = 0;
            Map<NamespacedKey, Set<String>> earned = earnedCriteria(); // une fois pour tous
            for (Player p : Bukkit.getOnlinePlayers()) apply(p, earned);
        }, SYNC_PERIOD_TICKS, SYNC_PERIOD_TICKS);
    }

    /**
     * Rattrapage immédiat à la connexion : appliqué avant le premier envoi des progrès au
     * client, l'état de la ville s'affiche sans rafale de notifications. Les étapes suivantes
     * (synchronisation périodique) déclenchent, elles, la notification « Progrès réalisé ».
     */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!enabled) return;
        Player player = event.getPlayer();
        // Un autre composant peut recalculer la disposition de Minecraft pendant la connexion
        // (chargements de progrès observés sur Youer) : on la refixe avant le premier envoi
        applyLayout();
        apply(player, earnedCriteria());
        // La touche L ouvrira directement l'onglet de la cité (l'arbre est envoyé au 1er tick)
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) selectTab(player);
        }, 40L);
    }

    /** Aligne les critères du joueur sur l'état de la ville. */
    private void apply(Player player, Map<NamespacedKey, Set<String>> earned) {
        for (Node n : nodes) {
            Advancement adv = Bukkit.getAdvancement(n.key());
            if (adv == null) continue;
            AdvancementProgress pr = player.getAdvancementProgress(adv);
            Set<String> wanted = n.anchor() ? Set.of(SEEN) : earned.getOrDefault(n.key(), Set.of());
            Collection<String> awarded = pr.getAwardedCriteria();
            for (String c : n.criteria()) {
                boolean has = awarded.contains(c);
                if (wanted.contains(c)) {
                    if (!has) pr.awardCriteria(c);
                } else if (has) {
                    pr.revokeCriteria(c);
                }
            }
        }
    }

    /** Critères mérités par la ville dans son état actuel (vide s'il n'y a pas de ville). */
    private Map<NamespacedKey, Set<String>> earnedCriteria() {
        Map<NamespacedKey, Set<String>> earned = new HashMap<>();
        if (!progress.cityExists()) return earned;
        ProgressionService.CityState state = progress.state(); // une seule lecture de la base
        earned.put(key("root"), Set.of(DONE));
        for (String row : ROWS) earned.put(key(row), Set.of(DONE));

        for (CityTier tier : CityTier.values()) {
            Set<String> chapter = new HashSet<>();
            if (state.level() >= tier.level) chapter.add(OPEN);
            for (CityQuest q : progress.requiredQuests(tier)) {
                if (progress.isQuestDone(state, tier, q)) chapter.add(questCriterion(q));
            }
            if (state.level() > tier.level) chapter.add(CHARTER);
            earned.put(chapterKey(tier), chapter);

            for (CityQuest q : tier.quests) {
                if (ProgressionService.isRecruit(q)) {
                    int stage = progress.recruitStage(q.targetNpc());
                    Set<String> got = new HashSet<>();
                    if (stage >= 1) got.add(MET);
                    if (stage >= 2) got.add(IN_TOWN);
                    if (stage >= 3) got.add(SETTLED);
                    earned.put(npcKey(q.targetNpc()), got);
                } else if (ProgressionService.isFeat(q)) {
                    earned.put(featKey(q), countedEarned(q, progress.questProgress(state, tier, q)));
                }
            }
        }
        return earned;
    }

    /** Critères accordés à un haut fait selon son avancement (voir {@link #countedCriteria}). */
    private Set<String> countedEarned(CityQuest quest, int value) {
        List<String> criteria = countedCriteria(quest);
        Set<String> got = new HashSet<>();
        if (criteria.size() == 1) {
            if (value >= progress.questManager().target(quest)) got.add(DONE);
        } else {
            for (int i = 0; i < Math.min(value, criteria.size()); i++) got.add(criteria.get(i));
        }
        return got;
    }

    /* =========================
       ONGLET SÉLECTIONNÉ
       ========================= */

    /**
     * Sélectionne l'onglet de la cité : la prochaine ouverture de l'écran des progrès (L) se
     * fait dessus. Aucune API Bukkit ne le permet : appel direct au serveur (noms Mojang, utilisés
     * à l'exécution par Paper depuis la 1.20.5). En cas d'échec, l'onglet reste à choisir à la main.
     *
     * @return true si l'onglet a été sélectionné
     */
    public boolean selectTab(Player player) {
        if (!enabled || tabSelectionBroken) return false;
        Advancement root = Bukkit.getAdvancement(key("root"));
        if (root == null) return false;
        try {
            Object holder       = root.getClass().getMethod("getHandle").invoke(root);
            Object serverPlayer = player.getClass().getMethod("getHandle").invoke(player);
            Object advancements = serverPlayer.getClass().getMethod("getAdvancements").invoke(serverPlayer);
            for (Method m : advancements.getClass().getMethods()) {
                if (m.getName().equals("setSelectedTab") && m.getParameterCount() == 1
                        && m.getParameterTypes()[0].isInstance(holder)) {
                    m.invoke(advancements, holder);
                    return true;
                }
            }
            tabSelectionBroken = true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            tabSelectionBroken = true;
        }
        plugin.getLogger().info("Sélection automatique de l'onglet de progrès indisponible sur ce serveur.");
        return false;
    }

    /* =========================
       DÉFINITION DE L'ARBRE
       ========================= */

    private List<Node> buildNodes() {
        List<Node> list = new ArrayList<>();
        NamespacedKey root = key("root");
        list.add(node(root, null, display(Material.WRITABLE_BOOK, npcManager.resolveSkin(CityNPC.MAYOR),
                "La Renaissance de Valmont", "gold",
                "Faire renaître Valmont de ses cendres, avec la Charte d'Alderic.\n"
                        + "Chaque étape montre son avancement au survol.",
                "task", true), List.of(DONE)));

        // En-têtes des quatre lignes (enfants de la racine)
        list.add(node(key(ROW_CHAPTERS), root, display(Material.FILLED_MAP, null, "Les chapitres", "yellow",
                "La trame de Valmont, du campement à la capitale.\nAu survol d'un chapitre : ses objectifs et son avancement.",
                "task", false), List.of(DONE)));
        list.add(node(key(ROW_SURVIVORS), root, display(Material.TOTEM_OF_UNDYING, null, "Les survivants de Valmont", "green",
                "Les artisans dispersés par la Nuit Noire, chacun sous le chapitre\noù on le retrouve. Rencontré, arrivé en ville, installé.",
                "task", false), List.of(DONE)));
        list.add(node(key(ROW_LEGENDS), root, display(Material.NETHER_STAR, null, "Les légendes de passage", "light_purple",
                "Figures légendaires attirées par la renommée de Valmont (quêtes bonus),\nchacune sous le chapitre où elle se présente.",
                "task", false), List.of(DONE)));
        list.add(node(key(ROW_FEATS), root, display(Material.DRAGON_HEAD, null, "Les hauts faits", "dark_purple",
                "Le dragon qui a brûlé Valmont (chapitre IX), puis les anciens fléaux\ndu royaume (chapitre X) : chacun sous la colonne de son chapitre.",
                "task", false), List.of(DONE)));

        // Ligne des chapitres : chaîne I → X
        NamespacedKey parent = key(ROW_CHAPTERS);
        for (CityTier tier : CityTier.values()) {
            List<String> criteria = new ArrayList<>();
            criteria.add(OPEN);
            StringBuilder desc = new StringBuilder("La ville devient ")
                    .append(ChatColor.stripColor(tier.tierName)).append('.');
            List<CityQuest> required = progress.requiredQuests(tier);
            for (CityQuest q : required) {
                criteria.add(questCriterion(q));
                desc.append("\n• ").append(plain(q.displayName()));
            }
            if (!tier.isMaxTier()) {
                criteria.add(CHARTER);
                desc.append("\n• Signer la charte auprès d'Alderic");
            }
            if (required.isEmpty()) desc.append("\nLes hauts faits du royaume vous attendent.");
            list.add(node(chapterKey(tier), parent, display(tier.icon, null, tier.chapter,
                    tier.isMaxTier() ? "gold" : "yellow", desc.toString(),
                    tier.isMaxTier() ? "challenge" : "goal", false), criteria));
            parent = chapterKey(tier);
        }

        // Lignes des personnages et des hauts faits : chaînes dans l'ordre des chapitres
        NamespacedKey lastSurvivor = key(ROW_SURVIVORS), lastLegend = key(ROW_LEGENDS), lastFeat = key(ROW_FEATS);
        for (CityTier tier : CityTier.values()) {
            String chapterLine = tier.chapter + "\n";
            for (CityQuest q : tier.quests) {
                if (ProgressionService.isRecruit(q)) {
                    CityNPC npc = q.targetNpc();
                    String name = ChatColor.stripColor(npc.displayName);
                    NamespacedKey k = npcKey(npc);
                    list.add(node(k, q.optional() ? lastLegend : lastSurvivor, display(ProgressionService.iconOf(npc),
                            npcManager.resolveSkin(npc), (q.optional() ? "★ " : "") + name,
                            q.optional() ? "light_purple" : "green",
                            chapterLine + npc.function + " — " + (q.optional() ? "visiteur légendaire" : "survivant de Valmont")
                                    + ".\nLe rencontrer, le ramener en ville, l'installer dans son bâtiment."
                                    + modLine(npc),
                            q.optional() ? "goal" : "task", false), List.of(MET, IN_TOWN, SETTLED)));
                    if (q.optional()) lastLegend = k; else lastSurvivor = k;
                } else if (ProgressionService.isFeat(q)) {
                    NamespacedKey k = featKey(q);
                    list.add(node(k, lastFeat, display(q.icon(), null,
                            plain(q.displayName()), "dark_purple",
                            chapterLine + plain(progress.questManager().description(q)).replace("[Haut fait] ", ""),
                            "challenge", false), countedCriteria(q)));
                    lastFeat = k;
                }
            }
        }

        // Un enfant invisible sous chaque feuille : rend tout l'arbre visible
        Set<NamespacedKey> parents = new HashSet<>();
        for (Node n : list) if (n.parent() != null) parents.add(n.parent());
        List<Node> anchors = new ArrayList<>();
        for (Node n : list) {
            if (parents.contains(n.key())) continue;
            NamespacedKey anchorKey = key(n.key().getKey() + "_" + SEEN);
            anchors.add(new Node(anchorKey, n.key(), json(n.key(), null, List.of(SEEN)),
                    List.of(SEEN), true));
        }
        list.addAll(anchors);
        return list;
    }

    /**
     * Mods liés à un personnage, pour la bulle de son progrès : « Lié au mod Tide », « Liée aux
     * mods Wilder Nature et Farmer's Delight », « Enrichi par Iron's Spells 'n Spellbooks », avec
     * la mention d'un mod requis absent du serveur (ni boutique ni quêtes).
     */
    private static String modLine(CityNPC npc) {
        List<com.citycore.integration.ModIntegrations.Mod> required = com.citycore.integration.ModIntegrations.requiredMods(npc);
        List<com.citycore.integration.ModIntegrations.Mod> optional = com.citycore.integration.ModIntegrations.optionalMods(npc);
        StringBuilder sb = new StringBuilder();
        if (!required.isEmpty()) {
            boolean missing = required.stream().anyMatch(m -> !com.citycore.integration.ModIntegrations.present(m.id));
            sb.append("\nLien avec ").append(required.size() > 1 ? "les mods " : "le mod ")
                    .append(com.citycore.integration.ModIntegrations.names(required))
                    .append(missing ? " — absent du serveur : ni boutique ni quêtes." : ".");
        }
        if (!optional.isEmpty()) {
            sb.append("\nContenu en plus avec ").append(com.citycore.integration.ModIntegrations.names(optional)).append('.');
        }
        return sb.toString();
    }

    /* =========================
       DISPOSITION FIXE
       ========================= */

    /**
     * Positions de chaque progrès : lignes dans l'ordre (chapitres, survivants, légendes, hauts
     * faits), une ligne vide entre elles, chaque personnage et chaque haut fait sous la colonne de
     * son chapitre (deux personnages du même chapitre l'un sous l'autre ; les hauts faits suivants
     * d'un même chapitre à sa droite).
     */
    private Map<NamespacedKey, float[]> layoutPositions() {
        Map<NamespacedKey, float[]> pos = new HashMap<>();
        // Hauteur de chaque ligne : autant de rangées que le chapitre le plus fourni (le chapitre X
        // compte trois légendes), puis une rangée vide avant la ligne suivante
        int maxSurvivors = 1, maxLegends = 1;
        for (CityTier tier : CityTier.values()) {
            int s = 0, l = 0;
            for (CityQuest q : tier.quests) {
                if (!ProgressionService.isRecruit(q)) continue;
                if (q.optional()) l++; else s++;
            }
            maxSurvivors = Math.max(maxSurvivors, s);
            maxLegends = Math.max(maxLegends, l);
        }
        final float yChapters = 0, ySurvivors = 2;
        final float yLegends = ySurvivors + maxSurvivors + 1, yFeats = yLegends + maxLegends + 1;
        pos.put(key("root"), new float[]{0, Math.round(yFeats / 2)});
        pos.put(key(ROW_CHAPTERS), new float[]{1, yChapters});
        pos.put(key(ROW_SURVIVORS), new float[]{1, ySurvivors});
        pos.put(key(ROW_LEGENDS), new float[]{1, yLegends});
        pos.put(key(ROW_FEATS), new float[]{1, yFeats});
        int featX = 0;
        for (CityTier tier : CityTier.values()) {
            float x = tier.level + 1;
            pos.put(chapterKey(tier), new float[]{x, yChapters});
            int survivors = 0, legends = 0;
            for (CityQuest q : tier.quests) {
                if (ProgressionService.isRecruit(q)) {
                    if (q.optional()) pos.put(npcKey(q.targetNpc()), new float[]{x, yLegends + legends++});
                    else pos.put(npcKey(q.targetNpc()), new float[]{x, ySurvivors + survivors++});
                } else if (ProgressionService.isFeat(q)) {
                    featX = Math.max(featX + 1, (int) x);
                    pos.put(featKey(q), new float[]{featX, yFeats});
                }
            }
        }
        return pos;
    }

    /**
     * Applique les positions au serveur (Minecraft n'offre pas d'API : accès direct aux données
     * du progrès, noms Mojang). Refusé sur certains serveurs (modules cloisonnés) : les lignes
     * restent alors droites, seul leur ordre vertical est laissé à Minecraft.
     */
    private void applyLayout() {
        if (layoutBroken) return;
        try {
            for (Map.Entry<NamespacedKey, float[]> e : layoutPositions().entrySet()) {
                Advancement adv = Bukkit.getAdvancement(e.getKey());
                if (adv == null) continue;
                Object holder  = adv.getClass().getMethod("getHandle").invoke(adv);
                Object value   = holder.getClass().getMethod("value").invoke(holder);
                Object display = ((java.util.Optional<?>) value.getClass().getMethod("display").invoke(value)).orElse(null);
                if (display == null) continue;
                display.getClass().getMethod("setLocation", float.class, float.class)
                        .invoke(display, e.getValue()[0], e.getValue()[1]);
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            layoutBroken = true;
            plugin.getLogger().info("Disposition fixe de l'onglet de progrès indisponible ("
                    + e.getClass().getSimpleName() + (e.getMessage() == null ? "" : " : " + e.getMessage())
                    + ") — lignes rangées par Minecraft.");
        }
    }

    /** Critères d'un haut fait : un par unité si l'objectif est petit, sinon un seul. */
    private List<String> countedCriteria(CityQuest quest) {
        int target = progress.questManager().target(quest);
        if (target <= 1 || target > MAX_COUNTED_CRITERIA) return List.of(DONE);
        List<String> criteria = new ArrayList<>();
        for (int i = 1; i <= target; i++) criteria.add("n" + i);
        return criteria;
    }

    private Node node(NamespacedKey key, NamespacedKey parent, JsonObject display, List<String> criteria) {
        return new Node(key, parent, json(parent, display, criteria), List.copyOf(criteria), false);
    }

    /** JSON d'un progrès : chaque critère est requis (la barre affiche « remplis/total »). */
    private static String json(NamespacedKey parent, JsonObject display, List<String> criteria) {
        JsonObject adv = new JsonObject();
        if (parent != null) adv.addProperty("parent", parent.toString());
        if (display != null) adv.add("display", display);
        JsonObject criteriaObj  = new JsonObject();
        JsonArray  requirements = new JsonArray();
        for (String c : criteria) {
            JsonObject trigger = new JsonObject();
            trigger.addProperty("trigger", "minecraft:impossible"); // accordé uniquement par le plugin
            criteriaObj.add(c, trigger);
            JsonArray group = new JsonArray();
            group.add(c);
            requirements.add(group);
        }
        adv.add("criteria", criteriaObj);
        adv.add("requirements", requirements);
        return GSON.toJson(adv);
    }

    private static JsonObject display(Material icon, String[] skin, String title, String color,
                                      String description, String frame, boolean root) {
        JsonObject iconObj = new JsonObject();
        if (skin != null) {
            // Tête du personnage (texture du skin)
            iconObj.addProperty("id", "minecraft:player_head");
            JsonObject textures = new JsonObject();
            textures.addProperty("name", "textures");
            textures.addProperty("value", skin[0]);
            JsonArray properties = new JsonArray();
            properties.add(textures);
            JsonObject profile = new JsonObject();
            profile.add("properties", properties);
            JsonObject components = new JsonObject();
            components.add("minecraft:profile", profile);
            iconObj.add("components", components);
        } else {
            iconObj.addProperty("id", icon.getKey().toString());
        }

        JsonObject display = new JsonObject();
        display.add("icon", iconObj);
        display.add("title", text(title, color));
        display.add("description", text(description, "gray"));
        display.addProperty("frame", frame);
        display.addProperty("show_toast", true);
        display.addProperty("announce_to_chat", false); // progression collective : pas de spam
        display.addProperty("hidden", false);
        if (root) display.addProperty("background", "minecraft:textures/block/stone_bricks.png");
        return display;
    }

    private static JsonObject text(String text, String color) {
        JsonObject o = new JsonObject();
        o.addProperty("text", text);
        o.addProperty("color", color);
        return o;
    }

    private static String plain(String text) {
        return ChatColor.stripColor(text).replace("★ ", "").trim();
    }

    private long anchorCount() {
        return nodes.stream().filter(Node::anchor).count();
    }

    /* =========================
       CLÉS
       ========================= */

    private String namespace() {
        return key("root").getNamespace();
    }

    /** Clés dans l'espace de noms du plugin (« citycore:… »). */
    private NamespacedKey key(String path) {
        return new NamespacedKey(plugin, path);
    }

    private NamespacedKey chapterKey(CityTier tier) {
        return key("chapter_" + tier.level);
    }

    private NamespacedKey npcKey(CityNPC npc) {
        return key("npc_" + npc.skinId);
    }

    private NamespacedKey featKey(CityQuest quest) {
        return key("feat_" + quest.id());
    }

    private static String questCriterion(CityQuest quest) {
        return "q_" + quest.id();
    }
}
