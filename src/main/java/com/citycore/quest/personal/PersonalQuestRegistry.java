package com.citycore.quest.personal;

import org.bukkit.block.Biome;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.citycore.quest.personal.PersonalQuestDefinition.TriggerType.*;

/**
 * Registre central de toutes les quêtes personnalisées disponibles.
 *
 * Pour ajouter une quête :
 *   1. Choisir un id unique (snake_case)
 *   2. Écrire le validator comme lambda
 *   3. Appeler register() dans le bloc statique
 *
 * Le validator reçoit :
 *   - player  : le joueur
 *   - context : objet contextuel selon le TriggerType
 *       MOVE    → null (la position est player.getLocation())
 *       KILL    → org.bukkit.entity.Entity (l'entité tuée)
 *       COLLECT → org.bukkit.inventory.ItemStack
 *       CRAFT   → org.bukkit.inventory.ItemStack (résultat)
 */
public class PersonalQuestRegistry {

    private static final Map<String, PersonalQuestDefinition> REGISTRY = new LinkedHashMap<>();

    static {

        // ── EXPLORER ─────────────────────────────────────────────────

        register(new PersonalQuestDefinition(
                "reach_summit",
                "§6⛰ Conquérant des sommets",
                "Atteindre une altitude ≥ 200 dans un biome montagne",
                MOVE,
                (player, ctx) -> {
                    int y = player.getLocation().getBlockY();
                    if (y < 200) return false;
                    Biome biome = player.getLocation().getBlock().getBiome();
                    return isMountainBiome(biome);
                },
                1
        ));

        register(new PersonalQuestDefinition(
                "reach_deep_ocean",
                "§9🌊 Abyssal",
                "Plonger à Y ≤ -40 dans un biome océan profond",
                MOVE,
                (player, ctx) -> {
                    int y = player.getLocation().getBlockY();
                    if (y > -40) return false;
                    Biome biome = player.getLocation().getBlock().getBiome();
                    return isDeepOceanBiome(biome);
                },
                1
        ));

        register(new PersonalQuestDefinition(
                "cross_desert",
                "§e☀ Traversée du désert",
                "Parcourir 5 chunks différents dans un biome désert",
                MOVE,
                (player, ctx) -> {
                    Biome biome = player.getLocation().getBlock().getBiome();
                    return biome == Biome.DESERT;
                },
                5  // 5 chunks distincts — le listener gère la déduplication
        ));

        register(new PersonalQuestDefinition(
                "reach_nether_roof",
                "§4🔥 Au-dessus de l'Enfer",
                "Atteindre Y ≥ 127 dans le Nether",
                MOVE,
                (player, ctx) -> {
                    if (player.getWorld().getEnvironment()
                            != org.bukkit.World.Environment.NETHER) return false;
                    return player.getLocation().getBlockY() >= 127;
                },
                1
        ));

        // ── COMBAT ───────────────────────────────────────────────────

        register(new PersonalQuestDefinition(
                "kill_elder_guardian",
                "§b🐟 Chasseur des abysses",
                "Tuer un Elder Guardian",
                KILL,
                (player, ctx) -> {
                    if (!(ctx instanceof org.bukkit.entity.Entity e)) return false;
                    return e.getType() == org.bukkit.entity.EntityType.ELDER_GUARDIAN;
                },
                1
        ));

        register(new PersonalQuestDefinition(
                "kill_warden",
                "§8👁 Vainqueur des ténèbres",
                "Tuer un Warden",
                KILL,
                (player, ctx) -> {
                    if (!(ctx instanceof org.bukkit.entity.Entity e)) return false;
                    return e.getType() == org.bukkit.entity.EntityType.WARDEN;
                },
                1
        ));
    }

    // ── API ──────────────────────────────────────────────────────────

    public static void register(PersonalQuestDefinition def) {
        REGISTRY.put(def.id(), def);
    }

    /** Récupère une définition par id. Retourne null si inconnue. */
    public static PersonalQuestDefinition get(String id) {
        return REGISTRY.get(id);
    }

    /** Toutes les quêtes enregistrées. */
    public static Collection<PersonalQuestDefinition> all() {
        return REGISTRY.values();
    }

    // ── Helpers biome ────────────────────────────────────────────────

    private static boolean isMountainBiome(Biome biome) {
        return switch (biome) {
            case WINDSWEPT_HILLS, WINDSWEPT_GRAVELLY_HILLS,
                 WINDSWEPT_FOREST, WINDSWEPT_SAVANNA,
                 JAGGED_PEAKS, FROZEN_PEAKS, STONY_PEAKS,
                 MEADOW, GROVE, SNOWY_SLOPES -> true;
            default -> false;
        };
    }

    private static boolean isDeepOceanBiome(Biome biome) {
        return switch (biome) {
            case DEEP_OCEAN, DEEP_COLD_OCEAN,
                 DEEP_FROZEN_OCEAN, DEEP_LUKEWARM_OCEAN -> true;
            default -> false;
        };
    }
}