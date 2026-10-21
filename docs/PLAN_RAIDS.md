# Plan « Raids » — les expéditions de Valmont

Conception arrêtée le 21/10/2026 ; R0, R1 et R2 codées le 21/10/2026 (tests en jeu à faire, §12).
Ce document se suffit à lui-même : chaque phase (R0 à R10) se commande dans un nouveau prompt,
par exemple :

> Lis `docs/PLAN_RAIDS.md` et code la phase R2. Respecte les règles communes, termine par la
> liste « À tester en jeu » et mets à jour le tableau d'avancement.

Documents liés : `docs/PLAN_VILLE_VIVANTE.md` (sièges, garde, besoins, calendrier),
`docs/INTEGRATION_MODS.md` (détection des mods, identifiants sous Youer),
`docs/TRAME_ET_ECONOMIE.md` (chapitres, économie).

## Avancement

| Phase | Contenu | Dépend de | Taille | État |
|---|---|---|---|---|
| R0 | Prototype technique : monde des raids, pose des structures, observation | — | petite | codée, test en jeu à faire |
| R1 | Monde, allers-retours et sécurité des joueurs | R0 | grosse | codée, test en jeu à faire |
| R2 | Catalogue des cibles et vérification des mods | R0 | moyenne | codée, test en jeu à faire |
| R3 | Écrans d'Alderic : Événements → Raid → Difficulté → Cibles → Fiche | R1, R2 | moyenne | codée, test en jeu à faire |
| R4 | L'assaut : défiance, objectifs, chrono, victoire, récompenses (niveaux I à III, terre) | R3 | grosse | codée, test en jeu à faire |
| R5 | Défenseurs ajoutés, renforts, chefs (niveaux IV à VII, terre) | R4 | moyenne à grosse | codée (sans trophées), test en jeu à faire |
| R6 | Villes fortifiées (villages agrandis + tours + siège du pouvoir) | R5 | moyenne | codée (refaite sans `/place`), test en jeu à faire |
| R7 | Abordage (navires, emplacements en mer) | R5 | moyenne | codée, test en jeu à faire |
| R8 | Repaires (maîtres, cages, souterrains) | R5 | moyenne | codée, test en jeu à faire |
| R9 | Raids du Nether et de l'End (mondes `citycore_raids_nether` et `citycore_raids_end`) | R5 | petite à moyenne | codée, test en jeu à faire |
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
- Tout retour hors de cette fin normale (connexion perdue, partie quittée, plantage, arrêt du
  serveur, remise à zéro du monde) se fait **auprès d'Alderic** (décision du 21/10/2026, §2.9).

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
  (config), **`generateStructures(true)`** (décision du 21/10/2026 : les cibles naissent de la
  génération, §2.4), `keepSpawnLoaded(false)`, `setAutoSave(false)` (le monde est jetable).
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
- À l'arrêt du serveur (décision du 21/10/2026 : ne rien garder des chunks), le monde est jeté :
  joueurs renvoyés au monde principal, `Bukkit.unloadWorld(monde, false)` (le serveur n'écrit pas
  ses chunks en s'arrêtant), dossier effacé. Si le déchargement est refusé, l'effacement du
  démarrage suivant prend le relais. Un joueur qui se reconnecte dans un monde des raids hors
  d'un raid revient au monde principal (`PlayerSpawnLocationEvent`).
- `/city raid purge` (admin, hors raid) fait la même chose à chaud.
- Avec `reinitialiser-au-demarrage: false`, l'index du prochain emplacement est gardé en SQLite
  (`raid_state`) pour ne jamais reprendre un emplacement usé.

### 2.4 Génération naturelle et repérage (décision du 21/10/2026)

Les premiers essais de R0 posaient les cibles par `/place structure` dans un terrain déjà généré,
sur une grille d'emplacements classés par famille de biomes : résultat jugé mauvais en jeu
(blocs chaotiques, structures qui flottent ou entaillent les collines). L'adaptation au terrain
d'une structure (remblai, enfouissement) ne se fait que pendant la génération du terrain ;
`/place jigsaw` n'agrandit pas non plus les villages (§7.8). **Décision** : utiliser la
génération des mods eux-mêmes.

- Le monde des raids génère ses structures **comme un monde neuf** : chaque cible y naît telle que
  son mod la fait apparaître (biomes autorisés, espacement, réglages de Structurify et Sparse
  Structures compris), adaptée au relief, avec les entités de ses gabarits. Le terrain est donc
  toujours celui de la structure ; la colonne « Terrain » du §7 devient indicative.
- **Repérage** : `World#locateNearestStructure(départ, structure, rayon, true)` (la recherche de
  `/locate`) trouve l'exemplaire naturel **non exploré** le plus proche d'un point de départ neuf
  (x = 10 000 + n × `espacement`, z = 10 000 ; `rayon-recherche` 100 chunks). L'exemplaire trouvé
  reçoit une référence : il ne ressort plus avant la remise à zéro du monde. La recherche se fait
  sur le fil principal mais ne génère que l'amorce des chunks candidats ; sa durée est notée.
  Constaté sous Youer (21/10/2026) : l'API a rendu « rien » en 0 ms, même pour
  `minecraft:village_plains`. Repli : la commande du jeu `/locate structure`, lancée dans le monde
  des raids (`execute in … positioned … run locate structure <id>`), retour capturé et coordonnées
  lues dans son lien « /tp @s x ~ z » ; les exemplaires déjà pris (que `/locate` ne marque pas)
  sont écartés par le plugin, le point de départ s'éloignant à chaque essai. Sous Youer, la
  commande s'exécute un peu après son envoi : sa réponse est attendue (60 s au plus). Coût
  constaté : 3,1 s du fil principal pour `minecraft:village_plains` (journal « Locating element
  … took … ms ») — pour R1, repérer les sites d'avance, au démarrage ou quand le serveur est
  calme, plutôt qu'au moment du rassemblement.
- **Sans placement** : au démarrage, chaque cible est contrôlée auprès du générateur
  (`ChunkGeneratorStructureState#getPlacementsForStructure`, par réflexion) ; une structure dont
  aucun biome n'existe dans le monde n'a aucun placement et ne naîtra jamais : cible masquée
  (« ne naissent pas sur ce serveur »). Constaté le 21/10/2026 : les 16 villages et avant-postes
  « de style » de Towns and Towers (`exclusives/…`) ne naissent que dans des biomes de
  Terralith, Biomes O' Plenty, Wythers, Biomes We've Gone, Regions Unexplored ou Tectonic,
  absents du serveur (recherche rendue en 0 ms) ; les villes fortifiées qui en dépendent aussi.
- **Introuvable** (biome trop rare dans le rayon) : la cible est masquée jusqu'au redémarrage
  (« introuvable dans le monde des raids »).
- Abandonnés : la grille de 4 096 emplacements, les familles de terrain (`raids.familles`), le
  classement des biomes et son cache, le contrôle du relief, `/place structure | jigsaw`.

### 2.5 Génération du site

- Chunk de départ de la structure d'abord, puis tous les chunks de sa **boîte** (connue une fois
  le départ généré : `World#getStructures(x, z, structure)` → `GeneratedStructure`) élargie de
  `marge` (32 blocs : abords, camp).
- **Toujours en tâche de fond, jamais sur le fil principal** : tickets de chargement du jeu
  (`ServerChunkCache#addRegionTicket`, ticket `FORCED` au niveau 33, posés par réflexion ; noms
  Spigot `a`/`b` sous Youer), 16 chunks demandés à la fois ; sinon `getChunkAtAsync` ; sinon refus.
  Constaté le 21/10/2026 : sous Youer, `getChunkAtAsync` ne rend rien pour un terrain neuf, et
  générer sur le fil principal a figé le serveur jusqu'à son arrêt par le watchdog.
- Tickets du plugin sur les chunks du site pendant le raid, retirés au nettoyage (en R0 : dès la
  fin du balayage).
- Piste (R1) : sites préparés d'avance pour les cibles les plus demandées, pendant que le
  serveur est calme.

### 2.6 Pose de structures

Plus aucune pour les cibles. Les villes fortifiées (§7.8, R6) demandent d'ajouter des tours et un
siège du pouvoir à un village : à revoir en R6 (structure « ville » déclarée par datapack et
générée naturellement, ou pièces ajoutées), sans `/place` dans un terrain déjà généré.

### 2.7 Balayage de la cible → `Arena`

Après la génération du site, à partir de la structure enregistrée par le jeu
(`GeneratedStructure`) :

- **Boîte** de la cible : celle de la structure ; **pièces** : celles de la structure
  (`getPieces`). Les points remarquables ne sont cherchés que dans les pièces (lecture de
  `ChunkSnapshot` hors du fil principal), jamais dans le terrain autour.
- **Points remarquables** : cloches, bannières (dont `valarian_conquest:*_hanging_banner`), cages
  (`minecraft:spawner`), coffres, tonneaux et pots, portes, lits ; entrées et points hauts
  accessibles (postes d'archers) en R5 ; centre (cloche, sinon centre de la boîte au sol).
- **Entités** de la boîte, classées par clé de type (`EntityType#getKey`, valable pour les
  créatures des mods sous Youer) :
  - **défenseurs** : monstres hostiles, soldats et archers de Valarian Conquest
    (`valarian_conquest:soldier`, `archer`), balistes (`valarian_conquest:ballista`), mages
    d'Iron's, boss de Mowzie's → étiquette `citycore.raid` + enregistrement ;
  - **civils** : villageois, citoyens de Valarian (`male_citizen`, `female_citizen`), marchands,
    allays → étiquette `citycore.raid.civil` ;
  - **à retirer** : golems de fer et de neige ;
  - **animaux** (ceux du terrain) et **décor** : cadres, porte-armures, tableaux.
- Les portes de Valarian Conquest sont des **entités** (`wooden_gate`, `gate_entity`,
  `drawbridge`).
- Les apparitions propres à certaines structures (pillards des avant-postes, sorcière de la hutte,
  gardiens) passent par l'apparition naturelle des créatures, coupée dans ce monde : le plugin les
  remplace (§5.3).
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
- **Reconnexion** : raid en cours → camp ; sinon → **auprès d'Alderic** (décision du 21/10/2026 :
  connexion perdue, partie quittée, plantage ou redémarrage, le retour se fait au point du maire,
  à deux pas de lui, face à lui ; point d'apparition du monde principal s'il n'y a pas encore de
  maire). Après un plantage, le monde est recréé : `PlayerSpawnLocationEvent` envoie auprès
  d'Alderic tout joueur dont la position enregistrée est dans le monde des raids. De même pour
  les joueurs évacués avant un déchargement (arrêt du serveur, `/city raid purge`) et pour une
  mort dans ce monde hors d'un raid.
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
4. pour les villes : le village et les pièces ajoutées existent ;
5. pour une cible du Nether ou de l'End, ce monde est activé. Qu'un exemplaire naisse bien dans le
   monde des raids ne se sait qu'à sa recherche (§2.4 : introuvable → masquée jusqu'au
   redémarrage).

Une cible incomplète est **masquée** (pas d'erreur). Console : une ligne de bilan, par exemple
`⚔ Raids : 168 cibles et 8 villes, 175 disponibles ; 1 masquée (Supplementaries absent : Galion)`.
Un mod **présent** dont une structure attendue manque (mod mis à jour) : avertissement qui la nomme.

### 3.3 Commandes de contrôle

- `/city mods` : une ligne « Raids : N cibles » par mod.
- `/city raid catalogue [niveau]` (admin) : cibles par niveau, disponibles ou non et pourquoi.
- `/city raid inconnues` (admin) : structures enregistrées sur le serveur qui ne sont ni au
  catalogue ni dans `ignorees` (pour un nouveau mod de structures).

### 3.4 Ajouter un mod de structures

1. Le déclarer dans `ModIntegrations.Mod` (témoin = une de ses structures) et dans
   `mod-integrations`.
2. `/city raid inconnues` liste ses structures ; en faire l'essai (`/city raid essai <id>`),
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
  renforce aussi les défenseurs de +5 % de vie par victoire (plafond +25 % ; d'abord +10 % et
  +50 %, baissé après le test des raids légendaires).
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

> **Refaite le 21/10/2026** (journal §12, « R6 ») : sans `/place`, une ville est un village **né
> de la génération** (`village.naturels`, ou ceux des terrains de la ville) entouré par le plugin
> de ses tours et de son siège, posés comme des gabarits. Le texte ci-dessous décrit le projet
> d'origine (villages de style agrandis), abandonné.

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

**Constat R0 (21/10/2026)** : `place jigsaw` (profondeur 10) n'assemble que les rues et la place
(boîte 121×39×161, une cloche, un villageois, aucune maison). La commande n'applique pas
l'« expansion hack » des villages (`use_expansion_hack: true` de la structure) : sans lui, une
maison accrochée à une rue doit tenir dans la boîte de la rue, haute d'un ou deux blocs, et elle
est refusée. Piste proposée pour R6 : déclarer dans un datapack du plugin une structure « ville »
par style (copie de `towns_and_towers:exclusives/village_<style>` avec `size` 10 à 12 et
`max_distance_from_center` 128, sans `structure_set` donc jamais générée d'elle-même), posée par
`place structure`, qui garde l'expansion hack.

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
| `RaidWorld` | mondes surface, Nether, End (création avec structures naturelles, règles, remise à zéro, abandon à l'arrêt) |
| `RaidSites` | repérage d'un exemplaire naturel non exploré, génération du site en tâche de fond, tickets, essais |
| `LoadTickets` | tickets de chargement du jeu par réflexion (génération sans bloquer le fil principal) |
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

Les exemplaires déjà pris sont marqués dans le monde lui-même (référence de la structure) : rien en base.

### 8.5 Configuration

`config.yml` (version 32), extrait :

```yaml
raids:
  active: true
  mondes:
    surface: {nom: citycore_raids, graine: 20261021}
    nether: {active: true, nom: citycore_raids_nether, graine: 20261022}
    end: {active: true, nom: citycore_raids_end, graine: 20261023}
    reinitialiser-au-demarrage: true
    espacement: 4096        # blocs entre les points de départ de deux recherches
    rayon-recherche: 100    # chunks : recherche d'un exemplaire naturel (§2.4)
    marge: 32               # blocs générés autour de la boîte de la cible
    heure: 6000
    difficulte: NORMAL
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
- Administration (`citycore.admin.raid`) : `start <cible> [ici] [force]` (rassemblement autour
  de l'administrateur avec `ici` ; `force` : sans déblocage ni recharge), `stop`, `essai
  <structure>` (repérage d'un exemplaire naturel et génération de son site ; R0), `zones`
  (recherches et essais), `tp [essai]`, `retour`, `purge`, `catalogue [niveau]`, `inconnues`,
  `recharge` (recharges levées), `debloquer <niveau>` (victoires comptées en dessous). `next`
  (objectif suivant) n'a pas été repris. Pour les tests : `/city niveau [monter|descendre|<n>]`
  (`citycore.admin.ville`), chapitre de la ville.

### 8.7 Écrans et dessins

Fonds : Événements, Difficulté, Cibles, Fiche. Tuiles : Raids, Sièges, Fêtes (verrouillée), 7
niveaux (chiffres romains, cadenas). Icônes des 8 types et « Tous ». Cartes de cible, flèches de
pages, bouton « Au hasard », bouton « Sonner le rassemblement » (actif / grisé). Repli classique
dans `MayorGUI`.

---

## 9. Phases

### R0 — Prototype technique (administration seulement)

**À coder** (revu le 21/10/2026 : génération naturelle, §2.4) : `RaidWorld` pour le monde de
surface (création avec structures naturelles, règles, remise à zéro au démarrage, abandon à
l'arrêt, `purge`) ; `RaidSites` : repérage d'un exemplaire naturel non exploré, génération du
site en tâche de fond ; `/city raid essai <structure>`, `zones`, `tp`, `retour` ; journal du
balayage dans la console (occupants par clé, cloches, bannières, cages, portes, taille).

**À tester en jeu** :
1. Le monde se crée avec ses structures ; au redémarrage il est effacé et recréé sans erreur ;
   à l'arrêt, « déchargé sans sauvegarde et effacé ».
2. `/city raid essai <id>` : durée de la recherche (console « Recherche de … en N ms »), taille
   de la boîte, temps de génération du site ; la structure est-elle intacte, sur son terrain ?
3. Essais, chacun noté (durée, occupants) : `towns_and_towers:exclusives/village_tudor`,
   `valarian_conquest:templar_castle`, `valarian_conquest:orleanian_castle`, `taxov:pirate_ship`,
   `taxov:illager_king_ship`, `mowziesmobs:wrought_chamber`, `minecraft:mansion`,
   `mostructures:villager_market`, `irons_spellbooks:pyromancer_tower`,
   `irons_spellbooks:catacombs`, `minecraft:village_desert`, `ati_structures:ice_tribe`. Lesquelles
   sont introuvables dans un rayon de 100 chunks ?
4. Les soldats de Valarian Conquest attaquent-ils un joueur sans citoyenneté ? Avec ? Les portes et
   le pont-levis se cassent-ils ? Les balistes tirent-elles ?
5. Le Wroughtnaut, les illageois du manoir, les mages d'Iron's apparaissent-ils ?
6. (Fait le 21/10/2026) `place jigsaw` n'agrandit pas les villages (§7.8).
7. Ecliptic Seasons, JourneyMap, Tough As Nails dans ce monde ; un PNJ Citizens et son animation
   Epic Fight.

**Décisions de sortie** : rayon de recherche et espacement ; cibles introuvables à retirer ou à
remplacer ; cibles à changer de niveau ; mise à jour du §7 et du §10.

### R1 — Monde, allers-retours et sécurité

**À coder** : `raids.camp` ; sites préparés d'avance et repérage à la demande avec progression
(§2.4, §2.5) ; camp à côté de la structure,
point de réapparition et héraut (§2.8) ; `RaidParticipants` (enrôlement, `raid_returns`,
`PlayerSpawnLocationEvent`, garde des téléportations, commandes bloquées, **mort : inventaire et
expérience gardés, réapparition au camp, 3 s d'invulnérabilité**, déconnexion) ; nettoyage ;
exclusion siège / raid ; `/city raid start <cible> ici` (sans écran ni objectifs : une cible au
choix, repérée dans le monde des raids, retour au bout du chrono).

**À tester en jeu** : départ à 2 joueurs dont un hors du cercle ; une cible du désert arrive dans
le désert, une cible de neige dans la neige ; le camp est à côté de la structure, hors de portée
des archers ; mort (chute, noyade, monstre) → réapparition au camp avec tout l'inventaire et
l'expérience, sans rien laisser au sol ; mort pendant le bilan ; un lit utilisé dans le monde des
raids ne change pas le point d'apparition ; déconnexion et retour ; arrêt du serveur en plein raid
puis reconnexion (retour auprès d'Alderic, décision du 21/10/2026) ; `/home`, `/tpa` bloqués ;
retraite par le héraut ; plus aucune entité du raid après la fin ; siège impossible pendant un raid.

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

- **Retour de secours** : après une déconnexion, un plantage, un arrêt du serveur ou une remise à
  zéro du monde, le joueur revient auprès d'Alderic (§2.9).
- **Relief** : 50 blocs d'écart tolérés (80 pour les sommets) ; monde des raids jeté à l'arrêt du
  serveur, rien n'en est gardé (§2.3).

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

---

## 12. Journal de réalisation

### R6 — villes fortifiées refaites sans `/place`, codées le 21/10/2026 (tests en jeu à faire)

Le projet d'origine (village de style agrandi par `place jigsaw`) reposait sur `/place`,
abandonné ; les villages « de style » de Towns and Towers ne naissent d'ailleurs jamais dans le
monde des raids (biomes de mods absents).

**Code** : `RaidTemplates` (pose d'un gabarit commune aux camps et aux villes : sans occupants,
sol comblé, coffres vidés, blocs « jigsaw » de raccord remplacés), `RaidCity` (siège et tours
autour du village), `RaidCatalog` (nouveau format des villes, villages par terrain, objectif
`tours`), `RaidTarget.City`, `Arena.extend`, `RaidAssault` (garnisons des tours et du siège,
bailli, tours prises), `RaidManager` (village tiré au sort, zone générée agrandie, ville
assemblée avant la préparation), raids.yml (section `villes`), `config-version` 38 (poids
`tours: 2`).

**Une ville** : un village tiré au sort parmi `village.naturels` (raids.yml ; sans la clé, ceux qui
naissent sur les terrains de la ville, jeu de base puis Towns and Towers) et qui naît dans le monde
des raids. Une fois le village repéré et le camp posé, le plugin pose le **siège du pouvoir** du
côté opposé au camp et **3 tours de la faction** (les deux côtés, et le côté du camp vers le coin
le plus éloigné de lui), hors du village, sur la terre ferme (relief de 14 blocs au plus, jusqu'à
deux reculs de 6 blocs), sans empiéter sur le camp. Gabarits tels quels, tournés au hasard, sans
leurs occupants (chevaux, balistes, illageois des sièges d'ATi). La zone générée autour du village
s'agrandit pour les contenir (plus grand gabarit + 20 blocs).

**Défense** : faction des sièges ; garnison du village (nombre du niveau, moins ce que prennent
tours et siège sur le maximum de 60 vivants), 3 + niveau ÷ 2 soldats par tour (étages et chemins
de ronde), 4 gardes au siège et le **bailli** (« Bailli de l'Ost du Corbeau ») au rez-de-chaussée
du siège. Bannière de la faction au sommet de chaque tour. Les villageois du village restent
(bonus « épargner »).

**Objectifs** : garnison, **tours prises** (une tour est prise quand toute sa garnison est tombée ;
ses deux derniers soldats brillent), bannières (dont celles des tours), bailli, tenir la place
(la cloche du village).

**Écarts** : pas de routes vers les tours ; le style du village n'est plus celui du nom de la ville
(Brumeval n'est plus Tudor) ; « au hasard » ne combine pas style, faction et siège ; zone générée
plus grande, donc un repérage plus long (jusqu'à 90 blocs autour du village pour le fort de
Haut-Bois).

**Camps (demande du 21/10/2026)** : sur la terre ferme, le camp de bandits seul
(`raids.camp.modeles`), posé plus souvent (relief de 12 blocs au plus, sol comblé jusqu'à 14
blocs, quelques flaques tolérées). Sur l'eau, plus de radeau du plugin : un petit bateau de
Towns and Towers tiré au sort (`raids.camp.navires` : `small_ship_4`, `escort_1`, `escort_2` du
village sur l'eau), coque dans l'eau de `flottaison` couches, l'eau remise autour de la coque et
l'intérieur sec ; départ et héraut sur le pont ; barques amarrées autour, comme avant. Sans
bateau disponible, le radeau reste en secours. `config-version` 39.

**Recul inversé des soldats** (signalé pendant les tests) : frappé, un soldat de faction était
tiré vers celui qui frappait. Le recul de Citizens pour un PNJ de type joueur est remplacé par un
recul à l'opposé du coup, et le soldat ne marche pas pendant une demi-seconde (il ne repart plus
aussitôt vers son adversaire). Vaut aussi pour les sièges.

**À tester en jeu** :
1. `/city raid start brumeval force` (et une ville de neige, `hautefroide`) : repérage plus long,
   village naturel, siège du côté opposé au camp, 3 tours autour, sans chevaux ni balistes, coffres
   vides, aucun bloc « jigsaw » visible.
2. Garnisons dans les tours et le siège, bannières de la faction au sommet des tours ; « Tours
   prises 1/3 » quand une tour est vidée ; bailli lumineux dans le siège ; place tenue à la cloche.
3. Chez Alderic : les villes ne sont plus grisées (niveau V).
4. Frapper un soldat de faction (raid ou siège) : il recule à l'opposé, puis revient au combat.

### Corrections après le test des raids légendaires (21/10/2026)

- **Vaisseau ancien : 15 Wardens** au lieu d'un. Le complément d'un gabarit reprenait l'espèce la
  plus nombreuse à bord, ici le Warden (le maître). Désormais le complément ne copie jamais le
  maître, le chef ni un boss (Warden, Wither, gardien ancien, ravageur, toute créature à plus de
  60 points de vie) ; sans autre modèle, il prend des soldats d'une faction. Le maître garde sa
  vie de boss (pas de multiplicateur du niveau).
- **Vaisseau du roi pirate : 257 bannières** à arracher. Bannières, cages et captifs sont plafonnés
  à 3 + niveau (4 au niveau I, 10 au niveau VII) ; n'importe lesquels comptent.
- **« Navire abordé » jamais rempli** : la vérification ne voyait que les joueurs en survie ou en
  aventure (un administrateur en créatif n'était jamais compté), et demandait d'être « au sol »
  selon le client. Désormais tout joueur engagé non spectateur compte pour les objectifs (aborder,
  tenir), debout à bord : hors de l'eau, hors d'une barque, pas en vol, un bloc plein sous les
  pieds. Les défenseurs, eux, ne visent toujours que les joueurs en survie ou en aventure.
- **Défenseurs trop coriaces** (« même avec des armes puissantes, c'est très long ») : au niveau
  VII, vie × 2,2 (× 3,3 avec la renommée) et armure de netherite pour les créatures qui n'en
  avaient pas (60 à 70 % des coups absorbés), chef × 3 (le champion des sièges montait à 770
  points de vie). Désormais (`config-version` 36, valeurs remplacées si elles n'ont pas été
  modifiées) : vie × 1 à 1,5, dégâts × 1 à 1,3, armure au plus de fer (aucune aux niveaux I et II,
  cuir III et IV, maille V et VI, fer VII) ; renommée : +5 % de vie par victoire, au plus +25 % ;
  chef créature × 2, unité promue chef × 1,5, le champion des sièges garde sa vie (140). Nombre de
  défenseurs et garnison inchangés.
- **Skin du héraut** : `raids.camp.heraut-skin` (`config-version` 37), skin de mineskin.org
  (`d6d121d0319f4aaf8c5bac2de7b8a7c5`), posé avant l'apparition ; valeur vide = skin de Minecraft.
- **« Place tenue » qui n'avance pas** : la place n'était montrée nulle part, et sans cloche elle
  était prise au centre de la boîte (souvent un toit ou un étage). Désormais : la cloche s'il y en
  a une, sinon le plus proche sol à ciel ouvert du cœur (cour, place) ; cercle de particules au sol
  (doré, rouge quand un défenseur est dedans) et colonne de lumière visible de loin, chaque
  demi-seconde ; les défenseurs qui disputent la place brillent ; message à l'arrivée au camp.

### Camps des mods, R7, R8 et R9 — codés le 21/10/2026 (tests en jeu à faire)

Demandes après le test de R3 à R5 : le clic sur « Rassembler » met du temps à répondre (on
reclique) ; un camp plus grand, de style camp, ou un camp existant des mods (camp de bandits) ;
les expéditions en mer et ce qui manque ; une commande d'administration pour monter et descendre
le niveau de la ville.

**Code** : `RaidCamp` (modèles des mods, radeau, plateformes du Nether et de l'End), `RaidLair`
(galerie des repaires enfouis, tour d'échelles des cibles flottantes), `RaidAssault` (navires,
maître, tenir, défenseurs disparus), `RaidManager` (recherche différée, barques, mondes du Nether
et de l'End), `RaidRun`, `RaidConfig`, `RaidParticipants` (barques), `RaidScreens` (retour au
clic, objectifs), `CityCommand` et `CitySubCommand` (`/city niveau`), `config-version` 35
(`raids.camp.modeles`, `raids.objectifs.tenir-secondes` et `tenir-rayon`, poids `aborder`,
`maitre`, `tenir` ; fusion contrôlée hors jeu depuis une configuration en version 33 et en
version 34).

**Clic sur « Rassembler »** : la recherche du terrain (commande `/locate` du jeu, quelques secondes
de fil principal) partait dans le même instant que le clic : le menu restait affiché pendant le
temps d'arrêt. Désormais le menu se ferme, le joueur reçoit un titre et la cloche, l'annonce part
(« le serveur peut marquer une courte pause »), et la recherche commence une demi-seconde plus
tard. Un second clic trouve l'expédition déjà lancée.

**Camp** : un modèle de `raids.camp.modeles` au hasard (par défaut `valarian_conquest:bandit_camp`
et `valarian_conquest:aegis_camp`, 12 × 12 : tentes, feu de camp, lits, tonneaux), posé par l'API
des structures de Paper **sans ses occupants** (ni bandits ni gardes), tourné au hasard, coffres et
tonneaux vidés, sol comblé dessous ; seulement sur la terre ferme et un relief de 6 blocs au plus.
Sinon le camp du plugin, agrandi à 7 × 7 (bordure de rondins, poteaux à lanterne, feu de camp,
foin, tonneau, bannière de Valmont). La zone générée autour de la cible s'agrandit pour contenir
le camp choisi.

**Abordage (R7)** : sur l'eau, radeau de Valmont de 9 × 9 (bastingage ouvert au milieu de chaque
côté, mât et bannière, lanternes) et une barque par joueur, plus une, amarrées autour
(indestructibles, retirées au nettoyage). Objectif `aborder` : un joueur engagé pose le pied sur le
navire, hors de l'eau et hors d'une barque. Postes des défenseurs et renforts sur la première place
libre au-dessus de la mer (pont, cale ; jamais sur les voiles) ; le chef d'un navire est un
« Capitaine » ; `remplacer` (raids.yml) retire l'équipage d'origine au profit de la faction.

**Repaires (R8)** : objectif `maitre` obligatoire : la créature `maitre` de raids.yml présente dans
la structure, sinon cherchée dans la boîte, sinon posée au cœur ; elle garde sa vie de boss, ne
laisse rien et brille. **Repaires enfouis** : un puits à échelles (marqué d'un poteau à lanterne)
descend du bord du camp, puis une galerie de 2 de haut, éclairée, mène à la première salle
repérée (près d'une porte, d'un coffre, d'une cage) ; grottes, eau et lave traversées sont
comblées de pierre. Postes et renforts à l'intérieur. Un défenseur, un chef ou un maître qui
disparaît sans mourir (le Warden s'enterre) compte comme vaincu : aucun objectif ne devient
impossible.

**Tenir** (villes, châteaux) : un joueur engagé dans un rayon de 8 blocs autour de la cloche ou du
cœur, sans défenseur dedans, fait avancer le compte (45 s) ; un défenseur qui revient le met en
pause.

**Cible flottante** (château dans le ciel) : tour d'échelles contre un pilier de pierre, du sol ou
de l'eau jusqu'au bord de la structure le plus proche du camp.

**Nether et End (R9)** : mondes `citycore_raids_nether` et `citycore_raids_end` créés au démarrage
quand ils sont actifs, remis à zéro comme le monde de surface ; leurs cibles (forteresse, vestiges
de bastion, citadelle maudite, cité de l'End) apparaissent chez Alderic. Camp : plateforme de 7 × 7
près de la base de la cible (pierre noire et basalte, lanternes des âmes ; obsidienne et briques
de l'End), sur le meilleur sol à 6 à 34 blocs de la boîte ; s'il n'y a que du vide ou de la lave,
plateforme au plus près à la hauteur de la base et passerelle de 3 de large jusqu'à la cible.
Postes sur la première place libre de la boîte (pas de toit de bedrock ni de vide). Les élytres
de la nef de l'End partent avec les cadres vidés à la préparation.

**Niveau de la ville** : `/city niveau [monter|descendre|<1-10>]` (`citycore.admin.ville`), pour
les tests : change le chapitre sans payer de charte ni offrir de parcelles ; ferme les écrans des
quêtes de la cité ouverts.

**Écarts** :
- **Mondes du Nether et de l'End** créés au démarrage, pas à la première demande : créer un monde
  en jeu figerait le serveur. `raids.mondes.nether.active: false` (ou `end`) les retire.
- **Porte** (`porte`, châteaux) : toujours pas suivie (entités de porte des mods, cassables ou
  non : à vérifier en jeu).
- **Villes fortifiées (R6)** : toujours grisées « bientôt » ; leur assemblage reposait sur
  `/place`, abandonné. À repenser.
- **Coffres vides** : voulu (§6.3) ; un butin de victoire reste possible (non fait).

**À tester en jeu** :
1. « Sonner le rassemblement » : le menu se ferme tout de suite, titre et cloche, puis la courte
   pause ; un nouveau clic dit que l'expédition est en cours.
2. Camp : un camp de bandits ou de l'Égide sans occupants, coffres vides, posé à plat ; sur un
   terrain accidenté ou sans Valarian Conquest, le camp du plugin de 7 × 7. Héraut et
   réapparition dans le camp, pas sur le feu.
3. Abordage (`navire-pirate-maudit`, `bateau-pirate`, `galion`) : radeau, barques (une par joueur
   + 1, incassables), défenseurs sur le pont, « Navire abordé » en posant le pied à bord, capitaine.
4. Repaire de surface (`hutte-de-la-sorciere`, `cabane-de-l-apothicaire`) : maître lumineux,
   défiance bloquée à 10 % tant qu'il vit ; repaire enfoui (`catacombes`, `monument-souterrain`) :
   puits à échelles, galerie éclairée jusqu'à une salle, défenseurs à l'intérieur.
5. Château (niveau VI) : « Place tenue » 45 s, pause quand un défenseur revient ; château dans le
   ciel : tour d'échelles.
6. Nether (`forteresse-du-nether`, `vestiges-de-bastion`) et End (`cite-de-l-end`) : mondes créés
   au démarrage (temps dans la console), plateforme et passerelle, défenseurs dans la structure,
   pas de dragon ; mort et réapparition sur la plateforme.
7. `/city niveau`, `monter`, `descendre`, `3` : chapitre affiché, niveaux de raid débloqués en
   conséquence chez Alderic.

### R3, R4 et R5 — codées le 21/10/2026 (tests en jeu à faire)

Constat du test de R1 : « le raid fonctionne, mais pas d'objectifs ni d'adversaires ». Le monde
des raids n'a pas d'apparition naturelle de créatures (`doMobSpawning` coupé, §2.2) : une
structure ne porte d'occupants que si son gabarit en contient. Les défenseurs sont donc ajoutés
par le plugin, plus ou moins nombreux et forts selon le niveau.

**Code** : `RaidAssault` (défenseurs, objectifs, renforts, chef, événements de l'assaut),
`RaidProgress` (tables `raid_state` et `raid_results` : victoires, recharges, historique),
`RaidRun` (défiance, objectifs), `RaidManager` (victoire, défaite, bilan, récompenses, barre,
suivi des quêtes, accès des écrans), `RaidParticipants` (anti-exploitation), `RaidCommands`
(`start … force`, `recharge`, `debloquer`), `EnemyArmy.spawnDefender` (soldat qui tient un poste),
`QuestHUD.setRaid` (défiance et objectifs en tête du panneau de quêtes), `RaidScreens` (écrans
d'Alderic illustrés et classiques), `MayorScreens` et `MayorGUI` (bouton Événements),
`UiMenu.windowTitle` et `refresh` (menus classiques, compte vivant), `ValmontArt` (boutons
Événements, Raids, Sièges, Fêtes, Rassembler, bannière Raids, dé « au hasard »),
`config-version` 34 (`raids.defenseurs`, `force`, `objectifs`, `recompenses` ; contrôlé hors jeu
sur la configuration du serveur ; les clés ajoutées gardent désormais leur commentaire).

**Défenseurs (§5.3)** : nombre tiré entre les bornes du niveau × (1 + 0,5 × (joueurs − 1)), au
plus 60 vivants ; ceux de la structure reçoivent la force du niveau (vie × 1 à 2,2, dégâts × 1 à
1,6, armure de cuir à netherite pour ceux qui n'en ont pas, ni butin ni départ, pas de brûlure au
soleil) et le reste est ajouté : cible `armee` (ou sans occupant hostile) → soldats d'une faction
des sièges (armée ennemie à part de celle des sièges, palier du siège selon le niveau) ; cible
`creatures` → ses créatures ; sinon des créatures du même genre que les occupants. Postes : au sol
près des portes et de la cloche une fois sur deux (pas sur les toits), sinon au cœur de la boîte,
jamais à moins de 14 blocs du camp. Toutes les 2 s, un défenseur sans cible s'en prend au joueur
engagé le plus proche en vue (20 blocs) ; les 3 derniers de la garnison brillent.
**Chef** (niveaux V à VII, villes, navires, ou `chef` de raids.yml) : lumineux, nommé (« Capitaine
de l'Ost du Corbeau », « Chef … »), vie × 2,5 à 3, objectif obligatoire. **Renforts** (§5.4) :
`renforts` vagues du niveau, sous 50 % (1 vague), 67/33 % (2), 75/50/25 % (3) de défiance : un
quart de la garnison arrive du côté opposé au camp et marche sur le cœur de la cible.

**Objectifs (§5.2)** : garnison (80 % abattus), cloche (sonnée sans défenseur à 16 blocs), bannières
(arrachées), cages (niveau IV et plus, repaires ; neutralisées en dessous), captifs (allays,
clic), chef. Un objectif dont la cible n'a pas l'élément est retiré ; à défaut, la garnison.
Défiance = 100 % − la part pondérée des objectifs remplis (poids dans `raids.objectifs.poids`),
au moins 10 % tant qu'un objectif obligatoire reste. Victoire : défiance à 0 → capitulation (les
défenseurs restants disparaissent), titre, son. Défaite : chrono écoulé ou plus aucun joueur
engagé ; rien de perdu, recharge de moitié.

**Récompenses (§6.1)** : caisse de la ville et chaque combattant (parti au camp et resté jusqu'au
bout, ou ayant abattu au moins un défenseur), × (1 + 15 % par bonus réussi : épargner, sans
perte, éclair) × (1 + renommée : 5 % par victoire du niveau, au plus 25 %) ; la renommée rend aussi
les défenseurs plus coriaces (+10 % de vie par victoire, au plus +50 %). Bilan dans le chat
(durée, défiance, défenseurs abattus, objectifs, meilleurs combattants, bonus, gains).

**Déblocage et recharge (§4.1, §6.2)** : un niveau s'ouvre au chapitre de la ville et après une
victoire au niveau précédent ; après une expédition, recharge du niveau pour tout le serveur.
`start … force` (administration) passe outre ; `recharge` les lève ; `debloquer <n>` compte une
victoire à chaque niveau en dessous (le chapitre reste exigé).

**Anti-exploitation (§6.3)** : aucun butin des créatures (l'expérience reste), rien ne tombe des
blocs cassés ni des explosions, cadres et tableaux arrachés sans rien laisser, pas de commerce
avec les villageois ; coffres, tonneaux et pots vidés (tables de butin retirées), cadres et
porte-armures vidés, golems retirés à la préparation.

**Écrans d'Alderic (§1.1)** : accueil de la mairie → bouton **Événements** (badge quand un raid
est prêt) à la place d'**Agrandir**, passé dans le **Trésor** ; Événements (Raids, Sièges en
consultation avec le calendrier, Fêtes « bientôt ») → Difficulté (7 niveaux : chiffre romain,
cadenas, conditions cochées, nombre de cibles, victoires, recharge) → Cibles (grille paginée,
filtres « toutes » + types du niveau, « au hasard », « prise ×N ») → Fiche (type, occupants, mods,
objectifs, chrono, récompense et bonus, joueurs dans le cercle d'Alderic revus chaque seconde,
« Sonner le rassemblement » grisé avec la raison). Sans le pack : mêmes écrans en menus
classiques (case « Événements » du menu d'Alderic). Un joueur qui sonne le rassemblement ne voit
pas le détail technique du repérage (envoyé à la console).

**Écarts** :
- **Cibles « bientôt »** : abordages (R7), repaires enfouis (R8) et villes fortifiées (R6)
  restent visibles mais grisés chez Alderic (`start … force` reste possible en administration).
- **Repérage à l'ouverture de la fiche** : pas fait. Une recherche coûte quelques secondes de fil
  principal ; la lancer à chaque fiche ouverte serait pire que de la lancer au rassemblement. Le
  repérage reste au signal (« les éclaireurs repèrent le terrain », avancement pour tous).
- **Objectifs `porte`, `tenir`, `maitre`, `aborder`** : pas encore suivis (R6 à R8) ; retirés.
- **Trophées** (bannière de la faction vaincue, trophée légendaire) : renvoyés à R10.
- Le compte des joueurs du cercle se met à jour dans la fiche sans la rouvrir (objet vivant) ;
  la barre de la fiche garde un texte fixe.

**À tester en jeu — R3, R4, R5** :
1. Mairie avec et sans pack : bouton Événements (badge), Agrandir dans le Trésor ; Événements →
   Raids → Difficulté (cadenas, conditions, recharge) → Cibles (filtres, pages, au hasard) →
   Fiche (compte du cercle en direct en entrant et sortant du cercle).
2. Bouton grisé avec la raison : niveau fermé, recharge, raid en cours, siège ; « Sonner le
   rassemblement » lance l'expédition (cloche, annonce) sans message technique au joueur.
3. Assaut niveau I (`camp-de-bandits` ou un camp vanilla) : défenseurs présents (nombre, armure,
   force), qui attaquent, ne brûlent pas au soleil, ne laissent rien ; objectifs dans le panneau de
   quêtes et la barre « Défiance N % · mm:ss » ; garnison → victoire, capitulation, bilan et gains
   (caisse et bourse).
4. Village (niveau III) : cloche refusée tant qu'un défenseur est à 16 blocs, acceptée ensuite ;
   bonus « épargner » perdu si un villageois est tué ; pas de commerce avec les villageois.
5. Avant-poste (niveau II) et une cible `armee` : soldats d'une faction (PNJ) qui tiennent leur
   poste puis attaquent ; bannières arrachées comptées ; rien ne tombe des blocs cassés.
6. Niveau III ou plus : renforts sous 50 % de défiance (titre, cor) ; niveau V : chef lumineux
   obligatoire (défiance bloquée à 10 % tant qu'il vit).
7. Défaite au chrono (`raids.niveaux.1.chrono-minutes: 1`) : bilan sans gain, recharge de moitié ;
   victoire : recharge entière, niveau suivant débloqué (au chapitre voulu) ; `recharge` et
   `debloquer` en administration.
8. Deux joueurs : plus de défenseurs (× 1,5) ; un joueur rentré au camp avant la fin sans avoir
   abattu personne n'est pas payé.

### R1 — codée le 21/10/2026 (tests en jeu à faire)

**Code** : `RaidRun` (raid en cours : étapes `PREPARATION → RASSEMBLEMENT → ASSAUT → BILAN`),
`RaidCamp` (camp), `RaidHerald` (héraut), `RaidParticipants` (sécurité des joueurs, table
`raid_returns`) ; `RaidManager` (déroulé, repérage commun aux essais et aux expéditions),
`RaidCommands` (`start`, `stop`, `quitter`), `RaidConfig` (`camp`, `rassemblement`,
`bilan-secondes`, `joueurs`, `niveaux`) ; `SiegeManager.setBlocker`, `EventCalendar.setPostponer`,
`DatabaseManager` (`raid_returns`), `CityCore` (branchements : position d'Alderic, il cesse de
suivre), `config-version` 33 (sous-sections ajoutées sous `raids`, contrôlé hors jeu sur une
copie de la configuration du serveur).

**Déroulé** : `/city raid start <cible> [ici]` (clé de `raids.yml` ou structure) → les éclaireurs
repèrent et génèrent le site (avancement dans la barre d'action de tous), camp et héraut posés →
rassemblement de 30 s autour d'Alderic (ou de l'administrateur avec `ici`) : annonce, titre,
cloche, Alderic cesse de suivre, cercle doré de 10 blocs, compte à rebours → les joueurs du cercle
(même monde, vivants, pas spectateurs) sont enrôlés, leur position notée, et partent au camp face
à la cible (cor) ; moins de `joueurs-min` : annulée → assaut au chrono du niveau (barre rouge
« ⚔ cible · mm:ss » ; pas d'objectifs avant R4) → bilan de 20 s → chacun rentre à sa position
d'origine (auprès d'Alderic si elle est perdue) → nettoyage (héraut, barre, occupants étiquetés
de la cible, tickets). Plus aucun joueur engagé dans le monde : fin anticipée.

**Écarts et précisions** :
- **Camp** : plancher de 5 × 5 posé par le plugin (radeau sur l'eau), feu de camp et bannière
  jaune (or de Valmont), du côté le plus plat des huit côtés et coins à `camp.distance` du bord de
  la boîte ; pas de `valarian_conquest:neutral_tent_camp` (il faudrait `/place`, abandonné).
- **Héraut** : PNJ Citizens d'un registre en mémoire ; une réplique (deux lignes de barre
  d'action), puis un bouton « [Battre en retraite] » / « [Rentrer à Valmont] » qui lance
  `/city raid quitter` (commande joueur).
- **Retours** : `raid_returns` vidée au démarrage (raid interrompu : retour auprès d'Alderic) ;
  joueur hors ligne à la fin : sa ligne est effacée, il revient auprès d'Alderic ; mort au moment
  du retour : il rentre à sa réapparition.
- **Entrée et commandes** : l'administration (`citycore.admin.raid`) n'est ni bloquée à l'entrée
  ni pour les commandes (tests) ; un joueur engagé qui sort par un autre moyen est considéré comme
  rentré. Liste des commandes refusées complétée (`tpyes`, `warps`, `tpo`, `tppos`, `tphere`).
- **Sièges** : `/city siege start` refusé pendant une expédition ; un siège du calendrier est
  reporté de 30 min (annonce), autant de fois que nécessaire. Pas d'expédition pendant un siège.
- **Sites préparés d'avance** : pas encore ; le repérage se fait au lancement (la recherche
  occupe le fil principal quelques secondes). À faire avec la fiche d'Alderic (R3) : repérer dès
  l'ouverture de la fiche.
- `niveaux` (§4.1, §6.1) est déjà dans `config.yml` ; R1 n'en lit que le chrono.

**À tester en jeu — R1** :
1. `/city raid start <clé>` (par exemple `village-plains`, `camp-de-bandits`,
   `commanderie-du-temple`) : messages des éclaireurs, avancement, camp à côté de la structure,
   hors de portée des archers ; héraut sur le camp.
2. Rassemblement : annonce, cercle doré autour d'Alderic, compte à rebours ; départ à 2 joueurs
   dont un hors du cercle (seul celui du cercle part) ; `start … ici` autour de soi.
3. Mort (chute, noyade, monstre) → réapparition au camp avec tout l'inventaire et l'expérience,
   rien au sol, invulnérable un instant ; mort pendant le bilan → rentre avec les autres.
4. Un lit utilisé dans le monde des raids ne change pas le point d'apparition ; pas de portail.
5. `/home`, `/tpa`, `/spawn` refusés à un joueur (pas à un administrateur) ; entrée par `/tp`
   refusée à un joueur.
6. Retraite par le héraut (bouton) ; fin au chrono (`raids.niveaux.1.chrono-minutes: 1` pour
   aller vite) ; bilan de 20 s ; retour à la position d'origine ; plus aucun occupant ni héraut.
7. Déconnexion pendant l'assaut puis retour : au camp ; après la fin : auprès d'Alderic. Arrêt
   du serveur en plein raid puis reconnexion : auprès d'Alderic.
8. `/city siege start` refusé pendant une expédition ; `/city raid start` refusé pendant un
   siège ; `/city raid stop` à chaque étape.

### R0 revue — génération naturelle (21/10/2026)

Après essai en jeu (structures posées par `/place` « chaotiques »), R0 passe à la génération
naturelle (§2.4 à §2.7) : monde des raids avec structures, repérage par
`locateNearestStructure` (exemplaire non exploré, point de départ neuf à chaque recherche),
génération du chunk de départ puis de la boîte et de la marge par tickets de chargement,
balayage des pièces de la `GeneratedStructure`. Retirés : `RaidSlots` (grille, classement des
biomes, relief), `TerrainFamilies`, `StructurePlacer` (`/place structure | jigsaw | gabarit`),
la table `raid_slots_used`, le cache `raids-emplacements.yml` (supprimé au démarrage),
`/city raid poser | jigsaw | emplacements` (remplacées par `essai` et `zones`). Ajouté :
`RaidSites`. `config-version` 32 : en-tête de la section `raids` réécrit, `espacement` 1024 →
4096, `rayon-prepare` remplacé par `rayon-recherche` (100 chunks) et `marge` (32 blocs),
sections `raids.terrain` et `raids.familles` retirées (contrôlé hors jeu sur une copie de la
configuration du serveur, second passage sans effet). La colonne « Terrain » de `raids.yml` reste
indicative ; la ligne de bilan ne dépend plus du terrain.

Le journal ci-dessous (R0 et R2 du 21/10/2026) garde l'historique de la première version.

### R0 et R2 — codées le 21/10/2026 (tests en jeu à faire)

**Code** : paquet `com.citycore.raid` — `RaidConfig`, `TerrainFamilies`, `RaidWorld`,
`RaidSlots`, `StructurePlacer`, `ArenaScanner` → `Arena`, `RaidTarget`, `RaidCatalog`,
`RaidManager` (façade : démarrage, essais de pose), `RaidCommands` (`/city raid`). Câblage dans
`CityCore`, `CityCommand`, `CitySubCommand` (`RAID`), `CityTabCompleter`, `CityPermissions`
(`citycore.admin.raid`, déclaré dans `plugin.yml`), `ModIntegrations` (3 mods, ligne Raids de
`/city mods`), `DatabaseManager` (`raid_slots_used`), `ConfigUpdater` (version 30, `raids` ajoutée
aux sections complétées), `config.yml` (section `raids`, clés `mod-integrations`), `raids.yml`
(nouveau), `docs/INTEGRATION_MODS.md`.

**Écarts et précisions par rapport au plan** :
- Lecture des biomes à **y = 256** (et non 64) : sous un relief, un point à y = 64 peut tomber
  dans un biome de grotte (grottes luxuriantes, concrétions), et l'emplacement serait perdu ;
  au-dessus du sol, le générateur rend toujours le biome de surface. Lecture par
  `UnsafeValues#getBiomeKey` (clé complète, biomes de mods compris), sans créer de chunk ; le
  nombre de chunks chargés pendant le classement est journalisé pour le vérifier.
- **Boîte de la cible** = étendue des blocs qui ont changé entre deux instantanés des chunks, pris
  juste avant et juste après la pose (pas de liste de blocs « naturels » à tenir) ; les points
  remarquables ne sont relevés que parmi ces blocs. Entités relevées **2 s après la pose**
  (occupants qui apparaissent au premier tick), classées : défenseurs (étiquette
  `citycore.raid`), civils (`citycore.raid.civil`), golems, portes (entités), engins, animaux,
  décor, autres. R0 n'en retire aucune (observation) ; points hauts et entrées viendront avec les
  postes (R5).
- Réglages ajoutés : `raids.terrain.relief-max-familles` (`sommets: 80`, `mer` et `mer-froide` :
  `-1`, sans contrôle) et `raids.terrain.rayon-relief` (48, emprise quand la taille de la cible
  est inconnue) ; emprise de 24 à 64 blocs (au-delà, les pièces d'un village épousent le
  terrain). `prets-au-demarrage` est déjà dans `config.yml` mais ne sert qu'à partir de R1 (en
  R0, chaque essai prépare son emplacement à la demande). Les chunks d'un essai sont rendus dès
  la fin du balayage (rien n'est gardé chargé).
- Table `raid_slots_used` : colonne **`idx`** (et non `index`, mot réservé en SQL).
- Commandes d'essai en plus : `/city raid jigsaw <pool> <cible> [profondeur] [famille]` (test 6)
  et `/city raid poser gabarit:<id> [famille]` (repli par gabarit, API Paper). La pièce de départ
  des places Tudor de Towns and Towers (`tudor_meeting_point_1`) a des blocs puzzle nommés
  `minecraft:street` (4, départs des rues) et `minecraft:bottom` (7, apparitions) : `cible` =
  `minecraft:street`.
- Pose : `execute in <world.getKey()> run place structure …` ; si la dimension est refusée, la
  forme `minecraft:<nom>` est essayée et l'essai le signale en console. Réussite = clé de
  traduction `commands.place.*.success` du retour capturé.
- **`raids.yml`** : une ligne par cible ; `structure:` pour une pièce unique ; objectifs **déduits
  du type et du niveau** (cages à partir du niveau IV et pour les repaires, chef à partir du
  niveau V et pour les villes et navires, ou quand la cible nomme un chef), écrits seulement pour
  les exceptions ; bonus `sans-perte` et `eclair` partout, `epargner` pour villages et villes ;
  mods requis déduits des espaces de noms (`avec-mod` pour en ajouter) ; `taille` ou `rayon`
  (emprise) ; `active: false` retire une cible. Variantes de Towns and Towers : la plus grande
  taille de la fourchette du §7. Fusion par version : les cibles, villes et structures écartées
  absentes du fichier du serveur y sont ajoutées (copie de l'ancien), le reste est gardé.
- Bilan console : « ⚔ Raids : 168 cibles et 8 villes, N disponibles ; M masquées (…) », écrit
  une fois la grille classée (le terrain en dépend).

**Premiers constats en jeu (21/10/2026)** :
- **`getChunkAtAsync` ne rend aucun chunk sous Youer** pour un terrain jamais généré (chunk vide,
  sans erreur) : la première préparation échouait (« chunk 1191, 615 non généré (?) »).
- **Ne jamais générer sur le fil principal** : un premier repli par `getChunkAt` (quelques chunks
  par tick) a figé le serveur — sous Youer, un seul chunk neuf demande plusieurs secondes
  (voisins, écoulement des fluides) ; le watchdog a fini par arrêter le serveur (tick de plus de
  60 s), la sauvegarde des mondes a planté et le processus est resté bloqué, tenant
  `world/session.lock`. Remplacé par des **tickets de chargement du jeu** posés par réflexion
  (`ServerChunkCache#addRegionTicket`, ticket `FORCED` au niveau 33, noms de Mojang communs à
  NeoForge et Paper) : la génération se fait sur les fils de génération, comme autour d'un
  joueur ; le plugin attend l'arrivée des chunks, 16 à la fois. Sans ces tickets ni
  `getChunkAtAsync`, la préparation est refusée. `/city raid tp` fait de même pour un terrain
  neuf (« Préparation de l'arrivée… »). Sous Youer, les classes du jeu se présentent aux plugins
  sous leurs noms Spigot (`ChunkProviderServer`) et les deux méthodes y sont `a(…)` / `b(…)`
  (table `spigot2srg` de NeoForge) : les noms de Mojang puis ceux-ci sont essayés. Constaté :
  la préparation et `/city raid tp` passent ainsi, sans figer le serveur.
- Retour de secours **auprès d'Alderic** (décision du 21/10/2026) : reconnexion dans le monde des
  raids, joueurs évacués à l'arrêt ou par `/city raid purge`, mort dans ce monde hors raid,
  `/city raid retour` sans position connue ; repli : point d'apparition du monde principal.
- Test 6 (`place jigsaw … minecraft:street 10`) : posé en 7,3 s, mais seulement des rues et la
  place (voir §7.8) ; les animaux relevés (cochons, moutons, vaches, ratons laveurs de Wilder
  Nature) sont ceux de la génération du terrain.
- Ajout de `/city raid retour` : retour à la position d'avant `/city raid tp` (sinon point
  d'apparition du monde principal). Un retour par téléportation sur le fil principal n'a pas fait
  quitter le monde des raids, alors que l'aller en tâche de fond avait marché : les deux
  commandes chargent d'abord le chunk d'arrivée, essaient `teleportAsync`, puis `teleport`, et
  notent le résultat en console (« ⚔ [Raids] Téléportation de … ») — à lire pour R1 (retours des
  joueurs). Secours sans le plugin : `/back` d'EssentialsX.
- Chunky (version NeoForge) est installé sur le serveur : piste pour générer d'avance les
  emplacements prêts de R1, plutôt que sur le fil principal.
- Le premier essai de jigsaw (relief max 16) a enchaîné les refus et fait générer une cinquantaine
  de régions : relief max porté à 50 (sommets 80, `config-version` 31) et relief contrôlé avant
  la préparation complète ; monde jeté à l'arrêt du serveur (§2.3).

**Contrôlé hors jeu** (code du plugin, sans serveur) : lecture du vrai `raids.yml` (168 cibles,
8 villes, 16/53/40/25/10+8/12/12 par niveau, aucun avertissement ; Towns and Towers 58 cibles +
8 villes, Valarian Conquest 21 + 8, ATi 46 + 7…), structures écartées par motif
(`minecraft:ruined_portal*`, `mostructures:*_abandoned_church`), fusion de `config.yml` v29 → v30
(sections `raids` et 3 clés `mod-integrations` ajoutées, second passage sans effet), fusion de
`raids.yml` par version (entrées manquantes ajoutées, réglage modifié conservé).

**À tester en jeu — R0** : voir la liste de la phase R0 (§9), revue pour la génération
naturelle. Déjà constaté : création et effacement du monde, génération des chunks en tâche de
fond par tickets, retour auprès d'Alderic, `place jigsaw` sans maisons.

**À tester en jeu — R2** :
1. Bilan console au démarrage (« ⚔ Raids : 168 cibles et 8 villes, … disponibles ; … masquées
   (…) ») et éventuels avertissements « structure … n'existe pas (mod mis à jour ?) ».
2. `mod-integrations: t_and_t: off` → 58 cibles et 8 villes masquées (« Towns and Towers
   absent ») ; `raids.mondes.nether.active: false` → 3 cibles masquées (citadelle maudite,
   vestiges de bastion, forteresse du Nether).
3. `/city raid catalogue` puis `/city raid catalogue 2` : disponibles et raisons.
4. `/city raid inconnues` vide (sinon : structures à classer, et espace de noms de Fish of
   Thieves à confirmer).
5. `/city mods` : ligne « Raids : N cibles (M disponibles) » sous chaque mod de structures et
   ligne du jeu de base.
6. Fusion de `config.yml` sur le serveur de test (version 29 → 30, copie de sauvegarde) et
   `plugins/CityCore/raids.yml` créé.

**Décisions attendues des tests** (sortie de R0) : monde Bukkit gardé ou repli datapack ; seuil
de dominance, relief maximal et hauteur de lecture des biomes ; cibles à retirer ou à changer de
niveau (mise à jour du §7 et du §10).
