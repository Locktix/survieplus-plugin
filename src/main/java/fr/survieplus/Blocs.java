package fr.survieplus;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/** Outils communs a l'abattage et au minage de filon. */
final class Blocs {

    /** Joueurs dont on est en train de casser des blocs (pour ne pas se re-declencher en boucle). */
    static final Set<UUID> EN_COURS = new HashSet<>();

    private Blocs() {}

    /** Blocs relies au depart (diagonales comprises), du plus proche au plus loin. Le depart est le premier. */
    static List<Block> connectes(Block depart, Predicate<Material> accepte, int max) {
        List<Block> resultat = new ArrayList<>();
        Set<Block> vus = new HashSet<>();
        ArrayDeque<Block> file = new ArrayDeque<>();
        file.add(depart);
        vus.add(depart);
        while (!file.isEmpty() && resultat.size() < max) {
            Block b = file.poll();
            resultat.add(b);
            for (int dx = -1; dx <= 1; dx++)
                for (int dy = -1; dy <= 1; dy++)
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        Block v = b.getRelative(dx, dy, dz);
                        if (vus.add(v) && accepte.test(v.getType())) file.add(v);
                    }
        }
        return resultat;
    }

    /**
     * Casse les blocs comme si le joueur les cassait lui-meme : enchantements (Fortune, Toucher de soie),
     * XP, usure de l'outil et plugins de protection sont respectes.
     * S'arrete si l'outil n'est plus valide ou va casser.
     */
    static int casser(Player p, List<Block> blocs, Predicate<Material> typeAttendu, Predicate<ItemStack> outilValide) {
        if (!p.isOnline()) return 0;
        int n = 0;
        EN_COURS.add(p.getUniqueId());
        try {
            for (Block b : blocs) {
                ItemStack outil = p.getInventory().getItemInMainHand();
                if (!outilValide.test(outil) || vaCasser(p, outil)) break;
                if (!typeAttendu.test(b.getType())) continue;
                if (p.breakBlock(b)) n++;
            }
        } finally {
            EN_COURS.remove(p.getUniqueId());
        }
        return n;
    }

    /** true si l'outil n'a plus qu'un point de durabilite (on le preserve). */
    static boolean vaCasser(Player p, ItemStack outil) {
        if (p.getGameMode() == GameMode.CREATIVE) return false;
        ItemMeta meta = outil.getItemMeta();
        if (!(meta instanceof Damageable d) || meta.isUnbreakable()) return false;
        int max = d.hasMaxDamage() ? d.getMaxDamage() : outil.getType().getMaxDurability();
        return max > 0 && max - d.getDamage() <= 1;
    }
}
