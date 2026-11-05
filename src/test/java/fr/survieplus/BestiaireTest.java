package fr.survieplus;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Paliers du bestiaire. */
class BestiaireTest {

    @Test
    void seuils() {
        assertEquals(10, Bestiaire.seuil("10", 74));
        assertEquals(74, Bestiaire.seuil("tout", 74));
        assertEquals(74, Bestiaire.seuil(" Toutes ", 74));
        assertEquals(74, Bestiaire.seuil("100", 74), "au-dela du total : compte comme tout");
        assertEquals(-1, Bestiaire.seuil("0", 74));
        assertEquals(-1, Bestiaire.seuil("dix", 74));
    }

    @Test
    void paliersLivresLisibles() throws IOException {
        var c = YamlConfiguration.loadConfiguration(Files.newBufferedReader(
                Path.of("src/main/resources/config.yml"), StandardCharsets.UTF_8));
        var s = c.getConfigurationSection("bestiaire.paliers");
        assertNotNull(s);
        for (String k : s.getKeys(false)) {
            assertTrue(Bestiaire.seuil(k, 70) > 0, "palier illisible : " + k);
            assertFalse(s.getStringList(k).isEmpty(), "palier sans objet : " + k);
        }
    }
}
