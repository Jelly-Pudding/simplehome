package com.jellypudding.simpleHome;

import com.jellypudding.simpleHome.commands.*;
import com.jellypudding.simpleHome.database.DatabaseManager;
import com.jellypudding.simpleHome.listeners.PlayerConnectionListener;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;
import java.util.UUID;

public final class SimpleHome extends JavaPlugin {

    private DatabaseManager databaseManager;
    private static final String DEFAULT_HOME_NAME = "home";
    private int maxHomeLimit = 10;
    private boolean allowCrossDimensionTeleport = false;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadConfigValues();

        databaseManager = new DatabaseManager(this, maxHomeLimit);

        registerCommands();
        getServer().getPluginManager().registerEvents(new PlayerConnectionListener(databaseManager), this);

        for (Player player : getServer().getOnlinePlayers()) {
            databaseManager.cachePlayer(player.getUniqueId());
        }

        int pluginId = 27541;
        new Metrics(this, pluginId);

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
        this.allowCrossDimensionTeleport = config.getBoolean("allow-cross-dimension-teleport", false);
    }

    private void registerCommands() {
        SetHomeCommand setHomeCmd = new SetHomeCommand(this, databaseManager, DEFAULT_HOME_NAME);
        HomeCommand homeCmd = new HomeCommand(this, databaseManager, DEFAULT_HOME_NAME, allowCrossDimensionTeleport);
        DelHomeCommand delHomeCmd = new DelHomeCommand(databaseManager);
        HomesCommand homesCmd = new HomesCommand(databaseManager);
        HomeAdminCommand homeAdminCmd = new HomeAdminCommand(this, databaseManager, maxHomeLimit, getServer());

        Objects.requireNonNull(getCommand("sethome")).setExecutor(setHomeCmd);
        Objects.requireNonNull(getCommand("sethome")).setTabCompleter(setHomeCmd);
        Objects.requireNonNull(getCommand("home")).setExecutor(homeCmd);
        Objects.requireNonNull(getCommand("home")).setTabCompleter(homeCmd);
        Objects.requireNonNull(getCommand("delhome")).setExecutor(delHomeCmd);
        Objects.requireNonNull(getCommand("delhome")).setTabCompleter(delHomeCmd);
        Objects.requireNonNull(getCommand("homes")).setExecutor(homesCmd);
        Objects.requireNonNull(getCommand("homes")).setTabCompleter(homesCmd);
        Objects.requireNonNull(getCommand("homeadmin")).setExecutor(homeAdminCmd);
        Objects.requireNonNull(getCommand("homeadmin")).setTabCompleter(homeAdminCmd);
    }

    // --- API Methods for external plugin integrations ---

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
        return databaseManager.increaseHomeLimit(playerUUID);
    }

    public int getCurrentHomeCount(UUID playerUUID) {
        if (databaseManager == null) {
            getLogger().warning("Attempted to get home count, but DatabaseManager is null.");
            return 0;
        }
        return databaseManager.getHomeCount(playerUUID);
    }
}
