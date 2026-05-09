package com.jellypudding.simpleHome.util;

import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.entity.Player;

import java.util.UUID;

public final class PlayerUtil {

    private PlayerUtil() {}

    public static boolean isValidHomeName(String name) {
        return name != null && !name.isEmpty() && name.matches("^[a-zA-Z0-9_-]+$") && name.length() <= 30;
    }

    public static UUID getPlayerUUID(Server server, String playerName) {
        Player onlinePlayer = server.getPlayer(playerName);
        if (onlinePlayer != null) {
            return onlinePlayer.getUniqueId();
        }

        OfflinePlayer offlinePlayer = server.getOfflinePlayer(playerName);
        if (offlinePlayer.hasPlayedBefore()) {
            return offlinePlayer.getUniqueId();
        }

        return null;
    }
}
