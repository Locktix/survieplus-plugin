package fr.survieplus;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.object.ObjectContents;
import fr.survieplus.pack.DessinsMenus;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Livre "Guide Survie+" : explique les options et les metiers, avec icones, sommaire cliquable
 * et infobulles. Donne une fois a la premiere connexion ; ouvrable depuis /sp (avec la progression
 * du joueur), recuperable avec /sp guide. Le contenu suit config.yml (niveaux, bonus, recompenses).
 *
 * Une page de livre fait ~14 lignes de ~20 caracteres : chaque page est calibree pour tenir.
 */
final class Guide implements Listener {

    private static final NamedTextColor ENCRE = NamedTextColor.BLACK;
    private static final NamedTextColor DISCRET = NamedTextColor.DARK_GRAY;
    private static final NamedTextColor TITRE = NamedTextColor.DARK_GREEN;
    private static final NamedTextColor LIEN = NamedTextColor.DARK_BLUE;
    private static final int PAGE_SOMMAIRE = 2;

    /** Couleurs lisibles sur le papier du livre (celles du chat sont trop claires). */
    private static final Map<Metier, NamedTextColor> COULEURS = Map.of(
            Metier.BUCHERON, NamedTextColor.GOLD,
            Metier.MINEUR, NamedTextColor.DARK_AQUA,
            Metier.FERMIER, NamedTextColor.DARK_GREEN,
            Metier.PECHEUR, NamedTextColor.BLUE,
            Metier.CHASSEUR, NamedTextColor.DARK_RED);

    private final SurviePlus plugin;
    private final NamespacedKey cleRecu;
    /** Dessins du pack pour le livre en cours de fabrication (null : livre sans images). */
    private DessinsMenus dessins;

    Guide(SurviePlus plugin) {
        this.plugin = plugin;
        this.cleRecu = new NamespacedKey(plugin, "guide-recu");
    }

    @EventHandler
    public void onArrivee(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (!plugin.getConfig().getBoolean("guide.premiere-connexion", true)) return;
        var pdc = p.getPersistentDataContainer();
        if (pdc.has(cleRecu, PersistentDataType.BOOLEAN)) return;
        pdc.set(cleRecu, PersistentDataType.BOOLEAN, true);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) return;
            donner(p);
            p.sendMessage(SurviePlus.PREFIXE.append(Component.text(
                    "Tu as reçu le guide du serveur ! Tu peux aussi le lire depuis /sp.", NamedTextColor.GREEN)));
        }, 40L);
    }

    /** Donne le livre (sans la page de progression, qui serait vite perimee). */
    void donner(Player p) {
        p.getInventory().addItem(livre(p, false)).values().forEach(reste -> p.getWorld().dropItem(p.getLocation(), reste));
    }

    /** Ouvre le livre a l'ecran, avec la progression a jour du joueur. */
    void ouvrir(Player p) {
        p.openBook(livre(p, true));
    }

    private ItemStack livre(Player p, boolean progression) {
        // Images du pack seulement pour le livre ouvert a l'ecran (le livre donne peut etre relu sans le pack)
        dessins = progression ? plugin.packs().pour(p) : null;
        ItemStack it = new ItemStack(Material.WRITTEN_BOOK);
        it.editMeta(BookMeta.class, b -> {
            b.title(Component.text("Guide Survie+"));
            b.author(Component.text("Survie+"));
            b.pages(pages(p, progression));
        });
        return it;
    }

    // ------------------------------------------------------------ briques

    private static Component sprite(String atlas, String nom) {
        // Blanc = pas de teinte (sinon l'icone prend la couleur du texte, noir dans un livre)
        return Component.object(ObjectContents.sprite(Key.key(atlas), Key.key(nom))).color(NamedTextColor.WHITE);
    }

    private static Component objet(String nom) {
        return sprite("minecraft:items", "item/" + nom);
    }

    private static Component objet(Material m) {
        return objet(m.getKey().getKey());
    }

    private static Component bloc(String nom) {
        return sprite("minecraft:blocks", "block/" + nom);
    }

    private static Component effet(String nom) {
        return sprite("minecraft:gui", "mob_effect/" + nom);
    }

    /** Icone d'un objet de recompense (les blocs n'ont pas tous une texture simple : livre a la place). */
    private static Component icone(ItemStack it) {
        return it.getType().isBlock() ? objet("book") : objet(it.getType());
    }

    private static Component tete(Player p) {
        return Component.object(ObjectContents.playerHead(p.getUniqueId())).color(NamedTextColor.WHITE);
    }

    private static Component t(String s) {
        return Component.text(s, ENCRE);
    }

    private static Component discret(String s) {
        return Component.text(s, DISCRET);
    }

    private static Component gras(String s, TextColor couleur) {
        return Component.text(s, couleur, TextDecoration.BOLD);
    }

    private static TextComponent.Builder page() {
        return Component.text();
    }

    /** Titre de page : icone + texte en gras ; un clic ramene au sommaire. */
    private static Component titre(Component icone, String texte, TextColor couleur) {
        return titre(Component.empty(), icone, texte, couleur);
    }

    /** Titre d'une page de metier, avec le decor du metier cale a droite (si le joueur a le pack). */
    private Component titre(Metier m, String texte, TextColor couleur) {
        return titre(dessins == null ? Component.empty() : dessins.decorLivre(m.ordinal()), objet(m.icone), texte, couleur);
    }

    private static Component titre(Component decor, Component icone, String texte, TextColor couleur) {
        return Component.text()
                .append(decor)
                .append(icone).append(Component.text(" "))
                .append(gras(texte, couleur))
                .clickEvent(ClickEvent.changePage(PAGE_SOMMAIRE))
                .hoverEvent(HoverEvent.showText(Component.text("Retour au sommaire")))
                .append(Component.text("\n\n"))
                .build();
    }

    private static Component lienCommande(String texte, String commande) {
        return Component.text("▶ " + texte, LIEN, TextDecoration.UNDERLINED)
                .clickEvent(ClickEvent.runCommand(commande))
                .hoverEvent(HoverEvent.showText(Component.text("Clique pour lancer " + commande)));
    }

    /** Barre de progression en carres : pleine en or au niveau max. */
    private static Component barre(Metiers.Progression pr, int cases) {
        int plein = pr.max() ? cases : (int) Math.round((double) cases * pr.actuel() / pr.requis());
        return Component.text("■".repeat(plein), pr.max() ? NamedTextColor.GOLD : NamedTextColor.DARK_GREEN)
                .append(Component.text("■".repeat(cases - plein), NamedTextColor.GRAY));
    }

    // ------------------------------------------------------------ pages

    private record Section(Component icone, String nom, List<Component> pages) {}

    private List<Component> pages(Player p, boolean progression) {
        Metiers mt = plugin.metiers();
        List<Section> sections = new ArrayList<>();

        sections.add(new Section(objet("writable_book"), "Les options", List.of(pageOptions())));
        if (mt.actif()) {
            sections.add(new Section(objet("experience_bottle"), "Les métiers", List.of(pageMetiers())));
            sections.add(new Section(objet("nether_star"), "Les niveaux", List.of(pageNiveaux())));
            sections.add(new Section(objet("name_tag"), "Les titres", List.of(pageTitres())));
            Component deblocages = pageDeblocages();
            if (deblocages != null) sections.add(new Section(bloc("oak_sapling"), "Déblocages", List.of(deblocages)));
            for (Metier m : Metier.values()) {
                List<Component> pm = new ArrayList<>();
                pm.add(pageMetier(p, m, progression));
                Component cadeaux = pageCadeaux(m);
                if (cadeaux != null) pm.add(cadeaux);
                sections.add(new Section(objet(m.icone), m.nom, pm));
            }
            if (progression) sections.add(new Section(tete(p), "Ta progression", List.of(pageProgression(p))));
        }

        List<Component> pages = new ArrayList<>();
        pages.add(couverture(p));
        pages.add(sommaire(sections));
        for (Section s : sections) pages.addAll(s.pages());
        return pages;
    }

    private Component couverture(Player p) {
        var icones = Component.text().append(Component.text("  "));
        for (Metier m : Metier.values()) icones.append(objet(m.icone)).append(Component.text("  "));
        return page()
                .append(Component.text("\n"))
                .append(icones.build())
                .append(Component.text("\n\n"))
                .append(dessins == null ? Component.empty() : dessins.paysageLivre())
                .append(Component.text(dessins == null ? "\n" : "\n\n"))
                .append(gras("     SURVIE+", TITRE))
                .append(Component.text("\n"))
                .append(Component.text("    le guide du serveur", DISCRET, TextDecoration.ITALIC))
                .append(Component.text("\n\n\n"))
                .append(tete(p)).append(t(" Bienvenue,\n"))
                .append(gras(p.getName(), ENCRE)).append(t(" !"))
                .append(Component.text("\n\n\n"))
                .append(Component.text("▶ Ouvrir le sommaire", LIEN, TextDecoration.UNDERLINED)
                        .clickEvent(ClickEvent.changePage(PAGE_SOMMAIRE))
                        .hoverEvent(HoverEvent.showText(Component.text("Page " + PAGE_SOMMAIRE))))
                .build();
    }

    private Component sommaire(List<Section> sections) {
        var page = page().append(gras("Sommaire", TITRE)).append(Component.text("\n\n"));
        int numero = PAGE_SOMMAIRE + 1;
        for (Section s : sections) {
            page.append(Component.text()
                    .append(s.icone()).append(Component.text(" "))
                    .append(Component.text(s.nom(), LIEN))
                    .clickEvent(ClickEvent.changePage(numero))
                    .hoverEvent(HoverEvent.showText(Component.text("Aller à la page " + numero))))
                    .append(Component.text("\n"));
            numero += s.pages().size();
        }
        return page.build();
    }

    private Component pageOptions() {
        record Option(Component icone, String nom, String comment) {}
        List<Option> options = List.of(
                new Option(objet("iron_axe"), "Abattage", "accroupi + hache"),
                new Option(objet("iron_pickaxe"), "Filon", "accroupi + pioche"),
                new Option(objet("wheat"), "Récolte", "clic droit, culture mûre"),
                new Option(objet("hopper"), "Tri", "Maj + clic droit, case vide"),
                new Option(objet("bone"), "Tombe", "tes affaires à ta mort"));
        var page = page().append(titre(objet("writable_book"), "Les options", TITRE));
        for (Option o : options)
            page.append(o.icone()).append(Component.text(" ")).append(gras(o.nom(), ENCRE))
                    .append(Component.text("\n")).append(discret(o.comment() + "\n"));
        return page.append(Component.text("\n")).append(lienCommande("Tout régler : /sp", "/sp")).build();
    }

    private Component pageMetiers() {
        var icones = Component.text();
        for (Metier m : Metier.values()) icones.append(objet(m.icone)).append(Component.text(" "));
        return page().append(titre(objet("experience_bottle"), "Les métiers", TITRE))
                .append(icones.build()).append(Component.text("\n\n"))
                .append(t("Les 5 progressent ")).append(gras("en même temps", ENCRE))
                .append(t(" : joue normalement !\n\n"))
                .append(t("Une barre en haut de l'écran montre l'XP gagnée.\n\n"))
                .append(discret("Blocs posés et mode créatif : pas d'XP.\n\n"))
                .append(lienCommande("Mes métiers : /metiers", "/metiers"))
                .build();
    }

    private Component pageNiveaux() {
        Metiers mt = plugin.metiers();
        var cfg = plugin.getConfig();
        var page = page().append(titre(objet("nether_star"), "Les niveaux", TITRE))
                .append(effet("luck")).append(t(" +" + Metiers.pourcent(mt.chanceDouble(1)) + " de récolte double par niveau "))
                .append(discret("(max " + Metiers.pourcent(mt.chanceDouble(mt.niveauMax())) + ")\n\n"));
        int p1 = mt.niveauPalier(1), p2 = mt.niveauPalier(2);
        if (p1 > 0 || p2 > 0) {
            String niveaux = p1 > 0 && p2 > 0 ? p1 + " et " + p2 : String.valueOf(Math.max(p1, p2));
            page.append(effet("haste")).append(t(" Niv. " + niveaux + " : un bonus par métier\n\n"));
        }
        page.append(objet("enchanted_book")).append(t(" Paliers : des cadeaux\n\n"));
        if (cfg.getBoolean("metiers.groupe.actif", true))
            page.append(effet("hero_of_the_village")).append(t(" À plusieurs (" + cfg.getInt("metiers.groupe.rayon", 20)
                    + " blocs) : +" + cfg.getInt("metiers.groupe.bonus-pourcent", 10) + " % d'XP"));
        return page.build();
    }

    private Component pageTitres() {
        var page = page().append(titre(objet("name_tag"), "Les titres", TITRE));
        String[] rangs = {"Apprenti", "Compagnon", "Expert", "Maître", "Grand maître"};
        for (int i = 0; i < rangs.length; i++) {
            int niv = (i + 1) * 10;
            if (niv > plugin.metiers().niveauMax()) break;
            page.append(discret("Niv. " + niv + "  ")).append(gras(rangs[i], i >= 3 ? NamedTextColor.DARK_PURPLE : ENCRE))
                    .append(Component.text("\n"));
        }
        String ouTitre = switch (plugin.metiers().modeTitreChat()) {
            case "prefixe" -> "Ton meilleur métier s'affiche devant ton pseudo dans le chat.";
            case "non" -> "Ton titre est visible dans /metiers.";
            default -> "Survole un pseudo dans le chat pour voir son titre.";
        };
        page.append(Component.text("\n")).append(t(ouTitre));
        int annonce = plugin.getConfig().getInt("metiers.annonce-niveau", 40);
        if (annonce > 0) page.append(t("\n\nDès le niv. " + annonce + ", tout le serveur est prévenu !"));
        return page.build();
    }

    private Component pageDeblocages() {
        var page = page().append(titre(bloc("oak_sapling"), "Déblocages", TITRE));
        boolean aucun = true;
        for (Module mod : Module.values()) {
            int niv = plugin.niveauRequis(mod);
            if (niv <= 0) continue;
            Metier m = mod.metierRequis();
            page.append(objet(m.icone)).append(Component.text(" ")).append(gras(mod.nom, ENCRE)).append(Component.text("\n"))
                    .append(discret(m.nom + " niv. " + niv + "\n\n"));
            aucun = false;
        }
        if (aucun) return null;
        return page.append(t("Avant, l'option est grisée dans /sp.")).build();
    }

    private Component pageMetier(Player p, Metier m, boolean progression) {
        Metiers mt = plugin.metiers();
        NamedTextColor c = COULEURS.get(m);
        var page = page().append(titre(m, m.nom, c))
                .append(gras("XP", c)).append(t(" : " + m.gain + "\n\n"));
        String[] effets = {"haste", "luck"};
        for (int i = 1; i <= 2; i++) {
            int niv = mt.niveauPalier(i);
            if (niv <= 0) continue;
            page.append(effet(effets[i - 1])).append(gras(" Niv. " + niv, c)).append(Component.text("\n"))
                    .append(t(m.paliers[i - 1] + "\n\n"));
        }
        if (progression) {
            Metiers.Progression pr = mt.progression(p.getUniqueId(), m);
            page.append(discret("Toi : niv. " + pr.niveau() + "\n")).append(barre(pr, 12));
        } else if (mt.recompenses.paliers(m).isEmpty()) {
            return page.build();
        }
        if (!mt.recompenses.paliers(m).isEmpty())
            page.append(Component.text(progression ? "\n\n" : "")).append(discret("Ses cadeaux : page suivante ▶"));
        return page.build();
    }

    private Component pageCadeaux(Metier m) {
        var paliers = plugin.metiers().recompenses.paliers(m);
        if (paliers.isEmpty()) return null;
        NamedTextColor c = COULEURS.get(m);
        var page = page().append(titre(m, "Cadeaux", c))
                .append(Component.text("Survole un cadeau pour le voir en détail.\n\n", DISCRET, TextDecoration.ITALIC));
        for (var en : paliers.entrySet()) {
            page.append(gras("Niv. " + en.getKey(), c)).append(Component.text("\n"));
            for (ItemStack it : en.getValue()) {
                boolean enchante = !it.getEnchantments().isEmpty()
                        || it.getItemMeta() instanceof EnchantmentStorageMeta l && l.hasStoredEnchants();
                page.append(Component.text()
                                .append(icone(it)).append(Component.text(" "))
                                .append(Recompenses.nom(it).color(ENCRE))
                                .append(Component.text(enchante ? " ✦" : "", NamedTextColor.DARK_PURPLE))
                                .hoverEvent(it.asHoverEvent()))
                        .append(Component.text("\n"));
            }
            page.append(Component.text("\n"));
        }
        return page.build();
    }

    private Component pageProgression(Player p) {
        Metiers mt = plugin.metiers();
        var page = page().append(titre(tete(p), p.getName(), TITRE))
                .append(t("Niveau total : ")).append(gras(String.valueOf(mt.niveauTotal(p.getUniqueId())), ENCRE))
                .append(Component.text("\n\n"));
        for (Metier m : Metier.values()) {
            Metiers.Progression pr = mt.progression(p.getUniqueId(), m);
            String detail = m.nom + " niv. " + pr.niveau()
                    + (pr.max() ? " (max)" : "\n" + pr.actuel() + " / " + pr.requis() + " XP");
            page.append(Component.text()
                            .append(objet(m.icone))
                            .append(Component.text(String.format(" %2d ", pr.niveau()), COULEURS.get(m), TextDecoration.BOLD))
                            .append(barre(pr, 10))
                            .hoverEvent(HoverEvent.showText(Component.text(detail))))
                    .append(Component.text("\n"));
        }
        String titre = mt.titre(p.getUniqueId());
        page.append(Component.text("\n"));
        if (titre != null) page.append(objet("name_tag")).append(t(" " + titre + "\n\n"));
        return page.append(lienCommande("Détails : /metiers", "/metiers")).build();
    }
}
