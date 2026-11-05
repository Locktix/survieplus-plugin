package fr.survieplus;

import fr.survieplus.pack.Packs;

import java.nio.file.Path;

/**
 * Lance par build.ps1 -Publier (hors du serveur) : ecrit le pack des menus sous le nom que le plugin
 * cherchera en ligne (survieplus-menus-<empreinte>.zip), pour l'envoyer sur GitHub.
 */
final class ExportPack {

    public static void main(String[] args) throws Exception {
        System.out.println(Packs.exporter(Menu.CASES, Path.of(args.length > 0 ? args[0] : "build/pack")));
    }
}
