package fr.survieplus;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;

/** Les metiers : tous progressent en meme temps, chacun avec sa propre XP et deux bonus a debloquer. */
enum Metier {
    BUCHERON("bucheron", "Bûcheron", Material.IRON_AXE, NamedTextColor.GOLD, BossBar.Color.GREEN,
            "Couper des bûches", "Récolte double",
            "La hache s'use moins sur un arbre entier", "Pomme ou pousse en plus"),
    MINEUR("mineur", "Mineur", Material.IRON_PICKAXE, NamedTextColor.AQUA, BossBar.Color.BLUE,
            "Miner des minerais", "Récolte double",
            "La pioche s'use moins sur un filon", "Fer, or et cuivre fondus"),
    FERMIER("fermier", "Fermier", Material.IRON_HOE, NamedTextColor.YELLOW, BossBar.Color.YELLOW,
            "Récolter, élever des animaux", "Récolte double",
            "Récolte au clic droit en 3x3", "Les cultures proches poussent vite"),
    PECHEUR("pecheur", "Pêcheur", Material.FISHING_ROD, NamedTextColor.DARK_AQUA, BossBar.Color.WHITE,
            "Pêcher", "Prise double",
            "Le poisson mord plus vite", "Plus de trésors"),
    CHASSEUR("chasseur", "Chasseur", Material.IRON_SWORD, NamedTextColor.RED, BossBar.Color.RED,
            "Tuer des monstres", "Butin double",
            "Plus d'XP sur les monstres", "Tête du monstre");

    final String cle;
    final String nom;
    final Material icone;
    final NamedTextColor couleur;
    final BossBar.Color couleurBarre;
    final String gain;
    final String bonus;
    /** Bonus debloques aux paliers 1 et 2 (niveaux reglables dans config.yml). */
    final String[] paliers;

    Metier(String cle, String nom, Material icone, NamedTextColor couleur, BossBar.Color couleurBarre,
           String gain, String bonus, String palier1, String palier2) {
        this.cle = cle;
        this.nom = nom;
        this.icone = icone;
        this.couleur = couleur;
        this.couleurBarre = couleurBarre;
        this.gain = gain;
        this.bonus = bonus;
        this.paliers = new String[]{palier1, palier2};
    }

    static Metier parCle(String s) {
        for (Metier m : values()) if (m.cle.equalsIgnoreCase(s)) return m;
        return null;
    }
}
