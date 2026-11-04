package fr.survieplus;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.Collection;
import java.util.Map;

/** Recolte au clic droit : une culture mure est recoltee et replantee aussitot (une graine est consommee). */
final class Recolte implements Listener {

    /** Culture -> ce qui sert a la replanter. */
    private static final Map<Material, Material> GRAINES = Map.of(
            Material.WHEAT, Material.WHEAT_SEEDS,
            Material.CARROTS, Material.CARROT,
            Material.POTATOES, Material.POTATO,
            Material.BEETROOTS, Material.BEETROOT_SEEDS,
            Material.NETHER_WART, Material.NETHER_WART,
            Material.COCOA, Material.COCOA_BEANS);

    private final SurviePlus plugin;

    Recolte(SurviePlus plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClic(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getHand() != EquipmentSlot.HAND) return;
        if (e.useInteractedBlock() == Event.Result.DENY) return;
        Block b = e.getClickedBlock();
        if (b == null || !mure(b)) return;
        Player p = e.getPlayer();
        if (p.getGameMode() == GameMode.SPECTATOR || !plugin.actif(p, Module.RECOLTE)) return;

        e.setUseInteractedBlock(Event.Result.DENY);
        e.setUseItemInHand(Event.Result.DENY);

        ItemStack outil = p.getInventory().getItemInMainHand();
        if (!recolter(p, b, outil)) return;
        // Fermier de haut niveau : les cultures mures autour sont recoltees aussi
        int rayon = plugin.metiers().rayonRecolte(p);
        for (int dx = -rayon; dx <= rayon; dx++)
            for (int dz = -rayon; dz <= rayon; dz++) {
                Block v = b.getRelative(dx, 0, dz);
                if ((dx != 0 || dz != 0) && mure(v)) recolter(p, v, outil);
            }
        p.swingMainHand();
    }

    private static boolean mure(Block b) {
        return GRAINES.containsKey(b.getType()) && b.getBlockData() instanceof Ageable c
                && c.getAge() >= c.getMaximumAge();
    }

    /** Recolte et replante une culture mure. false si un plugin de protection l'interdit. */
    private boolean recolter(Player p, Block b, ItemStack outil) {
        Material graine = GRAINES.get(b.getType());
        Ageable culture = (Ageable) b.getBlockData();

        // Respecte les plugins de protection : on demande si le joueur a le droit de casser ce bloc
        BlockBreakEvent test = new BlockBreakEvent(b, p);
        test.setDropItems(false);
        if (!test.callEvent()) return false;

        Collection<ItemStack> recolte = b.getDrops(outil, p); // Fortune compris
        if (plugin.metiers().recolteManuelle(p))
            for (ItemStack it : recolte) it.setAmount(Math.min(it.getMaxStackSize(), it.getAmount() * 2));
        boolean graineRetiree = false;
        for (ItemStack it : recolte)
            if (!graineRetiree && it.getType() == graine) {
                it.setAmount(it.getAmount() - 1);
                graineRetiree = true;
            }

        culture.setAge(0);
        b.setBlockData(culture);

        Location centre = b.getLocation().add(0.5, 0.4, 0.5);
        for (ItemStack it : recolte)
            if (it.getAmount() > 0) b.getWorld().dropItemNaturally(centre, it);
        Sound son = b.getType() == Material.NETHER_WART ? Sound.BLOCK_NETHER_WART_BREAK
                : b.getType() == Material.COCOA ? Sound.BLOCK_WOOD_BREAK : Sound.BLOCK_CROP_BREAK;
        b.getWorld().playSound(centre, son, 1f, 1f);
        return true;
    }
}
