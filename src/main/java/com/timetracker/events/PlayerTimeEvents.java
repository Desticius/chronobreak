package com.timetracker.events;

import com.timetracker.config.PlayerTimeData;
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

public class PlayerTimeEvents {
    private final PlayerTimeData playerTimeData;
    private final Map<UUID, Integer> playerWarnings = new HashMap<>();
    private int tickCounter = 0;

    public PlayerTimeEvents(PlayerTimeData playerTimeData) {
        this.playerTimeData = playerTimeData;
    }

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer) {
            ServerPlayer player = (ServerPlayer) event.getEntity();
            playerTimeData.playerLogin(player.getUUID(), player.getName().getString());
            
            // Reset warnings
            playerWarnings.put(player.getUUID(), 0);
            
            // Show remaining time message
            int remaining = playerTimeData.getRemainingTime(player.getUUID());
            Component message = Component.literal("Chronobreak: Welcome! You have " + formatTime(remaining) + " of playtime remaining today.")
                .withStyle(ChatFormatting.GREEN);
            player.sendSystemMessage(message);
        }
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer) {
            ServerPlayer player = (ServerPlayer) event.getEntity();
            playerTimeData.playerLogout(player.getUUID());
            
            // Clean up warnings
            playerWarnings.remove(player.getUUID());
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
            
            int remaining = playerTimeData.getRemainingTime(playerUUID);
            
            // Time warnings and kick
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
