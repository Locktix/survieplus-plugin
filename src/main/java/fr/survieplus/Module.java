package fr.survieplus;

import org.bukkit.Material;

/** Les fonctionnalites que chaque joueur peut activer / desactiver dans le menu. */
enum Module {
    BUCHERON("bucheron", "bucheron.active", "survieplus.bucheron", Material.IRON_AXE,
            "Abattage d'arbre", "Accroupi + hache : abat tout l'arbre"),
    REPLANTER("replanter", "bucheron.replanter", "survieplus.bucheron", Material.OAK_SAPLING,
            "Replantation auto", "Replante une pousse apres l'abattage"),
    FEUILLES("feuilles", "bucheron.feuilles", "survieplus.bucheron", Material.OAK_LEAVES,
            "Chute des feuilles", "Les feuilles de l'arbre abattu tombent aussitot"),
    MINEUR("mineur", "mineur.active", "survieplus.mineur", Material.IRON_PICKAXE,
            "Minage de filon", "Accroupi + pioche : mine tout le filon"),
    RECOLTE("recolte", "recolte.active", "survieplus.recolte", Material.WHEAT,
            "Recolte au clic droit", "Clic droit sur une culture mure : recolte et replante"),
    TOMBE("tombe", "tombe.active", "survieplus.tombe", Material.CHEST,
            "Tombe", "A ta mort, tes affaires et ton XP vont dans un coffre"),
    TRI("tri", "tri.active", "survieplus.tri", Material.HOPPER,
            "Tri d'inventaire", "Maj + clic droit sur une case vide : tout se range"),
    BARRE_METIERS("barre-metiers", "metiers.active", Metiers.PERMISSION, Material.EXPERIENCE_BOTTLE,
            "Barre de progression", "Affiche en haut de l'écran l'XP de métier gagnée");

    final String cle;
    final String cheminConfig;
    final String permission;
    final Material icone;
    final String nom;
    final String description;

    Module(String cle, String cheminConfig, String permission, Material icone, String nom, String description) {
        this.cle = cle;
        this.cheminConfig = cheminConfig;
        this.permission = permission;
        this.icone = icone;
        this.nom = nom;
        this.description = description;
    }

    /**
     * Metier dont il faut un certain niveau pour debloquer ce module (niveau dans metiers.deblocage.<cle>),
     * ou null si le module est libre.
     */
    Metier metierRequis() {
        return switch (this) {
            case REPLANTER -> Metier.BUCHERON;
            case RECOLTE -> Metier.FERMIER;
            case TOMBE -> Metier.CHASSEUR;
            default -> null;
        };
    }
}
