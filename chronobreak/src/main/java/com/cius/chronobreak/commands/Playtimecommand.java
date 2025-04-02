package com.cius.chronobreak.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.cius.chronobreak.config.PlaytimeData;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.ChatFormatting;

import java.util.UUID;

public class PlaytimeCommand {
    
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, PlaytimeData playtimeData) {
        dispatcher.register(
            Commands.literal("playtime")
                .executes(context -> {
                    return showPlaytime(context.getSource(), null, playtimeData);
                })
                .then(Commands.argument("player", StringArgumentType.word())
                    .requires(source -> source.hasPermission(2)) // Require op level for checking other players
                    .executes(context -> {
                        String playerName = StringArgumentType.getString(context, "player");
                        return showPlaytime(context.getSource(), playerName, playtimeData);
                    })
                )
        );
    }
    
    private static int showPlaytime(CommandSourceStack source, String targetPlayerName, PlaytimeData playtimeData) {
        try {
            if (targetPlayerName == null) {
                // Show playtime for command sender
                ServerPlayer player = source.getPlayerOrException();
                UUID playerUUID = player.getUUID();
                long totalPlaytime = playtimeData.getTotalPlaytime(playerUUID);
                int streak = playtimeData.getStreak(playerUUID);
                
                Component message = Component.literal("Your total playtime: " + formatTime(totalPlaytime))
                    .withStyle(ChatFormatting.GREEN);
                source.sendSuccess(() -> message, false);
                
                if (streak > 0) {
                    Component streakMessage = Component.literal("Current streak: " + streak + " day" + 
                            (streak != 1 ? "s" : "") + " (+" + Math.min(streak, 20) + " minute bonus)")
                            .withStyle(ChatFormatting.AQUA);
                    source.sendSuccess(() -> streakMessage, false);
                }
            } else {
                // Show playtime for specified player (admin command)
                MinecraftServer server = source.getServer();
                UUID targetUUID = null;
                String displayName = targetPlayerName;
                
                // Try to get player UUID from online players
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    if (player.getName().getString().equalsIgnoreCase(targetPlayerName)) {
                        targetUUID = player.getUUID();
                        displayName = player.getName().getString();
                        break;
                    }
                }
                
                if (targetUUID == null) {
                    // Player not found online
                    Component errorMessage = Component.literal("Player not found: " + targetPlayerName)
                        .withStyle(ChatFormatting.RED);
                    source.sendFailure(errorMessage);
                    return 0;
                }
                
                long totalPlaytime = playtimeData.getTotalPlaytime(targetUUID);
                int streak = playtimeData.getStreak(targetUUID);
                
                Component playerMessage = Component.literal(displayName + "'s total playtime: " + formatTime(totalPlaytime))
                    .withStyle(ChatFormatting.GREEN);
                source.sendSuccess(() -> playerMessage, false);
                
                if (streak > 0) {
                    Component streakMessage = Component.literal("Current streak: " + streak + " day" + 
                            (streak != 1 ? "s" : "") + " (+" + Math.min(streak, 20) + " minute bonus)")
                            .withStyle(ChatFormatting.AQUA);
                    source.sendSuccess(() -> streakMessage, false);
                }
            }
            
            return 1;
        } catch (CommandSyntaxException e) {
            Component errorMessage = Component.literal("This command can only be used by players.")
                .withStyle(ChatFormatting.RED);
            source.sendFailure(errorMessage);
            return 0;
        }
    }
    
    private static String formatTime(long minutes) {
        if (minutes >= 60) {
            long hours = minutes / 60;
            long mins = minutes % 60;
            
            if (hours >= 24) {
                long days = hours / 24;
                hours = hours % 24;
                
                return days + " day" + (days != 1 ? "s" : "") + 
                       (hours > 0 ? ", " + hours + " hour" + (hours != 1 ? "s" : "") : "") + 
                       (mins > 0 ? ", " + mins + " minute" + (mins != 1 ? "s" : "") : "");
            } else {
                return hours + " hour" + (hours != 1 ? "s" : "") + 
                       (mins > 0 ? ", " + mins + " minute" + (mins != 1 ? "s" : "") : "");
            }
        } else {
            return minutes + " minute" + (minutes != 1 ? "s" : "");
        }
    }
}