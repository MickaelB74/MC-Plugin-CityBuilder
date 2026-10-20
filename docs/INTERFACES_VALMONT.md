# Interfaces illustrées « Valmont »

Les menus des habitants (artisans, quêtes, récits, maire) sont dessinés par un petit pack de
ressources généré par le plugin. Seuls les menus ouverts par les PNJ changent : coffres, fours,
villageois et autres menus du jeu restent ceux de Minecraft. Le pack ne modifie aucune texture
de bloc ni d'objet.

Aperçus : `docs/ui-previews/*.png` (agrandis 3 fois).

## Écrans couverts

| Habitant | Écrans |
|---|---|
| Artisan rencontré hors de la ville | Portrait sur décor de route, « Suivez-moi / Attendez ici », son histoire, étapes du recrutement |
| Artisan arrivé en ville | « Son logis » (ateliers libres ou réservés à lui ; anneau doré = réservé, rouge = incomplet ; chaque atelier montre son inspection : blocs requis ✔/✘, porte, toit, lumière, niveau et points), « Promenade / Restez ici », son histoire |
| Artisan sans logis (son atelier a été supprimé) | Même écran, marqué « SANS LOGIS » : gestion fermée et quêtes en pause (rien n'est perdu) jusqu'au choix d'un nouvel atelier |
| Nouveau bâtiment (`/city build new`) | Choix du type (habitation, atelier, mairie, divers), puis de l'artisan pour un atelier (atelier libre ou réservé ; cadenas = personnage pas encore trouvé) ; chaque type et chaque personnage affiche ses prérequis dans sa bulle ; le nom est ensuite demandé dans le chat avec un nom proposé. Sans le pack, boutons cliquables dans le chat (prérequis au survol) |
| Artisan installé | Portrait, rang (étoiles), barre d'expérience, travail, suivi, Vendre, Entrepôt, Boutique, Quêtes ; un clic sur le portrait ouvre la gestion du bâtiment |
| Gestion du bâtiment (clic sur le portrait) | Variante de l'accueil (cadre turquoise, médaillon « maison ») : Logis → « Son atelier » (exigences ✔/✘, bonus du métier, points de détail, ce qu'il faut pour le niveau suivant), Actions, Son histoire ; barre = progression de l'atelier ; clic sur le portrait = retour au commerce |
| Actions | Chez tout artisan installé : « Employés » et « Créer son annexe de travail » (ou « Son annexe » une fois créée) ; chez Roland, en plus, « La garde » |
| Employés | Liste commune des habitants civils : ses employés (anneau violet, Maj + clic droit pour renvoyer), sans emploi (vert, clic pour embaucher), employés ailleurs (ambre), plus de place (gris) ; pied de page « EMPLOYÉS 1/2 · CYCLE 4:00 » |
| Son annexe | Comme « Son atelier » : outils du métier exigés, bonus, points de détail ; la bulle d'information donne le cycle de travail de chaque niveau |
| La garde (Roland) | Liste commune à tous les joueurs : gardes (anneau violet), recrues en formation (ambre, temps restant), habitants à former (vert ; gris s'il n'y a plus de place), têtes au skin de chacun ; pied de page « GARDE x/y · FORMATION a/b » ; clic sur un habitant → Rôle ; Maj + clic droit sur un garde ou une recrue → retour à la vie civile |
| Rôle d'une recrue | Rôles de `soldats.roles` (combat, vie, durée de formation, équipement) ; cadenas sur ceux d'un rang de Roland pas encore atteint ; un clic lance la formation |
| Vendre / Entrepôt / Boutique | Grille 9 × 4 avec pagination, cadres de couleur (prix plein, prix réduit, entrepôt plein, rupture, cadenas pour les articles d'un rang supérieur), bourse du joueur |
| Quêtes d'un artisan | Cartes « principale » et « spéciale » : récompense ou temps de repos, objectifs avec progression, bouton Accepter / Valider / En cours / En repos, accès aux récits ; un clic sur le ruban « Quêtes » ouvre le journal de quêtes (suivi et affichage du suivi) |
| Récits (quêtes personnelles) | Les cinq récits, un par rang : progression et état (accompli, en cours, à accepter) ; ceux d'un rang pas encore atteint sont verrouillés (cadenas, titre illisible, rang requis) |
| Alderic (maire) | Accueil (palier, parcelles, quêtes, bâtiments, trésor, agrandir), quêtes de la cité avec récit et charte, bâtiments (liste rangée par type, clic droit pour changer le type, suppression), trésor (caisse, bourse, dépôt) ; un clic sur son portrait ouvre « Population » |
| Population (clic sur le portrait d'Alderic) | Rangée 1 : nourriture, sécurité, santé, confort (anneau vert ≥ 70 %, ambre ≥ 40 %, rouge en dessous, gris = artisan pas installé), satisfaction, arrivées, impôt de l'aube, départs ; rangée 2 : habitants, places, gardes, recrues, deuils ; rangées 3-4 : chaque habitation (niveau, habitants, confort) ; pied de page « SATISFACTION 72 % · 16 HABITANTS ». Menu classique : case « Population » (bilan dans le chat) |
| Panneau Tab de la cité | Palier, Habitants, **Satisfaction** (barre et pourcentage, phase 6), Trésor, Parcelles |
| Journal de quêtes (ruban « Quêtes » d'un habitant, ou `/city quests`), onglet « Suivi » | Habitants ayant une quête en cours (anneau vert = suivi, gris = masqué), position du panneau (Bord, Marge, Près), affichage du suivi **Visible / Accroché / Masqué** (clic gauche = suivant, clic droit = précédent), « Tous » pour tout suivre. Aperçu : `docs/ui-previews/quetes_suivies.png` |
| Journal de quêtes, onglet « Récits » (`/city quests recits`) | Tous les personnages à récits, dans l'ordre de la trame : survivants (rangées 1 et 2), légendes (rangée 3). Cadenas = pas encore à Valmont ; anneau doré = récit à accepter, ambre = récit en cours, vert = à jour, gris = en ville sans atelier, rouge = mod absent. La bulle liste ses cinq récits (✔ accompli, ● en cours avec avancement, ! à accepter, cadenas et rang requis). Un clic sur un personnage installé ouvre l'écran de ses récits (le même que chez lui, retour vers le journal). Rangée 4 : bilan (à accepter, en cours) ; rangée 5 : récits accomplis, tous personnages. Aperçu : `docs/ui-previews/journal_recits.png` |
| Où sont-ils ? (`/city locate [personnage]`, alias `/c ou`) | À gauche, les personnages par pages de 12 : découverts d’abord (rencontrés par un joueur, arrivés ou installés ; anneau vert = position connue, doré = personnage choisi), puis cadenas pour ceux que personne n’a encore rencontrés. À droite, une carte vue du ciel dessinée par le plugin (couleurs des cartes du jeu, relief ombré ; parchemin = zone non chargée) : limites de Valmont en or, habitants en rouge, personnage choisi en jaune, vous en blanc. Un clic sur un personnage centre la carte sur lui (1 case = 2 blocs) ; clic gauche / droit sur la carte = zoom avant / arrière ; bouton « carte » de l’en-tête = vue d’ensemble de la ville. Plaque du bas : nom, distance (ou monde), coordonnées X · Y · Z. Sans le pack : la liste et les coordonnées dans le chat. Aperçu : `docs/ui-previews/localiser.png` |

Toute la logique reste celle des menus classiques (prix, stocks, XP, messages) : les nouveaux
écrans appellent exactement les mêmes actions.

## Affichages hors des menus

Ces deux affichages utilisent le même panneau compact : bandeau doré titré, lignes de 8 pixels
en petites capitales, barre de progression fine sous les objectifs.

- **Liste des joueurs (touche Tab)** : par-dessus la liste de Minecraft, une plaque « MyCivilisation »
  au centre, le panneau de la **cité** à gauche (nom, palier et niveau, habitants « vivants / places », trésor,
  parcelles avec barre) et celui du **joueur** à droite (pseudo, niveau, barre d'expérience,
  bourse, métier). Les joueurs connectés restent listés au milieu, entre les deux panneaux.
  Aperçus : `docs/ui-previews/tab.png` et `docs/ui-previews/ecran_tab_1080p.png` (écran entier).
  - Les panneaux sont placés à 72 pixels d'interface de part et d'autre du centre : au-delà de
    20 joueurs connectés (liste sur plusieurs colonnes) ou avec des pseudos très longs, la liste
    peut passer sous les panneaux.
  - Suivi des quêtes **accroché** : le panneau « Quêtes » (mêmes lignes que celui de gauche,
    10 lignes au plus) s'affiche sous celui de la cité, dans la même colonne ; il apparaît et
    disparaît avec la liste. Sans le pack, ses lignes s'affichent sous la liste des joueurs.
- **Panneau « Quêtes »** (remplace le tableau de droite) : à gauche de l'écran, 84 pixels de large.
  Une ligne par habitant, puis ses objectifs avec leur barre ; « ★ Voir … ! » quand une quête est
  prête à valider ; 12 lignes au plus. Aperçu : `docs/ui-previews/hud_quetes.png`.
  - Trois positions, au choix de chaque joueur dans `/city quests` : **Bord** (contre le bord
    gauche en 1080p avec l'échelle d'interface 4, réglage par défaut), **Marge** (un peu plus au
    centre : écrans 1440p, 720p, 1366 × 768) et **Près** (juste à gauche de l'inventaire : écrans
    16:10 ou interface agrandie). Minecraft place ce panneau par rapport au centre de l'écran : le
    serveur ne connaît pas la taille de l'écran du joueur, d'où ce choix manuel.
  - Minecraft ne prévient pas le serveur quand un joueur ouvre son inventaire : le panneau est donc
    affiché en permanence (comme l'ancien tableau), et l'inventaire l'assombrit comme le reste.
  - Trois affichages du suivi, au choix de chaque joueur dans le journal de quêtes : **Visible**
    (ce panneau, réglage par défaut), **Accroché** (rien en permanence à l'écran : le suivi
    s'affiche avec la liste des joueurs, touche Tab, voir plus haut) et **Masqué**. Le serveur ne
    sait pas quand Tab est enfoncé : en mode accroché, la liste est renvoyée dès que le suivi change.
  - Le journal de quêtes s'ouvre en cliquant sur le ruban « Quêtes » de l'écran des quêtes d'un
    habitant (menu classique : carte « Journal de quêtes » à gauche), ou avec `/city quests`.
    Raccourcis : `/city quests toggle` fait défiler les trois affichages (`/city quests toggle
    accroche` en choisit un) ; `/city quests recits` ouvre directement l'onglet « Récits ».
  - Ces réglages (affichage, habitants suivis, position, ainsi que le style des menus `/city ui`)
    sont les **préférences du joueur** : rangées dans la base (`database.db`, table
    `player_prefs`) et écrites aussitôt, elles sont gardées après une déconnexion, un redémarrage
    ou un arrêt brutal. Les anciens réglages rangés dans les données du joueur sont repris à sa
    première connexion (un panneau masqué reste masqué).
    Sans le pack, le tableau de droite remplace le panneau et le journal classique est utilisé
    (têtes à cliquer, affichage du suivi en bas à gauche, livre « Récits des habitants » en bas).
  - Technique : il est dessiné dans le titre d'une barre de boss blanche dont le pack rend la barre
    invisible. Les barres de boss **blanches** d'autres plugins perdent donc leur barre (leur titre
    reste affiché) ; les barres des boss du jeu (dragon, Wither, raids) ne sont pas concernées, mais
    elles s'affichent sous la barre invisible du panneau (19 pixels plus bas qu'à l'habitude).

Hors du pack de ressources : les **parcelles de la ville sur la carte JourneyMap** (plein écran
et minicarte) — chaque parcelle teintée d'or (#F1CF5E), contour extérieur de la ville, quadrillage
fin des chunks, nom de la ville, bulle au survol avec les parcelles et les habitants. Envoyées par
le serveur (plugin + module JourneyMap du mod `citycore_companion`) dès qu'une parcelle est
revendiquée ou rendue ; réglages dans la section `carte`, état avec `/city carte` (admin).
Détails et installation : `docs/INTEGRATION_MODS.md`, « Mod compagnon ».

## Fonctionnement

- Au démarrage, le plugin construit `plugins/CityCore/ui-pack/CityCore-UI.zip` à partir des images
  embarquées, puis le propose à chaque joueur à la connexion.
- Chaque image est un caractère d'une police ; le titre du coffre dessine toute l'interface. Sous
  les boutons dessinés, des objets invisibles portent les bulles d'information et les clics.
- Les portraits sont redessinés pixel par pixel à partir du vrai skin du personnage (lu une fois
  sur le serveur de textures de Mojang, puis mis en cache dans `ui-pack/skins/`). Sans skin connu,
  une silhouette encapuchonnée le remplace : renseignez `npc-skins.<clé>` dans config.yml.
- Un joueur qui refuse le pack garde les menus classiques. `/city ui classique` et
  `/city ui valmont` permettent de changer de style à tout moment.

## Hébergement du pack (config.yml, section `ui-pack`)

| Clé | Rôle |
|---|---|
| `enabled` | Active les menus illustrés |
| `self-host` / `port` | Le plugin sert lui-même le pack (port 8164 par défaut) |
| `public-address` | Adresse que les joueurs utilisent pour joindre le serveur. Vide = adresse locale détectée (même PC ou réseau local) |
| `url` | Adresse externe où vous avez déposé `CityCore-UI.zip` (prioritaire). Le fichier doit être identique à celui généré |
| `required` | `true` = pack obligatoire (un refus déconnecte) |

Pour des joueurs venant d'Internet : ouvrez le port choisi sur la box ou chez l'hébergeur, et
renseignez l'IP publique ou le nom de domaine dans `public-address`. L'adresse utilisée est
affichée dans la console au démarrage.

## Modifier les images

Les images sont produites par `tools/ui-art/ValmontArt.java` (dessin pixel par pixel, aucune
ressource Mojang copiée) :

```
java tools/ui-art/ValmontArt.java .
```

Vous pouvez aussi remplacer directement un PNG de `src/main/resources/pack/assets/citycore/textures/ui/`
par une version faite à la main, **en gardant son nom et ses dimensions**, puis recompiler.
Aucune image ne doit dépasser 256 × 256 pixels : au-delà, Minecraft affiche un rectangle vide à la
place (le plugin refuse alors de construire le pack et garde les menus classiques).

## Limites connues

- Techniquement, l'écran reste une fenêtre d'inventaire : l'inventaire du joueur est visible en
  bas (il est habillé aux couleurs de Valmont).
- Au survol, Minecraft 1.21.1 éclaire la case visée d'un carré blanc ; un pack ne peut pas le
  retirer.
- Les menus sans lien avec un habitant (`/city build`) restent classiques.
