import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Decoupe une planche d'icones Higgsfield en textures d'objets du pack (a lancer a la main) :
 *   java outils/PrepIcones.java <image> <dossier> <taille> <nom1> <nom2> ...
 * L'image contient les icones cote a cote sur fond magenta, une par part egale de la largeur, dans l'ordre des noms.
 * Chaque icone est detouree, recadree au carre puis reduite en pixel art (32 x 32 : en 16 x 16 les details
 * deviennent illisibles) ; toutes a la meme echelle. Un apercu agrandi (apercu_*.png) est ecrit dans le dossier
 * parent : a verifier puis supprimer. Planches actuelles :
 *   java outils/PrepIcones.java maquettes/higgsfield/filet.png src/main/resources/pack/objets 32 filet_vide filet_plein
 *   java outils/PrepIcones.java maquettes/higgsfield/bestiaire-icones.png src/main/resources/pack/objets 32 inconnu bestiaire medaille
 *   java outils/PrepIcones.java --contour maquettes/higgsfield/pioche-a.png src/main/resources/pack/objets 32 pioche_pierre pioche_fer pioche_diamant pioche_netherite pioche_maitre
 * --contour : n'efface que le fond relie au bord de l'image (sinon le cristal violet de la pioche, proche du
 * magenta, disparait) ; sans --contour, tout le magenta est efface (les trous des mailles du filet).
 */
public class PrepIcones {

    public static void main(String[] a) throws Exception {
        boolean contour = a.length > 0 && a[0].equals("--contour");
        if (contour) a = java.util.Arrays.copyOfRange(a, 1, a.length);
        BufferedImage src = ImageIO.read(new File(a[0]));
        File dst = new File(a[1]);
        int taille = Integer.parseInt(a[2]);
        String[] noms = java.util.Arrays.copyOfRange(a, 3, a.length);
        dst.mkdirs();
        int n = noms.length, part = src.getWidth() / n;
        BufferedImage[] icones = new BufferedImage[n];
        int[][] cadres = new int[n][];
        int cote = 0;
        for (int i = 0; i < n; i++) {
            icones[i] = detourer(src.getSubimage(i * part, 0, part, src.getHeight()), contour);
            cadres[i] = cadre(icones[i]);
            cote = Math.max(cote, Math.max(cadres[i][2] - cadres[i][0], cadres[i][3] - cadres[i][1]) + 1);
        }
        // Meme echelle pour toutes (le filet ne change pas de taille quand il se remplit)
        for (int i = 0; i < n; i++) {
            BufferedImage icone = reduire(icones[i], cadres[i], cote, taille);
            ImageIO.write(icone, "png", new File(dst, noms[i] + ".png"));
            ImageIO.write(agrandir(icone, 256 / taille), "png", new File(dst.getParentFile(), "apercu_" + noms[i] + ".png"));
        }
    }

    /** Fond magenta -> transparent. */
    static boolean fond(int rgb) {
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        // Magenta pur ou melange au magenta (bord des mailles) : rouge et bleu nettement au-dessus du vert
        return Math.min(r, b) - g > 60 && Math.abs(r - b) < 90;
    }

    static BufferedImage detourer(BufferedImage img, boolean contour) {
        int w = img.getWidth(), h = img.getHeight();
        boolean[] efface = new boolean[w * h];
        if (contour) {
            // Remplissage depuis les bords : seul le fond qui touche le bord est efface
            java.util.ArrayDeque<Integer> pile = new java.util.ArrayDeque<>();
            for (int x = 0; x < w; x++) { pile.push(x); pile.push((h - 1) * w + x); }
            for (int y = 0; y < h; y++) { pile.push(y * w); pile.push(y * w + w - 1); }
            while (!pile.isEmpty()) {
                int i = pile.pop(), x = i % w, y = i / w;
                if (efface[i] || !fond(img.getRGB(x, y))) continue;
                efface[i] = true;
                if (x > 0) pile.push(i - 1);
                if (x < w - 1) pile.push(i + 1);
                if (y > 0) pile.push(i - w);
                if (y < h - 1) pile.push(i + w);
            }
        } else {
            for (int i = 0; i < w * h; i++) efface[i] = fond(img.getRGB(i % w, i / w));
        }
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int i = 0; i < w * h; i++)
            out.setRGB(i % w, i / w, efface[i] ? 0 : (0xFF000000 | img.getRGB(i % w, i / w)));
        return out;
    }

    /** Cadre {x0, y0, x1, y1} de l'icone, sans les petits points isoles (etincelles) qui l'agrandiraient. */
    static int[] cadre(BufferedImage img) {
        int w = img.getWidth(), h = img.getHeight();
        int[] groupe = new int[w * h];
        java.util.List<int[]> cadres = new java.util.ArrayList<>();
        java.util.List<Integer> tailles = new java.util.ArrayList<>();
        java.util.ArrayDeque<Integer> pile = new java.util.ArrayDeque<>();
        for (int depart = 0; depart < w * h; depart++) {
            if (groupe[depart] != 0 || (img.getRGB(depart % w, depart / w) >>> 24) == 0) continue;
            int id = cadres.size() + 1, n = 0;
            int[] c = {w, h, -1, -1};
            groupe[depart] = id;
            pile.push(depart);
            while (!pile.isEmpty()) {
                int i = pile.pop(), x = i % w, y = i / w;
                n++;
                c[0] = Math.min(c[0], x); c[1] = Math.min(c[1], y); c[2] = Math.max(c[2], x); c[3] = Math.max(c[3], y);
                int[][] voisins = {{x + 1, y}, {x - 1, y}, {x, y + 1}, {x, y - 1}};
                for (int[] v : voisins) {
                    if (v[0] < 0 || v[1] < 0 || v[0] >= w || v[1] >= h) continue;
                    int j = v[1] * w + v[0];
                    if (groupe[j] == 0 && (img.getRGB(v[0], v[1]) >>> 24) != 0) {
                        groupe[j] = id;
                        pile.push(j);
                    }
                }
            }
            cadres.add(c);
            tailles.add(n);
        }
        int plusGrand = tailles.stream().max(Integer::compare).orElse(0);
        int[] t = {w, h, -1, -1};
        for (int i = 0; i < cadres.size(); i++)
            if (tailles.get(i) * 20 >= plusGrand) { // les etincelles font moins de 5 % de l'icone
                int[] c = cadres.get(i);
                t[0] = Math.min(t[0], c[0]); t[1] = Math.min(t[1], c[1]); t[2] = Math.max(t[2], c[2]); t[3] = Math.max(t[3], c[3]);
            }
        return t;
    }

    /** Reduit l'image dans le carre de cote "cote" centre sur le cadre : chaque case prend la couleur moyenne de son centre. */
    static BufferedImage reduire(BufferedImage img, int[] c, int cote, int n) {
        double cx = (c[0] + c[2]) / 2.0, cy = (c[1] + c[3]) / 2.0;
        double pas = (double) cote / n;
        BufferedImage out = new BufferedImage(n, n, BufferedImage.TYPE_INT_ARGB);
        for (int ty = 0; ty < n; ty++)
            for (int tx = 0; tx < n; tx++) {
                double sx = cx - cote / 2.0 + tx * pas, sy = cy - cote / 2.0 + ty * pas;
                long r = 0, g = 0, b = 0;
                int pleins = 0, total = 0;
                for (double y = sy + pas * 0.25; y < sy + pas * 0.75; y++)
                    for (double x = sx + pas * 0.25; x < sx + pas * 0.75; x++) {
                        int ix = (int) x, iy = (int) y;
                        total++;
                        if (ix < 0 || iy < 0 || ix >= img.getWidth() || iy >= img.getHeight()) continue;
                        int p = img.getRGB(ix, iy);
                        if ((p >>> 24) == 0) continue;
                        pleins++;
                        r += (p >> 16) & 255; g += (p >> 8) & 255; b += p & 255;
                    }
                if (total == 0 || pleins * 3 < total) continue; // un tiers suffit : garde les fils fins du filet
                out.setRGB(tx, ty, 0xFF000000 | (int) (r / pleins) << 16 | (int) (g / pleins) << 8 | (int) (b / pleins));
            }
        return out;
    }

    static BufferedImage agrandir(BufferedImage img, int f) {
        BufferedImage out = new BufferedImage(img.getWidth() * f, img.getHeight() * f, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < out.getHeight(); y++)
            for (int x = 0; x < out.getWidth(); x++) {
                int p = img.getRGB(x / f, y / f);
                // damier gris sous les pixels transparents, pour voir le detourage
                out.setRGB(x, y, (p >>> 24) != 0 ? p : (((x / 8 + y / 8) & 1) == 0 ? 0xFFBBBBBB : 0xFF888888));
            }
        return out;
    }
}
