package org.plugin.oredetectorplugin;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class OreDetectorPlugin extends JavaPlugin implements Listener {

    private List<Material> ores;

    private Map<UUID, List<Material>> playerDisabledOres = new HashMap<>();

    @Override
    public void onEnable() {
        initializeOreList();

        this.getServer().getPluginManager().registerEvents(this, this);
        this.getCommand("oretoggle").setExecutor(new OreToggleCommand());
    }

    private String getServerVersion() {
        String version = Bukkit.getServer().getVersion();
        if (version.contains("1.16")) {
            return "1.16";
        } else if (version.contains("1.17")) {
            return "1.17";
        } else if (version.contains("1.18")) {
            return "1.18";
        } else if (version.contains("1.19")) {
            return "1.19";
        } else if (version.contains("1.20")) {
            return "1.20";
        } else {
            return "UNKNOWN";
        }
    }

    private void initializeOreList() {
        ores = new ArrayList<>();

        // Ores common to all versions from 1.16 to 1.20
        ores.addAll(Arrays.asList(
                Material.COAL_ORE,
                Material.IRON_ORE,
                Material.GOLD_ORE,
                Material.REDSTONE_ORE,
                Material.LAPIS_ORE,
                Material.DIAMOND_ORE,
                Material.EMERALD_ORE,
                Material.NETHER_QUARTZ_ORE
        ));

        String version = getServerVersion();
        if (version.equals("1.17") || version.equals("1.18") || version.equals("1.19") || version.equals("1.20")) {
            // Ores introduced in versions 1.17+
            ores.addAll(Arrays.asList(
                    Material.COPPER_ORE,
                    Material.NETHER_GOLD_ORE,
                    Material.ANCIENT_DEBRIS,
                    Material.DEEPSLATE_COAL_ORE,
                    Material.DEEPSLATE_IRON_ORE,
                    Material.DEEPSLATE_COPPER_ORE,
                    Material.DEEPSLATE_GOLD_ORE,
                    Material.DEEPSLATE_REDSTONE_ORE,
                    Material.DEEPSLATE_EMERALD_ORE,
                    Material.DEEPSLATE_LAPIS_ORE,
                    Material.DEEPSLATE_DIAMOND_ORE
            ));
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        // Check if the player actually moved in terms of coordinates
        if (event.getFrom().getBlockX() == event.getTo().getBlockX() &&
                event.getFrom().getBlockY() == event.getTo().getBlockY() &&
                event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return; // The player did not move, so return early
        }

        Player player = event.getPlayer();

        for (int x = -3; x <= 3; x++) {
            for (int y = -3; y <= 3; y++) {
                for (int z = -3; z <= 3; z++) {
                    Block block = player.getLocation().add(x, y, z).getBlock();
                    if (ores.contains(block.getType())) {
                        List<Material> disabledOresForPlayer = playerDisabledOres.get(player.getUniqueId());
                        if (disabledOresForPlayer == null || !disabledOresForPlayer.contains(block.getType())) {
                            player.spawnParticle(Particle.END_ROD, block.getLocation().add(0.5, 0.5, 0.5), 1, 0.5, 0.5, 0.5, 0);
                            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.1f, 1f);
                        }
                    }
                }
            }
        }
    }


    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getClickedInventory() == null) return;

        if (event.getView().getTitle().equals("Toggle Ore Detection")) {
            event.setCancelled(true);

            Player player = (Player) event.getWhoClicked();
            ItemStack clickedItem = event.getCurrentItem();

            if (clickedItem != null && ores.contains(clickedItem.getType())) {
                Material clickedOre = clickedItem.getType();
                List<Material> disabledOresForPlayer = playerDisabledOres.getOrDefault(player.getUniqueId(), new ArrayList<>());

                if (disabledOresForPlayer.contains(clickedOre)) {
                    disabledOresForPlayer.remove(clickedOre);
                    // Removed the sendMessage line
                } else {
                    disabledOresForPlayer.add(clickedOre);
                    // Removed the sendMessage line
                }

                playerDisabledOres.put(player.getUniqueId(), disabledOresForPlayer);
                openOreToggleGUI(player);
            }
        }
    }


    public class OreToggleCommand implements CommandExecutor {

        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("Only players can use this command.");
                return true;
            }

            Player player = (Player) sender;
            openOreToggleGUI(player);

            return true;
        }
    }

    private void openOreToggleGUI(Player player) {
        int size = 27;  // A small inventory size, can be adjusted
        Inventory gui = Bukkit.createInventory(player, size, "Toggle Ore Detection");

        List<Material> disabledOresForPlayer = playerDisabledOres.getOrDefault(player.getUniqueId(), new ArrayList<>());

        for (Material ore : ores) {
            ItemStack item = new ItemStack(ore);
            ItemMeta meta = item.getItemMeta();

            if (disabledOresForPlayer.contains(ore)) {
                meta.setLore(Arrays.asList("Detection: Disabled"));
            } else {
                meta.setLore(Arrays.asList("Detection: Enabled"));
            }

            item.setItemMeta(meta);
            gui.addItem(item);
        }

        player.openInventory(gui);
    }
}
