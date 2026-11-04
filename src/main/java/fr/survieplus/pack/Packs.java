package fr.survieplus.pack;

import com.sun.net.httpserver.HttpServer;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;

/**
 * Pack de ressources des menus : fabrique au demarrage, envoye a la connexion (a cote de celui de Muscu,
 * le jeu empile les packs), et servi soit par un mini serveur web integre (pack.port), soit depuis une
 * adresse externe (pack.adresse, ex. https://minecraft.veldor.be) quand le port n'est pas joignable.
 * Les menus dessines ne s'affichent qu'aux joueurs qui ont bien charge le pack.
 */
public final class Packs implements Listener {

    private static final UUID ID_PACK = UUID.nameUUIDFromBytes("survieplus-menus".getBytes());
    private static final String PREFIXE_FICHIER = "survieplus-menus-";

    private final JavaPlugin plugin;
    private final int[] casesSp;
    private final Set<UUID> charges = new HashSet<>();
    private DessinsMenus dessins;
    private HttpServer serveur;
    private ExecutorService fils;
    private int portServeur = -1;

    public Packs(JavaPlugin plugin, int[] casesSp) {
        this.plugin = plugin;
        this.casesSp = casesSp;
    }

    private boolean actifConfig() {
        return plugin.getConfig().getBoolean("pack.active", true);
    }

    private int port() {
        return Math.max(1, Math.min(65535, plugin.getConfig().getInt("pack.port", 25567)));
    }

    private String adresse() {
        return plugin.getConfig().getString("pack.adresse", "").trim();
    }

    /** Fabrique le pack et ouvre le serveur web si besoin (au demarrage et apres /sp reload). */
    public void demarrer() {
        if (!actifConfig()) {
            arreter();
            dessins = null;
            charges.clear();
            return;
        }
        try {
            if (dessins == null) {
                dessins = DessinsMenus.fabriquer(casesSp);
                plugin.getDataFolder().mkdirs();
                Files.write(plugin.getDataFolder().toPath().resolve("survieplus-menus.zip"), dessins.pack().zip());
            }
        } catch (IOException | RuntimeException e) {
            plugin.getLogger().log(Level.SEVERE, "Impossible de fabriquer le pack des menus : menus classiques", e);
            dessins = null;
            return;
        }
        String empreinte = dessins.pack().sha1().substring(0, 12);
        if (!adresse().isEmpty()) {
            arreter();
            plugin.getLogger().info("Pack des menus : " + url(null) + " (empreinte " + empreinte + "). "
                    + "Ce fichier doit etre en ligne : c'est plugins/SurviePlus/survieplus-menus.zip renomme.");
            return;
        }
        if (serveur != null && portServeur == port()) return;
        arreter();
        try {
            fils = Executors.newFixedThreadPool(2, r -> {
                Thread t = new Thread(r, "SurviePlus-pack-http");
                t.setDaemon(true);
                return t;
            });
            serveur = HttpServer.create(new InetSocketAddress(port()), 0);
            serveur.createContext("/", echange -> {
                try (echange) {
                    DessinsMenus d = dessins;
                    String chemin = echange.getRequestURI().getPath();
                    if (d == null || !chemin.startsWith("/" + PREFIXE_FICHIER) || !chemin.endsWith(".zip")) {
                        echange.sendResponseHeaders(404, -1);
                        return;
                    }
                    byte[] zip = d.pack().zip();
                    echange.getResponseHeaders().set("Content-Type", "application/zip");
                    echange.sendResponseHeaders(200, zip.length);
                    try (OutputStream out = echange.getResponseBody()) {
                        out.write(zip);
                    }
                }
            });
            serveur.setExecutor(fils);
            serveur.start();
            portServeur = port();
            plugin.getLogger().info("Pack des menus servi sur le port " + port() + " (empreinte " + empreinte + ")");
        } catch (IOException e) {
            plugin.getLogger().warning("Pack des menus : impossible d'ouvrir le port " + port() + " (" + e.getMessage()
                    + "). Change pack.port, ou mets l'adresse du pack en ligne dans pack.adresse. Menus classiques en attendant.");
            arreter();
            dessins = null;
        }
    }

    public void arreter() {
        if (serveur != null) serveur.stop(0);
        if (fils != null) fils.shutdownNow();
        serveur = null;
        fils = null;
        portServeur = -1;
    }

    private String url(Player joueur) {
        String hote = adresse();
        if (hote.isEmpty()) {
            InetSocketAddress v = joueur == null ? null : joueur.getVirtualHost();
            hote = v == null ? "" : v.getHostString();
            int zero = hote.indexOf('\0');
            if (zero >= 0) hote = hote.substring(0, zero);
            if (hote.endsWith(".")) hote = hote.substring(0, hote.length() - 1);
            if (hote.isBlank()) hote = "localhost";
            if (hote.contains(":") && !hote.startsWith("[")) hote = "[" + hote + "]";
            hote = "http://" + hote + ":" + port();
        } else if (!hote.contains("://")) {
            hote = "https://" + hote;
        }
        if (hote.endsWith("/")) hote = hote.substring(0, hote.length() - 1);
        return hote + "/" + PREFIXE_FICHIER + dessins.pack().sha1().substring(0, 12) + ".zip";
    }

    // ───────────── Joueurs ─────────────

    @EventHandler(priority = EventPriority.MONITOR)
    public void onArrivee(PlayerJoinEvent e) {
        envoyer(e.getPlayer());
    }

    public void envoyer(Player joueur) {
        if (dessins == null) return;
        joueur.sendResourcePacks(ResourcePackRequest.resourcePackRequest()
                .packs(ResourcePackInfo.resourcePackInfo(ID_PACK, URI.create(url(joueur)), dessins.pack().sha1()))
                .replace(false)
                .required(false)
                .prompt(Component.text("SurviePlus", NamedTextColor.GOLD)
                        .append(Component.text(" : menus /sp et /metiers en images.", NamedTextColor.WHITE))));
    }

    @EventHandler
    public void onStatut(PlayerResourcePackStatusEvent e) {
        if (!ID_PACK.equals(e.getID())) return;
        Player p = e.getPlayer();
        switch (e.getStatus()) {
            case SUCCESSFULLY_LOADED -> charges.add(p.getUniqueId());
            case DECLINED, FAILED_DOWNLOAD, INVALID_URL, FAILED_RELOAD, DISCARDED -> {
                charges.remove(p.getUniqueId());
                if (e.getStatus() != PlayerResourcePackStatusEvent.Status.DECLINED)
                    plugin.getLogger().warning("Pack des menus : " + p.getName() + " n'a pas pu le charger (" + e.getStatus()
                            + ", adresse " + url(p) + "). Menus classiques pour ce joueur.");
            }
            default -> {
            }
        }
    }

    @EventHandler
    public void onDepart(PlayerQuitEvent e) {
        charges.remove(e.getPlayer().getUniqueId());
    }

    /** Les menus dessines, si ce joueur a le pack ; sinon null (menus classiques). */
    public DessinsMenus pour(Player joueur) {
        return dessins != null && charges.contains(joueur.getUniqueId()) ? dessins : null;
    }
}
