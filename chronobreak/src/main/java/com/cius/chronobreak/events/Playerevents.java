package com.cius.chronobreak.events;

import com.cius.chronobreak.config.PlaytimeData;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
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
        if (event.getPlayer() instanceof ServerPlayerEntity) {
            ServerPlayerEntity player = (ServerPlayerEntity) event.getPlayer();
            playtimeData.playerLogin(player.getUUID(), player.getName().getString());
            
            // Reset warnings
            playerWarnings.put(player.getUUID(), 0);
            
            // Show remaining time message
            int remaining = playtimeData.getRemainingTime(player.getUUID());
            if (remaining == Integer.MAX_VALUE) {
                player.sendMessage(new StringTextComponent(
                        TextFormatting.GREEN + "It's Saturday! Enjoy unlimited playtime today."), 
                        player.getUUID());
            } else {
                player.sendMessage(new StringTextComponent(
                        TextFormatting.GREEN + "Welcome! You have " + formatTime(remaining) + " of playtime remaining today."), 
                        player.getUUID());
            }
        }
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getPlayer() instanceof ServerPlayerEntity) {
            ServerPlayerEntity player = (ServerPlayerEntity) event.getPlayer();
            playtimeData.playerLogout(player.getUUID());
            
            // Clean up warnings
            playerWarnings.remove(player.getUUID());
        }
    }

    @SubscribeEvent
    public void onPlayerMove(PlayerEvent.PlayerMoveEvent event) {
        if (event.getPlayer() instanceof ServerPlayerEntity) {
            ServerPlayerEntity player = (ServerPlayerEntity) event.getPlayer();
            playtimeData.updatePlayerActivity(player.getUUID());
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
        
        for (ServerPlayerEntity player : server.getPlayerList().getPlayers()) {
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
                player.connection.disconnect(new StringTextComponent(
                        "You've reached your daily playtime limit. Come back tomorrow!"));
            } else if (remaining <= 5 && !playerWarnings.containsKey(playerUUID)) {
                playerWarnings.put(playerUUID, 1);
                player.sendMessage(new StringTextComponent(
                        TextFormatting.RED + "Warning: You have less than 5 minutes of playtime remaining today!"), 
                        playerUUID);
            } else if (remaining <= 15 && playerWarnings.getOrDefault(playerUUID, 0) < 1) {
                playerWarnings.put(playerUUID, 1);
                player.sendMessage(new StringTextComponent(
                        TextFormatting.YELLOW + "Warning: You have less than 15 minutes of playtime remaining today!"), 
                        playerUUID);
            } else if (remaining <= 30 && playerWarnings.getOrDefault(playerUUID, 0) < 1) {
                playerWarnings.put(playerUUID, 1);
                player.sendMessage(new StringTextComponent(
                        TextFormatting.YELLOW + "Warning: You have less than 30 minutes of playtime remaining today!"), 
                        playerUUID);
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