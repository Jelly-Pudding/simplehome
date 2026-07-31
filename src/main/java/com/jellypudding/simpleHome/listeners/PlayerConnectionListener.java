package com.jellypudding.simpleHome.listeners;

import com.jellypudding.simpleHome.database.DatabaseManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerConnectionListener implements Listener {

    private final DatabaseManager databaseManager;

    public PlayerConnectionListener(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        databaseManager.cachePlayer(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        databaseManager.uncachePlayer(event.getPlayer().getUniqueId());
    }
}
