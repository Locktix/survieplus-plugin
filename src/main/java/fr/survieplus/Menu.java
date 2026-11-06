package fr.survieplus;

import fr.survieplus.pack.DessinsMenus;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Menu /survieplus.
 * Ligne du milieu : modules personnels (un clic = activer / desactiver pour soi).
 * En bas : reglages du serveur (modifiables par les admins).
 */
final class Menu implements InventoryHolder {

    private static final Map<Integer, Module> EMPLACEMENTS = Map.of(
            10, Module.BUCHERON,
            11, Module.REPLANTER,
            12, Module.FEUILLES,
            13, Module.MINEUR,
            14, Module.RECOLTE,
            15, Module.TOMBE,
            16, Module.TRI,
            17, Module.MINAGE_3X3);

    private static final int SLOT_SOMMEIL = 26;
    private static final int SLOT_METIERS = 4;
    private static final int SLOT_GUIDE = 8;
    private static final int SLOT_BESTIAIRE = 18;
    /** Cases occupees (encadrees dans le fond du menu dessine). */
    static final int[] CASES = {SLOT_METIERS, SLOT_GUIDE, 10, 11, 12, 13, 14, 15, 16, 17, SLOT_BESTIAIRE, SLOT_SOMMEIL};

    private final SurviePlus plugin;
    private final Player joueur;
    private final DessinsMenus dessins;
    private final Inventory inv;

    Menu(SurviePlus plugin, Player joueur) {
        this.plugin = plugin;
        this.joueur = joueur;
        this.dessins = plugin.packs().pour(joueur);
        Component titre = dessins != null ? dessins.titreSp(etats())
                : Component.text("SurviePlus", NamedTextColor.DARK_GREEN);
        this.inv = Bukkit.createInventory(this, 27, titre);
    }

    /** Etat de chaque option dans le menu dessine : coche = actif, croix = coupe, cadenas = indisponible. */
    private Map<Integer, DessinsMenus.Etat> etats() {
        Map<Integer, DessinsMenus.Etat> etats = new HashMap<>();
        EMPLACEMENTS.forEach((slot, m) -> {
            boolean dispo = plugin.activeServeur(m) && joueur.hasPermission(m.permission) && plugin.debloque(joueur, m);
            etats.put(slot, !dispo ? DessinsMenus.Etat.BLOQUE
                    : plugin.choixJoueur(joueur, m) ? DessinsMenus.Etat.ACTIF : DessinsMenus.Etat.COUPE);
        });
        return etats;
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
            ItemStack vitre = objet(Material.GRAY_STAINED_GLASS_PANE, Component.text(" "), List.of(), false);
            for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, vitre);
        }
        EMPLACEMENTS.forEach((slot, m) -> inv.setItem(slot, icone(m)));
        inv.setItem(SLOT_SOMMEIL, iconeSommeil());
        inv.setItem(SLOT_METIERS, iconeMetiers());
        inv.setItem(SLOT_BESTIAIRE, iconeBestiaire());
        inv.setItem(SLOT_GUIDE, objet(Material.WRITTEN_BOOK, texte("Guide du serveur", NamedTextColor.YELLOW), List.of(
                texte("Tout ce que le serveur ajoute", NamedTextColor.GRAY),
                texte("Clic : lire   ·   Maj + clic : recevoir le livre", NamedTextColor.DARK_GRAY)), false));
    }

    private ItemStack iconeMetiers() {
        if (!plugin.metiers().actif() || !joueur.hasPermission(Metiers.PERMISSION))
            return objet(Material.BARRIER, texte("Métiers", NamedTextColor.RED),
                    List.of(texte("Desactive", NamedTextColor.DARK_RED)), false);
        return objet(Material.ENCHANTED_BOOK, texte("Métiers", NamedTextColor.GOLD), List.of(
                texte("Bûcheron, mineur, fermier, pêcheur, chasseur", NamedTextColor.GRAY),
                texte("Clique pour voir ta progression", NamedTextColor.DARK_GRAY)), false);
    }

    private boolean bestiaireDispo() {
        return plugin.bestiaire().actif() && joueur.hasPermission(Capture.PERMISSION);
    }

    @SuppressWarnings("UnstableApiUsage")
    private ItemStack iconeBestiaire() {
        if (!bestiaireDispo())
            return objet(Material.BARRIER, texte("Bestiaire", NamedTextColor.RED),
                    List.of(texte("Desactive", NamedTextColor.DARK_RED)), false);
        var especes = plugin.bestiaire().especes();
        int n = Bestiaire.nombre(plugin.bestiaire().captures(joueur), especes);
        ItemStack it = objet(Material.BOOK, texte("Bestiaire", NamedTextColor.GOLD), List.of(
                texte(n + " / " + especes.size() + " espèces attrapées au filet", NamedTextColor.GRAY),
                texte("Clique pour l'ouvrir", NamedTextColor.DARK_GRAY)), false);
        if (dessins != null) it.setData(io.papermc.paper.datacomponent.DataComponentTypes.ITEM_MODEL,
                fr.survieplus.pack.PackMenus.BESTIAIRE);
        return it;
    }

    private ItemStack icone(Module m) {
        List<Component> lore = new ArrayList<>();
        lore.add(texte(m.description, NamedTextColor.GRAY));
        lore.add(Component.empty());

        if (!plugin.activeServeur(m)) {
            lore.add(texte("Desactive par l'admin", NamedTextColor.DARK_RED));
            return objet(Material.BARRIER, texte(m.nom, NamedTextColor.RED), lore, false);
        }
        if (!joueur.hasPermission(m.permission)) {
            lore.add(texte("Tu n'as pas la permission", NamedTextColor.DARK_RED));
            return objet(Material.BARRIER, texte(m.nom, NamedTextColor.RED), lore, false);
        }

        if (!plugin.debloque(joueur, m)) {
            Metier metier = m.metierRequis();
            lore.add(texte("Débloqué au niveau " + plugin.niveauRequis(m) + " de " + metier.nom, NamedTextColor.GOLD));
            lore.add(texte("Tu es niveau " + plugin.metiers().niveau(joueur.getUniqueId(), metier), NamedTextColor.DARK_GRAY));
            return objet(m.icone, texte(m.nom, NamedTextColor.GRAY), lore, false);
        }

        boolean on = plugin.choixJoueur(joueur, m);
        lore.add(on ? texte("✔ Active", NamedTextColor.GREEN) : texte("✘ Desactive", NamedTextColor.RED));
        lore.add(texte("Clique pour changer", NamedTextColor.DARK_GRAY));
        return objet(m.icone, texte(m.nom, on ? NamedTextColor.GREEN : NamedTextColor.RED), lore, on);
    }

    private ItemStack iconeSommeil() {
        List<Component> lore = new ArrayList<>();
        lore.add(texte("Reglage du serveur", NamedTextColor.GRAY));
        lore.add(Component.empty());
        if (!Sommeil.actif(plugin)) {
            lore.add(texte("Desactive par l'admin (regle vanilla)", NamedTextColor.DARK_RED));
            return objet(Material.BARRIER, texte("Sommeil partage", NamedTextColor.RED), lore, false);
        }
        int pct = Sommeil.pourcentage(plugin);
        lore.add(texte(pct <= 1 ? "Un seul joueur suffit pour passer la nuit"
                : pct + "% des joueurs doivent dormir", NamedTextColor.YELLOW));
        if (joueur.hasPermission("survieplus.admin"))
            lore.add(texte("Clique pour changer (admin)", NamedTextColor.DARK_GRAY));
        return objet(Material.RED_BED, texte("Sommeil partage", NamedTextColor.AQUA), lore, false);
    }

    private void clic(int slot, boolean maj) {
        if (slot == SLOT_GUIDE) {
            joueur.closeInventory();
            if (maj) plugin.guide().donner(joueur);
            else plugin.guide().ouvrir(joueur);
            return;
        }
        if (slot == SLOT_BESTIAIRE) {
            if (bestiaireDispo()) new MenuBestiaire(plugin, joueur, 0).ouvrir();
            return;
        }
        if (slot == SLOT_METIERS) {
            if (plugin.metiers().actif() && joueur.hasPermission(Metiers.PERMISSION))
                new MenuMetiers(plugin, joueur).ouvrir();
            return;
        }
        if (slot == SLOT_SOMMEIL) {
            if (!joueur.hasPermission("survieplus.admin") || !Sommeil.actif(plugin)) return;
            Sommeil.palierSuivant(plugin);
            joueur.playSound(joueur.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1f);
            inv.setItem(slot, iconeSommeil());
            return;
        }
        Module m = EMPLACEMENTS.get(slot);
        if (m == null || !plugin.activeServeur(m) || !joueur.hasPermission(m.permission)
                || !plugin.debloque(joueur, m)) return;
        plugin.basculer(joueur, m);
        boolean on = plugin.choixJoueur(joueur, m);
        joueur.playSound(joueur.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, on ? 1.2f : 0.8f);
        // Menu dessine : la coche ou la croix fait partie du titre, on rouvre le menu pour le mettre a jour
        if (dessins != null) new Menu(plugin, joueur).ouvrir();
        else inv.setItem(slot, icone(m));
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
        cacherAttributs(it);
        return it;
    }

    /** Cache les lignes "Dans la main principale : X en points d'attaque..." des outils. */
    @SuppressWarnings("UnstableApiUsage")
    static void cacherAttributs(ItemStack it) {
        it.setData(io.papermc.paper.datacomponent.DataComponentTypes.TOOLTIP_DISPLAY,
                io.papermc.paper.datacomponent.item.TooltipDisplay.tooltipDisplay()
                        .addHiddenComponents(io.papermc.paper.datacomponent.DataComponentTypes.ATTRIBUTE_MODIFIERS)
                        .build());
    }

    /** Empeche de prendre les objets du menu et gere les clics. */
    static final class Ecouteur implements Listener {
        @EventHandler
        public void onClic(InventoryClickEvent e) {
            if (!(e.getView().getTopInventory().getHolder(false) instanceof Menu menu)) return;
            e.setCancelled(true);
            if (e.getRawSlot() < menu.inv.getSize()) menu.clic(e.getRawSlot(), e.isShiftClick());
        }

        @EventHandler
        public void onGlisser(InventoryDragEvent e) {
            if (e.getView().getTopInventory().getHolder(false) instanceof Menu) e.setCancelled(true);
        }
    }
}
