package com.jellypudding.simpleHome.commands;

import com.jellypudding.simpleHome.database.DatabaseManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class HomeCommand implements CommandExecutor, TabCompleter {

    private final JavaPlugin plugin;
    private final DatabaseManager databaseManager;
    private final String defaultHomeName;
    private final boolean allowCrossDimensionTeleport;

    public HomeCommand(JavaPlugin plugin, DatabaseManager databaseManager, String defaultHomeName, boolean allowCrossDimensionTeleport) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        this.defaultHomeName = defaultHomeName;
        this.allowCrossDimensionTeleport = allowCrossDimensionTeleport;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("This command can only be used by players.").color(NamedTextColor.RED));
            return true;
        }

        String homeName = (args.length > 0) ? args[0] : defaultHomeName;
        databaseManager.getHomeAsync(player.getUniqueId(), homeName).thenAccept(location -> {
            if (location == null) {
                player.sendMessage(Component.text("Home '" + homeName + "' not found or its world is not loaded.").color(NamedTextColor.RED));
                return;
            }
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) {
                    return;
                }
                if (!allowCrossDimensionTeleport && !player.getWorld().equals(location.getWorld())) {
                    player.sendMessage(Component.text("You cannot teleport to this home as it is in a different dimension.").color(NamedTextColor.RED));
                    return;
                }
                player.teleportAsync(location).thenAccept(success -> {
                    if (success) {
                        player.sendMessage(Component.text("Teleported to your home.").color(NamedTextColor.GREEN));
                    } else {
                        player.sendMessage(Component.text("Teleportation failed.").color(NamedTextColor.RED));
                    }
                });
            });
        });
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            return Collections.emptyList();
        }
        if (args.length == 1) {
            String currentArg = args[0].toLowerCase();
            return databaseManager.getCachedHomes(player.getUniqueId()).stream()
                    .filter(name -> name.toLowerCase().startsWith(currentArg))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}
