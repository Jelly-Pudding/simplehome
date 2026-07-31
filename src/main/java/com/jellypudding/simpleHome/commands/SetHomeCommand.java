package com.jellypudding.simpleHome.commands;

import com.jellypudding.simpleHome.database.DatabaseManager;
import com.jellypudding.simpleHome.util.PlayerUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class SetHomeCommand implements CommandExecutor, TabCompleter {

    private static final long OVERWRITE_CONFIRM_TIMEOUT_MILLIS = 30_000L;

    private final JavaPlugin plugin;
    private final DatabaseManager databaseManager;
    private final String defaultHomeName;

    private final Map<UUID, PendingOverwrite> pendingOverwrites = new HashMap<>();

    private record PendingOverwrite(String homeName, long expiresAt) {}

    private record HomesAndLimit(List<String> homes, int limit) {}

    public SetHomeCommand(JavaPlugin plugin, DatabaseManager databaseManager, String defaultHomeName) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        this.defaultHomeName = defaultHomeName;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("This command can only be used by players!").color(NamedTextColor.RED));
            return true;
        }

        boolean explicitName = args.length > 0;
        String homeName = explicitName ? args[0] : defaultHomeName;

        if (!PlayerUtil.isValidHomeName(homeName)) {
            player.sendMessage(Component.text("Invalid home name. Use letters, numbers, underscores, or hyphens.").color(NamedTextColor.RED));
            return true;
        }

        Location location = player.getLocation();
        databaseManager.getHomesAsync(player.getUniqueId())
                .thenCombine(databaseManager.getHomeLimitAsync(player.getUniqueId()), HomesAndLimit::new)
                .thenAccept(data -> plugin.getServer().getScheduler().runTask(plugin, () ->
                        finishSetHome(player, label, explicitName, homeName, location, data)));
        return true;
    }

    private void finishSetHome(Player player, String label, boolean explicitName, String homeName, Location location, HomesAndLimit data) {
        if (!player.isOnline()) {
            return;
        }

        boolean isUpdating = data.homes().contains(homeName.toLowerCase());

        if (!isUpdating && data.homes().size() >= data.limit()) {
            player.sendMessage(Component.text("You have reached your home limit of " + data.limit() + ".").color(NamedTextColor.RED));
            return;
        }

        if (isUpdating && !consumePendingOverwrite(player.getUniqueId(), homeName)) {
            pendingOverwrites.put(player.getUniqueId(),
                    new PendingOverwrite(homeName.toLowerCase(), System.currentTimeMillis() + OVERWRITE_CONFIRM_TIMEOUT_MILLIS));
            String confirmCommand = explicitName ? "/" + label + " " + homeName : "/" + label;
            player.sendMessage(Component.text("You already have a home named '" + homeName + "'. Setting it again will move it here.").color(NamedTextColor.YELLOW));
            player.sendMessage(Component.text("Run the command again within 30 seconds to confirm, or ").color(NamedTextColor.YELLOW)
                    .append(Component.text("[click here]").color(NamedTextColor.GREEN)
                            .clickEvent(ClickEvent.runCommand(confirmCommand))
                            .hoverEvent(HoverEvent.showText(Component.text("Overwrite home '" + homeName + "'"))))
                    .append(Component.text(".").color(NamedTextColor.YELLOW)));
            return;
        }

        databaseManager.setHomeAsync(player.getUniqueId(), homeName, location).thenAccept(success -> {
            if (success) {
                player.sendMessage(Component.text(isUpdating ? "Home '" + homeName + "' updated." : "Home set.").color(NamedTextColor.GREEN));
            } else {
                player.sendMessage(Component.text("Failed to set home '" + homeName + "'.").color(NamedTextColor.RED));
            }
        });
    }

    private boolean consumePendingOverwrite(UUID playerUUID, String homeName) {
        PendingOverwrite pending = pendingOverwrites.remove(playerUUID);
        return pending != null
                && pending.homeName().equalsIgnoreCase(homeName)
                && System.currentTimeMillis() <= pending.expiresAt();
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
