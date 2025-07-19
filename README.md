# SimpleHome Plugin
**SimpleHome** is a Minecraft Paper 1.21.7 plugin that allows players to set and teleport to multiple home locations using simple commands.

## Installation
1. Download the latest release [here](https://github.com/Jelly-Pudding/simplehome/releases/latest).
2. Place the `.jar` file in your Minecraft server's `plugins` folder.
3. Restart your server.

## Features
* Set multiple named home locations (default name is "home").
* Teleport to your saved homes.
* Delete specific homes.
* List your currently set homes.
* Stores home locations efficiently in an SQLite database.
* Players start with a limit of 1 home.

## Commands

### Player Commands
* `/sethome [name]` - Sets a home at your current location. Uses the name "home" if no name is provided.
* `/home [name]` - Teleports you to the specified home. Uses the name "home" if no name is provided.
* `/delhome <name>` - Deletes the home with the specified name.
* `/homes` - Lists all the homes you have currently set (clickable to use).

### Admin Commands
* `/homeadmin increase <player>` - Increases the specified player's home limit by 1.
* `/homeadmin decrease <player>` - Decreases the specified player's home limit by 1.
* `/homeadmin get <player>` - Shows the specified player's current home count, limit, and home names.
* `/homeadmin visit <player> <home_name>` - Teleports you to the specified player's home.

## Permissions
* `simplehome.use` - Allows `Player Commands` (`/sethome`, `/home`, `/delhome`, and `/homes`) - (Default: true)
* `simplehome.admin` - Allows `Admin Commands` (`/homeadmin increase`, `/homeadmin decrease`, `/homeadmin get`, `/homeadmin visit`) - (Default: op)

## API for Developers

### Setup Dependencies
1. Download the latest `SimpleHome.jar` and place it in a `libs` directory - and then add this to your `build.gradle` file:
    ```gradle
    dependencies {
        compileOnly files('libs/SimpleHome-1.0.4.jar')
    }
    ```

2. If SimpleHome is absolutely required by your plugin, then add this to your `plugin.yml` file - and this means if SimpleHome is not found then your plugin will not load:
    ```yaml
    depend: [SimpleHome]
    ```

### Getting SimpleHome Instance
You can import SimpleHome into your project through using the below code:
```java
import org.bukkit.Bukkit;
import com.jellypudding.simpleHome.SimpleHome;

Plugin simpleHomePlugin = Bukkit.getPluginManager().getPlugin("SimpleHome");
if (simpleHomePlugin instanceof SimpleHome && simpleHomePlugin.isEnabled()) {
    SimpleHome simpleHome = (SimpleHome) simpleHomePlugin;
}
```

### Available API Methods
```java
// Get player's current home limit
int currentLimit = simpleHome.getHomeLimit(playerUUID);

// Get player's current number of homes set
int currentHomes = simpleHome.getCurrentHomeCount(playerUUID);

// Increase player's home limit by 1 (returns success boolean)
boolean success = simpleHome.increaseHomeLimit(playerUUID);
```

## Support Me
[![ko-fi](https://ko-fi.com/img/githubbutton_sm.svg)](https://ko-fi.com/K3K715TC1R)
