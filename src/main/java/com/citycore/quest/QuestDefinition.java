package com.citycore.quest;

import java.util.List;

public record QuestDefinition(
        String id,
        String description,
        QuestType type,
        List<QuestObjective> objectives,
        QuestReward reward,
        boolean isSpecial
) {}