# CityCore — JourneyMap (mod serveur)

Petit mod NeoForge (Minecraft 1.21.1) qui dessine les bordures de la ville du plugin CityCore sur
la carte JourneyMap des joueurs (plein écran et minicarte).

- Le plugin CityCore écrit `plugins/CityCore/carte.json` (chunks revendiqués, nom, couleur).
- Ce mod le relit dès qu'il change et envoie la zone à chaque joueur avec l'**API serveur de
  JourneyMap** (superpositions envoyées par le serveur, JourneyMap 6.0.5 et plus).
- Les joueurs n'ont besoin que de JourneyMap.

## Construire

```bash
./gradlew build
```

Résultat : `build/libs/citycore-journeymap-1.0.0.jar`. Le premier build télécharge et prépare
Minecraft et NeoForge (plusieurs minutes) ; les suivants sont rapides.

## Installer (serveur)

Dans le dossier `mods` du serveur : JourneyMap NeoForge 1.21.1 (6.0.9) et
`citycore-journeymap-1.0.0.jar`, puis redémarrer. Journal attendu :
« API serveur de JourneyMap reçue », puis « Bordures de la ville : 1 zone(s) envoyée(s) ».

Sans JourneyMap, le mod ne fait rien (le serveur démarre normalement).

## Réglages

Section `carte` du config.yml de CityCore (couleur, opacités, épaisseur, nom) ;
`/city carte` donne l'état, `/city carte recharger` relit les réglages.

L'API JourneyMap n'est utilisée qu'à la compilation (`compileOnly`) : sa licence interdit d'en
redistribuer les classes, elles sont fournies par le mod JourneyMap à l'exécution.
