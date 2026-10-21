# Intégration des mods (serveur hybride)

CityCore tourne aussi sur un serveur **hybride** qui charge à la fois des mods NeoForge et des
plugins Bukkit/Paper (testé pour Youer 1.21.1). Certains habitants peuvent alors s'appuyer sur
le contenu d'un mod : ses objets, ses créatures, ses succès, ses structures.

Les personnages qui en profitent :

| Personnage | Chapitre | Mod(s) (identifiant) | Lien |
|---|---|---|---|
| Robinson Crusoé | I | Tough As Nails (`toughasnails`) | requis |
| Capitaine Achab | II | Tide (`tide`) | requis |
| Jack Sparrow | III | Tax' Ocean Villager (`taxov`) | requis |
| Obélix | IV | Wilder Nature (`wildernature`) **et** Farmer's Delight (`farmersdelight`) | les deux requis |
| Miyamoto Musashi | IV | Epic Fight (`epicfight`) | requis |
| Gambrinus | IV | Let's Do Brewery (`brewery`, qui installe Farm & Charm `farm_and_charm`) | requis |
| Geppetto | V | Handcrafted (`handcrafted`) **et** Furniture (`furniture`) | les deux requis |
| Gandalf | VI | Iron's Spells 'n Spellbooks (`irons_spellbooks`) | facultatif : magie vanilla sans lui |
| Ragnar | VII | Small Ships (`smallships`) | requis |
| Jeanne d'Arc | VIII | Valarian Conquest (`valarian_conquest`, soldats sous `ethans_custom_mod`) | requis |
| Hercule | X | Mowzie's Mobs (`mowziesmobs`) | requis |
| Harold | X | Dragon Mounts Remastered (`dmr`) | requis |
| Robin des Bois | II | Wilder Nature (`wildernature`) | facultatif : cerfs, venaison, tableau des primes |
| Indiana Jones | V | Mo' Structures (`mostructures`), ATi Structures (`ati_structures`) | facultatifs : récits dans leurs structures |

Versions analysées (jars du serveur, 20/10/2026) : Tough As Nails 10.1.0.13, Tide 2.0.3, Wilder
Nature 1.1.8, Farmer's Delight 1.2.10, Epic Fight 21.17.3.1, Handcrafted 4.0.3, Furniture 1.1.10,
Iron's Spells 3.15.4, Small Ships 2.0.0-b2.1, Valarian Conquest 4.2, Mowzie's Mobs 1.7.5,
Mo' Structures 1.6.0, ATi Structures 1.4.4, Dragon Mounts Remastered 1.9.2, Tax' Ocean Villager 4.0.1 ;
le 21/10/2026, Let's Do Brewery 2.1.13 et Farm & Charm 1.1.29 (identifiants lus dans leurs registres).
Chaque identifiant d'objet, de bloc, de créature et de structure de `config.yml` et des récits a été
vérifié dans ces jars.

## Principe

| Situation | Recrutement (quête de la cité) | Installation dans un bâtiment | Menu (vente, boutique, quêtes, récits) |
|---|---|---|---|
| Mod requis présent | oui | oui | oui |
| Mod requis absent (ou l'un des deux) | oui | oui | non : un dialogue l'explique |
| Mod facultatif présent | oui | oui | oui, avec le contenu du mod en plus |
| Mod facultatif absent | oui | oui | oui, contenu de base |

Sans son mod, un clic sur le personnage installé joue son dialogue « mod_missing » (lettre par
lettre), puis une ligne grise : « (Le mod Tide n'est pas installé sur ce serveur : Capitaine
Achab ne peut ni commercer ni confier de quêtes.) » ; avec deux mods requis : « (Les mods Wilder
Nature et Farmer's Delight ne sont pas tous les deux installés sur ce serveur : Obélix…) ». Ses
quêtes n'apparaissent pas dans le panneau des quêtes, et ses succès introuvables ne sont pas
signalés dans la console. À l'installation, le dialogue « building_assign_unavailable » remplace
« building_assign » ; un personnage enrichi par un mod facultatif présent joue
« building_assign_mod » s'il en a un (Gandalf).

## Détection et contrôle

Au démarrage, chaque mod est jugé **présent** si l'une de ses traces est visible :

1. un de ses objets témoins (`tide:brook_trout`, `epicfight:uchigatana`…) ;
2. un de ses succès (`dmr:main`, `irons_spellbooks:irons_spellbooks/root`…) ;
3. une de ses structures (Mo' Structures et ATi Structures, qui n'ajoutent ni objet ni succès :
   `mostructures:sphinx`, `ati_structures:ancient_temple`) ;
4. la liste des mods du chargeur (NeoForge, Forge ou Fabric), lue par réflexion.

La console l'indique avec les personnages concernés, par exemple :
`🧩 Mod Tide (tide) présent — objet tide:brook_trout : Capitaine Achab.`

**`/city mods`** (permission `citycore.admin`, console aussi) fait le bilan : chaque mod connu,
présent ou absent et comment, puis chaque personnage concerné et ce qu'il propose sur ce serveur
(« boutique, quêtes et récits », « recrutable, mais ni boutique ni quêtes », « contenu en plus »).

Pour tester l'absence sans désinstaller le mod, `config.yml` :

```yaml
mod-integrations:
  dmr: off     # auto = détection | on = forcer présent | off = forcer absent
  tide: auto   # Capitaine Achab
```

**Tester les menus sans atelier** (permission `citycore.admin.pnj`) :

| Commande | Effet |
|---|---|
| `/city npc <id> activer` | un clic droit d'administrateur sur le personnage ouvre son menu complet (vente, entrepôt, boutique, quêtes, récits, gestion), quel que soit son état, sans rencontre ni suivi vers la ville |
| `/city npc <id> menu` | ouvre ce menu à distance (en jeu), même si le personnage n'est pas encore apparu ; active le test pour la session |
| `/city npc <id> desactiver` | revient au comportement normal |
| `/city npc tous activer` / `desactiver` | tous les personnages d'un coup |

Rien n'est enregistré (`TestActivation`, en mémoire) : l'état réel ne change pas, donc pas d'étape
de recrutement validée, ni emplois, ni besoins ; les joueurs sans la permission gardent le menu
normal ; tout s'efface au redémarrage. Les menus montrent « Aucun atelier » là où il en faut un.
Avec un mod requis absent, le dialogue d'explication remplace toujours le menu. `/city npc <id>
levelUp` monte le rang pour voir toute la boutique.

## Objets de mods dans config.yml

Sur Youer, chaque objet de mod devient un `Material` Bukkit : `dmr:dragon_egg` s'appelle
`DMR_DRAGON_EGG` (« : » devient « _ », les autres signes disparaissent), et garde sa clé
d'origine. Les créatures des mods deviennent de même des `EntityType` (`MOWZIESMOBS_NAGA`,
vérifié dans `NeoForgeInjectBukkit` de Youer). Dans `config.yml`, on écrit simplement la clé du
mod entre guillemets :

```yaml
shop:
  5:
    - {material: "dmr:blank_egg", price: 600, quantity: 1, supply: {max: 1, restock-minutes: 1440}}
```

Valable pour `sell-prices`, `shop`, `requires`, les `item-pool` et `entity-pool` des quêtes, et
les blocs des ateliers (`batiments.ateliers`). Si le mod est absent, ces lignes sont ignorées sans
alerte (une ligne récapitulative dans la console) ; si le mod est **présent** mais l'objet
introuvable (mod mis à jour, faute de frappe), un avertissement le nomme et l'article est ignoré.

Écritures supplémentaires (contenu des légendes) :

| Écriture | Sens | Exemple |
|---|---|---|
| `"#minecraft:wool"` | tag d'objets (sinon de blocs) | rachat de toutes les laines, quête « Laine (toutes couleurs) » |
| `"handcrafted:*_chair"` | motif sur la clé (`*` = n'importe quoi) | quête « Chaises (toutes essences) » |
| `"a,b,-c"` | liste ; `-` retire de l'ensemble | `"valarian_conquest:*_citizenship,-valarian_conquest:nomad_citizenship"` |
| `nom: "…"` | libellé d'un objectif de quête qui accepte une famille | `{material: "#minecraft:planks", nom: "Planches (toutes essences)", base-amount: 64}` |
| `entity: "a,b,c"` | objectif de chasse qui accepte plusieurs créatures | les trois guerriers Umvuthana d'Hercule |
| `avec-mod: x` / `sans-mod: x` | article, rachat, objectif ou bloc d'atelier réservé à un mod présent / absent | Gandalf : contenu vanilla `sans-mod: irons_spellbooks` |
| `components: '[…]'` | données d'objet (format des composants de Minecraft), vérifiées au démarrage | parchemin de sort, livre de compétence, cuir teint |

Un groupe dans `sell-prices` vaut pour chacun de ses objets (même lot, même prix, même capacité) ;
une clé simple déclarée à part l'emporte. Dans `requires`, le préfixe avant « : » n'est un
fournisseur que s'il nomme un personnage (`thorvald:IRON_INGOT`) ; sinon c'est un objet de mod
(`farmersdelight:ham`).

Données de mods vendues (format lu dans les jars) :

- **Parchemins d'Iron's Spells** : composant `irons_spellbooks:spell_container`
  `{maxSpells:1,spellWheel:0b,mustEquip:0b,data:[{id:"irons_spellbooks:firebolt",index:0,level:1,locked:1b}]}`
  (le format de `ISpellContainer.createScrollContainer`) ;
- **Livres de compétence d'Epic Fight** : composant `epicfight:skill="epicfight:parrying"` (registre
  des compétences ; « Parer » et « Maître des épées » chez Musashi).

**Noms affichés** : `src/main/resources/integration/mod-labels.yml` (noms français choisis), puis
les fichiers de langue des mods (fr_fr, sinon en_us, lus dans les jars du dossier `mods/`), sinon
le chemin de la clé. Les créatures aussi (« Tuer 2 Pyromancien »).

Une quête déjà acceptée qui demande un objet ou une créature d'un mod désinstallé depuis est
écartée proprement (avertissement d'une ligne, pas d'erreur). Un récit réservé à un mod désinstallé
depuis disparaît des menus et se met en pause.

## Les légendes liées aux mods

Conception : `F:\Minecraft Serveur doc\Contexte\legendes-mods.md` (brief du 20/10/2026). Toutes
suivent le modèle de Jack Sparrow : légende ★ (quête bonus de la cité, jamais bloquante, ligne
« Les légendes de passage » de l'écran des progrès), atelier aux blocs requis **vanilla** (les blocs
du mod ne comptent qu'en bonus), boutique en 5 rangs, quêtes principale et spéciale, 5 récits, rangs
partagés par la ville. Skins intégrés au plugin (MineSkin).

| Légende | Où (rayon) | Atelier proposé | Récits |
|---|---|---|---|
| Robinson Crusoé | plage, rivage pierreux (250–500) | `Cabane_du_naufrage` | eau purifiée ; désert à midi avec une gourde ; échange avec un villageois ; nuit enneigée en tenue de laine complète ; 28 fioles d'eau purifiée (elles ne s'empilent pas : les 64 du brief ne tiennent pas dans le sac) |
| Capitaine Achab | rivière, plage, rivage (300–600) | `Baleiniere` | rapporter une truite de ruisseau, une anguille des cavernes, un cœlacanthe, un espadon de feu, le Deep Blue |
| Obélix | forêt, forêt sombre, taïga (300–600) | `Hutte_gauloise` | 5 sangliers ; trophée de cerf ; jambon laqué (festin) ; 3 ravageurs ; trophée de bison |
| Miyamoto Musashi | bambous, cerisaie, collines, prairie (350–700) | `Dojo` | monstres tués bokken en main, tachi en main ; 20 assaillants pendant un siège ; ravageur, wither squelettes à l'uchigatana |
| Gambrinus | taïgas, plaines (325–650) | `Brasserie` | une bière ; 4 plants de houblon sauvage, et une tournée le soir dans la taverne une bière en poche ; tente de la fête de la bière ; JoJannik, Lilitu Single Malt et MaggoAllan ; Bière noire |
| Geppetto | forêts, prairie (300–600) | `Echoppe_d_ebeniste` | 16 bûches de cerisier ; 4 chaises ; habitation meublée (6 blocs de meubles) ; horloge comtoise ; porte-armure + bûche de cerisier renommée |
| Gandalf | plaines, tournesols, forêt (350–700) | `Tour_du_mage` | avec le mod : pyromancien, cryomancien, nécromancien, Roi Mort, Écho de Tyros ; sans : récits vanilla |
| Ragnar | plages enneigées, rivages (400–800) | `Chantier_naval` | à bord d'un navire ; en drakkar à plus de 1 000 blocs ; 5 monstres aux boulets de canon ; océan gelé de nuit ; grand gardien depuis une galère ou un brick |
| Jeanne d'Arc | plaines, tournesols, forêt (400–800) | `Quartier_general` | 10 pillards ; parchemin de citoyenneté ; troll ; bélier ou catapulte ; cathédrale coiffée du grand heaume |
| Hercule | savanes, collines (500–1 000) | `Palestre` | Foliaath ; 3 Umvuthana ; Ferrous Wroughtnaut ; Frostmaw ; Umvuthi |

Écarts avec le brief, et pourquoi :

- **Prix des ressources vanilla** ramenés aux prix de référence des artisans (cuir 1 coin pièce,
  laine et ficelle 0,5 et 0,25, fer 1,5, or 2,5, bambou 0,125…) : les prix proposés dépassaient ceux
  du jeu et ouvraient des arbitrages (Indiana vend cactus et bambou ; Léon vend des vitres, retirées
  des rachats de Geppetto). Poissons de Tide revus à la baisse (ce sont les prises courantes des
  rivières avec le mod). Ferraille de mithril rachetée 18 : un lingot vendu 120 se fond en 4 ferrailles.
- **Small Ships 2.0.0-b2.1** n'enregistre que cogues, bricks, galères et drakkars (vérifié dans
  `ModItems` / `ModEntityTypesImpl` ; les modèles des barques à rames, galères de guerre et dhows
  existent mais ne sont pas enregistrés) : la cogue ouvre la boutique de Ragnar, son drakkar de chêne
  noir « Le Dragon de Ragnar » la couronne, et Valhalla se joue depuis une galère ou un brick.
- **Récits ⚙️** : voir le tableau suivant ; deux replis du brief sont gardés faute de donnée lisible.

| Mécanisme du brief | État |
|---|---|
| 1. Double dépendance de mods | fait (`CityNPC` : `"wildernature,farmersdelight"`) |
| 2. Contenu facultatif lié à un mod | fait (`"+mod"`, `avec-mod` / `sans-mod`, récits `onlyWith` / `onlyWithout`) |
| 3. Tuer en tenant une arme précise | fait (main principale, motif `epicfight:*_tachi`) |
| 4. Tuer pendant un siège | fait (`SiegeManager.isSiegeEnemy` : monstre de vague ou soldat ennemi) |
| 5. Être à bord d'un navire d'un type donné | fait (le joueur est passager de l'entité `smallships:*`, `Ship` étend `Boat`) |
| 6. Tuer avec un projectile donné | fait (dernier coup = boulet `AbstractCannonBall`, dont le tireur est propriétaire) — à vérifier |
| 7. Porter une tenue complète | fait (4 pièces de laine de TAN ; casque templier pour Jeanne) |
| 8. Se trouver dans une structure | fait (`World#getStructures`, structures des mods comprises) |
| 9. Bâtiment avec des blocs de mods | fait autrement : être dans une habitation de la ville qui contient au moins 6 blocs Handcrafted / Furniture (décompte gardé 30 s) |
| 10. Recherche d'un PNJ secondaire (Pinocchio) | repli : porte-armure + bûche de cerisier renommée à l'enclume |
| 11. Adoubement de Valarian Conquest | repli : un bâtiment de la ville nommé « Cathédrale », coiffé du grand heaume à croix d'or (l'adoubement est une variable du mod, non exposée) |
| 12. Objets portant des données de mod | fait (`components`) — parchemins et livres de compétence à vérifier en jeu |

### Gambrinus, le barman (Let's Do Brewery, 21/10/2026)

Légende ★ du chapitre IV, à côté de la taverne de Rosalie (demande du 21/10/2026, hors brief).
Le roi légendaire de la bière ; skin MineSkin « Brewer » (brasseur barbu, tablier de cuir).

- **Rachète tous les consommables du mod** (25) : les 6 bières, les 9 whiskys, la Bière noire et les
  9 plats (bretzel, pain d'épices, jarret de porc, saucisse, poulet frit, demi-poulet, purée,
  salade de pommes de terre, boulettes). Il ne rachète ni houblon, ni céréales séchées, ni levure.
- **Vend de quoi produire**, dans l'ordre de la production : houblon et cordes de Farm & Charm (le
  houblon grimpe aux cordes), levure, graines d'orge (rang 1) ; cuve de brassage en bois, silo en
  bois, graines d'avoine et de maïs, comptoir, bancs, table (2) ; cuve en cuivre (qui demande sinon
  un gabarit de garde des avant-postes), silo en cuivre, hachoir, cuisinière, saladier, grand fût
  (3) ; marmite, rôtissoire, tenues de la fête de la bière (la tenue complète protège de l'ivresse),
  buffets et desserte (4) ; cuve en netherite, bannière des brasseurs, « La chope du roi Gambrinus »
  (une bière de son stock, nommée) (5). Pas de laine à motifs : elle est dans `#minecraft:wool`,
  que Robinson rachète.
- **Prix** : un brassin dure 60 s (réglage du mod) et donne 1 à 5 boissons selon les événements
  réussis (sifflet, four, minuteur) ; une bière se rachète 1,25 coin, un whisky de la cuve en cuivre
  2,5, de la cuve en netherite 5, la Bière noire 12. Les plats tirés d'un seul ingrédient vendu
  en boutique se rachètent moins cher que lui : demi-poulet 0,5 (Rosalie vend le poulet cuit 0,875),
  purée 0,375 (pomme de terre cuite 0,625). Contrôle d'arbitrage hors ligne : aucune nouvelle alerte.
- **Réserves et machines des mods** : la marque « non revendable » des réserves suit l'artisanat,
  les fours, la forge, le tailleur de pierre et les blocs posés (`TagPropagation`), pas les machines
  des mods (silo, hachoir, marmite, cuve de brassage), dont les événements ne passent pas par
  Bukkit. Du blé pris chez Hélène peut donc finir en whisky revendable. D'où des prix qui paient
  le travail du brasseur (temps du brassin, cuves, houblon que personne ne rachète) et non le grain ou
  la viande, des capacités bornées (16 à 256), et des prix qui baissent à mesure que l'entrepôt se
  remplit. À surveiller en jeu.
- **Quêtes** : principales (bières, houblon, bretzels, pain d'épices, bière d'ortie, saucisses ou
  jarrets, whiskys de la cuve en cuivre) ; spéciales (houblon sauvage, bière de Haley, whiskys de la
  cuve en netherite, Bière noire, pillards, Booze — l'élémental de bière né d'un brassin raté ou d'une
  Bière noire lancée —, sorcières).
- **Atelier** `Brasserie` : chaudron, 4 tonneaux, fourneau (vanilla) ; en bonus les cuves de
  brassage, le comptoir et le grand fût du mod.
- **Mise à jour de config.yml** (version 29) : la section `gambrinus`, son atelier et la clé
  `mod-integrations.brewery` s'ajoutent ; fusion testée sur le `config.yml` du serveur (v27 → 29).

Nouveaux types de récits :

- **« Rapporter… »** (`NPCPersonalQuest.deliver`, déclencheur `DELIVER`) : les objets se remettent
  depuis les récits ouverts dans le menu du personnage (pas depuis le journal) ; un objet acheté à
  un PNJ ou pris dans une réserve est refusé ; s'ils sont déjà dans le sac à l'acceptation, ils sont
  remis aussitôt. Un objet peut devoir être renommé à l'enclume (Geppetto).
- Au-delà de 5 récits (Robin et Indiana avec leurs mods facultatifs), l'écran des récits passe à 4
  par page avec des flèches.

Écran des progrès (touche L) : la hauteur de chaque ligne suit le chapitre le plus fourni (trois
légendes aux chapitres IV et X) ; la bulle d'une légende nomme son ou ses mods et signale un mod requis absent.

Mise à jour de config.yml (version 27) : les sections des huit légendes, leurs ateliers et les
treize mods s'ajoutent ; `gandalf` et `robin` sont remplacées (l'ancienne version reste dans la copie
`config-ancien-v26-….yml`) ; les bonus de l'atelier de Gandalf passent aux blocs d'Iron's Spells.
Fusion testée hors serveur sur le `config.yml` du serveur de test.

## Harold

- **Place dans l'histoire** : chapitre X (Capitale, épilogue), quête bonus « Le dompteur de dragons ».
  Il n'apparaît qu'après la chute du dragon de l'End : les portails vers les îles extérieures
  sont ouverts, et Harold affirme que les autres dragons ne sont pas comme celui du Néant.
- **Où le trouver** : sur les sommets (pics enneigés, pentes neigeuses, bosquets), jusqu'à
  1 100 blocs de la ville.
- **Rachète** : morue, saumon, poisson tropical, poisson-globe (même prix que Martin),
  cuir, membranes de phantom.
- **Vend** : selles (rang 1), « le manuel du dragonnier » (livre : où trouver les œufs,
  éclosion, apprivoisement, vol), sifflets du mod noir, rouge, bleu et vert, étiquettes (rang 2),
  potion du vol plané (rang 3), « écaille de Furie » (résistance au feu, rang 4), œuf de dragon
  vierge `dmr:blank_egg` (rang 5, 1 par jour).
- **Quêtes** : principales (poisson, cuir, membranes, blocs de nid : magma, glace compactée) ;
  spéciales (chorus, carapaces de shulker, souffle du dragon, échos, **œufs de dragon du mod**,
  phantoms, endermen, shulkers des îles extérieures).
- **Récits** (quêtes personnelles, une par rang) :
  1. *Un œuf bien étrange* — succès du mod `dmr:find_egg` ;
  2. *Ce n'est pas un chien !* — `dmr:hatch_1_dragon_egg` ;
  3. *Pourquoi du poisson ?* — `dmr:tame_dragon` (le dragon s'apprivoise au poisson) ;
  4. *Au-dessus des nuages* — voler à dos de dragon au-dessus de Y = 500 (réglage `dragon.min-height`, la même altitude que la musique du dragon) ;
  5. *Les îles du bout du monde* — survoler les îles extérieures de l'End à dos de dragon.
- **Skin** : `npc-skins.harold.value` / `signature` (mineskin.org), puis redémarrage. Sans
  skin, son portrait montre une silhouette sur son décor (crépuscule sur les pics de Beurk,
  Krokmou dans le ciel). Aperçu : `docs/ui-previews/harold.png`.
- **Test rapide** (op) : `/city npc harold spawn` le fait apparaître près de vous, sans attendre
  le chapitre X ; `/city npc harold levelUp` monte son rang pour voir la boutique complète.

Contre l'arbitrage : Harold ne rachète pas les œufs (un œuf vierge acheté, posé puis cassé,
redonne un œuf de dragon) ; la récompense d'une quête « œuf » (200 coins × prime) reste
inférieure au prix de l'œuf vierge.

## Jack Sparrow et les navires du large (Tax' Ocean Villager)

Le mod génère en mer des navires (structures) : **navires pirates** (océans tempérés et chauds),
**vaisseaux de la Marine** (océans froids et profonds), **navires maudits** (pirates et marins
zombies), le **vaisseau de guerre du roi pirate illageois**, avec des villageois aux métiers du
mod (capitaine et matelots pirates, capitaine et marins de la Marine). Il ajoute aussi armures et
outils de pirate et de marin, deux disques et des « générateurs de navire ».

- **Place dans l'histoire** : chapitre III (Village), quête bonus « Une voile à l'horizon » :
  un capitaine sans navire qui guette les vaisseaux du large. Sans le mod, il se recrute et
  s'installe, puis explique qu'il n'y a pas une voile à l'horizon.
- **Récits**, un par rang :
  1. *À l'abordage !* — monter à bord d'un navire pirate (`taxov:pirate_ship`) ;
  2. *Contrebande* — commercer avec un capitaine ou un matelot pirate (métiers du mod) ;
  3. *Réquisitionner ! (terme nautique)* — hisser une bannière noire à bord d'un vaisseau de la
     Marine (`taxov:marine_ship`) ; Jack vend le « pavillon noir » ;
  4. *La malédiction de l'or aztèque* — aborder un navire maudit de nuit ;
  5. *Le roi des pirates* — terrasser le roi pirate illageois (`taxov:illager_pirate_king`).
- **Boutique** (en plus de ses trésors de la mer) : pavillon noir (rang 1), disque pirate
  (rang 2), tricorne pirate (rang 3), sabre pirate (rang 4) et un **navire pirate**
  (`taxov:ship_pirate`, l'objet du mod qui le fait apparaître) au rang 5, une fois par semaine.
- **Quêtes** : poudre à canon et lingots d'or en principales ; plastron pirate, disque de la
  Marine, pillards (l'équipage du roi pirate) en spéciales.
- **Détection technique** : « à bord » = debout sur le navire (ni dans l'eau, ni en barque, un
  bloc plein sous les pieds : une dalle ou une marche de pont compte) et soit dans la boîte d'une
  structure générée du mod (API
  `World#getStructures`), soit à moins de 12 blocs d'un poste d'équipage du mod
  (`taxov:block_pirate` / `block_crew` pour les pirates, `block_captain` / `block_sailor` pour la
  Marine). Ces postes font partie du gabarit de chaque navire : un navire posé à la main (objet
  « générateur » du mod, `/place`) est donc reconnu aussi, tant qu'ils ne sont pas cassés (les
  objets du mod posent les gabarits `taxov:pirateship`, `marineship`, `zombiepirate` et
  `zombiemarine`, vérifié dans le mod). Ces postes ne se fabriquent pas : on ne les obtient qu'en
  les cassant sur un navire. La recherche des postes a lieu au plus toutes les 1,5 s. Navire
  maudit posé à la main : postes d'équipage + villageois zombies à proximité. Le commerce passe
  par l'événement d'échange de Paper ; le roi pirate est reconnu comme Harold reconnaît son
  dragon (clé, nom Youer ou classe du mod).
- **Mise à jour de config.yml** : à la version 6, l'ancienne section `jacksparrow` est remplacée
  par la nouvelle (l'ancienne reste dans la copie `config-ancien-v5-….yml`).

## Mod compagnon (Epic Fight, JourneyMap)

Ce qu'un plugin ne peut pas faire seul (animer les PNJ pour Epic Fight, dessiner sur la carte
JourneyMap) passe par un mod serveur NeoForge construit à part : **« CityCore — Compagnon »**
(`companions/citycore-companion`, mod `citycore_companion`, jar `citycore-companion-<version>.jar`).
Il remplace les anciens `citycore-epicfight` et `citycore-journeymap`. Le plugin reste compilé
contre paper-api seul ; il parle au mod par un canal neutre :

| Module du mod | Actif si | Canal avec le plugin | Effet |
|---|---|---|---|
| Epic Fight | Epic Fight installé | étiquettes d'entités `citycore.geste.attaque` / `citycore.geste.tir` (`inhabitant.NpcGestures`) | PNJ animés : marche, course, coups, tirs |
| JourneyMap | JourneyMap installé (6.0.5 ou plus) | fichier `plugins/CityCore/carte.json` (`city.CityMapExport`) | parcelles de la ville sur la carte plein écran et la minicarte |

Chaque module est facultatif et isolé : sans son mod, aucune de ses classes n'est chargée.
Réglages du mod : `config/citycore_companion.toml` (sections `epicfight` et `journeymap`).
Construction, installation et réglages détaillés : `companions/citycore-companion/README.md`.

### Parcelles de la ville sur la carte JourneyMap

- **Ce qui s'affiche** : chaque parcelle remplie d'une teinte translucide (or de Valmont), le
  contour extérieur de la ville (côtés partagés effacés, trous conservés), le nom de la ville,
  une bulle au survol (« Valmont — 42 parcelles · 15 habitants ») et, en option, le quadrillage
  des chunks. Carte plein écran, minicarte (désactivable) et WebMap.
- **Rafraîchissement** : `/city create`, `/city claim` et `/city unclaim` passent par
  `CityManager`, qui prévient `CityMapExport` ; le fichier est réécrit au tick suivant (et revu
  toutes les 30 s pour le nom, les habitants et les réglages). Le mod le contrôle toutes les 2 s :
  la carte suit un claim en 2 à 3 s, sans reconnexion. Un agrandissement (`expand`) n'ajoute que
  des emplacements : la carte change au claim suivant.
- **Envoi aux joueurs** : API serveur de JourneyMap (superpositions envoyées par le serveur),
  5 s puis 30 s après la connexion, après un changement de dimension, à chaque changement et
  toutes les 2 min par sécurité (remplacement, jamais de doublon). Les joueurs n'ont besoin que
  de JourneyMap ; **JourneyMap doit aussi être installé sur le serveur**.
- **Réglages** (section `carte` de config.yml, relus par `/city carte recharger`) :

| Réglage | Défaut | Effet |
|---|---|---|
| `activee` | true | false : la ville disparaît des cartes |
| `nom` | "" | texte affiché sur la zone ; vide = nom de la ville |
| `couleur` | "F1CF5E" | couleur RRGGBB du remplissage et du contour |
| `remplissage` | 0.15 | opacité du remplissage de chaque parcelle |
| `bordure` | 0.9 | opacité du contour extérieur |
| `epaisseur` | 2.0 | épaisseur du contour extérieur, en pixels |
| `minicarte` | true | false : carte plein écran seulement |
| `quadrillage` | true | contour fin de chaque parcelle |
| `quadrillage-couleur` | "" | couleur du quadrillage ; vide = `couleur` |
| `quadrillage-opacite` | 0.35 | opacité du quadrillage |
| `quadrillage-epaisseur` | 1.0 | épaisseur du quadrillage, en pixels |

- **Commande** (permission `citycore.admin`, op par défaut) : `/city carte` donne l'état
  (parcelles publiées, dernière écriture, mod compagnon et JourneyMap détectés ou non) ;
  `/city carte recharger` relit la section `carte` et réécrit le fichier.
- **À tester en jeu** : ouvrir la carte (J) : zone dorée, quadrillage, nom, bulle ; minicarte
  aussi. `/city claim` puis `/city unclaim` : la carte suit en quelques secondes. Se déconnecter
  et revenir : la zone revient en 5 s. `carte.activee: false` puis `/city carte recharger` : la
  zone disparaît ; `true` : elle revient.
- **Points incertains** (non vérifiables hors jeu) : fonctionnement de JourneyMap côté serveur
  sous Youer ; nom affiché une fois par morceau pour une ville en plusieurs morceaux ; la
  détection des mods par `/city carte` (liste des mods lue par réflexion depuis le plugin).

## Ajouter un autre mod

1. Ajouter le mod à `ModIntegrations.Mod` (identifiant, nom, et au moins un témoin : objet, succès
   ou structure).
2. Ajouter sa clé à la section `mod-integrations` de `config.yml`.
3. Pour un habitant lié : dernier argument `"identifiant"` dans `CityNPC` (plusieurs séparés par
   des virgules ; `"+identifiant"` pour un mod facultatif), dialogues `mod_missing` et
   `building_assign_unavailable` (et `building_assign_mod` pour un mod facultatif).
4. Référencer ses objets et créatures par leur clé (`"mod:objet"`) dans `config.yml`, ses succès avec
   `modAdvancement("mod:succes")` dans `PersonalQuestCatalog`, ses récits réservés avec
   `.onlyWith("mod")` ; noms français dans `integration/mod-labels.yml`.
5. Vérifier les identifiants dans le jar du mod (modèles, données, classes d'enregistrement), puis
   `/city mods` et `/city eco check` sur le serveur.

## À vérifier en jeu

Ces points dépendent de Youer ou des mods et n'ont pas pu être testés hors du serveur :

- les lignes de détection dans la console au démarrage, et `/city mods` ;
- l'achat d'un sifflet ou d'un œuf vierge (objet du mod créé par le plugin) ;
- la validation des récits de vol : le type de la monture est reconnu par sa clé
  (`dmr:dragon`), son nom Youer (`DMR_DRAGON`) ou, à défaut, la classe du dragon du mod ;
- les parchemins de sort (Iron's Spells) et les livres de compétence (Epic Fight) achetés : ils
  doivent porter leur sort / leur compétence (sinon la console signale « données d'objet
  refusées » au démarrage et l'article disparaît) ;
- les récits de Ragnar : passager d'un navire de Small Ships, et crédit des morts aux boulets de
  canon (le tireur doit être le propriétaire du boulet) ;
- les créatures des mods dans les quêtes de chasse (type Youer `MOWZIESMOBS_NAGA`, etc.) et leurs
  noms affichés ;
- les familles d'objets (toutes laines, chaises…) dans les quêtes et le rachat ;
- l'atelier de Geppetto et le récit « Une maison meublée » (blocs de Handcrafted et Furniture
  dans une habitation) ;
- l'équilibre des prix des objets de mods, à comparer avec l'économie réelle des survivants.
