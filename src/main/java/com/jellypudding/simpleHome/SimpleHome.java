package com.jellypudding.simpleHome;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

public final class SimpleHome extends JavaPlugin implements Listener {

    private DatabaseManager databaseManager;
    private final String defaultHomeName = "home";
    private int maxHomeLimit = 5;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadConfigValues();

        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }

        databaseManager = new DatabaseManager(this, maxHomeLimit);

        getServer().getPluginManager().registerEvents(this, this);

        Objects.requireNonNull(getCommand("sethome")).setExecutor(this);
        Objects.requireNonNull(getCommand("sethome")).setTabCompleter(this);
        Objects.requireNonNull(getCommand("home")).setExecutor(this);
        Objects.requireNonNull(getCommand("home")).setTabCompleter(this);
        Objects.requireNonNull(getCommand("delhome")).setExecutor(this);
        Objects.requireNonNull(getCommand("delhome")).setTabCompleter(this);
        Objects.requireNonNull(getCommand("homes")).setExecutor(this);
        Objects.requireNonNull(getCommand("homes")).setTabCompleter(this);

        getLogger().info("SimpleHome has been enabled!");
    }

    @Override
    public void onDisable() {
        if (databaseManager != null) {
            databaseManager.closeConnection();
        }
        getLogger().info("SimpleHome has been disabled!");
    }

    private void loadConfigValues() {
        FileConfiguration config = getConfig();
        int configuredLimit = config.getInt("max-home-limit", 5);
        if (configuredLimit < 1) {
            getLogger().warning("Invalid max-home-limit in config.yml (must be >= 1). Using default value: 5");
            this.maxHomeLimit = 5;
        } else {
            this.maxHomeLimit = configuredLimit;
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("This command can only be used by players!").color(NamedTextColor.RED));
            return true;
        }

        String commandName = command.getName().toLowerCase();

        switch (commandName) {
            case "sethome":
                handleSetHome(player, args);
                break;
            case "home":
                handleHome(player, args);
                break;
            case "delhome":
                handleDelHome(player, args);
                break;
            case "homes":
                handleHomes(player);
                break;
            default:
                return false;
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            return Collections.emptyList();
        }

        String commandName = command.getName().toLowerCase();

        if (commandName.equals("home") || commandName.equals("delhome")) {
            if (args.length == 1) {
                String currentArg = args[0].toLowerCase();
                List<String> homeNames = databaseManager.getHomes(player.getUniqueId());
                return homeNames.stream()
                                .filter(name -> name.toLowerCase().startsWith(currentArg))
                                .collect(Collectors.toList());
            }
        }
        return Collections.emptyList();
    }

    // --- Command Handlers ---

    private void handleSetHome(Player player, String[] args) {
        String homeName = (args.length > 0) ? args[0] : defaultHomeName;

        if (!isValidHomeName(homeName)) {
            player.sendMessage(Component.text("Invalid home name. Use letters, numbers, underscores, or hyphens.").color(NamedTextColor.RED));
            return;
        }

        int currentHomeCount = databaseManager.getHomeCount(player.getUniqueId());
        int homeLimit = databaseManager.getHomeLimit(player.getUniqueId());
        boolean isUpdating = databaseManager.getHomes(player.getUniqueId()).contains(homeName.toLowerCase());

        if (!isUpdating && currentHomeCount >= homeLimit) {
            player.sendMessage(Component.text("You have reached your home limit of " + homeLimit + ".").color(NamedTextColor.RED));
            return;
        }

        Location location = player.getLocation();
        if (databaseManager.setHome(player.getUniqueId(), homeName, location)) {
            player.sendMessage(Component.text("Home set.").color(NamedTextColor.GREEN));
        } else {
            player.sendMessage(Component.text("Failed to set home '" + homeName + ".").color(NamedTextColor.RED));
        }
    }

    private void handleHome(Player player, String[] args) {
        String homeName = (args.length > 0) ? args[0] : defaultHomeName;

        Location location = databaseManager.getHome(player.getUniqueId(), homeName);

        if (location == null) {
            player.sendMessage(Component.text("Home '" + homeName + "' not found or its world is not loaded.").color(NamedTextColor.RED));
            return;
        }

        player.teleportAsync(location).thenAccept(success -> {
            if (success) {
                player.sendMessage(Component.text("Teleported to your home.").color(NamedTextColor.GREEN));
            } else {
                player.sendMessage(Component.text("Teleportation failed.").color(NamedTextColor.RED));
            }
        });
    }

    private void handleDelHome(Player player, String[] args) {
        if (args.length == 0) {
            player.sendMessage(Component.text("Usage: /delhome <name>").color(NamedTextColor.RED));
            return;
        }
        String homeName = args[0];

        if (databaseManager.deleteHome(player.getUniqueId(), homeName)) {
            player.sendMessage(Component.text("Home '" + homeName + "' deleted.").color(NamedTextColor.GREEN));
        } else {
            player.sendMessage(Component.text("Home '" + homeName + "' not found.").color(NamedTextColor.RED));
        }
    }

    private void handleHomes(Player player) {
        List<String> homeNames = databaseManager.getHomes(player.getUniqueId());
        int homeLimit = databaseManager.getHomeLimit(player.getUniqueId());

        if (homeNames.isEmpty()) {
            player.sendMessage(Component.text("You have no homes set. Use /sethome [name]").color(NamedTextColor.YELLOW));
        } else {
            String homesList = String.join(", ", homeNames);
            player.sendMessage(Component.text("Your homes (" + homeNames.size() + "/" + homeLimit + "): ").color(NamedTextColor.GOLD)
                                    .append(Component.text(homesList).color(NamedTextColor.WHITE)));
        }
    }

    private boolean isValidHomeName(String name) {
        return name != null && !name.isEmpty() && name.matches("^[a-zA-Z0-9_-]+$") && name.length() <= 30;
    }

    // --- API Methods --- 
    public int getHomeLimit(UUID playerUUID) {
        if (databaseManager == null) {
             getLogger().warning("Attempted to get home limit, but DatabaseManager is null.");
             return 1;
        }
        return databaseManager.getHomeLimit(playerUUID);
    }

    public boolean increaseHomeLimit(UUID playerUUID) {
         if (databaseManager == null) {
             getLogger().severe("Attempted to increase home limit, but DatabaseManager is null.");
             return false;
         }
         if (getHomeLimit(playerUUID) >= this.maxHomeLimit) {
             return false;
         }
         boolean success = databaseManager.increaseHomeLimit(playerUUID);
         return success;
    }

     public int getCurrentHomeCount(UUID playerUUID) {
        if (databaseManager == null) {
             getLogger().warning("Attempted to get home count, but DatabaseManager is null.");
             return 0;
        }
        return databaseManager.getHomeCount(playerUUID);
     }
}