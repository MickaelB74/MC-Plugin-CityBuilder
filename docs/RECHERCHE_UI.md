# Rendre les menus plus beaux et « dans le thème » — recherche (octobre 2026)

Les menus actuels sont des coffres vanilla remplis de vitres. Ils sont pratiques, mais
visuellement génériques. Cette note recense les solutions existantes, leurs contraintes avec
**Paper 1.21.1**, et propose un plan.

> Les informations viennent des pages officielles et de miroirs de documentation consultés en
> octobre 2026 (liens en fin de document). Les prix et les versions supportées évoluent :
> vérifiez-les sur la page officielle avant d'installer.

## 1. Les quatre familles de solutions

| Famille | Ce que ça change | Exemples | Compatible 1.21.1 ? |
|---|---|---|---|
| **Bibliothèque de menus (Java)** | La *logique* : pages, défilement, onglets, animations, code plus simple | InvUI, triumph-gui, InventoryFramework | Oui (InvUI 1.x) |
| **Habillage par resource pack** | Le *dessin* : un fond illustré (parchemin, bois, pierre) remplace les vitres | Pack maison (police + glyphes), Nexo, Oraxen, ItemsAdder, CraftEngine | Oui |
| **Fenêtres natives (« dialogs »)** | De vraies fenêtres modales : texte, boutons, champs de saisie, curseurs | API Dialog de Paper | **Non** : Minecraft 1.21.6+, API Paper 1.21.7+ |
| **Plugins clés en main** | HUD dessiné, cinématiques, arbres de progrès | BetterHud, Typewriter, UltimateAdvancementAPI, zMenu | Oui (selon version) |

## 2. Détail des options

### Bibliothèques Java (pour réécrire nos menus)

- **InvUI** — bibliothèque de GUI pour Paper : types *Normal, Paged, Tab, Scroll*, GUI
  imbriqués (une page défilante dans un onglet). Licence MIT. La v2 vise les versions « 26.x »
  de Minecraft ; la **v1 (1.49) couvre 1.14 → 1.21.11**, donc notre 1.21.1.
  Idéale pour : la boutique de Thorvald (35 articles → pages par rang), l'arbre de progression
  (défilement natif), les onglets Vendre / Entrepôt / Boutique.
- **triumph-gui** — plus léger : actions par case, pagination, défilement, constructeur
  d'items. Licence MIT.
- **InventoryFramework (IF)** — basé sur des « panneaux », menus décrits en XML possibles.
  La compatibilité 1.21.x est à vérifier sur le dépôt officiel (les sources trouvées divergent).

### Habillage par resource pack (le vrai gain esthétique)

Principe commun : le titre du coffre contient un caractère d'une police personnalisée dont
l'image est un fond complet, décalé par des espaces négatifs pour recouvrir les cases. Le
serveur gère les clics ; le resource pack fournit le dessin.

- **Pack maison** — vous avez déjà un resource pack (sons du sommet, du dragon, du pirate).
  On y ajoute une police `citycore:gui` avec quelques fonds médiévaux, et le plugin écrit le
  bon glyphe dans le titre de chaque menu. Gratuit, aucune dépendance ; il faut les images
  (à dessiner, ou packs « medieval GUI » vendus sur BuiltByBit, licence à vérifier).
- **Nexo** — définit des glyphes (texture, `ascent`, `height`) utilisables dans les titres avec
  `<glyph:id>` et `<shift:n>`. *Nexo ne gère que l'aspect visuel, pas l'inventaire* : nos menus
  restent dans le plugin. Paper/Folia 1.21.1 → 1.21.11. Prix non confirmé (le support semble
  lié à une preuve d'achat).
- **Oraxen** — même principe de glyphes dans les titres (modèle « freemium » selon une source
  secondaire, à vérifier). **ItemsAdder** — 24,99 $ sur BuiltByBit.
- **CraftEngine** — licence GPL-3.0, objets/blocs personnalisés côté serveur et génération
  automatique du resource pack ; 1.21 listée. Le constructeur de menus passe par un addon.

Ces quatre plugins se justifient surtout si vous voulez **aussi** des objets, blocs ou meubles
médiévaux personnalisés. Pour de simples fonds de menu, le pack maison suffit.

### Fenêtres natives (dialogs)

Ajoutées dans Minecraft 1.21.6 ; l'API Paper arrive en 1.21.7. Contenu : texte et objets ;
saisies : cases à cocher, choix multiples, champs de texte, curseurs ; boutons de type
confirmation ou liste d'actions. Parfait pour **les conversations à choix avec les PNJ**, la
saisie d'un montant (dépôt dans la caisse, aujourd'hui dans le chat) ou la confirmation d'une
réquisition. Nécessite de **mettre à jour le serveur** au-delà de 1.21.1.

### Plugins clés en main

- **BetterHud** — HUD dessiné (images converties en police, rendues sur des barres de boss),
  pack généré automatiquement ; Paper 1.20.4 → 1.21.x. Pourrait remplacer l'en-tête de la
  liste des joueurs et le scoreboard (caisse, chapitre, quêtes) par un HUD thématique.
- **Typewriter** — dialogues ramifiés, quêtes, cinématiques (trajectoires de caméra), panneau
  web ; Paper/Purpur, dont 1.21–1.21.1. Gratuit mais sous licence propre (ni revente, ni
  redistribution, ni modification). Il recouvre notre système de quêtes : à réserver aux
  cinématiques (arrivée d'un personnage, victoire contre le dragon).
- **UltimateAdvancementAPI** — onglets de progrès personnalisés, notifications, progression par
  joueur ou par équipe ; licence LGPL ; Paper 1.15 → 26.3 ; utilisable comme plugin séparé ou
  intégré (shade). Alternative plus riche à l'onglet natif que j'ai ajouté (voir plus bas).
- **zMenu / DeluxeMenus** — menus décrits en YAML (MiniMessage, PlaceholderAPI). Bien pour des
  menus statiques, peu adaptés à nos menus calculés (stocks, prix dynamiques).

## Choix retenu (octobre 2026)

Menus des habitants redessinés par un pack de ressources maison, dans le style des interfaces
« par joueur » d'Origin Realms, **uniquement pour les menus des PNJ** et **sans aucune texture de
bloc modifiée**. Réalisé : voir `docs/INTERFACES_VALMONT.md`. Les options ci-dessous restent
pour mémoire.

## 3. Recommandation pour CityCore (étude initiale)

1. **Maintenant, sans coût** : habillage par **resource pack maison** (fonds parchemin/bois pour
   le maire, les artisans, la boutique et les Chroniques) + migration progressive des menus vers
   **InvUI 1.x** pour la pagination et le défilement. C'est le meilleur rapport gain/effort.
2. **Si vous pouvez passer en 1.21.7+** : **dialogs natifs** pour les dialogues de PNJ et les
   saisies — c'est le changement le plus visible pour l'immersion.
3. **En option** : Nexo ou CraftEngine si vous voulez un univers d'objets médiévaux
   personnalisés ; BetterHud pour un HUD thématique ; Typewriter pour quelques cinématiques.

Je peux réaliser l'étape 1 (côté plugin) dès que vous avez choisi des fonds de menu, ou
préparer un pack de démonstration avec des fonds provisoires.

## 4. Déjà livré : la progression façon « progrès Minecraft »

- **Onglet « La Renaissance de Valmont »** dans l'écran des progrès (touche L), créé avec l'API
  Bukkit, sans plugin tiers : la Charte, les 10 chapitres, les 21 personnages, les 7 hauts faits,
  avec les notifications « Progrès réalisé ». Tout l'arbre est visible, avec le statut de chaque
  étape (cadre et barre « x/y » au survol). C'est la seule vue d'ensemble : l'ancien menu
  « Chroniques » a été retiré. Option `progression.advancements` dans config.yml.

## Sources

- InvUI : <https://github.com/NichtStudioCode/InvUI>
- triumph-gui : <https://triumphteam.dev/docs/triumph-gui>
- InventoryFramework (miroir du wiki) : <https://skwrus.duckdns.org/devnatan/inventory-framework/wiki>
- Nexo, glyphes : <https://docs.nexomc.com/configuration/glyphs> ; site : <https://nexomc.com/>
- Oraxen, GUI par glyphes : <https://docs.oraxen.com/usage/tutorials/f3g7k>
- ItemsAdder (BuiltByBit) : <https://builtbybit.com/resources/10839/>
- Comparatif ItemsAdder / Oraxen 2026 : <https://mineguard.pro/en/blog/itemsadder-vs-oraxen-custom-items-comparison-2026>
- CraftEngine : <https://www.minecraftbible.com/plugins/project/craftengine> ; <https://builtbybit.com/resources/82674/>
- Guide PaperMC « GUIs avec resource packs » (2022) : <https://forums.papermc.io/threads/framework-for-making-guis-with-resource-packs.662/>
- API Dialog de Paper : <https://docs.papermc.io/paper/dev/dialogs/>
- BetterHud (miroir de documentation) : <https://www.mintlify.com/toxicity188/BetterHud>
- Typewriter : <https://modrinth.com/plugin/typewriter>
- UltimateAdvancementAPI : <https://hangar.papermc.io/DevHeim/UltimateAdvancementAPI>
- zMenu : <https://docs.groupez.dev/zmenu/getting-started> ; <https://hangar.papermc.io/Maxlego08/zMenu>
