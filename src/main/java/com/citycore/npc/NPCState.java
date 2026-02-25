package com.citycore.npc;

public enum NPCState {
    WANDERER,  // Spawné, pas encore dans la ville
    ARRIVED,   // Entré dans la ville au moins une fois
    ASSIGNED   // Assigné à un bâtiment
}