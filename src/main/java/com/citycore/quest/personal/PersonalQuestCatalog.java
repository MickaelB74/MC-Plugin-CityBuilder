package com.citycore.quest.personal;

import com.citycore.integration.ItemIds;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.advancement.Advancement;
import org.bukkit.block.Biome;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.function.Predicate;

import static com.citycore.quest.personal.NPCPersonalQuest.of;
import static com.citycore.quest.personal.NPCPersonalQuest.withAmount;
import static com.citycore.quest.personal.PersonalQuestDefinition.TriggerType.*;

/**
 * Quêtes personnelles de chaque personnage, débloquées par son niveau (rang).
 *
 * Elles prolongent son histoire et le relient à un pan de Minecraft : le bûcheron envoie
 * explorer les forêts, le mineur descend au fond du monde, la tavernière veut un banquet…
 * Récompense en coins, une seule fois par joueur.
 */
public final class PersonalQuestCatalog {

    private PersonalQuestCatalog() {}

    /* =========================
       PERSONNAGES PRINCIPAUX
       ========================= */

    public static Map<Integer, List<NPCPersonalQuest>> bastien() {
        return Map.of(
                1, List.of(of("bastien_dark_forest", "§2🌲 Le bois noir",
                        "Pénétrer dans une forêt noire.", Material.DARK_OAK_SAPLING,
                        MOVE, inBiome(Biome.DARK_FOREST), 60)),
                2, List.of(withAmount("bastien_spiders", "§2🕷 Nettoyer les bois",
                        "Tuer 15 araignées qui infestent les forêts.", Material.STRING,
                        KILL, killed(EntityType.SPIDER, EntityType.CAVE_SPIDER), 15, 90)),
                3, List.of(of("bastien_cherry", "§d🌸 Les cerisiers en fleur",
                        "Trouver un bosquet de cerisiers.", Material.CHERRY_SAPLING,
                        MOVE, inBiome(Biome.CHERRY_GROVE), 150)),
                4, List.of(of("bastien_mangrove", "§2🌳 Les palétuviers",
                        "Patauger dans un marais de palétuviers.", Material.MANGROVE_PROPAGULE,
                        MOVE, inBiome(Biome.MANGROVE_SWAMP), 200)),
                5, List.of(of("bastien_nether_woods", "§4🍄 Les bois de l'Enfer",
                        "Marcher dans une forêt carmin ou biscornue du Nether.", Material.CRIMSON_FUNGUS,
                        MOVE, inBiome(Biome.CRIMSON_FOREST, Biome.WARPED_FOREST), 350))
        );
    }

    public static Map<Integer, List<NPCPersonalQuest>> helene() {
        return Map.of(
                1, List.of(withAmount("helene_bread", "§e🍞 Mains de boulangère",
                        "Faire du pain 10 fois à l'établi.", Material.BREAD,
                        CRAFT, crafted(m -> m == Material.BREAD), 10, 50)),
                2, List.of(of("helene_sunflowers", "§e🌻 Les tournesols",
                        "Trouver une plaine de tournesols.", Material.SUNFLOWER,
                        MOVE, inBiome(Biome.SUNFLOWER_PLAINS), 100)),
                3, List.of(of("helene_lush", "§a🌿 Les grottes luxuriantes",
                        "Descendre dans une grotte luxuriante.", Material.GLOW_BERRIES,
                        MOVE, inBiome(Biome.LUSH_CAVES), 150)),
                4, List.of(withAmount("helene_breed", "§6🐄 Le cheptel",
                        "Faire naître 20 animaux.", Material.WHEAT,
                        BREED, bred(), 20, 200)),
                5, List.of(of("helene_sniffer", "§2🌱 Les graines anciennes",
                        "Planter une graine de renifleur (succès « Planting the Past »).",
                        Material.TORCHFLOWER_SEEDS,
                        ADVANCEMENT, advancement("husbandry/plant_any_sniffer_seed"), 500))
        );
    }

    public static Map<Integer, List<NPCPersonalQuest>> brennan() {
        return Map.of(
                1, List.of(of("brennan_lookout", "§7⛰ Le belvédère",
                        "Atteindre Y ≥ 180 en montagne.", Material.STONE,
                        MOVE, inBiomeAboveY(180, MOUNTAINS), 80)),
                2, List.of(of("brennan_deepslate", "§8⛏ Au cœur de la roche",
                        "Descendre sous Y = -50.", Material.DEEPSLATE,
                        MOVE, belowY(-50), 120)),
                3, List.of(of("brennan_dripstone", "§6🪨 Les orgues de pierre",
                        "Explorer des grottes de spéléothèmes.", Material.POINTED_DRIPSTONE,
                        MOVE, inBiome(Biome.DRIPSTONE_CAVES), 150)),
                4, List.of(of("brennan_obsidian", "§5❄ La pierre de lave",
                        "Former de l'obsidienne (succès « Ice Bucket Challenge »).", Material.OBSIDIAN,
                        ADVANCEMENT, advancement("story/form_obsidian"), 200)),
                5, List.of(of("brennan_crying", "§d💧 La pierre qui pleure",
                        "Obtenir de l'obsidienne pleureuse.", Material.CRYING_OBSIDIAN,
                        ADVANCEMENT, advancement("nether/obtain_crying_obsidian"), 350))
        );
    }

    public static Map<Integer, List<NPCPersonalQuest>> martin() {
        return Map.of(
                1, List.of(withAmount("martin_first_catch", "§b🎣 Première prise",
                        "Pêcher 15 poissons.", Material.COD,
                        FISH, fished(Material.COD, Material.SALMON, Material.TROPICAL_FISH, Material.PUFFERFISH),
                        15, 50)),
                2, List.of(withAmount("martin_puffer", "§e🐡 Le poisson-globe",
                        "Pêcher 3 poissons-globes.", Material.PUFFERFISH,
                        FISH, fished(Material.PUFFERFISH), 3, 80)),
                3, List.of(of("martin_deep_dive", "§9🌊 Les lumières sous l'eau",
                        "Plonger sous Y = 30 dans un océan profond.", Material.PRISMARINE_SHARD,
                        MOVE, deepDive(), 120)),
                4, List.of(withAmount("martin_treasure", "§6💰 Pêche au trésor",
                        "Remonter 3 trésors à la ligne (livre, selle, étiquette, coquille…).",
                        Material.NAUTILUS_SHELL,
                        FISH, fished(Material.ENCHANTED_BOOK, Material.NAME_TAG, Material.SADDLE,
                                Material.NAUTILUS_SHELL, Material.BOW, Material.FISHING_ROD), 3, 250)),
                5, List.of(withAmount("martin_miraculous", "§b🐟 Pêche miraculeuse",
                        "Pêcher 200 poissons.", Material.FISHING_ROD,
                        FISH, fished(Material.COD, Material.SALMON, Material.TROPICAL_FISH, Material.PUFFERFISH),
                        200, 400))
        );
    }

    public static Map<Integer, List<NPCPersonalQuest>> godefroy() {
        return Map.of(
                1, List.of(of("godefroy_descent", "§8⛏ La descente",
                        "Descendre sous Y = 0.", Material.IRON_PICKAXE,
                        MOVE, belowY(0), 40)),
                2, List.of(of("godefroy_bedrock", "§8🪨 Les fondements du monde",
                        "Atteindre Y = -58, tout au fond.", Material.BEDROCK,
                        MOVE, belowY(-58), 100)),
                3, List.of(of("godefroy_diamonds", "§b💎 Diamants !",
                        "Miner un diamant.", Material.DIAMOND,
                        ADVANCEMENT, advancement("story/mine_diamond"), 150)),
                4, List.of(of("godefroy_deep_dark", "§3👂 Le grondement",
                        "Pénétrer dans les Profondeurs obscures.", Material.SCULK,
                        MOVE, inBiome(Biome.DEEP_DARK), 300)),
                5, List.of(of("godefroy_debris", "§4🔥 Les débris antiques",
                        "Extraire des débris antiques dans le Nether.", Material.ANCIENT_DEBRIS,
                        ADVANCEMENT, advancement("nether/obtain_ancient_debris"), 500))
        );
    }

    public static Map<Integer, List<NPCPersonalQuest>> rosalie() {
        return Map.of(
                1, List.of(of("rosalie_cake", "§c🎂 Le gâteau de la taverne",
                        "Préparer un gâteau.", Material.CAKE,
                        CRAFT, crafted(m -> m == Material.CAKE), 40)),
                2, List.of(withAmount("rosalie_rabbits", "§c🐇 Civet de lapin",
                        "Chasser 10 lapins.", Material.RABBIT,
                        KILL, killed(EntityType.RABBIT), 10, 60)),
                3, List.of(withAmount("rosalie_cattle", "§6🐄 Le bétail",
                        "Faire naître 10 vaches.", Material.BEEF,
                        BREED, bred(EntityType.COW, EntityType.MOOSHROOM), 10, 120)),
                4, List.of(withAmount("rosalie_hoglin", "§4🐗 Le sanglier de l'Enfer",
                        "Chasser 3 hoglins dans le Nether.", Material.COOKED_PORKCHOP,
                        KILL, killed(EntityType.HOGLIN), 3, 200)),
                5, List.of(of("rosalie_banquet", "§6🍽 Le grand banquet",
                        "Goûter à tous les plats (succès « A Balanced Diet »).", Material.GOLDEN_CARROT,
                        ADVANCEMENT, advancement("husbandry/balanced_diet"), 600))
        );
    }

    public static Map<Integer, List<NPCPersonalQuest>> thorvald() {
        return Map.of(
                1, List.of(of("thorvald_iron", "§7🔥 L'épreuve du feu",
                        "Fondre du fer.", Material.IRON_INGOT,
                        ADVANCEMENT, advancement("story/smelt_iron"), 30)),
                2, List.of(of("thorvald_armor", "§7🛡 Armure de fer",
                        "Revêtir une pièce d'armure de fer.", Material.IRON_CHESTPLATE,
                        ADVANCEMENT, advancement("story/obtain_armor"), 60)),
                3, List.of(of("thorvald_anvil", "§8⚒ L'enclume",
                        "Forger une enclume.", Material.ANVIL,
                        CRAFT, crafted(m -> m == Material.ANVIL), 120)),
                4, List.of(of("thorvald_diamond_gear", "§b💎 Couvert de diamants",
                        "Porter une armure complète de diamant.", Material.DIAMOND_CHESTPLATE,
                        ADVANCEMENT, advancement("story/shiny_gear"), 250)),
                5, List.of(of("thorvald_netherite", "§4🔨 Couvert de débris",
                        "Porter une armure complète de netherite.", Material.NETHERITE_CHESTPLATE,
                        ADVANCEMENT, advancement("nether/netherite_armor"), 800))
        );
    }

    public static Map<Integer, List<NPCPersonalQuest>> agathe() {
        return Map.of(
                1, List.of(withAmount("agathe_sheep", "§d🐑 Le troupeau",
                        "Faire naître 5 moutons.", Material.WHITE_WOOL,
                        BREED, bred(EntityType.SHEEP), 5, 50)),
                2, List.of(of("agathe_flower_forest", "§d🌷 La forêt fleurie",
                        "Trouver une forêt fleurie.", Material.ALLIUM,
                        MOVE, inBiome(Biome.FLOWER_FOREST), 90)),
                3, List.of(of("agathe_swamp", "§3🪻 Les orchidées des marais",
                        "Explorer un marais.", Material.BLUE_ORCHID,
                        MOVE, inBiome(Biome.SWAMP, Biome.MANGROVE_SWAMP), 100)),
                4, List.of(withAmount("agathe_banners", "§6🚩 Bannières de guilde",
                        "Confectionner 5 bannières.", Material.WHITE_BANNER,
                        CRAFT, crafted(m -> m.name().endsWith("_BANNER")), 5, 150)),
                5, List.of(withAmount("agathe_beds", "§c🛏 Les dortoirs",
                        "Confectionner 16 lits.", Material.RED_BED,
                        CRAFT, crafted(m -> m.name().endsWith("_BED")), 16, 250))
        );
    }

    public static Map<Integer, List<NPCPersonalQuest>> edmond() {
        return Map.of(
                1, List.of(of("edmond_tame", "§6🐎 Le cheval sauvage",
                        "Apprivoiser un animal.", Material.SADDLE,
                        ADVANCEMENT, advancement("husbandry/tame_an_animal"), 40)),
                2, List.of(withAmount("edmond_breed", "§6🐣 Naissances à l'écurie",
                        "Faire naître 10 animaux.", Material.HAY_BLOCK,
                        BREED, bred(), 10, 80)),
                3, List.of(of("edmond_gallop", "§e🏇 Galop dans la savane",
                        "Chevaucher un cheval dans la savane.", Material.LEATHER_HORSE_ARMOR,
                        MOVE, ridingHorseIn(Biome.SAVANNA, Biome.SAVANNA_PLATEAU, Biome.WINDSWEPT_SAVANNA), 120)),
                4, List.of(of("edmond_wolf_armor", "§7🐺 Armure de loup",
                        "Fabriquer une armure de loup.", Material.WOLF_ARMOR,
                        CRAFT, crafted(m -> m == Material.WOLF_ARMOR), 150)),
                5, List.of(of("edmond_two_by_two", "§6🦒 Deux par deux",
                        "Faire se reproduire tous les animaux (succès « Two by Two »).", Material.GOLDEN_CARROT,
                        ADVANCEMENT, advancement("husbandry/bred_all_animals"), 500))
        );
    }

    public static Map<Integer, List<NPCPersonalQuest>> roland() {
        return Map.of(
                1, List.of(withAmount("roland_night_watch", "§9🌙 La ronde de nuit",
                        "Tuer 20 morts-vivants (zombies et apparentés).", Material.ROTTEN_FLESH,
                        KILL, killed(EntityType.ZOMBIE, EntityType.HUSK, EntityType.DROWNED,
                                EntityType.ZOMBIE_VILLAGER), 20, 40)),
                2, List.of(withAmount("roland_pillagers", "§c⚔ Les pillards",
                        "Tuer 10 pillards.", Material.CROSSBOW,
                        KILL, killed(EntityType.PILLAGER), 10, 120)),
                3, List.of(of("roland_sniper", "§e🏹 Tireur d'élite",
                        "Abattre un squelette à plus de 50 blocs.", Material.BOW,
                        ADVANCEMENT, advancement("adventure/sniper_duel"), 200)),
                4, List.of(of("roland_hero", "§6🛡 Héros du village",
                        "Repousser un raid de pillards.", Material.WHITE_BANNER,
                        ADVANCEMENT, advancement("adventure/hero_of_the_village"), 400)),
                5, List.of(of("roland_monsters", "§4☠ Chasseur de monstres",
                        "Tuer chaque monstre au moins une fois (« Monsters Hunted »).", Material.DIAMOND_SWORD,
                        ADVANCEMENT, advancement("adventure/kill_all_mobs"), 800))
        );
    }

    public static Map<Integer, List<NPCPersonalQuest>> anselme() {
        return Map.of(
                1, List.of(of("anselme_honey", "§6🍯 Le rucher",
                        "Récolter du miel sans irriter les abeilles.", Material.HONEY_BOTTLE,
                        ADVANCEMENT, advancement("husbandry/safely_harvest_honey"), 50)),
                2, List.of(of("anselme_brew", "§5⚗ La première potion",
                        "Brasser une potion.", Material.BREWING_STAND,
                        ADVANCEMENT, advancement("nether/brew_potion"), 100)),
                3, List.of(of("anselme_blaze", "§6🔥 Le bâton de feu",
                        "Obtenir un bâton de blaze.", Material.BLAZE_ROD,
                        ADVANCEMENT, advancement("nether/obtain_blaze_rod"), 150)),
                4, List.of(of("anselme_doctor", "§a✚ Le médecin des morts",
                        "Guérir un villageois zombie.", Material.GOLDEN_APPLE,
                        ADVANCEMENT, advancement("story/cure_zombie_villager"), 300)),
                5, List.of(of("anselme_cocktail", "§d🧪 Le cocktail furieux",
                        "Subir tous les effets de potion (« A Furious Cocktail »).", Material.POTION,
                        ADVANCEMENT, advancement("nether/all_potions"), 800))
        );
    }

    public static Map<Integer, List<NPCPersonalQuest>> isabeau() {
        return Map.of(
                1, List.of(withAmount("isabeau_library", "§3📚 Les rayonnages",
                        "Fabriquer 3 bibliothèques.", Material.BOOKSHELF,
                        CRAFT, crafted(m -> m == Material.BOOKSHELF), 3, 40)),
                2, List.of(withAmount("isabeau_maps", "§3🗺 Cartographe",
                        "Fabriquer 3 cartes vierges.", Material.MAP,
                        CRAFT, crafted(m -> m == Material.MAP), 3, 60)),
                3, List.of(of("isabeau_sherd", "§6🏺 Les ruines oubliées",
                        "Exhumer un tesson de poterie au pinceau.", Material.BRUSH,
                        ADVANCEMENT, advancement("adventure/salvage_sherd"), 150)),
                4, List.of(of("isabeau_eye", "§5👁 L'Œil de l'Ender",
                        "Suivre un œil de l'Ender jusqu'à une forteresse.", Material.ENDER_EYE,
                        ADVANCEMENT, advancement("story/follow_ender_eye"), 300)),
                5, List.of(of("isabeau_the_end", "§5🌌 La Porte du Néant",
                        "Franchir le portail de l'End.", Material.END_PORTAL_FRAME,
                        ADVANCEMENT, advancement("story/enter_the_end"), 500))
        );
    }

    public static Map<Integer, List<NPCPersonalQuest>> leon() {
        return Map.of(
                1, List.of(of("leon_desert", "§e🏜 Les dunes",
                        "Traverser un désert.", Material.SAND,
                        MOVE, inBiome(Biome.DESERT), 40)),
                2, List.of(of("leon_badlands", "§6🏞 Les terres cuites",
                        "Explorer des badlands.", Material.TERRACOTTA,
                        MOVE, inBiome(Biome.BADLANDS, Biome.ERODED_BADLANDS, Biome.WOODED_BADLANDS), 100)),
                3, List.of(withAmount("leon_husks", "§e🧟 Les morts du désert",
                        "Tuer 10 zombies desséchés.", Material.ROTTEN_FLESH,
                        KILL, killed(EntityType.HUSK), 10, 80)),
                4, List.of(withAmount("leon_rose", "§c🌹 La Rose de Valmont",
                        "Fabriquer 10 fois des vitres teintées.", Material.RED_STAINED_GLASS_PANE,
                        CRAFT, crafted(m -> m.name().endsWith("_STAINED_GLASS_PANE")), 10, 150)),
                5, List.of(of("leon_beacon", "§b✨ Le phare de la cité",
                        "Ériger une balise.", Material.BEACON,
                        ADVANCEMENT, advancement("nether/create_beacon"), 400))
        );
    }

    public static Map<Integer, List<NPCPersonalQuest>> beatrice() {
        return Map.of(
                1, List.of(of("beatrice_trade", "§e🤝 L'art du marchand",
                        "Commercer avec un villageois.", Material.EMERALD,
                        ADVANCEMENT, advancement("adventure/trade"), 50)),
                2, List.of(of("beatrice_piglin", "§6✨ Ça brille !",
                        "Distraire un piglin avec de l'or.", Material.GOLD_INGOT,
                        ADVANCEMENT, advancement("nether/distract_piglin"), 100)),
                3, List.of(of("beatrice_summit", "§a⛰ Les pics d'émeraude",
                        "Atteindre le sommet d'une montagne.", Material.EMERALD_ORE,
                        SUMMIT, always(), 150)),
                4, List.of(of("beatrice_bastion", "§4🏰 Le trésor des piglins",
                        "Piller un coffre de bastion.", Material.GOLD_BLOCK,
                        ADVANCEMENT, advancement("nether/loot_bastion"), 300)),
                5, List.of(of("beatrice_lodestone", "§7🧭 Le chemin du retour",
                        "Lier une boussole à une magnétite.", Material.LODESTONE,
                        ADVANCEMENT, advancement("nether/use_lodestone"), 300))
        );
    }

    /* =========================
       PERSONNAGES SECONDAIRES
       ========================= */

    public static Map<Integer, List<NPCPersonalQuest>> robin() {
        return Map.of(
                1, List.of(withAmount("robin_pillagers", "§2🏹 Le prince des voleurs",
                        "Tuer 5 pillards.", Material.CROSSBOW,
                        KILL, killed(EntityType.PILLAGER), 5, 50)),
                2, List.of(of("robin_bullseye", "§2🎯 Dans le mille",
                        "Toucher le centre d'une cible.", Material.TARGET,
                        ADVANCEMENT, advancement("adventure/bullseye"), 80)),
                3, List.of(of("robin_captain", "§2🚩 Le capitaine des pillards",
                        "Tuer un capitaine de raid.", Material.WHITE_BANNER,
                        ADVANCEMENT, advancement("adventure/voluntary_exile"), 150)),
                4, List.of(of("robin_two_birds", "§2🐦 Deux oiseaux, une flèche",
                        "Tuer deux phantoms d'une seule flèche perforante.", Material.PHANTOM_MEMBRANE,
                        ADVANCEMENT, advancement("adventure/two_birds_one_arrow"), 250)),
                5, List.of(of("robin_pillager_now", "§2⚔ Qui est le pillard ?",
                        "Retourner l'arbalète contre un pillard (« Who's the Pillager Now? »).",
                        Material.ARROW,
                        ADVANCEMENT, advancement("adventure/whos_the_pillager_now"), 300))
        );
    }

    /**
     * Jack Sparrow (mod Tax' Ocean Villager) : les navires que le mod fait croiser au large.
     * Aborder un pirate, commercer avec son équipage, « réquisitionner » un vaisseau de la Marine
     * en y hissant le pavillon noir, braver un navire maudit au clair de lune, puis vaincre le
     * roi pirate illageois sur son vaisseau de guerre.
     */
    public static Map<Integer, List<NPCPersonalQuest>> jack() {
        return Map.of(
                1, List.of(of("jack_board", "§3⚓ À l'abordage !",
                        "Monter à bord d'un navire pirate croisant au large.", Material.OAK_BOAT,
                        MOVE, aboard(TAXOV_PIRATE_SHIP, PIRATE_MARKERS), 120)),
                2, List.of(of("jack_smuggle", "§6🍾 Contrebande",
                        "Commercer avec un équipage pirate (capitaine ou matelot pirate).", Material.EMERALD,
                        TRADE, tradedWith(TAXOV_PIRATES), 200)),
                3, List.of(of("jack_commandeer", "§9🏴 Réquisitionner ! (terme nautique)",
                        "Hisser une bannière noire à bord d'un navire de la Marine.", Material.BLACK_BANNER,
                        PLACE, hoisted(TAXOV_MARINE_SHIP, MARINE_MARKERS), 300)),
                4, List.of(of("jack_cursed", "§7💀 La malédiction de l'or aztèque",
                        "Aborder de nuit un navire maudit (pirates ou marins zombies).", Material.GOLD_NUGGET,
                        MOVE, aboardCursedAtNight(TAXOV_CURSED_SHIPS), 450)),
                5, List.of(of("jack_pirate_king", "§6👑 Le roi des pirates",
                        "Terrasser le roi pirate illageois sur son vaisseau de guerre.", Material.GOLDEN_HELMET,
                        KILL, killedMod("taxov:illager_pirate_king", "tax.taknax.taxov.entity.IllagerPirateKingEntity"), 900))
        );
    }

    public static Map<Integer, List<NPCPersonalQuest>> indiana() {
        return Map.of(
                1, List.of(of("indiana_jungle", "§a🌴 Le temple perdu",
                        "Explorer une jungle.", Material.BAMBOO,
                        MOVE, inBiome(Biome.JUNGLE, Biome.BAMBOO_JUNGLE, Biome.SPARSE_JUNGLE), 50)),
                2, List.of(of("indiana_mushrooms", "§d🍄 L'île aux champignons",
                        "Trouver des champs de champignons.", Material.RED_MUSHROOM_BLOCK,
                        MOVE, inBiome(Biome.MUSHROOM_FIELDS), 150)),
                3, List.of(of("indiana_ice", "§b❄ Les pics de glace",
                        "Trouver un champ de pics de glace.", Material.PACKED_ICE,
                        MOVE, inBiome(Biome.ICE_SPIKES), 150)),
                4, List.of(of("indiana_trials", "§6🗝 La chambre des épreuves",
                        "Entrer dans une chambre des épreuves.", Material.TRIAL_KEY,
                        ADVANCEMENT, advancement("adventure/minecraft_trials_edition"), 200)),
                5, List.of(of("indiana_adventuring", "§6📖 Le carnet du Graal",
                        "Visiter tous les biomes (« Adventuring Time »).", Material.FILLED_MAP,
                        ADVANCEMENT, advancement("adventure/adventuring_time"), 1000))
        );
    }

    public static Map<Integer, List<NPCPersonalQuest>> docbrown() {
        return Map.of(
                1, List.of(withAmount("docbrown_pistons", "§e⚙ Le convecteur temporel",
                        "Fabriquer 3 pistons.", Material.PISTON,
                        CRAFT, crafted(m -> m == Material.PISTON || m == Material.STICKY_PISTON), 3, 40)),
                2, List.of(withAmount("docbrown_fireworks", "§c🎆 1,21 gigawatt",
                        "Fabriquer 5 fois des fusées.", Material.FIREWORK_ROCKET,
                        CRAFT, crafted(m -> m == Material.FIREWORK_ROCKET), 5, 60)),
                3, List.of(of("docbrown_clouds", "§f☁ Au-dessus des nuages",
                        "Atteindre Y ≥ 300 dans le monde normal.", Material.FEATHER,
                        MOVE, overworldAboveY(300), 150)),
                4, List.of(of("docbrown_elytra", "§5🪽 Pas besoin de routes",
                        "Obtenir des élytres (« Sky's the Limit »).", Material.ELYTRA,
                        ADVANCEMENT, advancement("end/elytra"), 400)),
                5, List.of(of("docbrown_crafter", "§e🤖 La machine qui fabrique",
                        "Faire fabriquer un crafteur par un crafteur.", Material.CRAFTER,
                        ADVANCEMENT, advancement("adventure/crafters_crafting_crafters"), 300))
        );
    }

    public static Map<Integer, List<NPCPersonalQuest>> gandalf() {
        return Map.of(
                // Déclenchée par SummitListener (TriggerType.SUMMIT)
                1, List.of(of("gandalf_summit", "§6⛰ Conquérant des sommets",
                        "Atteindre le sommet d'une montagne.", Material.GOAT_HORN,
                        SUMMIT, always(), 100)),
                2, List.of(of("gandalf_enchant", "§d✨ L'art des enchantements",
                        "Enchanter un objet.", Material.ENCHANTING_TABLE,
                        ADVANCEMENT, advancement("story/enchant_item"), 60)),
                3, List.of(of("gandalf_fortress", "§4🏰 La forteresse",
                        "Trouver une forteresse du Nether.", Material.NETHER_BRICKS,
                        ADVANCEMENT, advancement("nether/find_fortress"), 150)),
                4, List.of(of("gandalf_dragon", "§5🐉 Libérer l'End",
                        "Terrasser le dragon de l'Ender.", Material.DRAGON_HEAD,
                        ADVANCEMENT, advancement("end/kill_dragon"), 600)),
                5, List.of(of("gandalf_end_city", "§d🏙 La cité au bout du monde",
                        "Trouver une cité de l'End.", Material.PURPUR_BLOCK,
                        ADVANCEMENT, advancement("end/find_end_city"), 500))
        );
    }

    public static Map<Integer, List<NPCPersonalQuest>> geralt() {
        return Map.of(
                1, List.of(withAmount("geralt_ghouls", "§7☠ Contrat : les goules",
                        "Tuer 10 zombies-villageois.", Material.ROTTEN_FLESH,
                        KILL, killed(EntityType.ZOMBIE_VILLAGER), 10, 80)),
                2, List.of(withAmount("geralt_ravager", "§7🐂 Contrat : le ravageur",
                        "Abattre 2 ravageurs.", Material.SADDLE,
                        KILL, killed(EntityType.RAVAGER), 2, 200)),
                3, List.of(of("geralt_wither", "§8💀 Contrat : le Fléau",
                        "Terrasser le Wither.", Material.NETHER_STAR,
                        KILL, killed(EntityType.WITHER), 600)),
                4, List.of(of("geralt_warden", "§3👁 Contrat : le gardien aveugle",
                        "Abattre un Warden.", Material.SCULK_SHRIEKER,
                        KILL, killed(EntityType.WARDEN), 1000)),
                5, List.of(of("geralt_dragon", "§5🐉 Contrat : le dragon",
                        "Porter le coup fatal à un dragon de l'Ender.", Material.DRAGON_EGG,
                        KILL, killed(EntityType.ENDER_DRAGON), 1000))
        );
    }

    /**
     * Harold (mod Dragon Mounts Remastered) : trouver un œuf, le faire éclore, apprivoiser son
     * dragon au poisson, puis voler — au-dessus des nuages, enfin jusqu'aux îles de l'End.
     * Les trois premiers récits suivent les succès du mod (dmr:…), les deux derniers le vol.
     */
    public static Map<Integer, List<NPCPersonalQuest>> harold() {
        return Map.of(
                1, List.of(of("harold_egg", "§e🥚 Un œuf bien étrange",
                        "Trouver un œuf de dragon dans un coffre (temples, igloos, trésors enfouis, forteresses…).",
                        Material.TURTLE_EGG,
                        ADVANCEMENT, modAdvancement("dmr:find_egg"), 120)),
                2, List.of(of("harold_hatch", "§a🐣 Ce n'est pas un chien !",
                        "Faire éclore un œuf de dragon : le poser, clic droit, puis patienter.",
                        Material.DRAGON_EGG,
                        ADVANCEMENT, modAdvancement("dmr:hatch_1_dragon_egg"), 200)),
                3, List.of(of("harold_tame", "§b🐟 Pourquoi du poisson ?",
                        "Apprivoiser un dragon en le nourrissant de poisson.",
                        Material.TROPICAL_FISH,
                        ADVANCEMENT, modAdvancement("dmr:tame_dragon"), 300)),
                4, List.of(of("harold_clouds", "§f☁ Au-dessus des nuages",
                        "Voler à dos de dragon au-dessus de Y = 200.",
                        Material.FEATHER,
                        MOVE, ridingDragon(p -> p.getWorld().getEnvironment() == World.Environment.NORMAL
                                && p.getLocation().getBlockY() >= 200), 450)),
                5, List.of(of("harold_outer_end", "§5🌌 Les îles du bout du monde",
                        "Survoler à dos de dragon les îles extérieures de l'End.",
                        Material.CHORUS_FLOWER,
                        MOVE, ridingDragon(p -> p.getWorld().getEnvironment() == World.Environment.THE_END
                                && OUTER_END.contains(p.getLocation().getBlock().getBiome())), 800))
        );
    }

    /* =========================
       VALIDATEURS RÉUTILISABLES
       ========================= */

    private static final Set<Biome> MOUNTAINS = EnumSet.of(
            Biome.WINDSWEPT_HILLS, Biome.WINDSWEPT_GRAVELLY_HILLS, Biome.WINDSWEPT_FOREST,
            Biome.WINDSWEPT_SAVANNA, Biome.JAGGED_PEAKS, Biome.FROZEN_PEAKS, Biome.STONY_PEAKS,
            Biome.MEADOW, Biome.GROVE, Biome.SNOWY_SLOPES, Biome.CHERRY_GROVE);

    private static final Set<Biome> DEEP_OCEANS = EnumSet.of(
            Biome.DEEP_OCEAN, Biome.DEEP_COLD_OCEAN, Biome.DEEP_FROZEN_OCEAN, Biome.DEEP_LUKEWARM_OCEAN);

    /** Îles extérieures de l'End (au-delà des portails ouverts par la mort du dragon). */
    private static final Set<Biome> OUTER_END = EnumSet.of(
            Biome.END_HIGHLANDS, Biome.END_MIDLANDS, Biome.SMALL_END_ISLANDS, Biome.END_BARRENS);

    // Navires et métiers du mod Tax' Ocean Villager (taxov)
    private static final Set<String> TAXOV_PIRATE_SHIP  = Set.of("taxov:pirate_ship");
    private static final Set<String> TAXOV_MARINE_SHIP  = Set.of("taxov:marine_ship");
    private static final Set<String> TAXOV_CURSED_SHIPS = Set.of("taxov:zombie_pirate_ship", "taxov:zombie_marine_ship");
    private static final Set<String> TAXOV_PIRATES      = Set.of("taxov:profession_pirate", "taxov:profession_crew");
    /**
     * Repères des navires du mod : les postes de travail de l'équipage font partie du gabarit du
     * navire. Ils sont là qu'il soit généré par le monde, posé avec le « générateur » du mod ou par
     * /place ; aucun point du pont n'en est à plus de 12 blocs (gabarits analysés : 9 à 12 x 28 à 30).
     */
    private static final Set<String> PIRATE_MARKERS = Set.of("taxov:block_pirate", "taxov:block_crew");
    private static final Set<String> MARINE_MARKERS = Set.of("taxov:block_captain", "taxov:block_sailor");
    private static final Set<String> SHIP_MARKERS   = Set.of("taxov:block_pirate", "taxov:block_crew",
            "taxov:block_captain", "taxov:block_sailor");
    /** Rayon (horizontal) et hauteurs de la recherche des repères autour du joueur. */
    private static final int MARKER_RADIUS = 12, MARKER_BELOW = 12, MARKER_ABOVE = 6;

    /** Dragon de Dragon Mounts Remastered : identifiant et classe de l'entité dans le mod. */
    private static final String DMR_DRAGON = "dmr:dragon";
    private static final String DMR_DRAGON_CLASS = "dmr.DragonMounts.server.entity.TameableDragonEntity";

    private static PersonalQuestValidator always() {
        return (player, ctx) -> true;
    }

    private static PersonalQuestValidator inBiome(Biome... biomes) {
        Set<Biome> set = EnumSet.of(biomes[0], biomes);
        return (player, ctx) -> set.contains(player.getLocation().getBlock().getBiome());
    }

    private static PersonalQuestValidator inBiomeAboveY(int y, Set<Biome> biomes) {
        return (player, ctx) -> player.getLocation().getBlockY() >= y
                && biomes.contains(player.getLocation().getBlock().getBiome());
    }

    private static PersonalQuestValidator belowY(int y) {
        return (player, ctx) -> player.getWorld().getEnvironment() == World.Environment.NORMAL
                && player.getLocation().getBlockY() <= y;
    }

    private static PersonalQuestValidator overworldAboveY(int y) {
        return (player, ctx) -> player.getWorld().getEnvironment() == World.Environment.NORMAL
                && player.getLocation().getBlockY() >= y;
    }

    private static PersonalQuestValidator deepDive() {
        return (player, ctx) -> player.isInWater()
                && player.getLocation().getBlockY() <= 30
                && DEEP_OCEANS.contains(player.getLocation().getBlock().getBiome());
    }

    private static PersonalQuestValidator ridingHorseIn(Biome... biomes) {
        Set<Biome> set = EnumSet.of(biomes[0], biomes);
        return (player, ctx) -> player.getVehicle() instanceof AbstractHorse
                && set.contains(player.getLocation().getBlock().getBiome());
    }

    /**
     * Le joueur est à bord d'un navire : debout dessus (ni dans l'eau, ni en barque), et soit dans
     * la structure générée par le monde, soit près d'un poste d'équipage du mod (navire posé à la
     * main). La recherche des repères est espacée d'au moins 1,5 s par joueur.
     */
    private static PersonalQuestValidator aboard(Set<String> structures, Set<String> markers) {
        Map<UUID, Long> lastScan = new HashMap<>();
        return (player, ctx) -> onDeck(player)
                && (inStructure(player.getLocation(), structures)
                || (scanAllowed(lastScan, player) && nearMarker(player.getLocation(), markers)));
    }

    /**
     * À bord d'un navire maudit, de nuit (au clair de lune, la malédiction se révèle) : structure
     * générée, ou navire posé à la main reconnu à ses postes d'équipage et à son équipage zombie.
     */
    private static PersonalQuestValidator aboardCursedAtNight(Set<String> structures) {
        Map<UUID, Long> lastScan = new HashMap<>();
        return (player, ctx) -> {
            long time = player.getWorld().getTime();
            if (time < 13000 || time > 23000 || !onDeck(player)) return false;
            if (inStructure(player.getLocation(), structures)) return true;
            return scanAllowed(lastScan, player) && nearMarker(player.getLocation(), SHIP_MARKERS)
                    && player.getNearbyEntities(16, 12, 16).stream()
                    .anyMatch(e -> e instanceof org.bukkit.entity.ZombieVillager);
        };
    }

    /** Une bannière noire posée à bord d'un navire (structure générée ou repères d'équipage). */
    private static PersonalQuestValidator hoisted(Set<String> structures, Set<String> markers) {
        return (player, ctx) -> ctx instanceof org.bukkit.block.Block b
                && (b.getType() == Material.BLACK_BANNER || b.getType() == Material.BLACK_WALL_BANNER)
                && (inStructure(b.getLocation(), structures) || nearMarker(b.getLocation(), markers));
    }

    /**
     * Debout sur un navire : ni dans l'eau ni en barque, et un bloc plein sous les pieds. Le bloc
     * porteur est lu juste sous la semelle (une dalle ou une marche de pont compte), avec deux
     * blocs de marge en dessous pour les sauts.
     */
    private static boolean onDeck(Player player) {
        if (player.isInWater() || player.getVehicle() != null) return false;
        org.bukkit.Location feet = player.getLocation();
        World w = feet.getWorld();
        int x = feet.getBlockX(), z = feet.getBlockZ();
        int y = (int) Math.floor(feet.getY() - 0.1);
        for (int dy = 0; dy <= 2; dy++) {
            if (w.getType(x, y - dy, z).isSolid()) return true;
        }
        return false;
    }

    /** Recherche coûteuse limitée à une fois toutes les 1,5 s par joueur. */
    private static boolean scanAllowed(Map<UUID, Long> lastScan, Player player) {
        long now = System.currentTimeMillis();
        Long last = lastScan.get(player.getUniqueId());
        if (last != null && now - last < 1500) return false;
        lastScan.put(player.getUniqueId(), now);
        return true;
    }

    /**
     * Un des blocs repères (objets du mod) se trouve-t-il à moins de 12 blocs (disque horizontal,
     * de 12 blocs dessous à 6 au-dessus) ? Seuls les chunks déjà chargés sont lus.
     */
    private static boolean nearMarker(org.bukkit.Location loc, Set<String> markerIds) {
        Set<Material> markers = new HashSet<>(); // pas d'EnumSet : matériaux de mods ajoutés à chaud
        for (String id : markerIds) {
            Material m = ItemIds.resolve(id);
            if (m != null) markers.add(m);
        }
        if (markers.isEmpty()) return false; // mod absent
        World w = loc.getWorld();
        int cx = loc.getBlockX(), cy = loc.getBlockY(), cz = loc.getBlockZ();
        int minY = Math.max(w.getMinHeight(), cy - MARKER_BELOW), maxY = Math.min(w.getMaxHeight() - 1, cy + MARKER_ABOVE);
        for (int dx = -MARKER_RADIUS; dx <= MARKER_RADIUS; dx++) {
            for (int dz = -MARKER_RADIUS; dz <= MARKER_RADIUS; dz++) {
                if (dx * dx + dz * dz > MARKER_RADIUS * MARKER_RADIUS) continue;
                int x = cx + dx, z = cz + dz;
                if (!w.isChunkLoaded(x >> 4, z >> 4)) continue;
                for (int y = minY; y <= maxY; y++) {
                    if (markers.contains(w.getType(x, y, z))) return true;
                }
            }
        }
        return false;
    }

    /** Échange avec un villageois exerçant un des métiers (clés « mod:métier »). */
    private static PersonalQuestValidator tradedWith(Set<String> professions) {
        return (player, ctx) -> {
            if (!(ctx instanceof org.bukkit.entity.Villager villager)) return false;
            try {
                return professions.contains(villager.getProfession().getKey().toString());
            } catch (RuntimeException e) {
                return false; // métier sans clé lisible
            }
        };
    }

    /** Créature d'un mod tuée par le joueur (clé, nom sur serveur hybride, ou classe du mod). */
    private static PersonalQuestValidator killedMod(String entityId, String modClassName) {
        return (player, ctx) -> ctx instanceof Entity e && ItemIds.entityIs(e, entityId, modClassName);
    }

    /**
     * Le point est-il dans l'une des structures indiquées (navires, villages…) ? Boîte de la
     * structure élargie d'un bloc et demi : le pont et le bastingage comptent.
     */
    private static boolean inStructure(org.bukkit.Location loc, Set<String> structures) {
        World world = loc.getWorld();
        if (world == null) return false;
        for (org.bukkit.generator.structure.GeneratedStructure s
                : world.getStructures(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) {
            String key;
            try {
                key = s.getStructure().getKey().toString();
            } catch (RuntimeException e) {
                continue;
            }
            if (structures.contains(key) && s.getBoundingBox().clone().expand(1.5).contains(loc.toVector())) return true;
        }
        return false;
    }

    /** Le joueur chevauche un dragon du mod, et la condition de lieu est remplie. */
    private static PersonalQuestValidator ridingDragon(Predicate<Player> where) {
        return (player, ctx) -> ItemIds.entityIs(player.getVehicle(), DMR_DRAGON, DMR_DRAGON_CLASS)
                && where.test(player);
    }

    private static PersonalQuestValidator killed(EntityType... types) {
        Set<EntityType> set = EnumSet.of(types[0], types);
        return (player, ctx) -> ctx instanceof Entity e && set.contains(e.getType());
    }

    /** Sans argument : n'importe quel animal. */
    private static PersonalQuestValidator bred(EntityType... types) {
        Set<EntityType> set = types.length == 0 ? Set.of() : EnumSet.of(types[0], types);
        return (player, ctx) -> ctx instanceof Entity e && (set.isEmpty() || set.contains(e.getType()));
    }

    private static PersonalQuestValidator fished(Material... mats) {
        Set<Material> set = EnumSet.of(mats[0], mats);
        return (player, ctx) -> ctx instanceof ItemStack i && set.contains(i.getType());
    }

    private static PersonalQuestValidator crafted(Predicate<Material> predicate) {
        return (player, ctx) -> ctx instanceof ItemStack i && predicate.test(i.getType());
    }

    /** Succès utilisés par les quêtes, clés complètes (vérifiés au démarrage, voir {@link #unknownAdvancements}). */
    private static final Set<String> USED_ADVANCEMENTS = new TreeSet<>();

    /**
     * Succès référencés par une quête mais absents du serveur (quête impossible).
     *
     * @param namespaceActive espaces de noms à vérifier : minecraft, et les mods présents (les
     *                        succès d'un mod absent ne sont pas signalés, ses habitants sont inactifs)
     */
    public static List<String> unknownAdvancements(Predicate<String> namespaceActive) {
        List<String> unknown = new ArrayList<>();
        for (String key : USED_ADVANCEMENTS) {
            NamespacedKey nk = NamespacedKey.fromString(key);
            if (nk == null || !namespaceActive.test(nk.getNamespace())) continue;
            if (Bukkit.getAdvancement(nk) == null) unknown.add(key);
        }
        return unknown;
    }

    /**
     * Succès Minecraft (clé vanilla, ex : "story/mine_diamond"). Vérifie la progression
     * du joueur : fonctionne à l'obtention ET à l'acceptation si déjà obtenu.
     */
    private static PersonalQuestValidator advancement(String key) {
        return advancementDone(NamespacedKey.minecraft(key));
    }

    /** Succès d'un mod, clé complète (ex : "dmr:tame_dragon"). */
    private static PersonalQuestValidator modAdvancement(String key) {
        NamespacedKey nk = NamespacedKey.fromString(key);
        if (nk == null) throw new IllegalArgumentException("clé de succès invalide : " + key);
        return advancementDone(nk);
    }

    private static PersonalQuestValidator advancementDone(NamespacedKey nk) {
        USED_ADVANCEMENTS.add(nk.toString());
        return (player, ctx) -> {
            Advancement adv = Bukkit.getAdvancement(nk);
            return adv != null && player.getAdvancementProgress(adv).isDone();
        };
    }
}
