package fr.survieplus;

import fr.survieplus.pack.DessinsMenus;
import fr.survieplus.pack.PackMenus;
import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Menu /bestiaire : une case par espece (oeuf si elle est capturee, page dechiree avec un point d'interrogation sinon),
 * 45 especes par page, puis page precedente, progression et page suivante.
 */
@SuppressWarnings("UnstableApiUsage")
final class MenuBestiaire implements InventoryHolder {

    private static final int PAR_PAGE = DessinsMenus.BESTIAIRE_PAR_PAGE;
    private static final int PRECEDENTE = DessinsMenus.BESTIAIRE_CASES_BAS[0];
    private static final int INFOS = DessinsMenus.BESTIAIRE_CASES_BAS[1];
    private static final int SUIVANTE = DessinsMenus.BESTIAIRE_CASES_BAS[2];

    private final SurviePlus plugin;
    private final Player joueur;
    private final DessinsMenus dessins;
    private final List<EntityType> especes;
    private final Set<String> captures;
    private final int page, pages, nombre;
    private final Inventory inv;

    MenuBestiaire(SurviePlus plugin, Player joueur, int page) {
        this.plugin = plugin;
        this.joueur = joueur;
        this.dessins = plugin.packs().pour(joueur);
        this.especes = plugin.bestiaire().especes();
        this.captures = plugin.bestiaire().captures(joueur);
        this.pages = Math.max(1, (especes.size() + PAR_PAGE - 1) / PAR_PAGE);
        this.page = Math.max(0, Math.min(page, pages - 1));
        this.nombre = Bestiaire.nombre(captures, especes);
        Component titre = dessins != null ? dessins.titreBestiaire(nombre, especes.size(), this.page, pages)
                : Component.text("Bestiaire " + nombre + "/" + especes.size()
                + (pages > 1 ? " (page " + (this.page + 1) + "/" + pages + ")" : ""), NamedTextColor.DARK_GREEN);
        this.inv = Bukkit.createInventory(this, 54, titre);
    }

    void ouvrir() {
        dessiner();
        joueur.openInventory(inv);
    }

    @Override
    public Inventory getInventory() {
        return inv;
    }

    private void dessiner() {
        if (dessins == null) {
            ItemStack vitre = objet(Material.GRAY_STAINED_GLASS_PANE, Component.text(" "), List.of());
            for (int i = PAR_PAGE; i < inv.getSize(); i++) inv.setItem(i, vitre);
        }
        for (int i = 0; i < PAR_PAGE; i++) {
            int n = page * PAR_PAGE + i;
            if (n < especes.size()) inv.setItem(i, icone(especes.get(n)));
        }
        if (page > 0)
            inv.setItem(PRECEDENTE, objet(Material.ARROW, texte("◀ Page précédente", NamedTextColor.YELLOW), List.of()));
        if (page < pages - 1)
            inv.setItem(SUIVANTE, objet(Material.ARROW, texte("Page suivante ▶", NamedTextColor.YELLOW), List.of()));
        inv.setItem(INFOS, infos());
    }

    private ItemStack icone(EntityType t) {
        Component nom = Component.translatable(t.translationKey()).decoration(TextDecoration.ITALIC, false);
        if (Bestiaire.capturee(captures, t))
            return objet(Bestiaire.oeuf(t), nom.color(NamedTextColor.WHITE), List.of(texte("✔ Capturée", NamedTextColor.GREEN)));
        List<Component> lore = new ArrayList<>();
        lore.add(texte("Pas encore capturée", NamedTextColor.DARK_GRAY));
        ItemStack it = objet(dessins != null ? Material.PAPER : Material.GRAY_DYE, nom.color(NamedTextColor.GRAY), lore);
        if (dessins != null) it.setData(DataComponentTypes.ITEM_MODEL, PackMenus.INCONNU);
        return it;
    }

    private ItemStack infos() {
        int total = especes.size();
        List<Component> lore = new ArrayList<>();
        int plein = total == 0 ? 0 : (int) Math.round(10.0 * nombre / total);
        lore.add(Component.text("■".repeat(plein), NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false)
                .append(Component.text("■".repeat(10 - plein), NamedTextColor.DARK_GRAY))
                .append(Component.text("  " + nombre + " / " + total, NamedTextColor.GRAY)));
        lore.add(texte("Attrape une espèce au filet pour l'ajouter", NamedTextColor.GRAY));
        lore.add(Component.empty());
        var paliers = plugin.bestiaire().paliers(total);
        if (paliers.isEmpty() && nombre < total) lore.add(texte("Pas de récompense configurée", NamedTextColor.DARK_GRAY));
        for (var p : paliers.entrySet()) {
            boolean fait = nombre >= p.getKey();
            Component objets = Component.empty();
            boolean premier = true;
            for (ItemStack it : p.getValue()) {
                if (!premier) objets = objets.append(Component.text(", "));
                objets = objets.append(Recompenses.nom(it));
                premier = false;
            }
            String quand = p.getKey() >= total ? "Toutes" : p.getKey() + " espèces";
            lore.add(texte((fait ? "✔ " : "") + quand + " : ", fait ? NamedTextColor.GREEN : NamedTextColor.GOLD)
                    .append(objets.color(fait ? NamedTextColor.DARK_GREEN : NamedTextColor.WHITE)));
        }
        if (plugin.getConfig().getBoolean("bestiaire.medaille", true))
            lore.add(texte((nombre >= total ? "✔ " : "") + "Toutes : la médaille du naturaliste",
                    nombre >= total ? NamedTextColor.GREEN : NamedTextColor.LIGHT_PURPLE));
        lore.add(Component.empty());
        lore.add(texte("Clic : retour au menu SurviePlus", NamedTextColor.DARK_GRAY));
        ItemStack it = objet(Material.BOOK, texte("Bestiaire : " + nombre + " / " + total + " espèces",
                nombre >= total ? NamedTextColor.GOLD : NamedTextColor.YELLOW), lore);
        if (dessins != null) it.setData(DataComponentTypes.ITEM_MODEL, PackMenus.BESTIAIRE);
        return it;
    }

    private void clic(int slot) {
        if (slot == PRECEDENTE && page > 0) tourner(page - 1);
        else if (slot == SUIVANTE && page < pages - 1) tourner(page + 1);
        else if (slot == INFOS) new Menu(plugin, joueur).ouvrir();
    }

    private void tourner(int nouvelle) {
        joueur.playSound(joueur.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.7f, 1f);
        new MenuBestiaire(plugin, joueur, nouvelle).ouvrir();
    }

    private static Component texte(String s, NamedTextColor couleur) {
        return Component.text(s, couleur).decoration(TextDecoration.ITALIC, false);
    }

    private static ItemStack objet(Material type, Component nom, List<Component> lore) {
        ItemStack it = new ItemStack(type);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(nom);
        meta.lore(lore);
        it.setItemMeta(meta);
        Menu.cacherAttributs(it);
        return it;
    }

    static final class Ecouteur implements Listener {
        @EventHandler
        public void onClic(InventoryClickEvent e) {
            if (!(e.getView().getTopInventory().getHolder(false) instanceof MenuBestiaire menu)) return;
            e.setCancelled(true);
            if (e.getRawSlot() < menu.inv.getSize()) menu.clic(e.getRawSlot());
        }

        @EventHandler
        public void onGlisser(InventoryDragEvent e) {
            if (e.getView().getTopInventory().getHolder(false) instanceof MenuBestiaire) e.setCancelled(true);
        }
    }
}
