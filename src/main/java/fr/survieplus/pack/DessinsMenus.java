package fr.survieplus.pack;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;

import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static fr.survieplus.pack.Dessin.argb;

/**
 * Les menus dans le style « carte des metiers » : une feuille de carte dans un cadre en bois sombre,
 * chaque metier est un chemin a l'encre (la partie parcourue est tracee, une croix marque ou l'on en est,
 * un drapeau quand c'est fini). Tout est dessine ici, en pixel art, avec les couleurs du papier des cartes
 * et du chene noir du jeu (aucune texture du jeu n'est copiee).
 * <p>
 * Coordonnees en pixels depuis le coin haut gauche du menu ; une case du coffre est en
 * (8 + 18 x colonne, 18 + 18 x ligne). Le jeu dessine le titre (donc ces images) sous les objets :
 * chaque element est place au-dessus de ceux qu'il chevauche, dans l'ordre ou ils sont soumis.
 */
public final class DessinsMenus {

    // Papier des cartes du jeu : deux couleurs
    private static final int PAPIER = argb(255, 0xD6BE96);
    private static final int PAPIER_BORD = argb(255, 0x99876C);
    // Chene noir
    private static final int[] BOIS = {argb(255, 0x4F3218), argb(255, 0x492F17), argb(255, 0x53381A), argb(255, 0x3E2912)};
    private static final int BOIS_JOINT = argb(255, 0x291A0C);
    private static final int BOIS_GRAIN = argb(255, 0x3A2411);
    private static final int CONTOUR = argb(255, 0x140C05);
    // Encres
    private static final int ENCRE = argb(255, 0x4A3018);
    private static final int ENCRE_PALE = argb(255, 0x8A6A44);
    private static final int CADRE_FIXE = argb(255, 0x7A5A3A);
    private static final int CROIX = argb(255, 0xB0261A);
    private static final int DRAPEAU = argb(255, 0x9A2A1A);
    // Cases de l'inventaire, creusees dans le bois
    private static final int CASE_FOND = argb(255, 0x2A1A0C);
    private static final int CASE_SOMBRE = argb(255, 0x100903);
    private static final int CASE_CLAIR = argb(255, 0x6B4A2A);

    // Marques de /sp
    private static final int COCHE = argb(255, 0x3D7A2A), CROIX_SP = argb(255, 0xA8321E), CADENAS = argb(255, 0x9C8A6E);

    public static final TextColor TEXTE_ENCRE = TextColor.color(0x3F2A14);
    public static final TextColor TEXTE_PALE = TextColor.color(0x7A5A3A);
    public static final TextColor TEXTE_TITRE = TextColor.color(0x4A3018);
    public static final TextColor TEXTE_ROUGE = TextColor.color(0x9A2A1A);

    /** Etat d'une option de /sp : une coche verte, une croix rouge ou un cadenas sous la case. */
    public enum Etat { ACTIF, COUPE, BLOQUE }

    // /metiers : coffre de 6 lignes (222 px de haut)
    static final int CHEMIN_X0 = 30, CHEMIN_X1 = 160;
    /** Pas de la progression du chemin (en pixels). */
    static final int PAS = 2;
    /** Cases du bas de /metiers : retour, niveau total, barre de progression. */
    public static final int[] METIERS_CASES_BAS = {45, 49, 53};
    /** Bestiaire : une espece par case sur les 5 premieres lignes, puis page precedente, progression, page suivante. */
    public static final int BESTIAIRE_PAR_PAGE = 45;
    public static final int[] BESTIAIRE_CASES_BAS = {45, 49, 53};

    /** Hauteur du mot « Inventaire » (position du libelle du jeu) dans un coffre de 6 et de 3 lignes. */
    static final int INVENTAIRE_Y_METIERS = 128, INVENTAIRE_Y_SP = 74;
    /** Zone du petit decor de chaque metier, entre le niveau et le pourcentage (au-dessus du chemin). */
    static final int DECOR_X0 = 116, DECOR_X1 = 142;
    /** Jalons du chemin : tous les cinquiemes du niveau max (tous les 10 niveaux pour 50). */
    static final int JALONS = 5;
    /** Hauteur des marques sous les options de /sp (ligne du bas du coffre). */
    static final int MARQUES_Y = 56;

    /** Couleurs des chemins (le trait sous l'encre), dans l'ordre des metiers. */
    private static final int[] COULEURS_CHEMIN = {
            argb(255, 0xD98A14), argb(255, 0x2FA8B8), argb(255, 0xB8A018), argb(255, 0x1F7F8A), argb(255, 0xB8382A)};

    private final PackMenus pack;
    private PackMenus.Mosaique fondMetiers, fondSp, fondBestiaire;
    /** Finesse des fonds : 3 pixels d'image par pixel du menu. */
    static final int HD = 3;

    private DessinsMenus(PackMenus pack) {
        this.pack = pack;
    }

    static int caseX(int slot) {
        return 8 + 18 * (slot % 9);
    }

    static int caseY(int slot) {
        return 18 + 18 * (slot / 9);
    }

    /** Haut de la ligne du metier n (0 a 4) dans /metiers. */
    static int ligneY(int n) {
        return caseY(n * 9);
    }

    /** Ondulation du chemin du metier n a l'abscisse x (-1, 0 ou 1). */
    static int vague(int n, int x) {
        return (int) Math.round(Math.sin((x + n * 7) / 9.0));
    }

    // ───────────── Fabrication ─────────────

    static DessinsMenus fabriquer(int[] casesSp) throws IOException {
        PackMenus pack = new PackMenus(Largeurs.charger());
        DessinsMenus d = new DessinsMenus(pack);

        d.fondMetiers = pack.mosaique("fond_metiers", d.fondMetiers(), -4, HD);
        d.fondSp = pack.mosaique("fond_sp", d.fondSp(casesSp), -4, HD);
        pack.image("coche", marque(Etat.ACTIF), MARQUES_Y);
        pack.image("croix", marque(Etat.COUPE), MARQUES_Y);
        pack.image("cadenas", marque(Etat.BLOQUE), MARQUES_Y);
        for (int n = 0; n < 5; n++) {
            int haut = ligneY(n) + 12 - 4;
            for (int k = 0; k <= (CHEMIN_X1 - CHEMIN_X0) / PAS; k++) pack.image("chemin_" + n + "_" + k, chemin(n, k * PAS, false), haut);
            pack.image("chemin_" + n + "_fini", chemin(n, CHEMIN_X1 - CHEMIN_X0, true), haut);
            pack.texte(ligneY(n));
        }
        // Le guide : fond des livres, decor de chaque metier a droite du titre de sa page, paysage sur la couverture
        pack.texture("gui/book.png", hd("livre"));
        for (int n = 0; n < 5; n++) d.livre("livre_decor_" + n, hd("decor_" + n), 10);
        BufferedImage monts = hd("bas_montagnes"), sapins = hd("bas_sapins");
        BufferedImage paysage = new BufferedImage(monts.getWidth() + 2 * HD + sapins.getWidth(),
                Math.max(monts.getHeight(), sapins.getHeight()), BufferedImage.TYPE_INT_ARGB);
        paysage.getGraphics().drawImage(monts, 0, paysage.getHeight() - monts.getHeight(), null);
        paysage.getGraphics().drawImage(sapins, monts.getWidth() + 2 * HD, paysage.getHeight() - sapins.getHeight(), null);
        d.livre("livre_paysage", paysage, 8);
        // Filet de capture (images Higgsfield, preparees par outils/PrepFilet.java)
        // et icones du bestiaire (images Higgsfield, preparees par outils/PrepIcones.java)
        for (String o : List.of("filet_vide", "filet_plein", "inconnu", "bestiaire", "medaille")) pack.objet(o, objet(o));
        pack.variantes(PackMenus.MODELE_FILET, "minecraft:item/cobweb", java.util.Map.of(
                PackMenus.FILET_VIDE, "survieplus:item/filet_vide",
                PackMenus.FILET_PLEIN, "survieplus:item/filet_plein"));
        pack.variantes(PackMenus.MODELE_MEDAILLE, "minecraft:item/gold_nugget", java.util.Map.of(
                PackMenus.MEDAILLE, "survieplus:item/medaille"));
        // Pioche du mineur (planche Higgsfield, outils/PrepIcones.java --contour) : tenue en main comme un outil
        // Trie : l'ordre de Map.of change a chaque lancement de Java, et le pack doit etre identique partout
        for (var e : new java.util.TreeMap<>(PackMenus.PIOCHES).entrySet()) {
            String nom = e.getKey().substring("survieplus:".length());
            pack.objet(nom, objet(nom), "minecraft:item/handheld");
        }
        for (String mat : List.of("stone", "iron", "diamond", "netherite")) {
            java.util.Map<String, String> cas = new java.util.HashMap<>();
            PackMenus.PIOCHES.forEach((texte, objet) -> {
                if (objet.equals(mat + "_pickaxe")) cas.put(texte, "survieplus:item/" + texte.substring("survieplus:".length()));
            });
            pack.variantes(net.kyori.adventure.key.Key.key("minecraft", mat + "_pickaxe"), "minecraft:item/" + mat + "_pickaxe", cas);
        }
        d.fondBestiaire = pack.mosaique("fond_bestiaire", d.fondBestiaire(), -4, HD);
        pack.texte(PackMenus.TITRE_Y);
        pack.texte(caseY(0) + 4);  // "Options :" de /sp
        // « Inventaire » sur l'etiquette de papier (le jeu ne l'affiche pas dans ces menus)
        pack.texte(INVENTAIRE_Y_METIERS);
        pack.texte(INVENTAIRE_Y_SP);
        pack.finir(icone());
        return d;
    }

    public PackMenus pack() {
        return pack;
    }

    // ───────────── Guide (livre) ─────────────

    /** Largeur d'une ligne de page de livre (au-dela, le jeu passe a la ligne). */
    static final int PAGE_LARGEUR = 114;
    private final Map<String, PackMenus.Glyphe> livre = new java.util.HashMap<>();

    /**
     * Image fine pour une ligne de livre, son bas a "bas" pixels sous le haut de la ligne. Sa hauteur est completee
     * par le haut jusqu'a un multiple de HD.
     */
    private void livre(String id, BufferedImage img, int bas) {
        int h = (img.getHeight() + HD - 1) / HD * HD;
        BufferedImage c = new BufferedImage(img.getWidth(), h, BufferedImage.TYPE_INT_ARGB);
        c.getGraphics().drawImage(img, 0, h - img.getHeight(), null);
        livre.put(id, pack.image(id, c, PackMenus.TITRE_Y + bas - h / HD, HD));
    }

    /** Image posee en x sur la ligne, sans faire avancer le texte qui suit. */
    private Component surLigne(PackMenus.Glyphe g, int x) {
        return Component.text(PackMenus.espace(x) + g.c() + PackMenus.espace(-x - g.avance()),
                        net.kyori.adventure.text.format.NamedTextColor.WHITE)
                .font(PackMenus.POLICE).shadowColor(net.kyori.adventure.text.format.ShadowColor.none());
    }

    /** Le decor du metier n, cale a droite de la ligne (a mettre en tete de la ligne du titre). */
    public Component decorLivre(int n) {
        PackMenus.Glyphe g = livre.get("livre_decor_" + n);
        // le curseur apres l'image (x + avance) doit rester dans la ligne, sinon le jeu passe a la ligne avant l'image
        return surLigne(g, PAGE_LARGEUR - 1 - g.avance());
    }

    /** Le paysage de la couverture, centre (a mettre en tete d'une ligne vide). */
    public Component paysageLivre() {
        PackMenus.Glyphe g = livre.get("livre_paysage");
        return surLigne(g, (PAGE_LARGEUR - (g.avance() - 1)) / 2);
    }

    // ───────────── Matieres ─────────────

    /** Planches de chene noir (4 px de haut, joints decales), avec un contour sombre. */
    private static void bois(Dessin d, int x, int y, int w, int h, long graine) {
        Random r = new Random(graine);
        d.rect(x, y, w, h, CONTOUR);
        for (int j = 1; j < h - 1; j++) {
            int rang = (j - 1) / 4, dansRang = (j - 1) % 4;
            int decalage = (rang * 7) % 16;
            for (int i = 1; i < w - 1; i++) {
                boolean joint = dansRang == 3 || (i + decalage) % 16 == 0;
                int c = joint ? BOIS_JOINT : BOIS[(rang + (i + decalage) / 16) % BOIS.length];
                if (!joint && r.nextInt(9) == 0) c = BOIS_GRAIN;
                d.px(x + i, y + j, c);
            }
        }
    }

    /** Feuille de papier aux bords dechires : bords ronges par endroits, un lisere plus fonce. */
    private static void papier(Dessin d, int x, int y, int w, int h, long graine) {
        Random r = new Random(graine);
        int[] haut = new int[w], bas = new int[w], gauche = new int[h], droite = new int[h];
        for (int i = 0; i < w; i++) {
            haut[i] = r.nextInt(5) == 0 ? 1 + r.nextInt(2) : 0;
            bas[i] = r.nextInt(5) == 0 ? 1 + r.nextInt(2) : 0;
        }
        for (int j = 0; j < h; j++) {
            gauche[j] = r.nextInt(5) == 0 ? 1 + r.nextInt(2) : 0;
            droite[j] = r.nextInt(5) == 0 ? 1 + r.nextInt(2) : 0;
        }
        for (int j = 0; j < h; j++) {
            for (int i = 0; i < w; i++) {
                int m = Math.min(Math.min(j - haut[i], h - 1 - j - bas[i]), Math.min(i - gauche[j], w - 1 - i - droite[j]));
                if (m < 0) continue;
                if (Math.min(i, w - 1 - i) + Math.min(j, h - 1 - j) < 3) continue; // coins arrondis
                d.px(x + i, y + j, m == 0 ? PAPIER_BORD : PAPIER);
            }
        }
    }

    /** Case creusee dans le bois (pour l'inventaire) ; (x, y) = position de l'objet. */
    private static void caseCreusee(Dessin d, int x, int y) {
        d.rect(x - 1, y - 1, 18, 18, CASE_FOND);
        d.rect(x - 1, y - 1, 18, 1, CASE_SOMBRE);
        d.rect(x - 1, y - 1, 1, 18, CASE_SOMBRE);
        d.rect(x, y + 16, 17, 1, CASE_CLAIR);
        d.rect(x + 16, y, 1, 17, CASE_CLAIR);
    }

    /** Cadre carre trace a l'encre a main levee autour d'une case ; (x, y) = coin du cadre 18 x 18. */
    private static void cadreEncre(Dessin d, int x, int y, int c) {
        d.rect(x, y, 18, 1, c);
        d.rect(x, y + 17, 18, 1, c);
        d.rect(x, y, 1, 18, c);
        d.rect(x + 17, y, 1, 18, c);
        // petits defauts de plume : depassements et un trou
        d.px(x - 1, y, c);
        d.px(x + 17, y + 18, c);
        d.px(x + 9, y + 17, 0);
        d.px(x + 18, y + 3, c);
    }

    /** Marque a l'encre sous une option de /sp (7 x 7) : coche verte, croix rouge ou cadenas. */
    static Dessin marque(Etat e) {
        Dessin d = new Dessin(7, 7);
        switch (e) {
            case ACTIF -> d.motif(0, 0, new String[]{
                    "......#", ".....##", "#...##.", "##.##..", ".###...", "..#....", "......."}, new char[]{'#'}, new int[]{COCHE});
            case COUPE -> d.motif(0, 0, new String[]{
                    "#.....#", "##...##", ".##.##.", "..###..", ".##.##.", "##...##", "#.....#"}, new char[]{'#'}, new int[]{CROIX_SP});
            case BLOQUE -> d.motif(1, 0, new String[]{
                    ".###.", "#...#", "#...#", "#####", "##.##", "##.##", "#####"}, new char[]{'#'}, new int[]{CADENAS});
        }
        return d;
    }

    /**
     * Bas commun, dessine par-dessus le bois du fond : les cases de l'inventaire du joueur, et une etiquette de papier
     * sous le mot « Inventaire » (le jeu l'ecrit en gris fonce, illisible sur le bois). Pixels du menu + 4.
     */
    private static void inventaire(Dessin d, int yLabel, int ySlots, int yBarre) {
        papier(d, 4 + 4, 4 + yLabel - 2, 58, 12, 78);
        for (int r = 0; r < 3; r++) for (int c = 0; c < 9; c++) caseCreusee(d, 4 + 8 + 18 * c, 4 + ySlots + 18 * r);
        for (int c = 0; c < 9; c++) caseCreusee(d, 4 + 8 + 18 * c, 4 + yBarre);
    }

    // ───────────── Fonds (image fine qui commence en x = -4, y = -4) ─────────────

    /** Texture d'objet du pack (pack/objets/). */
    private static BufferedImage objet(String nom) throws IOException {
        try (InputStream in = DessinsMenus.class.getResourceAsStream("/pack/objets/" + nom + ".png")) {
            if (in == null) throw new IOException("pack/objets/" + nom + ".png introuvable dans le plugin");
            return ImageIO.read(in);
        }
    }

    /** Image fine du pack (pack/hd/, preparee par outils/PrepHD.java a partir des images Higgsfield). */
    private static BufferedImage hd(String nom) throws IOException {
        try (InputStream in = DessinsMenus.class.getResourceAsStream("/pack/hd/" + nom + ".png")) {
            if (in == null) throw new IOException("pack/hd/" + nom + ".png introuvable dans le plugin");
            BufferedImage brut = ImageIO.read(in), img = new BufferedImage(brut.getWidth(), brut.getHeight(), BufferedImage.TYPE_INT_ARGB);
            img.getGraphics().drawImage(brut, 0, 0, null);
            return img;
        }
    }

    /**
     * Pose le calque (pixels du menu, grossis HD fois) sur le fond fin. Le papier de l'etiquette « Inventaire »
     * reprend le papier du fond, pris yPapier pixels plus haut (une zone vierge de la carte).
     */
    private static void calque(BufferedImage fond, Dessin calque, int yPapier) {
        BufferedImage vierge = new BufferedImage(fond.getWidth(), fond.getHeight(), BufferedImage.TYPE_INT_ARGB);
        vierge.getGraphics().drawImage(fond, 0, 0, null);
        for (int y = 0; y < calque.h; y++)
            for (int x = 0; x < calque.w; x++) {
                int c = calque.img.getRGB(x, y);
                if ((c >>> 24) == 0) continue;
                for (int j = 0; j < HD; j++)
                    for (int i = 0; i < HD; i++)
                        fond.setRGB(x * HD + i, y * HD + j, c == PAPIER ? vierge.getRGB(x * HD + i, (y - yPapier) * HD + j) : c);
            }
    }

    /** Colle un dessin fin (fond transparent) ; (x, bas) en pixels du menu + 4. */
    private static void coller(BufferedImage fond, BufferedImage img, int x, int bas) {
        int x0 = x * HD, y0 = bas * HD + HD - img.getHeight();
        for (int y = 0; y < img.getHeight(); y++)
            for (int i = 0; i < img.getWidth(); i++)
                if ((img.getRGB(i, y) >>> 24) != 0) fond.setRGB(x0 + i, y0 + y, img.getRGB(i, y));
    }

    private BufferedImage fondMetiers() throws IOException {
        // 6 lignes : haut du coffre jusqu'a y = 125, inventaire en dessous (fin a 222)
        BufferedImage fond = hd("fond_metiers");
        for (int n = 0; n < 5; n++) {
            BufferedImage decor = hd("decor_" + n);
            int w = (decor.getWidth() + HD - 1) / HD;
            if (w > DECOR_X1 - DECOR_X0 + 1 || decor.getHeight() > 13 * HD)
                throw new IllegalStateException("decor " + n + " trop grand");
            coller(fond, decor, 4 + DECOR_X0 + (DECOR_X1 - DECOR_X0 + 1 - w) / 2, 4 + ligneY(n) + 10);
        }
        // ligne du bas : des montagnes et un bout de foret entre les cases
        coller(fond, hd("bas_montagnes"), 4 + 30, 4 + 121);
        coller(fond, hd("bas_sapins"), 4 + 113, 4 + 122);

        Dessin d = new Dessin(184, 230);
        inventaire(d, INVENTAIRE_Y_METIERS, 139, 197);
        // trait a la plume sous le titre, qui remonte un peu au bout
        int finTitre = 9 + pack.largeurs().de("Carte des métiers") - 2;
        for (int x = 9; x <= finTitre; x++) d.px(4 + x, 4 + (x > finTitre - 3 ? 14 : 15), ENCRE_PALE);
        for (int n = 0; n < 5; n++) {
            int yy = 4 + ligneY(n) + 12;
            for (int x = CHEMIN_X0; x <= CHEMIN_X1; x++)
                if ((x - CHEMIN_X0) % 4 < 2) d.px(4 + x, yy + vague(n, x), ENCRE_PALE);
            // jalons : un point d'encre tous les cinquiemes du chemin
            for (int k = 1; k < JALONS; k++) {
                int x = CHEMIN_X0 + (CHEMIN_X1 - CHEMIN_X0) * k / JALONS;
                d.rect(4 + x, yy + vague(n, x) - 1, 2, 2, ENCRE);
            }
        }
        for (int slot : METIERS_CASES_BAS) cadreEncre(d, 4 + caseX(slot) - 1, 4 + caseY(slot) - 1, CADRE_FIXE);
        calque(fond, d, 80);
        return fond;
    }

    private BufferedImage fondBestiaire() throws IOException {
        // Page du carnet (Higgsfield, outils/PrepBestiaire.java) sur le haut ; le bois de l'inventaire des metiers dessous
        BufferedImage fond = hd("fond_metiers");
        fond.getGraphics().drawImage(hd("fond_bestiaire"), 0, 0, null);
        // la plume et l'empreinte des coins, posees dans la ligne du bas entre les boutons
        BufferedImage plume = hd("bestiaire_plume"), patte = hd("bestiaire_patte");
        coller(fond, plume, 4 + 53 - plume.getWidth() / HD / 2, 4 + 123);
        coller(fond, patte, 4 + 125 - patte.getWidth() / HD / 2, 4 + 123);

        Dessin d = new Dessin(184, 230);
        inventaire(d, INVENTAIRE_Y_METIERS, 139, 197);
        int finTitre = 9 + pack.largeurs().de("Bestiaire") - 2;
        for (int x = 9; x <= finTitre; x++) d.px(4 + x, 4 + (x > finTitre - 3 ? 14 : 15), ENCRE_PALE);
        // grille des especes : une case par espece, traits fins a l'encre
        int lignes = BESTIAIRE_PAR_PAGE / 9;
        for (int c = 0; c <= 9; c++) d.rect(4 + 7 + 18 * c, 4 + 17, 1, 18 * lignes + 1, ENCRE_PALE);
        for (int r = 0; r <= lignes; r++) d.rect(4 + 7, 4 + 17 + 18 * r, 18 * 9 + 1, 1, ENCRE_PALE);
        for (int slot : BESTIAIRE_CASES_BAS) cadreEncre(d, 4 + caseX(slot) - 1, 4 + caseY(slot) - 1, CADRE_FIXE);
        calque(fond, d, 80);
        return fond;
    }

    private BufferedImage fondSp(int[] cases) throws IOException {
        // 3 lignes : haut du coffre jusqu'a y = 71, inventaire en dessous (fin a 168)
        BufferedImage fond = hd("fond_sp");
        Dessin d = new Dessin(184, 176);
        inventaire(d, INVENTAIRE_Y_SP, 85, 143);
        // toutes les cases dans un cadre a l'encre ; l'etat des options est marque dessous (coche, croix, cadenas)
        for (int slot : cases) cadreEncre(d, 4 + caseX(slot) - 1, 4 + caseY(slot) - 1, CADRE_FIXE);
        calque(fond, d, 30);
        return fond;
    }

    /**
     * Chemin parcouru du metier n sur "longueur" pixels : trait d'encre avec la couleur du metier dessous,
     * et une croix rouge au bout (un drapeau si le metier est fini). L'image commence en x = CHEMIN_X0 - 2,
     * sa ligne 4 est la hauteur du chemin.
     */
    private static Dessin chemin(int n, int longueur, boolean fini) {
        Dessin d = new Dessin(CHEMIN_X1 - CHEMIN_X0 + 6, 9);
        int ox = CHEMIN_X0 - 2, base = 4;
        int fin = CHEMIN_X0 + longueur;
        for (int x = CHEMIN_X0; x <= fin; x++) {
            int v = vague(n, x);
            d.px(x - ox, base + v, ENCRE);
            d.px(x - ox, base + v + 1, COULEURS_CHEMIN[n]);
        }
        int vf = vague(n, fin);
        if (fini) {
            d.motif(fin - ox - 1, base + vf - 4, new String[]{"##.", "###", "##.", "#..", "#.."}, new char[]{'#'}, new int[]{DRAPEAU});
        } else {
            d.motif(fin - ox - 2, base + vf - 2, new String[]{"#...#", ".#.#.", "..#..", ".#.#.", "#...#"},
                    new char[]{'#'}, new int[]{CROIX});
        }
        return d;
    }

    /** Icone du pack : un bout de carte avec un chemin et une croix rouge. */
    private static BufferedImage icone() {
        Dessin d = new Dessin(16, 16);
        bois(d, 0, 0, 16, 16, 5);
        papier(d, 2, 2, 12, 12, 6);
        d.motif(4, 9, new String[]{"#..##", ".##.."}, new char[]{'#'}, new int[]{ENCRE});
        d.motif(8, 4, new String[]{"#...#", ".#.#.", "..#..", ".#.#.", "#...#"}, new char[]{'#'}, new int[]{CROIX});
        BufferedImage grand = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 64; y++) for (int x = 0; x < 64; x++) grand.setRGB(x, y, d.img.getRGB(x / 4, y / 4));
        return grand;
    }

    // ───────────── Titres ─────────────

    /** Une ligne de /metiers. */
    public record LigneMetier(String nom, int niveau, int niveauMax, long actuel, long requis, boolean max) {}

    /**
     * Longueur du chemin parcouru (en pixels, multiple de PAS) : tout le chemin va du niveau 0 au niveau max,
     * les jalons marquent chaque cinquieme ; le pourcentage affiche a droite reste celui du niveau en cours.
     */
    static int longueurChemin(LigneMetier l) {
        int total = CHEMIN_X1 - CHEMIN_X0;
        double fait = l.niveau() + (double) Math.min(l.actuel(), l.requis()) / Math.max(1, l.requis());
        int px = (int) (total * Math.max(0, Math.min(1, fait / Math.max(1, l.niveauMax()))));
        return Math.min(total, px) / PAS * PAS;
    }

    /** Titre de /metiers : le fond, puis pour chaque metier son chemin, son nom, son niveau et son pourcentage. */
    public Component titreMetiers(int niveauTotal, List<LigneMetier> lignes) {
        Toile t = new Toile(pack);
        t.mosaique(fondMetiers, -4);
        t.texte("Carte des métiers", 9, PackMenus.TITRE_Y, TEXTE_TITRE);
        t.texteDroite("total " + niveauTotal, 152, PackMenus.TITRE_Y, TEXTE_PALE);
        t.texte("Inventaire", 8, INVENTAIRE_Y_METIERS, TEXTE_ENCRE);
        for (int n = 0; n < lignes.size() && n < 5; n++) {
            LigneMetier l = lignes.get(n);
            int y = ligneY(n);
            int haut = y + 12 - 4;
            if (l.max()) {
                t.image(pack.get("chemin_" + n + "_fini", haut), CHEMIN_X0 - 2);
            } else {
                t.image(pack.get("chemin_" + n + "_" + longueurChemin(l) / PAS, haut), CHEMIN_X0 - 2);
            }
            t.texte(l.nom(), 29, y, TEXTE_ENCRE);
            t.texte("niv. " + l.niveau(), 29 + pack.largeurs().de(l.nom()) + 3, y, TEXTE_PALE);
            t.texteDroite(l.max() ? "fini" : pourcent(l.actuel(), l.requis()), 166, y, l.max() ? TEXTE_ROUGE : TEXTE_PALE);
        }
        return t.fin();
    }

    /** "0 %" a "99 %" (jamais 100 % avant le niveau suivant). */
    static String pourcent(long actuel, long requis) {
        return Math.min(99, Math.max(0, actuel * 100 / Math.max(1, requis))) + " %";
    }

    /** Titre du bestiaire : le fond, le nombre d'especes capturees (et la page s'il y en a plusieurs). */
    public Component titreBestiaire(int captures, int total, int page, int pages) {
        Toile t = new Toile(pack);
        t.mosaique(fondBestiaire, -4);
        t.texte("Bestiaire", 9, PackMenus.TITRE_Y, TEXTE_TITRE);
        String droite = captures + " / " + total + (pages > 1 ? "   p. " + (page + 1) + "/" + pages : "");
        t.texteDroite(droite, 166, PackMenus.TITRE_Y, captures >= total ? TEXTE_ROUGE : TEXTE_PALE);
        t.texte("Inventaire", 8, INVENTAIRE_Y_METIERS, TEXTE_ENCRE);
        return t.fin();
    }

    /** Titre de /sp : le fond, et sous chaque option (ligne du milieu) une coche, une croix ou un cadenas. */
    public Component titreSp(Map<Integer, Etat> etats) {
        Toile t = new Toile(pack);
        t.mosaique(fondSp, -4);
        t.texte("SurviePlus", 9, PackMenus.TITRE_Y, TEXTE_TITRE);
        t.texte("Options :", 9, caseY(0) + 4, TEXTE_PALE);
        t.texte("Inventaire", 8, INVENTAIRE_Y_SP, TEXTE_ENCRE);
        etats.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> {
            String id = switch (e.getValue()) {
                case ACTIF -> "coche";
                case COUPE -> "croix";
                case BLOQUE -> "cadenas";
            };
            t.image(pack.get(id, MARQUES_Y), caseX(e.getKey()) + 4);
        });
        return t.fin();
    }
}
