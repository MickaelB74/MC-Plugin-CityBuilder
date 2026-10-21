# CityCore — Compagnon (mod serveur)

Mod NeoForge (Minecraft 1.21.1) qui fait, pour le plugin CityCore, ce qu'un plugin Paper ne peut
pas faire seul. Il remplace les anciens mods `citycore-epicfight` et `citycore-journeymap`.

Deux modules indépendants, chacun actif **seulement si son mod est installé sur le serveur** :

| Module | Mod requis | Effet |
|---|---|---|
| Epic Fight | Epic Fight 21.17.3.1 | les PNJ du plugin (artisans, habitants, gardes, armées ennemies) sont animés par Epic Fight, comme les monstres |
| JourneyMap | JourneyMap 6.0.5 ou plus | les parcelles de la ville s'affichent sur la carte plein écran et la minicarte des joueurs |

Sans l'un de ces mods, son module reste éteint et aucune de ses classes n'est chargée (le serveur
démarre normalement). Le plugin ne dépend d'aucun mod : il communique avec ce mod par des
étiquettes d'entités (gestes) et un fichier (`plugins/CityCore/carte.json`).

## Module Epic Fight

**Pourquoi** : Citizens fait des PNJ à skin des entités « joueur ». Epic Fight anime un joueur à
partir des touches de son clavier, envoyées par son jeu ; un PNJ n'en a pas, il le croit immobile
et le laisse glisser dans sa pose d'attente.

**Ce que fait le module** (côté serveur, rien à installer chez les joueurs en plus d'Epic Fight) :
- mesure le déplacement réel de chaque PNJ et l'annonce comme des touches (avancer, reculer, pas
  de côté) aux joueurs qui le voient : marche, marche arrière, arrêt ; au-delà d'une vitesse, le
  PNJ est marqué « en course » (animation de course) ;
- annonce les PNJ en mode combat d'Epic Fight à chaque joueur qui les voit apparaître ;
- joue les gestes signalés par le plugin (étiquettes `citycore.geste.attaque` et
  `citycore.geste.tir`) : enchaînement de l'arme tenue, animation de tir de l'arc ou de
  l'arbalète. Affichage seulement : les dégâts restent ceux du plugin. Une étiquette déjà posée
  quand le PNJ est vu pour la première fois (restée à l'arrêt du serveur, remise par Citizens à sa
  réapparition) est retirée sans être jouée ;
- remet à zéro les animations d'un PNJ chez un joueur peu après qu'il le voit apparaître, puis
  chez tous ceux qui le voient quand il reste immobile et sans geste (`epicfight.gestes.remiseAZero`).
  Chez Epic Fight, tout balancement de bras lance une animation de frappe (« creuser ») qui pouvait
  rester bloquée en boucle, surtout à la connexion après un redémarrage : elle s'arrête ainsi.
  Le plugin retire de son côté le balancement de bras que Citizens fait faire aux PNJ à leur
  apparition (réglage `reset-yaw-on-spawn`) quand Epic Fight est présent.

Un PNJ = une entité joueur présente dans un monde mais absente de la liste des joueurs connectés.

Le module utilise des classes internes d'Epic Fight (pas d'API officielle) : il est calé sur
Epic Fight 21.17.3.1 ; une autre version peut demander une retouche. En cas d'incompatibilité, le
mod l'écrit une fois dans le journal et le serveur continue normalement.

## Module JourneyMap

Sur la carte JourneyMap de chaque joueur (plein écran et minicarte) :
- **chaque parcelle** (chunk revendiqué par la ville) est remplie d'une teinte translucide ;
- **le contour extérieur** de la ville est tracé (les côtés partagés entre parcelles disparaissent ;
  une parcelle non revendiquée entourée par la ville reste un trou ; deux morceaux qui ne se
  touchent que par un coin restent deux zones) ;
- **le nom de la ville** est affiché sur la zone, une bulle au survol donne les parcelles et les
  habitants (« Valmont — 42 parcelles · 15 habitants ») ;
- en option (activé par défaut), **le quadrillage** : contour fin de chaque parcelle, sous le
  contour de la ville.

Fonctionnement : le plugin écrit `plugins/CityCore/carte.json` à chaque claim, unclaim ou
fondation de la ville (et le revoit toutes les 30 s) ; le mod contrôle le fichier toutes les 2 s,
calcule les tracés et les envoie à chaque joueur par l'**API serveur de JourneyMap**
(superpositions envoyées par le serveur). Envois : à chaque changement du fichier, 5 s puis 30 s
après la connexion, 2 s puis 10 s après un changement de dimension, et toutes les 2 min par
sécurité (JourneyMap remplace la version précédente, sans doublon).

Les joueurs n'ont besoin que de JourneyMap (6.0.5 ou plus, la même version que le serveur de
préférence). L'API JourneyMap n'est utilisée qu'à la compilation (`compileOnly`) : sa licence
interdit d'en redistribuer les classes, elles sont fournies par le mod JourneyMap à l'exécution.

## Construire

```bash
./gradlew build
```

Résultat : `build/libs/citycore-companion-1.0.0.jar`. Le premier build télécharge et prépare
Minecraft et NeoForge (plusieurs minutes) ; les suivants sont rapides.

Dépendances de compilation (aucune n'est recopiée dans le jar) :
- Epic Fight : le jar du serveur, réglage `epicfight_jar` de `gradle.properties` (autre
  emplacement : `./gradlew build -Pepicfight_jar=chemin/vers/epic-fight.jar`) ;
- JourneyMap : l'API `info.journeymap:journeymap-api-neoforge` (`journeymap_api_version`,
  2.0.0-1.21.1), téléchargée depuis https://maven.blamejared.com.

## Installer (serveur)

1. Dans le dossier `mods` du serveur, **retirer les anciens jars** `citycore-epicfight-*.jar` et
   `citycore-journeymap-*.jar` (sinon les PNJ seraient animés deux fois).
2. Copier `citycore-companion-1.0.0.jar` dans `mods`, à côté d'Epic Fight et de JourneyMap
   (JourneyMap NeoForge 1.21.1, **6.0.9** conseillé : il faut l'installer **aussi sur le
   serveur**, pas seulement chez les joueurs).
3. Redémarrer. Journal attendu :
   - « Epic Fight présent : les PNJ de CityCore seront animés » ;
   - « JourneyMap … présent : les parcelles de la ville seront envoyées aux cartes des joueurs »,
     « API serveur de JourneyMap reçue », puis « Ville sur les cartes JourneyMap : N parcelle(s) ».

   Si le journal dit « JourneyMap … n'a pas l'API serveur v2 », la version installée est trop
   ancienne (ex. 6.0.0-beta.55) : le module de la carte reste éteint, le serveur démarre quand
   même. Remplacer JourneyMap par la 6.0.5 ou plus (6.0.9 conseillée), sur le serveur et chez les
   joueurs.
4. L'ancien fichier `config/citycore_epicfight.toml` n'est plus lu (il peut être supprimé) ;
   reporter dans `config/citycore_companion.toml` les réglages qui avaient été modifiés.

## Réglages

`config/citycore_companion.toml` (créé au premier démarrage, relu quand il est modifié) :

| Réglage | Défaut | Effet |
|---|---|---|
| `epicfight.actif` | true | false : module Epic Fight éteint |
| `epicfight.deplacements.marche` | 0.02 | vitesse (blocs/tick) à partir de laquelle un PNJ marche |
| `epicfight.deplacements.course` | 0.24 | vitesse à partir de laquelle il court ; 0 = jamais |
| `epicfight.gestes.attaques` | true | coups animés (gardes, soldats ennemis) |
| `epicfight.gestes.tirs` | true | tirs animés (archers, arbalétriers) |
| `epicfight.gestes.remiseAZero` | 20 | secondes sans geste ni déplacement avant de remettre à zéro les animations d'un PNJ (un geste bloqué en boucle s'arrête) ; 0 = jamais |
| `epicfight.modeCombat` | true | annonce les PNJ en mode combat d'Epic Fight |
| `epicfight.journal` | false | journal détaillé pour les tests (PNJ trouvés, marche/arrêt, gestes) |
| `journeymap.actif` | true | false : la ville disparaît des cartes, plus rien n'est envoyé |
| `journeymap.fichier` | `plugins/CityCore/carte.json` | fichier écrit par le plugin (relatif au dossier du serveur) |
| `journeymap.verification` | 2 | secondes entre deux contrôles du fichier |
| `journeymap.renvoi` | 120 | secondes entre deux renvois de sécurité ; 0 = jamais |
| `journeymap.journal` | false | journal détaillé (envois par joueur) |

Apparence de la ville sur la carte : section `carte` du `config.yml` de CityCore (couleur,
opacités, épaisseurs, minicarte, quadrillage) ; `/city carte` (admin) donne l'état,
`/city carte recharger` relit ces réglages.
