package com.timetracker.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;

public class ConfigManager {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final String CONFIG_FILE = "config/chronobreak.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    
    private static ModConfig config;
    
    public static void loadConfig() {
        File file = new File(CONFIG_FILE);
        if (file.exists()) {
            try (FileReader reader = new FileReader(file)) {
                config = GSON.fromJson(reader, ModConfig.class);
                
                // Validate config values to prevent issues
                validateConfig();
                
                LOGGER.info("Loaded config from {}", CONFIG_FILE);
            } catch (IOException e) {
                LOGGER.error("Failed to load config, using defaults", e);
                config = new ModConfig();
                saveConfig(); // Create the file with default values
            }
        } else {
            LOGGER.info("Config file not found, creating with default values");
            config = new ModConfig();
            saveConfig();
        }
    }
    
    private static void validateConfig() {
        // Ensure time limits are reasonable
        if (config.defaultDailyLimitMinutes < 0 || config.defaultDailyLimitMinutes > 10080) { // Max 1 week (7 days * 24 hours * 60 minutes)
            LOGGER.warn("Invalid defaultDailyLimitMinutes value: {}. Setting to default (120)", config.defaultDailyLimitMinutes);
            config.defaultDailyLimitMinutes = 120;
        }
        
        // Ensure AFK timeout is reasonable
        if (config.afkTimeoutSeconds < 10 || config.afkTimeoutSeconds > 3600) { // Between 10 seconds and 1 hour
            LOGGER.warn("Invalid afkTimeoutSeconds value: {}. Setting to default (180)", config.afkTimeoutSeconds);
            config.afkTimeoutSeconds = 180;
        }
        
        // Ensure warning times are sorted in descending order
        if (config.warningTimes != null && config.warningTimes.length > 0) {
            Arrays.sort(config.warningTimes);
            // Reverse the array to have descending order
            for (int i = 0; i < config.warningTimes.length / 2; i++) {
                int temp = config.warningTimes[i];
                config.warningTimes[i] = config.warningTimes[config.warningTimes.length - 1 - i];
                config.warningTimes[config.warningTimes.length - 1 - i] = temp;
            }
        } else {
            config.warningTimes = new int[] {30, 15, 5};
        }
    }
    
    public static void saveConfig() {
        File file = new File(CONFIG_FILE);
        try {
            if (!file.exists()) {
                file.getParentFile().mkdirs();
                file.createNewFile();
            }
            
            try (FileWriter writer = new FileWriter(file)) {
                GSON.toJson(config, writer);
                LOGGER.info("Saved config to {}", CONFIG_FILE);
            }
        } catch (IOException e) {
            LOGGER.error("Failed to save config", e);
        }
    }
    
    public static ModConfig getConfig() {
        if (config == null) {
            loadConfig();
        }
        return config;
    }
    
    public static class ModConfig {
        // Time limits
        public int defaultDailyLimitMinutes = 120; // 2 hours by default
        public boolean enableSaturdayUnlimited = true;
        public boolean enableAfkDetection = true;
        public int afkTimeoutSeconds = 180; // 3 minutes
        
        // Warning times (in minutes)
        public int[] warningTimes = {30, 15, 5};
        
        // Messages
        public String welcomeMessage = "Chronobreak: Welcome! You have {remaining} of playtime remaining today.";
        public String welcomeWithBonusMessage = "Chronobreak: Welcome! You have {remaining} of playtime remaining today (includes {bonus} bonus time).";
        public String timeAddedMessage = "Chronobreak: Added {minutes} minutes of bonus time to {player}'s daily allowance.";
        public String playerTimeAddedMessage = "Chronobreak: An admin granted you {minutes} minute(s) of additional playtime on top of your daily limit!";
        public String timeRemovedMessage = "Chronobreak: Removed {minutes} minutes from {player}'s remaining time.";
        public String playerTimeRemovedMessage = "Chronobreak: An admin reduced your remaining time by {minutes} minute(s)!";
        public String warningMessage = "Warning: You have less than {minutes} minutes of playtime remaining today!";
        public String kickMessage = "You've reached your daily playtime limit. Come back tomorrow!";
        public String afkMessage = "You are now AFK. Time tracking paused.";
        public String notAfkMessage = "You are no longer AFK. Time tracking resumed.";
        
        public ModConfig() {
            // Default constructor uses default values
        }
    }
} 