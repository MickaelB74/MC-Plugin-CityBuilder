package com.citycore.discordbot;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.utils.MarkdownSanitizer;
import net.dv8tion.jda.api.utils.data.DataArray;
import net.dv8tion.jda.api.utils.data.DataObject;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

/**
 * Les cartes (embeds) du bot : la grande carte de la ville, une carte par joueur, et la carte d'un
 * événement. Les constructeurs rendent une carte sans date : l'appelant compare son contenu à
 * celui de la carte en place et ne la modifie (en la datant) que s'il a changé.
 */
final class Cards {

    private static final int GREEN = 0x57F287, GREY = 0x80848E, RED = 0xED4245, ORANGE = 0xE67E22,
            GOLD = 0xF1CF5E, BLUE = 0x3498DB, TEAL = 0x1ABC9C, PURPLE = 0x9B59B6, BLURPLE = 0x5865F2;
    private static final NumberFormat NUMBERS = NumberFormat.getIntegerInstance(Locale.FRANCE);

    private Cards() {
    }

    /* ───────────────────────── Ville ───────────────────────── */

    /**
     * @param city   objet « ville » de l'état du plugin ; null : plugin sans état pour l'instant
     * @param online joueurs connectés
     */
    static EmbedBuilder city(DataObject city, String serverName, List<String> online, int color) {
        EmbedBuilder e = new EmbedBuilder().setColor(color);
        if (city == null || !city.getBoolean("fondee", false)) {
            return e.setTitle("🏰 " + serverName)
                    .setDescription("La ville n'est pas encore fondée.\n"
                            + "Retrouvez la **Clé de la ville** et posez-la là où Valmont renaîtra.")
                    .addField("Joueurs connectés", players(online), false);
        }
        e.setTitle("🏰 " + esc(city.getString("nom", serverName)));
        StringBuilder head = new StringBuilder("**" + esc(city.getString("palier", "")) + "** · niveau "
                + city.getInt("niveau", 1) + "\n*" + esc(city.getString("chapitre", "")) + "*");
        if (city.getBoolean("siegeEnCours", false)) head.append("\n\n⚔ **Siège en cours !** La ville se défend.");
        if (city.getBoolean("raidEnCours", false)) head.append("\n\n⚔ **Expédition en cours** : un raid est parti.");
        e.setDescription(head.toString());

        int quests = city.getInt("quetes", 0), questsTotal = city.getInt("quetesTotal", 0);
        int satisfaction = city.getInt("satisfaction", -1);
        int recruits = city.getInt("recrues", 0);

        e.addField("💰 Caisse", coins(city.getInt("caisse", 0)), true);
        e.addField("🗺 Parcelles", city.getInt("parcelles", 0) + " / " + city.getInt("parcellesMax", 0), true);
        e.addField("🏗 Bâtiments", String.valueOf(city.getInt("batiments", 0)), true);

        e.addField("🏠 Habitants", city.getInt("habitants", 0) + " / " + city.getInt("lits", 0) + " lits", true);
        e.addField("😊 Satisfaction", satisfaction < 0 ? "—" : satisfaction + " %", true);
        e.addField("🔨 Artisans", String.valueOf(city.getInt("artisans", 0)), true);

        e.addField("🛡 Garde", city.getInt("garde", 0) + (recruits > 0 ? " (+" + recruits + " en formation)" : ""), true);
        int won = city.getInt("siegesGagnes", 0), lost = city.getInt("siegesPerdus", 0);
        e.addField("⚔ Sièges", won + (won > 1 ? " repoussés · " : " repoussé · ") + lost + (lost > 1 ? " perdus" : " perdu"), true);
        e.addField("🏆 Raids victorieux", String.valueOf(city.getInt("raidsGagnes", 0)), true);

        if (questsTotal > 0) {
            e.addField("📜 Quêtes du chapitre", bar(quests, questsTotal, 10) + "  " + quests + " / " + questsTotal, false);
        }
        DataArray calendar = city.optArray("calendrier").orElse(null);
        if (calendar != null && calendar.length() > 0) {
            StringBuilder lines = new StringBuilder();
            for (int i = 0; i < calendar.length(); i++) lines.append(esc(calendar.getString(i).trim())).append('\n');
            e.addField("📅 Calendrier", cut(lines.toString().trim(), 1000), false);
        }
        e.addField("Joueurs connectés (" + online.size() + ")", players(online), false);
        return e;
    }

    private static String players(List<String> online) {
        if (online.isEmpty()) return "*Personne pour l'instant*";
        StringBuilder b = new StringBuilder();
        for (String name : online) b.append(b.isEmpty() ? "" : ", ").append(esc(name));
        return cut(b.toString(), 1000);
    }

    /* ───────────────────────── Joueurs ───────────────────────── */

    /**
     * Carte d'un joueur : tête en vignette, présence, puis niveau, métier et bourse sur une ligne,
     * barre d'expérience en dessous, rang au classement en pied de carte.
     *
     * @param player une entrée de la liste « joueurs » de l'état du plugin
     * @param head   adresse de l'image de la tête ({nom}, {uuid}), ou vide pour ne pas en afficher
     * @param rank   rang au classement (niveau, puis expérience), à partir de 1
     */
    static EmbedBuilder player(DataObject player, boolean online, String head, int rank) {
        String name = player.getString("nom", "?");
        int level = player.getInt("niveau", 1), xp = player.getInt("xp", 0), needed = player.getInt("xpRequis", 100);
        String job = player.getString("metier", "");
        long seen = player.getLong("vuLe", 0);
        int percent = needed <= 0 ? 0 : (int) Math.max(0, Math.min(100, 100L * xp / needed));
        int left = Math.max(0, needed - xp);
        String medal = switch (rank) {
            case 1 -> "🥇 ";
            case 2 -> "🥈 ";
            case 3 -> "🥉 ";
            default -> "";
        };

        EmbedBuilder e = new EmbedBuilder()
                .setColor(online ? GREEN : GREY)
                .setTitle(medal + esc(name))
                .setDescription(online ? "🟢 **En ligne**"
                        : seen > 0 ? "⚫ Hors ligne · vu <t:" + seen / 1000 + ":R>" : "⚫ Hors ligne")
                .addField("⭐ Niveau", "**" + level + "**", true)
                .addField("🔨 Métier", job.isBlank() ? "*Aucun*" : esc(job), true)
                .addField("💰 Bourse", coins(player.getInt("bourse", 0)), true)
                .addField("✨ Expérience · " + percent + " %", bar(xp, needed, 16) + "\n**" + NUMBERS.format(xp) + "** / "
                        + NUMBERS.format(needed) + " XP · encore " + NUMBERS.format(left) + " avant le niveau " + (level + 1), false)
                .setFooter(rank == 1 ? "1er du classement" : rank + "e du classement");
        if (!head.isBlank()) {
            try {
                // Pseudo encodé : ceux des joueurs Bedrock peuvent contenir un point ou une espace
                String encoded = URLEncoder.encode(name, StandardCharsets.UTF_8).replace("+", "%20");
                e.setThumbnail(head.replace("{nom}", encoded).replace("{uuid}", player.getString("uuid", "")));
            } catch (IllegalArgumentException invalidAddress) {
                // Adresse mal formée dans cartes.tete : carte sans image
            }
        }
        return e;
    }

    /* ───────────────────────── Événements ───────────────────────── */

    /** Carte d'un événement de la ville ; la couleur vient de la famille de son type (« siege.victoire »). */
    static EmbedBuilder event(DataObject event) {
        String type = event.getString("type", "");
        EmbedBuilder e = new EmbedBuilder()
                .setColor(eventColor(type))
                .setTitle(cut(event.getString("titre", "Événement"), 250))
                .setTimestamp(Instant.ofEpochMilli(event.getLong("date", System.currentTimeMillis())));
        String text = event.getString("texte", "");
        if (!text.isBlank()) e.setDescription(cut(esc(text), 2000));
        return e;
    }

    private static int eventColor(String type) {
        if (type.endsWith(".victoire")) return GREEN;
        if (type.endsWith(".defaite") || type.endsWith(".mort")) return RED;
        String family = type.contains(".") ? type.substring(0, type.indexOf('.')) : type;
        return switch (family) {
            case "siege"    -> RED;
            case "raid"     -> ORANGE;
            case "ville"    -> GOLD;
            case "batiment" -> BLUE;
            case "artisan"  -> TEAL;
            case "habitant" -> TEAL;
            case "garde"    -> BLURPLE;
            case "joueur"   -> GREEN;
            case "quete"    -> PURPLE;
            default         -> GREY;
        };
    }

    /* ───────────────────────── Outils ───────────────────────── */

    /** Texte venu du jeu, sans effet de mise en forme Discord. */
    static String esc(String s) {
        return MarkdownSanitizer.escape(s);
    }

    static String cut(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    private static String coins(int amount) {
        return NUMBERS.format(amount) + " coins";
    }

    /** Barre de progression en {@code cells} cases. */
    private static String bar(int value, int total, int cells) {
        int filled = total <= 0 ? 0 : (int) Math.max(0, Math.min(cells, Math.floor((double) cells * value / total)));
        return "▰".repeat(filled) + "▱".repeat(cells - filled);
    }
}
