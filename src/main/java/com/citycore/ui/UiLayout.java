package com.citycore.ui;

/**
 * Géométrie des menus « Valmont » (coffre de 6 rangées) et déclaration de tout ce que les
 * écrans affichent, à quelle hauteur. Les images viennent de tools/ui-art/ValmontArt.java,
 * qui utilise les mêmes positions.
 */
public final class UiLayout {

    private UiLayout() {}

    /** Gauche du cadre d'une case (18 px) du menu. */
    public static int sx(int col) { return 7 + 18 * col; }

    /** Haut du cadre d'une case du menu (rangées 0 à 5). */
    public static int sy(int row) { return 17 + 18 * row; }

    /** Haut de la cellule d'un texte centré verticalement dans une rangée. */
    public static int rowText(int row) { return sy(row) + 4; }

    /** Haut de la cellule d'un texte posé en haut d'une rangée (lignes de parchemin). */
    public static int rowTop(int row) { return sy(row) + 1; }

    // Bandeau du nom (en haut de la fenêtre)
    public static final int NAME_Y      = 3;
    public static final int NAME_CENTER = 88;
    public static final int NAME_MAX    = 116;

    // Barre de la rangée 5 (expérience, parcelles…) : 69 segments de 2 px
    public static final int BAR_X        = 28;
    public static final int BAR_Y        = sy(5) + 4;
    public static final int BAR_SEGMENTS = 69;
    public static final int BAR_TEXT_Y   = sy(5) + 3;
    public static final int BAR_CENTER   = 97;

    // Plaque de la rangée 0 (niveau, état)
    public static final int STATE_X = 12;
    public static final int STARS_X = 70, STARS_Y = sy(0) + 5;

    // Portrait : décor 64 x 64, buste 16 x 20 pixels de skin agrandis 3 fois
    public static final int PORTRAIT_X = 11, PORTRAIT_Y = sy(1) + 4;
    public static final int BUST_X = 19, BUST_Y = PORTRAIT_Y + 2, BUST_SCALE = 3, BUST_W = 16, BUST_H = 20;
    public static final int SILHOUETTE_Y = PORTRAIT_Y + 4;

    // Cartes de quête : barre de progression de 47 px (23 segments)
    public static final int QUEST_SEGMENTS = 23;
    // Lignes des récits : barre de 98 px (49 segments)
    public static final int STORY_SEGMENTS = 49;

    // HUD compact (panneau des quêtes, panneaux du Tab) : pièces de 84 px de large, bandeau doré
    // de 12 px, lignes de 8 px, bas de 3 px ; petite police (capitales 3 x 5, cellule de 8 px).
    // Dans une ligne : capitale de y+2 à y+6, barre de progression sur y+7.
    public static final int HUD_W = 84, HUD_TOP_H = 12, HUD_ROW_H = 8, HUD_BOTTOM_H = 3;
    public static final int HUD_TITLE_CELL = 2;                          // cellule du titre (bandeau)
    public static final int HUD_PAD = 5;                                  // marge du texte et de la barre
    public static final int HUD_SEGMENTS = (HUD_W - 2 * HUD_PAD) / 2;    // barre d'une ligne (74 px)
    public static final int HUD_MAX_ROWS = 12;
    /** Haut de la ligne n d'un panneau dont le bandeau commence en top (repère local). */
    public static int hudRowTop(int top, int row) { return top + HUD_TOP_H + HUD_ROW_H * row; }

    // Panneau « Quêtes actives » : titre de la première barre de boss, écrit à y = 3 et centré.
    // Une image prévue à la hauteur d'écran h y utilise la police h + 3 ; les abscisses partent du
    // milieu de l'écran. Trois positions au choix du joueur (/city quests) : en 1080p à l'échelle 4
    // (480 px de large), « bord » laisse 4 px à gauche ; « près » s'arrête 4 px avant l'inventaire.
    public static final int QP_OFFSET = 3, QP_TOP = 60;
    public static final int[] QP_LEFT = {-236, -208, -176};

    // Liste des joueurs (Tab) : une ligne d'en-tête commence à y = 10 et une image de hauteur Y y
    // tombe en 10 + Y - 6. Plaque du titre au centre, panneau « cité » à gauche, « joueur » à
    // droite ; l'avance de la ligne est nulle, donc la liste des joueurs reste au milieu, entre les
    // deux panneaux (TAB_GAP px de part et d'autre du centre).
    public static final int TAB_OFFSET = 6, TAB_ROWS = 4, TAB_GAP = 72, TAB_EXTRA_LINES = 1;

    /** Déclare au registre toutes les combinaisons image / hauteur utilisées par les écrans. */
    public static void declare(UiAtlas a) {
        for (String bg : new String[]{"bg_character", "bg_grid", "bg_quests", "bg_personal",
                "bg_cityquests", "bg_buildings", "bg_character_gestion"}) a.background(bg);

        int row0 = sy(0), row5 = sy(5);
        int[] rows14 = {sy(1), sy(2), sy(3), sy(4)};

        // En-tête : petits boutons et bannières
        for (String s : new String[]{"ic_close", "ic_back", "ic_info", "ic_follow", "ic_follow_stop",
                "ic_job", "ic_job_quit", "ic_job_locked", "ic_story", "ic_purse"}) a.sprite(s, row0);
        for (String s : new String[]{"rib_vendre", "rib_entrepot", "rib_boutique", "rib_quetes_pnj",
                "rib_recits", "rib_logis", "rib_batiments", "rib_supprimer", "rib_quetes",
                "rib_atelier", "rib_actions", "rib_garde", "rib_role"}) a.sprite(s, row0);
        // Pied de page : pagination et gros boutons
        for (String s : new String[]{"ic_prev", "ic_next", "ic_prev_off", "ic_next_off"}) a.sprite(s, row5);
        for (String s : new String[]{"fb_ajouter", "fb_supprimer", "fb_retour", "fb_recit", "fb_charte",
                "fb_charte_fonds", "fb_charte_lock", "fb_capitale"}) a.sprite(s, row5);
        // Gros boutons et plaques des rangées 1 à 4
        for (String s : new String[]{"btn_vendre", "btn_entrepot", "btn_boutique",
                "btn_suivre", "btn_arreter", "btn_logis", "btn_promener", "btn_rester", "btn_quetes",
                "btn_batiments", "btn_tresor", "btn_agrandir", "btn_agrandir_off", "btn_deposer",
                "btn_histoire", "btn_actions", "plate"}) a.sprite(s, rows14);
        // États des cases de liste, cadenas
        for (String s : new String[]{"ring_green", "ring_amber", "ring_red", "ring_gray", "ring_purple",
                "ring_gold"}) a.sprite(s, rows14);
        a.sprite("lock", sy(1) + 3, sy(2) + 3, sy(3) + 3, sy(4) + 3, sy(5) + 3);
        // Niveau, barres
        for (int n = 0; n <= 5; n++) a.sprite("stars_" + n, STARS_Y);
        a.sprite("seg_xp", BAR_Y);
        a.sprite("seg_xp_max", BAR_Y);
        a.sprite("coin", sy(1) + 5, sy(2) + 5, sy(5) + 5);
        // Quêtes des PNJ
        for (String s : new String[]{"qb_accepter", "qb_valider", "qb_encours", "qb_repos", "qb_aucune"})
            a.sprite(s, sy(4));
        a.sprite("vb_recits", sy(1));
        a.sprite("vb_recits_off", sy(1));
        a.sprite("seg_green", sy(2) + 12, sy(3) + 12);
        // Récits
        int[] storyBars = new int[5], badges = new int[5];
        for (int r = 1; r <= 5; r++) { storyBars[r - 1] = sy(r) + 13; badges[r - 1] = sy(r) + 4; }
        a.sprite("seg_thin", storyBars);
        for (String s : new String[]{"badge_check", "badge_progress", "badge_empty", "badge_new"}) a.sprite(s, badges);
        // Portraits
        for (String s : new String[]{"pf_nature", "pf_route", "pf_port", "pf_atelier", "pf_forge",
                "pf_taverne", "pf_mine", "pf_chapelle", "pf_mairie", "pf_dragon"}) a.sprite(s, PORTRAIT_Y);
        a.sprite("silhouette", SILHOUETTE_Y);
        int[] bustRows = new int[BUST_H];
        for (int k = 0; k < BUST_H; k++) bustRows[k] = BUST_Y + BUST_SCALE * k;
        a.sprite("px", bustRows);

        // HUD compact : panneau des quêtes (barre de boss) et panneaux du Tab
        declareHudStack(a, QP_TOP, QP_OFFSET, HUD_MAX_ROWS);
        declareHudStack(a, 0, TAB_OFFSET, TAB_ROWS);

        // Journal de quêtes (/city quests) : onglets « Suivi » et « Récits »
        a.background("bg_tracking");
        a.background("bg_journal");
        for (String s : new String[]{"tab_suivi_on", "tab_suivi_off", "tab_recits_on", "tab_recits_off"}) a.sprite(s, row0);
        for (String s : new String[]{"pos_bord", "pos_gauche", "pos_pres", "sel54"}) a.sprite(s, sy(4));
        for (String s : new String[]{"hud_on", "hud_off", "track_all"}) a.sprite(s, row5);

        // Textes : ombrés (bandeau, plaques, barres) et plats (lignes de parchemin)
        a.shadowText(NAME_Y, rowText(0), rowText(1), rowText(2), rowText(3), rowText(4), BAR_TEXT_Y, rowText(5));
        a.flatText(rowTop(1), rowTop(2), rowTop(3), rowTop(4), rowTop(5));
    }

    /**
     * Panneau HUD empilé (bandeau, lignes, bas après n lignes) dont le bandeau commence en top
     * (repère local) ; chaque hauteur h y utilise la police h + offset.
     */
    private static void declareHudStack(UiAtlas a, int top, int offset, int rows) {
        a.sprite("hud_top", top + offset);
        a.smallText(top + HUD_TITLE_CELL + offset);
        int[] rowYs = new int[rows], bars = new int[rows], bottoms = new int[rows + 1];
        for (int r = 0; r < rows; r++) {
            rowYs[r] = hudRowTop(top, r) + offset;
            bars[r]  = hudRowTop(top, r) + 7 + offset;
        }
        for (int n = 0; n <= rows; n++) bottoms[n] = hudRowTop(top, n) + offset;
        for (String s : new String[]{"hud_head", "hud_row", "hud_plain"}) a.sprite(s, rowYs);
        a.sprite("hud_bottom", bottoms);
        a.sprite("seg_hud", bars);
        a.sprite("seg_hud_gold", bars);
        a.smallText(rowYs); // cellule du texte = haut de la ligne
    }
}
