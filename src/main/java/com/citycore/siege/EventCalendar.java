package com.citycore.siege;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Calendrier des événements de la ville (phase 4 du plan « Valmont vivante »), en heure réelle :
 * section {@code evenements} de config.yml. Chaque créneau (type, jours, heure, joueurs minimum)
 * est annoncé {@code annonce-minutes} avant, puis lancé à l'heure dite s'il y a assez de joueurs
 * connectés. Les sièges sont le premier type d'événement ; les fêtes (phase 7) s'y ajouteront.
 */
public final class EventCalendar {

    /** Un type d'événement du calendrier (siège, fête…). */
    public interface Event {
        /** « Siège », « Fête »… */
        String label();

        /** Raison pour laquelle il ne peut pas avoir lieu (palier trop bas…), ou null. */
        String readiness();

        /** Lance l'événement ; null si c'est fait, sinon la raison du refus. */
        String start();

        boolean running();

        /** Arrête l'événement en cours ; false s'il n'y en avait pas. */
        boolean stop();
    }

    /** Un créneau du planning. */
    private record Slot(String type, Set<DayOfWeek> days, LocalTime time, int minPlayers) {}

    private static final Map<String, DayOfWeek> DAYS = Map.of(
            "lundi", DayOfWeek.MONDAY, "mardi", DayOfWeek.TUESDAY, "mercredi", DayOfWeek.WEDNESDAY,
            "jeudi", DayOfWeek.THURSDAY, "vendredi", DayOfWeek.FRIDAY, "samedi", DayOfWeek.SATURDAY,
            "dimanche", DayOfWeek.SUNDAY);
    private static final String[] DAY_NAMES = {"lundi", "mardi", "mercredi", "jeudi", "vendredi", "samedi", "dimanche"};

    private final JavaPlugin plugin;
    private final Map<String, Event> events = new LinkedHashMap<>();
    private final List<Slot> slots = new ArrayList<>();
    private final Set<String> done = new HashSet<>(); // annonces et lancements déjà faits (créneau + date)
    private ZoneId zone = ZoneId.systemDefault();
    private int announceMinutes = 5;

    /** Report d'un événement prévu (une expédition est en cours) : raison, ou null. */
    private java.util.function.Supplier<String> postponer = () -> null;
    /** Durée d'un report (PLAN_RAIDS §6.2 : 30 min). */
    private static final long POSTPONE_TICKS = 30L * 60 * 20;

    public EventCalendar(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void setPostponer(java.util.function.Supplier<String> postponer) {
        this.postponer = postponer;
    }

    /** Déclare un type d'événement : « siege » → le gestionnaire des sièges. */
    public void register(String type, Event event) {
        events.put(type, event);
    }

    public Event event(String type) {
        return type == null ? null : events.get(type.toLowerCase(Locale.ROOT));
    }

    public Set<String> types() {
        return events.keySet();
    }

    /** Lit le planning, puis le vérifie toutes les 20 secondes. */
    public void start() {
        reload();
        Bukkit.getScheduler().runTaskTimer(plugin, this::check, 100L, 400L);
    }

    /** Relit la section evenements directement dans config.yml. */
    public void reload() {
        File file = new File(plugin.getDataFolder(), "config.yml");
        ConfigurationSection root = file.exists() ? YamlConfiguration.loadConfiguration(file) : plugin.getConfig();
        ConfigurationSection s = root.getConfigurationSection("evenements");
        slots.clear();
        if (s == null) return;
        try {
            zone = ZoneId.of(s.getString("fuseau", "Europe/Paris"));
        } catch (Exception e) {
            plugin.getLogger().warning("[Événements] fuseau inconnu : " + s.getString("fuseau") + " — heure du serveur utilisée.");
            zone = ZoneId.systemDefault();
        }
        announceMinutes = Math.max(0, s.getInt("annonce-minutes", 5));
        for (Map<?, ?> entry : s.getMapList("planning")) {
            String type = String.valueOf(entry.get("type")).trim().toLowerCase(Locale.ROOT).replace("è", "e");
            Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
            Object rawDays = entry.get("jours");
            List<?> dayList = rawDays instanceof List<?> l ? l : List.of(String.valueOf(rawDays));
            for (Object d : dayList) {
                String name = String.valueOf(d).trim().toLowerCase(Locale.ROOT);
                if (name.equals("tous")) days.addAll(EnumSet.allOf(DayOfWeek.class));
                else if (DAYS.containsKey(name)) days.add(DAYS.get(name));
                else plugin.getLogger().warning("[Événements] jour inconnu : " + d);
            }
            LocalTime time;
            try {
                time = LocalTime.parse(String.valueOf(entry.get("heure")).trim(), DateTimeFormatter.ofPattern("H:mm"));
            } catch (Exception e) {
                plugin.getLogger().warning("[Événements] heure invalide : " + entry.get("heure") + " (format HH:MM)");
                continue;
            }
            Object min = entry.get("joueurs-min");
            int minPlayers = min instanceof Number n ? n.intValue() : 1;
            if (days.isEmpty()) continue;
            slots.add(new Slot(type, days, time, Math.max(1, minPlayers)));
        }
    }

    /** Annonces et lancements des créneaux (fenêtre d'une minute, une seule fois chacun). */
    private void check() {
        ZonedDateTime now = ZonedDateTime.now(zone);
        for (Slot slot : slots) {
            Event event = event(slot.type());
            if (event == null) continue;
            for (LocalDate date : List.of(now.toLocalDate(), now.toLocalDate().plusDays(1))) {
                if (!slot.days().contains(date.getDayOfWeek())) continue;
                ZonedDateTime at = date.atTime(slot.time()).atZone(zone);
                String id = slot.type() + "@" + at.toLocalDateTime();
                ZonedDateTime announceAt = at.minusMinutes(announceMinutes);
                if (announceMinutes > 0 && inWindow(now, announceAt) && done.add(id + ":annonce")) announce(slot, event);
                if (inWindow(now, at) && done.add(id + ":debut")) launch(slot, event);
            }
        }
    }

    private static boolean inWindow(ZonedDateTime now, ZonedDateTime at) {
        return !now.isBefore(at) && now.isBefore(at.plusSeconds(60));
    }

    private void announce(Slot slot, Event event) {
        if (event.readiness() != null || event.running()) return; // pas encore de sièges : rien à annoncer
        int online = Bukkit.getOnlinePlayers().size();
        String need = online < slot.minPlayers()
                ? " §8(il faudra au moins " + slot.minPlayers() + " joueurs connectés, vous êtes " + online + ")" : "";
        Bukkit.broadcastMessage("§c⚔ " + event.label() + " dans " + announceMinutes + " minute" + (announceMinutes > 1 ? "s" : "")
                + " ! §7Préparez la défense de Valmont." + need);
        for (Player p : Bukkit.getOnlinePlayers()) p.playSound(p.getLocation(), Sound.BLOCK_BELL_USE, 1f, 0.8f);
    }

    private void launch(Slot slot, Event event) {
        if (event.running() || event.readiness() != null) return;
        int online = Bukkit.getOnlinePlayers().size();
        if (online < slot.minPlayers()) {
            if (online > 0) {
                Bukkit.broadcastMessage("§7⚔ " + event.label() + " annulé : " + online + " joueur" + (online > 1 ? "s" : "")
                        + " connecté" + (online > 1 ? "s" : "") + ", il en faut " + slot.minPlayers() + ".");
            }
            return;
        }
        String wait = postponer.get();
        if (wait != null) {
            // Une expédition est partie : l'événement attend son retour (nouvel essai dans 30 min)
            Bukkit.broadcastMessage("§c⚔ " + event.label() + " reporté de 30 minutes : §7" + wait + ".");
            Bukkit.getScheduler().runTaskLater(plugin, () -> launch(slot, event), POSTPONE_TICKS);
            return;
        }
        String err = event.start();
        if (err != null) plugin.getLogger().info("[Événements] " + event.label() + " non lancé : " + err);
    }

    /* =========================
       PLANNING (/city event list)
       ========================= */

    /** Lignes du planning : chaque créneau et le prochain rendez-vous. */
    public List<String> listing() {
        List<String> lines = new ArrayList<>();
        if (slots.isEmpty()) {
            lines.add("§7📅 Aucun événement au planning §8(section evenements de config.yml)");
            return lines;
        }
        ZonedDateTime now = ZonedDateTime.now(zone);
        lines.add("§6📅 Calendrier de Valmont §8(" + zone.getId() + ")");
        for (Slot slot : slots) {
            Event event = event(slot.type());
            String label = event != null ? event.label() : slot.type() + " §c(type inconnu)";
            ZonedDateTime next = next(slot, now);
            String why = event == null ? null : event.readiness();
            lines.add("§7• §f" + label + " §7— " + days(slot.days()) + " à §f" + slot.time()
                    + " §8(" + slot.minPlayers() + " joueur" + (slot.minPlayers() > 1 ? "s" : "") + " min.)");
            if (next != null) {
                lines.add("  §7Prochain : §f" + DAY_NAMES[next.getDayOfWeek().getValue() - 1] + " "
                        + next.format(DateTimeFormatter.ofPattern("dd/MM 'à' HH:mm")) + " §8(dans " + until(now, next) + ")"
                        + (why != null ? " §8— " + why : ""));
            }
        }
        return lines;
    }

    /** Prochain début d'un créneau (dans les 8 jours), ou null. */
    private ZonedDateTime next(Slot slot, ZonedDateTime now) {
        for (int i = 0; i <= 7; i++) {
            LocalDate date = now.toLocalDate().plusDays(i);
            if (!slot.days().contains(date.getDayOfWeek())) continue;
            ZonedDateTime at = date.atTime(slot.time()).atZone(zone);
            if (at.isAfter(now)) return at;
        }
        return null;
    }

    private static String days(Set<DayOfWeek> days) {
        if (days.size() == 7) return "tous les jours";
        List<String> names = new ArrayList<>();
        for (DayOfWeek d : days) names.add(DAY_NAMES[d.getValue() - 1]);
        return String.join(", ", names);
    }

    /** « 2 j 3 h », « 3 h 12 min », « 4 min ». */
    private static String until(ZonedDateTime now, ZonedDateTime at) {
        Duration d = Duration.between(now, at);
        long days = d.toDays(), hours = d.toHoursPart(), minutes = d.toMinutesPart();
        if (days > 0) return days + " j " + hours + " h";
        if (hours > 0) return hours + " h " + minutes + " min";
        return Math.max(1, minutes) + " min";
    }
}
