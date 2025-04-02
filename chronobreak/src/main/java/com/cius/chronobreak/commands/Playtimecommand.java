package com.yourname.chronobreak.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.yourname.chronobreak.config.PlaytimeData;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;

import java.util.UUID;

public class PlaytimeCommand {
    
    public static void register(CommandDispatcher<CommandSource> dispatcher, PlaytimeData playtimeData) {
        dispatcher.register(
            Commands.literal("playtime")
                .executes(context -> {
                    return showPlaytime(context.getSource(), null, playtimeData);
                })
                .then(Commands.argument("player", StringArgumentType.word())
                    .requires(source -> source.hasPermissionLevel(2)) // Require op level for checking other players
                    .executes(context -> {
                        String playerName = StringArgumentType.getString(context, "player");
                        return showPlaytime(context.getSource(), playerName, playtimeData);
                    })
                )
        );
    }
    
    private static int showPlaytime(CommandSource source, String targetPlayerName, PlaytimeData playtimeData) {
        try {
            if (targetPlayerName == null) {
                // Show playtime for command sender
                ServerPlayerEntity player = source.asPlayer();
                UUID playerUUID = player.getUniqueID();
                long totalPlaytime = playtimeData.getTotalPlaytime(playerUUID);
                int streak = playtimeData.getStreak(playerUUID);
                
                source.sendFeedback(new StringTextComponent(
                        TextFormatting.GREEN + "Your total playtime: " + formatTime(totalPlaytime)), false);
                
                if (streak > 0) {
                    source.sendFeedback(new StringTextComponent(
                            TextFormatting.AQUA + "Current streak: " + streak + " day" + (streak != 1 ? "s" : "") + 
                            " (+" + Math.min(streak, 20) + " minute bonus)"), false);
                }
            } else {
                // Show playtime for specified player (admin command)
                MinecraftServer server = source.getServer();
                UUID targetUUID = null;
                String displayName = targetPlayerName;
                
                // Try to get player UUID from online players
                for (ServerPlayerEntity player : server.getPlayerList().getPlayers()) {
                    if (player.getName().getString().equalsIgnoreCase(targetPlayerName)) {
                        targetUUID = player.getUniqueID();
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
                
                long totalPlaytime = playtimeData.getTotalPlaytime(targetUUID);
                int streak = playtimeData.getStreak(targetUUID);
                
                source.sendFeedback(new StringTextComponent(
                        TextFormatting.GREEN + displayName + "'s total playtime: " + formatTime(totalPlaytime)), false);
                
                if (streak > 0) {
                    source.sendFeedback(new StringTextComponent(
                            TextFormatting.AQUA + "Current streak: " + streak + " day" + (streak != 1 ? "s" : "") + 
                            " (+" + Math.min(streak, 20) + " minute bonus)"), false);
                }
            }
            
            return 1;
        } catch (CommandSyntaxException e) {
            source.sendFeedback(new StringTextComponent(
                    TextFormatting.RED + "This command can only be used by players."), false);
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
