package com.citycore.discordbot;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.PermissionOverride;
import net.dv8tion.jda.api.entities.channel.attribute.IPermissionContainer;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * Les salons du bot. Chacun est repris par son identifiant (fichier de configuration, sinon
 * mémoire du bot) ; s'il n'existe pas, il est créé dans la catégorie du bot. Les droits de
 * « tout le monde » y sont posés à la création, et vérifiés à chaque lancement :
 * <ul>
 *   <li><b>événements, ville, joueurs</b> : lecture seule (ni message, ni fil, ni réaction, ni commande) ;</li>
 *   <li><b>admin</b> : invisible (visible des administrateurs du serveur Discord ; ajoutez-y les
 *       rôles de votre choix), sans écriture ;</li>
 *   <li><b>chat</b> : la commande {@code /chat} seulement. Discord n'accepte une commande que dans
 *       un salon où l'on a le droit d'envoyer des messages : ce droit reste donc ouvert, tout le
 *       reste est fermé (fichiers, liens, fils, réactions…), et le bot supprime aussitôt les
 *       messages tapés à la main (voir {@code Bot}) ;</li>
 *   <li><b>statut</b> : salon vocal où personne ne peut entrer ni écrire, dont le nom affiche le statut.</li>
 * </ul>
 * Les salons peuvent être renommés ou déplacés dans Discord : seuls leurs identifiants comptent.
 */
final class Channels {

    /** Tout ce qui revient à écrire dans un salon. */
    private static final EnumSet<Permission> WRITING = EnumSet.of(
            Permission.MESSAGE_SEND, Permission.MESSAGE_SEND_IN_THREADS, Permission.CREATE_PUBLIC_THREADS,
            Permission.CREATE_PRIVATE_THREADS, Permission.MESSAGE_ADD_REACTION, Permission.MESSAGE_ATTACH_FILES,
            Permission.MESSAGE_EMBED_LINKS, Permission.MESSAGE_TTS, Permission.MESSAGE_MENTION_EVERYONE,
            Permission.MESSAGE_EXT_EMOJI, Permission.MESSAGE_EXT_STICKER, Permission.MESSAGE_ATTACH_VOICE_MESSAGE,
            Permission.MESSAGE_SEND_POLLS, Permission.USE_APPLICATION_COMMANDS);
    /** Ce dont le bot a besoin pour publier. */
    private static final EnumSet<Permission> BOT = EnumSet.of(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND,
            Permission.MESSAGE_EMBED_LINKS, Permission.MESSAGE_HISTORY);

    /** Droits de « tout le monde » dans un salon : accordés, refusés. */
    private record Rights(EnumSet<Permission> allow, EnumSet<Permission> deny) {}

    private enum Access {
        /** La commande /chat seulement : envoyer et utiliser les commandes, rien d'autre. */
        COMMAND_ONLY,
        READ_ONLY,
        HIDDEN,
        /** Salon vocal de statut : visible, fermé. */
        STATUS;

        Rights rights() {
            EnumSet<Permission> allow = EnumSet.noneOf(Permission.class);
            EnumSet<Permission> deny = EnumSet.copyOf(WRITING);
            switch (this) {
                case COMMAND_ONLY -> {
                    allow.add(Permission.MESSAGE_SEND);
                    allow.add(Permission.USE_APPLICATION_COMMANDS);
                    deny.removeAll(allow);
                }
                case HIDDEN -> deny.add(Permission.VIEW_CHANNEL);
                case STATUS -> {
                    allow.add(Permission.VIEW_CHANNEL);
                    deny.add(Permission.VOICE_CONNECT);
                }
                case READ_ONLY -> { }
            }
            return new Rights(allow, deny);
        }
    }

    final TextChannel events, city, admin, chat, players;
    final VoiceChannel status;
    /** Ce qui n'a pas pu être réglé au lancement (droit manquant au bot), à afficher. */
    final List<String> problems = new ArrayList<>();

    Channels(Guild guild, Config config, Store store) {
        Category category = category(guild, config, store);
        events  = text(guild, category, config, store, "evenements", "évènements",
                "Journal de la ville : sièges, raids, bâtiments, niveaux…", Access.READ_ONLY);
        city    = text(guild, category, config, store, "ville", "valmont",
                "La ville en un coup d'œil, mise à jour en continu.", Access.READ_ONLY);
        admin   = text(guild, category, config, store, "admin", "admin",
                "Journal du serveur : commandes des opérateurs, modes de jeu.", Access.HIDDEN);
        chat    = text(guild, category, config, store, "chat", "chat",
                "Discussion avec le serveur Minecraft : écrivez avec /chat ici, /discord en jeu.", Access.COMMAND_ONLY);
        players = text(guild, category, config, store, "joueurs", "joueurs",
                "Une carte par joueur : niveau, expérience, métier, bourse.", Access.READ_ONLY);
        status  = voice(guild, category, config, store);

        // Salons déjà en place (créés par une version précédente, ou désignés à la main) : droits remis d'aplomb
        enforce(guild, events, Access.READ_ONLY);
        enforce(guild, city, Access.READ_ONLY);
        enforce(guild, admin, Access.HIDDEN);
        enforce(guild, chat, Access.COMMAND_ONLY);
        enforce(guild, players, Access.READ_ONLY);
        enforce(guild, status, Access.STATUS);
        if (!guild.getSelfMember().hasPermission(chat, Permission.MESSAGE_MANAGE)) {
            problems.add("Le bot n'a pas le droit « Gérer les messages » dans #" + chat.getName()
                    + " : les messages tapés à la main n'y seront pas supprimés (seule la commande /chat doit y servir).");
        }
    }

    /* ───────────────────────── Création ───────────────────────── */

    private static Category category(Guild guild, Config config, Store store) {
        String id = store.get("salon.categorie");
        Category found = id.isEmpty() ? null : guild.getCategoryById(id);
        if (found != null) return found;
        String name = config.get("salons.categorie", "Valmont");
        Category created = guild.createCategory(name).complete();
        store.put("salon.categorie", created.getId());
        System.out.println("Catégorie « " + name + " » créée.");
        return created;
    }

    private static TextChannel text(Guild guild, Category category, Config config, Store store,
                                    String key, String defaultName, String topic, Access access) {
        String configured = config.get("salons." + key + ".id");
        String id = configured.isEmpty() ? store.get("salon." + key) : configured;
        TextChannel found = id.isEmpty() ? null : guild.getTextChannelById(id);
        if (found != null) return found;
        if (!configured.isEmpty()) {
            throw new IllegalStateException("salons." + key + ".id (" + configured + ") ne désigne aucun salon textuel de ce serveur Discord");
        }
        String name = config.get("salons." + key, defaultName);
        Member self = guild.getSelfMember();
        Rights rights = settable(self, access.rights());
        EnumSet<Permission> own = EnumSet.copyOf(BOT);
        // Salon de discussion : le bot y supprime les messages tapés à la main
        if (access == Access.COMMAND_ONLY && self.hasPermission(Permission.MESSAGE_MANAGE)) own.add(Permission.MESSAGE_MANAGE);
        TextChannel created = category.createTextChannel(name).setTopic(topic)
                .addMemberPermissionOverride(self.getIdLong(), own, null)
                .addPermissionOverride(guild.getPublicRole(), rights.allow(), rights.deny())
                .complete();
        store.put("salon." + key, created.getId());
        System.out.println("Salon #" + name + " créé.");
        return created;
    }

    private static VoiceChannel voice(Guild guild, Category category, Config config, Store store) {
        String configured = config.get("salons.statut.id");
        String id = configured.isEmpty() ? store.get("salon.statut") : configured;
        VoiceChannel found = id.isEmpty() ? null : guild.getVoiceChannelById(id);
        if (found != null) return found;
        if (!configured.isEmpty()) {
            throw new IllegalStateException("salons.statut.id (" + configured + ") ne désigne aucun salon vocal de ce serveur Discord");
        }
        Member self = guild.getSelfMember();
        Rights rights = settable(self, Access.STATUS.rights());
        // Le bot garde le droit de s'y connecter, que Discord exige pour modifier un salon vocal
        // (son nom porte le statut).
        VoiceChannel created = category.createVoiceChannel("⏳ Statut du serveur")
                .addPermissionOverride(guild.getPublicRole(), rights.allow(), rights.deny())
                .addMemberPermissionOverride(self.getIdLong(),
                        EnumSet.of(Permission.VIEW_CHANNEL, Permission.VOICE_CONNECT, Permission.MANAGE_CHANNEL), null)
                .complete();
        store.put("salon.statut", created.getId());
        System.out.println("Salon vocal de statut créé.");
        return created;
    }

    /**
     * Discord ne laisse un bot régler, à la création d'un salon, que les droits qu'il possède
     * lui-même (sauf s'il a « Gérer les rôles »). Un droit qu'il n'a pas, « tout le monde » ne l'a
     * pas non plus au niveau du serveur : il n'y a rien à refuser.
     */
    private static Rights settable(Member self, Rights wanted) {
        if (self.hasPermission(Permission.MANAGE_ROLES)) return wanted;
        EnumSet<Permission> own = self.getPermissions();
        EnumSet<Permission> allow = EnumSet.copyOf(wanted.allow()), deny = EnumSet.copyOf(wanted.deny());
        allow.retainAll(own);
        deny.retainAll(own);
        return new Rights(allow, deny);
    }

    /* ───────────────────────── Droits des salons en place ───────────────────────── */

    /**
     * Met les droits de « tout le monde » d'un salon existant en accord avec son rôle. Les autres
     * réglages du salon (rôles ajoutés à la main, droits non concernés) ne sont pas touchés.
     * Modifier les droits d'un salon demande au bot le droit « Gérer les rôles » ; sans lui, l'écart
     * est signalé une fois au lancement et le salon reste tel quel.
     */
    private void enforce(Guild guild, IPermissionContainer channel, Access access) {
        Rights wanted = access.rights();
        Member self = guild.getSelfMember();
        PermissionOverride current = channel.getPermissionOverride(guild.getPublicRole());
        EnumSet<Permission> allowed = current == null ? EnumSet.noneOf(Permission.class) : current.getAllowed();
        EnumSet<Permission> denied = current == null ? EnumSet.noneOf(Permission.class) : current.getDenied();

        // Un droit que le bot n'a pas dans le salon ne peut pas être modifié par lui : laissé tel quel
        EnumSet<Permission> reachable = self.getPermissions(channel);
        EnumSet<Permission> allow = EnumSet.copyOf(allowed), deny = EnumSet.copyOf(denied);
        for (Permission p : wanted.allow()) {
            if (allowed.contains(p) || !reachable.contains(p)) continue;
            allow.add(p);
            deny.remove(p);
        }
        for (Permission p : wanted.deny()) {
            if (denied.contains(p) || !reachable.contains(p)) continue;
            deny.add(p);
            allow.remove(p);
        }
        if (allow.equals(allowed) && deny.equals(denied)) return; // déjà en ordre

        if (!self.hasPermission(channel, Permission.MANAGE_PERMISSIONS)) {
            problems.add("Droits de #" + channel.getName() + " à corriger, mais le bot n'a pas le droit « Gérer les rôles ».");
            return;
        }
        try {
            channel.upsertPermissionOverride(guild.getPublicRole()).setPermissions(allow, deny).complete();
            System.out.println("Droits de #" + channel.getName() + " mis à jour.");
        } catch (RuntimeException e) {
            problems.add("Droits de #" + channel.getName() + " non mis à jour : " + e.getMessage());
        }
    }
}
