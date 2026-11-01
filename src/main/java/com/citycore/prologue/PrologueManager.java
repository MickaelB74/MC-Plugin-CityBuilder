package com.citycore.prologue;

import com.citycore.city.CityManager;
import com.citycore.command.CityCommand;
import com.citycore.command.CityPermissions;
import com.citycore.npc.CityNPC;
import com.citycore.npc.NPCManager;
import com.citycore.ui.UiHud;
import com.citycore.util.DatabaseManager;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Prologue : avant Valmont, la quête commune « Le dernier artefact de Valmont ».
 *
 * Un administrateur cache le coffre de départ (/city create starterchest) : il ne contient que la
 * Clé de la ville de Valmont et reste scellé jusqu'au lancement de l'aventure (/city start). La
 * quête est alors commune à tous les joueurs (barre de chaleur en haut de l'écran, panneau des
 * quêtes) et se termine dès qu'un joueur prend la clé. Un clic droit avec la clé sur un bloc, puis
 * un second pour confirmer, fonde la ville sur ce chunk (même effet que /city create) : la clé
 * disparaît et Alderic apparaît.
 *
 * Étapes : AUCUNE (rien de caché) → COFFRE → QUETE → CLE → FONDEE. L'état est rangé dans
 * city_state (clés prologue.*) : /city restart l'efface avec le reste de la partie. Chaque clé porte
 * l'identifiant de sa partie : celle d'une partie effacée n'ouvre plus rien.
 */
public class PrologueManager implements Listener {

    public enum Stage { AUCUNE, COFFRE, QUETE, CLE, FONDEE }

    public static final String QUEST_NAME = "Le dernier artefact de Valmont";
    private static final String KEY_NAME  = "§6Clé de la ville de Valmont";

    private static final String STATE_STAGE  = "prologue.etape";
    private static final String STATE_CHEST  = "prologue.coffre";
    private static final String STATE_GAME   = "prologue.partie";
    private static final String STATE_FINDER = "prologue.trouvee_par";
    private static final String STATE_BOOK   = "prologue.livre."; // + UUID : carnet déjà remis à ce joueur
    private static final int    BOOK_SLOT    = 8;                  // emplacement 9 de la barre

    private static final double MAX_DISTANCE = 500.0; // barre de chaleur : vide au-delà (comme les pistes des PNJ)
    private static final int    KEY_SLOT     = 13;    // milieu du coffre
    private static final long   CONFIRM_MS   = 15_000; // second clic droit de la fondation
    private static final long   DOUBLE_MS    = 400;    // même clic reçu deux fois : ignoré

    private final JavaPlugin      plugin;
    private final DatabaseManager db;
    private final CityManager     cityManager;
    private final NPCManager      npcManager;
    private final NamespacedKey   keyTag;

    /** Mondes où rien ne doit se fonder ni se cacher (monde des raids, recréé à chaque démarrage). */
    private Predicate<World> forbiddenWorld = w -> false;

    private Stage  stage;
    private Spot   chest;  // coffre de départ (null = aucun)
    private String gameId; // partie en cours : inscrit sur la clé
    private String finder; // joueur qui a trouvé la clé

    private final Map<UUID, BossBar>   bars    = new HashMap<>();
    private final Map<UUID, Pending>   pending = new HashMap<>(); // fondation en attente de confirmation
    private final java.util.Set<UUID>  bookGiven = new java.util.HashSet<>(); // carnet remis (une fois par partie)

    /** Emplacement d'un bloc, comparable sans charger son monde. */
    private record Spot(String world, int x, int y, int z) {
        static Spot of(Block b) {
            return new Spot(b.getWorld().getName(), b.getX(), b.getY(), b.getZ());
        }

        static Spot parse(String s) {
            if (s == null) return null;
            String[] p = s.split(";");
            if (p.length != 4) return null;
            try {
                return new Spot(p[0], Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]));
            } catch (NumberFormatException e) {
                return null;
            }
        }

        String serialize() {
            return world + ";" + x + ";" + y + ";" + z;
        }

        boolean is(Location l) {
            return l != null && l.getWorld() != null && l.getWorld().getName().equals(world)
                    && l.getBlockX() == x && l.getBlockY() == y && l.getBlockZ() == z;
        }

        boolean is(Block b) {
            return b != null && is(b.getLocation());
        }

        /** Le bloc, ou null si son monde n'est pas chargé. */
        Block block() {
            World w = Bukkit.getWorld(world);
            return w == null ? null : w.getBlockAt(x, y, z);
        }

        Location center() {
            World w = Bukkit.getWorld(world);
            return w == null ? null : new Location(w, x + 0.5, y + 0.5, z + 0.5);
        }

        boolean loaded() {
            World w = Bukkit.getWorld(world);
            return w != null && w.isChunkLoaded(x >> 4, z >> 4);
        }
    }

    /** Premier clic droit de la fondation : chunk visé et moment du clic. */
    private record Pending(String world, int cx, int cz, long at) {
        boolean same(Chunk c) {
            return c.getWorld().getName().equals(world) && c.getX() == cx && c.getZ() == cz;
        }
    }

    public PrologueManager(JavaPlugin plugin, DatabaseManager db, CityManager cityManager, NPCManager npcManager) {
        this.plugin      = plugin;
        this.db          = db;
        this.cityManager = cityManager;
        this.npcManager  = npcManager;
        this.keyTag      = new NamespacedKey(plugin, "cle_valmont");
        try {
            stage = Stage.valueOf(state(STATE_STAGE, Stage.AUCUNE.name()));
        } catch (IllegalArgumentException e) {
            stage = Stage.AUCUNE;
        }
        chest  = Spot.parse(state(STATE_CHEST, null));
        gameId = state(STATE_GAME, null);
        finder = state(STATE_FINDER, "un joueur");
        try (PreparedStatement ps = db.getConnection().prepareStatement("SELECT key FROM city_state WHERE key LIKE ?")) {
            ps.setString(1, STATE_BOOK + "%");
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    try {
                        bookGiven.add(UUID.fromString(rs.getString("key").substring(STATE_BOOK.length())));
                    } catch (IllegalArgumentException ignored) {
                        // clé mal formée : ignorée
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void setForbiddenWorld(Predicate<World> forbiddenWorld) {
        this.forbiddenWorld = forbiddenWorld;
    }

    public Stage stage() {
        return stage;
    }

    /** Barre de chaleur des joueurs pendant la quête, revue chaque seconde. */
    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    /* =========================
       COFFRE DE DÉPART (/city create starterchest)
       ========================= */

    /**
     * Cache le coffre de départ : coffre visé (vide), sinon posé sur le bloc visé, face au poseur.
     * Un coffre déjà caché ailleurs est retiré (il ne contient que la clé).
     */
    public void placeChest(Player admin) {
        if (cityManager.isCityInitialized()) {
            admin.sendMessage("§c❌ Valmont est déjà fondée. §7Pour recommencer l'aventure : §f/city restart");
            return;
        }
        if (stage == Stage.CLE) {
            admin.sendMessage("§c❌ La clé a déjà été trouvée par §f" + finder + "§c : elle attend d'être posée.");
            admin.sendMessage("§7Clé perdue ? Fondez la ville directement : §f/city create");
            return;
        }
        Block target = admin.getTargetBlockExact(6);
        if (target == null) {
            admin.sendMessage("§c❌ Visez le sol (6 blocs au plus) ou un coffre vide.");
            return;
        }
        Block spot;
        if (target.getType() == Material.CHEST) {
            spot = target; // coffre visé : il devient le coffre de départ
            if (target.getBlockData() instanceof org.bukkit.block.data.type.Chest data
                    && data.getType() != org.bukkit.block.data.type.Chest.Type.SINGLE) {
                admin.sendMessage("§c❌ Coffre double : visez un coffre simple, ou le sol.");
                return;
            }
            if (!(chest != null && chest.is(target)) && !((Chest) target.getState()).getBlockInventory().isEmpty()) {
                admin.sendMessage("§c❌ Ce coffre n'est pas vide : la clé doit y être seule.");
                return;
            }
        } else {
            // Herbe haute, neige… : le coffre prend sa place ; sinon il se pose dessus
            spot = target.isReplaceable() ? target : target.getRelative(BlockFace.UP);
            if (!spot.getType().isAir() && !spot.isReplaceable()) {
                admin.sendMessage("§c❌ Visez le sol, à un endroit dégagé (ou un coffre vide).");
                return;
            }
        }
        if (forbiddenWorld.test(spot.getWorld())) {
            admin.sendMessage("§c❌ Pas dans le monde des raids : il est recréé à chaque démarrage.");
            return;
        }

        // Ancien coffre ailleurs : retiré avec sa clé
        if (chest != null && !chest.is(spot)) removeChest();

        if (spot.getType() != Material.CHEST) {
            spot.setType(Material.CHEST, false);
            if (spot.getBlockData() instanceof org.bukkit.block.data.type.Chest data) {
                data.setFacing(admin.getFacing().getOppositeFace()); // ouverture tournée vers le poseur
                data.setType(org.bukkit.block.data.type.Chest.Type.SINGLE);
                spot.setBlockData(data, false);
            }
        }
        if (gameId == null) {
            gameId = UUID.randomUUID().toString();
            saveState(STATE_GAME, gameId);
        }
        Inventory inv = ((Chest) spot.getState()).getBlockInventory();
        inv.clear();
        inv.setItem(KEY_SLOT, keyItem());

        chest = Spot.of(spot);
        saveState(STATE_CHEST, chest.serialize());
        if (stage != Stage.QUETE) setStage(Stage.COFFRE); // déplacé pendant la quête : elle continue

        admin.sendMessage("§6🗝 Coffre de départ caché en §f" + spot.getX() + " " + spot.getY() + " " + spot.getZ()
                + " §7(" + spot.getWorld().getName() + ")§6, avec la §eClé de la ville de Valmont§6.");
        admin.sendMessage(stage == Stage.QUETE
                ? "§7La quête continue : la barre de chaleur des joueurs mène désormais ici."
                : "§7Il reste scellé (incassable) jusqu'au lancement de l'aventure : §f/city start");
    }

    /** Retire le coffre de départ du monde s'il garde encore la clé (déplacement, réinitialisation). */
    public void removeChest() {
        if (chest == null || (stage != Stage.COFFRE && stage != Stage.QUETE)) return;
        Block b = chest.block();
        if (b != null && b.getType() == Material.CHEST) {
            ((Chest) b.getState()).getBlockInventory().clear();
            b.setType(Material.AIR, false);
        }
    }

    private boolean isStarterChest(Block b) {
        return chest != null && chest.is(b);
    }

    private boolean isStarterInventory(Inventory inv) {
        return chest != null && inv != null && inv.getType() == org.bukkit.event.inventory.InventoryType.CHEST
                && chest.is(inv.getLocation());
    }

    /** Le coffre est-il encore celui de l'artefact (scellé ou gardant la clé) ? */
    private boolean guarded() {
        return chest != null && (stage == Stage.COFFRE || stage == Stage.QUETE);
    }

    /* =========================
       LANCEMENT (/city start)
       ========================= */

    /** Réservé aux administrateurs (citycore.admin.ville) et à la console. */
    public void begin(CommandSender sender) {
        if (stage == Stage.FONDEE || cityManager.isCityInitialized()) {
            sender.sendMessage("§c❌ Valmont est déjà fondée : l'aventure a commencé.");
            return;
        }
        if (stage == Stage.QUETE || stage == Stage.CLE) {
            sender.sendMessage("§e🗺 La quête « " + QUEST_NAME + " » est déjà en cours.");
            return;
        }
        if (stage != Stage.COFFRE || chest == null) {
            sender.sendMessage("§c❌ Le dernier artefact de Valmont n'a pas encore été caché.");
            sender.sendMessage("§7Cachez d'abord le coffre de départ : §f/city create starterchest");
            return;
        }
        // Coffre retiré à la main entre-temps (WorldEdit, mod…) : la quête serait impossible
        if (chest.loaded()) {
            Block b = chest.block();
            boolean keyInside = false;
            if (b != null && b.getType() == Material.CHEST) {
                for (ItemStack item : ((Chest) b.getState()).getBlockInventory().getContents()) {
                    if (isCurrentKey(item)) keyInside = true;
                }
            }
            if (!keyInside) {
                sender.sendMessage("§c❌ Le coffre de départ a disparu, ou la clé n'y est plus.");
                sender.sendMessage("§7Cachez-le de nouveau : §f/city create starterchest");
                return;
            }
        }
        setStage(Stage.QUETE);

        String by = sender instanceof Player p ? p.getName() : null;
        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage("§6📜 Il y a vingt hivers, §eValmont§6, capitale des Marches, a brûlé en une seule nuit : la Nuit Noire.");
        Bukkit.broadcastMessage("§7De la ville, il ne reste qu'un artefact : la §6Clé de la ville§7, cachée par ses derniers gardiens.");
        Bukkit.broadcastMessage("§7Retrouvez-la. Là où elle sera posée, Valmont renaîtra.");
        Bukkit.broadcastMessage("§e🗺 Quête commune : §f" + QUEST_NAME + " §8— la barre en haut de l'écran se remplit en approchant.");
        Bukkit.broadcastMessage("§7📖 Chacun reçoit le carnet §6Survivant de Valmont §7(emplacement 9 de la barre).");
        if (by != null) Bukkit.broadcastMessage("§8Aventure lancée par " + by + ".");
        Bukkit.broadcastMessage("");
        for (Player p : Bukkit.getOnlinePlayers()) {
            giveBook(p);
            p.sendTitle("§6Le dernier artefact", "§7Retrouvez la Clé de la ville de Valmont", 10, 70, 20);
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f); // même son que la clé trouvée
        }
    }

    /* =========================
       LE CARNET « SURVIVANT DE VALMONT »
       Remis au lancement (et à qui arrive pendant la quête), une fois par joueur et par partie :
       il met les explorateurs en jambe et les mène à la quête de la clé. Sans autre usage, il peut
       être jeté ; il n'est pas redonné.
       ========================= */

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private static final String[] BOOK_PAGES = {
            "§6§lSurvivant\nde Valmont§r\n\n§8Carnet d'expédition\n\n\n\n§0Lisez-le avant de partir.",
            "Vous êtes un groupe d'explorateurs partis sur les traces d'une ancienne cité détruite : §6Valmont§0, jadis"
                    + " capitale des Marches.",
            "Les vieilles cartes la placent quelque part dans ces terres sauvages. Personne n'y est revenu depuis"
                    + " vingt hivers.",
            "Car en une seule nuit, Valmont a brûlé. Les anciens l'appellent la §4Nuit Noire§0 : une Porte ouverte vers"
                    + " le Néant, et un dragon noir venu de l'autre côté.",
            "Les survivants se sont dispersés dans les bois, les plaines et les montagnes. Ils n'attendent qu'un signe"
                    + " pour revenir.",
            "De la cité, il ne reste qu'un artefact : la §6Clé de la ville de Valmont§0, cachée dans un coffre par ses"
                    + " derniers gardiens.",
            "La légende dit que là où la Clé sera posée, Valmont renaîtra de ses cendres.",
            "§lVotre mission§r\n\n1. Retrouvez le coffre et la Clé. La barre en haut de l'écran se remplit à mesure"
                    + " que vous approchez.",
            "2. Choisissez ensemble l'endroit où rebâtir, puis faites un clic droit sur le sol avec la Clé.\n\n"
                    + "3. Alderic, le dernier intendant de Valmont, viendra à vous.",
            "Bonne route, explorateurs.\n\nValmont compte sur vous."
    };

    private static ItemStack survivorBook() {
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        org.bukkit.inventory.meta.BookMeta meta = (org.bukkit.inventory.meta.BookMeta) book.getItemMeta();
        meta.setTitle("Survivant de Valmont");
        meta.setAuthor("Un ancien de Valmont");
        meta.setGeneration(org.bukkit.inventory.meta.BookMeta.Generation.ORIGINAL);
        for (String page : BOOK_PAGES) meta.addPages(LEGACY.deserialize(page));
        book.setItemMeta(meta);
        return book;
    }

    /**
     * Carnet à l'emplacement 9 de la barre, une fois par partie. Un objet déjà là passe dans une
     * case libre de l'inventaire ; inventaire plein : le carnet tombe aux pieds du joueur (rien
     * de ce qu'il porte n'est jeté).
     */
    private void giveBook(Player p) {
        if (!bookGiven.add(p.getUniqueId())) return;
        saveState(STATE_BOOK + p.getUniqueId(), "1");
        ItemStack book = survivorBook();
        org.bukkit.inventory.PlayerInventory inv = p.getInventory();
        ItemStack current = inv.getItem(BOOK_SLOT);
        if (current != null && !current.getType().isAir()) {
            int free = inv.firstEmpty();
            if (free < 0) {
                p.getWorld().dropItem(p.getLocation(), book);
                p.sendMessage("§7📖 Inventaire plein : le carnet §6Survivant de Valmont §7est à vos pieds.");
                return;
            }
            inv.setItem(free, current);
        }
        inv.setItem(BOOK_SLOT, book);
    }

    /* =========================
       LA CLÉ
       ========================= */

    private ItemStack keyItem() {
        ItemStack key = new ItemStack(Material.TRIAL_KEY);
        ItemMeta meta = key.getItemMeta();
        meta.setDisplayName(KEY_NAME);
        meta.setLore(List.of(
                "§7Le dernier artefact de Valmont,",
                "§7sauvé des cendres de la Nuit Noire.",
                "",
                "§eClic droit sur le sol §7: Valmont",
                "§7renaîtra sur ce chunk."));
        meta.setEnchantmentGlintOverride(true);
        meta.getPersistentDataContainer().set(keyTag, PersistentDataType.STRING, gameId);
        key.setItemMeta(meta);
        return key;
    }

    /** Clé de Valmont, de cette partie ou d'une partie effacée. */
    private boolean isKey(ItemStack item) {
        return item != null && item.getType() == Material.TRIAL_KEY && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(keyTag, PersistentDataType.STRING);
    }

    private boolean isCurrentKey(ItemStack item) {
        return isKey(item) && gameId != null
                && gameId.equals(item.getItemMeta().getPersistentDataContainer().get(keyTag, PersistentDataType.STRING));
    }

    private boolean holdsKey(Player p) {
        for (ItemStack item : p.getInventory().getContents()) {
            if (isCurrentKey(item)) return true;
        }
        return false;
    }

    /** La clé quitte le coffre : la quête est terminée, la fondation attend son porteur. */
    private void takeKey(Player player, ItemStack key) {
        player.getInventory().addItem(key).values()
                .forEach(rest -> player.getWorld().dropItem(player.getLocation(), rest)); // inventaire plein
        finder = player.getName();
        saveState(STATE_FINDER, finder);
        setStage(Stage.CLE);
        clearBars();
        Bukkit.getScheduler().runTask(plugin, () -> player.closeInventory());

        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage("§a✔ Quête terminée : §f" + QUEST_NAME);
        Bukkit.broadcastMessage("§6🗝 §e" + finder + " §6a retrouvé la Clé de la ville de Valmont !");
        Bukkit.broadcastMessage("§7Choisissez ensemble où Valmont renaîtra : §fclic droit avec la clé sur le sol§7.");
        Bukkit.broadcastMessage("");
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        }
    }

    /* =========================
       FONDATION
       ========================= */

    /**
     * Fait renaître Valmont : ville créée, chunk revendiqué, Alderic à mayorAt (sinon devant le
     * fondateur). Effet commun à /city create et à la clé ; le prologue est terminé.
     */
    public void found(Chunk chunk, Location mayorAt, Player founder) {
        cityManager.initializeCity(CityCommand.CITY_NAME);
        cityManager.claimChunk(chunk);
        if (mayorAt != null) npcManager.spawnNPC(CityNPC.MAYOR, mayorAt);
        else npcManager.spawnMayor(founder);
        setStage(Stage.FONDEE);
        clearBars();
        pending.clear();
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onUseKey(PlayerInteractEvent event) {
        ItemStack item = event.getItem();
        if (!isKey(item)) return;
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_BLOCK && action != Action.RIGHT_CLICK_AIR) return;
        // Pas d'usage ordinaire de la clé (coffre-fort des épreuves) ni du bloc cliqué
        event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.DENY);
        Player player = event.getPlayer();
        EquipmentSlot hand = event.getHand() == null ? EquipmentSlot.HAND : event.getHand();

        if (!isCurrentKey(item)) {
            player.getInventory().setItem(hand, null);
            player.sendMessage("§7🗝 La clé tombe en poussière : elle appartenait à une Valmont oubliée.");
            return;
        }
        if (stage == Stage.FONDEE || cityManager.isCityInitialized()) {
            player.sendMessage("§7🗝 Valmont est déjà fondée : la clé n'ouvre plus que des souvenirs.");
            return;
        }
        if (action == Action.RIGHT_CLICK_AIR || event.getClickedBlock() == null) {
            com.citycore.util.TypewriterUtil.actionBar(player, "§6🗝 Visez le sol, là où Valmont doit renaître.");
            return;
        }
        Block block = event.getClickedBlock();
        World world = block.getWorld();
        if (world.getEnvironment() != World.Environment.NORMAL || forbiddenWorld.test(world)) {
            player.sendMessage("§c❌ Valmont ne peut renaître qu'à la surface du monde.");
            return;
        }

        Chunk chunk = block.getChunk();
        long now = System.currentTimeMillis();
        Pending first = pending.get(player.getUniqueId());
        if (first == null || !first.same(chunk) || now - first.at() > CONFIRM_MS) {
            pending.put(player.getUniqueId(), new Pending(world.getName(), chunk.getX(), chunk.getZ(), now));
            int bx = chunk.getX() << 4, bz = chunk.getZ() << 4;
            player.sendMessage("§6🗝 Faire renaître Valmont ici ? §7La ville commencera sur ce chunk §8(x "
                    + bx + "→" + (bx + 15) + ", z " + bz + "→" + (bz + 15) + ")§7.");
            player.sendMessage("§7Pour confirmer : §fclic droit de nouveau §7avec la clé, sur ce chunk, dans les 15 s.");
            return;
        }
        if (now - first.at() < DOUBLE_MS) return; // même clic reçu deux fois
        pending.remove(player.getUniqueId());

        player.getInventory().setItem(hand, null); // la clé s'enfonce dans le sol
        found(chunk, mayorSpot(block, event.getBlockFace(), player), player);

        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage("§6🏰 §e" + player.getName() + " §6a posé la Clé de la ville : §eValmont §6renaît de ses cendres !");
        Bukkit.broadcastMessage("§7👴 " + CityNPC.MAYOR.displayName + "§7, dernier intendant de Valmont, attend les bâtisseurs auprès de la clé.");
        Bukkit.broadcastMessage("");
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendTitle("§6Valmont renaît", "§7de ses cendres", 10, 70, 20);
            p.playSound(p.getLocation(), Sound.BLOCK_BELL_RESONATE, 1f, 1f);
        }
        plugin.getLogger().info("Valmont fondée avec la clé par " + player.getName() + " (chunk "
                + chunk.getX() + ", " + chunk.getZ() + " de " + world.getName() + ").");
    }

    /** Alderic se tient sur le bloc cliqué (dessus dégagé, à deux pas au moins), face au fondateur. */
    private static Location mayorSpot(Block clicked, BlockFace face, Player player) {
        if (face != BlockFace.UP) return null;
        Block feet = clicked.getRelative(BlockFace.UP);
        if (!feet.isPassable() || !feet.getRelative(BlockFace.UP).isPassable()) return null;
        Location at = feet.getLocation().add(0.5, 0, 0.5);
        if (at.distance(player.getLocation()) < 1.5) return null;
        at.setDirection(player.getLocation().toVector().subtract(at.toVector()));
        at.setPitch(0f);
        return at;
    }

    /* =========================
       PROTECTIONS DU COFFRE ET DE LA CLÉ
       ========================= */

    @EventHandler(ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent event) {
        if (stage != Stage.COFFRE || !isStarterInventory(event.getInventory())) return;
        event.setCancelled(true);
        event.getPlayer().sendMessage("§7🔒 Le coffre est scellé par une magie ancienne : l'aventure n'a pas commencé.");
        if (event.getPlayer().hasPermission(CityPermissions.ADMIN_VILLE)) {
            event.getPlayer().sendMessage("§7Quand tout le monde est prêt : §f/city start");
        }
    }

    /** Pendant la quête, on ne peut que prendre la clé : rien n'entre dans le coffre. */
    @EventHandler(ignoreCancelled = true)
    public void onChestClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (stage != Stage.QUETE || !isStarterInventory(top)) return;
        boolean inChest = event.getRawSlot() >= 0 && event.getRawSlot() < top.getSize();
        if (!inChest) {
            if (event.isShiftClick() || event.getAction() == InventoryAction.COLLECT_TO_CURSOR) event.setCancelled(true);
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        ItemStack current = event.getCurrentItem();
        if (!isCurrentKey(current)) return;
        top.setItem(event.getSlot(), null);
        takeKey(player, current);
    }

    @EventHandler(ignoreCancelled = true)
    public void onChestDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (stage != Stage.QUETE || !isStarterInventory(top)) return;
        if (event.getRawSlots().stream().anyMatch(slot -> slot < top.getSize())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onHopper(InventoryMoveItemEvent event) {
        if (guarded() && (isStarterInventory(event.getSource()) || isStarterInventory(event.getDestination()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (!guarded() || !isStarterChest(event.getBlock())) return;
        event.setCancelled(true);
        event.getPlayer().sendMessage("§c🔒 Ce coffre garde le dernier artefact de Valmont : il ne peut pas être détruit.");
        if (event.getPlayer().hasPermission(CityPermissions.ADMIN_VILLE)) {
            event.getPlayer().sendMessage("§7Pour le déplacer : §f/city create starterchest §7à un autre endroit.");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (guarded()) event.blockList().removeIf(this::isStarterChest);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (guarded()) event.blockList().removeIf(this::isStarterChest);
    }

    /** Un coffre collé au coffre de l'artefact en ferait un coffre double. */
    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (!guarded() || event.getBlockPlaced().getType() != Material.CHEST) return;
        for (BlockFace face : List.of(BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST)) {
            if (isStarterChest(event.getBlockPlaced().getRelative(face))) {
                event.setCancelled(true);
                event.getPlayer().sendMessage("§7Un coffre ne peut pas être collé au coffre de l'artefact.");
                return;
            }
        }
    }

    /** Clé jetée ou perdue à la mort : ni feu, ni lave, ni disparition au bout de 5 minutes. */
    @EventHandler(ignoreCancelled = true)
    public void onKeyDropped(ItemSpawnEvent event) {
        if (!isKey(event.getEntity().getItemStack())) return;
        event.getEntity().setInvulnerable(true);
        event.getEntity().setUnlimitedLifetime(true);
    }

    /* =========================
       SUIVI : BARRE DE CHALEUR, PANNEAU DES QUÊTES, CONNEXIONS
       ========================= */

    private void tick() {
        if (stage != Stage.QUETE || chest == null) {
            clearBars();
            return;
        }
        Location target = chest.center();
        for (Player p : Bukkit.getOnlinePlayers()) {
            BossBar bar = bars.computeIfAbsent(p.getUniqueId(), id -> {
                BossBar b = Bukkit.createBossBar("§e🗝 " + QUEST_NAME, BarColor.RED, BarStyle.SOLID);
                b.addPlayer(p);
                return b;
            });
            // Autre monde (Nether, End) : distance non mesurable, piste « froide »
            if (target == null || !p.getWorld().equals(target.getWorld())) {
                bar.setProgress(0.005);
                bar.setColor(BarColor.RED);
                continue;
            }
            double dist = p.getLocation().distance(target);
            bar.setProgress(Math.max(0.005, Math.min(1.0, 1.0 - dist / MAX_DISTANCE)));
            bar.setColor(dist < 20 ? BarColor.GREEN : dist < 100 ? BarColor.YELLOW : BarColor.RED);
        }
    }

    private void clearBars() {
        bars.values().forEach(BossBar::removeAll);
        bars.clear();
    }

    /** « Froid » … « Brûlant » selon la distance au coffre. */
    private String heat(Player p) {
        Location target = chest == null ? null : chest.center();
        if (target == null || !p.getWorld().equals(target.getWorld())) return "Froid";
        double dist = p.getLocation().distance(target);
        return dist < 20 ? "Brûlant" : dist < 60 ? "Chaud" : dist < 150 ? "Tiède" : "Froid";
    }

    /** Panneau illustré des quêtes : le prologue en tête, vide une fois la ville fondée. */
    public List<UiHud.Row> hudRows(Player p) {
        List<UiHud.Row> rows = new ArrayList<>();
        if (stage == Stage.QUETE) {
            rows.add(UiHud.Row.head("Le dernier artefact", UiHud.GOLD));
            String heat = heat(p);
            rows.add(UiHud.Row.value("• Trouver la clé", heat, heat.equals("Froid") ? UiHud.CREAM : UiHud.GOLD));
        } else if (stage == Stage.CLE) {
            rows.add(UiHud.Row.head("La clé de Valmont", UiHud.GOLD));
            if (holdsKey(p)) {
                rows.add(UiHud.Row.plain("★ Fonder Valmont !", UiHud.GREEN));
                rows.add(UiHud.Row.plain("Clic droit au sol", UiHud.CREAM));
            } else {
                rows.add(UiHud.Row.value("✔ Trouvée par", finder, UiHud.GREEN));
            }
        }
        return rows;
    }

    /** Lignes du tableau de droite (sans pack), même contenu. */
    public List<String> hudLines(Player p) {
        List<String> lines = new ArrayList<>();
        if (stage == Stage.QUETE) {
            lines.add("§6🗝 Le dernier artefact");
            lines.add("§7• §fTrouver la clé §7(" + heat(p) + ")");
            lines.add("§r ");
        } else if (stage == Stage.CLE) {
            lines.add("§6🗝 La clé de Valmont");
            lines.add(holdsKey(p) ? "§a★ §fFonder Valmont : clic droit au sol" : "§a✔ §fTrouvée par " + finder);
            lines.add("§r ");
        }
        return lines;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            switch (stage) {
                case COFFRE -> player.sendMessage("§6📜 Le dernier artefact de Valmont attend d'être retrouvé."
                        + (player.hasPermission(CityPermissions.ADMIN_VILLE)
                                ? " §7Quand tout le monde est prêt : §f/city start"
                                : " §7L'aventure commencera bientôt."));
                case QUETE -> {
                    player.sendMessage("§e🗺 Quête en cours : §f" + QUEST_NAME
                            + " §7— retrouvez la Clé de la ville (barre en haut de l'écran).");
                    giveBook(player); // arrivé après le lancement : son carnet aussi
                }
                case CLE -> {
                    player.sendMessage("§6🗝 §e" + finder + " §6a retrouvé la Clé de la ville :"
                            + " §7Valmont attend d'être fondée (clic droit avec la clé sur le sol).");
                    giveBook(player);
                }
                case AUCUNE -> {
                    if (player.hasPermission(CityPermissions.ADMIN_VILLE) && !cityManager.isCityInitialized()) {
                        player.sendMessage("§7[CityCore] Nouvelle partie : cachez le dernier artefact avec"
                                + " §f/city create starterchest§7, puis lancez l'aventure avec §f/city start§7.");
                    }
                }
                default -> { }
            }
        }, 60L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        BossBar bar = bars.remove(event.getPlayer().getUniqueId());
        if (bar != null) bar.removeAll();
        pending.remove(event.getPlayer().getUniqueId());
    }

    /* =========================
       ÉTAT ENREGISTRÉ (table city_state, clés prologue.*)
       ========================= */

    private void setStage(Stage next) {
        stage = next;
        saveState(STATE_STAGE, next.name());
    }

    private String state(String key, String def) {
        try (PreparedStatement ps = db.getConnection().prepareStatement("SELECT value FROM city_state WHERE key = ?")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString("value") : def;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return def;
        }
    }

    private void saveState(String key, String value) {
        try (PreparedStatement ps = db.getConnection().prepareStatement(
                "INSERT INTO city_state (key, value) VALUES (?, ?) ON CONFLICT(key) DO UPDATE SET value = excluded.value")) {
            ps.setString(1, key);
            ps.setString(2, value);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
