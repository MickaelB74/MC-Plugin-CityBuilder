package com.citycore.npc;

import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.npc.NPCRegistry;
import net.citizensnpcs.trait.HologramTrait;
import net.citizensnpcs.trait.SkinTrait;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class NPCManager {

    private static final String MAYOR_NPC_ID_TAG = "citycore_mayor";
    private static final String MAYOR_NAME       = "§6Alderic";

    private static final String SKIN_VALUE =
            "ewogICJ0aW1lc3RhbXAiIDogMTc3MTQ4OTcyNDg1OCwKICAicHJvZmlsZUlkIiA6ICI2NDg4Y2VjMjc4OGQ0MTI2OTk5NWMyMmY4OTdmMzA4OSIsCiAgInByb2ZpbGVOYW1lIiA6ICJBc3BlbjA1MyIsCiAgInNpZ25hdHVyZVJlcXVpcmVkIiA6IHRydWUsCiAgInRleHR1cmVzIiA6IHsKICAgICJTS0lOIiA6IHsKICAgICAgInVybCIgOiAiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS9kMTUzNTk3ZmE2M2U2MTJhMGQ2YWY2OWE0ZTFiMDFlY2YxM2M4ZGI0Y2E5ZThkYzdkZmZmODQ5YjBjMTAzZTlmIgogICAgfQogIH0KfQ==";
    private static final String SKIN_SIGNATURE =
            "A60BzbuZYlRE2SbwCz02jy+hnBG1o2D8QMl8IcfD994ft2CBTWEhAdhWN0Fey78EXKMTTRFmyGQweLDlV/29lPIRwSLhy77gb9fqkYnR1LaLykUAkiBJ0VBHJW7ZAYAAmOJE5ehoo/fwWADNwlVAu8oZzXGJhhf8goCiGnTBuRRXI6rkyMdMGpjkDqxATuew/0mtxNAGLVIORoHNhbBj1p3ihaM9By4L/A39oN/WthMf+rMQNwhLCuMBYXPI+//ShFhDJl/lDTIm7nvsCk/1vVVDEuULosjWqlYPf2r+r3hDAMIE5StyDk9ypxImHnDe3D2cb5DFNBtZKHLYyIq8enxXxotHcMRjZeaHg4KwajswshsMh07yvXO0x46nfF6RFcMEbjL2u7eRW4Y1bJjKVkxTZ9hmM6C9oHHYKvHRAT1cVo6YxGU8/fukthrZvD0BlQAjsDdwBGW/p2ex/dQtweHWDlamWeqhNIUBIdMF9qlWwNX6f24clecUIhEbXxXdDrupXNZuBrBtUkzicbPrC+PVJaKT0qCO9S2fyHW89VznqAK3whv3CVBGvqpq242IucuTHJpwDet1ctXXWw97ebSfvP//Cg1f9nn5mrE81OB5G5BEsS1Y32KRviQ5tfZPnPsU0SRXatoHzNp0RozrtF2B53SA5eII7ViNFpe/mIY=";

    private final JavaPlugin plugin;
    private final Set<UUID> followingPlayers = new HashSet<>();

    public NPCManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /* =========================
       SPAWN / FIND / REMOVE
       ========================= */

    public NPC spawnMayor(Player player) {
        Location loc = player.getLocation().clone();
        loc.add(loc.getDirection().normalize().multiply(2));
        loc.setY(Math.floor(loc.getY()));

        Location mayorLoc = loc.clone();
        mayorLoc.setYaw((player.getLocation().getYaw() + 180) % 360);
        mayorLoc.setPitch(0);

        NPCRegistry registry = CitizensAPI.getNPCRegistry();
        NPC npc = registry.createNPC(EntityType.PLAYER, MAYOR_NAME);
        npc.data().set(MAYOR_NPC_ID_TAG, true);

        // Skin via SkinTrait — AVANT spawn
        SkinTrait skinTrait = npc.getOrAddTrait(SkinTrait.class);
        skinTrait.setSkinPersistent("alderic", SKIN_SIGNATURE, SKIN_VALUE);

        // Hologramme Citizens natif
        HologramTrait hologram = npc.getOrAddTrait(HologramTrait.class);
        hologram.addLine("§7✦ §eMaire de la ville §7✦");

        // Spawn APRÈS le skin
        npc.spawn(mayorLoc);

        // Force SkinsRestorer à appliquer le skin sur le NPC si disponible
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            forceSkinsRestorerRefresh(npc);
        }, 20L);

        return npc;
    }

    /**
     * Utilise la commande console SkinsRestorer pour forcer
     * l'affichage du skin sur les clients crackés.
     * Ne plante pas si SkinsRestorer est absent.
     */
    private void forceSkinsRestorerRefresh(NPC npc) {
        if (!npc.isSpawned()) return;

        // Vérifie que SkinsRestorer est bien installé
        if (Bukkit.getPluginManager().getPlugin("SkinsRestorer") == null) {
            plugin.getLogger().info("SkinsRestorer absent — skin NPC limité aux clients officiels.");
            return;
        }

        try {
            // SkinsRestorer expose une commande console pour set le skin d'un joueur
            // On cible le NPC par son nom d'entité
            String npcName = npc.getEntity().getName();
            Bukkit.dispatchCommand(
                    Bukkit.getConsoleSender(),
                    "sr set " + npcName + " alderic"
            );
            plugin.getLogger().info("✅ SkinsRestorer refresh forcé pour Alderic.");
        } catch (Exception e) {
            plugin.getLogger().warning("⚠ SkinsRestorer refresh échoué : " + e.getMessage());
        }
    }

    public NPC getMayor() {
        for (NPC npc : CitizensAPI.getNPCRegistry()) {
            if (npc.data().has(MAYOR_NPC_ID_TAG)) return npc;
        }
        return null;
    }

    public boolean isMayor(NPC npc) {
        return npc.data().has(MAYOR_NPC_ID_TAG);
    }

    public void removeMayor() {
        NPC mayor = getMayor();
        if (mayor != null) mayor.destroy();
    }

    /* =========================
       MODE SUIVI
       ========================= */

    public void startFollowing(Player player) {
        NPC mayor = getMayor();
        if (mayor == null || !mayor.isSpawned()) return;

        mayor.getNavigator().getDefaultParameters()
                .range(50f)
                .speedModifier(0.8f)
                .distanceMargin(2.0);

        mayor.getNavigator().setTarget(player, false);
        followingPlayers.add(player.getUniqueId());
    }

    public void stopFollowing(Player player) {
        NPC mayor = getMayor();
        if (mayor != null && mayor.isSpawned()) {
            mayor.getNavigator().cancelNavigation();
        }
        followingPlayers.remove(player.getUniqueId());
    }

    public boolean isFollowing(Player player) {
        return followingPlayers.contains(player.getUniqueId());
    }
}