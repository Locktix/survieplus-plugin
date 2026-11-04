package fr.survieplus.pack;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;

import java.awt.image.BufferedImage;
import java.io.IOException;
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

    public static final TextColor TEXTE_ENCRE = TextColor.color(0x3F2A14);
    public static final TextColor TEXTE_PALE = TextColor.color(0x7A5A3A);
    public static final TextColor TEXTE_TITRE = TextColor.color(0x4A3018);
    public static final TextColor TEXTE_ROUGE = TextColor.color(0x9A2A1A);
    public static final TextColor CADRE_ON = TextColor.color(0x3D7A2A);
    public static final TextColor CADRE_OFF = TextColor.color(0xA8321E);
    public static final TextColor CADRE_BLOQUE = TextColor.color(0x9C8A6E);

    // /metiers : coffre de 6 lignes (222 px de haut)
    static final int CHEMIN_X0 = 30, CHEMIN_X1 = 160;
    /** Pas de la progression du chemin (en pixels). */
    static final int PAS = 2;
    /** Cases du bas de /metiers : retour, niveau total, barre de progression. */
    public static final int[] METIERS_CASES_BAS = {45, 49, 53};

    /** Hauteur du mot « Inventaire » (position du libelle du jeu) dans un coffre de 6 et de 3 lignes. */
    static final int INVENTAIRE_Y_METIERS = 128, INVENTAIRE_Y_SP = 74;

    /** Couleurs des chemins (le trait sous l'encre), dans l'ordre des metiers. */
    private static final int[] COULEURS_CHEMIN = {
            argb(255, 0xD98A14), argb(255, 0x2FA8B8), argb(255, 0xB8A018), argb(255, 0x1F7F8A), argb(255, 0xB8382A)};

    private final PackMenus pack;

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

        pack.image("fond_metiers", d.fondMetiers(), -4);
        pack.image("fond_sp", d.fondSp(casesSp), -4);
        for (int ligne = 0; ligne < 3; ligne++) pack.image("cadre", cadreEncre(), caseY(ligne * 9) - 2);
        for (int n = 0; n < 5; n++) {
            int haut = ligneY(n) + 12 - 4;
            for (int k = 0; k <= (CHEMIN_X1 - CHEMIN_X0) / PAS; k++) pack.image("chemin_" + n + "_" + k, chemin(n, k * PAS, false), haut);
            pack.image("chemin_" + n + "_fini", chemin(n, CHEMIN_X1 - CHEMIN_X0, true), haut);
            pack.texte(ligneY(n));
        }
        pack.texte(PackMenus.TITRE_Y);
        pack.texte(caseY(0) + 4);  // "Options :" de /sp
        pack.texte(caseY(18) + 4); // legende de /sp
        // « Inventaire » sur l'etiquette de papier (le jeu ne l'affiche pas dans ces menus)
        pack.texte(INVENTAIRE_Y_METIERS);
        pack.texte(INVENTAIRE_Y_SP);
        pack.finir(icone());
        return d;
    }

    public PackMenus pack() {
        return pack;
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

    /** Cadre blanc (teinte en jeu) : image 20 x 20, le cadre commence en (1, 1). */
    private static Dessin cadreEncre() {
        Dessin d = new Dessin(20, 20);
        cadreEncre(d, 1, 1, argb(255, 0xFFFFFF));
        return d;
    }

    private static void roseDesVents(Dessin d, int x, int y) {
        d.motif(x, y, new String[]{
                "....#....", "...#N#...", "....#....", ".#..#..#.", "####O####", ".#..#..#.", "....#....", "...#.#...", "....#....",
        }, new char[]{'#', 'N', 'O'}, new int[]{argb(255, 0x5A3D1E), DRAPEAU, DRAPEAU});
    }

    /**
     * Bas commun : l'inventaire du joueur dans le bois, et une etiquette de papier sous le mot « Inventaire »
     * (le jeu l'ecrit en gris fonce, illisible sur le bois). Valeurs en pixels du menu ; l'image commence en (-4, -4).
     */
    private static void inventaire(Dessin d, int yBois, int yLabel, int ySlots, int yBarre) {
        bois(d, 0, yBois + 4, 184, d.h - yBois - 4, 77);
        papier(d, 4 + 4, 4 + yLabel - 2, 58, 12, 78);
        for (int r = 0; r < 3; r++) for (int c = 0; c < 9; c++) caseCreusee(d, 4 + 8 + 18 * c, 4 + ySlots + 18 * r);
        for (int c = 0; c < 9; c++) caseCreusee(d, 4 + 8 + 18 * c, 4 + yBarre);
    }

    // ───────────── Fonds (image qui commence en x = -4, y = -4) ─────────────

    private Dessin fondMetiers() {
        // 6 lignes : haut du coffre jusqu'a y = 125, inventaire en dessous (fin a 222)
        Dessin d = new Dessin(184, 230);
        bois(d, 0, 0, 184, 133, 11);
        papier(d, 6, 6, 172, 123, 12);
        inventaire(d, 125, INVENTAIRE_Y_METIERS, 139, 197);
        roseDesVents(d, 4 + 157, 4 + 3);
        for (int n = 0; n < 5; n++) {
            int yy = 4 + ligneY(n) + 12;
            for (int x = CHEMIN_X0; x <= CHEMIN_X1; x++)
                if ((x - CHEMIN_X0) % 4 < 2) d.px(4 + x, yy + vague(n, x), ENCRE_PALE);
        }
        for (int slot : METIERS_CASES_BAS) cadreEncre(d, 4 + caseX(slot) - 1, 4 + caseY(slot) - 1, CADRE_FIXE);
        return d;
    }

    private Dessin fondSp(int[] cases) {
        // 3 lignes : haut du coffre jusqu'a y = 71, inventaire en dessous (fin a 168)
        Dessin d = new Dessin(184, 176);
        bois(d, 0, 0, 184, 79, 21);
        papier(d, 6, 6, 172, 69, 22);
        inventaire(d, 71, INVENTAIRE_Y_SP, 85, 143);
        roseDesVents(d, 4 + 157, 4 + 3);
        // cadres fixes (metiers, guide, sommeil) ; ceux des options sont ajoutes en couleur selon leur etat
        for (int slot : cases)
            if (slot < 9 || slot >= 18) cadreEncre(d, 4 + caseX(slot) - 1, 4 + caseY(slot) - 1, CADRE_FIXE);
        return d;
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
    public record LigneMetier(String nom, int niveau, long actuel, long requis, boolean max) {}

    /** Titre de /metiers : le fond, puis pour chaque metier son chemin, son nom, son niveau et son pourcentage. */
    public Component titreMetiers(int niveauTotal, List<LigneMetier> lignes) {
        Toile t = new Toile(pack);
        t.image(pack.get("fond_metiers", -4), -4);
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
                long longueur = (CHEMIN_X1 - CHEMIN_X0) * Math.min(l.actuel(), l.requis()) / Math.max(1, l.requis());
                t.image(pack.get("chemin_" + n + "_" + longueur / PAS, haut), CHEMIN_X0 - 2);
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

    /** Titre de /sp : le fond, les cadres a l'encre de couleur autour des options, et une legende. */
    public Component titreSp(Map<Integer, TextColor> cadres) {
        Toile t = new Toile(pack);
        t.image(pack.get("fond_sp", -4), -4);
        t.texte("SurviePlus", 9, PackMenus.TITRE_Y, TEXTE_TITRE);
        t.texte("Options :", 9, caseY(0) + 4, TEXTE_PALE);
        t.texte("Inventaire", 8, INVENTAIRE_Y_SP, TEXTE_ENCRE);
        cadres.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e ->
                t.image(pack.get("cadre", caseY(e.getKey()) - 2), caseX(e.getKey()) - 2, e.getValue()));
        int y = caseY(18) + 4;
        t.texte("■", 100, y, CADRE_ON).texte("actif", 107, y, TEXTE_PALE);
        t.texte("■", 133, y, CADRE_OFF).texte("coupé", 140, y, TEXTE_PALE);
        return t.fin();
    }
}
