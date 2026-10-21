package com.citycore.npc;

import com.citycore.command.CityPermissions;
import org.bukkit.entity.Player;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Habitants activés pour les tests (/city npc &lt;type|tous&gt; activer, administrateurs) : leur menu
 * complet (vente, entrepôt, boutique, quêtes, récits, gestion) s'ouvre sans atelier, pour un
 * administrateur seulement (permission {@link CityPermissions#ADMIN_PNJ}).
 *
 * Rien n'est enregistré et l'état réel de l'habitant ne change pas : il n'est pas « installé »,
 * donc ni étape de recrutement validée, ni emplois, ni besoins, ni rencontre déclenchée par le
 * clic. Tout s'efface au redémarrage ou avec /city npc &lt;type|tous&gt; desactiver.
 */
public final class TestActivation {

    private static final Set<CityNPC> ACTIVE = ConcurrentHashMap.newKeySet();

    private TestActivation() {}

    /** Active les menus de test ; false s'ils l'étaient déjà. */
    public static boolean enable(CityNPC npc) {
        return ACTIVE.add(npc);
    }

    /** Désactive les menus de test ; false s'ils ne l'étaient pas. */
    public static boolean disable(CityNPC npc) {
        return ACTIVE.remove(npc);
    }

    public static boolean isActive(CityNPC npc) {
        return ACTIVE.contains(npc);
    }

    /** Ce joueur voit le menu de test de cet habitant (activé, et le joueur est administrateur des PNJ). */
    public static boolean applies(CityNPC npc, Player player) {
        return ACTIVE.contains(npc) && player.hasPermission(CityPermissions.ADMIN_PNJ);
    }

    /** État qui choisit le menu montré à ce joueur : « installé » en test, sinon l'état réel. */
    public static NPCState menuState(CityNPC npc, NPCState real, Player player) {
        return real != NPCState.ASSIGNED && applies(npc, player) ? NPCState.ASSIGNED : real;
    }
}
