package com.citycore.integration;

import com.citycore.npc.CityNPC;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mods dont dépendent certains habitants (serveur hybride : mods NeoForge + plugins, ex. Youer).
 *
 * Un mod est jugé présent si l'une de ses traces se voit depuis le plugin : un de ses objets,
 * un de ses succès, une de ses structures, ou la liste des mods du chargeur (NeoForge, Forge ou
 * Fabric, lue par réflexion). Sur un serveur Paper sans mods, rien de tout cela n'existe : le mod
 * est absent.
 *
 * config.yml, section mod-integrations : « auto » (détection), « on » ou « off » pour forcer la
 * réponse, par exemple pour tester le comportement sans le mod sans le désinstaller.
 *
 * Deux usages :
 *  - mod requis (CityNPC.requiredMods, un ou plusieurs) : un habitant lié à un mod absent se
 *    recrute et s'installe normalement, mais ne commerce pas et ne confie pas de quêtes (voir
 *    VillagerGUI) ; avec plusieurs mods requis, il suffit qu'un seul manque ;
 *  - contenu facultatif (CityNPC.optionalMods, champs « avec-mod » / « sans-mod » de config.yml,
 *    récits réservés à un mod) : des articles, objectifs ou récits s'ajoutent quand le mod est là,
 *    sans rien désactiver quand il manque.
 */
public final class ModIntegrations {

    /** Mods connus du plugin. */
    public enum Mod {
        DRAGON_MOUNTS("dmr", "Dragon Mounts Remastered", "dmr:main", "dmr:dragon_egg", null),
        TAX_OCEAN("taxov", "Tax' Ocean Villager", "taxov:advancement_ocean", "taxov:pirate_sword", null),
        TOUGH_AS_NAILS("toughasnails", "Tough As Nails", null, "toughasnails:purified_water_bottle", null),
        TIDE("tide", "Tide", "tide:catch_legendary", "tide:brook_trout", null),
        WILDER_NATURE("wildernature", "Wilder Nature", null, "wildernature:venison", null),
        FARMERS_DELIGHT("farmersdelight", "Farmer's Delight", null, "farmersdelight:cooking_pot", null),
        EPIC_FIGHT("epicfight", "Epic Fight", null, "epicfight:uchigatana", null),
        HANDCRAFTED("handcrafted", "Handcrafted", null, "handcrafted:hammer", null),
        FURNITURE("furniture", "Furniture", null, "furniture:telescope", null),
        IRONS_SPELLBOOKS("irons_spellbooks", "Iron's Spells 'n Spellbooks",
                "irons_spellbooks:irons_spellbooks/root", "irons_spellbooks:scroll", null),
        SMALL_SHIPS("smallships", "Small Ships", null, "smallships:sail", null),
        VALARIAN_CONQUEST("valarian_conquest", "Valarian Conquest", null, "valarian_conquest:templar_sword", null),
        MOWZIES_MOBS("mowziesmobs", "Mowzie's Mobs", "mowziesmobs:kill_foliaath", "mowziesmobs:wrought_axe", null),
        // Let's Do Brewery dépend de Farm & Charm (silo, levure, cordes) : le mod suffit comme témoin
        BREWERY("brewery", "Let's Do Brewery", "brewery:main/root", "brewery:hops", "brewery:brewfest_tent"),
        // Mods de structures : ni objet ni succès, on cherche une de leurs structures
        MO_STRUCTURES("mostructures", "Mo' Structures", null, null, "mostructures:sphinx"),
        ATI_STRUCTURES("ati_structures", "ATi Structures", null, null, "ati_structures:ancient_temple");

        /** Identifiant du mod (espace de noms de ses objets et succès). */
        public final String id;
        public final String displayName;
        /** Succès du mod : sa présence sur le serveur trahit le mod (null = aucun témoin). */
        public final String advancement;
        /** Objet du mod servant de témoin (null = aucun). */
        public final String item;
        /** Structure du mod servant de témoin (null = aucune). */
        public final String structure;

        Mod(String id, String displayName, String advancement, String item, String structure) {
            this.id          = id;
            this.displayName = displayName;
            this.advancement = advancement;
            this.item        = item;
            this.structure   = structure;
        }

        public static Mod byId(String id) {
            if (id == null) return null;
            String ns = NAMESPACE_ALIASES.getOrDefault(id.toLowerCase(Locale.ROOT), id);
            for (Mod mod : values()) if (mod.id.equalsIgnoreCase(ns)) return mod;
            return null;
        }
    }

    /**
     * Espaces de noms secondaires d'un mod : les soldats de Valarian Conquest sont déclarés sous
     * « ethans_custom_mod », dans le même jar.
     */
    private static final Map<String, String> NAMESPACE_ALIASES = Map.of("ethans_custom_mod", "valarian_conquest");

    /** Détection en cours (une seule instance par démarrage), lue par le contenu facultatif. */
    private static volatile ModIntegrations active;
    /** Mods inconnus du plugin cités dans config.yml (« avec-mod: autre_mod ») : liste du chargeur. */
    private static final Map<String, Boolean> OTHER_MODS = new ConcurrentHashMap<>();

    private final JavaPlugin plugin;
    private final Map<Mod, Boolean> present = new EnumMap<>(Mod.class);
    /** Comment chaque mod a été jugé (pour /city mods). */
    private final Map<Mod, String> how = new EnumMap<>(Mod.class);

    public ModIntegrations(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** Détecte les mods (au démarrage, avant de charger les habitants). */
    public void detect() {
        for (Mod mod : Mod.values()) {
            String mode = plugin.getConfig().getString("mod-integrations." + mod.id, "auto")
                    .trim().toLowerCase(Locale.ROOT);
            boolean found;
            String reason;
            switch (mode) {
                case "on", "true", "yes" -> { found = true;  reason = "forcé par config.yml"; }
                case "off", "false", "no" -> { found = false; reason = "forcé par config.yml"; }
                default -> {
                    reason = detectedBy(mod);
                    found  = reason != null;
                    if (reason == null) reason = "aucune trace";
                }
            }
            present.put(mod, found);
            how.put(mod, reason);
            String users = String.join(", ", charactersOf(mod));
            if (found) {
                plugin.getLogger().info("🧩 Mod " + mod.displayName + " (" + mod.id + ") présent — " + reason
                        + (users.isEmpty() ? "" : " : " + users + "."));
            } else {
                plugin.getLogger().info("🧩 Mod " + mod.displayName + " (" + mod.id + ") absent"
                        + (mode.equals("auto") ? "" : " — " + reason)
                        + (users.isEmpty() ? "" : " : " + users + " sans ce contenu."));
            }
        }
        active = this;
    }

    /** Comment le mod a été repéré, ou null s'il est absent. */
    private String detectedBy(Mod mod) {
        if (mod.item != null && ItemIds.resolve(mod.item) != null) return "objet " + mod.item;
        if (mod.advancement != null) {
            NamespacedKey adv = NamespacedKey.fromString(mod.advancement);
            if (adv != null && Bukkit.getAdvancement(adv) != null) return "succès " + mod.advancement;
        }
        if (mod.structure != null && structureExists(mod.structure)) return "structure " + mod.structure;
        if (modLoaderReports(mod.id)) return "liste des mods du serveur";
        return null;
    }

    /** Structure enregistrée sur le serveur (« mostructures:sphinx ») ? */
    public static boolean structureExists(String id) {
        NamespacedKey key = NamespacedKey.fromString(id.toLowerCase(Locale.ROOT));
        if (key == null) return false;
        try {
            return Registry.STRUCTURE.get(key) != null;
        } catch (RuntimeException | LinkageError e) {
            return false; // registre indisponible sur ce serveur
        }
    }

    /** Liste des mods du chargeur, lue par réflexion (classes absentes sur Paper) ; aussi pour /city carte. */
    public static boolean modLoaderReports(String modId) {
        String[][] apis = {
                {"net.neoforged.fml.ModList", "get", "isLoaded"},
                {"net.minecraftforge.fml.ModList", "get", "isLoaded"},
                {"net.fabricmc.loader.api.FabricLoader", "getInstance", "isModLoaded"}};
        for (String[] api : apis) {
            try {
                Class<?> c = Class.forName(api[0]);
                Object instance = c.getMethod(api[1]).invoke(null);
                if (instance != null && Boolean.TRUE.equals(
                        c.getMethod(api[2], String.class).invoke(instance, modId))) return true;
            } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
                // chargeur absent ou classes non visibles depuis un plugin
            }
        }
        return false;
    }

    public boolean isPresent(Mod mod) {
        return mod != null && present.getOrDefault(mod, false);
    }

    /** Raison de la décision (« objet tide:brook_trout », « forcé par config.yml »…). */
    public String reason(Mod mod) {
        return how.getOrDefault(mod, "?");
    }

    /* =========================
       PRÉSENCE (ACCÈS STATIQUE)
       Lue par la configuration (articles « avec-mod »), les récits réservés à un mod, etc.
       ========================= */

    /**
     * Le mod est-il présent ? Mod connu : résultat de la détection (avec les réglages de
     * mod-integrations) ; autre mod : liste des mods du chargeur. Avant la détection : absent.
     */
    public static boolean present(String modId) {
        if (modId == null || modId.isBlank()) return true;
        Mod mod = Mod.byId(modId.trim());
        ModIntegrations current = active;
        if (mod != null) return current != null && current.isPresent(mod);
        return OTHER_MODS.computeIfAbsent(modId.trim().toLowerCase(Locale.ROOT), ModIntegrations::modLoaderReports);
    }

    /**
     * Condition d'un contenu facultatif : {@code with} = mods qui doivent tous être présents,
     * {@code without} = mods qui doivent tous être absents (listes séparées par des virgules ;
     * null ou vide = pas de condition).
     */
    public static boolean allows(String with, String without) {
        if (with != null) {
            for (String id : with.split(",")) if (!id.isBlank() && !present(id)) return false;
        }
        if (without != null) {
            for (String id : without.split(",")) if (!id.isBlank() && present(id)) return false;
        }
        return true;
    }

    /**
     * Condition d'un récit : null = toujours ; « tide » = mod présent ; « !irons_spellbooks » =
     * mod absent (contenu de repli).
     */
    public static boolean gate(String gate) {
        if (gate == null || gate.isBlank()) return true;
        return gate.startsWith("!") ? !present(gate.substring(1)) : present(gate);
    }

    /* =========================
       HABITANTS LIÉS À UN MOD
       ========================= */

    /** Mods requis par un habitant (vide = aucun). */
    public static List<Mod> requiredMods(CityNPC npc) {
        List<Mod> list = new ArrayList<>();
        if (npc == null) return list;
        for (String id : npc.requiredMods) {
            Mod mod = Mod.byId(id);
            if (mod != null) list.add(mod);
        }
        return list;
    }

    /** Mods qui enrichissent un habitant sans lui être nécessaires (vide = aucun). */
    public static List<Mod> optionalMods(CityNPC npc) {
        List<Mod> list = new ArrayList<>();
        if (npc == null) return list;
        for (String id : npc.optionalMods) {
            Mod mod = Mod.byId(id);
            if (mod != null) list.add(mod);
        }
        return list;
    }

    /** Premier mod requis par un habitant (null = aucun). */
    public static Mod requiredBy(CityNPC npc) {
        List<Mod> mods = requiredMods(npc);
        return mods.isEmpty() ? null : mods.get(0);
    }

    /** L'habitant peut-il commercer et confier des quêtes sur ce serveur (tous ses mods requis présents) ? */
    public boolean isAvailable(CityNPC npc) {
        for (Mod mod : requiredMods(npc)) if (!isPresent(mod)) return false;
        return true;
    }

    /** « Tide », « Wilder Nature et Farmer's Delight » : noms des mods, liés par « et ». */
    public static String names(List<Mod> mods) {
        List<String> names = mods.stream().map(m -> m.displayName).toList();
        if (names.isEmpty()) return "";
        if (names.size() == 1) return names.get(0);
        return String.join(", ", names.subList(0, names.size() - 1)) + " et " + names.get(names.size() - 1);
    }

    /**
     * Ligne grise affichée après le dialogue « mod_missing » :
     * « (Le mod Tide n'est pas installé sur ce serveur : Capitaine Achab ne peut ni commercer ni
     * confier de quêtes.) » ; avec deux mods requis, « Les mods A et B ne sont pas tous les deux
     * installés… ».
     */
    public static String unavailableMessage(CityNPC npc, String plainName) {
        List<Mod> mods = requiredMods(npc);
        String subject = switch (mods.size()) {
            case 0 -> "Un mod requis n'est pas installé";
            case 1 -> "Le mod " + mods.get(0).displayName + " n'est pas installé";
            case 2 -> "Les mods " + names(mods) + " ne sont pas tous les deux installés";
            default -> "Les mods " + names(mods) + " ne sont pas tous installés";
        };
        return "§8(" + subject + " sur ce serveur : " + plainName + " ne peut ni commercer ni confier de quêtes.)";
    }

    /** Personnages qui dépendent d'un mod ou en profitent (journal de démarrage, /city mods). */
    public static List<String> charactersOf(Mod mod) {
        List<String> out = new ArrayList<>();
        for (CityNPC npc : CityNPC.values()) {
            String name = org.bukkit.ChatColor.stripColor(npc.displayName);
            if (requiredMods(npc).contains(mod)) out.add(name);
            else if (optionalMods(npc).contains(mod)) out.add(name + " (contenu facultatif)");
        }
        return out;
    }

    /**
     * Bilan pour /city mods : chaque mod connu, présent ou absent (et comment on le sait), puis les
     * personnages qui en dépendent ou en profitent, avec ce que cela change pour eux.
     */
    public List<String> report() {
        List<String> lines = new ArrayList<>();
        lines.add("§6🧩 Mods du serveur §7(config.yml, section mod-integrations : auto | on | off)");
        for (Mod mod : Mod.values()) {
            boolean on = isPresent(mod);
            String mode = plugin.getConfig().getString("mod-integrations." + mod.id, "auto");
            lines.add((on ? "§a✔ " : "§c✘ ") + "§f" + mod.displayName + " §8(" + mod.id + ", " + mode + ") §7— "
                    + (on ? "présent" : "absent") + " §8(" + reason(mod) + ")");
            for (CityNPC npc : CityNPC.values()) {
                String name = org.bukkit.ChatColor.stripColor(npc.displayName);
                if (requiredMods(npc).contains(mod)) {
                    lines.add("   §7• " + name + " : " + (isAvailable(npc) ? "§aboutique, quêtes et récits"
                            : "§crecrutable, mais ni boutique ni quêtes")
                            + (requiredMods(npc).size() > 1 ? " §8(requiert " + names(requiredMods(npc)) + ")" : ""));
                } else if (optionalMods(npc).contains(mod)) {
                    lines.add("   §7• " + name + " : " + (on ? "§acontenu en plus" : "§7contenu de base seulement"));
                }
            }
        }
        return lines;
    }

    /**
     * Espace de noms dont les succès doivent exister : minecraft, ou un mod présent. Les succès
     * d'un mod absent ne sont pas signalés comme introuvables (ses habitants sont inactifs).
     */
    public boolean namespaceActive(String namespace) {
        if (namespace == null || namespace.equals(NamespacedKey.MINECRAFT)) return true;
        Mod mod = Mod.byId(namespace);
        return mod == null || isPresent(mod);
    }
}
