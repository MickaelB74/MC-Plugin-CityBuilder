package com.citycore.integration;

import com.citycore.npc.CityNPC;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/**
 * Mods dont dépendent certains habitants (serveur hybride : mods NeoForge + plugins, ex. Youer).
 *
 * Un mod est jugé présent si l'une de ses traces se voit depuis le plugin : un de ses objets,
 * un de ses succès, ou la liste des mods du chargeur (NeoForge, Forge ou Fabric, lue par
 * réflexion). Sur un serveur Paper sans mods, rien de tout cela n'existe : le mod est absent.
 *
 * config.yml, section mod-integrations : « auto » (détection), « on » ou « off » pour forcer la
 * réponse, par exemple pour tester le comportement sans le mod sans le désinstaller.
 *
 * Un habitant lié à un mod absent se recrute et s'installe normalement, mais ne commerce pas
 * et ne confie pas de quêtes (voir VillagerGUI).
 */
public final class ModIntegrations {

    /** Mods connus du plugin. */
    public enum Mod {
        DRAGON_MOUNTS("dmr", "Dragon Mounts Remastered", "dmr:main", "dmr:dragon_egg"),
        TAX_OCEAN("taxov", "Tax' Ocean Villager", "taxov:advancement_ocean", "taxov:pirate_sword");

        /** Identifiant du mod (espace de noms de ses objets et succès). */
        public final String id;
        public final String displayName;
        /** Succès racine du mod : sa présence sur le serveur trahit le mod. */
        public final String advancement;
        /** Objet du mod servant de témoin. */
        public final String item;

        Mod(String id, String displayName, String advancement, String item) {
            this.id          = id;
            this.displayName = displayName;
            this.advancement = advancement;
            this.item        = item;
        }

        public static Mod byId(String id) {
            for (Mod mod : values()) if (mod.id.equalsIgnoreCase(id)) return mod;
            return null;
        }
    }

    private final JavaPlugin plugin;
    private final Map<Mod, Boolean> present = new EnumMap<>(Mod.class);

    public ModIntegrations(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** Détecte les mods (au démarrage, avant de charger les habitants). */
    public void detect() {
        for (Mod mod : Mod.values()) {
            String mode = plugin.getConfig().getString("mod-integrations." + mod.id, "auto")
                    .trim().toLowerCase(Locale.ROOT);
            boolean found;
            String how;
            switch (mode) {
                case "on", "true", "yes" -> { found = true;  how = "forcé par config.yml"; }
                case "off", "false", "no" -> { found = false; how = "forcé par config.yml"; }
                default -> {
                    how   = detectedBy(mod);
                    found = how != null;
                }
            }
            present.put(mod, found);
            if (found) {
                plugin.getLogger().info("🧩 Mod " + mod.displayName + " (" + mod.id + ") présent"
                        + (how == null ? "" : " — " + how) + " : ses habitants sont disponibles.");
            } else {
                plugin.getLogger().info("🧩 Mod " + mod.displayName + " (" + mod.id + ") absent"
                        + (mode.equals("auto") ? "" : " — " + how)
                        + " : ses habitants se recrutent mais ne commercent pas.");
            }
        }
    }

    /** Comment le mod a été repéré, ou null s'il est absent. */
    private String detectedBy(Mod mod) {
        if (ItemIds.resolve(mod.item) != null) return "objet " + mod.item;
        NamespacedKey adv = NamespacedKey.fromString(mod.advancement);
        if (adv != null && Bukkit.getAdvancement(adv) != null) return "succès " + mod.advancement;
        if (modLoaderReports(mod.id)) return "liste des mods du serveur";
        return null;
    }

    /** Liste des mods du chargeur, lue par réflexion (classes absentes sur Paper). */
    private static boolean modLoaderReports(String modId) {
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

    /** Mod requis par un habitant (null = aucun). */
    public static Mod requiredBy(CityNPC npc) {
        return npc == null || npc.requiredMod == null ? null : Mod.byId(npc.requiredMod);
    }

    /** L'habitant peut-il commercer et confier des quêtes sur ce serveur ? */
    public boolean isAvailable(CityNPC npc) {
        Mod mod = requiredBy(npc);
        return mod == null || isPresent(mod);
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
