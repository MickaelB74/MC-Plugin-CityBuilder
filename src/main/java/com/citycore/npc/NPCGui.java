package com.citycore.npc;

import org.bukkit.entity.Player;

public interface NPCGui {
    void open(Player player);
    String getTitle();
    void handleClick(Player player, int slot);
}