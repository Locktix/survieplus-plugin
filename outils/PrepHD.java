import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Prepare les images Higgsfield pour le pack des menus (a lancer a la main, quand on change d'images) :
 *   java outils/PrepHD.java maquettes/higgsfield src/main/resources/pack/hd
 * Les images sortent a 3 fois la resolution du menu (1 pixel du menu = 3 x 3 pixels), marge de 4 px comprise.
 * La boussole et le dessin des montagnes sont deplaces pour ne pas passer sous une case ou sous le texte.
 */
public class PrepHD {

    static final int F = 3;

    public static void main(String[] a) throws Exception {
        File src = new File(a[0]), dst = new File(a[1]);
        dst.mkdirs();

        // ── /metiers : 184 x 230 (menu de 6 lignes + marge de 4) ──
        BufferedImage m = ImageIO.read(new File(src, "hd-fond-metiers.png"));
        boolean[][] masqueRose = masque(m, 752, 38, 838, 116, 90, null);
        BufferedImage roseM = detacher(m, masqueRose);
        effacer(m, masqueRose, 560, 170);
        BufferedImage fm = redim(m, 184 * F, 230 * F);
        coller(fm, redimHauteur(roseM, 11 * F), (4 + 156) * F, (4 + 3) * F); // x 156.., y 3..13 du menu
        ImageIO.write(fm, "png", new File(dst, "fond_metiers.png"));

        // ── /sp : 184 x 176 (menu de 3 lignes + marge de 4) ──
        BufferedImage s = ImageIO.read(new File(src, "hd-fond-sp.png"));
        // la boussole (avec son N), sans la coulure du bord dechire en haut a droite
        boolean[][] masqueRoseS = masque(s, 845, 46, 955, 165, 90, (x, y) -> x >= 932 && y < 70);
        boolean[][] masqueMonts = masque(s, 390, 53, 632, 145, 90, null);
        BufferedImage rs = detacher(s, masqueRoseS), ms = detacher(s, masqueMonts);
        effacer(s, masqueRoseS, 690, 200);
        effacer(s, masqueMonts, 390, 200);
        BufferedImage fs = redim(s, 184 * F, 176 * F);
        coller(fs, redimHauteur(rs, 14 * F), (4 + 156) * F, (4 + 1) * F); // au-dessus du guide (case 8)
        BufferedImage msr = redimLargeur(ms, 42 * F);
        coller(fs, msr, (4 + 104) * F, (4 + 34) * F - msr.getHeight()); // entre les metiers (case 4) et le guide
        ImageIO.write(fs, "png", new File(dst, "fond_sp.png"));

        // ── livre (textures/gui/book.png) : 256 x 256 unites, le jeu en dessine 192 x 192 ; le texte va de
        //    x 36 a 150 et de y 30 a 158, les fleches sont en y 157..170 : la page fait x 18..167, y 2..189 ──
        BufferedImage l = ImageIO.read(new File(src, "hd-livre.png"));
        BufferedImage livre = new BufferedImage(256 * F, 256 * F, BufferedImage.TYPE_INT_ARGB);
        BufferedImage page = redim(l, 150 * F, 188 * F);
        livre.getGraphics().drawImage(page, 18 * F, 2 * F, null);
        ImageIO.write(livre, "png", new File(dst, "livre.png"));

        // ── decors (planche sur fond transparent) ──
        BufferedImage d = ImageIO.read(new File(src, "hd-decors.png"));
        int[][] boites = {
                {30, 333, 117, 411}, {159, 326, 339, 427}, {373, 349, 546, 405}, {585, 337, 758, 417},
                {801, 330, 928, 408}, {963, 345, 1147, 398}, {1181, 333, 1307, 417}};
        String[] noms = {"decor_0", "decor_1", "decor_2", "decor_3", "decor_4", "bas_montagnes", "bas_sapins"};
        // taille max en pixels du menu (largeur, hauteur)
        int[][] max = {{27, 13}, {27, 13}, {27, 13}, {27, 13}, {27, 13}, {46, 14}, {40, 15}};
        for (int i = 0; i < noms.length; i++) {
            int[] b = boites[i];
            BufferedImage c = d.getSubimage(b[0], b[1], b[2] - b[0] + 1, b[3] - b[1] + 1);
            double e = Math.min(max[i][0] * F / (double) c.getWidth(), max[i][1] * F / (double) c.getHeight());
            BufferedImage r = seuil(redim(c, (int) Math.round(c.getWidth() * e), (int) Math.round(c.getHeight() * e)), 128);
            ImageIO.write(r, "png", new File(dst, noms[i] + ".png"));
            System.out.println(noms[i] + " " + r.getWidth() + "x" + r.getHeight());
        }
    }

    // ───────────── outils ─────────────

    static int dist(int a, int b) {
        return Math.abs(((a >> 16) & 255) - ((b >> 16) & 255)) + Math.abs(((a >> 8) & 255) - ((b >> 8) & 255))
                + Math.abs((a & 255) - (b & 255));
    }

    /** Couleur du papier dans une zone : la plus frequente (a 16 pres). */
    static int papier(BufferedImage im, int x0, int y0, int x1, int y1) {
        Map<Integer, Integer> n = new HashMap<>();
        for (int y = y0; y <= y1; y++) for (int x = x0; x <= x1; x++) n.merge(im.getRGB(x, y) & 0xF0F0F0, 1, Integer::sum);
        return n.entrySet().stream().max(Map.Entry.comparingByValue()).get().getKey() | 0x080808;
    }

    interface Exclu {
        boolean exclu(int x, int y);
    }

    /** Pixels de la zone qui ne sont pas du papier (le dessin a deplacer), hors exclusion. */
    static boolean[][] masque(BufferedImage im, int x0, int y0, int x1, int y1, int tol, Exclu exclu) {
        int p = papier(im, x0, y0, x1, y1);
        boolean[][] m = new boolean[im.getHeight()][im.getWidth()];
        for (int y = y0; y <= y1; y++)
            for (int x = x0; x <= x1; x++)
                m[y][x] = dist(im.getRGB(x, y), p) > tol && (exclu == null || !exclu.exclu(x, y));
        return m;
    }

    /** Copie du dessin (boite du masque), le reste transparent. */
    static BufferedImage detacher(BufferedImage im, boolean[][] m) {
        int bx0 = Integer.MAX_VALUE, by0 = Integer.MAX_VALUE, bx1 = -1, by1 = -1;
        for (int y = 0; y < m.length; y++)
            for (int x = 0; x < m[0].length; x++)
                if (m[y][x]) {
                    bx0 = Math.min(bx0, x);
                    by0 = Math.min(by0, y);
                    bx1 = Math.max(bx1, x);
                    by1 = Math.max(by1, y);
                }
        System.out.println("  dessin " + bx0 + "," + by0 + " .. " + bx1 + "," + by1);
        BufferedImage o = new BufferedImage(bx1 - bx0 + 1, by1 - by0 + 1, BufferedImage.TYPE_INT_ARGB);
        for (int y = by0; y <= by1; y++)
            for (int x = bx0; x <= bx1; x++)
                if (m[y][x]) o.setRGB(x - bx0, y - by0, 0xFF000000 | im.getRGB(x, y));
        return o;
    }

    /** Efface le dessin (et 3 px autour) en recopiant le papier vierge d'une autre zone, au meme decalage. */
    static void effacer(BufferedImage im, boolean[][] m, int sx, int sy) {
        int bx0 = Integer.MAX_VALUE, by0 = Integer.MAX_VALUE;
        for (int y = 0; y < m.length; y++)
            for (int x = 0; x < m[0].length; x++)
                if (m[y][x]) {
                    bx0 = Math.min(bx0, x);
                    by0 = Math.min(by0, y);
                }
        int r = 3;
        boolean[][] fait = new boolean[m.length][m[0].length];
        for (int y = 0; y < m.length; y++)
            for (int x = 0; x < m[0].length; x++) {
                if (!m[y][x]) continue;
                for (int j = y - r; j <= y + r; j++)
                    for (int i = x - r; i <= x + r; i++) {
                        if (j < 0 || i < 0 || j >= m.length || i >= m[0].length || fait[j][i]) continue;
                        fait[j][i] = true;
                        im.setRGB(i, j, im.getRGB(sx + i - bx0, sy + j - by0));
                    }
            }
    }

    /** Redimensionnement par moyenne de zone (couleurs ponderees par l'opacite). */
    static BufferedImage redim(BufferedImage im, int w, int h) {
        BufferedImage o = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        double sx = im.getWidth() / (double) w, sy = im.getHeight() / (double) h;
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                double a = 0, r = 0, g = 0, b = 0, n = 0;
                int j0 = (int) (y * sy), j1 = Math.max(j0 + 1, (int) ((y + 1) * sy));
                int i0 = (int) (x * sx), i1 = Math.max(i0 + 1, (int) ((x + 1) * sx));
                for (int j = j0; j < j1; j++)
                    for (int i = i0; i < i1; i++) {
                        int c = im.getRGB(Math.min(i, im.getWidth() - 1), Math.min(j, im.getHeight() - 1));
                        double al = (c >>> 24) / 255.0;
                        a += al;
                        r += al * ((c >> 16) & 255);
                        g += al * ((c >> 8) & 255);
                        b += al * (c & 255);
                        n++;
                    }
                if (a == 0) continue;
                int A = (int) Math.round(255 * a / n);
                o.setRGB(x, y, (A << 24) | ((int) Math.round(r / a) << 16) | ((int) Math.round(g / a) << 8) | (int) Math.round(b / a));
            }
        return o;
    }

    static BufferedImage redimHauteur(BufferedImage im, int h) {
        return redim(im, Math.max(1, (int) Math.round(im.getWidth() * h / (double) im.getHeight())), h);
    }

    static BufferedImage redimLargeur(BufferedImage im, int w) {
        return redim(im, w, Math.max(1, (int) Math.round(im.getHeight() * w / (double) im.getWidth())));
    }

    /** Opacite tout ou rien (pas de halo). */
    static BufferedImage seuil(BufferedImage im, int s) {
        for (int y = 0; y < im.getHeight(); y++)
            for (int x = 0; x < im.getWidth(); x++) {
                int c = im.getRGB(x, y);
                im.setRGB(x, y, (c >>> 24) >= s ? c | 0xFF000000 : 0);
            }
        return im;
    }

    static void coller(BufferedImage fond, BufferedImage im, int x0, int y0) {
        seuil(im, 128);
        for (int y = 0; y < im.getHeight(); y++)
            for (int x = 0; x < im.getWidth(); x++)
                if ((im.getRGB(x, y) >>> 24) != 0) fond.setRGB(x0 + x, y0 + y, im.getRGB(x, y));
    }
}
