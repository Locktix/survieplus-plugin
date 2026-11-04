package fr.survieplus;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/** La version de paper-plugin.yml est la derniere du CHANGELOG (on n'oublie pas l'un des deux). */
class VersionTest {

    @Test
    void versionDuPluginEnTeteDuChangelog() throws IOException {
        String version = YamlConfiguration.loadConfiguration(Files.newBufferedReader(
                Path.of("src/main/resources/paper-plugin.yml"), StandardCharsets.UTF_8)).getString("version");
        assertNotNull(version);
        assertTrue(version.matches("\\d+\\.\\d+\\.\\d+"), "version au format 1.2.3 : " + version);

        Matcher m = Pattern.compile("(?m)^## \\[([^]]+)]").matcher(Files.readString(Path.of("CHANGELOG.md"), StandardCharsets.UTF_8));
        assertTrue(m.find(), "aucune version dans CHANGELOG.md");
        assertEquals(version, m.group(1), "ajoute une section ## [" + version + "] en haut de CHANGELOG.md");
    }
}
