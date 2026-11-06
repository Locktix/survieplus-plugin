package fr.survieplus.pack;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.kyori.adventure.key.Key;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Le resource pack des menus, fabrique en memoire au demarrage (meme principe que le HUD de Muscu) :
 * <ul>
 *   <li>la police {@code survieplus:menu} : une image par caractere (zone privee Unicode), placee a une hauteur
 *       precise, plus des espaces positifs / negatifs pour placer chaque element au pixel pres ;</li>
 *   <li>des polices {@code survieplus:t<y>} : la police normale du jeu, decalee vers le bas ;</li>
 *   <li>le modele d'objet {@code survieplus:vide} (invisible) pour les zones survolables des menus.</li>
 * </ul>
 * Tout est place dans le titre du menu : les coordonnees sont en pixels depuis le coin haut gauche du menu
 * (le titre commence en x = 8, y = 6). Le zip est identique d'un demarrage a l'autre (meme empreinte).
 */
public final class PackMenus {

    public static final Key POLICE = Key.key("survieplus", "menu");
    public static final Key VIDE = Key.key("survieplus", "vide");
    /**
     * Objets dessines par le pack : l'objet garde le modele d'un objet du jeu (vu par ceux qui n'ont pas le pack)
     * et porte ce texte dans custom_model_data ; le pack remplace alors son modele.
     */
    public static final String FILET_VIDE = "survieplus:filet_vide", FILET_PLEIN = "survieplus:filet_plein";
    /** Objet du jeu dont le filet emprunte le modele. */
    public static final Key MODELE_FILET = Key.key("minecraft", "cobweb");
    /** Medaille du naturaliste (bestiaire complet) : modele de pepite d'or sans le pack. */
    public static final String MEDAILLE = "survieplus:medaille";
    public static final Key MODELE_MEDAILLE = Key.key("minecraft", "gold_nugget");
    /**
     * Apparences de la pioche du mineur (custom_model_data -> objet du jeu qui la porte). Sans le pack,
     * on voit la pioche du jeu correspondante.
     */
    public static final Map<String, String> PIOCHES = Map.of(
            "survieplus:pioche_pierre", "stone_pickaxe",
            "survieplus:pioche_fer", "iron_pickaxe",
            "survieplus:pioche_diamant", "diamond_pickaxe",
            "survieplus:pioche_netherite", "netherite_pickaxe",
            "survieplus:pioche_maitre", "netherite_pickaxe");
    /** Icones des menus, seulement pour ceux qui ont le pack (item_model direct). */
    public static final Key INCONNU = Key.key("survieplus", "inconnu"), BESTIAIRE = Key.key("survieplus", "bestiaire");
    /** Position du titre dans un menu de coffre. */
    static final int TITRE_X = 8, TITRE_Y = 6;
    private static final int FORMAT = 97;

    /** Un caractere de la police : son texte et de combien il fait avancer le curseur. */
    public record Glyphe(String c, int avance) {}

    private final Map<String, Glyphe> glyphes = new HashMap<>();
    private final JsonArray providers = new JsonArray();
    private final Map<String, byte[]> fichiers = new LinkedHashMap<>();
    private final Map<Integer, Key> policesTexte = new TreeMap<>();
    private final Largeurs largeurs;
    private int prochain = 0xE000;

    private byte[] zip;
    private String sha1;

    PackMenus(Largeurs largeurs) {
        this.largeurs = largeurs;
    }

    /** Ajoute une image dont le bord haut est a y (pixels du menu). */
    Glyphe image(String id, Dessin d, int y) {
        return image(id, d.img, y, 1);
    }

    /**
     * Ajoute une image "fine" : echelle pixels de l'image par pixel du menu (3 = trois fois plus fine que le jeu).
     * Sa hauteur doit etre un multiple de l'echelle, et l'image ne doit pas depasser 256 x 256 (taille des pages
     * de police du client) : voir {@link #mosaique}.
     */
    Glyphe image(String id, BufferedImage img, int y, int echelle) {
        String cle = id + "@" + y;
        Glyphe g = glyphes.get(cle);
        if (g != null) return g;
        if (img.getWidth() > 256 || img.getHeight() > 256 || img.getHeight() % echelle != 0)
            throw new IllegalArgumentException(id + " : " + img.getWidth() + " x " + img.getHeight() + " (echelle " + echelle + ")");
        String chemin = "menu/" + id.replaceAll("[^a-z0-9_]", "_") + ".png";
        fichiers.putIfAbsent("assets/survieplus/textures/" + chemin, png(img));
        String c = new String(Character.toChars(prochain++));
        int hauteur = img.getHeight() / echelle;
        JsonObject p = new JsonObject();
        p.addProperty("type", "bitmap");
        p.addProperty("file", "survieplus:" + chemin);
        p.addProperty("ascent", TITRE_Y + 7 - y);
        p.addProperty("height", hauteur);
        JsonArray chars = new JsonArray();
        chars.add(c);
        p.add("chars", chars);
        providers.add(p);
        g = new Glyphe(c, avance(img, hauteur));
        glyphes.put(cle, g);
        return g;
    }

    /** Avance du curseur apres une image, calculee comme le client : largeur utile x echelle, arrondie, + 1. */
    static int avance(BufferedImage img, int hauteur) {
        int largeur = 0;
        for (int x = img.getWidth() - 1; x >= 0 && largeur == 0; x--)
            for (int y = 0; y < img.getHeight(); y++)
                if ((img.getRGB(x, y) >>> 24) != 0) {
                    largeur = x + 1;
                    break;
                }
        return (int) (0.5 + (double) ((float) largeur * ((float) hauteur / (float) img.getHeight()))) + 1;
    }

    /** Remplace une texture du jeu dans le pack (ex : le fond des livres). */
    void texture(String chemin, BufferedImage img) {
        fichiers.put("assets/minecraft/textures/" + chemin, png(img));
    }

    /** Texture et modele plat d'un objet du pack : survieplus:item/<nom>. */
    void objet(String nom, BufferedImage img) {
        objet(nom, img, "minecraft:item/generated");
    }

    /** Idem avec un autre modele parent (minecraft:item/handheld : tenu en main comme un outil). */
    void objet(String nom, BufferedImage img, String parent) {
        fichiers.put("assets/survieplus/textures/item/" + nom + ".png", png(img));
        JsonObject textures = new JsonObject();
        textures.addProperty("layer0", "survieplus:item/" + nom);
        JsonObject modele = new JsonObject();
        modele.addProperty("parent", parent);
        modele.add("textures", textures);
        fichiers.put("assets/survieplus/models/item/" + nom + ".json", modele.toString().getBytes(StandardCharsets.UTF_8));
        // Definition d'objet survieplus:<nom>, pour item_model (icones des menus dessines)
        JsonObject m = new JsonObject();
        m.addProperty("type", "minecraft:model");
        m.addProperty("model", "survieplus:item/" + nom);
        JsonObject def = new JsonObject();
        def.add("model", m);
        fichiers.put("assets/survieplus/items/" + nom + ".json", def.toString().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Remplace le modele d'un objet du jeu selon son custom_model_data (texte -> modele survieplus:item/...).
     * Sans texte reconnu, l'objet garde son modele normal (repli = fichier d'origine du jeu).
     */
    void variantes(Key objet, String modeleOrigine, Map<String, String> cas) {
        JsonArray liste = new JsonArray();
        // Trie : l'ordre de Map.of change a chaque lancement de Java, et le pack doit etre identique partout
        new TreeMap<>(cas).forEach((texte, modele) -> {
            JsonObject m = new JsonObject();
            m.addProperty("type", "minecraft:model");
            m.addProperty("model", modele);
            JsonObject c = new JsonObject();
            c.addProperty("when", texte);
            c.add("model", m);
            liste.add(c);
        });
        JsonObject repli = new JsonObject();
        repli.addProperty("type", "minecraft:model");
        repli.addProperty("model", modeleOrigine);
        JsonObject select = new JsonObject();
        select.addProperty("type", "minecraft:select");
        select.addProperty("property", "minecraft:custom_model_data");
        select.add("cases", liste);
        select.add("fallback", repli);
        JsonObject def = new JsonObject();
        def.add("model", select);
        fichiers.put("assets/" + objet.namespace() + "/items/" + objet.value() + ".json", def.toString().getBytes(StandardCharsets.UTF_8));
    }

    /** Cote d'un carreau de mosaique, en pixels du menu. */
    static final int CARREAU = 46;

    /** Une grande image fine decoupee en carreaux (chacun est un caractere de la police). */
    public record Mosaique(String id, int y, int largeur, int hauteur) {}

    /** Ajoute une grande image fine (bord haut a y), decoupee en carreaux de CARREAU pixels du menu. */
    Mosaique mosaique(String id, BufferedImage img, int y, int echelle) {
        int w = img.getWidth() / echelle, h = img.getHeight() / echelle;
        for (int r = 0; r * CARREAU < h; r++)
            for (int c = 0; c * CARREAU < w; c++) {
                int cw = Math.min(CARREAU, w - c * CARREAU), ch = Math.min(CARREAU, h - r * CARREAU);
                BufferedImage carreau = img.getSubimage(c * CARREAU * echelle, r * CARREAU * echelle, cw * echelle, ch * echelle);
                image(id + "_" + r + "_" + c, carreau, y + r * CARREAU, echelle);
            }
        return new Mosaique(id, y, w, h);
    }

    public Glyphe get(String id, int y) {
        Glyphe g = glyphes.get(id + "@" + y);
        if (g == null) throw new IllegalArgumentException("image absente du pack : " + id + " @ " + y);
        return g;
    }

    /** Police de texte normale dont le haut des lettres tombe a y (pixels du menu). */
    public Key texte(int y) {
        return policesTexte.computeIfAbsent(y, h -> Key.key("survieplus", "t" + h));
    }

    public Largeurs largeurs() {
        return largeurs;
    }

    // ───────────── Espaces ─────────────

    /** Caracteres d'espace : ±1, ±2, ±4 ... ±512 px. */
    static String espace(int px) {
        StringBuilder sb = new StringBuilder();
        int base = px < 0 ? 0xF800 : 0xF820;
        int reste = Math.abs(px);
        for (int bit = 9; bit >= 0; bit--) {
            while (reste >= (1 << bit)) {
                sb.appendCodePoint(base + bit);
                reste -= 1 << bit;
            }
        }
        return sb.toString();
    }

    private static JsonObject espaces() {
        JsonObject adv = new JsonObject();
        for (int bit = 0; bit <= 9; bit++) {
            adv.addProperty(new String(Character.toChars(0xF800 + bit)), -(1 << bit));
            adv.addProperty(new String(Character.toChars(0xF820 + bit)), 1 << bit);
        }
        JsonObject p = new JsonObject();
        p.addProperty("type", "space");
        p.add("advances", adv);
        return p;
    }

    // ───────────── Fabrication du zip ─────────────

    void finir(BufferedImage icone) throws IOException {
        Gson gson = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
        Map<String, byte[]> tout = new LinkedHashMap<>();

        JsonObject pack = new JsonObject();
        pack.addProperty("description", "SurviePlus : menus en images (fourni par le serveur)");
        pack.addProperty("min_format", FORMAT);
        pack.addProperty("max_format", 999);
        JsonObject mcmeta = new JsonObject();
        mcmeta.add("pack", pack);
        tout.put("pack.mcmeta", json(gson, mcmeta));
        tout.put("pack.png", png(icone));

        // Police des menus : espaces + images
        JsonArray liste = new JsonArray();
        liste.add(espaces());
        liste.addAll(providers);
        JsonObject menu = new JsonObject();
        menu.add("providers", liste);
        tout.put("assets/survieplus/font/menu.json", json(gson, menu));

        // Polices de texte decalees : copie de la police du jeu avec un "ascent" plus bas
        JsonObject defaut = lirePoliceDefaut();
        for (Map.Entry<Integer, Key> e : policesTexte.entrySet()) {
            int decalage = e.getKey() - TITRE_Y;
            JsonArray ps = new JsonArray();
            JsonObject ref = new JsonObject();
            ref.addProperty("type", "reference");
            ref.addProperty("id", "minecraft:include/space");
            ps.add(ref);
            for (JsonElement el : defaut.getAsJsonArray("providers")) {
                JsonObject p = el.getAsJsonObject().deepCopy();
                p.addProperty("ascent", p.get("ascent").getAsInt() - decalage);
                ps.add(p);
            }
            JsonObject police = new JsonObject();
            police.add("providers", ps);
            tout.put("assets/survieplus/font/" + e.getValue().value() + ".json", json(gson, police));
        }

        // Objet invisible : zones survolables des menus (la bulle d'aide s'affiche, rien n'est dessine)
        JsonObject modele = new JsonObject();
        modele.addProperty("type", "minecraft:empty");
        JsonObject vide = new JsonObject();
        vide.add("model", modele);
        tout.put("assets/survieplus/items/vide.json", json(gson, vide));

        tout.putAll(fichiers);

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bos)) {
            for (Map.Entry<String, byte[]> e : tout.entrySet()) {
                ZipEntry z = new ZipEntry(e.getKey());
                z.setTime(315532800000L); // date fixe : meme zip, meme empreinte
                zos.putNextEntry(z);
                zos.write(e.getValue());
                zos.closeEntry();
            }
        }
        zip = bos.toByteArray();
        try {
            sha1 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(zip));
        } catch (Exception ex) {
            throw new IOException(ex);
        }
    }

    public byte[] zip() {
        return zip;
    }

    public String sha1() {
        return sha1;
    }

    /** Les fichiers du pack (pour les tests et l'apercu). */
    Map<String, byte[]> images() {
        return fichiers;
    }

    private static byte[] json(Gson gson, JsonObject o) {
        return gson.toJson(o).getBytes(StandardCharsets.UTF_8);
    }

    private static JsonObject lirePoliceDefaut() throws IOException {
        try (InputStream in = PackMenus.class.getResourceAsStream("/pack/police_defaut.json")) {
            if (in == null) throw new IOException("pack/police_defaut.json introuvable dans le plugin");
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    static byte[] png(BufferedImage img) {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ImageIO.write(img, "png", bos);
            return bos.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
