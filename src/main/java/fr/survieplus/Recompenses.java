package fr.survieplus;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Objets donnes quand on atteint certains niveaux de metier.
 * Dans config.yml : metiers.recompenses.<metier>.<niveau> = liste de "MATERIAU [quantite] [enchantement:niveau ...]".
 */
final class Recompenses {

    private final SurviePlus plugin;
    private final Set<String> dejaSignales = new HashSet<>();

    Recompenses(SurviePlus plugin) {
        this.plugin = plugin;
    }

    /** Niveau -> objets, pour ce metier, par niveau croissant. */
    SortedMap<Integer, List<ItemStack>> paliers(Metier m) {
        SortedMap<Integer, List<ItemStack>> res = new TreeMap<>();
        ConfigurationSection s = plugin.getConfig().getConfigurationSection("metiers.recompenses." + m.cle);
        if (s == null) return res;
        for (String cle : s.getKeys(false)) {
            int niveau;
            try {
                niveau = Integer.parseInt(cle.trim());
            } catch (NumberFormatException e) {
                signaler("niveau invalide '" + cle + "' pour " + m.cle);
                continue;
            }
            List<ItemStack> objets = new ArrayList<>();
            for (String texte : s.getStringList(cle)) {
                ItemStack it = lire(texte);
                if (it != null) objets.add(it);
            }
            if (!objets.isEmpty()) res.put(niveau, objets);
        }
        return res;
    }

    /** Objet decrit par "MATERIAU [quantite] [enchantement:niveau ...]", ou null (signale dans la console). */
    ItemStack lire(String texte) {
        String[] t = texte.trim().split("\\s+");
        Material mat = Material.matchMaterial(t[0]);
        if (mat == null || !mat.isItem() || mat.isAir()) {
            signaler("objet inconnu '" + t[0] + "'");
            return null;
        }
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        var registre = RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT);
        for (int i = 1; i < t.length; i++) {
            String mot = t[i];
            if (mot.matches("\\d+")) {
                it.setAmount(Math.max(1, Math.min(mat.getMaxStackSize(), Integer.parseInt(mot))));
                continue;
            }
            String[] e = mot.split(":");
            Enchantment ench = registre.get(NamespacedKey.minecraft(e[0].toLowerCase(Locale.ROOT)));
            int niv = 1;
            try {
                if (e.length > 1) niv = Integer.parseInt(e[1]);
            } catch (NumberFormatException ex) {
                ench = null;
            }
            if (ench == null) {
                signaler("enchantement invalide '" + mot + "' dans '" + texte + "'");
                continue;
            }
            if (meta instanceof EnchantmentStorageMeta livre) livre.addStoredEnchant(ench, niv, true);
            else meta.addEnchant(ench, niv, true);
        }
        it.setItemMeta(meta);
        return it;
    }

    private void signaler(String probleme) {
        if (dejaSignales.add(probleme))
            plugin.getLogger().warning("config.yml, recompenses : " + probleme);
    }

    /** Donne les recompenses des niveaux franchis (avant exclu, apres inclus). */
    void donner(Player p, Metier m, int avant, int apres) {
        for (var entree : paliers(m).subMap(avant + 1, apres + 1).entrySet()) {
            for (ItemStack it : entree.getValue()) {
                remettre(p, it);
                p.sendMessage(SurviePlus.PREFIXE
                        .append(Component.text("Récompense " + m.nom + " niv. " + entree.getKey() + " : ", NamedTextColor.GOLD))
                        .append(it.displayName())
                        .append(Component.text(it.getAmount() > 1 ? " x" + it.getAmount() : "", NamedTextColor.WHITE)));
            }
            p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.8f, 0.8f);
        }
    }

    /** Met l'objet dans l'inventaire du joueur (a ses pieds s'il est plein). */
    static void remettre(Player p, ItemStack it) {
        p.getInventory().addItem(it.clone()).values().forEach(reste -> p.getWorld().dropItem(p.getLocation(), reste));
    }

    /** Nom de l'objet (traduit par le client) + quantite. */
    static Component nom(ItemStack it) {
        Component c = Component.translatable(it.translationKey());
        return it.getAmount() > 1 ? c.append(Component.text(" x" + it.getAmount())) : c;
    }

    /** Liste des enchantements, ex : "Efficacite III, Solidite II" (vide si aucun). */
    static Component enchantements(ItemStack it) {
        Map<Enchantment, Integer> e = it.getItemMeta() instanceof EnchantmentStorageMeta livre
                ? livre.getStoredEnchants() : it.getEnchantments();
        Component c = Component.empty();
        boolean premier = true;
        for (var entree : e.entrySet()) {
            if (!premier) c = c.append(Component.text(", "));
            c = c.append(entree.getKey().displayName(entree.getValue()));
            premier = false;
        }
        return c;
    }
}
