package com.jellypudding.simpleHome.commands;

import com.jellypudding.simpleHome.database.DatabaseManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public class HomesCommand implements CommandExecutor, TabCompleter {

    private final DatabaseManager databaseManager;

    public HomesCommand(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("This command can only be used by players.").color(NamedTextColor.RED));
            return true;
        }

        databaseManager.getHomesAsync(player.getUniqueId())
                .thenCombine(databaseManager.getHomeLimitAsync(player.getUniqueId()), (homeNames, homeLimit) -> {
            if (homeNames.isEmpty()) {
                return Component.text("You have no homes set. Use /sethome [name]").color(NamedTextColor.YELLOW);
            }

            TextComponent homesText = Component.text("Your homes (" + homeNames.size() + "/" + homeLimit + "): ").color(NamedTextColor.GOLD);
            int size = homeNames.size();
            for (int i = 0; i < size; i++) {
                String name = homeNames.get(i);
                TextComponent homeNameComponent = Component.text(name)
                        .clickEvent(ClickEvent.suggestCommand("/home " + name))
                        .hoverEvent(HoverEvent.showText(Component.text("Click to use command.")))
                        .decoration(TextDecoration.UNDERLINED, true)
                        .color(NamedTextColor.WHITE);
                homesText = homesText.append(homeNameComponent);
                if (i < size - 1) {
                    homesText = homesText.append(Component.text(", ").color(NamedTextColor.WHITE));
                }
            }
            return homesText;
        }).thenAccept(player::sendMessage);
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        return Collections.emptyList();
    }
}
