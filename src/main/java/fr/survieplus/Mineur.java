package fr.survieplus;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

/** Minage de filon : accroupi + bon outil sur un minerai = tout le filon est mine. */
final class Mineur implements Listener {

    private final SurviePlus plugin;

    Mineur(SurviePlus plugin) {
        this.plugin = plugin;
    }

    /**
     * "Famille" du bloc pour regrouper un filon : les variantes deepslate vont avec les normales
     * (IRON_ORE et DEEPSLATE_IRON_ORE -> IRON_ORE). null si le bloc n'est pas concerne.
     */
    static String famille(Material m, Set<String> supplementaires) {
        String nom = m.name();
        boolean concerne = nom.endsWith("_ORE") || m == Material.ANCIENT_DEBRIS || supplementaires.contains(nom);
        if (!concerne) return null;
        return nom.startsWith("DEEPSLATE_") ? nom.substring("DEEPSLATE_".length()) : nom;
    }

    private static boolean estOutil(ItemStack it) {
        Material m = it.getType();
        return Tag.ITEMS_PICKAXES.isTagged(m) || Tag.ITEMS_SHOVELS.isTagged(m)
                || Tag.ITEMS_AXES.isTagged(m) || Tag.ITEMS_HOES.isTagged(m);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCasse(BlockBreakEvent e) {
        Player p = e.getPlayer();
        if (Blocs.EN_COURS.contains(p.getUniqueId())) return;
        Block depart = e.getBlock();
        // Lu une seule fois : famille() est appelee pour chaque voisin pendant la recherche du filon
        Set<String> supplementaires = new HashSet<>();
        for (String s : plugin.getConfig().getStringList("mineur.blocs-supplementaires"))
            supplementaires.add(s.trim().toUpperCase(Locale.ROOT));
        String famille = famille(depart.getType(), supplementaires);
        if (famille == null) return;
        if (p.getGameMode() == GameMode.SPECTATOR || !plugin.actif(p, Module.MINEUR)) return;

        var cfg = plugin.getConfig();
        if (cfg.getBoolean("mineur.accroupi-requis", true) && !p.isSneaking()) return;
        // Il faut le bon outil, au bon niveau (ex : pas de diamant avec une pioche en pierre)
        ItemStack outil = p.getInventory().getItemInMainHand();
        if (!estOutil(outil) || !depart.isPreferredTool(outil)) return;

        Predicate<Material> memeFamille = m -> Objects.equals(famille, famille(m, supplementaires));
        int max = plugin.pioche().maxFilon(p, outil, Math.max(1, cfg.getInt("mineur.max-blocs", 64)));
        List<Block> filon = Blocs.connectes(depart, memeFamille, max);
        if (filon.size() < 2) return;

        Material typeOutil = outil.getType();
        List<Block> reste = filon.subList(1, filon.size());
        plugin.getServer().getScheduler().runTask(plugin, () ->
                Blocs.casser(p, reste, memeFamille, it -> it.getType() == typeOutil));
    }
}
