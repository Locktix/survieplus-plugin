package fr.survieplus;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Un nouveau jar ne remplace jamais une valeur deja ecrite dans le config.yml du serveur. Quand une version change
 * un reglage par defaut, on passe donc a la nouvelle valeur seulement celles restees a l'ancien defaut :
 * un reglage modifie par l'admin n'est pas touche.
 */
final class MajConfig {

    private MajConfig() {}

    /** Ancien defaut de chaque reglage dont le defaut a change (1.4.0 : XP des minerais, recompenses du mineur, vision de la pioche). */
    static final Map<String, Object> ANCIENS_DEFAUTS = Map.ofEntries(
            Map.entry("metiers.xp.mineur.COAL_ORE", 3L),
            Map.entry("metiers.xp.mineur.COPPER_ORE", 2L),
            Map.entry("metiers.xp.mineur.IRON_ORE", 5L),
            Map.entry("metiers.xp.mineur.GOLD_ORE", 7L),
            Map.entry("metiers.xp.mineur.NETHER_GOLD_ORE", 2L),
            Map.entry("metiers.xp.mineur.REDSTONE_ORE", 3L),
            Map.entry("metiers.xp.mineur.LAPIS_ORE", 6L),
            Map.entry("metiers.xp.mineur.NETHER_QUARTZ_ORE", 3L),
            Map.entry("metiers.xp.mineur.DIAMOND_ORE", 15L),
            Map.entry("metiers.xp.mineur.EMERALD_ORE", 20L),
            Map.entry("metiers.xp.mineur.ANCIENT_DEBRIS", 25L),
            // La pioche du mineur evolue toute seule : plus de pioches offertes, des ressources a la place
            Map.entry("metiers.recompenses.mineur.10", List.of("IRON_PICKAXE efficiency:2 unbreaking:1")),
            Map.entry("metiers.recompenses.mineur.25", List.of("DIAMOND_PICKAXE efficiency:3 fortune:2")),
            // Vision nocturne de la pioche : sous Y = 0 au debut des tests de la 1.4.0, puis sous terre (Y <= 62)
            Map.entry("pioche.vision-y", 0L));

    /**
     * A appeler avant copyDefaults(true) (sinon isSet() voit aussi les valeurs par defaut).
     * Renvoie le nombre de reglages mis a jour.
     */
    static int appliquer(FileConfiguration c) {
        var defauts = c.getDefaults();
        if (defauts == null) return 0;
        int n = 0;
        for (var e : ANCIENS_DEFAUTS.entrySet()) {
            String cle = e.getKey();
            if (!c.isSet(cle) || !egal(c, cle, e.getValue())) continue;
            Object nouveau = defauts.get(cle);
            if (nouveau == null || egal(defauts, cle, e.getValue())) continue;
            c.set(cle, nouveau);
            n++;
        }
        return n;
    }

    private static boolean egal(org.bukkit.configuration.ConfigurationSection c, String cle, Object ancien) {
        if (ancien instanceof List<?> liste) return c.isList(cle) && Objects.equals(c.getStringList(cle), liste);
        return (c.isInt(cle) || c.isLong(cle)) && c.getLong(cle) == ((Number) ancien).longValue();
    }
}
