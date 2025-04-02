package com.cius.chronobreak.events;

import com.cius.chronobreak.config.PlaytimeData;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.ChatFormatting;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerEvents {
    private final PlaytimeData playtimeData;
    private final Map<UUID, Integer> playerWarnings = new HashMap<>();
    private int tickCounter = 0;

    public PlayerEvents(PlaytimeData playtimeData) {
        this.playtimeData = playtimeData;
    }

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer) {
            ServerPlayer player = (ServerPlayer) event.getEntity();
            playtimeData.playerLogin(player.getUUID(), player.getName().getString());
            
            // Reset warnings
            playerWarnings.put(player.getUUID(), 0);
            
            // Show remaining time message
            int remaining = playtimeData.getRemainingTime(player.getUUID());
            if (remaining == Integer.MAX_VALUE) {
                Component message = Component.literal("It's Saturday! Enjoy unlimited playtime today.")
                    .withStyle(ChatFormatting.GREEN);
                player.sendSystemMessage(message);
            } else {
                Component message = Component.literal("Welcome! You have " + formatTime(remaining) + " of playtime remaining today.")
                    .withStyle(ChatFormatting.GREEN);
                player.sendSystemMessage(message);
            }
        }
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer) {
            ServerPlayer player = (ServerPlayer) event.getEntity();
            playtimeData.playerLogout(player.getUUID());
            
            // Clean up warnings
            playerWarnings.remove(player.getUUID());
        }
    }

    // For player movement tracking in 1.20.1, we use LivingTickEvent for ServerPlayers
    @SubscribeEvent
    public void onPlayerTick(net.minecraftforge.event.entity.living.LivingEvent.LivingTickEvent event) {
        if (event.getEntity() instanceof ServerPlayer) {
            ServerPlayer player = (ServerPlayer) event.getEntity();
            // Update activity status whenever player is active
            if (player.walkDist > 0) {
                playtimeData.updatePlayerActivity(player.getUUID());
            }
        }
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.side != LogicalSide.SERVER || event.phase != TickEvent.Phase.END) {
            return;
        }
        
        // Only check every 20 ticks (1 second)
        tickCounter++;
        if (tickCounter < 20) {
            return;
        }
        tickCounter = 0;
        
        // Get server instance and player list
        net.minecraft.server.MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID playerUUID = player.getUUID();
            
            // Skip AFK players
            if (playtimeData.isPlayerAFK(playerUUID)) {
                continue;
            }
            
            int remaining = playtimeData.getRemainingTime(playerUUID);
            
            // If it's Saturday, unlimited time
            if (remaining == Integer.MAX_VALUE) {
                continue;
            }
            
            // Time warnings
            if (remaining <= 0) {
                Component kickMessage = Component.literal("You've reached your daily playtime limit. Come back tomorrow!");
                player.connection.disconnect(kickMessage);
            } else if (remaining <= 5 && !playerWarnings.containsKey(playerUUID)) {
                playerWarnings.put(playerUUID, 1);
                Component warningMessage = Component.literal("Warning: You have less than 5 minutes of playtime remaining today!")
                    .withStyle(ChatFormatting.RED);
                player.sendSystemMessage(warningMessage);
            } else if (remaining <= 15 && playerWarnings.getOrDefault(playerUUID, 0) < 1) {
                playerWarnings.put(playerUUID, 1);
                Component warningMessage = Component.literal("Warning: You have less than 15 minutes of playtime remaining today!")
                    .withStyle(ChatFormatting.YELLOW);
                player.sendSystemMessage(warningMessage);
            } else if (remaining <= 30 && playerWarnings.getOrDefault(playerUUID, 0) < 1) {
                playerWarnings.put(playerUUID, 1);
                Component warningMessage = Component.literal("Warning: You have less than 30 minutes of playtime remaining today!")
                    .withStyle(ChatFormatting.YELLOW);
                player.sendSystemMessage(warningMessage);
            }
        }
    }
    
    private String formatTime(int minutes) {
        if (minutes >= 60) {
            int hours = minutes / 60;
            int mins = minutes % 60;
            return hours + " hour" + (hours != 1 ? "s" : "") + 
                   (mins > 0 ? " and " + mins + " minute" + (mins != 1 ? "s" : "") : "");
        } else {
            return minutes + " minute" + (minutes != 1 ? "s" : "");
        }
    }
}