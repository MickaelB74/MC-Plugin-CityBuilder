# Plan « Raids » — les expéditions de Valmont

Conception arrêtée le 21/10/2026 ; **rien n'est codé**. Ce document se suffit à lui-même : chaque
phase (R0 à R10) se commande dans un nouveau prompt, par exemple :

> Lis `docs/PLAN_RAIDS.md` et code la phase R2. Respecte les règles communes, termine par la
> liste « À tester en jeu » et mets à jour le tableau d'avancement.

Documents liés : `docs/PLAN_VILLE_VIVANTE.md` (sièges, garde, besoins, calendrier),
`docs/INTEGRATION_MODS.md` (détection des mods, identifiants sous Youer),
`docs/TRAME_ET_ECONOMIE.md` (chapitres, économie).

## Avancement

| Phase | Contenu | Dépend de | Taille | État |
|---|---|---|---|---|
| R0 | Prototype technique : monde des raids, pose des structures, observation | — | petite | à faire |
| R1 | Monde, allers-retours et sécurité des joueurs | R0 | grosse | à faire |
| R2 | Catalogue des cibles et vérification des mods | R0 | moyenne | à faire |
| R3 | Écrans d'Alderic : Événements → Raid → Difficulté → Cibles → Fiche | R1, R2 | moyenne | à faire |
| R4 | L'assaut : défiance, objectifs, chrono, victoire, récompenses (niveaux I à III, terre) | R3 | grosse | à faire |
| R5 | Défenseurs ajoutés, renforts, chefs (niveaux IV à VII, terre) | R4 | moyenne à grosse | à faire |
| R6 | Villes fortifiées (villages agrandis + tours + siège du pouvoir) | R5 | moyenne | à faire |
| R7 | Abordage (navires, emplacements en mer) | R5 | moyenne | à faire |
| R8 | Repaires (maîtres, cages, souterrains) | R5 | moyenne | à faire |
| R9 | Raids du Nether et de l'End (mondes `citycore_raids_nether` et `citycore_raids_end`) | R5 | petite à moyenne | à faire |
| R10 | Finitions : dialogues, trophées, succès, sons, équilibrage, documentation joueurs | R4 à R9 | moyenne | à faire |

R6, R7, R8 et R9 sont indépendantes entre elles.

---

## En bref

Depuis le menu d'Alderic, les joueurs lancent un **raid** sur une cible choisie dans une liste :
camp, avant-poste, village, ville, fort, château, navire (**Abordage**) ou repaire (**Repaires**).
Les cibles sont les structures des mods installés (Valarian Conquest, Towns and Towers, ATi
Structures, Mo' Structures, Iron's Spells, Mowzie's Mobs, Medieval Buildings, Tax' Ocean Villager,
Supplementaries) et du jeu de base : **168 cibles** réparties en **7 niveaux de difficulté**.

Au lancement, les joueurs à moins de 10 blocs d'Alderic sont téléportés dans un **monde parallèle
réservé aux raids** (jamais autour de la ville). La cible y est posée sur un emplacement neuf **dont
le terrain correspond à la structure** (un village du désert dans le désert, un navire en mer, la
citadelle maudite dans le Nether), avec ses occupants. Les joueurs partent d'un camp à côté de la
cible ; un joueur mort y réapparaît avec tout son inventaire. Ils ont un chrono pour faire tomber la
**défiance** de l'adversaire en remplissant des objectifs ; victoire = récompense. Le raid se
relance à volonté (recharge par niveau) ; les mondes des raids sont effacés à chaque redémarrage du
serveur.

## Décisions prises

- Parcours : **Alderic → Événements → Raid → Difficulté → Cibles → Fiche → Sonner le rassemblement**.
- Départ : joueurs dans un rayon de **10 blocs** autour d'Alderic à la fin du compte à rebours.
- Lieu : un **monde parallèle** dédié, temporaire (pas une zone du monde principal).
- Chrono, objectifs, défiance adverse à renverser, récompense si victoire ; relançable.
- **7 niveaux** ; chaque niveau liste ses cibles, filtrables par type.
- Types retenus : camp, avant-poste, village, ville, fort et bastion, château, **Abordage** et
  **Repaires** (ces deux derniers dès maintenant).
- Toutes les structures des mods qui entrent dans ces types sont au catalogue (§7).
- **Terrain cohérent avec la structure** (décision du 21/10/2026) : chaque cible est posée sur un
  emplacement dont le biome fait partie de ceux où la structure apparaît naturellement (lus dans
  les jars) ; les cibles du Nether et de l'End ont leur propre monde (§2.4).
- **Mort dans le monde des raids** (décision du 21/10/2026) : l'inventaire est conservé et le
  joueur réapparaît au **camp de départ du raid, à côté de la structure** (§2.9).
- Structurify n'ajoute aucune structure (outil de réglage de la génération) : il n'apporte rien au
  catalogue, mais pourra servir à réserver certaines structures aux raids.

## Règles communes

- Plugin compilé contre **paper-api 1.21.1** seulement : aucun import NeoForge ; réflexion si besoin ;
  un module du mod compagnon (`companions/citycore-companion`) seulement si c'est inévitable.
- Les contenus de mods sont référencés par leur identifiant (`mod:chemin`) et **conditionnés à la
  détection du mod** (§3). Sur un Paper sans mods, seules les cibles du jeu de base restent.
- Les joueurs passent par les PNJ : Alderic pour lancer, le **héraut** du camp pour battre en
  retraite. `/city raid` = consultation et confort ; tests et administration sous
  `citycore.admin.raid` (déclaré dans `CityPermissions`, jamais `isOp()`). Aucun texte joueur ne
  renvoie vers une commande d'administration.
- Écrans dessinés en style Valmont (pack de ressources : glyphes dans le titre, boutons
  invisibles), avec un repli en menu classique. Ils doivent tenir à l'écran du joueur de test
  (1914×1007, échelle d'interface 4).
- Sons : au début, à la fin et aux moments clés seulement (cor du départ, chute d'une bannière,
  chute du chef, victoire, défaite). Aucune ambiance continue.
- Rien de punitif : une défaite ne coûte rien (pas de perte d'argent, d'objets ni d'habitants).
- Anti-exploitation stricte : la seule chose que rapporte un raid est sa récompense (§6.3).
- Vocabulaire : les quêtes des PNJ restent des « Quêtes ».
- Réglages dans `config.yml` avec montée de `config-version` (`ConfigUpdater`, commentaires
  conservés) ; catalogue dans un fichier à part, `raids.yml` (§8.5).
- Préférences de joueur éventuelles dans la table SQLite `player_prefs`, pas dans les PDC.
- Dialogues d'Alderic et du héraut : `TypewriterUtil`, deux lignes de barre d'action.
- Une phase = un ensemble testable seul sur Youer, avec des commandes d'administration pour
  accélérer ; pas de commit sans demande. La documentation joueurs
  (`F:\Minecraft Serveur doc`) se met à jour en R10, sans commit non demandé.

---

## 1. Parcours du joueur

### 1.1 Les écrans d'Alderic

1. **Accueil de la mairie** : un gros bouton **« Événements »** prend la place d'« Agrandir la
   ville », qui passe dans l'écran Trésor (défaut à confirmer, §11). Un badge signale un raid
   disponible.
2. **Événements** : trois tuiles.
   - **Raids** (cette fonctionnalité) ;
   - **Sièges** : consultation seulement (prochain siège du calendrier `EventCalendar`, dernier
     bilan `siege_results`) ;
   - **Fêtes** : verrouillée, « bientôt » (phase 7 de `PLAN_VILLE_VIVANTE.md`).
3. **Difficulté** : 7 tuiles (I à VII) avec le nom du niveau, le chapitre requis (cadenas), la
   condition de déblocage, le nombre de cibles disponibles avec les mods installés, les victoires
   et la recharge en cours.
4. **Cibles** du niveau : grille paginée de cartes. Onglets de filtre par type (Tous, ⛺ camp,
   🗼 avant-poste, 🏘 village, 🏙 ville, 🏰 fort, 🏯 château, ⚓ abordage, 🕸 repaire) et bouton
   **« Au hasard »**. Une carte : nom, type, mod, occupants, durée, récompense, « prise ×3 ».
5. **Fiche** : nom, description, mods, occupants, objectifs, chrono, récompense et bonus, nombre de
   joueurs **actuellement** dans le cercle autour d'Alderic, bouton **« Sonner le
   rassemblement »** (grisé, avec la raison, pendant une recharge, un raid ou un siège).

### 1.2 Rassemblement et départ

- Si aucun emplacement du bon terrain n'est prêt, la fiche affiche d'abord « Les éclaireurs
  repèrent le terrain… » avec une progression (§2.5) ; le rassemblement commence quand le terrain
  est prêt.
- Annonce à tous les joueurs connectés (chat, titre, cloche) : « Alderic lève une expédition
  contre la Commanderie du Temple : rejoignez-le (30 s) ».
- Alderic cesse de suivre ; un cercle de particules de 10 blocs l'entoure.
- Pendant les 30 s, la cible est posée et balayée (§2.6, §2.7).
- À zéro : les joueurs dans le cercle (même monde, vivants) sont enrôlés. Moins de `joueurs-min`
  → raid annulé, sans recharge.
- Position de retour de chacun enregistrée (SQLite), puis téléportation au camp, face à la cible.

### 1.3 Pendant le raid

- Barre rouge en haut de l'écran : « ⚔ Commanderie du Temple · Défiance 64 % · 11:42 » (rouge :
  le panneau de quêtes masque les barres blanches, comme pour les sièges).
- Objectifs dans le panneau de quêtes (`QuestHUD`), cochés au fur et à mesure.
- Le héraut du camp : « Battre en retraite » (le joueur rentre ; le raid continue pour les autres).
- Mort : **inventaire et expérience conservés, réapparition au camp de départ, à côté de la
  structure** (décision, §2.9) ; pas de limite de vies.

### 1.4 Fin du raid

- Victoire (défiance à 0) : les survivants ennemis capitulent (ils jettent les armes et
  disparaissent), titre, bilan (meilleurs combattants, objectifs, bonus), récompenses.
- Défaite (chrono écoulé, ou plus aucun joueur enrôlé dans le monde) : retrait, bilan, rien perdu.
- 20 s pour souffler (le héraut propose « Rentrer à Valmont » tout de suite), puis retour de chacun
  à sa position d'origine. Nettoyage (§2.10).

---

## 2. Le monde des raids (partie technique)

### 2.1 Choix retenu

**Des mondes Bukkit dédiés, créés par le plugin avec `WorldCreator`**, découpés en emplacements ;
chaque raid prend un emplacement neuf ; les mondes sont effacés et recréés au démarrage :

| Monde | Environnement | Cibles |
|---|---|---|
| `citycore_raids` | surface (`NORMAL`) | toutes sauf celles ci-dessous (164) |
| `citycore_raids_nether` | `NETHER` | citadelle maudite, vestiges de bastion, forteresse du Nether (3) |
| `citycore_raids_end` | `THE_END` | cité de l'End (1) |

Les mondes du Nether et de l'End ne sont créés qu'à la première demande (phase R9), et pas du tout
si `raids.mondes.nether` / `end` est désactivé.

Repli si `WorldCreator` se comporte mal sous Youer (constaté en R0) : une **dimension déclarée par
datapack** (`world/datapacks/citycore_raids/data/citycore/dimension/raids.json`, type
`minecraft:overworld`, générateur `minecraft:noise` réglages `minecraft:overworld`), chargée
nativement par NeoForge au démarrage. Même logique d'emplacements ; l'effacement se fait alors en
supprimant le dossier `world/dimensions/citycore/raids` avant le chargement des mondes (à
confirmer : ordre `onLoad` du plugin / chargement des niveaux sous Youer).

Écartés :
- une zone lointaine du monde principal : la structure y resterait pour toujours, accessible à
  pied, et la carte s'en remplirait ;
- un monde créé puis détruit à chaque raid : décharger un niveau en pleine partie avec une
  soixantaine de mods NeoForge est le chemin le moins fiable d'un serveur hybride (mods qui gardent
  des références au niveau).

Indice déjà observé : Youer inscrit les mondes Bukkit et les dimensions de mods dans
`youer-config/worlds.yml` (ex. `pocket_dimension` d'Iron's Spells, environnement
`IRONS_SPELLBOOKS_POCKET_DIMENSION`).

### 2.2 Création et règles du monde

- `WorldCreator("citycore_raids")` : environnement `NORMAL`, générateur normal, **graine fixe**
  (config), `generateStructures(false)` (pas de structures naturelles parasites ; à vérifier pour
  celles des mods), `keepSpawnInMemory(false)`, `setAutoSave(false)` (le monde est jetable).
- Règles de jeu du monde : `doMobSpawning false`, `doPatrolSpawning false`, `doTraderSpawning
  false`, `doInsomnia false`, **`disableRaids true`** (tuer un capitaine pillard donne le Mauvais
  présage, qui déclencherait un raid illageois dans un village cible), `doDaylightCycle false`,
  `doWeatherCycle false`, **`keepInventory true`** (décision : inventaire et expérience gardés à la
  mort), `doImmediateRespawn false`, `mobGriefing false`, `doFireTick false`,
  `announceAdvancements false`.
- Heure fixe (midi par défaut) ; une cible peut demander la nuit (`heure: 18000`).
- Difficulté du monde : réglable (`NORMAL` par défaut).
- Nether et End : mêmes règles ; graine fixe ; pas de portail utilisable (§2.9). Dans le monde de
  l'End, le combat du dragon ne concerne que les abords de (0, 0) : les emplacements en sont
  éloignés (à vérifier en R9).

### 2.3 Remise à zéro

- Au démarrage (`reinitialiser-au-demarrage: true`), pour chacun des mondes des raids : si Youer
  l'a déjà chargé, en sortir tout joueur, `Bukkit.unloadWorld(monde, false)`, supprimer le
  dossier, recréer. Sinon supprimer le dossier avant `createWorld`. Le cache de classement des
  emplacements (§2.4) est gardé.
- `/city raid purge` (admin, hors raid) fait la même chose à chaud.
- Avec `reinitialiser-au-demarrage: false`, l'index du prochain emplacement est gardé en SQLite
  (`raid_state`) pour ne jamais reprendre un emplacement usé.

### 2.4 Emplacements et terrain cohérent (décision)

Le terrain d'un raid doit correspondre à la structure : chaque cible est posée sur un emplacement
dont le biome fait partie de ceux où elle apparaît naturellement.

**Familles de terrain.** Les biomes sont regroupés en familles (réglables dans
`raids.familles` de `config.yml`) :

| Famille | Biomes |
|---|---|
| `plaines` | plains, sunflower_plains |
| `prairie` | meadow, cherry_grove |
| `foret` | forest, flower_forest, birch_forest, old_growth_birch_forest |
| `foret-sombre` | dark_forest |
| `taiga` | taiga, old_growth_pine_taiga, old_growth_spruce_taiga |
| `neige` | snowy_plains, ice_spikes, snowy_taiga, snowy_beach, grove, snowy_slopes |
| `sommets` | jagged_peaks, frozen_peaks, stony_peaks, windswept_hills, windswept_gravelly_hills, windswept_forest |
| `desert` | desert |
| `savane` | savanna, savanna_plateau, windswept_savanna |
| `badlands` | badlands, wooded_badlands, eroded_badlands |
| `jungle` | jungle, sparse_jungle, bamboo_jungle |
| `marais` | swamp, mangrove_swamp |
| `champignons` | mushroom_fields |
| `plage` | beach, stony_shore |
| `mer` | ocean, deep_ocean, lukewarm_ocean, deep_lukewarm_ocean, warm_ocean |
| `mer-froide` | cold_ocean, deep_cold_ocean, frozen_ocean, deep_frozen_ocean |
| `nether`, `end` | mondes du Nether et de l'End des raids |

Raccourci **`variee`** : plaines, forêt, forêt sombre, taïga, neige, sommets, savane, jungle,
badlands (structures qui apparaissent presque partout : `#minecraft:structurebiomes` de Valarian
Conquest, nombreuses structures d'ATi).

**Terrain de chaque cible.** La colonne « Terrain » du catalogue (§7) donne les familles autorisées,
lues dans les jars le 21/10/2026 : champ `biomes` de la structure, tags de biomes résolus (jeu de
base, tags communs `c:` de NeoForge, tags des mods ; les tags de Towns and Towers remplacent ceux
du jeu de base sur ce serveur). Elle est recopiée dans `raids.yml` (`terrain:`). Cas tranchés à la
main :
- **Villages et avant-postes « de style » de Towns and Towers** : leurs biomes viennent de mods de
  biomes absents du serveur (Terralith, Biomes O' Plenty, Wythers…) ; terrain choisi d'après ces
  biomes : `classic` → plaines ; `iberian` → savane ; `mediterranean` → savane, plage ; `nilotic` →
  savane, marais ; `oriental` (avant-poste) et halte des marchands → désert ; `piglin` → savane ;
  `rustic` → forêt, plaines ; `swedish` → taïga, neige ; `tudor` → plaines, prairie.
- **Mowzie's Mobs** (génération réglée par `config/mowziesmobs-common.toml` du serveur) : Frostmaw →
  neige ; bosquet des Umvuthana → savane ; Sculpteur (monastère) → sommets ; salle du Wroughtnaut →
  variée (souterraine).
- Galion de Supplementaries → mer ; château dans le ciel → plage (il flotte au-dessus) ; tribu des
  glaces d'ATi → mer froide (banquise) ; citadelle maudite d'Iron's → Nether.
- Variantes régionales (`towns_and_towers:village_<suffixe>`, `pillager_outpost_<suffixe>`,
  villages du jeu de base) : le biome du suffixe (`desert` → désert, `grove` → neige…).

**Grille.** Un emplacement tous les `espacement` (1 024) blocs, à partir de x = z = 10 000, en
lignes de 64 (4 096 emplacements par monde).

**Classement sans générer de chunks.** `World#getBiome(x, 64, z)` lit le biome du générateur
(biomes « de bruit ») sans créer le chunk (à vérifier sous Youer en R0). Pour chaque emplacement,
échantillonnage 9×9 dans le rayon de la plus grande cible (≈ 120 blocs) :
- famille **dominante** de l'emplacement = celle d'au moins 80 % des points ;
- un emplacement sans famille dominante est sauté ;
- un emplacement de terre doit aussi offrir de la terre pour le camp (§2.8).

Le classement des 4 096 emplacements se fait une fois, étalé sur plusieurs ticks au démarrage, et
se garde dans un cache (`plugins/CityCore/raids-emplacements.yml`, clé = graine) : la graine est
fixe, les mêmes emplacements reviennent à chaque remise à zéro.

**Choix de l'emplacement d'une cible.** Le premier emplacement libre dont la famille dominante est
dans le terrain de la cible (au hasard parmi les familles autorisées, pour varier). Les familles
rares (champignons, badlands, sommets) peuvent manquer près de l'origine : si la grille n'en
contient aucune, la cible est **masquée** avec un avertissement (jamais posée sur un terrain qui ne
lui correspond pas). Après génération, contrôle du relief : si l'écart de hauteur dans l'emprise
dépasse `relief-max` (16 blocs), emplacement suivant de la même famille.

### 2.5 Préparation des chunks

- Un emplacement **par famille déjà demandée** est gardé prêt (au démarrage : `plaines`, `foret`,
  `neige`, `mer` ; puis, après chaque raid, celui de la famille qui vient d'être utilisée) :
  `World#getChunkAtAsync` sur le rayon de la cible et du camp (≈ 160 blocs, 21×21 chunks), étalé
  dans le temps.
- Quand la fiche d'une cible s'ouvre et qu'aucun emplacement de son terrain n'est prêt, la
  préparation démarre aussitôt (« Les éclaireurs repèrent le terrain… », progression affichée) ;
  le bouton « Sonner le rassemblement » s'active quand elle est finie.
- Pendant le raid : tickets de chunk du plugin (`Chunk#addPluginChunkTicket`) sur toute la zone,
  retirés au nettoyage.

### 2.6 Pose des structures

- Commande lancée par le plugin, **dans le monde des raids** :
  `execute in <clé du monde> run place structure <id> <x> <y> <z>`, avec `world.getKey()` comme clé
  (à vérifier sous Youer : `minecraft:citycore_raids` ?). Retour capturé avec
  `Bukkit.createCommandSender(Consumer<Component>)` (API Paper) pour détecter un échec.
- `/place structure` **ignore les biomes autorisés** de la structure, applique son adaptation au
  terrain et fait apparaître les entités de ses gabarits. Il **exige que les chunks soient chargés**
  (d'où §2.5) et **n'enregistre pas** la structure : `World#getStructures` ne la voit pas, et les
  apparitions propres à la structure (pillards des avant-postes du jeu, sorcière de la hutte) n'ont
  pas lieu → le plugin les remplace (§5.3).
- Villes : `execute in … run place jigsaw <pool de départ> <cible> <profondeur> <x> <y> <z>` pour
  relancer l'assemblage d'un village avec plus de niveaux (jusqu'à 20, rayon 128 : à vérifier en
  1.21.1, ainsi que le nom de jigsaw « cible » attendu dans la pièce de départ).
- Repli pour un gabarit simple : `Bukkit.getStructureManager().loadStructure(clé)` puis
  `Structure#place(...)` avec entités (API Paper), ou `place template`.
- Pose pendant le compte à rebours ; mesurer le temps des grosses cibles (§10).

### 2.7 Balayage de la cible → `Arena`

Après la pose, lecture de `ChunkSnapshot` (hors fil principal) sur le rayon de la structure
(`max_distance_from_center`, 64 à 116 blocs), puis des entités de la zone :

- **Boîte** de la cible : étendue des blocs non naturels.
- **Points remarquables** : cloches, bannières (dont `valarian_conquest:*_hanging_banner`), cages
  (`minecraft:spawner`), coffres, tonneaux et pots, portes, lits, bouts de chemin à la lisière
  (entrées), points hauts accessibles (postes d'archers), centre (cloche, sinon barycentre).
- **Entités**, classées par clé de type (`EntityType#getKey`, valable pour les créatures des mods
  sous Youer) :
  - **défenseurs** : monstres hostiles, soldats et archers de Valarian Conquest
    (`valarian_conquest:soldier`, `archer`), balistes (`valarian_conquest:ballista`), mages
    d'Iron's, boss de Mowzie's → étiquette `citycore.raid` + enregistrement ;
  - **civils** : villageois, citoyens de Valarian (`male_citizen`, `female_citizen`), marchands,
    allays → étiquette `citycore.raid.civil` ;
  - **à retirer** : golems de fer et de neige ;
  - **décor** : cadres, porte-armures, tableaux.
- Les portes de Valarian Conquest sont des **entités** (`wooden_gate`, `gate_entity`,
  `drawbridge`).
- Résultat : un enregistrement `Arena` (boîte, centre, points remarquables, défenseurs, civils,
  camp, zone à tenir) utilisé par les objectifs et les défenseurs.

### 2.8 Camp des joueurs et héraut

Le camp est le **point de départ du raid, à côté de la structure** : posé à `distance-camp`
(32 blocs) **du bord de la boîte de la cible** (§2.7), hors de portée des archers postés aux
murs, du côté le plus plat et le plus proche d'une entrée, face à la cible. C'est aussi le **point
de réapparition** des joueurs morts (§2.9).
- **Terre** : `valarian_conquest:neutral_tent_camp` (ses citoyens retirés) ; sans Valarian :
  plateforme et bannière de Valmont posées par le plugin. Le sol du camp suit le terrain de la
  cible (le camp d'un raid dans le désert est dans le désert).
- **Mer** (Abordage, repaires en mer comme le temple englouti) : `taxov:marine_ship` (vaisseau de
  la Marine, allié) ancré à 32 blocs ; sans Tax' Ocean Villager : radeau du plugin. Barques
  fournies (retirées à la fin).
- **Banquise** (tribu des glaces) : camp posé sur la glace.
- **Repaire souterrain** : camp en surface, au-dessus de l'accès (§5.7).
- **Nether** : plateforme de basalte et de pierre noire ; **End** : plateforme d'obsidienne.
- **Point de réapparition** : un emplacement libre et sûr au centre du camp (deux blocs d'air
  au-dessus d'un bloc plein, hors de l'eau et de la lave), calculé à la pose.
- **Héraut** : PNJ Citizens (registre en mémoire, jamais enregistré) : « Battre en retraite »,
  puis « Rentrer à Valmont » après la victoire.

### 2.9 Allers-retours et sécurité

- **Retour** : table `raid_returns` remplie avant la téléportation.
- **Reconnexion** : raid en cours → camp ; sinon → position de retour. Après un plantage, le monde
  est recréé : `PlayerSpawnLocationEvent` renvoie à la position de retour tout joueur dont la
  position enregistrée est dans le monde des raids.
- **Entrée** : uniquement par le plugin (`PlayerTeleportEvent` vers ce monde annulé sinon).
- **Sortie** : par le héraut ou `/city raid quitter` (retraite). Commandes bloquées dans ce monde
  (config) : `sethome`, `home`, `back`, `spawn`, `tpa`, `tpahere`, `tpaccept`, `warp`, `tp`…
- **Mort** (décision) : **inventaire et expérience conservés** (règle `keepInventory` des mondes
  des raids, et par sécurité `PlayerDeathEvent#setKeepInventory(true)` / `setKeepLevel(true)` avec
  liste de butin vidée pour un joueur enrôlé) ; à la réapparition (`PlayerRespawnEvent`), le joueur
  revient au **point de réapparition du camp de départ, à côté de la structure** (§2.8) — jamais au
  lit ni au point d'apparition du monde. 3 s d'invulnérabilité à la réapparition. Pas de limite de
  vies ; la mort compte pour le bonus `sans-perte` et le bilan. Un joueur mort pendant le bilan
  réapparaît au camp et rentre avec les autres.
- **Lits et ancres** : pas de point d'apparition personnel dans ces mondes
  (`PlayerSetSpawnEvent` annulé).
- **Portails** : création annulée dans ce monde (`PortalCreateEvent`).
- Coffre de l'Ender autorisé (affaires personnelles).

### 2.10 Nettoyage

Fin du raid : défenseurs, civils, renforts, héraut et barques retirés ; barre et entrée de quête
retirées ; tickets de chunk levés ; emplacement marqué usé. Arrêt du serveur en plein raid :
rien à faire, le monde est recréé au démarrage et les joueurs renvoyés (§2.9).

### 2.11 Les autres mods dans ce monde

À observer en R0 : Ecliptic Seasons (saisons par dimension), JourneyMap (carte du monde des raids ;
JourneyMap est aussi côté serveur), Tough As Nails (température), Snow Real Magic, Epic Fight
(animations des PNJ via les étiquettes du mod compagnon), Citizens. Structurify et Sparse
Structures n'agissent que sur la génération naturelle, pas sur `/place`. Better Village modifie
les villages du jeu de base : ils apparaîtront ainsi dans les raids.

---

## 3. Vérification des mods et du catalogue

### 3.1 Mods concernés

Versions lues dans `F:\Minecraft Server V2\mods` le 21/10/2026.

| Mod | Clé `mod-integrations` | Espace des structures | Témoin de détection | Version | Cibles |
|---|---|---|---|---|---|
| Valarian Conquest | `valarian_conquest` (existe) | `valarian_conquest` | objet `valarian_conquest:templar_sword` (existe) | 4.2 | 21 |
| Towns and Towers | **`t_and_t` (nouveau)** | `towns_and_towers` (≠ identifiant du mod) | structure `towns_and_towers:village_meadow` | 1.13.7 | 58 |
| ATi Structures | `ati_structures` (existe) | `ati_structures` | structure (existe) | 1.4.4 | 46 |
| Mo' Structures | `mostructures` (existe) | `mostructures` | structure (existe) | 1.6.0 | 11 |
| Iron's Spells 'n Spellbooks | `irons_spellbooks` (existe) | `irons_spellbooks` | existe | 3.15.4 | 8 |
| Mowzie's Mobs | `mowziesmobs` (existe) | `mowziesmobs` | existe | 1.7.5 | 4 |
| Medieval Buildings | **`medieval_buildings` (nouveau)** | `medieval_buildings` | structure `medieval_buildings:fort` | 1.1.1 | 3 |
| Tax' Ocean Villager | `taxov` (existe) | `taxov` | existe | 4.0.1 | 5 (+ camp allié) |
| Supplementaries | **`supplementaries` (nouveau)** | `supplementaries` | structure `supplementaries:galleon` | 3.5.27 | 1 |
| Minecraft | — | `minecraft` | toujours présent | 1.21.1 | 11 |

Sans structure à apporter : Structurify 2.0.20 (réglages), Sparse Structures 3.0 (espacement),
Better Village 3.3.1 (modifie les villages du jeu de base).

Ajouts à `integration.ModIntegrations.Mod` (constructeur `id, nom, succès, objet, structure`) :
`TOWNS_AND_TOWERS("t_and_t", "Towns and Towers", null, null, "towns_and_towers:village_meadow")`,
`MEDIEVAL_BUILDINGS("medieval_buildings", "Medieval Buildings", null, null, "medieval_buildings:fort")`,
`SUPPLEMENTARIES("supplementaries", "Supplementaries", null, null, "supplementaries:galleon")`,
et leurs clés dans la section `mod-integrations` de `config.yml` (`auto | on | off`).

### 3.2 Contrôles au démarrage

Pour chaque cible de `raids.yml` :
1. chaque mod de `avec-mod` présent (`ModIntegrations`, réglage `auto/on/off` compris) ;
2. chaque structure enregistrée (`ModIntegrations.structureExists`, registre `Registry.STRUCTURE`) ;
3. chaque créature nommée par la cible (occupants ajoutés, maître, chef) connue
   (`Registry.ENTITY_TYPE`) ;
4. pour les villes : le village et les pièces ajoutées existent (le pool de départ n'est vérifiable
   qu'à la pose : un échec de `place jigsaw` masque la ville jusqu'au redémarrage, avec un
   avertissement) ;
5. son **terrain** : au moins un emplacement d'une de ses familles existe dans la grille du monde
   concerné (§2.4) ; pour une cible du Nether ou de l'End, ce monde est activé.

Une cible incomplète est **masquée** (pas d'erreur). Console : une ligne de bilan, par exemple
`⚔ Raids : 168 cibles, 160 disponibles ; 8 masquées (Supplementaries absent : galion ; aucun
emplacement « champignons » : village et avant-poste des champignons)`.
Un mod **présent** dont une structure attendue manque (mod mis à jour) : avertissement qui la nomme.

### 3.3 Commandes de contrôle

- `/city mods` : une ligne « Raids : N cibles » par mod.
- `/city raid catalogue [niveau]` (admin) : cibles par niveau, disponibles ou non et pourquoi.
- `/city raid inconnues` (admin) : structures enregistrées sur le serveur qui ne sont ni au
  catalogue ni dans `ignorees` (pour un nouveau mod de structures).

### 3.4 Ajouter un mod de structures

1. Le déclarer dans `ModIntegrations.Mod` (témoin = une de ses structures) et dans
   `mod-integrations`.
2. `/city raid inconnues` liste ses structures ; les poser en test (`/city raid poser <id>`),
   noter occupants et taille.
3. Les ajouter à `raids.yml` (type, niveau, nom, **terrain** d'après les biomes de la structure,
   défenseurs, objectifs) ou à `ignorees`.

---

## 4. Niveaux, types et déblocage

### 4.1 Les 7 niveaux

| Niv. | Nom | Chapitre | Défenseurs (1 joueur) | Chrono | Renforts | Recharge | Cibles |
|---|---|---|---|---|---|---|---|
| I | Escarmouche | II | 3 à 6 | 6 min | — | 30 min | 16 |
| II | Coup de main | III | 5 à 12 | 8 min | — | 45 min | 53 |
| III | Expédition | IV | 8 à 15 | 10 min | 0 à 1 vague | 60 min | 40 |
| IV | Assaut | V | 15 à 25 | 15 min | 1 vague | 90 min | 25 |
| V | Offensive | VI | 20 à 35 | 18 min | 2 vagues | 2 h | 10 + 8 villes |
| VI | Grande offensive | VIII | 25 à 45 | 20 min | 3 vagues | 3 h | 12 |
| VII | Légende | X | 30 et plus + boss | 25 min | 3 vagues + chef | 6 h | 12 |

- **Déblocage** : chapitre de la ville atteint **et** une victoire au niveau précédent (défaut).
- **Joueurs** : défenseurs × (1 + 0,5 × (joueurs − 1)), plafonnés à 60 vivants (comme les sièges).
- **Recharge** : par niveau, pour tout le serveur, à partir de la fin du raid ; moitié en cas de
  défaite ; un seul raid à la fois.

### 4.2 Les types

Pour tous les types, le terrain est celui de la structure (§2.4, colonne « Terrain » du §7).

| Type | Icône | Terrain habituel | Particularités | Objectifs typiques |
|---|---|---|---|---|
| Camp | ⛺ | terre | petit, ouvert | garnison |
| Avant-poste | 🗼 | terre | tour, archers en hauteur, captifs (allays) | garnison, bannière, libérer |
| Village | 🏘 | terre (banquise pour la tribu des glaces) | civils à épargner, cloche | garnison, cloche ; bonus épargner |
| Ville | 🏙 | terre (celui du style du village) | composée (§7.8) : tours aux entrées, bailli | tours, chef, tenir la place |
| Fort et bastion | 🏰 | terre, Nether (bastion, forteresse) | grande garnison, cages | garnison, cages, chef |
| Château | 🏯 | terre, Nether (citadelle), End (cité) | portes, bannières, donjon | porte, bannières, chef, tenir |
| Abordage | ⚓ | mer ou mer froide | navire allié comme camp, barques | aborder, capitaine, pavillon |
| Repaire | 🕸 | terre, parfois souterrain ou en mer | maître du repaire (boss ou mage), sbires | maître, cages, nid |

---

## 5. L'assaut

### 5.1 Défiance et chrono

- La **défiance** part de 100 % ; chaque objectif en retire une part (poids dans `raids.yml`,
  défauts par type). Un objectif peut être **obligatoire** (le chef, le maître) : la défiance ne
  descend pas sous 10 % tant qu'il reste à faire.
- **Victoire** : défiance à 0 → capitulation. **Défaite** : chrono écoulé.
- Moments clés (son et titre) : chute d'une bannière, chute du chef ou du maître, cloche sonnée,
  porte brisée, passage sous 50 % (renforts).

### 5.2 Objectifs

| Clé | Objectif | Détection |
|---|---|---|
| `garnison` | abattre X % des défenseurs | défenseurs étiquetés ; `EntityDeathEvent` |
| `cloche` | sonner la cloche une fois la place dégagée (aucun défenseur à 16 blocs) | cloche de l'`Arena` ; `BellRingEvent` |
| `bannieres` | arracher les bannières ennemies (le pavillon pour un navire) | blocs repérés ; `BlockBreakEvent` |
| `porte` | briser la porte ou le pont-levis | entités `valarian_conquest:wooden_gate`, `gate_entity`, `drawbridge` (cassables : à vérifier) ; à défaut, blocs de la porte |
| `chef` | abattre le chef (commandant, bailli, capitaine) | PNJ nommé et lumineux posé au point le plus défendu (donjon, place, pont) |
| `maitre` | abattre le maître du repaire | boss ou mage du mod présent dans la structure, sinon posé par le plugin |
| `cages` | détruire les cages à monstres | blocs `minecraft:spawner` repérés ; `BlockBreakEvent` |
| `tenir` | tenir une zone N secondes sans défenseur dedans | zone autour de la cloche, du donjon ou du pont |
| `liberer` | libérer les captifs | allays en cage, villageois enfermés : interaction ou cage ouverte |
| `aborder` | poser le pied sur le navire ennemi | joueur sur un bloc du navire (boîte de l'`Arena`) |
| bonus `epargner` | aucun civil tué | civils étiquetés |
| bonus `sans-perte` | aucun joueur mort | |
| bonus `eclair` | victoire en moins de X minutes | |

Les cages sont un objectif à partir du niveau IV et pour les repaires ; en dessous, elles sont
neutralisées à la pose (remplacées par de l'air).

### 5.3 Défenseurs

- **Sources** (par cible) :
  - `gabarit` : les occupants posés avec la structure (étiquetés au balayage) ;
  - `armee` : soldats des factions ennemies des sièges (`EnemyArmy`, PNJ Citizens, sans mod) ;
  - `creatures` : créatures par identifiant (`minecraft:pillager`, `valarian_conquest:soldier`…).
- **Complément** : si le gabarit compte moins que le minimum du niveau, le plugin ajoute des
  occupants aux postes de l'`Arena` (cloche, entrées, portes, points hauts pour les archers).
- **Remplacement** : pour les pièces ajoutées d'une ville et le `taxov:pirate_ship`, les occupants
  d'origine sont retirés et remplacés par ceux de la faction de la cible.
- Morts-vivants : ne brûlent pas au soleil ; aucun défenseur ne ramasse d'objet ni ne disparaît de
  lui-même (comme les assaillants des sièges).
- Soldats de Valarian Conquest : hostilité envers les joueurs à vérifier (le mod a des parchemins
  de citoyenneté) ; au besoin le plugin leur donne une cible (`Mob#setTarget`), sinon un module du
  mod compagnon.

### 5.4 Renforts et chef

- Quand la défiance passe sous 75, 50 puis 25 % (selon le niveau), des renforts arrivent du côté
  opposé au camp : escouades d'`EnemyArmy` / `Squad`, objectif fourni par
  `EnemyArmy#setObjective(Supplier<Location>)` (la zone à tenir, sinon le joueur le plus proche).
  À vérifier : `EnemyArmy` ne suppose pas le monde de la ville.
- Chef (niveaux V à VII, villes, navires) : nommé, lumineux, plus de vie, arme dédiée (modèle :
  le champion « Gorvak » des sièges).

### 5.5 Affichage

Une barre rouge (défiance + chrono), les objectifs dans le panneau de quêtes, les annonces en
titre. Bilan dans le chat.

### 5.6 Spécificités de l'Abordage

- Emplacement **mer** ou **mer froide** selon le navire (§7 : le brise-glace empalé en mer froide,
  le navire des sorcières en mer…) ; les navires se posent à la hauteur prévue par leur mod (à
  vérifier).
- Camp sur le navire allié, qui est aussi le point de réapparition (sur le pont) ; barques
  fournies ; un joueur tombé à l'eau nage ou, s'il se noie, réapparaît sur le pont avec son
  inventaire.
- Objectifs : `aborder`, `garnison`, `bannieres` (pavillon), `chef` (capitaine) ; le roi pirate
  illageois (`taxov:illager_pirate_king`) est le chef du vaisseau du roi.
- Piste future (hors plan) : navires de Small Ships prêtés aux joueurs.

### 5.7 Spécificités des Repaires

- Objectif `maitre` obligatoire : boss de Mowzie's (Frostmaw, Ferrous Wroughtnaut, Sculpteur), mage
  d'Iron's (apothicaire, cryomancien, pyromancien), Warden… S'il n'apparaît pas avec la structure,
  le plugin le pose (identifiants à relever dans les jars et dans les quêtes d'Hercule et de
  Gandalf de `config.yml`).
- Repaires souterrains (catacombes, temple enfoui, monument souterrain, salle du Wroughtnaut) : le
  camp est en surface, sur le terrain de la cible ; le plugin creuse un passage du camp à la
  première salle repérée, ou la cible est retirée si R0/R8 montre que c'est impraticable.
- Repaire en mer (temple englouti) : camp et réapparition sur le navire allié, comme l'Abordage.

---

## 6. Récompenses, relance, garde-fous

### 6.1 Récompenses (à équilibrer en jeu)

| Niv. | Caisse de la ville | Par joueur (Vault) | Trophée |
|---|---|---|---|
| I | 100 | 15 | — |
| II | 150 | 25 | — |
| III | 220 | 35 | première victoire du niveau : bannière de la faction vaincue |
| IV | 300 | 50 | idem |
| V | 400 | 70 | idem |
| VI | 520 | 90 | idem |
| VII | 700 | 120 | trophée unique par cible légendaire |

- Bonus : +15 % par bonus réussi ; renommée +5 % par victoire du niveau (plafond +25 %), qui
  renforce aussi les défenseurs de +10 % par victoire (plafond +50 %).
- Comparaison : un siège rapporte 150 + 50 × palier à la caisse.
- Bonus « sécurité » des besoins (phase 6) par le même branchement que les sièges, bonus seulement.
- Trophées marqués **non revendables** (même marque que les objets des réserves).

### 6.2 Relance

Recharge par niveau (§4.1). Pas de raid pendant un siège ; un siège prévu par le calendrier
pendant un raid est reporté de 30 min (annonce).

### 6.3 Anti-exploitation

- Défenseurs, renforts, boss : **aucun butin** (l'XP reste).
- Coffres, tonneaux, pots décorés : **vidés** et tables de butin retirées à la pose.
- Blocs cassés : rien ne tombe (`BlockDropItemEvent`), y compris ceux posés par les joueurs.
- **Commerce avec les villageois bloqué** dans ce monde (un village neuf à chaque raid = échanges
  neufs à volonté).
- Raids illageois, patrouilles, marchands ambulants coupés (§2.2) ; golems retirés.
- Les morts en raid comptent pour les quêtes de chasse et les récits (défaut, §11) ; aucune quête
  « Rapporter » n'est faisable faute de butin.
- Entrée réservée aux joueurs enrôlés ; recharge par niveau.

---

## 7. Catalogue des cibles (168)

Lu dans les jars le 21/10/2026. Terrain : familles de biomes où la structure est posée (§2.4) ;
« variée » = plaines, forêt, forêt sombre, taïga, neige, sommets, savane, jungle, badlands ;
« celui de la variante » = le biome indiqué par le suffixe de l'identifiant. Taille : pièce
principale (x×y×z). Occupants : entités des gabarits (pièce principale, ou toutes les pièces quand
c'est précisé) ; le nombre réel varie avec l'assemblage. « Ajoutés » : le gabarit n'a que des
civils ou rien → occupants du plugin (§5.3). Noms : propositions.

### 7.1 Niveau I — Escarmouche (16)

| Identifiant | Nom | Type | Terrain | Taille | Occupants lus | Défenseurs |
|---|---|---|---|---|---|---|
| `valarian_conquest:bandit_camp` | Camp de bandits | camp | variée | 12×12×12 | 2 soldats | gabarit + complément |
| `valarian_conquest:bandit_ruins` | Repaire de bandits | camp | variée | 17×24×17 | 2 soldats, 1 archer | gabarit + complément |
| `valarian_conquest:bandit_ruins_2` | Repaire de bandits (variante) | camp | variée | 17×24×16 | 1 soldat, 1 archer | gabarit + complément |
| `valarian_conquest:neutral_tent_camp` | Camp de tentes | camp | variée | 29×18×29 | 6 citoyens (civils) | ajoutés |
| `valarian_conquest:neutral_tent_camp_2` | Petit camp de tentes | camp | variée | 16×12×20 | 1 soldat, 3 citoyens | ajoutés |
| `ati_structures:nomadic_camp` | Camp nomade | camp | variée | 36×12×30 | 5 zombies momifiés | gabarit |
| `ati_structures:illager_homestead` | Ferme illageoise | camp | variée | 17×15×13 | 2 pillards, 1 vindicateur, cloche | gabarit + complément |
| `ati_structures:storage_house` | Entrepôt des pillards | camp | variée | 16×12×12 | 2 pillards, 1 vindicateur | gabarit + complément |
| `ati_structures:storage_shack` | Cabane des pillards | camp | variée | 17×11×13 | 2 pillards, 1 vindicateur | gabarit + complément |
| `towns_and_towers:exclusives/village_wandering_trader_camp` | Halte des marchands | camp | désert | 16 pièces | 10 marchands ambulants (civils) | ajoutés |
| `mostructures:pillager_mines` | Mines des pillards | camp | plaines, taïga, neige, sommets | 17×8×10, 9 pièces | aucun (naissent du terrain) | pillards ajoutés |
| `ati_structures:haunted_ruin` | Ruine hantée | repaire | plaines, forêt, taïga, savane | 15×10×15 | 6 squelettes (3 variantes) | gabarit |
| `ati_structures:stray_ruins` | Ruines des vagabonds | repaire | neige, sommets | 15×10×15 | 4 squelettes | gabarit |
| `ati_structures:silverfish_nest` | Nid de poissons d'argent | repaire | variée | 15×10×15 | 4 poissons d'argent, 1 cage | gabarit |
| `minecraft:swamp_hut` | Hutte de la sorcière | repaire | marais | — | sorcière (naît du terrain) | sorcière ajoutée (maître) |
| `irons_spellbooks:mangrove_hut` | Cabane de l'apothicaire | repaire | marais | 16×27×27 | 1 apothicaire (`irons_spellbooks:apothecarist`) | gabarit (maître) |

### 7.2 Niveau II — Coup de main (53)

| Identifiant | Nom | Type | Terrain | Taille | Occupants lus | Défenseurs |
|---|---|---|---|---|---|---|
| `minecraft:pillager_outpost` | Avant-poste pillard | avant-poste | plaines | 16×30×16 | allays en cage (pillards : naissent du terrain) | pillards ajoutés |
| `towns_and_towers:pillager_outpost_<suffixe>` × 22 : `badlands`, `beach`, `birch_forest`, `desert`, `flower_forest`, `forest`, `grove`, `jungle`, `meadow`, `mushroom_fields`, `old_growth_taiga`, `savanna`, `savanna_plateau`, `snowy_beach`, `snowy_plains`, `snowy_slopes`, `snowy_taiga`, `sparse_jungle`, `sunflower_plains`, `swamp`, `taiga`, `wooded_badlands` | Avant-postes pillards régionaux | avant-poste | celui de la variante | 9×4×9 à 35×18×30 | toutes pièces : 2 pillards, 1 vindicateur, 1 ou 2 allays en cage | gabarit + complément |
| `towns_and_towers:exclusives/pillager_outpost_<style>` × 8 : `classic`, `iberian`, `mediterranean`, `nilotic`, `oriental`, `rustic`, `swedish`, `tudor` | Avant-postes pillards de style | avant-poste | selon le style (§2.4) | 15×39×15 à 36×21×34 | idem | gabarit + complément |
| `valarian_conquest:templar_outpost` | Avant-poste templier | avant-poste | variée | 17×32×17 | 4 soldats, 3 archers, bannières | gabarit + complément |
| `valarian_conquest:valarian_outpost` | Avant-poste valarien | avant-poste | variée | 28×34×27 | 5 soldats, 2 archers | gabarit + complément |
| `valarian_conquest:visgothian_outpost` | Avant-poste wisigoth | avant-poste | variée | 23×32×23 | 4 soldats, 3 archers | gabarit + complément |
| `valarian_conquest:burgundian_outpost` | Avant-poste bourguignon | avant-poste | variée | 25×32×23 | 6 soldats, 2 archers | gabarit |
| `valarian_conquest:bohemian_outpost` | Avant-poste bohémien | avant-poste | variée | 32×35×29 | 7 soldats, 2 archers, 2 citoyens | gabarit |
| `valarian_conquest:neutral_outpost` | Avant-poste neutre | avant-poste | variée | 28×32×25 | 2 soldats, 2 archers, 3 citoyennes | gabarit + complément |
| `ati_structures:mud_tower` | Tour de terre | avant-poste | variée | 15×25×15 | 7 zombies momifiés | gabarit |
| `medieval_buildings:tower` | Tour de guet | avant-poste | variée | 12×30×12 | 3 cages à zombies | ajoutés |
| `mostructures:villager_tower` | Tour des villageois | avant-poste | plaines, forêt, forêt sombre, neige | 16×30×16, 6 pièces | toutes pièces : 14 villageois, golem | ajoutés |
| `mostructures:tavern` | Taverne de carrefour | avant-poste | plaines, forêt, forêt sombre, neige | 16×28×16, 9 pièces | aucun | ajoutés |
| `mostructures:ice_tower` | Tour de glace | avant-poste | neige, sommets | 22×25×22 | cages | ajoutés |
| `taxov:witch_ship` | Navire des sorcières | abordage | mer | 9×27×28 | 2 sorcières | gabarit + complément |
| `taxov:zombie_pirate_ship` | Navire pirate maudit | abordage | mer, mer froide | 12×26×28 | 4 zombies-villageois | gabarit + complément |
| `taxov:zombie_marine_ship` | Vaisseau maudit de la Marine | abordage | mer | 12×29×30 | 4 zombies-villageois | gabarit + complément |
| `medieval_buildings:ship` | Nef médiévale | abordage | mer, mer froide | 13×35×39 | 3 cages à squelettes | ajoutés |
| `mostructures:pirate_ship` | Bateau pirate | abordage | mer, mer froide | 32×25×32, 4 pièces | cages (squelettes, vagabonds, zombie, poissons d'argent, cube de magma) | ajoutés |
| `irons_spellbooks:impaled_icebreaker` | Brise-glace empalé | abordage | mer froide | 21×25×29 | aucun | ajoutés (pose en mer à vérifier) |
| `ati_structures:rotting_temple` | Temple pourrissant | repaire | jungle, marais | 20×20×20 | 9 zombies, 2 cages | gabarit |
| `ati_structures:rotting_temple_underground` | Temple pourrissant enfoui | repaire | jungle | 20×20×20 | 7 zombies, 2 cages | gabarit (souterrain) |
| `mostructures:big_pyramid` | Grande pyramide | repaire | désert | 48×25×48 | cages (7 squelettes, 3 zombies momifiés) | cages + complément |
| `mostructures:sphinx` | Sphinx | repaire | désert | 32×18×48 | cages à squelettes | cages + complément |
| `irons_spellbooks:mountain_tower` | Tour du cryomancien | repaire | prairie, neige, sommets | 28×52×17 | 1 cryomancien | gabarit (maître) |

### 7.3 Niveau III — Expédition (40)

| Identifiant | Nom | Type | Terrain | Taille | Occupants lus | Défenseurs |
|---|---|---|---|---|---|---|
| `minecraft:village_plains`, `village_desert`, `village_savanna`, `village_snowy`, `village_taiga` | Villages des plaines, du désert, de la savane, des neiges, de la taïga | village | celui de la variante | rayon 80 | villageois, golem | ajoutés |
| `towns_and_towers:village_<suffixe>` × 17 : `badlands`, `beach`, `birch_forest`, `flower_forest`, `forest`, `grove`, `jungle`, `meadow`, `mushroom_fields`, `old_growth_taiga`, `savanna_plateau`, `snowy_slopes`, `snowy_taiga`, `sparse_jungle`, `sunflower_plains`, `swamp`, `wooded_badlands` | Villages régionaux (suisse, viking, polonais, roumain, japonais…) | village | celui de la variante | rayon 80 à 116 | villageois, golem | ajoutés |
| `towns_and_towers:exclusives/village_<style>` × 8 : `classic`, `iberian`, `mediterranean`, `nilotic`, `piglin`, `rustic`, `swedish`, `tudor` | Villages de style | village | selon le style (§2.4) | rayon 80 à 116 | villageois, golem ; piglin : 3 hoglins | ajoutés |
| `ati_structures:ice_tribe` | Tribu des glaces | village | mer froide (banquise) | 42×20×34 | 11 villageois, golem, cloche | ajoutés |
| `ati_structures:jungle_settlement` | Colonie des sorcières | village | jungle | 48×17×48 | 12 sorcières, 5 gardiens | gabarit |
| `mostructures:villager_market` | Bourg du marché | village | plaines, forêt, forêt sombre, neige, savane | 32×23×32, 11 pièces | toutes pièces : 5 villageois, golem, cloche | ajoutés |
| `taxov:pirate_ship` | Navire pirate | abordage | mer | 9×29×28 | 4 villageois pirates (métiers du mod), golem | remplacés par des pirates |
| `towns_and_towers:village_ocean` | Village sur l'eau | abordage | mer, mer froide | 81×3×80 | villageois | ajoutés |
| `ati_structures:arachnid_dwelling` | Antre des arachnides | repaire | forêt sombre, taïga, neige, jungle, marais | 48×30×31 | 16 araignées venimeuses, 12 araignées, 8 cages | gabarit |
| `ati_structures:dojo` | Dojo hanté | repaire | plaines, forêt, taïga, savane, jungle | 35×40×29 | 16 squelettes | gabarit |
| `irons_spellbooks:pyromancer_tower` | Tour du pyromancien | repaire | plaines, forêt, forêt sombre, neige, sommets, marais | à relever | pyromancien (à relever) | maître |
| `ati_structures:jungle_grotto` | Grotte de la jungle | repaire | jungle, marais | 48×48×48 | 17 zombies, 11 phantoms, 7 noyés, 6 cages | gabarit |
| `ati_structures:sinking_temple` | Temple englouti | repaire | mer, mer froide | 48×48×48 | 37 zombies, 5 villageois | gabarit |

### 7.4 Niveau IV — Assaut (25)

| Identifiant | Nom | Type | Terrain | Taille | Occupants lus | Défenseurs |
|---|---|---|---|---|---|---|
| `valarian_conquest:hospitaller_outpost` | Avant-poste hospitalier | avant-poste | variée | 27×38×27 | 6 soldats, 6 archers, porte | gabarit |
| `valarian_conquest:barathian_outpost` | Avant-poste barathien | avant-poste | variée | 19×32×19 | 8 soldats, 2 archers, 4 balistes | gabarit |
| `valarian_conquest:orleanian_outpost` | Tour orléanaise | avant-poste | variée | 17×44×17 | 7 soldats, 2 archers, 3 balistes, porte, cloche | gabarit |
| `ati_structures:illager_tower` | Tour illageoise | avant-poste | variée | 19×25×19 | 10 pillards, 6 vindicateurs | gabarit |
| `ati_structures:dark_tower` | Tour sombre | avant-poste | variée | 23×21×23 | 19 zombies momifiés | gabarit |
| `ati_structures:overgrown_outpost` | Avant-poste envahi | avant-poste | forêt, forêt sombre, taïga, jungle | 27×45×35 | 25 zombies | gabarit |
| `ati_structures:desert_outpost` | Fortin du désert | fort | désert | 48×48×48 | 26 zombies momifiés | gabarit |
| `ati_structures:dark_keep` | Donjon sombre | fort | variée | 20×35×22 | 16 squelettes, bannières | gabarit |
| `ati_structures:small_keep` | Petit donjon | fort | variée | 17×38×23 | 13 zombies-villageois | gabarit |
| `ati_structures:woodland_keep` | Donjon des bois | fort | forêt, forêt sombre, taïga, neige, jungle, marais | 43×40×43 | 25 zombies | gabarit |
| `ati_structures:tavern` | Relais fortifié | fort | plaines, forêt, taïga, savane, badlands | 48×37×48 | 22 zombies momifiés, 4 villageois | gabarit |
| `ati_structures:villager_inn` | Auberge envahie | fort | plaines, forêt, forêt sombre, taïga, jungle, marais | 44×48×43 | 20 zombies momifiés, 5 zombies | gabarit |
| `ati_structures:manor` | Manoir | fort | forêt, forêt sombre, taïga, jungle | 35×31×48 | 12 vindicateurs, 10 zombies momifiés, 2 illusionnistes | gabarit |
| `ati_structures:old_residence` | Vieille demeure | fort | forêt, forêt sombre, taïga, jungle | 27×37×31 | 9 vindicateurs, 8 araignées, évocateur, illusionniste, 2 cages | gabarit |
| `ati_structures:castillo` | Castillo | fort | variée | 20×20×41 | 13 pillards, 9 vindicateurs, ravageur, illusionniste | gabarit |
| `medieval_buildings:fort` | Fort médiéval | fort | plaines | 68×30×55 | 16 villageois, 4 cages à araignées venimeuses, bannières | ajoutés |
| `mostructures:pillager_factory` | Fabrique des pillards | fort | plaines, taïga, neige, sommets | 32×22×32, 8 pièces | toutes pièces : ravageur (pillards : naissent du terrain) | pillards ajoutés |
| `mowziesmobs:umvuthana_grove` | Bosquet des Umvuthana | village | savane | généré par le mod | guerriers Umvuthana, Umvuthi (à vérifier) | gabarit |
| `supplementaries:galleon` | Galion | abordage | mer | 19×37×47, 17 pièces | toutes pièces : ravageur, golem | ajoutés |
| `towns_and_towers:pillager_outpost_ocean` | Avant-poste pillard en mer | abordage | mer, mer froide | 76×65×27 | 2 pillards, 1 vindicateur, allays | gabarit + complément |
| `ati_structures:spider_nest` | Nid d'araignées géant | repaire | variée | 45×32×45 | 51 araignées | gabarit |
| `ati_structures:gnome_hut` | Hutte du gnome | repaire | forêt sombre, taïga, jungle, champignons | 24×41×23 (2 variantes) | jusqu'à 79 zombies | gabarit |
| `ati_structures:underground_monument` | Monument souterrain | repaire | variée | 45×11×45 | 20 pillards | gabarit (souterrain) |
| `irons_spellbooks:ice_spider_den` | Antre des araignées des glaces | repaire | neige, sommets | à relever | à relever (cages) | gabarit |
| `irons_spellbooks:catacombs` | Catacombes | repaire | variée (souterrain) | à relever | à relever | gabarit (souterrain) |

### 7.5 Niveau V — Offensive (10 + 8 villes, §7.8)

| Identifiant | Nom | Type | Terrain | Taille | Occupants lus | Défenseurs |
|---|---|---|---|---|---|---|
| `ati_structures:old_fort` | Vieux fort | fort | neige, sommets | 48×48×48 | 22 squelettes, 2 wither squelettes, zombie momifié, cloche | gabarit |
| `ati_structures:granite_fort` | Fort de granit | fort | plaines, forêt, forêt sombre, taïga, savane | 48×48×48 | 18 squelettes, 15 zombies momifiés, 3 villageois, golems | gabarit |
| `ati_structures:quarry` | Carrière illageoise | fort | plaines, forêt | 48×26×48, 12 pièces | toutes pièces : 12 vindicateurs, 5 pillards, ravageur, illusionniste | gabarit |
| `ati_structures:ati_stoneworks` | Taillerie illageoise | fort | variée | 48×48×48, 14 pièces | toutes pièces : 28 vindicateurs, 17 pillards, 4 évocateurs, 2 illusionnistes, ravageur | gabarit |
| `ati_structures:fortified_temple` | Temple fortifié | fort | plaines, forêt, taïga, savane, jungle | 48×48×48 | 21 pillards, 13 vindicateurs, 8 zombies momifiés, 4 évocateurs, 4 illusionnistes | gabarit |
| `ati_structures:steam_house` | Forge à vapeur | fort | variée | 48×48×48 | 33 pillards, 11 gardiens, 7 zoglins, gardien ancien | gabarit |
| `irons_spellbooks:evoker_fort` | Fort des évocateurs | fort | variée | 19×34×19, 26 pièces | toutes pièces : archévocateur, vindicateur chasseur de mages, évocateur, pillard, 2 ravageurs | gabarit (chef : archévocateur) |
| `mowziesmobs:wrought_chamber` | Salle du Wroughtnaut | repaire | variée (salle souterraine) | généré par le mod | Ferrous Wroughtnaut (à vérifier) | maître |
| `ati_structures:ancient_temple` | Temple antique | repaire | jungle | 48×48×48 | 26 squelettes, ghast | gabarit |
| `minecraft:fortress` | Forteresse du Nether | fort | **Nether** | générée par le jeu | cages à blazes (blazes et wither squelettes naissent du terrain) | cages + wither squelettes et blazes ajoutés |

### 7.6 Niveau VI — Grande offensive (12)

| Identifiant | Nom | Type | Terrain | Taille | Occupants lus | Défenseurs |
|---|---|---|---|---|---|---|
| `valarian_conquest:burgundian_castle` | Château bourguignon | château | plaines, neige | 33×36×32 | 12 soldats, 6 archers, porte, 3 citoyennes | gabarit |
| `valarian_conquest:hospitaller_castle` | Château hospitalier | château | plaines, neige | 64×40×55 | 14 soldats, 10 archers, porte, 4 citoyens | gabarit |
| `valarian_conquest:visgothian_castle` | Château wisigoth | château | plaines, neige | 69×48×58 | 14 soldats, 8 archers, porte, 5 citoyens | gabarit |
| `valarian_conquest:valarian_castle` | Château valarien | château | plaines, neige | 72×40×80 | 17 soldats, 7 archers, porte, 7 citoyens | gabarit |
| `valarian_conquest:templar_castle` | Commanderie du Temple | château | plaines, neige | 66×36×80 | 19 soldats, 14 archers, 2 portes, cloche, 4 citoyens | gabarit |
| `ati_structures:catalonian_castle` | Château catalan | château | variée | 42×32×35, 3 pièces | 17 pillards, 6 vindicateurs, 4 illusionnistes, 8 zombies momifiés, ravageur, évocateur | gabarit |
| `ati_structures:ruined_castle` | Château en ruine | château | plaines, forêt, taïga, neige | 48×45×48 | 47 zombies, évocateur, illusionniste, cloche | gabarit |
| `ati_structures:marble_chateau` | Château de marbre | château | plaines, forêt, taïga, savane, jungle | 48×48×48 | 50 zombies momifiés | gabarit |
| `minecraft:mansion` | Manoir forestier | château | forêt sombre | généré par le jeu | vindicateurs, évocateurs (à vérifier avec `/place`) | gabarit |
| `mowziesmobs:frostmaw_spawn` | Antre du Frostmaw | repaire | neige | généré par le mod | Frostmaw (à vérifier) | maître |
| `ati_structures:wither_cavern` | Caverne du Wither | repaire | variée | 45×45×45 | 21 wither squelettes, cristal de l'End | gabarit |
| `minecraft:bastion_remnant` | Vestiges de bastion | fort | **Nether** | 46×24×46, 167 pièces possibles | piglins, brutes piglins, hoglins (pièces « mobs »), cage à cubes de magma | gabarit + complément |

### 7.7 Niveau VII — Légende (12)

| Identifiant | Nom | Type | Terrain | Taille | Occupants lus | Défenseurs |
|---|---|---|---|---|---|---|
| `valarian_conquest:barathian_castle` | Citadelle barathienne | château | plaines, neige | 120×52×96 | 37 soldats, 1 archer, 9 balistes, portes, 7 citoyens | gabarit |
| `valarian_conquest:orleanian_castle` | Forteresse d'Orléans | château | plaines, neige | 114×64×115 | 29 soldats, 1 archer, 13 balistes, pont-levis, cloche, 6 citoyens | gabarit |
| `ati_structures:wither_keep` | Donjon du Wither | château | variée | 30×47×17 | 20 wither squelettes | gabarit |
| `ati_structures:herobrine_stronghold` | Bastion de Herobrine | château | variée | 48×48×48 | 34 zombies momifiés, ravageur, wither squelette | gabarit |
| `mostructures:killer_bunny_castle` | Château du lapin tueur | château | plaines, forêt, forêt sombre, neige, savane | 32×35×32 | lapin (tueur), cages | gabarit + complément |
| `mostructures:the_castle_in_the_sky` | Château dans le ciel | château | plage (flotte au-dessus) | 48×24×48, 25 pièces, **flottant** | cages (8 zombies, 4 squelettes, 6 araignées venimeuses, 1 zombie-villageois) | ajoutés + cages |
| `irons_spellbooks:citadel` | Citadelle maudite | château | **Nether** | 51×70×51, 35 pièces | 12 armures maudites (`irons_spellbooks:cursed_armor_stand`) | gabarit + complément |
| `taxov:illager_king_ship` | Vaisseau du roi pirate | abordage | mer, mer froide | 36×105×120 | 49 pillards, 48 vindicateurs, 4 évocateurs, 4 illusionnistes, ravageur, roi pirate illageois | gabarit (chef : le roi) |
| `ati_structures:ancient_vessel` | Vaisseau ancien | abordage | mer, mer froide | 43×35×19 | Warden, 1 villageois | gabarit (maître : Warden) |
| `mowziesmobs:monastery` | Monastère du Sculpteur | repaire | sommets | généré par le mod | Sculpteur (à vérifier) | maître |
| `ati_structures:warden_monument` | Monument du Warden | repaire | neige, sommets | 48×48×48 | Warden | maître |
| `minecraft:end_city` | Cité de l'End | ville | **End** (hautes terres des îles extérieures) | générée par le jeu | shulkers (posés par la génération, à vérifier) ; élytres de la nef retirées à la pose | gabarit + complément |

### 7.8 Villes fortifiées (niveau V)

Une ville = un **village de style agrandi** (`place jigsaw` sur son pool de départ, profondeur
10 à 12 au lieu de 3 à 6) + **3 avant-postes d'une faction** sur les routes qui en sortent + un
**siège du pouvoir** du côté opposé au camp. Les occupants d'origine des pièces ajoutées sont
remplacés par ceux de la faction de la ville. Objectifs : prendre les tours (garnison + bannière
de chacune), abattre le bailli (chef), tenir la place (cloche). « Au hasard » combine un style,
une faction et un siège.

| Ville (nom proposé) | Pool de départ du village | Tours | Siège du pouvoir |
|---|---|---|---|
| Brumeval | `kaisyn:village/exclusives/tudor/town_centers` | `valarian_conquest:templar_outpost` | `ati_structures:manor` |
| Skarholm | `kaisyn:village/exclusives/swedish/town_centers` | `valarian_conquest:visgothian_outpost` | `ati_structures:woodland_keep` |
| Vila Real | `kaisyn:village/exclusives/iberian/town_centers` | `valarian_conquest:burgundian_outpost` | `ati_structures:castillo` |
| Portocalda | `kaisyn:village/exclusives/mediterranean/town_centers` | `valarian_conquest:hospitaller_outpost` | `ati_structures:old_residence` |
| Kemet | `kaisyn:village/exclusives/nilotic/base_plate` | `valarian_conquest:barathian_outpost` | `ati_structures:fortified_temple` |
| Haut-Bois | `kaisyn:village/exclusives/rustic/town_centers` | `valarian_conquest:valarian_outpost` | `medieval_buildings:fort` |
| Valclaire | `kaisyn:village/exclusives/classic/town_centers` | `valarian_conquest:bohemian_outpost` | `ati_structures:manor` |
| Hautefroide | `kaisyn:village/snowy_taiga_viking/town_centers` | `valarian_conquest:orleanian_outpost` | `ati_structures:dark_keep` |

Requiert Towns and Towers, Valarian Conquest et le mod du siège. Si `place jigsaw` n'agrandit pas
le village (R0), repli : la structure du village (`towns_and_towers:exclusives/village_<style>`)
telle quelle.

### 7.9 Structures écartées (52)

| Mod | Structures | Raison |
|---|---|---|
| Minecraft (23) | `ancient_city`, `buried_treasure`, `desert_pyramid`, `igloo`, `jungle_pyramid`, `mineshaft`, `mineshaft_mesa`, `monument`, `nether_fossil`, `ocean_ruin_cold`, `ocean_ruin_warm`, `ruined_portal` (7 variantes), `shipwreck`, `shipwreck_beached`, `stronghold`, `trail_ruins`, `trial_chambers` | souterrain géant, temples piégés, épaves et ruines vides, mécanique propre |
| ATi Structures (9) | `deepslate_keep` (5 marchands), `lighthouse`, `monastery_tower`, `mosque`, `old_home`, `pufferfish_cave`, `rotten_log`, `sculk_deposit`, `underwater_dungeon` | vides, trop petits ou sous l'eau |
| Mo' Structures (10) | `air_balloon`, `barn_house`, `desert/plains/savanna/snowy/taiga_abandoned_church`, `jungle_pyramid`, `lighthouse`, `moai` | décors sans occupants |
| Iron's Spells (1) | `ancient_battleground` | ruines (4 piglins) |
| Medieval Buildings (2) | `house_1`, `house_2` | maisons isolées |
| Towns and Towers (2) | `mimic_desert`, `wreckage_ocean` | piège isolé, épave |
| Tax' Ocean Villager (1) | `marine_ship` | **camp allié de l'Abordage** |
| Supplementaries (1) | `road_sign` | panneau |
| Let's Do Brewery (1) | `brewfest_tent` | réservée aux fêtes (phase 7) |
| Fish of Thieves (2) | `guardian_fruit_tree`, `seapost` | décors |

À mettre dans `ignorees` de `raids.yml` (pour que `/city raid inconnues` ne les signale pas).

---

## 8. Architecture du code

### 8.1 Paquet `com.citycore.raid`

| Classe | Rôle |
|---|---|
| `RaidConfig` | section `raids` de `config.yml` : monde, rassemblement, joueurs, niveaux, récompenses |
| `RaidCatalog` | lit `raids.yml` (cibles, villes, ignorées), vérifie la disponibilité (§3.2) |
| `RaidTarget` (record) | une cible : id, nom, type, niveau, structures (id + rôle), défenseurs, objectifs, poids, mods |
| `RaidWorld` | mondes surface, Nether, End (création, règles, remise à zéro), préparation, tickets |
| `RaidSlots` | grille d'emplacements, classement par famille de terrain (cache), choix d'un emplacement cohérent avec la cible, contrôle du relief |
| `TerrainFamilies` | familles de biomes (`raids.familles`) et terrain de chaque cible |
| `StructurePlacer` | `place structure / jigsaw / template` via `execute in`, retour capturé, repli API Paper |
| `ArenaScanner` → `Arena` (record) | balayage (§2.7) |
| `RaidManager` (Listener) | états `IDLE → PREPARATION → RASSEMBLEMENT → DEPLOIEMENT → ASSAUT → BILAN → RETOUR`, défiance, chrono, barre, récompenses, recharges |
| `RaidObjectives` | objectifs (§5.2), poids, détection |
| `RaidDefenders` | complément, remplacement, postes, renforts (`EnemyArmy`/`Squad`), chef, mise à l'échelle |
| `RaidParticipants` | enrôlement, retours, mort (inventaire gardé, réapparition au camp), déconnexions, garde des téléportations, commandes bloquées |
| `RaidHerald` | héraut du camp (Citizens, registre en mémoire) |
| `ui.screens.RaidScreens` | Événements, Difficulté, Cibles, Fiche (+ repli classique) |

### 8.2 Réutilisé

`EnemyArmy`, `Squad`, `SiegeConfig.Faction` (factions), `SiegeSounds`, la barre rouge et le
champion de `SiegeManager`, `QuestHUD`, `TypewriterUtil`, `ModIntegrations`
(`structureExists`, détection), `DatabaseManager`, `ConfigUpdater`, `UiKit` / `UiCanvas` /
`ValmontMenu`, `NPCManager` (suivi d'Alderic).

### 8.3 Fichiers existants à modifier

- `CityCore.java` : création et câblage.
- `ui/screens/MayorScreens.java` : bouton « Événements », « Agrandir » déplacé dans Trésor.
- `npc/mayor/MayorGUI.java` : repli classique et emplacement du bouton.
- `command/CityCommand.java`, `CitySubCommand.java`, `CityTabCompleter.java`, `CityPermissions.java`.
- `util/ConfigUpdater.java` (version 30, et fusion de `raids.yml` par version), `util/DatabaseManager.java`.
- `integration/ModIntegrations.java` (3 mods), `resources/config.yml` (`raids`, `mod-integrations`),
  `resources/integration/mod-labels.yml` (noms des créatures nouvelles si besoin).
- `siege/SiegeManager.java`, `siege/EventCalendar.java` : exclusion mutuelle siège / raid, report.
- `siege/EnemyArmy.java` : fonctionnement dans un autre monde, objectif fourni.
- `quest/QuestHUD.java` : entrée « raid en cours ».
- Pack de ressources et `tools/ui-art` : nouveaux dessins (§8.7).
- `docs/INTEGRATION_MODS.md` : les 3 nouveaux mods et les raids.

### 8.4 Base de données (SQLite)

| Table | Colonnes |
|---|---|
| `raid_returns` | `uuid` (clé), `world`, `x`, `y`, `z`, `yaw`, `pitch`, `raid_id`, `started_at` |
| `raid_results` | `id`, `cible`, `niveau`, `resultat`, `joueurs`, `duree`, `defiance_finale`, `bonus`, `date` |
| `raid_state` | `niveau` (clé), `victoires`, `renommee`, `recharge_jusqu_a` |
| `raid_slots_used` | `monde`, `index` (clé composée) : emplacements déjà utilisés, utile seulement avec `reinitialiser-au-demarrage: false` |

Le classement des emplacements par famille est dans `plugins/CityCore/raids-emplacements.yml`
(clé = graine et espacement), pas en base.

### 8.5 Configuration

`config.yml` (version 30), extrait :

```yaml
raids:
  active: true
  mondes:
    surface: {nom: citycore_raids, graine: 20261021}
    nether: {active: true, nom: citycore_raids_nether, graine: 20261022}
    end: {active: true, nom: citycore_raids_end, graine: 20261023}
    reinitialiser-au-demarrage: true
    espacement: 1024
    rayon-prepare: 160
    heure: 6000
    difficulte: NORMAL
  terrain:
    dominance: 0.8          # part des points de l'emplacement dans la même famille
    relief-max: 16          # écart de hauteur toléré dans l'emprise
    prets-au-demarrage: [plaines, foret, neige, mer]
  familles:                 # biomes de chaque famille (§2.4) ; « variee » est un raccourci
    plaines: [plains, sunflower_plains]
    desert: [desert]
    # … toutes les familles du §2.4
    variee: [plaines, foret, foret-sombre, taiga, neige, sommets, savane, jungle, badlands]
  camp:
    distance: 32            # blocs entre le bord de la cible et le camp (point de réapparition)
  rassemblement: {rayon: 10, secondes: 30, joueurs-min: 1}
  joueurs:
    garder-inventaire: true # décision : inventaire et expérience gardés à la mort
    invulnerable-reapparition-secondes: 3
    commandes-bloquees: [sethome, home, homes, back, spawn, tpa, tpahere, tpaccept, warp, tp]
  niveaux:
    1: {nom: "Escarmouche", chapitre: 2, defenseurs: [3, 6], chrono-minutes: 6, renforts: 0, recharge-minutes: 30, caisse: 100, joueur: 15}
    # … 2 à 7 selon §4.1 et §6.1
  recompenses: {bonus: 0.15, renommee-par-victoire: 0.05, renommee-max: 0.25}
```

`raids.yml` (catalogue, copié au premier démarrage puis complété par version), extrait :

```yaml
version: 1
cibles:
  commanderie-du-temple:
    nom: "La Commanderie du Temple"
    type: chateau
    niveau: 6
    avec-mod: valarian_conquest
    terrain: [plaines, neige]
    structures: [{id: "valarian_conquest:templar_castle", role: centre}]
    defenseurs: {source: gabarit}
    objectifs: [porte, bannieres, garnison, chef, tenir]
  hameau-de-brumeval:
    nom: "Le hameau de Brumeval"
    type: village
    niveau: 3
    avec-mod: t_and_t
    terrain: [plaines, prairie]
    structures: [{id: "towns_and_towers:exclusives/village_tudor", role: centre}]
    defenseurs: {source: armee, nombre: 8}
    objectifs: [garnison, cloche, {bonus: epargner}]
  citadelle-maudite:
    nom: "La citadelle maudite"
    type: chateau
    niveau: 7
    avec-mod: irons_spellbooks
    terrain: [nether]
    structures: [{id: "irons_spellbooks:citadel", role: centre}]
    defenseurs: {source: gabarit, complement: true}
    objectifs: [garnison, cages, chef, tenir]
  vaisseau-du-roi-pirate:
    nom: "Le vaisseau du roi pirate"
    type: abordage
    niveau: 7
    avec-mod: taxov
    terrain: [mer, mer-froide]
    structures: [{id: "taxov:illager_king_ship", role: centre}]
    defenseurs: {source: gabarit, chef: "taxov:illager_pirate_king"}
    objectifs: [aborder, garnison, bannieres, chef]
villes:
  brumeval:
    terrain: [plaines, prairie]   # celui du style du village
    village: {pool: "kaisyn:village/exclusives/tudor/town_centers", profondeur: 10}
    tours: {id: "valarian_conquest:templar_outpost", nombre: 3}
    siege: "ati_structures:manor"
    avec-mod: "t_and_t,valarian_conquest,ati_structures"
ignorees: ["minecraft:ruined_portal*", "minecraft:shipwreck*", "mostructures:*_abandoned_church"]
```

### 8.6 Commandes et permissions

- Joueurs (`citycore.player`) : `/city raid` (raid en cours, niveaux débloqués, recharges),
  `/city raid quitter` (retraite).
- Administration (`citycore.admin.raid`) : `start <cible> [ici]` (rassemblement autour de
  l'administrateur avec `ici`), `stop`, `next` (objectif suivant), `poser <structure>
  [famille]` (essai de pose sur un emplacement de la famille donnée, sinon de son terrain ; R0),
  `emplacements` (nombre d'emplacements classés par famille et par monde, emplacements prêts),
  `tp [emplacement]`, `purge`, `catalogue [niveau]`, `inconnues`, `recharge reset`,
  `debloquer <niveau>`.

### 8.7 Écrans et dessins

Fonds : Événements, Difficulté, Cibles, Fiche. Tuiles : Raids, Sièges, Fêtes (verrouillée), 7
niveaux (chiffres romains, cadenas). Icônes des 8 types et « Tous ». Cartes de cible, flèches de
pages, bouton « Au hasard », bouton « Sonner le rassemblement » (actif / grisé). Repli classique
dans `MayorGUI`.

---

## 9. Phases

### R0 — Prototype technique (administration seulement)

**À coder** : `RaidWorld` minimal pour le monde de surface (création, règles, remise à zéro au
démarrage, `purge`) ; `RaidSlots` et `TerrainFamilies` : classement de la grille par famille de
terrain (§2.4, cache), choix d'un emplacement de la famille demandée, contrôle du relief ;
préparation asynchrone ; `/city raid poser <structure> [famille]` (pose via `execute in`, retour
capturé ; famille par défaut : la première du terrain de la structure au §7) ;
`/city raid emplacements` ; `/city raid tp` ; journal du balayage dans la console (occupants par
clé, cloches, bannières, cages, portes, taille).

**À tester en jeu** :
1. Le monde se crée ; il apparaît dans `youer-config/worlds.yml` ; au redémarrage il est effacé et
   recréé sans erreur.
2. `/city raid emplacements` : durée du classement, nombre d'emplacements par famille (les
   familles rares, champignons et badlands, sont-elles présentes ?) ; le biome lu sans générer de
   chunk correspond-il au biome réel une fois sur place ?
3. Poser et chronométrer, chacune sur son terrain : `towns_and_towers:exclusives/village_tudor`,
   `valarian_conquest:templar_castle`, `valarian_conquest:orleanian_castle`, `taxov:pirate_ship`
   (mer), `taxov:illager_king_ship` (mer), `mowziesmobs:wrought_chamber`, `minecraft:mansion`,
   `mostructures:villager_market`, `irons_spellbooks:pyromancer_tower`,
   `irons_spellbooks:catacombs`, `minecraft:village_desert` (désert), `ati_structures:ice_tribe`
   (banquise).
4. Les soldats de Valarian Conquest attaquent-ils un joueur sans citoyenneté ? Avec ? Les portes et
   le pont-levis se cassent-ils ? Les balistes tirent-elles ?
5. Le Wroughtnaut, les illageois du manoir, les mages d'Iron's apparaissent-ils ?
6. `place jigsaw` sur `kaisyn:village/exclusives/tudor/town_centers`, profondeur 10 : quelle
   taille ?
7. Ecliptic Seasons, JourneyMap, Tough As Nails dans ce monde ; un PNJ Citizens et son animation
   Epic Fight.

**Décisions de sortie** : monde Bukkit gardé ou repli datapack ; seuil de dominance et relief
maximal ; cibles à retirer ou à changer de niveau ; mise à jour du §7 et du §10.

### R1 — Monde, allers-retours et sécurité

**À coder** : `raids.mondes`, `raids.terrain`, `raids.familles`, `raids.camp` ; emplacements prêts
par famille et préparation à la demande avec progression (§2.5) ; camp à côté de la structure,
point de réapparition et héraut (§2.8) ; `RaidParticipants` (enrôlement, `raid_returns`,
`PlayerSpawnLocationEvent`, garde des téléportations, commandes bloquées, **mort : inventaire et
expérience gardés, réapparition au camp, 3 s d'invulnérabilité**, déconnexion) ; nettoyage ;
exclusion siège / raid ; `/city raid start <cible> ici` (sans écran ni objectifs : une cible au
choix, posée sur son terrain, retour au bout du chrono).

**À tester en jeu** : départ à 2 joueurs dont un hors du cercle ; une cible du désert arrive dans
le désert, une cible de neige dans la neige ; le camp est à côté de la structure, hors de portée
des archers ; mort (chute, noyade, monstre) → réapparition au camp avec tout l'inventaire et
l'expérience, sans rien laisser au sol ; mort pendant le bilan ; un lit utilisé dans le monde des
raids ne change pas le point d'apparition ; déconnexion et retour ; arrêt du serveur en plein raid
puis reconnexion (retour à la position d'origine) ; `/home`, `/tpa` bloqués ; retraite par le
héraut ; plus aucune entité du raid après la fin ; siège impossible pendant un raid.

### R2 — Catalogue et vérification des mods

**À coder** : `raids.yml` complet (168 cibles du §7 avec leur `terrain`, 8 villes, `ignorees`),
`RaidCatalog`, 3 mods dans `ModIntegrations` et `mod-integrations`, contrôles et bilan au
démarrage (dont le terrain : §3.2), `/city raid catalogue`, `/city raid inconnues`, ligne Raids
dans `/city mods`, `ConfigUpdater` (version 30).

**À tester en jeu** : bilan console ; `mod-integrations: t_and_t: off` → 58 cibles masquées ;
`raids.mondes.nether.active: false` → 3 cibles masquées ; `/city raid inconnues` vide ; fusion de
`config.yml` sur le serveur de test.

### R3 — Écrans d'Alderic

**À coder** : bouton Événements (et Agrandir dans Trésor), écrans Événements, Difficulté, Cibles
(filtres, pages, au hasard), Fiche, repli classique, dessins.

**À tester en jeu** : parcours complet avec et sans pack ; cadenas et conditions ; compte en direct
des joueurs dans le cercle ; bouton grisé pendant une recharge.

### R4 — L'assaut (niveaux I à III, terre)

**À coder** : défiance, objectifs `garnison`, `cloche`, `bannieres`, `cages`, `liberer`, bonus ;
chrono, barre, panneau de quêtes ; victoire (capitulation), défaite ; récompenses, renommée,
recharges, déblocage ; anti-exploitation (§6.3) ; sons.

**À tester en jeu** : une cible de chaque type des niveaux I à III ; victoire, défaite (chrono
réduit) ; bonus épargner ; aucun butin, coffres vides, commerce bloqué ; recharge et déblocage du
niveau suivant.

### R5 — Défenseurs, renforts, chefs (niveaux IV à VII, terre)

**À coder** : complément et remplacement d'occupants, postes, mise à l'échelle par joueurs,
renforts par paliers de défiance, chef, objectifs `porte`, `chef`, `tenir`.

**À tester en jeu** : niveaux IV à VII sur des cibles de Valarian, ATi et Mo' Structures ; un raid
à 1 puis 3 joueurs ; temps de pose des plus grosses.

### R6 — Villes fortifiées

**À coder** : villages agrandis, tours sur les routes, siège du pouvoir, remplacement des
occupants, objectifs des villes, ville au hasard.

### R7 — Abordage

**À coder** : emplacements mer et mer froide, navire allié (camp et réapparition sur le pont),
barques, objectif `aborder`, capitaine, pavillon, roi pirate.

### R8 — Repaires

**À coder** : objectif `maitre`, pose du maître s'il manque, accès aux repaires souterrains (camp
en surface), repaire en mer (camp sur le navire allié).

### R9 — Raids du Nether et de l'End

**À coder** : mondes `citycore_raids_nether` et `citycore_raids_end` (créés à la première demande,
effacés au démarrage), classement de leurs emplacements (Nether : biomes du Nether ; End : hautes
terres des îles extérieures, loin de (0, 0)), camps (basalte, obsidienne), cibles
`minecraft:fortress`, `minecraft:bastion_remnant`, `irons_spellbooks:citadel`, `minecraft:end_city`
(élytres de la nef retirées).

**À tester en jeu** : création des deux mondes sous Youer ; pose des quatre cibles et de leurs
occupants (shulkers de la cité, piglins du bastion) ; aucun combat du dragon ni portail ; mort et
réapparition sur la plateforme du camp.

### R10 — Finitions

Dialogues d'Alderic et du héraut, trophées, succès dans l'écran des progrès (touche L),
équilibrage des récompenses, documentation joueurs (site), mise à jour de
`docs/INTEGRATION_MODS.md`.

---

## 10. Points incertains (à vérifier en jeu)

1. `WorldCreator` sous Youer : création à chaud, et effacement au démarrage si Youer charge le
   monde avant le plugin.
2. Clé du monde pour `execute in`, retour de `place` capturé par `createCommandSender`.
3. `place structure` pour les structures générées par code (`minecraft:mansion`,
   `mostructures:*` de type `mostructures:generic`, `mowziesmobs:*`, `supplementaries:galleon`) et
   leurs occupants (illageois du manoir, boss de Mowzie's, Umvuthana).
4. `place jigsaw` : arguments, profondeur maximale et rayon en 1.21.1.
5. Soldats de Valarian Conquest : hostilité (citoyenneté), combats entre factions, portes cassables,
   balistes.
6. Lecture du biome d'un chunk non généré (`World#getBiome`) pour classer les emplacements, durée
   du classement de la grille, présence des familles rares (champignons, badlands) dans la grille ;
   temps de préparation d'un emplacement à la demande.
7. Navires : hauteur de pose en mer ; brise-glace empalé ; camp sur le vaisseau de la Marine.
8. Repaires souterrains : accès.
9. Temps de pose des plus grosses cibles (citadelle barathienne 120×52×96, forteresse d'Orléans,
   citadelle maudite, vaisseau du roi pirate 36×105×120, château dans le ciel).
10. Les autres mods dans ce monde (§2.11) ; PNJ Citizens et animations Epic Fight.
11. Occupants de `irons_spellbooks:pyromancer_tower`, `ice_spider_den` et `catacombs` (pools non
    lus hors jeu).
12. Nombre réel d'occupants des structures en plusieurs pièces.
13. Mondes du Nether et de l'End créés par `WorldCreator` sous Youer ; combat du dragon d'un monde
    de l'End supplémentaire ; shulkers de la cité de l'End posés par `/place`.
14. Réapparition : un `PlayerRespawnEvent` qui fixe la position suffit-il sous Youer avec les mods
    qui touchent à la mort (Epic Fight, Tough As Nails) ?

## 11. Choix

### Tranchés (21/10/2026)

- **Terrain** : cohérent avec la structure de chaque raid (§2.4) ; naturel, graine fixe ; mondes du
  Nether et de l'End pour les cibles qui en viennent.
- **Mort dans le monde des raids** : inventaire (et expérience) conservés ; réapparition au camp de
  départ du raid, à côté de la structure (§2.8, §2.9) ; pas de limite de vies.

### Par défaut (à confirmer)

1. Lancement : tout joueur ayant le chapitre et le déblocage ; 1 joueur minimum.
2. « Événements » remplace « Agrandir la ville » sur l'accueil d'Alderic ; « Agrandir » passe dans
   l'écran Trésor.
3. Coffres vidés, aucun butin, blocs cassés sans objet.
4. Occupants ajoutés : soldats des factions des sièges ; pillards pour les avant-postes et la
   fabrique des pillards ; créatures du mod pour les repaires.
5. Récit : villages et villes « occupés » par une faction ennemie ; civils à épargner (bonus).
6. Déblocage : chapitre + une victoire au niveau précédent.
7. Un seul raid à la fois ; pas pendant un siège ; un siège prévu pendant un raid est reporté de
   30 min.
8. Les morts en raid comptent pour les quêtes de chasse et les récits.
9. Recharge par niveau pour tout le serveur, moitié en cas de défaite.
10. Une seule barre rouge (défiance + chrono) ; objectifs dans le panneau de quêtes.
11. Cibles du Nether et de l'End (4) gardées au catalogue ; à retirer si les mondes
    supplémentaires posent problème (`raids.mondes.nether/end.active: false`).
12. Familles rares absentes de la grille : la cible est masquée plutôt que posée ailleurs.
