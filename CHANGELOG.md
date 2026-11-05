# Changelog

Toutes les versions de SurviePlus. Format : [Keep a Changelog](https://keepachangelog.com/fr/1.1.0/),
numéros de version : [SemVer](https://semver.org/lang/fr/) (nouvelle fonction = 1.x.0, correctif = 1.x.y).

## [1.2.0] — 2026-11-05

### Ajouté
- **Filet de capture** : 8 ficelles autour d'une perle de l'Ender à l'établi (la recette apparaît dans le livre
  de recettes).
  - **Clic droit sur un animal ou un monstre** : il entre dans le filet. **Clic droit sur un bloc** : il ressort
    tel quel (nom, âge, vie, inventaire d'un âne, métier et échanges d'un villageois…). Sur un coffre ou une
    porte, il faut être accroupi.
  - Un mob relâché ne disparaît plus quand on s'éloigne, même un monstre.
  - Les **monstres** doivent d'abord être affaiblis (moins de 50 % de leur vie). Impossible de capturer les boss
    (Wither, Ender Dragon, Warden, gardien ancien), l'animal apprivoisé d'un autre joueur, un mob en laisse,
    un villageois en plein échange ou un mob sur lequel un joueur est assis.
  - Le filet se déchire après 10 captures (barre d'usure).
  - **Texture dessinée** (filet vide, et filet plein qui brille en vert) grâce au pack des menus. Sans le pack,
    le filet ressemble à une toile d'araignée.
  - Une page « Le filet » dans le livre guide.

### Modifié
- **Le pack des menus est hébergé sur GitHub** (Release `packs` du dépôt) au lieu d'O2switch :
  `pack.adresse: "https://github.com/Locktix/survieplus-plugin/releases/download/packs"`.
  `build.ps1 -Publier` le fabrique et l'envoie ; plus besoin de le mettre en ligne à la main.
- Au démarrage, la console prévient si le pack n'est pas en ligne à cette adresse.
  - Réglages dans `capture:` (`config.yml`), permission `survieplus.capture`.

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
- **Menus en images** (« carte des métiers ») grâce à un resource pack fourni par le serveur, en plus de celui de Muscu :
  - `/metiers` : une feuille de carte dans un cadre en chêne noir ; chaque métier est un chemin à l'encre,
    une croix rouge marque où l'on en est, un drapeau quand c'est fini ;
  - `/sp` : cadres à l'encre verte (option active), rouge (coupée) ou grise (bloquée) ;
  - l'inventaire du joueur est redessiné dans le même bois ;
  - les joueurs qui refusent le pack gardent les menus classiques. Réglages dans `pack:` (`config.yml`).
- **Livre guide** donné à la première connexion : sommaire cliquable, une page par métier avec ses bonus
  et ses cadeaux, page « Ta progression » quand on l'ouvre depuis `/sp`. `/sp guide` en redonne un.

### Modifié
- La **replantation auto** et la **récolte au clic droit** se débloquent au niveau 3 de Bûcheron et de
  Fermier. La **tombe** reste libre pour tous. Réglable dans `metiers.deblocage` (0 = libre pour tous).

### Corrigé
- Le `config.yml` livré rangeait l'XP des métiers sous `guide:` au lieu de `metiers:`. Sur un serveur
  sans métiers auparavant, le Mineur et le Chasseur n'auraient gagné aucune XP et le Pêcheur
  3 XP par prise. Corrigé avant toute mise en ligne.
- Les pourcentages s'affichent avec une virgule (« 12,5 % ») quelle que soit la langue du serveur.
- Une sauvegarde automatique de `metiers.yml` ne peut plus écraser celle faite à l'arrêt du serveur.

### Technique
- Tests automatiques (`src/test/java`), lancés par `build.ps1` avant de créer le jar.
- Le nom du jar suit la version de `paper-plugin.yml`.
- `build.ps1` copie aussi les sous-dossiers de `src/main/resources`.
- Maquettes des menus dans `maquettes/` (pages HTML).

### Mettre à jour depuis la 1.0.0
1. Faire une sauvegarde du monde.
2. Supprimer `SurviePlus-1.0.0.jar` et mettre `SurviePlus-1.1.0.jar` : deux versions du même plugin
   empêchent son chargement.
3. Retirer AuraSkills (ses niveaux ne sont pas repris : tout le monde repart de zéro en métiers).
4. Redémarrer. Le `config.yml` existant est complété tout seul ; les réglages déjà présents ne changent pas.
   Pour les menus en images (le port du pack n'est pas joignable chez exaroton) : mettre
   `pack.adresse: "https://minecraft.veldor.be"`, puis envoyer `plugins/SurviePlus/survieplus-menus.zip`
   sur ce site, renommé comme indiqué dans la console au démarrage (`survieplus-menus-<empreinte>.zip`).
5. Prévenir les joueurs : la replantation auto et la récolte au clic droit sont grisées jusqu'au
   niveau 3 de Bûcheron et de Fermier.

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
