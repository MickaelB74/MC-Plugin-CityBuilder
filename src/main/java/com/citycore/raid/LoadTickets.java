package com.citycore.raid;

import org.bukkit.World;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * Tickets de chargement du jeu, posés par réflexion (noms officiels de Mojang, ceux de NeoForge
 * et de Paper en 1.21.1) : le chunk est chargé, ou généré, par les fils de génération du serveur,
 * comme autour d'un joueur qui se déplace, **sans bloquer le fil principal**.
 *
 * Sous Youer, {@code getChunkAtAsync} ne rend rien pour un terrain jamais généré, et générer un
 * chunk neuf sur le fil principal ({@code getChunkAt}) prend plusieurs secondes (voisins, fluides) :
 * le 21/10/2026, une préparation de 441 chunks faite ainsi a figé le serveur jusqu'à son arrêt par
 * le watchdog. Ces tickets sont donc la seule façon de générer un emplacement.
 *
 * Ticket « forcé » au niveau 33 : chunk complet (bordure), sans tick des entités ni des blocs.
 * À poser et retirer sur le fil principal seulement.
 *
 * Noms : Youer présente aux plugins les classes du jeu sous leurs noms Spigot
 * ({@code ChunkProviderServer} pour {@code ServerChunkCache}) et traduit les noms demandés ; les
 * deux méthodes y sont {@code a(…)} et {@code b(…)} (table spigot2srg de NeoForge sous Youer),
 * {@code addRegionTicket} et {@code removeRegionTicket} sous Paper. Les deux écritures sont
 * essayées, avec la signature exacte (TicketType, ChunkPos, int, Object).
 */
final class LoadTickets {

    private final World world;
    private final Object chunkSource;
    private final Object type;
    private final Method add;
    private final Method remove;
    private final Constructor<?> chunkPos;

    private LoadTickets(World world, Object chunkSource, Object type, Method add, Method remove, Constructor<?> chunkPos) {
        this.world       = world;
        this.chunkSource = chunkSource;
        this.type        = type;
        this.add         = add;
        this.remove      = remove;
        this.chunkPos    = chunkPos;
    }

    /** Pour ce monde, ou null si le serveur n'offre pas ces méthodes ({@code error} reçoit la raison). */
    static LoadTickets of(World world, StringBuilder error) {
        try {
            Object level = world.getClass().getMethod("getHandle").invoke(world);
            Object source = level.getClass().getMethod("getChunkSource").invoke(level);
            ClassLoader loader = source.getClass().getClassLoader();
            Class<?> ticketType = Class.forName("net.minecraft.server.level.TicketType", false, loader);
            Object forced = ticketType.getField("FORCED").get(null);
            Constructor<?> pos = Class.forName("net.minecraft.world.level.ChunkPos", false, loader)
                    .getConstructor(int.class, int.class);
            Class<?>[] params = {ticketType, pos.getDeclaringClass(), int.class, Object.class};
            Method add = method(source.getClass(), params, "addRegionTicket", "a");
            Method remove = method(source.getClass(), params, "removeRegionTicket", "b");
            return new LoadTickets(world, source, forced, add, remove, pos);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            error.append(e.getClass().getSimpleName()).append(" : ").append(e.getMessage());
            return null;
        }
    }

    /**
     * Méthode publique de cette signature sous l'un des noms donnés (nom de Mojang, puis nom
     * Spigot). Échec : la raison cite les méthodes de même signature trouvées, pour le journal.
     */
    private static Method method(Class<?> c, Class<?>[] params, String... names) throws NoSuchMethodException {
        for (String name : names) {
            try {
                Method m = c.getMethod(name, params);
                if (m.getReturnType() == void.class) return m;
            } catch (NoSuchMethodException ignored) {
                // nom suivant
            }
        }
        java.util.List<String> same = new java.util.ArrayList<>();
        for (Method m : c.getMethods()) {
            if (java.util.Arrays.equals(m.getParameterTypes(), params)) same.add(m.getName());
        }
        throw new NoSuchMethodException(c.getName() + "." + String.join("/", names)
                + " (même signature : " + (same.isEmpty() ? "aucune" : String.join(", ", same)) + ")");
    }

    boolean isFor(World w) {
        return w != null && w.getUID().equals(world.getUID());
    }

    /** Demande le chunk (chargé ou généré en tâche de fond). */
    void add(int x, int z) throws ReflectiveOperationException {
        Object pos = chunkPos.newInstance(x, z);
        add.invoke(chunkSource, type, pos, 0, pos);
    }

    /** Retire la demande (le chunk reste chargé tant qu'un autre ticket ou un joueur le tient). */
    void remove(int x, int z) {
        try {
            Object pos = chunkPos.newInstance(x, z);
            remove.invoke(chunkSource, type, pos, 0, pos);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // monde déchargé entre-temps : plus rien à retirer
        }
    }
}
