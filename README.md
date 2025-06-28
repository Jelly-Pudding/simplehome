# SimpleHome Plugin
**SimpleHome** is a Minecraft Paper 1.21.6 plugin that allows players to set and teleport to multiple home locations using simple commands.

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

## Migrating from Old YML Format (Manual Script)

If you previously used a version of SimpleHome (before v1.0.2) that stored a single home per player in individual YML files (`plugins/SimpleHome/homes/<UUID>.yml`), you need to manually migrate this data to the new SQLite database used by version 1.0.2 and later.

**Prerequisites:**

1.  **New Plugin Version:** Ensure the new SimpleHome version (1.0.2+) is installed and has run at least once to create the empty `plugins/SimpleHome/homes.db` file.
2.  **`sqlite3` Tool:** The migration script requires the `sqlite3` command-line tool to be installed on your server's operating system.
    *   Debian/Ubuntu: `sudo apt-get update && sudo apt-get install sqlite3`
    *   CentOS/RHEL: `sudo yum install sqlite`
    *   Check your OS documentation for specific instructions.

**How to Use:**

1.  **Stop your Minecraft server.**
2.  Copy and paste the script found below into a file called `migrate_simplehome.sh` in your main server directory (the one containing your server `.jar` file, `plugins` folder etc.).
3.  Make the script executable: `chmod +x migrate_simplehome.sh`
4.  Run the script: `./migrate_simplehome.sh`
5.  Review the output for any errors or skipped files.
6.  **Start your Minecraft server.**
7.  Ask players (or test yourself) to use the `/home` command to verify their original home location was migrated correctly.

```bash
#!/bin/bash

# SimpleHome YML to SQLite Migration Script

# --- Configuration ---
# Adjust these paths if your server layout is different
SERVER_ROOT_DIR=$(pwd) # Assumes you run from the server root directory
PLUGIN_DIR="$SERVER_ROOT_DIR/plugins/SimpleHome"
OLD_HOMES_DIR="$PLUGIN_DIR/homes"
DATABASE_FILE="$PLUGIN_DIR/homes.db"
DEFAULT_HOME_NAME="home" # The name assigned to the migrated home. You won't want to change this.
# --- End Configuration ---

echo "=== SimpleHome YML to SQLite Migration ==="

# Function to print error messages
error_exit() {
  echo "ERROR: $1" >&2
  exit 1
}

# --- Sanity Checks ---
echo "Checking prerequisites..."
if [ ! -d "$PLUGIN_DIR" ]; then
  error_exit "Plugin directory not found: $PLUGIN_DIR"
fi
if [ ! -d "$OLD_HOMES_DIR" ]; then
  echo "INFO: Old homes directory not found: $OLD_HOMES_DIR"
  echo "INFO: No migration needed or possible."
  exit 0
fi
if ! command -v sqlite3 &> /dev/null; then
  error_exit "sqlite3 command not found. Please install sqlite3 ('sudo apt install sqlite3' or 'sudo yum install sqlite')."
fi
if [ ! -f "$DATABASE_FILE" ]; then
  error_exit "Database file not found: $DATABASE_FILE. Ensure SimpleHome v1.0.2+ has run once."
fi
echo "Prerequisites met."

# --- Migration Process ---
echo "Starting migration from $OLD_HOMES_DIR to $DATABASE_FILE..."
shopt -s nullglob # Prevent loop from running if no files match
shopt -s extglob # Enable extended globbing for case-insensitive matching

migrated_count=0
skipped_count=0

# Find all .yml files, case-insensitive
cd "$OLD_HOMES_DIR" || error_exit "Could not change directory to $OLD_HOMES_DIR"
for yaml_file in *.@(yml|YML); do
  filename=$(basename "$yaml_file")
  uuid="${filename%.*}" # Remove .yml extension

  # Basic UUID validation
  if [[ ! "$uuid" =~ ^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$ ]]; then
    echo "SKIP: Invalid filename (not a UUID): $filename"
    ((skipped_count++))
    continue
  fi

  echo "Processing $filename (UUID: $uuid)..."

  world=$(grep -i '^[[:space:]]*world:' "$yaml_file" | awk -F': *' '{print $2}' | tr -d '\r')
  x=$(grep -i '^[[:space:]]*x:' "$yaml_file" | awk -F': *' '{print $2}' | tr -d '\r')
  y=$(grep -i '^[[:space:]]*y:' "$yaml_file" | awk -F': *' '{print $2}' | tr -d '\r')
  z=$(grep -i '^[[:space:]]*z:' "$yaml_file" | awk -F': *' '{print $2}' | tr -d '\r')
  yaw=$(grep -i '^[[:space:]]*yaw:' "$yaml_file" | awk -F': *' '{print $2}' | tr -d '\r')
  pitch=$(grep -i '^[[:space:]]*pitch:' "$yaml_file" | awk -F': *' '{print $2}' | tr -d '\r')

  if [ -z "$world" ] || [ -z "$x" ] || [ -z "$y" ] || [ -z "$z" ] || [ -z "$yaw" ] || [ -z "$pitch" ]; then
    echo "SKIP: Could not parse all required fields from $filename."
    ((skipped_count++))
    continue
  fi

  # Construct and execute SQLite INSERT statement using REPLACE...
  # Ensures existing 'home' entry for a UUID would be overwritten if script is run multiple times
  sql_command="REPLACE INTO player_homes (uuid, home_name, world, x, y, z, yaw, pitch) VALUES ('$uuid', '$DEFAULT_HOME_NAME', '$world', $x, $y, $z, $yaw, $pitch);"

  # Execute using absolute path to DB file
  sqlite3 "$DATABASE_FILE" "$sql_command"
  if [ $? -eq 0 ]; then
    echo "  OK: Migrated home '$DEFAULT_HOME_NAME' for $uuid."
    ((migrated_count++))
  else
    echo "  ERROR: Failed to execute SQLite command for $uuid. Check database permissions and integrity."
    ((skipped_count++))
  fi

done
cd "$SERVER_ROOT_DIR" || echo "Warning: Could not change back to server root directory."

# --- Summary ---
echo "=========================================="
echo "Migration Summary:"
echo "  Successfully migrated: $migrated_count homes."
echo "  Skipped/Errors: $skipped_count files."
if [ $skipped_count -gt 0 ]; then
  echo "  Check the output above for details on skipped files or errors."
fi
echo "=========================================="

# Clean up shell options
shopt -u nullglob
shopt -u extglob

exit 0
```

## Support Me
[![ko-fi](https://ko-fi.com/img/githubbutton_sm.svg)](https://ko-fi.com/K3K715TC1R)
