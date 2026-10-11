# Plan « Valmont vivante » — bâtiments, habitants, sièges et garde

Plan de développement par phases, chacune testable en jeu avant de passer à la suivante.
Périmètre retenu : vérification des constructions, habitations et habitants (impôt, besoins),
sièges par vagues, fêtes, soldats de la garde. Pas de monuments ni d'autre gestion.

## Avancement

| Phase | Contenu | Dépend de | Taille | État |
|---|---|---|---|---|
| 0 | Prototype technique sur Youer | — | petite | **codée, à tester en jeu** |
| 1 | Types de bâtiments | — | petite | **codée, à tester en jeu** |
| 2 | Inspection des constructions | 1 | moyenne | à faire |
| 3 | Habitations et habitants (+ impôt simple) | 0, 2 | grosse | à faire |
| 4 | Protection renforcée, calendrier d'événements, sièges | 3 | moyenne à grosse | à faire |
| 5 | La garde (soldats) | 3, 4 | moyenne à grosse | à faire |
| 6 | Besoins et satisfaction | 3, 5 | moyenne | à faire |
| 7 | Fêtes | calendrier de la phase 4 | petite à moyenne | à faire |

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
| `/city proto soldat` | Soldat blessable (uniforme de `soldats.skins`, armure de fer, épée, 30 PV) qui attaque les monstres à moins de 16 blocs |
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
  autres « Divers ». Leur hauteur est estimée automatiquement au démarrage (sol autour de la zone,
  bloc le plus haut dans la zone).
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
  `thorvald`, `helene`…). Bas de la zone : le plus bas entre les coins cliqués et le sol autour ;
  haut : le bloc le plus haut de la zone (64 blocs au plus).
- **Changer le type** : clic droit sur le bâtiment dans la liste d'Alderic, ou
  `/city build type <nom> <type> [personnage]`. Un bâtiment habité par un artisan reste son atelier.
- **Hauteur** : `/city build hauteur <nom> <bas> <haut>` corrige l'estimation.
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

À tester : forge sans enclume refusée avec la liste ; installation après ajout ; niveau qui monte
en décorant et retombe en cassant l'enclume ; pas de ralentissement sur une zone 30×30×20.

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

À tester : maison à 3 lits = 3 arrivées ; lit cassé = départ ; habitant tué puis remplacé ;
impôt au lever du jour ; habitants restaurés après redémarrage.

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

## Phase 5 — La garde

- Écran « La garde » chez Roland : soldats / maximum, recrues en formation, « Former une recrue ».
- Maximum selon le rang de Roland (ex. 2, 4, 6, 9, 12).
- Formation : un habitant part à l'atelier de Roland, X minutes (comptées avec un joueur
  connecté), il en ressort armé et en uniforme (skin de `soldats.skins`).
- Un soldat garde son lit : population (habitants + soldats) ≤ lits.
- Patrouille automatique entre les bâtiments et la lisière ; attaque les monstres proches ; se
  soigne au repos. Pendant un siège, rejoint le côté attaqué.
- Mort : place libérée, il faut former une nouvelle recrue ; son lit accueillera un habitant.
- `/city garde list|skip|kill`.

À tester : maximum selon le rang ; formation avec `skip` ; intervention sur un monstre ; siège
avec et sans garde ; pas de remplacement automatique d'un soldat mort.

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
