package fr.survieplus;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Tombe : a la mort, les objets et l'XP sont ranges dans un coffre a l'endroit de la mort.
 * Le coffre reste vide en realite (rien a voler avec un entonnoir) : le contenu est sauvegarde
 * dans tombes.yml et rendu d'un coup au proprietaire quand il clique dessus.
 */
final class Tombes implements Listener {

    private record Tombe(String id, UUID proprio, String nom, long date, int xp, List<ItemStack> objets) {}

    private static final Set<EquipmentSlot> ARMURE = Set.of(
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.OFF_HAND);

    private final SurviePlus plugin;
    private final File fichier;
    private final Map<String, Tombe> tombes = new HashMap<>(); // cle = position du coffre
    private final Map<UUID, String> derniereTombe = new HashMap<>(); // pour le rappel a la reapparition

    Tombes(SurviePlus plugin) {
        this.plugin = plugin;
        this.fichier = new File(plugin.getDataFolder(), "tombes.yml");
        charger();
    }

    private static String cle(Block b) {
        return b.getWorld().getName() + ";" + b.getX() + ";" + b.getY() + ";" + b.getZ();
    }

    private static String position(Block b) {
        return b.getX() + " " + b.getY() + " " + b.getZ() + " (" + b.getWorld().getName() + ")";
    }

    // ------------------------------------------------------------ mort

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMort(PlayerDeathEvent e) {
        Player p = e.getEntity();
        if (e.getKeepInventory() || e.getDrops().isEmpty() || !plugin.actif(p, Module.TOMBE)) return;

        Block place = trouverPlace(p.getLocation());
        if (place == null) {
            p.sendMessage(SurviePlus.PREFIXE.append(Component.text(
                    "Impossible de placer ta tombe ici : tes affaires sont tombees au sol.", NamedTextColor.RED)));
            return;
        }

        List<ItemStack> objets = new ArrayList<>();
        for (ItemStack it : e.getDrops())
            if (it != null && !it.isEmpty()) objets.add(it.clone());
        int xp = e.getDroppedExp();
        e.getDrops().clear();
        e.setDroppedExp(0);

        boolean eau = place.getType() == Material.WATER;
        place.setType(Material.CHEST);
        if (eau && place.getBlockData() instanceof Waterlogged w) {
            w.setWaterlogged(true);
            place.setBlockData(w);
        }

        Tombe t = new Tombe(UUID.randomUUID().toString(), p.getUniqueId(), p.getName(),
                System.currentTimeMillis(), xp, objets);
        tombes.put(cle(place), t);
        sauvegarder();

        String msg = "Ta tombe t'attend en " + position(place) + ". Clic droit dessus pour tout recuperer.";
        derniereTombe.put(p.getUniqueId(), msg);
        p.sendMessage(SurviePlus.PREFIXE.append(Component.text(msg, NamedTextColor.YELLOW)));
    }

    @EventHandler
    public void onReapparition(PlayerRespawnEvent e) {
        String msg = derniereTombe.remove(e.getPlayer().getUniqueId());
        if (msg == null) return;
        Player p = e.getPlayer();
        plugin.getServer().getScheduler().runTaskLater(plugin, () ->
                p.sendMessage(SurviePlus.PREFIXE.append(Component.text(msg, NamedTextColor.YELLOW))), 5L);
    }

    /** Un bloc libre a l'endroit de la mort ou juste au-dessus ; null si impossible (vide du End...). */
    private Block trouverPlace(Location loc) {
        World w = loc.getWorld();
        int min = w.getMinHeight(), max = w.getMaxHeight() - 1;
        int x = loc.getBlockX(), z = loc.getBlockZ(), y = loc.getBlockY();
        if (y < min) {
            y = w.getHighestBlockYAt(x, z) + 1;
            if (y <= min) return null;
        }
        y = Math.min(y, max);
        for (int dy = 0; dy <= 10 && y + dy <= max; dy++) {
            Block b = w.getBlockAt(x, y + dy, z);
            if ((b.isEmpty() || b.isLiquid() || b.isReplaceable()) && !tombes.containsKey(cle(b))) return b;
        }
        return null;
    }

    // ------------------------------------------------------------ recuperation

    @EventHandler(priority = EventPriority.LOW)
    public void onClic(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getClickedBlock() == null) return;
        Tombe t = tombes.get(cle(e.getClickedBlock()));
        if (t == null) return;
        e.setCancelled(true); // jamais d'ouverture du coffre (vide)
        if (e.getHand() == EquipmentSlot.HAND) recuperer(e.getPlayer(), e.getClickedBlock(), t);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onCasse(BlockBreakEvent e) {
        Tombe t = tombes.get(cle(e.getBlock()));
        if (t == null) return;
        e.setCancelled(true);
        recuperer(e.getPlayer(), e.getBlock(), t);
    }

    private void recuperer(Player p, Block b, Tombe t) {
        boolean proprio = p.getUniqueId().equals(t.proprio());
        int minutes = plugin.getConfig().getInt("tombe.protection-minutes", 0);
        boolean libre = minutes > 0 && System.currentTimeMillis() - t.date() > minutes * 60_000L;
        if (!proprio && !libre && !p.hasPermission("survieplus.admin")) {
            p.sendMessage(SurviePlus.PREFIXE.append(Component.text(
                    "C'est la tombe de " + t.nom() + " : tu ne peux pas l'ouvrir.", NamedTextColor.RED)));
            return;
        }

        tombes.remove(cle(b));
        sauvegarder();
        boolean eau = b.getBlockData() instanceof Waterlogged w && w.isWaterlogged();
        b.setType(eau ? Material.WATER : Material.AIR);

        // L'armure et le bouclier se remettent en place si l'emplacement est libre
        PlayerInventory inv = p.getInventory();
        List<ItemStack> reste = new ArrayList<>();
        for (ItemStack it : t.objets()) {
            EquipmentSlot slot = it.getType().getEquipmentSlot();
            if (ARMURE.contains(slot) && inv.getItem(slot).isEmpty()) {
                inv.setItem(slot, it);
                continue;
            }
            reste.addAll(inv.addItem(it).values());
        }
        reste.forEach(it -> p.getWorld().dropItemNaturally(p.getLocation(), it));
        if (t.xp() > 0) p.giveExp(t.xp());

        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.2f);
        p.sendMessage(SurviePlus.PREFIXE.append(Component.text(
                proprio ? "Tu as recupere tes affaires !" : "Tu as ouvert la tombe de " + t.nom() + ".",
                NamedTextColor.GREEN)));
        if (!reste.isEmpty())
            p.sendMessage(SurviePlus.PREFIXE.append(Component.text(
                    "Inventaire plein : " + reste.size() + " objet(s) pose(s) a tes pieds.", NamedTextColor.GRAY)));
    }

    // ------------------------------------------------------------ protection

    @EventHandler(ignoreCancelled = true)
    public void onExplosion(EntityExplodeEvent e) {
        e.blockList().removeIf(b -> tombes.containsKey(cle(b)));
    }

    @EventHandler(ignoreCancelled = true)
    public void onExplosion(BlockExplodeEvent e) {
        e.blockList().removeIf(b -> tombes.containsKey(cle(b)));
    }

    // ------------------------------------------------------------ sauvegarde (tombes.yml)

    private void charger() {
        if (!fichier.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(fichier);
        for (String id : y.getKeys(false)) {
            ConfigurationSection s = y.getConfigurationSection(id);
            if (s == null) continue;
            try {
                byte[] octets = Base64.getDecoder().decode(s.getString("objets", ""));
                Tombe t = new Tombe(id, UUID.fromString(s.getString("proprio")), s.getString("nom", "?"),
                        s.getLong("date"), s.getInt("xp"), new ArrayList<>(Arrays.asList(ItemStack.deserializeItemsFromBytes(octets))));
                tombes.put(s.getString("position"), t);
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("Tombe illisible (" + id + ") : " + ex.getMessage());
            }
        }
        plugin.getLogger().info(tombes.size() + " tombe(s) chargee(s)");
    }

    private void sauvegarder() {
        YamlConfiguration y = new YamlConfiguration();
        tombes.forEach((position, t) -> {
            ConfigurationSection s = y.createSection(t.id());
            s.set("position", position);
            s.set("proprio", t.proprio().toString());
            s.set("nom", t.nom());
            s.set("date", t.date());
            s.set("xp", t.xp());
            s.set("objets", Base64.getEncoder().encodeToString(ItemStack.serializeItemsAsBytes(t.objets())));
        });
        try {
            y.save(fichier);
        } catch (IOException ex) {
            plugin.getLogger().severe("Impossible de sauvegarder tombes.yml : " + ex.getMessage());
        }
    }
}
