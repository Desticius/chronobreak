package com.timetracker.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.timetracker.config.PlayerTimeData;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.ChatFormatting;

import java.util.UUID;

public class TimeleftCommand {
    
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, PlayerTimeData playerTimeData) {
        dispatcher.register(
            Commands.literal("timeleft")
                .executes(context -> {
                    return showTimeLeft(context.getSource(), null, playerTimeData);
                })
                .then(Commands.argument("player", StringArgumentType.word())
                    .requires(source -> source.hasPermission(2)) // Require op level for checking other players
                    .executes(context -> {
                        String playerName = StringArgumentType.getString(context, "player");
                        return showTimeLeft(context.getSource(), playerName, playerTimeData);
                    })
                )
        );
    }
    
    private static int showTimeLeft(CommandSourceStack source, String targetPlayerName, PlayerTimeData playerTimeData) {
        try {
            if (targetPlayerName == null) {
                // Show time for command sender
                ServerPlayer player = source.getPlayerOrException();
                UUID playerUUID = player.getUUID();
                int remaining = playerTimeData.getRemainingTime(playerUUID);
                
                Component message = Component.literal("Chronobreak: You have " + formatTime(remaining) + 
                    " of playtime remaining today" + 
                    (playerTimeData.getPlayerBonusTime(playerUUID) > 0 ? 
                        " (includes " + formatTime(playerTimeData.getPlayerBonusTime(playerUUID)) + " bonus time)" : "") + ".")
                    .withStyle(ChatFormatting.GREEN);
                source.sendSuccess(() -> message, false);
            } else {
                // Show time for specified player (admin command)
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
                    Component errorMessage = Component.literal("Chronobreak: Player not found: " + targetPlayerName)
                        .withStyle(ChatFormatting.RED);
                    source.sendFailure(errorMessage);
                    return 0;
                }
                
                int remaining = playerTimeData.getRemainingTime(targetUUID);
                
                Component message = Component.literal("Chronobreak: " + displayName + " has " + formatTime(remaining) + 
                    " of playtime remaining today" + 
                    (playerTimeData.getPlayerBonusTime(targetUUID) > 0 ? 
                        " (includes " + formatTime(playerTimeData.getPlayerBonusTime(targetUUID)) + " bonus time)" : "") + ".")
                    .withStyle(ChatFormatting.GREEN);
                source.sendSuccess(() -> message, false);
            }
            
            return 1;
        } catch (CommandSyntaxException e) {
            Component errorMessage = Component.literal("Chronobreak: This command can only be used by players.")
                .withStyle(ChatFormatting.RED);
            source.sendFailure(errorMessage);
            return 0;
        }
    }
    
    private static String formatTime(int minutes) {
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
