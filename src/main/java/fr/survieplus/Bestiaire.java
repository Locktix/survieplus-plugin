package fr.survieplus;

import fr.survieplus.pack.PackMenus;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.CustomModelData;
import io.papermc.paper.datacomponent.item.ItemLore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Boss;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemRarity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Bestiaire : chaque espece attrapee au filet de capture s'y ajoute (une fois). Recompenses aux paliers
 * (bestiaire.paliers), XP de Chasseur pour chaque nouvelle espece, medaille du naturaliste quand tout est trouve.
 * Les especes capturees sont gardees dans les donnees du joueur.
 */
@SuppressWarnings("UnstableApiUsage")
final class Bestiaire {

    private final SurviePlus plugin;
    private final NamespacedKey cle;
    private final NamespacedKey cleMedaille;
    private final Set<String> dejaSignales = new HashSet<>();

    Bestiaire(SurviePlus plugin) {
        this.plugin = plugin;
        cle = new NamespacedKey(plugin, "bestiaire");
        cleMedaille = new NamespacedKey(plugin, "medaille");
    }

    boolean actif() {
        var cfg = plugin.getConfig();
        return cfg.getBoolean("capture.active", true) && cfg.getBoolean("bestiaire.active", true);
    }

    // ------------------------------------------------------------ especes

    /** Especes a trouver, dans l'ordre du bestiaire : les animaux, puis les monstres (par identifiant). */
    List<EntityType> especes() {
        var cfg = plugin.getConfig();
        Set<String> interdits = new HashSet<>();
        for (String s : cfg.getStringList("capture.interdits")) interdits.add(s.trim().toUpperCase(Locale.ROOT));
        boolean villageois = cfg.getBoolean("capture.villageois", true);
        World monde = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().getFirst();
        List<EntityType> liste = new ArrayList<>();
        for (EntityType t : EntityType.values()) {
            Class<?> c = t.getEntityClass();
            if (c == null || !t.isSpawnable() || !t.isAlive() || !Mob.class.isAssignableFrom(c) || Boss.class.isAssignableFrom(c))
                continue;
            if (interdits.contains(t.name()) || (!villageois && t == EntityType.VILLAGER)) continue;
            if (monde != null && !monde.getFeatureFlags().containsAll(t.requiredFeatures())) continue; // mobs experimentaux non actives
            if (oeuf(t) == null) continue; // sans oeuf : jamais dans la nature (geant, illusionniste...)
            liste.add(t);
        }
        liste.sort(Comparator.comparing((EntityType t) -> monstre(t)).thenComparing(t -> t.getKey().getKey()));
        return liste;
    }

    static boolean monstre(EntityType t) {
        return t.getEntityClass() != null && Enemy.class.isAssignableFrom(t.getEntityClass());
    }

    static Material oeuf(EntityType t) {
        return Material.matchMaterial(t.name() + "_SPAWN_EGG");
    }

    // ------------------------------------------------------------ captures du joueur

    Set<String> captures(Player p) {
        List<String> l = p.getPersistentDataContainer().get(cle, PersistentDataType.LIST.strings());
        return l == null ? new LinkedHashSet<>() : new LinkedHashSet<>(l);
    }

    static boolean capturee(Set<String> captures, EntityType t) {
        return captures.contains(t.getKey().toString());
    }

    /** Especes du bestiaire actuel deja capturees (une espece retiree de la liste ne compte plus). */
    static int nombre(Set<String> captures, List<EntityType> especes) {
        return (int) especes.stream().filter(t -> capturee(captures, t)).count();
    }

    /** Appele apres chaque capture au filet. */
    void enregistrer(Player p, EntityType t) {
        if (!actif()) return;
        List<EntityType> especes = especes();
        if (!especes.contains(t)) return;
        Set<String> captures = captures(p);
        if (!captures.add(t.getKey().toString())) return;
        p.getPersistentDataContainer().set(cle, PersistentDataType.LIST.strings(), new ArrayList<>(captures));

        int total = especes.size(), apres = nombre(captures, especes), avant = apres - 1;
        p.sendMessage(SurviePlus.PREFIXE
                .append(Component.text("Nouvelle espèce dans ton bestiaire : ", NamedTextColor.GREEN))
                .append(Component.translatable(t.translationKey(), NamedTextColor.YELLOW))
                .append(Component.text(" (" + apres + "/" + total + ")", NamedTextColor.GRAY)));
        p.playSound(p.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1f, 1f);

        var cfg = plugin.getConfig();
        long xp = cfg.getLong("bestiaire.xp-chasseur", 30);
        if (xp > 0 && p.getGameMode() != GameMode.CREATIVE && plugin.metiers().actif() && p.hasPermission(Metiers.PERMISSION))
            plugin.metiers().ajouter(p, Metier.CHASSEUR, xp, false);

        for (var palier : paliers(total).subMap(avant + 1, apres + 1).entrySet()) {
            for (ItemStack it : palier.getValue()) {
                Recompenses.remettre(p, it);
                p.sendMessage(SurviePlus.PREFIXE
                        .append(Component.text("Récompense du bestiaire (" + palier.getKey() + " espèces) : ", NamedTextColor.GOLD))
                        .append(Recompenses.nom(it).color(NamedTextColor.WHITE)));
            }
            if (palier.getKey() < total)
                Bukkit.broadcast(SurviePlus.PREFIXE.append(Component.text(p.getName() + " a capturé "
                        + palier.getKey() + " espèces différentes !", NamedTextColor.GREEN)));
        }
        if (apres >= total) {
            if (cfg.getBoolean("bestiaire.medaille", true)) Recompenses.remettre(p, medaille(p, total));
            Bukkit.broadcast(SurviePlus.PREFIXE.append(Component.text(p.getName() + " a complété le bestiaire : les "
                    + total + " espèces ! ", NamedTextColor.GOLD)));
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        }
    }

    // ------------------------------------------------------------ recompenses

    /** Nombre d'especes -> objets (bestiaire.paliers ; "tout" = toutes les especes). */
    SortedMap<Integer, List<ItemStack>> paliers(int total) {
        SortedMap<Integer, List<ItemStack>> res = new TreeMap<>();
        ConfigurationSection s = plugin.getConfig().getConfigurationSection("bestiaire.paliers");
        if (s == null) return res;
        for (String k : s.getKeys(false)) {
            int n = seuil(k, total);
            if (n <= 0) {
                if (dejaSignales.add(k))
                    plugin.getLogger().warning("config.yml, bestiaire.paliers : '" + k + "' n'est ni un nombre ni \"tout\"");
                continue;
            }
            List<ItemStack> objets = s.getStringList(k).stream()
                    .map(plugin.metiers().recompenses::lire).filter(Objects::nonNull).toList();
            if (objets.isEmpty()) continue;
            res.computeIfAbsent(n, x -> new ArrayList<>()).addAll(objets);
        }
        return res;
    }

    /** Palier "10" -> 10, "tout" -> total (un palier au-dela du total compte comme "tout") ; -1 si illisible. */
    static int seuil(String cle, int total) {
        String c = cle.trim().toLowerCase(Locale.ROOT);
        if (c.equals("tout") || c.equals("toutes")) return total;
        try {
            int n = Integer.parseInt(c);
            return n <= 0 ? -1 : Math.min(n, total);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** Medaille du naturaliste : objet souvenir, sans usage (texture Higgsfield avec le pack, pepite d'or sinon). */
    ItemStack medaille(Player p, int total) {
        ItemStack it = new ItemStack(Material.FIREWORK_STAR);
        it.setData(DataComponentTypes.MAX_STACK_SIZE, 1);
        it.setData(DataComponentTypes.ITEM_MODEL, PackMenus.MODELE_MEDAILLE);
        it.setData(DataComponentTypes.CUSTOM_MODEL_DATA, CustomModelData.customModelData().addString(PackMenus.MEDAILLE).build());
        it.setData(DataComponentTypes.ITEM_NAME, Component.text("Médaille du naturaliste", NamedTextColor.GOLD));
        it.setData(DataComponentTypes.RARITY, ItemRarity.EPIC);
        it.setData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
        it.setData(DataComponentTypes.LORE, ItemLore.lore(List.of(
                ligne("Décernée à " + p.getName(), NamedTextColor.GRAY),
                ligne("pour les " + total + " espèces du bestiaire", NamedTextColor.GRAY))));
        it.editMeta(m -> m.getPersistentDataContainer().set(cleMedaille, PersistentDataType.BOOLEAN, true));
        return it;
    }

    private static Component ligne(String s, NamedTextColor c) {
        return Component.text(s, c).decoration(TextDecoration.ITALIC, false);
    }
}
