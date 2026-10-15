package pl.smurfpvp;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.text.DecimalFormat;
import java.util.*;

public final class AvengersUpgrades extends JavaPlugin implements Listener {
    private NamespacedKey levelKey;
    private NamespacedKey markerKey;
    private final Map<UUID, MenuState> menus = new HashMap<>();
    private int maxLevel;
    private String title;

    private enum Screen { ITEMS, PREVIEW, CHANCE }
    private static final class MenuState {
        Screen screen;
        int selectedSlot;
        int chance;
        MenuState(Screen screen, int selectedSlot, int chance) {
            this.screen=screen; this.selectedSlot=selectedSlot; this.chance=chance;
        }
    }

    @Override public void onEnable() {
        saveDefaultConfig();
        levelKey = new NamespacedKey(this, "item_upgrade_level");
        markerKey = new NamespacedKey(this, "avengers_upgrade_item");
        maxLevel = Math.max(1, getConfig().getInt("max-level", 12));
        title = color(getConfig().getString("menu-title", "&8Avengers &c» &fUlepszenia"));
        getServer().getPluginManager().registerEvents(this, this);
        Objects.requireNonNull(getCommand("ulepsz")).setExecutor((sender, command, label, args) -> {
            if (!(sender instanceof Player p)) { sender.sendMessage("Only players can use this command."); return true; }
            if (!p.hasPermission("avengersupgrades.use")) { p.sendMessage(color("&cNie masz uprawnien.")); return true; }
            openItems(p); return true;
        });
        getLogger().info("AvengersUpgrades 4.0.0 enabled.");
    }

    private void openItems(Player p) {
        Inventory inv = Bukkit.createInventory(null, 54, title);
        frame(inv);
        inv.setItem(4, named(Material.NETHER_STAR, "&6&lPRZEDMIOTY DO ULEPSZENIA",
                "&7LPM: ulepsz wybrany przedmiot", "&7PPM: podglad i opcje ulepszania"));
        List<Integer> slots = itemSlots();
        int idx=0;
        for (int playerSlot=0; playerSlot< p.getInventory().getSize() && idx<slots.size(); playerSlot++) {
            ItemStack it=p.getInventory().getItem(playerSlot);
            if (it==null || it.getType().isAir() || it.getType()==Material.PLAYER_HEAD) continue;
            int menuSlot=slots.get(idx++);
            inv.setItem(menuSlot, display(it, "&f"+pretty(it.getType()), "&7Poziom: &e"+level(it)+"&7/"+maxLevel,
                    "&aLPM &7— ulepsz", "&6PPM &7— podglad"));
            // map menu slot to actual inventory slot encoded in custom model data not needed; stored in state lookup below
        }
        menus.put(p.getUniqueId(), new MenuState(Screen.ITEMS, -1, 100));
        p.openInventory(inv);
    }

    private List<Integer> itemSlots() { return List.of(10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34,37,38,39,40,41,42,43); }

    private void openPreview(Player p, int playerSlot) {
        ItemStack it=p.getInventory().getItem(playerSlot);
        if (it==null || it.getType().isAir()) { p.sendMessage(color("&cNie znaleziono przedmiotu.")); openItems(p); return; }
        Inventory inv=Bukkit.createInventory(null, 27, title);
        frame(inv);
        int lvl=level(it);
        inv.setItem(4, named(Material.ENCHANTED_BOOK, "&e&lPODGLAD ULEPSZENIA",
                "&7Przedmiot: &f"+pretty(it.getType()), "&7Obecny poziom: &e"+lvl+"/"+maxLevel,
                "&7Nastepny poziom: &a"+(lvl>=maxLevel ? "MAX" : lvl+1)));
        inv.setItem(11, display(it, "&6"+pretty(it.getType())+" &e✦"+lvl,
                "&7Cena: &e"+money(cost(lvl)), "&7LPM na opcje szansy, aby kontynuowac"));
        inv.setItem(13, named(Material.LIME_STAINED_GLASS_PANE, "&a&lULEPSZ 25%",
                "&7Szansa powodzenia: &a25%", "&7Koszt: &e"+money(cost(lvl))));
        inv.setItem(14, named(Material.YELLOW_STAINED_GLASS_PANE, "&e&lULEPSZ 50%",
                "&7Szansa powodzenia: &e50%", "&7Koszt: &e"+money(cost(lvl))));
        inv.setItem(15, named(Material.GREEN_STAINED_GLASS_PANE, "&2&lULEPSZ 100%",
                "&7Gwarantowane powodzenie", "&7Koszt: &e"+money(cost(lvl))));
        inv.setItem(22, named(Material.ARROW, "&bPOWROT", "&7Wroc do listy przedmiotow"));
        menus.put(p.getUniqueId(), new MenuState(Screen.PREVIEW, playerSlot, 100));
        p.openInventory(inv);
    }

    @EventHandler public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p) || !e.getView().getTitle().equals(title)) return;
        e.setCancelled(true);
        MenuState state=menus.get(p.getUniqueId());
        if (state==null) return;
        int raw=e.getRawSlot();
        if (raw<0 || raw>=e.getView().getTopInventory().getSize()) return;
        if (state.screen==Screen.ITEMS) {
            List<Integer> slots=itemSlots();
            int idx=slots.indexOf(raw);
            if (idx<0) return;
            List<Integer> playerSlots=eligibleSlots(p);
            if (idx>=playerSlots.size()) return;
            int playerSlot=playerSlots.get(idx);
            if (e.getClick()==ClickType.RIGHT || e.getClick()==ClickType.SHIFT_RIGHT) openPreview(p, playerSlot);
            else if (e.getClick().isLeftClick()) doUpgrade(p, playerSlot, 100, true);
        } else if (state.screen==Screen.PREVIEW) {
            if (raw==22) { openItems(p); return; }
            if (raw==13) doUpgrade(p,state.selectedSlot,25,false);
            if (raw==14) doUpgrade(p,state.selectedSlot,50,false);
            if (raw==15) doUpgrade(p,state.selectedSlot,100,false);
        }
    }

    private void doUpgrade(Player p, int playerSlot, int chance, boolean reopenList) {
        ItemStack it=p.getInventory().getItem(playerSlot);
        if (it==null || it.getType().isAir()) { p.sendMessage(color("&cPrzedmiot nie jest juz w ekwipunku.")); openItems(p); return; }
        int lvl=level(it);
        if (lvl>=maxLevel) { p.sendMessage(color("&aPrzedmiot ma juz maksymalny poziom.")); if(reopenList)openItems(p);else openPreview(p,playerSlot); return; }
        double price=cost(lvl);
        // Vault is deliberately not hard-required; without Vault, cost is taken from vanilla XP levels.
        int xpCost=(int)Math.ceil(price/100.0);
        if (p.getLevel()<xpCost) { p.sendMessage(color("&cPotrzebujesz "+xpCost+" poziomow XP (koszt: "+money(price)+").")); return; }
        p.setLevel(p.getLevel()-xpCost);
        boolean success=chance>=100 || new Random().nextInt(100)<chance;
        if (success) {
            setLevel(it,lvl+1);
            p.sendMessage(color("&aUlepszono przedmiot do poziomu &e"+(lvl+1)+"&a!"));
        } else p.sendMessage(color("&cUlepszenie nie powiodlo sie. Przedmiot zachowal poziom "+lvl+"."));
        if(reopenList) openItems(p); else openPreview(p,playerSlot);
    }

    private List<Integer> eligibleSlots(Player p) {
        List<Integer> result=new ArrayList<>();
        for(int s=0;s<p.getInventory().getSize();s++){
            ItemStack it=p.getInventory().getItem(s);
            if(it!=null && !it.getType().isAir() && it.getType()!=Material.PLAYER_HEAD) result.add(s);
        }
        return result;
    }
    private int level(ItemStack it) {
        if(it==null || it.getType().isAir() || !it.hasItemMeta()) return 1;
        Integer v=it.getItemMeta().getPersistentDataContainer().get(levelKey,PersistentDataType.INTEGER);
        return v==null?1:Math.max(1,v);
    }
    private void setLevel(ItemStack it,int level) {
        ItemMeta m=it.getItemMeta(); if(m==null)return;
        m.getPersistentDataContainer().set(levelKey,PersistentDataType.INTEGER,level);
        m.getPersistentDataContainer().set(markerKey,PersistentDataType.BYTE,(byte)1);
        List<String> lore=m.hasLore()&&m.getLore()!=null?new ArrayList<>(m.getLore()):new ArrayList<>();
        lore.removeIf(s -> ChatColor.stripColor(s).startsWith("Avengers Level:"));
        lore.add(color("&8Avengers Level: &e"+level+"/"+maxLevel));
        m.setLore(lore); it.setItemMeta(m);
    }
    private double cost(int level) { return Math.max(0,getConfig().getDouble("base-cost",100)+level*getConfig().getDouble("cost-per-level",100)); }
    private String money(double n) { return new DecimalFormat("#,##0.##").format(n); }
    private String pretty(Material m) { String[] p=m.name().toLowerCase(Locale.ROOT).split("_"); StringJoiner j=new StringJoiner(" "); for(String s:p)j.add(Character.toUpperCase(s.charAt(0))+s.substring(1)); return j.toString(); }
    private void frame(Inventory inv) {
        ItemStack pane=named(Material.BROWN_STAINED_GLASS_PANE,"&7 ");
        for(int i=0;i<inv.getSize();i++) if(i<9||i>=inv.getSize()-9||i%9==0||i%9==8) inv.setItem(i,pane);
    }
    private ItemStack named(Material mat,String name,String... lore) {
        ItemStack it=new ItemStack(mat); ItemMeta m=it.getItemMeta(); m.setDisplayName(color(name));
        if(lore.length>0)m.setLore(Arrays.stream(lore).map(this::color).toList());
        m.addItemFlags(ItemFlag.HIDE_ATTRIBUTES); it.setItemMeta(m); return it;
    }
    private ItemStack display(ItemStack src,String name,String... lore) {
        ItemStack it=src.clone(); ItemMeta m=it.getItemMeta(); if(m!=null){m.setDisplayName(color(name));m.setLore(Arrays.stream(lore).map(this::color).toList());it.setItemMeta(m);} return it;
    }
    private String color(String s) { return ChatColor.translateAlternateColorCodes('&',s==null?"":s); }
}
