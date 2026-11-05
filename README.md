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
- **Filet de capture** : attrape un animal ou un monstre (affaibli) et relâche-le ailleurs, tel quel.
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
`survieplus.recolte`, `survieplus.tombe`, `survieplus.tri`, `survieplus.metiers`, `survieplus.capture`.

## Filet de capture

Recette : 8 ficelles autour d'une perle de l'Ender.

- Clic droit sur un animal ou un monstre : il entre dans le filet.
- Clic droit sur un bloc : il ressort (accroupi sur un coffre ou une porte).

Les monstres doivent être sous 50 % de leur vie, les boss ne se capturent pas, et le filet se déchire après
10 captures. Tout se règle dans la section `capture:` de `config.yml`.

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
