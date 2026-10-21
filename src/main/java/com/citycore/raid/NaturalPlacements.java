package com.citycore.raid;

import org.bukkit.World;
import org.bukkit.generator.structure.Structure;

import java.lang.reflect.Method;
import java.util.List;

/**
 * La structure peut-elle naître dans ce monde ? Le générateur ne garde, pour chaque structure,
 * que les placements dont au moins un biome existe dans le monde
 * ({@code ChunkGeneratorStructureState#getPlacementsForStructure}) : une liste vide veut dire que
 * la structure n'y apparaîtra jamais, quelle que soit la distance de recherche. Exemple : les
 * villages et avant-postes « de style » de Towns and Towers (exclusives/…), qui ne naissent que
 * dans des biomes de Terralith, Biomes O' Plenty ou Wythers, absents du serveur.
 *
 * Lu par réflexion ; noms de Mojang puis noms Spigot présentés par Youer (table spigot2srg de
 * NeoForge : getGeneratorState = h, getPlacementsForStructure = a, registryAccess = H_,
 * registryOrThrow = d, wrapAsHolder = e, Registries.STRUCTURE = aR).
 */
final class NaturalPlacements {

    private final Object generatorState;
    private final Object structureRegistry;
    private final Method wrapAsHolder;
    private final Method placementsFor;

    private NaturalPlacements(Object generatorState, Object structureRegistry, Method wrapAsHolder, Method placementsFor) {
        this.generatorState    = generatorState;
        this.structureRegistry = structureRegistry;
        this.wrapAsHolder      = wrapAsHolder;
        this.placementsFor     = placementsFor;
    }

    /** Pour ce monde, ou null si le serveur n'offre pas ces méthodes ({@code error} reçoit la raison). */
    static NaturalPlacements of(World world, StringBuilder error) {
        try {
            Object level = world.getClass().getMethod("getHandle").invoke(world);
            Object source = level.getClass().getMethod("getChunkSource").invoke(level);
            ClassLoader loader = source.getClass().getClassLoader();
            Object state = method(source.getClass(), new Class<?>[0], "getGeneratorState", "h").invoke(source);
            Object access = method(level.getClass(), new Class<?>[0], "registryAccess", "H_").invoke(level);
            Class<?> resourceKey = Class.forName("net.minecraft.resources.ResourceKey", false, loader);
            Class<?> registries = Class.forName("net.minecraft.core.registries.Registries", false, loader);
            Object structureKey = field(registries, "STRUCTURE", "aR").get(null);
            Object registry = method(access.getClass(), new Class<?>[]{resourceKey}, "registryOrThrow", "d").invoke(access, structureKey);
            Method wrap = method(registry.getClass(), new Class<?>[]{Object.class}, "wrapAsHolder", "e");
            Class<?> holder = Class.forName("net.minecraft.core.Holder", false, loader);
            Method placements = method(state.getClass(), new Class<?>[]{holder}, "getPlacementsForStructure", "a");
            return new NaturalPlacements(state, registry, wrap, placements);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            error.append(e.getClass().getSimpleName()).append(" : ").append(e.getMessage());
            return null;
        }
    }

    /**
     * true si la structure a au moins un placement dans ce monde, false si elle n'y naîtra
     * jamais ; null si la réponse n'a pas pu être lue.
     */
    Boolean canGenerate(Structure structure) {
        try {
            Object handle = structure.getClass().getMethod("getHandle").invoke(structure);
            Object holder = wrapAsHolder.invoke(structureRegistry, handle);
            Object list = placementsFor.invoke(generatorState, holder);
            return list instanceof List<?> l ? !l.isEmpty() : null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            return null;
        }
    }

    /** Méthode publique de cette signature sous l'un des noms donnés (nom de Mojang, puis nom Spigot). */
    private static Method method(Class<?> c, Class<?>[] params, String... names) throws NoSuchMethodException {
        for (String name : names) {
            try {
                return c.getMethod(name, params);
            } catch (NoSuchMethodException ignored) {
                // nom suivant
            }
        }
        throw new NoSuchMethodException(c.getName() + "." + String.join("/", names));
    }

    private static java.lang.reflect.Field field(Class<?> c, String... names) throws NoSuchFieldException {
        for (String name : names) {
            try {
                return c.getField(name);
            } catch (NoSuchFieldException ignored) {
                // nom suivant
            }
        }
        throw new NoSuchFieldException(c.getName() + "." + String.join("/", names));
    }
}
