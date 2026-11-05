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
| `/metiers donner <joueur> <metier> <xp>` | Donne de l'XP de métier | `survieplus.admin` |
| `/metiers reset <joueur>` | Remet les métiers d'un joueur à zéro | `survieplus.admin` |
| `/survieplus reload` | Recharge la configuration | `survieplus.admin` |

Permissions des fonctions (toutes à `true` par défaut) : `survieplus.bucheron`, `survieplus.mineur`,
`survieplus.recolte`, `survieplus.tombe`, `survieplus.tri`, `survieplus.metiers`.

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
```

La version se change dans `src/main/resources/paper-plugin.yml` (et dans `CHANGELOG.md`).
