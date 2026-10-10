# Intégration des mods (serveur hybride)

CityCore tourne aussi sur un serveur **hybride** qui charge à la fois des mods NeoForge et des
plugins Bukkit/Paper (testé pour Youer 1.21.1). Certains habitants peuvent alors s'appuyer sur
le contenu d'un mod : ses objets, ses créatures, ses succès.

Deux personnages en profitent :

- **Harold**, le dresseur de dragons, lié au mod **Dragon Mounts Remastered** (identifiant
  `dmr`, version analysée : 1.9.2) ;
- **Jack Sparrow**, lié au mod **Tax' Ocean Villager** (identifiant `taxov`, version analysée :
  4.0.1), qui fait croiser des navires au large (voir plus bas).

## Principe

| Situation | Recrutement (quête de la cité) | Installation dans un bâtiment | Menu (vente, boutique, quêtes, récits) |
|---|---|---|---|
| Mod présent | oui | oui | oui |
| Mod absent | oui | oui | non : un dialogue l'explique |

Sans le mod, un clic sur Harold installé joue son dialogue « mod_missing » (lettre par lettre),
puis une ligne grise précise que le mod n'est pas installé. Ses quêtes n'apparaissent pas dans
le panneau des quêtes, et ses succès introuvables ne sont pas signalés dans la console.

## Détection

Au démarrage, le mod est jugé **présent** si l'une de ses traces est visible :

1. un de ses objets (`dmr:dragon_egg`) ;
2. un de ses succès (`dmr:main`) ;
3. la liste des mods du chargeur (NeoForge, Forge ou Fabric), lue par réflexion.

La console l'indique, par exemple :
`🧩 Mod Dragon Mounts Remastered (dmr) présent — objet dmr:dragon_egg : ses habitants sont disponibles.`

Pour tester l'absence sans désinstaller le mod, `config.yml` :

```yaml
mod-integrations:
  dmr: off     # auto = détection | on = forcer présent | off = forcer absent
  taxov: auto  # Jack Sparrow
```

## Objets de mods dans config.yml

Sur Youer, chaque objet de mod devient un `Material` Bukkit : `dmr:dragon_egg` s'appelle
`DMR_DRAGON_EGG` (« : » devient « _ », les autres signes disparaissent), et garde sa clé
d'origine. Dans `config.yml`, on écrit simplement la clé du mod entre guillemets :

```yaml
shop:
  5:
    - {material: "dmr:blank_egg", price: 600, quantity: 1, supply: {max: 1, restock-minutes: 1440}}
```

Valable pour `sell-prices`, `shop` et les `item-pool` des quêtes. Si le mod est absent,
ces lignes sont ignorées sans alerte (une ligne récapitulative dans la console). Les menus
affichent un nom français pour les objets connus (« Œuf de dragon », « Sifflet de dragon
rouge »…), sinon le nom tiré de la clé.

Une quête déjà acceptée qui demande un objet d'un mod désinstallé depuis est écartée
proprement (avertissement d'une ligne, pas d'erreur).

## Harold

- **Place dans l'histoire** : chapitre X (Capitale, épilogue), quête bonus « Le dompteur de dragons ».
  Il n'apparaît qu'après la chute du dragon de l'End : les portails vers les îles extérieures
  sont ouverts, et Harold affirme que les autres dragons ne sont pas comme celui du Néant.
- **Où le trouver** : sur les sommets (pics enneigés, pentes neigeuses, bosquets), jusqu'à
  1 100 blocs de la ville.
- **Rachète** : morue, saumon, poisson tropical, poisson-globe (même prix que Martin),
  cuir, membranes de phantom.
- **Vend** : selles (rang 1), « le manuel du dragonnier » (livre : où trouver les œufs,
  éclosion, apprivoisement, vol), sifflets du mod noir, rouge, bleu et vert, étiquettes (rang 2),
  potion du vol plané (rang 3), « écaille de Furie » (résistance au feu, rang 4), œuf de dragon
  vierge `dmr:blank_egg` (rang 5, 1 par jour).
- **Quêtes** : principales (poisson, cuir, membranes, blocs de nid : magma, glace compactée) ;
  spéciales (chorus, carapaces de shulker, souffle du dragon, échos, **œufs de dragon du mod**,
  phantoms, endermen, shulkers des îles extérieures).
- **Récits** (quêtes personnelles, une par rang) :
  1. *Un œuf bien étrange* — succès du mod `dmr:find_egg` ;
  2. *Ce n'est pas un chien !* — `dmr:hatch_1_dragon_egg` ;
  3. *Pourquoi du poisson ?* — `dmr:tame_dragon` (le dragon s'apprivoise au poisson) ;
  4. *Au-dessus des nuages* — voler à dos de dragon au-dessus de Y = 200 ;
  5. *Les îles du bout du monde* — survoler les îles extérieures de l'End à dos de dragon.
- **Skin** : `npc-skins.harold.value` / `signature` (mineskin.org), puis redémarrage. Sans
  skin, son portrait montre une silhouette sur son décor (crépuscule sur les pics de Beurk,
  Krokmou dans le ciel). Aperçu : `docs/ui-previews/harold.png`.
- **Test rapide** (op) : `/city npc harold spawn` le fait apparaître près de vous, sans attendre
  le chapitre X ; `/city npc harold levelUp` monte son rang pour voir la boutique complète.

Contre l'arbitrage : Harold ne rachète pas les œufs (un œuf vierge acheté, posé puis cassé,
redonne un œuf de dragon) ; la récompense d'une quête « œuf » (200 coins × prime) reste
inférieure au prix de l'œuf vierge.

## Jack Sparrow et les navires du large (Tax' Ocean Villager)

Le mod génère en mer des navires (structures) : **navires pirates** (océans tempérés et chauds),
**vaisseaux de la Marine** (océans froids et profonds), **navires maudits** (pirates et marins
zombies), le **vaisseau de guerre du roi pirate illageois**, avec des villageois aux métiers du
mod (capitaine et matelots pirates, capitaine et marins de la Marine). Il ajoute aussi armures et
outils de pirate et de marin, deux disques et des « générateurs de navire ».

- **Place dans l'histoire** : chapitre III (Village), quête bonus « Une voile à l'horizon » :
  un capitaine sans navire qui guette les vaisseaux du large. Sans le mod, il se recrute et
  s'installe, puis explique qu'il n'y a pas une voile à l'horizon.
- **Récits**, un par rang :
  1. *À l'abordage !* — monter à bord d'un navire pirate (`taxov:pirate_ship`) ;
  2. *Contrebande* — commercer avec un capitaine ou un matelot pirate (métiers du mod) ;
  3. *Réquisitionner ! (terme nautique)* — hisser une bannière noire à bord d'un vaisseau de la
     Marine (`taxov:marine_ship`) ; Jack vend le « pavillon noir » ;
  4. *La malédiction de l'or aztèque* — aborder un navire maudit de nuit ;
  5. *Le roi des pirates* — terrasser le roi pirate illageois (`taxov:illager_pirate_king`).
- **Boutique** (en plus de ses trésors de la mer) : pavillon noir (rang 1), disque pirate
  (rang 2), tricorne pirate (rang 3), sabre pirate (rang 4) et un **navire pirate**
  (`taxov:ship_pirate`, l'objet du mod qui le fait apparaître) au rang 5, une fois par semaine.
- **Quêtes** : poudre à canon et lingots d'or en principales ; plastron pirate, disque de la
  Marine, pillards (l'équipage du roi pirate) en spéciales.
- **Détection technique** : « à bord » = debout sur le navire (ni dans l'eau, ni en barque, un
  bloc plein sous les pieds : une dalle ou une marche de pont compte) et soit dans la boîte d'une
  structure générée du mod (API
  `World#getStructures`), soit à moins de 12 blocs d'un poste d'équipage du mod
  (`taxov:block_pirate` / `block_crew` pour les pirates, `block_captain` / `block_sailor` pour la
  Marine). Ces postes font partie du gabarit de chaque navire : un navire posé à la main (objet
  « générateur » du mod, `/place`) est donc reconnu aussi, tant qu'ils ne sont pas cassés (les
  objets du mod posent les gabarits `taxov:pirateship`, `marineship`, `zombiepirate` et
  `zombiemarine`, vérifié dans le mod). Ces postes ne se fabriquent pas : on ne les obtient qu'en
  les cassant sur un navire. La recherche des postes a lieu au plus toutes les 1,5 s. Navire
  maudit posé à la main : postes d'équipage + villageois zombies à proximité. Le commerce passe
  par l'événement d'échange de Paper ; le roi pirate est reconnu comme Harold reconnaît son
  dragon (clé, nom Youer ou classe du mod).
- **Mise à jour de config.yml** : à la version 6, l'ancienne section `jacksparrow` est remplacée
  par la nouvelle (l'ancienne reste dans la copie `config-ancien-v5-….yml`).

## Ajouter un autre mod

1. Ajouter le mod à `ModIntegrations.Mod` (identifiant, nom, succès et objet témoins).
2. Ajouter sa clé à la section `mod-integrations` de `config.yml`.
3. Pour un habitant lié : dernier argument `"identifiant"` dans `CityNPC`, dialogues
   `mod_missing` et `building_assign_unavailable`.
4. Référencer ses objets par leur clé (`"mod:objet"`) dans `config.yml`, et ses succès avec
   `modAdvancement("mod:succes")` dans `PersonalQuestCatalog`.

## À vérifier en jeu

Ces points dépendent de Youer et n'ont pas pu être testés hors du serveur :

- la ligne de détection dans la console au démarrage ;
- l'achat d'un sifflet ou d'un œuf vierge (objet du mod créé par le plugin) ;
- la validation des récits de vol : le type de la monture est reconnu par sa clé
  (`dmr:dragon`), son nom Youer (`DMR_DRAGON`) ou, à défaut, la classe du dragon du mod.
