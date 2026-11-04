package fr.survieplus;

import fr.survieplus.pack.Packs;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class SurviePlus extends JavaPlugin {

    static final Component PREFIXE = Component.text("[Survie+] ", NamedTextColor.GOLD);

    private final Map<Module, NamespacedKey> cles = new EnumMap<>(Module.class);
    private final MondeTest mondeTest = new MondeTest(this);
    private Metiers metiers;
    private Guide guide;
    private Packs packs;

    Packs packs() {
        return packs;
    }

    Metiers metiers() {
        return metiers;
    }

    Guide guide() {
        return guide;
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        // Ajoute dans config.yml les options des nouvelles versions
        getConfig().options().copyDefaults(true);
        saveConfig();
        for (Module m : Module.values()) cles.put(m, new NamespacedKey(this, m.cle));

        metiers = new Metiers(this);
        var pm = getServer().getPluginManager();
        pm.registerEvents(metiers, this);
        pm.registerEvents(new MenuMetiers.Ecouteur(), this);
        guide = new Guide(this);
        pm.registerEvents(guide, this);
        pm.registerEvents(new Bucheron(this), this);
        pm.registerEvents(new Mineur(this), this);
        pm.registerEvents(new Recolte(this), this);
        pm.registerEvents(new Tombes(this), this);
        pm.registerEvents(new Tri(this), this);
        pm.registerEvents(new Menu.Ecouteur(), this);
        Sommeil.appliquer(this);
        packs = new Packs(this, Menu.CASES);
        packs.demarrer();
        pm.registerEvents(packs, this);

        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, e -> {
            e.registrar().register("survieplus", "Ouvre le menu SurviePlus", List.of("sp"), new Commande());
            e.registrar().register("metiers", "Progression des metiers", List.of("metier"), new CommandeMetiers());
        });
    }

    @Override
    public void onDisable() {
        if (metiers != null) metiers.sauvegarder(false);
        if (packs != null) packs.arreter();
    }

    private static void message(CommandSender s, String texte, NamedTextColor couleur) {
        s.sendMessage(PREFIXE.append(Component.text(texte, couleur)));
    }

    /** /metiers : ouvre le menu. Admin : /metiers donner <joueur> <metier> <xp> | /metiers reset <joueur> */
    private final class CommandeMetiers implements BasicCommand {
        @Override
        public void execute(CommandSourceStack src, String[] args) {
            CommandSender s = src.getSender();
            if (args.length > 0 && (args[0].equalsIgnoreCase("donner") || args[0].equalsIgnoreCase("reset"))) {
                if (!s.hasPermission("survieplus.admin")) {
                    message(s, "Tu n'as pas la permission.", NamedTextColor.RED);
                    return;
                }
                boolean donner = args[0].equalsIgnoreCase("donner");
                if (args.length < (donner ? 4 : 2)) {
                    message(s, donner ? "/metiers donner <joueur> <metier> <xp>" : "/metiers reset <joueur>", NamedTextColor.YELLOW);
                    return;
                }
                Player cible = getServer().getPlayerExact(args[1]);
                if (cible == null) {
                    message(s, "Joueur introuvable (il doit être connecté).", NamedTextColor.RED);
                    return;
                }
                if (!donner) {
                    metiers.reinitialiser(cible);
                    message(s, "Métiers de " + cible.getName() + " remis à zéro.", NamedTextColor.GREEN);
                    return;
                }
                Metier m = Metier.parCle(args[2]);
                long xp;
                try {
                    xp = Long.parseLong(args[3]);
                } catch (NumberFormatException ex) {
                    xp = -1;
                }
                if (m == null || xp <= 0) {
                    message(s, "Métier : bucheron, mineur, fermier, pecheur, chasseur ; XP : nombre positif.", NamedTextColor.RED);
                    return;
                }
                metiers.ajouter(cible, m, xp, false);
                message(s, xp + " XP de " + m.nom + " donnés à " + cible.getName() + ".", NamedTextColor.GREEN);
                return;
            }
            if (!(src.getExecutor() instanceof Player p)) {
                s.sendMessage("Cette commande ouvre un menu : elle est reservee aux joueurs.");
                return;
            }
            if (!metiers.actif() || !p.hasPermission(Metiers.PERMISSION)) {
                message(s, "Les métiers sont désactivés.", NamedTextColor.RED);
                return;
            }
            new MenuMetiers(SurviePlus.this, p).ouvrir();
        }

        @Override
        public Collection<String> suggest(CommandSourceStack src, String[] args) {
            if (!src.getSender().hasPermission("survieplus.admin")) return List.of();
            if (args.length <= 1) return List.of("donner", "reset");
            if (args.length == 2) return getServer().getOnlinePlayers().stream().map(Player::getName).toList();
            if (args.length == 3 && args[0].equalsIgnoreCase("donner"))
                return java.util.Arrays.stream(Metier.values()).map(m -> m.cle).toList();
            return List.of();
        }
    }

    // ------------------------------------------------------------ preferences

    /** Le module est-il active par l'admin dans config.yml ? */
    boolean activeServeur(Module m) {
        return getConfig().getBoolean(m.cheminConfig, true);
    }

    /** Choix personnel du joueur (active par defaut), stocke dans ses donnees de joueur. */
    boolean choixJoueur(Player p, Module m) {
        return p.getPersistentDataContainer().getOrDefault(cles.get(m), PersistentDataType.BOOLEAN, true);
    }

    void basculer(Player p, Module m) {
        p.getPersistentDataContainer().set(cles.get(m), PersistentDataType.BOOLEAN, !choixJoueur(p, m));
    }

    /** Le module s'applique-t-il vraiment a ce joueur ? */
    boolean actif(Player p, Module m) {
        return activeServeur(m) && p.hasPermission(m.permission) && choixJoueur(p, m) && debloque(p, m);
    }

    /** Niveau de metier requis pour ce module (0 = libre, ou metiers desactives). */
    int niveauRequis(Module m) {
        if (m.metierRequis() == null || !metiers.actif()) return 0;
        return Math.max(0, getConfig().getInt("metiers.deblocage." + m.cle, 3));
    }

    /** Le joueur a-t-il le niveau de metier pour ce module ? */
    boolean debloque(Player p, Module m) {
        int n = niveauRequis(m);
        return n <= 0 || metiers.niveau(p.getUniqueId(), m.metierRequis()) >= n;
    }

    // ------------------------------------------------------------ commande

    private final class Commande implements BasicCommand {
        @Override
        public void execute(CommandSourceStack src, String[] args) {
            CommandSender s = src.getSender();
            if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
                if (!s.hasPermission("survieplus.admin")) {
                    s.sendMessage(PREFIXE.append(Component.text("Tu n'as pas la permission.", NamedTextColor.RED)));
                    return;
                }
                reloadConfig();
                Sommeil.appliquer(SurviePlus.this);
                packs.demarrer();
                s.sendMessage(PREFIXE.append(Component.text("Configuration rechargee.", NamedTextColor.GREEN)));
                return;
            }
            if (!(src.getExecutor() instanceof Player p)) {
                s.sendMessage("Cette commande ouvre un menu : elle est reservee aux joueurs.");
                return;
            }
            if (args.length > 0 && args[0].equalsIgnoreCase("test")) {
                if (!s.hasPermission("survieplus.admin")) {
                    s.sendMessage(PREFIXE.append(Component.text("Tu n'as pas la permission.", NamedTextColor.RED)));
                    return;
                }
                if (args.length > 1 && args[1].equalsIgnoreCase("reset")) mondeTest.reinitialiser(p);
                else mondeTest.basculer(p);
                return;
            }
            if (args.length > 0 && args[0].equalsIgnoreCase("guide")) {
                guide.donner(p);
                message(p, "Voici le guide du serveur !", NamedTextColor.GREEN);
                return;
            }
            new Menu(SurviePlus.this, p).ouvrir();
        }

        @Override
        public Collection<String> suggest(CommandSourceStack src, String[] args) {
            if (!src.getSender().hasPermission("survieplus.admin")) return args.length <= 1 ? List.of("guide") : List.of();
            if (args.length <= 1) return List.of("guide", "reload", "test");
            if (args.length == 2 && args[0].equalsIgnoreCase("test")) return List.of("reset");
            return List.of();
        }
    }
}
