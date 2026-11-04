package fr.survieplus.pack;

import java.awt.image.BufferedImage;

/** Petit outil de pixel art : on dessine pixel par pixel dans une image ARGB. */
final class Dessin {

    final BufferedImage img;
    final int w, h;

    Dessin(int w, int h) {
        this.w = w;
        this.h = h;
        this.img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
    }

    static int argb(int a, int rgb) {
        return (Math.max(0, Math.min(255, a)) << 24) | (rgb & 0xFFFFFF);
    }

    /** Gris (v de 0 à 1) avec une opacité : utile pour les images teintées par la couleur du texte. */
    static int gris(double v, int a) {
        int g = (int) Math.round(Math.max(0, Math.min(1, v)) * 255);
        return argb(a, (g << 16) | (g << 8) | g);
    }

    void px(int x, int y, int argb) {
        if (x >= 0 && y >= 0 && x < w && y < h) img.setRGB(x, y, argb);
    }

    void rect(int x, int y, int rw, int rh, int argb) {
        for (int j = y; j < y + rh; j++) for (int i = x; i < x + rw; i++) px(i, j, argb);
    }

    /** Rectangle aux coins coupés (rayon 1 ou 2). */
    void arrondi(int x, int y, int rw, int rh, int rayon, int argb) {
        for (int j = 0; j < rh; j++) {
            for (int i = 0; i < rw; i++) {
                int dx = Math.min(i, rw - 1 - i), dy = Math.min(j, rh - 1 - j);
                if (dx + dy < rayon) continue;
                px(x + i, y + j, argb);
            }
        }
    }

    /**
     * Motif en texte : chaque caractère est un pixel, la palette donne sa couleur ('.' ou ' ' = vide).
     */
    void motif(int x, int y, String[] lignes, char[] cles, int[] couleurs) {
        for (int j = 0; j < lignes.length; j++) {
            String l = lignes[j];
            for (int i = 0; i < l.length(); i++) {
                char c = l.charAt(i);
                for (int k = 0; k < cles.length; k++) {
                    if (cles[k] == c) px(x + i, y + j, couleurs[k]);
                }
            }
        }
    }

    /** Avance du glyphe pour le client : dernière colonne non transparente + 1 (espacement). */
    int avance() {
        for (int x = w - 1; x >= 0; x--) {
            for (int y = 0; y < h; y++) {
                if ((img.getRGB(x, y) >>> 24) != 0) return x + 2;
            }
        }
        return 1;
    }
}
