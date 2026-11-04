package fr.survieplus;

import fr.survieplus.pack.DessinsMenus;
import fr.survieplus.pack.PackMenus;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
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

/** Menu /metiers : niveau, progression, bonus et classement de chaque metier. */
final class MenuMetiers implements InventoryHolder {

    // Menu classique (sans le pack) : 3 lignes
    private static final int[] SLOTS = {11, 12, 13, 14, 15};
    // Menu dessine (avec le pack) : une ligne par metier, boutons en bas
    private static final int[] DESSIN_BAS = DessinsMenus.METIERS_CASES_BAS;

    private final SurviePlus plugin;
    private final Player joueur;
    private final DessinsMenus dessins;
    private final int slotRetour, slotTotal, slotBarre;
    private final Inventory inv;

    MenuMetiers(SurviePlus plugin, Player joueur) {
        this.plugin = plugin;
        this.joueur = joueur;
        this.dessins = plugin.packs().pour(joueur);
        if (dessins != null) {
            slotRetour = DESSIN_BAS[0];
            slotTotal = DESSIN_BAS[1];
            slotBarre = DESSIN_BAS[2];
            this.inv = Bukkit.createInventory(this, 54, titreDessine());
        } else {
            slotRetour = 18;
            slotTotal = 4;
            slotBarre = 22;
            this.inv = Bukkit.createInventory(this, 27, Component.text("Métiers", NamedTextColor.DARK_GREEN));
        }
    }

    private Component titreDessine() {
        Metiers metiers = plugin.metiers();
        List<DessinsMenus.LigneMetier> lignes = new ArrayList<>();
        for (Metier m : Metier.values()) {
            Metiers.Progression pr = metiers.progression(joueur.getUniqueId(), m);
            lignes.add(new DessinsMenus.LigneMetier(m.nom, pr.niveau(), pr.actuel(), pr.requis(), pr.max()));
        }
        return dessins.titreMetiers(metiers.niveauTotal(joueur.getUniqueId()), lignes);
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
        Metier[] metiers = Metier.values();
        if (dessins != null) {
            // Icone du metier a gauche, puis des objets invisibles sur toute la carte : la bulle d'aide
            // s'affiche ou que l'on pose la souris sur la ligne
            for (int i = 0; i < metiers.length; i++) {
                ItemStack icone = icone(metiers[i]);
                inv.setItem(i * 9, icone);
                ItemStack zone = icone.clone();
                zone.setData(io.papermc.paper.datacomponent.DataComponentTypes.ITEM_MODEL, PackMenus.VIDE);
                for (int c = 1; c < 9; c++) inv.setItem(i * 9 + c, zone);
            }
        } else {
            ItemStack vitre = objet(Material.GRAY_STAINED_GLASS_PANE, Component.text(" "), List.of(), false);
            for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, vitre);
            for (int i = 0; i < metiers.length; i++) inv.setItem(SLOTS[i], icone(metiers[i]));
        }
        inv.setItem(slotRetour, objet(Material.ARROW, texte("Retour au menu SurviePlus", NamedTextColor.YELLOW), List.of(), false));
        inv.setItem(slotBarre, iconeBarre());
        inv.setItem(slotTotal, iconeTotal());
    }

    private ItemStack iconeTotal() {
        Metiers metiers = plugin.metiers();
        List<Component> lore = new ArrayList<>();
        lore.add(texte("Somme des niveaux de tes 5 métiers", NamedTextColor.GRAY));
        lore.add(texte("Max : " + metiers.niveauMax() * Metier.values().length, NamedTextColor.DARK_GRAY));
        lore.add(Component.empty());
        lore.add(texte("Classement général", NamedTextColor.GOLD));
        List<Metiers.Place> top = metiers.classementTotal(5);
        if (top.isEmpty()) lore.add(texte("Personne pour l'instant", NamedTextColor.DARK_GRAY));
        for (int i = 0; i < top.size(); i++) {
            Metiers.Place pl = top.get(i);
            boolean moi = pl.nom().equals(joueur.getName());
            lore.add(texte((i + 1) + ". " + pl.nom() + " - niv. " + pl.niveau(), moi ? NamedTextColor.GREEN : NamedTextColor.GRAY));
        }
        return objet(Material.NETHER_STAR,
                texte("Niveau total : " + metiers.niveauTotal(joueur.getUniqueId()), NamedTextColor.GOLD), lore, false);
    }

    private ItemStack icone(Metier m) {
        Metiers metiers = plugin.metiers();
        Metiers.Progression pr = metiers.progression(joueur.getUniqueId(), m);
        List<Component> lore = new ArrayList<>();
        lore.add(texte("Niveau " + pr.niveau() + " / " + metiers.niveauMax(), NamedTextColor.WHITE));
        if (pr.max()) {
            lore.add(texte("Niveau maximum atteint !", NamedTextColor.GOLD));
        } else {
            int plein = (int) Math.round(10.0 * pr.actuel() / pr.requis());
            lore.add(Component.text("■".repeat(plein), NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false)
                    .append(Component.text("■".repeat(10 - plein), NamedTextColor.DARK_GRAY))
                    .append(Component.text("  " + pr.actuel() + " / " + pr.requis() + " XP", NamedTextColor.GRAY)));
        }
        lore.add(Component.empty());
        lore.add(texte("XP en : " + m.gain, NamedTextColor.GRAY));
        lore.add(texte(m.bonus + " : " + Metiers.pourcent(metiers.chanceDouble(pr.niveau())), NamedTextColor.AQUA));
        // Deja obtenu : bonus debloques et titre actuel ; puis ce qui reste a debloquer, par niveau croissant
        record Etape(int niveau, Component texte) {}
        List<Etape> aVenir = new ArrayList<>();
        for (Module mod : Module.values()) {
            int niv = plugin.niveauRequis(mod);
            if (mod.metierRequis() != m || niv <= 0) continue;
            if (pr.niveau() >= niv) lore.add(texte("✔ " + mod.nom, NamedTextColor.GREEN));
            else aVenir.add(new Etape(niv, Component.text(mod.nom)));
        }
        for (int palier = 1; palier <= 2; palier++) {
            int niv = metiers.niveauPalier(palier);
            if (niv <= 0) continue;
            String bonus = m.paliers[palier - 1];
            if (pr.niveau() >= niv) lore.add(texte("✔ " + bonus, NamedTextColor.GREEN));
            else aVenir.add(new Etape(niv, Component.text(bonus)));
        }
        String rang = Metiers.rang(pr.niveau());
        if (rang != null) lore.add(texte("✔ Titre : " + rang + " " + m.nom.toLowerCase(), NamedTextColor.LIGHT_PURPLE));
        int prochain = (pr.niveau() / 10 + 1) * 10;
        String rangSuivant = Metiers.rang(prochain);
        if (rangSuivant != null && prochain <= metiers.niveauMax() && !rangSuivant.equals(rang))
            aVenir.add(new Etape(prochain, Component.text("titre " + rangSuivant + " " + m.nom.toLowerCase())));
        // Prochaine recompense seulement (pour ne pas allonger la liste)
        metiers.recompenses.paliers(m).entrySet().stream()
                .filter(en -> en.getKey() > pr.niveau()).findFirst()
                .ifPresent(en -> {
                    Component objets = Component.text("récompense ");
                    boolean premier = true;
                    for (ItemStack it : en.getValue()) {
                        if (!premier) objets = objets.append(Component.text(" + "));
                        objets = objets.append(Recompenses.nom(it));
                        premier = false;
                    }
                    aVenir.add(new Etape(en.getKey(), objets));
                });
        aVenir.sort(java.util.Comparator.comparingInt(Etape::niveau));
        for (Etape e : aVenir)
            lore.add(texte("Niveau " + e.niveau() + " : ", NamedTextColor.DARK_GRAY).append(e.texte()));

        lore.add(Component.empty());
        lore.add(texte("Classement", NamedTextColor.GOLD));
        List<Metiers.Place> top = metiers.classement(m, 5);
        if (top.isEmpty()) lore.add(texte("Personne pour l'instant", NamedTextColor.DARK_GRAY));
        for (int i = 0; i < top.size(); i++) {
            Metiers.Place pl = top.get(i);
            boolean moi = pl.nom().equals(joueur.getName());
            lore.add(texte((i + 1) + ". " + pl.nom() + " - niv. " + pl.niveau(), moi ? NamedTextColor.GREEN : NamedTextColor.GRAY));
        }
        return objet(m.icone, texte(m.nom, m.couleur), lore, pr.max());
    }

    private ItemStack iconeBarre() {
        Module m = Module.BARRE_METIERS;
        List<Component> lore = new ArrayList<>();
        lore.add(texte(m.description, NamedTextColor.GRAY));
        lore.add(Component.empty());
        boolean on = plugin.choixJoueur(joueur, m);
        lore.add(on ? texte("✔ Active", NamedTextColor.GREEN) : texte("✘ Desactive", NamedTextColor.RED));
        lore.add(texte("Clique pour changer", NamedTextColor.DARK_GRAY));
        return objet(m.icone, texte(m.nom, on ? NamedTextColor.GREEN : NamedTextColor.RED), lore, on);
    }

    private void clic(int slot) {
        if (slot == slotRetour) {
            new Menu(plugin, joueur).ouvrir();
        } else if (slot == slotBarre) {
            plugin.basculer(joueur, Module.BARRE_METIERS);
            joueur.playSound(joueur.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1f);
            inv.setItem(slot, iconeBarre());
        }
    }

    private static Component texte(String s, NamedTextColor couleur) {
        return Component.text(s, couleur).decoration(TextDecoration.ITALIC, false);
    }

    private static ItemStack objet(Material type, Component nom, List<Component> lore, boolean brillant) {
        ItemStack it = new ItemStack(type);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(nom);
        meta.lore(lore);
        if (brillant) meta.setEnchantmentGlintOverride(true);
        it.setItemMeta(meta);
        Menu.cacherAttributs(it);
        return it;
    }

    static final class Ecouteur implements Listener {
        @EventHandler
        public void onClic(InventoryClickEvent e) {
            if (!(e.getView().getTopInventory().getHolder(false) instanceof MenuMetiers menu)) return;
            e.setCancelled(true);
            if (e.getRawSlot() < menu.inv.getSize()) menu.clic(e.getRawSlot());
        }

        @EventHandler
        public void onGlisser(InventoryDragEvent e) {
            if (e.getView().getTopInventory().getHolder(false) instanceof MenuMetiers) e.setCancelled(true);
        }
    }
}
