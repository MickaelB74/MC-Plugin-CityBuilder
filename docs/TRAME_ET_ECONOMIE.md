# CityCore — Trame, personnages et économie

Document de conception de la progression de la ville (version « équilibrage de zéro »).
Les valeurs chiffrées vivent dans `src/main/resources/config.yml` ; la trame et les quêtes
dans `CityTier.java`, les personnages dans `CityNPC.java` et `PersonalQuestCatalog.java`.

---

## 1. L'histoire : la renaissance de Valmont

Il y a vingt hivers, **Valmont**, capitale des Marches, a été réduite en cendres en une nuit :
les mages de la Tour avaient ouvert une « Porte » vers le Néant (le portail de l'End) et un
dragon noir en est sorti. C'est la **Nuit Noire**.

Les survivants — les artisans qui faisaient la richesse de la ville — se sont dispersés dans
les terres sauvages. **Alderic**, dernier intendant de Valmont, a sauvé la Charte de la ville.
Avec les joueurs, il fait **renaître Valmont** : `/city create` fonde la ville sous ce nom,
sans rien demander.

Le fil rouge suit la progression naturelle de Minecraft, en trois actes et un épilogue :

- **Acte I — survivre et s'installer** (chapitres I à III) : bois, pain, pierre et rivière ;
- **Acte II — les métiers, les routes et la garde** (IV à VI) : la mine et la taverne, la forge
  et la teinture, les caravanes et la milice ;
- **Acte III — le savoir, la révélation, la Porte** (VII à IX) : Isabeau déchiffre les registres
  (la Nuit Noire est venue d'une Porte ouverte avec des yeux de feu et de néant), le feu du
  Nether, puis l'assaut de la Porte : **l'Ender Dragon est l'apogée** (chapitre IX) ;
- **Épilogue — la nouvelle Valmont** (X) : la capitale, ses hauts faits et ses dernières légendes.

Chaque survivant porte une blessure de la Nuit Noire, que sa place dans la ville répare :
Brennan a sculpté l'arche qui s'est effondrée (« cette fois, les murs tiendront ») ; Thorvald a
forgé les portes qui ont tenu une heure ; Agathe avait douze ans quand le grand étendard a brûlé ;
Roland a perdu toute sa compagnie ; Isabeau cherche depuis vingt ans la cause du drame, et sa
découverte ouvre l'acte III. Les *Chroniques de Valmont* (livres vendus par Isabeau) racontent
cette histoire en jeu.

---

## 2. Les 10 chapitres de la ville

Pour passer au palier suivant : toutes les quêtes **non bonus** terminées, puis payer la
**charte** depuis la caisse de la ville. Récompense : des emplacements de chunks offerts
(ils ne renchérissent pas les agrandissements).

| Niv. | Palier | Chapitre | Recrues (★ = bonus) | Quêtes principales | Charte | Chunks offerts |
|---|---|---|---|---|---|---|
| 1 | Campement | I — Les cendres et la charte | Bastien | 7 chunks, 2 bâtiments, livrer 192 rondins | 150 | +6 |
| 2 | Hameau | II — Du pain pour l'hiver | Hélène, ★ Robin des Bois | 16 chunks, 4 bâtiments, réserve de 384 blé chez Hélène | 400 | +6 |
| 3 | Village | III — Les pierres et la rivière | Brennan, Martin, ★ Jack Sparrow (mod Tax' Ocean Villager) | 24 chunks, 6 bâtiments, livrer 320 pierres | 800 | +8 |
| 4 | Bourg | IV — La Fosse et le Chaudron | Godefroy, Rosalie | 32 chunks, 8 bâtiments, réserve de 256 charbon, Brennan Apprenti, 50 monstres | 1 500 | +8 |
| 5 | Ville | V — Le marteau et le métier | Thorvald, Agathe, ★ Indiana Jones | 42 chunks, 10 bâtiments, 640 briques de pierre (remparts), 192 lingots de fer chez Thorvald, Godefroy Apprenti | 2 500 | +10 |
| 6 | Ville franche | VI — Les routes et la garde | Edmond, Roland | 52 chunks, 12 bâtiments, 15 pillards, 150 monstres, Thorvald Compagnon, Agathe Apprentie | 4 000 | +12 |
| 7 | Cité | VII — La foi et le savoir | Frère Anselme, Isabeau, ★ Doc Brown | 62 chunks, 14 bâtiments, 24 bibliothèques | 6 000 | +14 |
| 8 | Grande cité | VIII — L'or, le verre et le feu | Léon, Béatrice, ★ Gandalf | 74 chunks, 16 bâtiments, 256 vitraux, 1 cloche, 128 lingots d'or chez Béatrice, 20 blazes | 8 500 | +16 |
| 9 | Citadelle | IX — La Porte du Néant | — | 12 yeux de l'Ender, Thorvald Expert, Anselme Compagnon, Roland Compagnon, **l'Ender Dragon** (apogée) | 12 000 | +20 |
| 10 | Capitale | X — La nouvelle Valmont | ★ Geralt, ★ Harold (mod Dragon Mounts) | Hauts faits (bonus) : Wither, Warden, 3 gardiens des abysses, 120 chunks, Thorvald et Béatrice Maîtres | — | — |

**Passage depuis l'ancienne trame en 7 chapitres** : au premier démarrage, le niveau enregistré
est converti vers le chapitre équivalent (1→1, 2→2, 3→3, 4→5, 5→6, 6→8, 7→10). Les quêtes ont
gardé leurs identifiants : ce qui était accompli le reste.

Types de quêtes de ville :

- **Recruter** un personnage : le trouver (il apparaît hors de la ville, de préférence dans
  son biome : le pêcheur sur une plage, le forgeron dans la neige…), le ramener, l'installer
  dans un bâtiment. La quête se termine à l'installation (les 3 phases du PNJ).
- **Livrer** des matériaux au chantier (clic gauche), ou les **réquisitionner** dans les stocks
  des artisans (Maj + clic droit) : la caisse paie le prix de revente, les items sont
  consommés par le chantier.
- **Réserve** : le stock d'un artisan doit atteindre un seuil (le grenier, l'arsenal, le trésor).
- **Rang** : un artisan doit atteindre un niveau (on commerce avec lui).
- **Chasse** : monstres tués par tous les joueurs (les boss comptent même sans tueur identifié).
- **Bonus (★)** : recrues secondaires et hauts faits. Elles ne bloquent jamais l'amélioration
  et restent disponibles après le passage de palier.

Le récit d'Alderic s'affiche à chaque passage de palier et se réécoute depuis le livre du
menu des quêtes. Objectifs, chartes et chunks offerts sont surchargeables dans `config.yml`
(`city-quests.targets`, `city-tiers`).

### Suivre la progression

La vue d'ensemble est **uniquement l'écran des progrès de Minecraft** (touche L), onglet
**« La Renaissance de Valmont »** : il n'y a plus de menu de progression fait maison.

- **Quatre lignes parallèles**, chacune dans l'ordre de la trame : les **chapitres** I à X
  (objectifs listés au survol), les **survivants de Valmont**, les **légendes de passage** (★) et
  les **hauts faits**. Chaque personnage et chaque haut fait se place sous la colonne de son
  chapitre (deux personnages du même chapitre l'un sous l'autre) : le dragon est sous le
  chapitre IX, les fléaux du royaume sous le chapitre X. Minecraft range les enfants d'un
  progrès dans un ordre aléatoire : une ligne (chaque étape n'a qu'une suite) reste droite et
  ordonnée, et les positions sont fixées par le plugin. Elles sont réappliquées à chaque
  connexion et après chaque rechargement des données, car un autre composant du serveur peut
  recalculer la disposition de Minecraft (chargements de progrès observés à chaque connexion
  sur Youer). Si un serveur refuse ce placement, une ligne « Disposition fixe … indisponible »
  apparaît dans la console : seul l'ordre vertical des lignes est alors laissé à Minecraft.
  Aperçu : `docs/ui-previews/arbre_progres.png` (43 étapes visibles, 17 colonnes sur 9 lignes).
- **Tout est visible dès le départ** : Minecraft cache normalement les étapes éloignées ; le
  plugin ajoute au bout de chaque ligne un nœud invisible accordé à tous, ce qui rend toute la
  ligne visible.
- **Statut de chaque étape** : cadre gris = à faire, cadre coloré = accompli. Au survol, la
  barre de titre se remplit et affiche le compte des critères :
  - chapitre : « ouvert » + un critère par quête obligatoire (objectifs, recrutements, dragon)
    + « charte signée » ;
  - personnage : rencontré → arrivé en ville → installé (les 3 phases du recrutement) ;
  - haut fait : créatures abattues (ex. 1/3 gardiens des abysses) ou rang atteint (ex. 3/5).
- Notification « Progrès réalisé » quand une étape s'accomplit. Progression collective : tous
  les joueurs reçoivent l'état de la ville (y compris en retour arrière si la ville est
  supprimée). L'onglet de la cité est présélectionné à la connexion : la touche L l'ouvre
  directement (sauf sur Youer, qui refuse cette présélection : l'onglet se choisit à la main).
- **`/city progression`** (alias `progres`, `avancement`, `chroniques`) présélectionne l'onglet
  et rappelle la touche du joueur. Désactivable avec `progression.advancements: false`.

---

## 3. Personnages principaux (les survivants)

Chaque artisan correspond à un pan de Minecraft et à un vrai métier médiéval. Règle de
marché : **un matériau n'est racheté que par un seul PNJ** (pas d'arbitrage entre PNJ).

| Personnage | Métier · axe Minecraft | Chap. | Histoire | Rachète | Vend (exemples) |
|---|---|---|---|---|---|
| **Bastien** | Bûcheron & charbonnier · bois | I | Charbonnier qui alimentait les forges ; ermite depuis la Nuit Noire | Toutes les bûches, tiges du Nether | Charbon de bois, bois écorcé, menuiserie, pousses exotiques, hache enchantée |
| **Hélène** | Fermière · cultures | II | Veuve qui a caché des semences dans son tablier pendant les famines | Céréales, légumes, fruits, os, cacao | Graines, poudre d'os, bottes de foin, graines anciennes du renifleur |
| **Brennan** | Tailleur de pierre · pierre | III | Maître maçon qui a sculpté l'arche effondrée ; rebâtit des remparts « qui tiendront » (V) | Roches, deepslate, tuf, obsidienne, pierre de l'End | Briques, pierres polies, tuiles, dallages |
| **Martin** | Pêcheur · pêche | III | Passeur qui a sauvé des enfants sur sa barque | Poissons, encre, algues | Cannes (enchantées), poisson cuit, seaux de poisson |
| **Godefroy** | Mineur · minerais | IV | Maître de la Fosse aux Corbeaux, sous terre pendant la catastrophe | Charbon, minerais bruts, redstone, lapis, diamants | Torches, lanternes, rails, wagonnets, pioches enchantées |
| **Rosalie** | Tavernière · cuisine | IV | Tenait le Chaudron Doré, la taverne des guildes ; ses chansons porteront la légende (X) | Viandes, œufs, champignons | Plats cuisinés (avec les stocks d'Hélène), gâteaux, cervoise, hydromel |
| **Thorvald** | Forgeron · forge | V | A forgé les portes qui ont tenu une heure face au dragon ; forge les lames de l'assaut (IX) | Lingots de fer et de cuivre, débris de netherite | Outils, armures, cotte de mailles, enclumes, cloches, netherite |
| **Agathe** | Tisserande · textile & teinture | V | Avait douze ans quand l'étendard a brûlé ; le refait flotter (VI) | Laines, ficelle, fleurs, cactus | Teintures, laines colorées, lits, tapis, bannières |
| **Edmond** | Éleveur & palefrenier · animaux | VI | Maître des écuries royales, il a libéré les chevaux | Cuir, peaux, écailles de tatou, foin | Selles, bardes, armures de cuir et de loup, étiquettes |
| **Roland** | Capitaine de la garde · combat | VI | Sergent qui a perdu sa compagnie ; « je ne laisserai pas Valmont brûler une seconde fois » (garde de l'assaut, IX) | Chair putréfiée (primes), flèches, silex, plumes | Arcs, arbalètes, flèches à effets ; contrats de chasse |
| **Frère Anselme** | Moine apothicaire · alchimie & abeilles | VII | Dernier moine de Saint-Clair, il a soigné les survivants ; potions de l'assaut (IX) | Miel, cire, verrues du Nether, ingrédients d'alchimie | Potions, alambics, bougies, ruches |
| **Isabeau** | Scribe & cartographe · savoir | VII | Archiviste qui a sauvé un coffre de registres et découvre la vérité sur la Porte | Papier, livres, cartes | Bibliothèques, pupitres, boussoles, cartes, *Chroniques de Valmont* |
| **Léon** | Verrier & tuilier · verre & terre cuite | VIII | Fils du verrier de la rosace ; porte le dernier éclat de la « Rose de Valmont » | Sable, sable rouge, argile, gravier | Verre, vitraux, briques, terres cuites émaillées |
| **Béatrice** | Orfèvre · or & gemmes | VIII | Fille du maître de la Monnaie, cynique en apparence | Or, émeraudes, améthystes | Pommes et carottes dorées, horloges, couronne, gabarits d'ornement |

Le maire **Alderic** reste le point d'entrée (quêtes de ville, caisse, agrandissement).

## 4. Personnages secondaires (légendes de passage)

Recrutement **bonus**. Chacun ouvre un marché de niche que les principaux ne couvrent pas.
Aucun PNJ principal ne dépend de leur stock (la progression ne les exige jamais).

| Personnage | Chap. | Niche | Particularité |
|---|---|---|---|
| **Robin des Bois** | II | Butin des pillards | Marchandises « de seconde main » moins chères mais en quantité limitée ; rachète les fioles inquiétantes |
| **Jack Sparrow** | III | Trésors de la mer et navires (mod Tax' Ocean Villager) | Nautiles, cœurs de la mer, prismarine ; tridents, « rhum de Tortuga », pavillon noir, disque et tricorne pirates du mod, et au rang 5 un **navire pirate** (objet du mod). Récits : aborder un navire pirate, contrebande avec un équipage, réquisitionner un vaisseau de la Marine, navire maudit au clair de lune, roi pirate illageois. Sans le mod : recrutable, installable, sans menu |
| **Indiana Jones** | V | Archéologie et curiosités | Rachète tessons, disques, cornes, clés des épreuves, froglights ; vend plantes de ses expéditions, œuf de renifleur et « le carnet du Graal » |
| **Doc Brown** | VII | Mécanismes (redstone) | Quartz, slime, poudre ; pistons, observateurs, TNT, fusées et les « ailes du Doc » (élytres, très rare) |
| **Gandalf** | VIII | Magie & Néant | Perles de l'Ender, carapaces de shulker, échos ; livres enchantés, balises, totems |
| **Geralt de Riv** | X | Chasse aux monstres | Rachète les têtes de monstres ; potions de sorceleur ; contrats de boss |
| **Harold** | X | Dragons (mod Dragon Mounts Remastered) | Rachète poisson, cuir, membranes ; vend selles, sifflets du mod, « le manuel du dragonnier », œuf de dragon vierge. Sans le mod : recrutable, installable, mais sans menu (voir `docs/INTEGRATION_MODS.md`) |

Chaque personnage (principal ou secondaire) a :

- une biographie affichée dans ses menus et des dialogues de rencontre, d'arrivée,
  d'installation et de montée de rang (`{ville}` et `{joueur}` sont remplacés) ;
- 5 récits (quêtes personnelles), un par rang, liés à son métier : biomes à explorer,
  succès Minecraft (validés aussi s'ils sont déjà obtenus), pêche, élevage, artisanat, chasse.
  Les 5 sont toujours affichés : ceux d'un rang pas encore atteint apparaissent verrouillés
  (cadenas, titre illisible, rang requis), au lieu d'emplacements vides.

---

## 5. Économie

### Principes

1. **Le stock est réel.** Ce qu'un joueur vend entre dans le stock du PNJ, limité par une
   capacité (« entrepôt »).
2. **Offre et demande.** Prix plein tant que le stock est sous 40 % de la capacité, puis
   baisse linéaire jusqu'à 35 % du prix ; entrepôt plein = le PNJ n'achète plus. Une vente
   est calculée lot par lot (vendre beaucoup d'un coup fait baisser le prix au fil de la vente).
3. **Revente du stock** (menu *Entrepôt*) au prix de rachat × 2.
4. **Production depuis les stocks.** Les articles fabriqués consomment leurs ingrédients
   dans le stock du PNJ ou d'un autre artisan : sans fer chez Thorvald, pas d'armure ; sans blé
   chez Hélène, pas de pain chez Rosalie. Les raretés sans ingrédient ont un approvisionnement
   limité qui se reconstitue avec le temps.
5. **Consommation.** Chaque PNJ consomme 8 % de sa capacité par heure (quand au moins un joueur
   est connecté) : la demande revient, une ferme automatique ne peut pas saturer un marché
   à l'infini.
6. **Marquage.** Tout item acheté à un PNJ est marqué : ni revendable, ni valable pour les
   quêtes des PNJ (mais utilisable pour construire, crafter et livrer les chantiers).
7. **Quêtes des PNJ (principale et spéciale).** Récompense = valeur de marché des items demandés × 1,25
   (principale) ou × 1,5 (spéciale, avec primes de chasse). Délai de 20 / 60 min par joueur et
   par PNJ. Les items livrés rejoignent le stock du PNJ.
8. **Employé d'un PNJ** : +10 % sur ses ventes à ce PNJ, −15 % sur ses achats chez lui.

### Ordres de grandeur

| Repère | Valeur |
|---|---|
| 1 stack de bûches communes | 20 coins |
| 1 stack de blé | 20 coins |
| 1 stack de cobblestone | 3 coins |
| 16 minerais de fer bruts | 20 coins |
| 1 diamant | 12 coins |
| Pioche en fer / en diamant (boutique) | 11 / 83 coins |
| Enclume | 107 coins |
| 1er emplacement de chunk acheté | 150 coins (+8 % par emplacement, plafond 2 500) |
| Total des chartes jusqu'à la Capitale | 24 650 coins |

### Exploits corrigés

| Faille de la version de test | Correction |
|---|---|
| Récupération **gratuite** du stock + recraft (lingots → bloc → lingots) qui efface le marquage : argent infini | Le stock se rachète au prix de revente ; le contrôle d'arbitrage couvre les recettes |
| Multiplicateur de stock de l'employé (vendre 64, stocker 96) : duplication d'items | Remplacé par +10 % / −15 % |
| Cadeau du maire (+8 chunks, +100 coins) versé à **chaque** nouveau joueur | Une seule fois pour la ville |
| Items achetés ou récupérés comptés dans les quêtes de collecte | Items marqués exclus |
| Récompense fixe des quêtes, quelle que soit la valeur demandée | Récompense = valeur des items × prime |
| Quêtes enchaînées sans limite | Délai entre deux quêtes |
| Quêtes de chasse impossibles à valider (jamais marquées « prêtes ») | Corrigé |
| Clic dans son propre inventaire déclenchant les boutons des menus | Seul le menu du haut réagit |
| `/city deposit` ignoré par les quêtes de dépôt | Corrigé |
| Menu de quêtes du maire resté ouvert pendant un passage de palier : icônes récupérables | Détection indépendante du niveau ; menus fermés au passage |
| Piste de recrutement écrasant l'objet du dernier emplacement de la barre | Placée seulement si l'emplacement est libre |
| Quêtes d'artisanat validées par des clics sans fabrication | Comptage des objets réellement obtenus |

### Contrôle automatique

Au démarrage, et via `/city eco check` (op), le plugin calcule pour chaque matériau racheté
le coût minimal pour l'obtenir en achetant aux PNJ puis en le transformant avec **les vraies
recettes du serveur** (établi, four, tailleur de pierre, forge). Il signale toute combinaison
rentable, y compris pour les récompenses de quêtes. La configuration livrée passe ce
contrôle sans aucune alerte avec les 1 290 recettes vanilla 1.21.1.

---

## 6. Guide d'administration

- **Nouvelle config** : `saveDefaultConfig` n'écrase pas un fichier existant. Sur le serveur
  de test, supprimez `plugins/CityCore/config.yml` pour obtenir la nouvelle version.
- **Données de test** : les identifiants de quêtes et de PNJ ont changé ; repartez d'une base
  propre (supprimez `plugins/CityCore/database.db`) et des PNJ Citizens de test.
- **Skins** : Alderic, Brennan, Jack Sparrow et Gandalf gardent leur skin. Pour les autres,
  renseignez `npc-skins.<clé>.value` et `.signature` (générés par exemple sur mineskin.org),
  sans recompiler. Sans skin, le PNJ apparaît avec le skin par défaut.
- **Réglages courants** : prix et capacités par PNJ (`<clé>.sell-prices`), articles
  (`<clé>.shop`), courbe de demande et consommation (`npc-economy`), délais et primes des
  quêtes des PNJ (`npc-quests`), chartes (`city-tiers`), objectifs (`city-quests.targets`).
  Après chaque modification : `/city eco check`.
- **Serveur à plusieurs** : les stocks sont communs à tous les joueurs ; si les entrepôts
  saturent trop vite, augmentez les capacités ou `consumption-percent-per-hour`.

## 7. À vérifier en jeu

Le code compile et la configuration est validée hors serveur, mais ces points demandent un
test réel :

- affichage des noms longs de PNJ par Citizens (« Indiana Jones », « Robin des Bois ») ;
- apparition des recrues dans leur biome (la recherche se limite aux chunks déjà générés,
  sinon le PNJ apparaît ailleurs dans le rayon) ;
- rythme global (revenus par heure, durée des chapitres) avec vos joueurs.
