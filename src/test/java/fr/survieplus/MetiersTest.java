package fr.survieplus;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Calculs des metiers (sans serveur) : courbe d'XP, chance de double, rangs, affichage. */
class MetiersTest {

    // Valeurs par defaut de config.yml
    private static Metiers.Progression progression(long xp) {
        return Metiers.progression(xp, 50, 25, 50);
    }

    @Test
    void progression_suitLaCourbeDeConfigYml() {
        assertEquals(new Metiers.Progression(0, 0, 50), progression(0));
        assertEquals(new Metiers.Progression(0, 49, 50), progression(49));
        assertEquals(new Metiers.Progression(1, 0, 75), progression(50));
        assertEquals(new Metiers.Progression(2, 0, 100), progression(50 + 75));
    }

    @Test
    void progression_niveauMax() {
        // somme de (50 + 25 n) pour n = 0..49 ; le dernier niveau coute 1275 XP (cf. commentaire de config.yml)
        long total = 0;
        for (int n = 0; n < 50; n++) total += 50 + 25L * n;
        assertEquals(33_125, total);
        assertEquals(new Metiers.Progression(49, 1274, 1275), progression(total - 1));
        Metiers.Progression max = progression(total);
        assertEquals(50, max.niveau());
        assertTrue(max.max());
        assertTrue(progression(Long.MAX_VALUE / 2).max());
    }

    @Test
    void progression_configAZeroNeBouclePasALInfini() {
        // xp-base et xp-par-niveau a 0 : chaque niveau coute quand meme 1 XP
        assertEquals(5, Metiers.progression(5, 0, 0, 50).niveau());
        assertEquals(3, Metiers.progression(1000, 0, 0, 3).niveau());
    }

    @Test
    void chanceDouble_plafonneeEtJamaisNegative() {
        assertEquals(0.0, Metiers.chanceDouble(0, 0.5, 25));
        assertEquals(0.05, Metiers.chanceDouble(10, 0.5, 25), 1e-9);
        assertEquals(0.25, Metiers.chanceDouble(50, 0.5, 25), 1e-9);
        assertEquals(0.25, Metiers.chanceDouble(500, 0.5, 25), 1e-9);
        assertEquals(0.0, Metiers.chanceDouble(10, -3, 25));
    }

    @Test
    void rang_tousLesDixNiveaux() {
        assertNull(Metiers.rang(0));
        assertNull(Metiers.rang(9));
        assertEquals("Apprenti", Metiers.rang(10));
        assertEquals("Compagnon", Metiers.rang(25));
        assertEquals("Maître", Metiers.rang(49));
        assertEquals("Grand maître", Metiers.rang(50));
        assertEquals("Grand maître", Metiers.rang(1000));
    }

    @Test
    void pourcent_virguleFrancaiseQuelleQueSoitLaLocaleDuServeur() {
        java.util.Locale avant = java.util.Locale.getDefault();
        try {
            java.util.Locale.setDefault(java.util.Locale.US);
            assertEquals("5 %", Metiers.pourcent(0.05));
            assertEquals("12,5 %", Metiers.pourcent(0.125));
            assertEquals("0 %", Metiers.pourcent(0));
        } finally {
            java.util.Locale.setDefault(avant);
        }
    }

    @Test
    void metier_parCle() {
        assertEquals(Metier.MINEUR, Metier.parCle("MINEUR"));
        assertEquals(Metier.PECHEUR, Metier.parCle("pecheur"));
        assertNull(Metier.parCle("pêcheur"));
        assertNull(Metier.parCle(""));
    }

    @Test
    void metier_clesUniques() {
        assertEquals(Metier.values().length, Arrays.stream(Metier.values()).map(m -> m.cle).distinct().count());
    }

    // ------------------------------------------------------------ bucheron / mineur

    @Test
    void pousse_chaqueTroncNaturelASaPousse() {
        // Si Mojang ajoute une essence, ce test rappelle de verifier la replantation
        for (Material m : Material.values()) {
            String nom = m.name();
            if (nom.startsWith("LEGACY_") || nom.startsWith("STRIPPED_") || !nom.endsWith("_LOG")) continue;
            assertNotNull(Bucheron.pousse(m), "pas de pousse pour " + nom);
        }
        assertEquals(Material.OAK_SAPLING, Bucheron.pousse(Material.OAK_LOG));
        assertEquals(Material.MANGROVE_PROPAGULE, Bucheron.pousse(Material.MANGROVE_LOG));
        assertEquals(Material.CRIMSON_FUNGUS, Bucheron.pousse(Material.CRIMSON_STEM));
        assertNull(Bucheron.pousse(Material.STRIPPED_OAK_LOG));
        assertNull(Bucheron.pousse(Material.OAK_WOOD));
    }

    @Test
    void famille_deepslateAvecLaVersionNormale() {
        Set<String> aucun = Set.of();
        assertEquals("IRON_ORE", Mineur.famille(Material.IRON_ORE, aucun));
        assertEquals("IRON_ORE", Mineur.famille(Material.DEEPSLATE_IRON_ORE, aucun));
        assertEquals("ANCIENT_DEBRIS", Mineur.famille(Material.ANCIENT_DEBRIS, aucun));
        assertNull(Mineur.famille(Material.STONE, aucun));
        assertNull(Mineur.famille(Material.GLOWSTONE, aucun));
        assertEquals("GLOWSTONE", Mineur.famille(Material.GLOWSTONE, Set.of("GLOWSTONE")));
    }
}
