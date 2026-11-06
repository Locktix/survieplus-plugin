package fr.survieplus;

import com.destroystokyo.paper.event.inventory.PrepareResultEvent;
import fr.survieplus.pack.PackMenus;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.CustomModelData;
import io.papermc.paper.datacomponent.item.ItemAttributeModifiers;
import io.papermc.paper.datacomponent.item.ItemLore;
import io.papermc.paper.datacomponent.item.TooltipDisplay;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.TileState;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.CrafterCraftEvent;
import org.bukkit.event.enchantment.PrepareItemEnchantEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Pioche du mineur : se fabrique (pioche en pierre entouree d'amethyste et de cuivre), est liee a celui qui la
 * fabrique et ne s'use jamais. Son niveau est le niveau de Mineur de son proprietaire : elle mine un peu plus vite
 * a chaque niveau et gagne quelque chose tous les 5 niveaux (Fortune, fer, aimant, diamant, vision nocturne,
 * netherite, filons plus grands, 3x3).
 * Un seul exemplaire valable par joueur : /pioche en refait une si elle est perdue, l'ancienne s'effrite alors.
 */
@SuppressWarnings("UnstableApiUsage")
final class Pioche implements Listener {

    static final String PERMISSION = "survieplus.pioche";
    /** Niveaux de Mineur des gains de la pioche (la fonte et le 3x3 viennent de config.yml). */
    static final int FORTUNE_1 = 5, FER = 10, AIMANT = 15, FORTUNE_2 = 20, DIAMANT = 25, VISION = 30,
            NETHERITE = 40, FILON = 45, MAITRE = 50;
    private static final int VISION_DUREE = 300; // ticks ; reposee sans cesse, au-dessus de 10 s : pas de clignotement

    /** texte : bulle de la pioche et annonce ; court : page du guide. */
    record Gain(int niveau, String texte, String court, boolean annoncer) {}

    private final SurviePlus plugin;
    private final NamespacedKey cleProprio, cleNom, cleId, cleNiveau, cleJoueur, cleRecette, cleVitesse;
    /** Face du bloc tape en dernier par chaque joueur (orientation du 3x3). */
    private final Map<UUID, BlockFace> faces = new HashMap<>();

    Pioche(SurviePlus plugin) {
        this.plugin = plugin;
        cleProprio = new NamespacedKey(plugin, "pioche_proprio");
        cleNom = new NamespacedKey(plugin, "pioche_nom");
        cleId = new NamespacedKey(plugin, "pioche_id");
        cleNiveau = new NamespacedKey(plugin, "pioche_niveau");
        cleJoueur = new NamespacedKey(plugin, "pioche"); // sur le joueur : id de sa pioche valable
        cleRecette = new NamespacedKey(plugin, "pioche_du_mineur");
        cleVitesse = new NamespacedKey(plugin, "pioche_vitesse");

        // Le resultat affiche est remplace par la pioche du joueur dans onPreparer
        ShapedRecipe recette = new ShapedRecipe(cleRecette, construire(new UUID(0, 0), "?", "?", 0));
        recette.shape("CAC", "APA", "CAC");
        recette.setIngredient('C', Material.COPPER_INGOT);
        recette.setIngredient('A', Material.AMETHYST_SHARD);
        recette.setIngredient('P', Material.STONE_PICKAXE);
        Bukkit.removeRecipe(cleRecette);
        Bukkit.addRecipe(recette);

        plugin.getServer().getScheduler().runTaskTimer(plugin, this::visionNocturne, 5L, 5L); // 1/4 s : reagit vite
    }

    void arreter() {
        Bukkit.removeRecipe(cleRecette);
    }

    boolean actif() {
        return plugin.getConfig().getBoolean("pioche.active", true);
    }

    private boolean autorise(Player p) {
        return actif() && p.hasPermission(PERMISSION);
    }

    // ------------------------------------------------------------ regles (testees dans PiocheTest)

    static Material materiau(int niveau) {
        if (niveau >= NETHERITE) return Material.NETHERITE_PICKAXE;
        if (niveau >= DIAMANT) return Material.DIAMOND_PICKAXE;
        if (niveau >= FER) return Material.IRON_PICKAXE;
        return Material.STONE_PICKAXE;
    }

    static int fortune(int niveau) {
        return niveau >= NETHERITE ? 3 : niveau >= FORTUNE_2 ? 2 : niveau >= FORTUNE_1 ? 1 : 0;
    }

    /**
     * Vitesse de minage ajoutee (attribut mining_efficiency, comme l'Efficacite : Efficacite V = 26).
     * Progressive : un peu a chaque niveau, de plus en plus vers la fin.
     */
    static double vitesse(int niveau, int niveauMax, double vitesseMax) {
        if (niveau <= 0 || niveauMax <= 0) return 0;
        double f = Math.min(1, (double) niveau / niveauMax);
        return Math.round(vitesseMax * Math.pow(f, 1.5) * 10) / 10.0;
    }

    static String apparence(int niveau) {
        if (niveau >= MAITRE) return "survieplus:pioche_maitre";
        if (niveau >= NETHERITE) return "survieplus:pioche_netherite";
        if (niveau >= DIAMANT) return "survieplus:pioche_diamant";
        if (niveau >= FER) return "survieplus:pioche_fer";
        return "survieplus:pioche_pierre";
    }

    /** XP + pct %, la partie decimale devenant une chance de +1 (tirage entre 0 et 1). */
    static long avecBonus(long xp, double pct, double tirage) {
        double extra = xp * Math.max(0, pct) / 100.0;
        long entier = (long) extra;
        return xp + entier + (tirage < extra - entier ? 1 : 0);
    }

    /** Les 8 blocs autour du bloc casse, dans le plan de la face tapee. */
    static List<int[]> decalages3x3(BlockFace face) {
        List<int[]> d = new ArrayList<>();
        for (int a = -1; a <= 1; a++)
            for (int b = -1; b <= 1; b++) {
                if (a == 0 && b == 0) continue;
                switch (face) {
                    case UP, DOWN -> d.add(new int[]{a, 0, b});
                    case EAST, WEST -> d.add(new int[]{0, a, b});
                    default -> d.add(new int[]{a, b, 0}); // NORTH, SOUTH
                }
            }
        return d;
    }

    /** Tous les gains de la pioche, par niveau. */
    List<Gain> gains() {
        List<Gain> g = new ArrayList<>(List.of(
                new Gain(FORTUNE_1, "Fortune I", "Fortune I", true),
                new Gain(FER, "Devient en fer", "Fer", true),
                new Gain(AIMANT, "Aimant : le butin va dans ton sac", "Aimant", true),
                new Gain(FORTUNE_2, "Fortune II", "Fortune II", true),
                new Gain(DIAMANT, "Devient en diamant", "Diamant", true),
                new Gain(VISION, "Vision nocturne sous terre", "Vision nocturne", true),
                new Gain(NETHERITE, "Devient en netherite, Fortune III", "Netherite, Fortune III", true),
                new Gain(FILON, "Filons plus grands", "Grands filons", true)));
        int fonte = plugin.metiers().niveauPalier(2);
        if (fonte > 0) g.add(new Gain(fonte, "Fonte automatique (métier)", "Fonte auto", false)); // deja annoncee par Metiers
        int x3 = plugin.niveauRequis(Module.MINAGE_3X3);
        boolean x3Actif = plugin.activeServeur(Module.MINAGE_3X3);
        if (x3Actif && x3 == MAITRE) g.add(new Gain(MAITRE, "Minage 3×3, apparence Grand maître", "3×3, Grand maître", true));
        else {
            if (x3Actif) g.add(new Gain(x3, "Minage 3×3", "Minage 3×3", true));
            g.add(new Gain(MAITRE, "Apparence Grand maître", "Grand maître", true));
        }
        g.sort(Comparator.comparingInt(Gain::niveau));
        return g;
    }

    // ------------------------------------------------------------ objet

    private ItemStack construire(UUID proprio, String nom, String id, int niveau) {
        ItemStack it = new ItemStack(materiau(niveau));
        it.editMeta(m -> {
            PersistentDataContainer pdc = m.getPersistentDataContainer();
            pdc.set(cleProprio, PersistentDataType.STRING, proprio.toString());
            pdc.set(cleNom, PersistentDataType.STRING, nom);
            pdc.set(cleId, PersistentDataType.STRING, id);
            pdc.set(cleNiveau, PersistentDataType.INTEGER, niveau);
        });
        it.setData(DataComponentTypes.ITEM_NAME, Component.text("Pioche du mineur", couleur(niveau)));
        it.setData(DataComponentTypes.UNBREAKABLE);
        // Ni table d'enchantement, ni reparation : elle progresse seulement avec le metier
        it.unsetData(DataComponentTypes.ENCHANTABLE);
        it.unsetData(DataComponentTypes.REPAIRABLE);
        int f = fortune(niveau);
        if (f > 0) it.addUnsafeEnchantment(Enchantment.FORTUNE, f);
        it.setData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, niveau >= MAITRE);

        // Garde les attributs de la pioche du jeu (degats, vitesse d'attaque) et ajoute la vitesse de minage
        ItemAttributeModifiers.Builder attributs = ItemAttributeModifiers.itemAttributes();
        ItemAttributeModifiers defaut = it.getData(DataComponentTypes.ATTRIBUTE_MODIFIERS);
        if (defaut != null)
            for (var e : defaut.modifiers()) attributs.addModifier(e.attribute(), e.modifier(), e.getGroup(), e.display());
        double v = vitesse(niveau, plugin.metiers().niveauMax(), plugin.getConfig().getDouble("pioche.vitesse-max", 26));
        if (v > 0) attributs.addModifier(Attribute.MINING_EFFICIENCY,
                new AttributeModifier(cleVitesse, v, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND),
                EquipmentSlotGroup.MAINHAND);
        it.setData(DataComponentTypes.ATTRIBUTE_MODIFIERS, attributs.build());

        it.setData(DataComponentTypes.ITEM_MODEL, materiau(niveau).getKey());
        it.setData(DataComponentTypes.CUSTOM_MODEL_DATA, CustomModelData.customModelData().addString(apparence(niveau)).build());
        it.setData(DataComponentTypes.TOOLTIP_DISPLAY, TooltipDisplay.tooltipDisplay().addHiddenComponents(
                DataComponentTypes.ATTRIBUTE_MODIFIERS, DataComponentTypes.UNBREAKABLE, DataComponentTypes.ENCHANTMENTS).build());
        it.setData(DataComponentTypes.LORE, ItemLore.lore(lore(nom, niveau, v)));
        return it;
    }

    private static NamedTextColor couleur(int niveau) {
        if (niveau >= MAITRE) return NamedTextColor.GOLD;
        if (niveau >= NETHERITE) return NamedTextColor.LIGHT_PURPLE;
        if (niveau >= DIAMANT) return NamedTextColor.AQUA;
        if (niveau >= FER) return NamedTextColor.WHITE;
        return NamedTextColor.GRAY;
    }

    private List<Component> lore(String nom, int niveau, double vitesse) {
        List<Component> l = new ArrayList<>();
        l.add(ligne("Liée à " + nom, NamedTextColor.GRAY));
        l.add(ligne("Niveau " + niveau + " / " + plugin.metiers().niveauMax() + "  (ton niveau de Mineur)", NamedTextColor.GOLD));
        if (vitesse > 0) l.add(ligne(String.format(java.util.Locale.FRANCE, "Vitesse de minage +%.1f", vitesse), NamedTextColor.AQUA));
        l.add(Component.empty());
        for (Gain g : gains()) {
            boolean ok = niveau >= g.niveau();
            l.add(Component.text((ok ? "✔ " : "· ") + "niv. " + g.niveau() + "  ", ok ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY)
                    .append(Component.text(g.texte(), ok ? NamedTextColor.WHITE : NamedTextColor.GRAY))
                    .decoration(TextDecoration.ITALIC, false));
        }
        l.add(Component.empty());
        double bonus = plugin.getConfig().getDouble("pioche.bonus-xp", 15);
        if (bonus > 0) l.add(ligne("+" + Metiers.pourcent(bonus / 100) + " d'XP de Mineur avec elle", NamedTextColor.GRAY));
        l.add(ligne("Incassable · reste avec toi à ta mort", NamedTextColor.DARK_GRAY));
        return l;
    }

    private static Component ligne(String s, NamedTextColor c) {
        return Component.text(s, c).decoration(TextDecoration.ITALIC, false);
    }

    boolean estPioche(ItemStack it) {
        return it != null && !it.isEmpty() && it.hasItemMeta()
                && it.getItemMeta().getPersistentDataContainer().has(cleProprio, PersistentDataType.STRING);
    }

    private String lire(ItemStack it, NamespacedKey cle) {
        return it.getItemMeta().getPersistentDataContainer().get(cle, PersistentDataType.STRING);
    }

    private UUID proprio(ItemStack it) {
        try {
            return UUID.fromString(lire(it, cleProprio));
        } catch (IllegalArgumentException | NullPointerException e) {
            return null;
        }
    }

    private boolean aMoi(Player p, ItemStack it) {
        return estPioche(it) && p.getUniqueId().equals(proprio(it));
    }

    /** La pioche valable de ce joueur (pas une ancienne copie) ? */
    boolean valide(Player p, ItemStack it) {
        return aMoi(p, it) && java.util.Objects.equals(lire(it, cleId), idJoueur(p));
    }

    private String idJoueur(Player p) {
        return p.getPersistentDataContainer().get(cleJoueur, PersistentDataType.STRING);
    }

    private int niveau(Player p) {
        return plugin.metiers().niveau(p.getUniqueId(), Metier.MINEUR);
    }

    /** Niveau de la pioche en main, ou -1 si ce n'est pas sa pioche valable (ou pioche desactivee). */
    int niveauEnMain(Player p, ItemStack outil) {
        return autorise(p) && valide(p, outil) ? niveau(p) : -1;
    }

    /** Bonus de la pioche sur l'XP de Mineur gagnee en minant avec. */
    long xpMineur(Player p, ItemStack outil, long xp) {
        if (niveauEnMain(p, outil) < 0) return xp;
        return avecBonus(xp, plugin.getConfig().getDouble("pioche.bonus-xp", 15), ThreadLocalRandom.current().nextDouble());
    }

    /** Taille max d'un filon mine avec cet outil. */
    int maxFilon(Player p, ItemStack outil, int normal) {
        return niveauEnMain(p, outil) >= FILON ? Math.max(normal, plugin.getConfig().getInt("pioche.filon-max-blocs", 128)) : normal;
    }

    /**
     * Met a jour la pioche du joueur dans son inventaire (niveau, apparence...) et fait disparaitre
     * les anciennes copies qu'il porte.
     */
    void actualiser(Player p) {
        PlayerInventory inv = p.getInventory();
        int niveau = niveau(p);
        for (int slot = 0; slot < inv.getSize(); slot++) {
            ItemStack it = inv.getItem(slot);
            if (!aMoi(p, it)) continue;
            if (!valide(p, it)) {
                inv.setItem(slot, null);
                info(p, "Une ancienne copie de ta pioche s'est effritée.", NamedTextColor.GRAY);
                continue;
            }
            Integer n = it.getItemMeta().getPersistentDataContainer().get(cleNiveau, PersistentDataType.INTEGER);
            if (n == null || n != niveau || it.getType() != materiau(niveau))
                inv.setItem(slot, construire(p.getUniqueId(), p.getName(), lire(it, cleId), niveau));
        }
    }

    /** Appele par Metiers quand le niveau de Mineur change. */
    void niveauGagne(Player p, int avant, int apres) {
        if (idJoueur(p) == null || !autorise(p)) return;
        actualiser(p);
        for (Gain g : gains())
            if (g.annoncer() && avant < g.niveau() && apres >= g.niveau())
                p.sendMessage(SurviePlus.PREFIXE.append(Component.text("Ta pioche du mineur : " + g.texte() + " !",
                        NamedTextColor.LIGHT_PURPLE)));
    }

    // ------------------------------------------------------------ /pioche

    void commande(Player p) {
        if (!autorise(p)) {
            message(p, "La pioche du mineur est désactivée.", NamedTextColor.RED);
            return;
        }
        if (idJoueur(p) == null) {
            message(p, "Fabrique-la : une pioche en pierre entourée de 4 éclats d'améthyste et de 4 lingots de cuivre.",
                    NamedTextColor.YELLOW);
            return;
        }
        actualiser(p);
        for (ItemStack it : p.getInventory().getContents())
            if (valide(p, it)) {
                message(p, "Ta pioche est déjà dans ton inventaire.", NamedTextColor.YELLOW);
                return;
            }
        String id = UUID.randomUUID().toString();
        p.getPersistentDataContainer().set(cleJoueur, PersistentDataType.STRING, id);
        Recompenses.remettre(p, construire(p.getUniqueId(), p.getName(), id, niveau(p)));
        p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1f);
        message(p, "Ta pioche te revient ! L'ancienne s'effritera si elle réapparaît.", NamedTextColor.GREEN);
    }

    // ------------------------------------------------------------ fabrication

    private boolean notreRecette(org.bukkit.inventory.Recipe r) {
        return r instanceof Keyed k && k.getKey().equals(cleRecette);
    }

    @EventHandler
    public void onPreparer(PrepareItemCraftEvent e) {
        for (ItemStack it : e.getInventory().getMatrix())
            if (estPioche(it)) { // pas d'ingredient (ni de reparation en combinant deux pioches)
                e.getInventory().setResult(null);
                return;
            }
        if (!notreRecette(e.getRecipe()) || !(e.getView().getPlayer() instanceof Player p)) return;
        if (!autorise(p)) {
            e.getInventory().setResult(null);
        } else if (idJoueur(p) != null) {
            e.getInventory().setResult(null);
            info(p, "Tu as déjà ta pioche du mineur (perdue ? /pioche)", NamedTextColor.YELLOW);
        } else {
            e.getInventory().setResult(construire(p.getUniqueId(), p.getName(), UUID.randomUUID().toString(), niveau(p)));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFabrication(CraftItemEvent e) {
        if (!notreRecette(e.getRecipe()) || !(e.getWhoClicked() instanceof Player p)) return;
        ItemStack resultat = e.getInventory().getResult();
        if (!autorise(p) || idJoueur(p) != null || !aMoi(p, resultat)) {
            e.setCancelled(true);
            return;
        }
        p.getPersistentDataContainer().set(cleJoueur, PersistentDataType.STRING, lire(resultat, cleId));
        p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.2f);
        message(p, "Ta pioche du mineur est liée à toi : elle grandira avec ton niveau de Mineur.", NamedTextColor.LIGHT_PURPLE);
    }

    @EventHandler(ignoreCancelled = true)
    public void onFabricateur(CrafterCraftEvent e) {
        if (notreRecette(e.getRecipe())) {
            e.setCancelled(true); // personne a qui la lier
            return;
        }
        var inv = ((org.bukkit.block.Crafter) e.getBlock().getState(false)).getInventory();
        for (ItemStack it : inv.getContents())
            if (estPioche(it)) {
                e.setCancelled(true);
                return;
            }
    }

    /** Ni enclume (renommage, livres), ni meule, ni table de forge (netherite) : elle evolue seule. */
    @EventHandler
    public void onAtelier(PrepareResultEvent e) {
        for (ItemStack it : e.getInventory().getContents())
            if (estPioche(it)) {
                e.setResult(null);
                return;
            }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEnchantement(PrepareItemEnchantEvent e) {
        if (estPioche(e.getItem())) e.setCancelled(true);
    }

    // ------------------------------------------------------------ appartenance

    /** Seul le proprietaire mine avec, et seulement avec sa copie valable. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onCasseAutorisee(BlockBreakEvent e) {
        Player p = e.getPlayer();
        ItemStack outil = p.getInventory().getItemInMainHand();
        if (!estPioche(outil) || valide(p, outil)) return;
        e.setCancelled(true);
        if (aMoi(p, outil)) {
            p.getInventory().setItemInMainHand(null);
            info(p, "Une ancienne copie de ta pioche s'est effritée.", NamedTextColor.GRAY);
        } else {
            info(p, "Cette pioche est liée à " + lire(outil, cleNom) + " : elle refuse de miner pour toi.", NamedTextColor.RED);
        }
    }

    /** Par terre : indestructible, ne disparait pas, et seul son proprietaire peut la ramasser. */
    @EventHandler(ignoreCancelled = true)
    public void onApparition(ItemSpawnEvent e) {
        Item item = e.getEntity();
        if (!estPioche(item.getItemStack())) return;
        item.setInvulnerable(true);
        item.setUnlimitedLifetime(true);
        UUID proprio = proprio(item.getItemStack());
        if (proprio != null) item.setOwner(proprio);
    }

    @EventHandler(ignoreCancelled = true)
    public void onRamasse(EntityPickupItemEvent e) {
        ItemStack it = e.getItem().getItemStack();
        if (!estPioche(it)) return;
        if (!(e.getEntity() instanceof Player p) || !aMoi(p, it)) {
            e.setCancelled(true);
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> actualiser(p));
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntonnoir(InventoryPickupItemEvent e) {
        if (estPioche(e.getItem().getItemStack())) e.setCancelled(true);
    }

    /** A la mort, elle reste dans l'inventaire (pas dans la tombe ni par terre). */
    @EventHandler(priority = EventPriority.LOW)
    public void onMort(PlayerDeathEvent e) {
        if (e.getKeepInventory()) return;
        Player p = e.getEntity();
        for (Iterator<ItemStack> i = e.getDrops().iterator(); i.hasNext(); ) {
            ItemStack it = i.next();
            if (!aMoi(p, it)) continue;
            i.remove();
            if (valide(p, it)) e.getItemsToKeep().add(it);
        }
    }

    @EventHandler
    public void onConnexion(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (autorise(p)) p.discoverRecipe(cleRecette);
        actualiser(p);
    }

    @EventHandler
    public void onDepart(PlayerQuitEvent e) {
        faces.remove(e.getPlayer().getUniqueId());
    }

    @EventHandler(ignoreCancelled = true)
    public void onMain(PlayerItemHeldEvent e) {
        if (aMoi(e.getPlayer(), e.getPlayer().getInventory().getItem(e.getNewSlot()))) actualiser(e.getPlayer());
    }

    // ------------------------------------------------------------ gains

    /** Aimant : le butin des blocs mines avec la pioche va directement dans l'inventaire. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onButin(BlockDropItemEvent e) {
        Player p = e.getPlayer();
        if (niveauEnMain(p, p.getInventory().getItemInMainHand()) < AIMANT) return;
        boolean pris = false;
        for (Iterator<Item> i = e.getItems().iterator(); i.hasNext(); ) {
            Item item = i.next();
            var reste = p.getInventory().addItem(item.getItemStack().clone());
            if (reste.isEmpty()) {
                i.remove();
                pris = true;
            } else {
                ItemStack r = reste.values().iterator().next();
                if (r.getAmount() != item.getItemStack().getAmount()) pris = true;
                item.setItemStack(r);
            }
        }
        if (pris) p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.3f, 1.6f);
    }

    /** Sous terre : sous pioche.vision-y (niveau de la mer par defaut) et sans ciel au-dessus de la tete. */
    static boolean sousTerre(int y, int yMax, int lumiereDuCiel) {
        return y <= yMax && lumiereDuCiel < 15;
    }

    private boolean sousTerre(Player p) {
        Block tete = p.getEyeLocation().getBlock();
        return sousTerre(tete.getY(), plugin.getConfig().getInt("pioche.vision-y", 62), tete.getLightFromSky());
    }

    /**
     * Vision nocturne sous terre, pioche en main. Elle reste quelques secondes si on prend une torche ou un bloc,
     * et s'arrete des qu'on remonte a l'air libre.
     */
    private void visionNocturne() {
        if (!actif()) return;
        for (Player p : Bukkit.getOnlinePlayers()) {
            boolean dessous = sousTerre(p);
            if (dessous && niveauEnMain(p, p.getInventory().getItemInMainHand()) >= VISION) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, VISION_DUREE, 0, true, false, false));
                continue;
            }
            // Retire seulement la notre (discrete, courte) : pas celle d'une potion
            PotionEffect actuel = p.getPotionEffect(PotionEffectType.NIGHT_VISION);
            if (actuel != null && actuel.isAmbient() && !actuel.hasParticles() && !actuel.hasIcon()
                    && actuel.getDuration() <= VISION_DUREE && !dessous)
                p.removePotionEffect(PotionEffectType.NIGHT_VISION);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onTape(PlayerInteractEvent e) {
        if (e.getAction() == Action.LEFT_CLICK_BLOCK && e.getBlockFace() != null)
            faces.put(e.getPlayer().getUniqueId(), e.getBlockFace());
    }

    /** Minage 3x3 (module MINAGE_3X3) ; accroupi = un seul bloc (ou le filon). */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCasse3x3(BlockBreakEvent e) {
        Player p = e.getPlayer();
        if (Blocs.EN_COURS.contains(p.getUniqueId()) || p.isSneaking()) return;
        ItemStack outil = p.getInventory().getItemInMainHand();
        if (niveauEnMain(p, outil) < 0 || !plugin.actif(p, Module.MINAGE_3X3)) return;
        Block centre = e.getBlock();
        if (!Tag.MINEABLE_PICKAXE.isTagged(centre.getType())) return;
        float durete = Math.max(centre.getType().getHardness(), 5f);
        BlockFace face = faces.getOrDefault(p.getUniqueId(), BlockFace.UP);
        List<Block> autour = new ArrayList<>();
        for (int[] d : decalages3x3(face)) {
            Block b = centre.getRelative(d[0], d[1], d[2]);
            float h = b.getType().getHardness();
            if (Tag.MINEABLE_PICKAXE.isTagged(b.getType()) && h >= 0 && h <= durete
                    && !(b.getState(false) instanceof TileState)) autour.add(b);
        }
        if (autour.isEmpty()) return;
        Material typeOutil = outil.getType();
        plugin.getServer().getScheduler().runTask(plugin, () ->
                Blocs.casser(p, autour, m -> Tag.MINEABLE_PICKAXE.isTagged(m), it -> it.getType() == typeOutil && valide(p, it)));
    }

    // ------------------------------------------------------------ messages

    private static void info(Player p, String texte, NamedTextColor c) {
        p.sendActionBar(Component.text(texte, c));
    }

    private static void message(Player p, String texte, NamedTextColor c) {
        p.sendMessage(SurviePlus.PREFIXE.append(Component.text(texte, c)));
    }
}
