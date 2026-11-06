# SurviePlus

Plugin [Paper](https://papermc.io/) qui améliore la survie, sans la dénaturer.
Chaque fonction est un module qu'on peut couper dans `config.yml` ou, pour le joueur, dans le menu `/sp`.

## Fonctions

- **Bûcheron** : abattre un arbre entier d'un coup, avec replantation automatique.
- **Mineur** : miner un filon entier d'un coup.
- **Récolte** : récolter et replanter les cultures au clic droit.
- **Tombe** : à la mort, les objets sont gardés dans une tombe au lieu de tomber par terre.
- **Tri** : Maj + clic droit sur une case vide pour trier un coffre ou son inventaire.
- **Sommeil partagé** : un pourcentage des joueurs suffit pour passer la nuit.
- **Filet de capture** : attrape un animal ou un monstre du premier coup et relâche-le ailleurs, tel quel.
- **Bestiaire** : chaque espèce attrapée au filet s'y ajoute ; récompenses par palier et médaille du naturaliste.
- **Pioche du mineur** : une pioche à soi, incassable, qui grandit avec le niveau de Mineur (jusqu'au 3×3).
- **Métiers** : Bûcheron, Mineur, Fermier, Pêcheur et Chasseur progressent en jouant (50 niveaux,
  bonus aux niveaux 20 et 35, objets offerts, titres, classements).
- **Menus en images** grâce à un resource pack (les joueurs qui le refusent gardent les menus classiques).
- **Livre guide** offert à la première connexion.

Le détail de chaque version est dans le [CHANGELOG](CHANGELOG.md).

## Commandes

| Commande | Description | Permission |
|---|---|---|
| `/survieplus` (`/sp`) | Menu des options du joueur | — |
| `/sp guide` | Redonne le livre guide | — |
| `/metiers` (`/metier`) | Niveaux, bonus et classements | `survieplus.metiers` |
| `/bestiaire` | Espèces attrapées au filet, paliers de récompenses | `survieplus.capture` |
| `/pioche` | Rend sa pioche du mineur perdue (l'ancienne s'effrite) | `survieplus.pioche` |
| `/metiers donner <joueur> <metier> <xp>` | Donne de l'XP de métier | `survieplus.admin` |
| `/metiers reset <joueur>` | Remet les métiers d'un joueur à zéro | `survieplus.admin` |
| `/survieplus reload` | Recharge la configuration | `survieplus.admin` |

Permissions des fonctions (toutes à `true` par défaut) : `survieplus.bucheron`, `survieplus.mineur`,
`survieplus.recolte`, `survieplus.tombe`, `survieplus.tri`, `survieplus.metiers`, `survieplus.capture`,
`survieplus.pioche`.

## Filet de capture

Recette : 8 ficelles autour d'une perle de l'Ender.

- Clic droit sur un animal ou un monstre : il entre dans le filet.
- Clic droit sur un bloc : il ressort (accroupi sur un coffre ou une porte).

Animaux et monstres s'attrapent du premier coup, les boss ne se capturent pas, et le filet se déchire après
10 captures. Tout se règle dans la section `capture:` de `config.yml`.

Chaque nouvelle espèce attrapée s'ajoute au **bestiaire** (`/bestiaire`) : récompenses à 10, 25 et 50 espèces
et quand on les a toutes (section `bestiaire:`), XP de Chasseur, et médaille du naturaliste pour qui le complète.

## Pioche du mineur

Recette : une pioche en pierre entourée de 4 éclats d'améthyste et de 4 lingots de cuivre.

Elle est liée à celui qui la fabrique (une seule par joueur), incassable, gardée à la mort, et personne d'autre
ne peut miner avec ni la ramasser. Ni enclume, ni meule, ni table d'enchantement : elle évolue seule.
Son niveau est le **niveau de Mineur** du joueur : elle mine un peu plus vite à chaque niveau, donne
+15 % d'XP de Mineur, et gagne :

| Niveau | Gain |
|---|---|
| 5 | Fortune I |
| 10 | Devient en fer |
| 15 | Aimant : le butin va dans l'inventaire |
| 20 | Fortune II |
| 25 | Devient en diamant |
| 30 | Vision nocturne sous terre : sous le niveau de la mer, sans ciel au-dessus (pioche en main) |
| 35 | *(fonte automatique : bonus du métier)* |
| 40 | Devient en netherite, Fortune III |
| 45 | Filons plus grands (128 blocs) |
| 50 | Minage 3×3 (option de `/sp`, accroupi = un bloc) et apparence Grand maître |

Perdue ? `/pioche` en refait une, et l'ancienne s'effrite si elle réapparaît. Réglages : section `pioche:`.

## Prérequis

- Serveur Paper 26.x (`api-version: 26.3`)
- Java 25

## Compiler

Le projet se compile avec les jars de Paper du serveur : le dossier du plugin doit être placé dans le
dossier du serveur (à côté de `libraries/`).

```powershell
powershell -File build.ps1              # compile, lance les tests, crée build\SurviePlus-<version>.jar
powershell -File build.ps1 -Installer   # ... et copie le jar dans plugins\
powershell -File build.ps1 -SansTests   # sans les tests
powershell -File build.ps1 -Publier     # ... et envoie le pack des menus sur GitHub (Release "packs")
```

Le pack des menus (resource pack) est téléchargé par les joueurs depuis la Release `packs` de ce dépôt
(`pack.adresse` dans `config.yml`). À chaque version qui change le pack, lancer `build.ps1 -Publier` :
la console du serveur prévient au démarrage si le pack n'est pas en ligne.

La version se change dans `src/main/resources/paper-plugin.yml` (et dans `CHANGELOG.md`).
