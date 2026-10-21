package com.citycore.quest.city;

import com.citycore.economy.MaterialGroups;
import com.citycore.npc.CityNPC;
import org.bukkit.Material;
import org.bukkit.block.Biome;
import org.bukkit.entity.EntityType;

import java.util.List;
import java.util.Set;

import static com.citycore.quest.city.CityQuest.*;

/**
 * Paliers de progression de la ville — la trame principale, en dix chapitres.
 *
 * Contexte : vingt hivers après la « Nuit Noire » où un dragon sorti d'une Porte vers le Néant a
 * réduit Valmont en cendres, Alderic — son dernier intendant — fait renaître la ville avec la
 * Charte. Acte I (I à III) : survivre et s'installer. Acte II (IV à VI) : les métiers, les routes
 * et la garde. Acte III (VII à IX) : le savoir, la révélation des registres, puis la Porte et le
 * dragon — l'apogée. Épilogue (X) : Valmont redevient capitale ; hauts faits et légendes.
 *
 * Chaque chapitre retrouve des survivants (artisans dispersés) liés à la Nuit Noire, et accueille
 * des légendes de passage (quêtes bonus), dont huit liées à un mod du serveur (Robinson, Achab,
 * Obélix, Musashi, Geppetto, Ragnar, Jeanne, Hercule) ; le chapitre IX n'en a aucune. Les identifiants des quêtes reprennent ceux de la
 * version en sept chapitres : la progression enregistrée est conservée (voir
 * {@link CityQuestManager#migrateTierScheme()}).
 *
 * Pour passer au palier suivant : toutes les quêtes non bonus terminées
 * + payer la charte (upgradeCost) depuis la caisse de la ville.
 * Récompense : rewardChunks emplacements de chunks offerts.
 *
 * Parcelles (barème du 21/10/2026, une grande ville) : 12, 24, 40, 60, 85, 110, 140 et 175 chunks
 * à revendiquer des chapitres I à VIII, 300 pour le haut fait du chapitre X. Les emplacements
 * offerts (10 à la fondation, puis 10 à 40 par charte) en couvrent la plus grande part ; le reste
 * s'achète à bas prix chez Alderic (city-expand). Les villes plus anciennes reçoivent la différence
 * (voir {@link CityQuestManager#migrateChunkScheme()}).
 * Coûts, récompenses et objectifs sont surchargeables dans config.yml (city-tiers, city-quests).
 */
public enum CityTier {

    /* =========================================================
       ACTE I — SURVIVRE ET S'INSTALLER
       ========================================================= */

    TIER_1(1, "§7Campement", Material.CAMPFIRE, "Chapitre I — Les cendres et la charte",
            List.of(
                    "§6Alderic §f: §oVingt hivers ont passé depuis que Valmont a brûlé. Je suis Alderic, son dernier intendant.",
                    "§6Alderic §f: §oCette charte est tout ce qui reste de notre ville. Avec elle, Valmont peut renaître, ici même.",
                    "§6Alderic §f: §oD'abord, délimitons le camp et dressons une palissade.",
                    "§6Alderic §f: §oOn dit qu'un vieux charbonnier vit seul dans les bois. Il nous faut son bois et son savoir.",
                    "§6Alderic §f: §oDes pêcheurs parlent d'un homme vêtu de peaux, sur la plage. Il compte les jours en entailles sur un poteau..."
            ),
            List.of(
                    claim("t1_claim", "§7Planter les jalons",
                            "Claimez §f{n} chunks §7pour délimiter le campement.", 12),
                    recruit("t1_bastien", "§2Le charbonnier des bois",
                            "Trouvez §2Bastien §7en forêt, ramenez-le et installez-le dans un bâtiment.",
                            CityNPC.WOODCUTTER, 250,
                            Biome.FOREST, Biome.BIRCH_FOREST, Biome.DARK_FOREST, Biome.TAIGA,
                            Biome.OLD_GROWTH_BIRCH_FOREST, Biome.OLD_GROWTH_PINE_TAIGA),
                    build("t1_buildings", "§7Les premiers abris",
                            "Délimitez §f{n} bâtiments §7(maison commune, cabane du bûcheron…).", 2),
                    deliver("t1_palisade", "§7La palissade",
                            "Livrez §f{n} rondins §7(toutes essences) pour la palissade.",
                            Material.OAK_LOG, MaterialGroups.LOGS, 192),
                    recruit("b1_robinson", "§a★ L'homme de l'île",
                            "§8[Bonus] §7Un naufragé barbu vit seul sur la côte depuis vingt-huit ans. Ramenez-le au campement.",
                            CityNPC.ROBINSON, 500,
                            Biome.BEACH, Biome.STONY_SHORE).asOptional()
            ),
            150, 10),

    TIER_2(2, "§aHameau", Material.WHEAT, "Chapitre II — Du pain pour l'hiver",
            List.of(
                    "§6Alderic §f: §oLe camp tient bon. Mais un toit ne nourrit personne, et l'hiver approche.",
                    "§6Alderic §f: §oBastien jure qu'une fermière survit seule dans les plaines, avec les dernières semences de Valmont.",
                    "§6Alderic §f: §oAvec du grain en réserve, nous serons un vrai hameau.",
                    "§6Alderic §f: §oEt méfiez-vous des bois : un archer y détrousse les pillards... mais il paraît qu'il a bon cœur.",
                    "§6Alderic §f: §oUn vieil homme fixe la rivière depuis trois jours. Il jure qu'« il » est là, quelque part."
            ),
            List.of(
                    claim("t2_claim", "§aÉtendre les champs",
                            "Claimez §f{n} chunks §7au total.", 24),
                    recruit("t2_helene", "§eLa fermière des plaines",
                            "Trouvez §eHélène§7, ramenez-la et installez-la dans une grange.",
                            CityNPC.FARMER, 350,
                            Biome.PLAINS, Biome.SUNFLOWER_PLAINS, Biome.MEADOW),
                    build("t2_buildings", "§aLes maisons du hameau",
                            "Délimitez §f{n} bâtiments §7dans la ville.", 4),
                    stock("t2_granary", "§eLe grenier",
                            "Constituez une réserve de §f{n} blé §7dans le stock d'Hélène.",
                            Material.WHEAT, CityNPC.FARMER, Material.WHEAT, 384),
                    recruit("t2_robin", "§2★ Le prince des voleurs",
                            "§8[Bonus] §7Un archer hors-la-loi protège les champs des pillards. Gagnez sa confiance.",
                            CityNPC.ROBIN, 400,
                            Biome.FOREST, Biome.DARK_FOREST, Biome.OLD_GROWTH_BIRCH_FOREST).asOptional(),
                    recruit("b2_achab", "§b★ Le vieux capitaine",
                            "§8[Bonus] §7Un capitaine à la jambe d'ivoire pêche jour et nuit sur la rive. Il cherche un poisson que personne n'a jamais vu.",
                            CityNPC.ACHAB, 600,
                            Biome.RIVER, Biome.BEACH, Biome.STONY_SHORE).asOptional()
            ),
            400, 12),

    TIER_3(3, "§2Village", Material.STONECUTTER, "Chapitre III — Les pierres et la rivière",
            List.of(
                    "§6Alderic §f: §oNous voilà village ! Mais nos huttes ne passeront pas un autre hiver.",
                    "§6Alderic §f: §oBrennan, qui sculpta l'arche de la grande porte, erre dans les collines en dressant des cairns. Lui seul sait choisir la pierre.",
                    "§6Alderic §f: §oEt il nous faut la rivière : Martin, le passeur dont la barque a sauvé vingt enfants la Nuit Noire, vit toujours sur les rives.",
                    "§6Alderic §f: §oOn signale une voile noire au large. Un capitaine sans navire, paraît-il..."
            ),
            List.of(
                    claim("t3_claim", "§2Le village s'étend",
                            "Claimez §f{n} chunks §7au total.", 40),
                    recruit("t2_brennan", "§7Le tailleur errant",
                            "Trouvez §7Brennan §7dans les collines et offrez-lui un atelier.",
                            CityNPC.STONEMASON, 350,
                            Biome.WINDSWEPT_HILLS, Biome.WINDSWEPT_GRAVELLY_HILLS,
                            Biome.WINDSWEPT_FOREST, Biome.STONY_SHORE, Biome.MEADOW),
                    recruit("t3_martin", "§bLe passeur de la rivière",
                            "Trouvez §bMartin §7sur les rives et installez-le près de l'eau.",
                            CityNPC.FISHERMAN, 450,
                            Biome.BEACH, Biome.STONY_SHORE, Biome.SNOWY_BEACH, Biome.RIVER),
                    build("t3_buildings", "§2Le cœur du village",
                            "Délimitez §f{n} bâtiments §7dans la ville.", 6),
                    deliver("t2_foundations", "§7Les fondations",
                            "Livrez §f{n} pierres §7(cobblestone) pour des fondations dignes de Brennan.",
                            Material.COBBLESTONE, Set.of(Material.COBBLESTONE), 320),
                    recruit("t3_jack", "§3★ Une voile à l'horizon",
                            "§8[Bonus] §7Un capitaine pirate sans navire rôde sur les plages. Ramenez-le... et surveillez vos poches.",
                            CityNPC.JACKSPARROW, 600,
                            Biome.BEACH, Biome.STONY_SHORE).asOptional()
            ),
            800, 15),

    /* =========================================================
       ACTE II — LES MÉTIERS, LES ROUTES ET LA GARDE
       ========================================================= */

    TIER_4(4, "§6Bourg", Material.IRON_PICKAXE, "Chapitre IV — La Fosse et le Chaudron",
            List.of(
                    "§6Alderic §f: §oLe village grandit. Il lui faut du charbon pour l'hiver et un lieu où se réchauffer le cœur.",
                    "§6Alderic §f: §oGodefroy, maître de la Fosse aux Corbeaux, était sous terre quand Valmont a brûlé. Il creuse encore, dans les montagnes.",
                    "§6Alderic §f: §oRosalie tenait le Chaudron Doré, la taverne où buvaient toutes les guildes. Retrouvons-la, et sa louche avec.",
                    "§6Alderic §f: §oLes nuits sont dangereuses : sécurisons les abords avant tout.",
                    "§6Alderic §f: §oUn colosse traverse les forêts avec un menhir sur le dos. Il dit qu'il n'est pas gros.",
                    "§6Alderic §f: §oEt un étranger en robe de voyage a désarmé trois brigands avec un sabre de bois.",
                    "§6Alderic §f: §oOn parle aussi d'un roi sans couronne qui cueille le houblon des taïgas. Il dit avoir inventé la bière."
            ),
            List.of(
                    claim("t4_claim", "§6Le bourg s'étend",
                            "Claimez §f{n} chunks §7au total.", 60),
                    recruit("t3_godefroy", "§8Le mineur de la Fosse",
                            "Trouvez §8Godefroy §7près des montagnes et offrez-lui une mine.",
                            CityNPC.MINER, 450,
                            Biome.WINDSWEPT_HILLS, Biome.WINDSWEPT_GRAVELLY_HILLS,
                            Biome.STONY_PEAKS, Biome.GROVE, Biome.SNOWY_SLOPES),
                    recruit("t3_rosalie", "§cLa tavernière",
                            "Trouvez §cRosalie §7sur les routes et ouvrez-lui une taverne.",
                            CityNPC.INNKEEPER, 450,
                            Biome.PLAINS, Biome.FOREST, Biome.BIRCH_FOREST),
                    build("t4_buildings", "§6Les échoppes du bourg",
                            "Délimitez §f{n} bâtiments §7dans la ville.", 8),
                    stock("t3_coal", "§8Réserve de charbon",
                            "Constituez une réserve de §f{n} charbon §7chez Godefroy.",
                            Material.COAL, CityNPC.MINER, Material.COAL, 256),
                    npcLevel("t3_brennan_rank", "§7Le maître et ses apprentis",
                            "§7Brennan §7doit atteindre le rang §fApprenti §7(commercez avec lui).",
                            Material.STONE_BRICKS, CityNPC.STONEMASON, 2),
                    kill("t3_rounds", "§cLes abords sûrs",
                            "Éliminez §f{n} monstres §7(tous joueurs confondus).",
                            Material.IRON_SWORD, null, 50),
                    recruit("b4_obelix", "§9★ Ils sont fous ces Valmontois",
                            "§8[Bonus] §7Un géant moustachu porte un menhir sur le dos et poursuit des sangliers. Offrez-lui un festin.",
                            CityNPC.OBELIX, 600,
                            Biome.FOREST, Biome.DARK_FOREST, Biome.TAIGA).asOptional(),
                    recruit("b4_musashi", "§c★ La voie du sabre",
                            "§8[Bonus] §7Un rōnin au chapeau de paille défie les brigands des routes. Gagnez son respect.",
                            CityNPC.MUSASHI, 700,
                            Biome.BAMBOO_JUNGLE, Biome.CHERRY_GROVE, Biome.WINDSWEPT_HILLS, Biome.MEADOW).asOptional(),
                    recruit("b4_gambrinus", "§e★ Le roi de la bière",
                            "§8[Bonus] §7Un roi en tablier de brasseur cueille le houblon des taïgas. Offrez-lui une brasserie près de la taverne.",
                            CityNPC.GAMBRINUS, 650,
                            Biome.TAIGA, Biome.OLD_GROWTH_PINE_TAIGA, Biome.OLD_GROWTH_SPRUCE_TAIGA, Biome.PLAINS).asOptional()
            ),
            1500, 18),

    TIER_5(5, "§bVille", Material.ANVIL, "Chapitre V — Le marteau et le métier",
            List.of(
                    "§6Alderic §f: §oNous voici ville ! Le seigneur des Marches nous accorde le droit de marché... si nos ateliers produisent.",
                    "§6Alderic §f: §oThorvald a forgé les portes de Valmont : elles ont tenu une heure face au dragon. Il dit que ce n'était pas assez.",
                    "§6Alderic §f: §oAgathe avait douze ans quand le grand étendard a brûlé. Apprentie des teinturiers, elle seule saurait le refaire.",
                    "§6Alderic §f: §oBrennan veut des remparts de pierre. « Cette fois, ils tiendront », répète-t-il.",
                    "§6Alderic §f: §oUn aventurier au chapeau de feutre fouille les ruines du monde. Il paraît qu'il cherche un marché...",
                    "§6Alderic §f: §oUn vieil artisan sculpte des pantins au coin du feu, sur la route du sud. Il paraît que l'un d'eux s'est enfui."
            ),
            List.of(
                    claim("t5_claim", "§bLes faubourgs",
                            "Claimez §f{n} chunks §7au total.", 85),
                    recruit("t4_thorvald", "§4Le forgeron du nord",
                            "Trouvez §4Thorvald §7dans les terres enneigées et offrez-lui une forge.",
                            CityNPC.BLACKSMITH, 550,
                            Biome.SNOWY_TAIGA, Biome.TAIGA, Biome.SNOWY_PLAINS,
                            Biome.GROVE, Biome.OLD_GROWTH_SPRUCE_TAIGA),
                    recruit("t4_agathe", "§dLa teinturière",
                            "Trouvez §dAgathe §7parmi les fleurs et installez son métier à tisser.",
                            CityNPC.WEAVER, 550,
                            Biome.FLOWER_FOREST, Biome.MEADOW, Biome.SUNFLOWER_PLAINS, Biome.CHERRY_GROVE),
                    build("t5_buildings", "§bLes ateliers",
                            "Délimitez §f{n} bâtiments §7dans la ville.", 10),
                    deliver("t4_walls", "§7Les remparts",
                            "Livrez §f{n} briques de pierre §7(toutes variantes) pour les remparts de Brennan.",
                            Material.STONE_BRICKS, MaterialGroups.STONE_BRICKS, 640),
                    stock("t4_arsenal", "§4L'arsenal",
                            "Constituez une réserve de §f{n} lingots de fer §7chez Thorvald.",
                            Material.IRON_INGOT, CityNPC.BLACKSMITH, Material.IRON_INGOT, 192),
                    npcLevel("t4_mine_rank", "§8La Fosse s'agrandit",
                            "§8Godefroy §7doit atteindre le rang §fApprenti§7.",
                            Material.IRON_PICKAXE, CityNPC.MINER, 2),
                    recruit("t4_indiana", "§6★ L'archéologue",
                            "§8[Bonus] §7Un aventurier au chapeau de feutre fouille les ruines du monde. Attirez-le sur votre marché.",
                            CityNPC.INDIANA, 700,
                            Biome.DESERT, Biome.SAVANNA, Biome.JUNGLE).asOptional(),
                    recruit("b5_geppetto", "§6★ Le menuisier et sa marionnette",
                            "§8[Bonus] §7Un vieil ébéniste cherche du bon bois... et une marionnette qui a pris la fuite.",
                            CityNPC.GEPPETTO, 600,
                            Biome.FOREST, Biome.BIRCH_FOREST, Biome.FLOWER_FOREST, Biome.MEADOW).asOptional()
            ),
            2500, 20),

    TIER_6(6, "§3Ville franche", Material.SHIELD, "Chapitre VI — Les routes et la garde",
            List.of(
                    "§6Alderic §f: §oNous voici ville franche ! Nos échevins sont élus, et l'impôt nous revient.",
                    "§6Alderic §f: §oEdmond, maître des écuries royales, a ouvert les stalles la Nuit Noire. Il suit les hardes sauvages dans la savane.",
                    "§6Alderic §f: §oRoland, sergent de la garde, a perdu toute sa compagnie cette nuit-là. Offrons-lui une milice, et une raison de rester.",
                    "§6Alderic §f: §oLes pillards rançonnent nos marchands sur les routes. Il est temps de les chasser.",
                    "§6Alderic §f: §oUn vieillard au grand chapeau pointu a été vu sur la route marchande. Les gardes disent qu'il fait des feux d'artifice."
            ),
            List.of(
                    claim("t6_claim", "§3La ville franche",
                            "Claimez §f{n} chunks §7au total.", 110),
                    recruit("t4_edmond", "§6Le maître des écuries",
                            "Trouvez §6Edmond §7dans la savane et bâtissez-lui des écuries.",
                            CityNPC.STABLEMASTER, 550,
                            Biome.SAVANNA, Biome.SAVANNA_PLATEAU, Biome.PLAINS),
                    recruit("t5_roland", "§9Le sergent sans compagnie",
                            "Trouvez §9Roland §7sur les routes et confiez-lui la garde de la ville.",
                            CityNPC.GUARD, 650,
                            Biome.PLAINS, Biome.FOREST, Biome.TAIGA),
                    build("t6_buildings", "§3Les quartiers",
                            "Délimitez §f{n} bâtiments §7dans la ville.", 12),
                    kill("t4_roads", "§cLes routes marchandes",
                            "Chassez §f{n} pillards §7qui rançonnent les marchands.",
                            Material.CROSSBOW, EntityType.PILLAGER, 15),
                    kill("t5_militia", "§9La milice",
                            "Éliminez §f{n} monstres §7pour aguerrir la milice de Roland.",
                            Material.SHIELD, null, 150),
                    npcLevel("t5_forge_rank", "§4Le maître forgeron",
                            "§4Thorvald §7doit atteindre le rang §fCompagnon§7.",
                            Material.ANVIL, CityNPC.BLACKSMITH, 3),
                    npcLevel("t5_banners", "§dLes bannières de la ville",
                            "§dAgathe §7doit atteindre le rang §fApprentie §7: l'étendard de Valmont flotte à nouveau.",
                            Material.WHITE_BANNER, CityNPC.WEAVER, 2),
                    // Le voyageur des routes : identifiant conservé (progression déjà enregistrée)
                    recruit("t6_gandalf", "§f★ Le magicien gris",
                            "§8[Bonus] §7Un vieil homme en gris marche sur les routes, un bâton à la main. Offrez-lui une tour.",
                            CityNPC.GANDALF, 700,
                            Biome.PLAINS, Biome.SUNFLOWER_PLAINS, Biome.FOREST).asOptional()
            ),
            4000, 25),

    /* =========================================================
       ACTE III — LE SAVOIR, LA RÉVÉLATION, LA PORTE
       ========================================================= */

    TIER_7(7, "§eCité", Material.BOOKSHELF, "Chapitre VII — La foi et le savoir",
            List.of(
                    "§6Alderic §f: §oNotre renommée dépasse les Marches : nous voici cité ! Une cité doit soigner, et se souvenir.",
                    "§6Alderic §f: §oFrère Anselme a soigné nos survivants dans les ruines, puis s'est retiré à Saint-Clair. Bâtissons-lui une infirmerie.",
                    "§6Alderic §f: §oIsabeau, l'archiviste, a sauvé un coffre de registres. Elle cherche depuis vingt ans ce qui a causé la Nuit Noire.",
                    "§6Alderic §f: §oUn inventeur tombé du ciel dans un éclair cherche un laboratoire... génie ou fou ?",
                    "§6Alderic §f: §oUn navire à tête de dragon a été vu sur la côte. Son capitaine a demandé où se trouvait l'abbaye... Je n'aime pas ça."
            ),
            List.of(
                    claim("t7_claim", "§eLa cité",
                            "Claimez §f{n} chunks §7au total.", 140),
                    recruit("t5_anselme", "§5L'ermite de Saint-Clair",
                            "Trouvez §5frère Anselme §7dans son ermitage et bâtissez une infirmerie.",
                            CityNPC.MONK, 650,
                            Biome.MEADOW, Biome.GROVE, Biome.CHERRY_GROVE, Biome.WINDSWEPT_HILLS),
                    recruit("t5_isabeau", "§3L'archiviste",
                            "Trouvez §3Isabeau §7et offrez un scriptorium à ses archives.",
                            CityNPC.SCRIBE, 650,
                            Biome.BIRCH_FOREST, Biome.OLD_GROWTH_BIRCH_FOREST, Biome.FOREST, Biome.DARK_FOREST),
                    build("t7_buildings", "§eL'abbaye et le scriptorium",
                            "Délimitez §f{n} bâtiments §7dans la ville.", 14),
                    deliver("t5_library", "§3La bibliothèque",
                            "Livrez §f{n} bibliothèques §7pour abriter les registres de Valmont.",
                            Material.BOOKSHELF, Set.of(Material.BOOKSHELF), 24),
                    recruit("t5_docbrown", "§e★ L'inventeur fou",
                            "§8[Bonus] §7Un savant tombé du ciel dans un éclair cherche un laboratoire.",
                            CityNPC.DOCBROWN, 750,
                            Biome.MEADOW, Biome.CHERRY_GROVE, Biome.SUNFLOWER_PLAINS).asOptional(),
                    recruit("b7_ragnar", "§3★ Une voile carrée sur la rivière",
                            "§8[Bonus] §7Un drakkar remonte la rivière. Son chef dit venir « visiter l'abbaye ». Convainquez-le de commercer plutôt que de piller.",
                            CityNPC.RAGNAR, 800,
                            Biome.SNOWY_BEACH, Biome.STONY_SHORE, Biome.BEACH).asOptional()
            ),
            6000, 28),

    TIER_8(8, "§dGrande cité", Material.BELL, "Chapitre VIII — L'or, le verre et le feu",
            List.of(
                    "§6Alderic §f: §oIsabeau a déchiffré les registres : la Nuit Noire est venue d'une Porte, ouverte avec des yeux de feu et de néant.",
                    "§6Alderic §f: §oLe feu de ces yeux vient des bâtons de blaze, au cœur des forteresses du Nether. Anselme sait les préparer.",
                    "§6Alderic §f: §oEt la cathédrale renaîtra : Léon porte au cou le dernier éclat de la rosace, Béatrice connaît les secrets du trésor.",
                    "§6Alderic §f: §oUne bannière blanche approche de la ville. À sa tête... une jeune fille en armure ? Les gardes n'en reviennent pas."
            ),
            List.of(
                    claim("t8_claim", "§dLa grande cité",
                            "Claimez §f{n} chunks §7au total.", 175),
                    recruit("t6_leon", "§aLe verrier du désert",
                            "Trouvez §aLéon §7dans les dunes : la rosace de Valmont doit renaître.",
                            CityNPC.GLASSMAKER, 800,
                            Biome.DESERT),
                    recruit("t6_beatrice", "§eL'orfèvre",
                            "Trouvez §eBéatrice §7près des terres d'or et ouvrez-lui un comptoir.",
                            CityNPC.GOLDSMITH, 800,
                            Biome.BADLANDS, Biome.WOODED_BADLANDS, Biome.ERODED_BADLANDS, Biome.SAVANNA),
                    build("t8_buildings", "§dLe parvis de la cathédrale",
                            "Délimitez §f{n} bâtiments §7dans la ville.", 16),
                    deliver("t6_windows", "§aLes vitraux de la cathédrale",
                            "Livrez §f{n} verres teintés §7(toutes couleurs) pour la nouvelle rosace.",
                            Material.RED_STAINED_GLASS, MaterialGroups.STAINED_GLASS, 256),
                    deliver("t6_bell", "§6La cloche de la cathédrale",
                            "Livrez §f{n} cloche §7(Thorvald sait en couler).",
                            Material.BELL, Set.of(Material.BELL), 1),
                    stock("t6_treasury", "§eLe trésor de la cité",
                            "Constituez une réserve de §f{n} lingots d'or §7chez Béatrice.",
                            Material.GOLD_INGOT, CityNPC.GOLDSMITH, Material.GOLD_INGOT, 128),
                    kill("t6_blazes", "§6Les gardiens de feu",
                            "Abattez §f{n} blazes §7dans les forteresses du Nether.",
                            Material.BLAZE_ROD, EntityType.BLAZE, 20),
                    recruit("b8_jeanne", "§e★ La Pucelle",
                            "§8[Bonus] §7Une jeune femme en armure mène une compagnie vers la cathédrale. Elle dit entendre des voix. Offrez-lui un quartier général digne de ce nom.",
                            CityNPC.JEANNE, 800,
                            Biome.PLAINS, Biome.SUNFLOWER_PLAINS, Biome.FOREST).asOptional()
            ),
            8500, 32),

    TIER_9(9, "§5Citadelle", Material.ENDER_EYE, "Chapitre IX — La Porte du Néant",
            List.of(
                    "§6Alderic §f: §oLes registres ne mentaient pas : la Porte dort sous la terre, au fond d'un fort oublié.",
                    "§6Alderic §f: §oLe dragon qui a brûlé Valmont vit toujours, au-delà. Il faut douze yeux pour rouvrir le passage.",
                    "§6Alderic §f: §oThorvald forge nos meilleures lames, Anselme prépare ses potions, Roland entraîne la garde.",
                    "§6Alderic §f: §oIl est temps d'en finir. Pour Valmont."
            ),
            List.of(
                    deliver("t6_eyes", "§5Les clés de la Porte",
                            "Livrez §f{n} yeux de l'Ender §7pour rouvrir la Porte du Néant.",
                            Material.ENDER_EYE, Set.of(Material.ENDER_EYE), 12),
                    npcLevel("t9_blades", "§4Les lames de la vengeance",
                            "§4Thorvald §7doit atteindre le rang §fExpert §7: il forge les armes de l'assaut.",
                            Material.DIAMOND_SWORD, CityNPC.BLACKSMITH, 4),
                    npcLevel("t9_potions", "§5Les potions de la Porte",
                            "§5Frère Anselme §7doit atteindre le rang §fCompagnon §7: ses potions protégeront l'assaut.",
                            Material.BREWING_STAND, CityNPC.MONK, 3),
                    npcLevel("t9_guard", "§9La garde de Valmont",
                            "§9Roland §7doit atteindre le rang §fCompagnon §7: la garde est prête.",
                            Material.IRON_CHESTPLATE, CityNPC.GUARD, 3),
                    kill("t6_dragon", "§5Venger Valmont",
                            "Terrassez §fle dragon de l'Ender§7, celui qui a brûlé Valmont.",
                            Material.DRAGON_HEAD, EntityType.ENDER_DRAGON, 1)
            ),
            12000, 40),

    /* =========================================================
       ÉPILOGUE — LA NOUVELLE VALMONT
       ========================================================= */

    TIER_10(10, "§6§lCapitale", Material.GOLDEN_HELMET, "Chapitre X — La nouvelle Valmont",
            List.of(
                    "§6Alderic §f: §oLe dragon est tombé. Valmont est vengée... et redevient la capitale des Marches !",
                    "§6Alderic §f: §oLes chansons de Rosalie porteront vos noms pendant des siècles. Mais une capitale doit rester vigilante.",
                    "§6Alderic §f: §oD'anciens fléaux dorment encore : dans les profondeurs, sous les mers, dans les forteresses du Nether.",
                    "§6Alderic §f: §oUn chasseur de monstres aux cheveux blancs propose ses services... contre rémunération.",
                    "§6Alderic §f: §oEt l'on parle d'un jeune Viking qui apprivoise les dragons. Après la Nuit Noire... j'ai peine à y croire.",
                    "§6Alderic §f: §oUn géant en peau de lion a étranglé un ours à mains nues près des sommets. Il demande où sont les vrais monstres."
            ),
            List.of(
                    kill("t7_wither", "§8★ Le Fléau noir",
                            "§8[Haut fait] §7Invoquez et terrassez §fle Wither§7.",
                            Material.WITHER_SKELETON_SKULL, EntityType.WITHER, 1).asOptional(),
                    kill("t7_warden", "§3★ Le gardien des profondeurs",
                            "§8[Haut fait] §7Abattez §fun Warden §7dans une cité antique.",
                            Material.SCULK_CATALYST, EntityType.WARDEN, 1).asOptional(),
                    kill("t7_elders", "§b★ Les gardiens des abysses",
                            "§8[Haut fait] §7Terrassez §f{n} gardiens des abysses§7.",
                            Material.PRISMARINE_SHARD, EntityType.ELDER_GUARDIAN, 3).asOptional(),
                    claim("t7_borders", "§a★ Les frontières du royaume",
                            "§8[Haut fait] §7Claimez §f{n} chunks §7au total.", 300).asOptional(),
                    npcLevel("t7_forge_master", "§4★ Maître forgeron",
                            "§8[Haut fait] §4Thorvald §7atteint le rang §fMaître§7.",
                            Material.NETHERITE_INGOT, CityNPC.BLACKSMITH, 5).asOptional(),
                    npcLevel("t7_gold_master", "§e★ Maîtresse orfèvre",
                            "§8[Haut fait] §eBéatrice §7atteint le rang §fMaître§7.",
                            Material.GOLD_BLOCK, CityNPC.GOLDSMITH, 5).asOptional(),
                    recruit("t7_geralt", "§7★ Le sorceleur",
                            "§8[Bonus] §7Un chasseur de monstres propose ses services. Trouvez-le dans les marais.",
                            CityNPC.GERALT, 1000,
                            Biome.DARK_FOREST, Biome.SWAMP, Biome.MANGROVE_SWAMP).asOptional(),
                    // Après la chute du dragon de l'End : les îles extérieures s'ouvrent aux dragonniers
                    recruit("t7_harold", "§c★ Le dompteur de dragons",
                            "§8[Bonus] §7Un jeune Viking affirme qu'on peut apprivoiser les dragons. Trouvez-le sur les sommets glacés.",
                            CityNPC.HAROLD, 1100,
                            Biome.JAGGED_PEAKS, Biome.FROZEN_PEAKS, Biome.SNOWY_SLOPES, Biome.STONY_PEAKS,
                            Biome.GROVE).asOptional(),
                    recruit("b10_hercule", "§d★ Les travaux du héros",
                            "§8[Bonus] §7Un colosse vêtu d'une peau de lion cherche des monstres dignes de lui. Valmont en a à revendre.",
                            CityNPC.HERCULE, 1000,
                            Biome.SAVANNA, Biome.SAVANNA_PLATEAU, Biome.WINDSWEPT_SAVANNA, Biome.WINDSWEPT_HILLS).asOptional()
            ),
            0, 0);

    public final int             level;
    public final String          tierName;
    /** Icône du chapitre (arbre de progression, onglet de progrès). */
    public final Material        icon;
    public final String          chapter;
    public final List<String>    story;
    public final List<CityQuest> quests;
    /** Prix de la charte (payé par la caisse) pour passer au palier suivant. */
    public final int             upgradeCost;
    /** Emplacements de chunks offerts au passage au palier suivant. */
    public final int             rewardChunks;

    CityTier(int level, String tierName, Material icon, String chapter, List<String> story,
             List<CityQuest> quests, int upgradeCost, int rewardChunks) {
        this.level        = level;
        this.tierName     = tierName;
        this.icon         = icon;
        this.chapter      = chapter;
        this.story        = story;
        this.quests       = quests;
        this.upgradeCost  = upgradeCost;
        this.rewardChunks = rewardChunks;
    }

    /** Retourne le palier correspondant à un niveau de ville. */
    public static CityTier fromLevel(int level) {
        for (CityTier tier : values()) {
            if (tier.level == level) return tier;
        }
        return TIER_1;
    }

    /** Retourne le palier suivant, ou null si déjà au max. */
    public CityTier next() {
        CityTier[] vals = values();
        for (int i = 0; i < vals.length - 1; i++) {
            if (vals[i] == this) return vals[i + 1];
        }
        return null;
    }

    public boolean isMaxTier() {
        return next() == null;
    }
}
