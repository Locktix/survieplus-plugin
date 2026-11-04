package fr.survieplus;

import org.bukkit.GameRules;
import org.bukkit.World;

/** Sommeil partage : regle la regle de jeu vanilla playersSleepingPercentage depuis la config. */
final class Sommeil {

    private static final int[] PALIERS = {1, 25, 50, 75, 100};

    private Sommeil() {}

    static boolean actif(SurviePlus plugin) {
        return plugin.getConfig().getBoolean("sommeil.active", true);
    }

    static int pourcentage(SurviePlus plugin) {
        return Math.clamp(plugin.getConfig().getInt("sommeil.pourcentage", 50), 0, 100);
    }

    static void appliquer(SurviePlus plugin) {
        if (!actif(plugin)) return;
        int pct = pourcentage(plugin);
        for (World w : plugin.getServer().getWorlds())
            if (w.getEnvironment() == World.Environment.NORMAL)
                w.setGameRule(GameRules.PLAYERS_SLEEPING_PERCENTAGE, pct);
    }

    /** Passe au palier suivant (1 -> 25 -> 50 -> 75 -> 100 -> 1), sauvegarde et applique. */
    static void palierSuivant(SurviePlus plugin) {
        int actuel = pourcentage(plugin);
        int suivant = PALIERS[0];
        for (int p : PALIERS)
            if (p > actuel) { suivant = p; break; }
        plugin.getConfig().set("sommeil.pourcentage", suivant);
        plugin.saveConfig();
        appliquer(plugin);
    }
}
