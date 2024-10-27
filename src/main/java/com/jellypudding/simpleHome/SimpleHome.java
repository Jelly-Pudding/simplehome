package com.jellypudding.simpleHome;

import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class SimpleHome extends JavaPlugin implements Listener, TabCompleter {

    private File homesDirectory;

    @Override
    public void onEnable() {
        // Create homes directory if it doesn't exist
        homesDirectory = new File(getDataFolder(), "homes");
        if (!homesDirectory.exists()) {
            homesDirectory.mkdirs();
        }

        // Register events and tab completers
        getServer().getPluginManager().registerEvents(this, this);
        Objects.requireNonNull(getCommand("home")).setTabCompleter(this);
        Objects.requireNonNull(getCommand("sethome")).setTabCompleter(this);

        getLogger().info("SimpleHome has been enabled!");
    }

    @Override
    public void onDisable() {
        getLogger().info("SimpleHome has been disabled!");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players!");
            return true;
        }

        if (command.getName().equalsIgnoreCase("sethome")) {
            setHome(player);
            return true;
        } else if (command.getName().equalsIgnoreCase("home")) {
            teleportHome(player);
            return true;
        }

        return false;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
        // No arguments for these commands
        return new ArrayList<>();
    }

    private File getPlayerHomeFile(UUID uuid) {
        return new File(homesDirectory, uuid + ".yml");
    }

    private void setHome(Player player) {
        Location location = player.getLocation();
        File playerFile = getPlayerHomeFile(player.getUniqueId());
        YamlConfiguration config = YamlConfiguration.loadConfiguration(playerFile);

        config.set("world", location.getWorld().getName());
        config.set("x", location.getX());
        config.set("y", location.getY());
        config.set("z", location.getZ());
        config.set("yaw", location.getYaw());
        config.set("pitch", location.getPitch());

        try {
            config.save(playerFile);
            player.sendMessage("Home location set!");
        } catch (IOException e) {
            player.sendMessage("Could not save home location!");
            getLogger().warning("Could not save home for " + player.getName() + ": " + e.getMessage());
        }
    }

    private void teleportHome(Player player) {
        File playerFile = getPlayerHomeFile(player.getUniqueId());
        if (!playerFile.exists()) {
            player.sendMessage("You haven't set a home yet! Use /sethome first.");
            return;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(playerFile);
        String worldName = config.getString("world");

        if (worldName == null || getServer().getWorld(worldName) == null) {
            player.sendMessage("Could not find your home world!");
            return;
        }

        Location location = new Location(
                getServer().getWorld(worldName),
                config.getDouble("x"),
                config.getDouble("y"),
                config.getDouble("z"),
                (float) config.getDouble("yaw"),
                (float) config.getDouble("pitch")
        );

        player.teleport(location);
        player.sendMessage("You teleported to your home.");
    }
}