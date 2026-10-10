package com.citycore.quest.city;

import org.bukkit.ChatColor;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

import java.util.List;

/**
 * Progression des quêtes de chasse de la ville (KILL_MOBS), tous joueurs confondus.
 */
public class CityQuestListener implements Listener {

    private final CityQuestManager questManager;

    public CityQuestListener(CityQuestManager questManager) {
        this.questManager = questManager;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        boolean hostile = event.getEntity() instanceof Enemy;
        List<CityQuest> progressed = questManager.onEntityKilled(
                event.getEntityType(), killer != null, hostile);

        if (killer == null) return;
        for (CityQuest quest : progressed) {
            int cur = Math.min(questManager.getProgress(quest.id()), questManager.target(quest));
            com.citycore.util.TypewriterUtil.actionBar(killer, "§6🏰 " + ChatColor.stripColor(quest.displayName())
                    + " §7— §f" + cur + "§7/§f" + questManager.target(quest));
        }
    }
}
