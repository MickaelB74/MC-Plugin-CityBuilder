package com.citycore.npc;

import com.citycore.inhabitant.NpcGestures;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.ai.EntityTarget;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.npc.NPCRegistry;
import net.citizensnpcs.trait.HologramTrait;
import net.citizensnpcs.trait.SkinTrait;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Map;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class NPCManager {

    private final JavaPlugin plugin;
    private final Set<CityNPC> wanderingNPCs = new HashSet<>();

    public NPCManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }
    public JavaPlugin getPlugin() { return plugin; }

    /**
     * À appeler dans onEnable — réattache la logique aux NPCs
     * déjà spawné par Citizens au redémarrage du serveur.
     */
    public void restoreNPCs() {
        int total = 0;
        int restored = 0;

        for (NPC npc : CitizensAPI.getNPCRegistry()) {
            total++;
            plugin.getLogger().info("NPC trouvé : id=" + npc.getId()
                    + " name=" + npc.getName()
                    + " hasMayorTag=" + npc.data().has(CityNPC.MAYOR.tag)
                    + " hasMasonTag=" + npc.data().has(CityNPC.STONEMASON.tag));

            CityNPC type = getNPCType(npc);
            if (type == null) {
                plugin.getLogger().info("  → Pas un NPC CityCore, ignoré.");
                continue;
            }

            applyMovement(npc); // PNJ créés par une version précédente du plugin
            applySkin(npc, type); // skin ajouté depuis dans config.yml (npc-skins)
            NpcGestures.prepare(npc); // pas de balancement de bras à l'apparition (Epic Fight)
            if (!npc.isSpawned() && npc.getStoredLocation() != null) {
                npc.spawn(npc.getStoredLocation());
            }
            // Geste resté posé à l'arrêt du serveur (Citizens remet les étiquettes) : jamais rejoué
            if (NpcGestures.clearLeftovers(npc)) {
                plugin.getLogger().info("  → Geste resté en attente retiré : " + type.displayName);
            }

            plugin.getLogger().info("  → Restauré : " + type.displayName);
            restored++;
        }

        plugin.getLogger().info("Bilan : " + total + " NPC(s) Citizens, "
                + restored + " restauré(s).");
    }

    /* =========================
       SPAWN GÉNÉRIQUE
       ========================= */

    /**
     * Méthode générique — peut spawner n'importe quel CityNPC.
     * Exemple futur : spawnNPC(CityNPC.BLACKSMITH, player)
     */
    public NPC spawnNPC(CityNPC type, Location location) {
        NPCRegistry registry = CitizensAPI.getNPCRegistry();
        NPC npc = registry.createNPC(EntityType.PLAYER, type.displayName);
        npc.data().setPersistent(type.tag, true);

        // Skin AVANT spawn (enum, sinon config npc-skins.<clé>)
        String[] skin = resolveSkin(type);
        if (skin != null) {
            SkinTrait skinTrait = npc.getOrAddTrait(SkinTrait.class);
            skinTrait.setSkinPersistent(type.skinId, skin[1], skin[0]);
        } else {
            plugin.getLogger().info("Aucun skin pour " + type.skinId
                    + " — renseignez npc-skins." + type.skinId + " dans config.yml.");
        }

        // Hologramme
        HologramTrait hologram = npc.getOrAddTrait(HologramTrait.class);
        hologram.addLine(type.hologramLine());

        applyMovement(npc);
        NpcGestures.prepare(npc);
        npc.spawn(location);

        // Refresh SkinsRestorer pour les clients crackés
        if (skin != null) {
            Bukkit.getScheduler().runTaskLater(plugin, () ->
                    forceSkinsRestorerRefresh(npc, type), 20L);
        }

        return npc;
    }

    /**
     * Capacités de déplacement communes aux PNJ de la cité : nage en surface, ouverture des portes.
     * Citizens ne l'active par défaut que pour les créatures aquatiques : un PNJ humain
     * coulait au fond de l'eau au lieu de nager derrière le joueur.
     */
    private void applyMovement(NPC npc) {
        npc.data().setPersistent(NPC.Metadata.SWIM, true);
        // Les ateliers ont une porte (exigence des bâtiments) : sans cela, le PNJ restait dehors
        npc.data().setPersistent(NPC.Metadata.PATHFINDER_OPEN_DOORS, true);
    }

    /** Applique le skin connu (plugin ou config.yml) s'il diffère de celui que porte le PNJ. */
    private void applySkin(NPC npc, CityNPC type) {
        String[] skin = resolveSkin(type);
        if (skin == null) return;
        SkinTrait trait = npc.getOrAddTrait(SkinTrait.class);
        if (skin[0].equals(trait.getTexture())) return;
        trait.setSkinPersistent(type.skinId, skin[1], skin[0]);
        plugin.getLogger().info("Skin de " + type.skinId + " appliqué.");
    }

    /**
     * Skin d'un PNJ : {value, signature}, ou null si aucun n'est défini.
     * Priorité aux valeurs codées dans CityNPC, puis à config.yml
     * (npc-skins.<clé>.value / signature — générables sur mineskin.org).
     */
    public String[] resolveSkin(CityNPC type) {
        if (type.skinValue != null && !type.skinValue.isBlank()) {
            return new String[]{type.skinValue, type.skinSignature};
        }
        String base  = "npc-skins." + type.skinId + ".";
        String value = plugin.getConfig().getString(base + "value", "");
        String sig   = plugin.getConfig().getString(base + "signature", "");
        return value.isBlank() || sig.isBlank() ? null : new String[]{value, sig};
    }

    /* =========================
       SPAWN MAIRE (helper)
       ========================= */

    /**
     * Spawne le maire 2 blocs devant le joueur, face à lui.
     */
    public NPC spawnMayor(Player player) {
        Location loc = player.getLocation().clone();
        loc.add(loc.getDirection().normalize().multiply(2));
        loc.setY(Math.floor(loc.getY() + 1));

        Location mayorLoc = loc.clone();
        mayorLoc.setYaw((player.getLocation().getYaw() + 180) % 360);
        mayorLoc.setPitch(0);

        return spawnNPC(CityNPC.MAYOR, mayorLoc);
    }

    /* =========================
       FIND / CHECK / REMOVE
       ========================= */

    public NPC getNPC(CityNPC type) {
        String target = ChatColor.stripColor(type.displayName);
        for (NPC npc : CitizensAPI.getNPCRegistry()) {
            String npcStripped = ChatColor.stripColor(npc.getName());
            if (npc.data().has(type.tag) || npcStripped.equals(target)) return npc;
        }
        return null;
    }

    public boolean isNPCType(NPC npc, CityNPC type) {
        return npc.data().has(type.tag);
    }

    /**
     * Retrouve le type d'un NPC Citizens depuis ses données.
     * Utile dans les listeners pour identifier qui on clique.
     */
    public CityNPC getNPCType(NPC npc) {
        for (CityNPC type : CityNPC.values()) {
            if (npc.data().has(type.tag)) return type;
        }
        return null;
    }

    public void removeNPC(CityNPC type) {
        NPC npc = getNPC(type);
        if (npc != null) npc.destroy();
    }

    /* =========================
       SKINSRESTORER REFRESH
       ========================= */

    private void forceSkinsRestorerRefresh(NPC npc, CityNPC type) {
        if (!npc.isSpawned()) return;
        if (Bukkit.getPluginManager().getPlugin("SkinsRestorer") == null) return;

        try {
            Bukkit.dispatchCommand(
                    Bukkit.getConsoleSender(),
                    "sr set " + npc.getEntity().getName() + " " + type.skinId
            );
            plugin.getLogger().info("✅ Skin " + type.skinId + " appliqué via SkinsRestorer.");
        } catch (Exception e) {
            plugin.getLogger().warning("⚠ SkinsRestorer refresh échoué : " + e.getMessage());
        }
    }

    /* =========================
       MODE SUIVI (générique)
       Un PNJ ne suit qu'une personne à la fois. Un superviseur relance le suivi s'il
       s'interrompt (chemin perdu, conversation terminée), fait presser le pas au PNJ
       distancé, le fait rejoindre un joueur parti trop loin et monter dans sa barque.
       ========================= */

    private static final double FOLLOW_MARGIN     = 2.5;  // distance de confort derrière le joueur
    private static final double HURRY_DISTANCE    = 10;   // au-delà, le PNJ presse le pas
    private static final double CATCH_UP_DISTANCE = 32;   // au-delà, il rejoint le joueur
    private static final double BOARD_DISTANCE    = 8;    // monte dans la barque du joueur
    private static final float  WALK_SPEED        = 1.0f;
    private static final float  HURRY_SPEED       = 1.35f;

    /** PNJ → joueur suivi. */
    private final Map<CityNPC, UUID> followers = new EnumMap<>(CityNPC.class);

    public void startFollowing(Player player, CityNPC type) {
        NPC npc = getNPC(type);
        if (npc == null || !npc.isSpawned()) return;
        followers.put(type, player.getUniqueId());
        npc.getNavigator().setPaused(false);
        navigateTo(npc, player, false);
    }

    public void stopFollowing(Player player, CityNPC type) {
        if (!player.getUniqueId().equals(followers.get(type))) return;
        followers.remove(type);
        NPC npc = getNPC(type);
        if (npc != null && npc.isSpawned()) {
            npc.getNavigator().cancelNavigation();
            if (npc.getEntity().isInsideVehicle()) npc.getEntity().leaveVehicle();
        }
    }

    public boolean isFollowing(Player player, CityNPC type) {
        return player.getUniqueId().equals(followers.get(type));
    }

    /** Le PNJ suit-il quelqu'un (ses autres déplacements sont alors suspendus) ? */
    public boolean isFollowingSomeone(CityNPC type) {
        return followers.containsKey(type);
    }

    /** Surveille les PNJ qui suivent un joueur, deux fois par seconde. */
    public void startFollowSupervisor() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Map.Entry<CityNPC, UUID> entry : new ArrayList<>(followers.entrySet())) {
                    NPC    npc    = getNPC(entry.getKey());
                    Player player = Bukkit.getPlayer(entry.getValue());
                    if (player == null) {
                        // Joueur déconnecté : le PNJ s'arrête où il est
                        followers.remove(entry.getKey());
                        if (npc != null && npc.isSpawned()) {
                            npc.getNavigator().cancelNavigation();
                            if (npc.getEntity().isInsideVehicle()) npc.getEntity().leaveVehicle();
                        }
                        continue;
                    }
                    if (npc != null && npc.isSpawned()) superviseFollower(npc, player);
                }
            }
        }.runTaskTimer(plugin, 10L, 10L);
    }

    private void superviseFollower(NPC npc, Player player) {
        Entity body = npc.getEntity();

        // Autre dimension : il attend le retour du joueur là où il est
        if (!body.getWorld().equals(player.getWorld())) {
            if (npc.getNavigator().isNavigating()) npc.getNavigator().cancelNavigation();
            return;
        }

        // Barque : il descend quand le joueur descend, et monte s'il reste une place
        Entity vehicle = player.getVehicle();
        if (body.getVehicle() != null && body.getVehicle() != vehicle) body.leaveVehicle();
        if (vehicle instanceof Boat boat) {
            if (body.getVehicle() == boat) return; // déjà à bord
            int seats = boat instanceof ChestBoat ? 1 : 2;
            if (boat.getPassengers().size() < seats
                    && body.getLocation().distanceSquared(boat.getLocation()) <= BOARD_DISTANCE * BOARD_DISTANCE) {
                npc.getNavigator().cancelNavigation();
                boat.addPassenger(body);
                return;
            }
        }

        if (npc.getNavigator().isPaused()) return; // en pleine conversation

        double dist2 = body.getLocation().distanceSquared(player.getLocation());
        if (dist2 > CATCH_UP_DISTANCE * CATCH_UP_DISTANCE) {
            catchUp(npc, player);
            return;
        }
        boolean hurry = dist2 > HURRY_DISTANCE * HURRY_DISTANCE;
        EntityTarget target = npc.getNavigator().getEntityTarget();
        boolean onPlayer = npc.getNavigator().isNavigating() && target != null
                && target.getTarget() != null && target.getTarget().getUniqueId().equals(player.getUniqueId());
        if (onPlayer) {
            npc.getNavigator().getLocalParameters().speedModifier(hurry ? HURRY_SPEED : WALK_SPEED);
        } else if (dist2 > FOLLOW_MARGIN * FOLLOW_MARGIN) {
            navigateTo(npc, player, hurry); // suivi interrompu : on le relance
        }
    }

    private void navigateTo(NPC npc, Player player, boolean hurry) {
        // Le pathfinder charge tous les chunks de sa portée : on la limite à la zone déjà chargée
        float range = SafeNavigation.loadedRange(npc.getEntity().getLocation(), (float) CATCH_UP_DISTANCE + 16f);
        if (range < SafeNavigation.MIN_RANGE) return; // zone pas prête : nouvel essai au prochain passage
        npc.getNavigator().getDefaultParameters()
                .range(range)
                .distanceMargin(FOLLOW_MARGIN)
                .avoidWater(false)        // traverse l'eau à la nage plutôt qu'un long détour
                .useNewPathfinder(true);
        npc.getNavigator().setTarget(player, false);
        npc.getNavigator().getLocalParameters().speedModifier(hurry ? HURRY_SPEED : WALK_SPEED);
    }

    /** Trop de retard (joueur à cheval, en barque pleine, chute…) : le PNJ le rejoint. */
    private void catchUp(NPC npc, Player player) {
        npc.getNavigator().cancelNavigation();
        npc.teleport(safeSpotNear(player), PlayerTeleportEvent.TeleportCause.PLUGIN);
        navigateTo(npc, player, false);
    }

    /** Point praticable à 2-3 blocs derrière le joueur (sol ferme ou surface de l'eau). */
    private Location safeSpotNear(Player player) {
        Location base = player.getLocation();
        Vector back = base.getDirection().setY(0);
        if (back.lengthSquared() < 1.0E-4) back = new Vector(0, 0, 1);
        back.normalize().multiply(-2.5);
        Vector side = new Vector(-back.getZ(), 0, back.getX());
        Vector[] offsets = {back, back.clone().add(side.clone().multiply(0.6)),
                back.clone().subtract(side.clone().multiply(0.6)), side, side.clone().multiply(-1)};
        for (Vector offset : offsets) {
            Location spot = standable(base.clone().add(offset));
            if (spot != null) {
                spot.setDirection(base.toVector().subtract(spot.toVector())); // face au joueur
                spot.setPitch(0);
                return spot;
            }
        }
        return base.clone();
    }

    /** Ajuste la hauteur (±2 blocs) sur un sol ferme ou la surface de l'eau ; null si rien de sûr. */
    private Location standable(Location around) {
        World world = around.getWorld();
        int x = around.getBlockX();
        int z = around.getBlockZ();
        for (int dy : new int[]{0, 1, -1, 2, -2}) {
            int   y     = around.getBlockY() + dy;
            Block feet  = world.getBlockAt(x, y, z);
            Block head  = world.getBlockAt(x, y + 1, z);
            Block below = world.getBlockAt(x, y - 1, z);
            boolean headFree = head.isPassable() && !head.isLiquid();
            boolean ground   = below.getType().isSolid() && feet.isPassable() && !feet.isLiquid();
            boolean swimming = feet.getType() == Material.WATER;
            if (headFree && (ground || swimming)) return new Location(world, x + 0.5, y, z + 0.5);
        }
        return null;
    }

    public boolean isSpawned(CityNPC type) {
        NPC npc = getNPC(type);
        return npc != null && npc.isSpawned();
    }

    public void removeAndReset(CityNPC type) {
        NPC npc = getNPC(type);
        if (npc != null) npc.destroy();
    }

    public boolean isWandering(CityNPC type) {
        return wanderingNPCs.contains(type);
    }

    public void setWandering(CityNPC type, boolean wandering) {
        if (wandering) {
            wanderingNPCs.add(type);
        } else {
            wanderingNPCs.remove(type);
            // Stop la navigation en cours
            NPC npc = getNPC(type);
            if (npc != null && npc.isSpawned()) {
                npc.getNavigator().cancelNavigation();
            }
        }
    }
}