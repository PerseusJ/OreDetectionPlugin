package org.plugin.oredetectorplugin;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.Color;

import java.util.*;

public class OreDetectorPlugin extends JavaPlugin implements Listener {

    private List<Material> ores;
    private Map<UUID, List<Material>> playerDisabledOres = new HashMap<>();
    private Map<UUID, Map<Material, Integer>> clickCounter = new HashMap<>(); // Track how many times a player clicks an ore
    private Map<UUID, Boolean> playerParticlesEnabled = new HashMap<>();
    private Map<UUID, Boolean> playerSoundsEnabled = new HashMap<>();
    private Map<UUID, Integer> particleClickCounter = new HashMap<>();
    private Map<UUID, Integer> soundClickCounter = new HashMap<>();


    @Override
    public void onEnable() {
        saveDefaultConfig();
        initializeOreList();
        registerOreDetectorCompassRecipe();
        saveDefaultConfig();  // Save default config if it doesn't exist
        reloadConfig();       // Load the config into memory

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!playerDisabledOres.containsKey(player.getUniqueId())) {
                playerDisabledOres.put(player.getUniqueId(), new ArrayList<>(ores));
            }
            if (!playerParticlesEnabled.containsKey(player.getUniqueId())) {
                playerParticlesEnabled.put(player.getUniqueId(), false);
            }
            if (!playerSoundsEnabled.containsKey(player.getUniqueId())) {
                playerSoundsEnabled.put(player.getUniqueId(), false);
            }
        }

        this.getCommand("oreconfigreload").setExecutor(new CommandExecutor() {
            @Override
            public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
                if (sender.hasPermission("oredetectorplugin.reload")) {
                    reloadConfig();
                    sender.sendMessage(ChatColor.GREEN + "OreDetectorPlugin config has been reloaded!");
                    return true;
                } else {
                    sender.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
                    return true;
                }
            }
        });

        this.getServer().getPluginManager().registerEvents(this, this);

        // Setup repeating task for sound effect
        this.getServer().getScheduler().scheduleSyncRepeatingTask(this, new Runnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    checkForOresNearby(player);
                }
            }
        }, 0L, 20L); // Check every 1 second. Change 20L to 40L for 2 seconds.
    }

    private void registerOreDetectorCompassRecipe() {
        ItemStack oreDetectorCompass = new ItemStack(Material.COMPASS);
        ItemMeta meta = oreDetectorCompass.getItemMeta();

        // Fetch the compass item name from config.yml
        String compassItemName = getConfig().getString("compass-item-name", "Ore Detector");

        meta.setDisplayName(compassItemName);
        oreDetectorCompass.setItemMeta(meta);

        ShapedRecipe oreDetectorRecipe = new ShapedRecipe(new NamespacedKey(this, "ore_detector_compass"), oreDetectorCompass);
        oreDetectorRecipe.shape(
                "D D",
                " C ",
                "D D"
        );
        oreDetectorRecipe.setIngredient('D', Material.DIAMOND);
        oreDetectorRecipe.setIngredient('C', Material.COMPASS);

        getServer().addRecipe(oreDetectorRecipe);
    }

    private boolean isOreDetectorCompass(ItemStack item) {
        // Fetch the compass item name from config.yml
        String compassItemName = getConfig().getString("compass-item-name", "Ore Detector");

        if (item != null && item.getType() == Material.COMPASS) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null && compassItemName.equals(meta.getDisplayName())) {
                return true;
            }
        }
        return false;
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
    public void onCraftItem(CraftItemEvent event) {
        String compassItemName = getConfig().getString("compass-item-name", "Ore Detector");
        ItemStack resultItem = event.getRecipe().getResult();
        if (resultItem.getType() == Material.COMPASS && resultItem.hasItemMeta() && resultItem.getItemMeta().hasDisplayName() && resultItem.getItemMeta().getDisplayName().equals(compassItemName)) {
            HumanEntity human = event.getWhoClicked();
            if (human instanceof Player) {
                Player player = (Player) human;
                if (!player.hasPermission("ore-detector.craft")) {
                    player.sendMessage(ChatColor.RED + "You do not have permission to craft the Ore Detector!");
                    event.setCancelled(true);
                }
            }
        }
    }


    public void checkForOresNearby(Player player) {
        int distance = getConfig().getInt("ore-detection-settings.distance");
        int particleCount = getConfig().getInt("ore-detection-settings.particle-count");
        float maxVolume = (float) getConfig().getDouble("ore-detection-settings.volume");

        boolean particlesEnabled = playerParticlesEnabled.get(player.getUniqueId());
        boolean soundEnabled = playerSoundsEnabled.get(player.getUniqueId());

        Block nearestOreBlock = null; // Store the nearest ore block found
        double nearestOreDistance = Double.MAX_VALUE; // Initialize to a large value for comparison

        for (int x = -distance; x <= distance; x++) {
            for (int y = -distance; y <= distance; y++) {
                for (int z = -distance; z <= distance; z++) {
                    Block block = player.getLocation().add(x, y, z).getBlock();
                    if (ores.contains(block.getType())) {
                        List<Material> disabledOresForPlayer = playerDisabledOres.get(player.getUniqueId());
                        if (disabledOresForPlayer == null || !disabledOresForPlayer.contains(block.getType())) {
                            double blockDistance = player.getLocation().distance(block.getLocation());
                            if (blockDistance < nearestOreDistance) { // Check if this ore is closer than the previously found the nearest ore
                                nearestOreDistance = blockDistance;
                                nearestOreBlock = block;
                            }

                            float scaledVolume = (float) (maxVolume * (1 - (blockDistance / (2 * distance))));

                            if (particlesEnabled) {
                                Particle particleEffect;
                                try {
                                    particleEffect = Particle.valueOf(getConfig().getString("ore-detection-settings.particles." + block.getType().name()).toUpperCase());
                                } catch (IllegalArgumentException e) {
                                    getLogger().warning("Invalid particle specified for " + block.getType().name() + " in config.yml. Falling back to END_ROD.");
                                    particleEffect = Particle.END_ROD;
                                }

                                if (particleEffect == Particle.REDSTONE) {
                                    spawnColoredDust(player, block);
                                } else if (particleEffect == Particle.FALLING_DUST) {
                                    BlockData blockData = block.getBlockData();
                                    player.spawnParticle(particleEffect, block.getLocation().add(0.5, 0.5, 0.5), particleCount, 0.5, 0.5, 0.5, 0, blockData);
                                } else {
                                    player.spawnParticle(particleEffect, block.getLocation().add(0.5, 0.5, 0.5), particleCount, 0.5, 0.5, 0.5, 0);
                                }
                            }

                            if (soundEnabled) {
                                Sound oreSound;
                                try {
                                    oreSound = Sound.valueOf(getConfig().getString("ore-detection-settings.sounds." + block.getType().name()).toUpperCase());
                                } catch (IllegalArgumentException e) {
                                    getLogger().warning("Invalid sound specified for " + block.getType().name() + " in config.yml. Falling back to ENTITY_EXPERIENCE_ORB_PICKUP.");
                                    oreSound = Sound.ENTITY_EXPERIENCE_ORB_PICKUP;
                                }
                                player.playSound(block.getLocation(), oreSound, scaledVolume, 1f); // Playing sound from ore's location
                            }
                        }
                    }
                }
            }
        }

        // Display the message about the nearest ore
        if (nearestOreBlock != null) {
            int gridDistance = (int) Math.ceil(nearestOreDistance); // Convert the distance to an integer for display
            String message = getConfig().getString("message", "%ore_name% is about %grid_range% blocks away")
                    .replace("%ore_name%", getConfig().getString("ore-names." + nearestOreBlock.getType().name(), nearestOreBlock.getType().name().replace("_", " ").toLowerCase()))
                    .replace("%grid_range%", String.valueOf(gridDistance));
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(message));

            // Set the compass target to this block
            updatePlayerCompassTarget(player, nearestOreBlock.getLocation());
        } else {
            // Reset compass to spawn location if no ores are nearby or all ores are disabled
            updatePlayerCompassTarget(player, player.getWorld().getSpawnLocation());
        }
    }

    private void updatePlayerCompassTarget(Player player, Location targetLocation) {
        player.setCompassTarget(targetLocation);
    }

    public void spawnColoredDust(Player player, Block block) {
        // Get block type as a string
        String blockType = block.getType().toString();

        // Pass the block type to getColorFromConfig
        Particle.DustOptions dustOptions = new Particle.DustOptions(getColorFromConfig(blockType), 1);

        player.spawnParticle(Particle.REDSTONE, block.getLocation().add(0.5, 0.5, 0.5), 15, 0.5, 0.5, 0.5, 0, dustOptions);
    }


    private Color getColorFromConfig(String oreType) {
        // Updated path to color configuration
        ConfigurationSection colorSection = getConfig().getConfigurationSection("ore-detection-settings.color." + oreType);

        if (colorSection == null) {
            getLogger().warning("Color configuration for " + oreType + " is missing. Defaulting to RED.");
            return Color.RED;
        }

        try {
            int r = colorSection.getInt("r");
            int g = colorSection.getInt("g");
            int b = colorSection.getInt("b");

            // Ensure color values are between 0 and 255
            r = Math.max(0, Math.min(255, r));
            g = Math.max(0, Math.min(255, g));
            b = Math.max(0, Math.min(255, b));

            return Color.fromRGB(r, g, b);
        } catch (Exception e) {
            getLogger().warning("RGB values in the config for " + oreType + " are not valid. Defaulting to RED.");
            return Color.RED;
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();

        if (!playerDisabledOres.containsKey(playerId)) {
            playerDisabledOres.put(playerId, new ArrayList<>(ores));
        }

        if (!playerParticlesEnabled.containsKey(playerId)) {
            playerParticlesEnabled.put(playerId, false);
        }

        if (!playerSoundsEnabled.containsKey(playerId)) {
            playerSoundsEnabled.put(playerId, false);
        }
    }


    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack itemInHand = player.getInventory().getItemInMainHand();

        String compassItemName = getConfig().getString("compass-item-name", "Ore Detector");

        if (itemInHand.getType() == Material.COMPASS && itemInHand.hasItemMeta() && itemInHand.getItemMeta().hasDisplayName() && itemInHand.getItemMeta().getDisplayName().equals(compassItemName)) {
            // Check for the use permission
            if (!player.hasPermission("ore-detector.use")) {
                player.sendMessage(ChatColor.RED + "You do not have permission to use the Ore Detector!");
                event.setCancelled(true); // Cancel the event to make sure they can't use the compass
                return;
            }

            if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
                openOreToggleGUI(player);
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getClickedInventory() == null) return;

        String compassGUITitle = getConfig().getString("compass-gui-title", "Toggle Ore Detection");
        if (event.getView().getTitle().equals(compassGUITitle)) {
            event.setCancelled(true);

            Player player = (Player) event.getWhoClicked();
            ItemStack clickedItem = event.getCurrentItem();

            // Handling particles
            if (event.getCurrentItem().getType() == Material.REDSTONE) {
                int currentClicks = particleClickCounter.getOrDefault(player.getUniqueId(), 0) + 1;

                if (currentClicks == 1) {
                    boolean currentStatus = playerParticlesEnabled.getOrDefault(player.getUniqueId(), true);
                    playerParticlesEnabled.put(player.getUniqueId(), !currentStatus);
                    particleClickCounter.put(player.getUniqueId(), 0);  // Reset click counter
                } else {
                    particleClickCounter.put(player.getUniqueId(), currentClicks);
                }

                openOreToggleGUI(player); // Refresh the GUI
            }

            // Handling sounds
            else if (event.getCurrentItem().getType() == Material.NOTE_BLOCK) {
                int currentClicks = soundClickCounter.getOrDefault(player.getUniqueId(), 0) + 1;

                if (currentClicks == 1) {
                    boolean currentStatus = playerSoundsEnabled.getOrDefault(player.getUniqueId(), true);
                    playerSoundsEnabled.put(player.getUniqueId(), !currentStatus);
                    soundClickCounter.put(player.getUniqueId(), 0);  // Reset click counter
                } else {
                    soundClickCounter.put(player.getUniqueId(), currentClicks);
                }

                openOreToggleGUI(player); // Refresh the GUI
            }

            if (clickedItem.getType() == Material.LIME_DYE) {
                playerDisabledOres.put(player.getUniqueId(), new ArrayList<>());
                openOreToggleGUI(player); // Refresh the GUI
            }

            // Handling "Disable All" button
            else if (clickedItem.getType() == Material.GRAY_DYE) {
                playerDisabledOres.put(player.getUniqueId(), new ArrayList<>(ores));
                openOreToggleGUI(player); // Refresh the GUI
            }

            if (clickedItem != null && ores.contains(clickedItem.getType())) {
                Material clickedOre = clickedItem.getType();
                List<Material> disabledOresForPlayer = playerDisabledOres.getOrDefault(player.getUniqueId(), new ArrayList<>());

                // Handle the click count for ores
                Map<Material, Integer> playerClicks = clickCounter.getOrDefault(player.getUniqueId(), new HashMap<>());
                int currentClicks = playerClicks.getOrDefault(clickedOre, 0) + 1;

                if (currentClicks == 2) {
                    if (disabledOresForPlayer.contains(clickedOre)) {
                        disabledOresForPlayer.remove(clickedOre);
                    } else {
                        disabledOresForPlayer.add(clickedOre);
                    }
                    playerDisabledOres.put(player.getUniqueId(), disabledOresForPlayer);
                    playerClicks.put(clickedOre, 0); // Reset the click counter for ores
                } else {
                    playerClicks.put(clickedOre, currentClicks);
                }

                clickCounter.put(player.getUniqueId(), playerClicks);
                openOreToggleGUI(player);
            }
        }
    }


    private void openOreToggleGUI(Player player) {
        int size = 54;  // Large chest size
        String compassGUITitle = getConfig().getString("compass-gui-title", "Toggle Ore Detection");
        Inventory gui = Bukkit.createInventory(player, size, compassGUITitle);

        List<Material> disabledOresForPlayer = playerDisabledOres.getOrDefault(player.getUniqueId(), new ArrayList<>());

        int slotIndex = 0;
        for (Material ore : ores) {
            if (slotIndex >= size) break; // Ensure we don't exceed the GUI size

            ItemStack item = new ItemStack(ore);
            ItemMeta meta = item.getItemMeta();
            String customOreName = getConfig().getString("ore-names." + ore.name(), ore.name().replace("_", " ").toLowerCase());

            if (disabledOresForPlayer.contains(ore)) {
                meta.setDisplayName(ChatColor.RED + customOreName);
            } else {
                meta.setDisplayName(ChatColor.GREEN + customOreName);
            }

            item.setItemMeta(meta);
            gui.setItem(slotIndex, item);

            slotIndex += 2; // Increment by 2 to leave space between each ore
        }

        // "Enable All" button
        ItemStack enableAllItem = new ItemStack(Material.LIME_DYE);
        ItemMeta enableAllMeta = enableAllItem.getItemMeta();
        enableAllMeta.setDisplayName(ChatColor.GREEN + "Enable All Ores");
        enableAllItem.setItemMeta(enableAllMeta);
        gui.setItem(45, enableAllItem);

        // "Disable All" button
        ItemStack disableAllItem = new ItemStack(Material.GRAY_DYE);
        ItemMeta disableAllMeta = disableAllItem.getItemMeta();
        disableAllMeta.setDisplayName(ChatColor.RED + "Disable All Ores");
        disableAllItem.setItemMeta(disableAllMeta);
        gui.setItem(53, disableAllItem);

        ItemStack particleToggleItem = new ItemStack(Material.REDSTONE);
        ItemMeta particleToggleMeta = particleToggleItem.getItemMeta();
        boolean particlesEnabled = playerParticlesEnabled.getOrDefault(player.getUniqueId(), true);
        particleToggleMeta.setDisplayName(particlesEnabled ? ChatColor.GREEN + "Particles: Enabled" : ChatColor.RED + "Particles: Disabled");
        particleToggleItem.setItemMeta(particleToggleMeta);

        ItemStack soundToggleItem = new ItemStack(Material.NOTE_BLOCK);
        ItemMeta soundToggleMeta = soundToggleItem.getItemMeta();
        boolean soundEnabled = playerSoundsEnabled.getOrDefault(player.getUniqueId(), true);
        soundToggleMeta.setDisplayName(soundEnabled ? ChatColor.GREEN + "Sound: Enabled" : ChatColor.RED + "Sound: Disabled");
        soundToggleItem.setItemMeta(soundToggleMeta);

        gui.setItem(48, particleToggleItem);
        gui.setItem(50, soundToggleItem);

        player.openInventory(gui);
    }
}