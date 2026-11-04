# Changelog

Toutes les versions de SurviePlus. Format : [Keep a Changelog](https://keepachangelog.com/fr/1.1.0/),
numéros de version : [SemVer](https://semver.org/lang/fr/) (nouvelle fonction = 1.x.0, correctif = 1.x.y).

## [1.1.0] — 2026-11-04

Les métiers remplacent AuraSkills : on progresse en jouant, sans mana, sans capacités et sans stats.

### Ajouté
- **5 métiers qui progressent en même temps** : Bûcheron, Mineur, Fermier, Pêcheur, Chasseur. 50 niveaux,
  de plus en plus longs (50 XP pour le niveau 1, 1 275 XP pour le niveau 50).
  - XP gagnée en cassant des bûches, en minant (plus le minerai est rare, plus ça rapporte), en récoltant
    des cultures mûres, en faisant se reproduire des animaux, en pêchant et en tuant des monstres.
  - Chance de récolte, de prise ou de butin **double** : +0,5 % par niveau, jusqu'à 25 %.
  - **Deux bonus par métier**, aux niveaux 20 et 35 :

    | Métier | Niveau 20 | Niveau 35 |
    |---|---|---|
    | Bûcheron | La hache s'use moins en abattant un arbre entier | Pomme ou pousse en plus |
    | Mineur | La pioche s'use moins en minant un filon entier | Fer, or et cuivre tombent en lingots |
    | Fermier | Récolte au clic droit en 3×3 | Les cultures proches poussent plus vite |
    | Pêcheur | Le poisson mord plus vite | Plus de trésors |
    | Chasseur | Plus d'XP sur les monstres | Chance d'avoir la tête du monstre |

  - **Objets offerts** aux niveaux 10, 25 et 50 (outils enchantés, livre Raccommodage…).
  - **Titres** à partir du niveau 10 (Apprenti, Compagnon, Expert, Maître, Grand maître), visibles en
    survolant le pseudo dans le chat. Annonce à tout le serveur aux niveaux 40 et 50.
  - **Bonus de groupe** : +10 % d'XP quand un autre joueur est à moins de 20 blocs.
  - **Barre de progression** en haut de l'écran pendant 3 s à chaque gain (désactivable dans `/sp`).
  - **`/metiers`** : niveaux, bonus, prochaines étapes, top 5 de chaque métier, niveau total et classement général.
  - Admin : `/metiers donner <joueur> <metier> <xp>` et `/metiers reset <joueur>`.
  - Anti-triche : rien en créatif, rien pour les blocs posés par un joueur (même poussés par un piston),
    rien pour les monstres de spawner, pas de double avec Toucher de soie ni sur l'équipement des monstres.
- **Livre guide** donné à la première connexion : sommaire cliquable, une page par métier avec ses bonus
  et ses cadeaux, page « Ta progression » quand on l'ouvre depuis `/sp`. `/sp guide` en redonne un.

### Modifié
- La **replantation auto**, la **récolte au clic droit** et la **tombe** se débloquent au niveau 3 de
  Bûcheron, Fermier et Chasseur. Réglable dans `metiers.deblocage` (0 = libre pour tous).

### Corrigé
- Le `config.yml` livré rangeait l'XP des métiers sous `guide:` au lieu de `metiers:`. Sur un serveur
  sans métiers auparavant, le Mineur et le Chasseur n'auraient gagné aucune XP (donc pas de tombe)
  et le Pêcheur 3 XP par prise. Corrigé avant toute mise en ligne.
- Les pourcentages s'affichent avec une virgule (« 12,5 % ») quelle que soit la langue du serveur.
- Une sauvegarde automatique de `metiers.yml` ne peut plus écraser celle faite à l'arrêt du serveur.

### Technique
- Tests automatiques (`src/test/java`), lancés par `build.ps1` avant de créer le jar.
- Le nom du jar suit la version de `paper-plugin.yml`.

### Mettre à jour depuis la 1.0.0
1. Faire une sauvegarde du monde.
2. Supprimer `SurviePlus-1.0.0.jar` et mettre `SurviePlus-1.1.0.jar` : deux versions du même plugin
   empêchent son chargement.
3. Retirer AuraSkills (ses niveaux ne sont pas repris : tout le monde repart de zéro en métiers).
4. Redémarrer. Le `config.yml` existant est complété tout seul ; les réglages déjà présents ne changent pas.
5. Prévenir les joueurs : replantation, récolte au clic droit et tombe sont grisées jusqu'au niveau 3
   du métier correspondant.

## [1.0.0]

### Ajouté
- **Abattage d'arbre** : accroupi + hache, tout l'arbre tombe. Replantation auto et chute des feuilles.
- **Minage de filon** : accroupi + bon outil, tout le filon est miné.
- **Récolte au clic droit** : la culture mûre est récoltée et replantée.
- **Tombe** : à la mort, les objets et l'XP vont dans un coffre que seul son propriétaire peut ouvrir.
- **Tri d'inventaire** : Maj + clic droit sur une case vide.
- **Sommeil partagé** : pourcentage de joueurs qui doivent dormir, réglable par les admins.
- **Menu `/sp`** : chaque joueur active ou désactive chaque option pour lui-même.

[1.1.0]: #110--2026-11-04
[1.0.0]: #100
