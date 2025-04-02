package com.cius.chronobreak.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PlaytimeData {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final String DATA_FILE = "config/playtimedata.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int DEFAULT_DAILY_LIMIT_MINUTES = 120; // 2 hours
    private static final int MAX_STREAK_BONUS = 20; // Maximum +20 minutes for streak

    // Maps player UUID to their data
    private Map<UUID, PlayerTimeData> playerData = new ConcurrentHashMap<>();
    
    // Maps player UUID to their last activity timestamp
    private Map<UUID, Long> lastActivity = new ConcurrentHashMap<>();
    
    // Maps player UUID to their login time
    private Map<UUID, Long> loginTimes = new ConcurrentHashMap<>();

    public PlaytimeData() {
    }

    public void loadData() {
        File file = new File(DATA_FILE);
        if (file.exists()) {
            try (FileReader reader = new FileReader(file)) {
                Type type = new TypeToken<Map<UUID, PlayerTimeData>>(){}.getType();
                Map<UUID, PlayerTimeData> loadedData = GSON.fromJson(reader, type);
                if (loadedData != null) {
                    playerData = new ConcurrentHashMap<>(loadedData);
                    LOGGER.info("Loaded playtime data for {} players", playerData.size());
                }
            } catch (IOException e) {
                LOGGER.error("Failed to load playtime data", e);
            }
        } else {
            LOGGER.info("No playtime data file found, creating new one");
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
            LOGGER.error("Failed to save playtime data", e);
        }
    }

    public void playerLogin(UUID playerUUID, String playerName) {
        // Get or create player data
        PlayerTimeData timeData = playerData.computeIfAbsent(playerUUID, uuid -> new PlayerTimeData(playerName));
        
        // Update last seen date
        timeData.lastSeen = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        
        // Record login time
        loginTimes.put(playerUUID, System.currentTimeMillis());
        
        // Initialize last activity
        lastActivity.put(playerUUID, System.currentTimeMillis());
        
        // Check if this is a new day
        LocalDate currentDate = LocalDate.now();
        if (!currentDate.format(DateTimeFormatter.ISO_LOCAL_DATE).equals(timeData.lastPlayDate)) {
            // Reset daily time if it's a new day
            timeData.dailyTime = 0;
            timeData.lastPlayDate = currentDate.format(DateTimeFormatter.ISO_LOCAL_DATE);
            
            // If they stayed under limit yesterday, increase streak
            if (timeData.underLimitYesterday) {
                timeData.streak++;
            } else {
                timeData.streak = 0;
            }
            
            // Reset under limit flag
            timeData.underLimitYesterday = true;
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
        
        PlayerTimeData timeData = playerData.get(playerUUID);
        if (timeData != null) {
            // Update total playtime
            timeData.totalPlaytime += sessionMinutes;
            
            // Update daily playtime
            timeData.dailyTime += sessionMinutes;
            
            // Check if they've gone over the limit
            if (timeData.dailyTime > getDailyLimit(playerUUID) && !isSaturday()) {
                timeData.underLimitYesterday = false;
            }
            
            saveData();
        }
        
        // Clean up login and activity tracking
        loginTimes.remove(playerUUID);
        lastActivity.remove(playerUUID);
    }

    public void updatePlayerActivity(UUID playerUUID) {
        lastActivity.put(playerUUID, System.currentTimeMillis());
    }

    public boolean isPlayerAFK(UUID playerUUID) {
        if (!lastActivity.containsKey(playerUUID)) {
            return false;
        }
        
        long lastActiveTime = lastActivity.get(playerUUID);
        long inactiveTime = System.currentTimeMillis() - lastActiveTime;
        
        // AFK if inactive for 5+ minutes
        return inactiveTime >= (5 * 60 * 1000);
    }

    public int getRemainingTime(UUID playerUUID) {
        PlayerTimeData timeData = playerData.get(playerUUID);
        if (timeData == null) {
            return getDailyLimit(playerUUID);
        }
        
        // Check if it's Saturday (unlimited time)
        if (isSaturday()) {
            return Integer.MAX_VALUE;
        }
        
        int dailyLimit = getDailyLimit(playerUUID);
        int remaining = dailyLimit - timeData.dailyTime;
        return Math.max(0, remaining);
    }

    public int getDailyLimit(UUID playerUUID) {
        PlayerTimeData timeData = playerData.get(playerUUID);
        if (timeData == null) {
            return DEFAULT_DAILY_LIMIT_MINUTES;
        }
        
        // Base limit plus streak bonus (max +20 minutes)
        int streakBonus = Math.min(timeData.streak, MAX_STREAK_BONUS);
        return DEFAULT_DAILY_LIMIT_MINUTES + streakBonus;
    }

    public long getTotalPlaytime(UUID playerUUID) {
        PlayerTimeData timeData = playerData.get(playerUUID);
        return timeData != null ? timeData.totalPlaytime : 0;
    }

    public int getStreak(UUID playerUUID) {
        PlayerTimeData timeData = playerData.get(playerUUID);
        return timeData != null ? timeData.streak : 0;
    }

    public void addTime(UUID playerUUID, int minutes) {
        PlayerTimeData timeData = playerData.get(playerUUID);
        if (timeData != null) {
            timeData.bonusTime += minutes;
            saveData();
        }
    }

    private boolean isSaturday() {
        return LocalDate.now().getDayOfWeek() == DayOfWeek.SATURDAY;
    }

    public static class PlayerTimeData {
        public String playerName;
        public long totalPlaytime = 0; // in minutes
        public int dailyTime = 0; // in minutes
        public int streak = 0; // consecutive days under limit
        public boolean underLimitYesterday = true;
        public int bonusTime = 0; // admin-granted bonus minutes
        public String lastPlayDate = "";
        public String lastSeen = "";

        public PlayerTimeData(String playerName) {
            this.playerName = playerName;
            this.lastPlayDate = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
            this.lastSeen = this.lastPlayDate;
        }
    }
}
