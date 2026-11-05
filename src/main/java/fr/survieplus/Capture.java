package fr.survieplus;

import fr.survieplus.pack.PackMenus;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.CustomModelData;
import io.papermc.paper.datacomponent.item.ItemLore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.AnimalTamer;
import org.bukkit.entity.Boss;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntitySnapshot;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.Villager;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.CrafterCraftEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Filet de capture : clic droit sur un animal ou un monstre pour l'enfermer dans le filet,
 * clic droit sur un bloc pour le relacher. Le mob garde tout (nom, age, inventaire, metier du villageois...).
 * Le filet s'use un peu a chaque capture (capture.utilisations).
 */
@SuppressWarnings("UnstableApiUsage")
final class Capture implements Listener {

    static final String PERMISSION = "survieplus.capture";
    /** Objet de base : sans effet au clic droit et sans usage hors de l'etabli (bloque plus bas). */
    private static final Material BASE = Material.FIREWORK_STAR;

    private final SurviePlus plugin;
    private final NamespacedKey cleFilet;
    private final NamespacedKey cleMob;
    private final NamespacedKey cleType;
    private final NamespacedKey cleRecette;

    Capture(SurviePlus plugin) {
        this.plugin = plugin;
        cleFilet = new NamespacedKey(plugin, "filet");
        cleMob = new NamespacedKey(plugin, "filet_mob");
        cleType = new NamespacedKey(plugin, "filet_type");
        cleRecette = new NamespacedKey(plugin, "filet_capture");
        ShapedRecipe recette = new ShapedRecipe(cleRecette, filet(0));
        recette.shape("FFF", "FPF", "FFF");
        recette.setIngredient('F', Material.STRING);
        recette.setIngredient('P', Material.ENDER_PEARL);
        Bukkit.removeRecipe(cleRecette);
        Bukkit.addRecipe(recette);
    }

    void arreter() {
        Bukkit.removeRecipe(cleRecette);
    }

    private boolean actif() {
        return plugin.getConfig().getBoolean("capture.active", true);
    }

    // ------------------------------------------------------------ objet

    /** Filet vide, deja use de "usure" captures. */
    private ItemStack filet(int usure) {
        ItemStack it = new ItemStack(BASE);
        it.setData(DataComponentTypes.MAX_STACK_SIZE, 1);
        int max = plugin.getConfig().getInt("capture.utilisations", 10);
        if (max > 0) {
            it.setData(DataComponentTypes.MAX_DAMAGE, max);
            it.setData(DataComponentTypes.DAMAGE, Math.min(usure, max - 1));
        }
        it.editMeta(m -> m.getPersistentDataContainer().set(cleFilet, PersistentDataType.BOOLEAN, true));
        vider(it);
        return it;
    }

    private void vider(ItemStack it) {
        it.editMeta(m -> {
            PersistentDataContainer pdc = m.getPersistentDataContainer();
            pdc.remove(cleMob);
            pdc.remove(cleType);
        });
        apparence(it, PackMenus.FILET_VIDE);
        it.setData(DataComponentTypes.ITEM_NAME, Component.text("Filet de capture", NamedTextColor.GOLD));
        it.unsetData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE);
        it.setData(DataComponentTypes.LORE, ItemLore.lore(List.of(
                ligne("Clic droit sur un animal ou un monstre", NamedTextColor.GRAY),
                ligne("pour l'attraper.", NamedTextColor.GRAY))));
    }

    private void remplir(ItemStack it, Mob mob, String donnees) {
        EntityType type = mob.getType();
        it.editMeta(m -> {
            PersistentDataContainer pdc = m.getPersistentDataContainer();
            pdc.set(cleMob, PersistentDataType.STRING, donnees);
            pdc.set(cleType, PersistentDataType.STRING, type.getKey().toString());
        });
        apparence(it, PackMenus.FILET_PLEIN);
        it.setData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
        it.setData(DataComponentTypes.ITEM_NAME, Component.text("Filet : ", NamedTextColor.GOLD)
                .append(Component.translatable(type.translationKey(), NamedTextColor.YELLOW)));

        List<Component> lore = new ArrayList<>();
        Component nom = mob.customName();
        if (nom != null) lore.add(Component.text("« ", NamedTextColor.GRAY).append(nom).append(Component.text(" »"))
                .decoration(TextDecoration.ITALIC, false));
        if (mob instanceof Ageable a && !a.isAdult()) lore.add(ligne("Bébé", NamedTextColor.GRAY));
        lore.add(ligne("♥ " + Math.round(mob.getHealth()) + " / " + Math.round(vieMax(mob)), NamedTextColor.RED));
        lore.add(Component.empty());
        lore.add(ligne("Clic droit sur un bloc pour le relâcher", NamedTextColor.DARK_GRAY));
        it.setData(DataComponentTypes.LORE, ItemLore.lore(lore));
    }

    /**
     * Texture Higgsfield du filet (vide ou plein) pour les joueurs qui ont le pack du serveur ;
     * les autres voient une toile d'araignee.
     */
    private static void apparence(ItemStack it, String variante) {
        it.setData(DataComponentTypes.ITEM_MODEL, PackMenus.MODELE_FILET);
        it.setData(DataComponentTypes.CUSTOM_MODEL_DATA, CustomModelData.customModelData().addString(variante).build());
    }

    private static Component ligne(String s, NamedTextColor c) {
        return Component.text(s, c).decoration(TextDecoration.ITALIC, false);
    }

    private boolean estFilet(ItemStack it) {
        return it != null && it.getType() == BASE && it.hasItemMeta()
                && it.getItemMeta().getPersistentDataContainer().has(cleFilet);
    }

    private String contenu(ItemStack it) {
        return it.getItemMeta().getPersistentDataContainer().get(cleMob, PersistentDataType.STRING);
    }

    private static double vieMax(Mob mob) {
        AttributeInstance a = mob.getAttribute(Attribute.MAX_HEALTH);
        return a != null ? a.getValue() : mob.getHealth();
    }

    // ------------------------------------------------------------ regles (testees dans CaptureTest)

    /** Usure du filet apres une capture, ou -1 s'il est deja hors d'usage. max <= 0 : filet inusable. */
    static int usureApresCapture(int usure, int max) {
        if (max <= 0) return 0;
        return usure + 1 > max ? -1 : usure + 1;
    }

    /** Le filet se casse-t-il en relachant son mob ? (c'etait sa derniere capture) */
    static boolean casseAuLacher(int usure, int max) {
        return max > 0 && usure >= max;
    }

    // ------------------------------------------------------------ capture

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClicMob(PlayerInteractEntityEvent e) {
        Player p = e.getPlayer();
        EquipmentSlot main = e.getHand();
        ItemStack it = p.getInventory().getItem(main);
        if (!estFilet(it)) return;
        e.setCancelled(true); // pas d'echange avec le villageois, pas de monture...
        if (!actif() || !p.hasPermission(PERMISSION)) {
            info(p, "Le filet de capture est désactivé.", NamedTextColor.RED);
            return;
        }
        if (contenu(it) != null) {
            info(p, "Ton filet est déjà plein : clic droit sur un bloc pour le relâcher.", NamedTextColor.YELLOW);
            return;
        }
        if (!(e.getRightClicked() instanceof Mob mob) || !mob.isValid()) {
            info(p, "Le filet n'attrape que les animaux et les monstres.", NamedTextColor.RED);
            return;
        }
        String refus = refus(p, mob);
        if (refus != null) {
            info(p, refus, NamedTextColor.RED);
            return;
        }

        int max = it.hasData(DataComponentTypes.MAX_DAMAGE) ? it.getData(DataComponentTypes.MAX_DAMAGE) : 0;
        int usure = it.hasData(DataComponentTypes.DAMAGE) ? it.getData(DataComponentTypes.DAMAGE) : 0;
        int nouvelle = usureApresCapture(usure, max);
        if (nouvelle < 0) {
            info(p, "Ce filet est trop usé.", NamedTextColor.RED);
            return;
        }

        // Ce qui n'a pas a suivre le mob : cavaliers (ex : bebe zombie sur un poulet), monture
        mob.eject();
        mob.leaveVehicle();

        EntitySnapshot photo = mob.createSnapshot();
        if (photo == null) {
            info(p, "Impossible d'attraper ce mob.", NamedTextColor.RED);
            return;
        }
        Location ou = mob.getLocation().add(0, mob.getHeight() / 2, 0);
        remplir(it, mob, photo.getAsString());
        if (max > 0) it.setData(DataComponentTypes.DAMAGE, nouvelle);
        mob.remove();
        p.getInventory().setItem(main, it);
        p.swingHand(main);

        ou.getWorld().spawnParticle(Particle.CLOUD, ou, 12, 0.3, 0.3, 0.3, 0.02);
        ou.getWorld().playSound(ou, Sound.ENTITY_ITEM_PICKUP, 1f, 0.6f);
        info(p, Component.text("Attrapé : ", NamedTextColor.GREEN)
                .append(Component.translatable(mob.getType().translationKey(), NamedTextColor.YELLOW)));
        captureRecente.put(p.getUniqueId(), Bukkit.getCurrentTick());
        plugin.bestiaire().enregistrer(p, mob.getType());
    }

    /**
     * Tick de la derniere capture de chaque joueur. Un clic droit sur un mob envoie aussi, juste apres,
     * un "clic avec l'objet en main" : vu comme un clic dans le vide ou sur le bloc derriere le mob, il ne doit
     * ni relacher le mob qu'on vient d'attraper, ni afficher "Vise un bloc..." par-dessus "Attrape : ...".
     */
    private final java.util.Map<java.util.UUID, Integer> captureRecente = new java.util.HashMap<>();

    /** Vrai si ce joueur vient d'attraper un mob (meme clic). */
    private boolean vientDeCapturer(Player p) {
        Integer tick = captureRecente.remove(p.getUniqueId());
        return tick != null && Bukkit.getCurrentTick() - tick <= 1;
    }

    /** Raison pour laquelle ce mob ne peut pas etre capture, ou null. */
    private String refus(Player p, Mob mob) {
        var cfg = plugin.getConfig();
        List<String> interdits = cfg.getStringList("capture.interdits").stream()
                .map(s -> s.trim().toUpperCase(Locale.ROOT)).toList();
        if (mob instanceof Boss || interdits.contains(mob.getType().name()))
            return "Celui-là est bien trop puissant pour un filet !";
        if (mob instanceof Villager v) {
            if (!cfg.getBoolean("capture.villageois", true)) return "Les villageois ne se capturent pas sur ce serveur.";
            if (v.isTrading()) return "Ce villageois est occupé avec un client.";
        }
        if (mob.getPassengers().stream().anyMatch(x -> x instanceof Player))
            return "Quelqu'un est assis dessus !";
        if (mob.isLeashed()) return "Détache-le d'abord.";
        if (mob instanceof Tameable t && t.isTamed() && !p.hasPermission("survieplus.admin")) {
            AnimalTamer maitre = t.getOwner();
            if (maitre != null && !maitre.getUniqueId().equals(p.getUniqueId()))
                return "Cet animal appartient à " + (maitre.getName() != null ? maitre.getName() : "quelqu'un d'autre") + ".";
        }
        return null;
    }

    // ------------------------------------------------------------ lacher

    @SuppressWarnings("deprecation") // isInteractable : deprecie car imprecis, mais c'est la liste la plus complete
    @EventHandler(priority = EventPriority.HIGH)
    public void onClicBloc(PlayerInteractEvent e) {
        ItemStack it = e.getItem();
        if (e.getHand() == null || !estFilet(it)) return;
        String donnees = contenu(it);
        if (donnees == null) return;
        Player p = e.getPlayer();
        // Meme clic que la capture : Paper cherche alors le bloc vise (le mob n'est plus la) et le signale comme
        // un clic sur l'herbe ou le mur derriere : on l'ignore, sinon le mob ressortirait aussitot
        if (vientDeCapturer(p)) {
            e.setUseInteractedBlock(Event.Result.DENY);
            e.setUseItemInHand(Event.Result.DENY);
            return;
        }
        if (e.getAction() == Action.RIGHT_CLICK_AIR) {
            info(p, "Vise un bloc pour relâcher le mob.", NamedTextColor.YELLOW);
            return;
        }
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getClickedBlock() == null) return;
        // Un coffre, une porte... s'ouvrent normalement (accroupi pour relacher dessus)
        if (e.getClickedBlock().getType().isInteractable() && !p.isSneaking()) return;
        if (e.useInteractedBlock() == Event.Result.DENY && e.useItemInHand() == Event.Result.DENY) return; // zone protegee
        e.setUseInteractedBlock(Event.Result.DENY);
        e.setUseItemInHand(Event.Result.DENY);
        if (!actif() || !p.hasPermission(PERMISSION)) {
            info(p, "Le filet de capture est désactivé.", NamedTextColor.RED);
            return;
        }

        Block clic = e.getClickedBlock();
        Block cible = clic.isReplaceable() ? clic : clic.getRelative(e.getBlockFace());
        if (!cible.isPassable()) {
            info(p, "Pas assez de place ici.", NamedTextColor.RED);
            return;
        }
        Location ou = cible.getLocation().add(0.5, 0, 0.5);

        Entity mob;
        try {
            mob = Bukkit.getEntityFactory().createEntitySnapshot(donnees).createEntity(ou);
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning("Filet illisible (" + p.getName() + ") : " + ex.getMessage());
            info(p, "Ce filet est abîmé : le mob ne peut pas sortir.", NamedTextColor.RED);
            return;
        }
        if (mob == null || !mob.isValid()) {
            info(p, "Le mob ne peut pas apparaître ici.", NamedTextColor.RED);
            return;
        }
        mob.setRotation(p.getLocation().getYaw() + 180, 0); // tourne vers le joueur
        // Relache par un joueur : il ne disparait plus quand on s'eloigne (comme un animal d'elevage)
        if (mob instanceof Mob m) m.setRemoveWhenFarAway(false);

        EquipmentSlot main = e.getHand();
        int max = it.hasData(DataComponentTypes.MAX_DAMAGE) ? it.getData(DataComponentTypes.MAX_DAMAGE) : 0;
        int usure = it.hasData(DataComponentTypes.DAMAGE) ? it.getData(DataComponentTypes.DAMAGE) : 0;
        if (casseAuLacher(usure, max)) {
            p.getInventory().setItem(main, null);
            p.playSound(p.getLocation(), Sound.ENTITY_ITEM_BREAK, 1f, 1f);
            info(p, "Ton filet s'est déchiré.", NamedTextColor.GRAY);
        } else {
            vider(it);
            p.getInventory().setItem(main, it);
        }
        p.swingHand(main);
        Location centre = ou.clone().add(0, 0.5, 0);
        ou.getWorld().spawnParticle(Particle.CLOUD, centre, 12, 0.3, 0.3, 0.3, 0.02);
        ou.getWorld().playSound(centre, Sound.ENTITY_CHICKEN_EGG, 1f, 0.8f);
    }

    // ------------------------------------------------------------ garde-fous

    /**
     * Objet du plugin fait sur l'etoile de feu d'artifice (filet, medaille du naturaliste) : il ne doit pas servir
     * d'ingredient (fusee, etoile teinte), on perdrait le mob du filet ou la medaille.
     */
    private boolean special(ItemStack it) {
        return it != null && it.getType() == BASE && it.hasItemMeta() && it.getItemMeta().getPersistentDataContainer()
                .getKeys().stream().anyMatch(k -> k.getNamespace().equals(cleFilet.getNamespace()));
    }

    @EventHandler
    public void onPreparer(PrepareItemCraftEvent e) {
        for (ItemStack it : e.getInventory().getMatrix())
            if (special(it)) {
                e.getInventory().setResult(null);
                return;
            }
        if (estFilet(e.getInventory().getResult()) && e.getView().getPlayer() instanceof Player p
                && (!actif() || !p.hasPermission(PERMISSION)))
            e.getInventory().setResult(null);
    }

    @EventHandler
    public void onFabricateur(CrafterCraftEvent e) {
        var inv = ((org.bukkit.block.Crafter) e.getBlock().getState(false)).getInventory();
        for (ItemStack it : inv.getContents())
            if (special(it)) {
                e.setCancelled(true);
                return;
            }
    }

    @EventHandler
    public void onConnexion(PlayerJoinEvent e) {
        if (actif() && e.getPlayer().hasPermission(PERMISSION)) e.getPlayer().discoverRecipe(cleRecette);
    }

    private static void info(Player p, String texte, NamedTextColor c) {
        info(p, Component.text(texte, c));
    }

    private static void info(Player p, Component texte) {
        p.sendActionBar(texte);
    }
}
