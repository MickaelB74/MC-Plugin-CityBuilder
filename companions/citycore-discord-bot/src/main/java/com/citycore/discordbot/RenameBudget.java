package com.citycore.discordbot;

/**
 * Quand renommer le salon vocal de statut. Discord n'accepte que deux changements de nom d'un
 * salon par tranche de dix minutes ; un troisième est mis en attente, jusqu'à dix minutes, et le
 * nom affiché prend alors beaucoup de retard. Le bot compte donc ses propres changements, sur une
 * fenêtre glissante un peu plus longue que celle de Discord, et n'en envoie que s'il lui en reste :
 * <ul>
 *   <li>aucun changement dans la fenêtre : le nouveau nom part tout de suite ;</li>
 *   <li>un changement dans la fenêtre : le dernier permis attend que le nom voulu soit stable
 *       depuis {@link #SETTLE_MS} (des joueurs qui arrivent l'un après l'autre ne le gaspillent pas) ;</li>
 *   <li>deux changements dans la fenêtre : il faut attendre que le plus ancien en sorte.</li>
 * </ul>
 */
final class RenameBudget {

    /** Fenêtre de Discord (dix minutes), avec une marge. */
    static final long WINDOW_MS = 10 * 60_000L + 20_000L;
    /** Stabilité exigée du nom voulu avant de dépenser le dernier changement de la fenêtre. */
    static final long SETTLE_MS = 45_000L;

    private RenameBudget() {
    }

    /**
     * @param now         maintenant (ms)
     * @param older       date de l'avant-dernier changement de nom (0 : aucun)
     * @param newer       date du dernier changement de nom (0 : aucun)
     * @param wantedSince depuis quand le nom voulu est celui-ci
     * @return true si le salon peut être renommé maintenant
     */
    static boolean allowed(long now, long older, long newer, long wantedSince) {
        int used = (now - older < WINDOW_MS ? 1 : 0) + (now - newer < WINDOW_MS ? 1 : 0);
        return used == 0 || (used == 1 && now - wantedSince >= SETTLE_MS);
    }
}
