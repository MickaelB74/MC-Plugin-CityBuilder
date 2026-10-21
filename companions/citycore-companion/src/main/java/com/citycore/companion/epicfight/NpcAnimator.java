package com.citycore.companion.epicfight;

import com.citycore.companion.CityCoreCompanion;
import com.citycore.companion.Settings;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import yesman.epicfight.api.animation.Animator;
import yesman.epicfight.api.animation.types.StaticAnimation;
import yesman.epicfight.api.asset.AssetAccessor;
import yesman.epicfight.gameasset.Animations;
import yesman.epicfight.network.EpicFightNetworkManager;
import yesman.epicfight.network.common.AbstractAnimatorControl;
import yesman.epicfight.network.server.SPAnimatorControl;
import yesman.epicfight.network.server.SPChangeLivingMotion;
import yesman.epicfight.network.server.SPChangePlayerMode;
import yesman.epicfight.network.server.SPUpdatePlayerInput;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;
import yesman.epicfight.world.capabilities.entitypatch.player.PlayerPatch;
import yesman.epicfight.world.capabilities.entitypatch.player.ServerPlayerPatch;
import yesman.epicfight.world.capabilities.item.CapabilityItem;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Anime les PNJ (entités « joueur » de Citizens) pour Epic Fight, côté serveur :
 * <ul>
 *   <li>déplacements : à chaque tick, le déplacement réel est traduit en « touches » (avancer,
 *       reculer, pas de côté) et envoyé aux joueurs qui voient le PNJ, seulement quand il change ;
 *       au-delà d'une vitesse, le PNJ est marqué « en course » (animation de course) ;</li>
 *   <li>arrivée d'un joueur près d'un PNJ : il reçoit aussitôt le mode combat et l'état actuel ;</li>
 *   <li>gestes : le plugin pose une étiquette sur le PNJ (« citycore.geste.attaque » ou
 *       « citycore.geste.tir ») ; le mod la retire et joue l'enchaînement de l'arme tenue ou son
 *       animation de tir. Affichage seulement : les dégâts restent ceux du plugin. Une étiquette
 *       déjà posée quand le PNJ est vu pour la première fois (restée à l'arrêt du serveur, remise
 *       par Citizens à sa réapparition) est retirée sans être jouée ;</li>
 *   <li>remise à zéro : peu après l'arrivée d'un joueur, puis pour un PNJ immobile et sans geste
 *       depuis un moment, ses animations sont recalculées chez les joueurs qui le voient (même
 *       paquet qu'Epic Fight à l'arrivée d'un joueur). Chez Epic Fight, tout balancement de bras
 *       lance une animation de frappe (« creuser ») qui pouvait rester bloquée en boucle, surtout
 *       à la connexion après un redémarrage : elle s'arrête ainsi.</li>
 * </ul>
 * Un PNJ = une entité joueur du serveur absente de la liste des joueurs connectés.
 */
final class NpcAnimator {

    /** Étiquettes posées par le plugin CityCore (voir NpcGestures dans le plugin). */
    static final String TAG_ATTACK = "citycore.geste.attaque";
    static final String TAG_SHOT   = "citycore.geste.tir";

    /** Recherche des PNJ dans les mondes : toutes les secondes. */
    private static final int SCAN_TICKS = 20;
    /** Ticks sous la vitesse de marche avant de considérer le PNJ arrêté (évite les à-coups). */
    private static final int STOP_TICKS = 4;
    /** Ticks rapides avant de passer en course, ticks lents avant d'en sortir. */
    private static final int RUN_IN_TICKS = 3, RUN_OUT_TICKS = 6;
    /** Envoi de l'état à un joueur qui arrive : quelques ticks après l'apparition du PNJ chez lui. */
    private static final int SYNC_DELAY = 3;
    /**
     * Remise à zéro chez ce joueur : après le balancement de bras que Citizens envoie à l'apparition
     * d'un PNJ (un tick plus tard, 6 ticks de geste).
     */
    private static final int RESET_DELAY = 12;
    /** Sans coup pendant 2 s, l'enchaînement repart du premier coup. */
    private static final int COMBO_RESET_TICKS = 40;
    /** Plus de 4 blocs en un tick : téléportation, pas un déplacement. */
    private static final double TELEPORT = 4.0;
    /** Indice du drapeau « sprint » des entités (Entity.FLAG_SPRINTING). */
    private static final int FLAG_SPRINTING = 3;

    /** État d'un PNJ suivi. */
    private static final class NpcState {
        final ServerPlayer npc;
        double lastX, lastZ;
        boolean moving, running, sprintedOnce;
        int stillTicks, fastTicks, slowTicks;
        float forward, strafe;      // dernières « touches » envoyées
        int combo, lastAttackTick = Integer.MIN_VALUE / 2;
        int lastGestureTick, lastResetTick; // dernier geste joué, dernière remise à zéro

        NpcState(ServerPlayer npc, int tick) {
            this.npc = npc;
            this.lastX = npc.getX();
            this.lastZ = npc.getZ();
            this.lastGestureTick = tick;
            this.lastResetTick = tick;
        }
    }

    /**
     * Envoi différé à un joueur qui vient de voir apparaître un PNJ : état (mode combat, « touches »),
     * ou remise à zéro de ses animations (reset).
     */
    private record PendingSync(UUID viewer, int npcId, int due, boolean reset) {}

    private final Map<Integer, NpcState> npcs = new HashMap<>();
    private final List<PendingSync> pending = new ArrayList<>();
    private int tick;
    private int lastLoggedCount = -1;
    /** Entity.setSharedFlag (protégée) : drapeau de sprint sans le bonus de vitesse de setSprinting. */
    private Method setSharedFlag;
    private boolean sprintUnavailable;
    /** Sortes d'erreurs déjà signalées (une ligne de journal chacune). */
    private final java.util.Set<String> warned = new java.util.HashSet<>();

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        tick++;
        // Module éteint dans les réglages (epicfight.actif) : les PNJ suivis s'arrêtent, plus rien n'est envoyé
        if (!Settings.EF_ENABLED.get()) {
            if (!npcs.isEmpty()) stopAll();
            return;
        }
        if (tick % SCAN_TICKS == 0) scan(server);
        for (Iterator<NpcState> it = npcs.values().iterator(); it.hasNext(); ) {
            NpcState s = it.next();
            if (s.npc.isRemoved()) {
                it.remove();
                continue;
            }
            // Une version d'Epic Fight différente ne doit jamais faire tomber le serveur : erreur signalée
            // une fois par sorte, le PNJ reste suivi
            try {
                move(s);
            } catch (Throwable t) {
                warnOnce("deplacement", t);
            }
            try {
                gestures(s);
            } catch (Throwable t) {
                warnOnce("geste", t);
            }
            try {
                idleReset(s);
            } catch (Throwable t) {
                warnOnce("remise", t);
            }
        }
        flushPending(server);
    }

    /**
     * Un joueur commence à voir un PNJ : mode combat et état actuel lui sont envoyés, puis ses
     * animations sont remises à zéro chez lui.
     */
    @SubscribeEvent
    public void onStartTracking(PlayerEvent.StartTracking event) {
        if (!(event.getTarget() instanceof ServerPlayer npc) || !(event.getEntity() instanceof ServerPlayer viewer)) return;
        if (!Settings.EF_ENABLED.get() || !isNpc(npc)) return;
        track(npc);
        pending.add(new PendingSync(viewer.getUUID(), npc.getId(), tick + SYNC_DELAY, false));
        pending.add(new PendingSync(viewer.getUUID(), npc.getId(), tick + RESET_DELAY, true));
    }

    @SubscribeEvent
    public void onStopped(ServerStoppedEvent event) {
        npcs.clear();
        pending.clear();
        lastLoggedCount = -1;
    }

    /** Module éteint : les PNJ en marche sont annoncés arrêtés (sinon ils marcheraient sur place), puis oubliés. */
    private void stopAll() {
        for (NpcState s : npcs.values()) {
            if (s.npc.isRemoved()) continue;
            if (s.running && !sprintUnavailable) setSprintFlag(s.npc, false);
            if (!s.moving) continue;
            try {
                broadcast(s.npc, new SPUpdatePlayerInput(s.npc.getId(), 0f, 0f));
            } catch (Throwable t) {
                warnOnce("arret", t);
            }
        }
        npcs.clear();
        pending.clear();
        lastLoggedCount = -1;
        CityCoreCompanion.LOGGER.info("Module Epic Fight éteint (epicfight.actif = false) : PNJ plus animés");
    }

    /* ───────────────────────── PNJ ───────────────────────── */

    /** PNJ : entité joueur absente de la liste des joueurs connectés (et pas un « faux joueur » d'un mod). */
    private static boolean isNpc(ServerPlayer p) {
        if (p instanceof FakePlayer || p.getServer() == null) return false;
        return p.getServer().getPlayerList().getPlayer(p.getUUID()) != p;
    }

    /** Recherche les PNJ chargés dans tous les mondes (Citizens ne les déclare pas comme joueurs). */
    private void scan(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity e : level.getAllEntities()) {
                if (e instanceof ServerPlayer p && !npcs.containsKey(p.getId()) && isNpc(p)) track(p);
            }
        }
        if (Settings.LOG.get() && npcs.size() != lastLoggedCount) {
            lastLoggedCount = npcs.size();
            CityCoreCompanion.LOGGER.info("{} PNJ animé(s) pour Epic Fight", npcs.size());
        }
    }

    /**
     * Suit un PNJ (s'il ne l'est pas déjà). Vu pour la première fois, il ne doit jouer aucun geste
     * ancien : une étiquette déjà posée (restée à l'arrêt du serveur, remise par Citizens à sa
     * réapparition) est retirée sans être jouée.
     */
    private NpcState track(ServerPlayer npc) {
        NpcState s = npcs.get(npc.getId());
        if (s != null) return s;
        s = new NpcState(npc, tick);
        npcs.put(npc.getId(), s);
        boolean stale = npc.removeTag(TAG_ATTACK);
        stale |= npc.removeTag(TAG_SHOT);
        if (stale && Settings.LOG.get()) {
            CityCoreCompanion.LOGGER.info("PNJ {} : geste resté en attente retiré sans être joué", npc.getGameProfile().getName());
        }
        return s;
    }

    /* ───────────────────────── Déplacements ───────────────────────── */

    private void move(NpcState s) {
        ServerPlayer npc = s.npc;
        double dx = npc.getX() - s.lastX, dz = npc.getZ() - s.lastZ;
        s.lastX = npc.getX();
        s.lastZ = npc.getZ();
        double speed = Math.sqrt(dx * dx + dz * dz);
        if (speed > TELEPORT) speed = 0;

        boolean movingNow = speed >= Settings.WALK_MIN.get();
        s.stillTicks = movingNow ? 0 : s.stillTicks + 1;
        boolean moving = movingNow || (s.moving && s.stillTicks < STOP_TICKS);

        float forward = 0f, strafe = 0f;
        if (moving) {
            if (movingNow) {
                float[] in = Motion.input(dx, dz, npc.getYHeadRot());
                forward = in[0];
                strafe = in[1];
            } else {
                // Courte pause dans le pas : on garde la direction précédente
                forward = s.forward;
                strafe = s.strafe;
            }
        }
        boolean changed = moving != s.moving
                || Math.abs(forward - s.forward) >= 0.25f || Math.abs(strafe - s.strafe) >= 0.25f;
        s.moving = moving;
        if (changed) {
            s.forward = forward;
            s.strafe = strafe;
            broadcast(npc, new SPUpdatePlayerInput(npc.getId(), forward, strafe));
            if (Settings.LOG.get()) {
                CityCoreCompanion.LOGGER.info("PNJ {} : {} (avant {}, côté {}, vitesse {})", npc.getGameProfile().getName(),
                        moving ? "marche" : "arrêt", forward, strafe, String.format("%.3f", speed));
            }
        }
        run(s, moving, speed);
    }

    /** Course : drapeau de sprint posé au-delà de la vitesse réglée (animation de course d'Epic Fight). */
    private void run(NpcState s, boolean moving, double speed) {
        double runMin = Settings.RUN_MIN.get();
        if (runMin <= 0 || sprintUnavailable) return;
        if (moving && speed >= runMin) {
            s.fastTicks++;
            s.slowTicks = 0;
        } else {
            s.slowTicks++;
            s.fastTicks = 0;
        }
        boolean run = s.running ? (moving && s.slowTicks < RUN_OUT_TICKS) : s.fastTicks >= RUN_IN_TICKS;
        if (run == s.running) return;
        s.running = run;
        setSprintFlag(s.npc, run);
        if (run) s.sprintedOnce = true;
        // Un PNJ n'a pas faim : la course ne doit pas lui coûter de nourriture (ni de vie à jeun)
        if (s.sprintedOnce && s.npc.getFoodData().getFoodLevel() < 20) s.npc.getFoodData().setFoodLevel(20);
    }

    private void setSprintFlag(ServerPlayer npc, boolean on) {
        try {
            if (setSharedFlag == null) {
                setSharedFlag = Entity.class.getDeclaredMethod("setSharedFlag", int.class, boolean.class);
                setSharedFlag.setAccessible(true);
            }
            setSharedFlag.invoke(npc, FLAG_SPRINTING, on);
        } catch (Throwable t) {
            sprintUnavailable = true;
            CityCoreCompanion.LOGGER.warn("Course des PNJ indisponible ({}) : ils marcheront seulement", t.toString());
        }
    }

    /* ───────────────────────── Gestes ───────────────────────── */

    private void gestures(NpcState s) {
        ServerPlayer npc = s.npc;
        if (npc.getTags().isEmpty()) return;
        if (npc.removeTag(TAG_ATTACK)) {
            s.lastGestureTick = tick;
            if (Settings.ATTACKS.get()) attack(s);
        }
        if (npc.removeTag(TAG_SHOT)) {
            s.lastGestureTick = tick;
            if (Settings.SHOTS.get()) shot(s);
        }
    }

    /** Coup : animation suivante de l'enchaînement de l'arme tenue. */
    private void attack(NpcState s) {
        List<? extends AssetAccessor<? extends StaticAnimation>> combo = comboFor(s.npc);
        if (combo.isEmpty()) return;
        if (tick - s.lastAttackTick > COMBO_RESET_TICKS) s.combo = 0;
        AssetAccessor<? extends StaticAnimation> anim = combo.get(s.combo % combo.size());
        s.combo++;
        s.lastAttackTick = tick;
        broadcast(s.npc, new SPAnimatorControl(AbstractAnimatorControl.Action.PLAY, anim, s.npc.getId(), 0.0F, false));
        if (Settings.LOG.get()) CityCoreCompanion.LOGGER.info("PNJ {} : coup {}", s.npc.getGameProfile().getName(), s.combo);
    }

    /** Tir : chaque joueur joue l'animation de tir de l'arme tenue (arc, arbalète, armes de mods). */
    private void shot(NpcState s) {
        boolean crossbow = s.npc.getMainHandItem().getItem() instanceof net.minecraft.world.item.CrossbowItem;
        AssetAccessor<? extends StaticAnimation> fallback = crossbow ? Animations.BIPED_CROSSBOW_SHOT : Animations.BIPED_BOW_SHOT;
        broadcast(s.npc, new SPAnimatorControl(AbstractAnimatorControl.Action.SHOT, fallback, s.npc.getId(), 0.0F, false));
        if (Settings.LOG.get()) CityCoreCompanion.LOGGER.info("PNJ {} : tir", s.npc.getGameProfile().getName());
    }

    /**
     * Enchaînement de l'arme tenue : celui qu'Epic Fight donne à l'arme (armes de mods comprises),
     * sans les deux derniers coups (attaque en courant, attaque en l'air) ; à défaut, selon le type
     * d'objet.
     */
    private static List<? extends AssetAccessor<? extends StaticAnimation>> comboFor(ServerPlayer npc) {
        ServerPlayerPatch patch = EpicFightCapabilities.getServerPlayerPatchAsOptional(npc).orElse(null);
        if (patch != null) {
            try {
                CapabilityItem cap = patch.getHoldingItemCapability(InteractionHand.MAIN_HAND);
                List<? extends AssetAccessor<? extends StaticAnimation>> list = cap == null ? null : cap.getAutoAttackMotion(patch);
                if (list != null && !list.isEmpty()) return list.size() > 2 ? list.subList(0, list.size() - 2) : list;
            } catch (Throwable ignored) {
                // Arme sans enchaînement connu : choix par type d'objet ci-dessous
            }
        }
        Item item = npc.getMainHandItem().getItem();
        if (item instanceof SwordItem) return present(Animations.SWORD_AUTO1, Animations.SWORD_AUTO2, Animations.SWORD_AUTO3);
        if (item instanceof AxeItem) return present(Animations.AXE_AUTO1, Animations.AXE_AUTO2);
        if (item instanceof TridentItem) return present(Animations.SPEAR_ONEHAND_AUTO);
        if (item instanceof DiggerItem) return present(Animations.TOOL_AUTO1, Animations.TOOL_AUTO2);
        return present(Animations.FIST_AUTO1, Animations.FIST_AUTO2, Animations.FIST_AUTO3);
    }

    /* ───────────────────────── Remise à zéro ───────────────────────── */

    /**
     * PNJ immobile, sans geste depuis le délai réglé (remiseAZero) : ses animations sont remises à
     * zéro chez les joueurs qui le voient. Un PNJ qui travaille ou s'entraîne (gestes réguliers) ou
     * qui marche n'est pas concerné.
     */
    private void idleReset(NpcState s) {
        int period = Settings.RESET_SECONDS.get() * 20;
        if (period <= 0 || s.moving) return;
        if (tick - s.lastGestureTick < period || tick - s.lastResetTick < period) return;
        s.lastResetTick = tick;
        SPChangeLivingMotion reset = resetPacket(s.npc);
        if (reset == null) return;
        broadcast(s.npc, reset);
        if (Settings.LOG.get()) CityCoreCompanion.LOGGER.info("PNJ {} : animations remises à zéro (immobile)", s.npc.getGameProfile().getName());
    }

    /**
     * Paquet de remise à zéro : les animations « de vie » du PNJ telles que le serveur les connaît
     * (repos, marche… selon l'objet tenu), celui qu'Epic Fight envoie lui-même quand un joueur
     * commence à voir le PNJ. Chez le joueur, il arrête les animations superposées en cours (une
     * frappe bloquée en boucle, par exemple) et l'animation est recalculée d'après l'état réel du
     * PNJ. null si le PNJ n'a pas de données Epic Fight.
     */
    private static SPChangeLivingMotion resetPacket(ServerPlayer npc) {
        ServerPlayerPatch patch = EpicFightCapabilities.getServerPlayerPatchAsOptional(npc).orElse(null);
        if (patch == null) return null;
        Animator animator = patch.getAnimator();
        SPChangeLivingMotion msg = new SPChangeLivingMotion(npc.getId());
        msg.putEntries(animator.getLivingAnimations().entrySet());
        return msg;
    }

    /** Animations disponibles parmi celles données (une animation non chargée est ignorée). */
    @SafeVarargs
    private static List<AssetAccessor<? extends StaticAnimation>> present(AssetAccessor<? extends StaticAnimation>... anims) {
        List<AssetAccessor<? extends StaticAnimation>> list = new ArrayList<>();
        for (AssetAccessor<? extends StaticAnimation> a : anims) if (a != null) list.add(a);
        return list;
    }

    /* ───────────────────────── Envois ───────────────────────── */

    /**
     * Joueurs arrivés près d'un PNJ : mode combat (si réglé) puis « touches » actuelles ; un peu
     * plus tard, remise à zéro de ses animations chez eux.
     */
    private void flushPending(MinecraftServer server) {
        if (pending.isEmpty()) return;
        for (Iterator<PendingSync> it = pending.iterator(); it.hasNext(); ) {
            PendingSync p = it.next();
            if (p.due() > tick) continue;
            it.remove();
            ServerPlayer viewer = server.getPlayerList().getPlayer(p.viewer());
            NpcState s = npcs.get(p.npcId());
            if (viewer == null || s == null || s.npc.isRemoved()) continue;
            try {
                if (p.reset()) {
                    SPChangeLivingMotion reset = resetPacket(s.npc);
                    if (reset != null) EpicFightNetworkManager.sendToPlayer(reset, viewer);
                    s.lastResetTick = tick;
                    continue;
                }
                if (Settings.FORCE_EPIC_MODE.get()) {
                    EpicFightNetworkManager.sendToPlayer(new SPChangePlayerMode(s.npc.getId(), PlayerPatch.PlayerMode.EPICFIGHT), viewer);
                }
                EpicFightNetworkManager.sendToPlayer(new SPUpdatePlayerInput(s.npc.getId(), s.forward, s.strafe), viewer);
            } catch (Throwable t) {
                CityCoreCompanion.LOGGER.warn("Envoi à {} impossible : {}", viewer.getGameProfile().getName(), t.toString());
            }
        }
    }

    private void warnOnce(String kind, Throwable t) {
        if (warned.add(kind)) CityCoreCompanion.LOGGER.warn("Animation des PNJ ({}) impossible : {}", kind, t.toString());
    }

    /** Envoi à tous les joueurs qui voient le PNJ. */
    private static void broadcast(ServerPlayer npc, CustomPacketPayload payload) {
        EpicFightNetworkManager.sendToAllPlayerTrackingThisEntity(payload, npc);
    }
}
