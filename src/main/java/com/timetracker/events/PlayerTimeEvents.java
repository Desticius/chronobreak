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
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.PlayerEvent.ItemPickupEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import com.timetracker.config.ConfigManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class PlayerTimeEvents {
    private final PlayerTimeData playerTimeData;
    private final Map<UUID, Integer> playerWarnings = new HashMap<>();
    private int tickCounter = 0;
    private int timeUpdateCounter = 0;

    public PlayerTimeEvents(PlayerTimeData playerTimeData) {
        this.playerTimeData = playerTimeData;
    }

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer) {
            ServerPlayer player = (ServerPlayer) event.getEntity();
            playerTimeData.playerLogin(player.getUUID(), player.getName().getString());
            
            // Initialize activity tracking
            playerTimeData.recordPlayerActivity(player.getUUID());
            
            // Reset warnings
            playerWarnings.put(player.getUUID(), 0);
            
            // Show remaining time message
            int remaining = playerTimeData.getRemainingTime(player.getUUID());
            int bonusTime = playerTimeData.getPlayerBonusTime(player.getUUID());

            Component message;
            if (bonusTime > 0) {
                String messageTemplate = ConfigManager.getConfig().welcomeWithBonusMessage;
                String formattedMessage = messageTemplate
                    .replace("{remaining}", formatTime(remaining))
                    .replace("{bonus}", formatTime(bonusTime));
                message = Component.literal(formattedMessage)
                    .withStyle(ChatFormatting.GREEN);
            } else {
                String messageTemplate = ConfigManager.getConfig().welcomeMessage;
                String formattedMessage = messageTemplate
                    .replace("{remaining}", formatTime(remaining));
                message = Component.literal(formattedMessage)
                    .withStyle(ChatFormatting.GREEN);
            }
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
        
        // Update player time every 30 seconds
        timeUpdateCounter++;
        if (timeUpdateCounter >= 30) {
            timeUpdateCounter = 0;
            updatePlayerTimes();
        }
        
        // Get server instance and player list
        net.minecraft.server.MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        
        // Create a copy of the player list to avoid concurrent modification
        List<ServerPlayer> players = new ArrayList<>(server.getPlayerList().getPlayers());
        
        // Process time warnings and kicks
        for (ServerPlayer player : players) {
            UUID playerUUID = player.getUUID();
            
            int remaining = playerTimeData.getRemainingTime(playerUUID);
            
            // Time warnings
            for (int warningMinutes : ConfigManager.getConfig().warningTimes) {
                if (remaining <= warningMinutes && playerWarnings.getOrDefault(playerUUID, 0) < 1) {
                    playerWarnings.put(playerUUID, 1);
                    String messageTemplate = ConfigManager.getConfig().warningMessage;
                    String formattedMessage = messageTemplate.replace("{minutes}", String.valueOf(warningMinutes));
                    
                    ChatFormatting color = warningMinutes <= 5 ? ChatFormatting.RED : ChatFormatting.YELLOW;
                    Component warningMessage = Component.literal(formattedMessage)
                        .withStyle(color);
                    player.sendSystemMessage(warningMessage);
                    break; // Only show the most urgent warning
                }
            }
            
            // Handle kicks separately to avoid concurrent modification
            if (remaining <= 0) {
                // Schedule kick for next tick to avoid concurrent modification
                server.execute(() -> {
                    Component kickMessage = Component.literal(ConfigManager.getConfig().kickMessage);
                    player.connection.disconnect(kickMessage);
                });
            }
        }
        
        // Process AFK status changes with a separate loop to be safe
        for (ServerPlayer player : players) {
            UUID playerUUID = player.getUUID();
            
            boolean wasAfk = playerTimeData.getAfkStatus(playerUUID);
            boolean isAfk = playerTimeData.isPlayerAFK(playerUUID);
            
            // If AFK status changed, notify player
            if (isAfk && !wasAfk) {
                Component afkMessage = Component.literal(ConfigManager.getConfig().afkMessage)
                    .withStyle(ChatFormatting.GRAY);
                player.sendSystemMessage(afkMessage);
            } else if (!isAfk && wasAfk) {
                Component activeMessage = Component.literal(ConfigManager.getConfig().notAfkMessage)
                    .withStyle(ChatFormatting.GRAY);
                player.sendSystemMessage(activeMessage);
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

    private void updatePlayerTimes() {
        net.minecraft.server.MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID playerUUID = player.getUUID();
            
            // Only process players with a login time
            if (playerTimeData.hasLoginTime(playerUUID)) {
                // Update the player's time without removing the login time
                playerTimeData.updatePlayerTime(playerUUID);
            }
        }
    }

    @SubscribeEvent
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getEntity() instanceof ServerPlayer) {
            ServerPlayer player = (ServerPlayer) event.getEntity();
            playerTimeData.recordPlayerActivity(player.getUUID());
        }
    }

    @SubscribeEvent
    public void onPlayerMove(net.minecraftforge.event.entity.living.LivingEvent.LivingTickEvent event) {
        if (event.getEntity() instanceof ServerPlayer) {
            ServerPlayer player = (ServerPlayer) event.getEntity();
            
            // Only record activity if the player has moved a significant distance
            // This is to avoid constant updates when a player is just looking around
            if (player.getDeltaMovement().lengthSqr() > 0.0001) {
                playerTimeData.recordPlayerActivity(player.getUUID());
            }
        }
    }

    @SubscribeEvent
    public void onItemPickup(ItemPickupEvent event) {
        if (event.getEntity() instanceof ServerPlayer) {
            ServerPlayer player = (ServerPlayer) event.getEntity();
            playerTimeData.recordPlayerActivity(player.getUUID());
        }
    }

    @SubscribeEvent
    public void onItemUse(LivingEntityUseItemEvent.Start event) {
        if (event.getEntity() instanceof ServerPlayer) {
            ServerPlayer player = (ServerPlayer) event.getEntity();
            playerTimeData.recordPlayerActivity(player.getUUID());
        }
    }
}
