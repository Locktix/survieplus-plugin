package fr.survieplus;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/** Abattage d'arbre : accroupi + hache sur une buche = tout l'arbre tombe. */
final class Bucheron implements Listener {

    private final SurviePlus plugin;

    Bucheron(SurviePlus plugin) {
        this.plugin = plugin;
    }

    static boolean estTronc(Material m) {
        return Tag.LOGS.isTagged(m) || m == Material.MUSHROOM_STEM;
    }

    /** Feuilles naturelles, blocs de verrues du Nether, champilampes, chapeaux de champignons geants. */
    private static boolean estFeuillage(Block b) {
        Material m = b.getType();
        if (Tag.LEAVES.isTagged(m)) {
            BlockData d = b.getBlockData();
            return !(d instanceof Leaves l) || !l.isPersistent();
        }
        return Tag.WART_BLOCKS.isTagged(m) || m == Material.SHROOMLIGHT
                || m == Material.RED_MUSHROOM_BLOCK || m == Material.BROWN_MUSHROOM_BLOCK;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCasse(BlockBreakEvent e) {
        Player p = e.getPlayer();
        Block depart = e.getBlock();
        Material type = depart.getType();
        if (Blocs.EN_COURS.contains(p.getUniqueId()) || !estTronc(type)) return;
        if (p.getGameMode() == GameMode.SPECTATOR || !plugin.actif(p, Module.BUCHERON)) return;

        var cfg = plugin.getConfig();
        if (cfg.getBoolean("bucheron.accroupi-requis", true) && !p.isSneaking()) return;
        Predicate<ItemStack> hache = it -> Tag.ITEMS_AXES.isTagged(it.getType());
        if (!hache.test(p.getInventory().getItemInMainHand())) return;

        List<Block> tronc = Blocs.connectes(depart, m -> m == type, Math.max(1, cfg.getInt("bucheron.max-blocs", 200)));
        if (tronc.size() < 2) return;
        if (compterFeuillage(tronc, cfg.getInt("bucheron.feuilles-min", 4)) < cfg.getInt("bucheron.feuilles-min", 4)) return;

        // Emplacements ou replanter : les buches les plus basses (gere aussi les arbres 2x2)
        int yMin = tronc.stream().mapToInt(Block::getY).min().orElse(depart.getY());
        List<Block> base = tronc.stream().filter(b -> b.getY() == yMin).toList();
        Material pousse = pousse(type);
        boolean replanter = pousse != null && plugin.actif(p, Module.REPLANTER);

        List<Block> feuilles = plugin.actif(p, Module.FEUILLES) ? feuillesAutour(tronc) : List.of();

        List<Block> reste = tronc.subList(1, tronc.size());
        // Au tick suivant : le bloc d'origine est alors casse normalement par le jeu
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Blocs.casser(p, reste, m -> m == type, hache);
            if (replanter) {
                BlockData donnees = pousse.createBlockData();
                for (Block b : base)
                    if (b.getType().isAir() && donnees.isSupported(b)) b.setBlockData(donnees);
            }
            if (!feuilles.isEmpty()) faireTomber(feuilles);
        });
    }

    // ------------------------------------------------------------ feuilles

    private static boolean feuilleNaturelle(Block b) {
        return Tag.LEAVES.isTagged(b.getType()) && b.getBlockData() instanceof Leaves l && !l.isPersistent();
    }

    /** Feuilles naturelles reliees au tronc, a 7 blocs max (portee vanilla des feuilles). */
    private static List<Block> feuillesAutour(List<Block> tronc) {
        List<Block> resultat = new ArrayList<>();
        Set<Block> vus = new HashSet<>(tronc);
        ArrayDeque<Block> file = new ArrayDeque<>();
        for (Block b : tronc) {
            for (Block v : voisins(b))
                if (vus.add(v) && feuilleNaturelle(v)) { file.add(v); resultat.add(v); }
        }
        for (int etape = 1; etape < 7 && !file.isEmpty() && resultat.size() < MAX_FEUILLES; etape++) {
            ArrayDeque<Block> suivante = new ArrayDeque<>();
            for (Block b : file)
                for (Block v : voisins(b))
                    if (vus.add(v) && feuilleNaturelle(v)) { suivante.add(v); resultat.add(v); }
            file = suivante;
        }
        return resultat;
    }

    private static final int MAX_FEUILLES = 1500;

    private static List<Block> voisins(Block b) {
        return List.of(b.getRelative(1, 0, 0), b.getRelative(-1, 0, 0), b.getRelative(0, 1, 0),
                b.getRelative(0, -1, 0), b.getRelative(0, 0, 1), b.getRelative(0, 0, -1));
    }

    /**
     * Laisse le jeu recalculer la distance des feuilles au tronc le plus proche, puis fait tomber
     * celles qui ne sont plus reliees a rien (distance 7), par vagues pour l'effet visuel.
     */
    private void faireTomber(List<Block> feuilles) {
        List<Block> file = new ArrayList<>(feuilles);
        Collections.shuffle(file);
        int parTick = Math.max(1, file.size() / 8);
        int[] i = {0};
        plugin.getServer().getScheduler().runTaskTimer(plugin, tache -> {
            for (int n = 0; n < parTick && i[0] < file.size(); n++, i[0]++) {
                Block b = file.get(i[0]);
                if (!(feuilleNaturelle(b) && b.getBlockData() instanceof Leaves l && l.getDistance() >= 7)) continue;
                LeavesDecayEvent evt = new LeavesDecayEvent(b);
                plugin.getServer().getPluginManager().callEvent(evt);
                if (!evt.isCancelled()) b.breakNaturally(true);
            }
            if (i[0] >= file.size()) tache.cancel();
        }, 10L, 2L);
    }

    /** Compte le feuillage autour du tronc (s'arrete des que le minimum est atteint). */
    private static int compterFeuillage(List<Block> tronc, int minimum) {
        if (minimum <= 0) return 0;
        Set<Block> vus = new HashSet<>();
        for (Block b : tronc)
            for (int dx = -1; dx <= 1; dx++)
                for (int dy = -1; dy <= 1; dy++)
                    for (int dz = -1; dz <= 1; dz++) {
                        Block v = b.getRelative(dx, dy, dz);
                        if (estFeuillage(v) && vus.add(v) && vus.size() >= minimum) return vus.size();
                    }
        return vus.size();
    }

    /** La pousse correspondant au tronc, ou null (troncs ecorces, bois, champignons...). */
    static Material pousse(Material tronc) {
        if (tronc == Material.MANGROVE_LOG) return Material.MANGROVE_PROPAGULE;
        if (tronc == Material.CRIMSON_STEM) return Material.CRIMSON_FUNGUS;
        if (tronc == Material.WARPED_STEM) return Material.WARPED_FUNGUS;
        String nom = tronc.name();
        if (nom.startsWith("STRIPPED_") || !nom.endsWith("_LOG")) return null;
        return Material.matchMaterial(nom.substring(0, nom.length() - 4) + "_SAPLING");
    }
}
