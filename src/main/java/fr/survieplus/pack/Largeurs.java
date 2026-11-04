package fr.survieplus.pack;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Largeur des caractères de la police du jeu (relevée dans le client 26.3), pour aligner le texte
 * à droite ou au centre, et savoir où placer l'élément suivant.
 */
public final class Largeurs {

    private final Map<Integer, Integer> avances = new HashMap<>();

    private Largeurs() {}

    static Largeurs charger() throws IOException {
        Largeurs l = new Largeurs();
        try (InputStream in = Largeurs.class.getResourceAsStream("/pack/largeurs.txt")) {
            if (in == null) throw new IOException("pack/largeurs.txt introuvable dans le plugin");
            BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            for (String ligne; (ligne = r.readLine()) != null; ) {
                if (ligne.isBlank() || ligne.startsWith("#")) continue;
                int eg = ligne.indexOf('=');
                l.avances.put(Integer.parseInt(ligne.substring(0, eg), 16), Integer.parseInt(ligne.substring(eg + 1).trim()));
            }
        }
        return l;
    }

    /** Avance totale du texte (dernier pixel visible = début + largeur - 2, à cause de l'espacement). */
    public int de(String texte) {
        int w = 0;
        for (int i = 0; i < texte.length(); ) {
            int cp = texte.codePointAt(i);
            w += avances.getOrDefault(cp, 6);
            i += Character.charCount(cp);
        }
        return w;
    }

    public boolean connu(int cp) {
        return avances.containsKey(cp);
    }

    /** Coupe le texte avec "…" pour qu'il tienne dans "max" pixels. */
    public String couper(String texte, int max) {
        if (de(texte) <= max) return texte;
        String fin = ".";
        while (!texte.isEmpty() && de(texte + fin) > max) texte = texte.substring(0, texte.length() - 1);
        return texte + fin;
    }
}
