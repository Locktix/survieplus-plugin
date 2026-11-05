package fr.survieplus;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Regles du filet de capture : vie des monstres et usure. */
class CaptureTest {

    @Test
    void unMonstreDoitEtreAffaibli() {
        // Zombie : 20 points de vie, seuil 50 %
        assertFalse(Capture.assezAffaibli(20, 20, 50));
        assertFalse(Capture.assezAffaibli(10.5, 20, 50));
        assertTrue(Capture.assezAffaibli(10, 20, 50), "pile 50 % : capturable");
        assertTrue(Capture.assezAffaibli(1, 20, 50));
    }

    @Test
    void seuilA100CaptureToujours() {
        assertTrue(Capture.assezAffaibli(20, 20, 100));
        assertTrue(Capture.assezAffaibli(5, 0, 50), "vie max inconnue : pas de blocage");
    }

    @Test
    void unFiletDeDixCapturesSertDixFois() {
        int max = 10, usure = 0, captures = 0;
        while (true) {
            int apres = Capture.usureApresCapture(usure, max);
            if (apres < 0) break;
            usure = apres;
            captures++;
            if (Capture.casseAuLacher(usure, max)) break;
        }
        assertEquals(10, captures);
        assertTrue(Capture.casseAuLacher(usure, max), "la 10e capture dechire le filet au lacher");
        assertFalse(Capture.casseAuLacher(9, max));
    }

    @Test
    void filetInusable() {
        assertEquals(0, Capture.usureApresCapture(0, 0));
        assertFalse(Capture.casseAuLacher(0, 0));
    }
}
