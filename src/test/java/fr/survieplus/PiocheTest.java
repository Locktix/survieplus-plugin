package fr.survieplus;

import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Regles de la pioche du mineur (niveau = niveau de Mineur). */
class PiocheTest {

    @Test
    void materiauSelonLeNiveau() {
        assertEquals(Material.STONE_PICKAXE, Pioche.materiau(0));
        assertEquals(Material.STONE_PICKAXE, Pioche.materiau(9));
        assertEquals(Material.IRON_PICKAXE, Pioche.materiau(10));
        assertEquals(Material.DIAMOND_PICKAXE, Pioche.materiau(25));
        assertEquals(Material.NETHERITE_PICKAXE, Pioche.materiau(40));
        assertEquals(Material.NETHERITE_PICKAXE, Pioche.materiau(50));
    }

    @Test
    void fortuneSelonLeNiveau() {
        assertEquals(0, Pioche.fortune(4));
        assertEquals(1, Pioche.fortune(5));
        assertEquals(2, Pioche.fortune(20));
        assertEquals(3, Pioche.fortune(40));
    }

    @Test
    void apparenceExisteDansLePackEtCorrespondAuMateriau() {
        for (int n = 0; n <= 50; n++) {
            String a = Pioche.apparence(n);
            assertTrue(fr.survieplus.pack.PackMenus.PIOCHES.containsKey(a), a);
            assertEquals(Pioche.materiau(n).getKey().getKey(), fr.survieplus.pack.PackMenus.PIOCHES.get(a), "niveau " + n);
        }
        assertEquals("survieplus:pioche_maitre", Pioche.apparence(50));
    }

    @Test
    void vitesseMonteAChaqueNiveauJusquAuMax() {
        assertEquals(0, Pioche.vitesse(0, 50, 26));
        double avant = 0;
        for (int n = 1; n <= 50; n++) {
            double v = Pioche.vitesse(n, 50, 26);
            assertTrue(v > avant, "niveau " + n + " : " + v);
            avant = v;
        }
        assertEquals(26, Pioche.vitesse(50, 50, 26));
        assertEquals(26, Pioche.vitesse(60, 50, 26), "plafonnee");
    }

    @Test
    void visionSousTerreSeulement() {
        assertTrue(Pioche.sousTerre(-40, 62, 0), "grotte profonde");
        assertTrue(Pioche.sousTerre(40, 62, 3), "grotte au-dessus de Y = 0");
        assertTrue(Pioche.sousTerre(55, 62, 14), "pres d'une entree de grotte");
        assertFalse(Pioche.sousTerre(40, 62, 15), "ravin a ciel ouvert");
        assertFalse(Pioche.sousTerre(70, 62, 0), "maison en surface");
    }

    @Test
    void bonusXp() {
        assertEquals(115, Pioche.avecBonus(100, 15, 0.99));
        // 4 XP + 15 % = 4,6 : +1 avec 60 % de chance
        assertEquals(5, Pioche.avecBonus(4, 15, 0.59));
        assertEquals(4, Pioche.avecBonus(4, 15, 0.61));
        assertEquals(4, Pioche.avecBonus(4, 0, 0));
    }

    @Test
    void troisSurTroisDansLePlanDeLaFace() {
        for (BlockFace face : List.of(BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST)) {
            List<int[]> d = Pioche.decalages3x3(face);
            assertEquals(8, d.size());
            Set<String> vus = new HashSet<>();
            for (int[] v : d) {
                // aucun decalage dans l'axe de la face
                assertEquals(0, v[0] * Math.abs(face.getModX()) + v[1] * Math.abs(face.getModY()) + v[2] * Math.abs(face.getModZ()));
                assertTrue(vus.add(v[0] + "," + v[1] + "," + v[2]));
            }
        }
    }
}
