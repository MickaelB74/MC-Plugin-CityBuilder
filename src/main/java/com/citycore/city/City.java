package com.citycore.city;

public class City {

    private final String name;
    private final int    level;
    private final int    coins;
    private final int    claimedChunks;
    private final int    maxChunks;
    private final int    residentCount;

    public City(String name, int level, int coins, int claimedChunks, int maxChunks, int residentCount) {
        this.name          = name;
        this.level         = level;
        this.coins         = coins;
        this.claimedChunks = claimedChunks;
        this.maxChunks     = maxChunks;
        this.residentCount = residentCount;
    }

    public String getName()         { return name; }
    public int    getLevel()        { return level; }
    public int    getCoins()        { return coins; }
    public int    getClaimedChunks(){ return claimedChunks; }
    public int    getMaxChunks()    { return maxChunks; }
    public int    getResidentCount(){ return residentCount; }
}