package com.citycore.npc;

import com.citycore.quest.personal.NPCPersonalQuest;
import com.citycore.quest.personal.PersonalQuestDefinition.TriggerType;
import org.bukkit.Material;
import org.bukkit.block.Biome;

import java.util.List;
import java.util.Map;

import static com.citycore.quest.personal.PersonalQuestDefinition.TriggerType.*;

public enum CityNPC {

    MAYOR(
            "citycore_mayor",
            "§6Alderic",
            "Maire de la ville",
            "alderic",
            "ewogICJ0aW1lc3RhbXAiIDogMTc3MTQ4OTcyNDg1OCwKICAicHJvZmlsZUlkIiA6ICI2NDg4Y2VjMjc4OGQ0MTI2OTk5NWMyMmY4OTdmMzA4OSIsCiAgInByb2ZpbGVOYW1lIiA6ICJBc3BlbjA1MyIsCiAgInNpZ25hdHVyZVJlcXVpcmVkIiA6IHRydWUsCiAgInRleHR1cmVzIiA6IHsKICAgICJTS0lOIiA6IHsKICAgICAgInVybCIgOiAiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS9kMTUzNTk3ZmE2M2U2MTJhMGQ2YWY2OWE0ZTFiMDFlY2YxM2M4ZGI0Y2E5ZThkYzdkZmZmODQ5YjBjMTAzZTlmIgogICAgfQogIH0KfQ==",
            "A60BzbuZYlRE2SbwCz02jy+hnBG1o2D8QMl8IcfD994ft2CBTWEhAdhWN0Fey78EXKMTTRFmyGQweLDlV/29lPIRwSLhy77gb9fqkYnR1LaLykUAkiBJ0VBHJW7ZAYAAmOJE5ehoo/fwWADNwlVAu8oZzXGJhhf8goCiGnTBuRRXI6rkyMdMGpjkDqxATuew/0mtxNAGLVIORoHNhbBj1p3ihaM9By4L/A39oN/WthMf+rMQNwhLCuMBYXPI+//ShFhDJl/lDTIm7nvsCk/1vVVDEuULosjWqlYPf2r+r3hDAMIE5StyDk9ypxImHnDe3D2cb5DFNBtZKHLYyIq8enxXxotHcMRjZeaHg4KwajswshsMh07yvXO0x46nfF6RFcMEbjL2u7eRW4Y1bJjKVkxTZ9hmM6C9oHHYKvHRAT1cVo6YxGU8/fukthrZvD0BlQAjsDdwBGW/p2ex/dQtweHWDlamWeqhNIUBIdMF9qlWwNX6f24clecUIhEbXxXdDrupXNZuBrBtUkzicbPrC+PVJaKT0qCO9S2fyHW89VznqAK3whv3CVBGvqpq242IucuTHJpwDet1ctXXWw97ebSfvP//Cg1f9nn5mrE81OB5G5BEsS1Y32KRviQ5tfZPnPsU0SRXatoHzNp0RozrtF2B53SA5eII7ViNFpe/mIY=",
            Map.of(
                    "first_meeting", List.of(
                            "§6Alderic §f: §oBonjour, aventurier. Je suis Alderic, maire de cette ville.",
                            "§6Alderic §f: §oNous avons grand besoin de bras courageux pour la faire prospérer.",
                            "§6Alderic §f: §oParlez-moi si vous souhaitez consulter l'état de la ville,",
                            "§6Alderic §f: §oou pour agrandir notre territoire."
                    )
            ),
            Map.of()
    ),

    STONEMASON(
            "citycore_stonemason",
            "§7Brennan",
            "Tailleur de pierre",
            "brennan",
            "ewogICJ0aW1lc3RhbXAiIDogMTczNTM0NDA5MTg5MCwKICAicHJvZmlsZUlkIiA6ICJiODU0NWMxMDlhZjE0ZGRjYmY4ZjhmZjg4ZTU2NzI4OSIsCiAgInByb2ZpbGVOYW1lIiA6ICJQdGFrb3B5c2tDWiIsCiAgInNpZ25hdHVyZVJlcXVpcmVkIiA6IHRydWUsCiAgInRleHR1cmVzIiA6IHsKICAgICJTS0lOIiA6IHsKICAgICAgInVybCIgOiAiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS84MmRhNGY1NDgxODE5MjcxMGE3OGFjNDVlNjI3Y2ExNmJlZjg4MDQzYzhiMTNhOTU4NjM2ODU1NGMwOGEwZTA2IgogICAgfQogIH0KfQ==",
            "Eu8c+SC9BCYH5qDpU/aALFEqRXeXa3nifZkCZAAL9K9XZ6yM++mZm4jWBNojyRxMWM32mRoFEJRY7rOvOtMwkUmIEm7XCdVhkEK36cPQ1aT0wLksYbeHpFok70Om6fOiFVXo3jxcAk13pd1xfpNjp4d0YfiqUehqW5/6nSXa3/ZqlMgNKuF8ZenXZ2UV8dSq6SY2x/xmGQGKbXWyygI9MLviXF5Hq7nCSQBn6dXGDpTu/HqTW+Mg53T4H8ogi6aGq76WXhquR09bkR/uHsEzyVA40yB3Q6gh9qPegTUCC+1W+xsiqXqzcTw/P/fGzs4nPWetM57a6tu+y4aedqkTI8TFy42YCV1AOIk3nvUT+4w2LAy0iAzlj1bhdQdnRiffHhpl6WD6iKm1LRolK/XpH6a03Dcq+Uay7b6Z4Hg5Vf8Yi3fsGlu5jpyH3nI4ylF9Aj33thOguNG0XQKIYL0xyvgO7IwPLmy0qMfPlEmklNp8JfJbTF52/UCmOGabDa/kDDX+B+aOzmYeel/xjdSwa3GjoziUllMTgxTucoH7QneX8Nqj/uVxxZdjKIITTZMblo6gnQ73HzufIOcc2FgHOr3elzTAXZJydPser4FH7VF8Lb57hfPYTkK/9eNHQi9KQ81b0yjXVgdGzkQIQEDm8UW+h3rh7kEOu09BlpNhVkQ=",
            Map.of(
                    "first_meeting", List.of(
                            "§7Brennan §f: §o*grogne* Encore un nouveau visage...",
                            "§7Brennan §f: §oJe suis completement perdu, vous pouvez m'aider ?"
                    ),
                    "city_arrival", List.of(
                            "§7Brennan §f: §o*regarde autour de lui* Pas mal, cette ville...",
                            "§7Brennan §f: §oJ'vais m'installer ici un moment. Si vous avez des pierres, amenez-les."
                    ),
                    "building_assign", List.of(
                            "§7Brennan §f: §oAh, enfin un atelier digne de ce nom !",
                            "§7Brennan §f: §oVenez me voir ici quand vous voulez vendre vos blocs."
                    )
            ),
            Map.of()
    ),

    JACKSPARROW(
            "citycore_jacksparrow",
            "§3Jack Sparrow",
            "Pirate des mers",
            "jacksparrow",
            "ewogICJ0aW1lc3RhbXAiIDogMTcwOTM5NjgzNTkyMCwKICAicHJvZmlsZUlkIiA6ICI4ZGUyNDAzYTEyMjU0ZmFkOTM1OTYxYWFlYmQwNGUyOSIsCiAgInByb2ZpbGVOYW1lIiA6ICJkZXNydHB1bWEiLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYTAzNTRmZjAzZGYwZjM0N2RiYmI3NjU1NjE2MDQzYjRiYzdiOWUxNjc2ODQxNDEwMjdhNWZmZDNhNjMwOTIzOSIKICAgIH0KICB9Cn0=",
            "fMOqMuG/FbKWFZnE5/RVmMpk4d+kftHEA0NMEjdO3QPeEGF6eLemb7gvm9fHKc4MBG3mpGVl2XPA4UAl25/vpspC0JlnJvLwwOnpwPieyy9ruGTppu0dNs++eR23EIrxz54fx+7cd+amz0uE/8snrsBNJ9jBAE8sG7CVLUUvAqVqUlAgEQkwisRQ6uFLhxgFLFIIBRGCXv9XURlSLsLwD4s8HyaHFNIpjWrqnlR5wy9OeRJ/10BijH51Ohi74zRdZmkJ0TCc0YrvSohaxzNNL61HykgYrxZSOHHuhABcBo6F+w6LohzOcV52FyQelHnyX/dWicY+2INYmQQaMUvTeEzNY/AdQ5LMQTmFqq2lraRQPMcpg11wjf/EMHva3MHDmYtHVxIN90vsLavt7pgYEx+KTLhUTqd1DvmQa1ESUdYaiyPRv1XU622okZrySQvHB9cvTgPKJo2Kj+BAK9XhtWCxS3XQo1N05DCPMIBOcz9UB767eT3jcQ9/CckDa7jIswOQsqmAqGlQTJhzr6QBDcvzYim/W+ue9w25H555zje/9RNYEa1IM9r/5ZpY1cuilyMpxKIGmc1nmRZvziPeNeKmQyiue8X2JE0ub0vJE43Ypg0vWeVA2S31JHJmtb0MNL3v2FvrUNy/Kg50INKSDNebjWt83s5my5KDFPzjjuQ=",
            Map.of(
                    "first_meeting", List.of(
                            "§3Jack §f: §oHHHAAARRRGGGGGG !!! Salut c'est Jack Sparrow ! Le vrai !",
                            "§3Jack §f: §oJ'ai besoin de 50€ en coupon western union... Tu as ca sur toi ?"
                    ),
                    "city_arrival", List.of(
                            "§3Jack §f: §o*hume l'air* Ah, une ville ! Civilisation... relatif.",
                            "§3Jack §f: §oJ'vais rester dans le coin. Pour les affaires, vous comprenez."
                    ),
                    "building_assign", List.of(
                            "§3Jack §f: §oUn port ! Enfin, presque... ça fera l'affaire.",
                            "§3Jack §f: §oVenez me voir ici pour tout ce qui vient de la mer."
                    )
            ),
            // ── Quêtes personnelles de Jack, débloquées par niveau ──────────
            Map.of(
                    1, List.of(
                            NPCPersonalQuest.of(
                                    "jack_reach_deep_ocean",
                                    "§9🌊 Abyssal",
                                    "Plonger à Y ≤ -40 dans un océan profond.",
                                    Material.KELP,
                                    MOVE,
                                    (player, ctx) -> {
                                        if (player.getLocation().getBlockY() > -40) return false;
                                        Biome b = player.getLocation().getBlock().getBiome();
                                        return isDeepOceanBiome(b);
                                    },
                                    400
                            )
                    ),
                    3, List.of(
                            NPCPersonalQuest.of(
                                    "jack_kill_elder_guardian",
                                    "§b🐟 Chasseur des abysses",
                                    "Tuer un Elder Guardian.",
                                    Material.PRISMARINE_SHARD,
                                    KILL,
                                    (player, ctx) -> ctx instanceof org.bukkit.entity.Entity e
                                            && e.getType() == org.bukkit.entity.EntityType.ELDER_GUARDIAN,
                                    1000
                            )
                    ),
                    5, List.of(
                            NPCPersonalQuest.of(
                                    "jack_nether_roof",
                                    "§4🔥 Au-dessus de l'Enfer",
                                    "Atteindre Y ≥ 127 dans le Nether.",
                                    Material.NETHERRACK,
                                    MOVE,
                                    (player, ctx) ->
                                            player.getWorld().getEnvironment()
                                                    == org.bukkit.World.Environment.NETHER
                                                    && player.getLocation().getBlockY() >= 127,
                                    1500
                            )
                    )
            )
    ),

    GANDALF(
        "citycore_gandalf",
                "§fGandalf",
                "Mage",
                "gandalf",
                "eyJ0aW1lc3RhbXAiOjE0OTM1ODEyMDU2NjgsInByb2ZpbGVJZCI6ImUzYjQ0NWM4NDdmNTQ4ZmI4YzhmYTNmMWY3ZWZiYThlIiwicHJvZmlsZU5hbWUiOiJNaW5pRGlnZ2VyVGVzdCIsInNpZ25hdHVyZVJlcXVpcmVkIjp0cnVlLCJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMWM0YTVlNDc0N2UwZTY5ZmQ5MTY1OTQ1ZDZhYmIxYzBhNzI1OTYxYWNlNmJmNzRhZWM1NGY3NDQzZDZiOWUxNSJ9fX0=",
                "imIk3vKxnCHHBzzjdvdRPgfFmhtOMvdowzPIQpLNDAl0Vk7JBWkt+q/aGaEoKtG+JzUWtyYXmPmmiCOlwdCD64u9kaloEZRllllzMkpFP84IKBqPYE6AJ0SB5jWyaq5uYL9bpjhsUx8d6z+gUjF3gCAFa8hn4K8oS350w++cPtjIBFDs106+xySUQdwJwtcoRrLRBbN/N/RGKjNfVASy7fKERDOZfIHZfPbhRa6B/e8ZeCgjDudLHlBmR2LeUHJaetRcE7P3xvGQcngw+wfnc4Q7P1MDd7R0R5q2JVjijoUIjb1Xe1YpDHeDE8MQnclIHiIZAlygN8BYv3bO/vOYVnCXaVtSAs4l+tPWMuF7yzOWnMNEqu0ZMc/Y/0Yt5FUqERsOcfigzTNzhXQGUusdhEEg3kMRUdIk8vdrLRv8LvbhuYzDW2aaYMZPXD4FhpJKUiLhxDeVOz0/emIus9OJ8HwsZlhE3P6/Zu8ClT2OWOsqS1oX1LF7rO38N0wOIVBN9ADV4YXNiyDA3eri8vN864N/1AJgQrfcbCpYm/gwoiIMHw/AbdGIeEXDPBsAt4g17nnMPsr67sLuCbkAY8thzSyaN1dc/yb3bQebQmmg/sWvMAfMGieBS6PBW0ZJtbIc7eUcALWdq/Jz+0hPfEYudI4uNk11+czDQLf56yTVNHA=",
        Map.of(
                "first_meeting", List.of(
                        "§fGandalf §7: §oUn magicien n'est jamais en retard...",
                        "§fGandalf §7: §oIl arrive précisément à l'heure prévue."
                ),
                "city_arrival", List.of(
                        "§fGandalf §7: §oJe sens une grande destinée en ces terres.",
                        "§fGandalf §7: §oMais l'ombre grandit à l'Est..."
                ),
                "building_assign", List.of(
                        "§fGandalf §7: §oUn lieu de savoir. Voilà qui me convient.",
                        "§fGandalf §7: §oReviens me voir lorsque tu auras gagné en sagesse."
                )
        ),

        // ─────────────────────────────────────────────
        // Quêtes personnelles de Gandalf
        // ─────────────────────────────────────────────
        Map.of(
                1, List.of(
                        // Déclenchée par SummitListener via onSummitReached()
                        // Le TriggerType.SUMMIT indique que la progression
                        // vient de l'extérieur, pas du PlayerMoveEvent.
                        // Le validator est un no-op (jamais appelé via MOVE).
                        NPCPersonalQuest.of(
                                "jack_reach_summit",
                                "§6⛰ Conquérant des sommets",
                                "Atteindre le sommet d'une montagne.",
                                Material.STONE_SWORD,
                                SUMMIT,
                                (player, ctx) -> true, // validé en externe par SummitListener
                                500
                        )
                )
        )
    );


    /* =========================
       CHAMPS
       ========================= */

    public final String tag;
    public final String displayName;
    public final String function;
    public final String skinId;
    public final String skinValue;
    public final String skinSignature;
    public final Map<String, List<String>> dialogues;
    public final Map<Integer, List<NPCPersonalQuest>> personalQuestsByLevel;

    /* =========================
       CONSTRUCTEUR
       ========================= */

    CityNPC(String tag, String displayName, String function,
            String skinId, String skinValue, String skinSignature,
            Map<String, List<String>> dialogues,
            Map<Integer, List<NPCPersonalQuest>> personalQuestsByLevel) {
        this.tag                   = tag;
        this.displayName           = displayName;
        this.function              = function;
        this.skinId                = skinId;
        this.skinValue             = skinValue;
        this.skinSignature         = skinSignature;
        this.dialogues             = dialogues;
        this.personalQuestsByLevel = personalQuestsByLevel;
    }

    /* =========================
       API QUÊTES PERSO
       ========================= */

    public List<NPCPersonalQuest> getPersonalQuestsForLevel(int npcLevel) {
        return personalQuestsByLevel.entrySet().stream()
                .filter(e -> e.getKey() <= npcLevel)
                .flatMap(e -> e.getValue().stream())
                .toList();
    }

    public boolean hasPersonalQuests() {
        return !personalQuestsByLevel.isEmpty();
    }

    public NPCPersonalQuest getPersonalQuestById(String id) {
        return personalQuestsByLevel.values().stream()
                .flatMap(List::stream)
                .filter(q -> q.id().equals(id))
                .findFirst()
                .orElse(null);
    }

    /* =========================
       DIALOGUES
       ========================= */

    public List<String> getDialogue(String key) {
        return dialogues.getOrDefault(key, List.of());
    }

    public static CityNPC fromTag(String tag) {
        for (CityNPC npc : values()) {
            if (npc.tag.equals(tag)) return npc;
        }
        return null;
    }

    public String hologramLine() {
        return "§7✦ §e" + function + " §7✦";
    }

    /* =========================
       HELPERS BIOMES (privés statiques)
       ========================= */

    private static boolean isDeepOceanBiome(Biome b) {
        return switch (b) {
            case DEEP_OCEAN, DEEP_COLD_OCEAN,
                 DEEP_FROZEN_OCEAN, DEEP_LUKEWARM_OCEAN -> true;
            default -> false;
        };
    }
}