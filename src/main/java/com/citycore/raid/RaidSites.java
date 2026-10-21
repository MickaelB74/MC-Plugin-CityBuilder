package com.citycore.raid;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.TranslationArgument;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.generator.structure.GeneratedStructure;
import org.bukkit.generator.structure.Structure;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.StructureSearchResult;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Sites des raids (décision du 21/10/2026 : génération naturelle). Le monde des raids génère ses
 * structures comme un monde neuf, avec les mods de structures du serveur : chaque cible apparaît
 * telle que son mod la fait naître, sur son terrain, adaptée au relief. Pour un raid, on repère
 * l'exemplaire naturel non exploré le plus proche d'un point de départ neuf
 * ({@code locateNearestStructure}, la recherche de /locate ; l'exemplaire trouvé est marqué et ne
 * ressort plus), puis on en génère les chunks en tâche de fond.
 *
 * Remplace la pose par /place structure dans un terrain déjà généré : sans l'adaptation au
 * terrain de la génération, les structures flottaient, entaillaient les collines ou se coupaient.
 *
 * Génération : toujours en tâche de fond, jamais sur le fil principal — tickets de chargement du
 * jeu ({@link LoadTickets}), sinon {@code getChunkAtAsync} ; si le serveur n'offre ni l'un ni
 * l'autre, la préparation est refusée plutôt que de bloquer le serveur (sous Youer, un seul chunk
 * neuf généré sur le fil principal prend plusieurs secondes).
 */
public final class RaidSites {

    /** Premier point de départ des recherches (x = z), loin de la ville et du point d'apparition. */
    public static final int ORIGIN = 10_000;
    /** Chunks demandés en même temps pendant une préparation. */
    private static final int IN_FLIGHT = 16;

    /**
     * Un exemplaire repéré et généré.
     *
     * @param number numéro d'essai (1, 2…), pour /city raid tp
     * @param box    boîte de la structure générée
     */
    public record Site(int number, String structure, Location start, BoundingBox box, long locateMillis) {}

    private final JavaPlugin plugin;
    private final RaidConfig config;
    private final RaidWorld raidWorld;

    /** Recherches déjà lancées : le point de départ suivant est plus loin. */
    private int searches;
    /** Chunks de départ des exemplaires déjà pris (la commande /locate ne les écarte pas d'elle-même). */
    private final Set<Long> used = new HashSet<>();
    private final List<Site> history = new ArrayList<>();
    /** Tickets de chunk posés par une préparation, par clé (numéro d'essai). */
    private final Map<Integer, Set<Long>> tickets = new HashMap<>();
    /** getChunkAtAsync ne rend rien sur ce serveur (constaté à la première préparation). */
    private boolean asyncUnavailable;
    /** Tickets de chargement du jeu (génération en tâche de fond), pour le monde actuel. */
    private LoadTickets gameTickets;
    private boolean ticketsWarned;

    public RaidSites(JavaPlugin plugin, RaidConfig config, RaidWorld raidWorld) {
        this.plugin    = plugin;
        this.config    = config;
        this.raidWorld = raidWorld;
    }

    /* =========================
       REPÉRAGE
       ========================= */

    /**
     * Résultat d'une recherche : l'emplacement de départ de la structure, ou la raison de l'échec.
     *
     * @param how manière dont l'exemplaire a été trouvé (API, commande /locate), pour le journal
     */
    public record Found(Location start, long millis, String error, String how) {}

    /** Essais au plus quand /locate rend un exemplaire déjà pris : le point de départ s'éloigne. */
    private static final int LOCATE_TRIES = 4;
    /** Coordonnées dans le lien du retour de /locate : « /tp @s 1234 ~ -567 ». */
    private static final Pattern TP = Pattern.compile("/tp @s (-?\\d+) \\S+ (-?\\d+)");

    /**
     * Repère l'exemplaire naturel non exploré le plus proche d'un point de départ neuf ; la réponse
     * arrive par {@code done} (fil principal). D'abord par l'API ({@code locateNearestStructure},
     * exemplaire non exploré) ; si elle ne rend rien, par la commande du jeu
     * {@code /locate structure} lancée dans le monde des raids. Constaté sous Youer : l'API rend
     * « rien » sans chercher (0 ms), et la commande s'exécute un peu après son envoi, sa réponse
     * arrivant plus tard : on l'attend (au plus {@link #LOCATE_WAIT_MS}). La recherche elle-même
     * occupe le fil principal (3 s pour un village des plaines). Les exemplaires déjà pris, que
     * /locate ne marque pas, sont écartés : le point de départ s'éloigne et on recommence.
     */
    public void locate(Structure structure, String id, Consumer<Found> done) {
        World w = raidWorld.world();
        if (w == null) {
            done.accept(new Found(null, 0, "monde des raids absent", null));
            return;
        }
        attemptLocate(w, structure, id, 0, System.currentTimeMillis(), new StringBuilder(), done);
    }

    /** Attente maximale de la réponse de /locate. */
    private static final long LOCATE_WAIT_MS = 60_000L;

    private void attemptLocate(World w, Structure structure, String id, int attempt, long t0, StringBuilder notes,
                               Consumer<Found> done) {
        if (attempt >= LOCATE_TRIES) {
            done.accept(new Found(null, System.currentTimeMillis() - t0, "seulement des exemplaires déjà pris près des"
                    + " points de départ essayés (" + notes + ")", null));
            return;
        }
        int x = ORIGIN + searches * config.spacing, z = ORIGIN;
        searches++;
        StructureSearchResult result = null;
        try {
            result = w.locateNearestStructure(new Location(w, x, 64, z), structure, config.searchRadius, true);
        } catch (RuntimeException e) {
            notes.append("API en erreur (").append(e).append(") ; ");
        }
        if (result != null && !used.contains(key(result.getLocation()))) {
            done.accept(new Found(result.getLocation(), System.currentTimeMillis() - t0, null, "API"));
            return;
        }
        if (result == null) notes.append("API : rien ; ");
        locateByCommand(w, id, x, z, (found, why) -> {
            if (found == null) {
                notes.append("/locate : ").append(why);
                done.accept(new Found(null, System.currentTimeMillis() - t0, "aucun exemplaire naturel trouvé autour de x "
                        + x + ", z " + z + " (" + notes + ")", null));
            } else if (used.contains(key(found))) {
                notes.append("/locate : exemplaire déjà pris ; ");
                attemptLocate(w, structure, id, attempt + 1, t0, notes, done);
            } else {
                done.accept(new Found(found, System.currentTimeMillis() - t0, null, "commande /locate"));
            }
        });
    }

    /**
     * {@code execute in <monde> positioned x 64 z run locate structure <id>}, retour capturé
     * ({@code Bukkit.createCommandSender}) et attendu ; les coordonnées sont lues dans le lien
     * « /tp @s x ~ z » du message de réussite (ou dans ses arguments « chat.coordinates »).
     * {@code then} reçoit l'emplacement, ou null et la raison.
     */
    private void locateByCommand(World w, String id, int x, int z, java.util.function.BiConsumer<Location, String> then) {
        List<Component> feedback = java.util.Collections.synchronizedList(new ArrayList<>());
        CommandSender sender;
        try {
            sender = Bukkit.createCommandSender(feedback::add);
        } catch (RuntimeException | LinkageError e) {
            then.accept(null, "retour de commande non capturable");
            return;
        }
        String command = "execute in " + w.getKey() + " positioned " + x + " 64 " + z + " run locate structure " + id;
        try {
            Bukkit.dispatchCommand(sender, command);
        } catch (RuntimeException e) {
            then.accept(null, "commande en erreur (" + e.getMessage() + ")");
            return;
        }
        long start = System.currentTimeMillis();
        BukkitTask[] task = new BukkitTask[1];
        Runnable check = () -> {
            List<Component> got;
            synchronized (feedback) {
                got = new ArrayList<>(feedback);
            }
            if (got.isEmpty()) {
                if (System.currentTimeMillis() - start < LOCATE_WAIT_MS) return;
                if (task[0] != null) task[0].cancel();
                then.accept(null, "pas de réponse au bout de " + LOCATE_WAIT_MS / 1000 + " s");
                return;
            }
            if (task[0] != null) task[0].cancel();
            for (Component c : got) {
                int[] xz = coordinates(c);
                if (xz != null) {
                    then.accept(new Location(w, xz[0] + 0.5, 64, xz[1] + 0.5), null);
                    return;
                }
            }
            List<String> keys = new ArrayList<>();
            for (Component c : got) keys.add(c instanceof TranslatableComponent t ? t.key()
                    : PlainTextComponentSerializer.plainText().serialize(c));
            then.accept(null, String.join(", ", keys));
        };
        task[0] = Bukkit.getScheduler().runTaskTimer(plugin, check, 1L, 1L);
    }

    /** Coordonnées (x, z) d'un retour de /locate, ou null. */
    private static int[] coordinates(Component c) {
        ClickEvent click = c.clickEvent();
        if (click != null) {
            Matcher m = TP.matcher(click.value());
            if (m.find()) return new int[]{Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2))};
        }
        if (c instanceof TranslatableComponent t) {
            List<TranslationArgument> args = t.arguments();
            if (t.key().equals("chat.coordinates") && args.size() >= 3) {
                try {
                    PlainTextComponentSerializer plain = PlainTextComponentSerializer.plainText();
                    return new int[]{Integer.parseInt(plain.serialize(args.get(0).asComponent()).trim()),
                            Integer.parseInt(plain.serialize(args.get(2).asComponent()).trim())};
                } catch (NumberFormatException ignored) {
                    // pas des nombres : on continue dans les enfants
                }
            }
            for (TranslationArgument a : args) {
                int[] r = coordinates(a.asComponent());
                if (r != null) return r;
            }
        }
        for (Component child : c.children()) {
            int[] r = coordinates(child);
            if (r != null) return r;
        }
        return null;
    }

    /** Clé du chunk de départ d'un exemplaire (exemplaires déjà pris). */
    private static long key(Location l) {
        return Chunk.getChunkKey(l.getBlockX() >> 4, l.getBlockZ() >> 4);
    }

    /**
     * Structure générée qui commence au chunk de {@code start} (chunk chargé) : la plus proche de
     * ce point parmi celles qui touchent ce chunk.
     */
    public GeneratedStructure generatedAt(Location start, Structure structure) {
        World w = start.getWorld();
        GeneratedStructure best = null;
        double bestDist = Double.MAX_VALUE;
        for (GeneratedStructure gs : w.getStructures(start.getBlockX() >> 4, start.getBlockZ() >> 4, structure)) {
            BoundingBox b = gs.getBoundingBox();
            double d = Math.pow(b.getCenterX() - start.getX(), 2) + Math.pow(b.getCenterZ() - start.getZ(), 2);
            if (d < bestDist) {
                bestDist = d;
                best = gs;
            }
        }
        return best;
    }

    /** Note l'essai (pour /city raid tp et /city raid zones) ; l'exemplaire ne ressortira plus. */
    public Site record(String structure, Location start, BoundingBox box, long locateMillis) {
        Site site = new Site(history.size() + 1, structure, start, box, locateMillis);
        history.add(site);
        used.add(key(start));
        return site;
    }

    public List<Site> history() { return history; }

    public Site site(int number) {
        return number >= 1 && number <= history.size() ? history.get(number - 1) : null;
    }

    public int searches() { return searches; }

    /** Monde recréé : recherches et essais oubliés. */
    public void reset() {
        releaseAll();
        history.clear();
        used.clear();
        searches = 0;
        gameTickets = null;
    }

    /* =========================
       GÉNÉRATION DES CHUNKS
       ========================= */

    /**
     * Génère (en tâche de fond) les chunks qui couvrent {@code box} élargie de {@code margin}
     * blocs, puis les garde chargés jusqu'à {@link #release}.
     *
     * @param progress avancement en % (appelé à chaque dizaine franchie)
     * @param done     null si tout est prêt, sinon la raison de l'échec
     */
    public void prepare(int key, BoundingBox box, int margin, IntConsumer progress, Consumer<String> done) {
        Deque<int[]> queue = new ArrayDeque<>();
        int x0 = ((int) Math.floor(box.getMinX()) - margin) >> 4, x1 = ((int) Math.floor(box.getMaxX()) + margin) >> 4;
        int z0 = ((int) Math.floor(box.getMinZ()) - margin) >> 4, z1 = ((int) Math.floor(box.getMaxZ()) + margin) >> 4;
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) queue.add(new int[]{x, z});
        load(key, queue, progress, done);
    }

    /** Charge (ou génère) les chunks de la file et y pose un ticket ; ceux déjà tenus sont passés. */
    private void load(int key, Deque<int[]> queue, IntConsumer progress, Consumer<String> done) {
        World w = raidWorld.world();
        if (w == null) {
            done.accept("monde des raids absent");
            return;
        }
        Set<Long> held = tickets.computeIfAbsent(key, k -> new HashSet<>());
        queue.removeIf(c -> held.contains(Chunk.getChunkKey(c[0], c[1])));
        int total = queue.size();
        if (total == 0) {
            done.accept(null);
            return;
        }
        LoadTickets game = gameTickets(w);
        if (game == null && asyncUnavailable) {
            done.accept(NO_BACKGROUND);
            return;
        }
        Map<Long, int[]> pending = new LinkedHashMap<>(); // demandés, pas encore chargés
        int[] state = {0, 0, -1}; // en cours (getChunkAtAsync), chargés, dernière dizaine annoncée
        long[] lastProgress = {System.currentTimeMillis()};
        boolean[] finished = {false};
        BukkitTask[] task = new BukkitTask[1];
        Consumer<String> finish = err -> {
            finished[0] = true;
            task[0].cancel();
            if (game != null) pending.values().forEach(c -> game.remove(c[0], c[1]));
            pending.clear();
            done.accept(err);
        };
        Consumer<Chunk> loaded = chunk -> {
            chunk.addPluginChunkTicket(plugin); // chunk déjà chargé : rien n'est généré ici
            held.add(Chunk.getChunkKey(chunk.getX(), chunk.getZ()));
            state[1]++;
            lastProgress[0] = System.currentTimeMillis();
            int tenth = state[1] * 10 / total;
            if (tenth != state[2]) {
                state[2] = tenth;
                progress.accept(tenth * 10);
            }
            if (state[1] == total) finish.accept(null);
        };
        task[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (finished[0]) return;
            if (System.currentTimeMillis() - lastProgress[0] > 120_000L) {
                finish.accept("chunks non générés au bout de 2 min (" + state[1] + "/" + total + ")");
                return;
            }
            if (game != null) {
                // Arrivées : le ticket du plugin prend le relais du ticket du jeu
                for (java.util.Iterator<int[]> it = pending.values().iterator(); it.hasNext() && !finished[0]; ) {
                    int[] c = it.next();
                    if (!w.isChunkLoaded(c[0], c[1])) continue;
                    it.remove();
                    Chunk chunk = w.getChunkAt(c[0], c[1]);
                    game.remove(c[0], c[1]);
                    loaded.accept(chunk);
                }
                // Nouvelles demandes, quelques-unes à la fois
                while (!finished[0] && pending.size() < IN_FLIGHT && !queue.isEmpty()) {
                    int[] c = queue.poll();
                    if (w.isChunkLoaded(c[0], c[1])) {
                        loaded.accept(w.getChunkAt(c[0], c[1]));
                        continue;
                    }
                    try {
                        game.add(c[0], c[1]);
                    } catch (ReflectiveOperationException | RuntimeException e) {
                        finish.accept("ticket de chargement refusé (" + e + ")");
                        return;
                    }
                    pending.put(Chunk.getChunkKey(c[0], c[1]), c);
                }
                return;
            }
            while (state[0] < IN_FLIGHT && !queue.isEmpty()) {
                int[] c = queue.poll();
                state[0]++;
                w.getChunkAtAsync(c[0], c[1], true).whenComplete((chunk, err) -> sync(() -> {
                    state[0]--;
                    if (finished[0]) return;
                    if (err != null || chunk == null) {
                        asyncUnavailable = true;
                        finish.accept(NO_BACKGROUND);
                        return;
                    }
                    loaded.accept(chunk);
                }));
            }
        }, 1L, 1L);
    }

    /** Raison d'un refus quand le serveur ne sait pas générer en tâche de fond. */
    private static final String NO_BACKGROUND = "ce serveur ne génère pas les chunks en tâche de fond ;"
            + " rien n'est généré sur le fil principal pour ne pas le bloquer";

    /** Tickets de chargement du jeu pour ce monde (résolus une fois par monde), ou null. */
    private LoadTickets gameTickets(World w) {
        if (gameTickets != null && gameTickets.isFor(w)) return gameTickets;
        StringBuilder error = new StringBuilder();
        gameTickets = LoadTickets.of(w, error);
        if (gameTickets != null) {
            plugin.getLogger().info("⚔ [Raids] Génération des sites par tickets de chargement du jeu (fils de génération).");
        } else if (!ticketsWarned) {
            ticketsWarned = true;
            plugin.getLogger().warning("⚔ [Raids] Tickets de chargement du jeu indisponibles (" + error
                    + ") : getChunkAtAsync à la place.");
        }
        return gameTickets;
    }

    /**
     * Le chunk est-il chargé ? Sinon il est demandé en tâche de fond, et {@code then} est appelé
     * une fois arrivé (null) ou en cas d'échec (raison).
     */
    public void whenLoaded(World w, int chunkX, int chunkZ, Consumer<String> then) {
        if (w.isChunkLoaded(chunkX, chunkZ)) {
            then.accept(null);
            return;
        }
        LoadTickets game = gameTickets(w);
        if (game == null) {
            then.accept(NO_BACKGROUND);
            return;
        }
        try {
            game.add(chunkX, chunkZ);
        } catch (ReflectiveOperationException | RuntimeException e) {
            then.accept("ticket de chargement refusé (" + e + ")");
            return;
        }
        long start = System.currentTimeMillis();
        BukkitTask[] task = new BukkitTask[1];
        task[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            boolean ok = w.isChunkLoaded(chunkX, chunkZ);
            if (!ok && System.currentTimeMillis() - start < 60_000L) return;
            task[0].cancel();
            // Ticket retiré un peu plus tard : un ticket du plugin ou le joueur arrivé tient le chunk
            Bukkit.getScheduler().runTaskLater(plugin, () -> game.remove(chunkX, chunkZ), 200L);
            then.accept(ok ? null : "chunk non généré au bout de 60 s");
        }, 1L, 1L);
    }

    private void sync(Runnable r) {
        if (Bukkit.isPrimaryThread()) r.run();
        else Bukkit.getScheduler().runTask(plugin, r);
    }

    /** Rend les chunks d'une préparation (tickets retirés). */
    public void release(int key) {
        Set<Long> held = tickets.remove(key);
        World w = raidWorld.world();
        if (held == null || w == null) return;
        for (long k : held) {
            int x = (int) k, z = (int) (k >> 32);
            if (w.isChunkLoaded(x, z)) w.getChunkAt(x, z).removePluginChunkTicket(plugin);
        }
    }

    /** Tous les tickets rendus (purge, arrêt du serveur). */
    public void releaseAll() {
        for (Integer key : new ArrayList<>(tickets.keySet())) release(key);
    }

    public int heldChunks() {
        return tickets.values().stream().mapToInt(Set::size).sum();
    }
}
