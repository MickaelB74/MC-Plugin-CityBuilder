package com.citycore.economy;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Crafter;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.type.Sapling;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockCookEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.CrafterCraftEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.SmithingInventory;
import org.bukkit.inventory.StonecutterInventory;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

/**
 * Ce qu'on fabrique avec des objets marqués non revendables est marqué à son tour.
 *
 * L'entrepôt des artisans se vide gratuitement (et leurs employés le remplissent sans rien
 * coûter) : sans ce garde-fou, blé de la réserve → pain → vente au PNJ créerait des pièces à
 * partir de rien. Couvre l'établi et l'inventaire, le crafteur, les fours (fourneau, fumoir,
 * haut fourneau, feu de camp), la table de forgeron, le tailleur de pierre, et les blocs posés
 * puis cassés (suivis dans leur chunk, y compris poussés par un piston ; ils résistent aux
 * explosions). Ce qui pousse (graines, pousses) n'est pas suivi : cultiver reste du travail.
 */
public final class TagPropagation implements Listener {

    private final JavaPlugin    plugin;
    private final NamespacedKey blocksKey;

    public TagPropagation(JavaPlugin plugin) {
        this.plugin    = plugin;
        this.blocksKey = new NamespacedKey(plugin, "reserve_blocks");
    }

    /** Établi et grille de l'inventaire : le résultat affiché est déjà marqué. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        ItemStack result = event.getInventory().getResult();
        if (result == null || result.getType().isAir()) return;
        for (ItemStack in : event.getInventory().getMatrix()) {
            if (ItemTags.isTagged(in)) {
                event.getInventory().setResult(derived(result));
                return;
            }
        }
    }

    /** Crafteur (bloc automatique). */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCrafter(CrafterCraftEvent event) {
        if (!(event.getBlock().getState(false) instanceof Crafter crafter)) return;
        for (ItemStack in : crafter.getInventory().getContents()) {
            if (ItemTags.isTagged(in)) {
                event.setResult(derived(event.getResult()));
                return;
            }
        }
    }

    /** Fourneau, fumoir, haut fourneau, feu de camp. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCook(BlockCookEvent event) {
        if (ItemTags.isTagged(event.getSource())) event.setResult(derived(event.getResult()));
    }

    /** Table de forgeron. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSmithing(PrepareSmithingEvent event) {
        ItemStack result = event.getResult();
        if (result == null || result.getType().isAir()) return;
        SmithingInventory inv = event.getInventory();
        if (ItemTags.isTagged(inv.getInputTemplate()) || ItemTags.isTagged(inv.getInputEquipment())
                || ItemTags.isTagged(inv.getInputMineral())) {
            event.setResult(derived(result));
        }
    }

    /**
     * Tailleur de pierre : il n'a pas d'événement « résultat » ; le résultat est marqué au moment de
     * le prendre. Maj + clic (tout tailler d'un coup) recrée chaque résultat sans passer par ici :
     * il est refusé pour une pierre de la réserve.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onStonecutter(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory() instanceof StonecutterInventory inv)) return;
        if (event.getRawSlot() != 1 || !ItemTags.isTagged(inv.getInputItem())) return;
        ItemStack result = event.getCurrentItem();
        if (result == null || result.getType().isAir()) return;
        if (event.isShiftClick()) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player p) {
                p.sendMessage("§7Matière de la réserve de la ville : taillez-la un résultat à la fois.");
            }
            return;
        }
        event.setCurrentItem(derived(result));
    }

    private static ItemStack derived(ItemStack result) {
        ItemStack copy = result.clone();
        ItemTags.tagDerived(copy);
        return copy;
    }

    /* =========================
       BLOCS POSÉS
       ========================= */

    // Un bloc posé perd les données de l'objet : sans suivi, poser puis casser un bloc de diamant de
    // la réserve rendrait un bloc vendable. Les blocs posés depuis un objet marqué sont notés dans
    // leur chunk (positions compactées) ; ce qu'ils donnent en cassant reste marqué.

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (!ItemTags.isTagged(event.getItemInHand())) return;
        Block placed = event.getBlockPlaced();
        // Plantations (graines, pousses, canne à sucre…) : ce qu'elles donnent est du travail
        if (placed.getBlockData() instanceof Ageable || placed.getBlockData() instanceof Sapling) return;
        track(placed, true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrop(BlockDropItemEvent event) {
        Block block = event.getBlock();
        if (!tracked(block)) return;
        for (Item item : event.getItems()) {
            ItemStack stack = item.getItemStack();
            ItemTags.tagDerived(stack);
            item.setItemStack(stack);
        }
        track(block, false);
    }

    /** Bloc cassé sans rien lâcher (créatif, outil inadapté) : la position est oubliée. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (tracked(block)) Bukkit.getScheduler().runTask(plugin, () -> track(block, false));
    }

    /** Explosions : les blocs de la réserve y résistent (sinon, une explosion les rendrait vendables). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(this::tracked);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(this::tracked);
    }

    /** Pistons : la marque suit le bloc déplacé. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        move(event.getBlocks(), event.getDirection());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        move(event.getBlocks(), event.getDirection());
    }

    private void move(List<Block> blocks, BlockFace direction) {
        List<Block> moved = new ArrayList<>();
        for (Block b : blocks) if (tracked(b)) moved.add(b);
        if (moved.isEmpty()) return;
        for (Block b : moved) track(b, false);
        for (Block b : moved) track(b.getRelative(direction), true);
    }

    /** Position compactée dans le chunk : x, z (4 bits chacun) et y décalé. */
    private static long pack(Block b) {
        return ((long) (b.getY() + 4096) << 8) | ((b.getZ() & 15L) << 4) | (b.getX() & 15L);
    }

    private boolean tracked(Block b) {
        long[] list = b.getChunk().getPersistentDataContainer().get(blocksKey, PersistentDataType.LONG_ARRAY);
        if (list == null) return false;
        long p = pack(b);
        for (long v : list) if (v == p) return true;
        return false;
    }

    private void track(Block b, boolean on) {
        PersistentDataContainer pdc = b.getChunk().getPersistentDataContainer();
        long[] list = pdc.get(blocksKey, PersistentDataType.LONG_ARRAY);
        long p = pack(b);
        List<Long> values = new ArrayList<>();
        if (list != null) for (long v : list) if (v != p) values.add(v);
        if (on) values.add(p);
        if (values.isEmpty()) {
            pdc.remove(blocksKey);
        } else {
            long[] out = new long[values.size()];
            for (int i = 0; i < out.length; i++) out[i] = values.get(i);
            pdc.set(blocksKey, PersistentDataType.LONG_ARRAY, out);
        }
    }
}
