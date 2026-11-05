package fr.survieplus;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Regles du filet de capture : usure. */
class CaptureTest {

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
