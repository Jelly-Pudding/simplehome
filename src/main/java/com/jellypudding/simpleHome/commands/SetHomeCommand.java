package com.jellypudding.simpleHome.commands;

import com.jellypudding.simpleHome.database.DatabaseManager;
import com.jellypudding.simpleHome.util.PlayerUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public class SetHomeCommand implements CommandExecutor, TabCompleter {

    private final DatabaseManager databaseManager;
    private final String defaultHomeName;

    public SetHomeCommand(DatabaseManager databaseManager, String defaultHomeName) {
        this.databaseManager = databaseManager;
        this.defaultHomeName = defaultHomeName;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("This command can only be used by players!").color(NamedTextColor.RED));
            return true;
        }

        String homeName = (args.length > 0) ? args[0] : defaultHomeName;

        if (!PlayerUtil.isValidHomeName(homeName)) {
            player.sendMessage(Component.text("Invalid home name. Use letters, numbers, underscores, or hyphens.").color(NamedTextColor.RED));
            return true;
        }

        int currentHomeCount = databaseManager.getHomeCount(player.getUniqueId());
        int homeLimit = databaseManager.getHomeLimit(player.getUniqueId());
        boolean isUpdating = databaseManager.getHomes(player.getUniqueId()).contains(homeName.toLowerCase());

        if (!isUpdating && currentHomeCount >= homeLimit) {
            player.sendMessage(Component.text("You have reached your home limit of " + homeLimit + ".").color(NamedTextColor.RED));
            return true;
        }

        Location location = player.getLocation();
        if (databaseManager.setHome(player.getUniqueId(), homeName, location)) {
            player.sendMessage(Component.text("Home set.").color(NamedTextColor.GREEN));
        } else {
            player.sendMessage(Component.text("Failed to set home '" + homeName + "'.").color(NamedTextColor.RED));
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        return Collections.emptyList();
    }
}
