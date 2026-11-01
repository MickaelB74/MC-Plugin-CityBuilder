package com.citycore.npc;

import net.citizensnpcs.api.event.NPCOpenDoorEvent;
import net.citizensnpcs.api.event.NPCOpenGateEvent;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Openable;
import org.bukkit.block.data.type.Door;
import org.bukkit.block.data.type.Gate;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.BoundingBox;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Portes et portillons franchis par les PNJ de la cité (artisans, Alderic, habitants).
 *
 * Citizens n'ouvre une porte que si elle est un point du chemin calculé, et la referme dès que
 * la navigation s'arrête, même si le PNJ est encore dans l'embrasure : il restait bloqué devant
 * une porte fermée, ou coincé dedans. En complément, tous les 4 ticks :
 *  - ouverture : un PNJ qui marche (ou qui se trouve dans l'embrasure) ouvre la porte ou le
 *    portillon fermé qu'il touche presque, avec un geste du bras et le son de la porte ;
 *  - fermeture : une porte ouverte par un PNJ (ici ou par Citizens) est refermée un instant
 *    après que plus personne, PNJ ou joueur, ne se trouve à son abord : jamais sur quelqu'un.
 * Seuls les PNJ autorisés à ouvrir les portes (PATHFINDER_OPEN_DOORS) le font : les gardes et
 * les soldats ennemis non. Les portes en fer (qui ne s'ouvrent pas à la main) et les trappes ne
 * sont pas concernées ; une porte ouverte par un joueur reste ouverte.
 */
public final class DoorAssistant implements Listener {

    /** Marge (blocs) autour du corps d'un PNJ en marche : une porte fermée à portée s'ouvre. */
    private static final double REACH = 0.9;
    /** Abord d'une porte (blocs autour d'elle) qui doit être libre pour la refermer. */
    private static final double CLEAR = 1.0;
    /** Délai (ms) entre le dernier passage à l'abord d'une porte et sa fermeture. */
    private static final long HOLD_MS = 1500;

    private final JavaPlugin plugin;
    /** PNJ à surveiller (artisans du registre principal, habitants de leur registre propre). */
    private final List<Supplier<? extends Iterable<NPC>>> sources = new ArrayList<>();
    /** Portes ouvertes par des PNJ (moitié basse) → dernier passage à leur abord (ms). */
    private final Map<Block, Long> opened = new HashMap<>();

    public DoorAssistant(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** Ajoute des PNJ à surveiller (relus à chaque passage). */
    public void addSource(Supplier<? extends Iterable<NPC>> source) {
        sources.add(source);
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 4L);
    }

    private void tick() {
        long now = System.currentTimeMillis();
        List<Entity> bodies = new ArrayList<>(); // tous les PNJ affichés : personne ne se fait refermer une porte dessus
        for (Supplier<? extends Iterable<NPC>> source : sources) {
            Iterable<NPC> npcs = source.get();
            if (npcs == null) continue;
            for (NPC npc : npcs) {
                if (npc == null || !npc.isSpawned()) continue;
                Entity body = npc.getEntity();
                bodies.add(body);
                if (!Boolean.TRUE.equals(npc.data().get(NPC.Metadata.PATHFINDER_OPEN_DOORS, false))) continue;
                boolean walking = npc.getNavigator().isNavigating();
                openAround(body, walking, now);
            }
        }
        closeBehind(now, bodies);
    }

    /**
     * Ouvre les portes fermées autour d'un PNJ : à portée de main s'il marche, ou celle dans
     * laquelle il se trouve (refermée sur lui) même à l'arrêt.
     */
    private void openAround(Entity body, boolean walking, long now) {
        BoundingBox self = body.getBoundingBox();
        BoundingBox reach = walking ? self.clone().expand(REACH, 0, REACH) : self;
        World w = body.getWorld();
        int y0 = (int) Math.floor(self.getMinY());
        for (int x = (int) Math.floor(reach.getMinX()); x <= (int) Math.floor(reach.getMaxX()); x++) {
            for (int z = (int) Math.floor(reach.getMinZ()); z <= (int) Math.floor(reach.getMaxZ()); z++) {
                for (int y = y0; y <= y0 + 1; y++) {
                    Block door = bottomHalf(w.getBlockAt(x, y, z));
                    if (door == null || !closedPassage(door)) continue;
                    if (!walking && !self.overlaps(BoundingBox.of(door))) continue; // à l'arrêt : seulement s'il est dedans
                    setOpen(door, true);
                    if (body instanceof LivingEntity living) living.swingMainHand();
                    opened.put(door, now);
                }
            }
        }
    }

    /** Referme les portes ouvertes par les PNJ dont l'abord est libre depuis HOLD_MS. */
    private void closeBehind(long now, List<Entity> bodies) {
        Iterator<Map.Entry<Block, Long>> it = opened.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Block, Long> e = it.next();
            Block door = e.getKey();
            if (!door.getWorld().isChunkLoaded(door.getX() >> 4, door.getZ() >> 4)
                    || !(door.getBlockData() instanceof Openable o) || !o.isOpen()) {
                it.remove(); // zone déchargée, porte cassée ou déjà refermée (joueur, Citizens)
                continue;
            }
            BoundingBox around = BoundingBox.of(door, door.getRelative(BlockFace.UP)).expand(CLEAR, 0, CLEAR);
            if (occupied(around, door.getWorld(), bodies)) {
                e.setValue(now); // quelqu'un passe : on attend qu'il s'éloigne
                continue;
            }
            if (now - e.getValue() < HOLD_MS) continue;
            setOpen(door, false);
            it.remove();
        }
    }

    private static boolean occupied(BoundingBox around, World world, List<Entity> bodies) {
        for (Entity body : bodies) {
            if (body.getWorld().equals(world) && body.getBoundingBox().overlaps(around)) return true;
        }
        for (Player p : world.getPlayers()) {
            if (p.getBoundingBox().overlaps(around)) return true;
        }
        return false;
    }

    /** Porte ou portillon fermé, qu'on ouvre à la main (pas en fer). */
    private static boolean closedPassage(Block block) {
        BlockData data = block.getBlockData();
        if (!(data instanceof Door) && !(data instanceof Gate)) return false;
        if (block.getType() == Material.IRON_DOOR || block.getType().name().contains("IRON")) return false;
        return !((Openable) data).isOpen();
    }

    /** Moitié basse d'une porte (le portillon tel quel), ou null si ce n'est ni l'un ni l'autre. */
    private static Block bottomHalf(Block block) {
        BlockData data = block.getBlockData();
        if (data instanceof Door door) return door.getHalf() == Bisected.Half.TOP ? block.getRelative(BlockFace.DOWN) : block;
        return data instanceof Gate ? block : null;
    }

    /** Ouvre ou ferme (la moitié haute d'une porte suit), avec le son de la porte. */
    private static void setOpen(Block door, boolean open) {
        BlockData data = door.getBlockData();
        if (!(data instanceof Openable openable) || openable.isOpen() == open) return;
        openable.setOpen(open);
        door.setBlockData(openable, true);
        Block top = door.getRelative(BlockFace.UP);
        if (data instanceof Door && top.getBlockData() instanceof Door upper && upper.isOpen() != open) {
            upper.setOpen(open);
            top.setBlockData(upper, false);
        }
        boolean gate = data instanceof Gate;
        boolean copper = door.getType().name().contains("COPPER");
        Sound sound = gate ? (open ? Sound.BLOCK_FENCE_GATE_OPEN : Sound.BLOCK_FENCE_GATE_CLOSE)
                : copper ? (open ? Sound.BLOCK_COPPER_DOOR_OPEN : Sound.BLOCK_COPPER_DOOR_CLOSE)
                : (open ? Sound.BLOCK_WOODEN_DOOR_OPEN : Sound.BLOCK_WOODEN_DOOR_CLOSE);
        door.getWorld().playSound(door.getLocation().add(0.5, 0.5, 0.5), sound, 0.8f, 0.9f + (float) Math.random() * 0.2f);
    }

    /* =========================
       PORTES OUVERTES PAR CITIZENS : refermées elles aussi derrière le PNJ
       ========================= */

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCitizensDoor(NPCOpenDoorEvent event) {
        Block door = bottomHalf(event.getDoor());
        if (door != null) opened.put(door, System.currentTimeMillis());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCitizensGate(NPCOpenGateEvent event) {
        Block gate = bottomHalf(event.getGate());
        if (gate != null) opened.put(gate, System.currentTimeMillis());
    }
}
