package com.jellypudding.simpleHome;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
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
    private int maxHomeLimit = 10;

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
        Objects.requireNonNull(getCommand("homeadmin")).setExecutor(this);
        Objects.requireNonNull(getCommand("homeadmin")).setTabCompleter(this);

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
        int configuredLimit = config.getInt("max-home-limit", 10);
        if (configuredLimit < 1) {
            getLogger().warning("Invalid max-home-limit in config.yml (must be >= 1). Using default value: 10");
            this.maxHomeLimit = 10;
        } else {
            this.maxHomeLimit = configuredLimit;
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        String commandName = command.getName().toLowerCase();

        // Handle admin commands that can be used by console.
        if (commandName.equals("homeadmin")) {
            handleHomeAdmin(sender, args);
            return true;
        }

        // All other commands require a player.
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("This command can only be used by players!").color(NamedTextColor.RED));
            return true;
        }

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
        String commandName = command.getName().toLowerCase();

        // Handle homeadmin tab completion (works for console too).
        if (commandName.equals("homeadmin")) {
            if (!sender.hasPermission("simplehome.admin")) {
                return Collections.emptyList();
            }

            if (args.length == 1) {
                String currentArg = args[0].toLowerCase();
                return List.of("increase", "decrease", "get", "visit").stream()
                        .filter(action -> action.startsWith(currentArg))
                        .collect(Collectors.toList());
            } else if (args.length == 2 && (args[0].equalsIgnoreCase("increase") || args[0].equalsIgnoreCase("decrease") || args[0].equalsIgnoreCase("get") || args[0].equalsIgnoreCase("visit"))) {
                String currentArg = args[1].toLowerCase();
                return getServer().getOnlinePlayers().stream()
                        .map(p -> p.getName())
                        .filter(name -> name.toLowerCase().startsWith(currentArg))
                        .collect(Collectors.toList());
            } else if (args.length == 3 && args[0].equalsIgnoreCase("visit")) {
                String currentArg = args[2].toLowerCase();
                UUID targetUUID = getPlayerUUID(args[1]);
                if (targetUUID != null) {
                    List<String> homeNames = databaseManager.getHomes(targetUUID);
                    return homeNames.stream()
                            .filter(name -> name.toLowerCase().startsWith(currentArg))
                            .collect(Collectors.toList());
                }
            }
        }

        // All other commands require a player.
        if (!(sender instanceof Player player)) {
            return Collections.emptyList();
        }

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
            TextComponent homesText = Component.text("Your homes (" + homeNames.size() + "/" + homeLimit + "): ").color(NamedTextColor.GOLD);
            int size = homeNames.size();
            for (int i = 0; i < size; i++) {
                String name = homeNames.get(i);
                TextComponent homesNameText = Component.text(name)
                        .clickEvent(ClickEvent.suggestCommand("/home " + name))
                        .hoverEvent(HoverEvent.showText(Component.text("Click to use command.")))
                        .decoration(TextDecoration.UNDERLINED, true)
                        .color(NamedTextColor.WHITE);
                homesText = homesText.append(homesNameText);
                if(i < size - 1) {
                    homesText = homesText.append(Component.text(", ").color(NamedTextColor.WHITE));
                }
            }
            player.sendMessage(homesText);
        }
    }

    private void handleHomeAdmin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("simplehome.admin")) {
            sender.sendMessage(Component.text("You don't have permission to use this command.").color(NamedTextColor.RED));
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /homeadmin <increase|decrease|get|visit> <player> [home_name]").color(NamedTextColor.RED));
            return;
        }

        String action = args[0].toLowerCase();
        String playerName = args[1];

        switch (action) {
            case "increase":
            case "decrease":
                UUID targetUUID = getPlayerUUID(playerName);
                if (targetUUID == null) {
                    sender.sendMessage(Component.text("Player '" + playerName + "' not found. Make sure the name is spelled correctly.").color(NamedTextColor.RED));
                    return;
                }
                Player onlinePlayer = getServer().getPlayer(playerName);
                handleHomeLimitChange(sender, playerName, targetUUID, onlinePlayer, action);
                break;

            case "get":
            case "visit":
                UUID targetUUID2 = getPlayerUUID(playerName);
                if (targetUUID2 == null) {
                    sender.sendMessage(Component.text("Player '" + playerName + "' not found. Make sure the name is spelled correctly.").color(NamedTextColor.RED));
                    return;
                }

                if (action.equals("get")) {
                    handleHomeInfo(sender, playerName, targetUUID2);
                } else {
                    handleHomeVisit(sender, args, playerName, targetUUID2);
                }
                break;

            default:
                sender.sendMessage(Component.text("Invalid action. Use 'increase', 'decrease', 'get', or 'visit'.").color(NamedTextColor.RED));
                break;
        }
    }

    private void handleHomeLimitChange(CommandSender sender, String playerName, UUID targetUUID, Player onlinePlayer, String action) {
        int currentLimit = databaseManager.getHomeLimit(targetUUID);

        if (action.equals("increase")) {
            if (currentLimit >= maxHomeLimit) {
                sender.sendMessage(Component.text(playerName + " is already at the maximum home limit (" + maxHomeLimit + ").").color(NamedTextColor.RED));
                return;
            }

            if (databaseManager.setHomeLimit(targetUUID, currentLimit + 1)) {
                sender.sendMessage(Component.text("Increased " + playerName + "'s home limit from " + currentLimit + " to " + (currentLimit + 1) + ".").color(NamedTextColor.GREEN));
                if (onlinePlayer != null) {
                    onlinePlayer.sendMessage(Component.text("Your home limit has been increased to " + (currentLimit + 1) + ".").color(NamedTextColor.GREEN));
                }
            } else {
                sender.sendMessage(Component.text("Failed to increase " + playerName + "'s home limit.").color(NamedTextColor.RED));
            }
        } else {
            if (currentLimit <= 1) {
                sender.sendMessage(Component.text(playerName + " is already at the minimum home limit (1).").color(NamedTextColor.RED));
                return;
            }

            int currentHomes = databaseManager.getHomeCount(targetUUID);
            int newLimit = currentLimit - 1;

            // Auto-delete excess homes if necessary.
            if (currentHomes > newLimit) {
                List<String> homeNames = databaseManager.getHomes(targetUUID);
                int homesToDelete = currentHomes - newLimit;

                for (int i = homeNames.size() - 1; i >= homeNames.size() - homesToDelete; i--) {
                    String homeToDelete = homeNames.get(i);
                    if (databaseManager.deleteHome(targetUUID, homeToDelete)) {
                        sender.sendMessage(Component.text("Auto-deleted home '" + homeToDelete + "' from " + playerName + ".").color(NamedTextColor.YELLOW));
                        if (onlinePlayer != null) {
                            onlinePlayer.sendMessage(Component.text("Your home '" + homeToDelete + "' was deleted due to a limit decrease.").color(NamedTextColor.YELLOW));
                        }
                    }
                }
            }

            if (databaseManager.setHomeLimit(targetUUID, newLimit)) {
                sender.sendMessage(Component.text("Decreased " + playerName + "'s home limit from " + currentLimit + " to " + newLimit + ".").color(NamedTextColor.GREEN));
                if (onlinePlayer != null) {
                    onlinePlayer.sendMessage(Component.text("Your home limit has been decreased to " + newLimit + ".").color(NamedTextColor.YELLOW));
                }
            } else {
                sender.sendMessage(Component.text("Failed to decrease " + playerName + "'s home limit.").color(NamedTextColor.RED));
            }
        }
    }

    private void handleHomeInfo(CommandSender sender, String playerName, UUID targetUUID) {
        int currentLimit = databaseManager.getHomeLimit(targetUUID);
        int homeCount = databaseManager.getHomeCount(targetUUID);
        List<String> homeNames = databaseManager.getHomes(targetUUID);

        sender.sendMessage(Component.text(playerName + "'s home info:").color(NamedTextColor.GOLD));
        sender.sendMessage(Component.text("  Current homes: " + homeCount + " / " + currentLimit).color(NamedTextColor.YELLOW));

        if (homeNames.isEmpty()) {
            sender.sendMessage(Component.text("  No homes set.").color(NamedTextColor.GRAY));
        } else {
            TextComponent homesText = Component.text("  Home names: ").color(NamedTextColor.AQUA);
            int size = homeNames.size();
            for (int i = 0; i < size; i++) {
                String name = homeNames.get(i);
                TextComponent homeNameComponent = Component.text(name)
                        .clickEvent(ClickEvent.suggestCommand("/homeadmin visit " + playerName + " " + name))
                        .hoverEvent(HoverEvent.showText(Component.text("Click to teleport to this home")))
                        .decoration(TextDecoration.UNDERLINED, true)
                        .color(NamedTextColor.WHITE);
                homesText = homesText.append(homeNameComponent);
                if (i < size - 1) {
                    homesText = homesText.append(Component.text(", ").color(NamedTextColor.WHITE));
                }
            }
            sender.sendMessage(homesText);
        }
    }

    private void handleHomeVisit(CommandSender sender, String[] args, String playerName, UUID targetUUID) {
        if (args.length < 3) {
            sender.sendMessage(Component.text("Usage: /homeadmin visit <player> <home_name>").color(NamedTextColor.RED));
            return;
        }

        if (!(sender instanceof Player adminPlayer)) {
            sender.sendMessage(Component.text("This command can only be used by players!").color(NamedTextColor.RED));
            return;
        }

        String homeName = args[2];
        Location homeLocation = databaseManager.getHome(targetUUID, homeName);

        if (homeLocation == null) {
            sender.sendMessage(Component.text("Home '" + homeName + "' not found for player '" + playerName + "' or its world is not loaded.").color(NamedTextColor.RED));
            return;
        }

        adminPlayer.teleportAsync(homeLocation).thenAccept(success -> {
            if (success) {
                adminPlayer.sendMessage(Component.text("Teleported to " + playerName + "'s home '" + homeName + "'.").color(NamedTextColor.GREEN));
            } else {
                adminPlayer.sendMessage(Component.text("Teleportation failed.").color(NamedTextColor.RED));
            }
        });
    }

    private boolean isValidHomeName(String name) {
        return name != null && !name.isEmpty() && name.matches("^[a-zA-Z0-9_-]+$") && name.length() <= 30;
    }

    private UUID getPlayerUUID(String playerName) {
        // First try to find online player.
        Player onlinePlayer = getServer().getPlayer(playerName);
        if (onlinePlayer != null) {
            return onlinePlayer.getUniqueId();
        }

        // If not online, try offline player.
        org.bukkit.OfflinePlayer offlinePlayer = getServer().getOfflinePlayer(playerName);
        if (offlinePlayer != null && offlinePlayer.hasPlayedBefore()) {
            return offlinePlayer.getUniqueId();
        }

        return null;
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
