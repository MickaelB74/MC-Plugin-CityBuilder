# CityCore — Bot Discord

Bot Discord (Java 21, [JDA](https://github.com/discord-jda/JDA)) qui affiche la vie du serveur
CityCore. Il tourne **à part du serveur Minecraft** et interroge l'API du mod « CityCore —
Compagnon » (module Discord).

```
plugin CityCore ──(fichiers plugins/CityCore/discord/)──▶ mod compagnon ──(API HTTP + jeton)──▶ bot ──▶ Discord
        ▲                                                       │
        └──────────── messages de Discord affichés en jeu ◀─────┘
```

## Les salons

Créés par le bot au premier lancement, dans une catégorie « Valmont » (noms réglables, et
modifiables ensuite dans Discord : le bot retient leurs identifiants).

| Salon | Contenu | Accès |
|---|---|---|
| `#évènements` | une carte par fait de la ville : sièges (début, victoire, défaite, bilan), raids (levée, départ, issue), bâtiments, chapitres et quêtes de la ville, parcelles, arrivées d'artisans et d'habitants, garde, niveaux des joueurs et des artisans, quêtes validées, récits terminés | lecture seule |
| `#valmont` | **une** grande carte de la ville, modifiée sur place : palier et chapitre, caisse, parcelles, bâtiments, habitants et lits, satisfaction, artisans, garde, sièges, raids, quêtes du chapitre, calendrier, joueurs connectés | lecture seule |
| `#admin` | journal du serveur : commandes des opérateurs et de la console, changements de mode de jeu, réinitialisation de la partie | caché (administrateurs du Discord ; ajoutez-y vos rôles) |
| `#chat` | discussion avec le jeu : `/discord <message>` en jeu, `/chat <message>` sur Discord ; connexions et déconnexions | la commande `/chat` seulement |
| `#joueurs` | une carte par joueur, modifiée sur place : tête du joueur, présence, niveau, métier, bourse, barre d'expérience, rang au classement | lecture seule |
| 🔊 statut | salon vocal fermé dont le nom donne l'état : `🟢 Valmont · 3/20 joueurs` ou `🔴 Valmont · hors ligne` | personne n'y entre |

Le statut du bot (sous son nom) reprend le même texte **en temps réel**. Le nom du salon vocal
suit d'aussi près que Discord le permet : il n'accepte que deux changements de nom d'un salon par
tranche de dix minutes. Le bot renomme donc le salon dans les deux secondes qui suivent une
connexion ou une déconnexion tant qu'aucun changement n'a eu lieu depuis dix minutes ; le second
changement de la tranche attend que le nombre de joueurs soit stable depuis 45 secondes (des amis
qui arrivent l'un après l'autre ne le gaspillent pas) ; au-delà, le nom patiente jusqu'à la fin de
la tranche. La carte de `#valmont` et les cartes de `#joueurs` suivent, elles, sans limite.

### Droits des salons

Posés à la création des salons, et vérifiés à chaque lancement du bot (seuls les droits de
« tout le monde » sont touchés ; les rôles ajoutés à la main sont conservés) :

- `#évènements`, `#valmont`, `#joueurs`, `#admin` et le salon vocal : aucune écriture (messages,
  fils, réactions, fichiers, commandes) ;
- `#chat` : la commande `/chat` et rien d'autre. Discord n'accepte une commande que dans un salon où
  l'on peut envoyer des messages : ce droit reste donc ouvert, tout le reste est fermé, et le bot
  **supprime aussitôt tout message tapé à la main** (avec un rappel de quelques secondes). Il lui
  faut pour cela le droit « Gérer les messages ».

Corriger les droits de salons **déjà créés** demande au bot le droit « Gérer les rôles » : sans
lui, le bot signale l'écart au lancement et laisse les salons tels quels. Ce droit peut être retiré
une fois les salons corrigés.

Aucun message du bot ne peut mentionner qui que ce soit (`@everyone` tapé en jeu reste du texte).

## Mise en place

### 1. Côté serveur Minecraft

1. Installer le plugin CityCore et le mod `citycore-companion` à jour, puis démarrer le serveur.
2. Le journal du serveur annonce « API Discord à l'écoute sur http://127.0.0.1:8787 ».
3. Ouvrir `config/citycore_companion.toml`, section `[discord]` : la valeur de `jeton` a été créée
   au démarrage. Elle servira au bot (`api.jeton`).

Bot sur une autre machine que le serveur : mettre `adresse = "0.0.0.0"` dans cette section et
n'ouvrir le port (8787) qu'à la machine du bot. L'API n'est pas chiffrée : sur Internet, passez par
un tunnel ou un réseau privé.

### 2. Créer le bot chez Discord

1. <https://discord.com/developers/applications> → **New Application**.
2. Onglet **Bot** → **Reset Token** : copier le jeton (`discord.jeton`). Aucune « Privileged
   Gateway Intent » n'est nécessaire.
3. Inviter le bot sur votre serveur Discord : onglet **OAuth2 → URL Generator**, cocher `bot` et
   `applications.commands`, puis les droits : *Voir les salons*, *Gérer les salons*, *Envoyer des
   messages*, *Intégrer des liens*, *Voir les anciens messages*, *Gérer les messages*, *Se connecter*. (Si le bot n'est
   pas encore membre du serveur, il affiche lui-même le lien d'invitation au lancement.)
4. Dans Discord : **Paramètres → Avancés → Mode développeur**, puis clic droit sur le serveur →
   **Copier l'identifiant du serveur** (`discord.serveur`).

### 3. Lancer le bot

```bash
./gradlew build
```

Résultat : `build/libs/citycore-discord-bot-1.0.0.jar` (un seul fichier, bibliothèques comprises).

Sous Windows, `lancer.bat` démarre le bot depuis ce dossier. Sinon :

```bash
java -jar build/libs/citycore-discord-bot-1.0.0.jar
```

Au premier lancement, le bot dépose `bot.properties` dans le dossier courant et s'arrête : y
renseigner `discord.jeton`, `discord.serveur` et `api.jeton`, puis relancer. Le bot crée alors les
salons, enregistre la commande `/chat` et commence à publier.

Deux fichiers restent à côté du bot, **à ne pas partager ni versionner** (ils sont ignorés par git) :
`bot.properties` (les jetons) et `etat-bot.json` (identifiants des salons et des messages, numéro
du dernier événement publié).

## Réglages (`bot.properties`)

| Réglage | Défaut | Effet |
|---|---|---|
| `discord.jeton` | — | jeton du bot Discord |
| `discord.serveur` | — | identifiant du serveur Discord |
| `api.adresse` | `http://127.0.0.1:8787` | adresse de l'API du mod compagnon |
| `api.jeton` | — | `discord.jeton` de `config/citycore_companion.toml` |
| `salons.categorie`, `salons.evenements`, `salons.ville`, `salons.admin`, `salons.chat`, `salons.joueurs` | Valmont, évènements, valmont, admin, chat, joueurs | noms des salons à leur création |
| `salons.<salon>.id`, `salons.statut.id` | vide | identifiant d'un salon existant à utiliser au lieu d'en créer un |
| `statut.nom` | vide | nom du serveur dans le statut ; vide = celui donné par le serveur, sinon le nom de la ville (« A Minecraft Server » n'est jamais affiché) |
| `cartes.intervalle-secondes` | 15 | intervalle de mise à jour des cartes |
| `cartes.couleur` | F1CF5E | couleur de la carte de la ville |
| `cartes.tete` | vide | image de la tête des joueurs : adresse avec `{nom}` ou `{uuid}` ; vide = `https://mc-heads.net/head/{nom}/128` (le pseudo est transmis à ce service) ; `aucune` = pas d'image |

Côté serveur : ce qui est publié se règle dans la section `discord` du `config.yml` de CityCore
(événements ignorés, journal des commandes, connexions) ; le port, l'adresse et le jeton de l'API
dans la section `[discord]` de `config/citycore_companion.toml`.

## Comportement

- **Serveur Minecraft arrêté** : le statut passe « hors ligne » après deux essais manqués, la carte
  de la ville garde son dernier état (pied de carte « Serveur hors ligne »), les joueurs passent
  hors ligne. Tout reprend seul au retour du serveur.
- **Bot arrêté** : à son retour, il publie les événements manqués (les 500 dernières lignes du
  journal sont gardées par le serveur ; au-delà de 40 d'un coup, seuls les 40 plus récents sont
  repris).
- **Une carte n'est modifiée que si son contenu a changé** ; un message supprimé à la main est
  renvoyé.
- `/chat` répond à son auteur seul (« message affiché en jeu », ou « le serveur ne répond pas »).

## L'API du mod compagnon

Toutes les demandes portent l'en-tête `Authorization: Bearer <jeton>` ; réponses en JSON.

| Route | Réponse |
|---|---|
| `GET /api/v1/etat` | `serveur` (nom, joueurs, max, enLigne), `dernierEvenement`, `citycore` (contenu de `etat.json` : `ville`, `joueurs`) |
| `GET /api/v1/evenements?apres=N&attente=S` | lignes du journal de numéro supérieur à N ; sans ligne nouvelle, la réponse attend jusqu'à S secondes (30 au plus). Sans `apres` : seulement `dernier` |
| `POST /api/v1/chat` `{"auteur": "…", "message": "…"}` | affiche le message aux joueurs ; `{"ok": true, "joueurs": n}` |

Une ligne du journal : `{"id", "date", "canal": "evenement" | "admin" | "chat", "type", "titre", "texte", "joueur"}`.
