package com.timetracker.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerTimeData {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final String DATA_FILE = "config/playertimedata.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int DEFAULT_DAILY_LIMIT_MINUTES = ConfigManager.getConfig().defaultDailyLimitMinutes;

    // Maps player UUID to their data
    private Map<UUID, PlayerData> playerData = new ConcurrentHashMap<>();
    
    // Maps player UUID to their login time
    private Map<UUID, Long> loginTimes = new ConcurrentHashMap<>();

    // Add these fields to the class
    private Map<UUID, Long> lastActivityTimes = new ConcurrentHashMap<>();
    private Map<UUID, Boolean> afkStatus = new ConcurrentHashMap<>();
    private static final int AFK_THRESHOLD_SECONDS = ConfigManager.getConfig().afkTimeoutSeconds;

    public PlayerTimeData() {
    }

    public void loadData() {
        File file = new File(DATA_FILE);
        if (file.exists()) {
            try (FileReader reader = new FileReader(file)) {
                Type type = new TypeToken<Map<UUID, PlayerData>>(){}.getType();
                Map<UUID, PlayerData> loadedData = GSON.fromJson(reader, type);
                if (loadedData != null) {
                    playerData = new ConcurrentHashMap<>(loadedData);
                    LOGGER.info("Loaded time data for {} players", playerData.size());
                }
            } catch (IOException e) {
                LOGGER.error("Failed to load player time data", e);
            }
        } else {
            LOGGER.info("No player time data file found, creating new one");
            saveData();
        }
    }

    public void saveData() {
        File file = new File(DATA_FILE);
        try {
            if (!file.exists()) {
                file.getParentFile().mkdirs();
                file.createNewFile();
            }
            
            try (FileWriter writer = new FileWriter(file)) {
                GSON.toJson(playerData, writer);
            }
        } catch (IOException e) {
            LOGGER.error("Failed to save player time data", e);
        }
    }

    public void playerLogin(UUID playerUUID, String playerName) {
        // Get or create player data
        PlayerData data = playerData.computeIfAbsent(playerUUID, uuid -> new PlayerData(playerName));
        
        // Update last seen date
        data.lastSeen = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        
        // Record login time
        loginTimes.put(playerUUID, System.currentTimeMillis());
        
        // Check if this is a new day
        LocalDate currentDate = LocalDate.now();
        if (!currentDate.format(DateTimeFormatter.ISO_LOCAL_DATE).equals(data.lastPlayDate)) {
            // Reset daily time if it's a new day
            data.dailyTime = 0;
            data.lastPlayDate = currentDate.format(DateTimeFormatter.ISO_LOCAL_DATE);
        }
        
        saveData();
    }

    public void playerLogout(UUID playerUUID) {
        if (!loginTimes.containsKey(playerUUID)) {
            return;
        }
        
        long loginTime = loginTimes.get(playerUUID);
        long sessionTime = System.currentTimeMillis() - loginTime;
        
        // Convert to minutes
        long sessionMinutes = sessionTime / (1000 * 60);
        
        PlayerData data = playerData.get(playerUUID);
        if (data != null) {
            // Update total playtime
            data.totalPlaytime += sessionMinutes;
            
            // Update daily playtime
            data.dailyTime += sessionMinutes;
            
            saveData();
        }
        
        // Clean up login tracking
        loginTimes.remove(playerUUID);
    }

    public int getRemainingTime(UUID playerUUID) {
        PlayerData data = playerData.get(playerUUID);
        if (data == null) {
            return DEFAULT_DAILY_LIMIT_MINUTES;
        }
        
        // Check if it's an unlimited day
        if (isUnlimitedDay()) {
            return Integer.MAX_VALUE;
        }
        
        // Include bonus time in the calculation
        int totalAllowedTime = DEFAULT_DAILY_LIMIT_MINUTES + data.bonusTime;
        int remaining = totalAllowedTime - data.dailyTime;
        return Math.max(0, remaining);
    }

    public long getTotalPlaytime(UUID playerUUID) {
        PlayerData data = playerData.get(playerUUID);
        return data != null ? data.totalPlaytime : 0;
    }

    public void addTime(UUID playerUUID, int minutes) {
        PlayerData data = playerData.get(playerUUID);
        if (data != null) {
            // Add bonus time instead of reducing daily time
            data.bonusTime += minutes;
            saveData();
        }
    }
    
    public void removeTime(UUID playerUUID, int minutes) {
        PlayerData data = playerData.get(playerUUID);
        if (data != null) {
            // Add to daily time used (effectively reducing remaining time)
            data.dailyTime += minutes;
            saveData();
        }
    }
    
    private boolean isUnlimitedDay() {
        // Check config setting first
        if (!ConfigManager.getConfig().enableSaturdayUnlimited) {
            return false;
        }
        return LocalDate.now().getDayOfWeek() == DayOfWeek.SATURDAY;
    }

    public int getPlayerBonusTime(UUID playerUUID) {
        PlayerData data = playerData.get(playerUUID);
        return data != null ? data.bonusTime : 0;
    }

    public boolean hasLoginTime(UUID playerUUID) {
        return loginTimes.containsKey(playerUUID);
    }

    public void recordPlayerActivity(UUID playerUUID) {
        lastActivityTimes.put(playerUUID, System.currentTimeMillis());
        
        // If player was AFK, they're now active
        if (afkStatus.getOrDefault(playerUUID, false)) {
            afkStatus.put(playerUUID, false);
            // Log player returning from AFK status
            LOGGER.info("Player {} is no longer AFK", playerUUID);
        }
    }

    public boolean isPlayerAFK(UUID playerUUID) {
        // Skip if AFK detection is disabled
        if (!ConfigManager.getConfig().enableAfkDetection) {
            return false;
        }
        
        if (!lastActivityTimes.containsKey(playerUUID)) {
            return false;
        }
        
        long lastActivity = lastActivityTimes.get(playerUUID);
        long currentTime = System.currentTimeMillis();
        long inactiveTime = (currentTime - lastActivity) / 1000; // in seconds
        
        boolean isAfk = inactiveTime >= AFK_THRESHOLD_SECONDS;
        
        // If player just became AFK, log it
        if (isAfk && !afkStatus.getOrDefault(playerUUID, false)) {
            afkStatus.put(playerUUID, true);
            LOGGER.info("Player {} is now AFK", playerUUID);
        }
        
        return isAfk;
    }

    public void updatePlayerTime(UUID playerUUID) {
        if (!loginTimes.containsKey(playerUUID)) {
            return;
        }
        
        // Skip if player is AFK
        if (isPlayerAFK(playerUUID)) {
            return;
        }
        
        long loginTime = loginTimes.get(playerUUID);
        long currentTime = System.currentTimeMillis();
        long sessionTimeMillis = currentTime - loginTime;
        
        // Calculate exact minutes (whole minutes, no fractions)
        int sessionMinutes = (int)(sessionTimeMillis / (1000 * 60));
        
        // Only update if at least one minute has passed
        if (sessionMinutes > 0) {
            PlayerData data = playerData.get(playerUUID);
            if (data != null) {
                // Update total playtime
                data.totalPlaytime = data.totalPlaytime + sessionMinutes;
                
                // Update daily playtime
                data.dailyTime = data.dailyTime + sessionMinutes;
                
                // Save the data
                saveData();
                
                // Update the login time to account for the minutes we've added
                // This keeps any "partial" minute for the next update
                loginTimes.put(playerUUID, loginTime + (sessionMinutes * 60 * 1000));
            }
        }
    }

    public boolean getAfkStatus(UUID playerUUID) {
        return afkStatus.getOrDefault(playerUUID, false);
    }

    public static class PlayerData {
        public String playerName;
        public long totalPlaytime = 0; // in minutes
        public int dailyTime = 0; // in minutes
        public int bonusTime = 0; // in minutes for storing additional time
        public String lastPlayDate = "";
        public String lastSeen = "";

        public PlayerData(String playerName) {
            this.playerName = playerName;
            this.lastPlayDate = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
            this.lastSeen = this.lastPlayDate;
        }
    }
}