package fr.survieplus;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Difficulty;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.TreeType;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.block.Sign;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.type.Cocoa;
import org.bukkit.block.sign.Side;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * /sp test : monde plat "survieplus_test" avec une zone pour essayer tous les modules.
 * En arrivant (spawn tourne vers le sud) : arbres devant, filons a droite, cultures a gauche, kit d'outils a cote.
 */
final class MondeTest {

    static final String NOM = "survieplus_test";

    private final SurviePlus plugin;
    private final NamespacedKey cleConstruit;
    private final Map<UUID, Location> retours = new HashMap<>();

    MondeTest(SurviePlus plugin) {
        this.plugin = plugin;
        this.cleConstruit = new NamespacedKey(plugin, "zone_construite");
    }

    /** Aller dans le monde test, ou revenir si on y est deja. */
    void basculer(Player p) {
        if (p.getWorld().getName().equals(NOM)) {
            Location retour = retours.remove(p.getUniqueId());
            if (retour == null) retour = plugin.getServer().getWorlds().getFirst().getSpawnLocation();
            p.teleportAsync(retour);
            message(p, "Retour au monde de survie.", NamedTextColor.GREEN);
            return;
        }
        World w = monde();
        if (!w.getPersistentDataContainer().has(cleConstruit)) construire(w);
        retours.put(p.getUniqueId(), p.getLocation());
        p.teleportAsync(spawn(w));
        message(p, "Bienvenue dans le monde test ! Arbres devant, filons a droite, cultures a gauche, outils dans le coffre.",
                NamedTextColor.GREEN);
        message(p, "/sp test pour revenir, /sp test reset pour tout reconstruire.", NamedTextColor.GRAY);
    }

    /** Efface et reconstruit la zone de test. */
    void reinitialiser(Player p) {
        World w = monde();
        int g = sol(w);
        for (int x = -50; x <= 50; x++)
            for (int z = -20; z <= 40; z++) {
                w.getBlockAt(x, g, z).setType(Material.GRASS_BLOCK, false);
                for (int y = g + 1; y <= g + 45; y++) {
                    Block b = w.getBlockAt(x, y, z);
                    if (!b.isEmpty()) b.setType(Material.AIR, false);
                }
            }
        w.getEntitiesByClass(Item.class).forEach(Item::remove);
        construire(w);
        if (p.getWorld().equals(w)) p.teleportAsync(spawn(w));
        message(p, "Zone de test reconstruite.", NamedTextColor.GREEN);
    }

    private World monde() {
        World w = plugin.getServer().getWorld(NOM);
        if (w == null) {
            w = new WorldCreator(NOM).type(WorldType.FLAT).generateStructures(false).createWorld();
            w.setDifficulty(Difficulty.PEACEFUL);
            w.setSpawnLocation(0, sol(w) + 1, -8);
        }
        return w;
    }

    private static int sol(World w) {
        return w.getHighestBlockYAt(0, -8);
    }

    private static Location spawn(World w) {
        return new Location(w, 0.5, sol(w) + 1, -8.5, 0f, 0f); // regard vers le sud (+z)
    }

    private static void message(Player p, String s, NamedTextColor c) {
        p.sendMessage(SurviePlus.PREFIXE.append(Component.text(s, c)));
    }

    // ------------------------------------------------------------ construction

    private void construire(World w) {
        int g = sol(w);
        w.setTime(6000);
        w.setStorm(false);

        // --- Arbres (devant) : rangee 1 a z=14, rangee 2 a z=30
        TreeType[] rangee1 = {TreeType.TREE, TreeType.BIG_TREE, TreeType.BIRCH, TreeType.REDWOOD, TreeType.DARK_OAK,
                TreeType.ACACIA, TreeType.CHERRY, TreeType.PALE_OAK, TreeType.RED_POPLAR};
        for (int i = 0; i < rangee1.length; i++) arbre(w, -32 + i * 8, g, 14, rangee1[i], Material.GRASS_BLOCK);
        // Deux chenes colles : les feuilles du voisin doivent rester
        arbre(w, -40, g, 14, TreeType.TREE, Material.GRASS_BLOCK);
        arbre(w, -44, g, 14, TreeType.TREE, Material.GRASS_BLOCK);

        arbre(w, -30, g, 30, TreeType.JUNGLE, Material.GRASS_BLOCK);
        arbre(w, -18, g, 30, TreeType.MEGA_REDWOOD, Material.GRASS_BLOCK);
        arbre(w, -6, g, 30, TreeType.MANGROVE, Material.MUD);
        arbre(w, 4, g, 30, TreeType.YELLOW_POPLAR, Material.GRASS_BLOCK);
        arbre(w, 12, g, 30, TreeType.CRIMSON_FUNGUS, Material.CRIMSON_NYLIUM);
        arbre(w, 20, g, 30, TreeType.WARPED_FUNGUS, Material.WARPED_NYLIUM);
        arbre(w, 28, g, 30, TreeType.RED_MUSHROOM, Material.MYCELIUM);
        arbre(w, 36, g, 30, TreeType.BROWN_MUSHROOM, Material.MYCELIUM);

        // --- Filons (a droite = ouest), z = 0..3
        int y = g + 1;
        cube(w, -8, y, 0, 2, 2, 1, Material.IRON_ORE);
        panneau(w, -8, y, -2, "Petit filon", "de fer (4)");
        cube(w, -15, y, 0, 4, 3, 4, Material.COAL_ORE);
        cube(w, -15, y, 0, 4, 1, 4, Material.DEEPSLATE_COAL_ORE);
        panneau(w, -14, y, -2, "Gros filon", "de charbon (48)");
        cube(w, -20, y, 0, 2, 2, 2, Material.DEEPSLATE_DIAMOND_ORE);
        panneau(w, -20, y, -2, "Diamant", "(pioche fer+)");
        cube(w, -26, y, 0, 3, 3, 3, Material.COPPER_ORE);
        panneau(w, -25, y, -2, "Cuivre (27)", "");
        cube(w, -31, y, 0, 2, 2, 2, Material.GOLD_ORE);
        panneau(w, -31, y, -2, "Or", "");
        cube(w, -36, y, 0, 3, 2, 2, Material.REDSTONE_ORE);
        panneau(w, -35, y, -2, "Redstone", "");
        cube(w, -41, y, 0, 2, 2, 2, Material.LAPIS_ORE);
        panneau(w, -41, y, -2, "Lapis", "");
        cube(w, -45, y, 0, 1, 3, 1, Material.EMERALD_ORE);
        panneau(w, -45, y, -2, "Emeraude", "");
        cube(w, -49, y, 0, 2, 1, 2, Material.ANCIENT_DEBRIS);
        panneau(w, -49, y, -2, "Debris", "antiques");

        // --- Cultures (a gauche = est), parcelles 5x5 avec de l'eau au centre
        Material[] cultures = {Material.WHEAT, Material.CARROTS, Material.POTATOES, Material.BEETROOTS};
        String[] noms = {"Ble", "Carottes", "Patates", "Betteraves"};
        for (int i = 0; i < cultures.length; i++) {
            int x0 = 6 + i * 7;
            for (int dx = 0; dx < 5; dx++)
                for (int dz = 0; dz < 5; dz++) {
                    Block sol = w.getBlockAt(x0 + dx, g, dz);
                    if (dx == 2 && dz == 2) { sol.setType(Material.WATER); continue; }
                    sol.setType(Material.FARMLAND);
                    mure(sol.getRelative(BlockFace.UP), cultures[i]);
                }
            panneau(w, x0 + 2, y, -2, noms[i], "(clic droit)");
        }
        // Verrues du Nether sur sable des ames
        for (int dx = 0; dx < 3; dx++)
            for (int dz = 0; dz < 3; dz++) {
                Block sol = w.getBlockAt(34 + dx, g, 1 + dz);
                sol.setType(Material.SOUL_SAND);
                mure(sol.getRelative(BlockFace.UP), Material.NETHER_WART);
            }
        panneau(w, 35, y, -2, "Verrues", "du Nether");
        // Cacao sur une colonne de bois d'acajou
        for (int dy = 0; dy < 3; dy++) {
            w.getBlockAt(42, y + dy, 2).setType(Material.JUNGLE_LOG);
            cacao(w.getBlockAt(41, y + dy, 2), BlockFace.EAST);
            cacao(w.getBlockAt(43, y + dy, 2), BlockFace.WEST);
            cacao(w.getBlockAt(42, y + dy, 1), BlockFace.SOUTH);
        }
        panneau(w, 42, y, -2, "Cacao", "");

        // --- Kit d'outils a cote du spawn
        kit(w.getBlockAt(-2, y, -7));
        panneau(w, -2, y, -9, "Kit de test", "");
        bazar(w.getBlockAt(2, y, -7));
        panneau(w, 2, y, -9, "Coffre en bazar", "(Maj+clic droit)");

        w.getPersistentDataContainer().set(cleConstruit, PersistentDataType.BOOLEAN, true);
    }

    private static void arbre(World w, int x, int g, int z, TreeType type, Material sol) {
        for (int dx = -1; dx <= 2; dx++)
            for (int dz = -1; dz <= 2; dz++) w.getBlockAt(x + dx, g, z + dz).setType(sol);
        w.generateTree(new Location(w, x, g + 1, z), new java.util.Random(), type);
    }

    private static void cube(World w, int x, int y, int z, int lx, int ly, int lz, Material m) {
        for (int dx = 0; dx < lx; dx++)
            for (int dy = 0; dy < ly; dy++)
                for (int dz = 0; dz < lz; dz++) w.getBlockAt(x + dx, y + dy, z + dz).setType(m);
    }

    private static void mure(Block b, Material culture) {
        b.setType(culture);
        if (b.getBlockData() instanceof Ageable a) {
            a.setAge(a.getMaximumAge());
            b.setBlockData(a);
        }
    }

    private static void cacao(Block b, BlockFace versTronc) {
        b.setType(Material.COCOA);
        if (b.getBlockData() instanceof Cocoa c) {
            c.setFacing(versTronc);
            c.setAge(c.getMaximumAge());
            b.setBlockData(c);
        }
    }

    private static void panneau(World w, int x, int y, int z, String ligne1, String ligne2) {
        Block b = w.getBlockAt(x, y, z);
        b.setType(Material.OAK_SIGN);
        if (b.getBlockData() instanceof org.bukkit.block.data.type.Sign d) {
            d.setRotation(BlockFace.NORTH); // lisible depuis le spawn
            b.setBlockData(d);
        }
        if (b.getState() instanceof Sign s) {
            s.getSide(Side.FRONT).line(1, Component.text(ligne1));
            s.getSide(Side.FRONT).line(2, Component.text(ligne2));
            s.update();
        }
    }

    /** Un coffre rempli n'importe comment de piles incompletes, pour tester le tri. */
    private static void bazar(Block b) {
        b.setType(Material.CHEST);
        if (!(b.getState() instanceof Chest coffre)) return;
        Material[] melange = {Material.COBBLESTONE, Material.OAK_LOG, Material.IRON_INGOT, Material.BREAD,
                Material.DIRT, Material.BIRCH_LOG, Material.GOLD_INGOT, Material.STONE_PICKAXE, Material.APPLE,
                Material.SPRUCE_LOG, Material.COAL, Material.IRON_SWORD, Material.WHEAT_SEEDS, Material.COPPER_INGOT};
        var inv = coffre.getBlockInventory();
        java.util.Random r = new java.util.Random(42);
        for (int slot = 0; slot < inv.getSize(); slot++) {
            if (r.nextInt(3) == 0) continue; // quelques trous
            Material m = melange[r.nextInt(melange.length)];
            inv.setItem(slot, new ItemStack(m, Math.min(m.getMaxStackSize(), 1 + r.nextInt(20))));
        }
    }

    private static void kit(Block b) {
        b.setType(Material.CHEST);
        if (!(b.getState() instanceof Chest coffre)) return;
        var inv = coffre.getBlockInventory();
        inv.addItem(
                outil(Material.NETHERITE_AXE, "Hache", Map.of(Enchantment.EFFICIENCY, 5, Enchantment.UNBREAKING, 3)),
                outil(Material.NETHERITE_PICKAXE, "Pioche Fortune III", Map.of(Enchantment.FORTUNE, 3, Enchantment.EFFICIENCY, 5)),
                outil(Material.NETHERITE_PICKAXE, "Pioche Toucher de soie", Map.of(Enchantment.SILK_TOUCH, 1, Enchantment.EFFICIENCY, 5)),
                outil(Material.STONE_PICKAXE, "Pioche en pierre (pas de diamant)", Map.of()),
                outil(Material.DIAMOND_HOE, "Houe Fortune III", Map.of(Enchantment.FORTUNE, 3)),
                presqueCasse(Material.IRON_AXE, "Hache presque cassee (3 coups)"),
                presqueCasse(Material.IRON_PICKAXE, "Pioche presque cassee (3 coups)"),
                new ItemStack(Material.BONE_MEAL, 64),
                new ItemStack(Material.EXPERIENCE_BOTTLE, 32),
                new ItemStack(Material.IRON_CHESTPLATE),
                new ItemStack(Material.SHIELD));
    }

    private static ItemStack outil(Material m, String nom, Map<Enchantment, Integer> enchants) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Component.text(nom, NamedTextColor.AQUA));
        enchants.forEach((e, n) -> meta.addEnchant(e, n, true));
        it.setItemMeta(meta);
        return it;
    }

    private static ItemStack presqueCasse(Material m, String nom) {
        ItemStack it = outil(m, nom, Map.of());
        if (it.getItemMeta() instanceof Damageable d) {
            int max = d.hasMaxDamage() ? d.getMaxDamage() : m.getMaxDurability();
            d.setDamage(max - 4);
            it.setItemMeta(d);
        }
        return it;
    }
}
