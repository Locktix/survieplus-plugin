package fr.survieplus;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Le config.yml livre dans le jar contient bien tout ce que le code lit.
 * (Une section mal placee ne plante pas : le code prend sa valeur par defaut, parfois 0. C'est arrive avec
 * metiers.xp, range par erreur sous guide: : le mineur et le chasseur ne gagnaient plus rien.)
 */
class ConfigTest {

    private static final Path RACINE = Path.of("src", "main");
    /** getInt("metiers.niveau-max"...), getLong("metiers.xp.mineur." + nom...), etc. */
    private static final Pattern LECTURE = Pattern.compile(
            "\\.get(?:Boolean|Int|Long|Double|String|StringList|ConfigurationSection|MapList)\\(\\s*\"([a-z0-9.-]+)\"");

    private static YamlConfiguration config() throws IOException {
        return YamlConfiguration.loadConfiguration(
                Files.newBufferedReader(RACINE.resolve("resources/config.yml"), StandardCharsets.UTF_8));
    }

    @Test
    void chaqueCleLueParLeCodeExisteDansConfigYml() throws IOException {
        YamlConfiguration c = config();
        TreeSet<String> cles = new TreeSet<>();
        try (Stream<Path> fichiers = Files.walk(RACINE.resolve("java"))) {
            for (Path f : fichiers.filter(p -> p.toString().endsWith(".java")).toList()) {
                Matcher m = LECTURE.matcher(Files.readString(f, StandardCharsets.UTF_8));
                while (m.find()) cles.add(m.group(1));
            }
        }
        assertTrue(cles.size() > 30, "lecture des sources ratee : " + cles);

        List<String> manquantes = new ArrayList<>();
        for (String cle : cles) {
            // Sans point : lecture dans une sous-section ou dans un autre fichier (tombes.yml, metiers.yml)
            if (!cle.contains(".")) continue;
            boolean ok;
            if (cle.endsWith(".")) ok = c.isConfigurationSection(cle.substring(0, cle.length() - 1)); // "metiers.xp.mineur." + nom
            else if (cle.endsWith("-")) ok = c.getKeys(true).stream().anyMatch(k -> k.startsWith(cle)); // "metiers.bonus-niveau-" + palier
            else ok = c.contains(cle);
            if (!ok) manquantes.add(cle);
        }
        assertEquals(List.of(), manquantes, "absentes de config.yml (ou mal placees)");
    }

    @Test
    void modulesEtMetiersOntLeursReglages() throws IOException {
        YamlConfiguration c = config();
        for (Module m : Module.values()) {
            assertTrue(c.contains(m.cheminConfig), m.cheminConfig);
            if (m.metierRequis() != null) assertTrue(c.contains("metiers.deblocage." + m.cle), "deblocage de " + m.cle);
        }
        for (Metier m : Metier.values()) {
            assertTrue(c.isConfigurationSection("metiers.recompenses." + m.cle), "recompenses de " + m.cle);
            String xp = "metiers.xp." + m.cle;
            assertTrue(c.contains(xp), xp);
        }
        // Les valeurs importantes pour la progression
        assertEquals(20, c.getLong("metiers.xp.mineur.DIAMOND_ORE"));
        assertEquals(5, c.getLong("metiers.xp.chasseur.defaut"));
        assertEquals(8, c.getLong("metiers.xp.pecheur.poisson"));
        // Le guide n'a que ses propres reglages
        assertEquals(java.util.Set.of("premiere-connexion"), c.getConfigurationSection("guide").getKeys(false));
    }

    @Test
    void anciensDefautsPassentAuNouveauBaremeSaufSiModifies() throws IOException {
        YamlConfiguration defauts = config();
        // Chaque ancien defaut correspond bien a un reglage du config.yml, dont la valeur a change
        for (var e : MajConfig.ANCIENS_DEFAUTS.entrySet()) {
            assertTrue(defauts.contains(e.getKey()), e.getKey());
            assertNotEquals(e.getValue() instanceof java.util.List<?> ? e.getValue() : String.valueOf(e.getValue()),
                    e.getValue() instanceof java.util.List<?> ? defauts.getStringList(e.getKey()) : defauts.getString(e.getKey()), e.getKey());
        }

        YamlConfiguration prod = new YamlConfiguration();
        prod.set("metiers.xp.mineur.COAL_ORE", 3);     // ancien defaut -> mis a jour
        prod.set("metiers.xp.mineur.DIAMOND_ORE", 50); // change par l'admin -> garde
        prod.set("metiers.recompenses.mineur.10", java.util.List.of("IRON_PICKAXE efficiency:2 unbreaking:1"));
        prod.set("metiers.recompenses.mineur.25", java.util.List.of("DIAMOND 64")); // change -> garde
        prod.setDefaults(defauts);
        assertEquals(2, MajConfig.appliquer(prod));
        assertEquals(4, prod.getLong("metiers.xp.mineur.COAL_ORE"));
        assertEquals(50, prod.getLong("metiers.xp.mineur.DIAMOND_ORE"));
        assertEquals(java.util.List.of("IRON_INGOT 16", "TORCH 64"), prod.getStringList("metiers.recompenses.mineur.10"));
        assertEquals(java.util.List.of("DIAMOND 64"), prod.getStringList("metiers.recompenses.mineur.25"));
        assertFalse(prod.isSet("metiers.xp.mineur.IRON_ORE"), "une cle absente n'est pas ecrite");
        assertEquals(0, MajConfig.appliquer(prod), "deuxieme passage : rien a faire");
    }
}
