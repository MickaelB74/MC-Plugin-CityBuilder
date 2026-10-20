package com.citycore.npc;

import com.citycore.quest.personal.NPCPersonalQuest;
import com.citycore.quest.personal.PersonalQuestCatalog;

import java.util.List;
import java.util.Map;

/**
 * Les habitants de la cité.
 *
 * Personnages principaux : les survivants de Valmont, artisans dispersés dans les terres
 * sauvages après la Nuit Noire. Chacun incarne un pan de Minecraft (bois, cultures, pierre,
 * pêche, mines, cuisine, forge, textile, élevage, combat, alchimie, savoir, verre, or) et
 * se recrute au fil des chapitres de la ville (voir CityTier).
 *
 * Personnages secondaires : des figures légendaires attirées par la renommée de la cité
 * (Robin des Bois, Jack Sparrow, Indiana Jones, Doc Brown, Gandalf, Geralt, Harold, et les
 * légendes liées aux mods : Robinson Crusoé, Capitaine Achab, Obélix, Miyamoto Musashi, Geppetto,
 * Ragnar, Jeanne d'Arc, Hercule). Leur recrutement est une quête bonus.
 *
 * Personnages liés à un mod (dernier argument, voir {@link #requiredMods}) : Harold a besoin de
 * Dragon Mounts Remastered (serveur hybride mods + plugins). Sans le mod, il se recrute et
 * s'installe, puis explique qu'il n'a rien à enseigner (dialogue mod_missing) au lieu d'ouvrir son
 * menu. Plusieurs mods peuvent être requis (« wildernature,farmersdelight » : Obélix) : il suffit
 * qu'un seul manque. Un mod précédé de « + » est seulement facultatif (« +irons_spellbooks » :
 * Gandalf) : l'habitant fonctionne sans lui, et gagne du contenu quand il est là.
 *
 * Dialogues : first_meeting (rencontre dans la nature), city_arrival (entrée en ville),
 * building_assign (installation), level_up (montée de rang) ; pour un personnage lié à un
 * mod : mod_missing (menu indisponible) et building_assign_unavailable (installation sans le
 * mod) ; pour un personnage enrichi par un mod facultatif : building_assign_mod (installation
 * avec le mod). {ville} et {joueur} sont remplacés à l'affichage.
 *
 * Skins : value/signature Mojang. Laissés vides, ils peuvent être fournis dans config.yml
 * (npc-skins.<clé>.value / signature), par exemple depuis mineskin.org.
 */
public enum CityNPC {

    /* =========================================================
       LE MAIRE
       ========================================================= */

    MAYOR(
            "citycore_mayor",
            "§6Alderic",
            "Intendant & maire",
            "alderic",
            "ewogICJ0aW1lc3RhbXAiIDogMTc3MTQ4OTcyNDg1OCwKICAicHJvZmlsZUlkIiA6ICI2NDg4Y2VjMjc4OGQ0MTI2OTk5NWMyMmY4OTdmMzA4OSIsCiAgInByb2ZpbGVOYW1lIiA6ICJBc3BlbjA1MyIsCiAgInNpZ25hdHVyZVJlcXVpcmVkIiA6IHRydWUsCiAgInRleHR1cmVzIiA6IHsKICAgICJTS0lOIiA6IHsKICAgICAgInVybCIgOiAiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS9kMTUzNTk3ZmE2M2U2MTJhMGQ2YWY2OWE0ZTFiMDFlY2YxM2M4ZGI0Y2E5ZThkYzdkZmZmODQ5YjBjMTAzZTlmIgogICAgfQogIH0KfQ==",
            "A60BzbuZYlRE2SbwCz02jy+hnBG1o2D8QMl8IcfD994ft2CBTWEhAdhWN0Fey78EXKMTTRFmyGQweLDlV/29lPIRwSLhy77gb9fqkYnR1LaLykUAkiBJ0VBHJW7ZAYAAmOJE5ehoo/fwWADNwlVAu8oZzXGJhhf8goCiGnTBuRRXI6rkyMdMGpjkDqxATuew/0mtxNAGLVIORoHNhbBj1p3ihaM9By4L/A39oN/WthMf+rMQNwhLCuMBYXPI+//ShFhDJl/lDTIm7nvsCk/1vVVDEuULosjWqlYPf2r+r3hDAMIE5StyDk9ypxImHnDe3D2cb5DFNBtZKHLYyIq8enxXxotHcMRjZeaHg4KwajswshsMh07yvXO0x46nfF6RFcMEbjL2u7eRW4Y1bJjKVkxTZ9hmM6C9oHHYKvHRAT1cVo6YxGU8/fukthrZvD0BlQAjsDdwBGW/p2ex/dQtweHWDlamWeqhNIUBIdMF9qlWwNX6f24clecUIhEbXxXdDrupXNZuBrBtUkzicbPrC+PVJaKT0qCO9S2fyHW89VznqAK3whv3CVBGvqpq242IucuTHJpwDet1ctXXWw97ebSfvP//Cg1f9nn5mrE81OB5G5BEsS1Y32KRviQ5tfZPnPsU0SRXatoHzNp0RozrtF2B53SA5eII7ViNFpe/mIY=",
            List.of(
                    "Dernier intendant de Valmont,",
                    "il a sauvé la Charte de la ville",
                    "la nuit où le dragon l'a brûlée."
            ),
            Map.of(
                    "first_meeting", List.of(
                            "§6Alderic §f: §oAh... vous voilà. Je suis Alderic, dernier intendant de Valmont.",
                            "§6Alderic §f: §oIl y a vingt hivers, un dragon venu du Néant a réduit notre cité en cendres.",
                            "§6Alderic §f: §oJ'ai sauvé ceci : la Charte de Valmont. Avec elle, Valmont renaîtra de ses cendres.",
                            "§6Alderic §f: §oParlez-moi pour suivre nos chantiers, agrandir nos terres et gérer la caisse."
                    )
            ),
            Map.of()
    ),

    /* =========================================================
       PERSONNAGES PRINCIPAUX
       ========================================================= */

    // Chapitre I — le bois
    WOODCUTTER(
            "citycore_woodcutter",
            "§2Bastien",
            "Bûcheron & charbonnier",
            "bastien",
            "ewogICJ0aW1lc3RhbXAiIDogMTY1Njg3MTUwNTc3MiwKICAicHJvZmlsZUlkIiA6ICIyMWUzNjdkNzI1Y2Y0ZTNiYjI2OTJjNGEzMDBhNGRlYiIsCiAgInByb2ZpbGVOYW1lIiA6ICJHZXlzZXJNQyIsCiAgInNpZ25hdHVyZVJlcXVpcmVkIiA6IHRydWUsCiAgInRleHR1cmVzIiA6IHsKICAgICJTS0lOIiA6IHsKICAgICAgInVybCIgOiAiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS8yMjljNDczOTA4YWQ3MmExNGMwOGI3YzMzYmM0ZTlkMzIxZTIyNTQzNThmNWI1NDVhYWFjYzVmYThjZThiZTdmIgogICAgfQogIH0KfQ==",
            "tfZp3KHwltIjcjRIWJ7qhGyGBgw05hsYGS6DuOyrtxlD9+QEROZ94FY05jt3b0VLyr1bjGn6VIlRX0WNpTEpeIh2kW1ynrQ56YD2yICrxKTKX0uaQ2QdgqHijElimp6sGAHpRfrtGvXPT9YHG8xAvrbx1Wd0VS1ombwb4UrIB4hJyN0T2GczU7GDb2htgslK1s1ZwMSEd7mXeiJlNEygtwN6Jq/fnkgbQropI8P1wbNWUIFF/S9iYs5MIs3f88Ui9wg2g5jPfLtwxXg7lQ3YRe927C/eqbAJu8tb8aOWBGZZOry+1eUhfe8OhlApnAkVj4SVUGC645lokm7FKkviOa0mZFRj6kylgjG6ha11D0ZnRcPjmeo281I5eItOfB+0Hj1uVM2ph7P8gpUamZb+nVu1ZSUzg/TP48XCwquzPurxbYw5r9zlKwo3ZiiP4EPiRN47AZfGWCIDPn28pLkOdzj3BZZRRZ1V71g3CCHG0d7A849hGtRRsKnJrJCSNy8APax+4ng7q9zon6T3bP3+OeGq/q+SsZQskTwP+p0Bee7+Mk9ZojPiTaC5nA88SFsbIulaa09pHIx+NH8gzdgwzVt6Jp9gTkj036JGz7MQefoiM6jPAkZ4QfmRTObNfN0RBzGFBZxiuKEA2Cb1Yw/MKLKA9k+PLBN95JX5b1IUB30=",
            List.of(
                    "Charbonnier des forêts de Valmont,",
                    "il nourrissait les forges de la ville.",
                    "Depuis la Nuit Noire, il vit en ermite",
                    "parmi les arbres et parle aux chênes."
            ),
            Map.of(
                    "first_meeting", List.of(
                            "§2Bastien §f: §oHé ! Doucement, l'ami... c'est rare de croiser une âme par ici.",
                            "§2Bastien §f: §oBastien, charbonnier. Enfin, je l'étais, quand Valmont avait des forges à nourrir.",
                            "§2Bastien §f: §oValmont renaît, dis-tu ? Alors elle aura besoin de bois... et de charbon pour ses forges. Montre-moi le chemin."
                    ),
                    "city_arrival", List.of(
                            "§2Bastien §f: §o*renifle* Ça sent la sciure fraîche. Bon signe.",
                            "§2Bastien §f: §oTrouvez-moi un coin près des arbres, je m'occupe du reste."
                    ),
                    "building_assign", List.of(
                            "§2Bastien §f: §oUne vraie cabane, avec un toit qui ne fuit pas ! Je n'en demandais pas tant.",
                            "§2Bastien §f: §oApportez-moi vos rondins : je paie comptant, et j'en fais du charbon."
                    ),
                    "level_up", List.of(
                            "§2Bastien §f: §oMes vieux amis charbonniers se souviennent de moi. J'ai de nouvelles essences pour toi."
                    )
            ),
            PersonalQuestCatalog.bastien()
    ),

    // Chapitre II — les cultures
    FARMER(
            "citycore_farmer",
            "§eHélène",
            "Fermière",
            "helene",
            "ewogICJ0aW1lc3RhbXAiIDogMTc2MjgzODQ3NDI0MSwKICAicHJvZmlsZUlkIiA6ICIxOGE1OWU0NWIxODU0YTEyODZmY2VhYTcxZTkwMDM4OCIsCiAgInByb2ZpbGVOYW1lIiA6ICIxMjM0bGFycyIsCiAgInNpZ25hdHVyZVJlcXVpcmVkIiA6IHRydWUsCiAgInRleHR1cmVzIiA6IHsKICAgICJTS0lOIiA6IHsKICAgICAgInVybCIgOiAiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS9kMjM2MDk3ZjNkOWIzYjExMzAwNjI5N2VhM2ZmZjQ2NDhjOGVkMmRmY2E0NjNiNzI3NGZhZDgyMjRkYzcxNjBjIiwKICAgICAgIm1ldGFkYXRhIiA6IHsKICAgICAgICAibW9kZWwiIDogInNsaW0iCiAgICAgIH0KICAgIH0KICB9Cn0=",
            "Xo71JO23FPzquaTbvpDBwj2aHp9ax2rGknalF6mOzLipWmOJyLDMTCvNXVtDEWvkuuMmy+xDqbbZwszkQT8v6t9KgQf61w0hCbfAQ8BNTCiv1h5F3Bu+mat3kGW1qCrg+K1fbw3pIHzsevpJ3WAlodcY3s+c0XNtwBqpNWfjXJJ+w10pakII1to1bHk9wVfk+fM0VkwyVZHu6ETNCh4HTngL46YvXYwQol2Af26duEU0IvjlNIxjGb+826T0U+IFOJOygNWoEdoWrp0WPxp8JKGh4/460N7g9yb4J0ge7kqwAlVtCRKVrzCXCijQSc03judH5HJZyhkusZja7GC4EACSptC4ERgTGhLseg0PTnGvyeTqMBeKZ0++zCoY69St6MU72Kd0+iTk/Pii586yL+I+ivfBu4jl+cssl9vz+a3sATk90jLFmB8yj674Kk/6oOLo6wxbVaB7waQhfAdDlPh4R5hS0hDk/sAg5ZwCuJJvOs1oP5rZx0WcMzqgdTYwV1DXkrWOIT0hs9FlKottAr1tp7znejQc2AGQtRRa4j03tKTVocs83+W1AYuk1P2YF4fq+PUsxfor2GCMnONXFtnAL7kkjuQp+Aa5rN3boigN2fghCP3XkUlky1YHCWkibjoN4sdFKYyUtHnUlcG94RoaxlB71F2jSEQluo72cLU=",
            List.of(
                    "Fermière des champs au sud de Valmont.",
                    "Veuve, elle a survécu aux hivers de",
                    "famine en cachant des semences",
                    "dans son tablier."
            ),
            Map.of(
                    "first_meeting", List.of(
                            "§eHélène §f: §oBonjour, étranger. Attention à mes semis, s'il vous plaît !",
                            "§eHélène §f: §oJe suis Hélène. Depuis la Nuit Noire, je cultive ce lopin seule, saison après saison.",
                            "§eHélène §f: §oBastien vous envoie ? Ce vieux grincheux avait raison : Valmont aura besoin de pain avant les murailles. Je vous suis."
                    ),
                    "city_arrival", List.of(
                            "§eHélène §f: §oBonne terre, ici. Noire et grasse... on va faire de belles moissons.",
                            "§eHélène §f: §oIl me faudra un champ près de l'eau, et des bras pour la récolte."
                    ),
                    "building_assign", List.of(
                            "§eHélène §f: §oUne grange ! Mes grains seront enfin à l'abri des rats.",
                            "§eHélène §f: §oApportez-moi vos récoltes : je les paie au juste prix et je garde des réserves pour l'hiver."
                    ),
                    "level_up", List.of(
                            "§eHélène §f: §oLes greniers se remplissent, les gens mangent à leur faim... Merci. J'ai de nouvelles graines pour vous."
                    )
            ),
            PersonalQuestCatalog.helene()
    ),

    // Chapitre II — la pierre
    STONEMASON(
            "citycore_stonemason",
            "§7Brennan",
            "Tailleur de pierre",
            "brennan",
            "ewogICJ0aW1lc3RhbXAiIDogMTczNTM0NDA5MTg5MCwKICAicHJvZmlsZUlkIiA6ICJiODU0NWMxMDlhZjE0ZGRjYmY4ZjhmZjg4ZTU2NzI4OSIsCiAgInByb2ZpbGVOYW1lIiA6ICJQdGFrb3B5c2tDWiIsCiAgInNpZ25hdHVyZVJlcXVpcmVkIiA6IHRydWUsCiAgInRleHR1cmVzIiA6IHsKICAgICJTS0lOIiA6IHsKICAgICAgInVybCIgOiAiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS84MmRhNGY1NDgxODE5MjcxMGE3OGFjNDVlNjI3Y2ExNmJlZjg4MDQzYzhiMTNhOTU4NjM2ODU1NGMwOGEwZTA2IgogICAgfQogIH0KfQ==",
            "Eu8c+SC9BCYH5qDpU/aALFEqRXeXa3nifZkCZAAL9K9XZ6yM++mZm4jWBNojyRxMWM32mRoFEJRY7rOvOtMwkUmIEm7XCdVhkEK36cPQ1aT0wLksYbeHpFok70Om6fOiFVXo3jxcAk13pd1xfpNjp4d0YfiqUehqW5/6nSXa3/ZqlMgNKuF8ZenXZ2UV8dSq6SY2x/xmGQGKbXWyygI9MLviXF5Hq7nCSQBn6dXGDpTu/HqTW+Mg53T4H8ogi6aGq76WXhquR09bkR/uHsEzyVA40yB3Q6gh9qPegTUCC+1W+xsiqXqzcTw/P/fGzs4nPWetM57a6tu+y4aedqkTI8TFy42YCV1AOIk3nvUT+4w2LAy0iAzlj1bhdQdnRiffHhpl6WD6iKm1LRolK/XpH6a03Dcq+Uay7b6Z4Hg5Vf8Yi3fsGlu5jpyH3nI4ylF9Aj33thOguNG0XQKIYL0xyvgO7IwPLmy0qMfPlEmklNp8JfJbTF52/UCmOGabDa/kDDX+B+aOzmYeel/xjdSwa3GjoziUllMTgxTucoH7QneX8Nqj/uVxxZdjKIITTZMblo6gnQ73HzufIOcc2FgHOr3elzTAXZJydPser4FH7VF8Lb57hfPYTkK/9eNHQi9KQ81b0yjXVgdGzkQIQEDm8UW+h3rh7kEOu09BlpNhVkQ=",
            List.of(
                    "Maître de la guilde des maçons de",
                    "Valmont, il a sculpté l'arche de la",
                    "grande porte. Fou de chagrin, il errait",
                    "dans les collines en dressant des cairns."
            ),
            Map.of(
                    "first_meeting", List.of(
                            "§7Brennan §f: §o*grogne* Encore un nouveau visage...",
                            "§7Brennan §f: §oBrennan, tailleur de pierre. C'est moi qui ai sculpté l'arche de Valmont... avant qu'elle ne s'effondre.",
                            "§7Brennan §f: §oJe tourne en rond dans ces collines depuis vingt hivers. Vous rebâtissez Valmont ? Cette fois, les murs tiendront. Montrez-moi ça."
                    ),
                    "city_arrival", List.of(
                            "§7Brennan §f: §o*tâte un mur* Hmm. Du travail d'amateur. Mais les fondations tiennent.",
                            "§7Brennan §f: §oJe reste. Quelqu'un doit bien vous apprendre à poser une pierre droite."
                    ),
                    "building_assign", List.of(
                            "§7Brennan §f: §oUn atelier avec un vrai établi de tailleur. Enfin.",
                            "§7Brennan §f: §oApportez-moi vos pierres brutes, je vous rendrai de quoi bâtir des remparts."
                    ),
                    "level_up", List.of(
                            "§7Brennan §f: §o*essuie son ciseau* Vous apprenez vite. Je peux vous montrer des tailles plus... nobles."
                    )
            ),
            PersonalQuestCatalog.brennan()
    ),

    // Chapitre III — l'eau
    FISHERMAN(
            "citycore_fisherman",
            "§bMartin",
            "Pêcheur",
            "martin",
            "ewogICJ0aW1lc3RhbXAiIDogMTc0NTI1NjQzNTQ1MiwKICAicHJvZmlsZUlkIiA6ICJiMTM1MDRmMjMxOGI0OWNjYWFkZDcyYWVhYmMyNTQ1MCIsCiAgInByb2ZpbGVOYW1lIiA6ICJUeXBrZW4iLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZTYxNjczODdmYzM3NGIwYWI2ZDNlYTkwYTUzNjVhMzE5MTljYjZlODBhOTU0ZTc1ZjhiN2ZmMWExYzhkNzJmNCIKICAgIH0KICB9Cn0=",
            "EU18y104IESnKJzoi+T1KXsowxby2PCvFIhjOigwiiUWQYJFA46D+GW7WSOoLhLgLDRPygYOtWorWDF420M58mi1HHrmMdUn3pobukAKj20IBRIEwOplRVIWl7qy88rz9X9BN8fYMqTqR3WOVh1aH/Y1CuEJDIoy0YwyW2J80OPA2160ipb/VM8UQ5YaVeLf+mvQrOB614SAgxF3nEBjUU7GBuSiHHovgBKtHaxmMiKzfdBUs8RGdwGAmyIxVRBgKL2q5jdMVM+xepHtLzY2GwXnp9+UfgEfT1JlF4tb4+t4u0iwFKD93e0d0Ww4zINfF6blsD+Tli33O389TGqhr4PfnL7PWxU6Zr4isCszvRsjA7eLGDgUrh17O1grlRcm5pwMeqxeHm/idD3cOB9l5praHbKj+Je/x+NWnq/5krk4mjfWR/VAPzmsZv3hXLKUVICtqxw0SW3TeU4xIuYpvnEwOLyy0wMMNzD5TEcAZYJUUnVweAModF4xFPAcTt6iG1apIEtd3frUCTFVoMRrrmRaN7IhptBkKVAiM+pv9drx83VmEglLse2QRD+7GG7Vy8sVCX6Wn3dQcKrwCf0NqlqjqG9nZXbTgAC3cbSDNEekyOyHimcANbD6iiGNNNdBIfAb3jIcAdnf4rNEGj88dkLj3FKAT4yCn6aoUuwlv8s=",
            List.of(
                    "Passeur sur la rivière de Valmont.",
                    "La nuit du drame, sa barque a sauvé",
                    "une vingtaine d'enfants.",
                    "Il n'en parle jamais."
            ),
            Map.of(
                    "first_meeting", List.of(
                            "§bMartin §f: §oChut... vous allez effrayer le poisson.",
                            "§bMartin §f: §oMartin. J'étais passeur sur la rivière de Valmont, autrefois.",
                            "§bMartin §f: §oValmont renaît au bord de l'eau ? Il faudra bien quelqu'un pour la nourrir de poisson. Allons-y."
                    ),
                    "city_arrival", List.of(
                            "§bMartin §f: §oAh, l'air sent la vase et le sel. Je me sens chez moi.",
                            "§bMartin §f: §oMontrez-moi la berge, j'y monterai un ponton."
                    ),
                    "building_assign", List.of(
                            "§bMartin §f: §oUn ponton et un séchoir à poissons ! Vous me gâtez.",
                            "§bMartin §f: §oRamenez-moi vos prises : je les achète, et je vous vends de quoi mieux pêcher."
                    ),
                    "level_up", List.of(
                            "§bMartin §f: §oLes marées sont bonnes cette saison. Je vous ai mis de côté quelques trésors de l'eau."
                    )
            ),
            PersonalQuestCatalog.martin()
    ),

    // Chapitre III — les mines
    MINER(
            "citycore_miner",
            "§8Godefroy",
            "Mineur",
            "godefroy",
            "ewogICJ0aW1lc3RhbXAiIDogMTU5ODU1MjYxMDUzNCwKICAicHJvZmlsZUlkIiA6ICJiZWNkZGIyOGEyYzg0OWI0YTliMDkyMmE1ODA1MTQyMCIsCiAgInByb2ZpbGVOYW1lIiA6ICJTdFR2IiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlLzU3ZmJhNzIzN2YyNmZlNDdjOWI5YWFjOWJjZGNmZjFjY2UwNGU5YWYyYzRlNzhkOTI5NjFkMDAzZWIzOGQ3MWEiCiAgICB9CiAgfQp9",
            "kSWroKCNUe7SisvByvhIj1JTIaf5g3ZW2ww/y+GPPWocX+8GBAlaOqlH5OOq5k2synSoNYXMsn5iNnZ74TsWzH00GdbjeNgiCaEm4t9GmzA9KKD7VyDc6Nq2Y8YgzgI2bNXHL7wR+6OhkyVL6wZoImrFlMh3YPE7Iafv6LxxWJT/+83U3yYmn9TOWOtVtxixB5WNf3E/rIzBmKX3F5r+dp4tCzksrkxjpEiqOLIfuHxnWSeOiYYa+km2WGe8gLyKonXWHR/WOu0LdU/rCAFL+/RtkzPj8jdVNRG3oxHc7n3EGR+0+5QSlnDK2oRVarnGBlkQhf7p4y3o2FTEBVe0BlqoEylnB6HzcHg5Z552qtpFxbUUb3jSC3gN1OsvBzRd/olg18vzkHIrg4ZzVp/7iG7raHS33sGzj2kTOqThUDdJuycPYMwLAIdzUHq8aKON4U4yZtBUubwi/b6w6B/LZ594DyHj+iofwlvplzky+PhE8lK+PMV/CNxWgCQOBcBejzTJqcRQM6108UFdljH2opweAlcVkCwTnTxszrgbDs1/0Y7Wy6Yfwm4dwma4NfFBsyErdWMxwAH4KqjqTphkExtLU0lv4t3rbKnY9tw7+pS0SXly1AuIQoT4Nssp8hpK+DS4kATm8c0P6Z2yawldDyH985b4o+cknn2JYC+Lcks=",
            List.of(
                    "Maître mineur de la Fosse aux Corbeaux.",
                    "Il était sous terre quand Valmont a",
                    "brûlé ; remonté trois jours plus tard,",
                    "il n'a trouvé que des cendres."
            ),
            Map.of(
                    "first_meeting", List.of(
                            "§8Godefroy §f: §o*tousse* Qui va là ? Baisse ta torche, tu m'aveugles !",
                            "§8Godefroy §f: §oGodefroy, maître de la Fosse aux Corbeaux. On était sous terre quand Valmont a brûlé... on n'a rien vu, rien entendu.",
                            "§8Godefroy §f: §oValmont se relève ? Elle aura besoin de fer et de charbon. Je connais les veines. Guide-moi vers la lumière."
                    ),
                    "city_arrival", List.of(
                            "§8Godefroy §f: §oTrop de ciel, ici... Mais il y a de la bonne roche sous nos pieds, ça me rassure.",
                            "§8Godefroy §f: §oIl me faudra une galerie et un treuil. Et du ragoût. Beaucoup de ragoût."
                    ),
                    "building_assign", List.of(
                            "§8Godefroy §f: §oUne mine à moi, avec des étais solides !",
                            "§8Godefroy §f: §oRapportez-moi vos minerais bruts : charbon, fer, cuivre, or, gemmes... je paie bien."
                    ),
                    "level_up", List.of(
                            "§8Godefroy §f: §oLa Fosse grandit ! J'ai de quoi creuser plus profond. Méfie-toi juste du grondement d'en bas..."
                    )
            ),
            PersonalQuestCatalog.godefroy()
    ),

    // Chapitre III — la taverne
    INNKEEPER(
            "citycore_innkeeper",
            "§cRosalie",
            "Tavernière",
            "rosalie",
            "ewogICJ0aW1lc3RhbXAiIDogMTY5NjA3MDUwMTQyNiwKICAicHJvZmlsZUlkIiA6ICJkN2Y4OTAyMWMxNmQ0ZjFhODg3ODQyNDQyZjc3NGI0ZiIsCiAgInByb2ZpbGVOYW1lIiA6ICJQYW5kYXRvSG9wZSIsCiAgInNpZ25hdHVyZVJlcXVpcmVkIiA6IHRydWUsCiAgInRleHR1cmVzIiA6IHsKICAgICJTS0lOIiA6IHsKICAgICAgInVybCIgOiAiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS83NDVhZmYxYmE1NTgyMDcxOTFiOTZhNjJhOWJiYmU2MzRlNmViMjdiZmUxNjE2ZWZhNTk1Y2UwNTYwODFiMjJmIgogICAgfQogIH0KfQ==",
            "wy+G64sySfJBqdB7aMU+L7yqVx3KFdDzafJt6DW6ozBzk3+aRbZwGfciboERTMo1QC/so9HumluWBZPdXy8BTiSZmbX/Scypt5gebObASVXsUXGEm9diywOLs802aM9Da9DVH0AEXdyzDSBII4W/IvFNaRPZS7LJNaffVhgU4Liymgb0GyRhFIJvp/Vdo3x2btvgRet/y4ljKDF2immZxUZmjYq1yt8eCAVMKnCA1ST27jdPVehz1PMX1j6pgqyLNYLO8+ZP2RD1c0GU3wru4d18uGsbpEeLFNK2LG9nRld1pys8vdo6ne3rmosGXN3XI2z8ZL72EgHpwFWePVujpIZgEMHzSD1lHnZmdjVGyO17wDteDFq1pF8kPVHKirtTApcsUdxy6LMRC6lED+tuXHL22l690pxTnyTRNX9lzv7jMQJm54dHJassRO+tY1p0Sud4Ah+77ZqASSBvsYVdRoJm+4p3j7C04dZvaxtTwXbeZrAdvy+TQ0Ei3+4RlfqZSy4VpBwxxS00xNeNa/FlTXH2cziiO9AwiGHYfIczMNHWC4ErJt6kCp9RDTe/CF2+e67yRafPAJWTlDQNf8KZ7kC6mzNk0EYkQsns8VqrnrbCQ8FONAm3Z8rQf7+KwEVpvtYGh3A1/iLd44pCcVZH6DvkPplqib7H9h1ADrFEx5c=",
            List.of(
                    "Elle tenait le Chaudron Doré, la taverne",
                    "où buvaient toutes les guildes de",
                    "Valmont. Il lui reste sa louche,",
                    "ses recettes... et toutes les rumeurs."
            ),
            Map.of(
                    "first_meeting", List.of(
                            "§cRosalie §f: §oEntrez, entrez ! Une soupe chaude pour la route ? C'est offert... pour cette fois.",
                            "§cRosalie §f: §oRosalie ! J'avais la plus belle taverne de Valmont, le Chaudron Doré. Il ne me reste que ma louche.",
                            "§cRosalie §f: §oUne ville sans taverne, c'est un corps sans cœur ! Emmenez-moi, je vous ouvre une auberge."
                    ),
                    "city_arrival", List.of(
                            "§cRosalie §f: §oOh, ça manque de rires, ici ! Ça va changer.",
                            "§cRosalie §f: §oUn bon emplacement près de la grand-place, et je m'occupe du reste."
                    ),
                    "building_assign", List.of(
                            "§cRosalie §f: §oMa nouvelle taverne ! Je l'appellerai le Chaudron d'Argent.",
                            "§cRosalie §f: §oApportez-moi viandes, œufs et champignons : je vous mitonne de quoi tenir tout l'hiver."
                    ),
                    "level_up", List.of(
                            "§cRosalie §f: §oLa salle ne désemplit pas ! J'ai ressorti des recettes du Chaudron Doré, rien que pour vous."
                    )
            ),
            PersonalQuestCatalog.rosalie()
    ),

    // Chapitre IV — la forge
    BLACKSMITH(
            "citycore_blacksmith",
            "§4Thorvald",
            "Forgeron",
            "thorvald",
            "ewogICJ0aW1lc3RhbXAiIDogMTYxMzg0NzUyNzEyOCwKICAicHJvZmlsZUlkIiA6ICJhOGExZGY5MGE3YjU0NTdjYTEwYjM5ZGQ0N2NmMjM5MCIsCiAgInByb2ZpbGVOYW1lIiA6ICJrZW5ueWtjb21haW4iLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMzYwNDY3ZDZhZDE0MzU2NWVlMzU1ZWExNGZjZGQyMjMzNmRlZTk2NTJiOWIxZGI2OGU0ZTQ4NjA0OWIxYmRlNCIKICAgIH0KICB9Cn0=",
            "ZZXX8xVkfgdxSNshgo5Zn9Qrq8gD5NPUbQf5cNPfnPdCHYX3qu9+8Sn7RP3AeleYhINgswZcQanb8F+yYkRGE9hO7xJX8ugYFtk8GGGUkyhMeWE2UjgWFfJZyCE/qPhmOB6eVX2GiciumCl6koxBOR5msH7bJEaYAojv0PQQzG6/NHXljqZCYhSPERfHzlnR8Xuh3M9ruAA98qRNTIu1x7O53zmjSbS65dodevs75jTEc6RjuWPWw8Kc6yPVXyLsKoA8bPvTjZN3okfaXofn2zMrzTUdGEJYptgjnJbBJl+Ndf19BWS876QaZAXkBhQeYCxWhdHSK1G3rxDGMsmzLURIBiXv5f4Gs5lVhbAZVMUPgHOv+84Yd0N5QFu+EU2+bM6//nXgpoXucGRrKk/kiR4TjaGMJoFf8u1u/iRey38AO3NJ0WTGR0z/pBzqVyce2h3vtq0cED/mgxdlzRyytsyDgQaRmYMlYGgA/DBboV50TGp5IzSIWaWDaRj7oEudIVJClCCzdhhrsQ+YE+4DmegRgdguILQ5PQ5yLGFXZxna5G7Jybwy44l7RfpqeDSJ+GfCJ4VnonSK/7fkPwR2Su3KsXuDQmKl9mT73MNuyutxDoZyQ+UliR90rZ83XDunHg7SV0DPYdGz3g4DiGulVovrihnxrZ+O3lHQm+lbVwQ=",
            List.of(
                    "Forgeron venu des fjords du nord,",
                    "il a forgé les portes de Valmont.",
                    "Elles ont tenu une heure face au dragon :",
                    "assez pour sauver des centaines de vies."
            ),
            Map.of(
                    "first_meeting", List.of(
                            "§4Thorvald §f: §oHalte. Tu marches sur ma neige.",
                            "§4Thorvald §f: §oThorvald, forgeron. Les portes de Valmont, c'est moi. Elles ont tenu une heure. Pas assez.",
                            "§4Thorvald §f: §oValmont se relève ? Alors elle aura des portes. Des vraies, cette fois. Je viens. Ne parle pas trop en route."
                    ),
                    "city_arrival", List.of(
                            "§4Thorvald §f: §oHm. Il fait trop chaud ici. Ça ira.",
                            "§4Thorvald §f: §oUne forge. Une enclume. Du charbon. Le reste, c'est mon affaire."
                    ),
                    "building_assign", List.of(
                            "§4Thorvald §f: §o*frappe l'enclume* Elle chante juste. Bonne forge.",
                            "§4Thorvald §f: §oApporte des lingots. Je forge outils et armures. Et je paie le fer au prix du fer."
                    ),
                    "level_up", List.of(
                            "§4Thorvald §f: §oTu es digne de meilleures lames. Regarde."
                    )
            ),
            PersonalQuestCatalog.thorvald()
    ),

    // Chapitre IV — le textile
    WEAVER(
            "citycore_weaver",
            "§dAgathe",
            "Tisserande & teinturière",
            "agathe",
            "ewogICJ0aW1lc3RhbXAiIDogMTcxNTAyMjgyOTUzNywKICAicHJvZmlsZUlkIiA6ICJkNWMyZjNmZWY2NjM0ZDk4OGFmMmJjMDkwNGEzMzAzNSIsCiAgInByb2ZpbGVOYW1lIiA6ICJCTU1HQSIsCiAgInNpZ25hdHVyZVJlcXVpcmVkIiA6IHRydWUsCiAgInRleHR1cmVzIiA6IHsKICAgICJTS0lOIiA6IHsKICAgICAgInVybCIgOiAiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS9mY2UwZjM5YTY4MTJkZmRmMjJlNTY5OGUwZDhhZjRkMDEwZTc2NWQ0ZTEzMDgwMWIxZmMyODRmYzYzOGZlN2NmIgogICAgfQogIH0KfQ==",
            "lXBNeeGxVWwjBkuPiokGYrfQfyTWEyJREspuiq6EHRP5I0UxVHWJm0+mzb8waIVgEBHlZJhfTxT8y/or5u06ybBO032rGS/vU1rFXyn8S49u5ec/IAtzTABMeESqn/mAs/fZxzu7JV5ogAkojmwqLO2c0GUz5kyETmjLsVnMMCYeTLkX78mpsUSG+DaGA7bDBtRzxyIR/0uLHPOTqdzfsqThb1o7nl3HLOxK48ZUFMbiSSShO6dTt0epwXAq/2K0s52uXp54ymlndz7A+b8VUuw0P1WSaKI7rt2U1cx6CxK9tsEyhEpzMTdrtpdYAZFm8aoMuHXbG/PLciE7prZMDr8TpujYe8nXfd9R9yGI/EqoAWu1cE6iI0Faoq3EhHjltSp833/RTmI7UpKlkFPxQ+1c5mu+rdQDzmciMTLkHPkRe32J7Ogx6xbXbyappbF28JOw6ClL2iG4dYRfAp7wHsQ3aGqKJRVqfDEngoVhK3DveHVotl6UhYTj5QGyR/VdcozbM2O5rev8LF1qbCMeyZLRTeaEKKGP/JXwmzwrR1fixTRCUg291voGBRaTBr598VkCMy0IiRErDqTwL4iA8UaR7sVCwcnTmp8bZy0hJG7WzydRGxmQhyEpclEUVpy54q5l3BVqzw8MzSqvAXJmdMo48zByvSLAIeaUTSMFkoE=",
            List.of(
                    "Apprentie de la rue des Teinturiers,",
                    "où la rivière coulait de mille couleurs.",
                    "Elle avait douze ans à la chute de",
                    "Valmont ; des bergers l'ont élevée."
            ),
            Map.of(
                    "first_meeting", List.of(
                            "§dAgathe §f: §oOh ! Ne bougez pas... la lumière sur votre cape est parfaite pour un bleu de pastel !",
                            "§dAgathe §f: §oJe suis Agathe. Petite, je vivais rue des Teinturiers, à Valmont. La rivière y coulait de toutes les couleurs...",
                            "§dAgathe §f: §oVous rebâtissez Valmont ? Alors son étendard flottera à nouveau ! Je viens avec vous !"
                    ),
                    "city_arrival", List.of(
                            "§dAgathe §f: §oQue c'est gris ! Il va falloir mettre de la couleur partout.",
                            "§dAgathe §f: §oUn atelier lumineux, près d'un champ de fleurs si possible !"
                    ),
                    "building_assign", List.of(
                            "§dAgathe §f: §oMon métier à tisser est installé ! Il ne manque plus que la laine.",
                            "§dAgathe §f: §oApportez-moi laine, ficelle et fleurs : je vous fais draps, tapis et bannières dignes de Valmont."
                    ),
                    "level_up", List.of(
                            "§dAgathe §f: §oJ'ai retrouvé les secrets de la rue des Teinturiers ! Venez voir mes nouvelles couleurs !"
                    )
            ),
            PersonalQuestCatalog.agathe()
    ),

    // Chapitre IV — l'élevage
    STABLEMASTER(
            "citycore_stablemaster",
            "§6Edmond",
            "Éleveur & palefrenier",
            "edmond",
            "ewogICJ0aW1lc3RhbXAiIDogMTY4Nzc3NTQzNzA3NCwKICAicHJvZmlsZUlkIiA6ICI5MGQ1NDY0OGEzNWE0YmExYTI2Yjg1YTg4NTU4OGJlOSIsCiAgInByb2ZpbGVOYW1lIiA6ICJFdW4wbWlhIiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlLzRhMDA1NjVkY2I4MWE3MDVlMzhlYzk5MmI0OGQ0OTFlMzI2OGIxOTY0N2M4YjJkMzI0ZDlhN2U0NTdlZTZmNzAiLAogICAgICAibWV0YWRhdGEiIDogewogICAgICAgICJtb2RlbCIgOiAic2xpbSIKICAgICAgfQogICAgfQogIH0KfQ==",
            "uttPflkQ6YRx7cElvclGPz4YCWzP7SWrJ1Bxcze0DV+igrpZTEaOn0NUA6kwGBdC/4zoFFoHP7sL+sJO/6hNDRQq5m2oCRH68Twjhv+iz9Ip7nz1nlGQQ+4oL4fiht7Eyz2JhEwJLrB7upXcBEfhFU7/8aEyn0kuVeEAMFFfDLsqns1a3XDBn6OilOjB/d7oIiox5STyqyVZVbPJAWGu/NDEvuQprApU0FCHz7wI6VOAp5Xxqu01LEoSMHNPCT8d0eZK0h3vWAhRjnqJGZBhlDnqMg8jx42Gr/dkCoMFfHIeFeTjEjXg07nj6jHITeLFumwMARUely/5WA2AQ9I3RNj+LkVc5g1RNQEZnIxdoavsLdROGcMVoG1Vpw0ojbIpwEsEnnsZJKnqjJeAH7D9YmpfAU2d/FW4Q2+SB61Vcr/gjRi8kvRxVR2CRPJB6ObyVj+bYfzCKrRuw20rsUtNM7neFxwD70l1rXEa+VK4C1JC5cpcQWATZmUIC5Pe8RnNqbQJav8iEjaFYzruFM3JhXxBQDdra+vzXP0xITwvlAS/22qqCwydDDZMhyCFxN72x+Up1RMLXhcwiteiQPCv8rCkFBI/u31D2qaOLnfys2wfMhWdGBogY0n/0ls6cmnAlI1Ig8MhVfewtOGPKmgiqgKyrAvAdMLPYoTpOuznstY=",
            List.of(
                    "Maître des écuries royales de Valmont.",
                    "Cette nuit-là, il a ouvert les stalles",
                    "pour sauver les chevaux. Depuis, il suit",
                    "les hardes sauvages en cherchant Éclair."
            ),
            Map.of(
                    "first_meeting", List.of(
                            "§6Edmond §f: §oDoucement... vous voyez ce cheval pie ? Il est sauvage, ne le brusquez pas.",
                            "§6Edmond §f: §oEdmond, ancien maître des écuries royales. J'ai ouvert les stalles cette nuit-là... les chevaux ont fui, et moi avec eux.",
                            "§6Edmond §f: §oUne ville avec des prés et des écuries ? Je viens. Peut-être qu'Éclair finira par me retrouver."
                    ),
                    "city_arrival", List.of(
                            "§6Edmond §f: §oIl faudra des enclos solides et de l'eau fraîche.",
                            "§6Edmond §f: §oLes bêtes sentent si un endroit est sûr. Elles semblent calmes, ici."
                    ),
                    "building_assign", List.of(
                            "§6Edmond §f: §oDes écuries ! Et une tannerie derrière, parfait.",
                            "§6Edmond §f: §oApportez-moi cuirs, peaux et écailles : je vous fournis selles, harnais et bardes."
                    ),
                    "level_up", List.of(
                            "§6Edmond §f: §oVos bêtes sont bien soignées. Je peux vous confier du matériel digne d'un chevalier."
                    )
            ),
            PersonalQuestCatalog.edmond()
    ),

    // Chapitre V — la garde
    GUARD(
            "citycore_guard",
            "§9Roland",
            "Capitaine de la garde",
            "roland",
            "ewogICJ0aW1lc3RhbXAiIDogMTY2OTMxNjI1MDgwOCwKICAicHJvZmlsZUlkIiA6ICI1NjY3NWIyMjMyZjA0ZWUwODkxNzllOWM5MjA2Y2ZlOCIsCiAgInByb2ZpbGVOYW1lIiA6ICJUaGVJbmRyYSIsCiAgInNpZ25hdHVyZVJlcXVpcmVkIiA6IHRydWUsCiAgInRleHR1cmVzIiA6IHsKICAgICJTS0lOIiA6IHsKICAgICAgInVybCIgOiAiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS9mYTk5OWUzNzZkMzg0NmQzOWQ5NDkwMDIxNGYxYjcwOWUzZmIzNTA3Zjc3ODZlNjgxNWEyMzU4Yzg0OWNkMWM1IgogICAgfQogIH0KfQ==",
            "mA6hgeJ6MRkEiNBunN6p3CrwRUGGeNSzEgKaBWskPqC7Trvr6Kpc/E0n5dIfagHgEgn+fivKWySszPYvG3UsfeT2+V1sO315fA+SDFwdqTPbsCU5h7W+fmczIFs7JrxruA57saJy8wJbxmsPcjBCrWfLy//7Qv5uIdbUuSshEPSEHBjJzzzf8htpoU3xMsPb2AlwgJY3OiZVsTzOW9pt0GIIxWAVeDOtgj2h7+dIo05Br6iKEA7BKOhij66hQqU3ebVNebHgNdicNZ1XWrz9ZQzE5rbMrYVsY2indRFW7QtiaqrYsSdTmZEjqbSEjSXExSl6S0bL/dHyzqLn3xYBlSvr1UJrfrQlwq0qJzz7y3yJZyQzle4ezKbal9LXC9p4T4jHmpWzOnLUe2PF8i/ZMndqVuxnwjC262EUFd3jwyrjNl1QpLlaXx1FztOf58DeB7Lm8oTykpmPg6R+Dc1RZDe8V3YY6Rj+GOmZ2TulKDWnv7+kaBqakK0UVwlEuH8+Ng4b5Y6qxLrlvX+z9lyUgSGn6pYEzV1acHdPkS1wVLCXcC4leu54TnFexC5KN6WvImLZOV1OZsG2jHCokYfssgRfhVXlyBbOAjbeB9lhlWU9KOH+PIXFd0Swm/4EtfxV7GDy3BeCa6oatCp9FtId4wHNuuIChPmnc3IJWCO8dHQ=",
            List.of(
                    "Sergent de la garde de Valmont.",
                    "Il a mené l'évacuation et perdu toute",
                    "sa compagnie. Depuis, il escorte les",
                    "convois contre les pillards."
            ),
            Map.of(
                    "first_meeting", List.of(
                            "§9Roland §f: §oMain sur la garde, voyageur. Tu viens en paix ?",
                            "§9Roland §f: §oRoland, sergent de la garde de Valmont. J'ai perdu toute ma compagnie cette nuit-là.",
                            "§9Roland §f: §oJe ne laisserai pas Valmont brûler une seconde fois. S'il lui faut un capitaine, en avant."
                    ),
                    "city_arrival", List.of(
                            "§9Roland §f: §oPas de tours de guet, des murs trop bas... il y a du travail.",
                            "§9Roland §f: §oJe recrute des archers. Et il me faut des flèches. Beaucoup de flèches."
                    ),
                    "building_assign", List.of(
                            "§9Roland §f: §oUne caserne et une armurerie. La ville peut dormir tranquille.",
                            "§9Roland §f: §oRapportez les preuves de vos chasses : je paie les primes. Et j'équipe ceux qui défendent la ville."
                    ),
                    "level_up", List.of(
                            "§9Roland §f: §oLa milice prend forme grâce à toi. L'armurerie peut maintenant t'offrir mieux."
                    )
            ),
            PersonalQuestCatalog.roland()
    ),

    // Chapitre V — la foi et les remèdes
    MONK(
            "citycore_monk",
            "§5Frère Anselme",
            "Moine apothicaire",
            "anselme",
            "ewogICJ0aW1lc3RhbXAiIDogMTc2NDcxNjc3Nzc2MCwKICAicHJvZmlsZUlkIiA6ICJiMzMwZWE4OWE3MTY0OWZjYTMwZmI5ZjU5MTQ5ZjNjMCIsCiAgInByb2ZpbGVOYW1lIiA6ICJUQUxLX0FMT1RfIiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlLzMwMTY1ZjRkZjRkZmFhZDhiMWQ0ZmZiNmUyNmFiNTg3ZWRiYmRmZmZlNGMxOWMxNTkwYmJkZTEwNzdjMDNiMjgiCiAgICB9CiAgfQp9",
            "b+6iYdaT02Z3gs4eTUW3cOzHqtkHDllYAIg0lX4KH8l6iToU37SJpxwfkUtSotr45npOZmv+2ShRmzcHP3SD2e+y0xNWT2Lp0e0lN2BGSchljvj59srxJdSQXhBLerNLGypBlHa0x+aXyUI50GrzSOW0B4Heqy8rBzfWWaOerAQFAmjcCnnJOcPtmTqG+88Np/+Cr4er61xXwQgBj2DlKgJkvRyBf0+zemYO3QOeoi7apJVhj1DVW1bSgz+nDm1pAS/bzDQBiMDmDDClraveyHGuc3wEY8EIIQslNvMl4YqqV/fapHJfzMhOkvnFyQSlGy7rfkQGMuop66wiwHlzL3YHmsDPT1/jr1A6nxmY1iXcJOkYg/VxLs29RRl0ZHXuSRdiZZ6mkTu8r6h+VYlaSVdtxhxZOpv9xtlGHl2B53K3OSyi+wHOyJBgJaEjS7dzvPX+8WfD7oOurAIA6FYSnjuSu+3rk0uhZP3fDSidt4LxDtLFxNiUwBUwAwU4RtuysndfL8NcI19QSrbpVHU6kN+SNcLT0P1zC1pY1Nu3U9nio586fR6g/Z4lPfPKMwClZg9cMZ8flSwG+r+6pdgvsuWVUpoTdOUxiY8XXNwnsEbbSeI1YLgb/M1pce1hhFVxq8IdtpN4MSnlN5s2sQXUU621OJelyBXxTriupK/5vgs=",
            List.of(
                    "Moine de l'abbaye de Saint-Clair,",
                    "gardien du jardin d'herbes et des ruches.",
                    "Il a soigné les survivants dans les",
                    "ruines, puis s'est retiré en ermite."
            ),
            Map.of(
                    "first_meeting", List.of(
                            "§5Frère Anselme §f: §oPaix sur vous, mon enfant. Attention, mes abeilles n'aiment pas les gestes brusques.",
                            "§5Frère Anselme §f: §oJe suis le dernier moine de l'abbaye de Saint-Clair. J'ai soigné les survivants de Valmont, puis je me suis retiré ici.",
                            "§5Frère Anselme §f: §oVotre ville a des malades, des blessés ? Alors ma place est parmi vous."
                    ),
                    "city_arrival", List.of(
                            "§5Frère Anselme §f: §oUn jardin d'herbes ici, des ruches là... et une chapelle, un jour.",
                            "§5Frère Anselme §f: §oLes gens d'ici ont l'air fatigués. Nous allons y remédier."
                    ),
                    "building_assign", List.of(
                            "§5Frère Anselme §f: §oUne infirmerie et un rucher ! Le Ciel est généreux.",
                            "§5Frère Anselme §f: §oApportez-moi miel, cire et herbes étranges : j'en ferai baumes et potions."
                    ),
                    "level_up", List.of(
                            "§5Frère Anselme §f: §oMes alambics n'ont jamais été aussi bien garnis. Voici des remèdes plus puissants."
                    )
            ),
            PersonalQuestCatalog.anselme()
    ),

    // Chapitre V — le savoir
    SCRIBE(
            "citycore_scribe",
            "§3Isabeau",
            "Scribe & cartographe",
            "isabeau",
            "ewogICJ0aW1lc3RhbXAiIDogMTY2MDUzMzMxMzEzMiwKICAicHJvZmlsZUlkIiA6ICJkZjY2OWIyOGFmNWE0MTNjODFhNjcwOGQ0ZDIyM2FlNSIsCiAgInByb2ZpbGVOYW1lIiA6ICJlbW9fbGFuZyIsCiAgInNpZ25hdHVyZVJlcXVpcmVkIiA6IHRydWUsCiAgInRleHR1cmVzIiA6IHsKICAgICJTS0lOIiA6IHsKICAgICAgInVybCIgOiAiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS8yYjdhNzE1YjRmNGQwOGNjNDllYmNjZjUwMTk4NmZmNDAyZWJkMjNhZmI5NzA2YzMzOTlkOWJkZGJlZjg0YzI4IgogICAgfQogIH0KfQ==",
            "h3H4j2Qwf8S5CIAEC04HJR7D+NeIzErL5hqdSVeS7CcbnkgPZQBG8AQMUpou14qloSAmZ130mkUrWAV4uGe8I7eNqc6pPaWTmSDVaXxLTebRdrAJcJNGhy0xmG0wHqIOexx9GyRIhUWtbLlKm/cgjHihPxccR5H40nRSxo6EPyhkFFSUR9h2kyKuJyUw/x4XRmSKn9yR0BP2VG6r7XF6v6hsAh53RHRNhaBjJL1GtH4tpD1w3yyjGsCUdlEr3VLRJs5AE/r/PMASpokvC6PEyjAQZrVGGKb6yZOi7CNBGTjD+gvtVOS5+p/GOir9O1AfxZ++uK9/izVpbZepGCka2IUGyH0FXsY+2xZJerBDfIvXQ32Y+fOZWWsgpQQ/0M0/EiesplmrPosMLHTnkh9DKMwOmJ0hRewrMaTotpBMYNNum+IfP4T2fDmK3DQHMYI2nhx6UL8j1j4vRFyjCwEBtJ8/bVXc51lLXlJfPYpeNxvQT3ctK/QLzApvnNYtFvJoW0+ENjVNvXaxbQ+mA/wwy5wtnBaqxTx1k39si4jnrFFM1aOA0TY6vNwlPGjJWTs290QGaI25jixUAjNVmxD31K96kOzwVu+3ImCAQv65kI6Rik/ywpdKaqMuGEluoxUUupEpWYcGnOHZZl6tSdVJAuhY/A2kJ30dlckwGgHtzaY=",
            List.of(
                    "Archiviste de la Grande Bibliothèque",
                    "de Valmont. Elle a sauvé un coffre de",
                    "registres et cherche depuis vingt ans",
                    "ce qui a causé la Nuit Noire."
            ),
            Map.of(
                    "first_meeting", List.of(
                            "§3Isabeau §f: §oPardon, je ne vous avais pas vu... j'étais plongée dans ce manuscrit.",
                            "§3Isabeau §f: §oIsabeau, archiviste de la Grande Bibliothèque de Valmont. J'ai sauvé un coffre de registres. Personne d'autre ne s'en souciait.",
                            "§3Isabeau §f: §oVous bâtissez une cité ? Alors elle doit avoir une mémoire. Je viens, et mes archives aussi."
                    ),
                    "city_arrival", List.of(
                            "§3Isabeau §f: §oIl faudra des registres pour tout : naissances, terres, impôts...",
                            "§3Isabeau §f: §oEt une bibliothèque. Les villes sans livres oublient pourquoi elles sont tombées."
                    ),
                    "building_assign", List.of(
                            "§3Isabeau §f: §oUn scriptorium, avec de vraies fenêtres pour lire !",
                            "§3Isabeau §f: §oApportez-moi papier et livres : je dresse vos cartes et je recopie le savoir de Valmont."
                    ),
                    "level_up", List.of(
                            "§3Isabeau §f: §oJ'ai déchiffré un nouveau feuillet des registres... Venez, il y a des choses que vous devez savoir."
                    )
            ),
            PersonalQuestCatalog.isabeau()
    ),

    // Chapitre VI — le verre
    GLASSMAKER(
            "citycore_glassmaker",
            "§aLéon",
            "Verrier & tuilier",
            "leon",
            "ewogICJ0aW1lc3RhbXAiIDogMTczNjE0NTI4NDUzOSwKICAicHJvZmlsZUlkIiA6ICI5ZDYzZjMxMGNiZWE0MWI1YmQ4YTM4ZmExYTBiMDI4MCIsCiAgInByb2ZpbGVOYW1lIiA6ICJTa3libHVlZm94dGFpbCIsCiAgInNpZ25hdHVyZVJlcXVpcmVkIiA6IHRydWUsCiAgInRleHR1cmVzIiA6IHsKICAgICJTS0lOIiA6IHsKICAgICAgInVybCIgOiAiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS8zMTU3MDM2NzJlNWI4NGNhYjFjM2M2YTU1ZDlkNTYzOTg1ODc0ZmJiNGUzMGYyM2UwNzU2YzFhOGYzNzEzZWZhIgogICAgfQogIH0KfQ==",
            "lumiauYGIkAS1BEbNEK5I/fsWuykf1KjR+AGb3I4Xz61QIB75FO/ZZ9K26Y3KRkA0hRLuiaT9NmFFUFBOk4eDWs2CfLUyxNdWUVk3zTm4+zZpXUCAcdLILjv7kYtc2JB5OoipJULZNkimuMh0QBuUq9HRc1fwLxqZGKVYx6tR8z6Ymg6JesaG9ogN7b9ieSdhKobI1EZ6t1PvtfYURIOqca4AFpMAGiLWzsB59X/Yk+nr0PJVYueaNVIFXkiOsZXsFlNigiNnPKaj0lFsWZGzZ99Js9tprAZDVc1By05uMlLIs17jOeDARKTKfyBJPHxlQAH07GHFoRPiSJrdWjpNQq04ddco/Ie+Dn1CNAURb6HeoSqLQtADpnwxHCbnMCHLBcN2EnmpmUyTeqT+S2I8gf4TOoS0NLXBEZ5D7ReF8Ou0fFx+X8abVSsBhuUyttGd++n8Hp/pf6x26sUy4baXCSx61LWgBu8MXZ2uENuQJ3AxoNCT8gR6/Q9POYVKHBT2TZkFp/9fpfW/Fpi8iegvBopjvFcX1dgXAp/dQmil3dWOYl48k20vnA1lWLRasGrQb0U/Ssl3MXnNtoOwKHDQIOZVoOqYb/JDmwZCIrARNnXHg7HpBbIuxYij3j9aWR1auC2fIUIFGLQHBtjNa3hcPPI62OG2tejaxlbbpPUv5w=",
            List.of(
                    "Fils du maître verrier de Valmont, qui",
                    "souffla la grande rosace de la cathédrale.",
                    "Il porte au cou le dernier éclat de la",
                    "Rose de Valmont et rêve de la refaire."
            ),
            Map.of(
                    "first_meeting", List.of(
                            "§aLéon §f: §oVous voyez ce sable ? Le plus fin des Marches. Il fait un verre clair comme l'eau.",
                            "§aLéon §f: §oLéon, verrier. Mon père a soufflé la grande rosace de Valmont... il ne m'en reste que cet éclat, autour du cou.",
                            "§aLéon §f: §oVous bâtissez une cathédrale ? Alors je viens. La Rose de Valmont renaîtra."
                    ),
                    "city_arrival", List.of(
                            "§aLéon §f: §oIl faudra de l'argile, du sable... et un four qui tienne la chaleur.",
                            "§aLéon §f: §oCes maisons manquent de fenêtres. Quelle tristesse."
                    ),
                    "building_assign", List.of(
                            "§aLéon §f: §oUn atelier avec un four à verre ! Je vais pleurer.",
                            "§aLéon §f: §oApportez-moi sable et argile : je vous rends vitres, briques et terres cuites de toutes les couleurs."
                    ),
                    "level_up", List.of(
                            "§aLéon §f: §oRegardez cette couleur ! Le secret de mon père me revient, éclat après éclat."
                    )
            ),
            PersonalQuestCatalog.leon()
    ),

    // Chapitre VI — l'or
    GOLDSMITH(
            "citycore_goldsmith",
            "§eBéatrice",
            "Orfèvre",
            "beatrice",
            "ewogICJ0aW1lc3RhbXAiIDogMTYxNzM1NjYyMDQwNSwKICAicHJvZmlsZUlkIiA6ICJjZGM5MzQ0NDAzODM0ZDdkYmRmOWUyMmVjZmM5MzBiZiIsCiAgInByb2ZpbGVOYW1lIiA6ICJSYXdMb2JzdGVycyIsCiAgInNpZ25hdHVyZVJlcXVpcmVkIiA6IHRydWUsCiAgInRleHR1cmVzIiA6IHsKICAgICJTS0lOIiA6IHsKICAgICAgInVybCIgOiAiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS9kZjgzODNmNzcyMDNhZTI2ZGZhZjFhOWFiYjRhZWVhZTE4NjM4N2MyYzI3MDkzNDIyODk5NmFhOWIxN2NiODkzIgogICAgfQogIH0KfQ==",
            "L/vVx2VhLYGqOEygE3L/nL/nd8c647VXJqarzA3X0x+I3laP7r/bAZzodJ+PTHBkiQj9gS1VpF3c0yyC2bNKO/bUN6C15TtUDJUTVdSXFXU9TlWof2lVz7496SO40SFg0oAzV8Q9c7hhuVIZOgKvZTX1/RW/GabILAjZCGX3asLKeEZH+upB1MfZW0HYk66fauGjKxai4EO1LDPx9FqmiiJrzaM1y4ELA+ua07mwcaeHVwkJobITo5djEv0mT+0YHEa1ttQ+B4TRI5wboFDdEz2LEUSpLr1PKIW432I5BSNoBbAcoF9orWshbDmyCYyZOHcXB5miG2q+QdsiyvOS3x/Lq0nzrx6B9pocGBstReO8kNgv+UUBrBcirusWE2DknaNsYIRy+bVTcQBH1dlleUnjm2XUOfGtNxAR5Dl3lmE1nZ3Hmym1WzW7qqQvyzGpQhEPWDmbMJr+y28RV8cCofn9kfwVWC5Byx/qSPFtEUxtRHCKUbEKI7k6notS8FvJUyItzh9lOuncTpdM7vT7aYL5Y0HFwfqq8LsveDxZnhA1e1Frb5roMIS0VJDwz6qOvBXa5UTybJM9CHyCRUKpKdRFnnFpyIssNGhNru5me39YhnLD8Y9Va8rpI/khGdYLKGWV6iU8MkWf5s1iXh0+Aor4JcRzgG8fUGxbgnIoM/U=",
            List.of(
                    "Fille du maître de la Monnaie de Valmont.",
                    "Absente la nuit du drame, elle a tout",
                    "perdu, puis refait fortune en orfèvre",
                    "itinérante. Cynique... en apparence."
            ),
            Map.of(
                    "first_meeting", List.of(
                            "§eBéatrice §f: §oNe touchez à rien, ou je vous fais payer le regard.",
                            "§eBéatrice §f: §oBéatrice, orfèvre. Mon père frappait la monnaie de Valmont... il ne reste rien de son trésor, ni de lui.",
                            "§eBéatrice §f: §oUne cité qui prospère, dites-vous ? L'or suit toujours la prospérité. Je viens — par intérêt, évidemment."
                    ),
                    "city_arrival", List.of(
                            "§eBéatrice §f: §oHmm. Des pavés propres, des échoppes... vous avez des clients. Bien.",
                            "§eBéatrice §f: §oIl me faudra une boutique solide, avec un coffre-fort."
                    ),
                    "building_assign", List.of(
                            "§eBéatrice §f: §oUn comptoir digne de ce nom. Je pourrais presque sourire.",
                            "§eBéatrice §f: §oOr, émeraudes, améthystes : je paie le juste prix. Et je vends ce que personne d'autre ne sait faire."
                    ),
                    "level_up", List.of(
                            "§eBéatrice §f: §oVous me rendez riche. Je déteste l'admettre, mais vous l'avez mérité. Voici ma réserve personnelle."
                    )
            ),
            PersonalQuestCatalog.beatrice()
    ),

    /* =========================================================
       PERSONNAGES SECONDAIRES (recrutement bonus)
       ========================================================= */

    ROBIN(
            "citycore_robin",
            "§2Robin des Bois",
            "Hors-la-loi au grand cœur",
            "robin",
            "ewogICJ0aW1lc3RhbXAiIDogMTc3NDI5MDM4NTQ3OSwKICAicHJvZmlsZUlkIiA6ICIxYzI3ZDE1YWE5ZTk0ODFhYjk1ZDBlZTFhYjI2ZGRiOCIsCiAgInByb2ZpbGVOYW1lIiA6ICJxVGIwa2VuIiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlL2Y3N2UyZjgzMzQ5MmZkODliMTAxNzBjODE0YWE2NGM0NTYwMzE2NWU4Mjg3MWNhNzUyYjk5ZWVmNjZhZjAyYzAiLAogICAgICAibWV0YWRhdGEiIDogewogICAgICAgICJtb2RlbCIgOiAic2xpbSIKICAgICAgfQogICAgfQogIH0KfQ==",
            "JSUb3OqLFywyZ1JSuqCbDkpJEAAQ54lFFLJrmmXahF8auC3Jbu3Eel8sBLgAAUp896h/r4m0D5shSP5W9sqBenLXQxurfWIW3MEatZbdGAEp177lCZoN4M1fTF8HDPQbt5p4TgD2J74XvHGGJ7PzwmRFPW/TsHDxntUAul/U7dNOHQ+ifLEnICnFwe8Bt8Qji4W9XXlmMg5i/h2xyDlNbsAjwaXniSov2dV+2S1mlwFNdW3iFWJWUYKmRSq6E1lOtFPEy8x9VtSpwGBB7Btmu9ZjKcH4RlTMKGgji0xCVs1qd+GVfn26gSCGNELw3W5pSngofkpBX34AqJrki53lWk3ScYLa2PwMvbhM521sacR9Li5+E9/JFOznPcoemUxnvmBgskWla0wPQlr8IUuitj7q6dHISxqg48PWx6hUFn6vGsyKagHMMfaerK2Y9X9tNI7+1MMidmnNq9uiGXAbpvEFABXbCJY7hhbXZlFO0wMQ5X5FN/MYTmrjss0WPt92SiZ1qrOYlpG7AIqhzJrcmGxVNMyCq3tMJ2C6VJbmK4v5cnxbF0sz7PzChuYUBWS2GGSwbJqpUorrzHf+JDPCzbqunsvJobDCZNqm3pdjKm4VC37euZf1cteb2aKYCN621sURszI3xMkWZC3gyp87PLBQu3EZr4ItMiYNrK+LqXU=",
            List.of(
                    "Le célèbre hors-la-loi de la forêt.",
                    "Il déleste les convois des pillards",
                    "et nourrit les survivants affamés.",
                    "Sa marchandise est de seconde main."
            ),
            Map.of(
                    "first_meeting", List.of(
                            "§2Robin §f: §o*une flèche se plante à vos pieds* Pas un pas de plus ! ...Ah, vous n'êtes pas un pillard. Pardon.",
                            "§2Robin §f: §oRobin, des Bois comme on dit. Je déleste les convois des pillards et je nourris ceux qui ont faim.",
                            "§2Robin §f: §oOn dit que Valmont renaît et accueille ses sans-logis. Ça me plaît. Je vous accompagne."
                    ),
                    "city_arrival", List.of(
                            "§2Robin §f: §oPas de shérif, pas de taxes injustes ? Je vais me plaire ici.",
                            "§2Robin §f: §oNe le répétez pas, mais j'ai de quoi aider les plus pauvres d'entre vous."
                    ),
                    "building_assign", List.of(
                            "§2Robin §f: §oUne planque en pleine ville ! Mes compagnons n'en reviendront pas.",
                            "§2Robin §f: §oMes marchandises sont de seconde main... mais à ce prix-là, qui s'en plaindrait ?"
                    ),
                    "level_up", List.of(
                            "§2Robin §f: §oLes pillards ont perdu gros cette semaine. Leurs pertes sont vos gains !"
                    )
            ),
            PersonalQuestCatalog.robin(),
            "+wildernature"   // contenu facultatif : cerfs, venaison, tableau des primes
    ),

    JACKSPARROW(
            "citycore_jacksparrow",
            "§3Jack Sparrow",
            "Pirate des mers",
            "jacksparrow",
            "ewogICJ0aW1lc3RhbXAiIDogMTcwOTM5NjgzNTkyMCwKICAicHJvZmlsZUlkIiA6ICI4ZGUyNDAzYTEyMjU0ZmFkOTM1OTYxYWFlYmQwNGUyOSIsCiAgInByb2ZpbGVOYW1lIiA6ICJkZXNydHB1bWEiLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYTAzNTRmZjAzZGYwZjM0N2RiYmI3NjU1NjE2MDQzYjRiYzdiOWUxNjc2ODQxNDEwMjdhNWZmZDNhNjMwOTIzOSIKICAgIH0KICB9Cn0=",
            "fMOqMuG/FbKWFZnE5/RVmMpk4d+kftHEA0NMEjdO3QPeEGF6eLemb7gvm9fHKc4MBG3mpGVl2XPA4UAl25/vpspC0JlnJvLwwOnpwPieyy9ruGTppu0dNs++eR23EIrxz54fx+7cd+amz0uE/8snrsBNJ9jBAE8sG7CVLUUvAqVqUlAgEQkwisRQ6uFLhxgFLFIIBRGCXv9XURlSLsLwD4s8HyaHFNIpjWrqnlR5wy9OeRJ/10BijH51Ohi74zRdZmkJ0TCc0YrvSohaxzNNL61HykgYrxZSOHHuhABcBo6F+w6LohzOcV52FyQelHnyX/dWicY+2INYmQQaMUvTeEzNY/AdQ5LMQTmFqq2lraRQPMcpg11wjf/EMHva3MHDmYtHVxIN90vsLavt7pgYEx+KTLhUTqd1DvmQa1ESUdYaiyPRv1XU622okZrySQvHB9cvTgPKJo2Kj+BAK9XhtWCxS3XQo1N05DCPMIBOcz9UB767eT3jcQ9/CckDa7jIswOQsqmAqGlQTJhzr6QBDcvzYim/W+ue9w25H555zje/9RNYEa1IM9r/5ZpY1cuilyMpxKIGmc1nmRZvziPeNeKmQyiue8X2JE0ub0vJE43Ypg0vWeVA2S31JHJmtb0MNL3v2FvrUNy/Kg50INKSDNebjWt83s5my5KDFPzjjuQ=",
            List.of(
                    "Capitaine sans navire, célèbre sur",
                    "toutes les mers... pour ses dettes.",
                    "Il guette les vaisseaux de la Marine",
                    "et des pirates : il lui en faut un."
            ),
            Map.of(
                    "first_meeting", List.of(
                            "§3Jack §f: §oAhoy ! Capitaine Jack Sparrow. Capitaine, j'insiste sur le titre.",
                            "§3Jack §f: §oMon navire ? Disons qu'il est... temporairement emprunté. Par d'autres pirates. Avec mon rhum.",
                            "§3Jack §f: §oDes vaisseaux croisent au large : la Marine, des pirates, et pire encore. Il m'en faut un.",
                            "§3Jack §f: §oValmont a une rivière, bientôt un port ? Voilà une proposition commerciale. Menez la marche, moussaillon."
                    ),
                    "city_arrival", List.of(
                            "§3Jack §f: §o*hume l'air* Ah ! Une ville. Civilisée... relativement. Et la mer n'est pas loin.",
                            "§3Jack §f: §oJe reste dans le coin. Pour affaires. Et parce que la Marine me cherche ailleurs."
                    ),
                    "building_assign", List.of(
                            "§3Jack §f: §oUn comptoir sur les quais ! Presque un navire, sans la mer qui bouge.",
                            "§3Jack §f: §oRapportez-moi le butin des navires : je l'achète. Et un jour, je vous trouverai un vaisseau. Promis. Plus ou moins."
                    ),
                    "building_assign_unavailable", List.of(
                            "§3Jack §f: §oUn comptoir, c'est bien. Mais pas une voile à l'horizon : ni Marine, ni pirates, ni navire maudit.",
                            "§3Jack §f: §oUn capitaine sans mer n'a rien à vendre. Je garde l'œil sur l'horizon."
                    ),
                    "mod_missing", List.of(
                            "§3Jack §f: §oJ'ai scruté l'horizon tous les soirs. Pas un seul navire : ni la Marine, ni l'ombre d'un pirate.",
                            "§3Jack §f: §oPas de navires, pas d'affaires. Revenez quand la mer sera plus... fréquentée."
                    ),
                    "level_up", List.of(
                            "§3Jack §f: §oLes affaires reprennent ! J'ai récupéré quelques... babioles. Ne demandez pas sur quel navire."
                    )
            ),
            PersonalQuestCatalog.jack(),
            "taxov"
    ),

    INDIANA(
            "citycore_indiana",
            "§6Indiana Jones",
            "Archéologue aventurier",
            "indiana",
            "ewogICJ0aW1lc3RhbXAiIDogMTYyMDk4NDA3NzE1NiwKICAicHJvZmlsZUlkIiA6ICI0ZTMwZjUwZTdiYWU0M2YzYWZkMmE3NDUyY2ViZTI5YyIsCiAgInByb2ZpbGVOYW1lIiA6ICJfdG9tYXRvel8iLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZWVhZjQzYzc0NzQ5N2UyMGZlMDY2N2Q2NzgxMGFhMWYzY2IwYWViOTZmY2M5NGI3Y2UxOGY1ZDE2NWM4Mjg0ZCIKICAgIH0KICB9Cn0=",
            "i0aTjGR3jQ6DwCLG9NPQzNTGrQE7dFpKLFF7/MHHOYRAwARsfIXR5VldYAhnPRKKfve4SkjiCs7Kz+C8J1Kj5lGdSjYgr2jg+Zv6hDMDYA1w74f5zBmsOXpS3HNuYk0E3hIVXKjA8v2uC4bGUC4ZMJUCGz/6lIT/u1W1ZzRHAamJRo4Ou9gL7q+1CfmNvRayGeHI1qtBQNI+cP/N0PUCKUiUiPJRhIIhOFl8s60qhX6iwMyJ6wL6CntJqu8NiHpFcuBxnTHaXCzvL8r2YtEzn4CeqWQHeZs0ftJl2S/x3rCoG5Y1xyn37oDbkno6DD9/ITaSgIfDM2nd/sms+xfs/VqgipvBu1R8EE0N1rpooeybIKbuGyNnQz8UGFeJitV0rW/QL3JTA7qU44NO1g7kjl4vTZt3YsQs/c6W98nL/Bqhw7UeVjdU2OoFzBOuVZKD7VlESHxJMOyBR0j132F4h3ujBH6kVkJ0fbJcwPoTG9Fwiyc7vAshe9LzUHY4GLogIWQ0l4Wt1RWdUHCGu4EumCSdookAu3AwPGgMgo16y+DP38EyAJHT/HzZoEiIs1Bd3ANLBjdkulVgYr85WB76AxgD9JXSn3nChLrK9AGTRr9x36ItBzB+q6054/g6QVD3aMiVGTOtmpKFC3kHcbcDMsZ8fKk8QW6EHYW7Z71U3QI=",
            List.of(
                    "Archéologue et chasseur de reliques.",
                    "Il a fui un temple qui s'effondrait,",
                    "le chapeau sur la tête et le fouet",
                    "à la ceinture. Il déteste les serpents."
            ),
            Map.of(
                    "first_meeting", List.of(
                            "§6Indy §f: §oChut ! Ne bougez pas... ce sol est piégé. Ah non, ce n'est qu'une racine.",
                            "§6Indy §f: §oIndiana Jones, archéologue. Je cherche des reliques, pas des ennuis. Ce sont les ennuis qui me trouvent.",
                            "§6Indy §f: §oVotre ville a obtenu le droit de marché ? Conduisez-moi. Et dites-moi qu'il n'y a pas de serpents."
                    ),
                    "city_arrival", List.of(
                            "§6Indy §f: §oDes ruines sous vos remparts... Il faudrait un musée ici. Tout ça appartient à un musée !",
                            "§6Indy §f: §oIl me faudra un comptoir pour mes trouvailles. Sans serpents, de préférence."
                    ),
                    "building_assign", List.of(
                            "§6Indy §f: §oParfait. Mes caisses d'expédition seront à l'abri ici.",
                            "§6Indy §f: §oRapportez-moi tessons, clés des épreuves et curiosités. En échange : plantes rares et reliques de mes voyages."
                    ),
                    "level_up", List.of(
                            "§6Indy §f: §oUne nouvelle caisse d'expédition vient d'arriver. Ne demandez pas comment je l'ai récupérée."
                    )
            ),
            PersonalQuestCatalog.indiana(),
            "+mostructures,+ati_structures"   // contenu facultatif : récits dans les structures des mods
    ),

    DOCBROWN(
            "citycore_docbrown",
            "§eDoc Brown",
            "Inventeur",
            "docbrown",
            "ewogICJ0aW1lc3RhbXAiIDogMTYyMjkwNDIxNDIxOSwKICAicHJvZmlsZUlkIiA6ICJiYjdjY2E3MTA0MzQ0NDEyOGQzMDg5ZTEzYmRmYWI1OSIsCiAgInByb2ZpbGVOYW1lIiA6ICJsYXVyZW5jaW8zMDMiLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNWU4MDNiM2RlY2VlYjM4ODBlZWIxOWRlYTEwYTExMjgxZjVkMGJhODczYjA3MDZiNzZmNTQzYTdjMmY4ZmQ3MCIKICAgIH0KICB9Cn0=",
            "tU4TGu5sTX8tpEpr/nsjKljB2SSTNKDsWmDztUE6PqNHyfVWHxjVKmn8YGmm7sB6oagsC4Ei+POZDHfmPFZldMqSjmAQHZf/jplE1g/4nJn+PjdMmQv3HraHccyz98qT1XYbG+y125358ahYPu9a3cUCBgez+tfdnP7qS7MNxyDpS6hmFELUlv/9fnXvS68ntrjvkWegRHzsUpoGyKop50cWLYLIjGUS4Tf/ZlCYNlGMXWxwK6kDDkfkNwIQ9t4WYoldfh0LyGKj40cX810ahHbK+0MZzLe0oKz8b0oovOSQ7xZNDEOthHGjMWpngl5ecyTn/SrhWbTd4ZIRwZi3B707p545baQipicSkomm84lSx9No3igR47EH2simai/XZmJg7thLeAjGirjwMsv/nN+dM+V2ohIOUDSePpZhraoZfjoOYyj8PIcR5IaBpkMsS3bboyqga2Ar2EQ2JYk8ctfuyYmc6Z1a+s8eZAiMCAelBMIGM/stf2Vvc4FHQRlpqduB0uGhY6wmt+xdrkc3tmFwowbfVzp3vdiFsyeKIYyyI+7CbKHvTCGsU3oHpqNNMnrTiBQqfhbbZxJzQtNeXRmTurkXx5iCSqWFgl3Gk2TvquzcmAefMKyyIF1TahMxqgs8cJ6FnDiwMYvTABmVYnZnvl7ZOE8xyuoxHKO6Y5U=",
            List.of(
                    "Inventeur génial et un peu fou.",
                    "Un éclair l'a projeté dans ce siècle",
                    "avec ses carnets, sa redstone et les",
                    "débris d'une bien étrange machine."
            ),
            Map.of(
                    "first_meeting", List.of(
                            "§eDoc §f: §oNom de Zeus ! Quelle année sommes-nous ? Non... ne répondez pas, ça vaut mieux.",
                            "§eDoc §f: §oEmmett Brown, inventeur. Ma machine s'est écrasée ici après un éclair de 2,21 gigawatt !",
                            "§eDoc §f: §oUne cité qui rêve d'une cathédrale ? Il lui faut des machines ! Des pistons ! De la redstone ! Vite !"
                    ),
                    "city_arrival", List.of(
                            "§eDoc §f: §oFascinant... Avec une tour d'horloge comme celle-là, on pourrait capter la foudre !",
                            "§eDoc §f: §oDonnez-moi un laboratoire, de la poudre de redstone et beaucoup de calme."
                    ),
                    "building_assign", List.of(
                            "§eDoc §f: §oUn laboratoire ! Je vais enfin pouvoir réparer ma machine... ou en construire une meilleure.",
                            "§eDoc §f: §oApportez-moi quartz, boules de slime et poudre à canon : je vous construirai des merveilles."
                    ),
                    "level_up", List.of(
                            "§eDoc §f: §oRegardez mon nouveau mécanisme ! Non, ne touchez pas, il n'est pas tout à fait... stable."
                    )
            ),
            PersonalQuestCatalog.docbrown()
    ),

    GANDALF(
            "citycore_gandalf",
            "§fGandalf",
            "Mage",
            "gandalf",
            "eyJ0aW1lc3RhbXAiOjE0OTM1ODEyMDU2NjgsInByb2ZpbGVJZCI6ImUzYjQ0NWM4NDdmNTQ4ZmI4YzhmYTNmMWY3ZWZiYThlIiwicHJvZmlsZU5hbWUiOiJNaW5pRGlnZ2VyVGVzdCIsInNpZ25hdHVyZVJlcXVpcmVkIjp0cnVlLCJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMWM0YTVlNDc0N2UwZTY5ZmQ5MTY1OTQ1ZDZhYmIxYzBhNzI1OTYxYWNlNmJmNzRhZWM1NGY3NDQzZDZiOWUxNSJ9fX0=",
            "imIk3vKxnCHHBzzjdvdRPgfFmhtOMvdowzPIQpLNDAl0Vk7JBWkt+q/aGaEoKtG+JzUWtyYXmPmmiCOlwdCD64u9kaloEZRllllzMkpFP84IKBqPYE6AJ0SB5jWyaq5uYL9bpjhsUx8d6z+gUjF3gCAFa8hn4K8oS350w++cPtjIBFDs106+xySUQdwJwtcoRrLRBbN/N/RGKjNfVASy7fKERDOZfIHZfPbhRa6B/e8ZeCgjDudLHlBmR2LeUHJaetRcE7P3xvGQcngw+wfnc4Q7P1MDd7R0R5q2JVjijoUIjb1Xe1YpDHeDE8MQnclIHiIZAlygN8BYv3bO/vOYVnCXaVtSAs4l+tPWMuF7yzOWnMNEqu0ZMc/Y/0Yt5FUqERsOcfigzTNzhXQGUusdhEEg3kMRUdIk8vdrLRv8LvbhuYzDW2aaYMZPXD4FhpJKUiLhxDeVOz0/emIus9OJ8HwsZlhE3P6/Zu8ClT2OWOsqS1oX1LF7rO38N0wOIVBN9ADV4YXNiyDA3eri8vN864N/1AJgQrfcbCpYm/gwoiIMHw/AbdGIeEXDPBsAt4g17nnMPsr67sLuCbkAY8thzSyaN1dc/yb3bQebQmmg/sWvMAfMGieBS6PBW0ZJtbIc7eUcALWdq/Jz+0hPfEYudI4uNk11+czDQLf56yTVNHA=",
            List.of(
                    "Un magicien gris, errant et énigmatique.",
                    "Il avait mis en garde les mages de",
                    "Valmont contre la Porte. Ils ont ri...",
                    "Il traque le dragon depuis."
            ),
            Map.of(
                    "first_meeting", List.of(
                            "§fGandalf §7: §oTiens, tiens... un bâtisseur. Il en faut, en ces temps de cendres.",
                            "§fGandalf §7: §oJ'avais mis en garde les mages de Valmont contre la Porte. Ils ont ri, jusqu'à la Nuit Noire.",
                            "§fGandalf §7: §oVos routes sont rouvertes, et les routes amènent toutes sortes de voyageurs. Il est temps que je veille sur votre cité."
                    ),
                    "city_arrival", List.of(
                            "§fGandalf §7: §oJe sens une grande destinée en ces murs... et une ombre qui guette depuis le Néant.",
                            "§fGandalf §7: §oIl me faudra une tour. Haute. Avec beaucoup de livres et peu de visiteurs."
                    ),
                    "building_assign", List.of(
                            "§fGandalf §7: §oUne tour de mage. Voilà qui me convient.",
                            "§fGandalf §7: §oApportez-moi perles de l'Ender et curiosités du Néant. En échange, je partage un peu de mon art."
                    ),
                    // Avec Iron's Spells 'n Spellbooks : la magie des sorts
                    "building_assign_mod", List.of(
                            "§fGandalf §7: §oUne tour, enfin. Les sorts s'écrivent à l'encre, et l'encre se mérite.",
                            "§fGandalf §7: §oApportez-moi l'essence des arcanes : je vous apprendrai à lire les parchemins."
                    ),
                    "level_up", List.of(
                            "§fGandalf §7: §oVous avez gagné en sagesse. Il est temps de vous confier des savoirs plus anciens."
                    )
            ),
            PersonalQuestCatalog.gandalf(),
            "+irons_spellbooks"   // avec le mod : encres, parchemins, grimoires ; sans : magie vanilla
    ),

    GERALT(
            "citycore_geralt",
            "§7Geralt de Riv",
            "Sorceleur",
            "geralt",
            "ewogICJ0aW1lc3RhbXAiIDogMTYxODgyODc5MjUzNSwKICAicHJvZmlsZUlkIiA6ICI5MThhMDI5NTU5ZGQ0Y2U2YjE2ZjdhNWQ1M2VmYjQxMiIsCiAgInByb2ZpbGVOYW1lIiA6ICJCZWV2ZWxvcGVyIiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlLzk0YTAzNmFhMjU1ODE1NDAxNzk0ZjcwMjRmODkxMzk5M2U3NGMwZWZjM2ZlY2YxNDFkNDY2ODVjZjQ4MDE2ZGUiCiAgICB9CiAgfQp9",
            "TZhRgSEwGLlAtjQ5SdmOitYxtzQGM8Es2jBptr/nLsqYsJG1NIMYZGzzkZp6+t7xFIYUM6oC+rP5bmbrQhQOwdu9jDOuTSuMeKbsJbwyTqTk44mOzD7uwQRk/EibTIidHrfu/iwsaU1b9hJ309HpWVp7rAH5swPI35kD8wwchAoCLR1C1t/ZIlMbTNGB2DuunbmE2qHW3S+iIhNaPNnsjCTioWOhbbepKf+hef/t8oRqHUb1gEtUI+S7M+kbiRL1uDompEYtSt+RJqyorb58ZWfpuqgoi8I/RnH3j0llsgULhYy49yQnHWfVc3CMsOuySagKq2vGD8f8rnmkJfvEtfuXmWvGttynTW5HnbUUGcmFfaJEFULVZtF8zuyF3xDC8wqnDV9hQ7yc/LggsoUAB99zBpJI9bIuv62v1kZEzZAZ13+zaR59E9eeN1OOZj/MVfkP9r7CTNqcS5b1dwG8ICBRqPpMX4btFiQ3C6y9wwoQtB4zams7RvoVXkLu0t0Tx4IEDCIx+qUTigjNAz8QE+Rry2k1muZ0mJsbX0KiVysADjA6cZxfoUSOTuROCgckrNI4cnYJpInhHZxHMDSX6u2zovtvil2pM1fWel42XVt5Ek4HpKa0WC9kyeIPJLeU3o5R9mMvVSwxnluFMB5ILWBCbvK0p7Twq2EFOxVS/+A=",
            List.of(
                    "Chasseur de monstres aux cheveux blancs,",
                    "armé de deux épées : l'acier pour les",
                    "hommes, l'argent pour les monstres.",
                    "Il ne travaille jamais gratuitement."
            ),
            Map.of(
                    "first_meeting", List.of(
                            "§7Geralt §f: §oHm.",
                            "§7Geralt §f: §oGeralt. Sorceleur. Je chasse les monstres, contre rémunération.",
                            "§7Geralt §f: §oUne capitale attire les monstres comme le miel attire les mouches. Vous allez avoir besoin de moi."
                    ),
                    "city_arrival", List.of(
                            "§7Geralt §f: §oTrop de monde. Trop de bruit... Ça ira.",
                            "§7Geralt §f: §oJe prends les contrats. Payez rubis sur l'ongle."
                    ),
                    "building_assign", List.of(
                            "§7Geralt §f: §oUne écurie pour mon cheval et un lit propre. Bien.",
                            "§7Geralt §f: §oTrophées de monstres : je les rachète. Potions et lames : je vous les vends."
                    ),
                    "level_up", List.of(
                            "§7Geralt §f: §oVous vous débrouillez. Pour quelqu'un qui n'est pas sorceleur. Tenez."
                    )
            ),
            PersonalQuestCatalog.geralt()
    ),

    // Lié au mod Dragon Mounts Remastered (identifiant « dmr ») : sans lui, pas de menu
    HAROLD(
            "citycore_harold",
            "§cHarold",
            "Dresseur de dragons",
            "harold",
            "ewogICJ0aW1lc3RhbXAiIDogMTY0NDU4NDE0NDU3MiwKICAicHJvZmlsZUlkIiA6ICIxNmFkYTc5YjFjMDk0MjllOWEyOGQ5MjgwZDNjNjE5ZiIsCiAgInByb2ZpbGVOYW1lIiA6ICJMYXp1bGl0ZV9adG9uZSIsCiAgInNpZ25hdHVyZVJlcXVpcmVkIiA6IHRydWUsCiAgInRleHR1cmVzIiA6IHsKICAgICJTS0lOIiA6IHsKICAgICAgInVybCIgOiAiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS84ODcyZmJmYTA1MTQ2ZDIzZDQ2ZDkxMzIwODQ1OGZlMjcxZTY5ODg0OGQ5N2UxY2VhZWEyMjljNTNmMjFmMTI5IgogICAgfQogIH0KfQ==",
            "s+oaTKEkAjqpt1rxwOy0QRrXZ16/M/KQ2o8+WF8A15shxazE2FkkjS7JItZR6fQbo5ZlEag8lrQagZyEKfrkNi11X7njgbCAmJMqwhOTPndNPISpoR7pLZR6hvwOxxK5jZLSUarsFuIWhaAGnC0NG1doYJrDSRT1tP3lrnuIADVsYZC12jFqf0PwUSKq5agUnKrN97YtuvLT4JmWtXDP33rGTda8aa7kuTQhG26x+W94QdT3jRz5wR6GNq0uUGj06aMl/0jE5YnRI+QHu1WLJWx0UDCqoTq1TYe9H/23O3hU6MOsY6xvFSRuqbFSf9qb+8mAevOSDUeubmrAA0rqTZk97mEMjPJiPSw/NzvrUjjOchmwtRmzUbEKR3Pwoesd7ej4y2jr1rIVpYgTJUzZCtggwBm1Hv/bksvznI7d5vGgDtoPIGdAnwC2XFl24ZxdEwrvbevcwJfvfGQNVUFo5XH6DXNeKNXWtCZNE7SaHTeRpzPSeGqpA6zovm2/gNK84NyRNl8om8OrIseKgfKbDLjxZjNf6Ojh/JxtXdCXHrxRzMZ+jWBCn8/taZL4JjP6KGxeeIKXda8U+ObiQc6UCeVOpU3SgBDlY3gu+n8Y4NRC4LVtOL0Cv1YV+8u5p54nHerclhF4Gx80lERJ/IAXExzTSjpV7QIK842ibCX6gMs=",
            List.of(
                    "Jeune Viking de Beurk, fils du chef.",
                    "Il a prouvé qu'un dragon peut être un",
                    "ami en soignant Krokmou, une Furie",
                    "Nocturne à la queue blessée."
            ),
            Map.of(
                    "first_meeting", List.of(
                            "§cHarold §f: §oOh ! Euh... salut. Pas de gestes brusques, d'accord ? Les dragons détestent ça. Moi aussi.",
                            "§cHarold §f: §oHarold, de Beurk. Fils du chef... et dresseur de dragons, même si mon père a mis des années à l'accepter.",
                            "§cHarold §f: §oVous avez abattu le dragon du Néant ? La Porte l'avait rendu fou. Les autres ne sont pas comme lui.",
                            "§cHarold §f: §oEmmenez-moi à Valmont. Je vais vous prouver qu'on peut voler avec eux plutôt que les combattre."
                    ),
                    "city_arrival", List.of(
                            "§cHarold §f: §oDes remparts, des tours... il ne manque qu'une piste d'envol !",
                            "§cHarold §f: §oTrouvez-moi un coin dégagé, avec vue sur le ciel. Et du poisson. Beaucoup de poisson."
                    ),
                    "building_assign", List.of(
                            "§cHarold §f: §oUne dragonnière ! Krokmou adorerait se rouler dans cette paille.",
                            "§cHarold §f: §oApportez-moi poisson et membranes de phantom : en échange, selles, sifflets et tous mes secrets de dresseur."
                    ),
                    "building_assign_unavailable", List.of(
                            "§cHarold §f: §oMerci pour le toit. Mais une dragonnière sans dragons...",
                            "§cHarold §f: §oSi un jour des œufs apparaissent dans ce monde, vous saurez où me trouver."
                    ),
                    "mod_missing", List.of(
                            "§cHarold §f: §oJ'ai parcouru tout le pays... à pied, faute de dragon. Pas un nid, pas un œuf, pas une écaille.",
                            "§cHarold §f: §oSans dragons, mon savoir ne vous sert à rien. Je reste à {ville}, au cas où ils reviendraient."
                    ),
                    "level_up", List.of(
                            "§cHarold §f: §oVotre dragonnière grandit ! J'ai de quoi équiper de vrais dragonniers, maintenant."
                    )
            ),
            PersonalQuestCatalog.harold(),
            "dmr"
    ),

    /* =========================================================
       LÉGENDES LIÉES AUX MODS (sans leur mod : recrutées et installées, sans commerce ni quêtes)
       ========================================================= */

    // Chapitre I — Tough As Nails (soif, température, eau purifiée)
    ROBINSON(
            "citycore_robinson",
            "§aRobinson Crusoé",
            "Naufragé",
            "robinson",
            "ewogICJ0aW1lc3RhbXAiIDogMTc0NjU3OTI2MjI0OSwKICAicHJvZmlsZUlkIiA6ICJkYzA5MjA4MTM2ZDg0Y2Y5OWIwMzFmMGI1NzM4OTdmNSIsCiAgInByb2ZpbGVOYW1lIiA6ICJLRUlUSF8wMzAyIiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlL2EyYjlhNTcyNjQ1MTRhYTRiZWEyNDMxMGU5MDJkNWE0MGNmNGM0MmZjZWZjMWRjODQzODk0Njg3NDNhMzg5ZGIiLAogICAgICAibWV0YWRhdGEiIDogewogICAgICAgICJtb2RlbCIgOiAic2xpbSIKICAgICAgfQogICAgfQogIH0KfQ==",
            "tMX0wEHd4zrfNsq/bc2IAHhUWp4UvcoCdngGTsxZabq7S71gNbdqyxkZl/sksRCpVEwv0ExtnuKd+bGn1u81fJ1h1cnM2snJ3fi7/gIlnl2gmF5Oy4eZv1gv00tQAvfsME2Acs9UhpkxmcVsiODm3KrnqrkAFgigkRpCb5vnUDtXbXay1+K9c19362Xkt+SCmcPaQFFMMu5fzM8+LmU/CACuXBXbM/ctfs1XH9X5hVRQ7klDKW167ISidADmnHL8bvKwB/yp+fuxrPuCojz5LATuBowiv83JSU9roYB1gGFDuVfpMEbvHcMc1ANMnCPhleLb2EUdYvyqmcgbxnqFjsI3dP8Ol3zyzBD36CRfqX1zuHLhOcdxLo2CeEEfVUa14f3wYE+UNxIObjRHrFNTTBx0dTFNo3jisRnKCuM4J+ZkxYiiyo2wFCSTmvRmh694llshV5WvxfumJBHZBGGl73gsXUBQhEZjmcblSr64giA9INCvgfz1Pom01z3z0Aqr8Qy/+/NgU12pVueS+OdyqUKTYIY+29Nea9RM3x3/8c/H0agEBVYDpBdeHAIRUjoXXqhcVrT8f3FYEaxM2tHQAEnhA9/UAt4lS6snvqBaKGu+FWi/lN9KdPxz0kH1z/DTXTJyL4cEANw7MfPyksqFdaTfGPcuw7zDvX6aC+puz8U=",
            List.of(
                    "Naufragé pendant vingt-huit ans",
                    "sur une île déserte. Il sait tirer",
                    "de l'eau potable d'un désert et",
                    "se tenir chaud dans la neige."
            ),
            Map.of(
                    "first_meeting", List.of(
                            "§aRobinson §f: §oUn visage ! Un vrai ! Pardonnez-moi, je n'ai parlé qu'à un perroquet depuis des années.",
                            "§aRobinson §f: §oDe l'eau qui ne rend pas malade, un abri contre le soleil et le froid : voilà tout ce que j'ai appris.",
                            "§aRobinson §f: §oVotre campement en aura besoin. Je prends mon poteau à encoches et je vous suis."
                    ),
                    "city_arrival", List.of(
                            "§aRobinson §f: §oDes toits ! Des voisins ! Je vais devoir réapprendre à dire bonjour.",
                            "§aRobinson §f: §oUne cabane au bord de l'eau me suffira. Et un poteau, pour compter les jours."
                    ),
                    "building_assign", List.of(
                            "§aRobinson §f: §oUne cabane, un chaudron, un feu. Je suis riche.",
                            "§aRobinson §f: §oApportez-moi ce que la nature donne, je vous apprendrai à survivre."
                    ),
                    "building_assign_unavailable", List.of(
                            "§aRobinson §f: §oUne cabane, un chaudron, un feu... Mais ici, l'eau est pure et le temps est doux.",
                            "§aRobinson §f: §oJe n'ai rien à vous apprendre... profitez-en."
                    ),
                    "mod_missing", List.of(
                            "§aRobinson §f: §oIci, l'eau est pure et le temps est doux. Je n'ai rien à vous apprendre...",
                            "§aRobinson §f: §o...profitez-en. Moi, je continue de compter les jours."
                    ),
                    "level_up", List.of(
                            "§aRobinson §f: §oVous apprenez vite. Sur mon île, il m'a fallu des années pour comprendre ça."
                    )
            ),
            PersonalQuestCatalog.robinson(),
            "toughasnails"
    ),

    // Chapitre II — Tide (poissons du mod, pêche en grotte, dans la lave, dans l'End)
    ACHAB(
            "citycore_achab",
            "§bCapitaine Achab",
            "Chasseur du grand poisson",
            "achab",
            "ewogICJ0aW1lc3RhbXAiIDogMTcxNDE5NjY5NzA3MiwKICAicHJvZmlsZUlkIiA6ICJmOTEyM2ZjOGQzMzg0OWMwOWFlMzk5YjQ2NDljZDRjZSIsCiAgInByb2ZpbGVOYW1lIiA6ICJab2VfX01heSIsCiAgInNpZ25hdHVyZVJlcXVpcmVkIiA6IHRydWUsCiAgInRleHR1cmVzIiA6IHsKICAgICJTS0lOIiA6IHsKICAgICAgInVybCIgOiAiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS8zZTEzZDNmZDNhYTZmY2JhZGFkNDIyZDNmMGQ0Y2MwMDBlODk4YTY3YmY5MWI3ZjVlZTYzNTVjZWRkMmUyOGZiIgogICAgfQogIH0KfQ==",
            "f/Lo+ov8bF4ipVQONGnNG56h/6QSEDN8zhbVc8y9iA6EYce0TO61umtjYo1RIscONJfxZ+o9vJM+ZN8jlh8mblSy3zlqk9EEmODzHLCKjOagQe0EP24cxTpuZ8bZv2dcUim3Blx3S6shvsthy15ibx997bg9Q9OMZz4BcC0Nq3pzngN09txvzwal65BKDrsJqfM+vFys0bW6QHrHPqxmYRBelh5+92KqlhWAqTvrbEwAgikAYJgXNJF2UGFn6ZF9PVBkqSleAmqQK/d5Y8k3sb75YphHWVslcj2LB4xOOMVc3rPvF5a8iU96RR9DvqB/gM8QvBPwf77B2byCPzitbBHatUeW7MXHu+I0wjo3x88HXN7IUIvu9SEFu26pJz8nqT8q1BooNFh4JFb7FDI7lVfNY9Sp1wngHrjBByHrVsURvUwVVK8/NwXAnE1XGs0CPkSqoYslTQ+vJH1POn60bJF7R6VesCMxlTmywhhbSkJqHgCgYZSYNHuJqZiARDMn0Ke2XXIro7GRSf3p/DjZegVsvZcgJvr70DNeW8EK5vgvO/ehox1bmUzE0S8KkmqQ7n+rDJI6UE0toXsI0PE8zLWzUbTDr4/dQb+Ha/7Uy7py/d8KCodFtfTzWd4t9qchYfmPZwJ5RyFRRppZgwC8Q8vW0oiDtDzdWRc1kAkL5EI=",
            List.of(
                    "Il a perdu une jambe, un navire",
                    "et son équipage pour un seul",
                    "poisson. Il compte bien perdre",
                    "encore un peu."
            ),
            Map.of(
                    "first_meeting", List.of(
                            "§bAchab §f: §oAppelez-moi Achab.",
                            "§bAchab §f: §oCes eaux cachent des bêtes que vos pêcheurs ne soupçonnent pas. Je les pêcherai toutes, jusqu'à la plus grande.",
                            "§bAchab §f: §oVotre hameau a une rivière ? Alors j'y jetterai ma ligne. Menez-moi."
                    ),
                    "city_arrival", List.of(
                            "§bAchab §f: §oUne rivière, des pêcheurs... et des eaux qui cachent leur jeu.",
                            "§bAchab §f: §oDonnez-moi un ponton face au courant. Je ne quitterai pas l'eau des yeux."
                    ),
                    "building_assign", List.of(
                            "§bAchab §f: §oUn ponton, un tonneau, une ligne.",
                            "§bAchab §f: §oRapportez-moi tout ce qui frétille d'étrange : je paie, et je paie bien."
                    ),
                    "building_assign_unavailable", List.of(
                            "§bAchab §f: §oDes morues. Des saumons. Toujours les mêmes.",
                            "§bAchab §f: §oCes eaux sont mortes... ou elles se taisent."
                    ),
                    "mod_missing", List.of(
                            "§bAchab §f: §oJ'ai vidé la rivière trois fois. Des morues, des saumons, rien d'autre.",
                            "§bAchab §f: §oCes eaux se taisent. Tant qu'elles se tairont, je n'ai rien à acheter."
                    ),
                    "level_up", List.of(
                            "§bAchab §f: §oLes eaux parlent enfin. J'ai de meilleures lignes pour vous, et de meilleurs hameçons."
                    )
            ),
            PersonalQuestCatalog.achab(),
            "tide"
    ),

    // Chapitre IV — Wilder Nature et Farmer's Delight (gibier, banquets) : les deux mods requis
    OBELIX(
            "citycore_obelix",
            "§9Obélix",
            "Livreur de menhirs",
            "obelix",
            "ewogICJ0aW1lc3RhbXAiIDogMTc1OTYyMTkyODUxNiwKICAicHJvZmlsZUlkIiA6ICJmZDlhMmNhNGNhMWM0YTkwYWEyODkyNThkYmM3MmFiNSIsCiAgInByb2ZpbGVOYW1lIiA6ICJJRnJhbmtvWCIsCiAgInNpZ25hdHVyZVJlcXVpcmVkIiA6IHRydWUsCiAgInRleHR1cmVzIiA6IHsKICAgICJTS0lOIiA6IHsKICAgICAgInVybCIgOiAiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS9hYTgxNjkxMjVjNTk3NzgwNGIwMzZlNmJjMTAyYjQ4NzhmN2UxM2RmZTEzMTA1M2JjMzI1NzRkYmE0OWNhM2Q0IgogICAgfQogIH0KfQ==",
            "QoKcnfEBTcw/3on8ZZ7z5FTt0R/Ra7k+KgTuUw6FIX7YWDKwQA0cc5PT6eVc3XHQRq+ljPQSj8fG6uS7JIjzlRnyA44tnjzQqu2yJ8remKe9cnsNrHapoAMOWV5k6u4KyZxSwH9cDqJiwkN5ajx8Q6mFDd3WQKIdBO7IApOafAh9Xv3zea/DvNtF2xygNrusESoym85zDAQKP7iD3jBaOuRUacS/OMO9jSGhIc8eJXTL9E5FoSWZBTmjbpCTyFpyx5WSxAP1zeey9mogDgpEgChHcoKEXviJGOb7/JwaAToLlVbFsi3N/kvSQzf+wuVLWq7K2g7r+EPQKDjD+4vtJ1ysF+y86Ew0U+I5XQzVdNzxMxdtUV7Xwjq9B+s5JyQIls9LZqjyFpWd94c7Sa90pA5iyULKXPavodOME5gub8pXrk4lUrt1sqtFlrTz21+T1yJBUBOR0b8f9lerN2tlahLN4VS4lmBxfpqWU7FmTh2V3zy2AAoNCu07+NgDAfBuBGFrOm08KMPw0sqnIVxdekKnvzF/4zSROiq17OYEBdC7WnbxcU5sNBPwrcNkNcAMNMPFJ+qk7wcmdW/Df4G0QD945RhOFIQYv00jpGJaZdaCrBMmkMag013f+N+9630wEcFWA85Yu/J8YV5prbB0U8a93qcntFuJnrCcOjMotew=",
            List.of(
                    "Livreur de menhirs, chasseur de",
                    "sangliers, mangeur de sangliers.",
                    "Tombé dans la marmite quand",
                    "il était petit."
            ),
            Map.of(
                    "first_meeting", List.of(
                            "§9Obélix §f: §oJe ne suis pas gros ! Je suis... enveloppé de muscles.",
                            "§9Obélix §f: §oIl paraît que votre taverne sert des sangliers ? Alors j'en suis. Et je porte mon menhir moi-même."
                    ),
                    "city_arrival", List.of(
                            "§9Obélix §f: §oÇa sent la soupe, ici ! Une taverne ? Très bien, très bien.",
                            "§9Obélix §f: §oOù est-ce que je pose mon menhir ? Il me faut une hutte, et une grande broche."
                    ),
                    "building_assign", List.of(
                            "§9Obélix §f: §oUne hutte, un feu, une broche.",
                            "§9Obélix §f: §oRamenez-moi du gibier, je vous prépare un banquet."
                    ),
                    "building_assign_unavailable", List.of(
                            "§9Obélix §f: §oPas un sanglier dans ces bois. Pas un !",
                            "§9Obélix §f: §oIls sont fous, ces Valmontois."
                    ),
                    "mod_missing", List.of(
                            "§9Obélix §f: §oPas de sangliers, pas de marmite, pas de banquet. Pas un !",
                            "§9Obélix §f: §oJe reste pour la soupe de la taverne. Mais je n'ai rien à vous vendre."
                    ),
                    "level_up", List.of(
                            "§9Obélix §f: §oUn banquet par jour, ça creuse ! J'ai de nouveaux festins pour vous."
                    )
            ),
            PersonalQuestCatalog.obelix(),
            "wildernature,farmersdelight"
    ),

    // Chapitre IV — Epic Fight (armes, tenue du vagabond, livres de compétences)
    MUSASHI(
            "citycore_musashi",
            "§cMiyamoto Musashi",
            "Rōnin",
            "musashi",
            "ewogICJ0aW1lc3RhbXAiIDogMTc4NDEwMDQ3MTQ0OCwKICAicHJvZmlsZUlkIiA6ICI3OTI0MDQ0N2ViNTk0ZDBkOTVmY2E3YmJiMWZiMjVhNSIsCiAgInByb2ZpbGVOYW1lIiA6ICJqdW5pb3JraW9iciIsCiAgInNpZ25hdHVyZVJlcXVpcmVkIiA6IHRydWUsCiAgInRleHR1cmVzIiA6IHsKICAgICJTS0lOIiA6IHsKICAgICAgInVybCIgOiAiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS81MGE0YmI5NWMzZmY1NDdkNzNhMGM1YTdmNzRhN2E5M2I0NTA4OGI3NGY5YTgyMzZhNjU1NDU2OGRkOTNjNmE4IgogICAgfQogIH0KfQ==",
            "uKXt41xa2gfhm5EDPJ9e3va6GkLEB73VLby/Nl3DSx+4XCLL8hbj04D5ZJyhm4KZnG7W6XhFyno+hjI6LbPFnI7F9aQrf3uyOFcNSt0iPp8tUAqco3h0DXMaEt8Qy2xbw7YYCrec0ToEcW0pSy9irXYA5vdmDoFf3A+tw40T5rXWktWI0003HssukUszxdxzRIRg0N4X1cjiK6/ErtLud1zC+KXHx5rrZZi45ouiwj8XyBzo/1QsSB3jyc0R/bHDJh+R8i4yabkHTXY0gxdXcAhz5/tg4WgpqUH34N62i76n6e87Se1IUnReTnAH5cU3ZsRhNwQ00JA0ICfu/n5KpfGOkll+4yzguGfsPOoMrzXW9WbkSA8Ua4e7oULgsnGY5uxaQj5jN1IVFFw/oWo9pxYBldhVBZln5GKBpCJLDfcOVZN+bwRuYYzerJ88tBY/AzJ/YeiPy/XGt9OdzHzAfIuSbkjzSivBwWU9qbpFS+5i0fuJPWppaNdRs7Sb0iCLiBeQC6+dt6sGDt7R7RhgNlXYpjJBGR8AaaVJ6UCQpTNPunrFkNfVT4HnxP6LWNTO9iiOjtNfVtoDo8s0iQhtz0aY7m8FUOskyDPpyjwZgVikdPMBO/Q1ehJ9ro5BgO3bntGPj0bwxEfWmN92jo5SXZtMJ5eVaURgvBZcC6+2Ixc=",
            List.of(
                    "Soixante duels, aucune défaite.",
                    "Il dit qu'on ne gagne pas avec",
                    "le sabre, mais avec le regard."
            ),
            Map.of(
                    "first_meeting", List.of(
                            "§cMusashi §f: §oJe ne cherche ni maître ni seigneur. Je cherche un adversaire digne.",
                            "§cMusashi §f: §oVotre bourg sera bientôt assiégé. Je peux apprendre à vos gens à tenir une lame. Montrez-moi le chemin."
                    ),
                    "city_arrival", List.of(
                            "§cMusashi §f: §oUn bourg qui s'arme. Bien. Le danger approche, je le sens.",
                            "§cMusashi §f: §oIl me faut un sol plat, une cible, et du silence."
                    ),
                    "building_assign", List.of(
                            "§cMusashi §f: §oUn dojo. Le sol est droit, la cible est juste.",
                            "§cMusashi §f: §oCommençons. Apportez-moi du fer : vos lames viendront de là."
                    ),
                    "building_assign_unavailable", List.of(
                            "§cMusashi §f: §oVos lames sont lourdes et vos gestes sont lents.",
                            "§cMusashi §f: §oIci, je n'ai rien à enseigner."
                    ),
                    "mod_missing", List.of(
                            "§cMusashi §f: §oVos lames sont lourdes, vos gestes sont lents, et vous ne savez ni parer ni esquiver.",
                            "§cMusashi §f: §oIci, je n'ai rien à enseigner. Je médite."
                    ),
                    "level_up", List.of(
                            "§cMusashi §f: §oVotre regard a changé. Vous êtes prêt pour de meilleures lames."
                    )
            ),
            PersonalQuestCatalog.musashi(),
            "epicfight"
    ),

    // Chapitre V — Handcrafted et Furniture (mobilier) : les deux mods requis
    GEPPETTO(
            "citycore_geppetto",
            "§6Geppetto",
            "Maître ébéniste",
            "geppetto",
            "ewogICJ0aW1lc3RhbXAiIDogMTYyMTMwMDU0MTAxMCwKICAicHJvZmlsZUlkIiA6ICI5OTdjZjFlMmY1NGQ0YzEyOWY2ZjU5ZTVlNjU1YjZmNyIsCiAgInByb2ZpbGVOYW1lIiA6ICJpbzEyIiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlLzRhNzQyZmIyMzhmNjMwNThmNGFhYjUyOTNkODZhOWY0ZjkxYTY5NzI3NDAxMTczZDlmNjAyM2NkZDg1ODRhZDAiCiAgICB9CiAgfQp9",
            "dGaUPTGp9lC/uyyA6Ejby0fBfkvb4smyeg7tXF8qNhrA2ISCkkWmABq4GTW8H1bfejx+vhqmru0L8VmXdvdjXDcdLhLYktYufgayg1feYU3AjFn6rneTAWKbjYRmIf83gW/xUEf1pFNQVN+xy6wQC0VPf6zFE8KD/qmJSkz+480oH4RX0rIg9FsKGG0l3/0zOtPmHZRnrMdYwyhIfrcMrTaEdvvrzoaN/2T+vMYcqf7t5mJye8Z9ScwonqBU33taCKVX9C9+AvdlYLXoAMHAonMskjAp1Okt+gcYz7nFGZ7HOS5f/gxF+EYQ4Skhp7QUvTmy5SPxsn04ukEzzkV98Mu30/cyx+ISobhYGKBShrv7UkgJ/pOrW6SsKxgcjE42J3J2ixrcxE3KfZ1QEGMtmrNUxhRwSD8qg2AbSvMRbUzv78uXgoWp74zdhYn9J2VTHm8vsILfqD7AtnhiQBgKKrIpdEYhSkTLyScxQkwzQdL/rjiplkADI5clbDBsMB5Cs1Epylway8Vr9lSLJVXfx/jIx9LDgKMJ2zEqeORlBNcjau9nMcSFUV+SzQo/8RtB1Ow/upLSwbEHFTLkULHDaroqcJL8Qf1gBmK9tg81cWCRKW1EGAlyiL6ft0sIDqV8BLZxkExLnFMa390QoYQ9f22qi6Ys3rpECHc5IkGqA3w=",
            List.of(
                    "Il a taillé un pantin dans une",
                    "bûche qui parlait. Depuis, il",
                    "cherche son fils de bois sur",
                    "toutes les routes."
            ),
            Map.of(
                    "first_meeting", List.of(
                            "§6Geppetto §f: §oVous n'auriez pas vu un petit pantin au nez un peu... long ?",
                            "§6Geppetto §f: §oUne ville qui se rebâtit a besoin de chaises, de tables, de lits. Je sais faire tout cela.",
                            "§6Geppetto §f: §oEmmenez-moi. Si mon garçon passe par votre ville, je veux y être."
                    ),
                    "city_arrival", List.of(
                            "§6Geppetto §f: §oQue de maisons vides ! Elles ont besoin de chaises, de tables, de lits...",
                            "§6Geppetto §f: §oUn établi, de bons copeaux, et je vous meuble toute la ville."
                    ),
                    "building_assign", List.of(
                            "§6Geppetto §f: §oUn établi, des copeaux, l'odeur du bois. Je suis chez moi.",
                            "§6Geppetto §f: §oApportez-moi du bon bois : je vous ferai des meubles dignes d'un roi."
                    ),
                    "building_assign_unavailable", List.of(
                            "§6Geppetto §f: §oMes gabarits... mes outils fins... tout est resté en route.",
                            "§6Geppetto §f: §oJe ne peux faire que des bancs grossiers."
                    ),
                    "mod_missing", List.of(
                            "§6Geppetto §f: §oSans mes gabarits ni mes outils fins, je ne fais que des bancs grossiers.",
                            "§6Geppetto §f: §oRien que je puisse vous vendre, hélas. Je guette la route, au cas où mon garçon passerait."
                    ),
                    "level_up", List.of(
                            "§6Geppetto §f: §oMes mains retrouvent leur adresse. Regardez ce que je sais faire, maintenant."
                    )
            ),
            PersonalQuestCatalog.geppetto(),
            "handcrafted,furniture"
    ),

    // Chapitre VII — Small Ships (navires à construire et à piloter, canons)
    RAGNAR(
            "citycore_ragnar",
            "§3Ragnar",
            "Roi des mers du Nord",
            "ragnar",
            "ewogICJ0aW1lc3RhbXAiIDogMTYyMTQwMDk1NjUzNiwKICAicHJvZmlsZUlkIiA6ICJmMGIzYmRkMjEwNDg0Y2VlYjZhNTQyYmZiOGEyNTdiMiIsCiAgInByb2ZpbGVOYW1lIiA6ICJBbm9uaW1ZVFQiLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOTUwOGRhMWY0YWUzMDcyNjRkN2E3NDRiNDA3MmY2MzdlMjljNjdmM2E4MDgyNjZiYWM5Y2IyMTAyNmJlNTI3ZiIKICAgIH0KICB9Cn0=",
            "Lg7egxyHudUWMpIEZIb7V6TtkmNF11Sr5oM1l85vXp+V1J1g9pkM+GM4iio/W6kEy+x/k/oM3pru7jQWXAgWFX2JWNtuvA0CtD69nlOnuLUxQYOwXCrAZe/iiEbp20emr1u8g+u0hCizaL9MZ8ZyJxeN7/ZBZT4FqtLdm+mzhqyZ0k2+vpyhuLsbG6EyDMfn/rKW3NlG9b/xQKekCG2r+J2s9Ep7FllcionhlEKXahXZ/ymI4ru6tjdLglpIeYQgyPD6o3fgUhQDudljBhm3cNBGSaQ0P2M4IM9uhqDnenGWbo4wm7RhKz5VUijy/FdmPFTKOy6MsE9VWAdS/Mosq6jvjXEXRCkzwhzMN2ZNUXY1H4XG2wWD+mJ4C0e2aZhxIzUjMH87ptG9N8RN9cd5kLxBULsSsQ8DUsWVv5iDXOCfIrGXzZ3124g3oyqTWgLA6QtfEgcKcOs1Pf3KTZMHWjfl5UYeQgvIbWT/YVE7y1TN3sZJGqU0kyXijlFJg5uv71W4yP+l+F1H7QRatBJykJ8ezmgqsKesBjxstOWJrAwbY4jV6Fw3TJWRHv8iml2RtziU3SODQpzGaIDIG4OTMt9Kvf4Ov4OeQ4awowAaF3tEMpB0avDB/fiCYVAGOfAGcklGUgIt4h1dRwKvh7hpZmmQ1h/mEJDElZgLz+94TJk=",
            List.of(
                    "Fermier devenu roi, il a navigué",
                    "vers l'ouest quand tous disaient",
                    "qu'il n'y avait rien.",
                    "Il y avait quelque chose."
            ),
            Map.of(
                    "first_meeting", List.of(
                            "§3Ragnar §f: §oLes dieux m'ont mené jusqu'ici. Et une abbaye, paraît-il.",
                            "§3Ragnar §f: §oCalmez-vous. Je préfère commercer. Enfin... aujourd'hui.",
                            "§3Ragnar §f: §oConstruisez-moi un chantier et je vous apprendrai à bâtir des navires dignes de ce nom."
                    ),
                    "city_arrival", List.of(
                            "§3Ragnar §f: §oUne abbaye, des greniers pleins... Hm. Je suis venu commercer, j'ai dit.",
                            "§3Ragnar §f: §oIl me faut un chantier au bord de l'eau. Mes navires ne naîtront pas dans un champ."
                    ),
                    "building_assign", List.of(
                            "§3Ragnar §f: §oUn chantier, de l'épicéa, de la laine pour les voiles.",
                            "§3Ragnar §f: §oAvec ça, Valmont aura une flotte."
                    ),
                    "building_assign_unavailable", List.of(
                            "§3Ragnar §f: §oVos barques ne tiendraient pas une vague.",
                            "§3Ragnar §f: §oSans vrais navires, je n'ai rien à vendre."
                    ),
                    "mod_missing", List.of(
                            "§3Ragnar §f: §oVos barques ne tiendraient pas une vague, et personne ici ne sait bâtir une coque.",
                            "§3Ragnar §f: §oSans vrais navires, je n'ai rien à vendre. Je garde l'abbaye à l'œil... pour la protéger."
                    ),
                    "level_up", List.of(
                            "§3Ragnar §f: §oLes dieux sourient à Valmont ! Mon chantier peut bâtir de plus grands navires."
                    )
            ),
            PersonalQuestCatalog.ragnar(),
            "smallships"
    ),

    // Chapitre VIII — Valarian Conquest (factions, chevaliers, engins de siège)
    JEANNE(
            "citycore_jeanne",
            "§eJeanne d'Arc",
            "Cheffe de guerre",
            "jeanne",
            "ewogICJ0aW1lc3RhbXAiIDogMTcxODA1NTM5NjI2MCwKICAicHJvZmlsZUlkIiA6ICJhN2E4YWY1NTk0ZGQ0OTJjYWViNjc1NzUzMzRlNmM0OCIsCiAgInByb2ZpbGVOYW1lIiA6ICJpdHNUcmFjZXJvdXRlIiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlLzJkNTBlNmEwMDBlYTFiYjdhODJjZDk4YTIxNjI4YjdlOWI1NmQ4MTVlMTU2YzdjNTExMWUxYjgyMGUyODg1MyIsCiAgICAgICJtZXRhZGF0YSIgOiB7CiAgICAgICAgIm1vZGVsIiA6ICJzbGltIgogICAgICB9CiAgICB9CiAgfQp9",
            "Bfn7dkQWbt/R0GWrdGytNZ3RrQ/5/OaPV3DFemevkeG5cdsIInjPH10OELAOeCRXGKxPxj91gY78TMj3bqIg5mu+GtN8e6L1H0n+rAg6romOv0N9Y+iSXJMi9oJfgvfYk1X0bAa4XSMf8YP6xfluht/gYA/fGAD6fBrsbWsrxHQebY5WSoLUEursiAC7OykbEpNBIs3hFUHDxSeswve0krMk9tw0kYucnFlNDCrNi3+xTZ/9zCXpgKKW9yRUMJd35kNfMiSpXEUK8/YkjGbJv/+t8SSNNbRk8R/rzTONt/07Qi2t6Qy9qPt7s8NGG6GMuMCDnHBX9bMHQhEgWtvtc9Im/rTbmspPJLLyDJbrmLu5ypZABBBFrmKZejupjhFC5qRFS+AXt9FwbpxOfGMaNgrQRYiM0PgrdMDLmPrPkaIvj0jYSqfi8FX7YYhk8wlL+NrgPrf+bNhAh1O8/fJc93TcDh7wZcQjSWgDIGksGbhlSzSNWrG1rmJY+FuPfwTlQ/dlcjmF1HDjvf0mRdzdtJyRot90Ue3LFefT9DYDkCMMHWnRwqJeqDiYsxtR8fSV/dMxVYM6v+4roLf8TgEuJC3y0y9Tx9r233Pof4f+TJKg4QQbMsEyIMq6MI+SbTUABfmcB73dAZzCXiZXENkWdJabPiUgR8ia8aQO8sJmC8A=",
            List.of(
                    "Bergère devenue cheffe de guerre,",
                    "elle a délivré une ville assiégée",
                    "et fait sacrer un roi.",
                    "Elle n'a jamais reculé."
            ),
            Map.of(
                    "first_meeting", List.of(
                            "§eJeanne §f: §oOn m'a dit qu'une cité se relevait ici, et qu'on l'assiégeait. Me voici.",
                            "§eJeanne §f: §oBâtissez-moi un quartier général : je ferai de vos gardes une armée."
                    ),
                    "city_arrival", List.of(
                            "§eJeanne §f: §oDes murs, une garde, des bannières : Valmont a du cœur.",
                            "§eJeanne §f: §oDonnez-moi un quartier général. Les assiégeants vont apprendre à nous craindre."
                    ),
                    "building_assign", List.of(
                            "§eJeanne §f: §oDes cartes, des bannières, une enclume. Valmont ne tombera pas.",
                            "§eJeanne §f: §oApportez-moi du fer : je ferai de vos gardes une armée."
                    ),
                    "building_assign_unavailable", List.of(
                            "§eJeanne §f: §oNi catapultes, ni chevaliers, ni royaumes en guerre... Le pays est en paix.",
                            "§eJeanne §f: §oJe reste, mais je n'ai rien à vous vendre."
                    ),
                    "mod_missing", List.of(
                            "§eJeanne §f: §oLes royaumes ne se battent plus, et personne ne fabrique de catapultes.",
                            "§eJeanne §f: §oJe veille sur Valmont. Mais je n'ai rien à vous vendre."
                    ),
                    "level_up", List.of(
                            "§eJeanne §f: §oVos gardes tiennent la ligne. Je peux armer de vrais chevaliers, maintenant."
                    )
            ),
            PersonalQuestCatalog.jeanne(),
            "valarian_conquest"
    ),

    // Chapitre X — Mowzie's Mobs (créatures et boss légendaires)
    HERCULE(
            "citycore_hercule",
            "§dHercule",
            "Demi-dieu",
            "hercule",
            "ewogICJ0aW1lc3RhbXAiIDogMTYyMjUyMDA0Nzg0MSwKICAicHJvZmlsZUlkIiA6ICJkZGVkNTZlMWVmOGI0MGZlOGFkMTYyOTIwZjdhZWNkYSIsCiAgInByb2ZpbGVOYW1lIiA6ICJEaXNjb3JkQXBwIiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlLzlkYmFkYzdmOGViMTg4ODhlZmNkYWU3ZTBkYjRjZjMwZmYyOGJkNTg4NTM0ZjRiYjA0MmQ1YmM2YmRjNGRmZWYiCiAgICB9CiAgfQp9",
            "QhR/bKHq2tO+Y20QESmjnowP2m2TWX6RSz2PLFMa7sxoN7m8bd81ekCNJLrxmThb6Cu9kQSK0v9m6pjVT3qpRZuOPlP+1+vpperGkOCSuhqX8KlRIvbZxW4ex2phdmbA5gMeNFalWBj5l4Ts+qX1rJbA/CB5cPuvu5J9s1Vfmbl3U+ApPrOq2Hk1KTFc4M9Nh2TcDidlKgCD0Pqnvf9ZmBZsfAM1ha+xisWZ7ibSpeMviS+M4cETvMKibkPmxekvCRTzz+tc547RzGtx8lVJSXSznrGfTGis3CqXUuXI489jOK9LRaQLWwQdumpQQss20/9Ihe5y8HBgrZhld/aKh6uVreXqmarp3j3M7gqOzzZ5sXbcSS9h8Mxjcwr+M25nvZW0DySI6guefkXkVYjvuWZX+mFtGLU/a8FYZRXzX4uEYp4A1IVUvmxX69iZ8Dd2UPuJtnaz/hf2bObHAkvCRqw0v22iW4Vqmm8KwBxW5ZsRN52u7AVtVkTR1mEyIrLaaFdBdtmnLCxStXMMw/ROcuU7HSWA/X1KJTbCU6q7RHVpZf3vfCcwY4WGuXsS5ILKWaSJ6dJ2gJNHqqIc0GYt/9jDs54YQdhjfCpqUgbADOY4wYQDCdg6qCWY3tlZQWc6RUxVatYKw6IcZCWVEw/dN+dHTi0yNTJQmjawKVEPQvA=",
            List.of(
                    "Fils d'un dieu, il a accompli",
                    "douze travaux pour expier une",
                    "faute. Il en cherche",
                    "un treizième."
            ),
            Map.of(
                    "first_meeting", List.of(
                            "§dHercule §f: §oOn m'a parlé d'une cité qui a vaincu un dragon. Impressionnant. Pour des mortels.",
                            "§dHercule §f: §oMontrez-moi vos monstres. Les plus gros."
                    ),
                    "city_arrival", List.of(
                            "§dHercule §f: §oC'est donc ça, la cité qui a vaincu un dragon ? Elle est... petite.",
                            "§dHercule §f: §oConstruisez-moi une palestre. Je m'entraînerai en attendant des monstres dignes de ce nom."
                    ),
                    "building_assign", List.of(
                            "§dHercule §f: §oUne palestre !",
                            "§dHercule §f: §oRapportez-moi les trophées de vos chasses : je saurai qui est digne."
                    ),
                    "building_assign_unavailable", List.of(
                            "§dHercule §f: §oDes zombies, des araignées... Ce ne sont pas des monstres,",
                            "§dHercule §f: §oce sont des échauffements."
                    ),
                    "mod_missing", List.of(
                            "§dHercule §f: §oDes zombies, des araignées... Ce ne sont pas des monstres, ce sont des échauffements.",
                            "§dHercule §f: §oRevenez quand vous aurez de vrais monstres à me montrer."
                    ),
                    "level_up", List.of(
                            "§dHercule §f: §oPas mal, pour des mortels. Voici des armes dignes d'un héros."
                    )
            ),
            PersonalQuestCatalog.hercule(),
            "mowziesmobs"
    );


    /* =========================
       CHAMPS
       ========================= */

    public final String tag;
    public final String displayName;
    public final String function;
    /** Identifiant du skin ET clé de configuration (section du PNJ dans config.yml). */
    public final String skinId;
    public final String skinValue;
    public final String skinSignature;
    /** Biographie courte, affichée dans les menus du PNJ. */
    public final List<String> backstory;
    public final Map<String, List<String>> dialogues;
    public final Map<Integer, List<NPCPersonalQuest>> personalQuestsByLevel;
    /** Mods dont dépend l'habitant (identifiants, ex : "dmr") ; vide pour le jeu de base. */
    public final List<String> requiredMods;
    /** Mods qui ajoutent du contenu à l'habitant sans lui être nécessaires (Gandalf, Robin…). */
    public final List<String> optionalMods;

    /* =========================
       CONSTRUCTEUR
       ========================= */

    CityNPC(String tag, String displayName, String function,
            String skinId, String skinValue, String skinSignature,
            List<String> backstory,
            Map<String, List<String>> dialogues,
            Map<Integer, List<NPCPersonalQuest>> personalQuestsByLevel) {
        this(tag, displayName, function, skinId, skinValue, skinSignature, backstory, dialogues,
                personalQuestsByLevel, null);
    }

    CityNPC(String tag, String displayName, String function,
            String skinId, String skinValue, String skinSignature,
            List<String> backstory,
            Map<String, List<String>> dialogues,
            Map<Integer, List<NPCPersonalQuest>> personalQuestsByLevel,
            String mods) {
        List<String> required = new java.util.ArrayList<>(), optional = new java.util.ArrayList<>();
        if (mods != null) {
            for (String m : mods.split(",")) {
                String id = m.trim();
                if (id.startsWith("+")) optional.add(id.substring(1).trim());
                else if (!id.isEmpty()) required.add(id);
            }
        }
        this.requiredMods          = List.copyOf(required);
        this.optionalMods          = List.copyOf(optional);
        this.tag                   = tag;
        this.displayName           = displayName;
        this.function              = function;
        this.skinId                = skinId;
        this.skinValue             = skinValue;
        this.skinSignature         = skinSignature;
        this.backstory             = backstory;
        this.dialogues             = dialogues;
        this.personalQuestsByLevel = personalQuestsByLevel;
    }

    /* =========================
       API QUÊTES PERSO
       ========================= */

    public List<NPCPersonalQuest> getPersonalQuestsForLevel(int npcLevel) {
        return personalQuestsByLevel.entrySet().stream()
                .filter(e -> e.getKey() <= npcLevel)
                .sorted(Map.Entry.comparingByKey())
                .flatMap(e -> e.getValue().stream())
                .filter(NPCPersonalQuest::offered)
                .toList();
    }

    /** Récit et rang de l'habitant qui le débloque. */
    public record RankedQuest(int level, NPCPersonalQuest quest) {}

    /**
     * Tous les récits, dans l'ordre des rangs (verrouillés compris). Les récits débloqués sont
     * les premiers de la liste, dans le même ordre que {@link #getPersonalQuestsForLevel(int)}.
     */
    public List<RankedQuest> getAllPersonalQuests() {
        return personalQuestsByLevel.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .flatMap(e -> e.getValue().stream().filter(NPCPersonalQuest::offered)
                        .map(q -> new RankedQuest(e.getKey(), q)))
                .toList();
    }

    /** Texte rendu illisible (récit verrouillé) : lettres et chiffres remplacés par « ? ». */
    public static String obfuscate(String text) {
        StringBuilder sb = new StringBuilder(text.length());
        text.codePoints().forEach(cp -> sb.appendCodePoint(Character.isLetterOrDigit(cp) ? '?' : cp));
        return sb.toString();
    }

    public boolean hasPersonalQuests() {
        return !personalQuestsByLevel.isEmpty();
    }

    /** Récit par identifiant (même réservé à un mod absent : une progression déjà enregistrée se relit). */
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
}
