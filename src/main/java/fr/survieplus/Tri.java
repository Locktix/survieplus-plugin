package fr.survieplus;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Tri d'inventaire : Maj + clic droit sur une case vide dans un coffre ou dans
 * son inventaire range tout. Dans l'inventaire du joueur, la barre d'action et l'armure ne bougent pas.
 */
final class Tri implements Listener {

    private static final Set<InventoryType> CONTENEURS = Set.of(
            InventoryType.CHEST, InventoryType.BARREL, InventoryType.SHULKER_BOX, InventoryType.ENDER_CHEST,
            InventoryType.DISPENSER, InventoryType.DROPPER, InventoryType.HOPPER);

    /** Cases rangees de l'inventaire du joueur : les 27 cases principales (pas la barre d'action). */
    private static final int JOUEUR_DEBUT = 9, JOUEUR_FIN = 36;

    private static final Comparator<ItemStack> ORDRE = Comparator
            .comparingInt(Tri::categorie)
            .thenComparing(it -> famille(it.getType()))
            .thenComparing(it -> it.getType().name())
            .thenComparing(it -> it.hasItemMeta() ? it.getItemMeta().getAsString() : "")
            .thenComparing(Comparator.comparingInt(ItemStack::getAmount).reversed());

    private final SurviePlus plugin;

    Tri(SurviePlus plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClic(InventoryClickEvent e) {
        boolean declencheur = e.getClick() == ClickType.SHIFT_RIGHT
                && (e.getCurrentItem() == null || e.getCurrentItem().isEmpty());
        if (!declencheur) return;
        if (!(e.getWhoClicked() instanceof Player p) || p.getGameMode() == GameMode.CREATIVE) return;
        if (!e.getCursor().isEmpty() || !plugin.actif(p, Module.TRI)) return;

        Inventory inv = e.getClickedInventory();
        if (inv == null || inv.getHolder(false) instanceof Menu) return;
        int debut, fin;
        if (inv.getType() == InventoryType.PLAYER) {
            debut = JOUEUR_DEBUT;
            fin = JOUEUR_FIN;
        } else if (CONTENEURS.contains(inv.getType())) {
            debut = 0;
            fin = inv.getSize();
        } else return;

        e.setCancelled(true);
        // Au tick suivant, une fois le clic traite par le jeu
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            trier(inv, debut, fin);
            p.updateInventory();
            p.playSound(p.getLocation(), Sound.ITEM_BUNDLE_INSERT, 0.8f, 1f);
        });
    }

    static void trier(Inventory inv, int debut, int fin) {
        // 1. Fusionne les piles identiques incompletes
        List<ItemStack> objets = new ArrayList<>();
        for (int i = debut; i < fin; i++) {
            ItemStack it = inv.getItem(i);
            if (it == null || it.isEmpty()) continue;
            int reste = it.getAmount();
            for (ItemStack f : objets) {
                if (reste == 0) break;
                if (!f.isSimilar(it) || f.getAmount() >= f.getMaxStackSize()) continue;
                int ajout = Math.min(reste, f.getMaxStackSize() - f.getAmount());
                f.setAmount(f.getAmount() + ajout);
                reste -= ajout;
            }
            if (reste > 0) {
                ItemStack copie = it.clone();
                copie.setAmount(reste);
                objets.add(copie);
            }
        }
        // 2. Trie et remet en place
        objets.sort(ORDRE);
        for (int i = debut; i < fin; i++) {
            int n = i - debut;
            inv.setItem(i, n < objets.size() ? objets.get(n) : null);
        }
    }

    /** 0 = outils, armes et armures ; 1 = blocs ; 2 = objets ; 3 = nourriture. */
    private static int categorie(ItemStack it) {
        Material m = it.getType();
        if (m.getMaxDurability() > 0) return 0;
        if (m.isEdible()) return 3;
        if (m.isBlock()) return 1;
        return 2;
    }

    /** Dernier mot du nom (OAK_LOG -> LOG, IRON_INGOT -> INGOT) pour regrouper les objets de meme sorte. */
    private static String famille(Material m) {
        String nom = m.name();
        int i = nom.lastIndexOf('_');
        return i < 0 ? nom : nom.substring(i + 1);
    }
}
