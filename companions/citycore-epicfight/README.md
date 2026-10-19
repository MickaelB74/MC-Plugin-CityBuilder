# CityCore — Epic Fight (mod serveur)

Petit mod NeoForge (Minecraft 1.21.1) qui donne aux PNJ du plugin CityCore (artisans, habitants,
gardes, armées ennemies) les animations d'Epic Fight, comme les monstres.

**Pourquoi** : Citizens fait des PNJ à skin des entités « joueur ». Epic Fight anime un joueur à
partir des touches de son clavier, envoyées par son jeu ; un PNJ n'en a pas, il le croit immobile
et le laisse glisser dans sa pose d'attente.

**Ce que fait le mod** (côté serveur, rien à installer chez les joueurs) :
- mesure le déplacement réel de chaque PNJ et l'annonce comme des touches (avancer, reculer, pas
  de côté) aux joueurs qui le voient : marche, marche arrière, arrêt ; au-delà d'une vitesse, le
  PNJ est marqué « en course » (animation de course) ;
- annonce les PNJ en mode combat d'Epic Fight à chaque joueur qui les voit apparaître ;
- joue les gestes signalés par le plugin (étiquettes `citycore.geste.attaque` et
  `citycore.geste.tir`) : enchaînement de l'arme tenue, animation de tir de l'arc ou de
  l'arbalète. Affichage seulement : les dégâts restent ceux du plugin.

Un PNJ = une entité joueur présente dans un monde mais absente de la liste des joueurs connectés.

## Construire

```bash
./gradlew build
```

Le jar d'Epic Fight sert à la compilation (réglage `epicfight_jar` de `gradle.properties`, par
défaut celui du serveur). Résultat : `build/libs/citycore-epicfight-1.0.0.jar`.

Le mod utilise des classes internes d'Epic Fight (pas d'API officielle) : il est calé sur
Epic Fight 21.17.3.1 ; une autre version peut demander une retouche. En cas d'incompatibilité, le
mod l'écrit une fois dans le journal et le serveur continue normalement.

## Installer (serveur)

Dans le dossier `mods` du serveur, à côté d'Epic Fight, puis redémarrer. Journal attendu :
« Epic Fight présent : les PNJ de CityCore seront animés ».

## Réglages

`config/citycore_epicfight.toml` (créé au premier démarrage, relu quand il est modifié) :

| Réglage | Défaut | Effet |
|---|---|---|
| `deplacements.marche` | 0.02 | vitesse (blocs/tick) à partir de laquelle un PNJ marche |
| `deplacements.course` | 0.24 | vitesse à partir de laquelle il court ; 0 = jamais |
| `gestes.attaques` | true | coups animés (gardes, soldats ennemis) |
| `gestes.tirs` | true | tirs animés (archers, arbalétriers) |
| `modeCombat` | true | annonce les PNJ en mode combat d'Epic Fight |
| `journal` | false | journal détaillé pour les tests (PNJ trouvés, marche/arrêt, gestes) |
