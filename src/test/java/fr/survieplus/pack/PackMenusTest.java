package fr.survieplus.pack;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Le pack des menus est valide pour le client, toujours identique, et un apercu des menus est
 * dessine dans build/apercu/ en refaisant le placement du jeu (images de la police + espaces + texte).
 * Si les fichiers extraits du jeu pour les maquettes sont la (maquettes/mc, uniquement sur le PC de dev),
 * l'apercu utilise la vraie police et les vraies icones ; sinon une approximation.
 */
class PackMenusTest {

    private static final int[] CASES_SP = {4, 8, 10, 11, 12, 13, 14, 15, 16, 22};
    private static final Path MC = Path.of("maquettes", "mc");
    private static DessinsMenus dessins;
    private static Map<String, byte[]> zip;

    @BeforeAll
    static void fabriquer() throws IOException {
        dessins = DessinsMenus.fabriquer(CASES_SP);
        zip = new TreeMap<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(dessins.pack().zip()))) {
            for (ZipEntry e; (e = in.getNextEntry()) != null; ) zip.put(e.getName(), in.readAllBytes());
        }
    }

    private static JsonObject json(String chemin) {
        return JsonParser.parseString(new String(zip.get(chemin), StandardCharsets.UTF_8)).getAsJsonObject();
    }

    @Test
    void pack_contientLeNecessaire() {
        assertTrue(zip.containsKey("pack.mcmeta"));
        assertTrue(zip.containsKey("pack.png"));
        assertTrue(zip.containsKey("assets/survieplus/font/menu.json"));
        assertEquals("minecraft:empty", json("assets/survieplus/items/vide.json")
                .getAsJsonObject("model").get("type").getAsString());
    }

    @Test
    void polices_valides() {
        // Le client refuse toute la police si une image a un "ascent" plus grand que sa hauteur
        for (String chemin : zip.keySet()) {
            if (!chemin.startsWith("assets/survieplus/font/")) continue;
            for (JsonElement el : json(chemin).getAsJsonArray("providers")) {
                JsonObject p = el.getAsJsonObject();
                if (!p.get("type").getAsString().equals("bitmap")) continue;
                int height = p.has("height") ? p.get("height").getAsInt() : 8;
                assertTrue(p.get("ascent").getAsInt() <= height, chemin + " : " + p);
                String fichier = p.get("file").getAsString();
                if (fichier.startsWith("survieplus:"))
                    assertTrue(zip.containsKey("assets/survieplus/textures/" + fichier.substring(11)), fichier);
            }
        }
    }

    @Test
    void pack_identiqueDUnDemarrageALAutre() throws IOException {
        assertEquals(dessins.pack().sha1(), DessinsMenus.fabriquer(CASES_SP).pack().sha1());
    }

    @Test
    void metiers_lesTextesNeSeChevauchentPas() {
        Largeurs l = dessins.pack().largeurs();
        // Pire cas : niveau a 2 chiffres, a gauche ; pourcentage a droite
        for (String nom : List.of("Bûcheron", "Mineur", "Fermier", "Pêcheur", "Chasseur")) {
            int finGauche = 29 + l.de(nom) + 3 + l.de("niv. 49");
            int debutDroite = 166 - l.de(DessinsMenus.pourcent(1274, 1275)) + 2;
            assertTrue(finGauche + 3 <= debutDroite, nom + " : " + finGauche + " / " + debutDroite);
        }
        assertEquals("99 %", DessinsMenus.pourcent(1274, 1275));
        assertEquals("0 %", DessinsMenus.pourcent(0, 50));
        // Titre et total ne se touchent pas, et la rose des vents (x >= 157) reste libre
        assertTrue(9 + l.de("Carte des métiers") + 6 <= 152 - l.de("total 250") + 2);
        // L'etiquette de papier sous « Inventaire » est assez large
        assertTrue(8 + l.de("Inventaire") <= 4 + 58 - 2);
    }

    // ───────────── Apercu ─────────────

    private static final List<DessinsMenus.LigneMetier> EXEMPLE = List.of(
            new DessinsMenus.LigneMetier("Bûcheron", 12, 340, 350, false),
            new DessinsMenus.LigneMetier("Mineur", 37, 900, 1_000, false),
            new DessinsMenus.LigneMetier("Fermier", 50, 0, 0, true),
            new DessinsMenus.LigneMetier("Pêcheur", 0, 12, 50, false),
            new DessinsMenus.LigneMetier("Chasseur", 49, 1_274, 1_275, false));

    @Test
    void apercu_metiers() throws IOException {
        Map<Integer, String> objets = new HashMap<>(Map.of(0, "iron_axe", 9, "iron_pickaxe", 18, "iron_hoe", 27, "fishing_rod",
                36, "iron_sword", 45, "arrow", 49, "nether_star", 53, "experience_bottle"));
        BufferedImage img = rendre(dessins.titreMetiers(148, EXEMPLE), 222, objets, 139, 197);
        enregistrer(img, "metiers.png");
        // Le chemin du chasseur (99 %) est trace bien plus loin que celui du pecheur (24 %)
        int x = 4 + 120;
        assertNotEquals(img.getRGB(x, 4 + DessinsMenus.ligneY(4) + 13 + DessinsMenus.vague(4, 120)),
                img.getRGB(x, 4 + DessinsMenus.ligneY(3) + 13 + DessinsMenus.vague(3, 120)));
    }

    @Test
    void apercu_sp() throws IOException {
        Map<Integer, TextColor> cadres = new HashMap<>();
        cadres.put(10, DessinsMenus.CADRE_ON);
        cadres.put(11, DessinsMenus.CADRE_ON);
        cadres.put(12, DessinsMenus.CADRE_OFF);
        cadres.put(13, DessinsMenus.CADRE_ON);
        cadres.put(14, DessinsMenus.CADRE_BLOQUE);
        cadres.put(15, DessinsMenus.CADRE_ON);
        cadres.put(16, DessinsMenus.CADRE_OFF);
        Map<Integer, String> objets = new HashMap<>(Map.of(4, "nether_star", 8, "filled_map", 10, "iron_axe", 11, "iron_hoe",
                12, "arrow", 13, "iron_pickaxe", 14, "fishing_rod", 15, "iron_sword", 16, "experience_bottle"));
        objets.put(22, "arrow");
        enregistrer(rendre(dessins.titreSp(cadres), 168, objets, 85, 143), "sp.png");
    }

    // ───────────── Rendu ─────────────

    /** Glyphe de la vraie police du jeu (si disponible) : fichier, position dans l'image, ascent, avance. */
    private record GlypheJeu(BufferedImage img, int sx, int sy, int w, int h, int ascent, int avance) {}

    private static Map<Integer, GlypheJeu> policeJeu() throws IOException {
        Path js = MC.resolve("police.js");
        if (!Files.exists(js)) return null;
        String s = Files.readString(js, StandardCharsets.UTF_8);
        JsonObject o = JsonParser.parseString(s.substring(s.indexOf('{'), s.lastIndexOf('}') + 1)).getAsJsonObject();
        Map<String, BufferedImage> images = new HashMap<>();
        Map<Integer, GlypheJeu> g = new HashMap<>();
        for (Map.Entry<String, JsonElement> e : o.entrySet()) {
            JsonArray a = e.getValue().getAsJsonArray();
            BufferedImage img = images.computeIfAbsent(a.get(0).getAsString(), f -> {
                try {
                    return ImageIO.read(MC.resolve(f).toFile());
                } catch (IOException ex) {
                    throw new IllegalStateException(ex);
                }
            });
            g.put(e.getKey().codePointAt(0), new GlypheJeu(img, a.get(1).getAsInt(), a.get(2).getAsInt(), a.get(3).getAsInt(),
                    a.get(4).getAsInt(), a.get(5).getAsInt(), a.get(7).getAsInt()));
        }
        return g;
    }

    /** Refait ce que fait le jeu : fond du coffre, titre (curseur qui avance, images a leur hauteur), puis objets. */
    private static BufferedImage rendre(Component titre, int h, Map<Integer, String> objets, int yInv, int yBarre) throws IOException {
        record Image(BufferedImage img, int ascent) {}
        Map<Integer, Image> images = new HashMap<>();
        Map<Integer, Integer> espaces = new HashMap<>();
        for (JsonElement el : json("assets/survieplus/font/menu.json").getAsJsonArray("providers")) {
            JsonObject p = el.getAsJsonObject();
            if (p.get("type").getAsString().equals("space")) {
                p.getAsJsonObject("advances").entrySet().forEach(e -> espaces.put(e.getKey().codePointAt(0), e.getValue().getAsInt()));
            } else {
                BufferedImage img = ImageIO.read(new ByteArrayInputStream(
                        zip.get("assets/survieplus/textures/" + p.get("file").getAsString().substring(11))));
                images.put(p.getAsJsonArray("chars").get(0).getAsString().codePointAt(0), new Image(img, p.get("ascent").getAsInt()));
            }
        }
        Map<Integer, GlypheJeu> police = policeJeu();

        int marge = 4;
        BufferedImage out = new BufferedImage(176 + 2 * marge, h + 2 * marge, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        // Fond du coffre du jeu (approximatif)
        g.setColor(new Color(0xC6C6C6));
        g.fillRect(marge, marge, 176, h);
        int x = PackMenus.TITRE_X;
        for (Component morceau : titre.children()) {
            TextComponent t = (TextComponent) morceau;
            Key cle = t.font();
            int couleur = (t.color() == null ? NamedTextColor.WHITE : t.color()).value();
            String s = t.content();
            if (cle != null && cle.equals(PackMenus.POLICE)) {
                for (int i = 0; i < s.length(); ) {
                    int cp = s.codePointAt(i);
                    i += Character.charCount(cp);
                    if (espaces.containsKey(cp)) {
                        x += espaces.get(cp);
                        continue;
                    }
                    Image im = images.get(cp);
                    assertNotNull(im, "caractere inconnu " + Integer.toHexString(cp));
                    coller(out, im.img(), 0, 0, im.img().getWidth(), im.img().getHeight(),
                            marge + x, marge + PackMenus.TITRE_Y + 7 - im.ascent(), couleur);
                    x += avance(im.img());
                }
            } else {
                int haut = Integer.parseInt(cle.value().substring(1));
                for (int i = 0; i < s.length(); ) {
                    int cp = s.codePointAt(i);
                    i += Character.charCount(cp);
                    GlypheJeu gj = police == null ? null : police.get(cp);
                    if (gj != null) {
                        coller(out, gj.img(), gj.sx(), gj.sy(), gj.w(), gj.h(), marge + x, marge + haut + 7 - gj.ascent(), couleur);
                        x += gj.avance();
                    } else {
                        g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 8));
                        g.setColor(new Color(couleur));
                        g.drawString(new String(Character.toChars(cp)), marge + x, marge + haut + 7);
                        x += dessins.pack().largeurs().de(new String(Character.toChars(cp)));
                    }
                }
            }
        }
        // Objets, dessines apres le titre (comme en jeu)
        for (Map.Entry<Integer, String> e : objets.entrySet()) {
            int ox = marge + DessinsMenus.caseX(e.getKey()), oy = marge + DessinsMenus.caseY(e.getKey());
            Path icone = MC.resolve(e.getValue() + ".png");
            if (Files.exists(icone)) g.drawImage(ImageIO.read(icone.toFile()), ox, oy, null);
            else {
                g.setColor(new Color(0x7A4FD0));
                g.fillRect(ox + 4, oy + 4, 8, 8);
            }
        }
        // Un objet dans l'inventaire et la barre d'action, pour voir les cases
        Path pain = MC.resolve("iron_sword.png");
        if (Files.exists(pain)) {
            g.drawImage(ImageIO.read(pain.toFile()), marge + 8, marge + yInv, null);
            g.drawImage(ImageIO.read(pain.toFile()), marge + 8 + 18 * 3, marge + yBarre, null);
        }
        g.dispose();
        return out;
    }

    private static int avance(BufferedImage img) {
        for (int x = img.getWidth() - 1; x >= 0; x--)
            for (int y = 0; y < img.getHeight(); y++)
                if ((img.getRGB(x, y) >>> 24) != 0) return x + 2;
        return 1;
    }

    /** Copie une partie d'image, teintee (multiplication de couleur, comme le jeu). */
    private static void coller(BufferedImage out, BufferedImage img, int sx, int sy, int w, int h, int x0, int y0, int rgb) {
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int p = img.getRGB(sx + x, sy + y);
                if ((p >>> 24) < 128 || x0 + x < 0 || y0 + y < 0 || x0 + x >= out.getWidth() || y0 + y >= out.getHeight()) continue;
                int r = ((p >> 16) & 255) * ((rgb >> 16) & 255) / 255;
                int gg = ((p >> 8) & 255) * ((rgb >> 8) & 255) / 255;
                int b = (p & 255) * (rgb & 255) / 255;
                out.setRGB(x0 + x, y0 + y, 0xFF000000 | (r << 16) | (gg << 8) | b);
            }
    }

    private static void enregistrer(BufferedImage img, String nom) throws IOException {
        Path dossier = Path.of("build", "apercu");
        Files.createDirectories(dossier);
        BufferedImage grand = new BufferedImage(img.getWidth() * 3, img.getHeight() * 3, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < grand.getHeight(); y++)
            for (int x = 0; x < grand.getWidth(); x++) grand.setRGB(x, y, img.getRGB(x / 3, y / 3));
        ImageIO.write(grand, "png", dossier.resolve(nom).toFile());
    }
}
