package com.jellypudding.simpleHome.commands;

import com.jellypudding.simpleHome.database.DatabaseManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class DelHomeCommand implements CommandExecutor, TabCompleter {

    private final DatabaseManager databaseManager;

    public DelHomeCommand(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("This command can only be used by players.").color(NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            player.sendMessage(Component.text("Usage: /delhome <name>").color(NamedTextColor.RED));
            return true;
        }

        String homeName = args[0];
        databaseManager.deleteHomeAsync(player.getUniqueId(), homeName).thenAccept(deleted -> {
            if (deleted) {
                player.sendMessage(Component.text("Home '" + homeName + "' deleted.").color(NamedTextColor.GREEN));
            } else {
                player.sendMessage(Component.text("Home '" + homeName + "' not found.").color(NamedTextColor.RED));
            }
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
