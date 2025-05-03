# SimpleHome Plugin
**SimpleHome** is a Minecraft Paper 1.21.4 plugin that allows players to set and teleport to multiple home locations using simple commands.

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
* `/sethome [name]` - Sets a home at your current location. Uses the name "home" if no name is provided.
* `/home [name]` - Teleports you to the specified home. Uses the name "home" if no name is provided.
* `/delhome <name>` - Deletes the home with the specified name.
* `/homes` - Lists all the homes you have currently set.

## Permissions
* `simplehome.use` - Allows players to use `/sethome`, `/home`, `/delhome`, and `/homes`. (Default: true)

## Support Me
[![ko-fi](https://ko-fi.com/img/githubbutton_sm.svg)](https://ko-fi.com/K3K715TC1R)
