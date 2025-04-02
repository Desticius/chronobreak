package com.cius.chronobreak.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.cius.chronobreak.config.PlaytimeData;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.ChatFormatting;

import java.util.UUID;

public class AddtimeCommand {
    
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, PlaytimeData playtimeData) {
        dispatcher.register(
            Commands.literal("addtime")
                .requires(source -> source.hasPermission(2)) // Require op level for this command
                .then(Commands.argument("player", StringArgumentType.word())
                    .then(Commands.argument("minutes", IntegerArgumentType.integer(1))
                        .executes(context -> {
                            String playerName = StringArgumentType.getString(context, "player");
                            int minutes = IntegerArgumentType.getInteger(context, "minutes");
                            return addTime(context.getSource(), playerName, minutes, playtimeData);
                        })
                    )
                )
        );
    }
    
    private static int addTime(CommandSourceStack source, String targetPlayerName, int minutes, PlaytimeData playtimeData) {
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
        
        if (targetUUID != null) {
            // Use the addTime method from PlaytimeData
            playtimeData.addTime(targetUUID, minutes);
            
            // Create a properly colored message component
            Component message = Component.literal("Added " + minutes + " minutes to " + displayName + "'s time limit.").withStyle(ChatFormatting.GREEN);
            source.sendSuccess(() -> message, true);
            
            // Notify player if online
            ServerPlayer targetPlayer = server.getPlayerList().getPlayer(targetUUID);
            if (targetPlayer != null) {
                Component playerMessage = Component.literal("An admin granted you " + minutes + " minute" + 
                        (minutes != 1 ? "s" : "") + " of bonus playtime!").withStyle(ChatFormatting.GREEN);
                targetPlayer.sendSystemMessage(playerMessage);
            }
        } else {
            Component errorMessage = Component.literal("Player not found: " + targetPlayerName).withStyle(ChatFormatting.RED);
            source.sendFailure(errorMessage);
        }
        
        return 1;
    }
}