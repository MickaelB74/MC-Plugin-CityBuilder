package com.citycore.npc;

import java.util.List;

public enum CityNPC {

    MAYOR(
            "citycore_mayor",
            "§6Alderic",
            "Maire de la ville",
            "alderic",
            "ewogICJ0aW1lc3RhbXAiIDogMTc3MTQ4OTcyNDg1OCwKICAicHJvZmlsZUlkIiA6ICI2NDg4Y2VjMjc4OGQ0MTI2OTk5NWMyMmY4OTdmMzA4OSIsCiAgInByb2ZpbGVOYW1lIiA6ICJBc3BlbjA1MyIsCiAgInNpZ25hdHVyZVJlcXVpcmVkIiA6IHRydWUsCiAgInRleHR1cmVzIiA6IHsKICAgICJTS0lOIiA6IHsKICAgICAgInVybCIgOiAiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS9kMTUzNTk3ZmE2M2U2MTJhMGQ2YWY2OWE0ZTFiMDFlY2YxM2M4ZGI0Y2E5ZThkYzdkZmZmODQ5YjBjMTAzZTlmIgogICAgfQogIH0KfQ==",
            "A60BzbuZYlRE2SbwCz02jy+hnBG1o2D8QMl8IcfD994ft2CBTWEhAdhWN0Fey78EXKMTTRFmyGQweLDlV/29lPIRwSLhy77gb9fqkYnR1LaLykUAkiBJ0VBHJW7ZAYAAmOJE5ehoo/fwWADNwlVAu8oZzXGJhhf8goCiGnTBuRRXI6rkyMdMGpjkDqxATuew/0mtxNAGLVIORoHNhbBj1p3ihaM9By4L/A39oN/WthMf+rMQNwhLCuMBYXPI+//ShFhDJl/lDTIm7nvsCk/1vVVDEuULosjWqlYPf2r+r3hDAMIE5StyDk9ypxImHnDe3D2cb5DFNBtZKHLYyIq8enxXxotHcMRjZeaHg4KwajswshsMh07yvXO0x46nfF6RFcMEbjL2u7eRW4Y1bJjKVkxTZ9hmM6C9oHHYKvHRAT1cVo6YxGU8/fukthrZvD0BlQAjsDdwBGW/p2ex/dQtweHWDlamWeqhNIUBIdMF9qlWwNX6f24clecUIhEbXxXdDrupXNZuBrBtUkzicbPrC+PVJaKT0qCO9S2fyHW89VznqAK3whv3CVBGvqpq242IucuTHJpwDet1ctXXWw97ebSfvP//Cg1f9nn5mrE81OB5G5BEsS1Y32KRviQ5tfZPnPsU0SRXatoHzNp0RozrtF2B53SA5eII7ViNFpe/mIY=",
            List.of(
                    "§6Alderic §f: §oBonjour, aventurier. Je suis Alderic, maire de cette ville.",
                    "§6Alderic §f: §oNous avons grand besoin de bras courageux pour la faire prospérer.",
                    "§6Alderic §f: §oParlez-moi si vous souhaitez consulter l'état de la ville,",
                    "§6Alderic §f: §oou pour agrandir notre territoire."
            )
    ),

    STONEMASON(
            "citycore_stonemason",
            "§7Brennan",
            "Tailleur de pierre",
            "brennan",
            "ewogICJ0aW1lc3RhbXAiIDogMTczNTM0NDA5MTg5MCwKICAicHJvZmlsZUlkIiA6ICJiODU0NWMxMDlhZjE0ZGRjYmY4ZjhmZjg4ZTU2NzI4OSIsCiAgInByb2ZpbGVOYW1lIiA6ICJQdGFrb3B5c2tDWiIsCiAgInNpZ25hdHVyZVJlcXVpcmVkIiA6IHRydWUsCiAgInRleHR1cmVzIiA6IHsKICAgICJTS0lOIiA6IHsKICAgICAgInVybCIgOiAiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS84MmRhNGY1NDgxODE5MjcxMGE3OGFjNDVlNjI3Y2ExNmJlZjg4MDQzYzhiMTNhOTU4NjM2ODU1NGMwOGEwZTA2IgogICAgfQogIH0KfQ==",
            "Eu8c+SC9BCYH5qDpU/aALFEqRXeXa3nifZkCZAAL9K9XZ6yM++mZm4jWBNojyRxMWM32mRoFEJRY7rOvOtMwkUmIEm7XCdVhkEK36cPQ1aT0wLksYbeHpFok70Om6fOiFVXo3jxcAk13pd1xfpNjp4d0YfiqUehqW5/6nSXa3/ZqlMgNKuF8ZenXZ2UV8dSq6SY2x/xmGQGKbXWyygI9MLviXF5Hq7nCSQBn6dXGDpTu/HqTW+Mg53T4H8ogi6aGq76WXhquR09bkR/uHsEzyVA40yB3Q6gh9qPegTUCC+1W+xsiqXqzcTw/P/fGzs4nPWetM57a6tu+y4aedqkTI8TFy42YCV1AOIk3nvUT+4w2LAy0iAzlj1bhdQdnRiffHhpl6WD6iKm1LRolK/XpH6a03Dcq+Uay7b6Z4Hg5Vf8Yi3fsGlu5jpyH3nI4ylF9Aj33thOguNG0XQKIYL0xyvgO7IwPLmy0qMfPlEmklNp8JfJbTF52/UCmOGabDa/kDDX+B+aOzmYeel/xjdSwa3GjoziUllMTgxTucoH7QneX8Nqj/uVxxZdjKIITTZMblo6gnQ73HzufIOcc2FgHOr3elzTAXZJydPser4FH7VF8Lb57hfPYTkK/9eNHQi9KQ81b0yjXVgdGzkQIQEDm8UW+h3rh7kEOu09BlpNhVkQ=",
            List.of(
                    "§7Brennan §f: §o*grogne* Encore un nouveau visage...",
                    "§7Brennan §f: §oJ'rachète les pierres, les graviers, tout c'qui sort de la terre.",
                    "§7Brennan §f: §oPas de bavardage, amène tes stacks et j'te file des coins."
            )
    ),

    JACKSPARROW(
            "citycore_jacksparrow",
            "§3Jack Sparrow",
            "Pirate des mers",
            "jacksparrow",
            "ewogICJ0aW1lc3RhbXAiIDogMTcwOTM5NjgzNTkyMCwKICAicHJvZmlsZUlkIiA6ICI4ZGUyNDAzYTEyMjU0ZmFkOTM1OTYxYWFlYmQwNGUyOSIsCiAgInByb2ZpbGVOYW1lIiA6ICJkZXNydHB1bWEiLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYTAzNTRmZjAzZGYwZjM0N2RiYmI3NjU1NjE2MDQzYjRiYzdiOWUxNjc2ODQxNDEwMjdhNWZmZDNhNjMwOTIzOSIKICAgIH0KICB9Cn0=",
            "fMOqMuG/FbKWFZnE5/RVmMpk4d+kftHEA0NMEjdO3QPeEGF6eLemb7gvm9fHKc4MBG3mpGVl2XPA4UAl25/vpspC0JlnJvLwwOnpwPieyy9ruGTppu0dNs++eR23EIrxz54fx+7cd+amz0uE/8snrsBNJ9jBAE8sG7CVLUUvAqVqUlAgEQkwisRQ6uFLhxgFLFIIBRGCXv9XURlSLsLwD4s8HyaHFNIpjWrqnlR5wy9OeRJ/10BijH51Ohi74zRdZmkJ0TCc0YrvSohaxzNNL61HykgYrxZSOHHuhABcBo6F+w6LohzOcV52FyQelHnyX/dWicY+2INYmQQaMUvTeEzNY/AdQ5LMQTmFqq2lraRQPMcpg11wjf/EMHva3MHDmYtHVxIN90vsLavt7pgYEx+KTLhUTqd1DvmQa1ESUdYaiyPRv1XU622okZrySQvHB9cvTgPKJo2Kj+BAK9XhtWCxS3XQo1N05DCPMIBOcz9UB767eT3jcQ9/CckDa7jIswOQsqmAqGlQTJhzr6QBDcvzYim/W+ue9w25H555zje/9RNYEa1IM9r/5ZpY1cuilyMpxKIGmc1nmRZvziPeNeKmQyiue8X2JE0ub0vJE43Ypg0vWeVA2S31JHJmtb0MNL3v2FvrUNy/Kg50INKSDNebjWt83s5my5KDFPzjjuQ=",
            List.of(
//                    "§3Jack §f: §oAh, un nouveau venu ! Permettez-moi de me présenter...",
//                    "§3Jack §f: §oCaptaine Jack Sparrow. Le Capitaine, c'est important.",
//                    "§3Jack §f: §oJe rachète tout ce qui vient de la mer. Poissons, trésors...",
//                    "§3Jack §f: §oMais si vous trouvez du rhum... ne le buvez pas avant de me voir."

                    "§3Jack §f: §oHHHAAARRRGGGGGG !!! Salut c'est Jack Sparrow ! Le vrai !",
                    "§3Jack §f: §oJ'ai besoin de 50€ en coupon western union... Tu as ca sur toi ?"
            )
    );

    public final String tag;
    public final String displayName;
    public final String function;
    public final String skinId;
    public final String skinValue;
    public final String skinSignature;
    public final List<String> introLines;

    CityNPC(String tag, String displayName, String function,
            String skinId, String skinValue, String skinSignature,
            List<String> introLines) {
        this.tag           = tag;
        this.displayName   = displayName;
        this.function      = function;
        this.skinId        = skinId;
        this.skinValue     = skinValue;
        this.skinSignature = skinSignature;
        this.introLines    = introLines;
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
}