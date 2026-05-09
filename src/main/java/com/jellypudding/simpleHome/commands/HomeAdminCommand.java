package com.jellypudding.simpleHome.commands;

import com.jellypudding.simpleHome.database.DatabaseManager;
import com.jellypudding.simpleHome.util.PlayerUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class HomeAdminCommand implements CommandExecutor, TabCompleter {

    private final DatabaseManager databaseManager;
    private final int maxHomeLimit;
    private final Server server;

    public HomeAdminCommand(DatabaseManager databaseManager, int maxHomeLimit, Server server) {
        this.databaseManager = databaseManager;
        this.maxHomeLimit = maxHomeLimit;
        this.server = server;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("simplehome.admin")) {
            sender.sendMessage(Component.text("You don't have permission to use this command.").color(NamedTextColor.RED));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /homeadmin <increase|decrease|get|visit> <player> [home_name]").color(NamedTextColor.RED));
            return true;
        }

        String action = args[0].toLowerCase();
        String playerName = args[1];

        switch (action) {
            case "increase", "decrease" -> {
                UUID targetUUID = PlayerUtil.getPlayerUUID(server, playerName);
                if (targetUUID == null) {
                    sender.sendMessage(Component.text("Player '" + playerName + "' not found. Make sure the name is spelled correctly.").color(NamedTextColor.RED));
                    return true;
                }
                handleHomeLimitChange(sender, playerName, targetUUID, server.getPlayer(playerName), action);
            }
            case "get", "visit" -> {
                UUID targetUUID = PlayerUtil.getPlayerUUID(server, playerName);
                if (targetUUID == null) {
                    sender.sendMessage(Component.text("Player '" + playerName + "' not found. Make sure the name is spelled correctly.").color(NamedTextColor.RED));
                    return true;
                }
                if (action.equals("get")) {
                    handleHomeInfo(sender, playerName, targetUUID);
                } else {
                    handleHomeVisit(sender, args, playerName, targetUUID);
                }
            }
            default -> sender.sendMessage(Component.text("Invalid action. Use 'increase', 'decrease', 'get', or 'visit'.").color(NamedTextColor.RED));
        }
        return true;
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
            return;
        }

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

    private void handleHomeVisit(CommandSender sender, String[] args, String playerName, UUID targetUUID) {
        if (args.length < 3) {
            sender.sendMessage(Component.text("Usage: /homeadmin visit <player> <home_name>").color(NamedTextColor.RED));
            return;
        }

        if (!(sender instanceof Player adminPlayer)) {
            sender.sendMessage(Component.text("This command can only be used by players.").color(NamedTextColor.RED));
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

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!sender.hasPermission("simplehome.admin")) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            String currentArg = args[0].toLowerCase();
            return List.of("increase", "decrease", "get", "visit").stream()
                    .filter(a -> a.startsWith(currentArg))
                    .collect(Collectors.toList());
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("increase") || args[0].equalsIgnoreCase("decrease")
                || args[0].equalsIgnoreCase("get") || args[0].equalsIgnoreCase("visit"))) {
            String currentArg = args[1].toLowerCase();
            return server.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(currentArg))
                    .collect(Collectors.toList());
        } else if (args.length == 3 && args[0].equalsIgnoreCase("visit")) {
            String currentArg = args[2].toLowerCase();
            UUID targetUUID = PlayerUtil.getPlayerUUID(server, args[1]);
            if (targetUUID != null) {
                return databaseManager.getHomes(targetUUID).stream()
                        .filter(name -> name.toLowerCase().startsWith(currentArg))
                        .collect(Collectors.toList());
            }
        }
        return Collections.emptyList();
    }
}
