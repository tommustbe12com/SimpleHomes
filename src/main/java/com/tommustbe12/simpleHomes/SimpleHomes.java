package com.tommustbe12.simpleHomes;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.*;

public final class SimpleHomes extends JavaPlugin implements Listener {

    private File dataFile;
    private YamlConfiguration data;
    private FileConfiguration config;
    private final int HARD_MAX = 8; // enforced maximum

    @Override
    public void onEnable() {
        if (!getDataFolder().exists()) getDataFolder().mkdirs();
        dataFile = new File(getDataFolder(), "players.yml");
        if (!dataFile.exists()) {
            try { dataFile.createNewFile(); } catch (IOException e) { getLogger().severe("Could not create players.yml"); }
        }
        data = YamlConfiguration.loadConfiguration(dataFile);

        saveDefaultConfig();
        config = getConfig();

        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("SimpleHomes enabled.");
    }

    @Override
    public void onDisable() {
        saveData();
        getLogger().info("SimpleHomes disabled.");
    }

    private void saveData() {
        try { data.save(dataFile); } catch (IOException e) { getLogger().severe("Failed to save players.yml"); }
    }

    private int getHomeLimit() {
        return Math.min(HARD_MAX, config.getInt("home-limit", 3));
    }

    private void setHomeLimit(int limit) {
        limit = Math.min(HARD_MAX, Math.max(0, limit));
        config.set("home-limit", limit);
        saveConfig();
    }

    private Map<String, Location> getHomes(UUID uuid) {
        Map<String, Location> map = new LinkedHashMap<>();
        String base = "players." + uuid + ".homes";
        if (!data.contains(base)) return map;
        for (String key : data.getConfigurationSection(base).getKeys(false)) {
            String p = base + "." + key;
            String world = data.getString(p + ".world", null);
            if (world == null) continue;
            World w = Bukkit.getWorld(world);
            if (w == null) continue;
            double x = data.getDouble(p + ".x");
            double y = data.getDouble(p + ".y");
            double z = data.getDouble(p + ".z");
            float yaw = (float) data.getDouble(p + ".yaw");
            float pitch = (float) data.getDouble(p + ".pitch");
            map.put(key, new Location(w, x, y, z, yaw, pitch));
        }
        return map;
    }

    private void saveHome(UUID uuid, String slotName, Location loc) {
        String base = "players." + uuid + ".homes." + slotName;
        data.set(base + ".world", loc.getWorld().getName());
        data.set(base + ".x", loc.getX());
        data.set(base + ".y", loc.getY());
        data.set(base + ".z", loc.getZ());
        data.set(base + ".yaw", loc.getYaw());
        data.set(base + ".pitch", loc.getPitch());
        saveData();
    }

    private void removeHome(UUID uuid, String slotName) {
        data.set("players." + uuid + ".homes." + slotName, null);
        saveData();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("sethomelimit")) {
            if (!sender.hasPermission("simplehomes.sethomelimit")) {
                sender.sendMessage("§cYou must be OP to use this command!");
                return true;
            }
            if (args.length != 1) {
                sender.sendMessage("§cUsage: /sethomelimit <0-8>");
                return true;
            }
            try {
                int limit = Integer.parseInt(args[0]);
                if (limit < 0 || limit > HARD_MAX) { sender.sendMessage("§cLimit must be 0-" + HARD_MAX); return true; }
                setHomeLimit(limit);
                sender.sendMessage("§aGlobal home limit set to §f" + limit);
            } catch (NumberFormatException e) {
                sender.sendMessage("§cInvalid number. Usage: /sethomelimit <0-8>");
            }
            return true;
        }

        if (!(sender instanceof Player)) { sender.sendMessage("Only players can use this."); return true; }
        Player p = (Player) sender;
        UUID id = p.getUniqueId();
        int limit = getHomeLimit();

        switch (command.getName().toLowerCase()) {
            case "sethome": openSetHomeGUI(p, limit); break;
            case "home": openHomeGUI(p, limit); break;
        }
        return true;
    }

    private void openSetHomeGUI(Player p, int limit) {
        Inventory inv = Bukkit.createInventory(null, 9, "Set Homes");
        Map<String, Location> homes = getHomes(p.getUniqueId());
        int used = homes.size();

        for (int i = 0; i < HARD_MAX; i++) {
            String key = "home" + (i + 1);
            ItemStack item;
            if (i < limit) {
                if (homes.containsKey(key)) item = makeNamedItem(Material.GREEN_CONCRETE, "§a" + key, Arrays.asList("§7Click to update", "§7Shift-click to delete"));
                else item = makeNamedItem(Material.LIME_STAINED_GLASS_PANE, "§e" + key + " §7(EMPTY)", Arrays.asList("§7Click to save location"));
            } else item = makeNamedItem(Material.GRAY_STAINED_GLASS_PANE, "§7Locked: " + key, Arrays.asList("§7Set by ops: /sethomelimit <0-8>"));
            inv.setItem(i, item);
        }

        // Slot 8 homes used / total
        inv.setItem(8, makeNamedItem(Material.BOOK, "§bHomes Used: §f" + used + " / " + limit,
                Arrays.asList("§7You have used §f" + used + "§7 out of §f" + limit + "§7 homes.")));
        p.openInventory(inv);
    }

    private void openHomeGUI(Player p, int limit) {
        Inventory inv = Bukkit.createInventory(null, 9, "Homes");
        Map<String, Location> homes = getHomes(p.getUniqueId());
        int used = homes.size();

        for (int i = 0; i < HARD_MAX; i++) {
            String key = "home" + (i + 1);
            ItemStack item;
            if (i < limit) {
                if (homes.containsKey(key)) item = makeNamedItem(Material.ENDER_PEARL, "§a" + key, Arrays.asList("§7Click to teleport"));
                else item = makeNamedItem(Material.OAK_SIGN, "§e" + key + " §7(EMPTY)", Arrays.asList("§7No home set"));
            } else item = makeNamedItem(Material.GRAY_STAINED_GLASS_PANE, "§7Locked: " + key, Arrays.asList("§7This home slot is locked by the server owner."));
            inv.setItem(i, item);
        }

        // Slot 8 shows homes used / total
        inv.setItem(8, makeNamedItem(Material.BOOK, "§bHomes Used: §f" + used + " / " + limit,
                Arrays.asList("§7You have used §f" + used + "§7 out of §f" + limit + "§7 homes.")));
        p.openInventory(inv);
    }

    private ItemStack makeNamedItem(Material mat, String name, List<String> lore) {
        ItemStack it = new ItemStack(mat);
        ItemMeta m = it.getItemMeta();
        if (m != null) { m.setDisplayName(name); if (lore != null) m.setLore(lore); it.setItemMeta(m); }
        return it;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent ev) {
        if (!(ev.getWhoClicked() instanceof Player)) return;
        Player p = (Player) ev.getWhoClicked();
        Inventory inv = ev.getView().getTopInventory();
        String title = ev.getView().getTitle();
        if (!title.startsWith("Set Homes") && !title.startsWith("Homes")) return;

        ev.setCancelled(true);
        int slot = ev.getRawSlot();
        if (slot < 0 || slot >= 9) return;
        int limit = getHomeLimit();
        if (slot == 8) return;

        String key = "home" + (slot + 1);
        if (title.startsWith("Set Homes")) {
            if (slot >= limit) { p.sendMessage("§cSlot locked."); return; }
            if (ev.isShiftClick()) {
                Map<String, Location> homes = getHomes(p.getUniqueId());
                if (!homes.containsKey(key)) { p.sendMessage("§cNo home to delete."); return; }
                removeHome(p.getUniqueId(), key);
                p.sendMessage("§aDeleted home §f" + key);
                p.closeInventory();
                new BukkitRunnable() { public void run() { openSetHomeGUI(p, limit); } }.runTaskLater(this, 2L);
            } else {
                saveHome(p.getUniqueId(), key, p.getLocation());
                p.sendMessage("§aSaved location to §f" + key);
                spawnParticles(p.getLocation());
                p.closeInventory();
                new BukkitRunnable() { public void run() { openSetHomeGUI(p, limit); } }.runTaskLater(this, 2L);
            }
        } else if (title.startsWith("Homes")) {
            if (slot >= limit) { p.sendMessage("§cSlot locked."); return; }
            Map<String, Location> homes = getHomes(p.getUniqueId());
            if (!homes.containsKey(key)) { p.sendMessage("§cNo home set."); return; }
            Location target = homes.get(key);
            if (target.getWorld() == null) { p.sendMessage("§cWorld not found."); return; }
            p.closeInventory();
            playTeleportSequence(p, target);
        }
    }

    private void playTeleportSequence(Player p, Location to) {
        Location from = p.getLocation().clone();
        spawnParticles(from);
        new BukkitRunnable() {
            @Override
            public void run() {
                p.teleport(to);
                spawnParticles(to);
                p.getWorld().spawnParticle(Particle.CLOUD, to, 50, 0.5, 0.5, 0.5, 0.02);
            }
        }.runTaskLater(this, 6L);
    }

    private void spawnParticles(Location loc) {
        loc.getWorld().spawnParticle(Particle.END_ROD, loc.add(0, 1, 0), 18, 0.4, 0.6, 0.4, 0.02);
        loc.getWorld().spawnParticle(Particle.FLAME, loc.add(0, -1, 0), 10, 0.2, 0.2, 0.2, 0.01); // replace
    }
}
