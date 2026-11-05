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

    private static final int[] CASES_SP = {4, 8, 10, 11, 12, 13, 14, 15, 16, 26};
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
    void filet_texturesEtRepliToileDAraignee() throws IOException {
        JsonObject select = json("assets/minecraft/items/cobweb.json").getAsJsonObject("model");
        assertEquals("minecraft:select", select.get("type").getAsString());
        assertEquals("minecraft:custom_model_data", select.get("property").getAsString());
        // Une vraie toile d'araignee (sans texte) garde le modele du jeu
        assertEquals("minecraft:item/cobweb", select.getAsJsonObject("fallback").get("model").getAsString());
        Map<String, String> cas = new java.util.LinkedHashMap<>();
        for (var c : select.getAsJsonArray("cases"))
            cas.put(c.getAsJsonObject().get("when").getAsString(),
                    c.getAsJsonObject().getAsJsonObject("model").get("model").getAsString());
        // Ordre fixe (trie) : sinon le pack change d'un lancement de Java a l'autre et le jeu le refuse
        assertEquals(new java.util.ArrayList<>(new TreeMap<>(cas).keySet()), new java.util.ArrayList<>(cas.keySet()));
        assertEquals(Map.of(PackMenus.FILET_VIDE, "survieplus:item/filet_vide",
                PackMenus.FILET_PLEIN, "survieplus:item/filet_plein"), cas);
        for (String nom : List.of("filet_vide", "filet_plein")) {
            assertEquals("survieplus:item/" + nom, json("assets/survieplus/models/item/" + nom + ".json")
                    .getAsJsonObject("textures").get("layer0").getAsString());
            BufferedImage img = javax.imageio.ImageIO.read(new ByteArrayInputStream(
                    zip.get("assets/survieplus/textures/item/" + nom + ".png")));
            assertEquals(32, img.getWidth(), nom);
            assertEquals(32, img.getHeight(), nom);
        }
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

    @Test
    void metiers_lesDecorsNeTouchentPasLeTexte() {
        Largeurs l = dessins.pack().largeurs();
        for (String nom : List.of("Bûcheron", "Mineur", "Fermier", "Pêcheur", "Chasseur")) {
            int finGauche = 29 + l.de(nom) + 3 + l.de("niv. 49");
            assertTrue(finGauche + 2 <= DessinsMenus.DECOR_X0, nom + " : " + finGauche);
        }
        assertTrue(DessinsMenus.DECOR_X1 + 3 <= 166 - l.de("99 %") + 2);
    }

    @Test
    void metiers_leCheminVaDuNiveau0AuNiveauMax() {
        int total = DessinsMenus.CHEMIN_X1 - DessinsMenus.CHEMIN_X0, moitie = total / 2 / DessinsMenus.PAS * DessinsMenus.PAS;
        assertEquals(0, DessinsMenus.longueurChemin(new DessinsMenus.LigneMetier("a", 0, 50, 0, 50, false)));
        assertEquals(moitie, DessinsMenus.longueurChemin(new DessinsMenus.LigneMetier("a", 25, 50, 0, 50, false)));
        assertEquals(moitie, DessinsMenus.longueurChemin(new DessinsMenus.LigneMetier("a", 24, 50, 50, 50, false)));
        assertTrue(DessinsMenus.longueurChemin(new DessinsMenus.LigneMetier("a", 49, 50, 1_274, 1_275, false)) <= total);
        assertEquals(0, DessinsMenus.longueurChemin(new DessinsMenus.LigneMetier("a", 12, 50, 340, 350, false)) % DessinsMenus.PAS);
    }

    // ───────────── Apercu ─────────────

    private static final List<DessinsMenus.LigneMetier> EXEMPLE = List.of(
            new DessinsMenus.LigneMetier("Bûcheron", 12, 50, 340, 350, false),
            new DessinsMenus.LigneMetier("Mineur", 37, 50, 900, 1_000, false),
            new DessinsMenus.LigneMetier("Fermier", 50, 50, 0, 0, true),
            new DessinsMenus.LigneMetier("Pêcheur", 0, 50, 12, 50, false),
            new DessinsMenus.LigneMetier("Chasseur", 49, 50, 1_274, 1_275, false));

    @Test
    void apercu_metiers() throws IOException {
        Map<Integer, String> objets = new HashMap<>(Map.of(0, "iron_axe", 9, "iron_pickaxe", 18, "iron_hoe", 27, "fishing_rod",
                36, "iron_sword", 45, "arrow", 49, "nether_star", 53, "experience_bottle"));
        BufferedImage img = rendre(dessins.titreMetiers(148, EXEMPLE), 222, objets, 139, 197);
        enregistrer(img, "metiers.png");
        // Le chemin du chasseur (99 %) est trace bien plus loin que celui du pecheur (24 %)
        int x = (4 + 120) * E + 1;
        assertNotEquals(img.getRGB(x, (4 + DessinsMenus.ligneY(4) + 13 + DessinsMenus.vague(4, 120)) * E + 1),
                img.getRGB(x, (4 + DessinsMenus.ligneY(3) + 13 + DessinsMenus.vague(3, 120)) * E + 1));
    }

    @Test
    void apercu_sp() throws IOException {
        Map<Integer, DessinsMenus.Etat> etats = new HashMap<>();
        etats.put(10, DessinsMenus.Etat.ACTIF);
        etats.put(11, DessinsMenus.Etat.ACTIF);
        etats.put(12, DessinsMenus.Etat.COUPE);
        etats.put(13, DessinsMenus.Etat.ACTIF);
        etats.put(14, DessinsMenus.Etat.BLOQUE);
        etats.put(15, DessinsMenus.Etat.ACTIF);
        etats.put(16, DessinsMenus.Etat.COUPE);
        Map<Integer, String> objets = new HashMap<>(Map.of(4, "enchanted_book", 8, "written_book", 10, "iron_axe", 11, "oak_sapling",
                12, "oak_leaves", 13, "iron_pickaxe", 14, "wheat", 15, "chest", 16, "hopper"));
        objets.put(26, "red_bed");
        enregistrer(rendre(dessins.titreSp(etats), 168, objets, 85, 143), "sp.png");
    }

    // ───────────── Livre (guide) ─────────────

    @Test
    void livre_imagesDansLaLigne() {
        // le decor et le paysage tiennent dans la largeur d'une page, sans faire avancer le texte
        for (int n = 0; n < 5; n++) assertEquals(0, avanceNette(dessins.decorLivre(n)));
        assertEquals(0, avanceNette(dessins.paysageLivre()));
        assertTrue(zip.containsKey("assets/minecraft/textures/gui/book.png"));
    }

    /** Avance totale d'un morceau de la police des menus (espaces + images). */
    private static int avanceNette(Component c) {
        int x = 0;
        String s = ((TextComponent) c).content();
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            i += Character.charCount(cp);
            if (cp >= 0xF800 && cp <= 0xF809) x -= 1 << (cp - 0xF800);
            else if (cp >= 0xF820 && cp <= 0xF829) x += 1 << (cp - 0xF820);
            else {
                int xx = x;
                // retrouve l'avance du glyphe dans la police du pack
                for (JsonElement el : json("assets/survieplus/font/menu.json").getAsJsonArray("providers")) {
                    JsonObject p = el.getAsJsonObject();
                    if (!p.get("type").getAsString().equals("bitmap")) continue;
                    if (p.getAsJsonArray("chars").get(0).getAsString().codePointAt(0) != cp) continue;
                    try {
                        BufferedImage img = ImageIO.read(new ByteArrayInputStream(
                                zip.get("assets/survieplus/textures/" + p.get("file").getAsString().substring(11))));
                        // le jeu coupe la ligne des que le curseur depasse la largeur de la page
                        assertTrue(xx >= 0 && xx + PackMenus.avance(img, p.get("height").getAsInt()) < DessinsMenus.PAGE_LARGEUR,
                                "image hors de la page : " + xx);
                        x += PackMenus.avance(img, p.get("height").getAsInt());
                    } catch (IOException e) {
                        throw new IllegalStateException(e);
                    }
                }
            }
        }
        return x;
    }

    @Test
    void apercu_livre() throws IOException {
        Map<Integer, GlypheJeu> police = policeJeu();
        // page d'un metier
        BufferedImage page = pageLivre();
        int y = 30;
        ligneLivre(page, police, dessins.decorLivre(0), 36, y);
        ligneLivre(page, police, Component.text("   Bûcheron", TextColor.color(0xFFAA00)), 36, y);
        String[] lignes = {"", "XP : couper du bois", "", "Niv. 15", "Hache plus rapide", "", "Niv. 30",
                "Bûches en double", "", "Toi : niv. 12"};
        for (String l : lignes) ligneLivre(page, police, Component.text(l, NamedTextColor.BLACK), 36, y += 9);
        enregistrer(page, "livre-metier.png");
        // couverture
        BufferedImage couv = pageLivre();
        ligneLivre(couv, police, dessins.paysageLivre(), 36, 30 + 3 * 9);
        ligneLivre(couv, police, Component.text("     SURVIE+", TextColor.color(0x00AA00)), 36, 30 + 5 * 9);
        ligneLivre(couv, police, Component.text("    le guide du serveur", NamedTextColor.DARK_GRAY), 36, 30 + 6 * 9);
        enregistrer(couv, "livre-couverture.png");
    }

    /** Ecran du livre a 3 pixels par pixel : le fond du pack, dessine en 192 x 192 (sur 256 x 256). */
    private static BufferedImage pageLivre() throws IOException {
        BufferedImage livre = ImageIO.read(new ByteArrayInputStream(zip.get("assets/minecraft/textures/gui/book.png")));
        assertEquals(256 * E, livre.getWidth());
        BufferedImage out = new BufferedImage(192 * E, 192 * E, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setColor(new Color(0x3A3A3A));
        g.fillRect(0, 0, out.getWidth(), out.getHeight());
        g.drawImage(livre.getSubimage(0, 0, 192 * E, 192 * E), 0, 0, null);
        g.dispose();
        return out;
    }

    /** Une ligne de livre : texte dans la police du jeu, ou espaces et images de la police des menus. */
    private static void ligneLivre(BufferedImage out, Map<Integer, GlypheJeu> police, Component c, int x, int y) throws IOException {
        TextComponent t = (TextComponent) c;
        int couleur = (t.color() == null ? NamedTextColor.BLACK : t.color()).value();
        String s = t.content();
        boolean menu = PackMenus.POLICE.equals(t.font());
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            i += Character.charCount(cp);
            if (menu) {
                if (cp >= 0xF800 && cp <= 0xF809) {
                    x -= 1 << (cp - 0xF800);
                    continue;
                }
                if (cp >= 0xF820 && cp <= 0xF829) {
                    x += 1 << (cp - 0xF820);
                    continue;
                }
                for (JsonElement el : json("assets/survieplus/font/menu.json").getAsJsonArray("providers")) {
                    JsonObject p = el.getAsJsonObject();
                    if (!p.get("type").getAsString().equals("bitmap")
                            || p.getAsJsonArray("chars").get(0).getAsString().codePointAt(0) != cp) continue;
                    BufferedImage img = ImageIO.read(new ByteArrayInputStream(
                            zip.get("assets/survieplus/textures/" + p.get("file").getAsString().substring(11))));
                    int hauteur = p.get("height").getAsInt();
                    coller(out, img, 0, 0, img.getWidth(), img.getHeight(), x * E, (y + 7 - p.get("ascent").getAsInt()) * E,
                            0xFFFFFF, E / (img.getHeight() / hauteur));
                    x += PackMenus.avance(img, hauteur);
                }
            } else {
                GlypheJeu gj = police == null ? null : police.get(cp);
                if (gj == null) {
                    x += 6;
                    continue;
                }
                coller(out, gj.img(), gj.sx(), gj.sy(), gj.w(), gj.h(), x * E, (y + 7 - gj.ascent()) * E, couleur, E);
                x += gj.avance();
            }
        }
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

    /** Finesse de l'apercu : 3 pixels par pixel du menu (celle des fonds). */
    private static final int E = DessinsMenus.HD;

    /**
     * Refait ce que fait le jeu, a 3 pixels par pixel du menu : fond du coffre, titre (curseur qui avance, images a leur
     * hauteur et a leur echelle), puis objets.
     */
    private static BufferedImage rendre(Component titre, int h, Map<Integer, String> objets, int yInv, int yBarre) throws IOException {
        record Image(BufferedImage img, int ascent, int hauteur) {
            int finesse() {
                return img.getHeight() / hauteur;
            }

            int avance() {
                return PackMenus.avance(img, hauteur);
            }
        }
        Map<Integer, Image> images = new HashMap<>();
        Map<Integer, Integer> espaces = new HashMap<>();
        for (JsonElement el : json("assets/survieplus/font/menu.json").getAsJsonArray("providers")) {
            JsonObject p = el.getAsJsonObject();
            if (p.get("type").getAsString().equals("space")) {
                p.getAsJsonObject("advances").entrySet().forEach(e -> espaces.put(e.getKey().codePointAt(0), e.getValue().getAsInt()));
            } else {
                BufferedImage img = ImageIO.read(new ByteArrayInputStream(
                        zip.get("assets/survieplus/textures/" + p.get("file").getAsString().substring(11))));
                assertEquals(0, img.getHeight() % p.get("height").getAsInt(), p.toString());
                images.put(p.getAsJsonArray("chars").get(0).getAsString().codePointAt(0),
                        new Image(img, p.get("ascent").getAsInt(), p.get("height").getAsInt()));
            }
        }
        Map<Integer, GlypheJeu> police = policeJeu();

        int marge = 4;
        BufferedImage out = new BufferedImage((176 + 2 * marge) * E, (h + 2 * marge) * E, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        // Fond du coffre du jeu (approximatif)
        g.setColor(new Color(0xC6C6C6));
        g.fillRect(marge * E, marge * E, 176 * E, h * E);
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
                    assertEquals(0, E % im.finesse(), "finesse non geree par l'apercu");
                    coller(out, im.img(), 0, 0, im.img().getWidth(), im.img().getHeight(),
                            (marge + x) * E, (marge + PackMenus.TITRE_Y + 7 - im.ascent()) * E, couleur, E / im.finesse());
                    x += im.avance();
                }
            } else {
                int haut = Integer.parseInt(cle.value().substring(1));
                for (int i = 0; i < s.length(); ) {
                    int cp = s.codePointAt(i);
                    i += Character.charCount(cp);
                    GlypheJeu gj = police == null ? null : police.get(cp);
                    if (gj != null) {
                        coller(out, gj.img(), gj.sx(), gj.sy(), gj.w(), gj.h(), (marge + x) * E, (marge + haut + 7 - gj.ascent()) * E, couleur, E);
                        x += gj.avance();
                    } else {
                        g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 8 * E));
                        g.setColor(new Color(couleur));
                        g.drawString(new String(Character.toChars(cp)), (marge + x) * E, (marge + haut + 7) * E);
                        x += dessins.pack().largeurs().de(new String(Character.toChars(cp)));
                    }
                }
            }
        }
        // Objets, dessines apres le titre (comme en jeu)
        for (Map.Entry<Integer, String> e : objets.entrySet()) {
            int ox = (marge + DessinsMenus.caseX(e.getKey())) * E, oy = (marge + DessinsMenus.caseY(e.getKey())) * E;
            Path icone = MC.resolve(e.getValue() + ".png");
            if (Files.exists(icone)) {
                BufferedImage ic = ImageIO.read(icone.toFile());
                // le feuillage est gris dans le jeu, teinte au rendu
                coller(out, ic, 0, 0, 16, 16, ox, oy, e.getValue().endsWith("_leaves") ? 0x48B518 : 0xFFFFFF, E);
            } else {
                g.setColor(new Color(0x7A4FD0));
                g.fillRect(ox + 4 * E, oy + 4 * E, 8 * E, 8 * E);
            }
        }
        // Un objet dans l'inventaire et la barre d'action, pour voir les cases
        Path epee = MC.resolve("iron_sword.png");
        if (Files.exists(epee)) {
            BufferedImage ic = ImageIO.read(epee.toFile());
            coller(out, ic, 0, 0, 16, 16, (marge + 8) * E, (marge + yInv) * E, 0xFFFFFF, E);
            coller(out, ic, 0, 0, 16, 16, (marge + 8 + 18 * 3) * E, (marge + yBarre) * E, 0xFFFFFF, E);
        }
        g.dispose();
        return out;
    }

    /** Copie une partie d'image, chaque pixel en carre de "bloc" pixels, teintee (multiplication, comme le jeu). */
    private static void coller(BufferedImage out, BufferedImage img, int sx, int sy, int w, int h, int x0, int y0, int rgb, int bloc) {
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int p = img.getRGB(sx + x, sy + y);
                if ((p >>> 24) < 128) continue;
                int r = ((p >> 16) & 255) * ((rgb >> 16) & 255) / 255;
                int gg = ((p >> 8) & 255) * ((rgb >> 8) & 255) / 255;
                int b = (p & 255) * (rgb & 255) / 255;
                for (int j = 0; j < bloc; j++)
                    for (int i = 0; i < bloc; i++) {
                        int ox = x0 + x * bloc + i, oy = y0 + y * bloc + j;
                        if (ox >= 0 && oy >= 0 && ox < out.getWidth() && oy < out.getHeight())
                            out.setRGB(ox, oy, 0xFF000000 | (r << 16) | (gg << 8) | b);
                    }
            }
    }

    private static void enregistrer(BufferedImage img, String nom) throws IOException {
        Path dossier = Path.of("build", "apercu");
        Files.createDirectories(dossier);
        ImageIO.write(img, "png", dossier.resolve(nom).toFile());
    }
}
