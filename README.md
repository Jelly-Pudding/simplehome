# SimpleHome Plugin
**SimpleHome** is a Minecraft Paper 1.21.1 plugin that allows players to set and teleport to their home location using simple commands.

## Installation
1. Download the latest release [here](https://github.com/Jelly-Pudding/simplehome/releases/latest).
2. Place the `.jar` file in your Minecraft server's `plugins` folder.
3. Restart your server.

## Features
- Each player's home location is stored in a separate file for optimal performance
- Home locations persist between server restarts
- Includes world, coordinates, and player rotation
- Simple commands for setting and teleporting to homes

## Commands
- `/sethome`: Sets your home location to your current position
- `/home`: Teleports you to your saved home location

## Permissions
- `simplehome.use`: Allows use of both /home and /sethome commands

## Data Storage
Each player's home is stored in its own file at `plugins/SimpleHome/homes/{uuid}.yml` containing:
```yaml
world: world_name
x: 0.0
y: 64.0
z: 0.0
yaw: 0.0
pitch: 0.0
```

## Support Me
Donations to my [Patreon](https://www.patreon.com/lolwhatyesme) will help with the development of this project.
