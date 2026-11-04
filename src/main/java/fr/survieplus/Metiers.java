package fr.survieplus;

import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Particle;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.FishHook;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Boss;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerHarvestBlockEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Metiers : on gagne de l'XP en jouant (bucheron, mineur, fermier, pecheur, chasseur).
 * Les niveaux donnent une chance de recolte / butin double, un titre dans le chat,
 * et pour le fermier la recolte au clic droit en 3x3.
 * Les blocs poses par les joueurs ne rapportent rien (marques dans les donnees du chunk).
 * Sauvegarde dans metiers.yml.
 */
final class Metiers implements Listener {

    static final String PERMISSION = "survieplus.metiers";
    private static final String[] RANGS = {"Apprenti", "Compagnon", "Expert", "Maître", "Grand maître"};
    private static final Set<Material> CULTURES = Set.of(Material.WHEAT, Material.CARROTS, Material.POTATOES,
            Material.BEETROOTS, Material.NETHER_WART, Material.COCOA);
    private static final Set<Material> TRESORS = Set.of(Material.ENCHANTED_BOOK, Material.NAME_TAG,
            Material.NAUTILUS_SHELL, Material.SADDLE);

    static final class Donnees {
        volatile String nom;
        final long[] xp = new long[Metier.values().length];

        Donnees(String nom) {
            this.nom = nom;
        }
    }

    record Progression(int niveau, long actuel, long requis) {
        boolean max() {
            return requis <= 0;
        }
    }

    record Place(String nom, long xp, int niveau) {}

    private static final class Barre {
        final BossBar bar = BossBar.bossBar(Component.empty(), 0f, BossBar.Color.GREEN, BossBar.Overlay.PROGRESS);
        Metier metier;
        long gain;
        BukkitTask masquer;
    }

    private final SurviePlus plugin;
    final Recompenses recompenses;
    private final File fichier;
    private final NamespacedKey clePoses;
    private final Map<UUID, Donnees> joueurs = new ConcurrentHashMap<>();
    private final Map<UUID, Barre> barres = new HashMap<>();
    /** Blocs qui viennent d'etre casses -> effets a appliquer a leurs objets (DOUBLE, FONTE). */
    private final Map<Block, Integer> enAttente = new HashMap<>();
    private final Object verrouFichier = new Object();
    private volatile boolean modifie;

    Metiers(SurviePlus plugin) {
        this.plugin = plugin;
        this.recompenses = new Recompenses(plugin);
        this.fichier = new File(plugin.getDataFolder(), "metiers.yml");
        this.clePoses = new NamespacedKey(plugin, "poses");
        charger();
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> sauvegarder(true), 6000L, 6000L);
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (actif()) faireGrandir();
        }, 200L, 200L);
    }

    private FileConfiguration cfg() {
        return plugin.getConfig();
    }

    boolean actif() {
        return cfg().getBoolean("metiers.active", true);
    }

    /** Le joueur gagne-t-il de l'XP de metier ? (pas en creatif ni en spectateur) */
    private boolean compte(Player p) {
        GameMode g = p.getGameMode();
        return actif() && p.hasPermission(PERMISSION) && (g == GameMode.SURVIVAL || g == GameMode.ADVENTURE);
    }

    // ------------------------------------------------------------ niveaux

    int niveauMax() {
        return Math.max(1, cfg().getInt("metiers.niveau-max", 50));
    }

    private long xpRequis(int niveau) {
        return Math.max(1, cfg().getLong("metiers.xp-base", 50) + (long) niveau * cfg().getLong("metiers.xp-par-niveau", 25));
    }

    Progression progression(long xp) {
        int max = niveauMax();
        for (int n = 0; n < max; n++) {
            long r = xpRequis(n);
            if (xp < r) return new Progression(n, xp, r);
            xp -= r;
        }
        return new Progression(max, 0, 0);
    }

    Progression progression(UUID id, Metier m) {
        Donnees d = joueurs.get(id);
        return progression(d == null ? 0 : d.xp[m.ordinal()]);
    }

    int niveau(UUID id, Metier m) {
        return progression(id, m).niveau();
    }

    /** Chance (0 a 1) de recolte / prise / butin double a ce niveau. */
    double chanceDouble(int niveau) {
        double pct = Math.min(cfg().getDouble("metiers.double-max", 25), niveau * cfg().getDouble("metiers.double-par-niveau", 0.5));
        return Math.max(0, pct) / 100.0;
    }

    private boolean tirageDouble(Player p, Metier m) {
        return ThreadLocalRandom.current().nextDouble() < chanceDouble(niveau(p.getUniqueId(), m));
    }

    /** Niveau qui debloque le bonus 1 ou 2 de chaque metier (0 = jamais). */
    int niveauPalier(int palier) {
        return cfg().getInt("metiers.bonus-niveau-" + palier, palier == 1 ? 20 : 35);
    }

    /** Le joueur a-t-il debloque ce bonus (palier 1 ou 2) de ce metier ? */
    private boolean bonus(Player p, Metier m, int palier) {
        int niv = niveauPalier(palier);
        return niv > 0 && compte(p) && niveau(p.getUniqueId(), m) >= niv;
    }

    /** Chance (0 a 1) lue dans metiers.bonus.<cle> (en %). */
    private boolean chance(String cle, double defaut) {
        return ThreadLocalRandom.current().nextDouble() * 100 < cfg().getDouble("metiers.bonus." + cle, defaut);
    }

    /** 1 si la recolte au clic droit doit prendre une zone de 3x3, sinon 0. */
    int rayonRecolte(Player p) {
        return bonus(p, Metier.FERMIER, 1) ? 1 : 0;
    }

    int niveauTotal(UUID id) {
        int total = 0;
        for (Metier m : Metier.values()) total += niveau(id, m);
        return total;
    }

    /** Titre du meilleur metier (a partir du niveau 10), ou null. */
    String titre(UUID id) {
        Metier meilleur = null;
        int niv = 0;
        for (Metier m : Metier.values()) {
            int n = niveau(id, m);
            if (n > niv) { niv = n; meilleur = m; }
        }
        String rang = rang(niv);
        return rang == null ? null : rang + " " + meilleur.nom.toLowerCase();
    }

    private Metier meilleurMetier(UUID id) {
        Metier meilleur = Metier.BUCHERON;
        int niv = -1;
        for (Metier m : Metier.values()) {
            int n = niveau(id, m);
            if (n > niv) { niv = n; meilleur = m; }
        }
        return meilleur;
    }

    static String rang(int niveau) {
        int i = Math.min(RANGS.length, niveau / 10) - 1;
        return i < 0 ? null : RANGS[i];
    }

    // ------------------------------------------------------------ gain d'XP

    private Donnees donnees(Player p) {
        Donnees d = joueurs.computeIfAbsent(p.getUniqueId(), k -> new Donnees(p.getName()));
        d.nom = p.getName();
        return d;
    }

    /** XP gagnee en jouant : bonus de groupe si un autre joueur est proche. */
    void gagner(Player p, Metier m, long xp) {
        if (xp <= 0) return;
        boolean groupe = enGroupe(p);
        if (groupe) {
            double extra = xp * cfg().getDouble("metiers.groupe.bonus-pourcent", 10) / 100.0;
            long entier = (long) extra;
            // la partie decimale devient une chance de +1 (sinon +10 % de 2 XP ne donnerait jamais rien)
            if (ThreadLocalRandom.current().nextDouble() < extra - entier) entier++;
            xp += entier;
        }
        ajouter(p, m, xp, groupe);
    }

    private boolean enGroupe(Player p) {
        if (!cfg().getBoolean("metiers.groupe.actif", true)) return false;
        double rayon = cfg().getDouble("metiers.groupe.rayon", 20);
        double r2 = rayon * rayon;
        for (Player autre : p.getWorld().getPlayers())
            if (autre != p && autre.getGameMode() != GameMode.SPECTATOR
                    && autre.getLocation().distanceSquared(p.getLocation()) <= r2) return true;
        return false;
    }

    /** Ajoute l'XP telle quelle (commande admin, ou apres le bonus de groupe). */
    void ajouter(Player p, Metier m, long xp, boolean groupe) {
        if (xp <= 0) return;
        Donnees d = donnees(p);
        int i = m.ordinal();
        int avant = progression(d.xp[i]).niveau();
        d.xp[i] = Math.max(0, d.xp[i] + xp);
        modifie = true;
        Progression apres = progression(d.xp[i]);
        afficher(p, m, xp, groupe, apres);
        if (apres.niveau() > avant) niveauGagne(p, m, avant, apres.niveau());
    }

    void reinitialiser(Player p) {
        Arrays.fill(donnees(p).xp, 0);
        modifie = true;
    }

    private void niveauGagne(Player p, Metier m, int avant, int niveau) {
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.2f);
        p.sendMessage(SurviePlus.PREFIXE
                .append(Component.text(m.nom + " niveau " + niveau + " ! ", m.couleur))
                .append(Component.text(m.bonus + " : " + pourcent(chanceDouble(niveau)), NamedTextColor.GRAY)));
        recompenses.donner(p, m, avant, niveau);
        for (Module mod : Module.values()) {
            int niv = plugin.niveauRequis(mod);
            if (mod.metierRequis() == m && niv > 0 && avant < niv && niveau >= niv)
                p.sendMessage(SurviePlus.PREFIXE.append(Component.text(
                        "Débloqué : " + mod.nom + " ! (" + mod.description + ")", NamedTextColor.GREEN)));
        }
        for (int palier = 1; palier <= 2; palier++) {
            int niv = niveauPalier(palier);
            if (niv > 0 && avant < niv && niveau >= niv)
                p.sendMessage(SurviePlus.PREFIXE.append(Component.text(
                        "Nouveau bonus : " + m.paliers[palier - 1] + " !", NamedTextColor.GREEN)));
        }
        String rang = rang(niveau);
        if (rang == null || rang.equals(rang(avant))) return;
        String titre = rang + " " + m.nom.toLowerCase();
        int annonce = cfg().getInt("metiers.annonce-niveau", 40);
        if (annonce > 0 && niveau >= annonce) {
            Bukkit.broadcast(SurviePlus.PREFIXE
                    .append(Component.text(p.getName(), NamedTextColor.WHITE))
                    .append(Component.text(" est devenu " + titre + " !", m.couleur)));
            for (Player autre : Bukkit.getOnlinePlayers())
                autre.playSound(autre.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.6f, 1f);
        } else {
            p.sendMessage(SurviePlus.PREFIXE.append(Component.text("Nouveau titre : " + titre, NamedTextColor.LIGHT_PURPLE)));
        }
    }

    static String pourcent(double chance) {
        double pct = chance * 100;
        return (pct == Math.rint(pct) ? String.valueOf((long) pct) : String.format("%.1f", pct)) + " %";
    }

    // ------------------------------------------------------------ barre de progression

    private void afficher(Player p, Metier m, long gain, boolean groupe, Progression pr) {
        if (!plugin.actif(p, Module.BARRE_METIERS)) return;
        Barre b = barres.computeIfAbsent(p.getUniqueId(), k -> new Barre());
        if (b.metier != m) b.gain = 0;
        b.metier = m;
        b.gain += gain;
        Component nom = Component.text(m.nom + " niv. " + pr.niveau(), m.couleur)
                .append(Component.text(pr.max() ? "  ·  niveau max" : "  ·  " + pr.actuel() + " / " + pr.requis() + " XP",
                        NamedTextColor.WHITE))
                .append(Component.text("  (+" + b.gain + ")", NamedTextColor.GREEN))
                .append(groupe ? Component.text("  en groupe", NamedTextColor.LIGHT_PURPLE) : Component.empty());
        b.bar.name(nom);
        b.bar.color(m.couleurBarre);
        b.bar.progress(pr.max() ? 1f : Math.min(1f, (float) pr.actuel() / pr.requis()));
        p.showBossBar(b.bar);
        if (b.masquer != null) b.masquer.cancel();
        b.masquer = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            p.hideBossBar(b.bar);
            b.gain = 0;
            b.metier = null;
            b.masquer = null;
        }, 60L);
    }

    // ------------------------------------------------------------ blocs poses par les joueurs

    /** Blocs qui rapportent de l'XP une fois casses : on retient ceux poses par un joueur. */
    private boolean suivi(Material m) {
        return Tag.LOGS.isTagged(m) || m == Material.MELON || m == Material.PUMPKIN || m == Material.SUGAR_CANE
                || xpMinerai(m) > 0;
    }

    private static int code(Block b) {
        return ((b.getY() + 4096) << 8) | ((b.getX() & 15) << 4) | (b.getZ() & 15);
    }

    private boolean pose(Block b) {
        int[] t = b.getChunk().getPersistentDataContainer().get(clePoses, PersistentDataType.INTEGER_ARRAY);
        if (t == null) return false;
        int c = code(b);
        for (int v : t) if (v == c) return true;
        return false;
    }

    private void marquer(Block b, boolean pose) {
        PersistentDataContainer pdc = b.getChunk().getPersistentDataContainer();
        int[] t = pdc.getOrDefault(clePoses, PersistentDataType.INTEGER_ARRAY, new int[0]);
        int c = code(b);
        int[] nouveau;
        if (pose) {
            for (int v : t) if (v == c) return;
            nouveau = Arrays.copyOf(t, t.length + 1);
            nouveau[t.length] = c;
        } else {
            nouveau = Arrays.stream(t).filter(v -> v != c).toArray();
            if (nouveau.length == t.length) return;
        }
        if (nouveau.length == 0) pdc.remove(clePoses);
        else pdc.set(clePoses, PersistentDataType.INTEGER_ARRAY, nouveau);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPose(BlockPlaceEvent e) {
        Block b = e.getBlockPlaced();
        if (suivi(b.getType())) marquer(b, true);
    }

    // Un bloc pose puis pousse par un piston reste "pose" a sa nouvelle place
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonSort(BlockPistonExtendEvent e) {
        deplacerMarques(e.getBlocks(), e.getDirection());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonRentre(BlockPistonRetractEvent e) {
        deplacerMarques(e.getBlocks(), e.getDirection());
    }

    private void deplacerMarques(List<Block> blocs, BlockFace sens) {
        List<Block> marques = new ArrayList<>();
        for (Block b : blocs)
            if (suivi(b.getType()) && pose(b)) marques.add(b);
        for (Block b : marques) marquer(b, false);
        for (Block b : marques) marquer(b.getRelative(sens), true);
    }

    // ------------------------------------------------------------ bucheron, mineur, fermier (blocs)

    private long xpMinerai(Material m) {
        String nom = m.name();
        if (nom.startsWith("DEEPSLATE_")) nom = nom.substring("DEEPSLATE_".length());
        return cfg().getLong("metiers.xp.mineur." + nom, 0);
    }

    private long xpCulture(Block b) {
        Material m = b.getType();
        if (CULTURES.contains(m))
            return b.getBlockData() instanceof Ageable a && a.getAge() >= a.getMaximumAge()
                    ? cfg().getLong("metiers.xp.fermier.culture", 2) : 0;
        if (m == Material.MELON || m == Material.PUMPKIN) return cfg().getLong("metiers.xp.fermier.melon-citrouille", 3);
        if (m == Material.SUGAR_CANE) return cfg().getLong("metiers.xp.fermier.canne-a-sucre", 1);
        return 0;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCasse(BlockBreakEvent e) {
        Block b = e.getBlock();
        Material type = b.getType();
        boolean etaitPose = suivi(type) && pose(b);
        if (etaitPose) marquer(b, false);
        // isDropItems() == false : casse "pour de faux" (ex : test de protection de la recolte au clic droit)
        if (etaitPose || !e.isDropItems()) return;
        Player p = e.getPlayer();
        if (!compte(p)) return;

        Metier m;
        long xp;
        if (Tag.LOGS.isTagged(type)) {
            m = Metier.BUCHERON;
            xp = cfg().getLong("metiers.xp.bucheron", 2);
        } else if ((xp = xpMinerai(type)) > 0) {
            m = Metier.MINEUR;
        } else if ((xp = xpCulture(b)) > 0) {
            m = Metier.FERMIER;
        } else return;

        ItemStack outil = p.getInventory().getItemInMainHand();
        if (b.getDrops(outil, p).isEmpty()) return; // mauvais outil : le bloc est detruit sans rien donner
        gagner(p, m, xp);

        // Bucheron palier 2 : pomme ou pousse en plus
        if (m == Metier.BUCHERON && bonus(p, m, 2) && chance("bucheron-fruit", 5)) {
            Material pousse = Bucheron.pousse(type);
            Material fruit = pousse == null || ThreadLocalRandom.current().nextBoolean() ? Material.APPLE : pousse;
            b.getWorld().dropItemNaturally(b.getLocation().add(0.5, 0.5, 0.5), new ItemStack(fruit));
        }

        // Pas de double ni de fonte avec Toucher de soie (sinon on duplique des blocs de minerai)
        if (outil.containsEnchantment(Enchantment.SILK_TOUCH)) return;
        int effets = 0;
        if (tirageDouble(p, m)) effets |= DOUBLE;
        if (m == Metier.MINEUR && bonus(p, m, 2)) effets |= FONTE; // Mineur palier 2
        if (effets == 0) return;
        if (enAttente.isEmpty()) plugin.getServer().getScheduler().runTask(plugin, enAttente::clear);
        enAttente.put(b, effets);
    }

    private static final int DOUBLE = 1, FONTE = 2;
    private static final Map<Material, Material> FONTES = Map.of(
            Material.RAW_IRON, Material.IRON_INGOT,
            Material.RAW_GOLD, Material.GOLD_INGOT,
            Material.RAW_COPPER, Material.COPPER_INGOT);

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onObjetsDuBloc(BlockDropItemEvent e) {
        Integer effets = enAttente.remove(e.getBlock());
        if (effets == null) return;
        for (Item it : e.getItems()) {
            ItemStack s = it.getItemStack();
            Material lingot = FONTES.get(s.getType());
            if ((effets & FONTE) != 0 && lingot != null) s = s.withType(lingot);
            if ((effets & DOUBLE) != 0) s.setAmount(Math.min(s.getMaxStackSize(), s.getAmount() * 2));
            it.setItemStack(s);
        }
    }

    // Bucheron / Mineur palier 1 : l'outil s'use moins pendant l'abattage d'un arbre ou le minage d'un filon
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onUsure(PlayerItemDamageEvent e) {
        Player p = e.getPlayer();
        if (!Blocs.EN_COURS.contains(p.getUniqueId())) return;
        Material outil = e.getItem().getType();
        Metier m = Tag.ITEMS_AXES.isTagged(outil) ? Metier.BUCHERON
                : Tag.ITEMS_PICKAXES.isTagged(outil) ? Metier.MINEUR : null;
        if (m != null && bonus(p, m, 1) && chance("usure-reduite", 50)) e.setCancelled(true);
    }

    // Fermier palier 2 : toutes les 10 s, les cultures proches ont une chance de pousser d'un cran
    private void faireGrandir() {
        int rayon = Math.max(1, cfg().getInt("metiers.bonus.croissance-rayon", 4));
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!bonus(p, Metier.FERMIER, 2)) continue;
            Block centre = p.getLocation().getBlock();
            for (int dx = -rayon; dx <= rayon; dx++)
                for (int dy = -2; dy <= 2; dy++)
                    for (int dz = -rayon; dz <= rayon; dz++) {
                        Block b = centre.getRelative(dx, dy, dz);
                        if (!POUSSENT.contains(b.getType()) || !(b.getBlockData() instanceof Ageable a)
                                || a.getAge() >= a.getMaximumAge() || !chance("croissance-chance", 10)) continue;
                        BlockState etat = b.getState();
                        a.setAge(a.getAge() + 1);
                        etat.setBlockData(a);
                        if (!new BlockGrowEvent(b, etat).callEvent()) continue;
                        etat.update(true);
                        b.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, b.getLocation().add(0.5, 0.5, 0.5), 3, 0.25, 0.25, 0.25);
                    }
        }
    }

    private static final Set<Material> POUSSENT = Set.of(Material.WHEAT, Material.CARROTS, Material.POTATOES,
            Material.BEETROOTS, Material.NETHER_WART, Material.COCOA, Material.MELON_STEM, Material.PUMPKIN_STEM,
            Material.SWEET_BERRY_BUSH);

    /** Recolte au clic droit (module Recolte) : donne l'XP de fermier, renvoie true si la recolte est doublee. */
    boolean recolteManuelle(Player p) {
        if (!compte(p)) return false;
        gagner(p, Metier.FERMIER, cfg().getLong("metiers.xp.fermier.culture", 2));
        return tirageDouble(p, Metier.FERMIER);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBaies(PlayerHarvestBlockEvent e) {
        Material m = e.getHarvestedBlock().getType();
        if (m != Material.SWEET_BERRY_BUSH && m != Material.CAVE_VINES && m != Material.CAVE_VINES_PLANT) return;
        Player p = e.getPlayer();
        if (!compte(p)) return;
        gagner(p, Metier.FERMIER, cfg().getLong("metiers.xp.fermier.baies", 1));
        if (tirageDouble(p, Metier.FERMIER))
            for (ItemStack s : e.getItemsHarvested()) s.setAmount(Math.min(s.getMaxStackSize(), s.getAmount() * 2));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onElevage(EntityBreedEvent e) {
        if (e.getBreeder() instanceof Player p && compte(p))
            gagner(p, Metier.FERMIER, cfg().getLong("metiers.xp.fermier.elevage", 5));
    }

    // ------------------------------------------------------------ pecheur

    private static final List<Material> TRESORS_PECHE = List.of(Material.ENCHANTED_BOOK, Material.NAME_TAG,
            Material.NAUTILUS_SHELL, Material.SADDLE, Material.BOW, Material.FISHING_ROD);

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPeche(PlayerFishEvent e) {
        Player p = e.getPlayer();
        // Pecheur palier 1 : le poisson mord plus vite
        if (e.getState() == PlayerFishEvent.State.FISHING) {
            if (!bonus(p, Metier.PECHEUR, 1)) return;
            FishHook hook = e.getHook();
            double f = 1 - Math.clamp(cfg().getDouble("metiers.bonus.peche-rapide", 30), 0, 90) / 100.0;
            hook.setWaitTime((int) (hook.getMinWaitTime() * f), Math.max(1, (int) (hook.getMaxWaitTime() * f)));
            return;
        }
        if (e.getState() != PlayerFishEvent.State.CAUGHT_FISH || !(e.getCaught() instanceof Item prise)) return;
        if (!compte(p)) return;
        ItemStack s = prise.getItemStack();
        // Pecheur palier 2 : une prise ordinaire peut devenir un tresor
        if (!TRESORS.contains(s.getType()) && s.getEnchantments().isEmpty()
                && bonus(p, Metier.PECHEUR, 2) && chance("peche-tresor", 10)) {
            Material type = TRESORS_PECHE.get(ThreadLocalRandom.current().nextInt(TRESORS_PECHE.size()));
            s = new ItemStack(type == Material.ENCHANTED_BOOK ? Material.BOOK : type);
            if (type == Material.ENCHANTED_BOOK || type == Material.BOW || type == Material.FISHING_ROD)
                s = Bukkit.getItemFactory().enchantWithLevels(s, 30,
                        io.papermc.paper.registry.RegistryAccess.registryAccess()
                                .getRegistry(io.papermc.paper.registry.RegistryKey.ENCHANTMENT)
                                .getTag(io.papermc.paper.registry.keys.tags.EnchantmentTagKeys.ON_RANDOM_LOOT),
                        new java.util.Random());
            prise.setItemStack(s);
        }
        String categorie = Tag.ITEMS_FISHES.isTagged(s.getType()) ? "poisson"
                : TRESORS.contains(s.getType()) || !s.getEnchantments().isEmpty() ? "tresor" : "dechet";
        gagner(p, Metier.PECHEUR, cfg().getLong("metiers.xp.pecheur." + categorie, 3));
        if (tirageDouble(p, Metier.PECHEUR))
            p.getInventory().addItem(s.clone()).values()
                    .forEach(reste -> p.getWorld().dropItem(p.getLocation(), reste));
    }

    // ------------------------------------------------------------ chasseur

    private static final Map<EntityType, Material> TETES = Map.of(
            EntityType.ZOMBIE, Material.ZOMBIE_HEAD,
            EntityType.SKELETON, Material.SKELETON_SKULL,
            EntityType.CREEPER, Material.CREEPER_HEAD,
            EntityType.WITHER_SKELETON, Material.WITHER_SKELETON_SKULL,
            EntityType.PIGLIN, Material.PIGLIN_HEAD);

    private long xpMob(LivingEntity e) {
        ConfigurationSection s = cfg().getConfigurationSection("metiers.xp.chasseur");
        if (s == null) return 0;
        String type = e.getType().name();
        if (s.contains(type)) return s.getLong(type);
        return e instanceof Enemy ? s.getLong("defaut", 5) : 0;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMort(EntityDeathEvent e) {
        LivingEntity mort = e.getEntity();
        Player p = mort.getKiller();
        if (p == null || mort instanceof Player || !compte(p)) return;
        if (cfg().getBoolean("metiers.ignorer-spawners", true) && mort.fromMobSpawner()) return;
        long xp = xpMob(mort);
        if (xp <= 0) return;
        gagner(p, Metier.CHASSEUR, xp);
        // Chasseur palier 1 : plus d'XP vanilla
        if (bonus(p, Metier.CHASSEUR, 1))
            e.setDroppedExp((int) Math.round(e.getDroppedExp() * (1 + cfg().getDouble("metiers.bonus.chasseur-xp", 50) / 100.0)));
        // Chasseur palier 2 : chance d'obtenir la tete du monstre
        Material tete = TETES.get(mort.getType());
        if (tete != null && bonus(p, Metier.CHASSEUR, 2) && chance("chasseur-tete", 5)
                && e.getDrops().stream().noneMatch(it -> it != null && it.getType() == tete))
            e.getDrops().add(new ItemStack(tete));
        if (mort instanceof Boss || !tirageDouble(p, Metier.CHASSEUR)) return;

        // On ne double pas l'equipement du monstre (sinon : donner un diamant a un zombie, le tuer...)
        List<ItemStack> equipement = new ArrayList<>();
        EntityEquipment eq = mort.getEquipment();
        if (eq != null) {
            equipement.add(eq.getItemInMainHand());
            equipement.add(eq.getItemInOffHand());
            equipement.addAll(Arrays.asList(eq.getArmorContents()));
        }
        List<ItemStack> bonus = new ArrayList<>();
        for (ItemStack it : e.getDrops())
            if (it != null && equipement.stream().noneMatch(it::isSimilar)) bonus.add(it.clone());
        e.getDrops().addAll(bonus);
    }

    // ------------------------------------------------------------ titre dans le chat

    /**
     * Ou afficher le titre dans le chat : "survol" (infobulle sur le pseudo, par defaut),
     * "prefixe" ([Expert mineur] devant le pseudo) ou "non".
     */
    String modeTitreChat() {
        String mode = cfg().getString("metiers.titre-chat", "survol").trim().toLowerCase(java.util.Locale.ROOT);
        return switch (mode) {
            case "prefixe", "prefix" -> "prefixe";
            case "non", "false", "aucun" -> "non";
            default -> "survol"; // "true" des anciennes configs compris
        };
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncChatEvent e) {
        String mode = modeTitreChat();
        if (!actif() || mode.equals("non")) return;
        UUID id = e.getPlayer().getUniqueId();
        String titre = titre(id);
        if (titre == null) return;
        NamedTextColor couleur = meilleurMetier(id).couleur;
        ChatRenderer ancien = e.renderer();
        if (mode.equals("prefixe")) {
            Component prefixe = Component.text("[" + titre + "] ", couleur);
            e.renderer((source, nom, message, lecteur) -> prefixe.append(ancien.render(source, nom, message, lecteur)));
            return;
        }
        // Survol : le pseudo (deja decore par Muscu, ex. "★5 Pseudo") garde son apparence,
        // le titre et le niveau total apparaissent dans une infobulle
        Component infobulle = Component.text(titre, couleur)
                .append(Component.text("\nNiveau total des métiers : " + niveauTotal(id), NamedTextColor.GRAY));
        e.renderer((source, nom, message, lecteur) ->
                ancien.render(source, nom.hoverEvent(HoverEvent.showText(infobulle)), message, lecteur));
    }

    // ------------------------------------------------------------ connexion

    @EventHandler
    public void onArrivee(PlayerJoinEvent e) {
        Donnees d = joueurs.get(e.getPlayer().getUniqueId());
        if (d != null && !e.getPlayer().getName().equals(d.nom)) {
            d.nom = e.getPlayer().getName();
            modifie = true;
        }
    }

    @EventHandler
    public void onDepart(PlayerQuitEvent e) {
        Barre b = barres.remove(e.getPlayer().getUniqueId());
        if (b != null && b.masquer != null) b.masquer.cancel();
    }

    // ------------------------------------------------------------ classement

    List<Place> classement(Metier m, int nombre) {
        int i = m.ordinal();
        return joueurs.values().stream()
                .filter(d -> d.xp[i] > 0)
                .sorted(Comparator.comparingLong((Donnees d) -> d.xp[i]).reversed())
                .limit(nombre)
                .map(d -> new Place(d.nom, d.xp[i], progression(d.xp[i]).niveau()))
                .toList();
    }

    /** Classement general : somme des niveaux des 5 metiers. */
    List<Place> classementTotal(int nombre) {
        return joueurs.values().stream()
                .map(d -> {
                    int total = 0;
                    long xp = 0;
                    for (long x : d.xp) { total += progression(x).niveau(); xp += x; }
                    return new Place(d.nom, xp, total);
                })
                .filter(pl -> pl.xp() > 0)
                .sorted(Comparator.comparingInt(Place::niveau).thenComparingLong(Place::xp).reversed())
                .limit(nombre)
                .toList();
    }

    // ------------------------------------------------------------ sauvegarde

    private void charger() {
        if (!fichier.exists()) return;
        ConfigurationSection s = YamlConfiguration.loadConfiguration(fichier).getConfigurationSection("joueurs");
        if (s == null) return;
        for (String cle : s.getKeys(false)) {
            ConfigurationSection j = s.getConfigurationSection(cle);
            if (j == null) continue;
            try {
                Donnees d = new Donnees(j.getString("nom", "?"));
                for (Metier m : Metier.values()) d.xp[m.ordinal()] = Math.max(0, j.getLong(m.cle, 0));
                joueurs.put(UUID.fromString(cle), d);
            } catch (IllegalArgumentException ignore) {
                plugin.getLogger().warning("metiers.yml : identifiant de joueur invalide " + cle);
            }
        }
    }

    void sauvegarder(boolean asynchrone) {
        if (!modifie) return;
        modifie = false;
        YamlConfiguration y = new YamlConfiguration();
        joueurs.forEach((id, d) -> {
            String base = "joueurs." + id + ".";
            y.set(base + "nom", d.nom);
            for (Metier m : Metier.values()) y.set(base + m.cle, d.xp[m.ordinal()]);
        });
        String contenu = y.saveToString();
        Runnable ecrire = () -> {
            synchronized (verrouFichier) {
                try {
                    plugin.getDataFolder().mkdirs();
                    File tmp = new File(fichier.getPath() + ".tmp");
                    Files.writeString(tmp.toPath(), contenu, StandardCharsets.UTF_8);
                    Files.move(tmp.toPath(), fichier.toPath(), StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException ex) {
                    modifie = true;
                    plugin.getLogger().severe("Impossible de sauvegarder metiers.yml : " + ex.getMessage());
                }
            }
        };
        if (asynchrone) plugin.getServer().getScheduler().runTaskAsynchronously(plugin, ecrire);
        else ecrire.run();
    }
}
