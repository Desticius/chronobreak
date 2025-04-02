package com.cius.chronobreak.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.cius.chronobreak.config.PlaytimeData;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;

import java.util.UUID;

public class TimeleftCommand {
    
    public static void register(CommandDispatcher<CommandSource> dispatcher, PlaytimeData playtimeData) {
        dispatcher.register(
            Commands.literal("timeleft")
                .executes(context -> {
                    return showTimeLeft(context.getSource(), null, playtimeData);
                })
                .then(Commands.argument("player", StringArgumentType.word())
                    .requires(source -> source.hasPermissionLevel(2)) // Require op level for checking other players
                    .executes(context -> {
                        String playerName = StringArgumentType.getString(context, "player");
                        return showTimeLeft(context.getSource(), playerName, playtimeData);
                    })
                )
        );
    }
    
    private static int showTimeLeft(CommandSource source, String targetPlayerName, PlaytimeData playtimeData) {
        try {
            if (targetPlayerName == null) {
                // Show time for command sender
                ServerPlayerEntity player = source.asPlayer();
                UUID playerUUID = player.getUUID();
                int remaining = playtimeData.getRemainingTime(playerUUID);
                
                if (remaining == Integer.MAX_VALUE) {
                    source.sendFeedback(new StringTextComponent(
                            TextFormatting.GREEN + "It's Saturday! You have unlimited playtime today."), false);
                } else {
                    source.sendFeedback(new StringTextComponent(
                            TextFormatting.GREEN + "You have " + formatTime(remaining) + " of playtime remaining today."), false);
                }
            } else {
                // Show time for specified player (admin command)
                MinecraftServer server = source.getServer();
                UUID targetUUID = null;
                String displayName = targetPlayerName;
                
                // Try to get player UUID from online players
                for (ServerPlayerEntity player : server.getPlayerList().getPlayers()) {
                    if (player.getName().getString().equalsIgnoreCase(targetPlayerName)) {
                        targetUUID = player.getUUID();
                        displayName = player.getName().getString();
                        break;
                    }
                }
                
                if (targetUUID == null) {
                    // Player not found online
                    source.sendFeedback(new StringTextComponent(
                            TextFormatting.RED + "Player not found: " + targetPlayerName), false);
                    return 0;
                }
                
                int remaining = playtimeData.getRemainingTime(targetUUID);
                
                if (remaining == Integer.MAX_VALUE) {
                    source.sendFeedback(new StringTextComponent(
                            TextFormatting.GREEN + displayName + " has unlimited playtime today (Saturday)."), false);
                } else {
                    source.sendFeedback(new StringTextComponent(
                            TextFormatting.GREEN + displayName + " has " + formatTime(remaining) + " of playtime remaining today."), false);
                }
            }
            
            return 1;
        } catch (CommandSyntaxException e) {
            source.sendFeedback(new StringTextComponent(
                    TextFormatting.RED + "This command can only be used by players."), false);
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