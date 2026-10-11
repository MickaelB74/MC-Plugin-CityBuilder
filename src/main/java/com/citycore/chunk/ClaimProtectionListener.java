package com.citycore.chunk;

import com.citycore.city.CityManager;
import com.citycore.city.ClaimCache;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Rabbit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.entity.EntityBreakDoorEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityInteractEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.Set;

public class ClaimProtectionListener implements Listener {

    private final CityManager cityManager;
    private final ClaimCache  claims; // chunks de la ville en cache (feu et blocs : événements fréquents)
    /** Assaillant d'un siège en cours autorisé à blesser les joueurs en ville (sieges.joueurs-blesses). */
    private java.util.function.Predicate<Entity> siegeAttacker;

    /** Types d'entités qui peuvent exploser. */
    private static final Set<EntityType> EXPLOSIVE_TYPES = Set.of(
            EntityType.CREEPER,
            EntityType.TNT,
            EntityType.TNT_MINECART,
            EntityType.FIREBALL,
            EntityType.SMALL_FIREBALL,
            EntityType.WITHER_SKULL,
            EntityType.WITHER,
            EntityType.GHAST,
            EntityType.END_CRYSTAL,
            EntityType.WIND_CHARGE
    );

    public ClaimProtectionListener(CityManager cityManager) {
        this.cityManager = cityManager;
        this.claims      = new ClaimCache(cityManager);
    }

    public void setSiegeAttacker(java.util.function.Predicate<Entity> siegeAttacker) {
        this.siegeAttacker = siegeAttacker;
    }

    /**
     * Annule TOUS les dégâts subis par un joueur dans un chunk claimé, sauf, pendant un siège (si
     * sieges.joueurs-blesses), ceux des assaillants : monstres du siège et soldats ennemis.
     * Couvre : PvE, eau, lave, chute, feu, explosion, poison, etc.
     * Les PNJ Citizens de type joueur (métadonnée « NPC ») ne sont pas concernés : leur
     * protection est réglée par Citizens, et les soldats de la garde doivent pouvoir être blessés.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof Player) || entity.hasMetadata("NPC")) return;
        if (siegeAttacker != null) {
            Entity source = event instanceof EntityDamageByEntityEvent byEntity ? byEntity.getDamager()
                    : com.citycore.inhabitant.NpcCombat.striking();
            if (source instanceof Projectile p && p.getShooter() instanceof Entity shooter) source = shooter;
            if (source != null && siegeAttacker.test(source)) return;
        }

        Chunk chunk = entity.getLocation().getChunk();
        if (cityManager.isChunkClaimed(chunk)) {
            event.setCancelled(true);
        }
    }

    /**
     * Empêche toute entité explosive de déclencher son explosion dans un chunk claimé.
     * Fonctionne pour Creeper, TNT, Fireball, Wither, End Crystal, etc.
     * L'entité est supprimée proprement (remove) sans exploser.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExplosionPrime(ExplosionPrimeEvent event) {
        Entity entity = event.getEntity();
        if (!EXPLOSIVE_TYPES.contains(entity.getType())) return;

        Chunk chunk = entity.getLocation().getChunk();
        if (cityManager.isChunkClaimed(chunk)) {
            event.setCancelled(true);
            entity.remove(); // supprime l'entité sans laisser de trace
        }
    }

    /**
     * Supprime tous les dommages de blocs causés par une explosion
     * si celle-ci touche un chunk claimé (sécurité supplémentaire).
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        // Retire de la liste les blocs situés dans des chunks claimés
        event.blockList().removeIf(block ->
                cityManager.isChunkClaimed(block.getChunk())
        );

        // Si l'explosion elle-même est dans un chunk claimé → annule tout
        if (cityManager.isChunkClaimed(event.getLocation().getChunk())) {
            event.setCancelled(true);
            event.getEntity().remove();
        }
    }

    /* =========================
       PROTECTION RENFORCÉE (phase 4 du plan « Valmont vivante ») : aucun bloc de la ville n'est
       touché par les monstres, le feu ou un piétinement, même pendant un siège.
       ========================= */

    /**
     * Blocs pris ou modifiés par une créature hostile (enderman, ravageur, wither, poisson
     * d'argent, zombie sur les œufs de tortue…), un lapin (carottes) ou un projectile qui n'a pas
     * été tiré par un joueur. Les blocs qui tombent (sable, gravier), les moutons qui broutent et
     * les villageois qui cultivent ne sont pas concernés.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        Entity entity = event.getEntity();
        boolean hostile = entity instanceof Enemy || entity instanceof Rabbit
                || (entity instanceof Projectile p && !(p.getShooter() instanceof Player));
        boolean trampled = event.getBlock().getType() == Material.FARMLAND && !(entity instanceof Player);
        if ((hostile || trampled) && claims.isClaimed(event.getBlock().getLocation())) event.setCancelled(true);
    }

    /** Portes enfoncées par les zombies et les vindicateurs. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreakDoor(EntityBreakDoorEvent event) {
        if (claims.isClaimed(event.getBlock().getLocation())) event.setCancelled(true);
    }

    /** Cultures et œufs de tortue piétinés par un joueur (saut sur la terre labourée). */
    @EventHandler(priority = EventPriority.HIGH)
    public void onTrample(PlayerInteractEvent event) {
        if (event.getAction() != Action.PHYSICAL || event.getClickedBlock() == null) return;
        Material type = event.getClickedBlock().getType();
        if ((type == Material.FARMLAND || type == Material.TURTLE_EGG)
                && claims.isClaimed(event.getClickedBlock().getLocation())) {
            event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
            event.setCancelled(true);
        }
    }

    /** Piétinement par une créature (cultures, œufs de tortue). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityTrample(EntityInteractEvent event) {
        Material type = event.getBlock().getType();
        if ((type == Material.FARMLAND || type == Material.TURTLE_EGG) && claims.isClaimed(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    /**
     * Feu : seul un joueur peut allumer un feu dans la ville (briquet, boule de feu lancée à la
     * main…) ; propagation, lave, foudre, boules de feu des monstres et explosions n'allument rien.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent event) {
        if (event.getPlayer() != null) return;
        if (claims.isClaimed(event.getBlock().getLocation())) event.setCancelled(true);
    }

    /** Aucun bloc ne brûle dans la ville. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        if (claims.isClaimed(event.getBlock().getLocation())) event.setCancelled(true);
    }

    /** Le feu ne se propage pas dans la ville. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFireSpread(BlockSpreadEvent event) {
        if (event.getSource().getType() == Material.FIRE || event.getSource().getType() == Material.SOUL_FIRE) {
            if (claims.isClaimed(event.getBlock().getLocation())) event.setCancelled(true);
        }
    }

    /** Tableaux et cadres arrachés par une explosion, un monstre ou un projectile (pas par un joueur). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakEvent event) {
        if (event instanceof HangingBreakByEntityEvent byEntity) {
            Entity remover = byEntity.getRemover();
            if (remover instanceof Projectile p && p.getShooter() instanceof Entity shooter) remover = shooter;
            if (remover instanceof Player && !remover.hasMetadata("NPC")) return;
        } else if (event.getCause() != HangingBreakEvent.RemoveCause.EXPLOSION) {
            return; // bloc support retiré, etc. : comportement normal
        }
        if (claims.isClaimed(event.getEntity().getLocation())) event.setCancelled(true);
    }

    /** Porte-armures et cadres abîmés par autre chose qu'un joueur (monstre, explosion, flèche perdue). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDecorationDamage(EntityDamageEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof ArmorStand) && !(entity instanceof ItemFrame)) return;
        if (event instanceof EntityDamageByEntityEvent byEntity) {
            Entity damager = byEntity.getDamager();
            if (damager instanceof Projectile p && p.getShooter() instanceof Entity shooter) damager = shooter;
            if (damager instanceof Player && !damager.hasMetadata("NPC")) return;
        }
        if (claims.isClaimed(entity.getLocation())) event.setCancelled(true);
    }
}
