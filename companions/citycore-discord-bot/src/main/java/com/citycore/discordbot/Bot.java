package com.citycore.discordbot;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import net.dv8tion.jda.api.exceptions.InvalidTokenException;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.data.DataArray;
import net.dv8tion.jda.api.utils.data.DataObject;
import net.dv8tion.jda.api.utils.messages.MessageRequest;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Bot Discord de CityCore. Il interroge l'API du mod compagnon (serveur Minecraft) et tient six
 * salons :
 * <ul>
 *   <li><b>événements</b> : une carte par fait de la vie de la ville (sièges, raids, bâtiments, niveaux…) ;</li>
 *   <li><b>ville</b> : une grande carte de la ville, modifiée sur place quand elle change ;</li>
 *   <li><b>admin</b> : le journal du serveur (commandes des opérateurs, modes de jeu) ;</li>
 *   <li><b>chat</b> : la discussion avec le jeu ({@code /discord} en jeu, {@code /chat} sur Discord) ;</li>
 *   <li><b>joueurs</b> : une carte par joueur, modifiée sur place ;</li>
 *   <li><b>statut</b> : un salon vocal fermé dont le nom donne l'état du serveur et le nombre de joueurs.</li>
 * </ul>
 * Deux fils : l'un attend les événements (demande gardée ouverte par le serveur : ils arrivent dès
 * qu'ils se produisent), l'autre relit l'état à intervalle régulier pour les cartes et le statut.
 * Le bot se passe du serveur Minecraft : s'il est arrêté, le statut passe « hors ligne » et tout
 * reprend à son retour, événements manqués compris.
 */
public final class Bot extends ListenerAdapter {

    /** Droits demandés à l'invitation : voir et gérer les salons, écrire, cartes, historique, gérer les messages, connexion vocale. */
    private static final long INVITE_PERMISSIONS = 1141776L;
    /** Après une connexion ou une déconnexion : le temps que le serveur ait compté ses joueurs. */
    private static final long REFRESH_DELAY_MS = 1_500L;
    /** Rappel « écrivez avec /chat » : pas plus d'un par ce délai. */
    private static final long HINT_EVERY_MS = 20_000L;
    /** Au-delà, les événements les plus anciens d'un rattrapage ne sont pas publiés. */
    private static final int MAX_CATCH_UP = 40;
    /** Nom d'un serveur Minecraft dont le MOTD n'a pas été réglé : sans intérêt dans le statut. */
    private static final String DEFAULT_MOTD = "A Minecraft Server";
    /** Image de la tête des joueurs quand cartes.tete est vide (« aucune » : pas d'image). */
    private static final String DEFAULT_HEAD = "https://mc-heads.net/head/{nom}/128";
    /** Échecs successifs de l'API avant d'afficher le serveur hors ligne. */
    private static final int FAILURES_BEFORE_OFFLINE = 2;

    private final JDA jda;
    private final Config config;
    private final Store store;
    private final ApiClient api;
    private final Channels channels;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(daemon("CityCore-Chat"));

    /** Dernier état reçu de l'API (null : jamais reçu). */
    private DataObject lastState;
    private int failures;
    private boolean refusalWarned;
    /** Contenu des cartes en place : une carte inchangée n'est pas renvoyée à Discord. */
    private String cityContent = "";
    private final Map<String, String> playerContent = new HashMap<>();
    private String statusName = "";
    private String presence = "";
    /** Nom que le salon de statut devrait porter, et depuis quand (vide : il est à jour). */
    private String wantedName = "";
    private long wantedSince;
    /** Réveil du fil de l'état : une connexion ou une déconnexion vient d'être annoncée. */
    private final Object wake = new Object();
    private boolean refreshAsked;
    private long hintAt;
    private boolean cleanupWarned;

    private Bot(JDA jda, Config config, Store store, ApiClient api, Channels channels) {
        this.jda = jda;
        this.config = config;
        this.store = store;
        this.api = api;
        this.channels = channels;
    }

    /* ───────────────────────── Démarrage ───────────────────────── */

    public static void main(String[] args) throws Exception {
        Config config = Config.load();
        if (config == null) {
            System.out.println("Fichier " + Config.FILE.toAbsolutePath() + " créé : remplissez-le "
                    + "(jeton du bot, serveur Discord, jeton de l'API), puis relancez le bot.");
            return;
        }
        String discordToken = config.get("discord.jeton"), guildId = config.get("discord.serveur");
        String apiToken = config.get("api.jeton");
        if (discordToken.isEmpty() || guildId.isEmpty() || apiToken.isEmpty()) {
            System.err.println("Configuration incomplète : discord.jeton, discord.serveur et api.jeton sont "
                    + "obligatoires (" + Config.FILE.toAbsolutePath() + ").");
            System.exit(1);
        }

        // Aucun message du bot ne peut mentionner qui que ce soit (@everyone tapé en jeu, par exemple)
        MessageRequest.setDefaultMentions(EnumSet.noneOf(Message.MentionType.class));

        JDA jda;
        try {
            // Aucune intention privilégiée : le bot ne lit pas le contenu des messages. Il sait seulement
            // qu'un message vient d'être posté (GUILD_MESSAGES), pour retirer du salon de discussion ce
            // qui n'est pas passé par /chat. Le suivi des salons est toujours actif dans JDA.
            jda = JDABuilder.createLight(discordToken, EnumSet.of(GatewayIntent.GUILD_MESSAGES)).build();
        } catch (InvalidTokenException e) {
            System.err.println("Jeton Discord refusé : vérifiez discord.jeton (" + Config.FILE.toAbsolutePath() + ").");
            System.exit(1);
            return;
        }
        jda.awaitReady();
        Guild guild = jda.getGuildById(guildId);
        if (guild == null) {
            System.err.println("Le bot n'est pas membre du serveur Discord " + guildId + ". Invitez-le avec ce lien, puis relancez :");
            System.err.println("https://discord.com/oauth2/authorize?client_id=" + jda.getSelfUser().getApplicationId()
                    + "&scope=bot%20applications.commands&permissions=" + INVITE_PERMISSIONS);
            jda.shutdownNow();
            System.exit(1);
        }

        Store store = Store.load();
        Channels channels;
        try {
            channels = new Channels(guild, config, store);
            guild.updateCommands().addCommands(
                    Commands.slash("chat", "Envoie un message aux joueurs connectés au serveur Minecraft")
                            .addOption(OptionType.STRING, "message", "Le message à afficher en jeu", true)
            ).complete();
        } catch (InsufficientPermissionException e) {
            System.err.println("Droit manquant sur le serveur Discord : " + e.getPermission().getName()
                    + ". Le bot a besoin de : Voir les salons, Gérer les salons, Envoyer des messages, "
                    + "Intégrer des liens, Voir les anciens messages, Gérer les messages, Se connecter (vocal).");
            jda.shutdownNow();
            System.exit(1);
            return;
        } catch (IllegalStateException e) {
            System.err.println("Configuration des salons : " + e.getMessage());
            jda.shutdownNow();
            System.exit(1);
            return;
        }

        if (!channels.problems.isEmpty()) {
            System.err.println("Droits des salons à revoir :");
            channels.problems.forEach(p -> System.err.println(" - " + p));
            System.err.println("Donnez au rôle du bot les droits manquants (Paramètres du serveur → Rôles), puis relancez-le. "
                    + "« Gérer les rôles » ne sert qu'à corriger des salons existants : il peut être retiré ensuite.");
        }

        Bot bot = new Bot(jda, config, store, new ApiClient(config.get("api.adresse", "http://127.0.0.1:8787"), apiToken), channels);
        jda.addEventListener(bot);
        Thread events = daemon("CityCore-Evenements").newThread(bot::eventLoop);
        Thread state = daemon("CityCore-Etat").newThread(bot::stateLoop);
        events.start();
        state.start();
        Runtime.getRuntime().addShutdownHook(new Thread(jda::shutdown));
        System.out.println("Bot connecté à « " + guild.getName() + " » en tant que " + jda.getSelfUser().getName()
                + ". API du serveur : " + config.get("api.adresse", "http://127.0.0.1:8787"));
    }

    private static java.util.concurrent.ThreadFactory daemon(String name) {
        return r -> {
            Thread t = new Thread(r, name);
            t.setDaemon(true);
            return t;
        };
    }

    /* ───────────────────────── Événements (journal du plugin) ───────────────────────── */

    private void eventLoop() {
        long last = store.lastEvent();
        while (!Thread.currentThread().isInterrupted()) {
            try {
                if (last < 0) {
                    // Premier lancement : on part du présent, sans rejouer le passé du serveur
                    last = api.events(-1).getLong("dernier", 0);
                    store.lastEvent(last);
                }
                DataArray list = api.events(last).getArray("evenements");
                if (list.length() == 0) continue;
                publish(list);
                last = list.getObject(list.length() - 1).getLong("id");
                store.lastEvent(last);
            } catch (InterruptedException e) {
                return;
            } catch (ApiClient.RefusedException e) {
                warnRefused();
                if (pause(30_000)) return;
            } catch (IOException e) {
                if (pause(5_000)) return; // serveur arrêté : nouvel essai, le fil de l'état affiche le statut
            } catch (RuntimeException e) {
                System.err.println("Événements non publiés : " + e);
                if (pause(5_000)) return;
            }
        }
    }

    /** Publie un lot du journal : cartes dans « événements », lignes dans « admin » et « chat ». */
    private void publish(DataArray list) {
        int from = Math.max(0, list.length() - MAX_CATCH_UP);
        List<MessageEmbed> cards = new ArrayList<>();
        StringBuilder admin = new StringBuilder(), chat = new StringBuilder();
        for (int i = from; i < list.length(); i++) {
            DataObject e = list.getObject(i);
            String type = e.getString("type", ""), who = Cards.esc(e.getString("joueur", "?"));
            String text = e.getString("texte", "");
            switch (e.getString("canal", "")) {
                case "evenement" -> {
                    cards.add(Cards.event(e).build());
                    if (cards.size() == 5) flushCards(cards);
                }
                case "admin" -> {
                    String stamp = "<t:" + e.getLong("date", System.currentTimeMillis()) / 1000 + ":T> ";
                    String line = switch (type) {
                        case "admin.commande" -> "🛠 **" + who + "** : `" + text.replace('`', 'ˋ') + "`";
                        case "admin.console"  -> "🖥 **Console** : `" + text.replace('`', 'ˋ') + "`";
                        case "admin.mode"     -> "🎮 **" + who + "** " + Cards.esc(text);
                        default               -> "⚠ **" + who + "** " + Cards.esc(text);
                    };
                    append(channels.admin, admin, stamp + line);
                }
                case "chat" -> {
                    // Un joueur arrive ou s'en va : statut et cartes revus sans attendre le prochain passage
                    if (type.equals("chat.connexion") || type.equals("chat.deconnexion")) askRefresh();
                    String line = switch (type) {
                        case "chat.connexion"   -> "➕ **" + who + "** " + Cards.esc(text);
                        case "chat.deconnexion" -> "➖ **" + who + "** " + Cards.esc(text);
                        default                 -> "⛏ **" + who + "** : " + Cards.esc(text);
                    };
                    append(channels.chat, chat, line);
                }
                default -> { }
            }
        }
        if (from > 0) {
            channels.events.sendMessage("*… " + from + " événements plus anciens n'ont pas été repris (bot absent trop longtemps).*").queue();
        }
        flushCards(cards);
        flush(channels.admin, admin);
        flush(channels.chat, chat);
    }

    private void flushCards(List<MessageEmbed> cards) {
        if (cards.isEmpty()) return;
        channels.events.sendMessageEmbeds(new ArrayList<>(cards)).queue();
        cards.clear();
    }

    /** Ajoute une ligne au message en préparation ; il part quand il approche la limite de Discord. */
    private static void append(TextChannel channel, StringBuilder buffer, String line) {
        String cut = Cards.cut(line, 900);
        if (buffer.length() + cut.length() > 1800) flush(channel, buffer);
        buffer.append(cut).append('\n');
    }

    private static void flush(TextChannel channel, StringBuilder buffer) {
        if (buffer.isEmpty()) return;
        channel.sendMessage(buffer.toString().trim()).queue();
        buffer.setLength(0);
    }

    /* ───────────────────────── État : statut, carte de la ville, cartes des joueurs ───────────────────────── */

    private void stateLoop() {
        long period = config.number("cartes.intervalle-secondes", 15, 5) * 1000L;
        while (!Thread.currentThread().isInterrupted()) {
            try {
                try {
                    lastState = api.state();
                    if (failures >= FAILURES_BEFORE_OFFLINE) System.out.println("Serveur Minecraft de retour.");
                    failures = 0;
                } catch (ApiClient.RefusedException e) {
                    warnRefused();
                    failures++;
                } catch (IOException e) {
                    if (++failures == FAILURES_BEFORE_OFFLINE) System.out.println("Serveur Minecraft injoignable (" + reason(e) + ") : statut hors ligne.");
                }
                boolean online = failures == 0;
                if (online || failures >= FAILURES_BEFORE_OFFLINE) {
                    updateStatus(online);
                    updateCards(online);
                }
            } catch (InterruptedException e) {
                return;
            } catch (RuntimeException e) {
                System.err.println("Cartes non mises à jour : " + e);
            }
            if (waitRefresh(period)) return;
        }
    }

    private void askRefresh() {
        synchronized (wake) {
            refreshAsked = true;
            wake.notifyAll();
        }
    }

    /** Attend le prochain passage, ou moins si un rafraîchissement est demandé ; true : le fil doit s'arrêter. */
    private boolean waitRefresh(long period) {
        try {
            boolean asked;
            synchronized (wake) {
                if (!refreshAsked) wake.wait(period);
                asked = refreshAsked;
                refreshAsked = false;
            }
            if (asked) Thread.sleep(REFRESH_DELAY_MS);
            return false;
        } catch (InterruptedException e) {
            return true;
        }
    }

    private DataObject server() {
        return lastState == null ? DataObject.empty() : lastState.optObject("serveur").orElseGet(DataObject::empty);
    }

    /**
     * Nom affiché dans le statut : celui des réglages du bot, sinon celui donné par le serveur,
     * sinon le nom de la ville, sinon celui de la catégorie des salons. Le nom par défaut de
     * Minecraft (« A Minecraft Server », quand rien n'a été réglé côté serveur) n'est jamais repris.
     */
    private String serverName() {
        String fixed = config.get("statut.nom");
        if (!fixed.isEmpty()) return fixed;
        String given = server().getString("nom", "").trim();
        if (!given.isEmpty() && !given.equalsIgnoreCase(DEFAULT_MOTD)) return given;
        if (lastState != null && !lastState.isNull("citycore")) {
            DataObject city = lastState.getObject("citycore").optObject("ville").orElseGet(DataObject::empty);
            String name = city.getString("nom", "").trim();
            if (city.getBoolean("fondee", false) && !name.isEmpty()) return name;
        }
        return config.get("salons.categorie", "Serveur");
    }

    /**
     * Statut du bot (en temps réel) et nom du salon vocal, renommé dès que Discord le permet
     * (deux changements par tranche de dix minutes : voir {@link RenameBudget}).
     */
    private void updateStatus(boolean online) {
        int count = server().getInt("joueurs", 0);
        String text = online
                ? "🟢 " + serverName() + " · " + count + "/" + server().getInt("max", 0) + (count > 1 ? " joueurs" : " joueur")
                : "🔴 " + serverName() + " · hors ligne";
        text = Cards.cut(text, 100);
        if (!text.equals(presence)) {
            presence = text;
            jda.getPresence().setPresence(online ? OnlineStatus.ONLINE : OnlineStatus.DO_NOT_DISTURB, Activity.customStatus(text));
        }
        if (statusName.isEmpty()) statusName = channels.status.getName();
        long now = System.currentTimeMillis();
        if (text.equals(statusName)) {
            wantedName = "";
            return;
        }
        if (!text.equals(wantedName)) {
            wantedName = text;
            wantedSince = now;
        }
        long[] past = renames();
        if (!RenameBudget.allowed(now, past[0], past[1], wantedSince)) return;
        store.put("statut.renommages", past[1] + "," + now); // retenu d'un lancement à l'autre
        statusName = text;
        wantedName = "";
        channels.status.getManager().setName(text).queue(null, error -> {
            statusName = "?"; // nom réel inconnu : nouvel essai au prochain passage
            System.err.println("Salon de statut non renommé : " + error.getMessage());
        });
    }

    /** Dates des deux derniers changements de nom du salon de statut (0 : aucun), la plus ancienne d'abord. */
    private long[] renames() {
        long[] out = new long[2];
        String[] parts = store.get("statut.renommages").split(",");
        try {
            if (parts.length == 2) {
                out[0] = Long.parseLong(parts[0].trim());
                out[1] = Long.parseLong(parts[1].trim());
            }
        } catch (NumberFormatException ignored) {
            // Mémoire abîmée : comme si rien n'avait été renommé
        }
        return out;
    }

    private void updateCards(boolean online) {
        if (lastState == null) return; // serveur jamais joint depuis le lancement : rien à afficher encore
        DataObject plugin = lastState.isNull("citycore") ? null : lastState.getObject("citycore");
        List<String> names = new ArrayList<>();
        if (online) {
            DataArray connected = server().optArray("enLigne").orElseGet(DataArray::empty);
            for (int i = 0; i < connected.length(); i++) names.add(connected.getString(i));
        }

        // La ville
        DataObject city = plugin == null ? null : plugin.optObject("ville").orElse(null);
        EmbedBuilder card = Cards.city(city, serverName(), names, config.color("cartes.couleur", 0xF1CF5E))
                .setFooter(online ? "Serveur en ligne" : "Serveur hors ligne — dernier état connu");
        String content = card.build().toData().toString();
        if (!content.equals(cityContent)) {
            upsert(channels.city, "carte.ville", card.setTimestamp(Instant.now()).build());
            cityContent = content;
        }

        // Les joueurs
        if (plugin == null) return;
        DataArray players = plugin.optArray("joueurs").orElseGet(DataArray::empty);
        String head = config.get("cartes.tete", DEFAULT_HEAD);
        if (head.equalsIgnoreCase("aucune")) head = "";
        for (int i = 0; i < players.length(); i++) {
            DataObject player = players.getObject(i);
            String uuid = player.getString("uuid", "");
            if (uuid.isEmpty()) continue;
            EmbedBuilder one = Cards.player(player, names.contains(player.getString("nom", "")), head, i + 1);
            String now = one.build().toData().toString();
            if (now.equals(playerContent.get(uuid))) continue;
            upsert(channels.players, "carte.joueur." + uuid, one.build());
            playerContent.put(uuid, now);
        }
    }

    /** Modifie le message enregistré sous cette clé, ou l'envoie s'il n'existe pas (ou a été supprimé). */
    private void upsert(TextChannel channel, String key, MessageEmbed embed) {
        String id = store.get(key);
        if (!id.isEmpty()) {
            try {
                channel.editMessageEmbedsById(id, embed).complete();
                return;
            } catch (ErrorResponseException e) {
                if (e.getErrorResponse() != ErrorResponse.UNKNOWN_MESSAGE) throw e;
            }
        }
        store.put(key, channel.sendMessageEmbeds(embed).complete().getId());
    }

    /* ───────────────────────── Salon de discussion : /chat seulement ───────────────────────── */

    /**
     * Un message tapé à la main dans le salon de discussion est retiré aussitôt : Discord ne permet
     * pas d'y autoriser la commande en interdisant d'écrire (une commande exige le droit d'envoyer
     * des messages), alors le bot fait le ménage. Son contenu n'est pas lu.
     */
    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (!event.isFromGuild() || event.getChannel().getIdLong() != channels.chat.getIdLong()) return;
        if (event.getAuthor().getIdLong() == jda.getSelfUser().getIdLong()) return;
        if (!event.getGuild().getSelfMember().hasPermission(channels.chat, Permission.MESSAGE_MANAGE)) {
            if (!cleanupWarned) {
                cleanupWarned = true;
                System.err.println("Message tapé dans #" + channels.chat.getName() + " non supprimé : le bot n'y a pas "
                        + "le droit « Gérer les messages ».");
            }
            return;
        }
        event.getMessage().delete().queue(null, error -> { });
        long now = System.currentTimeMillis();
        if (now - hintAt < HINT_EVERY_MS) return;
        hintAt = now;
        // Rappel discret, retiré au bout de quelques secondes
        channels.chat.sendMessage("✏️ Ici, on écrit aux joueurs avec la commande `/chat`.")
                .queue(hint -> hint.delete().queueAfter(8, TimeUnit.SECONDS, null, error -> { }), error -> { });
    }

    /* ───────────────────────── /chat : de Discord vers le jeu ───────────────────────── */

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("chat")) return;
        OptionMapping option = event.getOption("message");
        String message = option == null ? "" : option.getAsString().replaceAll("\\s+", " ").trim();
        if (message.isEmpty()) {
            event.reply("Message vide.").setEphemeral(true).queue();
            return;
        }
        String author = event.getMember() != null ? event.getMember().getEffectiveName() : event.getUser().getEffectiveName();
        String text = Cards.cut(message, 300);
        event.deferReply(true).queue();
        worker.execute(() -> {
            try {
                int players = api.chat(author, text);
                channels.chat.sendMessage("💬 **" + Cards.esc(author) + "** : " + Cards.esc(text)).queue();
                event.getHook().sendMessage(players == 0
                        ? "✅ Message envoyé, mais aucun joueur n'est connecté pour le lire."
                        : "✅ Message affiché en jeu (" + players + (players > 1 ? " joueurs connectés)." : " joueur connecté).")).queue();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (IOException | RuntimeException e) {
                event.getHook().sendMessage("❌ Le serveur Minecraft ne répond pas : message non transmis.").queue();
            }
        });
    }

    /* ───────────────────────── Outils ───────────────────────── */

    private synchronized void warnRefused() {
        if (refusalWarned) return;
        refusalWarned = true;
        System.err.println("L'API du serveur refuse le jeton : api.jeton doit être la valeur de discord.jeton "
                + "(config/citycore_companion.toml du serveur Minecraft).");
    }

    /** Pourquoi l'API n'a pas répondu, en clair (Java ne donne souvent aucun texte pour une connexion refusée). */
    private String reason(IOException e) {
        String address = config.get("api.adresse", "http://127.0.0.1:8787");
        if (e instanceof java.net.ConnectException) {
            return "connexion refusée par " + address + " : rien n'écoute à cette adresse, l'API du mod compagnon "
                    + "n'est pas ouverte ou pas sur cette machine";
        }
        if (e instanceof java.net.http.HttpTimeoutException) return "aucune réponse de " + address;
        return e.getMessage() == null ? e.getClass().getSimpleName() + " vers " + address : e.getMessage();
    }

    /** Attend ; renvoie true si le fil doit s'arrêter. */
    private static boolean pause(long ms) {
        try {
            Thread.sleep(ms);
            return false;
        } catch (InterruptedException e) {
            return true;
        }
    }
}
