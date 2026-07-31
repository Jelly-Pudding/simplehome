package com.jellypudding.simpleHome.database;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.logging.Level;

public class DatabaseManager {

    private final JavaPlugin plugin;
    private Connection connection;
    private final String databasePath;
    private int maxHomeLimit;

    private final ExecutorService dbExecutor;
    private volatile Thread dbThread;

    private final Map<UUID, List<String>> homeNameCache = new ConcurrentHashMap<>();

    public DatabaseManager(JavaPlugin plugin, int maxHomeLimit) {
        this.plugin = plugin;
        this.maxHomeLimit = maxHomeLimit;
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        this.databasePath = "jdbc:sqlite:" + new File(plugin.getDataFolder(), "homes.db").getAbsolutePath();
        this.dbExecutor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "SimpleHome-Database");
            thread.setDaemon(true);
            dbThread = thread;
            return thread;
        });
        CompletableFuture.runAsync(() -> {
            connect();
            initialiseDatabase(this.maxHomeLimit);
        }, dbExecutor).join();
    }

    private void connect() {
        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection(databasePath);
            plugin.getLogger().info("Successfully connected to SQLite database.");
        } catch (SQLException | ClassNotFoundException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not connect to SQLite database: " + e.getMessage(), e);
            connection = null;
        }
    }

    private void initialiseDatabase(int limit) {
        if (connection == null) return;

        String sqlHomes = "CREATE TABLE IF NOT EXISTS player_homes (" +
                          " uuid TEXT NOT NULL," +
                          " home_name TEXT NOT NULL COLLATE NOCASE," +
                          " world TEXT NOT NULL," +
                          " x REAL NOT NULL," +
                          " y REAL NOT NULL," +
                          " z REAL NOT NULL," +
                          " yaw REAL NOT NULL," +
                          " pitch REAL NOT NULL," +
                          " PRIMARY KEY (uuid, home_name)" +
                          ");";

        String sqlLimits = "CREATE TABLE IF NOT EXISTS player_home_limits (" +
                           " uuid TEXT PRIMARY KEY NOT NULL," +
                           " max_homes INTEGER NOT NULL DEFAULT 1 CHECK(max_homes >= 1 AND max_homes <= " + limit + ")" +
                           ");";

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(sqlHomes);
            plugin.getLogger().info("Database table 'player_homes' initialised.");
            stmt.execute(sqlLimits);
            plugin.getLogger().info("Database table 'player_home_limits' initialised.");

            migrateHomeLimitsSchema(limit);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not create database tables: " + e.getMessage(), e);
        }
    }

    private void migrateHomeLimitsSchema(int newLimit) {
        if (connection == null) return;

        try {
            String testSql = "INSERT OR IGNORE INTO player_home_limits (uuid, max_homes) VALUES (?, ?)";
            String testUuid = "test-constraint-check-uuid";

            try (PreparedStatement testStmt = connection.prepareStatement(testSql)) {
                testStmt.setString(1, testUuid);
                testStmt.setInt(2, newLimit);
                testStmt.executeUpdate();

                String checkSql = "SELECT max_homes FROM player_home_limits WHERE uuid = ?";
                try (PreparedStatement checkStmt = connection.prepareStatement(checkSql)) {
                    checkStmt.setString(1, testUuid);
                    ResultSet rs = checkStmt.executeQuery();

                    if (rs.next() && rs.getInt("max_homes") == newLimit) {
                        String deleteSql = "DELETE FROM player_home_limits WHERE uuid = ?";
                        try (PreparedStatement deleteStmt = connection.prepareStatement(deleteSql)) {
                            deleteStmt.setString(1, testUuid);
                            deleteStmt.executeUpdate();
                        }
                        plugin.getLogger().info("Database schema is compatible with max home limit: " + newLimit);
                        return;
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().info("Database constraint needs updating for new max home limit: " + newLimit);
        }

        try (Statement stmt = connection.createStatement()) {
            plugin.getLogger().info("Migrating player_home_limits table for new max limit: " + newLimit);

            String createTempSql = "CREATE TABLE player_home_limits_temp (" +
                                   " uuid TEXT PRIMARY KEY NOT NULL," +
                                   " max_homes INTEGER NOT NULL DEFAULT 1 CHECK(max_homes >= 1 AND max_homes <= " + newLimit + ")" +
                                   ");";
            stmt.execute(createTempSql);

            String copySql = "INSERT INTO player_home_limits_temp (uuid, max_homes) " +
                             "SELECT uuid, MIN(max_homes, " + newLimit + ") FROM player_home_limits";
            stmt.execute(copySql);

            stmt.execute("DROP TABLE player_home_limits");
            stmt.execute("ALTER TABLE player_home_limits_temp RENAME TO player_home_limits");

            plugin.getLogger().info("Successfully migrated player_home_limits table to support max limit: " + newLimit);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to migrate database schema: " + e.getMessage(), e);
        }
    }

    public void closeConnection() {
        dbExecutor.shutdown();
        try {
            if (!dbExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                plugin.getLogger().warning("Database tasks did not finish within 5 seconds; closing anyway.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                plugin.getLogger().info("Database connection closed.");
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not close database connection: " + e.getMessage(), e);
        }
    }

    private <T> CompletableFuture<T> supplyAsync(Supplier<T> task) {
        return CompletableFuture.supplyAsync(task, dbExecutor);
    }

    private <T> T runSync(Supplier<T> task) {
        if (Thread.currentThread() == dbThread) {
            return task.get();
        }
        return supplyAsync(task).join();
    }

    public void cachePlayer(UUID uuid) {
        getHomesAsync(uuid).thenAccept(homes -> homeNameCache.put(uuid, List.copyOf(homes)));
    }

    public void uncachePlayer(UUID uuid) {
        homeNameCache.remove(uuid);
    }

    public List<String> getCachedHomes(UUID uuid) {
        List<String> homes = homeNameCache.get(uuid);
        return homes != null ? homes : Collections.emptyList();
    }

    public int getHomeLimit(UUID uuid) {
        return runSync(() -> fetchHomeLimit(uuid));
    }

    public CompletableFuture<Integer> getHomeLimitAsync(UUID uuid) {
        return supplyAsync(() -> fetchHomeLimit(uuid));
    }

    public boolean increaseHomeLimit(UUID uuid) {
        return runSync(() -> applyHomeLimitIncrease(uuid));
    }

    public boolean setHomeLimit(UUID uuid, int limit) {
        return runSync(() -> storeHomeLimit(uuid, limit));
    }

    public CompletableFuture<Boolean> setHomeLimitAsync(UUID uuid, int limit) {
        return supplyAsync(() -> storeHomeLimit(uuid, limit));
    }

    public int getHomeCount(UUID uuid) {
        return runSync(() -> fetchHomeCount(uuid));
    }

    public boolean setHome(UUID uuid, String homeName, Location location) {
        return runSync(() -> storeHome(uuid, homeName, location));
    }

    public CompletableFuture<Boolean> setHomeAsync(UUID uuid, String homeName, Location location) {
        return supplyAsync(() -> storeHome(uuid, homeName, location));
    }

    public Location getHome(UUID uuid, String homeName) {
        return runSync(() -> fetchHome(uuid, homeName));
    }

    public CompletableFuture<Location> getHomeAsync(UUID uuid, String homeName) {
        return supplyAsync(() -> fetchHome(uuid, homeName));
    }

    public List<String> getHomes(UUID uuid) {
        return runSync(() -> fetchHomes(uuid));
    }

    public CompletableFuture<List<String>> getHomesAsync(UUID uuid) {
        return supplyAsync(() -> fetchHomes(uuid));
    }

    public boolean deleteHome(UUID uuid, String homeName) {
        return runSync(() -> removeHome(uuid, homeName));
    }

    public CompletableFuture<Boolean> deleteHomeAsync(UUID uuid, String homeName) {
        return supplyAsync(() -> removeHome(uuid, homeName));
    }

    private int fetchHomeLimit(UUID uuid) {
        if (connection == null) return 1;
        String sql = "SELECT max_homes FROM player_home_limits WHERE uuid = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, uuid.toString());
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("max_homes");
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not retrieve home limit for " + uuid + ": " + e.getMessage(), e);
        }
        return 1;
    }

    private boolean applyHomeLimitIncrease(UUID uuid) {
        if (connection == null) return false;
        int currentLimit = fetchHomeLimit(uuid);
        if (currentLimit >= this.maxHomeLimit) {
            return false;
        }
        int newLimit = currentLimit + 1;

        String sql = "INSERT OR REPLACE INTO player_home_limits (uuid, max_homes) VALUES (?, ?)";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, uuid.toString());
            pstmt.setInt(2, newLimit);
            pstmt.executeUpdate();
            plugin.getLogger().info("Increased home limit for " + uuid + " to " + newLimit);
            return true;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not increase home limit for " + uuid + ": " + e.getMessage(), e);
            return false;
        }
    }

    private boolean storeHomeLimit(UUID uuid, int limit) {
        if (connection == null) return false;
        if (limit < 1 || limit > this.maxHomeLimit) {
            return false;
        }

        String sql = "INSERT OR REPLACE INTO player_home_limits (uuid, max_homes) VALUES (?, ?)";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, uuid.toString());
            pstmt.setInt(2, limit);
            pstmt.executeUpdate();
            plugin.getLogger().info("Set home limit for " + uuid + " to " + limit);
            return true;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not set home limit for " + uuid + ": " + e.getMessage(), e);
            return false;
        }
    }

    private int fetchHomeCount(UUID uuid) {
        if (connection == null) return 0;
        String sql = "SELECT COUNT(*) FROM player_homes WHERE uuid = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, uuid.toString());
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not retrieve home count for " + uuid + ": " + e.getMessage(), e);
        }
        return 0;
    }

    private boolean storeHome(UUID uuid, String homeName, Location location) {
        if (connection == null) return false;
        String sql = "REPLACE INTO player_homes (uuid, home_name, world, x, y, z, yaw, pitch) VALUES(?, ?, ?, ?, ?, ?, ?, ?)";

        String storedName = homeName.toLowerCase();
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, uuid.toString());
            pstmt.setString(2, storedName);
            pstmt.setString(3, location.getWorld().getName());
            pstmt.setDouble(4, location.getX());
            pstmt.setDouble(5, location.getY());
            pstmt.setDouble(6, location.getZ());
            pstmt.setFloat(7, location.getYaw());
            pstmt.setFloat(8, location.getPitch());
            pstmt.executeUpdate();
            homeNameCache.computeIfPresent(uuid, (key, homes) -> {
                if (homes.contains(storedName)) return homes;
                List<String> updated = new ArrayList<>(homes);
                updated.add(storedName);
                Collections.sort(updated);
                return List.copyOf(updated);
            });
            return true;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save home '" + homeName + "' for " + uuid + ": " + e.getMessage(), e);
            return false;
        }
    }

    private Location fetchHome(UUID uuid, String homeName) {
        if (connection == null) return null;
        String sql = "SELECT world, x, y, z, yaw, pitch FROM player_homes WHERE uuid = ? AND home_name = ?";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, uuid.toString());
            pstmt.setString(2, homeName.toLowerCase());
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                String worldName = rs.getString("world");
                World world = plugin.getServer().getWorld(worldName);
                if (world == null) {
                    plugin.getLogger().warning("World '" + worldName + "' not found for home '" + homeName + "' of " + uuid);
                    return null;
                }
                double x = rs.getDouble("x");
                double y = rs.getDouble("y");
                double z = rs.getDouble("z");
                float yaw = rs.getFloat("yaw");
                float pitch = rs.getFloat("pitch");
                return new Location(world, x, y, z, yaw, pitch);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not retrieve home '" + homeName + "' for " + uuid + ": " + e.getMessage(), e);
        }
        return null;
    }

    private List<String> fetchHomes(UUID uuid) {
        if (connection == null) return new ArrayList<>();
        List<String> homeNames = new ArrayList<>();
        String sql = "SELECT home_name FROM player_homes WHERE uuid = ? ORDER BY home_name";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, uuid.toString());
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                homeNames.add(rs.getString("home_name"));
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not retrieve home list for " + uuid + ": " + e.getMessage(), e);
        }
        return homeNames;
    }

    private boolean removeHome(UUID uuid, String homeName) {
        if (connection == null) return false;
        String sql = "DELETE FROM player_homes WHERE uuid = ? AND home_name = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, uuid.toString());
            pstmt.setString(2, homeName.toLowerCase());
            int affectedRows = pstmt.executeUpdate();
            if (affectedRows > 0) {
                homeNameCache.computeIfPresent(uuid, (key, homes) -> homes.stream()
                        .filter(name -> !name.equalsIgnoreCase(homeName))
                        .toList());
            }
            return affectedRows > 0;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not delete home '" + homeName + "' for " + uuid + ": " + e.getMessage(), e);
            return false;
        }
    }
}
