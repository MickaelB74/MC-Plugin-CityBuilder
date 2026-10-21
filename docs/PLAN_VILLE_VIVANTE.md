# Plan « Valmont vivante » — bâtiments, habitants, sièges et garde

Plan de développement par phases, chacune testable en jeu avant de passer à la suivante.
Périmètre retenu : vérification des constructions, habitations et habitants (impôt, besoins),
sièges par vagues, fêtes, soldats de la garde. Pas de monuments ni d'autre gestion.

## Avancement

| Phase | Contenu | Dépend de | Taille | État |
|---|---|---|---|---|
| 0 | Prototype technique sur Youer | — | petite | **validée** (plafond de PNJ affichés à relever) |
| 1 | Types de bâtiments | — | petite | **validée** |
| 2 | Inspection des constructions | 1 | moyenne | **validée** |
| 3 | Habitations et habitants (+ impôt simple) | 0, 2 | grosse | **validée** |
| 4 | Protection renforcée, calendrier d'événements, sièges | 3 | moyenne à grosse | **validée** |
| 5 | La garde (soldats) | 3 (4 pour la défense des sièges) | moyenne à grosse | **validée** (faite avant la 4) |
| 6 | Besoins et satisfaction | 3, 5 | moyenne | **codée, à tester en jeu** |
| 7 | Fêtes | calendrier de la phase 4 | petite à moyenne | à faire |
| — | Emplois : les habitants travaillent pour les artisans (hors phase) | 3, 6 | moyenne à grosse | **codée, à tester en jeu** |
| — | Animations Epic Fight des PNJ (mod serveur compagnon, hors phase) | — | petite à moyenne | **codée, à tester en jeu** |
| — | Légendes liées aux mods : 8 nouvelles légendes, Gandalf au chapitre VI, contenu facultatif (hors phase, voir `docs/INTEGRATION_MODS.md`) | 4 (récit de siège de Musashi) | grosse | **codée, à tester en jeu** |
| — | Gambrinus, barman du chapitre IV (Let's Do Brewery) | — | moyenne | **codée, à tester en jeu** |

L'ordre suit les dépendances : en ville, les joueurs et les artisans ne peuvent pas être blessés,
donc un siège n'a d'enjeu que s'il y a des habitants à protéger. Les soldats sont formés à partir
d'habitants, et le besoin « sécurité » dépend des soldats et des sièges.

## Règles communes

- Une phase = un commit sur `dev`, testable seule sur Youer, avec des commandes op pour accélérer.
- Nouveaux réglages dans `config.yml`, avec une montée de `config-version` (`ConfigUpdater`).
- Nouveaux écrans dessinés en style Valmont, avec un repli en menu classique comme les écrans
  existants.
- **Dégâts** :
  - les joueurs restent invulnérables en ville (`ClaimProtectionListener` annule leurs dégâts) ;
  - les artisans et Alderic restent protégés (Citizens les protège par défaut) ;
  - seuls les habitants et les soldats peuvent être blessés et mourir ; les joueurs ne peuvent
    pas les frapper ;
  - aucun bloc n'est jamais endommagé dans les claims.

---

## Phase 0 — Prototype technique

But : lever les incertitudes propres au serveur hybride avant d'écrire le reste.

- **PNJ blessable** : un PNJ Citizens non protégé (`setProtected(false)`) est-il blessé par les
  monstres ? `NPCDeathEvent` est-il reçu ? Réapparaît-il tout seul ?
- **Charge serveur** : 20 à 30 PNJ qui se promènent, TPS mesurés avec 4 à 6 joueurs. Ce test
  fixe le nombre maximum d'habitants affichés.
- **Combat** : un PNJ armé attaque un zombie (`getNavigator().setTarget(cible, true)`).
- **Apparence (décidée)** : habitants et soldats sont des PNJ de type joueur, comme les artisans,
  pas des villageois Minecraft. Skins des habitants : liste `habitants.skins` de config.yml
  (10 entrées) ; un habitant qui devient soldat **change de skin** et prend l'uniforme de la
  garde (`soldats.skins`), en plus de l'armure.
- **Mort** : ni butin (l'armure et l'arme d'un soldat ne tombent pas), ni expérience, ni message
  de mort dans le chat.

### Ce qui est codé

Commande op temporaire **`/city proto`** (classe `com.citycore.debug.NpcPrototype`, à supprimer
après les tests). Les PNJ du prototype vivent dans un registre Citizens en mémoire : ils ne sont
jamais sauvegardés et disparaissent au redémarrage.

| Commande | Effet |
|---|---|
| `/city proto habitant` | Habitant blessable devant vous (PNJ de type joueur, skin suivant de la liste) |
| `/city proto soldat` | Désormais un **garde de test** au comportement réel de la garde (voir phase 4, « gardes de test ») ; à l'origine, soldat blessable du prototype qui attaquait les monstres à moins de 16 blocs |
| `/city proto former` | L'habitant du prototype le plus proche (16 blocs) devient soldat : il change de skin (uniforme), reçoit armure et épée, réapparaît sur place |
| `/city proto zombie` | Zombie qui cible le PNJ du prototype le plus proche |
| `/city proto foule <n>` | n habitants qui se promènent autour de vous (1 à 60), dans la ville uniquement |
| `/city proto tps` | TPS et temps moyen d'un tick, nombre de PNJ du prototype |
| `/city proto skins` | Relit les sections `habitants` (skins et promenade) et `soldats` de config.yml sans redémarrer, et en donne le bilan |
| `/city proto clear` | Supprime tous les PNJ du prototype |

Chaque événement est rapporté dans le chat des ops et la console : dégâts reçus, mort,
réapparition, transformation (annulée et signalée), attaque d'un soldat.

**Skins** (`config-version` 8 : les sections s'ajoutent d'elles-mêmes à un config.yml existant,
les réglages sont conservés). Pour chaque ligne : `value` + `signature` copiés depuis
https://mineskin.org (recommandé), ou `name` = pseudo d'un compte Minecraft.
- `habitants.skins` : distribués à tour de rôle. Liste vide : skins par défaut de Minecraft
  (Steve, Alex…), qui varient d'un PNJ à l'autre.
- `soldats.skins` : l'uniforme de la garde. Une seule ligne = tous les soldats identiques ;
  plusieurs lignes = à tour de rôle. Liste vide : le soldat garde son skin d'habitant.

**Promenade des habitants** (section `habitants`, `config-version` 9 : les réglages s'ajoutent
d'eux-mêmes sous la section existante) : `vitesse` (0.4, comme la flânerie des artisans ;
1 = un joueur), `pause-promenade` (6 s entre deux trajets, ± 50 %), `rayon-promenade` (12 blocs
autour de leur maison). Les destinations ne sont choisies que dans les chunks revendiqués et
chargés, au niveau du sol de la maison (pas sur les toits) ; un habitant qui sort de la ville
(poussé, en fuite) rentre chez lui, ou y est téléporté s'il est trop loin. Les habitants
n'apparaissent qu'en ville.

Correction faite au passage : `ClaimProtectionListener` annulait les dégâts de **toute** entité
de type joueur dans les claims, PNJ Citizens compris ; un soldat aurait été invulnérable en
ville. Seuls les vrais joueurs sont désormais protégés.

### À tester en jeu

1. `/city proto habitant` puis `/city proto zombie` dans la ville, de nuit, en difficulté normale :
   l'habitant perd-il des PV ? Meurt-il ? Le chat indique-t-il « mort » puis aucune réapparition ?
2. Même essai avec `/city proto soldat` : le soldat doit attaquer le zombie et le tuer. Tuez-le
   ensuite (`/city proto zombie` répété) : **aucune pièce d'armure ni épée au sol**, pas de
   message de mort dans le chat.
3. Skins : `/city proto skins` doit annoncer 10 skins d'habitants ; `/city proto foule 6` → les
   skins alternent. Remplissez `soldats.skins`, `/city proto skins`, puis `/city proto habitant`
   et `/city proto former` à côté de lui : il réapparaît en uniforme, armé, nommé « Soldat ».
4. Promenade : `/city proto foule 10` près de la limite de la ville → marche calme, pauses,
   aucun habitant ne sort des chunks revendiqués (`/city map` pour voir la limite). Poussez-en un
   dehors : il rentre. Réglez `habitants.vitesse` puis `/city proto skins` pour ajuster.
5. `/city proto foule 20`, puis `30`, avec les joueurs connectés : relever `/city proto tps`
   avant et après. Noter le seuil où les TPS descendent sous 19 (PNJ de type joueur, plus lourds
   que des villageois : le test est représentatif).
6. `/city proto clear`, puis redémarrer le serveur : aucun PNJ du prototype ne doit revenir.

Résultat attendu : feu vert (ou non) pour Citizens, plafond de PNJ affichés.

## Phase 1 — Types de bâtiments

- **Base de données** : colonnes `type`, `reserved_for`, `y_min`, `y_max` et `level` dans
  `buildings` (migration automatique).
- **Bâtiments existants** : ceux qui ont un PNJ deviennent « Atelier » réservé à ce PNJ, les
  autres « Divers ». Sans hauteur connue, le sol autour de la zone sert de sol de définition.
- **Types fixes** (leur comportement est codé) : **Habitation**, **Atelier**, **Mairie** (une seule),
  **Divers**. Un atelier peut être réservé à un personnage ou laissé libre : le premier artisan
  qui s'y installe le réserve.
- **Personnages pas encore trouvés** (aucun joueur ne les a rencontrés) : verrouillés (cadenas),
  aucun atelier ne peut leur être réservé, ni par le menu, ni par commande ; ils n'apparaissent
  pas dans les boutons du chat ni dans la complétion des commandes.
- **Création, le type d'abord** : `/city build new` (ou « Ajouter » chez Alderic) ouvre le choix
  du type, puis du personnage pour un atelier ; sans le pack, boutons cliquables dans le chat.
  Le nom est ensuite demandé dans le chat avec une question adaptée (« Nom de l'atelier de
  Thorvald (Forgeron) : ») et un nom proposé cliquable (Forge, Grange, Taverne, Maison_2…).
  Raccourci : `/city build new <type> [personnage|libre] [nom]` (personnage = prénom :
  `thorvald`, `helene`…).
- **Hauteur (révisée)** : plus d'estimation figée à la création (elle ne suivait pas un bâtiment
  construit ensuite). Les coins cliqués, au sol, donnent le **sol de définition** ; la **zone de
  recherche** va de `batiments.profondeur` (40) blocs dessous à `batiments.hauteur` (100) blocs
  au-dessus. La **hauteur réellement construite** (du bloc construit le plus bas, cave comprise,
  au plus haut ; terrain naturel exclu) est mesurée à chaque inspection et affichée
  (« Construit : Y 62 → 78 »). Le toit est mesuré à partir du sol de définition : une grotte sous
  la maison ne le fausse pas. Les bordures affichées vont du sol au sommet construit.
- **Changer le type** : clic droit sur le bâtiment dans la liste d'Alderic, ou
  `/city build type <nom> <type> [personnage]`. Un bâtiment habité par un artisan reste son atelier.
- **Hauteur** : `/city build hauteur <nom> auto` (règle de config.yml), `… <sol>` (nouveau sol de
  définition) ou `… <bas> <haut>` (zone de recherche manuelle). Au premier démarrage avec cette
  règle, les bâtiments existants la reçoivent (ancien bas de zone = sol de définition).
- **Affichage** : type et hauteur dans les bulles des listes ; le type s'affiche aussi dans la
  barre d'action quand on entre dans un bâtiment.
- **Rangement** : les listes de bâtiments (Alderic, `/city build`) sont triées par type, puis par
  nom, avec le type affiché. « Choisir son logis » ne propose que les ateliers libres ou réservés
  à ce personnage ; `/city build assign` applique la même règle.
- **Atelier supprimé → artisan « sans logis »** (nouvel état `HOMELESS`) : son écran de gestion
  (vente, entrepôt, boutique, quêtes, récits) est fermé, remplacé par l'écran d'arrivée en ville
  marqué « SANS LOGIS » avec « Choisir son logis ». Ses quêtes sont **en pause** : rien ne
  progresse (chasse, objets, récits), elles disparaissent du panneau, mais rien n'est perdu (rang,
  stock, quêtes en cours, étape « installé » du recrutement, progrès de la touche L). Un nouvel
  atelier le rétablit et tout reprend. Annonce dans le chat à la suppression. Au démarrage, un
  artisan installé dont le bâtiment avait déjà disparu passe aussi « sans logis ».

### À tester en jeu

1. Créer un bâtiment de chaque type avec le menu (type, puis nom dans le chat, puis zone), puis un
   autre en ligne de commande (`/city build new habitation Maison_test`).
2. Tenter une seconde mairie : refus.
3. Atelier : les personnages jamais rencontrés ont un cadenas et ne sont pas cliquables ;
   `/city build new atelier <prénom d'un personnage non rencontré>` est refusé.
4. Nom déjà pris ou vide : le nom est redemandé ; le nom proposé cliquable se recopie dans le chat.
5. Redémarrer : types, réservations et hauteurs conservés ; listes triées par type.
6. Un ancien bâtiment avec artisan est devenu « Atelier » réservé à cet artisan ; un ancien sans
   artisan est « Divers ».
7. Chez un artisan arrivé en ville, « Choisir son logis » ne montre que les ateliers libres ou
   réservés à lui ; une habitation n'apparaît pas.
8. Clic droit dans la liste d'Alderic (ou `/city build type`) : passer un « Divers » en atelier
   réservé, puis vérifier qu'il apparaît chez le bon artisan.
9. Sans le pack (`/city ui classique`) : `/city build new` affiche les boutons dans le chat
   (seuls les personnages trouvés sont proposés).
10. Console au premier démarrage : « Bâtiments : types ajoutés (n atelier(s) d'artisan…) » puis
    « hauteur estimée pour n bâtiment(s) ».
11. Sans logis : avec une quête en cours chez un artisan installé, supprimez son atelier (liste
    d'Alderic) → annonce dans le chat ; en lui parlant, écran « SANS LOGIS » sans vente ni
    quêtes ; la quête disparaît du panneau et ne progresse plus ; son chapitre reste validé dans
    la touche L. Choisissez-lui un nouvel atelier → menu complet, quête revenue avec sa
    progression d'avant.

**Attention** : après la mise à jour, les anciens bâtiments sans artisan sont « Divers » ; un
artisan arrivé en ville ne pourra s'y installer qu'une fois le bâtiment passé en atelier.

---

## Phase 2 — Inspection des constructions

- **Blocs requis par personnage** dans config.yml (ex. Thorvald : enclume, haut-fourneau, table de
  forgeron ; Hélène : composteur, tonneaux), avec les groupes de `MaterialGroups`.
- **Exigences communes** : une porte, un toit (part du sol couverte), de l'éclairage.
- **Lecture des blocs** : copie des chunks (`ChunkSnapshot`), hors du fil principal pour les
  grandes zones.
- **Niveau** : 0 = incomplet, 1 = conforme, 2 à 5 selon le détail (variété des blocs, blocs
  décoratifs réglables, volume, blocs bonus du métier).
- **Information quand le personnage demande son logis** : pour chaque bâtiment proposé, blocs
  requis ✔/✘ et ce qui manque pour le niveau suivant ; même information dans le panneau de
  quêtes pendant le recrutement ; dialogue d'arrivée qui annonce ses exigences.
- **Installation** refusée tant que le bâtiment est incomplet ; artisans déjà installés conservés
  avec un avertissement « atelier incomplet ».
- **Recalcul** après chaque bloc posé ou cassé dans une zone (regroupé sur quelques secondes) et
  à l'ouverture des menus ; message à chaque changement de niveau.
- **Effet du niveau** : capacité de l'entrepôt de l'artisan (ex. +25 % par niveau, à régler) ;
  `/city eco check` doit rester sans alerte.
- **Option** : les quêtes « N bâtiments » ne comptent que les bâtiments conformes.
- **Commande** : `/city build inspect`.

### Ce qui est codé

Classes `BuildingRequirements` (lecture de config.yml), `BuildingInspector` (lecture des blocs,
cache, nouvelle inspection, annonces, entrepôt) et `Inspection` (résultat et bulles).

**Réglages : section `batiments` de config.yml** (`config-version` 10, ajoutée d'elle-même) :
- `commun` : porte (1), lumière (2 sources), toit (60 % du sol) ; chaque section peut les remplacer
  (le ponton de Martin : toit 20 % ; la mine de Godefroy : pas de porte ; le nid de Harold : ni
  porte ni toit…) ;
- `habitation` (un lit), `mairie` (un pupitre pour la Charte et une bannière), et un atelier par
  personnage (`ateliers.<prénom>`) : `requis` (bloc, nombre, nom affiché) et `bonus` du métier.
  Les 21 ateliers ont des exigences par défaut tirées de leur métier, à ajuster librement
  (ex. Thorvald : enclume, haut-fourneau, table de forgeron ; Hélène : composteur, 2 tonneaux,
  2 bottes de foin ; Isabeau : pupitre, 4 bibliothèques, table de cartographie). « Divers » n'a
  aucune exigence ; un atelier libre n'a que les exigences communes ;
- `terrain` : blocs naturels (tags de Minecraft : roche, terre, sable, gravier, minerais, glace,
  neige, végétation, plus les blocs des grottes) exclus de la **variété** et de la hauteur
  construite — ils comptent toujours pour les exigences (eau du ponton de Martin, glace du nid de
  Harold) et les bonus. Limite connue : une structure souterraine naturelle (mine abandonnée,
  donjon) dans les 40 blocs sous un bâtiment compterait comme construite ;
- `niveaux` (points pour les niveaux 2 à 5 : 15, 30, 50, 75), `points` (variété : 1 par type de
  bloc de construction, terrain exclu, 30 max ; décor : 1 par décoration, 4 du même
  type au plus, objets accrochés et porte-armures compris, 40 max ; bonus du métier : 5 chacun ;
  surface : 1 par 10 blocs de sol sous un toit, 15 max), `decorations` (liste), `bonus-entrepot`
  (+25 % par niveau au-delà du 1er).

**Fonctionnement** :
- Lecture des blocs sur une copie des chunks, sur le fil principal : rapide pour des bâtiments de
  taille normale. La lecture hors du fil principal n'a pas été jugée utile à ce stade ; à revoir
  si des ralentissements apparaissent sur de très grandes zones.
- Toit : pour chaque colonne de la zone, au point le plus bas où l'on peut se tenir, y a-t-il un
  bloc plein au-dessus ? Les murs et piliers ne comptent ni dedans ni dehors ; le feuillage ne
  compte pas comme toit. Lits et portes comptent une fois (tête / bas).
- Résultats gardés en cache ; un bloc posé ou cassé, un seau vidé, un cadre ou un porte-armure posé
  dans la zone relance l'inspection une demi-seconde plus tard (les poses rapides sont regroupées).
- **Pas de message à chaque bloc** : seul le joueur qui fait changer le niveau est prévenu, et
  seulement à ce moment-là — titre + son quand le niveau monte (« Forge est conforme »,
  « Niveau 3 ★★★ »), simple barre d'action quand il baisse. Rien dans le chat.
- Le niveau est enregistré en base (colonne `level`) : l'entrepôt garde son bonus après un
  redémarrage.

**Où les joueurs voient les exigences** :
- piste de recrutement (bulle « Son atelier devra contenir : … ») et annonce d'arrivée en ville ;
- dialogue d'arrivée du personnage : « Pour mon atelier, il me faudra 1 enclume, 1 haut-fourneau
  et 1 table de forgeron, avec une porte, un toit (60 % du sol couvert) et 2 sources de lumière » ;
- « Choisir son logis » : la bulle du bouton et l'icône d'info listent ses exigences ; chaque
  atelier proposé montre son inspection pour ce personnage (✔/✘, niveau, points, prochain
  niveau), anneau rouge s'il est incomplet ; le clic est refusé avec la liste de ce qui manque ;
- **écran « Son atelier »** chez l'artisan installé (clic sur son portrait ; la bulle du portrait
  résume déjà l'état) : rangée 1 = exigences ✔/✘ avec leur icône, rangée 2 = bonus du métier
  présents ou à ajouter, rangée 3 = points de détail (variété, décor, bonus, surface, total et
  paliers), pied de page = ce qu'il faut pour le niveau suivant (« NIV. 2 → 3 : +12 PTS »). Les
  blocs sont relus à l'ouverture. Sans le pack : même détail dans la bulle de sa tête ;
- choix du type d'un nouveau bâtiment et choix de l'artisan d'un atelier (menu, et boutons du chat
  sans le pack) : chaque type et chaque personnage affiche ses prérequis dans sa bulle (les
  personnages pas encore trouvés aussi, sous cadenas) ;
- liste des bâtiments d'Alderic : inspection détaillée si la zone est chargée, sinon dernier
  niveau connu ; anneau rouge = incomplet ; barre d'action en entrant dans un bâtiment ;
- `/city build inspect [nom]` (bâtiment où l'on se trouve, ou par son nom) : rapport complet ;
  une seule ligne à la création d'un bâtiment et au changement de type.

**Bordures (affichage 5 secondes depuis la liste)** : les arêtes sont posées sur la face
extérieure des blocs du bâtiment (la ligne est ou sud passait auparavant à l'intérieur du dernier
bloc), en contour : bas et haut du bâtiment, une ceinture à hauteur des yeux, arêtes des coins ;
visibles du seul joueur qui les a demandées.

Écarts avec le plan : le panneau de quêtes n'affiche pas le recrutement (il ne montre que les
quêtes des habitants) ; les exigences sont donc sur la piste de recrutement à la place. L'option
« quêtes N bâtiments = bâtiments conformes » n'est pas faite (à décider).

### À tester en jeu

1. Démarrage : aucune ligne « [Bâtiments] bloc inconnu » dans la console.
2. Recrutement de Thorvald : la piste (papier 🗺) liste son atelier ; à son arrivée, il annonce
   ses exigences.
3. « Choisir son logis » avec un atelier vide : anneau rouge, bulle ✘ enclume / haut-fourneau /
   table de forgeron / porte / toit / lumière ; clic refusé avec la liste.
4. Poser les blocs un à un dans l'atelier : aucun message tant que rien ne change ; à la pose du
   dernier bloc requis, titre « … est conforme » + son ; l'installation passe.
5. Décorer (pots, tapis, bannières, cadres), varier les blocs, ajouter meule / chaudron de lave :
   titre à chaque niveau franchi ; clic sur le portrait de Thorvald → écran « Son atelier »
   (exigences, bonus, points, « NIV. x → y : +n PTS »).
6. Casser l'enclume : barre d'action « incomplet — il manque 1 enclume », rien dans le chat ;
   Thorvald reste installé, son écran d'atelier montre l'enclume en rouge.
6 bis. Choix du type puis de l'artisan d'un nouvel atelier : chaque bulle liste les prérequis.
6 ter. Bordures (clic sur un bâtiment dans la liste d'Alderic) : les lignes longent l'extérieur
   des murs sur les quatre côtés.
7. Entrepôt : au niveau 3, la capacité affichée dans « Vendre » / « Entrepôt » augmente de 50 % ;
   `/city eco check` reste sans alerte.
8. Toit : un abri sans toit ou un enclos ouvert → « toit ✘ (x % du sol couvert) » ; une maison
   fermée → ✔.
9. Zone de 30×30×20 inspectée plusieurs fois : pas de ralentissement visible (`/city proto tps`).
10. Hauteur (révisée) : délimiter une parcelle vide, puis y construire une tour de 30 blocs et une
    cave de 6 blocs sous terre : `/city build inspect` affiche « Construit : Y (cave) → Y (sommet) »
    sans rien régler ; la variété ne compte ni la pierre ni la terre creusées ; une grotte
    naturelle sous la maison ne change pas le pourcentage de toit ; les bordures (liste d'Alderic)
    vont du sol au sommet de la tour.

## Phase 3 — Habitations et habitants

- **Habitation** : lit(s), porte, éclairage, toit ; un lit (tête) = une place ; niveau par le
  même système.
- **Apparence** : PNJ de type joueur, skin tiré de `habitants.skins` (gardé par l'habitant ; il
  le quitte pour l'uniforme s'il devient soldat).
- **Table `inhabitants`** : nom généré, maison, lit, état (habitant, en formation, soldat, mort),
  date de la mort.
- **Arrivée** toutes les X minutes (joueur connecté, lit libre) : l'habitant arrive à pied depuis
  la lisière.
- **Vie** : promenade le jour, retour chez soi la nuit ; au-delà du plafond de la phase 0, les
  habitants sont comptés mais pas affichés.
- **Mort** : lit libéré, remplaçant après un délai réglable ; lit cassé = départ.
- **Impôt simple** par habitant et par jour de jeu, versé à la caisse.
- **Affichage** : « Habitants 12/16 » dans le panneau Tab (compteur séparé de `residents`).
- **Commandes** : `/city habitants spawn|kill|list`, accélérateur des délais.

### Ce qui est codé

Classe `com.citycore.inhabitant.InhabitantManager` (table `inhabitants`), avec `InhabitantSkins`
et `city.ClaimCache` (chunks de la ville en cache).

- **Places** : chaque lit (tête) d'une **habitation conforme** (inspection de la phase 2 : lit,
  porte, toit, lumière) accueille un habitant. Une habitation devenue incomplète garde ses
  habitants mais n'en accueille plus de nouveaux.
- **Arrivée** : toutes les `arrivee-minutes` (5), s'il reste un lit libre et qu'un joueur est
  connecté, un habitant au prénom médiéval (Jehan, Margot, Guillemette…) s'installe : une ligne
  dans le chat (« Jehan s'installe à Valmont (Maison, lit 2/3) »). Il apparaît sur un chunk de la
  lisière proche et marche jusqu'à sa maison (téléporté si la lisière est à plus de 48 blocs).
- **Vie quotidienne** : le jour, promenade autour de sa maison (réglages de la phase 0 : vitesse,
  pause, rayon), jamais hors de la ville ; la nuit, il rentre et **dort dans son lit** (trait
  « sommeil » de Citizens) ; il se lève au matin. Portes ouvertes par les habitants.
- **Plafond d'affichage** : `max-visibles` (24, faute du relevé de TPS de la phase 0) ; les plus
  proches des joueurs sont affichés, les autres sont comptés (impôt, population) sans PNJ.
- **Dangers** : seuls les monstres peuvent les blesser (coups et flèches des joueurs annulés). La
  nuit (`attaques-nocturnes`), un monstre sans cible à moins de 16 blocs d'un habitant le prend
  pour cible. Mort : une ligne dans le chat, ni butin ni message de mort ; son lit reste **en
  deuil** `remplacement-minutes` (20), puis accueille un nouvel habitant.
- **Départs** : lit cassé (immédiat), maison supprimée ou changée de type → l'habitant quitte la
  ville (une ligne dans le chat).
- **Impôt** : à chaque aube (nouveau jour de jeu), `impot` (2) pièces par habitant vivant versées
  à la caisse, une ligne dans le chat ; un seul versement si le temps saute plusieurs jours.
- **Affichage** : panneau Tab de la cité, ligne « Habitants » = vivants / places (elle comptait
  jusqu'ici les artisans installés) ; liste des bâtiments d'Alderic : « Habitants : 2 / 3 lits
  (1 en deuil) » sur chaque habitation.
- **Persistance** : habitants en base, PNJ recréés chez eux au démarrage (registre Citizens en
  mémoire, rien dans les sauvegardes de Citizens).
- **Commande** `/city habitants` (alias `population`) : bilan pour tous ; actions de test (op) :
  `arrivee` (arrivée immédiate), `kill <nom>` (mort par les mêmes événements qu'un coup de
  monstre), `impot` (versement immédiat), `deuil` (fin de tous les deuils), `recharger` (relit la
  section habitants de config.yml).
- **config.yml** (`config-version` 11, réglages ajoutés d'eux-mêmes sous `habitants`) :
  `arrivee-minutes`, `remplacement-minutes`, `impot`, `max-visibles`, `attaques-nocturnes`.

Le prototype `/city proto` reste en place pour la mesure des TPS et les soldats (phase 5).

### À tester en jeu

1. Une habitation conforme à 3 lits (lits, porte, toit, lumière) : `/city habitants arrivee` trois
   fois (ou attendre) → trois habitants arrivent de la lisière et marchent jusqu'à la maison ; une
   quatrième demande répond « aucun lit libre ».
2. `/city habitants` : bilan « 3/3 », la maison et ses habitants ; panneau Tab « Habitants 3/3 ».
3. Journée : ils flânent autour de la maison sans quitter la ville ; nuit (`/time set night`) :
   ils rentrent et se couchent dans leur lit ; au matin ils se lèvent.
4. Frapper un habitant : aucun dégât. Un zombie la nuit : il le prend pour cible.
5. `/city habitants kill <nom>` : « … a été tué », lit en deuil (bilan) ; `/city habitants deuil`
   puis `arrivee` → un remplaçant arrive.
6. Casser un lit : son habitant quitte la ville aussitôt.
7. `/time set 23000` puis attendre l'aube : « Impôt de l'aube : 3 habitants → +6 coins ».
8. Redémarrer : les habitants réapparaissent chez eux, deuils et bilan conservés.
9. Avec `max-visibles: 2` (`/city habitants recharger`) : 2 PNJ affichés, le bilan en compte 3.
10. Relever `/city proto tps` avec la ville peuplée pour régler `max-visibles`.

### Correctifs (dialogues, point des artisans)

- **Dialogues lettre par lettre** : la barre d'action ne tient que sur une ligne ; une réplique
  plus longue que `dialogues.largeur` (58 caractères, config.yml version 13) est coupée entre deux
  mots et affichée en morceaux successifs, le nom du personnage et la mise en forme repris à
  chaque morceau, « … » en fin de morceau.
- **Artisans qui ne rejoignaient pas leur point** : le point du PNJ et les points de repli étaient
  pris sur le bloc le plus haut de la colonne, donc **sur le toit** des ateliers couverts. Le point
  est désormais posé au-dessus du bloc cliqué (le sol de l'atelier) ; un point enregistré avant ce
  correctif, sans toit au-dessus, est ramené au sol de l'atelier (recherche à partir du sol de
  définition) ; les points de repli suivent la même règle. Les artisans ouvrent aussi les portes.

À tester : une réplique longue (Alderic, Bastien) s'affiche en deux ou trois morceaux sans sortir
de l'écran ; un artisan installé dans un atelier couvert, créé avant ou après ce correctif,
rejoint son point à l'intérieur (y compris après « suivre » puis « arrêter »), en passant la porte.

## Phase 4 — Sièges

**4a. Protection renforcée des claims** (aujourd'hui seules les explosions sont bloquées) : blocs
pris ou modifiés par les monstres (`EntityChangeBlockEvent`), portes enfoncées
(`EntityBreakDoorEvent`), feu (allumage, propagation, combustion), cultures piétinées.

**4b. Calendrier d'événements** (sièges et fêtes) : planning réel réglable (jours, heure,
joueurs minimum), annonce 5 minutes avant, `/city event list|start|stop`.

**4c. Le siège** :
- vagues selon le palier (I-III : zombies, squelettes ; IV-VI : + araignées, pillards, sorcières ;
  VII-VIII : + vindicateurs, évocateurs ; IX-X : + ravageurs et un champion nommé), nombre
  multiplié par les joueurs connectés ;
- premier siège à un palier réglable (proposé : chapitre IV) ;
- apparition hors des chunks revendiqués ; cibles données par le plugin (habitants, soldats,
  sinon la mairie) ;
- barre de siège **rouge** (le panneau de quêtes rend invisibles les barres blanches) ;
- victoire : récompense dans la caisse, XP pour Roland ; défaite : assaillants retirés ;
- assaillants marqués, supprimés en fin de siège ou au redémarrage ;
- `/city siege start [palier]|stop|next`.

À tester : sièges aux paliers 1, 5 et 10 ; aucun bloc touché ; joueurs et artisans indemnes ;
habitants tués puis remplacés ; plus aucun assaillant après un redémarrage en plein siège.

### Ce qui est codé

**4a. Protection renforcée** (`ClaimProtectionListener`, chunks de la ville en cache) — dans la
ville :
- une créature hostile (enderman, ravageur, wither, poisson d'argent, zombie…), un lapin ou un
  projectile qui n'a pas été tiré par un joueur ne peut ni prendre ni modifier un bloc ; les
  blocs qui tombent, les moutons qui broutent et les villageois qui cultivent ne sont pas
  concernés ;
- portes jamais enfoncées ;
- feu : seul un joueur peut en allumer un ; ni propagation, ni combustion de blocs, ni feu de lave,
  de foudre ou de boule de feu ;
- cultures et œufs de tortue jamais piétinés (joueurs et créatures) ;
- tableaux, cadres et porte-armures protégés de tout ce qui n'est pas un joueur (monstres,
  explosions, flèches perdues des gardes).

Limite : un monstre ajouté par un mod de Youer qui modifie les blocs sans passer par les
événements de Bukkit n'est pas arrêté (non vérifiable sans le mod).

**4b. Calendrier** (`siege.EventCalendar`, section `evenements`) : créneaux en heure réelle
(fuseau `Europe/Paris` par défaut) — type, jours (`lundi` … `dimanche`, `tous`), heure,
joueurs minimum. Par défaut : siège le mercredi et le samedi à 21:00, 2 joueurs minimum.
Annonce dans le chat (et cloche) `annonce-minutes` (5) avant ; à l'heure dite, lancement s'il y
a assez de joueurs, sinon « Siège annulé : 1 joueur connecté, il en faut 2 ». Pas d'annonce tant
que la ville n'a pas atteint `premier-palier`. Les fêtes de la phase 7 s'ajouteront comme un
nouveau type. `/city event list` (tous) : planning, prochain rendez-vous et compte à rebours ;
op : `start [siege]`, `stop`, `recharger`.

**4c. Le siège** (`siege.SiegeManager`, réglages `siege.SiegeConfig`, section `sieges`) :
- **Alerte** : titre « ⚔ Siège ! », cor de raid, message (chapitre, nombre de vagues, côté
  attaqué), puis `preparation-secondes` (30) avant la première vague.
- **Vagues** : `vagues` selon le palier (2 aux chapitres I-III, 3 aux IV-VI, 4 aux VII-VIII, 5
  aux IX-X). Assaillants par vague : (4 + palier) × (1 + 0,5 × (joueurs connectés − 1)), 40 au
  plus. Monstres tirés au hasard selon des poids par palier : zombies et squelettes ; + araignées,
  pillards, sorcières (IV) ; + vindicateurs, évocateurs (VII) ; + ravageurs (IX). À partir du
  chapitre IX, la dernière vague est menée par un **champion** nommé, lumineux, 120 PV, hache en
  netherite (« Gorvak le Brise-Portes »). Répit de `pause-vagues-secondes` (30) entre deux vagues.
- **Côté attaqué** : chaque vague arrive d'un bord de la ville, de préférence proche des joueurs,
  annoncé (« par le nord-est »). Les assaillants apparaissent **hors des chunks revendiqués**, à
  `distance-apparition` (12) blocs au-delà de la lisière, au sol (jamais dans l'eau), sans
  équipement ni monture au hasard (pas de poulet-jockey) ; squelettes, pillards et vindicateurs
  reçoivent leur arme (jamais lâchée). Ils ne brûlent pas au soleil, ne ramassent rien, ne
  rejoignent pas de raid, ne disparaissent pas d'eux-mêmes.
- **Cibles** : l'habitant, la recrue ou le garde le plus proche (48 blocs) ; sinon ils marchent sur
  la **mairie** (centre du bâtiment de type mairie), par étapes. Les joueurs restent invulnérables
  en ville, les artisans aussi.
- **La garde** : pendant un siège, les gardes qui ne voient pas d'ennemi accourent au pas de
  course vers le côté attaqué, puis font leur ronde autour de ce point ; ils attaquent tout
  monstre hostile qu'ils voient dans la ville.
- **Barre rouge** en haut de l'écran (le panneau de quêtes masque les barres blanches) : « Vague
  2/3 · 12 assaillants · par le nord · Gorvak · 14:21 restantes », compte à rebours pendant
  l'alerte et les répits.
- **Victoire** (toutes les vagues repoussées avant `duree-minutes`, 20 min après l'alerte) :
  titre, `coins` + `coins-par-palier` × palier dans la caisse (150 + 50 × palier), XP pour
  Roland (40 + 10 × palier ; annonce s'il monte de rang), bilan : meilleurs défenseurs,
  monstres abattus par la garde, habitants et gardes perdus. **Défaite** (temps écoulé) : les
  assaillants se retirent, pas de récompense, même bilan. Plus aucun joueur connecté : siège
  abandonné.
- **Nettoyage** : les assaillants sont marqués et jamais enregistrés avec le monde ; ceux d'un
  siège interrompu (arrêt du serveur, plantage) sont retirés au démarrage et au chargement de
  leur chunk. Un assaillant dont le chunk se décharge en plein siège réapparaît du côté
  attaqué ; un zombie noyé ou un squelette gelé reste un assaillant.
- `/city siege` (tous) : état du siège ; op : `start [palier]` (sans palier minimal, utile pour
  tester les chapitres 1, 5 et 10), `stop`, `next` (vague suivante).

**config.yml** (`config-version` 15) : sections `evenements` et `sieges` ajoutées d'elles-mêmes
avec leurs commentaires ; garde portée à 50 (voir phase 5).

### À tester en jeu

1. `/city event list` : planning, prochain siège, compte à rebours ; avant le chapitre IV, la
   mention « Valmont est encore trop modeste ».
2. Créneau de test : ajouter `- {type: siege, jours: [tous], heure: "HH:MM", joueurs-min: 1}`
   quelques minutes plus tard, `/city event recharger` → annonce 5 min avant, siège à l'heure (ou
   « Siège annulé » avec `joueurs-min: 2` et un seul joueur).
3. `/city siege start 1` : alerte, barre rouge, première vague par le côté annoncé, apparition
   hors de la ville ; les gardes accourent ; les assaillants visent habitants et gardes.
4. Pendant le siège : aucun bloc touché (portes, cultures, cadres) ; un joueur en ville ne prend
   aucun dégât ; les artisans non plus.
5. Un habitant tué → lit en deuil puis remplacement (phase 3) ; le bilan compte la perte.
6. `/city siege next` jusqu'à la victoire : récompense dans la caisse, XP de Roland, bilan.
7. `/city siege start 5` puis `start 10` : pillards et sorcières, puis vindicateurs, évocateurs,
   ravageurs et le champion (nom, lueur, annonce de sa chute).
8. Attendre la fin du temps (`duree-minutes: 2` puis `/city event recharger`) : défaite, les
   assaillants disparaissent.
9. Redémarrer le serveur en plein siège : plus aucun assaillant au retour, ni barre.
10. Hors siège : un enderman ne prend plus de blocs, le feu ne se propage pas, un zombie
    n'enfonce pas une porte, sauter sur une culture ne la piétine pas (ville uniquement).

### Correctifs (front de la garde, pillards, retour en ville, armée ennemie)

- **La garde en position** : avant, un garde finissait sa ronde avant de partir, au pas, puis
  errait au hasard autour du point de ralliement. Désormais, dès l'alerte et pendant chaque répit,
  chaque garde reçoit un **poste** le long du bord attaqué (ce chunk de lisière et ses voisins) :
  soldats et sergents en première ligne (2 et 4 blocs à l'intérieur de la lisière), archers en
  retrait (7 et 9 blocs), un poste tous les 2 blocs, au sol, hors des bâtiments, du centre vers
  les ailes. Il interrompt sa ronde et y court ; bloqué plus de 30 s (mur, rivière), il y est
  téléporté. Sur place, il reste immobile face à l'ennemi. Il quitte son poste pour combattre un
  ennemi entré dans la ville (16 blocs), puis y revient. Pendant un siège, les archers tirent aussi
  sur les ennemis qui approchent hors de la ville, sans quitter leur poste. Quand la vague
  suivante vient d'ailleurs, la garde change de front pendant le répit.
- **Pillards immobiles** : sans défenseur à moins de 48 blocs, les assaillants recevaient un point
  de marche pris à leur propre hauteur (souvent dans le vide ou dans le sol) : le chemin échouait,
  et les pillards, sorcières et évocateurs ne se déplacent pas d'eux-mêmes vers une cible
  lointaine. Désormais les étapes de marche (24 blocs) sont prises au sol, et toutes les 2 s chaque
  monstre est relancé vers sa cible tant qu'il n'est pas à portée (2,5 blocs au contact, 10 pour
  les tireurs) ou qu'il ne la voit pas.
- **Fin du siège** : les gardes quittent le front, rentrent vers leur quartier (un point de ronde
  près de leur lit, 90 s au plus), puis reprennent leurs rondes habituelles.
- **Types de siège** (`sieges.types`, tiré au hasard à chaque siège, 1 chance sur 2 par défaut) :
  - **monstres** (comme avant) ;
  - **bannière** : l'armée d'une **faction ennemie** (`sieges.banniere.factions` : l'Ost du
    Corbeau, les Lames de Grisval, la Horde des Marais ; nom et couleur), faite de soldats PNJ
    comme la garde (`sieges.banniere.unites` : milicien, archer, soldat, arbalétrier, chevalier ;
    vie, combat, arme et armure), d'autant plus nombreux et aguerris que la ville est avancée
    (`composition` par palier, nombre comme `sieges.assaillants`). Chaque vague est ouverte par un
    **porte-étendard** (bannière de la faction sur la tête ; sa chute est annoncée) ; à partir du
    chapitre VII, la dernière vague est menée par un **champion** (« Morvan le Noir », 140 PV,
    armure et épée en netherite, lumineux). Ils visent habitants et gardes (sinon les joueurs à
    moins de 24 blocs), au contact ou à l'arc / l'arbalète, marchent sur la mairie faute de
    cible, n'ouvrent pas les portes (une maison fermée protège ses habitants) et ne sont jamais
    téléportés quand ils sont bloqués. Pas de tirs entre eux ; ni butin ni message de mort ;
    jamais enregistrés. Les gardes les combattent comme des monstres, les joueurs peuvent les
    frapper. Uniformes : `sieges.banniere.skins` (vide = skins par défaut de Minecraft).
- `/city siege start [palier] [monstres|banniere]` ; `/city event recharger` relit aussi les
  uniformes ennemis. config.yml version 16 : `types` et `banniere` ajoutés d'eux-mêmes sous
  `sieges`.

À tester :
1. `/city siege start 1 monstres` : pendant les 30 s d'alerte, les gardes interrompent leur ronde
   et courent se placer en ligne sur le bord annoncé (archers derrière), face à l'extérieur.
2. Les pillards (`/city siege start 5 monstres`) avancent vers les habitants ou la mairie.
3. Après la victoire (`/city siege next` jusqu'au bout) ou `/city siege stop` : les gardes
   rentrent vers leurs maisons, puis reprennent leurs rondes.
4. `/city siege start 4 banniere` : message et barre au nom de la faction ; porte-étendard avec
   la bannière ; miliciens, soldats et archers ennemis attaquent habitants et gardes ; les gardes
   ripostent ; un joueur peut les abattre ; ils ne se blessent pas entre eux.
5. `/city siege start 8 banniere` : arbalétriers, chevaliers et le champion Morvan le Noir à la
   dernière vague.
6. Un habitant dans une maison à porte fermée est hors d'atteinte des soldats ennemis.

### Correctifs (combats entre soldats, dégâts, ambiance sonore)

- **Gardes et soldats ennemis s'ignoraient, aucun dégât entre eux ni sur les joueurs** : la
  fonction de Citizens qui dit si une entité est un PNJ d'un registre répond « oui » pour
  **n'importe quel** PNJ, quel que soit son registre. Résultat : chaque garde ou habitant était
  aussi pris pour un « soldat ennemi » (et inversement) ; les coups entre « soldats ennemis »
  étant bloqués, tous les coups de PNJ à PNJ l'étaient ; un coup de soldat ennemi sur un joueur
  passait pour un coup de garde (bloqué) ; un coup de joueur sur un soldat ennemi passait pour un
  coup sur un habitant (bloqué). Chaque gestionnaire vérifie désormais que le PNJ lui appartient.
- **Contrôle PvP** : pour Minecraft, habitants, gardes et soldats ennemis sont des joueurs ; si le
  PvP est coupé sur le serveur, leurs coups et leurs flèches entre eux n'ont aucun effet. Les coups
  sont donc portés par le plugin (`inhabitant.NpcCombat`) : dégâts de l'arme en main (épée de
  diamant 7…), armure prise en compte, recul, son de coup. Flèches : 4,5 (arc), 6 (arbalète). Un
  joueur qui frappe un soldat ennemi alors que le PvP est coupé : coup porté de même (recharge de
  l'attaque et coup critique en chute compris).
- **Riposte** : un soldat ennemi frappé par un garde ou un joueur (16 blocs) se retourne contre
  lui ; les gardes combattent tout soldat ennemi en vue dans la ville. L'auteur de chaque coup est
  retenu : le bilan compte bien les ennemis abattus par la garde et par chaque joueur.
- **Joueurs blessés pendant un siège** (`sieges.joueurs-blesses`, activé) : pendant un siège, les
  monstres et soldats ennemis peuvent blesser les joueurs, même en ville ; hors siège, les joueurs
  restent invulnérables en ville. `false` : invulnérables en ville même pendant un siège.
- **Ambiance sonore** (`siege.SiegeSounds`) :
  - alerte : trois coups de cloche (et la cloche de la mairie qui résonne), puis le **cor de
    guerre** venu du côté attaqué (cor de raid pour les monstres, cor de chèvre pour une armée) ;
    Roland : « Gardes, à vos postes ! Ils viennent par le nord ! » ;
  - compte à rebours avant chaque vague : un coup de tambour par seconde sur les 5 dernières, « 3,
    2, 1 » à l'écran ;
  - début de vague : cor, puis cri de guerre (acclamations d'une armée, rugissement de monstres) ;
    grondement ou second cor quand le champion arrive ;
  - pendant la vague : **tambours de guerre** toutes les 3 s, venus du camp ennemi ; son de coup à
    chaque coup porté entre combattants ;
  - porte-étendard abattu : bouclier brisé et fanfare ; champion abattu : fanfare ;
  - vague repoussée : deux coups de cloche, fanfare ;
  - victoire : fanfare, cloches à toute volée et **feux d'artifice** au-dessus de la mairie (aux
    couleurs de Valmont, éclatant très haut) ; défaite : grondement sourd et cloche qui résonne.
  Les sons « venus de » sont joués à 12 blocs de chaque joueur dans la direction de leur source :
  on les entend de partout et on sait d'où ils viennent.
- config.yml version 18 : `joueurs-blesses` ajouté de lui-même sous `sieges`.

À tester :
1. `/city siege start 4 banniere` : alerte (cloches, cor du côté annoncé, ordre de Roland),
   compte à rebours, cor et cris au début de la vague, tambours pendant le combat.
2. Les gardes engagent les soldats ennemis (coups, flèches, PV qui baissent) ; les soldats
   ennemis ripostent contre les gardes qui les frappent ; ils ne se blessent pas entre eux.
3. Les soldats ennemis blessent habitants et gardes, et les joueurs (en ville comme dehors) ;
   avec `joueurs-blesses: false` (`/city event recharger`), plus en ville.
4. Un joueur abat un soldat ennemi à l'épée et à l'arc ; le bilan de fin le compte.
5. Victoire : feux d'artifice au-dessus de la mairie ; défaite (`duree-minutes: 2`) : son sourd.

### Correctifs (plantage du serveur, battement de cœur, gardes de test)

- **Plantage « x not finite »** : un coup porté par le plugin n'avait ni attaquant ni point
  d'origine ; sur Youer, le recul calculé à partir de là donnait une vitesse invalide (NaN) à la
  victime, puis le serveur plantait en l'envoyant aux joueurs. Le dégât part désormais de la
  position de l'attaquant (le jeu en tire le recul), et toute vitesse invalide est remise à zéro
  aussitôt après le coup, ainsi qu'à chaque passage pour chaque habitant, garde et soldat ennemi.
- **Battement de cœur** pendant les vagues, pour chaque joueur, de plus en plus rapide à mesure
  que les assaillants approchent : toutes les 1,75 s au loin, 1,25 s à moins de 48 blocs, 1 s à
  moins de 24 (plus fort), 0,75 s à moins de 10. Les tambours de guerre venus du camp ennemi sont
  plus forts.
- **Gardes de test** : le soldat de `/city proto soldat` venait du prototype de la phase 0 (il ne
  faisait que frapper les monstres proches : ni postes, ni front, ni soldats ennemis). Désormais :
  - `/city garde test [nombre] [rôle]` (op) crée jusqu'à 20 **vrais gardes** près du joueur,
    tout de suite : rondes, front d'un siège, combat, retour en ville… comme un garde formé chez
    Roland ; sans lit ni formation, hors limite de la garde, jamais enregistrés (ils disparaissent
    au redémarrage) ; « (test) » dans `/city garde` ; leur mort n'ouvre pas de deuil ;
  - `/city garde test retirer` les retire ; `/city proto clear` aussi ;
  - `/city proto soldat [rôle]` crée maintenant un garde de test.
  Ils comptent dans la taille de la garde (menu de Roland) tant qu'ils existent.

À tester : `/city garde test 6`, `/city garde test 3 archer`, puis `/city siege start 4 banniere` :
les gardes de test prennent position sur le front, combattent les soldats ennemis, rentrent en
ville à la fin ; aucun plantage pendant les combats ; battement de cœur qui s'accélère quand
l'ennemi approche.

### Correctifs (sons, sortie de la garde, assaillants en ville)

- **Sons** (remplace l'ambiance sonore et le battement de cœur ci-dessus, demande mal comprise) :
  des sons **au début et à la fin seulement**, plus la chute de la bannière :
  - début du siège : cloches d'alarme, cor de guerre venu du côté attaqué ;
  - porte-étendard abattu : bouclier brisé et fanfare ;
  - fin : victoire (fanfare, cloches, feux d'artifice) ou défaite (grondement sourd).

  Retirés : battement de cœur, tambours, compte à rebours sonore (le « 3, 2, 1 » reste à
  l'écran), cor et cris à chaque vague, cloches entre les vagues, sons du champion, son ajouté à
  chaque coup porté. Restent les sons naturels du jeu (coups reçus, tir d'arc et d'arbalète).
- **La garde sort combattre** : pendant l'alerte et les répits, elle tient le front (inchangé) ;
  dès qu'une vague arrive, chaque garde quitte son poste et **sort de la ville** à la rencontre de
  l'assaillant le plus proche (monstre ou soldat, jusqu'à 64 blocs) : les soldats et sergents
  l'engagent au contact, les archers avancent jusqu'à portée de tir. Ils marchent au combat (par
  étapes, jamais téléportés). Quand il n'y a plus d'assaillant (répit), ils reviennent à leurs
  postes ; à la fin du siège, ils rentrent en ville et reprennent leurs rondes.
- **Tous les assaillants entrent dans la ville** : hors de la ville, les tireurs (archers et
  arbalétriers ennemis, squelettes, pillards, sorcières, évocateurs) ne s'arrêtent plus à la
  lisière pour tirer de loin : ils marchent vers la mairie (ou le côté attaqué) ; en chemin, un
  soldat tireur ne tire que sur une cible à moins de 10 blocs. Une fois en ville, ils combattent
  normalement. Les soldats au contact attaquent l'habitant ou le garde le plus proche (y compris
  les gardes sortis à leur rencontre), sinon marchent sur la mairie.

À tester : `/city garde test 6` et `/city garde test 3 archer`, puis `/city siege start 4 banniere`
et `/city siege start 5 monstres` : pendant l'alerte, la garde se met en ligne ; à l'arrivée de la
vague, elle sort et va au contact ; les archers ennemis et les pillards entrent dans la ville ;
seuls l'alerte, la chute de la bannière et la fin font du son.

## Phase 5 — La garde

Faite **avant la phase 4** (choix du 11/10/2026) : la défense pendant les sièges viendra avec la
phase 4.

- Écran « La garde » chez Roland : soldats / maximum, recrues en formation, « Former une recrue ».
- Maximum selon le rang de Roland : 8, 16, 25, 36, 50 (au lieu de 2 à 12, jugé trop peu) ;
  formations simultanées 2, 3, 4, 5, 6.
- Formation : un habitant part à l'atelier de Roland, X minutes (comptées avec un joueur
  connecté), il en ressort armé et en uniforme (skin de `soldats.skins`).
- Un soldat garde son lit : population (habitants + soldats) ≤ lits.
- Patrouille automatique sur le terrain de la ville ; attaque les monstres proches ; se soigne au
  repos. Pendant un siège, rejoint le côté attaqué (phase 4).
- Mort : place libérée, il faut former une nouvelle recrue ; son lit accueillera un habitant.

### Ce qui est codé

**Menus** (pixel art du pack, voir `docs/INTERFACES_VALMONT.md`) :
- Accueil d'un artisan installé : un clic sur le **portrait** ouvre la **gestion du bâtiment**,
  variante de l'écran d'accueil (cadre turquoise, médaillon « maison »). Le menu de droite n'y
  propose plus la vente, l'entrepôt… mais **Logis** (l'écran « Son atelier » de la phase 2),
  **Actions** et **Son histoire** ; la barre montre la progression de l'atelier vers le niveau
  suivant. Un clic sur le portrait revient au commerce. Vrai pour tous les artisans.
- **Actions** : « La garde » chez Roland ; « Aucune action pour l'instant » chez les autres (place
  prévue pour les actions des phases suivantes).
- **La garde** : liste **commune à tous les joueurs** des gardes (anneau violet), des recrues
  (ambre) et des habitants à former (vert ; gris s'il n'y a plus de place), têtes au skin de
  chacun (uniforme pour un garde), pied de page « GARDE 3/4 · FORMATION 1/1 ». Clic sur un
  habitant → **Rôle** ; **Maj + clic droit** sur un garde ou une recrue → il redevient habitant.
- **Rôle** : rôles de `soldats.roles` (combat, vie, durée, équipement) ; ceux d'un rang supérieur
  à celui de Roland sont verrouillés. Un clic lance la formation (annonce dans le chat).

**Règles** (`InhabitantManager`, réglages `GuardConfig`) :
- Formation seulement si Roland est **installé** dans son atelier ; taille de la garde (gardes +
  recrues) et nombre de formations simultanées selon son rang (`soldats.max`,
  `soldats.formations`). Le temps de formation n'avance que si un joueur est connecté (et que
  Roland a son atelier) ; il est enregistré, un redémarrage ne le perd pas.
- **Recrue** : elle se place **à côté de Roland** (à 2 blocs environ, une place par recrue) et
  s'y entraîne jour et nuit, avec son skin d'habitant (voir « Correctifs » plus bas). À la fin :
  elle devient **garde** — uniforme
  (`soldats.skins`, distribué à tour de rôle), équipement et points de vie de son rôle, annonce.
- **Garde** : toujours affiché (hors plafond `max-visibles`). Il **patrouille** sur le terrain de
  la ville (points pris au sol, dans les chunks revendiqués, **hors de la zone de tout
  bâtiment**, sans ouvrir les portes), de jour comme de nuit, **sans dormir**. Il repère les
  monstres hostiles dans la ville (`vision`, 16 blocs) : un soldat ou un sergent les attaque au
  contact, un archer tire à l'arc à `portee-arc` (14 blocs, une flèche toutes les 1,5 s, flèches
  non ramassables). Il ne poursuit pas un monstre hors de la ville. Au repos, il regagne 1 PV
  toutes les 5 s.
- Les gardes ne blessent que les monstres (ni joueur, ni habitant, ni animal) ; les joueurs ne
  blessent pas les gardes. La nuit, les monstres proches les prennent pour cible comme les
  habitants.
- Un garde **garde son lit** (la population ne dépasse pas le nombre de lits) et **ne paie pas
  l'impôt** (seuls les habitants civils le paient). Mort : annonce avec son rôle, lit en deuil
  puis un nouvel habitant arrive — **pas de remplacement automatique** du garde.
- Un garde renvoyé (menu ou commande) redevient habitant, avec son ancien skin, dans son lit.

**Commande** `/city garde` (alias `guard`) : `list` (bilan), `former <habitant> <rôle>`,
`renvoyer <nom>` — ouvertes à tous, comme le menu ; actions de test (op) : `fin` (fin immédiate
des formations), `kill <nom>`. Complétion des noms et des rôles.

**config.yml** (`config-version` 14, clés ajoutées d'elles-mêmes sous `soldats`, skins conservés) :
`max`, `formations`, `vitesse-patrouille`, `vision`, `portee-arc`, `roles` (soldat rang 1, archer
rang 2, sergent rang 4 : nom, rang, minutes, vie, combat `melee`/`distance`, arme et armure).
Base : colonnes `role`, `training_left`, `uniform` ajoutées à la table `inhabitants`.

### À tester en jeu

1. Accueil de n'importe quel artisan installé → clic sur le portrait : écran de gestion (Logis,
   Actions, Son histoire, barre de l'atelier) ; clic sur le portrait → retour au commerce ;
   Logis → « Son atelier », dont la flèche revient à la gestion.
2. Actions chez un autre artisan : « Aucune action pour l'instant ».
3. Roland (rang 1) → Actions → La garde : les habitants apparaissent avec leur tête ; pied de
   page « GARDE 0/8 · FORMATION 0/2 ».
4. Clic sur un habitant → Rôle : Soldat disponible, Archer (rang 2) et Sergent (rang 4)
   verrouillés ; choisir Soldat → annonce, la recrue marche jusqu'à l'atelier de Roland et y
   reste (y compris la nuit, sans dormir).
5. Un second joueur ouvre la même liste : il voit la recrue (menu commun) ; il lance une
   deuxième formation, la troisième est refusée (« Roland forme déjà 2 recrue(s) à la fois »).
6. `/city garde fin` : la recrue change de skin (uniforme), reçoit épée et armure de fer ;
   bilan `/city garde`.
7. Le garde patrouille dans la ville, hors des bâtiments, de jour comme de nuit (`/time set
   night`) ; il ne franchit pas les portes.
8. Un zombie dans la ville : le soldat l'attaque au contact ; avec Roland au rang 2, former un
   archer, qui tire de loin ; un joueur placé dans la ligne de tir n'est pas blessé.
9. Frapper un garde : aucun dégât. Le garde blessé regagne ses PV au repos.
10. Maj + clic droit sur le garde (ou `/city garde renvoyer <nom>`) : il redevient habitant avec
    son ancien skin ; l'impôt de l'aube le compte de nouveau.
11. `/city garde kill <nom>` : « …, soldat est tombé », lit en deuil, pas de nouveau garde ;
    après le deuil, un habitant arrive dans ce lit.
12. Redémarrer pendant une formation : la recrue reprend où elle en était ; les gardes
    réapparaissent près des joueurs présents en ville.
13. Déloger Roland (supprimer son atelier) : la formation se met en pause, aucune nouvelle
    formation possible.
14. Vérifier sur Youer : combat au contact et tir à l'arc des PNJ Citizens (non testés à la
    phase 0 pour l'arc).

### Correctifs (formation visible, couverture du sol, réveil des habitants)

- **Formation visible** : la recrue rejoint une place à environ 2 blocs de Roland (là où il se
  tient s'il est dans son atelier, sinon son point de travail), dans l'atelier ; chaque recrue a
  sa place. Si le chemin échoue pendant 20 s (porte, comptoir), elle y est téléportée. Sur
  place, elle s'entraîne face à Roland : épée de bois (soldat, sergent) ou arc (archer), gestes
  d'arme trois temps sur quatre, coup balayé ou étincelles, son discret. Au-dessus de son nom,
  un **minuteur** : « ⚔ Soldat en formation », barre de progression sur 10 crans et temps restant
  (m:ss), « ⏸ » en rouge quand la formation est en pause (personne en ligne, Roland sans
  atelier). Le minuteur n'est jamais enregistré avec le monde.
- **Couverture du sol** (inspection, phase 2) : un sol était compté « couvert » seulement s'il
  restait 2 blocs libres au-dessus de lui ; dans une pièce de 2 blocs de haut, un meuble posé
  (coffre, table, tapis…) faisait chercher le sol plus haut, sur le toit, compté à ciel ouvert.
  Les murs étaient aussi comptés à ciel ouvert. Désormais : premier vide au-dessus du sol, bloc
  solide plus haut → couvert, quelle que soit la hauteur sous plafond ; le haut d'un mur, d'un
  pilier ou d'un toit (2 blocs construits empilés ou plus sous le vide) n'est compté ni dedans ni
  dehors. Un bâtiment entièrement couvert approche donc 100 %.
- **Habitant resté couché** : le trait « sommeil » de Citizens garde le lit en mémoire quand le
  PNJ disparaît (plafond d'affichage, chunk déchargé) et le recouche à sa réapparition, alors que
  le plugin le croyait levé ; un habitant formé en pleine nuit restait aussi couché. Désormais le
  sommeil (et la posture « couché ») est retiré au réveil et à chaque disparition ; au réveil,
  l'habitant se lève à côté de son lit, et s'il est encore couché un instant plus tard, il
  réapparaît debout.

À tester : former un habitant → il marche jusqu'à Roland, s'arrête à côté de lui et s'entraîne
(gestes, particules), minuteur au-dessus de la tête qui décompte ; deux recrues ont deux places
différentes ; minuteur « en pause » si on déloge Roland ; il disparaît à la fin (`/city garde
fin`). Couverture : un bâtiment au toit complet, pièce de 2 blocs de haut meublée → près de
100 % (`/city build inspect <nom>`). Réveil : nuit puis matin (`/time set 0`) ; s'éloigner pour
que les habitants disparaissent la nuit, revenir le matin → tous debout ; former un habitant
qui dort → il se lève et part chez Roland.

## Phase 6 — Besoins et satisfaction

- **Nourriture** : consommation quotidienne dans les stocks d'Hélène et de Rosalie (remplace en
  partie leur consommation abstraite de 8 % par heure).
- **Sécurité** : soldats par habitant, résultat des derniers sièges.
- **Santé** : stock de Frère Anselme.
- **Confort** : niveau des habitations.
- Un besoin ne compte qu'une fois son artisan installé.
- Satisfaction 0-100 % : vitesse d'arrivée, montant de l'impôt, départs sous un seuil.
- Écran « Population » chez Alderic ; ligne « satisfaction » dans le panneau Tab.
- Équilibrage : revenus par heure face aux chartes (24 650 au total), `/city eco check`.

À tester : stock d'Hélène vide = impôt en baisse puis départs ; siège perdu = sécurité en baisse ;
maisons améliorées = confort en hausse.

### Ce qui est codé

Paquet `com.citycore.needs` : `NeedsManager` (besoins, satisfaction, aube) et `NeedsConfig`
(section `besoins` de config.yml).

**Les quatre besoins** (0 à 100 %) :
- **Nourriture** : à chaque aube, chaque habitant (gardes et recrues compris) mange
  `rations-par-habitant` (4) rations, prises dans les entrepôts d'**Hélène** (cultures : blé,
  carottes, pommes de terre, betteraves 1 ration ; pomme 2, citrouille 3, tranche de melon et
  baies 0,5) et de **Rosalie** (viandes : bœuf et porc 3, mouton, poulet, lapin 2, œuf 1,
  champignons 0,5), proportionnellement à ce que contient chaque entrepôt. Valeur = rations
  obtenues / demandées, **lissée** (moitié la veille, moitié le jour même). Avant le premier repas
  : ce que donneraient les réserves.
- **Sécurité** : gardes par habitant (1 pour `habitants-par-garde` = 5 donne 100 %) et, pour
  `poids-sieges` (40 %), l'issue des `sieges-retenus` (3) derniers sièges (repoussé 100 %, perdu
  0 %) ; sans siège encore, la garde seule.
- **Santé** : à chaque aube, `soins-par-habitant` (1) soin, pris dans l'entrepôt de **Frère
  Anselme** (fiole de miel 2, rayon de miel 1, verrue du Nether 1, poudre lumineuse 1, œil
  d'araignée 1, crème de magma 2, bâton de blaze 3, larme de ghast 4…) ; lissée de même.
- **Confort** : moyenne, sur les habitants, du confort de leur maison selon son niveau
  d'inspection (incomplète 20 %, niveau 1 : 40 %, 2 : 55, 3 : 70, 4 : 85, 5 : 100 %).
- Un besoin ne compte qu'une fois son artisan **installé** (nourriture : Hélène ou Rosalie, et
  seuls les entrepôts des artisans installés servent ; sécurité : Roland ; santé : Frère
  Anselme) ; le confort compte dès qu'il y a des habitants.
- La consommation abstraite des entrepôts d'Hélène, de Rosalie (3 %/h) et de Frère Anselme
  (4 %/h) remplace les 8 %/h de `npc-economy` : ce sont surtout les repas et les soins qui
  vident leurs entrepôts et relancent la demande.

**Satisfaction** = moyenne pondérée des besoins comptés (`poids` : nourriture 35, sécurité 25,
santé 15, confort 25) ; « — » sans habitant. Ses effets (version d'origine, **remplacée** : voir
« Correctifs (annexe, satisfaction…) » dans la section Emplois — plus de départs, arrivées jamais
arrêtées) :
- **arrivées** : délai entre deux arrivées × 0,6 dès 80 %, × 1 dès 50 %, × 1,6 dès 30 %, plus
  aucune arrivée en dessous ;
- **impôt** : × 1,5 dès 80 %, × 1 dès 50 %, × 0,5 dès 30 %, rien en dessous ;
- **départs** : sous `depart-sous` (25 %), un habitant civil (jamais un garde) quitte la ville à
  chaque aube, en donnant la raison du besoin le plus bas (« on y a faim », « on ne s'y sent pas
  en sécurité », « on y manque de soins », « les maisons y sont trop rudimentaires »).

**L'aube** : repas et soins, puis une ligne dans le chat (« Aube à Valmont : satisfaction 72 %
(nourriture 80 %, sécurité 60 %, santé —, confort 70 %) · repas : 48/64 rations »), départ
éventuel, puis l'impôt (avec son facteur).

**Affichage** :
- écran **« Population »** chez Alderic : un clic sur son **portrait** (la bulle du portrait donne
  la satisfaction). Rangée 1 : les quatre besoins (anneau vert ≥ 70 %, ambre ≥ 40 %, rouge en
  dessous, gris non compté ; bulle : valeur, détail, comment l'améliorer), la satisfaction et ses
  effets (arrivées, impôt par aube, départs) ; rangée 2 : habitants, places, gardes, recrues,
  deuils ; rangées 3-4 : chaque habitation (niveau, habitants, confort). Pied de page
  « SATISFACTION 72 % · 16 HABITANTS ». Menu classique (sans le pack) : une case « Population »
  dont la bulle donne le bilan, un clic l'affiche dans le chat ;
- panneau **Tab** de la cité : ligne **« Satisfaction »** avec sa barre et le pourcentage (vert,
  ambre, rouge) ; en-tête texte sans le pack : « Habitants : 12/16 (72 % satisfaits) ».

**Commandes** : `/city habitants besoins` (tous) : satisfaction, chaque besoin, effets ; op :
`/city habitants aube` (repas, soins, bilan, départs et impôt tout de suite), `/city habitants
recharger` relit aussi la section `besoins`. **Équilibrage** : `/city eco check` affiche l'impôt
par aube (≈ 3 aubes par heure) face aux chartes restantes (24 650 coins au total).

**Base** : tables `city_state` (nourriture et santé lissées, derniers repas) et `siege_results`
(issue de chaque siège mené à son terme). config.yml version 19 : section `besoins` ajoutée
d'elle-même avec ses commentaires.

### À tester en jeu

1. Clic sur le portrait d'Alderic : écran « Population » (besoins, effets, population,
   habitations) ; retour à la mairie. Panneau Tab : ligne « Satisfaction ».
2. Avec Hélène installée et son entrepôt garni : `/city habitants aube` → « repas : 64/64
   rations », nourriture 100 %, son entrepôt diminue. Entrepôt vidé (rachetez son stock depuis son
   écran « Entrepôt », ou laissez les aubes le consommer) puis deux aubes : nourriture qui baisse
   (lissée), impôt réduit, puis départs sous 25 %.
3. Sans Frère Anselme installé : santé « non comptée » (anneau gris), sans effet sur la
   satisfaction ; installé avec un entrepôt vide : santé à 0 %.
4. Roland installé, 1 garde pour 10 habitants : sécurité 50 % ; un siège perdu
   (`duree-minutes: 2`) : sécurité en baisse ; une victoire la fait remonter.
5. Une maison améliorée (niveau 1 → 3) : confort en hausse.
6. Satisfaction ≥ 80 % : arrivées plus rapides (écran, case « Arrivées ») et impôt × 1,5.
7. `/city eco check` : lignes « Impôt » et « Chartes ».

## Hors phase — Emplois : les habitants travaillent pour les artisans

Demande : la ville doit s'autogérer, sans que le jeu se résume à produire de la nourriture pour
la colonie. Les habitants peuvent être embauchés par un artisan et remplissent son entrepôt.

### Ce qui est codé

Paquet `com.citycore.jobs` : `JobsManager` (embauche, production, lieu de travail) et
`JobsConfig` (section `emplois`) ; comportement des employés dans `InhabitantManager`.

- **Embauche** (menu commun à tous les joueurs) : chez un artisan installé, gestion (clic sur son
  portrait) → **Actions** → **Employés**. Liste des habitants civils : violet = ses employés
  (Maj + clic droit : renvoyer), vert = sans emploi (clic : embaucher), ambre = employé ailleurs
  (clic : il change d'employeur), gris = plus de place. Un habitant formé à la garde quitte son
  emploi ; gardes et recrues ne travaillent pas.
- **Annexe de travail** : Actions → « Créer son annexe de travail » → nom dans le chat, puis la
  zone au bâton (comme un bâtiment), puis le **point de travail** (les employés se répartissent
  autour ; `/city build skip` = le centre). Nouveau type de bâtiment « Annexe », rattaché à
  l'artisan, une par artisan ; aussi par `/city build new annexe <artisan>`. Elle n'apparaît pas
  dans le choix général des types. **Exigences** : 1 lumière (ni toit ni porte : un champ, un
  chantier conviennent) et les deux premiers « bonus du métier » de son atelier (Hélène :
  cultures et ruche ; Bastien : four à charbon de bois et meule…), les suivants comptant comme
  bonus ; une section `batiments.annexes.<artisan>` (format des ateliers) les remplace. **Niveaux**
  1 à 5 comme les autres bâtiments (variété, décor, bonus) ; écran « Son annexe » (Actions → clic)
  : exigences, bonus, points, et le cycle de travail de chaque niveau.
- **Journée d'un employé** : le jour, il se rend à sa place dans l'annexe (téléporté s'il est
  bloqué 25 s), outil du métier en main (houe, hache, pioche, canne à pêche…), et travaille :
  gestes et éclats de la matière du métier, **sans son**. Au-dessus de sa tête : « ⚒ Hélène — au
  travail », barre et temps avant le prochain gain, puis « ✔ +11 objets à l'entrepôt » ; « ⏸ » et
  la raison s'il ne peut pas travailler (artisan sans atelier, annexe incomplète). La nuit, il
  rentre dormir. Sans annexe, il vit comme un habitant ordinaire.
- **Production** : à chaque cycle, chaque employé ajoute à l'entrepôt de l'artisan **8 exemplaires
  de chaque objet que l'artisan rachète** — « généré » (il ne coupe ni ne mine rien). Les joueurs
  se servent gratuitement dans l'entrepôt (objets marqués non revendables) : la ville récolte
  pour eux (relevé le 2026-10-20 : 1 exemplaire par cycle était trop peu).

### Équilibrage (section `emplois`)

| Garde-fou | Réglage | Valeur |
|---|---|---|
| Employés par artisan, selon son rang | `employes-max` | 1, 2, 2, 3, 3 (rangs 1 à 5) |
| Durée d'un cycle, selon le niveau de l'annexe | `cycle-minutes` | 4, 3,5, 3, 2,5, 2 min |
| Travail | — | de jour seulement (≈ 10 min sur 20), joueur connecté, artisan installé, annexe conforme |
| Exemplaires par objet courant et par cycle | `par-cycle` | 8 (≈ une pile par heure et par employé, annexe niveau 1) |
| Objets de valeur | `valeur-pleine` | au-delà de 0,5 coin l'unité, chance = 0,5 / valeur (une pomme 1 coin : 1 cycle sur 2) |
| Trophées | `valeur-max` | jamais produits au-delà de 20 coins l'unité (têtes, étoile du Nether, cœur de la mer…) |
| Plafond de l'entrepôt | `plafond` | plus de production au-delà de 50 % de la capacité : les ventes des joueurs restent au plein prix jusqu'à 40 % |
| Vitesse globale | `vitesse` | 1 |
| Rien d'autre | — | ni pièces, ni expérience d'artisan, ni progression de quête directe |

Ordres de grandeur (d'après config.yml) : un employé produit 1 à 15 objets par cycle selon
l'artisan, pour 0,5 à 7 coins de valeur ; soit, par heure réelle, 2 à 35 coins de valeur
d'entrepôt au niveau 1, et au plus ≈ 210 avec 3 employés et une annexe de niveau 5 (Indiana :
tessons, disques). Côté **nourriture** : un employé d'Hélène fournit ≈ 8 rations par cycle, un de
Rosalie ≈ 12, de quoi nourrir respectivement ≈ 3 et ≈ 5 habitants par aube au niveau 1 ; avec 3
employés chacun et des annexes de niveau 5, ≈ 50 habitants. Les quêtes de la cité qui comptent un
stock (le grenier d'Hélène à 384 blés…) restent l'affaire des joueurs : +1 blé par cycle et par
employé ne les remplit qu'en dizaines d'heures.

**Consommation abstraite** : elle ne fait plus descendre un stock sous
`npc-economy.consumption-floor-percent` (25 % de la capacité) — sinon elle dévorait la
production des employés (≈ 60 blés par heure chez Hélène contre ≈ 5 produits par un employé). Elle
garde son rôle : vider un entrepôt saturé par les ventes.

**Commandes** : `/city emplois` (tous) : chaque artisan, ses employés, son annexe, son cycle et sa
production moyenne ; op : `produire` (fin immédiate des cycles en cours), `recharger`.
`/city habitants` indique l'employeur de chaque habitant.

**Base** : colonnes `employer` et `work_left` (cycle en cours) de la table `inhabitants`.
config.yml version 20 : section `emplois`, `batiments.annexe` et `batiments.annexes`,
`npc-economy.consumption-floor-percent` ajoutés d'eux-mêmes.

### À tester en jeu

1. Hélène installée → gestion → Actions : cases « Employés » et « Créer son annexe de travail »
   (avec ses exigences) ; Roland : en plus « La garde ».
2. Créer l'annexe : nom, deux coins au bâton, point de travail ; écran « Son annexe » : cultures
   et ruche exigées, lumière ; niveau et cycle de chaque niveau.
3. Employés : embaucher un habitant (annonce), le rang limite le nombre ; un habitant employé
   ailleurs passe ambre ; Maj + clic droit le renvoie.
4. De jour : l'employé marche jusqu'à l'annexe, houe en main, gestes et éclats de blé, minuteur
   au-dessus de la tête ; à la fin du cycle « ✔ +N objets à l'entrepôt » et l'entrepôt d'Hélène
   augmente (écran Entrepôt). La nuit : il rentre dormir, le minuteur disparaît.
5. Annexe rendue incomplète (ruche retirée) : « ⏸ annexe incomplète », plus de production.
6. `/city emplois produire` puis `/city emplois` : production immédiate et bilan.
7. Entrepôt à 50 % de sa capacité : « entrepôt plein pour l'instant », plus de production de cet
   objet ; Gandalf, Geralt : jamais d'étoile du Nether ni de têtes.
8. Repas de l'aube (`/city habitants aube`) nourris par la production d'Hélène et de Rosalie.

### Correctifs (annexe, satisfaction, point PNJ, point de travail, ville toujours active)

- **Supprimer l'annexe** : écran « Son annexe » (Actions → l'annexe) → case « 🗑 Supprimer
  l'annexe », **Maj + clic droit** pour confirmer. La zone redevient libre ; les employés gardent
  leur emploi et attendent une nouvelle annexe. (La liste des bâtiments d'Alderic, mode
  suppression, le permet aussi.)
- **Satisfaction sans départs** : plus aucun habitant ne quitte la ville, même mécontent (au
  début, une bonne satisfaction est impossible, et le but n'est pas la gestion). Elle devient un
  bonus : au-dessus de 80 %, arrivées 1,7 fois plus rapides et impôt × 1,5 ; de 50 à 79 %,
  normal ; en dessous, arrivées 1,5 fois plus lentes et impôt × 0,75 — **jamais d'arrêt des
  arrivées** (avant : plus personne ne venait sous 30 %, et plus d'impôt). Réglage `depart-sous`
  retiré ; écran « Population » : la case « Départs » devient « Employés » (habitants qui
  travaillent). config.yml version 21 : nouvelles valeurs mises d'elles-mêmes si les anciennes
  n'avaient pas été modifiées.
- **Orientation du point PNJ** : elle était arrondie au point cardinal le plus proche, et
  appliquée par une téléportation qui ne tourne pas la tête d'un PNJ de type joueur ; après une
  conversation, l'artisan restait tourné vers le joueur. Désormais : la direction **exacte** du
  regard au moment du clic, tête et corps tournés, et l'artisan la reprend à son poste dès
  qu'aucun joueur n'est à moins de 5 blocs. (Les points déjà enregistrés gardent l'angle du clic
  d'origine : il suffit de les revoir si besoin.)
- **Point de travail** (annexe) : l'outil de la dernière étape devient « 📍 Point de travail »
  (« les employés se placent autour, tournés vers lui » : établi, enclume, champ…), avec ses
  propres messages.
- **Employés autour du point** : ils se répartissent sur un **cercle d'environ 2 blocs** autour
  du point de travail, à égale distance les uns des autres (2 : face à face ; 3 : en triangle),
  jamais sur le point ni l'un sur l'autre (place libre la plus proche de leur angle, dans
  l'annexe), et ils **regardent le point** en travaillant.
- **La ville vit où que soient les joueurs** (`city.CityChunkLoader`) : tant qu'au moins un
  joueur est connecté, chaque chunk revendiqué reste chargé et actif (ticket de chargement du
  plugin), chargé en arrière-plan sans à-coup, revu toutes les 30 s (nouvelles parcelles) et à
  chaque connexion ; tout est rendu quand le dernier joueur se déconnecte (et à l'arrêt du
  serveur). Artisans, habitants, gardes, employés, minuteurs et sièges continuent donc même si
  tout le monde est loin. Restent soumis à la présence d'un joueur, par les règles du jeu : la
  pousse des cultures et l'apparition naturelle des monstres.

À tester : supprimer l'annexe (Maj + clic droit) ; une aube avec une satisfaction basse :
personne ne part, impôt × 0,75, arrivées plus lentes mais présentes ; créer un atelier en
regardant en diagonale au clic du point : l'artisan regarde dans cette direction, s'en détourne
pour parler, puis s'y remet ; créer une annexe : outil « Point de travail », 2 ou 3 employés en
cercle autour, tournés vers le point ; partir à 300 blocs de la ville : les employés continuent de
produire, les artisans rejoignent leur poste (`/city emplois` ; retour en ville).

## Hors phase — Animations Epic Fight des PNJ

Avec Epic Fight sur le serveur, les PNJ du plugin (artisans, habitants, gardes, armées ennemies)
glissaient dans la pose d'attente d'Epic Fight, sans animation de marche. Cause : Citizens fait
des PNJ à skin des entités « joueur », et Epic Fight anime un joueur à partir des touches de son
clavier (envoyées par son jeu), qu'un PNJ n'a pas. Les monstres, eux, sont animés d'après leur
déplacement réel. Choix retenu : donner aux PNJ les vraies animations d'Epic Fight, comme les
monstres (plutôt que de les rendre avec le modèle de Minecraft).

### Ce qui est codé

- **Mod serveur NeoForge « CityCore — Compagnon »**, module Epic Fight
  (`companions/citycore-companion`, mod `citycore_companion`, qui porte aussi l'affichage de la
  ville sur JourneyMap : voir `docs/INTEGRATION_MODS.md`) ; rien à installer chez les joueurs :
  - à chaque tick, le déplacement réel de chaque PNJ est traduit en « touches » (avancer, reculer,
    pas de côté, par rapport à son regard) et envoyé aux joueurs qui le voient, seulement quand il
    change (pas d'à-coup : arrêt confirmé après 4 ticks immobiles) → marche, marche arrière,
    arrêt ; au-delà de 0,24 bloc/tick (≈ 5 blocs/s), le PNJ passe « en course » (animation de
    course ; sa faim n'en souffre pas) ;
  - un joueur qui voit apparaître un PNJ reçoit aussitôt le mode combat d'Epic Fight et l'état
    actuel (marche ou arrêt) ;
  - **gestes** : coups des gardes et des soldats ennemis → enchaînement de l'arme tenue, celui
    qu'Epic Fight donne à l'arme (armes de mods comprises ; à défaut : épée, hache, lance, outil,
    poings), repris au premier coup après 2 s de pause ; tirs des archers et arbalétriers →
    animation de tir de l'arme. **Affichage seulement** : les dégâts restent ceux du plugin.
    Entraînement chez Roland : coup ou tir selon le rôle.
  - Les gestes de travail des employés (bras) sont déjà animés par Epic Fight (mouvement de
    creuser).
- **Plugin** (`inhabitant.NpcGestures`) : à chaque coup ou tir, en plus du mouvement de bras de
  Minecraft, une étiquette d'entité (`citycore.geste.attaque` / `citycore.geste.tir`) signale le
  geste au mod, qui la retire au tick suivant. Sans le mod, l'étiquette reste sans effet. Le
  plugin ne dépend d'aucun mod.
- Réglages du mod : section `epicfight` de `config/citycore_companion.toml` du serveur (vitesses
  de marche et de course, gestes, mode combat, journal détaillé pour les tests).
- Le mod utilise des classes internes d'Epic Fight (pas d'API officielle) : calé sur Epic Fight
  21.17.3.1. Une incompatibilité est écrite une fois dans le journal, sans gêner le serveur.

### Installation

1. Construire le mod : dans `companions/citycore-companion`, `./gradlew build` →
   `build/libs/citycore-companion-1.0.0.jar` (compilé avec le jar d'Epic Fight du serveur).
2. Retirer l'ancien `citycore-epicfight-*.jar` du dossier `mods` du serveur, y copier le nouveau
   jar à côté d'Epic Fight ; redémarrer. Journal attendu : « Epic Fight présent : les PNJ de
   CityCore seront animés ».

### À tester en jeu

- Un artisan ou un habitant qui se promène : animation de marche d'Epic Fight, arrêt net quand il
  s'arrête, plus de glissade.
- Un garde qui patrouille, puis `/city siege start` : les gardes rejoignent le front (en courant si
  leur vitesse dépasse `epicfight.deplacements.course`), frappent avec les enchaînements de l'épée, les archers jouent l'animation de tir ;
  même chose pour les soldats ennemis (siège « bannière »).
- Un garde qui recule en combat : marche arrière.
- Entraînement chez Roland (`/city garde former`) : coups ou tirs animés.
- Se déconnecter et revenir près des PNJ : ils sont animés tout de suite.
- Si besoin : `journal = true` dans la section `epicfight` de `config/citycore_companion.toml`
  (PNJ trouvés, marche/arrêt, gestes), et ajuster `epicfight.deplacements.course` si les PNJ
  courent trop ou pas assez.
- Points incertains (non vérifiables hors jeu) : façon dont Youer présente les PNJ Citizens au
  mod ; Epic Fight qui renverrait lui-même un autre mode pour les PNJ ; rendu exact des
  animations de tir sans visée préalable.

## Phase 7 — Fêtes

- **Fête des moissons** : grande livraison commune chez Hélène, prix de rachat majorés.
- **Foire des métiers** : objets rares réapprovisionnés, −20 % en boutique.
- **Tournoi de Roland** : tir à l'arc sur cibles, classement.
- **Concours du bâtisseur** : chaque joueur présente un bâtiment, les autres votent.
- **Nuit du Souvenir** : siège spécial pour l'anniversaire de la Nuit Noire.
- Satisfaction des habitants en hausse pendant une fête.

À tester : `/city event start <fête>`, début, fin et retour des prix normaux.

---

## Choix par défaut (à confirmer)

- Hors siège, les habitants peuvent aussi être tués par les monstres de la nuit (réglable).
- Former un soldat ne coûte que du temps et un habitant (coût en option, 0 par défaut).
- Le niveau d'un atelier n'agit que sur la capacité de son entrepôt.
