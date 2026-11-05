package fr.survieplus.pack;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.ShadowColor;
import net.kyori.adventure.text.format.TextColor;

import java.util.ArrayList;
import java.util.List;

/**
 * Assemble le titre d'un menu dessine : chaque element est pose a une position X precise (pixels du menu).
 * Entre deux elements, des espaces positifs ou negatifs amenent le "curseur" du texte au bon endroit.
 */
public final class Toile {

    private final PackMenus pack;
    private final List<Component> morceaux = new ArrayList<>();
    private int curseur = PackMenus.TITRE_X;

    Toile(PackMenus pack) {
        this.pack = pack;
    }

    /** Image (blanche = couleurs d'origine). */
    public Toile image(PackMenus.Glyphe g, int x) {
        return image(g, x, NamedTextColor.WHITE);
    }

    /** Image teintee : les pixels blancs ou gris prennent cette couleur. */
    public Toile image(PackMenus.Glyphe g, int x, TextColor teinte) {
        aller(x);
        morceaux.add(Component.text(g.c(), teinte).font(PackMenus.POLICE).shadowColor(ShadowColor.none()));
        curseur += g.avance();
        return this;
    }

    /** Grande image decoupee en carreaux, son coin haut gauche en x. */
    public Toile mosaique(PackMenus.Mosaique m, int x) {
        for (int r = 0; r * PackMenus.CARREAU < m.hauteur(); r++)
            for (int c = 0; c * PackMenus.CARREAU < m.largeur(); c++)
                image(pack.get(m.id() + "_" + r + "_" + c, m.y() + r * PackMenus.CARREAU), x + c * PackMenus.CARREAU);
        return this;
    }

    /** Texte dont le premier pixel est a x, le haut des lettres a y. */
    public Toile texte(String s, int x, int y, TextColor couleur) {
        if (s.isEmpty()) return this;
        Key police = pack.texte(y);
        aller(x);
        morceaux.add(Component.text(s, couleur).font(police));
        curseur += pack.largeurs().de(s);
        return this;
    }

    /** Texte dont le dernier pixel est a x. */
    public Toile texteDroite(String s, int x, int y, TextColor couleur) {
        return texte(s, x - pack.largeurs().de(s) + 2, y, couleur);
    }

    /** Texte centre sur x. */
    public Toile texteCentre(String s, int x, int y, TextColor couleur) {
        return texte(s, x - (pack.largeurs().de(s) - 1) / 2, y, couleur);
    }

    private void aller(int x) {
        int d = x - curseur;
        if (d != 0) morceaux.add(Component.text(PackMenus.espace(d)).font(PackMenus.POLICE));
        curseur = x;
    }

    /** Le titre complet du menu. */
    public Component fin() {
        TextComponent.Builder b = Component.text();
        morceaux.forEach(b::append);
        return b.build();
    }
}
