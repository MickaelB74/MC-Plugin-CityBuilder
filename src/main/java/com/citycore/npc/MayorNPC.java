package com.citycore.npc;

import org.bukkit.Location;

public class MayorNPC {

    private Location spawnLocation;

    public MayorNPC(Location spawnLocation) {
        this.spawnLocation = spawnLocation;
    }

    public Location getSpawnLocation() {
        return spawnLocation;
    }
}