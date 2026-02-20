package com.citycore.npc;

public enum CityNPC {

    MAYOR(
            "citycore_mayor",           // tag unique en BDD Citizens
            "§6Alderic",                // nom affiché en jeu
            "Maire de la citée",        // fonction affichée dans l'hologramme
            "alderic",                  // id skin SkinsRestorer
            "ewogICJ0aW1lc3RhbXAiIDogMTc3MTQ4OTcyNDg1OCwKICAicHJvZmlsZUlkIiA6ICI2NDg4Y2VjMjc4OGQ0MTI2OTk5NWMyMmY4OTdmMzA4OSIsCiAgInByb2ZpbGVOYW1lIiA6ICJBc3BlbjA1MyIsCiAgInNpZ25hdHVyZVJlcXVpcmVkIiA6IHRydWUsCiAgInRleHR1cmVzIiA6IHsKICAgICJTS0lOIiA6IHsKICAgICAgInVybCIgOiAiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS9kMTUzNTk3ZmE2M2U2MTJhMGQ2YWY2OWE0ZTFiMDFlY2YxM2M4ZGI0Y2E5ZThkYzdkZmZmODQ5YjBjMTAzZTlmIgogICAgfQogIH0KfQ==",
            "A60BzbuZYlRE2SbwCz02jy+hnBG1o2D8QMl8IcfD994ft2CBTWEhAdhWN0Fey78EXKMTTRFmyGQweLDlV/29lPIRwSLhy77gb9fqkYnR1LaLykUAkiBJ0VBHJW7ZAYAAmOJE5ehoo/fwWADNwlVAu8oZzXGJhhf8goCiGnTBuRRXI6rkyMdMGpjkDqxATuew/0mtxNAGLVIORoHNhbBj1p3ihaM9By4L/A39oN/WthMf+rMQNwhLCuMBYXPI+//ShFhDJl/lDTIm7nvsCk/1vVVDEuULosjWqlYPf2r+r3hDAMIE5StyDk9ypxImHnDe3D2cb5DFNBtZKHLYyIq8enxXxotHcMRjZeaHg4KwajswshsMh07yvXO0x46nfF6RFcMEbjL2u7eRW4Y1bJjKVkxTZ9hmM6C9oHHYKvHRAT1cVo6YxGU8/fukthrZvD0BlQAjsDdwBGW/p2ex/dQtweHWDlamWeqhNIUBIdMF9qlWwNX6f24clecUIhEbXxXdDrupXNZuBrBtUkzicbPrC+PVJaKT0qCO9S2fyHW89VznqAK3whv3CVBGvqpq242IucuTHJpwDet1ctXXWw97ebSfvP//Cg1f9nn5mrE81OB5G5BEsS1Y32KRviQ5tfZPnPsU0SRXatoHzNp0RozrtF2B53SA5eII7ViNFpe/mIY="
    );

    // Futurs NPCs à ajouter ici :
    // BLACKSMITH("citycore_blacksmith", "§7Gareth", "Forgeron", "gareth", "..value..", "..sig.."),
    // MERCHANT("citycore_merchant",  "§eLyra",   "Marchande", "lyra",   "..value..", "..sig.."),
    // GUARD("citycore_guard",        "§fBrynn",  "Garde",     "brynn",  "..value..", "..sig.."),

    public final String tag;         // Identifiant Citizens (data tag unique)
    public final String displayName; // Nom coloré affiché en jeu
    public final String function;    // Fonction affichée dans l'hologramme
    public final String skinId;      // ID skin pour SkinsRestorer (sr set <skinId>)
    public final String skinValue;   // Texture base64 mineskin.org
    public final String skinSignature; // Signature base64 mineskin.org

    CityNPC(String tag, String displayName, String function,
            String skinId, String skinValue, String skinSignature) {
        this.tag          = tag;
        this.displayName  = displayName;
        this.function     = function;
        this.skinId       = skinId;
        this.skinValue    = skinValue;
        this.skinSignature = skinSignature;
    }

    /**
     * Retrouve un CityNPC depuis le tag Citizens stocké dans npc.data().
     */
    public static CityNPC fromTag(String tag) {
        for (CityNPC npc : values()) {
            if (npc.tag.equals(tag)) return npc;
        }
        return null;
    }

    /**
     * Retourne le texte hologramme formaté.
     */
    public String hologramLine() {
        return "§7✦ §e" + function + " §7✦";
    }
}