import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Prepare le fond du bestiaire a partir de l'image Higgsfield (a lancer a la main, quand on change d'image) :
 *   java outils/PrepBestiaire.java maquettes/higgsfield/bestiaire-fond.png src/main/resources/pack/hd
 * (utilise les outils de PrepHD.java, dans le meme dossier). Sorties, 3 fois la resolution du menu :
 * - fond_bestiaire.png : la page du carnet, 184 x 131 pixels du menu (marge de 4 comprise), collee en haut
 *   du fond des metiers (le bois de l'inventaire en dessous reste le meme) ;
 * - bestiaire_plume.png et bestiaire_patte.png : les dessins des coins, retires de la page (les cases les
 *   cacheraient) pour etre poses dans la ligne du bas, entre les boutons.
 */
public class PrepBestiaire {

    static final int F = 3, LARGEUR = 184 * F, HAUTEUR = 131 * F;

    public static void main(String[] a) throws Exception {
        BufferedImage im = ImageIO.read(new File(a[0]));
        File dst = new File(a[1]);
        dst.mkdirs();
        int w = im.getWidth(), h = im.getHeight();
        BufferedImage page = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        page.getGraphics().drawImage(im, 0, 0, null);

        // Dessins des coins (zones en proportion de l'image) : detaches, puis effaces avec du papier pris plus au centre
        BufferedImage plume = deplacer(page, 0.05, 0.07, 0.15, 0.21, 0.25);
        BufferedImage patte = deplacer(page, 0.85, 0.06, 0.96, 0.19, -0.25);

        ImageIO.write(PrepHD.redim(page, LARGEUR, HAUTEUR), "png", new File(dst, "fond_bestiaire.png"));
        double fx = LARGEUR / (double) w, fy = HAUTEUR / (double) h;
        ImageIO.write(petit(plume, fx, fy), "png", new File(dst, "bestiaire_plume.png"));
        ImageIO.write(petit(patte, fx, fy), "png", new File(dst, "bestiaire_patte.png"));
    }

    /** Detache le dessin de la zone (x0..x1, y0..y1 en fraction de l'image) et l'efface avec le papier decale de dx. */
    static BufferedImage deplacer(BufferedImage im, double x0, double y0, double x1, double y1, double dx) {
        int w = im.getWidth(), h = im.getHeight();
        boolean[][] m = PrepHD.masque(im, (int) (x0 * w), (int) (y0 * h), (int) (x1 * w), (int) (y1 * h), 120, null);
        BufferedImage dessin = PrepHD.detacher(im, m);
        int bx = Integer.MAX_VALUE, by = Integer.MAX_VALUE;
        for (int y = 0; y < m.length; y++) for (int x = 0; x < m[0].length; x++) if (m[y][x]) { bx = Math.min(bx, x); by = Math.min(by, y); }
        PrepHD.effacer(im, m, bx + (int) (dx * w), by);
        return dessin;
    }

    static BufferedImage petit(BufferedImage im, double fx, double fy) {
        return PrepHD.seuil(PrepHD.redim(im, (int) Math.round(im.getWidth() * fx), (int) Math.round(im.getHeight() * fy)), 128);
    }
}
