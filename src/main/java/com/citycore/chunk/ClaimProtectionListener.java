package com.citycore.chunk;

import com.citycore.city.CityManager;
import org.bukkit.Chunk;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;

import java.util.Set;

public class ClaimProtectionListener implements Listener {

    private final CityManager cityManager;

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
    }

    /**
     * Annule TOUS les dégâts subis par un joueur dans un chunk claimé.
     * Couvre : PvE, eau, lave, chute, feu, explosion, poison, etc.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof Player)) return;

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
}