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
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerTimeData {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final String DATA_FILE = "config/playertimedata.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int DEFAULT_DAILY_LIMIT_MINUTES = 120; // 2 hours default

    // Maps player UUID to their data
    private Map<UUID, PlayerData> playerData = new ConcurrentHashMap<>();
    
    // Maps player UUID to their login time
    private Map<UUID, Long> loginTimes = new ConcurrentHashMap<>();

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
        
        int remaining = DEFAULT_DAILY_LIMIT_MINUTES - data.dailyTime;
        return Math.max(0, remaining);
    }

    public static class PlayerData {
        public String playerName;
        public long totalPlaytime = 0; // in minutes
        public int dailyTime = 0; // in minutes
        public String lastPlayDate = "";
        public String lastSeen = "";

        public PlayerData(String playerName) {
            this.playerName = playerName;
            this.lastPlayDate = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
            this.lastSeen = this.lastPlayDate;
        }
    }
}
